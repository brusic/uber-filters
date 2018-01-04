package org.elasticsearch.plugin.index.analysis.analysis;

import org.elasticsearch.plugin.UberTokenFiltersPlugin;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.test.ESIntegTestCase;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

/**
 * Integratation tests using the transport client API
 *
 * Does not rely on database functionality
 */
public class PluginIT extends ESIntegTestCase {

    private static Logger logger = Loggers.getLogger(PluginIT.class);

    public void testPluginIsLoaded() throws Exception {
        NodesInfoResponse response = client().admin().cluster().prepareNodesInfo().setPlugins(true).get();

        for (NodeInfo nodeInfo : response.getNodes()) {
            boolean pluginFound = nodeInfo.getPlugins().getPluginInfos()
                    .stream()
                    .anyMatch(pluginInfo -> pluginInfo.getClassname().equals(UberTokenFiltersPlugin.class.getName()));

            assertTrue("Plugin is loaded", pluginFound);
        }
    }
}