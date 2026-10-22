import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface UpdateUserRequest {
    id?: string | number;
    userID: string;
    firstName: string;
    lastName: string;
    email: string;
    status: string; // "01" for active, "09" for inactive
    roleIds: string[];
}

export interface UpdateUserData {
    updated: boolean;
}

/**
 * Call update user API endpoint: USRU001
 */
export const updateUser = async (
    payload: UpdateUserRequest
): Promise<ApiResponse<UpdateUserData>> => {
    return await http.post<UpdateUserData>("/USRU001", payload);
};