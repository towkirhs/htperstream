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

## Home page and app lock
- The home button opens your list of sites. Tap the star next to the address bar to add the site you're on
  (tap again to remove it), or the ✕ next to a site on the home page.
- Every time you come back to the app it asks for your fingerprint or your phone's PIN/pattern.
  Phones without a screen lock are not locked. The app's contents are hidden in the recent-apps screen (Android 13+).

## Desktop site
On by default: sites are told this is Chrome on Windows, so they serve their desktop version, which
on many sites (Reddit included) still lays out to fit the phone. The monitor button next to the address
bar turns it off and on (dimmed = off).

## Ad blocker
Built into the browser: requests to about 76,000 known ad and tracker servers are blocked, and so are
automatic redirects to them, and pages cannot open pop-up windows (links you tap that would open a new tab open in place instead). The list is the
[StevenBlack hosts list](https://github.com/StevenBlack/hosts) (MIT), refreshed on every build.
Ads a site serves from its own servers, such as Reddit's promoted posts and X's promoted posts, are not blocked.

## Limits
- It cannot make a video sharper than the best version the site has.
- DRM services (Netflix etc.) and YouTube are not supported.

## Build
Every push to `main` builds and publishes an APK through GitHub Actions (`.github/workflows/build.yml`).
