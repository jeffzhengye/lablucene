package org.apache.lucene.postProcess.termselector;

import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectFloatHashMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.math.distribution.NormalDistributionImpl;
import org.apache.log4j.Logger;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.IndexUtility;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Distance;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.util.Math;
import org.dutir.util.Normalizer;
import org.apache.lucene.postProcess.MATF;

public class TFTranTermSelector extends TermSelector {
	private static Logger logger = Logger.getLogger(ProxTermSelector.class);
	int winSize = Integer.parseInt(ApplicationSetup.getProperty(
			"ProxTermSelector.winSize", "50"));
	float normF = Float.parseFloat(ApplicationSetup.getProperty(
			"ProxTermSelector.normPow", "0.5"));
	IndexUtility indexUtil = null;
	TermSelector selector = null;
	static int proxType = Integer.parseInt(ApplicationSetup.getProperty(
			"ProxTermSelector.proxType", "2"));

	// @Override
	// public void assignTermWeights(int[] docids, float[] scores,
	// QueryExpansionModel QEModel) {
	// org.dutir.util.symbol.MapSymbolTable symTable = new
	// org.dutir.util.symbol.MapSymbolTable();
	// String qts[] = this.originalQueryTermidSet.toArray(new String[0]);
	// for (int i = 0; i < qts.length; i++)
	// symTable.getOrAddSymbol(qts[i]);
	// TermAssociation tass = TermAssociation.built(this.searcher,
	// this.topDoc, symTable, field, winSize);
	//
	// selector = TermSelector.getTermSelector(
	// "DFRTermSelector", searcher);
	// // selector.setResultSet(this.topDoc);
	// selector.setOriginalQueryTerms(this.originalQueryTermidSet);
	// selector.setField(this.field);
	// selector.assignTermWeights(docids, scores, QEModel);
	// ExpansionTerm[] expTerms = selector.getMostWeightedTerms(selector
	// .getNumberOfUniqueTerms());
	// if (indexUtil == null)
	// indexUtil = new IndexUtility(this.searcher);
	//
	//
	// float idfs[] = getIDF(qts);
	// float proxW[] = new float[expTerms.length];
	// for (int i = 0; i < expTerms.length; i++) {
	// proxW[i] = 0;
	// for (int j = 0; j < qts.length; j++) {
	// // proxW[i] += tass.conditionProb(expTerms[i].getTerm(), qts[j])
	// // * idfs[j];
	// proxW[i] += tass.conditionProb(expTerms[i].getTerm(), qts[j]);
	// }
	// }
	//
	// // norm(2, proxW);
	// Normalizer.normN(proxW, normF);
	// // print(proxW);
	//
	// float totalW = org.dutir.util.Arrays.sum(proxW);
	//
	// QEModel.setTotalDocumentLength(totalW);
	// for (int i = 0; i < expTerms.length; i++) {
	// // expTerms[i].setWeightExpansion(expTerms[i].getWeightExpansion()
	// // );
	// // revise the weight
	// // expTerms[i].setWeightExpansion(expTerms[i].getWeightExpansion()
	// // * proxW[i]);
	//
	// TermsCache.Item item = getItem(expTerms[i].getTerm());
	// float TF = item.ctf;
	// float weight = QEModel.score(proxW[i], TF);
	// expTerms[i].setWeightExpansion(weight);
	// }
	//
	// Arrays.sort(expTerms);
	// this.termMap = new HashMap<String, ExpansionTerm>(1024);
	// float normaliser = expTerms[0].getWeightExpansion();
	// for (ExpansionTerm term : expTerms) {
	// term.setWeightExpansion(term.getWeightExpansion() / normaliser);
	// termMap.put(term.getTerm(), term);
	// }
	//
	// }

	public void assignTermWeights(int[] docids, float[] scores,
			QueryExpansionModel QEModel) {
		if (indexUtil == null)
			indexUtil = new IndexUtility(this.searcher);
		GeoExpansionTerm posCBTerm = new GeoExpansionTerm(
				this.originalQueryTermidSet);
		// get all terms in positive documents as candidate expansion terms
		// for each positive feedback document
		String sterms[][] = new String[docids.length][];
		int termFreqs[][] = new int[docids.length][];
		TermPositionVector tfvs[] = new TermPositionVector[docids.length];
		for (int i = 0; i < docids.length; i++) {
			TermPositionVector tfv = null;
			try {
				tfv = (TermPositionVector) this.searcher.getIndexReader()
						.getTermFreqVector(docids[i], field);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (tfv == null)
				logger.warn("document " + docids[i] + " not found");
			else {
				String strterms[] = sterms[i] = tfv.getTerms();
				int freqs[] = termFreqs[i] = tfv.getTermFrequencies();
				tfvs[i] = tfv;
				posCBTerm.insert(strterms, freqs, tfv);
			}
		}
//		posCBTerm.compute();

		// ///////////////////////////////////////////////////////////////
		
		String qts[] = this.originalQueryTermidSet.toArray(new String[0]);
		selector = TermSelector.getTermSelector("DFRTermSelector", searcher);
		((DFRTermSelector) selector).setFscore(posCBTerm.fscore);
		// selector.setResultSet(this.topDoc);
		selector.setOriginalQueryTerms(this.originalQueryTermidSet);
		selector.setField(this.field);
//		selector.assignTermWeights(docids, scores, QEModel);
		selector.assignTermWeights(sterms, termFreqs, tfvs, QEModel);
//		ExpansionTerm[] expTerms = selector.getMostWeightedTerms(selector
//				.getNumberOfUniqueTerms());
		this.termMap = selector.termMap;
	}
	
	void print(float[] array) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			buf.append(array[i] + "\t");
		}
		logger.debug(buf.toString());
	}

	private void norm(int type, float[] scores) {
		if (type == 0) {
			return;
		} else if (type == 1) {
			Normalizer.norm_MaxMin_0_1(scores);
		} else if (type == 2) {
			Normalizer.norm2(scores);
		} else if (type == 3) {
			Normalizer.norm2(scores);
			Normalizer.norm_MaxMin_0_1(scores);
		}
	}

	float[] getIDF(String qts[]) {
		float idfs[] = new float[qts.length];
		for (int i = 0; i < idfs.length; i++) {
			idfs[i] = Idf.idfBM25(indexUtil.getDF(qts[i], this.field),
					indexUtil.maxDoc());
			// idfs[i] = Idf.
		}
		return idfs;
	}

	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getInfo() {
		return "Prox" + proxType + "w=" + winSize + "F=" + normF;
	}

	static double sd = Double.parseDouble(ApplicationSetup.getProperty(
			"ProxTermSelector.sd", "100"));
	static NormalDistributionImpl nDist = new NormalDistributionImpl(0, sd);

	static double getTimes(int[] p1, int[] p2, int winSize, int docLen) {
		if (proxType == 1) {// HAL
			return Distance.unorderHALTimes(p1, p2, winSize);
		} else if (proxType == 2) {// unorder
			return Distance.noTimes(p1, p2, winSize, docLen);
		} else if (proxType == 3) { //
			return Distance.bigramFrequency(p1, p2, winSize);
		} else if (proxType == 4) {
			return Distance.unorderGaussianTimes(p1, p2, winSize, nDist);
		}
		return 0;
	}

	class GeoExpansionTerm {
		String qterms[] = null;
		float freqs[] = null;
		int position[][] = null;
		int queryNum = 0;
		float idfs[] = null;
		float positiveLen;

		/**
		 * accumulated term frequency
		 */
		TObjectDoubleHashMap<String> fscore = new TObjectDoubleHashMap<String>();
		private HashSet<String> termset;

		public GeoExpansionTerm(HashSet<String> originalQueryTermidSet) {
			this(originalQueryTermidSet.toArray(new String[0]));
			this.termset = originalQueryTermidSet;
		}

		public GeoExpansionTerm(String queryterms[]) {
			this.qterms = queryterms;
			this.queryNum = queryterms.length;
			Arrays.sort(qterms);
			freqs = new float[queryNum];
			// idfs = new float[queryNum];
			// for(int i=0; i < queryNum; i++){
			// idfs[i] = idf(qterms[i]);
			// }
			idfs = getIDF(this.qterms);
		}

		public GeoExpansionTerm(String queryterms[], float positiveLen) {
			this(queryterms);
			this.positiveLen = positiveLen;
		}

		private void reset() {
			Arrays.fill(freqs, 0);
			position = new int[queryNum][];
			for (int i = 0; i < queryNum; i++) {
				position[i] = new int[0];
			}
		}

		public void insert(String terms[], int tfreqs[], TermPositionVector vec) {
			reset();
			int docLen = 0;
			for (int i = 0; i < terms.length; i++) { // extract all terms in
														// both the query
				docLen += tfreqs[i];
				if (termset.contains(terms[i])) {
					int pos = Arrays.binarySearch(qterms, terms[i]);
					freqs[pos] = tfreqs[i];
					position[pos] = vec.getTermPositions(i);
					assert freqs[pos] == position[pos].length;
				}
			}

			for (int i = 0; i < terms.length; i++) {
				int[] termPos = vec.getTermPositions(i);
				assert tfreqs[i] == termPos.length;
				// double count = idf(terms[i]) * count(termPos, position,
				// winSize, docLen);
				double count = count(termPos, position, winSize, docLen);
				fscore.adjustOrPutValue(terms[i], count, count);
			}
		}

		/**
		 * toDo: debug the starting position in TermPositionVector.
		 * 
		 * @param start
		 * @return
		 */
		private double count(int[] tPos, int[][] queryPoss, int winSize,
				int docLen) {
			double retValue = 0;
			for (int i = 0; i < queryPoss.length; i++) {
				double count = getTimes(tPos, queryPoss[i], winSize, docLen);
				retValue += idfs[i] * count;
				// retValue += count;
			}
			// float selfCount = idf() * Distance.noTimes(tPos, tPos, winSize,
			// docLen);
			return retValue;
		}

		private float idf(String string) {
			// return 1;
			try {
				TermsCache.Item item = getItem(string);
				// int df = searcher.docFreq(new Term(field, string));
				float df = item.df;
				int maxdoc = searcher.maxDoc();
				return (float) java.lang.Math.min(1,
						java.lang.Math.log10(maxdoc / df) / 5.0f);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return 0;
		}

		public void compute() {
			String keys[] = fscore.keys(new String[0]);
			double values[] = fscore.getValues();
			double sum = org.dutir.util.Arrays.sum(values);
			double max = org.dutir.util.Arrays.findMax(values);
			fscore.clear();
			for (int i = 0; i < keys.length; i++) {
				fscore.put(keys[i], values[i] / max);
			}
		}

		public double getScore(String term) {
			double retValue = 0;
			retValue = this.fscore.get(term);
			return retValue;
		}
	}

	public static void main(String args[]) {
		float v[] = { 1.1f, 1.2f, 1.3f };

	}

}
