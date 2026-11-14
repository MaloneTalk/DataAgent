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
package io.github.malonetalk.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.malonetalk.model.po.MetricInfoPo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 指标口径 Mapper：单表查询走 LambdaQueryWrapper，逻辑删除由 {@code @TableLogic} 自动过滤。 */
@Mapper
public interface MetricInfoMapper extends AuditableMapper<MetricInfoPo> {

    default MetricInfoPo selectByKey(Integer datasourceId, String metricKey) {
        return selectOne(
                Wrappers.<MetricInfoPo>lambdaQuery()
                        .eq(MetricInfoPo::getDatasourceId, datasourceId)
                        .apply("LOWER(metric_key) = LOWER({0})", metricKey));
    }

    default List<MetricInfoPo> selectByDatasource(Integer datasourceId) {
        return selectList(
                Wrappers.<MetricInfoPo>lambdaQuery()
                        .eq(MetricInfoPo::getDatasourceId, datasourceId)
                        .orderByAsc(MetricInfoPo::getName, MetricInfoPo::getId));
    }

    default List<MetricInfoPo> suggest(Integer datasourceId) {
        return selectList(
                Wrappers.<MetricInfoPo>lambdaQuery()
                        .eq(MetricInfoPo::getDatasourceId, datasourceId)
                        .orderByAsc(MetricInfoPo::getName, MetricInfoPo::getId)
                        .last("LIMIT 5"));
    }
}
