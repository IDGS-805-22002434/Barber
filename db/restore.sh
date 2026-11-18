#!/usr/bin/env bash
# RNF17: recuperación de la base de datos desde un respaldo creado con backup.sh.
# Uso: DB_PASSWORD=... ./db/restore.sh respaldos/barbermanager_YYYYMMDD_HHMMSS.sql.gz
set -euo pipefail

ARCHIVO="${1:?Indica el archivo de respaldo (.sql.gz)}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-barbermanager}"
DB_USER="${DB_USER:-root}"

export MYSQL_PWD="${DB_PASSWORD:-}"
mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
  -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4"
gunzip -c "$ARCHIVO" | mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" "$DB_NAME"

echo "Base de datos $DB_NAME restaurada desde $ARCHIVO"
