# Third-Party Feature Sources

This project includes small, source-attributed implementations inspired by
`qiin2333/moonlight-vplus`:

- Repository: https://github.com/qiin2333/moonlight-vplus
- Referenced files:
  - `app/src/main/java/com/limelight/nvstream/StreamConfiguration.kt`
  - `app/src/main/java/com/limelight/nvstream/NvConnection.kt`
  - `app/src/main/java/com/limelight/nvstream/http/NvHTTP.kt`
  - `app/src/main/java/com/limelight/BitrateCardController.kt`

Implemented locally:

- Sunshine launch query extensions for VDD, display selection, custom display
  modes, and host-side resolution scale.
- Runtime bitrate adjustment through the host `bitrate` endpoint.
- Resume-current-session behavior hook for switching apps without forcing a
  quit/relaunch.

The local code is written against this repository's Java/Kotlin architecture and
is not a verbatim copy of the upstream implementation.
