/*
 * Terrier - Terabyte Retriever
 * Webpage: http://ir.dcs.gla.ac.uk/terrier
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - Department of Computing Science
 * http://www.gla.uk
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is HadoopPlugin.java.
 *
 * The Original Code is Copyright (C) 2004-2009 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk> (original author)
 *   
 */
package org.dutir.lucene.io;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.io.InputStreamReader;
import org.apache.log4j.Logger;

import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.FileStatus;

import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.Files;
import org.dutir.lucene.util.Files.FSCapability;

/** This class provides the main glue between Terrier and Hadoop. It has several main roles:<ol>
  * <li>Configure Terrier such that the Hadoop file systems can be accessed by Terrier.</li>
  * <li>Provide a means to access the Hadoop map-reduce cluster, using <a href="http://hadoop.apache.org/core/docs/current/hod.html">Hadoop on Demand (HOD)</a> if necessary.</li>
  * </ol>
  * <p><h3>Configuring Terrier to access HDFS</h3>
  * Terrier can access a Hadoop Distributed File System (HDFS), allowing collections and indices to be placed there.
  * To do so, ensure that your Hadoop <tt>conf/</tt> is on your CLASSPATH, and that the Hadoop plugin is loaded by Terrier,
  * by setting <tt>terrier.plugins=uk.ac.gla.terrier.utility.io.HadoopPlugin</tt> in your <tt>terrier.properties</tt> file.
  * </p>
  * <p><h3>Configuring Terrier to access an existing Hadoop MapReduce cluster</h3>
  * Terrier can access an existing MapReduce cluster, as long as the <tt>conf/</tt> folder for Hadoop is on your CLASSPATH.
  * If you do not already have an existing Hadoop cluster, Terrier can be configured to use HOD, to build a temporary
  * Hadoop cluster from a PBS (Torque) cluster. To configure HOD itself, the reader is referred to the
  * <a href="http://hadoop.apache.org/core/docs/current/hod.html">HOD documentation</a>. To use HOD from Terrier,
  * set the following properties:
  * <ul>
  * <li><tt>plugin.hadoop.hod</tt> - path to the hod binary, normally $HADOOP_HOME/contrib/hod/bin. If unset, then HOD is presusmed
  * to be unconfigured.</li>
  * <li><tt>plugin.hadoop.hod.nodes</tt> - the number of nodes/CPUs that you want to request from the PBS Torque cluster. Defaults to 6.</li>
  * <li><tt>plugin.hadoop.hod.params</tt> - any additional options you want to set on the HOD command line. See the 
  * <a href="http://hadoop.apache.org/core/docs/current/hod_user_guide.html#Command+Line">HOD User guide</a> for examples.</li>
  * </ul>
  * </p><p><h3>Using Hadoop MapReduce from Terier</h3>
  * You should use the JobFactory provided by this class when creating a MapReduce job from Terrier. The JobFactory
  * creates a HOD session should one be required, and also configures jobs such that the Terrier environment can
  * be recreated on the execution nodes.
  * <pre>
  * HadoopPlugin.JobFactory jf = HadoopPlugin.getJobFactory("HOD-TerrierIndexing");
  * if (jf == null)
  *     throw new Exception("Could not get JobFactory from HadoopPlugin");
  * JobConf conf = jf.newJob();
  * ....
  * jf.close(); //closing the JobFactory will ensure that the HOD session ends
  * </pre>
  * When using your own code in Terrier MapReduce jobs, ensure that you configure the Terrier application before
  * anything else:
  * <pre>
  * public void configure(JobConf jc)
  * {
  *     try{
  *         HadoopUtility.loadTerrierJob(jc);
  *     } catch (Exception e) {
  *         throw new Error("Cannot load ApplicationSetup", e);
  *     }
  * }
  * </pre>
  * </p>
  * @since 2.2
  * @author Craig Macdonald
  * @version $Revision: 1.4 $
  */
public class HadoopPlugin implements ApplicationSetup.TerrierApplicationPlugin
{
	/** instance of this class - it is a singleton */
	protected static HadoopPlugin singletonHadoopPlugin;
	/** main configuration object to use for Hadoop access */
	protected static Configuration singletonConfiguration;
	/** The logger used */
    protected static final Logger logger = Logger.getLogger(HadoopPlugin.class);
	

	/** a Job Factory is responsible for creating Terrier Map Reduce jobs.
	  * This should be used when requesting a Terrier map reduce job, as it
	  * adequately initialises the job, such that Terrier can run correctly.
	  */	
	public static abstract class JobFactory {
		/** Make a new job */
		public abstract JobConf newJob() throws Exception;
		
		protected static void makeTerrierJob(JobConf jc) throws IOException {
			HadoopUtility.makeTerrierJob(jc);
		}
		
		/** Finish with this job factory. If the JobFactory was created using HOD, then
		  * the HOD job will also be ended */
		public abstract void close();
	}
	
	
	static class DirectJobFactory extends JobFactory {
		protected Configuration c;
		DirectJobFactory(Configuration _c) { c = _c; }
		public JobConf newJob() throws Exception {
			JobConf rtr = new JobConf(c);
			makeTerrierJob(rtr);
			return rtr;
		}
		public void close() { }
	}
	
	private static final Random random = new Random();
	static class HODJobFactory extends JobFactory {
		protected String hodConfLocation = null;
		protected String hodBinaryLocation = null;
		protected boolean connected = false;
		HODJobFactory(String _hodJobName, String _hodBinaryLocation, String[] hodParams, int HodNumNodes) throws Exception
		{
			hodBinaryLocation = _hodBinaryLocation;
			doHod(_hodJobName, hodParams, HodNumNodes);
		}
		
		protected void doHod(String jobName, String[] hodParams, int NumNodes) throws Exception
		{
			if (jobName == null || jobName.length() == 0)
				jobName = "terrierHOD";	
			logger.info("Processing HOD for "+jobName+" at "+hodBinaryLocation + " request for " + NumNodes + " nodes");

			File hodDir = null;
			while (hodDir == null)
			{
				hodDir = new File(System.getProperty("java.io.tmpdir", "/tmp") + "/hod" + random.nextInt());
				if (hodDir.exists())
					hodDir = null;
			}
			if (! hodDir.mkdir())
			{
				throw new IOException("Could not create new HOD tmp dir at "+hodDir);
			}

			//build the HOD command
			String[] command = new String[8 + hodParams.length];
			command[0] = hodBinaryLocation;
			command[1] = "allocate";
			command[2] = "-d"; 
			command[3] = hodDir.getAbsolutePath();
			command[4] = "-n";
			command[5] = ""+NumNodes;
			command[6] = "-N";
			command[7] = jobName;
			int offset = 8;
			for(String param : hodParams)
				command[offset++] = param;

			//execute the command
			ProcessBuilder pb = new ProcessBuilder();
			pb.command(command);
			Process p = pb.start();
			
			//log all output from HOD
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				String line = null;
				while ((line = br.readLine()) != null)
					logger.info(line);
				br.close();
			} catch(IOException ioe) {}
			p.waitFor();
			
			//check for successfull HOD
			File hodConf = new File(hodDir, "hadoop-site.xml");
			if (! Files.exists((hodConf.toString())))
				throw new IOException("HOD did not produce a hadoop-site.xml");
			final int exitValue = p.exitValue();
			if (exitValue != 0)
			{
				throw new Exception("HOD allocation did not succeed (exit value was "+exitValue+")");
			}
			hodConfLocation = hodDir.getAbsolutePath();
			connected = true;
		}
		
		protected void disconnectHod() throws Exception
		{
			logger.info("Processing HOD disconnect");
			ProcessBuilder pb = new ProcessBuilder();
			pb.command(new String[]{hodBinaryLocation, "deallocate", "-d", hodConfLocation});
			Process p = pb.start();
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				String line = null;
				while ((line = br.readLine()) != null)
					logger.info(line);
				br.close();
			} catch(IOException ioe) {}
			p.waitFor();
			final int exitValue = p.exitValue();
			if (exitValue != 0)
			{
				logger.warn("HOD deallocate might not have succeeded (exit value was "+exitValue+")");
			}
			connected = false;
		}
		
		public JobConf newJob() throws Exception {
			JobConf rtr = new JobConf(hodConfLocation+"/hadoop-site.xml");
				makeTerrierJob(rtr);
			return rtr;
		}

		protected void finalize() {
			close();
		}
	
		public void close()	
		{
			if (connected)
				try{
					disconnectHod();
				} catch (Exception e) {
					logger.warn("Encoutered exception while closing HOD. A PBS job may need to be deleted.", e);
				}
		}
	}
	
	/** Get a JobFactory with the specified session name. This method attempts three processes, in order:
	  * <ol>
	  * <li>If the current/default Hadoop configuration has a real Hadoop cluster Job Tracker configured, then
	  * that will be used. This requires that the <tt>mapred.job.tracker</tt> property in the haddop-site.xml
	  * be configured.</li>
	  * <li>Next, it will attempt to use HOD to build a Hadoop MapReduce cluster. This requies the Terrier property
	  * relating to HOD be configured to point to the location of the HOD binary - <tt>plugin.hadoop.hod</tt></li>
	  * <li>As a last resort, Terrier will use the local job tracker that Hadoop provides on the localhost.</li> 
	  * </ol>
	  */
	public static JobFactory getJobFactory(String sessionName) { 
		return getJobFactory(sessionName, false);
	}
	
	protected static JobFactory getJobFactory(String sessionName, boolean persistent) {
		if (persistent)//TODO
			throw new Error("Persistent JobFactory not yet supported, sorry");
		Configuration globalConf = getGlobalConfiguration();
		
		try {
			//see if the current hadoop configuration has a real job tracker configured
			if (! globalConf.get("mapred.job.tracker").equals("local"))
			{
				logger.debug("Default configuration has job tracked set");	
				return new DirectJobFactory(globalConf);
			}
		
			// if not, try HOD	
			String hod = ApplicationSetup.getProperty("plugin.hadoop.hod", null);
			String[] hodParams = ApplicationSetup.getProperty("plugin.hadoop.hod.params", "").split(" ");
			if (hod != null)
			{
				int HodNodes = Integer.parseInt(ApplicationSetup.getProperty("plugin.hadoop.hod.nodes", ""+6));
				return new HODJobFactory(sessionName, hod, hodParams, HodNodes);
			}
			//as a last resort, use the local Hadoop job tracker
			logger.warn("No remote job tracker or HOD configuration found, using local job tracker");
			return new DirectJobFactory(globalConf);
		} catch (Exception e) {
			logger.warn("Exception occurred while creating JobFactory", e);
			return null;
		}
	}
	
	public static void setGlobalConfiguration(Configuration _config) {
		singletonConfiguration = _config;
	}
	
	public static Configuration getGlobalConfiguration()
	{
		if (singletonConfiguration == null)
		{
			singletonConfiguration = new Configuration();
		}
		return singletonConfiguration;
	}

	static HadoopPlugin getHadoopPlugin()
	{
		return singletonHadoopPlugin;
	}
	
	protected Configuration config = null;
	protected org.apache.hadoop.fs.FileSystem hadoopFS = null; 
	
	public HadoopPlugin() {
		singletonHadoopPlugin = this;
	}

	static class HadoopFSRandomAccessFile implements RandomDataInput
	//static class HadoopFSRandomAccessFile extends RandomAccessFile implements RandomDataInput
	{
		FSDataInputStream in;
		org.apache.hadoop.fs.FileSystem fs;
		String filename;
	
		public HadoopFSRandomAccessFile(org.apache.hadoop.fs.FileSystem fs, String filename) throws IOException
		{
			this.fs = fs;
			this.filename = filename;
			this.in = fs.open(new Path(filename));
		}

		public int read() throws IOException
		{
			return in.read();
		}

		public int read(byte b[], int off, int len) throws IOException
        {
            return in.read(in.getPos(), b, off, len);
        }

		public int readBytes(byte b[], int off, int len) throws IOException
		{
			return in.read(in.getPos(), b, off, len);
		}

		public void seek(long pos) throws IOException
		{
			in.seek(pos);
		}

		public long length() throws IOException
		{
			return fs.getFileStatus(new Path(filename)).getLen();
		}

		public void close() throws IOException
		{
			in.close();
		}
		
		/* implementation from RandomAccessFile */
		public final double readDouble() throws IOException {
			return Double.longBitsToDouble(readLong());
		}

		public final int readUnsignedShort() throws IOException {
    		int ch1 = this.read();
    		int ch2 = this.read();
    		if ((ch1 | ch2) < 0)
        		throw new EOFException();
    		return (ch1 << 8) + (ch2 << 0);
    	}

		public final short readShort() throws IOException {
    		int ch1 = this.read();
    		int ch2 = this.read();
    		if ((ch1 | ch2) < 0)
        		throw new EOFException();
    		return (short)((ch1 << 8) + (ch2 << 0));
    	}

		public final int readUnsignedByte() throws IOException {
    		int ch = this.read();
    		if (ch < 0)
        		throw new EOFException();
    		return ch;
    	}

		public final byte readByte() throws IOException {
    		int ch = this.read();
    		if (ch < 0)
        		throw new EOFException();
    		return (byte)(ch);
    	}

		public final boolean readBoolean() throws IOException {
    		int ch = this.read();
    		if (ch < 0)
        		throw new EOFException();
    		return (ch != 0);
    	}
		
		public final int readInt() throws IOException {
		int ch1 = this.read();
		int ch2 = this.read();
		int ch3 = this.read();
		int ch4 = this.read();
		if ((ch1 | ch2 | ch3 | ch4) < 0)
	   		throw new EOFException();
		return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
		}

		public final long readLong() throws IOException {
			return ((long)(readInt()) << 32) + (readInt() & 0xFFFFFFFFL);
		}
		
		public final float readFloat() throws IOException {
		return Float.intBitsToFloat(readInt());
		}

		public final void readFully(byte b[]) throws IOException {
		readFully(b, 0, b.length);
		}

		public final void readFully(byte b[], int off, int len) throws IOException {
		int n = 0;
		do {
			int count = this.read(b, off + n, len - n);
			if (count < 0)
				throw new EOFException();
			n += count;
		} while (n < len);
		}

		public int skipBytes(int n) throws IOException {
		long pos;
		long len;
		long newpos;

		if (n <= 0) {
			return 0;
		}
		pos = getFilePointer();
		len = length();
		newpos = pos + n;
		if (newpos > len) {
			newpos = len;
		}
		seek(newpos);

   		/* return the actual number of bytes skipped */
		return (int) (newpos - pos);
		}

		public long getFilePointer() throws IOException
		{
			return in.getPos();
		}
		public final char readChar() throws IOException {
    		int ch1 = this.read();
    		int ch2 = this.read();
    		if ((ch1 | ch2) < 0)
        		throw new EOFException();
    		return (char)((ch1 << 8) + (ch2 << 0));
    	}

		public final String readUTF() throws IOException {
			return DataInputStream.readUTF(this);
		}
		public final String readLine() throws IOException {
			StringBuffer input = new StringBuffer();
			int c = -1;
			boolean eol = false;

			while (!eol) {
			switch (c = read()) {
			case -1:
			case '\n':
			eol = true;
			break;
			case '\r':
			eol = true;
			long cur = getFilePointer();
			if ((read()) != '\n') {
				seek(cur);
			}
			break;
			default:
			input.append((char)c);
			break;
			}
			}

			if ((c == -1) && (input.length() == 0)) {
			return null;
			}
			return input.toString();
		}
	}

	public void initialise() throws Exception
	{
		config = getGlobalConfiguration();

		final org.apache.hadoop.fs.FileSystem DFS = hadoopFS = org.apache.hadoop.fs.FileSystem.get(config);

		FileSystem terrierDFS = new FileSystem() 
		{
			public String name()
			{
				return "hdfs";
			}

			/** capabilities of the filesystem */
			public byte capabilities() 
			{
				return FSCapability.READ | FSCapability.WRITE | FSCapability.RANDOM_READ 
					| FSCapability.STAT | FSCapability.DEL_ON_EXIT | FSCapability.LS_DIR;
			}
			public String[] schemes() { return new String[]{"dfs", "hdfs"}; }
	
			/** returns true if the path exists */
			public boolean exists(String filename) throws IOException
			{
				if (logger.isDebugEnabled()) logger.debug("Checking that "+filename+" exists answer="+DFS.exists(new Path(filename)));
				return DFS.exists(new Path(filename));
			}

			/** open a file of given filename for reading */
			public InputStream openFileStream(String filename) throws IOException
			{
				if (logger.isDebugEnabled()) logger.debug("Opening "+filename);
				return DFS.open(new Path(filename));
			}
			/** open a file of given filename for writing */
			public OutputStream writeFileStream(String filename) throws IOException
			{
				if (logger.isDebugEnabled()) logger.debug("Creating "+filename);
				return DFS.create(new Path(filename));
			}

			public boolean mkdir(String filename) throws IOException
			{
				return DFS.mkdirs(new Path(filename));
			}

			public RandomDataOutput writeFileRandom(String filename) throws IOException
			{
				throw new IOException("HDFS does not support random writing");
			}

			public RandomDataInput openFileRandom(String filename) throws IOException
			{
				return new HadoopFSRandomAccessFile(DFS, filename);
			}

			public boolean delete(String filename) throws IOException
			{
				return DFS.delete(new Path(filename), true);
			}

			public boolean deleteOnExit(String filename) throws IOException
			{
				return DFS.deleteOnExit(new Path(filename));
			}

			public String[] list(String path) throws IOException
			{
				final FileStatus[] contents = DFS.listStatus(new Path(path));
				final String[] names = new String[contents.length];
				for(int i=0; i<contents.length; i++)
				{
					names[i] = contents[i].getPath().getName();
				}
				return names;
			}

			public String getParent(String path) throws IOException
			{
				return new Path(path).getParent().getName();
			}

			public boolean rename(String source, String destination) throws IOException
			{
				return DFS.rename(new Path(source), new Path(destination));
			} 

			public boolean isDirectory(String path) throws IOException
			{
				return DFS.getFileStatus(new Path(path)).isDir();
			}

			public long length(String path) throws IOException
			{
				return DFS.getFileStatus(new Path(path)).getLen();
			}

			public boolean canWrite(String path) throws IOException
			{
				return DFS.getFileStatus(new Path(path)).getPermission().getUserAction().implies(FsAction.WRITE);
			}

			public boolean canRead(String path) throws IOException
			{
				return DFS.getFileStatus(new Path(path)).getPermission().getUserAction().implies(FsAction.READ);
			}
		};
		Files.addFileSystemCapability(terrierDFS);
	}

	public Configuration getConfiguration()
	{
		return config;
	}
}
