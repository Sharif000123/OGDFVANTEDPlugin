package ogdf.integration;

import org.graffiti.plugin.EditorPluginAdapter;
import org.graffiti.plugin.inspector.InspectorTab;

/**
 * Serves as the plugin entry point declared in OgdfIntegration.xml. It
 * registers the OGDF inspector tab, which creates each action on demand.
 */
public class OgdfTestPlugin extends EditorPluginAdapter {

    // Registers the OGDF sidebar tab when VANTED loads the addon.
    public OgdfTestPlugin() {
        this.tabs = new InspectorTab[]{
            new OgdfControlTab()
        };
    }
}
