#!/usr/bin/env python
import subprocess
import os
import sys
import gzip
from optparse import OptionParser
from optparse import OptionGroup
from sets import Set

def remove(input, qrel = "./TopicQrel/RF08/qrels.RF08.E"):
 print qrel, input
 f = open(qrel, 'r')
 qid = 'pre'
 dict={}
 for line in f:
  dlist = line.split()
  #print line 
  if qid != dlist[0]:
   nset = Set()
   qid = dlist[0]
   dict[qid] = nset
  dict[qid].add(dlist[2])
  #print len(dict[qid])
 f.close()

 f = open(input, 'r')
 if input.endswith('.gz'):
  f = gzip.open(input, 'r')
 fw = open(input+".filtered", 'w')
 for line in f:
  dlist = line.split()
  if dlist[0] in dict:
   #print len(dict[dlist[0]])
   if dlist[2] in dict[dlist[0]]:
    #print "del " + line
    continue
   else:
    fw.write(line)
 f.close()
 fw.close()



def main():
 parser = OptionParser(usage="usage: %prog [options] [filename]", version="%prog 1.0")
 parser.add_option("-t", "--trans",
                  action="store_true", dest="trans", default=False,
                  help="transfer a local file to the Haze server")
 parser.add_option("-g", "--gets",
                  action="store_true", dest="gets", default=False,
                  help="get a file from the Haze Server")
 des = "Remove the relevant docs that are in supplied qrel, only for the Relevanc Feedback Track"
 parser.set_description(des)
 (options, args) = parser.parse_args()
 print "options:", options
 print "args:", args
 len = len(args)
 if len == 1:
  remove(args[0])
 elif len == 2: 
  remove(args[0], qrel=args[1])



#############################################
if __name__ == '__main__':
 main()
 #print getMulti(["b"])
