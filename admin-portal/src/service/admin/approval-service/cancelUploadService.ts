import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";
import type { CancelUploadRequest } from "./approvalTypes";

/**
 * Cancel own upload (submitter only): APPR003
 */
export const cancelUpload = async (
    payload: CancelUploadRequest
): Promise<ApiResponse<unknown>> => {
    return await http.post<unknown>("/APPR003", payload);
};
