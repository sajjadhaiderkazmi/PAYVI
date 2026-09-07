import net.myjda.payvi.matching.ReceiptFieldParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReceiptFieldParserTest {

    @Test
    fun `parses a typical JazzCash screenshot OCR text`() {
        val text = """
            JazzCash
            Transaction Successful
            Amount: Rs 1,500.00
            To: Ali Raza
            Number: 0300-1234567
            TID: JC98765432
            Date: 07-Sep-2026
        """.trimIndent()

        val fields = ReceiptFieldParser.parse(text)

        assertEquals(1500.0, fields.amountValue)
        assertEquals("Ali Raza", fields.name)
        assertEquals("JC98765432", fields.txnId)
        assertEquals("03001234567", fields.number)
        assertEquals("2026-09-07", fields.dateIso)
    }

    @Test
    fun `parses a typical bank SMS`() {
        val text = "Dear Customer, Rs.1500 has been credited to A/C Title Ali Raza, A/C No 1234567890123456 " +
            "Ref No REF445566 on 07/09/2026. Thank you for banking with us."

        val fields = ReceiptFieldParser.parse(text)

        assertEquals(1500.0, fields.amountValue)
        assertEquals("REF445566", fields.txnId)
        assertEquals("1234567890123456", fields.number)
        assertEquals("2026-09-07", fields.dateIso)
    }

    @Test
    fun `parses EasyPaisa style SMS with amount before Rs suffix`() {
        val text = "You have received 2500/- from 03211234567. Trx ID: EP112233. 07-09-2026"

        val fields = ReceiptFieldParser.parse(text)

        assertEquals(2500.0, fields.amountValue)
        assertEquals("EP112233", fields.txnId)
        assertEquals("03211234567", fields.number)
    }

    @Test
    fun `blank text yields all-null fields without throwing`() {
        val fields = ReceiptFieldParser.parse("")
        assertNull(fields.amountValue)
        assertNull(fields.txnId)
        assertNull(fields.number)
        assertNull(fields.name)
        assertNull(fields.dateIso)
    }

    @Test
    fun `garbage OCR text does not crash and yields no false amount`() {
        val fields = ReceiptFieldParser.parse("asdkj 3839 !!@#\n---random---\n0")
        // "0" is not a valid positive amount, so nothing should match.
        assertNull(fields.amountValue)
    }

    @Test
    fun `comparableNumber normalizes 03xx, 92xx and plus92xx to the same value`() {
        val a = ReceiptFieldParser.comparableNumber("03001234567")
        val b = ReceiptFieldParser.comparableNumber("923001234567")
        val c = ReceiptFieldParser.comparableNumber("+923001234567")
        assertEquals(a, b)
        assertEquals(b, c)
    }

    @Test
    fun `comparableNumber returns null for too-short input`() {
        assertNull(ReceiptFieldParser.comparableNumber("12345"))
        assertNull(ReceiptFieldParser.comparableNumber(null))
        assertNull(ReceiptFieldParser.comparableNumber(""))
    }

    @Test
    fun `amount with comma thousands separator parses correctly`() {
        val fields = ReceiptFieldParser.parse("Amount: Rs 12,750.50 sent successfully")
        assertEquals(12750.50, fields.amountValue)
    }
}
