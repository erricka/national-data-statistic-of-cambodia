import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";
import { normalizeUploadDetail } from "./approvalMappers";
import type { RawRecord, UploadDetail, UploadDetailRequest } from "./approvalTypes";

/**
 * Fetch upload details for review: APPI002
 */
export const getUploadDetails = async (
    payload: UploadDetailRequest
): Promise<ApiResponse<UploadDetail>> => {
    const res = await http.post<RawRecord>("/APPI002", payload);
    return {
        ...res,
        data: res.data ? normalizeUploadDetail(res.data, payload.uploadId) : undefined,
    };
};
