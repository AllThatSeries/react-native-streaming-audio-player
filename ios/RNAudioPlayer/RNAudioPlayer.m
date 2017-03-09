
#import "RNAudioPlayer.h"
#import <React/RCTBridge.h>
#import <React/RCTEventDispatcher.h>
#import <React/RCTEventEmitter.h>
#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>

@interface RNAudioPlayer() {
    float duration;
    NSString *rapName;
    NSString *songTitle;
    NSURL *artWorkUrl;
    id<NSObject> playbackTimeObserver;
    MPNowPlayingInfoCenter *center;
    NSDictionary *songInfo;
    MPMediaItemArtwork *albumArt;
    MPMediaItemArtwork *defaultAlbumArt;
}

@end

@implementation RNAudioPlayer

@synthesize bridge = _bridge;


RCT_EXPORT_MODULE();

- (RNAudioPlayer *)init {
    self = [super init];
    if (self) {
        [self registerRemoteControlEvents];
        [self registerAudioInterruptionNotifications];
        UIImage *defaultArtwork = [UIImage imageNamed:@"default_artwork-t300x300"];
        defaultAlbumArt = [[MPMediaItemArtwork alloc] initWithImage: defaultArtwork];
        center = [MPNowPlayingInfoCenter defaultCenter];
        NSLog(@"AudioPlayer initialized!");
    }
    
    return self;
}


- (void)dealloc {
    NSLog(@"dealloc!!");
    [self unregisterRemoteControlEvents];
    [self unregisterAudioInterruptionNotifications];
    [self deactivate];
    defaultAlbumArt = nil;
}

#pragma mark - Pubic API


RCT_EXPORT_METHOD(prepare:(NSString *)url:(BOOL) bAutoPlay) {
    if(!([url length]>0)) return;
    
    NSURL *soundUrl = [[NSURL alloc] initWithString:url];
    self.playerItem = [AVPlayerItem playerItemWithURL:soundUrl];
    self.player = [AVPlayer playerWithPlayerItem:self.playerItem];
    self.player.automaticallyWaitsToMinimizeStalling = false;
    [self.playerItem addObserver:self forKeyPath:@"status" options:0 context:nil];
    [self.playerItem addObserver:self forKeyPath:@"playbackLikelyToKeepUp" options:NSKeyValueObservingOptionNew context:nil];
    
    soundUrl = nil;
}

RCT_EXPORT_METHOD(songInfo:(NSString *)name title:(NSString *)title url:(NSURL *)url) {
    rapName = name;
    songTitle = title;
    artWorkUrl = url;
    [self setNowPlayingInfo:true];
    CMTime assetDuration = self.player.currentItem.asset.duration;
    duration = CMTimeGetSeconds(assetDuration);
}

RCT_EXPORT_METHOD(play) {
    [self playAudio];
}

RCT_EXPORT_METHOD(pause) {
    [self pauseOrStop:@"PAUSE"];
}

RCT_EXPORT_METHOD(resume) {
    [self playAudio];
}

RCT_EXPORT_METHOD(stop) {
    [self pauseOrStop:@"STOP"];
}

RCT_EXPORT_METHOD(seekTo:(int) nSecond) {
    CMTime newTime = CMTimeMakeWithSeconds(nSecond/1000, 1);
    [self.player seekToTime:newTime];
}

#pragma mark - Audio

-(void) playAudio {
    [self.player play];
    
    // we need a weak self here for in-block access
    __weak typeof(self) weakSelf = self;
    
    playbackTimeObserver =
    [self.player addPeriodicTimeObserverForInterval:CMTimeMakeWithSeconds(1.0, NSEC_PER_SEC) queue:dispatch_get_main_queue() usingBlock:^(CMTime time) {
        
        [weakSelf.bridge.eventDispatcher sendDeviceEventWithName: @"onUpdatePosition"
                                                            body: @{@"currentPosition": @(CMTimeGetSeconds(time)*1000) }];
        songInfo = @{
                     MPMediaItemPropertyTitle: rapName,
                     MPMediaItemPropertyArtist: songTitle,
                     MPNowPlayingInfoPropertyPlaybackRate: [NSNumber numberWithFloat: 1.0f],
                     MPMediaItemPropertyPlaybackDuration: [NSNumber numberWithFloat:duration],
                     MPNowPlayingInfoPropertyElapsedPlaybackTime: [NSNumber numberWithDouble:self.currentPlaybackTime],
                     MPMediaItemPropertyArtwork: albumArt ? albumArt : defaultAlbumArt
                     };
        center.nowPlayingInfo = songInfo;
    }];
    
    [self activate];
}

-(void) pauseOrStop:(NSString *)value {
    [self.player pause];
    
    if ([value isEqualToString:@"STOP"]) {
        CMTime newTime = CMTimeMakeWithSeconds(0, 1);
        [self.player seekToTime:newTime];
        [self.playerItem removeObserver:self forKeyPath:@"status"];
        [self.playerItem removeObserver:self forKeyPath:@"playbackLikelyToKeepUp"];
        albumArt = nil;
    } else {
        [self deactivate];
        songInfo = @{
                     MPMediaItemPropertyTitle: rapName,
                     MPMediaItemPropertyArtist: songTitle,
                     MPNowPlayingInfoPropertyPlaybackRate: [NSNumber numberWithFloat: 0.0],
                     MPMediaItemPropertyPlaybackDuration: [NSNumber numberWithFloat:duration],
                     MPNowPlayingInfoPropertyElapsedPlaybackTime: [NSNumber numberWithDouble:self.currentPlaybackTime],
                     MPMediaItemPropertyArtwork: albumArt ? albumArt : defaultAlbumArt
                     };
        center.nowPlayingInfo = songInfo;
    }
    
    if (playbackTimeObserver) {
        [self.player removeTimeObserver:playbackTimeObserver];
        playbackTimeObserver = nil;
    }
}

- (NSTimeInterval)currentPlaybackTime {
    CMTime time = self.player.currentTime;
    if (CMTIME_IS_VALID(time)) {
        return time.value / time.timescale;
    }
    return 0;
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object
                        change:(NSDictionary *)change context:(void *)context {
    if (object == self.player.currentItem && [keyPath isEqualToString:@"status"]) {
        if (self.player.currentItem.status == AVPlayerItemStatusFailed) {
            [self.bridge.eventDispatcher sendDeviceEventWithName: @"onPlayerError"
                                                            body: @{@"action": @"ERROR" }];
        } else if (self.player.currentItem.status == AVPlayerItemStatusReadyToPlay) {
            [self.bridge.eventDispatcher sendDeviceEventWithName: @"onPlayerStateChanged"
                                                            body: @{@"playbackState": @4 }];
            [self playAudio];
        }
    } else if (object == self.player.currentItem && [keyPath isEqualToString:@"playbackLikelyToKeepUp"]) {
        // check if player has paused && player has begun playing
        if (!self.player.rate && CMTIME_COMPARE_INLINE(self.player.currentItem.currentTime, >, kCMTimeZero)) {
            [self playAudio];
            [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                            body: @{@"action": @"PLAY" }];
        }
    }
}


#pragma mark - Audio Session

-(void)playFinished:(NSNotification *)notification {
    [self.playerItem seekToTime:kCMTimeZero];
    [self.bridge.eventDispatcher sendDeviceEventWithName: @"onPlayerStateChanged"
                                                    body: @{@"playbackState": @5 }];
}

-(void)playStalled:(NSNotification *)notification {
    [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                    body: @{@"action": @"PAUSE" }];
}

-(void)activate {
    NSError *categoryError = nil;
    
    [[AVAudioSession sharedInstance] setActive:YES error:&categoryError];
    [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:&categoryError];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(audioRouteChangeListenerCallback:)
                                                 name:AVAudioSessionRouteChangeNotification
                                               object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(playFinished:)
                                                 name:AVPlayerItemDidPlayToEndTimeNotification
                                               object:self.playerItem];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(playStalled:)
                                                 name:AVPlayerItemPlaybackStalledNotification
                                               object:self.playerItem];
    
    if (categoryError) {
        NSLog(@"Error setting category in activate %@", [categoryError description]);
    }
}

- (void)deactivate {
    NSError *categoryError = nil;
    
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:AVAudioSessionRouteChangeNotification
                                                  object:nil];
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:AVPlayerItemDidPlayToEndTimeNotification
                                                  object:self.playerItem];
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:AVPlayerItemPlaybackStalledNotification
                                                  object:self.playerItem];
    
    [[AVAudioSession sharedInstance] setActive:NO error:&categoryError];
    
    if (categoryError) {
        NSLog(@"Error setting category in deactivate %@", [categoryError description]);
    }
}

- (void)registerAudioInterruptionNotifications
{
    // Register for audio interrupt notifications
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(onAudioInterruption:)
                                                 name:AVAudioSessionInterruptionNotification
                                               object:nil];
}

- (void)unregisterAudioInterruptionNotifications
{
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:AVAudioSessionRouteChangeNotification
                                                  object:nil];
}

- (void)onAudioInterruption:(NSNotification *)notification
{
    // Get the user info dictionary
    NSDictionary *interruptionDict = notification.userInfo;
    
    // Get the AVAudioSessionInterruptionTypeKey enum from the dictionary
    NSInteger interuptionType = [[interruptionDict valueForKey:AVAudioSessionInterruptionTypeKey] integerValue];
    
    // Decide what to do based on interruption type
    switch (interuptionType)
    {
        case AVAudioSessionInterruptionTypeBegan:
            if (duration != 0 && self.player.rate) {
                [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                                body: @{@"action": @"PAUSE" }];
            }
            break;
            
        case AVAudioSessionInterruptionTypeEnded:
            if (duration != 0 && !self.player.rate) {
                [self playAudio];
                [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                            body: @{@"action": @"PLAY" }];
            }
            break;
            
        default:
            NSLog(@"Audio Session Interruption Notification case default.");
            break;
    }
}


#pragma mark - Remote Control Events

- (void)audioRouteChangeListenerCallback:(NSNotification*)notification {
    NSDictionary *interuptionDict = notification.userInfo;
    NSInteger routeChangeReason = [[interuptionDict valueForKey:AVAudioSessionRouteChangeReasonKey] integerValue];
    
    // when headphone was pulled (AVAudioSessionRouteChangeReasonOldDeviceUnavailable)
    if (routeChangeReason == 2) {
        [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                        body: @{@"action": @"PAUSE" }];
    }
}

- (void)registerRemoteControlEvents {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.playCommand addTarget:self action:@selector(didReceivePlayCommand:)];
    [commandCenter.pauseCommand addTarget:self action:@selector(didReceivePauseCommand:)];
    [commandCenter.togglePlayPauseCommand addTarget:self action:@selector(didReceiveToggleCommand:)];
    [commandCenter.nextTrackCommand addTarget:self action:@selector(didReceiveNextTrackCommand:)];
    [commandCenter.previousTrackCommand addTarget:self action:@selector(didReceivePreviousTrackCommand:)];
    commandCenter.playCommand.enabled = YES;
    commandCenter.pauseCommand.enabled = YES;
    commandCenter.nextTrackCommand.enabled = YES;
    commandCenter.previousTrackCommand.enabled = YES;
    commandCenter.stopCommand.enabled = NO;
}

- (void)didReceivePlayCommand:(MPRemoteCommand *)event {
    [self playAudio];
    [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                    body: @{@"action": @"PLAY" }];
}

- (void)didReceivePauseCommand:(MPRemoteCommand *)event {
    [self pauseOrStop:@"PAUSE"];
    [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                    body: @{@"action": @"PAUSE" }];
}

- (void)didReceiveToggleCommand:(MPRemoteCommand *)event {
    // if music is playing
    if (self.player.rate == 1.0f) {
        [self pauseOrStop:@"PAUSE"];
        [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                        body: @{@"action": @"PAUSE" }];
    } else {
        [self playAudio];
        [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                        body: @{@"action": @"PLAY" }];
    }
}

- (void)didReceiveNextTrackCommand:(MPRemoteCommand *)event {
    [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                    body: @{@"action": @"NEXT" }];
}

- (void)didReceivePreviousTrackCommand:(MPRemoteCommand *)event {
    [self.bridge.eventDispatcher sendDeviceEventWithName: @"onRemoteControl"
                                                    body: @{@"action": @"PREV" }];
}

- (void)unregisterRemoteControlEvents {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.playCommand removeTarget:self];
    [commandCenter.pauseCommand removeTarget:self];
    [commandCenter.togglePlayPauseCommand removeTarget:self];
    [commandCenter.nextTrackCommand removeTarget:self];
    [commandCenter.previousTrackCommand removeTarget:self];
}

- (void)setNowPlayingInfo:(bool)isPlaying {
    UIImage *artWork = [UIImage imageWithData:[NSData dataWithContentsOfURL:artWorkUrl]];
    albumArt = [[MPMediaItemArtwork alloc] initWithImage: artWork];
    songInfo = @{
                 MPMediaItemPropertyTitle: rapName,
                 MPMediaItemPropertyArtist: songTitle,
                 MPNowPlayingInfoPropertyPlaybackRate: [NSNumber numberWithFloat:isPlaying ? 1.0f : 0.0],
                 MPMediaItemPropertyArtwork: albumArt ? albumArt : defaultAlbumArt
                 };
    center.nowPlayingInfo = songInfo;
}


@end
