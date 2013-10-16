/*
 * Terrier - Terabyte Retriever 
 * Webpage: http://ir.dcs.gla.ac.uk/terrier 
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - Department of Computing Science
 * http://www.gla.ac.uk/
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is ExpansionTerms.java.
 *
 * The Original Code is Copyright (C) 2004-2008 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Gianni Amati <gba{a.}fub.it> (original author)
 *   Ben He <ben{a.}dcs.gla.ac.uk> 
 *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk>
 *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk>
 */
package org.dutir.lucene.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectObjectProcedure;

import org.apache.log4j.Logger;
import org.apache.lucene.index.Term;
import org.apache.lucene.postProcess.QueryExpansionModel;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.model.Statistics;
import org.dutir.lucene.util.TermsCache.Item;

/**
 * This class implements a data structure of terms in the top-retrieved
 * documents.
 * <P>
 * <b>Properties</b>:
 * <ul>
 * <li><tt>expansion.mindocuments</tt> - the minimum number of documents a term
 * must exist in before it can be considered to be informative. Defaults to 2.
 * For more information, see Giambattista Amati: Information Theoretic Approach
 * to Information Extraction. FQAS 2006: 519-529 <a
 * href="http://dx.doi.org/10.1007/11766254_44">DOI 10.1007/11766254_44</a></li>
 * </ul>
 * 
 * @author Gianni Amati, Ben He, Vassilis Plachouras, Craig Macdonald
 * @version $Revision: 1.39 $
 */
public class ExpansionTerms {
	static TermsCache tcache = TermsCache.getInstance();

	/** The logger used */
	Logger logger = Logger.getLogger(this.getClass());
	/** The terms in the top-retrieval documents. */
	protected HashMap<String, ExpansionTerm> terms;
	/** The lexicon used for retrieval. */
	/** The number of documents in the collection. */
	protected int numberOfDocuments;
	/** The number of tokens in the collection. */
	protected float numberOfTokens;
	/** The average document length in the collection. */
	protected float averageDocumentLength;
	/** The number of tokens in the X top ranked documents. */
	protected float totalDocumentLength;
	/**
	 * The original query terms. Used only for Conservative Query Expansion,
	 * where no terms are added to the query, only the existing ones are
	 * reweighted.
	 */
	protected THashSet<String> originalTerms = new THashSet<String>();
	/** The ids of the original query terms. */
	protected TIntHashSet originalTermids = new TIntHashSet();
	/**
	 * The parameter-free term weight normaliser.
	 */
	public float normaliser = 1f;

	/**
	 * The minimum number of documents a term must occur in to be considered for
	 * expanded terms. This is not considered a parameter of query expansion, as
	 * the default value of 2 works extremely well. Set using the property
	 * <tt>expansion.mindocuments</tt>
	 */
	protected static final int EXPANSION_MIN_DOCUMENTS = Integer
			.parseInt(ApplicationSetup.getProperty("expansion.mindocuments",
					"2"));

	Searcher searcher;
	
	public static ExpansionTermAlphaBetaComparator AlphaBetaComparator = new ExpansionTermAlphaBetaComparator();
	public static class ExpansionTermAlphaBetaComparator implements Comparator{
		public int compare(Object arg0, Object arg1) {
			return ((ExpansionTerm) arg0).term.compareTo(((ExpansionTerm) arg0).term);
		}
		
	}
	/**
	 * This class implements a data structure for a term in the top-retrieved
	 * documents.
	 */
	public static class ExpansionTerm implements Comparable<ExpansionTerm> {
		
		/** The weight for query expansion. */
		protected float weightExpansion;
		/**
		 * The number of occurrences of the given term in the X top ranked
		 * documents.
		 */
		protected float withinDocumentFrequency;

		/** The document frequency of the term in the X top ranked documents. */
		protected int documentFrequency;
		private String term;

		/**
		 * The constructor of ExpansionTerm. Once the term is found in a top-
		 * retrieved documents, we create a record for this term.
		 * 
		 * @param termID
		 *            int the ID of the term
		 * @param withinDocumentFrequency2
		 *            float the frequency of the term in a top-retrieved
		 *            document
		 */
		public ExpansionTerm(String sterm, float withinDocumentFrequency2) {
			this.term = sterm;
			this.withinDocumentFrequency = withinDocumentFrequency2;
			this.documentFrequency = 1;
			this.weightExpansion = 0;
		}

		public ExpansionTerm(ExpansionTerm eterm) {
			this.term = eterm.term;
			this.withinDocumentFrequency = eterm.withinDocumentFrequency;
			this.documentFrequency = eterm.documentFrequency;
			this.weightExpansion = eterm.weightExpansion;
		}

		public ExpansionTerm clone() {
			return new ExpansionTerm(this);
		}

		/**
		 * Returns the ID of the term.
		 * 
		 * @return int the term ID.
		 */
		public String getTerm() {
			return this.term;
		}

		/**
		 * If the term is found in another top-retrieved document, we increase
		 * the frequency and the document frequency of the term.
		 * 
		 * @param withinDocumentFrequency
		 *            float the frequency of the term in the corresponding
		 *            top-retrieved document.
		 */
		public void insertRecord(float withinDocumentFrequency) {
			this.withinDocumentFrequency += withinDocumentFrequency;
			this.documentFrequency++;
		}

		/**
		 * Sets the expansion weight of the term.
		 * 
		 * @param weightExpansion
		 *            float the expansion weight of the term.
		 */
		public void setWeightExpansion(float weightExpansion) {
			this.weightExpansion = weightExpansion;
		}

		/**
		 * The method returns the document frequency of term in the
		 * top-retrieved documents.
		 * 
		 * @return int The document frequency of term in the top-retrieved
		 *         documents.
		 */
		public int getDocumentFrequency() {
			return this.documentFrequency;
		}

		/**
		 * The method returns the expansion weight of the term.
		 * 
		 * @return float The expansion weight of the term.
		 */
		public float getWeightExpansion() {
			return this.weightExpansion;
		}

		/**
		 * The method returns the frequency of the term in the X top-retrieved
		 * documents.
		 * 
		 * @return float The expansion weight of the term.
		 */
		public float getWithinDocumentFrequency() {
			return this.withinDocumentFrequency;
		}

		public String toString() {
			return this.term + ":" + this.weightExpansion;
		}

		/**
		 * Note: descending order, not ascending
		 */
		public int compareTo(ExpansionTerm o) {
			if (this.weightExpansion < o.weightExpansion) {
				return 1;
			} else if (this.weightExpansion > o.weightExpansion) {
				return -1;
			}
			return 0;
		}

	}

	Statistics stats = null;
	private String field;

	public ExpansionTerms(Searcher searcher, float totalLength, String field)
			throws IOException {
		this(searcher.maxDoc(), searcher.getNumTokens(field), searcher
				.getAverageLength(field), totalLength);
		this.searcher = searcher;
		this.field = field;
	}

	/**
	 * Constructs an instance of ExpansionTerms.
	 * 
	 * @param totalLength
	 *            The sum of the length of the top-retrieved documents.
	 * @param lexicon
	 *            Lexicon The lexicon used for retrieval.
	 */
	private ExpansionTerms(int numberOfDocuments, float numberOfTokens,
			float averageDocumentLength, float totalLength) {
		this.numberOfDocuments = numberOfDocuments;
		this.numberOfTokens = numberOfTokens;
		this.averageDocumentLength = averageDocumentLength;
		this.terms = new HashMap<String, ExpansionTerm>();
		this.totalDocumentLength = totalLength;
	}

	/** Allows the totalDocumentLength to be set after the fact */
	public void setTotalDocumentLength(float totalLength) {
		this.totalDocumentLength = totalLength;
	}

	/** Returns the terms of all terms found in the top-ranked documents */
	public String[] getTerms() {
		return terms.keySet().toArray(new String[0]);
	}

	/** Returns the unique number of terms found in all the top-ranked documents */
	public int getNumberOfUniqueTerms() {
		return terms.size();
	}

	/**
	 * This method implements the functionality of assigning expansion weights
	 * to the terms in the top-retrieved documents, and returns the most
	 * informative terms among them. Conservative Query Expansion
	 * (ConservativeQE) is used if the number of expanded terms is set to 0. In
	 * this case, no new query terms are added to the query, only the existing
	 * ones reweighted.
	 * 
	 * @param numberOfExpandedTerms
	 *            int The number of terms to extract from the top-retrieved
	 *            documents. ConservativeQE is set if this parameter is set to
	 *            0.
	 * @param QEModel
	 *            QueryExpansionModel the model used for query expansion
	 * @return TermTreeNode[] The expanded terms.
	 */
	public ExpansionTerm[] getExpandedTerms(int numberOfExpandedTerms,
			QueryExpansionModel QEModel) {
		// The number of terms to extract from the pseudo relevance set is the
		// minimum between the system setting and the number of unique terms in
		// the pseudo relevance set.
		numberOfExpandedTerms = Math.min(this.terms.size(),
				numberOfExpandedTerms);
		if (numberOfExpandedTerms < 1) {
			return new ExpansionTerm[0];
		}
		QEModel.setTotalDocumentLength(this.totalDocumentLength);
		QEModel.setCollectionLength(this.numberOfTokens);
		QEModel.setAverageDocumentLength(this.averageDocumentLength);
		QEModel.setNumberOfDocuments(this.numberOfDocuments);
//		QEModel.setDocumentFrequency(this.numberOfDocuments);
		// System.out.println("totalDocumentLength: "+totalDocumentLength);

		final boolean ConservativeQE = (numberOfExpandedTerms == 0);

		// weight the terms
		int posMaxWeight = 0;
		int size = terms.values().size();
		ExpansionTerms.ExpansionTerm[] allTerms = new ExpansionTerms.ExpansionTerm[size];

		allTerms = (ExpansionTerm[]) terms.values().toArray(allTerms);

		final int len = allTerms.length;

		for (int i = 0; i < len; i++) {
			try {
				// only consider terms which occur in 2 or more documents. Alter
				// using the expansion.mindocuments property.
				if (allTerms[i].getDocumentFrequency() < EXPANSION_MIN_DOCUMENTS ) {
					allTerms[i].setWeightExpansion(0);
					continue;
				}

				// Term term = new Term(field, allTerms[i].getTerm());
				// float TF = searcher.termFreq(term);
				// float Nt = searcher.docFreq(term);
				TermsCache.Item item = getItem(allTerms[i].getTerm());
				float TF = item.ctf;
				float DF = item.df;

				allTerms[i].setWeightExpansion(QEModel.score(allTerms[i]
						.getWithinDocumentFrequency(), TF, DF));
				if (allTerms[i].getWeightExpansion() > allTerms[posMaxWeight]
						.getWeightExpansion())
					posMaxWeight = i;

			} catch (NullPointerException npe) {
				// TODO print something more explanatory here
				logger
						.fatal(
								"A nullpointer exception occured while iterating over expansion terms at iteration number: "
										+ "i = " + i, npe);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// sort the terms by weight

		normaliser = allTerms[posMaxWeight].getWeightExpansion();
		if (QEModel.PARAMETER_FREE) {
			QEModel.setMaxTermFrequency(allTerms[posMaxWeight]
					.getWithinDocumentFrequency());
			normaliser = QEModel.parameterFreeNormaliser();
			if(logger.isDebugEnabled()) logger.debug("parameter free query expansion.");
		}
		// lexicon.findTerm(allTerms[posMaxWeight].termID);
		// if(logger.isDebugEnabled()){
		// logger.debug("term with the maximum weight: " + lexicon.getTerm() +
		// ", normaliser: " + Rounding.toString(normaliser, 4));
		// }
		THashSet<ExpansionTerm> expandedTerms = new THashSet<ExpansionTerm>();
		if (!ConservativeQE) {

			for (int i = 0; i < numberOfExpandedTerms; i++) {
				int position = i;
				for (int j = i; j < len; j++) {
					if (allTerms[j].getWeightExpansion() > allTerms[position]
							.getWeightExpansion())
						position = j;
				}
				if (position != i) {
					ExpansionTerm temp = allTerms[position];
					allTerms[position] = allTerms[i];
					allTerms[i] = temp;
				}

				ExpansionTerm tmpETerm = allTerms[i].clone();
				tmpETerm.setWeightExpansion(allTerms[i].getWeightExpansion()
						/ normaliser);

				// expandedTerms[i].normalisedFrequency =
				// terms[i].getWeightExpansion()/normaliser;
				if (!QEModel.PARAMETER_FREE)
					tmpETerm.setWeightExpansion(tmpETerm.getWeightExpansion()
							* QEModel.ROCCHIO_BETA);
				// normalisedFrequency *= QEModel.ROCCHIO_BETA;
				// System.out.println(tmpETerm);
				expandedTerms.add(tmpETerm);
			}
		} else {
			int allTermsCount = allTerms.length;
			int weighedOriginalTermsCount = 0;
			for (int i = 0; i < allTermsCount; i++) {
				if (weighedOriginalTermsCount == originalTerms.size())
					break;

				ExpansionTerm tmpETerm = allTerms[i].clone();
				tmpETerm.setWeightExpansion(allTerms[i].getWeightExpansion()
						/ normaliser);

				// expandedTerms[i].normalisedFrequency =
				// terms[i].getWeightExpansion()/normaliser;
				if (!QEModel.PARAMETER_FREE)
					tmpETerm.setWeightExpansion(tmpETerm.getWeightExpansion()
							* QEModel.ROCCHIO_BETA);
				// normalisedFrequency *= QEModel.ROCCHIO_BETA;
				expandedTerms.add(tmpETerm);
			}
		}
		return (ExpansionTerm[]) expandedTerms.toArray(new ExpansionTerm[0]);
	}

	/**
	 * Set the original query terms.
	 * 
	 * @param query
	 *            The original query.
	 */
	// public void setOriginalQueryTerms(MatchingQueryTerms query){
	// String[] terms = query.getTerms();
	// this.originalTermids.clear();
	// this.originalTerms.clear();
	// for (int i=0; i<terms.length; i++){
	// this.originalTerms.add(terms[i]);
	// this.originalTermids.add(query.getTermCode(terms[i]));
	// }
	// }
	private Item getItem(String term) {
		Term lterm = new Term(field, term);
		TermsCache.Item item = tcache.get(lterm);
		if (item == null) {
			try {
				float TF = searcher.termFreq(lterm);
				float Nt = searcher.docFreq(lterm);
				item = new TermsCache.Item(Nt, TF);
				tcache.put(lterm, item);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return item;
	}

	/**
	 * Returns the weight of a given term, computed by the specified query
	 * expansion model.
	 * 
	 * @param term
	 *            String the term to set the weight for.
	 * @param model
	 *            QueryExpansionModel the used query expansion model.
	 * @return float the weight of the specified term.
	 */
	public float getExpansionWeight(String term, QueryExpansionModel model) {

		return this.getExpansionWeight(term, model);
	}

	/**
	 * Returns the weight of a given term.
	 * 
	 * @param term
	 *            String the term to get the weight for.
	 * @return float the weight of the specified term.
	 */
	public float getExpansionWeight(String term) {
		return this.getExpansionWeight(term);
	}

	/**
	 * Returns the un-normalised weight of a given term.
	 * 
	 * @param term
	 *            String the given term.
	 * @return The un-normalised term weight.
	 */
	// public float getOriginalExpansionWeight(String term){
	// return getExpansionWeight(term)*normaliser;
	// }
	/**
	 * Returns the frequency of a given term in the top-ranked documents.
	 * 
	 * @param term
	 *            String the term to get the frequency for.
	 * @return float the frequency of the specified term in the top-ranked
	 *         documents.
	 */
	public float getFrequency(String term) {

		return terms.get(term).getWithinDocumentFrequency();
	}

	/**
	 * Returns the number of the top-ranked documents a given term occurs in.
	 * 
	 * @param termId
	 *            int the id of the term to get the frequency for.
	 * @return float the document frequency of the specified term in the
	 *         top-ranked documents.
	 */
	public float getDocumentFrequency(String term) {

		return terms.get(term).getDocumentFrequency();
	}

	/**
	 * Assign weight to terms that are stored in ExpansionTerm[] terms.
	 * 
	 * @param QEModel
	 *            QueryExpansionModel the used query expansion model.
	 */
	public void assignWeights(QueryExpansionModel QEModel) {
		// Set required statistics to the query expansion model
		QEModel.setTotalDocumentLength(this.totalDocumentLength);
		QEModel.setCollectionLength(this.numberOfTokens);
		QEModel.setAverageDocumentLength(this.averageDocumentLength);
		QEModel.setNumberOfDocuments(this.numberOfDocuments);

		// weight the terms
		int posMaxWeight = 0;

		ExpansionTerm[] allTerms = terms.values().toArray(new ExpansionTerm[0]);
		final int len = allTerms.length;

		for (int i = 0; i < len; i++) {
			try {
				if (allTerms[i].getDocumentFrequency() <= EXPANSION_MIN_DOCUMENTS) {
					allTerms[i].setWeightExpansion(0);
					continue;
				}
				Term term = new Term(field, allTerms[i].getTerm());
				float TF = searcher.termFreq(term);
				float DF = searcher.docFreq(term);
				allTerms[i].setWeightExpansion((float) QEModel.score(
						allTerms[i].getWithinDocumentFrequency(), TF, DF));
				if (allTerms[i].getWeightExpansion() > allTerms[posMaxWeight]
						.getWeightExpansion())
					posMaxWeight = i;

			} catch (NullPointerException npe) {
				// TODO print something more explanatory here
				logger
						.fatal(
								"A nullpointer exception occured while assigning weights on expansion terms at iteration: "
										+ "i = " + i, npe);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// sort the terms by weight
		normaliser = allTerms[posMaxWeight].getWeightExpansion();
		if (QEModel.PARAMETER_FREE) {
			QEModel.setMaxTermFrequency(allTerms[posMaxWeight]
					.getWithinDocumentFrequency());
			normaliser = (float) QEModel.parameterFreeNormaliser();
			if (logger.isInfoEnabled()) {
				if(logger.isInfoEnabled()) logger.info("parameter free query expansion.");
			}
		}
		// lexicon.findTerm(allTerms[posMaxWeight].termID);
		// if(logger.isDebugEnabled()){
		// logger.debug("term with the maximum weight: " + lexicon.getTerm() +
		// ", normaliser: " + Rounding.toString(normaliser, 4));
		// }
		for (int i = 0; i < len; i++) {
			allTerms[i].setWeightExpansion(allTerms[i].getWeightExpansion()
					/ normaliser);
			// expandedTerms[i].normalisedFrequency =
			// terms[i].getWeightExpansion()/normaliser;
			if (!QEModel.PARAMETER_FREE)
				allTerms[i].setWeightExpansion(allTerms[i].getWeightExpansion()
						* QEModel.ROCCHIO_BETA);
			// normalisedFrequency *= QEModel.ROCCHIO_BETA;
		}
	}

	/**
	 * Returns the weight of a term with the given term identifier, computed by
	 * the specified query expansion model.
	 * 
	 * @param termId
	 *            int the term identifier to set the weight for.
	 * @param model
	 *            QueryExpansionModel the used query expansion model.
	 * @return float the weight of the specified term.
	 */
	// public float getExpansionWeight(int termId, QueryExpansionModel model){
	// float score = 0;
	// Object o = terms.get(termId);
	// if (o != null)
	// {
	// float TF = 0;
	// float Nt = 0;
	// lexicon.findTerm(termId);
	// TF = lexicon.getTF();
	// Nt = lexicon.getNt();
	// score = model.score(((ExpansionTerm)o).getWithinDocumentFrequency(),
	// TF,
	// this.totalDocumentLength,
	// this.numberOfTokens,
	// this.averageDocumentLength
	// );
	// }
	// return score;
	// }

	/**
	 * Add a term in the X top-retrieved documents as a candidate of the
	 * expanded terms.
	 * 
	 * @param string
	 *            int the integer identifier of a term
	 * @param withinDocumentFrequency
	 *            float the within document frequency of a term
	 */
	public void insertTerm(String string, float withinDocumentFrequency) {
		final ExpansionTerm et = terms.get(string);
		if (et == null)
			terms.put(string,
					new ExpansionTerm(string, withinDocumentFrequency));
		else
			et.insertRecord(withinDocumentFrequency);
	}
}
