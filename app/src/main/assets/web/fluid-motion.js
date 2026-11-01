'use strict';
// Animate an independent surface: text is never scaled from a tiny button.
window.FluidMotion=(()=>{
 const damping=t=>(1-(1+8*t)*Math.exp(-8*t))/(1-9*Math.exp(-8));
 const box=r=>({left:r.left,top:r.top,width:r.width,height:r.height});
 const radius=r=>`${Math.min(r.width,r.height)/2}px`;
 const corners=value=>{const a=value.split(/\s+/).map(Number.parseFloat);return [a[0],a[1]??a[0],a[2]??a[0],a[3]??a[1]??a[0]]};
 function stop(panel){
  panel._morph?.cancel();panel._fade?.cancel();panel._shape?.cancel();
  panel._contentAnimations?.forEach(animation=>animation.cancel());panel._contentAnimations=null;
  panel._morph=panel._fade=panel._shape=null;
  panel._surface?.remove();panel._surface=null;panel.classList.remove('fluid-active','fluid-pending');
 }
 function run(panel,origin,reverse,done,shade){
  const interrupted=!!panel._morph,style=getComputedStyle(panel);
  const content=[...panel.children].filter(child=>child!==panel._surface);
  const contentOpacity=content[0]?getComputedStyle(content[0]).opacity:'1';
  const presented=interrupted?{opacity:contentOpacity,clipPath:style.clipPath}:null;
  const current=panel._surface?box(panel._surface.getBoundingClientRect()):null;
  const currentRadius=panel._surface?getComputedStyle(panel._surface).borderRadius:null;
  const shadeStyle=shade?getComputedStyle(shade):null;
  const shadeOpacity=shadeStyle?(panel._fade?shadeStyle.opacity:reverse?'1':'0'):0;
  const background=panel._surface?.style.background||style.background;
  stop(panel);
  if(matchMedia('(prefers-reduced-motion: reduce)').matches||!panel.animate){done();return}
  const target=box(panel.getBoundingClientRect()),source=origin&&origin.width>0&&origin.height>0?box(origin):{left:innerWidth/2-72,top:innerHeight-76,width:144,height:48};
  const finalRadius=getComputedStyle(panel).borderRadius;
  const begin=current||(reverse?target:source),end=reverse?source:target;
  const sourceRadius=origin?.radius!=null?`${origin.radius}px`:radius(source);
  const beginRadius=currentRadius||(reverse?finalRadius:sourceRadius),endRadius=reverse?sourceRadius:finalRadius;
  const clip=(r,round)=>`inset(${r.top-target.top}px ${target.left+target.width-r.left-r.width}px ${target.top+target.height-r.top-r.height}px ${r.left-target.left}px round ${round})`;
  const surface=document.createElement('div');surface.className='fluid-surface';surface.setAttribute('aria-hidden','true');panel.append(surface);panel._surface=surface;panel.classList.add('fluid-active');
  surface.style.background=background;
  const frames=[],reveals=[],startCorners=corners(beginRadius),endCorners=corners(endRadius);
  // Forms appear after expansion, and fade before shrinking, so fields are never cut through.
  const staged=panel.id==='manual-panel'||!!panel.querySelector('#account-form');
  for(let i=0;i<=24;i++){
   const t=i/24,p=damping(t),r={left:begin.left+(end.left-begin.left)*p,top:begin.top+(end.top-begin.top)*p,width:begin.width+(end.width-begin.width)*p,height:begin.height+(end.height-begin.height)*p};
   const round=startCorners.map((n,index)=>`${n+(endCorners[index]-n)*p}px`).join(' ');
   frames.push({offset:t,left:`${r.left}px`,top:`${r.top}px`,width:`${r.width}px`,height:`${r.height}px`,borderRadius:round});
   const reveal=staged?(reverse?Math.min(1,t/.35):Math.max(0,(t-.55)/.45)):p,start=Number(presented?.opacity??(reverse?1:staged?0:.05));
   reveals.push({offset:t,clipPath:clip(r,round),opacity:reverse?start*(1-reveal):start+(1-start)*reveal});
  }
  if(presented)reveals[0]={...reveals[0],...presented};
  const timing={duration:reverse?280:420,easing:'linear',fill:'both'};
  // Geometry follows a critically damped curve, with no overshoot at the viewport edge.
  const shape=surface.animate(frames,timing),easing='cubic-bezier(.2,.8,.2,1)';
  // The surface stays opaque. Fading its parent would expose the ledger through
  // the whole panel, then abruptly replace it with an opaque background at rest.
  const animation=panel.animate(reveals.map(({opacity,...frame})=>frame),timing);
  panel._contentAnimations=content.map(child=>child.animate(reveals.map(({offset,opacity})=>({offset,opacity})),timing));
  const shadeFrames=[{opacity:shadeOpacity},{opacity:reverse?0:1}];
  // Modal backdrops use a CSS transition: older Android WebViews ignore
  // WAAPI pseudoElement targets. Transitions reverse from the presented frame.
  const fade=shade?shade.animate(shadeFrames,{...timing,easing}):null;
  panel._shape=shape;panel._morph=animation;panel._fade=fade;
  animation.onfinish=()=>{if(panel._morph!==animation)return;stop(panel);done()};
 }
 return {run,stop};
})();
