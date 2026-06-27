package shopping.backend.repository

import cats.effect.IO
import doobie.{Query0, Transactor, Update0}
import doobie.implicits.*
import doobie.postgres.implicits.*
import shopping.backend.db.models.Notification

trait NotificationRepository:
    /** The user's notifications, unread first then newest first. */
    def list(userId: Long): IO[List[Notification]]

    /** Marks one notification read; false if it doesn't exist or isn't owned by
      * the user (ownership guard — a non-owner is indistinguishable from
      * absent).
      */
    def markRead(notificationId: Long, userId: Long): IO[Boolean]

    /** Marks all the user's notifications read; returns the number updated. */
    def markAllRead(userId: Long): IO[Int]

object NotificationRepository:

    private[repository] def listQuery(userId: Long): Query0[Notification] =
        sql"""SELECT id, user_id, order_id, type::text, message, read, created_at
              FROM notifications
              WHERE user_id = $userId
              ORDER BY read ASC, created_at DESC, id DESC""".query[Notification]

    private[repository] def markReadUpdate(
        notificationId: Long,
        userId: Long
    ): Update0 =
        sql"""UPDATE notifications SET read = true
              WHERE id = $notificationId AND user_id = $userId""".update

    private[repository] def markAllReadUpdate(userId: Long): Update0 =
        sql"""UPDATE notifications SET read = true
              WHERE user_id = $userId AND read = false""".update

    // Insert helper used by OrderRepository INSIDE its order transactions, so a
    // status change and its notification commit atomically. `type` is the
    // Postgres NotificationType enum, written from text via a cast.
    private[repository] def insertNotificationUpdate(
        userId: Long,
        orderId: Option[Long],
        notificationType: String,
        message: String
    ): Update0 =
        sql"""INSERT INTO notifications (user_id, order_id, type, message)
              VALUES ($userId, $orderId, $notificationType::NotificationType,
                      $message)""".update

    final class Live(xa: Transactor[IO]) extends NotificationRepository:
        def list(userId: Long): IO[List[Notification]] = listQuery(userId)
            .to[List]
            .transact(xa)

        def markRead(notificationId: Long, userId: Long): IO[Boolean] =
            markReadUpdate(notificationId, userId).run.map(_ > 0).transact(xa)

        def markAllRead(userId: Long): IO[Int] = markAllReadUpdate(userId)
            .run
            .transact(xa)
