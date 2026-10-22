import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";


export interface HeaderItem {
    title: string;
}

export interface DataDetailPayload {
    header: HeaderItem[][];
    list: (string | number | null)[][];
}
export interface FileInfo {
    sourceId: string;
    fileName: string;
    fileSize: number;
    formattedSize: string;
    extension: string;
}

// Request payload interface
export interface DataDetailPayloadRequest {
    sourceId: string;
    upload_id: string;
}

/**
 * Call data detail inquiry API endpoint: DTSI003
 */
export const getDataDetail = async (
    payload: DataDetailPayloadRequest = { sourceId: "", upload_id: "" }
): Promise<ApiResponse<DataDetailPayload>> => {
    return await http.post<DataDetailPayload>("/DTSI003", payload);
};

/**
 * Get file metadata via GET request
 * Endpoint: /files/{sourceId}
 */
export const getFileInfo = async (
    uploadId: string
): Promise<ApiResponse<FileInfo>> => {
    return await http.get<FileInfo>(`/files/${uploadId}`);
};

/**
 * Download dataset file via GET request
 * Endpoint: /files/{sourceId}/download
 */
export const downloadDataDetailFile = async (
    uploadId: string
): Promise<ApiResponse<Blob>> => {
    return await http.get<Blob>(`/files/${uploadId}/download`, {
        responseType: "blob",
    });
};