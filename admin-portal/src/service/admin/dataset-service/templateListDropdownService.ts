import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface TemplateDropdownRequest {
    categoryId?: string;
    page: number; // 0-indexed page number
    size: number;
}

export interface TemplateDropdownItem {
    source_id: number;
    source_name: string;
    category_description: string;
}

export interface TemplateDropdownData {
    list: TemplateDropdownItem[];
}

/**
 * Fetch list of template for dropdown select: DTSI008
 */
export const getTemplateListDropdown = async (
    payload: TemplateDropdownRequest
): Promise<ApiResponse<TemplateDropdownData>> => {
    return await http.post<TemplateDropdownData>("/DTSI008", payload);
};