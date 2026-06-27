package shopping.backend.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shopping.backend.apis.CartApiRoutes
import shopping.backend.db.models.{CartItem, Product}
import shopping.backend.repository.{CartRepository, ProductRepository}
import shopping.backend.service.{AuthTokenService, CartService}

class CartRoutesSpec extends CatsEffectSuite:

    private given Logger[IO] = Slf4jLogger.getLogger[IO]
    private val auth = AuthTokenService.Live("test-secret", 3600)

    private val cartService = CartService(
      new CartRepository:
          def create(userId: Long): IO[Long] = IO.pure(1L)
          def existsForUser(cartId: Long, userId: Long): IO[Boolean] = IO
              .pure(true)
          def items(cartId: Long): IO[List[CartItem]] = IO.pure(Nil)
          def addItem(c: Long, p: Long, q: Int, u: Int): IO[Unit] = IO.unit
          def setItemQuantity(c: Long, p: Long, q: Int): IO[Int] = IO.pure(0)
      ,
      new ProductRepository:
          def findById(id: Long): IO[Option[Product]] = IO.pure(None)
          def search(
              q: Option[String],
              category: Option[String],
              p: Int,
              s: Int
          ): IO[List[Product]] = IO.pure(Nil)
          def listCategories(): IO[List[String]] = IO.pure(Nil)
          def create(
              name: String,
              category: Option[String],
              priceJpy: Int,
              imageUrl: Option[String]
          ): IO[Product] = IO
              .pure(Product(0L, name, category, priceJpy, imageUrl))
          def update(
              id: Long,
              name: String,
              category: Option[String],
              priceJpy: Int,
              imageUrl: Option[String]
          ): IO[Option[Product]] = IO.pure(None)
          def delete(id: Long): IO[ProductRepository.DeleteResult] = IO
              .pure(ProductRepository.DeleteResult.NotFound)
    )

    private val app =
        CartApiRoutes(CartApiDelegateImpl(cartService, auth)).routes.orNotFound

    test("POST /cart without a token returns 401"):
        app.run(Request[IO](Method.POST, uri"/cart"))
            .map { resp =>
                assertEquals(resp.status, Status.Unauthorized)
            }

    test("POST /cart with an invalid token returns 401"):
        val req = Request[IO](Method.POST, uri"/cart").putHeaders(
          Authorization(Credentials.Token(AuthScheme.Bearer, "garbage"))
        )
        app.run(req).map(resp => assertEquals(resp.status, Status.Unauthorized))

    test("POST /cart with a valid token creates a cart (200)"):
        auth.issue(5L)
            .flatMap { token =>
                val req = Request[IO](Method.POST, uri"/cart").putHeaders(
                  Authorization(Credentials.Token(AuthScheme.Bearer, token))
                )
                app.run(req).map(resp => assertEquals(resp.status, Status.Ok))
            }
