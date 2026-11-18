#!/usr/bin/env bash
# RNF17: copia de seguridad de la base de datos de BarberManager.
# Uso: DB_PASSWORD=... ./db/backup.sh [carpeta_destino]
# Programa una copia diaria con cron, por ejemplo: 0 3 * * * DB_PASSWORD=... /ruta/db/backup.sh /respaldos
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-barbermanager}"
DB_USER="${DB_USER:-root}"
DESTINO="${1:-./respaldos}"
RETENCION_DIAS="${RETENCION_DIAS:-14}"

mkdir -p "$DESTINO"
ARCHIVO="$DESTINO/${DB_NAME}_$(date +%Y%m%d_%H%M%S).sql.gz"

MYSQL_PWD="${DB_PASSWORD:-}" mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
  --single-transaction --routines --triggers "$DB_NAME" | gzip > "$ARCHIVO"

# Se conservan solo los respaldos de los últimos RETENCION_DIAS días.
find "$DESTINO" -name "${DB_NAME}_*.sql.gz" -mtime +"$RETENCION_DIAS" -delete

echo "Respaldo creado: $ARCHIVO"
