package com.allthatseries.RNAudioPlayer;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

public class RNAudioPlayerModule extends ReactContextBaseJavaModule {

  ReactApplicationContext reactContext;

  public RNDeviceModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNAudioPlayer";
  }
}