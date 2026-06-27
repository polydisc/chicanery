package shopping.backend.repository

import cats.effect.IO
import cats.syntax.all.*
import doobie.{ConnectionIO, Query0, Transactor, Update0}
import doobie.implicits.*
import shopping.backend.db.models.CartItem

// Cart persistence. Cart line items are rows in `line_item` with order_id NULL;
// they get an order_id (and the cart rows are deleted) when an order is placed.
trait CartRepository:
    def create(userId: Long): IO[Long]

    /** True only if the cart exists and is owned by `userId` (ownership guard
      * for every cart operation — non-owners are indistinguishable from "not
      * found").
      */
    def existsForUser(cartId: Long, userId: Long): IO[Boolean]
    def items(cartId: Long): IO[List[CartItem]]
    def addItem(
        cartId: Long,
        productId: Long,
        quantity: Int,
        unitPriceJpy: Int
    ): IO[Unit]

    /** Sets the quantity (<= 0 removes the row). Returns rows affected; 0 means
      * the item was not in the cart.
      */
    def setItemQuantity(cartId: Long, productId: Long, quantity: Int): IO[Int]

object CartRepository:

    private[repository] def selectItemsQuery(cartId: Long): Query0[CartItem] =
        sql"""SELECT p.id, COALESCE(p.product_name, ''), li.price, li.quantity
              FROM line_item li
              JOIN products p ON li.product_id = p.id
              WHERE li.cart_id = $cartId AND li.order_id IS NULL
              ORDER BY p.id""".query[CartItem]

    private[repository] def selectItems(
        cartId: Long
    ): ConnectionIO[List[CartItem]] = selectItemsQuery(cartId).to[List]

    private[repository] def createUpdate(userId: Long): Update0 =
        sql"INSERT INTO carts (user_id, session_id) VALUES ($userId, NULL)"
            .update

    private[repository] def existsForUserQuery(
        cartId: Long,
        userId: Long
    ): Query0[Int] =
        sql"SELECT 1 FROM carts WHERE id = $cartId AND user_id = $userId"
            .query[Int]

    private[repository] def addItemUpdate(
        cartId: Long,
        productId: Long,
        quantity: Int,
        unitPriceJpy: Int
    ): Update0 =
        sql"""INSERT INTO line_item
              (cart_id, product_id, order_id, price, quantity)
              VALUES ($cartId, $productId, NULL, $unitPriceJpy, $quantity)
              ON CONFLICT (cart_id, product_id) WHERE order_id IS NULL
              DO UPDATE SET
                  quantity = line_item.quantity + EXCLUDED.quantity""".update

    private[repository] def setQuantityUpdate(
        cartId: Long,
        productId: Long,
        quantity: Int
    ): Update0 =
        if quantity <= 0 then
            sql"""DELETE FROM line_item
                  WHERE cart_id = $cartId AND product_id = $productId
                    AND order_id IS NULL""".update
        else
            sql"""UPDATE line_item SET quantity = $quantity
                  WHERE cart_id = $cartId AND product_id = $productId
                    AND order_id IS NULL""".update

    final class Live(xa: Transactor[IO]) extends CartRepository:

        def create(userId: Long): IO[Long] = createUpdate(userId)
            .withUniqueGeneratedKeys[Long]("id")
            .transact(xa)

        def existsForUser(cartId: Long, userId: Long): IO[Boolean] =
            existsForUserQuery(cartId, userId)
                .option
                .map(_.isDefined)
                .transact(xa)

        def items(cartId: Long): IO[List[CartItem]] = selectItems(cartId)
            .transact(xa)

        // Atomic upsert (no check-then-insert race), relying on the partial
        // unique index line_item_cart_product_uniq (V3 migration).
        def addItem(
            cartId: Long,
            productId: Long,
            quantity: Int,
            unitPriceJpy: Int
        ): IO[Unit] = addItemUpdate(cartId, productId, quantity, unitPriceJpy)
            .run
            .void
            .transact(xa)

        def setItemQuantity(
            cartId: Long,
            productId: Long,
            quantity: Int
        ): IO[Int] = setQuantityUpdate(cartId, productId, quantity)
            .run
            .transact(xa)
