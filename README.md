# fintrack-api

Backend API for **Fintrack**, a personal financial tracking app: bank accounts, credit cards, categorized
debits/credits, fixed/recurring transactions, credit card installments, and credit card invoices with
pay/unpay. Built with Spring Boot 4, Java 21, Spring Data JPA, Flyway, and secured with a Keycloak-issued
JWT (OAuth2 Resource Server).

## Stack

- Java 21, Spring Boot 4, Gradle
- PostgreSQL (via Flyway migrations in `src/main/resources/db/migration`)
- Keycloak as the identity provider (OAuth2/OIDC), the API validates JWTs — it never issues its own tokens
- springdoc-openapi for Swagger UI

## Prerequisites

- JDK 21
- Docker + Docker Compose

## 1. Configure environment

```bash
cp .env.example .env
```

The defaults work out of the box for local development; edit `.env` if you want different credentials.

## 2. Start Postgres and Keycloak

```bash
docker compose up -d
```

This starts `postgres` and `keycloak` (the `api` service is opt-in, see below). Keycloak auto-imports a
`fintrack` realm with a public client `fintrack-api` and a demo user (`demo` / `demo123`) — no manual setup
needed in the Keycloak admin console.

Wait for both to report healthy:

```bash
docker compose ps
```

## 3. Run the app

```bash
./gradlew bootRun
```

The app connects to the Postgres and Keycloak containers on `localhost` by default (see
`src/main/resources/application.yaml`) and runs Flyway migrations automatically on startup.

Alternatively, run the API itself inside Docker using the `api` compose profile (uses the `dev` build stage):

```bash
docker compose --profile api up --build
```

## 4. Get an access token

```bash
curl -s -X POST http://localhost:8081/realms/fintrack/protocol/openid-connect/token \
  -d 'client_id=fintrack-api' \
  -d 'grant_type=password' \
  -d 'username=demo' \
  -d 'password=demo123' | jq -r .access_token
```

Use the resulting token as a bearer token against the API, e.g.:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/fintrack/protocol/openid-connect/token \
  -d 'client_id=fintrack-api' -d 'grant_type=password' -d 'username=demo' -d 'password=demo123' \
  | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/bank-accounts
```

All `/api/**` endpoints require a valid bearer token. Resources (bank accounts, credit cards, categories,
transactions, templates, invoices) are scoped to the token's `sub` claim.

## API docs

Swagger UI: http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs

## Testing

```bash
./gradlew test
```

Tests use Testcontainers to run against a real PostgreSQL instance (Flyway migrations included) — no
external services need to be running; Docker is required.

## Docker image

The `Dockerfile` is multi-stage:

- `build` — compiles and packages the app with Gradle
- `dev` — full JDK image running `bootRun`, used by the `api` compose profile for container-based iteration
- `prod` — minimal JRE image running the packaged jar, intended for deployment

```bash
docker build --target prod -t fintrack-api:latest .
```

## Project structure

```
domain/       JPA entities and enums
repository/   Spring Data JPA repositories
service/      business logic
web/          REST controllers
dto/          request/response records
mapper/       entity <-> DTO mapping
security/     OAuth2 resource server config, current-user resolution
config/       misc configuration (scheduling, OpenAPI)
exception/    error handling
```
