import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";
import { MOCK_TEMPLATES, type TemplateItem } from "../../../data/mockTemplates";

export default function ViewTemplatePage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [template, setTemplate] = useState<TemplateItem | null>(null);
    const [showDeleteModal, setShowDeleteModal] = useState(false);

    useEffect(() => {
        // Find template by route ID param or fallback to first item
        const found = MOCK_TEMPLATES.find((t) => t.id === id) || MOCK_TEMPLATES[0];
        setTemplate(found);

        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "template_management",
                title: found ? found.name : "View Template",
                breadcrumb: ["Home", "Template Management", "View Template"]
            });
        }
    }, [id]);

    const handleDelete = () => {
        console.log("Deleted template ID:", template?.id);
        setShowDeleteModal(false);
        navigate("/admin/template");
    };

    if (!template) {
        return null;
    }

    return (
        <div className="app">
            <Sidebar active="template_management" />

            <div className="main">
                <Topbar pageTitle="View Template" />

                <main className="content">
                    <button
                        type="button"
                        className="btn btn-outline btn-sm"
                        onClick={() => navigate("/admin/template-management")}
                        style={{ padding: "6px 10px", marginBottom: "16px" }}
                    >
                        <Icon name="arrow-left" /> Back
                    </button>
                    {/* Header Row */}
                    <div className="page-head">
                        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>

                            <div>
                                <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                                    <h1 style={{ margin: 0 }}>{template.name}</h1>
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
                                        {template.code}
                                    </span>
                                </div>
                                <p className="cell-sub" style={{ marginTop: "4px" }}>
                                    {template.description}
                                </p>
                            </div>
                        </div>

                        <div className="actions" style={{ display: "flex", gap: "10px" }}>
                            {/* <button
                                className="btn btn-outline"
                                style={{ color: "var(--danger)", borderColor: "#fecaca" }}
                                onClick={() => setShowDeleteModal(true)}
                            >
                                <Icon name="trash" /> Delete
                            </button> */}
                            <button
                                className="btn btn-primary"
                                // onClick={() => navigate(`/admin/edit-template/${template.id}`)}
                                onClick={() => navigate("/admin/edit-template")}
                            >
                                <Icon name="edit" /> Edit Template
                            </button>
                        </div>
                    </div>

                    {/* Meta Overview Cards */}
                    <div className="stat-grid" style={{ marginBottom: "24px" }}>
                        <div className="stat">
                            <div className="label">Total Schema Columns</div>
                            <div className="val">{template.columnsCount}</div>
                        </div>
                        <div className="stat">
                            <div className="label">Last Updated</div>
                            <div className="val" style={{ fontSize: "18px", marginTop: "4px" }}>
                                {template.updatedAt}
                            </div>
                        </div>
                        <div className="stat">
                            <div className="label">Template Code</div>
                            <div className="val" style={{ fontSize: "18px", fontFamily: "monospace", marginTop: "4px" }}>
                                {template.code}
                            </div>
                        </div>
                    </div>

                    {/* Column Definitions Table */}
                    <div className="card" style={{ padding: "24px" }}>
                        <div style={{ marginBottom: "16px" }}>
                            <h2 style={{ fontSize: "16px", fontWeight: "600", color: "var(--text-main)", margin: 0 }}>
                                Schema Structure & Field Definitions
                            </h2>
                            <p className="cell-sub" style={{ marginTop: "2px", margin: 0 }}>
                                Configured database column names, data types, and attribute descriptions.
                            </p>
                        </div>

                        <div className="table-wrap">
                            <table className="tbl">
                                <thead>
                                    <tr>
                                        <th style={{ width: "25%" }}>Column Name</th>
                                        <th style={{ width: "20%" }}>Data Type</th>
                                        <th style={{ width: "55%" }}>Description</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {template.sampleColumns.map((col) => (
                                        <tr key={col.id}>
                                            <td>
                                                <code
                                                    style={{
                                                        background: "#f1f5f9",
                                                        padding: "4px 8px",
                                                        borderRadius: "4px",
                                                        color: "#0f172a",
                                                        fontSize: "13px",
                                                        fontWeight: 600
                                                    }}
                                                >
                                                    {col.item_name}
                                                </code>
                                            </td>
                                            <td>
                                                <span
                                                    style={{
                                                        fontSize: "12px",
                                                        fontWeight: 600,
                                                        color: "#2563eb",
                                                        background: "#eff6ff",
                                                        padding: "3px 8px",
                                                        borderRadius: "4px",
                                                        border: "1px solid #bfdbfe"
                                                    }}
                                                >
                                                    {col.type}
                                                </span>
                                            </td>
                                            <td className="cell-sub" style={{ fontSize: "13px" }}>
                                                Description metadata for field `{col.item_name}` within standard dataset schema structure.
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>

                    {/* Delete Confirmation Modal */}
                    {showDeleteModal && (
                        <div
                            style={{
                                position: "fixed",
                                inset: 0,
                                background: "rgba(15, 23, 42, 0.4)",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                zIndex: 1000
                            }}
                        >
                            <div className="card" style={{ width: "100%", maxWidth: "400px", padding: "24px" }}>
                                <h3 style={{ margin: "0 0 8px 0", fontSize: "18px", fontWeight: "600" }}>
                                    Delete Schema Template?
                                </h3>
                                <p className="cell-sub" style={{ fontSize: "14px", lineHeight: "1.5", marginBottom: "20px" }}>
                                    Are you sure you want to delete <strong>{template.name}</strong>? This action cannot be undone.
                                </p>
                                <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
                                    <button
                                        type="button"
                                        className="btn btn-outline"
                                        onClick={() => setShowDeleteModal(false)}
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="button"
                                        className="btn btn-primary"
                                        style={{ background: "var(--danger)", borderColor: "var(--danger)" }}
                                        onClick={handleDelete}
                                    >
                                        Delete Template
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}
                </main>
            </div>
        </div>
    );
}