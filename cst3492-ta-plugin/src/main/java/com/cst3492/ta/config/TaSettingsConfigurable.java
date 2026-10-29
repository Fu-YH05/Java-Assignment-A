package com.cst3492.ta.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/** Settings page under Tools > CST3492 TA. */
public class TaSettingsConfigurable implements Configurable {

    private JTextField chatBaseUrl;
    private JTextField chatModel;
    private JTextField embeddingBaseUrl;
    private JTextField embeddingModel;
    private JTextField materialsPath;
    private JSpinner topK;
    private JSpinner threshold;
    private JCheckBox hybridEnabled;
    private JTextField apiKey;
    private JTextField embeddingApiKey;

    @Override
    public String getDisplayName() {
        return "CST3492 TA";
    }

    @Override
    public @Nullable JComponent createComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        chatBaseUrl = new JTextField(40);
        chatModel = new JTextField(40);
        embeddingBaseUrl = new JTextField(40);
        embeddingModel = new JTextField(40);
        materialsPath = new JTextField(40);
        topK = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
        threshold = new JSpinner(new SpinnerNumberModel(0.35, 0.0, 1.0, 0.05));
        hybridEnabled = new JCheckBox("Enable hybrid retrieval (BM25 + RRF)");
        apiKey = new JPasswordField(40);
        embeddingApiKey = new JPasswordField(40);

        int row = 0;
        addRow(panel, gbc, row++, "Chat base URL:", chatBaseUrl);
        addRow(panel, gbc, row++, "Chat model:", chatModel);
        addRow(panel, gbc, row++, "Embedding base URL:", embeddingBaseUrl);
        addRow(panel, gbc, row++, "Embedding model:", embeddingModel);
        addRow(panel, gbc, row++, "Materials folder (PDFs):", materialsPath);
        addRow(panel, gbc, row++, "Chat API key (blank keeps saved key):", apiKey);
        addRow(panel, gbc, row++, "Embedding API key (blank keeps saved key):", embeddingApiKey);
        addRow(panel, gbc, row++, "Top-K:", topK);
        addRow(panel, gbc, row++, "Similarity threshold:", threshold);
        addRow(panel, gbc, row, "", hybridEnabled);

        JButton test = new JButton("Test Connection");
        test.addActionListener(e -> testConnection());
        GridBagConstraints gbc2 = (GridBagConstraints) gbc.clone();
        gbc2.gridx = 1;
        gbc2.gridy = row + 1;
        panel.add(test, gbc2);
        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private void testConnection() {
        String key = apiKey.getText().isBlank() ? TaSettingsState.getChatApiKey() : apiKey.getText().trim();
        String embedKey = embeddingApiKey.getText().isBlank()
                ? TaSettingsState.getEmbeddingApiKey() : embeddingApiKey.getText().trim();
        if (key == null || key.isBlank() || embedKey == null || embedKey.isBlank()) {
            Messages.showWarningDialog("Configure both chat and embedding API keys first.", "CST3492 TA");
            return;
        }
        String chatUrl = chatBaseUrl.getText().trim();
        String chatName = chatModel.getText().trim();
        String embedUrl = embeddingBaseUrl.getText().trim();
        String embedName = embeddingModel.getText().trim();
        com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().submit(() -> {
            boolean chatOk;
            boolean embedOk;
            try {
                new com.cst3492.ta.llm.HttpLlmClient(chatUrl, chatName, key, 10).complete(
                        java.util.List.of(new com.cst3492.ta.llm.Message("user", "Reply OK")), 0, 16);
                chatOk = true;
            } catch (Exception ex) { chatOk = false; }
            try {
                new com.cst3492.ta.embedding.HttpEmbeddingProvider(embedUrl, embedName, embedKey)
                        .embedAll(java.util.List.of("connection test"));
                embedOk = true;
            } catch (Exception ex) { embedOk = false; }
            String result = "Chat endpoint: " + (chatOk ? "OK" : "FAILED")
                    + "\nEmbedding endpoint: " + (embedOk ? "OK" : "FAILED");
            SwingUtilities.invokeLater(() -> Messages.showInfoMessage(result, "CST3492 TA Connection Test"));
        });
    }

    @Override
    public boolean isModified() {
        TaSettingsState s = TaSettingsState.getInstance();
        return !equals(chatBaseUrl, s.chatBaseUrl) || !equals(chatModel, s.chatModel)
                || !equals(embeddingBaseUrl, s.embeddingBaseUrl) || !equals(embeddingModel, s.embeddingModel)
                || !equals(materialsPath, s.materialsPath)
                || topK != null && (int) topK.getValue() != s.topK
                || threshold != null && (double) threshold.getValue() != s.threshold
                || hybridEnabled != null && hybridEnabled.isSelected() != s.hybridEnabled
                || apiKey != null && !apiKey.getText().isBlank()
                || embeddingApiKey != null && !embeddingApiKey.getText().isBlank();
    }

    @Override
    public void apply() {
        TaSettingsState s = TaSettingsState.getInstance();
        s.chatBaseUrl = chatBaseUrl.getText().trim();
        s.chatModel = chatModel.getText().trim();
        s.embeddingBaseUrl = embeddingBaseUrl.getText().trim();
        s.embeddingModel = embeddingModel.getText().trim();
        s.materialsPath = materialsPath.getText().trim();
        s.topK = (int) topK.getValue();
        s.threshold = (double) threshold.getValue();
        s.hybridEnabled = hybridEnabled.isSelected();
        if (!apiKey.getText().isBlank()) {
            TaSettingsState.setChatApiKey(apiKey.getText().trim());
            apiKey.setText("");
        }
        if (!embeddingApiKey.getText().isBlank()) {
            TaSettingsState.setEmbeddingApiKey(embeddingApiKey.getText().trim());
            embeddingApiKey.setText("");
        }
    }

    @Override
    public void reset() {
        TaSettingsState s = TaSettingsState.getInstance();
        chatBaseUrl.setText(s.chatBaseUrl);
        chatModel.setText(s.chatModel);
        embeddingBaseUrl.setText(s.embeddingBaseUrl);
        embeddingModel.setText(s.embeddingModel);
        materialsPath.setText(s.materialsPath);
        topK.setValue(s.topK);
        threshold.setValue(s.threshold);
        hybridEnabled.setSelected(s.hybridEnabled);
        apiKey.setText("");
        embeddingApiKey.setText("");
    }

    private static boolean equals(JTextField field, String value) {
        return field == null || field.getText().trim().equals(value == null ? "" : value.trim());
    }
}
