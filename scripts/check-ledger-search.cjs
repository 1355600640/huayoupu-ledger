'use strict';
// Synthetic browser data: search behavior, delayed input and header geometry.
const assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{for(const width of [320,393,430]){
  const page=await browser.newPage({viewport:{width,height:852},reducedMotion:'reduce'}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));await page.route('https://**',r=>r.abort());
  await page.addInitScript(()=>{
   const now=Date.now(),previous=new Date(new Date().getFullYear(),new Date().getMonth()-1,15).getTime();
   const rows=[{id:1,merchant:'咖啡测试',category:'餐饮',note:'早餐',occurredAt:now},{id:2,merchant:'地铁测试',category:'交通',note:'',occurredAt:now},{id:3,merchant:'上月咖啡',category:'餐饮',note:'跨月',occurredAt:previous}].map(r=>({...r,direction:'EXPENSE',source:'MANUAL',state:'POSTED',amountCents:1000}));
   window.searchRequests=[];window.LedgerNative={snapshotView(month,query){searchRequests.push(query);return JSON.stringify({ok:true,records:rows,channels:{ocr:{enabled:false}},accounts:[],automatic:{applications:[]}})},remember(){}};
  });
  await page.goto(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/web/index.html')).href);
  await page.evaluate(()=>document.fonts.ready);
  assert.equal(await page.locator('#ledger .header-actions [aria-label="添加记账"]').count(),0);
  assert.equal(await page.locator('#entries [data-record]').count(),2);
  const top=await page.locator('.home-month').evaluate(e=>e.getBoundingClientRect().top);
  await page.locator('#search-toggle').click();
  assert.equal(await page.locator('#ledger-search').evaluate(e=>e===document.activeElement),true);
  assert.equal(await page.locator('.home-month').evaluate(e=>e.getBoundingClientRect().top),top,'Opening search must preserve content position');
  await page.locator('#ledger-search').fill('咖啡');
  await page.waitForFunction(()=>query==='咖啡');
  assert.equal(await page.locator('#entries [data-record]').count(),2,'Search must include matching records from other months');
  await page.locator('#search-clear').click();
  assert.equal(await page.locator('#entries [data-record]').count(),2);
  assert.equal(await page.locator('#search-clear').isVisible(),false);
  assert.equal(await page.locator('#ledger-search').evaluate(e=>e===document.activeElement),true);
  await page.locator('#ledger-search').fill('未匹配');
  await page.locator('#ledger-search').press('Enter');
  assert.equal(await page.locator('#entries [data-record]').count(),0);
  assert.match(await page.locator('#entries').textContent(),/未找到匹配/);
  await page.locator('#search-cancel').click();
  assert.equal(await page.locator('#entries [data-record]').count(),2);
  assert.equal(await page.locator('#search-toggle').getAttribute('aria-expanded'),'false');
  // Closing before debounce fires must not reapply the abandoned query.
  await page.locator('#search-toggle').click();await page.locator('#ledger-search').fill('地铁');
  await page.evaluate(()=>LedgerUI.back());await page.waitForTimeout(250);
  assert.equal(await page.evaluate(()=>query),'');
  assert.equal(await page.locator('#ledger-search-form').isVisible(),false);
  await page.locator('#search-toggle').click();await page.locator('#ledger-search').press('Escape');
  assert.equal(await page.locator('#ledger-search-form').isVisible(),false);
  await page.locator('#search-toggle').click();await page.locator('#ledger-search').fill('咖啡');
  await page.evaluate(()=>go('assets'));await page.waitForTimeout(250);await page.evaluate(()=>go('ledger'));
  assert.equal(await page.evaluate(()=>query),'');
  assert.equal(await page.locator('#ledger-search-form').isVisible(),false);
  await page.locator('#search-toggle').click();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.evaluate(()=>LedgerUI.event('fontScale',1.5));
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  const largeTop=await page.locator('.home-month').evaluate(e=>e.getBoundingClientRect().top);
  await page.locator('#search-cancel').click();
  assert.equal(await page.locator('.home-month').evaluate(e=>e.getBoundingClientRect().top),largeTop,'Large text must also preserve the content position when search closes');
  assert.deepEqual(errors,[]);
  console.log(`PASS ${width}: stable header, cross-month search, submit, clear, cancel, back, Escape, debounce cleanup and navigation`);
  await page.close();
 }}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
