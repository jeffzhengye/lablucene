package org.apache.lucene.search;

import java.io.IOException;

public abstract class  RScorer extends Scorer {


	protected RScorer(Similarity similarity) {
		super(similarity);
	}
	@Override
	public float getLogAlphaD(int currentDoc) throws IOException {
		throw new RuntimeException();
	}
	public abstract float score(int currentDoc)throws IOException ;

}
