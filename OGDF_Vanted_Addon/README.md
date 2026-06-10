# OGDF <-> VANTED Addon (Overview)

This addon connects **VANTED (Java)** with **OGDF (C++)** using a subprocess bridge.

Design goal:

- VANTED keeps graph editing and visualization.
- OGDF performs computations on graphs (selectable layouts, metrics, and graph generation).
- Communication is GraphML over stdin/stdout (no JNI/SWIG binding required for this addon).
- Easy setup process, without installation of other tools, libraries, or dependencies beyond compiling the provided C++ bridge with OGDF, and importing the provided .jar addon into VANTED.

---

## 1) Project Structure

Addon root:

- `<your-workspace>/OGDF_Vanted_Addon`

Important files:

- `src/OgdfIntegration.xml`
  - Plugin descriptor VANTED parses from the addon JAR.
- `src/ogdf/integration/OgdfTestPlugin.java`
  - Plugin entry class, registers the OGDF inspector tab.
- `src/ogdf/integration/OgdfLayoutAlgorithm.java`
  - Java bridge logic for launching OGDF process and applying coordinates.
- `src/VantedOGDFAddon.cpp`
  - C++ executable source used by the addon (`VantedOGDFAddon.exe` on Windows, `VantedOGDFAddon` on Linux/macOS).
- `bin/`
  - Compiled Java classes + XML copied for packaging.
- `dist/OgdfIntegration.jar`
  - Built addon package.
- `VantedOGDFAddon.exe`
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
7. Java parses returned GraphML and applies updates by `nodeid` (nodes) and edge index/`edgeid` (edge geometry, e.g. bends).
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
- For graphs above the recommended metric-size thresholds, VANTED displays the
  current node/edge counts and asks for confirmation before calculating all
  metrics because the operation may take a long time or crash.
- OGDF may rewrite XML node `id` values to internal indices on write -> `nodeid` keeps a stable bridge identifier back to VANTED node order.

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

The parameter dialog adapts to the selected algorithm and shows only supported fields. Its labels reflect the OGDF mapping: for example, `Runs` maps to Sugiyama's `runs`, `Fixed iterations` to FMMM's `fixedIterations`, `Iterations` to stress minimization's `setIterations`, and `Iterations` to the FR spring embedder's `iterations`. The bridge also ignores unexpected unsupported values defensively, for example if GraphML contains a parameter the chosen layout does not use, the C++ bridge simply ignores it instead of failing.

---

## 5) Runtime Resolution Strategy (User-Friendly)

`OgdfLayoutAlgorithm` resolves runtime paths in this order:

1. JVM property for executable: `-Dogdf.layout.exe=<full-path>`
2. Environment variable for executable: `OGDF_LAYOUT_EXE`
3. Saved user preference (stored after first successful selection)
4. Auto-detected sibling executable next to addon JAR:
   - Windows: `VantedOGDFAddon.exe`
   - Linux/macOS: `VantedOGDFAddon`
5. OGDF directory from `-Dogdf.dir=<folder>`, `OGDF_DIR`, or saved preference
6. If still missing and UI is available: one-time directory chooser prompts for the OGDF folder

When an OGDF directory is selected, the addon searches common CMake output folders such as:

- `<OGDF>/build`
- `<OGDF>/build/Debug`
- `<OGDF>/build/Release`
- `<OGDF>/ogdf/build`

Recognized bridge executable names:

- `VantedOGDFAddon`
- plus `.exe` variants on Windows

Older bridge executables are not auto-detected because they may use the previous text protocol. If you really
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
- No machine-specific hardcoded absolute paths are required.
- A user can install addon + compile OGDF and point once to executable/runtime if needed.

---

## 6) New User Setup

Required files:

- `OgdfIntegration.jar` (already compiled; do not rebuild it)
- `VantedOGDFAddon.cpp` (must be compiled with OGDF)

Place `VantedOGDFAddon.cpp` in the folder structure shown below (the `ogdf_server` folder has to be created):

```
OGDF/
|-- CMakeLists.txt
`-- src/
    `-- ogdf_server/
        `-- VantedOGDFAddon.cpp
```


Insert this to the end of OGDF CMake file (`CMakeLists.txt`):

```cmake
add_executable(VantedOGDFAddon
    "${PROJECT_SOURCE_DIR}/src/ogdf_server/VantedOGDFAddon.cpp"
)
target_link_libraries(VantedOGDFAddon PRIVATE OGDF)
```

Build on Windows:

```powershell
cmake -S . -B build
cmake --build build --target VantedOGDFAddon --config Release
```

Build on Linux/macOS:

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --target VantedOGDFAddon -j
```

The result is `VantedOGDFAddon.exe` on Windows or `VantedOGDFAddon` on
Linux/macOS, normally inside `OGDF/build` or a configuration subfolder.

Install `OgdfIntegration.jar` with VANTED's addon manager. On first use, select
the main OGDF directory; the addon searches its CMake build folders
automatically. Alternatively, place the .jar file in
VANTED's addon folder.

No existing OGDF source files must be changed besides adding the CMake target above.

---

## 7) Known Non-Addon Noise

A startup exception like this is from a built-in VANTED plugin, not this addon:

- `de.ipk_gatersleben.ag_nw.graffiti.plugins.gui.layout_control.biomodels.RestApiBiomodels ... NullPointerException`

It does not block OGDF addon operation unless VANTED itself stops.

---

## 8) How to Extend the Addon

Most extensions should continue using the existing Java-to-C++ GraphML bridge and the single
`VantedOGDFAddon` executable.

### Adding a layout, generator, or metric

1. Extend the matching Java class in `src/ogdf/integration/`:
   - layouts: `OgdfLayoutAlgorithm.java`
   - generators: `OgdfGraphGeneratorAlgorithm.java`
   - metrics: `OgdfMetricsAlgorithm.java` and the shared metric display code in `OgdfLayoutAlgorithm.java`
2. Add any required GraphML request keys and user-input fields. Show only parameters supported by that feature and provide sensible defaults.
3. Update `VantedOGDFAddon.cpp` to read the new request values and call the corresponding OGDF function.
4. Return graph geometry through GraphML. Diagnostics, warnings, and metric values continue to use stderr.
5. Do not rename existing GraphML keys. Add new keys for new data, and introduce a protocol-version key only if a future change is not backward compatible.

### Adding a new top-level OGDF action

1. Create an `Algorithm` class under `src/ogdf/integration/`.
2. Add a button for it in `OgdfControlTab.java`. `OgdfTestPlugin` already registers this tab with VANTED.
3. Reuse `VantedOGDFAddon` and the existing runtime-resolution helpers where possible. A second native executable should only be introduced when the feature cannot reasonably share the current GraphML protocol.

After Java changes, rebuild `OgdfIntegration.jar`. After C++ changes, rebuild the
`VantedOGDFAddon` CMake target. Install the updated JAR in VANTED and ensure the rebuilt native
executable is in an auto-detected OGDF build folder or next to the addon JAR.

### Inspecting the JAR

A .jar uses the .zip archive format. Developers can open it directly with a ZIP tool, or temporarily
renaming `OgdfIntegration.jar` to `OgdfIntegration.zip` and extracting it. This exposes the plugin XML,
manifest, and compiled `.class` files. It does not recover the original editable `.java` source;
use the project's `src/ogdf/integration/` directory for development. If only the JAR is available,
a Java decompiler can produce approximate source code, but comments and some original structure
will be lost.

---

## 9) Maintenance Notes

- Keep .jar name and XML name in sync.
- Keep plugin XML DTD (Document Type Definition) header intact.
- Always close stdin in Java after writing request payload.
- On failures, first check the terminal or console from which VANTED was started. The addon reports
  warnings and native-process errors there, including:
  - process exit code
  - captured stderr
  - captured stdout
- C++ bridge source has no hardcoded absolute runtime paths, and the runtime loading is controlled by process environment/path.
