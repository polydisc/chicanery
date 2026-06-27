package shopping.backend.db

import cats.effect.{ExitCode, IO}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

trait DbMigrationComponent {
    val migration: Migration

    trait Migration {
        def migrate(
            jdbcUrl: String,
            user: String,
            password: String
        ): IO[MigrateResult] = IO.blocking {
            Flyway
                .configure()
                .baselineOnMigrate(true)
                .dataSource(jdbcUrl, user, password)
                .load()
                .migrate()
        }
    }
}
