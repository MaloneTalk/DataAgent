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
package io.github.malonetalk.service;

import io.github.malonetalk.entity.MetricInfo;
import io.github.malonetalk.mapper.MetricInfoMapper;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MetricInfoMapper metricInfoMapper;
    private final DatasourceService datasourceService;

    private Integer activeDatasourceId() {
        return datasourceService
                .getActiveDatasource()
                .orElseThrow(() -> new IllegalStateException("没有可用的数据源"))
                .getId();
    }

    @Override
    public String getCaliberByHint(String hint) {
        Integer dsId = activeDatasourceId();
        String normalized = SemanticUtils.trimToNull(hint);
        if (normalized == null) {
            return "缺少指标描述,无法查询口径。";
        }
        List<MetricInfo> candidates = metricInfoMapper.matchByHint(dsId, normalized);
        if (candidates.isEmpty()) {
            List<MetricInfo> suggestions = metricInfoMapper.suggest(dsId, 5);
            log.warn("指标口径未命中: hint={}", normalized);
            return formatNotFound(normalized, suggestions);
        }
        MetricInfo best = candidates.get(0);
        if (candidates.size() == 1) {
            return formatCaliber(best);
        }
        return formatCaliberWithAlternatives(best, candidates.subList(1, candidates.size()));
    }

    private String formatCaliber(MetricInfo m) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("指标口径: %s (%s)%n", m.getName(), m.getMetricKey()));
        if (m.getAliases() != null) {
            sb.append(String.format("同义词: %s%n", m.getAliases()));
        }
        if (m.getMeasureExpr() != null) {
            sb.append(String.format("度量: %s%n", m.getMeasureExpr()));
        }
        if (m.getFilters() != null) {
            sb.append(String.format("过滤: %s%n", m.getFilters()));
        }
        if (m.getTimeField() != null) {
            sb.append(String.format("时间字段: %s%n", m.getTimeField()));
        }
        if (m.getDescription() != null) {
            sb.append(String.format("说明: %s%n", m.getDescription()));
        }
        return sb.toString();
    }

    private String formatNotFound(String hint, List<MetricInfo> suggestions) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("未找到与\"%s\"匹配的指标口径。请确认指标名称,或在指标口径管理中定义它。%n", hint));
        if (!suggestions.isEmpty()) {
            sb.append("可选的已有指标: ");
            sb.append(
                    suggestions.stream()
                            .map(m -> m.getName() + "(" + m.getMetricKey() + ")")
                            .collect(Collectors.joining(", ")));
        } else {
            sb.append("当前尚未定义任何指标口径。");
        }
        return sb.toString();
    }

    private String formatCaliberWithAlternatives(MetricInfo best, List<MetricInfo> others) {
        StringBuilder sb = new StringBuilder(formatCaliber(best));
        sb.append(
                String.format(
                        "%n（注意:有多个相近指标,请确认你要的是\"%s\"。其他候选: %s）%n",
                        best.getName(),
                        others.stream()
                                .map(m -> m.getName() + "(" + m.getMetricKey() + ")")
                                .collect(Collectors.joining(", "))));
        return sb.toString();
    }

    @Override
    public MetricInfo create(MetricInfo metricInfo) {
        Integer dsId = activeDatasourceId();
        String key = SemanticUtils.normalizeObjectName(metricInfo.getMetricKey(), "指标 key 不能为空");
        if (metricInfoMapper.selectByKey(dsId, key) != null) {
            throw new IllegalArgumentException("指标 key 已存在: " + key);
        }
        metricInfo.setDatasourceId(dsId);
        metricInfo.setMetricKey(key);
        metricInfo.setCreateTime(LocalDateTime.now());
        metricInfo.setUpdateTime(LocalDateTime.now());
        metricInfoMapper.insert(metricInfo);
        return metricInfo;
    }

    @Override
    public MetricInfo update(Integer id, MetricInfo metricInfo) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        MetricInfo existing = getById(id);
        if (SemanticUtils.trimToNull(metricInfo.getName()) != null) {
            existing.setName(metricInfo.getName());
        }
        if (metricInfo.getAliases() != null) {
            existing.setAliases(metricInfo.getAliases());
        }
        if (metricInfo.getMeasureExpr() != null) {
            existing.setMeasureExpr(metricInfo.getMeasureExpr());
        }
        if (metricInfo.getFilters() != null) {
            existing.setFilters(metricInfo.getFilters());
        }
        if (metricInfo.getTimeField() != null) {
            existing.setTimeField(metricInfo.getTimeField());
        }
        if (metricInfo.getDescription() != null) {
            existing.setDescription(metricInfo.getDescription());
        }
        existing.setUpdateTime(LocalDateTime.now());
        metricInfoMapper.update(existing);
        return existing;
    }

    @Override
    public void delete(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        if (getById(id) == null) {
            throw new IllegalArgumentException("指标不存在: id=" + id);
        }
        metricInfoMapper.deleteByIds(List.of(id));
    }

    @Override
    public MetricInfo getById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        MetricInfo m = metricInfoMapper.selectById(id);
        if (m == null) {
            throw new IllegalArgumentException("指标不存在: id=" + id);
        }
        return m;
    }

    @Override
    public MetricInfo getByKey(String metricKey) {
        Integer dsId = activeDatasourceId();
        String key = SemanticUtils.normalizeObjectName(metricKey, "指标 key 不能为空");
        MetricInfo m = metricInfoMapper.selectByKey(dsId, key);
        if (m == null) {
            throw new IllegalArgumentException("指标不存在: key=" + key);
        }
        return m;
    }

    @Override
    public List<MetricInfo> listAll() {
        return metricInfoMapper.selectAllByDatasource(activeDatasourceId());
    }
}
