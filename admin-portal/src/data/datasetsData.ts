// datasetsData.ts

export interface DatasetColumn {
    name: string;
    type: string;
    desc: string;
}

export interface Dataset {
    id: string;
    title: string;
    domain: string;
    publisher: string;
    formats: string[];
    completeness: string;
    updated: string;
    summary: string;
    columns: DatasetColumn[];
}

const domains = [
    "Banking & Finance",
    "Public Finance",
    "Geospatial",
    "Demographics",
    "Agriculture",
    "Education",
    "Healthcare",
    "Trade & Commerce",
    "Tourism",
    "Transport & Infra"
];

const publishers = [
    "National Bank of Cambodia",
    "Ministry of Economy and Finance",
    "Ministry of Land Management",
    "National Institute of Statistics",
    "Ministry of Agriculture, Forestry and Fisheries",
    "Ministry of Education, Youth and Sport",
    "Ministry of Health",
    "Ministry of Commerce",
    "Ministry of Tourism",
    "Ministry of Public Works and Transport"
];

const sampleTitles = [
    "Annual Credit Distribution", "Public Sector Investment Report", "Commune Demographic Census",
    "Agricultural Yield Statistics", "Primary Education Enrollment Rates", "District Health Center Metrics",
    "Import/Export Volume Index", "International Visitor Arrivals", "Highway Traffic Density",
    "Microfinance Loan Portfolios", "National Revenue Collection", "GIS Boundaries & Demarcation",
    "Household Consumption Survey", "Rice Yield & Export Production", "Higher Education Graduation Statistics",
    "Vaccination Coverage Rates", "Cross-Border Trade Receipts", "Hotel Occupancy Metrics",
    "Bridge & Road Condition Survey", "Commercial Bank Asset Breakdown"
];

// Generate 100 structured datasets dynamically
export const datasets: Dataset[] = Array.from({ length: 100 }, (_, index) => {
    const idNum = String(index + 1).padStart(3, "0");
    const domainIdx = index % domains.length;
    const publisher = publishers[domainIdx];
    const domain = domains[domainIdx];
    const titleBase = sampleTitles[index % sampleTitles.length];
    const year = 2020 + (index % 7); // 2020 through 2026

    return {
        id: `pd_${idNum}`,
        title: `${titleBase} ${year}`,
        domain: domain,
        publisher: publisher,
        formats: index % 2 === 0 ? ["CSV", "JSON", "REST API"] : ["XLSX", "CSV"],
        completeness: `${92 + (index % 8)}%`,
        updated: `July ${2026 - (index % 2)}`,
        summary: `Official ${domain.toLowerCase()} dataset published by ${publisher} providing detailed metric distributions for ${year}.`,
        columns: [
            { name: "record_id", type: "UUID", desc: "Unique record identifier" },
            { name: "region_code", type: "String", desc: "Standard geographical/commune code" },
            { name: "total_value", type: "Decimal", desc: "Aggregated financial/numeric value" },
            { name: "is_active", type: "Boolean", desc: "Status flag" }
        ]
    };
});