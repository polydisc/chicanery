package shopping.backend.repository

import cats.effect.IO
import cats.syntax.all.*
import doobie.{ConnectionIO, Query0, Transactor, Update, Update0}
import doobie.implicits.*
import doobie.postgres.implicits.*
import java.time.LocalDate
import shopping.backend.db.models.{Order, OrderItem}

trait OrderRepository:
    /** Creates an order from the cart's items in ONE transaction: it locks the
      * caller's cart row (`FOR UPDATE`), verifies ownership, snapshots prices,
      * inserts the order, and empties the cart. The lock serialises concurrent
      * checkouts of the same cart, so a double submit can't create two orders —
      * the second attempt sees an empty cart. Returns CartNotFound when the
      * cart doesn't exist or isn't the caller's, EmptyCart when it has no
      * items.
      */
    def createFromCart(
        cartId: Long,
        userId: Long,
        shippingAddress: String
    ): IO[OrderRepository.CreateOutcome]

    /** Returns the order only if it belongs to `userId` (ownership guard). */
    def findById(orderId: Long, userId: Long): IO[Option[Order]]
    def list(userId: Long): IO[List[Order]]

    /** Atomically pays for the user's order: records the payment and moves a
      * NEW order to PROCESSING. The order row is locked for the transaction so
      * concurrent payments can't double-charge.
      */
    def pay(
        orderId: Long,
        userId: Long,
        details: String
    ): IO[OrderRepository.PaymentOutcome]

    /** Atomically cancels the user's order if it hasn't shipped yet (status is
      * NEW/PROCESSING/HOLD). The row is locked for the transaction. Cancelling
      * a PROCESSING (paid) order records a stub refund (the payment is flipped
      * to REFUNDED) — there is no real gateway, so no actual money moves.
      */
    def cancel(orderId: Long, userId: Long): IO[OrderRepository.CancelOutcome]

    /** Lists every order regardless of owner (admin order management). */
    def listAll: IO[List[Order]]

    /** Admin: moves a PROCESSING order to SHIPPED, recording the tracking
      * number, carrier and ETA and stamping the shipped date. The row is locked
      * for the transaction.
      */
    def ship(
        orderId: Long,
        trackingNumber: String,
        carrier: String,
        estimatedDeliveryDate: Option[LocalDate]
    ): IO[OrderRepository.ShipOutcome]

    /** Admin: moves a SHIPPED order to DELIVERED. The row is locked for the
      * transaction.
      */
    def deliver(orderId: Long): IO[OrderRepository.DeliverOutcome]

object OrderRepository:

    enum CreateOutcome:
        case CartNotFound
        case EmptyCart
        case Created(order: Order)

    enum PaymentOutcome:
        case OrderNotFound
        case NotPayable
        case Paid(order: Order)

    enum CancelOutcome:
        case OrderNotFound
        case NotCancellable
        case Cancelled(order: Order)

    enum ShipOutcome:
        case OrderNotFound
        case NotShippable
        case Shipped(order: Order)

    enum DeliverOutcome:
        case OrderNotFound
        case NotDeliverable
        case Delivered(order: Order)

    // An order may be cancelled until it ships.
    private val CancellableStatuses = Set("NEW", "PROCESSING", "HOLD")

    // Builds the order-event notification written (in the same transaction) when
    // an order changes state. Returns the ConnectionIO insert.
    private def insertOrderNotification(
        userId: Long,
        orderId: Long,
        notificationType: String,
        message: String
    ): ConnectionIO[Int] =
        NotificationRepository
            .insertNotificationUpdate(
              userId,
              Some(orderId),
              notificationType,
              message
            )
            .run

    // Order header columns shared by the owner-scoped and admin reads:
    // (shippingAddress, status, orderDate, trackingNumber, carrier,
    //  shippedDate, estimatedDeliveryDate).
    private type Header =
        (
            String,
            String,
            Option[LocalDate],
            Option[String],
            Option[String],
            Option[LocalDate],
            Option[LocalDate]
        )

    private[repository] def itemsForQuery(orderId: Long): Query0[OrderItem] =
        sql"""SELECT p.id, COALESCE(p.product_name, ''), li.price, li.quantity
              FROM line_item li
              JOIN products p ON li.product_id = p.id
              WHERE li.order_id = $orderId
              ORDER BY p.id""".query[OrderItem]

    // The shipment columns are nullable (absent until an order ships).
    private val headerColumns =
        fr"""COALESCE(shipping_address, ''),
             COALESCE(order_status::text, 'NEW'), order_date,
             tracking_number, carrier, shipped_date, estimated_delivery_date"""

    private[repository] def orderHeaderQuery(
        orderId: Long,
        userId: Long
    ): Query0[Header] =
        sql"""SELECT $headerColumns
              FROM orders WHERE id = $orderId AND user_id = $userId"""
            .query[Header]

    private[repository] def orderHeaderAdminQuery(
        orderId: Long
    ): Query0[Header] =
        sql"""SELECT $headerColumns
              FROM orders WHERE id = $orderId""".query[Header]

    private[repository] def orderIdsAllQuery: Query0[Long] =
        sql"SELECT id FROM orders ORDER BY id DESC".query[Long]

    // Returns (ownerUserId, status). The owner is needed so the SHIPPED /
    // DELIVERED notification can be addressed to the order's user even though
    // the admin transition itself isn't user-scoped.
    private[repository] def ownerStatusForUpdateAdminQuery(
        orderId: Long
    ): Query0[(Long, String)] =
        sql"""SELECT user_id, COALESCE(order_status::text, '')
              FROM orders WHERE id = $orderId FOR UPDATE"""
            .query[(Long, String)]

    private[repository] def markShippedUpdate(
        orderId: Long,
        trackingNumber: String,
        carrier: String,
        estimatedDeliveryDate: Option[LocalDate]
    ): Update0 =
        sql"""UPDATE orders
              SET order_status = 'SHIPPED',
                  shipped_date = current_date,
                  tracking_number = $trackingNumber,
                  carrier = $carrier,
                  estimated_delivery_date = $estimatedDeliveryDate
              WHERE id = $orderId AND order_status = 'PROCESSING'""".update

    private[repository] def markDeliveredUpdate(orderId: Long): Update0 =
        sql"""UPDATE orders SET order_status = 'DELIVERED'
              WHERE id = $orderId AND order_status = 'SHIPPED'""".update

    private[repository] def insertOrderUpdate(
        userId: Long,
        shippingAddress: String,
        total: Int
    ): Update0 =
        sql"""INSERT INTO orders
              (user_id, shipping_address, totalprice, order_date, order_status)
              VALUES ($userId, $shippingAddress, $total,
                      current_date, 'NEW')""".update

    private[repository] val insertLineItems: Update[(Long, Long, Int, Int)] =
        Update[(Long, Long, Int, Int)](
          """INSERT INTO line_item (order_id, product_id, price, quantity)
             VALUES (?, ?, ?, ?)"""
        )

    private[repository] def deleteCartItemsUpdate(cartId: Long): Update0 =
        sql"""DELETE FROM line_item
              WHERE cart_id = $cartId AND order_id IS NULL""".update

    // Locks the caller's cart row for the checkout transaction (also serves as
    // the ownership check). Returns the cart id only if it's the user's.
    private[repository] def lockCartForUserQuery(
        cartId: Long,
        userId: Long
    ): Query0[Long] =
        sql"""SELECT id FROM carts
              WHERE id = $cartId AND user_id = $userId FOR UPDATE""".query[Long]

    private[repository] def orderIdsQuery(userId: Long): Query0[Long] =
        sql"SELECT id FROM orders WHERE user_id = $userId ORDER BY id DESC"
            .query[Long]

    private[repository] def payStatusTotalQuery(
        orderId: Long,
        userId: Long
    ): Query0[(String, Int)] =
        sql"""SELECT COALESCE(order_status::text, ''), COALESCE(totalprice, 0)
              FROM orders
              WHERE id = $orderId AND user_id = $userId
              FOR UPDATE""".query[(String, Int)]

    private[repository] def insertPaymentUpdate(
        userId: Long,
        orderId: Long,
        amount: Int,
        details: String
    ): Update0 =
        sql"""INSERT INTO payment (user_id, order_id, amount, details)
              VALUES ($userId, $orderId, $amount, $details)""".update

    // Stub refund (R6): flip the order's payment(s) to REFUNDED. No real money
    // movement — wiring an actual gateway refund is a separate follow-up.
    private[repository] def markPaymentRefundedUpdate(orderId: Long): Update0 =
        sql"""UPDATE payment SET status = 'REFUNDED'
              WHERE order_id = $orderId""".update

    private[repository] def markProcessingUpdate(
        orderId: Long,
        userId: Long
    ): Update0 =
        sql"""UPDATE orders SET order_status = 'PROCESSING'
              WHERE id = $orderId AND user_id = $userId
                AND order_status = 'NEW'""".update

    private[repository] def statusForUpdateQuery(
        orderId: Long,
        userId: Long
    ): Query0[String] =
        sql"""SELECT COALESCE(order_status::text, '')
              FROM orders
              WHERE id = $orderId AND user_id = $userId
              FOR UPDATE""".query[String]

    private[repository] def markCancelledUpdate(
        orderId: Long,
        userId: Long
    ): Update0 =
        // The FOR UPDATE read already gates cancellability; the status guard is
        // belt-and-suspenders (mirrors markProcessingUpdate) so a shipped order
        // can never be flipped to CANCELLED even if the lock assumption breaks.
        sql"""UPDATE orders SET order_status = 'CANCELLED'
              WHERE id = $orderId AND user_id = $userId
                AND order_status IN ('NEW', 'PROCESSING', 'HOLD')""".update

    private def itemsFor(orderId: Long): ConnectionIO[List[OrderItem]] =
        itemsForQuery(orderId).to[List]

    private def orderFromHeader(
        orderId: Long,
        header: Option[Header]
    ): ConnectionIO[Option[Order]] =
        header match
            case None =>
                Option.empty[Order].pure[ConnectionIO]
            case Some(
                  (address, status, date, tracking, carrier, shipped, eta)
                ) =>
                itemsFor(orderId).map(items =>
                    Some(
                      Order(
                        id = orderId,
                        items = items,
                        shippingAddress = address,
                        status = status,
                        orderDate = date,
                        trackingNumber = tracking,
                        carrier = carrier,
                        shippedDate = shipped,
                        estimatedDeliveryDate = eta
                      )
                    )
                )

    private def orderById(
        orderId: Long,
        userId: Long
    ): ConnectionIO[Option[Order]] = orderHeaderQuery(orderId, userId)
        .option
        .flatMap(orderFromHeader(orderId, _))

    private def orderByIdAdmin(orderId: Long): ConnectionIO[Option[Order]] =
        orderHeaderAdminQuery(orderId)
            .option
            .flatMap(orderFromHeader(orderId, _))

    final class Live(xa: Transactor[IO]) extends OrderRepository:

        def createFromCart(
            cartId: Long,
            userId: Long,
            shippingAddress: String
        ): IO[CreateOutcome] =
            val program: ConnectionIO[CreateOutcome] = lockCartForUserQuery(
              cartId,
              userId
            ).option
                .flatMap {
                    case None =>
                        CreateOutcome.CartNotFound.pure[ConnectionIO]
                    case Some(_) =>
                        CartRepository
                            .selectItems(cartId)
                            .flatMap { cartItems =>
                                if cartItems.isEmpty then
                                    CreateOutcome.EmptyCart.pure[ConnectionIO]
                                else
                                    val total =
                                        cartItems.map(_.lineTotalJpy).sum
                                    for
                                        keys <- insertOrderUpdate(
                                          userId,
                                          shippingAddress,
                                          total
                                        ).withUniqueGeneratedKeys[
                                          (Long, LocalDate)
                                        ]("id", "order_date")
                                        (orderId, orderDate) = keys
                                        rows = cartItems.map(ci =>
                                            (
                                              orderId,
                                              ci.productId,
                                              ci.unitPriceJpy,
                                              ci.quantity
                                            )
                                        )
                                        _ <- insertLineItems.updateMany(rows)
                                        _ <- deleteCartItemsUpdate(cartId).run
                                        _ <- insertOrderNotification(
                                          userId,
                                          orderId,
                                          "ORDER_PLACED",
                                          s"Order #$orderId placed."
                                        )
                                    yield CreateOutcome.Created(
                                      Order(
                                        id = orderId,
                                        items = cartItems.map(ci =>
                                            OrderItem(
                                              ci.productId,
                                              ci.productName,
                                              ci.unitPriceJpy,
                                              ci.quantity
                                            )
                                        ),
                                        shippingAddress = shippingAddress,
                                        status = "NEW",
                                        orderDate = Some(orderDate)
                                      )
                                    )
                            }
                }
            program.transact(xa)

        def findById(orderId: Long, userId: Long): IO[Option[Order]] =
            orderById(orderId, userId).transact(xa)

        // Composes id-list + per-order loads into ONE transaction (no N+1
        // round-trips, consistent snapshot). Scoped to the user's own orders.
        def list(userId: Long): IO[List[Order]] = orderIdsQuery(userId)
            .to[List]
            .flatMap(_.traverse(orderById(_, userId)).map(_.flatten))
            .transact(xa)

        def pay(
            orderId: Long,
            userId: Long,
            details: String
        ): IO[PaymentOutcome] =
            val program: ConnectionIO[PaymentOutcome] = payStatusTotalQuery(
              orderId,
              userId
            ).option
                .flatMap {
                    case None =>
                        PaymentOutcome.OrderNotFound.pure[ConnectionIO]
                    case Some(("NEW", amount)) =>
                        for
                            _ <-
                                insertPaymentUpdate(
                                  userId,
                                  orderId,
                                  amount,
                                  details
                                ).run
                            _ <- markProcessingUpdate(orderId, userId).run
                            _ <- insertOrderNotification(
                              userId,
                              orderId,
                              "PAID",
                              s"Payment received for order #$orderId."
                            )
                            order <- orderById(orderId, userId)
                            outcome <-
                                order match
                                    case Some(o) =>
                                        (PaymentOutcome.Paid(o): PaymentOutcome)
                                            .pure[ConnectionIO]
                                    case None =>
                                        IllegalStateException(
                                          s"order $orderId vanished mid-payment"
                                        ).raiseError[
                                          ConnectionIO,
                                          PaymentOutcome
                                        ]
                        yield outcome
                    case Some(_) =>
                        PaymentOutcome.NotPayable.pure[ConnectionIO]
                }
            program.transact(xa)

        def cancel(orderId: Long, userId: Long): IO[CancelOutcome] =
            val program: ConnectionIO[CancelOutcome] = statusForUpdateQuery(
              orderId,
              userId
            ).option
                .flatMap {
                    case None =>
                        CancelOutcome.OrderNotFound.pure[ConnectionIO]
                    case Some(status) if CancellableStatuses.contains(status) =>
                        // A PROCESSING order has been paid, so cancelling it
                        // records a (stub) refund; NEW/HOLD have no payment.
                        val wasPaid = status == "PROCESSING"
                        for
                            _ <- markCancelledUpdate(orderId, userId).run
                            _ <-
                                if wasPaid then
                                    markPaymentRefundedUpdate(orderId).run
                                else
                                    0.pure[ConnectionIO]
                            _ <- insertOrderNotification(
                              userId,
                              orderId,
                              "CANCELLED",
                              if wasPaid then
                                  s"Order #$orderId cancelled; refund issued."
                              else
                                  s"Order #$orderId cancelled."
                            )
                            order <- orderById(orderId, userId)
                            outcome <-
                                order match
                                    case Some(o) =>
                                        val out: CancelOutcome = CancelOutcome
                                            .Cancelled(o)
                                        out.pure[ConnectionIO]
                                    case None =>
                                        IllegalStateException(
                                          s"order $orderId vanished mid-cancel"
                                        ).raiseError[
                                          ConnectionIO,
                                          CancelOutcome
                                        ]
                        yield outcome
                    case Some(_) =>
                        CancelOutcome.NotCancellable.pure[ConnectionIO]
                }
            program.transact(xa)

        // Admin reads/writes are NOT scoped to a user (admin order management).
        def listAll: IO[List[Order]] = orderIdsAllQuery
            .to[List]
            .flatMap(_.traverse(orderByIdAdmin).map(_.flatten))
            .transact(xa)

        def ship(
            orderId: Long,
            trackingNumber: String,
            carrier: String,
            estimatedDeliveryDate: Option[LocalDate]
        ): IO[ShipOutcome] =
            val program: ConnectionIO[ShipOutcome] =
                ownerStatusForUpdateAdminQuery(orderId)
                    .option
                    .flatMap {
                        case None =>
                            ShipOutcome.OrderNotFound.pure[ConnectionIO]
                        case Some((ownerId, "PROCESSING")) =>
                            for
                                _ <-
                                    markShippedUpdate(
                                      orderId,
                                      trackingNumber,
                                      carrier,
                                      estimatedDeliveryDate
                                    ).run
                                _ <- insertOrderNotification(
                                  ownerId,
                                  orderId,
                                  "SHIPPED",
                                  s"Order #$orderId shipped via $carrier " +
                                      s"($trackingNumber)."
                                )
                                order <- orderByIdAdmin(orderId)
                                outcome <-
                                    order match
                                        case Some(o) =>
                                            (
                                              ShipOutcome
                                                  .Shipped(o): ShipOutcome
                                            ).pure[ConnectionIO]
                                        case None =>
                                            IllegalStateException(
                                              s"order $orderId vanished mid-ship"
                                            ).raiseError[
                                              ConnectionIO,
                                              ShipOutcome
                                            ]
                            yield outcome
                        case Some(_) =>
                            ShipOutcome.NotShippable.pure[ConnectionIO]
                    }
            program.transact(xa)

        def deliver(orderId: Long): IO[DeliverOutcome] =
            val program: ConnectionIO[DeliverOutcome] =
                ownerStatusForUpdateAdminQuery(orderId)
                    .option
                    .flatMap {
                        case None =>
                            DeliverOutcome.OrderNotFound.pure[ConnectionIO]
                        case Some((ownerId, "SHIPPED")) =>
                            for
                                _ <- markDeliveredUpdate(orderId).run
                                _ <- insertOrderNotification(
                                  ownerId,
                                  orderId,
                                  "DELIVERED",
                                  s"Order #$orderId delivered."
                                )
                                order <- orderByIdAdmin(orderId)
                                outcome <-
                                    order match
                                        case Some(o) =>
                                            (
                                              DeliverOutcome
                                                  .Delivered(o): DeliverOutcome
                                            ).pure[ConnectionIO]
                                        case None =>
                                            IllegalStateException(
                                              s"order $orderId vanished mid-deliver"
                                            ).raiseError[
                                              ConnectionIO,
                                              DeliverOutcome
                                            ]
                            yield outcome
                        case Some(_) =>
                            DeliverOutcome.NotDeliverable.pure[ConnectionIO]
                    }
            program.transact(xa)
