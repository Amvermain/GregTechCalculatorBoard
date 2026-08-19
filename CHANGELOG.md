# Changelog

<p align="center">
  <b>English</b> | <a href="CHANGELOG_KR.md">한국어</a>
</p>

All notable changes to **GregTech Calculator Board** will be documented in this file.

---

## [1.0.4-fix2] - 2026-08-20

### Fixed
- **World Load & EMI Registration GTCEu Material Registry Premature Access Crash (`DynamicAddonCrawler`, `CalcBoardEmiPlugin`)**:
  - Fixed an `IllegalStateException` crash caused by asynchronous preloading accessing `GTRegistries.MATERIALS` during the initial game title/EMI entrypoint stage before GTCEu finished world/datapack registration.
  - Added strict isolation guards to ensure GTCEu internal registries are never queried before the player is fully inside an active world.
- **Recipe Search Dialog Empty State / Desync on First Open (`RecipeSearchDialog`)**:
  - Resolved an issue where opening the search dialog immediately after game load displayed `No matching recipes found.` due to desynchronized cache flags while background indexing was in progress.
  - The dialog now reactively detects background indexing completion and automatically populates the recipe list in real time, displaying `Loading recipes...` while indexing.
- **100% Text/Name Parsing Removal & Pure Bytecode/API Spec Extraction (`ParallelHelper`)**:
  - Completely purged all substring, item name, and regex tooltip matching, replacing them with direct `MetaMachine.getCurrentParallel()` method invocation and bytecode inspection of `modifyRecipe` overrides to detect standard vs. constant-power (Absolute) parallel hatches.
- **Hardcoded String Audit & Complete Localization (`MachineConfigDialog`, `ko_kr.json`, `en_us.json`)**:
  - Audited and localized all remaining hardcoded UI and tooltip strings including coil temperature, parallel multipliers, speed, time, energy, and constant power badges into formal translatable keys.

### Changed
- **0ms Instant Addon & Recipe Search Dialog Opening Optimization (`MachineAddonCatalog`, `BoardScreen`, `GregTechCalcBoard`)**:
  - Eliminated synchronous `refresh()` and `preloadFuture.join()` calls on modal opening, switching to a 100% non-blocking asynchronous preloading pipeline for 0ms dialog openings.
- **Dedicated Page Creation for Interactive Tutorial (`TutorialManager`)**:
  - Starting the interactive onboarding tutorial no longer clears existing canvas nodes; instead, it automatically creates a dedicated new page (`🎓 Tutorial`) to protect user work.

---

## [1.0.4-fix] - 2026-08-19

### Fixed
- **Multi-Slot Duplicate Output Rate Overwrite (`RecipeNode`, `FlowGraphSolver`)**:
  - Fixed an issue where recipes with multiple output slots of the same item (such as 100% and 85% chance outputs) would overwrite previous slot values during map aggregation, cutting calculated production rates.
  - Resolves the issue where sustainable production lines incorrectly showed negative raw input deficits in the Process Summary.
- **World Load MaterialRegistryManager Crash (`MachineAddonCatalog`, `CoilHelper`, `GregTechCalcBoard`)**:
  - Fixed an `IllegalStateException` crash caused by the addon crawler executing during early resource reload before GTCEu finished registering materials.
  - Removed blind field reflection and switched the catalog to lazy-load safely on the first time the calculator GUI is opened in-game.
- **Cupronickel Coil 75% Speed Penalty & Level Formula (`CoilHelper`, `MachineConfigDialog`)**:
  - Properly applied the 75% processing speed penalty (1.33x duration) for Cupronickel (1800K) coils when installed on Pyrolyse Ovens and Chemical Reactors.
  - Distinguished card badges by color, rendering speed penalties in red, duration reductions in cyan, and power discounts in gold.
- **Turbine Rotor Instance API Calling & Dynamic Extraction Fix (`DynamicAddonCrawler`, `TurbineRotorHelper`)**:
  - Resolved `NullPointerException` where `TurbineRotorBehaviour` instance methods were called as static, which caused rotors to fallback to 100% or misinterpret Enderium durability as efficiency (9180%).
  - Correctly extracts dynamic material stats (e.g. Scheelite 220%, Enderium 180% efficiency / 300% power, HSS series) directly from active GTCEu item instances.
- **Deductive Handling for Fixed-Chance Byproducts (`EmiRecipeConverter`)**:
  - Removed the hardcoded assumption that all GregTech chanced outputs increase by +5% per tier.
  - Directly inspects `tierChanceBoost` from GTCEu recipe definitions, ensuring recipes with fixed chances (like 85% Certus Quartz crystals) maintain their exact values when overclocked.

### Changed
- **Configurable Maintenance Hatch (CMH) Presets (`DynamicAddonCrawler`)**:
  - Replaced ambiguous intermediate values with exact in-game UI modes:
    - **Max Speed Mode**: **0.9x** Duration (10% faster craft time), **3.0x** maintenance problem occurrence rate (breaks 3x more often, EU power remains 1.0x unchanged).
    - **Max Eco Mode**: **1.1x** Duration (10% slower craft time), **0.2x** maintenance problem occurrence rate (breaks 80% less often, EU power remains 1.0x unchanged).
- **Large Turbine Multiblock Physics & Holder Efficiency Scaling (`RecipeNode`)**:
  - Implemented exact base voltage tiers and production limits for Large Steam Turbine (HV: 1,024 EU/t), Large Gas Turbine (EV: 4,096 EU/t), Large Plasma Turbine (IV: 16,384 EU/t), and Nyinsane Boosted Plasma Turbine (12x Parallel: 196,608 EU/t).
  - Dynamically calculates the +10% efficiency bonus (runtime extension / fuel reduction) and 2x EU/t scaling for each Rotor Holder tier above the turbine's base tier to match in-game physics.
- **Type-Based Dynamic Coil & Parallel Hatch Inspection (`CoilHelper`, `ParallelHelper`, `DynamicAddonCrawler`)**:
  - Replaced keyword-based string heuristics with strict `ICoilType` and `IParallelHatch` interface introspection.
  - Prevents non-coil decorative blocks (like stairs and slabs) from being mistakenly registered into the catalog.

---

## [1.0.4] - 2026-08-19

### Added
- **Interactive Hardware Addon Configuration Modal (`MachineConfigDialog`, `MachineAddon`)**:
  - **Interactive 3D Item Icon Rack**: Replaced the plain list with a 10-slot visual 3D item icon tray displaying equipped heating coils, maintenance hatches, turbine rotors, and traits simultaneously.
  - **Quick Addon Actions**: Click equipped icon slots to immediately unequip, or click `[✕ Clear All]` to reset all hardware addons in one click.
  - **Unified Category Filter & Instant Search**: Filter catalog items across `All`, `♨ Coil`, `⚡ Parallel`, `🔧 Maint`, `⚙ Traits`, and `+ Custom` with real-time text searching.
  - **Visual Catalog Cards**: Displays high-fidelity 3D item samples, exact stats badges, and `✔` installed indicators with dynamic hover highlights.
- **Dynamic Flowchart Addon Integration (`NodeCardRenderer`, `BoardTooltipRenderer`, `NodeWidget`)**:
  - **Mini 3D Addon Tray on Node Cards**: Equipped addons render as compact 14x14 3D item icons directly on the machine count row of recipe cards on the canvas.
  - **Rich Hardware & Addon Diagnostic Tooltips**: Hovering over the addon tray or `[⚙ 1x (+1)]` button renders an extensive tooltip showing multiblock/singleblock mode, effective parallel multipliers, and a formatted bulleted list of all active addon stats.
  - **One-Click Config Dialog Access**: Clicking either the mini addon icons or the parallel button opens the hardware configuration dialog.
- **Cross-Mod Dynamic Addon & Coil Discovery (`DynamicAddonCrawler`)**:
  - **Java Reflection Engine**: Dynamically introspects `Block` and `ICoilType` / `CoilType` objects in memory across all mods (GTCEu, KubeJS, Star Technology, GregTech addons).
  - **Multi-Flag Tooltip Analysis**: Parses both advanced and standard tooltip flags to dynamically extract heat capacity, processing speed, energy consumption, and parallel bonuses.
  - **Full Support for Modpack Custom Coils**: Seamlessly recognizes `kubejs:zalloy_coil_block` (13499K), `kubejs:magmada_alloy_coil_block` (16199K), `draconium`, `awakened`, `infinity`, `hypogen`, `eternity`, etc.
- **`Multiblock Info` Recipe-Based Dynamic Multiblock Detection (`MultiblockDetector`)**:
  - Completely removed string name heuristics and replaced them with direct scanning of EMI's `multiblock_info` structure recipes and GTCEu machine definitions, reliably identifying multiblock variants for all processing machines (Large Mixer, Large Chemical Reactor, etc.).

### Changed
- **Standardized Coil Stat Badges**: All heating coil badges consistently lead with their base heat capacity (`♨ [Temp]K`), clearly differentiating them while appending machine-tailored speed/energy discounts where applicable.
- **Cleaned Up Catalog Card Layout**: Removed redundant bottom install text labels, eliminating text truncation and expanding space for full item names and stat badges.
- **Mouse Wheel Interaction Refinement**: Removed accidental mouse wheel scrolling on the parallel button on node cards, preserving wheel scrolling strictly for Voltage Tier cycling and Alternative Ingredients.

### Fixed
- **Turbine Rotor Specs & Large Turbine Generation Engine Discrepancy (`RecipeNode`, `DynamicAddonCrawler`, `NodeCardRenderer`)**:
  - **Scheelite Turbine Rotor Support**: Added exact 220% Efficiency, 225% Power, and 64,000 EU/t capacity to the dynamic crawler and rotor material database.
  - **IV Large Gas Turbine (LGT) Real Generation Synchronization**: Synchronized IV Rotor Holder (8,192V) with Scheelite Rotor (225%) to match in-game physics precisely at `36,864 EU/t (4.5A IV)`, `1,152 Parallel`, `4.40s (220% Eff)`, and `523.6 mB/s`.
  - **Generator Node Badge Deduplication**: Directly displays `⚙ 220%` on generator cards and eliminates redundant `(+1)` counts for the rotor itself.
- **Key Binds / Controls Menu Shortcut Capture Collision (`GregTechCalcBoard`, `KeyBindings`)**: Fixed an issue where typing or binding keys inside Minecraft's Controls menu (`KeyBindsScreen`), options, chat, or pause screens would inadvertently open the calculator board.
- **Text Input Focus Guard**: Prevented global hotkey triggers while any `EditBox` or search text field is actively focused.
- **Shift Requirement & Line Wrapping in Addon Tooltips**: Removed the requirement to hold Shift to view coil stats in calculation dialogs, and replaced long single-line strings with clean multi-line bullet points.
- **Duplicate Remove Icon**: Fixed a double `✕` bug (`✕ ✕ Remove`) in addon tooltips.
- **Crawler False Positives**: Filtered out fusion coils, wire coil crafting items (`lv_coil`..`uhv_coil`), non-block parts, and cover items from heating coil and maintenance tabs.

---

## [1.0.3] - 2026-08-18

### Added
- **Interactive Onboarding Tutorial & Welcome Modal (`TutorialManager`, `TutorialOverlay`)**:
  - **Welcome Dialog (`WelcomeTutorialDialog`)**: Welcomes first-time players with an option to start a step-by-step interactive flowsheet tutorial.
  - **5-Step Core Tutorial Guide**:
    1. **Step 1. Add Recipe Nodes**: Use the canvas quick-add marker `[+]` or the top toolbar `[➕ Add]` button.
    2. **Step 2. Connect Basic Wires**: Drag from an output port (green) to an input port (blue) to establish resource flow.
    3. **Step 3. Shift + Drag 1:1 Auto-Ratio**: Hold `Shift` while wiring to automatically calculate and balance machine counts based on production/consumption rates.
    4. **Step 4. Base Master Anchor & Auto Ratio**: Toggle `[🎯]` on the final product node and click `[⚖ Ratio]` to synchronize the entire upstream production line.
    5. **Step 5. Disconnecting Wires & Canvas Clear**: Right-click any wire curve or port to disconnect, and use `[🗑 Clear]` to reset.
  - Features translucent tutorial cards, animated pulsating target highlights, and skip/restart options.
- **Contextual Quick-Add Canvas Marker (`[+]`)**:
  - Left-clicking any empty canvas area spawns a subtle, translucent **`[+]` quick-add button** at the clicked coordinates.
  - Clicking `[+]` opens the recipe search dialog and immediately spawns the selected machine node at the exact clicked canvas location.
- **Vector / Delta-Based Undo & Redo System (`HistoryManager`, `BoardCommand`)**:
  - **Ultra-Low Memory Command/Delta Architecture**: Records only relative positional deltas (`dx, dy`), node/wire references, and property old/new values instead of heavy full-graph snapshots.
  - **Hotkeys**: `Ctrl + Z` (Undo), `Ctrl + Y` or `Ctrl + Shift + Z` (Redo).
  - **Toolbar Controls**: Added `[↶ Undo]` and `[↷ Redo]` buttons with dynamic enabled/disabled styling.
  - **Comprehensive Action Coverage**: Supports undoing/redoing node movements (single & multi-drag), adding/deleting nodes, connecting/disconnecting wires (including Shift-scaling counts), inline/button property edits (machine counts, voltage tiers, OC modes, parallels, custom names, master anchors, turbine rotors), module grouping/expanding, auto-connect, auto-ratio, and max-flow optimizations.
  - **Independent Per-Tab History**: Each page tab maintains its own isolated undo/redo history stacks.
- **Collapsible Hotkey HUD (`HotkeyHudWidget`)**:
  - Added a collapsible hotkey guide overlay in the bottom-right corner of the canvas.
  - Press **`H`** to toggle the HUD open/closed at any time to check keybindings (`Ctrl+Z`, `Ctrl+Y`, `Ctrl+C/V/X/D`, `Shift+Wire`, `H`, `Delete`, `R/U`).
- **Dynamic Port & Wire Dragging Guidance Tooltips**:
  - Hovering over ingredient ports displays localized port descriptions and connection tips.
  - Live wire dragging renders contextual guidance tooltips:
    - Normal Drag: *"Click target port to connect wire."*
    - **`Shift + Drag`**: *"✨ Shift Connect: Automatically balances target machine count to 1:1 supply-demand ratio."*
- **Page Tab Management & Confirmation Dialogs (`PageTabBarWidget`, `DeletePageConfirmDialog`)**:
  - **Double-Click / Right-Click Tab Renaming**: Double-click or right-click any page tab header to edit its name inline with keyboard typing.
  - **Horizontal Drag & Wheel Scrolling**: Drag across the tab bar or use the mouse wheel to scroll horizontally when managing many preset tabs.
  - **Page Deletion Confirmation**: Added `DeletePageConfirmDialog` to prevent accidental deletion of important build flowcharts.
- **Switchable Power Display Modes (`PowerDisplayMode`)**:
  - Click the power summary row in the Process Summary panel to cycle through display units:
    - **`EU/t`** (Default GT tick rate)
    - **`EU/s`** (Per-second EU)
    - **`RF/t` / `FE/t`** (FE/RF equivalent for Thermal and cross-mod builds)
- **Standalone In-Game Manual & Guidebook Dialog (`GuideDialog`)**:
  - Added `[📖 Guide]` button on the top toolbar to access a built-in, 6-chapter manual modal directly over the canvas with zero external mod dependencies.
- **Multi-Selection & Batch Operations (Marquee Box, Multi-Drag, Clipboard)**:
  - **Marquee Box Selection**: Left-click and drag on empty canvas space to draw a cyan selection box and select multiple nodes at once.
  - **Shift + Click Multi-Select**: Toggle individual nodes into or out of selection.
  - **Batch Dragging**: Dragging any selected node moves all selected machines together while preserving relative positions and internal wires.
  - **Clipboard Shortcuts**:
    - `Ctrl + X`: Cut selected nodes and internal wires.
    - `Ctrl + C`: Copy selected nodes and internal wires to clipboard.
    - `Ctrl + V`: Paste copied nodes at cursor position with fresh unique IDs (UUIDs).
    - `Ctrl + D`: Duplicate selected nodes immediately.
    - `Delete` / `Backspace`: Batch delete selected nodes and connections.
    - `Ctrl + A`: Select all nodes on canvas.
- **Inline Custom Title Renaming (`NodeNameEditor`)**:
  - Double-click any node header title to open the inline text editor and rename machines (e.g. `Sulfuric Acid Line 1`, `Turbine Power Plant`) persistently.
- **Compound Module Subgraph Abstraction & Expansion**:
  - **`[📦 Group]`**: Condense complex interconnected production facilities into a single **Compound Module Card**.
  - **Automatic Byproduct Encapsulation**: Hides 100% self-recycled and internally consumed intermediate fluids/items, exposing only raw external inputs, net final outputs, combined EU/t, and total contained machines.
  - **`[⤢ Expand]` Restoration**: Click the expand button on a module card at any time to restore all individual machines and internal wires onto the canvas.
  - **Selective Module Grouping**: Group only a selected subset of nodes with automatic external wire rewiring.
- **Soft Dependency Mod Compatibility Guard (`ModCompatHelper`)**:
  - Isolates mod-specific bytecode calls for GregTech (`gtceu`), Thermal (`thermal`), and EnderIO (`enderio`).

### Fixes & Improvements
- **Z-Index & Depth Buffer Bleed-Through Fixes**:
  - Completely eliminated 3D item models and fluid sprites bleeding through overlapping node cards via explicit depth test disabling and per-node Z-offset layers.
  - SummaryOverlay, ToolbarWidget, PageTabBarWidget, and Dialogs now render reliably on top of all canvas elements.
- **Fixed Module Machine Count in Process Summary**:
  - Summary Overlay now accurately aggregates the total count of physical machines contained within compound modules (e.g. 58 machines) and lists detailed breakdown in tooltips.
- **Fixed Module Card UI Overlap & Clean Minimal Design**:
  - Compacted module badges (`📦 58 machines`) and tags with safety margins to prevent text clipping.
  - Completely hides irrelevant tier, generator, OC, rotor, and parallel buttons on compound module cards.
- **Comprehensive I18n Audit & Localization**:
  - Audited all UI components, dialogs, tutorial hints, and error messages to ensure 100% complete 1:1 translation coverage in English (`en_us.json`) and Korean (`ko_kr.json`).
- **Streamlined Top Toolbar Layout**:
  - Reorganized toolbar buttons into a clean, intuitive layout with responsive scrolling and rich tooltips.

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
