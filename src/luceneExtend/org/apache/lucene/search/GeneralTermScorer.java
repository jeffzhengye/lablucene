package org.apache.lucene.search;


import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.model.WeightingModel;

/**
 * Expert: A <code>Scorer</code> for documents matching a <code>Term</code>.
 */
final class GeneralTermScorer extends RScorer {
	private RTermWeight weight;
	private TermDocs termDocs;
	private byte[] norms;
	private float weightValue;
	private int doc;

	private final int[] docs = new int[32]; // buffered doc numbers
	private final int[] freqs = new int[32]; // buffered term freqs
	private int pointer;
	private int pointerMax;

	private static final int SCORE_CACHE_SIZE = 32;
	private float[] scoreCache = new float[SCORE_CACHE_SIZE];

	WeightingModel weightModel = null;

	IndexReader reader = null;

	public void setSearcher(IndexReader reader) {
		this.reader = reader;
	}

	/**
	 * Construct a <code>TermScorer</code>.
	 * 
	 * @param weight
	 *            The weight of the <code>Term</code> in the query.
	 * @param td
	 *            An iterator over the documents matching the <code>Term</code>.
	 * @param similarity
	 *            The </code>Similarity</code> implementation to be used for
	 *            score computations.
	 * @param norms
	 *            The field norms of the document fields for the
	 *            <code>Term</code>.
	 */
	GeneralTermScorer(RTermWeight weight, TermDocs td, Similarity similarity,
			byte[] norms) {
		super(similarity);
		this.weight = weight;
		this.termDocs = td;
		this.norms = norms;
		this.weightValue = weight.getValue(); // equal to the boost the
												// RTermQuery
		this.weightModel = weight.getweightModel();
	}

	public void score(HitCollector hc) throws IOException {
		next();
		score(hc, Integer.MAX_VALUE);
	}

	/**
	 * Returns the current document number matching the query. Initially
	 * invalid, until {@link #next()} is called the first time.
	 */
	public int doc() {
		return doc;
	}

	/**
	 * Advances to the next document matching the query. <br>
	 * The iterator over the matching documents is buffered using
	 * {@link TermDocs#read(int[],int[])}.
	 * 
	 * @return true iff there is another document matching the query.
	 */
	public boolean next() throws IOException {
		pointer++;
		if (pointer >= pointerMax) {
			pointerMax = termDocs.read(docs, freqs); // refill buffer
			if (pointerMax != 0) {
				pointer = 0;
			} else {
				termDocs.close(); // close stream
				doc = Integer.MAX_VALUE; // set to sentinel value
				return false;
			}
		}
		doc = docs[pointer];
		return true;
	}

	public float score() {
		return score(docs[pointer]);
		// float length = 0f;
		// float norm = Similarity.decodeNorm(this.norms[docs[pointer]]);
		// length = 1 / (norm * norm);
		//		
		// int type = weightModel.getType();
		// float rscore = weightValue ; //weightValue equals to the boost
		// if(type == Model.TYPE_PBW ){
		// rscore *= ((WeightingModel)weightModel).score(freqs[pointer],
		// length);
		// // rscore *= weightModel.score(freqs[pointer], length,
		// weight.getAverFieldLength());
		// }else if (type ==Model.TYPE_LM){
		// rscore *= ((WeightingModel)weightModel).score(freqs[pointer],
		// length);
		// }
		// // if(doc == 29341){
		// // System.out.println(this.weight.getQuery() + ":" + rscore);
		// // System.out.println("kf="+
		// ((WeightingModel)weightModel).getKeyFrequency() +", tf=" +
		// freqs[pointer] + " ,dl=" + length + " ,df=" +
		// ((WeightingModel)weightModel).getDocumentFrequency());
		// //
		// // }
		// // System.out.println("Doc " + doc() +" " +
		// this.weight.getTerm().text() + ": " + rscore);
		// return rscore;
	}

	@Override
	public float score(int currentDoc) {
		float length = 0f;
		float norm = Similarity.decodeNorm(this.norms[currentDoc]);
		length = 1 / (norm * norm);

		float rscore = weightValue; // weightValue equals to the boost
		if (doc == currentDoc) {
			rscore *= weightModel.score(freqs[pointer],
					length, currentDoc);
		} else {
			rscore *= weightModel.unseenScore(length, currentDoc);
		}
//		if (rscore == Float.NEGATIVE_INFINITY
//				|| rscore == Float.POSITIVE_INFINITY) {
//			System.out.println("" + freqs[pointer] + "," + length
//					+ ", " + ((WeightingModel) weightModel).getDocumentFrequency()
//					+ ", " + ((WeightingModel) weightModel).getKeyFrequency());
//			System.exit(1);
//		}
		return rscore;
	}

	/**
	 * Skips to the first match beyond the current whose document number is
	 * greater than or equal to a given target. <br>
	 * The implementation uses {@link TermDocs#skipTo(int)}.
	 * 
	 * @param target
	 *            The target document number.
	 * @return true iff there is such a match.
	 */
	public boolean skipTo(int target) throws IOException {
		// first scan in cache
		for (pointer++; pointer < pointerMax; pointer++) {
			if (docs[pointer] >= target) {
				doc = docs[pointer];
				return true;
			}
		}

		// not found in cache, seek underlying stream
		boolean result = termDocs.skipTo(target);
		if (result) {
			pointerMax = 1;
			pointer = 0;
			docs[pointer] = doc = termDocs.doc();
			freqs[pointer] = termDocs.freq();
		} else {
			doc = Integer.MAX_VALUE;
		}
		return result;
	}

	/**
	 * Returns an explanation of the score for a document. <br>
	 * When this method is used, the {@link #next()} method and the
	 * {@link #score(HitCollector)} method should not be used.
	 * 
	 * @param doc
	 *            The document number for the explanation.
	 */
	public Explanation explain(int doc) throws IOException {
		RTermQuery query = (RTermQuery) weight.getQuery();
		Explanation tfExplanation = new Explanation();
		int tf = 0;
		while (pointer < pointerMax) {
			if (docs[pointer] == doc)
				tf = freqs[pointer];
			pointer++;
		}
		if (tf == 0) {
			if (termDocs.skipTo(doc)) {
				if (termDocs.doc() == doc) {
					tf = termDocs.freq();
				}
			}
		}
		float length = 0f;
		float norm = Similarity.decodeNorm(this.norms[doc]);
		length = 1 / (norm * norm);
		float rscore = weightValue; // weightValue equals to the boost
		rscore *= ((WeightingModel) weightModel).score(tf, length);

		termDocs.close();

		tfExplanation.setValue(rscore);
		// tfExplanation.setDescription("tf(termFreq(" + query.getTerm() + ")="
		// + tf + ")");
		StringBuilder buf = new StringBuilder();
		buf.append("kf=" + ((WeightingModel) weightModel).getKeyFrequency()
				+ ", tf=" + tf + " ,dl=" + length + " ,df="
				+ ((WeightingModel) weightModel).getDocumentFrequency());
		tfExplanation.setDescription(buf.toString());
		return tfExplanation;
	}

	/** Returns a string representation of this <code>TermScorer</code>. */
	public String toString() {
		return "scorer(" + weight + ")";
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		boolean combiningTag = true;
		System.out.println((combiningTag ==true ?("Comb"+ 0.5):""));
	}
}
