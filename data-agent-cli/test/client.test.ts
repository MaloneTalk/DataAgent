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

import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { DataAgentClient } from '../src/client/DataAgentClient.js';
import { CliError, ExitCode } from '../src/errors.js';

const TRACEPARENT = '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01';

let server: Server;
let client: DataAgentClient;

beforeAll(async () => {
  server = createServer((request, response) => handle(request, response));
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  const address = server.address() as AddressInfo;
  client = new DataAgentClient({
    baseUrl: `http://127.0.0.1:${address.port}`,
    token: 'pat_test',
    timeoutMs: 5_000,
    version: '0.0.0-test',
    sessionId: 'run-123',
    traceProvider: async () => ({ traceparent: TRACEPARENT, traceId: 'trace' }),
  });
});

afterAll(async () => {
  await new Promise<void>((resolve, reject) => {
    server.close(error => (error ? reject(error) : resolve()));
  });
});

describe('DataAgentClient', () => {
  it('calls health with auth and trace headers', async () => {
    const result = await client.health();
    expect(result.name).toBe('test-token');
  });

  it('lists tools', async () => {
    const result = await client.listTools();
    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('get_domains');
  });

  it('calls a tool with session and JSON body and returns the tool result', async () => {
    const result = await client.callTool('get_domains', { datasource_id: 1 });
    expect(result.text).toBe('ok');
    expect(result.metadata.returnedRows).toBe(0);
  });

  it('maps 401 responses to auth errors', async () => {
    const error = await client.callTool('unauthorized', {}).catch(value => value);
    expect(error).toBeInstanceOf(CliError);
    expect((error as CliError).exitCode).toBe(ExitCode.Auth);
    expect((error as CliError).errorCode).toBe('UNAUTHORIZED');
  });
});

async function handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const url = request.url ?? '/';
  expect(request.headers.authorization).toBe('Bearer pat_test');
  expect(request.headers.traceparent).toBe(TRACEPARENT);

  if (url === '/agent/health') {
    return json(response, 200, {
      code: 200,
      errorCode: null,
      message: 'success',
      data: {
        id: 1,
        name: 'test-token',
        tokenPrefix: 'pat_test',
        expiresAt: '2026-12-31T23:59:59',
        lastUsedAt: null,
        revokedAt: null,
        createTime: '2026-11-10T10:00:00',
      },
    });
  }

  if (url === '/agent/tools') {
    return json(response, 200, {
      code: 200,
      errorCode: null,
      message: 'success',
      data: [
        {
          name: 'get_domains',
          description: 'Get domains',
          parameters: { type: 'object', properties: {} },
        },
      ],
    });
  }

  if (url === '/agent/tools/get_domains') {
    expect(request.headers['x-session-id']).toBe('run-123');
    expect(await readBody(request)).toEqual({ datasource_id: 1 });
    return json(response, 200, {
      code: 200,
      errorCode: null,
      message: 'success',
      data: {
        text: 'ok',
        metadata: { returnedRows: 0 },
        traceId: 'trace',
      },
    });
  }

  if (url === '/agent/tools/unauthorized') {
    return json(response, 401, {
      code: 401,
      errorCode: 'UNAUTHORIZED',
      message: 'Authentication is required.',
      data: null,
    });
  }

  json(response, 404, { code: 404, errorCode: 'NOT_FOUND', message: 'not found', data: null });
}

function json(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
}

async function readBody(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}
