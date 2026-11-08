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
package io.github.malonetalk.agent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.util.JsonUtils;
import io.github.malonetalk.agent.ToolCallContext;
import io.github.malonetalk.model.bo.DatasourceInfoBo;
import io.github.malonetalk.model.bo.ToolResultBo;
import io.github.malonetalk.model.po.DatasourcePo;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GetDatasourcesTool implements MarkAgentTool {

    private final ToolDatasourceResolver datasourceResolver;
    private final ToolExceptionMapper toolExceptionMapper;

    @Tool(
            name = ToolCallConstants.GET_DATASOURCES,
            converter = ToolResultBoConverter.class,
            description =
                    """
                    List the datasources available in this conversation. Call this first when
                    the user has not chosen a datasource or when multiple datasources may exist.
                    """)
    public ToolResultBo getDatasources(ToolCallContext ctx) {
        return toolExceptionMapper.runResult(
                () -> {
                    List<DatasourcePo> datasources = datasourceResolver.accessible(ctx);
                    List<DatasourceInfoBo> infos =
                            datasources.stream()
                                    .map(
                                            datasource ->
                                                    new DatasourceInfoBo(
                                                            datasource.getId(),
                                                            datasource.getName(),
                                                            datasource.getType(),
                                                            datasource.getDatabaseName(),
                                                            datasource.getDescription()))
                                    .toList();
                    return ToolResultBo.of(JsonUtils.getJsonCodec().toJson(infos));
                });
    }
}
