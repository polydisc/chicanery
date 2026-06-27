package shopping.backend.repository

import cats.effect.IO
import doobie.{Fragment, Fragments, Query0, Transactor, Update0}
import doobie.implicits.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import shopping.backend.db.models.Product

// Product repository (read + admin write side). Interface + Live impl so the
// service layer is testable with a fake. All dynamic SQL goes through
// interpolated bind parameters (`$value`) / Fragments — never string concat.
trait ProductRepository:
    def findById(id: Long): IO[Option[Product]]
    def search(
        query: Option[String],
        category: Option[String],
        page: Int,
        pageSize: Int
    ): IO[List[Product]]
    def listCategories(): IO[List[String]]

    // Admin write side.
    def create(
        name: String,
        category: Option[String],
        priceJpy: Int,
        imageUrl: Option[String]
    ): IO[Product]
    def update(
        id: Long,
        name: String,
        category: Option[String],
        priceJpy: Int,
        imageUrl: Option[String]
    ): IO[Option[Product]]
    def delete(id: Long): IO[ProductRepository.DeleteResult]

object ProductRepository:

    enum DeleteResult:
        case Deleted
        case NotFound
        case InUse // referenced by reviews/orders (FK violation)

    private val selectAll: Fragment =
        fr"""SELECT id, product_name, product_category, price_jpy,
                    product_image_url
             FROM products"""

    // Escape LIKE/ILIKE metacharacters so user input is matched literally.
    // Postgres' default LIKE escape character is the backslash, so no explicit
    // ESCAPE clause is needed (and doobie's `fr` would emit a 2-char '\\' that
    // Postgres rejects as an invalid escape string).
    private def escapeLike(s: String): String = s
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private[repository] def findByIdQuery(id: Long): Query0[Product] =
        (selectAll ++ fr"WHERE id = $id").query[Product]

    private[repository] def searchQuery(
        query: Option[String],
        category: Option[String],
        page: Int,
        pageSize: Int
    ): Query0[Product] =
        // Free-text matches name OR category; the optional category filter is an
        // exact match (browse-by-category).
        val textClause = query.map { q =>
            val like = "%" + escapeLike(q) + "%"
            fr"(product_name ILIKE $like OR product_category ILIKE $like)"
        }
        val categoryClause = category.map(c => fr"product_category = $c")
        val where = Fragments.whereAndOpt(textClause, categoryClause)
        // LIMIT/OFFSET are bigint in Postgres, so bind them as Long.
        val limit = pageSize.max(1).toLong
        val offset = page.max(0).toLong * limit
        (selectAll ++ where ++ fr"ORDER BY id LIMIT $limit OFFSET $offset")
            .query[Product]

    private[repository] def listCategoriesQuery: Query0[String] =
        // product_category is nullable; filter nulls and COALESCE so doobie sees
        // a non-optional String.
        sql"""SELECT DISTINCT COALESCE(product_category, '')
              FROM products
              WHERE product_category IS NOT NULL
              ORDER BY COALESCE(product_category, '')""".query[String]

    private[repository] def insertProductUpdate(
        name: String,
        category: Option[String],
        priceJpy: Int,
        imageUrl: Option[String]
    ): Update0 =
        sql"""INSERT INTO products
                  (product_name, product_category, price_jpy,
                   product_image_url, created_at, updated_at)
              VALUES
                  ($name, $category, $priceJpy, $imageUrl,
                   current_date, current_date)""".update

    private[repository] def updateProductUpdate(
        id: Long,
        name: String,
        category: Option[String],
        priceJpy: Int,
        imageUrl: Option[String]
    ): Update0 =
        sql"""UPDATE products
              SET product_name = $name, product_category = $category,
                  price_jpy = $priceJpy, product_image_url = $imageUrl,
                  updated_at = current_date
              WHERE id = $id""".update

    private[repository] def deleteProductUpdate(id: Long): Update0 =
        sql"DELETE FROM products WHERE id = $id".update

    final class Live(xa: Transactor[IO]) extends ProductRepository:

        def findById(id: Long): IO[Option[Product]] = findByIdQuery(id)
            .option
            .transact(xa)

        def search(
            query: Option[String],
            category: Option[String],
            page: Int,
            pageSize: Int
        ): IO[List[Product]] = searchQuery(query, category, page, pageSize)
            .to[List]
            .transact(xa)

        def listCategories(): IO[List[String]] = listCategoriesQuery
            .to[List]
            .transact(xa)

        def create(
            name: String,
            category: Option[String],
            priceJpy: Int,
            imageUrl: Option[String]
        ): IO[Product] = insertProductUpdate(name, category, priceJpy, imageUrl)
            .withUniqueGeneratedKeys[Long]("id")
            .map(id => Product(id, name, category, priceJpy, imageUrl))
            .transact(xa)

        def update(
            id: Long,
            name: String,
            category: Option[String],
            priceJpy: Int,
            imageUrl: Option[String]
        ): IO[Option[Product]] = updateProductUpdate(
          id,
          name,
          category,
          priceJpy,
          imageUrl
        ).run
            .map(rows =>
                if rows > 0 then
                    Some(Product(id, name, category, priceJpy, imageUrl))
                else
                    None
            )
            .transact(xa)

        def delete(id: Long): IO[DeleteResult] = deleteProductUpdate(id)
            .run
            .attemptSomeSqlState {
                case sqlstate.class23.FOREIGN_KEY_VIOLATION =>
                    ()
            }
            .transact(xa)
            .map {
                case Left(_) =>
                    DeleteResult.InUse
                case Right(0) =>
                    DeleteResult.NotFound
                case Right(rows) =>
                    DeleteResult.Deleted
            }
