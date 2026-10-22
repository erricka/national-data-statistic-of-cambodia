import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface CreateCategoryRequest {
    categoryName: string;
    description: string;
    status: string;
}

export interface CreateCategoryData {
    success: boolean;
}

export const createCategory = async (
    payload: CreateCategoryRequest
): Promise<ApiResponse<CreateCategoryData>> => {
    return await http.post<CreateCategoryData>("/CATR001", payload);
};