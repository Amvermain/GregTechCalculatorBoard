# Changelog

<p align="center">
  <b>English</b> | <a href="CHANGELOG_KR.md">한국어</a>
</p>

All notable changes to **GregTech Calculator Board** will be documented in this file.

---

## [1.0.3] - 2026-08-18

### Added
- **Interactive In-Game Manual & Guidebook Dialog**:
  - Added a dedicated **`[📖 Guide]`** button on the top toolbar to open a comprehensive, standalone manual dialog directly over the canvas with zero external mod dependencies.
  - **6 Core Chapters Included**:
    1. **Chapter 1. Getting Started & Canvas Navigation**: Panning, zooming, centering, adding recipes via EMI, and resizing cards.
    2. **Chapter 2. Wiring & Shift-Drag Auto Ratio**: Basic wires, **`Shift + Drag` auto ratio matching between supplier and consumer**, and wire disconnection.
    3. **Chapter 3. Base Master Anchor & Auto Ratio**: `[🎯]` Master anchor toggle, `[⚖ Auto Ratio]` reverse line synchronization, `[⚡ Auto Connect]`, `[🚀 Max Flow]`.
    4. **Chapter 4. Compound Modules**: Compressing large lines with `[📦 Group]`, auto byproduct encapsulation, and `[⤢ Expand]` restoration.
    5. **Chapter 5. Multi-Select & Shortcuts**: Marquee box selection, `Ctrl+C/V/D/A`, `Delete`, and double-click inline title renaming.
    6. **Chapter 6. Multi-Page Preset Tabs & Blueprints**: Top tab bar, blueprint string export/import, persistent saving.
  - Features dark slate styling, mouse wheel viewport scrolling, keyboard arrow navigation, and full multilingual localization.
- **Multi-Selection & Batch Operations (Marquee Box, Multi-Drag, Hotkeys)**:
  - **Marquee Box Selection**: Click and drag on empty canvas space to create a translucent bounding box and select multiple nodes simultaneously.
  - **Shift + Click Multi-Select**: Add or toggle individual nodes into selection.
  - **Batch Dragging**: Dragging any selected node moves all selected nodes together while preserving relative positions and internal wire connections.
  - **Clipboard Cut / Copy / Paste / Duplicate / Delete**:
    - `Ctrl + X`: **Cut selected nodes and internal wires (copy to clipboard and remove from canvas)**.
    - `Ctrl + C`: Copy selected nodes and internal connection wires to clipboard.
    - `Ctrl + V`: Paste copied nodes at cursor position with fresh unique IDs (UUIDs).
    - `Ctrl + D`: Duplicate selected nodes immediately.
    - `Delete` / `Backspace`: Batch delete selected nodes and associated wires.
    - `Ctrl + A`: Select all nodes.
  - **Bring-to-Front on Node Click**: Clicking or dragging any node dynamically promotes it to the top rendering layer above overlapping cards.
  - **Z-Index & Depth Buffer Bleed Fixes**:
    - Prevented 3D item models and fluid sprites from bleeding through overlapping node cards via explicit depth test disabling and per-node Z-offset tiers.
    - SummaryOverlay, ToolbarWidget, PageTabBarWidget, and GuideDialog now render on top of all canvas elements with dedicated Z-planes.
  - **Selective Module Grouping**: Group only the selected subset of nodes into a compound module, automatically rewiring external connections to new module ports.
- **Inline Custom Title Renaming**:
  - Double-click any node header title (`Chemical Reactor...`, `Compound Process...`) to enter inline editing mode and rename it with your keyboard.
- **Module Subgraph Abstraction & Expansion**:
  - **`[📦 Group]`**: Group multiple interconnected processing facilities on the canvas into a single **Compound Module Card** with a single click.
  - **Automatic Intermediary Encapsulation**: Internally recycled and consumed intermediate items/fluids are automatically hidden, exposing only **Raw External Inputs**, **Net Final Outputs**, **Total Combined EU/t**, and **Total Contained Machine Count**.
  - **`[⤢ Expand]` Restoration**: Clicking the expand button on the top-right of a module card instantly restores all original machine nodes and wire connections onto the canvas.
- **Multi-Page Preset Tabs (Pagination)**:
  - Added a top **Page Tab Bar (`[1. Acid Line] [2. Petrochem] [3. Power Plant] [+]`)** allowing players to seamlessly switch between independent flowcharts without opening file load dialogs.
- **Soft Dependency Mod Compatibility Guard**:
  - Introduced `ModCompatHelper` to ensure GregTech (`gtceu`), Thermal (`thermal`), and EnderIO (`enderio`) specific bytecode and reflection logic only execute when the corresponding mods are loaded.

### Fixes & Improvements
- **Fixed Module Machine Count in Balance Summary**:
  - Compound modules in the Summary Overlay now properly sum up their internal machines (e.g. 58 machines) and display the detailed breakdown in the tooltip.
- **Fixed Module Card UI Overlap**:
  - Compacted row 1 machine badge (`📦 58대`) and row 2 module tag (`[📦 Module]`) with increased safety margins to prevent any text collisions.
- **Minimal Clean UI for Compound Modules**:
  - Completely hides irrelevant tier, generator, OC, rotor, and parallel buttons on compound module cards.
- **Removed Deprecated Tier Cap Option**:
  - Cleaned up the confusing global tier cap button from the toolbar and code.

---

## [1.0.2] - 2026-08-18

### Added
- **Native GTCEu Live Turbine Rotor Scanner & 3D Item Grid Dialog**:
  - **48+ Real-time Rotor Scan**: Dynamically discovers all native GregTech rotors and custom modpack materials (KubeJS, Thermal, EnderIO, etc.) at runtime with zero hardcoding.
  - **3D Visual Item Grid Modal (`[⚙ 220%]` click)**: Displays authentic 3D item icons with genuine material tint colors (cyan Enderium, bronze Dragonsteel, golden Scheelite, etc.), efficiency badges, and live search.
  - **Full Multilingual Localization**: Automatically formats item names and tooltips (e.g. `Tritanium Turbine Rotor`, `Turbine Efficiency: 220%`, `Turbine Power: 220%`, `Click to equip to turbine`).
- **Rotor Power & Voltage Tier Auto-Parallel Derivation**:
  - Fully integrated with GTCEu's bytecode formula ($V_{\text{max}} = V_{\text{holder}} \times \frac{P_{\text{rotor}}}{100.0}$).
  - Selecting a rotor or changing voltage tier (`EV`, `IV`, `LuV`, `ZPM`, etc.) instantly derives and applies the optimal rated parallel count (Par) and power generation (+EU/t).
  - *Example: Nitrobenzene 32 EU/t + Scheelite Rotor (450% Power) + IV Rotor Holder $\rightarrow$ **1,152x Par, +36,864.0 EU/t, 4.00s cycle, 288.0 mB/s consumption**.*
- **Thermal Dynamo & Non-GT Generator Support**:
  - Automatically recognizes Thermal Expansion Dynamos (Lapidary, Stirling, Magmatic, Compression, etc.) as power generators, converting RF/FE energy (`300,000 RF`) into GT equivalent EU and calculating duration/consumption rates accurately.
  - Replaces raw hash recipe IDs with clean, smart localized titles (e.g. `Lapidary Dynamo (Diamond)`).
- **Generator Dedicated Emerald Panel Theme**:
  - Generator/Turbine nodes are rendered with a distinct deep emerald dark background (`#122218`), energy green borders (`#33AA66`), `[GEN]` badge, and `§a⚡` icon for instant visual recognition.
- **Machine Item Icon on Header**:
  - Displays the 3D block/item icon of the machine executing each recipe (Chemical Reactor, Distillation Tower, Large Turbine, etc.) on the top-left of every card header for instant identification.
- **Total Machines Needed Statistics & Breakdown Tooltip**:
  - Live summary panel aggregates the total number of physical machines required across the entire flowsheet under current settings.
  - Hovering over the total machine count displays a detailed tooltip breakdown of required machines by category (e.g. 3x Chemical Reactor, 2x Distillation Tower, 1x Large Gas Turbine...).
- **Interactive Panel Width Resizing**:
  - Drag the bottom-right corner resize handle (`⤡`) on any recipe card to freely adjust its panel width (160 to 400px), ensuring long item names and complex multi-input/output lists remain fully readable.
- **Interactive Numeric Parallel Input & Wheel Control**:
  - **Direct Number Input**: Click `Par` button to type any parallel number directly (e.g. `1152`).
  - **Mouse Wheel Control**: Scroll over `Par` button to scale by 2x (or `Shift + Wheel` for +/- 1 fine stepping).
  - **Right Click**: Instantly halves parallel count.
- **Integer Machine Count & Bottleneck-Free Ceiling**:
  - Auto Ratio and Shift+Drag calculations now round all machine counts up to integers ($1, 2, 3...$).
  - Guarantees $\text{Supply} \ge \text{Demand}$ across the entire chain to prevent upstream fuel/raw material starvation.
- **Bidirectional Shift + Drag Smart Rate Matching & Deficit Supplementing**:
  - **Forward (Output -> Input)**: Shift+Drag from output port to input port scales consumer machine count to match available supply safely without over-consumption.
  - **Reverse (Input -> Output)**: Shift+Drag from input (demand) port to output port scales producer machine count to match consumer demand with ceiling.
  - **Incremental Deficit Matching (Recycle Loop Support)**: When connecting an additional producer to an input port that already has incoming recycled flow (e.g. recovering dilute sulfuric acid into a nitration mixture), it **automatically deducts the existing supply and calculates producer machine count only for the remaining net deficit**!

### Performance & Polish
- **Optimized Recipe Search with In-Memory Pre-Indexing**:
  - Pre-indexes recipe names, inputs, outputs, and IDs into an in-memory search cache (`SearchableRecipe`) upon opening the dialog, eliminating typing lag and GC pressure completely even with tens of thousands of recipes.
- **Z-Index & Modal Occlusion Fix**:
  - Elevated modal dialogs with `translate(0, 0, 600)` and solid backgrounds to completely eliminate Z-fighting and canvas bleed-through.

### Fixed
- **Generator Power Output & Fixed Duration Overclocking**:
  - Generator recipes now correctly contribute to total power (+EU/t) instead of being treated as power consumption.
  - Tier adjustments now expand parallels rather than reducing cycle duration, matching native GTCEu large turbine mechanics.

---

## [1.0.1] - 2026-08-17

### Fixed
- **Overhauled Auto-Ratio Algorithm**:
  - Fixed runaway machine count inflation on complex multi-output branching trees (Distillation Towers, Steam Crackers) using the new Anchor-Outward Wavefront Balancer algorithm.
- **Fixed Machine Count Text Input**:
  - Fixed `Backspace` and `Delete` keys not working in machine count edit boxes.

### Added
- **Process Summary Mouse Wheel Scrolling**:
  - Added vertical scrollbar and scissor clipping to summary panels exceeding screen height.
- **Full UI Localization & Dynamic Button Widths**:
  - Applied complete I18n translation and dynamic button widths across all cards and toolbars.

---

## [1.0.0] - 2026-08-17

### Initial Release
- Full EMI and GregTech CEu Modern recipe integration (`A` or `[⚡]` in EMI to add to board).
- Infinite canvas with smooth zoom and pan.
- Full voltage tier support (ULV to MAX), standard/perfect overclocking, and parallel calculations.
- Master anchor (`🎯`) and chain-wide Auto-Ratio balancing.
- Max Tier Cap (`Cap: LV~MAX`) and Max Flow throughput optimization.
- NBT GZIP Base64 blueprint sharing (`GTBOARD:...`) with `Ctrl+C` and `Ctrl+V`.
- Real-time process summary for total power, raw inputs, and net outputs.
