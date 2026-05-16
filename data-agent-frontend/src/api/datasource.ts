import request from "./request";

export interface DatasourceRequest {
  id?: number;
  name: string;
  type: string;
  host?: string;
  port?: number;
  databaseName?: string;
  username?: string;
  password?: string;
  connectionUrl?: string;
  description?: string;
}

export interface DatasourceResponse {
  id: number;
  name: string;
  type: string;
  host?: string;
  port?: number;
  databaseName?: string;
  username?: string;
  connectionUrl?: string;
  status?: string;
  testStatus?: string;
  description?: string;
}

export function getDatasourceList() {
  return request.get<{
    code: number;
    message: string;
    data: DatasourceResponse[];
  }>("/datasource");
}

export function createDatasource(data: DatasourceRequest) {
  return request.post<{ code: number; message: string; data: boolean }>(
    "/datasource",
    data,
  );
}

export function updateDatasource(data: DatasourceRequest) {
  return request.put<{ code: number; message: string; data: boolean }>(
    `/datasource/${data.id}`,
    data,
  );
}

export function deleteDatasource(id: number) {
  return request.delete<{ code: number; message: string; data: boolean }>(
    `/datasource/${id}`,
  );
}
