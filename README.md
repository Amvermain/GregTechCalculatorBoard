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

### 1. Interactive Node Canvas & LOD Rendering
- Drag, pan, and zoom across an infinite canvas.
- Batched wire rendering and Level of Detail (LOD) mode for large charts with many nodes.
- Machine cards show processing time, overclock details, exact EU/t draw, and per-second item/fluid throughput.
- Full EMI integration: add recipes straight from EMI, or hover over ports and press `R` / `U` to look up recipes and uses on the fly.

### 2. Multi-Tab Pages & Composite Modules (Subgraphs)
- **Multi-Tab Pages**: Create, switch, rename, and manage multiple independent calculation boards.
- **Composite Modules (`[📦 Module]`)**: Group sub-processes into a single module card and expand (`⤢`) them back when needed.

### 3. Smart Wiring & Auto-Connect
- Drag from any output port to an input port to draw pipeline connections.
- Hit **Auto Connect** to automatically link up matching item and fluid ports across the board.
- **Smart Match Connect (`Shift + Drag`)**: Automatically scales machine counts with Integer Ceiling when dragging between ports.
- Right-click any wire or port socket to cut the connection.

### 4. Master Anchor & Auto Ratio (Integer Ceiling)
- Mark any target product or bottleneck machine as the **Base Anchor** (`🎯`).
- Hit **Auto Ratio** to rebalance and scale machine counts across connected nodes with **Integer Ceiling** ($1, 2, 3...$).
- Enforces $\text{Supply} \ge \text{Demand}$ across nodes to prevent upstream starvation.

### 5. Large Turbine / Generator Simulation & Rotor Selection
- **Generator Cards**: Distinct emerald panel theme (`+EU/t`) for easy identification.
- **3D Rotor Dialog (`[⚙ 220%]` click)**: Choose turbine rotors from installed GT addons and modpacks.
- **Auto Parallel Calculation**: Automatically calculates the turbine's maximum voltage ($V_{\text{max}} = V_{\text{holder}} \times \frac{P_{\text{rotor}}}{100}$) and sets rated parallels (Par).

### 6. Interactive Numeric Parallel Input
- **Direct Number Input**: Click `Par` button to type custom parallel numbers directly (e.g. `1152`).
- **Mouse Wheel Control**: Scroll on the `Par` button to scale by 2x (or `Shift + Wheel` for +/- 1 stepping).

### 7. In-Game Interactive Tutorial
- Built-in step-by-step interactive tutorial covering recipes, wiring, ratios, and modules.

### 8. Blueprint Sharing
- **Share (`Ctrl + C`)**: Copies the board layout and configurations as a string (`GTBOARD:...`).
- **Import (`Ctrl + V`)**: Paste a shared string to load blueprints.

### 9. Live Process Summary
- Overlay tracking total power draw (EU/t) and highest tier required.
- Summary of raw inputs needed vs. net output products and byproducts.

---

## Controls & Shortcuts

| Action | Key / Mouse |
| :--- | :--- |
| Open Calculator Board | Configurable in Options -> Controls |
| Pan Canvas | Right Click + Drag or Middle Click + Drag |
| Zoom Canvas | Mouse Wheel (Zooms to cursor) |
| Marquee Box Selection | Left Click + Drag on empty canvas space |
| Multi-Node Dragging | Drag any selected node to move all selected nodes together |
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
| Recipe / Uses Lookup | Hover over port and press `R` (Recipes) or `U` (Uses) |
| Scroll Top Toolbar / Tabs | Mouse Wheel or Left Click + Drag over toolbar / tab bar |

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
The compiled jar will be in `build/libs/gtcalcboard-1.20.1-1.0.5.jar`.

---

## License

MIT License - see [LICENSE](LICENSE) for details.
