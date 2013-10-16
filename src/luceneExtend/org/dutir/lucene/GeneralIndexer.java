/**
 * 
 */
package org.dutir.lucene;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.AnalyzerManager;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.dutir.lucene.parser.DocumentParser;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.util.stream.StreamGenerator;

/**
 * @author Yezheng
 * 
 */
public class GeneralIndexer {

	protected static Logger logger = Logger.getLogger(GeneralIndexer.class);
	
	public static void indexFromPropertyFile() {
		String parserName = ApplicationSetup.getProperty("Lucene.DocParser",
		"org.dutir.lucene.parser.GeneralDocParser");
		int MergeFactor = Integer.parseInt(ApplicationSetup.getProperty("Lucene.MergeFactor",
		"50"));
		int MaxMergeDocs = Integer.parseInt(ApplicationSetup.getProperty("Lucene.MaxMergeDocs",
		"2000000"));
		int MaxBufferedDocs = Integer.parseInt(ApplicationSetup.getProperty("Lucene.MaxBufferedDocs",
		"20000"));
		int RAMBufferSizeMB = Integer.parseInt(ApplicationSetup.getProperty("Lucene.RAMBufferSizeMB",
		"1024"));
		int maxFieldIndexUnit = Integer.parseInt(ApplicationSetup.getProperty("Lucene.maxFieldIndexUnit",
		"10000"));
		
		try {
			long start = System.currentTimeMillis();

			
			
			String indexDirectory = ApplicationSetup.getProperty(
					"Lucene.indexDirectory", "luceneindex");
			
			String corpusDir = ApplicationSetup.getProperty(
					"Lucene.corpusDirectory", ApplicationSetup.COLLECTION_SPEC);
			
			boolean create = Boolean.parseBoolean(ApplicationSetup.getProperty(
					"Lucene.create", "true"));
			
			String encoding = ApplicationSetup.getProperty(
					"corpus.encoding", "utf8");
			
			ApplicationSetup.resetLogFile(indexDirectory + "/index.log");
			logger  = Logger.getLogger(GeneralIndexer.class);
			Analyzer analyzer = AnalyzerManager.getFromPropertyFile();
			DocumentParser parser = (DocumentParser) Class.forName(parserName)
			.newInstance();
			
			if(logger.isInfoEnabled()) logger.info("Lucene.corpusDirectory: " + corpusDir);
			if(logger.isInfoEnabled()) logger.info("Lucene.indexDirectory: " + indexDirectory);
			if(logger.isInfoEnabled()) logger.info("Lucene.maxFieldIndexUnit: " + maxFieldIndexUnit);
			
			if(create){
				BufferedReader  br = StreamGenerator.getConsoleReader();
				String line = null;
				boolean interactive = Boolean.parseBoolean(ApplicationSetup.getProperty("GeneralIndexer.interactive", "true"));
				if(interactive){
					System.out.println("U R trying to create a new Index in Directory: " + indexDirectory);
					System.out.println("And the all contents in this directory would be deleted.\n input yes to create, no to cancel" );
				}
				while(interactive){
					System.out.print("input:");
					line = br.readLine();
					if(line != null && line.equals("yes")){
						break;
					}else if(line != null && line.equals("no")){
						System.exit(1);
					}
				}

			}
			
			File indexDir = new File(indexDirectory);
			if(!indexDir.exists()){
				indexDir.mkdirs();
			}
			
			IndexWriter writer = new IndexWriter(FSDirectory
					.getDirectory(indexDirectory), analyzer, create);
			
			writer.setMaxBufferedDocs(MaxBufferedDocs);
			writer.setMergeFactor(MergeFactor);
			writer.setRAMBufferSizeMB(RAMBufferSizeMB);
			writer.setMaxMergeDocs(MaxMergeDocs);
			
			writer.setMaxFieldLength(maxFieldIndexUnit);
			
			DiskCorpus<DocumentParser> corpus = new DiskCorpus<DocumentParser>(parser, corpusDir);
			corpus.setCharEncoding(encoding);
			corpus.setIndexWriter(writer);
			corpus.visitCorpus();
			
			writer.optimize();
			int count = writer.docCount();
			
			writer.close();
			long end = System.currentTimeMillis();
			if(logger.isInfoEnabled()) logger.info("Finished: " + count + " documents has been indexed");
			if(logger.isInfoEnabled()) logger.info("Indexing time: " + (end -start)/(1000*60) + " Minis");
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CorruptIndexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (LockObtainFailedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		indexFromPropertyFile();
	}

}
