# GregTech Calculator Board - Architecture & Developer Guide

<p align="center">
  <b>English</b> | <a href="ARCHITECTURE_KR.md">한국어</a>
</p>

> 📘 **Detailed Technical Specification Series**:
> * 🇰🇷 **Korean Edition**: [docs/ko_kr/CODE_SPECIFICATION.md](ko_kr/CODE_SPECIFICATION.md)
> * 🇺🇸 **English Edition**: [docs/en_us/CODE_SPECIFICATION.md](en_us/CODE_SPECIFICATION.md)
> The complete v2.3.0 architecture specifications, 5 graph algorithms, Gauss-Jordan mass balance linear solver, `CategoryCapabilityMatrix`, and 2-tier on-demand streaming protocol are documented in the links above.

This document describes the internal architecture, mathematical solver engine, canvas rendering pipeline, and multi-mod compatibility layer (SPI) of **GregTech Calculator Board**.

---

## 1. System Overview

GTCalcBoard is architected into 5 strictly isolated layers following **Clean Architecture** and **Service Provider Interface (SPI)** principles:

```mermaid
graph TD
    subgraph UI["1. Presentation & UI Layer (com.gtceu.calcboard.client.gui)"]
        BS["BoardScreen (Main Screen Orchestrator & Input Router)"]
        BDM["BoardDialogManager & ModalStack (LIFO Modal Lifecycle & ESC Dismissal)"]
        BCR["BoardCanvasRenderer (Viewport Culling & Canvas Rendering Coordinator)"]
        BAH["BoardActionHandler (Undo/Redo Actions & Node/Wire Removal Collector)"]
        BVT["BoardViewportTransform (Virtual GUI Scale Coordinate Transform Engine)"]
        CIH["CanvasInteractionHandler & CanvasStateMachine (FSM-Driven Mutual Exclusion)"]
        NLB["NodeLayoutBounds & NodeLayoutCalculator (Single-Source Hitbox & Layout Model)"]
        RP["RenderProfiler (F3 Real-Time Rendering & Solver Latency Profiler HUD)"]
        CUN["ClientUpdateNotifier (Background Check & In-Game Update Notification Badge)"]
        RENDER["Two-Pass Z-Order Rendering & Rate-Based Flow Wire Animation Shader"]
        WSI["WireSpatialIndex (128x128 AABB Uniform Grid O(log E) Spatial Indexing)"]
        NCTC["NodeCardTextCache (Dirty-Flag Based Text Truncation & Formatting Cache)"]
        Widgets["widget.* (NodeWidget, ToolbarWidget, PageTabBarWidget, HotkeyHudWidget, SummaryOverlay, FavoritesDockWidget)"]
        Dialogs["dialog.* (BoardSettingsDialog, MachineConfigDialog, BOMDialog, SearchDialog, GlobalBalanceDialog, JunctionSupplyDialog, FrameEditDialog)"]
        Search["search.* (RecipeSearchCacheManager, RecipeSearchQueryEngine & Composable Specification)"]
    end

    subgraph Core["2. Core Domain & Math Engine (com.gtceu.calcboard.api)"]
        Storage["storage.* (BoardManager, BoardPage, HistoryManager, BlueprintCodec, RecipeNodeSerializer)"]
        Preset["preset.* (CategoryMachinePreset, CategoryMachinePresetManager)"]
        Model["model.* (RecipeNode, ConnectionEdge, IngredientStack, CanvasGroupFrame, NodeRateCalculator, NodeWorkstationResolver)"]
        Solver["solver.* (FlowGraph, FlowGraphSolver, MassBalanceSolver, FlowBalanceMatrixSolver, FlowEdgeAllocator, FlowGraphModuleHandler)"]
        Linear["solver.linear.* (TwoStageLinearFlowSolver, GaussJordanEliminator, LinearEquationSystem)"]
        Stability["solver.* (ProcessStabilityAnalyzer, HarmonizedRatioOptimizer, AutoRatioEngine)"]
        Catalog["catalog.* (CapabilityMatrix, MachineAddonCatalog, PartCategory, MultiblockDetector)"]
        Type["type.* (GTVoltageTier, OverclockMode, EnergyType, SteamMode, FluidUnitMode, WireColorPreset, WireAnimationMode, SupplyMode, FlowSplitMode)"]
        Prop["property.* (NodeProperties, NodePropertyStore, NodeBadgeRegistry)"]
        SPI["spi.* (ModAdapterRegistry, IModAdapter, IModExtension, Providers)"]
    end

    subgraph Compat["3. Mod Compatibility Implementations (com.gtceu.calcboard.compat)"]
        subgraph Adapters["Domain Mod Adapters (100% Headless Safe)"]
            GT["gtceu (GTCEuMachineAnalyzer, physics.GTBoilerPhysics, physics.GTTurbinePhysics, physics.GTFusionHelper, helper.GTCombustionHelper, BOMResolver)"]
            CR_MOD["create (CreateSequencedRecipeExtractor, RPM/SU, Kinetic Machines)"]
            CDG["createdieselgenerators (Diesel Engines, SU/Fuel, Distillation)"]
            CNA["createnewage (Motors, Generator Coils, Magnet Rings, FE/SU Conversion)"]
            GR["greate (Tiered Kinetic Machines)"]
            TH["thermal (AugmentData, Tier Kits, Dynamos, RF/t)"]
            SY["systeams (Boilers, Steam Dynamos, Steam mB/s)"]
            ST["start (StarTReflectionBridge, Plasma Turbines, Threading Helix Structures, SPT/NPT Traits)"]
            TFG["tfg (TerraFirmaGreg Large Boilers, physics.TFGBoilerPhysics, Booster Fluids)"]
            VN["vanilla (Passive Unpowered Fallback)"]
        end
        SPI --> Adapters
    end

    subgraph ServerNet["4. Multiplayer Server & Network (server / network)"]
        NH["NetworkHandler (8 C2S / 9 S2C SimpleChannel Packets)"]
        PAGING["2-Tier On-Demand Paging (Lightweight Metadata + Lazy Load)"]
        CHUNK["512KB Chunked Streamer (Large NBT Stream Fragmentation)"]
        WLM["WorkspaceLockManager (Distributed Lease Locks & Optimistic Revisions)"]
        TBSD["TeamBoardSavedData (DimensionDataStorage NBT Persistence)"]
        TPR["ITeamProvider (FTB Teams, Phoenix Guilds, Vanilla Scoreboards)"]
    end

    subgraph Integration["5. External Recipe Viewer SPI (com.gtceu.calcboard.integration)"]
        RVR["RecipeViewerRegistry (Priority Viewer Election)"]
        IVA["IRecipeViewerAdapter (Common SPI Interface)"]
        subgraph Viewers["Recipe Viewer Adapters"]
            EMI_AD["EmiRecipeViewerAdapter (Priority: 100)"]
            JEI_AD["JeiRecipeViewerAdapter (Priority: 50)"]
            VAN_AD["VanillaRecipeViewerAdapter (Priority: 0 Fallback)"]
        end
        CCM["CategoryCapabilityMatrix (Pre-Baked O(1) Capability Cache)"]
        RVR --> IVA
        IVA --> Viewers
    end

    UI -->|Dispatch User Interactions & Batch Render| Core
    UI -->|Send & Receive Packets| ServerNet
    UI -->|Search & BoM Sync Requests| RVR
    ServerNet -->|Load & Store Domain Models| Core
    Core -->|Delegate Machine Rules & Physics| Compat
    Integration -->|Load Recipe Data| Core
```

The Core Domain Engine (`com.gtceu.calcboard.api`) and Common Mod Adapters (`com.gtceu.calcboard.compat`) maintain **0% dependency on Minecraft client GUI classes**, guaranteeing independent execution and 100% test pass rates under standard headless JVM environments.

---

## 2. Core Architectural Principles

### 2.1 Pure Domain Model (`RecipeNode`)
`RecipeNode` is an engine-level **pure domain data entity** representing all machines, generators, and compound modules on the canvas:
* **Zero Mod Coupling**: Contains no hardcoded mod branches or third-party classes.
* **Lifecycle & Physical Delegation**: Icon changes (`setMachineIcon`), energy types (`getEnergyType`), power calculations (`getSingleMachineEUt`), operational validation (`validateNode`), and multiblock BOM construction (`buildMultiblockBOM`) are delegated dynamically via `ModAdapterRegistry.getAdapterForNode(node)`.
* **Type-Safe Dynamic Properties**: Mod-specific metadata is encapsulated in `NodePropertyStore` using `NodeProperties`.
* **Immutable View Encapsulation (`FlowGraph`)**: Graph topologies are protected via `Collections.unmodifiableList` and indexed via an internal $O(1)$ `nodeMap`.

### 2.2 Mathematical Solver & Gauss-Jordan Mass Balance (`MassBalanceSolver`)
* **Gauss-Jordan Linear System ($A\mathbf{x} = \mathbf{b}$)**: Solves closed-loop recycling circuits with partial pivoting to guarantee exact mass conservation across complex chemical loops.
* **10-Pass Fixed-Point Relaxation**: Iteratively converges machine steady-state utilization efficiencies ($\eta \in [0.0, 1.0]$) under upstream supply limits.

### 2.3 High-Performance Rendering & Virtual Viewport Scaling Engine
* **Two-Pass Z-Order Rendering & `glClear` Depth Buffer Isolation**: Eliminates 3D item model and 2D background Z-clipping through per-node depth buffer clearing, rendering active/selected nodes in a deferred pass to guarantee strict visual layering.
* **Virtual Viewport Scaling Engine (`BoardViewportTransform`)**: Provides decoupled board-specific virtual GUI scaling ($S = \text{BoardScale} / \text{GameScale}$) to optimize workspace canvas real-estate across both low-DPI and high-DPI displays.
* **$128 \times 128$ AABB Uniform Grid (`WireSpatialIndex`)**: Accelerates wire hover and hit testing from $O(E)$ down to **$O(\log E)$** spatial search.
* **$O(1)$ Port Flow Caching & Text Memoization (`NodeCardTextCache`)**: Protects per-frame rendering operations through precomputed caching, sustaining 60+ FPS frame rates even on large graphs.

### 2.4 Multiplayer Streaming & Distributed Locks
* **2-Tier On-Demand Paging**: Open boards synchronize lightweight metadata (`S2CSyncWorkspaceMetaPacket`) first, loading detailed graph NBTs only upon tab activation.
* **512KB Chunked Streaming (`S2CChunkedDataPacket`)**: Splits large payloads into 512KB chunks, eliminating Netty 2MB buffer overflow crashes.
* **Distributed Lease Locks (`WorkspaceLockManager`)**: Prevents concurrent editing conflicts via 300s lease timeouts and optimistic revision validation.

### 2.5 Deductive Analysis Policy (Rule 5)
* **Zero Heuristics**: String matching, item names, and tooltip parsing (`id.getPath().contains(...)`) are strictly prohibited.
* **3-Step Deterministic Deduction**:
  1. Official APIs & Java Reflection.
  2. Physics Simulations & Internal Objects.
  3. Deterministic NBT numerical data structures & official `TagKey` lookups.

### 2.6 Hierarchical Modal Dialog Stack & Canvas Interaction FSM (ADR-026 & ADR-027)
* **LIFO Modal Stack (`ModalStack` & `IBoardModal`)**: Manages 26 workspace overlay dialogs with strict LIFO ordering, sequential ESC dismissal, and input isolation against ghost clicks.
* **Finite State Machine (`CanvasStateMachine`)**: Enforces mutually exclusive interaction states (IDLE, DRAGGING_NODES, WIRING, BOX_SELECTING, RESIZING, PANNING) with clean rollback on abort.

### 2.7 Composable Recipe Search & Extension Object SPI (ADR-028 & ADR-029)
* **Specification Pattern Query Engine (`RecipeSearchQueryEngine`)**: Decomposes recipe search into composable predicates (`@mod`, `#tag`, `tier:`, `eut:`) with memoized token indexing.
* **Interface Segregation & Extension Object Pattern (`IModAdapter`)**: Core lifecycle reduced to 86 lines; domain capabilities partitioned into 6 modular SPI providers (`IEnergySimulationProvider`, `ICompoundRecipeProvider`, `IHardwareAddonProvider`, `IMultiblockBOMProvider`, `IBoosterProvider`, `ICapabilityMatrixProvider`) with 100% backward compatibility.

### 2.8 Single-Source Node Layout Bounds Model (`NodeLayoutBounds`, ADR-030)
* **Decoupled Renderer & Hit Testing**: Hardcoded coordinates and offsets shared between card renderers and hit detection are consolidated into an immutable `NodeLayoutBounds` and `NodeLayoutCalculator` single source of truth.
* **Slim Mode Layout Integrity**: Guarantees exact coordinate parity for ports, drag handles, and hitboxes across Standard and Slim modes with $O(1)$ hit testing.

### 2.9 Shared Machine Pool Capacity Scaling & Stability Matrix (ADR-031 ~ ADR-033)
* **Shared Machine Pool Scaling (`CanvasGroupFrame`)**: Scales all connected processes proportionally ($S = M_{\text{target}} / D_{\text{current}}$) to match physical machine capacity ($M_{\text{target}}$, default 1.0) on multi-process frame setups.
* **Comprehensive Stability Defense Matrix (`ProcessStabilityAnalyzer`)**: Protects closed loops, positive feedback growth, catalyst decay, and conflicting anchors against infinite scaling runaway, presenting contextual warning badges (`[⚠ Loop]`, `[⚠ Growth]`) and actionable 5-line diagnostic tooltips via `NodeBadgeRegistry`.

### 2.10 Two-Stage Linear Flow Balance Solver & Junction Anchoring (ADR-034 & ADR-035)
* **Two-Stage Linear Flow Solver (`TwoStageLinearFlowSolver`)**: Combines continuous Gauss-Jordan flow solving with integer ceiling quantization to achieve single-click deterministic mass balance convergence across complex cyclic networks.
* **Junction Buffer Wiring & Anchoring**: Enables port context dragging for 1-click creation of surplus drain, deficit supply, and void sink junctions, alongside pinning fixed junction nodes as anchors to drive upstream/downstream rate calculations.

### 2.11 Domain Purity & SPI Separation (`com.gtceu.calcboard.api.spi`, ADR-037)
* **SPI Decoupling**: Relocated `ModAdapterRegistry` and `IModAdapter` to `api.spi` to eliminate reverse architectural dependencies between API and compat layers.
* **Pure Domain Models**: Completely stripped third-party mod field artifacts from `RecipeNode`, managing mode states, validation, and energy models strictly through SPI adapters and `NodePropertyStore`.

### 2.12 Simulation Purity & Invariant Protection (ADR-038)
* **Zero Side-Effect Solvers**: Guarantees mathematical calculation purity with 0 mutation of node ports or topology during graph evaluation.
* **Compound Module Scale Preservation**: Restores sub-process node scaling factors deterministically across module collapse and expand lifecycles.

### 2.13 Precision Cache Invalidation & Rendering Lifecycle (ADR-039)
* **Isolated Invalidation Boundaries**: Dragging, resizing, or recoloring sticky notes and group frames updates local visual bounds without triggering global flow balance recalculation or node card text cache eviction.
* **Cached Reflection & Search Acceleration**: Eliminates per-frame keyboard focus reflection overhead in recipe viewers (JEI/EMI) and leverages pre-indexed spatial bounds for immediate frame and auto-connect lookups.

### 2.14 Split Modes & Hierarchical Priority Flow Allocation (ADR-041)
* **Tri-State Split Modes (`FlowSplitMode`)**: Supports `PROPORTIONAL` (demand-weighted), `EQUAL` ($1/N$ uniform division), and `WEIGHTED` (user-defined explicit branch weights) split modes on junction nodes.
* **Hierarchical Priority Cascades (`FlowEdgeAllocator`)**: Wires carry an integer `priority` tier; higher-priority consumers are satisfied first, while residual flow within each priority tier is distributed according to the junction's split mode.

### 2.15 Shared Machine Pool In-Place Folding & Ratio Preservation (ADR-042)
* **Topology-Preserving In-Place Folding**: Collapses multi-recipe shared machine pool frames into a single compact virtual machine card without removing or modifying internal nodes or connections.
* **Proportional Scaling & Deficit Gating**: Preserves internal recipe duty ratios during machine count adjustments, while aggregating boundary I/O ports via `FlowGraphTopologyAnalyzer` and signaling upstream supply deficits.

### 2.16 Dedicated Sub-Page Composite Modules & Boundary I/O Pins (ADR-043)
* **1:1 Dedicated Sub-Page Isolation**: Upgrades compound modules to independent sub-pages (`PageType.MODULE`) accessible via double-click with breadcrumb and Escape navigation.
* **Boundary Pin Domain Contracts (`BoundaryPinNode`)**: Defines explicit `ModuleInputPin` and `ModuleOutputPin` boundary interface nodes for sub-pages, eliminating ambiguous topological inferences.

### 2.17 Damped Recirculation Loop Closed-Form Solver & Steady-State Visualization (ADR-044)
* **Infinite Geometric Series Closed-Form Convergence**: Solves steady-state recirculating supply via $S_{\text{steady}} = \frac{S_{\text{ext}}}{1 - r}$ in $O(1)$ without artificial deficit warnings.
* **Steady-State Operational Visualization**: Displays cyan circulating indicators for balanced recirculation loops and provides 1-click machine scaling to steady-state capacity.

### 2.18 RecipeNode Role Composition Decomposition (ADR-045)
* **INodeRole Composition**: Decomposes `RecipeNode` into a slim canvas entity composing specialized operational roles (`MachineNodeRole`, `SubPageModuleNodeRole`, `JunctionNodeRole`, `BoundaryPinNodeRole`).
* **Dual-Write NBT Backward Compatibility**: Maintains full read/write serialization compatibility with legacy blueprints and storage tags without data loss.
* **Immutable Calculation Snapshots**: Captures lock-free `NodeCalculationSnapshot` and `FlowGraphSnapshot` records to decouple background math calculations from canvas rendering.

### 2.19 Deterministic Spec Deduction & Compat Normalization (ADR-047)
* **Zero String Heuristics (Rule 5 Compliance)**: Eliminates legacy `contains` substring lookups across Create sequenced assembly, threading helix modifiers, offline energy hatch tiers, and Thermal dynamos.
* **Exact-Match Mapping & Strong Types**: Employs immutable identifier lookup tables (`Map<ResourceLocation, T>`) and static reflection caches (`Class.isAssignableFrom`) for deterministic behavior across custom modpacks.

### 2.20 Page Target Voltage Tier & Multiblock Auto-Provisioning (ADR-048)
* **Page-Level Target Voltage (`defaultVoltageTier`)**: Enables per-page default operating tiers, automatically upgrading singleblock machines and auto-equipping matching energy hatches on multiblock structures upon recipe insertion.
* **Transactional Batch Application**: Supports 1-click page-wide tier synchronization via `BatchChangeTierCommand` with full atomic Undo/Redo integration.

### 2.21 Machine & Recipe Transition Hardware Reconciler (ADR-049)
* **Idempotent Transition Pipeline (`NodeHardwareReconciler`)**: Standardizes hardware adaptation when switching recipes or machine models, purging incompatible addons, clamping voltage tiers, and maintaining lifecycle parity.
* **Complete Hardware Mementos**: Extends `SwitchRecipeCommand` to capture full snapshots of machine icons, multiblock state, parallel values, and installed addons for lossless Undo/Redo restoration.

### 2.22 Immutable Recipe Specification & Dynamic Port Projection (ADR-050)
* **Immutable Recipe Spec (`RecipeSpec`)**: Preserves pristine base recipe inputs and outputs, preventing loss of original ingredients when toggling machine models or addons.
* **Dynamic Hardware Port Projection (`IPortProjectionProvider`)**: Dynamically projects auxiliary fluid ports (steam, oxidizer, coolant) via pure derivation while isolating core recipe port indices (0..N-1) to protect existing wire topologies.

### 2.23 Star Technology Modular Combustion Complex (MCF) Integration (ADR-013)
* **Single Macro Node Model**: Models Star Technology's Modular Combustion Frame and up to 8 docked combustion/rocket modules within a unified macro node.
* **Centralized Coolant Consumption**: Derives common coolant demand proportionally across active module slots into a single external port, while aggregating complete frame and module blocks in the Multiblock BOM.

### 2.24 Team Workspace Common Domain Models & Layer Inversion Resolution (ADR-051)
* **API Domain Common DTOs**: Relocated `TeamWorkspacePage` and `CommitLogEntry` to `com.gtceu.calcboard.api.team`, eliminating reverse dependencies from Client GUI classes to Server Storage.
* **Strict Unidirectional Layer Boundaries**: Enforces `Client -> API/Team/Net`, `Server -> API/Team`, and `Network -> API/Team` contracts with zero direct client-server cross-coupling.

### 2.25 Chunked Payload Reception Upper-Bound Guard & DoS Defense (ADR-052)
* **Payload Size & Chunk Bounds**: `ServerChunkedPayloadAssembler` enforces a hard upper bound of 128 chunks (64MB total payload) per assembly session to prevent server memory exhaustion.
* **Defensive Assembly State Guards**: Rejects out-of-order chunk sequences, negative indices, and oversize fragments with early error returns and session eviction.

### 2.26 NodeInspectorPanel SRP Decomposition into 4 Sub-Inspectors (ADR-053)
* **Single Responsibility Decomposition**: Refactored the monolithic 1,199-line inspector panel into a lightweight host container (170 lines) delegating to 4 focused sub-inspectors: `MachineNodeInspector`, `JunctionNodeInspector`, `BoundaryPinInspector`, and `PageSettingsInspector`.
* **Isolated Sub-Component State**: Encapsulates widget lifecycles, chip grids, and input validation within independent component boundaries.

### 2.27 Solver Control Flow Flattening & Guard Clauses (ADR-054)
* **Flattened Nesting**: Flattened deep loop and conditional nesting across `FlowSummaryAggregator` and `MassBalanceSolver` to a maximum depth of 2, enforcing early guard returns.
* **Shallow Mathematical Helpers**: Extracted complex equation solvers and balance checks into shallow, self-describing private utility methods.

### 2.28 RecipeNode Direct Memory Copy Constructor Optimization (ADR-055)
* **Zero-Serialization Direct Copying**: Replaced legacy `deserializeNBT(serializeNBT())` clipboard cloning with a dedicated copy constructor `RecipeNode(RecipeNode other, String newId, Set<FlowGraph> visitedGraphs, int depth)`.
* **Cycle Guard & Role Polymorphism**: Preserves immutable `baseSpec` references, performs polymorphic `INodeRole.copy()`, and prevents graph recursion through cycle detection sets and depth clamping.

### 2.29 3-Track Modular Academy & Contextual Tutorial Architecture (ADR-056)
* **3-Track Progressive Onboarding**: Structures onboarding into a 45-second beginner starter tutorial, 4 independent academy chapters (ratio solving, wiring, module subpages, workspace collaboration), and non-intrusive contextual nudges.
* **Step Result Feedback & Persistence**: Introduces step completion result states allowing players to review canvas outcomes, while persisting placed machines and connections across tutorial stages.

### 2.30 TerraFirmaGreg (TFG) Large Boiler Booster Mechanism & Non-Linear Physics (ADR-057)
* **Dedicated TFG Physical Model**: Simulates TFG Large Bronze (480PU) and Steel Boilers (1280PU) with 9 booster catalyst fluids and dual-fuel Super Boiler secondary mode.
* **Non-Linear Water Consumption Curve**: Implements non-linear water consumption scaling with a 1.5-power exponent above 480PU, preventing under-allocation and in-game boiler explosions.

### 2.31 Canvas Defensive State Copying & Modal Hotkey Isolation (ADR-058)
* **Defensive Element Snapshotting**: Canvas container returns defensive copies of child collections to prevent concurrent modification during page transitions and graph mutation.
* **Modal Hotkey Interception**: Blocks background canvas shortcut routing (Delete, Backspace, Ctrl+Z) while modal dialogs or configuration panels are active.

---

> 📑 **Detailed Specifications**:
> * [[00] System Overview](en_us/spec/00_OVERVIEW.md)
> * [[01] Core Domain Models](en_us/spec/01_CORE_DOMAIN_AND_MODELS.md)
> * [[02] Mathematical Engine & Algorithms](en_us/spec/02_MATH_AND_ALGORITHMS.md)
> * [[03] UI & Rendering Pipeline](en_us/spec/03_UI_AND_RENDERING_PIPELINE.md)
> * [[04] Multiplayer & Network Protocol](en_us/spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)
> * [[05] External Integration & i18n](en_us/spec/05_INTEGRATION_AND_I18N.md)
