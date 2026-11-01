'use strict';
const assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{
  for(const edited of [false,true]){
   const page=await browser.newPage({viewport:{width:393,height:852},reducedMotion:'reduce'});
   await page.route('https://appassets.androidplatform.net/merchant-icon/**',route=>route.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlSMAAAAASUVORK5CYII=','base64')}));
   await page.goto(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/web/index.html')).href);
   await page.evaluate(()=>{document.body.classList.add('native-app');refresh=()=>render();records=[{id:1,state:'POSTED',source:'OCR',merchant:'合成商',category:'其他',amountCents:10060,direction:'EXPENSE',occurredAt:Date.now(),historyRevision:1,hasScreenshot:false,hasMerchantIcon:false}];render();openRecord(1)});
   await page.evaluate(edited=>{
    if(edited){$('merchant').value='用户正在改名';$('note').value='未保存备注'}
    records[0]={...records[0],merchant:'合成商贸',hasMerchantIcon:true,merchantIconKey:'a'.repeat(64),merchantIconRevision:123,iconStatus:'cached'};
    syncRecordHistory();
   },edited);
   assert(await page.evaluate(()=>draft.hasMerchantIcon),'A missing icon found later must update an already-open detail without needing a new screenshot');
   assert.equal(await page.locator('#merchant').inputValue(),edited?'用户正在改名':'合成商贸');
   if(edited)assert.equal(await page.locator('#note').inputValue(),'未保存备注');
   await page.evaluate(()=>{records[0].merchantIconRevision=124;syncRecordHistory();syncRecordHistory()});
   assert.equal(await page.locator('#merchant').inputValue(),edited?'用户正在改名':'合成商贸','Repeated refresh must keep the stored-name baseline separate from unsaved edits');
   assert.equal(await page.locator('#record-panel .merchant-icon img').count(),1);
   await page.close();console.log(`PASS detail merchant/icon refresh, unsaved edits=${edited}`);
  }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
