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

import io.github.malonetalk.model.bo.MetricInfoBo;
import io.github.malonetalk.utils.MetricAliasUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Deterministic matching; ranking never resolves two definitions of the same term. */
final class MetricMatcher {
    private MetricMatcher() {}

    static Matches match(List<MetricInfoBo> metrics, String hint) {
        String query = MetricAliasUtils.normalize(hint);
        List<Hit> hits = new ArrayList<>();
        for (MetricInfoBo metric : metrics) {
            for (String term : terms(metric)) {
                if (term.equals(query)) {
                    hits.add(new Hit(metric, 0, query.length()));
                } else if (term.length() >= 2) {
                    for (int start = query.indexOf(term);
                            start >= 0;
                            start = query.indexOf(term, start + 1)) {
                        int end = start + term.length();
                        if (hasBoundaries(query, term, start, end))
                            hits.add(new Hit(metric, start, end));
                    }
                }
            }
        }
        List<Hit> exact = hits.stream().filter(hit -> hit.length() == query.length()).toList();
        List<Hit> retained =
                exact.isEmpty()
                        ? hits.stream()
                                .filter(hit -> hits.stream().noneMatch(other -> covers(other, hit)))
                                .toList()
                        : exact;
        var firstConflict =
                retained.stream()
                        .filter(
                                hit ->
                                        retained.stream()
                                                .anyMatch(
                                                        other ->
                                                                !hit.metric().equals(other.metric())
                                                                        && overlaps(hit, other)))
                        .min(Comparator.comparingInt(Hit::start));
        List<Hit> relevant =
                firstConflict
                        .map(
                                conflict ->
                                        retained.stream()
                                                .filter(hit -> overlaps(hit, conflict))
                                                .toList())
                        .orElse(retained);
        List<MetricInfoBo> candidates =
                relevant.stream()
                        .sorted(Comparator.comparingInt(Hit::length).reversed())
                        .map(Hit::metric)
                        .distinct()
                        .toList();
        return new Matches(candidates, firstConflict.isPresent());
    }

    private static boolean covers(Hit longer, Hit shorter) {
        return longer.start() <= shorter.start()
                && longer.end() >= shorter.end()
                && longer.length() > shorter.length();
    }

    private static boolean overlaps(Hit left, Hit right) {
        return left.start() < right.end() && right.start() < left.end();
    }

    static List<String> terms(MetricInfoBo metric) {
        return Stream.concat(
                        Stream.of(metric.getName()),
                        MetricAliasUtils.aliases(metric.getAliases()).stream())
                .map(MetricAliasUtils::normalize)
                .filter(term -> !term.isEmpty())
                .distinct()
                .toList();
    }

    private static boolean hasBoundaries(String query, String term, int start, int end) {
        return (!isIdentifier(term.charAt(0))
                        || start == 0
                        || !isIdentifier(query.charAt(start - 1)))
                && (!isIdentifier(term.charAt(term.length() - 1))
                        || end == query.length()
                        || !isIdentifier(query.charAt(end)));
    }

    private static boolean isIdentifier(char value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9' || value == '_';
    }

    record Matches(List<MetricInfoBo> candidates, boolean conflicting) {}

    private record Hit(MetricInfoBo metric, int start, int end) {
        int length() {
            return end - start;
        }
    }
}
