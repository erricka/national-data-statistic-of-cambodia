import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface CategoryDropdownRequest {
    search?: string;
}

export interface CategoryDropdownItem {
    categoryId: number;
    categoryName: string;
    description?: string;
}

export interface CategoryDropdownData {
    list: CategoryDropdownItem[];
}

/**
 * Fetch list of categories for dropdown select: CATI005
 */
export const getCategoryListDropdown = async (
    payload: CategoryDropdownRequest = { search: "" }
): Promise<ApiResponse<CategoryDropdownData>> => {
    return await http.post<CategoryDropdownData>("/CATI005", payload);
};