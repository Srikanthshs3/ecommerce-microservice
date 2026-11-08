-- =====================================================================================================================
--  MySQL bootstrap (infrastructure only): creates the databases of the services that use MySQL.
--  Tables are NOT created here: each service creates and upgrades its own tables with Flyway
--  (src/main/resources/db/migration in that service) when it starts.
--
--  Runs ONCE, the first time the mysql container starts with an empty data volume (docker-entrypoint-initdb.d).
--  Idempotent: safe to run again by hand:
--      docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < init-scripts/mysql/01-create-databases.sql
--  Add a new MySQL-backed service = add one line below.
-- =====================================================================================================================

CREATE DATABASE IF NOT EXISTS user_service  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;  -- user-service
CREATE DATABASE IF NOT EXISTS cart_db       CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;  -- cart-service
CREATE DATABASE IF NOT EXISTS inventory_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;  -- inventory-service
CREATE DATABASE IF NOT EXISTS shipping_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;  -- shipping-service
CREATE DATABASE IF NOT EXISTS review_db     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;  -- review-service
CREATE DATABASE IF NOT EXISTS coupon_db     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;  -- coupon-service
