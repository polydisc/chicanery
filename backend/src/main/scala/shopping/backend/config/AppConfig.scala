package shopping.backend.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

// Scala 3 pureconfig: derive readers via `derives ConfigReader`
// (pureconfig-generic-scala3). Config keys map kebab-case -> camelCase, so
// `max-pool-size` in application.conf binds to `maxPoolSize`.
final case class Api(port: Int, host: String) derives ConfigReader

final case class Database(
    url: String,
    user: String,
    password: String,
    maxPoolSize: Int,
    schema: String
) derives ConfigReader

final case class Jwt(secret: String, expiryMinutes: Long) derives ConfigReader

// Browser clients (the Next.js frontend) call the API cross-origin, so the
// server must echo CORS headers. `allowedOrigins` lists the exact origins
// permitted (e.g. "http://localhost:3000"); a single "*" allows any origin.
final case class Cors(allowedOrigins: List[String]) derives ConfigReader

final case class AppConfig(api: Api, database: Database, jwt: Jwt, cors: Cors)
    derives ConfigReader
