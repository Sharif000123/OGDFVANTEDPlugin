# OGDF-VANTED Addon Developer Package

This folder contains only the editable sources and minimal build files needed
to continue developing the addon. It intentionally contains no compiled
classes, JARs, executables, IDE metadata, test files, or duplicate source files.

## Contents

```text
OGDF_Addon_Developer/
|-- README.md
|-- OGDF_FUNCTIONS_INVENTORY.md
|-- native/
|   |-- CMakeLists.txt
|   `-- VantedOGDFAddon.cpp
`-- java/
    |-- build.ps1
    |-- build.sh
    `-- src/
        |-- OgdfIntegration.xml
        `-- ogdf/integration/
            |-- OgdfControlTab.java
            |-- OgdfGraphGeneratorAlgorithm.java
            |-- OgdfLayoutAlgorithm.java
            |-- OgdfMetricsAlgorithm.java
            `-- OgdfTestPlugin.java
```

## Requirements

- JDK 17 or newer, with `javac` and `jar` available
- VANTED's `vanted-core.jar`
- An OGDF source checkout and its normal CMake build dependencies

The OGDF library itself and VANTED dependencies are not copied into this folder.

## Build The Java Addon

Windows PowerShell:

```powershell
cd java
.\build.ps1 -VantedCoreJar "D:\path\to\vanted-core\vanted-core.jar"
```

Linux or macOS:

```bash
cd java
chmod +x build.sh
./build.sh /path/to/vanted-core/vanted-core.jar
```

Both scripts create:

```text
java/out/OgdfIntegration.jar
```

The JAR must contain `OgdfIntegration.xml` at its root. Keep the JAR base name
and XML base name identical.

## Build The Native OGDF Bridge

Copy the `native` folder into the OGDF checkout as:

```text
<OGDF>/src/ogdf_server/
|-- CMakeLists.txt
`-- VantedOGDFAddon.cpp
```

Add this line near the end of OGDF's root `CMakeLists.txt`:

```cmake
add_subdirectory(src/ogdf_server)
```

Configure and build:

```bash
cmake -S . -B build
cmake --build build --target VantedOGDFAddon --config Release
```

On single-configuration Linux/macOS generators, a release configuration can be
selected during configuration:

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --target VantedOGDFAddon -j
```

The result is `VantedOGDFAddon.exe` on Windows and `VantedOGDFAddon` on
Linux/macOS.

## Files Needed By A User

There are two possible distribution methods.

### User Receives A Prebuilt Native Bridge

Send:

- `OgdfIntegration.jar`
- `VantedOGDFAddon.exe` on Windows, or `VantedOGDFAddon` on Linux/macOS
- any shared OGDF runtime libraries required by that executable

The user does not need the C++ source or CMake code in this case.

### User Compiles The Native Bridge

This is the appropriate method when no compatible executable is available,
for example when building on another operating system. Send:

- `OgdfIntegration.jar`
- `native/VantedOGDFAddon.cpp`
- `native/CMakeLists.txt`
- the build instructions from this README

The user copies the complete `native` folder into the OGDF checkout as
`<OGDF>/src/ogdf_server/`, adds
`add_subdirectory(src/ogdf_server)` to OGDF's root `CMakeLists.txt`, and builds
the `VantedOGDFAddon` target.

`VantedOGDFAddon.cpp` is the C++ source file. The compiled result is
`VantedOGDFAddon.exe` on Windows or `VantedOGDFAddon` on Linux/macOS.

## Install And Run

1. Install `OgdfIntegration.jar` through VANTED's addon manager.
2. Start an OGDF action from the OGDF tab.
3. When prompted, select the main OGDF directory containing the CMake build.
4. Check the VANTED terminal for native-process errors and warnings.

The Java addon exchanges GraphML with the native bridge through stdin/stdout.
Diagnostics and machine-readable metrics are returned through stderr.

## Development Workflow

- Java UI, GraphML serialization, runtime discovery, and response handling are
  under `java/src/ogdf/integration/`.
- Native layouts, generators, metrics, and GraphML processing are implemented
  in `native/VantedOGDFAddon.cpp`.
- Register new top-level actions in `OgdfControlTab.java`.
- Keep `OgdfTestPlugin.java` as the plugin entry class referenced by
  `OgdfIntegration.xml`. Despite its historical name, it is required runtime
  code, not a test file.
- Update `OGDF_FUNCTIONS_INVENTORY.md` whenever exposed OGDF functionality
  changes.
- Rebuild the JAR after Java changes and the native bridge after C++ changes.

For users who compile the native bridge themselves, distribute the JAR,
`native/VantedOGDFAddon.cpp`, `native/CMakeLists.txt`, and these instructions.
For users receiving a prebuilt bridge, distribute the JAR, the platform-specific
native executable, and any required OGDF runtime libraries.
