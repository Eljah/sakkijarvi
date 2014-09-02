package pmundur;

/**
 * Created with IntelliJ IDEA.
 * User: ilya
 * Date: 15.09.13
 * Time: 6:26
 * To change this template use File | Settings | File Templates.
 */

import pmundur.jlibrtp.DataFrame;
import pmundur.jlibrtp.Participant;
import pmundur.jlibrtp.RTPAppIntf;
import pmundur.jlibrtp.RTPSession;

import java.net.DatagramSocket;

/**
 * <p>This is an example of how to set up a Unicast session.</p>
 * <p>It does not accept any input arguments and is therefore of limited practical value, but it shows
 * the basics.</p>
 *
 * <p> The class has to implement RTPAppIntf.</p>
 * @author Arne Kepp
 */
public class UnicastExample implements RTPAppIntf {
    /** Holds a RTPSession instance */
    RTPSession rtpSession = null;

    /** A minimal constructor */
    public UnicastExample(RTPSession rtpSession) {
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


    public static void main(String[] args) {
        // 1. Create sockets for the RTPSession
        DatagramSocket rtpSocket = null;
        DatagramSocket rtcpSocket = null;
        try {
            rtpSocket = new DatagramSocket(16384);
            rtcpSocket = new DatagramSocket(16385);
        } catch (Exception e) {
            System.out.println("RTPSession failed to obtain port");
        }

        // 2. Create the RTP session
        RTPSession rtpSession = new RTPSession(rtpSocket, rtcpSocket);

        // 3. Instantiate the application object
        UnicastExample uex = new UnicastExample(rtpSession);

        // 4. Add participants we want to notify upon registration
        // a. Hopefully nobody is listening on this port.
        Participant part = new Participant("127.0.0.1",16386,16387);
        rtpSession.addParticipant(part);

        // 5. Register the callback interface, this launches RTCP threads too
        // The two null parameters are for the RTCP and debug interfaces, not use here
        rtpSession.RTPSessionRegister(uex, null, null);

        // Wait 2500 ms, because of the initial RTCP wait
        try{ Thread.sleep(2000); } catch(Exception e) {}

        // Note: The wait is optional, but insures SDES packets
        //       receive participants before continuing

        // 6. Send some data
        while(true)
        {


            String str = "Hi there!";
        rtpSession.sendData(str.getBytes());
        }
        // 7. Terminate the session, takes a few ms to kill threads in order.
        //rtpSession.endSession();
        //This may result in "Sleep interrupted" messages, ignore them
    }
}