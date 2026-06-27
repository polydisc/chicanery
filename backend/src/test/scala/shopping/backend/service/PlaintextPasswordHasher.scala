package shopping.backend.service

import cats.effect.IO

/** Deterministic, fast PasswordHasher for tests (no real bcrypt cost). */
object PlaintextPasswordHasher extends PasswordHasher:
    def hash(plain: String): IO[String] = IO.pure(plain)
    def verify(plain: String, hashed: String): IO[Boolean] = IO
        .pure(plain == hashed)
