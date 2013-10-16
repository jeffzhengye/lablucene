/**
 * 
 */
package org.apache.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.Files;


/**
 * @author yezheng
 * convert standard trec result file to relevant feedback file for lucene.
 */
public class TrecStandard2rfb {

	static TRECQrelsInMemory trecR = new TRECQrelsInMemory();
	
	public static void convert(String filename) throws IOException{
		BufferedReader br = Files.openFileReader(filename);
		BufferedWriter bw = new BufferedWriter(new FileWriter(filename + ".qrel"));
		String line = null;
		// for each line in the feedback (qrels) file
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.length() == 0) {
				continue;
			}

			// split line into space-separated pieces
			String[] pieces = line.split("\\s+");

			// grab topic id
			String topId = pieces[0];
			
			int pos = topId.indexOf("-");
			String numId = topId.substring(pos +1);
			// grab docno
			String docNo = pieces[2];
			// grab relevance judgment of docno with respect to this topic
			boolean relevant = trecR.isRelevant(numId, docNo);
			if(relevant){
				pieces[3] = "" +1;
			}else{
				pieces[3] = "" + 0;
			}
			bw.write(concate(pieces) + "\n");
		}
		bw.close();
		br.close();
	}
	
	private static String concate(String[] pieces) {
		StringBuilder buf = new StringBuilder();
		for(int i=0; i < pieces.length; i++){
			buf.append(pieces[i] + " ");
		}
		return buf.toString();
	}
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		String path = "/home/yezheng/corpus/TREC/Clueweb09/topics/rf09_phase1/assignments/YUIR.1";
		convert(path);
	}

}
