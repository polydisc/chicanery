# Requirements

The features the site set out to support, and where each one stands. For the
implementation details (endpoints, migrations, known limits) see
[STATUS.md](STATUS.md).

| #   | Requirement | Status |
| --- | --- | --- |
| R1  | Authenticated users; guests can browse | Done |
| R2  | Buy products; search by name or category | Done |
| R3  | Read and submit reviews and ratings | Done |
| R4  | Add / remove / modify cart items; checkout | Done |
| R5  | Provide a shipping address at order time | Done |
| R6  | Pay by card, bank transfer, or cash on delivery | Done — methods validated; refund recorded on cancel. No real payment gateway. |
| R7  | Cancel an order before it ships | Done |
| R8  | Notify the user on order status change | Done — in-app notifications. No email/SMS/push. |
| R9  | Shipment tracking and estimated delivery | Done — admin enters tracking number, carrier, and ETA. Not wired to a real carrier. |
| R10 | Admin: manage products and block users | Done — plus role-based access control. |

## Notes on the partial items

- **R6 — payment.** There is no real payment gateway; this is a local project.
  The method (card / bank transfer / cash on delivery) is validated server-side
  and recorded, and cancelling a paid order flips the payment to `REFUNDED`, but
  no money moves.
- **R8 — notifications.** Notifications are written in-app, in the same database
  transaction as the order state change, and shown in the SPA (a bell with an
  unread count and a notifications page). There is no external delivery channel.
- **R9 — shipment tracking.** The tracking number, carrier, and estimated
  delivery date are entered by an admin when marking an order shipped; they are
  not fetched from a carrier API.

These reflect the scope of a local portfolio build, not gaps in the flow: each
requirement works end to end within the running app.
