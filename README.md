# GregTech Calculator Board (GTCalcBoard)

<p align="center">
  <b>English</b> | <a href="README_KR.md">한국어</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg" alt="Minecraft 1.20.1">
  <img src="https://img.shields.io/badge/Loader-NeoForge%20%2F%20Forge%2047.2.0+-orange.svg" alt="Forge">
  <img src="https://img.shields.io/badge/GregTech-CEu%20Modern-blue.svg" alt="GTCEu Modern">
  <img src="https://img.shields.io/badge/EMI-Supported-purple.svg" alt="EMI">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License">
</p>

An in-game node graph calculator for GregTech CEu Modern. Instead of juggling external spreadsheets or web tools to balance your factory lines, you can drop recipes onto an infinite canvas, wire inputs and outputs together, and let the mod figure out the exact machine ratios and power requirements for you.

---

## Features

### 1. Interactive Node Canvas
- Drag, pan, and zoom across an infinite canvas.
- Machine cards show processing time, overclock details, exact EU/t draw, and per-second item/fluid throughput.
- Full EMI integration: add recipes straight from EMI, or hover over ports and press `R` / `U` to look up recipes and uses on the fly.

### 2. Smart Wiring & Auto-Connect
- Drag from any output port to an input port to draw pipeline connections.
- Hit **Auto Connect** to automatically link up all matching item and fluid ports across your board in one go.
- Right-click any wire or port socket to cut the connection.

### 3. Master Anchor & Auto Ratio
- Mark any bottleneck or target product recipe as the **Base Anchor** (`[Base]`).
- Hit **Auto Ratio** to instantly rebalance and scale machine counts across the entire upstream and downstream chain to match your anchor's exact throughput.

### 4. Max Flow & Tier Capping
- Set a global voltage cap (`Cap: LV ~ MAX`) so machines won't overclock past what you can actually build in your current stage of the game.
- Use **Max Flow** to automatically push all machines to the highest allowed tier and group them into multi-block parallel batches (`4x, 8x, 16x...`).

### 5. Blueprint Sharing
- **Share (`Ctrl + C`)**: Copies your entire setup (nodes, tiers, counts, wires, and layout) as a compact string (`GTBOARD:...`).
- **Import (`Ctrl + V`)**: Paste a shared string to load someone else's line layout in a second.
- Easy to share on Discord, forums, or notes.

### 6. Live Process Summary
- Live overlay tracking total power draw (EU/t) and highest tier required.
- Real-time breakdown of raw external inputs needed vs. net output products and byproducts.

---

## Controls & Shortcuts

| Action | Key / Mouse |
| :--- | :--- |
| Open Calculator Board | Configurable in Options -> Controls |
| Pan Canvas | Right Click + Drag or Middle Click + Drag |
| Zoom Canvas | Mouse Wheel (Zooms to cursor) |
| Connect Wire | Left Click & Drag from output port to input port |
| **Smart Match Connect** | **Shift + Drag from output port to input port** (Auto-matches consumer machine count to producer output) |
| Disconnect Wire / Port | Right Click on wire or port socket |
| Set Master Anchor | Click `[Base]` icon on recipe card header |
| Edit Machine Count | Click count box -> type number -> `Enter` or `Esc` |
| Quick Scale Count | `[-]` -1, `[+]` +1, `[/2]` Half, `[x2]` Double |
| Change Voltage Tier | Click or Mouse Wheel over Tier button (`LV`, `MV`, `HV`...) |
| Toggle Overclock Mode | Click `[STD OC]` / `[PERF OC]` button |
| Cycle Parallel | Click `[Par: 1x]` button |
| Copy Blueprint | `[Share]` button or `Ctrl + C` |
| Paste Blueprint | `[Import]` button or `Ctrl + V` |
| Recipe / Uses Lookup | Hover over port and press `R` (Recipes) or `U` (Uses) |
| Scroll Top Toolbar | Mouse Wheel or Left Click + Drag over toolbar |

---

## Requirements

- **Minecraft**: `1.20.1`
- **Mod Loader**: `Forge (47.2.0+)` / `NeoForge`
- **Required Dependencies**: None
- **Recommended**:
  - [GregTech CEu Modern](https://curseforge.com/minecraft/mc-mods/gregtech-ceu-modern)
  - [EMI](https://curseforge.com/minecraft/mc-mods/emi)

> Note: While GregTech is not a hard requirement, the calculator is specifically designed around GTCEu mechanics and voltage tiers, and has not been tested extensively without GregTech CEu Modern installed.

---

## Building from Source

```bash
git clone https://github.com/Amvermain/GregTechCalculatorBoard.git
cd GregTechCalculatorBoard
./gradlew build
```
The compiled jar will be in `build/libs/gtcalcboard-1.20.1-1.0.1.jar`.

---

## License

MIT License - see [LICENSE](LICENSE) for details.
