#!/bin/bash
# First start only: create the administrator account. start.sh chooses the password
# (stored in system/.env). It is hashed the way the NIS server checks it: bcrypt of
# "<profile>|<user>|<password>" with the "default" profile.
set -euo pipefail
user="${ADMIN_USER:-nis-admin}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is not set; start the system with system/start.sh}"

psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v user="$user" -v raw="default|$user|$ADMIN_PASSWORD" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO public.users (user_id, process_timestamp, user_status, first_name, last_name, password, email,
  enabled, requires_password_reset, non_locked, user_type, branch_code, effective_date, failed_login_attempts,
  first_register_user, first_register_date, first_register_time)
VALUES (:'user', now(), '01', 'System', 'Admin', crypt(:'raw', gen_salt('bf', 10)), 'admin@localhost',
  'Y', 'N', 'N', 'ADMIN', '', now(), '0', 'SYSTEM', to_char(now(), 'YYYYMMDD'), to_char(now(), 'HH24MISSMS'))
ON CONFLICT (user_id) DO UPDATE SET password = EXCLUDED.password, user_type = 'ADMIN',
  enabled = 'Y', user_status = '01', requires_password_reset = 'N', failed_login_attempts = '0';
INSERT INTO public.user_roles (user_id, role_id, process_timestamp, status, first_register_user, first_register_date, first_register_time)
SELECT :'user', 'ADMIN', now(), '01', 'SYSTEM', to_char(now(), 'YYYYMMDD'), to_char(now(), 'HH24MISSMS')
WHERE NOT EXISTS (SELECT 1 FROM public.user_roles WHERE user_id = :'user' AND role_id = 'ADMIN');
SQL
echo "Created administrator '$user'"
