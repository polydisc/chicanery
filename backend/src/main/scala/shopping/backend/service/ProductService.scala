package shopping.backend.service

import cats.effect.IO
import shopping.backend.db.models.Product
import shopping.backend.repository.ProductRepository

enum ProductInputError:
    case BlankName
    case NegativePrice

final class ProductService(productRepository: ProductRepository):

    def getById(id: Long): IO[Option[Product]] = productRepository.findById(id)

    /** Returns the matching products and the (echoed) page number. */
    def search(
        query: Option[String],
        category: Option[String],
        page: Int
    ): IO[(List[Product], Int)] = productRepository
        .search(query, category, page, ProductService.PageSize)
        .map(products => (products, page))

    def listCategories(): IO[List[String]] = productRepository.listCategories()

    // Admin write side. Inputs are validated (non-blank name, non-negative
    // price); an invalid input yields a typed error the route maps to 422.
    def createProduct(
        name: String,
        category: Option[String],
        priceJpy: Int,
        imageUrl: Option[String]
    ): IO[Either[ProductInputError, Product]] =
        ProductService.validate(name, priceJpy) match
            case Left(e) =>
                IO.pure(Left(e))
            case Right(_) =>
                productRepository
                    .create(name.trim, category, priceJpy, imageUrl)
                    .map(Right(_))

    def updateProduct(
        id: Long,
        name: String,
        category: Option[String],
        priceJpy: Int,
        imageUrl: Option[String]
    ): IO[Either[ProductInputError, Option[Product]]] =
        ProductService.validate(name, priceJpy) match
            case Left(e) =>
                IO.pure(Left(e))
            case Right(_) =>
                productRepository
                    .update(id, name.trim, category, priceJpy, imageUrl)
                    .map(Right(_))

    def deleteProduct(id: Long): IO[ProductRepository.DeleteResult] =
        productRepository.delete(id)

object ProductService:
    val PageSize: Int = 20

    def validate(name: String, priceJpy: Int): Either[ProductInputError, Unit] =
        if name.trim.isEmpty then
            Left(ProductInputError.BlankName)
        else if priceJpy < 0 then
            Left(ProductInputError.NegativePrice)
        else
            Right(())
