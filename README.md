# YT Uploader

YT Uploader is an Android app that uploads a selected **video file directly to YouTube**. It provides a floating shortcut that opens a video picker while you are using another app, and it can receive video files from Android's Share menu.

The app authorizes the user's Google account with the YouTube Data API, uploads the video from the phone using YouTube's resumable upload protocol, and displays transfer progress. No separate application server is used. Google Cloud configuration is required for API access.

## What It Does

- Select a locally available video using Android's document picker.
- Receive a video shared to YT Uploader from another Android app.
- Start a user-enabled floating button that opens the video picker above other apps.
- Upload the selected content URI directly to the authenticated YouTube channel.
- Set the video title and visibility (private by default).
- Show transfer progress and the resulting YouTube video link.
- Revoke the app's YouTube upload authorization from the app.

The app does **not** download a Reel from its Instagram URL. A copied Reel link is a webpage link, not the video's media file. The video must already be available as a file on the device or be shared by an app as `video/*`.

## Requirements

- Android 7.0 (API 24) or later.
- A YouTube channel associated with the Google account used to authorize the app.
- Google Play services on the Android device for OAuth authorization.
- Android SDK Platform 36 and JDK 17 to build from source.

## Use

### Choose a saved video

1. Open **ReelToShorts**.
2. Tap **Choose video file** and select a video.
3. Enter a title and description, choose visibility (private by default), and accept the linked Privacy Policy and terms.
4. Tap **Authorize & upload**. Approve the `youtube.upload` permission when Google prompts you.
5. Keep the app open while the transfer runs. The app shows transfer progress and a YouTube link when it completes.

### Use the floating button

1. In YT Uploader, tap **Enable floating button**.
2. Allow **Display over other apps** for **ReelToShorts** in Android Settings, then return to the app and tap **Enable floating button** again.
3. Open the app containing the video and tap **Choose video** on the floating control.
4. Select the video file, set its title, description, and visibility, accept the linked policies, then tap **Authorize & upload**.
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

## Google Cloud and OAuth Setup

Direct uploads require a Google Cloud project and an Android OAuth client:

1. Create or select a project in [Google Cloud Console](https://console.cloud.google.com/).
2. Enable **YouTube Data API v3** for that project.
3. Configure the Google Auth Platform consent screen. For personal testing, use the external testing audience and add the Google account that will upload as a test user.
4. Add the OAuth scope `https://www.googleapis.com/auth/youtube.upload` to the app's requested scopes/consent configuration.
5. Set the consent screen's privacy-policy URL to [PRIVACY_POLICY.md](https://github.com/DINESH-1121/YT-Uploader/blob/main/PRIVACY_POLICY.md) and add the YouTube Terms link presented by the app.
6. Create an **Android** OAuth client using the app ID `com.example.reelstoshorts` and the SHA-1 fingerprint of the certificate used to sign the installed APK.
7. Get the local debug certificate fingerprint with:

	```sh
	./gradlew signingReport
	```

	Use the `SHA1` value under the `debug` variant. On the development machine used for this build, it is `D9:C8:AC:EF:B1:E2:EB:D7:79:EB:09:A4:12:96:D1:35:B1:B2:17:AB`. Debug fingerprints differ between machines; always register the value reported by your own build. A release-signed APK needs its own matching Android OAuth client fingerprint.
8. Wait for the OAuth client configuration to propagate, then install the APK and authorize an upload from the app.

The Android app does not embed an OAuth client secret or require a `google-services.json` file. It requests only the `youtube.upload` scope. Google Play services provides the access token; the app keeps it in memory for the upload and does not persist Google credentials.

The current default YouTube Data API quota includes up to 100 `videos.insert` calls per day per project. Additional quota requires a YouTube API compliance audit. Google may also require OAuth consent-screen verification before the app can authorize users beyond configured test users. Check the current [quota and compliance audit guidance](https://developers.google.com/youtube/data/guides/quota_and_compliance_audits) and OAuth verification requirements for your distribution plan.

## Upload Behavior and Limitations

- The app uses YouTube Data API `videos.insert` with resumable 8 MiB chunks and displays transfer progress. Upload completion means the API accepted the video; YouTube may continue processing it afterward.
- Upload starts only after the user selects/shares a real video file and explicitly taps **Authorize & upload**.
- The app does **not** extract or download the currently playing Instagram Reel. Android isolates Instagram's media from other apps; a Reel must first be available as a local/shared video file.
- YouTube determines whether a video qualifies as a Short under its current eligibility rules. The app sends the description exactly as entered; adding `#Shorts` does not make an ineligible video a Short.
- The access token is not persisted. The app retries transient interruptions while it remains open; if retries are exhausted or the app is closed, start the upload again. Resumable session state is not persisted across app restarts.
- Visibility options are private, unlisted, and public, with private selected by default.

## Permissions and Privacy

- **Display over other apps:** lets the user-enabled floating control appear above Instagram and other apps.
- **Foreground service / special use:** keeps the explicitly enabled floating control active while the user switches apps. Android shows an ongoing notification.
- **Notifications:** used for the foreground-service notification on supported Android versions.
- **Video URI access:** the app streams the selected/shared video directly to YouTube and does not copy it to app storage.
- **Internet:** required to request OAuth tokens and upload video data directly to Google APIs.

The app has no backend and does not store Google credentials or video copies. Android app-data backup is disabled. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for the app's privacy policy and data handling details. Google and YouTube privacy and API policies also apply to authorization and uploads.

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
- Authorization: Google Play services Authorization API with the `youtube.upload` scope.
- Upload: YouTube Data API v3 resumable upload, directly from the device.

## Legal and Policy Links

- [YT Uploader Privacy Policy](PRIVACY_POLICY.md)
- [YT Uploader Terms of Service](TERMS_OF_SERVICE.md)
- [YouTube Terms of Service](https://www.youtube.com/t/terms)
- [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)