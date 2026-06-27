package shopping.backend.service

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all.*
import shopping.backend.db.models.Cart
import shopping.backend.repository.{CartRepository, ProductRepository}

enum CartError:
    case CartNotFound
    case ProductNotFound
    case ItemNotFound
    case InvalidQuantity

// Every operation is scoped to `userId`: a cart owned by someone else is
// indistinguishable from a missing cart (CartNotFound -> 404).
final class CartService(
    cartRepository: CartRepository,
    productRepository: ProductRepository
):

    def create(userId: Long): IO[Cart] = cartRepository
        .create(userId)
        .map(id => Cart(id, Nil))

    def get(cartId: Long, userId: Long): IO[Either[CartError, Cart]] =
        ensureCart(cartId, userId).semiflatMap(_ => loadCart(cartId)).value

    def addItem(
        cartId: Long,
        userId: Long,
        productId: Long,
        quantity: Int
    ): IO[Either[CartError, Cart]] =
        (
          for
              _ <- EitherT.cond[IO](quantity > 0, (), CartError.InvalidQuantity)
              _ <- ensureCart(cartId, userId)
              product <- EitherT.fromOptionF(
                productRepository.findById(productId),
                CartError.ProductNotFound
              )
              _ <- EitherT.liftF(
                cartRepository
                    .addItem(cartId, productId, quantity, product.priceJpy)
              )
              cart <- EitherT.liftF(loadCart(cartId))
          yield cart
        ).value

    def updateItem(
        cartId: Long,
        userId: Long,
        productId: Long,
        quantity: Int
    ): IO[Either[CartError, Cart]] =
        (
          for
              _ <- EitherT
                  .cond[IO](quantity >= 0, (), CartError.InvalidQuantity)
              _ <- ensureCart(cartId, userId)
              affected <- EitherT.liftF(
                cartRepository.setItemQuantity(cartId, productId, quantity)
              )
              _ <- EitherT.cond[IO](affected > 0, (), CartError.ItemNotFound)
              cart <- EitherT.liftF(loadCart(cartId))
          yield cart
        ).value

    private def ensureCart(
        cartId: Long,
        userId: Long
    ): EitherT[IO, CartError, Unit] = EitherT(
      cartRepository
          .existsForUser(cartId, userId)
          .map(Either.cond(_, (), CartError.CartNotFound))
    )

    private def loadCart(cartId: Long): IO[Cart] = cartRepository
        .items(cartId)
        .map(items => Cart(cartId, items))
