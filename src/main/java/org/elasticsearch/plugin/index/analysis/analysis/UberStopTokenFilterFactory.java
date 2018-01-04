package org.elasticsearch.plugin.index.analysis.analysis;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.search.suggest.analyzing.SuggestStopFilter;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.Analysis;

import java.util.Collection;
import java.util.Optional;

public class UberStopTokenFilterFactory extends AbstractUberTokenFilterFactory {

    private CharArraySet stopWords;

    private final boolean ignoreCase;

    private final boolean removeTrailing;

    public UberStopTokenFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);

        this.ignoreCase = settings.getAsBoolean("ignore_case", false);
        this.removeTrailing = settings.getAsBoolean("remove_trailing", true);

        this.stopWords = initStopWords(env, settings);

        if (settings.get("enable_position_increments") != null) {
            throw new IllegalArgumentException("enable_position_increments is not supported anymore. Please fix your analysis chain");
        }
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        if (removeTrailing) {
            return new StopFilter(tokenStream, stopWords);
        } else {
            return new SuggestStopFilter(tokenStream, stopWords);
        }
    }

    private CharArraySet initStopWords(Environment env, Settings settings) {
        Optional<Collection<String>> terms = loadTerms();
        if (terms.isPresent()) {
            logger.warn("found some stopwords: {}", terms.get());
            return new CharArraySet(terms.get(), ignoreCase);
        }

        // if term loader did not return results or failed to process
        // TODO: define strict mode
        return Analysis.parseStopWords(env, settings, StopAnalyzer.ENGLISH_STOP_WORDS_SET, ignoreCase);
    }

}
