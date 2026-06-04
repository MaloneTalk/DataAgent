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
