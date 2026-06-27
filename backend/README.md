# Chicanery Backend

## Tech stack

- Cats Effect (`IO`)
- Http4s (Ember)
- Doobie
- circe
- Flyway
- [PureConfig](https://pureconfig.github.io/)
- jwt-scala / jbcrypt
- [openapi-generator](https://github.com/OpenAPITools/openapi-generator)

See [CLAUDE.md](CLAUDE.md) for the functional-Scala conventions used here.

## Development

### Run locally

#### PostgreSQL

```shell
$ docker-compose -f ./docker/postgres.yml up
```

#### Backend service

Build:

```shell
$ sbt compile
```

Run tests:

```shell
$ sbt test
```

Run server

```shell
$ sbt run
```

Then REST endpoints should be available on `localhost:8080`.

### API code generation

The OpenAPI spec (`../openapi/backend-server.yaml`) is the source of truth and
code generation is sbt-managed: `sbt compile` runs `openApiGenerate` (wired via
`sourceGenerators` in `build.sbt`) into `target/openapi-generated/`, producing
`shopping.backend.models.*` (request/response types) and `shopping.backend.apis.*`
(per-tag route scaffolding). Never hand-edit the generated code — change the spec
and recompile.

### Formatting & linting

```shell
$ sbt scalafixEnable scalafixAll
$ sbt scalafmt
```

### Debugging PostgresDB on Docker

PSQL queries:
```shell
$ docker ps
xxxx   postgres   .....   docker-db-1
$ docker exec -it docker-db-1 bash
# (inside docker)
$ psql -U postgres -d backend_db
# (inside psql) you can run psql queries.
backend_db=# \dt etc
```

Initialization/cleanups:
```shell
# Remove containers and existing volumes (persistent data)
# See https://docs.docker.com/reference/cli/docker/compose/down/ for other options.
$ docker-compose -f ./docker/postgres.yml down -v
# Start with recreation (the existing volumes will be still reused)
$ docker-compose -f ./docker/postgres.yml up --force-recreate
```

## Deployment

Not deployed — the project runs locally only.
