import { useState, useEffect } from "react";
import { useNavigate, useLocation, useSearchParams } from "react-router-dom";
import Icon from "../../../icons/Icon";
import NavBar from "../../../layout/NavBar";
import DatasetCard from "../../../components/home/DatasetCard";
import "../../../index.css";
import {
    getCategoryDetails,
    type CategoryDetailItem,
} from "../../../service/client/categories/categoryDetailService";
import type { CategoryListItem } from "../../../service/client/categories/categoriesService";

const PAGE_SIZE = 8;
const SEARCH_DEBOUNCE_MS = 300;

function getPageNumbers(currentPage: number, totalPages: number) {
    const pages: (number | string)[] = [];

    if (totalPages <= 7) {
        for (let i = 1; i <= totalPages; i++) pages.push(i);
    } else {
        if (currentPage <= 4) {
            pages.push(1, 2, 3, 4, 5, "...", totalPages);
        } else if (currentPage >= totalPages - 3) {
            pages.push(1, "...", totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages);
        } else {
            pages.push(1, "...", currentPage - 1, currentPage, currentPage + 1, "...", totalPages);
        }
    }
    return pages;
}

/** Result of one API call, tagged with the request it belongs to. */
interface LoadResult {
    key: string;
    list: CategoryDetailItem[];
    total: number;
    totalPages: number;
    error: string | null;
}

export default function CategoryDetailsPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams] = useSearchParams();

    // 1. Retrieve category data passed via router state or query param
    const categoryFromState = (location.state as { category?: CategoryListItem })?.category;
    const categoryIdFromQuery = searchParams.get("id");
    const categoryId = categoryFromState?.categoryId || Number(categoryIdFromQuery) || 0;

    // Component states
    const [searchQuery, setSearchQuery] = useState("");        // what the user types
    const [debouncedSearch, setDebouncedSearch] = useState(""); // what is sent to the API
    const [currentPage, setCurrentPage] = useState(1);          // 1-indexed, same as the API
    const [result, setResult] = useState<LoadResult | null>(null);

    // 2. Debounce typing; a new search always starts again from page 1
    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedSearch(searchQuery.trim());
            setCurrentPage(1);
        }, SEARCH_DEBOUNCE_MS);
        return () => clearTimeout(handler);
    }, [searchQuery]);

    // 3. One request per (category, search, page). Loading is "the latest result
    //    is not for the current request yet".
    const requestKey = categoryId ? `${categoryId}|${debouncedSearch}|${currentPage}` : "";
    const loading = requestKey !== "" && result?.key !== requestKey;

    useEffect(() => {
        if (!requestKey) return;
        let active = true; // ignore responses that arrive after a newer request

        getCategoryDetails({
            search: debouncedSearch,
            page: currentPage, // backend pages start at 1 (offset = (page - 1) * size)
            size: PAGE_SIZE,
            categoryId,
        })
            .then((res) => {
                if (!active) return;
                if (res.code === "000000" && res.data) {
                    setResult({
                        key: requestKey,
                        list: res.data.list || [],
                        total: res.data.total || 0,
                        totalPages: res.data.totalPages || 0,
                        error: null,
                    });
                } else {
                    const errorMsg =
                        typeof res.message === "object" && res.message !== null
                            ? res.message.messageTitle
                            : res.message || "Failed to load datasets.";
                    setResult({ key: requestKey, list: [], total: 0, totalPages: 0, error: errorMsg });
                }
            })
            .catch((err: unknown) => {
                if (!active) return;
                const message = err instanceof Error ? err.message : "An unexpected error occurred.";
                setResult({ key: requestKey, list: [], total: 0, totalPages: 0, error: message });
            });

        return () => {
            active = false;
        };
    }, [requestKey, categoryId, debouncedSearch, currentPage]);

    const datasets = result?.list ?? [];
    const totalItems = result?.total ?? 0;
    const totalPages = result?.totalPages ?? 0;
    const error = !categoryId ? "Category not found." : result?.error ?? null;

    const goToPage = (page: number) => {
        setCurrentPage(page);
        window.scrollTo({ top: 0, behavior: "smooth" });
    };

    const clearSearch = () => {
        setSearchQuery("");
        setDebouncedSearch(""); // apply immediately, without waiting for the debounce
        setCurrentPage(1);
    };

    // UI Pagination range helpers
    const startIndex = (currentPage - 1) * PAGE_SIZE;
    const endIndex = Math.min(startIndex + datasets.length, totalItems);
    const isFirstPage = currentPage <= 1;
    const isLastPage = currentPage >= totalPages || totalPages === 0;

    return (
        <div className="about-page-wrapper">
            <NavBar />

            <main className="about-container">
                <div className="category-breadcrumb">
                    <button className="btn-back" onClick={() => navigate("/categories")}>
                        <Icon name="arrow-left" />
                        <span>Back to Categories</span>
                    </button>
                </div>

                {/* Header Information */}
                <header className="category-detail-header">
                    <div className="cat-header-top">
                        <div
                            className="cat-header-icon"
                            style={{ backgroundColor: "#ECFDF5", color: "#10B981" }}
                        >
                            <Icon name="building" />
                        </div>
                        <div className="cat-header-titles">
                            <h1 className="cat-header-khmer">
                                {categoryFromState?.categoryId} {categoryFromState?.categoryName || "Category Details"}
                            </h1>
                        </div>
                    </div>

                    <p className="cat-header-desc">
                        {categoryFromState?.description || "Explore datasets associated with this category."}
                    </p>

                    <div className="cat-header-meta-row">
                        <div className="meta-item">
                            <span className="meta-label">Total Datasets</span>
                            <span className="meta-value">
                                {categoryFromState?.totalDataset ?? totalItems}
                            </span>
                        </div>
                    </div>
                </header>

                {/* Search Bar */}
                <div className="category-datasets-toolbar">
                    <div className="cat-search-box">
                        <Icon name="search" />
                        <input
                            type="text"
                            placeholder="Search datasets in this category..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>
                </div>

                {/* Content Area */}
                {loading ? (
                    <div
                        style={{
                            textAlign: "center",
                            padding: "80px 0",
                            color: "#0F3D91",
                            fontWeight: 600,
                            fontSize: "16px",
                        }}
                    >
                        កំពុងទាញយកទិន្នន័យ...
                    </div>
                ) : error ? (
                    <div
                        style={{
                            textAlign: "center",
                            padding: "80px 0",
                            color: "#EF4444",
                            fontWeight: 600,
                        }}
                    >
                        {error}
                    </div>
                ) : datasets.length > 0 ? (
                    <>
                        <div className="datasets-grid">
                            {datasets.map((item) => (
                                <div
                                    key={item.upload_id ?? item.sourceId}
                                    onClick={() =>
                                        navigate(`/detail/${item.sourceId}`, {
                                            state: {
                                                sourceName: item.title,
                                                uploadId: item.upload_id
                                            },
                                        })
                                    }
                                    style={{ cursor: "pointer" }}
                                >
                                    <DatasetCard
                                        item={{
                                            sourceId: item.sourceId,
                                            title: item.title,
                                            category: item.categoryDescription,
                                            records: `${item.totalRecord?.toLocaleString() || 0}`,
                                            downloads: "—",
                                            badge: "teal",
                                        }}
                                    />
                                </div>
                            ))}
                        </div>

                        {/* Pagination Bar */}
                        {totalPages > 0 && (
                            <div
                                className="between row"
                                style={{
                                    marginTop: "32px",
                                    paddingTop: "20px",
                                    borderTop: "1px solid #e2e8f0",
                                    alignItems: "center",
                                    flexWrap: "wrap",
                                    gap: "12px",
                                }}
                            >
                                <span className="cell-sub" style={{ fontSize: "14px", color: "#64748b" }}>
                                    Showing <b>{totalItems > 0 ? startIndex + 1 : 0}</b> – <b>{endIndex}</b> of <b>{totalItems}</b> datasets
                                </span>

                                <div className="row" style={{ gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
                                    <button
                                        disabled={isFirstPage}
                                        onClick={() => goToPage(Math.max(currentPage - 1, 1))}
                                        style={{
                                            background: isFirstPage ? "#f8fafc" : "#ffffff",
                                            color: isFirstPage ? "#cbd5e1" : "#334155",
                                            border: "1px solid #e2e8f0",
                                            borderRadius: "8px",
                                            padding: "8px 14px",
                                            fontSize: "14px",
                                            fontWeight: 500,
                                            cursor: isFirstPage ? "not-allowed" : "pointer",
                                        }}
                                    >
                                        Previous
                                    </button>

                                    {getPageNumbers(currentPage, totalPages).map((page, index) => {
                                        if (page === "...") {
                                            return (
                                                <span key={`dots-${index}`} style={{ padding: "0 6px", color: "#94a3b8" }}>
                                                    ...
                                                </span>
                                            );
                                        }

                                        const pageNum = page as number;
                                        const isActive = currentPage === pageNum;

                                        return (
                                            <button
                                                key={pageNum}
                                                onClick={() => !isActive && goToPage(pageNum)}
                                                aria-current={isActive ? "page" : undefined}
                                                style={{
                                                    background: isActive ? "#0F3D91" : "#ffffff",
                                                    color: isActive ? "#ffffff" : "#334155",
                                                    border: isActive ? "1px solid #0F3D91" : "1px solid #e2e8f0",
                                                    borderRadius: "8px",
                                                    minWidth: "36px",
                                                    height: "36px",
                                                    padding: "0 8px",
                                                    fontSize: "14px",
                                                    fontWeight: 600,
                                                    cursor: isActive ? "default" : "pointer",
                                                }}
                                            >
                                                {pageNum}
                                            </button>
                                        );
                                    })}

                                    <button
                                        disabled={isLastPage}
                                        onClick={() => goToPage(Math.min(currentPage + 1, totalPages))}
                                        style={{
                                            background: isLastPage ? "#f8fafc" : "#ffffff",
                                            color: isLastPage ? "#cbd5e1" : "#334155",
                                            border: "1px solid #e2e8f0",
                                            borderRadius: "8px",
                                            padding: "8px 14px",
                                            fontSize: "14px",
                                            fontWeight: 500,
                                            cursor: isLastPage ? "not-allowed" : "pointer",
                                        }}
                                    >
                                        Next
                                    </button>
                                </div>
                            </div>
                        )}
                    </>
                ) : (
                    <div className="empty-datasets-state">
                        <Icon name="file" />
                        <h3>
                            {debouncedSearch ? "No Datasets Found" : "No Datasets Available"}
                        </h3>
                        <p>
                            {debouncedSearch
                                ? `No results match "${debouncedSearch}". Try a different keyword.`
                                : "There are currently no datasets available in this category."}
                        </p>

                        {searchQuery.trim() !== "" && (
                            <button
                                className="btn btn-outline"
                                style={{ marginTop: "8px" }}
                                onClick={clearSearch}
                            >
                                Clear Search
                            </button>
                        )}
                    </div>
                )}
            </main>

            <footer className="about-footer">
                <p>© 2026 National Institute of Statistics · Kingdom of Cambodia</p>
            </footer>
        </div>
    );
}