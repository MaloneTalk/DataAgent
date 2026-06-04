package io.github.malonetalk.service;

import io.github.malonetalk.dto.DomainCreateRequest;
import io.github.malonetalk.dto.DomainPageQuery;
import io.github.malonetalk.dto.DomainUpdateRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.DomainInfo;
import java.util.List;

public interface DomainService {

    PageResponse<DomainInfo> getDomainPage(DomainPageQuery query);

    DomainInfo findById(Integer id);

    DomainInfo create(DomainCreateRequest request);

    DomainInfo update(Integer id, DomainUpdateRequest request);

    void delete(Integer id);

    List<String> listDomainNames();
}
