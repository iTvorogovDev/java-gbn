# java-gbn
A Java program implementing Go-Back-N protocol to transfer a text file across an unreliable network I wrote as a project for Computer Networks class.

# DISCLAIMER
The code provided in this repository is meant **ONLY** as a portfolio entry for the potential employer's consideration. All binary files provided here are the intellectual property of University of Waterloo. Reusing any code provided here in your own assignments is an academic offense.

## TECHNICAL SPECIFICATIONS
1. Built and tested in Bash environment, and is only meant to be used within it
1. Java code compiled with openjdk 1.8.0_242
1. Built with GNU Make 4.1

## INSTRUCTIONS
1. From within the root folder, run make command
1. Start up the network emulator
1. Start up the receiver using receiver.sh
1. Start up the sender using sender.sh

For convenience, a sample file for transferring is included as 'big_example' in the submission

## SHELL SCRIPTS EXECUTION FORMAT
./receiver.sh \<host name/address for the network emulator\>  
              \<UDP port number used by the link emulator to receive ACKs from the receiver\>  
              \<UDP port number used by the 3 receiver to receive data from the emulator\>  
              \<name of the file into which the received data is written\>

./sender.sh \<host  of the network emulator\>  
            \<UDP port number used by the emulator to receive data from the sender\>  
            \<UDP port number used by the sender to receive ACKs from the emulator\>  
            \<name of the file to be transferred\>
            
./nEmulator-linux386 \<emulator's receiving UDP port number in the forward (sender) direction\>   
\<receiver’s network address\>  
\<receiver’s receiving UDP port number\>  
\<emulator's receiving UDP port number in the backward (receiver) direction\>  
\<sender’s network address\>  
\<sender’s receiving UDP port number\>  
\<maximum delay of the link in units of millisecond\>  
\<packet discard probability\>  
\<verbose-mode\> (Boolean: Set to 1, the network emulator will output its internal processing).
