#!/bin/bash
# Runs once, when the PostgreSQL container initialises an empty data volume.
#
# Each service gets its own database, owned by its own login user, and no
# other user may connect to it. The Identity Service uses POSTGRES_DB
# (identity_db) as POSTGRES_USER, which the image itself creates.
set -euo pipefail

create_service_database() {
	local database=$1 owner=$2 password=$3
	psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
		-v database="$database" -v owner="$owner" -v password="$password" <<-'SQL'
		CREATE ROLE :"owner" LOGIN PASSWORD :'password';
		CREATE DATABASE :"database" OWNER :"owner";
		REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
	SQL
}

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
	-v database="$POSTGRES_DB" <<-'SQL'
	REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
SQL

create_service_database parcel_db parcel_service "$PARCEL_DB_PASSWORD"
create_service_database merchant_db merchant_service "$MERCHANT_DB_PASSWORD"
create_service_database courier_db courier_service "$COURIER_DB_PASSWORD"
create_service_database dispatch_db dispatch_service "$DISPATCH_DB_PASSWORD"
create_service_database pricing_db pricing_service "$PRICING_DB_PASSWORD"
create_service_database notification_db notification_service "$NOTIFICATION_DB_PASSWORD"
