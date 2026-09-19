# ImageSeek — Android reverse image search app

A small Android app that lets a user select an image and sends it directly to Google Lens, then displays the Lens results inside the app. Google documents that Lens results can include visually similar images and websites containing the image or a similar image.

## Important scope

“Overall internet” is not a single search index. This app uses Google Lens as its default no-API-key search path. For a broader/independent product, add licensed providers such as TinEye through their APIs on a server; TinEye's public site currently advertises a reverse-image index with tens of billions of images, while noting that its API is a separate paid integration path.

## Build

Open this folder in Android Studio with Android SDK Platform 35 installed, then build the `app` module.

Or from a machine with Gradle available:

```text
gradle :app:assembleDebug
```

The debug APK will be under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

The app compresses the chosen picture to JPEG before sending it to reduce memory and upload size. It does not upload the picture until the user taps “Search similar images”.

The Google Lens upload endpoint is a current web flow used by Chromium (`https://lens.google.com/v3/upload`). Web endpoints can change, so this is not a guaranteed permanent public API.
