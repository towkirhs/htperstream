# HyperStream for Android

A small browser that finds the video on a page (Reddit, X and other sites) and plays it in its own
player: always the highest quality the site offers, preloaded to disk over several connections.

## Install
Download `HyperStream.apk` from the latest [release](../../releases) and open it on your phone
(or add this repo to Obtainium).

## Use
1. Open a site inside HyperStream, or share a link to it from the Reddit or X app.
2. Start the video. A **Play in best quality** button appears at the bottom.
3. In the player: tap for controls, double-tap a side to skip 10 s, tap the quality label to change quality.

## Ad blocker
Built into the browser: requests to about 76,000 known ad and tracker servers are blocked, and so are
automatic redirects to them (the pop-up ads of many streaming sites). The list is the
[StevenBlack hosts list](https://github.com/StevenBlack/hosts) (MIT), refreshed on every build.
Ads a site serves from its own servers, such as Reddit's promoted posts and X's promoted posts, are not blocked.

## Limits
- It cannot make a video sharper than the best version the site has.
- DRM services (Netflix etc.) and YouTube are not supported.

## Build
Every push to `main` builds and publishes an APK through GitHub Actions (`.github/workflows/build.yml`).
