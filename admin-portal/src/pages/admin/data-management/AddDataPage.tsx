import React, { useRef, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";
import SearchableTemplateSelect from "../../../components/data-management/SearchableTemplateSelect";
import SearchableCategorySelect from "../../../components/data-management/SearchableCategorySelect";
import { addDataset } from "../../../service/admin/dataset-service/addDatasetService";
import { getErrorMessage } from "../../../utils/errorHandler";
import {
    getCategoryListDropdown,
    type CategoryDropdownItem,
} from "../../../service/admin/dataset-service/categoryListDropdownService";
import {
    getTemplateListDropdown,
    type TemplateDropdownItem,
} from "../../../service/admin/dataset-service/templateListDropdownService";

export default function AddDataPage(): React.ReactElement {
    const navigate = useNavigate();
    const fileInputRef = useRef<HTMLInputElement | null>(null);

    // Form Field States
    const [title, setTitle] = useState<string>("");
    const [category, setCategory] = useState<string>("");
    const [template, setTemplate] = useState<string>("");
    const [description, setDescription] = useState<string>("");

    // Dynamic Categories State
    const [categories, setCategories] = useState<CategoryDropdownItem[]>([]);
    const [isLoadingCategories, setIsLoadingCategories] = useState<boolean>(false);

    // Dynamic Templates State
    const [templates, setTemplates] = useState<TemplateDropdownItem[]>([]);
    const [isLoadingTemplates, setIsLoadingTemplates] = useState<boolean>(false);

    // UI Loading & Alert States
    const [isSaving, setIsSaving] = useState<boolean>(false);
    const [errorMessage, setErrorMessage] = useState<string>("");
    const [successMessage, setSuccessMessage] = useState<string>("");

    // File Upload States
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [uploadProgress, setUploadProgress] = useState<number>(0);
    const [isUploading, setIsUploading] = useState<boolean>(false);
    const [isDragging, setIsDragging] = useState<boolean>(false);

    // Fetch Categories Dropdown on Component Mount
    useEffect(() => {
        const fetchCategories = async () => {
            setIsLoadingCategories(true);
            try {
                const response = await getCategoryListDropdown({ search: "" });
                if (response.code === "000000" && response.data?.list) {
                    setCategories(response.data.list);
                } else {
                    setCategories([]);
                }
            } catch (error) {
                console.error("Failed to load categories:", error);
                setCategories([]);
            } finally {
                setIsLoadingCategories(false);
            }
        };

        fetchCategories();
    }, []);

    // Clear and fetch template options whenever category changes
    useEffect(() => {
        setTemplates([]);
        setTemplate("");

        if (!category) {
            return;
        }

        const fetchTemplates = async () => {
            setIsLoadingTemplates(true);
            try {
                const response = await getTemplateListDropdown({
                    categoryId: category,
                    page: 0,
                    size: 100,
                });

                if (response.code === "000000" && response.data?.list) {
                    const list = response.data.list;
                    setTemplates(list);

                    if (list.length > 0) {
                        setTemplate(String(list[0].source_id));
                    }
                } else {
                    setTemplates([]);
                    setTemplate("");
                }
            } catch (error) {
                console.error("Failed to load templates:", error);
                setTemplates([]);
                setTemplate("");
            } finally {
                setIsLoadingTemplates(false);
            }
        };

        fetchTemplates();
    }, [category]);

    // Handle Category change wrapper
    const handleCategoryChange = (newCategoryId: string) => {
        setCategory(newCategoryId);
        setTemplate("");
        setTemplates([]);
    };

    // Handles picking a single file and triggering the upload animation
    const handleFileSelected = (file: File): void => {
        setSelectedFile(file);
        setIsUploading(true);
        setUploadProgress(0);

        let currentProgress = 0;
        const interval = setInterval(() => {
            currentProgress += 10;
            setUploadProgress(currentProgress);
            if (currentProgress >= 100) {
                clearInterval(interval);
                setIsUploading(false);
            }
        }, 60);
    };

    const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>): void => {
        if (e.target.files && e.target.files.length > 0) {
            handleFileSelected(e.target.files[0]);
        }
    };

    const handleDrop = (e: React.DragEvent<HTMLDivElement>): void => {
        e.preventDefault();
        setIsDragging(false);

        if (selectedFile) return;

        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            handleFileSelected(e.dataTransfer.files[0]);
        }
    };

    const handleRemoveFile = (): void => {
        setSelectedFile(null);
        setUploadProgress(0);
        setIsUploading(false);
        if (fileInputRef.current) {
            fileInputRef.current.value = "";
        }
    };

    const handleFormSubmit = async (e: React.FormEvent<HTMLFormElement>): Promise<void> => {
        e.preventDefault();

        if (!category) {
            setErrorMessage("Please select a category.");
            return;
        }

        if (!template) {
            setErrorMessage("Please select a template.");
            return;
        }

        if (!selectedFile) {
            setErrorMessage("Please browse and select a file first!");
            return;
        }

        setIsSaving(true);
        setErrorMessage("");
        setSuccessMessage("");

        try {
            const response = await addDataset({
                file: selectedFile,
                sourceId: template,
                sourceName: title,
                ownerId: "ADMIN",
                mode: "APPEND",
                conflictColumn: "",
                category,
                description
            });

            if (response?.code && response.code !== "000000") {
                setErrorMessage(getErrorMessage(response));
                return;
            }

            setSuccessMessage("Dataset created successfully!");

            setTimeout(() => navigate("/admin/data-management"), 400);

        } catch (err: any) {
            console.error("Error creating dataset:", err);
            setErrorMessage(getErrorMessage(err));
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="app">
            <Sidebar active="data_management" />

            <div className="main" style={{ width: "100%", overflowX: "hidden" }}>
                <Topbar pageTitle="Add New Dataset" />

                <main className="content" style={{ backgroundColor: "#fdfdfd", padding: "clamp(12px, 3vw, 24px)", maxWidth: "100%", boxSizing: "border-box" }}>

                    <style>{`
                        .responsive-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                            gap: 20px;
                            width: 100%;
                        }
                        .responsive-grid > div {
                            min-width: 0;
                        }
                    `}</style>

                    <form onSubmit={handleFormSubmit} style={{ display: "flex", flexDirection: "column", gap: "20px", width: "100%", boxSizing: "border-box" }}>

                        {/* Page Header */}
                        <div className="page-head" style={{ marginBottom: "8px" }}>
                            <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "flex-start" }}>
                                <button
                                    type="button"
                                    className="btn btn-outline btn-sm"
                                    onClick={() => navigate("/admin/data-management")}
                                    style={{ padding: "6px 10px" }}
                                >
                                    <Icon name="arrow-left" /> Back
                                </button>

                                <div>
                                    <h1 style={{ margin: "0 0 4px 0", fontSize: "clamp(20px, 4vw, 28px)" }}>Add Dataset</h1>
                                    <p className="cell-sub" style={{ margin: 0, color: "#64748b", fontSize: "14px" }}>
                                        Fill in the details below to initialize, organize, and register your new content database repository.
                                    </p>
                                </div>
                            </div>
                        </div>

                        {/* Error Alert */}
                        {errorMessage && (
                            <div style={{
                                backgroundColor: "#fef2f2",
                                color: "var(--danger, #ef4444)",
                                border: "1px solid #fecaca",
                                padding: "12px 16px",
                                borderRadius: "6px",
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
                                fontSize: "14px",
                                fontWeight: 500
                            }}>
                                ✓ {successMessage} Redirecting...
                            </div>
                        )}

                        {/* Row 1: Title & Status */}
                        <div className="responsive-grid">
                            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                <label style={{ fontSize: "14px", fontWeight: 500, color: "#475569" }}>
                                    Title <span style={{ color: "var(--danger)" }}>*</span>
                                </label>
                                <input
                                    type="text"
                                    placeholder="Dataset Title..."
                                    value={title}
                                    required
                                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setTitle(e.target.value)}
                                    style={{ width: "100%", boxSizing: "border-box", padding: "10px 12px", border: "1px solid #e2e8f0", borderRadius: "8px", fontSize: "14px", backgroundColor: "#ffffff" }}
                                />
                            </div>


                        </div>

                        {/* Row 2: Categories & Template */}
                        <div className="responsive-grid">
                            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                <label style={{ fontSize: "14px", fontWeight: 500, color: "#475569" }}>
                                    Categories <span style={{ color: "var(--danger)" }}>*</span>
                                </label>
                                <SearchableCategorySelect
                                    value={category}
                                    onChange={handleCategoryChange}
                                    options={categories}
                                    isLoading={isLoadingCategories}
                                />
                            </div>

                            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                                <label style={{ fontSize: "14px", fontWeight: 500, color: "#475569" }}>
                                    Template <span style={{ color: "var(--danger)" }}>*</span>
                                </label>
                                <SearchableTemplateSelect
                                    key={category} // Forces internal component reset (search text + dropdown state) when category changes
                                    value={template}
                                    onChange={(id: string) => setTemplate(id)}
                                    options={templates}
                                    isLoading={isLoadingTemplates}
                                />
                            </div>
                        </div>

                        {/* Description */}
                        <div style={{ display: "flex", flexDirection: "column", gap: "8px", width: "100%" }}>
                            <label style={{ fontSize: "14px", fontWeight: 500, color: "#475569" }}>Description</label>
                            <textarea
                                value={description}
                                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setDescription(e.target.value)}
                                rows={3}
                                style={{ width: "100%", boxSizing: "border-box", padding: "12px", border: "1px solid #e2e8f0", borderRadius: "8px", fontSize: "14px", resize: "vertical", backgroundColor: "#ffffff" }}
                            />
                        </div>

                        {/* DROPZONE SINGLE FILE BROWSE CONTAINER */}
                        <section style={{ width: "100%" }}>
                            <div className="card card-pad" style={{ padding: "clamp(16px, 3vw, 24px)", boxSizing: "border-box" }}>
                                <input
                                    type="file"
                                    ref={fileInputRef}
                                    accept=".csv, .xlsx, .xls, .pdf, .docx"
                                    multiple={false}
                                    style={{ display: "none" }}
                                    onChange={handleFileInputChange}
                                />

                                <div
                                    className="dropzone"
                                    onDragOver={(e: React.DragEvent<HTMLDivElement>) => {
                                        e.preventDefault();
                                        if (!selectedFile) setIsDragging(true);
                                    }}
                                    onDragLeave={() => setIsDragging(false)}
                                    onDrop={handleDrop}
                                    onClick={() => {
                                        if (!selectedFile) fileInputRef.current?.click();
                                    }}
                                    style={{
                                        cursor: selectedFile ? "default" : "pointer",
                                        border: isDragging ? "2px dashed #2563eb" : "2px dashed #cbd5e1",
                                        backgroundColor: selectedFile ? "#f8fafc" : isDragging ? "#eff6ff" : "#ffffff",
                                        padding: "32px 16px",
                                        borderRadius: "12px",
                                        textAlign: "center",
                                        position: "relative",
                                        overflow: "hidden",
                                        width: "100%",
                                        boxSizing: "border-box",
                                        opacity: selectedFile ? 0.6 : 1
                                    }}
                                >
                                    <div className="dz-ic" style={{ marginBottom: "8px" }}>
                                        <Icon name="folder" />
                                    </div>

                                    <h3 style={{ fontSize: "16px", fontWeight: 600, margin: "0 0 4px" }}>
                                        {selectedFile ? "File attachment selected" : "Drag & drop your file here"}
                                    </h3>
                                    <p className="muted" style={{ margin: "0 0 16px", fontSize: "13px", color: "#64748b" }}>
                                        {selectedFile ? "Remove current file to select a different one" : "or click to browse · Single file only (.xlsx, .xls, .csv up to 50 MB)"}
                                    </p>

                                    <button
                                        type="button"
                                        className="btn btn-primary"
                                        disabled={Boolean(selectedFile)}
                                        onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
                                            e.stopPropagation();
                                            fileInputRef.current?.click();
                                        }}
                                    >
                                        Browse File
                                    </button>

                                    {/* UPLOADING PROGRESS BAR */}
                                    {(isUploading || uploadProgress > 0) && (
                                        <div style={{ marginTop: "20px", width: "100%", maxWidth: "400px", marginInline: "auto" }}>
                                            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "12px", fontWeight: 600, color: "#475569", marginBottom: "6px" }}>
                                                <span>{isUploading ? "Uploading file..." : "File Uploaded"}</span>
                                                <span>{uploadProgress}%</span>
                                            </div>

                                            <div style={{ width: "100%", height: "6px", backgroundColor: "#e2e8f0", borderRadius: "3px", overflow: "hidden" }}>
                                                <div
                                                    style={{
                                                        width: `${uploadProgress}%`,
                                                        height: "100%",
                                                        backgroundColor: uploadProgress === 100 ? "#10b981" : "#2563eb",
                                                        transition: "width 0.15s ease-out"
                                                    }}
                                                />
                                            </div>
                                        </div>
                                    )}
                                </div>

                                {/* SINGLE FILE PREVIEW CARD */}
                                {selectedFile && !isUploading && (
                                    <>
                                        <div className="divider" style={{ margin: "20px 0", borderTop: "1px solid #e2e8f0" }}></div>
                                        <div className="sec-title" style={{ fontSize: "13px", fontWeight: 600, color: "#64748b", marginBottom: "10px" }}>
                                            Selected File
                                        </div>

                                        <div style={{
                                            padding: "12px 16px",
                                            border: "1px solid #e2e8f0",
                                            borderRadius: "10px",
                                            backgroundColor: "#f8fafc",
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "space-between",
                                            flexWrap: "wrap",
                                            gap: "12px"
                                        }}>
                                            <div style={{ display: "flex", alignItems: "center", gap: "12px", minWidth: 0, flex: "1 1 200px" }}>
                                                <span style={{ background: "#dcfce7", color: "#166534", padding: "8px", borderRadius: "8px", display: "inline-flex", flexShrink: 0 }}>
                                                    <Icon name="file" />
                                                </span>
                                                <div style={{ minWidth: 0 }}>
                                                    <div style={{ fontWeight: 600, fontSize: "14px", color: "#0f172a", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                                        {selectedFile.name}
                                                    </div>
                                                </div>
                                            </div>

                                            <div style={{ display: "flex", alignItems: "center", gap: "12px", flexShrink: 0 }}>
                                                <span className="badge badge-success" style={{ background: "#dcfce7", color: "#15803d", padding: "4px 8px", borderRadius: "6px", fontSize: "12px", fontWeight: 600 }}>
                                                    ● Ready to save
                                                </span>
                                                <button
                                                    type="button"
                                                    title="Remove File"
                                                    onClick={handleRemoveFile}
                                                    style={{ color: "#ef4444", cursor: "pointer", background: "none", border: "none" }}
                                                >
                                                    <Icon name="trash" />
                                                </button>
                                            </div>
                                        </div>
                                    </>
                                )}
                            </div>
                        </section>

                        {/* Actions */}
                        <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px", marginTop: "12px", flexWrap: "wrap" }}>
                            <button
                                type="button"
                                className="btn btn-ghost"
                                onClick={() => navigate("/admin/data-management")}
                                disabled={isSaving}
                            >
                                Cancel
                            </button>
                            <button
                                type="submit"
                                className="btn btn-primary"
                                disabled={isSaving || isUploading || !selectedFile}
                            >
                                {isSaving ? "Saving..." : "Submit"}
                            </button>
                        </div>

                    </form>
                </main>
            </div>
        </div>
    );
}