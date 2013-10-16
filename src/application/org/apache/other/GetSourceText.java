/**
 * 
 */
package org.apache.other;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.ISManager;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.util.Arrays;
import org.dutir.util.Strings;
import org.dutir.util.stream.StreamGenerator;

/**
 * @author yezheng
 *
 */
public class GetSourceText {
	static Logger logger  = Logger.getLogger(GetSourceText.class);
	static String idtag = ApplicationSetup.getProperty("TrecDocTags.idtag",
			"DOCNO");
	static String field = ApplicationSetup.getProperty("Lucene.SearchField",
			"content");
//		ApplicationSetup.getProperty("Lucene.SearchField",			"content");
	
	private static ArrayList<String> loadFeedback(String filename) {
		ArrayList<String> list = new ArrayList<String>();
		try {
			BufferedReader br = Files.openFileReader(filename);
			String line = null;
			// for each line in the feedback (qrels) file
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0) {
					continue;
				}
				list.add(line);


			}

			br.close();
			return list;
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}
	
	public static String getTextFromDocids(String docExtID, Searcher searcher) {
		try {
			TopDocs topdocs = searcher.search(new TermQuery(new Term(
					idtag, docExtID)), 1);
			if (topdocs.totalHits < 1) {
				logger.warn("doc |" + docExtID + "| do not exist.");
			} else {
				int docid = topdocs.scoreDocs[0].doc;

				TermPositionVector tfv = null;

				tfv = (TermPositionVector) searcher.getIndexReader()
						.getTermFreqVector(docid, field);
				if(tfv != null){
					String[] terms = tfv.getTerms();
					
					int freqs[] = tfv.getTermFrequencies();
					int length = Arrays.sum(freqs);
					String text[] = new String[length];
					float termpos[] = new float[length];
					
					int p =0;
					for (int k = 0; k < terms.length; k++) {
						String term = terms[k];
						
						int[] pos = tfv.getTermPositions(k);
						assert freqs[k] == pos.length;
						
						for(int j=0; j < pos.length; j++){
							text[p] = term;
							termpos[p++] = pos[j];
						}
					}
					int index[] = Arrays.indexSort(termpos);
					StringBuilder buf = new StringBuilder(1024);
					for(int i=0;i < index.length; i++){
						buf.append(text[index[i]] + " ");
					}
//					return Strings.concatenate(text, " ");
					return buf.toString();
					
				}else{
//					TermFreqVector tfv1 = null;
//						tfv1 = searcher.getIndexReader().getTermFreqVector(docid, docidField);
//						
				}

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	static void run(){
		try {
			Searcher searcher = ISManager.getSearcheFromPropertyFile();
			String path = ApplicationSetup.getProperty("Rocchio.Feedback.filename",
					ApplicationSetup.LUCENE_ETC + ApplicationSetup.FILE_SEPARATOR
							+ "feedback");
			ArrayList<String> list = loadFeedback(path);
			BufferedWriter writer = StreamGenerator.getBufferFileWriter("xiaoshi", 2);
			for(String line : list){
				// split line into space-separated pieces
				String[] pieces = line.split("\\s+");
				
				// grab topic id
				String topId = pieces[0];
				// grab docno
				String docNo = pieces[2].trim();
				String text = getTextFromDocids(docNo, searcher);
				writer.write(line + " " + text + "\n");
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	static void testSingle(String ids[]){
		try {
			Searcher searcher = ISManager.getSearcheFromPropertyFile();
			for(String id : ids){
				String text = getTextFromDocids(id, searcher);
				if(text == null){
					System.out.println(id + "  does not exist");
				}else{
					System.out.println(id + " exists");
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void testMemory()
	{
		float meg = 1024*1024;
		Runtime rt = Runtime.getRuntime();
		long smax = rt.maxMemory();
		long sfree = rt.freeMemory();
		System.out.println("smax=" + smax/meg + " ,sfree=" + sfree/meg) ;
		int length = 35000;
		
		int arrays[] = new int[length * (length-1)/2];
		
		smax = rt.maxMemory();
		sfree = rt.freeMemory();
		System.out.println("emax=" + smax/meg + " ,efree=" + sfree/meg) ;
	}
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) {
//		testSingle(args);
//		run();
		testMemory();
	}
}
