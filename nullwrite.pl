#!/usr/bin/perl -w

use IPC::Open2;
$| = 1;
$/ = "\0";

my $pid = open2(\*RDR, \*WTR, "~/nc localhost 8085");

print WTR "<get-status/>\0";

my $fpid = fork();
if ($fpid > 0) { # parent
    while (<RDR>) {
	print $_ . "\n";
    }
} else {
    $/ = "\n";
    while(<>) {
	print WTR $_ . "\0";
    }
}




