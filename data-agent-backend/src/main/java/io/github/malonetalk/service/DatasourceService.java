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

import io.github.malonetalk.entity.Datasource;
import java.util.List;
import java.util.Optional;

public interface DatasourceService {

    List<Datasource> findAll();

    Datasource findById(Integer id);

    boolean save(Datasource dataSource);

    boolean update(Datasource dataSource);

    boolean deleteById(Integer id);

    List<Datasource> findByStatus(String status);

    Optional<Datasource> getActiveDatasource();

    /** 会话维度解析数据源：有绑定用绑定（无视 status），无绑定回退激活源。 */
    Datasource getDatasourceForSession(String sessionId);

    /** 绑定会话到数据源；数据源不存在抛 400，已绑定则保留首次绑定。 */
    void bindSessionDatasource(String sessionId, Integer datasourceId);

    List<Datasource> findByType(String type);

    boolean updateStatus(Integer id, String status);
}
