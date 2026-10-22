import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

// --- Types for PERI004 (List All Permissions) ---
export interface PermissionItem {
    permissionId: string;
    name: string;
    description?: string;
    status?: string;
    createdAt?: string;
}

export interface PermissionListResponseData {
    list: PermissionItem[];
}

// --- Types for ROLR001 (Register Role) ---
export interface RegisterRoleRequest {
    roleId: string;
    name: string;
    description?: string; // Maps to REMARK column in backend
    status?: string;
    permissionIds?: string[];
}

export interface RegisterRoleResponseData {
    roleId: string;
    assignedPermissionsCount?: number;
}

/**
 * Endpoint: /PERI004
 * Fetches all available permissions to display in the selector grid
 */
export const getPermissionListForRegister = async (
    search: string = ""
): Promise<ApiResponse<PermissionListResponseData>> => {
    return await http.post<PermissionListResponseData>("/PERI004", { search });
};

/**
 * Endpoint: /ROLR001
 * Submits the newly registered role and its mapped permission IDs
 */
export const registerRole = async (
    payload: RegisterRoleRequest
): Promise<ApiResponse<RegisterRoleResponseData>> => {
    return await http.post<RegisterRoleResponseData>("/ROLR001", payload);
};