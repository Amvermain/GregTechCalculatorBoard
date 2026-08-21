# [03-04] Recipe Search, Filters, Hotkey HUD, Guidebook & Toast UI (Search & Tools)

> 📍 **GTCalcBoard Technical Specification Series**
> [[00] System Overview](00_OVERVIEW.md) ➔ [[01] Core Domain](01_CORE_DOMAIN_AND_MODELS.md) ➔ [[02] Math Engine](02_MATH_AND_ALGORITHMS.md) ➔ [[03-01] Canvas & Node Cards](03_01_CANVAS_AND_NODE_CARDS.md) ➔ [[03-02] Machine Config & Addons](03_02_MACHINE_CONFIG_AND_ADDONS.md) ➔ [[03-03] Page Summary & Dashboard](03_03_PAGE_SUMMARY_AND_DASHBOARD.md) ➔ **[03-04] Search, Tools & Guide** ➔ [[04] Multiplayer](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] External Integration](05_INTEGRATION_AND_I18N.md)

---

## 1. Async Recipe Search Engine & Dialog (`RecipeSearchDialog`)

Unified search dialog for finding recipes across tens of thousands of entries in real time and spawning them directly onto the canvas:

* **Parametric Multi-Token Search**:
  - `&` / space (AND), `|` (OR), `!` (NOT)
  - `@gtceu` (Mod ID), `#logs` (Item Tag), `[chemical_reactor]` (Category ID), `"exact"` (Exact match)
* **Drag to Search**: Dragging from an open socket onto empty canvas automatically filters recipes that consume or produce that material.

### 1.1 `RecipeSearchDialog` UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Search Dialog Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 460px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #2a2e39; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 14px;">🔍</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 13px;">Recipe Search & Node Spawner</span>
      </div>
      <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 2px 7px; font-size: 11px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
    </div>
    <!-- 2. Search Input & Filter Button -->
    <div style="padding: 10px 12px; background: #181d26; border-bottom: 1px solid #2d3748; display: flex; gap: 8px; align-items: center;">
      <input type="text" value="@gtceu benzene" style="background: #14171e; border: 1px solid #0284c7; border-radius: 3px; color: #38bdf8; padding: 4px 8px; font-size: 12px; flex: 1;" />
      <button style="background: #1e293b; border: 1px solid #475569; border-radius: 3px; color: #cbd5e1; padding: 4px 8px; font-size: 11px; cursor: pointer;">🔍 Category Filter</button>
    </div>
    <!-- 3. Search Results List -->
    <div style="padding: 10px; display: flex; flex-direction: column; gap: 6px;">
      <!-- Result 1 -->
      <div style="background: #14171e; border: 1px solid #334155; border-radius: 4px; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span>⚗️</span>
          <div>
            <div style="color: #f8fafc; font-weight: bold; font-size: 12px;">Benzene Cracking ➔ Ethylene & Gasoline</div>
            <div style="color: #64748b; font-size: 10px;">Large Chemical Reactor | Duration: 1.25s | Power: 480 EU/t (LV)</div>
          </div>
        </div>
        <button style="background: #0284c7; border: 1px solid #38bdf8; border-radius: 3px; color: #ffffff; padding: 3px 8px; font-size: 10px; font-weight: bold; cursor: pointer;">+ Create Node</button>
      </div>
      <!-- Result 2 -->
      <div style="background: #14171e; border: 1px solid #334155; border-radius: 4px; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span>⚡</span>
          <div>
            <div style="color: #f8fafc; font-weight: bold; font-size: 12px;">Crude Oil High-Pressure Distillation</div>
            <div style="color: #64748b; font-size: 10px;">Distillation Tower | Duration: 4.00s | Power: 1,920 EU/t (MV)</div>
          </div>
        </div>
        <button style="background: #0284c7; border: 1px solid #38bdf8; border-radius: 3px; color: #ffffff; padding: 3px 8px; font-size: 10px; font-weight: bold; cursor: pointer;">+ Create Node</button>
      </div>
    </div>
  </div>
</div>

---

## 2. Recipe Category Filter Modal (`RecipeFilterDialog`)

Modal for toggling specific recipe categories or blacklisting entire machine types:

### 2.1 `RecipeFilterDialog` UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Filter Dialog Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 340px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #2a2e39; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 6px;">
        <span>🔍</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 12px;">Recipe Category Filters</span>
      </div>
      <span style="color: #94a3b8; font-weight: bold; cursor: pointer; font-size: 11px;">✕</span>
    </div>
    <!-- 2. Search & Select All/None Buttons -->
    <div style="padding: 8px; background: #181d26; border-bottom: 1px solid #2d3748; display: flex; flex-direction: column; gap: 6px;">
      <input type="text" placeholder="Filter category..." style="background: #14171e; border: 1px solid #334155; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 11px;" />
      <div style="display: flex; gap: 4px;">
        <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 2px; color: #cbd5e1; padding: 1px 6px; font-size: 10px; cursor: pointer;">Select All</button>
        <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 2px; color: #cbd5e1; padding: 1px 6px; font-size: 10px; cursor: pointer;">Deselect All</button>
      </div>
    </div>
    <!-- 3. Category Checkbox List -->
    <div style="padding: 8px; display: flex; flex-direction: column; gap: 4px; max-height: 120px;">
      <label style="display: flex; align-items: center; justify-content: space-between; background: #14171e; padding: 3px 6px; border-radius: 3px; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 6px;">
          <input type="checkbox" checked style="cursor: pointer;" />
          <span style="color: #f8fafc; font-size: 11px;">Chemical Reactor</span>
        </div>
        <span style="color: #64748b; font-size: 10px;">342 recipes</span>
      </label>
      <label style="display: flex; align-items: center; justify-content: space-between; background: #14171e; padding: 3px 6px; border-radius: 3px; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 6px;">
          <input type="checkbox" checked style="cursor: pointer;" />
          <span style="color: #f8fafc; font-size: 11px;">Distillation Tower</span>
        </div>
        <span style="color: #64748b; font-size: 10px;">88 recipes</span>
      </label>
      <label style="display: flex; align-items: center; justify-content: space-between; background: #14171e; padding: 3px 6px; border-radius: 3px; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 6px;">
          <input type="checkbox" style="cursor: pointer;" />
          <span style="color: #94a3b8; font-size: 11px;">Centrifuge</span>
        </div>
        <span style="color: #64748b; font-size: 10px;">194 recipes</span>
      </label>
    </div>
  </div>
</div>

---

## 3. Navigation Tab Bar, Toolbar & Hotkey HUD (`HotkeyHudWidget`)

### 3.1 Tab Bar & Toolbar UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Combined Toolbar Widget -->
  <div style="display: flex; flex-direction: column; gap: 8px; width: 460px;">
    <!-- Top Workspace & Page Tab Bar -->
    <div style="background: #1e222b; border: 1px solid #3d4455; border-radius: 4px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between;">
      <div style="display: flex; gap: 4px;">
        <span style="background: #0284c7; color: #ffffff; font-weight: bold; padding: 3px 8px; border-radius: 3px; font-size: 11px;">👤 Personal: Benzene</span>
        <span style="background: #14171e; color: #94a3b8; padding: 3px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">+ New Tab</span>
        <span style="background: #1e1b4b; border: 1px solid #4338ca; color: #c7d2fe; padding: 3px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">👥 Team Share (FTB)</span>
      </div>
      <span style="background: #064e3b; color: #6ee7b7; padding: 1px 6px; border-radius: 2px; font-size: 10px;">● Synced</span>
    </div>
    <!-- Bottom Quick Action Toolbar -->
    <div style="background: #1e222b; border: 1px solid #3d4455; border-radius: 4px; padding: 4px 8px; display: flex; gap: 6px; align-items: center;">
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 3px 8px; font-size: 11px; cursor: pointer;">🔍 Search Recipes</button>
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 3px 8px; font-size: 11px; cursor: pointer;">⚡ Auto Wire</button>
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #fbbf24; padding: 3px 8px; font-size: 11px; cursor: pointer;">📊 Dashboard (B)</button>
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #a78bfa; padding: 3px 8px; font-size: 11px; cursor: pointer;">📖 Guide (F1)</button>
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 3px 8px; font-size: 11px; cursor: pointer; margin-left: auto;">⚙ Settings</button>
    </div>
  </div>
</div>

---

### 3.2 Hotkey HUD Widget (`HotkeyHudWidget`)

Persistent bottom-left HUD showing vital keyboard shortcuts, collapsible via `[-]` or clicking the `[?]` chip:

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Hotkey HUD Widget Container -->
  <div style="background-color: #0b1120; border: 1.5px solid #1e293b; border-radius: 5px; width: 220px; box-shadow: 0 4px 16px rgba(0,0,0,0.5); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #1e293b; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between;">
      <span style="font-weight: bold; color: #38bdf8; font-size: 11px;">Hotkeys HUD</span>
      <span style="color: #64748b; font-size: 10px; cursor: pointer;" title="Collapse">✕</span>
    </div>
    <!-- 2. Key Lines -->
    <div style="padding: 6px 8px; display: flex; flex-direction: column; gap: 4px; font-size: 10px;">
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">B</span>
        <span style="color: #94a3b8;">Global Balance Dashboard</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">T</span>
        <span style="color: #94a3b8;">Cycle Time Unit (/t, /s, /min)</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Space</span>
        <span style="color: #94a3b8;">Open Recipe Search</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Shift+Drag</span>
        <span style="color: #94a3b8;">1:1 Auto-Ratio Rate Match</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Ctrl+G</span>
        <span style="color: #f5d0fe;">Group / Expand Module</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Ctrl+Z / Y</span>
        <span style="color: #94a3b8;">Undo / Redo</span>
      </div>
    </div>
  </div>
</div>

---

## 4. In-Game Guidebook Modal (`GuideDialog`)

Interactive manual opened via `F1` or the Guidebook button, providing 8 categorized instruction tabs with visual keycap highlights:

* **8 Sidebar Categories**:
  1. `Basics (BASICS)`: Navigation, zoom, selection, dragging
  2. `Search (SEARCH)`: EMI and drag-to-search
  3. `Wiring (WIRING)`: Bézier wire connections and magnet snap
  4. `Auto-Ratio (MASTER)`: 1:1 rate matching and Auto-Ratio BFS
  5. `Compound Modules (MODULE)`: Sub-graph encapsulation (`Ctrl+G`)
  6. `Team Collaboration (TEAM)`: FTB Teams editing and distributed locks
  7. `Page Management (PAGES)`: Multi-tab layout and global dashboard
  8. `Shortcuts (SHORTCUTS)`: Full keyboard hotkey list

### 4.1 `GuideDialog` UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Guide Dialog Container -->
  <div style="background-color: #121722; border: 1.5px solid #3d4b66; border-radius: 6px; width: 500px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #1c2433; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #2e3a4e;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span>📖</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 13px;">GTCalcBoard Comprehensive Guidebook</span>
      </div>
      <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 2px 7px; font-size: 11px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
    </div>
    <!-- Main Body: Sidebar + Guide Content -->
    <div style="display: flex; min-height: 200px;">
      <!-- Left Sidebar Tabs -->
      <div style="width: 145px; background: #0f131c; border-right: 1px solid #232d3d; padding: 8px; display: flex; flex-direction: column; gap: 3px;">
        <span style="background: #0284c7; color: #ffffff; font-weight: bold; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">Basics (BASICS)</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">Recipe Search</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">Wiring</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">Auto-Ratio</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">Modules (Ctrl+G)</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">Team Locks</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">Shortcuts</span>
      </div>
      <!-- Right Content View -->
      <div style="flex: 1; padding: 12px; display: flex; flex-direction: column; gap: 8px;">
        <div style="font-weight: bold; color: #38bdf8; font-size: 13px; border-bottom: 1px solid #232d3d; padding-bottom: 4px;">1. Canvas Viewport Navigation & Selection</div>
        <div style="color: #cbd5e1; font-size: 11px; line-height: 1.5;">
          • <span style="background: #2a3447; border: 1px solid #4a5c7d; border-radius: 2px; padding: 1px 4px; color: #ffffaa; font-family: monospace;">Right-Click Drag</span> or <span style="background: #2a3447; border: 1px solid #4a5c7d; border-radius: 2px; padding: 1px 4px; color: #ffffaa; font-family: monospace;">Middle-Click Drag</span> pans the infinite canvas.<br/>
          • <span style="background: #2a3447; border: 1px solid #4a5c7d; border-radius: 2px; padding: 1px 4px; color: #ffffaa; font-family: monospace;">Mouse Wheel</span> zooms in and out (0.25x to 2.5x).<br/>
          • <span style="background: #2a3447; border: 1px solid #4a5c7d; border-radius: 2px; padding: 1px 4px; color: #ffffaa; font-family: monospace;">Left-Click Drag</span> creates a marquee box to select multiple nodes.
        </div>
      </div>
    </div>
  </div>
</div>

---

## 5. Global Action Toast Notifications (`BoardToast`)

Rendered at the top of all UI layers (bottom-center `screenHeight - 48px`) with smooth fade animations ($2,200\text{ms}$ duration, $250\text{ms}$ fade in/out):

* **Trigger Events**:
  - Clipboard copy/cut/paste (`Ctrl+C / V`)
  - Auto-save completion
  - Team lock acquisition, release, or expiry
  - Blueprint export and import

### 5.1 `BoardToast` UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Toast Floating Card -->
  <div style="background: rgba(24, 28, 38, 0.95); border: 1.5px solid #4e5d7a; border-radius: 4px; padding: 6px 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.6); display: inline-flex; align-items: center; gap: 8px;">
    <span style="color: #38bdf8; font-size: 13px;">✔</span>
    <span style="color: #ffffff; font-weight: bold; font-size: 11px;">12 nodes copied to clipboard. (Press Ctrl+V to paste)</span>
  </div>
</div>

---

## 6. Interactive Onboarding Tutorials (`tutorial/*`)

Guides newcomers through fundamental workflows (node creation, wiring, ratio balancing, compound modules) via pulsing highlight borders and instructional popups.

* **`TutorialManager`**: Singleton tutorial state tracker detecting milestone completion.
* **`TutorialOverlay`**: Renders pulse glow rings around target widgets/ports and instructions.

---

> ➡️ **Next Chapter**: [[04] Multiplayer Concurrency & Network Protocols](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)
