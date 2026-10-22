export interface CategoryItem {
    code: string;
    title: string;
    dept: string;
    records: string;
    statusBadge: "success" | "danger";
    statusText: "Active" | "Inactive";
    time: string;
}

const ministries = [
    "ក្រសួងអប់រំ យុវជន និងកីឡា",
    "ក្រសួងកសិកម្ម រុក្ខាប្រមាញ់ និងនេសាទ",
    "ក្រសួងសុខាភិបាល",
    "ទីស្តីការគណៈរដ្ឋមន្រ្តី",
    "ក្រសួងសេដ្ឋកិច្ច និងហិរញ្ញវត្ថុ",
    "ក្រសួងមហាផ្ទៃ",
    "ក្រសួងការពារជាតិ",
    "ក្រសួងការបរទេស និងសហប្រតិបត្តិការអន្តរជាតិ",
    "ក្រសួងរៀបចំដែនដី នគរូបនីយកម្ម និងសំណង់",
    "ក្រសួងបរិស្ថាន",
    "ក្រសួងទេសចរណ៍",
    "ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
    "ក្រសួងពាណិជ្ជកម្ម",
    "ក្រសួងរ៉ែ និងថាមពល",
    "ក្រសួងផែនការ",
    "ក្រសួងព័ត៌មាន",
    "ក្រសួងការងារ និងបណ្តុះបណ្តាលវិជ្ជាជីវៈ",
    "ក្រសួងធនធានទឹក និងឧតុនិយម",
    "ក្រសួងប្រៃសណីយ៍ និងទូរគមនាគមន៍",
    "ក្រសួងវប្បធម៌ និងវិចិត្រសិល្បៈ",
    "ក្រសួងធម្មការ និងសាសនា",
    "ក្រសួងកិច្ចការនារី",
    "ក្រសួងអភិវឌ្ឍន៍ជនបទ",
    "ក្រសួងសង្គមកិច្ច អតីតយុទ្ធជន និងយុវនីតិសម្បទា",
    "ក្រសួងមុខងារសាធារណៈ"
];

const sampleDescriptions = [
    "ទិន្នន័យគ្រឹះស្ថានសិក្សា និងស្ថិតិសិស្ស-និស្សិតទូទាំងប្រទេស",
    "ស្ថិតិទិន្នផលកសិកម្ម ផលនេសាទ និងការនាំចេញ",
    "ស្ថិតិមន្ទីរពេទ្យ គ្រូពេទ្យ និងទិន្នន័យសុខភាពសាធារណៈ",
    "ឯកសារច្បាប់ លិខិតបទដ្ឋានយុត្តិភ័ណ្ឌ និងអនុក្រឹត្យ",
    "ទិន្នន័យថវិកាជាតិ ចំណូលពន្ធ និងសូចនាករសេដ្ឋកិច្ច",
    "ទិន្នន័យអត្តសញ្ញាណប័ណ្ណ និងអត្រានុកូលដ្ឋាន",
    "ស្ថិតិកងយោធពលខេមរភូមិន្ទ និងតំបន់ការពារ",
    "ទិន្នន័យកិច្ចព្រមព្រៀងអន្តរជាតិ និងទិដ្ឋាការ",
    "ទិន្នន័យប័ណ្ណកម្មសិទ្ធិដីធ្លី និងគម្រោងសំណង់",
    "ទិន្នន័យតំបន់ការពារធម្មជាតិ និងការបំពុលបរិស្ថាន",
    "ស្ថិតិទេសចរអន្តរជាតិ និងអាជីវកម្មទេសចរណ៍",
    "ទិន្នន័យចុះបញ្ជីយានយន្ត និងផ្លូវថ្នល់",
    "ទិន្នន័យចុះបញ្ជីក្រុមហ៊ុន និងទំហំពាណិជ្ជកម្ម",
    "ទិន្នន័យអាជ្ញាប័ណ្ណរ៉ែ និងការផ្គត់ផ្គង់អគ្គិសនី",
    "ទិន្នន័យជម្រឿនប្រជាជន និងស្ថិតិក្រីក្រ (IDPoor)"
];

const timeOptions = [
    "2h ago", "5h ago", "Yesterday", "2 days ago",
    "1 week ago", "2 weeks ago", "1 month ago", "3 months ago"
];

const recordCounts = [
    "2.4M", "14.2M", "16.8M", "5.8M", "1.8M",
    "48,210", "12,480", "156,000", "89,400", "240,000", "9,120", "62,000"
];

// Dynamically generate 100 test categories for pagination testing
export const MOCK_CATEGORIES: CategoryItem[] = Array.from({ length: 100 }, (_, index) => {
    const idNum = String(index + 1).padStart(4, "0");
    const ministryBase = ministries[index % ministries.length];
    const deptBase = sampleDescriptions[index % sampleDescriptions.length];

    // Create status variations (roughly 85% Active, 15% Inactive)
    const isActive = index % 7 !== 0;

    return {
        code: `NIS-2026-${idNum}`,
        title: `${ministryBase} (តំបន់ ${Math.floor(index / ministries.length) + 1})`,
        dept: `${deptBase} - ជំហានទី ${(index % 5) + 1}`,
        records: recordCounts[index % recordCounts.length],
        statusBadge: isActive ? "success" : "danger",
        statusText: isActive ? "Active" : "Inactive",
        time: timeOptions[index % timeOptions.length]
    };
});