import java.io.*;
import java.util.Timer;
import java.util.TimerTask;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*

REFERENCES AND CODE USED

1. https://www.tutorialspoint.com/java/java_multithreading.htm - threaded programming in Java
2. https://www.geeksforgeeks.org/java-util-timer-class-java/ - scheduled threads in Java
3. https://docs.oracle.com/javase/7/docs/api/java/io/FileWriter.html - FileWriter documentation
4. https://howtodoinjava.com/java/multi-threading/how-to-use-locks-in-java-java-util-concurrent-locks-lock-tutorial-and-example/ - guide on using mutex locks in Java
5. A1 - reused some of my own Java code from the previous assignment

*/


public class Sender {

    private static Lock lock; //Provides mutual exclusion between threads

    //Variables to store the program state
    private static final int n = 10; //Maximum window size
    private static volatile int base; //Index of the first packet in the window
    private static volatile int ceil; //Index of the last packet in the window
    private static volatile Timer timer; //Used to keep track of timeouts
    private static ArrayList<Packet> packets = new ArrayList<Packet>(); //Array of packets over which the window will move
    private static volatile boolean isTimerRunning;

    //Variables to store the arguments
    private static int destPort; //Port on which the emulator is listening
    private static int listenPort; //Port on which the receiver thread is listening for packets from emulator
    private static String filePath; //Path to the file that we are going to transfer
    private static InetAddress destAddress; //Object to store the destination address parsed from the argument

    public static void main(String[] args) {

        SenderThread senderThread;
        ListenerThread listenerThread;

        //Parse the arguments
        try {

            destAddress = InetAddress.getByName(args[0]);
            destPort = Integer.parseInt(args[1]);
            listenPort = Integer.parseInt(args[2]);
            filePath = args[3];

        } catch (Exception e) {

            e.printStackTrace();
            System.err.println("Could not parse one or more arguments. Please verify your input and try again.");

        }

        //Read the file, and break it up into packets.
        File file = new File(filePath);
        FileReader fileReader = null;

        try {

            fileReader = new FileReader(file);

        } catch (FileNotFoundException e){

            e.printStackTrace();
            System.err.println("File not found. Please verify the file path and try again");

        }

        StringBuilder stringBuilder = new StringBuilder();
        int curChar;
        int curLength = 0;
        int counter = 0; //Used to keep track of the sequence number needed for each new packet

        try {

            //Read 500 characters from the file at a time
            while ((curChar = fileReader.read()) != -1) {

                ++curLength;
                stringBuilder.append((char) curChar);

                //Once 500 characters are read, write the resulting data into a packet
                if (curLength == 500) {

                    Packet newPacket;

                    try {

                        newPacket = Packet.createPacket(counter, stringBuilder.toString());
                        packets.add(newPacket);

                    } catch (Exception e) {

                        e.printStackTrace();
                        System.err.println("Cannot create a Packet object");
                        System.exit(1);

                    }

                    ++counter;
                    stringBuilder = new StringBuilder();
                    curLength = 0;

                }

            }

        } catch (IOException e) {

            e.printStackTrace();
            System.err.println("Error occurred while parsing the input file");

        }

        //Create a packet from the last part of the file that is less than 500 characters in length
        if (stringBuilder.length() > 0) {

            Packet newPacket;

            try {

                newPacket = Packet.createPacket(counter, stringBuilder.toString());
                packets.add(newPacket);

            } catch (Exception e) {

                e.printStackTrace();
                System.err.println("Cannot create a Packet object");
                System.exit(1);

            }
            ++counter;

        }

        //Setup the initial state
        base = 0;
        if (packets.size() < n) ceil = packets.size() - 1;
        else ceil = base + n - 1;
        isTimerRunning = false;
        lock = new ReentrantLock();
        senderThread = new SenderThread();
        listenerThread = new ListenerThread();

        //Run the threads
        senderThread.start();
        listenerThread.start();

    }

    private static class SenderThread extends Thread {

        private Thread thread;
        private static DatagramSocket senderSocket; //Socket we'll use for sending
        private static FileWriter seqnum; //Every sent packet will be logged to this file
        private volatile int next; //Index of the next packet we would like to send
        private static volatile int newest; //Index of the latest packet sent

        private SenderThread() {

            try {

                seqnum = new FileWriter("seqnum.log");

            } catch (IOException e) {

                e.printStackTrace();
                System.err.println("Could not initialize seqnum.log for writing");

            }

            try {

                senderSocket = new DatagramSocket(destPort);

            } catch (SocketException e) {

                e.printStackTrace();
                System.err.println("Cannot initialize the sender socket");

            }

            next = base;

        }

        public void run() {

            while (base <= ceil) {

                lock.lock();

                //Send the next packet if the window is not full
                if (next <= ceil) {

                    newest = next;
                    send(senderSocket, packets.get(next));

                    //Start the timer if it is not already running
                    if (!isTimerRunning) {


                        timer = new Timer(true);
                        timer.schedule(new Helper(), 100, 100);
                        isTimerRunning = true;

                    }

                    ++next;

                }

                lock.unlock();

            }

            //All packets sent and acknowledged - no need to run the timer anymore
            lock.lock();
            timer.cancel();
            timer.purge();
            lock.unlock();

            //Send an EOT packet
            try {

                Packet eotPacket = Packet.createEOT(packets.get(packets.size() - 1).getSeqNum() + 1);
                send(senderSocket, eotPacket);

            } catch (Exception e) {

                e.printStackTrace();
                System.err.println("Could not create and send an EOT packet");

            }

            try {

                seqnum.close();

            } catch (IOException e) {

                e.printStackTrace();
                System.err.println();

            }

        }

        public void start() {

            if (thread == null) {

                thread = new Thread(this, "SenderThread");
                thread.start();

            }

        }

        //A scheduled helper thread that resends all the packets in the window if a timeout occurs
        private static class Helper extends TimerTask {

            public void run() {

                for (int i = base; i <= newest; ++i) {

                    send(senderSocket, packets.get(i));

                }

            }

        }

        //Helper function that sends a provided packet through the specified socket
        private static void send(DatagramSocket socket, Packet packet) {

            //Serialize the Packet object into byte data and set up the datagram packet to be sent
            byte[] dataOut = packet.getUDPdata();
            DatagramPacket packetOut = new DatagramPacket(dataOut, dataOut.length, destAddress, destPort);

            //Send the packet
            try {

                socket.send(packetOut);

            } catch(IOException e) {

                e.printStackTrace();
                System.err.println("Error occurred while sending a packet");

            }

            //Log the sent packet
            try {

                seqnum.write(packet.getSeqNum() + "\n");

            } catch (IOException e){

                e.printStackTrace();
                System.err.println("Error occurred while logging to seqnum.log");

            }

        }

    }

    private static class ListenerThread extends Thread {

        private Thread thread;
        private DatagramSocket listenerSocket; //Socket used for listening on the incoming packets
        private FileWriter ack; //File where all incoming acknowledgements will be logged

        private ListenerThread() {

            try {

                listenerSocket = new DatagramSocket(listenPort);

            } catch (SocketException e) {

                e.printStackTrace();
                System.err.println("Cannot initialize the socket for listening");

            }

            try {

                ack = new FileWriter("ack.log");

            } catch (IOException e) {

                e.printStackTrace();
                System.err.println("Cannot initialize ack.log for writing");

            }

        }

        public void start() {

            if (thread == null) {

                thread = new Thread(this, "ListenerThread");
                thread.start();

            }

        }

        public void run() {

            //Listen for incoming packets until we receive an EOT
            while (true) {

                //Get the incoming datagram packet bytes, and convert it into a Packet object.
                byte[] dataIn = new byte[1024];
                DatagramPacket packetIn = new DatagramPacket(dataIn, dataIn.length);

                try {

                    listenerSocket.receive(packetIn);

                } catch (IOException e) {

                    e.printStackTrace();
                    System.err.println("Error occurred while receiving a packet");

                }

                //Deserialize the packet received
                byte[] rawData = packetIn.getData();
                Packet packet;

                try {

                    packet = Packet.parseUDPdata(rawData);
                    ack.write(packet.getSeqNum() + "\n"); //Log the acknowledgement received

                    if (packet.getType() == 2) { //Received an EOT, terminate

                        ack.close();
                        break;

                    } else {

                        //Find where in the window the packet we just received is
                        //(Since the seqnums wrap around, we can't just rely on simple arithmetic)
                        for (int i = base; i <= ceil; ++i) {

                            //Found a packet within the window that is waiting to be acknowledged
                            if (packets.get(i).getSeqNum() == packet.getSeqNum()) {

                                lock.lock();

                                //Reset the timer
                                timer.cancel();
                                timer.purge();
                                isTimerRunning = false;

                                //Update the state
                                base = i + 1;
                                ceil = base + n - 1;

                                //Don't let ceiling go out of bounds
                                if (ceil >= packets.size()) ceil = packets.size() - 1;

                                //Restart the timer
                                timer = new Timer(true);
                                timer.schedule(new SenderThread.Helper(), 100, 100);
                                isTimerRunning = true;

                                lock.unlock();
                                break;

                            }

                        }

                    }

                } catch (Exception e) {

                    e.printStackTrace();
                    System.err.println("Error occurred while deserializing a packet");

                }

            }

        }

    }

}
