package shopping.backend.service

import cats.effect.IO
import munit.CatsEffectSuite
import shopping.backend.db.models.{Order, OrderItem}
import shopping.backend.repository.OrderRepository
import shopping.backend.repository.OrderRepository.{
    CancelOutcome,
    CreateOutcome,
    DeliverOutcome,
    PaymentOutcome,
    ShipOutcome
}

class OrderServiceSpec extends CatsEffectSuite:

    private val user = 7L

    private val sampleOrder = Order(
      id = 10L,
      items = List(OrderItem(1L, "Banana", 200, 2)),
      shippingAddress = "Tokyo",
      status = "NEW",
      orderDate = None
    )

    private def orderRepo(
        createOutcomes: Map[Long, CreateOutcome] = Map.empty,
        byId: Map[Long, Order] = Map.empty,
        all: List[Order] = Nil,
        payOutcomes: Map[Long, PaymentOutcome] = Map.empty,
        cancelOutcomes: Map[Long, CancelOutcome] = Map.empty
    ): OrderRepository =
        new OrderRepository:
            def createFromCart(
                cartId: Long,
                userId: Long,
                shippingAddress: String
            ): IO[CreateOutcome] = IO.pure(
              createOutcomes.getOrElse(cartId, CreateOutcome.CartNotFound)
            )
            def findById(orderId: Long, userId: Long): IO[Option[Order]] = IO
                .pure(byId.get(orderId))
            def list(userId: Long): IO[List[Order]] = IO.pure(all)
            def pay(
                orderId: Long,
                userId: Long,
                details: String
            ): IO[PaymentOutcome] = IO.pure(
              payOutcomes.getOrElse(orderId, PaymentOutcome.OrderNotFound)
            )
            def cancel(orderId: Long, userId: Long): IO[CancelOutcome] = IO
                .pure(
                  cancelOutcomes.getOrElse(orderId, CancelOutcome.OrderNotFound)
                )
            def listAll: IO[List[Order]] = IO.pure(all)
            def ship(
                orderId: Long,
                trackingNumber: String,
                carrier: String,
                estimatedDeliveryDate: Option[java.time.LocalDate]
            ): IO[ShipOutcome] = IO.pure(ShipOutcome.OrderNotFound)
            def deliver(orderId: Long): IO[DeliverOutcome] = IO
                .pure(DeliverOutcome.OrderNotFound)

    test("create from a cart the user doesn't own returns CartNotFound"):
        val svc = OrderService(
          orderRepo(createOutcomes = Map(1L -> CreateOutcome.CartNotFound))
        )
        svc.create(1L, user, "Tokyo")
            .assertEquals(Left(OrderError.CartNotFound))

    test("create from an empty cart returns EmptyCart"):
        val svc = OrderService(
          orderRepo(createOutcomes = Map(1L -> CreateOutcome.EmptyCart))
        )
        svc.create(1L, user, "Tokyo").assertEquals(Left(OrderError.EmptyCart))

    test("create from the user's cart with items returns the order"):
        val svc = OrderService(
          orderRepo(createOutcomes =
              Map(1L -> CreateOutcome.Created(sampleOrder))
          )
        )
        svc.create(1L, user, "Tokyo").assertEquals(Right(sampleOrder))

    test("get returns the order when present, None otherwise"):
        val svc = OrderService(orderRepo(byId = Map(10L -> sampleOrder)))
        svc.get(10L, user).assertEquals(Some(sampleOrder)) *>
            svc.get(99L, user).assertEquals(None)

    test("list returns the user's orders"):
        val svc = OrderService(orderRepo(all = List(sampleOrder)))
        svc.list(user).assertEquals(List(sampleOrder))

    test("pay returns the paid order"):
        val paid = sampleOrder.copy(status = "PROCESSING")
        val svc = OrderService(
          orderRepo(payOutcomes = Map(10L -> PaymentOutcome.Paid(paid)))
        )
        svc.pay(10L, user, "card").assertEquals(PaymentOutcome.Paid(paid))

    test("pay an unknown/unowned order returns OrderNotFound"):
        val svc = OrderService(orderRepo())
        svc.pay(99L, user, "card").assertEquals(PaymentOutcome.OrderNotFound)

    test("pay an already-paid order returns NotPayable"):
        val svc = OrderService(
          orderRepo(payOutcomes = Map(10L -> PaymentOutcome.NotPayable))
        )
        svc.pay(10L, user, "card").assertEquals(PaymentOutcome.NotPayable)

    test("cancel returns the cancelled order"):
        val cancelled = sampleOrder.copy(status = "CANCELLED")
        val svc = OrderService(
          orderRepo(cancelOutcomes =
              Map(10L -> CancelOutcome.Cancelled(cancelled))
          )
        )
        svc.cancel(10L, user).assertEquals(CancelOutcome.Cancelled(cancelled))

    test("cancel an unknown/unowned order returns OrderNotFound"):
        val svc = OrderService(orderRepo())
        svc.cancel(99L, user).assertEquals(CancelOutcome.OrderNotFound)

    test("cancel a shipped order returns NotCancellable"):
        val svc = OrderService(
          orderRepo(cancelOutcomes = Map(10L -> CancelOutcome.NotCancellable))
        )
        svc.cancel(10L, user).assertEquals(CancelOutcome.NotCancellable)
