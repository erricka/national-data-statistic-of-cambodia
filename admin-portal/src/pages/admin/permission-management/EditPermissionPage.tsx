import { useEffect, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";

// Structure definitions for the capability privilege matrix mapping
interface PermissionItem {
    id: string;
    label: string;
    description: string;
}

interface PermissionGroup {
    category: string;
    icon: string;
    items: PermissionItem[];
}

// Master permission grid matching your system's taxonomy block configurations
const SYSTEM_PERMISSIONS_MATRIX: PermissionGroup[] = [
    {
        category: "Category Management",
        icon: "category",
        items: [
            { id: "category_view", label: "View Category", description: "Read and look up records across all localized indices." },
            { id: "category_write", label: "Write Category", description: "Upload, modify records, and commit data alterations." },
            { id: "category_edit", label: "Edit Category", description: "Create, edit, and archive data category structures." },
            { id: "category_export", label: "Export Raw Data", description: "Extract data structures into unstructured Excel/CSV sheets." }
        ]
    },
    {
        category: "Data Management",
        icon: "database",
        items: [
            { id: "dataset_view", label: "View Datasets", description: "Read and look up records across all localized indices." },
            { id: "dataset_write", label: "Write Datasets", description: "Upload, modify records, and commit data alterations." },
            { id: "dataset_edit", label: "Edit Datasets", description: "Create, edit, and archive data category structures." },
            { id: "dataset_export", label: "Export Raw Data", description: "Extract data structures into unstructured Excel/CSV sheets." }
        ]
    },
];

export default function EditPermissionPage() {
    const navigate = useNavigate();
    const location = useLocation();

    // Context record fallback safely mapped down from the main list view row router stream
    const permData = location.state?.permData || {
        id: 4,
        permissionName: "Data Export",
        keyName: "dataset_raw_export",
        moduleGroup: "Data Layer",
        description: "Extract massive raw relational rows out into unstructured CSV/XLS spreadsheet files.",
        riskLevel: "Medium",
        createdAt: "Feb 15, 2026"
    };

    // Editable Left Column Local State Management Fields
    const [permissionName, setPermissionName] = useState(permData.permissionName);
    const [moduleGroup] = useState(permData.moduleGroup);
    const [riskLevel] = useState<"High" | "Medium" | "Low">(permData.riskLevel);
    const [description, setDescription] = useState(permData.description);

    // Right Column State tracks active matrix grid checked selections
    const [selectedPermissions, setSelectedPermissions] = useState<string[]>([
        "dataset_view", "dataset_write" // Default initial matrix items checked mapping
    ]);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "permissions",
                title: `Modify Privilege Context: ${permData.permissionName}`,
                breadcrumb: ["Home", "Governance", "Capabilities Matrix", "Edit Definition"]
            });
        }
    }, [permData.permissionName]);

    // Checkbox toggling handler logic
    const handleCheckboxToggle = (permId: string) => {
        if (selectedPermissions.includes(permId)) {
            setSelectedPermissions(selectedPermissions.filter(item => item !== permId));
        } else {
            setSelectedPermissions([...selectedPermissions, permId]);
        }
    };

    // Global category macro cluster toggle
    const handleToggleCategoryAll = (group: PermissionGroup) => {
        const itemIds = group.items.map(i => i.id);
        const hasAllSelected = itemIds.every(itemId => selectedPermissions.includes(itemId));

        if (hasAllSelected) {
            setSelectedPermissions(selectedPermissions.filter(itemId => !itemIds.includes(itemId)));
        } else {
            const uniqueMerged = Array.from(new Set([...selectedPermissions, ...itemIds]));
            setSelectedPermissions(uniqueMerged);
        }
    };

    const handleFormSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setIsSaving(true);

        const updatedPayload = {
            id: permData.id,
            permissionName,
            keyName: permData.keyName, // Left system token key completely immutable to protect backend code mappings
            moduleGroup,
            riskLevel,
            description,
            matrixAssignments: selectedPermissions
        };

        console.log("Committing customized parameter configurations updates:", updatedPayload);

        setTimeout(() => {
            setIsSaving(false);
            navigate("/admin/permission-management");
        }, 800);
    };

    const isGroupAllChecked = (group: PermissionGroup) => group.items.map(i => i.id).every(id => selectedPermissions.includes(id));

    return (
        <div className="app">
            <Sidebar active="permission_management" />

            <div className="main" style={{ width: "100%", overflowX: "hidden" }}>
                <Topbar pageTitle={`Edit Capability Definition`} />

                <main className="content" style={{ padding: "clamp(12px, 3vw, 24px)", maxWidth: "100%", boxSizing: "border-box" }}>

                    {/* Embedded responsive style rules */}
                    <style>{`
                        .edit-permission-workspace-grid {
                            display: grid;
                            grid-template-columns: minmax(280px, 360px) 1fr;
                            gap: 24px;
                            align-items: start;
                            width: 100%;
                        }
                        .edit-matrix-items-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
                            gap: 16px;
                            width: 100%;
                        }
                        @media (max-width: 992px) {
                            .edit-permission-workspace-grid {
                                grid-template-columns: 1fr;
                            }
                        }
                        @media (max-width: 576px) {
                            .edit-matrix-items-grid {
                                grid-template-columns: 1fr;
                            }
                        }
                    `}</style>

                    <form onSubmit={handleFormSubmit} style={{ width: "100%", boxSizing: "border-box" }}>

                        {/* Page Head Header Block */}
                        <div className="page-head" style={{ marginBottom: "24px" }}>
                            <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "flex-start" }}>
                                <button
                                    type="button"
                                    className="btn btn-outline btn-sm"
                                    style={{ padding: "6px 10px" }}
                                    onClick={() => navigate("/admin/permission-management")}
                                >
                                    <Icon name="arrow-left" /> Back
                                </button>

                                <div>
                                    <h1 style={{ margin: "0 0 4px 0", fontSize: "clamp(20px, 4vw, 28px)" }}>Modify System Privilege Definition</h1>
                                    <p className="cell-sub" style={{ margin: 0, fontSize: "14px", color: "#64748b" }}>
                                        Updating parameters and matrix capabilities nodes configuration mapping properties for token <code style={{ backgroundColor: "#f1f5f9", padding: "2px 6px", borderRadius: "4px", fontSize: "12px", fontFamily: "monospace", color: "#0f172a", wordBreak: "break-all" }}>{permData.keyName}</code>.
                                    </p>
                                </div>
                            </div>
                        </div>

                        {/* Split Workspace Layout */}
                        <div className="edit-permission-workspace-grid">

                            {/* LEFT COLUMN: Input Control Fields Parameters Form */}
                            <div style={{ display: "flex", flexDirection: "column", gap: "24px", minWidth: 0 }}>
                                <div className="card card-pad" style={{ backgroundColor: "#ffffff", borderRadius: "8px", border: "1px solid #e2e8f0", padding: "clamp(16px, 3vw, 20px)", boxSizing: "border-box" }}>
                                    <h3 style={{ margin: "0 0 20px 0", fontSize: "12px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                                        Privilege Metadata
                                    </h3>

                                    <div style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
                                        <label className="field-group" style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                                            <span style={{ fontSize: "12px", fontWeight: 600, color: "#475569" }}>Registry Alias Name</span>
                                            <input
                                                type="text"
                                                className="form-control"
                                                style={{ width: "100%", boxSizing: "border-box", padding: "8px 12px", borderRadius: "6px", border: "1px solid #cbd5e1", fontSize: "14px" }}
                                                value={permissionName}
                                                onChange={(e) => setPermissionName(e.target.value)}
                                                required
                                            />
                                        </label>
                                    </div>
                                </div>

                                <div className="card card-pad" style={{ backgroundColor: "#ffffff", borderRadius: "8px", border: "1px solid #e2e8f0", padding: "clamp(16px, 3vw, 20px)", boxSizing: "border-box" }}>
                                    <h3 style={{ margin: "0 0 12px 0", fontSize: "12px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                                        Functional Definition Scope
                                    </h3>
                                    <textarea
                                        rows={4}
                                        style={{ width: "100%", boxSizing: "border-box", padding: "10px 12px", borderRadius: "6px", border: "1px solid #cbd5e1", fontSize: "13px", lineHeight: "1.5", resize: "vertical", fontFamily: "inherit", minHeight: "120px" }}
                                        value={description}
                                        onChange={(e) => setDescription(e.target.value)}
                                        placeholder="Add description constraints parameters details..."
                                        required
                                    />
                                </div>
                            </div>

                            {/* RIGHT COLUMN: Replicated Layout Matrix */}
                            <div style={{ display: "flex", flexDirection: "column", gap: "16px", minWidth: 0 }}>

                                {/* 1. Standalone Full-Width Header Bar Container */}
                                <div
                                    style={{
                                        display: "flex",
                                        alignItems: "center",
                                        backgroundColor: "#ffffff",
                                        padding: "16px clamp(16px, 3vw, 24px)",
                                        borderRadius: "8px",
                                        border: "1px solid #e2e8f0",
                                        boxShadow: "0 1px 2px 0 rgba(0, 0, 0, 0.05)"
                                    }}
                                >
                                    <div style={{ display: "flex", alignItems: "center", gap: "10px", fontWeight: 600, color: "#1e293b", fontSize: "15px" }}>
                                        <Icon name="settings" /> Configuration Capability Bindings Matrix
                                    </div>
                                </div>

                                {/* 2. Active Interleaved Checkbox Rows Grid Elements */}
                                <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
                                    {SYSTEM_PERMISSIONS_MATRIX.map((group, groupIdx) => {
                                        const isAllChecked = isGroupAllChecked(group);

                                        return (
                                            <div key={groupIdx} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                                                {/* Domain Block Category Header Row */}
                                                <div
                                                    style={{
                                                        display: "flex",
                                                        justifyContent: "space-between",
                                                        alignItems: "center",
                                                        backgroundColor: "#f8fafc",
                                                        padding: "12px 16px",
                                                        borderRadius: "6px",
                                                        border: "1px solid #e2e8f0",
                                                        flexWrap: "wrap",
                                                        gap: "8px"
                                                    }}
                                                >
                                                    <div style={{ display: "flex", alignItems: "center", gap: "8px", fontWeight: 600, color: "#334155", fontSize: "14px" }}>
                                                        <Icon name={group.icon} /> {group.category}
                                                    </div>
                                                    <button
                                                        type="button"
                                                        onClick={() => handleToggleCategoryAll(group)}
                                                        style={{ background: "none", border: "none", color: "#2563eb", fontSize: "12px", fontWeight: 500, cursor: "pointer", padding: 0 }}
                                                    >
                                                        {isAllChecked ? "Deselect All Section" : "Select All Section"}
                                                    </button>
                                                </div>

                                                {/* Sub-Items Checkbox Cards Grid */}
                                                <div className="edit-matrix-items-grid">
                                                    {group.items.map((item) => {
                                                        const isChecked = selectedPermissions.includes(item.id);
                                                        return (
                                                            <div
                                                                key={item.id}
                                                                onClick={() => handleCheckboxToggle(item.id)}
                                                                style={{
                                                                    display: "flex",
                                                                    alignItems: "start",
                                                                    gap: "14px",
                                                                    padding: "16px clamp(14px, 2.5vw, 20px)",
                                                                    border: isChecked ? "1px solid #cbd5e1" : "1px solid #e2e8f0",
                                                                    borderRadius: "8px",
                                                                    backgroundColor: isChecked ? "#ffffff" : "#fbfcfd",
                                                                    boxShadow: "0 1px 2px 0 rgba(0, 0, 0, 0.05)",
                                                                    cursor: "pointer",
                                                                    userSelect: "none",
                                                                    boxSizing: "border-box"
                                                                }}
                                                            >
                                                                {/* Checkbox Element Wrapper */}
                                                                <div style={{ marginTop: "2px", display: "flex", alignItems: "center", flexShrink: 0 }}>
                                                                    <input
                                                                        type="checkbox"
                                                                        checked={isChecked}
                                                                        readOnly
                                                                        style={{ width: "16px", height: "16px", cursor: "pointer" }}
                                                                    />
                                                                </div>

                                                                {/* Description Metadata Rows */}
                                                                <div style={{ display: "flex", flexDirection: "column", gap: "4px", minWidth: 0 }}>
                                                                    <span style={{ fontSize: "14px", fontWeight: 600, color: isChecked ? "#0f172a" : "#475569", wordBreak: "break-word" }}>
                                                                        {item.label}
                                                                    </span>
                                                                    <span className="cell-sub" style={{ fontSize: "12px", lineHeight: "1.5", color: "#64748b" }}>
                                                                        {item.description}
                                                                    </span>
                                                                </div>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>

                                {/* 3. Sticky Action Confirmation Footer Bar */}
                                <div
                                    style={{
                                        display: "flex",
                                        justifyContent: "space-between",
                                        alignItems: "center",
                                        border: "1px solid #e2e8f0",
                                        borderRadius: "8px",
                                        padding: "16px clamp(16px, 3vw, 24px)",
                                        backgroundColor: "#f8fafc",
                                        marginTop: "8px",
                                        flexWrap: "wrap",
                                        gap: "12px"
                                    }}
                                >
                                    <span className="cell-sub" style={{ fontSize: "13px", color: "#64748b" }}>
                                        Review security risk alignments fully before applying code mutations system-wide.
                                    </span>

                                    <div style={{ display: "flex", gap: "12px", flexWrap: "wrap" }}>
                                        <button
                                            type="button"
                                            className="btn btn-ghost"
                                            disabled={isSaving}
                                            onClick={() => navigate("/admin/permission-management")}
                                        >
                                            Cancel
                                        </button>
                                        <button
                                            type="submit"
                                            className="btn btn-primary"
                                            disabled={isSaving}
                                            style={{
                                                color: "#ffffff",
                                                padding: "8px 16px",
                                                borderRadius: "6px",
                                                fontWeight: 500,
                                                cursor: isSaving ? "not-allowed" : "pointer",
                                                whiteSpace: "nowrap"
                                            }}
                                        >
                                            {isSaving ? "Saving Profiles..." : "Save Matrix Configurations"}
                                        </button>
                                    </div>
                                </div>
                            </div>

                        </div>
                    </form>
                </main>
            </div>
        </div>
    );
}