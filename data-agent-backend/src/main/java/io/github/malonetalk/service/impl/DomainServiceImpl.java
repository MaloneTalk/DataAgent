package io.github.malonetalk.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.DomainCreateRequest;
import io.github.malonetalk.dto.DomainPageQuery;
import io.github.malonetalk.dto.DomainUpdateRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.DomainInfo;
import io.github.malonetalk.mapper.DomainInfoMapper;
import io.github.malonetalk.service.DomainService;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DomainServiceImpl implements DomainService {

    private final DomainInfoMapper domainInfoMapper;

    @Override
    public PageResponse<DomainInfo> getDomainPage(DomainPageQuery query) {
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        SemanticUtils.validateSortOrder(query.sortOrder());
        boolean sortDescending =
                SemanticConstants.SORT_ORDER_DESC.equalsIgnoreCase(query.sortOrder());
        PageHelper.startPage(pageNumber, pageSize);
        @SuppressWarnings("unchecked")
        Page<DomainInfo> page =
                (Page<DomainInfo>)
                        domainInfoMapper.selectPage(
                                new DomainPageQuery(
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.normalizeBlankToNull(query.keyword()),
                                        query.sortOrder()),
                                sortDescending);
        List<DomainInfo> items = page.getResult();
        long total = page.getTotal();
        return PageResponse.of(items, total, pageNumber, pageSize);
    }

    @Override
    public DomainInfo findById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        return domainInfoMapper.selectAll().stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public DomainInfo create(DomainCreateRequest request) {
        String normalizedName = SemanticUtils.requireName(request.name(), "领域名称");
        DomainInfo existing = domainInfoMapper.selectByName(normalizedName);
        if (existing != null) {
            throw new IllegalArgumentException("领域名称已存在: " + normalizedName);
        }
        DomainInfo domainInfo = new DomainInfo();
        domainInfo.setName(normalizedName);
        domainInfo.setDescription(SemanticUtils.normalizeBlankToNull(request.description()));
        domainInfo.setCreateTime(LocalDateTime.now());
        domainInfo.setUpdateTime(LocalDateTime.now());
        domainInfoMapper.insert(domainInfo);
        return domainInfo;
    }

    @Override
    public DomainInfo update(Integer id, DomainUpdateRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        DomainInfo existing = findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("领域不存在: id=" + id);
        }
        String normalizedName = SemanticUtils.requireName(request.name(), "领域名称");
        DomainInfo nameConflict = domainInfoMapper.selectByName(normalizedName);
        if (nameConflict != null && !nameConflict.getId().equals(id)) {
            throw new IllegalArgumentException("领域名称已存在: " + normalizedName);
        }
        existing.setName(normalizedName);
        existing.setDescription(SemanticUtils.normalizeBlankToNull(request.description()));
        existing.setUpdateTime(LocalDateTime.now());
        domainInfoMapper.update(existing);
        return existing;
    }

    @Override
    public void delete(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        DomainInfo existing = findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("领域不存在: id=" + id);
        }
        domainInfoMapper.deleteByIds(List.of(id));
    }

    @Override
    public List<String> listDomainNames() {
        return domainInfoMapper.selectAll().stream()
                .map(DomainInfo::getName)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }
}
