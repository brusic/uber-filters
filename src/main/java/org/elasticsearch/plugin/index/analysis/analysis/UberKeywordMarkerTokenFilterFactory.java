package org.elasticsearch.plugin.index.analysis.analysis;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.PatternKeywordMarkerFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.Analysis;

import java.util.Collection;
import java.util.Optional;

/**
 * A factory for creating keyword marker token filters that prevent tokens from
 * being modified by stemmers.  Two types of keyword marker filters are available:
 * the {@link SetKeywordMarkerFilter} and the {@link PatternKeywordMarkerFilter}.
 *
 * The {@link SetKeywordMarkerFilter} uses a set of keywords to denote which tokens
 * should be excluded from stemming.  This filter is created if the settings include
 * {@code keywords}, which contains the list of keywords, or {@code `keywords_path`},
 * which contains a path to a file in the config directory with the keywords.
 *
 * The {@link PatternKeywordMarkerFilter} uses a regular expression pattern to match
 * against tokens that should be excluded from stemming.  This filter is created if
 * the settings include {@code keywords_pattern}, which contains the regular expression
 * to match against.
 */
public class UberKeywordMarkerTokenFilterFactory extends AbstractUberTokenFilterFactory {

    private final CharArraySet keywordLookup;

    public UberKeywordMarkerTokenFilterFactory(IndexSettings indexSettings, Environment env, String name,
                                               Settings settings) {
        super(indexSettings, name, settings);

        boolean ignoreCase = settings.getAsBoolean("ignore_case", false);

        Collection<?> rules = null;

        Optional<Collection<String>> terms = loadTerms();
        if (terms.isPresent()) {
            rules = terms.get();
        }

        // if term loader did not return results or failed to process
        // TODO: define strict mode
        if (rules == null) {
            rules = Analysis.getWordSet(env, settings, "keywords");
        }

        if (rules == null) {
            throw new IllegalArgumentException(
                    "uber keyword filter requires either `query` `keywords`, `keywords_path`, " +
                            "or `keywords_pattern` to be configured");
        }
        // a set of keywords (or a path to them) is specified
        keywordLookup = new CharArraySet(rules, ignoreCase);

    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new SetKeywordMarkerFilter(tokenStream, keywordLookup);
    }
}
