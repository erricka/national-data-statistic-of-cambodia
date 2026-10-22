import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface CategoryListRequest {
    page: number; // 0-indexed page number
    size: number;
    search?: string;
}

export interface ApiCategoryItem {
    categoryId: number;
    categoryName: string;
    description: string;
    status: string;
    lastUpdate: string;
}

export interface CategoryListData {
    list: ApiCategoryItem[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
}

/**
 * Call category list API endpoint: CATI001
 */
export const getCategoryList = async (
    payload: CategoryListRequest
): Promise<ApiResponse<CategoryListData>> => {
    return await http.post<CategoryListData>("/CATI001", payload);
};