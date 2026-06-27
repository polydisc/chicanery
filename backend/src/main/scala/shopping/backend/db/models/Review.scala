package shopping.backend.db.models

// Domain model for a product review (the read-side projection we expose).
final case class Review(content: String, rating: Int)
