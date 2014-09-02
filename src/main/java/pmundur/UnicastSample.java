package pmundur;

/**
 * Created with IntelliJ IDEA.
 * User: ilya
 * Date: 15.09.13
 * Time: 1:18
 * To change this template use File | Settings | File Templates.
 */

import pmundur.jlibrtp.DataFrame;
import pmundur.jlibrtp.Participant;
import pmundur.jlibrtp.RTPAppIntf;
import pmundur.jlibrtp.RTPSession;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;

/**
 * <p>This is an example of how to set up a Unicast session.</p>
 * <p>It does not accept any input arguments and is therefore of limited practical value, but it shows
 * the basics.</p>
 *
 * <p> The class has to implement RTPAppIntf.</p>
 * @author Arne Kepp
 */
public class UnicastSample implements RTPAppIntf {
    /** Holds a RTPSession instance */
    RTPSession rtpSession = null;

    /** A minimal constructor */
    public UnicastSample(RTPSession rtpSession) {
        this.rtpSession = rtpSession;
    }



    // RTPAppIntf  All of the following are documented in the JavaDocs
    /** Used to receive data from the RTP Library. We expect no data */
    public void receiveData(DataFrame frame, Participant p) {
        /**
         * This concatenates all received packets for a single timestamp
         * into a single byte[]
         */
        byte[] data = frame.getConcatenatedData();




        /**
         * This returns the CNAME, if any, associated with the SSRC
         * that was provided in the RTP packets received.
         */
        String cname = p.getCNAME();

        System.out.println("Received data from " + cname);
        System.out.println(new String(data));
    }

    /** Used to communicate updates to the user database through RTCP */
    public void userEvent(int type, Participant[] participant) {
        //Do nothing
    }

    /** How many packets make up a complete frame for the payload type? */
    public int frameSize(int payloadType) {
        return 1;
    }
    // RTPAppIntf/



    public static void main(String[] args) throws Exception {


        class Bytes
        {
            byte[] bytes=new byte[1500];

            public Bytes(byte[] b)
            {
                bytes=b;
            }

        }



        // 1. Create sockets for the RTPSession
        DatagramSocket rtpSocket = null;
        DatagramSocket rtcpSocket = null;


        try {
            rtpSocket = new DatagramSocket(16584);
            rtcpSocket = new DatagramSocket(16585);
        } catch (Exception e) {
            System.out.println("RTPSession failed to obtain port");
        }


        // 2. Create the RTP session
        RTPSession rtpSession = new RTPSession(rtpSocket, rtcpSocket);


        // 3. Instantiate the application object
        UnicastSample uex = new UnicastSample(rtpSession);

        // 4. Add participants we want to notify upon registration
        // a. Hopefully nobody is listening on this port.
        Participant part = new Participant("127.0.0.1",16586,16587);
        rtpSession.addParticipant(part);
        //System.out.println(part.getPriv()+" "+part.getEmail()+" "+part.getCNAME()+" "+part.getSSRC());
        //rtpSession.setNaivePktReception(true);


        // 5. Register the callback interface, this launches RTCP threads too
        // The two null parameters are for the RTCP and debug interfaces, not use here
        rtpSession.RTPSessionRegister(uex,null,null);

        // Wait 2500 ms, because of the initial RTCP wait
        try{ Thread.sleep(2000); } catch(Exception e) {}

        System.out.println("Payload type is "+rtpSession.payloadType());
        rtpSession.payloadType(96);
        System.out.println("Payload type is "+rtpSession.payloadType());




        // Note: The wait is optional, but insures SDES packets
        //       receive participants before continuing

        // 6. Send some data
        String str = "Hi there!";
        Bytes[] imagesInByte={};

        final int FRAME_SIZE = 1400;


        BufferedImage outputImage = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        File img = new File("/home/ilya/fon.jpeg");
        //File img = new File("/home/ilya/test00000.jpeg");
        try { outputImage = ImageIO.read(img);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(outputImage, "jpg", baos);
            baos.flush();
        } catch (IOException e) { }

        byte[] imageInByteNotCompressed=null;
        OutputStream outNotCompressed=new ByteArrayOutputStream();
        JpegEncoder jpen=new JpegEncoder(outputImage, 80, outNotCompressed);
        jpen.Compress();

        imageInByteNotCompressed = ((ByteArrayOutputStream)outNotCompressed).toByteArray();

        System.out.println(imageInByteNotCompressed.length);

        int fragments= (int) Math.floor(imageInByteNotCompressed.length / FRAME_SIZE)+1;


            OutputStream[] out=new OutputStream[fragments];
            imagesInByte=new Bytes[fragments];
            for (int i=0;i<fragments;i++)
            {
            out[i]=new ByteArrayOutputStream();

            JpegRTPEncoder jpen2=new JpegRTPEncoder(outputImage, 80, out[i]);
            jpen2.Compress(i,i*FRAME_SIZE, FRAME_SIZE,155);

             System.out.println(((ByteArrayOutputStream)out[i]).toByteArray().length);


                imagesInByte[i]= new Bytes(((ByteArrayOutputStream)out[i]).toByteArray());

            System.out.println("JPEG - > RTP/JPEG ["+i+"] done");
            }

        while(true)
        {

            boolean flagEnded = false;
            int j=0;


            for (int i=0;i<fragments;i++)
        {
            rtpSession.sendData(imagesInByte[i].bytes,j*100,i);
            System.out.println(j*100+" "+i);
        }
            j++;
        }

            /*

            byte[] buf =new byte[FRAME_SIZE];

        int image_length=imageInByte.length;


        while(true)
        {

        boolean flagEnded = false;
        int j=0;
            while (!flagEnded)

            {
                if (j+FRAME_SIZE>=image_length)
                {buf= Arrays.copyOfRange(imageInByte, j, image_length);
                    //System.out.println("End. Buf size is "+buf.length+", imageInByte size is "+imageInByte.length+ ", i ="+i);
                    flagEnded = true;
                }
                else
                {
                    buf=Arrays.copyOfRange(imageInByte,j,j+FRAME_SIZE);
                //System.out.println("Buf size is "+buf.length+", imageInByte size is "+imageInByte.length+ ", i ="+i);
                }

                j=j+FRAME_SIZE;

                //rtpSession.sendData(buf,imagenb*FRAME_PERIOD,imagenb);
                //rtpSession.sendData(buf);

                //rtpSession.sendData(buf);
            rtpSession.sendData(str.getBytes());


        // 7. Terminate the session, takes a few ms to kill threads in order.
        //rtpSession.endSession();
        //This may result in "Sleep interrupted" messages, ignore them
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        */


    }
}