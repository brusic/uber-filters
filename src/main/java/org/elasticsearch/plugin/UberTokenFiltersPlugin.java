package org.elasticsearch.plugin;

import org.elasticsearch.plugin.index.analysis.analysis.UberKeywordMarkerTokenFilterFactory;
import org.elasticsearch.plugin.index.analysis.analysis.UberStemmerOverrideTokenFilterFactory;
import org.elasticsearch.plugin.index.analysis.analysis.UberStopTokenFilterFactory;
import org.elasticsearch.plugin.index.analysis.analysis.UberSynonymTokenFilterFactory;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class UberTokenFiltersPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> filters = new HashMap<>();

        filters.put("uber_keyword_marker", requiresAnalysisSettings(UberKeywordMarkerTokenFilterFactory::new));
        filters.put("uber_stemmer_override", requiresAnalysisSettings(UberStemmerOverrideTokenFilterFactory::new));
        filters.put("uber_stop", requiresAnalysisSettings(UberStopTokenFilterFactory::new));
        filters.put("uber_synonym", requiresAnalysisSettings((indexSettings, environment, name, settings) ->
                new UberSynonymTokenFilterFactory(indexSettings, environment, new AnalysisModule(environment, Collections
                        .singletonList(this)).getAnalysisRegistry(), name, settings)
        ));

        return filters;
    }

    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> settings = new ArrayList<>();

        settings.add(new Setting<>("uber_filters.jdbc.driver", "", Function.identity(),
                Setting.Property.NodeScope));
        settings.add(new Setting<>("uber_filters.jdbc.url", "", Function.identity(),
                Setting.Property.NodeScope));
        settings.add(new Setting<>("uber_filters.jdbc.user", "", Function.identity(),
                Setting.Property.NodeScope));
        settings.add(new Setting<>("uber_filters.jdbc.password", "", Function.identity(),
                Setting.Property.NodeScope));
        settings.add(new Setting<>("uber_filters.jdbc.fetchsize", "", Function.identity(),
                Setting.Property.NodeScope));

        return settings;
    }


    /*
     * Borrowed from {@link org.elasticsearch.index.analysis.AnalysisRegistry#requiresAnalysisSettings(AnalysisModule.AnalysisProvider)}
     *
     * Do not preload token filters that need to be configured first*
     */
    private static <T> AnalysisModule.AnalysisProvider<T> requiresAnalysisSettings(AnalysisModule
                                                                                          .AnalysisProvider<T> provider) {
        return new AnalysisModule.AnalysisProvider<T>() {
            @Override
            public T get(IndexSettings indexSettings, Environment environment, String name, Settings settings) throws IOException {
                return provider.get(indexSettings, environment, name, settings);
            }
            @Override
            public boolean requiresAnalysisSettings() {
                return true;
            }
        };
    }
}
