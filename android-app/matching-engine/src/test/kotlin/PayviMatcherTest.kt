import net.myjda.payvi.matching.OrderContext
import net.myjda.payvi.matching.PayviMatcher
import net.myjda.payvi.matching.PayviStatus
import net.myjda.payvi.matching.SmsCandidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PayviMatcherTest {

    private val baseOrder = OrderContext(
        amount = 1500.0,
        billingPhone = "03001234567",
        billingName = "Ali Raza",
        orderDateMillis = 1_757_000_000_000L // arbitrary fixed instant for reproducible tests
    )

    private fun smsAt(order: OrderContext, offsetMillis: Long, body: String) = SmsCandidate(
        id = "sms-1",
        sender = "JazzCash",
        body = body,
        timestampMillis = (order.orderDateMillis ?: 0L) + offsetMillis
    )

    @Test
    fun `duplicate flag from server always wins regardless of SMS`() {
        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = true,
            screenshotText = "Amount: Rs 1500 TID: JC1 Number: 03001234567",
            order = baseOrder,
            smsMessages = listOf(
                smsAt(baseOrder, 1000, "Rs 1500 received. TID: JC1. From 03001234567")
            )
        )
        assertEquals(PayviStatus.DUPLICATE, result.status)
    }

    @Test
    fun `amount plus transaction id match yields COMPLETED`() {
        val screenshot = "JazzCash\nAmount: Rs 1,500.00\nTID: JC98765432\nNumber: 03001234567"
        val sms = smsAt(baseOrder, 60_000, "You received Rs 1500 from 03001234567. TID JC98765432")

        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = screenshot,
            order = baseOrder,
            smsMessages = listOf(sms)
        )

        assertEquals(PayviStatus.COMPLETED, result.status)
        assertEquals(sms, result.matchedSms)
    }

    @Test
    fun `amount only match with nothing else yields NOT_SURE`() {
        val screenshot = "Amount: Rs 1,500.00 TID: JC98765432 Number: 03001234567"
        // Same amount, but a different sender/number and no transaction id in the SMS.
        val sms = smsAt(baseOrder, 60_000, "Rs 1500 debited from your account.")

        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = screenshot,
            order = baseOrder,
            smsMessages = listOf(sms)
        )

        assertEquals(PayviStatus.NOT_SURE, result.status)
    }

    @Test
    fun `no candidate SMS at all yields NOT_RECEIVED`() {
        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = "Amount: Rs 1500 TID: JC1",
            order = baseOrder,
            smsMessages = emptyList()
        )
        assertEquals(PayviStatus.NOT_RECEIVED, result.status)
    }

    @Test
    fun `SMS present but completely unrelated yields NOT_RECEIVED`() {
        val sms = smsAt(baseOrder, 60_000, "Your OTP is 445566. Do not share it with anyone.")
        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = "Amount: Rs 1500 TID: JC1 Number: 03001234567",
            order = baseOrder,
            smsMessages = listOf(sms)
        )
        assertEquals(PayviStatus.NOT_RECEIVED, result.status)
    }

    @Test
    fun `SMS outside the date window is ignored even if it would otherwise match`() {
        // 10 days after the order - well outside the default +3 day window.
        val sms = smsAt(baseOrder, 10L * 24 * 60 * 60 * 1000, "Rs 1500 received. TID JC98765432 from 03001234567")
        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = "Amount: Rs 1,500.00 TID: JC98765432 Number: 03001234567",
            order = baseOrder,
            smsMessages = listOf(sms)
        )
        assertEquals(PayviStatus.NOT_RECEIVED, result.status)
    }

    @Test
    fun `amount cross-checked against order total when screenshot OCR misses amount`() {
        // Screenshot OCR failed to find an amount (blurry image), but the
        // transaction id and number line up, and the SMS amount matches the
        // order's own total.
        val screenshot = "JazzCash Payment TID: JC98765432 Number: 03001234567"
        val sms = smsAt(baseOrder, 1000, "Rs 1500 received. TID JC98765432 from 03001234567")

        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = screenshot,
            order = baseOrder,
            smsMessages = listOf(sms)
        )

        assertEquals(PayviStatus.COMPLETED, result.status)
        assert(result.matchedFields.containsKey("amount"))
    }

    @Test
    fun `best of multiple candidate SMS is chosen, not just the first`() {
        val weakSms = smsAt(baseOrder, 1000, "Rs 1500 debited.")
        val strongSms = smsAt(baseOrder, 2000, "Rs 1500 received. TID JC98765432 from 03001234567")

        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = "Amount: Rs 1,500.00 TID: JC98765432 Number: 03001234567",
            order = baseOrder,
            smsMessages = listOf(weakSms, strongSms)
        )

        assertEquals(PayviStatus.COMPLETED, result.status)
        assertEquals(strongSms, result.matchedSms)
    }

    @Test
    fun `different amount in SMS does not count as a match`() {
        val sms = smsAt(baseOrder, 1000, "Rs 500 received. TID JC98765432 from 03001234567")
        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = "Amount: Rs 1,500.00 TID: JC98765432 Number: 03001234567",
            order = baseOrder,
            smsMessages = listOf(sms)
        )
        // txn id + number still match (2 signals) even though amount differs,
        // so this is still a strong (COMPLETED) match on the other two fields.
        assertEquals(PayviStatus.COMPLETED, result.status)
        assert(!result.matchedFields.containsKey("amount"))
    }
}
