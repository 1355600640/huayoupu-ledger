/* Exercise real detail-panel frames; synthetic bills never reach native storage. */
'use strict';
const assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try {
  for(const reducedMotion of ['no-preference','reduce'])for(const viewport of [{width:320,height:740},{width:393,height:852},{width:740,height:393}]){
   const page=await browser.newPage({viewport,reducedMotion}),errors=[];
   page.on('pageerror',e=>errors.push(e.message));
   await page.goto(pathToFileURL(path.resolve(process.env.FLUID_WEB_ROOT||path.join(__dirname,'../app/src/main/assets/web'),'index.html')).href);
   await page.evaluate(()=>{
    document.body.classList.add('native-app');refresh=()=>render();
    records=[1,2].map(id=>({id,state:'POSTED',source:'OCR',amountCents:1558,occurredAt:Date.now(),direction:'EXPENSE',merchant:'合成账单'+id,category:'餐饮美食'}));render();go('ledger');
   });
   const samples=await page.evaluate(()=>new Promise(resolve=>{
    openRecord(1);const values=[];
    function sample(){values.push({panel:Number(getComputedStyle($('record-panel').querySelector('.record-panel-content')).opacity),shade:Number(getComputedStyle($('record-shade')).opacity)});if($('record-panel')._morph)requestAnimationFrame(sample);else resolve(values)}requestAnimationFrame(sample);
   }));
   if(reducedMotion==='no-preference'){
    assert(samples.some(s=>s.panel>0.01&&s.panel<0.99),'Detail must visibly fade in');
    assert(samples.some(s=>s.shade>0.01&&s.shade<0.99),'Detail backdrop must fade in with the card, not appear instantly');
   }
   assert.equal(await page.locator('#merchant').inputValue(),'合成账单1');
   const exit=await page.evaluate(()=>new Promise(resolve=>{
    cancelEditor();const values=[];
    function sample(){values.push({panel:Number(getComputedStyle($('record-panel').querySelector('.record-panel-content')).opacity),shade:Number(getComputedStyle($('record-shade')).opacity)});if(!$('record-panel').hidden)requestAnimationFrame(sample);else resolve(values)}requestAnimationFrame(sample);
   }));
   if(reducedMotion==='no-preference')assert(exit.some(s=>s.panel>0.01&&s.panel<0.99&&s.shade>0.01&&s.shade<0.99),'Both card and backdrop must fade out');
   assert(await page.evaluate(()=>$('record-panel').hidden&&$('record-shade').hidden&&document.querySelector('.workspace').contains($('import'))));
   if(reducedMotion==='no-preference'){
    const reversal=await page.evaluate(()=>new Promise(resolve=>{
     openRecord(1);
     function reverse(){const before=Number(getComputedStyle($('record-panel').querySelector('.record-panel-content')).opacity);if(before<0.1||before>0.8){requestAnimationFrame(reverse);return}cancelEditor();resolve({before,after:Number(getComputedStyle($('record-panel').querySelector('.record-panel-content')).opacity)})}requestAnimationFrame(reverse);
    }));
    assert(Math.abs(reversal.before-reversal.after)<0.04,`Reversing entrance must not flash fully opaque: ${JSON.stringify(reversal)}`);
   }
   await page.evaluate(()=>{openRecord(1);cancelEditor();openRecord(2)});
   await page.waitForFunction(()=>!document.querySelector('#record-panel')._morph);
   assert(await page.evaluate(()=>recordPanelOpen&&!recordPanelClosing&&$('record-panel').open&&!$('record-panel').hidden&&$('record-panel').contains($('import'))));
   assert.equal(await page.locator('#merchant').inputValue(),'合成账单2');
   await page.evaluate(()=>window.LedgerUI.back());await page.waitForFunction(()=>document.querySelector('#record-panel').hidden);
   assert(await page.evaluate(()=>$('record-panel').hidden&&!document.querySelector('.workspace').hasAttribute('aria-hidden')&&Math.abs(document.querySelector('#ledger .bottom-nav').getBoundingClientRect().bottom-innerHeight)<2));
   assert.deepEqual(errors,[]);
   console.log(`PASS ${viewport.width}x${viewport.height} ${reducedMotion}: detail/backdrop frames, interruption, reopen, back and fixed tabs`);
   await page.close();
  }
 } finally {await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
