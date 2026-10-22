import Icon from "../../icons/Icon";
import "../../index.css";

type StatItem = {
    iconColor: string;
    label: string;
    value: string | number;
};

type Props = {
    items: StatItem[];
};

export default function StatisticDashboard({ items }: Props) {
    return (
        <div className="stat-grid">
            {items.map((item, i) => (
                <div key={i} className="stat">

                    {/* ICON */}
                    <div className="feature-icon-box" style={{ marginBottom: "6px" }}>
                        <Icon name="database" />
                    </div>

                    {/* LABEL */}
                    <div className="label">{item.label}</div>

                    {/* VALUE */}
                    <div className="val">{item.value}</div>

                </div>
            ))}
        </div>
    );
}