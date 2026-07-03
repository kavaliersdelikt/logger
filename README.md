# Logger

A compact, async JSON logger for PaperMC with Discord webhook support and per-player GUI.

## Download
Latest JAR (release):

- [https://github.com/kavaliersdelikt/logger/releases/latest/download/logger-1.5.4.jar](https://github.com/kavaliersdelikt/logger/releases/download/v1.5.4/logger-1.5.4.jar)

## Installation
1. Drop `logger-1.5.4.jar` into your server's `plugins/` folder.
2. Start the server once to generate default `config.yml`.
3. Configure `config.yml` (set `discord-webhook-url` to enable Discord notifications and adjust web panel settings if needed).
4. Reload or restart the server.

## Configuration
Important settings in `src/main/resources/config.yml` (copied to plugin data folder):

- `enable-file-logging`: true/false
- `enable-discord`: true/false
- `discord-webhook-url`: your webhook URL
- `discord-max-retries`: number of webhook retries
- `log-rotation.enabled`: enable log file rotation
- `web-panel.enabled`: enable the built-in dashboard
- `web-panel.port`: dashboard port
- `web-panel.auth-enabled`: enable basic auth for the web panel

## Commands
- `/logger` opens the admin GUI (requires operator)
- `/logger inspect <player>` opens a player's logs
- `/logger queue` shows webhook queue size
- `/logger clearqueue` clears the webhook queue
- `/logger web` shows the web panel URL
- `/logger reload` reloads the plugin configuration

## Changelog
See `CHANGELOG.md` for release notes.

## Contributing
Pull requests welcome — please include tests or clear reproduction steps for bugs.

## License
No license specified. Add a `LICENSE` file to make the repo public-friendly.
