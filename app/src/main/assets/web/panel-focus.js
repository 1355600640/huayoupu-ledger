'use strict';
// Custom sheets retain their existing geometry and motion. This also covers
// Android 11 WebViews without native inert support.
window.PanelFocus=(()=>{
 let active=null,trigger=null;
 const workspace=()=>document.querySelector('.workspace');
 const controls=()=>active?[...active.querySelectorAll('button,input,select,textarea,a[href],[tabindex]')].filter(e=>!e.disabled&&e.tabIndex>=0&&e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden'):[];
 const nestedDialog=()=>!!document.querySelector('dialog[open]');
 function focus(){if(active&&!nestedDialog())active.focus({preventScroll:true})}
 document.addEventListener('focusin',e=>{if(active&&!nestedDialog()&&!active.contains(e.target))focus()},true);
 document.addEventListener('keydown',e=>{
  if(!active||nestedDialog())return;
  if(e.key==='Escape'){e.preventDefault();window.LedgerUI?.back();return}
  if(e.key!=='Tab')return;
  const list=controls(),first=list[0],last=list[list.length-1];
  if(!first){e.preventDefault();focus();return}
  if(e.shiftKey&&(document.activeElement===first||document.activeElement===active)){e.preventDefault();last.focus()}
  else if(!e.shiftKey&&(document.activeElement===last||!active.contains(document.activeElement))){e.preventDefault();first.focus()}
 },true);
 return {
  activate(panel,control){
   active=panel;trigger=control?.isConnected?control:document.activeElement;
   focus();workspace().setAttribute('aria-hidden','true');workspace().inert=true;
  },
  release(panel){
   if(active!==panel)return;
   const previous=trigger;active=null;trigger=null;workspace().inert=false;workspace().removeAttribute('aria-hidden');
   if(previous?.isConnected&&previous.getClientRects().length)previous.focus({preventScroll:true});
  },
 };
})();
