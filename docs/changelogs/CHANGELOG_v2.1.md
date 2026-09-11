# Changelog (v2.1.x)

<p align="center">
  <b>English</b> | <a href="CHANGELOG_v2.1_KR.md">한국어</a>
</p>

> **Version Navigation**:
> - [Latest Changelog (v2.2.x)](../../CHANGELOG.md)
> - **v2.1.x Changelog (Current)**
> - [v2.0.x Changelog](CHANGELOG_v2.0.md)
> - [v1.0.x Changelog](CHANGELOG_v1.0.md)

## [2.1.0] - 2026-09-04

### Added
- **EMI Hover Slot Synchronous Highlighting (`EmiPreviewWidgetHolder`)**:
  - Implemented real-time synchronous hovering between EMI recipe viewer widgets and canvas node ports. Hovering any input/output slot in the EMI dialog instantly highlights corresponding item/fluid ports and connecting wires across the board canvas with luminous cyan outlines for instant visual flow tracing.
- **Interactive Junction Node Estimated Completion Time (ET / DT) & 14-Step Tutorial Progression**:
  - Completely overhauled the onboarding tutorial into a structured 14-step progressive curriculum (`TutorialStep`, Steps 1–14) with seamless mode separation:
    - **Basic Tutorial (Steps 1–9)**: Added Step 5 (`STEP_5_JUNCTION_ETA`) directly into the beginner curriculum. Immediately following boiler-to-turbine wiring, users practice clicking the junction bottom badge, typing a target batch size (e.g., 1,000 mB), and observing the real-time Estimated Completion Time (`ET: 2.0s`) recalculation. Pressing Enter automatically commits and transitions to the Machine Selector step.
    - **Advanced Tutorial (Steps 10–13)**: Enhanced Step 12 (`STEP_12_JUNCTION_SUPPLY`) for advanced junction mechanics, including right-click external supply configuration (Infinite Supply ∞ and Fixed Flow Limits to isolate upstream deficits) and live feedstock Depletion Time (`DT: xx.xs`).
  - Added in-depth Junction Node ET/DT documentation to the in-game Guide Dialog (Chapter 3 Wiring and Chapter 8 Shortcuts), detailing left-click batch entry, right-click external supply config, and Shift+right-click reset operations.

### Changed & Improved
- **Virtual Viewport GUI Scale Decoupling & Coordinate Consistency**:
  - Refined mouse hitbox translation, dialog dragging offsets, toolbar scissor clipping, and sticky note text rendering under custom board GUI scale settings (`boardGuiScale`), ensuring pixel-perfect alignment and zero coordinate drift regardless of custom UI scales.
- **Four-Language Localization Parity (i18n)**:
  - Synchronized all 974 localization keys with 100% parity across English (`en_us`), Korean (`ko_kr`), Simplified Chinese (`zh_cn`), and Russian (`ru_ru`), passing `tools/check_i18n.py` with zero missing keys or format token mismatches.

### Fixed
- **Shared Machine Multiblock BOM Duplication & Fractional Duty Counting**:
  - Fixed a critical calculation defect in `MultiblockBOMCalculator` where multiblock machine structures within Shared Machine Pools (`CanvasGroupFrame`) were duplicated across multiple fractional-duty recipes. Instead of multiplying structure blocks by individual recipe lines, the system now computes the sum of duty cycles across shared recipes and quantizes them to physical machine counts using ceiling rounding. This prevents duplicate counting of machine casings, heating coils, buses, hatches, and controllers in the Bill of Materials.
  - Introduced `MultiblockBOMSummary` and `MultiblockStructurePart` models to cleanly separate structure part requirements from raw recipe bills, properly formatting localized structure component names and required quantities.
- **EMI Item Tooltip Depth Buffer (Z-Index) Clipping & Z-Fighting**:
  - Resolved an issue in `BoardTooltipRenderer` where depth buffer values written during EMI 3D item model rendering caused subsequent tooltip backgrounds and text to be clipped or punched through by background item models. Added explicit draw call flushing (`graphics.flush()`), depth buffer clearing (`GL11.GL_DEPTH_BUFFER_BIT`), disabled depth testing, and applied Z-axis translation (`+1000.0f`) to guarantee pristine top-level tooltip rendering.
- **Tutorial Step Machine Config Button Glowing & State Transition Stability**:
  - Improved machine configuration button glowing indicators and step completion detection when entering the machine configuration stage in `TutorialManager`.
- **Junction Node Estimated Completion Time (ET) & Batch Energy Distortion Under Supply Starvation (`ProductionETACalculator`)**:
  - Fixed an issue where `calculateNetInflowRate`, `calculateNetOutflowRate`, and `calculateTotalEnergyForBatch` assumed theoretical 100% machine output regardless of upstream raw material deficits, calculating unrealistically optimistic completion times (ET) even when machines operated at reduced efficiency.
  - Integrated real-time machine operating efficiency (`getEfficiency()`) and operational status (`isOperational()`) directly into the flow traversal solver. Under feedstock bottlenecks or partial starvation, the calculated inflow rate now accurately reflects derated throughput, ensuring real-time ET and total batch energy dynamically stretch in proportion to real factory constraints.
- **Junction External Supply Config Dialog EditBox Hitbox & Label Overlap (`JunctionSupplyDialog`)**:
  - Fixed an issue where the radio option row click-bounds intercepted clicks intended for the numeric `Fixed External Supply Rate` text box, preventing the input field from receiving focus or accepting keyboard input.
  - Expanded dialog width from 260px to 300px to prevent the English/Russian supply rate label from overlapping the rate edit box (`...Supply Rat[ 100.00 ]`), and added auto-focusing on the text field when selecting the Fixed Rate radio option.
- **Junction External Supply Misclassification as Depletion Buffer & Inflow Calculation Defect (`ProductionETACalculator`, `NodeCardRenderer`)**:
  - Fixed an issue where a junction node with an external supply (fixed rate or infinite supply ∞) but without incoming upstream wires was erroneously classified as an unconnected feedstock buffer (`isInputSourceJunction`, Unconnected Raw Input), incorrectly computing and displaying Depletion Time (`DT`) instead of Estimated Completion Time (`ET`).
  - Corrected `calculateNetInflowRate` to properly recognize a junction node's external supply rate, resolving an issue where external supply yielded 0.0 inflow, fixing both junction ETA calculations and flow propagation into downstream consumer machines.
- **Junction Hover Tooltip Drain Rate and Depletion Time Numerical Discrepancy (`BoardTooltipRenderer`)**:
  - Resolved a numerical contradiction in junction hover tooltips where the displayed `Drain Rate` showed theoretical unconstrained demand (e.g., -62 mB/s), while `Depletion Time` was divided by the derated machine throughput (30.86 mB/s) resulting in an inconsistent duration (32.4s instead of 1,000 / 62 = 16.1s). The tooltip now displays the actual operational drain rate (`calculateNetOutflowRate`) consistent with the depletion timer.
- **Junction External Supply Badge Fluid Rate Formatting (`NodeCardRenderer`)**:
  - Fixed an issue where external supply badges rendered on junction node cards omitted fluid type detection (`isFluid()`), incorrectly formatting fluid rates as item units (`+31 /s` instead of `+31 mB/s`).
- **Discrete Machine Cycle Floating-Point Precision & Doubled Duration Defect (`ProductionETACalculator`)**:
  - Fixed a critical numerical defect where target batch quantities matching an exact single machine cycle (e.g., 1,000 mB for a 16.20s LCR run) were computed as two cycles (16.2s * 2 = 32.4s, producing 62 mB/s * 32.4s = 2008 mB). Double-precision floating point rounding ((1000.0 / 16.2) * 16.2 = 999.9999999999999 mB) caused 1000.0 / 999.9999999999999 = 1.0000000000000002, which `Math.ceil` rounded up to 2 discrete cycles.
  - Implemented an epsilon tolerance guard (1e-7) in `calculateETA` and `calculateDepletionTime` to eliminate false cycle increments, correctly computing 16.2s duration and consistent batch energy (311k EU).

## [2.1.0-beta.2] - 2026-09-04

### Added
- **Flow Saturation Wire Flow Modulation & Real-Time Bottleneck Visualization**:
  - Modulated wire flow animations across the canvas based on real-time supply saturation ratios (Saturation = Supply / Demand). Wires travel smoothly in Cyan Blue when fully supplied, slow down and intermittently stall (duty cycle stutter) in Amber Orange during partial starvation, and severely lag in Crimson Red during severe feedstock shortages.
  - Starved machine cards with reduced efficiency display an amber pulsing glow outline (Amber Pulse), enabling instantaneous identification of production line bottlenecks across dense flowcharts.
  - Added a 3-state wire animation toggle (`Rate Modulated`, `Uniform Pulse`, `Disabled`) in Board Settings (`[⚙ Settings]`).
- **Byproduct Void Management & Sink System**:
  - **Void Sink Junction Node (`SupplyMode.VOID_SINK`)**: Added a Void Sink mode to Junction reroute nodes to infinitely absorb and delete surplus byproducts without upstream demand backpropagation. In 1:N branching topologies, downstream normal consumer demand is strictly prioritized first, absorbing only true surplus and preventing downstream starvation.
  - **Port-Level Direct Void Marking**: Added `Alt + Right-Click` on machine output ports to exclude specific byproduct flows from the net product summary without severing wire connections.
  - **One-Click Summary Overlay Voiding & Collapsible Restore**: Added a `[🗑]` void button to net product rows in `SummaryOverlay` to move surplus byproducts to voiding with one click, along with a collapsible `🗑 Voided Byproducts` section and `[↩]` one-click restore button.

### Changed & Improved
- **Enhanced Canvas Visual Feedback for Voids and Bottlenecks**:
  - Rendered void sink junction nodes with purple borders and a `VOID` badge, and highlighted void-marked output ports with purple socket outlines and purple text labels.
  - Enhanced port hover tooltips with live feedstock saturation percentages (%), voided indicators, and an `[Alt+Right-Click]: Toggle Void Marking` shortcut prompt.
- **Systeams Steam Unit Formatting & Display Name Readability**:
  - Applied compact mB/s and B/s flow rate units to steam produced by multi-tier Systeams boilers, and resolved localized fluid display names (Warm Steam, Hot Steam, Superhot Steam, Plasma) accurately.

### Fixed
- **GTCEu Singleblock Sub-Tick Overclock Truncation & Power Distortion**:
  - Fixed an issue where high-voltage overclocks on singleblock machines pushed recipe durations below 1 tick (0.05s), causing astronomical EU/t power spikes due to singleblocks lacking subtick parallel capabilities. Singleblock overclocks now terminate early at 1 tick with integer tick truncation, while multiblock machines continue to support subtick parallel processing as intended.
- **Systeams Boiler Fluid Registry IDs & Recipe Manager Reflection Compatibility**:
  - Fixed boiler alternative fluid definitions to match Systeams registered fluid IDs (`systeams:steamier`, `systeams:steamiest`, `systeams:steamiester`, `systeams:steamiestest`), and added flexible runtime reflection resolution for boiler manager methods (`boil` vs `getBoiledFluid`, `fluidOut` vs `fluid`), resolving failures during boiler recipe deduction and steam conversion.

## [2.1.0-beta.1] - 2026-09-03

### Added
- **Dedicated Board GUI Scale Decoupling**:
  - Added an independent virtual viewport GUI scale option (`[⚙ Settings]` -> GUI Scale: Auto, 1x, 2x, 3x, 4x, 5x, 6x) allowing players to scale the calculator board interface independently of Minecraft's global GUI scale.
- **Adaptive Responsive Toolbar & Display Modes (`ToolbarWidget`)**:
  - Added 3 toolbar display modes (`AUTO`, `COMPACT` 22px icon mode, `FULL` text label mode) with dynamic 3-stage title contracting (`GT Calculator Board`, `GT Board`, `📟`) and a `[...]` overflow dropdown menu for overflowing buttons on compact window widths.
- **Dynamic Addon Catalog & High-Density List View (`AddonCatalogView`)**:
  - Dynamically calculates visible catalog rows (2~5 rows) based on dialog height and adds a 1-click `[▦ / ☰]` toggle to switch into a 20px high-density list mode, displaying 10+ addons simultaneously without scrolling. Automatically persists view preferences per-board.
- **High-Voltage Turbine Tier Modeling (UHV ~ MAX)**:
  - Extended GTCEu and Star Technology turbine physics simulations to support ultra-high voltage rotor holders and dynamo hatches (UHV, UEV, UIV, UXV, OpV, MAX), calculating exact flow rates, optimal efficiency points, and base power production.
- **Russian Language Localization (`ru_ru`)**:
  - Added complete Russian localization (`ru_ru.json`) covering all UI elements, tooltips, dialogs, hotkey guides, and in-game messages.
- **Multi-Tier Systeams Boiler Fluid Progression**:
  - Added support for multi-stage Systeams boiling transitions (Water -> Steam -> Warm Steam -> Hot Steam -> Superhot Steam) with dynamic fluid alternative cycling.
- **External Inventory Mod UI Collision Isolation**:
  - Integrated compatibility guards for Inventory Profiles Next (IPN) and similar inventory sorting mods, preventing foreign buttons and widgets from injecting into and cluttering the calculator board canvas.

### Changed & Improved
- **World Login Background Recipe Indexing Optimization**:
  - Optimized workstation sorting in category capability matrices with O(1) set-based lookups, eliminating client freezes and frame drops during Phase 3 background indexing upon joining worlds or servers.
- **Two-Pass Z-Order Rendering & Item Depth Isolation**:
  - Enforced discrete depth buffer isolation and two-pass layering on the canvas, eliminating Z-clipping artifacts and rendering glitches between 3D item models and 2D text elements.
- **High-Performance Node Card Text Caching (`NodeCardTextCache`)**:
  - Optimized title truncation, port throughput labels, and power strings with dirty-flag-driven text caching to eliminate per-frame formatting overhead across dense flowcharts.
- **Hierarchical Compound Module Flow Balance Precision**:
  - Refined flow graph solving across nested compound modules to accurately balance proportional scaling and isolate internal intermediate flows from net external inputs and outputs.
- **Minimap Overlay Protection Padding**:
  - Added an automatic 16px right margin to the bottom toolbar to prevent visual collisions with in-game minimap overlays such as JourneyMap.

### Fixed
- **Toolbar Button Click Bounds on Window Resizing**:
  - Fixed an issue where resizing the game window caused toolbar button hitboxes to misalign with their visual positions or cut off trailing buttons.
- **Machine Config Dialog Addon Catalog Layout Truncation**:
  - Fixed an issue where the bottom rows of the addon catalog were clipped when viewing the machine config dialog on specific display resolutions.
- **Modal Dialog Virtual Viewport Scissor Bounds & Hitbox Alignment**:
  - Fixed an issue where modal dialogs (BOM, Settings, Templates, Quick Switcher, Export/Import) had misaligned mouse click bounds and clipping boxes when using non-standard virtual GUI scale settings.
- **Systeams Dynamo Boiler Conversion State Synchronization**:
  - Fixed an issue where converting dynamos to steam boilers failed to update downstream fluid alternative ports and output steam tiers in offline headless environments.

## [2.1.0-alpha.5] - 2026-09-02

### Added
- **Hierarchical Compound Module BOM Aggregation & Scaling**:
  - Recursively resolves nested subgraphs inside Compound Modules and scales enclosed machine and multiblock structure requirements proportionally based on parent module machine counts when compiling the Bill of Materials (BOM).
- **Singleblock Tiered Item Resolution & BOM Traceability**:
  - Automatically resolves singleblock machine nodes into voltage-tiered item forms (e.g. `LV Rock Breaker` instead of generic controller IDs) and tracks which factory machines and process lines require each part in detail tooltips (`usedByMachines`).
- **GTCEu & Star Technology Multiblock Intrinsic Trait Addons**:
  - Added support for configuring GTCEu and Star Technology multiblock traits in the hardware config modal, including Throughput Boosting (4x Parallel, 1.6x Duration in Pyrolyse Oven / Super Cracker), Bulk Processing (16x Parallel, 13x Duration in LOAF / Ultimate ABS), and Overpressure (8x Parallel, 1.5x Duration, 1.25x EU in Autoclave), with full support for compound modifier chaining.
- **Create Sequenced Assembly Distinct Machine Icons**:
  - Automatically extracts and renders distinct machine icons for individual sub-steps (Deployer, Spout, Mechanical Press, Mechanical Saw) across Create sequenced assembly compound recipe layers.

### Changed & Improved
- **Shared Machine Pool BOM Duty Cycle Quantization**:
  - Aggregates cumulative duty cycles across enclosed machines sharing physical hardware and quantizes required machines to integral counts, preventing duplicate structural part entries in the BOM.
- **Batch Mode & Bulk Processing Trait Clarification**:
  - Clearly separated zero-penalty Batch Mode from 16x Bulk Processing mode in machine config tabs, tooltips, and badges.
- **Machine Selector Search & Category Mapping**:
  - Enhanced filtering, categorization, and sorting for singleblock and multiblock machines across multi-mod environments in the machine selector dialog.
- **In-Game Guide & Hotkey HUD Widget Reinforcement**:
  - Synchronized in-game guidebook entries and HUD quick action badges with the latest 16px grid snapping (`G`), quick page switcher (`Ctrl + K`), junction external supply modes, and shared machine pool features.
- **Star Technology Custom Recipe Modifier Integration**:
  - Enhanced deterministic reflection integration for Star Technology custom recipe modifiers and multiblock capabilities.

### Fixed
- **GTCEu ULV Recipe Overclock Delta Calculation**:
  - Fixed an issue where recipes with ULV base tier (8 EU/t) calculated an extra overclock tier delta when running on LV or higher voltage machines.
- **Heating Coil Capability Detection on Specific Multiblocks**:
  - Fixed an issue where certain non-coil multiblock machines (such as Rock Filtrator and Geode Filter) erroneously displayed heating coil addon slots due to loose recipe category matching.

## [2.1.0-alpha.4] - 2026-09-02

### Added
- **Applied Energistics 2 (AE2) Integration & Autocrafting Plan ETA**:
  - Bind any flowchart sub-page directly to an AE2 Processing Pattern (`[💠 Bind AE2 Pattern]`) or generate board pages directly from the Pattern Encoding Terminal.
  - Renders an unobtrusive header banner with calculated total ETA, total energy, and bottleneck drill-down badges directly on AE2's Crafting Confirmation Screen (`CraftConfirmScreen`) prior to starting autocrafting jobs.
- **16px Precision Grid Snapping**:
  - Toggle discrete 16px coordinate snapping (`G` key or lower-left HUD checkbox) for nodes, frames, and sticky notes.
- **Hierarchical Page Navigation Drawer (`PageBrowserDrawer`)**:
  - Slide-in sidebar navigation drawer for organizing dozens of canvas pages inside expandable virtual folder trees with real-time text search and drag-and-drop page relocation.
- **Quick Page Switcher (`Ctrl + K`)**:
  - IDE-style fuzzy search popup modal to instantly jump between canvas pages across complex project workspaces.
- **Infinite & Fixed-Rate External Supply (`SupplyMode`)**:
  - Configure Junction nodes as infinite external sources or specified per-second rates, blocking unwarranted upstream demand and automatically offsetting raw input deficits in summary calculations.
- **Machine Hardware Template Cloner (`TemplateCloneDialog`)**:
  - Extract voltage tier, parallel count, overclock mode, and addon configurations into named reusable templates and batch-inject them across selected machine nodes.

### Changed & Improved
- **Scrollbar Interaction Optimization**:
  - Added proportional track click jumping and robust global mouse thumb dragging across recipe search lists and picker dialogs.
- **Process Balance Summary External Flow Accounting**:
  - Enhanced `SummaryOverlay` calculations to accurately deduct infinite and fixed-rate external supplies when compiling net raw material deficits.
- **Documentation & In-Game Guide Synchronization**:
  - Fully synchronized official technical specifications and in-game guidebook entries with the latest hotkeys (`G`, `Ctrl+K`), folder drawer, template cloner, and AE2 features.

### Fixed
- **Recipe Search Scrollbar Dragging Issue**:
  - Fixed an issue where clicking and dragging the scrollbar thumb in the recipe search dialog did not update the list position.
- **Mod Addon & Fusion Capability Detection Inconsistencies**:
  - Fixed an issue where certain fusion category machines and Thermal upgrade kits failed to register hardware stats in specific modpack environments.

## [2.1.0-alpha.3] - 2026-09-02

### Added
- **Turbine Generator Rotor Lifetime & Wear Rate Modeling**:
  - Calculates real-time durability wear rate per second and total estimated lifetime for turbine rotors in Large Gas, Steam, and Plasma Turbines, rendering detailed durability badges in card tooltips.
  - Added decoupled tier selection for Rotor Holders and Dynamo Hatches in machine configuration.
  - Added Lubricant supply toggle for 100% maximum turbine output efficiency boost.
- **Dynamic Available Machine Parallel & Overclock Estimation**:
  - Dynamically calculates the physical maximum parallel capacity (P_max) supportable by equipped energy hatches and supplied voltage, displaying visual warnings when configured parallel exceeds hardware limits.
- **Machine & Multiblock Selector Interactive Tutorial Step**:
  - Added interactive tutorial onboarding steps for switching between singleblock machines and multiblock structures (e.g. Electric Blast Furnace), exploring hardware capability badges (`🏛 Multiblock`, `♨ Coil`, `⚡ Par Hatch`).
- **In-Game Command Support (`/calcboard open`)**:
  - Added `/calcboard open` command on client and server environments to instantly launch and access calculator boards.

### Changed & Improved
- **Canvas Interaction & Sub-Handler Optimization**:
  - Improved mouse dragging responsiveness, frame resizing, note editing, and wire routing across large-scale factory canvases.
- **Heating Coil, Reflector & Thermal Augment Detection Precision**:
  - Enhanced runtime specification detection for modpack custom heating coils, nuclear reactor reflectors, and Thermal Series augments, accurately factoring temperature and efficiency bonuses into overclock formulas.
- **Modular Property Registry & Optional Mod Compatibility**:
  - Decoupled tech mod properties (GTCEu, Create, Thermal) into dedicated modular registries (`GTCEuProperties`, `CreateProperties`, `ThermalProperties`), ensuring robust stability and isolation in modpack environments where optional mods are absent.
  - Blueprints containing properties from uninstalled mods are safely preserved without data loss or crashes during cross-instance transfers.

### Fixed
- **Dedicated Server Headless Classloading Safety**:
  - Completely isolated client GUI and rendering dependencies to eliminate `NoClassDefFoundError` crashes on dedicated server environments.
- **Turbine Power & Coil Temperature Missing Bonus Calculation**:
  - Fixed an issue where certain gas turbines and multiblock reactors defaulted to baseline efficiency values without factoring in equipped rotor bonuses or custom coil temperatures.

## [2.1.0-alpha.2] - 2026-08-31

### Added
- **Shared Machine Pool (Time-Sharing Frame)**:
  - Added dedicated Shared Machine Pool frames allowing multiple recipe cards to share a single physical machine pool across time.
  - Computes cumulative duty (`Total Duty %`) and quantized required machines (`Ceil`), displaying real-time header badges and comprehensive breakdown tooltips.
  - Added `[⚙ Config]` button in the frame header to synchronize voltage tiers, overclock modes, parallel limits, and equipped addons across all enclosed machines.
  - Accurately de-duplicates shared multiblock structures in the Bill of Materials (BOM) calculation.
- **Port Multi-Selection & Bundle Batch Wiring UX**:
  - Added marquee port selection: dragging a box over machine cards selectively grabs input or output ports without selecting entire cards.
  - Added Windows Explorer-style selection: `Ctrl + Click` to toggle individual ports, `Shift + Click` to select continuous port ranges.
  - Added multi-wire bundle dragging with real-time multi-bezier curve rendering.
  - Dropping on empty canvas automatically creates vertically aligned Junction nodes with 1:1 wiring.
  - Dropping onto a Shared Machine Pool automatically wires matching machines, spawns missing recipe cards from the search index, synchronizes hardware configs, and expands frame bounds.
  - Intelligent secondary input preference matching: prioritizes recipes sharing the same secondary fluids/items (e.g. Lubricant vs Water) as the template machine.
  - Holding `Shift` while dropping performs 1:1 Auto-Ratio matching with exact fractional machine counts (Duty).
  - Added `[⛶ Auto-Fit]` action button in frame headers and double-click to instantly auto-fit frame bounds to enclosed contents.
- **Interactive Tutorial Step 11**:
  - Added interactive tutorial step introducing Shared Machine Pools, hardware synchronization, and bundle wiring.

### Changed & Improved
- **Two-Column Machine Card Port Highlight Precision**:
  - Refined slot selection highlights and hit-test bounding boxes on 2-column cards so port highlights remain strictly confined to their respective left/right halves even on rows with empty inputs or outputs.
- **Frame Auto-Expansion on Node Spawning**:
  - Frame bounds automatically expand downward when auto-spawning multiple recipe cards from bundle drops, fully integrated with `ResizeFrameCommand` for instant Undo/Redo (`Ctrl + Z`).

### Fixed
- **Fractional Auto-Ratio Machine Count Truncation**:
  - Fixed an issue where performing 1:1 Auto-Ratio on newly spawned machines in a Shared Machine Pool truncated fractional counts (e.g. `0.22`, `0.47`) into integer `1`.
- **Junction Node Bundle Spawn Rendering Sync**:
  - Fixed a UI widget list synchronization issue where junction nodes created from bundle drops did not appear on the canvas until another node was modified.

## [2.1.0-alpha.1] - 2026-08-31

### Added
- **I/O Port Hiding & Selective Restore**:
  - Added the ability to hide unwanted input/output ports by right-clicking socket ports, automatically disconnecting attached wires to keep complex multi-product machine cards compact and clean.
  - Added a status pill badge at the bottom of cards indicating the number of hidden ports (e.g. `2 Outputs hidden`, `1 Input hidden`, `3 Ports hidden`).
  - Clicking the pill badge opens a dropdown popup modal listing all hidden ports with ingredient previews, allowing individual ports to be restored to the card with a single click.
  - Hidden port states are fully preserved across local saves, dedicated server multiplayer synchronization, and shareable blueprints.
- **Smart Inline Text Editing Engine**:
  - Upgraded all canvas inline editable text fields (machine name, machine count, parallel count, target batch quantity) with rich text manipulation.
  - Added mouse click/drag cursor positioning, range selection with `Shift + Arrow Keys` / `Shift + Home/End`, and select-all with `Ctrl + A`.
  - Added fast word-level jumping and deletion via `Ctrl + Arrow Keys` and `Ctrl + Backspace / Delete`.
  - Added full clipboard integration for copy (`Ctrl + C`), cut (`Ctrl + X`), and paste (`Ctrl + V`).
- **JEI Unofficial Full Compatibility & Dedicated Integration**:
  - Added full support for the JEI Unofficial recipe viewer fork, including native bookmark groups and crafting mode integration.
  - Separated JEI++ (Just Enough Calculation) and JEI Unofficial integrations into dedicated subsystems for robust independent lifecycle management.
- **Multiblock BOM Shopping List Export to JEI / JEI++**:
  - Direct export of required multiblock construction parts, casings, and hatches to JEI / JEI Unofficial bookmark groups or JEI++ calculation goals via the BOM dialog and `B` hotkey.

### Changed & Improved
- **JEI Recipe Slot & Layout Extraction Stability**:
  - Upgraded the recipe parsing layer for JEI 15.20+ and JEI Unofficial to safely capture complex vanilla and modded recipe layouts without crashing or missing item stacks.

### Fixed
- **JEI Recipe Search Missing Ingredients & Anvil Fallback**:
  - Fixed an issue where browsing vanilla recipes in JEI Unofficial caused recipes to display as empty `[Anvil] Anvil` entries with missing input and output item previews.
