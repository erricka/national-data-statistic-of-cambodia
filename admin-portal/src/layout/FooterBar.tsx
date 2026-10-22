import { Link } from "react-router-dom";


export default function ModernDataExplorer() {
    return (
        <footer style={{ background: "#ffffff", borderTop: "1px solid var(--border, #e2e8f0)", marginTop: "auto" }}>
            <div
                style={{
                    maxWidth: "1280px",
                    margin: "0 auto",
                    padding: "48px 24px 24px 24px",
                    display: "flex",
                    flexDirection: "column",
                    gap: "32px"
                }}
            >
                {/* Top Footer Columns */}
                <div
                    className="grid"
                    style={{
                        gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
                        gap: "32px"
                    }}
                >
                    {/* Column 1: Brand Info */}
                    <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                        <div style={{ fontWeight: "700", fontSize: "16px", color: "var(--n-900, #0f172a)" }}>
                            Open Data Platform
                        </div>
                        <p style={{ fontSize: "13px", color: "var(--n-500, #64748b)", lineHeight: "1.6" }}>
                            Providing open access to official economic, financial, and public sector datasets to support transparency and research.
                        </p>
                    </div>

                    {/* Column 2: Quick Links */}
                    <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        <div style={{ fontWeight: "600", fontSize: "14px", color: "var(--n-800, #1e293b)", marginBottom: "4px" }}>
                            Explore
                        </div>
                        <Link to="/test" style={{ fontSize: "13px", color: "var(--n-600, #475569)", textDecoration: "none" }}>Data Explore</Link>
                        <Link to="/publishers" style={{ fontSize: "13px", color: "var(--n-600, #475569)", textDecoration: "none" }}>Publishers & Ministries</Link>
                        <Link to="/api" style={{ fontSize: "13px", color: "var(--n-600, #475569)", textDecoration: "none" }}>Developer API</Link>
                    </div>

                    {/* Column 3: Resources */}
                    <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        <div style={{ fontWeight: "600", fontSize: "14px", color: "var(--n-800, #1e293b)", marginBottom: "4px" }}>
                            Resources
                        </div>
                        <a href="#" style={{ fontSize: "13px", color: "var(--n-600, #475569)", textDecoration: "none" }}>Documentation</a>
                        <a href="#" style={{ fontSize: "13px", color: "var(--n-600, #475569)", textDecoration: "none" }}>Open Data Policy</a>
                        <a href="#" style={{ fontSize: "13px", color: "var(--n-600, #475569)", textDecoration: "none" }}>Terms of Service</a>
                    </div>

                    {/* Column 4: Contact & Support */}
                    <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        <div style={{ fontWeight: "600", fontSize: "14px", color: "var(--n-800, #1e293b)", marginBottom: "4px" }}>
                            Support
                        </div>
                        <span style={{ fontSize: "13px", color: "var(--n-600, #475569)" }}>Phnom Penh, Cambodia</span>
                        <span style={{ fontSize: "13px", color: "var(--n-600, #475569)" }}>support@opendata.gov.kh</span>
                    </div>
                </div>

                {/* Bottom Copyright Bar */}
                <div
                    className="between row"
                    style={{
                        borderTop: "1px solid var(--border, #e2e8f0)",
                        paddingTop: "20px",
                        fontSize: "12px",
                        color: "var(--n-500, #64748b)",
                        flexWrap: "wrap",
                        gap: "12px"
                    }}
                >
                    <span>© 2026 Open Data Portal. All rights reserved.</span>
                    <div className="row" style={{ gap: "16px" }}>
                        <a href="#" style={{ color: "inherit", textDecoration: "none" }}>Privacy Policy</a>
                        <a href="#" style={{ color: "inherit", textDecoration: "none" }}>Licenses</a>
                    </div>
                </div>
            </div>
        </footer>
    );
}