import { useEffect, useState, useCallback, useRef } from "react";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import { useNavigate } from "react-router-dom";
import {
    getRoleList,
    type ApiRoleItem,
} from "../../../service/admin/role-service/roleListService";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";

// Stable Extractor defined outside component scope
const roleDataExtractor = (res: any) => ({
    items: res?.data?.list || [],
    total: res?.data?.total || 0,
    totalPages: res?.data?.totalPages || 1,
});

export default function RoleManagementPage() {
    const navigate = useNavigate();

    // Table Filters & Pagination State
    const [searchTerm, setSearchTerm] = useState("");
    const [debouncedSearchTerm, setDebouncedSearchTerm] = useState("");
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    // Debounce Handler for Search Bar
    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedSearchTerm(searchTerm);
            setCurrentPage(1);
        }, 300);

        return () => clearTimeout(handler);
    }, [searchTerm]);

    // Shell Navigation Integration
    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "roles",
                title: "Roles & Permissions",
                breadcrumb: ["Home", "Governance", "Roles & Permissions"],
            });
        }
    }, []);

    // Sync Ref to hold current state values across re-renders safely
    const stateRef = useRef({ currentPage, pageSize, debouncedSearchTerm });
    useEffect(() => {
        stateRef.current = { currentPage, pageSize, debouncedSearchTerm };
    }, [currentPage, pageSize, debouncedSearchTerm]);

    // Fetcher Provider Callback matching standardized 1-indexed page payload
    const fetcherFn = useCallback(() => {
        const { currentPage: page, pageSize: size, debouncedSearchTerm: search } = stateRef.current;
        return getRoleList({
            search: search.trim(),
            page: page, // Standardized 1-indexed page argument
            size,
        });
    }, []);

    // Apply Guard Hook
    const {
        data: roles,
        totalItems,
        totalPages,
        isLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage,
        executeFetch: fetchRoles,
    } = usePermissionGuard<ApiRoleItem>(fetcherFn, roleDataExtractor);

    useEffect(() => {
        fetchRoles();
    }, [currentPage, pageSize, debouncedSearchTerm, fetchRoles]);

    const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearchTerm(e.target.value);
    };

    const effectiveTotalPages = totalPages || Math.ceil(totalItems / pageSize) || 1;

    // Range Calculations
    const startItemIndex = totalItems > 0 ? (currentPage - 1) * pageSize + 1 : 0;
    const endItemIndex = Math.min(currentPage * pageSize, totalItems);

    // Dynamic Pagination Bar Page Generator
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
            sidebarActive="role_management"
            pageTitle="Role Management"
            hasInitialLoaded={hasInitialLoaded}
            isLoading={isLoading}
            isForbidden={isForbidden}
        >
            <div className="page-head">
                <div>
                    <h1>Role Management</h1>
                    <p>Manage security roles, access policies, and operational scope across system components.</p>
                </div>
                <div className="actions">
                    <button
                        className="btn btn-primary"
                        id="b-new"
                        onClick={() => navigate(`/admin/create-role`)}
                    >
                        <Icon name="plus" /> New Role
                    </button>
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
                <div className="toolbar between" style={{ width: "100%", gap: "16px" }}>
                    <label className="field" style={{ flex: 1, maxWidth: "340px" }}>
                        <span id="ic-s"><Icon name="search" /></span>
                        <input
                            placeholder="Search roles..."
                            value={searchTerm}
                            onChange={handleSearchChange}
                            style={{ width: "100%" }}
                        />
                    </label>

                    <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                        <label className="field" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
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
                        </label>
                    </div>
                </div>

                {/* Error Banner */}
                {errorMessage && (
                    <div style={{
                        margin: "16px",
                        backgroundColor: "#fef2f2",
                        color: "var(--danger, #ef4444)",
                        border: "1px solid #fecaca",
                        padding: "12px 16px",
                        borderRadius: "6px",
                        fontSize: "14px"
                    }}>
                        {errorMessage}
                    </div>
                )}

                {/* Role Table Wrap */}
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
                                <th>Role Details</th>
                                <th>Description</th>
                                <th style={{ width: "100px", textAlign: "center" }}>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {!hasInitialLoaded && isLoading ? (
                                <tr>
                                    <td colSpan={4} className="cell-sub" style={{ padding: "48px 24px", textAlign: "center" }}>
                                        Loading roles...
                                    </td>
                                </tr>
                            ) : roles.length > 0 ? (
                                roles.map((role, index) => (
                                    <tr key={role.roleId || index}>
                                        <td style={{ textAlign: "center", color: "var(--text-muted, #64748b)", fontWeight: 500 }}>
                                            {(currentPage - 1) * pageSize + index + 1}
                                        </td>
                                        <td>
                                            <div className="cell-main">{role.name}</div>
                                            <div className="cell-sub">{role.roleId}</div>
                                        </td>
                                        <td>
                                            <p style={{ margin: 0, fontSize: "13px", lineHeight: "1.5", maxWidth: "420px" }}>
                                                {role.description || "-"}
                                            </p>
                                        </td>
                                        <td>
                                            <div className="t-row-actions" style={{ justifyContent: "center" }}>
                                                <button
                                                    className="icon-btn"
                                                    title="Configure Matrix"
                                                    onClick={() => navigate(`/admin/role-detail`, { state: { roleData: role } })}
                                                >
                                                    <Icon name="eye" />
                                                </button>
                                                <button
                                                    className="icon-btn"
                                                    title="Edit Role"
                                                    onClick={() => navigate(`/admin/edit-role`, { state: { roleData: role } })}
                                                >
                                                    <Icon name="edit" />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))
                            ) : (
                                <tr>
                                    <td colSpan={4} className="cell-sub" style={{ padding: "48px 24px", textAlign: "center" }}>
                                        {debouncedSearchTerm ? `No security roles matching search query expression: "${debouncedSearchTerm}"` : "No roles found."}
                                    </td>
                                </tr>
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
                        Showing {startItemIndex}–{endItemIndex} of {totalItems} roles
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
                                fontSize: "12px"
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
                                fontSize: "12px"
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