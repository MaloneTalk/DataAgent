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

import static io.github.malonetalk.common.SemanticConstants.RELATION_KEY_SEPARATOR;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LogicalTableRelationHelper {

    public static final String RELATION_TYPE_FOREIGN_KEY = "foreign_key";
    public static final String RELATION_SOURCE_PHYSICAL = "physical";
    public static final String RELATION_SOURCE_LOGICAL = "logical";

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public LogicalTableRelationHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalizeTableName(String tableName, String fieldName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return tableName.trim();
    }

    public String normalizeIdentifierKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public boolean sameTableName(String left, String right) {
        return normalizeIdentifierKey(left).equals(normalizeIdentifierKey(right));
    }

    public List<String> normalizeColumnNames(List<String> columnNames, String fieldName) {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }

        Set<String> uniqueKeys = new LinkedHashSet<>();
        Set<String> normalizedColumns = new LinkedHashSet<>();
        for (String columnName : columnNames) {
            if (columnName == null || columnName.isBlank()) {
                throw new IllegalArgumentException(fieldName + " contains a blank column name.");
            }
            String normalizedColumnName = columnName.trim();
            String uniqueKey = normalizedColumnName.toLowerCase(Locale.ROOT);
            if (!uniqueKeys.add(uniqueKey)) {
                throw new IllegalArgumentException(
                        fieldName + " contains duplicate column: " + normalizedColumnName);
            }
            normalizedColumns.add(normalizedColumnName);
        }
        return normalizedColumns.stream().toList();
    }

    public String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    public String buildColumnSignature(List<String> columnNames) {
        return normalizeColumnNames(columnNames, "columnNames").stream()
                .map(columnName -> columnName.toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + RELATION_KEY_SEPARATOR + right)
                .orElse("");
    }

    public String toJson(List<String> columnNames) {
        try {
            return objectMapper.writeValueAsString(
                    normalizeColumnNames(columnNames, "columnNames"));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize relation columns.", e);
        }
    }

    public List<String> fromJson(String columnNamesJson, String fieldName) {
        if (columnNamesJson == null || columnNamesJson.isBlank()) {
            throw new IllegalArgumentException(fieldName + " json cannot be blank.");
        }
        try {
            List<String> columnNames = objectMapper.readValue(columnNamesJson, STRING_LIST_TYPE);
            return normalizeColumnNames(columnNames, fieldName);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to parse relation columns from " + fieldName + ".", e);
        }
    }
}
