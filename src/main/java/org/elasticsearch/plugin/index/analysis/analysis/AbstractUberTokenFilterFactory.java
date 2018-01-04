package org.elasticsearch.plugin.index.analysis.analysis;

import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.plugin.loader.DatabaseTermLoader;
import org.elasticsearch.plugin.loader.TermLoader;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;

import java.util.Collection;
import java.util.Optional;

public abstract class AbstractUberTokenFilterFactory extends AbstractTokenFilterFactory {

    private static final String PLUGIN_PREFIX = "uber_filters";

    private TermLoader termLoader;

    AbstractUberTokenFilterFactory(IndexSettings indexSettings, String name, Settings settings) {
        super(indexSettings, name, settings);

        logger.info("Creating {} for {}", name, indexSettings.getIndex().getName());

        // only the query is needed from the settings
        // the if block is useful in the future if different loaders are defined (ex S3 loader)
        String query = settings.get("query", null);
        if (query != null) {
            termLoader = new DatabaseTermLoader(indexSettings, settings, PLUGIN_PREFIX);
        } else {
            logger.warn("No term loader created");
        }
    }

    public abstract TokenStream create(TokenStream tokenStream);

    Optional<Collection<String>> loadTerms() {
        if (termLoader != null) {
            Collection<String> value = termLoader.loadTerms();
            logger.debug("Found {} terms", value.size());
            return Optional.of(value);
        } else {
            logger.warn("No term loader defined");
            return Optional.empty();
        }
    }
}