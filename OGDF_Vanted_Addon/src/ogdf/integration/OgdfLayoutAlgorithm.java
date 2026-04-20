package ogdf.integration;

import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import org.graffiti.graph.Edge;
import org.graffiti.graph.Graph;
import org.graffiti.graph.Node;
import org.graffiti.graphics.CoordinateAttribute;
import org.graffiti.plugin.algorithm.AbstractAlgorithm;

public class OgdfLayoutAlgorithm extends AbstractAlgorithm {

    @Override
    public String getName() {
        return "Apply OGDF Sugiyama Layout"; // This is the text on the button in VANTED
    }
    @Override
    public String getCategory() {
        return "Layout"; // This explicitly targets the "Layout" tab in VANTED
    }

    @Override
    public void execute() {
        Graph activeGraph = this.graph;

        if (activeGraph == null || activeGraph.isEmpty()) {
            System.out.println("No graph is open to layout!");
            return;
        }

        try {
            // 1. Setup the Data Mapping (VANTED Nodes -> Integers)
//            List<Node> nodeList = activeGraph.getNodes();
//            List<Edge> edgeList = activeGraph.getEdges();
            List<Node> nodeList = new ArrayList<>(activeGraph.getNodes());
            List<Edge> edgeList = new ArrayList<>(activeGraph.getEdges());
            Map<Node, Integer> nodeToIndex = new HashMap<>();
            
            for (int i = 0; i < nodeList.size(); i++) {
                nodeToIndex.put(nodeList.get(i), i);
            }

            String exePath = resolveOgdfExecutablePath();
            if (exePath == null) {
                System.err.println("OGDF executable not found. Set -Dogdf.layout.exe=<path> or OGDF_LAYOUT_EXE.");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(exePath);
            Process process = pb.start();

            try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
                writer.println(nodeList.size() + " " + edgeList.size());

                for (Edge e : edgeList) {
                    int srcIndex = nodeToIndex.get(e.getSource());
                    int tgtIndex = nodeToIndex.get(e.getTarget());
                    writer.println(srcIndex + " " + tgtIndex);
                }
                writer.flush();
            }

            int exitCode = process.waitFor();
            String stdout = readAll(process.getInputStream()).trim();
            String stderr = readAll(process.getErrorStream()).trim();

            if (exitCode != 0) {
                System.err.println("OGDF process failed with exit code " + exitCode + " (" + exePath + ").");
                if (!stderr.isEmpty()) {
                    System.err.println("OGDF stderr: " + stderr);
                }
                if (!stdout.isEmpty()) {
                    System.err.println("OGDF stdout: " + stdout);
                }
                return;
            }

            Scanner scanner = new Scanner(stdout);
            scanner.useLocale(Locale.US);

            if (!scanner.hasNextInt()) {
                System.err.println("Error: Did not receive valid output from OGDF.");
                if (!stderr.isEmpty()) {
                    System.err.println("OGDF stderr: " + stderr);
                }
                if (!stdout.isEmpty()) {
                    System.err.println("OGDF stdout: " + stdout);
                }
                scanner.close();
                return;
            }

            int returnedNodeCount = scanner.nextInt();
            int updateCount = Math.min(returnedNodeCount, nodeList.size());
            boolean transactionStarted = false;
            try {
                activeGraph.getListenerManager().transactionStarted(this);
                transactionStarted = true;

                for (int i = 0; i < updateCount; i++) {
                    if (!scanner.hasNextDouble()) {
                        throw new IllegalStateException("OGDF output ended early while reading x for node " + i);
                    }
                    double newX = scanner.nextDouble();
                    if (!scanner.hasNextDouble()) {
                        throw new IllegalStateException("OGDF output ended early while reading y for node " + i);
                    }
                    double newY = scanner.nextDouble();

                    Node targetNode = nodeList.get(i);
                    CoordinateAttribute coord = (CoordinateAttribute) targetNode.getAttribute("graphics.coordinate");
                    coord.setCoordinate(new Point2D.Double(newX, newY));
                }
            } finally {
                if (transactionStarted) {
                    activeGraph.getListenerManager().transactionFinished(this);
                }
            }

            if (returnedNodeCount != nodeList.size()) {
                System.err.println("Warning: OGDF returned " + returnedNodeCount + " nodes, expected " + nodeList.size() + ".");
            }

            System.out.println("Layout applied successfully!");
            scanner.close();
        } catch (Exception ex) {
            System.err.println("Failed to run OGDF layout:");
            ex.printStackTrace();
        }
    }

    private static String resolveOgdfExecutablePath() {
        String propertyPath = System.getProperty("ogdf.layout.exe");
        if (isExecutable(propertyPath)) {
            return propertyPath;
        }

        String envPath = System.getenv("OGDF_LAYOUT_EXE");
        if (isExecutable(envPath)) {
            return envPath;
        }

        String userHome = System.getProperty("user.home");
        String[] fallbackCandidates = new String[] {
            userHome + "\\AppData\\Roaming\\VANTED\\addons\\ogdf_layout_fixed.exe",
            "D:\\Work\\Vanted\\OGDFPlugin\\OGDF_Vanted_Addon\\ogdf_layout_fixed.exe",
            "D:\\Work\\OGDF\\ogdf\\build\\ogdf_layout.exe"
        };
        for (String candidate : fallbackCandidates) {
            if (isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isExecutable(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        File file = new File(path.trim());
        return file.isFile() && file.canExecute();
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString("UTF-8");
    }
}
