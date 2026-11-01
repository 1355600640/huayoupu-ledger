package net.ankio.ledger.core

import java.text.Normalizer

/** Terminal/pending transaction states are feedback, never an editable expense draft. */
object TransactionRejection {
    private val closed = Regex("交易(?:已)?关闭|订单(?:已)?关闭|订单已取消|交易已取消")
    private val refundPending = Regex("退款处理中|退款申请中|退款审核中|退款受理中|退款中|待退款|等待退款")
    private val paidTotal = Regex("^(?:实付款|实付金额|实际支付)[：:]?[¥￥]?([0-9]+(?:\\.[0-9]{1,2})?)(?:元)?$")
    private val refundTotal = Regex("^退款(?:成功|完成)(?:支付宝|微信(?:支付渠道)?)?[：:]?[¥￥]([0-9]+(?:\\.[0-9]{1,2})?)(?:元)?$")

    fun reason(raw: String): String? {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val compact = normalized.filterNot(Char::isWhitespace)
        if (closed.containsMatchIn(compact)) return "交易已关闭，不予记账"
        // “申请退款” and “极速退款” are service buttons on ordinary paid orders.
        if (refundPending.containsMatchIn(compact)) return "退款处理中，暂不记账"
        if (compact.contains("退款失败")) return "退款未完成，暂不记账"
        if (StandaloneRefundReceipt.matches(raw)) return StandaloneRefundReceipt.message(raw)
        // A shopping refund status row can state the returned sum without a
        // “全额” label. Require an unambiguous paid total and completed return.
        val fields = normalized.lineSequence().map { it.filterNot(Char::isWhitespace) }.toList()
        fun amounts(pattern: Regex) = fields.mapNotNull { line ->
            pattern.matchEntire(line)?.groupValues?.get(1)?.let(Money::parseCents)
        }.distinct().singleOrNull()
        val paid = amounts(paidTotal)
        if (paid != null && paid > 0 && paid == amounts(refundTotal)) return "已全额退款，不予记账"
        return null
    }
}
