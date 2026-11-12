/* Astra 7.1: self-contained, offline-capable WebGL humanoid. No libraries or camera access. */
(function (root) {
"use strict";
const canvas = root.document && root.document.getElementById("astraAvatarCanvas");
if (!canvas) return;
const fallback = root.document.getElementById("avatarFallback");
const reduceMotion = root.matchMedia && root.matchMedia("(prefers-reduced-motion: reduce)").matches;
const state = {
  mode: "idle", speaking: false, tiltX: 0, tiltY: 0, tiltZ: 0,
  pointerX: 0, pointerY: 0, dragX: 0, dragY: 0, waveUntil: 0,
  lastTouch: 0, interactionUntil: 0, quality: "auto"
};
let gl;
try {
  gl = canvas.getContext("webgl", {
    alpha: true, antialias: false, powerPreference: "low-power",
    depth: true, stencil: false, premultipliedAlpha: false
  }) || canvas.getContext("experimental-webgl");
} catch (ignored) { gl = null; }
function unavailable() {
  canvas.style.display = "none";
  if (fallback) fallback.style.display = "grid";
  const status = root.document.getElementById("avatarStatus");
  if (status) status.textContent = "3D unavailable • Touch controls still work";
}
function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
function lerp(a, b, t) { return a + (b-a)*t; }
function timeNow() { return root.performance ? root.performance.now() : Date.now(); }
function setStatus(s) {
 const el = root.document.getElementById("avatarStatus");
 if (el) el.textContent = s;
}
function motion(x, y, z) {
  if (![x,y,z].every(Number.isFinite)) return;
  state.tiltX = clamp(x/10, -1, 1);
  state.tiltY = clamp(y/10, -1, 1);
  state.tiltZ = clamp(z/10, -1, 1);
}
function wave() {
  state.waveUntil = timeNow() + 1500;
  state.interactionUntil = timeNow() + 2200;
  setStatus("👋 Hello, human");
}
function setMode(mode) {
  const valid = ["idle", "thinking", "speaking", "error", "listening"];
  state.mode = valid.includes(mode) ? mode : "idle";
  if (state.mode === "thinking") setStatus("Thinking with you");
  else if (state.mode === "speaking") setStatus("Speaking");
  else if (state.mode === "error") setStatus("Connection needs attention");
  else if (state.mode === "listening") setStatus("Listening");
  else setStatus("Move your phone • Touch to interact");
}
function setSpeaking(v) { state.speaking = Boolean(v); setMode(v ? "speaking" : "idle"); }
function setQuality(v) { state.quality = v === "low" ? "low" : "auto"; if (gl) resize(); }
const api = {motion, wave, setMode, setSpeaking, setQuality};
root.astraAvatar = api;
const stage = root.document.getElementById("avatarStage") || canvas;
let drag = null;
stage.addEventListener("pointerdown", function(e) {
  drag = {x:e.clientX,y:e.clientY,start:timeNow(),distance:0};
  if(stage.setPointerCapture) try{stage.setPointerCapture(e.pointerId)}catch(ignored){}
});
stage.addEventListener("pointermove", function(e) {
  if (!drag) return;
  const dx=e.clientX-drag.x, dy=e.clientY-drag.y;
  drag.distance += Math.abs(dx)+Math.abs(dy);
  state.dragX=clamp(state.dragX+dx/180,-1.2,1.2);
  state.dragY=clamp(state.dragY+dy/220,-.8,.8);
  state.pointerX=clamp((e.clientX-stage.getBoundingClientRect().left)/Math.max(1,stage.clientWidth)*2-1,-1,1);
  state.pointerY=clamp((e.clientY-stage.getBoundingClientRect().top)/Math.max(1,stage.clientHeight)*2-1,-1,1);
  state.interactionUntil=timeNow()+2000;
  drag.x=e.clientX;drag.y=e.clientY;
});
stage.addEventListener("pointerup", function(e) {
  if(!drag)return;
  const held=timeNow()-drag.start;
  if(held<350 && drag.distance<14)wave();
  drag=null;
});
stage.addEventListener("pointercancel",()=>{drag=null;});
if (!gl) { unavailable(); return; }
canvas.addEventListener("webglcontextlost", function(e){e.preventDefault();unavailable();}, false);

const vertexSource = `
attribute vec3 aPosition;
attribute vec3 aNormal;
uniform mat4 uWorld;
uniform mat4 uViewProjection;
varying vec3 vNormal;
varying vec3 vWorld;
void main(){
 vec4 p=uWorld*vec4(aPosition,1.0);
 vWorld=p.xyz;
 vNormal=normalize(mat3(uWorld)*aNormal);
 gl_Position=uViewProjection*p;
}`;
const fragmentSource = `
precision mediump float;
uniform vec3 uColor;
uniform float uAlpha;
uniform float uEmission;
varying vec3 vNormal;
varying vec3 vWorld;
void main(){
 vec3 n=normalize(vNormal);
 vec3 L=normalize(vec3(-0.42,0.85,1.0));
 float diffuse=max(dot(n,L),0.0);
 float rim=pow(1.0-max(dot(n,normalize(vec3(0.0,0.18,1.0))),0.0),2.5);
 float shine=pow(max(dot(reflect(-L,n),normalize(vec3(0.0,0.0,1.0))),0.0),28.0);
 vec3 result=uColor*(0.26+0.61*diffuse)+vec3(0.44,0.82,1.0)*rim*0.20;
 result+=vec3(0.72,0.91,1.0)*shine*0.36+uColor*uEmission;
 gl_FragColor=vec4(result,uAlpha);
}`;
function shader(type, src) {
  const item = gl.createShader(type);gl.shaderSource(item,src);gl.compileShader(item);
  if(!gl.getShaderParameter(item,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(item));
  return item;
}
let program;
try {
  program=gl.createProgram();
  gl.attachShader(program,shader(gl.VERTEX_SHADER,vertexSource));
  gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragmentSource));
  gl.linkProgram(program);
  if(!gl.getProgramParameter(program,gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(program));
} catch (e) { unavailable(); return; }
gl.useProgram(program);
const attrs={p:gl.getAttribLocation(program,"aPosition"),n:gl.getAttribLocation(program,"aNormal")};
const unif={world:gl.getUniformLocation(program,"uWorld"),vp:gl.getUniformLocation(program,"uViewProjection"),color:gl.getUniformLocation(program,"uColor"),alpha:gl.getUniformLocation(program,"uAlpha"),emission:gl.getUniformLocation(program,"uEmission")};
gl.enable(gl.DEPTH_TEST);
gl.depthFunc(gl.LEQUAL);
gl.enable(gl.BLEND);
gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);
gl.disable(gl.CULL_FACE);
function meshFromGrid(rows, columns, fn) {
 const vertices=[], indices=[];
 for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++){
   const item=fn(i/rows,j/columns);
   vertices.push(...item.slice(0,6));
 }
 for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){
   const a=i*(columns+1)+j, b=a+columns+1;
   indices.push(a,b,a+1,b,b+1,a+1);
 }
 const vb=gl.createBuffer(),ib=gl.createBuffer();
 gl.bindBuffer(gl.ARRAY_BUFFER,vb);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(vertices),gl.STATIC_DRAW);
 gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ib);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint16Array(indices),gl.STATIC_DRAW);
 return {vb,ib,count:indices.length};
}
const sphere=meshFromGrid(18,28,(u,v)=>{
 const th=u*Math.PI, ph=v*Math.PI*2;
 const x=Math.sin(th)*Math.cos(ph),y=Math.cos(th),z=Math.sin(th)*Math.sin(ph);
 return [x,y,z,x,y,z];
});
const torus=meshFromGrid(72,9,(u,v)=>{
 const t=u*Math.PI*2, p=v*Math.PI*2;
 const r=0.035, major=1;
 const cs=Math.cos(t),sn=Math.sin(t),c=Math.cos(p),s=Math.sin(p);
 return [(major+r*c)*cs,(major+r*c)*sn,r*s,c*cs,c*sn,s];
});
function I(){return new Float32Array([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]);}
function mul(a,b) {const out=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++){let n=0;for(let k=0;k<4;k++)n+=a[k*4+r]*b[c*4+k];out[c*4+r]=n;}return out;}
function T(x,y,z){const m=I();m[12]=x;m[13]=y;m[14]=z;return m;}
function S(x,y,z){const m=I();m[0]=x;m[5]=y;m[10]=z;return m;}
function RX(a){const m=I(),c=Math.cos(a),s=Math.sin(a);m[5]=c;m[6]=s;m[9]=-s;m[10]=c;return m;}
function RY(a){const m=I(),c=Math.cos(a),s=Math.sin(a);m[0]=c;m[2]=-s;m[8]=s;m[10]=c;return m;}
function RZ(a){const m=I(),c=Math.cos(a),s=Math.sin(a);m[0]=c;m[1]=s;m[4]=-s;m[5]=c;return m;}
function combine(parent,...parts){let m=parent;for(const part of parts)m=mul(m,part);return m;}
function projection(fov, aspect, near, far){let m=new Float32Array(16), f=1/Math.tan(fov/2);m[0]=f/aspect;m[5]=f;m[10]=(far+near)/(near-far);m[11]=-1;m[14]=2*far*near/(near-far);return m;}
let vp=I();
function resize(){
  const rect=canvas.getBoundingClientRect(),scale=state.quality==="low"||reduceMotion?1:Math.min(root.devicePixelRatio||1,1.45);
  const w=Math.min(860,Math.max(2,Math.floor(rect.width*scale))),h=Math.min(610,Math.max(2,Math.floor(rect.height*scale)));
  if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;gl.viewport(0,0,w,h);}
  vp=mul(projection(Math.PI/3, w/h,.1,25),T(0,-.08,-5.2));
}
root.addEventListener("resize",resize,{passive:true});
resize();
function renderMesh(mesh,mat,color,alpha,emission) {
  if(alpha<.999) gl.depthMask(false);
  else gl.depthMask(true);
  gl.bindBuffer(gl.ARRAY_BUFFER,mesh.vb);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,mesh.ib);
  gl.enableVertexAttribArray(attrs.p);gl.enableVertexAttribArray(attrs.n);
  gl.vertexAttribPointer(attrs.p,3,gl.FLOAT,false,24,0);
  gl.vertexAttribPointer(attrs.n,3,gl.FLOAT,false,24,12);
  gl.uniformMatrix4fv(unif.world,false,mat);
  gl.uniform3fv(unif.color,color);
  gl.uniform1f(unif.alpha,alpha);
  gl.uniform1f(unif.emission,emission||0);
  gl.drawElements(gl.TRIANGLES,mesh.count,gl.UNSIGNED_SHORT,0);
  if(alpha<.999)gl.depthMask(true);
}
function orb(parent,x,y,z,sx,sy,sz,color,glow=0,opacity=1) {
 renderMesh(sphere,combine(parent,T(x,y,z),S(sx,sy,sz)),color,opacity,glow);
}
function ring(parent,x,y,z,sx,sy,sz,color,glow=0,opacity=1) {
 renderMesh(torus,combine(parent,T(x,y,z),S(sx,sy,sz)),color,opacity,glow);
}
const dark=[.025,.068,.17],metal=[.13,.31,.52],ice=[.36,.78,.96],white=[.70,.94,1.0],skin=[.19,.43,.65],ink=[.035,.12,.20],violet=[.34,.32,.67];
let smoothX=0,smoothY=0,smoothZ=0,pX=0,pY=0,last=0,frame=0;
function draw(t) {
 const secs=t*.001;
 const active=t<state.interactionUntil;
 const desiredY=clamp(state.tiltX*.28+state.dragX*.48,-.7,.7);
 const desiredX=clamp(-state.tiltY*.15+state.dragY*.30,-.44,.44);
 const desiredZ=clamp(state.tiltX*.10,-.13,.13);
 smoothY=lerp(smoothY,desiredY,.075);smoothX=lerp(smoothX,desiredX,.075);smoothZ=lerp(smoothZ,desiredZ,.075);
 pX=lerp(pX,active?state.pointerX:0,.09);pY=lerp(pY,active?state.pointerY:0,.09);
 const busy=state.mode==="thinking",speaking=state.speaking||state.mode==="speaking";
 const bob=reduceMotion?0:Math.sin(secs*1.7)*.038;
 const pulse=busy?0.5+0.5*Math.sin(secs*8):0.5+0.5*Math.sin(secs*2.3);
 const waveActive=t<state.waveUntil;
 const wavePhase=waveActive?Math.sin((state.waveUntil-t)*.014)*.30:0;
 gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
 const rootM=combine(I(),T(0,bob,0),RY(smoothY),RX(smoothX*.35),RZ(smoothZ));
 // Hero torso, shoulders, central energy drive, collar.
 orb(rootM,0,-.84,-.08,.74,.88,.45,dark);
 orb(rootM,0,-.70,.08,.60,.67,.44,metal,.035);
 orb(rootM,0,-1.22,.18,.34,.42,.20,ink);
 orb(rootM,-.61,-.30,-.06,.41,.32,.40,metal,.02);
 orb(rootM,.61,-.30,-.06,.41,.32,.40,metal,.02);
 orb(rootM,0,-.17,.08,.34,.19,.31,ink);
 ring(rootM,0,-.33,.20,.37,.13,.35,ice,.35,.88);
 orb(rootM,0,-.67,.48,.18,.23,.08,white,.25+.42*pulse,.9);
 orb(rootM,0,-.67,.43,.30,.34,.12,ice,.06,.36);
 ring(rootM,0,-.68,.48,.37,.41,.20,ice,.3,.74);
 // Arms: individually pivot at shoulders; wave animates the left hand.
 for(const sign of [-1,1]){
   const waving=sign===1&&waveActive;
   const arm=combine(rootM,T(sign*.78,-.37,.01),RZ(sign*(waving?-.85+wavePhase:.13+Math.sin(secs*.8)*.025)));
   orb(arm,0,-.19,0,.25,.43,.27,metal);
   orb(arm,0,-.57,.055,.22,.27,.24,dark);
   ring(arm,0,-.59,.12,.21,.16,.19,ice,.24,.88);
   orb(arm,0,-.88,.06,.20,.19,.21,skin,.12);
   orb(arm,0,-.90,.22,.09,.12,.05,ice,.14,.90);
 }
 // Neck.
 orb(rootM,0,.17,0,.22,.33,.22,ink);
 ring(rootM,0,.12,.02,.33,.16,.32,ice,.38,.83);
 // Head follows hand motion and the phone's acceleration.
 const head=combine(rootM,T(0,.91,.09),RY(pX*.26+Math.sin(secs*.5)*.026),RX(-pY*.20));
 orb(head,0,0,0,.51,.60,.44,dark,.02);
 orb(head,0,.02,.13,.47,.53,.40,skin,.015);
 orb(head,0,.01,.39,.40,.43,.18,ink);
 orb(head,0,.015,.51,.36,.38,.07,ice,.13,.26); // translucent visor
 // Ears / flaring cranial lenses.
 orb(head,-.50,-.025,.02,.16,.27,.18,metal);
 orb(head,.50,-.025,.02,.16,.27,.18,metal);
 orb(head,-.55,-.015,.10,.07,.17,.09,ice,.28);
 orb(head,.55,-.015,.10,.07,.17,.09,ice,.28);
 // Eyes track horizontal pointer without a camera.
 for(const sign of [-1,1]){
   orb(head,sign*.19,.12,.539,.13,.08,.05,white,.70+.28*pulse);
   orb(head,sign*.19+pX*.026,.12-pY*.017,.586,.052,.046,.02,ice,.75);
   orb(head,sign*.19,.24,.51,.17,.023,.025,metal);
 }
 // Voice-reactive mouth and cheek light bars.
 orb(head,0,-.19,.554,.14,speaking?.042+Math.abs(Math.sin(secs*17))*.055:.025,.025,white,.28);
 orb(head,-.31,-.16,.48,.062,.018,.03,ice,.45);
 orb(head,.31,-.16,.48,.062,.018,.03,ice,.45);
 orb(head,0,-.45,.18,.22,.11,.16,dark);
 // Crown and floating halo.
 orb(head,0,.55,-.055,.38,.12,.31,metal);
 orb(head,0,.595,.015,.22,.045,.22,ice,.4);
 ring(rootM,0,.95,-.35,.82,.90,.82,ice,.30+.32*pulse,.62);
 ring(rootM,0,.93,-.38,.98,1.04,.98,violet,.12,.3);
 // Low-frequency energy orbit in front.
 ring(rootM,0,-1.8,-.14,.75,.12,.60,ice,.24,.42);
}
function tick(t) {
 root.requestAnimationFrame(tick);
 if(root.document.hidden)return;
 const fps=state.quality==="low"||reduceMotion?18:30;
 if(t-last<1000/fps)return;
 last=t;
 if(++frame%100===0)resize();
 draw(t);
}
resize();
root.requestAnimationFrame(tick);
setMode("idle");
})(window);
