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
package io.github.malonetalk.service.semantic.sync;

import io.github.malonetalk.dto.semantic.SyncTableResult;
import io.github.malonetalk.dto.semantic.SyncTableSemanticsResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SemanticSyncResultService {

    public SyncTableSemanticsResponse summarize(List<SyncTableResult> results) {
        int addedTables = 0;
        int reactivatedTables = 0;
        int updatedTables = 0;
        int missingTablesMarked = 0;
        int addedColumns = 0;
        int reactivatedColumns = 0;
        int updatedColumns = 0;
        int missingColumnsMarked = 0;

        for (SyncTableResult result : results) {
            if (result.tableAdded()) {
                addedTables++;
            }
            if (result.tableReactivated()) {
                reactivatedTables++;
            }
            if (result.tableUpdated()) {
                updatedTables++;
            }
            if (result.tableMarkedMissing()) {
                missingTablesMarked++;
            }
            addedColumns += result.addedColumns();
            reactivatedColumns += result.reactivatedColumns();
            updatedColumns += result.updatedColumns();
            missingColumnsMarked += result.missingColumnsMarked();
        }

        return new SyncTableSemanticsResponse(
                addedTables,
                reactivatedTables,
                updatedTables,
                missingTablesMarked,
                addedColumns,
                reactivatedColumns,
                updatedColumns,
                missingColumnsMarked,
                results);
    }
}
