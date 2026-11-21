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
import io.github.malonetalk.enums.MetricCalculationType;
import io.github.malonetalk.enums.MetricValueType;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.MetricInfoMapper;
import io.github.malonetalk.model.bo.MetricInfoBo;
import io.github.malonetalk.model.bo.MetricLookupBo;
import io.github.malonetalk.model.converter.MetricConverter;
import io.github.malonetalk.model.converter.MetricPromptConverter;
import io.github.malonetalk.model.dto.MetricCreateDto;
import io.github.malonetalk.model.dto.MetricQueryDto;
import io.github.malonetalk.model.dto.MetricUpdateDto;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.model.po.MetricInfoPo;
import io.github.malonetalk.service.csv.CsvAccessService;
import io.github.malonetalk.utils.MetricAliasUtils;
import io.github.malonetalk.utils.SemanticUtils;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MetricInfoMapper metricInfoMapper;
    private final MetricConverter metricConverter;
    private final MetricPromptConverter metricPromptConverter;
    private final DatasourceWriteLockService datasourceLocks;
    private final CsvAccessService csvAccess;
    private final MetricTableReferenceService metricReferences;

    @Override
    public MetricLookupBo lookupCaliber(
            Integer datasourceId, Optional<String> metricKey, Optional<String> hint) {
        var key = metricKey.map(String::strip).filter(value -> !value.isEmpty());
        if (key.isPresent()) {
            List<MetricInfoBo> exact =
                    toBoList(metricInfoMapper.selectAllByKey(datasourceId, key.get()));
            if (exact.size() > 1) {
                throw BusinessException.of(
                        ErrorCode.DATA_CONFLICT, "同一数据源存在重复的有效指标 key: " + key.get());
            }
            return exact.isEmpty() ? notFound(datasourceId, key.get()) : resolved(exact.get(0));
        }
        String query =
                hint.map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .orElseThrow(
                                () ->
                                        BusinessException.of(
                                                ErrorCode.BAD_REQUEST, "metric_key 和 hint 至少填写一项"));
        var matched =
                MetricMatcher.match(
                        toBoList(metricInfoMapper.selectByDatasource(datasourceId)), query);
        if (matched.candidates().isEmpty()) return notFound(datasourceId, query);
        if (matched.candidates().size() == 1) return resolved(matched.candidates().get(0));
        return new MetricLookupBo(
                MetricLookupBo.Status.AMBIGUOUS,
                metricPromptConverter.formatCandidates(matched.candidates(), matched.conflicting()),
                matched.candidates(),
                matched.conflicting());
    }

    private MetricLookupBo resolved(MetricInfoBo metric) {
        return new MetricLookupBo(
                MetricLookupBo.Status.RESOLVED,
                metricPromptConverter.convert(metric),
                List.of(metric),
                false);
    }

    private MetricLookupBo notFound(Integer datasourceId, String query) {
        return new MetricLookupBo(
                MetricLookupBo.Status.NOT_FOUND,
                metricPromptConverter.formatNotFound(
                        query, toBoList(metricInfoMapper.suggest(datasourceId))),
                List.of(),
                false);
    }

    @Override
    @Transactional
    public MetricInfoBo create(MetricCreateDto dto) {
        DatasourcePo datasource = datasourceLocks.lock(dto.datasourceId());
        Integer dsId = datasource.getId();
        String key = SemanticUtils.normalizeObjectName(dto.metricKey(), "指标 key 不能为空");
        if (metricInfoMapper.selectByKey(dsId, key) != null) {
            throw new IllegalArgumentException("指标 key 已存在: " + key);
        }
        MetricInfoPo po = metricConverter.toPoForInsert(dto);
        po.setDatasourceId(dsId);
        po.setMetricKey(key);
        po.setAliases(MetricAliasUtils.canonicalize(po.getAliases()));
        normalizeBusinessProperties(po);
        requireReadyCsvReferences(po, datasource);
        metricInfoMapper.insert(po);
        return metricConverter.toBo(po);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MetricInfoBo update(Integer id, MetricUpdateDto dto) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        // Re-read after locking: another edit may have committed while locating the datasource.
        DatasourcePo datasource = datasourceLocks.lock(requireById(id).getDatasourceId());
        MetricInfoPo po =
                Optional.ofNullable(
                                metricInfoMapper.selectOne(
                                        Wrappers.<MetricInfoPo>lambdaQuery()
                                                .eq(MetricInfoPo::getId, id)
                                                .last("FOR UPDATE")))
                        .orElseThrow(() -> new IllegalArgumentException("指标不存在: id=" + id));
        metricConverter.toPoForUpdate(dto, po);
        po.setAliases(MetricAliasUtils.canonicalize(po.getAliases()));
        normalizeBusinessProperties(po);
        if (!StringUtils.hasText(dto.name())) {
            po.setName(null);
        }
        requireReadyCsvReferences(po, datasource);
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
    public IPage<MetricInfoBo> page(MetricQueryDto dto) {
        long current = dto.getPage() == null ? 1L : dto.getPage();
        long size = dto.getPageSize() == null ? 20L : dto.getPageSize();
        Page<MetricInfoPo> page =
                metricInfoMapper.selectPage(
                        new Page<>(current, size),
                        Wrappers.<MetricInfoPo>lambdaQuery()
                                .eq(MetricInfoPo::getDatasourceId, dto.getDatasourceId())
                                .orderByAsc(MetricInfoPo::getName, MetricInfoPo::getId));
        return page.convert(metricConverter::toBo);
    }

    private void requireReadyCsvReferences(MetricInfoPo metric, DatasourcePo datasource) {
        if (!csvAccess.managed(datasource)) return;
        var readyTables =
                csvAccess.ready(datasource).stream()
                        .map(task -> SemanticUtils.normalizeIdentifier(task.getPhysicalTableName()))
                        .collect(Collectors.toSet());
        metricReferences.requireReadyTables(metric, readyTables);
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

    private void normalizeBusinessProperties(MetricInfoPo po) {
        po.setCalculationType(
                Optional.ofNullable(po.getCalculationType())
                        .orElse(MetricCalculationType.EXPRESSION));
        BigDecimal multiplier =
                Optional.ofNullable(po.getResultMultiplier()).orElse(BigDecimal.ONE);
        if (multiplier.signum() <= 0
                || multiplier.scale() > 12
                || multiplier.precision() - multiplier.scale() > 12) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "结果倍率必须为正数，最多12位整数和12位小数");
        }
        po.setResultMultiplier(multiplier);
        po.setUnit(SemanticUtils.trimToNull(po.getUnit()));
        if (po.getValueType() == MetricValueType.PERCENT) {
            if (multiplier.compareTo(BigDecimal.ONE) != 0) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "百分比指标的结果倍率必须为1，展示时再乘以100");
            }
            po.setUnit("1");
        } else if (po.getValueType() != null && !StringUtils.hasText(po.getUnit())) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "请填写标准结果单位");
        }
        if (po.getValueType() != MetricValueType.MONEY) {
            po.setCurrencyCode(null);
            return;
        }
        String currency =
                Optional.ofNullable(SemanticUtils.trimToNull(po.getCurrencyCode()))
                        .map(code -> code.toUpperCase(Locale.ROOT))
                        .orElseThrow(
                                () -> BusinessException.of(ErrorCode.BAD_REQUEST, "金额指标必须填写币种"));
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "不支持的币种代码: " + currency);
        }
        po.setCurrencyCode(currency);
    }
}
