# Chicanery OpenAPI Spec

API schema for the Chicanery backend server.
You can use OpenAPI tools such as Postman to open the schema and documentation.

## Code generation by schema

You can auto-generate TS/Scala code based on the schema(!),
which makes the schema a shared contract between backend and frontend.

### Frontend

The code generation is automatically handled by [openapi-typescript](https://openapi-ts.dev/)
The configuration is in [package.json](../frontend/package.json).
See [frontend readme](../frontend/README.md).

### Backend

The code generation is handled by the [openapi-generator](https://openapi-generator.tech/)
`scala-http4s-server` generator, wired into `sbt compile` via `sourceGenerators`.
The configuration is in [build.sbt](../backend/build.sbt) (`openApiInputSpec`,
`openApiGeneratorName`, `openApiOutputDir`, `openApiPackageName`). It produces
`shopping.backend.models.*` (types) and `shopping.backend.apis.*` (route scaffolding).
See [backend readme](../backend/README.md).