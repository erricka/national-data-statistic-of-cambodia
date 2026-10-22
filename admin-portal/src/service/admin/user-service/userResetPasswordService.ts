import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface UserResetPasswordByAdminRequest {
    userID: string;
}

export interface UserResetPasswordByAdminData {
    reset: boolean;
}

/**
 * Call reset user password by admin to default API endpoint: USRU003
 */
export const resetPasswordByAdminToDefault = async (
    payload: UserResetPasswordByAdminRequest
): Promise<ApiResponse<UserResetPasswordByAdminData>> => {
    return await http.post<UserResetPasswordByAdminData>("/USRU003", payload);
};