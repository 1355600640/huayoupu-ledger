package net.ankio.ledger.data

import net.ankio.ledger.core.*

/** Stable evidence slots survive image cleanup; missing bytes do not renumber later captures. */
data class RecordCaptureHistory(
    val sequence: Int,
    val at: Long,
    val kind: String,
    val amountCents: Long?,
    val refundedCents: Long,
    val rawText: String,
    val hasScreenshot: Boolean,
    val refundMode: String = "cumulative",
    val refundId: String? = null,
    val refundAmountCents: Long? = null,
)

data class RestoredCaptureHistory(val entry: RecordCaptureHistory, val image: ByteArray? = null)

internal fun paymentProvider(capture: Capture): String? {
    val origin = capture.origin.lowercase()
    return when {
        origin.contains("com.tencent.mm") || origin == "微信" || origin == "微信支付" -> "wechat"
        origin.contains("com.eg.android.alipaygphone") || origin == "支付宝" -> "alipay"
        capture.source in setOf(Source.IMAGE, Source.OCR) && capture.text.contains("微信") && !capture.text.contains("支付宝") -> "wechat"
        capture.source in setOf(Source.IMAGE, Source.OCR) && capture.text.contains("支付宝") && !capture.text.contains("微信") -> "alipay"
        else -> null
    }
}

internal fun validRefundSummary(bill:ParsedBill)=bill.refund?.mode=="cumulative" && validRefundEvidence(bill)

internal fun validRefundEvidence(bill: ParsedBill): Boolean {
    val refund = bill.refund ?: return false
    return refund.provider in setOf("wechat", "alipay") && refund.originalAmountCents in 1..Money.MAX_CENTS &&
        refund.refundedCents in 0..refund.originalAmountCents && bill.direction == Direction.EXPENSE &&
        bill.amountCents == refund.originalAmountCents - refund.refundedCents &&
        (refund.mode == "cumulative" || refund.mode == "single" && !refund.refundId.isNullOrBlank())
}
