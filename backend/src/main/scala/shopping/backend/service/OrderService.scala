package shopping.backend.service

import cats.effect.IO
import shopping.backend.db.models.Order
import shopping.backend.repository.OrderRepository
import shopping.backend.repository.OrderRepository.CreateOutcome

enum OrderError:
    case CartNotFound
    case EmptyCart

final class OrderService(orderRepository: OrderRepository):

    // Ownership and emptiness are decided inside the repository's single locked
    // checkout transaction (see OrderRepository.createFromCart); we just map the
    // outcome to the typed error.
    def create(
        cartId: Long,
        userId: Long,
        shippingAddress: String
    ): IO[Either[OrderError, Order]] = orderRepository
        .createFromCart(cartId, userId, shippingAddress)
        .map {
            case CreateOutcome.CartNotFound =>
                Left(OrderError.CartNotFound)
            case CreateOutcome.EmptyCart =>
                Left(OrderError.EmptyCart)
            case CreateOutcome.Created(o) =>
                Right(o)
        }

    def get(orderId: Long, userId: Long): IO[Option[Order]] = orderRepository
        .findById(orderId, userId)

    def list(userId: Long): IO[List[Order]] = orderRepository.list(userId)

    def pay(
        orderId: Long,
        userId: Long,
        details: String
    ): IO[OrderRepository.PaymentOutcome] = orderRepository
        .pay(orderId, userId, details)

    def cancel(orderId: Long, userId: Long): IO[OrderRepository.CancelOutcome] =
        orderRepository.cancel(orderId, userId)
