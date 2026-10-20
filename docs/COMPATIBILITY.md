# Compatibility (nothing here is verified)
| Host | Status |
|---|---|
| PC / Android / generic Bluetooth hosts | Generic mode. Expected to work. UNTESTED. |
| Nintendo Switch 2 | Switch mode is EXPERIMENTAL and UNTESTED. Do not treat as supported until tested on a real console. |
Switch mode needs: root plus a Magisk module that changes the Bluetooth device class (JoyCon Droid's FAQ names SwitchControllerCOD for Android 13+; check its current instructions). Without it the Switch will likely ignore the device.
Known risks: the HID descriptor, subcommand replies and stick calibration were written from memory of public protocol notes and may be wrong; button mapping is positional (south=B, east=A, west=Y, north=X) and may need remapping; no motion or rumble.
