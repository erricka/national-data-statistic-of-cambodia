import Icon from "../../../icons/Icon";
import "../../../index.css";
import NavBar from "../../../layout/NavBar";

export default function AboutPage() {
    return (
        <div className="about-page-wrapper">
            <NavBar />

            <main className="about-container">
                {/* Hero Banner */}
                <div className="hero-public about-hero">
                    <span className="badge hero-badge">
                        Ministry of Planning
                    </span>

                    <h1 className="about-hero-title">
                        About National Institute of Statistics
                    </h1>

                    <p className="about-hero-desc">
                        Empowering evidence-based decision-making through open, accessible,
                        and reliable national demographic, economic, and social statistical data.
                    </p>
                </div>

                {/* Mission & Vision Cards */}
                <section className="about-grid-2">
                    <div className="about-card">
                        <div className="icon-badge icon-badge-teal">
                            <Icon name="shield" />
                        </div>
                        <h3>Our Mission</h3>
                        <p>
                            To collect, process, analyze, and disseminate official statistical information in an accurate, timely, and transparent manner to support national development strategies.
                        </p>
                    </div>

                    <div className="about-card">
                        <div className="icon-badge icon-badge-blue">
                            <Icon name="globe" />
                        </div>
                        <h3>Our Vision</h3>
                        <p>
                            To be a trusted regional leader in statistical excellence, delivering high-quality digital data services that foster innovation and public policy transparency.
                        </p>
                    </div>
                </section>

                {/* Core Functions Section */}
                <section className="about-section">
                    <h2 className="section-title">Core Functions</h2>

                    <div className="about-grid-3">
                        <div className="feature-item">
                            <div className="feature-icon-box">
                                <Icon name="database" />
                            </div>
                            <div>
                                <b>National Censuses & Surveys</b>
                                <p>Conducting periodic population, agricultural, and economic censuses across all 25 provinces.</p>
                            </div>
                        </div>

                        <div className="feature-item">
                            <div className="feature-icon-box">
                                <Icon name="chart" />
                            </div>
                            <div>
                                <b>Data Standardization</b>
                                <p>Establishing harmonized statistical methodologies aligned with international UN standards.</p>
                            </div>
                        </div>

                        <div className="feature-item">
                            <div className="feature-icon-box">
                                <Icon name="building" />
                            </div>
                            <div>
                                <b>Inter-Ministry Coordination</b>
                                <p>Collaborating with 14+ ministries to centralize national indicators into open data platforms.</p>
                            </div>
                        </div>
                    </div>
                </section>

                {/* Organization Statistics Banner */}
                <section className="about-stats-card">
                    <div className="stats-header">
                        <h4>Key Indicators Managed</h4>
                        <p>Covering public sector records, census results, and socio-economic updates.</p>
                    </div>

                    <div className="about-stats-numbers">
                        <div className="stat-item">
                            <b>25</b>
                            <small>Provinces Covered</small>
                        </div>
                        <div className="stat-divider" />
                        <div className="stat-item">
                            <b>14+</b>
                            <small>Partner Ministries</small>
                        </div>
                        <div className="stat-divider" />
                        <div className="stat-item">
                            <b>900+</b>
                            <small>Public Datasets</small>
                        </div>
                    </div>
                </section>
            </main>

            {/* Footer */}
            <footer className="about-footer">
                <p>© 2026 National Institute of Statistics · Kingdom of Cambodia</p>
            </footer>
        </div>
    );
}