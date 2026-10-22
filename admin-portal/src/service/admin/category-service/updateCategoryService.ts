import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface UpdateCategoryRequest {
    categoryId: number;
    categoryName: string;
    description: string;
    status: string;
}

export interface UpdateCategoryData {
    success: boolean;
}

/**
 * Call update category API endpoint: CATU001
 */
export const updateCategory = async (
    payload: UpdateCategoryRequest
): Promise<ApiResponse<UpdateCategoryData>> => {
    return await http.post<UpdateCategoryData>("/CATU001", payload);
};