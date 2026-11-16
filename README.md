# Reclaim

Reclaim is an Android app that gives time back from Instagram Reels and YouTube Shorts. It runs as an accessibility service on the phone. It does not use the network, and it does not decide what you are watching from a URL.

The installable app is [`releases/Reclaim.apk`](releases/Reclaim.apk). Download that file, open it on the phone, and turn Reclaim on in Android accessibility settings. Android may call this a restricted setting because the app was installed from an APK. Allow restricted settings for Reclaim from **Settings → Apps → Reclaim**, then enable the service.

## What it does

- Covers the **Reels** icon in Instagram’s bottom bar.
- Covers the **Shorts** icon in YouTube’s bottom bar.
- Leaves Reels and Shorts that appear inside the feed visible. Those rows move while you scroll, so the app does not try to cover them.
- When a Reel or Short is open, a finger swipe presses the system back button. From a chat, that returns to the chat.
- A tap on an open Reel or Short still reaches the video. The system back button is not intercepted.

## Architecture

Reclaim is one Android app module. Detection rules are plain Kotlin so they can be tested without a phone. The accessibility service and the overlays are the only parts that touch Android.

```
Instagram / YouTube
        │  accessibility events (window and content changes, not scroll)
        ▼
ReclaimAccessibilityService
        │  reads the window
        ▼
ScreenReader  ──►  list of controls (label, bounds, view id)
        │
        ├── iconCoverRegions()     bottom-bar Reels or Shorts icon only
        │         │
        │         ▼
        │   OverlayController.updateIcons()   small covers, redrawn only when the icon moves
        │
        └── classifyOpenedClip()    Reel, Short, or neither
                  │
                  ▼
            OverlayController swipe layer, only while a Reel or Short is open
                  │  finger swipe
                  ▼
            GLOBAL_ACTION_BACK
```

| Piece | Role |
| --- | --- |
| `MainActivity` | Shows whether the service is on and opens Android accessibility settings. |
| `ReclaimAccessibilityService` | Receives events for Instagram (`com.instagram.android`) and YouTube (`com.google.android.youtube`). Throttles a scan, then updates the icon covers and the swipe layer. |
| `ScreenReader` | Walks the accessibility tree and copies each control’s label, screen bounds, clickable flag, and view id. |
| `logic/ScreenRules.kt` | Decides which bottom-bar icon to cover and whether the screen is an opened Reel or Short. No Android APIs. |
| `OverlayController` | Draws the icon covers and, only for an open Reel or Short, a full-screen layer that distinguishes a swipe from a tap. |
| `ScreenRulesTest` | JVM tests for the icon rule and the Reel / Short classification. |

### Icon covers

A control is covered only when its center sits in the bottom 18% of the screen and its label is the Reels tab (Instagram) or the Shorts tab (YouTube). A “Shorts” or “Reels” row higher on the screen is part of the feed and is left alone. The cover is redrawn only when those rectangles change, so scrolling the feed does not keep rebuilding it.

### How a Reel or Short is recognized

The service looks at visible controls. It does not read the address bar or any URL.

An Instagram screen counts as a Reel when about half of these buttons are visible and they sit together in a vertical action rail: **share**, **repost**, **follow**. Share and follow scattered down a normal feed do not count.

A YouTube screen counts as a Short in either of two ways:

- About half of **like**, **dislike**, **comment**, and **remix** are visible and stacked vertically. “Dislike” does not count as “like”. The same buttons in one horizontal row, as on a normal video, do not count.
- A view id names the Shorts player (`reel_player`, `shorts_player`, or `reel_watch`). A Shorts shelf id does not count.

### Swipe and back

The swipe layer is added only after that classification succeeds. Movement past a small threshold is a swipe and calls `performGlobalAction(GLOBAL_ACTION_BACK)`. A small movement is treated as a tap and replayed to the app underneath. The layer does not take key focus, so the phone’s own back button still reaches Instagram or YouTube.

## Build

Open the project in Android Studio, or from this folder:

```
gradlew.bat assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. The copy checked into `releases/Reclaim.apk` is that same debug build, kept here so it can be downloaded without building.
