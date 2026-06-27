package shopping.backend.service

import munit.FunSuite

class PaymentServiceSpec extends FunSuite:

    private val svc = PaymentService()

    test("card with a card number is valid"):
        assertEquals(
          svc.validate("card", Some("4242 4242 4242 4242")),
          Right(PaymentMethod.Card)
        )

    test("card without details is rejected"):
        assertEquals(
          svc.validate("card", None),
          Left(PaymentValidationError.MissingCardDetails)
        )

    test("card with blank details is rejected"):
        assertEquals(
          svc.validate("card", Some("   ")),
          Left(PaymentValidationError.MissingCardDetails)
        )

    test("bank_transfer needs no details"):
        assertEquals(
          svc.validate("bank_transfer", None),
          Right(PaymentMethod.BankTransfer)
        )

    test("cod needs no details"):
        assertEquals(svc.validate("cod", None), Right(PaymentMethod.Cod))

    test("an unknown method is rejected"):
        assertEquals(
          svc.validate("bitcoin", None),
          Left(PaymentValidationError.UnknownMethod)
        )
