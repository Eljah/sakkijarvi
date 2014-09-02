package ru.sakkijarvi;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.imageio.plugins.jpeg.JPEGImageWriter;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.w3c.dom.Element;
import pmundur.JpegRTPEncoder;
import sun.net.www.content.image.jpeg;


/**
 * Created by Leon on 21.08.14.
 */
public class TestMain {


   //static int PACKETS_NUMBER=100;

    static int PACKETS_NUMBER=88;


    static BufferedImage outputImage = null;
    byte[] imageInByte = null;
    static byte[][] arrayImageInByte;


    private static BufferedImage process(BufferedImage old, int pos) {
        int w = old.getWidth();
        int h = old.getHeight();
        BufferedImage img = new BufferedImage(
                w, h, old.getType());
        Graphics2D g2d = img.createGraphics();
        g2d.drawImage(old, 0, 0, null);
        g2d.setPaint(Color.red);
        g2d.setFont(new Font("Serif", Font.BOLD, 20));
        String s = "Hello, world! "+pos;
        FontMetrics fm = g2d.getFontMetrics();
        int x = img.getWidth() - fm.stringWidth(s) - 5;
        int y = fm.getHeight();
        g2d.drawString(s, x-pos*5, y);
        g2d.dispose();
        return img;
    }



    public static void saveAsJpeg(String jpgFlag,BufferedImage image_to_save, float JPEGcompression, FileOutputStream fos){
        JPEGImageWriter imageWriter = (JPEGImageWriter) ImageIO.getImageWritersBySuffix("jpeg").next();
        ImageOutputStream ios = null;
        try {
            ios = ImageIO.createImageOutputStream(fos);
        } catch (IOException e) {
            e.printStackTrace();
        }
        imageWriter.setOutput(ios);

        //and metadata
        IIOMetadata imageMetaData = imageWriter.getDefaultImageMetadata(new ImageTypeSpecifier(image_to_save), null);

        if (jpgFlag != null){

            int dpi = 96;

            try {
                dpi = Integer.parseInt(jpgFlag);
            } catch (Exception e) {
                e.printStackTrace();
            }

            //old metadata
            //jpegEncodeParam.setDensityUnit(com.sun.image.codec.jpeg.JPEGEncodeParam.DENSITY_UNIT_DOTS_INCH);
            //jpegEncodeParam.setXDensity(dpi);
            //jpegEncodeParam.setYDensity(dpi);

            //new metadata
            Element tree = (Element) imageMetaData.getAsTree("javax_imageio_jpeg_image_1.0");
            Element jfif = (Element)tree.getElementsByTagName("app0JFIF").item(0);
            jfif.setAttribute("Xdensity", Integer.toString(dpi));
            jfif.setAttribute("Ydensity", Integer.toString(dpi));

        }

        if(JPEGcompression>=0 && JPEGcompression<=1f){

            //old compression
            //jpegEncodeParam.setQuality(JPEGcompression,false);

            // new Compression
            JPEGImageWriteParam jpegParams = (JPEGImageWriteParam) imageWriter.getDefaultWriteParam();
            jpegParams.setCompressionMode(JPEGImageWriteParam.MODE_EXPLICIT);
            jpegParams.setCompressionQuality(JPEGcompression);
            //jpegParams.
        }

        //old write and clean
        //jpegEncoder.encode(image_to_save, jpegEncodeParam);

        //new Write and clean up
        try {

            imageWriter.write(imageMetaData, new IIOImage(image_to_save, null, null), null);
            ios.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        imageWriter.dispose();
    }



public static void prepareJpegs()
{
    outputImage = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
    File img = new File("fon.jpeg");
    //File img = new File("/home/ilya/test00000.jpeg");

    arrayImageInByte=new byte[PACKETS_NUMBER][];
    final int FRAME_SIZE = 1392;// 1260;  //1300

    int i=20;


    for (int g=0;g<PACKETS_NUMBER;g++)
    {
        try {
            outputImage = ImageIO.read(img);
            process(outputImage,i);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(outputImage, "jpg", baos);
            baos.flush();

            OutputStream out = new ByteArrayOutputStream();
            //JpegRTPEncoder jpen = new JpegRTPEncoder(outputImage, 80, out);
            JpegRTPEncoder jpen = new JpegRTPEncoder(outputImage, 255, out); //todo 80 was hardcoded in the underlied method   255 get some problems
            int MARKERS=0;
            jpen.Compress(g, (FRAME_SIZE-MARKERS)*g, (FRAME_SIZE), g*MARKERS);

            arrayImageInByte[g]= ((ByteArrayOutputStream) out).toByteArray();

            //imageInByte = ((ByteArrayOutputStream) out).toByteArray();
            System.out.println("JPEG - > RTP/JPEG done; RTP/JPEG size is " + arrayImageInByte[g].length+" n="+g);
            System.out.println(toHexArray(arrayImageInByte[g]));

        } catch (IOException e) {
        }
    }
}







    public static void main(String[] args) throws Exception {
        Logger LOG= Log.getRootLogger();
        //LOG.setDebugEnabled(true);

        prepareJpegs();

        TestRtspServlet.setImage(arrayImageInByte);

        Server server = new Server();

        ServerConnector pxy = new ServerConnector(server);
        pxy.setPort(8888);
        server.addConnector(pxy);

// Use ContextHandlerCollection
        ContextHandlerCollection contexts = new ContextHandlerCollection();
// Don't forget to add it to the server!
        server.setHandler(contexts);

        ServletContextHandler testApp = new ServletContextHandler(contexts, "/",
                ServletContextHandler.NO_SESSIONS);

        //ServletHolder rootServletHolder = new ServletHolder(HelloServlet.class);
        ServletHolder testServletHolder = new ServletHolder(TestRtspServlet.class);

        //testApp.addServlet(rootServletHolder, "/hello/*");
        testApp.addServlet(testServletHolder, "/auto/*");

        // Specify the Session ID Manager
        HashSessionIdManager idmanager = new HashSessionIdManager();
        server.setSessionIdManager(idmanager);

        // Create the SessionHandler (wrapper) to handle the sessions
        HashSessionManager manager = new HashSessionManager();
        SessionHandler sessions = new SessionHandler(manager);

        testApp.setSessionHandler(sessions);



        server.setHandler(testApp);

        server.start();
        server.join();
    }

    public static class HelloServlet extends HttpServlet {
        private static final long serialVersionUID = -6154475799000019575L;
        private static final String greeting = "Hello World";

        protected void doGet(HttpServletRequest request,
                             HttpServletResponse response) throws ServletException,
                IOException {
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(greeting);
            response.getWriter().println("<h1>Hello Servlet</h1>");
            response.getWriter().println("session=" + request.getSession(true).getId());

        }
    }

    static final byte[] HEX_CHAR_TABLE = {
            (byte)'0', (byte)'1', (byte)'2', (byte)'3',
            (byte)'4', (byte)'5', (byte)'6', (byte)'7',
            (byte)'8', (byte)'9', (byte)'a', (byte)'b',
            (byte)'c', (byte)'d', (byte)'e', (byte)'f'
    };

      public static String toHexArray(byte[] raw)
            throws UnsupportedEncodingException
    {
        byte[] hex = new byte[2 * raw.length];
        int index = 0;

        for (byte b : raw) {
            int v = b & 0xFF;
            hex[index++] = HEX_CHAR_TABLE[v >>> 4];
            hex[index++] = HEX_CHAR_TABLE[v & 0xF];
        }
        return new String(hex, "ASCII");
    }
}

