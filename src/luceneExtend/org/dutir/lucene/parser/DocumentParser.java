/**
 * 
 */
package org.dutir.lucene.parser;

import java.io.File;
import java.io.InputStream;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;

/**
 * @author Yezheng
 *
 */
public interface DocumentParser {

	final int Cache = 1024 * 1024 * 2;
	
//	public Document breakIntoDocumentsBegin( String pCollectionText ) throws Exception;
	public Document nextDoc() throws Exception;
	public void setup(InputStream is, File file, String encoding);
	public void setParserFile(String path);
	public void setParserFile(File file);
	public void setIndexWriter(IndexWriter writer);
	public void indexAll();
}
