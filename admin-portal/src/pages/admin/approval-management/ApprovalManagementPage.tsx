import { useCallback, useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";
import { useAuth } from "../../../context/AuthContext";
import ApprovalStatusBadge from "../../../components/approval/ApprovalStatusBadge";
import ApprovalPagination from "../../../components/approval/ApprovalPagination";
import ApprovalToast from "../../../components/approval/ApprovalToast";
import type { ApprovalToastMessage } from "../../../components/approval/ApprovalToast";
import AuditHistoryModal from "./approval-modal/AuditHistoryModal";
import CancelUploadModal from "./approval-modal/CancelUploadModal";
import { getApprovalList } from "../../../service/admin/approval-service/approvalListService";
import { APPROVAL_STATUS, APPROVAL_STATUS_TABS } from "../../../service/admin/approval-service/approvalStatus";
import type { KnownApprovalStatus } from "../../../service/admin/approval-service/approvalStatus";
import { canCancelUpload, isSameUser } from "../../../service/admin/approval-service/approvalRules";
import type { ApprovalListData, ApprovalListItem } from "../../../service/admin/approval-service/approvalTypes";
import type { ApiResponse } from "../../../api/api";
import { formatApprovalDateTime, formatCount } from "../../../utils/approvalFormatters";

// Stable extractor defined outside component scope to prevent re-render loops
const approvalDataExtractor = (res: ApiResponse<ApprovalListData>) => ({
    items: res?.data?.list || [],
    total: res?.data?.total || 0,
    totalPages: res?.data?.totalPages || 1,
});

const COLUMN_COUNT = 8;

// Date on the first line, time underneath, to keep the column narrow
const renderDateTime = (raw: string) => {
    const [date, time] = formatApprovalDateTime(raw).split(" ");
    return (
        <>
            <div>{date}</div>
            {time && <div>{time}</div>}
        </>
    );
};

// Keeps row actions visible when the table scrolls horizontally on narrower screens
const STICKY_ACTIONS_STYLE = {
    position: "sticky" as const,
    right: 0,
    background: "var(--surface)",
    boxShadow: "-6px 0 8px -6px rgba(15, 23, 42, 0.12)",
    paddingLeft: "8px",
    paddingRight: "8px",
};

export default function ApprovalManagementPage() {
    const navigate = useNavigate();
    const { user } = useAuth();
    const currentUserId = user?.userID;

    // Filters and Pagination State (1-indexed page)
    const [activeStatus, setActiveStatus] = useState<KnownApprovalStatus>(APPROVAL_STATUS.PENDING);
    const [searchTerm, setSearchTerm] = useState<string>("");
    const [debouncedSearch, setDebouncedSearch] = useState<string>("");
    const [currentPage, setCurrentPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<number>(10);

    // Modal & notification state
    const [auditItem, setAuditItem] = useState<ApprovalListItem | null>(null);
    const [cancelItem, setCancelItem] = useState<ApprovalListItem | null>(null);
    const [toast, setToast] = useState<ApprovalToastMessage | null>(null);

    // Search Debouncer (same 300ms behaviour as User Management)
    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedSearch(searchTerm);
            setCurrentPage(1);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    // Sync Ref to hold current state values across re-renders safely
    const stateRef = useRef({ currentPage, pageSize, debouncedSearch, activeStatus });
    useEffect(() => {
        stateRef.current = { currentPage, pageSize, debouncedSearch, activeStatus };
    }, [currentPage, pageSize, debouncedSearch, activeStatus]);

    // Fetcher: APPI001. Also records tab counts for the status that was requested.
    const fetcherFn = useCallback(async () => {
        const { currentPage: page, pageSize: size, debouncedSearch: search, activeStatus: status } = stateRef.current;
        const tab = APPROVAL_STATUS_TABS.find((t) => t.key === status) ?? APPROVAL_STATUS_TABS[0];
        const keyword = search.trim();

        const response = await getApprovalList({
            status: tab.code,
            sourceId: "",
            search: keyword,
            page,
            size,
        });
        return response;
    }, []);

    // Apply Reusable Permission & Data Fetching Hook
    const {
        data: approvals,
        totalItems,
        totalPages,
        isLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage,
        executeFetch: fetchApprovalList,
    } = usePermissionGuard<ApprovalListItem>(fetcherFn, approvalDataExtractor);

    useEffect(() => {
        fetchApprovalList();
    }, [currentPage, pageSize, debouncedSearch, activeStatus, fetchApprovalList]);

    const handleTabChange = (status: KnownApprovalStatus) => {
        if (status === activeStatus) return;
        setActiveStatus(status);
        setCurrentPage(1);
    };

    const handleTabKeyDown = (e: KeyboardEvent<HTMLDivElement>, status: KnownApprovalStatus) => {
        if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            handleTabChange(status);
        }
    };

    const handleCancelSuccess = (message: string) => {
        setCancelItem(null);
        setToast({ id: Date.now(), type: "success", text: message });
        fetchApprovalList();
    };

    const dismissToast = useCallback(() => setToast(null), []);
    const closeAudit = useCallback(() => setAuditItem(null), []);

    return (
        <AdminPageContainer
            sidebarActive="approval_management"
            pageTitle="Approval Management"
            hasInitialLoaded={hasInitialLoaded}
            isLoading={isLoading}
            isForbidden={isForbidden}
        >
            <div className="page-head">
                <div>
                    <h1>Approval Management</h1>
                    <p>Review, approve or reject staged dataset uploads before they are published</p>
                </div>
            </div>

            <ApprovalToast toast={toast} onDismiss={dismissToast} />
            <AuditHistoryModal item={auditItem} onClose={closeAudit} />
            <CancelUploadModal
                isOpen={!!cancelItem}
                uploadId={cancelItem?.uploadId ?? ""}
                datasetName={cancelItem?.datasetName}
                onClose={() => setCancelItem(null)}
                onSuccess={handleCancelSuccess}
            />

            <div className="card" style={{ display: "flex", flexDirection: "column", overflow: "hidden", minWidth: 0 }}>
                {/* Status Tabs */}
                <div className="tabs" role="tablist" aria-label="Approval status" style={{ padding: "0 12px", overflowX: "auto" }}>
                    {APPROVAL_STATUS_TABS.map((tab) => {
                        const isActive = tab.key === activeStatus;
                        return (
                            <div
                                key={tab.key}
                                role="tab"
                                tabIndex={0}
                                aria-selected={isActive}
                                className={`tab ${isActive ? "active" : ""}`}
                                onClick={() => handleTabChange(tab.key)}
                                onKeyDown={(e) => handleTabKeyDown(e, tab.key)}
                                style={{ whiteSpace: "nowrap", display: "inline-flex", alignItems: "center", gap: "6px" }}
                            >
                                {tab.label}                
                            </div>
                        );
                    })}
                </div>

                {/* Search & Page Controls Toolbar */}
                <div
                    className="toolbar"
                    style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%", gap: "12px", flexWrap: "wrap" }}
                >
                    <label className="field" style={{ flex: "1 1 340px", maxWidth: "480px" }}>
                        <span id="ic-s"><Icon name="search" /></span>
                        <input
                            placeholder="Search dataset title, code, or author…"
                            aria-label="Search approvals"
                            style={{ width: "100%" }}
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </label>

                    <label className="field" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                        <span className="cell-sub" style={{ fontSize: "13px", whiteSpace: "nowrap" }}>Show:</span>
                        <select
                            value={pageSize}
                            onChange={(e) => {
                                setPageSize(Number(e.target.value));
                                setCurrentPage(1);
                            }}
                            style={{
                                padding: "6px 10px",
                                borderRadius: "6px",
                                border: "1px solid var(--border)",
                                fontSize: "13px",
                                backgroundColor: "#fff",
                                cursor: "pointer",
                            }}
                        >
                            <option value={10}>10</option>
                            <option value={20}>20</option>
                            <option value={50}>50</option>
                        </select>
                    </label>
                </div>

                {/* Error Banner */}
                {errorMessage && (
                    <div
                        role="alert"
                        style={{ padding: "12px 20px", color: "var(--danger, #ef4444)", backgroundColor: "#fef2f2", display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px" }}
                    >
                        <span>{errorMessage}</span>
                        <button className="btn btn-outline btn-sm" onClick={() => fetchApprovalList()} disabled={isLoading}>
                            Retry
                        </button>
                    </div>
                )}

                {/* Table Container */}
                <div className="table-wrap" style={{ opacity: isLoading ? 0.5 : 1, transition: "opacity 0.2s ease-in-out" }}>
                    <table className="tbl">
                        <thead>
                            <tr>
                                <th>Dataset Name</th>
                                <th>Category</th>
                                <th>Upload File Name</th>
                                <th>Submitted By</th>
                                <th>Submitted At</th>
                                <th style={{ textAlign: "right" }}>Total Rows</th>
                                <th>Status</th>
                                <th style={{ width: "120px", textAlign: "center", ...STICKY_ACTIONS_STYLE, background: "var(--n-50)" }}>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {!hasInitialLoaded && isLoading ? (
                                <tr>
                                    <td colSpan={COLUMN_COUNT} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        Loading approval list...
                                    </td>
                                </tr>
                            ) : approvals.length === 0 ? (
                                <tr>
                                    <td colSpan={COLUMN_COUNT} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        {errorMessage ? "Unable to load approval records." : "No approval records found."}
                                    </td>
                                </tr>
                            ) : (
                                approvals.map((row, index) => {
                                    const isMine = isSameUser(row.submitterId, currentUserId);
                                    const showCancel = canCancelUpload(row.status, row.submitterId, currentUserId);
                                    const hasUploadId = !!row.uploadId;

                                    return (
                                        <tr key={row.uploadId || `${row.sourceId}-${currentPage}-${index}`}>
                                            <td style={{ maxWidth: "260px" }}>
                                                <div className="cell-main" style={{ overflowWrap: "anywhere" }}>{row.datasetName || "-"}</div>
                                                {row.datasetCode && <div className="cell-sub">{row.datasetCode}</div>}
                                            </td>
                                            <td>
                                                {row.categoryName ? (
                                                    <span
                                                       className="badge badge-green"
                                                        style={{  color: "#137333"}}
                                                    >
                                                        {row.categoryName}
                                                    </span>
                                                ) : (
                                                    <span className="cell-sub">-</span>
                                                )}
                                            </td>
                                            <td
                                                className="cell-sub"
                                                title={row.fileName}
                                                style={{ maxWidth: "160px", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}
                                            >
                                                {row.fileName || "-"}
                                            </td>
                                            <td>
                                                <div className="row" style={{ gap: "6px", flexWrap: "wrap" }}>
                                                    <span>{row.submitterName || "-"}</span>
                                                    {isMine && (
                                                        <span className="badge badge-info" style={{ fontSize: "11px", padding: "1px 8px" }}>You</span>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="cell-sub" style={{ whiteSpace: "nowrap" }}>{renderDateTime(row.submittedAt)}</td>
                                            <td style={{ textAlign: "right", fontVariantNumeric: "tabular-nums" }}>{formatCount(row.totalRows)}</td>
                                            <td><ApprovalStatusBadge status={row.status} rawStatus={row.rawStatus} /></td>
                                            <td style={STICKY_ACTIONS_STYLE}>
                                                <div className="t-row-actions" style={{ justifyContent: "center" }}>
                                                    <button
                                                        className="icon-btn"
                                                        title="Review / Preview"
                                                        aria-label={`Review ${row.datasetName || "upload"}`}
                                                        disabled={!hasUploadId}
                                                        onClick={() => navigate(`/admin/approvals/${encodeURIComponent(row.uploadId)}`)}
                                                    >
                                                        <Icon name="eye" />
                                                    </button>
                                                    <button
                                                        className="icon-btn"
                                                        title="View Audit History"
                                                        aria-label={`View audit history for ${row.datasetName || "upload"}`}
                                                        disabled={!hasUploadId}
                                                        onClick={() => setAuditItem(row)}
                                                    >
                                                        <Icon name="clock" />
                                                    </button>
                                                    {showCancel && hasUploadId && (
                                                        <button
                                                            className="icon-btn"
                                                            title="Cancel Upload"
                                                            aria-label={`Cancel upload ${row.datasetName || ""}`.trim()}
                                                            onClick={() => setCancelItem(row)}
                                                            style={{ color: "var(--danger, #ef4444)" }}
                                                        >
                                                            <Icon name="slash" />
                                                        </button>
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>

                {/* Bottom Pagination */}
                <ApprovalPagination
                    currentPage={currentPage}
                    totalPages={totalPages}
                    totalItems={totalItems}
                    pageSize={pageSize}
                    isLoading={isLoading}
                    itemLabel="records"
                    onPageChange={setCurrentPage}
                />
            </div>
        </AdminPageContainer>
    );
}
