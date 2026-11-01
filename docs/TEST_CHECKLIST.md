# GregTech Calculator Board Release QA & Smoke Test Checklist

> 🌐 **Language / 언어**: **English** | [한국어](ko_kr/TEST_CHECKLIST.md)

This checklist is the official, streamlined verification runbook for `GregTechCalculatorBoard` release builds.
All deterministic calculations, mathematical solvers, NBT serialization, and layer boundaries are already 100% automatically validated by our automated test suites. This document focuses exclusively on the **5-Minute In-Game & Dedicated Server Smoke Test** that requires human sensory verification.

---

## 🤖 Phase 1: Automated Pre-Flight Gates (Run Before Launch)

Run these three commands in terminal. All must exit with `0 errors`:

```bash
# 1. 70 Headless JUnit Domain, Solver, & ArchUnit Architecture Tests
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
- [ ] **Community & Support Dialog**: Clicking the Community button on the left activity bar or Guide menu opens the GitHub Issues & Discord popup, and clicking links safely prompts confirmation before opening the external web browser.
- [ ] **Turbine Dynamo & Laser Amperage Limits**: In a Large Turbine config dialog, verify normal turbines cycle dynamo amperage up to `16A` (never `64A`), while laser-capable turbines (such as Supreme/Nyinsane Plasma Turbines) cycle through `1A, 2A, 4A, 16A, 256A, 1024A, 4096A` and clamp back to `16A` when switched to a normal turbine.
- [ ] **Generator Multiblock Dynamo & Capacity Check**: Place a Star Technology Nuclear Fission Reactor node (`gtceu:nuclear_reactor`); confirm it displays `[⚡ GEN]` and positive EU/t generation without heating coil slots, offers Dynamo, Substation Dynamo (`64A`), and Laser Source Hatches (`256A`–`4096A`), and runs without a tier warning as long as total dynamo hatch capacity covers the recipe output power (e.g., `16A UV` dynamo hatch on a `1A UEV` recipe).
- [ ] **Configurable Maintenance Hatch Pre-Overclock Duration**: Equip a `0.9x` Configurable Maintenance Hatch on a non-subtick Kube (`t_large_centrifuge` / `t_large_electrolyzer`) with a `60t` LV recipe overclocked to HV (`3t`) or an Industrial Barrel with a `5t` or `80t` recipe overclocked to `2t`; confirm the duration remains `3t` (`99t` with `33x` Batch Mode) on the Kube and `2t` (`3t` with Throughput Boosting) on the Industrial Barrel, matching in-game production speed.
- [ ] **Hellforge & Fornax's Infernal Rotary Engine Crucible Heat & Parallel Bonus**: Place a Star Technology Hellforge (`gtceu:hellforge`) or Fornax's Infernal Rotary Engine (`gtceu:fornaxs_infernal_rotary_engine`) node; confirm heating coil slots are hidden, crucible heat (e.g., `1800 MK`) and heating fluid selection (`900 MK`–`4050 MK`) are displayed in the inspector and config dialog, and selecting a hotter fluid applies a `2x` infernal parallel bonus for every `450 MK` above the recipe requirement.

### 4. History Stack & Persistence
- [ ] **Undo / Redo Fidelity**: Perform node moves, wire cuts, and recipe switches; confirm `Ctrl + Z` and `Ctrl + Y` restore exact positions and wire states without orphan nodes.
- [ ] **Save & Reload Integrity**: Place machines and wires $\rightarrow$ save world $\rightarrow$ exit to main menu $\rightarrow$ reload world; confirm board layout and calculations restore with 100% fidelity.
- [ ] **Folder Browser State & Search**: In folder browser, click the search box to filter pages and folders, verify hint text and right-click to clear, collapse/expand folders and delete a page; confirm browser stays open without closing, and reopening the board screen retains the collapsed/expanded folder states.
- [ ] **Shared Machine Pool Warning Badges**: Form a Shared Machine Pool with multiblock machines lacking energy hatches; confirm the pool header and sub-cards display `[⚠ LV]` red warning tints, `⚡ ⚠ No Power Hatch` badges, and energy hatch configuration hints in the tooltip.
- [ ] **Blueprint Clipboard**: Click `Share` (Base64 copied to clipboard) $\rightarrow$ click `Import` in another page; confirm factory pastes with identical parameters.

### 5. Dedicated Server & Multiplayer Safety
- [ ] **Dedicated Server Boot**: Start a dedicated server environment with the mod installed; confirm clean startup with **zero `NoClassDefFoundError` or client class references**.
- [ ] **Multiplayer Team Sync**: Connect 2 clients; confirm team workspace edits, node locks, and commit logs broadcast smoothly without desyncs, and renaming the FTB Teams party immediately updates the team workspace header name and persists across server restarts.
- [ ] **Page Settings Network Security & Isolation**: In a multiplayer team workspace, adjust page voltage tier or auto-hatch settings; confirm settings synchronize across team members in real time, persist on the server without leaking to other teams, and reject unauthorized edits with optimistic rollback.
- [ ] **Layered Ordered Chemistry Machine Selection, Default Traits, Hardware Sync, Sequential Cycle Rate & Auto-Ratio**: Add an Ordered Chemistry layered recipe (such as 3,4-Ethylenedioxythiophene in Star Technology); confirm both Advanced Synthesis Plant (ASP) and True Synthesis Array (TSA) appear in the machine selector with ASP selected by default, switching to or creating with TSA equips Throughput Boosting by default, clicking the `[▦ I/II]`, `[STD OC]`, and `[⚙]` buttons on the card triggers their respective actions without shifted hitboxes, changing hatches/machine/parallel on any step syncs to all steps while counting them as 1 machine, each step's per-second consumption/production rate uses the total sequential cycle duration across all steps rather than a single step's duration, and running Auto-Ratio scales all steps and their upstream input suppliers together.
