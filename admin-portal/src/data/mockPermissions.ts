export interface PermissionRow {
    id: number;
    permissionName: string;
    keyName: string;
    moduleGroup: "System" | "User Ops" | "Reporting" | "Data Layer";
    description: string;
    riskLevel: "High" | "Medium" | "Low";
    assignedRoles: string[];
    createdAt: string;
}

const moduleGroups: ("System" | "User Ops" | "Reporting" | "Data Layer")[] = [
    "System",
    "User Ops",
    "Reporting",
    "Data Layer"
];

const capabilityBases = [
    { name: "System Config", key: "system_config", desc: "Modify underlying system configurations, environment parameters, and core API mappings." },
    { name: "Audit Purge", key: "audit_purge", desc: "Hard-delete historical security events and system trail logging collections." },
    { name: "Account Management", key: "user_account", desc: "Create, modify, block, or delete team identity records and update baseline profiles." },
    { name: "Raw Data Export", key: "dataset_export", desc: "Extract raw relational rows into unstructured CSV, XLS, or JSON dataset files." },
    { name: "Workflow Approval", key: "content_approve", desc: "Authorize staging data changes and finalize push logs into primary active schemas." },
    { name: "Analytics Access", key: "analytics_read", desc: "Access synthesized summary graphs, metrics panels, and diagnostic data outputs." },
    { name: "Record Index Lookup", key: "dataset_read", desc: "Baseline visibility rights allowing row-level lookups across all public indices." },
    { name: "API Token Minting", key: "api_token_write", desc: "Generate, refresh, and revoke bearer credentials for programmatic service integrations." }
];

const rolePools = [
    ["Super Administrator"],
    ["Super Administrator", "Data Administrator"],
    ["Super Administrator", "Data Administrator", "Department Manager"],
    ["Data Entry Officer", "Public User"],
    ["Auditor"]
];

// Generate 80 mock permissions for testing
export const MOCK_PERMISSIONS: PermissionRow[] = Array.from({ length: 80 }, (_, index) => {
    const base = capabilityBases[index % capabilityBases.length];
    const group = moduleGroups[index % moduleGroups.length];
    const sequenceNum = Math.floor(index / capabilityBases.length) + 1;

    let riskLevel: "High" | "Medium" | "Low" = "Low";
    if (index % 3 === 0) {
        riskLevel = "High";
    } else if (index % 2 === 0) {
        riskLevel = "Medium";
    }

    const assigned = rolePools[index % rolePools.length];

    return {
        id: index + 1,
        permissionName: sequenceNum > 1 ? `${base.name} L${sequenceNum}` : base.name,
        keyName: `${base.key}_${(index + 1).toString().padStart(3, "0")}`,
        moduleGroup: group,
        description: base.desc,
        riskLevel: riskLevel,
        assignedRoles: assigned,
        createdAt: `Jan ${(index % 28) + 1}, 2026`
    };
});