package ogdf.integration;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import org.AttributeHelper;
import org.Vector2d;
import org.graffiti.graph.Edge;
import org.graffiti.graph.Graph;
import org.graffiti.graph.Node;
import org.graffiti.plugin.algorithm.AbstractAlgorithm;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class OgdfLayoutAlgorithm extends AbstractAlgorithm {

    // GraphML keys and user-configurable runtime lookup names.
    private static final Preferences PREFS = Preferences.userNodeForPackage(OgdfLayoutAlgorithm.class);

    private static final String KEY_NODE_ID = "nodeid";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_NODE_LABEL = "label";
    private static final String KEY_EDGE_ID = "edgeid";
    private static final String KEY_EDGE_LABEL = "edgelabel";
    private static final String KEY_EDGE_BENDS = "bends";
    private static final String KEY_LAYOUT_ID = "ogdf.layout";
    private static final String KEY_LAYOUT_ITERATIONS = "ogdf.iterations";
    private static final String KEY_LAYOUT_SECONDARY_ITERATIONS = "ogdf.secondaryIterations";
    private static final String KEY_LAYOUT_PAGE_RATIO = "ogdf.pageRatio";
    private static final String KEY_LAYOUT_TRANSPOSE = "ogdf.transpose";
    private static final String KEY_LAYOUT_INCLUDE_METRICS = "ogdf.includeMetrics";
    private static final String METRIC_PREFIX = "OGDF_METRIC";

    private static final String ENV_LAYOUT_EXE = "OGDF_LAYOUT_EXE";
    private static final String ENV_OGDF_DIR = "OGDF_DIR";
    private static final String ENV_RUNTIME_DIR = "OGDF_RUNTIME_DIR";
    private static final String ENV_LIB_DIR = "OGDF_LIB_DIR";
    private static final String PROP_LAYOUT_EXE = "ogdf.layout.exe";
    private static final String PROP_OGDF_DIR = "ogdf.dir";
    private static final String PROP_RUNTIME_DIR = "ogdf.runtime.dir";
    private static final String PROP_LIB_DIR = "ogdf.lib.dir";
    private static final String PREF_LAYOUT_EXE = "layoutExecutable";
    private static final String PREF_OGDF_DIR = "ogdfDirectory";
    private static final String PREF_RUNTIME_DIR = "runtimeDirectory";
    private static final String PREF_SELECTED_LAYOUT = "selectedLayout";
    private static final String PREF_LAYOUT_ITERATIONS = "layoutIterations";
    private static final String PREF_LAYOUT_SECONDARY_ITERATIONS = "layoutSecondaryIterations";
    private static final String PREF_LAYOUT_PAGE_RATIO = "layoutPageRatio";
    private static final String PREF_LAYOUT_TRANSPOSE = "layoutTranspose";
    private static final String PREF_LAYOUT_INCLUDE_METRICS = "layoutIncludeMetrics";
    private static final String[] LAYOUT_EXECUTABLE_BASE_NAMES = {
            "ogdf_layout_fixed",
            "metrics_layout"
    };
    private static final double LAYOUT_FIT_MARGIN = 80.0;
    private static final double LAYOUT_MIN_SPREAD = 180.0;
    private static final double LAYOUT_NODE_SPACING = 95.0;
    private static final double LAYOUT_MAX_SCALE_UP = 4.0;
    private static final int OPTION_LAYOUT_ID = 0;
    private static final int OPTION_LAYOUT_NAME = 1;
    private static final int OPTION_ITERATIONS = 2;
    private static final int OPTION_SECONDARY_ITERATIONS = 3;
    private static final int OPTION_PAGE_RATIO = 4;
    private static final int OPTION_TRANSPOSE = 5;
    private static final int OPTION_INCLUDE_METRICS = 6;
    private static final String[] LAYOUT_IDS = {
            "sugiyama",
            "fmmm",
            "stress",
            "spring_fr",
            "spring_kk",
            "davidson_harel",
            "gem",
            "circular",
            "balloon",
            "linear",
            "tree",
            "radial_tree",
            "planarization",
            "pivot_mds"
    };
    private static final String[] LAYOUT_NAMES = {
            "Sugiyama (layered)",
            "FMMM force-directed",
            "Stress minimization",
            "Spring FR exact",
            "Spring Kamada-Kawai",
            "Davidson-Harel",
            "GEM",
            "Circular",
            "Balloon",
            "Linear",
            "Tree",
            "Radial tree",
            "Planarization",
            "Pivot MDS"
    };
    private static final String[] LAYOUT_DESCRIPTIONS = {
            "Layered drawing for directed or hierarchical graphs.",
            "Fast multipole force-directed drawing for general graphs.",
            "Distance-preserving force layout; best when the graph is connected.",
            "Fruchterman-Reingold force-directed layout for general graphs.",
            "Kamada-Kawai spring layout; best when the graph is connected.",
            "Simulated annealing style force-directed layout.",
            "Force-directed layout with built-in OGDF defaults.",
            "Places nodes on circles; useful for overview drawings.",
            "Radial balloon layout based on a spanning tree.",
            "Places nodes along a line; useful for ordering, not dense readability.",
            "Tree/forest drawing; requires a graph without undirected cycles.",
            "Radial tree drawing; requires one connected tree.",
            "Planarization-based layout for general graphs.",
            "Multidimensional scaling layout; requires a connected graph."
    };
    private static final String[] LAYOUT_ITERATION_LABELS = {
            "Runs",
            "Fixed iterations",
            "Iterations",
            "Iterations",
            "Global iterations",
            "Iterations",
            "Iterations",
            "Iterations",
            "Iterations",
            "Iterations",
            "Iterations",
            "Iterations",
            "Iterations",
            "Iterations"
    };
    private static final String[] LAYOUT_SECONDARY_LABELS = {
            "Fails",
            "Fine-tuning iterations",
            "Secondary iterations",
            "Secondary iterations",
            "Local iterations",
            "Secondary iterations",
            "Secondary iterations",
            "Secondary iterations",
            "Secondary iterations",
            "Secondary iterations",
            "Secondary iterations",
            "Secondary iterations",
            "Secondary iterations",
            "Secondary iterations"
    };
    private static final boolean[] LAYOUT_USES_ITERATIONS = {
            true, true, true, true, true, true, false, false, false, false, false, false, false, false
    };
    private static final boolean[] LAYOUT_USES_SECONDARY_ITERATIONS = {
            true, true, false, false, true, false, false, false, false, false, false, false, false, false
    };
    private static final boolean[] LAYOUT_USES_PAGE_RATIO = {
            true, false, false, false, false, false, false, true, false, false, false, false, true, false
    };
    private static final boolean[] LAYOUT_USES_TRANSPOSE = {
            true, false, false, false, false, false, false, false, false, false, false, false, false, false
    };
    private static final int[] LAYOUT_DEFAULT_ITERATIONS = {
            15, 30, 200, 400, 200, 10, 0, 0, 0, 0, 0, 0, 0, 0
    };
    private static final int[] LAYOUT_DEFAULT_SECONDARY_ITERATIONS = {
            4, 20, 0, 0, 200, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    private static final double[] LAYOUT_DEFAULT_PAGE_RATIOS = {
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0
    };
    private static final boolean[] LAYOUT_DEFAULT_TRANSPOSE = {
            true, false, false, false, false, false, false, false, false, false, false, false, false, false
    };

    @Override
    public String getName() {
        return "Apply OGDF Layout"; // Text on the button in VANTED
    }
    @Override
    public String getCategory() {
        return "Layout"; // This explicitly targets the "Layout" tab in VANTED
    }

    // Runs the bridge flow: VANTED graph -> GraphML -> OGDF -> VANTED graph.
    @Override
    public void execute() {
        Graph activeGraph = this.graph;

        if (activeGraph == null || activeGraph.isEmpty()) {
            System.out.println("No graph is open to layout!");
            return;
        }

        try {
            List<Node> nodeList = new ArrayList<>(activeGraph.getNodes());
            List<Edge> edgeList = new ArrayList<>(activeGraph.getEdges());
            Map<Node, Integer> nodeToIndex = new HashMap<>();

            for (int i = 0; i < nodeList.size(); i++) {
                nodeToIndex.put(nodeList.get(i), i);
            }

            Object[] layoutOptions = chooseLayoutOptions(activeGraph);
            if (layoutOptions == null) {
                return;
            }

            String graphMlRequest = buildGraphMlRequest(activeGraph, nodeList, edgeList, nodeToIndex, layoutOptions);
            RuntimeConfig runtime = resolveRuntimeConfig();
            if (runtime.executablePath == null) {
                runtime = chooseOgdfDirectoryAndRemember(runtime);
            }
            if (runtime.executablePath == null) {
                System.err.println("OGDF bridge executable not found. Configure " + PROP_OGDF_DIR + ", "
                        + ENV_OGDF_DIR + ", " + PROP_LAYOUT_EXE + ", or " + ENV_LAYOUT_EXE + ".");
                return;
            }

            Process process = startOgdfProcess(runtime);
            ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            Thread stdoutReader = startStreamReader(process.getInputStream(), stdoutBuffer, "OGDF stdout reader");
            Thread stderrReader = startStreamReader(process.getErrorStream(), stderrBuffer, "OGDF stderr reader");

            try (OutputStream output = process.getOutputStream()) {
                output.write(graphMlRequest.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            int exitCode = process.waitFor();
            stdoutReader.join();
            stderrReader.join();
            String stdout = bufferToString(stdoutBuffer);
            String stderr = bufferToString(stderrBuffer).trim();

            if (exitCode != 0) {
                System.err.println("OGDF process failed with exit code " + exitCode + " (" + runtime.executablePath + ").");
                if (!stderr.isEmpty()) {
                    System.err.println("OGDF stderr: " + stderr);
                }
                if (!stdout.isEmpty()) {
                    System.err.println("OGDF stdout: " + stdout);
                }
                return;
            }

            GraphLayoutResult layoutResult = parseGraphMlResponse(stdout);
            if (layoutResult.nodeLayouts.isEmpty()) {
                System.err.println("Error: Did not receive valid output from OGDF.");
                if (!stderr.isEmpty()) {
                    System.err.println("OGDF stderr: " + stderr);
                }
                String outTrimmed = stdout.trim();
                if (!outTrimmed.isEmpty()) {
                    System.err.println("OGDF stdout: " + outTrimmed);
                }
                return;
            }

            Map<String, Long> metrics = parseMetrics(stderr);
            String nonMetricStderr = extractNonMetricStderr(stderr);
            double[] layoutTransform = createLayoutTransform(layoutResult, nodeList, edgeList);

            int updatedNodeCount = 0;
            int missingNodeCount = 0;
            int updatedEdgeCount = 0;
            int missingEdgeCount = 0;
            boolean transactionStarted = false;
            try {
                // Group all coordinate/bend changes into one VANTED graph update.
                activeGraph.getListenerManager().transactionStarted(this);
                transactionStarted = true;

                for (int i = 0; i < nodeList.size(); i++) {
                    NodeLayoutData layoutData = layoutResult.nodeLayouts.get(i);
                    if (layoutData == null) {
                        missingNodeCount++;
                        continue;
                    }

                    Node targetNode = nodeList.get(i);
                    AttributeHelper.setPosition(targetNode,
                            transformX(layoutData.x, layoutTransform),
                            transformY(layoutData.y, layoutTransform));

                    Double width = layoutData.width;
                    Double height = layoutData.height;
                    if (width != null || height != null) {
                        double currentWidth = width != null ? width.doubleValue() : AttributeHelper.getWidth(targetNode);
                        double currentHeight = height != null ? height.doubleValue() : AttributeHelper.getHeight(targetNode);
                        if (currentWidth > 0 && currentHeight > 0) {
                            AttributeHelper.setSize(targetNode, currentWidth, currentHeight);
                        }
                    }

                    if (layoutData.label != null && !layoutData.label.isEmpty()) {
                        AttributeHelper.setLabel(targetNode, layoutData.label);
                    }
                    updatedNodeCount++;
                }

                if (!layoutResult.edgeLayouts.isEmpty()) {
                    String polylineShapeClass = findPolylineEdgeShapeClass();
                    for (int i = 0; i < edgeList.size(); i++) {
                        EdgeLayoutData edgeData = layoutResult.edgeLayouts.get(i);
                        if (edgeData == null) {
                            missingEdgeCount++;
                            continue;
                        }

                        Edge targetEdge = edgeList.get(i);
                        AttributeHelper.removeEdgeBends(targetEdge);
                        for (Vector2d bendPoint : edgeData.bends) {
                            AttributeHelper.addEdgeBend(targetEdge,
                                    transformX(bendPoint.x, layoutTransform),
                                    transformY(bendPoint.y, layoutTransform));
                        }
                        if (!edgeData.bends.isEmpty() && polylineShapeClass != null) {
                            AttributeHelper.setEdgeBendStyle(targetEdge, polylineShapeClass);
                        }
                        if (edgeData.label != null && !edgeData.label.isEmpty()) {
                            AttributeHelper.setLabel(targetEdge, edgeData.label);
                        }
                        updatedEdgeCount++;
                    }
                }

                applyMetricsToGraph(activeGraph, metrics);
            } finally {
                if (transactionStarted) {
                    activeGraph.getListenerManager().transactionFinished(this);
                }
            }

            if (missingNodeCount > 0) {
                System.err.println("Warning: OGDF response missed " + missingNodeCount + " node(s).");
            }
            if (missingEdgeCount > 0) {
                System.err.println("Warning: OGDF response missed " + missingEdgeCount + " edge(s).");
            }
            if (!metrics.isEmpty()) {
                System.out.println("OGDF metrics: " + formatMetrics(metrics));
                if (optionBoolean(layoutOptions, OPTION_INCLUDE_METRICS, false)) {
                    showMetricsDialog(metrics);
                }
            }
            if (!nonMetricStderr.isEmpty()) {
                System.err.println("OGDF stderr: " + nonMetricStderr);
            }

            System.out.println(optionString(layoutOptions, OPTION_LAYOUT_NAME, "OGDF") + " layout applied successfully (" + updatedNodeCount + "/" + nodeList.size()
                    + " nodes, " + updatedEdgeCount + "/" + edgeList.size() + " edges updated).");
        } catch (Exception ex) {
            System.err.println("Failed to run OGDF layout:");
            ex.printStackTrace();
        }
    }

    // Serializes VANTED graph data into OGDF-readable GraphML.
    private static String buildGraphMlRequest(Graph graph, List<Node> nodeList, List<Edge> edgeList,
            Map<Node, Integer> nodeToIndex, Object[] layoutOptions) {
        StringBuilder sb = new StringBuilder(Math.max(4096, nodeList.size() * 160 + edgeList.size() * 90));
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<graphml>\n");

        appendKey(sb, "k_ogdf_layout", "graph", KEY_LAYOUT_ID, "string");
        appendKey(sb, "k_ogdf_iterations", "graph", KEY_LAYOUT_ITERATIONS, "int");
        appendKey(sb, "k_ogdf_secondary_iterations", "graph", KEY_LAYOUT_SECONDARY_ITERATIONS, "int");
        appendKey(sb, "k_ogdf_page_ratio", "graph", KEY_LAYOUT_PAGE_RATIO, "double");
        appendKey(sb, "k_ogdf_transpose", "graph", KEY_LAYOUT_TRANSPOSE, "boolean");
        appendKey(sb, "k_ogdf_include_metrics", "graph", KEY_LAYOUT_INCLUDE_METRICS, "boolean");
        appendKey(sb, "k_nodeid", "node", KEY_NODE_ID, "int");
        appendKey(sb, "k_x", "node", KEY_X, "double");
        appendKey(sb, "k_y", "node", KEY_Y, "double");
        appendKey(sb, "k_width", "node", KEY_WIDTH, "double");
        appendKey(sb, "k_height", "node", KEY_HEIGHT, "double");
        appendKey(sb, "k_node_label", "node", KEY_NODE_LABEL, "string");
        appendKey(sb, "k_edgeid", "edge", KEY_EDGE_ID, "int");
        appendKey(sb, "k_edge_label", "edge", KEY_EDGE_LABEL, "string");
        appendKey(sb, "k_edge_bends", "edge", KEY_EDGE_BENDS, "string");

        sb.append("  <graph id=\"G\" edgedefault=\"")
                .append(graph.isDirected() ? "directed" : "undirected")
                .append("\">\n");
        appendData(sb, "k_ogdf_layout", optionString(layoutOptions, OPTION_LAYOUT_ID, LAYOUT_IDS[0]));
        appendData(sb, "k_ogdf_iterations", Integer.toString(optionInt(layoutOptions, OPTION_ITERATIONS, LAYOUT_DEFAULT_ITERATIONS[0])));
        appendData(sb, "k_ogdf_secondary_iterations", Integer.toString(optionInt(layoutOptions, OPTION_SECONDARY_ITERATIONS, LAYOUT_DEFAULT_SECONDARY_ITERATIONS[0])));
        appendData(sb, "k_ogdf_page_ratio", Double.toString(optionDouble(layoutOptions, OPTION_PAGE_RATIO, LAYOUT_DEFAULT_PAGE_RATIOS[0])));
        appendData(sb, "k_ogdf_transpose", Boolean.toString(optionBoolean(layoutOptions, OPTION_TRANSPOSE, LAYOUT_DEFAULT_TRANSPOSE[0])));
        appendData(sb, "k_ogdf_include_metrics", Boolean.toString(optionBoolean(layoutOptions, OPTION_INCLUDE_METRICS, false)));

        // nodeid is the stable VANTED mapping; OGDF may rewrite XML node IDs.
        for (Node node : nodeList) {
            int nodeId = nodeToIndex.get(node);
            sb.append("    <node id=\"n").append(nodeId).append("\">\n");
            appendData(sb, "k_nodeid", Integer.toString(nodeId));
            appendData(sb, "k_x", Double.toString(AttributeHelper.getPositionX(node)));
            appendData(sb, "k_y", Double.toString(AttributeHelper.getPositionY(node)));
            appendData(sb, "k_width", Double.toString(AttributeHelper.getWidth(node)));
            appendData(sb, "k_height", Double.toString(AttributeHelper.getHeight(node)));

            String label = AttributeHelper.getLabel(node, "");
            if (label != null && !label.isEmpty()) {
                appendData(sb, "k_node_label", label);
            }
            sb.append("    </node>\n");
        }

        // Edge IDs use edgeList order so returned bends can be mapped back.
        for (int i = 0; i < edgeList.size(); i++) {
            Edge edge = edgeList.get(i);
            int src = nodeToIndex.get(edge.getSource());
            int tgt = nodeToIndex.get(edge.getTarget());

            sb.append("    <edge id=\"e")
                    .append(i)
                    .append("\" source=\"n")
                    .append(src)
                    .append("\" target=\"n")
                    .append(tgt)
                    .append("\"");

            if (edge.isDirected() != graph.isDirected()) {
                sb.append(" directed=\"").append(edge.isDirected() ? "true" : "false").append("\"");
            }

            sb.append(">\n");
            appendData(sb, "k_edgeid", Integer.toString(i));
            String edgeLabel = AttributeHelper.getLabel(edge, "");
            if (edgeLabel != null && !edgeLabel.isEmpty()) {
                appendData(sb, "k_edge_label", edgeLabel);
            }
            String bends = serializeEdgeBends(AttributeHelper.getEdgeBends(edge));
            if (!bends.isEmpty()) {
                appendData(sb, "k_edge_bends", bends);
            }
            sb.append("    </edge>\n");
        }

        sb.append("  </graph>\n");
        sb.append("</graphml>\n");
        return sb.toString();
    }

    // Parses OGDF GraphML output into node and edge layout updates.
    private static GraphLayoutResult parseGraphMlResponse(String graphMl) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entities when parsing process output.
        setIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document;
        try (StringReader reader = new StringReader(graphMl)) {
            document = builder.parse(new InputSource(reader));
        }

        Map<Integer, NodeLayoutData> nodeResult = new HashMap<>();
        Map<Integer, EdgeLayoutData> edgeResult = new HashMap<>();
        Element root = document.getDocumentElement();
        if (root == null) {
            return new GraphLayoutResult(nodeResult, edgeResult);
        }
        String rootName = root.getNodeName();
        if (rootName == null || !rootName.toLowerCase().endsWith("graphml")) {
            return new GraphLayoutResult(nodeResult, edgeResult);
        }

        // GraphML data values reference key IDs; attr.name tells us their meaning.
        Map<String, String> keyToAttrName = new HashMap<>();
        NodeList keyElements = document.getElementsByTagName("key");
        for (int i = 0; i < keyElements.getLength(); i++) {
            Element keyElement = (Element) keyElements.item(i);
            String id = keyElement.getAttribute("id");
            if (id == null || id.isEmpty()) {
                continue;
            }
            String attrName = keyElement.getAttribute("attr.name");
            keyToAttrName.put(id, attrName == null || attrName.isEmpty() ? id : attrName);
        }

        NodeList nodeElements = document.getElementsByTagName("node");
        for (int i = 0; i < nodeElements.getLength(); i++) {
            Element nodeElement = (Element) nodeElements.item(i);
            Map<String, String> valuesByAttrName = readDataByAttrName(nodeElement, keyToAttrName);

            Integer nodeId = tryParseInt(valuesByAttrName.get(KEY_NODE_ID));
            if (nodeId == null) {
                nodeId = tryParseFlexibleInt(nodeElement.getAttribute("id"));
            }
            if (nodeId == null) {
                continue;
            }

            Double x = tryParseDouble(valuesByAttrName.get(KEY_X));
            Double y = tryParseDouble(valuesByAttrName.get(KEY_Y));
            if (x == null || y == null) {
                x = Double.valueOf(fallbackNodeX(nodeId.intValue()));
                y = Double.valueOf(fallbackNodeY(nodeId.intValue()));
            }

            Double width = tryParseDouble(valuesByAttrName.get(KEY_WIDTH));
            Double height = tryParseDouble(valuesByAttrName.get(KEY_HEIGHT));
            String label = valuesByAttrName.get(KEY_NODE_LABEL);
            if (label != null && label.isEmpty()) {
                label = null;
            }
            nodeResult.put(nodeId, new NodeLayoutData(x.doubleValue(), y.doubleValue(), width, height, label));
        }

        NodeList edgeElements = document.getElementsByTagName("edge");
        for (int i = 0; i < edgeElements.getLength(); i++) {
            Element edgeElement = (Element) edgeElements.item(i);
            Map<String, String> valuesByAttrName = readDataByAttrName(edgeElement, keyToAttrName);

            Integer edgeIndex = tryParseInt(valuesByAttrName.get(KEY_EDGE_ID));
            if (edgeIndex == null) {
                edgeIndex = tryParseFlexibleInt(edgeElement.getAttribute("id"));
            }
            if (edgeIndex == null) {
                edgeIndex = Integer.valueOf(i);
            }

            // Bends are written by OGDF as a space-separated "x y x y ..." list.
            String label = valuesByAttrName.get(KEY_EDGE_LABEL);
            if (label != null && label.isEmpty()) {
                label = null;
            }
            List<Vector2d> bends = parseEdgeBends(valuesByAttrName.get(KEY_EDGE_BENDS));
            edgeResult.put(edgeIndex, new EdgeLayoutData(bends, label));
        }

        return new GraphLayoutResult(nodeResult, edgeResult);
    }

    // Keeps OGDF's free coordinate system close to the previous VANTED graph position.
    private static double[] createLayoutTransform(GraphLayoutResult layoutResult, List<Node> nodeList, List<Edge> edgeList) {
        double[] layoutBounds = computeLayoutBounds(layoutResult);
        if (!hasBounds(layoutBounds)) {
            return new double[] {1.0, 0.0, 0.0};
        }

        double[] currentBounds = computeCurrentNodeBounds(nodeList);
        int nodeCount = Math.max(1, nodeList == null ? 1 : nodeList.size());
        double preferredSpread = Math.max(LAYOUT_MIN_SPREAD, Math.sqrt(nodeCount) * LAYOUT_NODE_SPACING);

        double targetCenterX;
        double targetCenterY;
        double targetWidth;
        double targetHeight;
        if (hasBounds(currentBounds)) {
            targetCenterX = boundsCenterX(currentBounds);
            targetCenterY = boundsCenterY(currentBounds);
            targetWidth = Math.max(boundsWidth(currentBounds), preferredSpread);
            targetHeight = Math.max(boundsHeight(currentBounds), preferredSpread);
        } else {
            targetWidth = preferredSpread;
            targetHeight = preferredSpread;
            targetCenterX = LAYOUT_FIT_MARGIN + targetWidth / 2.0;
            targetCenterY = LAYOUT_FIT_MARGIN + targetHeight / 2.0;
        }

        double layoutWidth = Math.max(boundsWidth(layoutBounds), 1.0);
        double layoutHeight = Math.max(boundsHeight(layoutBounds), 1.0);
        double scale = Math.min(targetWidth / layoutWidth, targetHeight / layoutHeight);
        if (!isFinite(scale) || scale <= 0.0) {
            scale = 1.0;
        }
        scale = Math.min(scale, LAYOUT_MAX_SCALE_UP);

        double dx = targetCenterX - boundsCenterX(layoutBounds) * scale;
        double dy = targetCenterY - boundsCenterY(layoutBounds) * scale;

        double minX = layoutBounds[0] * scale + dx;
        double minY = layoutBounds[1] * scale + dy;
        if (minX < LAYOUT_FIT_MARGIN) {
            dx += LAYOUT_FIT_MARGIN - minX;
        }
        if (minY < LAYOUT_FIT_MARGIN) {
            dy += LAYOUT_FIT_MARGIN - minY;
        }

        return new double[] {scale, dx, dy};
    }

    private static double transformX(double x, double[] transform) {
        return x * transform[0] + transform[1];
    }

    private static double transformY(double y, double[] transform) {
        return y * transform[0] + transform[2];
    }

    private static double[] computeCurrentNodeBounds(List<Node> nodeList) {
        double[] bounds = emptyBounds();
        if (nodeList == null) {
            return bounds;
        }
        for (Node node : nodeList) {
            double x = AttributeHelper.getPositionX(node);
            double y = AttributeHelper.getPositionY(node);
            if (!isFinite(x) || !isFinite(y)) {
                continue;
            }
            double halfWidth = Math.max(1.0, AttributeHelper.getWidth(node)) / 2.0;
            double halfHeight = Math.max(1.0, AttributeHelper.getHeight(node)) / 2.0;
            addBoundsBox(bounds, x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
        }
        return bounds;
    }

    private static double[] computeLayoutBounds(GraphLayoutResult layoutResult) {
        double[] bounds = emptyBounds();
        if (layoutResult == null) {
            return bounds;
        }
        for (NodeLayoutData layoutData : layoutResult.nodeLayouts.values()) {
            if (layoutData == null || !isFinite(layoutData.x) || !isFinite(layoutData.y)) {
                continue;
            }
            double halfWidth = Math.max(1.0, layoutData.width == null ? 1.0 : layoutData.width.doubleValue()) / 2.0;
            double halfHeight = Math.max(1.0, layoutData.height == null ? 1.0 : layoutData.height.doubleValue()) / 2.0;
            addBoundsBox(bounds, layoutData.x - halfWidth, layoutData.y - halfHeight,
                    layoutData.x + halfWidth, layoutData.y + halfHeight);
        }
        for (EdgeLayoutData edgeData : layoutResult.edgeLayouts.values()) {
            if (edgeData == null) {
                continue;
            }
            for (Vector2d bend : edgeData.bends) {
                if (bend != null) {
                    addBoundsPoint(bounds, bend.x, bend.y);
                }
            }
        }
        return bounds;
    }

    private static double[] emptyBounds() {
        return new double[] {
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };
    }

    private static void addBoundsBox(double[] bounds, double minX, double minY, double maxX, double maxY) {
        addBoundsPoint(bounds, minX, minY);
        addBoundsPoint(bounds, maxX, maxY);
    }

    private static void addBoundsPoint(double[] bounds, double x, double y) {
        if (!isFinite(x) || !isFinite(y)) {
            return;
        }
        bounds[0] = Math.min(bounds[0], x);
        bounds[1] = Math.min(bounds[1], y);
        bounds[2] = Math.max(bounds[2], x);
        bounds[3] = Math.max(bounds[3], y);
    }

    private static boolean hasBounds(double[] bounds) {
        return bounds != null && bounds.length >= 4
                && isFinite(bounds[0]) && isFinite(bounds[1])
                && isFinite(bounds[2]) && isFinite(bounds[3])
                && bounds[0] <= bounds[2] && bounds[1] <= bounds[3];
    }

    private static double boundsWidth(double[] bounds) {
        return bounds[2] - bounds[0];
    }

    private static double boundsHeight(double[] bounds) {
        return bounds[3] - bounds[1];
    }

    private static double boundsCenterX(double[] bounds) {
        return (bounds[0] + bounds[2]) / 2.0;
    }

    private static double boundsCenterY(double[] bounds) {
        return (bounds[1] + bounds[3]) / 2.0;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double fallbackNodeX(int nodeId) {
        int safeId = Math.max(0, nodeId);
        return (safeId % 10) * LAYOUT_NODE_SPACING;
    }

    private static double fallbackNodeY(int nodeId) {
        int safeId = Math.max(0, nodeId);
        return (safeId / 10) * LAYOUT_NODE_SPACING;
    }

    // Small helpers for GraphML values, metrics, and path parsing.
    private static Map<String, String> readDataByAttrName(Element element, Map<String, String> keyToAttrName) {
        Map<String, String> valuesByAttrName = new HashMap<>();
        NodeList dataElements = element.getElementsByTagName("data");
        for (int j = 0; j < dataElements.getLength(); j++) {
            Element dataElement = (Element) dataElements.item(j);
            String keyId = dataElement.getAttribute("key");
            if (keyId == null || keyId.isEmpty()) {
                continue;
            }
            String attrName = keyToAttrName.getOrDefault(keyId, keyId);
            valuesByAttrName.put(attrName, Objects.toString(dataElement.getTextContent(), ""));
        }
        return valuesByAttrName;
    }

    // Parses edge bend points from OGDF output string format "x1 y1 x2 y2 ..."
    private static List<Vector2d> parseEdgeBends(String bendsText) {
        List<Vector2d> bends = new ArrayList<>();
        if (bendsText == null) {
            return bends;
        }
        String trimmed = bendsText.trim();
        if (trimmed.isEmpty()) {
            return bends;
        }

        String[] tokens = trimmed.split("\\s+");
        for (int i = 0; i + 1 < tokens.length; i += 2) {
            Double x = tryParseDouble(tokens[i]);
            Double y = tryParseDouble(tokens[i + 1]);
            if (x != null && y != null) {
                bends.add(new Vector2d(x.doubleValue(), y.doubleValue()));
            }
        }
        return bends;
    }

    // Serializes edge bend points to string format for OGDF input.
    private static String serializeEdgeBends(List<Vector2d> bends) {
        if (bends == null || bends.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bends.size() * 24);
        for (Vector2d bend : bends) {
            if (bend == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Double.toString(bend.x)).append(' ').append(Double.toString(bend.y));
        }
        return sb.toString();
    }

    // Finds the edge shape class name containing "polyline" for bend rendering.
    private static String findPolylineEdgeShapeClass() {
        Map<String, String> edgeShapes = AttributeHelper.getEdgeShapes();
        if (edgeShapes == null || edgeShapes.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : edgeShapes.entrySet()) {
            String className = entry.getValue();
            if (className != null && className.toLowerCase().contains("polyline")) {
                return className;
            }
        }
        return null;
    }

    // Extracts metric values (crossings, bends, etc.) from OGDF stderr output.
    private static Map<String, Long> parseMetrics(String stderr) {
        Map<String, Long> result = new HashMap<>();
        if (stderr == null || stderr.isEmpty()) {
            return result;
        }
        String[] lines = stderr.split("\\R");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.startsWith(METRIC_PREFIX)) {
                continue;
            }
            String remainder = trimmed.substring(METRIC_PREFIX.length()).trim();
            if (remainder.isEmpty()) {
                continue;
            }
            String[] tokens = remainder.split("\\s+");
            for (String token : tokens) {
                int eqPos = token.indexOf('=');
                if (eqPos <= 0 || eqPos >= token.length() - 1) {
                    continue;
                }
                String key = token.substring(0, eqPos);
                Long value = tryParseLong(token.substring(eqPos + 1));
                if (!key.isEmpty() && value != null) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    // Filters out metric lines from stderr, returning only error/warning messages.
    private static String extractNonMetricStderr(String stderr) {
        if (stderr == null || stderr.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(stderr.length());
        String[] lines = stderr.split("\\R");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.startsWith(METRIC_PREFIX)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
        }
        return sb.toString().trim();
    }

    // Stores OGDF metrics as graph attributes for user access.
    private static void applyMetricsToGraph(Graph graph, Map<String, Long> metrics) {
        if (graph == null || metrics == null || metrics.isEmpty()) {
            return;
        }
        setGraphMetric(graph, metrics, "crossings");
        setGraphMetric(graph, metrics, "bends");
        setGraphMetric(graph, metrics, "nodeOverlaps");
        setGraphMetric(graph, metrics, "nodes");
        setGraphMetric(graph, metrics, "edges");
    }

    // Sets a single metric attribute on the graph under the "ogdf" namespace.
    private static void setGraphMetric(Graph graph, Map<String, Long> metrics, String key) {
        Long value = metrics.get(key);
        if (value == null) {
            return;
        }
        try {
            AttributeHelper.setAttribute(graph, "ogdf", key, Integer.valueOf(value.intValue()));
        } catch (RuntimeException ignored) {
            // Ignore metric mapping errors; layout itself is already complete.
        }
    }

    // Formats metrics map into human-readable string (e.g., "crossings=5, bends=2").
    private static String formatMetrics(Map<String, Long> metrics) {
        List<String> parts = new ArrayList<>();
        appendMetricPart(parts, metrics, "crossings");
        appendMetricPart(parts, metrics, "bends");
        appendMetricPart(parts, metrics, "nodeOverlaps");
        appendMetricPart(parts, metrics, "nodes");
        appendMetricPart(parts, metrics, "edges");
        if (parts.isEmpty()) {
            for (Map.Entry<String, Long> entry : metrics.entrySet()) {
                parts.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return String.join(", ", parts);
    }

    // Adds one metric if present.
    private static void appendMetricPart(List<String> parts, Map<String, Long> metrics, String key) {
        Long value = metrics.get(key);
        if (value != null) {
            parts.add(key + "=" + value);
        }
    }

    private static void showMetricsDialog(Map<String, Long> metrics) {
        if (!isUiAvailable() || metrics == null || metrics.isEmpty()) {
            return;
        }

        JTextArea textArea = new JTextArea(formatMetricsForCopy(metrics));
        textArea.setEditable(false);
        textArea.setColumns(34);
        textArea.setRows(8);
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        textArea.setCaretPosition(0);

        JOptionPane.showMessageDialog(null, textArea, "OGDF Metrics", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String formatMetricsForCopy(Map<String, Long> metrics) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("OGDF Metrics\n\n");
        appendMetricLine(sb, metrics, "crossings", "Crossings");
        appendMetricLine(sb, metrics, "bends", "Bends");
        appendMetricLine(sb, metrics, "nodeOverlaps", "Node overlaps");
        appendMetricLine(sb, metrics, "nodes", "Nodes");
        appendMetricLine(sb, metrics, "edges", "Edges");

        Set<String> known = new LinkedHashSet<>();
        known.add("crossings");
        known.add("bends");
        known.add("nodeOverlaps");
        known.add("nodes");
        known.add("edges");
        for (Map.Entry<String, Long> entry : metrics.entrySet()) {
            if (!known.contains(entry.getKey())) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static void appendMetricLine(StringBuilder sb, Map<String, Long> metrics, String key, String label) {
        Long value = metrics.get(key);
        if (value != null) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    // Safely parses an integer string, returning null on failure.
    private static Integer tryParseInt(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Parses IDs like "e99" or "node99" by reading trailing digits.
    private static Integer tryParseFlexibleInt(String value) {
        Integer parsed = tryParseInt(value);
        if (parsed != null) {
            return parsed;
        }
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int pos = trimmed.length() - 1;
        while (pos >= 0 && Character.isDigit(trimmed.charAt(pos))) {
            pos--;
        }
        if (pos == trimmed.length() - 1) {
            return null;
        }
        return tryParseInt(trimmed.substring(pos + 1));
    }

    // Safely parses a Double string, returning null on failure.
    private static Double tryParseDouble(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            Double parsed = Double.valueOf(trimmed);
            return isFinite(parsed.doubleValue()) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Safely parses a Long string, returning null on failure.
    private static Long tryParseLong(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Appends a GraphML key definition to the request builder.
    private static void appendKey(StringBuilder sb, String id, String attrFor, String attrName, String attrType) {
        sb.append("  <key id=\"")
                .append(id)
                .append("\" for=\"")
                .append(attrFor)
                .append("\" attr.name=\"")
                .append(attrName)
                .append("\" attr.type=\"")
                .append(attrType)
                .append("\"/>\n");
    }

    // Appends a GraphML data element to the request builder.
    private static void appendData(StringBuilder sb, String key, String value) {
        sb.append("      <data key=\"")
                .append(key)
                .append("\">")
                .append(escapeXml(value))
                .append("</data>\n");
    }

    // Escapes XML special characters for safe embedding.
    private static String escapeXml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
            case '&':
                escaped.append("&amp;");
                break;
            case '<':
                escaped.append("&lt;");
                break;
            case '>':
                escaped.append("&gt;");
                break;
            case '"':
                escaped.append("&quot;");
                break;
            case '\'':
                escaped.append("&apos;");
                break;
            default:
                escaped.append(c);
                break;
            }
        }
        return escaped.toString();
    }

    // Sets XML parser feature if supported, silently ignores otherwise.
    private static void setIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Keep defaults if this parser implementation does not support the feature.
        }
    }

    // Opens a small VANTED-style dialog for layout selection and common parameters.
    private static Object[] chooseLayoutOptions(Graph graph) {
        int selectedLayoutIndex = findLayoutIndex(PREFS.get(PREF_SELECTED_LAYOUT, LAYOUT_IDS[0]));
        if (!isUiAvailable()) {
            return rememberedLayoutOptions(selectedLayoutIndex);
        }

        JComboBox<String> algorithmBox = new JComboBox<>(LAYOUT_NAMES);
        algorithmBox.setSelectedIndex(selectedLayoutIndex);

        SpinnerNumberModel iterationsModel = new SpinnerNumberModel(
                readIntPreference(PREF_LAYOUT_ITERATIONS, LAYOUT_DEFAULT_ITERATIONS[selectedLayoutIndex]), 0, 1000000, 1);
        SpinnerNumberModel secondaryIterationsModel = new SpinnerNumberModel(
                readIntPreference(PREF_LAYOUT_SECONDARY_ITERATIONS, LAYOUT_DEFAULT_SECONDARY_ITERATIONS[selectedLayoutIndex]), 0, 1000000, 1);
        SpinnerNumberModel pageRatioModel = new SpinnerNumberModel(
                readDoublePreference(PREF_LAYOUT_PAGE_RATIO, LAYOUT_DEFAULT_PAGE_RATIOS[selectedLayoutIndex]), 0.05, 100.0, 0.05);
        JSpinner iterationsSpinner = new JSpinner(iterationsModel);
        JSpinner secondaryIterationsSpinner = new JSpinner(secondaryIterationsModel);
        JSpinner pageRatioSpinner = new JSpinner(pageRatioModel);
        JCheckBox transposeBox = new JCheckBox();
        transposeBox.setSelected(PREFS.getBoolean(PREF_LAYOUT_TRANSPOSE, LAYOUT_DEFAULT_TRANSPOSE[selectedLayoutIndex]));
        JCheckBox metricsBox = new JCheckBox();
        metricsBox.setSelected(readIncludeMetricsPreference(selectedLayoutIndex));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        addDialogRow(panel, gbc, 0, "Layout algorithm", algorithmBox);

        JLabel descriptionLabel = new JLabel();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(descriptionLabel, gbc);

        JLabel compatibilityLabel = new JLabel();
        gbc.gridy = 2;
        panel.add(compatibilityLabel, gbc);

        JLabel iterationsLabel = addDialogRow(panel, gbc, 3, "Iterations", iterationsSpinner);
        JLabel secondaryIterationsLabel = addDialogRow(panel, gbc, 4, "Secondary iterations", secondaryIterationsSpinner);
        JLabel pageRatioLabel = addDialogRow(panel, gbc, 5, "Page ratio", pageRatioSpinner);
        JLabel transposeLabel = addDialogRow(panel, gbc, 6, "Transpose step", transposeBox);
        addDialogRow(panel, gbc, 7, "Calculate metrics", metricsBox);

        JLabel note = new JLabel("<html>Only supported inputs are shown. Use 0 for an OGDF default where available.</html>");
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        panel.add(note, gbc);

        algorithmBox.addActionListener(event -> {
            int index = selectedLayoutIndex(algorithmBox);
            iterationsSpinner.setValue(Integer.valueOf(LAYOUT_DEFAULT_ITERATIONS[index]));
            secondaryIterationsSpinner.setValue(Integer.valueOf(LAYOUT_DEFAULT_SECONDARY_ITERATIONS[index]));
            pageRatioSpinner.setValue(Double.valueOf(LAYOUT_DEFAULT_PAGE_RATIOS[index]));
            transposeBox.setSelected(LAYOUT_DEFAULT_TRANSPOSE[index]);
            metricsBox.setSelected(false);
            updateLayoutDialog(index, graph, descriptionLabel, compatibilityLabel,
                    iterationsLabel, iterationsSpinner, secondaryIterationsLabel, secondaryIterationsSpinner,
                    pageRatioLabel, pageRatioSpinner, transposeLabel, transposeBox);
            resizeDialogToContent(panel);
        });
        updateLayoutDialog(selectedLayoutIndex, graph, descriptionLabel, compatibilityLabel,
                iterationsLabel, iterationsSpinner, secondaryIterationsLabel, secondaryIterationsSpinner,
                pageRatioLabel, pageRatioSpinner, transposeLabel, transposeBox);

        if (!showLayoutOptionsDialog(panel)) {
            return null;
        }

        Object[] options = createLayoutOptions(selectedLayoutIndex(algorithmBox),
                ((Number) iterationsSpinner.getValue()).intValue(),
                ((Number) secondaryIterationsSpinner.getValue()).intValue(),
                ((Number) pageRatioSpinner.getValue()).doubleValue(),
                transposeBox.isSelected(),
                metricsBox.isSelected());
        rememberLayoutOptions(options);
        return options;
    }

    private static boolean showLayoutOptionsDialog(JPanel contentPanel) {
        final boolean[] accepted = new boolean[] {false};
        JDialog dialog = new JDialog((Window) null, "OGDF Layout Parameters", java.awt.Dialog.ModalityType.APPLICATION_MODAL);

        JPanel rootPanel = new JPanel(new BorderLayout(0, 10));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        rootPanel.add(scrollPane, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(event -> {
            accepted[0] = true;
            dialog.dispose();
        });
        cancelButton.addActionListener(event -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(rootPanel);
        dialog.getRootPane().setDefaultButton(okButton);
        fitDialogToScreen(dialog);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return accepted[0];
    }

    private static void resizeDialogToContent(java.awt.Component content) {
        Window window = SwingUtilities.getWindowAncestor(content);
        if (window != null) {
            fitDialogToScreen(window);
        }
    }

    private static void fitDialogToScreen(Window window) {
        window.pack();
        Dimension size = window.getSize();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = Math.max(520, screen.width - 120);
        int maxHeight = Math.max(260, screen.height - 120);
        if (size.width > maxWidth || size.height > maxHeight) {
            window.setSize(Math.min(size.width, maxWidth), Math.min(size.height, maxHeight));
        }
        window.validate();
    }

    private static JLabel addDialogRow(JPanel panel, GridBagConstraints gbc, int row, String label, java.awt.Component editor) {
        JLabel labelComponent = new JLabel(label);
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(editor, gbc);
        return labelComponent;
    }

    private static void updateLayoutDialog(int layoutIndex, Graph graph, JLabel descriptionLabel, JLabel compatibilityLabel,
            JLabel iterationsLabel, java.awt.Component iterationsEditor,
            JLabel secondaryIterationsLabel, java.awt.Component secondaryIterationsEditor,
            JLabel pageRatioLabel, java.awt.Component pageRatioEditor,
            JLabel transposeLabel, java.awt.Component transposeEditor) {
        int safeIndex = safeLayoutIndex(layoutIndex);
        descriptionLabel.setText("<html><b>" + htmlEscape(LAYOUT_NAMES[safeIndex]) + "</b>: "
                + htmlEscape(LAYOUT_DESCRIPTIONS[safeIndex]) + "<br><b>Inputs:</b> "
                + htmlEscape(layoutInputSummary(safeIndex)) + "</html>");
        compatibilityLabel.setText("<html><b>Current graph:</b> "
                + htmlEscape(layoutCompatibilityMessage(safeIndex, graph)) + "</html>");

        iterationsLabel.setText(LAYOUT_ITERATION_LABELS[safeIndex] + " (0 = OGDF default)");
        secondaryIterationsLabel.setText(LAYOUT_SECONDARY_LABELS[safeIndex] + " (0 = OGDF default)");
        setDialogRowVisible(iterationsLabel, iterationsEditor, LAYOUT_USES_ITERATIONS[safeIndex]);
        setDialogRowVisible(secondaryIterationsLabel, secondaryIterationsEditor, LAYOUT_USES_SECONDARY_ITERATIONS[safeIndex]);
        setDialogRowVisible(pageRatioLabel, pageRatioEditor, LAYOUT_USES_PAGE_RATIO[safeIndex]);
        setDialogRowVisible(transposeLabel, transposeEditor, LAYOUT_USES_TRANSPOSE[safeIndex]);
    }

    private static void setDialogRowVisible(JLabel label, java.awt.Component editor, boolean visible) {
        label.setVisible(visible);
        editor.setVisible(visible);
    }

    private static String layoutInputSummary(int layoutIndex) {
        List<String> inputs = new ArrayList<>();
        if (LAYOUT_USES_ITERATIONS[layoutIndex]) {
            inputs.add(LAYOUT_ITERATION_LABELS[layoutIndex]);
        }
        if (LAYOUT_USES_SECONDARY_ITERATIONS[layoutIndex]) {
            inputs.add(LAYOUT_SECONDARY_LABELS[layoutIndex]);
        }
        if (LAYOUT_USES_PAGE_RATIO[layoutIndex]) {
            inputs.add("Page ratio");
        }
        if (LAYOUT_USES_TRANSPOSE[layoutIndex]) {
            inputs.add("Transpose step");
        }
        if (inputs.isEmpty()) {
            return "No layout-specific inputs; OGDF defaults are used.";
        }
        return String.join(", ", inputs);
    }

    private static String layoutCompatibilityMessage(int layoutIndex, Graph graph) {
        int[] info = graphShapeInfo(graph);
        int nodes = info[0];
        int edges = info[1];
        int components = info[2];
        boolean hasCycle = info[3] != 0;
        boolean connected = nodes == 0 || components <= 1;
        boolean tree = nodes > 0 && connected && !hasCycle && edges == nodes - 1;
        boolean forest = nodes == 0 || !hasCycle;
        String shape = nodes + " node(s), " + edges + " edge(s), " + components + " component(s)";

        String id = LAYOUT_IDS[safeLayoutIndex(layoutIndex)];
        if ("radial_tree".equals(id)) {
            return tree ? "Compatible. Radial Tree requires one connected tree. (" + shape + ")"
                    : "Fallback will be used. Radial Tree requires one connected tree. (" + shape + ")";
        }
        if ("tree".equals(id)) {
            return forest ? "Compatible. Tree works for forests and trees. (" + shape + ")"
                    : "Fallback will be used. Tree layout requires a forest, so cyclic graphs are not valid. (" + shape + ")";
        }
        if ("pivot_mds".equals(id)) {
            return connected ? "Compatible. Pivot MDS requires a connected graph. (" + shape + ")"
                    : "Fallback will be used. Pivot MDS requires a connected graph. (" + shape + ")";
        }
        if ("stress".equals(id) || "spring_kk".equals(id)) {
            return connected ? "Best fit. This layout works best on connected graphs. (" + shape + ")"
                    : "Allowed, but disconnected graphs may need coordinate fallback/normalization. (" + shape + ")";
        }
        return "Compatible with general graphs. (" + shape + ")";
    }

    private static int[] graphShapeInfo(Graph graph) {
        if (graph == null || graph.isEmpty()) {
            return new int[] {0, 0, 0, 0};
        }
        List<Node> nodes = new ArrayList<>(graph.getNodes());
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        Map<Node, Integer> nodeToIndex = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            nodeToIndex.put(nodes.get(i), Integer.valueOf(i));
        }

        int[] parent = new int[nodes.size()];
        int[] rank = new int[nodes.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }

        boolean hasCycle = false;
        for (Edge edge : edges) {
            Integer sourceIndex = nodeToIndex.get(edge.getSource());
            Integer targetIndex = nodeToIndex.get(edge.getTarget());
            if (sourceIndex == null || targetIndex == null) {
                continue;
            }
            if (sourceIndex.intValue() == targetIndex.intValue()
                    || !unionComponents(parent, rank, sourceIndex.intValue(), targetIndex.intValue())) {
                hasCycle = true;
            }
        }

        Set<Integer> components = new LinkedHashSet<>();
        for (int i = 0; i < parent.length; i++) {
            components.add(Integer.valueOf(findComponent(parent, i)));
        }
        return new int[] {nodes.size(), edges.size(), components.size(), hasCycle ? 1 : 0};
    }

    private static int findComponent(int[] parent, int index) {
        int root = index;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[index] != index) {
            int next = parent[index];
            parent[index] = root;
            index = next;
        }
        return root;
    }

    private static boolean unionComponents(int[] parent, int[] rank, int a, int b) {
        int rootA = findComponent(parent, a);
        int rootB = findComponent(parent, b);
        if (rootA == rootB) {
            return false;
        }
        if (rank[rootA] < rank[rootB]) {
            parent[rootA] = rootB;
        } else if (rank[rootA] > rank[rootB]) {
            parent[rootB] = rootA;
        } else {
            parent[rootB] = rootA;
            rank[rootA]++;
        }
        return true;
    }

    private static String htmlEscape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return escapeXml(text);
    }

    private static Object[] createLayoutOptions(int layoutIndex, int iterations, int secondaryIterations,
            double pageRatio, boolean transpose, boolean includeMetrics) {
        int safeIndex = safeLayoutIndex(layoutIndex);
        double safePageRatio = pageRatio > 0.0 ? pageRatio : LAYOUT_DEFAULT_PAGE_RATIOS[safeIndex];
        return new Object[] {
                LAYOUT_IDS[safeIndex],
                LAYOUT_NAMES[safeIndex],
                Integer.valueOf(Math.max(0, iterations)),
                Integer.valueOf(Math.max(0, secondaryIterations)),
                Double.valueOf(safePageRatio),
                Boolean.valueOf(transpose),
                Boolean.valueOf(includeMetrics)
        };
    }

    private static Object[] rememberedLayoutOptions(int layoutIndex) {
        int safeIndex = safeLayoutIndex(layoutIndex);
        return createLayoutOptions(safeIndex,
                readIntPreference(PREF_LAYOUT_ITERATIONS, LAYOUT_DEFAULT_ITERATIONS[safeIndex]),
                readIntPreference(PREF_LAYOUT_SECONDARY_ITERATIONS, LAYOUT_DEFAULT_SECONDARY_ITERATIONS[safeIndex]),
                readDoublePreference(PREF_LAYOUT_PAGE_RATIO, LAYOUT_DEFAULT_PAGE_RATIOS[safeIndex]),
                PREFS.getBoolean(PREF_LAYOUT_TRANSPOSE, LAYOUT_DEFAULT_TRANSPOSE[safeIndex]),
                readIncludeMetricsPreference(safeIndex));
    }

    private static void rememberLayoutOptions(Object[] options) {
        PREFS.put(PREF_SELECTED_LAYOUT, optionString(options, OPTION_LAYOUT_ID, LAYOUT_IDS[0]));
        PREFS.putInt(PREF_LAYOUT_ITERATIONS, optionInt(options, OPTION_ITERATIONS, LAYOUT_DEFAULT_ITERATIONS[0]));
        PREFS.putInt(PREF_LAYOUT_SECONDARY_ITERATIONS, optionInt(options, OPTION_SECONDARY_ITERATIONS, LAYOUT_DEFAULT_SECONDARY_ITERATIONS[0]));
        PREFS.putDouble(PREF_LAYOUT_PAGE_RATIO, optionDouble(options, OPTION_PAGE_RATIO, LAYOUT_DEFAULT_PAGE_RATIOS[0]));
        PREFS.putBoolean(PREF_LAYOUT_TRANSPOSE, optionBoolean(options, OPTION_TRANSPOSE, LAYOUT_DEFAULT_TRANSPOSE[0]));
        PREFS.putBoolean(PREF_LAYOUT_INCLUDE_METRICS, optionBoolean(options, OPTION_INCLUDE_METRICS, false));
    }

    private static int selectedLayoutIndex(JComboBox<String> box) {
        return safeLayoutIndex(box.getSelectedIndex());
    }

    private static int findLayoutIndex(String id) {
        if (id != null) {
            String cleanedId = id.trim();
            for (int i = 0; i < LAYOUT_IDS.length; i++) {
                if (LAYOUT_IDS[i].equalsIgnoreCase(cleanedId)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static int safeLayoutIndex(int index) {
        return index >= 0 && index < LAYOUT_IDS.length ? index : 0;
    }

    private static boolean readIncludeMetricsPreference(int layoutIndex) {
        int safeIndex = safeLayoutIndex(layoutIndex);
        if ("fmmm".equals(LAYOUT_IDS[safeIndex])) {
            return false;
        }
        return PREFS.getBoolean(PREF_LAYOUT_INCLUDE_METRICS, false);
    }

    private static String optionString(Object[] options, int index, String fallback) {
        Object value = optionValue(options, index);
        return value instanceof String && !((String) value).isEmpty() ? (String) value : fallback;
    }

    private static int optionInt(Object[] options, int index, int fallback) {
        Object value = optionValue(options, index);
        return value instanceof Number ? Math.max(0, ((Number) value).intValue()) : fallback;
    }

    private static double optionDouble(Object[] options, int index, double fallback) {
        Object value = optionValue(options, index);
        return value instanceof Number && ((Number) value).doubleValue() > 0.0 ? ((Number) value).doubleValue() : fallback;
    }

    private static boolean optionBoolean(Object[] options, int index, boolean fallback) {
        Object value = optionValue(options, index);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static Object optionValue(Object[] options, int index) {
        return options != null && index >= 0 && index < options.length ? options[index] : null;
    }

    private static int readIntPreference(String key, int defaultValue) {
        try {
            return PREFS.getInt(key, defaultValue);
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private static double readDoublePreference(String key, double defaultValue) {
        try {
            return PREFS.getDouble(key, defaultValue);
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    // Locates OGDF executable and native runtime libraries from several sources.
    static RuntimeConfig resolveRuntimeConfig() {
        File addonDir = resolveAddonDirectory();
        File ogdfDir = firstDirectoryFile(
                System.getProperty(PROP_OGDF_DIR),
                System.getenv(ENV_OGDF_DIR),
                PREFS.get(PREF_OGDF_DIR, null));

        List<String> executableCandidates = new ArrayList<>();
        addCandidate(executableCandidates, System.getProperty(PROP_LAYOUT_EXE));
        addCandidate(executableCandidates, System.getenv(ENV_LAYOUT_EXE));
        addRememberedExecutableCandidate(executableCandidates, PREFS.get(PREF_LAYOUT_EXE, null));
        executableCandidates.addAll(addonExecutableCandidates(addonDir));
        executableCandidates.addAll(ogdfExecutableCandidates(ogdfDir));

        String executablePath = firstExecutable(executableCandidates);

        String runtimeDirectory = firstDirectory(
                System.getProperty(PROP_RUNTIME_DIR),
                System.getProperty(PROP_LIB_DIR),
                System.getenv(ENV_RUNTIME_DIR),
                System.getenv(ENV_LIB_DIR),
                PREFS.get(PREF_RUNTIME_DIR, null),
                inferRuntimeDirectory(executablePath),
                ogdfDir == null ? null : ogdfDir.getAbsolutePath(),
                addonDir == null ? null : addonDir.getAbsolutePath());

        return new RuntimeConfig(executablePath, runtimeDirectory);
    }

    // Prompts once for the OGDF directory and searches known CMake output paths.
    static RuntimeConfig chooseOgdfDirectoryAndRemember(RuntimeConfig current) {
        if (!isUiAvailable()) {
            return current;
        }
        File initialDirectory = resolveAddonDirectory();
        if (initialDirectory == null || !initialDirectory.isDirectory()) {
            initialDirectory = new File(System.getProperty("user.home", "."));
        }

        JFileChooser chooser = new JFileChooser(initialDirectory);
        chooser.setDialogTitle("Select OGDF directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) {
            return current;
        }

        File selected = chooser.getSelectedFile();
        if (selected == null || !selected.isDirectory()) {
            return current;
        }

        String executablePath = firstExecutable(ogdfExecutableCandidates(selected));
        if (executablePath == null) {
            JOptionPane.showMessageDialog(null,
                    "No GraphML-compatible OGDF bridge executable was found below:\n" + selected.getAbsolutePath()
                            + "\n\nExpected one of: " + executableNameSummary()
                            + "\n\nNote: old ogdf_layout executables use the previous text protocol and are ignored.",
                    "OGDF Bridge Not Found", JOptionPane.ERROR_MESSAGE);
            return current;
        }

        PREFS.put(PREF_OGDF_DIR, selected.getAbsolutePath());
        PREFS.put(PREF_LAYOUT_EXE, executablePath);
        String runtimeDirectory = inferRuntimeDirectory(executablePath);
        if (!isDirectory(runtimeDirectory)) {
            runtimeDirectory = current.runtimeDirectory;
        }
        if (isDirectory(runtimeDirectory)) {
            PREFS.put(PREF_RUNTIME_DIR, runtimeDirectory);
        }
        return new RuntimeConfig(executablePath, runtimeDirectory);
    }

    // Starts OGDF with an environment that can find native libraries.
    static Process startOgdfProcess(RuntimeConfig runtime) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(runtime.executablePath);
        configureProcessEnvironment(pb, runtime);
        try {
            return pb.start();
        } catch (IOException firstStartError) {
            RuntimeConfig retry = maybeChooseRuntimeDirectory(runtime, firstStartError);
            if (retry == null) {
                throw firstStartError;
            }
            ProcessBuilder retryPb = new ProcessBuilder(retry.executablePath);
            configureProcessEnvironment(retryPb, retry);
            return retryPb.start();
        }
    }

    // Prompts for runtime directory when native libraries cannot be loaded.
    private static RuntimeConfig maybeChooseRuntimeDirectory(RuntimeConfig current, IOException startError) {
        if (!isUiAvailable()) {
            return null;
        }
        if (!isLikelyMissingRuntimeLibrary(startError)) {
            return null;
        }

        File initialDirectory = resolveAddonDirectory();
        if (initialDirectory == null || !initialDirectory.isDirectory()) {
            initialDirectory = new File(System.getProperty("user.home", "."));
        }

        String message = "OGDF executable was found, but required OGDF runtime libraries could not be loaded.\n"
                + "Please select the folder containing OGDF runtime files (DLL, .so, or .dylib files).";
        JOptionPane.showMessageDialog(null, message, "Select OGDF Runtime Folder", JOptionPane.INFORMATION_MESSAGE);

        JFileChooser chooser = new JFileChooser(initialDirectory);
        chooser.setDialogTitle("Select OGDF runtime directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File selected = chooser.getSelectedFile();
        if (selected == null || !selected.isDirectory()) {
            return null;
        }
        String runtimeDirectory = selected.getAbsolutePath();
        PREFS.put(PREF_RUNTIME_DIR, runtimeDirectory);
        return new RuntimeConfig(current.executablePath, runtimeDirectory);
    }

    // Configures process environment variables (PATH, LD_LIBRARY_PATH, etc.).
    private static void configureProcessEnvironment(ProcessBuilder pb, RuntimeConfig runtime) {
        Map<String, String> env = pb.environment();
        Set<String> entries = new LinkedHashSet<>();
        String exeParent = parentDirectory(runtime.executablePath);
        if (isDirectory(exeParent)) {
            entries.add(exeParent);
        }
        if (isDirectory(runtime.runtimeDirectory)) {
            entries.add(runtime.runtimeDirectory);
        }
        prependToEnvironmentPath(env, "PATH", entries);
        if (isLinux()) {
            prependToEnvironmentPath(env, "LD_LIBRARY_PATH", entries);
        }
        if (isMac()) {
            prependToEnvironmentPath(env, "DYLD_LIBRARY_PATH", entries);
            prependToEnvironmentPath(env, "DYLD_FALLBACK_LIBRARY_PATH", entries);
        }
    }

    // Prepends directories to a PATH-like environment variable.
    private static void prependToEnvironmentPath(Map<String, String> env, String variableName, Set<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        String envKey = resolveEnvironmentVariableName(env, variableName);
        String currentPath = env.get(envKey);
        StringBuilder merged = new StringBuilder();
        for (String entry : entries) {
            if (!isDirectory(entry)) {
                continue;
            }
            if (merged.length() > 0) {
                merged.append(File.pathSeparatorChar);
            }
            merged.append(entry);
        }
        if (merged.length() == 0) {
            return;
        }
        if (currentPath != null && !currentPath.trim().isEmpty()) {
            merged.append(File.pathSeparatorChar).append(currentPath);
        }
        env.put(envKey, merged.toString());
    }

    // Resolves variable names case-insensitively for Windows PATH vs Path.
    private static String resolveEnvironmentVariableName(Map<String, String> env, String variableName) {
        for (String key : env.keySet()) {
            if (variableName.equalsIgnoreCase(key)) {
                return key;
            }
        }
        return variableName;
    }

    // Lists potential OGDF executable paths in the addon directory.
    private static List<String> addonExecutableCandidates(File addonDir) {
        List<String> candidates = new ArrayList<>();
        if (addonDir == null || !addonDir.isDirectory()) {
            return candidates;
        }
        for (String executableName : platformExecutableNames()) {
            candidates.add(new File(addonDir, executableName).getAbsolutePath());
        }
        return candidates;
    }

    // Lists bridge executable paths below an OGDF source/build directory.
    private static List<String> ogdfExecutableCandidates(File ogdfDir) {
        List<String> candidates = new ArrayList<>();
        if (ogdfDir == null || !ogdfDir.isDirectory()) {
            return candidates;
        }
        for (File dir : knownOgdfSearchDirectories(ogdfDir)) {
            addExecutableCandidatesIn(candidates, dir);
        }
        addRecursiveExecutableCandidates(candidates, ogdfDir, 5, new int[] { 0 });
        return candidates;
    }

    // Common places where CMake generators put executable targets.
    private static List<File> knownOgdfSearchDirectories(File ogdfDir) {
        Set<String> relativePaths = new LinkedHashSet<>();
        relativePaths.add("");
        relativePaths.add("build");
        relativePaths.add("build/bin");
        relativePaths.add("bin");
        relativePaths.add("Debug");
        relativePaths.add("Release");
        relativePaths.add("RelWithDebInfo");
        relativePaths.add("MinSizeRel");
        relativePaths.add("build/Debug");
        relativePaths.add("build/Release");
        relativePaths.add("build/RelWithDebInfo");
        relativePaths.add("build/MinSizeRel");
        relativePaths.add("build/bin/Debug");
        relativePaths.add("build/bin/Release");
        relativePaths.add("build/bin/RelWithDebInfo");
        relativePaths.add("build/bin/MinSizeRel");
        relativePaths.add("ogdf/build");
        relativePaths.add("ogdf/build/bin");
        relativePaths.add("ogdf/build/Debug");
        relativePaths.add("ogdf/build/Release");
        relativePaths.add("ogdf/build/RelWithDebInfo");
        relativePaths.add("ogdf/build/MinSizeRel");

        List<File> dirs = new ArrayList<>();
        for (String relativePath : relativePaths) {
            File dir = relativePath.isEmpty() ? ogdfDir : new File(ogdfDir, relativePath);
            if (dir.isDirectory()) {
                dirs.add(dir);
            }
        }
        return dirs;
    }

    // Adds all supported executable names from one directory.
    private static void addExecutableCandidatesIn(List<String> candidates, File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        for (String executableName : platformExecutableNames()) {
            candidates.add(new File(dir, executableName).getAbsolutePath());
        }
    }

    // Fallback for CMake generators with a less common output folder.
    private static void addRecursiveExecutableCandidates(List<String> candidates, File dir, int depth, int[] visitedDirs) {
        if (dir == null || !dir.isDirectory() || depth < 0 || visitedDirs[0] > 500) {
            return;
        }
        visitedDirs[0]++;
        addExecutableCandidatesIn(candidates, dir);

        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory() && !child.isHidden()) {
                addRecursiveExecutableCandidates(candidates, child, depth - 1, visitedDirs);
            }
        }
    }

    // Returns platform-specific executable name candidates.
    private static List<String> platformExecutableNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String baseName : LAYOUT_EXECUTABLE_BASE_NAMES) {
            if (isWindows()) {
                names.add(baseName + ".exe");
                names.add(baseName);
            } else {
                names.add(baseName);
                names.add(baseName + ".exe");
            }
        }
        return new ArrayList<>(names);
    }

    // Short display of names the directory scan understands.
    private static String executableNameSummary() {
        return String.join(", ", platformExecutableNames());
    }

    // Adds a candidate to the list if non-null and non-empty.
    private static void addCandidate(List<String> candidates, String value) {
        if (value != null && !value.trim().isEmpty()) {
            candidates.add(value);
        }
    }

    // Reuses only saved bridge paths with names that belong to the GraphML protocol.
    private static void addRememberedExecutableCandidate(List<String> candidates, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        if (hasRecognizedExecutableName(value)) {
            candidates.add(value);
        } else {
            PREFS.remove(PREF_LAYOUT_EXE);
        }
    }

    // Prevents auto-loading older text-protocol executables such as ogdf_layout.exe.
    private static boolean hasRecognizedExecutableName(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        String fileName = new File(path.trim()).getName();
        for (String executableName : platformExecutableNames()) {
            if (fileName.equalsIgnoreCase(executableName)) {
                return true;
            }
        }
        return false;
    }

    // Returns the first executable path from the candidates list.
    private static String firstExecutable(List<String> candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (isExecutable(candidate)) {
                return normalizePath(candidate);
            }
        }
        return null;
    }

    // Returns the first valid directory path from the candidates.
    private static String firstDirectory(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (isDirectory(candidate)) {
                return normalizePath(candidate);
            }
        }
        return null;
    }

    // Returns the first valid directory as a File.
    private static File firstDirectoryFile(String... candidates) {
        String path = firstDirectory(candidates);
        return path == null ? null : new File(path);
    }

    // Extracts parent directory path from a file path.
    private static String parentDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path.trim());
        File parent = file.getParentFile();
        return parent == null ? null : parent.getAbsolutePath();
    }

    // Infers runtime directory as the parent of the executable.
    private static String inferRuntimeDirectory(String executablePath) {
        String parent = parentDirectory(executablePath);
        if (isDirectory(parent)) {
            return parent;
        }
        return null;
    }

    // Resolves the directory containing this plugin's JAR/class files.
    private static File resolveAddonDirectory() {
        try {
            URI location = OgdfLayoutAlgorithm.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File source = new File(location);
            if (source.isFile()) {
                return source.getParentFile();
            }
            if (source.isDirectory()) {
                return source;
            }
        } catch (Exception ignored) {
            // Ignore and fall back to other path sources.
        }
        return null;
    }

    // Normalizes path to absolute form.
    private static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        return new File(path).getAbsolutePath();
    }

    // Platform and filesystem checks.
    private static boolean isUiAvailable() {
        return !GraphicsEnvironment.isHeadless();
    }

    // Checks if running on Windows.
    private static boolean isWindows() {
        return osName().contains("win");
    }

    // Checks if running on Linux or a Unix-like OS.
    private static boolean isLinux() {
        String os = osName();
        return os.contains("linux") || os.contains("nix") || os.contains("nux") || os.contains("aix");
    }

    // Checks if running on macOS.
    private static boolean isMac() {
        String os = osName();
        return os.contains("mac") || os.contains("darwin");
    }

    // Gets lowercase OS name for platform detection.
    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase();
    }

    // Detects if startup failed because a shared library is missing.
    private static boolean isLikelyMissingRuntimeLibrary(IOException error) {
        if (error == null || error.getMessage() == null) {
            return false;
        }
        String msg = error.getMessage().toLowerCase();
        return msg.contains("error=126")
                || msg.contains("module could not be found")
                || msg.contains(".dll")
                || msg.contains(".so")
                || msg.contains(".dylib")
                || msg.contains("shared libraries")
                || msg.contains("library not loaded")
                || msg.contains("cannot find the file specified");
    }

    // Checks if path points to an executable file.
    private static boolean isExecutable(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        File file = new File(path.trim());
        return file.isFile() && file.canExecute();
    }

    // Checks if path points to a directory.
    private static boolean isDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        return new File(path.trim()).isDirectory();
    }

    // Drains process output while OGDF is still running, preventing large GraphML responses from blocking the process.
    static Thread startStreamReader(InputStream in, ByteArrayOutputStream buffer, String threadName) {
        Thread thread = new Thread(() -> {
            try {
                copyAll(in, buffer);
            } catch (IOException ex) {
                System.err.println(threadName + " failed: " + ex.getMessage());
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void copyAll(InputStream in, ByteArrayOutputStream buffer) throws IOException {
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
    }

    static String bufferToString(ByteArrayOutputStream buffer) throws IOException {
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    // Simple data holders for runtime config and parsed layout results.
    static final class RuntimeConfig {
        final String executablePath;
        final String runtimeDirectory;

        private RuntimeConfig(String executablePath, String runtimeDirectory) {
            this.executablePath = executablePath;
            this.runtimeDirectory = runtimeDirectory;
        }
    }

    private static final class NodeLayoutData {
        private final double x;
        private final double y;
        private final Double width;
        private final Double height;
        private final String label;

        private NodeLayoutData(double x, double y, Double width, Double height, String label) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
        }
    }

    private static final class EdgeLayoutData {
        private final List<Vector2d> bends;
        private final String label;

        private EdgeLayoutData(List<Vector2d> bends, String label) {
            this.bends = bends == null ? Collections.emptyList() : bends;
            this.label = label;
        }
    }

    private static final class GraphLayoutResult {
        private final Map<Integer, NodeLayoutData> nodeLayouts;
        private final Map<Integer, EdgeLayoutData> edgeLayouts;

        private GraphLayoutResult(Map<Integer, NodeLayoutData> nodeLayouts, Map<Integer, EdgeLayoutData> edgeLayouts) {
            this.nodeLayouts = nodeLayouts == null ? Collections.emptyMap() : nodeLayouts;
            this.edgeLayouts = edgeLayouts == null ? Collections.emptyMap() : edgeLayouts;
        }
    }
}
