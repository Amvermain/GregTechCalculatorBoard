# GregTech Calculator Board (GT Calc Board)

An in-game flowchart editor and recipe rate calculator for **GregTech CEu Modern**, **EMI**, **JEI**, and multi-mod ecosystems, featuring real-time multiplayer team synchronization.

<span><span><span><span><span><iframe src="https://www.youtube.com/embed/xpAbk7TA0lY?si=lAjWgGbrJJETU6Ku&amp;controls=0" width="560" height="315" frameborder="0" allowfullscreen="allowfullscreen"></iframe></span></span></span></span></span>

GregTech Calculator Board provides an in-game, node-based canvas for designing and balancing GregTech production lines. Players can place recipe cards, connect input/output ports with wires, configure machine overclocks and hardware addons, and calculate required machine ratios, power consumption, and material throughput directly inside the client without external spreadsheets or tools.

***

### Installation & Server Compatibility

The mod is fully optional on both client and server:

*   **Installed on Both Client and Server**: Enables real-time multiplayer team workspace synchronization, shared page tabs, and concurrent collaboration across dedicated servers.
*   **Client-Side Only**: Players can connect to vanilla or unmodded servers and maintain full access to personal calculator boards and local blueprints.
*   **Server-Side Only**: Server hosts can install the mod without requiring connecting clients to have the mod installed.

***

### Features

#### Pluggable Recipe Viewer SPI (EMI, JEI, & Vanilla Support)

*   **Multi-Viewer Ecosystem Compatibility**: Seamlessly integrates with **EMI**, **JEI (Just Enough Items)**, **JEI Unofficial**, and **JEI++ (Just Enough Calculation / BoM)**. Automatically elects the best available viewer at runtime.
*   **Side Favorites Dock Panel**: A collapsible panel on the left of the board screen (`[⭐ Favorites (N) ▶]`) allows placing favorited items onto the canvas via single-click or drag-and-drop.
*   **Hierarchical Recipe Navigation & Interactive Preview**:
    *   Hovering over a favorite displays a sub-panel listing all producing recipes (Machine / Duration / EU/t).
    *   Hovering over a recipe renders an interactive preview card with tooltips, `R`/`U` lookups, and single-click node spawning.
*   **One-Click Recipe Addition (`[+]` Button)**: Clicking EMI's `[+]` (Fill Recipe) button or dragging recipes directly onto the canvas adds the recipe to the calculator board.
*   **Pure Vanilla Fallback**: Operates smoothly in unmodded/headless environments using vanilla `RecipeManager`.

#### In-Place Alternative Recipe Switching

*   **One-Click Recipe Switcher**: Switch recipes on existing nodes without deleting them via the `[🔄 Switch Recipe]` button or right-click menu.
*   **Smart Wire Preservation**: Automatically retains wire connections on matching item and fluid ports (`IngredientStack.getId()`).
*   **Full Undo / Redo**: Integrated with the history stack (`Ctrl + Z` / `Ctrl + Y`) for instant rollback.

#### Multiblock Bill of Materials (BOM) System

*   **Automated Structural Solver**: Open via `B` or the `[📦 BOM]` toolbar button to calculate required casings, heating coils, hatches, and controllers across the factory.
*   **Hybrid Hatch Override & Casing Subtraction**: Customize energy and utility hatches with dynamic casing block deductions and dual lower-tier hatch toggling.
*   **1-Click Recipe Tree Target**: Export shopping lists directly into EMI Recipe Tree or JEI++ targets, or copy formatted text to clipboard.

#### Target Batch Production ETA (Estimated Time) System

*   **Batch Quantity Target**: Specify target batch quantities (e.g. `100x`, `1,000x`, `10 B`) on goal and reroute nodes.
*   **Real-Time ETA Calculation**: Computes remaining time based on upstream inflow rates and displays `ET: 24m 52s` badges with total batch energy and material requirements.

#### Multiplayer Team Workspaces

*   **Real-Time Synchronization**: Changes made on team boards are synchronized across connected teammates in real time.
*   **Team System Integration**: Supports FTB Teams, Phoenix Guilds, and Vanilla Scoreboard Teams to establish isolated team workspaces.
*   **Automatic Background Sync**: Modifies and commits changes automatically after 3 seconds of inactivity and upon closing the interface or switching tabs.
*   **Per-Page Edit Locks**: Automatically claims temporary edit locks upon node interaction to prevent simultaneous write conflicts, with live teammate presence indicators.
*   **Save History & Forking**: Displays revision history logs and allows forking past team revisions into personal board tabs (`[Personal Board]`).

#### Recipe Search & Contextual Auto-Wiring

*   **Parametric, Boolean & Category Search**: Supports tokenized queries including `AND` (whitespace or `&`), `OR` (`|`), `NOT` (`!`), mod namespaces (`@mod`), item/fluid tags (`#tag`), and machine categories (`[category]`, `[pyro]`, `%smelting`).
*   **Optimized Multicore Indexing**: Parallel search execution indexes 170,000+ recipes without keystroke lag, prioritizing exact title and category matches.
*   **Contextual Drag-to-Search**: Dragging a wire from a port onto empty canvas opens a filtered search for consumers (from outputs) or producers (from inputs). Selecting a recipe places the node and connects the wire, with optional ratio matching when holding `Shift`.

#### Hardware Addons & Multiblock Configuration

*   **Addon Configuration Modal**: Equips and manages heating coils, maintenance hatches, turbine rotors, Thermal Expansion augments, and machine traits.
*   **Deductive Multiblock Capability Matrix**: Inspects recipe metadata and multiblock structures to determine machine compatibility and switch between singleblock and multiblock modes.
*   **Coil & Addon Stat Calculation**: Extracts temperature thresholds, EBF discounts, Pyrolyse speed multipliers, LCR benefits, and Multi Smelter parallel limits according to GTCEu formulas.
*   **Thermal Expansion Augment Support**: Supports 1-kit tier upgrades (LV~EV, 6x~48x parallel) with automatic replacement, along with 3-slot stacking for regular augments.

#### Multi-Mod Energy & Mechanics Compatibility

*   **GregTech CEu Modern**: Full ULV~MAX voltage tiers, Standard/Perfect/Lossless overclocking, subtick CPS batching, and dual energy hatch support.
*   **Create & Create: New Age**: Kinetic generator calculations (Water Wheels, Windmills, Steam Engines in SU/RPM), electric motors, generator coils, and magnet rings.
*   **Thermal Series**: Dynamo RF/t generation, augment multipliers, and tier upgrade kits.
*   **Systeams & Steam Boilers**: Steam boiler mB/s output, steam dynamos, and LP/HP steam machine consumption modeling.

#### Blueprint Packaging & Shareable Imports
*   **Rich Blueprint Metadata**: Export entire flowcharts or selected node groups with custom titles, descriptions, authors, and tag metadata.
*   **Interactive Import Preview**: Inspect node count, machines, connections, and major inputs/outputs before choosing to open in a new page or overwrite.

#### Shared Machine Pool (Time-Sharing Frame)
*   **Time-Shared Machine Pooling**: Group multiple recipes into a shared machine pool frame to model a single physical machine executing multiple recipes across time.
*   **Duty Cycle & Required Machine Solving**: Computes cumulative duty (`Total Duty %`) and quantized required machines (`Ceil`), rendering live header badges and comprehensive breakdown tooltips.
*   **Hardware Config Synchronization**: Synchronize voltage tiers, overclock modes, parallel counts, and addons across all enclosed machines from the frame header.
*   **De-duplicated Multiblock BOM**: Accurately accounts shared multiblock structures once in the BOM calculation.

#### Port Multi-Selection & Bundle Batch Wiring UX
*   **Marquee Port Selection**: Drag a selection box over machine cards to selectively grab multiple input or output ports without selecting entire cards.
*   **Explorer-Style Multi-Select**: `Ctrl + Click` to toggle individual ports, `Shift + Click` to select port ranges.
*   **Multi-Wire Bundle Dragging**: Drag from multiple selected ports to bundle wires with real-time multi-bezier curve rendering.
*   **Junction Node Generation & Frame Auto-Wiring**: Drop on empty canvas to spawn vertical aligned junction nodes, or drop onto a Shared Machine Pool to automatically wire matching machines and spawn missing recipe cards with hardware sync and auto-expanding bounds.
*   **Secondary Input Preference Matching**: Intelligently prioritizes matching secondary inputs/fluids (e.g. Lubricant vs Water) when auto-spawning recipes in machine pools.
*   **One-Click Auto-Fit Frame**: Header `[⛶]` button and double-click to instantly auto-fit frame bounds to enclosed contents.

#### I/O Port Hiding & Selective Restore

*   **Hide Unused Ports**: Right-click any input/output port to sever existing wires and hide unwanted ports, keeping complex multi-output machine cards compact and clutter-free.
*   **Hidden Ports Badge & Dropdown Popup**: Displays a pill badge (e.g. `2 Outputs hidden`, `3 Ports hidden`) on the machine card. Clicking the badge opens a dropdown popup to selectively unhide ports with a single click.

#### Smart Inline Text Editing Engine

*   **Rich Text Control**: All inline editable fields (machine name, machine count, parallel count, target batch quantity) support native cursor positioning, Shift/drag selection, Ctrl+A select all, and Ctrl+C/V/X clipboard operations.
*   **Word-Level Navigation**: Rapidly jump and delete words using Ctrl + Arrow Keys and Ctrl + Backspace/Delete.

#### Accessibility & Canvas Controls

*   **Fit to View (`Home` / `F`)**: Instantly center and auto-scale the camera to bring all placed factory nodes into view via the toolbar button or hotkeys.
*   **Smart Auto-Connect Filter**: Interactively select and filter matching items/fluids via checkboxes when dragging wires or auto-connecting machines.
*   **Integrated Board Settings Modal**: Dedicated settings dialog (`[⚙ Settings]`) to configure fluid units, time units, singleplayer pause, and harmonization ratio limits.
*   **5-Level UI Font Scale**: Adjust machine configuration dialog size (`0.75x`, `0.85x`, `1.0x`, `1.15x`, `1.30x`) via the `[Aa 1.0x]` button, mouse wheel, or `+`/`-` keys.
*   **Uniform Global Fluid Units**: Toggle canvas fluid rates between `Auto`, `Always mB`, and `Always B` via toolbar or `Shift+T`.
*   **Page Tab Overflow Navigation**: Smoothly click `«`, `»` or scroll mouse wheel across multiple board pages without edge clipping.
*   **Infinite Canvas & LOD**: Pan with right-click or middle-click drag, zoom with mouse wheel, and render large graphs efficiently using Level of Detail (LOD) mode.
*   **Undo & Redo**: Delta-based history stack (`Ctrl + Z` / `Ctrl + Y`) for all canvas actions.

#### Compound Modules (Subgraphs)

*   **Modularization (`Ctrl + G`)**: Collapses groups of interconnected nodes into a single composite module card.
*   **Internal Flow Hiding**: Encapsulates internally consumed items and fluids, exposing only true external inputs, net outputs, total machine count, and aggregate power draw.
*   **Unpack / Expand**: Restores encapsulated machines and wiring back onto the main canvas with a single click.

***

### Requirements & Mod Compatibility

*   **Minecraft**: `1.20.1`
*   **Mod Loader**: `Minecraft Forge (47.2.0+)`
*   **Required Dependencies**: **None** (Fully standalone, 0 hard dependencies required)
*   **Supported Recipe Viewers**:
    *   [EMI](https://curseforge.com/minecraft/mc-mods/emi) (1.1.24+ / Recommended)
    *   [JEI (Just Enough Items)](https://curseforge.com/minecraft/mc-mods/jei) (15.2.0+)
    *   [JEI++ (Just Enough Calculation)](https://curseforge.com/minecraft/mc-mods/just-enough-calculation) (BOM recipe tree integration)
*   **Supported Tech & Factory Mods**:
    *   [GregTech CEu Modern](https://curseforge.com/minecraft/mc-mods/gregtech-ceu-modern) (1.7.0+ or Star Technology forks)
    *   [Create](https://curseforge.com/minecraft/mc-mods/create) (0.5.1 / 6.0+)
    *   [Create: New Age](https://curseforge.com/minecraft/mc-mods/create-new-age)
    *   [Thermal Series](https://curseforge.com/minecraft/mc-mods/thermal-expansion) (Expansion, Foundation, Cultivation)
    *   [Systeams](https://curseforge.com/minecraft/mc-mods/systeams)
*   **Optional Team Synchronization**:
    *   [FTB Teams](https://curseforge.com/minecraft/mc-mods/ftb-teams-forge) (For dedicated server multiplayer team workspaces)
    *   [Phoenix Guilds](https://curseforge.com/minecraft/mc-mods/phoenix-guilds) (Teams & Guilds integration)