import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";

interface ColumnItem {
    id: string;
    item_name: string;
    item_description: string;
    type: string;
    data_length: string; // Length / Precision / Scale parameter
}

// Data types list
const DATABASE_TYPES = [
    "VARCHAR",
    "CHAR",
    "DECIMAL",
    "TEXT",
    "INTEGER",
    "BIGINT",
    "BOOLEAN",
    "DATE",
    "TIMESTAMP",
    "JSONB"
];

export default function CreateTemplatePage() {
    const navigate = useNavigate();

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "template_management",
                title: "Create Template",
                breadcrumb: ["Home", "Template Management", "Create Template"]
            });
        }
    }, []);

    // Form & Alert States
    const [templateName, setTemplateName] = useState("");
    const [templateDescription, setTemplateDescription] = useState("");
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    // Initial columns list
    const [columns, setColumns] = useState<ColumnItem[]>([
        { id: "1", item_name: "id", item_description: "", type: "BIGINT", data_length: "" },
        { id: "2", item_name: "title", item_description: "", type: "VARCHAR", data_length: "" }
    ]);

    // Add a new empty row to the table
    const handleAddColumn = () => {
        setErrorMessage(null); // Clear error if any
        const newRow: ColumnItem = {
            id: Date.now().toString(),
            item_name: "",
            item_description: "",
            type: "VARCHAR",
            data_length: ""
        };
        setColumns([...columns, newRow]);
    };

    // Remove a row by ID
    const handleDeleteColumn = (id: string) => {
        if (columns.length === 1) {
            setErrorMessage("At least one column definition is required.");
            return;
        }
        setErrorMessage(null);
        setColumns(columns.filter((col) => col.id !== id));
    };

    // Update column row values dynamically
    const handleColumnChange = (id: string, field: keyof ColumnItem, value: string) => {
        setColumns((prev) =>
            prev.map((col) => (col.id === id ? { ...col, [field]: value } : col))
        );
    };

    // Submit / Save handler
    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!templateName.trim()) {
            setErrorMessage("Please enter a template name.");
            return;
        }

        // Validate column item names
        for (let i = 0; i < columns.length; i++) {
            if (!columns[i].item_name.trim()) {
                setErrorMessage(`Item Name is required for row #${i + 1}.`);
                return;
            }
        }

        // Combine type + data_length for the final payload
        const formattedColumns = columns.map((col) => {
            const hasLength = col.data_length.trim().length > 0;
            const fullType = hasLength ? `${col.type}(${col.data_length.trim()})` : col.type;

            return {
                id: col.id,
                item_name: col.item_name,
                item_description: col.item_description,
                type: fullType
            };
        });

        const payload = {
            name: templateName,
            description: templateDescription,
            schema: formattedColumns
        };

        console.log("Creating Schema Template:", payload);
        navigate("/admin/template-management");
    };

    return (
        <div className="app">
            <Sidebar active="template_management" />

            <div className="main">
                <Topbar pageTitle="Create Template" />

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
                            <div>
                                <h1>Create Schema Template</h1>
                                <p className="cell-sub" style={{ marginTop: "4px" }}>
                                    Define dataset column structure, field descriptions, database data types, and custom lengths.
                                </p>
                            </div>
                            <div className="actions" style={{ display: "flex", gap: "12px" }}>
                                <button
                                    type="button"
                                    className="btn btn-ghost"
                                    onClick={() => navigate("/admin/template-management")}
                                >
                                    Cancel
                                </button>
                                <button type="submit" className="btn btn-primary">
                                    <Icon name="check" /> Create Template
                                </button>
                            </div>
                        </div>

                        {/* Custom Alert Banner */}
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

                        {/* Section 1: Template Metadata */}
                        <div className="card" style={{ padding: "24px", marginBottom: "24px" }}>
                            <h2 style={{ fontSize: "16px", fontWeight: "600", color: "var(--text-main)", marginBottom: "16px", marginTop: 0 }}>
                                Template Information
                            </h2>

                            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
                                <div>
                                    <label className="cell-sub" style={{ display: "block", fontWeight: "600", marginBottom: "6px" }}>
                                        Template Name <span style={{ color: "var(--danger)" }}>*</span>
                                    </label>
                                    <input
                                        type="text"
                                        className="field"
                                        placeholder="e.g., Primary School Enrollment Schema"
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
                                        Description (Optional)
                                    </label>
                                    <input
                                        type="text"
                                        className="field"
                                        placeholder="Briefly describe what this schema structure covers"
                                        value={templateDescription}
                                        onChange={(e) => setTemplateDescription(e.target.value)}
                                        style={{ width: "100%", padding: "10px 14px" }}
                                    />
                                </div>
                            </div>
                        </div>

                        {/* Section 2: Schema Table Builder */}
                        <div className="card" style={{ padding: "24px" }}>
                            <div className="toolbar between" style={{ padding: 0, marginBottom: "20px", borderBottom: "none" }}>
                                <div>
                                    <h2 style={{ fontSize: "16px", fontWeight: "600", color: "var(--text-main)", margin: 0 }}>
                                        Column Definitions
                                    </h2>
                                    <p className="cell-sub" style={{ marginTop: "2px", margin: 0 }}>
                                        Configure fields, data types, and custom lengths for this schema.
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    className="btn btn-primary"
                                    onClick={handleAddColumn}
                                >
                                    <Icon name="plus" /> Add Item
                                </button>
                            </div>

                            <div className="table-wrap">
                                <table className="tbl">
                                    <thead>
                                        <tr>
                                            <th style={{ width: "50px", textAlign: "center" }}>No.</th>
                                            <th style={{ width: "22%" }}>Item Name</th>
                                            <th style={{ width: "35%" }}>Item Description</th>
                                            <th style={{ width: "18%" }}>Type</th>
                                            <th style={{ width: "15%" }}>Data Length</th>
                                            <th style={{ width: "60px", textAlign: "center" }}>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {columns.map((col, index) => (
                                            <tr key={col.id}>
                                                {/* Row Number */}
                                                <td style={{ textAlign: "center", fontWeight: "600", color: "var(--text-sub, #64748b)" }}>
                                                    {index + 1}
                                                </td>

                                                {/* Item Name */}
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

                                                {/* Item Description */}
                                                <td>
                                                    <input
                                                        type="text"
                                                        className="field"
                                                        placeholder="Describe what this field contains..."
                                                        value={col.item_description}
                                                        onChange={(e) => handleColumnChange(col.id, "item_description", e.target.value)}
                                                        style={{ width: "100%", fontSize: "13px" }}
                                                    />
                                                </td>

                                                {/* Database Type */}
                                                <td>
                                                    <div style={{ position: "relative", width: "100%" }}>
                                                        <select
                                                            className="field"
                                                            value={col.type}
                                                            onChange={(e) => handleColumnChange(col.id, "type", e.target.value)}
                                                            style={{
                                                                width: "100%",
                                                                fontSize: "13px",
                                                                background: "#ffffff",
                                                                cursor: "pointer",
                                                                paddingRight: "24px",
                                                                WebkitAppearance: "none",
                                                                MozAppearance: "none",
                                                                appearance: "none"
                                                            }}
                                                        >
                                                            {DATABASE_TYPES.map((type) => (
                                                                <option key={type} value={type}>
                                                                    {type}
                                                                </option>
                                                            ))}
                                                        </select>

                                                        {/* Custom Icon Overlay */}
                                                        <div
                                                            style={{
                                                                position: "absolute",
                                                                right: "8px",
                                                                top: "50%",
                                                                transform: "translateY(-50%)",
                                                                pointerEvents: "none",
                                                                display: "flex",
                                                                alignItems: "center",
                                                                color: "#64748b"
                                                            }}
                                                        >
                                                            <Icon name="chevron-down" />
                                                        </div>
                                                    </div>
                                                </td>

                                                {/* Data Length (Enabled for All Types) */}
                                                <td>
                                                    <input
                                                        type="text"
                                                        className="field"
                                                        placeholder="Input Length"
                                                        value={col.data_length}
                                                        onChange={(e) => handleColumnChange(col.id, "data_length", e.target.value)}
                                                        style={{ width: "100%", fontSize: "13px", background: "#ffffff" }}
                                                    />
                                                </td>

                                                {/* Action */}
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