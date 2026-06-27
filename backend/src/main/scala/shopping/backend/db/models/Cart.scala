package shopping.backend.db.models

// Domain cart. `lineTotalJpy`/`totalJpy` are derived (no extra columns), so the
// Doobie Read derives from the 4 stored fields of CartItem.
final case class CartItem(
    productId: Long,
    productName: String,
    unitPriceJpy: Int,
    quantity: Int
):
    def lineTotalJpy: Int = unitPriceJpy * quantity

final case class Cart(id: Long, items: List[CartItem]):
    def totalJpy: Int = items.map(_.lineTotalJpy).sum
