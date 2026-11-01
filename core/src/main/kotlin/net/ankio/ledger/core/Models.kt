package net.ankio.ledger.core

enum class Source { OCR, IMAGE, SMS, NOTIFICATION, MANUAL }
enum class Direction { EXPENSE, INCOME }
enum class RecordState { POSTED, REVIEW }

data class Capture(
    val source: Source,
    val origin: String,
    val text: String,
    val receivedAt: Long = System.currentTimeMillis(),
)

data class ParsedBill(
    val amountCents: Long?,
    val direction: Direction?,
    val merchant: String,
    val category: String,
    val occurredAt: Long,
    val transactionId: String?,
    val reliable: Boolean,
    val reason: String,
    val timeExplicit: Boolean = false,
    val refund: RefundInfo? = null,
)

/** Completed refund evidence; amountCents is always computed locally from these cents. */
data class RefundInfo(
    val provider: String,
    val originalAmountCents: Long,
    val refundedCents: Long,
    val mode: String = "cumulative",
    val refundId: String? = null,
)

data class ExistingBill(
    val id: Long,
    val source: Source,
    val origin: String,
    val text: String,
    val amountCents: Long?,
    val direction: Direction?,
    val merchant: String,
    val occurredAt: Long,
    val transactionId: String?,
    val timeExplicit: Boolean? = null,
)

sealed class Duplicate {
    data object None : Duplicate()
    data class Exact(val id: Long) : Duplicate()
    data class Possible(val id: Long) : Duplicate()
}
