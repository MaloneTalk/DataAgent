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

export const routePermissions: Record<string, string> = {
  '/chat': 'analysis:chat:use',
  '/data-source': 'data:datasource:read',
  '/semantic/domain': 'semantic:domain:read',
  '/semantic/table': 'semantic:table:read',
  '/semantic/metric': 'semantic:metric:read',
  '/semantic/relation': 'semantic:relation:read',
  '/asset/report': 'asset:report:read',
  '/asset/table-export': 'asset:export:read',
  '/asset/evidence': 'asset:evidence:read',
  '/system/user': 'sys:user:read',
  '/system/role': 'sys:role:read',
};
export function permissionForPath(path: string) {
  return routePermissions[path.startsWith('/chat/') ? '/chat' : path];
}
