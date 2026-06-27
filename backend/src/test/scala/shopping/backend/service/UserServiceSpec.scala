package shopping.backend.service

import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import shopping.backend.db.models.User
import shopping.backend.models.{LoginRequest, RegisterRequest}
import shopping.backend.repository.UserRepository

class UserServiceSpec extends CatsEffectSuite:

    // With the plaintext test hasher, the stored "password" is the plaintext.
    private val seededUser = User(
      userId = 1L,
      password = "secret",
      accessToken = "tok-123"
    )

    /** Login-only fake: `insert` is never exercised. */
    private def loginService(users: Map[String, User]): UserService =
        UserService(
          new UserRepository:
              def findByEmail(emailAddress: String): IO[Option[User]] = IO
                  .pure(users.get(emailAddress))
              def insert(
                  request: RegisterRequest,
                  passwordHash: String,
                  accessToken: String
              ): IO[Option[Long]] = IO.pure(Some(1L))
              def findRole(userId: Long): IO[Option[String]] = IO.pure(None)
              def setState(userId: Long, state: String): IO[Boolean] = IO
                  .pure(false)
          ,
          PlaintextPasswordHasher
        )

    /** Fake backed by a role map + a recorded set of state changes. */
    private def adminService(
        roles: Map[Long, String],
        existingUsers: Set[Long],
        changes: Ref[IO, List[(Long, String)]]
    ): UserService = UserService(
      new UserRepository:
          def findByEmail(emailAddress: String): IO[Option[User]] = IO
              .pure(None)
          def insert(
              request: RegisterRequest,
              passwordHash: String,
              accessToken: String
          ): IO[Option[Long]] = IO.pure(None)
          def findRole(userId: Long): IO[Option[String]] = IO
              .pure(roles.get(userId))
          def setState(userId: Long, state: String): IO[Boolean] =
              if existingUsers.contains(userId) then
                  changes.update(_ :+ (userId, state)).as(true)
              else
                  IO.pure(false)
      ,
      PlaintextPasswordHasher
    )

    /** Register fake: counts inserts and refuses already-known emails,
      * simulating the `users.email_address` UNIQUE constraint.
      */
    private def registerService(
        existingEmails: Set[String],
        inserts: Ref[IO, Int]
    ): UserService = UserService(
      new UserRepository:
          def findByEmail(emailAddress: String): IO[Option[User]] = IO
              .pure(None)
          def insert(
              request: RegisterRequest,
              passwordHash: String,
              accessToken: String
          ): IO[Option[Long]] =
              if existingEmails.contains(request.emailAddress) then
                  IO.pure(None)
              else
                  inserts.update(_ + 1).as(Some(42L))
          def findRole(userId: Long): IO[Option[String]] = IO.pure(None)
          def setState(userId: Long, state: String): IO[Boolean] = IO
              .pure(false)
      ,
      PlaintextPasswordHasher
    )

    private val newUser = RegisterRequest(
      "new@example.com",
      "pw",
      "Jane",
      "Doe",
      None,
      None
    )

    test("login returns the user id for valid credentials"):
        assertIO(
          loginService(Map("john@example.com" -> seededUser))
              .login(LoginRequest("john@example.com", "secret")),
          Right(1L)
        )

    test("login rejects a wrong password"):
        assertIO(
          loginService(Map("john@example.com" -> seededUser))
              .login(LoginRequest("john@example.com", "wrong")),
          Left(LoginError.InvalidCredentials)
        )

    test("login rejects an unknown email"):
        assertIO(
          loginService(Map.empty)
              .login(LoginRequest("nobody@example.com", "secret")),
          Left(LoginError.InvalidCredentials)
        )

    test("login rejects a blocked account even with the right password"):
        val blocked = seededUser.copy(state = "BLOCKED")
        assertIO(
          loginService(Map("john@example.com" -> blocked))
              .login(LoginRequest("john@example.com", "secret")),
          Left(LoginError.InvalidCredentials)
        )

    test("isAdmin is true only for ADMIN-role users"):
        Ref.of[IO, List[(Long, String)]](Nil)
            .flatMap { changes =>
                val svc = adminService(
                  Map(1L -> "ADMIN", 2L -> "USER"),
                  Set(),
                  changes
                )
                svc.isAdmin(1L).assertEquals(true) *>
                    svc.isAdmin(2L).assertEquals(false) *>
                    svc.isAdmin(3L).assertEquals(false) // unknown -> not admin
            }

    test("block/unblock records state and reports unknown + admin-protection"):
        Ref.of[IO, List[(Long, String)]](Nil)
            .flatMap { changes =>
                // user 2 is a USER, user 1 is an ADMIN; both exist.
                val svc = adminService(
                  Map(1L -> "ADMIN", 2L -> "USER"),
                  Set(1L, 2L),
                  changes
                )
                for
                    blocked <- svc.block(2L)
                    unblocked <- svc.unblock(2L)
                    missing <- svc.block(99L)
                    admin <- svc.block(1L) // must be refused
                    recorded <- changes.get
                    _ = assertEquals(blocked, UserService.BlockOutcome.Done)
                    _ = assertEquals(unblocked, true)
                    _ = assertEquals(missing, UserService.BlockOutcome.NotFound)
                    _ = assertEquals(
                      admin,
                      UserService.BlockOutcome.CannotBlockAdmin
                    )
                    _ = assertEquals(
                      recorded,
                      List((2L, "BLOCKED"), (2L, "ACTIVE"))
                    )
                yield ()
            }

    test("register inserts a new user and returns its id"):
        Ref.of[IO, Int](0)
            .flatMap { inserts =>
                registerService(Set.empty, inserts)
                    .register(newUser)
                    .flatMap {
                        case Right(id) =>
                            inserts
                                .get
                                .map { count =>
                                    assertEquals(id, 42L)
                                    assertEquals(count, 1)
                                }
                        case Left(error) =>
                            IO(fail(s"expected a user id, got $error"))
                    }
            }

    test("register rejects an already-registered email"):
        Ref.of[IO, Int](0)
            .flatMap { inserts =>
                val request = newUser.copy(emailAddress = "john@example.com")
                assertIO(
                  registerService(Set("john@example.com"), inserts)
                      .register(request),
                  Left(RegisterError.EmailAlreadyRegistered)
                )
            }
