package shopping.backend.routes

import cats.effect.IO
import org.http4s.{AuthScheme, Credentials, Request, Response, Status}
import org.http4s.headers.Authorization
import shopping.backend.service.AuthTokenService

// Shared helper for the auth-gated delegates: extract a Bearer token, verify it,
// and run the handler with the authenticated user id — otherwise 401. The
// generated `responses` objects don't expose 401 (it isn't a per-operation
// business outcome), so the Unauthorized response is built directly.
object AuthSupport:

    def withUser(req: Request[IO], auth: AuthTokenService)(
        f: Long => IO[Response[IO]]
    ): IO[Response[IO]] =
        bearerToken(req) match
            case Some(token) =>
                auth.verify(token)
                    .flatMap {
                        case Some(userId) =>
                            f(userId)
                        case None =>
                            IO.pure(Response[IO](Status.Unauthorized))
                    }
            case None =>
                IO.pure(Response[IO](Status.Unauthorized))

    private def bearerToken(req: Request[IO]): Option[String] = req
        .headers
        .get[Authorization]
        .collect {
            case Authorization(Credentials.Token(AuthScheme.Bearer, token)) =>
                token
        }
