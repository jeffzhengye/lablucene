/**
 * 
 */
package org.dutir.lucene.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.channels.FileLock;

import org.apache.log4j.Logger;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Searcher;
import org.dutir.util.AbstractExternalizable;
import org.dutir.util.FastCache;

/**
 * @author yezheng This class is used to cache the ctf and df for a term. The
 *         speed can be boosted greatly, especially when we search from multiple
 *         indexes.
 */

public class TermsCache {

	Logger logger = Logger.getLogger(this.getClass());
	/**
	 * you should set a different path for every different collection.
	 */
	static String path = null;
	static String lockpath = null;
	static {
		String indexPath = ApplicationSetup.getProperty(
				"Lucene.indexDirectory", ".");
		path = indexPath + "/terms.cache";
		lockpath = indexPath + "/termscache.lock";
	}

	public static class Item implements Serializable {
		public float df = 0;
		public float ctf = 0;

		public Item(float df, float ctf) {
			this.df = df;
			this.ctf = ctf;
		}
	}

	static TermsCache instance = null;

	FastCache<Term, Item> fc = null;

	// new FastCache<String,String>(100,0.5);

	// static String path = "./conf/terms.cache";
	public static TermsCache getInstance() {
		if (instance == null) {
			instance = new TermsCache();
		}
		return instance;
	}

	public Item getItem(String term, String field, Searcher searcher) {
		TermsCache.Item item = get(term, field);
		if (item == null) {
			Term lterm = new Term(field, term);
			try {
				float TF = searcher.termFreq(lterm);
				float Nt = searcher.docFreq(lterm);
				item = new TermsCache.Item(Nt, TF);
				put(lterm, item);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return item;
	}

	public Item getItem(Term lterm, Searcher searcher) {
		TermsCache.Item item = get(lterm);
		if (item == null) {
			try {
				float TF = searcher.termFreq(lterm);
				float Nt = searcher.docFreq(lterm);
				item = new TermsCache.Item(Nt, TF);
				put(lterm, item);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return item;
	}

	private TermsCache() {
		initCache();
	}

	private void initCache() {
		try {
			File file = new File(path);
			// File lockfile = new File(lockpath);
			// if(lockfile.exists()){
			// Thread.sleep(100);
			// initCache();
			// }else{
			// lockfile.createNewFile();
			// if(file.exists()){
			// fc = (FastCache<Term, Item>)
			// AbstractExternalizable.readObject(new File(path));
			// }
			// else{
			// fc = new FastCache<Term, Item>(1024*1000);
			// }
			// lockfile.delete();
			// }

			if (file.exists()) {
				fc = (FastCache<Term, Item>) AbstractExternalizable
						.readObject(new File(path));
				if (fc != null) {
					if(logger.isInfoEnabled()) logger.info("load existing TermsCache with " + fc.size()
							+ " entries from :" + path);
					return;
				}
			}
			fc = new FastCache<Term, Item>(1024 * 1000);
			logger.warn("create a new TermsCache");
		} catch (Exception e) {
			e.printStackTrace();
			fc = new FastCache<Term, Item>(1024 * 1000);
			logger.warn("create a new TermsCache with exception");
		}
	}

	int insertCount = 1;

	public void put(Term term, Item item) {
		insertCount++;
		this.fc.put(term, item);

	}

	final Item get(Term term) {
		return this.fc.get(term);
	}

	public Item get(String term, String field) {
		return this.fc.get(new Term(field, term));
	}

	public boolean contain(Term term) {
		return this.fc.containsKey(term);
	}

	public void save() {
		try {
			if (insertCount > 10) {
				AbstractExternalizable.serializeTo(fc, new File(path));
				insertCount = 0;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		// TermsCache tc = getInstance();
		// Term t1 = new Term(new String("fld"), "term1");
		// Term t2 = new Term("fld", "term2");
		// // tc.put(t1, new TermsCache.Item(10f, 15f));
		// // tc.put(t2, new TermsCache.Item(101f, 15f));
		// // tc.save();
		// boolean tag = tc.contain(t1);
		// System.out.println(tag);
		File file = new File("lock.txt");
		// FileOutputStream fos= new FileOutputStream("file.txt");
		RandomAccessFile rfile = new RandomAccessFile("file.txt", "rw");
		FileLock fl = rfile.getChannel().tryLock();
		if (fl != null) {
			System.out.println("Locked File:" + fl.isValid() + ", "
					+ fl.isShared());
			// System.out.println(rfile.readLine());
			FileInputStream fis = new FileInputStream("file.txt");
			byte[] b = new byte[1024 * 10];
			int len = fis.read(b);
			System.out.println("first:" + len + ": " + new String(b, 0, len));

			fis.close();
			System.out.println("first status after close: Locked File:"
					+ fl.isValid() + ", " + fl.isShared());

			rfile.write("US".getBytes());
			System.out.println("first sleep");
			Thread.sleep(1000 * 5);
			fl.release();
			System.out.println("first Released Lock: " + fl.isValid() + ", "
					+ fl.isShared());
		} else {
			while (true) {
				System.out.println("the file is locked");
				Thread.sleep(100);
				fl = rfile.getChannel().lock();
				FileInputStream fis = new FileInputStream("file.txt");
				byte[] b = new byte[1024 * 10];
				int len = fis.read(b);
				System.out.println("second:" + len + ": "
						+ new String(b, 0, len));
				fis.close();

				if (fl != null) {
					System.out.println("the file is unlocked");
					break;

				}
			}
		}
		rfile.close();
	}

}
