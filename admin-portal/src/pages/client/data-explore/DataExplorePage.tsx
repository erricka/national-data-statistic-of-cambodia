import { useState, useEffect, useCallback } from "react";
import Icon from "../../../icons/Icon";
import NavBar from "../../../layout/NavBar";
import { useNavigate } from "react-router-dom";
import {
    getDataExplore,
    type DataExploreItem,
} from "../../../service/client/data-explore/dataExploreService";

function getPageNumbers(currentPage: number, totalPages: number) {
    const pages: (number | string)[] = [];

    if (totalPages <= 7) {
        for (let i = 1; i <= totalPages; i++) pages.push(i);
    } else {
        if (currentPage <= 4) {
            pages.push(1, 2, 3, 4, 5, "...", totalPages);
        } else if (currentPage >= totalPages - 3) {
            pages.push(
                1,
                "...",
                totalPages - 4,
                totalPages - 3,
                totalPages - 2,
                totalPages - 1,
                totalPages
            );
        } else {
            pages.push(
                1,
                "...",
                currentPage - 1,
                currentPage,
                currentPage + 1,
                "...",
                totalPages
            );
        }
    }
    return pages;
}

export default function DataExplorePage() {
    const navigate = useNavigate();

    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [datasets, setDatasets] = useState<DataExploreItem[]>([]);

    // Search & Debounce States
    const [searchQuery, setSearchQuery] = useState("");
    const [debouncedSearch, setDebouncedSearch] = useState("");

    const [currentPage, setCurrentPage] = useState(1);
    const itemsPerPage = 50;
    const [totalItems, setTotalItems] = useState(0);

    // 1. Debounce only searchQuery (200ms delay)
    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedSearch(searchQuery.trim());
        }, 200);

        return () => clearTimeout(handler);
    }, [searchQuery]);

    // 2. Fetch data whenever debouncedSearch OR currentPage changes
    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);

            const payload = {
                search: debouncedSearch,
                page: currentPage - 1,
                size: itemsPerPage,
            };

            const res = await getDataExplore(payload);

            if (res.code === "000000" && res.data) {
                setDatasets(res.data.list || []);
                const calcTotal =
                    res.data.total ?? res.data.list?.length ?? 0;
                setTotalItems(calcTotal);
            } else {
                const errorMsg =
                    typeof res.message === "object" && res.message !== null
                        ? res.message.messageTitle
                        : res.message || "Failed to fetch datasets.";
                setError(errorMsg);
            }
        } catch (err: any) {
            setError(err.message || "An unexpected error occurred.");
        } finally {
            setLoading(false);
        }
    }, [debouncedSearch, currentPage, itemsPerPage]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const handleSearchChange = (val: string) => {
        setSearchQuery(val);
        setCurrentPage(1);
    };

    const handleResetFilters = () => {
        setSearchQuery("");
        setDebouncedSearch("");
        setCurrentPage(1);
    };

    const totalPages = Math.ceil(totalItems / itemsPerPage) || 1;
    const hasActiveSearch = searchQuery.trim().length > 0;

    return (
        <div
            className="about-page-wrapper"
            style={{
                width: "100%",
                maxWidth: "100vw",
                overflowX: "hidden",
                boxSizing: "border-box",
                display: "flex",
                flexDirection: "column",
                minHeight: "100vh",
            }}
        >
            <NavBar />

            <main
                className="about-container"
                style={{
                    flex: 1,
                    maxWidth: "1200px",
                    width: "100%",
                    margin: "0 auto",
                    padding: "24px",
                    boxSizing: "border-box",
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: loading || error ? "center" : "flex-start",
                }}
            >
                <div className="content" style={{ position: "relative", width: "100%" }}>
                    {/* 1. Page Header */}
                    <div className="page-head" style={{ marginBottom: "20px" }}>
                        <div>
                            <h1>Data Explore</h1>
                            <p>Search, inspect, and query public datasets instantly.</p>
                        </div>
                    </div>

                    {/* 2. Top Minimalist Filter Bar */}
                    <div className="card card-pad" style={{ marginBottom: "24px" }}>
                        <div className="row" style={{ gap: "12px", flexWrap: "wrap" }}>
                            {/* Search Input */}
                            <div className="search" style={{ flex: 1, minWidth: "280px" }}>
                                <Icon name="search" />
                                <input
                                    type="text"
                                    placeholder="Search datasets..."
                                    value={searchQuery}
                                    style={{ paddingTop: "8px", paddingBottom: "8px" }}
                                    onChange={(e) => handleSearchChange(e.target.value)}
                                />
                                {searchQuery && (
                                    <button
                                        onClick={() => handleSearchChange("")}
                                        style={{
                                            border: "none",
                                            background: "transparent",
                                            cursor: "pointer",
                                            padding: "0 8px",
                                            color: "#94a3b8",
                                        }}
                                    >
                                        ✕
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* 3. Dynamic Section: Standard Loading / Error / Content */}
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
                            {/* Dataset Grid */}
                            <div
                                className="grid"
                                style={{
                                    gridTemplateColumns:
                                        "repeat(auto-fill, minmax(340px, 1fr))",
                                    gap: "20px",
                                }}
                            >
                                {datasets.map((item) => (
                                    <div
                                        key={item.source_id}
                                        className="card card-pad dataset-card"
                                        style={{
                                            border: "1px solid var(--border)",
                                            display: "flex",
                                            flexDirection: "column",
                                        }}
                                    >
                                        <div
                                            className="between row"
                                            style={{ marginBottom: "10px" }}
                                        >
                                            <span className="badge badge-teal">
                                                {item.category_description || "General"}
                                            </span>
                                        </div>

                                        <h3
                                            style={{
                                                fontSize: "16px",
                                                fontWeight: "600",
                                                marginBottom: "8px",
                                                lineHeight: 1.5,
                                            }}
                                        >
                                            {item.title}
                                        </h3>

                                        <div
                                            className="between row"
                                            style={{
                                                marginTop: "auto",
                                                paddingTop: "12px",
                                                borderTop: "1px solid var(--border)",
                                            }}
                                        >
                                            <div className="row" style={{ gap: "4px" }}>
                                                <span className="badge badge-neutral">
                                                    {item.file_extention ? item.file_extention.toUpperCase() : "FILE"}
                                                </span>
                                            </div>
                                            <button
                                                className="btn btn-ghost btn-sm"
                                                onClick={() =>
                                                    navigate(`/detail/${item.source_id}`, {
                                                        state: {
                                                            sourceName: (item as any).source_name || item.title,
                                                            uploadId: (item as any).upload_id || item.upload_id
                                                        },
                                                    })
                                                }
                                            >
                                                View Detail →
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>

                            {/* 4. Pagination Bar */}
                            {totalPages > 0 && (
                                <div
                                    className="between row"
                                    style={{
                                        marginTop: "32px",
                                        paddingTop: "20px",
                                        borderTop: "1px solid #e2e8f0",
                                        alignItems: "center",
                                    }}
                                >
                                    {/* Left Text */}
                                    <span
                                        className="cell-sub"
                                        style={{ fontSize: "14px", color: "#64748b" }}
                                    >
                                        Showing <b>{(currentPage - 1) * itemsPerPage + 1}</b> –{" "}
                                        <b>{Math.min(currentPage * itemsPerPage, totalItems)}</b> of{" "}
                                        <b>{totalItems}</b> datasets
                                    </span>

                                    {/* Right Pagination Buttons */}
                                    <div className="row" style={{ gap: "6px", alignItems: "center" }}>
                                        {/* Previous Button */}
                                        <button
                                            disabled={currentPage === 1}
                                            onClick={() =>
                                                setCurrentPage((prev) => Math.max(prev - 1, 1))
                                            }
                                            style={{
                                                background: currentPage === 1 ? "#f8fafc" : "#ffffff",
                                                color: currentPage === 1 ? "#cbd5e1" : "#334155",
                                                border: "1px solid #e2e8f0",
                                                borderRadius: "8px",
                                                padding: "8px 14px",
                                                fontSize: "14px",
                                                fontWeight: 500,
                                                cursor: currentPage === 1 ? "not-allowed" : "pointer",
                                                transition: "all 0.15s ease",
                                            }}
                                        >
                                            Previous
                                        </button>

                                        {/* Truncated Number List */}
                                        {getPageNumbers(currentPage, totalPages).map((page, index) => {
                                            if (page === "...") {
                                                return (
                                                    <span
                                                        key={`dots-${index}`}
                                                        style={{ padding: "0 6px", color: "#94a3b8" }}
                                                    >
                                                        ...
                                                    </span>
                                                );
                                            }

                                            const pageNum = page as number;
                                            const isActive = currentPage === pageNum;

                                            return (
                                                <button
                                                    key={pageNum}
                                                    onClick={() => setCurrentPage(pageNum)}
                                                    style={{
                                                        background: isActive ? "#2563eb" : "#ffffff",
                                                        color: isActive ? "#ffffff" : "#334155",
                                                        border: isActive
                                                            ? "1px solid #2563eb"
                                                            : "1px solid #e2e8f0",
                                                        borderRadius: "8px",
                                                        minWidth: "36px",
                                                        height: "36px",
                                                        padding: "0 8px",
                                                        fontSize: "14px",
                                                        fontWeight: 600,
                                                        cursor: "pointer",
                                                        transition: "all 0.15s ease",
                                                    }}
                                                >
                                                    {pageNum}
                                                </button>
                                            );
                                        })}

                                        {/* Next Button */}
                                        <button
                                            disabled={currentPage === totalPages || totalPages === 0}
                                            onClick={() =>
                                                setCurrentPage((prev) =>
                                                    Math.min(prev + 1, totalPages)
                                                )
                                            }
                                            style={{
                                                background:
                                                    currentPage === totalPages || totalPages === 0
                                                        ? "#f8fafc"
                                                        : "#ffffff",
                                                color:
                                                    currentPage === totalPages || totalPages === 0
                                                        ? "#cbd5e1"
                                                        : "#334155",
                                                border: "1px solid #e2e8f0",
                                                borderRadius: "8px",
                                                padding: "8px 14px",
                                                fontSize: "14px",
                                                fontWeight: 500,
                                                cursor:
                                                    currentPage === totalPages || totalPages === 0
                                                        ? "not-allowed"
                                                        : "pointer",
                                                transition: "all 0.15s ease",
                                            }}
                                        >
                                            Next
                                        </button>
                                    </div>
                                </div>
                            )}
                        </>
                    ) : (
                        /* Empty State Container */
                        <div
                            className="card card-pad"
                            style={{ textAlign: "center", padding: "48px 24px" }}
                        >
                            <h3>
                                {hasActiveSearch
                                    ? "No datasets found"
                                    : "No datasets available"}
                            </h3>
                            <p className="cell-sub" style={{ marginTop: "8px" }}>
                                {hasActiveSearch
                                    ? "Try adjusting your search terms to find what you're looking for."
                                    : "There are currently no datasets published in the repository."}
                            </p>

                            {/* Render Reset Filters button ONLY if search input is active */}
                            {hasActiveSearch && (
                                <button
                                    className="btn btn-primary"
                                    style={{ marginTop: "16px" }}
                                    onClick={handleResetFilters}
                                >
                                    Reset Filters
                                </button>
                            )}
                        </div>
                    )}
                </div>
            </main>

            {/* Sticky Footer */}
            <footer
                style={{
                    background: "#0F3D91",
                    color: "#9DBDF0",
                    padding: "32px",
                    marginTop: "auto",
                    textAlign: "center",
                    width: "100%",
                    boxSizing: "border-box",
                }}
            >
                © 2026 National Institute of Statistics · Kingdom of Cambodia
            </footer>
        </div>
    );
}