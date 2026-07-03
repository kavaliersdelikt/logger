# Changelog

## [1.5.1] - 2026-07-03
- Added a built-in web panel with configurable port and optional basic auth
- Added paginated event browsing and richer summaries for players and deaths
- Improved death logging with coordinates and cause details
- Added operator commands to clear the webhook queue and view the web panel URL
- Suppressed Discord queue-full warnings from the server console
- Filtered out container/item movement events from Discord webhooks while keeping file logging intact
- Added log file rotation after size thresholds

## [1.0.0] - Initial
- Initial plugin with:
  - Async JSON file logging
  - Discord webhook queue with retry/backoff
  - Per-player GUI for inspecting recent logs
  - Configurable filters and thread pool


