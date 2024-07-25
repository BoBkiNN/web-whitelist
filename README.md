This plugin downloads whitelist from url and adds players to ComfyWhitelist

## Commands:
* `/webwl reload` - reloads config and restarts updater (`webwhitelist.reload`)
* `/webwl status` - see the status (`webwhitelist.status`)
* `/webwl switch` - switch auto-sync (`webwhitelist.switch`)
* `/webwl update` - force-sync whitelist (`webwhitelist.update`)

### Other permissions:
* `webwhitelist.command` - allow usage of command
* `webwhitelist.*` - grants all permissions above

Whitelist format is json list like:
```json
["player1", "player2"]
```