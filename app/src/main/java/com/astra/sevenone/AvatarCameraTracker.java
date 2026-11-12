package com.astra.sevenone;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.PointF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import android.content.res.Configuration;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import org.json.JSONObject;
import java.util.Arrays;
import java.util.List;

/**
 * Private, foreground-only face and upper-body motion tracking.
 * Raw camera frames stay on-device, are never stored, and are not sent to chat APIs.
 * This class emits only a small, transient set of normalized motion measurements.
 */
public final class AvatarCameraTracker {
    public interface Listener {
        void onStatus(String text);
        void onTracking(JSONObject frame);
    }
    private static final long FRAME_INTERVAL_MS = 240L; // low heat / ~4 FPS ML inference
    private final Activity activity;
    private final Listener listener;
    private final CameraManager manager;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice device;
    private CameraCaptureSession captureSession;
    private ImageReader reader;
    private FaceDetector faceDetector;
    private PoseDetector poseDetector;
    private volatile boolean active = false;
    private volatile boolean busy = false;
    private volatile long lastFrameMs = 0;
    private String cameraId;
    private int sensorOrientation = 90;
    private boolean isFront = true;

    public AvatarCameraTracker(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    }

    public boolean isActive() { return active; }

    public void start() {
        if (active) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            listener.onStatus("Camera permission required.");
            return;
        }
        if (manager == null) {
            listener.onStatus("No camera service available.");
            return;
        }
        try {
            cameraId = null;
            for (String id : manager.getCameraIdList()) {
                Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id; isFront = true; break;
                }
            }
            // Face/body tracking is intended for selfie camera; refuse silent rear camera use.
            if (cameraId == null) { listener.onStatus("A front camera is required for face/body tracking."); return; }
            Integer orientation = manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (orientation != null) sensorOrientation = orientation;
            FaceDetectorOptions faceOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .enableTracking()
                .build();
            faceDetector = FaceDetection.getClient(faceOptions);
            poseDetector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE).build());
            cameraThread = new HandlerThread("AstraAvatarCamera");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
            // Camera2 YUV stream: no hidden recording, no photos, no network access.
            reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 3);
            active = true;
            busy = false;
            reader.setOnImageAvailableListener(this::onImage, cameraHandler);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    if (!active) { camera.close(); return; }
                    device = camera;
                    try { beginCapture(camera); }
                    catch (Exception ex) { listener.onStatus("Camera preview could not start."); stop(); }
                }
                @Override public void onDisconnected(CameraDevice camera) {
                    camera.close(); device = null; listener.onStatus("Camera disconnected."); stop();
                }
                @Override public void onError(CameraDevice camera, int error) {
                    camera.close(); device = null; listener.onStatus("Camera error (" + error + ")."); stop();
                }
            }, cameraHandler);
            listener.onStatus("Front camera active. Move back to show your shoulders and arms.");
        } catch (SecurityException ignored) {
            listener.onStatus("Camera permission denied."); stop();
        } catch (Exception ignored) {
            listener.onStatus("Unable to initialize face/body tracking."); stop();
        }
    }

    private void beginCapture(CameraDevice camera) throws CameraAccessException {
        if (reader == null) return;
        camera.createCaptureSession(Arrays.asList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
            @Override public void onConfigured(CameraCaptureSession session) {
                if (!active || device == null) { session.close(); return; }
                captureSession = session;
                try {
                    CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    request.addTarget(reader.getSurface());
                    request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                    session.setRepeatingRequest(request.build(), null, cameraHandler);
                    listener.onStatus("Tracking active • Camera on • Frames stay on device");
                } catch (Exception ex) {
                    listener.onStatus("Camera cannot deliver tracking frames."); stop();
                }
            }
            @Override public void onConfigureFailed(CameraCaptureSession session) {
                listener.onStatus("Camera capture configuration failed."); stop();
            }
        }, cameraHandler);
    }

    private int rotationDegrees() {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int deviceDegrees;
        switch (rotation) {
            case Surface.ROTATION_90: deviceDegrees = 90; break;
            case Surface.ROTATION_180: deviceDegrees = 180; break;
            case Surface.ROTATION_270: deviceDegrees = 270; break;
            default: deviceDegrees = 0;
        }
        return isFront ? (sensorOrientation + deviceDegrees) % 360
            : (sensorOrientation - deviceDegrees + 360) % 360;
    }

    private void onImage(ImageReader stream) {
        Image frame;
        try { frame = stream.acquireLatestImage(); }
        catch (IllegalStateException ignored) { return; }
        if (frame == null) return;
        long now = SystemClock.elapsedRealtime();
        if (!active || busy || now-lastFrameMs < FRAME_INTERVAL_MS) {
            frame.close(); return;
        }
        lastFrameMs=now;
        busy=true;
        try {
            final InputImage img = InputImage.fromMediaImage(frame, rotationDegrees());
            if (faceDetector == null || poseDetector == null) { busy=false; frame.close(); return; }
            faceDetector.process(img)
                .addOnSuccessListener(faces -> {
                    if (!active) { closeFrame(frame); return; }
                    try {
                        final JSONObject payload = new JSONObject();
                        writeFace(payload, faces, img.getWidth(), img.getHeight());
                        poseDetector.process(img)
                            .addOnSuccessListener(pose -> {
                                if (!active) return;
                                writePose(payload, pose, img.getWidth(), img.getHeight());
                                listener.onTracking(payload);
                            })
                            .addOnFailureListener(error -> { if(active) listener.onTracking(payload); })
                            .addOnCompleteListener(task -> closeFrame(frame));
                    } catch (Exception ex) { closeFrame(frame); }
                })
                .addOnFailureListener(error -> closeFrame(frame));
        } catch (Exception ignored) { closeFrame(frame); }
    }

    private void closeFrame(Image frame) {
        try { frame.close(); } catch(Exception ignored) { }
        busy = false;
    }

    private static double clamp(double v) { return Math.max(-1, Math.min(1, v)); }
    private static void writeFace(JSONObject out, List<Face> faces, int width, int height) {
        try {
            if (faces.isEmpty()) { out.put("face", false); return; }
            Face f = faces.get(0);
            Rect rect = f.getBoundingBox();
            out.put("face", true);
            out.put("yaw", clamp(f.getHeadEulerAngleY()/40.0));
            out.put("pitch", clamp(f.getHeadEulerAngleX()/35.0));
            out.put("roll", clamp(f.getHeadEulerAngleZ()/40.0));
            out.put("faceX", clamp((rect.centerX()/(double)Math.max(1,width) - .5)*2));
            out.put("faceY", clamp((rect.centerY()/(double)Math.max(1,height) - .5)*2));
        } catch (Exception ignored) { }
    }

    private static PointF pt(Pose pose, int id) {
        PoseLandmark mark=pose.getPoseLandmark(id);
        if(mark==null || mark.getInFrameLikelihood()<.45f) return null;
        return mark.getPosition();
    }
    private static void writePose(JSONObject out, Pose pose, int width, int height) {
        try {
            PointF leftS=pt(pose,PoseLandmark.LEFT_SHOULDER);
            PointF rightS=pt(pose,PoseLandmark.RIGHT_SHOULDER);
            PointF leftW=pt(pose,PoseLandmark.LEFT_WRIST);
            PointF rightW=pt(pose,PoseLandmark.RIGHT_WRIST);
            boolean body=leftS!=null&&rightS!=null;
            out.put("body",body);
            if(!body)return;
            double shoulderWidth=Math.max(50.0, Math.abs(leftS.x-rightS.x));
            out.put("lean",clamp((rightS.y-leftS.y)/shoulderWidth));
            if(leftW!=null) out.put("leftArm",clamp((leftS.y-leftW.y)/(shoulderWidth*1.5)));
            if(rightW!=null) out.put("rightArm",clamp((rightS.y-rightW.y)/(shoulderWidth*1.5)));
            PointF leftH=pt(pose,PoseLandmark.LEFT_HIP);
            PointF rightH=pt(pose,PoseLandmark.RIGHT_HIP);
            if(leftH!=null&&rightH!=null) {
                double shoulders=(leftS.x+rightS.x)/2,hips=(leftH.x+rightH.x)/2;
                out.put("sway",clamp((shoulders-hips)/shoulderWidth));
            }
        } catch (Exception ignored) { }
    }

    public void stop() {
        active=false;
        busy=false;
        // Release sensors and camera only on the UI thread; callbacks can arrive after stop.
        if (captureSession != null) { try { captureSession.close(); } catch(Exception ignored){} captureSession=null; }
        if (device != null) { try { device.close(); } catch(Exception ignored){} device=null; }
        if (reader != null) { try { reader.close(); } catch(Exception ignored){} reader=null; }
        if (faceDetector != null) { try { faceDetector.close(); } catch(Exception ignored){} faceDetector=null; }
        if (poseDetector != null) { try { poseDetector.close(); } catch(Exception ignored){} poseDetector=null; }
        if (cameraThread != null) { cameraThread.quitSafely(); cameraThread=null; cameraHandler=null; }
    }
}
