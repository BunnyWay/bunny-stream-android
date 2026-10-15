# Ads (VAST and VMAP)

The player plays the ads configured for your library. Set the VAST or VMAP tag URL in the Bunny
dashboard (Stream > your library > Player) and both `BunnyStreamPlayer` and
`BunnyLiveStreamPlayer` pick it up. There is nothing to wire in code: ads are requested and played
by the Google IMA SDK, which ships with `net.bunny:player`.

## What plays where

| | VOD | Live |
|---|---|---|
| Pre-roll (VAST) | yes | yes, before joining the live edge |
| Skippable ads | yes | yes |
| Mid-rolls and post-rolls (VMAP) | yes | no, see below |
| Ads on the recording of an ended stream | n/a | yes, the recording gets its own ads, mid-rolls included |

A live stream has no fixed timeline, so a client-side player has nowhere to put a mid-roll: the
player plays the pre-roll and then joins the live edge. Ad breaks inside a running live stream need
server-side ad insertion, which the SDK does not do. The dashboard has one tag per library, so if
yours is a VMAP tag with mid-rolls for your videos, live viewers get one ad break when they join
and nothing after it.

The live player plays the pre-roll once per stream. Refreshes and the automatic recovery after a
network drop reconnect without it. When the stream ends and the player switches to the recording,
that is new content and it gets its own ads. A recovery on the recording reconnects without ads
too, so mid-rolls still ahead of the viewer at that point are skipped.

## During an ad

- The control bar, the LIVE badge, the watermark and the speed badge step aside. IMA draws its own
  ad UI: the countdown, the skip button for skippable ads and the "Learn more" link.
- Seeking is not possible. A `seekTo()` made during an ad is applied when the ad break ends, which
  is what makes "continue watching" from [resume positions](play-a-video.md#resume-positions) land
  in the right place after a pre-roll.
- `getCurrentPosition()` and `getDuration()` keep describing the video, not the ad, and resume
  positions are not saved until the video is back.
- Fullscreen and Picture-in-Picture work as usual, and the ad UI follows the video into
  fullscreen.

## Your own controls

With `controlsEnabled = false` your UI has to step aside too. Follow `onAdPlayingChanged`:

```kotlin
player.controlsEnabled = false
player.onAdPlayingChanged = { playingAd ->
    myControls.isVisible = !playingAd
}
```

## Using a different tag in code

The video player can use a tag other than the dashboard's, for example a different one per
placement in your app. Set it before `playVideo`:

```kotlin
player.vastTagUrl = "https://pubads.g.doubleclick.net/gampad/ads?..."
player.playVideo(videoId)
```

`null` (the default) uses the dashboard's tag and an empty string plays without ads.
`BunnyLiveStreamPlayer` always uses the tag from the dashboard.

## When an ad fails

A broken tag, an empty ad response or an ad server that does not answer never blocks the video:
playback starts as soon as IMA gives up, and an unreachable ad server holds the start back for a
few seconds at most. The reason is logged under the `DefaultBunnyPlayer` tag, for example
`IMA ad error: ... VAST_NO_ADS_AFTER_WRAPPER`.

To try ads without touching the library, use Google's
[sample ad tags](https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/tags)
with `vastTagUrl`. The demo app has a field for it in its settings screen.

## Gotchas

- Chromecast: the receiver plays the video without client-side ads. Casting during an ad
  continues the video on the TV, and ads that already played are not played again when the
  session ends and playback comes back to the phone. A video that started on the TV never ran
  its pre-roll, so it plays when playback comes back to the phone.
- The IMA SDK adds the `com.google.android.gms.permission.AD_ID` permission to your merged
  manifest. It has been there since the IMA dependency was added to `net.bunny:player`; declare it
  in the Play Console's data safety form like any other ads SDK.
- R8 / ProGuard: the IMA SDK ships its own keep rules, there is nothing to add.
- Android TV: the TV player hides its own controls during an ad and leaves the remote to the ad's
  skip button.
