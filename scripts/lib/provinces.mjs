// The 25 capital/provinces of Cambodia in official numbering order.
// `id` matches the location ids of the @svg-maps/cambodia map.
export const PROVINCES = [
  { no: 1, id: 'banteay-meanchey', en: 'Banteay Meanchey', km: 'បន្ទាយមានជ័យ' },
  { no: 2, id: 'battambang', en: 'Battambang', km: 'បាត់ដំបង' },
  { no: 3, id: 'kampong-cham', en: 'Kampong Cham', km: 'កំពង់ចាម' },
  { no: 4, id: 'kampong-chhnang', en: 'Kampong Chhnang', km: 'កំពង់ឆ្នាំង' },
  { no: 5, id: 'kampong-speu', en: 'Kampong Speu', km: 'កំពង់ស្ពឺ' },
  { no: 6, id: 'kampong-thom', en: 'Kampong Thom', km: 'កំពង់ធំ' },
  { no: 7, id: 'kampot', en: 'Kampot', km: 'កំពត' },
  { no: 8, id: 'kandal', en: 'Kandal', km: 'កណ្តាល' },
  { no: 9, id: 'koh-kong', en: 'Koh Kong', km: 'កោះកុង' },
  { no: 10, id: 'kratie', en: 'Kratie', km: 'ក្រចេះ' },
  { no: 11, id: 'mondulkiri', en: 'Mondulkiri', km: 'មណ្ឌលគិរី' },
  { no: 12, id: 'phnom-penh', en: 'Phnom Penh', km: 'ភ្នំពេញ' },
  { no: 13, id: 'preah-vihear', en: 'Preah Vihear', km: 'ព្រះវិហារ' },
  { no: 14, id: 'prey-veng', en: 'Prey Veng', km: 'ព្រៃវែង' },
  { no: 15, id: 'pursat', en: 'Pursat', km: 'ពោធិ៍សាត់' },
  { no: 16, id: 'rattanakkiri', en: 'Ratanakiri', km: 'រតនគិរី' },
  { no: 17, id: 'siem-reap', en: 'Siem Reap', km: 'សៀមរាប' },
  { no: 18, id: 'preah-Sihanouk', en: 'Preah Sihanouk', km: 'ព្រះសីហនុ' },
  { no: 19, id: 'stung-treng', en: 'Stung Treng', km: 'ស្ទឹងត្រែង' },
  { no: 20, id: 'svay-rieng', en: 'Svay Rieng', km: 'ស្វាយរៀង' },
  { no: 21, id: 'takeo', en: 'Takeo', km: 'តាកែវ' },
  { no: 22, id: 'oddar-meanchey', en: 'Oddar Meanchey', km: 'ឧត្តរមានជ័យ' },
  { no: 23, id: 'kep', en: 'Kep', km: 'កែប' },
  { no: 24, id: 'pailin', en: 'Pailin', km: 'ប៉ៃលិន' },
  { no: 25, id: 'tboung-khmum', en: 'Tboung Khmum', km: 'ត្បូងឃ្មុំ' },
];

// Fold the spelling variants that appear across ministries' uploads
// (subscript DA/TA, long/short i, Pailin's vowel, diacritics, zero-width chars).
export function normalizeKhmer(s) {
  return String(s ?? '')
    .normalize('NFC')
    .replace(/[\u200B-\u200D\uFEFF\s.,()\-–—_/]/g, '')
    .replace(/្ដ/g, '្ត') // coeng DA -> coeng TA
    .replace(/ី/g, 'ិ') // ី -> ិ
    .replace(/ៃ/g, 'ែ') // ៃ -> ែ
    .replace(/[៍ៈ៉៊់៌]/g, '') // ៍ ៈ and other diacritics
    .replace(/^(រាជធានី|ខេត្ត)/, '');
}

function levenshtein(a, b) {
  const m = a.length, n = b.length;
  if (!m) return n;
  if (!n) return m;
  let prev = Array.from({ length: n + 1 }, (_, j) => j);
  for (let i = 1; i <= m; i++) {
    const cur = [i];
    for (let j = 1; j <= n; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[n];
}

const CANON = PROVINCES.map((p) => ({ ...p, norm: normalizeKhmer(p.km), enNorm: p.en.toLowerCase().replace(/[^a-z]/g, '') }));

/** Returns the province `id` for a name (Khmer or English) and/or a code, or null. */
export function matchProvince(name, code) {
  const raw = String(name ?? '').trim();
  if (raw) {
    const latin = raw.toLowerCase().replace(/[^a-z]/g, '');
    if (latin.length > 2) {
      const hit = CANON.find((p) => p.enNorm === latin || (latin.length > 4 && p.enNorm.startsWith(latin.slice(0, 6))));
      if (hit) return hit.id;
    }
    const n = normalizeKhmer(raw);
    if (n) {
      let best = null, bestScore = Infinity;
      for (const p of CANON) {
        const d = levenshtein(n, p.norm);
        const score = d / Math.max(p.norm.length, n.length);
        if (score < bestScore) { bestScore = score; best = p; }
      }
      if (best && bestScore <= 0.34) return best.id;
      // A non-empty name that is clearly not a province (e.g. "Total", "Rural") must not
      // fall back to the code.
      return null;
    }
  }
  const m = String(code ?? '').match(/(\d{1,3})$/);
  if (m) {
    const no = Number(m[1]);
    const hit = PROVINCES.find((p) => p.no === no);
    if (hit) return hit.id;
  }
  return null;
}
