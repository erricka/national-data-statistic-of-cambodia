import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface DataExploreRequest {
    search?: string;
    page: number; // 0-indexed page number
    size: number;
}

export interface DataExploreItem {
    source_id: string;
    upload_id: string;
    title: string;
    category_description: string;
    file_extention: string;
}

export interface DataExploreData {
    list: DataExploreItem[];
    page: number;
    size: number;
    total?: number;
    totalPages?: number;
}

/**
 * Call data explore list API endpoint: DTSI006
 */
export const getDataExplore = async (
    payload: DataExploreRequest
): Promise<ApiResponse<DataExploreData>> => {
    return await http.post<DataExploreData>("/DTSI006", payload);
};