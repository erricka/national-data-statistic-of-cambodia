# NIS admin system

Works like the team's NIS data portal: **one admin portal link** for everyone. Staff sign in with their usual NIS account, upload an Excel file, an approver approves it, and **the public website updates by itself** a few minutes later:
https://erricka.github.io/national-data-statistic-of-cambodia/

The system (database, NIS server and admin portal) runs in one GitHub Codespace, a computer in the cloud that GitHub runs for the owner. The code is in this repository (`server/`, `admin-portal/`). **The database is not.** It contains staff accounts, so the owner adds it once and it stays inside the Codespace.

```
Staff (browser) ──► admin portal link ──► NIS server ──► database
                                                            │ every approval
                                                            ▼
                                  public website updates by itself (2–3 minutes)
```

---

## For staff: adding new data (no setup)

1. Open the **admin portal link** you were given (bookmark it). If GitHub shows a notice first, click **Continue**.
2. Sign in with **your usual NIS account** (for example your ministry's upload account).
3. **Dataset → New Dataset**: enter a title, choose the category (ministry) and the template (table), choose the Excel file filled in with that table's template, and click **Submit**. The upload is now **Pending**.
4. The **approver** (for example your ministry's approval account) signs in, opens **Approval Management**, checks the preview and clicks **Approve**.
5. That's all. The public website shows the new data **2–3 minutes** later.

> **An approved upload replaces all earlier data of that table.** Always upload the **complete table** (all years), not only the new rows.

If the link does not open, the system is switched off. Ask the owner to open it (below).

---

## For the owner: keeping the system running

### First time (about 15 minutes)

1. Ask for the database file `01-nis-data.sql.gz` (from the practice-system zip) privately.
2. Click **[Open the NIS admin system](https://codespaces.new/erricka/national-data-statistic-of-cambodia?quickstart=1)** (or on the repository page: **Code → Codespaces → Create codespace on main**). A code editor opens in your browser.
3. In the **Explorer** on the left, open `system/database/private` and drag the database file into it.
4. In the **Terminal** at the bottom, type `bash system/start.sh` and press Enter. The first time takes 5–10 minutes. At the end it shows the **admin portal link** and your own login (`nis-admin`).
5. Send the link to staff. Staff use their usual NIS accounts (the passwords from the NIS system at the time of the backup).
6. On github.com, open **Settings → Codespaces** (your account settings) and set **Default idle timeout** to **240 minutes** and **Default retention period** to **30 days**.

### Every work day

Click **[Open the NIS admin system](https://codespaces.new/erricka/national-data-statistic-of-cambodia?quickstart=1)** again (it reopens your existing Codespace). The system starts by itself within a few minutes and the link works again; nothing to type. Keep the browser tab open while staff work.

The link only works while the Codespace is running. GitHub stops it after the idle timeout (up to 4 hours without you using the tab), and then the link stops working until you open it again.

### If staff cannot open the link

In the Codespace, open the **Ports** tab (next to Terminal), right-click port **8080** → **Port Visibility** → **Public**. (The system does this by itself when it starts; some organisations do not allow it.)

### Commands (optional, in the Terminal)

| Command | What it does |
|---|---|
| `bash system/start.sh` | Starts the system (if it did not start by itself) and shows the link and login. |
| `bash system/publish.sh` | Publishes approved data now, instead of waiting for the automatic publishing. |
| `bash system/backup.sh` | Saves the whole database to `system/database/private/nis-backup-<date>.sql.gz`. |
| `bash system/stop.sh` | Stops the system; data is kept. |
| `bash system/reset.sh` | Deletes all changes and reloads the newest file in `system/database/private`. |

What the automatic publishing did is in `system/auto-publish.log`.

### Keep the data safe

- All uploads live in this one Codespace. **GitHub deletes a Codespace that has not been opened for 30 days.** Run `bash system/backup.sh` every week or after important uploads, then right-click the backup in the Explorer → **Download**.
- Backups and the database file contain staff accounts. Keep them private, never on GitHub or in group chats.
- Use **one** Codespace for the whole team. A second Codespace would have its own separate data. If a different person takes over, give them the latest backup: they drag it into `system/database/private` in their Codespace and run `bash system/start.sh` (or `bash system/reset.sh`).
- Before publishing, the system checks that it has every upload already on the website and refuses otherwise, so a second copy cannot remove published data.

## Good to know

- This is a separate copy of the NIS system. Uploads here do not reach the team's NIS server, and data uploaded there does not appear here.
- Anyone with the link can see the sign-in page. Staff passwords should be strong, and accounts that are no longer needed should be disabled in **User Management**.
- Settings and generated passwords are in `system/.env` inside the Codespace; it is never saved to GitHub. `AUTO_PUBLISH=no` turns automatic publishing off; `SHARE_LINK=no` keeps the link private to the owner.
- Logs: `docker compose -f system/docker-compose.yml logs server` (or `db`, `admin`).
- **Without Codespaces**: on any always-on computer with Docker and Git, clone the repository, put the database file in `system/database/private` and run `bash system/start.sh`. The admin portal is then at `http://<that computer>:8080/login`. That computer needs permission to push to this repository for automatic publishing.

## How it works

- `docker-compose.yml` runs PostgreSQL 18, the NIS server (`server/`) and the admin portal (`admin-portal/`, port 8080, which forwards `/api/` to the server).
- `database/init/` loads the database file and creates the `nis-admin` login on the first start.
- `auto-publish.sh` checks every minute whether the set of approved uploads changed and then runs `publish.sh`: it exports the approved data to `public/data/`, checks nothing published elsewhere would be lost (`lib/check-releases.mjs`) and pushes to GitHub. The **Deploy website** GitHub Action then rebuilds the website.
- `.devcontainer/devcontainer.json` sets up the Codespace and starts the system whenever the Codespace starts.
