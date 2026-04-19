CREATE TABLE api_clients (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    api_key     VARCHAR(255) NOT NULL UNIQUE,
    api_secret  VARCHAR(255) NOT NULL,
    tier        VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_clients_api_key ON api_clients(api_key);
CREATE INDEX idx_api_clients_email ON api_clients(email);
