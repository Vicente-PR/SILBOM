#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TEST_DB_USERNAME:?Defina el rol PostgreSQL no propietario de la aplicación}"
: "${TEST_DB_PASSWORD:?Defina su contraseña sin escribirla en el repositorio}"
[[ "$TEST_DB_USERNAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || { echo 'Nombre de rol inválido'; exit 1; }
test_database="silbom_test_$(date +%s)_$$"
createdb "$test_database"
cleanup() { dropdb --if-exists "$test_database"; }
trap cleanup EXIT
psql -X -v ON_ERROR_STOP=1 -v app_role="$TEST_DB_USERNAME" -d "$test_database" -f db/schema.sql
# Repetición real: el esquema debe conservar datos y no fallar al aplicar dos veces.
# Simula el nombre de la política permisiva original para cubrir una actualización real.
psql -X -v ON_ERROR_STOP=1 -d "$test_database" -c "CREATE POLICY bloqueos_planta_isolation_policy ON bloqueos_loto USING (planta_id='PLANTA-SAN-JOAQUIN');"
psql -X -v ON_ERROR_STOP=1 -v app_role="$TEST_DB_USERNAME" -d "$test_database" -f db/schema.sql
export TEST_DB_URL="jdbc:postgresql://${TEST_DB_HOST:-127.0.0.1}:${PGPORT:-5432}/$test_database"
mvn -B -ntp -Ppostgres-it verify
