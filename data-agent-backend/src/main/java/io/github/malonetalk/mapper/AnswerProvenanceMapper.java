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
package io.github.malonetalk.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.malonetalk.model.po.AnswerProvenancePo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnswerProvenanceMapper {

    AnswerProvenancePo selectForUpdateBySessionIdAndAnswerId(
            @Param("sessionId") String sessionId, @Param("answerId") String answerId);

    IPage<AnswerProvenancePo> selectPage(
            IPage<AnswerProvenancePo> page,
            @Param("sessionId") String sessionId,
            @Param("answerId") String answerId,
            @Param("sessionIds") List<String> sessionIds);

    int insert(AnswerProvenancePo provenance);

    int updateBySessionIdAndAnswerId(AnswerProvenancePo provenance);

    int deleteBySessionId(@Param("sessionId") String sessionId);
}
