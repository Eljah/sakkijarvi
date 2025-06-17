package ru.sakkijarvi.rtsp;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Very small RTSP server example supporting MJPEG and rudimentary H.264 streaming.
 * It is intentionally simple and only meant for testing.
 */
public class RtspStreamingServer {
    private final int port;
    private final List<BufferedImage> frames;
    private final boolean h264;
    private Thread thread;
    private final AtomicBoolean running = new AtomicBoolean();

    public RtspStreamingServer(int port, List<BufferedImage> frames, boolean h264) {
        this.port = port;
        this.frames = frames;
        this.h264 = h264;
    }

    public void start() {
        running.set(true);
        thread = new Thread(this::run, "rtsp-server");
        thread.start();
    }

    public void stop() {
        running.set(false);
        try {
            if (thread != null) thread.join();
        } catch (InterruptedException ignored) {}
    }

    private void run() {
        try (ServerSocket server = new ServerSocket(port)) {
            Socket client = server.accept();
            BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream out = client.getOutputStream();

            String line;
            int clientPort = 0;
            String seq = "0";
            while ((line = br.readLine()) != null && running.get()) {
                if (line.startsWith("CSeq")) {
                    seq = line.substring(5).trim();
                } else if (line.startsWith("Transport:")) {
                    int p = line.indexOf("client_port=");
                    if (p >= 0) {
                        String portPart = line.substring(p + 12);
                        if (portPart.contains("-")) portPart = portPart.substring(0, portPart.indexOf('-'));
                        clientPort = Integer.parseInt(portPart.trim());
                    }
                }
                if (line.isEmpty()) {
                    String requestLine = br.readLine();
                    if (requestLine == null) break;
                    if (requestLine.startsWith("OPTIONS")) {
                        respond(out, seq, "200 OK", "Public: OPTIONS, DESCRIBE, SETUP, PLAY\r\n");
                    } else if (requestLine.startsWith("DESCRIBE")) {
                        String sdp =
                                "v=0\r\n" +
                                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                                "s=Stream\r\n" +
                                "t=0 0\r\n" +
                                "m=video 0 RTP/AVP " + (h264 ? "96" : "26") + "\r\n" +
                                "c=IN IP4 0.0.0.0\r\n" +
                                (h264 ? "a=rtpmap:96 H264/90000\r\n" : "a=rtpmap:26 JPEG/90000\r\n");
                        respond(out, seq, "200 OK", "Content-Type: application/sdp\r\nContent-Length: " + sdp.length() + "\r\n\r\n" + sdp);
                    } else if (requestLine.startsWith("SETUP")) {
                        respond(out, seq, "200 OK", "Transport: RTP/AVP;unicast;server_port=50000;client_port=" + clientPort + "\r\nSession: 123456\r\n");
                    } else if (requestLine.startsWith("PLAY")) {
                        respond(out, seq, "200 OK", "Session: 123456\r\n");
                        stream(client.getInetAddress(), clientPort);
                        break;
                    } else {
                        respond(out, seq, "501 Not Implemented", "");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void respond(OutputStream out, String seq, String status, String extra) throws IOException {
        String resp = "RTSP/1.0 " + status + "\r\nCSeq: " + seq + "\r\n" + extra + "\r\n";
        out.write(resp.getBytes());
    }

    private void stream(InetAddress addr, int port) throws IOException {
        DatagramSocket sock = new DatagramSocket();
        int seq = 0;
        int timestamp = 0;
        for (BufferedImage f : frames) {
            if (!running.get()) break;
            byte[] payload;
            if (h264) {
                payload = encodeH264Frame(f);
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(f, "jpg", baos);
                payload = baos.toByteArray();
            }
            ByteBuffer rtp = ByteBuffer.allocate(12 + payload.length);
            rtp.put((byte)0x80); // V=2
            rtp.put((byte)(h264 ? 96 : 26));
            rtp.putShort((short)seq++);
            rtp.putInt(timestamp);
            rtp.putInt(0); // SSRC
            rtp.put(payload);
            DatagramPacket pkt = new DatagramPacket(rtp.array(), rtp.position(), addr, port);
            sock.send(pkt);
            timestamp += 3600; // 25fps
            try { Thread.sleep(40); } catch (InterruptedException ignored) {}
        }
        sock.close();
    }

    private byte[] encodeH264Frame(BufferedImage img) throws IOException {
        org.jcodec.codecs.h264.H264Encoder encoder = org.jcodec.codecs.h264.H264Encoder.createH264Encoder();
        org.jcodec.common.model.Picture pic = org.jcodec.scale.AWTUtil.fromBufferedImage(img, org.jcodec.common.model.ColorSpace.RGB);
        ByteBuffer buf = ByteBuffer.allocate(pic.getWidth() * pic.getHeight() * 6);
        org.jcodec.common.VideoEncoder.EncodedFrame out = encoder.encodeFrame(pic, buf);
        ByteBuffer data = out.getData();
        byte[] arr = new byte[data.remaining()];
        data.get(arr);
        return arr;
    }
}
