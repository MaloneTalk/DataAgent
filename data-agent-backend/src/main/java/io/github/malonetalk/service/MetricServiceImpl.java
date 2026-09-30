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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.malonetalk.mapper.MetricInfoMapper;
import io.github.malonetalk.model.bo.MetricInfoBo;
import io.github.malonetalk.model.converter.MetricConverter;
import io.github.malonetalk.model.dto.BaseBatchQueryDto;
import io.github.malonetalk.model.dto.MetricCreateDto;
import io.github.malonetalk.model.dto.MetricUpdateDto;
import io.github.malonetalk.model.po.MetricInfoPo;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_CANDIDATES = 3;
    private static final String ALIAS_SEPARATORS = "[,，、;；/|]";

    private final MetricInfoMapper metricInfoMapper;
    private final MetricConverter metricConverter;
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
        String query = SemanticUtils.trimToNull(hint);
        if (query == null) {
            return "缺少指标描述,无法查询口径。";
        }
        List<MetricInfoBo> candidates =
                match(toBoList(metricInfoMapper.selectByDatasource(dsId)), query);
        if (candidates.isEmpty()) {
            List<MetricInfoBo> suggestions = toBoList(metricInfoMapper.suggest(dsId));
            log.warn("指标口径未命中: hint={}", query);
            return formatNotFound(query, suggestions);
        }
        MetricInfoBo best = candidates.get(0);
        if (candidates.size() == 1) {
            return best.toCaliberText();
        }
        return formatCaliberWithAlternatives(best, candidates.subList(1, candidates.size()));
    }

    /**
     * 反向包含匹配:拿指标的每个名字去用户的话里找,而不是拿整句话去别名串里找。
     * 后者要求模型先把问题提炼成干净的指标名,而它通常直接把用户原话整句传进来,必然落空。
     * 多个指标命中时按「命中的词有多长」降序——命中"销售额"比命中"额"可信。
     */
    static List<MetricInfoBo> match(List<MetricInfoBo> metrics, String query) {
        return metrics.stream()
                .map(m -> new Hit(m, longestMatchedTerm(m, query)))
                .filter(Hit::matched)
                .sorted(Comparator.comparingInt(Hit::length).reversed())
                .limit(MAX_CANDIDATES)
                .map(Hit::metric)
                .toList();
    }

    private static int longestMatchedTerm(MetricInfoBo metric, String query) {
        return termsOf(metric).stream()
                .filter(term -> SemanticUtils.containsIgnoreCase(query, term))
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    /** name + aliases 拆成候选词。过短的别名(如"额")会误伤大量无关问句,直接丢弃。 */
    private static List<String> termsOf(MetricInfoBo metric) {
        Stream<String> aliases =
                metric.getAliases() == null
                        ? Stream.empty()
                        : Arrays.stream(metric.getAliases().split(ALIAS_SEPARATORS));
        return Stream.concat(Stream.of(metric.getName()), aliases)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(term -> term.length() >= MIN_TERM_LENGTH)
                .distinct()
                .toList();
    }

    private record Hit(MetricInfoBo metric, int length) {
        boolean matched() {
            return length > 0;
        }
    }

    private String formatNotFound(String hint, List<MetricInfoBo> suggestions) {
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

    private String formatCaliberWithAlternatives(MetricInfoBo best, List<MetricInfoBo> others) {
        return best.toCaliberText()
                + String.format(
                        "%n（注意:有多个相近指标,请确认你要的是\"%s\"。其他候选: %s）%n",
                        best.getName(),
                        others.stream()
                                .map(m -> m.getName() + "(" + m.getMetricKey() + ")")
                                .collect(Collectors.joining(", ")));
    }

    @Override
    public MetricInfoBo create(MetricCreateDto dto) {
        Integer dsId = activeDatasourceId();
        String key = SemanticUtils.normalizeObjectName(dto.metricKey(), "指标 key 不能为空");
        if (metricInfoMapper.selectByKey(dsId, key) != null) {
            throw new IllegalArgumentException("指标 key 已存在: " + key);
        }
        MetricInfoPo po = metricConverter.toPoForInsert(dto);
        po.setDatasourceId(dsId);
        po.setMetricKey(key);
        metricInfoMapper.insert(po);
        return metricConverter.toBo(po);
    }

    @Override
    public MetricInfoBo update(Integer id, MetricUpdateDto dto) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        MetricInfoPo po = requireById(id);
        metricConverter.toPoForUpdate(dto, po);
        if(!StringUtils.hasText(dto.name())) {
            po.setName(null);
        }
        metricInfoMapper.updateById(po);
        return metricConverter.toBo(po);
    }

    @Override
    public void delete(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        if (metricInfoMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("指标不存在: id=" + id);
        }
    }

    @Override
    public MetricInfoBo getById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        return metricConverter.toBo(requireById(id));
    }

    @Override
    public IPage<MetricInfoBo> page(BaseBatchQueryDto dto) {
        long current = dto.getPage() == null ? 1L : dto.getPage();
        long size = dto.getPageSize() == null ? 20L : dto.getPageSize();
        Integer dsId = activeDatasourceId();
        Page<MetricInfoPo> page =
                metricInfoMapper.selectPage(
                        new Page<>(current, size),
                        Wrappers.<MetricInfoPo>lambdaQuery()
                                .eq(MetricInfoPo::getDatasourceId, dsId)
                                .orderByAsc(MetricInfoPo::getName, MetricInfoPo::getId));
        return page.convert(metricConverter::toBo);
    }

    private MetricInfoPo requireById(Integer id) {
        MetricInfoPo po = metricInfoMapper.selectById(id);
        if (po == null) {
            throw new IllegalArgumentException("指标不存在: id=" + id);
        }
        return po;
    }

    private List<MetricInfoBo> toBoList(List<MetricInfoPo> pos) {
        return pos.stream().map(metricConverter::toBo).toList();
    }
}
