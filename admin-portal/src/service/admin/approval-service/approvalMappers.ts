/**
 * Response normalizers for the Approval APIs.
 *
 * The APPI/APPR response bodies were not fully documented when this feature
 * was built. Every assumption about backend field names lives in this file
 * so UI components never depend on raw response shapes.
 *
 * Candidate keys are ordered: documented/camelCase name first, then names
 * already used by other NIS endpoints (e.g. DTSI009 `title`,
 * `categoryDescription`, `totalRecord`; DTSR002 `tableName`, `ownerId`,
 * `sourceName`), then snake_case variants.
 */
import { APPROVAL_STATUS, APPROVAL_STATUS_CODE, resolveApprovalStatus } from "./approvalStatus";
import type { ApprovalStatus, KnownApprovalStatus } from "./approvalStatus";
import type {
    ApprovalListData,
    ApprovalListItem,
    ApprovalStatusCounts,
    AuditHistoryData,
    AuditHistoryEntry,
    RawPagedData,
    RawRecord,
    RawStagedData,
    StagedCellValue,
    StagedData,
    StagedDataHeader,
    StagedDataRow,
    StagedHeaderCell,
    UploadDetail,
} from "./approvalTypes";

/* ----------------------------- helpers ----------------------------- */

const isRecord = (value: unknown): value is RawRecord =>
    typeof value === "object" && value !== null && !Array.isArray(value);

const pickString = (raw: RawRecord, keys: string[]): string => {
    for (const key of keys) {
        const value = raw[key];
        if (typeof value === "string" && value.trim()) return value.trim();
        if (typeof value === "number" && Number.isFinite(value)) return String(value);
    }
    return "";
};

const toNumber = (value: unknown): number | null => {
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim()) {
        const parsed = Number(value.replace(/,/g, ""));
        return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
};

const pickNumber = (raw: RawRecord, keys: string[]): number | null => {
    for (const key of keys) {
        const value = toNumber(raw[key]);
        if (value !== null) return value;
    }
    return null;
};

const positiveInt = (value: unknown, fallback: number): number => {
    const n = toNumber(value);
    return n !== null && n > 0 ? Math.floor(n) : fallback;
};

/** "/path/to/file.xlsx" or "C:\\path\\file.xlsx" -> "file.xlsx" (never shows the server path) */
const fileNameFromPath = (path: string): string => path.split(/[\\/]/).pop() ?? "";

/* ------------------------- shared key lists ------------------------ */
// Used only by the APPI001 list mapper below, whose response shape is not yet confirmed.
// Once the backend query for APPI001 is known, replace these with the exact field names
// (as done for APPI002) and remove this block.

const KEYS = {
    uploadId: ["uploadId", "upload_id"],
    sourceId: ["sourceId", "source_id"],
    datasetName: ["datasetName", "datasetTitle", "sourceName", "title", "dataset_name", "source_name"],
    datasetCode: ["datasetCode", "code", "dataset_code"],
    categoryName: ["categoryName", "categoryDescription", "category", "category_name"],
    fileName: ["fileName", "uploadFileName", "originalFileName", "file_name"],
    submitterId: ["submitterId", "submittedBy", "createdBy", "uploadedBy", "ownerId", "submitted_by", "created_by"],
    submitterName: ["submitterName", "submittedByName", "createdByName", "uploadedByName", "submitter_name"],
    submittedAt: ["submittedAt", "submitDate", "createdAt", "createDate", "uploadDate", "submitted_at", "created_at"],
    totalRows: ["totalRows", "totalRecords", "totalRecord", "rowCount", "rowsInserted", "total_rows", "total_record"],
    status: ["status", "approvalStatus", "uploadStatus", "approval_status"],
};

/** Prefers an explicit file name; otherwise derives it from filePath (APPI002 returns only the path). */
const readFileName = (raw: RawRecord): string =>
    pickString(raw, KEYS.fileName) || fileNameFromPath(pickString(raw, ["filePath", "file_path"]));

const readStatus = (raw: RawRecord): { status: ApprovalStatus; rawStatus: string } => {
    const rawStatus = pickString(raw, KEYS.status);
    return { status: resolveApprovalStatus(rawStatus), rawStatus };
};

/* ------------------------------ APPI001 ---------------------------- */

export const normalizeApprovalListItem = (raw: unknown): ApprovalListItem => {
    const r = isRecord(raw) ? raw : {};
    const sourceId = pickString(r, KEYS.sourceId);
    const submitterId = pickString(r, KEYS.submitterId);

    return {
        uploadId: pickString(r, KEYS.uploadId),
        sourceId,
        datasetName: pickString(r, KEYS.datasetName),
        // Existing dataset screens show sourceId as the dataset code.
        datasetCode: pickString(r, KEYS.datasetCode) || sourceId,
        categoryName: pickString(r, KEYS.categoryName),
        fileName: readFileName(r),
        submitterId,
        submitterName: pickString(r, KEYS.submitterName) || submitterId,
        submittedAt: pickString(r, KEYS.submittedAt),
        totalRows: pickNumber(r, KEYS.totalRows),
        ...readStatus(r),
    };
};

/**
 * Per-status counts are optional. Supported shapes (first match wins):
 *   { statusCounts: { "03": 5, ... } }  or  { statusCounts: { PENDING: 5 } }
 *   { counts: {...} }                   (same keys as above)
 *   { pendingCount: 5, approvedCount: 1, rejectedCount: 2, cancelledCount: 4 }
 */
const normalizeStatusCounts = (raw: RawRecord): ApprovalStatusCounts => {
    const counts: ApprovalStatusCounts = {};
    const nested = isRecord(raw.statusCounts) ? raw.statusCounts : isRecord(raw.counts) ? raw.counts : null;

    if (nested) {
        for (const [key, value] of Object.entries(nested)) {
            const status = resolveApprovalStatus(key);
            const n = toNumber(value);
            if (status !== APPROVAL_STATUS.UNKNOWN && n !== null) counts[status] = n;
        }
        return counts;
    }

    (Object.keys(APPROVAL_STATUS_CODE) as KnownApprovalStatus[]).forEach((status) => {
        const n = pickNumber(raw, [`${status.toLowerCase()}Count`]);
        if (n !== null) counts[status] = n;
    });
    if (counts.CANCELLED === undefined) {
        const n = pickNumber(raw, ["canceledCount"]);
        if (n !== null) counts.CANCELLED = n;
    }
    return counts;
};

export const normalizeApprovalListData = (raw: unknown, requestedSize: number): ApprovalListData => {
    const r: RawPagedData = isRecord(raw) ? raw : {};
    const list = Array.isArray(r.list) ? r.list.map(normalizeApprovalListItem) : [];
    const size = positiveInt(r.size, requestedSize);
    const total = toNumber(r.total) ?? list.length;

    return {
        list,
        page: positiveInt(r.page, 1),
        size,
        total,
        totalPages: positiveInt(r.totalPages, Math.max(1, Math.ceil(total / size))),
        statusCounts: normalizeStatusCounts(r),
    };
};

/* ------------------------------ APPI002 ---------------------------- */

/**
 * APPI002 — mapped to the exact fields of backend query `retrieveApprovalDetail`:
 *   historyId, uploadId, sourceId, title, sourceName, tableName, categoryId,
 *   categoryDescription, description, filePath, status, statusLabel,
 *   submittedBy, submittedAt, rawData
 */
export const normalizeUploadDetail = (raw: unknown, requestedUploadId: string): UploadDetail => {
    const r = isRecord(raw) ? raw : {};
    const sourceId = pickString(r, ["sourceId"]);
    const sourceName = pickString(r, ["sourceName"]);
    const rawStatus = pickString(r, ["status"]);

    // title = COALESCE(upload title typed by uploader, source_name, source_id):
    // only treat it as an upload title when it is not one of the fallbacks.
    const title = pickString(r, ["title"]);
    const uploadTitle = title && title !== sourceName && title !== sourceId ? title : "";

    return {
        uploadId: pickString(r, ["uploadId"]) || requestedUploadId,
        sourceId,
        datasetName: sourceName || sourceId,
        datasetCode: sourceId,
        uploadTitle,
        description: pickString(r, ["description"]),
        targetTable: pickString(r, ["tableName"]),
        categoryName: pickString(r, ["categoryDescription"]),
        fileName: fileNameFromPath(pickString(r, ["filePath"])),
        submitterId: pickString(r, ["submittedBy"]),
        submittedAt: pickString(r, ["submittedAt"]),
        status: resolveApprovalStatus(rawStatus),
        rawStatus,
    };
};

/* ------------------------------ APPI003 ---------------------------- */

const toCellValue = (value: unknown): StagedCellValue => {
    if (value === null || value === undefined) return null;
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
    try {
        return JSON.stringify(value);
    } catch {
        return String(value);
    }
};

const headerTitle = (cell: unknown): string => {
    if (typeof cell === "string" || typeof cell === "number") return String(cell).trim();
    if (isRecord(cell)) return pickString(cell, ["title", "label", "name", "key", "field", "column"]);
    return "";
};

/**
 * Backend format (same as DTSI003 on the public Data Detail page):
 *   header[0] = group titles, "" = continues the group on its left
 *   header[last] = column titles
 * Produces render-ready header rows (with colSpan/rowSpan) and the leaf column list.
 */
const buildGroupedHeaders = (rawRows: unknown[]): { headers: StagedDataHeader[]; headerRows: StagedHeaderCell[][] } => {
    const levels = rawRows.filter(Array.isArray).map((row) => (row as unknown[]).map(headerTitle));
    if (levels.length === 0) return { headers: [], headerRows: [] };

    const top = levels[0];
    const bottom = levels.length > 1 ? levels[levels.length - 1] : [];
    const count = Math.max(top.length, bottom.length);
    const hasBottom = bottom.some((t) => t !== "");

    const headers: StagedDataHeader[] = [];
    for (let i = 0; i < count; i++) {
        const label = (hasBottom ? bottom[i] : "") || top[i] || `Column ${i + 1}`;
        headers.push({ key: `c${i}`, label });
    }

    if (!hasBottom) {
        return { headers, headerRows: [headers.map((h) => ({ title: h.label, colSpan: 1, rowSpan: 1 }))] };
    }

    const row1: StagedHeaderCell[] = [];
    const row2: StagedHeaderCell[] = [];
    let i = 0;
    while (i < count) {
        const groupTitle = top[i] ?? "";
        if (!groupTitle) {
            // No group above this column: the column title fills both header rows
            row1.push({ title: headers[i].label, colSpan: 1, rowSpan: 2 });
            i++;
            continue;
        }
        let span = 1;
        while (i + span < count && (top[i + span] ?? "") === "" && (bottom[i + span] ?? "") !== "") span++;

        const isGroup = span > 1 || (bottom[i] ?? "") !== "";
        if (isGroup) {
            row1.push({ title: groupTitle, colSpan: span, rowSpan: 1 });
            for (let j = i; j < i + span; j++) row2.push({ title: bottom[j] || headers[j].label, colSpan: 1, rowSpan: 1 });
        } else {
            row1.push({ title: groupTitle, colSpan: 1, rowSpan: 2 });
        }
        i += span;
    }

    // No grouped columns at all: collapse to a single header row
    if (row2.length === 0) return { headers, headerRows: [row1.map((c) => ({ ...c, rowSpan: 1 }))] };
    return { headers, headerRows: [row1, row2] };
};

/** Spec format: tb_headers as strings or { key, label } objects. */
const buildFlatHeaders = (rawHeaders: unknown[]): StagedDataHeader[] => {
    const seen = new Set<string>();
    return rawHeaders.map((h, index) => {
        let key = "";
        let label = "";
        if (typeof h === "string" || typeof h === "number") {
            key = String(h);
            label = key;
        } else if (isRecord(h)) {
            key = pickString(h, ["key", "field", "column", "name", "title"]);
            label = pickString(h, ["label", "title", "name", "key", "field", "column"]);
        }
        if (!key) key = `column_${index + 1}`;
        if (seen.has(key)) key = `${key}_${index + 1}`;
        seen.add(key);
        return { key, label: label || key };
    });
};

export const normalizeStagedData = (raw: unknown, requestedPage: number, requestedSize: number): StagedData => {
    const r: RawStagedData = isRecord(raw) ? raw : {};
    const isBackendFormat = Array.isArray(r.header) || Array.isArray(r.list);
    const rawRows = (isBackendFormat ? r.list : r.tb_rows) ?? [];
    const rowsIn = Array.isArray(rawRows) ? rawRows : [];

    let headers: StagedDataHeader[];
    let headerRows: StagedHeaderCell[][];

    if (isBackendFormat) {
        ({ headers, headerRows } = buildGroupedHeaders(Array.isArray(r.header) ? r.header : []));
    } else {
        headers = buildFlatHeaders(Array.isArray(r.tb_headers) ? r.tb_headers : []);
        headerRows = [];
    }

    // Fallback: derive headers from the rows when none were sent
    if (headers.length === 0 && rowsIn.length > 0) {
        const first = rowsIn[0];
        if (Array.isArray(first)) {
            headers = first.map((_, i) => ({ key: `c${i}`, label: `Column ${i + 1}` }));
        } else {
            const keys: string[] = [];
            rowsIn.forEach((row) => isRecord(row) && Object.keys(row).forEach((k) => !keys.includes(k) && keys.push(k)));
            headers = buildFlatHeaders(keys);
        }
    }
    if (headerRows.length === 0) {
        headerRows = headers.length ? [headers.map((h) => ({ title: h.label, colSpan: 1, rowSpan: 1 }))] : [];
    }

    const rows: StagedDataRow[] = rowsIn.map((row) => {
        const out: StagedDataRow = {};
        headers.forEach((header, index) => {
            if (Array.isArray(row)) out[header.key] = toCellValue(row[index]);
            else if (isRecord(row)) out[header.key] = toCellValue(row[header.key] ?? row[header.label]);
            else out[header.key] = null;
        });
        return out;
    });

    const size = positiveInt(r.size, requestedSize);
    const reportedTotal = toNumber(r.total) ?? toNumber(r.totalRecords) ?? toNumber(r.totalCount);
    const total = reportedTotal ?? rows.length;

    return {
        title: pickString(r as RawRecord, ["title"]),
        subTitle: pickString(r as RawRecord, ["subTitle", "sub_title"]),
        headers,
        headerRows,
        rows,
        page: positiveInt(r.page, requestedPage),
        size,
        total,
        totalKnown: reportedTotal !== null,
        totalPages: positiveInt(r.totalPages, Math.max(1, Math.ceil(total / size))),
    };
};

/* ------------------------------ APPI004 ---------------------------- */

const AUDIT_KEYS = {
    id: ["auditId", "historyId", "id", "seq"],
    action: ["action", "actionName", "activity", "event", "actionType"],
    status: ["status", "approvalStatus"],
    actorId: ["actorId", "actionBy", "userId", "userID", "reviewerId", "createdBy"],
    actorName: ["actorName", "actionByName", "userName", "reviewerName", "createdByName"],
    actionAt: ["actionAt", "actionDate", "createdAt", "createDate", "timestamp", "date"],
    comment: ["comment", "reason", "remark", "note", "description"],
};

const MAPPED_AUDIT_KEYS = new Set<string>([...Object.values(AUDIT_KEYS).flat(), "uploadId", "upload_id"]);

const humanizeKey = (key: string): string =>
    key
        .replace(/_/g, " ")
        .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
        .replace(/^./, (c) => c.toUpperCase());

const normalizeAuditEntry = (raw: unknown, index: number): AuditHistoryEntry => {
    const r = isRecord(raw) ? raw : {};
    const action = pickString(r, AUDIT_KEYS.action);
    const statusSource = pickString(r, AUDIT_KEYS.status) || action;
    const actorId = pickString(r, AUDIT_KEYS.actorId);

    const details = Object.entries(r)
        .filter(([key, value]) => !MAPPED_AUDIT_KEYS.has(key) && value !== null && value !== undefined && value !== "")
        .filter(([, value]) => typeof value === "string" || typeof value === "number" || typeof value === "boolean")
        .map(([key, value]) => ({ label: humanizeKey(key), value: String(value) }));

    return {
        id: pickString(r, AUDIT_KEYS.id) || `audit-${index}`,
        action,
        status: resolveApprovalStatus(statusSource),
        actorId,
        actorName: pickString(r, AUDIT_KEYS.actorName) || actorId,
        actionAt: pickString(r, AUDIT_KEYS.actionAt),
        comment: pickString(r, AUDIT_KEYS.comment),
        details,
    };
};

/** Accepts `data` as an array, or as an object holding `list` / `history`. */
export const normalizeAuditHistory = (raw: unknown): AuditHistoryData => {
    let items: unknown[] = [];
    if (Array.isArray(raw)) items = raw;
    else if (isRecord(raw) && Array.isArray(raw.list)) items = raw.list;
    else if (isRecord(raw) && Array.isArray(raw.history)) items = raw.history;

    return { list: items.map(normalizeAuditEntry) };
};