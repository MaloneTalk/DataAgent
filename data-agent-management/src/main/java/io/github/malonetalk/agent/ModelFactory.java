package io.github.malonetalk.agent;

import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ModelFactory {

    @Value("${agentscope.model.provider:dashscope}")
    private String provider;

    @Value("${agentscope.api.key:}")
    private String apiKey;

    @Value("${agentscope.model.name:qwen3-max}")
    private String modelName;

    @Value("${agentscope.model.base-url:}")
    private String baseUrl;

    public ModelFactory() {}

    public ModelFactory(String provider, String apiKey, String modelName, String baseUrl) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
    }

    public Model createModel() {
        return createModel(apiKey, modelName);
    }

    public Model createModel(String apiKey, String modelName) {
        return createModel(apiKey, modelName, baseUrl);
    }

    public Model createModel(String apiKey, String modelName, String baseUrl) {
        return switch (provider.toLowerCase()) {
            case "dashscope" ->
                    DashScopeChatModel.builder().apiKey(apiKey).modelName(modelName).stream(true)
                            .build();
            case "openai" -> {
                OpenAIChatModel.Builder builder =
                        OpenAIChatModel.builder().apiKey(apiKey).modelName(modelName).stream(true);
                if (baseUrl != null && !baseUrl.isEmpty()) {
                    builder.baseUrl(baseUrl);
                }
                yield builder.build();
            }
            case "anthropic" ->
                    AnthropicChatModel.builder().apiKey(apiKey).modelName(modelName).stream(true)
                            .build();

            case "ollama" -> {
                OllamaChatModel.Builder builder = OllamaChatModel.builder().modelName(modelName);
                if (baseUrl != null && !baseUrl.isEmpty()) {
                    builder.baseUrl(baseUrl);
                } else {
                    builder.baseUrl("http://localhost:11434");
                }
                yield builder.build();
            }
            default ->
                    throw new IllegalArgumentException("Unsupported model provider: " + provider);
        };
    }

    public String getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }
}
