import { useEffect, useState, useCallback, useRef } from "react";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import { useNavigate } from "react-router-dom";
import {
    getCategoryListDropdown,
    type CategoryDropdownItem,
} from "../../../service/admin/dataset-service/categoryListDropdownService";
import {
    getDatasetList,
    type DatasetListItem,
} from "../../../service/admin/dataset-service/datasetManagementPageService";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";

const SEARCH_DEBOUNCE_MS = 300;

// Backend extractor mapped to exact response structure
const datasetDataExtractor = (res: {
    data?: { list?: DatasetListItem[]; total?: number; totalPages?: number };
}) => ({
    items: res?.data?.list || [],
    total: res?.data?.total || 0,
    totalPages: res?.data?.totalPages || 1,
});

export default function DataManagementPage() {
    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "datasets",
                title: "Dataset Management",
                breadcrumb: ["Home", "Dataset Management"],
            });
        }
    }, []);

    const navigate = useNavigate();

    // Table Filters & Pagination State
    const [searchTerm, setSearchTerm] = useState<string>("");           // what the user types
    const [debouncedSearch, setDebouncedSearch] = useState<string>(""); // what is sent to the API
    const [currentPage, setCurrentPage] = useState<number>(1);          // 1-indexed, same as the API
    const [pageSize, setPageSize] = useState<number>(20);
    const [selectedCategory, setSelectedCategory] = useState<string>("ALL");

    // Dynamic Categories State
    const [categories, setCategories] = useState<CategoryDropdownItem[]>([]);
    const [isLoadingCategories, setIsLoadingCategories] = useState<boolean>(false);

    // Fetch Categories
    useEffect(() => {
        const fetchCategories = async () => {
            setIsLoadingCategories(true);
            try {
                const response = await getCategoryListDropdown({ search: "" });
                if (response?.code === "000000" && response?.data?.list) {
                    setCategories(response.data.list);
                } else {
                    setCategories([]);
                }
            } catch (error) {
                console.error("Failed to fetch categories:", error);
                setCategories([]);
            } finally {
                setIsLoadingCategories(false);
            }
        };

        fetchCategories();
    }, []);

    // Search debounce: wait until typing stops (same as User Management).
    // Prevents one request per keystroke and results arriving out of order.
    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedSearch(searchTerm.trim());
            setCurrentPage(1);
        }, SEARCH_DEBOUNCE_MS);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    // Sync ref to always hold real-time state values without causing re-renders
    const stateRef = useRef({ currentPage, pageSize, debouncedSearch, selectedCategory });
    useEffect(() => {
        stateRef.current = { currentPage, pageSize, debouncedSearch, selectedCategory };
    }, [currentPage, pageSize, debouncedSearch, selectedCategory]);

    // Fetcher function sending exact 1-indexed page
    const fetcherFn = useCallback(() => {
        const { currentPage: page, pageSize: size, debouncedSearch: search, selectedCategory: categoryId } = stateRef.current;

        return getDatasetList({
            search,
            page, // 1-indexed as confirmed by backend (offset = (page - 1) * size)
            size,
            categoryId: categoryId === "ALL" ? "" : categoryId,
        });
    }, []);

    // Permission & Data Fetching Hook
    const {
        data: datasets,
        totalItems,
        totalPages,
        isLoading: isLoadingDatasets,
        isForbidden,
        hasInitialLoaded,
        errorMessage,
        executeFetch: fetchDatasets,
    } = usePermissionGuard<DatasetListItem>(fetcherFn, datasetDataExtractor);

    // Execute fetch on parameter changes
    useEffect(() => {
        fetchDatasets();
    }, [currentPage, pageSize, debouncedSearch, selectedCategory, fetchDatasets]);

    const effectiveTotalPages = totalPages || Math.ceil(totalItems / pageSize) || 1;
    const isFirstPage = currentPage <= 1;
    const isLastPage = currentPage >= effectiveTotalPages;

    // Sequential start & end indexes calculation
    const startItemIndex = totalItems > 0 ? (currentPage - 1) * pageSize + 1 : 0;
    const endItemIndex = Math.min(currentPage * pageSize, totalItems);

    // Pagination numbers generator
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

    const formatDate = (dateStr: string) => {
        if (!dateStr || dateStr.length !== 8) return dateStr || "-";
        return `${dateStr.substring(0, 4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}`;
    };

    return (
        <AdminPageContainer
            sidebarActive="data_management"
            pageTitle="Dataset Management"
            hasInitialLoaded={hasInitialLoaded}
            isLoading={!hasInitialLoaded && isLoadingDatasets}
            isForbidden={isForbidden}
        >
            <div className="page-head">
                <div>
                    <h1>Dataset Management</h1>
                    <p>Browse, review, publish datasets</p>
                </div>
                <div className="actions">
                    <button
                        className="btn btn-primary"
                        id="b-new"
                        onClick={() => navigate(`/admin/add-data`)}
                    >
                        <Icon name="plus" /> New Dataset
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
                {/* Toolbar */}
                <div className="toolbar" style={{ display: "flex", gap: "12px", alignItems: "center", width: "100%" }}>
                    <label className="field" style={{ flex: 1, minWidth: "220px", width: "100%", maxWidth: "none" }}>
                        <span id="ic-s"><Icon name="search" /></span>
                        <input
                            type="text"
                            value={searchTerm}
                            placeholder="Search datasets by name, code or keyword…"
                            style={{ width: "100%" }}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </label>

                    <div style={{ flexShrink: 0, minWidth: "200px" }}>
                        <div style={{ position: "relative", width: "100%" }}>
                            <select
                                className="field"
                                value={selectedCategory}
                                disabled={isLoadingCategories}
                                onChange={(e) => {
                                    setSelectedCategory(e.target.value);
                                    setCurrentPage(1);
                                }}
                                style={{
                                    width: "100%",
                                    fontSize: "13px",
                                    background: "#ffffff",
                                    cursor: isLoadingCategories ? "wait" : "pointer",
                                    paddingRight: "36px",
                                    WebkitAppearance: "none",
                                    MozAppearance: "none",
                                    appearance: "none",
                                }}
                            >
                                <option value="ALL">
                                    {isLoadingCategories ? "Loading categories..." : "All Categories"}
                                </option>
                                {categories.map((cat) => (
                                    <option key={cat.categoryId} value={String(cat.categoryId)}>
                                        {cat.categoryId} {cat.categoryName}
                                    </option>
                                ))}
                            </select>
                            <div
                                style={{
                                    position: "absolute",
                                    right: "12px",
                                    top: "50%",
                                    transform: "translateY(-50%)",
                                    pointerEvents: "none",
                                    display: "flex",
                                    alignItems: "center",
                                    color: "#64748b",
                                }}
                            >
                                <Icon name="chevron-down" />
                            </div>
                        </div>
                    </div>

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
                            <option value={10}>10</option>
                            <option value={20}>20</option>
                            <option value={50}>50</option>
                        </select>
                    </div>
                </div>

                {/* Table Wrap */}
                <div
                    className="table-wrap"
                    style={{
                        opacity: isLoadingDatasets ? 0.5 : 1,
                        transition: "opacity 0.2s ease-in-out",
                    }}
                >
                    <table className="tbl">
                        <thead>
                            <tr>
                                <th style={{ width: "60px", textAlign: "center" }}>No.</th>
                                <th>Dataset Title</th>
                                <th>Category Description</th>
                                <th>Records</th>
                                <th>Last Updated</th>
                                <th style={{ width: "80px", textAlign: "center" }}>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {!hasInitialLoaded && isLoadingDatasets ? (
                                <tr>
                                    <td colSpan={6} style={{ textAlign: "center", padding: "48px", color: "#64748b" }}>
                                        Loading datasets...
                                    </td>
                                </tr>
                            ) : datasets.length === 0 ? (
                                <tr>
                                    <td
                                        colSpan={6}
                                        style={{
                                            textAlign: "center",
                                            padding: "48px",
                                            color: errorMessage ? "var(--danger, #ef4444)" : "#64748b",
                                        }}
                                    >
                                        {errorMessage || "No datasets found."}
                                        {errorMessage && (
                                            <div style={{ marginTop: "12px" }}>
                                                <button
                                                    className="btn btn-outline btn-sm"
                                                    onClick={() => fetchDatasets()}
                                                    disabled={isLoadingDatasets}
                                                >
                                                    Retry
                                                </button>
                                            </div>
                                        )}
                                    </td>
                                </tr>
                            ) : (
                                datasets.map((row, index) => {
                                    const rowNumber = startItemIndex + index;

                                    return (
                                        <tr key={row.upload_id || `${row.sourceId}-${currentPage}-${index}`}>
                                            <td style={{ textAlign: "center", color: "#64748b", fontWeight: 500 }}>
                                                {rowNumber}
                                            </td>
                                            <td>
                                                <div className="cell-main">{row.title}</div>
                                                <div className="cell-sub">{row.sourceId}</div>
                                            </td>
                                            <td>
                                                <span
                                                    className="badge badge-green"
                                                    style={{
                                                        backgroundColor: "#e6f4ea",
                                                        color: "#137333",
                                                        borderColor: "#ceead6",
                                                    }}
                                                >
                                                    {row.categoryDescription || "N/A"}
                                                </span>
                                            </td>
                                            <td style={{ fontVariantNumeric: "tabular-nums" }}>
                                                {row.totalRecord?.toLocaleString() || 0}
                                            </td>
                                            <td className="cell-sub">{formatDate(row.lastChangeDate)}</td>
                                            <td>
                                                <div className="t-row-actions" style={{ justifyContent: "center" }}>
                                                    <button
                                                        className="icon-btn"
                                                        title="View Detail"
                                                        aria-label={`View detail of ${row.title}`}
                                                        disabled={!row.upload_id}
                                                        onClick={() => navigate(`/admin/data-management/${encodeURIComponent(row.upload_id)}`)}
                                                    >
                                                        <Icon name="eye" />
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

                {/* Pagination Controls */}
                <div
                    className="toolbar between"
                    style={{
                        borderTop: "1px solid var(--border)",
                        padding: "12px 20px",
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        flexWrap: "wrap",
                        gap: "10px",
                    }}
                >
                    <span className="cell-sub">
                        Showing {startItemIndex}–{endItemIndex} of {totalItems} datasets
                    </span>

                    <div className="row" style={{ display: "flex", gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
                        <button
                            className="btn btn-outline btn-sm"
                            disabled={isFirstPage || isLoadingDatasets}
                            onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 1))}
                            style={{
                                opacity: isFirstPage || isLoadingDatasets ? 0.5 : 1,
                                cursor: isFirstPage || isLoadingDatasets ? "not-allowed" : "pointer",
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
                                    disabled={isLoadingDatasets}
                                    aria-current={currentPage === page ? "page" : undefined}
                                    onClick={() => page !== currentPage && setCurrentPage(page)}
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
                            disabled={isLastPage || isLoadingDatasets}
                            onClick={() => setCurrentPage((prev) => Math.min(prev + 1, effectiveTotalPages))}
                            style={{
                                opacity: isLastPage || isLoadingDatasets ? 0.5 : 1,
                                cursor: isLastPage || isLoadingDatasets ? "not-allowed" : "pointer",
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
