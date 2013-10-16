//package org.apache.lucene.search;
//
//import java.io.IOException;
//
//import org.apache.lucene.index.IndexReader;
//import org.apache.lucene.index.TermDocs;
//import org.apache.lucene.search.model.Model;
//import org.apache.lucene.search.model.WeightModelManager;
//import org.apache.lucene.search.model.WeightingModel;
//
//public class RBM25FTermWeight extends RTermWeight {
//	
//	String[] fields;
//	float[] boosts;
//	String idfField;
//	Searcher searcher;
//	
//	/**
//	 * this is method must be overwrote, if u want to implement 
//	 * your own weighting model that is different from the framework of BM25, DFR.
//	 * 
//	 * @param searcher
//	 * @param query
//	 */
//	public void initial(Searcher searcher, RTermQuery query){
//		try {
//			this.searcher = searcher;
//			this.similarity = searcher.getSimilarity();
//			this.weightModel = WeightModelManager.getFromPropertyFile();
//			this.query = query;
//			this.term = query.getTerm();
//			
//			
//			
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//	
//	public String getLongestField(String fields[]){
//		float max =0;
//		String lfield = null;
//		for(int i=0; i < fields.length; i++){
//			float avelen = this.searcher.getAverageLength(fields[i]);
//			if(max < avelen){
//				max = avelen;
//				lfield = fields[i];
//			}
//		}
//		return lfield;
//	}
//	
//	public float getAverageLength(String string) {
//		// TODO Auto-generated method stub
//		return this.searcher.getAverageLength(string);
//	}
//	
//	public Scorer scorer(IndexReader reader) throws IOException {
//		return new BM25FTermScorer(this, this.searcher, reader, query,  this.similarity, weightModel);
//	}
//}
