Looking to report an issue/bug or make a feature request? Please refer to the [README file](https://github.com/mihonapp/mihon#issues-feature-requests-and-contributing).

---

Thanks for your interest in contributing to Mihon!


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/mihonapp/mihon/issues), please comment on it so others are aware.
You do not need to ask for permission nor an assignment.

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes.

## Local Development

To test your changes, you can simply click **Run 'app'** or **Debug 'app'** at the top of Android Studio to build and run the app. No other configurations are needed.

## Building and Installing the Debug App

If you do not have either a **rooted device** or an **emulator without Google Play**, you must use the **Debug build of Mihon**.

To test your changes, you can build and install a debug version of the app. This version will be installed separately from the release version and will have a ".dev" suffix in its name.

1.  **Build the debug APK:**
    Open a terminal in the project root and run the following command:
    ```
    ./gradlew assembleDebug
    ```

2.  **Install the debug app:**
    To install the app on a connected device or emulator, run:
    ```
    ./gradlew installDebug
    ```

## Getting help

- Join [the Discord server](https://discord.gg/mihon) for online help and to ask questions while developing.

# Translations

Translations are done externally via Weblate. See [our website](https://mihon.app/docs/contribute#translation) for more details.


# Forks

Forks are allowed so long as they abide by [the project's LICENSE](https://github.com/mihonapp/mihon/blob/main/LICENSE).

When creating a fork, remember to:

- To avoid confusion with the main app:
    - Change the app name
    - Change the app icon
    - Change or disable the [app update checker](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt)
- To avoid installation conflicts:
    - Change the `applicationId` in [`build.gradle.kts`](https://github.com/mihonapp/mihon/blob/main/app/build.gradle.kts)
- To avoid having your data polluting the main app's analytics and crash report services:
    - If you want to use Firebase analytics, replace [`google-services.json`](https://github.com/mihonapp/mihon/blob/main/app/src/standard/google-services.json) with your own
