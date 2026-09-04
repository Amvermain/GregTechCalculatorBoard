# GregTech Calculator Board (GTCalcBoard) Technical Specification Series

This document serves as the official Master Index for the **GregTech Calculator Board (GTCalcBoard)** developer specifications, detailing the internal system architecture, core mathematical solvers, 5 graph algorithms, Gauss-Jordan mass balance linear solver, rendering pipeline, UI component wireframes, 2-tier on-demand streaming protocol, and server persistence schemas.

---

## 📌 Document Metadata

| Item | Specification |
| :--- | :--- |
| **Document Version** | `v2.1.0` (Aligned with ADR-001 ~ ADR-019) |
| **Target Platform** | Minecraft 1.20.1 (Minecraft Forge 47.2.0+) |
| **Dependencies** | Java 17+, GregTech CEu Modern, EMI / JEI (Recipe Viewer) |
| **Soft Dependencies** | FTB Teams, Phoenix Guilds (Multiplayer Team Integration) |
| **Language** | English (`en_us`) |
| **ADR Index** | **[Architecture Decision Records Repository (docs/adr/)](../adr/README.md)** |

---

## 📑 Specification Table of Contents

```
docs/en_us/
├── CODE_SPECIFICATION.md                    # 📑 [English Master Index]
├── TEST_CHECKLIST.md                        # 🧪 [English QA Test Checklist]
├── ARCHITECTURE.md                          # 🏛️ [English Architecture Guide]
└── spec/
    ├── [00] System Architecture & Design Principles ─────► 00_OVERVIEW.md
    ├── [01] Core Domain Models & Capability Matrix ──────► 01_CORE_DOMAIN_AND_MODELS.md
    ├── [02] Math Engine & Graph Solving Algorithms ──────► 02_MATH_AND_ALGORITHMS.md
    ├── [03] UI & Rendering Pipeline Overview ────────────► 03_UI_AND_RENDERING_PIPELINE.md
    │   ├── [03-01] 2D Canvas & Node Card Rendering ──────► 03_01_CANVAS_AND_NODE_CARDS.md
    │   ├── [03-02] Machine Config & Addon Rack UI ───────► 03_02_MACHINE_CONFIG_AND_ADDONS.md
    │   ├── [03-03] Page Summary & Dashboard UI ──────────► 03_03_PAGE_SUMMARY_AND_DASHBOARD.md
    │   └── [03-04] Recipe Search, Tools & Guide UI ──────► 03_04_RECIPE_SEARCH_AND_TOOLS.md
    ├── [04] Multiplayer Concurrency & Network ───────────► 04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md
    └── [05] External Mod Integrations & i18n Units ──────► 05_INTEGRATION_AND_I18N.md
```

---

## 📖 Chapter Summaries & Links

### 1. [[00] System Architecture & Design Principles](spec/00_OVERVIEW.md)
* **Vision & Values**: In-game self-sufficiency, mathematical integrity, frictionless multiplayer collaboration.
* **5-Tier Clean Architecture**: UI, Core, Compat SPI, Server/Network, Integration SPI layer separation.
* **Headless Isolation**: 100% independent unit testing and dedicated server crash safety.

### 2. [[01] Core Domain Models & Capability Matrix](spec/01_CORE_DOMAIN_AND_MODELS.md)
* **Core Data Models**: `GTVoltageTier` (15 tiers), `IngredientStack`, `RecipeNode` (pure domain entity), `FlowGraph` (immutable view encapsulation).
* **Capability Matrix (`CategoryCapabilityMatrix`)**: Pre-baked $O(1)$ capability cache.
* **Domain Helpers & Compound Modules**: `FlowGraphModuleHandler` (`Ctrl+G`), `BlueprintCodec`, `HistoryManager`.

### 3. [[02] Math Engine & Graph Solving Algorithms](spec/02_MATH_AND_ALGORITHMS.md)
* **Overclocking Formulas**: Standard/Perfect/Lossless multipliers, sub-tick batch promotion, CPS calculations.
* **Mass Balance Linear Solver (`MassBalanceSolver`)**: Closed-loop recycling solver with partial pivoting ($A\mathbf{x}=\mathbf{b}$).
* **5 Core Algorithms (`FlowGraphSolver`)**: 10-Pass Relaxation, PortFlowStats, Dual-Pass Auto-Ratio, Net Balance, Shift-Drag 1:1 Matching.

### 4. [[03] UI & Rendering Pipeline Overview](spec/03_UI_AND_RENDERING_PIPELINE.md)
* **[[03-01] 2D Canvas & Node Cards](spec/03_01_CANVAS_AND_NODE_CARDS.md)**: 2D viewport math, Single-Pass Batch Render, `WireSpatialIndex` AABB spatial grid, cubic Bézier spline rendering.
* **[[03-02] Machine Config & Addon Rack UI](spec/03_02_MACHINE_CONFIG_AND_ADDONS.md)**: Parallel multipliers, quick presets, installed addons tray, addon catalog browser.
* **[[03-03] Page Summary & Dashboard UI](spec/03_03_PAGE_SUMMARY_AND_DASHBOARD.md)**: `SummaryOverlay`, `GlobalBalanceDashboardDialog`, `ItemContributionPopup`.
* **[[03-04] Recipe Search, Tools & Guide UI](spec/03_04_RECIPE_SEARCH_AND_TOOLS.md)**: Parametric search dialog, category filter modal, toolbar widgets, HUDs, in-game guidebook, toasts, and interactive tutorial.

### 5. [[04] Multiplayer Concurrency & Network Protocol](spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)
* **Network Pipeline**: 8 C2S / 9 S2C packet definitions.
* **2-Tier On-Demand Paging & 512KB Chunked Streaming**: Large payload fragmentation and Netty buffer protection.
* **Distributed Lease Locks**: `WorkspaceLockManager` and `TeamBoardSavedData` NBT persistence.

### 6. [[05] External Mod Integrations & i18n Units](spec/05_INTEGRATION_AND_I18N.md)
* **Recipe Viewer SPI**: Runtime election across EMI, JEI, and Vanilla.
* **7 Mod Adapters**: GTCEu (physics separation), Create, Create New Age, Thermal Series, Systeams, Star Technology, Vanilla.
* **3-Step Deterministic Deduction (Rule 5)**: API/Reflection, Physics Simulation, Deterministic NBT/TagKey inspection.
