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

import type { Directive } from 'vue';
import { watchEffect } from 'vue';
import { useUserStore } from '@/stores/user';
const stops = new WeakMap<HTMLElement, () => void>();
export const permission: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const store = useUserStore();
    const display = el.style.display;
    stops.set(
      el,
      watchEffect(() => {
        el.style.display = store.hasPermission(binding.value) ? display : 'none';
      }),
    );
  },
  unmounted(el) {
    stops.get(el)?.();
    stops.delete(el);
  },
};
