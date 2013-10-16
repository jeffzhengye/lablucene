//package org.apache.lucene.search;
//
///**
// * BM25FTermScorer.java
// *
// * Copyright (c) 2008 "Joaquín Pérez-Iglesias"
// *
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//import java.io.IOException;
//import java.util.Iterator;
//import java.util.Map.Entry;
//
//import org.apache.lucene.index.IndexReader;
//import org.apache.lucene.index.Term;
//import org.apache.lucene.index.TermDocs;
//import org.apache.lucene.search.Explanation;
//import org.apache.lucene.search.Scorer;
//import org.apache.lucene.search.Similarity;
//import org.apache.lucene.search.TermQuery;
//import org.apache.lucene.search.model.Model;
//import org.apache.lucene.search.model.WeightingModel;
//import org.dutir.lucene.util.ApplicationSetup;
//import org.ninit.models.bm25f.BM25FParameters;
//
///**
// * Calculate the relevance value of a term applying BM25F function ranking. The
// * {@link BM25FParameters} k1,b_field, boost_field are used.<BR>
// * 
// * @author "Joaquin Perez-Iglesias"
// * @see BM25FParameters
// * 
// */
//public class BM25FTermScorer extends Scorer {
//
//	private TermDocs[] termDocs;
//	private RTermQuery term;
//	private float idf = 0f;
//	private IndexReader reader;
//
//	static float K1; 
//	static String[] fields;
//	static float[] boost;
//	static float[] bParam;
//	static{
//		K1 = Float.parseFloat(ApplicationSetup.getProperty("Lucene.BM25F.K1", "0.5"));
//		String line = ApplicationSetup.getProperty("Lucene.BM25F.Fields", "");
//		String cfields[] = line.split("\\s*,\\s*");
//		fields = new String[cfields.length];
//		boost = new float[cfields.length];
//		bParam = new float[cfields.length];
//		for(int i=0; i < cfields.length; i++){
//			String vals[] = cfields[i].split(":");
//			fields[i] = vals[0];
//			boost[i] = Float.parseFloat(vals[1]);
////			bParam[i] = Float.parseFloat(vals[2]);
//		}
//	}
//	
//	private boolean[] termDocsNext;
//	private int doc = Integer.MAX_VALUE;
//	private boolean initializated = false;
//	RBM25FTermWeight weight;
//	Searcher searcher;
//	float df = 0;
//	float avelen = 0;
//	float numTokens =0;
//	float numUniTokens =0;
//	float totleTokens = 0;
//	Model weightModel ;
//	
//	public BM25FTermScorer(RBM25FTermWeight weight, Searcher searcher,  IndexReader reader,
//			RTermQuery term, Similarity similarity, Model weightModel) {
//		super(similarity);
//		this.weight = weight;
//		this.searcher= searcher;
//		this.reader = reader;
//		this.weightModel = weightModel;
//		this.term = term;
//		this.termDocs = new TermDocs[this.fields.length];
//		this.termDocsNext = new boolean[this.fields.length];
//		try {
//			
//			for (int i = 0; i < this.fields.length; i++){
//				
//				Term tterm = new Term(this.fields[i], term.getTerm().text());
//				this.termDocs[i] = reader.termDocs(tterm);
//				float tmpdf = searcher.docFreq(tterm) ;
//				if(df < tmpdf){
//					df = tmpdf;
//				}
//				avelen  += boost[i] * searcher.getAverageLength(this.fields[i]);
//				this.numTokens += searcher.getNumTokens(fields[i]);
//				
//				float tmpnumUniTokens = searcher.getNumUniqTokens(fields[i]);
//				if(this.numUniTokens < tmpnumUniTokens){
//					this.numUniTokens = tmpnumUniTokens;
//				}
//				totleTokens += searcher.termFreq(tterm);
//			}
//			
//			if (weightModel.getType() == Model.TYPE_PBW) {
//				((WeightingModel) this.weightModel).prepare(searcher.maxDoc(),
//						avelen, this.numTokens, this.numUniTokens, df, term
//								.getOccurNum(), this.totleTokens);
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}
//
//
//
//	/**
//	 * 
//	 * @return The field with the longest average length
//	 */
//
//	private String idfField = null;
//
//	private String getIdfField() {
//		if (idfField == null) {
//			idfField = this.weight.getLongestField(fields);
//		}
//		return idfField;
//	}
//
//	/*
//	 * (non-Javadoc)
//	 * 
//	 * @see org.apache.lucene.search.Scorer#doc()
//	 */
//	@Override
//	public int doc() {
//		return this.doc;
//	}
//
//	/*
//	 * (non-Javadoc)
//	 * 
//	 * @see org.apache.lucene.search.Scorer#explain(int)
//	 */
//	public Explanation explain(int doc) throws IOException {
//		if (!this.skipTo(doc))
//			return null;
//		float acum = 0f;
//		Explanation result = new Explanation();
//		Explanation tf = new Explanation();
//		result.setDescription("BM25F (" + this.term.getTerm().text() + ")");
//
//		for (int i = 0; i < this.fields.length; i++) {
//
//			if (this.termDocs[i].doc() == doc) {
//				Explanation partial = new Explanation();
//
//				byte[] norm = this.reader.norms(this.fields[i]);
//
//				float av_length = getAverageLength(this.fields[i]);
//				float length = 1 / ((Similarity.decodeNorm(norm[this.doc()])) * (Similarity
//						.decodeNorm(norm[this.doc()])));
//
//				float aux = 0f;
//				aux = this.bParam[i] * length / av_length;
//
//				aux = aux + 1 - this.bParam[i];
//				acum += this.boost[i] * this.termDocs[i].freq() / aux;
//
//				partial = new Explanation(this.boost[i]
//						* this.termDocs[i].freq() / aux, "(" + this.fields[i]
//						+ ":" + this.term.getTerm().text() + ") B:"
//						+ this.bParam[i] + ",Length:" + length + ",AvgLength:"
//						+ av_length + ",Freq:" + this.termDocs[i].freq()
//						+ ",Boost:" + this.boost[i]);
//				tf.addDetail(partial);
//			}
//		}
//
//		Explanation idfE = new Explanation(this.idf, " idf (docFreq:"
//				+ this.reader.docFreq(new Term(BM25FParameters.getIdfField(),
//						this.term.getTerm().text())) + ",numDocs:"
//				+ this.reader.numDocs() + ")");
//		result.addDetail(idfE);
//
//		tf.setDescription("K1: " + acum + "/(" + acum + " + "
//				+ BM25FParameters.getK1() + ")");
//
//		acum = acum / (BM25FParameters.getK1() + acum);
//		tf.setValue(acum);
//		result.addDetail(tf);
//		acum = acum * this.idf;
//		result.setValue(acum);
//
//		return result;
//	}
//
//	private float getAverageLength(String string) {
//		// TODO Auto-generated method stub
//		return this.weight.getAverageLength(string);
//	}
//
//	private boolean init() throws IOException {
//		boolean result = false;
//		for (int i = 0; i < this.fields.length; i++) {
//			this.termDocsNext[i] = this.termDocs[i].next();
//			if (this.termDocsNext[i] && this.termDocs[i].doc() < this.doc) {
//				result = true;
//				this.doc = this.termDocs[i].doc();
//			}
//		}
//		return result;
//	}
//
//	/*
//	 * (non-Javadoc)
//	 * 
//	 * @see org.apache.lucene.search.Scorer#next()
//	 */
//	@Override
//	public boolean next() throws IOException {
//
//		if (!initializated) {
//			this.initializated = true;
//			return this.init();
//		}
//
//		int min = Integer.MAX_VALUE;
//
//		for (int i = 0; i < this.fields.length; i++) {
//			if (this.termDocsNext[i] && this.termDocs[i].doc() == this.doc) {
//				this.termDocsNext[i] = this.termDocs[i].next();
//			}
//			if (this.termDocsNext[i] && this.termDocs[i].doc() < min)
//				min = this.termDocs[i].doc();
//		}
//		return ((this.doc = min) != Integer.MAX_VALUE);
//	}
//
//	/*
//	 * (non-Javadoc)
//	 * 
//	 * @see org.apache.lucene.search.Scorer#score()
//	 */
//	@Override
//	public float score() throws IOException {
//		float acum = 0f;
//		float freq = 0f;
//		
//		for (int i = 0; i < this.fields.length; i++) {
//
//			if (this.termDocs[i].doc() == doc) {
//				byte[] norm = this.reader.norms(this.fields[i]);
//
//				float av_length = (float) getAverageLength(this.fields[i]);
//				float length = 0f;
//				float normV = Similarity.decodeNorm(norm[this.doc()]);
//				length = 1 / (normV * normV);
//				
//				acum += this.boost[i] * length;
//				freq += this.boost[i] *	this.termDocs[i].freq();
//				
//			}
//		}
//		return ((WeightingModel)this.weightModel).score(freq, acum);
//	}
//
//	private float getK1() {
//		// TODO Auto-generated method stub
//		return K1;
//	}
//
//	/*
//	 * (non-Javadoc)
//	 * 
//	 * @see org.apache.lucene.search.Scorer#skipTo(int)
//	 */
//	@Override
//	public boolean skipTo(int target) throws IOException {
//		while (this.doc() < target && this.next()) {
//		}
//
//		return this.doc() == target;
//	}
//
//}
