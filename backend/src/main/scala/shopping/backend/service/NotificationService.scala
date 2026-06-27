package shopping.backend.service

import cats.effect.IO
import shopping.backend.db.models.Notification
import shopping.backend.repository.NotificationRepository

// Read-side of in-app notifications (R8). Notifications are *written* inside the
// order transactions (see OrderRepository); this service only lists them and
// marks them read, scoped to the authenticated user.
final class NotificationService(repository: NotificationRepository):

    def list(userId: Long): IO[List[Notification]] = repository.list(userId)

    def markRead(notificationId: Long, userId: Long): IO[Boolean] = repository
        .markRead(notificationId, userId)

    def markAllRead(userId: Long): IO[Unit] =
        repository.markAllRead(userId).void
