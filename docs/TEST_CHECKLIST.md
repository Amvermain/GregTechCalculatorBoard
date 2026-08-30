# GregTech Calculator Board Feature Verification Checklist (QA Test Checklist)

This document is the official QA verification checklist for testing all core features, calculation solver, mod compatibility SPI layers, UI/UX, and localization in `GregTechCalculatorBoard`.

---

## 1. Canvas Operations & Interactive Wiring

### 1.1 Canvas Base Controls
- [ ] **Pan**: Right-click drag moves the canvas smoothly across all directions.
- [ ] **Zoom**: Mouse wheel scroll zooms between $25\% \sim 200\%$.
- [ ] **Reset Origin**: Pressing `Space` centers the viewport at $(0, 0)$.
- [ ] **Fit to View (`Home` / `F`)**: Pressing `Home` or `F` automatically centers and scales the camera to encompass all placed nodes.
- [ ] **Selection & Manipulation**:
  - [ ] Single node click selection and dragging.
  - [ ] Drag-box multi-node selection and batch movement.
  - [ ] `Ctrl + A` selects all nodes and frames.
  - [ ] `Delete` / `Backspace` deletes selected nodes and frames.
  - [ ] `Ctrl + Z` (Undo) and `Ctrl + Y` (Redo) history operations work seamlessly.

### 1.2 Wiring & Ratio Calculation
- [ ] **Basic Wiring**: Left-click green output port $\rightarrow$ left-click blue input port creates a curved wire.
- [ ] **✨ Shift + Wiring (1:1 Auto Ratio Scaling)**:
  - [ ] Holding `Shift` while connecting dynamically calculates provider production vs consumer demand and scales the consumer machine count to match 1:1 (e.g. 500 mB/s provider $\rightarrow$ 100 mB/s consumer scales count to **5.0 machines**).
- [ ] **Wire Reroute Junction**:
  - [ ] **Double-clicking** an existing wire curve inserts a zero-cost [🔀 Reroute Junction Node].
  - [ ] Branching flow to multiple machines from a junction distributes rates accurately.
  - [ ] `Shift + Wiring` from a junction uses the branched rate for 1:1 auto ratio calculations.
- [ ] **Severing Wires**: Right-clicking a wire curve or right-clicking a connected port severs the wire immediately.
- [ ] **Drag-to-Search**:
  - [ ] Dragging from a port onto empty canvas opens the 4-button quick marker (🔍 Search, ➕ Junction, 📋 Duplicate).
  - [ ] Clicking 🔍 opens the recipe search dialog pre-filtered for consuming/producing recipes of that ingredient.
  - [ ] Selecting a recipe places the node and automatically establishes the wire connection.

### 1.3 Page Tabs & Blueprint Sharing
- [ ] **Page Tab Management**:
  - [ ] Add new page with top `+` button.
  - [ ] Switch pages (camera position and zoom level are remembered per page).
  - [ ] Right-click tab header to rename inline.
  - [ ] Click `x` to delete tab (first tab is protected).
  - [ ] **Tab Bar Overflow Navigation**: When tabs exceed screen width, verify left/right `«`, `»` indicator clicking and wheel scrolling navigate smoothly with 16px right padding.
- [ ] **Save & Load**: `💾 Save` / `📂 Load` persistently writes and restores all pages, nodes, frames, notes, and wires to/from `calcboard_save.nbt`.
- [ ] **Blueprint Sharing**:
  - [ ] `📋 Share` exports the factory into a compressed text blueprint code copied to clipboard.
  - [ ] `📥 Import` restores shared blueprint codes with 100% fidelity.

### 1.4 Alternative Recipe Switching & Accessibility (Recipe Switcher & UI Scaling)
- [ ] **In-Place Recipe Switching (Switch Recipe)**:
  - [ ] Verify `[🔄 Switch Recipe]` in `MachineConfigDialog` or context menu displays matching candidate recipes.
  - [ ] Verify ports with identical item/fluid IDs preserve incoming and outgoing wire connections automatically.
  - [ ] Verify full undo/redo support via `Ctrl+Z` / `Ctrl+Y`.
- [ ] **5-Level UI Font Scale (`FontScale`)**:
  - [ ] Verify `[Aa 1.0x]` button click (left/right), wheel scroll, and `+`/`-` keys scale the machine dialog from 0.75x to 1.30x.
  - [ ] Verify center-anchored matrix unprojection maintains pixel-perfect button clicking.
- [ ] **Uniform Global Fluid Units (`FluidUnitMode`)**:
  - [ ] Verify cycling `Auto` ➔ `Always mB` ➔ `Always B` via toolbar button or `Shift+T`.
  - [ ] Verify canvas-wide fluid rates and tooltips render consistently in the selected unit.

---

## 2. Calculation Solver & Overclocking & Parallel Engine

### 2.1 Voltage Tiers & Overclock Modes (GTCEu)
- [ ] **Voltage Tier Selection**: Changing tiers from ULV to MAX reflects voltage (V), duration, and base EU/t correctly.
- [ ] **Overclock Modes**:
  - [ ] **Standard**: $4\times\text{ EU/t}$, $2\times\text{ Speed}$ per tier ($50\%$ energy efficiency).
  - [ ] **Perfect**: $4\times\text{ EU/t}$, $4\times\text{ Speed}$ per tier ($100\%$ energy efficiency).
  - [ ] **Lossless**: $2\times\text{ EU/t}$, $2\times\text{ Speed}$ per tier.
  - [ ] **Subtick Execution**: Durations $< 1.0\text{ tick}$ convert to `batchesPerTick` accurately.

### 2.2 Parallel Scaling & Voltage Tier Power Allocation
- [ ] **Parallel Power Scaling**:
  - [ ] Verify formula: $\text{Total EU/t} = \text{Overclocked EU/t} \times \text{Parallels}$.
  - [ ] **4x Parallel Verification**: Requires 1 voltage tier higher power ($4\times\text{ EU/t}$, e.g. EV 1,920 EU/t $\rightarrow$ 4x parallel consumes 7,680 EU/t, IV power).
  - [ ] **16x Parallel Verification**: Requires 2 voltage tiers higher power ($16\times\text{ EU/t}$, e.g. EV 1,920 EU/t $\rightarrow$ 16x parallel consumes 30,720 EU/t, LuV power).
- [ ] **Parallel Control Hatch**:
  - [ ] Installing 4x, 16x, 64x, 256x parallel control hatches scales throughput and power consumption proportionally.
- [ ] **Energy Hatch Capacity**:
  - [ ] `GTEnergyHatchAddon` calculates total capacity ($V \times A$) across 1A, 2A, 4A, and 16A hatches.
  - [ ] **Dual Hatch Overclock**: 2 hatches of the identical tier grant +1 voltage tier overclocking headroom.
  - [ ] **Asymmetric Hatches**: Asymmetric combinations (e.g. 16A EV + 1A IV) derive effective tier from total EU/t capacity.

### 2.3 Cyclic Dependency & Graph Balancing
- [ ] **Cycle Detection**: Closed loop cycles (A $\rightarrow$ B $\rightarrow$ C $\rightarrow$ A) solve without infinite loops and output correct Net Production rates.
- [ ] **RateTimeUnit Toggle**: Switching between `/s`, `/m`, `/h`, `/t`, and `/batch` converts all node rates and summary cards consistently.
- [ ] **Machine Pinning**: Pinning machine counts visually displays upstream/downstream utilization ($\%$) and shortage/surplus rates.

---

## 3. Mod Compatibility SPI Layers

### 3.1 GregTech CEu Modern
- [ ] **Heating Coils**: Cupronickel through Trinium coils validate recipe temperature thresholds and apply EUt discounts & speed boosts.
- [ ] **Steam Multiblocks**: Steam boilers, grinders, and ovens use fixed nominal flow rates (64 mB/t for HP, 32 mB/t for LP) instead of parallel multipliers.
- [ ] **Large Turbines & Rotors**: Steam/Gas/Plasma turbine power formulas reflect rotor efficiency ($\%$) and power ($\%$).
- [ ] **Cleanroom & Fusion**: Cleanroom tier badges and fusion reactor reflector tier bonuses calculate accurately.

### 3.2 Thermal Series & Systeams
- [ ] **Thermal Augments**: Scale, DynamoPower, and DynamoEnergy augments extract and apply deductively.
- [ ] **Systeams Boilers**: Water consumption and steam generation scale with fuel energy multipliers.
- [ ] **Steam Dynamos**: Steam consumption converts to RF/FE generation with dynamo overclocking.

### 3.3 Create & Create: New Age
- [ ] **Stress Units (SU) & RPM**: Water wheels, windmill bearings, and steam engines propagate SU/t generation.
- [ ] **Overstress Simulation**: Exceeding network SU capacity halts all machines (0% efficiency).
- [ ] **Generator Coils & Magnets**: Magnet strength tiers and counts calculate exact FE/t generation.
- [ ] **Motors**: FE consumption vs output SU/RPM converts properly.

### 3.4 Star Technology
- [ ] **GCU & Threading**: GCU compound modules and Threading Helix Co-Processor parallel scaling operate correctly.

---

## 4. Group Frames & Compound Modules

### 4.1 Group Frames
- [ ] **Create Frame (`Ctrl + G`)**: Wraps selected nodes in a colored group frame box with batch dragging.
- [ ] **Title & Sticky Notes**: Inline editable frame titles and sticky note memos.
- [ ] **Theme Colors (🎨)**: Color palette switcher updates outline and background tint.
- [ ] **Ungroup**: Deleting the frame preserves all internal nodes and wires safely.

### 4.2 Compound Module Packaging
- [ ] **Collapse to Module (`📦`)**:
  - [ ] Compresses the entire factory inside the frame into a single compound module card.
  - [ ] **Port Aggregation**: Only wires connected to external machines outside the frame become module card ports.
  - [ ] **Internal Encapsulation**: Intermediate wires between internal nodes are hidden inside the card.
  - [ ] **Power & Machine Sum**: Displays aggregated power and total machine count on the header.
- [ ] **Expand Module (`⤢`)**:
  - [ ] Restores the original internal layout, node positions, and wiring with 100% fidelity.

---

## 5. Multiblock Construction BOM

- [ ] **3D Structure Scanning**: Aggregates total casings, coils, hatches, buses, and controllers across all placed multiblocks.
- [ ] **Dual Hatch Optimization**: Toggle between `1x Normal Hatch` and `2x Lower Tier Hatches` updates the BOM in real-time.
- [ ] **Filtering & Stack Conversion**: Filter by Casings/Coils/Hatches/Controllers and displays stack counts (e.g. `2st + 15`).
- [ ] **Copy Shopping List (`📋`)**: Copies markdown formatted BOM shopping list to clipboard.
- [ ] **Register in EMI**: Adds all required materials into the EMI Recipe Tree with one click.

---

## 6. Recipe Search & Favorites

### 6.1 Advanced Prefix & Boolean Search
- [ ] **Mod Filter (`@`)**: `@gtceu`, `@thermal`, `@create_new_age`.
- [ ] **Tag Filter (`#`)**: `#forge:ingots`, `#forge:dusts`.
- [ ] **Category Filter (`[` or `%`)**: `[macerator]`, `[pyrolyse_oven]`.
- [ ] **Input/Output Filter (`in:`, `out:`, `>`, `<`)**: `in:iron`, `out:steel`, `>polyethylene`, `<oxygen`.
- [ ] **Boolean Operators (`!`, `|`, `"..."`)**: `!charcoal`, `oil | creosote`, `"heavy fuel"`.

### 6.2 Bilingual Search
- [ ] Search by localized name (e.g. Korean `"물레방아"`, `"고급 모터"`) or English fallback (`"water wheel"`, `"motor"`) simultaneously.

### 6.3 Favorites Dock
- [ ] Toggle star (⭐) to pin/unpin recipes.
- [ ] Drag-and-drop recipes directly from the right dock onto the canvas.

---

## 7. UI/UX & Accessibility & i18n

- [ ] **Interactive Tutorial**: 15-second guided walkthrough from Step 1 (Adding node) to Step 7 (Packaging module).
- [ ] **Global Balance Dashboard (`B`)**: Checks net input/output raw material balance across the factory.
- [ ] **Board Settings Modal (`[⚙ Settings]`)**: Configures fluid rate units (Auto/mB/B), singleplayer pause, and harmonization ratio limits.
- [ ] **Smart Auto-Connect Filter Dialog**: Interactively select ports via checkboxes when auto-wiring machines.
- [ ] **Blueprint Metadata Import/Export Preview (`[📥] / [📤]`)**: Displays node count, machine summary, and title/tag metadata before importing or exporting.
- [ ] **i18n Consistency**: `en_us.json` and `ko_kr.json` are 100% synchronized with matching tokens (`%s`, `%d`).

---

## 8. Ready-to-Use Test Preset Blueprints

Instead of creating all nodes manually in-game, you can copy the compressed blueprint codes below and click **`📥 Import`** on the top toolbar in the Calculator Board to immediately generate complex test graphs on your canvas.

### 📌 Preset 1: Petrochemical Distillation & 16x Parallel Loop (GTCEu)
* **Tests**: 16x Parallel Control Hatch, LuV Energy Hatch power allocation, Distillation Tower 4-fluid byproduct routing, Thermal Cracker connection, Group Frames.
```text
GTBOARD:H4sIAAAAAAAA/81Xz2/jRBSeNE2apomoKhaQ4ODDHtiDpfxw0mQ5kNKm20htqdp02Z7CZGacjNbxWPa43XAB7REhceaC+FP2T+ICV3jjiTc/1mmyP4TooVXtmXnf+973vXnOI7SNMq6gLMgjhNJZVOjjgB2FPpZcuC0LRT/baEuE0gtltCqVRVk8EqErW7+2ovfwgAyxS9jXf+oNObTp4hFD6W+5s40K2JHMd+HIWxbk1AloeyAJCx8L7uTQBqdz/2/KscdQ5vj0unMEobPcVZGjc9PoARGuxNxl9AyTIfw9VEAmMTm8RJ+8PqpHfe443B30fD6AmDw4drjnMQqAC6OZ7THsFMrx4EzQ0GFwXF5if8BklzMfbZw9TaOch33sOMxRSzXuh7UGrjYxtk2bVW3TKlt9s1GvlUyL1O3Gfs2y90kVjvIZ4R6Lj0qhHR48YS4DmoUPoYrilvnEEeQ5RIfYV92D86ODy6MUyvPgG6jIuXocpXDJfKgFgxQ2PRE8a3XQhJk8wT49YXwwVHS8BNrvhP88kFElJ7Qv4QYAKjCDcVcxv9M+bR92LzuHvfa1jnLTuonruqszOcSSDYQ/7tDlfBeAy9CRvK/yei2JXZCEcTRZaFzyQRptK+TfcSqHEOKvPMp7vvCYLzkLIM0tpcj2tWx9rzGgRZHeJIl0cypS/9X9Is2fMHw7No5DlqzVXZ3gUK3q2bBKlz7h8ZxypwDGaAWAU1W0lQActepNALOPlwDgKwBsnWNvKIc4MXpRh3H1Eh168dmSuDcr4hYumQ0e9MfGExwkBt/TgfzJut4ABxpB4ovFzoEpBekrOWyk0V7s3kiUnqO8iNCuaik+Js9Bje3IAxfgRBa1FJpGHwUjpkBdTJ0PO4ojPHCZ5ORYwNrZ5vOphsVue3G03hBLMsyhHeixxOeekiwqlusvjPjMALzrC2gEF+IuwgRxi5CYwwg87GqcQOIenSh+msCU0MxIUDBjJooPuAn4sctGnmoxoQ8Yt3JQSk9FOAROJIYM0+hjMmQjTrBz5TFGZzLPop0I0hl+oaz323zdvoihG3CW9IVjnKgkjS8hrUdZVGShnGJsleLmCr3soB8IR7UvRfskdhLtUfC2bXPCmUuUgaiu+3KCc2TSk6Z9GhL0xgBvHLDFBNEyQaTeXhDQ6jrBVdif9NrFm8gJb3u6vSZqoXAaPjXKB0ZU/Tek8DZlTyhw7iCSwIDpe3u+MtNjFLNL0N7DYQptdYJTaMXqCtuMhJqGZJL8kUIfdYKjEDunCqBemiDyZOG+s04f6ALG6tREP3oX6U3FVZglZzqe/OeD0Tqj0Gd6M+WBhPs2ElFPqgK83zCkizw7De1OpqFm2a5V6xXLpDX4ZbFSxWywft2s7ZOm1SS0sl/uL0xD7Q86DcnkaeiPxGloKT2r56Hu8nnoHtLnJqJUrIW9o5mlRle3gTVnot+XzETnSTNRav2ZaO9QtUBGjRWz0SRZolf3Fmeke14nT/nRXf0/Gt1+XOHjzJVkeJQYe0cHCdQCHXb+yVu7uThL5ntaeONkyfcM65dZGVtV067XGqZVaVKzT1jdtBqWTUv1Bm7apQUHn3xQB798lejgXxIdPE/Je9l2kd1kr25pX6xv0J9Q7MSs7cMJsRF1so9j92p4ram4BFDSmZ0SjTe7hHHH5dBQt9rcPRfNAOrjqv3U0GkG8Ud+LmJ2rY/W9Zr5eoJJowwRjvD/+erl31mUuVOstX5+NjN8PGyWcAWXrZJplylA6rOq2ahY1MRNWsHVOqUV2oRxR3IJWv78gsHUGV/iRvQVoRK+drkEqw4j0bRGaMoudiffItG/Pwgxij2yjXbAXC6MITz+VoDr3vbFKNLoWlxBftzt0Bc6n6wUeus6BKZRFuQ/2YvmIq+3PTHyekWZjaxJehaThP4FWTLUuRoSAAA=
```

---

### 📌 Preset 2: Create Kinetic & Create New Age Power
* **Tests**: Large Water Wheel SU generation, Generator Coil FE conversion, Advanced Motor driving machines, Create node card HUD.
```text
GTBOARD:H4sIAAAAAAAA/81WQY/bRBSebDZpkg0IBBUcQPIBIS6W1rHTOFUF2e4mELq7h03CtqdoPH5ORnU81niyaThVnDjyCxA/hZ/CT+DSM7yxk90NJKpLW9QcHHv85nvvfe/7bNcIqZJSJHxIaoSQYpnUPZrAyVxSxUXUcUj6q5I7Yq7iuUqjCmVSpjMxj1TneXYfF9iURgy++TNbqJD9iM6A1AdKQpIYo4irpErqNFQgI8S+gqSiochHTAJVcD9JA8dzHVghe9zfcWdfLWMg+/1h9wzLKvNIV5XmLJK7TESK8gj8M8qm+H+si1zVw/Em+XSFGVI5gfECT+V4MQUIC6TKk17I4xh8bKc+uwWwbqpAKjw5E/48BASsKY2hhhwk2Tv9oUgqMZU0DCHUoVkLX/i+xZq2G5iHFmWm41kts93wAtNzbNe2/UY7cClCSWA8hjVUgRzw5FuIAIcgJEK9J65AslCwp5gdcw+GR+cnRxcnBVLjyUOc17leTlu4AImTAmxhPxbJ4845WXFTY1T63wGfTDUhz3AWCyGfJiqd82oWn0wUg/m/ycEKdTWT5VBzX3vUP+8O+8fjwSjL8qTzZD31D7JOjnHvRMhl39+JWSB1JHMeKu7pxq4V8+GpjjQudaRxqSOLpKprv+S+mmKSFzVSi6WIQSoOCTZ6Ryu2O7oWI3lVEccvEfFer7tVupVAYKn3A8iGfesyE2mpdzrqn9yo9J3yTh63fJ6hjCNYjCm2NllrcswEf03LFEe7PGPZbR8CZpkObTVMh+GZZ9me6R26FprHudd0g03PaKw3aRr5+1bT/LTVNB9nAt/kZtMxB93T7vHwAi3T6+awzFbEXX55/7pn4xjD3rpZfns3nvj/r4X/i1mof6Ur8cczoQX5dt4vdpv6bmA5ZgMaaBjXYabnW74Jlt107MOWZ7P2S94v5LWs8vyPV7fKJjNvwir/5HqHVY5WYcaZDsttlV/I2hPlQCLQWnwZAw/X0suK/f5GeAJ5enDrRWY0rYYxGBnm18amafVKr6uPmxWuP80qKcO5PibyPT7zCadISkyEQv714teDMiktNFGdn79a9ZfqDyzXbrbattmyWMt0AtYw3Ra0TadtteCe3XAZxe+bkuIKNf3ZI1S84sz40uiGwJTkjKulMVgmCmZo3Wmqng7v3DBKo9Xw08sfhZitzVIlB+i6CGG0vvRE9iroXylmqVhzkYUN8qjvP8saKiuRbc3DYJGU0QervWQjc77tWzPnm8rtzBlJj9ckkb8B5GpqOdELAAA=
```

---

### 📌 Preset 3: Multiblock Construction BOM & Compound Module (GTCEu)
* **Tests**: Electric Blast Furnace (EBF) + Large Chemical Reactor (LCR), Multiblock BOM window (`Shift + B`), Compound Module Card packaging (`📦`) and expanding (`⤢`).
```text
GTBOARD:H4sIAAAAAAAA/81WTW/bRhBdWbZMfRhOA7uXtgAPQdGiIGBLiqT4UtuyXKmwEsNW0gQoIiyXQ2lhkkssl3bUQ4Geey/af9Of1Et7bWf5ASmu7bhxClgXgruzs2/evHlihZAyWQmEA1GFELJUIjWbRnAQS6q4CHZfkeRXJqsiVmGskqhCiZSoL+JAff1Huo8LbEoDBvmCQZYD6gOpnioAzxwEE6HKpEY9BTLA1OcQGToT+WiiGMQ7kQ4bcx1mkCXuXLm+rGYhkOXBqDdERCXqOCJIARXJw5BK6nngDWNP8dDjINONTSYpO8MMvQDkZHYMkkGgcM8pkvXIB43oODuLqw8KpDaITmM7UgkDuhLO8LmZAvLPx5AkGk+pYlODVJE6JnmYBg9fmMfiAmSRVKRQQiYv+rISeehkpM4Rztla8YUzcMhKckmBrIX6XBfLUxTBFomx54cg6QRS+tcgVlel0cxdDbRINsKZFN4sgtMQwFngoUBWB9ERdl3qYkeauaXhiyJZ8+kkAMXZocBYkrRrfRAdxNQ70vCSSAyT4HrAsNhRSjqurTPBvREkmFUs8fCqgQjYFHzOqHcZQYlUE7aG9E3vudr97W0NbaSdM/u6DvMLpHh770vd2CzbVY1N0vVclzMOAZvpVYMYjCqYCDkjtUVqtJZ48B7irgykCG7Q9obPA0D5uWqHY+SivK/ZWlS4rlAEimKgM6Rsis+uhpUhSET5adpr0PxLzsa2RyM1dmPEwaBAyjw69HgYgoNF1PyFJHkpBWLwaCic2ANMWlFUTkDNJWCE88kopMgfNd3t9jY0HWur3QKrCXbdslvArI6z1X7M7OaTRr2FqSQwHkKeqkCqPPpGs06xMXjVmjjHhnmCneHtePfpaO/pwd7JQYFUeLSPYnyql5MSTkCi+QCWsByK6OXuIIWOI8aodPrAJ1NNyi/Ygwshz7LBzXpwI0MIMxXCSNNe7R31uqOTQXfce57e9Sr3P4M8SOvpZgrCWX0H9zXkVU+orWss5JL5uJeFm/s63DxMw4ukrGv5jjtqitf9WSGVUAqcHsUhwsJXtSnr0XhNck2+7dOv7+jTm6daaB5Ekfkux/4kd+bswPhf3n1zxH1w8enNLt6/Ny4+vZOL9++vi/fvjYv/b58o/8HAPe2745yKsQSqO3FHA+9fY+Bb9U6jA07DoluPbau5xdDAG21mNTptaFG3RcGxLxl4/4MauHoPA7+aoQ9h4Ndxf42BH+lws5uFmydp+K0N/EeSO3XJlZgwV2vKzE6u1RT8t3OlCuTvWW//0PzKPOqeZCb9uXnZto8lSoFp+kwPpWK6QprzKsz9Z0OTB1EISUj+5a+JXrrdH/vt1FMkK0x4Qv798/d/lcjKhWZl96dfF+ztUb3tdjrtTt0CG7atZh061pNm27YajWaraTcpqzcoGqriCoX9WR8lYmkpmkNQqOdYO0lX+CGOAvpOaZpoaPfs9zl/NMhan7z+IISfj0yZVHH0gpSDlH90FVcKP5HsrXjACnkwcN6kFZWUSI/ejpwSTkN2NoP6ModK/gEwJFAokQ0AAA==
```

---

### 📌 Preset 4: Thermal Boiler & Steam Dynamo Power Loop (Thermal / Systeams)
* **Tests**: Compression Boiler Water $\rightarrow$ Steam calculation, Steam Dynamo FE generation, fluid balance.
```text
GTBOARD:H4sIAAAAAAAA/71VTW/bRhAdSZZCyQoSBClg5KRDrwREWYpIHxLZ+qhdKE5hK1+XCCvuSFqY5BLLlV3n1mP/QH9Pf1IP7bkdckXbSRxERT50kbgczrz35j2xBlCFciQ5JjUAKFagPmMJDlaKaSGjXhuyTxXuyJWOVzqrKlSgwkK5inTvd3OfDvwli3x8+pc5sGArYiFC+VQjC6tQZ4FGFVHTc0ystAdsL7SPq70kLbCgKPgHJ1v6MqYGo/GLowEBqIjolvlvPjf/FaO5t86/F4oIfcXmeu8iLTIYPj59D0cJfvBlpBkV8WfMX9J3PwWyninoJjzSS1QhC/ZmUgSopr4MY4VJQnoWoCqSUSDiGDmBroc3WuTQC2CJ5JnkqwCpZU0ztUA9EaigOH5ZAitmigUBBmmpwfyj5/GW63Qe2y7zZnbbcVq25zR925t3WNvxnZnb7FIrhb6IMW9FGxDJTxghrVoqGnVXnqPyA+mf0XSafTrZPx7snwwKUBPJAbniOD3OKJygIj8gUdiKZfK6d2ygl6DmM8UPUSyWqSR/kPAXUp0lOnPTWvgds+YbskyNUgQxhbO4nKSK1w+H+5Pp85Pp6XA8MoPe5Pu24L4h06cdLaS6POKfbluAOgm6CrSYpeSuvPGgf13aOMhKS1BNCbwSXC9pzD81qMVKxqi0wITY3knDMXxxtSv42nkpjoa3mtWaS7LB3hzNxm9cbhST7xXTTeLxMI8Hv6TZcpr1+UbBcNus22ztNm0fOQXDmz22vW6rZe82ERnnXnfe2f1MMApfFAz9f4Lx4IawU6PO+5HYHo6H/cnJUX86Gm6QiFv6fSoL9cwAjUFWtGkKegPI7V6ZK2qT285wP8hNZ4D+fG05SQo9+Th9jezPumE/aRg0Vz8MrMZo2LhaS/7WSpUrbvYPuJkdSlD2ZSDVv38/fVuB8kUqQu+3wzX2zFUOd3bnHB2763c6dptx13Y91rR5Z+Y6LnKXzzoWlLXQ5NSdibH7msov8oIojqWMKYzLzA+9sz+vlWLReqHZ5Tspw9z+Vdim+NDLKXNMzbjcmisZZvbbSAIiJ6Ij/qshU9HSPLqZLhVy9vrZNdTXOVT4D2E11DFICAAA=
```
