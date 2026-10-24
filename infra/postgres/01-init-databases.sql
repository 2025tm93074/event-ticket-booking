-- Database-per-service: every service gets its own database and its own credentials.
-- CONNECT is revoked from PUBLIC so a service user cannot open another service's database.

CREATE USER catalog_user WITH PASSWORD 'catalog_pass';
CREATE DATABASE catalog_db OWNER catalog_user;
REVOKE CONNECT ON DATABASE catalog_db FROM PUBLIC;

CREATE USER inventory_user WITH PASSWORD 'inventory_pass';
CREATE DATABASE inventory_db OWNER inventory_user;
REVOKE CONNECT ON DATABASE inventory_db FROM PUBLIC;

CREATE USER booking_user WITH PASSWORD 'booking_pass';
CREATE DATABASE booking_db OWNER booking_user;
REVOKE CONNECT ON DATABASE booking_db FROM PUBLIC;

CREATE USER payment_user WITH PASSWORD 'payment_pass';
CREATE DATABASE payment_db OWNER payment_user;
REVOKE CONNECT ON DATABASE payment_db FROM PUBLIC;
