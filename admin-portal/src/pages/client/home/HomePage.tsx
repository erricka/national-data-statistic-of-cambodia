import { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import CategoryCard from "../../../components/home/CategoryCard";
import HomeHeroSeaction from "../../../components/home/HomeHeroSeaction";
import "../../../index.css";
import NavBar from "../../../layout/NavBar";
import {
    getTopDataHome,
    type TopDataHomeItem,
} from "../../../service/client/home/topDataHomeService";
import {
    getTopCategoriesHome,
    type CategoryHomeItem,
} from "../../../service/client/home/topCategoryHomeService";

export default function Home() {
    const [, setTopDatasets] = useState<TopDataHomeItem[]>([]);
    const [categories, setCategories] = useState<CategoryHomeItem[]>([]);
    const [loadingCategories, setLoadingCategories] = useState<boolean>(true);
    const navigate = useNavigate();

    useEffect(() => {
        const fetchHomeData = async () => {
            try {
                // Fetch both top datasets and categories in parallel
                const [datasetsRes, categoriesRes] = await Promise.all([
                    getTopDataHome({ page: 0, size: 50 }),
                    getTopCategoriesHome(),
                ]);

                if (datasetsRes.code === "000000" && datasetsRes.data) {
                    setTopDatasets(datasetsRes.data.list || []);
                }

                if (categoriesRes.code === "000000" && categoriesRes.data) {
                    setCategories(categoriesRes.data.list || []);
                }
            } catch (err) {
                console.error("Failed to load home data:", err);
            } finally {
                setLoadingCategories(false);
            }
        };

        fetchHomeData();
    }, []);

    return (
        <div
            style={{
                display: "flex",
                flexDirection: "column",
                minHeight: "100vh",
            }}
        >
            {/* NAVBAR */}
            <NavBar />

            <div
                className="pub-wrap"
                style={{
                    maxWidth: "1500px",
                    margin: "0 auto",
                    padding: "28px 32px",
                    width: "100%",
                    boxSizing: "border-box",
                    flex: 1,
                }}
            >
               
                <HomeHeroSeaction />

                {/* CATEGORY */}
                <div className="row between" style={{ marginBottom: "14px" }}>
                    <h2 style={{ fontSize: "19px", fontWeight: 700 }}>
                        Browse by Category
                    </h2>
                    <Link to="/categories" className="btn btn-ghost btn-sm" style={{ textDecoration: "none" }}>
                        View all
                    </Link>
                </div>

                {!loadingCategories && categories.length > 0 ? (
                    <div className="grid g-2" style={{ marginBottom: "32px" }}>
                        {categories.map((item) => (
                            <CategoryCard
                                key={item.categoryId}

                                item={{
                                    icon: "building",
                                    color: "green",
                                    title: item.categoryName,
                                    count: item.description || "Category",
                                    id: item.categoryId
                                }}
                                onClick={() =>
                                    navigate(`/categories/detail?id=${item.categoryId}`, {
                                        state: { category: item },
                                    })
                                }
                            />
                        ))}
                    </div>
                ) : loadingCategories ? (
                    <div style={{ textAlign: "center", padding: "24px 0", color: "#64748b" }}>
                        កំពុងទាញយកប្រភេទទិន្នន័យ...
                    </div>
                ) : (
                    <div style={{ textAlign: "center", padding: "24px 0", color: "#64748b", marginBottom: "32px" }}>
                        មិនមានប្រភេទទិន្នន័យបង្ហាញទេ
                    </div>
                )}
            </div>

            <footer
                style={{
                    background: "#0F3D91",
                    color: "#9DBDF0",
                    padding: "32px",
                    marginTop: "auto",
                    textAlign: "center",
                }}
            >
                © 2026 National Institute of Statistics · Kingdom of Cambodia
            </footer>
        </div>
    );
}