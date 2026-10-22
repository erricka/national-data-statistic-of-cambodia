import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";
import { normalizeAuditHistory } from "./approvalMappers";
import type { AuditHistoryData, AuditHistoryRequest } from "./approvalTypes";

/**
 * Fetch audit trail for an upload: APPI004
 */
export const getAuditHistory = async (
    payload: AuditHistoryRequest
): Promise<ApiResponse<AuditHistoryData>> => {
    const res = await http.post<unknown>("/APPI004", payload);
    return {
        ...res,
        // An empty/absent body is treated as "no history" rather than an error.
        data: normalizeAuditHistory(res.data),
    };
};
