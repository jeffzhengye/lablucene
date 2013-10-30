/**
 * 
 */
package org.apache.lucene.postProcess.termselector;

/**
 * 
 * 
 * The fbTermInfo class is used to store Information of feedback terms, including number of feedback document,
 * document id in the index, tf in each feedback document, feedback document length, feedback term weight in each
 * feedback document, position vectors in all feedback documents and  collection probability
 * @author Jun Miao 
 * @since 16/10/2013
 */

public class FbTermInfo {
	/**
	 * 
	 */
	protected int docNumber;
	private int docIds[];
	private double tfPerDoc[];
	private int fbDoclength[];
	private double weightPerDoc[];
	private int positionPerDoc[][] = null;
	private double collectionProbability = -1;
	
	protected void setdocIds(int docId, int index) {
		docIds[index] = docId;
	}

	protected double getdocIds(int index) {
		return docIds[index];
	}

	protected void setTfPerDoc(double tf, int index) {
		tfPerDoc[index] = tf;
	}

	protected double getTfPerDoc(int index) {
		return tfPerDoc[index];
	}

	protected void setfbDocLength(int docLength, int index) {
		fbDoclength[index] = docLength;
	}

	protected int getfbDocLength(int index) {
		return fbDoclength[index];
	}

	protected void setWeightPerDoc(double weight, int index) {
		weightPerDoc[index] = weight;
	}

	protected double getWeightPerDoc(int index) {
		return weightPerDoc[index];
	}

	protected void setpositionPerDoc(int[] positions, int index) {
		positionPerDoc[index] = positions;
	}

	protected int[] getpositionPerDoc(int index) {
		return positionPerDoc[index];
	}

	protected void setcollectionProbability(double colprobability) {
		collectionProbability = colprobability;
	}

	protected double getcollectionProbability() {
		return collectionProbability;
	}

	public FbTermInfo(int len) {
		docIds = new int[len];
		tfPerDoc = new double[len];
		fbDoclength = new int[len];
		weightPerDoc = new double[len];
		positionPerDoc = new int[len][];
		java.util.Arrays.fill(docIds, 0);
		java.util.Arrays.fill(tfPerDoc, 0);
		java.util.Arrays.fill(fbDoclength, 0);
		java.util.Arrays.fill(weightPerDoc, 0);
		docNumber = len;
		
		
	}
}


