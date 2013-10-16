package org.dutir.lucene.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

public class Med11DocParser extends GeneralDocParser {

	static HashMap<String, String> visitreports_visitid = new HashMap<String, String>(1024*10);
//	static HashMap<String, String> droped_visitreports_visitid = new HashMap<String, String>(1024*10);
	
	static {
		String path = "./TopicQrel/UnivOfPittReportMappingToVisit.txt";
		String drop_path = "./TopicQrel/845.dropped.report.filenames.txt";
		BufferedReader br = org.dutir.util.stream.StreamGenerator.getBufferFileReader(path, 10);
		BufferedReader br1 = org.dutir.util.stream.StreamGenerator.getBufferFileReader(drop_path, 10);
		String line = null;
		try {
			while((line = br.readLine()) != null){
				String parts[] = line.split("\\s");
				visitreports_visitid.put(parts[0], parts[2]);
			}
			System.out.println("visitreports_visitid size: " + visitreports_visitid.size());
			br.close();
			
			while((line = br1.readLine()) != null){
				
				String parts[] = line.split("[\\s]+");
				String ret = visitreports_visitid.remove(parts[1].trim());
				System.out.println(parts.length + ":" + parts[0].trim() ); 
				if(ret == null || ret.endsWith("null")) continue;
				System.out.println("Removed: " + parts[1].trim() + ":" + ret);
			}
			br1.close();
			
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public void indexAll() {
		try {
			logger.info("adding doc: " + this.file.getAbsolutePath());
			FieldTokenizer ftokenizer = new FieldTokenizer(new BufferedReader(
					new InputStreamReader(this.bis, this.encoding)));
			String idtag = ftokenizer.tagSet.getIdTag();
			int ncounter =0;
			while (!ftokenizer.isEndOfFile()) {
				Document doc = ftokenizer.nextDocument();
				if(doc == null) continue;
				Field idField = doc.getField(idtag);
				String mapped_value = map(idField.stringValue());
				if(mapped_value ==null || mapped_value.equalsIgnoreCase("null")){
					System.out.println("null visit:" + idField.stringValue());
					ncounter++;
					continue;
				}else{
					idField.setValue(mapped_value);
				}
				
				if (doc != null) {
//					System.out.println(doc);
					this.writer.addDocument(doc);
				} else {
					// System.out.println(doc);
				}
			}
			System.out.println("null visit num:" + ncounter);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private String map(String name) {
		return visitreports_visitid.get(name);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
