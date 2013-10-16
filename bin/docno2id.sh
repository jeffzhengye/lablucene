#!/bin/bash


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
fi

bin/anyclass.sh org.apache.tools.Docno2DocInnerID $@
