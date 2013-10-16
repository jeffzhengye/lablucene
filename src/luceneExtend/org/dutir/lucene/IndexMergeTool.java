/**
 * 
 */
package org.dutir.lucene;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryParser.AnalyzerManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.dutir.util.Arrays;
import org.dutir.util.stream.StreamGenerator;

/**
 * @author yezheng
 * 
 */
public class IndexMergeTool {

	public static void optimize(String indexPath) {
		Directory dir;
		try {
			Analyzer analyzer = AnalyzerManager.getFromPropertyFile();
			dir = FSDirectory.getDirectory(indexPath);
			IndexWriter writer = new IndexWriter(dir, analyzer, false);
			writer.optimize();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void optimize(String indexPath, String[] existingIndexes) {
		Directory dir;
		try {
			Analyzer analyzer = AnalyzerManager.getFromPropertyFile();
			dir = FSDirectory.getDirectory(indexPath);
			BufferedReader br = StreamGenerator.getConsoleReader();
			System.out.println("input yes to create a new index in " + indexPath +
					", or no to use the existing index in this dir: ");
			System.out.println("input exit to quit" ); 
			String line = null;
			IndexWriter writer = null;
			while((line = br.readLine()) != null){
				if(line.equalsIgnoreCase("yes")){
					writer = new IndexWriter(dir, analyzer, true);
					break;
				}else if(line.equalsIgnoreCase("no")){
					writer = new IndexWriter(dir, analyzer, false);
					break;
				}else if(line.equalsIgnoreCase("exit")){
					System.exit(0);
				}
			}
			Directory edirs[] = new Directory[existingIndexes.length];
			IndexReader readers[] = new IndexReader[existingIndexes.length];
			for(int i=0; i < existingIndexes.length; i++){
				edirs[i] = FSDirectory.getDirectory(existingIndexes[i]);
				readers[i] = IndexReader.open(edirs[i]);
			}
			writer.addIndexes(readers);
			writer.optimize();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void optimize(String[] indexPaths){
		String mergedIndex = indexPaths[0]; 
		String existIndexes[] = new String[indexPaths.length -1];
		System.arraycopy(indexPaths, 1, existIndexes, 0, indexPaths.length -1);
		optimize(mergedIndex, existIndexes);
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
	    if (args.length < 3) {
	        System.err.println("Usage: IndexMergeTool <mergedIndex> <index1> <index2> [index3] ...");
	        System.exit(1);
	      }
	    optimize(args);
//		String path = "/home/yezheng/corpus/TREC/chemistry2009/LowMFIndex/testPatent0-7";
//		String[] epathes = new String[]{"/home/yezheng/corpus/TREC/chemistry2009/multiFieldIndex/multiFieldArticleIndex"};
//		optimize(path, epathes);
	}

}
