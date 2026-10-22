import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface UpdateRoleRequest {
    roleId: string;
    name: string;
    description?: string;
    status?: string;
    permissionIds?: string[];
}

export interface UpdateRoleResponseData {
    updated: number;
    assignedPermissionsCount?: number;
}

/**
 * Endpoint: /ROLU001
 * Updates security role details and re-assigns the permission matrix
 */
export const updateRole = async (
    payload: UpdateRoleRequest
): Promise<ApiResponse<UpdateRoleResponseData>> => {
    return await http.post<UpdateRoleResponseData>("/ROLU001", payload);
};