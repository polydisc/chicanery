package shopping.backend.service

import cats.effect.IO
import java.time.LocalDate
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

class AdminOrderServiceSpec extends CatsEffectSuite:

    private val sample = Order(
      id = 10L,
      items = List(OrderItem(1L, "Banana", 200, 2)),
      shippingAddress = "Tokyo",
      status = "PROCESSING",
      orderDate = None
    )

    private def repo(
        all: List[Order] = Nil,
        shipOutcomes: Map[Long, ShipOutcome] = Map.empty,
        deliverOutcomes: Map[Long, DeliverOutcome] = Map.empty
    ): OrderRepository =
        new OrderRepository:
            def createFromCart(c: Long, u: Long, a: String): IO[CreateOutcome] =
                IO.pure(CreateOutcome.CartNotFound)
            def findById(o: Long, u: Long): IO[Option[Order]] = IO.pure(None)
            def list(u: Long): IO[List[Order]] = IO.pure(Nil)
            def pay(o: Long, u: Long, d: String): IO[PaymentOutcome] = IO
                .pure(PaymentOutcome.OrderNotFound)
            def cancel(o: Long, u: Long): IO[CancelOutcome] = IO
                .pure(CancelOutcome.OrderNotFound)
            def listAll: IO[List[Order]] = IO.pure(all)
            def ship(
                orderId: Long,
                trackingNumber: String,
                carrier: String,
                estimatedDeliveryDate: Option[LocalDate]
            ): IO[ShipOutcome] = IO.pure(
              shipOutcomes.getOrElse(orderId, ShipOutcome.OrderNotFound)
            )
            def deliver(orderId: Long): IO[DeliverOutcome] = IO.pure(
              deliverOutcomes.getOrElse(orderId, DeliverOutcome.OrderNotFound)
            )

    test("listAll returns every order"):
        AdminOrderService(repo(all = List(sample)))
            .listAll
            .assertEquals(List(sample))

    test("ship a PROCESSING order returns the shipped order"):
        val shipped = sample.copy(
          status = "SHIPPED",
          trackingNumber = Some("T1"),
          carrier = Some("Yamato")
        )
        AdminOrderService(
          repo(shipOutcomes = Map(10L -> ShipOutcome.Shipped(shipped)))
        ).ship(10L, "T1", "Yamato", Some(LocalDate.parse("2026-07-04")))
            .assertEquals(ShipOutcome.Shipped(shipped))

    test("ship a non-PROCESSING order returns NotShippable"):
        AdminOrderService(
          repo(shipOutcomes = Map(10L -> ShipOutcome.NotShippable))
        ).ship(10L, "T1", "Yamato", None).assertEquals(ShipOutcome.NotShippable)

    test("ship an unknown order returns OrderNotFound"):
        AdminOrderService(repo())
            .ship(99L, "T1", "Yamato", None)
            .assertEquals(ShipOutcome.OrderNotFound)

    test("deliver a SHIPPED order returns the delivered order"):
        val delivered = sample.copy(status = "DELIVERED")
        AdminOrderService(
          repo(deliverOutcomes =
              Map(10L -> DeliverOutcome.Delivered(delivered))
          )
        ).deliver(10L).assertEquals(DeliverOutcome.Delivered(delivered))

    test("deliver a non-SHIPPED order returns NotDeliverable"):
        AdminOrderService(
          repo(deliverOutcomes = Map(10L -> DeliverOutcome.NotDeliverable))
        ).deliver(10L).assertEquals(DeliverOutcome.NotDeliverable)
