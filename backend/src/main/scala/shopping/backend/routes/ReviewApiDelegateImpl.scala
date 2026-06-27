package shopping.backend.routes

import cats.effect.IO
import org.http4s.{Request, Response}
import org.typelevel.log4cats.Logger
import shopping.backend.apis.ReviewApiDelegate
import shopping.backend.apis.ReviewApiDelegate.*
import shopping.backend.db.models.Review as DomainReview
import shopping.backend.models.Review
import shopping.backend.service.{AuthTokenService, ReviewError, ReviewService}

final class ReviewApiDelegateImpl(
    reviewService: ReviewService,
    authTokenService: AuthTokenService
)(using logger: Logger[IO])
    extends ReviewApiDelegate[IO]:

    def getReviewsByProductId: getReviewsByProductId =
        new getReviewsByProductId:
            def handle(
                req: Request[IO],
                productId: Long,
                responses: getReviewsByProductIdResponses[IO]
            ): IO[Response[IO]] = reviewService
                .getByProductId(productId)
                .flatMap(reviews =>
                    responses.resp200(reviews.map(ReviewApiDelegateImpl.toApi))
                )

    // Authenticated: the review author is the verified user, never the body.
    def createReview: createReview =
        new createReview:
            def handle(
                req: Request[IO],
                body: IO[shopping.backend.models.CreateReview],
                productId: Long,
                responses: createReviewResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    body.attempt
                        .flatMap {
                            case Right(input) =>
                                reviewService
                                    .create(
                                      productId,
                                      userId,
                                      input.content,
                                      // rating is a refined 1..5 Int in the
                                      // generated model; unwrap to plain Int.
                                      input.rating.value
                                    )
                                    .flatMap {
                                        case Right(review) =>
                                            responses.resp201(
                                              ReviewApiDelegateImpl
                                                  .toApi(review)
                                            )
                                        case Left(
                                              ReviewError.ProductNotFound
                                            ) =>
                                            responses.resp404()
                                        case Left(ReviewError.InvalidReview) =>
                                            responses.resp422()
                                    }
                            case Left(error) =>
                                logger.warn(
                                  s"createReview decode failed: ${error.getMessage}"
                                ) *> responses.resp422()
                        }
                }

object ReviewApiDelegateImpl:
    private def toApi(r: DomainReview): Review = Review(
      content = r.content,
      rating = r.rating
    )
