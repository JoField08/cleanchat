# CleanChat

CleanChat is a lightweight Minecraft client-side mod that automatically filters unwanted chat messages.

## Features

- Create and manage custom filter rules
- Test filters before adding them
- Support for different filter modes, including strings and regular expressions
- Choose an action for matching messages
- Enable or disable individual rules
- Enable or disable CleanChat globally
- Scroll through and manage large rule lists
- Save your configuration

## Filter Tester

The built-in Filter Tester lets you check a pattern against a chat message before creating a rule.

Enter:

1. The chat message
2. The filter pattern
3. The desired filter mode

The tester shows whether the message matches the pattern.

You can use **Add this selected Filter** to create a rule from the current tester settings.

## Filter Rules

Each rule contains:

- **Enabled** — Enable or disable the rule
- **Pattern** — Text or pattern to match
- **Mode** — How the pattern should be interpreted
- **Action** — What happens when the rule matches
- **Delete** — Remove the rule

Click **+ Add rule** to create a new rule manually.

## Global Toggle

Use the **CleanChat: ON/OFF** button to enable or disable the mod.

Disabling CleanChat does not remove your configured rules.

## Saving

Click **Save** to save your current configuration and close the settings screen.

> Changes are applied immediately, but saving keeps them for future sessions.

## Example

A simple string filter:

```text
Welcome to the server!
