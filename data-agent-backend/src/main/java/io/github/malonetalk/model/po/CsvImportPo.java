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
package io.github.malonetalk.model.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.malonetalk.enums.CsvImportStatus;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("csv_import")
public class CsvImportPo extends BasePo {
    @TableId private String id;
    private Integer datasourceId;
    private String displayName;
    private String description;
    private String originalFilename;
    private String fileSha256;
    private Long fileSize;
    private String physicalTableName;

    /** Identifies one execution, including a sync-only retry. */
    private String attemptId;

    private Integer submittedBy;
    private String parseOptionsJson;
    private String columnsJson;
    private CsvImportStatus status;
    private Long processedRows;
    private Long rowCount;
    private String errorMessage;
    private LocalDateTime expiresAt;
}
