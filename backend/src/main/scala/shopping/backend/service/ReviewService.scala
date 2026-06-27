package shopping.backend.service

import cats.effect.IO
import shopping.backend.db.models.Review
import shopping.backend.repository.ReviewRepository

// Typed failures for the review write path (mapped to HTTP status in the route).
enum ReviewError:
    case ProductNotFound
    case InvalidReview

final class ReviewService(reviewRepository: ReviewRepository):
    def getByProductId(productId: Long): IO[List[Review]] = reviewRepository
        .findByProductId(productId)

    // Validate the input, then persist in a single statement. A missing product
    // surfaces as a foreign-key violation (insert returns false) -> 404; the
    // author is the authenticated user, never taken from the request body.
    def create(
        productId: Long,
        userId: Long,
        content: String,
        rating: Int
    ): IO[Either[ReviewError, Review]] =
        val trimmed = content.trim
        if rating < 1 || rating > 5 || trimmed.isEmpty then
            IO.pure(Left(ReviewError.InvalidReview))
        else
            reviewRepository
                .insert(productId, userId, trimmed, rating)
                .map {
                    case true =>
                        Right(Review(trimmed, rating))
                    case false =>
                        Left(ReviewError.ProductNotFound)
                }
