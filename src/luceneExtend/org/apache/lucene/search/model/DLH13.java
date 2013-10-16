
package org.apache.lucene.search.model;
/**
 * This class implements the DLH13 weighting model. This is a parameter-free
 * weighting model. Even if the user specifies a parameter value, it will <b>NOT</b>
 * affect the results. It is highly recomended to use the model with query expansion. 
 */
public class DLH13 extends WeightingModel {
	private float k = 0.5f;
	/** 
	 * A default constructor.
	 */
	public DLH13() {
		super();
	}
	
	/**
	 * Returns the name of the model.
	 * @return the name of the model
	 */
	public final String getInfo() {
		return "DLH13";
	}
	/**
	 * Uses DLH13 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given 
	 *         tf and docLength, and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		float f  = tf/docLength ;
  		return 
			 keyFrequency
			* (tf*Idf.log ((tf* averageDocumentLength/docLength) *
					( numberOfDocuments/termFrequency) )
			   + 0.5f* Idf.log(2f* (float)Math.PI*tf*(1f-f)))
			   /(tf + k);
	}
	public float unseenScore(float length){
		return 0;
	}
	/**
	 * Uses DLH13 to compute a weight for a term in a document.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @param n_t The document frequency of the term
	 * @param F_t the term frequency in the collection
	 * @param keyFrequency the term frequency in the query
	 * @return the score assigned by the weighting model DLH13.
	 */
	public final float score(
		float tf,
		float docLength,
		float n_t,
		float F_t,
		float keyFrequency) {
		float f  = tf/docLength ;
  		return 
			 keyFrequency
			* (tf*Idf.log ((tf* averageDocumentLength/docLength) *( numberOfDocuments/F_t) )
			   + 0.5f* Idf.log(2f* (float)Math.PI*tf*(1f-f)))
			   /(tf + k);
	}
	
}
