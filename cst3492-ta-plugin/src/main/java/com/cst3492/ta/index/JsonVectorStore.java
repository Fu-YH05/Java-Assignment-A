package com.cst3492.ta.index;

import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.util.JsonUtil;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Two-file index: index.meta.json for metadata and vectors.bin for row-major float32,
 * little-endian. Writes are atomic via temporary files and moves.
 */
public final class JsonVectorStore {

    private final Path dir;
    private final Path metaFile;
    private final Path vectorFile;
    private IndexMeta meta = new IndexMeta();
    private float[][] vectors = new float[0][];

    public JsonVectorStore(Path dir) {
        this.dir = dir;
        this.metaFile = dir.resolve("index.meta.json");
        this.vectorFile = dir.resolve("vectors.bin");
    }

    public IndexMeta meta() {
        return meta;
    }

    public float[][] vectors() {
        return vectors;
    }

    public boolean exists() {
        return Files.exists(metaFile);
    }

    public void load() throws IOException {
        meta = JsonUtil.mapper().readValue(metaFile.toFile(), IndexMeta.class);
        vectors = new float[meta.chunks.size()][];
        if (meta.dimension <= 0 || meta.chunks.isEmpty()) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(vectorFile.toFile(), "r")) {
            long expected = (long) meta.chunks.size() * meta.dimension * 4;
            if (raf.length() < expected) {
                throw new IOException("vectors.bin is truncated: " + raf.length() + " < " + expected);
            }
            byte[] buffer = new byte[meta.dimension * 4];
            for (int row = 0; row < meta.chunks.size(); row++) {
                raf.readFully(buffer);
                ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
                float[] v = new float[meta.dimension];
                for (int i = 0; i < meta.dimension; i++) {
                    v[i] = bb.getFloat();
                }
                vectors[row] = v;
            }
        }
    }

    public synchronized void replaceDocument(String docId, List<KnowledgeChunk> chunks, List<float[]> rows) {
        // Remove old rows and the document entry, then append the new rows at the end.
        List<KnowledgeChunk> keptChunks = new ArrayList<>();
        List<float[]> keptVectors = new ArrayList<>();
        for (int i = 0; i < meta.chunks.size(); i++) {
            if (!meta.chunks.get(i).docId.equals(docId)) {
                keptChunks.add(meta.chunks.get(i));
                keptVectors.add(vectors[i]);
            }
        }
        keptChunks.addAll(chunks);
        keptVectors.addAll(rows);

        IndexMeta.DocumentInfo info = new IndexMeta.DocumentInfo();
        info.docName = chunks.isEmpty() ? docId : chunks.get(0).docName;
        info.chunkCount = chunks.size();
        meta.documents.put(docId, info);
        meta.chunks = keptChunks;
        vectors = keptVectors.toArray(new float[0][]);
    }

    public synchronized void removeDocument(String docId) {
        List<KnowledgeChunk> keptChunks = new ArrayList<>();
        List<float[]> keptVectors = new ArrayList<>();
        for (int i = 0; i < meta.chunks.size(); i++) {
            if (!meta.chunks.get(i).docId.equals(docId)) {
                keptChunks.add(meta.chunks.get(i));
                keptVectors.add(vectors[i]);
            }
        }
        meta.documents.remove(docId);
        meta.chunks = keptChunks;
        vectors = keptVectors.toArray(new float[0][]);
    }

    public synchronized void save(String embeddingModel, int dimension) throws IOException {
        meta.embeddingModel = embeddingModel;
        meta.dimension = dimension;
        Files.createDirectories(dir);
        Path metaTmp = dir.resolve("index.meta.json.tmp");
        Path vectorTmp = dir.resolve("vectors.bin.tmp");
        JsonUtil.mapper().writerWithDefaultPrettyPrinter().writeValue(metaTmp.toFile(), meta);
        try (RandomAccessFile raf = new RandomAccessFile(vectorTmp.toFile(), "rw")) {
            raf.setLength(0);
            ByteBuffer buffer = ByteBuffer.allocate(dimension * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float[] row : vectors) {
                buffer.clear();
                for (float x : row) {
                    buffer.putFloat(x);
                }
                raf.write(buffer.array(), 0, buffer.position());
            }
        }
        moveAtomically(metaTmp, metaFile);
        moveAtomically(vectorTmp, vectorFile);
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
