import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import NavBar from "../../../layout/NavBar";
import {
    getCategorySummary,
    getCategoryList,
    type CategoryListItem,
} from "../../../service/client/categories/categoriesService";

export default function CategoriesPage() {
    const navigate = useNavigate();

    const [totalCategories, setTotalCategories] = useState<number>(0);
    const [totalDatasets, setTotalDatasets] = useState<number>(0);

    const [searchQuery, setSearchQuery] = useState<string>("");
    const [categories, setCategories] = useState<CategoryListItem[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const isInitialMount = useRef(true);

    useEffect(() => {
        const fetchInitialData = async () => {
            try {
                setLoading(true);
                setError(null);

                const [summaryRes, listRes] = await Promise.all([
                    getCategorySummary(),
                    getCategoryList({ search: "", page: 0, size: 50 }),
                ]);

                if (summaryRes.code === "000000" && summaryRes.data?.list) {
                    setTotalCategories(summaryRes.data.list.totalCategory || 0);
                    setTotalDatasets(summaryRes.data.list.totalDatasource || 0);
                }

                if (listRes.code === "000000" && listRes.data) {
                    setCategories(listRes.data.list || []);
                } else {
                    const errorMsg =
                        typeof listRes.message === "object" && listRes.message !== null
                            ? listRes.message.messageTitle
                            : listRes.message || "Failed to fetch categories.";
                    setError(errorMsg);
                }
            } catch (err: any) {
                setError(err.message || "An unexpected error occurred.");
            } finally {
                setLoading(false);
            }
        };

        fetchInitialData();
    }, []);

    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false;
            return;
        }

        const handler = setTimeout(async () => {
            try {
                setLoading(true);
                setError(null);

                const res = await getCategoryList({
                    search: searchQuery.trim(),
                    page: 0,
                    size: 50,
                });

                if (res.code === "000000" && res.data) {
                    setCategories(res.data.list || []);
                } else {
                    const errorMsg =
                        typeof res.message === "object" && res.message !== null
                            ? res.message.messageTitle
                            : res.message || "Failed to search categories.";
                    setError(errorMsg);
                }
            } catch (err: any) {
                setError(err.message || "An unexpected error occurred.");
            } finally {
                setLoading(false);
            }
        }, 300);

        return () => clearTimeout(handler);
    }, [searchQuery]);

    return (
        <div className="about-page-wrapper">
            <NavBar />

            <main className="about-container">
                <header className="categories-workspace-header">
                    <div className="header-text-content">
                        <h1 className="workspace-title">
                            Browse Datasets by Category
                        </h1>
                        <p className="workspace-desc">
                            Explore open government datasets grouped by ministries, public domains, and national economic sectors.
                        </p>
                    </div>

                    {/* Right-Side Directory Mini-Stats */}
                    <div className="header-directory-stats">
                        <div className="dir-stat-box">
                            <b>{totalCategories}</b>
                            <span>Primary Sectors</span>
                        </div>
                        <div className="dir-stat-box highlight-stat">
                            <b>{totalDatasets.toLocaleString()}</b>
                            <span>Total Datasets</span>
                        </div>
                    </div>
                </header>

                {/* Filter & Search Bar */}
                <div className="categories-filter-bar">
                    <div className="cat-search-box">
                        <Icon name="search" />
                        <input
                            type="text"
                            placeholder="Filter categories..."
                            value={searchQuery}
                            style={{ paddingTop: "10px", paddingBottom: "10px" }}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>
                </div>

                {/* Content Area */}
                {loading ? (
                    <div
                        style={{
                            textAlign: "center",
                            padding: "80px 0",
                            color: "#0F3D91",
                            fontWeight: 600,
                            fontSize: "16px",
                        }}
                    >
                        កំពុងទាញយកប្រភេទទិន្នន័យ...
                    </div>
                ) : error ? (
                    <div
                        style={{
                            textAlign: "center",
                            padding: "80px 0",
                            color: "#EF4444",
                            fontWeight: 600,
                        }}
                    >
                        {error}
                    </div>
                ) : categories.length === 0 ? (
                    <div
                        style={{
                            textAlign: "center",
                            padding: "80px 0",
                            color: "#64748b",
                            fontWeight: 500,
                        }}
                    >
                        មិនមានប្រភេទទិន្នន័យបង្ហាញទេ
                    </div>
                ) : (
                    <div className="categories-grid">
                        {categories.map((cat) => (
                            <div
                                key={cat.categoryId}
                                className="category-card-item"
                                onClick={() =>
                                    navigate(`/categories/detail?id=${cat.categoryId}`, {
                                        state: { category: cat },
                                    })
                                }
                                style={{ cursor: "pointer" }}
                            >
                                <div className="cat-card-header">
                                    <div
                                        className="cat-icon-wrapper"
                                        style={{ backgroundColor: "#ECFDF5", color: "#10B981" }}
                                    >
                                        <Icon name="building" />
                                    </div>
                                    <span className="cat-count-badge">
                                        {cat.totalDataset} datasets
                                    </span>
                                </div>

                                <div className="cat-card-body">
                                    <h3 className="cat-title-khmer">{cat.categoryId} {cat.categoryName}</h3>
                                    <p className="cat-desc">{cat.description || "—"}</p>
                                </div>

                                <div className="cat-card-footer">
                                    <span>Explore Datasets</span>
                                    <Icon name="chevron-right" />
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </main>

            {/* Footer */}
            <footer className="about-footer">
                <p>© 2026 National Institute of Statistics · Kingdom of Cambodia</p>
            </footer>
        </div>
    );
}