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
package io.github.malonetalk.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.agentscope.core.model.exception.AuthenticationException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExceptionResponseMapperTest {
    private final ExceptionResponseMapper mapper = new ExceptionResponseMapper();

    @Test
    void modelAuthenticationFailureIsNotAUserLoginFailureOrGenericInternalError() {
        var cause =
                new AuthenticationException("upstream rejected secret-key", "test-model", "openai");
        var response = mapper.resolve(new CompletionException(cause));

        assertEquals(ErrorCode.MODEL_AUTHENTICATION_FAILED, response.errorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.errorCode().getHttpStatus());
        assertEquals(ErrorCode.MODEL_AUTHENTICATION_FAILED.getDefaultMessage(), response.message());
        assertFalse(response.message().contains("secret-key"));
    }

    @Test
    void applicationLoginFailuresKeepTheirExistingCode() {
        var response = mapper.resolve(BusinessException.of(ErrorCode.UNAUTHORIZED));

        assertEquals(ErrorCode.UNAUTHORIZED, response.errorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, response.errorCode().getHttpStatus());
    }
}
