This plugin downloads whitelist from url and adds players to ComfyWhitelist

## Commands:
* `/webwl reload` - reloads config and restarts updater (`webwhitelist.reload`)
* `/webwl status` - see the status (`webwhitelist.status`)

### Other permissions:
* `webwhitelist.command` - allow usage of command
* `webwhitelist.*` - grants all permissions above

## Protocol description:
All data is transferred as json and always has this map:

| field | field type | description                                  |
|-------|------------|----------------------------------------------|
| type  | string     | message type                                 |
| id    | string     | unique message id. UUID v4 string by default |
| data  | map        | message data                                 |

`data` contains all other data that depends on `type`
 
`S->C` means from webserver to plugin, `C->S` means from plugin to webserver.

When responding to messages, plugin always adds `sucess` boolean field.
if `sucess` value is `false`then there might be `errors` field with error chain.
Note that below tables can contain detailed description of this field

### Message types

Below tables are contents of `data` field in packet

### `list` - Used to get list of whitelisted player

S->C

| field | field type | description  |
|-------|------------|--------------|

(`data` is empty)

C->S

| field   | field type      | description                 |
|---------|-----------------|-----------------------------|
| players | list of strings | list of whitelisted players |

### `add` - Used to add players to whitelist

S->C

| field   | field type      | description                 |
|---------|-----------------|-----------------------------|
| players | list of strings | list of player names to add |


C->S

| field   | field type      | description                                                            |
|---------|-----------------|------------------------------------------------------------------------|
| success | bool            | true if all players added successfully                                 |
| players | list of strings | players that were not added. <br/>Only exists when `sucess` is `false` |

### `remove` - Used to remove players from whitelist

S->C

| field   | field type      | description                    |
|---------|-----------------|--------------------------------|
| players | list of strings | list of player names to remove |


C->S

| field   | field type      | description                                                              |
|---------|-----------------|--------------------------------------------------------------------------|
| success | bool            | `true` if all players removed successfully                               |
| players | list of strings | players that were not removed. <br/>Only exists when `sucess` is `false` |

### `info` - Used to get info about server and plugin

S->C

| field | field type | description  |
|-------|------------|--------------|

(`data` is empty)

C->S

| field          | field type      | description                 |
|----------------|-----------------|-----------------------------|
| plugin_version | string          | plugin version              |
| players_online | list of strings | list of online player names |
| handlers       | list of strings | list of message types       |
| logs           | list of logs    | list of latest 15 logs      |

### `kick` - Used to kick player. 
Only enabled if `additional-types` is set to `true` in config.yml

S->C

| field          | field type                  | description                                                                                      |
|----------------|-----------------------------|--------------------------------------------------------------------------------------------------|
| player         | string                      | player name to kick                                                                              |
| message        | depends on `message_format` | message to kick player with. (Optional)                                                          |
| message_format | string                      | type of message to parse from `message` field.<br/>Must be `minimessage` or `legacy`. (Optional) |


C->S

| field          | field type      | description                 |
|----------------|-----------------|-----------------------------|

(`data` is empty)

### `unknown` - Only sent when message type from server is not defined on client, always unsuccessful

C->S

| field | field type | description  |
|-------|------------|--------------|

(`data` is empty)

### Log format
| field     | field type           | description                                                                                                    |
|-----------|----------------------|----------------------------------------------------------------------------------------------------------------|
| timestamp | long                 | log creation unix timestamp                                                                                    |
| action    | action               | action for this log.<br/>Might be one of: `ADDED`, `REMOVED`, `CONNECTED`, `DISCONNECTED`, `FAILED_TO_CONNECT` |
| players   | list of player names | list of player names if `action` is `ADD` or `REMOVE`, else empty list                                         |

### Errors response
When some message type cannot be processed or message type not found there will be errors with `success` set to `false`.
Also, there might be `errors` field that will contain list of errors

Error format:

| field | field type | description             |
|-------|------------|-------------------------|
| type  | string     | java class of exception |
| msg   | string     | error message           |
