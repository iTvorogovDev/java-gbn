import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/*

REFERENCES AND CODE USED

1. A1 - reused some of my own Java code from the previous assignment
2. https://docs.oracle.com/javase/7/docs/api/java/io/FileWriter.html - FileWriter documentation

*/

public class Receiver {

    //Variables to store the program state
    private static int expectedPacketNum; //Which packet sequence number we are expecting
    private static Packet lastAckedPacket; //Last in-order packet that was acknowledged
    private static DatagramSocket listenerSocket; //Socket used for listening on incoming packets
    private static DatagramSocket senderSocket; //Socket used for sending acknowledgements
    private static InetAddress destAddress; //Object to store the destination address parsed from the provided argument

    //Variables to store the arguments
    private static int destPort; //Port on which the emulator is listening for packets in backward direction
    private static int listenPort; //Port on which the receiver is listening for incoming packets
    private static FileWriter outFile; //Used to write the received in-order data to output file
    private static FileWriter arrival; //Used to log sequence number of each received packet to arrival.log

    public static void main(String[] args) {

        //Parse the arguments
        try {

            destAddress = InetAddress.getByName(args[0]);
            destPort = Integer.parseInt(args[1]);
            listenPort = Integer.parseInt(args[2]);
            outFile = new FileWriter(args[3], false);

        } catch (Exception e) {

            e.printStackTrace();
            System.err.println("Could not parse one or more arguments. Please check your arguments and try again.");

        }

        //Set up the initial state
        expectedPacketNum = 0;
        lastAckedPacket = null;

        //Initialize the log file for writing
        try {

            arrival = new FileWriter("arrival.log", false);

        } catch (IOException e) {

            e.printStackTrace();
            System.err.println("Could not initialize a log file for writing");

        }

        //Initialize the sockets
        try {

            listenerSocket = new DatagramSocket(listenPort);
            senderSocket = new DatagramSocket();

        } catch (SocketException e) {

            e.printStackTrace();
            System.err.println("A socket error has occurred");

        }

        //Begin listening on incoming packets
        while (true) {

            try {

                //Get the incoming datagram packet bytes, and convert it into a Packet object.
                byte[] dataIn = new byte[1024];
                DatagramPacket packetIn = new DatagramPacket(dataIn, dataIn.length);
                listenerSocket.receive(packetIn);
                byte[] rawData = packetIn.getData();
                Packet packet = Packet.parseUDPdata(rawData);

                //Log the arrival
                arrival.write(packet.getSeqNum() + "\n");

                if (packet.getType() == 2) {

                    //EOT received, send an EOT back, close the files and terminate
                    byte[] dataOut = Packet.createEOT(packet.getSeqNum()).getUDPdata();
                    DatagramPacket packetOut = new DatagramPacket(dataOut, dataOut.length, destAddress, destPort);
                    senderSocket.send(packetOut);
                    arrival.close();
                    outFile.close();
                    break;

                } else if (packet.getSeqNum() == expectedPacketNum) {

                    //Received an in-order packet, write it to the output file and update program state accordingly

                    //Write the data received
                    outFile.write(new String(packet.getData()));

                    //Increment the expected file number
                    expectedPacketNum = (expectedPacketNum + 1) % 32;

                    //Send an acknowledgement of the received packet
                    lastAckedPacket = packet;
                    byte[] dataOut = Packet.createACK(packet.getSeqNum()).getUDPdata();
                    DatagramPacket packetOut = new DatagramPacket(dataOut, dataOut.length, destAddress, destPort);
                    senderSocket.send(packetOut);

                } else if (lastAckedPacket != null) {

                    //Else, resend the acknowledgement for the last acked packet.
                    byte[] dataOut = Packet.createACK(lastAckedPacket.getSeqNum()).getUDPdata();
                    DatagramPacket packetOut = new DatagramPacket(dataOut, dataOut.length, destAddress, destPort);
                    senderSocket.send(packetOut);

                }

            } catch (Exception e) {

                e.printStackTrace();
                System.err.println("Could not serialize the incoming data into a Packet object");

            }

        }

    }

}
