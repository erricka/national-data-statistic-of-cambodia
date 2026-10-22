interface ApprovalPaginationProps {
    currentPage: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    isLoading: boolean;
    itemLabel: string;
    onPageChange: (page: number) => void;
}

/**
 * Pagination footer. Markup, page-number windowing and styles are identical to
 * the inline pagination used on the Dataset and User Management pages.
 */
export default function ApprovalPagination({
    currentPage,
    totalPages,
    totalItems,
    pageSize,
    isLoading,
    itemLabel,
    onPageChange,
}: ApprovalPaginationProps) {
    const effectiveTotalPages = totalPages || Math.ceil(totalItems / pageSize) || 1;
    const startItemIndex = totalItems > 0 ? (currentPage - 1) * pageSize + 1 : 0;
    const endItemIndex = Math.min(currentPage * pageSize, totalItems);

    const getPageNumbers = () => {
        const pages: (number | string)[] = [];
        if (effectiveTotalPages <= 5) {
            for (let i = 1; i <= effectiveTotalPages; i++) pages.push(i);
        } else if (currentPage <= 3) {
            pages.push(1, 2, 3, 4, "...", effectiveTotalPages);
        } else if (currentPage >= effectiveTotalPages - 2) {
            pages.push(1, "...", effectiveTotalPages - 3, effectiveTotalPages - 2, effectiveTotalPages - 1, effectiveTotalPages);
        } else {
            pages.push(1, "...", currentPage - 1, currentPage, currentPage + 1, "...", effectiveTotalPages);
        }
        return pages;
    };

    const prevDisabled = currentPage <= 1 || isLoading;
    const nextDisabled = currentPage >= effectiveTotalPages || isLoading;

    return (
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
                Showing {startItemIndex}–{endItemIndex} of {totalItems.toLocaleString()} {itemLabel}
            </span>

            <div className="row" style={{ display: "flex", gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
                <button
                    className="btn btn-outline btn-sm"
                    disabled={prevDisabled}
                    onClick={() => onPageChange(Math.max(currentPage - 1, 1))}
                    style={{
                        opacity: prevDisabled ? 0.5 : 1,
                        cursor: prevDisabled ? "not-allowed" : "pointer",
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
                            aria-current={currentPage === page ? "page" : undefined}
                            onClick={() => page !== currentPage && onPageChange(page)}
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
                    disabled={nextDisabled}
                    onClick={() => onPageChange(Math.min(currentPage + 1, effectiveTotalPages))}
                    style={{
                        opacity: nextDisabled ? 0.5 : 1,
                        cursor: nextDisabled ? "not-allowed" : "pointer",
                        padding: "10px 12px",
                        fontSize: "12px",
                    }}
                >
                    Next
                </button>
            </div>
        </div>
    );
}
