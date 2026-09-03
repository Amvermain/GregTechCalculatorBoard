# GregTech Calculator Board (GT Calc Board)

An in-game node flowchart editor and production line rate calculator for **GregTech CEu Modern**, **EMI**, **JEI**, and multi-mod ecosystems, featuring real-time multiplayer team collaboration.

<span><span><span><span><span><span><span><iframe width="560" height="315" src="https://www.youtube.com/embed/IeDe2dZ5b_o?si=uzwK-4LStUl9dR-H" frameborder="0" allowfullscreen="allowfullscreen"></iframe></span></span></span></span></span></span></span>

GregTech Calculator Board provides an interactive, node-based canvas for designing, balancing, and optimizing complex production lines directly inside Minecraft. Place recipe cards, connect ports with dynamic wires, configure machine overclocks and hardware addons, and calculate required machine counts, power draw, and material throughput without external spreadsheets.

***

### Zero-Dependency & Server Flexibility

The mod is completely standalone and fully optional on both client and server:

* **Both Client & Server**: Enables real-time multiplayer team workspace synchronization and concurrent collaboration on dedicated servers.
* **Client-Side Only**: Join vanilla or unmodded servers and keep full access to personal boards and blueprints.
* **Server-Side Only**: Server hosts can install the mod without requiring connecting players to install it.
* **0 Hard Dependencies**: Works out of the box with pure vanilla fallback, or seamlessly integrates with installed mod ecosystems.

***

### Key Features

#### 1. Intuitive Node Canvas & Recipe Flowcharting
* **Seamless Recipe Viewer Integration**: Works natively with **EMI**, **JEI**, and **JEI++**. Place recipes via collapsible **Favorites Dock Panel**, click EMI's `[+]` button, or drag-and-drop directly onto the canvas.
* **Contextual Drag-to-Search**: Drag wires from any port to empty canvas to instantly search matching producer or consumer recipes and auto-connect with ratio matching.
* **In-Place Recipe Switching**: Switch recipes on existing nodes with one click without severing compatible wire connections.
* **Compound Subgraphs & Group Frames**: Group related machines into visual frames (`Ctrl+G`) or collapse entire production chains into compact composite modules (`Ctrl+Shift+G`).
* **Rich Canvas UX**: 16px grid snapping, port bundle wiring, I/O port hiding for compact cards, blueprint sharing, and full Undo/Redo (`Ctrl+Z` / `Ctrl+Y`).

#### 2. Deep GregTech & Multi-Mod Calculation Engine
* **GregTech CEu Modern**: Full ULV~MAX voltage tiers, Standard/Perfect/Lossless overclocking, subtick CPS batching, and dual energy hatch support.
* **Hardware Addons & Multiblocks**: Deductive calculation for heating coils (temperature & speed bonuses), parallel hatches, configurable maintenance hatches, and turbine rotors.
* **Multi-Mod Energy Systems**: Kinetic calculations for **Create** & **Create: New Age** (SU, RPM, generator coils), RF/t dynamos & tier kits for **Thermal Series**, and steam consumption modeling for **Systeams** / Boilers.

#### 3. Factory Multiblock BOM & Shared Machine Pools
* **Multiblock Bill of Materials (BOM)**: Automatically aggregates all required casings, coils, hatches, and controllers across the factory (`B` hotkey). Export shopping lists directly to EMI Recipe Tree, JEI++, or clipboard.
* **Shared Machine Pools (Time-Sharing)**: Group multiple recipes sharing physical machines into a pool frame (`Ctrl+Shift+S`). Automatically calculates cumulative duty cycles (`Duty %`), quantized machine counts, and de-duplicated BOM.

#### 4. AE2 Integration & Target Batch Production ETA
* **AE2 Autocrafting Integration**: Bind board pages directly to AE2 Processing Patterns (`[💠 Bind AE2 Pattern]`). Displays calculated completion times (ETA) and bottleneck overlays directly on AE2's Crafting Confirmation screen.
* **Target Batch Quantity & Real-Time ETA**: Define batch goals (e.g. `100x`, `1,000x`, `10 B`) on goal nodes to calculate exact remaining completion time, total energy, and raw material requirements.

#### 5. Real-Time Multiplayer Team Workspaces
* **Live Team Collaboration**: Design factory blueprints concurrently with teammates on dedicated servers with sub-second synchronization.
* **Team System Integration**: Native support for **FTB Teams**, **Phoenix Guilds**, and **Vanilla Scoreboard Teams**.
* **Edit Concurrency Control**: Automatic per-page edit locks and live teammate presence indicators prevent concurrent overwrite collisions, backed by automated background saving and revision history forks.

***

### Requirements & Mod Compatibility

* **Minecraft**: `1.20.1`
* **Mod Loader**: `Minecraft Forge (47.2.0+)`
* **Required Dependencies**: **None** (Fully standalone)
* **Supported Recipe Viewers**:
  * [EMI](https://curseforge.com/minecraft/mc-mods/emi) (Recommended)
  * [JEI (Just Enough Items)](https://curseforge.com/minecraft/mc-mods/jei) / [JEI++](https://curseforge.com/minecraft/mc-mods/just-enough-calculation) / [JEI unofficial](https://www.curseforge.com/minecraft/mc-mods/jei-unofficial)
* **Supported Tech & Automation Mods**:
  * [GregTech CEu Modern](https://curseforge.com/minecraft/mc-mods/gregtech-ceu-modern) (1.7.0+ or Star Technology forks)
  * [Applied Energistics 2 (AE2)](https://curseforge.com/minecraft/mc-mods/applied-energistics-2)
  * [Create](https://curseforge.com/minecraft/mc-mods/create) & [Create: New Age](https://curseforge.com/minecraft/mc-mods/create-new-age)
  * [Thermal Series](https://curseforge.com/minecraft/mc-mods/thermal-expansion) & [Systeams](https://curseforge.com/minecraft/mc-mods/systeams)
* **Multiplayer Team Integration**:
  * [FTB Teams](https://curseforge.com/minecraft/mc-mods/ftb-teams-forge) / [Phoenix Guilds](https://curseforge.com/minecraft/mc-mods/phoenix-guilds)