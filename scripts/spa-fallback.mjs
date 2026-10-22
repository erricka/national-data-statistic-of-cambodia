// Static hosts such as GitHub Pages serve 404.html for unknown paths; copying the
// app shell there lets deep links like /datasets/nis_05a1 load the SPA.
import fs from 'node:fs/promises';
await fs.copyFile('dist/index.html', 'dist/404.html');
