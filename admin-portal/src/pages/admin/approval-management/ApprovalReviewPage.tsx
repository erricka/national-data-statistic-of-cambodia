import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";
import { useAuth } from "../../../context/AuthContext";
import ApprovalStatusBadge from "../../../components/approval/ApprovalStatusBadge";
import ApprovalPagination from "../../../components/approval/ApprovalPagination";
import ApprovalToast from "../../../components/approval/ApprovalToast";
import type { ApprovalToastMessage } from "../../../components/approval/ApprovalToast";
import AuditTimeline from "../../../components/approval/AuditTimeline";
import StagedDataTable from "../../../components/approval/StagedDataTable";
import ApproveUploadModal from "./approval-modal/ApproveUploadModal";
import RejectUploadModal from "./approval-modal/RejectUploadModal";
import { getUploadDetails } from "../../../service/admin/approval-service/uploadDetailService";
import { getStagedData } from "../../../service/admin/approval-service/stagedDataService";
import { getAuditHistory } from "../../../service/admin/approval-service/auditHistoryService";
import { getApprovalErrorMessage } from "../../../service/admin/approval-service/approvalErrors";
import { getReviewActionState, getStoredUser, hasAdminRole } from "../../../service/admin/approval-service/approvalRules";
import { APPROVAL_STATUS } from "../../../service/admin/approval-service/approvalStatus";
import type { ApprovalStatus } from "../../../service/admin/approval-service/approvalStatus";
import type { AuditHistoryEntry, StagedData, UploadDetail } from "../../../service/admin/approval-service/approvalTypes";
import type { ApiResponse } from "../../../api/api";
import { formatApprovalDateTime, formatCount } from "../../../utils/approvalFormatters";

const APPROVAL_LIST_PATH = "/admin/approvals";
const PREVIEW_PAGE_SIZE = 50;
// How long the success message stays visible before returning to the list
const REDIRECT_DELAY_MS = 1500;

// Stable extractor: APPI002 returns a single object, exposed as a one-item list for usePermissionGuard
const uploadDetailExtractor = (res: ApiResponse<UploadDetail>) => ({
    items: res?.data ? [res.data] : [],
    total: res?.data ? 1 : 0,
    totalPages: 1,
});

const EMPTY_STAGED: StagedData = {
    title: "",
    subTitle: "",
    headers: [],
    headerRows: [],
    rows: [],
    page: 1,
    size: PREVIEW_PAGE_SIZE,
    total: 0,
    totalKnown: false,
    totalPages: 1,
};

export default function ApprovalReviewPage() {
    const navigate = useNavigate();
    const { uploadId = "" } = useParams<{ uploadId: string }>();
    const { user } = useAuth();

    // Decision state: overrides a still-PENDING refreshed status until the backend reflects the decision
    const [decidedStatus, setDecidedStatus] = useState<ApprovalStatus | null>(null);
    const [isApproveOpen, setIsApproveOpen] = useState(false);
    const [isRejectOpen, setIsRejectOpen] = useState(false);
    const [toast, setToast] = useState<ApprovalToastMessage | null>(null);
    const [redirectAfterDecision, setRedirectAfterDecision] = useState(false);

    /* ------------------------------ APPI002 ------------------------------ */

    const uploadIdRef = useRef(uploadId);
    useEffect(() => {
        uploadIdRef.current = uploadId;
    }, [uploadId]);

    const detailFetcher = useCallback(() => getUploadDetails({ uploadId: uploadIdRef.current }), []);

    const {
        data: detailList,
        isLoading: isDetailLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage: detailError,
        executeFetch: fetchDetail,
    } = usePermissionGuard<UploadDetail>(detailFetcher, uploadDetailExtractor);

    const detail = detailList[0] ?? null;

    useEffect(() => {
        if (uploadId) fetchDetail();
    }, [uploadId, fetchDetail]);

    /* ------------------------------ APPI003 ------------------------------ */

    const [previewPage, setPreviewPage] = useState(1);
    const [previewReload, setPreviewReload] = useState(0);
    const [previewResult, setPreviewResult] = useState<{ key: string; data: StagedData; error: string } | null>(null);

    const [auditReload, setAuditReload] = useState(0);
    const [auditResult, setAuditResult] = useState<{ key: string; entries: AuditHistoryEntry[]; error: string } | null>(null);

    // Navigating between uploads reuses this component: reset per-upload state during render
    const [trackedUploadId, setTrackedUploadId] = useState(uploadId);
    if (trackedUploadId !== uploadId) {
        setTrackedUploadId(uploadId);
        setPreviewPage(1);
        setPreviewResult(null);
        setAuditResult(null);
        setDecidedStatus(null);
        setRedirectAfterDecision(false);
    }

    // sourceId comes from APPI002 — never hardcoded
    const sourceId = detail?.sourceId ?? "";
    const detailUploadId = detail?.uploadId ?? "";

    // Loading state is derived: a request is in flight while its key differs from the last completed one
    const previewKey = detailUploadId ? `${detailUploadId}|${sourceId}|${previewPage}|${previewReload}` : "";
    const isPreviewLoading = previewKey !== "" && previewResult?.key !== previewKey;

    useEffect(() => {
        if (!previewKey) return;
        let active = true; // ignores out-of-order responses when paging quickly

        getStagedData({ sourceId, uploadId: detailUploadId, page: previewPage, size: PREVIEW_PAGE_SIZE })
            .then((res) => {
                if (!active) return;
                if (res?.code === "000000" && res.data) {
                    setPreviewResult({ key: previewKey, data: res.data, error: "" });
                } else {
                    setPreviewResult({ key: previewKey, data: EMPTY_STAGED, error: "Failed to load preview data." });
                }
            })
            .catch((error) => {
                if (!active) return;
                console.error("Staged data fetch failed:", error);
                setPreviewResult({
                    key: previewKey,
                    data: EMPTY_STAGED,
                    error: getApprovalErrorMessage(error, "Failed to load preview data."),
                });
            });

        return () => {
            active = false;
        };
    }, [previewKey, sourceId, detailUploadId, previewPage]);

    const staged = previewResult?.data ?? EMPTY_STAGED;
    const previewError = previewResult?.error ?? "";
    const reloadPreview = useCallback(() => setPreviewReload((n) => n + 1), []);

    /* ------------------------------ APPI004 ------------------------------ */

    const auditKey = uploadId ? `${uploadId}|${auditReload}` : "";
    const isAuditLoading = auditKey !== "" && auditResult?.key !== auditKey;

    useEffect(() => {
        if (!auditKey) return;
        let active = true;

        getAuditHistory({ uploadId })
            .then((res) => {
                if (!active) return;
                if (res?.code === "000000") {
                    setAuditResult({ key: auditKey, entries: res.data?.list ?? [], error: "" });
                } else {
                    setAuditResult({ key: auditKey, entries: [], error: "Failed to load audit history." });
                }
            })
            .catch((error) => {
                if (!active) return;
                console.error("Audit history fetch failed:", error);
                setAuditResult({
                    key: auditKey,
                    entries: [],
                    error: getApprovalErrorMessage(error, "Failed to load audit history."),
                });
            });

        return () => {
            active = false;
        };
    }, [auditKey, uploadId]);

    const auditEntries = auditResult?.entries ?? [];
    const auditError = isAuditLoading ? "" : auditResult?.error ?? "";
    const reloadAudit = useCallback(() => setAuditReload((n) => n + 1), []);

    /* --------------------------- Maker-Checker --------------------------- */

    const baseStatus = detail?.status ?? APPROVAL_STATUS.UNKNOWN;
    const effectiveStatus: ApprovalStatus =
        baseStatus === APPROVAL_STATUS.PENDING && decidedStatus ? decidedStatus : baseStatus;

    // ADMIN can approve/reject any non-finalized upload (backend must enforce this too)
    const isAdmin = hasAdminRole(user) || hasAdminRole(getStoredUser());
    const reviewState = getReviewActionState(effectiveStatus, detail?.submitterId ?? "", user?.userID, isAdmin);
    const actionsDisabled = !reviewState.canReview || isDetailLoading;

    const handleDecisionSuccess = (status: ApprovalStatus) => (message: string) => {
        setIsApproveOpen(false);
        setIsRejectOpen(false);
        // Lock the buttons and show the new status while the success message is visible
        setDecidedStatus(status);
        setToast({ id: Date.now(), type: "success", text: `${message} Returning to the approval list…` });
        setRedirectAfterDecision(true);
    };

    // After a successful approve/reject, go back to the list once the message has been shown.
    // `replace` stops the browser Back button from returning to the finalized upload.
    useEffect(() => {
        if (!redirectAfterDecision) return;
        const timer = setTimeout(() => navigate(APPROVAL_LIST_PATH, { replace: true }), REDIRECT_DELAY_MS);
        return () => clearTimeout(timer);
    }, [redirectAfterDecision, navigate]);

    const dismissToast = useCallback(() => setToast(null), []);

    /* ------------------------------ Render ------------------------------- */

    const previewTotal = staged.total;
    // APPI002 has no record count; use APPI003's total only when the backend actually reports one
    const totalRecords = previewResult && !previewResult.error && staged.totalKnown ? staged.total : null;

    const backButton = (
        <button
            className="btn btn-outline btn-sm"
            style={{ padding: "6px 10px", marginBottom: "12px" }}
            onClick={() => navigate(APPROVAL_LIST_PATH)}
        >
            <Icon name="arrow-left" /> Back
        </button>
    );

    const renderFailure = (message: string) => (
        <>
            {backButton}
            <div className="card card-pad" style={{ textAlign: "center", padding: "48px 20px", color: "#64748b" }}>
                <div style={{ color: "var(--danger, #ef4444)", marginBottom: "16px" }}>{message}</div>
                {uploadId && (
                    <button className="btn btn-outline btn-sm" onClick={() => fetchDetail()} disabled={isDetailLoading}>
                        Retry
                    </button>
                )}
            </div>
        </>
    );

    return (
        <AdminPageContainer
            sidebarActive="approval_management"
            pageTitle="Review Upload"
            hasInitialLoaded={hasInitialLoaded || !uploadId}
            isLoading={isDetailLoading}
            isForbidden={isForbidden}
        >
            <ApprovalToast toast={toast} onDismiss={dismissToast} />

            {!uploadId ? (
                renderFailure("No upload was specified.")
            ) : !detail ? (
                renderFailure(detailError || "Upload details could not be found.")
            ) : (
                <>
                    <ApproveUploadModal
                        isOpen={isApproveOpen}
                        uploadId={detail.uploadId}
                        datasetName={detail.datasetName}
                        onClose={() => setIsApproveOpen(false)}
                        onSuccess={handleDecisionSuccess(APPROVAL_STATUS.APPROVED)}
                    />
                    <RejectUploadModal
                        isOpen={isRejectOpen}
                        uploadId={detail.uploadId}
                        datasetName={detail.datasetName}
                        onClose={() => setIsRejectOpen(false)}
                        onSuccess={handleDecisionSuccess(APPROVAL_STATUS.REJECTED)}
                    />

                    {/* Header */}
                    <div className="page-head">
                        <div style={{ minWidth: 0 }}>
                            {backButton}
                            <div className="row" style={{ gap: "10px", marginBottom: "6px", flexWrap: "wrap" }}>
                                <ApprovalStatusBadge status={effectiveStatus} rawStatus={detail.rawStatus} />
                                <span className="cell-sub">
                                    {[detail.datasetCode, detail.fileName].filter(Boolean).join(" · ") || detail.uploadId}
                                </span>
                            </div>
                            <h1 style={{ overflowWrap: "anywhere" }}>{detail.datasetName || "Untitled dataset"}</h1>
                            {(detail.uploadTitle || detail.description) && (
                                <p style={{ overflowWrap: "anywhere" }}>
                                    {[detail.uploadTitle, detail.description].filter(Boolean).join(" — ")}
                                </p>
                            )}
                            <p>
                                Submitted by <b style={{ color: "var(--text)" }}>{detail.submitterId || "-"}</b> on{" "}
                                {formatApprovalDateTime(detail.submittedAt)}
                            </p>
                        </div>

                        <div className="actions">
                            <button
                                className="btn btn-outline"
                                disabled={actionsDisabled}
                                title={reviewState.reason || "Reject this upload"}
                                onClick={() => setIsRejectOpen(true)}
                                style={{
                                    color: actionsDisabled ? undefined : "var(--danger, #ef4444)",
                                    opacity: actionsDisabled ? 0.5 : 1,
                                    cursor: actionsDisabled ? "not-allowed" : "pointer",
                                }}
                            >
                                <Icon name="x" /> Reject
                            </button>
                            <button
                                className="btn btn-primary"
                                disabled={actionsDisabled}
                                title={reviewState.reason || "Approve this upload"}
                                onClick={() => setIsApproveOpen(true)}
                                style={{ opacity: actionsDisabled ? 0.5 : 1, cursor: actionsDisabled ? "not-allowed" : "pointer" }}
                            >
                                <Icon name="check" /> Approve
                            </button>
                        </div>
                    </div>

                    {/* Maker-Checker / finalized notice */}
                    {reviewState.reason && (
                        <div
                            role="note"
                            style={{
                                display: "flex",
                                alignItems: "center",
                                gap: "10px",
                                padding: "12px 16px",
                                marginBottom: "18px",
                                borderRadius: "var(--r)",
                                fontSize: "13.5px",
                                background: reviewState.isSubmitter && effectiveStatus === APPROVAL_STATUS.PENDING ? "var(--warning-50)" : "var(--p-50)",
                                color: reviewState.isSubmitter && effectiveStatus === APPROVAL_STATUS.PENDING ? "#B45309" : "var(--p-700)",
                                border: "1px solid var(--border)",
                            }}
                        >
                            <Icon name={reviewState.isSubmitter && effectiveStatus === APPROVAL_STATUS.PENDING ? "shield" : "check"} />
                            <span>{reviewState.reason}</span>
                        </div>
                    )}

                    {/* Summary Banner */}
                    <div className="stat-grid">
                        <div className="stat">
                            <div className="label">Target Table</div>
                            <div className="val" style={{ fontSize: "17px", overflowWrap: "anywhere", fontFamily: "ui-monospace, monospace" }}>
                                {detail.targetTable || "-"}
                            </div>
                        </div>
                        <div className="stat">
                            <div className="label">Total Records</div>
                            <div className="val">{formatCount(totalRecords)}</div>
                        </div>
                        <div className="stat">
                            <div className="label">Category</div>
                            <div className="val" style={{ fontSize: "17px", overflowWrap: "anywhere" }}>
                                {detail.categoryName || "-"}
                            </div>
                        </div>
                        <div className="stat">
                            <div className="label">Submitted At</div>
                            <div className="val" style={{ fontSize: "17px" }}>{formatApprovalDateTime(detail.submittedAt)}</div>
                        </div>
                    </div>

                    {/* Staged Data Preview */}
                    <div className="card" style={{ display: "flex", flexDirection: "column", overflow: "hidden", minWidth: 0, marginBottom: "18px" }}>
                        <div className="card-head">
                            <div style={{ minWidth: 0 }}>
                                <h3>
                                    Staged Data Preview
                                    {staged.headers.length > 0 && (
                                        <span className="sub"> · {staged.headers.length} columns</span>
                                    )}
                                </h3>
                                {staged.subTitle && (
                                    <div className="sub" style={{ marginTop: "2px", overflowWrap: "anywhere" }}>{staged.subTitle}</div>
                                )}
                            </div>
                        </div>

                        <StagedDataTable
                            headers={staged.headers}
                            headerRows={staged.headerRows}
                            rows={staged.rows}
                            isLoading={isPreviewLoading}
                            hasLoaded={previewResult !== null}
                            errorMessage={isPreviewLoading ? "" : previewError}
                            onRetry={reloadPreview}
                        />

                        {!previewError && previewTotal > 0 && (
                            <ApprovalPagination
                                currentPage={previewPage}
                                totalPages={staged.totalPages}
                                totalItems={previewTotal}
                                pageSize={PREVIEW_PAGE_SIZE}
                                isLoading={isPreviewLoading}
                                itemLabel="rows"
                                onPageChange={setPreviewPage}
                            />
                        )}
                    </div>

                    {/* Audit Trail */}
                    <div className="card">
                        <div className="card-head">
                            <h3>Audit Trail</h3>
                            <button className="btn btn-ghost btn-sm" onClick={reloadAudit} disabled={isAuditLoading}>
                                {isAuditLoading ? "Refreshing..." : "Refresh"}
                            </button>
                        </div>
                        <div className="card-body">
                            <AuditTimeline entries={auditEntries} isLoading={isAuditLoading} errorMessage={auditError} onRetry={reloadAudit} />
                        </div>
                    </div>
                </>
            )}
        </AdminPageContainer>
    );
}