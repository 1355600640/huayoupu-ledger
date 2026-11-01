'use strict';
const assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{for(const viewport of [{width:320,height:640},{width:393,height:852},{width:740,height:393}]){
  const page=await browser.newPage({viewport}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(pathToFileURL(path.resolve(process.env.FLUID_WEB_ROOT||path.join(__dirname,'../app/src/main/assets/web'),'index.html')).href);
  await page.evaluate(()=>{document.body.classList.add('native-app');refresh=()=>render();records=[{id:1,state:'POSTED',source:'OCR',amountCents:1234,occurredAt:Date.now(),direction:'EXPENSE',merchant:'合成商户',category:'餐饮'}];render();go('settings')});
  await page.evaluate(()=>{settingsHelp();window.__pendingShade=Number(getComputedStyle($('sheet'),'::backdrop').opacity)});
  await page.waitForFunction(()=>{const o=Number(getComputedStyle($('sheet'),'::backdrop').opacity);return o>0&&o<.95});
  const reverse=await page.evaluate(()=>{
   const panel=$('sheet'),value=()=>Number(getComputedStyle(panel,'::backdrop').opacity);
   const before=value();closeSheet();const after=value();
   return {before,after,backgroundFilter:getComputedStyle(panel,'::backdrop').backdropFilter};
  });
  assert.equal(await page.evaluate(()=>window.__pendingShade),0,'Before the first measured morph frame the modal must not flash a fully dimmed backdrop');
  assert(reverse.before>0&&reverse.before<.95,'Test must interrupt a partially dimmed entrance');
  assert(Math.abs(reverse.before-reverse.after)<.025,`Reversing a sheet must preserve the backdrop frame: ${JSON.stringify(reverse)}`);
  assert.equal(reverse.backgroundFilter,'none','Morphing must not repeatedly blur and composite the whole underlying page');
  await page.waitForFunction(()=>!document.querySelector('#sheet').open);
  for(const kind of ['sheet','record','manual']){
   const result=await page.evaluate(async(kind)=>{
    const panel=kind==='sheet'?$('sheet'):$(kind+'-panel');
    if(kind==='sheet')settingsHelp();else if(kind==='record'){go('ledger');openRecord(1)}else beginManual();
    await new Promise(requestAnimationFrame);
    panel.getAnimations({subtree:true}).forEach(a=>{a.pause();a.currentTime=kind==='manual'?300:160});
    const surface=panel._surface;
    const child=panel.querySelector(kind==='sheet'?'.sheet-heading':'.'+kind+'-panel-content');
    const value={rootOpacity:Number(getComputedStyle(panel).opacity),surfaceOpacity:surface?Number(getComputedStyle(surface).opacity):0,contentOpacity:Number(getComputedStyle(child).opacity)};
    FluidMotion.stop(panel);if(kind==='sheet')closeSheet(false);else if(kind==='record')closeRecordPanel(false);else closeManual(false);
    return value;
   },kind);
   assert.equal(result.rootOpacity,1,`${kind}: the shared surface must stay opaque while only contents fade`);
   assert.equal(result.surfaceOpacity,1,`${kind}: moving white surface must not expose the underlying page`);
   assert(result.contentOpacity>0&&result.contentOpacity<1,`${kind}: content should still fade during the morph`);
  }
  assert.deepEqual(errors,[]);console.log(`PASS ${viewport.width}x${viewport.height}: backdrop reversal, opaque shared surfaces, fading contents`);await page.close();
 }}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
