/**
 * Formatting helpers for Approval Management.
 * Output follows the date convention already used across admin screens
 * ("YYYY-MM-DD"), extended with "HH:mm" for timestamps.
 */

const pad = (n: number) => String(n).padStart(2, "0");

const formatLocalDate = (date: Date): string =>
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;

/**
 * Accepts the formats the NIS backend is known or likely to return:
 *   "YYYYMMDD", "YYYYMMDDHHmm", "YYYYMMDDHHmmss",
 *   "YYYY-MM-DD HH:mm[:ss]", ISO strings with timezone, and epoch millis.
 * Unrecognized values are returned unchanged so no information is lost.
 */
export const formatApprovalDateTime = (raw: string | number | null | undefined): string => {
    if (raw === null || raw === undefined || raw === "") return "-";

    if (typeof raw === "number") {
        const d = new Date(raw);
        return Number.isNaN(d.getTime()) ? String(raw) : formatLocalDate(d);
    }

    const value = raw.trim();

    if (/^\d{8}$/.test(value)) {
        return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`;
    }
    if (/^\d{12}(\d{2})?$/.test(value)) {
        return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)} ${value.slice(8, 10)}:${value.slice(10, 12)}`;
    }

    // Timestamps with an explicit timezone are converted to the viewer's local time.
    if (/[T ]\d{2}:\d{2}.*(Z|[+-]\d{2}:?\d{2})$/.test(value)) {
        const d = new Date(value);
        if (!Number.isNaN(d.getTime())) return formatLocalDate(d);
    }

    // Naive local timestamps are displayed as-is (no timezone shifting).
    const naive = value.match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?/);
    if (naive) {
        const [, y, m, d, hh, mm] = naive;
        return hh ? `${y}-${m}-${d} ${hh}:${mm}` : `${y}-${m}-${d}`;
    }

    return value;
};

/** Human-readable file size (binary units), e.g. 12.4 MB. */
export const formatFileSize = (bytes: number | null | undefined): string => {
    if (bytes === null || bytes === undefined || !Number.isFinite(bytes) || bytes < 0) return "-";
    if (bytes < 1024) return `${bytes} B`;

    const units = ["KB", "MB", "GB", "TB"];
    let value = bytes / 1024;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex++;
    }
    return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[unitIndex]}`;
};

/** Thousands-separated count, or "-" when unknown. */
export const formatCount = (value: number | null | undefined): string =>
    value === null || value === undefined ? "-" : value.toLocaleString();
