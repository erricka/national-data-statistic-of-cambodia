import Icon from "../../icons/Icon";

export type FeaturedDataset = {
    sourceId?: string;
    title: string;
    category: string;
    records: string;
    downloads: string;
    badge: string;
};

type Props = {
    item: FeaturedDataset;
};

export default function DatasetCard({ item }: Props) {

    return (
        <div
            className="dataset-card"
            style={{
                display: "flex",
                flexDirection: "column",
                height: "100%",
            }}
        >
            {/* Top row */}
            <div
                className="row between"
                style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                }}
            >
                <span className={`badge badge-${item.badge}`}>
                    {item.category}
                </span>
            </div>

            {/* Title */}
            <b
                style={{
                    fontSize: "15.5px",
                    display: "block",
                    margin: "12px 0 6px",
                }}
            >
                {item.title}
            </b>

            {/* Meta */}
            <div
                className="row"
                style={{
                    display: "flex",
                    gap: "16px",
                }}
            >
                <span className="cell-sub">{item.records} records</span>
            </div>

            {/* Actions - marginTop: "auto" pushes this row to the bottom */}
            <div
                className="row"
                style={{
                    display: "flex",
                    gap: "8px",
                    marginTop: "auto",
                    paddingTop: "14px",
                }}
            >
                <button
                    className="btn btn-outline btn-sm"

                >
                    <Icon name="eye" />
                    View Detail
                </button>

            </div>
        </div>
    );
}