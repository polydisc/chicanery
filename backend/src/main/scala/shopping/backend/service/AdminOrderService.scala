package shopping.backend.service

import cats.effect.IO
import java.time.LocalDate
import shopping.backend.db.models.Order
import shopping.backend.repository.OrderRepository

// Admin-only order operations (not scoped to a user): list every order and
// drive the shipment lifecycle (PROCESSING -> SHIPPED -> DELIVERED).
final class AdminOrderService(orderRepository: OrderRepository):

    def listAll: IO[List[Order]] = orderRepository.listAll

    def ship(
        orderId: Long,
        trackingNumber: String,
        carrier: String,
        estimatedDeliveryDate: Option[LocalDate]
    ): IO[OrderRepository.ShipOutcome] = orderRepository
        .ship(orderId, trackingNumber, carrier, estimatedDeliveryDate)

    def deliver(orderId: Long): IO[OrderRepository.DeliverOutcome] =
        orderRepository.deliver(orderId)
