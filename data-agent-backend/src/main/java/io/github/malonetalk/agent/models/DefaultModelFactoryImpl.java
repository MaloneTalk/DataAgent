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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultModelFactoryImpl implements ModelFactory {

    private final Map<String, ModelProvider> providerMap;

    @Autowired
    public DefaultModelFactoryImpl(List<ModelProvider> providers) {
        providerMap =
                providers.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        ModelProvider::provider,
                                        Function.identity(),
                                        (v1, v2) -> {
                                            throw new IllegalStateException(
                                                    "Provider "
                                                            + v1.provider()
                                                            + " should be only one instance! Now"
                                                            + " there are at least two instance: "
                                                            + v1.getClass().getName()
                                                            + " and "
                                                            + v2.getClass().getName());
                                        }));
    }

    @Override
    public Model getInstance(ModelConfig config) {
        if (!StringUtils.hasText(config.getProvider())) {
            throw new IllegalArgumentException("provider should not be empty!");
        }
        ModelProvider provider = providerMap.get(config.getProvider());
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Illegal provider argument: " + config.getProvider());
        }
        return provider.createModel(config);
    }
}
