import { useEffect } from "react";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import Icon from "../../icons/Icon";

interface ApprovalModalShellProps {
    isOpen: boolean;
    title: string;
    onClose: () => void;
    /** While true, the modal cannot be dismissed (prevents losing an in-flight request). */
    isBusy?: boolean;
    maxWidth?: string;
    children: ReactNode;
    footer?: ReactNode;
}

/**
 * Dialog frame for Approval Management.
 * Visuals mirror the existing category/user modals (portal, blurred backdrop, 12px card).
 */
export default function ApprovalModalShell({
    isOpen,
    title,
    onClose,
    isBusy = false,
    maxWidth = "560px",
    children,
    footer,
}: ApprovalModalShellProps) {
    // Lock background scroll and support Escape to close (same as ViewUserModal)
    useEffect(() => {
        if (!isOpen) return;
        document.body.style.overflow = "hidden";
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape" && !isBusy) onClose();
        };
        window.addEventListener("keydown", onKeyDown);
        return () => {
            document.body.style.overflow = "";
            window.removeEventListener("keydown", onKeyDown);
        };
    }, [isOpen, isBusy, onClose]);

    if (!isOpen) return null;

    return createPortal(
        <>
            {/* Backdrop Layer */}
            <div
                style={{
                    position: "fixed",
                    inset: 0,
                    background: "rgba(15, 23, 42, 0.5)",
                    backdropFilter: "blur(2px)",
                    zIndex: 99998,
                }}
                onClick={isBusy ? undefined : onClose}
            />

            {/* Modal Dialog Card */}
            <div
                role="dialog"
                aria-modal="true"
                aria-label={title}
                style={{
                    position: "fixed",
                    top: "50%",
                    left: "50%",
                    transform: "translate(-50%, -50%)",
                    zIndex: 99999,
                    width: "100%",
                    maxWidth,
                    padding: "0 16px",
                    boxSizing: "border-box",
                }}
            >
                <div
                    style={{
                        background: "#ffffff",
                        borderRadius: "12px",
                        boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)",
                        overflow: "hidden",
                        display: "flex",
                        flexDirection: "column",
                        maxHeight: "calc(100vh - 48px)",
                    }}
                >
                    {/* Header */}
                    <div
                        style={{
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                            padding: "18px 24px",
                            borderBottom: "1px solid var(--border)",
                            boxSizing: "border-box",
                            flexShrink: 0,
                        }}
                    >
                        <h3 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 600, color: "#0f172a" }}>{title}</h3>
                        <button
                            className="icon-btn"
                            onClick={onClose}
                            type="button"
                            disabled={isBusy}
                            aria-label="Close"
                            style={{ height: "auto", width: "auto" }}
                        >
                            <Icon name="x" />
                        </button>
                    </div>

                    {/* Body */}
                    <div style={{ padding: "24px", boxSizing: "border-box", overflowY: "auto" }}>{children}</div>

                    {/* Footer */}
                    {footer && (
                        <div
                            style={{
                                display: "flex",
                                justifyContent: "flex-end",
                                gap: "12px",
                                padding: "16px 24px",
                                borderTop: "1px solid var(--border)",
                                flexShrink: 0,
                            }}
                        >
                            {footer}
                        </div>
                    )}
                </div>
            </div>
        </>,
        document.body
    );
}

/** Inline error box used inside approval modals (same style as AddCategoryModal). */
export function ModalErrorBanner({ message }: { message: string }) {
    if (!message) return null;
    return (
        <div
            role="alert"
            style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                padding: "12px 16px",
                marginBottom: "18px",
                color: "#b91c1c",
                backgroundColor: "#fef2f2",
                border: "1px solid #fecaca",
                borderRadius: "8px",
                fontSize: "14px",
            }}
        >
            <span>{message}</span>
        </div>
    );
}
