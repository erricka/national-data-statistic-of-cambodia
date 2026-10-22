import { useEffect, useRef, useState } from "react";
import Icon from "../../icons/Icon";
import type { TemplateDropdownItem } from "../../service/admin/dataset-service/templateListDropdownService"; // Adjust import path if needed

interface Props {
    value: string;
    onChange: (templateId: string) => void;
    options: TemplateDropdownItem[];
    isLoading?: boolean;
}

export default function SearchableTemplateSelect({
    value,
    onChange,
    options,
    isLoading = false,
}: Props) {
    const [isOpen, setIsOpen] = useState(false);
    const [searchTerm, setSearchTerm] = useState("");
    const containerRef = useRef<HTMLDivElement>(null);

    // Selected template mapped from dynamic API options
    const selectedTemplate = options.find((t) => String(t.source_id) === String(value));

    // Filter options by source_id, source_name, or category_description
    const filteredTemplates = options.filter(
        (t) =>
            String(t.source_id).toLowerCase().includes(searchTerm.toLowerCase()) ||
            (t.source_name && t.source_name.toLowerCase().includes(searchTerm.toLowerCase())) ||
            (t.category_description && t.category_description.toLowerCase().includes(searchTerm.toLowerCase()))
    );

    // Close dropdown on outside click
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    return (
        <div ref={containerRef} style={{ position: "relative", width: "100%" }}>
            {/* Trigger Box */}
            <div
                onClick={() => setIsOpen(!isOpen)}
                style={{
                    width: "100%",
                    padding: "10px 12px",
                    border: isOpen ? "1px solid var(--primary, #2563eb)" : "1px solid #e2e8f0",
                    borderRadius: "8px",
                    fontSize: "14px",
                    backgroundColor: "#ffffff",
                    cursor: "pointer",
                    height: "46px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    boxShadow: isOpen ? "0 0 0 3px rgba(37, 99, 235, 0.1)" : "none",
                    transition: "all 0.15s ease"
                }}
            >
                {selectedTemplate ? (
                    <div style={{ display: "flex", alignItems: "center", gap: "8px", overflow: "hidden" }}>
                        <span
                            style={{
                                fontSize: "11px",
                                fontFamily: "monospace",
                                fontWeight: 700,
                                color: "#2563eb",
                                background: "#eff6ff",
                                padding: "2px 6px",
                                borderRadius: "4px",
                                border: "1px solid #bfdbfe",
                                flexShrink: 0
                            }}
                        >
                            {selectedTemplate.source_id}
                        </span>
                        <span style={{ color: "#0f172a", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                            {selectedTemplate.source_name}
                        </span>
                    </div>
                ) : (
                    <span style={{ color: "#94a3b8" }}>
                        {isLoading ? "Loading templates..." : "Select template..."}
                    </span>
                )}
                <span style={{ pointerEvents: "none", display: "inline-flex", color: "#64748b" }}>
                    <Icon name="chevron-down" />
                </span>
            </div>

            {/* Dropdown Menu */}
            {isOpen && (
                <div
                    style={{
                        position: "absolute",
                        top: "calc(100% + 4px)",
                        left: 0,
                        right: 0,
                        backgroundColor: "#ffffff",
                        border: "1px solid #cbd5e1",
                        borderRadius: "8px",
                        boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)",
                        zIndex: 1000,
                        maxHeight: "320px",
                        display: "flex",
                        flexDirection: "column",
                        overflow: "hidden"
                    }}
                >
                    {/* Search Input Field */}
                    <div style={{ padding: "8px", borderBottom: "1px solid #f1f5f9", backgroundColor: "#f8fafc" }}>
                        <div style={{ position: "relative", display: "flex", alignItems: "center" }}>
                            <span style={{ position: "absolute", left: "10px", color: "#94a3b8", display: "inline-flex" }}>
                                <Icon name="search" />
                            </span>
                            <input
                                type="text"
                                placeholder="Search template code or name..."
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                autoFocus
                                style={{
                                    width: "100%",
                                    padding: "8px 10px 8px 32px",
                                    border: "1px solid #cbd5e1",
                                    borderRadius: "6px",
                                    fontSize: "13px",
                                    outline: "none",
                                    backgroundColor: "#ffffff"
                                }}
                            />
                            {searchTerm && (
                                <button
                                    type="button"
                                    onClick={() => setSearchTerm("")}
                                    style={{
                                        position: "absolute",
                                        right: "8px",
                                        background: "none",
                                        border: "none",
                                        color: "#94a3b8",
                                        cursor: "pointer",
                                        display: "inline-flex"
                                    }}
                                >
                                    <Icon name="x" />
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Options List */}
                    <div style={{ overflowY: "auto", padding: "4px 0" }}>
                        {filteredTemplates.length > 0 ? (
                            filteredTemplates.map((item) => {
                                const isSelected = String(item.source_id) === String(value);
                                return (
                                    <div
                                        key={item.source_id}
                                        onClick={() => {
                                            onChange(String(item.source_id));
                                            setIsOpen(false);
                                            setSearchTerm("");
                                        }}
                                        style={{
                                            padding: "10px 12px",
                                            cursor: "pointer",
                                            backgroundColor: isSelected ? "#eff6ff" : "transparent",
                                            display: "flex",
                                            flexDirection: "column",
                                            gap: "2px",
                                            borderLeft: isSelected ? "3px solid #2563eb" : "3px solid transparent",
                                            transition: "background-color 0.1s ease"
                                        }}
                                        onMouseEnter={(e) => {
                                            if (!isSelected) e.currentTarget.style.backgroundColor = "#f8fafc";
                                        }}
                                        onMouseLeave={(e) => {
                                            if (!isSelected) e.currentTarget.style.backgroundColor = "transparent";
                                        }}
                                    >
                                        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                                            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                                                <span
                                                    style={{
                                                        fontSize: "11px",
                                                        fontFamily: "monospace",
                                                        fontWeight: 700,
                                                        color: isSelected ? "#1d4ed8" : "#475569",
                                                        background: isSelected ? "#bfdbfe" : "#f1f5f9",
                                                        padding: "1px 6px",
                                                        borderRadius: "4px"
                                                    }}
                                                >
                                                    {item.source_id}
                                                </span>
                                                <span style={{ fontSize: "13px", fontWeight: 600, color: "#0f172a" }}>
                                                    {item.source_name}
                                                </span>
                                            </div>
                                            {isSelected && (
                                                <span style={{ color: "#2563eb", display: "inline-flex" }}>
                                                    <Icon name="check" />
                                                </span>
                                            )}
                                        </div>
                                        {item.category_description && (
                                            <p style={{ margin: 0, fontSize: "12px", color: "#64748b" }}>
                                                {item.category_description}
                                            </p>
                                        )}
                                    </div>
                                );
                            })
                        ) : (
                            <div style={{ padding: "16px", textAlign: "center", fontSize: "13px", color: "#94a3b8" }}>
                                {isLoading
                                    ? "Loading templates..."
                                    : `No templates found for "${searchTerm}"`}
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}