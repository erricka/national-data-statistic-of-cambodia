import { APPROVAL_STATUS_BADGE } from "../../service/admin/approval-service/approvalStatus";
import type { ApprovalStatus } from "../../service/admin/approval-service/approvalStatus";

interface ApprovalStatusBadgeProps {
    status: ApprovalStatus;
    /** Raw backend value, shown when the status is not recognized. */
    rawStatus?: string;
}

/** Status pill using the existing `.badge` classes (same markup as ViewDataDetailPage). */
export default function ApprovalStatusBadge({ status, rawStatus }: ApprovalStatusBadgeProps) {
    const config = APPROVAL_STATUS_BADGE[status];
    const label = status === "UNKNOWN" && rawStatus ? rawStatus : config.label;

    return (
        <span className={`badge ${config.className}`}>
            <span className="d"></span>
            {label}
        </span>
    );
}
