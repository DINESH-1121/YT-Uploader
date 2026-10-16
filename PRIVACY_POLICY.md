# Privacy Policy

Last updated: 2026-10-16

YT Uploader is an Android application maintained in the [DINESH-1121/YT-Uploader](https://github.com/DINESH-1121/YT-Uploader) repository. This policy explains how the app handles information when you use its YouTube upload feature.

## Information and Permissions

- **Video and metadata:** When you explicitly start an upload, the app reads the selected video through Android's document provider and sends its bytes, title, description, and selected visibility directly to YouTube Data API v3.
- **Google authorization:** Google Play services requests your consent for the `youtube.upload` scope. The app uses the returned access token in memory to make the upload request. It does not store your Google password, access token, or refresh token.
- **Overlay permission:** If enabled, Android permits the app's floating video-picker shortcut to appear above other apps. The app cannot read another app's screen or extract its currently playing video.
- **Network:** Upload data is sent directly from the device to Google/YouTube over HTTPS. YT Uploader does not operate an upload server, analytics service, or advertising service.

## Storage and Sharing

YT Uploader does not copy the selected video into its own storage, keep a resumable upload session across app restarts, or share your video with anyone other than Google/YouTube for the upload you requested. Android backup of app data is disabled. Google and YouTube process the request under their own policies:

- [Google Privacy Policy](https://policies.google.com/privacy)
- [YouTube Terms of Service](https://www.youtube.com/t/terms)
- [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)

## Revoking Access

Use **Disconnect YouTube access** in the app or revoke access through [Google Account security settings](https://security.google.com/settings/security/permissions). The app does not retain YouTube API data or credentials after it closes. Revoke access in Google settings as well if you no longer want this app to authorize uploads.

## Children's Privacy

YT Uploader is not designed for children and does not knowingly collect personal information from children.

## Contact

For privacy questions, open an issue in the [project repository](https://github.com/DINESH-1121/YT-Uploader/issues).