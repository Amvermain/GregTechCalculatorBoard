# Changelog

<p align="center">
  <b>English</b> | <a href="CHANGELOG_KR.md">한국어</a>
</p>

> **Archived Versions**:
> - [v2.2.x Changelog](docs/changelogs/CHANGELOG_v2.2.md)
> - [v2.1.x Changelog](docs/changelogs/CHANGELOG_v2.1.md)
> - [v2.0.x Changelog](docs/changelogs/CHANGELOG_v2.0.md)
> - [v1.0.x Changelog](docs/changelogs/CHANGELOG_v1.0.md)

## [Unreleased]

### Added

### Improved

### Fixed

## [2.4.0-beta.1] - 2026-09-20

### Added
- Added a Batch Run Calculator dialog, allowing players to calculate the total processing time, required raw materials, projected output yields, and energy consumption based on a finite input batch or a target production goal. Accessible via the Optimize toolbar dropdown or by clicking any resource in the Process Summary panel.
- Added a Recipe Override tab in the machine settings dialog, allowing players to manually customize base processing time, power consumption/generation, and input/output ingredient amounts for unsupported generic machines or custom processes, with one-click restoration to default recipe values.
- Added a Local Web Dashboard, allowing players to view and monitor the active calculator board in real time on a secondary monitor or web browser while building in-world. Disabled by default to save system resources, it can be enabled via the in-game Settings dialog or client config, and accessed via the Share / I/O toolbar menu or the /gtcalcboard web command.

### Improved
- Improved the Local Web Dashboard icon rendering and caching pipeline with background prewarming, browser-level asset caching, and robust texture loading to eliminate in-game stutter when viewing large factory blueprints.
- Added a Page Browser to the Local Web Dashboard, allowing players to view all pages, search by name or folder, switch blueprints freely, and toggle in-game live page tracking.
- Improved Local Web Dashboard visual rendering with dedicated item/fluid slot plates, strict vertical icon alignment, two-column port layout to prevent text overlapping, dynamic card height scaling to cleanly enclose large recipe port lists, and authentic item/fluid/machine icon rendering while stripping raw color formatting codes.
- Aligned Local Web Dashboard node card dimensions, row height, and port wire connections with in-game layout specifications, ensuring consistent spacing between adjacent machines and preventing junction pins from being overlapped.
- Added comprehensive measurement unit formatting (mB, B, kB, MB, and compact EU/t) to the Local Web Dashboard, allowing players to read fluid and power rates clearly with an instant unit toggle (Auto / B / mB) in the top toolbar.

### Fixed
- Fixed an issue where steam multiblock machines such as the Steam Kiln and Steam Ore Factory incorrectly triggered missing energy hatch warnings and displayed electric/maintenance hatches in machine configuration dialogs, correctly restricting compatible addons to steam hatches.
- Fixed an issue where recipes added from viewers or switched on existing nodes could lose their input and output ports.
- Fixed an issue where recipes for low-tier machines like macerators omitted input ingredient ports when added to the board.
- Fixed an issue where parallel hatches on unpowered multiblock machines were capped to low voltage limits before energy hatches were installed, accurately preserving the hatch's rated parallel multiplier (including Star Technology Theta 2's 8x buff).

## [2.3.0] - 2026-09-16

### Added
- Added an auto-ratio button to expanded group frames, allowing players to calculate and balance upstream and downstream machine counts against the entire group without needing to collapse it into a module.
- Added a full-flow PNG export feature under the Share / I/O menu, allowing players to copy the entire active canvas page to the system clipboard as a clear, high-resolution image regardless of the current viewport or zoom level (with automatic fallback to the screenshots folder if clipboard access is unavailable). (Contributed by @SirEdvin in #9, thanks!)
- Added a comprehensive 3-track interactive tutorial system, featuring a basic starter tutorial, 4 specialized academy chapters for ratio solving, wiring, module subpages, and workspace management, along with non-intrusive contextual tips for in-game factory design.
- Added TerraFirmaGreg (TFG) Large Boiler support, allowing players to calculate steam generation and fuel consumption for Large Bronze and Large Steel Boilers with 9 selectable booster fluids, standard or purified water quality, dynamic water penalty scaling, and dual-fuel Super Boiler mode.

### Improved
- Improved the interactive tutorial flow so completing an action pauses to highlight the results and changes on the canvas with clear feedback, allowing players to review the outcome and proceed at their own pace using the Next Step button or Space/Enter keys, while preserving already placed machines and connections across steps.
- Streamlined the toolbar help dropdown menu by restructuring it cleanly around the basic starter tutorial and academy chapters.
- Improved Chapter 1 of the interactive academy by adding an explicit Alt+R auto-ratio step after anchor setup, and replacing placeholder nodes with realistic multi-step processing lines: a copper wire and cable line for harmonized integer scaling, and a 3-node leaching circuit with external acid makeup for damped recirculation loop scaling.
- Improved recirculating loop steady-state balancing to be automatically optimized alongside standard [Alt+R] and [Alt+Shift+R] auto-ratio calculations, and added a clickable [🔄 Steady State] badge on loop machine cards for one-click right-sizing.
- Hardened multiplayer server memory protection against abnormal network upload streams when committing large team workspace pages.
- Optimized node and blueprint copying to eliminate screen stutter when duplicating or pasting large factory layouts and complex process groups.

### Fixed
- Fixed an issue where equipping parallel control hatches on multiblock machines could become stuck at 1x parallel after opening the machine configuration dialog.
- Fixed an issue where junction external supply, infinite supply, or raw inflow was not recognized by downstream machine inputs when viewing flow rates in 1x recipe batch mode.
- Fixed a game crash occurring when clicking empty canvas space while editing values on compound or module nodes.
- Prevented keyboard shortcuts (such as Delete, Backspace, or Ctrl+Z) from accidentally affecting background canvas nodes while a dialog or popup window is open.
- Fixed an issue where switching page tabs while dragging a connection wire could leave the wire hanging or connect across different pages.
- Fixed an issue in Academy Chapter 3 where pressing Escape inside a module subpage would quit the tutorial instead of returning to the parent canvas.
- Improved page management to automatically clean up child subpages whenever their parent page is deleted, ensuring orphaned subpages are never left behind on the board.
- Hardened canvas UI interactions and flow calculations against unexpected crashes when switching pages, navigating complex graphs, or loading world saves with corrupted values.
