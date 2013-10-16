package org.apache.lucene.search;



/** @author yezheng
 * This implement is for academic search */

public class RDefaultSimilarity extends Similarity {
	
  /** store the document length directly */
  public float lengthNorm(String fieldName, int numTerms) {
    return (float)numTerms;
  }
  
  /** Implemented as <code>1/sqrt(sumOfSquaredWeights)</code>. */
  public float queryNorm(float sumOfSquaredWeights) {
	  return 1.0f;
//    return (float)(1.0 / Math.sqrt(sumOfSquaredWeights));
  }

  /** Implemented as <code>sqrt(freq)</code>. */
  public float tf(float freq) {
    return (float)Math.sqrt(freq);
  }
    
  /** Implemented as <code>1 / (distance + 1)</code>. */
  public float sloppyFreq(int distance) {
    return 1.0f / (distance + 1);
//	  return 1f; 
  }
    
  /** Implemented as <code>log(numDocs/(docFreq+1)) + 1</code>. */
  public float idf(int docFreq, int numDocs) {
    return (float)(Math.log(numDocs/(double)(docFreq+1)) + 1.0);
  }
    
  /** Implemented as <code>overlap / maxOverlap</code>. */
  public float coord(int overlap, int maxOverlap) {
    return 1f;
  }
  
}
