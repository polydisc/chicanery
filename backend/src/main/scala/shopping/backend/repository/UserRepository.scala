package shopping.backend.repository

import cats.effect.IO
import doobie.{Query0, Transactor, Update0}
import doobie.implicits.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import shopping.backend.db.models.User
import shopping.backend.models.RegisterRequest

// Repository as an interface so services can be tested with in-memory fakes
// (see UserServiceSpec). The Live implementation holds a single, already-built
// Transactor[IO] and transacts each composed ConnectionIO once, at this edge.
trait UserRepository:
    def findByEmail(emailAddress: String): IO[Option[User]]

    /** Inserts a new user with an already-hashed password, returning the new
      * user id. Returns None if the email is already registered (relies on the
      * `users.email_address` UNIQUE constraint, so the check is atomic — no
      * check-then-insert race).
      */
    def insert(
        request: RegisterRequest,
        passwordHash: String,
        accessToken: String
    ): IO[Option[Long]]

    /** The user's role ("USER"/"ADMIN"), or None if the user doesn't exist. */
    def findRole(userId: Long): IO[Option[String]]

    /** Sets the user's account state; returns false if no such user. */
    def setState(userId: Long, state: String): IO[Boolean]

// Queries are defined as named Query0/Update0 values (used by Live and checked
// against the real schema by QueryCheckSpec via doobie's analysis).
object UserRepository:

    private[repository] def findByEmailQuery(
        emailAddress: String
    ): Query0[User] =
        sql"""SELECT id, password, access_token,
                     COALESCE(state::text, 'ACTIVE')
              FROM users
              WHERE email_address = $emailAddress""".query[User]

    private[repository] def findRoleQuery(userId: Long): Query0[String] =
        sql"""SELECT COALESCE(role::text, 'USER')
              FROM users WHERE id = $userId""".query[String]

    private[repository] def setStateUpdate(
        userId: Long,
        state: String
    ): Update0 =
        sql"""UPDATE users SET state = $state::UserState,
                               updated_at = current_date
              WHERE id = $userId""".update

    private[repository] def insertUpdate(
        request: RegisterRequest,
        passwordHash: String,
        accessToken: String
    ): Update0 =
        sql"""INSERT INTO users
              (state, first_name, last_name, email_address, password,
               postal_code, address, access_token, created_at, updated_at)
              VALUES
              ('ACTIVE', ${request.firstName}, ${request.lastName},
               ${request.emailAddress}, $passwordHash,
               ${request.postalCode}, ${request.address}, $accessToken,
               current_date, current_date)""".update

    final class Live(xa: Transactor[IO]) extends UserRepository:

        def findByEmail(emailAddress: String): IO[Option[User]] =
            findByEmailQuery(emailAddress).option.transact(xa)

        def insert(
            request: RegisterRequest,
            passwordHash: String,
            accessToken: String
        ): IO[Option[Long]] = insertUpdate(request, passwordHash, accessToken)
            .withUniqueGeneratedKeys[Long]("id")
            .attemptSomeSqlState { case sqlstate.class23.UNIQUE_VIOLATION =>
                ()
            }
            .transact(xa)
            .map(_.toOption)

        def findRole(userId: Long): IO[Option[String]] = findRoleQuery(userId)
            .option
            .transact(xa)

        def setState(userId: Long, state: String): IO[Boolean] = setStateUpdate(
          userId,
          state
        ).run.map(_ > 0).transact(xa)
