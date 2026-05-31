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
package io.github.malonetalk.service.semantic.column;

import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticUpdateRequest;
import java.util.List;

public interface ColumnSemanticService {

    PageResponse<ColumnSemanticResponse> getColumnPage(
            Integer datasourceId,
            String tableName,
            PageRequest pageRequest,
            String keyword,
            String sortOrder);

    void updateColumnSemantic(
            Integer datasourceId, String tableName, ColumnSemanticUpdateRequest request);

    void resetColumnSemantic(Integer datasourceId, String tableName, String columnName);

    int resetColumnSemantics(Integer datasourceId, String tableName, List<String> columnNames);
}
