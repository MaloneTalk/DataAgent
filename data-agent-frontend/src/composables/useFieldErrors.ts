/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import { reactive, watch } from 'vue';
import { getFieldErrorMap, type FieldErrorMap } from '@/api/request';

export function useFieldErrors<T extends object>(form?: T) {
  const fieldErrors = reactive<FieldErrorMap>({});

  function clearFieldErrors() {
    Object.keys(fieldErrors).forEach(key => delete fieldErrors[key]);
  }

  function applyFieldErrors(error: unknown) {
    clearFieldErrors();
    Object.assign(fieldErrors, getFieldErrorMap(error));
  }

  if (form) {
    watch(form, clearFieldErrors, { deep: true });
  }

  return {
    fieldErrors,
    clearFieldErrors,
    applyFieldErrors,
  };
}
