import type {
    StagedCellValue,
    StagedDataHeader,
    StagedDataRow,
    StagedHeaderCell,
} from "../../service/admin/approval-service/approvalTypes";

interface StagedDataTableProps {
    headers: StagedDataHeader[];
    /** Render-ready header rows (1 row, or 2 when columns are grouped). */
    headerRows: StagedHeaderCell[][];
    rows: StagedDataRow[];
    isLoading: boolean;
    hasLoaded: boolean;
    errorMessage: string;
    onRetry?: () => void;
}

const MAX_CELL_WIDTH = "280px";
const CELL_BORDER = "1px solid var(--border)";

const renderCell = (value: StagedCellValue) => {
    if (value === null || value === "") {
        return <span style={{ color: "var(--text-subtle)" }}>—</span>;
    }
    if (typeof value === "number") return value.toLocaleString();
    if (typeof value === "boolean") return value ? "true" : "false";
    return value;
};

const stateCell = { textAlign: "center" as const, padding: "48px", color: "#64748b" };

/**
 * Renders whatever columns APPI003 returns, including grouped (two-level)
 * headers in the same way as the public Data Detail page. Uses the existing
 * `.table-wrap` / `.tbl` styles; the grid scrolls in both directions with a
 * sticky header, and long values are truncated with the full text on hover.
 */
export default function StagedDataTable({
    headers,
    headerRows,
    rows,
    isLoading,
    hasLoaded,
    errorMessage,
    onRetry,
}: StagedDataTableProps) {
    const colSpan = Math.max(headers.length, 1);
    const showInitialLoading = isLoading && (!hasLoaded || rows.length === 0);
    const isGrouped = headerRows.length > 1;

    return (
        <div
            className="table-wrap"
            style={{
                opacity: isLoading && !showInitialLoading ? 0.5 : 1,
                transition: "opacity 0.2s ease-in-out",
                // Bounded height keeps the horizontal scrollbar and pagination within reach
                maxHeight: "min(560px, 65vh)",
                overflow: "auto",
            }}
            tabIndex={0}
            aria-label="Staged data preview"
        >
            <table className="tbl">
                {headerRows.length > 0 && (
                    <thead style={{ position: "sticky", top: 0, zIndex: 1 }}>
                        {headerRows.map((cells, rowIndex) => (
                            <tr key={rowIndex}>
                                {cells.map((cell, cellIndex) => (
                                    <th
                                        key={cellIndex}
                                        colSpan={cell.colSpan > 1 ? cell.colSpan : undefined}
                                        rowSpan={cell.rowSpan > 1 ? cell.rowSpan : undefined}
                                        title={cell.title}
                                        style={{
                                            maxWidth: cell.colSpan > 1 ? undefined : MAX_CELL_WIDTH,
                                            overflow: "hidden",
                                            textOverflow: "ellipsis",
                                            verticalAlign: "middle",
                                            textAlign: cell.colSpan > 1 ? "center" : "left",
                                            // Vertical lines make column groups readable
                                            borderLeft: isGrouped ? CELL_BORDER : undefined,
                                            borderRight: isGrouped ? CELL_BORDER : undefined,
                                        }}
                                    >
                                        {cell.title}
                                    </th>
                                ))}
                            </tr>
                        ))}
                    </thead>
                )}
                <tbody>
                    {showInitialLoading ? (
                        <tr>
                            <td colSpan={colSpan} style={stateCell}>Loading preview data...</td>
                        </tr>
                    ) : errorMessage ? (
                        <tr>
                            <td colSpan={colSpan} style={stateCell}>
                                <div style={{ color: "var(--danger, #ef4444)", marginBottom: onRetry ? "12px" : 0 }}>{errorMessage}</div>
                                {onRetry && (
                                    <button className="btn btn-outline btn-sm" onClick={onRetry} disabled={isLoading}>
                                        Retry
                                    </button>
                                )}
                            </td>
                        </tr>
                    ) : rows.length === 0 ? (
                        <tr>
                            <td colSpan={colSpan} style={stateCell}>No staged data available.</td>
                        </tr>
                    ) : (
                        rows.map((row, rowIndex) => (
                            <tr key={rowIndex}>
                                {headers.map((header) => {
                                    const value = row[header.key] ?? null;
                                    const isNumeric = typeof value === "number";
                                    return (
                                        <td
                                            key={header.key}
                                            title={value === null ? "" : String(value)}
                                            style={{
                                                maxWidth: MAX_CELL_WIDTH,
                                                whiteSpace: "nowrap",
                                                overflow: "hidden",
                                                textOverflow: "ellipsis",
                                                textAlign: isNumeric ? "right" : "left",
                                                fontVariantNumeric: isNumeric ? "tabular-nums" : undefined,
                                                borderLeft: isGrouped ? CELL_BORDER : undefined,
                                                borderRight: isGrouped ? CELL_BORDER : undefined,
                                            }}
                                        >
                                            {renderCell(value)}
                                        </td>
                                    );
                                })}
                            </tr>
                        ))
                    )}
                </tbody>
            </table>
        </div>
    );
}
