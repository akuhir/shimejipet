# Habibty Android app

Native Kotlin Android overlay companion. Build with JDK 17, Gradle 8.9, Android Gradle Plugin 8.5.2.

## Features
- Select a photo and import it as the pet.
- Round crop option, size/speed/lift controls.
- Movement toggle. OFF keeps the pet stationary.
- Dragging the pet holds it at the release position for 60 seconds.
- Speech bubbles show text for 12 seconds.
- Chocolate-maroon and cream app theme.
- Foreground notification branded Habibty.

## Build
Run `gradle assembleDebug` from the project root. GitHub Actions builds the debug APK on pushes to main.

## Notes
The project is a new source bundle based on the repository structure and requested behaviour. It has not been compiled or device-tested in this environment. Test overlay permission, notification permission, photo import, and Android 11 behaviour on-device.
