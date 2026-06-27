# Project status

_Last updated: 2026-06-27_

Snapshot of the local online shopping website. Both halves are now built out: the
**backend** is a complete, authenticated shopping + admin API with CI, and the
**frontend** is fully wired to it (auth → browse/search → product detail + reviews
→ cart → order → payment → tracking → cancel, in-app notifications on every
order state change, plus an admin panel) with its own test harness.

## At a glance

- **Backend**: compiles and runs; **114 logic tests** + **42 DB query-analysis
  checks** pass. Built contract-first against `openapi/backend-server.yaml`.
- **Frontend**: Next.js App Router, fully wired to the API via a typed axios
  layer; **20 Vitest/RTL/MSW tests**.
- **CI** (`.github/workflows/ci.yml`, on push to `main` + PRs):
  - `Build & test` (blocking): `scalafmtCheckAll` + `sbt test`.
  - `DB query analysis` (blocking): migrates a real Postgres and type-checks
    every repository query (doobie analysis).
  - `Frontend test & build` (blocking): `npm test` + `lint` + `build`.

## Backend

Scala 3.5 · Cats Effect 3 (`IO`) · http4s 0.23 (Ember) · Doobie 1.0-RC5 · circe ·
Flyway · PureConfig · MUnit · jwt-scala / bcrypt. FP conventions: see
[`backend/CLAUDE.md`](backend/CLAUDE.md).

### Architecture

- **Contract-first codegen**: `openapi/backend-server.yaml` → openapi-generator
  `scala-http4s-server` (wired into `sbt compile` via `sourceGenerators`) →
  `shopping.backend.models.*` (request/response types) and
  `shopping.backend.apis.*` (`*ApiRoutes` / `*ApiDelegate` scaffolding). We
  implement the `*ApiDelegate[IO]` traits.
- **Layers**: Routes (`routes/*ApiDelegateImpl.scala`) → Service
  (`service/*.scala`, typed-error `Either`s / outcome ADTs) → Repository
  (`repository/*.scala`, `trait` + `Live`, queries as named `Query0`/`Update0`
  values). Dependencies wired by constructor injection in `Main`, which acquires
  the Doobie `Transactor` once as a `Resource`.
- **Auth & RBAC**: stateless HS256 JWT (`AuthTokenService`). `login`/`register`
  mint a token; protected endpoints are gated by `AuthSupport.withUser` (401
  without a valid Bearer token) and scoped to the authenticated user. Admin
  endpoints add `AdminSupport.withAdmin` (403 unless the user's DB `role` is
  `ADMIN`). `login` rejects `BLOCKED` accounts.
- **CORS**: http4s CORS middleware, origins from `cors.allowed-origins`
  (defaults to the Next.js dev origin), credentials off (bearer-token auth).

### Endpoints (mounted under `/api/v1`)

| Method & path | Auth | Description |
|---|---|---|
| `POST /register` | public | Create a user (bcrypt), returns a JWT |
| `POST /login` | public | Returns a JWT (rejects blocked accounts) |
| `GET /login` | Bearer | Refresh: re-issue a JWT |
| `GET /products/{id}` | public | Product by id |
| `GET /products/search?query=&category=&page=` | public | Paged search; free-text matches name **or** category |
| `GET /products/categories` | public | Distinct product categories |
| `GET /reviews/{productId}` | public | Reviews for a product |
| `POST /reviews/{productId}` | Bearer | Add a review (rating 1–5); author from the token |
| `POST /cart` | Bearer | Create an empty cart (owned by caller) |
| `GET /cart/{cartId}` | Bearer (owner) | Cart with items + total |
| `POST /cart/{cartId}/items` | Bearer (owner) | Add/increment an item |
| `PUT /cart/{cartId}/items/{productId}` | Bearer (owner) | Set quantity (0 removes) |
| `POST /orders` | Bearer | Create an order from a cart (atomic, empties cart) |
| `GET /orders` | Bearer | List the caller's orders |
| `GET /orders/{orderId}` | Bearer (owner) | Order detail |
| `POST /orders/{orderId}/payment` | Bearer (owner) | Pay (NEW → PROCESSING); method ∈ card/bank_transfer/cod, validated (422 otherwise) |
| `POST /orders/{orderId}/cancel` | Bearer (owner) | Cancel while not shipped (NEW/PROCESSING/HOLD) → 409 otherwise |
| `GET /admin/me` | Admin | 200 if caller is an admin (gates admin UI) |
| `POST /admin/products` | Admin | Create a product |
| `PUT /admin/products/{id}` | Admin | Update a product |
| `DELETE /admin/products/{id}` | Admin | Delete (409 if referenced by orders/reviews) |
| `POST /admin/users/{id}/block` | Admin | Block a user (admins can't be blocked → 409) |
| `POST /admin/users/{id}/unblock` | Admin | Unblock a user |
| `GET /notifications` | Bearer | The caller's notifications (newest first) |
| `POST /notifications/{id}/read` | Bearer (owner) | Mark one notification read |
| `POST /notifications/read-all` | Bearer | Mark all the caller's notifications read |
| `GET /admin/orders` | Admin | List every order (order management) |
| `POST /admin/orders/{id}/ship` | Admin | Mark shipped (PROCESSING → SHIPPED) with tracking#/carrier/ETA → 409 otherwise |
| `POST /admin/orders/{id}/deliver` | Admin | Mark delivered (SHIPPED → DELIVERED) → 409 otherwise |

Authorization: cart/order resources are owner-scoped; a non-owner is
indistinguishable from "not found" (404). `userId`/`role` are taken only from
the verified token / the DB (never from the request body/query).

### Database

Schema + seed are managed by **Flyway** migrations in
`backend/src/main/resources/db/migration/` (`V1`–`V13`), applied on app startup;
the Docker container only creates an empty `backend_db`.

- `V1` schema · `V2` dev seed (`john.doe@example.com` / `password`) ·
  `V3` cart-item unique index (atomic upsert) · `V4`/`V6` owner + NOT NULL
  columns · `V5` `payment.amount` · `V6` id/FK columns aligned to `bigint` ·
  `V7` richer test seed (product images, combo products, reviews) ·
  `V8` `UserRole` enum + `role` column + dev admin (`admin@example.com` /
  `password`) · `V9` order shipment tracking columns (`tracking_number`,
  `carrier`, `estimated_delivery_date`) · `V10` `notifications` table +
  `NotificationType` enum · `V11` `payment.status` (PAID / REFUNDED) · `V12`
  `payment.status` CHECK constraint · `V13` `products.price_jpy >= 0` CHECK
  constraint.

### Testing & CI

- **Logic tests** (`sbt test`): munit-cats-effect, in-memory repository fakes,
  no DB required — services (incl. ownership/error paths), route auth/admin
  gates (401/403), JWT round-trip, card-number masking, payment/cancel/block
  outcomes.
- **Query analysis** (`QueryCheckSpec`, opt-in via `RUN_DB_QUERY_CHECKS=1` + a
  reachable Postgres): doobie analyzes every repository query against the real
  schema (columns / arity / nullability / Scala↔SQL types). Runs in CI; skipped
  locally so `sbt test` stays green offline.

## Frontend

Next.js (App Router) + TypeScript + Tailwind/DaisyUI + framer-motion. A typed
axios layer (`src/api/*`) over the generated `schema.d.ts` wraps every endpoint;
a request interceptor attaches the bearer token and a 401 redirects to `/login`.

- **Shell**: every authenticated page shares a `Header` (the _Chicanery_
  brand wordmark in a script font / terracotta, the notification bell with its
  unread badge, and Orders / Basket / Admin / Log out — current page marked with
  an orange background) and a centered `Footer`.
- **Flow**: `/` redirects into the app (signed-in → `/home`, else `/welcome`);
  login/register; home product search + category chips (deep-linkable via
  `?category=`); product detail with reviews + a review form; cart with quantity
  edits; checkout/payment; order confirmation with a cancel button; order
  history with shipment tracking (carrier / tracking# / ETA once shipped); an
  in-app notification bell + `/notifications` list (unread badge, mark
  read/all-read) fed by order state changes; and an admin page (`/admin`, shown
  to admins) for product CRUD, user block/unblock, and order management (ship
  with tracking/ETA, mark delivered).
- **Tests** (`npm test`): Vitest (jsdom) + React Testing Library + **MSW**
  (mocks the API at the network layer) — util, API clients (GET/POST), and a
  component test.

## Requirements (R1–R10, see issue #3)

| Req | Status |
|---|---|
| R1 authenticated users + guest browse | ✅ |
| R2 buy / search by name **or** category | ✅ (search + browse) |
| R3 reviews & ratings (read + submit) | ✅ |
| R4 add/remove/modify cart + checkout | ✅ |
| R5 shipping address at order | ✅ |
| R6 payment (card / transfer / COD) | ✅ (3 methods, server-validated, refund recorded on cancel; no real gateway) |
| R7 cancel an order before it ships | ✅ |
| R8 notifications on status change | ✅ (in-app, fired on every order state change) |
| R9 shipment tracking + ETA | ✅ (admin ship/deliver + tracking#, carrier, ETA) |
| R10 admin: manage products + block users | ✅ (+ RBAC) |

## Known limitations / follow-ups

- **Payment is a stub** — there is no real gateway. The method (card /
  bank_transfer / cod) is server-validated and recorded, and cancelling a paid
  (PROCESSING) order records a refund (the payment row flips to REFUNDED), but
  no actual money moves.
- **Blocking is enforced at login** — a stateless JWT stays valid until it
  expires (60 min), so an already-issued token keeps working until then.
- **Seeded admin is dev-only** (`admin@example.com` / `password`) — change or
  remove it for any real deployment.
- **Notifications (R8) are in-app only** — created in the order transaction on
  every state change and shown in the SPA (bell + `/notifications`); there is no
  email/SMS/push delivery. Shipment tracking (R9) is admin-driven and
  simulated — the tracking number/carrier/ETA are entered by an admin, not
  fetched from a real carrier API.
- **No guest carts** — cart/orders require authentication.
- **Not deployed** — runs locally only; no live demo URL yet (issue #37).
- `users.access_token` column is vestigial (JWT replaced it).

## Running it

```shell
# 1. Postgres (from backend/) — empty backend_db; Flyway builds the schema
docker compose -f ./docker/postgres.yml up -d
# 2. Backend (from backend/) — applies V1–V13 (migrate + seed), serves :8080
sbt run
# 3. Frontend (from frontend/) — http://localhost:3000
npm run dev

# Tests
sbt test                # backend logic tests (no DB needed)
cd frontend && npm test # frontend Vitest/RTL/MSW

# Regenerate shared types after editing the OpenAPI spec
sbt compile                                 # backend: shopping.backend.{models,apis}.*
cd ../frontend && npm run gen-api-types     # frontend: src/api/schema.d.ts
```

Logins: shopper `john.doe@example.com` / `password`; admin `admin@example.com` /
`password` (an "Admin" link appears on `/home` for admins).

See [`CLAUDE.md`](CLAUDE.md) (repo overview) and
[`backend/CLAUDE.md`](backend/CLAUDE.md) (backend FP conventions).
