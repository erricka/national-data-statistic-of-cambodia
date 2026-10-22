import { createPortal } from "react-dom";
import Icon from "../../../../icons/Icon";
import type { ApiCategoryItem } from "../../../../service/admin/category-service/categoryListService";

interface ViewCategoryModalProps {
    isOpen: boolean;
    categoryData: ApiCategoryItem | null;
    onClose: () => void;
}

export default function ViewCategoryModal({ isOpen, categoryData, onClose }: ViewCategoryModalProps) {
    if (!isOpen || !categoryData) return null;

    const formatDate = (rawDate: string) => {
        if (!rawDate || rawDate.length !== 8) return rawDate;
        return `${rawDate.slice(0, 4)}-${rawDate.slice(4, 6)}-${rawDate.slice(6, 8)}`;
    };

    return createPortal(
        <div
            style={{
                position: "fixed",
                inset: 0,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                zIndex: 99999,
                padding: "16px",
                boxSizing: "border-box"
            }}
        >
            {/* Backdrop Mask */}
            <div
                style={{
                    position: "absolute",
                    inset: 0,
                    background: "rgba(15, 23, 42, 0.5)",
                    zIndex: -1
                }}
                onClick={onClose}
            />

            {/* Modal Card */}
            <div
                style={{
                    position: "relative",
                    width: "100%",
                    maxWidth: "750px",
                    background: "#ffffff",
                    borderRadius: "12px",
                    boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)",
                    boxSizing: "border-box",
                    overflow: "hidden",
                    zIndex: 1,
                    textAlign: "left"
                }}
            >
                {/* Header Layout */}
                <div
                    style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        padding: "16px 24px",
                        borderBottom: "1px solid var(--border)",
                        boxSizing: "border-box"
                    }}
                >
                    <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                        <h3 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 600, color: "#334155" }}>
                            Category Details
                        </h3>
                    </div>
                    <button className="icon-btn" onClick={onClose} type="button" style={{ color: "#cbd5e1" }}>
                        <Icon name="x" />
                    </button>
                </div>

                {/* Body Content */}
                <div
                    style={{
                        display: "flex",
                        gap: "32px",
                        padding: "32px",
                        boxSizing: "border-box"
                    }}
                >
                    {/* Info Column */}
                    <div
                        style={{
                            flex: 1,
                            display: "flex",
                            flexDirection: "column",
                            gap: "16px",
                            fontSize: "1rem",
                            color: "#475569",
                            lineHeight: "1.5"
                        }}
                    >
                        <div>
                            <strong style={{ color: "#1e293b" }}>Name:</strong> {categoryData.categoryName}
                        </div>
                        <div>
                            <strong style={{ color: "#1e293b" }}>Description:</strong> {categoryData.description || "-"}
                        </div>
                        <div>
                            <strong style={{ color: "#1e293b" }}>Status:</strong>{" "}
                            <span
                                className={`badge ${categoryData.status === "01" ? "badge-success" : "badge-secondary"}`}
                                style={{ display: "inline-flex" }}
                            >
                                <span className="d"></span>
                                {categoryData.status === "01" ? "Active" : "Inactive"}
                            </span>
                        </div>
                        <div>
                            <strong style={{ color: "#1e293b" }}>Updated:</strong> {formatDate(categoryData.lastUpdate)}
                        </div>
                    </div>
                </div>
            </div>
        </div>,
        document.body
    );
}