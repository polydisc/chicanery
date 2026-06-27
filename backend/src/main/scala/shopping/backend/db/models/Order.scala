package shopping.backend.db.models

import java.time.LocalDate

final case class OrderItem(
    productId: Long,
    productName: String,
    unitPriceJpy: Int,
    quantity: Int
):
    def lineTotalJpy: Int = unitPriceJpy * quantity

// status mirrors the Postgres `OrderStatus` enum, read as text ('NEW', ...).
// The shipment fields (tracking number, carrier, shipped/ETA dates) are absent
// until an admin marks the order SHIPPED (R9).
final case class Order(
    id: Long,
    items: List[OrderItem],
    shippingAddress: String,
    status: String,
    orderDate: Option[LocalDate],
    trackingNumber: Option[String] = None,
    carrier: Option[String] = None,
    shippedDate: Option[LocalDate] = None,
    estimatedDeliveryDate: Option[LocalDate] = None
):
    def totalJpy: Int = items.map(_.lineTotalJpy).sum
