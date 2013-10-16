
package org.apache.lucene.search.model;
import java.io.Serializable;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.RTermQuery;
import org.apache.lucene.search.Searcher;
import org.dutir.lucene.util.ApplicationSetup;

public abstract class WeightingModel implements Serializable,Cloneable {
	private static final long serialVersionUID = 1L;
	/** The class used for computing the idf values.*/
	protected Idf i = null;
	/** The average length of documents in the collection.*/
	public float averageDocumentLength;
	/** The term frequency in the query.*/
	public float keyFrequency;
	/** The document frequency of the term in the collection.*/
	public float documentFrequency;
	/** The term frequency in the collection.*/
	public float termFrequency;
	/** The number of documents in the collection.*/
	public float numberOfDocuments;
	/** The number of tokens in the collections. */
	public float numberOfTokens;
	/** The parameter c. This defaults to 1.0, but should be set using in the constructor
	  * of each child weighting model to the sensible default for that weighting model. */
	public float c = Float.parseFloat(ApplicationSetup.getProperty("wm.c", "1.0f"));
	/** Number of unique terms in the collection */
	public float numberOfUniqueTerms;	

	/**
	 * A default constructor that initialises the idf i attribute
	 */
	public WeightingModel() {
	}

	/** Clone this weighting model */
	public Object clone() {
		try{
			WeightingModel newModel = (WeightingModel)super.clone();
			newModel.i = (Idf)this.i.clone();
			return newModel;
		} catch (CloneNotSupportedException e) {
			throw new InternalError(e.toString());
		}
	}


	/**
	 * Returns the name of the model.
	 * @return java.lang.String
	 */
	public abstract String getInfo();
	
	public void prepare(Searcher searcher, RTermQuery query)
	{
		Term term = query.getTerm();
		String field = term.field();
		try {
			numberOfDocuments = searcher.maxDoc();
			averageDocumentLength = searcher.getAverageLength(field);
			numberOfTokens = searcher.getNumTokens(field);
			numberOfUniqueTerms = searcher.getNumUniqTokens(field);
			documentFrequency = searcher.docFreq(term);
			this.keyFrequency = query.getOccurNum();
			this.termFrequency = searcher.termFreq(term);
			i = new Idf(numberOfDocuments);
//			System.out.println(term.text() + ": " + documentFrequency);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	
	public void prepare(float numberOfDocuments, float averageDocumentLength, float numberOfTokens, 
			float numberOfUniqueTerms, float documentFrequency, float keyFrequency, float termFrequency)
	{
			this.numberOfDocuments = numberOfDocuments;
			this.averageDocumentLength = averageDocumentLength;
			this.numberOfTokens = numberOfTokens;
			this.numberOfUniqueTerms = numberOfUniqueTerms;
			this.documentFrequency = documentFrequency;
			this.keyFrequency = keyFrequency;
			this.termFrequency = termFrequency;
			i = new Idf(numberOfDocuments);
	}
	
	/**
	 * This method provides the contract for implementing weighting models.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @return the score assigned to a document with the given tf 
	 * and docLength, and other preset parameters
	 */
	public abstract float score(float tf, float docLength);
	/**
	 * This method provides the contract for implementing weighting models.
	 * @param tf The term frequency in the document
	 * @param docLength the document's length
	 * @param n_t The document frequency of the term
	 * @param F_t the term frequency in the collection
	 * @param keyFrequency the term frequency in the query
	 * @return the score returned by the implemented weighting model.
	 */
	public abstract float score(
		float tf,
		float docLength,
		float n_t,
		float F_t,
		float keyFrequency);
	
	/**
	 * this method must be overwrote by a subclass of language model 
	 * 
	 * @param dl the length of a document
	 * @return
	 */
	public float getAlphaD(float dl){
		throw new UnsupportedOperationException();
	}
	
	/**
	 * Sets the average length of documents in the collection.
	 * @param avgDocLength The documents' average length.
	 */
	public void setAverageDocumentLength(float avgDocLength) {
		averageDocumentLength = avgDocLength;
	}
	
	
	/**
	 * Sets the c value
	 * @param c the term frequency normalisation parameter value.
	 */
	public void setParameter(float c) {
		this.c = c;
	}


	/**
	 * Returns the parameter as set by setParameter()
	 */
	public float getParameter() {
		return this.c;
	}

	/**
	 * Sets the document frequency of the term in the collection.
	 * @param docFreq the document frequency of the term in the collection.
	 * 
	 */
	public void setDocumentFrequency(float docFreq) {
		documentFrequency = docFreq;
	}
	
	public float getDocumentFrequency(){
		return documentFrequency;
	}
	/**
	 * Sets the term's frequency in the query.
	 * @param keyFreq the term's frequency in the query.
	 */
	public void setKeyFrequency(float keyFreq) {
		keyFrequency = keyFreq;
	}
	
	public float getKeyFrequency(){
		return keyFrequency;
	}
	
	/**
	 * Set the number of tokens in the collection.
	 * @param value The number of tokens in the collection.
	 * 
	 */
	public void setNumberOfTokens(float value){
		this.numberOfTokens = value;
	}
	/**
	 * Sets the number of documents in the collection.
	 * @param numOfDocs the number of documents in the collection.
	 * 
	 */
	public void setNumberOfDocuments(float numOfDocs) {
		numberOfDocuments = numOfDocs;
		i.setNumberOfDocuments(numOfDocs);
	}
	/**
	 * Sets the term's frequency in the collection.
	 * @param termFreq the term's frequency in the collection.
	 * 
	 */
	public void setTermFrequency(float termFreq) {
		termFrequency = termFreq;
	}
	/**
	 * Set the number of unique terms in the collection.
	 * 
	 */
	public void setNumberOfUniqueTerms(float number) {
		numberOfUniqueTerms = number;
	}
	/**
	* This method provides the contract for implementing the 
	* Stirling formula for the power series.
	* @param n The parameter of the Stirling formula.
	* @param m The parameter of the Stirling formula.
	* @return the approximation of the power series
	*/
	public float stirlingPower(float n, float m) {
		float dif = n - m;
		return (float)( (m + 0.5f) * Idf.log(n / m) + dif * Idf.log(n) );
	}

	public float unseenScore(float length){
		return score(0, length);
	}
}
