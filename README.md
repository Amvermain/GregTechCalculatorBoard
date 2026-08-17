# GregTech Calculator Board (GTCalcBoard)

<p align="center">
  <b>English</b> | <a href="README_KR.md">한국어</a> | <a href="CHANGELOG.md">Changelog</a>
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

### 3. Master Anchor & Bottleneck-Free Auto Ratio (Integer Ceiling)
- Mark any target product or bottleneck machine as the **Base Anchor** (`🎯`).
- Hit **Auto Ratio** to instantly rebalance and scale machine counts across the entire upstream and downstream chain with **Integer Ceiling** ($1, 2, 3...$).
- Enforces $\text{Supply} \ge \text{Demand}$ across all nodes to guarantee that no upstream starvation occurs in real gameplay.

### 4. Large Turbine / Generator Simulation & 48+ Rotor Scanner
- **Dedicated Emerald Theme**: Generator nodes feature a distinctive deep emerald dark theme (`#122218`) and energy green borders (`+EU/t`).
- **3D Visual Rotor Dialog (`[⚙ 220%]` click)**: Scans 48+ native and modpack-added GT rotors in real-time, rendering distinct item icons, efficiency badges, and localized tooltips.
- **Rotor Power & Tier Auto Parallel Derivation**: Selecting a rotor or changing voltage tier automatically calculates the turbine's maximum voltage ($V_{\text{max}} = V_{\text{holder}} \times \frac{P_{\text{rotor}}}{100}$) and sets optimal rated parallels (Par) in 1 second.

### 5. Interactive Numeric Parallel Input & Wheel Control
- **Direct Number Input**: Click `Par` button to type any parallel number directly (e.g. `1152`).
- **Mouse Wheel Control**: Scroll on the `Par` button to quickly scale by 2x (or `Shift + Wheel` for +/- 1 fine stepping).

### 6. Blueprint Sharing
- **Share (`Ctrl + C`)**: Copies your entire setup (nodes, tiers, counts, wires, and layout) as a compact string (`GTBOARD:...`).
- **Import (`Ctrl + V`)**: Paste a shared string to load someone else's line layout in a second.
- Easy to share on Discord, forums, or notes.

### 7. Live Process Summary
- Live overlay tracking total power draw (EU/t) and highest tier required (with generator output subtracted).
- Real-time breakdown of raw external inputs needed vs. net output products and byproducts.

---

## Controls & Shortcuts

| Action | Key / Mouse |
| :--- | :--- |
| Open Calculator Board | Configurable in Options -> Controls |
| Pan Canvas | Right Click + Drag or Middle Click + Drag |
| Zoom Canvas | Mouse Wheel (Zooms to cursor) |
| Connect Wire | Left Click & Drag between ports (Output -> Input or Input -> Output) |
| **Smart Match Connect (Bidirectional)** | **Shift + Drag between ports**<br>• Output -> Input: Scales consumer machine count to producer supply (Integer Ceiling)<br>• Input -> Output: Scales producer machine count to consumer demand (Integer Ceiling) |
| Disconnect Wire / Port | Right Click on wire or port socket |
| Set Master Anchor | Click `🎯` icon on recipe card header |
| Edit Machine Count | Click count box -> type number -> `Enter` or `Esc`, or use `[-]`/`[+]`/`[/2]`/`[x2]` |
| **Adjust Parallel (Par)** | **Click `[Par: 1x]` to type number**, Mouse Wheel to double/halve, Right Click for 1/2 |
| **Equip Turbine Rotor** | Click **`[⚙ 220%]` rotor button** -> Choose from 3D visual rotor dialog |
| Change Voltage Tier | Click or Mouse Wheel over Tier button (`LV`, `MV`, `HV`...) |
| Toggle Overclock Mode | Click `[STD OC]` / `[PERF OC]` button |
| Blueprint Copy / Paste | `Ctrl + C` (Copy) / `Ctrl + V` (Import) in calculator board |
| Parallel Factor (Par) | • **Click**: Type custom integer directly (e.g. 1152)<br>• **Right Click**: Halve parallel<br>• **Mouse Wheel**: Multiply/Divide (Shift+Wheel: +1 / -1) |
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
The compiled jar will be in `build/libs/gtcalcboard-1.20.1-1.0.2.jar`.

---

## License

MIT License - see [LICENSE](LICENSE) for details.
