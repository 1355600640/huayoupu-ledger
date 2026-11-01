package net.ankio.ledger

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordMetadataLayoutTest {
    @Test fun longCategoriesAndSourceLabelsStayWholeWithoutHidingAmounts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            web.run("""
                month=localMonth(Date.now());filter='expense';records=['OCR','IMAGE','NOTIFICATION','SMS','MANUAL'].map((source,i)=>({id:990000+i,merchant:'示例超长商户名称有限公司',category:'母婴亲子与高速代扣超长分类',source,direction:'EXPENSE',state:'POSTED',amountCents:i===0?99999999:2690,occurredAt:Date.now()}));render()
            """.trimIndent())
            val result=web.value("""
                (()=>Array.from(document.querySelectorAll('.transaction')).every(e=>{
                    const badge=e.querySelector('.record-meta .tag'),range=document.createRange();range.selectNodeContents(badge);
                    const lineCount=range.getClientRects().length;
                    const amount=e.querySelector('.entry-amount').getBoundingClientRect(),box=e.getBoundingClientRect();
                    return lineCount===1&&amount.left>=box.left&&amount.right<=box.right&&document.documentElement.scrollWidth<=innerWidth
                }))()
            """.trimIndent())
            assertEquals("Source badges must stay on one line and amounts stay visible", "true",result)
            assertTrue(web.text("document.querySelector('.transaction').getAttribute('aria-label')").contains("母婴亲子与高速代扣超长分类"))
            assertEquals("母婴亲子与高速代扣超长分类",web.text("document.querySelector('.record-category').textContent"))
        }
    }
}
