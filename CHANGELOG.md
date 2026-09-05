# Changelog

<p align="center">
  <b>English</b> | <a href="CHANGELOG_KR.md">한국어</a>
</p>

## [Unreleased]

## [2.2.0-alpha.2] - 2026-09-06

### Added
- **Tutorial Previous Step Navigation & UI Label Alignment**:
  - Added a `[⮜ Prev]` navigation button to the tutorial overlay, allowing players to navigate back to previous steps and re-experience earlier tutorial exercises.
  - Aligned tutorial instructions with the latest RFC-025 workspace UI labels across all 4 languages (referencing `[+ Add Recipe...]`, `[🔍 Search recipes...]`, `[▦ BOM]`, right Inspector `[⚙ Configure Junction]`, and `[📁]` activity dock button).
- **Junction Node Fixed Continuous Drain Mode & Inspector Integration**:
  - Added a dedicated "Fixed Continuous Drain Rate" mode (`SupplyMode.FIXED_DRAIN`) to Junction (Reroute) nodes, allowing players to specify persistent external consumption rates (e.g., `-65,536 mB/s` for passive rocket tank fueling or continuous steam turbine draw).
  - Propagates persistent drain rates as active upstream port demand, enabling auto-ratio (`A` key / `FlowGraphSolver.autoRatioFromAnchor`) to automatically scale upstream producer machine counts to match the drain rate.
  - Resolved output port flow rate checking (`getOutputPortStats`) and tooltip calculations to accurately recognize continuous drain rates as connected consumer demand, showing correct consumed flow, deficit warnings (⚠), and balanced supply metrics.
  - Seamlessly integrated into the Flow Summary Aggregator (`FlowSummaryAggregator`) as baseline consumption, accurately offsetting upstream production for balanced net accounting.
  - Enhanced Node Cards (`NodeCardRenderer`) and Inspector Side Panel (`NodeInspectorPanel`) with orange accent styling, badges (`-rate/s`), and interactive configuration dialog support (`JunctionSupplyDialog`).
- **Target Output Rate Inverse Solver & Fractional Auto-Ratio**:
  - Added a Target Output Rate Dialog (`Ctrl + Left Click` on any output port) that automatically calculates the required machine count from a desired production rate.
  - Supports fractional expressions (e.g., `1/12s`, `1/60s`, `5/2min`) and diverse rate units (`/t`, `/s`, `/min`, `/h`, `/d`, `mB/s`, `B/min`) for instant entry without manual conversion.
  - [⚖ Auto Ratio] now supports high-precision fractional scaling up to 4 decimal places (`0.0001` precision) without forced integer ceiling rounding, accurately synchronizing slow or intermittent production lines.
- **Unified Canvas Workspace & Context-Driven Controls (RFC-025)**:
  - **Right-Click Context Menu**: Right-clicking empty canvas space opens a creation/action menu (Add Recipe, Junction, Paste, Fit View, Auto Connect, Auto Ratio). Right-clicking nodes, selections, or ports opens targeted action menus with hotkey indicators.
  - **Unreal Blueprint Navigation & Smooth Diagonal Pan**: Right-click drag pans the canvas smoothly, while stationary right-click (<4.5px) opens the context menu. Added GLFW polling-based smooth WASD/arrow key panning with inertia and damping, fully supporting diagonal movement (WA, AS, SD, WD) and Shift acceleration.
  - **Modern Compact Header Toolbar & Integrated Help Dropdown**: Redesigned the top toolbar to match modern IDE layouts: compact left controls (`[⚙ Settings]`, `[Page Name ▼]`, `[🔍 Recipe Space]`, dropdown groups for `[Optimize ▼]`, `[View ▼]`, `[I/O ▼]`, `[? Help ▼]`), and right quick actions (`[↶ Undo]`, `[↷ Redo]`, `[✕ Close]`). Dropdown menus dynamically resize to fit contents without text clipping, and toggleable view options (Rate Unit, Fluid Display, Grid Snap, Slim Card Mode) remain open on click for rapid successive adjustments. The new `[? Help ▼]` dropdown provides instant access to User Manual (📖), Basic Interactive Tutorial (▶), Advanced Tutorial (✦), and Hotkey Guide (⌨), with quick tutorial launch buttons also integrated into the Guidebook modal and Hotkey HUD header.
  - **Left Activity Side Dock**: Added a slim vertical activity dock on the left edge with instant toggles for Page/Folder Explorer (`📁`), Favorite Recipes (`⭐`), Blueprints & Templates (`📋`), Team Workspaces (`👥`), Hotkey Guide (`?`), and Settings (`⚙`), fully integrating previously floating favorite and hotkey chips into a clean side dock layout matching the RFC-025 wireframe design.
  - **Node Inspector Side Panel & Slim Card Mode**: Selecting a node opens a dedicated right-side Inspector panel for count adjustments, tier chips, overclock modes, and hardware configurations. Added a Slim Card Mode option in Settings to keep cards compact on canvas.
  - **Adaptive Bottom Status Bar**: Added an adaptive bottom bar showing selection count or total node/wire stats with quick chips (`[∑ Balance]`, `[▦ BOM]`, `[⏸ Pause]`).
- **Per-Craft Batch View Mode (`1x`)**:
  - Added a `1x` per-craft batch unit to the canvas toolbar and settings dialog unit cycling list (`/t` -> `/s` -> `/min` -> `/h` -> `/d` -> `1x`).
  - Displays raw recipe input/output amounts per single craft cycle independent of machine counts or overclocked cycles-per-second.
  - Ports with perfectly matching stoichiometric amounts display a green checkmark (`✔`), allowing instant visual verification of 1:1 chemical reaction ratios and closed recycling loops.

- **Combustion Generator Family Tier Progression & Rated Output Capping**:
  - Established seamless tier progression between singleblock combustion generators (LV, MV, HV) and multiblock combustion engines (EV LCE, IV ECE, LuV~UEV combustion modules).
  - When changing the voltage tier via node cards or the inspector panel (e.g., HV -> EV, EV -> IV), nodes automatically transition to the matching machine hardware (`LV/MV/HV Combustion Generator` ➔ `EV Large Combustion Engine` ➔ `IV Extreme Combustion Engine` ➔ `LuV+ Combustion Module`), updating the machine icon, multiblock flag, and display name simultaneously.
  - Resolved power distortion where singleblock generators produced abnormal amperage (e.g., `60A MV`) from high-energy fuels by deterministically capping output to the singleblock rated voltage (1A at that tier).
- **Combustion Engine & Modular Combustion Boosting (GTCEu & Star Technology)**:
  - Full support for GTCEu Large Combustion Engine (LCE, EV) with Oxygen boosting (1.5x power, 2x fuel parallel) and Extreme Combustion Engine (ECE, IV) with Liquid Oxygen boosting (2.0x power, 2x fuel parallel).
  - Full support for Star Technology Modular Combustion Frame (MCF) coolant multipliers (Distilled Water +20%, Deionized Water +40%, unsupplied -10%) and T1~T4 Combustion Modules with oxidizer boosting (WFNA 5x, RFNA 6x, O2F2 8x, FCSO 12x power, 2x fuel parallel).
  - Generator recipes scale power deterministically via voltage-proportional parallels without electric overclock duration reduction, preserving per-recipe batch duration (0.40s) and fuel energy density (EU/mB).

### Changed & Improved
- **Closed-Loop Recirculation Protection & Greedy Supply-Filling Allocation**:
  - Enhanced the flow balance solver to protect closed recirculation loops (e.g., chemical byproduct recycling) against feedback attenuation and rate collapse.
  - Implemented greedy demand-filling allocation to fulfill 100% of consumer requirements before distributing surplus flows.
  - Clarified port status indicators: actual shortages relative to current machine speed are flagged with amber deficit warnings (⚠), while upstream-induced speed limits are indicated by a distinct blue throttled badge (↓).
- **Pyrolyse Oven & Liquefaction Tower Coil Modifier Accuracy**:
  - Accurately computes coil temperature speed modifiers for Pyrolyse Ovens and Liquefaction Towers (+50% speed per tier above Kanthal 2700K, 0.75x speed penalty for Cupronickel).
- **Onboarding Tutorial UX Improvements & Event-Driven Decoupling**:
  - Clarified Step 1 instructions to guide players to right-click empty canvas space to open the context menu and select Recipe Search.
  - Enhanced Step 4 to visually glow existing wires and instruct players to right-click to cut the connection wire first, then Shift-drag from the junction output to the turbine input for 1:1 auto-ratio scaling.
  - Adjusted Advanced Tutorial Step 12 junction node coordinates to spawn cleanly below the Shared Machine Pool frame, preventing visual overlap with cutter machine card output slots.
  - Completely decoupled canvas wire interaction handlers from `TutorialManager` by publishing Forge `FlowGraphEvent` (WireConnected, WireDisconnected, JunctionInserted), with `TutorialManager` subscribing and reacting exclusively to tutorial-scoped graph mutations.

### Fixed
- **Electric Blast Furnace (EBF) Excess Temperature Perfect Overclock (POC)**:
  - Fixed an issue where Electric Blast Furnace (EBF) and Alloy Blast Smelter (ABS) failed to apply 1 Perfect Overclock (POC, 4x speed, 0.25 duration) per 1800K excess temperature above recipe requirement (T_machine = T_coil + 100 * max(0, Tier - 2)) and 0.95x energy discount per 900K.
- **Generator Mode Reset on Non-Turbine Power Generators**:
  - Fixed an issue where `isGenerator` was incorrectly cleared for non-turbine power generators (Combustion Engines, Dynamos) during node validation.
- **Per-Craft (`1x`) Tooltip Rate Distortion & Duplicate Suffix Bug**:
  - Fixed an issue where hovering over input/output ports in `1x` batch mode displayed distorted production rates (e.g., overclocked per-second throughput instead of per-craft batch amounts) and malformed duplicate suffixes (e.g., `(11.52 B1x)`) when holding `Shift`.
- **Coil Addon Stat Invalidation on Card Controls & Header Quick Select**:
  - Fixed an issue where changing heating coils via node card badges (`♨ [Temp]K`) or the config dialog top quick-select header failed to update machine EU/t, duration, and cycle rates until manually manipulated through the lower catalog grid.
- **Input Consumption Chance & Signed Tier Boost Calculation Bug**:
  - Fixed an issue where input ingredients with probabilistic consumption chances (e.g., Star Technology Cyclonic Sifter Netherite Reinforced Mesh with 3% consumption chance) were calculated as 100% consumed, distorting factory demand rates by up to 33x.
  - Added full support for negative tier chance boosts (e.g., -0.2%/tier reduction in mesh consumption on overclock) in domain rate solvers, recipe converters (EMI/JEI), NBT serialization, and port tooltips.
- **Junction Node Inspector Panel Distortion & Missing Supply Dialog Access**:
  - Fixed an issue where selecting a Junction/Reroute node improperly displayed machine-specific controls (machine count, voltage tier chips, overclock mode, hardware config, and power/duration stats) in the right-hand Inspector panel.
  - Implemented a dedicated junction inspector layout featuring bound ingredient preview, supply/buffer mode badges, target batch amount with ETA/DT duration badge, and total inflow/outflow/net flow statistics.
  - Added a direct `[⚙ Configure Junction]` shortcut button in the inspector panel and integrated junction configuration into the node right-click context menu to grant immediate access to external supply modes, buffer size, and port allocation limits.
- **Wire Severing Blocked by Context Menu Regression**:
  - Fixed a regression where right-clicking connection wires on the canvas opened the canvas context menu instead of cutting the wire.
  - Reordered canvas mouse event priorities so right-clicking wires severs them immediately with sound effects and undo support, while preserving canvas and node context menus for stationary right-clicks on empty canvas and node cards.
- **Group Frame & Sticky Note History (Undo / Redo) Support**:
  - Fixed an issue where creating group frames (`Ctrl+G`), shared machine frames (`Ctrl+Shift+S`), and sticky notes failed to record undo/redo history commands, preventing players from reverting newly created frames.
  - Fixed a critical loss of group frames and sticky notes when collapsing them into a compound module or grouping nodes, ensuring `GroupModuleCommand` and `ExpandModuleCommand` fully capture and restore enclosed frames and notes upon undo and redo.
  - Added undo/redo history tracking for frame and sticky note property edits (dialog saves, color cycling, sticky note deletions, and resizing).
- **Tutorial Step 12 Infinite Supply Event Detection**:
  - Resolved an issue where configuring a Junction node to Infinite Supply did not advance the tutorial step by dispatching `FlowGraphEvent.JunctionConfigured` and adding fallback detection in `FlowGraphEvent.PostSolve`.

## [2.2.0-alpha.1] - 2026-09-05

### Added
- **Greate Mod Compatibility & Kinetic Tier Integration**:
  - Added native integration for the **Greate** mod (Create + GregTech addon), bringing kinetic processing lines directly into the calculator board.
  - Full support for all 10 kinetic machine tiers: Andesite (ULV), Steel (LV), Aluminium (MV), Stainless Steel (HV), Titanium (EV), Tungstensteel (IV), Chrome (LuV), Iridium (ZPM), Osmium (UV), and Neutronium (UHV).
  - Dedicated recipe handling and speed/stress calculations for Greate Mills, Crushers, Mixers, Presses, and Saws.
  - Integrated circuit configuration (1-24) support and validation for recipes requiring circuit settings.
  - Shaft torque capacity validation: machine nodes display warning indicators when total required stress exceeds the safe capacity of the connected shaft tier.
- **High-Speed Recipe Animation Batching & Multiplier Badges**:
  - Ultra-fast recipes with cycle times under 1.0s (such as 0.05s centrifuges or extreme overclocks) now automatically bundle into smooth visual bursts at a stable 1.0s interval.
  - Flow particle pulses display multiplier badges (e.g., `2x`, `5x`, `20x`) reflecting the batched cycle count, maintaining clear readability and fluid 60 FPS rendering performance without particle overlap.
- **Junction Priority Flow Routing & Accumulation Buffers**:
  - **Fixed Rate Priority Routing**: Output connections from junction nodes can now be assigned a fixed flow limit. The flow solver fulfills fixed-rate priority consumer lines first before balancing remaining flows among unconstrained consumers.
  - **Accumulation Buffer Mode**: Junction nodes can be toggled into Accumulation Buffer mode with a configurable target batch size. The system calculates the charge duration and duty cycle, preventing false-positive starvation warnings for downstream machines running on intermittent periodic batches.
  - Input slots receiving buffered batch flows display an amber hourglass indicator (⏳) and a detailed tooltip explaining the intermittent duty cycle.
  - Redesigned the Junction Supply Dialog with a dedicated flow allocation table, fixed limit inputs, and accumulation buffer settings.

### Changed & Improved
- **Orthogonal Dual-Stream Wire Flow Modulation**:
  - Decoupled incoming feed line animations from outgoing product lines:
    - Input lines lacking sufficient supply emit an amber-to-crimson warning pulse to clearly indicate feed starvation.
    - Outgoing product lines from partially starved machines travel smoothly at speeds reduced proportionally to actual machine operating efficiency, accurately representing physical production rates rather than displaying false error pulses.
- **Page Tab Bar Width Calculation & Icon Alignment**:
  - Included pin and AE2 status icon prefixes into the exact tab width calculation in `PageTabBarWidget`, preventing title text clipping and hover hitbox drift across open tabs.
- **Hardware Addon Catalog Ordering**:
  - Added dedicated catalog comparator prioritizing reset cards, category priority, coil temperatures, and hatch tiers in machine addon selection dialogs.
- **Four-Language Localization Synchronization (i18n)**:
  - Synchronized 27 new translation keys across English, Korean, Russian, and Simplified Chinese covering Greate kinetic machine tiers, shaft overload warnings, circuit badges, junction priority routing, and batch accumulation buffers.

### Fixed
- **Star Technology Reflector Fusion Reactor Overclock Boost Scaling**:
  - Fixed an issue where equipping higher-tier reflectors (e.g. T3 or T4 on recipes requiring T2 like Duranium) on Reflector Fusion Reactors failed to apply overclock boosts, keeping production rates fixed at base speed (14.40s, 10 mB/s, 0 OC).
  - The calculation engine now accounts for the excess reflector tier delta (Installed Tier - Required Tier) and applies GTCEu 2:2 Perfect Overclocking (halving duration and doubling EU/t per tier delta), matching Star Technology's in-game behavior. Added reflector overclock boost indicators (+N OC, Nx speed) to reflector badge tooltips.
- **Star Technology Throughput Boosting (TPB) Machine Duration Integer Tick Truncation**:
  - Fixed an issue where machines with duration multipliers such as Star Technology's Industrial Accumulation Vessel (Throughput Boosting: 1.6x duration, 4x parallel) calculated fractional ticks (e.g. 2 ticks * 1.6 = 3.2 ticks -> 0.16s) instead of integer ticks (3 ticks -> 0.15s, 26.67 B/s).
  - The calculation engine now truncates post-overclock machine duration to integer ticks using floor rounding, aligning with GTCEu Modern's recipe execution engine.
- **Star Technology Large Chemical Reactor (LCR) Coil Buff Environment Isolation**:
  - Fixed an issue where Star Technology's custom heating coil speed bonus and energy discount (`Chemical Reactor: 100% Spd, 95% Energy`) were erroneously applied to Large Chemical Reactors across all modpacks (including vanilla GTCEu Modern and TFG), causing calculation discrepancies and confusing coil tooltips.
  - Coil speed and energy bonuses for Large Chemical Reactors are now strictly isolated to environments where Star Technology is loaded (`ModCompatHelper.isStarTLoaded()`), treating LCRs in vanilla GTCEu as standard multiblocks without false coil bonuses and hiding the chemical reactor line from coil tooltips.
- **Create & Greate Secondary Workstation Precedence**:
  - Resolved an issue in `EmiRecipeConverter` where Create Basin and Blaze Burner blocks were erroneously selected as the primary workstation icon for mixer and press recipes instead of the actual mechanical machine.

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
  - **One-Click Summary Overlay Voiding & Collapsible Restore**: Added a `[🗑️]` void button to net product rows in `SummaryOverlay` to move surplus byproducts to voiding with one click, along with a collapsible `🗑️ Voided Byproducts` section and `[↩️]` one-click restore button.

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

## [2.0.2] - 2026-08-31

### Added
- **Multi-Column & Progressive Assembly Compound Cluster Generation (`EmiStepRecipeDetector`, `CompoundRecipeBuilder`)**:
  - Multi-step, multi-slice, and progressive assembly recipes (such as `Stargate Component Assembly` and GTCEu `Assembly Line` with up to 16 columns) imported from recipe viewers are now automatically partitioned across sequential layer nodes (`Layer I` through `Layer XVI`) grouped inside a purple Compound Module frame (`🧩 ...`).

### Fixed
- **GTCEu Macerator Byproduct Tier Gating Logic (`GTPowerCalculator`, `NodeRateCalculator`, `BoardTooltipRenderer`)**:
  - Fixed an issue where secondary outputs (slot 1 onward) in Macerator recipes with 100% base chance or multi-product definitions (such as Plant Ball processing) were incorrectly calculated and displayed as active on ULV/LV/MV tiers or labeled with inaccurate tier requirements.
  - Gated all secondary outputs from slot 1 onward behind the HV+ voltage tier requirement (`Byproducts from HV+`), matching in-game singleblock and multiblock Macerator behavior, with inactive outputs properly displaying `0% (Requires HV+)`.

## [2.0.1] - 2026-08-31

### Added
- **Automatic Fusion Reactor Controller Matching (`GTCEuModAdapter`, `GTAddonCompatibilityHandler`, `EmiRecipeConverter`, `JeiRecipeConverter`)**:
  - When importing recipes for Fusion Reactors (Mk1, Mk2, Mk3), the controller workstation matching the recipe's minimum required voltage tier (e.g. 180M EU -> ZPM Mk2) is now strictly prioritized and assigned as the default machine icon upon creation.

### Changed & Improved
- **Machine Configuration Dialog Layout & Label Optimization (`MachineConfigDialog`, `GTCEuModGuiHandler`, `AddonCatalogView`)**:
  - Compacted top header buttons (Recipe Switch, Single/Multiblock Toggle, Font Scale) and added ellipsis truncation for long machine titles to eliminate button overlap across all languages.
  - Formatted controller buttons in the multiblock selector (e.g. `⚛ Fusion Mk1/Mk2/Mk3`, `⚡ Aux Mk1/Mk2`) by stripping internal bracket tags (such as `[FRC I]`) for clean rendering within constrained widths.
  - Refined horizontal category scroll bar navigation (◀ / ▶ arrows) and added smart name shortening for long addon titles in the hardware catalog.
- **Reroute Junction Flow Balancing & Multi-Branch Auto-Ratio (`FlowBalanceMatrixSolver`, `FlowGraphTopologyAnalyzer`, `FlowSummaryAggregator`)**:
  - Enhanced Auto-Ratio backwards and forward balancing across complex multi-branch network graphs containing reroute/junction nodes, ensuring 100% operational efficiency without artificial flow bottlenecks.
  - Improved port flow statistics gauges to accurately track pass-through demands on reroute nodes.

### Fixed
- **Fusion Reactor Node Tier & Badge Synchronization on Creation (`EmiRecipeConverter`, `GTBadgeProvider`, `GTAddonCompatibilityHandler`)**:
  - Fixed an issue where recipes requiring Fusion Mk2 (ZPM) were overwritten with Mk3 (UV) controllers during multiblock conversion, causing voltage tier and badge mismatch.
  - Fixed badge evaluation in `GTBadgeProvider` to derive reactor tier from the active controller model rather than purely runtime operating voltage, preserving accurate Mk1/Mk2/Mk3 display when overclocking.
- **GTCEu Turbine Rotor Holder Efficiency Calculation (`GTPowerCalculator`, `GTTurbinePhysics`, `GTTurbineHelper`)**:
  - Fixed an issue where the efficiency bonus from higher-tier rotor holders (e.g. EV, IV, ZPM) was incorrectly added rather than scaled multiplicatively with rotor efficiency, causing cycle duration and fuel consumption discrepancies.
- **GTCEu Recipe Viewer Extraction & Condition Branching (`GTCEuRecipeHandler`, `JeiRecipeConverter`)**:
  - Fixed an issue where certain complex GTCEu recipes with condition branches, multi-fluid outputs, or specific chance byproducts failed to extract properly or dropped valid output ports during EMI/JEI conversion (thanks to @kairan0 via PR #3, #4, #5).
- **Reflector Missing Badge & Warning Text Formatting (`GTBadgeProvider`, `GTNodeValidator`, `BoardTooltipRenderer`)**:
  - Fixed an issue where the missing reflector tier text displayed '(None)' in parentheses instead of a clean 'None' label, and standardized reflector badge symbols to ✦.

## [2.0.0] - 2026-08-30

### Added
- **Systeams & Steam Boiler Multi-Mode Support (`SysteamsModAdapter`, `SysteamsRecipeHandler`, `SysteamsModGuiHandler`)**:
  - Added seamless switching between Systeams Steam Boiler mode (steam generation) and Thermal Dynamo mode (RF generation) directly on machine cards.
  - Implemented exact steam boiler thermodynamics and boiling physics calculations for water, distilled water, and alternative boiling fluids.
- **Enhanced Thermal Series Augments & Upgrade Kits System (`ThermalAugmentHelper`, `ThermalModAdapter`, `ThermalModGuiHandler`)**:
  - Distinctly separated tier upgrade kits (LV~EV, 1-kit limit with automatic replacement) and standard augments (up to 3 slots).
  - Added Shift-click quick installation for multiple copies, clear slot capacity tooltips, and real-time RF/t power draw & cycle time scaling.
- **Simplified Chinese (zh_cn) Localization**:
  - Added full Simplified Chinese language support across all UI screens, dialogs, tooltips, and badges.

### Changed & Improved
- **Reroute Junction Flow Balancing & Supply Tracking (`FlowBalanceMatrixSolver`)**:
  - Enhanced multi-input and multi-output reroute nodes to accurately track upstream producer operational efficiencies and downstream port demands, preventing flow bottlenecks and ratio calculation distortions.
- **Fluid Tag Alternative Interpretations in Recipe Conversion (`EmiRecipeConverter`)**:
  - Fluid input ports defined with fluid tags are now automatically expanded to discover and link all matching alternative fluid identifiers across installed mods.
- **Canvas Interaction & Dialog Responsiveness**:
  - Polished rendering and click handling for auto-connect filters, toolbars, summary overlays, and machine cards.

### Fixed
- **Thermal Dynamo Energy Extraction & Base RF Scaling**:
  - Fixed an issue where certain Thermal and Systeams recipe categories failed to extract base recipe RF energy values, resulting in zero-duration or inaccurate throughput calculations.
- **Thermal Augment Multiplier Duplication Glitch**:
  - Fixed an issue where installing multiple identical augments could erroneously apply compounding multiplier calculations instead of linear additive scaling.

## [2.0.0-beta.1] - 2026-08-29

### Added
- **Blueprint Metadata & Preview Import/Export Dialog (`ExportBlueprintDialog`, `ImportBlueprintDialog`)**:
  - Added dedicated modal dialogs for exporting blueprints with custom titles, descriptions, and tags.
  - Added an interactive blueprint preview dialog when importing, showing total nodes, machines, wire counts, and major inputs/outputs before choosing to "Open in New Page" or "Overwrite Current Page".
- **Smart Auto-Connect Resource Selection Filter Dialog (`AutoConnectFilterDialog`)**:
  - When auto-connecting nodes or dragging wires, a resource selection dialog now allows players to preview and selectively check/uncheck matching items and fluids to connect.
- **Canvas Fit-to-View Feature & Hotkey (`Home` / `F`) (`ToolbarWidget`, `HotkeyHudWidget`)**:
  - Added a "Fit to View" button on the toolbar and hotkey shortcuts (`Home` / `F`) to instantly center the canvas and scale the zoom level to encompass all placed nodes.
- **Ratio & Mass Balance Settings Tab in Board Settings Dialog (`BoardSettingsDialog`)**:
  - Added a dedicated "Ratio & Balance" configuration tab to customize the maximum machine count limit for perfect integer harmonization and adjust surplus tolerance thresholds.

### Changed & Improved
- **Large Workspace Streaming & On-Demand Page Loading**:
  - Implemented chunked packet streaming for multiplayer team workspaces, preventing network payload overflows during large factory commits.
  - Optimized multiplayer connection speed by synchronizing page metadata first and loading full page canvas data on-demand during tab switching.
- **Wire Hover & Click Detection Performance Optimization**:
  - Implemented spatial partitioning indexing for canvas wires, ensuring smooth, lag-free wire selection, hovering, and rerouting even in massive factory flowcharts with hundreds of connections.
- **Multiplayer Edit Lock Instant Cleanup on Disconnect**:
  - Teammate canvas edit locks are now immediately released when a player closes the board or disconnects from the server, eliminating wait times for other team members.

### Fixed
- **JEI Mode Inactive Probability Byproduct Output Ports**:
  - Fixed an issue where 0% chance byproducts that should be inactive at lower voltage tiers (e.g. bone meal and feathers from raw chicken maceration in LV/MV) were incorrectly instantiated as output ports when imported via JEI.
- **Tier Switching Internal State Desynchronization**:
  - Fixed an issue where machine voltage tier adjustments temporarily displayed desynchronized power consumption figures until the node was moved or reopened.
- **Team Page Deletion Permission Validation Conflict**:
  - Fixed a client-server state desynchronization issue when non-admin team members attempted to delete shared team pages.

## [2.0.0-alpha.12] - 2026-08-29

### Added
- **Integrated Board Settings Modal Dialog (`BoardSettingsDialog`, `ToolbarWidget`)**:
  - Added a dedicated settings modal accessible via the `[⚙ Settings]` button on the top-right toolbar to easily configure and manage global fluid units (Auto, Always mB, Always B), time units (/s, /t, /min, /h), singleplayer pause toggling, and default overclock modes.

### Changed & Improved
- **Enhanced Hovered Recipe Addition Hotkey (`Shift + A`)**:
  - Hovering over items or recipes anywhere—including EMI and JEI recipe viewer screens, container/inventory sidebars, and bookmark/favorites panels—and pressing `Shift + A` now immediately adds the target recipe to the flowchart board.
  - Resolved modifier key conflicts ensuring `Shift + A` triggers reliably without interfering with active text inputs (`EditBox` search bar focus).
- **Multiblock Model Controller Selection in Machine Configuration Dialog**:
  - All valid multiblock machine variants (e.g. Large Macerator, Advanced Large Miner) supporting the same recipe category are now properly displayed and switchable via clicking or mouse-wheel scrolling in the dialog header.
- **Auto-Hide FTB Library Sidebar Buttons in `BoardScreen` (`ClientForgeEvents`)**:
  - Automatically hides FTB Quests, FTB Chunks, and FTB Teams sidebar buttons that previously overlapped the top-left canvas area in `BoardScreen`, providing a clean workspace while 100% preserving native EMI `[+]` transfer functionality.

### Fixed
- **Multiblock Energy Hatch & Parallel Hatch Combined Overclock Calculation Bug (`GTCEuModAdapter`, `GTParallelHatchAddon`, `EnergyHatchHelper`)**:
  - Fixed an issue where parallel overclocking exceeded maximum supported voltage/amperage when energy hatches were installed and added support for dual lower-tier energy hatch tier-up (+1 tier).
  - Fixed a multiplier calculation flaw where a machine's base parallel and equipped parallel hatch were erroneously compounded.
- **Multiblock BoM Optional Hatch & Parallel Hatch Casing Reduction (`GTCEuBOMHelper`, `MultiblockStructureCatalog`)**:
  - Uninstalled optional hatches (such as preview Elite Parallel Control Hatches) present in base 3D blueprint shapes are now properly excluded from the BoM and seamlessly restored to base structural casings (+1 count).
  - Intelligently pruned unnecessary item/fluid buses from recipes without corresponding I/O.
- **JEI / EMI Search Bar Typing Shortcut (`B`) Collision Bug (`ClientForgeEvents`)**:
  - Fixed an issue where typing into JEI or EMI search boxes inside inventory, container, or recipe viewer screens would inadvertently intercept the `B` key and open the calculator board.
- **JEI Recipe Indexing & Discovery in Search Dialog (`JeiRecipeViewerAdapter`)**:
  - Fixed an issue in JEI-only environments where recipes were delayed or failed to populate when opening the Recipe Search Dialog.
- **Programmed Circuit Input Port Bug in JEI Mode (`JeiRecipeConverter`, `GTCEuRecipeHandler`)**:
  - Fixed an issue where non-consumed configuration circuits (`gtceu:programmed_circuit`, `gtceu:integrated_circuit`) were incorrectly added as demand input ports (`-0/s` or `Demand: 2/s`) when converting recipes from JEI.
- **Steam Machine Tier Switching Icon Corruption (`GTCEuModAdapter`)**:
  - Fixed an issue where changing tiers or switching from LP/HP steam machines would erroneously redirect the machine icon to an unrelated controller (e.g. Large Miner).
- **Layered Recipe Extraction & Reflection Hardening (`LayeredRecipeHelper`)**:
  - Resolved missing layout slots and wrapper object extraction issues during GTCEu and third-party recipe conversion.

### Known Issues
- **Unintended Byproduct Output Ports in JEI Mode for LV/MV Macerator Recipes**:
  - When importing certain GTCEu recipes (e.g. Raw Chicken maceration) via JEI, byproducts with 0% chance that should be inactive at lower voltage tiers (LV/MV) are temporarily instantiated as output ports. (To be resolved in the next release).

## [2.0.0-alpha.11] - 2026-08-28

### Added
- **Phoenix Guilds (Teams) Mod Integration (`PhoenixGuildsProvider`, `TeamProviderRegistry`, `TeamProviderTest`)**:
  - Implemented soft-dependency team provider for Phoenix Guilds ([CurseForge 1612085](https://www.curseforge.com/minecraft/mc-mods/phoenix-guilds) / `phoenix_guilds`) using safe reflection.
  - Automatically isolates team flowchart workspaces for guild members and grants team page administration privileges to guild officers and owners (`GuildRank.OFFICER`, `GuildRank.OWNER`).
- **In-Place Alternative Recipe Switching (`RecipeNode`, `AlternativeRecipeFinder`, `MachineConfigDialog`, `RecipeSwitchTest`)**:
  - Switch recipes for existing nodes on the canvas directly without deletion via the `[🔄 Switch Recipe]` header button in `MachineConfigDialog` or the right-click node context menu.
  - Automatically matches and ranks candidate recipes by same machine ID and shared primary outputs.
  - Smart wire preservation retains existing connections for shared input/output ingredients (`IngredientStack.getId()`), with complete Undo/Redo (`Ctrl+Z` / `Ctrl+Y`) support.
- **Unified Recipe Viewer Support Extension (JEI / JEI++ & Vanilla Integration) (`IRecipeViewerAdapter`, `RecipeViewerRegistry`, `JeiRecipeViewerAdapter`, `VanillaRecipeViewerAdapter`, `RecipeViewerRegistryTest`)**:
  - Seamlessly supports environments running JEI (Just Enough Items) and JEI++ (Just Enough Calculation / BoM) with recipe catalog indexing, `[R]`/`[U]` hotkey lookups, bookmark synchronization, and one-click multiblock BoM tree registration.
  - Pure vanilla fallback enables offline and vanilla recipe catalog lookup when no recipe viewer mod is present.
- **Uniform Global Fluid Unit Formatting Option (`FluidUnitMode`, `FormatUtil`, `ToolbarWidget`, `HotkeyHudWidget`, `UiFormattingTest`)**:
  - Added canvas-wide fluid rate display unit toggles between `Auto` (smart unit scaling), `Always mB` (forced millibuckets), and `Always B` (forced buckets) via the toolbar button and `Shift+T` hotkey.
  - Selected fluid unit preference is automatically persisted across game sessions in client configuration.
- **5-Level UI Font Scale for Machine Configuration Dialog (`FontScale`, `MachineConfigDialog`)**:
  - Scale machine settings UI across 5 presets (`0.75x`, `0.85x`, `1.0x`, `1.15x`, `1.30x`) via the `[Aa 1.0x]` header button.
  - Supports left/right click cycling, mouse wheel scrolling, and `+`/`-` keyboard shortcuts with pixel-perfect center-anchored matrix unprojection.

### Changed & Improved
- **Page Tab Bar Overflow Navigation & Padding (`PageTabBarWidget`)**:
  - Added click support on `«` and `»` overflow indicators to smoothly scroll through tab bars when page counts exceed screen width.
  - Added a 16px right margin buffer preventing the final tab and the `[+]` button from being obscured by overflow arrows.
- **Hotkey HUD Fluid Unit Guide (`HotkeyHudWidget`)**:
  - Added `Shift+T` fluid unit cycle shortcut guide in the bottom-left hotkeys HUD widget.

### Fixed
- **Page Tab Bar Scissor Boundary Clipping & Unclickable Overflow Arrow Bug (`PageTabBarWidget`)**:
  - Fixed an issue where the right overflow arrow `»` failed to respond to mouse clicks and the right border of the final tab was clipped outside the scissor rendering rectangle.

## [2.0.0-alpha.10] - 2026-08-27

### Added
- **Target Batch Production Estimated Time (ETA) & Goal Node System (`RFC-005`, `ProductionETACalculator`, `NodeTargetBatchEditor`, `NodeProperties`, `ETACalculationTest`)**:
  - Set target batch goals (e.g. `100x`, `1,000x`, `10 B`) on reroute and terminal goal nodes.
  - Computes real-time estimated completion duration (Target Amount / Net Inflow Rate) and renders human-readable `ET: 24m 52s` badges beneath cards.
  - Hovering over goal nodes reveals total energy required (EU) and raw upstream material consumption across the batch duration.
  - Supports inline clicking/typing to edit batch goals with Shift + Click to reset.
- **Kinetic Generator & Motor EMI Recipe Integration & SU Search Indexing (`KineticGenerationEmiRecipe`, `CalcBoardEmiPlugin`, `CreateRecipeHandler`, `CreateNewAgeRecipeHandler`, `RecipeSearchEngine`)**:
  - Registered kinetic generation and motor recipes as native EMI synthetic recipes with workstations and outputs, allowing seamless favorite pinning and recipe lookup.
  - Enriched search indices so querying `<su`, `<stress`, or machine names immediately matches stress unit generation recipes.
- **Magnet & Multi-Stackable Addon Click Interaction (`MachineConfigDialog`, `CreateNewAgeModAdapter`)**:
  - Left-clicking catalog cards now stacks addons (+1) up to 12 slots, while Shift + Left-Click fills remaining slots (+12) or batch-replaces them.
  - Right-clicking uninstalls 1 copy (-1), and the top `Clear Magnets` button resets all installed magnets.

### Changed & Improved
- **Addon Configuration Dialog Performance & First-Open Stuttering Elimination (`MachineConfigDialog`, `MachineAddonCatalog`, `MultiblockDetector`, `GTCEuMultiblockScanner`, `CoilHelper`, `TurbineRotorHelper`)**:
  - Eliminated the 1-second freeze and frame drops when opening the machine configuration dialog by converting linear scans across 100,000+ EMI recipes and 10,000+ blocks into pinpoint O(1) category lookups and early keyword skips.
  - Optimized language code synchronization and preloading lifecycle so registry indexing executes seamlessly in background threads.
- **Deferred Addon Tooltip Rendering (`MachineConfigDialog`)**:
  - Resolved UI layering bug where lengthy addon descriptions were clipped by dialog boundaries.

### Fixed
- **Star Technology Greenhouse & Farm Threading/Coil Capability Misidentification Bug (`CategoryCapabilityMatrix`, `MultiblockDetector`, `StarTModAdapter`, `GTCEuThreadingTest`)**:
  - Fixed an issue where Farm/Greenhouse multiblocks containing decorative coil blocks were misclassified as heating coil multiblocks or had threading tabs unexpectedly activated upon switching icons.
- **Guarded Against Crashes When Pressing Keybinds Outside Worlds or in Config Menus (`BoardScreen`, `ClientForgeEvents`, `NetworkHandler`)**:
  - Prevented a `NullPointerException` crash in vanilla `AbstractContainerScreen.containerTick()` caused by opening `BoardScreen` via hotkey when not in a world (e.g. while in the title screen, controls menu, or mod config screens where `mc.player == null`).
  - Added guards so hotkeys do not intercept input while in pause, options, controls/keybinds, death, or configuration screens.
  - Added lifecycle validation guards across `BoardScreen.init()` and `containerTick()` to ensure the screen closes cleanly if world/player context is absent.
  - Gracefully closes `BoardScreen` upon logging out of a world and guarded packet dispatch against disconnected channels.
- **GT Thermal Centrifuge & Crafting Table Mod Misidentification Bug (`ThermalAugmentHelper`, `GTCEuModAdapter`, `CreateModAdapter`, `VanillaModAdapter`, `ModAdapterRegistryTest`)**:
  - Fixed a heuristic string matching bug where GregTech machines containing the word "thermal" (e.g. Thermal Centrifuge) were misclassified as Thermal Expansion machines and rendered with `⚡ Thermal` badges and RF power instead of GT EU/t.
  - Fixed a workstation routing bug where Vanilla crafting table recipes with secondary workstations from Thermal (Tinker Bench) or Create (Mechanical Crafter) were incorrectly captured by mod adapters instead of falling back cleanly to Vanilla Passive (`🍃 Passive`).
- **HP Steam to Electric Singleblock Machine Icon Recovery Bug (`CategoryCapabilityMatrix`, `GTCEuModAdapter`, `GTCEuSteamProcessingTest`)**:
  - Fixed an issue where disabling HP Steam mode unexpectedly promoted singleblock machines to multiblock icons instead of restoring the original tier-specific singleblock workstation.
- **Stress Unit (SU) Contextual Producer Search & Duplicate/Disabled Kinetic Entry Fix (`RecipeSearchDialog`, `RecipeSearchEngine`, `CalcBoardEmiPlugin`, `CreateRecipeHandler`, `CreateNewAgeRecipeHandler`, `RecipeSearchEngineTest`)**:
  - Fixed an issue where dragging from a node's Stress Unit input port into the recipe search dialog yielded `No matching recipes found.` due to missing virtual stress IDs in kinetic generator recipe indices.
  - Fixed duplicated `[kinetic generation]` and `[Create Kinetic]` entries in search dialogs.
  - Filtered out non-kinetic or modpack-disabled/hidden items (such as `Solar Heating Plate` and `Reinforced Motor` in Star Technology) from recipe viewer registration.

## [2.0.0-alpha.9] - 2026-08-26

### Added
- **Native EMI Recipe Transfer (`[+]`) Button & Drag-and-Drop Integration (`BoardEmiRecipeHandler`, `BoardEmiDragDropHandler`, `CalcBoardEmiPlugin`)**:
  - Clicking EMI's native `[+]` button while viewing recipes with the Calculator Board open now directly instantiates and adds the recipe as an active node onto the canvas.
  - Supports dragging recipes directly from EMI onto the board canvas to place new nodes.
- **Multiblock Construction Bill of Materials (BOM) System (`RFC-003`, `MultiblockBOMCalculator`, `MultiblockStructureCatalog`, `MultiblockBOMDialog`, `MultiblockBOMEmiRecipe`, `MultiblockBOMTest`)**:
  - Added dedicated global/per-page BOM calculation dialog opened via hotkey `B` or the `[📦 BOM]` toolbar button.
  - Categorizes structure parts into Casings, Coils, Hatches/Buses, and Controllers with precise stack and remainder formatting (e.g. `3 stacks + 48 (240)`).
  - One-click `[★ Register in EMI]` button to register the aggregated construction parts as a virtual root recipe in EMI's crafting tree for effortless ingredient tracing.
  - Added `[📋 Copy List]` clipboard text export functionality.
  - Real-time dual lower-tier energy hatch toggle (`⚡ 1x Normal Energy Hatch ↔ 2x 1-Tier Lower Energy Hatches`).
- **Energy Hatch & Dynamic Hybrid Hatch Override System (`RFC-004`, `GTEnergyHatchAddon`, `GTHatchAddon`, `EnergyHatchHelper`, `GTHatchHelper`)**:
  - Determines multiblock voltage tier and amperage (1A, 2A, 4A, 16A, etc.) through equipped energy hatch addons with automatic tier overclock ceiling clamping.
  - Dynamically calculates needed I/O bus and hatch counts based on slot capacities (1x, 4x, 9x, 16x) and enforces Distillation Tower single-fluid hatch constraints.
- **Horizontal Node Flip (`RecipeNode`, `NodeCardRenderer`, `BoardHotkeyHandler`, `NodeFlipTest`)**:
  - Hotkey `Alt + F` or card flip button (`[⇄]`) horizontally mirrors input (left) and output (right) slot layout to minimize wire crossings in dense process layouts.
- **Lifecycle Event Bus & Addon Hooks System (`RFC-001`, `RecipeNodeEvent`, `FlowGraphEvent`, `MachineCatalogEvent`, `LifecycleEventBusTest`)**:
  - Standardized Forge event bus hooks notifying external listeners on node lifecycle stages (create, modify, delete, pre/post calculation, and catalog build).
- **GregTech Large Multiblock Steam Boilers & Throttle Control (`GTBoilerTier`, `GTCEuRecipeHandler`, `GTCEuModAdapter`, `MachineConfigDialog`)**:
  - Accurately parses Large Boiler recipes (`gtceu:large_boiler`), calculating baseline steam production (800 mB/t = 16,000 mB/s) and water consumption (5 mB/t = 100 mB/s, 1:160 ratio) for the Large Bronze Boiler.
  - Multi-tier speed scaling for large boilers: L-Bronze 1.0x (800 mB/t), L-Steel 2.25x (1,800 mB/t), L-Titanium 4.0x (3,200 mB/t), and L-Tungstensteel 8.0x (6,400 mB/t).
  - Integrated 25% ~ 100% Throttle slider and preset buttons in `MachineConfigDialog` and node cards, dynamically scaling duration, fuel burn rate, and steam output.
- **Canvas Group Frame Selection Model, Clipboard & Undo/Redo Integration (`BoardSelectionModel`, `CanvasGroupFrameRenderer`, `CanvasInteractionHandler`, `NodeClipboard`, `BoardCommand`, `CanvasGroupFrameTest`)**:
  - Full marquee box selection and Shift/Ctrl multi-selection support for group frames.
  - Comprehensive clipboard support (`Ctrl + C`, `Ctrl + V`, `Ctrl + X`, `Ctrl + D`, `Delete`) for frames and contained nodes, preserving relative layout coordinates upon paste.
  - Complete history tracking (`Undo / Redo`) for frame creation, deletion, movement, and resizing.
  - Prevented duplicate delta application (Double-Delta) when dragging multiple selected frames and nodes.
- **Create: New Age Carbon Brushes Generator Coil & Magnet BOM Aggregation (`MultiblockBOMCalculator`, `CreateNewAgeModAdapter`, `IModAdapter`, `CreateNewAgeTest`)**:
  - Extended `IModAdapter.populateExtraBOMParts` SPI to automatically include 1x `Generator Coil` (`create_new_age:generator_coil`) per Carbon Brushes machine under the `COIL` category in the BOM.
  - Automatically aggregates all installed singleblock and kinetic addons (up to 12 magnets per generator, thermal augments, threading helixes) into the BOM multiplied by machine count.
  - Classified `MAGNET` items under the `COIL` category, properly displaying them in the Coils and All BOM tabs.
- **Muffler & Maintenance Hatch Catalog and BOM Support (`MultiblockStructureCatalog`, `MultiblockBOMCalculator`, `GTCEuModAdapter`)**:
  - Added Muffler Hatches (LV~MAX) and Maintenance / Auto-Maintenance Hatches to the catalog and BOM construction blueprints.
- **Star Technology Sterile Cleaning Maintenance Hatch Support (`StarTAddonCrawler`, `StarTModAdapter`, `MachineAddonTest`)**:
  - Added `start_core:sterile_cleaning_maintenance_hatch` to the maintenance addons catalog with full localization support.

### Changed & Improved
- **Background Data Indexing & Memory Footprint Optimization**:
  - Optimized recipe search token caching and indexing throughput, eliminating frame drops and reducing memory allocation during startup and recipe search.
- **Flow Graph JSON Serialization Payload Optimization (`FlowGraphSerializer`)**:
  - Omitted default field values during graph serialization to significantly minimize blueprint and NBT data size.
- **UI Layout and Toolbar Margin Polish (`BoardScreen`, `ToolbarWidget`)**:
  - Refined toolbar button boundaries and padding, improving Favorites Dock placement and responsiveness.

### Fixed
- **Auto Connect Duplicate Bypass Wire Prevention (`ToolbarWidget`)**:
  - Prevented redundant direct bypass wires from being created between producers and consumers that are already routed through a reroute junction hub.
- **Large Boiler Recipe Double-Acceleration Duration Bug (`GTCEuRecipeHandler`, `GTCEuModAdapter`)**:
  - Fixed an issue where singleblock boiler speed multipliers were redundantly applied to large boiler recipes, distorting cycle duration down to 0.05s (1 tick) and vastly overconsuming fuel.
- **Dialog & Search Text Input 'E' Key / Canvas Hotkey Conflict Bug (`RecipeSearchDialog`, `BoardScreen`, `MultiblockBOMDialog`, `GlobalBalanceDashboardDialog`)**:
  - Fixed a key routing bug where typing letters (such as 'E', 'B', 'T', 'F', 'J') into search boxes or modal input fields triggered Minecraft's inventory close key (E) or board canvas shortcuts instead of typing cleanly into the input box.

---

## [2.0.0-alpha.8] - 2026-08-25

### Added
- **Star Technology & Threading Helix System (`StarTModAdapter`, `StarTAddonCrawler`, `StarTTurbineHelper`, `GTThreadingHelix`, `NodeThreadingConfig`, `MachineConfigDialog`, `NodeCardRenderer`)**:
  - Added dedicated `[🧵 Threading]` sub-tab in `MachineConfigDialog` for all GTCEu multiblock machines supporting Threading (Generalis, Velocitas, Efficienta, Parallelismus, Filum).
  - Live two-way synchronization between the Threading Builder and the top `Active Addons` tray with combined stats badges and quantity indicators (e.g. `8x OpV Weaving Thread Helix`).
  - Added item decoration quantity badges on addon slot icons and node card trays (e.g. `8`).
  - Multi-model Plasma Turbine support: Large Plasma Turbine (LPT, 1x), Supreme Plasma Turbine (SPT, 6x), and Nyinsane Plasma Turbine (NPT, 12x).
  - Star Technology Multiblock Traits: Lubricant Boosting (+25% / +50% EU/t with Tungsten Disulfide) and Coolant Boosting (+75% / +150% EU/t with Superstate Helium 3 / Oganesson Stabilized BEC).
- **GregTech Steam-Era Processing Machinery (LP Bronze / HP Steel) & Direct Steam Consumption Mode (`SteamMode`, `RecipeNode`, `CategoryCapabilityMatrix`, `GTCEuGuiHandler`, `MachineConfigDialog`)**:
  - Added direct steam processing modes (`LP Steam`, `HP Steam`) for all GregTech recipes that support steam processing machinery (Macerators, Compressors, Alloy Smelters, Furnaces, Extractors, Rock Breakers, etc.).
  - **GregTech Steam Physics Ratio**: 1 EU = 2 mB Steam (2 L Steam).
  - **Low Pressure Steam (LP Bronze)**: 2.0x duration (0.5x speed), disconnected from electric EU grid (0 EU/t), activates a BaseEUt * 2 mB/t Steam (`gtceu:steam`) fluid input slot.
  - **High Pressure Steam (HP Steel)**: 1.0x duration (1.0x standard LV speed), disconnected from electric EU grid (0 EU/t), activates a BaseEUt * 2 mB/t Steam (`gtceu:steam`) fluid input slot.
  - **Node Card Controls & Dialog Presets**: Interactive tier button cycling (`[LP Steam] ↔ [HP Steam] ↔ [LV] ↔ ...`) and 1-click preset buttons in `MachineConfigDialog`.
  - **Direct Boiler Wiring & Auto-Ratio**: Seamlessly drag steam wires from Steam Boilers (`gtceu:steam_boiler`) directly to steam machines, fully supporting `Shift + Connect` Auto-Ratio machine scaling.
- **Favorites Interactions & Live Synchronization (`RecipeSearchDialog`, `FavoritesDockWidget`)**:
  - Added favorite star (⭐) toggle button to each recipe search result row and right-click to toggle favorite status.
  - Added right-click on items in the Favorites Dock to instantly remove them from favorites.
  - Enhanced workstation (machine item) favorites to correctly display all processable recipes in the search dialog's `[⭐ Favorites]` filter.

### Changed & Improved
- **GTCEu Modern / Star Technology Architecture Decoupling (`StarTModAdapter`, `ModAdapterRegistry`, `IModAdapter`)**:
  - Cleanly isolated Star Technology and Threading mechanics into `StarTModAdapter` via the `IModAdapter` SPI pattern without hardcoding mod-specific logic into GTCEu core.
- **Massive RAM & GC Optimization for 4GB Low-Memory Environments (`RecipeSearchEngine`, `DynamicAddonCrawler`, `ClientForgeEvents`, `CategoryCapabilityMatrix`)**:
  - Completely refactored `SearchableRecipe` with the Flyweight pattern and `String.intern()`, reducing total recipe search heap allocation from **~320MB down to ~14MB (>95% reduction)** across 80,000+ recipes.
  - Deduplicated item outputs by `Item` and NBT keys (`Set<Item>`) during dynamic addon crawler passes, slashing temporary heap allocations by >98%.
  - Added automatic cache eviction (`clearGlobalCache`, `reset`) and explicit `System.gc()` call on world logout (`ClientPlayerNetworkEvent.LoggingOut`) to prevent `OutOfMemoryError` and data pack reload failures when reloading worlds on 4GB RAM.
  - Eliminated per-frame list copying in `FavoritesDockWidget` and cached `findRecipesForFavorite` lookups to prevent periodic GC stuttering during rendering.
- **Virtual FE Item Cleanup & Grid Energy Refinement (`CreateNewAgeRecipeHandler`, `CreateRecipeHandler`, `CreateNewAgeModAdapter`)**:
  - Removed duplicate virtual `FE` item input/output slots from Generator Coils, Carbon Brushes, and Motors.
  - Recipe item slots now only contain physical resources (Stress Units, items, fluids), while electricity is cleanly aggregated at the grid level on card headers and the summary overlay.
- **Create Encased Fan Fixed Processing Duration (`CreateModAdapter`, `CreateGuiHandler`)**:
  - Aligned Encased Fan bulk processing (Blasting, Splashing/Washing, Smoking, Haunting) with vanilla Create mechanics: processing time remains fixed (default 7.5s) regardless of RPM, as RPM only extends airflow reach/distance and increases SU impact.
  - Added tooltip guidance noting that throughput is increased by placing multiple fans (`Machine Count: N`), not by speeding up RPM.
- **Full GregTech Steam Boiler & Steam Production Support (`GTCEuRecipeHandler`, `GTCEuModAdapter`, `SysteamsModAdapter`)**:
  - Fixed category routing collision where `SysteamsModAdapter` hijacked `gtceu:steam_boiler` recipes.
  - Automatically calculates fuel burn, water consumption (`minecraft:water`), and steam production (`gtceu:steam`) based on official GTCEu ratios (Small Bronze Boiler baseline: 120 L/s = 6 mB/t Steam, 1 mB Water -> 160 mB Steam).
  - Automatically generates empty container outputs (e.g. `minecraft:bucket` from Lava Buckets).
  - Supported speed & throughput scaling across all boiler tiers: High Pressure Steel Boiler (3x), Large Bronze Boiler (8x), Large Steel Boiler (15x), Large Titanium Boiler (26.6x), and Large Tungstensteel Boiler (40x).
- **Removed Recipe Shortcut Key ('A') (`ClientForgeEvents`, `KeyBindings`)**:
  - Removed screen shortcut key recipe addition to eliminate keybinding conflicts with EMI/JEI bookmarks.

### Fixed
- **GTCEu Turbine Rotor Material NBT Persistence & Texture Tint Restoration (`GTRotorAddon`, `MachineAddon`, `TurbineRotorHelper`)**:
  - Fixed turbine rotor material loss and color tint reverting to default Neutronium texture after returning from the Main Menu or reloading worlds.
  - Serialized full `ItemStack` NBT (`GT.PartStats: {Material: ...}`) into `MachineAddon` save tags and added dynamic fallback material reconstruction in `GTRotorAddon.getItemStackSample()`.
- **Plasma Turbine Trait Retrieval & Fluid Name Resolution (`GTCEuAddonCrawler`, `StarTAddonCrawler`, `GTTurbineHelper`, `StarTTurbineHelper`, `IngredientRenderer`)**:
  - Fixed energy type condition in `isTurbine` and relaxed model matching in `isCompatibleStarTTrait`, allowing SPT/NPT boosting traits to properly display in `[📜 Traits]` and `[All]` tabs.
  - Added dynamic namespace resolution (`gtceu`, `start_core`, `gtceu_start`) for boosting fluids, preventing `Fluids.EMPTY` from falling back to `AIR` text and icon.
- **Threading Sub-Tab Label Overlap (`MachineConfigDialog`)**:
  - Shortened sub-tab button labels (`Sup`, `Spd`, `Par`, `Thrd`) to eliminate text clipping and overlap.

---

## [2.0.0-alpha.7] - 2026-08-23

### Added
- **Create: New Age Integration (`CreateNewAgeModAdapter`, `CreateNewAgeGuiHandler`, `CreateNewAgeRecipeHandler`, `CreateNewAgeAddonCrawler`, `CreateMagnetAddon`)**:
  - **Generator Coil & Carbon Brushes Calculations**:
    - Supported equipping up to 12 magnet items with Shift-click batch install.
    - Calculated Generator Coil base stress (`24.0 SU/RPM`) and total stress load ((24.0 + Strength) * RPM).
    - Dynamically retrieved mod config (`suToEnergy`) via runtime reflection for power output (FE/t = Strength * RPM * suToEnergy).
    - Rendered Goggle-style UI header with energy stats (Base Stress, Total Stress, Efficiency, FE/t).
  - **Motors & Motor Extensions Calculations**: Calculated SU output and FE power consumption for motors and extension multipliers.
  - **Kinetic Overstressed Handling (`FlowGraphSolver`)**: Halts nodes (`0.0 efficiency`, `0 FE/t`, `0 SU/s`) when Stress Unit supply is deficient.
- **GTCEu Fusion Reactor Simulation & Start Buffer Aggregation (`RecipeNode`, `FlowGraphSolver`, `SummaryOverlay`, `NodeBadgeRegistry`)**:
  - Extracted recipe `eu_to_start` (ignition requirement) and preserved in NBT.
  - Automatically determined Fusion Tier (Mk1 <= 160M, Mk2 <= 320M, Mk3 > 320M EU) and minimum voltage tier (LuV/ZPM/UV) with target tier clamping.
  - Supported Fusion Reflector addons (T1-T3) and spec integration.
  - Displayed Fusion Start Buffer badge on node card headers.
  - Aggregated per-tier reactor counts and total startup EU (`⚛ Fusion Start Buffer`) in the Summary Overlay with detailed tooltips.
- **Parametric Search Input/Output Prefixes & Prefix Guide Dock (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - `in:`, `input:`, `>` : Input material filter.
  - `out:`, `output:`, `<`, `^` : Output product filter.
  - **Always-Visible Prefix Guide Dock**:
    - Added a docked side panel on the left of the search dialog displaying 8 core prefixes (`@`, `#`, `[`, `>`, `<`, `!`, `|`, `"`).
    - Added Quick Insert feature: Clicking any prefix chip auto-appends it into the search box.
    - Applied responsive auto-centering layout.

### Changed & Improved
- **Addon Deductive Analysis Structure**:
  - Replaced heuristic string parsing with official Java interfaces, deterministic NBT (`AugmentData`), and runtime config lookups.
  - Refactored `DynamicAddonCrawler` and `MachineConfigDialog` polymorphic architecture.
  - Supported equipping up to 12 slots for Thermal Augments.
- **Code Quality & Javadoc Standardization**:
  - Standardized all Javadoc comments to English and cleaned up redundant comments.

---

## [2.0.0-alpha.6] - 2026-08-23

### Added
- **Create Mod Kinetic System & Stress Unit (SU) Scaling (`CreateModAdapter`, `CreateRecipeHandler`, `CreateGuiHandler`)**:
  - Added power generation calculations for Large Water Wheel, Water Wheel, Windmill Bearing, Steam Engine, Hand Crank, Creative Motor, and Electric Motor.
  - Applied proportional scaling for processing duration and stress impact (Base SU * RPM / 32) based on rotation speed relative to 32 RPM.
  - Added `create:stress_units` I/O ports with real-time rate display, wire connections, and Auto-Ratio balancing.
  - Indexed Create processing recipes (Pressing, Crushing, Milling, Mixing, Cutting, Polishing, Rolling, etc.) under the SU consumer search dialog.
  - Added SU/FE conversion and UI controls for Create Crafts & Additions Alternator and Electric Motor.
- **Reroute & Junction Nodes (`RecipeNode`, `FlowGraph`, `FlowGraphSolver`)**:
  - Added 32x32 zero-cost (0 EU/t, 0s) Reroute & Junction Nodes.
  - Automatically bound ingredient specifications based on upstream/downstream connections.
  - Added double-click splitting on wires to insert junction nodes.
  - Supported pass-through flow during Auto-Ratio calculations.
- **Canvas Group Frames & Sticky Notes (`CanvasGroupFrame`, `CanvasStickyNote`, `FrameEditDialog`, `NoteEditDialog`)**:
  - Added 8-color group frames (`CanvasGroupFrame`) supporting header dragging, resizing, and 1-click compound module packaging.
  - Added bounding-box-based frame creation on selected nodes with `Ctrl + G`.
  - Added standalone sticky notes (`CanvasStickyNote`) with auto-wrapped text, color cycling, and double-click editing.
  - Handled automatic containment when dragging nodes in and out of frames.
  - Supported packaging and expanding compound modules containing group frames and notes.
- **Canvas 4-Button Quick Action Marker ([🔍] [🔀] [🖼] [📝]) (`BoardScreen`, `CanvasInteractionHandler`)**:
  - Added quick action marker on empty canvas clicks and wire drops (Recipe Search, Junction Node, Group Frame, Sticky Note).
- **Node Card Controls & Multi-Energy Framework (`EnergyType`, `NodeCardRenderer`, `NodeWidget`)**:
  - Added `EnergyType` domain model (`ELECTRIC_EU`, `ELECTRIC_FE`, `HEAT_OR_SELF`, `KINETIC_SU`, `MANA`).
  - Thermal Boilers (`HEAT_OR_SELF`): Replaced voltage tier and EU/t display with Boiler banner and steam production rates.
  - Thermal Dynamos (`ELECTRIC_FE`): Replaced tier buttons with Dynamo banner and RF/t units, converted to 4 RF = 1 EU for summary calculations.
- **Updated Interactive Tutorial (`TutorialStep`, `TutorialManager`, `TutorialOverlay`)**:
  - Restructured tutorial into a 7-step course covering junction nodes, group frames, quick actions, hardware config, and compound modules.
- **Systeams Runtime Reflection Integration (`SysteamsModAdapter`, `ThermalModAdapter`)**:
  - Queried Systeams configs (`STEAM_RATIO_*`, `SPEED_*`) and recipes at runtime.
  - Categorized Steam Dynamo (`systeams:steam`) as a steam-consuming generator.

### Fixed & Changed
- **Hardware Config Dialog Performance & Synchronization (`DynamicAddonCrawler`, `MachineConfigDialog`)**:
  - Removed synchronous recipe scans when opening the dialog; separated into registry lookup and background NBT queries to eliminate open latency.
  - Updated catalog cache invalidation when removing active addons, clicking Clear All, or right-clicking catalog cards.
- **Dynamo & Boiler Spec Calculation (`ThermalAugmentHelper`, `DynamicAddonCrawler`)**:
  - Calculated specs based on `ThermalFuel`/`SteamFuel` class hierarchy, Forge tags, and KubeJS NBT tags (`DynEA`, `DynP`, `DynEM`).
  - Indexed KubeJS upgrade kits (`kubejs:lv/mv/hv/ev_upgrade_kit`, `arc_kit`, `mci_kit`) while excluding disabled dummy items.
- **Contextual Search Filtering (`RecipeSearchDialog`)**:
  - Fixed an issue where searching machine categories (`[chemical reactor]`) in Producer/Consumer dialogs returned unmatched recipes.
- **Singleblock Machine Config Interaction (`MachineConfigDialog`)**:
  - Fixed click handling for augment kit cards on singleblock thermal machines and boilers.
- **Dynamo & Boiler Tooltips (`BoardTooltipRenderer`)**:
  - Corrected dynamo hover tooltip from consumption to generation (`Total Power Generation: +400.00 RF/t`).
  - Added steam production rate (`Total Steam: +30,000.00 mB/s`) to boiler hover tooltips.
- **Compound Module Power Stats Display (`NodeCardRenderer`)**:
  - Fixed rendering condition causing net power generation/consumption stats (`+320.00 EU/t (Gen)`, etc.) to be omitted from row 2 of compound module cards.

---

## [2.0.0-alpha.5] - 2026-08-22

### Added
- **EMI Default Recipe Prioritization & Badge (`FavoritesDockWidget`, `RecipeSearchDialog`)**:
  - Prioritized user-set EMI Default Recipes (`Ctrl + Left Click`) at the top of favorites sub-panels and search results.
  - Added star (`★`) badge and highlight border to Default Recipes.
- **Category Query with Spaces Support (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - Added support for bracketed category queries containing spaces, such as `[gas turbine]` and `[large chemical reactor]`.
  - Improved ranking for exact multi-word category matches without brackets.
- **Board Viewer Presence & Tooltip (`TeamPresenceTracker`, `WorkspaceTabBarWidget`)**:
  - Displays count of teammates actively viewing the board with a hover tooltip listing player names and open page tabs.
  - Updates presence state when toggling between Personal Board and Team Board tabs.

### Fixed & Changed
- **Multiplayer Team Workspace Isolation & LAN Support (`FTBTeamsProvider`, `ClientWorkspaceState`)**:
  - Isolated team UUIDs for FTB Teams parties and solo players to prevent cross-team workspace access.
  - Added collaboration synchronization support for LAN published worlds.
- **Lock Badge Sync & Player Nickname Display (`WorkspaceTabBarWidget`, `BoardScreen`)**:
  - Updated lock status badge to dynamically track the active page tab.
  - Displays in-game player names (`Locked by <Name>`) instead of raw UUID substrings in lock badges and toasts.
- **Team Page Deletion Permission Check (`FTBTeamsProvider`, `TeamProviderRegistry`)**:
  - Added server-side permission checks verifying FTB Teams ranks (`OWNER`, `OFFICER`) and LAN host status before deleting team pages.
- **Favorites Dock EMI Loading Decoupling (`FavoritesDockWidget`, `RecipeSearchDialog`)**:
  - Decoupled Favorites Dock loading from full search cache indexing for faster rendering once EMI finishes loading.
  - Simplified recipe caching flow using standard EMI APIs.
- **Header Pass-Through & Tooltip Placement (`CanvasInteractionHandler`, `WorkspaceTabBarWidget`)**:
  - Allowed canvas drag and click interactions to pass through empty regions of the top header and toolbar.
  - Fixed Unicode font rendering glitch (`\uFE0F`) and adjusted top-bar tooltip positioning to prevent screen clipping.
- **Search Freeze on 0 Results (`RecipeSearchDialog`)**:
  - Fixed an issue where queries with 0 results triggered repeated searches during rendering.
  - Isolated non-thread-safe calls from parallel streams and added search debouncing.
- **Cleaned Up EMI Overlay Handler (`CalcBoardEmiOverlayHandler`)**:
  - Removed unused reflection-based EMI overlay handler class.

---

## [2.0.0-alpha.4] - 2026-08-21

### Added
- **Favorites Dock Panel (`FavoritesDockWidget`, `BoardScreen`)**:
  - Added a collapsible panel on the left of the board screen to place favorited items onto the canvas via single-click or drag-and-drop.
- **Favorite Recipe Navigation & EMI Preview Integration (`FavoritesDockWidget`, `RecipeHoverPreviewRenderer`)**:
  - Displays producing recipes and EMI preview cards upon hovering over favorites, supporting slot hover tooltips and R/U key lookups.
  - Adjusted hover hitboxes between panels for smoother cursor transitions.
- **Multi-Phase Loading State & Progress Bar (`MachineConfigDialog`, `RecipeSearchDialog`, `FavoritesDockWidget`)**:
  - Added multi-phase progress text and progress bars to machine hardware config, recipe search, and favorites dock during background indexing.
- **Bracket Category Search (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - Supports filtering recipes by category or machine using bracket syntax (`[smelting]`, `[pyro]`, `%smelting`).
- **Recipe Category Filter Integration (`FavoritesDockWidget`, `RecipeFilterConfig`)**:
  - Excluded categories in `RecipeFilterDialog` are automatically omitted from favorite recipe exploration and automatic node placement.

### Fixed & Changed
- **GTCEu High-Tier Coil Stats Calculation (`CoilHelper`, `MachineAddon`)**:
  - Corrected formula calculations for high-tier and custom coils (e.g. `Abyssal Alloy Coil` Cracker discount `0.07x`, Pyrolyse speed, LCR, and Multi Smelter parallel).
  - Prioritized `ICoilType.getTier()` to prevent tier calculation discrepancies.
- **Recipe Search Performance Optimization (`RecipeSearchDialog`, `RecipeSearchEngine`)**:
  - Removed unnecessary list copying and applied parallel stream filtering during search to improve query response time.
- **Search Result Relevance & Ranking Improvements (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - Updated scoring logic to prioritize name and category matches when a query is provided.
- **EMI Recipe Screen `[+]` Button Integration (`CalcBoardEmiOverlayHandler`)**:
  - Clicking EMI's `[+]` button or pressing the hotkey now adds the recipe and switches to the board screen.
- **`BoardScreen` Class Hierarchy Fix (`BoardScreen`, `BoardContainerMenu`)**:
  - Restored `Screen` inheritance to prevent inventory-related sidebar overlays from rendering over the board.
- **Language File Cleanup (`en_us.json`, `ko_kr.json`)**:
  - Removed duplicate keys and synchronized resource entries between English and Korean language files.

---

## [2.0.0-alpha.3] - 2026-08-21

### Added
- **Category Capability Matrix Engine (`CategoryCapabilityMatrix`, `CategoryCapability`)**:
  - Implemented pre-baked O(1) capability lookup matrix mapping recipe categories directly to machine workstations and supported addon categories.
  - Added deterministic capability deduction for Heating Coils, Turbine Rotors, Parallel Hatches, Maintenance Hatches, and Thermal Series Augments.
  - Added dedicated unit test suite (`CategoryCapabilityMatrixTest`) covering singleblock, multiblock, turbine, dynamo, and filtrator categories.
- **Deductive Multiblock Capability Mapping (`MultiblockDetector`, `EmiRecipeConverter`)**:
  - Automatically deduce coil and turbine capabilities for complex multiblocks (Large Chemical Reactor, Pyrolyse Oven, EBF, Cracker, etc.) by analyzing workstation structures from `multiblock_info`.
  - Dynamically synchronize deduced categories with `MultiblockDetector.registerCoilCategory` and `registerTurbineCategory`.
- **Thermal Machine Upgrade Kit Replacement & 3-Slot Augment Stacking (`RecipeNode`, `MachineConfigDialog`, `ThermalAugmentHelper`)**:
  - Implemented 1-Kit-only rule for Thermal Upgrade Kits (LV~EV, 6x~48x parallel scale) with automatic replacement upon selecting a new tier.
  - Added support for stacking identical regular augments (ARC, MCI, etc.) across up to 3 slots (e.g. 3x EV MCI = 4.096x fuel energy) using pure NBT float tags (`AugmentData.Type: Dynamo_Fuel`).
  - Added catalog card badges (`✔ x2`, `✔ x3`, `3/3`), Left-Click (+1) / Right-Click (-1), and single-instance removal from top active slot bar.

### Changed
- **Elimination of Heuristic Addon Deduction (Rule 5 Addon Spec Deductive Analysis Policy)**:
  - Completely purged all substring checks (`contains("rotor")`, `contains("coil")`, `id.getPath()`), tooltip string parsing, and item display name heuristics across the entire codebase.
  - Migrated all addon extraction to deterministic official tags (`gtceu:circuits/*`, `thermal:upgrade_kit`), exact NBT float/int structures (`AugmentData`), and runtime reflection (`CoilHelper`, `TurbineRotorHelper`, `ParallelHelper`).
- **Responsive Dialog Loading Overlays & Live State Updates (`MachineConfigDialog`, `RecipeSearchDialog`)**:
  - Added clean section-level loading overlays with animated indicators while background indexing is in progress.
  - Implemented reactive live updates: as soon as background baking completes, the `[♨ Coil]`, `[⚡ Parallel]`, and `[🔧 Maint]` chips auto-populate seamlessly without modal freezing or needing to reopen dialogs.
- **Massive Recipe Search Indexing Speedup (250x faster, 2m -> <1s) (`RecipeSearchDialog`, `RecipeSearchEngine`)**:
  - Eliminated O(N^2) category loops in EMI recipe caching using multi-core `parallelStream`, indexing 170,000+ recipes in under 1 second.
  - Streamlined fluid stack detection using direct `FluidEmiStack` instance checks.
- **Dynamic Addon Crawler 20ms Direct Extraction & Disabled Item Guard (`DynamicAddonCrawler`)**:
  - Streamlined output collection directly from `EmiRecipe.getOutputs()` with preserved NBT, finishing in under 20ms.
  - Removed blind Forge item registry scans for Thermal augments, completely eliminating disabled dummy items (`thermal_extra`, etc.) from catalog leaks.

### Fixed
- **Large Chemical Reactor & Multiblock Missing Coil Capabilities (`CategoryCapabilityMatrix`, `MultiblockDetector`)**:
  - Fixed issue where Large Chemical Reactor and other multiblocks failed to expose the Coil tab due to disjoint singleblock vs. multiblock recipe categories in GTCEu.
- **EMI & RecipeManager Async Lifecycle Synchronization (`CalcBoardEmiPlugin`, `ClientForgeEvents`, `RecipeSearchDialog`)**:
  - Implemented background async retry polling (200ms intervals) on world load, guaranteeing matrix pre-baking starts the moment EMI finishes worker thread registration.
  - Eliminated premature `bake(null)` wipeout bug where empty capabilities cleared matrix state.

---

## [2.0.0-alpha.2] - 2026-08-20

### Added
- **Interactive 6-Step Tutorial & Curated Dummy Recipes (`TutorialManager`, `RecipeSearchDialog`)**:
  - Re-architected interactive onboarding tutorial into 6 clear steps: Step 1 (Add Boiler), Step 2 (Drag-to-Search Turbine & Auto-Wire), Step 3 (Sever Wire), Step 4 (Shift-Drag 1:1 Auto-Ratio), Step 5 (Process Summary & Compound Module `Ctrl+G`), Step 6 (Completion).
  - Dedicated tutorial workspace isolation (`🎓 Tutorial`) and instant 0ms curated dummy recipes (`Steam Turbine (Tutorial)`, `Boiler (Tutorial)`, `Steam Engine (Tutorial)`) ensuring zero-lag and clutter-free onboarding.
- **Type-Strict Stack Indexing & Prioritized Search Ranking (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - Divided searchable stacks into strict fluid vs. item registry indexing to prevent cross-type query pollution.
  - Prioritized exact fluid ID matches (+1,000 pts) and generator/multiblock categories over generic crafting table items.

### Changed
- **Shortcut & Documentation Realignment (`GuideDialog`, `en_us.json`, `ko_kr.json`, `README.md`, `README_KR.md`)**:
  - Replaced legacy toolbar button references with `Ctrl + G` for modular group packaging across all in-game guides and tutorials.
  - Comprehensive documentation overhaul with complete feature coverage, dynamic addon crawler details, and matter-of-fact technical tone.

### Fixed
- **Tutorial Step 2 Auto-Wire Transition (`RecipeSearchDialog`)**:
  - Resolved an issue where auto-wiring nodes spawned from the search dialog failed to notify `TutorialManager.onWireConnected()`, preventing advancement to Step 3.
  - Added immediate canvas widget rebuilding upon auto-wire completion.

---

## [2.0.0-alpha.1] - 2026-08-20

### Added
- **Multiplayer Shared Team Workspace Architecture**:
  - **Full Client-Server Synchronization**: Introduced bidirectional network protocol (`C2S`/`S2C`) enabling real-time collaboration on shared factory design flowcharts across multiplayer servers.
  - **Pluggable Multi-Team Provider Backend (`ITeamProvider`, `TeamProviderRegistry`)**:
    - Full soft-dependency integration with **FTB Teams** via safe runtime reflection.
    - Automatic fallback support for **Vanilla Scoreboard Teams** and standalone world workspaces.
  - **Granular Per-Page Edit Locks & Heartbeat (`WorkspaceLockManager`, `S2CLockResultPacket`)**:
    - Automatic edit lock acquisition upon canvas/machine interactions to prevent concurrent editing collisions.
    - Locks automatically release upon switching page tabs, closing the GUI, or after timeout expiration.
  - **Live Teammate Presence & Cursor Tracking (`S2CBroadcastPresencePacket`)**:
    - Real-time display of active teammates, their currently viewed page tabs, and presence indicators.
  - **Seamless Frictionless Auto-Sync (Google Docs/Figma Style)**:
    - 3-second inactivity debounced auto-saving combined with immediate commit on page navigation or screen exit.
    - Server-side commit squashing in `TeamWorkspaceData` to keep history logs concise and clean.
  - **Save History & Personal Forking (`RecentSavesDialog`)**:
    - View revision history logs and fork/restore past team revisions directly into personal board tabs with full node and connection graph cloning.
  - **Administrative Page Deletion Protection (`C2SDeleteTeamPagePacket`)**:
    - Deletion of team pages restricted to **Team Owners, Officers, and Server Admins (OPs)**.
    - Protected last remaining page from deletion to ensure design integrity.
- **Singleplayer Game Pause Toggle (`BoardScreen`, `BoardManager`, `ToolbarWidget`)**:
  - Added an interactive `[⏸ Pause: ON]` / `[▶ Pause: OFF]` toggle button on the singleplayer toolbar (`isPauseScreen` dynamic integration).
  - Allows players to freely choose between pausing the world while calculating complex lines or letting factory automation run in background.
- **Advanced Parametric & Boolean Recipe Search Engine (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - Implemented an EMI/JEI-style boolean and parametric search engine supporting multi-token AND (`&` / spaces), OR (`|`), NOT (`!`), Mod ID (`@gtceu`), Tags (`#logs`), Category/Machine (`[pyrolyse_oven]`), and Quoted phrases (`"..."`).
  - Full indexing of machine display names, localized categories, inputs, outputs, and registry IDs.
- **Contextual Drag-to-Search & Auto-Wire Node Creation (`CanvasInteractionHandler`, `RecipeSearchDialog`)**:
  - Dragging a wire from any port into empty canvas space automatically opens the recipe search dialog pre-filtered for consumers (output drag) or producers (input drag).
  - Spawns the new machine node at the cursor position and **automatically wires the ports together** in a single seamless action.
  - Supports `Shift + Drag` for instant 1:1 auto-ratio matching upon creation.

### Changed
- **Adaptive Compact Header Layout (`BoardScreen`, `WorkspaceTabBarWidget`, `PageTabBarWidget`)**:
  - Automatically hides team collaboration top bars in singleplayer or non-modded servers to preserve maximum canvas workspace.
  - Cleaned up redundant manual save buttons in favor of seamless background auto-sync.
- **Silent Background Collaboration Sync**:
  - Removed intrusive toast notifications during routine background syncs and lock state transitions while keeping critical conflict alerts.

### Fixed
- **i18n & Modal Formatting Parity**:
  - Completed 100% key parity and formatting token alignment across `en_us.json` and `ko_kr.json` (340 translation keys).

---

## [1.0.6] - 2026-08-20

### Added
- **Global Multi-Page Balance Dashboard (`GlobalBalanceDashboardDialog`, `GlobalBalanceAggregator`)**:
  - Added an interactive modal dashboard to aggregate net material production, consumption, and global EU/t power balance across multiple preset pages (`B` hotkey and `[📊 Global Balance]` toolbar button).
  - **Page Selection Sidebar**: Select or deselect specific pages with instant re-aggregation to evaluate segmented production lines.
  - **Flow Tab Filtering & Search**: Categorize flow items into Deficits, Surplus, and Balanced tabs with real-time search filtering.
  - **Per-Item Page Contribution Drilldown (`ItemContributionPopup`)**: Click any material row to inspect exact per-page production and consumption breakdowns.
  - **Global Power Balance Bar**: Displays net EU/t generation/consumption, highest active voltage tier badge, and comprehensive tooltips.

### Changed
- **Supply / Demand Rate Notation Polish (`FormatUtil`, `NodeCardRenderer`)**:
  - Replaced ambiguous double-slash formatting with clean plus/minus color-coded rate notation (`+3.48M -3.2M/s +` for surplus, `+2.5M -3.2M/s ⚠` for deficit).
  - Expanded default node card width from `235px` to `245px` for optimal typography fit.
- **Official GTCEu Modern & Star Technology Fork Variant Switching (`build.gradle`, `gradle.properties`)**:
  - Introduced `gtceu_variant` configuration property in `gradle.properties` to seamlessly switch between official GTCEu Modern (CurseMaven) and Star Technology Fork (FlatDir) dependencies.

### Fixed
- **Global Balance Material Row Text Overlap (`GlobalBalanceDashboardDialog`)**:
  - Replaced hardcoded text coordinates with a strictly chained right-to-left dynamic layout to prevent rate strings and flow detail numbers from colliding.
  - Expanded dashboard width to `580px` and formatted massive machine counts (e.g. 581M) with compact SI notation.

---

## [1.0.5] - 2026-08-20

### Added
- **Level of Detail (LOD) Rendering for Large Node Charts (`NodeCardRenderer`, `BoardScreen`)**:
  - Implemented automatic LOD mode switching when zoomed out (`zoom < 0.28`), rendering lightweight 2D card blocks and omitting intensive 3D item models and text to maintain high framerates on massive flowcharts with 1,200+ nodes.
  - Aligned LOD input/output port attachment points with pixel-perfect precision to prevent visual wire misalignment during zoom transitions.
- **Single-Batch GPU Wire & Pulse Rendering (`ConnectionRenderer`)**:
  - Batched all Bezier curve pipeline wires and animated flow pulse dots into single OpenGL `QUADS` vertex draw calls.

### Fixed
- **3D Item Model Z-Index Ghosting & Layer Leaks (`BoardScreen`, `NodeCardRenderer`)**:
  - Fixed an issue where Minecraft global 3D render buffer queues bled through foreground GUI cards and overlay modals by adding per-layer buffer flushing and depth buffer clearing.
- **World Load `Cannot retrieve all materials before registration` Crash (`DynamicAddonCrawler`)**:
  - Eliminated unsafe reflective method invocations on `MaterialRegistryManager` that triggered an `IllegalStateException` during world entry / pack reload phases.
- **Guidebook Sidebar Tab Text Truncation (`GuideDialog`, `en_us.json`, `ko_kr.json`)**:
  - Expanded the guidebook modal width to 500px and sidebar width to 145px, and refined category tab titles to prevent text clipping.

### Changed
- **Documentation & Localization Polish (`README.md`, `README_KR.md`, `en_us.json`, `ko_kr.json`)**:
  - Refined documentation and in-game guide descriptions into concise, clear, and technical language.

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
  - Fully integrated with GTCEu's bytecode formula (V_max = V_holder * P_rotor / 100.0).
  - Selecting a rotor or changing voltage tier (`EV`, `IV`, `LuV`, `ZPM`, etc.) instantly derives and applies the optimal rated parallel count (Par) and power generation (+EU/t).
  - *Example: Nitrobenzene 32 EU/t + Scheelite Rotor (450% Power) + IV Rotor Holder -> **1,152x Par, +36,864.0 EU/t, 4.00s cycle, 288.0 mB/s consumption**.*
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
  - Auto Ratio and Shift+Drag calculations now round all machine counts up to integers (1, 2, 3...).
  - Guarantees Supply >= Demand across the entire chain to prevent upstream fuel/raw material starvation.
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
