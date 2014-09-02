package pmundur; /**
 * Created with IntelliJ IDEA.
 * User: ilya
 * Date: 14.09.13
 * Time: 21:55
 * To change this template use File | Settings | File Templates.
 */
// Version 1.0a
// Copyright (C) 1998, James R. Weeks and BioElectroMech.
// Visit BioElectroMech at www.obrador.com.  Email James@obrador.com.

// See license.txt for details about the allowed used of this software.
// This software is based in part on the work of the Independent JPEG Group.
// See IJGreadme.txt for details about the Independent JPEG Group's license.

// This encoder is inspired by the Java Jpeg encoder by Florian Raemy,
// studwww.eurecom.fr/~raemy.
// It borrows a great deal of code and structure from the Independent
// Jpeg Group's Jpeg 6a library, Copyright Thomas G. Lane.
// See license.txt for details.

import sun.misc.IOUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.*;
import java.util.Arrays;
import java.util.Vector;

/*
* JpegEncoder - The JPEG main program which performs a jpeg compression of
* an image.
*/

public class JpegRTPEncoder extends Frame {
    Thread runner;
    BufferedOutputStream outStream;
    Image image;
    JpegInfoRTP JpegObj;
    HuffmanRTP Huf;
    DCTRTP DCTRTP;
    int imageHeight, imageWidth;
    int Quality = 255;//127;
    int code;
    public static int[] jpegNaturalOrder = {
            0, 1, 8, 16, 9, 2, 3, 10,
            17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34,
            27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36,
            29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46,
            53, 60, 61, 54, 47, 55, 62, 63,
    };

    public int fragmentOffset;
    public int frameSize;
    //public int DQTlength=134;
    public int DQTlength = 128;

    public static void main(String[] args) {
        BufferedImage outputImage = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        try {
            OutputStream out = new ByteArrayOutputStream();
            File img = new File("fon.jpeg");
            outputImage = ImageIO.read(img);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(outputImage, "jpg", baos);
            baos.flush();
            byte[] imageInByte = baos.toByteArray();
            //JpegRTPEncoder jpen=new JpegRTPEncoder(outputImage, 80, out);
            JpegRTPEncoder jpen = new JpegRTPEncoder(outputImage, 255, out);
            jpen.Compress(0, 0, 1400, 0,0);

            FileOutputStream fos = new FileOutputStream(new File("outputtest.jpeg"));

            // Put data in your baos

            ((ByteArrayOutputStream) out).writeTo(fos);
            System.out.println("JPEG - > JPEG done");

        } catch (IOException e) {
        }


    }

    public JpegRTPEncoder(Image image, int quality, OutputStream out) {
        MediaTracker tracker = new MediaTracker(this);
        tracker.addImage(image, 0);
        try {
            tracker.waitForID(0);
        } catch (InterruptedException e) {
            //TODO
        }
        /*
        * Quality of the image.
        * 0 to 100 and from bad image quality, high compression to good
        * image quality low compression
        */
        Quality = quality;

        /*
        * Getting picture information
        * It takes the Width, Height and RGB scans of the image.
        */
        JpegObj = new JpegInfoRTP(image);

        imageHeight = JpegObj.imageHeight;
        imageWidth = JpegObj.imageWidth;
        outStream = new BufferedOutputStream(out);
        DCTRTP = new DCTRTP(Quality);
        Huf = new HuffmanRTP(imageWidth, imageHeight);
    }

    public void setQuality(int quality) {
        DCTRTP = new DCTRTP(quality);
    }

    public int getQuality() {
        return Quality;
    }

    public void Compress(int packetnum, int offset, int frame_size, int markers, int jpegmarkers) {
        fragmentOffset = offset;
        frameSize = frame_size;
        int jpermarkerheadrers=0;
        if (offset == 0) {   //+132
            WriteRTPJPEGHeaders(outStream, offset);
            if (Quality > 128) {
                WriteRTPBeforeQuantizationTableHeaders(outStream);
                WriteQuantizationTableHeaders(outStream);
                jpermarkerheadrers = WriteHeaders(outStream);
                System.out.println("We consider RTP|JPEG markers before the compressed data:" + markers);
                System.out.println("We consider JPEG markers before the compressed data:" + jpegmarkers+" and they really are "+jpermarkerheadrers);
                System.out.println("Actually the size of compressed data placed to the current packet will be:" + (frameSize -markers - jpegmarkers));
                WriteCompressedData(offset, frameSize -markers- jpegmarkers, outStream);
            }
        } else {
            System.out.println("We taking into account payload offset due to RTP|JPEG markers added in the first packet" + (offset - markers - jpegmarkers)+"while the initial offset was "+offset);
            WriteRTPJPEGHeaders(outStream, offset - markers);
            System.out.println("Actually the size of compressed data placed to the current packet will be:" + (frameSize - jpermarkerheadrers));
            WriteCompressedData(offset-markers-jpegmarkers, frameSize- jpermarkerheadrers, outStream);
        }
        //WriteRestartMarker(outStream);

        if (packetnum == 87) {
            WriteEOI(outStream);
        }
        try {
            outStream.flush();
        } catch (IOException e) {
            //TODO
            System.out.println("IO Error: " + e.getMessage());
        }
    }

    public void WriteCompressedData(int offset, int frame_size, BufferedOutputStream outStream) {
        int i, j, r, c, a, b;
        int comp, xpos, ypos, xblockoffset, yblockoffset;
        float inputArray[][];
        float dctArray1[][] = new float[8][8];
        double dctArray2[][] = new double[8][8];
        int dctArray3[] = new int[8 * 8];

        /*
         * This method controls the compression of the image.
         * Starting at the upper left of the image, it compresses 8x8 blocks
         * of data until the entire image has been compressed.
         */

        int lastDCvalue[] = new int[JpegObj.NumberOfComponents];
        //int zeroArray[] = new int[64]; // initialized to hold all zeros
        //int Width = 0, Height = 0;
        //int nothing = 0, not;
        int MinBlockWidth, MinBlockHeight;
// This initial setting of MinBlockWidth and MinBlockHeight is done to
// ensure they start with values larger than will actually be the case.
        MinBlockWidth = ((imageWidth % 8 != 0) ? (int) (Math.floor(imageWidth / 8d) + 1) * 8 : imageWidth);
        MinBlockHeight = ((imageHeight % 8 != 0) ? (int) (Math.floor(imageHeight / 8d) + 1) * 8 : imageHeight);
        for (comp = 0; comp < JpegObj.NumberOfComponents; comp++) {
            MinBlockWidth = Math.min(MinBlockWidth, JpegObj.BlockWidth[comp]);
            MinBlockHeight = Math.min(MinBlockHeight, JpegObj.BlockHeight[comp]);
        }
        ByteArrayOutputStream bytesOutputStream = new ByteArrayOutputStream();


        xpos = 0;
        for (r = 0; r < MinBlockHeight; r++) {
            for (c = 0; c < MinBlockWidth; c++) {
                xpos = c * 8;
                ypos = r * 8;

                //xpos = c;
                //ypos = r;

                for (comp = 0; comp < JpegObj.NumberOfComponents; comp++) {
                    //Width = JpegObj.BlockWidth[comp];
                    //Height = JpegObj.BlockHeight[comp];
                    inputArray = JpegObj.Components[comp];

                    for (i = 0; i < JpegObj.VsampFactor[comp]; i++) {
                        for (j = 0; j < JpegObj.HsampFactor[comp]; j++) {
                            xblockoffset = j * 8;
                            yblockoffset = i * 8;
                            for (a = 0; a < 8; a++) {
                                for (b = 0; b < 8; b++) {

// I believe this is where the dirty line at the bottom of the image is
// coming from.  I need to do a check here to make sure I'm not reading past
// image data.
// This seems to not be a big issue right now. (04/04/98)
                                    dctArray1[a][b] = inputArray[ypos + yblockoffset + a][xpos + xblockoffset + b];
//todo revert
                                    // dctArray1[a][b] = inputArray[ypos + yblockoffset + a][xpos + xblockoffset + b];
                                    // System.out.println("["+ypos+" + "+yblockoffset+" + "+a+"]["+xpos+" + "+xblockoffset+" + "+b+"]");
                                    // System.out.println("["+(ypos+yblockoffset+a)+"]["+(xpos+xblockoffset+b)+"]");

                                }
                            }
// The following code commented out because on some images this technique
// results in poor right and bottom borders.
//                        if ((!JpegObj.lastColumnIsDummy[comp] || c < Width - 1) && (!JpegObj.lastRowIsDummy[comp] || r < Height - 1)) {
                            dctArray2 = DCTRTP.forwardDCT(dctArray1);
                            dctArray3 = DCTRTP.quantizeBlock(dctArray2, JpegObj.QtableNumber[comp]);
//                        }
//                        else {
//                           zeroArray[0] = dctArray3[0];
//                           zeroArray[0] = lastDCvalue[comp];
//                           dctArray3 = zeroArray;
//                        }
                            Huf.HuffmanBlockEncoder(bytesOutputStream, dctArray3, lastDCvalue[comp], JpegObj.DCtableNumber[comp], JpegObj.ACtableNumber[comp]);
                            lastDCvalue[comp] = dctArray3[0];
                        }
                    }
                }
            }
        }
        //Arrays.copyOfRange(dctArray3,fragmentOffset,Math.min((fragmentOffset+frameSize),dctArray3.length-1))


        Huf.flushBuffer(bytesOutputStream);


        byte[] imageInByteNotCompressed = bytesOutputStream.toByteArray();
        System.out.println(offset);
        System.out.println(offset + frame_size);
        System.out.println(imageInByteNotCompressed.length);
        byte[] imageInByteNotCompressedFragmented = Arrays.copyOfRange(imageInByteNotCompressed, Math.min(offset, imageInByteNotCompressed.length - 1), Math.min((offset + frame_size), imageInByteNotCompressed.length - 1));
        System.out.println("Length of fragment is " + imageInByteNotCompressedFragmented.length);
        System.out.println(imageInByteNotCompressedFragmented.length);
        //OutputStream bytesOutputStreamFragmented=new ByteArrayOutputStream();
        try {
            //byte[] SOI = {(byte) 0xd8};    //todo remove, experimental soi marker addition
            //outStream.write(SOI);
            outStream.write(imageInByteNotCompressedFragmented);
        }
        //    bytesOutputStreamFragmented.write(imageInByteNotCompressedFragmented);
        //}
        catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }

    public void WriteEOI(BufferedOutputStream out) {
        byte[] EOI = {(byte) 0xFF, (byte) 0xD9};
        WriteMarker(EOI, out);
    }

    public void WriteRTPJPEGHeaders(BufferedOutputStream out, int offset) {
        byte TypeSpecific = 0x00;
        byte[] FragmentOffset = new byte[3];
        FragmentOffset[0] = (byte) ((offset) >> 16);
        FragmentOffset[1] = (byte) ((offset) >> 8);
        FragmentOffset[2] = (byte) ((offset) & 0xFF);
        byte Type = (byte) 0;///(byte)64; //todo let it be   Sitched from 0 !!!!
        byte Q = (byte) 255; //(byte)Quality; //todo let it be
        int imageWidth_8 = imageWidth / 8;
        int imageHeight_8 = imageHeight / 8;
        System.out.println("Image width normed" + imageWidth_8);
        System.out.println("Image height normed" + imageHeight_8);
        byte Width = (byte) (imageWidth_8); //todo let it be
        byte Height = (byte) (imageHeight_8); //todo let it be
        byte[] RTPJPEG = new byte[8];
        RTPJPEG[0] = TypeSpecific;
        RTPJPEG[1] = FragmentOffset[0];
        RTPJPEG[2] = FragmentOffset[1];
        RTPJPEG[3] = FragmentOffset[2];
        RTPJPEG[4] = Type;
        RTPJPEG[5] = Q;
        RTPJPEG[6] = Width;
        RTPJPEG[7] = Height;
        //WriteArray(RTPJPEG, out);
        try {
            out.write(RTPJPEG, 0, 8);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void WriteRTPBeforeQuantizationTableHeaders(BufferedOutputStream out) {
        byte mbz = 0x00; //todo xz
        byte Precision = 0x00; //can be ox00
        byte[] Length = new byte[2];
        Length[0] = (byte) (DQTlength >> 8);
        Length[1] = (byte) (DQTlength & 0xFF);
        byte BeforeDQT[] = new byte[4];
        BeforeDQT[0] = mbz;
        BeforeDQT[1] = Precision;
        BeforeDQT[2] = Length[0];
        BeforeDQT[3] = Length[1];
        //WriteArray(BeforeDQT, out);
        try {
            out.write(BeforeDQT, 0, 4);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void WriteRestartMarker(BufferedOutputStream out) {
        byte[] RestartInterval = new byte[2];
        int RestartInt = frameSize;
        RestartInterval[0] = (byte) (RestartInt >> 8);
        RestartInterval[1] = (byte) (RestartInt & 0xFF);
        byte Restart[] = new byte[4];
        Restart[0] = RestartInterval[0];
        Restart[1] = RestartInterval[1];
        Restart[2] = (byte) (1 << 7);
        Restart[2] = (byte) (Restart[2] | 1 << 6);
        Restart[2] = (byte) (Restart[2] | 3 << 4);
        Restart[2] = (byte) (Restart[2] | 15);
        Restart[3] = (byte) (0xFF);


        //WriteArray(BeforeDQT, out);
        try {
            out.write(Restart, 0, 4);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


    public void WriteQuantizationTableHeaders(BufferedOutputStream out) {

        int i, j, offset;
        int tempArray[];
        //byte DQT[] = new byte[68];
        //byte DQT[] = new byte[128];
/*        DQT[0] = (byte) 0xFF;
    DQT[1] = (byte) 0xDB;
    DQT[2] = (byte) 0x00;
    DQT[3] = (byte) 0x84;
    offset = 0;
    for (i = 0; i < 1; i++) {
    DQT[offset++] = (byte) ((0 << 4) + i);
    tempArray = DCTRTP.quantum[i];
    for (j = 0; j < 64; j++) {
        DQT[offset++] = (byte) tempArray[jpegNaturalOrder[j]];
    }

}
   */
        byte DQT[] = {0x10, 0x0b, 0x0c, 0x0e, 0x0c, 0x0a, 0x10, 0x0e, 0x0d, 0x0e, 0x12, 0x11, 0x10, 0x13, 0x18, 0x28, 0x1a, 0x18, 0x16, 0x16, 0x18, 0x31, 0x23, 0x25, 0x1d, 0x28, 0x3a, 0x33, 0x3d, 0x3c, 0x39, 0x33, 0x38, 0x37, 0x40, 0x48, 0x5c, 0x4e, 0x40, 0x44, 0x57, 0x45, 0x37, 0x38, 0x50, 0x6d, 0x51, 0x57, 0x5f, 0x62, 0x67, 0x68, 0x67, 0x3e, 0x4d, 0x71, 0x79, 0x70, 0x64, 0x78, 0x5c, 0x65, 0x67, 0x63, 0x11, 0x12, 0x12, 0x18, 0x15, 0x18, 0x2f, 0x1a, 0x1a, 0x2f, 0x63, 0x42, 0x38, 0x42, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63, 0x63};
        //WriteArray(DQT, out);
        try {
            out.write(DQT, 0, 128);
            //out.write(DQT, 0, 124);
            //out.write(DQT, 0, 64);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        /*
        byte DQT[] = {0x10,0x0b,0x0c,0x0e,0x0c,0x0a,0x10,0x0e,0x0d,0x0e,0x12,0x11,0x10,0x13,0x18,0x28,0x1a,0x18,0x16,0x16,0x18,0x31,0x23,0x25,0x1d,0x28,0x3a,0x33,0x3d,0x3c,0x39,0x33,0x38,0x37,0x40,0x48,0x5c,0x4e,0x40,0x44,0x57,0x45,0x37,0x38,0x50,0x6d,0x51,0x57,0x5f,0x62,0x67,0x68,0x67,0x3e,0x4d,0x71,0x79,0x70,0x64,0x78,0x5c,0x65,0x67,0x63,0x11,0x12,0x12,0x18,0x15,0x18,0x2f,0x1a,0x1a,0x2f,0x63,0x42,0x38,0x42,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63,0x63};
        try {
            out.write(DQT, 0, 128);
            //out.write(DQT, 0, 64);

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
          */
    }


    public int WriteHeaders(BufferedOutputStream out) {
        int i, j, index, offset, length;
        int tempArray[];

        int markersByteCount = 0;

// the SOI marker
        byte[] SOI = {(byte) 0xFF, (byte) 0xD8};
        markersByteCount=markersByteCount+SOI.length;
        WriteMarker(SOI, out);

// The order of the following headers is quiet inconsequential.
// the JFIF header
        byte JFIF[] = new byte[18];
        JFIF[0] = (byte) 0xff;
        JFIF[1] = (byte) 0xe0;
        JFIF[2] = (byte) 0x00;
        JFIF[3] = (byte) 0x10;
        JFIF[4] = (byte) 0x4a;
        JFIF[5] = (byte) 0x46;
        JFIF[6] = (byte) 0x49;
        JFIF[7] = (byte) 0x46;
        JFIF[8] = (byte) 0x00;
        JFIF[9] = (byte) 0x01;
        JFIF[10] = (byte) 0x00;
        JFIF[11] = (byte) 0x00;
        JFIF[12] = (byte) 0x00;
        JFIF[13] = (byte) 0x01;
        JFIF[14] = (byte) 0x00;
        JFIF[15] = (byte) 0x01;
        JFIF[16] = (byte) 0x00;
        JFIF[17] = (byte) 0x00;
        //markersByteCount=markersByteCount+JFIF.length;
        //WriteArray(JFIF, out);

// Comment Header
        String comment = new String();
        comment = JpegObj.getComment();
        length = comment.length();
        byte COM[] = new byte[length + 4];
        COM[0] = (byte) 0xFF;
        COM[1] = (byte) 0xFE;
        COM[2] = (byte) ((length >> 8) & 0xFF);
        COM[3] = (byte) (length & 0xFF);
        java.lang.System.arraycopy(JpegObj.Comment.getBytes(), 0, COM, 4, JpegObj.Comment.length());
        //markersByteCount=markersByteCount+COM.length;
        //WriteArray(COM, out);

// The DQT header
// 0 is the luminance index and 1 is the chrominance index
        byte DQT[] = new byte[134];
        DQT[0] = (byte) 0xFF;
        DQT[1] = (byte) 0xDB;
        DQT[2] = (byte) 0x00;
        DQT[3] = (byte) 0x84;
        offset = 4;
        for (i = 0; i < 2; i++) {
            DQT[offset++] = (byte) ((0 << 4) + i);
            tempArray = DCTRTP.quantum[i];
            for (j = 0; j < 64; j++) {
                DQT[offset++] = (byte) tempArray[jpegNaturalOrder[j]];
            }
        }
        markersByteCount=markersByteCount+DQT.length;
        WriteArray(DQT, out);

// Start of Frame Header
        byte SOF[] = new byte[19];
        SOF[0] = (byte) 0xFF;
        SOF[1] = (byte) 0xC0;
        SOF[2] = (byte) 0x00;
        SOF[3] = (byte) 17;
        SOF[4] = (byte) JpegObj.Precision;
        SOF[5] = (byte) ((JpegObj.imageHeight >> 8) & 0xFF);
        SOF[6] = (byte) ((JpegObj.imageHeight) & 0xFF);
        SOF[7] = (byte) ((JpegObj.imageWidth >> 8) & 0xFF);
        SOF[8] = (byte) ((JpegObj.imageWidth) & 0xFF);
        SOF[9] = (byte) JpegObj.NumberOfComponents;
        index = 10;
        for (i = 0; i < SOF[9]; i++) {
            SOF[index++] = (byte) JpegObj.CompID[i];
            SOF[index++] = (byte) ((JpegObj.HsampFactor[i] << 4) + JpegObj.VsampFactor[i]);
            SOF[index++] = (byte) JpegObj.QtableNumber[i];
        }
        markersByteCount=markersByteCount+SOF.length;
        WriteArray(SOF, out);

// The DHT Header
        byte DHT1[], DHT2[], DHT3[], DHT4[];
        int bytes, temp, oldindex, intermediateindex;
        length = 2;
        index = 4;
        oldindex = 4;
        DHT1 = new byte[17];
        DHT4 = new byte[4];
        DHT4[0] = (byte) 0xFF;
        DHT4[1] = (byte) 0xC4;
        for (i = 0; i < 4; i++) {
            bytes = 0;
            DHT1[index++ - oldindex] = (byte) ((int[]) Huf.bits.elementAt(i))[0];
            for (j = 1; j < 17; j++) {
                temp = ((int[]) Huf.bits.elementAt(i))[j];
                DHT1[index++ - oldindex] = (byte) temp;
                bytes += temp;
            }
            intermediateindex = index;
            DHT2 = new byte[bytes];
            for (j = 0; j < bytes; j++) {
                DHT2[index++ - intermediateindex] = (byte) ((int[]) Huf.val.elementAt(i))[j];
            }
            DHT3 = new byte[index];
            java.lang.System.arraycopy(DHT4, 0, DHT3, 0, oldindex);
            java.lang.System.arraycopy(DHT1, 0, DHT3, oldindex, 17);
            java.lang.System.arraycopy(DHT2, 0, DHT3, oldindex + 17, bytes);
            DHT4 = DHT3;
            oldindex = index;
        }
        DHT4[2] = (byte) (((index - 2) >> 8) & 0xFF);
        DHT4[3] = (byte) ((index - 2) & 0xFF);
        markersByteCount=markersByteCount+DHT4.length;
        WriteArray(DHT4, out);


// Start of Scan Header
        byte SOS[] = new byte[14];
        SOS[0] = (byte) 0xFF;
        SOS[1] = (byte) 0xDA;
        SOS[2] = (byte) 0x00;
        SOS[3] = (byte) 12;
        SOS[4] = (byte) JpegObj.NumberOfComponents;
        index = 5;
        for (i = 0; i < SOS[4]; i++) {
            SOS[index++] = (byte) JpegObj.CompID[i];
            SOS[index++] = (byte) ((JpegObj.DCtableNumber[i] << 4) + JpegObj.ACtableNumber[i]);
        }
        SOS[index++] = (byte) JpegObj.Ss;
        SOS[index++] = (byte) JpegObj.Se;
        SOS[index++] = (byte) ((JpegObj.Ah << 4) + JpegObj.Al);
        markersByteCount=markersByteCount+SOS.length;
        WriteArray(SOS, out);
        return markersByteCount; //just magic todo
    }

    void WriteMarker(byte[] data, BufferedOutputStream out) {
        try {
            out.write(data, 0, 2);
        } catch (IOException e) {
            //TODO
            System.out.println("IO Error: " + e.getMessage());
        }
    }

    void WriteArray(byte[] data, BufferedOutputStream out) {
        int length;
        try {
            length = ((data[2] & 0xFF) << 8) + (data[3] & 0xFF) + 2;
            out.write(data, 0, length);
        } catch (IOException e) {
            //TODO
            System.out.println("IO Error: " + e.getMessage());
        }
    }
}

// This class incorporates quality scaling as implemented in the JPEG-6a
// library.

 /*
 * pmundur.DCTRTP - A Java implementation of the Discreet Cosine Transform
 */

class DCTRTP {
    /**
     * pmundur.DCTRTP Block Size - default 8
     */
    public int N = 8;

    /**
     * Image Quality (0-100) - default 80 (good image / good compression)
     */
    public int QUALITY = 255; //was 80

    public int quantum[][] = new int[2][];
    public double Divisors[][] = new double[2][];

    /**
     * Quantitization Matrix for luminace.
     */
    public int quantum_luminance[] = new int[N * N];
    public double DivisorsLuminance[] = new double[N * N];

    /**
     * Quantitization Matrix for chrominance.
     */
    public int quantum_chrominance[] = new int[N * N];
    public double DivisorsChrominance[] = new double[N * N];

    /**
     * Constructs a new pmundur.DCTRTP object. Initializes the cosine transform matrix
     * these are used when computing the pmundur.DCTRTP and it's inverse. This also
     * initializes the run length counters and the ZigZag sequence. Note that
     * the image quality can be worse than 25 however the image will be
     * extemely pixelated, usually to a block size of N.
     *
     * @param QUALITY The quality of the image (0 worst - 100 best)
     */
    public DCTRTP(int QUALITY) {
        initMatrix(QUALITY);
    }


    /*
     * This method sets up the quantization matrix for luminance and
     * chrominance using the Quality parameter.
     */
    private void initMatrix(int quality) {
        double[] AANscaleFactor = {1.0, 1.387039845, 1.306562965, 1.175875602,
                1.0, 0.785694958, 0.541196100, 0.275899379};
        int i;
        int j;
        int index;
        int Quality;
        int temp;

// converting quality setting to that specified in the jpeg_quality_scaling
// method in the IJG Jpeg-6a C libraries

        Quality = quality;
        if (Quality <= 0)
            Quality = 1;
        if (Quality > 100)
            Quality = 100;
        if (Quality < 50)
            Quality = 5000 / Quality;
        else
            Quality = 200 - Quality * 2;

// Creating the luminance matrix

        quantum_luminance[0] = 16;
        quantum_luminance[1] = 11;
        quantum_luminance[2] = 10;
        quantum_luminance[3] = 16;
        quantum_luminance[4] = 24;
        quantum_luminance[5] = 40;
        quantum_luminance[6] = 51;
        quantum_luminance[7] = 61;
        quantum_luminance[8] = 12;
        quantum_luminance[9] = 12;
        quantum_luminance[10] = 14;
        quantum_luminance[11] = 19;
        quantum_luminance[12] = 26;
        quantum_luminance[13] = 58;
        quantum_luminance[14] = 60;
        quantum_luminance[15] = 55;
        quantum_luminance[16] = 14;
        quantum_luminance[17] = 13;
        quantum_luminance[18] = 16;
        quantum_luminance[19] = 24;
        quantum_luminance[20] = 40;
        quantum_luminance[21] = 57;
        quantum_luminance[22] = 69;
        quantum_luminance[23] = 56;
        quantum_luminance[24] = 14;
        quantum_luminance[25] = 17;
        quantum_luminance[26] = 22;
        quantum_luminance[27] = 29;
        quantum_luminance[28] = 51;
        quantum_luminance[29] = 87;
        quantum_luminance[30] = 80;
        quantum_luminance[31] = 62;
        quantum_luminance[32] = 18;
        quantum_luminance[33] = 22;
        quantum_luminance[34] = 37;
        quantum_luminance[35] = 56;
        quantum_luminance[36] = 68;
        quantum_luminance[37] = 109;
        quantum_luminance[38] = 103;
        quantum_luminance[39] = 77;
        quantum_luminance[40] = 24;
        quantum_luminance[41] = 35;
        quantum_luminance[42] = 55;
        quantum_luminance[43] = 64;
        quantum_luminance[44] = 81;
        quantum_luminance[45] = 104;
        quantum_luminance[46] = 113;
        quantum_luminance[47] = 92;
        quantum_luminance[48] = 49;
        quantum_luminance[49] = 64;
        quantum_luminance[50] = 78;
        quantum_luminance[51] = 87;
        quantum_luminance[52] = 103;
        quantum_luminance[53] = 121;
        quantum_luminance[54] = 120;
        quantum_luminance[55] = 101;
        quantum_luminance[56] = 72;
        quantum_luminance[57] = 92;
        quantum_luminance[58] = 95;
        quantum_luminance[59] = 98;
        quantum_luminance[60] = 112;
        quantum_luminance[61] = 100;
        quantum_luminance[62] = 103;
        quantum_luminance[63] = 99;

        for (j = 0; j < 64; j++) {
            temp = (quantum_luminance[j] * Quality + 50) / 100;
            if (temp <= 0) temp = 1;
            if (temp > 255) temp = 255;
            quantum_luminance[j] = temp;
        }
        index = 0;
        for (i = 0; i < 8; i++) {
            for (j = 0; j < 8; j++) {
// The divisors for the LL&M method (the slow integer method used in
// jpeg 6a library).  This method is currently (04/04/98) incompletely
// implemented.
//                        DivisorsLuminance[index] = ((double) quantum_luminance[index]) << 3;
// The divisors for the AAN method (the float method used in jpeg 6a library.
                DivisorsLuminance[index] = 1d / (8d * quantum_luminance[index] * AANscaleFactor[i] * AANscaleFactor[j]);
                index++;
            }
        }


// Creating the chrominance matrix

        quantum_chrominance[0] = 17;
        quantum_chrominance[1] = 18;
        quantum_chrominance[2] = 24;
        quantum_chrominance[3] = 47;
        quantum_chrominance[4] = 99;
        quantum_chrominance[5] = 99;
        quantum_chrominance[6] = 99;
        quantum_chrominance[7] = 99;
        quantum_chrominance[8] = 18;
        quantum_chrominance[9] = 21;
        quantum_chrominance[10] = 26;
        quantum_chrominance[11] = 66;
        quantum_chrominance[12] = 99;
        quantum_chrominance[13] = 99;
        quantum_chrominance[14] = 99;
        quantum_chrominance[15] = 99;
        quantum_chrominance[16] = 24;
        quantum_chrominance[17] = 26;
        quantum_chrominance[18] = 56;
        quantum_chrominance[19] = 99;
        quantum_chrominance[20] = 99;
        quantum_chrominance[21] = 99;
        quantum_chrominance[22] = 99;
        quantum_chrominance[23] = 99;
        quantum_chrominance[24] = 47;
        quantum_chrominance[25] = 66;
        quantum_chrominance[26] = 99;
        quantum_chrominance[27] = 99;
        quantum_chrominance[28] = 99;
        quantum_chrominance[29] = 99;
        quantum_chrominance[30] = 99;
        quantum_chrominance[31] = 99;
        quantum_chrominance[32] = 99;
        quantum_chrominance[33] = 99;
        quantum_chrominance[34] = 99;
        quantum_chrominance[35] = 99;
        quantum_chrominance[36] = 99;
        quantum_chrominance[37] = 99;
        quantum_chrominance[38] = 99;
        quantum_chrominance[39] = 99;
        quantum_chrominance[40] = 99;
        quantum_chrominance[41] = 99;
        quantum_chrominance[42] = 99;
        quantum_chrominance[43] = 99;
        quantum_chrominance[44] = 99;
        quantum_chrominance[45] = 99;
        quantum_chrominance[46] = 99;
        quantum_chrominance[47] = 99;
        quantum_chrominance[48] = 99;
        quantum_chrominance[49] = 99;
        quantum_chrominance[50] = 99;
        quantum_chrominance[51] = 99;
        quantum_chrominance[52] = 99;
        quantum_chrominance[53] = 99;
        quantum_chrominance[54] = 99;
        quantum_chrominance[55] = 99;
        quantum_chrominance[56] = 99;
        quantum_chrominance[57] = 99;
        quantum_chrominance[58] = 99;
        quantum_chrominance[59] = 99;
        quantum_chrominance[60] = 99;
        quantum_chrominance[61] = 99;
        quantum_chrominance[62] = 99;
        quantum_chrominance[63] = 99;

        for (j = 0; j < 64; j++) {
            temp = (quantum_chrominance[j] * Quality + 50) / 100;
            if (temp <= 0) temp = 1;
            if (temp >= 255) temp = 255;
            quantum_chrominance[j] = temp;
        }
        index = 0;
        for (i = 0; i < 8; i++) {
            for (j = 0; j < 8; j++) {
// The divisors for the LL&M method (the slow integer method used in
// jpeg 6a library).  This method is currently (04/04/98) incompletely
// implemented.
//                        DivisorsChrominance[index] = ((double) quantum_chrominance[index]) << 3;
// The divisors for the AAN method (the float method used in jpeg 6a library.
                DivisorsChrominance[index] = 1d / (8d * quantum_chrominance[index] * AANscaleFactor[i] * AANscaleFactor[j]);
                index++;
            }
        }

// quantum and Divisors are objects used to hold the appropriate matices

        quantum[0] = quantum_luminance;
        Divisors[0] = DivisorsLuminance;
        quantum[1] = quantum_chrominance;
        Divisors[1] = DivisorsChrominance;


    }

    /*
     * This method preforms forward pmundur.DCTRTP on a block of image data using
     * the literal method specified for a 2-D Discrete Cosine Transform.
     * It is included as a curiosity and can give you an idea of the
     * difference in the compression result (the resulting image quality)
     * by comparing its output to the output of the AAN method below.
     * It is ridiculously inefficient.
     */

// For now the final output is unusable.  The associated quantization step
// needs some tweaking.  If you get this part working, please let me know.

    public double[][] forwardDCTExtreme(float input[][]) {
        double output[][] = new double[N][N];
        //double tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7;
        //double tmp10, tmp11, tmp12, tmp13;
        //double z1, z2, z3, z4, z5, z11, z13;
        //int i;
        //int j;
        int v, u, x, y;
        for (v = 0; v < 8; v++) {
            for (u = 0; u < 8; u++) {
                for (x = 0; x < 8; x++) {
                    for (y = 0; y < 8; y++) {
                        output[v][u] += input[x][y] * Math.cos(((2 * x + 1) * (double) u * Math.PI) / 16d) * Math.cos(((2 * y + 1) * (double) v * Math.PI) / 16d);
                    }
                }
                output[v][u] *= 0.25d * ((u == 0) ? (1d / Math.sqrt(2)) : 1d) * ((v == 0) ? (1d / Math.sqrt(2)) : 1d);
            }
        }
        return output;
    }


    /*
     * This method preforms a pmundur.DCTRTP on a block of image data using the AAN
     * method as implemented in the IJG Jpeg-6a library.
     */
    public double[][] forwardDCT(float input[][]) {
        double output[][] = new double[N][N];
        double tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7;
        double tmp10, tmp11, tmp12, tmp13;
        double z1, z2, z3, z4, z5, z11, z13;
        int i;
        int j;

// Subtracts 128 from the input values
        for (i = 0; i < 8; i++) {
            for (j = 0; j < 8; j++) {
                output[i][j] = input[i][j] - 128d;
//                        input[i][j] -= 128;

            }
        }

        for (i = 0; i < 8; i++) {
            tmp0 = output[i][0] + output[i][7];
            tmp7 = output[i][0] - output[i][7];
            tmp1 = output[i][1] + output[i][6];
            tmp6 = output[i][1] - output[i][6];
            tmp2 = output[i][2] + output[i][5];
            tmp5 = output[i][2] - output[i][5];
            tmp3 = output[i][3] + output[i][4];
            tmp4 = output[i][3] - output[i][4];

            tmp10 = tmp0 + tmp3;
            tmp13 = tmp0 - tmp3;
            tmp11 = tmp1 + tmp2;
            tmp12 = tmp1 - tmp2;

            output[i][0] = tmp10 + tmp11;
            output[i][4] = tmp10 - tmp11;

            z1 = (tmp12 + tmp13) * 0.707106781d;
            output[i][2] = tmp13 + z1;
            output[i][6] = tmp13 - z1;

            tmp10 = tmp4 + tmp5;
            tmp11 = tmp5 + tmp6;
            tmp12 = tmp6 + tmp7;

            z5 = (tmp10 - tmp12) * 0.382683433d;
            z2 = 0.541196100d * tmp10 + z5;
            z4 = 1.306562965d * tmp12 + z5;
            z3 = tmp11 * 0.707106781d;

            z11 = tmp7 + z3;
            z13 = tmp7 - z3;

            output[i][5] = z13 + z2;
            output[i][3] = z13 - z2;
            output[i][1] = z11 + z4;
            output[i][7] = z11 - z4;
        }

        for (i = 0; i < 8; i++) {
            tmp0 = output[0][i] + output[7][i];
            tmp7 = output[0][i] - output[7][i];
            tmp1 = output[1][i] + output[6][i];
            tmp6 = output[1][i] - output[6][i];
            tmp2 = output[2][i] + output[5][i];
            tmp5 = output[2][i] - output[5][i];
            tmp3 = output[3][i] + output[4][i];
            tmp4 = output[3][i] - output[4][i];

            tmp10 = tmp0 + tmp3;
            tmp13 = tmp0 - tmp3;
            tmp11 = tmp1 + tmp2;
            tmp12 = tmp1 - tmp2;

            output[0][i] = tmp10 + tmp11;
            output[4][i] = tmp10 - tmp11;

            z1 = (tmp12 + tmp13) * 0.707106781d;
            output[2][i] = tmp13 + z1;
            output[6][i] = tmp13 - z1;

            tmp10 = tmp4 + tmp5;
            tmp11 = tmp5 + tmp6;
            tmp12 = tmp6 + tmp7;

            z5 = (tmp10 - tmp12) * 0.382683433d;
            z2 = 0.541196100d * tmp10 + z5;
            z4 = 1.306562965d * tmp12 + z5;
            z3 = tmp11 * 0.707106781d;

            z11 = tmp7 + z3;
            z13 = tmp7 - z3;

            output[5][i] = z13 + z2;
            output[3][i] = z13 - z2;
            output[1][i] = z11 + z4;
            output[7][i] = z11 - z4;
        }

        return output;
    }

    /*
    * This method quantitizes data and rounds it to the nearest integer.
    */
    public int[] quantizeBlock(double inputData[][], int code) {
        int outputData[] = new int[N * N];
        int i, j;
        int index;
        index = 0;
        for (i = 0; i < 8; i++) {
            for (j = 0; j < 8; j++) {
// The second line results in significantly better compression.
                outputData[index] = (int) (Math.round(inputData[i][j] * Divisors[code][index]));
//                        outputData[index] = (int)(((inputData[i][j] * (((double[]) (Divisors[code]))[index])) + 16384.5) -16384);
                index++;
            }
        }

        return outputData;
    }

    /*
    * This is the method for quantizing a block pmundur.DCTRTP'ed with forwardDCTExtreme
    * This method quantitizes data and rounds it to the nearest integer.
    */
    public int[] quantizeBlockExtreme(double inputData[][], int code) {
        int outputData[] = new int[N * N];
        int i, j;
        int index;
        index = 0;
        for (i = 0; i < 8; i++) {
            for (j = 0; j < 8; j++) {
                outputData[index] = (int) (Math.round(inputData[i][j] / quantum[code][index]));
                index++;
            }
        }

        return outputData;
    }
}

// This class was modified by James R. Weeks on 3/27/98.
// It now incorporates pmundur.HuffmanRTP table derivation as in the C jpeg library
// from the IJG, Jpeg-6a.

class HuffmanRTP {
    int bufferPutBits, bufferPutBuffer;
    public int ImageHeight;
    public int ImageWidth;
    public int DC_matrix0[][];
    public int AC_matrix0[][];
    public int DC_matrix1[][];
    public int AC_matrix1[][];
    public int DC_matrix[][][];
    public int AC_matrix[][][];
    public int code;
    public int NumOfDCTables;
    public int NumOfACTables;
    public int[] bitsDCluminance = {0x00, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0};
    public int[] valDCluminance = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public int[] bitsDCchrominance = {0x01, 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0};
    public int[] valDCchrominance = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public int[] bitsACluminance = {0x10, 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d};
    public int[] valACluminance =
            {0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
                    0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
                    0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
                    0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
                    0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16,
                    0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
                    0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
                    0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
                    0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
                    0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
                    0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
                    0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
                    0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
                    0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
                    0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
                    0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
                    0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4,
                    0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
                    0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea,
                    0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
                    0xf9, 0xfa};
    public int[] bitsACchrominance = {0x11, 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77};
    public int[] valACchrominance =
            {0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
                    0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
                    0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
                    0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
                    0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34,
                    0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
                    0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
                    0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
                    0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
                    0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                    0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
                    0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
                    0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96,
                    0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
                    0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
                    0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
                    0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2,
                    0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
                    0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9,
                    0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
                    0xf9, 0xfa};
    public Vector bits;
    public Vector val;

    /*
     * jpegNaturalOrder[i] is the natural-order position of the i'th element
     * of zigzag order.
     */
    public static int[] jpegNaturalOrder = {
            0, 1, 8, 16, 9, 2, 3, 10,
            17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34,
            27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36,
            29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46,
            53, 60, 61, 54, 47, 55, 62, 63,
    };

    /*
    * The pmundur.HuffmanRTP class constructor
    */
    public HuffmanRTP(int Width, int Height) {

        bits = new Vector();
        bits.addElement(bitsDCluminance);
        bits.addElement(bitsACluminance);
        bits.addElement(bitsDCchrominance);
        bits.addElement(bitsACchrominance);
        val = new Vector();
        val.addElement(valDCluminance);
        val.addElement(valACluminance);
        val.addElement(valDCchrominance);
        val.addElement(valACchrominance);
        initHuf();
        //code=code;
        ImageWidth = Width;
        ImageHeight = Height;

    }

    /**
     * HuffmanBlockEncoder run length encodes and pmundur.HuffmanRTP encodes the quantized
     * data.
     */

    public void HuffmanBlockEncoder(OutputStream outStream, int zigzag[], int prec, int DCcode, int ACcode) {
        int temp, temp2, nbits, k, r, i;

        NumOfDCTables = 2;
        NumOfACTables = 2;

// The DC portion

        temp = temp2 = zigzag[0] - prec;
        if (temp < 0) {
            temp = -temp;
            temp2--;
        }
        nbits = 0;
        while (temp != 0) {
            nbits++;
            temp >>= 1;
        }
//        if (nbits > 11) nbits = 11;
        bufferIt(outStream, DC_matrix[DCcode][nbits][0], DC_matrix[DCcode][nbits][1]);
        // The arguments in bufferIt are code and size.
        if (nbits != 0) {
            bufferIt(outStream, temp2, nbits);
        }

// The AC portion

        r = 0;

        for (k = 1; k < 64; k++) {
            if ((temp = zigzag[jpegNaturalOrder[k]]) == 0) {
                r++;
            } else {
                while (r > 15) {
                    bufferIt(outStream, AC_matrix[ACcode][0xF0][0], AC_matrix[ACcode][0xF0][1]);
                    r -= 16;
                }
                temp2 = temp;
                if (temp < 0) {
                    temp = -temp;
                    temp2--;
                }
                nbits = 1;
                while ((temp >>= 1) != 0) {
                    nbits++;
                }
                i = (r << 4) + nbits;
                bufferIt(outStream, AC_matrix[ACcode][i][0], AC_matrix[ACcode][i][1]);
                bufferIt(outStream, temp2, nbits);

                r = 0;
            }
        }

        if (r > 0) {
            bufferIt(outStream, AC_matrix[ACcode][0][0], AC_matrix[ACcode][0][1]);
        }

    }

// Uses an integer long (32 bits) buffer to store the pmundur.HuffmanRTP encoded bits
// and sends them to outStream by the byte.

    void bufferIt(OutputStream outStream, int code, int size) {
        int PutBuffer = code;
        int PutBits = bufferPutBits;

        PutBuffer &= (1 << size) - 1;
        PutBits += size;
        PutBuffer <<= 24 - PutBits;
        PutBuffer |= bufferPutBuffer;

        while (PutBits >= 8) {
            int c = ((PutBuffer >> 16) & 0xFF);
            try {
                outStream.write(c);
            } catch (IOException e) {
                //TODO
                System.out.println("IO Error: " + e.getMessage());
            }
            if (c == 0xFF) {
                try {
                    outStream.write(0);
                } catch (IOException e) {
                    //TODO
                    System.out.println("IO Error: " + e.getMessage());
                }
            }
            PutBuffer <<= 8;
            PutBits -= 8;
        }
        bufferPutBuffer = PutBuffer;
        bufferPutBits = PutBits;

    }

    void flushBuffer(OutputStream outStream) {
        int PutBuffer = bufferPutBuffer;
        int PutBits = bufferPutBits;
        while (PutBits >= 8) {
            int c = ((PutBuffer >> 16) & 0xFF);
            try {
                outStream.write(c);
            } catch (IOException e) {
                //TODO
                System.out.println("IO Error: " + e.getMessage());
            }
            if (c == 0xFF) {
                try {
                    outStream.write(0);
                } catch (IOException e) {
                    //TODO
                    System.out.println("IO Error: " + e.getMessage());
                }
            }
            PutBuffer <<= 8;
            PutBits -= 8;
        }
        if (PutBits > 0) {
            int c = ((PutBuffer >> 16) & 0xFF);
            try {
                outStream.write(c);
            } catch (IOException e) {
                //TODO
                System.out.println("IO Error: " + e.getMessage());
            }
        }
    }

    /*
    * Initialisation of the pmundur.HuffmanRTP codes for Luminance and Chrominance.
    * This code results in the same tables created in the IJG Jpeg-6a
    * library.
    */

    public void initHuf() {
        DC_matrix0 = new int[12][2];
        DC_matrix1 = new int[12][2];
        AC_matrix0 = new int[255][2];
        AC_matrix1 = new int[255][2];
        DC_matrix = new int[2][][];
        AC_matrix = new int[2][][];
        int p, l, i, lastp, si, code;
        int[] huffsize = new int[257];
        int[] huffcode = new int[257];

        /*
        * init of the DC values for the chrominance
        * [][0] is the code   [][1] is the number of bit
        */

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsDCchrominance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            DC_matrix1[valDCchrominance[p]][0] = huffcode[p];
            DC_matrix1[valDCchrominance[p]][1] = huffsize[p];
        }

        /*
        * Init of the AC hufmann code for the chrominance
        * matrix [][][0] is the code & matrix[][][1] is the number of bit needed
        */

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsACchrominance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            AC_matrix1[valACchrominance[p]][0] = huffcode[p];
            AC_matrix1[valACchrominance[p]][1] = huffsize[p];
        }

        /*
        * init of the DC values for the luminance
        * [][0] is the code   [][1] is the number of bit
        */
        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsDCluminance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            DC_matrix0[valDCluminance[p]][0] = huffcode[p];
            DC_matrix0[valDCluminance[p]][1] = huffsize[p];
        }

        /*
        * Init of the AC hufmann code for luminance
        * matrix [][][0] is the code & matrix[][][1] is the number of bit
        */

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsACluminance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }
        for (int q = 0; q < lastp; q++) {
            AC_matrix0[valACluminance[q]][0] = huffcode[q];
            AC_matrix0[valACluminance[q]][1] = huffsize[q];
        }

        DC_matrix[0] = DC_matrix0;
        DC_matrix[1] = DC_matrix1;
        AC_matrix[0] = AC_matrix0;
        AC_matrix[1] = AC_matrix1;
    }

}

/*
 * pmundur.JpegInfoRTP - Given an image, sets default information about it and divides
 * it into its constituant components, downsizing those that need to be.
 */

class JpegInfoRTP {
    String Comment;
    public Image imageobj;
    public int imageHeight;
    public int imageWidth;
    public int BlockWidth[];
    public int BlockHeight[];

    // the following are set as the default
    public int Precision = 8;
    public int NumberOfComponents = 3;
    //public int NumberOfComponents = 1;

    public float Components[][][];
    public int[] CompID = {1, 2, 3};
    public int[] HsampFactor = {1, 1, 1};
    public int[] VsampFactor = {1, 1, 1};
    public int[] QtableNumber = {0, 1, 1};
    public int[] DCtableNumber = {0, 1, 1};
    public int[] ACtableNumber = {0, 1, 1};
    public boolean[] lastColumnIsDummy = {false, false, false};
    public boolean[] lastRowIsDummy = {false, false, false};
    public int Ss = 0;
    public int Se = 63;
    public int Ah = 0;
    public int Al = 0;
    public int compWidth[], compHeight[];
    public int MaxHsampFactor;
    public int MaxVsampFactor;


    public JpegInfoRTP(Image image) {
        Components = new float[NumberOfComponents][][];
        compWidth = new int[NumberOfComponents];
        compHeight = new int[NumberOfComponents];
        BlockWidth = new int[NumberOfComponents];
        BlockHeight = new int[NumberOfComponents];
        imageobj = image;
        imageWidth = image.getWidth(null);
        System.out.println("Image width is " + imageWidth);
        imageHeight = image.getHeight(null);
        System.out.println("Image height is " + imageHeight);
        Comment = "JPEG Encoder Copyright 1998, James R. Weeks and BioElectroMech.  ";
        getYCCArray();
    }

    public void setComment(String comment) {
        Comment.concat(comment);
    }

    public String getComment() {
        return Comment;
    }

    /*
     * This method creates and fills three arrays, Y, Cb, and Cr using the
     * input image.
     */

    private void getYCCArray() {
        int values[] = new int[imageWidth * imageHeight];
        int r, g, b, y, x;
// In order to minimize the chance that grabPixels will throw an exception
// it may be necessary to grab some pixels every few scanlines and process
// those before going for more.  The time expense may be prohibitive.
// However, for a situation where memory overhead is a concern, this may be
// the only choice.
        PixelGrabber grabber = new PixelGrabber(imageobj.getSource(), 0, 0, imageWidth, imageHeight, values, 0, imageWidth);
        MaxHsampFactor = 1;
        MaxVsampFactor = 1;
        for (y = 0; y < NumberOfComponents; y++) {
            MaxHsampFactor = Math.max(MaxHsampFactor, HsampFactor[y]);
            MaxVsampFactor = Math.max(MaxVsampFactor, VsampFactor[y]);
        }
        for (y = 0; y < NumberOfComponents; y++) {
            compWidth[y] = (((imageWidth % 8 != 0) ? ((int) Math.ceil(imageWidth / 8d)) * 8 : imageWidth) / MaxHsampFactor) * HsampFactor[y];
            if (compWidth[y] != ((imageWidth / MaxHsampFactor) * HsampFactor[y])) {
                lastColumnIsDummy[y] = true;
            }
            // results in a multiple of 8 for compWidth
            // this will make the rest of the program fail for the unlikely
            // event that someone tries to compress an 16 x 16 pixel image
            // which would of course be worse than pointless
            BlockWidth[y] = (int) Math.ceil(compWidth[y] / 8d);
            compHeight[y] = (((imageHeight % 8 != 0) ? ((int) Math.ceil(imageHeight / 8d)) * 8 : imageHeight) / MaxVsampFactor) * VsampFactor[y];
            if (compHeight[y] != ((imageHeight / MaxVsampFactor) * VsampFactor[y])) {
                lastRowIsDummy[y] = true;
            }
            BlockHeight[y] = (int) Math.ceil(compHeight[y] / 8d);
        }
        try {
            if (grabber.grabPixels() != true) {
                try {
                    throw new AWTException("Grabber returned false: " + grabber.status());
                } catch (Exception e) {
                    //TODO
                }
            }
        } catch (InterruptedException e) {
            //TODO
        }
        float Y[][] = new float[compHeight[0]][compWidth[0]];
        float Cr1[][] = new float[compHeight[0]][compWidth[0]];
        float Cb1[][] = new float[compHeight[0]][compWidth[0]];
        //float Cb2[][] = new float[compHeight[1]][compWidth[1]];
        //float Cr2[][] = new float[compHeight[2]][compWidth[2]];
        int index = 0;
        for (y = 0; y < imageHeight; ++y) {
            for (x = 0; x < imageWidth; ++x) {
                r = ((values[index] >> 16) & 0xff);
                g = ((values[index] >> 8) & 0xff);
                b = (values[index] & 0xff);

// The following three lines are a more correct color conversion but
// the current conversion technique is sufficient and results in a higher
// compression rate.
//                Y[y][x] = 16 + (float)(0.8588*(0.299 * (float)r + 0.587 * (float)g + 0.114 * (float)b ));
//                Cb1[y][x] = 128 + (float)(0.8784*(-0.16874 * (float)r - 0.33126 * (float)g + 0.5 * (float)b));
//                Cr1[y][x] = 128 + (float)(0.8784*(0.5 * (float)r - 0.41869 * (float)g - 0.08131 * (float)b));
                Y[y][x] = (float) (0.299 * r + 0.587 * g + 0.114 * b);
                Cb1[y][x] = 128 + (float) (-0.16874 * r - 0.33126 * g + 0.5 * b);
                Cr1[y][x] = 128 + (float) (0.5 * r - 0.41869 * g - 0.08131 * b);
                index++;
            }
        }

// Need a way to set the H and V sample factors before allowing downsampling.
// For now (04/04/98) downsampling must be hard coded.
// Until a better downsampler is implemented, this will not be done.
// Downsampling is currently supported.  The downsampling method here
// is a simple box filter.

        Components[0] = Y;
//        Cb2 = DownSample(Cb1, 1);
        Components[1] = Cb1;
//        Cr2 = DownSample(Cr1, 2);
        Components[2] = Cr1;
    }

    float[][] DownSample(float[][] C, int comp) {
        int inrow, incol;
        int outrow, outcol;
        float output[][];
        //int temp;
        int bias;
        inrow = 0;
        incol = 0;
        output = new float[compHeight[comp]][compWidth[comp]];
        for (outrow = 0; outrow < compHeight[comp]; outrow++) {
            bias = 1;
            for (outcol = 0; outcol < compWidth[comp]; outcol++) {
                output[outrow][outcol] = (C[inrow][incol++] + C[inrow++][incol--] + C[inrow][incol++] + C[inrow--][incol++] + bias) / 4f;
                bias ^= 3;
            }
            inrow += 2;
            incol = 0;
        }
        return output;
    }
}
