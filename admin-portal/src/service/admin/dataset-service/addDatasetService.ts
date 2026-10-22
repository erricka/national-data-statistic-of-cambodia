import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api"; // adjust path as needed

export interface AddDatasetPayload {
    file: File;
    sourceId: string;
    sourceName: string;
    ownerId: string;
    mode: string;
    description: string;
    conflictColumn?: string;
    [key: string]: any;
}

// Payload object contained inside 'data' field returned by backend
export interface AddDatasetResponse {
    uploadId: string;
    sourceId: string;
    tableName: string;
    mode: string;
    rowsInserted: number;
    rowsBefore: number;
    rowsAfter: number;
    tableColumns: string[];
    async: boolean;
}

/**
 * Call create dataset/file upload API endpoint: DTSR002
 */
export const addDataset = async (
    payload: AddDatasetPayload
): Promise<ApiResponse<AddDatasetResponse>> => {
    const formData = new FormData();
    formData.append("file", payload.file);
    formData.append("sourceId", payload.sourceId);
    formData.append("sourceName", payload.sourceName);
    formData.append("ownerId", payload.ownerId || "ADMIN");
    formData.append("mode", payload.mode || "APPEND");
    formData.append("conflictColumn", payload.conflictColumn || "");
    formData.append("description", payload.description || "");

    return await http.post<AddDatasetResponse>("/DTSR002", formData, {
        headers: {
            "Content-Type": "multipart/form-data",
        },
    });
};