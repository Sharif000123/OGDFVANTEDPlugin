package ogdf.integration;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import org.graffiti.editor.GravistoService;
import org.graffiti.plugin.algorithm.Algorithm;
import org.graffiti.plugin.inspector.InspectorTab;
import org.graffiti.plugin.view.View;

/**
 * Builds the OGDF inspector tab shown beside VANTED's other sidebar tabs. Its
 * buttons launch graph generation, layout, and metric actions.
 */
public final class OgdfControlTab extends InspectorTab {

    private static final long serialVersionUID = 1L;

    // Tab construction -------------------------------------------------------
    // Builds the OGDF tab and connects its three buttons to their actions.
    public OgdfControlTab() {
        this.title = "OGDF";
        setPreferredTabPosition(InspectorTab.TAB_TRAILING);
        setLayout(new BorderLayout());

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = -1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 8, 0);

        addButton(content, gbc, "Generate OGDF Graph",
                "Create a new graph with OGDF graph generators.",
                OgdfGraphGeneratorAlgorithm::new);
        addButton(content, gbc, "Apply OGDF Layout",
                "Run an OGDF layout on the active VANTED graph.",
                OgdfLayoutAlgorithm::new);
        addButton(content, gbc, "Calculate Graph Metrics",
                "Calculate OGDF graph and layout metrics for the active graph.",
                OgdfMetricsAlgorithm::new);

        gbc.insets = new Insets(6, 0, 0, 0);
        JLabel note = new JLabel("<html>Generate a graph before applying layouts or calculating metrics.</html>");
        note.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(note, nextRow(gbc));

        gbc.weighty = 1.0;
        content.add(new JPanel(), nextRow(gbc));

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    // Button dispatch --------------------------------------------------------
    // Adds a button that creates and runs its algorithm when clicked.
    private static void addButton(JPanel content, GridBagConstraints gbc, String text, String tooltip,
            Supplier<Algorithm> algorithmFactory) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(180, button.getPreferredSize().height));
        button.addActionListener(event -> runAlgorithm(algorithmFactory.get(), event));
        content.add(button, nextRow(gbc));
    }

    // Advances the shared layout constraints to the next row.
    private static GridBagConstraints nextRow(GridBagConstraints gbc) {
        gbc.gridy++;
        return gbc;
    }

    // Runs an algorithm through VANTED and reports startup errors to the user.
    private static void runAlgorithm(Algorithm algorithm, ActionEvent event) {
        try {
            GravistoService.getInstance().runAlgorithm(algorithm, event);
        } catch (RuntimeException ex) {
            System.err.println("Failed to start OGDF action:");
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Failed to start OGDF action:\n" + ex.getMessage(),
                    "OGDF", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Visibility control -----------------------------------------------------
    // Keeps the OGDF tab visible for every view; each action validates its graph when clicked.
    @Override
    public boolean visibleForView(View view) {
        return true;
    }
}
