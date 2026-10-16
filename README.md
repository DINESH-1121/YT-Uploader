# YT Uploader

YT Uploader is an Android app that helps hand a **video file** to the official YouTube app. It provides a floating shortcut that opens a video picker while you are using another app, and it can receive video files from Android's Share menu.

YouTube handles account sign-in, upload details, upload progress, and final publishing. This project does not use the YouTube Data API, Google Cloud, a backend, or automatic Instagram video extraction.

## What It Does

- Select a locally available video using Android's document picker.
- Receive a video shared to YT Uploader from another Android app.
- Start a user-enabled floating button that opens the video picker above other apps.
- Hand the selected content URI to the YouTube app with temporary read access.

The app does **not** download a Reel from its Instagram URL. A copied Reel link is a webpage link, not the video's media file. The video must already be available as a file on the device or be shared by an app as `video/*`.

## Requirements

- Android 7.0 (API 24) or later.
- YouTube installed for the direct handoff. If YouTube cannot accept the file, open YouTube and choose **Create → Upload a video** from its library.
- Android SDK Platform 36 and JDK 17 to build from source.

## Use

### Choose a saved video

1. Open **ReelToShorts**.
2. Tap **Choose video file** and select a video.
3. Tap **Continue to YouTube**.
4. Complete the upload in YouTube. YouTube displays upload progress and asks for any required details.

### Use the floating button

1. In YT Uploader, tap **Enable floating button**.
2. Allow **Display over other apps** for **ReelToShorts** in Android Settings, then return to the app and tap **Enable floating button** again.
3. Open the app containing the video and tap **Choose video** on the floating control.
4. Select the video file and continue to YouTube.
5. Tap **Disable floating button** in YT Uploader when finished.

The floating control opens the system file picker; it cannot read or capture the currently playing video inside Instagram.

### Share a video into the app

Use the source app's Android **Share** action and choose **ReelToShorts**. This works only when the source app shares an actual video file. Sharing a URL or text link will not provide the video bytes.

## Overlay Permission on Sideloaded Installs

Android may block restricted settings for apps installed outside the Play Store. If Android shows an **App was denied access** warning:

1. Open **Settings → Apps → ReelToShorts**.
2. Open the **⋮** menu and select **Allow restricted settings**, if shown.
3. Return to **Special app access → Display over other apps → ReelToShorts** and enable it.
4. Return to YT Uploader and tap **Enable floating button** again.

Settings labels vary by Android version and device manufacturer. The overlay permission is needed only for the floating control; choosing or sharing a video does not require it.

## YouTube Connection and Upload Behavior

YT Uploader does not sign in to Google or upload directly. When you tap **Continue to YouTube**, it sends an Android `ACTION_SEND` intent containing the selected `content://` URI and a temporary read grant to the official YouTube package. YouTube decides whether that intent is supported on the installed version. If it is not, the app displays a fallback message so you can choose the file from YouTube's upload screen.

Because YouTube performs the upload, YT Uploader cannot show upload percentage, choose a title, set visibility, or report whether publishing succeeded. Those steps and progress appear in YouTube.

## Permissions and Privacy

- **Display over other apps:** lets the user-enabled floating control appear above Instagram and other apps.
- **Foreground service / special use:** keeps the explicitly enabled floating control active while the user switches apps. Android shows an ongoing notification.
- **Notifications:** used for the foreground-service notification on supported Android versions.
- **Video URI access:** the app reads a selected or shared video only to pass it to YouTube. The read grant is temporary.

The app has no backend and does not request network access. It does not copy video data into its own cache or store Google credentials, and Android app-data backup is disabled. YouTube's own privacy and upload behavior applies after handoff.

## Build and Test

From the repository root, with JDK 17 and Android SDK Platform 36 installed:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected, authorized Android device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build is signed with the local Android debug key and is for development/testing. A release build is not configured with a release signing key.

## Project Details

- Application ID: `com.example.reelstoshorts`
- Kotlin and Jetpack Compose UI.
- Minimum SDK: 24; target SDK: 36.
- Overlay implementation: `OverlayService` and Android `WindowManager`.
- Video selection and sharing: Android document picker and `ACTION_SEND` intents.