# [03-02] Machine Configuration & Addon Rack UI (Machine Config & Addons)

> 📍 **GTCalcBoard Technical Specification Series**
> [[00] System Overview](00_OVERVIEW.md) ➔ [[01] Core Domain](01_CORE_DOMAIN_AND_MODELS.md) ➔ [[02] Math Engine](02_MATH_AND_ALGORITHMS.md) ➔ [[03-01] Canvas & Node Cards](03_01_CANVAS_AND_NODE_CARDS.md) ➔ **[03-02] Machine Config & Addons** ➔ [[03-03] Page Summary & Dashboard](03_03_PAGE_SUMMARY_AND_DASHBOARD.md) ➔ [[03-04] Search, Tools & Guide](03_04_RECIPE_SEARCH_AND_TOOLS.md) ➔ [[04] Multiplayer](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] External Integration](05_INTEGRATION_AND_I18N.md)

---

## 1. Component Architecture (`MachineConfigDialog`)

Modal dialog opened by right-clicking a node or clicking the `[ ⚙ ]` button on a node card:

```mermaid
flowchart TB
    subgraph Header["1. Machine & Workstation"]
        direction LR
        TITLE["Machine / Icon / Category"] ~~~ WS_SELECT["Workstation Selector (1:N)"]
    end

    subgraph Section1["2. Parallel Configuration"]
        direction LR
        PAR_INPUT["Parallel Numerical Input"] ~~~ PRESETS["Quick Presets: 1x, 4x, 16x, 64x, 256x"] ~~~ AUTO_MAX["[⚡ Auto Max Parallel]"]
    end

    subgraph Section2["3. Active Addon Tray"]
        direction LR
        ACTIVE_LIST["Active Addons (Coil, Hatch, etc.)"] ~~~ STATS_BADGE["Duration, Power, Parallel Multipliers"] ~~~ DEL_BTN["[✕] 1-Click Remove"]
    end

    subgraph Section3["4. Addon Catalog Browser"]
        direction LR
        TABS["Category Tabs: Coils, Hatches, etc."] ~~~ SEARCH["Real-time Search Filter"] ~~~ CHIP_GRID["Smart Chip Grid (1-Click Install)"]
    end

    Header --> Section1 --> Section2 --> Section3
```

---

## 2. Machine Configuration UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Dialog Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 440px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Dialog Header -->
    <div style="background-color: #2a2e39; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 14px;">⚙</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 13px;">Machine Process & Hardware Configuration</span>
        <span style="background: #334155; color: #f1f5f9; padding: 1px 6px; border-radius: 3px; font-size: 10px; font-weight: bold;">LV</span>
      </div>
      <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 2px 7px; font-size: 11px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
    </div>
    <!-- Dialog Body -->
    <div style="padding: 12px; display: flex; flex-direction: column; gap: 10px;">
      <!-- Section A: Parallel Control -->
      <div style="background: #181d26; border: 1px solid #2d3748; border-radius: 4px; padding: 8px;">
        <div style="font-weight: bold; color: #38bdf8; font-size: 11px; margin-bottom: 6px;">Base Machine Parallel Multiplier</div>
        <div style="display: flex; align-items: center; gap: 6px;">
          <input type="text" value="16" style="background: #14171e; border: 1px solid #0284c7; border-radius: 3px; color: #38bdf8; font-weight: bold; width: 42px; padding: 2px 6px; font-size: 12px; text-align: center;" />
          <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; cursor: pointer;">1x</button>
          <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; cursor: pointer;">4x</button>
          <button style="background: #0284c7; border: 1px solid #38bdf8; border-radius: 3px; color: #ffffff; font-weight: bold; padding: 2px 6px; font-size: 10px; cursor: pointer;">16x</button>
          <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; cursor: pointer;">64x</button>
          <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; cursor: pointer;">256x</button>
          <button style="background: #7c2d12; border: 1px solid #ea580c; border-radius: 3px; color: #ffedd5; font-weight: bold; padding: 2px 6px; font-size: 10px; margin-left: auto; cursor: pointer;">⚡ Auto Max</button>
        </div>
      </div>
      <!-- Section B: Installed Addons -->
      <div style="background: #181d26; border: 1px solid #2d3748; border-radius: 4px; padding: 8px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
          <span style="font-weight: bold; color: #a78bfa; font-size: 11px;">Active Hardware Addons (2)</span>
          <span style="color: #64748b; font-size: 10px;">Click to remove</span>
        </div>
        <div style="display: flex; flex-direction: column; gap: 4px;">
          <!-- Item 1: Coil -->
          <div style="background: #1f2430; border: 1px solid #475569; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span>🧲</span>
              <span style="color: #f8fafc; font-weight: 500;">Cupronickel Coil Block</span>
              <span style="background: #451a03; color: #fdba74; font-size: 9px; padding: 1px 4px; border-radius: 2px;">1,800 K</span>
            </div>
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #10b981; font-size: 10px;">Power 0.95x</span>
              <span style="color: #ef4444; font-weight: bold; cursor: pointer; padding: 0 4px;">✕</span>
            </div>
          </div>
          <!-- Item 2: Parallel Hatch -->
          <div style="background: #1f2430; border: 1px solid #475569; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span>⚡</span>
              <span style="color: #f8fafc; font-weight: 500;">EV Parallel Control Hatch</span>
              <span style="background: #1e1b4b; color: #c7d2fe; font-size: 9px; padding: 1px 4px; border-radius: 2px;">EV Tier</span>
            </div>
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #38bdf8; font-size: 10px;">Parallel 16x</span>
              <span style="color: #ef4444; font-weight: bold; cursor: pointer; padding: 0 4px;">✕</span>
            </div>
          </div>
        </div>
      </div>
      <!-- Section C: Addon Catalog Browser -->
      <div style="background: #181d26; border: 1px solid #2d3748; border-radius: 4px; padding: 8px;">
        <div style="display: flex; gap: 4px; margin-bottom: 8px; flex-wrap: wrap;">
          <span style="background: #334155; color: #f8fafc; padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: bold; cursor: pointer;">All</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">Coils</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">Parallel Hatches</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">Maintenance</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">Turbine Rotors</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">Thermal</span>
          <span style="background: #1e293b; color: #a855f7; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">+ Custom</span>
        </div>
        <!-- Search & Catalog Chips Grid -->
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 4px; max-height: 90px; overflow-y: hidden;">
          <div style="background: #14171e; border: 1px solid #334155; border-radius: 3px; padding: 3px 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <span style="color: #cbd5e1; font-size: 11px;">Kanthal Coil Block</span>
            <span style="color: #f59e0b; font-size: 9px;">2,700 K</span>
          </div>
          <div style="background: #14171e; border: 1px solid #334155; border-radius: 3px; padding: 3px 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <span style="color: #cbd5e1; font-size: 11px;">Nichrome Coil Block</span>
            <span style="color: #f59e0b; font-size: 9px;">3,600 K</span>
          </div>
          <div style="background: #14171e; border: 1px solid #334155; border-radius: 3px; padding: 3px 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <span style="color: #cbd5e1; font-size: 11px;">RTM Coil Block</span>
            <span style="color: #f59e0b; font-size: 9px;">4,500 K</span>
          </div>
          <div style="background: #14171e; border: 1px solid #334155; border-radius: 3px; padding: 3px 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <span style="color: #cbd5e1; font-size: 11px;">HSS-G Coil Block</span>
            <span style="color: #f59e0b; font-size: 9px;">5,400 K</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

## 3. In-Place Alternative Recipe Switching (`AlternativeRecipeFinder`)

Switch recipes for machines directly on the canvas without deleting nodes, preserving ports and wiring connections:

* **Entry Point**: Click `[🔄 Switch Recipe]` in the header of `MachineConfigDialog` or select from the node context menu.
* **Candidate Ranking**:
  1. **Tier 1**: Same Machine ID / Category + Same primary outputs (1st/2nd outputs).
  2. **Tier 2**: All other valid alternative recipes within the same machine category.
* **Smart Wire Preservation**:
  - Preserves incoming/outgoing wire connections for matching item/fluid IDs (`IngredientStack.getId()`).
  - Gracefully disconnects only wires whose ingredients are absent in the newly selected recipe.
  - Fully integrated with the `Undo/Redo` stack (`Ctrl+Z`).

---

## 4. UI Font Scale & Accessibility (`FontScale`)

Provides 5 preset UI scaling levels for `MachineConfigDialog` across high-DPI displays and compact viewports:

* **5 Preset Scaling Levels**:
  - `MINI (0.75x)`: Ultra-compact mode for GUI Scale 4/Auto.
  - `COMPACT (0.85x)`: Dense overview for large addon racks.
  - `NORMAL (1.0x)`: Standard Minecraft UI scale.
  - `LARGE (1.15x)`: Enhanced readability on 1440p displays.
  - `HUGE (1.30x)`: Maximum scale for 4K / accessibility.
* **Interactive Controls**:
  - `[Aa 1.0x]` Header Button: Left-click (cycle zoom in), Right-click (cycle zoom out), Mouse wheel scroll.
  - Keyboard Hotkeys: `+` / `Numpad +` (zoom in), `-` / `Numpad -` (zoom out).
  - Center-anchored matrix transformation and virtual mouse unprojection guarantee 100% pixel-perfect button hit detection.

---

> ➡️ **Proceed to next section**: [[03-03] Page Summary and Global Balance Dashboard](03_03_PAGE_SUMMARY_AND_DASHBOARD.md)
