# GregTech Calculator Board (GTCalcBoard) Technical Specification Series

This document serves as the official Master Index for the **GregTech Calculator Board (GTCalcBoard)** architecture, core math engines, 5 graph-solving algorithms, rendering pipelines, UI component wireframes, multiplayer concurrency lock protocols, and persistence schemas.

---

## 📌 Document Metadata

| Field | Description |
| :--- | :--- |
| **Specification Version** | `2.0.0` (Base Commit: `6c63984`) |
| **Target Platform** | Minecraft 1.20.1 (Minecraft Forge 47.2.0+) |
| **Dependencies** | Java 17+, GregTech CEu Modern, EMI (Recipe Viewer) |
| **Soft Dependencies** | FTB Teams (Multiplayer team sync) |
| **Language** | English (`en_us`) / [한국어 (`ko_kr`)](../ko_kr/CODE_SPECIFICATION.md) |

---

## 📑 Full Table of Contents

```
docs/en_us/spec/
├── [00] System Architecture & Design Principles ───────► 00_OVERVIEW.md
├── [01] Core Domain Models & Capability Matrix ────────► 01_CORE_DOMAIN_AND_MODELS.md
├── [02] Math Engine & Graph Solving Algorithms ────────► 02_MATH_AND_ALGORITHMS.md
├── [03] UI & Rendering Pipeline Overview ──────────────► 03_UI_AND_RENDERING_PIPELINE.md
│   ├── [03-01] 2D Canvas & Node Card Rendering ────────► 03_01_CANVAS_AND_NODE_CARDS.md
│   ├── [03-02] Machine Config & Addon Rack UI ─────────► 03_02_MACHINE_CONFIG_AND_ADDONS.md
│   ├── [03-03] Page Summary & Global Balance Dashboard ► 03_03_PAGE_SUMMARY_AND_DASHBOARD.md
│   └── [03-04] Recipe Search, HUD, Guide & Toast ──────► 03_04_RECIPE_SEARCH_AND_TOOLS.md
├── [04] Multiplayer Concurrency & Network Protocols ──► 04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md
└── [05] External Mod Integrations & i18n Units ────────► 05_INTEGRATION_AND_I18N.md
```

---

## 📖 Chapter Summaries & Quick Links

### 1. [[00] System Architecture & Design Principles](spec/00_OVERVIEW.md)
* **Vision & Core Values**: In-game completeness, mathematical rigor, frictionless multiplayer collaboration.
* **4-Tier Architecture**: Clean separation between Presentation (UI), Core Domain/Math, Multiplayer/Persistence (Net/Server), and External Integration layers.
* **Headless Independence**: Pure JVM isolation allowing 100% test coverage without Minecraft client/server context.

### 2. [[01] Core Domain Models & Deterministic Capability Matrix](spec/01_CORE_DOMAIN_AND_MODELS.md)
* **Core Models**: `GTVoltageTier` (15 voltage tiers), `IngredientStack`, `RecipeNode`, `FlowGraph`, `BoardPage`.
* **Deterministic Capability Matrix (`CategoryCapabilityMatrix`)**: RFC-V2-005 pre-baking mechanism and $O(1)$ deductive metadata cache.
* **Domain Helpers & Compound Modules**: `CoilHelper`, `FlowGraphModuleHandler` (`Ctrl+G`), `BlueprintCodec` (NBT/GZIP/Base64), `HistoryManager`.

### 3. [[02] Math Engine & Graph Solving Algorithms](spec/02_MATH_AND_ALGORITHMS.md)
* **Overclock Formulas**: Standard, Perfect, and Lossless speed/power multipliers; sub-tick ($<1.0\text{ tick}$) batch promotion and CPS math.
* **5 Core Graph Algorithms (`FlowGraphSolver`)**: 10-Pass Fixed-Point Bottleneck Relaxation, PortFlowStats, Dual-Pass Auto-Ratio, Net Process Balance, Shift-Drag 1:1 Rate Matching.

### 4. [[03] UI & Rendering Pipeline Overview](spec/03_UI_AND_RENDERING_PIPELINE.md)
* **[[03-01] 2D Canvas & Node Cards](spec/03_01_CANVAS_AND_NODE_CARDS.md)**: 2D viewport coordinate transforms, Cubic Bézier wire rendering with flow pulses, Standard & Compound Module card HTML wireframes.
* **[[03-02] Machine Config & Addon Rack UI](spec/03_02_MACHINE_CONFIG_AND_ADDONS.md)**: Parallel numerical input, quick presets, active addon tray, coil/turbine/thermal catalog browser HTML wireframes.
* **[[03-03] Page Summary & Global Balance Dashboard](spec/03_03_PAGE_SUMMARY_AND_DASHBOARD.md)**: Real-time `SummaryOverlay`, multi-page `GlobalBalanceDashboardDialog`, `ItemContributionPopup` drill-down HTML wireframes.
* **[[03-04] Search, Filter, HUD, Guide & Toast](spec/03_04_RECIPE_SEARCH_AND_TOOLS.md)**: Parametric async recipe search, category filter dialog, Hotkey HUD widget, in-game `GuideDialog`, action toast notifications, and interactive tutorial system.

### 5. [[04] Multiplayer Concurrency & Network Protocols](spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)
* **Network Pipeline**: Forge `SimpleChannel` with 6 C2S and 4 S2C packet specifications.
* **Distributed Concurrency (`WorkspaceLockManager`)**: Lease-based (5-minute) soft locking, 30-second heartbeats, and optimistic revision conflict prevention.
* **Persistence Schema (`TeamBoardSavedData`)**: `<world>/data/gtcalcboard_workspaces.dat` NBT tag hierarchy and `ITeamProvider` abstraction.

### 6. [[05] External Mod Integrations & i18n Units](spec/05_INTEGRATION_AND_I18N.md)
* **Mod Compatibility Adapter System (`com.gtceu.calcboard.compat`)**: SPI priority routing, modular 3-tier sub-packages (`gtceu`, `create`, `thermal`, `systeams`, `vanilla`), dynamic crawler orchestration.
* **EMI Recipe Viewer**: `CalcBoardEmiPlugin` lifecycle hooks, asynchronous matrix baking trigger, `EmiRecipeConverter`.
* **Rate Units & Formatting**: `RateTimeUnit` (/t, /s, /min, /h, /d), `FormatUtil` SI power prefixes, bidirectional `en_us`/`ko_kr` synchronization.

---

## 🛠️ Verification & Testing Guide
Whenever core algorithms or UI logic are modified, execute `./gradlew test` to ensure 100% pass across all unit test suites.
