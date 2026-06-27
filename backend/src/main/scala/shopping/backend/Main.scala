package shopping.backend

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderException
import shopping.backend.apis.{
    AdminApiRoutes,
    AuthApiRoutes,
    CartApiRoutes,
    NotificationApiRoutes,
    OrderApiRoutes,
    ProductApiRoutes,
    ReviewApiRoutes
}
import shopping.backend.config.AppConfig
import shopping.backend.db.{DbMigrationComponent, DbTransactorComponent}
import shopping.backend.repository.{
    CartRepository,
    NotificationRepository,
    OrderRepository,
    ProductRepository,
    ReviewRepository,
    UserRepository
}
import shopping.backend.routes.{
    AdminApiDelegateImpl,
    AuthApiDelegateImpl,
    CartApiDelegateImpl,
    NotificationApiDelegateImpl,
    OrderApiDelegateImpl,
    ProductApiDelegateImpl,
    ReviewApiDelegateImpl
}
import shopping.backend.service.{
    AdminOrderService,
    AuthTokenService,
    CartService,
    NotificationService,
    OrderService,
    PasswordHasher,
    PaymentService,
    ProductService,
    ReviewService,
    UserService
}

object Main extends IOApp with DbMigrationComponent with DbTransactorComponent:

    given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    override val migration: Migration = new Migration {}
    override val transactor: DbTransactor = new DbTransactor {}

    private val ping: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case GET -> Root / "ping" =>
            Ok("pong!")
    }

    private val loadConfig: IO[AppConfig] = IO.fromEither(
      ConfigSource
          .default
          .load[AppConfig]
          .left
          .map(ConfigReaderException[AppConfig](_))
    )

    private val DevDefaultJwtSecret =
        "dev-only-change-me-please-use-a-long-random-secret"

    // Fail fast on a too-short signing key; loudly warn if the committed dev
    // default is still in use (anyone could forge tokens with it).
    private def checkJwtSecret(secret: String): IO[Unit] =
        IO.raiseWhen(secret.length < 16)(
          IllegalStateException("jwt.secret must be at least 16 characters")
        ) *>
            IO.whenA(secret == DevDefaultJwtSecret)(
              logger.warn(
                "jwt.secret is the built-in development default — set JWT_SECRET to " +
                    "a strong random value before deploying."
              )
            )

    def run(args: List[String]): IO[ExitCode] =
        for
            config <- loadConfig
            db = config.database
            _ <- checkJwtSecret(config.jwt.secret)
            _ <- migration.migrate(db.url, db.user, db.password)
            _ <- logger.info(
              s"Starting service on ${config.api.host}:${config.api.port}"
            )
            exitCode <- transactor
                .init(db)
                .use { xa =>
                    val productRepository = ProductRepository.Live(xa)
                    val cartRepository = CartRepository.Live(xa)
                    val orderRepository = OrderRepository.Live(xa)

                    val authTokenService = AuthTokenService
                        .Live(config.jwt.secret, config.jwt.expiryMinutes * 60)
                    val userService = UserService(
                      UserRepository.Live(xa),
                      PasswordHasher.BCryptHasher()
                    )
                    val productService = ProductService(productRepository)
                    val reviewService = ReviewService(ReviewRepository.Live(xa))
                    val cartService = CartService(
                      cartRepository,
                      productRepository
                    )
                    val orderService = OrderService(orderRepository)
                    val paymentService = PaymentService()
                    val adminOrderService = AdminOrderService(orderRepository)
                    val notificationService = NotificationService(
                      NotificationRepository.Live(xa)
                    )

                    val authRoutes =
                        AuthApiRoutes(
                          AuthApiDelegateImpl(userService, authTokenService)
                        ).routes
                    val productRoutes =
                        ProductApiRoutes(ProductApiDelegateImpl(productService))
                            .routes
                    val reviewRoutes =
                        ReviewApiRoutes(
                          ReviewApiDelegateImpl(reviewService, authTokenService)
                        ).routes
                    val cartRoutes =
                        CartApiRoutes(
                          CartApiDelegateImpl(cartService, authTokenService)
                        ).routes
                    val orderRoutes =
                        OrderApiRoutes(
                          OrderApiDelegateImpl(
                            orderService,
                            paymentService,
                            authTokenService
                          )
                        ).routes
                    val notificationRoutes =
                        NotificationApiRoutes(
                          NotificationApiDelegateImpl(
                            notificationService,
                            authTokenService
                          )
                        ).routes
                    val adminRoutes =
                        AdminApiRoutes(
                          AdminApiDelegateImpl(
                            productService,
                            userService,
                            adminOrderService,
                            authTokenService
                          )
                        ).routes

                    val router =
                        Router(
                          "/api/v1" ->
                              (authRoutes <+> productRoutes <+> reviewRoutes <+>
                                  cartRoutes <+> orderRoutes <+>
                                  notificationRoutes <+> adminRoutes),
                          "/" -> ping
                        ).orNotFound

                    // Browsers call this API cross-origin (the SPA on :3000),
                    // so echo CORS headers for the configured origins. Auth is
                    // via the Authorization header (no cookies), so credentials
                    // stay off.
                    val allowedOrigins = config.cors.allowedOrigins.toSet
                    val corsPolicy =
                        if allowedOrigins.contains("*") then
                            CORS.policy.withAllowOriginAll
                        else
                            CORS.policy
                                .withAllowOriginHost(host =>
                                    allowedOrigins.contains(host.renderString)
                                )
                    val httpApp = corsPolicy
                        .withAllowMethodsAll
                        .withAllowHeadersAll
                        .apply(router)

                    EmberServerBuilder
                        .default[IO]
                        .withHost(
                          Hostname
                              .fromString(config.api.host)
                              .getOrElse(host"0.0.0.0")
                        )
                        .withPort(
                          Port.fromInt(config.api.port).getOrElse(port"8080")
                        )
                        .withHttpApp(httpApp)
                        .build
                        .use(_ => IO.never)
                        .as(ExitCode.Success)
                }
        yield exitCode
