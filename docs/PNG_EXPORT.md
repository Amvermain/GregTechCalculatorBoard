# Copy flow as PNG

Open the board's **Share / I/O** menu and choose **Copy flow as PNG**. Paste into an application that accepts images.

The export includes the entire active page, not just the viewport or selected nodes. It preserves folded shared-machine groups as folded cards and does not expand module subpages. Nodes, frames, sticky notes and connecting curves are included with padding, using two image pixels per canvas unit. The output uses the board's existing colors and card layout, but forces full detail even when the current view is zoomed out. It omits surrounding menus, selection highlights, resize grips, transient drag wires and wire pulse animation.

Export never pans, zooms, resizes or deselects the live board. It renders into a separate framebuffer using fresh node widgets, including fresh port layouts for wire endpoints. PNG encoding and clipboard transfer happen off the render thread. The render thread still performs the actual drawing and GPU readback, so a large page can briefly pause rendering.

## Limits and fallback

- The image is limited to 16,777,216 pixels and the GPU's maximum texture dimensions. Larger pages produce a warning rather than silently reducing text readability. Split oversized flows into smaller pages; tiled export is not implemented.
- Image clipboard access requires a desktop-capable Java runtime and desktop clipboard service. Minecraft forces AWT into headless mode, so a small bundled, dependency-free Java worker runs with desktop AWT enabled. It uses the same Java installation as Minecraft; no external clipboard utility or network service is required.
- The worker retains clipboard ownership for desktops such as X11, where pasted image data is requested lazily. It exits when ownership changes or Minecraft exits. Repeated exports replace the previous worker.
- If desktop clipboard access fails (including a headless-only Java installation), the PNG is saved under `screenshots/gtcalcboard/` inside the Minecraft game directory, and the location is displayed and logged. A screenshot is not silently reported as copied when it was only saved.
- No Windows/macOS or native Wayland validation has been performed yet. The portable AWT implementation is intended to support desktop platforms; native Wayland availability depends on the runtime and desktop, with file fallback otherwise.

## Verification

- Full Gradle suite: 1,054 tests, no failures, errors or skipped tests.
- New focused tests cover negative/fractional bounds, padding, wire excursions, empty/invalid/oversized exports, PNG/image clipboard flavors, pixel round trips and exception-safe nested presentation overrides.
- Real Minecraft 1.20.1 / Forge 47.4.20 rendering was exercised under Xvfb with a desktop Java 21 runtime (compiled for Java 17), without optional integrations loaded. Test flows included negative coordinates, separated nodes with real Minecraft item icons, a connecting wire, a frame and a sticky note outside the frame. A second case covered a folded shared-machine card with deliberately stale stored height.
- An independent desktop JVM pasted the exported images from the system clipboard: **3116 × 1396 pixels**. Both images were visually inspected. The smoke fixture asserted unchanged serialized graph data, pan, zoom, selection, projection matrix, framebuffer binding and viewport.
- A Java 17 installation without desktop AWT libraries exercised the real PNG-file fallback. The saved image was inspected, not treated as successful clipboard delivery.

Build with a Java 17 Gradle JVM:

```sh
JAVA_HOME=/path/to/java17 ./gradlew build --no-daemon
```

The existing upstream build requires local mod JARs in `libs/` (or a configured instance): JEI, CoFH Core, Thermal Foundation/Expansion/Cultivation, Create, Create New Age and AE2, at the versions in `gradle.properties` / `build.gradle`. These dependencies are not committed. The clipboard worker is built by `clipboardJar` and embedded as `gtcalcboard-clipboard.jar` in the mod; no separate installation is needed.
