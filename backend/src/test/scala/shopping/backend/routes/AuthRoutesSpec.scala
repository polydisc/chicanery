package shopping.backend.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shopping.backend.apis.AuthApiRoutes
import shopping.backend.db.models.User
import shopping.backend.models.{LoginRequest, RegisterRequest}
import shopping.backend.repository.UserRepository
import shopping.backend.service.{
    AuthTokenService,
    PlaintextPasswordHasher,
    UserService
}

class AuthRoutesSpec extends CatsEffectSuite:

    private given Logger[IO] = Slf4jLogger.getLogger[IO]
    private val auth = AuthTokenService.Live("test-secret", 3600)

    private val seededUser = User(
      userId = 7L,
      password = "secret",
      accessToken = "tok-123"
    )

    private def app(users: Map[String, User]): HttpApp[IO] =
        val service = UserService(
          new UserRepository:
              def findByEmail(emailAddress: String): IO[Option[User]] = IO
                  .pure(users.get(emailAddress))
              def insert(
                  request: RegisterRequest,
                  passwordHash: String,
                  accessToken: String
              ): IO[Option[Long]] = IO.pure(Some(7L))
              def findRole(userId: Long): IO[Option[String]] = IO.pure(None)
              def setState(userId: Long, state: String): IO[Boolean] = IO
                  .pure(false)
          ,
          PlaintextPasswordHasher
        )
        AuthApiRoutes(AuthApiDelegateImpl(service, auth)).routes.orNotFound

    test("POST /login returns 200 and a JWT that resolves to the user id"):
        val request = Request[IO](Method.POST, uri"/login")
            .withEntity(LoginRequest("john@example.com", "secret"))
        app(Map("john@example.com" -> seededUser))
            .run(request)
            .flatMap { resp =>
                assertEquals(resp.status, Status.Ok)
                resp.as[String].flatMap(auth.verify).assertEquals(Some(7L))
            }

    test("POST /login returns 403 for an invalid password"):
        val request = Request[IO](Method.POST, uri"/login")
            .withEntity(LoginRequest("john@example.com", "wrong"))
        app(Map("john@example.com" -> seededUser))
            .run(request)
            .map { resp =>
                assertEquals(resp.status, Status.Forbidden)
            }

    test("POST /login returns 400 for a malformed body (no 500)"):
        val request = Request[IO](Method.POST, uri"/login")
            .withEntity("not json")
        app(Map.empty)
            .run(request)
            .map { resp =>
                assertEquals(resp.status, Status.BadRequest)
            }

    test("GET /login (refresh) without a token returns 401"):
        app(Map.empty)
            .run(Request[IO](Method.GET, uri"/login"))
            .map { resp =>
                assertEquals(resp.status, Status.Unauthorized)
            }

    test("GET /login (refresh) with a valid token re-issues a token"):
        auth.issue(7L)
            .flatMap { token =>
                val req = Request[IO](Method.GET, uri"/login").putHeaders(
                  Authorization(Credentials.Token(AuthScheme.Bearer, token))
                )
                app(Map.empty)
                    .run(req)
                    .flatMap { resp =>
                        assertEquals(resp.status, Status.Ok)
                        resp.as[String]
                            .flatMap(auth.verify)
                            .assertEquals(Some(7L))
                    }
            }
