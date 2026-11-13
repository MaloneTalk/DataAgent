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
package io.github.malonetalk.agent.tools.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolSuspendException;
import io.github.malonetalk.agent.tools.ToolExceptionMapper;
import io.github.malonetalk.agent.tools.ToolResultBoConverter;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.exception.ExceptionResponseMapper;
import io.github.malonetalk.model.bo.ToolResultBo;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultBoConverterTest {

    private final ToolResultBoConverter converter = new ToolResultBoConverter();

    @Test
    void convertsTextAndMetadata() {
        ToolResultBlock block =
                converter.convert(new ToolResultBo("hello", Map.of("k", "v")), ToolResultBo.class);

        assertEquals("hello", ((TextBlock) block.getOutput().get(0)).getText());
        assertEquals(Map.of("k", "v"), block.getMetadata());
    }

    @Test
    void mapsBusinessExceptionToErrorMetadata() {
        ToolExceptionMapper mapper = new ToolExceptionMapper(new ExceptionResponseMapper());

        ToolResultBo result =
                mapper.runResult(
                        () -> {
                            throw BusinessException.of(ErrorCode.BAD_REQUEST, "bad");
                        });

        assertEquals("bad", result.text());
        assertEquals(
                ErrorCode.BAD_REQUEST.getCode(),
                result.metadata().get(ToolExceptionMapper.METADATA_ERROR_CODE));
    }

    @Test
    void rethrowsSuspendException() {
        ToolExceptionMapper mapper = new ToolExceptionMapper(new ExceptionResponseMapper());

        assertThrows(
                ToolSuspendException.class,
                () ->
                        mapper.runResult(
                                () -> {
                                    throw new ToolSuspendException("question");
                                }));
    }
}
