#!/bin/bash
# Creates tenant audit databases on first Postgres startup
set -e
for tenant in kgh kgsc; do
    DB="tenant_${tenant}_audit"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -c "SELECT 'CREATE DATABASE $DB' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB')\gexec"
    echo "Database ready: $DB"
done
