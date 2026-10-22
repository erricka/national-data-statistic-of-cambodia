import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";
import ApprovalStatusBadge from "../../../components/approval/ApprovalStatusBadge";
import ApprovalPagination from "../../../components/approval/ApprovalPagination";
import StagedDataTable from "../../../components/approval/StagedDataTable";
import { getUploadDetails } from "../../../service/admin/approval-service/uploadDetailService";
import { getStagedData } from "../../../service/admin/approval-service/stagedDataService";
import { getApprovalErrorMessage } from "../../../service/admin/approval-service/approvalErrors";
import type { StagedData, UploadDetail } from "../../../service/admin/approval-service/approvalTypes";
import type { ApiResponse } from "../../../api/api";
import { formatApprovalDateTime, formatCount } from "../../../utils/approvalFormatters";

/**
 * Read-only detail page for a dataset upload, opened from Dataset Management.
 * Same layout as the Approval review page, without the audit trail and the
 * approve / reject actions.
 *
 * Data: APPI002 (upload details) + APPI003 (paged data preview).
 */

const DATASET_LIST_PATH = "/admin/data-management";
const PREVIEW_PAGE_SIZE = 50;

// Stable extractor: APPI002 returns a single object, exposed as a one-item list for usePermissionGuard
const uploadDetailExtractor = (res: ApiResponse<UploadDetail>) => ({
    items: res?.data ? [res.data] : [],
    total: res?.data ? 1 : 0,
    totalPages: 1,
});

const EMPTY_PREVIEW: StagedData = {
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

export default function DataDetailReviewPage() {
    const navigate = useNavigate();
    const { uploadId = "" } = useParams<{ uploadId: string }>();

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

    // Navigating between datasets reuses this component: reset per-upload state during render
    const [trackedUploadId, setTrackedUploadId] = useState(uploadId);
    if (trackedUploadId !== uploadId) {
        setTrackedUploadId(uploadId);
        setPreviewPage(1);
        setPreviewResult(null);
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
                    setPreviewResult({ key: previewKey, data: EMPTY_PREVIEW, error: "Failed to load data preview." });
                }
            })
            .catch((error) => {
                if (!active) return;
                console.error("Data preview fetch failed:", error);
                setPreviewResult({
                    key: previewKey,
                    data: EMPTY_PREVIEW,
                    error: getApprovalErrorMessage(error, "Failed to load data preview."),
                });
            });

        return () => {
            active = false;
        };
    }, [previewKey, sourceId, detailUploadId, previewPage]);

    const preview = previewResult?.data ?? EMPTY_PREVIEW;
    const previewError = previewResult?.error ?? "";
    const reloadPreview = useCallback(() => setPreviewReload((n) => n + 1), []);

    /* ------------------------------ Render ------------------------------- */

    const previewTotal = preview.total;
    // APPI002 has no record count; use APPI003's total only when the backend actually reports one
    const totalRecords = previewResult && !previewResult.error && preview.totalKnown ? preview.total : null;

    const backButton = (
        <button
            className="btn btn-outline btn-sm"
            style={{ padding: "6px 10px", marginBottom: "12px" }}
            onClick={() => navigate(DATASET_LIST_PATH)}
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
            sidebarActive="data_management"
            pageTitle="Dataset Detail"
            hasInitialLoaded={hasInitialLoaded || !uploadId}
            isLoading={isDetailLoading}
            isForbidden={isForbidden}
        >
            {!uploadId ? (
                renderFailure("No dataset was specified.")
            ) : !detail ? (
                renderFailure(detailError || "Dataset details could not be found.")
            ) : (
                <>
                    {/* Header */}
                    <div className="page-head">
                        <div style={{ minWidth: 0 }}>
                            {backButton}
                            <div className="row" style={{ gap: "10px", marginBottom: "6px", flexWrap: "wrap" }}>
                                <ApprovalStatusBadge status={detail.status} rawStatus={detail.rawStatus} />
                                <span className="cell-sub">
                                    {[detail.datasetCode, detail.fileName].filter(Boolean).join(" · ") || detail.uploadId}
                                </span>
                            </div>
                            <h1 style={{ overflowWrap: "anywhere" }}>{detail.datasetName || "Untitled dataset"}</h1>
                            
                            <p>
                                Submitted by <b style={{ color: "var(--text)" }}>{detail.submitterId || "-"}</b> on{" "}
                                {formatApprovalDateTime(detail.submittedAt)}
                            </p>
                        </div>
                    </div>

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

                    {/* Data Preview */}
                    <div className="card" style={{ display: "flex", flexDirection: "column", overflow: "hidden", minWidth: 0 }}>
                        <div className="card-head">
                            <div style={{ minWidth: 0 }}>
                                <h3>
                                    Data Preview
                                    {preview.headers.length > 0 && (
                                        <span className="sub"> · {preview.headers.length} columns</span>
                                    )}
                                </h3>
                                {preview.subTitle && (
                                    <div className="sub" style={{ marginTop: "2px", overflowWrap: "anywhere" }}>{preview.subTitle}</div>
                                )}
                            </div>
                        </div>

                        <StagedDataTable
                            headers={preview.headers}
                            headerRows={preview.headerRows}
                            rows={preview.rows}
                            isLoading={isPreviewLoading}
                            hasLoaded={previewResult !== null}
                            errorMessage={isPreviewLoading ? "" : previewError}
                            onRetry={reloadPreview}
                        />

                        {!previewError && previewTotal > 0 && (
                            <ApprovalPagination
                                currentPage={previewPage}
                                totalPages={preview.totalPages}
                                totalItems={previewTotal}
                                pageSize={PREVIEW_PAGE_SIZE}
                                isLoading={isPreviewLoading}
                                itemLabel="rows"
                                onPageChange={setPreviewPage}
                            />
                        )}
                    </div>
                </>
            )}
        </AdminPageContainer>
    );
}
