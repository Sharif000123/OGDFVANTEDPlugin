package ogdf.integration;

import org.graffiti.plugin.EditorPluginAdapter;
import org.graffiti.plugin.inspector.InspectorTab;

public class OgdfTestPlugin extends EditorPluginAdapter {

    public OgdfTestPlugin() {
        this.tabs = new InspectorTab[] {
                new OgdfControlTab()
        };
    }
}
