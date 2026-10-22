import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface CreateUserPayload {
    userID: string;
    firstName: string;
    lastName: string;
    password: string;
    email: string;
    status: "01" | "09";
    userType: string;
    roleIds: string[];
}
export interface CreateUserResponse {
    userID?: string;
}

/**
 * Call create user API endpoint: USRR001
 */
export const createUser = async (
    payload: CreateUserPayload
): Promise<ApiResponse<CreateUserResponse>> => {
    return await http.post<CreateUserResponse>("/USRR001", payload);
};