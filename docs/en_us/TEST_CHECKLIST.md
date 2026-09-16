# GregTech Calculator Board Release QA & Smoke Test Checklist

> 🌐 **Language / 언어**: **English** | [한국어](../ko_kr/TEST_CHECKLIST.md)

This checklist is the official, streamlined verification runbook for `GregTechCalculatorBoard` release builds.
All deterministic calculations, mathematical solvers, NBT serialization, and layer boundaries are already 100% automatically validated by our automated test suites. This document focuses exclusively on the **5-Minute In-Game & Dedicated Server Smoke Test** that requires human sensory verification.

---

## 🤖 Phase 1: Automated Pre-Flight Gates (Run Before Launch)

Run these three commands in terminal. All must exit with `0 errors`:

```bash
# 1. 68 Headless JUnit Domain, Solver, & ArchUnit Architecture Tests
./gradlew test

# 2. 4-Language i18n Parity & Font Safety (No VS16) Check
python tools/check_i18n.py

# 3. Fast Rule Compliance Linter (Rule 1, 5, 6 & Anti-Hype)
python tools/lint_agent_rules.py --diff
```

---

## 🎮 Phase 2: 5-Minute In-Game Smoke Test (Release Candidate Protocol)

Perform these 12 critical manual checks in a test world before publishing a new release:

### 1. Canvas Rendering & Viewport Navigation
- [ ] **Board Open & Fit**: Press `B` (or configured hotkey) to open board screen. Press `Home` or `F`; camera smoothly centers and bounds all placed nodes.
- [ ] **Pan & Zoom Smoothness**: Right-click drag pans smoothly. Mouse wheel zooms smoothly between $25\% \sim 200\%$ without font artifacts or visual bleeding.
- [ ] **Scissor & Visual Bleeding**: Open subpage modules or dialogs; confirm widgets and wires do not render outside their clipping boundaries.

### 2. Interactive Wiring & Core FSM
- [ ] **Spline Wire Connection**: Click green output socket $\rightarrow$ blue input socket. Spline wire renders cleanly with throughput saturation colors.
- [ ] **Shift + Auto-Ratio**: Hold `Shift` while connecting two machines; confirm target consumer machine count automatically scales 1:1 to match supply.
- [ ] **Wire Double-Click Junction**: Double-click an existing wire; confirm zero-cost [🔀 Junction] inserts cleanly and splits flow.
- [ ] **Wire Cutting**: Right-click on a wire or socket to sever the connection immediately.
- [ ] **Drag-to-Search**: Drag from a port into empty canvas; confirm 4-button quick action marker appears and recipe search opens pre-filtered.

### 3. Dialogs, Modals, & Hotkey Isolation
- [ ] **LIFO Modal Dismissal**: Open machine config $\rightarrow$ open recipe switcher. Press `ESC` or click backdrop; confirm only the topmost modal dialog closes first.
- [ ] **Input Leak Protection**: While typing in any inline text field (`EditBox`, machine count, rename), confirm global hotkeys (`E`, `Space`, `W/A/S/D`) do not close the GUI or leak to background canvas.

### 4. History Stack & Persistence
- [ ] **Undo / Redo Fidelity**: Perform node moves, wire cuts, and recipe switches; confirm `Ctrl + Z` and `Ctrl + Y` restore exact positions and wire states without orphan nodes.
- [ ] **Save & Reload Integrity**: Place machines and wires $\rightarrow$ save world $\rightarrow$ exit to main menu $\rightarrow$ reload world; confirm board layout and calculations restore with 100% fidelity.
- [ ] **Blueprint Clipboard**: Click `Share` (Base64 copied to clipboard) $\rightarrow$ click `Import` in another page; confirm factory pastes with identical parameters.

### 5. Dedicated Server & Multiplayer Safety
- [ ] **Dedicated Server Boot**: Start a dedicated server environment with the mod installed; confirm clean startup with **zero `NoClassDefFoundError` or client class references**.
- [ ] **Multiplayer Team Sync**: Connect 2 clients; confirm team workspace edits, node locks, and commit logs broadcast smoothly without desyncs.
