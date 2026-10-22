export interface ColumnItem {
    id: string;
    item_name: string;
    type: string;
    item_description?: string;
}

export interface TemplateItem {
    id: string;
    code: string;
    name: string;
    description: string;
    status: "active" | "draft" | "archived";
    columnsCount: number;
    updatedAt: string;
    sampleColumns: ColumnItem[];
}

const DOMAINS = [
    "Demographics & Health",
    "Agriculture & Land",
    "Education Enrollment",
    "Economic Indicators",
    "Public Transport",
    "Tourism & Hospitality",
    "Environmental Monitoring",
    "Energy & Utilities",
    "Labor & Employment",
    "Trade & Exports"
];

const STATUSES: Array<"active" | "draft" | "archived"> = ["active", "draft", "archived"];

const SAMPLE_FIELDS = [
    { item_name: "id", type: "BIGINT" },
    { item_name: "code", type: "VARCHAR(50)" },
    { item_name: "title", type: "VARCHAR(255)" },
    { item_name: "amount", type: "DECIMAL(10,2)" },
    { item_name: "recorded_at", type: "TIMESTAMP" },
    { item_name: "is_active", type: "BOOLEAN" },
    { item_name: "metadata", type: "JSONB" },
    { item_name: "region", type: "VARCHAR(100)" }
];

// Generate 100 static dataset schema templates
export const MOCK_TEMPLATES: TemplateItem[] = Array.from({ length: 100 }, (_, index) => {
    const id = (index + 1).toString();
    const domain = DOMAINS[index % DOMAINS.length];
    const status = STATUSES[index % STATUSES.length];
    const columnsCount = Math.floor(Math.random() * 12) + 4; // Between 4 and 15 columns

    return {
        id,
        code: `TPL-2026-${(index + 1).toString().padStart(3, "0")}`,
        name: `${domain} Schema Template #${index + 1}`,
        description: `Standardized schema structure used for collecting and auditing data in ${domain.toLowerCase()} sector across regional branches.`,
        status,
        columnsCount,
        updatedAt: index % 2 === 0 ? `${(index % 12) + 1}h ago` : `${(index % 5) + 1} days ago`,
        sampleColumns: [
            { id: "1", ...SAMPLE_FIELDS[index % SAMPLE_FIELDS.length] },
            { id: "2", ...SAMPLE_FIELDS[(index + 1) % SAMPLE_FIELDS.length] },
            { id: "3", ...SAMPLE_FIELDS[(index + 2) % SAMPLE_FIELDS.length] }
        ]
    };
});