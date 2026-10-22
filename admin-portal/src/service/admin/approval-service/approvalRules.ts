/**
 * Approval business rules used by the UI.
 *
 * These checks exist for UX only (hiding/disabling actions). The backend is
 * the authority and must enforce the same rules on APPR001/APPR002/APPR003.
 */
import { APPROVAL_STATUS } from "./approvalStatus";
import type { ApprovalStatus } from "./approvalStatus";

/** Role allowed to approve/reject any pending upload, including its own. */
export const ADMIN_ROLE = "ADMIN";

interface UserWithRoles {
    roles?: unknown[];
    roleIds?: string[];
}

/** Case-insensitive user ID comparison. Returns false if either side is unknown. */
export const isSameUser = (a?: string | null, b?: string | null): boolean => {
    const left = (a ?? "").trim().toLowerCase();
    const right = (b ?? "").trim().toLowerCase();
    return left !== "" && left === right;
};

/** Roles may be plain strings ("ADMIN") or objects ({ roleId, name, ... }). */
const readRoleValues = (role: unknown): string[] => {
    if (typeof role === "string") return [role];
    if (role && typeof role === "object") {
        const r = role as Record<string, unknown>;
        return ["roleId", "name", "roleName", "code"]
            .map((key) => r[key])
            .filter((value): value is string => typeof value === "string");
    }
    return [];
};

export const hasAdminRole = (user?: UserWithRoles | null): boolean => {
    if (!user) return false;
    const values = [...(user.roles ?? []).flatMap(readRoleValues), ...(user.roleIds ?? [])];
    return values.some((value) => value.trim().toUpperCase() === ADMIN_ROLE);
};

/** User saved by LoginPage (localStorage "user"). Fallback only; useAuth() is preferred. */
export const getStoredUser = (): UserWithRoles | null => {
    try {
        const raw = localStorage.getItem("user");
        return raw ? (JSON.parse(raw) as UserWithRoles) : null;
    } catch {
        return null;
    }
};

/** Only the submitter may cancel, and only while the upload is still pending. */
export const canCancelUpload = (
    status: ApprovalStatus,
    submitterId: string,
    currentUserId?: string | null
): boolean => status === APPROVAL_STATUS.PENDING && isSameUser(submitterId, currentUserId);

export interface ReviewActionState {
    canReview: boolean;
    isSubmitter: boolean;
    reason: string;
}

const FINALIZED_STATUSES: ApprovalStatus[] = [
    APPROVAL_STATUS.APPROVED,
    APPROVAL_STATUS.REJECTED,
    APPROVAL_STATUS.CANCELLED,
];

/**
 * - Finalized uploads can never be approved/rejected again (anyone).
 * - ADMIN may approve/reject any non-finalized upload, including their own.
 * - Everyone else: Maker-Checker (cannot review own upload) and status must be PENDING.
 */
export const getReviewActionState = (
    status: ApprovalStatus,
    submitterId: string,
    currentUserId?: string | null,
    isAdmin = false
): ReviewActionState => {
    const isSubmitter = isSameUser(submitterId, currentUserId);

    if (FINALIZED_STATUSES.includes(status)) {
        return { canReview: false, isSubmitter, reason: "This upload has already been finalized." };
    }
    if (isAdmin) {
        return { canReview: true, isSubmitter, reason: "" };
    }
    if (status === APPROVAL_STATUS.UNKNOWN) {
        return {
            canReview: false,
            isSubmitter,
            reason: "The upload status could not be determined. Please refresh or contact the administrator.",
        };
    }
    if (isSubmitter) {
        return {
            canReview: false,
            isSubmitter,
            reason: "You submitted this upload. Another approver must review it.",
        };
    }
    return { canReview: true, isSubmitter, reason: "" };
};
