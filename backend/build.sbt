name := "shopping-backend"

version := "1.0"

val scala3Version = "3.5.0"
val scala213 = "2.13.6"
scalaVersion := scala3Version

// Only necessary for SNAPSHOT releases
resolvers ++= Resolver.sonatypeOssRepos("snapshots")

// http4s
val http4sVersion = "0.23.27"
val log4catsVersion = "2.7.0"
// JWT Auth
val jwtHttp4sVersion = "1.2.3"
val jwtScalaVersion = "10.0.1"
// Doobie
val doobieVersion = "1.0.0-RC5"
val circeVersion = "0.15.0-M1"
// Flyway
val flywayVersion = "10.16.0"
// PureConfig
val pureConfigVersion = "0.17.7"

libraryDependencies ++= Seq(
    // http4s
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-ember-client" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
    // SLF4J binding — without this the logger is a no-op and all logs are
    // dropped (the SLF4J StaticLoggerBinder warning). logback-classic reads
    // src/main/resources/logback.xml.
    "ch.qos.logback" % "logback-classic" % "1.5.6",
    // Http4s JWT Auth
    "dev.profunktor" %% "http4s-jwt-auth" % jwtHttp4sVersion,
    "com.github.jwt-scala" %% "jwt-core" % jwtScalaVersion,
    // Password hashing
    "org.mindrot" % "jbcrypt" % "0.4",
    // Doobie
    "org.tpolecat" %% "doobie-core" % doobieVersion,
    "org.tpolecat" %% "doobie-postgres" % doobieVersion,
    "org.postgresql" % "postgresql"  % "42.7.3",
    "org.tpolecat" %% "doobie-specs2" % doobieVersion,
    "org.tpolecat" %% "doobie-hikari" % doobieVersion,
    // Circe
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    // Refined (required by the scala-http4s-server generated code)
    "io.circe" %% "circe-refined" % circeVersion,
    "eu.timepit" %% "refined" % "0.11.3",
    // FlywayDB
    "org.flywaydb" % "flyway-core" % flywayVersion,
    "org.flywaydb" % "flyway-database-postgresql" % flywayVersion % "runtime",
    // PureConfig
    "com.github.pureconfig" %% "pureconfig-generic-scala3" % pureConfigVersion,
    // Testing
    "org.scalameta" %% "munit" % "1.0.1" % Test,
    "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test,
    "org.tpolecat" %% "doobie-munit" % doobieVersion % Test,
)

// General Settings
lazy val commonSettings = Seq(
    testFrameworks += new TestFramework("munit.Framework"),
    // The scala-http4s-server generated code uses kind-projector `*` syntax.
    scalacOptions += "-Xkind-projector",
    semanticdbEnabled := true, // enable SemanticDB
    semanticdbVersion := scalafixSemanticdb.revision, // only required for Scala 2.x
    Compile / doc / scalacOptions ++= Seq(
        "-groups",
        "-sourcepath", (LocalRootProject / baseDirectory).value.getAbsolutePath,
        "-Wunused:all", "-Wunused:imports",
    )
)


// Projects
lazy val `shopping-backend` = project.in(file("."))
  .settings(commonSettings)
  .settings(
      name := "shopping-backend",
      // OpenAPI code generation: openapi/backend-server.yaml is the source of
      // truth. The scala-http4s-server generator emits shopping.backend.definitions.*
      // (request/response types) plus per-tag Routes scaffolding. Generated sources
      // are wired into compilation via sourceGenerators below, so a plain
      // `sbt compile` regenerates them. Never hand-edit generated code.
      openApiInputSpec := (
          baseDirectory.value.getParentFile / "openapi" / "backend-server.yaml"
      ).getPath,
      openApiGeneratorName := "scala-http4s-server",
      openApiOutputDir := (target.value / "openapi-generated").getPath,
      openApiPackageName := "shopping.backend",
      Compile / sourceGenerators += Def.task {
          openApiGenerate.value
          (file(openApiOutputDir.value) ** "*.scala").get()
      }.taskValue
  )
