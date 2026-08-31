# GregTech Calculator Board - Architecture & Developer Guide

<p align="center">
  <b>English</b> | <a href="ARCHITECTURE_KR.md">한국어</a>
</p>

> 📘 **Detailed Technical Specification Series**:
> * 🇰🇷 **Korean Edition**: [docs/ko_kr/CODE_SPECIFICATION.md](ko_kr/CODE_SPECIFICATION.md)
> * 🇺🇸 **English Edition**: [docs/en_us/CODE_SPECIFICATION.md](en_us/CODE_SPECIFICATION.md)
> The complete v2.0.0 architecture specifications, 5 graph algorithms, Gauss-Jordan mass balance linear solver, `CategoryCapabilityMatrix`, and 2-tier on-demand streaming protocol are documented in the links above.

This document describes the internal architecture, mathematical solver engine, canvas rendering pipeline, and multi-mod compatibility layer (SPI) of **GregTech Calculator Board**.

---

## 1. System Overview

GTCalcBoard is architected into 5 strictly isolated layers following **Clean Architecture** and **Service Provider Interface (SPI)** principles:

```mermaid
graph TD
    subgraph UI["1. Presentation & UI Layer (com.gtceu.calcboard.client.gui)"]
        BS["BoardScreen & Editor (2D Viewport, Camera Pan/Zoom, Matrix Transforms)"]
        CIH["CanvasInteractionHandler (CanvasPanZoomHandler, CanvasSelectionHandler, CanvasQuickAddMarkerHandler)"]
        BATCH["Single-Pass Batch Render (Single Geometry Draw Pass)"]
        WSI["WireSpatialIndex (128x128 AABB Uniform Grid O(log E))"]
        Widgets["widget.* (NodeWidget, HiddenPortsPopup, ToolbarWidget, PageTabBarWidget, HotkeyHudWidget)"]
        Editors["editor.* (InlineTextEditor Engine, NodeCountEditor, NodeNameEditor, NodeParallelEditor, NodeTargetBatchEditor)"]
        Dialogs["dialog.* (BoardSettingsDialog, MachineConfigDialog, BOMDialog, SearchDialog, GlobalBalanceDialog)"]
        Search["search.* (RecipeSearchCacheManager, RecipeSearchQueryEngine)"]
    end

    subgraph Core["2. Core Domain & Math Engine (com.gtceu.calcboard.api)"]
        Storage["storage.* (BoardManager, BoardPage, HistoryManager, BlueprintCodec)"]
        Preset["preset.* (CategoryMachinePreset, CategoryMachinePresetManager)"]
        Model["model.* (RecipeNode, ConnectionEdge, IngredientStack, NodeRateCalculator, NodeWorkstationResolver)"]
        Solver["solver.* (FlowGraph, FlowGraphSolver, MassBalanceSolver, FlowBalanceMatrixSolver, FlowGraphTopologyAnalyzer, FlowSummaryAggregator, ProductionETACalculator)"]
        Catalog["catalog.* (CapabilityMatrix, MachineAddonCatalog, PartCategory)"]
        Type["type.* (GTVoltageTier, OverclockMode, EnergyType, SteamMode, FluidUnitMode, WireColorPreset)"]
        Prop["property.* (NodeProperties, NodePropertyStore)"]
    end

    subgraph Compat["3. Mod Compatibility Common SPI (com.gtceu.calcboard.compat)"]
        MAR["ModAdapterRegistry (Priority Dynamic Routing SPI)"]
        IMA["IModAdapter (Lifecycle, Overclock, Energy, BOM Contribution & Validation)"]
        subgraph Adapters["Domain Mod Adapters (100% Headless Safe)"]
            GT["gtceu (physics.GTBoilerPhysics, physics.GTTurbinePhysics, BOMResolver)"]
            CR_MOD["create (RPM/SU, Stress Capacity, Kinetic Machines)"]
            CNA["createnewage (Motors, Generator Coils, Magnet Rings, FE/SU Conversion)"]
            TH["thermal (AugmentData, Tier Kits, Dynamos, RF/t)"]
            SY["systeams (Boilers, Steam Dynamos, Steam mB/s)"]
            ST["start (Plasma Turbines, Threading Helix Structures, SPT/NPT Traits)"]
            VN["vanilla (Passive Unpowered Fallback)"]
        end
        MAR --> IMA
        IMA --> Adapters
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

### 2.3 High-Performance Rendering & Spatial Indexing
* **Single-Pass Batch Rendering**: Culls off-screen elements and renders all visible cards and wires in a unified geometry batch.
* **$128 \times 128$ AABB Uniform Grid (`WireSpatialIndex`)**: Accelerates wire hover and hit testing from $O(E)$ down to **$O(\log E)$**.

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

---

> 📑 **Detailed Specifications**:
> * [[00] System Overview](en_us/spec/00_OVERVIEW.md)
> * [[01] Core Domain Models](en_us/spec/01_CORE_DOMAIN_AND_MODELS.md)
> * [[02] Mathematical Engine & Algorithms](en_us/spec/02_MATH_AND_ALGORITHMS.md)
> * [[03] UI & Rendering Pipeline](en_us/spec/03_UI_AND_RENDERING_PIPELINE.md)
> * [[04] Multiplayer & Network Protocol](en_us/spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)
> * [[05] External Integration & i18n](en_us/spec/05_INTEGRATION_AND_I18N.md)
