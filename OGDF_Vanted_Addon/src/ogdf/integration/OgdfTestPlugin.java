package ogdf.integration;

import org.graffiti.plugin.GenericPluginAdapter;
import org.graffiti.plugin.algorithm.Algorithm;

public class OgdfTestPlugin extends GenericPluginAdapter {

    public OgdfTestPlugin() {
        super();
        
        // This registers your custom algorithm in the VANTED menu
        this.algorithms = new Algorithm[] {
            new OgdfLayoutAlgorithm()
        };
    }
}