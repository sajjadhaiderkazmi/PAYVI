package net.myjda.payvi.matching

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Best-effort field extraction from free-form payment text - either OCR'd
 * from a payment screenshot, or the body of an SMS. Pakistani mobile
 * wallet (JazzCash/EasyPaisa) and bank SMS/receipts don't follow one fixed
 * format, so every extractor here is a prioritized list of patterns; the
 * first one that matches wins. A field that isn't found is left null
 * rather than guessed - a wrong guess is worse than "unknown" because the
 * matcher downstream treats "unknown" safely (contributes to "Not Sure"
 * instead of a false "Completed").
 */
object ReceiptFieldParser {

    private val AMOUNT_PATTERNS = listOf(
        Regex("""(?:rs\.?|pkr|rupees)\s*[:\-]?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""amount\s*[:\-]?\s*(?:rs\.?|pkr)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|pkr|/-)""", RegexOption.IGNORE_CASE)
    )

    // The "id/no/#" suffix is required (not optional) on the trx/txn/
    // transaction and ref/reference patterns below - otherwise a plain
    // sentence like "Transaction Successful" would greedily match
    // "Transaction" + the next word as if it were a transaction id.
    private val TXN_ID_PATTERNS = listOf(
        Regex("""(?:trx|txn|transaction)\s*(?:id|no\.?|#)\s*[:\-]?\s*([A-Za-z0-9]{5,20})""", RegexOption.IGNORE_CASE),
        Regex("""ref(?:erence)?\s*(?:id|no\.?|#)\s*[:\-]?\s*([A-Za-z0-9]{5,20})""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:tid|cnf|confirmation)\s*[:\-]?\s*([A-Za-z0-9]{5,20})""", RegexOption.IGNORE_CASE)
    )

    // Pakistani mobile numbers, allowing the common "0300-1234567" style
    // separator: 03xxxxxxxxx, 0300-1234567, +923xxxxxxxxx, 923xxxxxxxxx.
    private val MOBILE_NUMBER_PATTERN =
        Regex("""(?:\+?92[-\s]?|0)3\d{2}[-\s]?\d{7}\b""")

    private val ACCOUNT_NUMBER_PATTERN =
        Regex("""(?:a/?c|account)\s*(?:no\.?|#|number)?\s*[:\-]?\s*(\d{6,20})""", RegexOption.IGNORE_CASE)

    // The label ("Name:", "To:", "Beneficiary:", ...) is matched case-
    // insensitively via the inline (?i:...) flag, but the captured name
    // itself stays case-SENSITIVE (must look like "Ali Raza", not
    // "ali raza" or a stray all-caps bank disclaimer word).
    // Note: within the captured name, whitespace between words is
    // restricted to spaces/tabs ([ \t]) rather than \s, so the match
    // cannot run across a newline onto the next line's label (e.g. a
    // "Number:" line right after the name).
    private val NAME_PATTERN =
        Regex("""(?i:beneficiary|receiver|recipient|a/?c\s*title|account\s*title|title|name|to)[ \t]*[:\-]?[ \t]*([A-Z][a-zA-Z']+(?:[ \t]+[A-Z][a-zA-Z']+){0,3})""")

    private val DATE_PATTERNS = listOf(
        // 2026-09-07
        Regex("""\b(\d{4}-\d{2}-\d{2})\b"""),
        // 07-09-2026, 07/09/2026, 07.09.2026
        Regex("""\b(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})\b"""),
        // 07-Sep-2026, 07 Sep 2026, 7Sep26
        Regex(
            """\b(\d{1,2}[-\s]?(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[-\s]?\d{2,4})\b""",
            RegexOption.IGNORE_CASE
        ),
        // Sep 07, 2026 / September 7 2026
        Regex(
            """\b((?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{2,4})\b""",
            RegexOption.IGNORE_CASE
        )
    )

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd",
        "dd-MM-yyyy", "dd/MM/yyyy", "dd.MM.yyyy",
        "dd-MM-yy", "dd/MM/yy",
        "dd-MMM-yyyy", "dd MMM yyyy", "ddMMMyy", "dd-MMM-yy",
        "MMM dd, yyyy", "MMM dd yyyy", "MMMM dd, yyyy", "MMMM dd yyyy"
    )

    private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun parse(text: String): ReceiptFields {
        if (text.isBlank()) return ReceiptFields.EMPTY

        val amount = extractAmount(text)
        val txnId = extractFirstGroup(text, TXN_ID_PATTERNS)
        val number = extractNumber(text)
        val name = NAME_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        val (dateText, dateIso) = extractDate(text)

        return ReceiptFields(
            name = name,
            amountText = amount?.first,
            amountValue = amount?.second,
            dateText = dateText,
            dateIso = dateIso,
            txnId = txnId?.uppercase(Locale.US),
            number = number,
            rawText = text
        )
    }

    /** Normalizes a phone/account number to just its digits, and for an
     * 11-digit Pakistani mobile written with a leading 0, drops the 0 so
     * "03001234567" and "923001234567" and "+923001234567" all normalize
     * to the same "923001234567"-style comparable digits are exposed via
     * [comparableNumber]. */
    fun comparableNumber(number: String?): String? {
        if (number.isNullOrBlank()) return null
        var digits = number.filter { it.isDigit() }
        if (digits.length == 11 && digits.startsWith("0")) {
            digits = "92" + digits.substring(1)
        }
        if (digits.length < 7) return null
        // Compare on the last 10 digits so "923001234567" and "3001234567"
        // and "03001234567" all agree.
        return digits.takeLast(10)
    }

    private fun extractAmount(text: String): Pair<String, Double>? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val raw = match.groupValues[1]
            val cleaned = raw.replace(",", "")
            val value = cleaned.toDoubleOrNull() ?: continue
            if (value <= 0.0) continue
            return raw to value
        }
        return null
    }

    private fun extractFirstGroup(text: String, patterns: List<Regex>): String? {
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            return match.groupValues[1]
        }
        return null
    }

    private fun extractNumber(text: String): String? {
        MOBILE_NUMBER_PATTERN.find(text)?.let { return normalizeMobile(it.value) }
        ACCOUNT_NUMBER_PATTERN.find(text)?.let { return it.groupValues[1] }
        return null
    }

    /** Strips separators and folds a "92xxxxxxxxxx" prefix back to "0xxxxxxxxxx". */
    private fun normalizeMobile(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.startsWith("92") && digits.length == 12) {
            "0" + digits.substring(2)
        } else {
            digits
        }
    }

    private fun extractDate(text: String): Pair<String?, String?> {
        for (pattern in DATE_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val raw = match.groupValues[1]
            val iso = tryNormalizeDate(raw)
            if (iso != null) {
                return raw to iso
            }
        }
        return null to null
    }

    private fun tryNormalizeDate(raw: String): String? {
        // Normalize a stray comma/extra spaces so "Sep 07,2026" parses like "Sep 07, 2026".
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        for (pattern in DATE_FORMATS) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH)
                fmt.isLenient = false
                val parsed = fmt.parse(cleaned) ?: continue
                return ISO_FORMAT.format(parsed)
            } catch (_: Exception) {
                // Try the next format - a parse failure here is expected and normal.
            }
        }
        return null
    }
}
