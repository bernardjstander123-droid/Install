const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const test=require("node:test");
const base=path.resolve(__dirname,"..");
const read=p=>fs.readFileSync(path.join(base,p),"utf8");
const manifest=read("app/src/main/AndroidManifest.xml");
const activity=read("app/src/main/java/com/astra/sevenone/MainActivity.java");
const camera=read("app/src/main/java/com/astra/sevenone/AvatarCameraTracker.java");
const mic=read("app/src/main/java/com/astra/sevenone/AvatarMicrophoneTracker.java");
const ui=read("app/src/main/assets/index.html");
test("Camera and microphone require explicit runtime OS permission",()=>{
 assert.match(manifest,/android\.permission\.CAMERA/);
 assert.match(manifest,/android\.permission\.RECORD_AUDIO/);
 assert.match(activity,/requestPermissions\(new String\[\]\{Manifest\.permission\.CAMERA\}/);
 assert.match(activity,/requestPermissions\(new String\[\]\{Manifest\.permission\.RECORD_AUDIO\}/);
 assert.match(activity,/checkSelfPermission\(Manifest\.permission\.CAMERA\)/);
 assert.match(activity,/checkSelfPermission\(Manifest\.permission\.RECORD_AUDIO\)/);
 assert.match(ui,/id="cameraToggle"/);
 assert.match(ui,/id="micToggle"/);
});
test("Sensors must stop when app pauses",()=>{
 assert.match(activity,/@Override protected void onPause\(\)/);
 assert.match(activity,/avatarCamera\.stop\(\)/);
 assert.match(activity,/avatarMicrophone\.stop\(\)/);
 assert.match(camera,/public void stop\(\)/);
 assert.match(mic,/public void stop\(\)/);
});
test("Only numeric motion and energy are exposed to the avatar, not raw frames/audio",()=>{
 assert.match(camera,/InputImage\.fromMediaImage/);
 assert.match(camera,/listener\.onTracking\(payload\)/);
 assert.match(mic,/onLevel\(level\)/);
 assert.doesNotMatch(camera,/https?:\/\//);
 assert.doesNotMatch(mic,/https?:\/\//);
 assert.match(ui,/window\.astraTracking/);
 assert.match(ui,/window\.astraMicLevel/);
});
test("Trackers have bounded real-time rate/size settings",()=>{
 assert.match(camera,/FRAME_INTERVAL_MS = 240L/);
 assert.match(camera,/ImageReader\.newInstance\(640, 480/);
 assert.match(mic,/short\[\] samples=new short\[1600\]/);
});
