# HyperDesk

HyperDesk is an independent Android desktop launcher inspired by the supplied HyperDroid APK. It recreates the desktop workflow with its own source and UI.

## Implemented in this iteration

- Android Home launcher entry point, desktop background, shortcut column, Start Menu, search box, taskbar, and quick-launch controls.
- Internal app windows with move, resize, minimize, maximize, restore, and close controls.
- Built-in Browser (WebView), Notepad with local autosave, terminal command interpreter, Settings, app list, and Task Manager panel.
- File Explorer entry points through Android's document picker.
- Three desktop wallpaper color themes and taskbar shortcuts.
- The terminal supports `help`, `date`, `apps`, `battery`, `storage`, `open <name>`, `clear`, and `about`.

## Android platform boundaries

HyperDesk can launch installed Android apps, but Android does not let an ordinary launcher embed another app's Activity inside its own resizable window. Those apps open under Android's normal app switching behavior. The built-in terminal is a command interpreter, not unrestricted CMD or a Linux shell. Task Manager can show HyperDesk windows and device memory/battery/storage, but Android restricts process inspection and force-stop controls for other apps. File access is granted through Android's document picker.

## Build

Open this folder in Android Studio, allow Gradle sync, then build the `app` debug variant. Select **HyperDesk** as the default Home app in Android's Home app settings.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, and minimum Android 8.0 (API 26). A local Android SDK is required to build an APK.
