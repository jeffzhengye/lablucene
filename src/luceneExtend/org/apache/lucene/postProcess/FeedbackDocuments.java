package org.apache.lucene.postProcess;

import java.util.Arrays;

/**
 * Class representing feedback documents, pseudo- or otherwise.
 * 
 * @since 3.0
 */
public class FeedbackDocuments {
	public int docid[];
	public int rank[];
	public float score[];
	public float doclen[];

	public float totalDocumentLength;

	public FeedbackDocuments() {

	}

	public FeedbackDocuments(int docid[], int rank[], float score[],
			float totalDocumentLength) {
		this.docid = docid;
		this.rank = rank;
		this.score = score;
		this.totalDocumentLength = totalDocumentLength;
	}

	public FeedbackDocuments(int docid[], int rank[], float score[],
			float totalDocumentLength, float doclen[]) {
		this.docid = docid;
		this.rank = rank;
		this.score = score;
		this.totalDocumentLength = totalDocumentLength;
		this.doclen = doclen;
	}

	public FeedbackDocuments toTopK(int effdoc) {
		if(effdoc > docid.length){
			return this;
		}
		float doclen_sub[] = Arrays.copyOfRange(doclen, 0, effdoc);
		float doc_length = org.dutir.util.Arrays.sum(doclen_sub);
		return new FeedbackDocuments(Arrays.copyOfRange(docid, 0, effdoc),
				Arrays.copyOfRange(rank, 0, effdoc), Arrays.copyOfRange(score,
						0, effdoc), doc_length, doclen_sub);
	}
	
	public static void main(String args[]){
		int a[] = {1,2,3,4};
		int b[] = Arrays.copyOfRange(a, 0, a.length);
		System.out.println(b[a.length - 1]);
	}
}
