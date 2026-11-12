package com.astra.sevenone;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Optional audio-energy tracking for the avatar mouth. Never stores speech,
 * never transcribes words, and never sends mic samples to an AI provider.
 */
public final class AvatarMicrophoneTracker {
    public interface Listener {
        void onStatus(String status);
        void onLevel(double level);
    }
    private final Activity activity;
    private final Listener listener;
    private volatile boolean active;
    private AudioRecord recorder;
    private Thread thread;

    public AvatarMicrophoneTracker(Activity activity, Listener listener) {
        this.activity=activity;
        this.listener=listener;
    }

    public boolean isActive() { return active; }

    public void start() {
        if(active)return;
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            listener.onStatus("Microphone permission required.");
            return;
        }
        try {
            int rate=16000;
            int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
            if(min==AudioRecord.ERROR||min==AudioRecord.ERROR_BAD_VALUE)
                throw new IllegalStateException("Audio buffer unsupported");
            int length=Math.max(min,rate/5*2);
            recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,
                AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,length);
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)
                throw new IllegalStateException("Mic initialization failed");
            recorder.startRecording();
            active=true;
            listener.onStatus("Mic reactive • Recording level only • No audio stored");
            thread=new Thread(()->{
                short[] samples=new short[1600]; // 100ms @16kHz
                while(active) {
                    AudioRecord current=recorder;
                    if(current==null)break;
                    int read;
                    try{read=current.read(samples,0,samples.length);}catch(Exception ignored){break;}
                    if(read<=0)continue;
                    double sum=0.0;
                    for(int i=0;i<read;i++){
                        double v=samples[i]/32768.0;
                        sum+=v*v;
                    }
                    double rms=Math.sqrt(sum/read);
                    final double level=Math.max(0,Math.min(1,rms*9.0));
                    if(active)listener.onLevel(level);
                }
            },"AstraAvatarMic");
            thread.setDaemon(true);
            thread.start();
        } catch(Exception ignored) {
            stop();
            listener.onStatus("Microphone unavailable. Check permissions and other apps.");
        }
    }

    public void stop() {
        active=false;
        AudioRecord current=recorder;
        recorder=null;
        if(current!=null){
            try{if(current.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)current.stop();}
            catch(Exception ignored){}
            try{current.release();}catch(Exception ignored){}
        }
        thread=null;
    }
}
