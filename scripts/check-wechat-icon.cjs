'use strict';
const assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{
  const page=await browser.newPage({viewport:{width:393,height:852},reducedMotion:'reduce'});
  await page.route('https://appassets.androidplatform.net/merchant-icon/**',route=>route.abort());
  await page.goto(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/web/index.html')).href);
  for(const category of ['',null,'其他','其他收入']){
   assert(await page.evaluate(category=>ledgerIcon({category,paymentProvider:'wechat'}).includes('fa-brands fa-weixin'),category));
  }
  for(const category of ['餐饮','交通','自定义分类']){
   assert(!await page.evaluate(category=>ledgerIcon({category,paymentProvider:'wechat'}).includes('fa-weixin'),category));
  }
  for(const paymentProvider of ['alipay',null,undefined]){
   assert(!await page.evaluate(paymentProvider=>ledgerIcon({category:'其他',paymentProvider}).includes('fa-weixin'),paymentProvider));
  }
  await page.evaluate(()=>{
   document.body.classList.add('native-app');refresh=()=>render();
   records=[{id:1,state:'POSTED',source:'OCR',merchant:'微信转账',category:'其他',paymentProvider:'wechat',amountCents:1,direction:'EXPENSE',occurredAt:Date.now()}];render();
  });
  assert.equal(await page.locator('[data-record="1"] .fa-weixin').count(),1);
  await page.evaluate(()=>openRecord(1));
  assert.equal(await page.locator('#record-panel .record-hero .fa-weixin').count(),1);
  await page.evaluate(()=>{records[0].paymentProvider='alipay';syncRecordHistory()});
  assert.equal(await page.locator('#record-panel .record-hero .fa-weixin').count(),0,'An open detail follows changed payment evidence');
  const prioritized=await page.evaluate(()=>ledgerIcon({...records[0],paymentProvider:'wechat',hasMerchantIcon:true,merchantIconKey:'a'.repeat(64)}));
  assert(prioritized.includes('merchant-icon')&&prioritized.includes('<img'),'Merchant artwork stays ahead of the fallback');
  await page.evaluate(()=>{cancelEditor(true);records[0]={...records[0],paymentProvider:'wechat',hasMerchantIcon:true,merchantIconKey:'a'.repeat(64)};render()});
  await page.waitForFunction(()=>!document.querySelector('[data-record="1"] .merchant-icon img'));
  assert.equal(await page.locator('[data-record="1"] .fa-weixin').count(),1,'Failed merchant images reveal the WeChat fallback');
  console.log('PASS WeChat fallback, category and merchant precedence, detail refresh, failed image');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
