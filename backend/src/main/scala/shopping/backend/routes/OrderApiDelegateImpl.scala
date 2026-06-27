package shopping.backend.routes

import cats.effect.IO
import org.http4s.{Request, Response}
import org.typelevel.log4cats.Logger
import shopping.backend.apis.OrderApiDelegate
import shopping.backend.apis.OrderApiDelegate.*
import shopping.backend.db.models.Order as DomainOrder
import shopping.backend.models.{Order, OrderItem}
import shopping.backend.repository.OrderRepository.{
    CancelOutcome,
    PaymentOutcome
}
import shopping.backend.service.{
    AuthTokenService,
    OrderError,
    OrderService,
    PaymentMethod,
    PaymentService
}

// All order endpoints are auth-gated and scoped to the authenticated user. The
// order owner comes from the verified token, never from the request body/query,
// so orders can't be forged or read across users.
final class OrderApiDelegateImpl(
    orderService: OrderService,
    paymentService: PaymentService,
    authTokenService: AuthTokenService
)(using logger: Logger[IO])
    extends OrderApiDelegate[IO]:

    def createOrder: createOrder =
        new createOrder:
            def handle(
                req: Request[IO],
                body: IO[shopping.backend.models.CreateOrder],
                responses: createOrderResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    body.attempt
                        .flatMap {
                            case Right(request) =>
                                orderService
                                    .create(
                                      request.cartId,
                                      userId,
                                      request.shippingAddress
                                    )
                                    .flatMap {
                                        case Right(order) =>
                                            responses.resp200(
                                              OrderApiDelegateImpl.toApi(order)
                                            )
                                        case Left(OrderError.CartNotFound) =>
                                            responses.resp404()
                                        case Left(OrderError.EmptyCart) =>
                                            responses.resp422()
                                    }
                            case Left(error) =>
                                logger.warn(
                                  s"createOrder decode failed: ${error.getMessage}"
                                ) *> responses.resp422()
                        }
                }

    def getOrder: getOrder =
        new getOrder:
            def handle(
                req: Request[IO],
                orderId: Long,
                responses: getOrderResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    orderService
                        .get(orderId, userId)
                        .flatMap {
                            case Some(order) =>
                                responses
                                    .resp200(OrderApiDelegateImpl.toApi(order))
                            case None =>
                                responses.resp404()
                        }
                }

    def listOrders: listOrders =
        new listOrders:
            def handle(
                req: Request[IO],
                responses: listOrdersResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    orderService
                        .list(userId)
                        .flatMap(orders =>
                            responses
                                .resp200(orders.map(OrderApiDelegateImpl.toApi))
                        )
                }

    def payOrder: payOrder =
        new payOrder:
            def handle(
                req: Request[IO],
                body: IO[shopping.backend.models.PaymentRequest],
                orderId: Long,
                responses: payOrderResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    body.attempt
                        .flatMap {
                            case Right(payment) =>
                                paymentService.validate(
                                  payment.method,
                                  payment.details
                                ) match
                                    case Left(_) =>
                                        responses.resp422()
                                    case Right(method) =>
                                        val details = OrderApiDelegateImpl
                                            .safePaymentDetails(
                                              method,
                                              payment.details
                                            )
                                        orderService
                                            .pay(orderId, userId, details)
                                            .flatMap {
                                                case PaymentOutcome
                                                        .Paid(order) =>
                                                    responses.resp200(
                                                      OrderApiDelegateImpl
                                                          .toApi(order)
                                                    )
                                                case PaymentOutcome
                                                        .OrderNotFound =>
                                                    responses.resp404()
                                                case PaymentOutcome
                                                        .NotPayable =>
                                                    responses.resp409()
                                            }
                            case Left(error) =>
                                logger.warn(
                                  s"payOrder decode failed: ${error.getMessage}"
                                ) *> responses.resp422()
                        }
                }

    def cancelOrder: cancelOrder =
        new cancelOrder:
            def handle(
                req: Request[IO],
                orderId: Long,
                responses: cancelOrderResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    orderService
                        .cancel(orderId, userId)
                        .flatMap {
                            case CancelOutcome.Cancelled(order) =>
                                responses
                                    .resp200(OrderApiDelegateImpl.toApi(order))
                            case CancelOutcome.OrderNotFound =>
                                responses.resp404()
                            case CancelOutcome.NotCancellable =>
                                responses.resp409()
                        }
                }

object OrderApiDelegateImpl:

    // Build the payment detail string that gets PERSISTED. We never store the
    // card number — only the method and, for cards, the last 4 digits (computed
    // from the digits alone, so spaced/hyphenated input like "4242 4242 4242
    // 4242" can't leak a full PAN). bank_transfer / cod carry no details.
    private[routes] def safePaymentDetails(
        method: PaymentMethod,
        rawDetails: Option[String]
    ): String =
        method match
            case PaymentMethod.Card =>
                val digits = rawDetails.getOrElse("").filter(_.isDigit)
                if digits.length >= 4 then
                    s"card ending ${digits.takeRight(4)}"
                else
                    "card"
            case PaymentMethod.BankTransfer =>
                "bank_transfer"
            case PaymentMethod.Cod =>
                "cod"

    private[routes] def toApi(o: DomainOrder): Order = Order(
      id = o.id,
      items = o
          .items
          .map(oi =>
              OrderItem(
                productId = oi.productId,
                productName = oi.productName,
                unitPriceJpy = oi.unitPriceJpy,
                quantity = oi.quantity,
                lineTotalJpy = oi.lineTotalJpy
              )
          ),
      totalJpy = o.totalJpy,
      shippingAddress = o.shippingAddress,
      status = o.status,
      orderDate = o.orderDate,
      trackingNumber = o.trackingNumber,
      carrier = o.carrier,
      shippedDate = o.shippedDate,
      estimatedDeliveryDate = o.estimatedDeliveryDate
    )
