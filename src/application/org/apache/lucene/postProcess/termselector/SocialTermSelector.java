/**
 * 
 */
package org.apache.lucene.postProcess.termselector;

import gnu.trove.TIntObjectHashMap;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.RQuery;
import org.apache.lucene.search.RTermQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.RBooleanClause.Occur;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.ISManager;
import org.dutir.lucene.IndexUtility;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;
import org.dutir.util.AbstractExternalizable;
import org.dutir.util.FastCache;
import org.dutir.util.Strings;

/**
 * @author zheng
 * 
 */
public class SocialTermSelector extends TermSelector {
	private static Logger logger = Logger.getLogger(SocialTermSelector.class);
	static Searcher socialSearch = ISManager
			.getSearcheFromPath(ApplicationSetup.getProperty(
					"SocialTermSelector.social.indexpath", ""));
	static IndexReader socialReader = socialSearch.getIndexReader();
	static IndexUtility indexUtil = new IndexUtility(socialSearch);
	static Idf socialIDF = new Idf(indexUtil.maxDoc());
	static String socialField = ApplicationSetup.getProperty(
			"SocialTermSelector.searchFiled", "tagtitle");
	static int expDoc = Integer.parseInt(ApplicationSetup.getProperty(
			"SocialTermSelector.documents", "100"));
	static int strategy = Integer.parseInt(ApplicationSetup.getProperty(
			"SocialTermSelector.strategy", "10"));
	static float lambda = Float.parseFloat(ApplicationSetup.getProperty(
			"SocialTermSelector.lambda", "0.5"));
	static boolean combiningTag = Boolean.parseBoolean(ApplicationSetup
			.getProperty("SocialTermSelector.combiningTag", "false"));
	static FastCache<String, Float> conditinProbCache = null;
	static String socialCondPath = ApplicationSetup.LUCENE_ETC + "/"
			+ socialField + "_socialCondPro.cache";
	String addInfo = "";
	static {
		File file = new File(socialCondPath);
		if(logger.isInfoEnabled()) logger.info("socialCondCachePath: " + socialCondPath);
		if (file.exists()) {
			try {
				conditinProbCache = (FastCache<String, Float>) AbstractExternalizable
						.readObject(file);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else
			conditinProbCache = new FastCache<String, Float>(1024 * 1024);

	}

	public RQuery getSocailQuery() {
		String terms[] = this.originalQueryTermidSet.toArray(new String[0]);
		RBooleanQuery bquery = new RBooleanQuery();
		for (int i = 0; i < terms.length; i++) {
			bquery.add(new RTermQuery(new Term(socialField, terms[i])),
					RBooleanClause.Occur.SHOULD);
		}
		return bquery;
	}

	public Query getMustSocailQuery(String _terms[]) {
		return getSocailQuery(_terms, BooleanClause.Occur.MUST);
	}

	public Query getSocailQuery(String _terms[], BooleanClause.Occur occur) {
		String terms[] = _terms;
		BooleanQuery bquery = new BooleanQuery();
		for (int i = 0; i < terms.length; i++) {
			bquery.add(new TermQuery(new Term(socialField, terms[i])), occur);
		}
		return bquery;
	}

	static String sQEModel = ApplicationSetup.getProperty(
			"Lucene.QueryExpansion.Model", "KL");
	private QueryExpansionModel socialQEModel;

	public QueryExpansionModel getSocialExpansionModel() {
		if (socialQEModel == null) {
			try {
				if (sQEModel.indexOf(".") == -1) {
					sQEModel = "org.apache.lucene.postProcess." + sQEModel;
				}
				socialQEModel = (QueryExpansionModel) Class.forName(sQEModel)
						.newInstance();
				socialQEModel.setCollectionLength(socialSearch
						.getNumTokens(socialField));
				socialQEModel.setAverageDocumentLength(socialSearch
						.getAverageLength(socialField));
				socialQEModel.setNumberOfDocuments(socialSearch.maxDoc());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return socialQEModel;
	}

	static String dmu = ApplicationSetup.getProperty("slm.mu", "500");
	static float mu = Integer.parseInt(ApplicationSetup.getProperty("rm.mu",
			dmu));

	// float numOfTokens = this.searcher.getNumTokens(field);
	public float score(float tf, float docLength, float termFrequency,
			float numberOfTokens) {
		float pc = termFrequency / numberOfTokens;
		return (tf + mu * pc) / (docLength + mu);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.termselector.TermSelector#assignTermWeights
	 * (int[], float[], org.apache.lucene.postProcess.QueryExpansionModel)
	 */
	@Override
	public void assignTermWeights(int[] docids, float[] scores,
			QueryExpansionModel QEModel) {
		try {
			TopDocCollector topdc = new TopDocCollector(1000);
			String originalModel = ApplicationSetup.getProperty(
					"Lucene.Search.WeightingModel", "BM25");
			ApplicationSetup
					.setProperty("Lucene.Search.WeightingModel", "BM25");
			socialSearch.search(getSocailQuery(), topdc);
			ApplicationSetup.setProperty("Lucene.Search.WeightingModel",
					originalModel);
			TopDocs tdocs = topdc.topDocs();
			int minDoc = Math.min(expDoc, tdocs.totalHits);
			int socialdocIDs[] = new int[minDoc];
			float socialScores[] = new float[minDoc];
			for (int i = 0; i < minDoc; i++) {
				socialdocIDs[i] = tdocs.scoreDocs[i].doc;
				socialScores[i] = tdocs.scoreDocs[i].score;
			}
			ApplicationSetup.setProperty("Lucene.Search.WeightingModel",
					originalModel);
			// ///////////////////////////////////////////////////////////////
			if (strategy == 1) { // \sig p(t|e)*p(e|F)
				QueryExpansionModel socialQEModel = getSocialExpansionModel();
				TermSelector selector = TermSelector.getTermSelector(
						"DFRTermSelector", socialSearch);
				selector.setResultSet(topdc);
				selector.setOriginalQueryTerms(this.originalQueryTermidSet);
				selector.setField(socialField);
				selector.assignTermWeights(socialdocIDs, socialScores,
						socialQEModel);
				ExpansionTerm[] expTerms = selector
						.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				this.termMap = new HashMap<String, ExpansionTerm>(100);
				// for(int i=0; i < expTerms.length; i++){
				// String term = expTerms[i].getTerm();
				// // if(this.searcher.getIndexReader().docFreq(new
				// Term(this.field,
				// term)) > 0){
				// this.termMap.put(term, expTerms[i]);
				// // }
				// }

				// generative model
				int queryLen = this.originalQueryTermidSet.size();
				float pF[] = new float[queryLen];
				int pos = 0;
				String sq[] = new String[queryLen];
				for (String q : this.originalQueryTermidSet) {
					sq[pos] = q;
					pF[pos++] = indexUtil.getDF(new Term(socialField, q))
							/ indexUtil.maxDoc();
				}
				for (int i = 0; i < expTerms.length; i++) {
					String term = expTerms[i].getTerm();
					float weight = 0;
					for (int j = 0; j < queryLen; j++) {
						weight += pF[j] * conditionProb(term, sq[j]);
					}
					expTerms[i].setWeightExpansion(weight
							* expTerms[i].getWeightExpansion());
					this.termMap.put(term, expTerms[i]);
					// }
				}
			} else if (strategy == 2) { // co-occurrence
				QueryExpansionModel socialQEModel = getSocialExpansionModel();
				TermSelector selector = TermSelector.getTermSelector(
						"DFRTermSelector", socialSearch);
				selector.setResultSet(topdc);
				selector.setOriginalQueryTerms(this.originalQueryTermidSet);
				selector.setField(socialField);
				selector.assignTermWeights(socialdocIDs, socialScores,
						socialQEModel);
				ExpansionTerm[] expTerms = selector
						.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				Arrays.sort(expTerms, ExpansionTerms.AlphaBetaComparator);
				this.termMap = new HashMap<String, ExpansionTerm>(100);

				// co-occurrence model
				int queryLen = this.originalQueryTermidSet.size();
				float pF[] = new float[queryLen];
				int pos = 0;
				String sq[] = new String[queryLen];
				float qidf[] = new float[queryLen];
				for (String q : this.originalQueryTermidSet) {
					sq[pos] = q;
					float df = indexUtil.getDF(new Term(socialField, q));
					int N = indexUtil.maxDoc();
					qidf[pos] = socialIDF.idfNENQUIRY(df);
					pF[pos++] = df / (float) N;

				}

				// Set set = new HashSet();
				// set.add(socialField);
				// SetBasedFieldSelector fieldsel = new
				// SetBasedFieldSelector(set,
				// set);
				// TermFreqVector tfvcache[] = new TermFreqVector[minDoc];
				for (int i = 0; i < expTerms.length; i++) {
					String term = expTerms[i].getTerm();
					float weight = 0;
					for (int j = 0; j < queryLen; j++) {
						String keys[] = { term, sq[j] };
						String key = tocachekey(keys);
						weight += (conditinProbCache.get(key) != null ? conditinProbCache
								.get(key)
								: 0) + 0.0001;
					}

					// trigram
					for (int j = 0; j < queryLen - 1; j++) {
						for (int j1 = j + 1; j1 < queryLen; j1++) {
							String keys[] = { term, sq[j], sq[j1] };
							String key = tocachekey(keys);
							weight += (conditinProbCache.get(key) != null ? conditinProbCache
									.get(key)
									: 0) + 0.0001;
						}
					}

					expTerms[i].setWeightExpansion(weight);
					this.termMap.put(term, expTerms[i]);
					// }
				}
			} else if (strategy == 3) { // sig p(t|e)*p(e|d)*p(d|F)
				QueryExpansionModel socialQEModel = getSocialExpansionModel();
				TermSelector selector = TermSelector.getTermSelector(
						"DFRTermSelector", socialSearch);
				selector.setResultSet(topdc);
				selector.setOriginalQueryTerms(this.originalQueryTermidSet);
				selector.setField(socialField);
				selector.assignTermWeights(socialdocIDs, scores, socialQEModel);
				ExpansionTerm[] expTerms = selector
						.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				Arrays.sort(expTerms, ExpansionTerms.AlphaBetaComparator);
				this.termMap = new HashMap<String, ExpansionTerm>(100);

				// co-occurrence model
				int queryLen = this.originalQueryTermidSet.size();
				float pF[] = new float[queryLen];
				int pos = 0;
				String sq[] = new String[queryLen];
				float qidf[] = new float[queryLen];
				for (String q : this.originalQueryTermidSet) {
					sq[pos] = q;
					float df = indexUtil.getDF(new Term(socialField, q));
					int N = indexUtil.maxDoc();
					qidf[pos] = socialIDF.idfNENQUIRY(df);
					pF[pos++] = df / (float) N;
				}

				Set set = new HashSet();
				set.add(socialField);
				SetBasedFieldSelector fieldsel = new SetBasedFieldSelector(set,
						set);
				TermFreqVector tfvcache[] = new TermFreqVector[minDoc];
				for (int i = 0; i < expTerms.length; i++) {
					String term = expTerms[i].getTerm();

					float termFrequency = socialSearch.termFreq(toTerm(term));
					float numberOfTokens = socialSearch
							.getNumTokens(socialField);
					float weight = 0;
					for (int k = 0; k < minDoc; k++) {
						float dl = 0;
						float tf = 0;
						TermFreqVector tfv = null;
						if (tfvcache[k] == null) {
							tfvcache[k] = socialReader.getTermFreqVector(
									socialdocIDs[k], socialField);
						}
						tfv = tfvcache[k];
						String strterms[] = tfv.getTerms();
						int freqs[] = tfv.getTermFrequencies();
						dl = org.dutir.util.Arrays.sum(freqs);
						int curPos = Arrays.binarySearch(strterms, 0,
								strterms.length, term);
						float docWeight = 0;
						float selfWeight = 0;
						if (curPos >= 0) {
							tf = freqs[curPos];
							selfWeight += socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* Idf.log(1 + freqs[curPos]);
							for (int j = 0; j < queryLen; j++) {
								int qid = Arrays.binarySearch(strterms, 0,
										strterms.length, sq[j]);
								if (qid >= 0)
									docWeight += qidf[j]
											* Idf.log(1 + freqs[qid])
											* Idf.log(1 + freqs[curPos]);
							}
							// trigram
							selfWeight += socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* Idf.log(1 + freqs[curPos]);
							for (int j = 0; j < queryLen - 1; j++) {
								for (int j1 = j + 1; j1 < queryLen; j1++) {
									int qid = Arrays.binarySearch(strterms, 0,
											strterms.length, sq[j]);
									if (qid >= 0) {
										int qid1 = Arrays.binarySearch(
												strterms, 0, strterms.length,
												sq[j1]);
										if (qid1 >= 0)
											docWeight += qidf[j]
													* Idf.log(1 + freqs[qid])
													* Idf
															.log(1 + freqs[curPos])
													* qidf[j1]
													* Idf.log(1 + freqs[qid1]);
									}

								}
							}
						}
						weight += (docWeight + selfWeight) * socialScores[k]
								* score(tf, dl, termFrequency, numberOfTokens);
						// *socialScores[k];
						// * score(tf, dl, termFrequency, numberOfTokens);
						// (docWeight + 0)*
					}
					expTerms[i].setWeightExpansion(weight * socialIdf(term));
					this.termMap.put(term, expTerms[i]);
				}
			} else if (strategy == 4) { // sig p(t|e)*p(e|d)*p(d|F)
				QueryExpansionModel socialQEModel = getSocialExpansionModel();
				TermSelector selector = TermSelector.getTermSelector(
						"DFRTermSelector", socialSearch);
				selector.setResultSet(topdc);
				selector.setOriginalQueryTerms(this.originalQueryTermidSet);
				selector.setField(socialField);
				selector.assignTermWeights(socialdocIDs, scores, socialQEModel);
				ExpansionTerm[] expTerms = selector
						.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				Arrays.sort(expTerms, ExpansionTerms.AlphaBetaComparator);
				this.termMap = new HashMap<String, ExpansionTerm>(100);

				// co-occurrence model
				int queryLen = this.originalQueryTermidSet.size();
				float pF[] = new float[queryLen];
				int pos = 0;
				String sq[] = new String[queryLen];
				float qidf[] = new float[queryLen];
				for (String q : this.originalQueryTermidSet) {
					sq[pos] = q;
					float df = indexUtil.getDF(new Term(socialField, q));
					int N = indexUtil.maxDoc();
					qidf[pos] = socialIDF.idfNENQUIRY(df);
					pF[pos++] = df / (float) N;
				}

				Set set = new HashSet();
				set.add(socialField);
				SetBasedFieldSelector fieldsel = new SetBasedFieldSelector(set,
						set);
				TermFreqVector tfvcache[] = new TermFreqVector[minDoc];
				for (int i = 0; i < expTerms.length; i++) {
					String term = expTerms[i].getTerm();

					float termFrequency = socialSearch.termFreq(toTerm(term));
					float numberOfTokens = socialSearch
							.getNumTokens(socialField);
					float weight = 0;
					for (int k = 0; k < minDoc; k++) {
						float dl = 0;
						float tf = 0;
						TermFreqVector tfv = null;
						if (tfvcache[k] == null) {
							tfvcache[k] = socialReader.getTermFreqVector(
									socialdocIDs[k], socialField);
						}
						tfv = tfvcache[k];
						String strterms[] = tfv.getTerms();
						int freqs[] = tfv.getTermFrequencies();
						dl = org.dutir.util.Arrays.sum(freqs);
						int curPos = Arrays.binarySearch(strterms, 0,
								strterms.length, term);
						float docWeight = 0;
						float selfWeight = 0;
						if (curPos >= 0) {
							tf = freqs[curPos];
							selfWeight += socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* Idf.log(1 + freqs[curPos]);
							for (int j = 0; j < queryLen; j++) {
								int qid = Arrays.binarySearch(strterms, 0,
										strterms.length, sq[j]);
								if (qid >= 0)
									docWeight += qidf[j]
											* Idf.log(1 + freqs[qid])
											* Idf.log(1 + freqs[curPos]);
							}
							// trigram
							selfWeight += socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* Idf.log(1 + freqs[curPos]);
							for (int j = 0; j < queryLen - 1; j++) {
								for (int j1 = j + 1; j1 < queryLen; j1++) {
									int qid = Arrays.binarySearch(strterms, 0,
											strterms.length, sq[j]);
									if (qid >= 0) {
										int qid1 = Arrays.binarySearch(
												strterms, 0, strterms.length,
												sq[j1]);
										if (qid1 >= 0)
											docWeight += qidf[j]
													* Idf.log(1 + freqs[qid])
													* Idf
															.log(1 + freqs[curPos])
													* qidf[j1]
													* Idf.log(1 + freqs[qid1]);
									}

								}
							}
						}

						weight += (docWeight + selfWeight) * socialScores[k]
								* score(tf, dl, termFrequency, numberOfTokens);
						// *socialScores[k];
						// * score(tf, dl, termFrequency, numberOfTokens);
						// (docWeight + 0)*
					}

					for (int k = 0; k < docids.length; k++) {
						float dl = 0;
						float tf = 0;
						TermFreqVector tfv = null;
						if (tfvcache[k] == null) {
							tfvcache[k] = this.searcher.getIndexReader()
									.getTermFreqVector(docids[k], this.field);
						}
						tfv = tfvcache[k];
						String strterms[] = tfv.getTerms();
						int freqs[] = tfv.getTermFrequencies();
						dl = org.dutir.util.Arrays.sum(freqs);
						int curPos = Arrays.binarySearch(strterms, 0,
								strterms.length, term);
						float docWeight = 0;
						float selfWeight = 0;
						if (curPos >= 0) {
							tf = freqs[curPos];
							selfWeight += socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* Idf.log(1 + freqs[curPos]);
							for (int j = 0; j < queryLen; j++) {
								int qid = Arrays.binarySearch(strterms, 0,
										strterms.length, sq[j]);
								if (qid >= 0)
									docWeight += qidf[j]
											* Idf.log(1 + freqs[qid])
											* Idf.log(1 + freqs[curPos]);
							}
							// trigram
							selfWeight += socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* socialIdf(term)
									* Idf.log(1 + freqs[curPos])
									* Idf.log(1 + freqs[curPos]);
							for (int j = 0; j < queryLen - 1; j++) {
								for (int j1 = j + 1; j1 < queryLen; j1++) {
									int qid = Arrays.binarySearch(strterms, 0,
											strterms.length, sq[j]);
									if (qid >= 0) {
										int qid1 = Arrays.binarySearch(
												strterms, 0, strterms.length,
												sq[j1]);
										if (qid1 >= 0)
											docWeight += qidf[j]
													* Idf.log(1 + freqs[qid])
													* Idf
															.log(1 + freqs[curPos])
													* qidf[j1]
													* Idf.log(1 + freqs[qid1]);
									}

								}
							}
						}
						weight += (docWeight + selfWeight) * socialScores[k]
								* score(tf, dl, termFrequency, numberOfTokens);
					}
					expTerms[i].setWeightExpansion(weight * socialIdf(term));
					this.termMap.put(term, expTerms[i]);
				}

			} else if (strategy == 5) { // RM or KL using socialTags
				QueryExpansionModel socialQEModel = getSocialExpansionModel();
				TermSelector selector = TermSelector.getTermSelector(
						"RMTermSelector", socialSearch);
				selector.setResultSet(topdc);
				selector.setOriginalQueryTerms(this.originalQueryTermidSet);
				selector.setField(socialField);
				selector.assignTermWeights(socialdocIDs, socialScores,
						socialQEModel);
				ExpansionTerm[] expTerms = selector
						.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				this.termMap = new HashMap<String, ExpansionTerm>(100);
				for (int i = 0; i < expTerms.length; i++) {
					String term = expTerms[i].getTerm();
					this.termMap.put(term, expTerms[i]);
				}
			} else if (strategy == 10) { // // generate cache file
				QueryExpansionModel socialQEModel = getSocialExpansionModel();
				TermSelector selector = TermSelector.getTermSelector(
						"DFRTermSelector", socialSearch);
				selector.setResultSet(topdc);
				selector.setOriginalQueryTerms(this.originalQueryTermidSet);
				selector.setField(socialField);
				selector.assignTermWeights(socialdocIDs, scores, socialQEModel);
				ExpansionTerm[] expTerms = selector
						.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				Arrays.sort(expTerms, ExpansionTerms.AlphaBetaComparator);
				this.termMap = new HashMap<String, ExpansionTerm>(100);

				// co-occurrence model
				int queryLen = this.originalQueryTermidSet.size();
				float pF[] = new float[queryLen];
				int pos = 0;
				String sq[] = new String[queryLen];
				float qidf[] = new float[queryLen];
				for (String q : this.originalQueryTermidSet) {
					sq[pos] = q;
					float df = indexUtil.getDF(new Term(socialField, q));
					int N = indexUtil.maxDoc();
					qidf[pos] = socialIDF.idfNENQUIRY(df);
					pF[pos++] = df / (float) N;
				}

				TIntObjectHashMap<TermFreqVector> idVectorMap = new TIntObjectHashMap<TermFreqVector>(
						20000);
				// bigram
				for (int i = 0; i < expTerms.length; i++) {
					String term = expTerms[i].getTerm();

					for (int j = 0; j < queryLen; j++) {
						float weight = 0;
						int maxCompute = 20000;
						TopDocCollector temptopdc = new TopDocCollector(
								maxCompute);
						originalModel = ApplicationSetup.getProperty(
								"Lucene.Search.WeightingModel", "BM25");
						ApplicationSetup.setProperty(
								"Lucene.Search.WeightingModel", "BM25");
						String ssquery[] = { term, sq[j] };
						socialSearch.search(getMustSocailQuery(ssquery),
								temptopdc);
						ApplicationSetup.setProperty(
								"Lucene.Search.WeightingModel", originalModel);
						minDoc = Math.min(maxCompute, temptopdc.getTotalHits());
						logger.debug("bi_minDoc:" + minDoc);
						for (int k = 0; k < minDoc; k++) {
							int docid = temptopdc.topDocs().scoreDocs[k].doc;
							TermFreqVector tfv = null;
							if (idVectorMap.contains(docid)) {
								tfv = idVectorMap.get(docid);
							} else {
								tfv = socialReader.getTermFreqVector(docid,
										socialField);
							}
							String strterms[] = tfv.getTerms();
							int freqs[] = tfv.getTermFrequencies();
							int curPos = Arrays.binarySearch(strterms, 0,
									strterms.length, term);
							if (curPos >= 0) {
								int qid = Arrays.binarySearch(strterms, 0,
										strterms.length, sq[j]);
								if (qid >= 0)
									weight += qidf[j] * Idf.log(1 + freqs[qid])
											* Idf.log(1 + freqs[curPos]);
							}
						}
						// co-occurrence
						weight = weight * socialIdf(term);
						String keys[] = { term, sq[j] };
						String key = tocachekey(keys);
						conditinProbCache.put(key, weight);
						if (weight <= 0) {
							logger.error("weigh=" + weight + " < 0 for: " + key
									+ ":" + socialIdf(term) + "," + qidf[j]);
						}
					}

					// trigram
					for (int j = 0; j < queryLen - 1; j++) {
						for (int j1 = j + 1; j1 < queryLen; j1++) {
							float weight = 0;
							int maxCompute = 20000;
							TopDocCollector temptopdc = new TopDocCollector(
									maxCompute);
							originalModel = ApplicationSetup.getProperty(
									"Lucene.Search.WeightingModel", "BM25");
							ApplicationSetup.setProperty(
									"Lucene.Search.WeightingModel", "BM25");
							String ssquery[] = { term, sq[j], sq[j1] };
							socialSearch.search(getMustSocailQuery(ssquery),
									temptopdc);
							ApplicationSetup.setProperty(
									"Lucene.Search.WeightingModel",
									originalModel);
							minDoc = Math.min(maxCompute, temptopdc
									.getTotalHits());
							logger.debug("tri_minDoc:" + minDoc);
							for (int k = 0; k < minDoc; k++) {
								int docid = temptopdc.topDocs().scoreDocs[k].doc;
								TermFreqVector tfv = null;
								if (idVectorMap.contains(docid)) {
									tfv = idVectorMap.get(docid);
								} else {
									tfv = socialReader.getTermFreqVector(docid,
											socialField);
								}
								String strterms[] = tfv.getTerms();
								int freqs[] = tfv.getTermFrequencies();
								int curPos = Arrays.binarySearch(strterms, 0,
										strterms.length, term);
								if (curPos >= 0) {
									int qid = Arrays.binarySearch(strterms, 0,
											strterms.length, sq[j]);
									if (qid >= 0) {
										int qid1 = Arrays.binarySearch(
												strterms, 0, strterms.length,
												sq[j1]);
										if (qid1 >= 0)
											weight += qidf[j]
													* Idf.log(1 + freqs[qid])
													* Idf
															.log(1 + freqs[curPos])
													* qidf[j1]
													* Idf.log(1 + freqs[qid1]);
									}

								}
							}
							// co-occurrence
							weight = weight * socialIdf(term)
									* socialIDF.idfENQUIRY(10 + minDoc);
							String keys[] = { term, sq[j], sq[j1] };
							String key = tocachekey(keys);
							conditinProbCache.put(key, weight);
							if (weight <= 0) {
								logger.error("weigh =" + weight + "< 0 for: "
										+ key + ":" + socialIdf(term) + ","
										+ qidf[j] + ", " + qidf[j1]);
							}
						}
					}
					// }
				}
				AbstractExternalizable.serializeTo(conditinProbCache, new File(
						socialCondPath));
			}

			// ////////////////////////////////////////////////////////////////
			if (combiningTag) {
				TermSelector selector = TermSelector.getTermSelector(
						"RocchioTermSelector", searcher);
				// selector.setResultSet(this.topDoc);
				selector.setOriginalQueryTerms(this.originalQueryTermidSet);
				selector.setField(this.field);
				selector.setResultSet(topdc);
				selector.assignTermWeights(docids, scores, QEModel);
				ExpansionTerm[] expTerms = selector
						.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				addInfo += selector.getInfo();
				norm(expTerms);
				ExpansionTerm currentExpTerms[] = this
						.getMostWeightedTerms(selector.getNumberOfUniqueTerms());
				norm(currentExpTerms);
				this.termMap = new HashMap<String, ExpansionTerm>(1024);

				for (ExpansionTerm eterm : expTerms) {
					String term = eterm.getTerm();
					eterm.setWeightExpansion(eterm.getWeightExpansion()
							* lambda);
					this.termMap.put(term, eterm);
				}

				for (ExpansionTerm eterm : currentExpTerms) {
					String term = eterm.getTerm();
					ExpansionTerm tmp_eterm = this.termMap.get(term);
					if (tmp_eterm == null) {
						tmp_eterm = new ExpansionTerm(term, 2);
						tmp_eterm.setWeightExpansion(eterm.getWeightExpansion()
								* (1 - lambda));
//						tmp_eterm.setWeightExpansion(eterm.getWeightExpansion()
//								);
						this.termMap.put(term, tmp_eterm);
					} else {
//						tmp_eterm.setWeightExpansion(tmp_eterm
//								.getWeightExpansion()
//								+ eterm.getWeightExpansion());
						tmp_eterm.setWeightExpansion(tmp_eterm
								.getWeightExpansion()
								+ eterm.getWeightExpansion() * (1 - lambda));
						this.termMap.put(term, tmp_eterm);
					}
				}
			}
			ExpansionTerm currentExpTerms[] = this
					.getMostWeightedTerms(ApplicationSetup.EXPANSION_TERMS);
			norm(currentExpTerms);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void norm(ExpansionTerm allTerms[]) {
		if (allTerms == null || allTerms.length < 1)
			return;
		Arrays.sort(allTerms);
		float normaliser = allTerms[0].getWeightExpansion();
		for (ExpansionTerm term : allTerms) {
			if (normaliser != 0) {
				if(normaliser< term.getWeightExpansion()){
					logger.error("program logic error, exit");
					System.exit(1);
				}
				term.setWeightExpansion(term.getWeightExpansion() / normaliser);
			}
		}
	}

	public static String tocachekey(String keys[]) {
		Arrays.sort(keys);
		String key = Strings.concatenate(keys);
		return key;
	}

	public float socialIdf(String term) {
		float df = indexUtil.getDF(new Term(socialField, term));
		int N = indexUtil.maxDoc();
		return socialIDF.idfNENQUIRY(df);
	}

	public float conditionProb(String term1, String term2) {
		return (float) ((indexUtil.getPhraseDF(toTerm(term1), toTerm(term2),
				100) + 0.5) / (indexUtil.getDF(toTerm(term2)) + 0.5));
	}

	public Term toTerm(String sterm) {
		return new Term(socialField, sterm);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.termselector.TermSelector#assignTermWeights
	 * (java.lang.String[][], int[][],
	 * org.apache.lucene.index.TermPositionVector[],
	 * org.apache.lucene.postProcess.QueryExpansionModel)
	 */
	@Override
	public void assignTermWeights(String[][] terms, int[][] freqs,
			TermPositionVector[] tfvs, QueryExpansionModel QEModel) {
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.lucene.postProcess.termselector.TermSelector#getInfo()
	 */
	@Override
	public String getInfo() {
		return "S_" + socialField + "_sDoc" + expDoc + "_stra" + strategy
				+ (combiningTag == true ? ("Comb" + lambda) : "") +addInfo;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		boolean combiningTag = true;
		System.out.println((combiningTag == true ? ("Comb" + lambda) : ""));
	}

}
