import axios from "axios";
import type {
    AxiosInstance,
    AxiosRequestConfig,
    AxiosResponse,
    AxiosError
} from "axios";
import type { ApiResponse } from "../api/api";

// Server address; set VITE_API_BASE_URL when building. The default suits a web server
// that forwards /api/ to the NIS server (see admin-portal/nginx.conf.template).
const BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api/nis-portal";
// const BASE_URL = "http://localhost:8010/api/nis-portal";

const apiClient: AxiosInstance = axios.create({
    baseURL: BASE_URL,
    timeout: 60000,
    withCredentials: true, // IMPORTANT: Allows browser to receive and send Spring JSESSIONID cookie
    headers: {
        "Content-Type": "application/json",
        Accept: "application/json"
    }
});

// Response Interceptor: Automatically handles backend error payloads
apiClient.interceptors.response.use(
    (response: AxiosResponse<ApiResponse>) => {
        if (response.data && response.data.code && response.data.code !== "000000") {
            return Promise.reject({
                isBusinessError: true,
                code: response.data.code,
                response: response,
                message: typeof response.data.message === "object"
                    ? response.data.message.messageBody
                    : response.data.message
            });
        }
        return response;
    },
    (error: AxiosError<ApiResponse>) => {
        if (error.response?.data?.message) {
            const apiMsg = error.response.data.message;
            if (typeof apiMsg === "object" && apiMsg.messageBody) {
                error.message = apiMsg.messageBody;
            }
        }
        return Promise.reject(error);
    }
);

export const http = {
    get: async <T>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> => {
        const response = await apiClient.get<ApiResponse<T>>(url, config);
        return response.data;
    },

    post: async <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> => {
        const response = await apiClient.post<ApiResponse<T>>(url, data, config);
        return response.data;
    },

    delete: async <T>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> => {
        const response = await apiClient.delete<ApiResponse<T>>(url, config);
        return response.data;
    }
};

export default apiClient;