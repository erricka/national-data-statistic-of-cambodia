import { useEffect } from "react";
import Icon from "../../../../icons/Icon";

interface UserRow {
    id: string | number;
    name: string;
    email: string;
    dept: string;
    role: string;
    statusKey: string;
    statusText: string;
    time: string;
    initial: string;
}

interface ViewUserModalProps {
    isOpen: boolean;
    user: UserRow | null;
    onClose: () => void;
}

export default function ViewUserModal({ isOpen, user, onClose }: ViewUserModalProps) {
    // Lock background scrolling when modal is active
    useEffect(() => {
        if (isOpen) {
            document.body.style.overflow = "hidden";
        } else {
            document.body.style.overflow = "";
        }
        return () => {
            document.body.style.overflow = "";
        };
    }, [isOpen]);

    if (!isOpen || !user) return null;

    // Helper to map roles to style classes or display colors matches your parent table structure
    const ROLE_COLORS: Record<string, string> = {
        "Super Administrator": "danger",
        "Data Administrator": "info",
        "Department Manager": "teal",
        "Data Entry Officer": "neutral",
        "Public User": "neutral"
    };

    const STATUS_MAP: Record<string, string> = {
        active: "success",
        warning: "warning",
        danger: "danger"
    };

    return (
        <div
            className="modal-overlay"
            style={{
                position: "fixed",
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                backgroundColor: "rgba(15, 23, 42, 0.6)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                zIndex: 999,
                padding: "20px"
            }}
            onClick={onClose}
        >
            <div
                className="modal-card card"
                style={{
                    width: "100%",
                    maxWidth: "550px",
                    backgroundColor: "#ffffff",
                    borderRadius: "8px",

                    overflow: "hidden",
                    animation: "fadeIn 0.2s ease-out"
                }}
                onClick={(e) => e.stopPropagation()} // Prevent closing when clicking inside the content
            >
                {/* Header section */}
                <div
                    style={{
                        display: "flex",
                        alignItems: "center",
                        padding: "16px 24px",
                        borderBottom: "1px solid var(--border, #cbd5e1)",
                        justifyContent: "space-between"
                    }}
                >
                    <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 600, color: "#1e293b" }}>
                        User Information
                    </h3>
                    <button
                        className="icon-btn"
                        onClick={onClose}
                        style={{ border: "none", background: "none", cursor: "pointer", padding: "4px" }}
                    >
                        <Icon name="x" />
                    </button>
                </div>

                {/* Profile Hero section */}
                <div
                    style={{
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "center",
                        padding: "24px",
                        backgroundColor: "#f8fafc",
                        borderBottom: "1px solid var(--border, #e2e8f0)",
                        textAlign: "center"
                    }}
                >
                    <span
                        className="avatar"
                        style={{
                            width: "64px",
                            height: "64px",
                            fontSize: "22px",
                            fontWeight: 600,
                            background: "var(--p-100, #e0e7ff)",
                            color: "var(--p-700, #4338ca)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            borderRadius: "50%",
                            marginBottom: "12px"
                        }}
                    >
                        {user.initial}
                    </span>
                    <h2 style={{ margin: "0 0 4px 0", fontSize: "18px", fontWeight: 600, color: "#0f172a" }}>
                        {user.name}
                    </h2>
                    <span className={`badge badge-${STATUS_MAP[user.statusKey] || "neutral"}`} style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                        <span className="d"></span>
                        {user.statusText}
                    </span>
                </div>

                {/* Details Data Body list layout */}
                <div style={{ padding: "24px", display: "flex", flexDirection: "column", gap: "16px" }}>

                    <div style={{ display: "grid", gridTemplateColumns: "120px 1fr", gap: "8px", alignItems: "baseline" }}>
                        <span style={{ fontSize: "13px", fontWeight: 500, color: "#64748b" }}>Email Address</span>
                        <span style={{ fontSize: "14px", color: "#334155", wordBreak: "break-all" }}>{user.email}</span>
                    </div>

                    <div style={{ display: "grid", gridTemplateColumns: "120px 1fr", gap: "8px", alignItems: "center" }}>
                        <span style={{ fontSize: "13px", fontWeight: 500, color: "#64748b" }}>Access Level</span>
                        <div>
                            <span className={`badge badge-${ROLE_COLORS[user.role] || "neutral"}`}>
                                {user.role}
                            </span>
                        </div>
                    </div>

                    <div style={{ display: "grid", gridTemplateColumns: "120px 1fr", gap: "8px", alignItems: "baseline" }}>
                        <span style={{ fontSize: "13px", fontWeight: 500, color: "#64748b" }}>Last Active</span>
                        <span style={{ fontSize: "14px", color: "#334155" }}>{user.time}</span>
                    </div>
                </div>

            </div>
        </div>
    );
}