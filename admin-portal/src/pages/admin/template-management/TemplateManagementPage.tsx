import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";
import { MOCK_TEMPLATES, type TemplateItem, } from "../../../data/mockTemplates";

const ITEMS_PER_PAGE = 8;

export default function TemplateManagementPage() {
    const navigate = useNavigate();
    const [searchTerm, setSearchTerm] = useState("");
    const [currentPage, setCurrentPage] = useState(1);
    const [templates] = useState<TemplateItem[]>(MOCK_TEMPLATES);

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "template_management",
                title: "Template Management",
                breadcrumb: ["Home", "Template Management"]
            });
        }
    }, []);

    // Filter templates dynamically by search input
    const filteredTemplates = useMemo(() => {
        return templates.filter((tpl) =>
            tpl.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
            tpl.code.toLowerCase().includes(searchTerm.toLowerCase()) ||
            tpl.description.toLowerCase().includes(searchTerm.toLowerCase())
        );
    }, [templates, searchTerm]);

    // Calculate Pagination variables
    const totalPages = Math.ceil(filteredTemplates.length / ITEMS_PER_PAGE) || 1;

    // Reset back to page 1 whenever search query changes
    const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearchTerm(e.target.value);
        setCurrentPage(1);
    };

    // Paginated slice
    const displayedTemplates = useMemo(() => {
        const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        return filteredTemplates.slice(startIndex, startIndex + ITEMS_PER_PAGE);
    }, [filteredTemplates, currentPage]);

    const startItemIndex = filteredTemplates.length > 0 ? (currentPage - 1) * ITEMS_PER_PAGE + 1 : 0;
    const endItemIndex = Math.min(currentPage * ITEMS_PER_PAGE, filteredTemplates.length);

    // const getStatusBadge = (status: TemplateItem["status"]) => {
    //     switch (status) {
    //         case "active":
    //             return { class: "badge-success", text: "Active" };
    //         case "draft":
    //             return { class: "badge-warning", text: "Draft" };
    //         case "archived":
    //             return { class: "badge-danger", text: "Archived" };
    //         default:
    //             return { class: "", text: status };
    //     }
    // };

    // Helper for generating page numbers array (with truncation if needed)
    const getPageNumbers = () => {
        const pages: (number | string)[] = [];
        if (totalPages <= 5) {
            for (let i = 1; i <= totalPages; i++) pages.push(i);
        } else {
            if (currentPage <= 3) {
                pages.push(1, 2, 3, 4, "...", totalPages);
            } else if (currentPage >= totalPages - 2) {
                pages.push(1, "...", totalPages - 3, totalPages - 2, totalPages - 1, totalPages);
            } else {
                pages.push(1, "...", currentPage - 1, currentPage, currentPage + 1, "...", totalPages);
            }
        }
        return pages;
    };

    return (
        <div className="app">
            <Sidebar active="template_management" />

            <div className="main">
                <Topbar pageTitle="Template Management" />

                <main className="content">
                    {/* Header Row */}
                    <div className="page-head">
                        <div>
                            <h1>Schema Templates</h1>
                            <p className="cell-sub" style={{ marginTop: "4px" }}>
                                Manage standardized dataset schema templates across all departments.
                            </p>
                        </div>
                        <div className="actions">
                            <button
                                className="btn btn-primary"
                                onClick={() => navigate("/admin/create-template")}
                            >
                                <Icon name="plus" /> New Template
                            </button>
                        </div>
                    </div>

                    {/* Stats Overview */}
                    <div className="stat-grid" style={{ marginBottom: "20px" }}>
                        <div className="stat">
                            <div className="label">Total Schema Templates</div>
                            <div className="val">{templates.length}</div>
                        </div>
                    </div>

                    {/* Search & Toolbar */}
                    <div className="card" style={{ padding: "16px", marginBottom: "24px" }}>
                        <div className="toolbar" style={{ padding: 0, border: "none" }}>
                            <label className="field" style={{ flex: 1, maxWidth: "420px" }}>
                                <span><Icon name="search" /></span>
                                <input
                                    type="text"
                                    placeholder="Search template by name, code or keyword..."
                                    value={searchTerm}
                                    style={{ width: "100%" }}
                                    onChange={handleSearchChange}
                                />
                            </label>
                        </div>
                    </div>

                    {/* 3 Column Grid Box Layout */}
                    <div
                        style={{
                            display: "grid",
                            gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))",
                            gap: "20px"
                        }}
                    >
                        {displayedTemplates.map((tpl) => {
                            // const badge = getStatusBadge(tpl.status);
                            return (
                                <div
                                    key={tpl.id}
                                    className="card"
                                    style={{
                                        padding: "20px",
                                        display: "flex",
                                        flexDirection: "column",
                                        justifyContent: "space-between",
                                        transition: "transform 0.15s ease, box-shadow 0.15s ease",
                                        cursor: "pointer"
                                    }}
                                >
                                    <div>
                                        {/* Top Card Row */}
                                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "12px" }}>
                                            <span
                                                style={{
                                                    fontSize: "12px",
                                                    fontFamily: "monospace",
                                                    color: "#64748b",
                                                    background: "#f1f5f9",
                                                    padding: "2px 8px",
                                                    borderRadius: "4px"
                                                }}
                                            >
                                                {tpl.code}
                                            </span>
                                            {/* <span className={`badge ${badge.class}`}>
                                                <span className="d"></span>
                                                {badge.text}
                                            </span> */}
                                        </div>

                                        {/* Title & Description */}
                                        <h3 style={{ fontSize: "16px", fontWeight: "600", color: "var(--text-main)", margin: "0 0 6px 0" }}>
                                            {tpl.name}
                                        </h3>
                                        <p className="cell-sub" style={{ fontSize: "13px", lineHeight: "1.5", marginBottom: "16px" }}>
                                            {tpl.description}
                                        </p>

                                        {/* Sample Columns Preview Chips */}
                                        <div style={{ background: "#f8fafc", borderRadius: "8px", padding: "10px 12px", marginBottom: "16px" }}>
                                            <div style={{ fontSize: "11px", fontWeight: 600, color: "#64748b", textTransform: "uppercase", marginBottom: "6px" }}>
                                                Preview Fields ({tpl.columnsCount} total)
                                            </div>
                                            <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }}>
                                                {tpl.sampleColumns.map((col) => (
                                                    <span
                                                        key={col.id}
                                                        style={{
                                                            fontSize: "12px",
                                                            fontFamily: "monospace",
                                                            background: "#ffffff",
                                                            border: "1px solid #e2e8f0",
                                                            borderRadius: "4px",
                                                            padding: "2px 6px",
                                                            color: "#334155"
                                                        }}
                                                    >
                                                        {col.item_name}
                                                    </span>
                                                ))}
                                                {tpl.columnsCount > 3 && (
                                                    <span style={{ fontSize: "12px", color: "#64748b", padding: "2px 4px" }}>
                                                        +{tpl.columnsCount - 3} more
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                    </div>

                                    {/* Bottom Card Footer */}
                                    <div
                                        style={{
                                            borderTop: "1px solid var(--border)",
                                            paddingTop: "12px",
                                            display: "flex",
                                            justifyContent: "space-between",
                                            alignItems: "center"
                                        }}
                                        onClick={(e) => e.stopPropagation()}
                                    >
                                        <div className="cell-sub" style={{ fontSize: "12px" }}>
                                            Updated {tpl.updatedAt}
                                        </div>

                                        <div className="t-row-actions">
                                            <button
                                                className="icon-btn"
                                                title="View Template"
                                                // onClick={() => navigate(`/admin/template/${tpl.id}`)}
                                                onClick={() => navigate("/admin/template-detail")}
                                            >
                                                <Icon name="eye" />
                                            </button>
                                            <button
                                                className="icon-btn"
                                                title="Edit Template"
                                                // onClick={() => navigate(`/admin/edit-template/${tpl.id}`)}
                                                onClick={() => navigate("/admin/edit-template")}
                                            >
                                                <Icon name="edit" />
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    {/* Empty State */}
                    {filteredTemplates.length === 0 && (
                        <div className="card" style={{ padding: "40px", textAlign: "center", color: "#64748b", marginTop: "20px" }}>
                            <Icon name="search" />
                            <p style={{ marginTop: "8px", fontWeight: "500" }}>No matching templates found.</p>
                        </div>
                    )}

                    {/* Bottom Pagination Toolbar */}
                    {filteredTemplates.length > 0 && (
                        <div
                            style={{
                                marginTop: "24px",
                                padding: "12px 20px",
                                display: "flex",
                                justifyContent: "space-between",
                                alignItems: "center"
                            }}
                        >
                            <span className="cell-sub">
                                Showing {startItemIndex}–{endItemIndex} of {filteredTemplates.length} templates
                            </span>

                            <div className="row" style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                                <button
                                    className="btn btn-outline btn-sm"
                                    disabled={currentPage === 1}
                                    onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 1))}
                                    style={{ opacity: currentPage === 1 ? 0.5 : 1, cursor: currentPage === 1 ? "not-allowed" : "pointer", padding: "10px 12px", fontSize: "12px" }}
                                >
                                    Previous
                                </button>

                                {getPageNumbers().map((page, idx) =>
                                    typeof page === "number" ? (
                                        <button
                                            key={idx}
                                            className={`btn btn-sm ${currentPage === page ? "btn-primary" : "btn-outline"}`}
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
                                    disabled={currentPage === totalPages}
                                    onClick={() => setCurrentPage((prev) => Math.min(prev + 1, totalPages))}
                                    style={{ opacity: currentPage === totalPages ? 0.5 : 1, cursor: currentPage === totalPages ? "not-allowed" : "pointer", padding: "10px 12px", fontSize: "12px" }}
                                >
                                    Next
                                </button>
                            </div>
                        </div>
                    )}
                </main>
            </div>
        </div>
    );
}