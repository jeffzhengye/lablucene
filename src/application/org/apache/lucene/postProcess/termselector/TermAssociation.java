/**
 * 
 */
package org.apache.lucene.postProcess.termselector;

import java.io.IOException;

import org.apache.commons.math.distribution.NormalDistributionImpl;
import org.apache.log4j.Logger;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Distance;
import org.dutir.math.matrix.DenseMatrix;
import org.dutir.util.symbol.MapSymbolTable;
import org.dutir.util.symbol.SymbolTable;


/**
 * for TopicTermSelector.java
 * @author zheng
 *
 */

public class TermAssociation {

	static Logger logger = Logger.getLogger(TermAssociation.class);
	static int proxType = Integer.parseInt(ApplicationSetup.getProperty("TermAssociation.proxType", "2"));
	final DenseMatrix mMatrix;
	final SymbolTable mSymbolTable;
	//square sigma for normal distribution. 
	static double sd = Double.parseDouble(ApplicationSetup.getProperty("TermAssociation.sd", "1"));
	static NormalDistributionImpl nDist = new NormalDistributionImpl(0, sd);
	
	
	public TermAssociation (DenseMatrix matrix, SymbolTable symbolTable){
		mMatrix = matrix;
		mSymbolTable = symbolTable;
	}
	
	
	public double conditionProb(int x, int givenY){
		try {
			return mMatrix.value(givenY, x);
		} catch (Exception e) {
			logger.error("contitionProb: " + mMatrix.numRows() + ", " + mSymbolTable.numSymbols());
			logger.error("contitionProb: ", e);
		}
		return 0;
	}
	
	public double conditionProb(String x, String giveY){
		int idX = mSymbolTable.symbolToID(x);
		int idY = mSymbolTable.symbolToID(giveY);
		return conditionProb(idX, idY);
	}

	public static TermAssociation built(Searcher searcher,
			TopDocCollector topDoc, MapSymbolTable coTable, String field, int winSize) {
		ScoreDoc scoreDocs[] = topDoc.topDocs().scoreDocs;
		String termCache[][] = new String[scoreDocs.length][]; 
		int termFreq[][] = new int[scoreDocs.length][]; 
		int positions[][][] = new int[scoreDocs.length][][]; 
		int docLens[] = new int[scoreDocs.length];
		for (int i = 0; i < scoreDocs.length; i++) {
			int docid = scoreDocs[i].doc;
			TermPositionVector vec = null;
			try {
//				vec = searcher.getIndexReader().getTermFreqVector(docid,
//						field);
				vec = (TermPositionVector) searcher
				.getIndexReader().getTermFreqVector(docid, field);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (vec == null)
				logger.warn("document " + docid + " not found, field=" + field);
			else {
				String strterms[] = vec.getTerms();
				int freqs[] = vec.getTermFrequencies();
				termCache[i] = strterms;
				termFreq[i] = freqs; 
				docLens[i] = org.dutir.util.Arrays.sum(freqs); 
				positions[i] = new int[strterms.length][];
				for (int k = 0; k < strterms.length; k++) {
					coTable.getOrAddSymbol(strterms[k]);
					positions[i][k] = vec.getTermPositions(k);
				}
			}
		}
		logger.debug("\tfinished reading doc position info for building term associations: " + coTable.numSymbols());
		DenseMatrix matrix = new DenseMatrix(coTable.numSymbols(), coTable.numSymbols());
		
		for(int i =0; i < scoreDocs.length; i++){
			for(int j=0; j < termCache[i].length -1 ; j++){
				int id1 = coTable.symbolToID(termCache[i][j]); 
				if(id1 == -1 ){
					continue;
				}else{
					double time = getTimes(positions[i][j], positions[i][j],  winSize, docLens[i]);
					double total = time + matrix.value(id1, id1);
					matrix.setValue(id1, id1, total);
				}
				for(int k =1; k < termCache[i].length; k++){
					int id2 = coTable.symbolToID(termCache[i][k]); 
					if(id2 == -1 ){
						continue;
					}
//					int time = Distance.noTimes(positions[i][j], positions[i][k], winSize, docLens[i]);
//					int time = Distance.unorderHALTimes(positions[i][j], positions[i][k], winSize);
					double time = getTimes(positions[i][j], positions[i][k], winSize, docLens[i]);
					if(time == 0){
						continue;
					}
					double total = time + matrix.value(id1, id2);
					matrix.setValue(id1, id2, total);
					matrix.setValue(id2, id1, total);
				}
			}
		}
//		matrix.normalizeRows(); 
//		matrix.normalizeColumns();
		logger.debug("\tfinished building local term association dictionary:" + coTable.numSymbols());
//		logger.debug("\n" + matrix.toString());
		
		return new TermAssociation(matrix, coTable);
	}


	static double getTimes(int[] p1, int[] p2, int winSize, int docLen){
		if(proxType == 1){//HAL
			return Distance.unorderHALTimes(p1, p2, winSize);
		}else if(proxType == 2){// unorder
			return Distance.noTimes(p1, p2, winSize, docLen);
		}else if(proxType ==3 ){ // 
			return Distance.bigramFrequency(p1, p2, winSize);
		}else if (proxType == 4){
			return Distance.unorderGaussianTimes(p1, p2, winSize, nDist);
		}
		
		return 0; 
	}
	
	public double conditionProb(int[] xs, int givenY) {
		double retV =0;
		for(int i=0; i < xs.length; i++){
			retV += conditionProb(xs[i], givenY);
		}
		return retV / xs.length;
	}
	
	public static void main(String args[]){
		for(int i=0; i < 10; i++){
			System.out.println(i);
			if(i == 5){
				return;
			}
		}
	}
}
