package org.elasticsearch.plugin.index.analysis.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.StemmerOverrideFilter;
import org.apache.lucene.analysis.miscellaneous.StemmerOverrideFilter.StemmerOverrideMap;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.Analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class UberStemmerOverrideTokenFilterFactory extends AbstractUberTokenFilterFactory {

    private final StemmerOverrideMap overrideMap;

    public UberStemmerOverrideTokenFilterFactory(IndexSettings indexSettings, Environment env, String name,
                                                 Settings settings) throws IOException {
        super(indexSettings, name, settings);

        List<String> rules = null;

        Optional<Collection<String>> terms = loadTerms();
        if (terms.isPresent()) {
            rules = new ArrayList<>(terms.get());
        }

        // if term loader did not return results or failed to process
        // TODO: define strict mode
        if (rules == null) {
            rules = Analysis.getWordList(env, settings, "rules");
        }

        if (rules == null) {
            throw new IllegalArgumentException("uber stemmer override filter requires either `query` " +
                    "`rules` or `rules_path` to be configured");
        }

        StemmerOverrideFilter.Builder builder = new StemmerOverrideFilter.Builder(false);
        parseRules(rules, builder, "=>");
        overrideMap = builder.build();
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new StemmerOverrideFilter(tokenStream, overrideMap);
    }

    private static void parseRules(List<String> rules, StemmerOverrideFilter.Builder builder, String
            mappingSep) {
        for (String rule : rules) {
            String key, override;
            List<String> mapping = Strings.splitSmart(rule, mappingSep, false);
            if (mapping.size() == 2) {
                key = mapping.get(0).trim();
                override = mapping.get(1).trim();
            } else {
                throw new RuntimeException("Invalid Keyword override Rule:" + rule);
            }

            if (key.isEmpty() || override.isEmpty()) {
                throw new RuntimeException("Invalid Keyword override Rule:" + rule);
            } else {
                builder.add(key, override);
            }
        }
    }
}