# Claude Info

Small tray tool that shows how many tokens Claude Code has used, what that would have cost in API usage, and how close you are to your 5-hour and weekly limits.

<img src="docs/screenshot.png" alt="Claude Info Screenshot" style="max-width: 350px;">

## What's inside

- Usage limits: 5-hour limit and the weekly limit.
- Usage: current day and the last three months. Each with a token breakdown (input, output, cache write 5 min + 1 h, cache read) and approximate USD costs.
- Hovering over the total shows a tooltip with the per-model cost breakdown.
- Prices are fetched from [LiteLLM](https://github.com/BerriAI/litellm).
- Usage updates continuously in the UI.
  - Token usage is read from the JSONL file every 10s. (Only the new tail segment is parsed, so it stays performant even with many sessions.)
  - Prices are fetched from the LiteLLM API every 60 min.
  - Usage limits are fetched from the Claude API every 10 min.
- Left-clicking the tray icon opens the UI in a popup with a native blur backdrop (Acrylic on Windows 10/11, NSVisualEffectView vibrancy on macOS, semi-transparent Compose fallback on Linux). X closes the window, the app keeps running in the tray. Right-click offers a "Start with Windows/macOS/Linux" toggle and the option to quit.
- Token usage and costs are computed entirely locally from `~/.claude/projects/**/*.jsonl`. The usage-limit bars are the exception: they come from Claude's usage endpoint, authenticated with the OAuth token Claude Code already stores in `~/.claude/.credentials.json` (used read-only, refreshed automatically when expired).
- Update check

## Installation

Ready-made installers are available on the [releases page](https://github.com/PhilTdr/claude-info/releases):

| Platform | File |
|-----------|-------|
| Windows   | `ClaudeInfo-*.msi` |
| macOS (Apple Silicon) | `ClaudeInfo-*-arm64.dmg` |
| macOS (Intel) | `ClaudeInfo-*-x64.dmg` |
| Linux (Debian/Ubuntu) | `claude-info_*.deb` |

### First launch

- **Windows**: SmartScreen popup → "More info" → "Run anyway". The installer dialog lists `treder.dev Apps` as the publisher.
- **macOS**: Open with double click → App is blocked.
  - Option 1: Right-click the `.app` → "Open" → "Open" again. macOS remembers this per machine.
  - Option 2: Settings → Privacy & Security → "ClaudeInfo" was blocked to protect your Mac → Open Anyway [Screenshot](docs/mac_settings_security.png)
- **Linux**: `sudo dpkg -i claude-info_*.deb`. Whether the tray icon actually shows up depends on the desktop environment.

## For developers

You need JDK 17+ with `JAVA_HOME` set. CI runs on JDK 21.

```bash
./gradlew :desktopApp:hotRun --auto                    # Hot-reload development
./gradlew :desktopApp:run                              # Normal start
./gradlew :shared:jvmTest                              # Tests
./gradlew :desktopApp:packageDistributionForCurrentOS  # Distribution (DMG / MSI / DEB)
```

### Tech stack

Kotlin with Compose Multiplatform (JVM target for desktop)
Material 3
Ktor (CIO) for the LiteLLM pricing fetch, the Claude usage-limit fetch + OAuth token refresh, and the GitHub release/update check
kotlinx-serialization / -datetime for date and JSON handling
Tray integration via `java.awt.SystemTray` + `PopupMenu`
The native blur backdrops use JNA: on Windows `user32!SetWindowCompositionAttribute` is called, on macOS an `NSVisualEffectView` is wrapped and the Metal clear color is made transparent via Skiko reflection. Signing happens in CI with jsign (Windows) and `codesign` (macOS).

### Architecture

Clean Architecture, MVVM, Repository Pattern.

```
shared/commonMain/      domain (models, repository interfaces)
                        data/aggregation (pure aggregator)
                        presentation (ViewModel, Compose UI)
shared/jvmMain/         ClaudeInfoApp (manual DI)
                        JSONL parser & tail cache
                        pricing fetch (Ktor) + refresh loop
                        GitHub update check (Ktor)
                        usage-limit fetch (Ktor) + 10-min poll
                        OAuth token refresh/provider + credentials.json read/write
                        settings.json reader
desktopApp/             main.kt (window setup, focus loss)
                        backdrop/ (Windows / macOS / Linux fallback)
                        tray/ (SystemTray + AWT menu)
```

### Data source

Token usage: the source is the `~/.claude/projects/**/*.jsonl` files. Per line, only completed assistant responses count, i.e. `type == "assistant"` with `message.stop_reason` set. Streaming intermediates are ignored, otherwise they would be counted twice.
Model prices: the source is [LiteLLM](https://github.com/BerriAI/litellm).
Usage limits: the rolling 5-hour and weekly utilization come from Claude's OAuth usage endpoint (`GET https://api.anthropic.com/api/oauth/usage`), authenticated with the OAuth token in `~/.claude/.credentials.json`. The token is used read-only and refreshed automatically (then written back) once expired. The endpoint is polled every 10 min; rate-limit or auth errors degrade to a warning icon instead of failing.
