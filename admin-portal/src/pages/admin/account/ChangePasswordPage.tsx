import { useRef, useState } from "react";
import type { SyntheticEvent } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import { AdminPageContainer } from "../../../layout/AdminPageContainer";
import { changeOwnPassword } from "../../../service/admin/user-service/changePasswordService";
import { PASSWORD_RULES } from "../../../utils/password";
import { getErrorMessage } from "../../../utils/errorHandler";

/* -------------------------------------------------------------------------- */
/* Password input with show/hide toggle (same look as AddUserPage)            */
/* -------------------------------------------------------------------------- */

interface PasswordFieldProps {
    id: string;
    label: string;
    value: string;
    onChange: (value: string) => void;
    autoComplete: string;
    disabled: boolean;
    invalid?: boolean;
    describedBy?: string;
}

function PasswordField({ id, label, value, onChange, autoComplete, disabled, invalid, describedBy }: PasswordFieldProps) {
    const [visible, setVisible] = useState(false);

    return (
        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            <label htmlFor={id} style={{ fontWeight: 600, fontSize: "14px", color: "#1e293b" }}>
                {label} <span style={{ color: "var(--danger, #ef4444)" }}>*</span>
            </label>
            <div style={{ position: "relative", display: "flex", alignItems: "center" }}>
                <input
                    id={id}
                    type={visible ? "text" : "password"}
                    className="input"
                    style={{
                        width: "100%",
                        boxSizing: "border-box",
                        paddingRight: "40px",
                        borderColor: invalid ? "var(--danger, #ef4444)" : undefined,
                    }}
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    autoComplete={autoComplete}
                    disabled={disabled}
                    aria-invalid={invalid || undefined}
                    aria-describedby={describedBy}
                    required
                />
                <button
                    type="button"
                    onClick={() => setVisible((v) => !v)}
                    disabled={disabled}
                    aria-label={visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
                    title={visible ? "Hide password" : "Show password"}
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
                        justifyContent: "center",
                    }}
                >
                    {visible ? (
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
        </div>
    );
}

/* -------------------------------------------------------------------------- */
/* Page                                                                       */
/* -------------------------------------------------------------------------- */

const bannerStyle = (kind: "error" | "success") => ({
    display: "flex",
    alignItems: "center",
    gap: "8px",
    padding: "12px 16px",
    marginBottom: "20px",
    borderRadius: "8px",
    fontSize: "14px",
    color: kind === "error" ? "#b91c1c" : "#047857",
    backgroundColor: kind === "error" ? "#fef2f2" : "#ecfdf5",
    border: `1px solid ${kind === "error" ? "#fecaca" : "#a7f3d0"}`,
});

export default function ChangePasswordPage() {
    const navigate = useNavigate();

    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [successMessage, setSuccessMessage] = useState("");
    // Synchronous guard so a double click cannot send two requests
    const inFlightRef = useRef(false);

    const ruleResults = PASSWORD_RULES.map((rule) => ({ ...rule, passed: rule.test(newPassword) }));
    const meetsPolicy = ruleResults.every((r) => r.passed);
    const confirmMismatch = confirmPassword !== "" && confirmPassword !== newPassword;
    const sameAsCurrent = newPassword !== "" && newPassword === currentPassword;

    const canSubmit =
        currentPassword !== "" && meetsPolicy && confirmPassword === newPassword && !sameAsCurrent && !isSubmitting;

    const clearMessages = () => {
        setErrorMessage("");
        setSuccessMessage("");
    };

    const handleSubmit = async (e: SyntheticEvent) => {
        e.preventDefault();
        if (!canSubmit || inFlightRef.current) return;

        inFlightRef.current = true;
        setIsSubmitting(true);
        clearMessages();

        try {
            const res = await changeOwnPassword(currentPassword, newPassword);
            if (res?.code === "000000") {
                setCurrentPassword("");
                setNewPassword("");
                setConfirmPassword("");
                setSuccessMessage("Your password has been changed. Use the new password the next time you sign in.");
            } else {
                setErrorMessage(getErrorMessage({ message: res?.message }) || "Failed to change password.");
            }
        } catch (error) {
            console.error("Change password failed:", error);
            setErrorMessage(getErrorMessage(error) || "Failed to change password. Please try again.");
        } finally {
            inFlightRef.current = false;
            setIsSubmitting(false);
        }
    };

    return (
        <AdminPageContainer
            sidebarActive="change_password"
            pageTitle="Change Password"
            hasInitialLoaded={true}
            isLoading={false}
            isForbidden={false}
        >
            <div className="page-head">
                <div>
                    <h1>Change Password</h1>
                    <p>Update the password you use to sign in to the NIS Web Portal</p>
                </div>
            </div>

            <div className="card" style={{ maxWidth: "560px" }}>
                <div className="card-head">
                    <h3 style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                        <Icon name="key" /> Your password
                    </h3>
                </div>

                <form className="card-body" onSubmit={handleSubmit} noValidate>
                    {errorMessage && (
                        <div role="alert" style={bannerStyle("error")}>
                            {errorMessage}
                        </div>
                    )}
                    {successMessage && (
                        <div role="status" style={bannerStyle("success")}>
                            <Icon name="check" /> {successMessage}
                        </div>
                    )}

                    <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
                        <PasswordField
                            id="cp-current"
                            label="Current password"
                            value={currentPassword}
                            onChange={(v) => {
                                setCurrentPassword(v);
                                clearMessages();
                            }}
                            autoComplete="current-password"
                            disabled={isSubmitting}
                        />

                        <div>
                            <PasswordField
                                id="cp-new"
                                label="New password"
                                value={newPassword}
                                onChange={(v) => {
                                    setNewPassword(v);
                                    clearMessages();
                                }}
                                autoComplete="new-password"
                                disabled={isSubmitting}
                                invalid={sameAsCurrent}
                                describedBy="cp-rules"
                            />
                            {sameAsCurrent && (
                                <div style={{ color: "var(--danger, #ef4444)", fontSize: "12.5px", marginTop: "6px" }}>
                                    The new password must be different from the current password.
                                </div>
                            )}

                            {/* Live password policy checklist */}
                            <ul
                                id="cp-rules"
                                aria-label="Password requirements"
                                style={{
                                    listStyle: "none",
                                    margin: "12px 0 0",
                                    padding: "12px 14px",
                                    background: "var(--n-50)",
                                    border: "1px solid var(--border)",
                                    borderRadius: "8px",
                                    display: "grid",
                                    gap: "6px",
                                }}
                            >
                                {ruleResults.map((rule) => (
                                    <li
                                        key={rule.id}
                                        data-passed={rule.passed}
                                        style={{
                                            display: "flex",
                                            alignItems: "center",
                                            gap: "8px",
                                            fontSize: "13px",
                                            color: rule.passed ? "#047857" : "#64748b",
                                        }}
                                    >
                                        <span
                                            aria-hidden="true"
                                            style={{
                                                display: "inline-flex",
                                                width: "16px",
                                                height: "16px",
                                                alignItems: "center",
                                                justifyContent: "center",
                                                transform: "scale(0.8)",
                                            }}
                                        >
                                            <Icon name={rule.passed ? "check" : "x"} />
                                        </span>
                                        <span>
                                            {rule.label}
                                            <span className="sr-only" style={{ position: "absolute", left: "-9999px" }}>
                                                {rule.passed ? " (met)" : " (not met)"}
                                            </span>
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        </div>

                        <div>
                            <PasswordField
                                id="cp-confirm"
                                label="Confirm new password"
                                value={confirmPassword}
                                onChange={(v) => {
                                    setConfirmPassword(v);
                                    clearMessages();
                                }}
                                autoComplete="new-password"
                                disabled={isSubmitting}
                                invalid={confirmMismatch}
                            />
                            {confirmMismatch && (
                                <div style={{ color: "var(--danger, #ef4444)", fontSize: "12.5px", marginTop: "6px" }}>
                                    Passwords do not match.
                                </div>
                            )}
                        </div>
                    </div>

                    <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px", marginTop: "28px" }}>
                        <button
                            type="button"
                            className="btn btn-ghost"
                            onClick={() => navigate(-1)}
                            disabled={isSubmitting}
                            style={{ borderRadius: "8px" }}
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            className="btn btn-primary"
                            disabled={!canSubmit}
                            style={{
                                borderRadius: "8px",
                                minWidth: "160px",
                                opacity: canSubmit ? 1 : 0.6,
                                cursor: canSubmit ? "pointer" : "not-allowed",
                            }}
                        >
                            {isSubmitting ? "Changing..." : "Change Password"}
                        </button>
                    </div>
                </form>
            </div>
        </AdminPageContainer>
    );
}
