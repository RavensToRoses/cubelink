# CubeLink (v0.1, untested)
Android app for the Anbernic RG Cube that sends its built-in buttons and sticks as a Bluetooth HID gamepad.
- Open in Android Studio (it creates the Gradle wrapper), run on the RG Cube. minSdk 28.
- Open the app, tap "Make discoverable", pair from the host's Bluetooth menu, tap the host under "Paired hosts".
- Keep CubeLink in the foreground: it reads input as an app and has no background mode yet.
- Remap: tap a button name, press the physical key, tap "Save profile". Profile JSON format: docs/sample-profile.json (Android key code -> HID button 0..15).
No root, firmware patching or custom ROM is used or required.

## Switch mode (experimental, needs root)
1. Install the Bluetooth device-class Magisk module per its own instructions, reboot.
2. In CubeLink tap the mode button to "Switch Pro Controller" (app restarts).
3. On the Switch: Controllers > Change Grip/Order. Tap "Make discoverable" in CubeLink and wait for it to connect.

## Build without Android Studio
Push this project to a GitHub repo. The included workflow (.github/workflows/build.yml) builds the APK; download it from the run's Artifacts. Unverified: not run yet.
