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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.utils.RequestAssert;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LogicalTableRelationHelper {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public LogicalTableRelationHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> normalizeColumnNames(List<String> columnNames, String fieldName) {
        RequestAssert.requireNotEmpty(columnNames, fieldName + " cannot be empty.");
        Set<String> uniqueKeys = new HashSet<>();
        List<String> normalizedColumns = new ArrayList<>(columnNames.size());
        // 按不区分大小写的名称判重，序列化时保留列名原有大小写。
        for (String columnName : columnNames) {
            String normalizedColumnName =
                    RequestAssert.requireNotBlank(
                            columnName, fieldName + " contains a blank column name.");
            String uniqueKey = normalizedColumnName.toLowerCase(Locale.ROOT);
            if (!uniqueKeys.add(uniqueKey)) {
                throw BusinessException.of(
                        ErrorCode.BAD_REQUEST,
                        fieldName + " contains duplicate column: " + normalizedColumnName);
            }
            normalizedColumns.add(normalizedColumnName);
        }
        return List.copyOf(normalizedColumns);
    }

    public String toJson(List<String> columnNames, String fieldName) {
        try {
            return objectMapper.writeValueAsString(normalizeColumnNames(columnNames, fieldName));
        } catch (JsonProcessingException e) {
            throw BusinessException.of(
                    ErrorCode.OPERATION_FAILED, "Failed to serialize relation columns.", e);
        }
    }

    public List<String> fromJson(String columnNamesJson, String fieldName) {
        String normalizedJson =
                RequestAssert.requireNotBlank(
                        columnNamesJson, fieldName + " json cannot be blank.");
        try {
            return normalizeColumnNames(
                    objectMapper.readValue(normalizedJson, STRING_LIST_TYPE), fieldName);
        } catch (JsonProcessingException e) {
            throw BusinessException.of(
                    ErrorCode.BAD_REQUEST,
                    "Failed to parse relation columns from " + fieldName + ".",
                    e);
        }
    }
}
