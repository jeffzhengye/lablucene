
package org.apache.lucene.search.model;
/**
 * This class implements the TF_IDF weighting model.
 * tf is given by Robertson's tf and idf is given by the 
 * standard Sparck Jones' idf [Sparck Jones, 1972].
 */
public class TF_IDF extends WeightingModel {
	
	/** model name */
	private static final String name = "TF_IDF";

	/** The constant k_1.*/
	private float k_1 = 1.2f;
	
	/** The constant b.*/
	private float b = 0.75f;

	/** 
	 * A default constructor to make this model.
	 */
	public TF_IDF() {
		super();
	}

	public TF_IDF(float b) {
		this();
		this.b = b;
	}

	/**
	 * Returns the name of the model, in this case "TF_IDF"
	 * @return the name of the model
	 */
	public final String getInfo() {
		return name;
	}
	/**
	 * Uses TF_IDF to compute a weight for a term in a document.
	 * @param tf The term frequency of the term in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given 
	 *		 tf and docLength, and other preset parameters
	 */
	public final float score(float tf, float docLength) {
		float Robertson_tf = k_1*tf/(tf+k_1*(1-b+b*docLength/averageDocumentLength));
		float idf = (float)Idf.log(numberOfDocuments/documentFrequency+1);
		return keyFrequency * Robertson_tf * idf;
	}
	/**
	 * Uses TF_IDF to compute a weight for a term in a document.
	 * @param tf The term frequency of the term in the document
	 * @param docLength the document's length
	 * @param documentFrequency The document frequency of the term (ignored)
	 * @param termFrequency the term frequency in the collection (ignored)
	 * @param keyFrequency the term frequency in the query (ignored).
	 * @return the score assigned by the weighting model TF_IDF.
	 */
	public final float score(
		float tf,
		float docLength,
		float documentFrequency,
		float termFrequency,
		float keyFrequency) 
	{
		float Robertson_tf = k_1*tf/(tf+k_1*(1-b+b*docLength/averageDocumentLength));
		float idf =(float) Idf.log(numberOfDocuments/documentFrequency+1);
		return keyFrequency*Robertson_tf * idf;

	}

	/**
	 * Sets the b parameter to ranking formula
	 * @param b the b parameter value to use.
	 */
	public void setParameter(float b) {
		this.b = b;
	}


	/**
	 * Returns the b parameter to the ranking formula as set by setParameter()
	 */
	public float getParameter() {
		return this.b;
	}
}
