package shopping.backend.service

import cats.effect.IO
import java.time.OffsetDateTime
import munit.CatsEffectSuite
import shopping.backend.db.models.Notification
import shopping.backend.repository.NotificationRepository

class NotificationServiceSpec extends CatsEffectSuite:

    private val sample = Notification(
      id = 1L,
      userId = 7L,
      orderId = Some(10L),
      notificationType = "SHIPPED",
      message = "Order #10 shipped.",
      read = false,
      createdAt = OffsetDateTime.parse("2026-06-27T00:00:00Z")
    )

    private def repo(
        listed: List[Notification] = Nil,
        readResult: Boolean = false
    ): NotificationRepository =
        new NotificationRepository:
            def list(userId: Long): IO[List[Notification]] = IO.pure(listed)
            def markRead(notificationId: Long, userId: Long): IO[Boolean] = IO
                .pure(readResult)
            def markAllRead(userId: Long): IO[Int] = IO.pure(listed.length)

    test("list returns the user's notifications"):
        NotificationService(repo(listed = List(sample)))
            .list(7L)
            .assertEquals(List(sample))

    test("markRead returns true when the notification was owned/updated"):
        NotificationService(repo(readResult = true))
            .markRead(1L, 7L)
            .assertEquals(true)

    test("markRead returns false for an unknown/unowned notification"):
        NotificationService(repo(readResult = false))
            .markRead(99L, 7L)
            .assertEquals(false)

    test("markAllRead completes"):
        NotificationService(repo(listed = List(sample)))
            .markAllRead(7L)
            .assertEquals(())
