package shopping.backend.routes

import cats.effect.IO
import org.http4s.{Request, Response}
import org.typelevel.log4cats.Logger
import shopping.backend.apis.CartApiDelegate
import shopping.backend.apis.CartApiDelegate.*
import shopping.backend.db.models.Cart as DomainCart
import shopping.backend.models.{Cart, CartItem}
import shopping.backend.service.{AuthTokenService, CartError, CartService}

// All cart endpoints are auth-gated (AuthSupport.withUser -> 401 if no/invalid
// token) and scoped to the authenticated user, so carts can't be read or
// mutated by anyone but their owner.
final class CartApiDelegateImpl(
    cartService: CartService,
    authTokenService: AuthTokenService
)(using logger: Logger[IO])
    extends CartApiDelegate[IO]:

    def createCart: createCart =
        new createCart:
            def handle(
                req: Request[IO],
                responses: createCartResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    cartService
                        .create(userId)
                        .flatMap(cart =>
                            responses.resp200(CartApiDelegateImpl.toApi(cart))
                        )
                }

    def getCart: getCart =
        new getCart:
            def handle(
                req: Request[IO],
                cartId: Long,
                responses: getCartResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    cartService
                        .get(cartId, userId)
                        .flatMap {
                            case Right(cart) =>
                                responses
                                    .resp200(CartApiDelegateImpl.toApi(cart))
                            case Left(_) =>
                                responses.resp404()
                        }
                }

    def addCartItem: addCartItem =
        new addCartItem:
            def handle(
                req: Request[IO],
                body: IO[shopping.backend.models.AddCartItem],
                cartId: Long,
                responses: addCartItemResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    body.attempt
                        .flatMap {
                            case Right(item) =>
                                cartService
                                    .addItem(
                                      cartId,
                                      userId,
                                      item.productId,
                                      item.quantity
                                    )
                                    .flatMap {
                                        case Right(cart) =>
                                            responses.resp200(
                                              CartApiDelegateImpl.toApi(cart)
                                            )
                                        case Left(CartError.InvalidQuantity) =>
                                            responses.resp422()
                                        case Left(_) =>
                                            responses.resp404()
                                    }
                            case Left(error) =>
                                logger.warn(
                                  s"addCartItem decode failed: ${error.getMessage}"
                                ) *> responses.resp422()
                        }
                }

    def updateCartItem: updateCartItem =
        new updateCartItem:
            def handle(
                req: Request[IO],
                body: IO[shopping.backend.models.UpdateCartItem],
                cartId: Long,
                productId: Long,
                responses: updateCartItemResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    body.attempt
                        .flatMap {
                            case Right(update) =>
                                cartService
                                    .updateItem(
                                      cartId,
                                      userId,
                                      productId,
                                      update.quantity
                                    )
                                    .flatMap {
                                        case Right(cart) =>
                                            responses.resp200(
                                              CartApiDelegateImpl.toApi(cart)
                                            )
                                        case Left(CartError.InvalidQuantity) =>
                                            responses.resp422()
                                        case Left(_) =>
                                            responses.resp404()
                                    }
                            case Left(error) =>
                                logger.warn(
                                  s"updateCartItem decode failed: ${error.getMessage}"
                                ) *> responses.resp422()
                        }
                }

object CartApiDelegateImpl:
    private def toApi(c: DomainCart): Cart = Cart(
      id = c.id,
      items = c
          .items
          .map(ci =>
              CartItem(
                productId = ci.productId,
                productName = ci.productName,
                unitPriceJpy = ci.unitPriceJpy,
                quantity = ci.quantity,
                lineTotalJpy = ci.lineTotalJpy
              )
          ),
      totalJpy = c.totalJpy
    )
