package pmundur;

import java.util.BitSet;

public class RTPpacket{

    //size of the RTP header:
    static int HEADER_SIZE = 12;

    //Fields that compose the RTP header
    public int Version;
    public int Padding;
    public int Extension;
    public int CC;
    public int Marker;
    public int PayloadType;
    public int SequenceNumber;
    public int TimeStamp;
    public int Ssrc;

    //Bitstream of the RTP header
    public byte[] header;

    //size of the RTP payload
    public int payload_size;
    //Bitstream of the RTP payload
    public byte[] payload;



    //--------------------------
    //Constructor of an RTPpacket object from header fields and payload bitstream
    //--------------------------
    public RTPpacket(int PType, int Framenb, int Time, byte[] data, int data_length){
        //fill by default header fields:
        Version = 2;
        Padding = 0;
        Extension = 0;
        CC = 0;
        Marker = 0;
        Ssrc = 11;

        //fill changing header fields:
        SequenceNumber = Framenb;
        TimeStamp = Time;
        PayloadType = PType;

        //build the header bistream:
        //--------------------------
        header = new byte[HEADER_SIZE];

        //fill the header array of byte with RTP header fields

        //header[0] = ...
        // .....
        header[0] = (byte)(Version << 6);
        header[0] = (byte)(header[0] | Padding << 5);
        header[0] = (byte)(header[0] | Extension << 4);
        header[0] = (byte)(header[0] | CC);
        //System.out.println(Version);
        //System.out.println(Padding);
        //System.out.println(Extension);
        //System.out.println(CC);
        //System.out.print("Byte 0: ");
        //printByte(header[0]);
        header[1] = (byte)(header[1] | Marker << 7);
        header[1] = (byte)(header[1] | PayloadType);
        //System.out.println(Marker);
        //System.out.println(PayloadType);
        //System.out.println("Byte 1: ");
        //printByte(header[1]);
        //System.out.println(SequenceNumber);
        header[2] = (byte)(SequenceNumber >> 8);
        //System.out.println("Byte 2: ");
        printByte(header[2]);
        header[3] = (byte)(SequenceNumber & 0xFF);
        //System.out.println("Byte 3: ");
        //printByte(header[3]);
        //System.out.println(TimeStamp);
        //System.out.println(TimeStamp >> 24);
        header[4] = (byte)(TimeStamp >> 24);
        //System.out.println("Byte 4: ");
        //printByte(header[4]);
        //System.out.println(TimeStamp >> 16);
        header[5] = (byte)(TimeStamp >> 16);
        //System.out.println("Byte 5: ");
        //printByte(header[5]);
        //System.out.println(TimeStamp >> 8);
        header[6] = (byte)(TimeStamp >> 8);
        //System.out.println("Byte 6: ");
        //printByte(header[6]);
        //System.out.println(TimeStamp & 0xFF);
        header[7] = (byte)(TimeStamp & 0xFF);
        //System.out.println("Byte 7: ");
        //printByte(header[7]);
        //System.out.println(Ssrc);
        //System.out.println(Ssrc >> 24);
        header[8] = (byte)(Ssrc >> 24);
        //System.out.println("Byte 8: ");
        //printByte(header[8]);
        //System.out.println(Ssrc >> 16);
        header[9] = (byte)(Ssrc >> 16);
        //System.out.println("Byte 9: ");
        //printByte(header[9]);
        //System.out.println(Ssrc >> 8);
        header[10] = (byte)(Ssrc >> 8);
        //System.out.println("Byte 10: ");
        //printByte(header[10]);
        //System.out.println(Ssrc & 0xFF);
        header[11] = (byte)(Ssrc & 0xFF);
        //Systemout.println("Byte 11: ");
        //printByte(header[11]);

        //fill the payload bitstream:
        //--------------------------
        payload_size = data_length;
        //System.out.println("Packet size is:"+ data_length);
        payload = new byte[data_length];

        //fill payload array of byte from data (given in parameter of the constructor)
        for (int i = 0; i < data_length; i++){
            payload[i] = data[i];
        }

    }

    //--------------------------
    //Constructor of an RTPpacket object from the packet bistream
    //--------------------------
    public RTPpacket(byte[] packet, int packet_size)
    {
        //fill default fields:
        Version = 2;
        Padding = 0;
        Extension = 0;
        CC = 0;
        Marker = 0;
        Ssrc = 0;

        //check if total packet size is lower than the header size
        if (packet_size >= HEADER_SIZE)
        {
            //get the header bitsream:
            header = new byte[HEADER_SIZE];
            for (int i=0; i < HEADER_SIZE; i++)
                header[i] = packet[i];

            //get the payload bitstream:
            payload_size = packet_size - HEADER_SIZE;
            payload = new byte[payload_size];
            for (int i=HEADER_SIZE; i < packet_size; i++)
                payload[i-HEADER_SIZE] = packet[i];

            //interpret the changing fields of the header:
            PayloadType = header[1] & 127;
            SequenceNumber = unsigned_int(header[3]) + 256*unsigned_int(header[2]);
            TimeStamp = unsigned_int(header[7]) + 256*unsigned_int(header[6]) + 65536*unsigned_int(header[5]) + 16777216*unsigned_int(header[4]);
        }
    }

    //--------------------------
    //getpayload: return the payload bistream of the RTPpacket and its size
    //--------------------------
    public int getpayload(byte[] data) {

        for (int i=0; i < payload_size; i++)
            data[i] = payload[i];

        return(payload_size);
    }

    //--------------------------
    //getpayload_length: return the length of the payload
    //--------------------------
    public int getpayload_length() {
        return(payload_size);
    }

    //--------------------------
    //getlength: return the total length of the RTP packet
    //--------------------------
    public int getlength() {
        return(payload_size + HEADER_SIZE);
    }

    //--------------------------
    //getpacket: returns the packet bitstream and its length
    //--------------------------
    public int getpacket(byte[] packet)
    {
        //construct the packet = header + payload
        for (int i=0; i < HEADER_SIZE; i++)
            packet[i] = header[i];
        for (int i=0; i < payload_size; i++)
            packet[i+HEADER_SIZE] = payload[i];

        //return total size of the packet
        return(payload_size + HEADER_SIZE);
    }

    //--------------------------
    //gettimestamp
    //--------------------------

    public int gettimestamp() {
        return(TimeStamp);
    }

    //--------------------------
    //getsequencenumber
    //--------------------------
    public int getsequencenumber() {
        return(SequenceNumber);
    }

    //--------------------------
    //getpayloadtype
    //--------------------------
    public int getpayloadtype() {
        return(PayloadType);
    }


    //--------------------------
    //print headers without the SSRC
    //--------------------------
    public void printheader() {
        final String TAG = "printheader";

        for (int i=0; i < (HEADER_SIZE-4); i++) {

            for (int j = 7; j>=0 ; j--) {
                if (((1 << j) & header[i]) != 0) {
                    System.out.println("1");
                } else {
                    System.out.println("0");
                }
            }
        }

    }

    //return the unsigned value of 8-bit integer nb
    private static int unsigned_int(int nb) {

        if (nb >= 0) {
            return(nb);
        } else {
            return(256+nb);
        }
    }

    void printByte(byte b)
    {
        byte[] bb={b};
        BitSet bits = BitSet.valueOf(bb);
        for (int i=0;i<8;i++)
        {
            System.out.print(printBoolean(bits.get(i))+" ");

        }
        System.out.println();
    }

    int printBoolean(boolean bool)
    {
        if (bool) {return 1;} else {return 0;}
    }

}