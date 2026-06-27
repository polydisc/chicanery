package shopping.backend.service

import cats.effect.IO
import munit.CatsEffectSuite
import shopping.backend.db.models.Product
import shopping.backend.repository.ProductRepository
import shopping.backend.repository.ProductRepository.DeleteResult

class ProductServiceSpec extends CatsEffectSuite:

    private val banana = Product(1L, "Banana", Some("fruit"), 200, None)
    private val cola = Product(2L, "Cola", Some("drink"), 150, None)

    private def service(products: List[Product]): ProductService =
        ProductService(
          new ProductRepository:
              def findById(id: Long): IO[Option[Product]] = IO
                  .pure(products.find(_.id == id))
              def search(
                  query: Option[String],
                  category: Option[String],
                  page: Int,
                  pageSize: Int
              ): IO[List[Product]] = IO.pure(
                category.fold(products)(c =>
                    products.filter(_.productCategory.contains(c))
                )
              )
              def listCategories(): IO[List[String]] = IO
                  .pure(products.flatMap(_.productCategory).distinct.sorted)
              def create(
                  name: String,
                  category: Option[String],
                  priceJpy: Int,
                  imageUrl: Option[String]
              ): IO[Product] = IO
                  .pure(Product(99L, name, category, priceJpy, imageUrl))
              def update(
                  id: Long,
                  name: String,
                  category: Option[String],
                  priceJpy: Int,
                  imageUrl: Option[String]
              ): IO[Option[Product]] = IO.pure(
                Option.when(products.exists(_.id == id))(
                  Product(id, name, category, priceJpy, imageUrl)
                )
              )
              def delete(id: Long): IO[DeleteResult] = IO.pure(
                if products.exists(_.id == id) then
                    DeleteResult.Deleted
                else
                    DeleteResult.NotFound
              )
        )

    test("getById returns the product when present"):
        assertIO(service(List(banana)).getById(1L), Some(banana))

    test("getById returns None when absent"):
        assertIO(service(List(banana)).getById(99L), None)

    test("search returns the products and echoes the page number"):
        assertIO(service(List(banana)).search(None, None, 2), (List(banana), 2))

    test("search filters by category"):
        assertIO(
          service(List(banana, cola)).search(None, Some("drink"), 0),
          (List(cola), 0)
        )

    test("listCategories returns the distinct sorted categories"):
        assertIO(
          service(List(banana, cola)).listCategories(),
          List("drink", "fruit")
        )

    test("createProduct returns the persisted product"):
        assertIO(
          service(Nil).createProduct("Mango", Some("fruit"), 300, None),
          Right(Product(99L, "Mango", Some("fruit"), 300, None))
        )

    test("createProduct rejects a blank name"):
        assertIO(
          service(Nil).createProduct("   ", Some("fruit"), 300, None),
          Left(ProductInputError.BlankName)
        )

    test("createProduct rejects a negative price"):
        assertIO(
          service(Nil).createProduct("Mango", None, -1, None),
          Left(ProductInputError.NegativePrice)
        )

    test("updateProduct returns the updated product, or None if absent"):
        service(List(banana))
            .updateProduct(1L, "Banana+", Some("fruit"), 250, None)
            .assertEquals(
              Right(Some(Product(1L, "Banana+", Some("fruit"), 250, None)))
            ) *>
            service(List(banana))
                .updateProduct(99L, "x", None, 1, None)
                .assertEquals(Right(None))

    test("updateProduct rejects invalid input"):
        service(List(banana))
            .updateProduct(1L, "", None, 1, None)
            .assertEquals(Left(ProductInputError.BlankName))

    test("deleteProduct reports Deleted vs NotFound"):
        service(List(banana))
            .deleteProduct(1L)
            .assertEquals(DeleteResult.Deleted) *>
            service(List(banana))
                .deleteProduct(99L)
                .assertEquals(DeleteResult.NotFound)
