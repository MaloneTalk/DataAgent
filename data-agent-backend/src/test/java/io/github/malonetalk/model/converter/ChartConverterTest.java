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
package io.github.malonetalk.model.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.model.bo.ChartPayloadBo;
import io.github.malonetalk.model.bo.ChartSpecBo;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChartConverterTest {
    @ParameterizedTest
    @ValueSource(strings = {"bar", "line", "pie", "scatter"})
    void preservesLargeDimensionsThroughJsonWithoutMutatingSource(String chartType)
            throws Exception {
        var mapper = new ObjectMapper();
        var group = new BigInteger("18446744073709551615");
        var rows =
                List.<Map<String, Object>>of(
                        Map.of("id", 9007199254740992L, "group", group, "amount", 10),
                        Map.of("id", 9007199254740993L, "group", group, "amount", 20));
        var spec =
                ChartSpecBo.builder()
                        .chartType(chartType)
                        .title("Amounts")
                        .xField("id")
                        .yFields(List.of("amount"))
                        .seriesField("pie".equals(chartType) ? "" : "group")
                        .build();
        var metadata = new ChartConverter(mapper).toMetadata(new ChartPayloadBo(spec, rows));
        var restored = mapper.readValue(mapper.writeValueAsString(metadata), ChartPayloadBo.class);

        for (int i = 0; i < rows.size(); i++) {
            var expectedId = rows.get(i).get("id");
            assertEquals(
                    "scatter".equals(chartType) ? expectedId : expectedId.toString(),
                    restored.rows().get(i).get("id"));
            if (!"pie".equals(chartType)) {
                assertEquals(group.toString(), restored.rows().get(i).get("group"));
            }
            assertEquals(rows.get(i).get("amount"), restored.rows().get(i).get("amount"));
            assertEquals(9007199254740992L + i, rows.get(i).get("id"));
            assertEquals(group, rows.get(i).get("group"));
        }
    }
}
