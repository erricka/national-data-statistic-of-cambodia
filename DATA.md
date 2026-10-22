# Project notes: data, changes and how to contribute

## What I changed

I rebuilt the public website of the National Institute of Statistics as a new portal modelled on [SingStat](https://www.singstat.gov.sg/), in English and Khmer.

- **New home page**: search, eight headline figures ("Latest Data"), six themes, long-run charts, a province map and recent releases.
- **A page for every published table**, with the official layout and merged headers, a chart builder and downloads (Excel, CSV, JSON).
- **Search, themes and ministry pages**, a release calendar, an About page, and a layout that works on phones.
- **English titles for all 352 tables**; agency names in both languages.
- **A data export step**: the website reads a snapshot of the published statistics instead of connecting to the database directly.
- **Live on GitHub Pages**: https://erricka.github.io/national-data-statistic-of-cambodia/

The old website addresses (`/detail/...`, `/categories`, `/data-explore`) still work and redirect to the new pages.

## How the data works

The website never connects to PostgreSQL directly. The data reaches it like this:

```
PostgreSQL database  ──►  npm run export:data  ──►  public/data/*.json  ──►  website
(NIS data platform)       (run by staff)            (saved in this repo)
```

- Only published data is exported: active tables, and only uploads approved for release (status `01`), the same rule the NIS server uses. No users, passwords or logs are read.
- The database itself is never changed. The export only reads from it.

### Connecting to our database and refreshing the data

The database address goes in a file called **`.env`** in the project folder. It stays on your computer and is never uploaded to GitHub.

1. Copy `.env.example` to `.env`.
2. Set the database line to our PostgreSQL database — the same one the NIS server and admin portal use. Its address, user and password are in the server project's `src/main/resources/application.yaml` under `spring.datasource` (`jdbc:postgresql://HOST:5432/DB` becomes `postgres://USER:PASSWORD@HOST:5432/DB`):
   ```
   DATABASE_URL=postgres://USER:PASSWORD@DB_HOST:5432/DB_NAME
   NIS_API_URL=https://YOUR-NIS-SERVER/api/nis-portal     # recommended
   ```
3. Refresh the website with one command:
   ```bash
   npm run refresh
   ```
   This exports the approved data from the database (`npm run export:data`), saves the new snapshot in `public/data/` to GitHub, and republishes the website (`npm run deploy:pages`).

Set `NIS_API_URL` too: it lets each table keep the official layout produced by the NIS server. Without it, tables are published as plain rows. The export is done by `scripts/export-data.mjs`.

Use a read-only database account:

```sql
CREATE ROLE portal_reader LOGIN PASSWORD 'choose-a-password';
GRANT CONNECT ON DATABASE DB_NAME TO portal_reader;
GRANT USAGE ON SCHEMA public TO portal_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO portal_reader;
```

### Loading tables live from the NIS server (optional)

In the same `.env` file, set:

```
VITE_DATA_MODE=api
VITE_API_BASE_URL=https://YOUR-NIS-SERVER/api/nis-portal
```

The server must use HTTPS, and its `cors.allowed-origins` setting must include the website's address. Only the tables become live; the home page figures, theme lists and map still come from the snapshot, so keep refreshing it.

## Adding new data with Excel

New data is entered through the **existing NIS admin portal**, not through this website. No code changes are needed for this.

1. **Upload**: sign in to the admin portal → **Data Management → Dataset** → open the dataset → **Add data** → choose the Excel file filled in with that dataset's template. The server reads the file into the dataset's table and marks it **Pending**.
2. **Approve**: a reviewer opens **Approval Management**, checks the upload and clicks **Approve**. Only approved uploads are public.
3. **Publish**: run `npm run refresh` (or let the scheduled refresh below do it). The new figures appear on the website within a few minutes, including the home-page figures and the province map if the upload belongs to their tables.

To publish approvals automatically, schedule `npm run refresh` every night (cron on Linux, Task Scheduler on Windows) on a computer that has this project, the `.env` file, Node.js, and permission to push to GitHub.

### When code changes are needed

| Situation | Code change? |
|---|---|
| New year or month for an existing table, corrections, re-uploads | **No.** Upload, approve, refresh. |
| A new ministry or agency | Small: add it in the admin portal, then add its English name to `scripts/meta/categories.json` and its theme to `scripts/meta/themes.json`. |
| A completely new table (new Excel template) | **Yes, on the NIS server.** A developer adds the database table, a reader for the Excel file (`app/nisXX/migrator/…MigratorService.kt`) and a display layout (`app/nisXX/inquiry/…InquiryService.kt`), then registers the dataset in the admin portal. This website picks the table up automatically on the next refresh; add its English title to `scripts/meta/datasets.en.json`. |
| A new home-page figure or map figure | Small: add its SQL query to `scripts/indicators.config.mjs`. |

## How to add your work

### First time

1. Ask the repository owner to add you under **Settings → Collaborators**.
2. Install [Node.js](https://nodejs.org/) 20 or later and [Git](https://git-scm.com/).
3. Get the code and start it:

```bash
git clone https://github.com/erricka/national-data-statistic-of-cambodia.git
cd national-data-statistic-of-cambodia
npm ci
npm run dev        # open http://localhost:5173
```

### Every change

```bash
git pull                              # get everyone else's latest work
git checkout -b my-change             # work on your own branch
# ...edit files; the browser updates as you save...
npm run build                         # check that nothing is broken
git add -A
git commit -m "Describe what you changed"
git push -u origin my-change
```

Then open a **pull request** on GitHub, ask someone to review it, and merge it into `main`.

### Publishing to the website

The website does not update by itself. After a change is merged, or after refreshing the data:

```bash
git checkout main
git pull
npm run deploy:pages
```

The site updates within one or two minutes. For new data, `npm run refresh` does the export, the GitHub save and the publishing in one step.

## Where to change common things

| To change | Edit |
|---|---|
| Headline figures and map figures (SQL queries) | `scripts/indicators.config.mjs` |
| English titles of tables | `scripts/meta/datasets.en.json` |
| Ministry names, theme groupings | `scripts/meta/categories.json`, `scripts/meta/themes.json` |
| Website text in English and Khmer | `src/i18n/strings.ts` |
| Colours, spacing, layout | `src/styles/app.css` |
| Pages | `src/pages/` |

After editing anything in `scripts/`, run `npm run export:data` again so the snapshot picks up the change.

Known problems in the source data (copied Khmer titles, empty tables, one table the server cannot display) are listed in the [README](README.md#notes-on-the-source-data).
