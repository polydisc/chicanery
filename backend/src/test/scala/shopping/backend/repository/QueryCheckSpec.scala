package shopping.backend.repository

import cats.effect.IO
import doobie.Transactor
import doobie.munit.IOChecker
import shopping.backend.models.RegisterRequest

/** Type-checks every repository query against a REAL Postgres schema via
  * doobie's analysis (column names, arity, nullability, Scala<->SQL types). It
  * does NOT execute writes. The query values checked here are the exact ones
  * the Live repositories use, so a passing check verifies the real SQL.
  *
  * OPT-IN: these run only when RUN_DB_QUERY_CHECKS=1 AND a Postgres is
  * reachable; otherwise every check is skipped (so `sbt test` stays green
  * offline). They run for real in CI ("DB query analysis" job) against a
  * migrated Postgres.
  *
  * The id / foreign-key columns were aligned to `bigint` in migration V6, so
  * the INTEGER<->Long coercion that previously failed analysis is resolved and
  * all checks pass.
  */
class QueryCheckSpec extends munit.FunSuite with IOChecker:

    private val dbUrl = sys
        .env
        .getOrElse("DB_URL", "jdbc:postgresql://localhost:5432/backend_db")
    private val dbUser = sys.env.getOrElse("DB_USER", "postgres")
    private val dbPassword = sys.env.getOrElse("DB_PASSWORD", "postgres")

    private val enabled = sys
        .env
        .get("RUN_DB_QUERY_CHECKS")
        .exists(v => v == "1" || v == "true")

    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      dbUrl,
      dbUser,
      dbPassword,
      None
    )

    private lazy val dbAvailable: Boolean =
        enabled &&
            scala
                .util
                .Try(
                  java.sql
                      .DriverManager
                      .getConnection(dbUrl, dbUser, dbPassword)
                      .close()
                )
                .isSuccess

    private def checking(name: String)(thunk: => Unit): Unit =
        test(name) {
            assume(
              dbAvailable,
              "query checks disabled (set RUN_DB_QUERY_CHECKS=1 with a reachable Postgres)"
            )
            thunk
        }

    private val sampleRegister = RegisterRequest(
      "e@example.com",
      "secret",
      "First",
      "Last",
      None,
      None
    )

    // users
    checking("users.findByEmail")(
      check(UserRepository.findByEmailQuery("a@example.com"))
    )
    checking("users.insert")(
      check(UserRepository.insertUpdate(sampleRegister, "hash", "tok"))
    )
    checking("users.findRole")(check(UserRepository.findRoleQuery(0L)))
    checking("users.setState")(
      check(UserRepository.setStateUpdate(0L, "ACTIVE"))
    )

    // products
    checking("products.findById")(check(ProductRepository.findByIdQuery(0L)))
    checking("products.insert")(
      check(
        ProductRepository.insertProductUpdate("n", Some("c"), 100, Some("u"))
      )
    )
    checking("products.update")(
      check(
        ProductRepository
            .updateProductUpdate(0L, "n", Some("c"), 100, Some("u"))
      )
    )
    checking("products.delete")(
      check(ProductRepository.deleteProductUpdate(0L))
    )
    checking("products.categories")(
      check(ProductRepository.listCategoriesQuery)
    )
    checking("products.search (filtered)")(
      check(ProductRepository.searchQuery(Some("banana"), Some("fruit"), 0, 20))
    )
    checking("products.search (unfiltered)")(
      check(ProductRepository.searchQuery(None, None, 0, 20))
    )
    checking("products.search (text only)")(
      check(ProductRepository.searchQuery(Some("banana"), None, 0, 20))
    )

    // reviews
    checking("reviews.findByProductId")(
      check(ReviewRepository.findByProductIdQuery(0L))
    )
    checking("reviews.insert")(
      check(ReviewRepository.insertReviewUpdate(0L, 0L, "content", 5))
    )

    // cart
    checking("carts.create")(check(CartRepository.createUpdate(0L)))
    checking("carts.existsForUser")(
      check(CartRepository.existsForUserQuery(0L, 0L))
    )
    checking("cart.items")(check(CartRepository.selectItemsQuery(0L)))
    checking("cart.addItem (upsert)")(
      check(CartRepository.addItemUpdate(0L, 0L, 1, 100))
    )
    checking("cart.setQuantity (update)")(
      check(CartRepository.setQuantityUpdate(0L, 0L, 1))
    )
    checking("cart.setQuantity (delete)")(
      check(CartRepository.setQuantityUpdate(0L, 0L, 0))
    )

    // orders + payment
    checking("orders.insert")(
      check(OrderRepository.insertOrderUpdate(0L, "addr", 100))
    )
    checking("orders.lineItems insert")(check(OrderRepository.insertLineItems))
    checking("orders.deleteCartItems")(
      check(OrderRepository.deleteCartItemsUpdate(0L))
    )
    checking("orders.lockCartForUser")(
      check(OrderRepository.lockCartForUserQuery(0L, 0L))
    )
    checking("orders.header")(check(OrderRepository.orderHeaderQuery(0L, 0L)))
    checking("orders.items")(check(OrderRepository.itemsForQuery(0L)))
    checking("orders.ids")(check(OrderRepository.orderIdsQuery(0L)))
    checking("orders.payStatusTotal")(
      check(OrderRepository.payStatusTotalQuery(0L, 0L))
    )
    checking("payment.insert")(
      check(OrderRepository.insertPaymentUpdate(0L, 0L, 100, "details"))
    )
    checking("payment.markRefunded")(
      check(OrderRepository.markPaymentRefundedUpdate(0L))
    )
    checking("orders.statusForUpdate")(
      check(OrderRepository.statusForUpdateQuery(0L, 0L))
    )
    checking("orders.markCancelled")(
      check(OrderRepository.markCancelledUpdate(0L, 0L))
    )
    checking("orders.markProcessing")(
      check(OrderRepository.markProcessingUpdate(0L, 0L))
    )
    checking("orders.headerAdmin")(
      check(OrderRepository.orderHeaderAdminQuery(0L))
    )
    checking("orders.idsAll")(check(OrderRepository.orderIdsAllQuery))
    checking("orders.ownerStatusForUpdateAdmin")(
      check(OrderRepository.ownerStatusForUpdateAdminQuery(0L))
    )
    checking("orders.markShipped")(
      check(OrderRepository.markShippedUpdate(0L, "track", "carrier", None))
    )
    checking("orders.markDelivered")(
      check(OrderRepository.markDeliveredUpdate(0L))
    )
    checking("notifications.list")(check(NotificationRepository.listQuery(0L)))
    checking("notifications.markRead")(
      check(NotificationRepository.markReadUpdate(0L, 0L))
    )
    checking("notifications.markAllRead")(
      check(NotificationRepository.markAllReadUpdate(0L))
    )
    checking("notifications.insert")(
      check(
        NotificationRepository
            .insertNotificationUpdate(0L, Some(0L), "PAID", "msg")
      )
    )
