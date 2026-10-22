import { useState } from "react";
import type { SyntheticEvent } from "react";
import { createPortal } from "react-dom";
import Icon from "../../../../icons/Icon";
import { createCategory } from "../../../../service/admin/category-service/createCategoryService";

interface AddCategoryModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess?: () => void;
}

export default function AddCategoryModal({
    isOpen,
    onClose,
    onSuccess
}: AddCategoryModalProps) {
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");

    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [showSuccessToast, setShowSuccessToast] = useState(false);

    if (!isOpen) return null;

    const resetForm = () => {
        setName("");
        setDescription("");
        setErrorMessage("");
    };

    const handleCloseModal = () => {
        resetForm();
        onClose();
    };

    const handleSubmit = async (e: SyntheticEvent) => {
        e.preventDefault();

        setIsSubmitting(true);
        setErrorMessage("");

        try {
            const response = await createCategory({
                categoryName: name.trim(),
                description: description.trim(),
                status: "01"
            });

            if (response.code === "000000" && response.data?.success) {
                setShowSuccessToast(true);

                setTimeout(() => {
                    setShowSuccessToast(false);
                    resetForm();
                    if (onSuccess) onSuccess();
                    onClose();
                }, 1200);
            } else {
                const msg = response.message;
                const errorStr =
                    typeof msg === "string"
                        ? msg
                        : msg?.messageBody || "Failed to create category.";
                setErrorMessage(errorStr);
            }
        } catch (error) {
            console.error("Create category failed:", error);
            setErrorMessage("An unexpected error occurred while creating category.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return createPortal(
        <>
            {/* Success Toast Banner */}
            {showSuccessToast && (
                <div
                    style={{
                        position: "fixed",
                        top: "24px",
                        right: "24px",
                        zIndex: 100000,
                        display: "flex",
                        alignItems: "center",
                        gap: "10px",
                        backgroundColor: "#10b981",
                        color: "#ffffff",
                        padding: "12px 20px",
                        borderRadius: "8px",
                        boxShadow: "0 10px 15px -3px rgba(16, 185, 129, 0.3), 0 4px 6px -4px rgba(0, 0, 0, 0.1)",
                        fontSize: "14px",
                        fontWeight: 500,
                        animation: "slideIn 0.25s ease-out"
                    }}
                >
                    <Icon name="check" />
                    <span>Category created successfully!</span>
                </div>
            )}

            {/* Backdrop Layer */}
            <div
                style={{
                    position: "fixed",
                    inset: 0,
                    background: "rgba(15, 23, 42, 0.5)",
                    backdropFilter: "blur(2px)",
                    zIndex: 99998
                }}
                onClick={isSubmitting ? undefined : handleCloseModal}
            />

            {/* Modal Dialog Card */}
            <div
                style={{
                    position: "fixed",
                    top: "50%",
                    left: "50%",
                    transform: "translate(-50%, -50%)",
                    zIndex: 99999,
                    width: "100%",
                    maxWidth: "600px",
                    padding: "0 16px",
                    boxSizing: "border-box"
                }}
            >
                <div
                    style={{
                        background: "#ffffff",
                        borderRadius: "12px",
                        boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)",
                        overflow: "hidden"
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
                            boxSizing: "border-box"
                        }}
                    >
                        <h3 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 600, color: "#0f172a" }}>
                            Add Category
                        </h3>
                        <button
                            className="icon-btn"
                            onClick={handleCloseModal}
                            type="button"
                            disabled={isSubmitting}
                            style={{ height: "auto", width: "auto" }}
                        >
                            <Icon name="x" />
                        </button>
                    </div>

                    {/* Form */}
                    <form onSubmit={handleSubmit} style={{ padding: "24px", boxSizing: "border-box" }}>
                        {errorMessage && (
                            <div
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
                                    fontSize: "14px"
                                }}
                            >
                                <Icon name="alert-circle" />
                                <span>{errorMessage}</span>
                            </div>
                        )}

                        <p className="hint" style={{ fontStyle: "italic", marginBottom: "20px", fontSize: "13px", color: "#64748b" }}>
                            The field labels marked with <span className="req" style={{ color: "#ef4444" }}>*</span> are required input fields.
                        </p>

                        {/* Name Input */}
                        <div className="form-row" style={{ width: "100%", display: "block", marginBottom: "18px" }}>
                            <label htmlFor="cat-name" style={{ display: "block", marginBottom: "8px", textAlign: "left", fontWeight: 500, fontSize: "14px", color: "#334155" }}>
                                Name <span className="req" style={{ color: "#ef4444" }}>*</span>
                            </label>
                            <input
                                id="cat-name"
                                type="text"
                                className="input"
                                style={{ width: "100%", boxSizing: "border-box", borderRadius: "8px" }}
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                disabled={isSubmitting}
                                required
                            />
                        </div>

                        {/* Description Textarea */}
                        <div className="form-row" style={{ width: "100%", display: "block", marginBottom: "24px" }}>
                            <label htmlFor="cat-desc" style={{ display: "block", marginBottom: "8px", textAlign: "left", fontWeight: 500, fontSize: "14px", color: "#334155" }}>
                                Description
                            </label>
                            <textarea
                                id="cat-desc"
                                className="input"
                                style={{ resize: "vertical", minHeight: "110px", fontFamily: "inherit", width: "100%", boxSizing: "border-box", borderRadius: "8px" }}
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                disabled={isSubmitting}
                            />
                        </div>

                        {/* Actions */}
                        <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px" }}>
                            <button
                                type="button"
                                className="btn btn-ghost"
                                onClick={handleCloseModal}
                                disabled={isSubmitting}
                                style={{ borderRadius: "8px" }}
                            >
                                Cancel
                            </button>
                            <button
                                type="submit"
                                className="btn btn-primary"
                                disabled={isSubmitting}
                                style={{ borderRadius: "8px", minWidth: "100px" }}
                            >
                                {isSubmitting ? "Submitting..." : "Submit"}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </>,
        document.body
    );
}