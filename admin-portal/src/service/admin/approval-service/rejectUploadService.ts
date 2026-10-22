import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";
import type { RejectUploadRequest } from "./approvalTypes";

/**
 * Reject an upload: APPR002
 */
export const rejectUpload = async (
    payload: RejectUploadRequest
): Promise<ApiResponse<unknown>> => {
    return await http.post<unknown>("/APPR002", payload);
};
