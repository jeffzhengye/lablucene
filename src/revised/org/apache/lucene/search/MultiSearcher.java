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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.dutir.lucene.util.TermsCache;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements search over a set of <code>Searchables</code>.
 * 
 * <p>
 * Applications usually need only call the inherited {@link #search(Query)} or
 * {@link #search(Query,Filter)} methods.
 */
public class MultiSearcher extends Searcher {
	/**
	 * Document Frequency cache acting as a Dummy-Searcher. This class is no
	 * full-fledged Searcher, but only supports the methods necessary to
	 * initialize Weights.
	 */
	static TermsCache tcache = TermsCache.getInstance();
	
	private static class CachedDfSource extends Searcher {
		private Map dfMap; // Map from Terms to corresponding doc freqs
		private Map termFreqMap;
		private int maxDoc; // document count
		private Map numTokensMap;
		private Map numUniqTokensMap;
		private Map averFiledLen;
		
		private Term curTerm = null;
		private TermsCache.Item curItem = null;
		private Searcher cSearcher = null;
		
		public CachedDfSource(Map dfMap, int maxDoc, Similarity similarity) {
			this.dfMap = dfMap;
			this.maxDoc = maxDoc;
			numTokensMap = new HashMap();
			numUniqTokensMap = new HashMap();
			this.averFiledLen =  new HashMap();
			setSimilarity(similarity);
		}

		public CachedDfSource(Map dfMap, Map termFreqMap,
				 int maxDoc, Similarity similarity) {
			this.dfMap = dfMap;
			this.termFreqMap = termFreqMap;
			this.maxDoc = maxDoc;
			
			setSimilarity(similarity);
			
			
			numTokensMap = new HashMap();
			numUniqTokensMap = new HashMap();
			this.averFiledLen =  new HashMap();
		}

		public void setSearcher(Searcher searcher){
			this.cSearcher = searcher;
		}
		
		public int docFreq(Term term) {
			int df = 0;
//			try {
//				Integer inter = (Integer)dfMap.get(term);
//				if(inter != null){
//					df = inter.intValue();
//				}else{
//					df = this.cSearcher.docFreq(term);
//				}
//			} catch (NullPointerException e) {
//				throw new IllegalArgumentException("df for term " + term.text()
//						+ " not available");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			if(term.equals(curTerm)){
				df =  (int) curItem.df;
			}else{
				curItem = tcache.getItem(term, cSearcher);
				df = (int) curItem.df;
			}
			return df;
		}
		
		@Override
		public int termFreq(Term term) throws IOException {
			int tf = 0;
//			try {
//				Integer inter = (Integer)numTokensMap.get(term);
//				if(inter != null){
//					df = inter.intValue();
//				}else{
//					df = this.cSearcher.termFreq(term);
//				}
//			} catch (NullPointerException e) {
//				throw new IllegalArgumentException("df for term " + term.text()
//						+ " not available");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			if(term.equals(curTerm)){
				tf =  (int) curItem.ctf;
			}else{
				curItem = tcache.getItem(term, cSearcher);
				tf = (int) curItem.ctf;
			}
			return tf;
		}

		public float getAverageLength(String field) {
			float df = 0;
			Float inter = (Float) averFiledLen.get(field);
			if (inter != null) {
				df = inter.floatValue();
				
			} else {
				df = this.cSearcher.getAverageLength(field);
				this.averFiledLen.put(field, df);
			}
			return df;
		}

		public IndexReader getIndexReader() {
			return this.cSearcher.getIndexReader();
		}

		public float getFieldLength(String fieldName, int docid) {
			return this.cSearcher.getFieldLength(fieldName, docid);
		}

		public float getNumTokens(String field) {
			float df = 0;
			Float inter = (Float) numTokensMap.get(field);
			if (inter != null) {
				df = inter.floatValue();
			} else {
				df = this.cSearcher.getNumTokens(field);
				this.numTokensMap.put(field, df);
			}
			return df;
		}

		public float getNumUniqTokens(String fieldName) {
			float df = 0;
			Float inter = (Float) numUniqTokensMap.get(fieldName);
			if (inter != null) {
				df = inter.floatValue();
			} else {
				df = this.cSearcher.getNumUniqTokens(fieldName);
				this.numUniqTokensMap.put(fieldName, df);
			}
			return df;
		}
		
		public int[] docFreqs(Term[] terms) {
			int[] result = new int[terms.length];
			for (int i = 0; i < terms.length; i++) {
				result[i] = docFreq(terms[i]);
			}
			return result;
		}
		
		public float[] termFreqs(Term[] allTermsArray) throws IOException {
			// TODO Auto-generated method stub
			throw new IOException();
		}
		
		public int maxDoc() {
			return maxDoc;
		}

		public Query rewrite(Query query) {
			// this is a bit of a hack. We know that a query which
			// creates a Weight based on this Dummy-Searcher is
			// always already rewritten (see preparedWeight()).
			// Therefore we just return the unmodified query here
			return query;
		}

		public void close() {
			throw new UnsupportedOperationException();
		}

		public Document doc(int i) {
			throw new UnsupportedOperationException();
		}

		public Document doc(int i, FieldSelector fieldSelector) throws CorruptIndexException, IOException {
			return this.cSearcher.doc(i, fieldSelector);
		}

		public Explanation explain(Weight weight, int doc) {
			throw new UnsupportedOperationException();
		}

		public void search(Weight weight, Filter filter, HitCollector results) {
			throw new UnsupportedOperationException();
		}

		public TopDocs search(Weight weight, Filter filter, int n) throws IOException {
			return this.cSearcher.search(weight, filter, n);
		}

		public TopFieldDocs search(Weight weight, Filter filter, int n,
				Sort sort) {
			throw new UnsupportedOperationException();
		}


	}

	private Searchable[] searchables;
	private int[] starts;
	private int maxDoc = 0;
	private MultiReader reader;

	/** Creates a searcher which searches <i>searchables</i>. */
	public MultiSearcher(Searchable[] searchables) throws IOException {
		this.searchables = searchables;
		IndexReader readers[] = new IndexReader[searchables.length];

		starts = new int[searchables.length + 1]; // build starts array
		for (int i = 0; i < searchables.length; i++) {
			starts[i] = maxDoc;
			maxDoc += searchables[i].maxDoc(); // compute maxDocs
			readers[i] = searchables[i].getIndexReader();
		}
		starts[searchables.length] = maxDoc;
		reader = new MultiReader(readers);
	}

	/** Return the array of {@link Searchable}s this searches. */
	public Searchable[] getSearchables() {
		return searchables;
	}

	protected int[] getStarts() {
		return starts;
	}

	// inherit javadoc
	public void close() throws IOException {
		for (int i = 0; i < searchables.length; i++)
			searchables[i].close();
	}

	public int docFreq(Term term) throws IOException {
		int docFreq = 0;
		for (int i = 0; i < searchables.length; i++)
			docFreq += searchables[i].docFreq(term);
		return docFreq;
	}

	// inherit javadoc
	public Document doc(int n) throws CorruptIndexException, IOException {
		int i = subSearcher(n); // find searcher index
		return searchables[i].doc(n - starts[i]); // dispatch to searcher
	}

	// inherit javadoc
	public Document doc(int n, FieldSelector fieldSelector)
			throws CorruptIndexException, IOException {
		int i = subSearcher(n); // find searcher index
		return searchables[i].doc(n - starts[i], fieldSelector); // dispatch to
		// searcher
	}

	/**
	 * Returns index of the searcher for document <code>n</code> in the array
	 * used to construct this searcher.
	 */
	public int subSearcher(int n) { // find searcher for doc n:
		// replace w/ call to Arrays.binarySearch in Java 1.2
		int lo = 0; // search starts array
		int hi = searchables.length - 1; // for first element less
		// than n, return its index
		while (hi >= lo) {
			int mid = (lo + hi) >>> 1;
			int midValue = starts[mid];
			if (n < midValue)
				hi = mid - 1;
			else if (n > midValue)
				lo = mid + 1;
			else { // found a match
				while (mid + 1 < searchables.length
						&& starts[mid + 1] == midValue) {
					mid++; // scan to last match
				}
				return mid;
			}
		}
		return hi;
	}

	/**
	 * Returns the document number of document <code>n</code> within its
	 * sub-index.
	 */
	public int subDoc(int n) {
		return n - starts[subSearcher(n)];
	}

	public int maxDoc() throws IOException {
		return maxDoc;
	}

	public TopDocs search(Weight weight, Filter filter, int nDocs)
			throws IOException {

		HitQueue hq = new HitQueue(nDocs);
		int totalHits = 0;

		for (int i = 0; i < searchables.length; i++) { // search each searcher
			TopDocs docs = searchables[i].search(weight, filter, nDocs);
			totalHits += docs.totalHits; // update totalHits
			ScoreDoc[] scoreDocs = docs.scoreDocs;
			for (int j = 0; j < scoreDocs.length; j++) { // merge scoreDocs into
				// hq
				ScoreDoc scoreDoc = scoreDocs[j];
				scoreDoc.doc += starts[i]; // convert doc
				if (!hq.insert(scoreDoc))
					break; // no more scores > minScore
			}
		}

		ScoreDoc[] scoreDocs = new ScoreDoc[hq.size()];
		for (int i = hq.size() - 1; i >= 0; i--)
			// put docs in array
			scoreDocs[i] = (ScoreDoc) hq.pop();

		float maxScore = (totalHits == 0) ? Float.NEGATIVE_INFINITY
				: scoreDocs[0].score;

		return new TopDocs(totalHits, scoreDocs, maxScore);
	}

	public TopFieldDocs search(Weight weight, Filter filter, int n, Sort sort)
			throws IOException {
		FieldDocSortedHitQueue hq = null;
		int totalHits = 0;

		float maxScore = Float.NEGATIVE_INFINITY;

		for (int i = 0; i < searchables.length; i++) { // search each searcher
			TopFieldDocs docs = searchables[i].search(weight, filter, n, sort);

			if (hq == null)
				hq = new FieldDocSortedHitQueue(docs.fields, n);
			totalHits += docs.totalHits; // update totalHits
			maxScore = Math.max(maxScore, docs.getMaxScore());
			ScoreDoc[] scoreDocs = docs.scoreDocs;
			for (int j = 0; j < scoreDocs.length; j++) { // merge scoreDocs into
				// hq
				ScoreDoc scoreDoc = scoreDocs[j];
				scoreDoc.doc += starts[i]; // convert doc
				if (!hq.insert(scoreDoc))
					break; // no more scores > minScore
			}
		}

		ScoreDoc[] scoreDocs = new ScoreDoc[hq.size()];
		for (int i = hq.size() - 1; i >= 0; i--)
			// put docs in array
			scoreDocs[i] = (ScoreDoc) hq.pop();

		return new TopFieldDocs(totalHits, scoreDocs, hq.getFields(), maxScore);
	}

	// inherit javadoc
	public void search(Weight weight, Filter filter, final HitCollector results)
			throws IOException {
		for (int i = 0; i < searchables.length; i++) {

			final int start = starts[i];

			searchables[i].search(weight, filter, new HitCollector() {
				public void collect(int doc, float score) {
					results.collect(doc + start, score);
				}
			});

		}
	}

	public Query rewrite(Query original) throws IOException {
		Query[] queries = new Query[searchables.length];
		for (int i = 0; i < searchables.length; i++) {
			queries[i] = searchables[i].rewrite(original);
		}
		return queries[0].combine(queries);
	}

	public Explanation explain(Weight weight, int doc) throws IOException {
		int i = subSearcher(doc); // find searcher index
		return searchables[i].explain(weight, doc - starts[i]); // dispatch to
		// searcher
	}

	/**
	 * Create weight in multiple index scenario.
	 * 
	 * Distributed query processing is done in the following steps: 1. rewrite
	 * query 2. extract necessary terms 3. collect dfs for these terms from the
	 * Searchables 4. create query weight using aggregate dfs. 5. distribute
	 * that weight to Searchables 6. merge results
	 * 
	 * Steps 1-4 are done here, 5+6 in the search() methods
	 * 
	 * @return rewritten queries
	 */
	protected Weight createWeight(Query original) throws IOException {
		// step 1
		Query rewrittenQuery = rewrite(original);

		// step 2
		Set terms = new HashSet();
		rewrittenQuery.extractTerms(terms);
		
		// step3
		Term[] allTermsArray = new Term[terms.size()];
		terms.toArray(allTermsArray);
//		int[] aggregatedDfs = new int[terms.size()];
//		float[] aggregatedTFs = new float[terms.size()];
//		
//		for (int i = 0; i < searchables.length; i++) {
//			int[] dfs = searchables[i].docFreqs(allTermsArray);
//			float[] tfs = searchables[i].termFreqs(allTermsArray);
//			for (int j = 0; j < aggregatedDfs.length; j++) {
//				aggregatedDfs[j] += dfs[j];
//				aggregatedTFs[j] += tfs[j];
//			}
//		}
		
		for(int i=0; i < allTermsArray.length; i ++){
			Term term = allTermsArray[i];
			if(!tcache.contain(term)){
				int df =0;
				float tfs =0;
				for (int j = 0; j < searchables.length; j++) {
					df += searchables[j].docFreq(term);
					tfs += searchables[j].termFreq(term);
				}
				tcache.put(term, new TermsCache.Item(df, tfs));
			}
		}

//		HashMap dfMap = new HashMap();
//		HashMap termFreqMap = new HashMap();
//		for (int i = 0; i < allTermsArray.length; i++) {
//			dfMap.put(allTermsArray[i], new Integer(aggregatedDfs[i]));
//			termFreqMap.put(allTermsArray[i], new Float(aggregatedTFs[i]));
//		}

		// step4
		int numDocs = maxDoc();
//		CachedDfSource cacheSim = new CachedDfSource(dfMap, termFreqMap, numDocs,
//				getSimilarity());
		CachedDfSource cacheSim = new CachedDfSource(null, null, numDocs,
				getSimilarity());
		cacheSim.setSearcher(this);
		return rewrittenQuery.weight(cacheSim);
	}

	@Override
	public int termFreq(Term term) throws IOException {
		int termFreq = 0;
		for (int i = 0; i < searchables.length; i++)
			termFreq += searchables[i].termFreq(term);
		return termFreq;
	}

	// added by ye zheng
	public float getAverageLength(String field) {
		float retValue = 0;
		for (int i = 0; i < this.searchables.length; i++) {
			retValue += this.searchables[i].getAverageLength(field);
		}
		return retValue / this.searchables.length;
	}

	public IndexReader getIndexReader() {
		return this.reader;
	}

	public float getFieldLength(String fieldName, int docid) {
		int i = subSearcher(docid); // find searcher index
		return searchables[i].getFieldLength(fieldName, docid - starts[i]);
	}

	public float getNumTokens(String fieldName) {
		float retValue = 0;
		for(int i=0; i < searchables.length; i++){
			retValue += searchables[i].getNumTokens(fieldName);
		}
		return retValue;
	}
	
	
	public float getNumUniqTokens(String fieldName) {
		float retValue = 0;
		for(int i=0; i < searchables.length; i++){
			float tmp = searchables[i].getNumUniqTokens(fieldName);
			if(retValue < tmp){
				retValue = tmp;
			}
		}
		return retValue;
	}

	public float[] termFreqs(Term[] allTermsArray) {
		// TODO Auto-generated method stub
		throw new RuntimeException();
	}

}
