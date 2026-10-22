import { useState, useEffect, useRef } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import NavBar from "../../../layout/NavBar";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import {
    getDataDetail,
    getFileInfo,
    downloadDataDetailFile,
    type DataDetailPayload,
    type FileInfo,
} from "../../../service/client/information/dataDetailService";

// -------------------------------------------------------------
// DYNAMIC HEADER SPAN HELPER
// Handles 1-level and 2-level headers, incorporating empty string "" spans
// -------------------------------------------------------------
const buildHeaderSpans = (
    level1: { title: string }[],
    level2: { title: string }[]
) => {
    const hasLevel2 =
        level2.length > 0 &&
        level2.some((item) => item.title && item.title.trim() !== "");
    const spans: { title: string; colSpan: number; rowSpan: number }[] = [];

    let i = 0;
    while (i < level1.length) {
        const item = level1[i];

        if (!item.title) {
            i++;
            continue;
        }

        let spanCount = 1;
        while (
            i + spanCount < level1.length &&
            level1[i + spanCount].title === ""
        ) {
            spanCount++;
        }

        const isGroupHeader =
            spanCount > 1 || (hasLevel2 && level2[i]?.title !== "");

        spans.push({
            title: item.title,
            colSpan: spanCount,
            rowSpan: !isGroupHeader && hasLevel2 ? 2 : 1,
        });

        i += spanCount;
    }

    return spans;
};

// -------------------------------------------------------------
// DOWNLOAD NOTICE (replaces the browser alert)
// -------------------------------------------------------------
interface DownloadNotice {
    type: "error" | "success";
    title: string;
    detail?: string;
}

/** Downloads use responseType "blob", so a JSON error body arrives as a Blob. Read the real message from it. */
const readBlobMessage = async (blob: Blob): Promise<string> => {
    try {
        const json = JSON.parse(await blob.text());
        const msg = json?.message;
        return (typeof msg === "object" && msg !== null ? msg.messageBody || msg.messageTitle : msg) || json?.data?.error || "";
    } catch {
        return "";
    }
};

const getDownloadErrorMessage = async (err: unknown): Promise<string> => {
    const e = err as { response?: { status?: number; data?: unknown }; message?: string };
    const data = e?.response?.data;
    if (data instanceof Blob) {
        const fromServer = await readBlobMessage(data);
        if (fromServer) return fromServer;
    }
    if (!e?.response) return "Unable to connect to the server. Please check your connection and try again.";
    if (e.response.status === 404) return "The file could not be found on the server.";
    if ((e.response.status ?? 0) >= 500) return "The server had a problem preparing the file. Please try again later.";
    return "Please try again later.";
};

export default function DataDetailPage() {
    const { sourceId } = useParams<{ sourceId: string }>();
    const navigate = useNavigate();

    // Return to wherever the user came from (Home, Category, search…);
    // if the page was opened directly (no in-app history), go to the home page.
    const handleBack = () => {
        if ((window.history.state?.idx ?? 0) > 0) navigate(-1);
        else navigate("/");
    };
    const location = useLocation();
    const locationSourceName = (location.state as { sourceName?: string })?.sourceName;
    const uploadId = (location.state as { uploadId?: string })?.uploadId;

    const currentSourceId = sourceId || "";
    const currentUploadId = uploadId || "";

    const [activeSection, setActiveSection] = useState<string>("preview");
    const [isMobile, setIsMobile] = useState<boolean>(false);
    const [loading, setLoading] = useState<boolean>(true);
    const [downloading, setDownloading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);
    const [dataDetail, setDataDetail] = useState<DataDetailPayload | null>(null);
    const [fileInfo, setFileInfo] = useState<FileInfo | null>(null);
    const [downloadNotice, setDownloadNotice] = useState<DownloadNotice | null>(null);

    const isManualClick = useRef(false);

    const displayTitle = locationSourceName || "";

    useEffect(() => {
        if (!currentUploadId) return;

        console.log("currentUploadId", currentUploadId);

        const fetchData = async () => {
            try {
                setLoading(true);
                setError(null);

                const [detailRes, fileRes] = await Promise.all([
                    getDataDetail({ sourceId: currentSourceId, upload_id: currentUploadId }),
                    getFileInfo(currentUploadId),
                ]);

                if (detailRes.code === "000000" && detailRes.data) {
                    setDataDetail(detailRes.data);
                } else {
                    const errorMsg =
                        typeof detailRes.message === "object" && detailRes.message !== null
                            ? detailRes.message.messageTitle
                            : detailRes.message || "Failed to fetch data.";
                    setError(errorMsg);
                }

                if (fileRes?.code === "000000" && fileRes.data) {
                    setFileInfo(fileRes.data);
                }
            } catch (err: any) {
                setError(err.message || "An unexpected error occurred.");
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [currentSourceId]);

    const handleDownload = async (uploadIdToDownload: string, fileName?: string) => {
        setDownloadNotice(null);
        try {
            setDownloading(true);

            // 1. First attempt
            let res: unknown = await downloadDataDetailFile(uploadIdToDownload);
            let rawData: unknown = (res as { data?: unknown })?.data ?? res;

            // 2. RETRY LOGIC: If server returned an empty 0-byte blob, retry after 500ms
            if (rawData instanceof Blob && rawData.size === 0) {
                console.warn("Received 0 B file, retrying download...");
                await new Promise((resolve) => setTimeout(resolve, 500));
                res = await downloadDataDetailFile(uploadIdToDownload);
                rawData = (res as { data?: unknown })?.data ?? res;
            }

            if (!(rawData instanceof Blob) || rawData.size === 0) {
                setDownloadNotice({
                    type: "error",
                    title: "មិនអាចទាញយកឯកសារបានទេ",
                    detail: "The file is empty or is still being prepared on the server. Please try again in a moment.",
                });
                return;
            }

            // A JSON body instead of a file means the server answered with an error
            if (rawData.type.includes("json")) {
                const message = await readBlobMessage(rawData);
                setDownloadNotice({ type: "error", title: "មិនអាចទាញយកឯកសារបានទេ", detail: message || "Please try again later." });
                return;
            }

            // 3. Resolve file name
            const targetFileName = fileName || fileInfo?.fileName || `${uploadIdToDownload}.csv`;

            // 4. Trigger Browser Download
            const url = window.URL.createObjectURL(rawData);
            const link = document.createElement("a");
            link.href = url;
            link.setAttribute("download", targetFileName);
            document.body.appendChild(link);
            link.click();

            setTimeout(() => {
                link.remove();
                window.URL.revokeObjectURL(url);
            }, 100);

            setDownloadNotice({ type: "success", title: "ការទាញយកបានចាប់ផ្ដើម", detail: targetFileName });
        } catch (err: unknown) {
            console.error("Download failed:", err);
            setDownloadNotice({
                type: "error",
                title: "មិនអាចទាញយកឯកសារបានទេ",
                detail: await getDownloadErrorMessage(err),
            });
        } finally {
            setDownloading(false);
        }
    };

    // Success messages close themselves; errors stay until the user closes them
    useEffect(() => {
        if (downloadNotice?.type !== "success") return;
        const timer = setTimeout(() => setDownloadNotice(null), 4000);
        return () => clearTimeout(timer);
    }, [downloadNotice]);

    useEffect(() => {
        const handleResize = () => {
            setIsMobile(window.innerWidth <= 768);
        };
        handleResize();
        window.addEventListener("resize", handleResize);
        return () => window.removeEventListener("resize", handleResize);
    }, []);

    const sidebarItems = [
        { id: "preview", label: "មើលទិន្នន័យលម្អិត" },
        { id: "download", label: "ទាញយកទិន្នន័យ" },
    ];

    const headerLevel1 = dataDetail?.header?.[0] || [];
    const headerLevel2 = dataDetail?.header?.[1] || [];
    const tableList = dataDetail?.list || [];

    const hasValidLevel2 =
        headerLevel2.length > 0 &&
        headerLevel2.some((sub) => sub.title && sub.title.trim() !== "");

    const scrollToSection = (id: string) => {
        setActiveSection(id);
        isManualClick.current = true;

        const section = document.getElementById(id);
        if (section) {
            section.scrollIntoView({ behavior: "smooth", block: "start" });
        }

        setTimeout(() => {
            isManualClick.current = false;
        }, 800);
    };

    useEffect(() => {
        const handleScroll = () => {
            if (isManualClick.current) return;

            const OFFSET = 150;
            const scrollPosition = window.scrollY + OFFSET;

            for (const item of sidebarItems) {
                const el = document.getElementById(item.id);
                if (el) {
                    const top = el.offsetTop;
                    const height = el.offsetHeight;
                    if (scrollPosition >= top && scrollPosition < top + height) {
                        setActiveSection(item.id);
                        break;
                    }
                }
            }
        };

        window.addEventListener("scroll", handleScroll, { passive: true });
        return () => window.removeEventListener("scroll", handleScroll);
    }, []);

    const sectionStyle = {
        scrollMarginTop: isMobile ? "110px" : "80px",
        maxWidth: "100%",
        boxSizing: "border-box" as const,
    };

    return (
        <div
            style={{
                width: "100%",
                maxWidth: "100vw",
                overflowX: "hidden",
                boxSizing: "border-box",
                display: "flex",
                flexDirection: "column",
                minHeight: "100vh",
            }}
        >
            <NavBar />

            {/* Top Header Banner using displayTitle passed from Home */}
            <div
                style={{
                    backgroundColor: "#FFFFFF",
                    borderBottom: "1px solid #CBD5E1",
                    padding: isMobile ? "20px 16px" : "32px 32px",
                    boxSizing: "border-box",
                    width: "100%",
                }}
            >
                <div style={{ maxWidth: "none", margin: "0 auto", boxSizing: "border-box", width: "100%" }}>
                    <button className="btn-back" onClick={handleBack} style={{ marginBottom: "16px" }}>
                        <Icon name="arrow-left" />
                        <span>Back</span>
                    </button>

                    <h1
                        style={{
                            fontSize: isMobile ? "20px" : "28px",
                            fontWeight: 700,
                            color: "var(--text)",
                            margin: "0 0 12px 0",
                            wordBreak: "break-word",
                        }}
                    >
                        {displayTitle}
                    </h1>
                    <p
                        className="cell-sub"
                        style={{
                            maxWidth: "840px",
                            fontSize: isMobile ? "13px" : "15px",
                            lineHeight: 1.6,
                            color: "var(--text-muted)",
                            margin: 0,
                        }}
                    >
                        This dataset provides detailed statistics for {displayTitle}.
                    </p>
                </div>
            </div>

            {/* Main Content Area */}
            <div
                style={{
                    maxWidth: "none", // use the full page width (side padding below)
                    margin: "0 auto",
                    padding: isMobile ? "16px" : "40px 32px",
                    boxSizing: "border-box",
                    width: "100%",
                    flex: 1,
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: loading || error ? "center" : "flex-start",
                }}
            >
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
                        កំពុងទាញយកទិន្នន័យ...
                    </div>
                ) : error ? (
                    <div style={{ textAlign: "center", padding: "80px 0", color: "#EF4444", fontWeight: 600 }}>
                        {error}
                    </div>
                ) : (
                    <div
                        style={{
                            display: "flex",
                            flexDirection: isMobile ? "column" : "row",
                            gap: isMobile ? "24px" : "24px", // space between left menu and content
                            alignItems: "stretch",
                            width: "100%",
                            boxSizing: "border-box",
                        }}
                    >
                        {/* Sidebar Navigation */}
                        <aside
                            style={{
                                display: "flex",
                                flexDirection: isMobile ? "row" : "column",
                                overflowX: isMobile ? "auto" : "visible",
                                whiteSpace: "nowrap",
                                borderLeft: "none",
                                borderBottom: isMobile ? "2px solid #CBD5E1" : "none",
                                position: "sticky",
                                top: "0px",
                                backgroundColor: "transparent",
                                zIndex: 10,
                                paddingBottom: isMobile ? "4px" : "0",
                                width: isMobile ? "100%" : "200px", // just wide enough for the menu labels
                                minWidth: isMobile ? "auto" : "200px",
                                flexShrink: 0,
                                boxSizing: "border-box",
                            }}
                        >
                            {sidebarItems.map((item) => {
                                const isActive = activeSection === item.id;
                                return (
                                    <button
                                        key={item.id}
                                        onClick={() => scrollToSection(item.id)}
                                        style={{
                                            display: "flex",
                                            alignItems: "center",
                                            padding: isMobile ? "10px 14px" : "12px 18px",
                                            backgroundColor: "transparent",
                                            color: isActive ? "#0F3D91" : "var(--text)",
                                            border: "none",
                                            borderLeft: isMobile
                                                ? "none"
                                                : isActive
                                                    ? "3px solid #0F3D91"
                                                    : "3px solid transparent",
                                            borderBottom: isMobile
                                                ? isActive
                                                    ? "3px solid #0F3D91"
                                                    : "3px solid transparent"
                                                : "none",
                                            marginLeft: isMobile ? "0" : "-2px",
                                            marginBottom: isMobile ? "-2px" : "0",
                                            textAlign: "left",
                                            fontSize: "14px",
                                            fontWeight: isActive ? 700 : 400,
                                            cursor: "pointer",
                                            transition: "all 0.2s ease",
                                            flexShrink: 0,
                                        }}
                                    >
                                        {item.label}
                                    </button>
                                );
                            })}
                        </aside>

                        {/* Detail Sections */}
                        <section
                            style={{
                                display: "flex",
                                flexDirection: "column",
                                gap: isMobile ? "36px" : "64px",
                                flex: 1,
                                minWidth: 0,
                                boxSizing: "border-box",
                                width: "100%",
                            }}
                        >
                            {/* Preview Section */}
                            <div id="preview" style={sectionStyle}>
                                <h2 style={{ fontSize: isMobile ? "18px" : "22px", fontWeight: 700, margin: "0 0 8px 0", color: "var(--text)" }}>
                                    មើលទិន្នន័យលម្អិត
                                </h2>
                                <p className="cell-sub" style={{ marginBottom: "16px", color: "var(--text-muted)", fontSize: "14px" }}>
                                    តារាងខាងក្រោមបង្ហាញពីទិន្នន័យដែលបានស្វែងរក ជាមួយនឹងព័ត៌មានលម្អិតសំខាន់ៗពីសំណុំទិន្នន័យផ្សេងៗ។
                                </p>

                                <div style={{ border: "1px solid #CBD5E1", borderRadius: "var(--r)", overflowX: "auto", width: "100%", boxSizing: "border-box", backgroundColor: "#ffffff" }}>
                                    <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "center", fontSize: "13px", minWidth: "1200px" }}>
                                        <thead>
                                            <tr style={{ backgroundColor: "#F8FAFC" }}>
                                                {buildHeaderSpans(headerLevel1, headerLevel2).map((item, idx) => (
                                                    <th
                                                        key={idx}
                                                        colSpan={item.colSpan > 1 ? item.colSpan : undefined}
                                                        rowSpan={item.rowSpan > 1 ? item.rowSpan : undefined}
                                                        style={{
                                                            padding: "10px",
                                                            fontWeight: 600,
                                                            color: "var(--text)",
                                                            border: "1px solid #CBD5E1",
                                                            verticalAlign: "middle",
                                                        }}
                                                    >
                                                        {item.title}
                                                    </th>
                                                ))}
                                            </tr>

                                            {hasValidLevel2 && (
                                                <tr style={{ backgroundColor: "#F1F5F9" }}>
                                                    {headerLevel2.map((item, idx) => {
                                                        if (!item.title || item.title.trim() === "") return null;

                                                        return (
                                                            <th
                                                                key={idx}
                                                                style={{
                                                                    padding: "8px",
                                                                    fontWeight: 600,
                                                                    color: "var(--text)",
                                                                    border: "1px solid #CBD5E1",
                                                                }}
                                                            >
                                                                {item.title}
                                                            </th>
                                                        );
                                                    })}
                                                </tr>
                                            )}
                                        </thead>
                                        <tbody>
                                            {tableList.map((row, rIdx) => (
                                                <tr key={rIdx}>
                                                    {row.map((cell, cIdx) => (
                                                        <td
                                                            key={cIdx}
                                                            style={{
                                                                padding: "10px",
                                                                color: "var(--text)",
                                                                border: "1px solid #CBD5E1",
                                                                textAlign: typeof cell === "string" ? "left" : "center",
                                                            }}
                                                        >
                                                            {cell !== null && cell !== undefined ? cell : "-"}
                                                        </td>
                                                    ))}
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>

                            {/* Download Section */}
                            <div id="download" style={sectionStyle}>
                                <h2 style={{ fontSize: isMobile ? "18px" : "22px", fontWeight: 700, margin: "0 0 8px 0", color: "var(--text)" }}>
                                    ទាញយកទិន្នន័យ
                                </h2>
                                <p className="cell-sub" style={{ marginBottom: "16px", color: "var(--text-muted)", fontSize: "14px" }}>
                                    ផ្នែកនេះផ្ដល់ជូននូវទម្រង់ឯកសារដែលអាចរកបានសម្រាប់ទាញយក។
                                </p>

                                <div style={{ border: "1px solid #CBD5E1", borderRadius: "var(--r)", overflowX: "auto", width: "100%", boxSizing: "border-box", backgroundColor: "#ffffff" }}>
                                    <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px", minWidth: "500px" }}>
                                        <thead>
                                            <tr style={{ backgroundColor: "#F8FAFC" }}>
                                                <th style={{ padding: "12px 14px", fontWeight: 600, border: "1px solid #CBD5E1" }}>Name</th>
                                                <th style={{ padding: "12px 14px", fontWeight: 600, border: "1px solid #CBD5E1" }}>File Format</th>
                                                <th style={{ padding: "12px 14px", fontWeight: 600, border: "1px solid #CBD5E1" }}>Size</th>
                                                <th style={{ padding: "12px 14px", fontWeight: 600, border: "1px solid #CBD5E1", textAlign: "center" }}>Action</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td style={{ padding: "14px", color: "var(--text)", fontWeight: 500, wordBreak: "break-word", border: "1px solid #CBD5E1" }}>
                                                    {fileInfo?.fileName || `${currentSourceId}.xlsx`}
                                                </td>
                                                <td style={{ padding: "14px", border: "1px solid #CBD5E1" }}>
                                                    <span style={{ backgroundColor: "#0062ff", color: "#ffffff", padding: "4px 8px", borderRadius: "4px", fontSize: "12px", fontWeight: 700, textTransform: "lowercase" }}>
                                                        {fileInfo?.extension ? fileInfo.extension.replace(".", "") : "xlsx"}
                                                    </span>
                                                </td>
                                                <td style={{ padding: "14px", color: "var(--text)", fontWeight: 600, border: "1px solid #CBD5E1" }}>
                                                    {fileInfo?.formattedSize || "—"}
                                                </td>
                                                <td style={{ padding: "14px", textAlign: "center", border: "1px solid #CBD5E1" }}>
                                                    <button
                                                        onClick={() => handleDownload(currentUploadId, fileInfo?.fileName)}
                                                        disabled={downloading}
                                                        className="btn btn-primary"
                                                        style={{
                                                            backgroundColor: "#0F3D91",
                                                            border: "none",
                                                            borderRadius: "4px",
                                                            padding: "6px 16px",
                                                            fontSize: "13px",
                                                            whiteSpace: "nowrap",
                                                            color: "#ffffff",
                                                            cursor: downloading ? "not-allowed" : "pointer",
                                                            opacity: downloading ? 0.7 : 1,
                                                        }}
                                                    >
                                                        {downloading ? "កំពុងទាញយក..." : "ទាញយកទិន្នន័យ"}
                                                    </button>
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>

                                {downloadNotice && (
                                    <div
                                        role={downloadNotice.type === "error" ? "alert" : "status"}
                                        style={{
                                            display: "flex",
                                            alignItems: "flex-start",
                                            gap: "12px",
                                            marginTop: "16px",
                                            padding: "14px 16px",
                                            borderRadius: "10px",
                                            border: `1px solid ${downloadNotice.type === "error" ? "#FECACA" : "#A7F3D0"}`,
                                            borderLeft: `4px solid ${downloadNotice.type === "error" ? "#DC2626" : "#059669"}`,
                                            backgroundColor: downloadNotice.type === "error" ? "#FEF2F2" : "#ECFDF5",
                                            color: downloadNotice.type === "error" ? "#991B1B" : "#065F46",
                                            boxShadow: "0 1px 2px rgba(15, 23, 42, 0.05)",
                                        }}
                                    >
                                        <span
                                            aria-hidden="true"
                                            style={{
                                                display: "inline-flex",
                                                alignItems: "center",
                                                justifyContent: "center",
                                                width: "28px",
                                                height: "28px",
                                                flexShrink: 0,
                                                borderRadius: "50%",
                                                backgroundColor: downloadNotice.type === "error" ? "#FEE2E2" : "#D1FAE5",
                                                color: downloadNotice.type === "error" ? "#DC2626" : "#059669",
                                            }}
                                        >
                                            <Icon name={downloadNotice.type === "error" ? "x" : "check"} />
                                        </span>

                                        <div style={{ flex: 1, minWidth: 0 }}>
                                            <div style={{ fontWeight: 700, fontSize: "14px" }}>{downloadNotice.title}</div>
                                            {downloadNotice.detail && (
                                                <div style={{ marginTop: "4px", fontSize: "13px", lineHeight: 1.5, overflowWrap: "anywhere" }}>
                                                    {downloadNotice.detail}
                                                </div>
                                            )}
                                        </div>

                                        {downloadNotice.type === "error" && (
                                            <button
                                                type="button"
                                                onClick={() => handleDownload(currentUploadId, fileInfo?.fileName)}
                                                disabled={downloading}
                                                style={{
                                                    flexShrink: 0,
                                                    background: "#FFFFFF",
                                                    color: "#991B1B",
                                                    border: "1px solid #FECACA",
                                                    borderRadius: "6px",
                                                    padding: "6px 12px",
                                                    fontSize: "13px",
                                                    fontWeight: 600,
                                                    cursor: downloading ? "not-allowed" : "pointer",
                                                }}
                                            >
                                                Try again
                                            </button>
                                        )}

                                        <button
                                            type="button"
                                            aria-label="Close message"
                                            onClick={() => setDownloadNotice(null)}
                                            style={{
                                                flexShrink: 0,
                                                background: "transparent",
                                                border: "none",
                                                color: "inherit",
                                                cursor: "pointer",
                                                padding: "4px",
                                                opacity: 0.7,
                                                display: "inline-flex",
                                            }}
                                        >
                                            <Icon name="x" />
                                        </button>
                                    </div>
                                )}
                            </div>
                        </section>
                    </div>
                )}
            </div>

            {/* Footer */}
            <footer
                style={{
                    background: "#0F3D91",
                    color: "#9DBDF0",
                    padding: "24px 16px",
                    marginTop: "auto",
                    textAlign: "center",
                    fontSize: "13px",
                    width: "100%",
                    boxSizing: "border-box",
                }}
            >
                © 2026 National Institute of Statistics · Kingdom of Cambodia
            </footer>
        </div>
    );
}