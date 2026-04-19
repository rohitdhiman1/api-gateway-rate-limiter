INSERT INTO api_clients (id, email, name, api_key, api_secret, tier, enabled, created_at)
VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'demo-free@example.com', 'Demo Free User',
     'demo-free-api-key', 'demo-free-api-secret', 'FREE', true, now()),
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'demo-premium@example.com', 'Demo Premium User',
     'demo-premium-api-key', 'demo-premium-api-secret', 'PREMIUM', true, now());
