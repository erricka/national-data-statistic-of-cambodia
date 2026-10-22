import { http } from "../../../api/apiClient";
import type { ApiResponse } from "../../../api/api";

export interface DashboardStatsData {
    totalRecords: number;
    totalPublished: number;
    publishedThisMonth: number;
}

/**
 * Call dashboard statistics API endpoint: DTSI010
 */
export const getDashboardStats = async (): Promise<ApiResponse<DashboardStatsData>> => {
    return await http.post("/DTSI010");
};