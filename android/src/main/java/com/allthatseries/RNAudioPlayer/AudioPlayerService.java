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
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.IOException;

public class AudioPlayerService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener {

    public static final String SESSION_TAG = "mmFM";

    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_FAST_FORWARD = "fastForward";
    public static final String ACTION_REWIND = "rewind";

    public static final String PARAM_TRACK_URI = "uri";

    private MediaSessionCompat mMediaSession;
    private MediaPlayer mMediaPlayer;
    private AudioManager mAudioManager;
    private MediaControllerCompat mMediaController;
    private NotificationManagerCompat mNotificationManager;
    private PlaybackStateCompat mPlaybackState;

    private MediaNotificationManager mMediaNotificationManager;

    public class ServiceBinder extends Binder {

        public AudioPlayerService getService() {
            return AudioPlayerService.this;
        }
    }

    private Binder mBinder = new ServiceBinder();

    private MediaSessionCompat.Callback mMediaSessionCallback = new MediaSessionCompat.Callback() {

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {

            try {
                switch (mPlaybackState.getState()) {
                    case PlaybackStateCompat.STATE_PLAYING:
                    case PlaybackStateCompat.STATE_PAUSED:
                        mMediaPlayer.reset();
                        mMediaPlayer.setDataSource(AudioPlayerService.this, uri);
                        mPlaybackState = new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_CONNECTING, 0, 1.0f)
                                .build();
                        mMediaSession.setPlaybackState(mPlaybackState);
                        mMediaSession.setMetadata(new MediaMetadataCompat.Builder()
                                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, extras.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, extras.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
                                .build());
                        mMediaPlayer.prepareAsync();
                        break;
                    case PlaybackStateCompat.STATE_NONE:
                        mMediaPlayer.setDataSource(AudioPlayerService.this, uri);
                        mPlaybackState = new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_CONNECTING, 0, 1.0f)
                                .build();
                        mMediaSession.setPlaybackState(mPlaybackState);
                        mMediaSession.setMetadata(new MediaMetadataCompat.Builder()
                                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "ESPN: PTI")
                                .putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, "ESPN: PTI")
                                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "ESPN")
                                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Cubs The Favorites?: 10/14/15")
                                .build());
                        mMediaPlayer.prepareAsync();


                        break;

                }
            } catch (IOException e) {

            }

        }

        @Override
        public void onPlay() {
            switch (mPlaybackState.getState()) {
                case PlaybackStateCompat.STATE_PAUSED:
                    mMediaPlayer.start();
                    mPlaybackState = new PlaybackStateCompat.Builder()
                            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                            .build();
                    mMediaSession.setPlaybackState(mPlaybackState);
//                    updateNotification();
                    break;

            }
        }

        @Override
        public void onPause() {
            switch (mPlaybackState.getState()) {
                case PlaybackStateCompat.STATE_PLAYING:
                    mMediaPlayer.pause();
                    mPlaybackState = new PlaybackStateCompat.Builder()
                            .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                            .build();
                    mMediaSession.setPlaybackState(mPlaybackState);
//                    updateNotification();
                    break;

            }
        }
    };

    public AudioPlayerService() {
    }

    public MediaSessionCompat.Token getMediaSessionToken() {
        return mMediaSession.getSessionToken();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mMediaPlayer.start();
        mPlaybackState = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .build();
        mMediaSession.setPlaybackState(mPlaybackState);
        mMediaNotificationManager.startNotification();
//        updateNotification();
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mPlaybackState = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .build();
        mMediaSession.setPlaybackState(mPlaybackState);
        mMediaPlayer.reset();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mPlaybackState = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .build();

        mNotificationManager = NotificationManagerCompat.from(getApplicationContext());

        // 1) set up media session and media session callback
        mMediaSession = new MediaSessionCompat(this, SESSION_TAG);
        mMediaSession.setCallback(mMediaSessionCallback);
        mMediaSession.setActive(true);
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setPlaybackState(mPlaybackState);

        // 2) get instance to AudioManager
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // 3) create our media player
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);

        // 4) create the media controller
        try {
            mMediaController = new MediaControllerCompat(this, mMediaSession.getSessionToken());
        } catch(RemoteException e) {
            e.printStackTrace();
        }

        try {
            mMediaNotificationManager = new MediaNotificationManager(this);
        } catch(RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMediaPlayer.release();
        mMediaSession.release();
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

    private NotificationCompat.Action createAction(int iconResId, String title, String action) {
        Intent intent = new Intent(this, AudioPlayerService.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, 0);
        return new NotificationCompat.Action.Builder(iconResId, title, pendingIntent).build();
    }

    private void updateNotification() {

        NotificationCompat.Action playPauseAction = mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING ?
                createAction(R.drawable.ic_action_pause, "Pause", ACTION_PAUSE) :
                createAction(R.drawable.ic_action_play, "Play", ACTION_PLAY);

        NotificationCompat.Builder notificationBuilder = (android.support.v7.app.NotificationCompat.Builder) new NotificationCompat.Builder(this);

        notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        notificationBuilder.setCategory(NotificationCompat.CATEGORY_TRANSPORT);
        notificationBuilder.setContentTitle("Cubs The Favorites?: 10/14/15");
        notificationBuilder.setContentText("ESPN: PTI");
        notificationBuilder.setOngoing(mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING);
        notificationBuilder.setShowWhen(false);
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher);
        notificationBuilder.setAutoCancel(false);
        notificationBuilder.addAction(createAction(R.drawable.ic_action_rewind, "Rewind", ACTION_REWIND));
        notificationBuilder.addAction(playPauseAction);
        notificationBuilder.addAction(createAction(R.drawable.ic_action_fast_forward, "Fast Forward", ACTION_FAST_FORWARD));
        notificationBuilder.setStyle(new NotificationCompat.MediaStyle()
                       .setMediaSession(mMediaSession.getSessionToken())
                        .setShowActionsInCompactView(1, 2));

        mNotificationManager.notify(null, 0, notificationBuilder.build());
    }
}