import type { ApiResponse } from "../../../api/api";
import { http } from "../../../api/apiClient";

export interface DatasetListRequest {
    search: string;
    page: number; // 0-indexed
    size: number;
    categoryId: string;
}

export interface DatasetListItem {
    sourceId: string;
    upload_id: string;
    title: string;
    categoryDescription: string;
    totalRecord: number;
    lastChangeDate: string; // Format: "YYYYMMDD"
}

export interface DatasetListData {
    list: DatasetListItem[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
}

/**
 * Fetch dataset list: DTSI009
 */
export const getDatasetList = async (
    payload: DatasetListRequest
): Promise<ApiResponse<DatasetListData>> => {
    return await http.post<DatasetListData>("/DTSI009", payload);
};