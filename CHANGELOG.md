# Changelog

<p align="center">
  <b>English</b> | <a href="CHANGELOG_KR.md">한국어</a>
</p>

> **Archived Versions**:
> - [v2.1.x Changelog](docs/changelogs/CHANGELOG_v2.1.md)
> - [v2.0.x Changelog](docs/changelogs/CHANGELOG_v2.0.md)
> - [v1.0.x Changelog](docs/changelogs/CHANGELOG_v1.0.md)

## [Unreleased]

## [2.2.0] - 2026-09-12

### Fixed
- Fixed an issue where external recipe viewer (EMI) hotkeys intercepted text typing while the recipe search dialog is open, and prevented duplicate background caching calls during recipe reloading.
- Fixed an issue where the Throughput Boosting multiblock trait displayed as "Pyrolyse Oven" in the machine configuration dialog and cards.
- Fixed an issue where fractional power multipliers were rounded incorrectly on addon badges, and resolved an issue where Throughput Boosting's 4x parallel was mistakenly treated as extra power draw, preventing overclocking and causing excessive processing durations.
- Fixed an issue where single-energy-hatch multiblock machines (such as Rock Filtrator) could be equipped with multiple energy hatches to trigger voltage tier-skip overclocking.
- Fixed an issue where connecting a junction node in a recirculation loop could trigger false loop warnings, freeze machine rates, or cause machine counts to explode during auto-ratio calculation.

## [2.2.0-beta.4] - 2026-09-11

### Fixed
- Fixed an issue in multiplayer team workspaces where page edit permissions prematurely released after a few seconds of idle time while keeping the calculation board or configuration dialogs open, and added interactive click-to-acquire and click-to-release toggles to the top-bar lock badge.
- Fixed an issue where items marked as prepared in the Multiblock Construction Bill of Materials (BOM) checklist were still included as required materials in clipboard exports and EMI/JEI recipe goals, and updated the dialog banner and export list to cleanly separate prepared and remaining materials.
- Fixed an issue where modifying machine hardware and addons from the right inspector panel did not synchronize changes to the canvas graph, and ensured opening configuration dialogs immediately acquires page editing lock ("Editing by Me") in multiplayer team workspaces.
- Fixed an issue where the Multiblock Construction Bill of Materials (BOM) dialog did not include shared team workspace pages in the included pages list or calculations when collaborating in team workspaces.
- Fixed a crash when clicking team workspace tabs or switching workspaces on multiplayer servers.
- Fixed an issue in multiplayer team workspaces where players could become locked out from editing with a false lock badge showing their own name, and prevented revision conflict errors during page auto-saving.
- Fixed an issue where connected input ports in deficit incorrectly displayed throttled consumption rates instead of the nominal demand rate on machine cards.
- Fixed an issue where multi-resource recirculating loops with net decay failed to converge to steady-state and caused calculation freezes.
- Fixed an issue where multi-step recirculation loops receiving external raw materials incorrectly marked intermediate product ports as unfed damped loops and shut down machine operations.
- Fixed an issue where fractional auto-ratio scaling from a multi-product anchor machine over-scaled downstream machines to the largest output instead of respecting the limiting bottleneck.
- Improved auto-ratio optimization on recirculation loops with external feed to properly converge machine counts to loop throughput limits.

## [2.2.0-beta.3] - 2026-09-10

### Added
- Added warning badges ([⚠ Damped]) and detailed port notices for unfed damped recirculation loops, clearly alerting players when circulating processes decay to zero throughput due to missing external supplementary feed lines.
- Added the input/output direction flip option to the junction node right-click context menu.
- Added Weighted split mode to junction nodes, allowing players to assign custom weight ratios to outgoing lines with full support for fixed flow caps and priority tiering.
- Added in-place folding for Shared Machine Pools, allowing players to collapse multi-recipe shared machines into a single compact machine card without removing internal nodes, preserving internal recipe ratios while scaling machine count and providing clear deficit warnings when upstream supply is insufficient.
- Upgraded compound process modules to use dedicated 1:1 sub-pages with boundary input/output pins, allowing players to double-click modules to inspect and edit internal layouts, navigate back smoothly via breadcrumbs or the Escape key, and keep module sub-pages organized in a dedicated explorer section.
- Added steady-state recirculation tracking and one-click scaling for damped recycling loops, displaying a cyan circulating indicator instead of a false deficit warning when machines are stably operating on external supply, with detailed multi-flow tooltips and one-click machine scaling to steady-state capacity.

### Changed
- Overhauled boundary pins in module subpages into compact pins displaying ingredient icons alongside direction and flow rate badges, adding hover quick-deletion, inline renaming, right-click context menu actions, and a dedicated pin inspector panel.
- Improved the junction external supply dialog to display the active time unit (e.g. mB/t, mB/s) beside the input field, allowing players to enter numbers directly in their current view unit or specify explicit units (e.g. 2B, 2B/s, 100mB/t).
- Optimized background catalog access and machine recipe loading for improved interface responsiveness and stability during large process calculations.

### Fixed
- Fixed an issue where undoing a module collapse caused duplicate sub-pages to accumulate in the process modules list, and ensured sub-page lifecycle is cleanly restored upon redo.
- Fixed an issue where energy hatch addons could not be equipped on custom electric multiblocks (such as Star Technology's Molten Destabiliser and Ore Processing Plant) when selective hatch slots were used in their structure definitions.
- Fixed an issue where switching a turbine generator to a Large Plasma Turbine retained inflated parallel tiers from previous models, causing fluid fuel consumption to be multiplied excessively.
- Fixed an issue where recipes requiring ULV power incorrectly registered a non-existent ULV electric machine and displayed an empty icon, ensuring electric machines start at their true minimum tier (LV) while preserving steam mode transitions.
- Fixed an issue where multiblock machines equipped with lower-tier energy hatches still executed higher-tier recipes with excessive amperage, ensuring insufficient energy hatch or machine voltage halts operation with clear deficit warnings until an adequate hatch is equipped.
- Fixed an issue where the team workspace tab and multiplayer collaboration features were not displayed on singleplayer LAN hosts or when joining a party in-game, ensuring team workspaces synchronize in real time upon party creation or member joining.
- Fixed an issue where closed-loop processes with machine capacity mismatches on intermediate products incorrectly converged to 0% operating efficiency and caused deficit warnings.
- Fixed an issue where the machine icon in the header rendered incorrectly as a missing texture when folding a Shared Machine Pool frame.
- Fixed an issue where context menu items for group frames displayed unlocalized translation keys and overlapped with shortcut labels, properly synchronizing localization keys across all 4 languages and dynamically adjusting menu width.
- Fixed an issue where the target pool machine input in the group frame configuration window could not receive keyboard focus or overlapped with its label, allowing smooth mouse selection and Tab navigation between input fields.
- Fixed an issue where ports on folded Shared Machine Pool cards could not be clicked or connected with wires, and ensured internal machines are cleanly excluded from marquee selection, highlight boxes, and hover tooltips while folded.
- Fixed an issue where compressing a process group containing intermediate junction nodes into a module caused junction nodes to be treated as physical machines, creating phantom input/output ports and distorting the process summary net balance.
- Fixed an issue where compressing machines that receive both internal circulation and external supply (e.g. from a junction) into a module caused the external demand to be over-allocated to the full recipe rate, reducing module operating efficiency and creating unnecessary surplus output ports.

## [2.2.0-beta.2] - 2026-09-09

### Added
- Simplified junction node splitting to Proportional and Equal (1/N) modes while unifying priority tiering across all connection wires, satisfying higher-priority lines first and distributing flow within each tier according to the node's split mode.
- Added connection wire priority adjustment via mouse wheel scroll on canvas wires or the junction port list, complete with visual priority badges and informative wire tooltips.

### Fixed
- Fixed an issue where the crafting plan duration (ETA) banner did not appear in the AE2 autocrafting confirmation screen for linked pages, and streamlined page generation to the Shift+A shortcut while restoring pattern creation hint tooltips.
- Fixed an issue where inactive machines only displayed a generic requirement warning, now explicitly listing all unsatisfied operating conditions (such as missing energy hatches, insufficient coil temperature, reflector tier, or slot deficits) on the card title, machine icon, and configuration tooltips.
- Fixed an issue where large turbine generators returned to a singleblock turbine lost their generator status upon uninstalling rotor addons.
- Fixed an issue where ULV tier processing recipes transitioning from steam mode were improperly clamped up to LV.
- Fixed an issue where singleblock combustion generators could be misconfigured as multiblock engines, improperly displaying oxygen and coolant boost options.
- Improved dedicated server stability when calculating recipes for external mod machines without client dependencies.
- Fixed an issue where the Bill of Materials (BOM) failed to reflect the tank size and required steam engine count for Create Steam Boilers, properly listing the configured Fluid Tank blocks and engines based on boiler operating level.
- Fixed an issue where electric multiblock machines without an energy hatch incorrectly operated with unlimited power at the recipe's base voltage tier, and ensured missing energy hatches trigger an inactive status warning until equipped.
- Fixed an issue where multiblock machines equipped with an energy hatch still allowed changing the voltage tier via scrolling or the inspector panel, properly locking the machine's voltage tier to the installed energy hatch.
- Fixed an issue where unconnected input and output ports sharing the same resource (such as heating fluids or catalysts) disappeared from the module card when collapsing a group of machines into a compound module.
- Fixed an issue where output flow allocation values set on junction nodes were reset to 0 when grouping machines into a module or expanding them back.
- Fixed an issue where Create: New Age generator coils and motors did not update Stress Unit (SU) consumption and generation when changing machine count.
- Fixed an issue where inactive machines failing operational requirements (such as missing energy hatches or insufficient coil temperatures) continued to display output flows to downstream lines, properly cutting off output flow to 0.
- Fixed an issue where hovering over turbine generator cards caused unnecessary continuous recalculation of rotor wear and tier parameters on every render frame.
- Fixed an issue where individual machine scales within compound modules were improperly altered when expanding and collapsing modules.
- Fixed unnecessary full process recalculation and text re-rendering when dragging, resizing, or changing colors of sticky notes and decorative group frames.
- Improved responsiveness and eliminated stutter when navigating the canvas with keyboard panning (WASD) while recipe viewers (JEI) are active.
- Improved auto-connect performance when wiring large process lines with numerous nodes and connection wires.
- Optimized opening of the Global Balance Dashboard by reusing existing calculations for inactive pages.

## [2.2.0-beta.1] - 2026-09-08

### Added
- Added sail count (8–128) configuration for the Create Windmill Bearing in the machine setup menu, calculating exact operating speed (1–16 RPM) and rotational stress generation (512–8,192 SU) based on the physical sail assembly.
- Categorized rotational machines and energy converters into 4 distinct groups (Kinetic Sources, Fuel & Steam Engines, Electric Motors, and Kinetic Alternators) for cleaner recipe browsing.
- Added independent configuration for Create Steam Boiler tank size (4–72 blocks), heat level (0–18), and water supply (10–180 mB/t) with real-time bottleneck indicators, quick presets, and a one-click water auto-match button.
- Added a quick config button (`[⚙]`) on Create Steam Boiler node cards, opening the dedicated boiler hardware setup panel directly for tank size, water supply, and blaze burner management.
- Added Heated Blaze Burner (+1 level) and Superheated Blaze Burner (+2 levels) hardware addons to the boiler configuration, automatically synchronizing boiler level, fluid inputs, and kinetic stress capacity.
- Added the ability to drag and click the scrollbar in the Add Recipe dialog, allowing smooth navigation through recipe search results using the mouse.
- Added a unit preference preservation toggle in the board settings menu, ensuring selected rate time units (such as per second or per minute) and fluid display units are remembered across game restarts and between world sessions.
- Improved the Smart Auto-Connect feature to automatically wire matching resources in recirculation and feedback loops, allowing setups like Nether Star production and catalyst recycling to be connected with a single click.
- Added an "Add Sticky Note" option to the canvas right-click context menu (and shortcut 'N') to quickly place memo notes at the mouse cursor position.

### Fixed
- Fixed significant frame drops and stuttering when viewing boards with numerous machine nodes by optimizing node card and ingredient rendering.
- Fixed an issue where machine nodes or selection marquees disappeared when working on large boards with dozens of nodes.
- Fixed an issue where connections wired between alternative or tag-compatible resources and newly created reroute nodes were unintentionally deleted when opening the board or running solvers.
- Fixed a potential crash caused by circular or deeply nested module references during material list (BOM) calculation and power summary aggregation.
- Fixed an issue where kinetic generators (such as the Large Water Wheel) appeared twice in the recipe search dialog by decoupling internal generator definitions from external recipe viewers.
- Cleaned up external recipe viewer (EMI) registries by moving kinetic generators into the board's native machine catalog, keeping viewer searches pristine without synthetic recipe pollution.
- Fixed an issue where Create stress units (SU) scaled with the active time unit (such as per-minute or per-hour), ensuring rotational stress and capacity consistently display as a constant SU value across all time settings.
- Fixed an issue where required recipe fluid inputs (such as Liquid Oxygen) were unintentionally removed when switching machine models (such as singleblock to multiblock) on processing machines.
- Fixed an issue where the Distillation Tower was incorrectly recognized as a turbine generator, preventing turbine-specific rotor slots from appearing on distillation processing setups.
- Fixed an issue where the parallel hatch button and addon category appeared on multiblocks that do not support parallel hatches (such as the Distillation Tower) when sharing recipe categories with parallel-capable machines.
- Fixed an issue where multiblock machines equipped with standard 2A energy hatches incorrectly allowed additional parallels for recipes consuming near 1A, and ensured parallel limits are calculated prior to coil and energy discounts.
- Fixed an issue where running Auto Ratio on a loop setup with a fixed-rate junction anchor incorrectly reset all machine counts to 0.01 or 1, and ensured physically infeasible deficit loops trigger proper divergence warnings.
- Fixed an issue where typing 'W', 'A', 'S', or 'D' while renaming a page tab or searching for recipes in external recipe viewers caused the canvas to unintentionally pan.
- Fixed an issue where the machine card header title was not updated when switching the node to another machine or controller block.
- Fixed an issue where text entered in the sticky note editing dialog was not visible when using custom board GUI scale settings.

## [2.2.0-alpha.4] - 2026-09-08

### Added
- **Contextual Junction Buffer Wiring & Flow Rate Anchoring**:
  - Added quick-add flyout submenus when dragging wires from ports into empty canvas space to create surplus drain, deficit supply, void sink, or infinite supply junction nodes with exact calculated rates in one click.
  - Added the ability to pin external supply or fixed drain junction nodes as reference Anchors via right-click, automatically scaling upstream or downstream machine counts to match the target flow rate.
  - Added a one-click match button (`[⚡]`) in the junction configuration dialog to automatically fill the rate with the total connected inflow or downstream demand.
- **Auto-Ratio Recirculation Loop Runaway Warning Badges & Interactive Guidance**:
  - Displays an amber warning badge (`[⚠ Loop]`) on machines in closed loops where automatic scaling was suppressed due to missing external ingredient supplies, preventing runaway machine count calculations.
  - Hovering over the warning badge displays clear explanations and recommended actions, with a one-click shortcut to pin the machine as an Anchor.
- **Contextual Process Instability Warnings & Diagnostic Guidance**:
  - Added dedicated warning badges for positive feedback growth loops (`[⚠ Growth]`), catalyst decay loops (`[⚠ Catalyst]`), conflicting multiple anchors (`[⚠ Conflict]`), and extreme micro-yield recipes (`[⚠ Yield]`).
  - Provides customized 5-line diagnostic tooltips and one-click actions (such as unpinning conflicting anchors or fixing operating scale) to help troubleshoot and balance complex automated setups.
- **Shared Machine Pool Capacity-Driven Auto-Ratio**:
  - Added an auto-ratio button (`[⚖]`) to shared machine pool frame headers to proportionally scale all connected processes to match target physical machine capacity (default 1.0x).
  - Supports fractional precision scaling on regular click and integer ceiling scaling when holding [Alt], with configurable target machine capacity in the frame settings dialog.
- **Configurable In-Game Mod Update Notifications**:
  - Added an unobtrusive notification badge on the Settings button inside the Calculator Board when a new version of the mod is released, allowing players to view the latest version and download link directly from the settings dialog.
  - Added update notification preferences in the Settings dialog, allowing players to freely enable or disable automatic update checks, in-board badges, and login chat notifications (disabled by default to prevent chat spam in modpacks).
- **Real-Time Rendering & Calculation Profiler HUD**:
  - Added a real-time profiler HUD in the bottom-right corner of the screen when debug mode (`F3`) is enabled, displaying per-section rendering and calculation times along with frames per second (FPS).

### Changed & Improved
- **Addon Performance & Tooltip Display Improvements**:
  - Added full performance breakdowns (Power Output, Fuel Energy, Process Duration, and multi-copy combined effects) to Thermal and Systeams augment hover tooltips.
  - Improved addon grid card subtitles and dual-multiplier badges with compact layouts, preventing text truncation in machine configuration menus.
- **Draggable Favorites Menu Scrollbar**:
  - Made the scrollbar in the Favorites menu and its recipe list panel draggable by clicking and holding, allowing smooth navigation without relying only on mouse wheel scrolling.
  - Enhanced scrollbar thickness and added hover/drag highlights for easier grabbing and clearer visual feedback.

### Fixed
- **Fixed Large Gas Turbine Power Output & Fuel Consumption Fallback**:
  - Fixed an issue where Large Gas Turbines erroneously fell back to HV base tier (1,024 EU/t) instead of EV (4,096 EU/t), resulting in lower calculated power output, distorted fuel consumption rates, and incorrect parallel counts.
  - Equipping a turbine rotor on a singleblock gas turbine node now automatically promotes the workstation to the large multiblock turbine.
- **Fixed Flow Allocation and Match Flow Button for Continuous Drain Junctions in Branching Networks**:
  - Fixed an issue where branching a producer's output to multiple consumers and a continuous drain junction displayed an inaccurate naive split supply rate on the junction tooltip, or caused the match flow (`[⚡]`) button to fill the entire producer output rather than the true available surplus.
  - Improved the junction configuration dialog to preserve precise decimal rates up to 4 decimal places without truncation.
- **Fixed Multi-Click Requirement on Long Recirculation Loops in Auto-Ratio**:
  - Fixed an issue where long closed recirculation loops (such as multi-step nether star crafting) required clicking the Auto-Ratio button multiple times to reach balanced machine counts and clear false-positive loop warning badges.
- **Fixed False-Positive Growth and Loop Warnings on Anchored Recirculation Cycles**:
  - Fixed an issue where output branching or mixed voltage tiers across balanced recirculation loops were falsely diagnosed as growth or deficit loops after setting an anchor.
  - Clicking the anchor action on warning badges now strictly preserves a single anchor across the board instead of creating duplicate anchor conflicts.
  - Warning badges on already-anchored machines now properly indicate that the anchor scale is fixed, rather than redundantly suggesting pinning as an anchor.
- **Fixed Shift Detailed Rate Tooltip Missing on Buckets Fluid Notation**:
  - Fixed an issue where port tooltips did not display detailed exact rates when holding [Shift] if fluid rates were represented in Buckets (e.g. 1.64 B/s) due to premature decimal rounding.
  - Holding [Shift] now accurately displays full-precision exact rates (e.g. 1.64 B/s (1.6384 B/s)) across all fluid unit modes.
- **Fixed Reroute Node Dragging/Editing Inoperability and Vertical Card Resize Issues**:
  - Resolved an issue where reroute nodes could not be dragged across the canvas or double-clicked to edit target batch amounts.
  - Fixed an issue where vertical card resizing caused hitboxes and resize handles to remain at their previous heights, and corrected wire connection endpoints on hidden ports.
- **Fixed Machine Config Dialog Frame Drops and Addon Catalog Stutter**:
  - Fixed an issue where opening the machine configuration dialog caused a significant frame drop by optimizing background node rendering behind dialogs and caching addon card presentation data.
  - Machine configuration dialogs now open and scroll smoothly without UI lag or frame drops.
- **Fixed Excluded Recipe Categories Appearing in Favorites Menu**:
  - Fixed an issue where recipes belonging to categories disabled in the Recipe Category Filter modal still appeared in the Favorites dock sidebar and sub-recipe flyouts.
  - Category exclusions now apply immediately and dynamically filter recipes across both search results and Favorites menus.
- **Fixed Multiblock Parallel and Overclock Calculation with Energy & Parallel Hatches**:
  - Fixed an issue where multiblock machines always attempted maximum parallel processing even when installed energy hatches lacked sufficient power, causing excessive power consumption calculations. Parallel processing is now accurately capped by the total power capacity of the installed energy hatches.
  - Aligned recipe calculation order so recipes are batched by parallel capacity before overclocking, ensuring lower-tier recipes running with high parallel in higher-tier multiblocks correctly calculate within machine power limits instead of consuming excessive higher-tier energy.
- **Fixed Alternative Input Cycling in Slim Card Mode**:
  - Fixed an issue where scrolling over an input port with alternative ingredients in Slim Card Mode changed the machine voltage tier instead of cycling items.
- **Fixed Star Technology Large/Extreme Chemical Reactor Coil Overclock Bonuses**:
  - Fixed an issue where Large Chemical Reactor (LCR) and Extreme Chemical Reactor (ECR) failed to apply recipe duration reduction and EU/t discount bonuses when equipped with heating coils in Star Technology.
  - Accurately applies chemical reactor processing speed and energy consumption bonuses across all 11 heating coil tiers (from Cupronickel to Abyssal Alloy).
- **Fixed Turbine Misidentification and Restored Boost Options on Combustion Engines**:
  - Fixed an issue where Extreme Combustion Engine (ECE) and Large Combustion Engine (LCE) erroneously opened the turbine rotor configuration and rotor catalog instead of combustion engine controls.
  - Restored oxygen boost (LCE), liquid oxygen boost (ECE), oxidizer boost, and frame coolant boost options in the machine configuration dialog, power tooltips, and node badges.
- **Fixed Runaway Power Generation and Cycle Distortion on Combustion Engines & Rocket Modules**:
  - Fixed an issue where opening the machine configuration dialog on combustion engines and rocket modules caused power generation and cycle speeds to multiply uncontrollably.
  - Combustion generators and rocket modules now reliably maintain their intended base generation (such as 2A UV on SRM) and physical running cycles without parallel hatch corruption.
- **Fixed Missing Auxiliary Fluid Input Slots on Combustion Engine & Rocket Modules**:
  - Fixed an issue where Star Technology combustion and rocket modules (T1–T4) and combustion engine boosters failed to generate required auxiliary fluid input slots (lubricants, oxidizers, coolants) on the node card.
  - Essential operating fluids (Lubricant, Tungsten Disulfide) and optional booster fluids (WFNA, RFNA, O₂F₂, FcSO₂, coolants) now accurately generate input slots with precise consumption rates synced to tooltips and mass balance solving.
- **Fixed Runaway Machine Count Explosion in Closed Recirculation Loops during Auto-Ratio**:
  - Fixed an issue where running Auto-Ratio on production lines containing closed byproduct recirculation loops (such as fuel desulfurization with hydrogen recycling) caused machine counts to multiply uncontrollably into millions due to cyclic feedback amplification.
  - Auto-Ratio now reliably protects closed recirculation loops from infinite scaling while accurately balancing external supply and demand.
- **Fixed Inactive Canvas Hotkeys (Alt+R, Shift+C, G) & Dropdown Esc Close**:
  - Resolved an issue where canvas keyboard shortcuts such as Auto-Ratio (`Alt+R`), Fractional Auto-Ratio (`Shift+Alt+R`), Auto-Connect (`Shift+C`), and Grid Snap toggle (`G`) did not trigger when pressed on the board.
  - Added Auto-Ratio (`Alt+R`) and Auto-Connect (`Shift+C`) to the shortcut help guide (`H`), and enabled pressing `Esc` to close open toolbar dropdown menus.

## [2.2.0-alpha.3] - 2026-09-07

### Added
- **Improved Wire Color Interpolation & Settings Preview**:
  - Improved wire color interpolation based on supply/demand saturation ratio, ensuring smooth and natural color transitions without muddy mid-tones.
  - Added a real-time preview curve and gradient bar to the Wire tab in the Board Settings Dialog to preview saturation colors.
- **Create: Diesel Generators Mod Support**:
  - Added support for Create: Diesel Generators, calculating kinetic stress capacity (SU) and fluid fuel consumption rates for all 3 diesel engines (Default, Modular, Huge).
  - Added recipe support for basin fermenting (`basin_fermenting`), crude oil distillation (`distillation`), and compression molding (`compression_molding`).
- **Multi-Filter Recipe Search Query Support**:
  - Added multi-filter recipe search query support, allowing combined filters such as mod namespace (`@mod`), item tag (`#tag`), voltage tier (`tier:`), and power range (`eut:`).
  - Optimized search query evaluation and indexing for responsive search performance across large recipe catalogs.
- **Sequential ESC Dismissal & Modal Dialog Input Isolation**:
  - Improved dialog navigation so pressing `ESC` or clicking outside dismisses only the topmost active dialog in reverse order, preventing accidental workspace exits.
  - Enhanced modal input routing to prevent mouse clicks and keyboard shortcuts from leaking into background canvas elements while dialogs are open.
- **Canvas Interaction Cancellation via ESC / Right-Click**:
  - Added interaction cancellation: pressing `ESC` or right-clicking while dragging nodes or connecting wires immediately cancels the operation and reverts components to their previous positions.
  - Prevented gesture overlap across node dragging, box selection, wire drawing, and viewport panning for smoother canvas manipulation.
- **Multi-Selection Floating Action Bar**:
  - Added a floating action bar above selected nodes whenever 2 or more nodes are selected.
  - Provides one-click access to Group into Frame (`▤`), Group into Module (`📦`), Shared Machine Frame (`⧉`), Auto Ratio (`⚖`), Copy (`📋`), and Delete (`✕`).
- **Multiblock BOM & Global Balance in Left Activity Bar**:
  - Moved the Multiblock BOM (`▦`, Shift+B/M) and Global Balance Dashboard (`📊`, B) buttons to the Left Activity Bar for easier access.
  - Cleaned up redundant chip buttons from the bottom status bar for a cleaner layout.

### Fixed
- **Accurate Rates for Continuous Per-Tick Fluid & Item Recipes**:
  - Fixed an issue where GregTech machines with continuous per-tick fluid or item consumption/production (such as Greenhouses or Pisciculture Fisheries) had their throughput rates drastically underestimated on the board.
- **Fixed Inappropriate Coil Option Display on Non-Coil Machines**:
  - Fixed an issue where multiblock machines with fixed structural coil blocks (such as Heat Chamber, Draco Infusion, or Titan Forge) inappropriately exposed coil tier options in their GUI and BOM.
  - Fixed an issue where non-coil machines (such as Void Extractor) displayed coil tier options on their cards when sharing a recipe category with higher-tier coil machines.
- **Stutter-Free Closed Loop Recirculation Recalculation**:
  - Resolved UI frame drops and micro-stuttering when adjusting machine counts or tiers in complex closed-loop recirculation setups, ensuring smooth real-time responsiveness.
- **Kinetic Machine & Diesel Engine Inspector Display Improvements**:
  - Fixed an issue where GT voltage tiers (`LV`) and overclock buttons inappropriately appeared on Create kinetic machines and diesel engines in the inspector panel; power metrics now display with appropriate units (`SU`, `FE/t`, `EU/t`).
  - Fixed an issue in the Favorites Dock where clicking a diesel engine displayed unrelated kinetic recipes instead of filtering specifically to its combustion power recipes.
- **Page Browser & Favorites Dock Usability Improvements**:
  - Fixed a layout issue where opening the Page Browser drawer covered the left activity bar buttons.
  - Applied mutually exclusive toggling between the Page Browser drawer and Favorites Dock, and prevented mouse hover and click events from leaking into background panels.
- **Hide Multiplayer Options in Singleplayer Worlds**:
  - Hidden the Team Collaboration button (`👥`) and workspace export options from the left activity bar and toolbar when running in singleplayer worlds.
- **Singleblock Combustion Generator Addon Filter Fix**:
  - Fixed an issue where multiblock boost traits (Oxygen / Liquid Oxygen Boost) and maintenance hatches inappropriately appeared in the machine config dialog for singleblock combustion generators (LV~HV).
- **Recipe Initial Voltage Tier & Workstation Clamping**:
  - Fixed an issue where recipes imported from EMI/JEI were incorrectly initialized to low voltage tiers below the recipe requirement or the machine's minimum tier.
  - Improved tier resolution to automatically select matching tiered workstation icons based on recipe requirements and machine specifications.
- **GTCEu & Star Technology Layered Recipe Cluster Extraction**:
  - Fixed an issue where multi-step progressive recipes (such as Large Rotor Machine recipes in Star Technology) imported from EMI/JEI collapsed into a single node with merged inputs.
  - Accurately decomposes layered recipes into sequential nodes (`Layer I`, `Layer II`, etc.) inside a compound group frame with properly apportioned durations and voltage tiers.
- **Node Inspector & Process Summary Panel Alignment**:
  - Fixed an issue where the right-side Node Inspector panel and Process Summary overlay overlapped at identical screen positions.
  - The Process Summary panel now dynamically shifts to the left of the active inspector, keeping real-time flow and power balances visible during machine configuration.
- **Combustion Generator Fuel Rate Accuracy & ZPM Tier Cap**:
  - Corrected combustion generator base voltages for EV+ tiers so fluid fuel consumption rates (such as HOG) match in-game mechanics.
  - Capped combustion generator tier progression at ZPM, preventing Supreme Combustion Module from rolling over into rocket-fuel-only modules (UV) when scrolling.
  - Enabled Modular Combustion Frame (MCF) coolant boost addons on Star Technology combustion and rocket modules with appropriate power multipliers.
- **Node Inspector High Voltage Tier Selection & Responsive Grid**:
  - Expanded available voltage tiers in the Node Inspector panel beyond EV up to MAX for multiblock and singleblock electric machines.
  - Reorganized voltage tier chips into an adaptive multi-row grid, preventing chips from overflowing outside the panel boundary on machines with numerous tiers.
- **Singleblock Combustion Generator Icon Rendering Fix**:
  - Fixed an issue where the machine item icon failed to render on node cards and the inspector panel for singleblock combustion generators (LV~HV).
  - Added compatibility resolution so previously saved board files seamlessly migrate to modern machine identifiers.

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
