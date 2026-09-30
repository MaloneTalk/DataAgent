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
package io.github.malonetalk.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 指标口径。审计字段与逻辑删除由 {@link BasePo} 统一承载。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("metric_info")
public class MetricInfoPo extends BasePo {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer datasourceId;
    private String metricKey;
    private String name;
    private String aliases;
    private String measureExpr;
    private String filters;
    private String timeField;
    private String description;
}
