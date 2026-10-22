/**
 * Approval status — single source of truth.
 *
 * No approval status codes existed in the project before this feature, so the
 * mapping below follows the backend contract supplied for Approval Management.
 * If the backend contract changes, update ONLY this file.
 */

export const APPROVAL_STATUS = {
    PENDING: "PENDING",
    APPROVED: "APPROVED",
    REJECTED: "REJECTED",
    CANCELLED: "CANCELLED",
    UNKNOWN: "UNKNOWN",
} as const;

export type ApprovalStatus = (typeof APPROVAL_STATUS)[keyof typeof APPROVAL_STATUS];

export type KnownApprovalStatus = Exclude<ApprovalStatus, "UNKNOWN">;

/** Backend status codes used by APPI001 `status` and returned on records. */
export const APPROVAL_STATUS_CODE: Record<KnownApprovalStatus, string> = {
    APPROVED: "01",
    REJECTED: "02",
    PENDING: "03",
    CANCELLED: "04",
};

/**
 * Tabs shown on the Approval Management list page, in display order.
 * The fourth tab is labelled "All / Cancelled" per the feature spec and
 * requests the cancelled status code.
 */
export const APPROVAL_STATUS_TABS: { key: KnownApprovalStatus; label: string; code: string }[] = [
    { key: APPROVAL_STATUS.PENDING, label: "Pending", code: APPROVAL_STATUS_CODE.PENDING },
    { key: APPROVAL_STATUS.APPROVED, label: "Approved", code: APPROVAL_STATUS_CODE.APPROVED },
    { key: APPROVAL_STATUS.REJECTED, label: "Rejected", code: APPROVAL_STATUS_CODE.REJECTED },
    { key: APPROVAL_STATUS.CANCELLED, label: "All / Cancelled", code: APPROVAL_STATUS_CODE.CANCELLED },
];

/** Maps each status to an existing `.badge-*` class from index.css. */
export const APPROVAL_STATUS_BADGE: Record<ApprovalStatus, { className: string; label: string }> = {
    PENDING: { className: "badge-warning", label: "Pending" },
    APPROVED: { className: "badge-success", label: "Approved" },
    REJECTED: { className: "badge-danger", label: "Rejected" },
    CANCELLED: { className: "badge-neutral", label: "Cancelled" },
    UNKNOWN: { className: "badge-neutral", label: "Unknown" },
};

const CODE_TO_STATUS: Record<string, KnownApprovalStatus> = Object.fromEntries(
    Object.entries(APPROVAL_STATUS_CODE).map(([status, code]) => [code, status as KnownApprovalStatus])
);

const NAME_TO_STATUS: Record<string, KnownApprovalStatus> = {
    PENDING: "PENDING",
    APPROVED: "APPROVED",
    REJECTED: "REJECTED",
    CANCELLED: "CANCELLED",
    CANCELED: "CANCELLED",
};

/**
 * Accepts either a backend code ("03") or a status name ("PENDING", "pending")
 * and returns a normalized status. Anything unrecognized becomes UNKNOWN.
 */
export const resolveApprovalStatus = (raw: unknown): ApprovalStatus => {
    if (raw === null || raw === undefined) return APPROVAL_STATUS.UNKNOWN;
    const value = String(raw).trim();
    if (!value) return APPROVAL_STATUS.UNKNOWN;

    const byCode = CODE_TO_STATUS[value] ?? CODE_TO_STATUS[value.padStart(2, "0")];
    if (byCode) return byCode;

    return NAME_TO_STATUS[value.toUpperCase()] ?? APPROVAL_STATUS.UNKNOWN;
};
