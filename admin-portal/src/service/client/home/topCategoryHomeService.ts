import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface CategoryHomeItem {
    categoryId: number;
    categoryName: string;
    description: string;
}

export interface CategoryHomeData {
    list: CategoryHomeItem[];
}

/**
 * Call top categories API endpoint: CATI002
 */
export const getTopCategoriesHome = async (): Promise<ApiResponse<CategoryHomeData>> => {
    return await http.post<CategoryHomeData>("/CATI002", {});
};