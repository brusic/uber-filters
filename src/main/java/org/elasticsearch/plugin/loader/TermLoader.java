package org.elasticsearch.plugin.loader;

import java.util.Collection;

public interface TermLoader {

    Collection<String> loadTerms();
}