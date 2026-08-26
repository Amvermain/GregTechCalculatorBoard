# [03-03] Page Summary & Global Balance Dashboard UI (Summary & Dashboard)

> 📍 **GTCalcBoard Technical Specification Series**
> [[00] System Overview](00_OVERVIEW.md) ➔ [[01] Core Domain](01_CORE_DOMAIN_AND_MODELS.md) ➔ [[02] Math Engine](02_MATH_AND_ALGORITHMS.md) ➔ [[03-01] Canvas & Node Cards](03_01_CANVAS_AND_NODE_CARDS.md) ➔ [[03-02] Machine Config & Addons](03_02_MACHINE_CONFIG_AND_ADDONS.md) ➔ **[03-03] Page Summary & Dashboard** ➔ [[03-04] Search, Tools & Guide](03_04_RECIPE_SEARCH_AND_TOOLS.md) ➔ [[04] Multiplayer](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] External Integration](05_INTEGRATION_AND_I18N.md)

---

## 1. Current Page Summary Overlay (`SummaryOverlay`)

Positioned on the right side of the screen as a persistent or collapsible panel, providing real-time aggregation of total power consumption, active machine counts, raw external inputs, and net final products.

* **Global UI State Persistence (`BoardManager`)**: Clicking the header `[»]` button toggles and commits the collapsed state to `BoardManager.summaryOverlayCollapsed` NBT, preserving user preference across page tab switches and session reloads.

### 1.1 `SummaryOverlay` UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Summary Panel Container -->
  <div style="background-color: #1a1e26; border: 1.5px solid #3d4455; border-radius: 6px; width: 260px; box-shadow: 0 4px 16px rgba(0,0,0,0.5); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #242934; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 6px; font-weight: bold; color: #ffae00;">
        <span>⚡</span>
        <span style="color: #ffffff;">Current Page Balance</span>
      </div>
      <span style="color: #94a3b8; font-weight: bold; cursor: pointer;" title="Collapse Panel">»</span>
    </div>
    <!-- 2. Power & Machine Totals -->
    <div style="padding: 8px 10px; background: #14171e; border-bottom: 1px solid #2d3748; display: flex; flex-direction: column; gap: 4px;">
      <div style="display: flex; justify-content: space-between;">
        <span style="color: #fbbf24; font-weight: 500;">Total Power:</span>
        <span style="color: #f8fafc; font-weight: bold;">⚡ -4,800 EU/t <span style="color: #38bdf8; font-size: 10px;">(EV)</span></span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="color: #cbd5e1;">Active Machines:</span>
        <span style="color: #e2e8f0; font-weight: 500;">12 <span style="color: #64748b; font-size: 10px;">(Details)</span></span>
      </div>
    </div>
    <!-- 3. Balance Lists -->
    <div style="padding: 10px; display: flex; flex-direction: column; gap: 8px;">
      <!-- Raw Inputs Section -->
      <div>
        <div style="font-size: 11px; font-weight: bold; color: #f87171; margin-bottom: 4px; display: flex; align-items: center; gap: 4px;">
          <span>▼</span> Net Raw Inputs
        </div>
        <div style="background: #1f2430; border-radius: 4px; padding: 4px 6px; display: flex; flex-direction: column; gap: 3px; font-size: 11px;">
          <div style="display: flex; justify-content: space-between;">
            <span style="color: #cbd5e1;">Benzene</span>
            <span style="color: #f87171; font-weight: 500;">-100 mB/s</span>
          </div>
          <div style="display: flex; justify-content: space-between;">
            <span style="color: #cbd5e1;">Hydrogen</span>
            <span style="color: #f87171; font-weight: 500;">-2.0k mB/s</span>
          </div>
        </div>
      </div>
      <!-- Net Outputs Section -->
      <div>
        <div style="font-size: 11px; font-weight: bold; color: #34d399; margin-bottom: 4px; display: flex; align-items: center; gap: 4px;">
          <span>▲</span> Net Final Products
        </div>
        <div style="background: #1f2430; border-radius: 4px; padding: 4px 6px; display: flex; flex-direction: column; gap: 3px; font-size: 11px;">
          <div style="display: flex; justify-content: space-between;">
            <span style="color: #cbd5e1;">Ethylene</span>
            <span style="color: #34d399; font-weight: bold;">+200 mB/s</span>
          </div>
          <div style="display: flex; justify-content: space-between;">
            <span style="color: #cbd5e1;">Byproduct Slag</span>
            <span style="color: #34d399; font-weight: bold;">+50 mB/s</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

## 2. Global Balance Dashboard Dialog (`GlobalBalanceDashboardDialog`)

Opened via hotkey `B` to inspect factory-wide balances across multiple selected board pages.

### 2.1 `GlobalBalanceDashboardDialog` UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Dashboard Dialog Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 560px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #2a2e39; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 14px;">📊</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 13px;">Global Balance Dashboard</span>
      </div>
      <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 2px 7px; font-size: 11px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
    </div>
    <!-- Main Layout: Sidebar + Content -->
    <div style="display: flex; min-height: 220px;">
      <!-- Left Sidebar: Page Selector -->
      <div style="width: 170px; background: #181d26; border-right: 1px solid #2d3748; padding: 10px; display: flex; flex-direction: column; gap: 6px;">
        <div style="font-weight: bold; color: #94a3b8; font-size: 11px; margin-bottom: 2px;">Select Aggregated Pages</div>
        <div style="display: flex; flex-direction: column; gap: 4px;">
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer; background: #1e293b; padding: 4px 6px; border-radius: 3px;">
            <input type="checkbox" checked style="cursor: pointer;" />
            <span style="color: #f8fafc; font-size: 11px;">1. Oil Refining Line</span>
          </label>
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer; background: #1e293b; padding: 4px 6px; border-radius: 3px;">
            <input type="checkbox" checked style="cursor: pointer;" />
            <span style="color: #f8fafc; font-size: 11px;">2. Ethylene & Polymers</span>
          </label>
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer; background: #1e293b; padding: 4px 6px; border-radius: 3px;">
            <input type="checkbox" checked style="cursor: pointer;" />
            <span style="color: #f8fafc; font-size: 11px;">3. Gas Turbine Plant</span>
          </label>
        </div>
        <!-- Global Power Card -->
        <div style="margin-top: auto; background: #14171e; border: 1px solid #334155; border-radius: 4px; padding: 6px;">
          <div style="color: #fbbf24; font-size: 10px; font-weight: bold;">Global Energy Balance</div>
          <div style="color: #34d399; font-size: 11px; font-weight: bold;">+12,400 EU/t (Net Surplus)</div>
        </div>
      </div>
      <!-- Right Main: Filter Tabs & Material Balance List -->
      <div style="flex: 1; padding: 10px; display: flex; flex-direction: column; gap: 8px;">
        <!-- Filter Tabs & Search Box -->
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <div style="display: flex; gap: 4px;">
            <span style="background: #0284c7; color: #ffffff; padding: 2px 8px; border-radius: 3px; font-size: 10px; font-weight: bold; cursor: pointer;">All</span>
            <span style="background: #1e293b; color: #f87171; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">Deficits (2)</span>
            <span style="background: #1e293b; color: #34d399; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">Surplus (4)</span>
            <span style="background: #1e293b; color: #38bdf8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">Balanced (1)</span>
          </div>
          <input type="text" placeholder="Search item..." style="background: #14171e; border: 1px solid #334155; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; width: 90px;" />
        </div>
        <!-- Material Balance Table -->
        <div style="display: flex; flex-direction: column; gap: 4px;">
          <!-- Item Row 1 (Deficit) -->
          <div style="background: #181d26; border: 1px solid #7f1d1d; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #ef4444;">●</span>
              <span style="color: #f8fafc; font-weight: 500;">Crude Oil</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="color: #f87171; font-weight: bold;">-800 mB/s (Deficit)</span>
              <span style="color: #64748b; font-size: 10px;">Drill-down ➔</span>
            </div>
          </div>
          <!-- Item Row 2 (Surplus) -->
          <div style="background: #181d26; border: 1px solid #064e3b; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #10b981;">●</span>
              <span style="color: #f8fafc; font-weight: 500;">Polyethylene</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="color: #34d399; font-weight: bold;">+120 /s (Surplus)</span>
              <span style="color: #64748b; font-size: 10px;">Drill-down ➔</span>
            </div>
          </div>
          <!-- Item Row 3 (Balanced) -->
          <div style="background: #181d26; border: 1px solid #0369a1; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #38bdf8;">●</span>
              <span style="color: #f8fafc; font-weight: 500;">Ethylene Gas</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="color: #38bdf8; font-weight: bold;">0.00 (Balanced)</span>
              <span style="color: #64748b; font-size: 10px;">Drill-down ➔</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

## 3. Item Contribution Drill-Down Popup (`ItemContributionPopup`)

Decomposes the selected item's sources and sinks across pages in $O(N)$ complexity.

### 3.1 `ItemContributionPopup` UI Wireframe

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Popup Container -->
  <div style="background-color: #1e293b; border: 1.5px solid #0284c7; border-radius: 6px; width: 320px; box-shadow: 0 8px 24px rgba(2,132,199,0.3); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #0f172a; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #334155;">
      <div style="font-weight: bold; color: #38bdf8; font-size: 11px;">[Ethylene Gas] Production/Consumption by Page</div>
      <span style="color: #94a3b8; cursor: pointer; font-size: 10px;">✕</span>
    </div>
    <!-- 2. Breakdown Rows -->
    <div style="padding: 8px; display: flex; flex-direction: column; gap: 4px;">
      <!-- Page 1 -->
      <div style="background: #14171e; border-radius: 3px; padding: 4px 6px; display: flex; justify-content: space-between;">
        <span style="color: #cbd5e1; font-size: 11px;">1. Oil Refining Line:</span>
        <span style="color: #34d399; font-weight: bold; font-size: 11px;">+500 mB/s (Production)</span>
      </div>
      <!-- Page 2 -->
      <div style="background: #14171e; border-radius: 3px; padding: 4px 6px; display: flex; justify-content: space-between;">
        <span style="color: #cbd5e1; font-size: 11px;">2. Ethylene & Polymers:</span>
        <span style="color: #f87171; font-weight: bold; font-size: 11px;">-500 mB/s (Consumption)</span>
      </div>
      <div style="border-top: 1px solid #334155; margin: 4px 0;"></div>
      <!-- Net Balance -->
      <div style="display: flex; justify-content: space-between; padding: 0 4px;">
        <span style="color: #94a3b8; font-size: 11px;">Cross-Page Net Balance:</span>
        <span style="color: #38bdf8; font-weight: bold; font-size: 11px;">0.00 mB/s (100% Matched)</span>
      </div>
    </div>
  </div>
</div>

---

## 4. Multiblock Construction Bill of Materials Dialog (`MultiblockBOMDialog`)

Opened via hotkey `M` or `Shift + B` (and the `[📦 BOM]` toolbar button), this dialog aggregates required construction casings, coils, hatches, buses, and controllers across the current or all workspace pages.

* **Category Tab Filtering**: `All`, `Casings`, `Coils`, `Hatches & Buses`, `Controllers`.
* **Stack Unit Formatting**: Automatically displays quantities in standard inventory stack notation (e.g. `3 stacks + 48 (240 items)`).
* **Dual Energy Hatch Toggle**: `⚡ 1x Normal Energy Hatch ↔ 2x 1-Tier Lower Energy Hatches` for real-time parts substitution.
* **One-Click EMI Integration (`[★ Register in EMI]`)**: Registers the full bill of materials as a virtual recipe root in EMI for seamless recipe ingredient tracking.
* **Clipboard Export (`[📋 Copy List]`)**: Copies the formatted bill of materials list to the system clipboard.

---

> ➡️ **Next Chapter**: [[03-04] Recipe Search, Filters, Hotkey HUD, Guide & Toast UI](03_04_RECIPE_SEARCH_AND_TOOLS.md)
