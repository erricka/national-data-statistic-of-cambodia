export interface DatasetItem {
    code: string;
    title: string;
    category: string;
    catBadge: string;
    dept: string;
    records: string;
    statusBadge: string;
    statusText: string;
    time: string;
}

const ministries = [
    { name: "ក្រសួងសុខាភិបាល", badge: "danger", dept: "Ministry of Health" },
    { name: "ក្រសួងកសិកម្ម រុក្ខាប្រមាញ់ និងនេសាទ", badge: "teal", dept: "Ministry of Agriculture" },
    { name: "ក្រសួងអប់រំ យុវជន និងកីឡា", badge: "info", dept: "Ministry of Education" },
    { name: "ទីស្តីការគណៈរដ្ឋមន្រ្តី", badge: "neutral", dept: "Ministry of Labour" },
    { name: "ក្រសួងសេដ្ឋកិច្ច និងហិរញ្ញវត្ថុ", badge: "teal", dept: "Ministry of Economy and Finance" },
    { name: "ក្រសួងទេសចរណ៍", badge: "info", dept: "Ministry of Tourism" },
    { name: "ក្រសួងសាធារណការ និងដឹកជញ្ជូន", badge: "danger", dept: "Ministry of Public Works" },
    { name: "ក្រសួងផែនការ", badge: "neutral", dept: "Ministry of Planning" }
];

const sampleTitles = [
    "Provincial Health Indicators", "Rice Production Yield", "Education Youth Official Survey",
    "Labour Force Survey", "Household Income & Expenditure", "Annual Credit Distribution",
    "Public Sector Investment Report", "Commune Demographic Census", "Agricultural Yield Statistics",
    "Primary Education Enrollment Rates", "District Health Center Metrics", "Import/Export Volume Index",
    "International Visitor Arrivals", "Highway Traffic Density", "Microfinance Loan Portfolios",
    "National Revenue Collection", "GIS Boundaries & Demarcation", "Vaccination Coverage Rates"
];

const statuses = [
    { badge: "success", text: "Published" },
    { badge: "warning", text: "Pending" },
    { badge: "info", text: "In Review" },
    { badge: "info", text: "Draft" },
    { badge: "danger", text: "Rejected" },
    { badge: "neutral", text: "Archived" }
];

const timeOptions = [
    "2h ago", "5h ago", "Yesterday", "2 days ago",
    "3 days ago", "1 week ago", "2 weeks ago", "1 month ago"
];

const recordCounts = [
    "48,210", "12,480", "9,640", "31,002", "87,320",
    "156,000", "2.4M", "89,400", "240,000", "14.2M", "62,000"
];

// Generate 100 structured datasets dynamically
export const MOCK_DATASETS: DatasetItem[] = Array.from({ length: 100 }, (_, index) => {
    const idNum = String(1000 - index).padStart(4, "0");
    const ministryObj = ministries[index % ministries.length];
    const titleBase = sampleTitles[index % sampleTitles.length];
    const statusObj = statuses[index % statuses.length];
    const year = 2026 - Math.floor(index / 30);

    return {
        code: `NIS-${year}-${idNum}`,
        title: `${titleBase} (${year})`,
        category: ministryObj.name,
        catBadge: ministryObj.badge,
        dept: ministryObj.dept,
        records: recordCounts[index % recordCounts.length],
        statusBadge: statusObj.badge,
        statusText: statusObj.text,
        time: timeOptions[index % timeOptions.length]
    };
});