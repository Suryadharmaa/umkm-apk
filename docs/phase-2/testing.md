# Phase 2 verification

Build with JDK 17 or 21 and an Android SDK with Platform/Build Tools 36. Run the checked-in Gradle wrapper.

```shell
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

The connected suite uses JUnit, Room and Compose. Run it on a disposable API 28 AOSP emulator at 720×1440, 320 dpi, 1.0 font scale with English (US) system locale and portrait orientation. App labels and money formatting are Indonesian. The real activity UI flow writes and archives disposable catalog records. The database test uses a separate uniquely named database file and verifies CRUD across close/reopen. The screenshots use isolated Compose fixtures and reject unexpected pixel changes above 0.1% against the checked-in baselines.

## Refreshing screenshot baselines

Only record a new baseline after reviewing an intentional visual change:

```shell
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class id.umkmku.pos.ScreenTest -e recordScreenshots true id.umkmku.pos.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/id.umkmku.pos/files/screenshots/dashboard.png app/src/androidTest/assets/screenshots/dashboard.png
adb pull /sdcard/Android/data/id.umkmku.pos/files/screenshots/cashier.png app/src/androidTest/assets/screenshots/cashier.png
./gradlew :app:connectedDebugAndroidTest
```

For full-app evidence, use a fresh disposable installation and run CatalogFlowTest with instrumentation argument captureApp=true. This exercise retains the created product for the force-stop/relaunch check and writes four full-window images to the same screenshot directory. The first baseline uses the supplied reference for color and grouping direction. Original Figma review, additional device sizes and assistive technology checks remain release work.


Phase 2 exercises the catalog and reopen portion of A01 and A02, the draft/cart portion of A03, and the shipped screen states from A16. Full cash/QRIS, historical receipts, reports and backup cases remain in the MVP matrix.

## Run evidence

- Gradle debug app and instrumentation APKs built successfully.
- JUnit local suite passed 3 tests. The API 28 connected suite passed 6 tests, including both screenshot baselines.
- Verified process restart with adb force-stop and launch. The PID changed and Kasir still showed the saved product, Rp6.000 price, and Minuman category.
- See [full-window emulator evidence](screenshots/README.md).

## Phase 2 quality gate

CI builds the debug application and runs JUnit on pull requests. Locally, the debug build passed with 4 unit tests and 8 API 28 emulator tests. The compact steppers retained 48 dp touch targets and were exercised with a long product name in a 320 dp layout at 200% font size. Cart restoration now drops malformed rows and bounds its saved payload.

Intentionally reviewed and refreshed the Cashier and Cart screenshots after adjusting row spacing and replacing quantity text buttons. The Dashboard baseline remained byte-for-byte identical.
