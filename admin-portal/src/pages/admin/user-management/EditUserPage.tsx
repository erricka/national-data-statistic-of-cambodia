import { useEffect, useState, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";
import Icon from "../../../icons/Icon";
import {
    getUserRoleListDropdown,
    type UserRoleDropdownItem,
} from "../../../service/admin/user-service/userRoleListDropdownService";
import { resetPasswordByAdminToDefault } from "../../../service/admin/user-service/userResetPasswordService";
import { updateUser } from "../../../service/admin/user-service/updateUserService";

declare global {
    interface Window {
        NIS: any;
    }
}

interface UserRow {
    id: string | number;
    username: string;
    firstName?: string;
    lastName?: string;
    email: string;
    roles?: { roleId: string; name: string }[] | string[];
    isActive: boolean;
}

export default function EditUserPage() {
    const navigate = useNavigate();
    const location = useLocation();

    const passedUserData = location.state?.userData as UserRow | undefined;

    const [username, setUsername] = useState("");
    const [firstname, setFirstname] = useState("");
    const [lastname, setLastname] = useState("");
    const [email, setEmail] = useState("");

    const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([]);
    const [isRoleDropdownOpen, setIsRoleDropdownOpen] = useState<boolean>(false);
    const roleDropdownRef = useRef<HTMLDivElement>(null);

    const [isActive, setIsActive] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [successMessage, setSuccessMessage] = useState("");

    const [roles, setRoles] = useState<UserRoleDropdownItem[]>([]);
    const [isLoadingRoles, setIsLoadingRoles] = useState<boolean>(false);

    const [isResetConfirmModalOpen, setIsResetConfirmModalOpen] = useState(false);
    const [isResettingPassword, setIsResettingPassword] = useState(false);
    const [securityNoticeMessage, setSecurityNoticeMessage] = useState("");

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "user_management",
                title: "User Management",
                breadcrumb: ["Home", "User Management", "Edit User"]
            });
        }

        // Fetch Roles for Dropdown
        const fetchRoles = async () => {
            setIsLoadingRoles(true);
            try {
                const response = await getUserRoleListDropdown({ search: "" });
                if (response.code === "000000" && response.data?.list) {
                    setRoles(response.data.list);
                } else {
                    setRoles([]);
                }
            } catch (error) {
                console.error("Failed to load roles:", error);
                setRoles([]);
            } finally {
                setIsLoadingRoles(false);
            }
        };

        fetchRoles();
    }, []);

    // Load user data passed via router state
    useEffect(() => {
        if (passedUserData) {
            setUsername(passedUserData.username || "");
            setFirstname(passedUserData.firstName || "");
            setLastname(passedUserData.lastName || "");
            setEmail(passedUserData.email || "");
            setIsActive(passedUserData.isActive ?? true);

            if (Array.isArray(passedUserData.roles)) {
                const preselected = passedUserData.roles.map((r: any) =>
                    typeof r === "string" ? r : r.roleId || r.name
                );
                setSelectedRoleIds(preselected);
            }
        }
    }, [passedUserData]);

    // Close role dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (roleDropdownRef.current && !roleDropdownRef.current.contains(event.target as Node)) {
                setIsRoleDropdownOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    const handleRoleToggle = (roleId: string) => {
        setSelectedRoleIds((prev) =>
            prev.includes(roleId)
                ? prev.filter((id) => id !== roleId)
                : [...prev, roleId]
        );
    };

    // Submit profile updates
    const handleFormSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (selectedRoleIds.length === 0) {
            setErrorMessage("Please select at least one role.");
            return;
        }

        setIsSaving(true);
        setErrorMessage("");
        setSuccessMessage("");

        const payload = {
            id: passedUserData?.id,
            userID: username,
            firstName: firstname,
            lastName: lastname,
            email,
            status: isActive ? "01" : "09",
            roleIds: selectedRoleIds // Key name matches Kotlin backend extraction: body.get("roleIds")
        };

        try {
            const res = await updateUser(payload);

            if (res.code === "000000" || res.data?.updated) {
                setSuccessMessage("User profile and roles updated successfully!");
                setTimeout(() => {
                    navigate("/admin/user-management");
                }, 1500);
            } else {
                const messageText = typeof res.message === "string"
                    ? res.message
                    : res.message?.messageBody || "Failed to update user profile.";
                setErrorMessage(messageText);
            }
        } catch (error: any) {
            console.error("Update user error:", error);
            setErrorMessage("An unexpected error occurred while updating the user profile.");
        } finally {
            setIsSaving(false);
        }
    };

    // Open confirmation modal
    const handleOpenResetConfirm = () => {
        setIsResetConfirmModalOpen(true);
    };

    // Execute API USRU003 call, display success notice, and navigate back
    const handleConfirmResetPassword = async () => {
        const targetUser = username || String(passedUserData?.id || "");

        if (!targetUser) {
            setSecurityNoticeMessage("User ID is missing.");
            setIsResetConfirmModalOpen(false);
            return;
        }

        setIsResettingPassword(true);
        setSecurityNoticeMessage("");

        try {
            const res = await resetPasswordByAdminToDefault({ userID: targetUser });

            if (res.code === "000000" || res.data?.reset) {
                setIsResetConfirmModalOpen(false);

                setSecurityNoticeMessage(`✓ Password for user "${targetUser}" has been successfully reset to default! Redirecting to User Management...`);

                setTimeout(() => {
                    navigate("/admin/user-management");
                }, 1500);
            } else {
                setIsResetConfirmModalOpen(false);
                const messageText = typeof res.message === "string"
                    ? res.message
                    : res.message?.messageBody || "Failed to reset password.";
                setSecurityNoticeMessage(`Error: ${messageText}`);
            }
        } catch (error: any) {
            setIsResetConfirmModalOpen(false);
            console.error("Reset password error:", error);
            setSecurityNoticeMessage("Error: An unexpected error occurred while resetting password.");
        } finally {
            setIsResettingPassword(false);
        }
    };

    return (
        <div className="app">
            <Sidebar active="user_management" />

            <div className="main" style={{ width: "100%", overflowX: "hidden" }}>
                <Topbar pageTitle="Edit User" />

                <main className="content" style={{ padding: "clamp(12px, 3vw, 24px)", maxWidth: "100%", boxSizing: "border-box" }}>

                    <style>{`
                        .user-form-grid {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 24px;
                            width: 100%;
                        }
                        @media (max-width: 768px) {
                            .user-form-grid {
                                grid-template-columns: 1fr;
                                gap: 16px;
                            }
                        }
                    `}</style>

                    <div className="page-head" style={{ marginBottom: "16px" }}>
                        <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "flex-start" }}>
                            <button
                                type="button"
                                className="btn btn-outline btn-sm"
                                style={{ padding: "6px 10px" }}
                                onClick={() => navigate("/admin/user-management")}
                            >
                                <Icon name="arrow-left" /> Back
                            </button>
                            <div>
                                <h1 style={{ margin: 0 }}>Edit User: {username || "Profile"}</h1>
                            </div>
                        </div>
                    </div>

                    {/* Main User Profile Details Card */}
                    <div className="card" style={{ padding: "32px 24px", boxSizing: "border-box" }}>
                        <h3 style={{ margin: "0 0 8px 0", fontSize: "16px", color: "#1e293b" }}>User Profile</h3>
                        <p
                            className="hint"
                            style={{
                                fontStyle: "italic",
                                color: "#64748b",
                                fontSize: "13px",
                                marginBottom: "24px"
                            }}
                        >
                            The field labels marked with <span style={{ color: "var(--danger, #ef4444)" }}>*</span> are required input fields.
                        </p>

                        {/* Error Alert */}
                        {errorMessage && (
                            <div style={{
                                backgroundColor: "#fef2f2",
                                color: "var(--danger, #ef4444)",
                                border: "1px solid #fecaca",
                                padding: "12px 16px",
                                borderRadius: "6px",
                                marginBottom: "16px",
                                fontSize: "14px"
                            }}>
                                {errorMessage}
                            </div>
                        )}

                        {/* Success Alert */}
                        {successMessage && (
                            <div style={{
                                backgroundColor: "#f0fdf4",
                                color: "#166534",
                                border: "1px solid #bbf7d0",
                                padding: "12px 16px",
                                borderRadius: "6px",
                                marginBottom: "16px",
                                fontSize: "14px",
                                fontWeight: 500
                            }}>
                                ✓ {successMessage} Redirecting...
                            </div>
                        )}

                        <form onSubmit={handleFormSubmit} style={{ display: "flex", flexDirection: "column", gap: "20px", width: "100%" }}>

                            {/* Row 1: UserName & Email */}
                            <div className="user-form-grid">
                                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                    <label htmlFor="u-name" style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b" }}>
                                        UserName <span style={{ color: "var(--danger, #ef4444)" }}>*</span>
                                    </label>
                                    <input
                                        id="u-name"
                                        type="text"
                                        className="input"
                                        style={{ width: "100%", boxSizing: "border-box" }}
                                        value={username}
                                        onChange={(e) => setUsername(e.target.value)}
                                        required
                                    />
                                </div>

                                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                    <label htmlFor="u-email" style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b" }}>
                                        Email <span style={{ color: "var(--danger, #ef4444)" }}>*</span>
                                    </label>
                                    <input
                                        id="u-email"
                                        type="email"
                                        className="input"
                                        style={{ width: "100%", boxSizing: "border-box" }}
                                        placeholder="example@example.com"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        required
                                    />
                                </div>
                            </div>

                            {/* Row 2: First Name & Last Name */}
                            <div className="user-form-grid">
                                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                    <label htmlFor="f-name" style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b" }}>
                                        First Name <span style={{ color: "var(--danger, #ef4444)" }}>*</span>
                                    </label>
                                    <input
                                        id="f-name"
                                        type="text"
                                        className="input"
                                        style={{ width: "100%", boxSizing: "border-box" }}
                                        value={firstname}
                                        onChange={(e) => setFirstname(e.target.value)}
                                        required
                                    />
                                </div>

                                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                    <label htmlFor="l-name" style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b" }}>
                                        Last Name <span style={{ color: "var(--danger, #ef4444)" }}>*</span>
                                    </label>
                                    <input
                                        id="l-name"
                                        type="text"
                                        className="input"
                                        style={{ width: "100%", boxSizing: "border-box" }}
                                        value={lastname}
                                        onChange={(e) => setLastname(e.target.value)}
                                        required
                                    />
                                </div>
                            </div>

                            {/* Row 3: Roles Multi-select Dropdown */}
                            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                <label style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b" }}>
                                    Roles <span style={{ color: "var(--danger, #ef4444)" }}>*</span>
                                </label>

                                <div ref={roleDropdownRef} style={{ position: "relative", width: "100%" }}>
                                    <div
                                        className="input"
                                        onClick={() => setIsRoleDropdownOpen((prev) => !prev)}
                                        style={{
                                            minHeight: "42px",
                                            height: "auto",
                                            width: "100%",
                                            boxSizing: "border-box",
                                            cursor: "pointer",
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "space-between",
                                            paddingRight: "36px",
                                            paddingTop: "6px",
                                            paddingBottom: "6px",
                                            position: "relative",
                                            backgroundColor: "#fff"
                                        }}
                                    >
                                        <div style={{ display: "flex", flexWrap: "wrap", gap: "6px", alignItems: "center" }}>
                                            {selectedRoleIds.length === 0 ? (
                                                <span style={{ color: "#94a3b8" }}>
                                                    {isLoadingRoles ? "Loading roles..." : "Select Roles..."}
                                                </span>
                                            ) : (
                                                selectedRoleIds.map((rId) => {
                                                    const matched = roles.find((r) => (r.roleId || r.name) === rId);
                                                    const labelText = matched ? (matched.name || matched.roleId) : rId;
                                                    return (
                                                        <span
                                                            key={rId}
                                                            style={{
                                                                display: "inline-flex",
                                                                alignItems: "center",
                                                                gap: "4px",
                                                                background: "#e2e8f0",
                                                                color: "#334155",
                                                                padding: "2px 8px",
                                                                borderRadius: "4px",
                                                                fontSize: "12px",
                                                                fontWeight: 500
                                                            }}
                                                        >
                                                            {labelText}
                                                            <span
                                                                onClick={(e) => {
                                                                    e.stopPropagation();
                                                                    handleRoleToggle(rId);
                                                                }}
                                                                style={{ cursor: "pointer", fontWeight: "bold", marginLeft: "2px" }}
                                                            >
                                                                ×
                                                            </span>
                                                        </span>
                                                    );
                                                })
                                            )}
                                        </div>

                                        <svg
                                            style={{
                                                position: "absolute",
                                                right: "12px",
                                                top: "50%",
                                                transform: `translateY(-50%) rotate(${isRoleDropdownOpen ? "180deg" : "0deg"})`,
                                                transition: "transform 0.2s ease",
                                                pointerEvents: "none"
                                            }}
                                            width="16"
                                            height="16"
                                            viewBox="0 0 24 24"
                                            fill="none"
                                            stroke="#64748b"
                                            strokeWidth="2"
                                            strokeLinecap="round"
                                            strokeLinejoin="round"
                                        >
                                            <polyline points="6 9 12 15 18 9"></polyline>
                                        </svg>
                                    </div>

                                    {isRoleDropdownOpen && (
                                        <div
                                            style={{
                                                position: "absolute",
                                                top: "100%",
                                                left: 0,
                                                right: 0,
                                                marginTop: "4px",
                                                backgroundColor: "#ffffff",
                                                border: "1px solid var(--border, #cbd5e1)",
                                                borderRadius: "6px",
                                                boxShadow: "0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)",
                                                maxHeight: "200px",
                                                overflowY: "auto",
                                                zIndex: 50,
                                                padding: "4px 0"
                                            }}
                                        >
                                            {isLoadingRoles ? (
                                                <div style={{ padding: "8px 12px", fontSize: "13px", color: "#64748b" }}>Loading roles...</div>
                                            ) : roles.length === 0 ? (
                                                <div style={{ padding: "8px 12px", fontSize: "13px", color: "#64748b" }}>No roles available</div>
                                            ) : (
                                                roles.map((r) => {
                                                    const roleValue = r.roleId || r.name;
                                                    const isChecked = selectedRoleIds.includes(roleValue);
                                                    return (
                                                        <div
                                                            key={roleValue}
                                                            onClick={() => handleRoleToggle(roleValue)}
                                                            style={{
                                                                display: "flex",
                                                                alignItems: "center",
                                                                gap: "10px",
                                                                padding: "8px 12px",
                                                                cursor: "pointer",
                                                                fontSize: "14px",
                                                                color: "#334155",
                                                                backgroundColor: isChecked ? "#f1f5f9" : "transparent"
                                                            }}
                                                        >
                                                            <input
                                                                type="checkbox"
                                                                checked={isChecked}
                                                                onChange={() => { }}
                                                                style={{ cursor: "pointer", width: "16px", height: "16px" }}
                                                            />
                                                            <span>{r.name || r.roleId}</span>
                                                        </div>
                                                    );
                                                })
                                            )}
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Active Checkbox */}
                            <div style={{ marginTop: "8px", display: "flex", alignItems: "center", gap: "8px" }}>
                                <input
                                    id="u-active"
                                    type="checkbox"
                                    checked={isActive}
                                    onChange={(e) => setIsActive(e.target.checked)}
                                    style={{
                                        width: "16px",
                                        height: "16px",
                                        cursor: "pointer"
                                    }}
                                />
                                <label htmlFor="u-active" style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b", cursor: "pointer" }}>
                                    Active
                                </label>
                            </div>

                            {/* Action Buttons */}
                            <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px", marginTop: "12px" }}>
                                <button
                                    type="button"
                                    className="btn btn-ghost"
                                    onClick={() => navigate("/admin/user-management")}
                                    disabled={isSaving}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="btn btn-primary"
                                    disabled={isSaving}
                                >
                                    {isSaving ? "Saving..." : "Save"}
                                </button>
                            </div>
                        </form>
                    </div>

                    {/* Dedicated Authentication & Security Card */}
                    <div className="card" style={{ padding: "24px", marginTop: "24px", boxSizing: "border-box" }}>
                        <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", color: "#1e293b" }}>Authentication & Security</h3>
                        <p style={{ margin: "0 0 16px 0", fontSize: "13px", color: "#64748b" }}>
                            Manage credential resets or manually assign temporary credentials for this user.
                        </p>

                        {securityNoticeMessage && (
                            <div style={{
                                backgroundColor: securityNoticeMessage.startsWith("Error")
                                    ? "#fef2f2"
                                    : "#f0fdf4",
                                color: securityNoticeMessage.startsWith("Error")
                                    ? "var(--danger, #ef4444)"
                                    : "#166534", // Dark green text for success
                                border: `1px solid ${securityNoticeMessage.startsWith("Error")
                                    ? "#fecaca"
                                    : "#bbf7d0"
                                    }`,
                                padding: "12px 16px",
                                borderRadius: "6px",
                                marginBottom: "16px",
                                fontSize: "14px",
                                fontWeight: 500,
                                display: "flex",
                                alignItems: "center",
                                gap: "8px"
                            }}>
                                {securityNoticeMessage}
                            </div>
                        )}

                        <div style={{ display: "flex", gap: "12px", flexWrap: "wrap" }}>
                            <button
                                type="button"
                                className="btn btn-outline"
                                style={{ color: "#ef4444", borderColor: "#fca5a5" }}
                                onClick={handleOpenResetConfirm}
                                disabled={isResettingPassword}
                            >
                                <Icon name="key" />
                                {isResettingPassword ? "Resetting Password..." : "Reset Password"}
                            </button>

                        </div>
                    </div>

                    {/* Custom Confirmation Modal */}
                    {isResetConfirmModalOpen && (
                        <div style={{
                            position: "fixed",
                            top: 0,
                            left: 0,
                            right: 0,
                            bottom: 0,
                            backgroundColor: "rgba(15, 23, 42, 0.4)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            zIndex: 1000,
                            padding: "16px"
                        }}>
                            <div className="card" style={{ width: "100%", maxWidth: "440px", padding: "24px", backgroundColor: "#fff", borderRadius: "8px", boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)" }}>
                                <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" }}>
                                    <div style={{
                                        width: "40px",
                                        height: "40px",
                                        borderRadius: "50%",
                                        backgroundColor: "#fef2f2",
                                        color: "#ef4444",
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "center",
                                        flexShrink: 0
                                    }}>
                                        <Icon name="key" />
                                    </div>
                                    <h3 style={{ margin: 0, fontSize: "18px", color: "#1e293b", fontWeight: 600 }}>Reset Password</h3>
                                </div>
                                <p style={{ margin: "0 0 24px 0", fontSize: "14px", color: "#64748b", lineHeight: "1.5" }}>
                                    Are you sure you want to reset the password for user <strong>"{username}"</strong>? The password will be reset to the default configuration.
                                </p>
                                <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px" }}>
                                    <button
                                        type="button"
                                        className="btn btn-ghost"
                                        onClick={() => setIsResetConfirmModalOpen(false)}
                                        disabled={isResettingPassword}
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="button"
                                        className="btn btn-primary"
                                        style={{ backgroundColor: "#ef4444", borderColor: "#ef4444" }}
                                        onClick={handleConfirmResetPassword}
                                        disabled={isResettingPassword}
                                    >
                                        {isResettingPassword ? "Resetting..." : "Confirm Reset"}
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