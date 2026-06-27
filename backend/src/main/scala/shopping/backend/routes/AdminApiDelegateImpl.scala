package shopping.backend.routes

import cats.effect.IO
import org.http4s.{Request, Response}
import org.typelevel.log4cats.Logger
import shopping.backend.apis.AdminApiDelegate
import shopping.backend.apis.AdminApiDelegate.*
import shopping.backend.db.models.Product as DomainProduct
import shopping.backend.models.{Product, ProductInput}
import shopping.backend.repository.OrderRepository.{DeliverOutcome, ShipOutcome}
import shopping.backend.repository.ProductRepository.DeleteResult
import shopping.backend.service.{
    AdminOrderService,
    AuthTokenService,
    ProductService,
    UserService
}

// Admin-only endpoints (product CRUD + user block/unblock + order management).
// Every handler is gated by AdminSupport.withAdmin -> 401 (no token) / 403 (not
// an admin).
final class AdminApiDelegateImpl(
    productService: ProductService,
    userService: UserService,
    adminOrderService: AdminOrderService,
    authTokenService: AuthTokenService
)(using logger: Logger[IO])
    extends AdminApiDelegate[IO]:

    private def withAdmin(
        req: Request[IO]
    )(f: Long => IO[Response[IO]]): IO[Response[IO]] =
        AdminSupport.withAdmin(req, authTokenService, userService)(f)

    def adminCheck: adminCheck =
        new adminCheck:
            def handle(
                req: Request[IO],
                responses: adminCheckResponses[IO]
            ): IO[Response[IO]] = withAdmin(req)(_ => responses.resp200())

    def createProduct: createProduct =
        new createProduct:
            def handle(
                req: Request[IO],
                body: IO[ProductInput],
                responses: createProductResponses[IO]
            ): IO[Response[IO]] =
                withAdmin(req) { _ =>
                    body.attempt
                        .flatMap {
                            case Right(in) =>
                                productService
                                    .createProduct(
                                      in.productName,
                                      in.productCategory,
                                      in.priceJpy,
                                      in.productImageUrl
                                    )
                                    .flatMap {
                                        case Right(p) =>
                                            responses.resp201(
                                              AdminApiDelegateImpl.toApi(p)
                                            )
                                        case Left(_) =>
                                            responses.resp422()
                                    }
                            case Left(error) =>
                                logger.warn(
                                  s"createProduct decode failed: ${error.getMessage}"
                                ) *> responses.resp422()
                        }
                }

    def updateProduct: updateProduct =
        new updateProduct:
            def handle(
                req: Request[IO],
                body: IO[ProductInput],
                productId: Long,
                responses: updateProductResponses[IO]
            ): IO[Response[IO]] =
                withAdmin(req) { _ =>
                    body.attempt
                        .flatMap {
                            case Right(in) =>
                                productService
                                    .updateProduct(
                                      productId,
                                      in.productName,
                                      in.productCategory,
                                      in.priceJpy,
                                      in.productImageUrl
                                    )
                                    .flatMap {
                                        case Right(Some(p)) =>
                                            responses.resp200(
                                              AdminApiDelegateImpl.toApi(p)
                                            )
                                        case Right(None) =>
                                            responses.resp404()
                                        case Left(_) =>
                                            responses.resp422()
                                    }
                            case Left(error) =>
                                logger.warn(
                                  s"updateProduct decode failed: ${error.getMessage}"
                                ) *> responses.resp422()
                        }
                }

    def deleteProduct: deleteProduct =
        new deleteProduct:
            def handle(
                req: Request[IO],
                productId: Long,
                responses: deleteProductResponses[IO]
            ): IO[Response[IO]] =
                withAdmin(req) { _ =>
                    productService
                        .deleteProduct(productId)
                        .flatMap {
                            case DeleteResult.Deleted =>
                                responses.resp204()
                            case DeleteResult.NotFound =>
                                responses.resp404()
                            case DeleteResult.InUse =>
                                responses.resp409()
                        }
                }

    def blockUser: blockUser =
        new blockUser:
            def handle(
                req: Request[IO],
                userId: Long,
                responses: blockUserResponses[IO]
            ): IO[Response[IO]] =
                withAdmin(req) { _ =>
                    userService
                        .block(userId)
                        .flatMap {
                            case UserService.BlockOutcome.Done =>
                                responses.resp204()
                            case UserService.BlockOutcome.NotFound =>
                                responses.resp404()
                            case UserService.BlockOutcome.CannotBlockAdmin =>
                                responses.resp409()
                        }
                }

    def unblockUser: unblockUser =
        new unblockUser:
            def handle(
                req: Request[IO],
                userId: Long,
                responses: unblockUserResponses[IO]
            ): IO[Response[IO]] =
                withAdmin(req) { _ =>
                    userService
                        .unblock(userId)
                        .flatMap {
                            case true =>
                                responses.resp204()
                            case false =>
                                responses.resp404()
                        }
                }

    def listAllOrders: listAllOrders =
        new listAllOrders:
            def handle(
                req: Request[IO],
                responses: listAllOrdersResponses[IO]
            ): IO[Response[IO]] =
                withAdmin(req) { _ =>
                    adminOrderService
                        .listAll
                        .flatMap(orders =>
                            responses
                                .resp200(orders.map(OrderApiDelegateImpl.toApi))
                        )
                }

    def shipOrder: shipOrder =
        new shipOrder:
            def handle(
                req: Request[IO],
                body: IO[shopping.backend.models.ShipOrder],
                orderId: Long,
                responses: shipOrderResponses[IO]
            ): IO[Response[IO]] =
                withAdmin(req) { _ =>
                    body.attempt
                        .flatMap {
                            case Right(in)
                                if in.trackingNumber.trim.nonEmpty &&
                                    in.carrier.trim.nonEmpty =>
                                adminOrderService
                                    .ship(
                                      orderId,
                                      in.trackingNumber.trim,
                                      in.carrier.trim,
                                      in.estimatedDeliveryDate
                                    )
                                    .flatMap {
                                        case ShipOutcome.Shipped(order) =>
                                            responses.resp200(
                                              OrderApiDelegateImpl.toApi(order)
                                            )
                                        case ShipOutcome.OrderNotFound =>
                                            responses.resp404()
                                        case ShipOutcome.NotShippable =>
                                            responses.resp409()
                                    }
                            case Right(_) =>
                                responses.resp422()
                            case Left(error) =>
                                logger.warn(
                                  s"shipOrder decode failed: ${error.getMessage}"
                                ) *> responses.resp422()
                        }
                }

    def deliverOrder: deliverOrder =
        new deliverOrder:
            def handle(
                req: Request[IO],
                orderId: Long,
                responses: deliverOrderResponses[IO]
            ): IO[Response[IO]] =
                withAdmin(req) { _ =>
                    adminOrderService
                        .deliver(orderId)
                        .flatMap {
                            case DeliverOutcome.Delivered(order) =>
                                responses
                                    .resp200(OrderApiDelegateImpl.toApi(order))
                            case DeliverOutcome.OrderNotFound =>
                                responses.resp404()
                            case DeliverOutcome.NotDeliverable =>
                                responses.resp409()
                        }
                }

object AdminApiDelegateImpl:
    private def toApi(p: DomainProduct): Product = Product(
      id = p.id,
      productName = p.productName,
      productCategory = p.productCategory,
      priceJpy = p.priceJpy,
      productImageUrl = p.productImageUrl
    )
