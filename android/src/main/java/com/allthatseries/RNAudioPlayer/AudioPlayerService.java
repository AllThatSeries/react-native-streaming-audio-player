package com.allthatseries.RNAudioPlayer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class AudioPlayerService extends Service {

    private static final String TAG = AudioPlayerService.class.getSimpleName();

    public static final String SESSION_TAG = "AUDIO_SESSION";

    // The action of the incoming Intent indicating that it contains a command to be executed
    public static final String ACTION_CMD = "com.allthatseries.RNAudioPlayer.ACTION_CMD";
    // The key in the extras of the incoming Intent indicating the command that should be executed
    public static final String CMD_NAME = "CMD_NAME";
    // A value of a CMD_NAME key in the extras of the incoming Intent that indicates that the music playback should be paused
    public static final String CMD_PAUSE = "CMD_PAUSE";
    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 30000;

    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_FAST_FORWARD = "fastForward";
    public static final String ACTION_REWIND = "rewind";

    private MediaSessionCompat mMediaSession;
    private MediaControllerCompat mMediaController;
    private MediaNotificationManager mMediaNotificationManager;
    private Playback mPlayback;
    private final DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);

    public class ServiceBinder extends Binder {

        public AudioPlayerService getService() {
            return AudioPlayerService.this;
        }
    }

    private Binder mBinder = new ServiceBinder();

    private MediaSessionCompat.Callback mMediaSessionCallback = new MediaSessionCompat.Callback() {

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            mMediaSession.setActive(true);
            mDelayedStopHandler.removeCallbacksAndMessages(null);

            // The service needs to continue running even after the bound client (usually a
            // MediaController) disconnects, otherwise the music playback will stop.
            // Calling startService(Intent) will keep the service running until it is explicitly killed.
            startService(new Intent(getApplicationContext(), AudioPlayerService.class));

            mPlayback.playFromUri(uri, extras);
        }

        @Override
        public void onPlay() {
            mMediaSession.setActive(true);
            mDelayedStopHandler.removeCallbacksAndMessages(null);

            // The service needs to continue running even after the bound client (usually a
            // MediaController) disconnects, otherwise the music playback will stop.
            // Calling startService(Intent) will keep the service running until it is explicitly killed.
            startService(new Intent(getApplicationContext(), AudioPlayerService.class));
            mPlayback.resume();
        }

        @Override
        public void onPause() {
            if (mPlayback.isPlaying()) {
                mPlayback.pause();
                mMediaSession.setActive(false);
                // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
                // potentially stopping the service.
                mDelayedStopHandler.removeCallbacksAndMessages(null);
                mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
                stopForeground(true);
            }
        }

        @Override
        public void onStop() {
            mPlayback.stop();
            mMediaSession.setActive(false);
            // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
            // potentially stopping the service.
            mDelayedStopHandler.removeCallbacksAndMessages(null);
            mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
            stopForeground(true);
        }
    };

    private Playback.Callback mPlaybackCallback = new Playback.Callback() {
        @Override
        public void onCompletion() {
            updatePlaybackState();
            mMediaNotificationManager.startNotification();
        }

        @Override
        public void onError(String error) {
            mMediaNotificationManager.stopNotification();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            updatePlaybackState();
        }

        @Override
        public void onMediaMetadataChanged(MediaMetadataCompat metadata) {
            mMediaSession.setMetadata(metadata);
            mMediaNotificationManager.startNotification();
        }
    };

    public MediaSessionCompat.Token getMediaSessionToken() {
        return mMediaSession.getSessionToken();
    }

    public Playback getPlayback() {
        return this.mPlayback;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 1) set up media session and media session callback
        mMediaSession = new MediaSessionCompat(this, SESSION_TAG);
        mMediaSession.setCallback(mMediaSessionCallback);
        mMediaSession.setActive(true);
        mMediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // 2) Create a Playback instance
        mPlayback = new Playback(this);
        mPlayback.setCallback(mPlaybackCallback);
        updatePlaybackState();

        // 3) Create the media controller
        try {
            mMediaController = new MediaControllerCompat(this, mMediaSession.getSessionToken());
        } catch(RemoteException e) {
            e.printStackTrace();
        }

        // 4) Create notification manager instance
        try {
            mMediaNotificationManager = new MediaNotificationManager(this);
        } catch(RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMediaSession.release();
        mMediaNotificationManager.stopNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    mMediaController.getTransportControls().play();
                    break;
                case ACTION_FAST_FORWARD:
                    mMediaController.getTransportControls().fastForward();
                    break;
                case ACTION_REWIND:
                    mMediaController.getTransportControls().rewind();
                    break;
                case ACTION_PAUSE:
                    mMediaController.getTransportControls().pause();
                    break;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Update the current media player state, optionally showing an error message.
     */
    public void updatePlaybackState() {
        Log.d(TAG, "updatePlaybackState, playback state=" + mPlayback.getState());

        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (mPlayback != null ) {
            position = mPlayback.getCurrentStreamPosition();
        }
        long actions =
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT;

        if (mPlayback.isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        } else {
            actions |= PlaybackStateCompat.ACTION_PLAY;
        }

        int state = mPlayback.getState();

        //noinspection ResourceType
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, position, 1.0f, SystemClock.elapsedRealtime());

        mMediaSession.setPlaybackState(stateBuilder.build());
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<AudioPlayerService> mWeakReference;

        private DelayedStopHandler(AudioPlayerService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioPlayerService service = mWeakReference.get();
            if (service != null && service.getPlayback() != null) {
                if (service.getPlayback().isPlaying()) {
                    Log.d(TAG, "Ignoring delayed stop since the media player is in use.");
                    return;
                }
                Log.d(TAG, "Stopping service with delay handler.");
                service.stopSelf();
            }
        }
    }
}