# GregTech Calculator Board Comprehensive QA Test Checklist

This document is the official QA test checklist for `GregTechCalculatorBoard`, systematically verifying all primary features, calculation solvers, mod compatibility SPI layers, UI/UX workflows, and internationalization (i18n) integrity.

---

## 1. Canvas Interaction & Interactive Wiring (Canvas & Wiring)

### 1.1 Canvas Viewport Controls
- [ ] **Pan Navigation**: Verify smooth canvas dragging via mouse right-click drag.
- [ ] **Zoom Controls**: Verify seamless zooming between $25\% \sim 200\%$ via mouse wheel scroll.
- [ ] **Origin Reset**: Verify camera aligns to $(0, 0)$ on pressing `Space`.
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

## 2. Calculation Solver & Overclocking & Parallel Engine (Solver & Overclocking)

### 2.1 Voltage Tiers & Overclocking (GTCEu)
- [ ] **Voltage Tier Progression**: Changing tiers from ULV to MAX accurately updates voltage (V), duration, and base power (EU/t).
- [ ] **Overclock Modes**:
  - [ ] **Standard Overclock**: $+1\text{ Tier} = 4\times\text{ EU/t}, 2\times\text{ Speed}$ (50% energy efficiency).
  - [ ] **Perfect Overclock**: $+1\text{ Tier} = 4\times\text{ EU/t}, 4\times\text{ Speed}$ (100% energy efficiency).
  - [ ] **Lossless Overclock**: $+1\text{ Tier} = 2\times\text{ EU/t}, 2\times\text{ Speed}$.
  - [ ] **Sub-tick Execution**: Processing times $< 1.0\text{ tick}$ promote to batch operations per engine tick (`batchesPerTick`).

### 2.2 Parallel Processing & Power Compounding
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

### 2.3 Cyclic Dependencies & Mass Conservation Solvers
- [ ] **Closed-Loop Recycling (Gauss-Jordan Mass Balance)**:
  - [ ] Closed-loop cycles ($A \rightarrow B \rightarrow C \rightarrow A$) resolve without infinite recursion, computing exact net mass yields.
- [ ] **Time Unit Toggling (RateTimeUnit)**:
  - [ ] `/s` (per sec), `/min` (per min), `/h` (per hour), `/t` (per tick), `/batch` (per batch) scale all values consistently.
- [ ] **Machine Pinning**:
  - [ ] Pinning specific machine counts highlights upstream/downstream utilization percentages and bottlenecks.

---

## 3. Mod Compatibility SPI Layers (Mod Compat SPI)

### 3.1 GregTech CEu Modern
- [ ] **Heating Coils**:
  - [ ] Coil tier changes from Cupronickel to Trinium validate temperature requirements.
  - [ ] Excess temperature applies 5% compound EUt discounts per 900K.
- [ ] **Steam Multiblocks**:
  - [ ] Steam boilers and machines evaluate fixed nominal ratings (64 mB/t HP, 32 mB/t LP) without parallel multiplication.
- [ ] **Large Turbines & Rotors**:
  - [ ] Large steam/gas/plasma turbines scale baseline generation with rotor efficiency (%), power (%), and durability.
- [ ] **Cleanroom & Fusion**:
  - [ ] Cleanroom tier badges render accurately; fusion reactor reflector tiers add power bonuses.

### 3.2 Thermal Series & Systeams
- [ ] **Thermal Augments**:
  - [ ] Scale, DynamoPower, and DynamoEnergy augments extract deterministic multipliers via NBT.
- [ ] **Systeams Boilers**:
  - [ ] Water consumption and steam generation scale with composite fuel efficiency augments.
- [ ] **Steam Dynamos**:
  - [ ] Steam consumption evaluates exact RF/FE generation and overclock limits.

### 3.3 Create & Create: New Age
- [ ] **Kinetic Propagation (Stress Units & RPM)**:
  - [ ] Water Wheels, Windmill Bearings, and Steam Engines calculate generated SU/t.
  - [ ] Processing machines evaluate speed and SU consumption based on RPM.
- [ ] **Overstress Simulation**:
  - [ ] When consumed SU exceeds available SU, network efficiency halts at 0% simulating Create physics.
- [ ] **Generator Coils & Magnets**:
  - [ ] Installed magnet strength and counts calculate exact FE/t generation.
- [ ] **Electric Motors**:
  - [ ] FE consumption evaluates output SU and RPM ratings.

### 3.4 Star Technology
- [ ] **GCU & Threading**:
  - [ ] GCU compound calculations and Threading Helix Co-Processor parallel scaling apply accurately.

---

## 4. Group Frames & Compound Modules

### 4.1 Group Frames
- [ ] **Frame Creation (`Ctrl + G`)**: Creates a visual frame bounding selected nodes; dragging the header moves all inner nodes.
- [ ] **Titles & Notes**: Inline title editing and sticky notes.
- [ ] **Theme Colors (🎨)**: Palette button toggles border and background colors.
- [ ] **Ungrouping**: Deleting a frame preserves all contained nodes.

### 4.2 Compound Module Packaging
- [ ] **Collapse to Module (`📦`)**:
  - [ ] Packaging compresses the sub-graph into a **single compound module card**.
  - [ ] **Port Aggregation**: Only external connections are promoted to outer module sockets.
  - [ ] **Internal Edge Encapsulation**: Intermediate wires are hidden inside the module card.
  - [ ] **Aggregated Stats**: Top header sums total power consumption and total machine count.
- [ ] **Expand Module (`⤢`)**:
  - [ ] Expanding restores original node coordinates and wiring with 100% fidelity.

---

## 5. Multiblock Construction BOM (Bill of Materials)

- [ ] **3D Structure Scanning**:
  - [ ] Summarizes casing, heating coil, hatch, bus, and controller quantities.
- [ ] **Dual Hatch Optimization**:
  - [ ] Toggles between `1x Full Tier Hatch` vs `2x Lower Tier Hatches` with live BOM updates.
- [ ] **Stack Conversions & Filters**:
  - [ ] Filters by Casings, Coils, Hatches, and Controllers.
  - [ ] Inventory stack conversions display accurately (e.g. `2st + 15`).
- [ ] **Shopping List Export (`📋`)**:
  - [ ] Exports clean markdown shopping lists to the clipboard.
- [ ] **Recipe Viewer Integration (`Register in EMI/JEI`)**:
  - [ ] 1-click registration of BOM targets into EMI/JEI recipe trees.

---

## 6. Recipe Search & Favorites (Search & Favorites)

### 6.1 Prefix & Boolean Search Engine
- [ ] **Mod Filters (`@`)**: Filters by mod namespace (e.g. `@gtceu`, `@thermal`).
- [ ] **Tag Filters (`#`)**: Filters by item/fluid tags (e.g. `#forge:ingots`).
- [ ] **Category Filters (`[` or `%`)**: Filters by workstation/category (e.g. `[macerator]`).
- [ ] **Input / Output Filters (`in:`, `out:`, `>`, `<`)**:
  - [ ] `in:iron`, `>polyethylene` (Input filters).
  - [ ] `out:steel`, `<oxygen` (Output filters).
- [ ] **Boolean Operators (`!`, `|`, `"..."`)**:
  - [ ] `!charcoal` (NOT exclusion).
  - [ ] `oil | creosote` (OR query).
  - [ ] `"heavy fuel"` (Exact phrase search).

### 6.2 Bilingual Search
- [ ] **English & Korean Dual Matching**:
  - [ ] Matches both Korean terms (e.g. `"물레방아"`, `"탄소 브러시"`) and English terms (e.g. `"water wheel"`, `"motor"`).

### 6.3 Favorites Dock
- [ ] **Star (⭐) Toggle**: Stars toggle recipe bookmarks in search lists and node cards.
- [ ] **Drag-to-Place**: Dragging recipes from the favorites dock places nodes directly on canvas.

---

## 7. UI/UX & Internationalization & Modals (i18n & Dialogs)

- [ ] **Interactive Onboarding Tutorial**:
  - [ ] Step-by-step 7-stage interactive guide with glow highlights and completion badges.
- [ ] **Global Balance Dashboard (`B` Key)**:
  - [ ] Summarizes cross-page feedstock deficits, surpluses, and global energy budgets.
- [ ] **Integrated Board Settings Modal (`[⚙ Settings]`)**:
  - [ ] Configures global fluid units (Auto/mB/B), time formats, singleplayer pause, and integer ratio limits.
- [ ] **Smart Auto-Connect Filter Dialog (`AutoConnectFilterDialog`)**:
  - [ ] Interactively select target resource sockets via checkboxes during multi-port wiring.
- [ ] **Blueprint Metadata Import/Export Preview (`[📥] / [📤]`)**:
  - [ ] Inspect total node counts and key item/fluid flows before confirming import or export.
- [ ] **i18n Consistency**:
  - [ ] `en_us.json` and `ko_kr.json` format tokens (`%s`, `%d`) match 100%.
  - [ ] Passes `testI18nCompletenessAndConsistency` unit tests.

---

## 8. Ready-to-Use Test Preset Blueprints

Copy and import these blueprints via the top toolbar's **`📥 Import`** button to instantiate pre-built test graphs instantly.

### 📌 Preset 1: Petrochemical & Distillation Tower 16x Parallel Loop (GTCEu)
```text
GTBOARD:H4sIAAAAAAAA/81Xz2/jRBSeNE2apomoKhaQ4ODDHtiDpfxw0mQ5kNKm20htqdp02Z7CZGacjNbxWPa43XAB7REhceaC+FP2T+ICV3jjiTc/1mmyP4TooVXtmXnf+973vXnOI7SNMq6gLMgjhNJZVOjjgB2FPpZcuC0LRT/baEuE0gtltCqVRVk8EqErW7+2ovfwgAyxS9jXf+oNObTp4hFD6W+5s40K2JHMd+HIWxbk1AloeyAJCx8L7uTQBqdz/2/KscdQ5vj0unMEobPcVZGjc9PoARGuxNxl9AyTIfw9VEAmMTm8RJ+8PqpHfe443B30fD6AmDw4drjnMQqAC6OZ7THsFMrx4EzQ0GFwXF5if8BklzMfbZw9TaOch33sOMxRSzXuh7UGrjYxtk2bVW3TKlt9s1GvlUyL1O3Gfs2y90kVjvIZ4R6Lj0qhHR48YS4DmoUPoYrilvnEEeQ5RIfYV92D86ODy6MUyvPgG6jIuXocpXDJfKgFgxQ2PRE8a3XQhJk8wT49YXwwVHS8BNrvhP88kFElJ7Qv4QYAKjCDcVcxv9M+bR92LzuHvfa1jnLTuonruqszOcSSDYQ/7tDlfBeAy9CRvK/yei2JXZCEcTRZaFzyQRptK+TfcSqHEOKvPMp7vvCYLzkLIM0tpcj2tWx9rzGgRZHeJIl0cypS/9X9Is2fMHw7No5DlqzVXZ3gUK3q2bBKlz7h8ZxypwDGaAWAU1W0lQActepNALOPlwDgKwBsnWNvKIc4MXpRh3H1Eh168dmSuDcr4hYumQ0e9MfGExwkBt/TgfzJut4ABxpB4ovFzoEpBekrOWyk0V7s3kiUnqO8iNCuaik+Js9Bje3IAxfgRBa1FJpGHwUjpkBdTJ0PO4ojPHCZ5ORYwNrZ5vOphsVue3G03hBLMsyhHeixxOeekiwqlusvjPjMALzrC2gEF+IuwgRxi5CYwwg87GqcQOIenSh+msCU0MxIUDBjJooPuAn4sctGnmoxoQ8Yt3JQSk9FOAROJIYM0+hjMmQjTrBz5TFGZzLPop0I0hl+oaz323zdvoihG3CW9IVjnKgkjS8hrUdZVGShnGJsleLmCr3soB8IR7UvRfskdhLtUfC2bXPCmUuUgaiu+3KCc2TSk6Z9GhL0xgBvHLDFBNEyQaTeXhDQ6jrBVdif9NrFm8gJb3u6vSZqoXAaPjXKB0ZU/Tek8DZlTyhw7iCSwIDpe3u+MtNjFLNL0N7DYQptdYJTaMXqCtuMhJqGZJL8kUIfdYKjEDunCqBemiDyZOG+s04f6ALG6tREP3oX6U3FVZglZzqe/OeD0Tqj0Gd6M+WBhPs2ElFPqgK83zCkizw7De1OpqFm2a5V6xXLpDX4ZbFSxWywft2s7ZOm1SS0sl/uL0xD7Q86DcnkaeiPxGloKT2r56Hu8nnoHtLnJqJUrIW9o5mlRle3gTVnot+XzETnSTNRav2ZaO9QtUBGjRWz0SRZolf3Fmeke14nT/nRXf0/Gt1+XOHjzJVkeJQYe0cHCdQCHXb+yVu7uThL5ntaeONkyfcM65dZGVtV067XGqZVaVKzT1jdtBqWTUv1Bm7apQUHn3xQB798lejgXxIdPE/Je9l2kd1kr25pX6xv0J9Q7MSs7cMJsRF1so9j92p4ram4BFDSmZ0SjTe7hHHH5dBQt9rcPRfNAOrjqv3U0GkG8Ud+LmJ2rY/W9Zr5eoJJowwRjvD/+erl31mUuVOstX5+NjN8PGyWcAWXrZJplylA6rOq2ahY1MRNWsHVOqUV2oRxR3IJWv78gsHUGV/iRvQVoRK+drkEqw4j0bRGaMoudiffItG/Pwgxij2yjXbAXC6MITz+VoDr3vbFKNLoWlxBftzt0Bc6n6wUeus6BKZRFuQ/2YvmIq+3PTHyekWZjaxJehaThP4FWTLUuRoSAAA=
```

---

### 📌 Preset 2: Create Rotational Kinetics & New Age FE Generation
```text
GTBOARD:H4sIAAAAAAAA/81WQY/bRBSebDZpkg0IBBUcQPIBIS6W1rHTOFUF2e4mELq7h03CtqdoPH5ORnU81niyaThVnDjyCxA/hZ/CT+DSM7yxk90NJKpLW9QcHHv85nvvfe/7bNcIqZJSJHxIaoSQYpnUPZrAyVxSxUXUcUj6q5I7Yq7iuUqjCmVSpjMxj1TneXYfF9iURgy++TNbqJD9iM6A1AdKQpIYo4irpErqNFQgI8S+gqSiochHTAJVcD9JA8dzHVghe9zfcWdfLWMg+/1h9wzLKvNIV5XmLJK7TESK8gj8M8qm+H+si1zVw/Em+XSFGVI5gfECT+V4MQUIC6TKk17I4xh8bKc+uwWwbqpAKjw5E/48BASsKY2hhhwk2Tv9oUgqMZU0DCHUoVkLX/i+xZq2G5iHFmWm41kts93wAtNzbNe2/UY7cClCSWA8hjVUgRzw5FuIAIcgJEK9J65AslCwp5gdcw+GR+cnRxcnBVLjyUOc17leTlu4AImTAmxhPxbJ4845WXFTY1T63wGfTDUhz3AWCyGfJiqd82oWn0wUg/m/ycEKdTWT5VBzX3vUP+8O+8fjwSjL8qTzZD31D7JOjnHvRMhl39+JWSB1JHMeKu7pxq4V8+GpjjQudaRxqSOLpKprv+S+mmKSFzVSi6WIQSoOCTZ6Ryu2O7oWI3lVEccvEfFer7tVupVAYKn3A8iGfesyE2mpdzrqn9yo9J3yTh63fJ6hjCNYjCm2NllrcswEf03LFEe7PGPZbR8CZpkObTVMh+GZZ9me6R26FprHudd0g03PaKw3aRr5+1bT/LTVNB9nAt/kZtMxB93T7vHwAi3T6+awzFbEXX55/7pn4xjD3rpZfns3nvj/r4X/i1mof6Ur8cczoQX5dt4vdpv6bmA5ZgMaaBjXYabnW74Jlt107MOWZ7P2S94v5LWs8vyPV7fKJjNvwir/5HqHVY5WYcaZDsttlV/I2hPlQCLQWnwZAw/X0suK/f5GeAJ5enDrRWY0rYYxGBnm18amafVKr6uPmxWuP80qKcO5PibyPT7zCadISkyEQv714teDMiktNFGdn79a9ZfqDyzXbrbattmyWMt0AtYw3Ra0TadtteCe3XAZxe+bkuIKNf3ZI1S84sz40uiGwJTkjKulMVgmCmZo3Wmqng7v3DBKo9Xw08sfhZitzVIlB+i6CGG0vvRE9iroXylmqVhzkYUN8qjvP8saKiuRbc3DYJGU0QervWQjc77tWzPnm8rtzBlJj9ckkb8B5GpqOdELAAA=
```

---

### 📌 Preset 3: 3D Multiblock BOM & Compound Modules (GTCEu)
```text
GTBOARD:H4sIAAAAAAAA/81WTW/bRhBdWbZMfRhOA7uXtgAPQdGiIGBLiqT4UtuyXKmwEsNW0gQoIiyXQ2lhkkssl3bUQ4Geey/af9Of1Et7bWf5ASmu7bhxClgXgruzs2/evHlihZAyWQmEA1GFELJUIjWbRnAQS6q4CHZfkeRXJqsiVmGskqhCiZSoL+JAff1Huo8LbEoDBvmCQZYD6gOpnioAzxwEE6HKpEY9BTLA1OcQGToT+WiiGMQ7kQ4bcx1mkCXuXLm+rGYhkOXBqDdERCXqOCJIARXJw5BK6nngDWNP8dDjINONTSYpO8MMvQDkZHYMkkGgcM8pkvXIB43oODuLqw8KpDaITmM7UgkDuhLO8LmZAvLPx5AkGk+pYlODVJE6JnmYBg9fmMfiAmSRVKRQQiYv+rISeehkpM4Rztla8YUzcMhKckmBrIX6XBfLUxTBFomx54cg6QRS+tcgVlel0cxdDbRINsKZFN4sgtMQwFngoUBWB9ERdl3qYkeauaXhiyJZ8+kkAMXZocBYkrRrfRAdxNQ70vCSSAyT4HrAsNhRSjqurTPBvREkmFUs8fCqgQjYFHzOqHcZQYlUE7aG9E3vudr97W0NbaSdM/u6DvMLpHh770vd2CzbVY1N0vVclzMOAZvpVYMYjCqYCDkjtUVqtJZ48B7irgykCG7Q9obPA0D5uWqHY+SivK/ZWlS4rlAEimKgM6Rsis+uhpUhSET5adpr0PxLzsa2RyM1dmPEwaBAyjw69HgYgoNF1PyFJHkpBWLwaCic2ANMWlFUTkDNJWCE88kopMgfNd3t9jY0HWur3QKrCXbdslvArI6z1X7M7OaTRr2FqSQwHkKeqkCqPPpGs06xMXjVmjjHhnmCneHtePfpaO/pwd7JQYFUeLSPYnyql5MSTkCi+QCWsByK6OXuIIWOI8aodPrAJ1NNyi/Ygwshz7LBzXpwI0MIMxXCSNNe7R31uqOTQXfce57e9Sr3P4M8SOvpZgrCWX0H9zXkVU+orWss5JL5uJeFm/s63DxMw4ukrGv5jjtqitf9WSGVUAqcHsUhwsJXtSnr0XhNck2+7dOv7+jTm6daaB5Ekfkux/4kd+bswPhf3n1zxH1w8enNLt6/Ny4+vZOL9++vi/fvjYv/b58o/8HAPe2745yKsQSqO3FHA+9fY+Bb9U6jA07DoluPbau5xdDAG21mNTptaFG3RcGxLxl4/4MauHoPA7+aoQ9h4Ndxf42BH+lws5uFmydp+K0N/EeSO3XJlZgwV2vKzE6u1RT8t3OlCuTvWW//0PzKPOqeZCb9uXnZto8lSoFp+kwPpWK6QprzKsz9Z0OTB1EISUj+5a+JXrrdH/vt1FMkK0x4Qv798/d/lcjKhWZl96dfF+ztUb3tdjrtTt0CG7atZh061pNm27YajWaraTcpqzcoGqriCoX9WR8lYmkpmkNQqOdYO0lX+CGOAvpOaZpoaPfs9zl/NMhan7z+IISfj0yZVHH0gpSDlH90FVcKP5HsrXjACnkwcN6kFZWUSI/ejpwSTkN2NoP6ModK/gEwJFAokQ0AAA==
```

---

### 📌 Preset 4: Thermal Boilers & Steam Dynamos Loop (Thermal / Systeams)
```text
GTBOARD:H4sIAAAAAAAA/71VTW/bRhAdSZZCyQoSBClg5KRDrwREWYpIHxLZ+qhdKE5hK1+XCCvuSFqY5BLLlV3n1mP/QH9Pf1IP7bkdckXbSRxERT50kbgczrz35j2xBlCFciQ5JjUAKFagPmMJDlaKaSGjXhuyTxXuyJWOVzqrKlSgwkK5inTvd3OfDvwli3x8+pc5sGArYiFC+VQjC6tQZ4FGFVHTc0ystAdsL7SPq70kLbCgKPgHJ1v6MqYGo/GLowEBqIjolvlvPjf/FaO5t86/F4oIfcXmeu8iLTIYPj59D0cJfvBlpBkV8WfMX9J3PwWyninoJjzSS1QhC/ZmUgSopr4MY4VJQnoWoCqSUSDiGDmBroc3WuTQC2CJ5JnkqwCpZU0ztUA9EaigOH5ZAitmigUBBmmpwfyj5/GW63Qe2y7zZnbbcVq25zR925t3WNvxnZnb7FIrhb6IMW9FGxDJTxghrVoqGnVXnqPyA+mf0XSafTrZPx7snwwKUBPJAbniOD3OKJygIj8gUdiKZfK6d2ygl6DmM8UPUSyWqSR/kPAXUp0lOnPTWvgds+YbskyNUgQxhbO4nKSK1w+H+5Pp85Pp6XA8MoPe5Pu24L4h06cdLaS6POKfbluAOgm6CrSYpeSuvPGgf13aOMhKS1BNCbwSXC9pzD81qMVKxqi0wITY3knDMXxxtSv42nkpjoa3mtWaS7LB3hzNxm9cbhST7xXTTeLxMI8Hv6TZcpr1+UbBcNus22ztNm0fOQXDmz22vW6rZe82ERnnXnfe2f1MMApfFAz9f4Lx4IawU6PO+5HYHo6H/cnJUX86Gm6QiFv6fSoL9cwAjUFWtGkKegPI7V6ZK2qT285wP8hNZ4D+fG05SQo9+Th9jezPumE/aRg0Vz8MrMZo2LhaS/7WSpUrbvYPuJkdSlD2ZSDVv38/fVuB8kUqQu+3wzX2zFUOd3bnHB2763c6dptx13Y91rR5Z+Y6LnKXzzoWlLXQ5NSdibH7msov8oIojqWMKYzLzA+9sz+vlWLReqHZ5Tspw9z+Vdim+NDLKXNMzbjcmisZZvbbSAIiJ6Ij/qshU9HSPLqZLhVy9vrZNdTXOVT4D2E11DFICAAA=
```
