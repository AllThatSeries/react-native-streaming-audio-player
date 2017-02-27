package com.allthatseries.RNAudioPlayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNAudioPlayerModule extends ReactContextBaseJavaModule implements ServiceConnection, LifecycleEventListener {

    public static final String TAG = "RNAudioPlayer";

    ReactApplicationContext reactContext;

    private MediaControllerCompat mMediaController;

    public RNAudioPlayerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        this.reactContext.addLifecycleEventListener(this);
    }

    @Override
    public String getName() {
    return "RNAudioPlayer";
    }

    private void sendEvent(String eventName,
                           @Nullable WritableMap params) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    @Override
    public void initialize() {
        super.initialize();

        try {
            Intent intent = new Intent(this.reactContext, AudioPlayerService.class);
            this.reactContext.startService(intent);
            boolean isBound = this.reactContext.bindService(intent, this, Context.BIND_AUTO_CREATE);
            if (isBound) {
                Log.d(TAG, "Bound");
            } else {
                Log.d(TAG, "Not Bound");
            }

        } catch (Exception e) {
            Log.e("ERROR", e.getMessage());
        }
    }

    private MediaControllerCompat.Callback mMediaControllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {

//            switch (state.getState()) {
//                case PlaybackState.STATE_NONE:
//                    mPlayButton.setImageResource(R.mipmap.ic_play);
//                    break;
//                case PlaybackState.STATE_PLAYING:
//                    mPlayButton.setImageResource(R.mipmap.ic_pause);
//                    break;
//                case PlaybackState.STATE_PAUSED:
//                    mPlayButton.setImageResource(R.mipmap.ic_play);
//                    break;
//                case PlaybackState.STATE_FAST_FORWARDING:
//                    break;
//                case PlaybackState.STATE_REWINDING:
//                    break;
//            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
        }
    };

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

        if (service instanceof AudioPlayerService.ServiceBinder) {
            try {
                mMediaController = new MediaControllerCompat(this.reactContext,
                        ((AudioPlayerService.ServiceBinder) service).getService().getMediaSessionToken());
                mMediaController.registerCallback(mMediaControllerCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }


    @Override
    public void onHostResume() {
        Log.d(TAG, "onHostResume");

    }

    @Override
    public void onHostPause() {
        Log.d(TAG, "onHostPause");

    }

    @Override
    public void onHostDestroy() {
        Log.d(TAG, "onHostDestroy");

    }

    @ReactMethod
    public void play() {
        Uri uri = Uri.parse("https://api.soundcloud.com/tracks/284081601/stream?client_id=a6fc7f2f8f0cded1aba16e1d98cdefb2");
        mMediaController.getTransportControls().playFromUri(uri, null);
    }

    @ReactMethod
    public void pause() {
        mMediaController.getTransportControls().pause();
    }

    @ReactMethod
    public void resume() {

    }

    @ReactMethod
    public void stop() {

    }

    @ReactMethod
    public void seekTo(int timeMillis) {

    }


}