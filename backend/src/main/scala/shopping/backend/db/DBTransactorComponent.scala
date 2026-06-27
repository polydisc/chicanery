package shopping.backend.db

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger
import shopping.backend.config.Database

trait DbTransactorComponent {
    val transactor: DbTransactor

    trait DbTransactor {
        def init(
            database: Database
        )(implicit logger: Logger[IO]): Resource[IO, Transactor[IO]] =
            for {
                hikariConfig <- Resource.pure {
                    val config = new HikariConfig()
                    config.setDriverClassName("org.postgresql.Driver")
                    config.setJdbcUrl(database.url)
                    config.setUsername(database.user)
                    config.setPassword(database.password)
                    config.setMaximumPoolSize(database.maxPoolSize)
                    config
                }
                xa <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
            } yield xa
    }
}
