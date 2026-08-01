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
package io.github.malonetalk.service.semantic;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.DomainCreateRequest;
import io.github.malonetalk.dto.DomainPageQuery;
import io.github.malonetalk.dto.DomainUpdateRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.DomainInfo;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.mapper.DomainInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.utils.RequestAssert;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DomainServiceImpl implements DomainService {

    private final DomainInfoMapper domainInfoMapper;
    private final TableInfoMapper tableInfoMapper;

    @Override
    public PageResponse<DomainInfo> getDomainPage(DomainPageQuery query) {
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        boolean sortDescending = SemanticUtils.isDescendingSort(query.sortOrder());
        PageHelper.startPage(pageNumber, pageSize);
        @SuppressWarnings("unchecked")
        Page<DomainInfo> page =
                (Page<DomainInfo>)
                        domainInfoMapper.selectPage(
                                new DomainPageQuery(
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.trimToNull(query.keyword()),
                                        query.sortOrder()),
                                sortDescending);
        List<DomainInfo> items = page.getResult();
        long total = page.getTotal();
        return PageResponse.of(items, total, pageNumber, pageSize);
    }

    @Override
    public DomainInfo findById(Integer id) {
        RequestAssert.requireNonNull(id, "id must not be null.");
        return domainInfoMapper.selectById(id);
    }

    @Override
    public DomainInfo create(DomainCreateRequest request) {
        String normalizedName =
                SemanticUtils.normalizeObjectName(
                        request.name(), "Missing domain name for domain creation.");
        DomainInfo existing = domainInfoMapper.selectByName(normalizedName);
        if (existing != null) {
            throw BusinessException.of(
                    ErrorCode.DOMAIN_NAME_CONFLICT,
                    "Domain name already exists: " + normalizedName);
        }
        DomainInfo domainInfo = new DomainInfo();
        domainInfo.setName(normalizedName);
        domainInfo.setDescription(SemanticUtils.trimToNull(request.description()));
        domainInfo.setCreateTime(LocalDateTime.now());
        domainInfo.setUpdateTime(LocalDateTime.now());
        domainInfoMapper.insert(domainInfo);
        return domainInfo;
    }

    @Override
    public DomainInfo update(Integer id, DomainUpdateRequest request) {
        RequestAssert.requireNonNull(id, "id must not be null.");
        DomainInfo existing = findById(id);
        if (existing == null) {
            throw BusinessException.of(
                    ErrorCode.DOMAIN_NOT_FOUND, "Domain does not exist: id=" + id);
        }
        String normalizedName =
                SemanticUtils.normalizeObjectName(
                        request.name(), "Missing domain name for domain update.");
        DomainInfo nameConflict = domainInfoMapper.selectByName(normalizedName);
        if (nameConflict != null && !nameConflict.getId().equals(id)) {
            throw BusinessException.of(
                    ErrorCode.DOMAIN_NAME_CONFLICT,
                    "Domain name already exists: " + normalizedName);
        }
        existing.setName(normalizedName);
        existing.setDescription(SemanticUtils.trimToNull(request.description()));
        existing.setUpdateTime(LocalDateTime.now());
        domainInfoMapper.update(existing);
        return existing;
    }

    @Override
    public void delete(Integer id) {
        RequestAssert.requireNonNull(id, "id must not be null.");
        DomainInfo existing = findById(id);
        if (existing == null) {
            throw BusinessException.of(
                    ErrorCode.DOMAIN_NOT_FOUND, "Domain does not exist: id=" + id);
        }
        if (SemanticConstants.DEFAULT_DOMAIN.equalsIgnoreCase(existing.getName())) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_IN_USE,
                    "The default domain cannot be deleted: " + existing.getName());
        }
        int referenceCount = tableInfoMapper.countByDomain(existing.getName());
        if (referenceCount > 0) {
            throw BusinessException.of(
                    ErrorCode.RESOURCE_IN_USE, "Domain is currently in use: " + existing.getName());
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
