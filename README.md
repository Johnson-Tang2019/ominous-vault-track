# Ominous Vault Track

A lightweight Fabric client mod that highlights ominous vaults and lets you hide vaults you have already handled by right-clicking them.

## Features

- Highlights ominous vaults only; normal vaults are ignored.
- Right-click an ominous vault to locally exclude it from rendering.
- Stores excluded vaults separately by server and dimension.
- Configurable highlight color, tracer color, render radius, tracer item requirement, and refresh behavior.
- Quick config shortcut: `B + X`.
- Cloth Config settings screen with optional Mod Menu integration.

## Requirements

- Fabric Loader
- Fabric API
- Cloth Config
- Mod Menu is optional, but recommended for opening the config screen from the mods list.

## Installation

1. Install Fabric Loader.
2. Install Fabric API and Cloth Config.
3. Put the Ominous Vault Track jar in your `mods` folder.
4. Launch the game and open the config screen with Mod Menu or `B + X`.

## Configuration

The mod is disabled by default. Enable highlighting in the config screen before use.

Available options include:

- Highlight color
- Excluded vault render mode and color
- Tracer line color
- Whether tracer lines require a specific held item
- Required tracer item ID, defaulting to `minecraft:ominous_trial_key`
- Render radius
- Optional timed refresh for locally excluded vaults

## Local Data

Only vaults that you right-click are saved locally. Scanned vaults are kept in memory and are not written to the config file.

Saved records are separated by server address and world dimension.
