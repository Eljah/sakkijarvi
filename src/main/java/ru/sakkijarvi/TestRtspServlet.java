package ru.sakkijarvi;

import pmundur.jlibrtp.StaticProcs;
import ru.sakkijarvi.rtp.RTPStream;
import ru.sakkijarvi.rtsp.RtspServlet;
import ru.sakkijarvi.rtsp.SDP;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by Leon on 21.08.14.
 */
public class TestRtspServlet extends RtspServlet {

    static byte[][] arrayImageInByte;

    String RTSP_ID="123456";
    int local_rtp_port = 59720;
    int local_rtcp_port = 59721;
    int RTP_dest_port = 59820; //destination port for RTP packets  (given by the RTSP Client)
    int RTCP_dest_port = 59821; //destination port for RTP packets  (given by the RTSP Client)
    RTPStream rtpStream;

    public static void setImage( byte[][] _array)
    {
        arrayImageInByte=_array;
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.doOptions(request, response);
        //doGet(request,response);
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                Locale.ENGLISH);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));


        response.setHeader("CSeq", "2");
        response.setHeader("Date", dateFormat.format(new Date()));
        //"Date: " +
        response.setStatus(HttpServletResponse.SC_OK);
        ////response.getWriter().
        //response.getWriter().println("<h1>Hello Servlet</h1>");
        //response.getWriter().println("session=" + request.getSession(true).getId());
    }

    protected void doDescribe(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.doDescribe(request, response);
        //doGet(request,response);

        rtpStream=new RTPStream(InetAddress.getByName(request.getRemoteAddr()), RTP_dest_port, RTCP_dest_port, local_rtp_port, local_rtcp_port);
        rtpStream.setImage(arrayImageInByte);
        rtpStream.prepareBroadcasting();

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                Locale.ENGLISH);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String remoteIP=request.getLocalAddr();

        response.setHeader("CSeq", "3");
        //"Date: " +
        /*
        "Content-Type: application/sdp" + CRLF +
                        "Content-Base: rtsp://localhost:6666/autostream" + EXTENSION + "/" + CRLF +
                        "Server: GStreamer RTSP server" + CRLF +
                        "Date: " + dateFormat.format(new Date()) + CRLF +
                        "Content-Length: 276"
         */
        response.setHeader("Content-Type", "application/sdp");
        response.setHeader("Content-Base", "rtsp://"+remoteIP+":"+request.getLocalPort()+request.getRequestURI());
        response.setHeader("Date", dateFormat.format(new Date()));

        String CRLF="\r\n";
        String RTSP_ID="123456";
        String MJPEG_TYPE="26";

        int RTP_dest_port = 59820; //destination port for RTP packets  (given by the RTSP Client)


        SDP responced=new SDP("");
        response.getWriter().println("v=0" + CRLF +
                "o=- " + RTSP_ID + " 1 IN IP4 127.0.0.1" + CRLF +
                "s=" + RTSP_ID + CRLF +
                "i=rtsp-server" + CRLF +
                "e=NONE" + CRLF +
                "t=0 0" + CRLF +
                "a=tool:GStreamer" + CRLF +
                "a=type:broadcast" + CRLF +
                "a=control:*" + CRLF +
                "a=range:npt=0,000000-" + CRLF +
                "m=video " + RTP_dest_port + " RTP/AVP " + MJPEG_TYPE + CRLF +
                "c=IN IP4 127.0.0.1" + CRLF +
                //"a=rtpmap:96 JPEG/90000" +CRLF+   PCMU
                "a=rtpmap:" + MJPEG_TYPE + " JPEG/90000" + CRLF +
                //"a=rtpmap:"+MJPEG_TYPE+" PCMU/8000"+CRLF+
                "a=control:stream=0" + CRLF);



        response.setStatus(HttpServletResponse.SC_OK);
        ////response.getWriter().
        //response.getWriter().println("<h1>Hello Servlet</h1>");
        //response.getWriter().println("session=" + request.getSession(true).getId());
    }




    protected void doSetup(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.doDescribe(request, response);
        //doGet(request,response);
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                Locale.ENGLISH);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        String remoteIP=request.getLocalAddr();

        response.setHeader("CSeq", "4");
        response.setHeader("Transport", "RTP/AVP;unicast;client_port=" + RTP_dest_port + "-" + RTCP_dest_port + ";server_port=" + local_rtp_port + "-" + local_rtcp_port + ";source="+remoteIP+";ssrc=11;mode=\"PLAY\"");
        response.setHeader("Content-Base", "rtsp://"+remoteIP+":"+request.getLocalPort()+request.getRequestURI());
        response.setHeader("Date", dateFormat.format(new Date()));
        //response.setHeader("Session", request.getSession(true).getId());
        response.setHeader("Session", RTSP_ID);

        response.setStatus(HttpServletResponse.SC_OK);
        ////response.getWriter().
        //response.getWriter().println("<h1>Hello Servlet</h1>");
        //response.getWriter().println("session=" + request.getSession(true).getId());
    }

    protected void doPlay(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException, InterruptedException {
        String RTSP_ID="123456";


        super.doPlay(request, response);
        //doGet(request,response);
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                Locale.ENGLISH);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        long longStartTime=System.currentTimeMillis();
        String longTimeString=	StaticProcs.bytesToUIntLong(StaticProcs.uIntLongToByteWord(longStartTime), 0)+"";
        int seqString = 0;
        rtpStream.setStartTime(longStartTime);

        response.setHeader("CSeq", "5");
        response.setHeader("RTP-Info", "url=rtsp://"+request.getLocalAddr()+":"+request.getLocalPort()+request.getRequestURI()+"/stream=0;seq=" + seqString + ";rtptime=" + longTimeString);
        response.setHeader("Range", "npt=0,0-");
        response.setHeader("Date", dateFormat.format(new Date()));
        response.setHeader("Session", RTSP_ID);

        /*
          "RTP-Info: url=rtsp://127.0.0.1:6666/autostream" + EXTENSION + "/stream=0;seq=" + seqString + ";rtptime=" + longTimeString + CRLF + //todo check on extension
                        //"RTP-Info: url=rtsp://192.168.0.107:6666/autostream/stream=0;seq=" + seqString + ";rtptime=" + longTimeString + CRLF +
                                //"RTP-Info: url=rtsp://localhost:6666/autostream" + EXTENSION + "/stream=0;seq=" + (imagenb * 100 + j) + ";rtptime=" + (imagenb * FRAME_PERIOD + j) + CRLF +
                                // "RTP-Info: url=rtp://localhost:"+RTP_dest_port+"/stream=1;seq="+(imagenb*100+j)+";rtptime="+(imagenb*FRAME_PERIOD+j)+CRLF+
                                //"Range: npt=0.0-119.961667" + CRLF +
                                "Range: npt=0,0-119,961667" + CRLF +
                                "Server: GStreamer RTSP server" + CRLF +
                                "Session: " + RTSP_ID + CRLF +
                                "Date: " + dateFormat.format(new Date()) + CRLF + CRLF);
         */


        response.setStatus(HttpServletResponse.SC_OK);
       rtpStream.startBroadcasting();
        Thread broadcast=new Thread(rtpStream);
        broadcast.start();

        ////response.getWriter().
        //response.getWriter().println("<h1>Hello Servlet</h1>");
        //response.getWriter().println("session=" + request.getSession(true).getId());
    }


}
