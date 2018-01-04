package org.elasticsearch.plugin.index.analysis.analysis;

import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.plugin.UberTokenFiltersPlugin;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;

import static org.hamcrest.Matchers.instanceOf;

/**
 * Unit tests for {@link UberSynonymTokenFilterFactory}
 */
public class UberSynonymTokenFilterFactoryTests extends ESTestCase {

    public void testUberStopFilter() throws IOException {
        Settings settings = Settings.builder()
                .put("index.analysis.filter.stop_db_test.type", "uber_synonym")
                .putArray("index.analysis.filter.stop_db_test.synonyms", "i-pod, i pod => ipod", "universe, cosmos")
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .build();

        ESTestCase.TestAnalysis analysis = AnalysisTestsHelper.createTestAnalysis
                (settings, new UberTokenFiltersPlugin());
        TokenFilterFactory tokenFilter = analysis.tokenFilter.get("stop_db_test");
        assertThat(tokenFilter, instanceOf(UberSynonymTokenFilterFactory.class));
    }

    public void testValidation() throws IOException {
        Settings settings = Settings.builder()
                .put("index.analysis.filter.stop_db_test.type", "uber_synonym")
                .put("index.analysis.filter.stop_db_test.query", "select * from synonyms")
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .build();

        IllegalArgumentException thrown = expectThrows(IllegalArgumentException.class,
                () -> AnalysisTestsHelper.createTestAnalysis(settings, new UberTokenFiltersPlugin()));
        assertTrue("Should throw exception", thrown.getMessage().startsWith("Required uber_filters. " +
                "settings are not defined"));
    }
}