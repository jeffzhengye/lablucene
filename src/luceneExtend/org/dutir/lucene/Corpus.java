
package org.dutir.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexWriter;
import org.dutir.lucene.parser.DocumentParser;

/**
 * The <code>Corpus</code> abstract class provides a basis for passing
 * training and testing data to data handlers.  The methods walk
 * handlers over the training and/or test data, depending on which of
 * the methods is called.
 */
public class Corpus<P extends DocumentParser> {

	IndexWriter writer = null;
    /**
     * Construct a corpus.
     */
    protected Corpus() { 
        /* only for protection */
    }

    /**
     * Visit the entire corpus, sending all extracted events to the
     * specified handler.
     *
     * <p>This is just a convenience method that is defined by:
     * <blockquote><pre>
     * visitCorpus(handler,handler);
     * </pre></blockquote>
     *
     * @param handler Handler for events extracted from the corpus.
     * @throws IOException If there is an underlying I/O error.
     */
    public void visitCorpus(DocumentParser parser) 
        throws IOException {

    }

    public void setIndexWriter(IndexWriter writer){
    	this.writer = writer;
    }

}
