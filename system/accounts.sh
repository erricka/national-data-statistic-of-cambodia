#!/bin/bash
# Lists who can sign in to the admin portal: each ministry's upload and approval
# accounts, the administrators, and the temporary password used by "Reset password".
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] || { echo "Start the system first:  bash system/start.sh"; exit 1; }
set -a; . ./.env; set +a

echo
echo "  Your login:  $ADMIN_USER  /  $ADMIN_PASSWORD"
echo
echo "  Staff sign in with their usual NIS username and password."
echo "  Upload accounts can only upload. Approval accounts can upload and approve."
echo
docker compose exec -T db psql -U postgres -d nis -P footer=off -c "
WITH acc AS (
  SELECT u.user_id, r.role_id,
         CASE WHEN u.user_id ~ '^[0-9A-Za-z]+_' THEN split_part(u.user_id, '_', 1) END AS prefix
  FROM users u JOIN user_roles r ON r.user_id = u.user_id
  WHERE u.enabled = 'Y' AND u.user_status = '01')
SELECT COALESCE(c.category_name, '(NIS / other)') AS \"Ministry\",
       string_agg(DISTINCT a.user_id, ', ') FILTER (WHERE a.role_id IN ('UPLOAD', 'NIS_UPLOAD')) AS \"Uploads\",
       string_agg(DISTINCT a.user_id, ', ') FILTER (WHERE a.role_id IN ('APPROVAL', 'ADMIN')) AS \"Uploads + approves\",
       string_agg(DISTINCT a.user_id, ', ') FILTER (WHERE a.role_id = 'REVIEWER') AS \"View only\"
FROM acc a LEFT JOIN category c
  ON c.category_id = CASE WHEN a.prefix ~ '^[0-9]+$' THEN a.prefix::bigint END
GROUP BY 1 ORDER BY min(COALESCE(c.category_id, 1000))"

cat <<MSG

  Forgot a password? Sign in as $ADMIN_USER -> User Management -> open the user ->
  Reset password. Their password becomes:  $RESET_PASSWORD
  Send it to them privately. After signing in they should change it at once:
  key icon (top right) -> Change Password. (Everyone you reset gets this same password.)

  New staff member? User Management -> add the user, and give them the role
  UPLOAD (upload only) or APPROVAL (upload and approve).

MSG
