#include <algorithm>
#include <chrono>
#include <cctype>
#include <cmath>
#include <iomanip>
#include <iostream>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <string>

#include <ogdf/basic/Graph.h>
#include <ogdf/basic/GraphAttributes.h>
#include <ogdf/basic/LayoutStatistics.h>
#include <ogdf/basic/graph_generators.h>
#include <ogdf/basic/simple_graph_alg.h>
#include <ogdf/energybased/DavidsonHarelLayout.h>
#include <ogdf/energybased/FMMMLayout.h>
#include <ogdf/energybased/GEMLayout.h>
#include <ogdf/energybased/PivotMDS.h>
#include <ogdf/energybased/SpringEmbedderFRExact.h>
#include <ogdf/energybased/SpringEmbedderKK.h>
#include <ogdf/energybased/StressMinimization.h>
#include <ogdf/fileformats/GraphIO.h>
#include <ogdf/layered/BarycenterHeuristic.h>
#include <ogdf/layered/CoffmanGrahamRanking.h>
#include <ogdf/layered/FastHierarchyLayout.h>
#include <ogdf/layered/FastSimpleHierarchyLayout.h>
#include <ogdf/layered/GreedyInsertHeuristic.h>
#include <ogdf/layered/GreedySwitchHeuristic.h>
#include <ogdf/layered/LongestPathRanking.h>
#include <ogdf/layered/MedianHeuristic.h>
#include <ogdf/layered/OptimalHierarchyLayout.h>
#include <ogdf/layered/OptimalRanking.h>
#include <ogdf/layered/SiftingHeuristic.h>
#include <ogdf/layered/SugiyamaLayout.h>
#include <ogdf/misclayout/BalloonLayout.h>
#include <ogdf/misclayout/CircularLayout.h>
#include <ogdf/misclayout/LinearLayout.h>
#include <ogdf/orthogonal/OrthoLayout.h>
#include <ogdf/planarity/EmbedderMaxFace.h>
#include <ogdf/planarity/EmbedderMinDepthMaxFace.h>
#include <ogdf/planarity/FixedEmbeddingInserter.h>
#include <ogdf/planarity/PlanarizationLayout.h>
#include <ogdf/planarity/PlanarSubgraphBoyerMyrvold.h>
#include <ogdf/planarity/PlanarSubgraphFast.h>
#include <ogdf/planarity/SimpleEmbedder.h>
#include <ogdf/planarity/SubgraphPlanarizer.h>
#include <ogdf/planarity/VariableEmbeddingInserter.h>
#include <ogdf/planarity/VariableEmbeddingInserterDyn.h>
#include <ogdf/tree/RadialTreeLayout.h>
#include <ogdf/tree/TreeLayout.h>

namespace {

struct LayoutRequest {
    std::string layout = "sugiyama";
    int iterations = 0;
    int secondaryIterations = 0;
    double pageRatio = 1.0;
    bool transpose = true;
    bool includeMetrics = true;
    std::string heuristicOne;
    std::string heuristicTwo;
    std::string heuristicThree;
    std::string heuristicFour;
};

struct GeneratorRequest {
    std::string generator = "random_simple_probability";
    int nodes = 12;
    int edges = 16;
    int secondary = 4;
    double probability = 0.25;
    bool directed = false;
    bool labels = true;
};

std::string readAllStdin() {
    std::ostringstream buffer;
    buffer << std::cin.rdbuf();
    return buffer.str();
}

std::string trim(std::string value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) {
        return "";
    }
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1);
}

std::string toLower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
            [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
}

std::string xmlDecode(std::string value) {
    struct Replacement {
        const char* from;
        const char* to;
    };
    const Replacement replacements[] = {
            {"&quot;", "\""},
            {"&apos;", "'"},
            {"&lt;", "<"},
            {"&gt;", ">"},
            {"&amp;", "&"}};

    for (const Replacement& replacement : replacements) {
        std::string::size_type pos = 0;
        while ((pos = value.find(replacement.from, pos)) != std::string::npos) {
            value.replace(pos, std::char_traits<char>::length(replacement.from), replacement.to);
            pos += std::char_traits<char>::length(replacement.to);
        }
    }
    return value;
}

std::string graphDataValue(const std::string& graphMl, const std::string& keyId) {
    const std::string keyNeedle = "key=\"" + keyId + "\"";
    std::string::size_type searchPos = 0;
    while ((searchPos = graphMl.find("<data", searchPos)) != std::string::npos) {
        const auto tagEnd = graphMl.find('>', searchPos);
        if (tagEnd == std::string::npos) {
            return "";
        }
        const std::string tag = graphMl.substr(searchPos, tagEnd - searchPos + 1);
        const auto closePos = graphMl.find("</data>", tagEnd);
        if (closePos == std::string::npos) {
            return "";
        }
        if (tag.find(keyNeedle) != std::string::npos) {
            return trim(xmlDecode(graphMl.substr(tagEnd + 1, closePos - tagEnd - 1)));
        }
        searchPos = closePos + 7;
    }
    return "";
}

int parseInt(const std::string& text, int fallback) {
    try {
        const std::string cleaned = trim(text);
        if (cleaned.empty()) {
            return fallback;
        }
        return std::stoi(cleaned);
    } catch (...) {
        return fallback;
    }
}

double parseDouble(const std::string& text, double fallback) {
    try {
        const std::string cleaned = trim(text);
        if (cleaned.empty()) {
            return fallback;
        }
        return std::stod(cleaned);
    } catch (...) {
        return fallback;
    }
}

bool parseBool(const std::string& text, bool fallback) {
    const std::string value = toLower(trim(text));
    if (value == "true" || value == "1" || value == "yes") {
        return true;
    }
    if (value == "false" || value == "0" || value == "no") {
        return false;
    }
    return fallback;
}

LayoutRequest parseLayoutRequest(const std::string& graphMl) {
    LayoutRequest request;
    const std::string layout = graphDataValue(graphMl, "k_ogdf_layout");
    if (!layout.empty()) {
        request.layout = toLower(layout);
    }
    request.iterations = parseInt(graphDataValue(graphMl, "k_ogdf_iterations"), request.iterations);
    request.secondaryIterations = parseInt(
            graphDataValue(graphMl, "k_ogdf_secondary_iterations"), request.secondaryIterations);
    request.pageRatio = parseDouble(graphDataValue(graphMl, "k_ogdf_page_ratio"), request.pageRatio);
    request.transpose = parseBool(graphDataValue(graphMl, "k_ogdf_transpose"), request.transpose);
    request.includeMetrics = parseBool(graphDataValue(graphMl, "k_ogdf_include_metrics"), request.includeMetrics);
    request.heuristicOne = toLower(graphDataValue(graphMl, "k_ogdf_heuristic_one"));
    request.heuristicTwo = toLower(graphDataValue(graphMl, "k_ogdf_heuristic_two"));
    request.heuristicThree = toLower(graphDataValue(graphMl, "k_ogdf_heuristic_three"));
    request.heuristicFour = toLower(graphDataValue(graphMl, "k_ogdf_heuristic_four"));
    return request;
}

bool isGenerateRequest(const std::string& graphMl) {
    return toLower(graphDataValue(graphMl, "k_ogdf_mode")) == "generate";
}

bool isMetricsRequest(const std::string& graphMl) {
    return toLower(graphDataValue(graphMl, "k_ogdf_mode")) == "metrics";
}

GeneratorRequest parseGeneratorRequest(const std::string& graphMl) {
    GeneratorRequest request;
    const std::string generator = graphDataValue(graphMl, "k_ogdf_generator");
    if (!generator.empty()) {
        request.generator = toLower(generator);
    }
    request.nodes = parseInt(graphDataValue(graphMl, "k_ogdf_generator_nodes"), request.nodes);
    request.edges = parseInt(graphDataValue(graphMl, "k_ogdf_generator_edges"), request.edges);
    request.secondary = parseInt(graphDataValue(graphMl, "k_ogdf_generator_secondary"), request.secondary);
    request.probability = parseDouble(graphDataValue(graphMl, "k_ogdf_generator_probability"), request.probability);
    request.directed = parseBool(graphDataValue(graphMl, "k_ogdf_generator_directed"), request.directed);
    request.labels = parseBool(graphDataValue(graphMl, "k_ogdf_generator_labels"), request.labels);
    return request;
}

void ensureNodeSizes(ogdf::GraphAttributes& ga) {
    for (ogdf::node v : ga.constGraph().nodes) {
        if (!(ga.width(v) > 0.0)) {
            ga.width(v) = 30.0;
        }
        if (!(ga.height(v) > 0.0)) {
            ga.height(v) = 30.0;
        }
    }
}

void clearEdgeBends(ogdf::GraphAttributes& ga) {
    for (ogdf::edge e : ga.constGraph().edges) {
        ga.bends(e).clear();
    }
}

void runSpringFallback(ogdf::GraphAttributes& ga, int iterations, const std::string& reason) {
    std::cerr << "Warning: " << reason << " Falling back to Spring FR exact." << std::endl;
    ogdf::SpringEmbedderFRExact layout;
    layout.iterations(iterations > 0 ? iterations : 400);
    layout.call(ga);
}

void runBalloonFallback(ogdf::GraphAttributes& ga, const std::string& reason) {
    std::cerr << "Warning: " << reason << " Falling back to Balloon layout." << std::endl;
    ogdf::BalloonLayout layout;
    layout.call(ga);
}

void ensureFiniteNodePositions(ogdf::GraphAttributes& ga) {
    int index = 0;
    for (ogdf::node v : ga.constGraph().nodes) {
        if (!std::isfinite(ga.x(v)) || !std::isfinite(ga.y(v))) {
            ga.x(v) = static_cast<double>((index % 10) * 80);
            ga.y(v) = static_cast<double>((index / 10) * 80);
        }
        ++index;
    }
}

void applyGridCoordinates(ogdf::GraphAttributes& ga, int columns) {
    const int safeColumns = std::max(1, columns);
    int index = 0;
    for (ogdf::node v : ga.constGraph().nodes) {
        ga.x(v) = static_cast<double>((index % safeColumns) * 90);
        ga.y(v) = static_cast<double>((index / safeColumns) * 90);
        ++index;
    }
}

ogdf::RankingModule* createSugiyamaRanking(const std::string& id) {
    if (id == "optimal") {
        return new ogdf::OptimalRanking;
    }
    if (id == "coffman_graham") {
        return new ogdf::CoffmanGrahamRanking;
    }
    return new ogdf::LongestPathRanking;
}

ogdf::LayeredCrossMinModule* createSugiyamaCrossMin(const std::string& id) {
    if (id == "median") {
        return new ogdf::MedianHeuristic;
    }
    if (id == "sifting") {
        return new ogdf::SiftingHeuristic;
    }
    if (id == "greedy_switch") {
        return new ogdf::GreedySwitchHeuristic;
    }
    if (id == "greedy_insert") {
        return new ogdf::GreedyInsertHeuristic;
    }
    return new ogdf::BarycenterHeuristic;
}

ogdf::HierarchyLayoutModule* createSugiyamaCoordinateAssignment(const std::string& id) {
    if (id == "fast_simple") {
        return new ogdf::FastSimpleHierarchyLayout;
    }
    if (id == "optimal") {
        return new ogdf::OptimalHierarchyLayout;
    }
    return new ogdf::FastHierarchyLayout;
}

ogdf::PlanarSubgraphModule<int>* createPlanarSubgraphHeuristic(const std::string& id, int runs) {
    const int safeRuns = std::max(1, runs);
    if (id == "boyer_myrvold") {
        return new ogdf::PlanarSubgraphBoyerMyrvold(safeRuns, 0.5);
    }
    auto* subgraph = new ogdf::PlanarSubgraphFast<int>;
    subgraph->runs(safeRuns);
    return subgraph;
}

ogdf::EdgeInsertionModule* createEdgeInserterHeuristic(const std::string& id) {
    if (id == "fixed") {
        return new ogdf::FixedEmbeddingInserter;
    }
    if (id == "variable_dynamic") {
        return new ogdf::VariableEmbeddingInserterDyn;
    }
    return new ogdf::VariableEmbeddingInserter;
}

ogdf::EmbedderModule* createOrthogonalEmbedder(const std::string& id) {
    if (id == "min_depth_max_face") {
        return new ogdf::EmbedderMinDepthMaxFace;
    }
    if (id == "max_face") {
        return new ogdf::EmbedderMaxFace;
    }
    return new ogdf::SimpleEmbedder;
}

ogdf::OrthoLayout* createOrthogonalLayouter(const LayoutRequest& request, int bendBound) {
    const std::string model = request.heuristicFour.empty() ? "progressive" : request.heuristicFour;
    auto* ortho = new ogdf::OrthoLayout;
    ortho->progressive(model != "traditional" && model != "traditional_scaled");
    ortho->scaling(model == "progressive_scaled" || model == "traditional_scaled");
    if (bendBound > 0) {
        ortho->bendBound(bendBound);
    }
    return ortho;
}

void runSelectedLayout(ogdf::GraphAttributes& ga, const LayoutRequest& request) {
    const std::string id = toLower(request.layout);
    const int iterations = std::max(0, request.iterations);
    const int secondary = std::max(0, request.secondaryIterations);
    const double pageRatio = request.pageRatio > 0.0 ? request.pageRatio : 1.0;

    clearEdgeBends(ga);

    if (id == "sugiyama") {
        ogdf::SugiyamaLayout layout;
        layout.setRanking(createSugiyamaRanking(request.heuristicOne));
        layout.setCrossMin(createSugiyamaCrossMin(request.heuristicTwo));
        layout.setLayout(createSugiyamaCoordinateAssignment(request.heuristicThree));
        if (iterations > 0) {
            layout.runs(iterations);
        }
        if (secondary > 0) {
            layout.fails(secondary);
        }
        layout.transpose(request.transpose);
        layout.pageRatio(pageRatio);
        layout.call(ga);
    } else if (id == "fmmm") {
        ogdf::FMMMLayout layout;
        if (iterations > 0) {
            layout.fixedIterations(iterations);
        }
        if (secondary > 0) {
            layout.fineTuningIterations(secondary);
        }
        layout.call(ga);
    } else if (id == "stress") {
        ogdf::StressMinimization layout;
        if (iterations > 0) {
            layout.setIterations(iterations);
        }
        layout.call(ga);
    } else if (id == "spring_fr") {
        ogdf::SpringEmbedderFRExact layout;
        if (iterations > 0) {
            layout.iterations(iterations);
        }
        layout.call(ga);
    } else if (id == "spring_kk") {
        ogdf::SpringEmbedderKK layout;
        if (iterations > 0) {
            layout.computeMaxIterations(false);
            layout.setMaxGlobalIterations(iterations);
        }
        if (secondary > 0) {
            layout.setMaxLocalIterations(secondary);
        }
        layout.call(ga);
    } else if (id == "davidson_harel") {
        ogdf::DavidsonHarelLayout layout;
        if (iterations > 0) {
            layout.setNumberOfIterations(iterations);
            layout.setIterationNumberAsFactor(false);
        }
        layout.call(ga);
    } else if (id == "gem") {
        ogdf::GEMLayout layout;
        layout.call(ga);
    } else if (id == "circular") {
        ogdf::CircularLayout layout;
        layout.pageRatio(pageRatio);
        layout.call(ga);
    } else if (id == "balloon") {
        ogdf::BalloonLayout layout;
        layout.call(ga);
    } else if (id == "linear") {
        ogdf::LinearLayout layout;
        layout.call(ga);
    } else if (id == "tree") {
        if (!ogdf::isAcyclicUndirected(ga.constGraph())) {
            runSpringFallback(ga, iterations, "TreeLayout requires a forest.");
            return;
        }
        ogdf::TreeLayout layout;
        layout.siblingDistance(60.0);
        layout.subtreeDistance(80.0);
        layout.levelDistance(90.0);
        layout.treeDistance(120.0);
        layout.call(ga);
    } else if (id == "radial_tree") {
        if (!ogdf::isTree(ga.constGraph())) {
            runBalloonFallback(ga, "RadialTreeLayout requires one connected tree.");
            return;
        }
        ogdf::RadialTreeLayout layout;
        layout.levelDistance(90.0);
        layout.call(ga);
    } else if (id == "planarization") {
        ogdf::PlanarizationLayout layout;
        auto* crossMin = new ogdf::SubgraphPlanarizer;
        const int permutations = iterations > 0 ? iterations : 1;
        crossMin->permutations(permutations);
        crossMin->setSubgraph(createPlanarSubgraphHeuristic(request.heuristicOne, permutations));
        crossMin->setInserter(createEdgeInserterHeuristic(request.heuristicTwo));
        layout.setCrossMin(crossMin);
        layout.setEmbedder(createOrthogonalEmbedder(request.heuristicThree));
        layout.setPlanarLayouter(createOrthogonalLayouter(request, secondary));
        layout.pageRatio(pageRatio);
        layout.call(ga);
    } else if (id == "pivot_mds") {
        if (!ogdf::isConnected(ga.constGraph())) {
            runSpringFallback(ga, iterations, "PivotMDS requires a connected graph.");
            return;
        }
        ogdf::PivotMDS layout;
        layout.call(ga);
    } else {
        throw std::runtime_error("Unsupported OGDF layout: " + request.layout);
    }
}

void applyGeneratedLayout(ogdf::GraphAttributes& ga, const std::string& generator, int secondary) {
    if (ga.constGraph().numberOfNodes() == 0) {
        return;
    }
    if (generator == "grid") {
        applyGridCoordinates(ga, secondary);
        return;
    }

    LayoutRequest request;
    request.includeMetrics = false;
    request.iterations = 200;
    request.secondaryIterations = 0;
    request.layout = (generator == "random_tree" || generator == "regular_tree") ? "tree" : "fmmm";
    try {
        runSelectedLayout(ga, request);
    } catch (...) {
        LayoutRequest fallback;
        fallback.includeMetrics = false;
        fallback.layout = "circular";
        runSelectedLayout(ga, fallback);
    }
}

void setGeneratedAttributes(ogdf::GraphAttributes& ga, bool labels) {
    int index = 0;
    for (ogdf::node v : ga.constGraph().nodes) {
        ga.idNode(v) = index;
        ga.width(v) = 25.0;
        ga.height(v) = 25.0;
        if (labels) {
            ga.label(v) = std::to_string(index + 1);
        }
        ++index;
    }
}

void generateGraph(ogdf::Graph& g, const GeneratorRequest& request) {
    const std::string id = toLower(request.generator);
    const int n = std::max(0, request.nodes);
    const int secondary = std::max(1, request.secondary);
    const double probability = std::min(1.0, std::max(0.0, request.probability));
    const long maxEdges = n > 1 ? (static_cast<long>(n) * static_cast<long>(n - 1)) / 2L : 0L;
    const int m = static_cast<int>(std::min<long>(std::max(0, request.edges), maxEdges));

    if (id == "random_simple_probability") {
        if (request.directed) {
            ogdf::randomDigraph(g, n, probability);
        } else if (!ogdf::randomSimpleGraphByProbability(g, n, probability)) {
            throw std::runtime_error("Could not generate random G(n,p) graph.");
        }
    } else if (id == "random_simple_edges") {
        if (!ogdf::randomSimpleGraph(g, n, m)) {
            throw std::runtime_error("Could not generate random G(n,m) graph.");
        }
    } else if (id == "random_connected") {
        const int connectedEdges = n <= 1 ? 0 : std::max(n - 1, m);
        if (!ogdf::randomSimpleConnectedGraph(g, n, connectedEdges)) {
            throw std::runtime_error("Could not generate connected graph.");
        }
    } else if (id == "random_tree") {
        ogdf::randomTree(g, n);
    } else if (id == "regular_tree") {
        ogdf::regularTree(g, n, secondary);
    } else if (id == "planar_connected") {
        ogdf::randomPlanarConnectedGraph(g, n, m);
    } else if (id == "complete") {
        ogdf::completeGraph(g, n);
    } else if (id == "grid") {
        ogdf::gridGraph(g, std::max(1, n), secondary, false, false);
    } else {
        throw std::runtime_error("Unsupported OGDF graph generator: " + request.generator);
    }
}

void emitMetric(std::ostream& out, const std::string& key, double value) {
    if (std::isfinite(value)) {
        out << ' ' << key << '=' << std::setprecision(10) << value;
    }
}

void emitMetric(std::ostream& out, const std::string& key, size_t value) {
    out << ' ' << key << '=' << value;
}

void emitMetric(std::ostream& out, const std::string& key, int value) {
    out << ' ' << key << '=' << value;
}

double safeDensity(size_t n, size_t m, bool directed) {
    if (n < 2) {
        return 0.0;
    }
    const double possibleEdges = directed
            ? static_cast<double>(n) * static_cast<double>(n - 1)
            : (static_cast<double>(n) * static_cast<double>(n - 1)) / 2.0;
    return possibleEdges > 0.0 ? static_cast<double>(m) / possibleEdges : 0.0;
}

double drawingArea(const ogdf::GraphAttributes& ga) {
    if (ga.constGraph().numberOfNodes() < 1) {
        return 0.0;
    }
    const ogdf::DRect bounds = ga.boundingBox();
    return bounds.width() * bounds.height();
}

void emitMetrics(const ogdf::Graph& g, ogdf::GraphAttributes& ga, long long layoutRuntimeMs,
        const std::string& runtimeMetricKey) {
    const size_t nodeCount = static_cast<size_t>(g.numberOfNodes());
    const size_t edgeCount = static_cast<size_t>(g.numberOfEdges());
    const ogdf::EdgeArray<size_t> bendsPerEdge = ogdf::LayoutStatistics::numberOfBends(ga);
    const ogdf::NodeArray<size_t> overlapsPerNode = ogdf::LayoutStatistics::numberOfNodeOverlaps(ga);

    size_t crossingSum = 0;
    size_t nodeCrossingSum = 0;
    size_t bendCount = 0;
    const size_t crossingMetricEdgeLimit = 2000;
    const size_t pairMetricNodeLimit = 2000;
    const bool calculateCrossings = edgeCount <= crossingMetricEdgeLimit;
    const bool calculateNodeCrossings = edgeCount <= crossingMetricEdgeLimit && nodeCount <= pairMetricNodeLimit;
    if (calculateCrossings) {
        const ogdf::EdgeArray<size_t> crossingsPerEdge = ogdf::LayoutStatistics::numberOfCrossings(ga);
        for (ogdf::edge e : g.edges) {
            crossingSum += crossingsPerEdge[e];
        }
    } else {
        std::cerr << "Warning: skipped crossing metric for graph with " << edgeCount << " edges." << std::endl;
    }
    if (calculateNodeCrossings) {
        const ogdf::EdgeArray<size_t> nodeCrossingsPerEdge = ogdf::LayoutStatistics::numberOfNodeCrossings(ga);
        for (ogdf::edge e : g.edges) {
            nodeCrossingSum += nodeCrossingsPerEdge[e];
        }
    } else {
        std::cerr << "Warning: skipped node-crossing metric for graph with "
                  << nodeCount << " nodes and " << edgeCount << " edges." << std::endl;
    }

    double minEdgeLength = std::numeric_limits<double>::infinity();
    double maxEdgeLength = 0.0;
    double edgeLengthSum = 0.0;
    const ogdf::EdgeArray<double> edgeLengths = ogdf::LayoutStatistics::edgeLengths(ga);
    for (ogdf::edge e : g.edges) {
        bendCount += bendsPerEdge[e];
        const double length = edgeLengths[e];
        if (std::isfinite(length)) {
            minEdgeLength = std::min(minEdgeLength, length);
            maxEdgeLength = std::max(maxEdgeLength, length);
            edgeLengthSum += length;
        }
    }
    const double avgEdgeLength = edgeCount > 0 ? edgeLengthSum / static_cast<double>(edgeCount) : 0.0;
    if (!std::isfinite(minEdgeLength)) {
        minEdgeLength = 0.0;
    }

    size_t overlapSum = 0;
    size_t isolatedNodes = 0;
    size_t minDegree = nodeCount > 0 ? std::numeric_limits<size_t>::max() : 0;
    size_t maxDegree = 0;
    size_t degreeSum = 0;
    for (ogdf::node v : g.nodes) {
        const size_t degree = static_cast<size_t>(v->degree());
        if (degree == 0) {
            isolatedNodes++;
        }
        minDegree = std::min(minDegree, degree);
        maxDegree = std::max(maxDegree, degree);
        degreeSum += degree;
        overlapSum += overlapsPerNode[v];
    }
    if (nodeCount == 0) {
        minDegree = 0;
    }
    const double avgDegree = nodeCount > 0 ? static_cast<double>(degreeSum) / static_cast<double>(nodeCount) : 0.0;

    ogdf::EdgeArray<double> edgeLengthDeviationPerEdge(g);
    const double edgeLengthDeviation = ogdf::LayoutStatistics::edgeLengthDeviation(ga, edgeLengthDeviationPerEdge);

    double neighbourhoodPreservationAvg = 0.0;
    if (nodeCount > 0 && nodeCount <= pairMetricNodeLimit) {
        const ogdf::NodeArray<double> neighbourhood = ogdf::LayoutStatistics::neighbourhoodPreservation(ga);
        double sum = 0.0;
        for (ogdf::node v : g.nodes) {
            sum += neighbourhood[v];
        }
        neighbourhoodPreservationAvg = sum / static_cast<double>(nodeCount);
    }

    double gabrielRatioAvg = 0.0;
    const bool calculateGabrielRatio = nodeCount > 0 && nodeCount <= 500 && (nodeCount * std::max<size_t>(edgeCount, 1)) <= 1000000;
    if (calculateGabrielRatio) {
        ogdf::Graph gabrielGraphReference;
        const ogdf::NodeArray<double> gabrielRatios = ogdf::LayoutStatistics::gabrielRatio(ga, gabrielGraphReference);
        double sum = 0.0;
        for (ogdf::node v : g.nodes) {
            sum += gabrielRatios[v];
        }
        gabrielRatioAvg = sum / static_cast<double>(nodeCount);
    }

    std::cerr << "OGDF_METRIC";
    emitMetric(std::cerr, "nodes", nodeCount);
    emitMetric(std::cerr, "edges", edgeCount);
    emitMetric(std::cerr, "density", safeDensity(nodeCount, edgeCount, ga.directed()));
    emitMetric(std::cerr, "components", ogdf::connectedComponents(g));
    emitMetric(std::cerr, "isolatedNodes", isolatedNodes);
    emitMetric(std::cerr, "avgDegree", avgDegree);
    emitMetric(std::cerr, "minDegree", minDegree);
    emitMetric(std::cerr, "maxDegree", maxDegree);
    emitMetric(std::cerr, "bends", bendCount);
    emitMetric(std::cerr, "nodeOverlaps", overlapSum / 2);
    if (!runtimeMetricKey.empty()) {
        emitMetric(std::cerr, runtimeMetricKey, static_cast<size_t>(std::max<long long>(0, layoutRuntimeMs)));
    }
    emitMetric(std::cerr, "graphArea", drawingArea(ga));
    emitMetric(std::cerr, "aspectRatio", ogdf::LayoutStatistics::aspectRatio(ga));
    emitMetric(std::cerr, "minEdgeLength", minEdgeLength);
    emitMetric(std::cerr, "avgEdgeLength", avgEdgeLength);
    emitMetric(std::cerr, "maxEdgeLength", maxEdgeLength);
    emitMetric(std::cerr, "edgeLengthDeviation", edgeLengthDeviation);
    emitMetric(std::cerr, "nodeResolution", ogdf::LayoutStatistics::nodeResolution(ga));
    emitMetric(std::cerr, "angularResolution", ogdf::LayoutStatistics::angularResolution(ga));
    emitMetric(std::cerr, "nodeUniformity", ogdf::LayoutStatistics::nodeUniformity(ga));
    emitMetric(std::cerr, "edgeOrthogonality", ogdf::LayoutStatistics::edgeOrthogonality(ga));
    emitMetric(std::cerr, "closestPairDistance", ogdf::LayoutStatistics::closestPairOfPoints(ga));
    emitMetric(std::cerr, "horizontalBalance", ogdf::LayoutStatistics::horizontalVerticalBalance(ga, false));
    emitMetric(std::cerr, "verticalBalance", ogdf::LayoutStatistics::horizontalVerticalBalance(ga, true));
    emitMetric(std::cerr, "nodeOrthogonality", ogdf::LayoutStatistics::nodeOrthogonality(ga));
    emitMetric(std::cerr, "neighbourhoodPreservation", neighbourhoodPreservationAvg);
    if (calculateGabrielRatio) {
        emitMetric(std::cerr, "gabrielRatio", gabrielRatioAvg);
    }
    if (ga.directed()) {
        emitMetric(std::cerr, "averageFlow", ogdf::LayoutStatistics::averageFlow(ga));
        emitMetric(std::cerr, "upwardsFlow", ogdf::LayoutStatistics::upwardsFlow(ga));
    }
    if (calculateCrossings) {
        emitMetric(std::cerr, "crossings", crossingSum / 2);
        emitMetric(std::cerr, "crossingPercentage", ogdf::LayoutStatistics::percentageCrossingVsMaxCrossings(ga));
    }
    if (calculateNodeCrossings) {
        emitMetric(std::cerr, "nodeCrossings", nodeCrossingSum);
    }
    std::cerr << std::endl;
}

} // namespace

int main() {
    try {
        std::ios::sync_with_stdio(false);
        std::cin.tie(nullptr);

        const std::string graphMlInput = readAllStdin();

        ogdf::Graph g;
        const long attributes = ogdf::GraphAttributes::nodeGraphics
                | ogdf::GraphAttributes::edgeGraphics
                | ogdf::GraphAttributes::nodeLabel
                | ogdf::GraphAttributes::edgeLabel
                | ogdf::GraphAttributes::nodeId;
        ogdf::GraphAttributes ga(g, attributes);

        if (isGenerateRequest(graphMlInput)) {
            GeneratorRequest generatorRequest = parseGeneratorRequest(graphMlInput);
            generateGraph(g, generatorRequest);
            setGeneratedAttributes(ga, generatorRequest.labels);
            ensureNodeSizes(ga);
            applyGeneratedLayout(ga, toLower(generatorRequest.generator), generatorRequest.secondary);
            ensureFiniteNodePositions(ga);
        } else {
            const bool metricsOnly = isMetricsRequest(graphMlInput);
            LayoutRequest request = parseLayoutRequest(graphMlInput);
            std::istringstream input(graphMlInput);
            if (!ogdf::GraphIO::readGraphML(ga, g, input)) {
                std::cerr << "Error: Could not read GraphML input from stdin." << std::endl;
                return 2;
            }

            ensureNodeSizes(ga);
            long long layoutRuntimeMs = 0;
            if (!metricsOnly) {
                const auto layoutStart = std::chrono::steady_clock::now();
                runSelectedLayout(ga, request);
                const auto layoutEnd = std::chrono::steady_clock::now();
                layoutRuntimeMs =
                        std::chrono::duration_cast<std::chrono::milliseconds>(layoutEnd - layoutStart).count();
            }
            ensureFiniteNodePositions(ga);

            if (metricsOnly || request.includeMetrics) {
                emitMetrics(g, ga, layoutRuntimeMs, metricsOnly ? "" : "layoutRuntimeMs");
            }
        }

        if (!ogdf::GraphIO::writeGraphML(ga, std::cout)) {
            std::cerr << "Error: Could not write GraphML output to stdout." << std::endl;
            return 3;
        }

        std::cout.flush();
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "Exception: " << e.what() << std::endl;
        return 10;
    } catch (...) {
        std::cerr << "Unknown exception in ogdf_layout_fixed." << std::endl;
        return 11;
    }
}
