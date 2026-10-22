import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface CategoryDetailRequest {
    search?: string;
    page: number;
    size: number;
    categoryId: number;
}

export interface CategoryDetailItem {
    sourceId: string;
    upload_id: string;
    title: string;
    categoryDescription: string;
    totalRecord: number;
}

export interface CategoryDetailData {
    list: CategoryDetailItem[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
}

/**
 * Call category datasets list API endpoint: DTSI007
 */
export const getCategoryDetails = async (
    payload: CategoryDetailRequest
): Promise<ApiResponse<CategoryDetailData>> => {
    return await http.post<CategoryDetailData>("/DTSI007", payload);
};