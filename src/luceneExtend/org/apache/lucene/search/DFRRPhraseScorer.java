/**
 * 
 */
package org.apache.lucene.search;

import java.io.IOException;

import org.apache.lucene.index.TermPositions;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Distance;
import org.dutir.math.function.GammaFunction;
import org.dutir.math.function.WikipediaGammaFunction;

/**
 * @author zheng
 * 
 */
public class DFRRPhraseScorer extends RPhraseScorer {
	private int slop;
	protected static final double REC_LOG_2 = 1.0d / Math.log(2.0d);
	protected static final GammaFunction gf = new GammaFunction();

	// DFRRPhraseScorer(Weight weight, TermPositions[] tps, int[] offsets,
	// Similarity similarity, byte[] norms) {
	// super(weight, tps, offsets, similarity, norms);
	// }

	DFRRPhraseScorer(Weight weight, TermPositions[] tps, int[] offsets,
			Similarity similarity, int slop, byte[] norms) {
		super(weight, tps, offsets, similarity, norms);
		this.slop = slop;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.lucene.search.RPhraseScorer#phraseFreq()
	 */
	@Override
	protected float phraseFreq() throws IOException {
		// sort list with pq
		pq.clear();
		for (PhrasePositions pp = first; pp != null; pp = pp.next) {
			pp.firstPosition();
			pq.put(pp); // build pq from list
		}
		pqToList(); // rebuild list from pq

		// for counting how many times the exact phrase is found in current
		// document,
		// just count how many times all PhrasePosition's have exactly the same
		// position.
		float tfreq = 0;

		float length = 0f;
		float norm = Similarity.decodeNorm(this.norms[this.doc()]);
		length = 1 / (norm * norm);

		int count = first.count + 1;
		int p1[] = null;
		int i = 0;
		try {
			p1 = new int[count];
			i = 0;
			do {
				p1[i++] = first.position + first.offset;
			} while (i < count && first.nextPosition());
		} catch (Exception e) {
			System.out.println("" + count + ", " + i);
			e.printStackTrace();
			System.exit(1);
		}

		count = last.count + 1;
		i = 0;
		int p2[] = new int[count];
		do {
			p2[i++] = last.position + last.offset;
		} while (last.nextPosition());

		tfreq = (float) getTimes(p1, p2, slop, (int) length);
		// System.out.println("len:" + this.doc() + "," +length + ", " + tfreq);
		return tfreq;
	}

	static double getTimes(int[] p1, int[] p2, int winSize, int docLen) {
		int proxType = 2;
		if (proxType == 1) {// HAL
			return Distance.unorderHALTimes(p1, p2, winSize);
		} else if (proxType == 2) {// unorder
			return Distance.noTimes(p1, p2, winSize, docLen);
		} else if (proxType == 3) { // 
			return Distance.bigramFrequency(p1, p2, winSize);
		}
		// else if (proxType == 4){
		// return Distance.unorderGaussianTimes(p1, p2, winSize, nDist);
		// }

		return 0;
	}

	int counter = 0;

//	@Override
//	public float score(int currentDoc) {
//
//		if (freq == 0 || this.doc() != currentDoc)
//			return 0.0f;
//
//		float docLength = 0f;
//		float norm = Similarity.decodeNorm(this.norms[currentDoc]);
//		docLength = 1 / (norm * norm);
//
//		float rscore = value; // weightValue equals to the boost
//
//		float matchingNGrams = freq;
//
//		final int numberOfNGrams = (int) ((docLength > 0 && docLength < slop) ? 1
//				: docLength - slop + 1);
//
//		double score = 0.0d;
//
//		// apply Norm2 to pf?
//		double matchingNGramsNormalised = matchingNGrams;
//
//		double background = numberOfNGrams;
//
//		double p = 1.0D / background;
//		double q = 1.0d - p;
//		score = -gf.compute_log(background + 1.0d) * REC_LOG_2
//				+ gf.compute_log(matchingNGramsNormalised + 1.0d) * REC_LOG_2
//				+ gf.compute_log(background - matchingNGramsNormalised + 1.0d)	* REC_LOG_2 
//				- matchingNGramsNormalised * Math.log(p)* REC_LOG_2 
//				- (background - matchingNGramsNormalised)* Math.log(q) * REC_LOG_2;
//		score = score / (1.0d + matchingNGramsNormalised);
//
//		return (float) (rscore * score);
//	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

	}

}
