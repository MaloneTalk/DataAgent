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

import io.github.malonetalk.dto.ReportPageQuery;
import io.github.malonetalk.dto.ReportResponse;
import io.github.malonetalk.dto.pagination.PageResponse;

public interface ReportService {

    /**
     * 保存成功返回主键
     */
    int create(String sessionId, String title, String content);

    PageResponse<ReportResponse> getReportPage(ReportPageQuery query);

    void deleteById(Integer id);

    ReportResponse findById(Integer id);
}
