'use strict';
// Synthetic browser-only data; no device financial data is read or changed.
const assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{
  for(const viewport of [{width:320,height:640},{width:393,height:852},{width:740,height:393}])for(const reducedMotion of ['no-preference','reduce']){
   const page=await browser.newPage({viewport,reducedMotion}),errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.goto(pathToFileURL(path.resolve(process.env.FLUID_WEB_ROOT||path.join(__dirname,'../app/src/main/assets/web'),'index.html')).href);
   await page.evaluate(()=>{document.body.classList.add('native-app');refresh=()=>render();bankOptions=[{kind:'bank_icbc',label:'工商银行',icon:'icbc'}];accounts=[{id:1,kind:'bank_icbc',name:'测试银行卡',amountCents:10000,type:'ASSET'}];records=[{id:1,state:'POSTED',source:'OCR',amountCents:1234,occurredAt:Date.now(),direction:'EXPENSE',merchant:'合成商户',category:'餐饮',history:[{sequence:1,hasScreenshot:true,screenshotUrl:'/test.svg',kind:'payment',createdAt:Date.now()}]}];render();go('assets')});
   await page.locator('#add-account').click();
   if(reducedMotion==='no-preference'){
    await page.evaluate(async()=>{await new Promise(requestAnimationFrame);const panel=$('sheet');[panel._morph,panel._shape,...(panel._contentAnimations||[])].filter(Boolean).forEach(a=>{a.pause();a.currentTime=150})});
    assert.equal(await page.locator('#sheet-content').evaluate(e=>Number(getComputedStyle(e).opacity)),0,'Account fields must not show clipped text before the surface has expanded');
    await page.evaluate(()=>$('sheet')._morph.finish());
   }
   await page.locator('#account-kind').selectOption('bank');
   await page.waitForTimeout(460);
   const close=await page.evaluate(()=>{const body=$('sheet-content');body.scrollTop=body.scrollHeight;$('sheet').scrollTop=$('sheet').scrollHeight;const r=$('sheet').querySelector('.sheet-heading button').getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:innerHeight,scroll:body.scrollTop,scrollHeight:body.scrollHeight,clientHeight:body.clientHeight}});
   assert(close.top>=0&&close.bottom<=close.height,`Bank editor close must remain visible after scrolling: ${JSON.stringify(close)}`);
   assert(close.scrollHeight>close.clientHeight&&close.scroll>0,'Test must actually scroll the bank form');
   await page.locator('#sheet .sheet-heading button').click();await page.waitForFunction(()=>!document.querySelector('#sheet').open);
   await page.locator('[data-account="1"]').click();await page.waitForTimeout(460);
   assert.equal(await page.locator('#sheet-title').textContent(),'编辑账户');
   await page.evaluate(()=>window.LedgerUI.back());await page.waitForFunction(()=>!document.querySelector('#sheet').open);
   await page.evaluate(()=>go('ledger'));
   await page.locator('#ledger .balance').click();await page.waitForTimeout(460);
   assert.match(await page.locator('#sheet-title').textContent(),/每日支出/);
   await page.evaluate(()=>window.LedgerUI.back());await page.waitForFunction(()=>!document.querySelector('#sheet').open);
   await page.locator('[data-record="1"]').click();
   if(reducedMotion==='no-preference'){
    const geometry=await page.evaluate(()=>{const s=$('record-panel')._surface;return s?{first:s.getBoundingClientRect().toJSON(),frames:$('record-panel')._shape.effect.getKeyframes()}:null});
    assert(geometry&&Number.parseFloat(geometry.frames[0].height)<Number.parseFloat(geometry.frames.at(-1).height),'Bill details must expand their surface bounds from the row');
    assert.notEqual(geometry.frames[0].borderRadius,geometry.frames.at(-1).borderRadius,'Morph must interpolate corner radius');
   }
   await page.waitForTimeout(460);
   await page.locator('[data-history-sequence="1"]').click();await page.waitForTimeout(460);
   assert.match(await page.locator('#sheet-title').textContent(),/付款截图.*1 \/ 1/);
   await page.evaluate(()=>window.LedgerUI.back());await page.waitForFunction(()=>!document.querySelector('#sheet').open);
   assert(await page.locator('#record-panel').isVisible(),'Closing a screenshot returns to its bill');
   await page.evaluate(()=>cancelEditor());await page.waitForFunction(()=>document.querySelector('#record-panel').hidden);
   await page.evaluate(()=>go('stats'));await page.locator('[data-ranking-category="餐饮"]').click();await page.waitForTimeout(460);
   assert.match(await page.locator('#sheet-title').textContent(),/餐饮/);
   await page.evaluate(()=>window.LedgerUI.back());await page.waitForFunction(()=>!document.querySelector('#sheet').open);
   await page.evaluate(()=>go('settings'));await page.locator('#settings-help').click();
   await page.waitForFunction(()=>!document.querySelector('#sheet').classList.contains('fluid-pending'));
   assert.equal(await page.locator('#sheet').evaluate(e=>getComputedStyle(e).boxShadow),'none','The modal backdrop must not be compounded by a dialog shadow');
   if(await page.locator('#sheet .fluid-surface').count())assert.equal(await page.locator('#sheet .fluid-surface').evaluate(e=>getComputedStyle(e).boxShadow),'none','The moving surface must not add a second shadow');
   assert(await page.locator('#sheet-content').evaluate(e=>e.clientHeight>100),'Help text must remain visible');
   await page.waitForTimeout(460);assert(await page.locator('#sheet').isVisible());
   await page.evaluate(()=>window.LedgerUI.back());await page.waitForFunction(()=>!document.querySelector('#sheet').open);
   // Reversing an in-flight sheet must preserve the rendered frame and leave no surface.
   await page.evaluate(()=>{settingsHelp();closeSheet();showDataTools()});await page.waitForTimeout(470);
   assert.equal(await page.locator('#sheet-title').textContent(),'账单与备份');
   await page.evaluate(()=>closeSheet());await page.waitForFunction(()=>!document.querySelector('#sheet').open);
   assert.equal(await page.locator('.fluid-surface').count(),0,'Closing must release all temporary surfaces');
   await page.evaluate(()=>go('ledger'));await page.locator('#ledger [aria-label="添加记账"]').click();
   if(reducedMotion==='no-preference'){
    await page.evaluate(()=>{const panel=$('manual-panel');[panel._morph,panel._shape,panel._fade,...(panel._contentAnimations||[])].filter(Boolean).forEach(a=>{a.pause();a.currentTime=150})});
    assert.equal(await page.locator('#manual-panel .fluid-surface').evaluate(e=>getComputedStyle(e).boxShadow),'none','The manual-entry surface must not add a dark halo over the backdrop');
    assert.equal(await page.locator('.manual-panel-content').evaluate(e=>Number(getComputedStyle(e).opacity)),0,'Manual entry content must appear after its surface expands');
    await page.evaluate(()=>$('manual-panel')._morph.finish());
   }
   await page.waitForTimeout(460);
   assert(await page.locator('#manual-shade').evaluate(e=>{const r=e.getBoundingClientRect();return r.width>=innerWidth-1&&r.height>=innerHeight-1}),'The manual-entry shade must cover the viewport');
   assert(await page.locator('#manual-panel').isVisible(),'Plus must expand to a manual-entry panel');
   await page.evaluate(()=>{$('manual-amount').value='38.60';$('manual-note').value='旋转前输入';const state=getState();go('assets');restoreState(state)});
   await page.waitForTimeout(460);
   assert(await page.locator('#manual-panel').isVisible(),'Restoring Activity state must restore the manual-entry panel');
   assert.equal(await page.locator('#manual-amount').inputValue(),'38.60','Restoring a manual panel must preserve the typed amount');
   await page.evaluate(()=>window.LedgerUI.back());await page.waitForFunction(()=>document.querySelector('#manual-panel').hidden);
   assert.deepEqual(errors,[]);console.log(`PASS ${viewport.width}x${viewport.height} ${reducedMotion}: bank close/edit, summary, bill/image, category, help/backup reversal, manual restoration/back`);await page.close();
  }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
