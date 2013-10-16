/**
 * 
 */
package org.apache.lucene.postProcess;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RTermQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;

/**
 * @author yezheng
 * 
 */
public class DistanceRelationPrinter extends QueryExpansion {

	static String idtag = ApplicationSetup.getProperty("TrecDocTags.idtag",
			"DOCNO");
	
	static TRECQrelsInMemory trecR = new TRECQrelsInMemory();
	static FileWriter distanceRelation = null;
	static String field = ApplicationSetup.getProperty("Lucene.SearchField",
			"content");

	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher);
		if (distanceRelation == null) {
			try {
				distanceRelation = new FileWriter("distanceRelation.txt");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			String queryid = query.getTopicId();
			String[] qterms = getQueryTerms();
			Arrays.sort(qterms);
			float[] idfs = getIDFs(qterms);

			ScoreDoc docs[] = this.ScoreDoc;
			for (int i = 0; i < 30 && i < docs.length; i++) {
				Document document = searcher.doc(docs[i].doc);
				float score = docs[i].score;
				String id = document.get(idtag);

				boolean rtag = trecR.isRelevant(queryid, id);
				StringBuilder buf = new StringBuilder();
				buf.append("id:" + queryid + ", score:" + score + ", qNum: "
						+ qterms.length);
				TermPositionVector vec = (TermPositionVector) searcher
						.getIndexReader().getTermFreqVector(docs[i].doc, field);
				String[] terms = vec.getTerms();
				int length = 0;
				int freqs[] = vec.getTermFrequencies();
				for (int k = 0; k < freqs.length; k++) {
					length += freqs[k];
				}
				float[] averPos = new float[qterms.length];
				Arrays.fill(averPos, length);
				int[][] minPos = new int[qterms.length][];

				for (int k = 0; k < terms.length; k++) {
					String term = terms[k];
					int qid = Arrays.binarySearch(qterms, term);
					if (qid > -1) {
						int[] pos = vec.getTermPositions(k);
						averPos[qid] = org.dutir.util.Arrays.aver(pos);
						minPos[qid] = pos;
					}
				}
				buf.append(", " + length + "\n\t");

				int maxP = org.dutir.util.Arrays.findMaxPos(idfs);
				float averDist = 0;
				float miniDist = 0;

				for (int j = 0; j < averPos.length; j++) {
					if (j != maxP) {
						averDist += Math.abs(averPos[maxP] - averPos[j]);
						buf.append("a:" + Math.abs(averPos[maxP] - averPos[j])
								+ ", ");

						// //////////////////////////////////////////////////////
						float mini = miniDist(minPos[maxP], minPos[j], length);
						miniDist += mini;
						buf.append("m:" + mini + ", ");
					}
				}
				buf.append(" ta:" + averDist / averPos.length);
				buf.append(", tm:" + miniDist / averPos.length);
				if (rtag) { // relevant
					buf.append(" Re\n");
				} else {
					buf.append(" non-R\n");
				}
				distanceRelation.write(buf.toString());
				distanceRelation.flush();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return topDoc;
	}

	private float miniDist(int[] termPositions, int[] termPositions2, int length) {
		float min = Float.POSITIVE_INFINITY;
		if (termPositions == null || termPositions2 == null) {
			return length;
		}
		for (int i = 0; i < termPositions.length; i++) {
			for (int j = 0; j < termPositions2.length; j++) {
				float tmp = Math.abs(termPositions[i] - termPositions2[j]);
				if (min > tmp) {
					min = tmp;
				}
			}
		}
		return min;
	}

	private float[] getIDFs(String[] qterms) {
		try {
			float idfs[] = new float[qterms.length];
			float dnum = this.searcher.maxDoc();
			for (int i = 0; i < qterms.length; i++) {
				int df = this.searcher.docFreq(new Term(field, qterms[i]));
				idfs[i] = (float) Idf
						.log((float) ((dnum - df + 0.5) / (df + 0.5)));
			}
			return idfs;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private String[] getQueryTerms() {

		ArrayList<String> list = new ArrayList<String>();
		for (int i = 0; i < bclause.length; i++) {
			// BooleanClause bc = bclause[i];
			Query rtq = bclause[i].getQuery();
			if (rtq instanceof RTermQuery) {
				list.add(((RTermQuery) rtq).getTerm().text());

			}else{
				logger.warn("this query is not expected for QE");
			}
		}
		return list.toArray(new String[0]);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
