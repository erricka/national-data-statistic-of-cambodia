// src/services/templateService.ts
import { http } from "../api/apiClient";
import type { ApiResponse } from "../api/api";

export interface TemplateSchemaItem {
    id: string;
    item_name: string;
    item_description: string;
    type: string;
    data_length?: string;
}

export interface CreateTemplatePayload {
    name: string;
    description: string;
    schema: TemplateSchemaItem[];
}

export interface TemplateItem {
    id: string;
    name: string;
    description: string;
    schema: TemplateSchemaItem[];
    createdAt: string;
}

export const templateService = {

    getTemplates: (page = 1, limit = 10, search = "") => {
        return http.get<ApiResponse<TemplateItem[]>>(
            `/templates?page=${page}&limit=${limit}&search=${encodeURIComponent(search)}`
        );
    },

    createTemplate: (payload: CreateTemplatePayload) => {
        return http.post<ApiResponse<TemplateItem>>("/templates", payload);
    },

    deleteTemplate: (id: string) => {
        return http.delete<ApiResponse<void>>(`/templates/${id}`);
    }
};