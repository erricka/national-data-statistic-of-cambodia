import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";
import { MOCK_TEMPLATES, type ColumnItem } from "../../../data/mockTemplates";

const DATABASE_TYPES = [
    "VARCHAR(255)",
    "TEXT",
    "INTEGER",
    "BIGINT",
    "DECIMAL(10,2)",
    "BOOLEAN",
    "DATE",
    "TIMESTAMP",
    "JSONB"
];

export default function EditTemplatePage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    // Form & Alert States
    const [templateName, setTemplateName] = useState("");
    const [templateDescription, setTemplateDescription] = useState("");
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [columns, setColumns] = useState<ColumnItem[]>([]);

    useEffect(() => {
        // Find existing template by route ID or fallback to first item
        const found = MOCK_TEMPLATES.find((t) => t.id === id) || MOCK_TEMPLATES[0];

        if (found) {
            setTemplateName(found.name);
            setTemplateDescription(found.description);

            // Format sample columns with descriptions
            const loadedColumns: ColumnItem[] = found.sampleColumns.map((col, idx) => ({
                id: col.id || (idx + 1).toString(),
                item_name: col.item_name,
                item_description: col.item_description || `Field definition for ${col.item_name}`,
                type: col.type
            }));
            setColumns(loadedColumns);
        }

        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "template_management",
                title: "Edit Template",
                breadcrumb: ["Home", "Template Management", "Edit Template"]
            });
        }
    }, [id]);

    // Add a new empty row to the table
    const handleAddColumn = () => {
        setErrorMessage(null);
        const newRow: ColumnItem = {
            id: Date.now().toString(),
            item_name: "",
            item_description: "",
            type: "VARCHAR(255)"
        };
        setColumns([...columns, newRow]);
    };

    // Remove a row with inline error handling
    const handleDeleteColumn = (colId: string) => {
        if (columns.length === 1) {
            setErrorMessage("At least one column definition is required.");
            return;
        }
        setErrorMessage(null);
        setColumns(columns.filter((col) => col.id !== colId));
    };

    // Dynamic row updates
    const handleColumnChange = (colId: string, field: keyof ColumnItem, value: string) => {
        setColumns((prev) =>
            prev.map((col) => (col.id === colId ? { ...col, [field]: value } : col))
        );
    };

    // Save changes submit handler
    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!templateName.trim()) {
            setErrorMessage("Please enter a template name.");
            return;
        }

        const payload = {
            id,
            name: templateName,
            description: templateDescription,
            schema: columns
        };

        console.log("Updated Template Payload:", payload);
        navigate(`/admin/template/${id}`);
    };

    return (
        <div className="app">
            <Sidebar active="template_management" />

            <div className="main">
                <Topbar pageTitle="Edit Template" />

                <main className="content">
                    <form onSubmit={handleSubmit}>
                        <button
                            type="button"
                            className="btn btn-outline btn-sm"
                            onClick={() => navigate("/admin/template-management")}
                            style={{ padding: "6px 10px", marginBottom: "16px" }}
                        >
                            <Icon name="arrow-left" /> Back
                        </button>
                        {/* Page Header & Actions */}
                        <div className="page-head">
                            <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>

                                <div>
                                    <h1 style={{ margin: 0 }}>Edit Schema Template</h1>
                                    <p className="cell-sub" style={{ marginTop: "4px" }}>
                                        Modify structural field types, column names, or descriptions.
                                    </p>
                                </div>
                            </div>

                            <div className="actions" style={{ display: "flex", gap: "12px" }}>
                                <button
                                    type="button"
                                    className="btn btn-outline"
                                    onClick={() => navigate(`/admin/template/${id}`)}
                                >
                                    Cancel
                                </button>
                                <button type="submit" className="btn btn-primary">
                                    <Icon name="check" /> Save Changes
                                </button>
                            </div>
                        </div>

                        {/* Styled Error Alert */}
                        {errorMessage && (
                            <div
                                style={{
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "space-between",
                                    padding: "12px 16px",
                                    marginBottom: "20px",
                                    borderRadius: "8px",
                                    background: "#fef2f2",
                                    border: "1px solid #fecaca",
                                    color: "#991b1b",
                                    fontSize: "14px",
                                    fontWeight: "500"
                                }}
                            >
                                <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                                    <span style={{ color: "#ef4444", display: "flex", alignItems: "center" }}>
                                        <Icon name="alert-circle" />
                                    </span>
                                    <span>{errorMessage}</span>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => setErrorMessage(null)}
                                    style={{
                                        background: "transparent",
                                        border: "none",
                                        color: "#991b1b",
                                        cursor: "pointer",
                                        padding: "4px",
                                        display: "flex",
                                        alignItems: "center"
                                    }}
                                >
                                    <Icon name="x" />
                                </button>
                            </div>
                        )}

                        {/* Template Details Card */}
                        <div className="card" style={{ padding: "24px", marginBottom: "24px" }}>
                            <h2 style={{ fontSize: "16px", fontWeight: "600", color: "var(--text-main)", marginBottom: "16px", marginTop: 0 }}>
                                Template Details
                            </h2>

                            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
                                <div>
                                    <label className="cell-sub" style={{ display: "block", fontWeight: "600", marginBottom: "6px" }}>
                                        Template Name <span style={{ color: "var(--danger)" }}>*</span>
                                    </label>
                                    <input
                                        type="text"
                                        className="field"
                                        placeholder="Template Name"
                                        value={templateName}
                                        onChange={(e) => {
                                            setTemplateName(e.target.value);
                                            if (errorMessage) setErrorMessage(null);
                                        }}
                                        style={{ width: "100%", padding: "10px 14px" }}
                                    />
                                </div>

                                <div>
                                    <label className="cell-sub" style={{ display: "block", fontWeight: "600", marginBottom: "6px" }}>
                                        Description
                                    </label>
                                    <input
                                        type="text"
                                        className="field"
                                        placeholder="Description..."
                                        value={templateDescription}
                                        onChange={(e) => setTemplateDescription(e.target.value)}
                                        style={{ width: "100%", padding: "10px 14px" }}
                                    />
                                </div>
                            </div>
                        </div>

                        {/* Column Schema Builder Card */}
                        <div className="card" style={{ padding: "24px" }}>
                            <div className="toolbar between" style={{ padding: 0, marginBottom: "20px", borderBottom: "none" }}>
                                <div>
                                    <h2 style={{ fontSize: "16px", fontWeight: "600", color: "var(--text-main)", margin: 0 }}>
                                        Column Definitions
                                    </h2>
                                    <p className="cell-sub" style={{ marginTop: "2px", margin: 0 }}>
                                        Configure fields and database data types for this template.
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    className="btn btn-outline"
                                    onClick={handleAddColumn}
                                    style={{ color: "var(--primary)", borderColor: "var(--primary)" }}
                                >
                                    <Icon name="plus" /> Add Item
                                </button>
                            </div>

                            <div className="table-wrap">
                                <table className="tbl">
                                    <thead>
                                        <tr>
                                            <th style={{ width: "25%" }}>Item Name</th>
                                            <th style={{ width: "45%" }}>Item Description</th>
                                            <th style={{ width: "20%" }}>Type</th>
                                            <th style={{ width: "10%", textAlign: "center" }}>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {columns.map((col) => (
                                            <tr key={col.id}>
                                                {/* Column Item Name */}
                                                <td>
                                                    <input
                                                        type="text"
                                                        className="field"
                                                        placeholder="column_name"
                                                        value={col.item_name}
                                                        onChange={(e) => handleColumnChange(col.id, "item_name", e.target.value)}
                                                        style={{ width: "100%", fontFamily: "monospace", fontSize: "13px" }}
                                                    />
                                                </td>

                                                {/* Column Description */}
                                                <td>
                                                    <input
                                                        type="text"
                                                        className="field"
                                                        placeholder="Describe field usage..."
                                                        value={col.item_description || ""}
                                                        onChange={(e) => handleColumnChange(col.id, "item_description", e.target.value)}
                                                        style={{ width: "100%", fontSize: "13px" }}
                                                    />
                                                </td>

                                                {/* Data Type Select */}
                                                <td>
                                                    <select
                                                        className="field"
                                                        value={col.type}
                                                        onChange={(e) => handleColumnChange(col.id, "type", e.target.value)}
                                                        style={{ width: "100%", fontSize: "13px", background: "#ffffff" }}
                                                    >
                                                        {DATABASE_TYPES.map((type) => (
                                                            <option key={type} value={type}>
                                                                {type}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </td>

                                                {/* Delete Row Action */}
                                                <td style={{ textAlign: "center" }}>
                                                    <button
                                                        type="button"
                                                        className="icon-btn"
                                                        title="Delete Row"
                                                        onClick={() => handleDeleteColumn(col.id)}
                                                        style={{ color: "var(--danger)" }}
                                                    >
                                                        <Icon name="trash" />
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </form>
                </main>
            </div>
        </div>
    );
}