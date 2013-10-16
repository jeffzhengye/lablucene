package org.apache.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.ISManager;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.util.stream.StreamGenerator;


public class Docno2DocInnerID {
	static Logger logger = Logger.getLogger(Docno2DocInnerID.class);
	static Searcher searcher = ISManager.getSearcheFromPropertyFile();
	static Random r  = new Random();
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		convert();

	}

	public static void convert() {
		try {
			BufferedReader br = Files
					.openFileReader(ApplicationSetup.TREC_QRELS);
			Vector<String> buf = new Vector<String>();
			Vector<String> idbuf = new Vector<String>();
			String str = null;
			while ((str = br.readLine()) != null) {
				if (str.length() > 0 && !str.startsWith("#")) {
					idbuf.add(str + ".innerID");
					buf.add(str);
				}
			}

			br.close();
			for (int i = 0; i < buf.size(); i++) {
				BufferedWriter wt = StreamGenerator.getBufferFileWriter(idbuf
						.get(i), 2);
				String lines[] = org.dutir.util.Files.readLinesFromFile(
						new File(buf.get(i)), "UTF8");
				for (String line : lines) {
					StringTokenizer stk = new StringTokenizer(line);
					String queryid = stk.nextToken(); //field 1
					wt.write(queryid);
					wt.write(" " + stk.nextToken()); //field 2
					String docno = stk.nextToken(); //field 3
					wt.write(" " + getDocId(docno));
					while (stk.hasMoreElements()) {
						String term = stk.nextToken();
						wt.write(" " + term);
						
					}
					wt.newLine();
				}
				wt.close();
			}

		} catch (IOException ioe) {
			if (ApplicationSetup.TREC_QRELS == null) {
				logger
						.fatal(
								"An error occured while initialising the qrels file path",
								ioe);
			} else {
				logger.fatal(
						"An error occured while initialising the qrels file at:"
								+ ApplicationSetup.TREC_QRELS, ioe);
			}
			return;
		}
	}

	static String idtag = ApplicationSetup.getProperty("TrecDocTags.idtag",
			"DOCNO");

	
	/**
	 * 
	 * @param docIds
	 * @param effDocuments
	 * @return return the inner id of a doc
	 */
	protected static String getDocId(String docno) {
		try {
			TermDocs tdocs = searcher.getIndexReader().termDocs(new Term(idtag, docno));
			if(tdocs.next()){
				return "" + tdocs.doc();
			}else {
				logger.warn("doc |" + docno + "| do not exist.");
			} 
		} catch (IOException e) {
			e.printStackTrace();
			
		}
		return "unknown"+ Math.abs(r.nextLong());
	}
	/**
	 * 
	 * @param docIds
	 * @param effDocuments
	 * @return return the inner id of a doc
	 */
	protected static String getDocId1(String docno) {
		Query q = new TermQuery(new Term(idtag, docno));
		TopDocs topdocs;
		try {
			topdocs = searcher.search(q, 1);
			if (topdocs.totalHits < 1) {
				logger.warn("doc |" + docno + "| do not exist.");
			} else {
				int docid = topdocs.scoreDocs[0].doc;
				return "" + docid;
			}
		} catch (IOException e) {
			e.printStackTrace();
			
		}
		return "unknown" + Math.abs(r.nextLong());
	}
	
	
}
