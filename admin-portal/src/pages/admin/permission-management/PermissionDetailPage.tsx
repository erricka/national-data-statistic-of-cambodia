import { useEffect, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";

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

// Global baseline matrix structure matching your taxonomy blocks
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

export default function PermissionDetailPage() {
    const navigate = useNavigate();
    const location = useLocation();

    // Context record fallback safely passed from the main Registry table router stream
    const permData = location.state?.permData || {
        id: 4,
        permissionName: "Data Export",
        keyName: "dataset_raw_export",
        moduleGroup: "Data Layer",
        description: "Extract massive raw relational rows out into unstructured CSV/XLS spreadsheet files.",
        riskLevel: "Medium",
        createdAt: "Feb 15, 2026"
    };

    const [selectedPermissions] = useState<string[]>([
        "dataset_view", "dataset_write"
    ]);

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "permissions",
                title: `Capability Detail: ${permData.permissionName}`,
                breadcrumb: ["Home", "Governance", "Capabilities Matrix", "Detail"]
            });
        }
    }, [permData.permissionName]);

    return (
        <div className="app">
            <Sidebar active="permission_management" />

            <div className="main" style={{ width: "100%", overflowX: "hidden" }}>
                <Topbar pageTitle={`Capability: ${permData.permissionName}`} />

                <main className="content" style={{ padding: "clamp(12px, 3vw, 24px)", maxWidth: "100%", boxSizing: "border-box" }}>

                    {/* Embedded responsive style rules */}
                    <style>{`
                        .permission-detail-workspace-grid {
                            display: grid;
                            grid-template-columns: minmax(260px, 340px) 1fr;
                            gap: 24px;
                            align-items: start;
                            width: 100%;
                        }
                        .matrix-items-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
                            gap: 16px;
                            width: 100%;
                        }
                        @media (max-width: 992px) {
                            .permission-detail-workspace-grid {
                                grid-template-columns: 1fr;
                            }
                        }
                        @media (max-width: 576px) {
                            .matrix-items-grid {
                                grid-template-columns: 1fr;
                            }
                        }
                    `}</style>

                    {/* Page Head Context Block */}
                    <div className="page-head" style={{ marginBottom: "24px" }}>
                        <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "flex-start" }}>
                            <button
                                className="btn btn-outline btn-sm"
                                style={{ padding: "6px 10px" }}
                                onClick={() => navigate("/admin/permission-management")}
                            >
                                <Icon name="arrow-left" /> Back
                            </button>

                            <div>
                                <h1 style={{ margin: "0 0 4px 0", fontSize: "clamp(20px, 4vw, 28px)" }}>{permData.permissionName}</h1>
                                <p style={{ margin: 0, fontSize: "14px", color: "#64748b" }}>
                                    System Key Token: <code style={{ backgroundColor: "#f1f5f9", padding: "2px 6px", borderRadius: "4px", fontSize: "12px", fontFamily: "monospace", wordBreak: "break-all" }}>{permData.keyName}</code>
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Two-Column Responsive Split Workspace Layout */}
                    <div className="permission-detail-workspace-grid">

                        {/* LEFT COLUMN: Metadata Parameters */}
                        <div style={{ display: "flex", flexDirection: "column", gap: "24px", minWidth: 0 }}>
                            <div className="card card-pad" style={{ backgroundColor: "#ffffff", borderRadius: "8px", border: "1px solid #e2e8f0", padding: "clamp(16px, 3vw, 20px)", boxSizing: "border-box" }}>
                                <h3 style={{ margin: "0 0 16px 0", fontSize: "12px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                                    Privilege Attributes
                                </h3>

                                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                                    <div>
                                        <div className="cell-sub" style={{ marginBottom: "4px", fontSize: "13px", color: "#64748b" }}>Domain Category</div>
                                        <div className="cell-main" style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "14px", fontWeight: 500, color: "#334155" }}>
                                            <Icon name="category" /> {permData.moduleGroup}
                                        </div>
                                    </div>

                                    <div>
                                        <div className="cell-sub" style={{ marginBottom: "4px", fontSize: "13px", color: "#64748b" }}>Instantiated On</div>
                                        <div className="cell-main" style={{ fontSize: "13px", color: "#475569" }}>{permData.createdAt}</div>
                                    </div>
                                </div>
                            </div>

                            <div className="card card-pad" style={{ backgroundColor: "#ffffff", borderRadius: "8px", border: "1px solid #e2e8f0", padding: "clamp(16px, 3vw, 20px)", boxSizing: "border-box" }}>
                                <h3 style={{ margin: "0 0 12px 0", fontSize: "12px", fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                                    Functional Scope Description
                                </h3>
                                <p style={{ margin: 0, fontSize: "13px", lineHeight: "1.6", color: "#475569" }}>
                                    {permData.description}
                                </p>
                            </div>
                        </div>

                        {/* RIGHT COLUMN: Matrix Capability View */}
                        <div className="card" style={{ padding: 0, backgroundColor: "#ffffff", borderRadius: "8px", border: "1px solid #e2e8f0", minWidth: 0 }}>
                            <div className="toolbar" style={{ padding: "16px clamp(16px, 3vw, 24px)", borderBottom: "1px solid var(--border, #e2e8f0)" }}>
                                <div className="cell-main" style={{ fontSize: "15px", fontWeight: 600 }}>
                                    Capability Privilege Matrix Assignments
                                </div>
                            </div>

                            <div style={{ padding: "clamp(16px, 3vw, 24px)", display: "flex", flexDirection: "column", gap: "32px" }}>
                                {SYSTEM_PERMISSIONS_MATRIX.map((group, groupIdx) => {

                                    return (
                                        <div key={groupIdx} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>

                                            <div
                                                style={{
                                                    display: "flex",
                                                    justifyContent: "space-between",
                                                    alignItems: "center",
                                                    backgroundColor: "#f8fafc",
                                                    padding: "12px 16px",
                                                    borderRadius: "6px",
                                                    border: "1px solid #e2e8f0"
                                                }}
                                            >
                                                <div style={{ display: "flex", alignItems: "center", gap: "8px", fontWeight: 600, color: "#334155", fontSize: "14px" }}>
                                                    <Icon name={group.icon} /> {group.category}
                                                </div>

                                            </div>

                                            <div className="matrix-items-grid">
                                                {group.items.map((item) => {
                                                    const isChecked = selectedPermissions.includes(item.id);
                                                    return (
                                                        <div
                                                            key={item.id}
                                                            style={{
                                                                display: "flex",
                                                                alignItems: "start",
                                                                gap: "12px",
                                                                padding: "14px",
                                                                border: isChecked ? "1px solid #cbd5e1" : "1px solid #e2e8f0",
                                                                borderRadius: "6px",
                                                                backgroundColor: isChecked ? "#ffffff" : "#fbfcfd",
                                                                cursor: "pointer",
                                                                userSelect: "none",
                                                                boxSizing: "border-box"
                                                            }}
                                                        >
                                                            <div style={{ marginTop: "2px", flexShrink: 0 }}>
                                                                <input
                                                                    type="checkbox"
                                                                    checked={isChecked}
                                                                    readOnly
                                                                    style={{ width: "16px", height: "16px", cursor: "pointer" }}
                                                                />
                                                            </div>

                                                            <div style={{ display: "flex", flexDirection: "column", gap: "2px", minWidth: 0 }}>
                                                                <span style={{ fontSize: "13px", fontWeight: 600, color: isChecked ? "#0f172a" : "#475569", wordBreak: "break-word" }}>
                                                                    {item.label}
                                                                </span>
                                                                <span className="cell-sub" style={{ fontSize: "12px", lineHeight: "1.4", color: "#64748b" }}>
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

                        </div>

                    </div>
                </main>
            </div>
        </div>
    );
}