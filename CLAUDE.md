# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Monorepo for a local online shopping website with three top-level parts:

- `frontend/` — TypeScript + Next.js (App Router) + React + TailwindCSS/DaisyUI
- `backend/` — Scala 3 + http4s + Cats Effect + Doobie, served on `localhost:8080`
- `openapi/` — the OpenAPI spec that acts as the **shared contract** between the two

## The OpenAPI contract is the source of truth

`openapi/backend-server.yaml` defines all API types and endpoints. Both sides generate code from it — **never hand-edit the generated types; change the spec and regenerate.**

- Frontend: `cd frontend && npm run gen-api-types` → `src/api/schema.d.ts` (via `openapi-typescript`)
- Backend: `sbt compile` runs `openApiGenerate` (openapi-generator `scala-http4s-server`, wired via `sourceGenerators` in `build.sbt`) into `target/openapi-generated/`, producing:
  - `shopping.backend.models.*` — the request/response case classes with circe codecs (e.g. `LoginRequest`, `Product`, `ProductList`), and
  - `shopping.backend.apis.*` — per-tag `…ApiRoutes`/`…ApiDelegate` http4s scaffolding. We implement the `*ApiDelegate[IO]` traits (see `routes/*ApiDelegateImpl.scala`) and mount `…ApiRoutes(delegate).routes`.

Endpoints are mounted under `/api/v1` (see `Main.scala` router).

## Backend architecture

The `db/` layer (`DbTransactorComponent`, `DbMigrationComponent`) still uses the
**cake pattern** (a `*Component` trait with an inner trait, mixed into `Main`).
Newer feature code uses plain **constructor injection** (preferred for new work —
see [backend/CLAUDE.md](backend/CLAUDE.md)): `Main` loads config, acquires the
`Transactor[IO]` once, and wires repositories → services → route delegates explicitly.

Request flow, layer by layer:

- **Routes** (`routes/*ApiDelegateImpl.scala`) — implement the generated
  `shopping.backend.apis.*ApiDelegate[IO]` traits; map typed service results to the
  contract's HTTP status codes. Mounted in `Main` via `…ApiRoutes(delegate).routes`.
- **Service** (`service/*.scala`) — business logic as `final class`es taking repo
  traits; return `IO[Either[ConcreteError, A]]` (a real error ADT, never `Either[_, A]`).
- **Repository** (`repository/*.scala`) — a `trait` + `Live` impl taking a
  `Transactor[IO]`; Doobie SQL (`sql"..."`/`fr"..."`), composed as `ConnectionIO`
  and `.transact`ed once. Tests inject in-memory fakes of the trait.

Other backend pieces: config via PureConfig (Scala 3 `derives ConfigReader`) from
`src/main/resources/application.conf` (`AppConfig`). The Postgres schema and seed
data are managed by **Flyway** migrations in
`backend/src/main/resources/db/migration/` (`V1__init_schema.sql`, `V2__seed_dev_data.sql`),
applied on app startup; the Docker container only creates an empty DB `backend_db`.
Passwords are hashed with bcrypt (`PasswordHasher`); seed login is
`john.doe@example.com` / `password`.

### Backend commands (run in `backend/`)

```shell
docker-compose -f ./docker/postgres.yml up   # start Postgres first
sbt compile                                   # build (also regenerates OpenAPI code)
sbt test                                       # run tests (MUnit framework)
sbt run                                         # run server on localhost:8080
sbt scalafixEnable scalafixAll                 # lint
sbt scalafmt                                    # format (scalafmt 3.8.3, maxColumn 80, 4-space indent)
```

Run a single test with MUnit: `sbt "testOnly shopping.backend.UserDbSpec"`.

Inspect the DB: `docker exec -it docker-db-1 bash` then `psql -U postgres -d backend_db`. Reset volumes: `docker-compose -f ./docker/postgres.yml down -v`.

## Frontend architecture

Next.js App Router under `frontend/src/app/` — each route is a directory with a `page.tsx`. Shared building blocks in `src/components/` (`layout/`, `ui/`, `ui/icon/`). Import alias `@/*` → `src/*`.

- API calls use `axios`; generated request/response types come from `src/api/schema.d.ts`.
- Styling: Tailwind + DaisyUI. Brand primary color is `#FFA451` (`tailwind.config.ts`). Animations via framer-motion.

### Frontend commands (run in `frontend/`)

```shell
npm run dev            # dev server at http://localhost:3000
npm run build          # production build
npm run lint           # next lint (ESLint)
npm run prettier       # format with Prettier
npm run gen-api-types  # regenerate src/api/schema.d.ts from the OpenAPI spec
```

## Conventions

- Changing an API: edit `openapi/backend-server.yaml` first, then regenerate types on both sides.
- Frontend formatting/lint is Prettier + ESLint; backend is scalafmt + scalafix. Run the relevant formatter before committing.
