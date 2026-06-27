package shopping.backend.routes

import cats.effect.IO
import java.time.OffsetDateTime
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import shopping.backend.apis.NotificationApiRoutes
import shopping.backend.db.models.Notification
import shopping.backend.repository.NotificationRepository
import shopping.backend.service.{AuthTokenService, NotificationService}

class NotificationRoutesSpec extends CatsEffectSuite:

    private val auth = AuthTokenService.Live("test-secret", 3600)

    private val sample = Notification(
      id = 1L,
      userId = 7L,
      orderId = Some(10L),
      notificationType = "SHIPPED",
      message = "Order #10 shipped.",
      read = false,
      createdAt = OffsetDateTime.parse("2026-06-27T00:00:00Z")
    )

    // markRead returns true only for notification id 1 (the "owned" one).
    private val repo =
        new NotificationRepository:
            def list(userId: Long): IO[List[Notification]] = IO
                .pure(List(sample))
            def markRead(notificationId: Long, userId: Long): IO[Boolean] = IO
                .pure(notificationId == 1L)
            def markAllRead(userId: Long): IO[Int] = IO.pure(1)

    private val app =
        NotificationApiRoutes(
          NotificationApiDelegateImpl(NotificationService(repo), auth)
        ).routes.orNotFound

    private def withToken(req: Request[IO], userId: Long): IO[Request[IO]] =
        auth.issue(userId)
            .map(t =>
                req.putHeaders(
                  Authorization(Credentials.Token(AuthScheme.Bearer, t))
                )
            )

    test("GET /notifications without a token returns 401"):
        app.run(Request[IO](Method.GET, uri"/notifications"))
            .map(r => assertEquals(r.status, Status.Unauthorized))

    test("GET /notifications with a token returns 200"):
        withToken(Request[IO](Method.GET, uri"/notifications"), 7L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.Ok))

    test("POST /notifications/1/read marks read (204)"):
        withToken(Request[IO](Method.POST, uri"/notifications/1/read"), 7L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.NoContent))

    test("POST /notifications/99/read for an unowned id returns 404"):
        withToken(Request[IO](Method.POST, uri"/notifications/99/read"), 7L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.NotFound))

    test("POST /notifications/read-all returns 204"):
        withToken(Request[IO](Method.POST, uri"/notifications/read-all"), 7L)
            .flatMap(app.run)
            .map(r => assertEquals(r.status, Status.NoContent))

    test("read-all without a token returns 401"):
        app.run(Request[IO](Method.POST, uri"/notifications/read-all"))
            .map(r => assertEquals(r.status, Status.Unauthorized))
