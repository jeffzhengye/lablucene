/*
 * Terrier - Terabyte Retriever
 * Webpage: http://terrier.org
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.ac.gla.uk
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the LiCense for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is TRECQuerying.java.
 *
 * The Original Code is Copyright (C) 2004-2011 the University of Glasgow.
 * All Rights Reserved.
 *
*/

/*
* This file is probably based on a class with the same name from Terrier, 
* so we keep the copyright head here. If you have any question, please notify me first.
* Thanks. 
*/
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
