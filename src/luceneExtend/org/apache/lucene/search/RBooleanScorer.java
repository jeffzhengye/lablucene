package org.apache.lucene.search;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.TreeSet;

import javax.naming.InitialContext;

/**
 * An alternative to BooleanScorer that also allows a minimum number of optional
 * scorers that should match. <br>
 * Implements skipTo(), and has no limitations on the numbers of added scorers. <br>
 * Uses ConjunctionScorer, DisjunctionScorer, ReqOptScorer and ReqExclScorer.
 */
class RBooleanScorer extends RScorer {
	private ArrayList requiredScorers = new ArrayList();
	private ArrayList optionalScorers = new ArrayList();
	private ArrayList prohibitedScorers = new ArrayList();
	TreeSet<Integer> docSet = null;

	private class Coordinator {
		int maxCoord = 0; // to be increased for each non prohibited scorer

		private float[] coordFactors = null;

		void init() { // use after all scorers have been added.
			coordFactors = new float[maxCoord + 1];
			Similarity sim = getSimilarity();
			for (int i = 0; i <= maxCoord; i++) {
				coordFactors[i] = sim.coord(i, maxCoord);
			}
		}

		int nrMatchers; // to be increased by score() of match counting scorers.

		void initDoc() {
			nrMatchers = 0;
		}

		float coordFactor() {
			return coordFactors[nrMatchers];
		}
	}

	private final Coordinator coordinator;

	/** The number of optionalScorers that need to match (if there are any) */
	private final int minNrShouldMatch;

	/**
	 * Whether it is allowed to return documents out of order. This can
	 * accelerate the scoring of disjunction queries.
	 */
	private boolean allowDocsOutOfOrder;
	private int doc;
	private float score;

	/**
	 * Create a BooleanScorer2.
	 * 
	 * @param similarity
	 *            The similarity to be used.
	 * @param minNrShouldMatch
	 *            The minimum number of optional added scorers that should match
	 *            during the search. In case no required scorers are added, at
	 *            least one of the optional scorers will have to match during
	 *            the search.
	 * @param allowDocsOutOfOrder
	 *            Whether it is allowed to return documents out of order. This
	 *            can accelerate the scoring of disjunction queries.
	 */
	public RBooleanScorer(Similarity similarity, int minNrShouldMatch,
			boolean allowDocsOutOfOrder) {
		super(similarity);
		if (minNrShouldMatch < 0) {
			throw new IllegalArgumentException(
					"Minimum number of optional scorers should not be negative");
		}
		coordinator = new Coordinator();
		this.minNrShouldMatch = minNrShouldMatch;
		this.allowDocsOutOfOrder = allowDocsOutOfOrder;
	}

	/**
	 * Create a BooleanScorer2. In no required scorers are added, at least one
	 * of the optional scorers will have to match during the search.
	 * 
	 * @param similarity
	 *            The similarity to be used.
	 * @param minNrShouldMatch
	 *            The minimum number of optional added scorers that should match
	 *            during the search. In case no required scorers are added, at
	 *            least one of the optional scorers will have to match during
	 *            the search.
	 */
	public RBooleanScorer(Similarity similarity, int minNrShouldMatch) {
		this(similarity, minNrShouldMatch, false);
	}

	/**
	 * Create a BooleanScorer2. In no required scorers are added, at least one
	 * of the optional scorers will have to match during the search.
	 * 
	 * @param similarity
	 *            The similarity to be used.
	 */
	public RBooleanScorer(Similarity similarity) {
		this(similarity, 0, false);
	}

	public void add(final RScorer scorer, boolean required, boolean prohibited) {
		if (!prohibited) {
			coordinator.maxCoord++;
		}

		if (required) {
			if (prohibited) {
				throw new IllegalArgumentException(
						"scorer cannot be required and prohibited");
			}
			requiredScorers.add(scorer);
		} else if (prohibited) {
			prohibitedScorers.add(scorer);
		} else {
			optionalScorers.add(scorer);
		}
	}

	/** Count a scorer as a single match. */
	private class SingleMatchScorer extends Scorer {
		private Scorer scorer;
		private int lastScoredDoc = -1;

		SingleMatchScorer(Scorer scorer) {
			super(scorer.getSimilarity());
			this.scorer = scorer;
		}

		public float score() throws IOException {
			if (this.doc() >= lastScoredDoc) {
				lastScoredDoc = this.doc();
				coordinator.nrMatchers++;
			}
			return scorer.score();
		}

		public int doc() {
			return scorer.doc();
		}

		public boolean next() throws IOException {
			return scorer.next();
		}

		public boolean skipTo(int docNr) throws IOException {
			return scorer.skipTo(docNr);
		}

		public Explanation explain(int docNr) throws IOException {
			return scorer.explain(docNr);
		}

		@Override
		public float getLogAlphaD(int currentDoc) throws IOException {
			// TODO Auto-generated method stub
			throw new RuntimeException();
		}
	}

	private static Similarity defaultSimilarity = new DefaultSimilarity();

	/**
	 * Scores and collects all matching documents.
	 * 
	 * @param hc
	 *            The collector to which all matching documents are passed
	 *            through {@link HitCollector#collect(int, float)}. <br>
	 *            When this method is used the {@link #explain(int)} method
	 *            should not be used.
	 */
	public void score(HitCollector hc) throws IOException {
		next();
		score(hc, Integer.MAX_VALUE);

	}

	/**
	 * Expert: Collects matching documents in a range. <br>
	 * Note that {@link #next()} must be called once before this method is
	 * called for the first time.
	 * 
	 * @param hc
	 *            The collector to which all matching documents are passed
	 *            through {@link HitCollector#collect(int, float)}.
	 * @param max
	 *            Do not score documents past this.
	 * @return true if more matching documents may remain.
	 */
	protected boolean score(HitCollector hc, int max) throws IOException {
		// null pointer exception when next() was not called before:
		int docNr = doc;
		while (docNr < max) {
			hc.collect(docNr, score());
			if (!next()) {
				return false;
			}
			docNr = doc();
		}
		return true;
	}

	public int doc() {
		return doc;
	}

	public boolean next() throws IOException {
		if (docSet == null) {
			initial();
		}
		if(docSet.size() > 0){
			doc = docSet.pollFirst();
			int len = this.optionalScorers.size();
			score =0;
			RScorer scorer = null;
			for(int i=0; i < len; i++){
				scorer = (RScorer)this.optionalScorers.get(i);
				score += scorer.score(doc);
				if(doc == scorer.doc()){
					if(scorer.next()){
						docSet.add(scorer.doc());
					}
				}
			}
			return true;
		}
		score =0;
		return false;
	}

	private void initial() {
		docSet = new TreeSet<Integer>();
		int len = this.optionalScorers.size();
		RScorer scorer = null;
		for(int i=0; i < len; i++){
			scorer = (RScorer)this.optionalScorers.get(i);
			try {
				if(scorer.next()){
					docSet.add(scorer.doc());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}

	public float score() throws IOException {
		return score;
	}
	
	@Override
	public float score(int doc) throws IOException {
		throw new UnsupportedOperationException();
	}
	/**
	 * Skips to the first match beyond the current whose document number is
	 * greater than or equal to a given target.
	 * 
	 * <p>
	 * When this method is used the {@link #explain(int)} method should not be
	 * used.
	 * 
	 * @param target
	 *            The target document number.
	 * @return true iff there is such a match.
	 */
	public boolean skipTo(int target) throws IOException {
		throw new UnsupportedOperationException();
	}

	public Explanation explain(int doc) {
		throw new UnsupportedOperationException();
	}
}
