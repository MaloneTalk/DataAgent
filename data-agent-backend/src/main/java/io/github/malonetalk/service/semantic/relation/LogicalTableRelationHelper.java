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
package io.github.malonetalk.service.semantic.relation;

import static io.github.malonetalk.common.SemanticConstants.RELATION_GROUP_SEPARATOR;
import static io.github.malonetalk.common.SemanticConstants.RELATION_KEY_SEPARATOR;
import static io.github.malonetalk.common.SemanticConstants.RELATION_TABLE_COLUMN_SEPARATOR;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.utils.AssertUtils;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LogicalTableRelationHelper {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public LogicalTableRelationHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalizeTableName(String tableName, String missingMessage) {
        return SemanticUtils.normalizeObjectName(tableName, missingMessage);
    }

    public List<String> normalizeColumnNames(List<String> columnNames, String fieldName) {
        AssertUtils.requireNotEmpty(columnNames, fieldName + " cannot be empty.");
        Set<String> uniqueKeys = new LinkedHashSet<>();
        Set<String> normalizedColumns = new LinkedHashSet<>();
        for (String columnName : columnNames) {
            String normalizedColumnName =
                    AssertUtils.requireNotBlank(
                            columnName, fieldName + " contains a blank column name.");
            String uniqueKey =
                    SemanticUtils.normalizeObjectName(
                            normalizedColumnName,
                            "Missing columnName while normalizing logical relation columns.");
            if (!uniqueKeys.add(uniqueKey)) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        fieldName + " contains duplicate column: " + normalizedColumnName);
            }
            normalizedColumns.add(normalizedColumnName);
        }
        return normalizedColumns.stream().toList();
    }

    public String buildColumnSignature(List<String> columnNames) {
        return normalizeColumnNames(columnNames, "columnNames").stream()
                .map(
                        columnName ->
                                SemanticUtils.normalizeObjectName(
                                        columnName,
                                        "Missing columnName while building column signature."))
                .reduce((left, right) -> left + RELATION_KEY_SEPARATOR + right)
                .orElse("");
    }

    public String buildRelationKey(
            String sourceTableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames) {
        return SemanticUtils.normalizeObjectName(
                        sourceTableName, "Missing sourceTableName for logical relation key.")
                + RELATION_TABLE_COLUMN_SEPARATOR
                + buildColumnSignature(sourceColumnNames)
                + RELATION_GROUP_SEPARATOR
                + SemanticUtils.normalizeObjectName(
                        targetTableName, "Missing targetTableName for logical relation key.")
                + RELATION_TABLE_COLUMN_SEPARATOR
                + buildColumnSignature(targetColumnNames);
    }

    public String toJson(List<String> columnNames) {
        try {
            return objectMapper.writeValueAsString(
                    normalizeColumnNames(columnNames, "columnNames"));
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ErrorCode.OPERATION_FAILED, "Failed to serialize relation columns.");
        }
    }

    public List<String> fromJson(String columnNamesJson, String fieldName) {
        String normalizedJson =
                AssertUtils.requireNotBlank(
                        columnNamesJson, fieldName + " json cannot be blank.");
        try {
            return normalizeColumnNames(
                    objectMapper.readValue(normalizedJson, STRING_LIST_TYPE), fieldName);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Failed to parse relation columns from " + fieldName + ".");
        }
    }
}
