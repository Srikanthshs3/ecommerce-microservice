-- Create order_db if it does not already exist
SELECT 'CREATE DATABASE order_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'order_db')\gexec

-- Create payment_db if it does not already exist
SELECT 'CREATE DATABASE payment_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payment_db')\gexec

-- Grant permissions to default user
GRANT ALL PRIVILEGES ON DATABASE order_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO postgres;