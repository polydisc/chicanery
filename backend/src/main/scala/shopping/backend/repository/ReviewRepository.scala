package shopping.backend.repository

import cats.effect.IO
import doobie.{Query0, Transactor, Update0}
import doobie.implicits.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import shopping.backend.db.models.Review

// Review repository (interface + Live impl so the service is testable with a
// fake). COALESCE keeps the non-optional domain fields null-safe.
trait ReviewRepository:
    def findByProductId(productId: Long): IO[List[Review]]

    /** Insert a review. Returns false if the product/user foreign key doesn't
      * exist (so the caller can map it to a 404 without a separate existence
      * check / extra transaction).
      */
    def insert(
        productId: Long,
        userId: Long,
        content: String,
        rating: Int
    ): IO[Boolean]

object ReviewRepository:

    private[repository] def findByProductIdQuery(
        productId: Long
    ): Query0[Review] =
        sql"""SELECT COALESCE(review, ''), COALESCE(rating, 0)
              FROM reviews
              WHERE product_id = $productId
              ORDER BY id""".query[Review]

    private[repository] def insertReviewUpdate(
        productId: Long,
        userId: Long,
        content: String,
        rating: Int
    ): Update0 =
        sql"""INSERT INTO reviews
                  (product_id, user_id, rating, review, created_at, updated_at)
              VALUES
                  ($productId, $userId, $rating, $content,
                   current_date, current_date)""".update

    final class Live(xa: Transactor[IO]) extends ReviewRepository:
        def findByProductId(productId: Long): IO[List[Review]] =
            findByProductIdQuery(productId).to[List].transact(xa)

        def insert(
            productId: Long,
            userId: Long,
            content: String,
            rating: Int
        ): IO[Boolean] = insertReviewUpdate(productId, userId, content, rating)
            .run
            .attemptSomeSqlState {
                case sqlstate.class23.FOREIGN_KEY_VIOLATION =>
                    ()
            }
            .transact(xa)
            .map(_.isRight)
