/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
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

    public Model createModel() {
        return createModel(apiKey, modelName, baseUrl);
    }

    private Model createModel(String apiKey, String modelName, String baseUrl) {
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
}
