const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const source = fs.readFileSync(path.join(root, "app/src/main/assets/avatar.js"), "utf8");
const html = fs.readFileSync(path.join(root, "app/src/main/assets/index.html"), "utf8");

function mount({webgl=true,reducedMotion=false}={}){
  let frames = [];
  const handlers = {};
  let drawn=0;
  const labels={};
  const gl = {
    VERTEX_SHADER:35633, FRAGMENT_SHADER:35632, COMPILE_STATUS:35713,
    LINK_STATUS:35714, ARRAY_BUFFER:34962, ELEMENT_ARRAY_BUFFER:34963,
    STATIC_DRAW:35044, DEPTH_TEST:2929, LEQUAL:515, BLEND:3042,
    SRC_ALPHA:770, ONE_MINUS_SRC_ALPHA:771, CULL_FACE:2884,
    FLOAT:5126, TRIANGLES:4, UNSIGNED_SHORT:5123,
    COLOR_BUFFER_BIT:16384, DEPTH_BUFFER_BIT:256,
    createShader(){return {};},shaderSource(){},compileShader(){},
    getShaderParameter(){return true;},createProgram(){return {};},
    attachShader(){},linkProgram(){},getProgramParameter(){return true;},
    useProgram(){},getAttribLocation(){return 0;},getUniformLocation(){return {};},
    enable(){},disable(){},depthFunc(){},blendFunc(){},
    createBuffer(){return {};},bindBuffer(){},bufferData(){},
    viewport(){},clearColor(){},clear(){},depthMask(){},
    enableVertexAttribArray(){},vertexAttribPointer(){},
    uniformMatrix4fv(){},uniform3fv(){},uniform1f(){},
    drawElements(){drawn++;}
  };
  const canvas={
    style:{},width:0,height:0,
    getContext(){return webgl?gl:null;},
    getBoundingClientRect(){return {width:300,height:200,left:0,top:0};},
    addEventListener(event,fn){handlers["canvas:"+event]=fn;}
  };
  const stage={
    clientWidth:300,clientHeight:200,
    getBoundingClientRect(){return {left:0,top:0,width:300,height:200};},
    setPointerCapture(){},
    addEventListener(event,fn){handlers[event]=fn;}
  };
  const fallback={style:{display:"none"}};
  const avatarStatus={textContent:""};
  const dom={astraAvatarCanvas:canvas,avatarStage:stage,avatarFallback:fallback,avatarStatus};
  const win={
    document:{hidden:false,getElementById(name){return dom[name]||null;}},
    matchMedia(){return {matches:reducedMotion};},
    devicePixelRatio:2,
    performance:{now(){return 1000;}},
    addEventListener(){},
    requestAnimationFrame(fn){frames.push(fn);}
  };
  vm.runInNewContext(source,{window:win}, {timeout:2000});
  return {win,handlers,canvas,fallback,avatarStatus,
    advance(time){const cb=frames.shift(); assert.ok(cb,"scheduled animation frame");cb(time);},
    drawn(){return drawn;}
  };
}
test("HTML embeds the local 3D canvas, gestures and voice controls",()=>{
  assert.match(html,/id="astraAvatarCanvas"/);
  assert.match(html,/src="avatar.js"/);
  for (const id of ["avatarStage","avatarStatus","avatarVoice","avatarWave","motion"]) {
    assert.ok(html.includes('id="'+id+'"'),id);
  }
  const inline = html.match(/<script>([\s\S]*?)<\/script>/);
  assert.ok(inline,"expected inline chat app script");
  new vm.Script(inline[1],{filename:"chat-ui.js"});
});
test("WebGL avatar initializes and draws a 3D mesh",()=>{
  const obj=mount();
  assert.equal(typeof obj.win.astraAvatar.motion,"function");
  assert.equal(typeof obj.win.astraAvatar.wave,"function");
  obj.advance(100);
  assert.ok(obj.drawn()>20,"expected articulated 3D geometry");
  assert.ok(obj.canvas.width>0&&obj.canvas.height>0);
});
test("Touch and motion update avatar without requiring a camera",()=>{
  const obj=mount();
  obj.win.astraAvatar.motion(3,5,8);
  obj.handlers.pointerdown({clientX:40,clientY:35,pointerId:1});
  obj.handlers.pointermove({clientX:75,clientY:62});
  obj.handlers.pointerup({clientX:75,clientY:62});
  obj.win.astraAvatar.setMode("thinking");
  assert.equal(obj.avatarStatus.textContent,"Thinking with you");
  obj.win.astraAvatar.setSpeaking(true);
  assert.equal(obj.avatarStatus.textContent,"Speaking");
  assert.equal(typeof obj.win.astraAvatar.setTracking,"function");
  assert.equal(typeof obj.win.astraAvatar.setMicLevel,"function");
  obj.win.astraAvatar.setTracking({
    face:true,body:true,yaw:.25,pitch:.18,roll:-.2,lean:.35,leftArm:.7,rightArm:.2
  });
  obj.win.astraAvatar.setMicLevel(.4);
  obj.win.astraAvatar.wave();
  assert.match(obj.avatarStatus.textContent,/Hello/);
  obj.advance(100);
  assert.ok(obj.drawn()>20);
});
test("If WebGL is unavailable, a safe fallback stays interactive",()=>{
  const obj=mount({webgl:false});
  assert.equal(obj.fallback.style.display,"grid");
  assert.equal(obj.canvas.style.display,"none");
  assert.doesNotThrow(()=>obj.win.astraAvatar.setQuality("low"));
  assert.doesNotThrow(()=>obj.win.astraAvatar.wave());
});
test("Motion reduction still produces renderable meshes",()=>{
  const obj=mount({reducedMotion:true});
  obj.advance(100);assert.ok(obj.drawn()>20);
});
