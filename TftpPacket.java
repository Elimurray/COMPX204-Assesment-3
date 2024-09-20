// Eli Murray
// 1626960

import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.*;

/**
 * 
 * * This class encapsulates the structure of a TFTP packet, which includes
 * the packet type (such as Read Request, Data, Acknowledgment, or Error),
 * the block number
 */
public class TftpPacket {
   /**
    * the type of packet (RRQ, DATA, ACK, ERROR)
    */
   public byte type;

   /**
    * the block number of the packet
    */
   public byte blockNumber;

   /**
    * the data section of the packet
    */
   public byte[] data;

   /**
    * Constructor for a TFTP packet this takes a DatagramPacket and extracts the
    * type, block number and data
    * 
    * @param p the DatagramPacket to extract the data from
    * @see DatagramPacket
    * @throws InvalidPacketException if the packet is not a valid TFTP packet
    */
   public TftpPacket(DatagramPacket p) {

      this.type = p.getData()[0];

      int offset = 1;

      if (this.type == 2 || this.type == 3) {
         this.blockNumber = p.getData()[1];
         offset++;
      }

      if (this.type != 3) {
         this.data = Arrays.copyOfRange(p.getData(), offset, p.getLength());
      }

   }
}
