# CLAUDE.md — Backend (Scala 3, typelevel stack)

This file guides Claude Code when writing **functional Scala** in `backend/`. It is
auto-loaded for backend work and **overrides** generic habits. Read the root
[CLAUDE.md](../CLAUDE.md) for build/run/test commands and the cake-pattern overview.

Stack: **Scala 3.5 · Cats Effect 3 (`IO`) · http4s 0.23 (Ember) · Doobie 1.0-RC5 ·
circe · Flyway · PureConfig · MUnit**. This is the **typelevel** stack — *not* Akka.
Do not introduce `Future`, actors, or Akka/Pekko idioms; they conflict with everything below.

> If a snippet here conflicts with a generic "best practice" you'd otherwise reach for,
> this file wins. When unsure, prefer the more functional option and ask.

---

## 0. The prime directive — `IO` is a *description*, not a running program

An `IO[A]` is a **value that describes** an effect. Nothing runs until the
**"end of the world"**: `IOApp.run` (see [Main.scala](src/main/scala/shopping/backend/Main.scala)).
`val p = IO.println("hi"); p; p` prints *nothing* — effects are values you compose.

Almost every FP mistake below is a violation of this one rule. Build a description;
thread `IO` through the whole call graph; run it **once**, at the edge.

❌ **Never** run effects in the middle of logic:
```scala
val user = userService.login(req).unsafeRunSync()   // ❌ breaks referential transparency
```
✅ Compose and return `IO`; let `Main` run it:
```scala
def handle(req: LoginRequest): IO[Response[IO]] =
    userService.login(req).flatMap(toResponse)       // ✅ still a description
```

`.unsafeRunSync()` / `.unsafeRunAsync` are allowed **only** in a non-`IOApp` script
entrypoint or rare interop — never in services, repos, routes, or constructors.

---

## 1. Effect discipline (Cats Effect 3)

**Suspend side effects — pick the right constructor:**
- `IO(...)` / `IO.delay(...)` — synchronous, non-blocking side effects.
- `IO.blocking(...)` — **thread-blocking** calls (JDBC, file IO, legacy sync clients).
  Shifts to the blocking pool so the compute pool isn't starved.
- `IO.interruptible(...)` — long blocking calls you want cancelable.
- `IO.pure(x)` — **only** for already-computed values. `x` is evaluated eagerly, so
  `IO.pure(println(...))` is a bug. Use `IO.delay` for anything effectful.

```scala
val contents: IO[String] = IO.blocking(scala.io.Source.fromFile(p).mkString)  // ✅ not IO(...)
```

**Lifecycle → `Resource`.** Anything with a finalizer (DB pool, http client, file)
is a `Resource`; it releases safely, in reverse order, even on failure.
```scala
val xa: Resource[IO, Transactor[IO]] = HikariTransactor.fromHikariConfig[IO](cfg)
xa.use(transactor => program.transact(transactor))   // ✅ acquired once, released for you
```
❌ Don't hand-roll `try/finally`, call `.close()`, or build a transactor inside a method.

**Compose with combinators, not loops.**
- Sequential: `flatMap`/`map` or a `for`-comprehension.
- Over a collection: `list.traverse(f)` (sequential) / `list.parTraverse(f)` (concurrent),
  `sequence` / `parSequence`.
- Independent values: `(ioA, ioB).parMapN(f)` / `mapN`.
```scala
import cats.syntax.all.*
def loadAll(ids: List[UserId]): IO[List[User]] = ids.traverse(repo.find)   // ✅ List[IO]->IO[List]
```

**Shared mutable state → `Ref`/`AtomicCell`, never `var`/`synchronized`.**
```scala
counter.update(_ + 1)                 // ✅ atomic
state.modify(s => (s.next, s.value))  // ✅ atomic read-modify-write
```

**Concurrency:** use `parTraverse`, `parMapN`, `IO.race`, `.background`, `Supervisor`,
and `Semaphore` for limits. Never `new Thread`, thread pools by hand, or `Future`.

**Errors in `IO`:** `IO` is a `MonadThrow`. Recover with `handleErrorWith`/`recoverWith`,
inspect with `attempt: IO[Either[Throwable, A]]`, raise with `IO.raiseError(e)`.
Run finalizers with `guarantee`/`bracket`/`onError`/`onCancel`. **Cancelation is not an
error** — don't try to `handleErrorWith` it; use `onCancel`/`guarantee`.
❌ Never `throw` inside `map`/`flatMap` — return `IO.raiseError`.

**Time:** `IO.sleep(d)` (frees the thread) — never `Thread.sleep`.

**Typeclasses (pragmatic):** deep components may abstract over the narrowest constraint
(`F[_]: Sync` / `: Temporal` / `: Async`) and pick `IO` at the edge — but concrete `IO`
is perfectly fine for an end app. Don't add `F[_]` everywhere as ceremony (see §6.3).

---

## 2. Imperative → functional (kill the OOP/Java reflexes)

| ❌ Don't | ✅ Do |
|---------|------|
| `var`, `while`, `do/while` | `val`, recursion (`@tailrec`), `foldLeft`, `traverse` |
| `return` | the last expression *is* the value; `if`/`match` are expressions |
| `ListBuffer`/`ArrayBuffer`/`mutable.Map` accumulators | `map`/`collect`/`groupBy`/`foldLeft`/`foldMap` |
| index loops `for (i <- 0 until xs.size)` | `xs.map` / `xs.zipWithIndex` / `xs.collect` |
| Java getters/setters, mutable POJOs | `case class` + `.copy` |
| inheritance hierarchies for data variants | `enum` / sealed ADT + typeclasses |
| Scala-2 `implicit val`/`def` | Scala-3 `given` / `using` |
| value classes (`extends AnyVal`) | `opaque type` |

```scala
val total = items.foldLeft(BigDecimal(0))(_ + _.price)          // ✅ not a var + while
val byRole = users.filter(_.active).groupBy(_.role)             // ✅
val label  = if score >= 60 then "pass" else "fail"            // ✅ expression
```

Keep functions **small, pure, and total** (defined for all inputs, no hidden effects).
Push all effects (DB, HTTP, clock, random, logging) to the edges in `IO`. Define
typeclass instances with `given` and require them with context bounds (`def f[A: Show]`).

---

## 3. Error handling & data modeling

**Errors are values. Illegal states are unrepresentable. Parse at the boundary.**

### 3.1 Model errors as a concrete ADT — never a wildcard or `Throwable`
❌ `IO[Either[_, User]]`, `Either[Throwable, A]`, `Either[String, A]` — all erase the
error and defeat exhaustiveness. Model a concrete error ADT instead:
```scala
enum UserError:
    case NotFound(id: UserId)
    case InvalidCredentials
    case DuplicateEmail(email: Email)

def login(req: LoginRequest): IO[Either[UserError, User]] = ...   // ✅ concrete, exhaustive
```

### 3.2 `Option`: never `.get` / `.head`
Each `.get`/`.head` is a hidden `throw`. This repo currently has
`loginRequest.username.get.asString.get` — **fix it** by turning absence into a typed error:
```scala
val username: Either[UserError, String] =
    for
        raw  <- loginRequest.username.toRight(UserError.InvalidCredentials)  // Option -> Either
        name <- raw.asString.toRight(UserError.InvalidCredentials)
    yield name
```
Toolbox: `fold`, `getOrElse`, `toRight(err)`, `map`/`flatMap`, pattern match, `headOption`.

### 3.3 `Either` / `EitherT` for fail-fast effectful flows
`Either[E, A]` short-circuits on the first `Left`. To chain **effectful** fallible steps
without nesting `IO[Either[...]]` by hand, use `EitherT`, then `.value` at the edge:
```scala
def register(in: SignupForm): EitherT[IO, UserError, User] =
    for
        email <- EitherT.fromEither[IO](Email.parse(in.email))
        _     <- EitherT(repo.findByEmail(email).map(_.toLeft(()).leftMap(_ => UserError.DuplicateEmail(email))))
        user  <- EitherT.liftF(repo.insert(email))
    yield user
// val out: IO[Either[UserError, User]] = register(form).value
```
Pick: `IO[Either[E,A]]` for a few steps; `EitherT` for many; the `IO` error channel
(`raiseError`) for **unexpected/infrastructural** failures (DB down, timeout).

### 3.4 `Validated` to accumulate (e.g. form validation)
`Either` is fail-fast; `ValidatedNel` accumulates *all* errors (it's `Applicative`, not `Monad`):
```scala
import cats.syntax.all.*
(Email.parseV(f.email), Name.parseV(f.name), Age.parseV(f.age)).mapN(User.apply)  // ✅ collects all
```
Don't `for`-comprehend `Validated` expecting accumulation — it has no lawful `flatMap`.

### 3.5 ADTs, exhaustive matches, opaque IDs
Model domains with `enum`/`sealed trait`; match exhaustively and let the compiler enforce it.
❌ Don't add `case _ =>` to a sealed match — it silences the check that catches new cases.
Use `opaque type` + a smart constructor for typed IDs so an unvalidated value can't exist:
```scala
opaque type Email = String
object Email:
    def parse(s: String): Either[UserError, Email] =
        if s.matches(".+@.+\\..+") then Right(s) else Left(UserError.InvalidCredentials)
    extension (e: Email) def value: String = e
```

### 3.6 Parse, don't validate
Convert untrusted input (JSON body, query param, DB row) into typed domain values
**once, at the boundary**. The core only ever holds valid values — never re-checks.

---

## 4. Doobie

**Build `ConnectionIO`, compose, `.transact(xa)` once.** A `ConnectionIO` is a
description; `.transact` runs **one transaction**. Statements that must be atomic go in
**one** for-comprehension before transacting.
```scala
val transfer: ConnectionIO[Unit] =
    for
        _ <- debit(from, amount)
        _ <- credit(to, amount)
    yield ()
transfer.transact(xa)   // ✅ one atomic transaction
```
❌ Don't `.transact` per query / inside a loop (breaks atomicity). ❌ Don't `.unsafeRunSync` per query.

**SQL injection safety:** interpolate every value with `${...}` — it becomes a bound
parameter, not text. **Never** build SQL by string concatenation.
```scala
sql"select id, name from users where email = $email".query[User].option   // ✅ bound
// ❌ NEVER: Fragment.const(s"... where email = '$email'")
```
Dynamic SQL → `Fragments` helpers (`whereAndOpt`, `in`, `set`), still parameterized.
`fr"#$x"` / `Fragment.const` splice **raw** text — only for *trusted* identifiers, never user data.

**Result accessors by cardinality:** `.option` (0/1), `.unique` (exactly 1, else throws),
`.to[List]` (all), `.nel` (1+), `.stream` (constant-memory cursor). Use `.option` when a
row may be absent — never `.unique`.

**Writes:** `.update.run` (rows affected); `.withUniqueGeneratedKeys[T](cols...)` for
inserts (don't re-`SELECT`); `Update[I](sql).updateMany(xs)` for batches (don't loop inserts).

**Transactor:** `HikariTransactor.fromHikariConfig[IO](cfg)` as a `Resource`, built once in
`Main`. `Transactor.fromDriverManager` is for scripts/tests only.

**Map rows → domain types inside the repo.** Repos return `IO[Option[User]]`, not raw tuples.

**Error mapping:** `attemptSomeSqlState { case sqlstate.class23.UNIQUE_VIOLATION => ... }`
to turn SQL errors into your error ADT. Don't swallow with `.attempt.void`.

---

## 5. http4s (0.23)

**Routes:** `HttpRoutes.of[IO]` + `Http4sDsl[IO]`; match method then path. Use typed
extractors (`IntVar`, `UUIDVar`) and matchers (`QueryParamDecoderMatcher`,
`OptionalQueryParamDecoderMatcher`, `ValidatingQueryParamDecoderMatcher`). Don't build
`Response` imperatively — use status constructors (`Ok`, `BadRequest`, ...).

**circe codecs:** derive **semiauto** `given`s in the companion; never `io.circe.generic.auto._`
(slow compiles, surprising structural matches).
```scala
final case class User(id: UUID, name: String)
object User:
    given Encoder[User] = deriveEncoder
    given Decoder[User] = deriveDecoder
```
Bridge with `jsonOf[IO, T]` / `jsonEncoderOf[IO, T]`, or `import org.http4s.circe.CirceEntityCodec.*`.

**Map the error ADT → status codes.** ❌ Don't collapse everything into
`case _ => NotFound()` — it hides the cause and returns the wrong code.
```scala
def toResponse(e: UserError): IO[Response[IO]] = e match
    case UserError.NotFound(_)          => NotFound(ErrorBody("not found").asJson)
    case UserError.InvalidCredentials   => Forbidden(ErrorBody("bad creds").asJson)
    case UserError.DuplicateEmail(_)    => Conflict(ErrorBody("exists").asJson)        // 409
// route: service.login(req).flatMap(_.fold(toResponse, u => Ok(u.asJson)))
```
Status guide: `400` malformed/missing input · `422` valid-syntax-but-invalid · `404`
genuinely absent · `409` conflict · `500` *unexpected* only. Never `200` for a failure.

**Decode failures:** `req.as[T]` raises `MessageFailure` on bad input — recover to a 4xx,
don't let it 500. Don't `throw`/`unsafeRunSync`/`.get` in handlers. Wrap unavoidable
blocking in `IO.blocking`.

**Compose & mount:** one `*Routes` object per resource exposing `routes: HttpRoutes[IO]`;
combine with `<+>` (needs `cats.syntax.all.*`); mount with `Router`; call `.orNotFound`
**once** at the top — never per file. Build the Ember **client** once as a `Resource`.

---

## 6. Architecture & testing

### 6.1 Layering — strict boundaries
`Routes (HTTP) → Service (domain logic) → Repository (DB)`.
- http4s types (`Request`/`Response`/`EntityDecoder`) stay in **Routes**.
- Doobie types (`ConnectionIO`/`Fragment`/`Read`) stay in **Repository**; repos return
  **domain types**.
- Services hold pure domain logic, return `IO[A]` (raising via `MonadThrow`) or
  `EitherT[IO, DomainError, A]`. No SQL, no JSON, no `Status` in services.

### 6.2 Dependency wiring
This repo uses the **cake pattern** (`*Component` traits + self-types, wired in `Main`).
- **Keep it consistent** where it lives: each layer a `trait XComponent`, dependencies
  via self-types one layer down, construction in `Main` — no side-effecting `val`s in
  component bodies, no reaching across layers.
- **For new modules, prefer plain constructor injection** (the modern CE3 idiom — Volpe,
  *Practical FP in Scala*): deps as constructor params, graph assembled once in `Main`
  inside a `Resource`. It's lower-ceremony and far easier to test.
```scala
final class LiveUserService(repo: UserRepo) extends UserService:
    def login(req: LoginRequest): IO[Either[UserError, User]] = ...
```
Don't rewrite working cake layers wholesale; migrate opportunistically.
❌ Never: global `object` singletons with side effects, hardcoded config/credentials
(load via PureConfig and inject), or a transactor instantiated per method/request.

### 6.3 Tagless final — opt-in, not default
`trait UserRepo[F[_]]` helps when you need a second interpreter or genuine
`F`-polymorphism. For an app that only runs on `IO`, a plain `trait UserRepo` returning
`IO` is clearer — `Sync[F].delay` everywhere is ceremony, not abstraction.

### 6.4 Testing (MUnit + munit-cats-effect)
Tests return `IO[Unit]` — **never `unsafeRunSync` in tests**.
```scala
class UserSuite extends munit.CatsEffectSuite:
    test("login succeeds"):
        assertIO(svc.login(valid).map(_.isRight), true)
```
- Compose multiple assertions with `*>` or a for-comprehension (otherwise only the last runs).
- DB resources via `ResourceFunFixture(transactorResource)` (verify the fixture name
  against the munit-cats-effect version on the classpath).
- **Doobie query typechecking:** `doobie-munit` `IOChecker` — `check(theQuery)` validates
  SQL arity/nullability/types against a real test DB without executing.
- **http4s routes:** `routes.orNotFound.run(Request[IO](GET, uri"/...")).map(r => assertEquals(r.status, Ok))`.
- **Inject fakes via the constructor** (e.g. a `UserRepo` backed by `Ref[IO, Map[...]]`) —
  don't reach for Mockito when a stub instance works.
- Property tests: `munit-scalacheck` + `forAll` on pure functions.

---

## 7. Forbidden patterns — quick ❌ checklist

Scan generated code for these before finishing:
- `.unsafeRunSync()` / `.unsafeRunAsync` in logic, services, repos, routes, tests
- `Future`, `Await.result`, Akka/Pekko anything
- `Thread.sleep` (use `IO.sleep`), `new Thread`, `synchronized`
- `throw` / `try`/`catch` for control flow (use `raiseError` / `attempt`)
- `.get` on `Option`, `.head`/`.tail` on possibly-empty lists, `.asInstanceOf`
- `Either[_, A]` / `Either[Throwable, A]` / `Either[String, A]`, stringly-typed errors
- `null`, `var`, `while`, `return`, mutable collections as accumulators
- `case _ =>` swallowing cases in a sealed/`enum` match
- string-concatenated SQL; `.transact` per query/in a loop; `.unique` where a row may be absent
- blanket `case _ => NotFound()`; `200` for failures; unhandled `req.as[T]` decode errors
- `io.circe.generic.auto._`; building `Response` imperatively
- `object` singletons with side effects; transactor built per method; hardcoded credentials
- Scala-2 `implicit val`/`def`; value classes for newtypes; speculative `F[_]`/typeclass abstraction

---

## Authoritative references
- Cats Effect 3: <https://typelevel.org/cats-effect/> · Cats: <https://typelevel.org/cats/>
- http4s 0.23: <https://http4s.org/v0.23/docs/> · Doobie: <https://typelevel.org/doobie/>
- Scala 3 book/reference: <https://docs.scala-lang.org/scala3/>
- Gabriel Volpe, *Practical FP in Scala* (canonical CE3 app structure): <https://leanpub.com/pfp-scala>
- "Parse, don't validate" — Alexis King: <https://lexi-lambda.github.io/blog/2019/11/05/parse-don-t-validate/>
- munit-cats-effect: <https://typelevel.org/munit-cats-effect/>
