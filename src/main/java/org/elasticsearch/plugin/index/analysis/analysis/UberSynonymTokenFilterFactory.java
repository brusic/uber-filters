package org.elasticsearch.plugin.index.analysis.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.synonym.WordnetSynonymParser;
import org.elasticsearch.common.io.FastStringReader;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.Analysis;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class UberSynonymTokenFilterFactory extends AbstractUberTokenFilterFactory {

    private final SynonymMap synonymMap;
    private final boolean ignoreCase;

    public UberSynonymTokenFilterFactory(IndexSettings indexSettings, Environment env, AnalysisRegistry analysisRegistry,
                                     String name, Settings settings) throws IOException {
        super(indexSettings, name, settings);

        Reader rulesReader = getReader(env, settings);

        this.ignoreCase = settings.getAsBoolean("ignore_case", false);
        boolean expand = settings.getAsBoolean("expand", true);

        String tokenizerName = settings.get("tokenizer", "whitespace");

        AnalysisModule.AnalysisProvider<TokenizerFactory> tokenizerFactoryFactory =
                analysisRegistry.getTokenizerProvider(tokenizerName, indexSettings);
        if (tokenizerFactoryFactory == null) {
            throw new IllegalArgumentException("failed to find tokenizer [" + tokenizerName + "] for synonym token filter");
        }
        final TokenizerFactory tokenizerFactory = tokenizerFactoryFactory.get(indexSettings, env, tokenizerName,
                AnalysisRegistry.getSettingsFromIndexSettings(indexSettings,
                        AnalysisRegistry.INDEX_ANALYSIS_TOKENIZER + "." + tokenizerName));
        Analyzer analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = tokenizerFactory == null ? new WhitespaceTokenizer() : tokenizerFactory.create();
                TokenStream stream = ignoreCase ? new LowerCaseFilter(tokenizer) : tokenizer;
                return new TokenStreamComponents(tokenizer, stream);
            }
        };

        try {
            SynonymMap.Builder parser;

            if ("wordnet".equalsIgnoreCase(settings.get("format"))) {
                parser = new WordnetSynonymParser(true, expand, analyzer);
                ((WordnetSynonymParser) parser).parse(rulesReader);
            } else {
                parser = new SolrSynonymParser(true, expand, analyzer);
                ((SolrSynonymParser) parser).parse(rulesReader);
            }

            synonymMap = parser.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to build synonyms", e);
        }
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        // fst is null means no synonyms
        return synonymMap.fst == null ? tokenStream : new SynonymFilter(tokenStream, synonymMap, ignoreCase);
    }

    private Reader getReader(Environment env, Settings settings) {
        Optional<Collection<String>> terms = loadTerms();

        if (terms.isPresent()) {
            return createReaderFromList(terms.get());
        }

        // if term loader did not return results or failed to process
        // TODO: define strict mode
        return getDefaultReader(env, settings);
    }

    private Reader getDefaultReader(Environment env, Settings settings) {
        Reader rulesReader;
        if (settings.getAsArray("synonyms", null) != null) {
            List<String> rules = Analysis.getWordList(env, settings, "synonyms");
            StringBuilder sb = new StringBuilder();
            for (String line : rules) {
                sb.append(line).append(System.lineSeparator());
            }
            rulesReader = new FastStringReader(sb.toString());
        } else if (settings.get("synonyms_path") != null) {
            rulesReader = Analysis.getReaderFromFile(env, settings, "synonyms_path");
        } else {
            throw new IllegalArgumentException("synonym filter requires either `query` `synonyms` or " +
                    "`synonyms_path`" +
                    " to be configured");
        }
        return rulesReader;
    }

    private Reader createReaderFromList(Collection<String> rules){
        StringBuilder sb = new StringBuilder();
        for (String line : rules) {
            sb.append(line).append(System.getProperty("line.separator"));
        }
        logger.warn("found these values {}", sb.toString());
        return new FastStringReader(sb.toString());
    }
}