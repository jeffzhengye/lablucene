/**
 * 
 */
package org.apache.lucene.search.model;

/**
 * @author yezheng
 *
 */
public class DPH extends WeightingModel {
	private float k = 0.5f;
	/* (non-Javadoc)
	 * @see org.apache.lucene.search.model.WeightingModel#getInfo()
	 */
	public final String getInfo() {
		return "DPH";
	}

	/* (non-Javadoc)
	 * @see org.apache.lucene.search.model.WeightingModel#score(float, float)
	 * Note: tf must > 1 (for PhraseQuery), or the return value could be negative. 
	 */
	@Override
	public float score(float tf, float docLength) {
		float f = tf/docLength;
 		float norm = (1f-f) * (1f -f)/(tf+1f);
 
 		return (float) (keyFrequency* norm
 			 * (tf*i.log ((tf*
			averageDocumentLength/docLength) *
			( numberOfDocuments/termFrequency) )
 			   + 0.5d* Idf.log((float) (2f*Math.PI*tf*(1f-f)))
 			 ));
	}
	
	public float unseenScore(float docLength){
 		return 0;
	}
	/* (non-Javadoc)
	 * @see org.apache.lucene.search.model.WeightingModel#score(float, float, float, float, float)
	 */
	public float score(float tf, float docLength, float n_t, float F_t,
			float keyFrequency) {
//		float tf,
//		float docLength,
//		float n_t,
//		double F_t) {
        float f = tf/docLength;
 		float norm = (1f-f) * (1f -f)/(tf+1f);
 
 		return (float) (keyFrequency* norm
 			 * (tf*i.log ((tf*
			averageDocumentLength/docLength) *
			( numberOfDocuments/F_t) )
 			   + 0.5d* Idf.log((float) (2d*Math.PI*tf*(1d-f)))
 			   ));
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
}
