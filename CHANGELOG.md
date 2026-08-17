# Changelog

<p align="center">
  <b>English</b> | <a href="CHANGELOG_KR.md">한국어</a>
</p>

All notable changes to **GregTech Calculator Board** will be documented in this file.

---

## [1.0.2] - 2026-08-18

### Added
- **Native GTCEu Live Turbine Rotor Scanner & 3D Item Grid Dialog**:
  - **48+ Real-time Rotor Scan**: Dynamically discovers all native GregTech rotors and custom modpack materials (KubeJS, Thermal, EnderIO, etc.) at runtime with zero hardcoding.
  - **3D Visual Item Grid Modal (`[⚙ 220%]` click)**: Displays authentic 3D item icons with genuine material tint colors (cyan Enderium, bronze Dragonsteel, golden Scheelite, etc.), efficiency badges, and live search.
  - **Full Multilingual Localization**: Automatically formats item names and tooltips (e.g. `Tritanium Turbine Rotor`, `Turbine Efficiency: 220%`, `Turbine Power: 220%`, `Click to equip to turbine`).
- **Rotor Power & Voltage Tier Auto-Parallel Derivation**:
  - Fully integrated with GTCEu's bytecode formula ($V_{\text{max}} = V_{\text{holder}} \times \frac{P_{\text{rotor}}}{100.0}$).
  - Selecting a rotor or changing voltage tier (`EV`, `IV`, `LuV`, `ZPM`, etc.) instantly derives and applies the optimal rated parallel count (Par) and power generation (+EU/t).
  - *Example: Nitrobenzene 32 EU/t + Scheelite Rotor (450% Power) + IV Rotor Holder $\rightarrow$ **1,152x Par, +36,864.0 EU/t, 4.00s cycle, 288.0 mB/s consumption**.*
- **Generator Dedicated Emerald Panel Theme**:
  - Generator/Turbine nodes are rendered with a distinct deep emerald dark background (`#122218`), energy green borders (`#33AA66`), `[GEN]` badge, and `§a⚡` icon for instant visual recognition.
- **Interactive Numeric Parallel Input & Wheel Control**:
  - **Direct Number Input**: Click `Par` button to type any parallel number directly (e.g. `1152`).
  - **Mouse Wheel Control**: Scroll over `Par` button to scale by 2x (or `Shift + Wheel` for +/- 1 fine stepping).
  - **Right Click**: Instantly halves parallel count.
- **Integer Machine Count & Bottleneck-Free Ceiling**:
  - Auto Ratio and Shift+Drag calculations now round all machine counts up to integers ($1, 2, 3...$).
  - Guarantees $\text{Supply} \ge \text{Demand}$ across the entire chain to prevent upstream fuel/raw material starvation.
- **Bidirectional Shift + Drag Smart Rate Matching**:
  - **Forward (Output -> Input)**: Shift+Drag from output port to input port scales consumer machine count to match producer supply.
  - **Reverse (Input -> Output)**: Shift+Drag from input (demand) port to output port scales producer machine count to match consumer demand.

### Performance & Polish
- **Optimized Recipe Search with In-Memory Pre-Indexing**:
  - Pre-indexes recipe names, inputs, outputs, and IDs into an in-memory search cache (`SearchableRecipe`) upon opening the dialog, eliminating typing lag and GC pressure completely even with tens of thousands of recipes.
- **Z-Index & Modal Occlusion Fix**:
  - Elevated modal dialogs with `translate(0, 0, 600)` and solid backgrounds to completely eliminate Z-fighting and canvas bleed-through.

### Fixed
- **Generator Power Output & Fixed Duration Overclocking**:
  - Generator recipes now correctly contribute to total power (+EU/t) instead of being treated as power consumption.
  - Tier adjustments now expand parallels rather than reducing cycle duration, matching native GTCEu large turbine mechanics.

---

## [1.0.1] - 2026-08-17

### Fixed
- **Overhauled Auto-Ratio Algorithm**:
  - Fixed runaway machine count inflation on complex multi-output branching trees (Distillation Towers, Steam Crackers) using the new Anchor-Outward Wavefront Balancer algorithm.
- **Fixed Machine Count Text Input**:
  - Fixed `Backspace` and `Delete` keys not working in machine count edit boxes.

### Added
- **Process Summary Mouse Wheel Scrolling**:
  - Added vertical scrollbar and scissor clipping to summary panels exceeding screen height.
- **Full UI Localization & Dynamic Button Widths**:
  - Applied complete I18n translation and dynamic button widths across all cards and toolbars.

---

## [1.0.0] - 2026-08-17

### Initial Release
- Full EMI and GregTech CEu Modern recipe integration (`A` or `[⚡]` in EMI to add to board).
- Infinite canvas with smooth zoom and pan.
- Full voltage tier support (ULV to MAX), standard/perfect overclocking, and parallel calculations.
- Master anchor (`🎯`) and chain-wide Auto-Ratio balancing.
- Max Tier Cap (`Cap: LV~MAX`) and Max Flow throughput optimization.
- NBT GZIP Base64 blueprint sharing (`GTBOARD:...`) with `Ctrl+C` and `Ctrl+V`.
- Real-time process summary for total power, raw inputs, and net outputs.
