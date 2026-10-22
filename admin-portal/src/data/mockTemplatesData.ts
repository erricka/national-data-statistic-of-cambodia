export interface TemplateOption {
    id: string;
    code: string;
    name: string;
    description: string;
    columnsCount: number;
}

export const MOCK_TEMPLATES_LIST: TemplateOption[] = [
    {
        id: "nis_26a1",
        code: "26A1",
        name: "26A1 - Case Statistics",
        description: "Case Statistics.",
        columnsCount: 12
    },
    {
        id: "nis_26a2",
        code: "26A2",
        name: "26A2",
        description: "",
        columnsCount: 6
    },
    {
        id: "nis_24a1",
        code: "24A1",
        name: "24A1 Gender Statistics",
        description: "24A1 Gender Statistics",
        columnsCount: 6
    },
    {
        id: "nis_26a5_generic_training",
        code: "26A5",
        name: "26A5 - Generic Training",
        description: "Generic Training.",
        columnsCount: 8
    },
    {
        id: "nis_26a5_judicial_training",
        code: "26A5",
        name: "26A5 - Judical Training",
        description: "Judical Training.",
        columnsCount: 8
    },
    {
        id: "nis_26a7_inspection",
        code: "26A7",
        name: "26A57 - Inspection",
        description: "Inspection.",
        columnsCount: 8
    },
];