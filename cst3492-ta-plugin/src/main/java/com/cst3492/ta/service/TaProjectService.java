package com.cst3492.ta.service;

import com.cst3492.ta.chunk.Chunker;
import com.cst3492.ta.citation.CitationVerifier;
import com.cst3492.ta.config.TaSettingsState;
import com.cst3492.ta.embedding.EmbeddingProvider;
import com.cst3492.ta.embedding.HttpEmbeddingProvider;
import com.cst3492.ta.index.IndexMeta;
import com.cst3492.ta.index.JsonVectorStore;
import com.cst3492.ta.llm.HttpLlmClient;
import com.cst3492.ta.llm.LlmClient;
import com.cst3492.ta.llm.Message;
import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.parser.MaterialParser;
import com.cst3492.ta.prompt.PromptBuilder;
import com.cst3492.ta.retrieval.HybridRetriever;
import com.cst3492.ta.retrieval.KeywordIndex;
import com.cst3492.ta.retrieval.Retriever;
import com.cst3492.ta.retrieval.ScoredChunk;
import com.cst3492.ta.util.TextUtil;
import com.cst3492.ta.util.VectorMath;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Project-scoped orchestrator: owns the index, the retriever and the LLM client. The index is
 * loaded lazily on first query so opening a project does not block startup.
 */
@Service(Service.Level.PROJECT)
public final class TaProjectService implements Disposable {

    private final Path indexPath;
    private JsonVectorStore store;
    private Retriever retriever;
    private KeywordIndex keywordIndex;
    private String loadedModel;
    private int loadedDimension;

    public TaProjectService(@NotNull Project project) {
        this(project, indexDirectory());
    }

    TaProjectService(Project project, Path indexPath) {
        this.indexPath = indexPath;
    }

    public static TaProjectService getInstance(@NotNull Project project) {
        return project.getService(TaProjectService.class);
    }

    public record IndexStatus(boolean ready, String description) { }

    /** Local metadata only: checking UI readiness must not contact a provider or access credentials. */
    public synchronized IndexStatus indexStatus() throws IOException {
        if (store == null) loadIndex();
        if (store.meta().chunks.isEmpty()) return new IndexStatus(false, "No course index");
        TaSettingsState settings = TaSettingsState.getInstance();
        if (store.meta().chunkingVersion != 2
                || !settings.embeddingModel.equals(loadedModel)
                || !settings.embeddingBaseUrl.equals(store.meta().embeddingBaseUrl)) {
            return new IndexStatus(false, "Index needs rebuilding");
        }
        return new IndexStatus(true, store.meta().documents.size() + " documents · "
                + store.meta().chunks.size() + " chunks");
    }

    /* ---------------- index building ---------------- */

    public record BuildResult(int documentsBuilt, int documentsSkipped, int chunkCount, int pageCount) {
    }

    public synchronized BuildResult buildIndex(ProgressIndicator indicator) throws IOException {
        TaSettingsState settings = TaSettingsState.getInstance();
        String apiKey = TaSettingsState.getEmbeddingApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("No embedding API key configured. Open Settings > Tools > CST3492 TA.");
        }
        Path materials = Path.of(settings.materialsPath);
        if (settings.materialsPath.isBlank() || !Files.isDirectory(materials)) {
            throw new IOException("Materials folder is not configured or does not exist.");
        }
        EmbeddingProvider provider = new HttpEmbeddingProvider(
                settings.embeddingBaseUrl, settings.embeddingModel, apiKey);

        return buildIndex(settings, provider, indicator);
    }

    synchronized BuildResult buildIndex(TaSettingsState settings, EmbeddingProvider provider,
                                       ProgressIndicator indicator) throws IOException {
        Path materials = Path.of(settings.materialsPath);

        if (store == null) {
            loadIndex();
        }

        List<Path> pdfs;
        try (Stream<Path> walk = Files.list(materials)) {
            pdfs = walk.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted().toList();
        }
        if (pdfs.isEmpty()) {
            throw new IOException("No PDF files found in " + materials);
        }

        // Build a separate snapshot so failed builds cannot corrupt the active retriever.
        JsonVectorStore next = new JsonVectorStore(indexPath);
        boolean reusable = store.meta().chunkingVersion == 2
                && provider.model().equals(store.meta().embeddingModel)
                && settings.embeddingBaseUrl.equals(store.meta().embeddingBaseUrl);
        int built = 0;
        int skipped = 0;
        int totalChunks = 0;
        int totalPages = 0;
        int done = 0;
        for (Path pdf : pdfs) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            indicator.checkCanceled();
            String docId = pdf.getFileName().toString().replaceAll("\\.pdf$", "");
            String fileHash = sha256Of(pdf);
            IndexMeta.DocumentInfo existing = store.meta().documents.get(docId);
            if (reusable && existing != null && fileHash.equals(existing.sha256)) {
                List<KnowledgeChunk> oldChunks = new ArrayList<>();
                List<float[]> oldVectors = new ArrayList<>();
                for (int i = 0; i < store.meta().chunks.size(); i++) {
                    if (docId.equals(store.meta().chunks.get(i).docId)) {
                        oldChunks.add(store.meta().chunks.get(i));
                        oldVectors.add(store.vectors()[i]);
                    }
                }
                next.replaceDocument(docId, oldChunks, oldVectors);
                next.meta().documents.put(docId, existing);
                skipped++;
                done++;
                indicator.setFraction((double) done / pdfs.size());
                continue;
            }
            indicator.setText("Parsing " + pdf.getFileName());
            List<KnowledgeChunk> chunks = Chunker.chunkDocument(docId, MaterialParser.parse(pdf));
            if (chunks.isEmpty()) {
                skipped++;
                done++;
                continue;
            }
            indicator.setText("Embedding " + pdf.getFileName());
            List<float[]> vectors = provider.embedAll(chunks.stream()
                    .map(c -> chunkEmbedText(c)).toList());
            vectors.forEach(VectorMath::normalize);
            next.replaceDocument(docId, chunks, vectors);
            IndexMeta.DocumentInfo info = next.meta().documents.get(docId);
            info.sha256 = fileHash;
            info.pageCount = chunks.get(chunks.size() - 1).pageTo;
            built++;
            totalChunks += chunks.size();
            totalPages += info.pageCount;
            done++;
            indicator.setFraction((double) done / pdfs.size());
        }
        next.meta().chunkingVersion = 2;
        next.meta().embeddingBaseUrl = settings.embeddingBaseUrl;
        indicator.checkCanceled();
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        next.save(provider.model(), next.meta().chunks.isEmpty() ? 0 : firstDimension(next));
        activateIndex(next);
        return new BuildResult(built, skipped, totalChunks, totalPages);
    }

    private static String chunkEmbedText(KnowledgeChunk chunk) {
        String header = (chunk.docName + " page " + chunk.pageFrom + " " + chunk.title).trim();
        return TextUtil.normalize(header) + " | " + chunk.text;
    }

    private static int firstDimension(JsonVectorStore store) {
        for (float[] row : store.vectors()) {
            if (row != null && row.length > 0) {
                return row.length;
            }
        }
        throw new IllegalStateException("Cannot determine the embedding dimension");
    }

    private static String sha256Of(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(file);
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IOException("Cannot hash " + file, e);
        }
    }

    private static Path indexDirectory() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null && !appData.isBlank()
                ? Path.of(appData)
                : Path.of(System.getProperty("user.home"));
        return base.resolve("cst3492-ta").resolve("index");
    }

    private void loadIndex() throws IOException {
        JsonVectorStore loaded = new JsonVectorStore(indexPath);
        if (loaded.exists()) {
            loaded.load();
        }
        activateIndex(loaded);
    }

    private void activateIndex(JsonVectorStore updated) {
        store = updated;
        keywordIndex = new KeywordIndex(store.meta().chunks);
        retriever = new Retriever(store.meta().chunks, store.vectors());
        loadedModel = store.meta().embeddingModel;
        loadedDimension = store.meta().dimension;
    }

    /* ---------------- question answering ---------------- */

    public record Answer(String text, List<KnowledgeChunk> sources, boolean grounded) {
    }

    public synchronized Answer ask(String question, @Nullable String codeContext) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        TaSettingsState settings = TaSettingsState.getInstance();
        String apiKey = TaSettingsState.getChatApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("No API key configured. Open Settings > Tools > CST3492 TA.");
        }
        if (store == null) {
            loadIndex();
        }
        if (store == null || store.meta().chunks.isEmpty()) {
            throw new IOException("The index is empty. Click Build Index first.");
        }
        EmbeddingProvider provider = new HttpEmbeddingProvider(
                settings.embeddingBaseUrl, settings.embeddingModel, requireEmbeddingKey());
        if (loadedModel != null && !loadedModel.equals(provider.model())) {
            throw new IOException("The embedding model changed (" + loadedModel + " -> "
                    + provider.model() + "). Rebuild the index.");
        }
        float[] queryVector = provider.embedAll(List.of(question)).get(0);
        validateIndex(settings, queryVector);

        List<ScoredChunk> hits = searchIndex(question, queryVector, settings);

        List<KnowledgeChunk> retrieved = new ArrayList<>();
        int budget = settings.maxContextTokens;
        for (ScoredChunk hit : hits) {
            if (budget - hit.chunk().tokenCount < 0) {
                break;
            }
            budget -= hit.chunk().tokenCount;
            retrieved.add(hit.chunk());
        }

        LlmClient client = new HttpLlmClient(settings.chatBaseUrl, settings.chatModel, apiKey,
                settings.timeoutSeconds);
        List<Message> messages;
        boolean grounded = !retrieved.isEmpty();
        if (grounded) {
            messages = List.of(new Message("system", PromptBuilder.systemGrounded()),
                    new Message("user", PromptBuilder.userPrompt(question, codeContext, retrieved)));
        } else {
            messages = List.of(new Message("system", PromptBuilder.systemGeneral()),
                    new Message("user", PromptBuilder.userPrompt(question, codeContext, List.of())));
        }
        String raw = client.complete(messages, settings.temperature, 1200);
        String verified = CitationVerifier.verify(raw, retrieved);
        return new Answer(verified, retrieved, grounded);
    }

    /* ---------------- shared LLM helper for F1 actions ---------------- */

    public LlmClient llmClient() throws IOException {
        TaSettingsState settings = TaSettingsState.getInstance();
        String apiKey = TaSettingsState.getChatApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("No API key configured. Open Settings > Tools > CST3492 TA.");
        }
        return new HttpLlmClient(settings.chatBaseUrl, settings.chatModel, apiKey, settings.timeoutSeconds);
    }

    /** Retrieval for the code-review action; returns the retrieved chunks only. */
    public synchronized List<KnowledgeChunk> retrieveForContext(String query) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        TaSettingsState settings = TaSettingsState.getInstance();
        if (store == null) {
            loadIndex();
        }
        if (store == null || store.meta().chunks.isEmpty()) {
            return List.of();
        }
        EmbeddingProvider provider = new HttpEmbeddingProvider(
                settings.embeddingBaseUrl, settings.embeddingModel, requireEmbeddingKey());
        float[] queryVector = provider.embedAll(List.of(query)).get(0);
        validateIndex(settings, queryVector);
        List<ScoredChunk> hits = searchIndex(query, queryVector, settings);
        return hits.stream().map(ScoredChunk::chunk).toList();
    }

    synchronized List<ScoredChunk> searchIndex(String query, float[] vector, TaSettingsState settings)
            throws IOException {
        validateIndex(settings, vector);
        return settings.hybridEnabled
                ? new HybridRetriever(keywordIndex, retriever).search(query, vector, settings.topK, settings.threshold)
                : retriever.search(vector, settings.topK, settings.threshold);
    }

    @Override
    public synchronized void dispose() {
        store = null;
        retriever = null;
        keywordIndex = null;
    }

    private static String requireEmbeddingKey() throws IOException {
        String key = TaSettingsState.getEmbeddingApiKey();
        if (key == null || key.isBlank()) {
            throw new IOException("Configure the embedding API key in Settings > Tools > CST3492 TA.");
        }
        return key;
    }

    private void validateIndex(TaSettingsState settings, float[] query) throws IOException {
        if (store.meta().chunkingVersion != 2
                || !settings.embeddingModel.equals(loadedModel)
                || !settings.embeddingBaseUrl.equals(store.meta().embeddingBaseUrl)
                || query.length != loadedDimension) {
            throw new IOException("The index is outdated or embedding settings changed. Click Build Index.");
        }
    }
}
