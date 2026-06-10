package ogdf.integration;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.graffiti.graph.Edge;
import org.graffiti.graph.Graph;
import org.graffiti.graph.Node;
import org.graffiti.plugin.algorithm.AbstractAlgorithm;

/**
 * Sends the current VANTED graph to OGDF for metric calculation. It stores and
 * displays the returned metrics without changing the layout.
 */
public class OgdfMetricsAlgorithm extends AbstractAlgorithm {

    // VANTED algorithm metadata ---------------------------------------------
    // Returns the action name displayed in the OGDF tab.
    @Override
    public String getName() {
        return "Calculate Graph Metrics";
    }

    // Returns the legacy category name used by VANTED algorithm metadata.
    @Override
    public String getCategory() {
        return "OGDF";
    }

    // Metrics request lifecycle ---------------------------------------------
    // Builds a metrics-only GraphML request and starts it outside the UI thread.
    @Override
    public void execute() {
        Graph activeGraph = this.graph;
        if (activeGraph == null || activeGraph.isEmpty()) {
            System.out.println("No graph is open to calculate metrics!");
            return;
        }
        if (!OgdfLayoutAlgorithm.confirmExpensiveMetricCalculation(activeGraph)) {
            return;
        }

        List<Node> nodeList = new ArrayList<>(activeGraph.getNodes());
        List<Edge> edgeList = new ArrayList<>(activeGraph.getEdges());
        Map<Node, Integer> nodeToIndex = new HashMap<>();
        for (int i = 0; i < nodeList.size(); i++) {
            nodeToIndex.put(nodeList.get(i), Integer.valueOf(i));
        }

        String graphMlRequest = OgdfLayoutAlgorithm.buildGraphMlRequest(
                activeGraph, nodeList, edgeList, nodeToIndex, null, "metrics");
        Thread worker = new Thread(() -> runMetrics(activeGraph, graphMlRequest), "OGDF metrics calculator");
        worker.setDaemon(true);
        worker.start();
    }

    // Runs the native bridge, stores the metrics, and displays the result.
    private void runMetrics(Graph activeGraph, String graphMlRequest) {
        try {
            // Runtime discovery is shared with layouts and graph generation.
            OgdfLayoutAlgorithm.RuntimeConfig runtime = OgdfLayoutAlgorithm.resolveRuntimeConfig();
            if (runtime.executablePath == null) {
                runtime = OgdfLayoutAlgorithm.chooseOgdfDirectoryAndRemember(runtime);
            }
            if (runtime.executablePath == null) {
                System.err.println("OGDF bridge executable not found for graph metrics.");
                return;
            }

            Process process = OgdfLayoutAlgorithm.startOgdfProcess(runtime);
            ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
            Thread stdoutReader = OgdfLayoutAlgorithm.startStreamReader(
                    process.getInputStream(), stdoutBuffer, "OGDF metrics stdout reader");
            Thread stderrReader = OgdfLayoutAlgorithm.startStreamReader(
                    process.getErrorStream(), stderrBuffer, "OGDF metrics stderr reader");

            // Sends GraphML through stdin; the bridge reports machine-readable metrics through stderr.
            try (OutputStream output = process.getOutputStream()) {
                output.write(graphMlRequest.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            int exitCode = process.waitFor();
            stdoutReader.join();
            stderrReader.join();
            String stdout = OgdfLayoutAlgorithm.bufferToString(stdoutBuffer);
            String stderr = OgdfLayoutAlgorithm.bufferToString(stderrBuffer).trim();

            if (exitCode != 0) {
                System.err.println("OGDF metrics calculation failed with exit code "
                        + exitCode + " (" + runtime.executablePath + ").");
                if (!stderr.isEmpty()) {
                    System.err.println("OGDF stderr: " + stderr);
                }
                if (!stdout.isEmpty()) {
                    System.err.println("OGDF stdout: " + stdout);
                }
                return;
            }

            Map<String, String> metrics = OgdfLayoutAlgorithm.parseMetrics(stderr);
            String nonMetricStderr = OgdfLayoutAlgorithm.extractNonMetricStderr(stderr);
            if (metrics.isEmpty()) {
                System.err.println("Error: OGDF did not return graph metrics.");
                if (!nonMetricStderr.isEmpty()) {
                    System.err.println("OGDF stderr: " + nonMetricStderr);
                }
                return;
            }

            // Save metrics on the graph so they remain available after closing the dialog.
            OgdfLayoutAlgorithm.applyMetricsToGraph(activeGraph, metrics);
            System.out.println("OGDF metrics: " + OgdfLayoutAlgorithm.formatMetrics(metrics));
            if (!nonMetricStderr.isEmpty()) {
                System.err.println("OGDF stderr: " + nonMetricStderr);
            }
            String warnings = String.join("\n", OgdfLayoutAlgorithm.extractWarningMessages(nonMetricStderr));
            SwingUtilities.invokeLater(() -> {
                OgdfLayoutAlgorithm.showMetricsDialog(metrics);
                OgdfLayoutAlgorithm.showWarningDialog(warnings);
            });
        } catch (Exception ex) {
            System.err.println("Failed to calculate OGDF graph metrics:");
            ex.printStackTrace();
        }
    }
}
