/**
 * 
 */
package sample;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.AnalyzerManager;
import org.apache.lucene.search.RangeQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.dutir.lucene.ISManager;

/**
 * @author yezheng
 *
 */
@SuppressWarnings("deprecation")
public class DateRangeSearch {
	static RAMDirectory directory = new RAMDirectory();
	static IndexWriter writer = null;
	
	static {
		try {
			writer = new IndexWriter(directory, AnalyzerManager
					.getFromPropertyFile());
			Document doc1 = new Document();
			doc1.add(new Field("date", "20051226", Field.Store.YES, Field.Index.NOT_ANALYZED));
			
			Document doc2 = new Document();
			doc2.add(new Field("date", "20011226", Field.Store.YES, Field.Index.NOT_ANALYZED));
			
			
			Document doc3 = new Document();
			doc3.add(new Field("date", "20011226", Field.Store.YES, Field.Index.NOT_ANALYZED));
			
			Document doc4 = new Document();
			doc4.add(new Field("date", "20010926", Field.Store.YES, Field.Index.NOT_ANALYZED));
			
			writer.addDocument(doc1);
			writer.addDocument(doc2);
			writer.addDocument(doc3);
			writer.addDocument(doc4);
//			writer.addDocument(doc1);
//			writer.addDocument(doc1);
			
			writer.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void lessThan() throws CorruptIndexException, IOException{
		
//		DateField.MIN_DATE_STRING()
		Term lowerTerm = new Term("date", "00000000"); 
		Term upperTerm = new Term("date", "20171226");
		RangeQuery rquery = new RangeQuery(lowerTerm, upperTerm, true);
		Searcher searcher = ISManager.getSearcheFromPropertyFile();
			//new IndexSearcher(directory);
		TopDocs tdocs = searcher.search(rquery, null, 15);
		
		System.out.println(tdocs.totalHits);
		Document doc = searcher.doc(1);
		List list = doc.getFields();
		String date = doc.get("date");
		System.out.println(date);
		System.out.println(list);
	}
	
	public static void between(){
		
	}
	/**
	 * @param args
	 * @throws IOException 
	 * @throws CorruptIndexException 
	 */
	public static void main(String[] args) throws Exception {
		lessThan();
	}

}
