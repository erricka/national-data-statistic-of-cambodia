import { useEffect, useState, useRef } from "react";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";
import Icon from "../../../icons/Icon";
import { useNavigate } from "react-router-dom";
import { createUser, type CreateUserPayload } from "../../../service/admin/user-service/createUserService";
import {
    getUserRoleListDropdown,
    type UserRoleDropdownItem
} from "../../../service/admin/user-service/userRoleListDropdownService";
import { getErrorMessage } from "../../../utils/errorHandler";
import { generateSecurePassword } from "../../../utils/password";

export default function AddUserPage() {
    const navigate = useNavigate();
    const [username, setUsername] = useState("");
    const [firstname, setFirstname] = useState("");
    const [lastname, setLastname] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [email, setEmail] = useState("");

    // Multi-select state for roles
    const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([]);
    const [isRoleDropdownOpen, setIsRoleDropdownOpen] = useState<boolean>(false);
    const roleDropdownRef = useRef<HTMLDivElement>(null);

    const [isActive, setIsActive] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [successMessage, setSuccessMessage] = useState("");

    const [roles, setRoles] = useState<UserRoleDropdownItem[]>([]);
    const [isLoadingRoles, setIsLoadingRoles] = useState<boolean>(false);

    useEffect(() => {
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "user_management",
                title: "User Management",
                breadcrumb: ["Home", "User Management", "Add User"]
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

    // Close multi-select dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (roleDropdownRef.current && !roleDropdownRef.current.contains(event.target as Node)) {
                setIsRoleDropdownOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    const handleGeneratePassword = () => {
        setPassword(generateSecurePassword());
        setShowPassword(true);
    };

    // Toggle Role selection logic
    const handleRoleToggle = (roleId: string) => {
        setSelectedRoleIds((prev) =>
            prev.includes(roleId)
                ? prev.filter((id) => id !== roleId)
                : [...prev, roleId]
        );
    };

    const handleFormSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        // Client-side validation for roles
        if (selectedRoleIds.length === 0) {
            setErrorMessage("Please select at least one role.");
            return;
        }

        setIsSaving(true);
        setErrorMessage("");
        setSuccessMessage("");

        const payload: CreateUserPayload = {
            userID: username,
            firstName: firstname,
            lastName: lastname,
            password,
            email,
            status: isActive ? "01" : "09",
            userType: "SYSUSER",
            roleIds: selectedRoleIds
        };

        try {
            const response = await createUser(payload);

            if (response.code && response.code !== "000000") {
                setErrorMessage(getErrorMessage(response));
                return;
            }

            setSuccessMessage("User created successfully!");
            setTimeout(() => {
                navigate("/admin/user-management");
            }, 1500);

        } catch (error: any) {
            console.error("Error creating user:", error);
            setErrorMessage(getErrorMessage(error));
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="app">
            <Sidebar active="user_management" />

            <div className="main" style={{ width: "100%", overflowX: "hidden" }}>
                <Topbar pageTitle="Add User" />

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
                                <h1 style={{ margin: 0 }}>Add User</h1>
                            </div>
                        </div>
                    </div>

                    <div className="card" style={{ padding: "32px 24px", boxSizing: "border-box" }}>
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

                            {/* Row 3: Password & Custom Multi-select Role Dropdown */}
                            <div className="user-form-grid">
                                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                    <label htmlFor="u-pass" style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b" }}>
                                        Password <span style={{ color: "var(--danger, #ef4444)" }}>*</span>
                                    </label>
                                    <div style={{ display: "flex", width: "100%" }}>
                                        <div style={{ position: "relative", flex: 1, display: "flex", alignItems: "center" }}>
                                            <input
                                                id="u-pass"
                                                type={showPassword ? "text" : "password"}
                                                className="input"
                                                style={{
                                                    width: "100%",
                                                    borderTopRightRadius: 0,
                                                    borderBottomRightRadius: 0,
                                                    boxSizing: "border-box",
                                                    paddingRight: "40px"
                                                }}
                                                value={password}
                                                onChange={(e) => setPassword(e.target.value)}
                                                required
                                            />
                                            <button
                                                type="button"
                                                onClick={() => setShowPassword(!showPassword)}
                                                style={{
                                                    position: "absolute",
                                                    right: "10px",
                                                    zIndex: 2,
                                                    background: "transparent",
                                                    border: "none",
                                                    cursor: "pointer",
                                                    color: "#64748b",
                                                    padding: "4px",
                                                    display: "flex",
                                                    alignItems: "center",
                                                    justifyContent: "center"
                                                }}
                                                title={showPassword ? "Hide password" : "Show password"}
                                            >
                                                {showPassword ? (
                                                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                                        <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"></path>
                                                        <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"></path>
                                                        <path d="M6.61 6.61A13.52 13.52 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"></path>
                                                        <line x1="2" y1="2" x2="22" y2="22"></line>
                                                    </svg>
                                                ) : (
                                                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                                        <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z"></path>
                                                        <circle cx="12" cy="12" r="3"></circle>
                                                    </svg>
                                                )}
                                            </button>
                                        </div>

                                        <button
                                            type="button"
                                            className="btn btn-outline"
                                            onClick={handleGeneratePassword}
                                            style={{
                                                borderTopLeftRadius: 0,
                                                borderBottomLeftRadius: 0,
                                                borderLeft: "none",
                                                borderColor: "var(--border, #cbd5e1)",
                                                color: "#0F3D91",
                                                fontWeight: 500,
                                                whiteSpace: "nowrap"
                                            }}
                                        >
                                            Generate
                                        </button>
                                    </div>
                                </div>

                                {/* Multi-select Role Dropdown Container */}
                                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                    <label style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b" }}>
                                        Roles <span style={{ color: "var(--danger, #ef4444)" }}>*</span>
                                    </label>

                                    <div ref={roleDropdownRef} style={{ position: "relative", width: "100%" }}>
                                        {/* Dropdown Input Box Display */}
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

                                            {/* Dropdown Chevron Icon */}
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

                                        {/* Dropdown Floating Options Menu */}
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
                                                                    onChange={() => { }} // Controlled via parent row click
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

                            {/* Buttons */}
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
                                    {isSaving ? "Saving..." : "Submit"}
                                </button>
                            </div>

                        </form>
                    </div>
                </main>
            </div>
        </div>
    );
}