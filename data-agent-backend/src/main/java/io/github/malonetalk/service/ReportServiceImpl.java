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

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.convertor.ReportConverter;
import io.github.malonetalk.dto.ReportPageQuery;
import io.github.malonetalk.dto.ReportResponse;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.Report;
import io.github.malonetalk.mapper.ReportMapper;
import io.github.malonetalk.utils.SemanticUtils;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final ReportConverter reportConverter;

    @Override
    public int create(String sessionId, String title, String content) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("报告标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("报告正文不能为空");
        }
        Report report = new Report();
        report.setSessionId(sessionId);
        report.setTitle(title);
        report.setContent(content);
        report.setIsDeleted(false);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        if (reportMapper.insert(report) <= 0) {
            throw new RuntimeException("报告保存失败");
        }
        return report.getId();
    }

    @Override
    public PageResponse<ReportResponse> getReportPage(ReportPageQuery query) {
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        boolean sortDescending = SemanticUtils.isDescendingSort(query.sortOrder());
        Page<Object> startedPage = PageHelper.startPage(pageNumber, pageSize);
        Page<Report> page =
                (Page<Report>)
                        reportMapper.selectPage(
                                new ReportPageQuery(
                                        SemanticUtils.trimToNull(query.sessionId()),
                                        pageNumber,
                                        pageSize,
                                        SemanticUtils.trimToNull(query.keyword()),
                                        query.sortOrder()),
                                sortDescending);
        List<ReportResponse> responses =
                page.getResult().stream().map(reportConverter::toResponse).toList();
        startedPage.close();
        return PageResponse.of(responses, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    public ReportResponse findById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("报告不存在或已删除: id=" + id);
        }
        return reportConverter.toResponse(report);
    }

    @Override
    public void deleteById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        int affected = reportMapper.deleteById(id);
        if (affected == 0) {
            throw new IllegalArgumentException("报告不存在或已删除: id=" + id);
        }
    }
}
