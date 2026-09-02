# GregTech Calculator Board Comprehensive Feature Verification Checklist (QA Test Checklist)

This document is the official QA verification checklist for `GregTechCalculatorBoard`, systematically validating all core features, mathematical calculation solvers, mod compatibility SPI layers, physical simulation models, UI/UX workflows, and internationalization (i18n) integrity.

---

## 1. Canvas Operations & Interactive Wiring (Canvas & Wiring)

### 1.1 Canvas Viewport Controls
- [ ] **Pan Navigation**: Verify smooth canvas dragging via mouse right-click drag.
- [ ] **Zoom Controls**: Verify seamless zooming between $25\% \sim 200\%$ via mouse wheel scroll.
- [ ] **Origin Reset**: Verify camera aligns to $(0, 0)$ on pressing `Space` or the reset origin button.
- [ ] **Fit to View (`Home` / `F`)**: Pressing `Home` or `F` automatically centers and scales camera to encompass all placed nodes.
- [ ] **Selection & Batch Operations**:
  - [ ] Single node click selection and drag movement.
  - [ ] Multi-node box selection and batch dragging.
  - [ ] `Ctrl + A` select-all functionality.
  - [ ] `Delete` / `Backspace` keys batch delete selected nodes/frames.
  - [ ] `Ctrl + Z` (Undo) and `Ctrl + Y` (Redo) history stack execution.

### 1.2 Wire Connections & Ratio Scaling
- [ ] **Basic Wiring**: Verify spline wire connects when clicking an output port (green) $\rightarrow$ input port (blue).
- [ ] **✨ Shift + Connect (1:1 Auto-Ratio Matching)**:
  - [ ] Holding `Shift` while connecting analyzes source production vs target consumption, automatically scaling target machine count 1:1 (e.g., 500 mB/s source $\rightarrow$ 100 mB/s consumer scales machine count to **5.0**).
- [ ] **Reroute Junctions**:
  - [ ] **Double-clicking** an existing wire immediately inserts a zero-cost [🔀 Reroute Junction] node.
  - [ ] Flow and item distribution split correctly through junction nodes to multiple consumers.
  - [ ] `Shift + Connect` from junction nodes calculates ratios based on diverted flow.
- [ ] **Wire Cutting**: Right-clicking a wire or clicking a connected socket port severs the connection immediately.
- [ ] **Drag-to-Search**:
  - [ ] Dragging from a port into empty canvas displays a 4-button quick action marker (🔍 Search, ➕ Junction, 📋 Copy, etc.).
  - [ ] Clicking 🔍 opens the recipe search dialog pre-filtered for consuming/producing recipes.
  - [ ] Selecting a recipe places the node and connects the wire automatically.

### 1.3 Page Tabs & Persistence / Sharing
- [ ] **Page Tab Management**:
  - [ ] `+` button creates a new canvas page.
  - [ ] Clicking tabs switches pages (retaining page camera pan & zoom levels).
  - [ ] Right-clicking tabs allows inline title renaming.
  - [ ] `x` button deletes tabs (with last tab protection).
  - [ ] **Tab Overflow Navigation**: Smooth scroll and `«`, `»` indicators when tabs overflow, with 16px right padding.
- [ ] **Save & Load**: `💾 Save` / `📂 Load` preserves all tabs, nodes, frames, notes, and wiring into NBT files (`calcboard_save.nbt`).
- [ ] **Blueprint Sharing**:
  - [ ] `📋 Share` exports the entire factory into a compressed Base64 string to the clipboard.
  - [ ] `📥 Import` restores external blueprints with 100% layout and parameter fidelity.

### 1.4 Recipe Switching & Accessibility
- [ ] **In-Place Recipe Switching**:
  - [ ] Clicking `[🔄 Switch Recipe]` in config dialogs displays alternative recipes for the same machine/products.
  - [ ] Existing wires on matching item/fluid ports are preserved automatically without disconnects.
  - [ ] `Ctrl+Z` / `Ctrl+Y` fully reverts and reapplies recipe switch operations.
- [ ] **Font & UI Scaling (5 Levels)**:
  - [ ] `[Aa 1.0x]` button, wheel scroll, and `+`/`-` shortcuts adjust UI scaling from 0.75x to 1.30x.
  - [ ] Click hitboxes align accurately across all scales via Matrix Scaling & Virtual Mouse inversion.
- [ ] **Global Fluid Unit Mode (FluidUnitMode)**:
  - [ ] Toolbar button or `Shift+T` cycles between `Auto` ➔ `Always mB` ➔ `Always B`.
  - [ ] Fluid numbers across all canvas nodes and tooltips unify immediately.

---

## 2. Calculation Solver & Overclocking & Math Engine (Solver & Math Engine)

### 2.1 Voltage Tiers & Overclocking Formulas (GTCEu)
- [ ] **Voltage Tier Differential ($\Delta\text{Tier}$)**: Verify $\Delta\text{Tier} = \max(0, \text{TargetTier} - \text{RecipeTier})$ from ULV to MAX.
- [ ] **Overclock Mode Scaling**:
  - [ ] **Standard Mode**: $+1\text{ Tier} = 4\times\text{ EU/t}, 2\times\text{ Speed}$ ($\text{EnergyFactor}=4.0, \text{SpeedFactor}=2.0$).
  - [ ] **Perfect Mode**: $+1\text{ Tier} = 4\times\text{ EU/t}, 4\times\text{ Speed}$ ($\text{EnergyFactor}=4.0, \text{SpeedFactor}=4.0$).
  - [ ] **Lossless Mode**: $+1\text{ Tier} = 1\times\text{ EU/t}, 1\times\text{ Speed}$ ($\text{EnergyFactor}=1.0, \text{SpeedFactor}=1.0$).
- [ ] **Sub-tick Execution ($< 1.0\text{ Tick}$)**:
  - [ ] When duration drops $< 1.0\text{ tick}$, promote to engine batching: $\text{BatchesPerTick} = \frac{1.0}{\text{Duration}}$ and fix effective duration to $1.0\text{ tick}$.
  - [ ] Verify $\text{Effective EU/t} = \text{Calculated EU/t} \times \text{BatchesPerTick}$.
  - [ ] Verify exact cycles per second: $\text{CPS} = 20.0 \times \text{BatchesPerTick} \times \text{Parallel} \times \text{MachineCount}$.
- [ ] **Tier Chance Boost for Probabilistic Outputs**:
  - [ ] Verify boosted output chance on tier increase: $\text{Effective Chance} = \min(1.0, \text{BaseChance} + (\Delta\text{Tier} \times \text{TierChanceBoost}))$.

### 2.2 Gauss-Jordan Mass Conservation Solver (`MassBalanceSolver`)
- [ ] **Closed-Loop Linear Formulation ($A\mathbf{x} = \mathbf{b}$)**:
  - [ ] Formulate mass balance ($ \text{Production} - \text{Consumption} = 0 $) across $M$ connected internal intermediates plus anchor machine constraint.
- [ ] **Gauss-Jordan Elimination with Partial Pivoting ($O(N^3)$)**:
  - [ ] Detect singular/under-determined pivots ($|A_{pk}| < 10^{-9}$) and calculate robust machine count vector $\mathbf{x}$.
  - [ ] Validate complex closed loops (e.g. ethylbenzene recycling, platinum group refinement) traversing reroute junctions.
- [ ] **10-Pass Fixed-Point Bottleneck Relaxation**:
  - [ ] In supply-deficient DAGs, converge downstream machine efficiencies ($\eta_v \in [0.0, 1.0]$) within 10 iterations ($\Delta\eta < 10^{-4}$).
- [ ] **Target Batch ETA & Total Resource Integration (`ProductionETACalculator`)**:
  - [ ] Compute batch duration $T_{\text{ET}} = \frac{A_{\text{target}}}{\text{Rate}_{\text{in}}}$.
  - [ ] Compute total cumulative energy $E_{\text{total}} = \sum (n.\text{getTotalEUt}() \times 20 \times T_{\text{ET}})\text{ [EU]}$ and raw material totals.

### 2.3 Parallel Processing & Energy Hatch Capacity
- [ ] **Parallel Power Scaling**:
  - [ ] $\text{Total Power (EU/t)} = \text{Overclock EU/t} \times \text{Parallel}$.
  - [ ] **4x Parallel**: Requires $+1\text{ Tier}$ power (e.g. EV 1,920 EU/t $\rightarrow$ 4x requires 7,680 EU/t IV power).
  - [ ] **16x Parallel**: Requires $+2\text{ Tier}$ power (e.g. EV 1,920 EU/t $\rightarrow$ 16x requires 30,720 EU/t LuV power).
- [ ] **Parallel Control Hatches**:
  - [ ] 4x, 16x, 64x, 256x hatches scale outputs and power consumption proportionally.
- [ ] **Energy Hatch Capacity**:
  - [ ] `GTEnergyHatchAddon` calculates total capacity via voltage $\times$ amperage (1A, 2A, 4A, 16A).
  - [ ] **Dual Hatch Overclock**: Dual equal-tier energy hatches apply a $+1\text{ Tier}$ overclock boost.
  - [ ] **Asymmetrical Hatches**: Asymmetrical configurations evaluate effective tiers based on peak power capacity.

---

## 3. Mod Compatibility SPI Layers & Non-Standard Calculations (Mod Compat SPI)

### 3.1 GregTech CEu Modern
- [ ] **Heating Coil Physics Deductions**:
  - [ ] **Electric Blast Furnace (EBF)**: Calculate excess temperature $\Delta T = \max(0, T_{\text{coil}} - T_{\text{recipe}})$ and apply 5% compound discount per 900K ($0.95^{\lfloor \Delta T / 900 \rfloor}$).
  - [ ] **Pyrolyse Oven**: Verify duration multiplier $\text{DurationMult} = \frac{100.0}{\text{PyrolyseSpeed}\%}$.
  - [ ] **Cracking Unit**: Verify power multiplier $\text{EUtMult} = \frac{\text{CrackingEnergy}\%}{100.0}$.
  - [ ] **Multi Smelter**: Verify coil tier dedicated parallel scaling ($32\text{x}, 64\text{x}, 128\text{x}\dots$).
- [ ] **Large Turbines & Rotor Holder Physics (`GTTurbinePhysics`)**:
  - [ ] **Rotor Holder Throughput Cap**: Verify tier base capacity (EV 4,096 EU/t base, doubling per tier) and rotor power scaling $\lfloor \text{BaseCap} \times \frac{\text{RotorPower}}{100} \rfloor$.
  - [ ] **Rotor Efficiency & Holder Bonus**: Combine rotor base efficiency + holder tier bonus ($\Delta\text{Tier} \times 10\%$) to scale fuel cycle duration.
  - [ ] **Auto Parallel Tuning**: Verify $\text{Parallel} = \lceil \frac{\text{HolderMaxEUt}}{\text{RecipeBaseEUt}} \rceil$.
  - [ ] **Turbine Flow Deficit**: Detect input flow $< 100\%$ and apply power scaling and deficit warnings.
- [ ] **Steam Boilers & Throttle Physics (`GTBoilerPhysics`, `GTBoilerTier`)**:
  - [ ] **Boiler Ratings**: LP Bronze ($120\text{ L/s}$), HP Steel ($360\text{ L/s}$), L-Bronze ($16\text{k/s}$), L-Steel ($36\text{k/s}$), L-Titanium ($64\text{k/s}$), L-Tungstensteel ($128\text{k/s}$) solid vs liquid acceleration.
  - [ ] **Multiblock Throttle ($\theta \in [25\%, 100\%]$)**: Verify $\text{Effective Speed} = \text{TierSpeed} \times \frac{\theta}{100.0}$.
  - [ ] **Water to Steam 1:160 Expansion**: $\text{Water Rate (mB/t)} = \frac{\text{Steam Rate (mB/t)}}{160.0}$.
- [ ] **Fusion Reactor Physics (`GTFusionHelper`)**:
  - [ ] Start EU ignition tiering (Mk1 $\le 160\text{M}$, Mk2 $\le 320\text{M}$, Mk3 $\le 640\text{M}$, Mk4/Mk5).
  - [ ] Special Fusion Overclock: **$2\times\text{ Power}, 2\times\text{ Speed}$ (Energy Factor 2.0, Speed Factor 2.0)**.

### 3.2 Create
- [ ] **RPM-Based Non-Linear Scaling**:
  - [ ] Baseline 32 RPM: $\text{SpeedFactor} = \max\left(0.01, \frac{\text{RPM}}{32.0}\right)$.
  - [ ] Processing duration: $\text{Duration} = \frac{\text{BaseDuration}}{\text{SpeedFactor}}$.
  - [ ] Consumed stress: $\text{Effective SU} = \text{BaseSU} \times \text{SpeedFactor}$.
- [ ] **Fan Processing Fixed In-World Physics**:
  - [ ] Splashing/Washing, Smoking, Blasting, Haunting preserve fixed in-world processing duration (150 ticks = 7.5s) regardless of RPM.
- [ ] **Standard SU Burden by Recipe Type**:
  - [ ] Helve Hammer: 16x RPM ($512\text{ SU}$ at 32 RPM).
  - [ ] Press / Compacting / Rolling / Lathe / Centrifugation: 8x RPM ($256\text{ SU}$ at 32 RPM).
  - [ ] Milling / Mixing / Cutting / Vacuumizing: 4x RPM ($128\text{ SU}$ at 32 RPM).
  - [ ] Polishing: 2x RPM ($64\text{ SU}$ at 32 RPM).
- [ ] **Kinetic Generation & Power Conversion**:
  - [ ] Water Wheel (256 SU), Large Water Wheel (512 SU), Windmill Bearing (512 SU), Creative Motor (16,384 SU).
  - [ ] Steam Engine: $200\text{ mB/s}$ Steam consumption $\rightarrow +2,048\text{ SU}$ output.
  - [ ] Alternator ($\text{SU} \rightarrow \text{FE}$) and Electric Motor ($\text{FE} \rightarrow \text{SU}$).

### 3.3 Create: New Age
- [ ] **Magnet Ring 12-Slot Magnetic Force Compounding**:
  - [ ] Sum of up to 12 installed magnets: $\text{TotalStrength} = \sum_{i=1}^{12} F_{m, i}$.
- [ ] **Generator Coil $\text{SU} \rightarrow \text{FE}$ Conversion**:
  - [ ] Generated power: $\text{Generated FE/t} = \text{TotalStrength} \times |\text{RPM}| \times \text{suToEnergy}$ (deduced config ratio, default $\frac{15}{512}$).
  - [ ] Required stress: $\text{Required SU/t} = (24.0 + \text{TotalStrength}) \times |\text{RPM}|$.
- [ ] **Carbon Brushes & Generator Coil Multiblock BoM injection and Motor processing**.

### 3.4 Thermal Series
- [ ] **Upgrade Kit Base Scaling**:
  - [ ] Scale factor (1x to 4x) machine processing multiplier and augment slot expansion.
- [ ] **Machine vs Dynamo Augment Branching**:
  - [ ] Machine: $\text{Duration} = \frac{\text{BaseDuration}}{\text{ScaleFactor} \times \text{DurationMult}}$, $\text{Power} = \text{BasePower} \times \text{ScaleFactor} \times \text{PowerMult}$.
  - [ ] Dynamo: Duration reduces on power increase (fuel rate increases): $\text{Duration} = \text{BaseDuration} \times \frac{\text{FuelEnergyMult}}{\text{ScaleFactor} \times \text{PowerMult}}$.

### 3.5 Thermal Systeams
- [ ] **Dynamo $\leftrightarrow$ Boiler 1-Click Mode Switching**:
  - [ ] Preserve base RF fuel energy and dynamically reconstruct steam boiling recipes in real-time.
- [ ] **Fluid Boiling & Steam Ratio Physics**:
  - [ ] Query `STEAM_RATIO_*` and `SPEED_*`, applying fluid conversion ratio `inToOutRatio = 0.25` (1 Water $\rightarrow$ 4 Steam).
- [ ] **Steam Dynamo**: Steam consumption $\rightarrow 400\text{ RF/t}$ generation with augment scaling.

### 3.6 Star Technology
- [ ] **Threading Helix Non-Linear Math**:
  - [ ] Speed points: $\text{points} = \frac{\text{totalSpeed}}{100.0}$, $n = \frac{-1 + \sqrt{1 + 8 \cdot \text{points}}}{2}$.
  - [ ] Duration multiplier: $\text{DurationMult} = 0.5^n \times \sqrt{\text{EffectiveParallels}}$.
  - [ ] Energy efficiency: $\text{PowerMult} = \frac{30.0}{30.0 + \text{totalEfficiency}}$.
  - [ ] Parallels & Threads: $\text{EffectiveParallels} = 1 + \lfloor \frac{\text{totalParallels}}{20} \rfloor$, $\text{EffectiveThreads} = 1 + \lfloor \frac{\text{totalThreading}}{5} \rfloor$.
- [ ] **Ultra-High-Voltage Plasma Turbine Models**:
  - [ ] Supreme Plasma Turbine (SPT, 6x parallel, $98,304\text{ EU/t}$ base).
  - [ ] Nyinsane Plasma Turbine (NPT, 12x parallel, $196,608\text{ EU/t}$ base).

---

## 4. Group Frames & Compound Modules (Group Frames & Compound Modules)

### 4.1 Group Frames
- [ ] **Frame Creation (`Ctrl + G`)**: Bounding box encapsulates selected nodes with header dragging moving all internal nodes.
- [ ] **Titles & Sticky Notes**: Inline title editing and sticky note annotation persistence.
- [ ] **Palette Themes (🎨)**: Theme button updates border and background fill colors.
- [ ] **Ungrouping**: Frame deletion safely preserves internal nodes.

### 4.2 Compound Module Packaging
- [ ] **Module Collapse (`📦`)**:
  - [ ] Collapses entire internal workflow into a **single module card**.
  - [ ] **Port Aggregation**: Only external-facing input/output ports are exposed on card boundaries.
  - [ ] **Internal Loop Hiding**: Intermediate intra-frame wires are cleanly encapsulated.
  - [ ] **Aggregated Metrics**: Top badge displays total power consumption/generation and machine counts.
- [ ] **Module Expansion (`⤢`)**:
  - [ ] Expanding the module restores original node positions, configurations, and wiring with **100% layout fidelity**.

---

## 5. Multiblock & Factory BOM Calculation (Multiblock & Factory BOM)

- [ ] **3D Structure Auto-Scanning**:
  - [ ] Accurately aggregates casings, heating coils, hatches/busses, and controllers for all placed multiblocks.
- [ ] **Hierarchical Compound Module BOM Scaling**:
  - [ ] Recursively compounds parent module machine counts ($P_{\text{child}} = P_{\text{parent}} \times M_{\text{module}}$) to scale nested sub-machine and multiblock part counts accurately.
- [ ] **Shared Machine Pool Frame Duty Aggregation**:
  - [ ] Calculates cumulative duty cycles across enclosed machines sharing physical hardware, quantizing to integral machines ($\lceil \sum \text{Duty} \rceil$) to prevent duplicate BOM parts.
- [ ] **Singleblock Voltage Tier Item Resolution & Usage Trace**:
  - [ ] Dynamically resolves singleblock machines into tier-specific items (e.g. `gtceu:lv_rock_breaker`) and lists contributing processes in `usedByMachines`.
- [ ] **Dual Hatch Optimization**:
  - [ ] Toggling between `1x Regular Tier Hatch` vs `2x Lower Tier Hatches` dynamically updates BoM items.
- [ ] **Material Filtering & Stack Breakdown**:
  - [ ] All / Casings / Coils / Hatches / Controllers tab filters.
  - [ ] Total item count displays inventory stack breakdowns (e.g. `2st + 15`).
- [ ] **Shopping List Export (`📋`)**:
  - [ ] Formatted markdown text copied directly to system clipboard.
- [ ] **EMI Integration (`Register in EMI`)**:
  - [ ] Batch registers BoM materials into EMI Recipe Tree / Bookmarks.

---

## 6. Recipe Search & Favorites (Recipe Search & Favorites)

### 6.1 Advanced Prefix & Boolean Search Engine
- [ ] **Mod Filter (`@`)**: `@gtceu`, `@thermal`, `@create_new_age` filter by namespace.
- [ ] **Tag Filter (`#`)**: `#forge:ingots`, `#forge:dusts` search item tags.
- [ ] **Category Filter (`[` or `%`)**: `[macerator]`, `[pyrolyse_oven]` filter by machine workstation.
- [ ] **Input/Output Filters (`in:`, `out:`, `>`, `<`)**:
  - [ ] `in:iron`, `>polyethylene` (input ingredient filter).
  - [ ] `out:steel`, `<oxygen` (output product filter).
- [ ] **Boolean Operators (`!`, `|`, `"..."`)**:
  - [ ] `!charcoal` (NOT operator).
  - [ ] `oil | creosote` (OR operator).
  - [ ] `"heavy fuel"` (exact phrase query).

### 6.2 Bilingual Search
- [ ] **Bilingual Indexing**:
  - [ ] Korean queries (`"물레방아"`, `"고급 모터"`, `"탄소 브러시"`) match localized registry names.
  - [ ] English fallback queries (`"water wheel"`, `"motor"`, `"coil"`) match simultaneously.

### 6.3 Favorites Dock
- [ ] **Bookmark Toggle (⭐)**: Clicking star icon in search or node cards registers/unregisters favorites.
- [ ] **Drag-to-Canvas**: Dragging recipes directly from the favorites dock onto canvas places configured nodes.

---

## 7. UI/UX, Internationalization & Dialogs (i18n & Dialogs)

- [ ] **Interactive 15s Tutorial**:
  - [ ] 7-step guided highlight tour (Node placement $\rightarrow$ Module packaging) operates smoothly.
  - [ ] Tutorial completion toast and badge award.
- [ ] **Global Balance Dashboard (`B` Key)**:
  - [ ] Factory-wide raw input and net product balance sheets aggregate accurately.
- [ ] **Board Settings Modal (`[⚙ Settings]`)**:
  - [ ] Fluid units (Auto/mB/B), time units, singleplayer pause, harmonize integer ratio limits persist across sessions.
- [ ] **Auto-Connect Filter Dialog (`AutoConnectFilterDialog`)**:
  - [ ] Checkbox selection for selective multi-port resource linking.
- [ ] **Blueprint Metadata & Import Preview (`[📥] / [📤]`)**:
  - [ ] Title/description/tags metadata and pre-import node/material summaries.
- [ ] **i18n Consistency**:
  - [ ] `en_us.json` and `ko_kr.json` format tokens (`%s`, `%d`) match with zero missing translation keys.
  - [ ] `testI18nCompletenessAndConsistency` unit test passes.

---

## 8. Ready-to-Use Test Preset Blueprints

Copy any of the compressed blueprint codes below and click **`📥 Import`** in the calculator board toolbar to instantly generate test workflows on your canvas.

### 📌 Preset 1: Petrochemical & Distillation Tower 16x Parallel Loop (GTCEu)
* **Verifies**: 16x parallel hatch, LuV energy hatch scaling, 4-byproduct distillation wiring, pyrolyse cracker linking, group frames.
```text
GTBOARD:H4sIAAAAAAAA/81Xz2/jRBSeNE2apomoKhaQ4ODDHtiDpfxw0mQ5kNKm20htqdp02Z7CZGacjNbxWPa43XAB7REhceaC+FP2T+ICV3jjiTc/1mmyP4TooVXtmXnf+973vXnOI7SNMq6gLMgjhNJZVOjjgB2FPpZcuC0LRT/baEuE0gtltCqVRVk8EqErW7+2ovfwgAyxS9jXf+oNObTp4hFD6W+5s40K2JHMd+HIWxbk1AloeyAJCx8L7uTQBqdz/2/KscdQ5vj0unMEobPcVZGjc9PoARGuxNxl9AyTIfw9VEAmMTm8RJ+8PqpHfe443B30fD6AmDw4drjnMQqAC6OZ7THsFMrx4EzQ0GFwXF5if8BklzMfbZw9TaOch33sOMxRSzXuh7UGrjYxtk2bVW3TKlt9s1GvlUyL1O3Gfs2y90kVjvIZ4R6Lj0qhHR48YS4DmoUPoYrilvnEEeQ5RIfYV92D86ODy6MUyvPgG6jIuXocpXDJfKgFgxQ2PRE8a3XQhJk8wT49YXwwVHS8BNrvhP88kFElJ7Qv4QYAKjCDcVcxv9M+bR92LzuHvfa1jnLTuonruqszOcSSDYQ/7tDlfBeAy9CRvK/yei2JXZCEcTRZaFzyQRptK+TfcSqHEOKvPMp7vvCYLzkLIM0tpcj2tWx9rzGgRZHeJIl0cypS/9X9Is2fMHw7No5DlqzVXZ3gUK3q2bBKlz7h8ZxypwDGaAWAU1W0lQActepNALOPlwDgKwBsnWNvKIc4MXpRh3H1Eh168dmSuDcr4hYumQ0e9MfGExwkBt/TgfzJut4ABxpB4ovFzoEpBekrOWyk0V7s3kiUnqO8iNCuaik+Js9Bje3IAxfgRBa1FJpGHwUjpkBdTJ0PO4ojPHCZ5ORYwNrZ5vOphsVue3G03hBLMsyhHeixxOeekiwqlusvjPjMALzrC2gEF+IuwgRxi5CYwwg87GqcQOIenSh+msCU0MxIUDBjJooPuAn4sctGnmoxoQ8Yt3JQSk9FOAROJIYM0+hjMmQjTrBz5TFGZzLPop0I0hl+oaz323zdvoihG3CW9IVjnKgkjS8hrUdZVGShnGJsleLmCr3soB8IR7UvRfskdhLtUfC2bXPCmUuUgaiu+3KCc2TSk6Z9GhL0xgBvHLDFBNEyQaTeXhDQ6jrBVdif9NrFm8gJb3u6vSZqoXAaPjXKB0ZU/Tek8DZlTyhw7iCSwIDpe3u+MtNjFLNL0N7DYQptdYJTaMXqCtuMhJqGZJL8kUIfdYKjEDunCqBemiDyZOG+s04f6ALG6tREP3oX6U3FVZglZzqe/OeD0Tqj0Gd6M+WBhPs2ElFPqgK83zCkizw7De1OpqFm2a5V6xXLpDX4ZbFSxWywft2s7ZOm1SS0sl/uL0xD7Q86DcnkaeiPxGloKT2r56Hu8nnoHtLnJqJUrIW9o5mlRle3gTVnot+XzETnSTNRav2ZaO9QtUBGjRWz0SRZolf3Fmeke14nT/nRXf0/Gt1+XOHjzJVkeJQYe0cHCdQCHXb+yVu7uThL5ntaeONkyfcM65dZGVtV067XGqZVaVKzT1jdtBqWTUv1Bm7apQUHn3xQB798lejgXxIdPE/Je9l2kd1kr25pX6xv0J9Q7MSs7cMJsRF1so9j92p4ram4BFDSmZ0SjTe7hHHH5dBQt9rcPRfNAOrjqv3U0GkG8Ud+LmJ2rY/W9Zr5eoJJowwRjvD/+erl31mUuVOstX5+NjN8PGyWcAWXrZJplylA6rOq2ahY1MRNWsHVOqUV2oRxR3IJWv78gsHUGV/iRvQVoRK+drkEqw4j0bRGaMoudiffItG/Pwgxij2yjXbAXC6MITz+VoDr3vbFKNLoWlxBftzt0Bc6n6wUeus6BKZRFuQ/2YvmIq+3PTHyekWZjaxJehaThP4FWTLUuRoSAAA=
```

---

### 📌 Preset 2: Create Kinetic Stress Units & New Age Electricity Generation
* **Verifies**: Large water wheel SU generation, generator coil FE conversion, advanced motor rotation, Create custom card controls.
```text
GTBOARD:H4sIAAAAAAAA/81WQY/bRBSebDZpkg0IBBUcQPIBIS6W1rHTOFUF2e4mELq7h03CtqdoPH5ORnU81niyaThVnDjyCxA/hZ/CT+DSM7yxk90NJKpLW9QcHHv85nvvfe/7bNcIqZJSJHxIaoSQYpnUPZrAyVxSxUXUcUj6q5I7Yq7iuUqjCmVSpjMxj1TneXYfF9iURgy++TNbqJD9iM6A1AdKQpIYo4irpErqNFQgI8S+gqSiochHTAJVcD9JA8dzHVghe9zfcWdfLWMg+/1h9wzLKvNIV5XmLJK7TESK8gj8M8qm+H+si1zVw/Em+XSFGVI5gfECT+V4MQUIC6TKk17I4xh8bKc+uwWwbqpAKjw5E/48BASsKY2hhhwk2Tv9oUgqMZU0DCHUoVkLX/i+xZq2G5iHFmWm41kts93wAtNzbNe2/UY7cClCSWA8hjVUgRzw5FuIAIcgJEK9J65AslCwp5gdcw+GR+cnRxcnBVLjyUOc17leTlu4AImTAmxhPxbJ4845WXFTY1T63wGfTDUhz3AWCyGfJiqd82oWn0wUg/m/ycEKdTWT5VBzX3vUP+8O+8fjwSjL8qTzZD31D7JOjnHvRMhl39+JWSB1JHMeKu7pxq4V8+GpjjQudaRxqSOLpKprv+S+mmKSFzVSi6WIQSoOCTZ6Ryu2O7oWI3lVEccvEfFer7tVupVAYKn3A8iGfesyE2mpdzrqn9yo9J3yTh63fJ6hjCNYjCm2NllrcswEf03LFEe7PGPZbR8CZpkObTVMh+GZZ9me6R26FprHudd0g03PaKw3aRr5+1bT/LTVNB9nAt/kZtMxB93T7vHwAi3T6+awzFbEXX55/7pn4xjD3rpZfns3nvj/r4X/i1mof6Ur8cczoQX5dt4vdpv6bmA5ZgMaaBjXYabnW74Jlt107MOWZ7P2S94v5LWs8vyPV7fKJjNvwir/5HqHVY5WYcaZDsttlV/I2hPlQCLQWnwZAw/X0suK/f5GeAJ5enDrRWY0rYYxGBnm18amafVKr6uPmxWuP80qKcO5PibyPT7zCadISkyEQv714teDMiktNFGdn79a9ZfqDyzXbrbattmyWMt0AtYw3Ra0TadtteCe3XAZxe+bkuIKNf3ZI1S84sz40uiGwJTkjKulMVgmCmZo3Wmqng7v3DBKo9Xw08sfhZitzVIlB+i6CGG0vvRE9iroXylmqVhzkYUN8qjvP8saKiuRbc3DYJGU0QervWQjc77tWzPnm8rtzBlJj9ckkb8B5GpqOdELAAA=
```

---

### 📌 Preset 3: 3D Multiblock BoM Materials & Compound Modules (GTCEu)
* **Verifies**: EBF + LCR compound flow, multiblock BoM dialog (`Shift + B`), module collapsing (`📦`) and expansion (`⤢`).
```text
GTBOARD:H4sIAAAAAAAA/81WTW/bRhBdWbZMfRhOA7uXtgAPQdGiIGBLiqT4UtuyXKmwEsNW0gQoIiyXQ2lhkkssl3bUQ4Geey/af9Of1Et7bWf5ASmu7bhxClgXgruzs2/evHlihZAyWQmEA1GFELJUIjWbRnAQS6q4CHZfkeRXJqsiVmGskqhCiZSoL+JAff1Huo8LbEoDBvmCQZYD6gOpnioAzxwEE6HKpEY9BTLA1OcQGToT+WiiGMQ7kQ4bcx1mkCXuXLm+rGYhkOXBqDdERCXqOCJIARXJw5BK6nngDWNP8dDjINONTSYpO8MMvQDkZHYMkkGgcM8pkvXIB43oODuLqw8KpDaITmM7UgkDuhLO8LmZAvLPx5AkGk+pYlODVJE6JnmYBg9fmMfiAmSRVKRQQiYv+rISeehkpM4Rztla8YUzcMhKckmBrIX6XBfLUxTBFomx54cg6QRS+tcgVlel0cxdDbRINsKZFN4sgtMQwFngoUBWB9ERdl3qYkeauaXhiyJZ8+kkAMXZocBYkrRrfRAdxNQ70vCSSAyT4HrAsNhRSjqurTPBvREkmFUs8fCqgQjYFHzOqHcZQYlUE7aG9E3vudr97W0NbaSdM/u6DvMLpHh770vd2CzbVY1N0vVclzMOAZvpVYMYjCqYCDkjtUVqtJZ48B7irgykCG7Q9obPA0D5uWqHY+SivK/ZWlS4rlAEimKgM6Rsis+uhpUhSET5adpr0PxLzsa2RyM1dmPEwaBAyjw69HgYgoNF1PyFJHkpBWLwaCic2ANMWlFUTkDNJWCE88kopMgfNd3t9jY0HWur3QKrCXbdslvArI6z1X7M7OaTRr2FqSQwHkKeqkCqPPpGs06xMXjVmjjHhnmCneHtePfpaO/pwd7JQYFUeLSPYnyql5MSTkCi+QCWsByK6OXuIIWOI8aodPrAJ1NNyi/Ygwshz7LBzXpwI0MIMxXCSNNe7R31uqOTQXfce57e9Sr3P4M8SOvpZgrCWX0H9zXkVU+orWss5JL5uJeFm/s63DxMw4ukrGv5jjtqitf9WSGVUAqcHsUhwsJXtSnr0XhNck2+7dOv7+jTm6daaB5Ekfkux/4kd+bswPhf3n1zxH1w8enNLt6/Ny4+vZOL9++vi/fvjYv/b58o/8HAPe2745yKsQSqO3FHA+9fY+Bb9U6jA07DoluPbau5xdDAG21mNTptaFG3RcGxLxl4/4MauHoPA7+aoQ9h4Ndxf42BH+lws5uFmydp+K0N/EeSO3XJlZgwV2vKzE6u1RT8t3OlCuTvWW//0PzKPOqeZCb9uXnZto8lSoFp+kwPpWK6QprzKsz9Z0OTB1EISUj+5a+JXrrdH/vt1FMkK0x4Qv798/d/lcjKhWZl96dfF+ztUb3tdjrtTt0CG7atZh061pNm27YajWaraTcpqzcoGqriCoX9WR8lYmkpmkNQqOdYO0lX+CGOAvpOaZpoaPfs9zl/NMhan7z+IISfj0yZVHH0gpSDlH90FVcKP5HsrXjACnkwcN6kFZWUSI/ejpwSTkN2NoP6ModK/gEwJFAokQ0AAA==
```

---

### 📌 Preset 4: Thermal Boiler & Steam Dynamo Loop (Thermal / Systeams)
* **Verifies**: Compression boiler water $\rightarrow$ steam, Steam Dynamo FE generation, fluid balance sheets.
```text
GTBOARD:H4sIAAAAAAAA/71VTW/bRhAdSZZCyQoSBClg5KRDrwREWYpIHxLZ+qhdKE5hK1+XCCvuSFqY5BLLlV3n1mP/QH9Pf1IP7bkdckXbSRxERT50kbgczrz35j2xBlCFciQ5JjUAKFagPmMJDlaKaSGjXhuyTxXuyJWOVzqrKlSgwkK5inTvd3OfDvwli3x8+pc5sGArYiFC+VQjC6tQZ4FGFVHTc0ystAdsL7SPq70kLbCgKPgHJ1v6MqYGo/GLowEBqIjolvlvPjf/FaO5t86/F4oIfcXmeu8iLTIYPj59D0cJfvBlpBkV8WfMX9J3PwWyninoJjzSS1QhC/ZmUgSopr4MY4VJQnoWoCqSUSDiGDmBroc3WuTQC2CJ5JnkqwCpZU0ztUA9EaigOH5ZAitmigUBBmmpwfyj5/GW63Qe2y7zZnbbcVq25zR925t3WNvxnZnb7FIrhb6IMW9FGxDJTxghrVoqGnVXnqPyA+mf0XSafTrZPx7snwwKUBPJAbniOD3OKJygIj8gUdiKZfK6d2ygl6DmM8UPUSyWqSR/kPAXUp0lOnPTWvgds+YbskyNUgQxhbO4nKSK1w+H+5Pp85Pp6XA8MoPe5Pu24L4h06cdLaS6POKfbluAOgm6CrSYpeSuvPGgf13aOMhKS1BNCbwSXC9pzD81qMVKxqi0wITY3knDMXxxtSv42nkpjoa3mtWaS7LB3hzNxm9cbhST7xXTTeLxMI8Hv6TZcpr1+UbBcNus22ztNm0fOQXDmz22vW6rZe82ERnnXnfe2f1MMApfFAz9f4Lx4IawU6PO+5HYHo6H/cnJUX86Gm6QiFv6fSoL9cwAjUFWtGkKegPI7V6ZK2qT285wP8hNZ4D+fG05SQo9+Th9jezPumE/aRg0Vz8MrMZo2LhaS/7WSpUrbvYPuJkdSlD2ZSDVv38/fVuB8kUqQu+3wzX2zFUOd3bnHB2763c6dptx13Y91rR5Z+Y6LnKXzzoWlLXQ5NSdibH7msov8oIojqWMKYzLzA+9sz+vlWLReqHZ5Tspw9z+Vdim+NDLKXNMzbjcmisZZvbbSAIiJ6Ij/qshU9HSPLqZLhVy9vrZNdTXOVT4D2E11DFICAAA=
```
