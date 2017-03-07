/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 * @flow
 */

import React, { Component } from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  View,
  Button,
  DeviceEventEmitter,
  Slider,
} from 'react-native';
import Player from 'react-native-streaming-audio-player';

const PLAYBACK_STATE = {
  STATE_NONE:                   0,
  STATE_STOPPED:                1,
  STATE_PAUSED:                 2,
  STATE_PLAYING:                3,
  STATE_FAST_FORWARDING:        4,
  STATE_REWINDING:              5,
  STATE_BUFFERING:              6,
  STATE_ERROR:                  7,
  STATE_CONNECTING:             8,
  STATE_SKIPPING_TO_PREVIOUS:   9,
  STATE_SKIPPING_TO_NEXT:       10,
  STATE_SKIPPING_TO_QUEUE_ITEM: 11,
}

const playlist = [
  {
    "title": "A390",
    "artist": "D.fyne",
    "album_art_uri": "https://i1.sndcdn.com/artworks-000202359698-p3mvly-crop.jpg",
    "stream_url": "https://api.soundcloud.com/tracks/302064339/stream?client_id=3ed8237e8a4bfc63db818a732c95bc38",
    "duration": 177831
  },
  {
    "title": "MAZ.B & GONHILLS - 깍지(Bean pod)",
    "artist": "MAZ.B",
    "album_art_uri": "https://i1.sndcdn.com/artworks-000209689043-i362gn-crop.jpg",
    "stream_url": "https://api.soundcloud.com/tracks/309633076/stream?client_id=3ed8237e8a4bfc63db818a732c95bc38",
    "duration": 157115
  },
  {
    "title": "!",
    "artist": "Chanill",
    "album_art_uri": "https://i1.sndcdn.com/artworks-000207764979-3fgl63-crop.jpg",
    "stream_url": "https://api.soundcloud.com/tracks/307721807/stream?client_id=3ed8237e8a4bfc63db818a732c95bc38",
    "duration": 177048
  },
  {
    "title": "Minnie (with BangDong)",
    "artist": "영회",
    "album_art_uri": "https://i1.sndcdn.com/artworks-000206785833-dwtjgu-crop.jpg",
    "stream_url": "https://api.soundcloud.com/tracks/306703566/stream?client_id=3ed8237e8a4bfc63db818a732c95bc38",
    "duration": 199149
  }
]

export default class Example extends Component {

  constructor(props) {
    super(props);

    this.state = {
      currentTime: 0,
      index: 0,
    }

    this.dragging = false;

    this.onUpdatePosition = this.onUpdatePosition.bind(this);
    this.onSkipTrack = this.onSkipTrack.bind(this);
    this.onPlaybackStateChanged = this.onPlaybackStateChanged.bind(this);
  }

  componentDidMount() {
    DeviceEventEmitter.addListener('onUpdatePosition', this.onUpdatePosition);
    DeviceEventEmitter.addListener('onSkipTrack', this.onSkipTrack);
    DeviceEventEmitter.addListener('onPlaybackStateChanged', this.onPlaybackStateChanged);
  }

  componentWillUnmount() {
    DeviceEventEmitter.removeListener('onUpdatePosition', this.onUpdatePosition);
    DeviceEventEmitter.removeListener('onSkipTrack', this.onSkipTrack);
    DeviceEventEmitter.removeListener('onPlaybackStateChanged', this.onPlaybackStateChanged);
  }

  onUpdatePosition(event) {
    console.log("Current position: " + event.currentPosition);
    Player.isPlaying(() => {
      if (!this.dragging) {
        this.setState({
          currentTime: parseInt(event.currentPosition),  // convert milisecond to second
        });
      }
    })
  }

  onSkipTrack(event) {
    if (event.skip === "Next") {
      this.onNext();
    } else if (event.skip === "Prev") {
      this.onPrev();
    }
  }

  onPlaybackStateChanged(event) {
    console.log("PlaybackState: " + event.state);
  }

  onPlay() {
    Player.play(playlist[this.state.index].stream_url, playlist[this.state.index]);
  }

  onPause() {
    Player.pause();
  }

  onStop() {
    this.setState({
      currentTime: 0,
    });
    Player.stop();
  }

  onNext() {
    //    Player.stop();

    this.setState({
      currentTime: 0,
      index: (this.state.index + 1) % playlist.length,
    }, () => {
      this.onPlay();
    })
  }

  onPrev() {
    //    Player.stop();

    this.setState({
      currentTime: 0,
      index: this.state.index === 0 ? playlist.length - 1 : this.state.index,
    }, () => {
      this.onPlay();
    })
  }

  onSeekTo(pos) {
    this.dragging = false;
    Player.seekTo(pos);
  }

  render() {
    return (
      <View style={styles.container}>
        <View style={{ flexDirection: 'row', alignSelf: 'stretch', justifyContent: 'space-around' }}>
          <Button
            title='<<'
            onPress={() => this.onPrev()}
            color='red'
          />
          <Button
            title='Play'
            onPress={() => this.onPlay()}
            color='red'
          />
          <Button
            title='Pause'
            onPress={() => this.onPause()}
            color='red'
          />
          <Button
            title='Stop'
            onPress={() => this.onStop()}
            color='red'
          />
          <Button
            title='>>'
            onPress={() => this.onNext()}
            color='red'
          />
        </View>
        <View style={{ alignSelf: 'stretch', marginVertical: 10 }}>
          <Slider
            value={this.state.currentTime}
            minimumValue={0}
            maximumValue={playlist[this.state.index].duration}
            onValueChange={value => {
              this.dragging = true;
            }}
            step={1}
            onSlidingComplete={value => this.onSeekTo(value)}
          />
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginHorizontal: 15 }}>
            <Text>{this.state.currentTime}</Text>
            <Text>{playlist[this.state.index].duration}</Text>
          </View>
        </View>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
});

AppRegistry.registerComponent('Example', () => Example);
