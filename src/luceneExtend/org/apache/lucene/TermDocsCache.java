/**
 * 
 */
package org.apache.lucene;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.dutir.lucene.util.ApplicationSetup;

import com.aliasi.util.AbstractExternalizable;
import com.aliasi.util.FastCache;



/**
 * @author yezheng
 *
 */
public class TermDocsCache implements TermDocs {

	
	static class HTerm implements Serializable{
		Term term = null;
		int docnum;
		int segInfoHash;
		
		public HTerm(Term term, int docnum, int segInfoHash){
			this.term = term;
			this.docnum = docnum;
			this.segInfoHash = segInfoHash;
		}
		
		public int hashCode(){
			return term.hashCode() + docnum + segInfoHash;
		}
		
	}
	
	static class Item implements Serializable{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1246356606335212142L;
		int docs[] = new int[0];
		int tfs[] = new int[0];
		
		public void insert(int[] cache_docs, int[] cache_freqs, int len) {
			int new_len = len + docs.length;
			int new_docs[] = new int[new_len];
			int new_tfs[] = new int[new_len];
		
			System.arraycopy(docs, 0, new_docs, 0, docs.length);
			System.arraycopy(cache_docs, 0, new_docs, docs.length, len);
			System.arraycopy(tfs, 0, new_tfs, 0, tfs.length);
			System.arraycopy(cache_freqs, 0, new_tfs, tfs.length, len);
			this.docs = new_docs;
			this.tfs = new_tfs;
		}
	}
	
	Term term = null;
	Item item = null;
	int doc =0; //current position
	int freq ; //current frequency
	int pos = 0;
	int size =0;
	
	/**
	 * you should set a different path for every different collection. 
	 */
	static String path = ApplicationSetup.getProperty("lucene.TermDocsCache.path", "./conf/TermDocs.cache");
	FastCache<HTerm, Item> cache = null;
	static TermDocsCache  instance = null;
	
	
	
	public static TermDocsCache getInstance(){
		if(instance == null){
			instance = new TermDocsCache(1024*1000);
		}
		return instance; 
	}
	
	private TermDocsCache(Term term, Item item, int size){
		this.term = term;
		this.item = item;
		this.size = size;
	}
	
	private TermDocsCache(int cacheSize)
	{
		try {
			File file = new File(path);
			if(file.exists()){
				cache = (FastCache<HTerm, Item>) AbstractExternalizable.readObject(new File(path));
			}
			else{
				cache = new FastCache<HTerm, Item>(cacheSize);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	
	/* (non-Javadoc)
	 * @see org.apache.lucene.index.TermDocs#close()
	 */
	public void close() throws IOException {
		//do noting
	}

	/* (non-Javadoc)
	 * @see org.apache.lucene.index.TermDocs#doc()
	 */
	public int doc() {
		// TODO Auto-generated method stub
		return this.item.docs[pos];
	}

	/* (non-Javadoc)
	 * @see org.apache.lucene.index.TermDocs#freq()
	 */
	public int freq() {
		// TODO Auto-generated method stub
		return this.item.tfs[pos];
	}

	/* (non-Javadoc)
	 * @see org.apache.lucene.index.TermDocs#next()
	 */
	public boolean next() throws IOException {
		if(pos < size){
			pos++;
			freq =item.tfs[pos];
			doc =  item.docs[pos];
			return true;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.apache.lucene.index.TermDocs#read(int[], int[])
	 */
	public int read(int[] docs, int[] freqs) throws IOException {
		int rlen = Math.min(docs.length, size - pos);
		System.arraycopy(item.docs, pos, docs, 0, rlen);
		System.arraycopy(item.tfs, pos, freqs, 0, rlen);
		pos += rlen;
		return rlen;
	}

	/* (non-Javadoc)
	 * @see org.apache.lucene.index.TermDocs#seek(org.apache.lucene.index.Term)
	 */
	public void seek(Term term) throws IOException {
		throw new IllegalAccessError();
	}

	/* (non-Javadoc)
	 * @see org.apache.lucene.index.TermDocs#seek(org.apache.lucene.index.TermEnum)
	 */
	public void seek(TermEnum termEnum) throws IOException {
		throw new IllegalAccessError();
	}

	/* (non-Javadoc)
	 * @see org.apache.lucene.index.TermDocs#skipTo(int)
	 */
	public boolean skipTo(int target) throws IOException {
		if(pos +target < size){
			pos += target;
			return true;
		}
		pos = size -1;
		return false;
	}

	
	public TermDocs termDocs(Term term){
		throw new IllegalAccessError();
	}
	
	int count =1;
	public TermDocs termDocs(Term term, IndexReader reader) {
		int docnum = reader.maxDoc();
//		int segInfo = ((SegmentReader)reader).
		HTerm hterm = new HTerm(term, docnum, 0);
		
		Item item = cache.get(hterm);
		if (item != null) {
			return new TermDocsCache(term, item, item.docs.length);
		}
		
		try {
			TermDocs termDocs = reader.termDocs(term);
			
			item = new Item();
			if (termDocs == null) {
				return null;
			}
			
			int csize = 1024 * 10;
			int cache_docs[] = new int[csize]; int cache_freqs[] = new int[csize];
			int len = 0;
			while((len = termDocs.read(cache_docs, cache_freqs)) > 0){
				item.insert(cache_docs, cache_freqs, len);
			}
			cache.put(hterm, item);
			if(count ++ % 40 ==0){
				save();
			}
			
			return new TermDocsCache(term, item, item.docs.length);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public void save(){
		try {
			AbstractExternalizable.serializeTo(cache, new File(path));
			System.out.println("Saving TermDocsCache");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public static void main(String[] args) {
		Term t1 = new Term("t1", "v1");
		Term t2 = new Term("t2", "v2");
		
		Item item1 = new Item();
		Item item2 = new Item();
		item1.docs = new int[]{1,2,3};
		item1.tfs = new int[]{1,2,3};
		
		TermDocsCache tc = getInstance();
//		tc.cache.put(t1, item1);
//		tc.cache.put(t2, item2);
		
		tc.save();
		
		boolean tag = tc.cache.containsKey(t1);
		System.out.println(tag);
	}

}
