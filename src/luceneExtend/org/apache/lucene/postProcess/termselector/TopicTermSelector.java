/**
 * 
 */
package org.apache.lucene.postProcess.termselector;

import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.postProcess.termselector.LatentDirichletAllocation.GibbsSample;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Rounding;
import org.dutir.lucene.util.TermsCache;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.util.Arrays;
import org.dutir.util.Normalizer;
import org.dutir.util.symbol.MapSymbolTable;
import org.dutir.util.symbol.SymbolTable;

/**
 * @author yezheng
 * 
 */

public class TopicTermSelector extends TermSelector {
	private static Logger logger = Logger.getLogger(TopicTermSelector.class);
	static boolean LanguageModel = Boolean.parseBoolean(ApplicationSetup
			.getProperty("Lucene.Search.LanguageModel", "false"));
	static int strategy = Integer.parseInt(ApplicationSetup.getProperty(
			"TopicTermSelector.strategy", "3"));
	static boolean expTag = Boolean.parseBoolean(ApplicationSetup.getProperty(
			"TopicTermSelector.expTag", "false"));
	static int expNum = Integer.parseInt(ApplicationSetup.getProperty(
			"TopicTermSelector.expNum", "15"));
	static int expDoc = Integer.parseInt(ApplicationSetup.getProperty(
			"TopicTermSelector.expDoc", "10"));

	static boolean associationTag = Boolean.parseBoolean(ApplicationSetup
			.getProperty("TopicTermSelector.associationTag", "false"));

	static float threshold = Float.parseFloat(ApplicationSetup.getProperty(
			"TopicTermSelector.threshold", "0.2"));
	static float lambda = Float.parseFloat(ApplicationSetup.getProperty(
			"TopicTermSelector.lambda", "0.5"));
	static float beta = Float.parseFloat(ApplicationSetup.getProperty(
			"TopicTermSelector.beta", "0.3"));
	static int winSize = Integer.parseInt(ApplicationSetup.getProperty(
			"Association.winSize", "50"));

	static Random RANDOM = new Random(43);
	static short NUM_TOPICS = Short.parseShort(ApplicationSetup.getProperty(
			"TopicTermSelector.NUM_TOPICS", "5"));
	
//	static double DOC_TOPIC_PRIOR = 0.01;
	static double TOPIC_WORD_PRIOR = 0.01;
	static double DOC_TOPIC_PRIOR = 2d/NUM_TOPICS;
	static int numSamples = 30;
	static int burnin = 30;
	static int sampleLag = 10;

	static int BURNIN_EPOCHS = 10;
	static int SAMPLE_LAG = 30;
	static int NUM_SAMPLES = 30;

	protected int EXPANSION_MIN_DOCUMENTS;
	float dscores[];

	TObjectIntHashMap<String> dfMap = null;

	public TopicTermSelector() {
		super();
		this.setMetaInfo("normalize.weights", "true");
		this.EXPANSION_MIN_DOCUMENTS = Integer.parseInt(ApplicationSetup
				.getProperty("expansion.mindocuments", "2"));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.termselector.TermSelector#assignTermWeights
	 * (int[], org.apache.lucene.postProcess.QueryExpansionModel)
	 */
	@Override
	public void assignTermWeights(int[] docids, float scores[],
			QueryExpansionModel QEModel) {
		dscores = new float[scores.length];
		System.arraycopy(scores, 0, dscores, 0, scores.length);

		
		if(LanguageModel){
		indriNorm(dscores);
		}
		Normalizer.norm2(dscores);
//		logger.info("sum of doc weights:" + Arrays.sum(dscores));
//		Normalizer.norm_MaxMin_0_1(dscores);
//		if(logger.isInfoEnabled()) 
//		{
//			StringBuffer buf = new StringBuffer();
//			for(int i=0; i < dscores.length; i++){
//				buf.append("" + dscores[i] + ", ");
//			}
//			logger.info("4.doc weights:" + buf.toString());
//		}
		String[][] termCache = null;
		int[][] termFreq = null;
		termMap = new HashMap<String, ExpansionTerm>();
		this.feedbackSetLength = 0;
		termCache = new String[docids.length][];
		termFreq = new int[docids.length][];
		dfMap = new TObjectIntHashMap<String>();
		for (int i = 0; i < docids.length; i++) {
			int docid = docids[i];
			TermFreqVector tfv = null;
			try {
				tfv = this.searcher.getIndexReader().getTermFreqVector(docid,
						field);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (tfv == null)
				logger.warn("document " + docid + " not found, field=" + field);
			else {
				String strterms[] = tfv.getTerms();
				int freqs[] = tfv.getTermFrequencies();
				termCache[i] = strterms;
				termFreq[i] = freqs;
			}
		}

		// //////////LDA clustering/////////////////////
		MapSymbolTable SYMBOL_TABLE = new MapSymbolTable();
		int[][] DOC_WORDS = new int[docids.length][];
		int querytermid[] = new int[this.originalQueryTermidSet.size()];
		int pos = 0;
		int backids[] = new int[0];
		if (expTag) {
			int reallExp = Math.min(expDoc, docids.length);
			int expDocs[] = new int[reallExp];
			float expscores[] = new float[reallExp];
			System.arraycopy(docids, 0, expDocs, 0, reallExp);
			System.arraycopy(scores, 0, expscores, 0, reallExp);
			TermSelector selector = TermSelector.getTermSelector(
					"RocchioTermSelector", this.searcher);
			selector.setField(field);
			selector.setMetaInfo("normalize.weights", "false");
			selector.setOriginalQueryTerms(originalQueryTermidSet);
			selector.assignTermWeights(expDocs, expscores, QEModel);

			HashMap<String, ExpansionTerm> map = selector
					.getMostWeightedTermsInHashMap(expNum);
			assert map.size() <= expNum;
			Set<String> keyset = new HashSet<String>(map.keySet());
			keyset.addAll(this.originalQueryTermidSet);
			querytermid = new int[keyset.size()];
			for (String term : keyset) {
				int id = SYMBOL_TABLE.getOrAddSymbol(term);
				querytermid[pos++] = id;
			}

			// //////////////////////////////////////
			// ExpansionTerm exTerms[] =
			// selector.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
			// TIntHashSet set = new TIntHashSet();
			// for(int i = exTerms.length-1; i > 0 && i > exTerms.length-1
			// -expNum; i--){
			// int id = SYMBOL_TABLE.getOrAddSymbol(exTerms[i].getTerm());
			// set.add(id);
			// }
			// backids = set.toArray();
			// /////////////////////////////////////

		}else{
			for (String term : this.originalQueryTermidSet) {
				int id = SYMBOL_TABLE.getOrAddSymbol(term);
				querytermid[pos++] = id;
			}
		}

		for (int i = 0; i < docids.length; i++) {
			int len = Arrays.sum(termFreq[i]);
			DOC_WORDS[i] = new int[len];
			pos = 0;
			for (int j = 0; j < termCache[i].length; j++) {
				String term = termCache[i][j];
				dfMap.adjustOrPutValue(term, 1, 1);
				int id = SYMBOL_TABLE.getOrAddSymbol(term);
				for (int k = 0; k < termFreq[i][j]; k++) {
					DOC_WORDS[i][pos++] = id;
				}
			}
			assert len == pos;
		}
		for (int[] words : DOC_WORDS)
			Arrays.permute(words, RANDOM);
		// LdaReportingHandler handler = new LdaReportingHandler(SYMBOL_TABLE);

		// get a co-occurrence lookup map. ////////////
		MapSymbolTable coTable = SYMBOL_TABLE.clone();
		TermAssociation tAss = null;
		if (associationTag) {
			tAss = TermAssociation.built(this.searcher, this.topDoc, coTable,
					this.field, winSize);
		}

		// ////////////////////////////////////////////

		// LatentDirichletAllocation.GibbsSample sample =
		// LatentDirichletAllocation
		// .gibbsSampler(DOC_WORDS, NUM_TOPICS, DOC_TOPIC_PRIOR,
		// TOPIC_WORD_PRIOR, BURNIN_EPOCHS, SAMPLE_LAG,
		// NUM_SAMPLES, RANDOM, querytermid, backids, null, tAss);
		LatentDirichletAllocation.GibbsSample sample = LatentDirichletAllocation
				.gibbsSampler(DOC_WORDS, NUM_TOPICS, DOC_TOPIC_PRIOR,
						TOPIC_WORD_PRIOR, BURNIN_EPOCHS, SAMPLE_LAG,
						NUM_SAMPLES, RANDOM, querytermid, backids, null);

		LatentDirichletAllocation lda = sample.lda();
		short[][] qsamples = lda.sampleTopics(querytermid, numSamples, burnin,
				sampleLag, RANDOM);

		float theta[] = new float[NUM_TOPICS];
		java.util.Arrays.fill(theta, 0);
		for (int i = 0; i < qsamples.length; i++) {
			for (int j = 0; j < qsamples[i].length; j++) {
				theta[qsamples[i][j]]++;
			}
		}
		float total = querytermid.length * numSamples;
		for (int i = 0; i < theta.length; i++) {
			theta[i] = theta[i] / total;
		}

		// handler.fullReport(sample,5,2,true);
		// //////////////LDA clusteringend////////////////
		ExpansionTerm[] allTerms = selectTerm(SYMBOL_TABLE, sample, QEModel,
				theta, querytermid, lda, tAss);
//		logger.info( " feedback term: " + this.termMap.size());
	}

	float[] sampleTheta(int numTopics, LatentDirichletAllocation lda,
			int[] words) {
		short[][] qsamples = lda.sampleTopics(words, numSamples, burnin,
				sampleLag, RANDOM);

		float theta[] = new float[numTopics];
		java.util.Arrays.fill(theta, 0);

		for (int i = 0; i < qsamples.length; i++) {
			for (int j = 0; j < qsamples[i].length; j++) {
				theta[qsamples[i][j]]++;
			}
		}
		float total = words.length * numSamples;
		for (int i = 0; i < theta.length; i++) {
			theta[i] = theta[i] / total;
		}
		return theta;
	}

	float[][] sampleThetas(int times, int numTopics,
			LatentDirichletAllocation lda, int[] words) {
		float retValue[][] = new float[times][];
		for (int i = 0; i < times; i++) {
			retValue[i] = sampleTheta(numTopics, lda, words);
		}
		return retValue;
	}

	float[] sampleThetasAver(int times, int numTopics,
			LatentDirichletAllocation lda, int[] words) {
		float[][] aver = sampleThetas(times, numTopics, lda, words);
		float retV[] = new float[numTopics];
		for (int i = 0; i < aver.length; i++) {
			for (int j = 0; j < aver[0].length; j++) {
				retV[j] = aver[i][j];
			}
		}
		for (int i = 0; i < numTopics; i++) {
			retV[i] = retV[i] / numTopics;
		}
		return retV;
	}

	private ExpansionTerm[] selectTerm(SymbolTable SYMBOL_TABLE,
			GibbsSample sample, QueryExpansionModel QEModel, float theta[],
			int querytermid[], LatentDirichletAllocation lda,
			TermAssociation tAss) {
		ExpansionTerm[] allTerms = new ExpansionTerm[SYMBOL_TABLE.numSymbols()];

		int index[] = Arrays.indexSort(theta);

		if (logger.isDebugEnabled()) {
			for (int i = 0; i < index.length; i++) {
				float prob = 0;
				for (int j = 0; j < querytermid.length; j++) {
					prob += Idf.log(sample.topicWordProb(index[i],
							querytermid[j]));
				}
				if(logger.isDebugEnabled()) logger.debug("topic: " + index[i] + " - " + theta[index[i]]
						+ ", topicCount: " + sample.topicCount(index[i])
						+ ", prob: " + prob);
			}
			StringBuilder buf = new StringBuilder();
			for (int i = 0, n = sample.numDocuments(); i < n; i++) {
				buf.append(i + ":\t");
				for (int j = 0; j < sample.numTopics(); j++) {
					buf.append(Rounding
							.round(sample.documentTopicProb(i, j), 5)
							+ "\t");
				}
				buf.append("\n");
			}
			if(logger.isDebugEnabled()) logger.debug("doc topic distribution\n" + buf.toString());
		}

		final int len = allTerms.length;
		int maxTopic = index[index.length - 1];
		if(logger.isDebugEnabled()) logger.debug("max topic: " + maxTopic);

		int maxQueryTermId = querytermid[querytermid.length - 1];

		if (strategy == 1) { // take advantage of the topic with the highest
								// prob
			float totalweight = 0;
			int feedbackNum = sample.numDocuments();
			for (int i = 0; i < len; i++) {
				String term = SYMBOL_TABLE.idToSymbol(i);
				TermsCache.Item item = getItem(term);
				float TF = item.ctf;
				float DF = item.df;
				float weight = 0;

				weight = 0;
				for (int d = 0; d < feedbackNum; d++) {
					double docProb = sample.docWordCount(d, i)
							/ (float) sample.documentLength(d);
					if (docProb == 0) {
						continue;
					}
					double topicProb = sample.topicWordProb(maxTopic, i);
					double onedocWeight = (1 - beta) * docProb + beta
							* topicProb;
					QEModel.setTotalDocumentLength(1);
					weight += QEModel.score((float) onedocWeight, TF, DF);
				}
				weight /= feedbackNum;
				if (dfMap.get(term) < EXPANSION_MIN_DOCUMENTS) {
					weight = 0;
				}
				allTerms[i] = new ExpansionTerm(term, 0);
				allTerms[i].setWeightExpansion(weight);
				this.termMap.put(term, allTerms[i]);
				totalweight += weight;
			}

			java.util.Arrays.sort(allTerms);
			// determine double normalizing factor
			float normaliser = allTerms[0].getWeightExpansion();
			for (ExpansionTerm term : allTerms) {
				if (normaliser != 0) {
					term.setWeightExpansion(term.getWeightExpansion()
							/ totalweight);
				}
			}
		} else if (strategy == 2) {// take advantage of the topics with the
									// probabilities higher than a threshold
			float totalweight = 0;
			int feedbackNum = sample.numDocuments();
			for (int i = 0; i < len; i++) {

				String term = SYMBOL_TABLE.idToSymbol(i);
				TermsCache.Item item = getItem(term);
				float TF = item.ctf;float DF = item.df;
				float weight = 0;

				weight = 0;
				for (int d = 0; d < sample.numDocuments(); d++) {
					double docProb = sample.docWordCount(d, i)
							/ (float) sample.documentLength(d);
					if (docProb == 0) {
						continue;
					}

					double topicProb = sample.docWordProb(d, i, theta,
							threshold);
					double onedocWeight = (1 - beta) * docProb + beta
							* topicProb;
					QEModel.setTotalDocumentLength(1);
					
					weight += QEModel.score((float) onedocWeight, TF, DF);
				}

				weight /= feedbackNum;
				if (dfMap.get(term) < EXPANSION_MIN_DOCUMENTS) {
					weight = 0;
				}
				allTerms[i] = new ExpansionTerm(term, 0);
				allTerms[i].setWeightExpansion(weight);
				this.termMap.put(term, allTerms[i]);
				totalweight += weight;
			}

			java.util.Arrays.sort(allTerms);
			// determine double normalizing factor
			float normaliser = allTerms[0].getWeightExpansion();
			for (ExpansionTerm term : allTerms) {
				if (normaliser != 0) {
					term.setWeightExpansion(term.getWeightExpansion()
							/ totalweight);
				}
			}
		} else if (strategy == 3) {// adding term association
			float totalweight = 0;
			int feedbackNum = sample.numDocuments();
			int times = 10;
			float thetas[][] = sampleThetas(times, NUM_TOPICS, lda, querytermid);

			for (int i = 0; i < len; i++) {
				String term = SYMBOL_TABLE.idToSymbol(i);
				TermsCache.Item item = getItem(term);
				float TF = item.ctf;float DF = item.df;
				float weight = 0;

				weight = 0;
				for (int d = 0; d < feedbackNum; d++) {
					double docProb = sample.docWordCount(d, i)
							/ (float) sample.documentLength(d);
					// if(docProb ==0){
					// continue;
					// }

					double topicProb = sample.docWordProb(i, thetas);

					double onedocWeight = (1 - beta) * docProb + beta
							* topicProb;
					QEModel.setTotalDocumentLength(1);
					weight += QEModel.score((float) onedocWeight, TF, DF);
				}

				weight /= feedbackNum;
				if (dfMap.get(term) < EXPANSION_MIN_DOCUMENTS) {
					weight = 0;
				}
				allTerms[i] = new ExpansionTerm(term, 0);
				allTerms[i].setWeightExpansion(weight);
				this.termMap.put(term, allTerms[i]);
				totalweight += weight;
			}

			java.util.Arrays.sort(allTerms);
			// determine double normalizing factor
			float normaliser = allTerms[0].getWeightExpansion();
			for (ExpansionTerm term : allTerms) {
				if (normaliser != 0) {
					term.setWeightExpansion(term.getWeightExpansion()
							/ totalweight);
				}
			}
		} else if (strategy == 4) {// relevance model alike
			float totalweight = 0;
			int feedbackNum = sample.numDocuments();
			int times = 10;
			float thetas[][] = sampleThetas(times, NUM_TOPICS, lda, querytermid);

			for (int i = 0; i < len; i++) {
				String term = SYMBOL_TABLE.idToSymbol(i);
				TermsCache.Item item = getItem(term);
				float TF = item.ctf;
				float DF = item.df;
				float tf = sample.wordCount(i);
				float tokens = sample.numTokens();
				QEModel.setDocumentFrequency(tokens);
				float pt = QEModel.score(tf, TF, DF);
				float weight = pt;
				for (int qi = 0; qi < querytermid.length; qi++) {

					for (int ti = 0; ti < NUM_TOPICS; ti++) {

					}
				}

				weight /= feedbackNum;
				if (dfMap.get(term) < EXPANSION_MIN_DOCUMENTS) {
					weight = 0;
				}
				allTerms[i] = new ExpansionTerm(term, 0);
				allTerms[i].setWeightExpansion(weight);
				this.termMap.put(term, allTerms[i]);
				totalweight += weight;
			}

			java.util.Arrays.sort(allTerms);
			// determine double normalizing factor
			float normaliser = allTerms[0].getWeightExpansion();
			for (ExpansionTerm term : allTerms) {
				if (normaliser != 0) {
					term.setWeightExpansion(term.getWeightExpansion()
							/ totalweight);
				}
			}
		} else if (strategy == 5) { // take advantage of the topic with the
									// highest prob
			float totalweight = 0;
			int feedbackNum = sample.numDocuments();
			for (int i = 0; i < len; i++) {
				String term = SYMBOL_TABLE.idToSymbol(i);
				TermsCache.Item item = getItem(term);
				float TF = item.ctf;
				float DF = item.df;
				float weight = 0;

				weight = 0;
				for (int d = 0; d < feedbackNum; d++) {

					double docProb = 0;
					// docProb= sample.docWordCount(d, i)/
					// (float)sample.documentLength(d);
					docProb = score(sample.docWordCount(d, i), sample
							.documentLength(d), TF, QEModel
							.getCollectionLength());
					// docProb = (1-beta) * sample.docWordCount(d, i)/
					// (float)sample.documentLength(d) + beta *
					// TF/QEModel.getCollectionLength();
					weight += sample.documentTopicProb(d, maxTopic) * docProb
							* dscores[d];
				}
				QEModel.setTotalDocumentLength(1);
				weight = QEModel.score(weight, TF, DF);

				if (dfMap.get(term) < EXPANSION_MIN_DOCUMENTS) {
					weight = 0;
				}
				allTerms[i] = new ExpansionTerm(term, 0);
				allTerms[i].setWeightExpansion(weight);
				this.termMap.put(term, allTerms[i]);
				totalweight += weight;
			}
			java.util.Arrays.sort(allTerms);
			for (ExpansionTerm term : allTerms) {
				term
						.setWeightExpansion(term.getWeightExpansion()
								/ totalweight);
			}
		} else if (strategy == 6) { // take advantage of the topic with the
									// highest prob
			float totalweight = 0;
			int feedbackNum = sample.numDocuments();
			for (int i = 0; i < len; i++) {
				String term = SYMBOL_TABLE.idToSymbol(i);
				TermsCache.Item item = getItem(term);
				float TF = item.ctf;
				float DF = item.df;
				float weight = 0;

				weight = 0;
				for (int d = 0; d < feedbackNum; d++) {
					double docProb = 0;
					// docProb= sample.docWordCount(d, i)/
					// (float)sample.documentLength(d);
					docProb = score(sample.docWordCount(d, i), sample
							.documentLength(d), TF, QEModel
							.getCollectionLength());
					// docProb = (1-beta) * sample.docWordCount(d, i)/
					// (float)sample.documentLength(d) + beta *
					// TF/QEModel.getCollectionLength();
					float norm = 0;
					for (int ti = 0; ti < index.length; ti++) {
						if (theta[index[ti]] > threshold) {
							weight += sample.documentTopicProb(d, index[ti])
									* docProb * dscores[d];
						}
					}
					if(weight == Float.NaN){
						System.exit(0);
					}
					// weight += sample.documentTopicProb(d, maxTopic) * docProb
					// * dscores[d];
				}
				QEModel.setTotalDocumentLength(1);
				weight = QEModel.score(weight, TF, DF);

				if (dfMap.get(term) < EXPANSION_MIN_DOCUMENTS) {
					weight = 0;
				}
				allTerms[i] = new ExpansionTerm(term, 1);
				allTerms[i].setWeightExpansion(weight);
				this.termMap.put(term, allTerms[i]);
				totalweight += weight;
			}
			java.util.Arrays.sort(allTerms);
			for (ExpansionTerm term : allTerms) {
				term
						.setWeightExpansion(term.getWeightExpansion()
								/ totalweight);
			}
		} else if (strategy == 7) {// adding term association
			float totalweight = 0;
			int feedbackNum = sample.numDocuments();
			int times = 10;
			float thetas[] = sampleThetasAver(times, NUM_TOPICS, lda,
					querytermid);

			float doctopics[][] = new float[feedbackNum][NUM_TOPICS];
			for (int d = 0; d < feedbackNum; d++) {
				for(int ti=0; ti < NUM_TOPICS; ti++){
					doctopics[d][ti] =  (float) sample.documentTopicProb(d, ti);
				}
			}
			
			for (int i = 0; i < len; i++) {
				String term = SYMBOL_TABLE.idToSymbol(i);
				TermsCache.Item item = getItem(term);
				float TF = item.ctf;
				float DF = item.df;
				float weight = 0;

				// for(int oi =0; oi < thetas.length; oi++){

				weight = 0;
				for (int d = 0; d < feedbackNum; d++) {
					double docProb = 0;
					// docProb = sample.docWordCount(d, i)/
					// (float)sample.documentLength(d);
					docProb = score(sample.docWordCount(d, i), sample
							.documentLength(d), TF, QEModel
							.getCollectionLength());
					float subweight = 0;
					for (int zi = 0; zi < thetas.length; zi++) {
						float pzi = thetas[zi]; // p(z_i | \theta)
						subweight += pzi * doctopics[d][zi] * docProb
								* dscores[d];
					}
					weight +=  subweight;
				}

				// }

				QEModel.setTotalDocumentLength(1);
				weight = QEModel.score(weight, TF, DF);

				if (dfMap.get(term) < EXPANSION_MIN_DOCUMENTS) {
					weight = 0;
				}
				allTerms[i] = new ExpansionTerm(term, 0);
				allTerms[i].setWeightExpansion(weight);
				this.termMap.put(term, allTerms[i]);
				totalweight += weight;
			}

			java.util.Arrays.sort(allTerms);
			// determine double normalizing factor
			float normaliser = allTerms[0].getWeightExpansion();
			for (ExpansionTerm term : allTerms) {
				if (normaliser != 0) {
					term.setWeightExpansion(term.getWeightExpansion()
							/ totalweight);
				}
			}
		}
		return allTerms;
	}

	static String dmu = ApplicationSetup.getProperty("dlm.mu", "500");
	static float mu = Integer.parseInt(ApplicationSetup.getProperty("topicSL.mu",
			dmu));

	// float numOfTokens = this.searcher.getNumTokens(field);
	public float score(float tf, float docLength, float termFrequency,
			float numberOfTokens) {
		float pc = termFrequency / numberOfTokens;
		return (tf + mu * pc) / (docLength + mu);
	}

	private void indriNorm(float[] pQ) {
		
		float K = pQ[0]; // first is max
		float sum = 0;
		for (int i = 0; i < pQ.length; i++) {
			pQ[i] = Idf.exp(K + pQ[i]);
			sum += pQ[i];
		}
		for (int i = 0; i < pQ.length; i++) {
			pQ[i] /= sum;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.lucene.postProcess.termselector.TermSelector#getInfo()
	 */
	@Override
	public String getInfo() {
		return "TopicSel_s=" + strategy + "t=" + NUM_TOPICS + "beta=" + beta
				+ "expTag=" + expTag;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		StringBuilder buf = new StringBuilder();
	}

	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		// TODO Auto-generated method stub

	}

}
