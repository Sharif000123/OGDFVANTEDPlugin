#include <iostream>
#include <stdexcept>
#include <vector>

#include <ogdf/basic/Graph.h>
#include <ogdf/basic/GraphAttributes.h>
#include <ogdf/layered/SugiyamaLayout.h>

int main() {
    try {
        int nodeCount = 0;
        int edgeCount = 0;
        if (!(std::cin >> nodeCount >> edgeCount)) {
            std::cerr << "Error: Could not read graph size from stdin." << std::endl;
            return 2;
        }
        if (nodeCount < 0 || edgeCount < 0) {
            std::cerr << "Error: Negative node/edge counts are invalid." << std::endl;
            return 3;
        }

        ogdf::Graph g;
        ogdf::GraphAttributes ga(g, ogdf::GraphAttributes::nodeGraphics | ogdf::GraphAttributes::edgeGraphics);

        std::vector<ogdf::node> nodes;
        nodes.reserve(static_cast<size_t>(nodeCount));

        for (int i = 0; i < nodeCount; ++i) {
            ogdf::node n = g.newNode();
            ga.width(n) = 10.0;
            ga.height(n) = 10.0;
            nodes.push_back(n);
        }

        for (int i = 0; i < edgeCount; ++i) {
            int src = 0;
            int tgt = 0;
            if (!(std::cin >> src >> tgt)) {
                std::cerr << "Error: Could not read edge " << i << " from stdin." << std::endl;
                return 4;
            }
            if (src < 0 || src >= nodeCount || tgt < 0 || tgt >= nodeCount) {
                std::cerr << "Error: Edge index out of bounds: " << src << " -> " << tgt << std::endl;
                return 5;
            }
            g.newEdge(nodes[static_cast<size_t>(src)], nodes[static_cast<size_t>(tgt)]);
        }

        ogdf::SugiyamaLayout layout;
        layout.call(ga);

        std::cout << nodeCount << '\n';
        for (int i = 0; i < nodeCount; ++i) {
            ogdf::node n = nodes[static_cast<size_t>(i)];
            std::cout << ga.x(n) << ' ' << ga.y(n) << '\n';
        }
        std::cout.flush();
        return 0;
    } catch (const std::exception &e) {
        std::cerr << "Exception: " << e.what() << std::endl;
        return 10;
    } catch (...) {
        std::cerr << "Unknown exception in ogdf_layout_fixed." << std::endl;
        return 11;
    }
}
