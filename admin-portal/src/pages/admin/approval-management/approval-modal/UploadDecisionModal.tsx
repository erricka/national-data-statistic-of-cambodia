import { useRef, useState } from "react";
import type { SyntheticEvent } from "react";
import ApprovalModalShell, { ModalErrorBanner } from "../../../../components/approval/ApprovalModalShell";
import { getApprovalErrorMessage } from "../../../../service/admin/approval-service/approvalErrors";
import type { ApiResponse } from "../../../../api/api";

export interface UploadDecisionModalProps {
    isOpen: boolean;
    uploadId: string;
    datasetName?: string;
    onClose: () => void;
    /** Called after the API succeeds; receives the success message to show. */
    onSuccess: (message: string) => void;
}

interface DecisionConfig {
    id: string;
    title: string;
    prompt: string;
    fieldLabel: string;
    fieldPlaceholder: string;
    isRequired: boolean;
    confirmLabel: string;
    dismissLabel?: string;
    busyLabel: string;
    confirmClassName: string;
    successMessage: string;
    failureMessage: string;
    submit: (uploadId: string, text: string) => Promise<ApiResponse<unknown>>;
}

/**
 * Confirmation dialog with a comment/reason field, shared by
 * Approve (APPR001), Reject (APPR002) and Cancel (APPR003).
 */
export default function UploadDecisionModal({
    isOpen,
    uploadId,
    datasetName,
    onClose,
    onSuccess,
    config,
}: UploadDecisionModalProps & { config: DecisionConfig }) {
    const [text, setText] = useState("");
    const [touched, setTouched] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    // Synchronous guard so rapid double-clicks cannot fire two requests
    const inFlightRef = useRef(false);

    const trimmed = text.trim();
    const isInvalid = config.isRequired && trimmed.length === 0;
    const fieldId = `${config.id}-text`;

    const reset = () => {
        setText("");
        setTouched(false);
        setErrorMessage("");
    };

    const handleClose = () => {
        if (inFlightRef.current) return;
        reset();
        onClose();
    };

    const handleSubmit = async (e: SyntheticEvent) => {
        e.preventDefault();
        if (isInvalid || inFlightRef.current || !uploadId) return;

        inFlightRef.current = true;
        setIsSubmitting(true);
        setErrorMessage("");

        try {
            const response = await config.submit(uploadId, trimmed);
            if (response?.code === "000000") {
                reset();
                onSuccess(config.successMessage);
            } else {
                const msg = response?.message;
                setErrorMessage(typeof msg === "string" && msg ? msg : (typeof msg === "object" && msg?.messageBody) || config.failureMessage);
            }
        } catch (error) {
            console.error(`${config.title} failed:`, error);
            setErrorMessage(getApprovalErrorMessage(error, config.failureMessage));
        } finally {
            inFlightRef.current = false;
            setIsSubmitting(false);
        }
    };

    return (
        <ApprovalModalShell isOpen={isOpen} title={config.title} onClose={handleClose} isBusy={isSubmitting}>
            <form onSubmit={handleSubmit} noValidate>
                <ModalErrorBanner message={errorMessage} />

                <p style={{ margin: "0 0 18px 0", fontSize: "14px", color: "#334155", lineHeight: 1.6 }}>
                    {config.prompt}
                    {datasetName && (
                        <>
                            <br />
                            <b style={{ color: "#0f172a" }}>{datasetName}</b>
                        </>
                    )}
                </p>

                <div className="form-row" style={{ width: "100%", display: "block", marginBottom: "24px" }}>
                    <label
                        htmlFor={fieldId}
                        style={{ display: "block", marginBottom: "8px", textAlign: "left", fontWeight: 500, fontSize: "14px", color: "#334155" }}
                    >
                        {config.fieldLabel}{" "}
                        {config.isRequired ? (
                            <span className="req" style={{ color: "#ef4444" }}>*</span>
                        ) : (
                            <span className="cell-sub">(optional)</span>
                        )}
                    </label>
                    <textarea
                        id={fieldId}
                        className="input"
                        placeholder={config.fieldPlaceholder}
                        style={{
                            resize: "vertical",
                            minHeight: "110px",
                            fontFamily: "inherit",
                            width: "100%",
                            boxSizing: "border-box",
                            borderRadius: "8px",
                            borderColor: touched && isInvalid ? "#ef4444" : undefined,
                        }}
                        value={text}
                        onChange={(e) => setText(e.target.value)}
                        onBlur={() => setTouched(false)}
                        disabled={isSubmitting}
                        aria-required={config.isRequired}
                        aria-invalid={touched && isInvalid}
                        autoFocus
                    />
                    
                </div>

                <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px" }}>
                    <button type="button" className="btn btn-ghost" onClick={handleClose} disabled={isSubmitting} style={{ borderRadius: "8px" }}>
                        {config.dismissLabel ?? "Cancel"}
                    </button>
                    <button
                        type="submit"
                        className={`btn ${config.confirmClassName}`}
                        disabled={isSubmitting || isInvalid}
                        style={{
                            borderRadius: "8px",
                            minWidth: "100px",
                            opacity: isSubmitting || isInvalid ? 0.6 : 1,
                            cursor: isSubmitting || isInvalid ? "not-allowed" : "pointer",
                        }}
                    >
                        {isSubmitting ? config.busyLabel : config.confirmLabel}
                    </button>
                </div>
            </form>
        </ApprovalModalShell>
    );
}

export type { DecisionConfig };
