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

import io.github.malonetalk.dto.TableExportPageQuery;
import io.github.malonetalk.dto.TableExportResponse;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.Datasource;
import java.nio.file.Path;

public interface TableExportService {

    TableExportResponse create(String sessionId, Datasource datasource, String title, String sql);

    PageResponse<TableExportResponse> getExportPage(TableExportPageQuery query, Integer userId);

    Path findDownload(String id, Integer userId);

    void deleteById(String id, Integer userId);
}
