import Icon from "../../icons/Icon";

type Props = {
    feed: any[];
};

export default function RecentActivitiesDashboard({ feed }: Props) {
    return (
        <div className="card" style={{ marginTop: "18px" }}>
            <div className="card-head">
                <h3>Recent Activities</h3>
            </div>

            <div className="card-body">
                {feed.map((f, i) => (
                    <div key={i} className="feed-item">

                        {/* ICON */}
                        <span className={`feed-ic ic ${f[1]}`}>
                            <Icon name={f[0]} />
                        </span>

                        {/* BODY */}
                        <div className="body">

                            <div
                                dangerouslySetInnerHTML={{ __html: f[2] }}
                            />

                            <div className="time">
                                {f[3]}
                            </div>

                        </div>

                    </div>
                ))}
            </div>
        </div>
    );
}