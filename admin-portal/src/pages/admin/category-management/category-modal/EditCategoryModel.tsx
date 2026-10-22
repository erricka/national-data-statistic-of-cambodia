import { useState, useEffect } from "react";
import type { SyntheticEvent } from "react";
import { createPortal } from "react-dom";
import Icon from "../../../../icons/Icon";
import type { ApiCategoryItem } from "../../../../service/admin/category-service/categoryListService";
import { updateCategory } from "../../../../service/admin/category-service/updateCategoryService";

export interface EditCategoryModalProps {
    isOpen: boolean;
    categoryData: ApiCategoryItem | null;
    onClose: () => void;
    onSuccess?: () => void;
    onSubmit?: (data: { name: string; description: string; image?: File | null }) => void;
}

export default function EditCategoryModal({
    isOpen,
    categoryData,
    onClose,
    onSuccess,
    onSubmit
}: EditCategoryModalProps) {
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [status, setStatus] = useState("01");

    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [showSuccessToast, setShowSuccessToast] = useState(false);

    useEffect(() => {
        if (categoryData) {
            setName(categoryData.categoryName || "");
            setDescription(categoryData.description || "");
            setStatus(categoryData.status || "01");
            setErrorMessage("");
        }
    }, [categoryData]);

    if (!isOpen) return null;

    const handleSubmit = async (e: SyntheticEvent) => {
        e.preventDefault();

        if (onSubmit && !onSuccess) {
            onSubmit({ name, description });
            onClose();
            return;
        }

        if (!categoryData) return;

        setIsSubmitting(true);
        setErrorMessage("");

        try {
            const response = await updateCategory({
                categoryId: categoryData.categoryId,
                categoryName: name.trim(),
                description: description.trim(),
                status: status
            });

            if (response.code === "000000" && response.data?.success) {

                setShowSuccessToast(true);

                setTimeout(() => {
                    setShowSuccessToast(false);
                    if (onSuccess) onSuccess();
                    onClose();
                }, 1200);
            } else {
                const msg = response.message;
                const errorStr =
                    typeof msg === "string"
                        ? msg
                        : msg?.messageBody || "Failed to update category.";
                setErrorMessage(errorStr);
            }
        } catch (error) {
            console.error("Update category failed:", error);
            setErrorMessage("An unexpected error occurred while saving changes.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return createPortal(
        <>
            {/* Styled Success Toast Notification */}
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
                    <span>Category updated successfully!</span>
                </div>
            )}

            {/* Modal Overlay */}
            <div
                style={{
                    position: "fixed",
                    inset: 0,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    zIndex: 99999,
                    padding: "16px",
                    textAlign: "left",
                    boxSizing: "border-box"
                }}
            >
                {/* Backdrop Mask */}
                <div
                    style={{
                        position: "absolute",
                        inset: 0,
                        background: "rgba(15, 23, 42, 0.5)",
                        backdropFilter: "blur(2px)",
                        zIndex: -1
                    }}
                    onClick={isSubmitting ? undefined : onClose}
                />

                {/* Main Card */}
                <div
                    style={{
                        position: "relative",
                        width: "100%",
                        maxWidth: "600px",
                        background: "#ffffff",
                        borderRadius: "12px",
                        boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)",
                        boxSizing: "border-box",
                        overflow: "hidden",
                        zIndex: 1
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
                        <h3 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 600, color: "#0f172a" }}>Edit Category</h3>
                        <button
                            className="icon-btn"
                            onClick={onClose}
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
                            <label htmlFor="edit-cat-name" style={{ display: "block", marginBottom: "8px", textAlign: "left", fontWeight: 500, fontSize: "14px", color: "#334155" }}>
                                Name <span className="req" style={{ color: "#ef4444" }}>*</span>
                            </label>
                            <input
                                id="edit-cat-name"
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
                        <div className="form-row" style={{ width: "100%", display: "block", marginBottom: "18px" }}>
                            <label htmlFor="edit-cat-desc" style={{ display: "block", marginBottom: "8px", textAlign: "left", fontWeight: 500, fontSize: "14px", color: "#334155" }}>
                                Description
                            </label>
                            <textarea
                                id="edit-cat-desc"
                                className="input"
                                style={{ resize: "vertical", minHeight: "110px", fontFamily: "inherit", width: "100%", boxSizing: "border-box", borderRadius: "8px" }}
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                disabled={isSubmitting}
                            />
                        </div>

                        {/* Status Select */}
                        <div className="form-row" style={{ width: "100%", display: "block", marginBottom: "20px" }}>
                            <label htmlFor="edit-cat-status" style={{ display: "block", marginBottom: "8px", textAlign: "left", fontWeight: 500, fontSize: "14px", color: "#334155" }}>
                                Status
                            </label>
                            <select
                                id="edit-cat-status"
                                className="input"
                                value={status}
                                onChange={(e) => setStatus(e.target.value)}
                                disabled={isSubmitting}
                                style={{ width: "100%", boxSizing: "border-box", borderRadius: "8px" }}
                            >
                                <option value="01">Active</option>
                                <option value="09">Inactive</option>
                            </select>
                        </div>


                        <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px", marginTop: "24px" }}>
                            <button
                                type="button"
                                className="btn btn-ghost"
                                onClick={onClose}
                                disabled={isSubmitting}
                                style={{ borderRadius: "8px" }}
                            >
                                Cancel
                            </button>
                            <button
                                type="submit"
                                className="btn btn-primary"
                                disabled={isSubmitting}
                                style={{ borderRadius: "8px", minWidth: "120px" }}
                            >
                                {isSubmitting ? "Saving..." : "Save Changes"}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </>,
        document.body
    );
}