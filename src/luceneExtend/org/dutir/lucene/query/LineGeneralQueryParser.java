package org.dutir.lucene.query;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Vector;

import org.dutir.lucene.util.Files;

public class LineGeneralQueryParser extends Clueweb09QueryParser {
	/**
	 * Extracts and stores all the queries from a query file.
	 * 
	 * @param queryfilename
	 *            String the name of a file containing topics.
	 * @param vecStringQueries
	 *            Vector a vector containing the queries as strings.
	 * @param vecStringIds
	 *            Vector a vector containing the query identifiers as strings.
	 * @return boolean true if some queries were successfully extracted.
	 */
	public boolean extractQuery(String queryfilename,
			Vector<String> vecStringQueries, Vector<String> vecStringIds) {
		boolean gotSome = false;
		try {
			BufferedReader br;
			if (!Files.exists(queryfilename) || !Files.canRead(queryfilename)) {
				logger.error("The topics file " + queryfilename
						+ " does not exist, or it cannot be read.");
				return false;
			} else {
				br = Files.openFileReader(queryfilename, desiredEncoding);
				String line = null;
				while((line = br.readLine()) != null){
					String parts[] = line.split(":");
					
					logger.debug(parts[0] + " : " + parts[1]);
					vecStringQueries.add(parts[1]);
//					assert parts[0].length() > 5;
//					if( parts[0].length() > 4 && parts[0].charAt(4) == '-'){
//						vecStringIds.add(parts[0].substring(5));
//					}else
					{
						vecStringIds.add(parts[0]);
					}
					
				}
				// after processing each query file, close the BufferedReader
				br.close();
				gotSome = true;
			}
		} catch (IOException ioe) {
			logger.error(
					"Input/Output exception while extracting queries from the topic file named "
							+ queryfilename, ioe);
		}
		return gotSome;
	}
}
