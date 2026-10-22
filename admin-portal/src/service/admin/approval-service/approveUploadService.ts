import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";
import type { ApproveUploadRequest } from "./approvalTypes";

/**
 * Approve an upload: APPR001
 */
export const approveUpload = async (
    payload: ApproveUploadRequest
): Promise<ApiResponse<unknown>> => {
    return await http.post<unknown>("/APPR001", payload);
};
