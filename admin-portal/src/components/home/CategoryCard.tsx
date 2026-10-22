import Icon from "../../icons/Icon";

export type CategoryItem = {
    icon: string;
    color: string;
    title: string;
    count: string;
    id: number;
};

type Props = {
    item: CategoryItem;
    onClick?: () => void;
};

export default function CategoryCard({ item, onClick }: Props) {
    return (
        <div className="cat-card"
            onClick={onClick}>
            {/* icon */}
            <div className={`ci ic ${item.color}`}>
                <Icon name={item.icon} />
            </div>

            {/* title */}
            <b
                style={{
                    fontSize: "20px",
                    display: "block",
                }}
            >
                {item.id} {item.title}
            </b>

            {/* count */}
            {/* <span className="cell-sub">{item.count}</span> */}
        </div>
    );
}