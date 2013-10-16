
package org.apache.lucene.search.model;
/**
 * This class implements the TF_IDF weighting model as it is implemented in <a href="http://www.lemurproject.org">Lemur</a>.
 * See <a href="http://www.cs.cmu.edu/~lemur/1.0/tfidf.ps">Notes on the Lemur TFIDF model. Chenxiang Zhai, 2001</a>.
 */
public class LemurTF_IDF extends WeightingModel {
	/** The constant k_1.*/
	private float k_1 = 1.2f;
	
	/** The constant b.*/
	private float b = 0.75f;
	/** 
	 * A default constructor. This must be followed by 
	 * specifying the c value.
	 */
	public LemurTF_IDF() {
		super();
	}
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "LemurTF_IDF";
	}
	/**
	 * Uses LemurTF_IDF to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given 
	 *         tf and docLength, and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		float Robertson_tf = k_1*tf/(tf+k_1*(1-b+b*docLength/averageDocumentLength));
		return (float) (keyFrequency*Robertson_tf * 
				Math.pow(Idf.log(numberOfDocuments/documentFrequency), 2));
	}
	
	public float unseenScore(float length){
		return 0;
	}
	/**
	 * Uses LemurTF_IDF to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @param documentFrequency The document frequency of the term
	 * @param termFrequency the term frequency in the collection
	 * @param keyFrequency the term frequency in the query
	 * @return the score assigned by the weighting model LemurTF_IDF.
	 */
	public final float score(
		float tf,
		float docLength,
		float documentFrequency,
		float termFrequency,
		float keyFrequency) {
		float Robertson_tf = k_1*tf/(tf+k_1*(1-b+b*docLength/averageDocumentLength));
		return (float) (keyFrequency*Robertson_tf * 
				Math.pow(Idf.log(numberOfDocuments/documentFrequency), 2));

	}
	
}
