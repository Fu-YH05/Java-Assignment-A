package com.cst3492.ta.service;

import com.cst3492.ta.config.TaSettingsState;
import com.cst3492.ta.embedding.EmbeddingProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexLifecycleTest {
    @TempDir Path dir;

    private final ProgressIndicator progress = (ProgressIndicator) Proxy.newProxyInstance(
            ProgressIndicator.class.getClassLoader(), new Class<?>[]{ProgressIndicator.class},
            (proxy, method, args) -> null);

    private static final class Embeddings implements EmbeddingProvider {
        private final String model;
        private final int dimension;
        int calls;
        boolean fail;
        Embeddings(String model, int dimension) { this.model = model; this.dimension = dimension; }
        public String model() { return model; }
        public List<float[]> embedAll(List<String> texts) {
            calls++;
            if (fail) throw new IllegalStateException("Simulated provider failure");
            return texts.stream().map(text -> {
                float[] vector = new float[dimension];
                vector[0] = 1;
                return vector;
            }).toList();
        }
    }

    private TaSettingsState settings() throws Exception {
        TaSettingsState settings = new TaSettingsState();
        settings.materialsPath = Files.createDirectories(dir.resolve("materials")).toString();
        settings.embeddingModel = "test-model";
        return settings;
    }

    private void pdf(TaSettingsState settings, String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(Path.of(settings.materialsPath, "lecture.pdf").toFile());
        }
    }

    @Test void firstBuildImmediatelySupportsDenseAndHybridRetrieval() throws Exception {
        var settings = settings();
        pdf(settings, "Original course content");
        var service = new TaProjectService(null, dir.resolve("index"));
        service.buildIndex(settings, new Embeddings(settings.embeddingModel, 2), progress);
        assertTrue(service.searchIndex("course", new float[]{1, 0}, settings)
                .get(0).chunk().text.contains("Original"));
        settings.hybridEnabled = true;
        assertFalse(service.searchIndex("course", new float[]{1, 0}, settings).isEmpty());
    }

    @Test void changedMaterialReplacesActiveRetrieverAndUnchangedMaterialIsSkipped() throws Exception {
        var settings = settings();
        pdf(settings, "Original course content");
        var service = new TaProjectService(null, dir.resolve("index"));
        var provider = new Embeddings(settings.embeddingModel, 2);
        service.buildIndex(settings, provider, progress);
        service = new TaProjectService(null, dir.resolve("index"));
        assertEquals(1, service.buildIndex(settings, provider, progress).documentsSkipped());
        assertEquals(1, provider.calls);
        pdf(settings, "Updated course content");
        service.buildIndex(settings, provider, progress);
        assertTrue(service.searchIndex("course", new float[]{1, 0}, settings)
                .get(0).chunk().text.contains("Updated"));
    }

    @Test void changedModelAndEndpointReembedUnchangedFiles() throws Exception {
        var settings = settings();
        pdf(settings, "Course content");
        var service = new TaProjectService(null, dir.resolve("index"));
        service.buildIndex(settings, new Embeddings(settings.embeddingModel, 2), progress);
        settings.embeddingModel = "new-model";
        var provider = new Embeddings(settings.embeddingModel, 3);
        assertEquals(1, service.buildIndex(settings, provider, progress).documentsBuilt());
        assertFalse(service.searchIndex("course", new float[]{1, 0, 0}, settings).isEmpty());
        settings.embeddingBaseUrl = "https://new-provider.invalid/v1";
        assertThrows(java.io.IOException.class,
                () -> service.searchIndex("course", new float[]{1, 0, 0}, settings));
        assertEquals(1, service.buildIndex(settings, provider, progress).documentsBuilt());
        assertEquals(2, provider.calls);
    }

    @Test void failedRebuildKeepsPreviousWorkingSnapshot() throws Exception {
        var settings = settings();
        pdf(settings, "Original course content");
        var service = new TaProjectService(null, dir.resolve("index"));
        var provider = new Embeddings(settings.embeddingModel, 2);
        service.buildIndex(settings, provider, progress);
        pdf(settings, "Changed course content");
        provider.fail = true;
        assertThrows(IllegalStateException.class, () -> service.buildIndex(settings, provider, progress));
        assertTrue(service.searchIndex("course", new float[]{1, 0}, settings)
                .get(0).chunk().text.contains("Original"));
    }

    @Test void legacyChunkerIndexIsRebuiltEvenWhenPdfHasNotChanged() throws Exception {
        var settings = settings();
        pdf(settings, "Previously omitted short page");
        var service = new TaProjectService(null, dir.resolve("index"));
        var provider = new Embeddings(settings.embeddingModel, 2);
        service.buildIndex(settings, provider, progress);
        var disk = new com.cst3492.ta.index.JsonVectorStore(dir.resolve("index"));
        disk.load();
        disk.meta().chunkingVersion = 0;
        disk.save(settings.embeddingModel, 2);
        service = new TaProjectService(null, dir.resolve("index"));
        assertEquals(1, service.buildIndex(settings, provider, progress).documentsBuilt());
        assertEquals(2, provider.calls);
        assertTrue(service.searchIndex("short", new float[]{1, 0}, settings)
                .get(0).chunk().text.contains("Previously omitted"));
    }
}
