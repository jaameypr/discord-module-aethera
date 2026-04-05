# discord-module-aethera

A Discord integration module for the [Aethera](https://github.com/jaameypr/aethera-next) Minecraft server management panel.

## Features

- **Chat Relay** — Forwards Minecraft player chat to a Discord channel (default formatting only)
- **Join/Leave Events** — Posts join and leave notifications to a Discord channel
- **Whitelist Requests** — When a player is denied due to whitelist, sends an approval request to Discord with a button
- **Guild Management** — The bot can create Discord server invites and list available channels

## Requirements

- Aethera ≥ 0.2.0
- A Discord Application with a bot token ([Discord Developer Portal](https://discord.com/developers/applications))

## Configuration

After installing the module via Aethera's module registry, configure the following in the module's detail view:

| Variable             | Description                                                   |
|----------------------|---------------------------------------------------------------|
| `DISCORD_BOT_TOKEN`  | Bot token from Discord Developer Portal (Bot → Token)         |
| `DISCORD_CLIENT_ID`  | Application ID (General Information → Application ID)        |
| `DISCORD_PUBLIC_KEY` | Public key for signature verification (General Information)  |
| `AETHERA_CALLBACK_URL` | Internal Aethera URL (default: `http://aethera-app:3000`) |

## Discord Bot Permissions

When inviting the bot, the following permissions are required:
- View Channels
- Send Messages
- Embed Links
- Create Invite
- Read Message History

## Architecture

The module runs as an internal Docker container on `aethera-net`. It:
1. Maintains a persistent Discord bot connection via JDA (Gateway WebSocket)
2. Polls Aethera's internal log API every 3 seconds for log lines from configured servers
3. Processes log lines with regex patterns to detect chat/join/leave/whitelist events
4. Posts Discord messages via the bot
5. Receives button interactions from Discord users via JDA's event system
6. Calls Aethera's callback endpoint to execute whitelist commands

### Chat Regex Note

Chat relay uses the standard Minecraft log format `<PlayerName> message`. This will NOT work
correctly when server-side chat plugins (EssentialsChat, ChatControl, etc.) override the format.

### Whitelist Detection

Supports two whitelist rejection log formats:
- **Legacy**: `PlayerName[/IP] logged in but they are not on the white-list`
- **Modern**: `name='PlayerName'...lost connection: You are not white-listed on this server`

Player UUIDs are extracted from the log when available, otherwise looked up via the Mojang API.
Player skins are fetched from [Crafatar](https://crafatar.com).

## API

All endpoints require `Authorization: Bearer <AETHERA_API_KEY>` unless noted.

| Method | Path                                 | Description                         |
|--------|--------------------------------------|-------------------------------------|
| GET    | `/api/guilds`                        | List guilds the bot is in           |
| GET    | `/api/guilds/{guildId}/channels`     | List text channels in a guild       |
| POST   | `/api/guilds/{guildId}/invite`       | Create a Discord invite             |
| GET    | `/api/guilds/bot-invite`             | Get the bot OAuth2 invite URL       |
| GET    | `/api/servers/{id}/config`           | Get Discord config for a server     |
| PUT    | `/api/servers/{id}/config`           | Update Discord config               |
| DELETE | `/api/servers/{id}/config`           | Remove Discord config               |
| GET    | `/api/servers/{id}/whitelist-requests` | List pending whitelist requests   |
| POST   | `/api/servers/{id}/log`              | Push a single log line              |
| GET    | `/actuator/health`                   | Health check (no auth)              |
