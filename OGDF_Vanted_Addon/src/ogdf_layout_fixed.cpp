#include <algorithm>
#include <cctype>
#include <cmath>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>

#include <ogdf/basic/Graph.h>
#include <ogdf/basic/GraphAttributes.h>
#include <ogdf/basic/LayoutStatistics.h>
#include <ogdf/basic/simple_graph_alg.h>
#include <ogdf/energybased/DavidsonHarelLayout.h>
#include <ogdf/energybased/FMMMLayout.h>
#include <ogdf/energybased/GEMLayout.h>
#include <ogdf/energybased/PivotMDS.h>
#include <ogdf/energybased/SpringEmbedderFRExact.h>
#include <ogdf/energybased/SpringEmbedderKK.h>
#include <ogdf/energybased/StressMinimization.h>
#include <ogdf/fileformats/GraphIO.h>
#include <ogdf/layered/SugiyamaLayout.h>
#include <ogdf/misclayout/BalloonLayout.h>
#include <ogdf/misclayout/CircularLayout.h>
#include <ogdf/misclayout/LinearLayout.h>
#include <ogdf/planarity/PlanarizationLayout.h>
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

void runSelectedLayout(ogdf::GraphAttributes& ga, const LayoutRequest& request) {
    const std::string id = toLower(request.layout);
    const int iterations = std::max(0, request.iterations);
    const int secondary = std::max(0, request.secondaryIterations);
    const double pageRatio = request.pageRatio > 0.0 ? request.pageRatio : 1.0;

    clearEdgeBends(ga);

    if (id == "sugiyama") {
        ogdf::SugiyamaLayout layout;
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

void emitMetrics(const ogdf::Graph& g, ogdf::GraphAttributes& ga) {
    const ogdf::EdgeArray<size_t> bendsPerEdge = ogdf::LayoutStatistics::numberOfBends(ga);
    const ogdf::NodeArray<size_t> overlapsPerNode = ogdf::LayoutStatistics::numberOfNodeOverlaps(ga);

    size_t crossingSum = 0;
    size_t bendCount = 0;
    const int crossingMetricEdgeLimit = 2000;
    const bool calculateCrossings = g.numberOfEdges() <= crossingMetricEdgeLimit;
    if (calculateCrossings) {
        const ogdf::EdgeArray<size_t> crossingsPerEdge = ogdf::LayoutStatistics::numberOfCrossings(ga);
        for (ogdf::edge e : g.edges) {
            crossingSum += crossingsPerEdge[e];
        }
    } else {
        std::cerr << "Warning: skipped crossing metric for graph with "
                  << static_cast<size_t>(g.numberOfEdges()) << " edges." << std::endl;
    }

    for (ogdf::edge e : g.edges) {
        bendCount += bendsPerEdge[e];
    }

    size_t overlapSum = 0;
    for (ogdf::node v : g.nodes) {
        overlapSum += overlapsPerNode[v];
    }

    std::cerr << "OGDF_METRIC bends=" << bendCount
              << " nodeOverlaps=" << (overlapSum / 2)
              << " nodes=" << static_cast<size_t>(g.numberOfNodes())
              << " edges=" << static_cast<size_t>(g.numberOfEdges());
    if (calculateCrossings) {
        std::cerr << " crossings=" << (crossingSum / 2);
    }
    std::cerr << std::endl;
}

} // namespace

int main() {
    try {
        std::ios::sync_with_stdio(false);
        std::cin.tie(nullptr);

        const std::string graphMlInput = readAllStdin();
        LayoutRequest request = parseLayoutRequest(graphMlInput);

        ogdf::Graph g;
        const long attributes = ogdf::GraphAttributes::nodeGraphics
                | ogdf::GraphAttributes::edgeGraphics
                | ogdf::GraphAttributes::nodeLabel
                | ogdf::GraphAttributes::edgeLabel
                | ogdf::GraphAttributes::nodeId;
        ogdf::GraphAttributes ga(g, attributes);

        std::istringstream input(graphMlInput);
        if (!ogdf::GraphIO::readGraphML(ga, g, input)) {
            std::cerr << "Error: Could not read GraphML input from stdin." << std::endl;
            return 2;
        }

        ensureNodeSizes(ga);
        runSelectedLayout(ga, request);
        ensureFiniteNodePositions(ga);

        if (request.includeMetrics) {
            emitMetrics(g, ga);
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
