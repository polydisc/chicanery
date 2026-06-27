package shopping.backend.routes

import cats.effect.IO
import org.http4s.{Request, Response}
import shopping.backend.apis.ProductApiDelegate
import shopping.backend.apis.ProductApiDelegate.*
import shopping.backend.db.models.Product as DomainProduct
import shopping.backend.models.{Product, ProductList}
import shopping.backend.service.ProductService

final class ProductApiDelegateImpl(productService: ProductService)
    extends ProductApiDelegate[IO]:

    def getProductById: getProductById =
        new getProductById:
            def handle(
                req: Request[IO],
                productId: Long,
                responses: getProductByIdResponses[IO]
            ): IO[Response[IO]] = productService
                .getById(productId)
                .flatMap {
                    case Some(product) =>
                        responses.resp200(ProductApiDelegateImpl.toApi(product))
                    case None =>
                        responses.resp404()
                }

    def searchProducts: searchProducts =
        new searchProducts:
            def handle(
                req: Request[IO],
                query: Option[String],
                category: Option[String],
                page: Option[Int],
                responses: searchProductsResponses[IO]
            ): IO[Response[IO]] = productService
                .search(query, category, page.getOrElse(0).max(0))
                .flatMap { case (products, pageNumber) =>
                    responses.resp200(
                      ProductList(
                        products.map(ProductApiDelegateImpl.toApi),
                        pageNumber
                      )
                    )
                }

    def listCategories: listCategories =
        new listCategories:
            def handle(
                req: Request[IO],
                responses: listCategoriesResponses[IO]
            ): IO[Response[IO]] = productService
                .listCategories()
                .flatMap(responses.resp200)

object ProductApiDelegateImpl:
    // Map the domain row to the generated API model at the HTTP boundary.
    private def toApi(p: DomainProduct): Product = Product(
      id = p.id,
      productName = p.productName,
      productCategory = p.productCategory,
      priceJpy = p.priceJpy,
      productImageUrl = p.productImageUrl
    )
