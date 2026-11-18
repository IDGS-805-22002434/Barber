# Política de respaldo y recuperación (RNF17)

**Qué se respalda:** la base de datos MySQL `barbermanager` (usuarios, citas, horarios, notificaciones, promociones y cupones). La base local de la app (Room) vive en cada teléfono y no se respalda en la nube.

| Elemento | Política |
|---|---|
| Frecuencia | Una copia completa diaria (3:00 a. m.) y una antes de cada actualización del servidor |
| Herramienta | `db/backup.sh` (`mysqldump --single-transaction`, comprimido con gzip) |
| Retención | 14 días (variable `RETENCION_DIAS`) |
| Ubicación | Carpeta fuera del servidor de la base, por ejemplo un disco externo o almacenamiento en la nube |
| Recuperación | `db/restore.sh <archivo.sql.gz>`; probar la restauración en una base de prueba al menos una vez al mes |

Ejemplo de cron: `0 3 * * * DB_PASSWORD=*** /ruta/Barber/db/backup.sh /respaldos`

Después de restaurar, reinicia el servidor Ktor. Las contraseñas están cifradas (PBKDF2) y los códigos de recuperación se guardan solo como hash, por lo que el respaldo no expone credenciales en texto plano.
