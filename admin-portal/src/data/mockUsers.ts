export interface UserRow {
    id: string;
    name: string;
    email: string;
    dept: string;
    role: string;
    statusKey: string;
    statusText: string;
    time: string;
    initial: string;
}

export const ROLE_COLORS: Record<string, string> = {
    "Super Administrator": "danger",
    "Data Administrator": "info",
    "Department Manager": "teal",
    "Data Entry Officer": "neutral",
    "Public User": "neutral"
};

export const STATUS_MAP: Record<string, string> = {
    active: "success",
    warning: "warning",
    danger: "danger"
};

const firstNames = ["Sok", "Vann", "Chan", "Lim", "Heng", "Pich", "Kim", "Ouk", "Khem", "Chea"];
const lastNames = ["Reaksmey", "Molika", "Dara", "Sophea", "Visal", "Sothea", "Nimol", "Channary", "Vannak", "Bora"];
const depts = [
    { name: "Statistics Office", domain: "nis.gov.kh" },
    { name: "Ministry of Health", domain: "health.gov.kh" },
    { name: "Ministry of Agriculture", domain: "agri.gov.kh" },
    { name: "Ministry of Education", domain: "moeys.gov.kh" },
    { name: "Ministry of Planning", domain: "plan.gov.kh" },
    { name: "Ministry of Labour", domain: "labour.gov.kh" },
    { name: "Ministry of Tourism", domain: "tourism.gov.kh" }
];

const roles = ["Super Administrator", "Data Administrator", "Department Manager", "Data Entry Officer", "Public User"];
const times = ["2 min ago", "1h ago", "3h ago", "5h ago", "Yesterday", "2 days ago", "3 weeks ago", "Never"];

// Generate 80 test users
export const MOCK_USERS: UserRow[] = Array.from({ length: 80 }, (_, index) => {
    const fn = firstNames[index % firstNames.length];
    const ln = lastNames[Math.floor(index / firstNames.length) % lastNames.length];
    const deptObj = depts[index % depts.length];
    const role = roles[index % roles.length];

    // Status distribution
    let statusKey = "active";
    let statusText = "Active";
    if (index % 7 === 0) {
        statusKey = "warning";
        statusText = "Pending";
    } else if (index % 11 === 0) {
        statusKey = "danger";
        statusText = "Disabled";
    }

    return {
        id: (index + 1) + "",
        name: `${fn} ${ln}`,
        email: `${fn.toLowerCase()}.${ln.toLowerCase()}${index + 1}@${deptObj.domain}`,
        dept: deptObj.name,
        role: role,
        statusKey: statusKey,
        statusText: statusText,
        time: times[index % times.length],
        initial: `${fn[0]}${ln[0]}`
    };
});