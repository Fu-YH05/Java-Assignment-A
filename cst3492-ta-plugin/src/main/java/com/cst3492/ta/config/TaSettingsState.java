package com.cst3492.ta.config;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Non-secret settings live in the component state; the API key never leaves PasswordSafe.
 */
@State(name = "CstTaSettings", storages = @Storage("cst3492-ta.xml"))
public class TaSettingsState implements PersistentStateComponent<TaSettingsState> {

    public String chatBaseUrl = "https://api.deepseek.com";
    public String chatModel = "deepseek-chat";
    public String embeddingBaseUrl = "https://api.siliconflow.cn/v1";
    public String embeddingModel = "BAAI/bge-m3";
    public String materialsPath = "";
    public int topK = 5;
    public double threshold = 0.35;
    public boolean hybridEnabled = false;
    public int maxContextTokens = 4000;
    public double temperature = 0.2;
    public int timeoutSeconds = 60;

    private static CredentialAttributes attributes() {
        return new CredentialAttributes("CST3492 TA", null, TaSettingsState.class, false);
    }

    public static String getApiKey() {
        Credentials credentials = PasswordSafe.getInstance().get(attributes());
        return credentials == null ? null : credentials.getPasswordAsString();
    }

    public static String getChatApiKey() {
        Credentials credentials = PasswordSafe.getInstance().get(serviceAttributes("chat"));
        return credentials == null ? getApiKey() : credentials.getPasswordAsString();
    }

    public static String getEmbeddingApiKey() {
        Credentials credentials = PasswordSafe.getInstance().get(serviceAttributes("embedding"));
        return credentials == null ? null : credentials.getPasswordAsString();
    }

    private static CredentialAttributes serviceAttributes(String service) {
        return new CredentialAttributes("CST3492 TA " + service, null, TaSettingsState.class, false);
    }

    public static void setChatApiKey(String key) {
        setServiceKey("chat", key);
    }

    public static void setEmbeddingApiKey(String key) {
        setServiceKey("embedding", key);
    }

    private static void setServiceKey(String service, String key) {
        PasswordSafe.getInstance().set(serviceAttributes(service), key == null || key.isBlank()
                ? null : new Credentials("api-key", key));
    }

    public static void setApiKey(String key) {
        PasswordSafe.getInstance().set(attributes(), key == null || key.isBlank()
                ? null : new Credentials("api-key", key));
    }

    public static TaSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(TaSettingsState.class);
    }

    @Override
    public @Nullable TaSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull TaSettingsState state) {
        this.chatBaseUrl = state.chatBaseUrl;
        this.chatModel = state.chatModel;
        this.embeddingBaseUrl = state.embeddingBaseUrl;
        this.embeddingModel = state.embeddingModel;
        this.materialsPath = state.materialsPath;
        this.topK = state.topK;
        this.threshold = state.threshold;
        this.hybridEnabled = state.hybridEnabled;
        this.maxContextTokens = state.maxContextTokens;
        this.temperature = state.temperature;
        this.timeoutSeconds = state.timeoutSeconds;
    }
}
