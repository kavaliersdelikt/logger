# Changelog

## [1.5.4] - 2026-07-03
- Fixed the web panel event list so new events appear in the visible table again
- Ensured the panel sorts and paginates events newest-first instead of leaving older events on the first page

## [1.5.3] - 2026-07-03
- Fixed the web panel refresh loop so the dashboard remains live and updates automatically again
- Preserved player/world/action filter selections across refreshes to avoid the earlier filter reset issue

## [1.5.2] - 2026-07-03
- Hotfixed player death logging so deaths are recorded reliably even when the primary event path is missed
- Added a fallback damage-based death check with cooldown protection to prevent duplicate entries

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


