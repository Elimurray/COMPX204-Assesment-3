// Eli Murray
// 1626960

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.*;

public class TftpServer {
   /**
    * Port the server listens on
    */
   private static int port = 69;

   /**
    * List of all workers
    */
   public static List<TftpWorker> workers = new ArrayList<TftpWorker>();

   /**
    * 
    * The entry point for the TFTP server application.
    *
    * This method initializes the server to listen on a specified port (defaulting
    * to 69 if no
    * port is provided). It continuously listens for incoming DatagramPackets,
    * creating a
    * new TftpWorker for each received packet to handle client requests.
    *
    * If the provided port argument is empty, the server will default to port 69.
    * The method also manages a list of active worker threads, removing any that
    * are no longer alive.
    *
    * @param args Command line arguments where the first argument specifies the
    *             port number
    * 
    */
   public static void main(String[] args) {
      // if port in arg use that port or default 69
      if (args.length > 0) {
         if (args[0].equals("")) {
            port = 69;
         } else {
            port = Integer.parseInt(args[0]);
         }
      }

      // create a new DatagramSocket listen on port
      try {
         // create ds on port otherwise set to 69
         DatagramSocket dataGramSocket = new DatagramSocket(port);
         System.out.println("TftpServer is on port " + port);

         // infinite loop creating workers
         for (;;) {
            // makes byte array
            byte[] buffer = new byte[1472];
            DatagramPacket packet = new DatagramPacket(buffer, 1472);

            // goes through workers, if alive leave it if not remove
            for (TftpWorker w : workers) {
               if (!w.isAlive()) {
                  workers.remove(w);
               }
            }

            // receive packet
            dataGramSocket.receive(packet);

            // create worker to handle packet
            // try catch for worker errors
            try {
               TftpWorker worker = new TftpWorker(packet);

               System.out.println("Worker made");

               workers.add(worker);
               worker.start();
            } catch (Exception e) {
               System.err.println("error creating worker: " + e);
            }

         }
      } catch (Exception e) {
         System.err.println("Exception: " + e);
      }

   }

}
