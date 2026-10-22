import { useEffect, useState } from "react";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";
import { useNavigate } from "react-router-dom";

// Preview Data Mock
const PREVIEW_DATA = [
    ["Phnom Penh", "Boeng Keng Kang", "2,281,951", "1,108,420", "1,173,531", "512,840", "98.2"],
    ["Kandal", "Ta Khmau", "1,193,402", "581,200", "612,202", "268,510", "41.0"],
    ["Siem Reap", "Siem Reap", "1,014,234", "498,110", "516,124", "221,870", "36.4"],
    ["Battambang", "Battambang", "987,400", "482,330", "505,070", "214,650", "33.1"],
    ["Kampong Cham", "Kampong Cham", "895,120", "436,800", "458,320", "198,430", "27.8"],
    ["Takeo", "Daun Keo", "843,931", "410,220", "433,711", "187,560", "18.9"]
];

export default function ViewDataDetailPage() {
    const [activeTab, setActiveTab] = useState("meta");
    const navigate = useNavigate();

    const downloadFilePath =
        "/3_MoEYS_Education_Youth_and_Sports_Official_Date_03_06_2026_time.xlsx";

    const filename = downloadFilePath.substring(downloadFilePath.lastIndexOf("/") + 1);

    useEffect(() => {
        // Notify the shell container layer about local screen info context updates
        if (window.NIS && typeof window.NIS.shell === "function") {
            window.NIS.shell({
                active: "datasets",
                title: "Population Census 2024",
                breadcrumb: ["Home", "Datasets", "Detail"]
            });
        }

        // Trigger visual rendering hooks if core visualization modules exist
        if (window.NIS && window.NIS.charts) {
            if (activeTab === "stats" && typeof window.NIS.charts.donut === "function") {
                window.NIS.charts.donut("genderD", [
                    { v: 49, color: "#1D4ED8" },
                    { v: 51, color: "#14B8A6" }
                ], { center: "16.9M", sub: "total" });
            }

            if (activeTab === "viz" && typeof window.NIS.charts.bar === "function") {
                window.NIS.charts.bar("vizbar", [
                    { label: "P.Penh", v: 2281, color: "#1D4ED8" },
                    { label: "Kandal", v: 1193, color: "#1D4ED8" },
                    { label: "S.Reap", v: 1014, color: "#1D4ED8" },
                    { label: "B.bang", v: 987, color: "#3B82F6" },
                    { label: "K.Cham", v: 895, color: "#3B82F6" },
                    { label: "Takeo", v: 843, color: "#3B82F6" },
                    { label: "P.Veng", v: 790, color: "#94A3B8" }
                ]);
            }
        }
    }, [activeTab]);

    return (
        <div className="app">
            <Sidebar active="data_management" />

            <div className="main">
                <Topbar pageTitle="Population Census 2024" />

                <main className="content">
                    <div className="page-head">

                        <div>
                            <button
                                className="btn btn-outline btn-sm"
                                style={{ padding: "6px 10px", marginBottom: "12px" }}
                                onClick={() => navigate("/admin/data-management")}
                            >
                                <Icon name="arrow-left" /> Back
                            </button>

                            <div className="row" style={{ gap: "10px", marginBottom: "6px" }}>
                                <span className="badge badge-success">
                                    <span className="d"></span>Published
                                </span>
                                <span className="cell-sub">NIS-2024-0912 · v2.3</span>
                            </div>
                            <h1>Population Census 2024 — Q2</h1>
                            <p>Ministry of Planning · National coverage · Last updated 11 Jun 2026</p>
                        </div>
                        <div className="actions">
                            <button className="btn btn-outline" id="b-edit">
                                <Icon name="edit" /> Edit
                            </button>
                            <a
                                href={downloadFilePath}
                                download={filename}
                                className="btn btn-outline"
                                id="b-dl">
                                <Icon name="folder" /> Download
                            </a>
                            <button
                                className="btn btn-primary"
                                id="b-pub"
                            //onClick={() => navigate("/data-details")}
                            >
                                <Icon name="settings" /> View Public Page
                            </button>
                        </div>
                    </div>

                    {/* Metrics summary frame */}
                    <div className="stat-grid">
                        <div className="stat"><div className="label">Total Records</div><div className="val">2,401,830</div></div>
                        <div className="stat"><div className="label">Columns</div><div className="val">28</div></div>
                        <div className="stat"><div className="label">Downloads</div><div className="val">14,206</div></div>
                        <div className="stat">
                            <div className="label">Data Quality</div>
                            <div className="val" style={{ color: "#15803D" }}>98.4%</div>
                        </div>
                    </div>

                    <div className="card">
                        {/* Tab Controller Trigger Interface bar */}
                        <div className="tabs">
                            {[
                                { id: "meta", label: "Metadata" },
                                { id: "data", label: "Data Preview" },
                                // { id: "api", label: "API" },
                            ].map((tab) => (
                                <div
                                    key={tab.id}
                                    className={`tab ${activeTab === tab.id ? "active" : ""}`}
                                    onClick={() => setActiveTab(tab.id)}
                                >
                                    {tab.label}
                                </div>
                            ))}
                        </div>

                        {/* TAB PANEL: Metadata Profile */}
                        {activeTab === "meta" && (
                            <div className="card-body">
                                <div className="grid g-2">
                                    <div>
                                        <div className="sec-title">Description</div>
                                        <p className="muted" style={{ lineHeight: 1.7 }}>
                                            Quarterly update of the 2024 national population census covering demographic distribution across all 25 provinces.
                                            Includes age, gender, household size, and urban/rural classification at the commune level.
                                        </p>
                                        <div style={{ height: "18px" }}></div>
                                        <div className="sec-title">Tags</div>
                                        <div className="chips">
                                            {["population", "census", "demographics", "2024", "provincial"].map((tag, idx) => (
                                                <span key={idx} className="chip">{tag}</span>
                                            ))}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="kv"><span className="k">Dataset Code</span><span className="v">NIS-2024-0912</span></div>
                                        <div className="kv"><span className="k">Owner Department</span><span className="v">Ministry of Planning</span></div>
                                        <div className="kv"><span className="k">Category</span><span className="v">Demographics</span></div>
                                        <div className="kv"><span className="k">Coverage</span><span className="v">National · 25 provinces</span></div>
                                        <div className="kv"><span className="k">Update Frequency</span><span className="v">Quarterly</span></div>
                                        <div className="kv"><span className="k">License</span><span className="v">Open Data (CC-BY 4.0)</span></div>
                                        <div className="kv"><span className="k">Format</span><span className="v">CSV · XLSX · JSON · API</span></div>
                                        <div className="kv"><span className="k">Created</span><span className="v">14 Mar 2024</span></div>
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* TAB PANEL: Spreadsheet Preview Data Matrix */}
                        {activeTab === "data" && (
                            <div className="card-body" style={{ padding: 0 }}>
                                <div className="table-wrap">
                                    <table className="tbl">
                                        <thead>
                                            <tr>
                                                <th>Province</th>
                                                <th>Commune</th>
                                                <th>Total Pop.</th>
                                                <th>Male</th>
                                                <th>Female</th>
                                                <th>Households</th>
                                                <th>Urban %</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {PREVIEW_DATA.map((row, idx) => (
                                                <tr key={idx}>
                                                    <td className="cell-main">{row[0]}</td>
                                                    {row.slice(1).map((cell, cellIdx) => (
                                                        <td key={cellIdx}>{cell}</td>
                                                    ))}
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}

                        {/* TAB PANEL: Developer API Token Endpoint Access */}
                        {activeTab === "api" && (
                            <div className="card-body">
                                <div className="sec-title">REST API Endpoint</div>
                                <div style={{ background: "#0F172A", color: "#E2E8F0", borderRadius: "10px", padding: "16px 18px", fontFamily: "ui-monospace,monospace", fontSize: "13px", overflow: "auto" }}>
                                    <div><span style={{ color: "#22C55E" }}>GET</span> https://data.nis.gov.kh/api/v1/datasets/NIS-2024-0912</div>
                                    <div style={{ color: "#64748B", marginTop: "8px" }}>Authorization: Bearer &lt;api_token&gt;</div>
                                    <div style={{ color: "#64748B" }}>Accept: application/json</div>
                                </div>
                                <div style={{ height: "16px" }}></div>
                                <div className="kv"><span className="k">Rate limit</span><span className="v">1,000 requests / hour</span></div>
                                <div className="kv"><span className="k">Formats</span><span className="v">JSON · CSV · GeoJSON</span></div>
                                <div className="kv"><span className="k">Authentication</span><span className="v">API Key (public read)</span></div>
                                <div style={{ marginTop: "16px" }}>
                                    <button className="btn btn-outline btn-sm">
                                        <Icon name="plus" /> Generate API Key
                                    </button>
                                </div>
                            </div>
                        )}

                    </div>
                </main>
            </div>
        </div>
    );
}