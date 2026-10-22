import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface CategorySummaryData {
    list: {
        totalCategory: number;
        totalDatasource: number;
    };
}

export interface CategoryListRequest {
    search?: string;
    page: number;
    size: number;
}

export interface CategoryListItem {
    categoryId: number;
    categoryName: string;
    description: string;
    totalDataset: number;
}

export interface CategoryListData {
    list: CategoryListItem[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
}

export const getCategorySummary = async (): Promise<ApiResponse<CategorySummaryData>> => {
    return await http.post<CategorySummaryData>("/CATI004");
};

export const getCategoryList = async (
    payload: CategoryListRequest
): Promise<ApiResponse<CategoryListData>> => {
    return await http.post<CategoryListData>("/CATI003", payload);
};