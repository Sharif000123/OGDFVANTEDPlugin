package ogdf.integration;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JViewport;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import org.AttributeHelper;
import org.Vector2d;
import org.graffiti.editor.MainFrame;
import org.graffiti.graph.AdjListGraph;
import org.graffiti.graph.Edge;
import org.graffiti.graph.Graph;
import org.graffiti.graph.Node;
import org.graffiti.plugin.algorithm.AbstractAlgorithm;
import org.graffiti.plugin.algorithm.Category;
import org.graffiti.plugin.view.View;
import org.graffiti.session.Session;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import de.ipk_gatersleben.ag_nw.graffiti.GraphHelper;

public class OgdfGraphGeneratorAlgorithm extends AbstractAlgorithm {

    private static final Preferences PREFS = Preferences.userNodeForPackage(OgdfGraphGeneratorAlgorithm.class);

    private static final String[] GENERATOR_IDS = {
            "random_simple_probability",
            "random_simple_edges",
            "random_connected",
            "random_tree",
            "regular_tree",
            "planar_connected",
            "complete",
            "grid"
    };
    private static final String[] GENERATOR_NAMES = {
            "Random simple graph G(n, p)",
            "Random simple graph G(n, m)",
            "Random connected graph",
            "Random tree",
            "Regular tree",
            "Random planar connected graph",
            "Complete graph",
            "Grid graph"
    };
    private static final String[] GENERATOR_DESCRIPTIONS = {
            "Creates a simple graph by adding each possible edge with probability p.",
            "Creates a simple graph with n nodes and m random edges.",
            "Creates a connected simple graph with n nodes and at least n - 1 edges.",
            "Creates a random tree with n nodes.",
            "Creates a rooted tree with a fixed number of children per node.",
            "Creates a connected planar graph. OGDF may adjust infeasible edge counts.",
            "Creates the complete graph K_n.",
            "Creates a rectangular rows x columns grid."
    };
    private static final int[] DEFAULT_NODES = {
            12, 12, 12, 12, 15, 12, 8, 4
    };
    private static final int[] DEFAULT_EDGES = {
            16, 16, 16, 11, 14, 18, 0, 0
    };
    private static final int[] DEFAULT_SECONDARY = {
            0, 0, 0, 0, 3, 0, 0, 5
    };
    private static final double[] DEFAULT_PROBABILITY = {
            0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25
    };
    private static final boolean[] USES_EDGES = {
            false, true, true, false, false, true, false, false
    };
    private static final boolean[] USES_PROBABILITY = {
            true, false, false, false, false, false, false, false
    };
    private static final boolean[] USES_SECONDARY = {
            false, false, false, false, true, false, false, true
    };
    private static final String[] NODE_LABELS = {
            "Number of nodes",
            "Number of nodes",
            "Number of nodes",
            "Number of nodes",
            "Number of nodes",
            "Number of nodes",
            "Number of nodes",
            "Rows"
    };
    private static final String[] SECONDARY_LABELS = {
            "Secondary value",
            "Secondary value",
            "Secondary value",
            "Secondary value",
            "Children per node",
            "Secondary value",
            "Secondary value",
            "Columns"
    };

    private static final int OPTION_GENERATOR = 0;
    private static final int OPTION_NODES = 1;
    private static final int OPTION_EDGES = 2;
    private static final int OPTION_PROBABILITY = 3;
    private static final int OPTION_SECONDARY = 4;
    private static final int OPTION_DIRECTED = 5;
    private static final int OPTION_LABELS = 6;

    private static final String PREF_GENERATOR = "generator";
    private static final String PREF_NODES = "nodes";
    private static final String PREF_EDGES = "edges";
    private static final String PREF_PROBABILITY = "probability";
    private static final String PREF_SECONDARY = "secondary";
    private static final String PREF_DIRECTED = "directed";
    private static final String PREF_LABELS = "labels";
    private static final double GENERATED_LAYOUT_MIN_MARGIN = 30.0;
    private static final double GENERATED_LAYOUT_MAX_MARGIN = 80.0;
    private static final double GENERATED_LAYOUT_MARGIN_FRACTION = 0.08;
    private static final double GENERATED_LAYOUT_MIN_SIDE_USAGE = 0.70;
    private static final double GENERATED_LAYOUT_NODE_SPACING = 90.0;
    private static final int GENERATED_LAYOUT_FALLBACK_VIEW_WIDTH = 820;
    private static final int GENERATED_LAYOUT_FALLBACK_VIEW_HEIGHT = 620;

    @Override
    public String getName() {
        return "Generate OGDF Graph";
    }

    @Override
    public String getCategory() {
        return "File.New.OGDF Graph";
    }

    @Override
    public Set<Category> getSetCategory() {
        return new HashSet<>(Arrays.asList(Category.GRAPH, Category.COMPUTATION));
    }

    @Override
    public boolean isAlwaysExecutable() {
        return true;
    }

    @Override
    public void execute() {
        Object[] options = chooseGeneratorOptions();
        if (options == null) {
            return;
        }

        Thread worker = new Thread(() -> runGenerator(options), "OGDF graph generator");
        worker.setDaemon(true);
        worker.start();
    }

    private void runGenerator(Object[] options) {
        try {
            OgdfLayoutAlgorithm.RuntimeConfig runtime = OgdfLayoutAlgorithm.resolveRuntimeConfig();
            if (runtime.executablePath == null) {
                runtime = OgdfLayoutAlgorithm.chooseOgdfDirectoryAndRemember(runtime);
            }
            if (runtime.executablePath == null) {
                System.err.println("OGDF bridge executable not found for graph generation.");
                return;
            }

            String request = buildGeneratorRequest(options);
            Process process = OgdfLayoutAlgorithm.startOgdfProcess(runtime);
            ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            Thread stdoutReader = OgdfLayoutAlgorithm.startStreamReader(process.getInputStream(), stdoutBuffer, "OGDF generator stdout reader");
            Thread stderrReader = OgdfLayoutAlgorithm.startStreamReader(process.getErrorStream(), stderrBuffer, "OGDF generator stderr reader");

            try (OutputStream output = process.getOutputStream()) {
                output.write(request.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            int exitCode = process.waitFor();
            stdoutReader.join();
            stderrReader.join();
            String stdout = OgdfLayoutAlgorithm.bufferToString(stdoutBuffer);
            String stderr = OgdfLayoutAlgorithm.bufferToString(stderrBuffer).trim();

            if (exitCode != 0) {
                System.err.println("OGDF graph generation failed with exit code " + exitCode + " (" + runtime.executablePath + ").");
                if (!stderr.isEmpty()) {
                    System.err.println("OGDF stderr: " + stderr);
                }
                return;
            }
            if (!stderr.isEmpty()) {
                System.err.println("OGDF stderr: " + stderr);
            }

            String generatorId = optionString(options, OPTION_GENERATOR, GENERATOR_IDS[0]);
            Graph generatedGraph = parseGeneratedGraph(stdout, optionBoolean(options, OPTION_DIRECTED, false));
            generatedGraph.setName("OGDF " + GENERATOR_NAMES[findGeneratorIndex(generatorId)]);
            SwingUtilities.invokeLater(() -> showGeneratedGraph(generatedGraph));
        } catch (Exception ex) {
            System.err.println("Failed to generate OGDF graph:");
            ex.printStackTrace();
        }
    }

    private void showGeneratedGraph(Graph generatedGraph) {
        if (generatedGraph == null || generatedGraph.isEmpty()) {
            JOptionPane.showMessageDialog(null, "OGDF did not return a graph.", "OGDF Graph Generation", JOptionPane.ERROR_MESSAGE);
            return;
        }

        normalizeGeneratedGraph(generatedGraph, resolveGraphViewportSize(null));
        MainFrame.getInstance().showGraph(generatedGraph, new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getName()));
        SwingUtilities.invokeLater(() -> {
            normalizeGeneratedGraph(generatedGraph, resolveGraphViewportSize(generatedGraph));
            GraphHelper.issueCompleteRedrawForActiveView();
            System.out.println("OGDF graph generated successfully (" + generatedGraph.getNumberOfNodes()
                    + " nodes, " + generatedGraph.getNumberOfEdges() + " edges).");
        });
    }

    private static Object[] chooseGeneratorOptions() {
        int selectedGenerator = findGeneratorIndex(PREFS.get(PREF_GENERATOR, GENERATOR_IDS[0]));
        if (!isUiAvailable()) {
            return rememberedGeneratorOptions(selectedGenerator);
        }

        JComboBox<String> generatorBox = new JComboBox<>(GENERATOR_NAMES);
        generatorBox.setSelectedIndex(selectedGenerator);
        JSpinner nodesSpinner = new JSpinner(new SpinnerNumberModel(
                readIntPreference(PREF_NODES, DEFAULT_NODES[selectedGenerator]), 1, 100000, 1));
        JSpinner edgesSpinner = new JSpinner(new SpinnerNumberModel(
                readIntPreference(PREF_EDGES, DEFAULT_EDGES[selectedGenerator]), 0, 10000000, 1));
        JSpinner probabilitySpinner = new JSpinner(new SpinnerNumberModel(
                readDoublePreference(PREF_PROBABILITY, DEFAULT_PROBABILITY[selectedGenerator]), 0.0, 1.0, 0.05));
        JSpinner secondarySpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0, readIntPreference(PREF_SECONDARY, DEFAULT_SECONDARY[selectedGenerator])), 0, 100000, 1));
        JCheckBox directedBox = new JCheckBox();
        directedBox.setSelected(PREFS.getBoolean(PREF_DIRECTED, false));
        JCheckBox labelsBox = new JCheckBox();
        labelsBox.setSelected(PREFS.getBoolean(PREF_LABELS, true));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        addDialogRow(panel, gbc, 0, "Generator", generatorBox);
        JLabel descriptionLabel = new JLabel();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        panel.add(descriptionLabel, gbc);

        JLabel nodesLabel = addDialogRow(panel, gbc, 2, NODE_LABELS[selectedGenerator], nodesSpinner);
        JLabel edgesLabel = addDialogRow(panel, gbc, 3, "Number of edges", edgesSpinner);
        JLabel probabilityLabel = addDialogRow(panel, gbc, 4, "Edge probability", probabilitySpinner);
        JLabel secondaryLabel = addDialogRow(panel, gbc, 5, SECONDARY_LABELS[selectedGenerator], secondarySpinner);
        addDialogRow(panel, gbc, 6, "Create directed graph", directedBox);
        addDialogRow(panel, gbc, 7, "Add node labels", labelsBox);

        generatorBox.addActionListener(event -> {
            int index = selectedGeneratorIndex(generatorBox);
            nodesSpinner.setValue(Integer.valueOf(DEFAULT_NODES[index]));
            edgesSpinner.setValue(Integer.valueOf(DEFAULT_EDGES[index]));
            probabilitySpinner.setValue(Double.valueOf(DEFAULT_PROBABILITY[index]));
            secondarySpinner.setValue(Integer.valueOf(DEFAULT_SECONDARY[index]));
            updateGeneratorDialog(index, panel, descriptionLabel, nodesLabel, edgesLabel, edgesSpinner,
                    probabilityLabel, probabilitySpinner, secondaryLabel, secondarySpinner);
        });
        updateGeneratorDialog(selectedGenerator, panel, descriptionLabel, nodesLabel, edgesLabel, edgesSpinner,
                probabilityLabel, probabilitySpinner, secondaryLabel, secondarySpinner);

        if (!showGeneratorDialog(panel)) {
            return null;
        }

        Object[] options = createGeneratorOptions(selectedGeneratorIndex(generatorBox),
                ((Number) nodesSpinner.getValue()).intValue(),
                ((Number) edgesSpinner.getValue()).intValue(),
                ((Number) probabilitySpinner.getValue()).doubleValue(),
                ((Number) secondarySpinner.getValue()).intValue(),
                directedBox.isSelected(),
                labelsBox.isSelected());
        rememberGeneratorOptions(options);
        return options;
    }

    private static void updateGeneratorDialog(int index, JPanel panel, JLabel descriptionLabel, JLabel nodesLabel,
            JLabel edgesLabel, java.awt.Component edgesEditor, JLabel probabilityLabel, java.awt.Component probabilityEditor,
            JLabel secondaryLabel, java.awt.Component secondaryEditor) {
        int safeIndex = safeGeneratorIndex(index);
        descriptionLabel.setText("<html><b>" + htmlEscape(GENERATOR_NAMES[safeIndex]) + "</b>: "
                + htmlEscape(GENERATOR_DESCRIPTIONS[safeIndex]) + "</html>");
        nodesLabel.setText(NODE_LABELS[safeIndex]);
        secondaryLabel.setText(SECONDARY_LABELS[safeIndex]);
        setDialogRowVisible(edgesLabel, edgesEditor, USES_EDGES[safeIndex]);
        setDialogRowVisible(probabilityLabel, probabilityEditor, USES_PROBABILITY[safeIndex]);
        setDialogRowVisible(secondaryLabel, secondaryEditor, USES_SECONDARY[safeIndex]);
        resizeDialogToContent(panel);
    }

    private static Graph parseGeneratedGraph(String graphMl, boolean directedDefault) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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

        Graph result = new AdjListGraph();
        result.setDirected(directedDefault);
        Map<String, String> keyToAttrName = readGraphMlKeys(document);
        Map<String, Node> nodesByXmlId = new HashMap<>();
        NodeList nodeElements = document.getElementsByTagName("node");
        int nodeCount = nodeElements.getLength();

        result.getListenerManager().transactionStarted(result);
        try {
            for (int i = 0; i < nodeCount; i++) {
                Element nodeElement = (Element) nodeElements.item(i);
                Map<String, String> data = readDataByAttrName(nodeElement, keyToAttrName);
                double x = parseDouble(data.get("x"), fallbackX(i, nodeCount));
                double y = parseDouble(data.get("y"), fallbackY(i, nodeCount));
                Node node = result.addNode(AttributeHelper.getDefaultGraphicsAttributeForNode(new Vector2d(x, y)));
                double width = parseDouble(data.get("width"), 25.0);
                double height = parseDouble(data.get("height"), 25.0);
                AttributeHelper.setSize(node, width, height);
                AttributeHelper.setShapeEllipse(node);
                String label = data.get("label");
                if (label != null && !label.isEmpty()) {
                    AttributeHelper.setLabel(node, label);
                }
                nodesByXmlId.put(nodeElement.getAttribute("id"), node);
            }

            NodeList edgeElements = document.getElementsByTagName("edge");
            for (int i = 0; i < edgeElements.getLength(); i++) {
                Element edgeElement = (Element) edgeElements.item(i);
                Node source = nodesByXmlId.get(edgeElement.getAttribute("source"));
                Node target = nodesByXmlId.get(edgeElement.getAttribute("target"));
                if (source == null || target == null) {
                    continue;
                }
                boolean directed = parseBoolean(edgeElement.getAttribute("directed"), directedDefault);
                Edge edge = result.addEdge(source, target, directed,
                        AttributeHelper.getDefaultGraphicsAttributeForEdge(Color.BLACK, Color.BLACK, directed));
                Map<String, String> data = readDataByAttrName(edgeElement, keyToAttrName);
                String label = data.get("edgelabel");
                if (label != null && !label.isEmpty()) {
                    AttributeHelper.setLabel(edge, label);
                }
            }

        } finally {
            result.getListenerManager().transactionFinished(result);
        }
        return result;
    }

    private static void normalizeGeneratedGraph(Graph graph, Dimension viewportSize) {
        List<Node> nodes = new ArrayList<>(graph.getNodes());
        if (nodes.isEmpty()) {
            return;
        }

        double[] sourceBounds = computeNodeBounds(nodes);
        if (!hasBounds(sourceBounds)) {
            applyCircularFallback(nodes);
            sourceBounds = computeNodeBounds(nodes);
        }
        if (!hasBounds(sourceBounds)) {
            return;
        }

        Dimension safeViewportSize = validDimension(viewportSize)
                ? viewportSize
                : new Dimension(GENERATED_LAYOUT_FALLBACK_VIEW_WIDTH, GENERATED_LAYOUT_FALLBACK_VIEW_HEIGHT);
        double viewportWidth = Math.max(240.0, safeViewportSize.getWidth());
        double viewportHeight = Math.max(220.0, safeViewportSize.getHeight());
        double margin = Math.max(GENERATED_LAYOUT_MIN_MARGIN,
                Math.min(GENERATED_LAYOUT_MAX_MARGIN, Math.min(viewportWidth, viewportHeight) * GENERATED_LAYOUT_MARGIN_FRACTION));
        double fitWidth = Math.max(120.0, viewportWidth - 2.0 * margin);
        double fitHeight = Math.max(120.0, viewportHeight - 2.0 * margin);
        double sourceWidth = Math.max(boundsWidth(sourceBounds), 1.0);
        double sourceHeight = Math.max(boundsHeight(sourceBounds), 1.0);
        double maxScale = Math.min(fitWidth / sourceWidth, fitHeight / sourceHeight);
        double minSideScale = sourceWidth <= sourceHeight
                ? (fitWidth * GENERATED_LAYOUT_MIN_SIDE_USAGE) / sourceWidth
                : (fitHeight * GENERATED_LAYOUT_MIN_SIDE_USAGE) / sourceHeight;
        double scale = Math.min(maxScale, minSideScale);
        if (!isFinite(scale) || scale <= 0.0) {
            scale = 1.0;
        }

        double dx = viewportWidth / 2.0 - boundsCenterX(sourceBounds) * scale;
        double dy = viewportHeight / 2.0 - boundsCenterY(sourceBounds) * scale;

        double minX = sourceBounds[0] * scale + dx;
        double minY = sourceBounds[1] * scale + dy;
        double maxX = sourceBounds[2] * scale + dx;
        double maxY = sourceBounds[3] * scale + dy;
        if (minX < margin) {
            dx += margin - minX;
        }
        if (minY < margin) {
            dy += margin - minY;
        }
        if (maxX > viewportWidth - margin) {
            dx -= maxX - (viewportWidth - margin);
        }
        if (maxY > viewportHeight - margin) {
            dy -= maxY - (viewportHeight - margin);
        }

        boolean transactionStarted = false;
        try {
            graph.getListenerManager().transactionStarted(graph);
            transactionStarted = true;
            for (Node node : nodes) {
                double x = AttributeHelper.getPositionX(node);
                double y = AttributeHelper.getPositionY(node);
                if (isFinite(x) && isFinite(y)) {
                    AttributeHelper.setPosition(node, x * scale + dx, y * scale + dy);
                }
            }
        } finally {
            if (transactionStarted) {
                graph.getListenerManager().transactionFinished(graph);
            }
        }
    }

    private static Dimension resolveGraphViewportSize(Graph graph) {
        Dimension activeViewSize = resolveActiveViewSize(graph);
        if (validDimension(activeViewSize)) {
            return activeViewSize;
        }

        Dimension desktopSize = resolveDesktopSize();
        if (validDimension(desktopSize)) {
            return desktopSize;
        }

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int fallbackWidth = Math.min(GENERATED_LAYOUT_FALLBACK_VIEW_WIDTH, Math.max(360, screenSize.width - 280));
        int fallbackHeight = Math.min(GENERATED_LAYOUT_FALLBACK_VIEW_HEIGHT, Math.max(300, screenSize.height - 240));
        return new Dimension(fallbackWidth, fallbackHeight);
    }

    private static Dimension resolveActiveViewSize(Graph graph) {
        try {
            MainFrame mainFrame = MainFrame.getInstance();
            if (mainFrame == null) {
                return null;
            }
            Session session = graph == null ? mainFrame.getActiveSession() : mainFrame.getEditorSessionForGraph(graph);
            if (session == null) {
                return null;
            }
            View view = session.getActiveView();
            if (view == null) {
                return null;
            }
            return componentVisibleSize(view.getViewComponent());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Dimension resolveDesktopSize() {
        try {
            MainFrame mainFrame = MainFrame.getInstance();
            if (mainFrame == null) {
                return null;
            }
            JDesktopPane desktop = mainFrame.getDesktop();
            if (desktop != null && validDimension(desktop.getSize())) {
                return desktop.getSize();
            }
            Dimension frameSize = mainFrame.getSize();
            if (validDimension(frameSize)) {
                return new Dimension(Math.max(320, frameSize.width - 380), Math.max(260, frameSize.height - 150));
            }
        } catch (RuntimeException ignored) {
            // Fall back to screen-based defaults below.
        }
        return null;
    }

    private static Dimension componentVisibleSize(JComponent component) {
        if (component == null) {
            return null;
        }
        Rectangle visible = component.getVisibleRect();
        if (visible != null && visible.width > 0 && visible.height > 0) {
            return new Dimension(visible.width, visible.height);
        }
        Dimension size = component.getSize();
        if (validDimension(size)) {
            return size;
        }
        Container parent = component.getParent();
        while (parent != null) {
            if (parent instanceof JViewport) {
                Dimension extentSize = ((JViewport) parent).getExtentSize();
                if (validDimension(extentSize)) {
                    return extentSize;
                }
            }
            Dimension parentSize = parent.getSize();
            if (validDimension(parentSize)) {
                return parentSize;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static boolean validDimension(Dimension size) {
        return size != null && size.width >= 120 && size.height >= 120;
    }

    private static double[] computeNodeBounds(List<Node> nodes) {
        double[] bounds = emptyBounds();
        for (Node node : nodes) {
            double x = AttributeHelper.getPositionX(node);
            double y = AttributeHelper.getPositionY(node);
            if (!isFinite(x) || !isFinite(y)) {
                continue;
            }
            double halfWidth = Math.max(1.0, AttributeHelper.getWidth(node)) / 2.0;
            double halfHeight = Math.max(1.0, AttributeHelper.getHeight(node)) / 2.0;
            addBoundsPoint(bounds, x - halfWidth, y - halfHeight);
            addBoundsPoint(bounds, x + halfWidth, y + halfHeight);
        }
        return bounds;
    }

    private static void applyCircularFallback(List<Node> nodes) {
        int total = Math.max(1, nodes.size());
        double radius = Math.max(120.0, Math.sqrt(total) * GENERATED_LAYOUT_NODE_SPACING / 2.0);
        for (int i = 0; i < nodes.size(); i++) {
            double angle = (2.0 * Math.PI * i) / total;
            AttributeHelper.setPosition(nodes.get(i),
                    Math.cos(angle) * radius,
                    Math.sin(angle) * radius);
        }
    }

    private static double[] emptyBounds() {
        return new double[] {
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };
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

    private static String buildGeneratorRequest(Object[] options) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<graphml>\n");
        appendKey(sb, "k_ogdf_mode", "graph", "ogdf.mode", "string");
        appendKey(sb, "k_ogdf_generator", "graph", "ogdf.generator", "string");
        appendKey(sb, "k_ogdf_generator_nodes", "graph", "ogdf.generator.nodes", "int");
        appendKey(sb, "k_ogdf_generator_edges", "graph", "ogdf.generator.edges", "int");
        appendKey(sb, "k_ogdf_generator_probability", "graph", "ogdf.generator.probability", "double");
        appendKey(sb, "k_ogdf_generator_secondary", "graph", "ogdf.generator.secondary", "int");
        appendKey(sb, "k_ogdf_generator_directed", "graph", "ogdf.generator.directed", "boolean");
        appendKey(sb, "k_ogdf_generator_labels", "graph", "ogdf.generator.labels", "boolean");
        sb.append("  <graph id=\"G\" edgedefault=\"")
                .append(optionBoolean(options, OPTION_DIRECTED, false) ? "directed" : "undirected")
                .append("\">\n");
        appendData(sb, "k_ogdf_mode", "generate");
        appendData(sb, "k_ogdf_generator", optionString(options, OPTION_GENERATOR, GENERATOR_IDS[0]));
        appendData(sb, "k_ogdf_generator_nodes", Integer.toString(optionInt(options, OPTION_NODES, DEFAULT_NODES[0])));
        appendData(sb, "k_ogdf_generator_edges", Integer.toString(optionInt(options, OPTION_EDGES, DEFAULT_EDGES[0])));
        appendData(sb, "k_ogdf_generator_probability", Double.toString(optionDouble(options, OPTION_PROBABILITY, DEFAULT_PROBABILITY[0])));
        appendData(sb, "k_ogdf_generator_secondary", Integer.toString(optionInt(options, OPTION_SECONDARY, DEFAULT_SECONDARY[0])));
        appendData(sb, "k_ogdf_generator_directed", Boolean.toString(optionBoolean(options, OPTION_DIRECTED, false)));
        appendData(sb, "k_ogdf_generator_labels", Boolean.toString(optionBoolean(options, OPTION_LABELS, true)));
        sb.append("  </graph>\n");
        sb.append("</graphml>\n");
        return sb.toString();
    }

    private static Map<String, String> readGraphMlKeys(Document document) {
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
        return keyToAttrName;
    }

    private static Map<String, String> readDataByAttrName(Element element, Map<String, String> keyToAttrName) {
        Map<String, String> valuesByAttrName = new HashMap<>();
        NodeList dataElements = element.getElementsByTagName("data");
        for (int i = 0; i < dataElements.getLength(); i++) {
            Element dataElement = (Element) dataElements.item(i);
            String keyId = dataElement.getAttribute("key");
            String attrName = keyToAttrName.getOrDefault(keyId, keyId);
            valuesByAttrName.put(attrName, Objects.toString(dataElement.getTextContent(), ""));
        }
        return valuesByAttrName;
    }

    private static void appendKey(StringBuilder sb, String id, String target, String attrName, String attrType) {
        sb.append("  <key id=\"").append(id)
                .append("\" for=\"").append(target)
                .append("\" attr.name=\"").append(attrName)
                .append("\" attr.type=\"").append(attrType)
                .append("\"/>\n");
    }

    private static void appendData(StringBuilder sb, String key, String value) {
        sb.append("    <data key=\"").append(key).append("\">")
                .append(escapeXml(value))
                .append("</data>\n");
    }

    private static Object[] createGeneratorOptions(int generatorIndex, int nodes, int edges, double probability,
            int secondary, boolean directed, boolean labels) {
        int safeIndex = safeGeneratorIndex(generatorIndex);
        return new Object[] {
                GENERATOR_IDS[safeIndex],
                Integer.valueOf(Math.max(1, nodes)),
                Integer.valueOf(Math.max(0, edges)),
                Double.valueOf(Math.max(0.0, Math.min(1.0, probability))),
                Integer.valueOf(Math.max(1, secondary)),
                Boolean.valueOf(directed),
                Boolean.valueOf(labels)
        };
    }

    private static Object[] rememberedGeneratorOptions(int generatorIndex) {
        int safeIndex = safeGeneratorIndex(generatorIndex);
        return createGeneratorOptions(safeIndex,
                readIntPreference(PREF_NODES, DEFAULT_NODES[safeIndex]),
                readIntPreference(PREF_EDGES, DEFAULT_EDGES[safeIndex]),
                readDoublePreference(PREF_PROBABILITY, DEFAULT_PROBABILITY[safeIndex]),
                readIntPreference(PREF_SECONDARY, DEFAULT_SECONDARY[safeIndex]),
                PREFS.getBoolean(PREF_DIRECTED, false),
                PREFS.getBoolean(PREF_LABELS, true));
    }

    private static void rememberGeneratorOptions(Object[] options) {
        PREFS.put(PREF_GENERATOR, optionString(options, OPTION_GENERATOR, GENERATOR_IDS[0]));
        PREFS.putInt(PREF_NODES, optionInt(options, OPTION_NODES, DEFAULT_NODES[0]));
        PREFS.putInt(PREF_EDGES, optionInt(options, OPTION_EDGES, DEFAULT_EDGES[0]));
        PREFS.putDouble(PREF_PROBABILITY, optionDouble(options, OPTION_PROBABILITY, DEFAULT_PROBABILITY[0]));
        PREFS.putInt(PREF_SECONDARY, optionInt(options, OPTION_SECONDARY, DEFAULT_SECONDARY[0]));
        PREFS.putBoolean(PREF_DIRECTED, optionBoolean(options, OPTION_DIRECTED, false));
        PREFS.putBoolean(PREF_LABELS, optionBoolean(options, OPTION_LABELS, true));
    }

    private static boolean showGeneratorDialog(JPanel contentPanel) {
        final boolean[] accepted = new boolean[] {false};
        JDialog dialog = new JDialog((Window) null, "OGDF Graph Generator", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
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

    private static void setDialogRowVisible(JLabel label, java.awt.Component editor, boolean visible) {
        label.setVisible(visible);
        editor.setVisible(visible);
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

    private static int selectedGeneratorIndex(JComboBox<String> box) {
        return safeGeneratorIndex(box.getSelectedIndex());
    }

    private static int findGeneratorIndex(String id) {
        if (id != null) {
            String cleaned = id.trim();
            for (int i = 0; i < GENERATOR_IDS.length; i++) {
                if (GENERATOR_IDS[i].equalsIgnoreCase(cleaned)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static int safeGeneratorIndex(int index) {
        return index >= 0 && index < GENERATOR_IDS.length ? index : 0;
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
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
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

    private static double fallbackX(int index, int total) {
        double radius = Math.max(120.0, total * 8.0);
        double angle = total <= 0 ? 0.0 : (2.0 * Math.PI * index) / total;
        return 200.0 + Math.cos(angle) * radius;
    }

    private static double fallbackY(int index, int total) {
        double radius = Math.max(120.0, total * 8.0);
        double angle = total <= 0 ? 0.0 : (2.0 * Math.PI * index) / total;
        return 200.0 + Math.sin(angle) * radius;
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isNaN(parsed) || Double.isInfinite(parsed) ? fallback : parsed;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isUiAvailable() {
        return !GraphicsEnvironment.isHeadless();
    }

    private static void setIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Keep defaults if this XML parser does not support the feature.
        }
    }

    private static String htmlEscape(String text) {
        return escapeXml(text);
    }

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
}
