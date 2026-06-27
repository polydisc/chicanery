package shopping.backend.routes

import cats.effect.IO
import org.http4s.{Request, Response}
import org.typelevel.log4cats.Logger
import shopping.backend.apis.AuthApiDelegate
import shopping.backend.apis.AuthApiDelegate.*
import shopping.backend.models.{LoginRequest, RegisterRequest}
import shopping.backend.service.{
    AuthTokenService,
    LoginError,
    RegisterError,
    UserService
}

// Implements the generated contract-first delegate. Paths, request decoding and
// the allowed status codes come from the OpenAPI spec; here we only map service
// results (typed Eithers) onto those responses. On success a signed JWT is
// minted and returned as the access token. A malformed body (the `IO[_]` decode
// failing) is handled explicitly as 400 so it never becomes a 500.
final class AuthApiDelegateImpl(
    userService: UserService,
    authTokenService: AuthTokenService
)(using logger: Logger[IO])
    extends AuthApiDelegate[IO]:

    def login: login =
        new login:
            def handle(
                req: Request[IO],
                body: IO[LoginRequest],
                responses: loginResponses[IO]
            ): IO[Response[IO]] = body
                .attempt
                .flatMap {
                    case Right(request) =>
                        userService
                            .login(request)
                            .flatMap {
                                case Right(userId) =>
                                    authTokenService
                                        .issue(userId)
                                        .flatMap(responses.resp200)
                                case Left(LoginError.InvalidCredentials) =>
                                    logger.warn(
                                      "Login rejected: invalid credentials"
                                    ) *> responses.resp403()
                            }
                    case Left(error) =>
                        logger.warn(
                          s"Login body decode failed: ${error.getMessage}"
                        ) *> responses.resp400()
                }

    def registerUser: registerUser =
        new registerUser:
            def handle(
                req: Request[IO],
                body: IO[RegisterRequest],
                responses: registerUserResponses[IO]
            ): IO[Response[IO]] = body
                .attempt
                .flatMap {
                    case Right(request) =>
                        userService
                            .register(request)
                            .flatMap {
                                case Right(userId) =>
                                    authTokenService
                                        .issue(userId)
                                        .flatMap(responses.resp200)
                                case Left(
                                      RegisterError.EmailAlreadyRegistered
                                    ) =>
                                    responses.resp409()
                            }
                    case Left(error) =>
                        logger.warn(
                          s"Register body decode failed: ${error.getMessage}"
                        ) *> responses.resp400()
                }

    def refreshAccessToken: refreshAccessToken =
        new refreshAccessToken:
            def handle(
                req: Request[IO],
                responses: refreshAccessTokenResponses[IO]
            ): IO[Response[IO]] =
                // Re-issue a fresh token for a caller presenting a valid one
                // (AuthSupport.withUser -> 401 if missing/invalid).
                AuthSupport.withUser(req, authTokenService) { userId =>
                    authTokenService.issue(userId).flatMap(responses.resp200)
                }
