# planelyx-api

Backend API for **Planelyx**, a personal financial tracking app: bank accounts, credit cards, categorized
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
`planelyx` realm with a public client `planelyx-api` and a demo user (`demo` / `Demo@Fintrack1`) — no manual setup
needed in the Keycloak admin console.

Self-registration is enabled: the login page shows a "Register" link, and new users are subject to the realm
password policy — at least 12 characters with an uppercase letter, a lowercase letter, a digit and a special
character, and not equal to the username or email. Email verification is off, since the realm has no SMTP
server configured.

The client's redirect URIs and web origins are scoped to the Angular app's origin rather than `*`. That origin
defaults to `http://localhost:4200` and can be overridden by setting `PLANELYX_UI_ORIGIN` on the `keycloak`
container (Keycloak substitutes `${VAR:default}` placeholders at realm-import time).

Note that `--import-realm` only imports when the realm does not yet exist. If you already have a `planelyx`
realm in the `keycloak` database, edits to `realm-export.json` are ignored on restart — change the setting in
the admin console instead, or recreate the realm.

### Login theme

Keycloak's own login, registration and account-recovery screens are branded by the `planelyx` theme in
`docker/keycloak/themes/planelyx`, mounted into the container and selected via the realm's `loginTheme`.

It inherits from Keycloak's built-in `keycloak.v2` theme and adds a single stylesheet, so no FreeMarker
templates are copied and upgrades stay cheap. The palette mirrors the Angular app's PrimeNG "Aura" preset
(emerald primary, zinc surfaces) and follows the OS light/dark preference. To restyle, edit
`login/resources/css/planelyx.css` — `start-dev` disables theme caching, so a browser refresh is enough.

Structural changes (moving fields, changing markup) mean copying the relevant `.ftl` out of Keycloak's
`org.keycloak.keycloak-themes-*.jar` into `login/` and editing it there. Note that the registration form's
fields are declarative — add or reorder them under Realm settings → User profile, not in `register.ftl`.

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
curl -s -X POST http://localhost:8081/realms/planelyx/protocol/openid-connect/token \
  -d 'client_id=planelyx-api' \
  -d 'grant_type=password' \
  -d 'username=demo' \
  -d 'password=Demo@Fintrack1' | jq -r .access_token
```

Use the resulting token as a bearer token against the API, e.g.:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/planelyx/protocol/openid-connect/token \
  -d 'client_id=planelyx-api' -d 'grant_type=password' -d 'username=demo' -d 'password=Demo@Fintrack1' \
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

## Code style

Formatting (import order, indentation, brace style) is enforced with [Spotless](https://github.com/diffplug/spotless)
using `palantir-java-format`, wired into the `check` task.

```bash
./gradlew spotlessApply   # auto-fix formatting
./gradlew spotlessCheck   # verify formatting (runs as part of ./gradlew check)
```

Blank lines before `return` statements and around `if`/`else` blocks are a manual convention (no formatter
enforces statement-level spacing like this) — please follow it when adding new code.

## Docker image

The `Dockerfile` is multi-stage:

- `build` — compiles and packages the app with Gradle
- `dev` — full JDK image running `bootRun`, used by the `api` compose profile for container-based iteration
- `prod` — minimal JRE image running the packaged jar, intended for deployment

```bash
docker build --target prod -t planelyx-api:latest .
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
