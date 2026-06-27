package shopping.backend.routes

import cats.effect.IO
import org.http4s.{Request, Response, Status}
import shopping.backend.service.{AuthTokenService, UserService}

// Admin gate: 401 when the token is missing/invalid (via AuthSupport), then 403
// when the authenticated user isn't an admin. Role is read from the DB so a
// revoked admin loses access immediately (the JWT carries only the user id).
object AdminSupport:

    def withAdmin(req: Request[IO], auth: AuthTokenService, users: UserService)(
        f: Long => IO[Response[IO]]
    ): IO[Response[IO]] =
        AuthSupport.withUser(req, auth) { userId =>
            users
                .isAdmin(userId)
                .flatMap {
                    case true =>
                        f(userId)
                    case false =>
                        IO.pure(Response[IO](Status.Forbidden))
                }
        }
