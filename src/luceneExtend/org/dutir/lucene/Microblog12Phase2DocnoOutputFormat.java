package org.dutir.lucene;

import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import org.apache.log4j.Logger;
import org.apache.lucene.OutputFormat;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.util.CharOper;
import org.dutir.util.Strings;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.Language;

public 	class Microblog12Phase2DocnoOutputFormat implements OutputFormat {
	Searcher searcher;
	protected static final Logger logger = Logger.getLogger(MicroblogOutput.class);
	
	static String profileDirectory = "./profiles";
	static Detector detector = null;
	static {
		try {
			DetectorFactory.loadProfile(profileDirectory);
			detector = DetectorFactory.create();
		} catch (LangDetectException e) {
			e.printStackTrace();
		}
	}
	
	
	public Microblog12Phase2DocnoOutputFormat(Searcher searcher) {
		this.searcher = searcher;
	}

	/**
	 * Prints the results for the given search request, using the specified
	 * destination.
	 * 
	 * @param pw
	 *            PrintWriter the destination where to save the results.
	 * @param q
	 *            SearchRequest the object encapsulating the query and the
	 *            results.
	 */
	public void printResults(String queryID, final PrintWriter pw,
			final TopDocCollector collector) {
		TopDocs topDocs = collector.topDocs();
		int len = topDocs.totalHits;
		logger.info(queryID + " Total hits: " + len + " " + topDocs.scoreDocs.length + " " + end);
		int maximum = Math.min(topDocs.scoreDocs.length, end);

		// if (minimum > set.getResultSize())
		// minimum = set.getResultSize();
		final String queryIdExpanded = queryID + " ";
		final String methodExpanded = " " + runName
				+ ApplicationSetup.EOL;
		StringBuilder sbuffer = new StringBuilder();
		// the results are ordered in descending order
		// with respect to the score.
		
		int limit = 100000;
		
		int counter = 0;
		long  querydate = Long.parseLong(System.getProperty("BEFOREDATE"));
		long newest_querydate = Long.parseLong(System.getProperty("NEWEST_BEFOREDATE"));
		ArrayList<Tweet> results = new ArrayList<Tweet>(1014*100);
		for (int i = start; i < maximum && counter < limit; i++) {
			int docid = topDocs.scoreDocs[i].doc;
			float score = topDocs.scoreDocs[i].score;
			//////////////////////////
			Document doc = null;
			String filename = null;
			String decision = "yes";
			String tweet = null;
			try {
				doc = searcher.doc(docid);
				tweet = doc.get("content"); 
				filename = doc.get(docidField);
				if(!isEnglish(tweet)){
//					if(counter < 50){
//						logger.info("Rank " + counter + " || " + tweet);
//					}
					continue;
				}
				if(isRTTweet(tweet)){
					continue;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
//			sbuffer.append(" " + time + " " + System.getProperty("BEFOREDATE"));
			
			long tweetdate = Long.parseLong(filename);
			
			if(tweetdate < querydate || tweetdate > newest_querydate) continue;
			
			/////////////decision ////////////////////////////
			if(counter > 50){
				decision = "no";
			}
			results.add(new Tweet(queryID, filename, score, decision, tweetdate, tweet));
			////////////////////////////
//			sbuffer.append(queryIdExpanded);
//			sbuffer.append(filename);
//			sbuffer.append(" ");
////			sbuffer.append(counter);
////			sbuffer.append(" ");
//			sbuffer.append(score);
//			sbuffer.append(" " + decision);
//			sbuffer.append(methodExpanded);
			
			counter++;
		}
		Collections.sort(results);
		pw.write(Strings.concatenate(results.toArray(new Tweet[0]), methodExpanded) + methodExpanded);
//		pw.write(sbuffer.toString());
	}
	
	
	private static void tweetExtract(String text){
		
	}
	
	private static boolean isRTTweet(String text){
		if(text !=null && text.startsWith("RT")){
			return true;
		}
		return false;
	}
	/**
	 * 
	 * @param text
	 */
	private static boolean isEnglish(String text){
		int len = text.length();
		int counter =0;
		for(int i= 0; i < len; i++){
			char c = text.charAt(i);
			if(CharOper.isEnglishLetter(c) || CharOper.isBlank(c) || Character.isDigit(c)){
				counter ++;
			}
		}
		double prob = counter/(double)len;
		if(prob >= 0.7){
			return true;
		}else{
			return false;
		}
	}
	
	static class Tweet implements Comparable<Tweet>{
		String queryID = "";
		float score; String decision =null;
		String fileID; String tweet ="";
		long longID;
		public Tweet(String queryID, String fileID, float score, String decision, long longID, String tweet){
			this.queryID = queryID; this.fileID = fileID; this.score = score;
			this.decision = decision; this.longID = longID; this.tweet= tweet;
		}
		
		public String toString(){
			StringBuilder sbuffer = new StringBuilder();
			sbuffer.append(queryID +" ");
			sbuffer.append(fileID);
			sbuffer.append(" ");
			sbuffer.append(score);
			sbuffer.append(" " + decision);
			return sbuffer.toString();
		}
		
		public int compareTo(Tweet o) {
			if(this.longID > o.longID)return 1;
			else if(this.longID < o.longID)return -1;
			return 0;
		} 
	}
	public static void main(String args[]) throws LangDetectException{
		String tweet = "BBC World Service plans 650 job cuts      (AP) - AP - The BBC said Wednesday that it plans to cut 650 jobs, more tha... http://ow.ly/1b2u20";
		detector.append(tweet);
		String lang = detector.detect();
		ArrayList<Language> list = detector.getProbabilities();
		System.out.println(lang);
		System.out.println(list);
		System.out.println(isEnglish(tweet));
	}
	
}
