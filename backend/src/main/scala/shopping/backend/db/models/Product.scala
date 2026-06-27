package shopping.backend.db.models

// Domain model for a product row. Kept free of HTTP/circe concerns; the route
// layer maps this to the generated API type `shopping.backend.models.Product`.
final case class Product(
    id: Long,
    productName: String,
    productCategory: Option[String],
    priceJpy: Int,
    productImageUrl: Option[String]
)
