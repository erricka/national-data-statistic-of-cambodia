import Icon from "../../icons/Icon";
import { APPROVAL_STATUS_BADGE } from "../../service/admin/approval-service/approvalStatus";
import type { AuditHistoryEntry } from "../../service/admin/approval-service/approvalTypes";
import { formatApprovalDateTime } from "../../utils/approvalFormatters";

interface AuditTimelineProps {
    entries: AuditHistoryEntry[];
    isLoading: boolean;
    errorMessage: string;
    onRetry?: () => void;
}

interface Tone {
    icon: string;
    color: "blue" | "green" | "red" | "amber" | "teal";
}

/** Picks icon/color from the entry's status, falling back to keywords in the action text. */
const getTone = (entry: AuditHistoryEntry): Tone => {
    const text = `${entry.status} ${entry.action}`.toUpperCase();
    if (text.includes("APPROV")) return { icon: "check", color: "green" };
    if (text.includes("REJECT")) return { icon: "x", color: "red" };
    if (text.includes("CANCEL")) return { icon: "slash", color: "amber" };
    if (text.includes("SUBMIT") || text.includes("UPLOAD")) return { icon: "upload", color: "blue" };
    return { icon: "clock", color: "teal" };
};

const getActionLabel = (entry: AuditHistoryEntry): string => {
    if (entry.action) return entry.action;
    if (entry.status !== "UNKNOWN") return APPROVAL_STATUS_BADGE[entry.status].label;
    return "Activity";
};

const stateStyle = { textAlign: "center" as const, padding: "32px 16px", color: "#64748b", fontSize: "14px" };

/**
 * Vertical audit trail rendered from APPI004. Uses the existing activity-feed
 * styles (.feed-item / .feed-ic / .ic.<color>) with a connector line.
 */
export default function AuditTimeline({ entries, isLoading, errorMessage, onRetry }: AuditTimelineProps) {
    if (isLoading && entries.length === 0) {
        return <div style={stateStyle}>Loading audit history...</div>;
    }

    if (errorMessage) {
        return (
            <div style={stateStyle}>
                <div style={{ color: "var(--danger, #ef4444)", marginBottom: onRetry ? "12px" : 0 }}>{errorMessage}</div>
                {onRetry && (
                    <button className="btn btn-outline btn-sm" onClick={onRetry} disabled={isLoading}>
                        Retry
                    </button>
                )}
            </div>
        );
    }

    if (entries.length === 0) {
        return <div style={stateStyle}>No audit history available.</div>;
    }

    return (
        <ol
            aria-label="Audit history"
            style={{ listStyle: "none", margin: 0, padding: 0, opacity: isLoading ? 0.5 : 1, transition: "opacity 0.2s ease-in-out" }}
        >
            {entries.map((entry, index) => {
                const tone = getTone(entry);
                const isLast = index === entries.length - 1;

                return (
                    <li key={entry.id} className="feed-item" style={{ position: "relative", borderBottom: "none", paddingBottom: isLast ? 0 : "16px" }}>
                        {!isLast && (
                            <span
                                aria-hidden="true"
                                style={{
                                    position: "absolute",
                                    left: "16px",
                                    top: "46px",
                                    bottom: 0,
                                    width: "2px",
                                    background: "var(--border)",
                                }}
                            />
                        )}

                        <span className={`feed-ic ic ${tone.color}`}>
                            <Icon name={tone.icon} />
                        </span>

                        <div className="body" style={{ minWidth: 0, flex: 1 }}>
                            <div>
                                <b>{getActionLabel(entry)}</b>
                                {entry.actorName && (
                                    <span className="muted">
                                        {" "}by <span style={{ color: "var(--text)", fontWeight: 500 }}>{entry.actorName}</span>
                                    </span>
                                )}
                            </div>
                            <div className="time">{formatApprovalDateTime(entry.actionAt)}</div>

                            {entry.comment && (
                                <div
                                    style={{
                                        marginTop: "8px",
                                        padding: "8px 12px",
                                        background: "var(--n-50)",
                                        border: "1px solid var(--border)",
                                        borderRadius: "var(--r-sm)",
                                        color: "var(--n-700)",
                                        whiteSpace: "pre-wrap",
                                        overflowWrap: "anywhere",
                                    }}
                                >
                                    {entry.comment}
                                </div>
                            )}

                            {entry.details.length > 0 && (
                                <div className="cell-sub" style={{ marginTop: "6px", overflowWrap: "anywhere" }}>
                                    {entry.details.map((d) => `${d.label}: ${d.value}`).join(" · ")}
                                </div>
                            )}
                        </div>
                    </li>
                );
            })}
        </ol>
    );
}
