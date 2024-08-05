This plugin downloads whitelist from url and adds players to ComfyWhitelist

## Commands:
* `/webwl reload` - reloads config and restarts updater (`webwhitelist.reload`)
* `/webwl status` - see the status (`webwhitelist.status`)

### Other permissions:
* `webwhitelist.command` - allow usage of command
* `webwhitelist.*` - grants all permissions above

## Protocol description:
All data is transferred as json and always has this map:

| field | field type | description  |
|-------|------------|--------------|
| type  | string     | message type |
| data  | map        | message data |

`data` contains all other data that depends on `type`
 
`S->C` means from webserver to plugin, `C->S` means from plugin to webserver.

When responding to messages, plugin adds `sucess` boolean field.
if `sucess` value is `false`then there might be `error` field with error chain.

### Message types

Below tables are contents of `data` field in packet

### list - Used to get list of whitelisted player

S->C

| field | field type | description  |
|-------|------------|--------------|
(`data` is empty)

C->S

| field   | field type      | description                 |
|---------|-----------------|-----------------------------|
| players | list of strings | list of whitelisted players |

### add - Used to add players to whitelist

S->C

| field   | field type      | description                 |
|---------|-----------------|-----------------------------|
| players | list of strings | list of player names to add |


C->S

| field   | field type | description                            |
|---------|------------|----------------------------------------|
| success | bool       | true if all players added successfully |

### remove - Used to remove players from whitelist

S->C

| field   | field type      | description                    |
|---------|-----------------|--------------------------------|
| players | list of strings | list of player names to remove |


C->S

| field   | field type | description                                |
|---------|------------|--------------------------------------------|
| success | bool       | `true` if all players removed successfully |

### Errors response
When some message type cannot be processed or message type not found there will be errors with `success` set to `false`.
Also, there might be `errors` field that will contain list of errors

Error format:

| field | field type | description             |
|-------|------------|-------------------------|
| type  | string     | java class of exception |
| msg   | string     | error message           |

If json cannot be parsed or this type of message doesn't exist then message type is set to `unknown`