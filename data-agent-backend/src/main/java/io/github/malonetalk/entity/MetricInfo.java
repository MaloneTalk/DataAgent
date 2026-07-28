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
package io.github.malonetalk.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MetricInfo {

    private Integer id;
    private Integer datasourceId;
    private String metricKey;
    private String name;
    private String aliases;
    private String measureExpr;
    private String filters;
    private String timeField;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDeleted;

    /**
     * 渲染指标口径文本,供大模型在合成 SQL 时消费。
     * 仅依赖实体自身字段,无外部依赖,因此放在实体内部而非 Service。
     */
    public String toCaliberText() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("指标口径: %s (%s)%n", name, metricKey));
        if (aliases != null) {
            sb.append(String.format("同义词: %s%n", aliases));
        }
        if (measureExpr != null) {
            sb.append(String.format("度量: %s%n", measureExpr));
        }
        if (filters != null) {
            sb.append(String.format("过滤: %s%n", filters));
        }
        if (timeField != null) {
            sb.append(String.format("时间字段: %s%n", timeField));
        }
        if (description != null) {
            sb.append(String.format("说明: %s%n", description));
        }
        return sb.toString();
    }
}
