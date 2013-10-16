
package org.apache.lucene.postProcess;

import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.util.ApplicationSetup;

/**
 * This class should be extended by the classes used
 * for weighting temrs and documents.
 * <p><b>Properties:</b><br><ul>
 * <li><tt>rocchio.beta</tt> - defaults to 0.4d</li>
 * <li><tt>parameter.free.expansion</tt> - defaults to true.</li>
 * </ul>
 */
public abstract class QueryExpansionModel {
	public boolean SUPPORT_PARAMETER_FREE_QE = true;
	/** The average document length in the collection. */
    protected float averageDocumentLength;
    /** The total length of the X top-retrieved documents.
     *  X is given by system setting.
     */
    protected float totalDocumentLength;
    
    /** The number of tokens in the collection. */
    protected float collectionLength;
    
	/** The document frequency of the term in the collection.*/
    protected float documentFrequency;
	/** An instance of Idf, in order to compute the logs.*/
	protected Idf idf;
	/** The maximum in-collection term frequencty of the terms in the pseudo relevance set.*/
	protected float maxTermFrequency;
	/** The number of documents in the collection. */
	protected long numberOfDocuments;
	/** The number of top-ranked documents in the pseudo relevance set. */	
	protected float EXPANSION_DOCUMENTS = 
		Integer.parseInt(ApplicationSetup.getProperty("expansion.documents", "3"));
	/** The number of the most weighted terms from the pseudo relevance set 
	 * to be added to the original query. There can be overlap between the 
	 * original query terms and the added terms from the pseudo relevance set.*/
	protected float EXPANSION_TERMS = 
		Integer.parseInt(ApplicationSetup.getProperty("expansion.terms", "10"));
	
	/** Rocchio's beta for query expansion. Its default value is 0.4.*/
	public float ROCCHIO_BETA;
	public float ROCCHIO_GAMMA;
	
	public float LM_ALPHA; //used in feedback model of Language Model. 
	
	/** Boolean variable indicates whether to apply the parameter free query expansion. */
	public boolean PARAMETER_FREE;
	
	/**
	 * Initialises the Rocchio's beta for query expansion.
	 */
	public void initialise() {
		/* Accept both rocchio.beta and rocchio_beta as property name. rocchio_beta will deprecated in due course. */
		ROCCHIO_BETA = Float.parseFloat(ApplicationSetup.getProperty("rocchio.beta", "0.4"));
		ROCCHIO_GAMMA = Float.parseFloat(ApplicationSetup.getProperty("rocchio.gamma", "0.4"));
		PARAMETER_FREE = Boolean.parseBoolean(ApplicationSetup.getProperty("parameter.free.expansion", "true"));
		LM_ALPHA = Float.parseFloat(ApplicationSetup.getProperty("lm.alpha", "0.5"));
	}

	/**
	 * @param numberOfDocuments the numberOfDocuments to set
	 */
	public void setNumberOfDocuments(long numberOfDocuments) {
		this.numberOfDocuments = numberOfDocuments;
	}
	/**
	 *  A default constructor for the class that initialises the idf attribute.
	 */
	public QueryExpansionModel() {
		idf = new Idf();
		this.initialise();
	}
    /**
     * Returns the name of the model.
     * Creation date: (19/06/2003 12:09:55)
     * @return java.lang.String
     */
    public abstract String getInfo();
    
    /**
     * Set the average document length.
     * @param averageDocumentLength float The average document length.
     */
    public void setAverageDocumentLength(float averageDocumentLength){
        this.averageDocumentLength = averageDocumentLength;
    }
    
    /**
     * Set the collection length.
     * @param collectionLength float The number of tokens in the collection.
     */
    public void setCollectionLength(float collectionLength){
        this.collectionLength = collectionLength;
    }
    
    public float getCollectionLength(){
    	return this.collectionLength;
    }
    /**
     * Set the document frequency.
     * @param documentFrequency float The document frequency of a term.
     */
    public void setDocumentFrequency(float documentFrequency){
        this.documentFrequency = documentFrequency;
    }
    
    /**
     * Set the total document length.
     * @param totalDocumentLength float The total document length.
     */
    public void setTotalDocumentLength(float totalDocumentLength){
        this.totalDocumentLength = totalDocumentLength;
    }
    
    /** 
     * This method sets the maximum of the term frequency values of query terms.
     * @param maxTermFrequency
     */
    public void setMaxTermFrequency(float maxTermFrequency){
    	this.maxTermFrequency = maxTermFrequency;
    }
    
    /**
     * This method provides the contract for computing the normaliser of
     * parameter-free query expansion.
     * @return The normaliser.
     */
    public abstract float parameterFreeNormaliser();
    
    /**
     * This method provides the contract for computing the normaliser of
     * parameter-free query expansion.
     * @param maxTermFrequency The maximum of the in-collection term frequency of the terms in the pseudo relevance set.
     * @param collectionLength The number of tokens in the collections.
     * @param totalDocumentLength The sum of the length of the top-ranked documents.
     * @return The normaliser.
     */
    public abstract float parameterFreeNormaliser(float maxTermFrequency, float collectionLength, float totalDocumentLength);
    
	/**
	 * This method provides the contract for implementing query expansion models.
	 * @param withinDocumentFrequency float The term 
	 *        frequency in the X top-retrieved documents.
     * @param termFrequency float The term frequency in the collection.
	 * @return the score assigned to a document with the parameters, 
	 *         and other preset parameters
	 */
	public abstract float score(float withinDocumentFrequency, float termFrequency, float df);
	
	/**
	 * This method provides the contract for implementing query expansion models.
     * For some models, we have to set the beta and the documentFrequency of a term.
	 * @param withinDocumentFrequency float The term frequency in the X top-retrieved documents.
     * @param termFrequency float The term frequency in the collection.
     * @param totalDocumentLength float The sum of length of the X top-retrieved documents.
     * @param collectionLength float The number of tokens in the whole collection.
     * @param averageDocumentLength float The average document length in the collection.
	 * @return float The score returned by the implemented model.
	 */
	public abstract float score(
        float withinDocumentFrequency, 
        float termFrequency,
        float totalDocumentLength, 
        float collectionLength, 
        float averageDocumentLength,
        float df
    );   
}
