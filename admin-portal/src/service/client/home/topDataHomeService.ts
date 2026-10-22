import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface TopDataHomeRequest {
    page: number; // 0-indexed page number
    size: number;
}

export interface TopDataHomeItem {
    source_id: string;
    upload_id: string;
    source_name: string;
    category_description: string;
    table_name: string;
    total_record: number;
}

export interface TopDataHomeData {
    list: TopDataHomeItem[];
    page: number;
    size: number;
    total?: number;
    totalPages?: number;
}

/**
 * Call top data home API endpoint: DTSI005
 */
export const getTopDataHome = async (
    payload: TopDataHomeRequest
): Promise<ApiResponse<TopDataHomeData>> => {
    return await http.post<TopDataHomeData>("/DTSI005", payload);
};