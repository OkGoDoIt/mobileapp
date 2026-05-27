# Background Audio Validation

Date: 2026-05-27

This file records validation for the continuous watch background-audio path.

## Automated Validation Passed

Mobile:

```sh
./gradlew :libpebble3:jvmTest :pebble:testDebugUnitTest :composeApp:assembleDebug --no-daemon --stacktrace
./gradlew :pebble:compileKotlinIosArm64 :libpebble3:compileKotlinIosArm64 --no-daemon --stacktrace
```

Firmware:

```sh
./waf build
./waf test -M test_background_audio
./waf test
```

Covered areas include packet parsing and 64-bit endianness, stream start/data/gap/stop/checkpoint handling, disconnect interruption state, file-backed transcription task persistence, segment metadata/transcript persistence, coordinator transcription flow, Android app assembly, iOS KMP compile, and PebbleOS background-audio protocol/spool tests.

## Hardware Validation Status

Real hardware validation has not been executed in this environment. The feature should not be considered 100% production-validated until the following matrix has been run with real watches and phones.

Required Android device checks:

- Enable background audio on a paired watch and verify at least 5 minutes of continuous segment creation.
- Run a 30-minute stream and verify segment rotation, checkpoints, storage stats, and transcription status.
- Disconnect for less than the watch spool capacity and verify no user-visible gap for buffered audio.
- Disconnect beyond spool capacity and verify explicit gap metadata appears in the Background Audio screen.
- Kill/restart the Android app/service and verify pending transcription resumes.
- Fill or lower available storage enough to trigger low-storage receiver flags and watch-side pause behavior.

Required iOS device checks:

- Verify the iOS app does not advertise `SupportsBackgroundAudioStreaming` until physical Core Bluetooth background restoration is validated.
- Validate that a connected iOS device reports the feature as phone-unsupported in the Background Audio screen.

Required watch checks:

- Verify background audio coexists with normal dictation and Watch Index Memo.
- Trigger a mic conflict and confirm the gap duration reflects elapsed conflict time.
- Validate battery and throughput behavior during a 30-minute capture.

## Current Conclusion

The implementation is complete for automated build/test coverage, but physical hardware validation remains pending.
