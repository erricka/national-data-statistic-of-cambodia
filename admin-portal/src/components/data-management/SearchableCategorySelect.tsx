import { useEffect, useRef, useState } from "react";
import Icon from "../../icons/Icon";
import type { CategoryDropdownItem } from "../../service/admin/dataset-service/categoryListDropdownService";

interface Props {
    value: string;
    onChange: (categoryId: string) => void;
    options: CategoryDropdownItem[];
    isLoading?: boolean;
}

export default function SearchableCategorySelect({ value, onChange, options, isLoading = false }: Props) {
    const [isOpen, setIsOpen] = useState(false);
    const [searchTerm, setSearchTerm] = useState("");
    const containerRef = useRef<HTMLDivElement>(null);

    // Selected Category Item mapped from API
    const selectedCategory = options.find((c) => String(c.categoryId) === String(value));

    // Filter by Category ID, Category Name, or Description
    const filteredCategories = options.filter(
        (c) =>
            String(c.categoryId).toLowerCase().includes(searchTerm.toLowerCase()) ||
            (c.categoryName && c.categoryName.toLowerCase().includes(searchTerm.toLowerCase())) ||
            (c.description && c.description.toLowerCase().includes(searchTerm.toLowerCase()))
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
                {selectedCategory ? (
                    <div style={{ display: "flex", alignItems: "center", gap: "8px", overflow: "hidden" }}>
                        <span
                            style={{
                                fontSize: "11px",
                                fontFamily: "monospace",
                                fontWeight: 700,
                                color: "#059669",
                                background: "#ecfdf5",
                                padding: "2px 6px",
                                borderRadius: "4px",
                                border: "1px solid #a7f3d0",
                                flexShrink: 0
                            }}
                        >
                            {selectedCategory.categoryId}
                        </span>
                        <span style={{ color: "#0f172a", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                            {selectedCategory.categoryName}
                        </span>
                    </div>
                ) : (
                    <span style={{ color: "#94a3b8" }}>
                        {isLoading ? "Loading categories..." : "Select category..."}
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
                                placeholder="ស្វែងរកក្រសួង (Search category or code)..."
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
                        {filteredCategories.length > 0 ? (
                            filteredCategories.map((item) => {
                                const isSelected = String(item.categoryId) === String(value);
                                return (
                                    <div
                                        key={item.categoryId}
                                        onClick={() => {
                                            onChange(String(item.categoryId));
                                            setIsOpen(false);
                                            setSearchTerm("");
                                        }}
                                        style={{
                                            padding: "10px 12px",
                                            cursor: "pointer",
                                            backgroundColor: isSelected ? "#ecfdf5" : "transparent",
                                            display: "flex",
                                            flexDirection: "column",
                                            gap: "2px",
                                            borderLeft: isSelected ? "3px solid #059669" : "3px solid transparent",
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
                                                        color: isSelected ? "#047857" : "#475569",
                                                        background: isSelected ? "#a7f3d0" : "#f1f5f9",
                                                        padding: "1px 6px",
                                                        borderRadius: "4px"
                                                    }}
                                                >
                                                    {item.categoryId}
                                                </span>
                                                <span style={{ fontSize: "13px", fontWeight: 600, color: "#0f172a" }}>
                                                    {item.categoryName}
                                                </span>
                                            </div>
                                            {isSelected && (
                                                <span style={{ color: "#059669", display: "inline-flex" }}>
                                                    <Icon name="check" />
                                                </span>
                                            )}
                                        </div>
                                        {item.description && (
                                            <p style={{ margin: 0, fontSize: "12px", color: "#64748b" }}>
                                                {item.description}
                                            </p>
                                        )}
                                    </div>
                                );
                            })
                        ) : (
                            <div style={{ padding: "16px", textAlign: "center", fontSize: "13px", color: "#94a3b8" }}>
                                {isLoading ? "កំពុងទាញយកទិន្នន័យ... (Loading...)" : `រកមិនឃើញប្រភេទ " ${searchTerm} " ទេ (No categories found)`}
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}