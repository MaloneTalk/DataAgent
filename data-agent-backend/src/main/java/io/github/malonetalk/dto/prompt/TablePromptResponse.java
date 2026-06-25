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
package io.github.malonetalk.dto.prompt;

import java.util.List;

/** Agent-facing DTO for LLM prompt formatting. */
public record TablePromptResponse(
        String name,
        String domain,
        String description,
        List<TableRelationPromptResponse> relations) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String domain;
        private String description;
        private List<TableRelationPromptResponse> relations;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder relations(List<TableRelationPromptResponse> relations) {
            this.relations = relations;
            return this;
        }

        public TablePromptResponse build() {
            return new TablePromptResponse(name, domain, description, relations);
        }
    }
}
