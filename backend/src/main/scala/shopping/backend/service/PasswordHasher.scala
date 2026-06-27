package shopping.backend.service

import cats.effect.IO
import org.mindrot.jbcrypt.BCrypt

// Password hashing as an interface so services can inject a fast deterministic
// fake in tests. bcrypt is intentionally CPU-heavy, so the calls are suspended
// with IO.blocking (they would otherwise starve the compute pool).
trait PasswordHasher:
    def hash(plain: String): IO[String]
    def verify(plain: String, hashed: String): IO[Boolean]

object PasswordHasher:

    final class BCryptHasher(logRounds: Int = 12) extends PasswordHasher:
        def hash(plain: String): IO[String] = IO
            .blocking(BCrypt.hashpw(plain, BCrypt.gensalt(logRounds)))

        def verify(plain: String, hashed: String): IO[Boolean] = IO
            .blocking(BCrypt.checkpw(plain, hashed))
