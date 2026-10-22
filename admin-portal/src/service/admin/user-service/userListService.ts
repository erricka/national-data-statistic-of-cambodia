import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface UserListRequest {
    page: number; // 0-indexed page number
    size: number;
    search?: string;
    userStatus?: string;
}

export interface ApiUserRoleItem {
    roleId?: string;
    name?: string;
    description?: string;
    status?: string;
    effectiveDate?: string;
}

export interface ApiUserItem {
    userID: string;
    userStatus: "01" | "09" | string; // "01" = Active, "09" = Disabled
    firstName: string;
    lastName: string;
    email: string;
    department?: string;
    lastLogin?: string;
    roles?: ApiUserRoleItem[];
}

export interface UserListData {
    list: ApiUserItem[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
}

/**
 * Call user list API endpoint: USRI001
 */
export const getUserList = async (
    payload: UserListRequest
): Promise<ApiResponse<UserListData>> => {
    return await http.post<UserListData>("/USRI001", payload);
};