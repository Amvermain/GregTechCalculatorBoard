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
