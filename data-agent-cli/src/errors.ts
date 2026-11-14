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
 */

export enum ExitCode {
  Success = 0,
  Usage = 2,
  Auth = 3,
  Network = 4,
  Server = 5,
}

export class CliError extends Error {
  readonly exitCode: ExitCode;
  readonly errorCode: string;

  constructor(exitCode: ExitCode, errorCode: string, message: string) {
    super(message);
    this.name = 'CliError';
    this.exitCode = exitCode;
    this.errorCode = errorCode;
  }
}

export interface ErrorPayload {
  errorCode: string;
  message: string;
}

export function toErrorPayload(error: unknown): ErrorPayload {
  if (error instanceof CliError) {
    return { errorCode: error.errorCode, message: error.message };
  }
  if (error instanceof Error) {
    return { errorCode: 'INTERNAL_ERROR', message: error.message };
  }
  return { errorCode: 'INTERNAL_ERROR', message: String(error) };
}

export function exitCodeOf(error: unknown): ExitCode {
  return error instanceof CliError ? error.exitCode : ExitCode.Server;
}
