# GregTech Calculator Board (GTCalcBoard)

<p align="center">
  <b>English</b> | <a href="README_KR.md">한국어</a> | <a href="CHANGELOG.md">Changelog</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg" alt="Minecraft 1.20.1">
  <img src="https://img.shields.io/badge/Loader-Forge%2047.2.0+-orange.svg" alt="Forge">
  <img src="https://img.shields.io/badge/GregTech-CEu%20Modern-blue.svg" alt="GTCEu Modern">
  <img src="https://img.shields.io/badge/Recipe%20Viewer-EMI%20%2F%20JEI%20Supported-purple.svg" alt="EMI & JEI">
  <a href="https://discord.gg/NaJWk3UjJN"><img src="https://img.shields.io/badge/Discord-Join%20Community-5865F2?logo=discord&logoColor=white" alt="Discord"></a>
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License">
</p>

An in-game node graph calculator and flowchart editor for GregTech CEu Modern, EMI, JEI, and multi-mod ecosystems. Designed for planning and balancing complex multi-step production lines directly inside Minecraft without relying on external spreadsheets or web calculators.

---

## Features

### 1. Intuitive Node Canvas & Smart Flowcharting
- **Pluggable Recipe Viewer Integration**: Native integration with **EMI**, **JEI**, and **JEI++** via pluggable SPI, plus an offline pure vanilla fallback. Add recipes via the collapsible **Favorites Dock (`[⭐ Favorites (N) ▶]`)** or click EMI's native `[+]` button.
- **Contextual Drag-to-Search & Auto-Wiring**: Drag wires from any port onto empty canvas to instantly search matching producer or consumer recipes and auto-connect with integer ratio matching (`Shift`).
- **In-Place Recipe Switching**: Switch recipes on existing nodes with one click (`[🔄 Switch Recipe]`) without severing compatible wire connections (`IngredientStack.getId()`).
- **Port Multi-Selection & Wire Bundling**: Marquee port selection box, `Ctrl`/`Shift` multi-select, and bundle dragging with real-time multi-bezier curve rendering.
- **Port Hiding & Selective Restore**: Right-click ports to hide unused I/O ports for compact machine cards, with one-click restore via hidden count badge.
- **Canvas Navigation & Subgraphs**: 16px grid snapping (`G`), quick page switcher (`Ctrl + K`), folder tree drawer, compound submodules (`Ctrl + Shift + G`), visual group frames (`Ctrl + G`), and full Undo/Redo (`Ctrl + Z` / `Ctrl + Y`).

### 2. Deep GregTech & Multi-Mod Calculation Engine
- **GregTech CEu Modern**: Full ULV~MAX voltage tiers, Standard/Perfect/Lossless overclocking, subtick CPS batching, and dual energy hatch support.
- **Hardware Addons & Multiblocks**: Deductive calculation for heating coils (temperature & duration bonuses), parallel hatches, configurable maintenance hatches, and turbine rotors.
- **Multi-Mod Energy Systems**: Kinetic calculations for **Create** & **Create: New Age** (SU, RPM, generator coils), RF/t dynamos & tier kits for **Thermal Series**, and steam consumption modeling for **Systeams** / Boilers.
- **Master Anchor & Auto-Ratio Sizing**: Designate bottleneck machines as Base Anchors to automatically scale all upstream production chains backwards using integer ceiling.

### 3. Factory Multiblock BOM & Shared Machine Pools
- **Automated Multiblock Bill of Materials (BOM)**: Aggregates all multiblock and singleblock construction requirements (casings, coils, hatches, controllers) across pages, frames, and compound modules (`B` hotkey). Export shopping lists directly to EMI Recipe Tree, JEI++, or clipboard.
- **Shared Machine Pools (Time-Sharing Frame)**: Group multiple recipes sharing physical machines into a pool frame (`Ctrl + Shift + S`). Automatically calculates cumulative duty cycles (`Total Duty %`), quantized machine counts (`Ceil`), and de-duplicated multiblock BOM.
- **Hardware Config Synchronization**: Synchronize voltage tiers, overclock modes, parallel counts, and addons across all enclosed machines from the frame header.

### 4. AE2 Integration & Target Batch Production ETA
- **AE2 Autocrafting Integration**: Bind board pages directly to AE2 Processing Patterns (`[💠 Bind AE2 Pattern]`). Displays calculated completion times (ETA) and bottleneck overlays directly on AE2's Crafting Confirmation screen (`CraftConfirmScreen`).
- **DAG Pipeline & Critical Path ETA**: Evaluates multi-tier autocrafting jobs over a directed acyclic graph (DAG), accounting for machine parallelism, batch counts, and stage pipeline delays.
- **Target Batch Quantity & Real-Time ETA**: Define batch goals (e.g. `100x`, `1,000x`, `10 B`) on goal and reroute nodes to compute remaining completion time, total energy (EU), and raw material requirements.

### 5. Real-Time Multiplayer Team Workspaces
- **Live Team Collaboration**: Design factory blueprints concurrently with teammates on dedicated servers with sub-second synchronization.
- **Team System Integration**: Native support for **FTB Teams**, **Phoenix Guilds**, and **Vanilla Scoreboard Teams**.
- **Edit Concurrency Control**: Automatic per-page edit locks and live teammate presence indicators prevent concurrent overwrite collisions, backed by automated background saving and revision history forks.

### 6. Advanced Flow Analytics & Responsive UX
- **Flow Saturation Wire Modulation**: Dynamically modulates wire pulse animation speeds, stalls (duty cycle stutter), and RGB colors (Cyan -> Amber -> Crimson) based on real-time supply saturation ratios ($R = \text{Supply} / \text{Demand}$), with pulsing glow outlines indicating starved bottleneck machines.
- **Junction Void Sink & Port-Level Voiding**: Configure Junction nodes into infinite void sinks (`SupplyMode.VOID_SINK`) or `Alt + Right-Click` output ports to absorb surplus byproducts and exclude them from net production summaries without severing topology.
- **Responsive Toolbar & Adaptive UI**: Adaptive toolbar contracts title and collapses overflow buttons on compact screens, high-density addon list view (`[▦ / ☰]`), 5-level UI font scaling (`[Aa 1.0x]`), and Level-of-Detail (LOD) rendering for large graphs (1,200+ nodes).

---

## Controls & Shortcuts

| Action | Shortcut | Description |
| :--- | :--- | :--- |
| **Pan / Zoom Canvas** | Right/Middle Click + Drag / Mouse Wheel | Move around and zoom in/out (zooms to cursor) |
| **Quick Add Recipe** | `Space` or Double-Click on canvas | Open recipe browser / search dialog |
| **Hotkey Guide HUD** | `H` | Toggle in-game overlay showing all context hotkeys |
| **Multiblock BOM Dialog** | `B` | Open factory bill of materials breakdown |
| **Quick Page Switcher** | `Ctrl + K` | Fuzzy search and instantly switch between board pages |
| **Undo / Redo** | `Ctrl + Z` / `Ctrl + Y` (or `Ctrl + Shift + Z`) | Step backward or forward through edit history |

<details>
<summary><b>Full Controls & Shortcuts Cheatsheet (Click to expand)</b></summary>

| Action | Key / Mouse |
| :--- | :--- |
| Open Calculator Board | Configurable in Options -> Controls |
| Pan Canvas | Right Click + Drag or Middle Click + Drag |
| Zoom Canvas | Mouse Wheel (Zooms to cursor) |
| Quick Add Recipe | Space or Double-Click on empty canvas space |
| Marquee Box Selection | Left Click + Drag on empty canvas space (Shift + Click to toggle) |
| Multi-Node Dragging | Drag any selected node to move all selected nodes together |
| Create Frame | Ctrl + G |
| Create Shared Machine Pool | Ctrl + Shift + S |
| Group into Module | Ctrl + Shift + G |
| Quick Page Switcher | Ctrl + K (Fuzzy search and instant jump) |
| Toggle 16px Grid Snap | G or lower-left HUD checkbox |
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
| Toggle Void Marking | Alt + Right Click on output port |
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

</details>

---

## Installation & Server Compatibility

GregTech Calculator Board is completely optional on both client and server sides:

- **Client Only (Vanilla / Non-Modded Server)**: You can install the mod on your client and join any multiplayer server, even if the server does not have the mod installed. Personal calculation boards remain fully functional.
- **Server Only (Vanilla Clients)**: Server owners can install the mod on dedicated servers without requiring connecting clients to have the mod installed.
- **Both Client & Server**: When installed on both client and server, real-time multiplayer team workspace synchronization and live collaboration features are enabled.

---

## Requirements & Mod Compatibility

- **Minecraft**: `1.20.1`
- **Mod Loader**: `Minecraft Forge (47.2.0+)`
- **Required Dependencies**: **None** (Fully standalone, 0 hard dependencies required)
- **Supported Recipe Viewers (At least one required)**:
  - [EMI](https://curseforge.com/minecraft/mc-mods/emi) (1.1.0+ recommended)
  - [JEI (Just Enough Items)](https://curseforge.com/minecraft/mc-mods/jei) (15.2.0+) / [JEI Unofficial](https://curseforge.com/minecraft/mc-mods/jei-unofficial)
  - [JEI++ (Just Enough Calculation)](https://curseforge.com/minecraft/mc-mods/just-enough-calculation) (BOM recipe tree integration)
- **Supported Tech & Factory Mods**:
  - [GregTech CEu Modern](https://curseforge.com/minecraft/mc-mods/gregtech-ceu-modern) (1.7.0+ or Star Technology forks)
  - [Applied Energistics 2 (AE2)](https://curseforge.com/minecraft/mc-mods/applied-energistics-2) (Autocrafting pattern binding & DAG ETA evaluation)
  - [Create](https://curseforge.com/minecraft/mc-mods/create) (0.5.1 / 6.0+)
  - [Create: New Age](https://curseforge.com/minecraft/mc-mods/create-new-age)
  - [Greate](https://curseforge.com/minecraft/mc-mods/greate) (Tiered kinetic machine & shaft integration)
  - [Thermal Series](https://curseforge.com/minecraft/mc-mods/thermal-expansion) (Expansion, Foundation, Cultivation)
  - [Systeams](https://curseforge.com/minecraft/mc-mods/systeams)
- **Optional Team Synchronization**:
  - [FTB Teams](https://curseforge.com/minecraft/mc-mods/ftb-teams-forge) (For dedicated server multiplayer team workspaces)
  - [Phoenix Guilds](https://curseforge.com/minecraft/mc-mods/phoenix-guilds) (Teams & Guilds integration)

---

## Building from Source

```bash
git clone https://github.com/Amvermain/GregTechCalculatorBoard.git
cd GregTechCalculatorBoard
./gradlew build
```
The compiled jar will be located in `build/libs/gtcalcboard-1.20.1-2.2.0-alpha.1.jar`.

---

## Developer & Architecture Documentation

- **[Architecture & Developer Guide (English)](docs/ARCHITECTURE.md)**: Internal solver engine, 4-tier architecture, multi-mod physical models, and rendering pipeline.
- **[Architecture & Developer Guide (Korean)](docs/ARCHITECTURE_KR.md)**: 내부 엔진 구조, 4계층 아키텍처, 멀티 모드 물리 모델 및 렌더링 파이프라인.
- **[Detailed Code Specification (English)](docs/en_us/CODE_SPECIFICATION.md)**: Full system architecture, 5 graph algorithms, overclocking formulas, `CategoryCapabilityMatrix`, multiplayer concurrency lock protocol, and SavedData persistence schemas.
- **[Detailed Code Specification (Korean)](docs/ko_kr/CODE_SPECIFICATION.md)**: 전체 시스템 아키텍처, 5대 그래프 알고리즘, 오버클럭 연산 공식, `CategoryCapabilityMatrix`, 멀티플레이 동시성 락 프로토콜 및 영속화 스키마.
- **[Architecture Decision Records (ADR)](docs/adr/README.md)**: System design decisions and evolution records.

---

## Community & Support

- **Discord**: [Join our Discord Server](https://discord.gg/NaJWk3UjJN) - Discuss factory blueprints, ask questions, and chat with the community.
- **Issue Tracker**: [GitHub Issues](https://github.com/Amvermain/GregTechCalculatorBoard/issues) - Report bugs and submit feature requests.

---

## Special Thanks

- **The Reel One** - For providing UX/UI feedback, design suggestions, and community testing.
- **rafaelpnsm** - For providing UX/UI feedback, design suggestions, and community testing.

---

## Inspirations

- [SatisFlow](https://satisflow.app/) - Interactive node-graph factory calculator and flowchart planner for Satisfactory.
- [Foreman 2](https://github.com/DanielKote/Foreman2) - Node-based visual flowchart production line calculator for Factorio.
- [Helmod](https://mods.factorio.com/mod/helmod) - Feature-rich in-game factory calculation and recipe matrix solver mod for Factorio.

---

## License

MIT License - see [LICENSE](LICENSE) for details.

