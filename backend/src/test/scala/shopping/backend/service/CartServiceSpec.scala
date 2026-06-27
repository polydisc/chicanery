package shopping.backend.service

import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import shopping.backend.db.models.{CartItem, Product}
import shopping.backend.repository.{CartRepository, ProductRepository}

class CartServiceSpec extends CatsEffectSuite:

    private val banana = Product(1L, "Banana", Some("fruit"), 200, None)
    private val owner = 1L
    private val other = 2L

    // In-memory CartRepository keyed by cartId -> (ownerId, items), so ownership
    // is part of the contract being tested.
    private type State = Map[Long, (Long, List[CartItem])]

    private def fakeCartRepo(state: Ref[IO, State]): CartRepository =
        new CartRepository:
            def create(userId: Long): IO[Long] = state.modify { m =>
                val id = m.keySet.maxOption.getOrElse(0L) + 1
                (m + (id -> (userId, Nil)), id)
            }
            def existsForUser(cartId: Long, userId: Long): IO[Boolean] = state
                .get
                .map(_.get(cartId).exists(_._1 == userId))
            def items(cartId: Long): IO[List[CartItem]] = state
                .get
                .map(_.get(cartId).fold(List.empty[CartItem])(_._2))
            def addItem(
                cartId: Long,
                productId: Long,
                quantity: Int,
                unitPriceJpy: Int
            ): IO[Unit] = state.update { m =>
                m.updatedWith(cartId)(
                  _.map { case (uid, current) =>
                      val updated =
                          if current.exists(_.productId == productId) then
                              current.map(ci =>
                                  if ci.productId == productId then
                                      ci.copy(quantity = ci.quantity + quantity)
                                  else
                                      ci
                              )
                          else
                              current :+
                                  CartItem(
                                    productId,
                                    s"product-$productId",
                                    unitPriceJpy,
                                    quantity
                                  )
                      (uid, updated)
                  }
                )
            }
            def setItemQuantity(
                cartId: Long,
                productId: Long,
                quantity: Int
            ): IO[Int] = state.modify { m =>
                m.get(cartId) match
                    case Some((uid, current))
                        if current.exists(_.productId == productId) =>
                        val updated =
                            if quantity <= 0 then
                                current.filterNot(_.productId == productId)
                            else
                                current.map(ci =>
                                    if ci.productId == productId then
                                        ci.copy(quantity = quantity)
                                    else
                                        ci
                                )
                        (m + (cartId -> (uid, updated)), 1)
                    case _ =>
                        (m, 0)
            }

    private def productRepo(products: List[Product]): ProductRepository =
        new ProductRepository:
            def findById(id: Long): IO[Option[Product]] = IO
                .pure(products.find(_.id == id))
            def search(
                q: Option[String],
                category: Option[String],
                p: Int,
                s: Int
            ): IO[List[Product]] = IO.pure(products)
            def listCategories(): IO[List[String]] = IO
                .pure(products.flatMap(_.productCategory).distinct.sorted)
            def create(
                name: String,
                category: Option[String],
                priceJpy: Int,
                imageUrl: Option[String]
            ): IO[Product] = IO
                .pure(Product(0L, name, category, priceJpy, imageUrl))
            def update(
                id: Long,
                name: String,
                category: Option[String],
                priceJpy: Int,
                imageUrl: Option[String]
            ): IO[Option[Product]] = IO.pure(None)
            def delete(id: Long): IO[ProductRepository.DeleteResult] = IO
                .pure(ProductRepository.DeleteResult.NotFound)

    /** Seeds an empty cart with id 1 owned by `owner`. */
    private def withService(run: CartService => IO[Unit]): IO[Unit] = Ref
        .of[IO, State](Map(1L -> (owner, Nil)))
        .flatMap { state =>
            run(CartService(fakeCartRepo(state), productRepo(List(banana))))
        }

    test("create returns a new empty cart for the user"):
        withService { svc =>
            svc.create(owner)
                .map { cart =>
                    assertEquals(cart.items, Nil)
                    assertEquals(cart.totalJpy, 0)
                }
        }

    test("get another user's cart returns CartNotFound (ownership)"):
        withService { svc =>
            svc.get(1L, other).assertEquals(Left(CartError.CartNotFound))
        }

    test("addItem to another user's cart returns CartNotFound (ownership)"):
        withService { svc =>
            svc.addItem(1L, other, 1L, 1)
                .assertEquals(Left(CartError.CartNotFound))
        }

    test("get an unknown cart returns CartNotFound"):
        withService { svc =>
            svc.get(999L, owner).assertEquals(Left(CartError.CartNotFound))
        }

    test("addItem with an unknown product returns ProductNotFound"):
        withService { svc =>
            svc.addItem(1L, owner, 999L, 1)
                .assertEquals(Left(CartError.ProductNotFound))
        }

    test("addItem with a non-positive quantity returns InvalidQuantity"):
        withService { svc =>
            svc.addItem(1L, owner, 1L, 0)
                .assertEquals(Left(CartError.InvalidQuantity))
        }

    test("addItem adds the product and computes the total"):
        withService { svc =>
            svc.addItem(1L, owner, 1L, 2)
                .map {
                    case Right(cart) =>
                        assertEquals(cart.items.map(_.productId), List(1L))
                        assertEquals(cart.totalJpy, 400)
                    case Left(e) =>
                        fail(s"expected a cart, got $e")
                }
        }

    test("addItem twice for the same product increments the quantity"):
        withService { svc =>
            svc.addItem(1L, owner, 1L, 1) *>
                svc.addItem(1L, owner, 1L, 3)
                    .map {
                        case Right(cart) =>
                            assertEquals(cart.items.head.quantity, 4)
                            assertEquals(cart.totalJpy, 800)
                        case Left(e) =>
                            fail(s"expected a cart, got $e")
                    }
        }

    test("updateItem to 0 removes the item"):
        withService { svc =>
            svc.addItem(1L, owner, 1L, 2) *>
                svc.updateItem(1L, owner, 1L, 0)
                    .assertEquals(
                      Right(shopping.backend.db.models.Cart(1L, Nil))
                    )
        }

    test("updateItem on a missing item returns ItemNotFound"):
        withService { svc =>
            svc.updateItem(1L, owner, 1L, 5)
                .assertEquals(Left(CartError.ItemNotFound))
        }

    test("updateItem with a negative quantity returns InvalidQuantity"):
        withService { svc =>
            svc.updateItem(1L, owner, 1L, -1)
                .assertEquals(Left(CartError.InvalidQuantity))
        }
