# NIS admin system

The whole NIS data system (database, NIS server and admin portal) running in your browser with GitHub Codespaces. Use it to add data with Excel, approve it and publish it to the public website: https://erricka.github.io/national-data-statistic-of-cambodia/

The code is in this repository (`server/`, `admin-portal/`). **The database is not.** It contains staff accounts, so you add it yourself, and it stays inside your Codespace.

## What you need

- A GitHub account that the repository owner has added under **Settings → Collaborators** (needed to publish).
- The database file, `01-nis-data.sql.gz`, from the practice-system zip, or a newer backup from a colleague (see [Handing over](#handing-over-to-a-colleague)). Get it privately from the project owner.

## 1. Open the system (first time: about 10 minutes)

1. On the repository page on GitHub, click **Code → Codespaces → Create codespace on main**. A code editor opens in your browser.
2. In the **Explorer** on the left, open `system/database/private` and drag the database file into it.
3. In the **Terminal** at the bottom, run:
   ```bash
   bash system/start.sh
   ```
   The first time, this builds the server and admin portal (5–10 minutes). At the end it prints the **admin portal link**, a **username** and a **password**.
4. Open the link and sign in.

Next time, open the same Codespace from **Code → Codespaces** (don't create a new one) and run `bash system/start.sh` again. It starts in about a minute with all your changes.

## 2. Add data with Excel

1. **Dataset → New Dataset**: enter a title, choose the category (ministry) and the template (table), choose the Excel file filled in with that table's template, and **Submit**. The upload is now **Pending**.
2. **Approval Management**: open the upload, check the preview, and click **Approve**.

> **An approved upload replaces all earlier data of that table.** Always upload the **complete table** (all years), not only the new rows.

## 3. Publish to the website

```bash
bash system/publish.sh
```

This exports the approved data, saves it to GitHub, and the website shows it 1–2 minutes later. If nothing new was approved, it says the website is already up to date. **Anything you publish is public.**

Before publishing, it checks that this copy has every upload already on the website. If someone published from a different copy, it stops instead of removing their data. Then get their backup (below) or let them publish.

## Commands

| Command | What it does |
|---|---|
| `bash system/start.sh` | Starts the system and prints the link and login. |
| `bash system/publish.sh` | Puts approved data on the public website. |
| `bash system/backup.sh` | Saves the whole database to `system/database/private/nis-backup-<date>.sql.gz`. |
| `bash system/stop.sh` | Stops the system; your data is kept. |
| `bash system/reset.sh` | Deletes everything in this copy and reloads the newest file in `system/database/private`. |

## Keep your work safe

- Your data lives only in your Codespace. **GitHub deletes Codespaces that are not used for about 30 days.** Run `bash system/backup.sh` after important work, then right-click the backup in the Explorer → **Download**.
- Backups and the database file contain staff accounts. Share them only privately, never on GitHub or in group chats.
- Stop the Codespace when you are done (**Code → Codespaces → … → Stop codespace**) to save your free Codespaces hours.

## Handing over to a colleague

Every Codespace has its own copy of the database. To let someone else continue:

1. Run `bash system/backup.sh` and download the backup.
2. Send it to them privately.
3. They drag it into `system/database/private` in their Codespace and run `bash system/reset.sh` (or `bash system/start.sh` on a new Codespace). The newest file in the folder is loaded.

Simplest: let **one person** do the publishing, and hand over the backup when that changes.

## Good to know

- This is a separate copy. Uploads here do not reach the real NIS server, and data on the real server does not appear here.
- The login is created for this copy (`nis-admin`). The staff accounts in the database keep their own passwords.
- All settings and generated passwords are in `system/.env` in your Codespace; it is never saved to GitHub.
- Something wrong? `docker compose -f system/docker-compose.yml logs server` shows the server log (`db`, `admin` for the others).

## How it fits together

```
admin portal (admin-portal/, port 8080) ── /api/ ──► NIS server (server/) ──► PostgreSQL (your database file)
                                                                                    │
                                              bash system/publish.sh ◄──────────────┘
                                                        │  exports approved data to public/data/, pushes to GitHub
                                                        ▼
                                     "Deploy website" GitHub Action ──► public website (GitHub Pages)
```

Files: `docker-compose.yml` runs the three parts; `database/init/` loads your database file and creates the `nis-admin` login on the first start; `lib/check-releases.mjs` is the safety check used by `publish.sh`. Codespaces settings are in `.devcontainer/devcontainer.json`.

**Without Codespaces**: on any computer with Docker and Git, clone the repository, put the database file in `system/database/private`, and run the same commands (the admin portal is at http://localhost:8080/login).
