package shopping.backend.service

// Supported payment methods (R6). The contract's `PaymentRequest.method` is a
// free-form string at the wire level (the generator doesn't emit an enum), so
// it's parsed/validated here at the boundary.
enum PaymentMethod:
    case Card,
        BankTransfer,
        Cod

object PaymentMethod:
    def parse(s: String): Option[PaymentMethod] =
        s match
            case "card" =>
                Some(Card)
            case "bank_transfer" =>
                Some(BankTransfer)
            case "cod" =>
                Some(Cod)
            case _ =>
                None

enum PaymentValidationError:
    case UnknownMethod
    case MissingCardDetails

final class PaymentService:

    /** Validates a payment request. The method must be one of the supported
      * values; `card` additionally requires non-empty details (the card
      * number). bank_transfer / cod need no details. There is no real gateway —
      * a valid request is simply accepted.
      */
    def validate(
        method: String,
        details: Option[String]
    ): Either[PaymentValidationError, PaymentMethod] =
        PaymentMethod.parse(method) match
            case None =>
                Left(PaymentValidationError.UnknownMethod)
            case Some(PaymentMethod.Card) if details.forall(_.trim.isEmpty) =>
                Left(PaymentValidationError.MissingCardDetails)
            case Some(m) =>
                Right(m)
