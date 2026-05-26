# API Gateway with Rate Limiting

A production-grade API Gateway built with Java 17 and Spring Boot 3, demonstrating enterprise backend patterns: JWT authentication, sliding-window rate limiting, circuit breaking, and request routing to downstream microservices.

## Architecture

```
┌─────────────┐     ┌──────────────────────────────────────────────┐
│   Client     │────▶│              API Gateway (:8080)             │
└─────────────┘     │                                              │
                    │  ┌─────────────┐  ┌──────────────────────┐   │
                    │  │ Correlation  │─▶│  JWT Authentication  │   │
                    │  │ ID Filter    │  │  Filter              │   │
                    │  └─────────────┘  └──────────────────────┘   │
                    │                           │                   │
                    │                   ┌───────▼──────────────┐   │
                    │                   │  Rate Limit Filter    │   │
                    │                   │  (Redis sliding window│   │
                    │                   └───────┬──────────────┘   │
                    │                           │                   │
                    │                   ┌───────▼──────────────┐   │
                    │                   │  Proxy + Circuit      │   │
                    │                   │  Breaker (Resilience4j│   │
                    │                   └───────┬──────────────┘   │
                    └───────────────────────────┼──────────────────┘
                                                │
                    ┌───────────────────────────┼──────────────────┐
                    │                           │                   │
              ┌─────▼─────┐  ┌─────────▼──────┐  ┌──────▼────────┐
              │   User     │  │   Pricing      │  │ Notification  │
              │   Service  │  │   Service      │  │ Service       │
              │   (:8081)  │  │   (:8082)      │  │ (:8083)       │
              └────────────┘  └────────────────┘  └───────────────┘
```

### Request Lifecycle

1. **CorrelationIdFilter** — generates or propagates `X-Correlation-ID` header, sets it in MDC for structured logging across all services
2. **JwtAuthenticationFilter** — extracts Bearer token, validates JWT signature and expiry, injects `clientId` and `tier` into request attributes. Public paths (`/auth/*`, `/actuator/*`, `/swagger-ui/*`) bypass this filter
3. **RateLimitFilter** — checks the client's request count against their tier limit using Redis. Adds `X-RateLimit-Limit`, `X-RateLimit-Remaining` headers to every response. Returns 429 with `Retry-After` when exceeded
4. **RequestLoggingFilter** — logs method, URI, status code, duration, and client ID for every request
5. **GatewayController** — resolves the target downstream service from the URL path prefix and delegates to `ProxyService`
6. **ProxyService** — forwards the request via WebClient, wrapped in a Resilience4j `@CircuitBreaker`. Returns 503 with a meaningful error when the breaker trips

### Layered Architecture

Each module follows a clean separation of concerns:
- **Controllers** — HTTP request handling, input validation, response mapping
- **Services** — business logic (JWT generation/validation, rate limit checks, auth flows, request proxying)
- **Repositories** — Spring Data JPA interfaces for database access
- **DTOs** — request/response objects, separate from JPA entities
- **Entities** — JPA-mapped domain objects (`ApiClient` with UUID primary key, `ClientTier` enum)
- **Filters** — cross-cutting concerns as ordered servlet filters
- **Exception Handler** — `@RestControllerAdvice` with consistent `ErrorResponse` format across all error types (validation, auth, rate limit, 500)

### Data Layer

- **PostgreSQL** — stores API clients (credentials, tier, enabled status). Schema managed by Flyway migrations (`V1__create_api_clients.sql`), with seed data for demo clients (`V2__seed_demo_clients.sql`)
- **Redis** — sliding window rate limit counters using sorted sets. A Lua script ensures atomic check-and-increment to prevent race conditions under concurrent load

### Design Decisions

- **Sliding Window Rate Limiting**: Uses Redis sorted sets with a Lua script for atomic check-and-increment. More accurate than fixed-window counters — no burst allowance at window boundaries.
- **Filter Chain Architecture**: Request processing flows through ordered servlet filters (Correlation ID → JWT Auth → Rate Limit → Logging), keeping each concern isolated and testable.
- **Circuit Breaker**: Resilience4j with count-based sliding window. Trips after 50% failure rate over 10 calls, waits 30s in open state, then allows 3 probe requests in half-open.
- **JWT over API Key per-request**: Clients register once for an API key/secret, then exchange for short-lived JWTs. Reduces database lookups on every request.
- **Tiered Rate Limits**: FREE tier gets 100 req/min, PREMIUM gets 1000 req/min. Tier is embedded in the JWT claim, so no extra DB call during rate limiting.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Cache/Rate Limiting | Redis 7 |
| Migrations | Flyway |
| Circuit Breaker | Resilience4j |
| Auth | JWT (jjwt) |
| API Docs | SpringDoc OpenAPI |
| Monitoring | Spring Boot Actuator |
| Testing | JUnit 5, Testcontainers, MockMvc |
| Containerization | Docker, Docker Compose |

## Getting Started

### Prerequisites
- Docker and Docker Compose

### Setup

1. **Copy the environment template**:
   ```bash
   cp .env.example .env
   ```

2. **Generate a secure JWT secret**:
   ```bash
   openssl rand -base64 64
   ```
   
3. **Edit `.env`** and set your configuration:
   ```
   # Database
   DB_URL=jdbc:postgresql://localhost:5432/gateway_db
   DB_USERNAME=gateway
   DB_PASSWORD=your-secure-database-password
   
   # Redis
   REDIS_HOST=localhost
   REDIS_PORT=6379
   
   # JWT
   JWT_SECRET=your-generated-jwt-secret-from-step-2
   
   # Service URLs
   GATEWAY_ROUTES_USER_SERVICE_URL=http://localhost:8081
   GATEWAY_ROUTES_PRICING_SERVICE_URL=http://localhost:8082
   GATEWAY_ROUTES_NOTIFICATION_SERVICE_URL=http://localhost:8083
   ```
   
   Note: For local development, the default values in `.env` work out of the box.
   For production, update the database and Redis connection details accordingly.

### Run

```bash
docker-compose up --build
```

All four services, PostgreSQL, and Redis start automatically. The database is migrated and seeded with demo clients on startup.

## Testing the API

### 1. Register a new client

```bash
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"My App","email":"dev@example.com","tier":"FREE"}' | jq
```

### 2. Get an access token (using seeded demo client)

```bash
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"demo-free-api-key","apiSecret":"demo-free-api-secret"}' | jq
```

Save the token:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"demo-free-api-key","apiSecret":"demo-free-api-secret"}' | jq -r '.accessToken')
```

### 3. Route to downstream services

```bash
# User service
curl -s http://localhost:8080/api/users/ -H "Authorization: Bearer $TOKEN" | jq

# Pricing service
curl -s http://localhost:8080/api/pricing/ -H "Authorization: Bearer $TOKEN" | jq

# Notification service
curl -s http://localhost:8080/api/notifications/ -H "Authorization: Bearer $TOKEN" | jq

# Specific resources
curl -s http://localhost:8080/api/pricing/BTC -H "Authorization: Bearer $TOKEN" | jq
curl -s http://localhost:8080/api/users/USR-001 -H "Authorization: Bearer $TOKEN" | jq
```

### 4. Test rate limiting

```bash
# Send 105 requests rapidly (FREE tier = 100/min)
for i in $(seq 1 105); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    http://localhost:8080/api/users/ -H "Authorization: Bearer $TOKEN")
  echo "Request $i: $STATUS"
done
# Requests 101+ return 429 with Retry-After header
```

### 5. Test rate limit headers

```bash
curl -v http://localhost:8080/api/users/ -H "Authorization: Bearer $TOKEN" 2>&1 | grep -i "x-ratelimit\|retry-after"
```

### 6. Test circuit breaker

```bash
# Stop a downstream service
docker-compose stop user-service

# Requests to that service will fail, eventually tripping the breaker
for i in $(seq 1 10); do
  curl -s http://localhost:8080/api/users/ -H "Authorization: Bearer $TOKEN" | jq .status
done
# After enough failures, returns 503 immediately (circuit open)

# Restart and recovery
docker-compose start user-service
```

### 7. Check health and metrics

```bash
curl -s http://localhost:8080/actuator/health | jq
curl -s http://localhost:8080/actuator/circuitbreakers | jq
```

### 8. Swagger UI

Open http://localhost:8080/swagger-ui in a browser.

## Running Tests

Tests use Testcontainers, so Docker must be running:

```bash
cd api-gateway
mvn test
```

The integration test suite verifies:
- Full register → authenticate → access flow
- Rate limiter allows 100 requests, rejects the 101st with 429 and Retry-After header, then recovers after window reset
- Auth endpoints are exempt from rate limiting
- Unauthenticated requests return 401

## Project Structure

```
api-gateway-rate-limiter/
├── api-gateway/              # Core gateway service
│   ├── controller/           # Auth + proxy endpoints
│   ├── filter/               # JWT, rate limit, correlation ID, logging
│   ├── service/              # JWT, auth, rate limiting, proxy logic
│   ├── entity/               # JPA entities
│   ├── repository/           # Spring Data repositories
│   ├── dto/                  # Request/response objects
│   ├── exception/            # Global error handling
│   └── config/               # Redis, Swagger, Resilience4j
├── user-service/             # Mock user microservice
├── pricing-service/          # Mock pricing microservice
├── notification-service/     # Mock notification microservice
└── docker-compose.yml        # Full stack orchestration
```
