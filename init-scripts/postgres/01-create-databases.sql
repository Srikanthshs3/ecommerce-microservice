-- =====================================================================================================================
--  Postgres bootstrap (infrastructure only): creates the databases of the services that use Postgres.
--  Tables are NOT created here: each service creates and upgrades its own tables with Flyway
--  (src/main/resources/db/migration in that service) when it starts.
--
--  Runs ONCE, the first time the postgres container starts with an empty data volume
--  (docker-entrypoint-initdb.d). It is idempotent, so running it again by hand is safe:
--      docker compose exec -T postgres psql -U postgres -f /docker-entrypoint-initdb.d/01-create-databases.sql
--  Add a new Postgres-backed service = add one block below.
-- =====================================================================================================================

-- order-service
SELECT 'CREATE DATABASE order_db ENCODING ''UTF8'''
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'order_db')\gexec

-- payment-service
SELECT 'CREATE DATABASE payment_db ENCODING ''UTF8'''
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payment_db')\gexec

-- Store and compare every timestamp in UTC (the services use TIMESTAMPTZ and java.time.Instant).
ALTER DATABASE order_db   SET timezone TO 'UTC';
ALTER DATABASE payment_db SET timezone TO 'UTC';
