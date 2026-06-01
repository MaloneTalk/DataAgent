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
package io.github.malonetalk.convertor;

import io.github.malonetalk.dto.semantic.ColumnSemanticResponse;
import io.github.malonetalk.entity.ColumnInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ColumnSemanticConverter {

    @Mapping(target = "physicalColumnDescription", expression = "java(null)")
    @Mapping(target = "typeName", expression = "java(null)")
    @Mapping(target = "primaryKey", expression = "java(null)")
    @Mapping(target = "hasPhysicalColumn", constant = "true")
    @Mapping(target = "effective", expression = "java(Boolean.TRUE.equals(columnInfo.getIsVisible()))")
    @Mapping(target = "invalidReason", expression = "java(null)")
    ColumnSemanticResponse toResponse(ColumnInfo columnInfo);
}
