# PicoPix PPX2055 Android USB diagnostic test 0.1

This is intentionally a diagnostic first-stage test, not yet a movie player.
It verifies the three risky Android-specific parts before implementing the full AM7xxx frame stream:

1. Android USB-host detection and permission
2. AM7xxx mass-storage to display-mode switch
3. Android MediaProjection permission

## Build
Open the directory in Android Studio (JDK 17), let Gradle sync, then Build > Build APK(s).
Expected APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Test sequence
1. Connect external power to the Y-OTG adapter first.
2. Connect the PPX2055 data plug.
3. Start app and press `USB pruefen`; accept USB permission.
4. If PID is `1101`, press `In Displaymodus umschalten`.
5. Wait 3-5 seconds, then press `USB pruefen` again.
6. Press `Bildschirmfreigabe testen` and accept Android's capture dialog.
7. Copy the entire log and retain the displayed VID/PID, interface and endpoint lines.

## Deliberate quality limits for the later streaming stage
- output: 800 x 480
- JPEG quality: 55
- maximum: 15 fps
- aspect ratio preserved, black bars instead of upscaling/cropping
- latest-frame-wins queue, so old frames are dropped instead of adding latency

## Important
This build does not yet transmit frames. That is deliberate: Android USB descriptors after the mode switch are needed to bind the correct interface and endpoint safely. Sending guessed AM7xxx frame packets can hang the USB interface. The resulting log is the input for stage 0.2, which adds the test image and then MediaProjection streaming.

Protocol sources: libam7xxx (GPL-2.0+) and picopix-ppx2055-linux (GPL-3.0). This project should be distributed under GPL-3.0-or-later once their protocol implementation is incorporated.
