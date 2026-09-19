# Wordbook (Android)

Your vocab app as a real Android app, with a home screen widget.

- The app is your Wordbook web app running inside the app (`app/src/main/assets/index.html`).
- The widget shows the word of the day (`WordWidget.kt`). Tap it to open the app, tap the shuffle icon for another word.
- Words and settings are shared with the widget automatically. Nothing to set up.

## Get the APK without a computer setup (GitHub)

1. Make a free GitHub account and create a new repository (private is fine).
2. Upload everything in this folder to it. Make sure the hidden `.github` folder goes up too
   (if it doesn't, use Add file > Create new file, type `.github/workflows/build-apk.yml`, and paste in that file's contents).
3. Open the **Actions** tab, pick **Build APK**, and let it run (about 5 minutes). Use **Run workflow** if it doesn't start on its own.
4. Open the finished run, download the **wordbook-apk** artifact, unzip it, and you get `app-debug.apk`.
5. Send the APK to your phone and open it. Android will ask you to allow installs from that app (Files or Chrome). Allow it.

## Or build it in Android Studio

Open this folder in Android Studio, let it sync, then Build > Build APK(s). The APK ends up in `app/build/outputs/apk/debug/`.

## Add the widget

Touch and hold an empty spot on the home screen, tap Widgets, find Wordbook, and drag it out.
You can resize it. The card options on the Widget tab (part of speech, meaning, example, size, color, theme) apply to it.

## Moving your words over from the old version

In the old app: Widget tab > Your data > Copy backup, and paste it into a note.
In this app: Widget tab > Your data > Restore backup, paste, tap Restore words.
