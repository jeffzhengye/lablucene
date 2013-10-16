#!/usr/bin/perl -w

use strict;

# convert the full set of judgments into a prels file that NEU and UMass evaluate tools can use
# usage:  fullrels_to_prels.pl fullrels >prels

# the fullrels file has 7 columns
# query#, docid, judgment, judgeID, timestamp, algID, inclusion_prob
# the output prels file has 5 columns
# query#, docid, judgment, algID, inclusion_prob

# the prels file uses judgments from the judge with the lowest ID

# map 0->0, 1->1, 2->1, and 3->0
# i.e. nonrel,reasonable to nonrel and rel,highlyrel to rel
my @judgmentmap = (0, 1, 1, 0);

if (@ARGV < 1)
{
	print "usage:  prels_to_qrels.pl fullrels_file >qrels_file\n";
	exit();
}

my %qrels;
my %alg;
my %prob;

open Q, $ARGV[0] or die "could not open $ARGV[0]: $!\n";
while (<Q>)
{
	chomp;
	my ($query, $docid, $judgment, $judge, $time, $alg, $incl) = split(/\s+/);

	next if ($query == 11171);
	next if ($judge == 63 || $judge == 66 || $judge == 70);

	$qrels{$query}{$judge}{$docid} = $judgmentmap[$judgment];
	$prob{$query}{$judge}{$docid} = $incl unless (defined $prob{$query}{$judge}{$docid});


############################################################
#  ben version
#	if (defined $alg{$query}{$judge}{$docid} && $alg != $alg{$query}{$judge}{$docid})
#	{
#		$alg{$query}{$judge}{$docid} = 2;
#	}
#	else
#	{
#		$alg{$query}{$judge}{$docid} = $alg;
#	}
#
#	# a document may have two inclusion probabilities:
#	# 1 if served by UMass, or not-1 if served by NEU.
#	# if it's ever 1, we want it to be 1 in the prels
#	if ($incl >= $prob{$query}{$judge}{$docid})
#	{
#		$prob{$query}{$judge}{$docid} = $incl;
#	}		
############################################################
#  virgil version	
	# a document may have two inclusion probabilities:
	# 1 if served by UMass, or not-1 if served by NEU.
	# if it's both, keep the NEU one
	if ($incl != $prob{$query}{$judge}{$docid}){
		$alg{$query}{$judge}{$docid} = 2;
		if ($incl < $prob{$query}{$judge}{$docid}){ $prob{$query}{$judge}{$docid} = $incl;}		
	} 
	
	else{
		$alg{$query}{$judge}{$docid} = $alg;
		$prob{$query}{$judge}{$docid} = $incl;
	}		
############################################################
	
	
	
	
}
close Q;

foreach my $q (sort {$a <=> $b} keys %qrels)
{
	# if there's more than one judge, pick the one with lowest ID#
	my @judges = sort {$a <=> $b} keys %{$qrels{$q}};
	foreach my $docid (keys %{$qrels{$q}{$judges[0]}})
	{
		print "$q $docid $qrels{$q}{$judges[0]}{$docid} $alg{$q}{$judges[0]}{$docid} $prob{$q}{$judges[0]}{$docid}\n";
	}
}
