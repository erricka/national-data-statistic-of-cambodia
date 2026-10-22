import { useEffect, useState, useCallback, useRef } from "react";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import AddCategoryModal from "./category-modal/AddCategoryModal";
import EditCategoryModal from "./category-modal/EditCategoryModel";
import ViewCategoryModal from "./category-modal/ViewCategoryModal";
import {
    getCategoryList,
    type ApiCategoryItem,
} from "../../../service/admin/category-service/categoryListService";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";

// Extractor mapped to response payload
const categoryDataExtractor = (res: any) => ({
    items: res?.data?.list || [],
    total: res?.data?.total || 0,
    totalPages: res?.data?.totalPages || 1,
});

export default function CategoryManagementPage() {
    const [searchQuery, setSearchQuery] = useState("");
    const [debouncedSearch, setDebouncedSearch] = useState("");
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    // Modal Control States
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [editingCategory, setEditingCategory] = useState<ApiCategoryItem | null>(null);
    const [isViewModalOpen, setIsViewModalOpen] = useState(false);
    const [viewingCategory, setViewingCategory] = useState<ApiCategoryItem | null>(null);

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
                active: "category_management",
                title: "Category Management",
                breadcrumb: ["Home", "Category Management"],
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
        return getCategoryList({
            search: search.trim(),
            page: page, // Standardized 1-indexed page argument
            size,
        });
    }, []);

    // Execute Permission & Fetch Guard Hook
    const {
        data: categories,
        totalItems,
        totalPages,
        isLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage,
        executeFetch: loadCategories,
    } = usePermissionGuard<ApiCategoryItem>(fetcherFn, categoryDataExtractor);

    useEffect(() => {
        loadCategories();
    }, [currentPage, pageSize, debouncedSearch, loadCategories]);

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
            sidebarActive="category_management"
            pageTitle="Category Management"
            hasInitialLoaded={hasInitialLoaded}
            isLoading={isLoading}
            isForbidden={isForbidden}
        >
            <div className="page-head">
                <div>
                    <h1>Category Management</h1>
                    <p>Organize, create, and inspect data category structures</p>
                </div>
                {/* <div className="actions">
                    <button className="btn btn-primary" id="b-new" onClick={() => setIsAddModalOpen(true)}>
                        <Icon name="plus" /> New Category
                    </button>
                </div> */}

                {/* Modals */}
                <AddCategoryModal
                    isOpen={isAddModalOpen}
                    onClose={() => setIsAddModalOpen(false)}
                    onSuccess={loadCategories}
                />
                <EditCategoryModal
                    isOpen={isEditModalOpen}
                    categoryData={editingCategory}
                    onClose={() => {
                        setIsEditModalOpen(false);
                        setEditingCategory(null);
                    }}
                    onSuccess={loadCategories}
                />
                <ViewCategoryModal
                    isOpen={isViewModalOpen}
                    categoryData={viewingCategory}
                    onClose={() => {
                        setIsViewModalOpen(false);
                        setViewingCategory(null);
                    }}
                />
            </div>

            <div className="stat-grid" style={{ marginBottom: "16px" }}>
                <div className="stat">
                    <div className="label">Total Categories</div>
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
                <div className="toolbar" style={{ display: "flex", gap: "12px", alignItems: "center", width: "100%" }}>
                    <label className="field" style={{ flex: 1, width: "100%", maxWidth: "none" }}>
                        <span id="ic-s"><Icon name="search" /></span>
                        <input
                            placeholder="Search category by name or description…"
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
                    </div>
                </div>

                {/* Error Banner */}
                {errorMessage && (
                    <div style={{ padding: "12px 20px", color: "var(--danger, #ef4444)", backgroundColor: "#fef2f2" }}>
                        {errorMessage}
                    </div>
                )}

                {/* Category Table */}
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
                                <th>Category Name</th>
                                <th>Description</th>
                                <th style={{ width: "120px", textAlign: "center" }}>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {!hasInitialLoaded && isLoading ? (
                                <tr>
                                    <td colSpan={4} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        Loading categories...
                                    </td>
                                </tr>
                            ) : categories.length === 0 ? (
                                <tr>
                                    <td colSpan={4} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        No category records found.
                                    </td>
                                </tr>
                            ) : (
                                categories.map((cat, index) => {
                                    const rowNumber = (currentPage - 1) * pageSize + index + 1;

                                    return (
                                        <tr key={cat.categoryId || index}>
                                            <td style={{ textAlign: "center", color: "#64748b", fontWeight: 500 }}>
                                                {rowNumber}
                                            </td>
                                            <td>
                                                <div className="cell-main">{cat.categoryName}</div>
                                                <div className="cell-sub">{cat.categoryId}</div>
                                            </td>
                                            <td>{cat.description || "-"}</td>
                                            <td>
                                                <div className="t-row-actions" style={{ justifyContent: "center" }}>
                                                    <button
                                                        className="icon-btn"
                                                        title="View Details"
                                                        onClick={() => {
                                                            setViewingCategory(cat);
                                                            setIsViewModalOpen(true);
                                                        }}
                                                    >
                                                        <Icon name="eye" />
                                                    </button>
                                                    {/* <button
                                                        className="icon-btn"
                                                        title="Edit Category"
                                                        onClick={() => {
                                                            setEditingCategory(cat);
                                                            setIsEditModalOpen(true);
                                                        }}
                                                    >
                                                        <Icon name="edit" />
                                                    </button> */}
                                                </div>
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
                        Showing {startItemIndex}–{endItemIndex} of {totalItems} categories
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