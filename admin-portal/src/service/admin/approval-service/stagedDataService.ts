import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";
import { normalizeStagedData } from "./approvalMappers";
import type { RawStagedData, StagedData, StagedDataRequest } from "./approvalTypes";

/**
 * Fetch one page of staged (not yet approved) rows: APPI003
 * Headers and rows are dynamic per dataset.
 */
export const getStagedData = async (
    payload: StagedDataRequest
): Promise<ApiResponse<StagedData>> => {
    const res = await http.post<RawStagedData>("/APPI003", payload);
    return {
        ...res,
        data: res.data ? normalizeStagedData(res.data, payload.page, payload.size) : undefined,
    };
};
