# Changelog

<p align="center">
  <b>English</b> | <a href="CHANGELOG_KR.md">한국어</a>
</p>

All notable changes to **GregTech Calculator Board** will be documented in this file.

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
  - Accurately parses Large Boiler recipes (`gtceu:large_boiler`), calculating baseline steam production ($800\text{ mB/t} = 16,000\text{ mB/s}$) and water consumption ($5\text{ mB/t} = 100\text{ mB/s}$, 1:160 ratio) for the Large Bronze Boiler.
  - Multi-tier speed scaling for large boilers: L-Bronze $1.0\times$ ($800\text{ mB/t}$), L-Steel $2.25\times$ ($1,800\text{ mB/t}$), L-Titanium $4.0\times$ ($3,200\text{ mB/t}$), and L-Tungstensteel $8.0\times$ ($6,400\text{ mB/t}$).
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
- **Magnet & Multi-Stackable Addon Click Interaction (`MachineConfigDialog`, `CreateNewAgeModAdapter`)**:
  - Left-clicking catalog cards now stacks addons (+1) up to 12 slots, while Shift + Left-Click fills remaining slots (+12) or batch-replaces them.
  - Right-clicking uninstalls 1 copy (-1), and the top `Clear Magnets` button resets all installed magnets.
- **Deferred Addon Tooltip Rendering (`MachineConfigDialog`)**:
  - Resolved UI layering bug where lengthy addon descriptions were clipped by dialog boundaries.

### Fixed
- **Auto Connect Duplicate Bypass Wire Prevention (`ToolbarWidget`)**:
  - Prevented redundant direct bypass wires from being created between producers and consumers that are already routed through a reroute junction hub.
- **Large Boiler Recipe Double-Acceleration Duration Bug (`GTCEuRecipeHandler`, `GTCEuModAdapter`)**:
  - Fixed an issue where singleblock boiler speed multipliers were redundantly applied to large boiler recipes, distorting cycle duration down to 0.05s (1 tick) and vastly overconsuming fuel.
- **HP Steam to Electric Singleblock Machine Icon Recovery Bug (`CategoryCapabilityMatrix`, `GTCEuModAdapter`, `GTCEuSteamProcessingTest`)**:
  - Fixed an issue where disabling HP Steam mode unexpectedly promoted singleblock machines to multiblock icons instead of restoring the original tier-specific singleblock workstation.
- **Dialog & Search Text Input 'E' Key / Canvas Hotkey Conflict Bug (`RecipeSearchDialog`, `BoardScreen`, `MultiblockBOMDialog`, `GlobalBalanceDashboardDialog`)**:
  - Fixed a key routing bug where typing letters (such as 'E', 'B', 'T', 'F', 'J') into search boxes or modal input fields triggered Minecraft's inventory close key (E) or board canvas shortcuts instead of typing cleanly into the input box.
- **Kinetic Generator & Motor EMI Recipe Integration & SU Search Indexing (`KineticGenerationEmiRecipe`, `CalcBoardEmiPlugin`, `CreateRecipeHandler`, `CreateNewAgeRecipeHandler`, `RecipeSearchEngine`)**:
  - Fixed an issue where pinning kinetic generators (such as Large Water Wheel) to EMI favorites did not link to their generation recipes by registering them as native EMI synthetic recipes with workstations and outputs.
  - Enriched search indices so querying `<su`, `<stress`, or machine names immediately matches stress unit generation recipes.

---

## [2.0.0-alpha.8] - 2026-08-25

### Added
- **Star Technology & Threading Helix System (`StarTModAdapter`, `StarTAddonCrawler`, `StarTTurbineHelper`, `GTThreadingHelix`, `NodeThreadingConfig`, `MachineConfigDialog`, `NodeCardRenderer`)**:
  - Added dedicated `[🧵 Threading]` sub-tab in `MachineConfigDialog` for all GTCEu multiblock machines supporting Threading (Generalis, Velocitas, Efficienta, Parallelismus, Filum).
  - Live two-way synchronization between the Threading Builder and the top `Active Addons` tray with combined stats badges and quantity indicators (e.g. `8x OpV Weaving Thread Helix`).
  - Added item decoration quantity badges on addon slot icons and node card trays (e.g. `8`).
  - Multi-model Plasma Turbine support: Large Plasma Turbine (LPT, $1\times$), Supreme Plasma Turbine (SPT, $6\times$), and Nyinsane Plasma Turbine (NPT, $12\times$).
  - Star Technology Multiblock Traits: Lubricant Boosting (+25% / +50% EU/t with Tungsten Disulfide) and Coolant Boosting (+75% / +150% EU/t with Superstate Helium 3 / Oganesson Stabilized BEC).
- **GregTech Steam-Era Processing Machinery (LP Bronze / HP Steel) & Direct Steam Consumption Mode (`SteamMode`, `RecipeNode`, `CategoryCapabilityMatrix`, `GTCEuGuiHandler`, `MachineConfigDialog`)**:
  - Added direct steam processing modes (`LP Steam`, `HP Steam`) for all GregTech recipes that support steam processing machinery (Macerators, Compressors, Alloy Smelters, Furnaces, Extractors, Rock Breakers, etc.).
  - **GregTech Steam Physics Ratio**: $1\text{ EU} = 2\text{ mB Steam}$ ($2\text{ L Steam}$).
  - **Low Pressure Steam (LP Bronze)**: $2.0\times$ duration ($0.5\times$ speed), disconnected from electric EU grid ($0\text{ EU/t}$), activates a $\text{BaseEUt} \times 2\text{ mB/t}$ Steam (`gtceu:steam`) fluid input slot.
  - **High Pressure Steam (HP Steel)**: $1.0\times$ duration ($1.0\times$ standard LV speed), disconnected from electric EU grid ($0\text{ EU/t}$), activates a $\text{BaseEUt} \times 2\text{ mB/t}$ Steam (`gtceu:steam`) fluid input slot.
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
  - Automatically calculates fuel burn, water consumption (`minecraft:water`), and steam production (`gtceu:steam`) based on official GTCEu ratios (Small Bronze Boiler baseline: $120\text{ L/s} = 6\text{ mB/t}$ Steam, $1\text{mB Water} \rightarrow 160\text{mB Steam}$).
  - Automatically generates empty container outputs (e.g. `minecraft:bucket` from Lava Buckets).
  - Supported speed & throughput scaling across all boiler tiers: High Pressure Steel Boiler ($3\times$), Large Bronze Boiler ($8\times$), Large Steel Boiler ($15\times$), Large Titanium Boiler ($26.6\times$), and Large Tungstensteel Boiler ($40\times$).
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
    - Calculated Generator Coil base stress (`24.0 SU/RPM`) and total stress load ($(24.0 + \text{Strength}) \times \text{RPM}$).
    - Dynamically retrieved mod config (`suToEnergy`) via runtime reflection for power output ($\text{FE/t} = \text{Strength} \times \text{RPM} \times \text{suToEnergy}$).
    - Rendered Goggle-style UI header with energy stats (Base Stress, Total Stress, Efficiency, FE/t).
  - **Motors & Motor Extensions Calculations**: Calculated SU output and FE power consumption for motors and extension multipliers.
  - **Kinetic Overstressed Handling (`FlowGraphSolver`)**: Halts nodes (`0.0 efficiency`, `0 FE/t`, `0 SU/s`) when Stress Unit supply is deficient.
- **GTCEu Fusion Reactor Simulation & Start Buffer Aggregation (`RecipeNode`, `FlowGraphSolver`, `SummaryOverlay`, `NodeBadgeRegistry`)**:
  - Extracted recipe `eu_to_start` (ignition requirement) and preserved in NBT.
  - Automatically determined Fusion Tier (Mk1 $\le 160\text{M}$, Mk2 $\le 320\text{M}$, Mk3 $> 320\text{M EU}$) and minimum voltage tier (LuV/ZPM/UV) with target tier clamping.
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
  - Applied proportional scaling for processing duration and stress impact ($\text{Base SU} \times \frac{\text{RPM}}{32}$) based on rotation speed relative to 32 RPM.
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
  - Implemented pre-baked $O(1)$ capability lookup matrix mapping recipe categories directly to machine workstations and supported addon categories.
  - Added deterministic capability deduction for Heating Coils, Turbine Rotors, Parallel Hatches, Maintenance Hatches, and Thermal Series Augments.
  - Added dedicated unit test suite (`CategoryCapabilityMatrixTest`) covering singleblock, multiblock, turbine, dynamo, and filtrator categories.
- **Deductive Multiblock Capability Mapping (`MultiblockDetector`, `EmiRecipeConverter`)**:
  - Automatically deduce coil and turbine capabilities for complex multiblocks (Large Chemical Reactor, Pyrolyse Oven, EBF, Cracker, etc.) by analyzing workstation structures from `multiblock_info`.
  - Dynamically synchronize deduced categories with `MultiblockDetector.registerCoilCategory` and `registerTurbineCategory`.
- **Thermal Machine Upgrade Kit Replacement & 3-Slot Augment Stacking (`RecipeNode`, `MachineConfigDialog`, `ThermalAugmentHelper`)**:
  - Implemented 1-Kit-only rule for Thermal Upgrade Kits (LV~EV, 6x~48x parallel scale) with automatic replacement upon selecting a new tier.
  - Added support for stacking identical regular augments (ARC, MCI, etc.) across up to 3 slots (e.g. 3x EV MCI = $4.096\times$ fuel energy) using pure NBT float tags (`AugmentData.Type: Dynamo_Fuel`).
  - Added catalog card badges (`✔ x2`, `✔ x3`, `3/3`), Left-Click (+1) / Right-Click (-1), and single-instance removal from top active slot bar.

### Changed
- **Elimination of Heuristic Addon Deduction (Rule 5 Addon Spec Deductive Analysis Policy)**:
  - Completely purged all substring checks (`contains("rotor")`, `contains("coil")`, `id.getPath()`), tooltip string parsing, and item display name heuristics across the entire codebase.
  - Migrated all addon extraction to deterministic official tags (`gtceu:circuits/*`, `thermal:upgrade_kit`), exact NBT float/int structures (`AugmentData`), and runtime reflection (`CoilHelper`, `TurbineRotorHelper`, `ParallelHelper`).
- **Responsive Dialog Loading Overlays & Live State Updates (`MachineConfigDialog`, `RecipeSearchDialog`)**:
  - Added clean section-level loading overlays with animated indicators while background indexing is in progress.
  - Implemented reactive live updates: as soon as background baking completes, the `[♨ Coil]`, `[⚡ Parallel]`, and `[🔧 Maint]` chips auto-populate seamlessly without modal freezing or needing to reopen dialogs.
- **Massive Recipe Search Indexing Speedup ($250\times$ faster, 2m $\rightarrow$ <1s) (`RecipeSearchDialog`, `RecipeSearchEngine`)**:
  - Eliminated $O(N^2)$ category loops in EMI recipe caching using multi-core `parallelStream`, indexing 170,000+ recipes in under 1 second.
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
