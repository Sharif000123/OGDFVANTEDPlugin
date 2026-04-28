# OGDF <-> VANTED Addon (Overview)

This addon connects **VANTED (Java)** with **OGDF (C++)** using a subprocess bridge.

Design goal:
- VANTED keeps graph editing and visualization.
- OGDF performs computations on graphs (selectable layouts, metrics, and future graph generation).
- Communication is GraphML over stdin/stdout (no JNI/SWIG binding required for this addon).

---

## 1) Project Structure

Addon root:
- `<your-workspace>/OGDF_Vanted_Addon`

Important files:
- `src/OgdfIntegration.xml`
  - Plugin descriptor VANTED parses from the addon JAR.
- `src/ogdf/integration/OgdfTestPlugin.java`
  - Plugin entry class, registers algorithms.
- `src/ogdf/integration/OgdfLayoutAlgorithm.java`
  - Java bridge logic for launching OGDF process and applying coordinates.
- `src/ogdf_layout_fixed.cpp`
  - C++ executable source used by the addon (`ogdf_layout_fixed.exe` on Windows, `ogdf_layout_fixed` on Linux/macOS).
- `bin/`
  - Compiled Java classes + XML copied for packaging.
- `dist/OgdfIntegration.jar`
  - Built addon package.
- `ogdf_layout_fixed.exe`
  - Built native executable.

Runtime addon install folder:
- Windows: `%APPDATA%\VANTED\addons`
- Linux/macOS: VANTED addon directory for your installation

---

## 2) How Loading Works in VANTED

On startup, VANTED scans addon JARs in:
- the user addon folder (for example `%APPDATA%\VANTED\addons` on Windows)

For each addon:
1. It opens the JAR.
2. It expects an internal XML named exactly like the JAR base name.
   - Example: `OgdfIntegration.jar` must contain `OgdfIntegration.xml`.
3. It parses XML with Graffiti/VANTED plugin DTD rules.

The descriptor in this project includes the required DOCTYPE and plugin metadata.

---

## 3) Runtime Flow (Java -> C++ -> Java)

When the user runs **Apply OGDF Layout**:
1. Java reads current VANTED graph.
2. Java opens a small parameter dialog for layout algorithm + common options.
3. Java maps VANTED nodes to contiguous integer IDs (`nodeid`) and edges to `edgeid`.
4. Java serializes graph + layout settings as GraphML.
5. Java launches the OGDF executable and sends GraphML to stdin.
6. OGDF reads GraphML, dispatches to the selected layout, and writes GraphML to stdout.
7. Java parses returned GraphML and applies updates by `nodeid` (nodes) and edge index/`edgeid` (bends).
8. Java updates VANTED graph attributes in one transaction.

Error handling in the bridge:
- Captures process exit code.
- Captures stderr and stdout on failure.
- Reports meaningful diagnostics in VANTED console.

---

## 4) Current GraphML Protocol

Input sent from Java to OGDF (`stdin`):
- GraphML document with one `<graph>` containing nodes/edges.
- Graph-level layout keys include: `ogdf.layout`, `ogdf.iterations`, `ogdf.secondaryIterations`, `ogdf.pageRatio`, `ogdf.transpose`, `ogdf.includeMetrics`.
- Node keys include: `nodeid`, `x`, `y`, `width`, `height`, optional `label`.
- Edge keys include: `edgeid`, optional `edgelabel`, optional `bends` (space-separated x/y pairs).

Output returned from OGDF (`stdout`):
- GraphML document written by OGDF `GraphIO::writeGraphML(...)`.
- Java matches nodes using `nodeid` and applies returned values (`x`, `y`, optional size/label).
- Java applies edge bends from GraphML `bends` data and updates edge geometry.

Metrics returned from OGDF (`stderr`):
- A machine-readable line is emitted, e.g. `OGDF_METRIC crossings=... bends=... nodes=... edges=...`.
- Java stores these values on the graph under `ogdf/*` attributes and prints them to console.

Why `nodeid` matters:
- OGDF may rewrite XML node `id` values to internal indices on write.
- `nodeid` keeps a stable bridge identifier back to VANTED node order.

Supported selectable layouts:
- `SugiyamaLayout`
- `FMMMLayout`
- `StressMinimization`
- `SpringEmbedderFRExact`
- `SpringEmbedderKK`
- `DavidsonHarelLayout`
- `GEMLayout`
- `CircularLayout`
- `BalloonLayout`
- `LinearLayout`
- `TreeLayout`
- `RadialTreeLayout`
- `PlanarizationLayout`
- `PivotMDS`

The generic dialog fields are mapped per algorithm. For example, main iterations become `runs` for Sugiyama, `fixedIterations` for FMMM, `setIterations` for stress minimization, and `iterations` for the FR spring embedder. Unsupported fields are ignored by the chosen layout.

---

## 5) Runtime Resolution Strategy (User-Friendly)

`OgdfLayoutAlgorithm` resolves runtime paths in this order:
1. JVM property for executable: `-Dogdf.layout.exe=<full-path>`
2. Environment variable for executable: `OGDF_LAYOUT_EXE`
3. Saved user preference (stored after first successful selection)
4. Auto-detected sibling executable next to addon JAR:
   - Windows: `ogdf_layout_fixed.exe`
   - Linux/macOS: `ogdf_layout_fixed`
5. OGDF directory from `-Dogdf.dir=<folder>`, `OGDF_DIR`, or saved preference
6. If still missing and UI is available: one-time directory chooser prompts for the OGDF folder

When an OGDF directory is selected, the addon searches common CMake output folders such as:
- `<OGDF>/build`
- `<OGDF>/build/Debug`
- `<OGDF>/build/Release`
- `<OGDF>/ogdf/build`

Recognized bridge executable names:
- `ogdf_layout_fixed`
- `metrics_layout`
- plus `.exe` variants on Windows

Older `ogdf_layout` executables are not auto-detected because they may use the previous text protocol. If you really
need a custom executable name, set it explicitly with `-Dogdf.layout.exe=<full-path>` or `OGDF_LAYOUT_EXE`.

OGDF runtime library directory is resolved from:
1. JVM property: `-Dogdf.runtime.dir=<folder>` (or `-Dogdf.lib.dir=<folder>`)
2. Environment variable: `OGDF_RUNTIME_DIR` (or `OGDF_LIB_DIR`)
3. Saved user preference
4. Executable parent directory

When starting the native bridge, the addon prepends the executable/runtime folders to:
- Windows: `PATH`
- Linux: `PATH` and `LD_LIBRARY_PATH`
- macOS: `PATH`, `DYLD_LIBRARY_PATH`, and `DYLD_FALLBACK_LIBRARY_PATH`

If executable starts but OGDF runtime libraries cannot be loaded, the addon prompts once for the runtime directory and stores it.

Result:
- No machine-specific hardcoded absolute paths are required.
- A user can install addon + compile OGDF and point once to executable/runtime if needed.

---

## 6) Build + Deploy (Current Workflow)

### Build Java classes
Compile sources into `bin` with `vanted-core.jar` on classpath.

### Build addon JAR
Package from `bin`:
- `OgdfIntegration.xml`
- `ogdf/integration/*.class`

Output:
- `dist/OgdfIntegration.jar`

### Build native executable
Compile either directly:
- `src/ogdf_layout_fixed.cpp`
Against OGDF headers/libs.

Or, if the OGDF checkout contains the VANTED bridge target, build it through OGDF CMake:
- `cmake --build <OGDF>/build --target ogdf_layout_fixed`

Output:
- Windows: `ogdf_layout_fixed.exe`
- Linux/macOS: `ogdf_layout_fixed`

### Deploy
Copy both files to:
- `%APPDATA%\VANTED\addons\OgdfIntegration.jar`
- Windows: `%APPDATA%\VANTED\addons\ogdf_layout_fixed.exe`
- Linux/macOS: place `ogdf_layout_fixed` next to the addon JAR in the VANTED addon folder

Restart VANTED.

---

## 7) Known Non-Addon Noise

A startup exception like this is from a built-in VANTED plugin, not this addon:
- `de.ipk_gatersleben.ag_nw.graffiti.plugins.gui.layout_control.biomodels.RestApiBiomodels ... NullPointerException`

It does not block OGDF addon operation unless VANTED itself stops.

---

## 8) How to Extend

To add more OGDF features (metrics, generation, transforms):
1. Create a new Java algorithm class in `src/ogdf/integration/`.
2. Register it in `OgdfTestPlugin`.
3. Reuse GraphML bridge or add a dedicated native executable.
4. Keep key names explicit and versioned if protocol grows.
5. Rebuild JAR and deploy executable + JAR to addon folder.

Good next candidate:
- Add `ogdf_metrics.exe` bridge and expose it as a VANTED analysis command.

---

## 9) Maintenance Notes

- Keep JAR name and XML name in sync.
- Keep plugin XML DTD header intact.
- Always close stdin in Java after writing request payload.
- On failures, inspect:
  - process exit code
  - captured stderr
  - captured stdout
- C++ bridge source has no hardcoded absolute runtime paths; runtime loading is controlled by process environment/path.

This addon intentionally favors robustness and simplicity over tight in-process coupling.
