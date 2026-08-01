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

import io.github.malonetalk.entity.MetricInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MetricInfoMapper {

    int insert(MetricInfo metricInfo);

    int update(MetricInfo metricInfo);

    int deleteByIds(@Param("ids") List<Integer> ids);

    int restoreById(MetricInfo metricInfo);

    MetricInfo selectById(@Param("id") Integer id);

    MetricInfo selectByKey(
            @Param("datasourceId") Integer datasourceId, @Param("metricKey") String metricKey);

    MetricInfo selectAnyByKey(
            @Param("datasourceId") Integer datasourceId, @Param("metricKey") String metricKey);

    List<MetricInfo> selectAllByDatasource(@Param("datasourceId") Integer datasourceId);

    List<MetricInfo> matchByHint(
            @Param("datasourceId") Integer datasourceId, @Param("hint") String hint);

    List<MetricInfo> suggest(
            @Param("datasourceId") Integer datasourceId, @Param("limit") int limit);
}
