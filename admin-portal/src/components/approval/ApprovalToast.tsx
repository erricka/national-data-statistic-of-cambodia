import { useEffect } from "react";
import { createPortal } from "react-dom";
import Icon from "../../icons/Icon";

export interface ApprovalToastMessage {
    id: number;
    type: "success" | "error";
    text: string;
}

interface ApprovalToastProps {
    toast: ApprovalToastMessage | null;
    onDismiss: () => void;
    durationMs?: number;
}

/**
 * Page-level toast. Same visual as the success toast used in the category
 * modals, rendered at page level so it stays visible after a modal closes.
 */
export default function ApprovalToast({ toast, onDismiss, durationMs = 3000 }: ApprovalToastProps) {
    useEffect(() => {
        if (!toast) return;
        const timer = setTimeout(onDismiss, durationMs);
        return () => clearTimeout(timer);
    }, [toast, onDismiss, durationMs]);

    if (!toast) return null;

    const isSuccess = toast.type === "success";

    return createPortal(
        <div
            role="status"
            aria-live="polite"
            style={{
                position: "fixed",
                top: "24px",
                right: "24px",
                zIndex: 100000,
                display: "flex",
                alignItems: "center",
                gap: "10px",
                backgroundColor: isSuccess ? "#10b981" : "#ef4444",
                color: "#ffffff",
                padding: "12px 20px",
                borderRadius: "8px",
                boxShadow: isSuccess
                    ? "0 10px 15px -3px rgba(16, 185, 129, 0.3), 0 4px 6px -4px rgba(0, 0, 0, 0.1)"
                    : "0 10px 15px -3px rgba(239, 68, 68, 0.3), 0 4px 6px -4px rgba(0, 0, 0, 0.1)",
                fontSize: "14px",
                fontWeight: 500,
                maxWidth: "calc(100vw - 48px)",
            }}
        >
            <Icon name={isSuccess ? "check" : "x"} />
            <span>{toast.text}</span>
        </div>,
        document.body
    );
}
