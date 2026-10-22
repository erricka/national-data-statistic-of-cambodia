import type { FeaturedDataset } from "../components/home/DatasetCard";

export interface CategoryInfo {
    id: string;
    titleKhmer: string;
    titleEnglish: string;
    description: string;
    icon: string;
    colorBg: string;
    colorIcon: string;
    totalDatasets: number;
    totalDownloads: string;
    lastUpdated: string;
}

export const CATEGORY_INFO: CategoryInfo = {
    id: "health",
    titleKhmer: "ក្រសួងសុខាភិបាល",
    titleEnglish: "Ministry of Health",
    description: "Public health indicators, hospital infrastructure, disease control, and medical statistics across the Kingdom of Cambodia.",
    icon: "shield",
    colorBg: "#EFF6FF",
    colorIcon: "#2563EB",
    totalDatasets: 100,
    totalDownloads: "182.4k",
    lastUpdated: "July 2026"
};

// Seed arrays to generate realistic data items
const DOMAINS = [
    { name: "Health", badge: "danger" },
    { name: "Education", badge: "teal" },
    { name: "Agriculture", badge: "success" },
    { name: "Public Finance", badge: "warning" },
    { name: "Infrastructure", badge: "info" },
    { name: "Demographics", badge: "neutral" },
    { name: "Trade & Commerce", badge: "primary" }
];

const TOPICS = [
    "Hospital & Medical Facility Directory",
    "Vaccination & Immunization Progress Report",
    "Maternal Health & Child Nutrition Survey",
    "Public School Infrastructure & Student Enrollment",
    "Teacher Training & Staff Distribution Log",
    "Rice Production & Paddy Yield Statistics",
    "Fisheries Yield & Coastal Aquaculture Log",
    "Annual Public Expenditure & Budget Allocation",
    "National Tax Revenue & Custom Duty Collection",
    "Road Construction & Highway Paving Projects",
    "Clean Water Supply & Sanitation Coverage",
    "Provincial Population Density & Migration Patterns",
    "Import & Export Commodity Trade Balance",
    "Special Economic Zones (SEZ) Employment Survey",
    "Air Quality & Environmental Monitoring Data",
    "Tourism Arrivals & Hotel Occupancy Rates",
    "Mobile Broadband & Fiber Coverage Index",
    "Microfinance Loans & Agricultural Credit Data",
    "Disaster Risk & Seasonal Flood Impact Assessment",
    "Renewable Energy Generation & Solar Capacity"
];

const PROVINCES = [
    "Phnom Penh", "Siem Reap", "Battambang", "Preah Sihanouk",
    "Kampong Cham", "Kandal", "Takeo", "Kampot", "Kampong Speu", "National Level"
];

// Helper function to generate 100 rich datasets
function generate100Datasets(): FeaturedDataset[] {
    const list: FeaturedDataset[] = [];

    for (let i = 1; i <= 100; i++) {
        const domainObj = DOMAINS[i % DOMAINS.length];
        const topic = TOPICS[i % TOPICS.length];
        const province = PROVINCES[i % PROVINCES.length];
        const year = 2020 + (i % 7); // Generates years 2020–2026

        // Pseudo-random deterministic values based on index
        const recordsCount = ((i * 137) % 95 + 5) * 100; // e.g. 1,400 to 9,800
        const downloadsCount = ((i * 43) % 88 + 12) * 25; // e.g. 300 to 2,500

        list.push({
            title: `${province} ${topic} (${year})`,
            category: domainObj.name,
            records: `${recordsCount.toLocaleString()} records`,
            downloads: downloadsCount.toLocaleString(),
            badge: domainObj.badge
        });
    }

    return list;
}

export const SAMPLE_DATASETS: FeaturedDataset[] = generate100Datasets();