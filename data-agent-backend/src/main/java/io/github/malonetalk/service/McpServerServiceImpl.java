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

import io.github.malonetalk.entity.McpServer;
import io.github.malonetalk.mapper.McpServerMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper mcpServerMapper;

    public McpServerServiceImpl(McpServerMapper mcpServerMapper) {
        this.mcpServerMapper = mcpServerMapper;
    }

    @Override
    public List<McpServer> findAll() {
        return mcpServerMapper.selectAll();
    }

    @Override
    public McpServer findById(Integer id) {
        return mcpServerMapper.selectById(id);
    }

    @Override
    public McpServer findByName(String name) {
        return mcpServerMapper.selectByName(name);
    }

    @Override
    public boolean save(McpServer mcpServer) {
        mcpServer.setCreateTime(LocalDateTime.now());
        mcpServer.setUpdateTime(LocalDateTime.now());
        return mcpServerMapper.insert(mcpServer) > 0;
    }

    @Override
    public boolean update(McpServer mcpServer) {
        mcpServer.setUpdateTime(LocalDateTime.now());
        return mcpServerMapper.update(mcpServer) > 0;
    }

    @Override
    public boolean deleteById(Integer id) {
        return mcpServerMapper.deleteById(id) > 0;
    }

    @Override
    public List<McpServer> findByStatus(String status) {
        return mcpServerMapper.selectByStatus(status);
    }
}
