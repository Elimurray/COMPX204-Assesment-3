// Eli Murray
// 1626960

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * TftpClient is a simple implementation of a Trivial File Transfer Protocol
 * (TFTP) client.
 * 
 * This client can request a file from a TFTP server and save it to a specified
 * location.
 * It handles communication over UDP, sending requests, and receiving data
 * packets from the server.
 * 
 * Usage: java TftpClient <server> <port> <filePath> <saveLocation>
 */
public class TftpClient {

    /**
     * DatagramSocket used for sending and receiving packets.
     * 
     * @see DatagramSocket
     * @see TftpWorker
     */
    private static DatagramSocket ds;

    /**
     * The port on which the TFTP server is listening, default is 69.
     * 
     * @see TftpServer
     */
    private static int port = 69;

    /**
     * The InetAddress of the TFTP server to connect to.
     * 
     * @see TftpServer
     * @see InetAddress
     */
    private static InetAddress serverAddress;

    /**
     * The port number of the server, which will be set upon the first response.
     */
    private static int serverPort = 0;

    /**
     * The complete path to the file to be requested from the server.
     */
    private static String filename;

    /**
     * The path where the received file will be saved.
     */
    private static String saveLocation = "received_";

    /**
     * Buffer to hold the response data received from the server.
     */
    private static byte[] repsonseBuffer;

    /**
     * Main method to execute the TFTP client.
     * 
     * @param args Command line arguments for server address, port, file path, and
     *             save location.
     */
    public static void main(String[] args) {
        try {
            // check for correct number of arguments
            // if (args.length != 4) {
            // System.err.println("Usage: java TftpClient <server> <port> <filePath>
            // <savelocation>");
            // System.exit(1);
            // }

            // // parse arguments given
            // serverAddress = InetAddress.getByName(args[0]);
            // serverPort = Integer.parseInt(args[1]);
            // filename = args[2];
            // saveLocation = args[3];

            serverAddress = InetAddress.getByName("127.0.0.1");
            serverPort = Integer.parseInt("69");
            filename = "test.java";
            saveLocation = "lol";

            System.out.println("Server: " + serverAddress.getHostAddress());
            System.out.println("Server port: " + serverPort);
            System.out.println("Requesting: " + filename);
            System.out.println("Saving to: " + saveLocation);

            // create socket to file requesting server
            ds = new DatagramSocket();

            // create request message
            byte[] data = filename.getBytes();
            byte type = 1; // Read Request (RRQ)
            byte[] message = new byte[data.length + 1];
            message[0] = type;
            System.arraycopy(data, 0, message, 1, data.length);

            // send request
            DatagramPacket packet = new DatagramPacket(message, 0, message.length, serverAddress, port);
            ds.send(packet);

            // prepare block numbers for later checking
            byte responseBlockNumber = 0;
            byte prevBlockNumber = -1;

            // get response
            for (;;) {
                // set up buffer, packet, and set timeout
                byte[] buf = new byte[1472];
                DatagramPacket p = new DatagramPacket(buf, 1472);
                ds.setSoTimeout(30000);

                // get response from server, if server does not respond in 30 seconds close the
                // connection
                for (;;) {
                    try {
                        ds.receive(p);

                        // check if worker port is set then set
                        if (serverPort == 0) {
                            serverPort = p.getPort();
                        }
                        break;
                    } catch (SocketTimeoutException e) {
                        System.out.println("Server not responding... closing connection");
                        return;
                    }
                }

                // make TftpPacket from response
                TftpPacket handledPacket = new TftpPacket(p);

                // handle response
                responseBlockNumber = handleClientPacket(handledPacket);

                // check for duplicate data packet
                if (responseBlockNumber - 1 == prevBlockNumber) {
                    WriteToFile(repsonseBuffer);
                    prevBlockNumber = responseBlockNumber;
                } else {
                    System.out.println("Duplicate block received, not writing to file");
                }

                // send back ACK
                DatagramPacket ackPacket = new DatagramPacket(new byte[] { 0, responseBlockNumber }, 2, serverAddress,
                        serverPort);

                Acknowledge(ackPacket);
            }

        } catch (Exception e) {
            // print out any exceptions
            System.err.println("Exception: " + e.getMessage());
        }
    }

    /**
     * 
     * Handles the response received from the TFTP server.
     *
     * This method processes the incoming TFTP packet, checks for error packets,
     * and handles the received data. It also determines when all data blocks have
     * been successfully received and writes the final block to the output file.
     *
     * @param p The TftpPacket containing the response from the server.
     * @return The block number of the received data, or -1 if an error occurs.
     */
    private static byte handleClientPacket(TftpPacket p) {
        // convert response to string
        byte reponseType = p.type;

        // check for error packet
        try {
            if (reponseType == 4) {
                System.out.println(new String(p.data));
            }
        } catch (Exception e) {
            System.err.println("Error packet recived: " + e.getMessage());
            return -1;
        }

        // exit if all blocks received
        if (p.data.length < 512 || p.data.length == 0) {
            System.out.println("All blocks received");
            WriteToFile(p.data);
            System.exit(0);
        }

        byte blockNumberToRespondWith = p.blockNumber;
        repsonseBuffer = p.data;

        // return with the block number to acknowledge
        return blockNumberToRespondWith;

    }

    /**
     * 
     * Sends an acknowledgment (ACK) packet back to the TFTP server.
     *
     * This method constructs an ACK packet using the block number from the received
     * data packet and sends it to the server. The first byte of the ACK packet is
     * always set to 3 (indicating an ACK), followed by the block number.
     *
     * @param p The DatagramPacket received from the TFTP server containing the data
     *          to acknowledge.
     */
    private static void Acknowledge(DatagramPacket p) {
        try {
            // make ack packet
            byte[] ackData = new byte[2];
            ackData[0] = 3;
            ackData[1] = p.getData()[1];
            DatagramPacket ackPacket = new DatagramPacket(ackData, 2, p.getAddress(), p.getPort());

            // send ack
            Respond(ackPacket);
        } catch (Exception e) {
            System.err.println("Error sending ACK");
        }
    }

    /**
     *
     * Sends a DatagramPacket response to the designated address and port.
     *
     * This method attempts to send the provided DatagramPacket using the
     * established DatagramSocket. If an error occurs during the sending
     * process, it will print an error message to the console.
     *
     * @param p The DatagramPacket to be sent as a response.
     */
    private static void Respond(DatagramPacket p) {
        // trys to send the packet
        try {
            ds.send(p);
        } catch (Exception e) {
            System.err.println("Error sending response");
        }
    }

    /**
     * Writes the provided byte array data to a specified file.
     *
     * This method attempts to append the given byte array to a file
     * specified by the `saveLocation` variable. If the file does not
     * exist, it will be created. Any exceptions during the write process
     * will be printed to the console.
     *
     * @param data The byte array containing the data to be written to the file.
     */
    private static void WriteToFile(byte[] data) {
        // set up file output stream
        FileOutputStream fos = null;

        try {
            // write data to file
            File file = new File(saveLocation);
            fos = new FileOutputStream(file, true);
            fos.write(data);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // finnaly close the file output stream if it is open
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e2) {
                System.err.println("Exception2: " + e2);
            }
        }
    }
}
// changes