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
package io.github.malonetalk.agent.datasource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class SqlExecutorTest {

    private final SqlExecutor executor = new SqlExecutor(mock(DynamicDataSourceManager.class));

    @Test
    void acceptsBusinessTableCountsAndAppliesOuterRowLimit() {
        String sql =
                """
                SELECT 'ticket' t, COUNT(*) c FROM ticket
                UNION ALL SELECT 'audit_log', COUNT(*) FROM audit_log
                UNION ALL SELECT 'status_event', COUNT(*) FROM status_event
                UNION ALL SELECT 'dispatch_record', COUNT(*) FROM dispatch_record
                UNION ALL SELECT 'acceptance_seq', COUNT(*) FROM acceptance_seq
                UNION ALL SELECT 'ticket_attachment', COUNT(*) FROM ticket_attachment
                UNION ALL SELECT 'idempotency', COUNT(*) FROM idempotency
                """
                        .trim();

        assertEquals(sql, executor.validateSelectSql(sql + ";"));
        assertEquals(
                "SELECT * FROM (" + sql + ") AS _sandbox LIMIT 200",
                executor.validateAndTransform(sql));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "SELECT acceptance_no FROM ticket",
                "SELECT operator_id FROM audit_log UNION SELECT operator_id FROM status_event",
                "(SELECT operator_id FROM audit_log) UNION ALL (SELECT operator_id FROM"
                        + " status_event)",
                "((SELECT acceptance_no FROM ticket))"
            })
    void acceptsPlainUnionAndParenthesedQueries(String sql) {
        assertEquals(sql, executor.validateSelectSql(sql));
    }

    @Test
    void preservesUnionOrderingAndExistingLimit() {
        String sql =
                "SELECT operator_id, created_at FROM audit_log "
                        + "UNION ALL SELECT operator_id, created_at FROM status_event "
                        + "ORDER BY created_at DESC LIMIT 20";

        assertEquals(sql, executor.validateAndTransform(sql));
    }

    @Test
    void branchLimitDoesNotReplaceOverallLimit() {
        String sql =
                "(SELECT operator_id FROM audit_log LIMIT 10) "
                        + "UNION ALL (SELECT operator_id FROM status_event LIMIT 10)";

        assertEquals(
                "SELECT * FROM (" + sql + ") AS _sandbox LIMIT 200",
                executor.validateAndTransform(sql));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "SELECT operator_id INTO copied_users FROM audit_log",
                "SELECT operator_id INTO copied_users FROM audit_log UNION ALL SELECT operator_id"
                        + " FROM status_event",
                "SELECT operator_id FROM audit_log UNION ALL SELECT operator_id INTO copied_users"
                        + " FROM status_event",
                "((SELECT operator_id INTO copied_users FROM audit_log))",
                "SELECT operator_id FROM audit_log UNION ALL ((SELECT operator_id INTO copied_users"
                        + " FROM status_event))"
            })
    void rejectsSelectIntoInEveryUnionBranchAndParenthesis(String sql) {
        BusinessException error =
                assertThrows(BusinessException.class, () -> executor.validateSelectSql(sql));

        assertEquals(ErrorCode.SQL_NOT_ALLOWED, error.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETE FROM ticket", "SELECT FROM", "VALUES (1), (2)"})
    void rejectsInvalidOrUnsupportedStatementsWithBusinessError(String sql) {
        BusinessException error =
                assertThrows(BusinessException.class, () -> executor.validateSelectSql(sql));

        assertEquals(ErrorCode.SQL_NOT_ALLOWED, error.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validatesDeeplyNestedAstWithoutOverflow(boolean selectInto) throws Exception {
        String sql =
                selectInto
                        ? "SELECT operator_id INTO copied_users FROM audit_log"
                        : "SELECT operator_id FROM audit_log";
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        // Build the AST directly to isolate our traversal from the parser's nesting limits.
        for (int i = 0; i < 20_000; i++) {
            select = new ParenthesedSelect().withSelect(select);
        }
        Select root = select;
        if (selectInto) {
            BusinessException error =
                    assertThrows(
                            BusinessException.class,
                            () ->
                                    ReflectionTestUtils.invokeMethod(
                                            executor, "validateSelectBody", root));
            assertEquals(ErrorCode.SQL_NOT_ALLOWED, error.getErrorCode());
        } else {
            assertDoesNotThrow(
                    () -> ReflectionTestUtils.invokeMethod(executor, "validateSelectBody", root));
        }
    }
}
