package com.allthatseries.RNAudioPlayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaDescriptionCompat;

import com.emuneee.marshmallowfm.utils.LogHelper;

import java.io.IOException;

import static android.content.ContentValues.TAG;

public class AudioPlayerService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener {

    public static final String SESSION_TAG = "mmFM";

    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_FAST_FORWARD = "fastForward";
    public static final String ACTION_REWIND = "rewind";

    public static final String PARAM_TRACK_URI = "uri";

    private MediaSession mMediaSession;
    private MediaPlayer mMediaPlayer;
    private AudioManager mAudioManager;
    private PlaybackState mPlaybackState;
    private MediaController mMediaController;

    public class ServiceBinder extends Binder {

        public AudioPlayerService getService() {
            return AudioPlayerService.this;
        }
    }

    private Binder mBinder = new ServiceBinder();

    private MediaSession.Callback mMediaSessionCallback = new MediaSession.Callback() {

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            Uri uri = extras.getParcelable(PARAM_TRACK_URI);
            onPlayFromUri(uri, null);
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {

            try {
                switch (mPlaybackState.getState()) {
                    case PlaybackState.STATE_PLAYING:
                    case PlaybackState.STATE_PAUSED:
                        mMediaPlayer.reset();
                        mMediaPlayer.setDataSource(AudioPlayerService.this, uri);
                        mMediaPlayer.prepare();
                        mPlaybackState = new PlaybackState.Builder()
                                .setState(PlaybackState.STATE_CONNECTING, 0, 1.0f)
                                .build();
                        mMediaSession.setPlaybackState(mPlaybackState);
                        mMediaSession.setMetadata(new MediaMetadata.Builder()
                                .putString(MediaMetadata.METADATA_KEY_ARTIST, extras.getString(MediaMetadata.METADATA_KEY_ARTIST))
                                .putString(MediaMetadata.METADATA_KEY_TITLE, extras.getString(MediaMetadata.METADATA_KEY_TITLE))
                                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, extras.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI))
                                .build());
                        break;
                    case PlaybackState.STATE_NONE:
                        mMediaPlayer.setDataSource(AudioPlayerService.this, uri);
                        mMediaPlayer.prepare();
                        mPlaybackState = new PlaybackState.Builder()
                                .setState(PlaybackState.STATE_CONNECTING, 0, 1.0f)
                                .build();
                        mMediaSession.setPlaybackState(mPlaybackState);
                        mMediaSession.setMetadata(new MediaMetadata.Builder()
                                .putString(MediaMetadata.METADATA_KEY_ARTIST, extras.getString(MediaMetadata.METADATA_KEY_ARTIST))
                                .putString(MediaMetadata.METADATA_KEY_TITLE, extras.getString(MediaMetadata.METADATA_KEY_TITLE))
                                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, extras.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI))
                                .build());
                        break;

                }
            } catch (IOException e) {

            }

        }

        @Override
        public void onPlay() {
            switch (mPlaybackState.getState()) {
                case PlaybackState.STATE_PAUSED:
                    mMediaPlayer.start();
                    mPlaybackState = new PlaybackState.Builder()
                            .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                            .build();
                    mMediaSession.setPlaybackState(mPlaybackState);
                    updateNotification();
                    break;

            }
        }

        @Override
        public void onPause() {
            switch (mPlaybackState.getState()) {
                case PlaybackState.STATE_PLAYING:
                    mMediaPlayer.pause();
                    mPlaybackState = new PlaybackState.Builder()
                            .setState(PlaybackState.STATE_PAUSED, 0, 1.0f)
                            .build();
                    mMediaSession.setPlaybackState(mPlaybackState);
                    updateNotification();
                    break;

            }
        }

        @Override
        public void onRewind() {
            switch (mPlaybackState.getState()) {
                case PlaybackState.STATE_PLAYING:
                    mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition() - 10000);
                    break;

            }
        }

        @Override
        public void onFastForward() {
            switch (mPlaybackState.getState()) {
                case PlaybackState.STATE_PLAYING:
                    mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition() + 10000);
                    break;

            }
        }
    };

    public AudioPlayerService() {
    }

    public MediaSession.Token getMediaSessionToken() {
        return mMediaSession.getSessionToken();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mMediaPlayer.start();
        mPlaybackState = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                .build();
        mMediaSession.setPlaybackState(mPlaybackState);
        updateNotification();
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mPlaybackState = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_NONE, 0, 1.0f)
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

        mPlaybackState = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_NONE, 0, 1.0f)
                .build();

        // 1) set up media session and media session callback
        mMediaSession = new MediaSession(this, SESSION_TAG);
        mMediaSession.setCallback(mMediaSessionCallback);
        mMediaSession.setActive(true);
        mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setPlaybackState(mPlaybackState);

        // 2) get instance to AudioManager
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // 3) create our media player
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);

        // 4) create the media controller
        mMediaController = new MediaController(this, mMediaSession.getSessionToken());
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

    private Notification.Action createAction(int iconResId, String title, String action) {
        Intent intent = new Intent(this, AudioPlayerService.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, 0);
        return new Notification.Action.Builder(iconResId, title, pendingIntent).build();
    }

    private void updateNotification() {

        MediaDescription description = mMediaController.getMetadata().getDescription();

        String fetchArtUrl = null;
        Bitmap art = null;
        if (description.getIconUri() != null) {
            // This sample assumes the iconUri will be a valid URL formatted String, but
            // it can actually be any valid Android Uri formatted String.
            // async fetch the album art icon
            String artUrl = description.getIconUri().toString();
            art = AlbumArtCache.getInstance().getBigImage(artUrl);
            if (art == null) {
                fetchArtUrl = artUrl;
                // use a placeholder art while the remote art is being downloaded
//                art = BitmapFactory.decodeResource(mService.getResources(),
//                        R.drawable.ic_default_art);
            }
        }


        Notification.Action playPauseAction = mPlaybackState.getState() == PlaybackState.STATE_PLAYING ?
                createAction(R.drawable.ic_action_pause, "Pause", ACTION_PAUSE) :
                createAction(R.drawable.ic_action_play, "Play", ACTION_PLAY);

        Notification.Builder notificationBuilder = new Notification.Builder(this);

        notificationBuilder.setPriority(Notification.PRIORITY_DEFAULT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setOngoing(mPlaybackState.getState() == PlaybackState.STATE_PLAYING)
                .setShowWhen(false)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(false)
                .addAction(createAction(R.drawable.ic_action_rewind, "Rewind", ACTION_REWIND))
                .addAction(playPauseAction)
                .addAction(createAction(R.drawable.ic_action_fast_forward, "Fast Forward", ACTION_FAST_FORWARD))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mMediaSession.getSessionToken())
                        .setShowActionsInCompactView(1, 2))
                .setLargeIcon(art);

        if (fetchArtUrl != null) {
            fetchBitmapFromURLAsync(fetchArtUrl, notificationBuilder);
        }

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, notificationBuilder.build());
    }

    private void fetchBitmapFromURLAsync(final String bitmapUrl,
                                         final Notification.Builder builder) {
        AlbumArtCache.getInstance().fetch(bitmapUrl, new AlbumArtCache.FetchListener() {
            @Override
            public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                if (mMediaController.getMetadata() != null && mMediaController.getMetadata().getDescription().getIconUri() != null &&
                        mMediaController.getMetadata().getDescription().getIconUri().toString().equals(artUrl)) {
                    // If the media is still the same, update the notification:
                    LogHelper.d(TAG, "fetchBitmapFromURLAsync: set bitmap to ", artUrl);
                    builder.setLargeIcon(bitmap);
                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, builder.build());
                }
            }
        });
    }
}
