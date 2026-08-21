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

An in-game node graph calculator and flowchart editor for GregTech CEu Modern and EMI. Designed for planning and balancing complex multi-step production lines directly inside Minecraft without relying on external spreadsheets or web calculators.

---

## Features

### 1. Multiplayer Shared Team Workspaces
- **Real-Time Collaboration**: Share and edit factory flowcharts collaboratively with team members on dedicated servers.
- **FTB Teams & Vanilla Scoreboard Integration**: Automatically maps team permissions, roles, and memberships.
- **Granular Per-Page Edit Locks & Presence**: Acquires temporary edit locks during node/canvas interaction to prevent concurrent overwrite collisions, with live teammate presence indicators.
- **Frictionless Auto-Sync**: 3-second inactivity debounced auto-commit and screen exit saving, accompanied by revision history logs and personal forking (`Personal Board`).
- **Administrative Page Protection**: Page deletion restricted to Team Owners, Officers, and Server Admins (OPs).

### 2. Parametric Recipe Search & Contextual Auto-Wiring
- **Boolean & Parametric Search Engine**: Filter recipes using multi-token AND (`&`/space), OR (`|`), NOT (`!`), `@mod`, `#tags`, `[machine_category]`, and exact `"quotes"`.
- **Drag-to-Search & Auto-Wire**: Drag from any port onto empty canvas to query matching consumers or producers, and spawn the machine pre-wired with optional ratio-matching (`Shift`).

### 3. Global Multi-Page Balance Dashboard
- **Multi-Page Production Aggregation**: Evaluate net material throughput, raw material deficits, surpluses, and global EU/t power balance across selectable preset pages (`B` hotkey).
- **Flow Category Filtering & Drilldown**: Filter by Deficit, Surplus, and Balanced items, with per-item page contribution popups showing exact per-page breakdowns.

### 4. Interactive Node Canvas & Singleplayer Pause Control
- **Level of Detail (LOD) Rendering**: Switches to lightweight 2D rendering when zoomed out to maintain performance on charts with 1,200+ nodes.
- **Singleplayer Pause Toggle**: Switch between `Pause: ON` and `Pause: OFF` to choose whether the game world freezes while planning lines.
- **EMI Integration**: Drag recipes directly from EMI or hover over ports and press `R` (Recipes) / `U` (Uses) for in-place recipe inspection.

### 5. Multi-Tab Presets & Compound Modules
- **Multi-Tab Pages**: Create, switch, rename, and manage independent calculation flowcharts per production branch.
- **Compound Modules (`Ctrl + G`)**: Collapse sub-graphs into a single module card with automatic internal byproduct encapsulation, proportional scaling, and 1-click restoration (`Expand`).

### 6. Undo, Redo, & Clipboard System
- **History Stack**: Full `Ctrl + Z` (Undo) and `Ctrl + Y` / `Ctrl + Shift + Z` (Redo) support across node, wire, grouping, and property modifications.
- **Clipboard & Box Selection**: Marquee box selection, batch dragging, Cut (`Ctrl + X`), Copy (`Ctrl + C`), Paste (`Ctrl + V`), and Duplicate (`Ctrl + D`).

### 7. Wiring & Smart Auto-Connect
- Manual port-to-port bezier curve wiring.
- **Auto Connect**: Automatically scans and connects matching item and fluid ports across the board.
- **Smart Match Connect (`Shift + Drag`)**: Automatically scales target machine counts using integer ceiling calculation upon wire connection.
- Right-click any wire curve or port socket to disconnect.

### 8. Base Anchor & Integer Ceiling Auto Ratio
- Designate any bottleneck or target machine as the Base Anchor (`Anchor`).
- **Auto Ratio**: Scales all upstream machines backwards to satisfy the anchor's input requirement using integer ceiling ($1, 2, 3...$), ensuring $\text{Supply} \ge \text{Demand}$.

### 9. Large Turbine Simulation & Rotor Scanner
- Generator cards with distinct power output display.
- **Dynamic Rotor Dialog**: Scans installed GT addons and modpacks to extract rotor efficiency (%), power (%), durability, and maximum voltage ratings.
- **Automatic Parallel Calculation**: Evaluates $V_{\text{max}} = V_{\text{holder}} \times \frac{P_{\text{rotor}}}{100}$ and configures rated parallels.

### 10. Dynamic GTCEu Addon & Hardware Optimization
- **Dynamic Runtime Indexing**: Automatically discovers coils, rotors, hatches, and machines from any installed GTCEu addon or modpack at runtime without hardcoding.
- **Heating Coils**: Accounts for coil temperatures (Cupronickel 1800K to Trinium 9000K+), duration modifiers, and speed penalties.
- **Parallel Hatches**: Supports standard and constant-power (Absolute) parallel hatches with direct numeric input and mouse wheel scaling.
- **Configurable Maintenance Hatches**: Supports Max Speed (0.9x duration) and Max Eco (1.1x duration) modes.
- **Modpack Compatibility**: Tested with Star Technology, Monifactory, Nomifactory Modern, TerraFirmaGreg, and custom datapack/KubeJS recipes.

### 11. In-Game Guidebook & Interactive Tutorial
- **8-Chapter In-Game Manual**: Reference manual covering basics, search syntax, wiring, master anchor, modules, team sync, presets, and shortcuts.
- **6-Step Interactive Tutorial**: Isolated tutorial workspace with curated dummy recipes.

### 12. Text Blueprint Sharing & Live Process Summary
- **Blueprint Import / Export**: Export or import complete flowchart layouts as compressed text strings (`GTBOARD:...`).
- **Live Process Summary**: Real-time overlay tracking total EU/t draw, highest active voltage tier, raw material requirements, and net product output.

---

## Controls & Shortcuts

| Action | Key / Mouse |
| :--- | :--- |
| Open Calculator Board | Configurable in Options -> Controls |
| Pan Canvas | Right Click + Drag or Middle Click + Drag |
| Zoom Canvas | Mouse Wheel (Zooms to cursor) |
| Quick Add Recipe | Space or Double-Click on empty canvas space |
| Marquee Box Selection | Left Click + Drag on empty canvas space (Shift + Click to toggle) |
| Multi-Node Dragging | Drag any selected node to move all selected nodes together |
| Group into Module | Ctrl + G |
| Undo / Redo | Ctrl + Z (Undo) / Ctrl + Y or Ctrl + Shift + Z (Redo) |
| Clipboard Operations | Ctrl + C (Copy) / Ctrl + X (Cut) / Ctrl + V (Paste) / Ctrl + D (Duplicate) |
| Select All Nodes | Ctrl + A |
| Delete Selected Nodes | Delete or Backspace |
| Rename Machine Title | Double-Click on machine card header title |
| Toggle Hotkey Guide HUD | H |
| Toggle Global Balance Dashboard | B |
| Connect Wire | Left Click & Drag between ports |
| Drag to Search & Auto-Wire | Drag from port onto empty canvas -> Select recipe |
| Smart Match Connect (Bidirectional) | Shift + Drag between ports (Integer Ceiling matching) |
| Disconnect Wire / Port | Right Click on wire or port socket |
| Set Master Anchor | Click Anchor icon on recipe card header |
| Edit Machine Count | Click count box -> type number -> Enter / Esc, or use [-] / [+] / [/2] / [x2] |
| Adjust Parallel (Par) | Click [Par] to type number, Mouse Wheel to scale, Right Click for 1/2 |
| Equip Turbine Rotor | Click rotor button -> Select from rotor dialog |
| Change Voltage Tier | Click or Mouse Wheel over Tier button (LV, MV, HV...) |
| Toggle Overclock Mode | Click [STD OC] / [PERF OC] button |
| Blueprint Text Export / Import | Click [Share] / [Import] on top toolbar |
| Recipe / Uses Lookup | Hover over port and press R (Recipes) or U (Uses) |
| Scroll Top Toolbar / Tabs | Mouse Wheel or Left Click + Drag over toolbar / tab bar |

---

## Installation & Server Compatibility

GregTech Calculator Board is completely optional on both client and server sides:

- **Client Only (Vanilla / Non-Modded Server)**: You can install the mod on your client and join any multiplayer server, even if the server does not have the mod installed. Personal calculation boards remain fully functional.
- **Server Only (Vanilla Clients)**: Server owners can install the mod on dedicated servers without requiring connecting clients to have the mod installed.
- **Both Client & Server**: When installed on both client and server, real-time multiplayer team workspace synchronization and live collaboration features are enabled.

---

## Requirements

- **Minecraft**: `1.20.1`
- **Mod Loader**: `Forge (47.2.0+)` / `NeoForge`
- **Required Dependencies**: None
- **Recommended**:
  - [GregTech CEu Modern](https://curseforge.com/minecraft/mc-mods/gregtech-ceu-modern)
  - [EMI](https://curseforge.com/minecraft/mc-mods/emi)
  - [FTB Teams](https://curseforge.com/minecraft/mc-mods/ftb-teams-forge) (Optional, for multiplayer team sync)

> Note: While GregTech is not a hard requirement, the calculator is specifically designed around GTCEu mechanics and voltage tiers, and has not been tested extensively without GregTech CEu Modern installed.

---

## Building from Source

```bash
git clone https://github.com/Amvermain/GregTechCalculatorBoard.git
cd GregTechCalculatorBoard
./gradlew build
```
The compiled jar will be located in `build/libs/gtcalcboard-1.20.1-2.0.0-alpha.2.jar`.

---

## Developer & Architecture Documentation

- **[Detailed Code Specification (Korean)](docs/ko_kr/CODE_SPECIFICATION.md)**: 전체 시스템 아키텍처, 5대 그래프 알고리즘, 오버클럭 연산 공식, `CategoryCapabilityMatrix`, 멀티플레이 동시성 락 프로토콜 및 영속화 스키마.
- **[Detailed Code Specification (English)](docs/en_us/CODE_SPECIFICATION.md)**: Full system architecture, 5 graph algorithms, overclocking formulas, `CategoryCapabilityMatrix`, multiplayer concurrency lock protocol, and SavedData persistence schemas.
- **[Architecture & Developer Guide (English)](docs/ARCHITECTURE.md)**: Internal solver engine and canvas rendering pipeline.
- **[v2.0 Development Plan](docs/DEVELOPMENT_PLAN_V2.md)**: Multiplayer and FTB Teams roadmap.
- **[RFC-V2-005 (CategoryCapabilityMatrix)](docs/RFC_V2_CATEGORY_CAPABILITY_MATRIX.md)**: Deterministic recipe capability matrix RFC.

---

## Special Thanks

- **The Reel One** - For providing UX/UI feedback, design suggestions, and community testing.

---

## License

MIT License - see [LICENSE](LICENSE) for details.
