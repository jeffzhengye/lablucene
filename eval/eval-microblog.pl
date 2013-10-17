#!/usr/bin/env perl

=head1 NAME

eval-microblog.pl - Rerank a microblog and evaluate using trec_eval

=head1 SYNOPSIS

eval-microblog.pl [options] run ...

  Options:
    -qrels           Specify which qrels file to use
    -track           Track root directory
    -level           Relevance level cutoff (default = 1)
    -opts            Options to trec_eval  ('-q -M1000' by default)

=head1 DESCRIPTION

Re-sorts the run's input file correctly and puts it through trec_eval.

The trec_eval invocation is F<trec_eval -q -M1000>, which means to
output individual query results, and only look at retrieved documents
to rank 1000.  This latter limitation is implicit in many of the
measures, and some measures will change significantly if evaluated to
different ranks.

=head1 FILES

This script assumes that several POSIX utilities, including sort(1),
mktemp(1), and gzip(1) are in your path.

Requires trec_eval version 7.2 or (presumably) later.  We need the
relational output and the recip_rank measure.

The run is expected to be found in F<$root/results/RUN/input.gz>.

Output is to stdout.  A control script (usually eval-all-tasks.pl or
some such) is used to catch the output and put it in the right place.

=cut

use strict;
use Pod::Usage;
use Getopt::Long;

my $root = "/trec/trec20/microblog";
my $qrels_root = "$root/eval";
my $qrels_all = "$qrels_root/qrelsfile";
my $trec_eval_loc = "/usr/local/bin/trec_eval";

my $trec_eval_opts = "-q -M1000";
my $rel_cutoff = 1;

GetOptions("qrels=s" => \$qrels_all,
	   "root=s"  => \$root,
           "opts=s"  => \$trec_eval_opts,
	   "level=i" => \$rel_cutoff,
	   ) or pod2usage(2);
my $run = shift or die pod2usage();

# Change these depending on your setup...

my $trec_eval = "$trec_eval_loc -l$rel_cutoff $trec_eval_opts";

my $runfile = "$root/results/$run/input.gz";
my $runtmp = `mktemp -q /tmp/eval.XXXXXX`;

die "Error: couldn't create temporary run file $runtmp\n" if ($? >> 8) != 0;

open(RUNIN, "gzip -dc $runfile | sed -e 's/^MB00*//' | sort -s -k 1,1n -k 3,3nr |") or die "Can't read $runfile to sort: $!\n";
open(RUNOUT, ">$runtmp") or die "Can't write to $runtmp: $!\n";
my $last = -1;
my $score = 1000;
while (<RUNIN>) {
    my ($topic, undef, $docid, undef, undef, $tag) = split;
    if ($topic != $last) {
	$last = $topic;
	$score = 1000;
    }
    print RUNOUT "$topic Q0 $docid ", 1001-$score, " $score $tag\n";
    $score--;
}
close RUNIN;
close RUNOUT;

open(EVAL, "$trec_eval $qrels_all $runtmp |")
    or die "Can't run trec_eval on $runtmp: $!\n";

while (<EVAL>) {
    print;
}
close(EVAL);

qx(rm -f $runtmp);
die "Error: couldn't remove temporary run file $runtmp\n" if ($? >> 8) != 0;

