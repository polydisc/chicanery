package shopping.backend.service

import cats.effect.IO
import java.util.UUID
import shopping.backend.models.{LoginRequest, RegisterRequest}
import shopping.backend.repository.UserRepository

// Per-operation domain errors modelled as values (never thrown). Splitting them
// keeps each route's error mapping exhaustive with no impossible cases.
enum LoginError:
    case InvalidCredentials

enum RegisterError:
    case EmailAlreadyRegistered

final class UserService(
    userRepository: UserRepository,
    passwordHasher: PasswordHasher
):

    // Both return the authenticated user id; the route layer mints the JWT.
    def login(request: LoginRequest): IO[Either[LoginError, Long]] =
        userRepository
            .findByEmail(request.username)
            .flatMap {
                // A blocked account can't authenticate. Return the generic
                // InvalidCredentials (no distinct "blocked" signal) to avoid
                // leaking account state. Note: existing JWTs stay valid until
                // they expire (stateless tokens).
                case Some(user) if user.state == "BLOCKED" =>
                    IO.pure(Left(LoginError.InvalidCredentials))
                case Some(user) =>
                    passwordHasher
                        .verify(request.password, user.password)
                        .map {
                            case true =>
                                Right(user.userId)
                            case false =>
                                Left(LoginError.InvalidCredentials)
                        }
                case None =>
                    IO.pure(Left(LoginError.InvalidCredentials))
            }

    def isAdmin(userId: Long): IO[Boolean] = userRepository
        .findRole(userId)
        .map(_.contains("ADMIN"))

    /** Block a user. Admins can't be blocked — that prevents locking every
      * admin out (including self-blocking). NotFound for an unknown user.
      */
    def block(userId: Long): IO[UserService.BlockOutcome] = userRepository
        .findRole(userId)
        .flatMap {
            case None =>
                IO.pure(UserService.BlockOutcome.NotFound)
            case Some("ADMIN") =>
                IO.pure(UserService.BlockOutcome.CannotBlockAdmin)
            case Some(_) =>
                userRepository
                    .setState(userId, "BLOCKED")
                    .map(ok =>
                        if ok then
                            UserService.BlockOutcome.Done
                        else
                            UserService.BlockOutcome.NotFound
                    )
        }

    /** Unblock a user. Returns false if no such user. */
    def unblock(userId: Long): IO[Boolean] = userRepository
        .setState(userId, "ACTIVE")

    def register(request: RegisterRequest): IO[Either[RegisterError, Long]] =
        // Single atomic insert; uniqueness is enforced by the DB constraint, so
        // there is no check-then-insert race. The access_token column is a
        // vestigial pre-JWT field; we still populate it (NOT NULL).
        for
            accessToken <- IO(UUID.randomUUID().toString)
            passwordHash <- passwordHasher.hash(request.password)
            inserted <- userRepository
                .insert(request, passwordHash, accessToken)
        yield inserted.toRight(RegisterError.EmailAlreadyRegistered)

object UserService:
    enum BlockOutcome:
        case Done
        case NotFound
        case CannotBlockAdmin
