import { useEffect, useCallback } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import {
    getRoleDetail,
    type RoleDetailData,
    type RolePermissionItem
} from "../../../service/admin/role-service/roleDetailService";
import { usePermissionGuard } from "../../../hooks/usePermissionGuard";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";

declare global {
    interface Window {
        NIS: any;
    }
}

const roleDetailDataExtractor = (res: any) => ({
    items: res?.data ? [res.data] : [],
    total: res?.data ? 1 : 0,
    totalPages: 1,
});

export default function RoleDetailPage() {
    const navigate = useNavigate();
    const location = useLocation();

    const rawRoleData = location.state?.roleData || {};
    const targetRoleId = rawRoleData.roleId || rawRoleData.keyName || rawRoleData.id || "ADMIN";

    const fetcherFn = useCallback(() => {
        return getRoleDetail({ roleId: targetRoleId });
    }, [targetRoleId]);

    const {
        data: roleDetailList,
        isLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage,
        executeFetch: loadRoleDetail,
    } = usePermissionGuard<RoleDetailData>(fetcherFn, roleDetailDataExtractor);

    useEffect(() => {
        loadRoleDetail();
    }, [loadRoleDetail]);

    const roleData = roleDetailList.length > 0 ? roleDetailList[0] : null;
    const roleName = roleData?.roleName || rawRoleData.name || rawRoleData.roleName || targetRoleId;
    const roleId = roleData?.roleId || targetRoleId;

    // Extract permissions list from API payload
    const permissionsList: RolePermissionItem[] = roleData?.permissions || [];

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "role_management",
                title: `Role Detail: ${roleName}`,
                breadcrumb: ["Home", "Governance", "Roles & Permissions", "Detail"],
            });
        }
    }, [roleName]);

    return (
        <AdminPageContainer
            sidebarActive="role_management"
            pageTitle={`Role: ${roleName}`}
            hasInitialLoaded={hasInitialLoaded}
            isLoading={isLoading}
            isForbidden={isForbidden}
        >
            <style>{`
                .permission-detail-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
                    gap: 16px;
                    width: 100%;
                }
            `}</style>

            {/* Header */}
            <div className="page-head" style={{ marginBottom: "24px" }}>
                <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "flex-start" }}>
                    <button
                        className="btn btn-outline btn-sm"
                        style={{ padding: "6px 10px" }}
                        onClick={() => navigate("/admin/role-management")}
                    >
                        <Icon name="arrow-left" /> Back
                    </button>

                    <div>
                        <h1 style={{ margin: "0 0 4px 0", fontSize: "clamp(20px, 4vw, 28px)" }}>{roleName}</h1>
                        <p style={{ margin: 0, fontSize: "14px", color: "#64748b" }}>
                            System Key:{" "}
                            <code style={{ backgroundColor: "#f1f5f9", padding: "2px 6px", borderRadius: "4px", fontSize: "12px", wordBreak: "break-all" }}>
                                {roleId}
                            </code>
                        </p>
                    </div>
                </div>
            </div>

            {/* Error Message */}
            {errorMessage && (
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
                    {errorMessage}
                </div>
            )}

            {/* Permissions List Card */}
            <div className="card" style={{ padding: 0, width: "100%", overflow: "hidden" }}>
                <div style={{ borderBottom: "1px solid var(--border, #e2e8f0)", padding: "20px 24px" }}>
                    <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 600, color: "#1e293b" }}>
                        Permission Assigned to This Role
                    </h3>
                </div>

                <div style={{ padding: "24px", display: "flex", flexDirection: "column", gap: "20px" }}>
                    <p style={{ margin: 0, fontSize: "14px", color: "#64748b" }}>
                        Users assigned to this security profile have explicit authorization to perform the explicit scopes registered below:
                    </p>

                    {permissionsList.length > 0 ? (
                        <div className="permission-detail-grid">
                            {permissionsList.map((perm, index) => {
                                const permName = perm.description || perm.permissionId;
                                return (
                                    <div
                                        key={perm.permissionId || index}
                                        style={{
                                            padding: "16px 20px",
                                            backgroundColor: "#f8fafc",
                                            border: "1px solid #e2e8f0",
                                            borderRadius: "8px",
                                            display: "flex",
                                            flexDirection: "column",
                                            alignItems: "flex-start",
                                            justifyContent: "center",
                                            gap: "6px",
                                            boxSizing: "border-box",
                                        }}
                                    >
                                        <span style={{ fontSize: "13px", fontWeight: 500, color: "#334155", lineHeight: "1.4", wordBreak: "break-word" }}>
                                            {permName}
                                        </span>
                                        {perm.permissionId && (
                                            <span style={{ fontSize: "11px", color: "#94a3b8", fontFamily: "monospace", textTransform: "uppercase" }}>
                                                {perm.permissionId}
                                            </span>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        <div style={{ padding: "16px", backgroundColor: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: "6px", color: "#64748b", fontSize: "13px", fontStyle: "italic" }}>
                            No permissions assigned to this role.
                        </div>
                    )}
                </div>
            </div>
        </AdminPageContainer>
    );
}