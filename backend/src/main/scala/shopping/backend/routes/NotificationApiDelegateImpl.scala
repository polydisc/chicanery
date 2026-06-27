package shopping.backend.routes

import cats.effect.IO
import org.http4s.{Request, Response}
import shopping.backend.apis.NotificationApiDelegate
import shopping.backend.apis.NotificationApiDelegate.*
import shopping.backend.db.models.Notification as DomainNotification
import shopping.backend.models.Notification
import shopping.backend.service.{AuthTokenService, NotificationService}

// In-app notifications (R8). Every endpoint is auth-gated and scoped to the
// authenticated user: the user id comes from the verified token, so a caller
// can only ever see / mutate their own notifications.
final class NotificationApiDelegateImpl(
    notificationService: NotificationService,
    authTokenService: AuthTokenService
) extends NotificationApiDelegate[IO]:

    def listNotifications: listNotifications =
        new listNotifications:
            def handle(
                req: Request[IO],
                responses: listNotificationsResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    notificationService
                        .list(userId)
                        .flatMap(ns =>
                            responses.resp200(
                              ns.map(NotificationApiDelegateImpl.toApi)
                            )
                        )
                }

    def markNotificationRead: markNotificationRead =
        new markNotificationRead:
            def handle(
                req: Request[IO],
                notificationId: Long,
                responses: markNotificationReadResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    notificationService
                        .markRead(notificationId, userId)
                        .flatMap {
                            case true =>
                                responses.resp204()
                            case false =>
                                responses.resp404()
                        }
                }

    def markAllNotificationsRead: markAllNotificationsRead =
        new markAllNotificationsRead:
            def handle(
                req: Request[IO],
                responses: markAllNotificationsReadResponses[IO]
            ): IO[Response[IO]] =
                AuthSupport.withUser(req, authTokenService) { userId =>
                    notificationService.markAllRead(userId) *>
                        responses.resp204()
                }

object NotificationApiDelegateImpl:
    private def toApi(n: DomainNotification): Notification = Notification(
      id = n.id,
      notificationType = n.notificationType,
      message = n.message,
      read = n.read,
      orderId = n.orderId,
      createdAt = Some(n.createdAt.toZonedDateTime)
    )
