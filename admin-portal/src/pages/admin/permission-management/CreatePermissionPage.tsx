import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";

// Define the structure of grouped system capabilities
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

// Master permission grid structure matching your sidebar taxonomy map
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
            { id: "dateset_edit", label: "Edit Datasets", description: "Create, edit, and archive data category structures." },
            { id: "dataset_export", label: "Export Raw Data", description: "Extract data structures into unstructured Excel/CSV sheets." }
        ]
    },

];

export default function CreatePermissionPage() {
    const navigate = useNavigate();

    // Mocking a static target role profile name for UI context
    const roleName = "Data Administrator";

    // State tracks selected capability ID checkbox strings
    const [selectedPermissions, setSelectedPermissions] = useState<string[]>([
        "category_manage", "dataset_view", "dataset_write" // Default initial states
    ]);
    const [isSaving, setIsSaving] = useState(false);

    // Checkbox toggling handler logic
    const handleCheckboxToggle = (permId: string) => {
        if (selectedPermissions.includes(permId)) {
            setSelectedPermissions(selectedPermissions.filter(id => id !== permId));
        } else {
            setSelectedPermissions([...selectedPermissions, permId]);
        }
    };

    // Global category macro handler toggles all child items simultaneously
    const handleToggleCategoryAll = (group: PermissionGroup) => {
        const itemIds = group.items.map(i => i.id);
        const hasAllSelected = itemIds.every(id => selectedPermissions.includes(id));

        if (hasAllSelected) {
            // Remove all items in this group
            setSelectedPermissions(selectedPermissions.filter(id => !itemIds.includes(id)));
        } else {
            // Merge items ensuring duplicates don't form
            const uniqueMerged = Array.from(new Set([...selectedPermissions, ...itemIds]));
            setSelectedPermissions(uniqueMerged);
        }
    };

    const handleFormSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setIsSaving(true);

        console.log(`Updating ${roleName} authorization matrices mapping:`, selectedPermissions);

        setTimeout(() => {
            setIsSaving(false);
            navigate("/admin/permission-management");
        }, 800);
    };

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "roles",
                title: `Configure Matrix: ${roleName}`,
                breadcrumb: ["Home", "Governance", "Roles Management", "Matrix Setup"]
            });
        }
    }, [roleName]);

    return (
        <div className="app">
            <Sidebar active="permission_management" />

            <div className="main">
                <Topbar pageTitle={`Authorization Grid: ${roleName}`} />

                <main className="content">
                    <form onSubmit={handleFormSubmit}>

                        {/* Page Title Context Header Block */}
                        <div className="page-head" style={{ marginBottom: "24px" }}>
                            <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "flex-start" }}>
                                <button
                                    type="button"
                                    className="btn btn-outline btn-sm"
                                    onClick={() => navigate("/admin/permission-management")}
                                    style={{ padding: "6px 10px" }}
                                >
                                    <Icon name="arrow-left" /> Back
                                </button>

                                <div>
                                    <h1 style={{ margin: "0 0 4px 0" }}>Configure Security Matrix</h1>
                                    <p className="cell-sub" style={{ margin: 0 }}>
                                        Check system capabilities below to anchor baseline clearance levels mapped for the <strong style={{ color: "#0f172a" }}>{roleName}</strong> identity profile.
                                    </p>
                                </div>
                            </div>
                        </div>

                        {/* Interactive Main Checkbox Table Matrix Area */}
                        <div className="card" style={{ padding: 0 }}>
                            <div className="toolbar" style={{ padding: "16px 24px", borderBottom: "1px solid var(--border)" }}>
                                <div className="cell-main" style={{ fontSize: "15px", fontWeight: 600 }}>
                                    Capability Privilege Matrix Assignments
                                </div>
                            </div>

                            <div style={{ padding: "24px", display: "flex", flexDirection: "column", gap: "32px" }}>
                                {SYSTEM_PERMISSIONS_MATRIX.map((group, groupIdx) => {
                                    const groupItemIds = group.items.map(i => i.id);
                                    const isGroupAllChecked = groupItemIds.every(id => selectedPermissions.includes(id));

                                    return (
                                        <div key={groupIdx} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>

                                            {/* Domain Block Category Header Row */}
                                            <div
                                                style={{
                                                    display: "flex",
                                                    justifyContent: "between",
                                                    alignItems: "center",
                                                    backgroundColor: "#f8fafc",
                                                    padding: "10px 16px",
                                                    borderRadius: "6px",
                                                    border: "1px solid #e2e8f0"
                                                }}
                                            >
                                                <div style={{ display: "flex", alignItems: "center", gap: "8px", fontWeight: 600, color: "#334155", fontSize: "14px" }}>
                                                    <Icon name={group.icon} /> {group.category}
                                                </div>
                                                <button
                                                    type="button"
                                                    onClick={() => handleToggleCategoryAll(group)}
                                                    style={{ background: "none", border: "none", color: "#2563eb", fontSize: "12px", fontWeight: 500, cursor: "pointer", marginLeft: "6px" }}
                                                >
                                                    {isGroupAllChecked ? "Deselect All Section" : "Select All Section"}
                                                </button>
                                            </div>

                                            {/* Sub-Items Checkbox Indented Grid Layout */}
                                            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", paddingLeft: "4px" }}>
                                                {group.items.map((item) => {
                                                    const isChecked = selectedPermissions.includes(item.id);
                                                    return (
                                                        <div
                                                            key={item.id}
                                                            onClick={() => handleCheckboxToggle(item.id)}
                                                            style={{
                                                                display: "flex",
                                                                alignItems: "start",
                                                                gap: "12px",
                                                                padding: "14px",
                                                                border: isChecked ? "1px solid #cbd5e1" : "1px solid #e2e8f0",
                                                                borderRadius: "6px",
                                                                backgroundColor: isChecked ? "#ffffff" : "#fbfcfd",
                                                                cursor: "pointer",
                                                                userSelect: "none"
                                                            }}
                                                        >
                                                            {/* Checkbox Input Visual Element */}
                                                            <div style={{ marginTop: "2px" }}>
                                                                <input
                                                                    type="checkbox"
                                                                    checked={isChecked}
                                                                    onChange={() => { }} // Controlled via parent element click wrapper
                                                                    style={{ width: "16px", height: "16px", cursor: "pointer" }}
                                                                />
                                                            </div>

                                                            {/* Text descriptions */}
                                                            <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                                                                <span style={{ fontSize: "13px", fontWeight: 600, color: isChecked ? "#0f172a" : "#475569" }}>
                                                                    {item.label}
                                                                </span>
                                                                <span className="cell-sub" style={{ fontSize: "12px", lineHeight: "1.4" }}>
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

                            {/* Sticky Save Controls Footer Bar */}
                            <div className="toolbar between" style={{ borderTop: "1px solid var(--border)", padding: "16px 24px", backgroundColor: "#f8fafc" }}>
                                <span className="cell-sub">Confirm modifications to apply permission shifts system-wide.</span>

                                <div style={{ display: "flex", gap: "12px" }}>
                                    <button
                                        type="button"
                                        className="btn btn-ghost"
                                        onClick={() => navigate("/admin/permission-management")}
                                        disabled={isSaving}
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="submit"
                                        className="btn btn-primary"
                                        style={{
                                            color: "#ffffff",
                                            padding: "8px 16px",
                                            borderRadius: "6px",
                                            fontWeight: 500,
                                            cursor: isSaving ? "not-allowed" : "pointer"
                                        }}
                                        disabled={isSaving}
                                    >
                                        {isSaving ? "Saving Profiles..." : "Apply Access Matrices"}
                                    </button>
                                </div>
                            </div>
                        </div>

                    </form>
                </main>
            </div>
        </div>
    );
}