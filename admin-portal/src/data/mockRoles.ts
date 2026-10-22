export interface RoleRow {
    id: number;
    roleName: string;
    keyName: string;
    description: string;
    membersCount: number;
    permissions: string[];
    createdAt: string;
    variant: "danger" | "info" | "teal" | "neutral" | "warning";
}

const baseRoles = [
    { name: "Super Administrator", key: "admin", variant: "danger", perms: ["All Access", "User Write", "System Config", "Logs Purge"] },
    { name: "Data Administrator", key: "data_admin", variant: "info", perms: ["User Write", "Reports View", "Data Export", "Content Approve"] },
    { name: "Department Manager", key: "manager", variant: "teal", perms: ["User Write", "Reports View", "Data Export"] },
    { name: "Data Entry Officer", key: "editor", variant: "neutral", perms: ["Data Write", "Data View"] },
    { name: "Public User", key: "viewer", variant: "neutral", perms: ["Data View"] },
    { name: "Security Auditor", key: "auditor", variant: "warning", perms: ["Audit View", "Logs View"] }
];

export const ROLES_DATA: RoleRow[] = Array.from({ length: 80 }, (_, index) => {
    const base = baseRoles[index % baseRoles.length];
    const tier = Math.floor(index / baseRoles.length) + 1;

    return {
        id: index + 1,
        roleName: tier > 1 ? `${base.name} L${tier}` : base.name,
        keyName: `${base.key}_${index + 1}`,
        description: `Handles permissions and scope management for ${base.name.toLowerCase()} group scope level ${tier}.`,
        membersCount: (index * 7 + 3) % 250,
        permissions: base.perms,
        variant: base.variant as RoleRow["variant"],
        createdAt: `Feb ${(index % 28) + 1}, 2026`
    };
});