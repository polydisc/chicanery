package shopping.backend.routes

import munit.FunSuite
import shopping.backend.service.PaymentMethod

// The card number must NEVER be persisted — only the method and last 4 digits.
class PaymentMaskingSpec extends FunSuite:

    private def safe(method: PaymentMethod, details: Option[String]): String =
        OrderApiDelegateImpl.safePaymentDetails(method, details)

    test("card keeps only the last 4 digits"):
        assertEquals(
          safe(PaymentMethod.Card, Some("4111111111111111")),
          "card ending 1111"
        )

    test("card with spaces does not leak the full PAN"):
        val stored = safe(PaymentMethod.Card, Some("4242 4242 4242 4242"))
        assertEquals(stored, "card ending 4242")
        assert(!stored.contains("4242 4242"))

    test("card with hyphens is reduced to the last 4 digits"):
        assertEquals(
          safe(PaymentMethod.Card, Some("4242-4242-4242-1234")),
          "card ending 1234"
        )

    test("card with no usable digits stores just the method"):
        assertEquals(safe(PaymentMethod.Card, Some("n/a")), "card")
        assertEquals(safe(PaymentMethod.Card, None), "card")

    test("bank_transfer and cod carry no details"):
        assertEquals(
          safe(PaymentMethod.BankTransfer, Some("ignored")),
          "bank_transfer"
        )
        assertEquals(safe(PaymentMethod.Cod, None), "cod")
