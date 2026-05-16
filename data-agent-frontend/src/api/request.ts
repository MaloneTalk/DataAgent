import axios from "axios";
import type {
  AxiosInstance,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";
import { ElMessage } from "element-plus";

interface ApiResponse<T = any> {
  code: number;
  data: T;
  message: string;
}

const service: AxiosInstance = axios.create({
  baseURL: "/api",
  timeout: 30000,
});

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data;
    if (res.code !== 200) {
      ElMessage.error(res.message || "请求失败");
      return Promise.reject(new Error(res.message || "Error"));
    }
    return res;
  },
  (error) => {
    const message =
      error.response?.data?.message || error.message || "网络错误";
    ElMessage.error(message);
    return Promise.reject(error);
  },
);

export default service;
export type { ApiResponse };
