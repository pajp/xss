#!/usr/bin/perl -w

use IPC::Open2;
use Socket;
use IO::Socket;

$| = 1;
$/ = "\0";

my $host = shift || 'localhost';
my $port = shift || 8086;

my @script = ("E ping ts=\"(.*)\"",
	      "S <pong ts=\"\$1\"/>\0",
	      "S <r q=\"1\" c=\"guest-login\"/>\0",
	      "E s=\"ok\"",
	      "S <r q=\"2\" c=\"join-game\"/>\0",
	      "E s=\"ok\"",
	      "S <quit/>\0");
	      
my $loop = 1;
my $endafterscript = 1;
#my $iaddr = inet_aton($host) || die "inet_aton: $!";
#my $paddr = sockaddr_in($port, $iaddr);
#my $proto = getprotobyname('tcp');



my $fpid;

$SIG{CHLD} = \&sigchld;

sub sigchld {
  print "% child died, I'm leaving too...\n";
  exit;
}


MAIN: do {
  my $srv = IO::Socket::INET->new(Proto => "tcp",
				  PeerAddr => $host,
				  PeerPort => $port)
    || die "connect to $host:$port: $!";
  
  #socket(SOCK, PF_INET, SOCK_STREAM, $proto)  || die "socket: $!";
  #connect(SOCK, $paddr) || die "connect: $!";
  #open(STDOUT, ">&SOCK");
  #open(STDIN, "<&SOCK");
  print STDERR "% connected.\n";
  
  #print SOCK "<get-status/>\0";
  
  #$fpid = fork();
  my $fpid = 1;
  if ($fpid > 0) { # parent
    my $line;
    my $a;
    
    print STDERR "% script start.\n" if @script;
  SCRIPTLINE: for (@script) {
      print STDERR "%% " . $_ . "\n";
      my ($action, $value) = split / /, $_, 2;
      #print "action: $action value: $value\n";
      if ($action eq "S") {
	$value =~ s/\$1/$a/ if $a;
	print STDERR "%  sending " . $value . "\n";
	print $srv $value;
      } elsif ($action eq "E") {
	print STDERR "%  expecting /$value/\n";
	my $found = 0;
	while (!$found) {
	  my $data = <$srv> || die "end of stream";
	  my $row = decrypt($data);
	  print STDERR "%_ " . $row . "\n";
	  if ($row =~ /$value/) {
	    if ($1) {
	      $a = $1;
	    } else {
	      undef $a;
	    }
	    $found = 1;
	  }
	}
      }
    }
    print STDERR "% end of script.\n" if (@script);

    if (!$endafterscript) {
      while (defined ($line = <SOCK>)) {
	my $data = decrypt($line);
	$data =~ s/\/>/\/>\n/gm;
	$data =~ s/></>\n</gm;
	print STDERR $data . "\n";
      }
    }
  } else {
    $/ = "\n";
    open(STDOUT, ">&SOCK");
    while(<>) {
      chomp;
      print $_ . "\0";
    }
  }
  close SOCK;
} while ($loop);
exit;
  
sub decrypt {
  my $data = shift;

  my @darr = split //, $data;
  for (@darr) {
    my $n = ord($_)-13;
    $_ = chr($n < 0 ? 255-$n : $n) unless ord($_) == 0;
  }
  return join '', @darr;
}


