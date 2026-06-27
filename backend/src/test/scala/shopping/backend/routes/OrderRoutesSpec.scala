package shopping.backend.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shopping.backend.apis.OrderApiRoutes
import shopping.backend.db.models.{Order, OrderItem}
import shopping.backend.models.{CreateOrder, PaymentRequest}
import shopping.backend.repository.OrderRepository
import shopping.backend.repository.OrderRepository.{
    CancelOutcome,
    CreateOutcome,
    PaymentOutcome
}
import shopping.backend.service.{AuthTokenService, OrderService, PaymentService}

class OrderRoutesSpec extends CatsEffectSuite:

    private given Logger[IO] = Slf4jLogger.getLogger[IO]
    private val auth = AuthTokenService.Live("test-secret", 3600)

    private val paidOrder = Order(
      id = 10L,
      items = List(OrderItem(1L, "Banana", 200, 2)),
      shippingAddress = "Tokyo",
      status = "PROCESSING",
      orderDate = None
    )

    // cart 1 -> Created, cart 2 -> EmptyCart, anything else -> CartNotFound.
    private val orderService = OrderService(
      new OrderRepository:
          def createFromCart(c: Long, u: Long, a: String): IO[CreateOutcome] =
              IO.pure(
                c match
                    case 1L =>
                        CreateOutcome.Created(paidOrder.copy(status = "NEW"))
                    case 2L =>
                        CreateOutcome.EmptyCart
                    case _ =>
                        CreateOutcome.CartNotFound
              )
          def findById(o: Long, u: Long): IO[Option[Order]] = IO
              .pure(Option.when(o == 10L)(paidOrder))
          def list(u: Long): IO[List[Order]] = IO.pure(List(paidOrder))
          def pay(o: Long, u: Long, d: String): IO[PaymentOutcome] = IO
              .pure(PaymentOutcome.Paid(paidOrder))
          def cancel(o: Long, u: Long): IO[CancelOutcome] = IO.pure(
            CancelOutcome.Cancelled(paidOrder.copy(status = "CANCELLED"))
          )
          def listAll: IO[List[Order]] = IO.pure(Nil)
          def ship(
              o: Long,
              t: String,
              c: String,
              e: Option[java.time.LocalDate]
          ): IO[OrderRepository.ShipOutcome] = IO
              .pure(OrderRepository.ShipOutcome.OrderNotFound)
          def deliver(o: Long): IO[OrderRepository.DeliverOutcome] = IO
              .pure(OrderRepository.DeliverOutcome.OrderNotFound)
    )

    private val app =
        OrderApiRoutes(
          OrderApiDelegateImpl(orderService, PaymentService(), auth)
        ).routes.orNotFound

    test("POST /orders/10/payment without a token returns 401"):
        app.run(Request[IO](Method.POST, uri"/orders/10/payment"))
            .map { resp =>
                assertEquals(resp.status, Status.Unauthorized)
            }

    test("POST /orders/10/payment with a valid token pays (200)"):
        auth.issue(7L)
            .flatMap { token =>
                val req = Request[IO](Method.POST, uri"/orders/10/payment")
                    .putHeaders(
                      Authorization(Credentials.Token(AuthScheme.Bearer, token))
                    )
                    .withEntity(
                      PaymentRequest("card", Some("4242 4242 4242 4242"))
                    )
                app.run(req).map(resp => assertEquals(resp.status, Status.Ok))
            }

    test("pay with an unknown method returns 422"):
        auth.issue(7L)
            .flatMap { token =>
                val req = Request[IO](Method.POST, uri"/orders/10/payment")
                    .putHeaders(
                      Authorization(Credentials.Token(AuthScheme.Bearer, token))
                    )
                    .withEntity(PaymentRequest("bitcoin", None))
                app.run(req)
                    .map(resp =>
                        assertEquals(resp.status, Status.UnprocessableEntity)
                    )
            }

    test("pay by card with no card number returns 422"):
        auth.issue(7L)
            .flatMap { token =>
                val req = Request[IO](Method.POST, uri"/orders/10/payment")
                    .putHeaders(
                      Authorization(Credentials.Token(AuthScheme.Bearer, token))
                    )
                    .withEntity(PaymentRequest("card", None))
                app.run(req)
                    .map(resp =>
                        assertEquals(resp.status, Status.UnprocessableEntity)
                    )
            }

    test("pay by cod needs no details (200)"):
        auth.issue(7L)
            .flatMap { token =>
                val req = Request[IO](Method.POST, uri"/orders/10/payment")
                    .putHeaders(
                      Authorization(Credentials.Token(AuthScheme.Bearer, token))
                    )
                    .withEntity(PaymentRequest("cod", None))
                app.run(req).map(resp => assertEquals(resp.status, Status.Ok))
            }

    test("POST /orders/10/cancel without a token returns 401"):
        app.run(Request[IO](Method.POST, uri"/orders/10/cancel"))
            .map(resp => assertEquals(resp.status, Status.Unauthorized))

    test("POST /orders/10/cancel with a valid token cancels (200)"):
        auth.issue(7L)
            .flatMap { token =>
                val req = Request[IO](Method.POST, uri"/orders/10/cancel")
                    .putHeaders(
                      Authorization(Credentials.Token(AuthScheme.Bearer, token))
                    )
                app.run(req).map(resp => assertEquals(resp.status, Status.Ok))
            }

    private def authed(req: Request[IO]): IO[Request[IO]] = auth
        .issue(7L)
        .map(t =>
            req.putHeaders(
              Authorization(Credentials.Token(AuthScheme.Bearer, t))
            )
        )

    test("POST /orders without a token returns 401"):
        app.run(
              Request[IO](Method.POST, uri"/orders")
                  .withEntity(CreateOrder(1L, "Tokyo"))
            )
            .map(r => assertEquals(r.status, Status.Unauthorized))

    test("POST /orders from a valid cart returns the order (200)"):
        authed(
          Request[IO](Method.POST, uri"/orders")
              .withEntity(CreateOrder(1L, "Tokyo"))
        ).flatMap(app.run).map(r => assertEquals(r.status, Status.Ok))

    test("POST /orders for an unowned cart returns 404"):
        authed(
          Request[IO](Method.POST, uri"/orders")
              .withEntity(CreateOrder(9L, "Tokyo"))
        ).flatMap(app.run).map(r => assertEquals(r.status, Status.NotFound))

    test("POST /orders for an empty cart returns 422"):
        authed(
          Request[IO](Method.POST, uri"/orders")
              .withEntity(CreateOrder(2L, "Tokyo"))
        ).flatMap(app.run)
            .map(r => assertEquals(r.status, Status.UnprocessableEntity))

    test("GET /orders returns the caller's orders (200)"):
        authed(Request[IO](Method.GET, uri"/orders"))
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Ok))

    test("GET /orders without a token returns 401"):
        app.run(Request[IO](Method.GET, uri"/orders"))
            .map(r => assertEquals(r.status, Status.Unauthorized))

    test("GET /orders/10 returns the order (200), unknown id 404"):
        authed(Request[IO](Method.GET, uri"/orders/10"))
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Ok)) *>
            authed(Request[IO](Method.GET, uri"/orders/99"))
                .flatMap(app.run)
                .map(r => assertEquals(r.status, Status.NotFound))
