package shopping.backend.service

import cats.effect.IO
import cats.effect.kernel.Ref
import munit.CatsEffectSuite
import shopping.backend.db.models.Review
import shopping.backend.repository.ReviewRepository

class ReviewServiceSpec extends CatsEffectSuite:

    // Inserted rows are recorded so we can assert the write happened (and with
    // the trimmed content / authenticated author). The fake mimics the FK
    // contract: insert returns false for a product that doesn't exist.
    private type Inserted = (Long, Long, String, Int)

    private def fakeReviewRepo(
        existingProducts: Set[Long],
        byProduct: Map[Long, List[Review]],
        inserts: Ref[IO, List[Inserted]]
    ): ReviewRepository =
        new ReviewRepository:
            def findByProductId(productId: Long): IO[List[Review]] = IO
                .pure(byProduct.getOrElse(productId, Nil))
            def insert(
                productId: Long,
                userId: Long,
                content: String,
                rating: Int
            ): IO[Boolean] =
                if existingProducts.contains(productId) then
                    inserts
                        .update(_ :+ (productId, userId, content, rating))
                        .as(true)
                else
                    IO.pure(false)

    private def withService(
        existingProducts: Set[Long] = Set(1L),
        byProduct: Map[Long, List[Review]] = Map.empty
    )(run: (ReviewService, Ref[IO, List[Inserted]]) => IO[Unit]): IO[Unit] = Ref
        .of[IO, List[Inserted]](Nil)
        .flatMap { inserts =>
            run(
              ReviewService(
                fakeReviewRepo(existingProducts, byProduct, inserts)
              ),
              inserts
            )
        }

    test("getByProductId returns the product's reviews"):
        val reviews = List(Review("Great", 5), Review("Okay", 3))
        withService(byProduct = Map(1L -> reviews)) { (svc, _) =>
            assertIO(svc.getByProductId(1L), reviews)
        }

    test("create persists a valid review by the authenticated user"):
        withService() { (svc, inserts) =>
            for
                result <- svc.create(1L, 7L, "  Loved it  ", 5)
                _ = assertEquals(result, Right(Review("Loved it", 5)))
                recorded <- inserts.get
                _ = assertEquals(recorded, List((1L, 7L, "Loved it", 5)))
            yield ()
        }

    test("create maps a missing product (FK violation) to ProductNotFound"):
        withService() { (svc, inserts) =>
            for
                result <- svc.create(99L, 7L, "Nice", 5)
                _ = assertEquals(result, Left(ReviewError.ProductNotFound))
                recorded <- inserts.get
                _ = assertEquals(recorded, Nil)
            yield ()
        }

    test("create rejects an out-of-range rating without touching the db"):
        withService() { (svc, inserts) =>
            for
                tooHigh <- svc.create(1L, 7L, "Nice", 6)
                tooLow <- svc.create(1L, 7L, "Nice", 0)
                _ = assertEquals(tooHigh, Left(ReviewError.InvalidReview))
                _ = assertEquals(tooLow, Left(ReviewError.InvalidReview))
                recorded <- inserts.get
                _ = assertEquals(recorded, Nil)
            yield ()
        }

    test("create rejects blank content"):
        withService() { (svc, _) =>
            assertIO(
              svc.create(1L, 7L, "   ", 4),
              Left(ReviewError.InvalidReview)
            )
        }
