#!/bin/bash
#
# Terrier - Terabyte Retriever
# Webpage: http://ir.dcs.gla.ac.uk/terrier 
# Contact: terrier@dcs.gla.ac.uk 
#
# The contents of this file are subject to the Mozilla Public
# License Version 1.1 (the "License"); you may not use this file
# except in compliance with the License. You may obtain a copy of
# the License at http://www.mozilla.org/MPL/
#
# Software distributed under the License is distributed on an "AS
# IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
# implied. See the License for the specific language governing
# rights and limitations under the License.
#
# The Original Code is trec_terrier.sh
#
# The Initial Developer of the Original Code is the University of Glasgow.
# Portions created by The Initial Developer are Copyright (C) 2004-2008
# the initial Developer. All Rights Reserved.
#
# Contributor(s):
#	Vassilis Plachouras <vassilis@dcs.gla.ac.uk> (original author)
#	Craig Macdonald <craigm@dcs.gla.ac.uk>
#
# a script for handling the indexing and retrieval from a standard 
# TREC test collection. It also prints out the contents of the basic
# structures of Terrier.

fullPath () {
	t='TEMP=`cd $TEMP; pwd`'
	for d in $*; do
		eval `echo $t | sed 's/TEMP/'$d'/g'`
	done
}

LUCENE_BIN=`dirname $0`
if [ -e "$LUCENE_BIN/terrier-env.sh" ];
then
	. $LUCENE_BIN/terrier-env.sh
fi

#setup TERRIER_HOME
if [ ! -n "$LUCENE_HOME" ]
then
	#find out where this script is running
	TEMPVAR=`dirname $0`
	#make the path abolute
	fullPath TEMPVAR
	#terrier folder is folder above
	LUCENE_HOME=`dirname $TEMPVAR`
	echo "Setting LUCENE_HOME to $LUCENE_HOME"
fi



#setup CLASSPATH
for jar in $LUCENE_HOME/lib/*.jar; do
	CLASSPATH="${CLASSPATH}:$jar"
done

#setup LUCENE_ETC
if [ ! -n "$LUCENE_ETC" ]
then
      LUCENE_ETC="$LUCENE_HOME/etc"
fi

#setup JAVA_HOME
if [ ! -n "$JAVA_HOME" ]
then
	#where is java?
	TEMPVAR=`which java`
	#j2sdk/bin folder is in the dir that java was in
	TEMPVAR=`dirname $TEMPVAR`
	#then java install prefix is folder above
	JAVA_HOME=$TEMPVAR
	echo "Setting JAVA_HOME to $JAVA_HOME"
fi

#CLASSPATH="${TERRIER_HOME}/conf"

#JAVA_OPTIONS="-ea -Dlucene=clueweb09

#CLASSPATH="${LUCENE_HOME}/conf"
echo "lucene options: $LUCENE_OPTIONS"
#echo "java Option: $JAVA_OPTIONS"
#pDir="-Dlucene.pDir=trec8Disk45-CR-T401-450"
pDir="-Dlucene.pDir=clueweb09"
#pDir="-Dlucene.pDir=trec7Disk45-CR-T351-400"
#pDir="-Dlucene.pDir=trec6Disk45T301-350"
#pDir="-Dlucene.pDir=trec5Disk24T251-300"

java -Xmx2600M $JAVA_OPTIONS $LUCENE_OPTIONS \
	 -Dlucene.etc=$LUCENE_ETC \
	 -Dlucene.home=$LUCENE_HOME \
         $pDir \
-cp $CLASSPATH org.apache.other.GetSourceText $@

# -cp $CLASSPATH org.apache.other.GetSourceText $@	
# -cp $CLASSPATH org.dutir.lucene.TrecLucene $@
