import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface PermissionListRequest {
    page: number; // 0-indexed page number
    size: number;
    search?: string;
}

export interface ApiPermissionItem {
    permissionId: string;
    name: string;
    description: string;
    status: string;
    createdAt: string;
}

export interface PermissionListData {
    list: ApiPermissionItem[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
}

/**
 * Call permission list API endpoint
 */
export const getPermissionList = async (
    payload: PermissionListRequest
): Promise<ApiResponse<PermissionListData>> => {
    return await http.post<PermissionListData>("/PERI001", payload);
};