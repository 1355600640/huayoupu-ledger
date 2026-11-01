package net.ankio.ledger.data

import net.ankio.ledger.core.*
import org.junit.Assert.*
import org.junit.Test

class RefundLedgerTest {
    private fun bill(refund:RefundInfo?=RefundInfo("wechat",1000,200,"cumulative"),amount:Long=800,
        direction:Direction=Direction.EXPENSE)=ParsedBill(amount,direction,"测试商户","餐饮",1,null,true,"",true,refund)

    @Test fun cumulativeSummariesIncludePartialFullAndZeroRefunds() {
        for(provider in listOf("wechat","alipay"))for(amount in listOf(0L,200L,1000L)) {
            val value=bill(RefundInfo(provider,1000,amount,"cumulative"),1000-amount)
            assertTrue(validRefundEvidence(value));assertTrue(validRefundSummary(value))
        }
    }

    @Test fun singleRefundIsEvidenceOnlyAndNeedsAnIdentifier() {
        val single=bill(RefundInfo("wechat",1000,200,"single","refund-123"))
        assertTrue(validRefundEvidence(single));assertFalse(validRefundSummary(single))
        assertFalse(validRefundEvidence(bill(single.refund!!.copy(refundId=" "))))
    }

    @Test fun invalidProviderRangeDirectionNetAmountOrModeCannotBecomeASummary() {
        val original=bill();val refund=original.refund!!
        val invalid=listOf(bill(null),bill(refund.copy(provider="other")),bill(refund.copy(mode="unknown")),
            bill(refund.copy(originalAmountCents=0)),bill(refund.copy(originalAmountCents=Money.MAX_CENTS+1)),
            bill(refund.copy(refundedCents=-1)),bill(refund.copy(refundedCents=1001)),
            bill(amount=799),bill(direction=Direction.INCOME))
        invalid.forEach { assertFalse(validRefundEvidence(it));assertFalse(validRefundSummary(it)) }
    }
}
