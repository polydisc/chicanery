package shopping.backend.routes

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import shopping.backend.models.{ProductInput, ShipOrder}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shopping.backend.apis.AdminApiRoutes
import shopping.backend.db.models.{Order, Product, User}
import shopping.backend.models.RegisterRequest
import shopping.backend.repository.OrderRepository.{DeliverOutcome, ShipOutcome}
import shopping.backend.repository.ProductRepository.DeleteResult
import shopping.backend.repository.{
    OrderRepository,
    ProductRepository,
    UserRepository
}
import shopping.backend.service.{
    AdminOrderService,
    AuthTokenService,
    PasswordHasher,
    ProductService,
    UserService
}

class AdminRoutesSpec extends CatsEffectSuite:

    private given Logger[IO] = Slf4jLogger.getLogger[IO]
    private val auth = AuthTokenService.Live("test-secret", 3600)

    // role: 1 -> ADMIN, 2 -> USER
    private val userRepo =
        new UserRepository:
            def findByEmail(e: String): IO[Option[User]] = IO.pure(None)
            def insert(
                r: RegisterRequest,
                p: String,
                t: String
            ): IO[Option[Long]] = IO.pure(None)
            def findRole(userId: Long): IO[Option[String]] = IO.pure(
              if userId == 1L then
                  Some("ADMIN")
              else
                  Some("USER")
            )
            def setState(userId: Long, state: String): IO[Boolean] = IO
                .pure(true)

    private val productRepo =
        new ProductRepository:
            def findById(id: Long): IO[Option[Product]] = IO.pure(None)
            def search(
                q: Option[String],
                c: Option[String],
                p: Int,
                s: Int
            ): IO[List[Product]] = IO.pure(Nil)
            def listCategories(): IO[List[String]] = IO.pure(Nil)
            def create(
                n: String,
                c: Option[String],
                pr: Int,
                i: Option[String]
            ): IO[Product] = IO.pure(Product(1L, n, c, pr, i))
            def update(
                id: Long,
                n: String,
                c: Option[String],
                pr: Int,
                i: Option[String]
            ): IO[Option[Product]] = IO.pure(None)
            def delete(id: Long): IO[DeleteResult] = IO
                .pure(DeleteResult.NotFound)

    import shopping.backend.db.models.OrderItem
    private val shippedOrder = Order(
      id = 10L,
      items = List(OrderItem(1L, "Banana", 200, 2)),
      shippingAddress = "Tokyo",
      status = "SHIPPED",
      orderDate = None,
      trackingNumber = Some("TRACK123"),
      carrier = Some("Yamato")
    )

    // order 10 ships/delivers; order 99 is unknown; anything else is in the
    // wrong state for the requested transition.
    private val orderRepo =
        new OrderRepository:
            def createFromCart(
                c: Long,
                u: Long,
                a: String
            ): IO[OrderRepository.CreateOutcome] = IO
                .pure(OrderRepository.CreateOutcome.CartNotFound)
            def findById(o: Long, u: Long): IO[Option[Order]] = IO.pure(None)
            def list(u: Long): IO[List[Order]] = IO.pure(Nil)
            def pay(
                o: Long,
                u: Long,
                d: String
            ): IO[OrderRepository.PaymentOutcome] = IO
                .pure(OrderRepository.PaymentOutcome.OrderNotFound)
            def cancel(o: Long, u: Long): IO[OrderRepository.CancelOutcome] = IO
                .pure(OrderRepository.CancelOutcome.OrderNotFound)
            def listAll: IO[List[Order]] = IO.pure(List(shippedOrder))
            def ship(
                o: Long,
                t: String,
                c: String,
                e: Option[java.time.LocalDate]
            ): IO[ShipOutcome] = IO.pure(
              o match
                  case 10L =>
                      ShipOutcome.Shipped(shippedOrder)
                  case 99L =>
                      ShipOutcome.OrderNotFound
                  case _ =>
                      ShipOutcome.NotShippable
            )
            def deliver(o: Long): IO[DeliverOutcome] = IO.pure(
              o match
                  case 10L =>
                      DeliverOutcome
                          .Delivered(shippedOrder.copy(status = "DELIVERED"))
                  case 99L =>
                      DeliverOutcome.OrderNotFound
                  case _ =>
                      DeliverOutcome.NotDeliverable
            )

    private val app =
        AdminApiRoutes(
          AdminApiDelegateImpl(
            ProductService(productRepo),
            UserService(userRepo, PasswordHasher.BCryptHasher()),
            AdminOrderService(orderRepo),
            auth
          )
        ).routes.orNotFound

    private def withToken(req: Request[IO], userId: Long): IO[Request[IO]] =
        auth.issue(userId)
            .map(t =>
                req.putHeaders(
                  Authorization(Credentials.Token(AuthScheme.Bearer, t))
                )
            )

    test("admin route without a token returns 401"):
        app.run(Request[IO](Method.POST, uri"/admin/users/2/block"))
            .map(r => assertEquals(r.status, Status.Unauthorized))

    test("admin route as a non-admin user returns 403"):
        withToken(Request[IO](Method.POST, uri"/admin/users/2/block"), 2L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Forbidden))

    test("admin route as an admin user succeeds (204)"):
        withToken(Request[IO](Method.POST, uri"/admin/users/2/block"), 1L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.NoContent))

    test("blocking an admin is refused (409)"):
        // user 1 is an ADMIN in the fake; an admin can't block another admin.
        withToken(Request[IO](Method.POST, uri"/admin/users/1/block"), 1L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Conflict))

    test("GET /admin/me is 200 for an admin, 403 otherwise"):
        withToken(Request[IO](Method.GET, uri"/admin/me"), 1L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Ok)) *>
            withToken(Request[IO](Method.GET, uri"/admin/me"), 2L)
                .flatMap(app.run)
                .map(r => assertEquals(r.status, Status.Forbidden))

    test("POST /admin/products with a blank name returns 422"):
        withToken(Request[IO](Method.POST, uri"/admin/products"), 1L)
            .map(_.withEntity(ProductInput("   ", None, 100, None)))
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.UnprocessableEntity))

    test("POST /admin/products with a negative price returns 422"):
        withToken(Request[IO](Method.POST, uri"/admin/products"), 1L)
            .map(_.withEntity(ProductInput("Mango", None, -1, None)))
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.UnprocessableEntity))

    test("GET /admin/orders requires admin (401 without token, 403 non-admin)"):
        app.run(Request[IO](Method.GET, uri"/admin/orders"))
            .map(r => assertEquals(r.status, Status.Unauthorized)) *>
            withToken(Request[IO](Method.GET, uri"/admin/orders"), 2L)
                .flatMap(app.run)
                .map(r => assertEquals(r.status, Status.Forbidden)) *>
            withToken(Request[IO](Method.GET, uri"/admin/orders"), 1L)
                .flatMap(app.run)
                .map(r => assertEquals(r.status, Status.Ok))

    private def shipBody = ShipOrder("TRACK123", "Yamato", None)

    test("POST /admin/orders/10/ship as admin ships (200)"):
        withToken(Request[IO](Method.POST, uri"/admin/orders/10/ship"), 1L)
            .map(_.withEntity(shipBody))
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Ok))

    test("ship a non-PROCESSING order is 409, unknown is 404"):
        withToken(Request[IO](Method.POST, uri"/admin/orders/11/ship"), 1L)
            .map(_.withEntity(shipBody))
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Conflict)) *>
            withToken(Request[IO](Method.POST, uri"/admin/orders/99/ship"), 1L)
                .map(_.withEntity(shipBody))
                .flatMap(app.run)
                .map(r => assertEquals(r.status, Status.NotFound))

    test("ship with a blank tracking number is rejected (422)"):
        withToken(Request[IO](Method.POST, uri"/admin/orders/10/ship"), 1L)
            .map(_.withEntity(ShipOrder("   ", "Yamato", None)))
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.UnprocessableEntity))

    test("ship without a token is 401"):
        Request[IO](Method.POST, uri"/admin/orders/10/ship")
            .withEntity(shipBody)
            .pure[IO]
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Unauthorized))

    test("POST /admin/orders/10/deliver as admin delivers (200)"):
        withToken(Request[IO](Method.POST, uri"/admin/orders/10/deliver"), 1L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Ok))

    test("deliver a non-SHIPPED order is 409, unknown is 404"):
        withToken(Request[IO](Method.POST, uri"/admin/orders/11/deliver"), 1L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Conflict)) *>
            withToken(
              Request[IO](Method.POST, uri"/admin/orders/99/deliver"),
              1L
            ).flatMap(app.run).map(r => assertEquals(r.status, Status.NotFound))
