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
package io.github.malonetalk.entity;

import java.time.LocalDateTime;

public class LogicalTableRelation {

    private Integer id;
    private Integer datasourceId;
    private String sourceTableName;
    private String sourceColumnNamesJson;
    private String sourceColumnSignature;
    private String targetTableName;
    private String targetColumnNamesJson;
    private String targetColumnSignature;
    private String relationType;
    private String description;
    private Boolean isEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(Integer datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getSourceTableName() {
        return sourceTableName;
    }

    public void setSourceTableName(String sourceTableName) {
        this.sourceTableName = sourceTableName;
    }

    public String getSourceColumnNamesJson() {
        return sourceColumnNamesJson;
    }

    public void setSourceColumnNamesJson(String sourceColumnNamesJson) {
        this.sourceColumnNamesJson = sourceColumnNamesJson;
    }

    public String getSourceColumnSignature() {
        return sourceColumnSignature;
    }

    public void setSourceColumnSignature(String sourceColumnSignature) {
        this.sourceColumnSignature = sourceColumnSignature;
    }

    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
    }

    public String getTargetColumnNamesJson() {
        return targetColumnNamesJson;
    }

    public void setTargetColumnNamesJson(String targetColumnNamesJson) {
        this.targetColumnNamesJson = targetColumnNamesJson;
    }

    public String getTargetColumnSignature() {
        return targetColumnSignature;
    }

    public void setTargetColumnSignature(String targetColumnSignature) {
        this.targetColumnSignature = targetColumnSignature;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
