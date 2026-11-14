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
package io.github.malonetalk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.malonetalk.model.bo.MetricInfoBo;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricServiceImplTest {

    @Test
    void matchesMetricWhenHintIsTheFullQuestion() {
        List<MetricInfoBo> metrics = List.of(metric("销售额", "流水,GMV,营收"));

        assertEquals(List.of("销售额"), names(MetricServiceImpl.match(metrics, "上个月的GMV是多少")));
    }

    @Test
    void matchesCaseInsensitive() {
        List<MetricInfoBo> metrics = List.of(metric("销售额", "GMV"));

        assertEquals(List.of("销售额"), names(MetricServiceImpl.match(metrics, "上个月的gmv多少")));
    }

    @Test
    void ignoresSingleCharacterAliases() {
        List<MetricInfoBo> metrics = List.of(metric("销售额", "额"));

        assertTrue(MetricServiceImpl.match(metrics, "账户余额还剩多少").isEmpty());
    }

    @Test
    void ranksLongerMatchFirst() {
        List<MetricInfoBo> metrics = List.of(metric("销售", null), metric("销售额", null));

        assertEquals(List.of("销售额", "销售"), names(MetricServiceImpl.match(metrics, "查一下销售额")));
    }

    @Test
    void returnsEmptyWhenNoTermAppears() {
        List<MetricInfoBo> metrics = List.of(metric("销售额", "GMV"));

        assertTrue(MetricServiceImpl.match(metrics, "华东区有多少家门店").isEmpty());
    }

    @Test
    void toleratesNullNameAndAliases() {
        List<MetricInfoBo> metrics = List.of(new MetricInfoBo());

        assertTrue(MetricServiceImpl.match(metrics, "销售额").isEmpty());
    }

    private static MetricInfoBo metric(String name, String aliases) {
        MetricInfoBo metric = new MetricInfoBo();
        metric.setName(name);
        metric.setAliases(aliases);
        return metric;
    }

    private static List<String> names(List<MetricInfoBo> metrics) {
        return metrics.stream().map(MetricInfoBo::getName).toList();
    }
}
