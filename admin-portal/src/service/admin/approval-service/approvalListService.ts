import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";
import { normalizeApprovalListData } from "./approvalMappers";
import type { ApprovalListData, ApprovalListRequest, RawPagedData } from "./approvalTypes";

/**
 * Fetch approval list: APPI001
 */
export const getApprovalList = async (
    payload: ApprovalListRequest
): Promise<ApiResponse<ApprovalListData>> => {
    const res = await http.post<RawPagedData>("/APPI001", payload);
    return {
        ...res,
        data: res.data ? normalizeApprovalListData(res.data, payload.size) : undefined,
    };
};
