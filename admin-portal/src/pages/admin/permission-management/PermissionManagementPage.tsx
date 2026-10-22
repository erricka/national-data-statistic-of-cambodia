import { useEffect, useState, useCallback, useRef } from "react";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import {
    getPermissionList,
    type ApiPermissionItem,
} from "../../../service/admin/permission-service/permissionListService";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";

// Stable Extractor defined outside component scope
const permissionDataExtractor = (res: any) => ({
    items: res?.data?.list || [],
    total: res?.data?.total || 0,
    totalPages: res?.data?.totalPages || 1,
});

export default function PermissionManagementPage() {
    const [searchQuery, setSearchQuery] = useState("");
    const [debouncedSearch, setDebouncedSearch] = useState("");
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    // Debounce Handler for Search Bar
    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedSearch(searchQuery);
            setCurrentPage(1);
        }, 300);
        return () => clearTimeout(handler);
    }, [searchQuery]);

    // Shell Breadcrumb Registration
    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "permission_management",
                title: "Permission Management",
                breadcrumb: ["Home", "Governance", "Permissions"],
            });
        }
    }, []);

    // Sync Ref to hold current state values across re-renders safely
    const stateRef = useRef({ currentPage, pageSize, debouncedSearch });
    useEffect(() => {
        stateRef.current = { currentPage, pageSize, debouncedSearch };
    }, [currentPage, pageSize, debouncedSearch]);

    // Fetcher Provider Callback matching standardized 1-indexed page payload
    const fetcherFn = useCallback(() => {
        const { currentPage: page, pageSize: size, debouncedSearch: search } = stateRef.current;
        return getPermissionList({
            search: search.trim(),
            page: page, // Standardized 1-indexed page argument
            size,
        });
    }, []);

    // Execute Guard Hook
    const {
        data: permissions,
        totalItems,
        totalPages,
        isLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage,
        executeFetch: loadPermissions,
    } = usePermissionGuard<ApiPermissionItem>(fetcherFn, permissionDataExtractor);

    useEffect(() => {
        loadPermissions();
    }, [currentPage, pageSize, debouncedSearch, loadPermissions]);

    const effectiveTotalPages = totalPages || Math.ceil(totalItems / pageSize) || 1;

    // Range Display Calculations
    const startItemIndex = totalItems > 0 ? (currentPage - 1) * pageSize + 1 : 0;
    const endItemIndex = Math.min(currentPage * pageSize, totalItems);

    // Pagination Generator
    const getPageNumbers = () => {
        const pages: (number | string)[] = [];
        if (effectiveTotalPages <= 5) {
            for (let i = 1; i <= effectiveTotalPages; i++) pages.push(i);
        } else {
            if (currentPage <= 3) {
                pages.push(1, 2, 3, 4, "...", effectiveTotalPages);
            } else if (currentPage >= effectiveTotalPages - 2) {
                pages.push(1, "...", effectiveTotalPages - 3, effectiveTotalPages - 2, effectiveTotalPages - 1, effectiveTotalPages);
            } else {
                pages.push(1, "...", currentPage - 1, currentPage, currentPage + 1, "...", effectiveTotalPages);
            }
        }
        return pages;
    };

    return (
        <AdminPageContainer
            sidebarActive="permission_management"
            pageTitle="Permission Management"
            hasInitialLoaded={hasInitialLoaded}
            isLoading={isLoading}
            isForbidden={isForbidden}
        >
            <div className="page-head">
                <div>
                    <h1>Permission System Registry</h1>
                    <p>Monitor atomic security privileges and infrastructure execution boundaries.</p>
                </div>
            </div>

            <div
                className="card"
                style={{
                    display: "flex",
                    flexDirection: "column",
                    overflow: "hidden",
                }}
            >
                {/* Search & Page Controls Toolbar */}
                <div className="toolbar" style={{ display: "flex", gap: "12px", alignItems: "center", width: "100%" }}>
                    <label className="field" style={{ flex: 1, width: "100%", maxWidth: "none" }}>
                        <span id="ic-s"><Icon name="search" /></span>
                        <input
                            placeholder="Search permissions by name, ID or description…"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            style={{ width: "100%" }}
                        />
                    </label>

                    <div style={{ display: "flex", alignItems: "center", gap: "8px", flexShrink: 0 }}>
                        <span className="cell-sub" style={{ fontSize: "13px", whiteSpace: "nowrap" }}>Show:</span>
                        <select
                            value={pageSize}
                            onChange={(e) => {
                                setPageSize(Number(e.target.value));
                                setCurrentPage(1);
                            }}
                            style={{
                                padding: "6px 12px",
                                borderRadius: "6px",
                                border: "1px solid var(--border)",
                                fontSize: "13px",
                                backgroundColor: "#fff",
                                cursor: "pointer",
                            }}
                        >
                            <option value={5}>5</option>
                            <option value={10}>10</option>
                            <option value={20}>20</option>
                            <option value={50}>50</option>
                        </select>
                    </div>
                </div>

                {/* Error Banner */}
                {errorMessage && (
                    <div style={{ padding: "12px 20px", color: "var(--danger, #ef4444)", backgroundColor: "#fef2f2" }}>
                        {errorMessage}
                    </div>
                )}

                {/* Permission Table */}
                <div
                    className="table-wrap"
                    style={{
                        position: "relative",
                        opacity: isLoading ? 0.5 : 1,
                        transition: "opacity 0.2s ease-in-out",
                    }}
                >
                    <table className="tbl">
                        <thead>
                            <tr>
                                <th style={{ width: "60px", textAlign: "center" }}>No.</th>
                                <th>Permission Details</th>
                                <th>Description Definition Scope</th>
                                <th>Status</th>
                                <th>Created Date</th>
                            </tr>
                        </thead>
                        <tbody>
                            {!hasInitialLoaded && isLoading ? (
                                <tr>
                                    <td colSpan={5} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        Loading permissions...
                                    </td>
                                </tr>
                            ) : permissions.length === 0 ? (
                                <tr>
                                    <td colSpan={5} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        No permission records found.
                                    </td>
                                </tr>
                            ) : (
                                permissions.map((perm, index) => {
                                    const rowNumber = (currentPage - 1) * pageSize + index + 1;

                                    return (
                                        <tr key={perm.permissionId || index}>
                                            <td style={{ textAlign: "center", color: "#64748b", fontWeight: 500 }}>
                                                {rowNumber}
                                            </td>
                                            <td>
                                                <div className="cell-main">{perm.name}</div>
                                                <div className="cell-sub" style={{ fontFamily: "monospace", fontSize: "11px" }}>
                                                    {perm.permissionId}
                                                </div>
                                            </td>
                                            <td>
                                                <p style={{ margin: 0, fontSize: "13px", lineHeight: "1.5", maxWidth: "380px" }}>
                                                    {perm.description || "-"}
                                                </p>
                                            </td>
                                            <td>
                                                <span
                                                    style={{
                                                        backgroundColor: perm.status === "01" ? "#f0fdf4" : "#fef2f2",
                                                        color: perm.status === "01" ? "#15803d" : "#b91c1c",
                                                        borderRadius: "9999px",
                                                        padding: "4px 12px",
                                                        fontSize: "13px",
                                                        fontWeight: 600,
                                                        display: "inline-flex",
                                                        alignItems: "center",
                                                        gap: "6px",
                                                    }}
                                                >
                                                    <span
                                                        style={{
                                                            width: "6px",
                                                            height: "6px",
                                                            borderRadius: "50%",
                                                            backgroundColor: perm.status === "01" ? "#16a34a" : "#dc2626",
                                                        }}
                                                    />
                                                    {perm.status === "01" ? "Active" : "Disabled"}
                                                </span>
                                            </td>
                                            <td className="cell-sub">
                                                {perm.createdAt ? new Date(perm.createdAt).toLocaleDateString() : "-"}
                                            </td>
                                        </tr>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>

                {/* Pagination Controls */}
                <div
                    className="toolbar between"
                    style={{
                        borderTop: "1px solid var(--border)",
                        padding: "12px 20px",
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                    }}
                >
                    <span className="cell-sub">
                        Showing {startItemIndex}–{endItemIndex} of {totalItems} permissions
                    </span>

                    <div className="row" style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                        <button
                            className="btn btn-outline btn-sm"
                            disabled={currentPage === 1 || isLoading}
                            onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 1))}
                            style={{
                                opacity: currentPage === 1 || isLoading ? 0.5 : 1,
                                cursor: currentPage === 1 || isLoading ? "not-allowed" : "pointer",
                                padding: "10px 12px",
                                fontSize: "12px",
                            }}
                        >
                            Previous
                        </button>

                        {getPageNumbers().map((page, idx) =>
                            typeof page === "number" ? (
                                <button
                                    key={idx}
                                    className={`btn btn-sm ${currentPage === page ? "btn-primary" : "btn-outline"}`}
                                    disabled={isLoading}
                                    onClick={() => setCurrentPage(page)}
                                >
                                    {page}
                                </button>
                            ) : (
                                <span key={idx} style={{ padding: "0 4px", color: "var(--text-sub)" }}>
                                    {page}
                                </span>
                            )
                        )}

                        <button
                            className="btn btn-outline btn-sm"
                            disabled={currentPage === effectiveTotalPages || isLoading}
                            onClick={() => setCurrentPage((prev) => Math.min(prev + 1, effectiveTotalPages))}
                            style={{
                                opacity: currentPage === effectiveTotalPages || isLoading ? 0.5 : 1,
                                cursor: currentPage === effectiveTotalPages || isLoading ? "not-allowed" : "pointer",
                                padding: "10px 12px",
                                fontSize: "12px",
                            }}
                        >
                            Next
                        </button>
                    </div>
                </div>
            </div>
        </AdminPageContainer>
    );
}