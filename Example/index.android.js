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
} from 'react-native';
import Player from 'react-native-streaming-audio-player';

export default class Example extends Component {

  onPlay() {
    const uri = "https://api.soundcloud.com/tracks/284081601/stream?client_id=a6fc7f2f8f0cded1aba16e1d98cdefb2"
    Player.play(uri, {
      title: "Complex",
      album_url: "https://i1.sndcdn.com/artworks-000184313208-xt911o-large.jpg",
      artist_name: "Zion.T"
    });
  }

  onPause() {
    Player.pause();
  }

  render() {
    return (
      <View style={styles.container}>
        <View style={{flexDirection:'row', alignSelf:'stretch', justifyContent:'space-around'}}>
          <Button 
            title='<<'
            onPress={() => this.onPlay()}
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
            title='>>'
            onPress={() => this.onPlay()}
            color='red'
          />          
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
