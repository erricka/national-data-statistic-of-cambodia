type Props = {
    data: any[];
};

export default function NotificationItemDashboard({ data }: Props) {
    const isUnread = data[0];

    return (
        <div
            className={`notif ${isUnread ? "unread" : ""}`}
            style={{
                display: "flex",
                gap: "10px",
                alignItems: "flex-start",
                marginBottom: "6px"
            }}
        >
            {/* dot indicator */}
            <span
                className="nd"
                style={{
                    background: isUnread ? undefined : "#CBD5E1",
                }}
            />

            {/* content */}
            <div>
                <b style={{ fontSize: "13px", display: "block" }}>
                    {data[1]}
                </b>

                <span className="cell-sub">
                    {data[2]}
                </span>

                <div
                    className="time"
                    style={{
                        fontSize: "11px",
                        color: "var(--text-subtle)",
                        marginTop: "3px",
                    }}
                >
                    {data[3]}
                </div>
            </div>
        </div>
    );
}