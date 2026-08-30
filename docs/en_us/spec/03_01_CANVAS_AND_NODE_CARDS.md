# [03-01] 2D Canvas Viewport & Node Card Rendering (Canvas & Node Cards)

> 📍 **GTCalcBoard Technical Specification Series**
> [[00] System Overview](00_OVERVIEW.md) ➔ [[01] Core Domain](01_CORE_DOMAIN_AND_MODELS.md) ➔ [[02] Math Engine](02_MATH_AND_ALGORITHMS.md) ➔ **[03-01] Canvas & Node Cards** ➔ [[03-02] Machine Config & Addons](03_02_MACHINE_CONFIG_AND_ADDONS.md) ➔ [[03-03] Page Summary & Dashboard](03_03_PAGE_SUMMARY_AND_DASHBOARD.md) ➔ [[03-04] Search & Toolbar](03_04_RECIPE_SEARCH_AND_TOOLS.md) ➔ [[04] Multiplayer](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] External Integration](05_INTEGRATION_AND_I18N.md)

---

## 1. 2D Viewport Coordinate Transformation (`BoardScreen`)

Transforms between screen pixel coordinates and infinite 2D canvas coordinates.

$$\text{CanvasX} = \frac{\text{ScreenX} - \text{PanX}}{\text{Zoom}}, \quad \text{CanvasY} = \frac{\text{ScreenY} - \text{PanY}}{\text{Zoom}}$$
$$\text{ScreenX} = (\text{CanvasX} \times \text{Zoom}) + \text{PanX}, \quad \text{ScreenY} = (\text{CanvasY} \times \text{Zoom}) + \text{PanY}$$

* **Zoom Range**: $0.25 \times \dots 2.5 \times$ (Exponential mouse-wheel scaling)
* **Pan Control**: Right-click drag or middle-wheel drag
* **Grid Spacing**: $20\text{px} \times \text{Zoom}$ dot grid rendering

---

## 2. Cubic Bézier Wire Rendering (`ConnectionRenderer`)

Renders directed spline connections from output port $(x_1, y_1)$ to input port $(x_2, y_2)$.

### 2.1 Control Points & Curve Formulation
$$dx = |x_2 - x_1|, \quad \text{offset} = \max\left(40.0, \, \frac{dx}{2.0}\right)$$
$$P_0 = (x_1, y_1), \quad P_1 = (x_1 + \text{offset}, y_1), \quad P_2 = (x_2 - \text{offset}, y_2), \quad P_3 = (x_2, y_2)$$
$$B(t) = (1-t)^3 P_0 + 3(1-t)^2 t P_1 + 3(1-t) t^2 P_2 + t^3 P_3 \quad (t \in [0.0, 1.0])$$

### 2.2 Wire Status Colors
* **Balanced**: `0xFF38BDF8` (Cyan), animated flow pulses
* **Deficit**: `0xFFEF4444` (Red), feedstock shortage pulsing warning
* **Surplus**: `0xFF10B981` (Emerald), safe surplus

---

## 3. High-Performance Rendering & Spatial Indexing

### 3.1 Single-Pass Batch Rendering
Eliminates redundant draw calls and per-frame `glClear` invocations by culling off-screen elements and rendering all visible nodes and connections in a single geometry batch.

### 3.2 $128 \times 128$ AABB Uniform Grid Spatial Index (`WireSpatialIndex`)
Optimizes wire hover and click hit-testing across 1,000+ wire networks from $O(E)$ exhaustive search down to **$O(\log E)$ spatial grid lookups**.

```mermaid
flowchart LR
    CUR["Mouse Cursor (CanvasX, CanvasY)"] --> GRID["WireSpatialIndex (128x128 Grid Cell Hash)"]
    GRID --> CANDIDATES["Filter 3~5 Candidate Wires in Radius"]
    CANDIDATES --> HIT["24-Segment Bézier Hit Testing (<= 6.0px / Zoom)"]
    HIT --> ACTION["Select / Delete / Hover Popup Response"]
```

---

## 4. Node Card UI Wireframes (`NodeCardRenderer`)

### 4.1 Standard Recipe Node Card

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Card Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 340px; box-shadow: 0 4px 12px rgba(0,0,0,0.5); overflow: hidden;">
    <!-- 1. Card Header -->
    <div style="background-color: #2a2e39; padding: 6px 8px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 6px; font-weight: bold; color: #ffde59;">
        <span style="font-size: 14px;">★</span>
        <span style="color: #ffffff;">Large Chemical Reactor</span>
      </div>
      <div style="display: flex; align-items: center; gap: 4px;">
        <span style="background: #222834; border: 1px solid #4a5568; border-radius: 3px; padding: 1px 5px; font-size: 10px; color: #ffeb3b; cursor: pointer;">🎯</span>
        <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
      </div>
    </div>
    <!-- Card Body -->
    <div style="padding: 8px;">
      <!-- 2. Machine Count & Mini Addons Row -->
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px;">
        <div style="display: flex; align-items: center; gap: 4px;">
          <span style="color: #8892b0; font-size: 11px;">Count:</span>
          <button style="background: #181d26; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; width: 18px; height: 18px; line-height: 14px; text-align: center; cursor: pointer; font-size: 11px;">-</button>
          <div style="background: #14171e; border: 1px solid #3d4455; border-radius: 3px; padding: 1px 6px; color: #ffffaa; font-weight: bold; font-size: 11px; text-align: center; min-width: 32px;">1.00</div>
          <button style="background: #181d26; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; width: 18px; height: 18px; line-height: 14px; text-align: center; cursor: pointer; font-size: 11px;">+</button>
          <button style="background: #181d26; border: 1px solid #3d4455; border-radius: 3px; color: #94a3b8; padding: 1px 4px; font-size: 10px; cursor: pointer;">/2</button>
          <button style="background: #181d26; border: 1px solid #3d4455; border-radius: 3px; color: #94a3b8; padding: 1px 4px; font-size: 10px; cursor: pointer;">x2</button>
        </div>
        <!-- Mini Addons Tray -->
        <div style="display: flex; gap: 3px;">
          <span style="background: #181d26; border: 1px solid #354054; border-radius: 3px; padding: 1px 4px; font-size: 10px; color: #e2e8f0;" title="Cupronickel Coil">🧲</span>
          <span style="background: #181d26; border: 1px solid #354054; border-radius: 3px; padding: 1px 4px; font-size: 10px; color: #38bdf8;" title="4x Parallel Control">⚡</span>
        </div>
      </div>
      <!-- 3. Tier, Overclock & Config Row -->
      <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 8px;">
        <span style="background: #334155; border: 1px solid #64748b; border-radius: 3px; padding: 1px 6px; font-size: 10px; font-weight: bold; color: #f1f5f9;">LV</span>
        <span style="background: #1e293b; border: 1px solid #475569; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #94a3b8;">Standard OC</span>
        <span style="background: #0f172a; border: 1px solid #0284c7; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #38bdf8; margin-left: auto;">⚙ 4x (+2)</span>
      </div>
      <!-- 4. Power & Timing Summary -->
      <div style="background: #14171e; border-radius: 4px; padding: 4px 6px; display: flex; justify-content: space-between; align-items: center; font-size: 11px; margin-bottom: 8px;">
        <span style="color: #fbbf24; font-weight: 500;">⚡ -480 EU/t <span style="color: #10b981; font-size: 10px;">(100%)</span></span>
        <span style="color: #38bdf8;">1.25s <span style="color: #94a3b8; font-size: 10px;">(0.80/s)</span></span>
      </div>
      <div style="border-top: 1px solid #353c4d; margin: 6px 0 8px 0;"></div>
      <!-- 5. Input & Output Sockets -->
      <div style="display: flex; justify-content: space-between; gap: 8px; font-size: 11px;">
        <!-- Left: Inputs -->
        <div style="display: flex; flex-direction: column; gap: 5px; flex: 1;">
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 8px; height: 8px; background-color: #10b981; border: 1.5px solid #34d399; border-radius: 50%; box-shadow: 0 0 4px #10b981;" title="Input Socket (Balanced)"></div>
            <span style="color: #cbd5e1;">Benzene</span>
            <span style="color: #94a3b8; font-size: 10px; margin-left: auto;">100 mB/s</span>
          </div>
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 8px; height: 8px; background-color: #10b981; border: 1.5px solid #34d399; border-radius: 50%; box-shadow: 0 0 4px #10b981;" title="Input Socket (Balanced)"></div>
            <span style="color: #cbd5e1;">Hydrogen</span>
            <span style="color: #94a3b8; font-size: 10px; margin-left: auto;">2.0k mB/s</span>
          </div>
        </div>
        <!-- Right: Outputs -->
        <div style="display: flex; flex-direction: column; gap: 5px; flex: 1; text-align: right;">
          <div style="display: flex; align-items: center; justify-content: flex-end; gap: 5px;">
            <span style="color: #94a3b8; font-size: 10px;">100 mB/s</span>
            <span style="color: #cbd5e1;">Cyclohexane</span>
            <div style="width: 8px; height: 8px; background-color: #38bdf8; border: 1.5px solid #7dd3fc; border-radius: 50%; box-shadow: 0 0 4px #38bdf8;" title="Output Socket (Connected)"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

### 3.2 Compound Module Card

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Module Card Container -->
  <div style="background-color: #1d172e; border: 1.5px solid #9955ff; border-radius: 6px; width: 340px; box-shadow: 0 4px 12px rgba(153,85,255,0.25); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #2a1c42; padding: 6px 8px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #3d2a5e;">
      <div style="display: flex; align-items: center; gap: 6px; font-weight: bold; color: #d8b4fe;">
        <span style="font-size: 14px;">📦</span>
        <span style="color: #ffffff;">Oil Refining & Distillation Line</span>
      </div>
      <div style="display: flex; align-items: center; gap: 4px;">
        <span style="background: #352055; border: 1px solid #7744aa; border-radius: 3px; padding: 1px 5px; font-size: 10px; color: #ddaaff; cursor: pointer;" title="Expand Module">⤢</span>
        <span style="background: #222834; border: 1px solid #4a5568; border-radius: 3px; padding: 1px 5px; font-size: 10px; color: #ffeb3b; cursor: pointer;">🎯</span>
        <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
      </div>
    </div>
    <!-- Body -->
    <div style="padding: 8px;">
      <!-- 2. Controls & Machines Count Badge -->
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px;">
        <div style="display: flex; align-items: center; gap: 4px;">
          <span style="color: #c084fc; font-size: 11px;">Scale:</span>
          <button style="background: #2a1c42; border: 1px solid #581c87; border-radius: 3px; color: #e9d5ff; width: 18px; height: 18px; line-height: 14px; text-align: center; cursor: pointer; font-size: 11px;">-</button>
          <div style="background: #14171e; border: 1px solid #581c87; border-radius: 3px; padding: 1px 6px; color: #f5d0fe; font-weight: bold; font-size: 11px; text-align: center; min-width: 32px;">1.00</div>
          <button style="background: #2a1c42; border: 1px solid #581c87; border-radius: 3px; color: #e9d5ff; width: 18px; height: 18px; line-height: 14px; text-align: center; cursor: pointer; font-size: 11px;">+</button>
          <button style="background: #2a1c42; border: 1px solid #581c87; border-radius: 3px; color: #d8b4fe; padding: 1px 4px; font-size: 10px; cursor: pointer;">/2</button>
          <button style="background: #2a1c42; border: 1px solid #581c87; border-radius: 3px; color: #d8b4fe; padding: 1px 4px; font-size: 10px; cursor: pointer;">x2</button>
        </div>
        <span style="background: #3b0764; border: 1px solid #9333ea; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #f3e8ff; font-weight: bold;">📦 18 Machines Inside</span>
      </div>
      <!-- 3. Power & Module Tag -->
      <div style="background: #14171e; border-radius: 4px; padding: 4px 6px; display: flex; justify-content: space-between; align-items: center; font-size: 11px; margin-bottom: 8px;">
        <span style="color: #fbbf24; font-weight: 500;">⚡ -4,800 EU/t <span style="color: #c084fc; font-size: 10px;">(EV Peak Load)</span></span>
        <span style="color: #d8b4fe; font-weight: bold;">[ 📦 Compound Module ]</span>
      </div>
      <div style="border-top: 1px solid #3d2a5e; margin: 6px 0 8px 0;"></div>
      <!-- 4. Promoted Boundary I/O -->
      <div style="display: flex; justify-content: space-between; gap: 8px; font-size: 11px;">
        <!-- Left: Net External Inputs -->
        <div style="display: flex; flex-direction: column; gap: 5px; flex: 1;">
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 8px; height: 8px; background-color: #10b981; border: 1.5px solid #34d399; border-radius: 50%;" title="External Input Port"></div>
            <span style="color: #e2e8f0;">Crude Oil</span>
            <span style="color: #a855f7; font-size: 10px; margin-left: auto;">1.0k mB/s</span>
          </div>
        </div>
        <!-- Right: Net External Outputs -->
        <div style="display: flex; flex-direction: column; gap: 5px; flex: 1; text-align: right;">
          <div style="display: flex; align-items: center; justify-content: flex-end; gap: 5px;">
            <span style="color: #a855f7; font-size: 10px; margin-right: auto;">400 mB/s</span>
            <span style="color: #e2e8f0;">Diesel</span>
            <div style="width: 8px; height: 8px; background-color: #38bdf8; border: 1.5px solid #7dd3fc; border-radius: 50%;" title="External Output Port"></div>
          </div>
          <div style="display: flex; align-items: center; justify-content: flex-end; gap: 5px;">
            <span style="color: #a855f7; font-size: 10px; margin-right: auto;">200 mB/s</span>
            <span style="color: #e2e8f0;">LPG</span>
            <div style="width: 8px; height: 8px; background-color: #38bdf8; border: 1.5px solid #7dd3fc; border-radius: 50%;" title="External Output Port"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

## 4. Page Tab Bar Overflow Navigation (`PageTabBarWidget`)

Provides smooth horizontal scrolling and clickable navigation when multiple board pages exceed the screen width.

* **Left / Right Overflow Indicators**:
  - Clicking the left `«` indicator scrolls left towards previous tabs (`scrollX -= 80px`).
  - Clicking the right `»` indicator scrolls right towards following tabs (`scrollX += 80px`).
* **Mouse Wheel Scrolling**: Hovering over the tab bar allows fast horizontal scrolling via mouse wheel up/down.
* **Scissor & End-Padding**: 16px right padding ensures the final page tab and `[+]` new page creation button remain fully visible without being obscured by the right overflow arrow.

---

## 5. Unused I/O Port Hiding & Selective Restore (`HiddenPortsPopup`)

Eliminates visual clutter by hiding unwanted inputs or byproduct outputs on complex machine cards.

* **Port Hiding Interaction**: **Right-clicking** any input/output port severs connected wires and hides that specific port.
* **Hidden Ports Pill Badge**:
  - Renders a pill badge (`%d Output hidden`, `%d Input hidden`, or `%d Ports hidden`) at the bottom right of the card.
  - Hovering displays an outline highlight; clicking toggles the dropdown modal below the node card.
* **Hidden Ports Popup Modal (`HiddenPortsPopup`)**:
  - Provides a list view containing `[IN]` / `[OUT]` tags, ingredient icons, localized item/fluid names, and an unhide (👁) button.
  - Clicking any entry restores that port back onto the card and immediately updates the widget layout.

---

## 6. Smart Inline Text Editing Engine (`InlineTextEditor`)

All inline editable text fields (`NodeNameEditor`, `NodeCountEditor`, `NodeParallelEditor`, `NodeTargetBatchEditor`) extend a unified `InlineTextEditor` core engine.

* **Cursor Positioning & Selection**:
  - Precise character-level cursor positioning and text range selection via mouse click and drag.
  - Keyboard range selection with `Shift + Left/Right` and `Shift + Home/End`.
  - `Ctrl + A`: Select all text.
* **Word-Level Navigation**:
  - `Ctrl + Left/Right`: Rapid word break jumping.
  - `Ctrl + Backspace / Delete`: Word-level deletion.
* **Clipboard Support**:
  - Supports `Ctrl + C` (Copy), `Ctrl + X` (Cut), and `Ctrl + V` (Paste).
  - Built-in fallback clipboard buffer for headless test environments.
* **Shortcut Isolation**: Key presses while editing are fully consumed and prevented from bubbling up to canvas global hotkeys.

---

> ➡️ **Next Sub-Specification**: [[03-02] Machine Configuration & Addon Rack UI](03_02_MACHINE_CONFIG_AND_ADDONS.md)
