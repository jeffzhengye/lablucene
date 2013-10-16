/**
 * 
 */
package org.dutir.lucene;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.AnalyzerManager;
import org.apache.lucene.queryParser.RMultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RDefaultSimilarity;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.util.stream.StreamGenerator;


/**
 * @author yezheng
 *
 */
public class ISManager {
	static Logger logger  = Logger.getLogger(ISManager.class.getName());
	
	static Searcher searcher = null;
	static int SearcherPathType_MULTIPATH = 0;
	static int SearcherPathType_Intelligence = 1;
	static int SearcherPathType = Integer.parseInt(ApplicationSetup.getProperty("Lucene.ISManager.SearcherPathType", "0"));
	
	public static void getSearcherList(ArrayList<Searcher> list) throws IOException{
		String indexPaths = ApplicationSetup.getProperty(
				"Lucene.indexDirectory", "./index");
		String indexps[] = indexPaths.split("\\s*,\\s*");
		for(int i=0; i < indexps.length; i++){
			File file = new File(indexps[i]);
			getSearcherList(list, file);
		}
	}
	
	private static void getSearcherList(ArrayList<Searcher> list, File file) throws IOException{
		if(file.isDirectory()){
			FSDirectory dir = NIOFSDirectory.getDirectory(file);
//				FSDirectory.getDirectory(file);
			if(dir.fileExists("segments.gen")){
				try {
					IndexReader reader = IndexReader.open(dir, true);
					
					list.add(new IndexSearcher(reader));
					if(logger.isInfoEnabled()) logger.info("loading subIndex from: " + file.getAbsolutePath());
				} catch (Exception e) {
					if(logger.isInfoEnabled()) logger.info("false Index: " + file.getAbsolutePath());
					e.printStackTrace();
					System.exit(1);
				}
				
			}else{
				dir.close();
				File[] flist = file.listFiles();
				for(int i=0; i < flist.length; i++){
					getSearcherList(list, flist[i]);
				}
			}
		}
		
	}
	
	public static Searcher getSearcheFromPath(String path){
		ArrayList<Searcher> list = new ArrayList<Searcher>();
		File file = new File(path);
		try {
			Similarity similarity = new RDefaultSimilarity();
			getSearcherList(list, file);
			if(list.size() < 1){
				logger.warn("no index has been found in path: " + path);
				if(logger.isInfoEnabled()) logger.info("no index has been found in path: " + path);
				System.exit(1);
			}
			Searcher seachers[] = new IndexSearcher[list.size()];
			for (int i = 0; i < list.size(); i++) {
				seachers[i] = list.get(i);
				seachers[i].setSimilarity(similarity);
			}
			searcher = new MultiSearcher(seachers);
			logger.debug("using Similarity: " + similarity.getClass());
			searcher.setSimilarity(similarity);
			return searcher;
		} catch (IOException e) {
			logger.error("loading index failed", e);
		}
		return null;
	}
	
	
	public static Searcher getSearcheFromPropertyFile(){
		try {
			if(searcher == null){
				Similarity similarity = new RDefaultSimilarity();
				if(SearcherPathType == SearcherPathType_MULTIPATH){
					String indexPaths = ApplicationSetup.getProperty(
							"Lucene.indexDirectory", "./index");
					String indexps[] = indexPaths.split("\\s*,\\s*");
					if (indexps.length == 1) {
						searcher = new IndexSearcher(indexPaths);
					} else {
						IndexSearcher seachers[] = new IndexSearcher[indexps.length];
						for (int i = 0; i < indexps.length; i++) {
							seachers[i] = new IndexSearcher(IndexReader.open(NIOFSDirectory.getDirectory(indexps[i]), true));
							seachers[i].setSimilarity(similarity);
						}
						searcher = new MultiSearcher(seachers);
					}
				}else if(SearcherPathType == SearcherPathType_Intelligence){
					ArrayList<Searcher> list = new ArrayList<Searcher>();
					getSearcherList(list);
					if(list.size() < 1){
						logger.warn("no index has been found in path: " + ApplicationSetup.getProperty(
								"Lucene.indexDirectory", "./index"));
						if(logger.isInfoEnabled()) logger.info("no index has been found in path: " + ApplicationSetup.getProperty(
								"Lucene.indexDirectory", "./index"));
						System.exit(1);
					}
					Searcher seachers[] = new IndexSearcher[list.size()];
					for (int i = 0; i < list.size(); i++) {
						seachers[i] = list.get(i);
						seachers[i].setSimilarity(similarity);
					}
					searcher = new MultiSearcher(seachers);
				}
				logger.debug("using Similarity: " + similarity.getClass());
				searcher.setSimilarity(similarity);
			}
		} catch (CorruptIndexException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(logger.isInfoEnabled()) logger.info("load index successfully from path: " + ApplicationSetup.getProperty(
				"Lucene.indexDirectory", "./index"));
		return searcher;
		
	}
	
	static String docidField = null;
	static private String getIdFieldName() {
		if(docidField ==null){
			docidField = ApplicationSetup.getProperty(
					"TrecDocTags.idtag", "DOCNO");
		}
		return docidField;
	}
	
	public static void searchDemo() throws Exception{
		String fieldstr = ApplicationSetup.getProperty(
				"Lucene.SearchField", "title,content");
		
		String[] searchFeilds = fieldstr.split("\\s*,\\s*");
		Analyzer analyzer = AnalyzerManager.getFromPropertyFile();
		
		String postProcess = ApplicationSetup.getProperty(
				"Lucene.PostProcess", "");
		String postProcesses[] = postProcess.split("\\s*,\\s*");
		Searcher searcher = getSearcheFromPropertyFile();
		float len = searcher.getAverageLength(searchFeilds[0]);
		System.out.println("the average length of filed: " + searchFeilds[0] + " is " + len);
		BufferedReader br = StreamGenerator.getConsoleReader();
		System.out.print("query: ");
		String line = null;
		while((line = br.readLine()) != null){
			Query query = RMultiFieldQueryParser.parse(line, searchFeilds,
					analyzer);
			TopDocs topDocs = searcher.search(query, 20);
			for (int i = 0; i < topDocs.totalHits; i++) {
				int docid = topDocs.scoreDocs[i].doc;
				Document doc = null;
				String filename = null;
				try {
					doc = searcher.doc(docid);
					filename = doc.get(getIdFieldName());
					if(filename.matches("10\\.1039/.*")){
						filename = filename.toLowerCase();
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				StringBuilder sbuffer = new StringBuilder();
				float score = topDocs.scoreDocs[i].score;
				sbuffer.append(filename.trim());
				sbuffer.append(" ");
				
				sbuffer.append(i);
				sbuffer.append(" ");
				sbuffer.append(score);
				System.out.println(sbuffer);
			}
			System.out.print("query: ");
		}
	}
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		searchDemo();
	}

}
