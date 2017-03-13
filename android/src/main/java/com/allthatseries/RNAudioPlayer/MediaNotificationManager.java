/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.allthatseries.RNAudioPlayer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;

/**
 * Keeps track of a notification and updates it automatically for a given
 * MediaSession. Maintaining a visible notification (usually) guarantees that the music service
 * won't be killed during playback.
 */
public class MediaNotificationManager extends BroadcastReceiver {
    private static final String TAG = MediaNotificationManager.class.getSimpleName();

    private static final int NOTIFICATION_ID = 412;
    private static final int REQUEST_CODE = 100;

    public static final String ACTION_PAUSE = "com.allthatseries.RNAudioPlayer.pause";
    public static final String ACTION_PLAY = "com.allthatseries.RNAudioPlayer.play";
    public static final String ACTION_PREV = "com.allthatseries.RNAudioPlayer.prev";
    public static final String ACTION_NEXT = "com.allthatseries.RNAudioPlayer.next";

    private final AudioPlayerService mService;
    private MediaSessionCompat.Token mSessionToken;
    private MediaControllerCompat mController;
    private MediaControllerCompat.TransportControls mTransportControls;

    private PlaybackStateCompat mPlaybackState;
    private MediaMetadataCompat mMetadata;

    private final NotificationManagerCompat mNotificationManager;

    private final PendingIntent mPauseIntent;
    private final PendingIntent mPlayIntent;
    private final PendingIntent mPreviousIntent;
    private final PendingIntent mNextIntent;

    private boolean mStarted = false;

    public MediaNotificationManager(AudioPlayerService service) throws RemoteException {
        mService = service;
        updateSessionToken();

        mNotificationManager = NotificationManagerCompat.from(service);

        String pkg = mService.getPackageName();
        mPauseIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PAUSE).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mPlayIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PLAY).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mPreviousIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_PREV).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);
        mNextIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                new Intent(ACTION_NEXT).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT);

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        mNotificationManager.cancelAll();
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before {@link #stopNotification} is called.
     */
    public void startNotification() {
        if (!mStarted) {
            mMetadata = mController.getMetadata();
            mPlaybackState = mController.getPlaybackState();

            // The notification must be updated after setting started to true
            Notification notification = createNotification();
            if (notification != null) {
                mController.registerCallback(mCb);
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION_NEXT);
                filter.addAction(ACTION_PAUSE);
                filter.addAction(ACTION_PLAY);
                filter.addAction(ACTION_PREV);
                mService.registerReceiver(this, filter);

                mService.startForeground(NOTIFICATION_ID, notification);
                mStarted = true;
            }
        }
    }

    /**
     * Removes the notification and stops tracking the session. If the session
     * was destroyed this has no effect.
     */
    public void stopNotification() {
        if (mStarted) {
            mStarted = false;
            mController.unregisterCallback(mCb);
            try {
                mNotificationManager.cancel(NOTIFICATION_ID);
                mService.unregisterReceiver(this);
            } catch (IllegalArgumentException ex) {
                // ignore if the receiver is not registered.
            }
            mService.stopForeground(true);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "Received intent with action " + action);
        Intent newIntent = new Intent("change-playback-action-event");

        switch (action) {
            case ACTION_PAUSE:
                newIntent.putExtra("action", PlaybackAction.PAUSE);
                LocalBroadcastManager.getInstance(context).sendBroadcast(newIntent);
                break;
            case ACTION_PLAY:
                newIntent.putExtra("action", PlaybackAction.PLAY);
                LocalBroadcastManager.getInstance(context).sendBroadcast(newIntent);
                break;
            case ACTION_NEXT:
                newIntent.putExtra("action", PlaybackAction.SKIP_TO_NEXT);
                LocalBroadcastManager.getInstance(context).sendBroadcast(newIntent);
                break;
            case ACTION_PREV:
                newIntent.putExtra("action", PlaybackAction.SKIP_TO_PREVIOUS);
                LocalBroadcastManager.getInstance(context).sendBroadcast(newIntent);
                break;
            default:
                Log.w(TAG, "Unknown intent ignored. Action=" + action);
        }
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see {@link android.media.session.MediaController.Callback#onSessionDestroyed()})
     */
    private void updateSessionToken() throws RemoteException {
        MediaSessionCompat.Token freshToken = mService.getMediaSessionToken();
        if (mSessionToken == null && freshToken != null ||
                mSessionToken != null && !mSessionToken.equals(freshToken)) {
            if (mController != null) {
                mController.unregisterCallback(mCb);
            }
            mSessionToken = freshToken;
            if (mSessionToken != null) {
                mController = new MediaControllerCompat(mService, mSessionToken);
                mTransportControls = mController.getTransportControls();
                if (mStarted) {
                    mController.registerCallback(mCb);
                }
            }
        }
    }

    private final MediaControllerCompat.Callback mCb = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            mPlaybackState = state;
            Log.d(TAG, "Received new playback state " + state);
            if (state.getState() == PlaybackStateCompat.STATE_STOPPED ||
                    state.getState() == PlaybackStateCompat.STATE_NONE) {
                stopNotification();
            } else {
                Notification notification = createNotification();
                if (notification != null) {
                    mNotificationManager.notify(NOTIFICATION_ID, notification);
                }
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            mMetadata = metadata;
            Log.d(TAG, "Received new metadata " + metadata);
            Notification notification = createNotification();
            if (notification != null) {
                mNotificationManager.notify(NOTIFICATION_ID, notification);
            }
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
            Log.d(TAG, "Session was destroyed, resetting to the new session token");
            try {
                updateSessionToken();
            } catch (RemoteException e) {
                Log.e(TAG, e + "could not connect media controller");
            }
        }
    };

    private Notification createNotification() {
        Log.d(TAG, "updateNotificationMetadata. mMetadata= " + mMetadata);
        if (mMetadata == null || mPlaybackState == null) {
            return null;
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mService);
        int playPauseButtonPosition = 0;

        // If skip to previous action is enabled
        if ((mPlaybackState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            notificationBuilder.addAction(R.drawable.ic_skip_previous_white_24dp, "previous", mPreviousIntent);

            // If there is a "skip to previous" button, the play/pause button will
            // be the second one. We need to keep track of it, because the MediaStyle notification
            // requires to specify the index of the buttons (actions) that should be visible
            // when in compact view.
            playPauseButtonPosition = 1;
        }

        addPlayPauseAction(notificationBuilder);

        // If skip to next action is enabled
        if ((mPlaybackState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            notificationBuilder.addAction(R.drawable.ic_skip_next_white_24dp, "next", mNextIntent);
        }

        MediaDescriptionCompat description = mMetadata.getDescription();

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
                art = BitmapFactory.decodeResource(mService.getResources(),
                    R.drawable.ic_default_art);
            }
        }

        notificationBuilder
                .setStyle(new NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(new int[]{playPauseButtonPosition})  // show only play/pause in compact view
                    .setMediaSession(mSessionToken))
                .setColor(0xffdf533b)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setUsesChronometer(true)
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setLargeIcon(art);

        setNotificationPlaybackState(notificationBuilder);
        if (fetchArtUrl != null) {
            fetchBitmapFromURLAsync(fetchArtUrl, notificationBuilder);
        }

        return notificationBuilder.build();
    }

    private void addPlayPauseAction(NotificationCompat.Builder builder) {
        Log.d(TAG, "updatePlayPauseAction");
        String label;
        int icon;
        PendingIntent intent;
        if (mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            label = "pause";
            icon = R.drawable.ic_pause_white_24dp;
            intent = mPauseIntent;
        } else {
            label = "play";
            icon = R.drawable.ic_play_arrow_white_24dp;
            intent = mPlayIntent;
        }
        builder.addAction(new NotificationCompat.Action(icon, label, intent));
    }

    private void setNotificationPlaybackState(NotificationCompat.Builder builder) {
        Log.d(TAG, "updateNotificationPlaybackState. mPlaybackState=" + mPlaybackState);
        if (mPlaybackState == null || !mStarted) {
            Log.d(TAG, "updateNotificationPlaybackState. cancelling notification!");
            mService.stopForeground(true);
            return;
        }

        if (mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING && mPlaybackState.getPosition() >= 0) {
            builder
                .setWhen(System.currentTimeMillis() - mPlaybackState.getPosition())
                .setShowWhen(true)
                .setUsesChronometer(true);
        } else {
            Log.d(TAG, "updateNotificationPlaybackState. hiding playback position");
            builder
                .setWhen(0)
                .setShowWhen(false)
                .setUsesChronometer(false);
        }

        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING);
    }

    private void fetchBitmapFromURLAsync(final String bitmapUrl, final NotificationCompat.Builder builder) {
        AlbumArtCache.getInstance().fetch(bitmapUrl, new AlbumArtCache.FetchListener() {
            @Override
            public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                if (mMetadata != null && mMetadata.getDescription().getIconUri() != null &&
                            mMetadata.getDescription().getIconUri().toString().equals(artUrl)) {
                    // If the media is still the same, update the notification:
                    Log.d(TAG, "fetchBitmapFromURLAsync: set bitmap to " + artUrl);
                    builder.setLargeIcon(bitmap);
                    mNotificationManager.notify(NOTIFICATION_ID, builder.build());
                }
            }
        });
    }
}
