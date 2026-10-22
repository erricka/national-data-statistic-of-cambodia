import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface RoleListRequest {
    page: number; // 0-indexed page number
    size: number;
    search?: string;
}

export interface ApiRoleItem {
    roleId: string;
    name: string;
    description: string;
    permission?: string;
}

export interface RoleListData {
    list: ApiRoleItem[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
}

/**
 * Call role list API endpoint: ROLI001
 */
export const getRoleList = async (
    payload: RoleListRequest
): Promise<ApiResponse<RoleListData>> => {
    return await http.post<RoleListData>("/ROLI001", payload);
};