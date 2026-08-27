# GregTech Calculator Board (GTCalcBoard)

<p align="center">
  <b>English</b> | <a href="README_KR.md">한국어</a> | <a href="CHANGELOG.md">Changelog</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg" alt="Minecraft 1.20.1">
  <img src="https://img.shields.io/badge/Loader-NeoForge%20%2F%20Forge%2047.2.0+-orange.svg" alt="Forge">
  <img src="https://img.shields.io/badge/GregTech-CEu%20Modern-blue.svg" alt="GTCEu Modern">
  <img src="https://img.shields.io/badge/Recipe%20Viewer-EMI%20%2F%20JEI%20Supported-purple.svg" alt="EMI & JEI">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License">
</p>

An in-game node graph calculator and flowchart editor for GregTech CEu Modern, EMI, JEI, and multi-mod ecosystems. Designed for planning and balancing complex multi-step production lines directly inside Minecraft without relying on external spreadsheets or web calculators.

---

## Features

### 1. Unified Pluggable Recipe Viewer SPI (EMI, JEI, & Vanilla)
- **Multi-Mod Recipe Viewer Compatibility**: Automatically integrates with **EMI**, **JEI (Just Enough Items)**, and **JEI++ (Just Enough Calculation / BoM)**, selecting the optimal viewer adapter at runtime.
- **Favorites Dock Panel (`[⭐ Favorites (N) ▶]`)**: Collapsible left sidebar for instant 1-click or drag-and-drop node placement from bookmarked recipes.
- **Native Recipe Transfer (`[+]` Button)**: Click EMI's native `[+]` button while viewing recipes to immediately add and open the node on the active board canvas.
- **Pure Vanilla Fallback**: Fallback adapter operates offline without recipe viewer mods using Minecraft's native `RecipeManager`.

### 2. In-Place Alternative Recipe Switching
- **One-Click Recipe Switcher**: Switch recipes on existing nodes without deletion via `[🔄 Switch Recipe]` in `MachineConfigDialog` or right-click context menu.
- **Smart Wire Preservation**: Automatically preserves incoming/outgoing wire connections for matching item and fluid ports (`IngredientStack.getId()`).
- **Undo / Redo Support**: Fully integrated with the delta history stack (`Ctrl + Z` / `Ctrl + Y`).

### 3. Multiblock Construction Bill of Materials (BOM) System
- **Automated Structural Solver (`B` Hotkey / `[📦 BOM]`)**: Aggregates all multiblock and singleblock construction requirements (casings, coils, hatches, controllers) across single pages or entire documents.
- **Hybrid Hatch Override**: Real-time dual lower-tier energy hatch toggling (`⚡ 1x Normal ↔ 2x 1-Tier Lower`) and dynamic casing block deductions.
- **1-Click Recipe Tree Target**: Export shopping lists directly into EMI Recipe Tree or JEI++ calculation goals, or copy formatted text to clipboard.

### 4. Target Batch Production ETA (Estimated Time) System
- **Batch Quantity Goals**: Define target production quantities (e.g. `100x`, `1,000x`, `10 B`) on goal and reroute nodes.
- **Real-Time Inflow ETA**: Calculates estimated duration (`ET: 24m 52s`) based on upstream rates, displaying total batch energy (EU) and raw material requirements.

### 5. Multiplayer Shared Team Workspaces
- **Real-Time Collaboration**: Share and edit factory flowcharts collaboratively with team members on dedicated servers.
- **FTB Teams & Vanilla Scoreboard Integration**: Automatically maps team permissions, roles, and memberships.
- **Granular Per-Page Edit Locks & Presence**: Acquires temporary edit locks during node/canvas interaction to prevent concurrent overwrite collisions, with live teammate presence indicators.
- **Frictionless Auto-Sync**: 3-second inactivity debounced auto-commit and screen exit saving, accompanied by revision history logs and personal forking (`Personal Board`).

### 6. Parametric Recipe Search & Contextual Auto-Wiring
- **Boolean & Parametric Search Engine**: Filter recipes using multi-token AND (`&`/space), OR (`|`), NOT (`!`), `@mod`, `#tags`, `[machine_category]`, and exact `"quotes"`.
- **Drag-to-Search & Auto-Wire**: Drag from any port onto empty canvas to query matching consumers or producers, and spawn the machine pre-wired with optional ratio-matching (`Shift`).

### 7. Multi-Mod Energy & Mechanics Support
- **GregTech CEu Modern**: Full ULV~MAX voltage tiers, Standard/Perfect/Lossless overclocking, subtick CPS batching, and dual energy hatch support.
- **Create & Create: New Age**: Kinetic generator calculations (Water Wheels, Windmills, Steam Engines in SU/RPM), electric motors, generator coils, and magnet rings.
- **Thermal Series**: Dynamo RF/t generation, augment multipliers, and tier upgrade kits.
- **Systeams & Steam Boilers**: Steam boiler mB/s output, steam dynamos, and LP/HP steam machine consumption modeling.

### 8. Accessibility & Canvas Controls
- **5-Level UI Font Scale**: Adjust machine configuration dialog size (`0.75x`, `0.85x`, `1.0x`, `1.15x`, `1.30x`) via `[Aa 1.0x]`, mouse wheel, or `+`/`-` keys.
- **Uniform Global Fluid Units**: Toggle canvas fluid rates between `Auto`, `Always mB`, and `Always B` via the toolbar button or `Shift+T`.
- **Page Tab Overflow Navigation**: Smoothly click `«`, `»` or scroll mouse wheel across multiple board pages without edge clipping.
- **Level of Detail (LOD) Rendering**: Lightweight 2D rendering when zoomed out maintains high performance on large graphs with 1,200+ nodes.
- **Singleplayer Pause Toggle**: Switch between `Pause: ON` and `Pause: OFF` to choose whether the game world freezes while planning lines.

### 9. Multi-Tab Presets & Compound Modules
- **Multi-Tab Pages**: Create, switch, rename, and manage independent calculation flowcharts per production branch.
- **Compound Modules (`Ctrl + G`)**: Collapse sub-graphs into a single module card with automatic internal byproduct encapsulation, proportional scaling, and 1-click restoration (`Expand`).

### 10. Undo, Redo, & Clipboard System
- **History Stack**: Full `Ctrl + Z` (Undo) and `Ctrl + Y` / `Ctrl + Shift + Z` (Redo) support across node, wire, grouping, and property modifications.
- **Clipboard & Box Selection**: Marquee box selection, batch dragging, Cut (`Ctrl + X`), Copy (`Ctrl + C`), Paste (`Ctrl + V`), and Duplicate (`Ctrl + D`).

### 11. Base Anchor & Auto Ratio Sizing
- Designate any bottleneck or target machine as the Base Anchor (`Anchor`).
- **Auto Ratio**: Scales all upstream machines backwards to satisfy the anchor's input requirement using integer ceiling ($1, 2, 3...$), ensuring $\text{Supply} \ge \text{Demand}$.

### 12. Dynamic Addon & Hardware Optimization
- **Dynamic Runtime Indexing**: Automatically discovers coils, rotors, hatches, and machines from any installed GTCEu addon or modpack at runtime without hardcoding.
- **Heating Coils**: Accounts for coil temperatures (Cupronickel 1800K to Trinium 9000K+), duration modifiers, and speed penalties.
- **Parallel Hatches**: Supports standard and constant-power (Absolute) parallel hatches with direct numeric input and mouse wheel scaling.
- **Configurable Maintenance Hatches**: Supports Max Speed (0.9x duration) and Max Eco (1.1x duration) modes.

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
| Toggle Multiblock BOM Dialog | B |
| Connect Wire | Left Click & Drag between ports |
| Drag to Search & Auto-Wire | Drag from port onto empty canvas -> Select recipe |
| Smart Match Connect (Bidirectional) | Shift + Drag between ports (Integer Ceiling matching) |
| Disconnect Wire / Port | Right Click on wire or port socket |
| Switch Recipe (In-Place) | Click [🔄 Switch Recipe] in Machine Config or Node Context Menu |
| Cycle Rate Time Unit | T (/s -> /min -> /h -> /d -> /t) |
| Cycle Global Fluid Unit | Shift + T (Auto -> Always mB -> Always B) |
| Adjust UI Font Scale | Click [Aa 1.0x] (Left/Right), Mouse Wheel, or [+] / [-] |
| Set Master Anchor | Click Anchor icon on recipe card header |
| Edit Machine Count | Click count box -> type number -> Enter / Esc, or use [-] / [+] / [/2] / [x2] |
| Adjust Parallel (Par) | Click [Par] to type number, Mouse Wheel to scale, Right Click for 1/2 |
| Equip Turbine Rotor | Click rotor button -> Select from rotor dialog |
| Change Voltage Tier | Click or Mouse Wheel over Tier button (LV, MV, HV...) |
| Toggle Overclock Mode | Click [STD OC] / [PERF OC] button |
| Blueprint Text Export / Import | Click [Share] / [Import] on top toolbar |
| Recipe / Uses Lookup | Hover over port and press R (Recipes) or U (Uses) |
| Scroll Tabs & Toolbar | Mouse Wheel or click [«] / [»] overflow indicators |

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
  - [EMI](https://curseforge.com/minecraft/mc-mods/emi) or [JEI](https://curseforge.com/minecraft/mc-mods/jei)
  - [FTB Teams](https://curseforge.com/minecraft/mc-mods/ftb-teams-forge) (Optional, for multiplayer team sync)

---

## Building from Source

```bash
git clone https://github.com/Amvermain/GregTechCalculatorBoard.git
cd GregTechCalculatorBoard
./gradlew build
```
The compiled jar will be located in `build/libs/gtcalcboard-1.20.1-2.0.0-alpha.11.jar`.

---

## Developer & Architecture Documentation

- **[Architecture & Developer Guide (English)](docs/ARCHITECTURE.md)**: Internal solver engine, 4-tier architecture, multi-mod physical models, and rendering pipeline.
- **[Architecture & Developer Guide (Korean)](docs/ARCHITECTURE_KR.md)**: 내부 엔진 구조, 4계층 아키텍처, 멀티 모드 물리 모델 및 렌더링 파이프라인.
- **[Detailed Code Specification (English)](docs/en_us/CODE_SPECIFICATION.md)**: Full system architecture, 5 graph algorithms, overclocking formulas, `CategoryCapabilityMatrix`, multiplayer concurrency lock protocol, and SavedData persistence schemas.
- **[Detailed Code Specification (Korean)](docs/ko_kr/CODE_SPECIFICATION.md)**: 전체 시스템 아키텍처, 5대 그래프 알고리즘, 오버클럭 연산 공식, `CategoryCapabilityMatrix`, 멀티플레이 동시성 락 프로토콜 및 영속화 스키마.
- **[v2.0 Development Plan](docs/DEVELOPMENT_PLAN_V2.md)**: Multiplayer and FTB Teams roadmap.

---

## Special Thanks

- **The Reel One** - For providing UX/UI feedback, design suggestions, and community testing.

---

## License

MIT License - see [LICENSE](LICENSE) for details.

