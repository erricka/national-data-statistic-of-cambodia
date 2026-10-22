import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface RolePermissionItem {
    permissionId: string;
    description: string;
}

export interface RoleDetailData {
    roleId: string;
    roleName: string;
    permissions?: RolePermissionItem[];
}

export interface RoleDetailRequest {
    roleId: string;
}

/**
 * Call get role detail API endpoint: ROLI002
 */
export const getRoleDetail = async (
    payload: RoleDetailRequest
): Promise<ApiResponse<RoleDetailData>> => {
    return await http.post<RoleDetailData>("/ROLI002", payload);
};