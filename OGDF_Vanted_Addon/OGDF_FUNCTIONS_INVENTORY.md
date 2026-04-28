# OGDF Functions Inventory for the VANTED Bridge

This inventory was made from the local OGDF checkout at `D:\Work\OGDF\ogdf`.
The goal is to decide which OGDF functions should be exposed through the VANTED addon.

## Recommended Bridge Groups

The bridge should probably support three top-level command groups:

- `layout`: reads a graph, applies an OGDF layout, returns GraphML with positions and bends.
- `generate`: creates an OGDF graph, returns GraphML so VANTED can display it.
- `metrics`: reads a graph/layout and returns layout quality metrics such as crossings and bends.

For user friendliness, each command should accept common parameters where possible:

- `algorithm`: name of the OGDF algorithm/generator/metric group.
- `iterations`: optional generic parameter mapped to the algorithm-specific OGDF setter.
- `timeoutMs`: optional process timeout from Java/VANTED.
- `seed`: optional for random generators.
- `directed`: optional graph direction flag where relevant.
- `includeMetrics`: optional flag to run metrics after a layout.

## Layouts

### Direct Layouts: Best First Targets

These are the easiest to expose because they work with `GraphAttributes` or inherit a `GraphAttributes` call path. They fit well with the current GraphML bridge.

#### Layered / Sugiyama

- `SugiyamaLayout`
  - Calls:
    - `call(GraphAttributes& GA)`
    - `call(ClusterGraphAttributes& CGA)`
    - `call(GraphAttributes& GA, NodeArray<int>& rank)`
    - `callUML(GraphAttributes& GA)`
  - Important parameters:
    - `runs(int)`: crossing minimization repetitions.
    - `fails(int)`: failed crossing-min attempts before stopping one repetition.
    - `transpose(bool)`: extra fine tuning step.
    - `arrangeCCs(bool)`: arrange connected components separately.
    - `minDistCC(double)`: connected-component distance.
    - `pageRatio(double)`: desired page ratio.
    - `setRanking(...)`: ranking module.
    - `setCrossMin(...)`: crossing minimization module.
    - `setLayout(...)`: coordinate assignment module.
    - `setPacker(...)`: component packing module.

Sugiyama submodules worth exposing later:

- Ranking:
  - `LongestPathRanking`
  - `OptimalRanking`
  - `CoffmanGrahamRanking`
- Cycle removal / acyclic subgraph:
  - `DfsAcyclicSubgraph`
  - `GreedyCycleRemoval`
- Crossing minimization:
  - `BarycenterHeuristic`
  - `MedianHeuristic`
  - `SiftingHeuristic`
  - `GreedyInsertHeuristic`
  - `GreedySwitchHeuristic`
  - `SplitHeuristic`
  - `GridSifting`
- Final hierarchy layout:
  - `FastHierarchyLayout`
  - `FastSimpleHierarchyLayout`
  - `OptimalHierarchyLayout`
  - `OptimalHierarchyClusterLayout`
- Component packing:
  - `TileToRowsCCPacker`
  - `SimpleCCPacker`

#### Energy-Based / Force-Directed

- `FMMMLayout`
  - `call(GraphAttributes& GA)`
  - `call(ClusterGraphAttributes& GA)`
  - `call(GraphAttributes& GA, const EdgeArray<double>& edgeLength)`
  - Important parameters:
    - `fixedIterations(int)`
    - `fineTuningIterations(int)`
    - `stopCriterion(...)`
    - `threshold(double)`
    - `stepsForRotatingComponents(int)`
    - `maxIterChange(...)`
    - `maxIterFactor(int)`
- `FastMultipoleEmbedder`
  - `call(GraphAttributes& GA)`
  - `call(GraphAttributes& GA, const EdgeArray<float>& edgeLength, const NodeArray<float>& nodeSize)`
  - Important parameter:
    - `setNumIterations(uint32_t)`
- `FastMultipoleMultilevelEmbedder`
  - `call(GraphAttributes& GA)`
- `DavidsonHarelLayout`
  - `call(GraphAttributes& GA)`
  - Important parameters:
    - `setNumberOfIterations(int)`
    - `setIterationNumberAsFactor(bool)`
- `DavidsonHarel`
  - `call(GraphAttributes& GA)`
  - Important parameter:
    - `setNumberOfIterations(int)`
- `GEMLayout`
  - `call(GraphAttributes& GA)`
- `SpringEmbedderFRExact`
  - `call(GraphAttributes& GA)`
  - Important parameters:
    - `iterations(int)`
    - `convTolerance(double)`
- `SpringEmbedderGridVariant`
  - Inherits `SpringEmbedderBase`
  - Important parameters:
    - `iterations(int)`
    - `iterationsImprove(int)`
- `SpringEmbedderKK`
  - `call(GraphAttributes& GA)`
  - `call(GraphAttributes& GA, const EdgeArray<double>& eLength)`
  - Important parameters:
    - `setMaxGlobalIterations(int)`
    - `setMaxLocalIterations(int)`
    - `setGlobalIterationFactor(int)`
    - `computeMaxIterations(bool)`
    - `setStopTolerance(double)`
- `StressMinimization`
  - `call(GraphAttributes& GA)`
  - Important parameter:
    - `setIterations(int)`
- `PivotMDS`
  - `call(GraphAttributes& GA)`
- `TutteLayout`
  - `call(GraphAttributes& GA)`
  - `call(GraphAttributes& GA, const List<node>& givenNodes)`
- `DTreeMultilevelEmbedder2D`
  - `call(GraphAttributes& GA)`
- `DTreeMultilevelEmbedder3D`
  - `call(GraphAttributes& GA)`
- `MultilevelLayout`
  - `call(GraphAttributes& GA)`
- `ModularMultilevelMixer`
  - `call(GraphAttributes& GA)`
  - `call(MultilevelGraph& MLG)`
- `ScalingLayout`
  - `call(GraphAttributes& GA)`
  - `call(MultilevelGraph& MLG)`
  - Important parameter:
    - `setExtraScalingSteps(unsigned int)`
- `NodeRespecterLayout`
  - `call(GraphAttributes& attr)`
  - Important parameter:
    - `setNumberOfIterations(int)`

#### Miscellaneous Layouts

- `BalloonLayout`
  - `call(GraphAttributes& GA)`
- `BertaultLayout`
  - `call(GraphAttributes& GA)`
  - Important parameter:
    - `iterno(int)`
- `CircularLayout`
  - `call(GraphAttributes& GA)`
- `LinearLayout`
  - `call(GraphAttributes& GA)`
- `ProcrustesSubLayout`
  - `call(GraphAttributes& GA)`

#### Tree Layouts

- `TreeLayout`
  - `call(GraphAttributes& GA)`
- `RadialTreeLayout`
  - `call(GraphAttributes& GA)`

#### Planar Layouts

These are usable, but some require planar graphs or internally work through `GridLayout`.

- `PlanarizationLayout`
  - `call(GraphAttributes& GA)`
  - `call(GraphAttributes& GA, Graph& G)`
- `PlanarizationGridLayout`
  - Inherits `GridLayoutModule`, so it has a `GraphAttributes` call path.
- `FPPLayout`
  - Inherits `PlanarGridLayoutModule`.
- `MixedModelLayout`
  - Inherits `GridLayoutPlanRepModule`.
- `PlanarDrawLayout`
  - Inherits `PlanarGridLayoutModule`.
- `PlanarStraightLayout`
  - Inherits `PlanarGridLayoutModule`.
- `SchnyderLayout`
  - Inherits `PlanarGridLayoutModule`.

Related planar layout submodules:

- `BiconnectedShellingOrder`
- `TriconnectedShellingOrder`
- `BitonicOrdering`
- `LeftistOrdering`
- `MixedModelCrossingsBeautifierModule`
- `MMCBBase`
- `MMCBDoubleGrid`
- `MMCBLocalStretch`

#### Upward Layouts

- `DominanceLayout`
  - `call(GraphAttributes& GA)`
- `VisibilityLayout`
  - `call(GraphAttributes& GA)`
- `UpwardPlanarizationLayout`
  - `call(GraphAttributes& GA)`
- `LayerBasedUPRLayout`
  - Layout module for `UpwardPlanRep`, indirectly useful for upward layouts.

Related upward submodules:

- `FUPSSimple`
- `MaximalFUPS`
- `SubgraphUpwardPlanarizer`
- `UpwardPlanarSubgraphSimple`
- `FixedEmbeddingUpwardEdgeInserter`

#### Geometric / Local Improvement Layouts

- `GeometricEdgeInsertion`
  - `call(GraphAttributes& GA)`
- `GeometricVertexInsertion`
  - `call(GraphAttributes& GA)`
- `VertexMovement`
  - `call(GraphAttributes& GA)`
- `RandomVertexPosition`
  - `call(GraphAttributes& GA, node v)`
- `CrossingMinimalPosition`
  - `call(GraphAttributes& GA, node v)`
- `CrossingMinimalPositionApx`
- `CrossingMinimalPositionApxWeighted`

#### Packing Layouts

- `ComponentSplitterLayout`
  - `call(GraphAttributes& GA)`
- `SimpleCCPacker`
  - `call(GraphAttributes& GA)`
- `TileToRowsCCPacker`
  - `call(Array<DPoint>& box, Array<DPoint>& offset, double pageRatio)`
  - `call(Array<IPoint>& box, Array<IPoint>& offset, double pageRatio)`

#### Orthogonal Layouts

These need planar representation objects, so they are a later bridge target.

- `OrthoLayout`
  - `call(PlanRep& PG, adjEntry adjExternal, Layout& drawing)`
- `OrthoShaper`
  - `call(PlanRep& PG, CombinatorialEmbedding& E, OrthoRep& OR, bool fourPlanar)`
- `EdgeRouter`
  - `call(PlanRep&, OrthoRep&, GridLayoutMapped&, ...)`
- Compaction submodules with step parameters:
  - `FlowCompaction`
    - `maxImprovementSteps(int)`
    - `scalingSteps(int)`
  - `LongestPathCompaction`
    - `maxImprovementSteps(int)`

#### Cluster Layouts

These require `ClusterGraph` / `ClusterGraphAttributes`, so they are a later target unless VANTED cluster metadata is mapped.

- `ClusterPlanarizationLayout`
  - `call(Graph& G, ClusterGraphAttributes& acGraph, ClusterGraph& cGraph, bool simpleCConnect)`
  - `call(Graph& G, ClusterGraphAttributes& acGraph, ClusterGraph& cGraph, EdgeArray<double>& edgeWeight, bool simpleCConnect)`
- `ClusterOrthoLayout`
  - `call(ClusterPlanRep& PG, adjEntry adjExternal, Layout& drawing)`
- `ClusterOrthoShaper`
  - `call(ClusterPlanRep& PG, CombinatorialEmbedding& E, OrthoRep& OR, int startBoundBendsPerEdge, bool fourPlanar)`
- `CPlanarEdgeInserter`
- `CPlanarSubClusteredGraph`
- `CconnectClusterPlanar`
- `ILPClusterPlanarity`
- `MaximumCPlanarSubgraph`

#### UML Layouts

These require OGDF UML-specific graph objects.

- `PlanarizationLayoutUML`
  - `call(GraphAttributes& GA)`
  - `call(UMLGraph& umlGraph)`
- `OrthoLayoutUML`
  - `call(PlanRepUML& PG, adjEntry adjExternal, Layout& drawing)`
- `UMLLayoutModule`
- `SubgraphPlanarizerUML`
- `FixedEmbeddingInserterUML`
- `VariableEmbeddingInserterUML`
- `VariableEmbeddingInserterDynUML`

## Graph Generators

These are in `ogdf/basic/graph_generators`.

### Deterministic Generators

- `customGraph(Graph& G, int n, List<pair<int,int>> edges)`
- `circulantGraph(Graph& G, int n, Array<int> jumps)`
- `regularLatticeGraph(Graph& G, int n, int k)`
- `regularTree(Graph& G, int n, int children)`
- `completeGraph(Graph& G, int n)`
- `completeKPartiteGraph(Graph& G, const Array<int>& signature)`
- `completeBipartiteGraph(Graph& G, int n, int m)`
- `wheelGraph(Graph& G, int n)`
- `cubeGraph(Graph& G, int n)`
- `globeGraph(Graph& G, int meridians, int latitudes)`
- `suspension(Graph& G, int s)`
- `gridGraph(Graph& G, int n, int m, bool loopN, bool loopM)`
- `petersenGraph(Graph& G, int n = 5, int m = 2)`
- `emptyGraph(Graph& G, int nodes)`

### Randomized Generators

- `randomRegularGraph(Graph& G, int n, int d)`
- `randomGraph(Graph& G, int n, int m)`
- `randomSimpleGraph(Graph& G, int n, int m)`
- `randomSimpleGraphByProbability(Graph& G, int n, double pEdge)`
- `randomSimpleConnectedGraph(Graph& G, int n, int m)`
- `randomBiconnectedGraph(Graph& G, int n, int m)`
- `randomPlanarConnectedGraph(Graph& G, int n, int m)`
- `randomPlanarBiconnectedGraph(Graph& G, int n, int m, bool multiEdges)`
- `randomPlanarBiconnectedDigraph(Graph& G, int n, int m, double p, bool multiEdges)`
- `randomUpwardPlanarBiconnectedDigraph(Graph& G, int n, int m)`
- `randomPlanarCNBGraph(Graph& G, int n, int m, int b)`
- `randomTriconnectedGraph(Graph& G, int n, double p1, double p2)`
- `randomPlanarTriconnectedGraph(Graph& G, int n, int m)`
- `randomPlanarTriconnectedGraph(Graph& G, int n, double p1, double p2)`
- `randomTree(Graph& G, int n)`
- `randomTree(Graph& G, int n, int maxDeg, int maxWidth)`
- `randomDigraph(Graph& G, int n, double p)`
- `randomSeriesParallelDAG(Graph& G, int edges, double p, double flt)`
- `randomGeometricCubeGraph(Graph& G, int nodes, double threshold, int dimension)`
- `randomWaxmanGraph(Graph& G, int nodes, double alpha, double beta, double width, double height)`
- `preferentialAttachmentGraph(Graph& G, int nodes, int minDegree)`
- `randomWattsStrogatzGraph(Graph& G, int n, int k, double probability)`
- `randomChungLuGraph(Graph& G, Array<int> expectedDegreeDistribution)`
- `randomEdgesGraph(Graph& G, function<double(node,node)> probability)`
- `randomProperMaximalLevelPlaneGraph(Graph& G, vector<vector<node>>& emb, int N, int K, bool radial)`
- `randomHierarchy(Graph& G, int n, int m, bool planar, bool singleSource, bool longEdges)`
- `pruneEdges(Graph& G, int max_edges, int min_deg)`
- `randomGeographicalThresholdGraph(...)`

### Cluster / Simultaneous Generators

- `randomCConnectedClustering(ClusterGraph& C, int cNum)`
- `randomClustering(ClusterGraph& C, int cNum)`
- `randomClustering(ClusterGraph& C, const node root, int moreInLeaves)`
- `randomPlanarClustering(ClusterGraph& CG, const RandomClusterConfig& config)`
- `randomClusterPlanarGraph(Graph& G, ClusterGraph& CG, int clusters, int node_per_cluster, int edges_per_cluster)`
- `randomSyncPlanInstance(sync_plan::SyncPlan& pq, int pipe_count, int min_deg)`
- `randomSEFEInstanceBySharedGraph(Graph* sefe, EdgeArray<uint8_t>& edge_types, int edges1, int edges2)`
- `randomSEFEInstanceByUnionGraph(const Graph* sefe, EdgeArray<uint8_t>& edge_types, double frac_shared, double frac_g1)`

### Graph Operations

These are not generators in the strict sense, but they can create new graphs from existing ones.

- `graphUnion(...)`
- `graphProduct(...)`
- `cartesianProduct(...)`
- `tensorProduct(...)`
- `lexicographicalProduct(...)`
- `strongProduct(...)`
- `coNormalProduct(...)`
- `modularProduct(...)`
- `rootedProduct(...)`
- `complement(...)`
- `intersection(...)`
- `join(...)`

## Metrics

These are in `ogdf/basic/LayoutStatistics.h`. They are the most natural metric functions for the bridge.

### Scalar Metrics

- `graphArea(const GraphAttributes& ga)`
- `aspectRatio(const GraphAttributes& ga)`
- `percentageCrossingVsMaxCrossings(const GraphAttributes& ga)`
- `edgeLengthDeviation(const GraphAttributes& ga, EdgeArray<double>& out)`
- `nodeResolution(const GraphAttributes& ga)`
- `angularResolution(const GraphAttributes& ga)`
- `nodeUniformity(const GraphAttributes& ga, size_t gridWidth = 10, size_t gridHeight = 10)`
- `edgeOrthogonality(const GraphAttributes& ga)`
- `closestPairOfPoints(const GraphAttributes& ga)`
- `horizontalVerticalBalance(const GraphAttributes& ga, bool vertical = false)`
- `nodeOrthogonality(const GraphAttributes& ga, double epsilon = 1e-9)`
- `averageFlow(const GraphAttributes& ga)`
- `upwardsFlow(const GraphAttributes& ga)`
- `concentration(const GraphAttributes& ga)`

### Per-Edge Metrics

- `edgeLengths(const GraphAttributes& ga, bool considerSelfLoops = false)`
- `numberOfBends(const GraphAttributes& ga, bool considerSelfLoops = false)`
- `numberOfCrossings(const GraphAttributes& ga)`
- `numberOfNodeCrossings(const GraphAttributes& ga)`

### Per-Node Metrics

- `numberOfNodeOverlaps(const GraphAttributes& ga)`
- `neighbourhoodPreservation(const GraphAttributes& ga)`
- `gabrielRatio(const GraphAttributes& ga, Graph& gabrielGraphReference)`

### Geometry / Debug Output

- `angles(const GraphAttributes& ga, bool considerBends = true)`
- `centerOfMass(const GraphAttributes& ga)`
- `intersectionGraph(const GraphAttributes& ga, Graph& H, NodeArray<DPoint>& points, NodeArray<node>& origNode, EdgeArray<edge>& origEdge)`

### Simple Metrics We Can Compute Without OGDF Helpers

These are useful for VANTED even if they are not dedicated `LayoutStatistics` calls:

- node count
- edge count
- graph density
- average degree
- min/max degree
- connected component count
- directed/undirected flag
- self-loop count
- parallel edge count

## Suggested Implementation Order

1. `metrics`
   - Expose all `LayoutStatistics` values first.
   - This gives immediate useful output and is safer than changing graph geometry.
2. `layout`
   - Start with:
     - `SugiyamaLayout`
     - `FMMMLayout`
     - `StressMinimization`
     - `SpringEmbedderFRExact`
     - `SpringEmbedderKK`
     - `CircularLayout`
     - `TreeLayout`
     - `RadialTreeLayout`
     - `PlanarizationLayout`
   - Add a generic `iterations` parameter and map it per algorithm.
3. `generate`
   - Start with simple deterministic/random generators:
     - `emptyGraph`
     - `completeGraph`
     - `completeBipartiteGraph`
     - `gridGraph`
     - `regularTree`
     - `randomGraph`
     - `randomSimpleGraph`
     - `randomTree`
     - `randomPlanarConnectedGraph`
     - `randomHierarchy`
4. Advanced layouts
   - Cluster, UML, orthogonal, and advanced planar/upward algorithms require more special OGDF objects and should be added after the basic bridge API is stable.

