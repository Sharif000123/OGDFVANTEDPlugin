# Implemented OGDF Functions for the VANTED Bridge

This file lists only OGDF functionality that is reachable through the current
VANTED addon and `VantedOGDFAddon.cpp`. It is an implementation inventory, not
a list of possible future OGDF features.

The bridge supports three GraphML request modes:

- `layout`: imports a VANTED graph, applies one selected layout, and returns GraphML.
- `generate`: creates and initially lays out a new graph.
- `metrics`: imports the current VANTED graph and calculates metrics without changing its layout.

Graph import and export use:

- `GraphIO::readGraphML(GraphAttributes&, Graph&, std::istream&)`
- `GraphIO::writeGraphML(const GraphAttributes&, std::ostream&)`

The bridge initializes `GraphAttributes` with `nodeGraphics`, `edgeGraphics`,
`nodeLabel`, `edgeLabel`, and `nodeId`. This preserves the node coordinates,
node dimensions, edge bends, labels, node IDs, and graph directedness used by
the implemented layouts, generators, and metrics.

## Implemented Layouts

### Sugiyama (Layered)

- OGDF class: `SugiyamaLayout`
- Call: `call(GraphAttributes&)`
- Exposed settings:
  - `runs(int)`
  - `fails(int)`
  - `transpose(bool)`
  - `pageRatio(double)`
  - `setRanking(...)`
  - `setCrossMin(...)`
  - `setLayout(...)`
- Ranking choices:
  - `LongestPathRanking`
  - `OptimalRanking`
  - `CoffmanGrahamRanking`
- Crossing-minimization choices:
  - `BarycenterHeuristic`
  - `MedianHeuristic`
  - `SiftingHeuristic`
  - `GreedySwitchHeuristic`
  - `GreedyInsertHeuristic`
- Coordinate-assignment choices:
  - `FastHierarchyLayout`
  - `FastSimpleHierarchyLayout`
  - `OptimalHierarchyLayout`

### FMMM Force-Directed

- OGDF class: `FMMMLayout`
- Call: `call(GraphAttributes&)`
- Exposed settings:
  - `stopCriterion(...)`
    - `FixedIterations`
    - `Threshold`
  - `fixedIterations(int)`
  - `fineTuningIterations(int)`
  - `threshold(double)`

### Stress Minimization

- OGDF class: `StressMinimization`
- Call: `call(GraphAttributes&)`
- Exposed settings:
  - `setIterations(int)`
  - `convergenceCriterion(...)`
    - `None`
    - `PositionDifference`
    - `Stress`
- The current addon uses uniform edge costs.

### Spring FR Exact

- OGDF class: `SpringEmbedderFRExact`
- Call: `call(GraphAttributes&)`
- Exposed setting:
  - `iterations(int)`

### Spring Kamada-Kawai

- OGDF class: `SpringEmbedderKK`
- Call: `call(GraphAttributes&)`
- Exposed settings:
  - `computeMaxIterations(false)` when a global limit is supplied
  - `setMaxGlobalIterations(int)`
  - `setMaxLocalIterations(int)`

### Davidson-Harel

- OGDF class: `DavidsonHarelLayout`
- Call: `call(GraphAttributes&)`
- Exposed settings:
  - `setNumberOfIterations(int)`
  - `setIterationNumberAsFactor(false)` when an iteration count is supplied

### GEM

- OGDF class: `GEMLayout`
- Call: `call(GraphAttributes&)`
- Uses OGDF defaults.

### Circular

- OGDF class: `CircularLayout`
- Call: `call(GraphAttributes&)`
- Exposed setting:
  - `pageRatio(double)`

### Balloon

- OGDF class: `BalloonLayout`
- Call: `call(GraphAttributes&)`
- Requires a connected graph. The layout is not applied when this requirement is not met.

### Linear

- OGDF class: `LinearLayout`
- Call: `call(GraphAttributes&)`
- Uses OGDF defaults.

### Tree

- OGDF class: `TreeLayout`
- Call: `call(GraphAttributes&)`
- Requires a forest. The layout is not applied to cyclic graphs.
- Fixed bridge settings:
  - `siblingDistance(60.0)`
  - `subtreeDistance(80.0)`
  - `levelDistance(90.0)`
  - `treeDistance(120.0)`

### Radial Tree

- OGDF class: `RadialTreeLayout`
- Call: `call(GraphAttributes&)`
- Requires one connected tree. The layout is not applied when this requirement is not met.
- Fixed bridge setting:
  - `levelDistance(90.0)`

### Orthogonal (Planarization)

- Main OGDF class: `PlanarizationLayout`
- Call: `call(GraphAttributes&)`
- Exposed settings:
  - crossing permutations through `SubgraphPlanarizer::permutations(int)`
  - bend bound through `OrthoLayout::bendBound(int)`
  - `pageRatio(double)`
- Module wiring:
  - `SubgraphPlanarizer::setSubgraph(...)`
  - `SubgraphPlanarizer::setInserter(...)`
  - `PlanarizationLayout::setCrossMin(...)`
  - `PlanarizationLayout::setEmbedder(...)`
  - `PlanarizationLayout::setPlanarLayouter(...)`
- Planar-subgraph choices:
  - `PlanarSubgraphFast<int>`
    - repetitions through `runs(int)`
  - `PlanarSubgraphBoyerMyrvold`
    - constructed with the selected repetition count and probability `0.5`
- Edge-insertion choices:
  - `VariableEmbeddingInserter`
  - `VariableEmbeddingInserterDyn`
  - `FixedEmbeddingInserter`
- Embedder choices:
  - `SimpleEmbedder`
  - `EmbedderMinDepthMaxFace`
  - `EmbedderMaxFace`
- Orthogonal model choices through `OrthoLayout`:
  - progressive
  - traditional
  - progressive with scaling
  - traditional with scaling
- The four models are mapped through:
  - `progressive(bool)`
  - `scaling(bool)`

### Pivot MDS

- OGDF class: `PivotMDS`
- Call: `call(GraphAttributes&)`
- Requires a connected graph. The layout is not applied when this requirement is not met.
- The current addon uses uniform edge costs.

## Implemented Graph Generators

The generator dialog exposes these eight graph types.

### Random Simple Graph G(n, p)

- Undirected: `randomSimpleGraphByProbability(Graph&, int, double)`
- Directed: `randomDigraph(Graph&, int, double)`
- Inputs:
  - number of nodes
  - edge probability
  - directed/undirected selection

### Random Simple Graph G(n, m)

- `randomSimpleGraph(Graph&, int, int)`
- Inputs:
  - number of nodes
  - number of edges

### Random Connected Graph

- `randomSimpleConnectedGraph(Graph&, int, int)`
- Inputs:
  - number of nodes
  - number of edges
- The bridge raises the edge count to at least `n - 1` when necessary.

### Random Tree

- `randomTree(Graph&, int)`
- Input:
  - number of nodes

### Regular Tree

- `regularTree(Graph&, int, int)`
- Inputs:
  - number of nodes
  - children per node

### Random Planar Connected Graph

- `randomPlanarConnectedGraph(Graph&, int, int)`
- Inputs:
  - number of nodes
  - number of edges

### Complete Graph

- `completeGraph(Graph&, int)`
- Input:
  - number of nodes

### Grid Graph

- `gridGraph(Graph&, int, int, false, false)`
- Inputs:
  - rows
  - columns

### Initial Generator Layout

Generated graphs receive an initial layout automatically:

- Grid graphs receive bridge-generated grid coordinates.
- Random and regular trees use the implemented `TreeLayout`.
- All other generated graphs use the implemented `FMMMLayout` with 200 iterations.
- Nodes receive default dimensions of 25 by 25.
- Optional node labels use consecutive numbers starting at 1.

## Implemented Metrics

Metrics can be calculated after a layout or through the standalone
**Calculate Graph Metrics** action. The standalone action measures the graph's
current VANTED coordinates without applying a layout.

### Direct OGDF LayoutStatistics Calls

- `LayoutStatistics::numberOfBends(...)`
  - reported as `bends`
- `LayoutStatistics::numberOfNodeOverlaps(...)`
  - reported as `nodeOverlaps`
- `LayoutStatistics::numberOfCrossings(...)`
  - reported as `crossings`
- `LayoutStatistics::percentageCrossingVsMaxCrossings(...)`
  - reported as `crossingPercentage`
- `LayoutStatistics::numberOfNodeCrossings(...)`
  - reported as `nodeCrossings`
- `LayoutStatistics::edgeLengths(...)`
  - aggregated into `minEdgeLength`, `avgEdgeLength`, and `maxEdgeLength`
- `LayoutStatistics::edgeLengthDeviation(...)`
  - reported as `edgeLengthDeviation`
- `LayoutStatistics::nodeResolution(...)`
  - reported as `nodeResolution`
- `LayoutStatistics::angularResolution(...)`
  - reported as `angularResolution`
- `LayoutStatistics::nodeUniformity(...)`
  - reported as `nodeUniformity`
- `LayoutStatistics::edgeOrthogonality(...)`
  - reported as `edgeOrthogonality`
- `LayoutStatistics::closestPairOfPoints(...)`
  - reported as `closestPairDistance`
- `LayoutStatistics::horizontalVerticalBalance(..., false)`
  - reported as `horizontalBalance`
- `LayoutStatistics::horizontalVerticalBalance(..., true)`
  - reported as `verticalBalance`
- `LayoutStatistics::nodeOrthogonality(...)`
  - reported as `nodeOrthogonality`
- `LayoutStatistics::neighbourhoodPreservation(...)`
  - averaged and reported as `neighbourhoodPreservation`
- `LayoutStatistics::gabrielRatio(...)`
  - averaged and reported as `gabrielRatio`
- `LayoutStatistics::averageFlow(...)`
  - reported as `averageFlow` for directed graphs
- `LayoutStatistics::upwardsFlow(...)`
  - reported as `upwardsFlow` for directed graphs
- `LayoutStatistics::aspectRatio(...)`
  - reported as `aspectRatio`

### Metrics Calculated by the Bridge

- `nodes`: `Graph::numberOfNodes()`
- `edges`: `Graph::numberOfEdges()`
- `density`: calculated from node count, edge count, and graph directedness
- `components`: `connectedComponents(...)`
- `isolatedNodes`: counted from node degrees
- `avgDegree`: calculated from node degrees
- `minDegree`: calculated from node degrees
- `maxDegree`: calculated from node degrees
- `graphArea`: width times height of `GraphAttributes::boundingBox()`
- `layoutRuntimeMs`: measured around the selected layout call and reported only
  when metrics are requested together with a layout

The bridge also aggregates the per-edge bend counts and per-node overlap counts
before reporting their scalar values.

### Metric Size Warnings

The bridge no longer skips metrics because of fixed graph-size limits. Before
starting an expensive calculation, the Java addon asks the user whether to
continue when any of these recommended thresholds are exceeded:

- crossings and crossing percentage: more than 2000 edges
- node crossings: more than 2000 nodes or more than 2000 edges
- neighbourhood preservation: more than 2000 nodes
- Gabriel ratio: more than 500 nodes or
  `nodes * max(edges, 1) > 1,000,000`

The confirmation dialog displays the current node count, edge count, node-edge
product, and every exceeded recommendation. It warns that the calculation may
take a very long time, consume substantial memory, make VANTED unresponsive,
or crash VANTED or the native OGDF process. If the user continues, all metrics
are calculated.

## Supporting OGDF Graph Operations

The bridge also directly uses these OGDF checks and helpers:

- `isConnected(...)`
- `isAcyclicUndirected(...)`
- `isTree(...)`
- `connectedComponents(...)`
- `GraphAttributes::boundingBox()`

These support layout compatibility checks and metric calculation; they are not
separate actions in the VANTED UI.

## Current Scope

The current bridge does not expose weighted layouts, cluster layouts, UML
layouts, upward layouts, graph products, graph transformations, or arbitrary
OGDF classes that are not listed above.
