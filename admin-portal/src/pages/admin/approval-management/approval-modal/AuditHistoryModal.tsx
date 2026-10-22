import { useCallback, useEffect, useState } from "react";
import ApprovalModalShell from "../../../../components/approval/ApprovalModalShell";
import AuditTimeline from "../../../../components/approval/AuditTimeline";
import ApprovalStatusBadge from "../../../../components/approval/ApprovalStatusBadge";
import { getAuditHistory } from "../../../../service/admin/approval-service/auditHistoryService";
import { getApprovalErrorMessage } from "../../../../service/admin/approval-service/approvalErrors";
import type { ApprovalListItem, AuditHistoryEntry } from "../../../../service/admin/approval-service/approvalTypes";
import { formatApprovalDateTime } from "../../../../utils/approvalFormatters";

interface AuditHistoryModalProps {
    item: ApprovalListItem | null;
    onClose: () => void;
}

export default function AuditHistoryModal({ item, onClose }: AuditHistoryModalProps) {
    const [reload, setReload] = useState(0);
    const [result, setResult] = useState<{ key: string; entries: AuditHistoryEntry[]; error: string } | null>(null);

    const uploadId = item?.uploadId ?? "";
    const requestKey = uploadId ? `${uploadId}|${reload}` : "";
    const isLoading = requestKey !== "" && result?.key !== requestKey;

    useEffect(() => {
        if (!requestKey) return;
        let active = true; // ignore a late response after the user opened another row

        getAuditHistory({ uploadId })
            .then((res) => {
                if (!active) return;
                if (res?.code === "000000") {
                    setResult({ key: requestKey, entries: res.data?.list ?? [], error: "" });
                } else {
                    setResult({ key: requestKey, entries: [], error: "Failed to load audit history." });
                }
            })
            .catch((error) => {
                if (!active) return;
                console.error("Audit history fetch failed:", error);
                setResult({
                    key: requestKey,
                    entries: [],
                    error: getApprovalErrorMessage(error, "Failed to load audit history."),
                });
            });

        return () => {
            active = false;
        };
    }, [requestKey, uploadId]);

    // Only show results that belong to the currently selected upload
    const isCurrent = result?.key === requestKey;
    const entries = isCurrent ? result.entries : [];
    const errorMessage = isCurrent ? result.error : "";
    const retry = useCallback(() => setReload((n) => n + 1), []);

    return (
        <ApprovalModalShell isOpen={!!item} title="Audit History" onClose={onClose} maxWidth="640px">
            {item && (
                <>
                    <div className="card" style={{ padding: "14px 16px", marginBottom: "18px", boxShadow: "none" }}>
                        <div className="row between" style={{ alignItems: "flex-start", gap: "12px" }}>
                            <div style={{ minWidth: 0 }}>
                                <div className="cell-main" style={{ overflowWrap: "anywhere" }}>{item.datasetName || "-"}</div>
                                <div className="cell-sub" style={{ overflowWrap: "anywhere" }}>
                                    {item.fileName || item.uploadId} · Submitted by {item.submitterName || "-"} on{" "}
                                    {formatApprovalDateTime(item.submittedAt)}
                                </div>
                            </div>
                            <ApprovalStatusBadge status={item.status} rawStatus={item.rawStatus} />
                        </div>
                    </div>

                    <AuditTimeline entries={entries} isLoading={isLoading} errorMessage={errorMessage} onRetry={retry} />
                </>
            )}
        </ApprovalModalShell>
    );
}
