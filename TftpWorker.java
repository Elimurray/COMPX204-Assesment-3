// Eli Murray
// 1626960

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The TftpWorker class is a thread that handles a single TFTP request.
 * A worker is created with a DatagramPacket with request and an id.
 * Worker reads request and gets file name.
 * Reads the file and splits it into blocks of 512 bytes.
 * Sends each block to client and waits for ack with matching block num.
 * If client no response after 5s then resend.
 * If client no response after 30s close connection.
 * End is noticed if block less than 512 or 0
 * 
 * @author Eli Murray
 * @version 1.0
 * @see DatagramPacket
 * @see DatagramSocket
 * @see InetAddress
 * @see Thread
 */
public class TftpWorker extends Thread {

   // request packet type 1
   private static final byte RRQ = 1;

   // data packet type 2
   private static final byte DATA = 2;

   // ack packet type 3
   private static final byte ACK = 3;

   // error packet type 4
   private static final byte ERROR = 4;

   // filename
   public String filename;

   // The DatagramSocket used to send and receive packets
   private DatagramSocket dataSocket;

   // client add
   private InetAddress clientAddress;

   // client port
   private int clientPort;

   // worker port
   private int workerPort;

   /**
    * Returns the port the worker is listening on
    * 
    * @return the port the worker is listening on
    */
   public int getWorkerPort() {
      return workerPort;
   }

   /**
    *
    * Constructs a TftpWorker instance to handle incoming TFTP requests.
    * 
    * This constructor processes the request packet, initializes the worker's
    * parameters, and creates a new DatagramSocket for communication.
    * 
    * 
    *
    * 
    * @param req the request packet to process
    * @throws SocketException
    * @throws SecurityException
    * 
    * @see DatagramPacket
    * @see DatagramSocket
    * @see InetAddress
    * @see SocketException
    * @see SecurityException
    * @see TftpPacket
    */
   public TftpWorker(DatagramPacket req) throws SocketException, SecurityException {
      // create packet from req
      TftpPacket request = new TftpPacket(req);

      // get type
      byte type = request.type;
      filename = new String(request.data);

      // create new ds random port
      dataSocket = new DatagramSocket();

      // get client ip and port and worker port
      clientAddress = req.getAddress();
      clientPort = req.getPort();
      workerPort = dataSocket.getLocalPort();

      // if ack send error because not req packet
      // must be req first
      if (type == ACK) {
         System.out.println("ACK found, sending error to client");
         Respond(MakeDataGramPacket(ERROR, new byte[] { (byte) 8 }, clientAddress, clientPort));
      } else if (type != RRQ) {
         System.out.println("Invalid request type, dieing...");
         return;
      }

   }

   /**
    *
    * Executes the main processing logic for the TFTP worker.
    * 
    * This method attempts to read the requested file and sends the file data
    * in blocks to the client. It handles exceptions related to file reading
    * and responds with appropriate error packets if issues occur.
    * If the file is successfully read, it breaks the data into blocks
    * and initiates the process to send these blocks to the client.
    */
   public void run() {
      // create array of file data
      byte[] fileData;

      // tries to read file
      // if not there tell client then returns
      // any other errors, send error
      try {
         fileData = ReadFile(filename);
      } catch (Exception e) {
         System.out.println("Error reading file");
         Respond(MakeDataGramPacket(ERROR, "Error reading file".getBytes(), clientAddress, clientPort));
         return;
      }

      // create list of byte array called blocks
      List<byte[]> blocks = GetBlocks(fileData);

      sendBlocks(blocks);

   }

   /**
    * Sends a packet to the client.
    *
    * @param p the packet to send to the client
    * @see DatagramPacket
    */
   private void Respond(DatagramPacket p) {
      try {
         dataSocket.send(p);
      } catch (Exception e) {
         System.out.println("Error sending response");
      }
   }

   /**
    * 
    * Sends a list of data blocks to the client and handles acknowledgments.
    *
    * This method iterates over the provided blocks, sending each block
    * to the client and waiting for an ack before sending
    * the next one. If the client does not respond within a specified
    * timeout, the block is resent up to a maximum of six attempts.
    * If the last block is sent, the method informs the client and closes
    * the connection.
    *
    * @param blocks the list of byte arrays to send to the client
    */
   private void sendBlocks(List<byte[]> blocks) {
      // send each block and wait for a response before sending the next

      try {

         // loop through the blocks and send each one to the client
         for (int i = 0; i < blocks.size(); i++) {
            // set the block number and the block data to send
            byte blockNumber = (byte) (i + 1);
            byte[] block = blocks.get(i);

            // create the packet to send
            DatagramPacket packet = MakeDataGramPacket(DATA, blockNumber, block, clientAddress, clientPort);

            // send the packet
            Respond(packet);

            // check for last block
            if (block.length < 512) {
               System.out.println("Last block sent");
               return;
            }

            // prepare for the response
            byte[] ackData = new byte[2];
            DatagramPacket ackPacket = new DatagramPacket(ackData, 2);

            // set time out to 5 seconds and a counter for re sends
            int acksTimeOut = 0;
            dataSocket.setSoTimeout(5000);

            // loop until the client responds or the connection is closed
            for (;;) {

               try {
                  // receive the ack packet or throw when the time out is reached
                  dataSocket.receive(ackPacket);
                  acksTimeOut = 0;
                  break;
               } catch (SocketTimeoutException e) {
                  // catch time out and resend the block. if I have tried 6 times then close the
                  // connection
                  acksTimeOut++;
                  System.out.println("no response, resending " + blockNumber);
                  if (acksTimeOut == 6) {
                     System.out.println("no response, closing conection");
                     return;
                  }
                  Respond(MakeDataGramPacket(DATA, blockNumber, block, clientAddress, clientPort));
               }
            }

            TftpPacket ackHandled = new TftpPacket(ackPacket);

            byte ackType = ackHandled.type;
            byte blockNumberClient = ackHandled.blockNumber;

            if (ackType != ACK) {
               System.out.println("Invalid ack");
               return;
            }
            if (blockNumberClient != blockNumber) {
               i = blockNumberClient;
            }
         }

         byte[] finalPacketData = new byte[3];
         finalPacketData[0] = DATA;
         finalPacketData[1] = (byte) (blocks.size() + 1);
         finalPacketData[2] = 0;

         DatagramPacket finalPacket = new DatagramPacket(finalPacketData, 0,
               finalPacketData.length, clientAddress,
               clientPort);

         Respond(finalPacket);

         System.out.println("all sent");
         dataSocket.close();
      } catch (Exception e) {
         System.out.println("error with blocks");
      }
   }

   /**
    * Reads a file from a specified filename and
    * returns the data as a byte array
    *
    * @param filename the path to the file to read
    * @return a byte array containing the data from the file
    * @throws Exception if there is an error reading the file
    * @see File
    * @see FileInputStream
    */
   private byte[] ReadFile(String filename) {
      // open the file and read all the data into a byte array and return it
      try {

         File file = new File(filename);
         FileInputStream fis = new FileInputStream(file);
         byte[] fileData = fis.readAllBytes();
         fis.close();
         return fileData;
      } catch (Exception e) {

         Respond(MakeDataGramPacket(ERROR, "File note found".getBytes(), clientAddress, clientPort));
         System.out.println(e.getMessage());
         return new byte[0];

      }

   }

   /**
    * Splits a byte[] into blocks of 512 bytes each and returns a list of byte
    * arrays each of size 512 except the last one which may be smaller than or
    * equal to 512
    * 
    * @param data the byte[] to split into blocks
    * @return a list of byte arrays
    */
   private List<byte[]> GetBlocks(byte[] data) {
      // make a list of byte arrays
      List<byte[]> blocks = new ArrayList<byte[]>();

      int dataLength = data.length;

      // calc the amount of full blocks
      int amountOfFullBlocks = dataLength / 512;

      // set start and end points for the blocks
      int startPos = 0;
      int endPos = 512;
      byte[] block;

      // loop through the data array and copy the range start to end into a new array
      // if the end position is not within the last block
      while (endPos / 512 != amountOfFullBlocks + 1) {
         block = Arrays.copyOfRange(data, startPos, endPos);
         blocks.add(block);
         startPos += 512;
         endPos += 512;
      }

      // copy the last block into a new array
      if (startPos == dataLength) {
         return blocks;
      }
      block = Arrays.copyOfRange(data, startPos, dataLength);
      blocks.add(block);

      return blocks;

   }

   /**
    *
    * Creates a DatagramPacket for sending data over a network.
    *
    * This method constructs a packet by prepending the specified type byte
    * to the given data array. The resulting packet can then be sent to
    * the specified address and port.
    *
    * @param type    the type of the packet (e.g., request, response)
    * @param data    the data to be included in the packet
    * @param address the destination InetAddress
    * @param port    the destination port number
    * @return a DatagramPacket containing the type and data
    */
   private DatagramPacket MakeDataGramPacket(byte type, byte[] data, InetAddress address, int port) {
      // shift the data array down by one and slot the type into the first position
      byte[] dataToSend = new byte[data.length + 1];
      dataToSend[0] = type;
      System.arraycopy(data, 0, dataToSend, 1, data.length);

      // create the packet
      return new DatagramPacket(dataToSend, 0, dataToSend.length, address, port);
   }

   /**
    * Creates a DatagramPacket for sending data with a specified block number.
    *
    * This method constructs a packet by prepending the specified block number
    * to the given data array. The resulting packet is then prepared for sending
    * to the specified address and port.
    *
    * @param type    the type of the packet (e.g., request, response)
    * @param block   the block number to include in the packet
    * @param data    the data to be included in the packet
    * @param address the destination InetAddress
    * @param port    the destination port number
    * @return a DatagramPacket containing the block number and data
    */
   private DatagramPacket MakeDataGramPacket(byte type, byte block, byte[] data, InetAddress address, int port) {
      // shift the data array down by one and slot the block number into the first
      // position
      byte[] dataToSend = new byte[data.length + 2];
      dataToSend[0] = type;
      dataToSend[1] = block;
      System.arraycopy(data, 0, dataToSend, 2, data.length);

      // create the packet
      return new DatagramPacket(dataToSend, 0, dataToSend.length, address, port);
   }

}
