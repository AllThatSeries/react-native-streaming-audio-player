package com.allthatseries.RNAudioPlayer;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

public class RNAudioPlayerModule extends ReactContextBaseJavaModule {

  ReactApplicationContext reactContext;
  private MediaBrowserCompat mMediaBrowser;

  public RNDeviceModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNAudioPlayer";
  }

  private MediaControllerCompat.Callback mMediaControllerCallback = new MediaControllerCompat.Callback() {
      @Override
      public void onPlaybackStateChanged(PlaybackStateCompat state) {

          switch (state.getState()) {
              case PlaybackState.STATE_NONE:
                  mPlayButton.setImageResource(R.mipmap.ic_play);
                  break;
              case PlaybackState.STATE_PLAYING:
                  mPlayButton.setImageResource(R.mipmap.ic_pause);
                  break;
              case PlaybackState.STATE_PAUSED:
                  mPlayButton.setImageResource(R.mipmap.ic_play);
                  break;
              case PlaybackState.STATE_FAST_FORWARDING:
                  break;
              case PlaybackState.STATE_REWINDING:
                  break;
          }
      }

      @Override
      public void onMetadataChanged(MediaMetadataCompat metadata) {
          super.onMetadataChanged(metadata);
          mTitle.setText(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
      }
  };

  private final MediaBrowserCompat.ConnectionCallback mConnectionCallback = new MediaBrowserCompat.ConnectionCallback() {
      @Override
      public void onConnected() {
          Log.d(TAG, "onConnected");
          try {
              connectToSession(mMediaBrowser.getSessionToken());
          } catch (RemoteException e) {
              LogHelper.e(TAG, e, "could not connect media controller");
          }
      }

      @Override
      public void onConnectionSuspended() {
          super.onConnectionSuspended();
      }

      @Override
      public void onConnectionFailed() {
          super.onConnectionFailed();
      }
  };

  private void connectToSession(MediaSessionCompat.Token token) throws RemoteException {
      MediaControllerCompat mediaController = new MediaControllerCompat(this, token);
      setSupportMediaController(mediaController);
      mediaController.registerCallback(mMediaControllerCallback);

//        if (shouldShowControls()) {
//            showPlaybackControls();
//        } else {
//            LogHelper.d(TAG, "connectionCallback.onConnected: " +
//                    "hiding controls because metadata is null");
//            hidePlaybackControls();
//        }
//
//        onMediaControllerConnected();
  }


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    mPlayButton = (ImageButton) findViewById(R.id.play);
    mTitle = (TextView) findViewById(R.id.title);
    mPlayButton.setOnClickListener(this);
    findViewById(R.id.rewind).setOnClickListener(this);
    findViewById(R.id.forward).setOnClickListener(this);

    mMediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, AudioPlayerService.class), mConnectionCallback, null);
  }

  @Override
  protected void onStart() {
    super.onStart();
    Log.d(TAG, "Activity onStart");

    mMediaBrowser.connect();
  }

  @Override
  protected void onStop() {
    super.onStop();
    LogHelper.d(TAG, "Activity onStop");
    if (getSupportMediaController() != null) {
        getSupportMediaController().unregisterCallback(mMediaControllerCallback);
    }
    mMediaBrowser.disconnect();
  }


//   @Override
//   public void onClick(View v) {
//       Uri uri = Uri.parse("https://api.soundcloud.com/tracks/300554964/stream?client_id=3ed8237e8a4bfc63db818a732c95bc38");
//       Bundle bundle = new Bundle();
//       bundle.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Zion.T");
//       bundle.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Complex");
//       bundle.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "https://i1.sndcdn.com/artworks-000200892021-b4eaaw-large.jpg");

//       switch (v.getId()) {
//           case R.id.play:
//               int pbState = getSupportMediaController().getPlaybackState().getState();
//               if (pbState == PlaybackState.STATE_PLAYING) {
//                   getSupportMediaController().getTransportControls().pause();
//               } else if (pbState == PlaybackStateCompat.STATE_PAUSED) {
//                   getSupportMediaController().getTransportControls().playFromUri(uri, bundle);
//               }else {
//                   //getSupportMediaController().getTransportControls().playFromUri(uri, bundle);
//                   getSupportMediaController().getTransportControls().playFromUri(uri, bundle);
//               }
// //                if (mMediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
// //                    mMediaController.getTransportControls().pause();
// //                } else if(mMediaController.getPlaybackState().getState() == PlaybackState.STATE_PAUSED){
// //                    mMediaController.getTransportControls().play();
// //                } else {
// //                    Uri uri = Uri.parse("https://api.soundcloud.com/tracks/300554964/stream?client_id=3ed8237e8a4bfc63db818a732c95bc38");
// //                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
// //                        Bundle bundle = new Bundle();
// //                        bundle.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Zion.T");
// //                        bundle.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Complex");
// //                        bundle.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "https://i1.sndcdn.com/artworks-000200892021-b4eaaw-large.jpg");
// //                        mMediaController.getTransportControls().playFromUri(uri, bundle);
// //                    } else {
// //                        Bundle bundle = new Bundle();
// //                        bundle.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Zion.T");
// //                        bundle.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Complex");
// //                        bundle.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "https://i1.sndcdn.com/artworks-000200892021-b4eaaw-large.jpg");
// //                        mMediaController.getTransportControls().playFromUri(uri, bundle);
// //                    }
// //
// //
// //                }
//               break;
//           case R.id.rewind:
//               getSupportMediaController().getTransportControls().rewind();
//               break;
//           case R.id.forward:
//               getSupportMediaController().getTransportControls().fastForward();
//               break;
//       }

  }
}