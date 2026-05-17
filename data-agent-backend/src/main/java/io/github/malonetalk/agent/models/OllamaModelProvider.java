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
package io.github.malonetalk.agent.models;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OllamaModelProvider implements ModelProvider {
    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public Model createModel(ModelConfig config) {
        OllamaChatModel.Builder builder = OllamaChatModel.builder().modelName(config.getName());
        if (StringUtils.hasText(config.getBaseUrl())) {
            builder.baseUrl(config.getBaseUrl());
        } else {
            builder.baseUrl("http://localhost:11434");
        }
        return builder.build();
    }
}
