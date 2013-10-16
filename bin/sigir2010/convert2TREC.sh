#!/bin/bash

#this bash is used to convert the rf09 offical qrel from stapMAP formato to standard TREC FORMAT
cat $1 | cut -d ' ' -f 1,2,4 |  awk '{ print $1 " 0 " $2 " " $3 }'
