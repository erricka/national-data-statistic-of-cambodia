import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";
import { encryptPassword, getPublicKey } from "../../auth/authService";

export interface ChangePasswordResult {
    changed?: boolean;
}

/**
 * Change the logged-in user's own password: USRU004
 *
 * - The user is taken from the session on the server; no userID is sent.
 * - Both passwords are RSA-encrypted with the /auth/init public key, the same way as /login.
 */
export const changeOwnPassword = async (
    currentPassword: string,
    newPassword: string
): Promise<ApiResponse<ChangePasswordResult>> => {
    const publicKey = await getPublicKey();
    return await http.post<ChangePasswordResult>("/USRU004", {
        currentPassword: encryptPassword(currentPassword, publicKey),
        newPassword: encryptPassword(newPassword, publicKey),
    });
};
