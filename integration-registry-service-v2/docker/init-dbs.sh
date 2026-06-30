#!/bin/bash
set -e
for tenant in kgh kgsc; do
    DB="tenant_${tenant}_integrationregistry"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -c "SELECT 'CREATE DATABASE $DB' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB')\gexec"
    echo "Tenant DB ready: $DB"
done

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -c "SELECT 'CREATE DATABASE platform_catalogue' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'platform_catalogue')\gexec"
echo "Platform catalogue DB ready (shared with tenant-config-service if co-located)"
