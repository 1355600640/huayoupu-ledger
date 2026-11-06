package net.ankio.ledger.data

import net.ankio.ledger.core.Money

/**
 * Budget model for monthly overall and per-category budgets.
 * [month] is formatted as "yyyy-MM", or "*" indicating a recurring default for any month.
 * [category] is empty string "" for total overall budget, or category name.
 */
data class LedgerBudget(
    val id: Long = 0,
    val month: String,
    val category: String = "",
    val amountCents: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun validate() {
        require(id >= 0) { "预算编号无效" }
        require(month == "*" || month.matches(Regex("""^\d{4}-\d{2}$"""))) { "预算月份格式错误" }
        require(category.length <= 40) { "预算分类名称过长" }
        require(amountCents in 0..Money.MAX_CENTS) { "预算金额无效" }
    }
}
