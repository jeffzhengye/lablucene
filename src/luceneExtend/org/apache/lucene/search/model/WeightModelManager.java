/**
 * 
 */
package org.apache.lucene.search.model;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.RPhraseQuery;
import org.apache.lucene.search.RTermQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocs;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.TermsCache;

/**
 * @author Yezheng
 *
 */
public class WeightModelManager {

	static WeightingModel fileWeightModle = null;
	static TermsCache tcache = TermsCache.getInstance();
	/**
	 * Caution: The query term related properties are not setup in this method.
	 * Currently, it is used in ModelBasedSelector (PRF) in order to keep constant
	 * consistent with the language model used in first-pass retrieval.
	 * @param searcher
	 * @param field
	 * @return
	 */
	public static WeightingModel getFromPropertyFile(Searcher searcher, RTermQuery query){
		    String strmodel = null;
		    String field = query.getTerm().field();
			strmodel = ApplicationSetup.getProperty("Lucene.Search.WeightingModel", "BM25");
			if(strmodel.indexOf(".") == -1){
				strmodel = "org.apache.lucene.search.model." + strmodel;
			}
			try {
				WeightingModel model = (WeightingModel) Class.forName(strmodel).newInstance();
				int maxDoc = searcher.maxDoc();
				float averageFiledLength = searcher.getAverageLength(field);
				float numberOfTokens = searcher.getNumTokens(field);
				float numberOfUniqueTerms = searcher.getNumUniqTokens(field);
				
				TermsCache.Item item = tcache.getItem(query.getTerm(), searcher);
				float df = item.df;
				float termFreq = item.ctf;
				model.prepare(maxDoc,
							averageFiledLength, numberOfTokens,
							numberOfUniqueTerms, df, query.getOccurNum(), termFreq, query.getqueryLen());
				model.setSearcher(searcher);
				model.setQuery(query);

				return model;
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		return null;
	}
	
	public static WeightingModel getFromPropertyFile(Searcher searcher, RPhraseQuery query){
	    String strmodel = null;
	    String field = query.getTerms()[0].field();
		strmodel = ApplicationSetup.getProperty("Lucene.Search.WeightingModel", "BM25");
//		strmodel = "BM25";
		if(strmodel.indexOf(".") == -1){
			strmodel = "org.apache.lucene.search.model." + strmodel;
		}
		try {
			WeightingModel model = (WeightingModel) Class.forName(strmodel).newInstance();
			int maxDoc = searcher.maxDoc();
			float averageFiledLength = searcher.getAverageLength(field);
			float numberOfTokens = searcher.getNumTokens(field);
			float numberOfUniqueTerms = searcher.getNumUniqTokens(field);
			
			
			
//			TermsCache.Item item = tcache.getItem(query.getTerm(), searcher);
//			float df = item.df;
//			float termFreq = item.ctf;
			/////////////////////////////////////////
			PhraseQuery pquery = new PhraseQuery();
			Term terms[] = query.getTerms();
			int pos[] = query.getPositions();
			for(int i=0; i < terms.length; i++){
				pquery.add(terms[i], pos[i]);
			}
			pquery.setSlop(query.getSlop());
			TopDocs tdocs = searcher.search(pquery, 2);
			float df = tdocs.totalHits;
			//the value of termFreq is estimated as following.  Refer to Indri to find a better solution.
			float termFreq = (float) (df * Idf.log(2 + query.getSlop()));
//			float termFreq = maxDoc / 50f;
			
//			if(termFreq > maxDoc){
//				System.out.println("bingo");
//			}
//			float df = searcher.maxDoc() /100f; 
//			float termFreq = df * 2; 
//			averageFiledLength = (numberOfTokens - maxDoc *(query.getSlop() - 1 ) )/(float)maxDoc;
			model.prepare(maxDoc,
						averageFiledLength, numberOfTokens,
						numberOfUniqueTerms, df, query.getOccurNum(), termFreq, query.getqueryLen());
			model.setSearcher(searcher);
//			model.setQuery(query);
			
			return model;
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	return null;
}
	
	
	/**
	 * 
	 * @param searcher
	 * @param field
	 * @return a unspecified model (w.r.t Query), df and ctf are set to 0. 
	 */
	public static WeightingModel getFromPropertyFile(Searcher searcher, String field){
	    String strmodel = null;
		strmodel = ApplicationSetup.getProperty("Lucene.Search.WeightingModel", "BM25");
		if(strmodel.indexOf(".") == -1){
			strmodel = "org.apache.lucene.search.model." + strmodel;
		}
		try {
			WeightingModel model = (WeightingModel) Class.forName(strmodel).newInstance();
			int maxDoc = searcher.maxDoc();
			float averageFiledLength = searcher.getAverageLength(field);
			float numberOfTokens = searcher.getNumTokens(field);
			float numberOfUniqueTerms = searcher.getNumUniqTokens(field);
			
//			TermsCache.Item item = tcache.getItem(query.getTerm(), searcher);
			float df = 0;
			float termFreq = 0;
			model.prepare(maxDoc, 
						averageFiledLength, numberOfTokens,
						numberOfUniqueTerms, df, 1, termFreq, -1); // this may have problem to set querylength = -1;
			model.setSearcher(searcher);
			return model;
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	return null;
}
	
	public static WeightingModel getFromPropertyFile(){
	    String model = null;
		model = ApplicationSetup.getProperty("Lucene.Search.WeightingModel", "BM25");
		if(model.indexOf(".") == -1){
			model = "org.apache.lucene.search.model." + model;
		}
		try {
			return (WeightingModel)Class.forName(model).newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	
	return null;
}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
