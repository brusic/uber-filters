package org.elasticsearch.plugin.index.analysis.analysis;

import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.plugin.UberTokenFiltersPlugin;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.search.suggest.analyzing.SuggestStopFilter;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.io.StringReader;

import static org.hamcrest.Matchers.instanceOf;

/**
 * Unit tests for {@link UberStopTokenFilterFactory}
 *
 * Tests are not integration tests, therefore cannot access a database
 */
public class UberStopTokenFilterFactoryTests extends ESTestCase {

    public void testUberStopFilter() throws IOException {
        Settings settings = Settings.builder()
                .put("index.analysis.filter.stop_uber_test.type", "uber_stop")
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .build();

        ESTestCase.TestAnalysis analysis = AnalysisTestsHelper.createTestAnalysis
                (settings, new UberTokenFiltersPlugin());
        TokenFilterFactory tokenFilter = analysis.tokenFilter.get("stop_uber_test");
        assertThat(tokenFilter, instanceOf(UberStopTokenFilterFactory.class));

        Tokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("does not matter"));
        TokenStream tokenStream = tokenFilter.create(tokenizer);

        // database or not, should use normal stop filter (with default remove_trailing)
        assertThat(tokenStream, instanceOf(StopFilter.class));
    }

    public void testUberSuggestStopFilter() throws IOException {
        Settings settings = Settings.builder()
                .put("index.analysis.filter.stop_uber_test.type", "uber_stop")
                .put("index.analysis.filter.stop_uber_test.remove_trailing", false)
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .build();

        ESTestCase.TestAnalysis analysis = AnalysisTestsHelper.createTestAnalysis
                (settings, new UberTokenFiltersPlugin());
        TokenFilterFactory tokenFilter = analysis.tokenFilter.get("stop_uber_test");
        assertThat(tokenFilter, instanceOf(UberStopTokenFilterFactory.class));

        Tokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("does not matter"));
        TokenStream tokenStream = tokenFilter.create(tokenizer);

        // database or not, should use suggest stop filter  (when remove_trailing is false)
        assertThat(tokenStream, instanceOf(SuggestStopFilter.class));
    }

    public void testValidation() throws IOException {
        Settings settings = Settings.builder()
                .put("index.analysis.filter.stop_uber_test.type", "uber_stop")
                .put("index.analysis.filter.stop_uber_test.query", "select * from stopwords")
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .build();

        IllegalArgumentException thrown = expectThrows(IllegalArgumentException.class,
                () -> AnalysisTestsHelper.createTestAnalysis(settings, new UberTokenFiltersPlugin()));
        assertTrue("Should throw exception", thrown.getMessage().startsWith("Required uber_filters. " +
                "settings are not defined"));
    }
}