import { useEffect, useState, useCallback, useRef } from "react";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import { useNavigate } from "react-router-dom";
import ViewUserModal from "./user-management-modal/ViewUserModal";
import {
    getUserList,
    type ApiUserItem,
    type ApiUserRoleItem,
} from "../../../service/admin/user-service/userListService";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";

declare global {
    interface Window {
        NIS: any;
    }
}

// User object structure expected by local modal component
export interface LocalViewUser {
    id: string;
    name: string;
    email: string;
    dept: string;
    role: string;
    statusKey: string;
    statusText: string;
    time: string;
    initial: string;
}

// Stable Extractor defined outside component scope to prevent re-render loops
const userDataExtractor = (res: any) => ({
    items: res?.data?.list || [],
    total: res?.data?.total || 0,
    totalPages: res?.data?.totalPages || 1,
});

export default function UserManagementPage() {
    const navigate = useNavigate();

    const [editingUser] = useState<LocalViewUser | null>(null);
    const [viewingUser, setViewingUser] = useState<LocalViewUser | null>(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    // Filters and Pagination State (1-indexed page)
    const [searchTerm, setSearchTerm] = useState<string>("");
    const [debouncedSearch, setDebouncedSearch] = useState<string>("");
    const [currentPage, setCurrentPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<number>(10);
    const [statusFilter, setStatusFilter] = useState<string>("00");

    // Search Debouncer
    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedSearch(searchTerm);
            setCurrentPage(1);
        }, 300);

        return () => clearTimeout(timer);
    }, [searchTerm]);

    // Shell Navigation Integration
    useEffect(() => {
        if (!editingUser && window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "users",
                title: "User Management",
                breadcrumb: ["Home", "Governance", "Users"],
            });
        }
    }, [editingUser]);

    // Sync Ref to hold current state values across re-renders safely
    const stateRef = useRef({ currentPage, pageSize, debouncedSearch, statusFilter });
    useEffect(() => {
        stateRef.current = { currentPage, pageSize, debouncedSearch, statusFilter };
    }, [currentPage, pageSize, debouncedSearch, statusFilter]);

    // Fetcher Provider Callback matching standardized 1-indexed page payload
    const fetcherFn = useCallback(() => {
        const { currentPage: page, pageSize: size, debouncedSearch: search, statusFilter: status } = stateRef.current;
        return getUserList({
            page: page,
            size,
            search: search.trim(),
            userStatus: status,
        });
    }, []);

    // Apply Reusable Permission & Data Fetching Hook
    const {
        data: users,
        totalItems,
        totalPages,
        isLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage,
        executeFetch: fetchUserList,
    } = usePermissionGuard<ApiUserItem>(fetcherFn, userDataExtractor);

    useEffect(() => {
        fetchUserList();
    }, [currentPage, pageSize, debouncedSearch, statusFilter, fetchUserList]);

    // Format helper methods
    const getUserFullName = (user: ApiUserItem) => {
        const full = `${user.firstName || ""} ${user.lastName || ""}`.trim();
        return full || user.userID || "N/A";
    };

    const getUserInitial = (user: ApiUserItem) => {
        const name = getUserFullName(user);
        return name.charAt(0).toUpperCase();
    };

    const getStatusText = (statusKey: string) => {
        return statusKey === "01" ? "Active" : "Disabled";
    };

    const getStatusMapKey = (statusKey: string) => {
        return statusKey === "01" ? "active" : "disabled";
    };

    const renderRoleBadges = (roles?: ApiUserRoleItem[]) => {
        if (!roles || roles.length === 0) {
            return <span className="cell-sub" style={{ fontStyle: "italic" }}>-</span>;
        }

        return (
            <div style={{ display: "flex", gap: "4px", flexWrap: "wrap", maxWidth: "240px" }}>
                {roles.map((r, idx) => {
                    const roleName = r.name || r.roleId || "-";
                    return (
                        <span
                            key={r.roleId || idx}
                            className="badge badge-neutral"
                            style={{ fontSize: "11px", padding: "2px 6px" }}
                        >
                            {roleName}
                        </span>
                    );
                })}
            </div>
        );
    };

    const effectiveTotalPages = totalPages || Math.ceil(totalItems / pageSize) || 1;

    // Range calculations
    const startItemIndex = totalItems > 0 ? (currentPage - 1) * pageSize + 1 : 0;
    const endItemIndex = Math.min(currentPage * pageSize, totalItems);

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
            sidebarActive="user_management"
            pageTitle="User Management"
            hasInitialLoaded={hasInitialLoaded}
            isLoading={isLoading}
            isForbidden={isForbidden}
        >
            <div className="page-head">
                <div>
                    <h1>User Management</h1>
                    <p>Manage government staff accounts, departments and access</p>
                </div>
                <div className="actions">
                    <button
                        className="btn btn-primary"
                        id="b-add"
                        onClick={() => navigate("/admin/add-user")}
                    >
                        <Icon name="plus" /> Create User
                    </button>
                </div>
            </div>

            <ViewUserModal
                isOpen={isModalOpen}
                user={viewingUser}
                onClose={() => {
                    setIsModalOpen(false);
                    setViewingUser(null);
                }}
            />

            <div className="stat-grid" style={{ marginBottom: "16px" }}>
                <div className="stat">
                    <div className="ic blue" id="u1">
                        <Icon name="users" />
                    </div>
                    <div className="label">Total Users</div>
                    <div className="val">{totalItems}</div>
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
                <div
                    className="toolbar"
                    style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        width: "100%",
                    }}
                >
                    <label className="field" style={{ flex: "0 0 340px" }}>
                        <span id="ic-s"><Icon name="search" /></span>
                        <input
                            placeholder="Search by name or email…"
                            style={{ width: "100%" }}
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </label>

                    <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                        <label className="field">
                            <select
                                value={statusFilter}
                                onChange={(e) => {
                                    setStatusFilter(e.target.value);
                                    setCurrentPage(1);
                                }}
                            >
                                <option value="00">All Status</option>
                                <option value="01">Active</option>
                                <option value="09">Disabled</option>
                            </select>
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
                    <div style={{ padding: "12px 20px", color: "var(--danger, #ef4444)", backgroundColor: "#fef2f2" }}>
                        {errorMessage}
                    </div>
                )}

                {/* Table Container */}
                <div
                    className="table-wrap"
                    style={{
                        opacity: isLoading ? 0.5 : 1,
                        transition: "opacity 0.2s ease-in-out",
                    }}
                >
                    <table className="tbl">
                        <thead>
                            <tr>
                                <th style={{ width: "60px", textAlign: "center" }}>NO</th>
                                <th>NAME</th>
                                <th>EMAIL</th>
                                <th>DEPARTMENT</th>
                                <th>ROLE</th>
                                <th>STATUS</th>
                                <th>LAST LOGIN</th>
                                <th style={{ width: "100px", textAlign: "center" }}>ACTIONS</th>
                            </tr>
                        </thead>
                        <tbody>
                            {!hasInitialLoaded && isLoading ? (
                                <tr>
                                    <td colSpan={8} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        Loading users...
                                    </td>
                                </tr>
                            ) : users.length === 0 ? (
                                <tr>
                                    <td colSpan={8} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        No users found.
                                    </td>
                                </tr>
                            ) : (
                                users.map((user, idx) => {
                                    const fullName = getUserFullName(user);
                                    const statusKey = getStatusMapKey(user.userStatus);
                                    const statusText = getStatusText(user.userStatus);

                                    const formattedRoleNames =
                                        user.roles && user.roles.length > 0
                                            ? user.roles.map((r) => r.name || r.roleId).join(", ")
                                            : "-";

                                    const rowNumber = (currentPage - 1) * pageSize + idx + 1;

                                    return (
                                        <tr key={user.userID || idx}>
                                            <td style={{ textAlign: "center" }} className="cell-sub">
                                                {rowNumber}
                                            </td>
                                            <td>
                                                <div className="row" style={{ gap: "10px" }}>
                                                    <span
                                                        className="avatar"
                                                        style={{
                                                            width: "32px",
                                                            height: "32px",
                                                            fontSize: "12px",
                                                            background: "var(--p-100)",
                                                            color: "var(--p-700)",
                                                        }}
                                                    >
                                                        {getUserInitial(user)}
                                                    </span>
                                                    <span className="cell-main">{fullName}</span>
                                                </div>
                                            </td>
                                            <td className="cell-sub">{user.email || "-"}</td>
                                            <td className="cell-sub">{user.department || "-"}</td>

                                            <td>{renderRoleBadges(user.roles)}</td>

                                            <td>
                                                <span
                                                    style={{
                                                        backgroundColor:
                                                            user.userStatus === "01" ? "#f0fdf4" : "#fef2f2",
                                                        color: user.userStatus === "01" ? "#15803d" : "#b91c1c",
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
                                                            backgroundColor:
                                                                user.userStatus === "01" ? "#16a34a" : "#dc2626",
                                                        }}
                                                    />
                                                    {statusText}
                                                </span>
                                            </td>
                                            <td className="cell-sub">{user.lastLogin || "-"}</td>
                                            <td>
                                                <div className="t-row-actions" style={{ justifyContent: "center" }}>
                                                    <button
                                                        className="icon-btn"
                                                        title="View"
                                                        onClick={() => {
                                                            setViewingUser({
                                                                id: user.userID,
                                                                name: fullName,
                                                                email: user.email,
                                                                dept: user.department || "-",
                                                                role: formattedRoleNames,
                                                                statusKey: statusKey,
                                                                statusText: statusText,
                                                                time: user.lastLogin || "-",
                                                                initial: getUserInitial(user),
                                                            });
                                                            setIsModalOpen(true);
                                                        }}
                                                    >
                                                        <Icon name="eye" />
                                                    </button>
                                                    <button
                                                        className="icon-btn"
                                                        title="Edit"
                                                        onClick={() =>
                                                            navigate(`/admin/edit-user`, {
                                                                state: {
                                                                    userData: {
                                                                        id: user.userID,
                                                                        username: user.userID,
                                                                        email: user.email,
                                                                        firstName: user.firstName,
                                                                        lastName: user.lastName,
                                                                        roles: user.roles || [],
                                                                        isActive: user.userStatus === "01",
                                                                    },
                                                                },
                                                            })
                                                        }
                                                    >
                                                        <span style={{ pointerEvents: "none" }}>
                                                            <Icon name="edit" />
                                                        </span>
                                                    </button>
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
                        Showing {startItemIndex}–{endItemIndex} of {totalItems} users
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