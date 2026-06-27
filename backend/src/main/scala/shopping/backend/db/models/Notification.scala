package shopping.backend.db.models

import java.time.OffsetDateTime

// An in-app notification addressed to a user, created whenever one of their
// orders changes state (R8). `notificationType` mirrors the Postgres
// `NotificationType` enum, read as text.
final case class Notification(
    id: Long,
    userId: Long,
    orderId: Option[Long],
    notificationType: String,
    message: String,
    read: Boolean,
    createdAt: OffsetDateTime
)
