import type { ApprovalStatus } from "./approvalStatus";

/* ------------------------------------------------------------------ */
/* Requests — field names follow the backend contract exactly.        */
/* ------------------------------------------------------------------ */

/** APPI001 */
export interface ApprovalListRequest {
    status: string;
    sourceId: string;
    search: string;
    page: number; // 1-indexed, same as other list APIs in this project
    size: number;
}

/** Shared by APPI002 and APPI004 */
export interface UploadIdRequest {
    uploadId: string;
}

/** APPI002 */
export type UploadDetailRequest = UploadIdRequest;

/** APPI004 */
export type AuditHistoryRequest = UploadIdRequest;

/** APPI003 */
export interface StagedDataRequest {
    sourceId: string;
    uploadId: string;
    page: number;
    size: number;
}

/** APPR001 — comment is optional in the UI; an empty string is sent when blank. */
export interface ApproveUploadRequest {
    uploadId: string;
    comment: string;
}

/** APPR002 — reason is mandatory. */
export interface RejectUploadRequest {
    uploadId: string;
    reason: string;
}

/** APPR003 */
export interface CancelUploadRequest {
    uploadId: string;
    reason: string;
}

/* ------------------------------------------------------------------ */
/* Raw responses — the backend response bodies are not fully          */
/* documented, so they are typed loosely and normalized in            */
/* approvalMappers.ts. UI components only consume normalized models.  */
/* ------------------------------------------------------------------ */

export type RawRecord = Record<string, unknown>;

export interface RawPagedData {
    list?: unknown[];
    page?: unknown;
    size?: unknown;
    total?: unknown;
    totalPages?: unknown;
    [key: string]: unknown;
}

/**
 * APPI003 raw response. Backend (previewApprovalData) returns:
 *   title, subTitle, header (rows of { title }), list (rows of values), total, totalPages, page, size
 * `tb_headers` / `tb_rows` from the original spec are still accepted.
 */
export interface RawStagedData {
    header?: unknown[];
    list?: unknown[];
    tb_headers?: unknown[];
    tb_rows?: unknown[];
    [key: string]: unknown;
}

/* ------------------------------------------------------------------ */
/* Normalized models consumed by the UI                               */
/* ------------------------------------------------------------------ */

export interface ApprovalListItem {
    uploadId: string;
    sourceId: string;
    datasetName: string;
    datasetCode: string;
    categoryName: string;
    fileName: string;
    submitterId: string;
    submitterName: string;
    submittedAt: string; // raw backend value; format with formatApprovalDateTime
    totalRows: number | null;
    status: ApprovalStatus;
    rawStatus: string;
}

export type ApprovalStatusCounts = Partial<Record<ApprovalStatus, number>>;

/** APPI001 normalized response */
export interface ApprovalListData {
    list: ApprovalListItem[];
    page: number;
    size: number;
    total: number;
    totalPages: number;
    /** Only populated when the backend returns per-status counts. */
    statusCounts: ApprovalStatusCounts;
}

/**
 * APPI002 normalized response.
 * Matches backend query `retrieveApprovalDetail` (datasource_history + datasource + category).
 * The backend does not return file size, record count, or submitter display name.
 */
export interface UploadDetail {
    uploadId: string;
    sourceId: string;
    datasetName: string; // datasource.source_name
    datasetCode: string; // source_id
    uploadTitle: string; // title typed by the uploader; empty when backend fell back to the dataset name
    description: string;
    targetTable: string;
    categoryName: string;
    fileName: string; // stored file name, derived from filePath
    submitterId: string; // user ID (first_register_user)
    submittedAt: string;
    status: ApprovalStatus;
    rawStatus: string;
}

export type StagedCellValue = string | number | boolean | null;

export interface StagedDataHeader {
    key: string;
    label: string;
}

export type StagedDataRow = Record<string, StagedCellValue>;

/** One <th> in the (possibly grouped) table header. */
export interface StagedHeaderCell {
    title: string;
    colSpan: number;
    rowSpan: number;
}

/** APPI003 normalized response */
export interface StagedData {
    title: string;
    subTitle: string;
    /** Leaf columns, in order; body cells are looked up by `key`. */
    headers: StagedDataHeader[];
    /** Header rows ready to render (1 row, or 2 rows when columns are grouped). */
    headerRows: StagedHeaderCell[][];
    rows: StagedDataRow[];
    page: number;
    size: number;
    total: number;
    /** False when APPI003 did not report a total and `total` is only the row count of this page. */
    totalKnown: boolean;
    totalPages: number;
}

export interface AuditDetailItem {
    label: string;
    value: string;
}

export interface AuditHistoryEntry {
    id: string;
    action: string;
    status: ApprovalStatus;
    actorId: string;
    actorName: string;
    actionAt: string;
    comment: string;
    /** Any additional scalar fields returned by APPI004 that are not mapped above. */
    details: AuditDetailItem[];
}

/** APPI004 normalized response */
export interface AuditHistoryData {
    list: AuditHistoryEntry[];
}
