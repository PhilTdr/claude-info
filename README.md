# Claude Info

Kleines Tray-Tool, das zeigt, wie viele Tokens Claude Code gerade verbraucht hat und was das ungefähr kostet. Läuft im Hintergrund, ohne dauerhaftes Fenster.

## Was drin ist

- Zwei Sektionen: aktueller Tag und die letzten drei Monate. Jeweils mit Token-Aufschlüsselung (Input, Output, Cache Write 5 min + 1 h, Cache Read) und ungefähren USD-Kosten.
- Hover über die Summe (bei „Heute" oder einem Monat) zeigt ein Tooltip mit der Kostenaufschlüsselung pro Modell — Kosten und Anteil je Modell.
- Preise zieht die App live von [LiteLLM](https://github.com/BerriAI/litellm) — einmal beim Start und danach alle 60 Minuten. Schlägt eine Aktualisierung fehl, bleiben die zuletzt erfolgreich geladenen Preise in Kraft. Klappt schon der allererste Abruf nicht, zeigt das Popup statt der Auswertung einen Hinweis mit „Erneut versuchen"-Button; bis die ersten Preise da sind, läuft ein Spinner.
- Update-Intervall ist 10 Sekunden. Pro JSONL-Datei wird nur das neue Tail-Stück geparst — das bleibt auch bei vielen Sessions schnell.
- Linksklick aufs Tray-Icon öffnet Popup direkt neben dem Klick, mit nativem Blur-Hintergrund (Acrylic auf Windows 10/11, NSVisualEffectView-Vibrancy auf macOS, halbtransparenter Compose-Fallback unter Linux). X schließt das Fenster, App läuft im Tray weiter. Rechtsklick bietet die möglichkeit zum beenden.
- Liest ausschließlich lokal aus `~/.claude/projects/**/*.jsonl`. Keine Anthropic-Calls, kein API-Key, nichts geht raus.

## Installation

Fertige Installer gibt's auf der [Releases-Seite](https://github.com/PhilTdr/claude-info/releases):

| Plattform | Datei |
|-----------|-------|
| Windows   | `ClaudeInfo-*.msi` |
| macOS (Apple Silicon) | `ClaudeInfo-*-arm64.dmg` |
| macOS (Intel) | `ClaudeInfo-*-x64.dmg` |
| Linux (Debian/Ubuntu) | `claude-info_*.deb` |

### Erststart

- **Windows**: SmartScreen-Popup → „Weitere Informationen" → „Trotzdem ausführen". Im Installer-Dialog steht `ClaudeInfo Build` als Aussteller.
- **macOS**: Doppelklick blockt. Rechtsklick auf die `.app` → „Öffnen" → noch mal „Öffnen". macOS merkt sich das pro Maschine.
- **Linux**: `sudo dpkg -i claude-info_*.deb` (oder Paketmanager der Wahl). Ob das Tray-Icon dann tatsächlich auftaucht, hängt von der Desktop-Umgebung ab.

### Build verifizieren (optional)

```bash
# Signatur prüfen — Aussteller muss "ClaudeInfo Build" sein
codesign -dvv ClaudeInfo.app                       # macOS
Get-AuthenticodeSignature .\ClaudeInfo-*.msi       # Windows (PowerShell)

# SHA-256 abgleichen
shasum -a 256 -c SHA256SUMS.txt                    # macOS / Linux
Get-FileHash .\ClaudeInfo-*.msi -Algorithm SHA256  # Windows (PowerShell)

# Build-Provenance über Sigstore (GitHub Artifact Attestations)
gh attestation verify <file> --repo PhilTdr/claude-info
```

### Plattform-Support

| OS      | Tray-Icon     | Popup | AWT-Menu |
|---------|---------------|-------|----------|
| Windows | ✓             | ✓     | ✓        |
| macOS   | ✓             | ✓     | ✓        |
| Linux   | (DE-abhängig) | ✓     | ✓        |

Unter Linux ist `java.awt.SystemTray` einfach so wie es ist — GNOME ohne passende Extension zeigt das Icon z.B. gar nicht. Wenn die Tray-Registrierung scheitert, öffnet die App das Popup einmal direkt; danach ist sie über Schließen + Re-Start nutzbar, aber halt nicht mehr als langlebiges Tray-Programm gedacht.

## Für Entwickler

Du brauchst JDK 17+ mit gesetztem `JAVA_HOME`. CI läuft mit JDK 21.

```bash
./gradlew :desktopApp:hotRun --auto                    # Hot-Reload-Entwicklung
./gradlew :desktopApp:run                              # Normaler Start
./gradlew :shared:jvmTest                              # Tests
./gradlew :desktopApp:packageDistributionForCurrentOS  # Distribution (DMG / MSI / DEB)
```

### Tech Stack

Kotlin mit Compose Multiplatform (JVM-Target, kein Android/iOS) und Material 3. Ktor (CIO) für den LiteLLM-Pricing-Fetch, kotlinx-serialization / -datetime fürs Datums- und JSON-Handling. Tray-Integration über `java.awt.SystemTray` + `PopupMenu`. Für die nativen Blur-Backdrops kommt JNA zum Einsatz: unter Windows wird `user32!SetWindowCompositionAttribute` aufgerufen, unter macOS wird eine `NSVisualEffectView` gewrappt und via Skiko-Reflection die Metal-Clear-Color transparent gesetzt. Signiert wird in der CI mit jsign (Windows) und `codesign` (macOS).

### Architektur

Clean Architecture, MVVM, Repository Pattern.

```
shared/commonMain/    domain (Models inkl. PricingTable/PricingState, Repository-Interfaces) · data/aggregation (reiner Aggregator) · presentation (ViewModel, Compose-UI)
shared/jvmMain/       ClaudeInfoApp (manuelle DI) · JSONL-Parser & Tail-Cache · Pricing-Fetch (Ktor) + Refresh-Loop · settings.json-Reader
desktopApp/           main.kt (Window-Setup, Focus-Loss) · backdrop/ (Windows / macOS / Linux-Fallback) · tray/ (SystemTray + AWT-Menu)
```

`UsageRepository` exponiert drei Flows (`getTodayUsage`, `getMonthUsage`, `getHistoryUsage`), die alle aus einer gemeinsamen `shareIn`-Quelle gespeist werden. Heißt: pro 10-Sekunden-Tick gibt es genau einen Filesystem-Scan und einen Parser-Pass — nicht drei.

### Datenquelle

Quelle sind die `~/.claude/projects/**/*.jsonl`-Dateien. Pro Zeile zählen nur abgeschlossene Assistant-Antworten, also `type == "assistant"` mit gesetztem `message.stop_reason`. Streaming-Zwischenstände werden ignoriert, sonst würde doppelt gezählt. Modellpreise holt die App beim Start und danach alle 60 Minuten von LiteLLM und hält sie im Speicher. Schlägt eine Aktualisierung fehl, gelten weiter die zuletzt erfolgreich geladenen Preise; für Modelle, die der Feed nicht kennt, wird mit 0 $ gerechnet (Tokens werden trotzdem gezählt).

## Credits

Inspiration und Vorbild: [`ccusage`](https://github.com/ryoppippi/ccusage).
Preisdaten: [LiteLLM](https://github.com/BerriAI/litellm).

