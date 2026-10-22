import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface UserRoleDropdownItem {
    roleId: string;
    name: string;
    description?: string;
    status?: string;
}

export interface UserRoleDropdownData {
    list: UserRoleDropdownItem[];
}

export interface UserRoleDropdownRequest {
    search?: string;
}

export const getUserRoleListDropdown = async (
    payload: UserRoleDropdownRequest = { search: "" }
): Promise<ApiResponse<UserRoleDropdownData>> => {
    return await http.post<UserRoleDropdownData>("/ROLI003", payload);
};