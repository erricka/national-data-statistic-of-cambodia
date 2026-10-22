import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import {
    getPermissionListForRegister,
    registerRole,
    type PermissionItem,
} from "../../../service/admin/role-service/addRoleService";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";

declare global {
    interface Window {
        NIS: any;
    }
}

const permissionDataExtractor = (res: any) => ({
    items: res?.data?.list || [],
    total: res?.data?.list?.length || 0,
    totalPages: 1,
});

export default function CreateRolePage() {
    const navigate = useNavigate();

    // Form inputs
    const [roleId, setRoleId] = useState("");
    const [roleName, setRoleName] = useState("");
    const [description, setDescription] = useState("");
    const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);

    // Submission & Feedback states
    const [isSaving, setIsSaving] = useState(false);
    const [formError, setFormError] = useState("");
    const [formSuccess, setFormSuccess] = useState("");

    // Permission guard fetch callback
    const fetcherFn = useCallback(() => {
        return getPermissionListForRegister("");
    }, []);

    const {
        data: availablePermissions,
        isLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage: loadPermissionsError,
        executeFetch: loadPermissions,
    } = usePermissionGuard<PermissionItem>(fetcherFn, permissionDataExtractor);

    useEffect(() => {
        loadPermissions();
    }, [loadPermissions]);

    const handleNameChange = (val: string) => {
        setRoleName(val);
        const generatedKey = val.toUpperCase().trim().replace(/[^A-Z0-9]+/g, "_");
        setRoleId(generatedKey);
    };

    const handleTogglePermission = (permissionId: string) => {
        if (isSaving || !!formSuccess) return;
        if (selectedPermissions.includes(permissionId)) {
            setSelectedPermissions(selectedPermissions.filter((id) => id !== permissionId));
        } else {
            setSelectedPermissions([...selectedPermissions, permissionId]);
        }
    };

    const handleSaveRole = async (e: React.FormEvent) => {
        e.preventDefault();
        setFormError("");
        setFormSuccess("");

        if (!roleName.trim()) {
            setFormError("Role Name is required.");
            return;
        }

        if (!roleId.trim()) {
            setFormError("Role ID is required.");
            return;
        }

        setIsSaving(true);

        try {
            const res = await registerRole({
                roleId: roleId.trim(),
                name: roleName.trim(),
                description: description.trim(),
                status: "01",
                permissionIds: selectedPermissions,
            });

            if (res.code === "000000") {
                setFormSuccess("Security role registered successfully! Redirecting...");

                // Wait 1.5 seconds to show success banner before navigating
                setTimeout(() => {
                    navigate("/admin/role-management");
                }, 1500);
            } else if (res.code === "403000" || res.code === "999999") {
                setFormError("You do not have authorization/permissions to perform role registration.");
                setIsSaving(false);
            } else {
                const msg = typeof res.message === "string"
                    ? res.message
                    : res.message?.messageBody || "Failed to register new role.";
                setFormError(msg);
                setIsSaving(false);
            }
        } catch (error: any) {
            console.error("Error registering role:", error);
            setFormError("An unexpected system error occurred while registering the role.");
            setIsSaving(false);
        }
    };

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "role_management",
                title: "Register New Role",
                breadcrumb: ["Home", "Governance", "Roles & Permissions", "Create"],
            });
        }
    }, []);

    return (
        <AdminPageContainer
            sidebarActive="role_management"
            pageTitle="Register Security Role"
            hasInitialLoaded={hasInitialLoaded}
            isLoading={isLoading}
            isForbidden={isForbidden}
        >
            <style>{`
                .role-workspace-grid {
                    display: grid;
                    grid-template-columns: minmax(280px, 360px) 1fr;
                    gap: 24px;
                    align-items: start;
                    width: 100%;
                }
                .permission-cards-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
                    gap: 12px;
                    width: 100%;
                }
                @media (max-width: 992px) {
                    .role-workspace-grid {
                        grid-template-columns: 1fr;
                    }
                }
            `}</style>

            <form onSubmit={handleSaveRole} style={{ width: "100%", boxSizing: "border-box" }}>
                {/* Page Header */}
                <div className="page-head" style={{ marginBottom: "24px" }}>
                    <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "flex-start" }}>
                        <button
                            type="button"
                            className="btn btn-outline btn-sm"
                            style={{ padding: "6px 10px" }}
                            onClick={() => navigate("/admin/role-management")}
                            disabled={isSaving}
                        >
                            <Icon name="arrow-left" /> Back
                        </button>

                        <div>
                            <h1 style={{ margin: "0 0 4px 0", fontSize: "clamp(20px, 4vw, 28px)" }}>Register New Role</h1>
                            <p className="cell-sub" style={{ margin: 0, color: "#64748b", fontSize: "14px" }}>
                                Establish operational access boundaries and active functional capabilities across core workspace domains.
                            </p>
                        </div>
                    </div>
                </div>

                {/* Success Banner */}
                {formSuccess && (
                    <div
                        style={{
                            backgroundColor: "#f0fdf4",
                            color: "#16a34a",
                            border: "1px solid #bbf7d0",
                            padding: "12px 16px",
                            borderRadius: "6px",
                            marginBottom: "20px",
                            fontSize: "14px",
                            fontWeight: 500,
                            display: "flex",
                            alignItems: "center",
                            gap: "8px"
                        }}
                    >
                        <Icon name="check-circle" /> {formSuccess}
                    </div>
                )}

                {/* Error Banner */}
                {(formError || loadPermissionsError) && (
                    <div
                        style={{
                            backgroundColor: "#fef2f2",
                            color: "var(--danger, #ef4444)",
                            border: "1px solid #fecaca",
                            padding: "12px 16px",
                            borderRadius: "6px",
                            marginBottom: "20px",
                            fontSize: "14px",
                        }}
                    >
                        {formError || loadPermissionsError}
                    </div>
                )}

                {/* Two-Column Responsive Workspace Layout */}
                <div className="role-workspace-grid">
                    {/* LEFT COLUMN: Role Basic Inputs */}
                    <div style={{ display: "flex", flexDirection: "column", gap: "24px", minWidth: 0 }}>
                        <div className="card card-pad" style={{ padding: "clamp(16px, 3vw, 20px)", boxSizing: "border-box" }}>
                            <h3 style={{ margin: "0 0 16px 0", fontSize: "13px", fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                                Role Name & Key
                            </h3>

                            <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                                <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                                    <label style={{ fontSize: "12px", fontWeight: 600, color: "#475569" }}>Role Name *</label>
                                    <input
                                        type="text"
                                        placeholder="e.g. System Reviewer"
                                        value={roleName}
                                        onChange={(e) => handleNameChange(e.target.value)}
                                        style={{ width: "100%", boxSizing: "border-box", padding: "8px 12px", border: "1px solid #cbd5e1", borderRadius: "6px", fontSize: "14px" }}
                                        required
                                        disabled={isSaving || !!formSuccess}
                                    />
                                </div>

                                <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                                    <label style={{ fontSize: "12px", fontWeight: 600, color: "#475569" }}>Role ID (System Key) *</label>
                                    <input
                                        type="text"
                                        placeholder="SYSTEM_KEY"
                                        value={roleId}
                                        onChange={(e) => setRoleId(e.target.value.toUpperCase().replace(/[^A-Z0-9_]+/g, ""))}
                                        style={{ width: "100%", boxSizing: "border-box", padding: "8px 12px", border: "1px solid #cbd5e1", borderRadius: "6px", fontSize: "13px", fontFamily: "monospace" }}
                                        required
                                        disabled={isSaving || !!formSuccess}
                                    />
                                </div>
                            </div>
                        </div>

                        <div className="card card-pad" style={{ padding: "clamp(16px, 3vw, 20px)", boxSizing: "border-box" }}>
                            <h3 style={{ margin: "0 0 12px 0", fontSize: "13px", fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                                Description (Remark)
                            </h3>
                            <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                                <textarea
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                    placeholder="Enter description or remarks for this role..."
                                    rows={4}
                                    style={{ width: "100%", boxSizing: "border-box", minHeight: "180px", padding: "10px 12px", border: "1px solid #cbd5e1", borderRadius: "6px", fontSize: "13px", lineHeight: "1.6", resize: "vertical" }}
                                    disabled={isSaving || !!formSuccess}
                                />
                            </div>
                        </div>
                    </div>

                    {/* RIGHT COLUMN: Permissions Matrix Selection */}
                    <div className="card" style={{ padding: 0, minWidth: 0, display: "flex", flexDirection: "column" }}>
                        <div className="toolbar" style={{ borderBottom: "1px solid var(--border, #e2e8f0)", padding: "16px clamp(16px, 3vw, 24px)" }}>
                            <div className="cell-main" style={{ fontSize: "15px", fontWeight: 600 }}>
                                Assign Permission to this role
                            </div>
                        </div>

                        <div style={{ padding: "clamp(16px, 3vw, 24px)", display: "flex", flexDirection: "column", gap: "16px", flex: 1 }}>
                            {availablePermissions.length > 0 ? (
                                <div className="permission-cards-grid">
                                    {availablePermissions.map((perm) => {
                                        const isChecked = selectedPermissions.includes(perm.permissionId);
                                        return (
                                            <div
                                                key={perm.permissionId}
                                                onClick={() => handleTogglePermission(perm.permissionId)}
                                                style={{
                                                    display: "flex",
                                                    alignItems: "center",
                                                    gap: "12px",
                                                    padding: "12px 14px",
                                                    backgroundColor: isChecked ? "#f0fdf4" : "#f8fafc",
                                                    border: isChecked ? "1px solid #10b981" : "1px solid #e2e8f0",
                                                    borderRadius: "6px",
                                                    cursor: isSaving || formSuccess ? "not-allowed" : "pointer",
                                                    transition: "all 0.15s ease",
                                                    boxSizing: "border-box",
                                                    opacity: isSaving || formSuccess ? 0.7 : 1
                                                }}
                                            >
                                                <span style={{ color: isChecked ? "#10b981" : "#94a3b8", display: "flex", flexShrink: 0 }}>
                                                    <Icon name={isChecked ? "check-circle" : "circle"} />
                                                </span>
                                                <div style={{ display: "flex", flexDirection: "column" }}>
                                                    <span style={{ fontSize: "13px", fontWeight: 500, color: isChecked ? "#0f172a" : "#475569", wordBreak: "break-word" }}>
                                                        {perm.description || perm.name || perm.permissionId}
                                                    </span>
                                                    <span style={{ fontSize: "11px", color: "#94a3b8", fontFamily: "monospace" }}>
                                                        {perm.permissionId}
                                                    </span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div style={{ padding: "16px", backgroundColor: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "6px", color: "#64748b", fontSize: "13px" }}>
                                    No permissions found in the system.
                                </div>
                            )}
                        </div>

                        {/* Action Bar Footer */}
                        <div
                            style={{
                                borderTop: "1px solid var(--border, #e2e8f0)",
                                padding: "16px clamp(16px, 3vw, 24px)",
                                backgroundColor: "#f8fafc",
                                display: "flex",
                                justifyContent: "space-between",
                                alignItems: "center",
                                flexWrap: "wrap",
                                gap: "12px",
                            }}
                        >
                            <span className="cell-sub" style={{ fontSize: "13px", color: "#64748b" }}>
                                Selected permissions: <strong>{selectedPermissions.length}</strong>
                            </span>

                            <div style={{ display: "flex", gap: "12px", flexWrap: "wrap" }}>
                                <button
                                    type="button"
                                    className="btn btn-ghost"
                                    onClick={() => navigate("/admin/role-management")}
                                    disabled={isSaving || !!formSuccess}
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
                                        cursor: isSaving || formSuccess ? "not-allowed" : "pointer",
                                        whiteSpace: "nowrap",
                                    }}
                                    disabled={isSaving || !!formSuccess}
                                >
                                    {isSaving ? "Creating Matrix..." : formSuccess ? "Registered!" : "Register System Profile"}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </form>
        </AdminPageContainer>
    );
}