// Headline indicators ("Latest Data") and province-level indicators.
//
// Every query only reads rows that belong to an APPROVED upload — the same rule the
// NIS portal server applies to its public endpoints (datasource_history.status = '01').
// `{approved}` is replaced with that predicate before the query runs.

export const APPROVED = `upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01')`;

const MONTHS = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'];
const monthNumberSql = (col) =>
  `CASE lower(trim(${col}::text)) ${MONTHS.map((m, i) => `WHEN '${m}' THEN ${i + 1} WHEN '${i + 1}' THEN ${i + 1}`).join(' ')} END`;

/**
 * Headline indicators.
 *  - frequency: 'M' (monthly, rows have year+month) or 'A' (annual, rows have year)
 *  - transform: 'yoy' turns an index series into year-on-year % change
 *  - completeYearsOnly: drop the current (incomplete) calendar year for annual series
 */
export const INDICATORS = [
  {
    id: 'cpi-inflation',
    datasetId: 'nis_05a1',
    theme: 'economy',
    title: { en: 'Inflation (CPI, year-on-year)', km: 'អតិផរណា (CPI ធៀបឆ្នាំមុន)' },
    unit: { en: '%', km: '%' },
    frequency: 'M',
    transform: 'yoy',
    format: 'percent1',
    sql: `SELECT DISTINCT ON (year, month) year, month, index_value::float AS value
          FROM nis_05a1_cpi_statistics
          WHERE item_code = '0' AND {approved} AND index_value IS NOT NULL
          ORDER BY year, month, id DESC`,
  },
  {
    id: 'exchange-rate',
    datasetId: 'nis_35a13',
    theme: 'economy',
    title: { en: 'Exchange rate (official, KHR per USD)', km: 'អត្រាប្តូរប្រាក់ផ្លូវការ (រៀល/ដុល្លារ)' },
    unit: { en: 'KHR', km: '៛' },
    frequency: 'M',
    format: 'integer',
    sql: `SELECT DISTINCT ON (year, m) year::int AS year, m AS month, value FROM (
            SELECT year, ${monthNumberSql('month')} AS m, NULLIF(trim(official_fx_midpoint::text), '')::float AS value, id
            FROM nis_35a13_monthly_average_exchange_rate WHERE {approved}) t
          WHERE m IS NOT NULL AND value IS NOT NULL
          ORDER BY year, m, id DESC`,
  },
  {
    id: 'imports',
    datasetId: 'nis_05a5',
    theme: 'economy',
    title: { en: 'Imports (monthly value)', km: 'ការនាំចូល (តម្លៃប្រចាំខែ)' },
    unit: { en: 'KHR billion', km: 'ប៊ីលានរៀល' },
    frequency: 'M',
    format: 'decimal1',
    sql: `SELECT DISTINCT ON (year, month) year, month, wholesale_price::float AS value
          FROM nis_05a5_wholesale_price
          WHERE item_code = 'I' AND {approved} AND wholesale_price IS NOT NULL
          ORDER BY year, month, id DESC`,
  },
  {
    id: 'tourist-arrivals',
    datasetId: 'nis_27_its_a',
    theme: 'industry',
    title: { en: 'International tourist arrivals', km: 'ភ្ញៀវទេសចរអន្តរជាតិ' },
    unit: { en: 'visitors', km: 'នាក់' },
    frequency: 'A',
    completeYearsOnly: true,
    format: 'compact',
    sql: `SELECT DISTINCT ON (year) year, NULLIF(trim(tourist_count::text), '')::float AS value
          FROM nis_27_its_a_tourist_arrivals_stats
          WHERE indicator_code = '27_its_a_t' AND {approved}
          ORDER BY year, id DESC`,
  },
  {
    id: 'health-workforce',
    datasetId: 'nis_12a1',
    theme: 'society',
    title: { en: 'Public health workforce', km: 'មន្ត្រីសុខាភិបាលសាធារណៈ' },
    unit: { en: 'staff', km: 'នាក់' },
    frequency: 'A',
    completeYearsOnly: true,
    format: 'integer',
    // No total row is published, so sum every staff category reported for the year.
    sql: `SELECT year, sum(staff_count)::float AS value
          FROM nis_12a1_public_health_staff
          WHERE {approved} AND staff_count IS NOT NULL
          GROUP BY year ORDER BY year`,
  },
  {
    id: 'vehicles-registered',
    datasetId: 'nis_25b1',
    theme: 'industry',
    title: { en: 'Vehicles registered', km: 'យានយន្តបានចុះបញ្ជី' },
    unit: { en: 'vehicles', km: 'គ្រឿង' },
    frequency: 'A',
    completeYearsOnly: true,
    format: 'compact',
    sql: `SELECT DISTINCT ON (year) year::int AS year, vehicle_count::float AS value
          FROM nis_25b1_registered_vehicle_stats
          WHERE indicator_code = '25b1_y_total' AND {approved}
          ORDER BY year, id DESC`,
  },
  {
    id: 'air-passengers',
    datasetId: 'nis_32a2',
    theme: 'industry',
    title: { en: 'International air passengers', km: 'អ្នកដំណើរតាមផ្លូវអាកាសអន្តរជាតិ' },
    unit: { en: 'passengers', km: 'នាក់' },
    frequency: 'A',
    completeYearsOnly: true,
    format: 'compact',
    sql: `SELECT DISTINCT ON (year) year::int AS year, total_passengers::float AS value
          FROM nis_32a2_air_passengers
          WHERE category_code = '32A2_2' AND {approved}
          ORDER BY year, id DESC`,
  },
  {
    id: 'port-containers',
    datasetId: 'nis_25a9',
    theme: 'industry',
    title: { en: 'Container throughput, Sihanoukville Port', km: 'កុងតឺន័រឆ្លងកាត់កំពង់ផែក្រុងព្រះសីហនុ' },
    unit: { en: "thousand TEUs", km: 'ពាន់ TEUs' },
    frequency: 'A',
    completeYearsOnly: true,
    format: 'decimal1',
    sql: `SELECT DISTINCT ON (year) year::int AS year, container_volume_thousand_teus::float AS value
          FROM nis_25a9_sihanoukville_port_container_stats
          WHERE container_type_code = '25a9_total' AND {approved}
          ORDER BY year, id DESC`,
  },
];

/**
 * Province-level indicators for the map. Each query returns
 * (code, name, year, value); the exporter matches names to the 25 provinces.
 * `{latestYear}` keeps only the most recent year that has data.
 */
export const PROVINCE_INDICATORS = [
  {
    id: 'road-accidents',
    datasetId: 'nis_25c4',
    title: { en: 'Road traffic accidents', km: 'គ្រោះថ្នាក់ចរាចរណ៍ផ្លូវគោក' },
    unit: { en: 'cases', km: 'ករណី' },
    sql: `SELECT province_code AS code, province_kh AS name, year::int AS year, NULLIF(trim(case_total), '')::float AS value
          FROM nis_25c4_road_accident_by_province_stats WHERE {approved} AND year ~ '^[0-9]{4}$'`,
  },
  {
    id: 'road-deaths',
    datasetId: 'nis_25c4',
    title: { en: 'Road traffic deaths', km: 'អ្នកស្លាប់ដោយគ្រោះថ្នាក់ចរាចរណ៍' },
    unit: { en: 'deaths', km: 'នាក់' },
    sql: `SELECT province_code AS code, province_kh AS name, year::int AS year, NULLIF(trim(inj_dth_total), '')::float AS value
          FROM nis_25c4_road_accident_by_province_stats WHERE {approved} AND year ~ '^[0-9]{4}$'`,
  },
  {
    id: 'driving-licences',
    datasetId: 'nis_25b4',
    title: { en: 'Driving licences issued', km: 'ប័ណ្ណបើកបរបានចេញ' },
    unit: { en: 'licences', km: 'ប័ណ្ណ' },
    sql: `SELECT province_code AS code, province_name_kh AS name, year::int AS year, license_count::float AS value
          FROM nis_25b4_driver_license_by_province_stats
          WHERE {approved} AND indicator_code = '25b4_drvlic_total' AND year ~ '^[0-9]{4}$'`,
  },
  {
    id: 'cultural-visitors',
    datasetId: 'nis_18a2',
    title: { en: 'Visitors to major cultural sites', km: 'ភ្ញៀវទេសចរនៅទីតាំងវប្បធម៌សំខាន់ៗ' },
    unit: { en: 'visitors', km: 'នាក់' },
    sql: `SELECT province_code AS code, province_kh AS name, year::int AS year,
                 COALESCE(NULLIF(regexp_replace(domestic_visitors, '[^0-9.]', '', 'g'), '')::float, 0)
               + COALESCE(NULLIF(regexp_replace(international_visitors, '[^0-9.]', '', 'g'), '')::float, 0) AS value
          FROM nis_18a2_cultural_site_tourist_stats
          WHERE {approved} AND year ~ '^[0-9]{4}$'`,
    aggregate: 'sum',
  },
];
