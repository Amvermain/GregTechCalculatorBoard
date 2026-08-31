# [01] Core Domain Models & Deterministic Capability Matrix (Core Domain & Models)

> 📍 **GTCalcBoard Technical Specification Series**
> [[00] System Overview](00_OVERVIEW.md) ➔ **[01] Core Domain & Models** ➔ [[02] Math & Algorithms](02_MATH_AND_ALGORITHMS.md) ➔ [[03] UI & Rendering Pipeline](03_UI_AND_RENDERING_PIPELINE.md) ➔ [[04] Multiplayer & Network](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] External Integration & i18n](05_INTEGRATION_AND_I18N.md)

---

## 1. Core Data Models (`com.gtceu.calcboard.api`)

### 1.1 `GTVoltageTier` (Voltage Tier Enum)
Defines all 15 voltage tiers of GregTech CEu Modern with complete formatting tokens and themes.

| Tier | Voltage (EU/t) | UI Code | Theme Color (Hex ARGB) |
| :--- | :--- | :--- | :--- |
| `ULV` | 8 | ULV | `0xFF8C8C8C` |
| `LV` | 32 | LV | `0xFFDCDCDC` |
| `MV` | 128 | MV | `0xFFFF6464` |
| `HV` | 512 | HV | `0xFFFFFF64` |
| `EV` | 2,048 | EV | `0xFF6464FF` |
| `IV` | 8,192 | IV | `0xFFFF64FF` |
| `LuV` | 32,768 | LuV | `0xFF64FFFF` |
| `ZPM` | 131,072 | ZPM | `0xFFFF6464` |
| `UV` | 524,288 | UV | `0xFF64FF64` |
| `UHV` | 2,097,152 | UHV | `0xFFFF3232` |
| `UEV` | 8,388,608 | UEV | `0xFF64B4FF` |
| `UIV` | 33,554,432 | UIV | `0xFF32FF82` |
| `UXV` | 134,217,728 | UXV | `0xFFFF82FF` |
| `OpV` | 536,870,912 | OpV | `0xFF5050FF` |
| `MAX` | 2,147,483,647 | MAX | `0xFFFF8282` |

---

### 1.2 `IngredientStack` (Ingredient Stack Model)
Encapsulates items and fluids moving through input/output port sockets.

```java
public class IngredientStack {
    private final ResourceLocation id;        // Unique identifier (e.g. gtceu:benzene)
    private final String displayName;          // Localized name (e.g. "Benzene")
    private double amount;                     // Quantity per single recipe cycle
    private final boolean isFluid;             // Fluid flag (true: mB / false: Item count)
    private double chance;                     // Base acquisition probability (0.0 ~ 1.0)
    private double tierChanceBoost;            // Added chance per tier increase (default: 0.05 = +5%)
}
```

---

### 1.3 `RecipeNode` (Pure Domain Node Model)
Holds the operational and calculation state of machines, generators, or compound modules on the canvas, delegating mod-specific behaviors to `IModAdapter`.

```mermaid
classDiagram
    class RecipeNode {
        +String id
        +double posX, posY
        +String name
        +List~IngredientStack~ inputs
        +List~IngredientStack~ outputs
        +double baseDurationTicks
        +double baseEUt
        +GTVoltageTier recipeTier
        +GTVoltageTier targetTier
        +OverclockMode overclockMode
        +double machineCount
        +int parallel
        +boolean isMultiblock
        +boolean isGenerator
        +boolean isFlipped
        +SteamMode steamMode
        +List~MachineAddon~ addons
        +ResourceLocation machineIcon
        +ResourceLocation recipeCategoryId
        +List~ResourceLocation~ availableWorkstations
        +NodePropertyStore properties
        +double efficiency
        +Set~Integer~ hiddenInputIndices
        +Set~Integer~ hiddenOutputIndices
        +hideInputPort(int) void
        +unhideInputPort(int) void
        +hideOutputPort(int) void
        +unhideOutputPort(int) void
        +getVisibleInputIndices() List~Integer~
        +getVisibleOutputIndices() List~Integer~
        +getTotalHiddenCount() int
        +setMachineIcon(icon) void
        +getEnergyType() EnergyType
        +getOverclockResult() OverclockResult
        +getSingleMachineEUt() double
        +getTotalEUt() double
        +getCyclesPerSecond() double
    }
```

* **Clean Architecture & SPI Delegation (Pure Domain Model)**:
  - `RecipeNode` is a pure calculation domain entity across all supported mods (Create, Thermal, GTCEu, Vanilla, etc.).
  - Contains no hardcoded mod branches; machine icon change handling (`setMachineIcon`), physical energy type resolution (`getEnergyType`), single machine power computation (`computeSingleMachinePower`), node operational validation (`validateNode`), and multiblock BOM calculation (`buildMultiblockBOM`) are delegated dynamically via `ModAdapterRegistry.getAdapterForNode(this)`.
* **`hiddenInputIndices` / `hiddenOutputIndices`**: Set of integer port indices hidden by the user to reduce visual clutter on cards. Persisted via `RecipeNodeSerializer`. Renderers and wire solvers invoke `getVisibleInputIndices()` / `getVisibleOutputIndices()` to filter rendered and active ports.
* **`isFlipped`**: Horizontally inverts the input (left) and output (right) socket ports to eliminate wire crossings in complex flowcharts.
* **`efficiency` ($\eta \in [0.0, 1.0]$)**: Machine utilization computed by solvers (`FlowGraphSolver`, `MassBalanceSolver`) subject to upstream supply limits and closed-loop cycles.
* **`calculateEffectiveOutputRates()`**: Computes per-second output flow combining machine count, parallel multiplier, overclocking, sub-tick batching, addon compounding, and byproduct tier chance boosts.

---

### 1.4 `NodePropertyStore` & `NodeProperties` (Type-Safe Dynamic Properties)
Manages mod-specific and feature-specific metadata without polluting the `RecipeNode` class fields.

```java
public class NodePropertyStore {
    private final Map<NodeProperty<?>, Object> properties = new HashMap<>();

    public <T> T get(NodeProperty<T> prop) {
        return (T) properties.getOrDefault(prop, prop.defaultValue());
    }

    public <T> void set(NodeProperty<T> prop, T value) {
        properties.put(prop, value);
    }
}
```

* **Standard Properties (`NodeProperties`)**:
  - `REQUIRED_REFLECTOR_TIER` (`Integer`, default `0`): Fusion reactor reflector tier requirement
  - `TURBINE_ROTOR_EFFICIENCY` (`Integer`, default `100`): Large turbine rotor efficiency (%)
  - `TURBINE_ROTOR_POWER` (`Integer`, default `100`): Large turbine rotor power factor (%)
  - `TURBINE_ROTOR_NAME` (`String`, default `""`): Installed rotor material display name
  - `TURBINE_HOLDER_BONUS` (`Integer`, default `0`): Rotor holder bonus efficiency (%)
  - `CLEANROOM_TIER` (`Integer`, default `0`): Cleanroom cleanliness requirement level
  - `EBF_TEMPERATURE` (`Integer`, default `0`): Electric Blast Furnace required temperature ($K$)
  - `BOILER_THROTTLE` (`Integer`, default `100`): Large boiler operational throttle percentage (25% ~ 100%)
  - `TARGET_BATCH_AMOUNT` (`Double`, default `0.0`): Terminal/reroute target batch quota
  - `TARGET_BATCH_TIME_SEC` (`Double`, default `0.0`): Target completion deadline in seconds

---

### 1.5 Dedicated Calculation & Workstation Resolvers (SRP Decomposition)

To preserve `RecipeNode` as a pure POJO domain model, rate integration and workstation lookup logic are decomposed into dedicated components:

* **`NodeRateCalculator`**:
  - Integrates per-second ingredient flow rates (`IngredientStack`) by compounding machine counts, parallels, overclocks, duration cycles, subtick CPS, addon multipliers, and tier byproduct probability boosts.
  - Dedicated yield calculations (`getSingleMachineYieldPerSecond`) and active consumption rates.
* **`NodeWorkstationResolver`**:
  - Deductively resolves workstation `ResourceLocation`s for corresponding voltage tiers from official registries and capability matrices.
  - Handles multiblock controller validation and tier variant workstation filtering.

---

### 1.6 `FlowGraph` & Immutable Collection Encapsulation

`FlowGraph` encapsulates the full canvas node network topology, strictly guarding against external state corruption.

* **Immutable View Encapsulation (`Collections.unmodifiableList`)**:
  - `getNodes()` and `getEdges()` return unmodifiable views, forcing state mutations through explicit methods (`addNode`, `removeNode`, `connect`, `disconnect`).
* **$O(1)$ Fast Node Index Synchronization (`nodeMap`)**:
  - Internal `Map<String, RecipeNode> nodeMap` is synchronized during node addition, removal, and clearing, guaranteeing $O(1)$ lookup time for `getNode(id)`.
* **`ConnectionEdge` Immutable Record**:
  ```java
  public record ConnectionEdge(String fromNodeId, int outputIndex, String toNodeId, int inputIndex)
  ```

---

### 1.7 `EnergyType` & `SteamMode` (Multi-Energy & Physical Models)

* **`EnergyType`**:
  - `ELECTRIC_EU`: GregTech power (EU/t)
  - `KINETIC_SU`: Create rotational kinetic energy (SU, RPM)
  - `ELECTRIC_FE`: Thermal / Create New Age power (RF/t, FE/t)
  - `HEAT_OR_SELF`: Steam boilers and combustors (mB/s Steam generation)
  - `NONE`: Passive / unpowered recipes (0 Power)
* **`SteamMode`**:
  - `NONE`: Standard electric operation
  - `LOW_PRESSURE`: Low pressure steam processing ($2.0\times$ duration, 1 EU = 2 mB Steam)
  - `HIGH_PRESSURE`: High pressure steam processing ($1.0\times$ duration, 1 EU = 2 mB Steam)

---

## 2. Deterministic Capability Matrix (`CategoryCapabilityMatrix`)

An $O(1)$ immutable global cache built during game initialization via deductive analysis to eliminate heuristic tooltip string parsing.

```mermaid
flowchart LR
    subgraph Bake["1. Pre-Baking Pipeline (CategoryCapabilityMatrix.bake)"]
        GTR["GTRegistries.MACHINES Scan\n(MachineDefinition.getRecipeTypes)"]
        INFO["EMI multiblock_info Structure Scan\n(ICoilType Material Inspection)"]
        TAGS["Forge/Thermal Tag Indexing\n(thermal:lapidary_fuel, etc.)"]
        
        GTR & INFO & TAGS --> BUILDER["CategoryCapabilityMatrixBuilder"]
        BUILDER --> BAKE_PROCESS["1) Category ➔ Workstations (1:N) Mapping\n2) Heating Coil Support Deduction\n3) Turbine/Generator Spec Extraction\n4) Parallel/Maintenance Hatch Capabilities"]
        BAKE_PROCESS --> MATRIX[("CategoryCapabilityMatrix (Immutable Cache)")]
    end

    subgraph Query["2. Runtime O(1) Query Pipeline"]
        NODE["RecipeNode (Recipe Select/Convert)"] --> GET["matrix.getCapability(recipeCategoryId)"]
        MATRIX --> GET
        GET --> INJECT["Inject Workstations & Capability Flags into Node"]
        INJECT --> UI["MachineConfigDialog (Render Valid Tabs Instantly)"]
        INJECT --> SOLVER["FlowGraphSolver (Precise Overclock/Heating Math)"]
    end
```

---

## 3. Compound Module System (`FlowGraphModuleHandler`)

Packages multi-node subgraphs into a single compact compound module card (`Ctrl+G`) or expands them back into full subgraphs.

```mermaid
flowchart LR
    subgraph Expanded["Expanded Sub-Graph"]
        M1["Machine 1 (Crude Feed)"] --> M2["Machine 2 (Intermediate)"]
        M2 --> M3["Machine 3 (Refinery)"]
        M3 -->|Recycled Byproduct| M1
    end
    
    subgraph Collapsed["Compound Module Card"]
        CM["[Module] Oil Refinery Line\nNet EU/t: -4,800 (EV)\n12 Machines | 2 In | 3 Out"]
    end
    
    Expanded -- "Group (Ctrl+G)" --> Collapsed
    Collapsed -- "Expand (Ctrl+G)" --> Expanded
```

1. **Boundary I/O Promotion**: Intermediate wires between internal nodes are encapsulated; only external feedstock and end-products are promoted to outer card socket ports.
2. **Wire Remapping**: External `ConnectionEdge` instances are remapped to the newly created compound card ports.
3. **Proportional Scaling**: Changing machine count on the compound card proportionally scales all internal machine counts and flow rates.

---

## 4. Serialization, Clipboard & Disk Management (`BlueprintCodec`, `BlueprintFileManager`, `NodeClipboard`)

### 4.1 `BlueprintCodec`
Serializes graph topology, coordinates, addons, voltage tiers, viewport, and metadata into NBT followed by GZIP compression and Base64 encoding. Supports optional human-readable title prefixes for chat readability while maintaining 100% backward compatibility.

$$\text{Blueprint String} = \text{"GTBOARD:"} + [\text{Title} + \text{":"}] + \text{Base64}\Big(\text{GZIP}\big(\text{BlueprintPackage.serializeNBT()}\big)\Big)$$

### 4.2 `BlueprintFileManager`
* **Storage Path**: `<gameDir>/gtcalcboard/blueprints/`
* **File Format**: `.gtcb` (Compressed NBT with Atomic File Move to prevent corruption)
* **Key Features**: Individual blueprint disk persistence, directory scanning with metadata extraction, file deletion, and native OS file explorer integration.

### 4.3 `NodeClipboard`
* Copies (`Ctrl+C`) / cuts (`Ctrl+X`) selected nodes and internal edges.
* Pastes (`Ctrl+V`) with new UUIDs and a $+20\text{px}$ offset relative to the cursor or canvas center.

---

## 5. Undo / Redo Command Architecture (`HistoryManager`, `BoardCommand`)

Maintains command deltas for all canvas operations with minimal memory overhead (<2MB for 1,000+ undo steps).

* **Supported Commands**: `MoveNodesCommand`, `AddConnectionCommand` / `RemoveConnectionCommand`, `AddNodesCommand` / `RemoveNodesCommand`, `ModifyPropertyCommand`, `GroupModuleCommand` / `ExpandModuleCommand`, `ResizeFrameCommand`.

---

## 6. Canvas Group Frames & Shared Machine Pools (`CanvasGroupFrame`)

Manages visual grouping regions and time-sharing machine pools.

* **`isSharedMachineFrame`**: Time-sharing mode where all enclosed machine recipes share a single physical machine pool.
* **Cumulative Duty Calculation**: $\text{Total Duty} = \sum \text{machineCount}_i$, Required Machines = $\lceil \text{Total Duty} \rceil$.
* **Batch Hardware Config Synchronization (`syncHardwareConfig`)**: Synchronizes voltage tiers, overclock modes, parallel limits, and equipped addons across all enclosed machines from the frame header.
* **One-Click Auto-Fit Frame (`autoFit`)**: Automatically adjusts frame bounding box to tightly enclose all contained/intersecting nodes with a 24px padding.

---

## 7. Port Reference Record (`PortRef`)

A lightweight immutable record identifying specific input/output ports on the canvas.

```java
public record PortRef(String nodeId, boolean isInput, int portIndex) {}
```

* **Multi-Port Selection**: Windows Explorer-style `Ctrl + Click` individual toggle and `Shift + Click` continuous range selection.
* **Bundle Batch Wiring**: Dragging from multiple selected ports renders real-time multi-bezier curves, spawning vertical aligned junction nodes or auto-wiring shared machine pools.

---

## 8. Category Default Machine Preset System (`CategoryMachinePreset`, `CategoryMachinePresetManager`)

Remembers preferred machine models, voltage tiers, parallel factors, overclock modes, and hardware addons per recipe category (`categoryId`), automatically applying them when placing new nodes.

* **Domain Entity (`CategoryMachinePreset`)**:
  - Encapsulates machine icon, multiblock state, target voltage tier, parallel count, overclock mode, steam mode, addons, node properties, and threading configuration bound to a recipe category.
  - `applyTo(RecipeNode node)`: Injects preset configuration into new nodes while safely preserving the minimum required voltage tier of the recipe.
* **Preset Manager (`CategoryMachinePresetManager`)**:
  - Singleton in-memory registry and NBT persistence manager (`serializeNBT` / `deserializeNBT`).
  - Provides CRUD interfaces via `BoardSettingsDialog` and `MachineConfigDialog`.

---

> ➡️ **Next Chapter**: [[02] Mathematical Engine & Graph Analysis Algorithms](02_MATH_AND_ALGORITHMS.md)
