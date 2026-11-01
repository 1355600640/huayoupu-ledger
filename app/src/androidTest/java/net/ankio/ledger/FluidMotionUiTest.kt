package net.ankio.ledger

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.core.*
import net.ankio.ledger.data.IngestResult
import net.ankio.ledger.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FluidMotionUiTest {
    @Test fun plusPanelsKeepOneFullScreenShadeAndRevealContentsAfterExpansion() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            web.run("go('ledger');document.querySelector('#ledger [aria-label=\"添加记账\"]').click()")
            assertEquals("Manual entry must dim the entire viewport even in older WebViews", "true",
                web.value("(()=>{const r=$('manual-shade').getBoundingClientRect();return r.width>=innerWidth-1&&r.height>=innerHeight-1})()"))
            web.run("closeManual(false)")
            for(kind in listOf("manual","account")) {
                val panel=if(kind=="manual") "manual-panel" else "sheet"
                web.run(if(kind=="manual") "document.querySelector('#ledger [aria-label=\"添加记账\"]').click()" else "go('assets');$('add-account').click()")
                web.await("!$('${panel}').classList.contains('fluid-pending')")
                if(web.value("matchMedia('(prefers-reduced-motion: reduce)').matches")=="false") {
                    web.run("{const p=$('${panel}');[p._morph,p._shape,p._fade,...(p._contentAnimations||[])].filter(Boolean).forEach(a=>{a.pause();a.currentTime=150})}")
                    assertEquals("$kind must use its backdrop without a moving shadow", "none",web.text("getComputedStyle($('${panel}')._surface).boxShadow"))
                    val content=if(kind=="manual") "document.querySelector('.manual-panel-content')" else "$('sheet-content')"
                    assertEquals("$kind must not reveal clipped fields during early expansion", "0",web.value("Number(getComputedStyle($content).opacity)"))
                    web.run("$('${panel}')._morph.finish()")
                }
                web.await("!$('${panel}')._morph")
                if(kind=="manual") {
                    web.run("manualKey('2');manualKey('8');manualKey('.');manualKey('5')")
                    assertEquals("28.5",web.text("$('manual-amount').value"))
                } else {
                    assertEquals("true",web.value("$('sheet-content').clientHeight>100"))
                }
                web.run("window.LedgerUI.back()")
                web.await(if(kind=="manual") "$('manual-panel').hidden&&$('manual-shade').hidden" else "!$('sheet').open")
                assertEquals("Back releases the moving surface", "0",web.value("document.querySelectorAll('.fluid-surface').length"))
            }
        }
    }
    @Test fun helpSheetKeepsReadableBodyAndUsesOnlyOneBackdropForDepth() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            web.run("go('settings');settingsHelp()")
            web.await("$('sheet').open&&!$('sheet').classList.contains('fluid-pending')")
            assertEquals("Help text must occupy a visible scrolling body, including older WebViews", "true",
                web.value("$('sheet-content').clientHeight>100&&$('sheet-content').scrollHeight>100"))
            assertEquals("The modal backdrop supplies depth without a second dark halo", "none",
                web.text("getComputedStyle($('sheet')).boxShadow"))
            if(web.value("!!$('sheet')._surface")=="true") {
                assertEquals("The temporary surface must not add another shadow", "none",
                    web.text("getComputedStyle($('sheet')._surface).boxShadow"))
            }
            web.await("!$('sheet')._morph")
            web.run("$('sheet-content').scrollTop=$('sheet-content').scrollHeight")
            assertEquals("Close remains visible after scrolling help", "true",web.value("$('sheet').querySelector('.sheet-heading button').getBoundingClientRect().bottom<=innerHeight"))
            web.run("window.LedgerUI.back()")
            web.await("!$('sheet').open")
            assertEquals("Closing releases temporary surfaces", "0",web.value("document.querySelectorAll('.fluid-surface').length"))
        }
    }
    @Test fun modalBackdropReversesFromPresentedFrameAndSurfaceStaysOpaque() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            web.run("""
                go('settings');window.__shade={values:[],done:false,reduced:matchMedia('(prefers-reduced-motion: reduce)').matches};settingsHelp();
                __shade.pending=Number(getComputedStyle($('sheet'),'::backdrop').opacity);
                function sampleShade(){
                  const value=Number(getComputedStyle($('sheet'),'::backdrop').opacity);__shade.values.push(value);
                  if((value>0&&value<.95)||__shade.reduced||__shade.values.length>90){
                    __shade.before=value;closeSheet();__shade.after=Number(getComputedStyle($('sheet'),'::backdrop').opacity);__shade.done=true;
                  }else requestAnimationFrame(sampleShade);
                }
                requestAnimationFrame(sampleShade);
            """.trimIndent())
            web.await("__shade.done")
            if(web.value("__shade.reduced")=="true")return@use
            assertEquals("Modal backdrop must preserve intermediate brightness: ${web.text("JSON.stringify(__shade)")}", "true",web.value("__shade.pending===0&&__shade.before>0&&__shade.before<.95&&Math.abs(__shade.before-__shade.after)<.025"))
            web.run("if($('sheet')._morph)$('sheet')._morph.finish()")
            web.await("!$('sheet').open")
            web.run("settingsHelp()")
            web.await("!!$('sheet')._morph")
            web.run("[$('sheet')._morph,$('sheet')._fade,$('sheet')._shape,...($('sheet')._contentAnimations||[])].filter(Boolean).forEach(a=>{a.pause();a.currentTime=150})")
            assertEquals("The changing panel background must not become translucent", "true",web.value("getComputedStyle($('sheet')).opacity==='1'&&getComputedStyle($('sheet')._surface).opacity==='1'"))
            assertEquals("Contents keep their separate fade", "true",web.value("Number(getComputedStyle($('sheet').querySelector('.sheet-heading')).opacity)>0&&Number(getComputedStyle($('sheet').querySelector('.sheet-heading')).opacity)<1"))
            web.run("closeSheet(false)")
        }
    }
    @Test fun billDetailsAndBackdropFadeTogetherAndReverseWithoutFlashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            web.run("""
                refresh=()=>render();ledgerView=null;
                records=[1,2].map(id=>({id,_detailsLoaded:true,state:'POSTED',source:'OCR',amountCents:1558,occurredAt:Date.now(),direction:'EXPENSE',merchant:'动画验证'+id,category:'餐饮美食'}));render();go('ledger');
                window.__motion={reduced:matchMedia('(prefers-reduced-motion: reduce)').matches,enter:[],exit:[]};openRecord(1);
                // Check rendered intermediate styles at controlled animation times.
                // A loaded emulator can skip every rAF in a short real-time exit;
                // the browser regression separately samples unpaused live frames.
                function hold(time){[$('record-panel')._morph,$('record-panel')._fade,$('record-panel')._shape,...($('record-panel')._contentAnimations||[])].filter(Boolean).forEach(a=>{a.pause();a.currentTime=time})}
                function frame(){return [Number(getComputedStyle($('record-panel').querySelector('.record-panel-content')).opacity),Number(getComputedStyle($('record-shade')).opacity)]}
                if(__motion.reduced){cancelEditor();__motion.done=true}
                else{
                  hold(100);requestAnimationFrame(()=>{
                    __motion.enter.push(frame());cancelEditor();hold(90);
                    requestAnimationFrame(()=>{__motion.exit.push(frame());$('record-panel')._morph.finish();__motion.done=true});
                  });
                }
            """.trimIndent())
            web.await("window.__motion?.done===true")
            if(web.value("__motion.reduced")=="false") {
                assertEquals("Both layers must visibly fade in", "true",web.value("__motion.enter.some(([p,s])=>p>0.01&&p<0.99&&s>0.01&&s<0.99)"))
                assertEquals("Both layers must visibly fade out: ${web.text("JSON.stringify(__motion)")}", "true",web.value("__motion.exit.some(([p,s])=>p>0.01&&p<0.99&&s>0.01&&s<0.99)"))
                // Pause at a known presented frame; emulator scheduling must not decide
                // which fraction of the opening animation this interruption covers.
                web.run("openRecord(1);[$('record-panel')._morph,$('record-panel')._fade,$('record-panel')._shape,...($('record-panel')._contentAnimations||[])].filter(Boolean).forEach(a=>{a.pause();a.currentTime=100})")
                web.run("window.__beforeReverse=Number(getComputedStyle($('record-panel').querySelector('.record-panel-content')).opacity);cancelEditor();window.__afterReverse=Number(getComputedStyle($('record-panel').querySelector('.record-panel-content')).opacity)")
                assertEquals("Interrupting entrance must preserve current opacity", "true",web.value("__beforeReverse>0.1&&__beforeReverse<0.95&&Math.abs(__beforeReverse-__afterReverse)<0.04"))
            }
            web.run("openRecord(1);cancelEditor();openRecord(2)")
            web.await("!$('record-panel')._morph && recordPanelOpen")
            assertEquals("动画验证2",web.text("$('merchant').value"))
            assertEquals("true",web.value("$('record-panel').contains($('import'))&&!$('record-shade').hidden"))
            web.run("window.LedgerUI.back()")
            web.await("$('record-panel').hidden&&$('record-shade').hidden&&document.querySelector('.workspace').contains($('import'))")
        }
    }
    @Test fun returningToScrolledStatisticsKeepsBottomTabsInsideViewportDuringEntrance() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            web.run("refresh=()=>render();ledgerView=null;month=localMonth(Date.now());records=Array.from({length:30},(_,i)=>({id:900000+i,state:'POSTED',source:'MANUAL',amountCents:100,occurredAt:Date.now(),direction:'EXPENSE',merchant:'合成商户',category:'分类'+i}));render();go('stats')")
            android.os.SystemClock.sleep(250)
            web.run("document.querySelector('#stats').scrollTop=900;go('ledger');go('stats');window.__returnedNav=document.querySelector('#stats .bottom-nav').getBoundingClientRect().bottom;window.__retainedScroll=document.querySelector('#stats').scrollTop")
            assertTrue("Statistics must really be scrolled",web.value("window.__retainedScroll").toDouble()>100)
            assertEquals("Tabs must remain at the viewport bottom while entering",web.value("innerHeight").toDouble(),web.value("window.__returnedNav").toDouble(),2.0)
            android.os.SystemClock.sleep(250)
            assertEquals("true",web.value("document.querySelector('#stats .bottom-nav').contains(document.elementFromPoint(innerWidth/2,innerHeight-20))"))
        }
    }
    @Test fun pendingPanelKeepsStableLayoutAndInterruptedCloseDoesNotLoseEditor() {
        val app=ApplicationProvider.getApplicationContext<LedgerApp>()
        val now=System.currentTimeMillis()
        val saved=app.store.ingest(Capture(Source.OCR,"fluid-test","支付成功 合成商户 ${UUID.randomUUID()}",now),
            ParsedBill(null,null,"合成商户","其他",now,null,false,"金额待确认",false)) as IngestResult.Saved
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val web=WebUiProbe(scenario);web.ready()
                web.await("!!document.querySelector('[data-pending=\"${saved.record.id}\"]')")
                val capsule=web.value("document.querySelector('[data-pending=\"${saved.record.id}\"]').getBoundingClientRect().height").toDouble()
                capture(app,"fluid-pending-home.png")
                web.run("document.querySelector('[data-pending=\"${saved.record.id}\"]').click();window.__panelHeight=document.querySelector('#record-panel').getBoundingClientRect().height")
                val early=web.value("document.querySelector('#record-panel').getBoundingClientRect().height").toDouble()
                web.await("!document.querySelector('#record-panel')._morph")
                val final=web.value("document.querySelector('#record-panel').getBoundingClientRect().height").toDouble()
                capture(app,"fluid-pending-panel.png")
                assertTrue("Detail must be expanded without animating layout: $capsule/$early/$final",final>capsule*2 && kotlin.math.abs(early-final)<2)
                assertEquals("true",web.value("document.querySelector('#ledger').classList.contains('mobile-active') && document.querySelector('#record-panel').contains(document.querySelector('#import'))"))
                assertEquals("SECTION",web.text("document.querySelector('#record-panel').tagName"))
                assertEquals("true",web.value("Math.abs(document.querySelector('#record-panel').getBoundingClientRect().bottom-innerHeight)<2"))
                web.run("cancelEditor();go('ledger');document.querySelector('[data-pending=\"${saved.record.id}\"]').click()")
                web.await("document.querySelector('#record-panel').open && document.querySelector('#merchant').value==='合成商户'")
                web.run("window.LedgerUI.back()")
                web.await("!document.querySelector('#record-panel').open && document.querySelector('.workspace').contains(document.querySelector('#import'))")
                assertEquals("true",web.value("document.querySelector('#ledger').classList.contains('mobile-active')"))
            }
        } finally { app.store.delete(saved.record.id);app.changed() }
    }
    private fun capture(app:LedgerApp,name:String) {
        val bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { java.io.File(app.cacheDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
    }
}
