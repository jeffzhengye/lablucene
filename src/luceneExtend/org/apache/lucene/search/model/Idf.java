
package org.apache.lucene.search.model;
import java.io.Serializable;

/**
 * This class computes the idf values for specific terms in the collection.
 */
public final class Idf implements Serializable, Cloneable{
	
	public static final float base = 2f;
	/** The natural logarithm of 2, used to change the base of logarithms.*/
	public static final float LOG_2_OF_E = (float)Math.log(2.0D);
	/** The reciprocal of CONSTANT, computed for efficiency.*/
	public static final float REC_LOG_2_OF_E = 1.0f / LOG_2_OF_E;
	/** The number of documents in the collection.*/
	private float numberOfDocuments;
	/** A default constructor. NOTE: You must set the number of documents
	  * if you intend to use the idf* functions in this class */
	public Idf() {}

	/** Make a perfect clone of this object */
	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError(e.toString());
		}
	}
	
	/** 
	 * A constructor specifying the number of documents in the collection.
	 * @param docs The number of documents.
	 */
	public Idf(float docs) {
		numberOfDocuments = docs;
	}
	
	/**
	 * Returns the idf of d.
	 * @param d The given term frequency
	 * @return the base 2 log of numberOfDocuments/d
	 */
	public final float idf(float d) {
		return (float) (Math.log(numberOfDocuments/d) * REC_LOG_2_OF_E);
	}
	
	/**
	 * Returns the idf of the given number d.
	 * @param d the number for which the idf will be computed.
	 * @return the idf of the given number d.
	 */
	public final float idf(int d) {
		return (float) (Math.log(numberOfDocuments/((float)d)) * REC_LOG_2_OF_E);
	}
	
	/**
	 * Returns the idf of d.
	 * @param d The given term frequency
	 * @return the base 2 log of numberOfDocuments/d
	 */
	public final float idfDFR(float d) {
		return (float)(Math.log((numberOfDocuments+1)/(d+0.5)) * REC_LOG_2_OF_E);
	}
	
	/**
	 * Returns the idf of the given number d.
	 * @param d the number for which the idf will be computed.
	 * @return the idf of the given number d.
	 */
	public final float idfDFR(int d) {
		return (float)(Math.log((numberOfDocuments+1)/((float)d+0.5d)) * REC_LOG_2_OF_E);
	}
	
	/**
	 * The INQUERY idf formula. We need to check again this formula, 
	 * as it seems that there is a bug in the expression
	 * numberOfDocuments - d / d.
	 * @param d the number for which the idf will be computed
	 * @return the INQUERY idf of the number d
	 */
	public final float idfENQUIRY(float d) {
		return (float)(Math.log(numberOfDocuments - d / d) * REC_LOG_2_OF_E);
	}
	public final float idfBM25(float d) {
		return (float)(Math.log((numberOfDocuments - d + 0.5f)
				/ (d + 0.5f)) * REC_LOG_2_OF_E);
	}
	public final static float idfBM25(float d, int maxDoc) {
		return (float)(Math.log((maxDoc - d + 0.5f)
				/ (d + 0.5f)) * REC_LOG_2_OF_E);
	}
	
	/**
	 * Return the normalised idf of the given number.
	 * @param d The number of which the idf is computed.
	 * @return the normalised idf of d
	 */
	public final float idfN(float d) {
		return (log(numberOfDocuments, d) / log(numberOfDocuments));
	}
	
	public void setNumberOfDocuments(float N){
		this.numberOfDocuments = N;
	}
	
	/**
	 * Return the normalised idf of the given number.
	 * @param d The number of which the idf is computed.
	 * @return the normalised idf of d
	 */
	public final float idfN(int d) {
		return (log(numberOfDocuments, (float)d) / log(numberOfDocuments));
	}
	
	/**
	 * The normalised INQUERY idf formula
	 * @param d the number for which we will compute the normalised idf
	 * @return the normalised INQUERY idf of d
	 */
	public final float idfNENQUIRY(float d) {
		return (log(numberOfDocuments + 1.0f, d + 0.5f) / log(numberOfDocuments+1.0f));
	}
	
	/**
	 * Returns the base 2 log of the given float precision number.
	 * @param tf The number of which the log we will compute
	 * @return the base 2 log of the given number
	 */
	public static final float log(double tf) { 
		return (float) (Math.log(tf) * REC_LOG_2_OF_E);
	}
	
	public static final float log(float tf) {
		return (float) (Math.log(tf) * REC_LOG_2_OF_E);
	}
	
	public static final float exp(float score){
		return (float) Math.pow(base, score);
	}
	/**
	 * Returns the base 2 log of d1 over d2
	 * @param d1 the nominator
	 * @param d2 the denominator
	 * @return the base 2 log of d1/d2
	 */
	public static final float log(float d1, float d2) {
		return (float)(Math.log(d1/d2) * REC_LOG_2_OF_E);
	}

}
