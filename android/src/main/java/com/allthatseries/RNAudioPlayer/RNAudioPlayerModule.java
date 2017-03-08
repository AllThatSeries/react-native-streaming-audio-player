package com.allthatseries.RNAudioPlayer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNAudioPlayerModule extends ReactContextBaseJavaModule implements ServiceConnection, LifecycleEventListener {

    public static final String TAG = "RNAudioPlayer";

    ReactApplicationContext reactContext;

    private MediaControllerCompat mMediaController;
    private AudioPlayerService mService;

    public RNAudioPlayerModule(ReactApplicationContext reactContext) {
        super(reactContext);

        this.reactContext = reactContext;
        this.reactContext.addLifecycleEventListener(this);

        // Register receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction("update-position-event");
        filter.addAction("skip-event");
        LocalBroadcastManager.getInstance(reactContext).registerReceiver(mLocalBroadcastReceiver, filter);
    }

    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            WritableMap params = Arguments.createMap();

            switch(intent.getAction()) {
                case "update-position-event":
                    int nCurrentPosition = intent.getIntExtra("currentPosition", 0);
                    params.putInt("currentPosition", nCurrentPosition);
                    sendEvent("onUpdatePosition", params);
                    break;
                case "skip-event":
                    String strSkip = intent.getStringExtra("skip");
                    params.putString("skip", strSkip);
                    sendEvent("onSkipTrack", params);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public String getName() {
    return "RNAudioPlayer";
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
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
            WritableMap params = Arguments.createMap();
            params.putInt("state", state.getState());
            sendEvent("onPlaybackStateChanged", params);
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
                mService = ((AudioPlayerService.ServiceBinder) service).getService();
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
    public void play(String stream_url, ReadableMap metadata) {
        Bundle bundle = new Bundle();
        bundle.putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.getString("title"));
        bundle.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, metadata.getString("album_art_uri"));
        bundle.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.getString("artist"));
        mMediaController.getTransportControls().playFromUri(Uri.parse(stream_url), bundle);
    }

    @ReactMethod
    public void pause() {
        mMediaController.getTransportControls().pause();
    }

    @ReactMethod
    public void resume() {
        mMediaController.getTransportControls().play();
    }

    @ReactMethod
    public void stop() {
        mMediaController.getTransportControls().stop();
    }

    @ReactMethod
    public void seekTo(int timeMillis) {
        mMediaController.getTransportControls().seekTo(timeMillis);
    }

    @ReactMethod
    public void isPlaying(Callback cb) {
        cb.invoke(mService.getPlayback().isPlaying());
    }

    @ReactMethod
    public void getDuration(Callback cb) {
        cb.invoke(mService.getPlayback().getDuration());
    }

    @ReactMethod
    public void getCurrentStreamPosition(Callback cb) {
        cb.invoke(mService.getPlayback().getCurrentStreamPosition());
    }
}