/*
 * Dataverse Network - A web application to distribute, share and analyze quantitative data.
 * Copyright (C) 2009
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 *  along with this program; if not, see http://www.gnu.org/licenses
 * or write to the Free Software Foundation,Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package edu.harvard.iq.dvn.ingest.statdataio.impl.plugins.por;

import edu.harvard.iq.dvn.ingest.org.thedata.statdataio.*;
import edu.harvard.iq.dvn.ingest.org.thedata.statdataio.spi.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.logging.*;
import java.util.Locale;

import static java.lang.System.*;

import org.apache.commons.codec.binary.Hex;

/**
 *
 * @author Akio Sone
 */

public class PORFileReaderSpi extends StatDataFileReaderSpi{

    private static Logger dbgLog = Logger.getLogger(PORFileReaderSpi.class.getPackage().getName());
    private static int POR_HEADER_SIZE = 500;
    
    /**
     * 
     */
    public static int POR_MARK_POSITION_DEFAULT = 461;
    /**
     *
     */
    public static String POR_MARK = "SPSSPORT";
    
    private boolean windowsNewLine = true;
    
    
    private static String[] formatNames = {"por", "POR"};
    private static String[] extensions = {"por"};
    private static String[] mimeType = {"application/x-spss-por"};

    
    /**
     *
     */
    public PORFileReaderSpi(){
       super("HU-IQSS-DVN-project",
            "0.1",
            formatNames,
            extensions,
            mimeType,
            PORFileReaderSpi.class.getName());
       dbgLog.fine(PORFileReaderSpi.class.getName()+"is called");
    }

    /**
     * Returns the value of the description of the corresponding
     * PORFileReader class.
     *
     * @param locale
     * @return the value of the description of the corresponding
     * PORFileReader class
     */
    public String getDescription(Locale locale) {
        return "HU-IQSS-DVN-project SPSS Portable File Reader";
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof BufferedInputStream)) {
            return false;
        }
        if (source  == null){
            throw new IllegalArgumentException("source == null!");
        }
        BufferedInputStream stream = (BufferedInputStream)source;
        dbgLog.fine("applying the por test\n");

        byte[] b = new byte[POR_HEADER_SIZE];

        if (stream.markSupported()){
            stream.mark(0);
        }

        int nbytes = stream.read(b, 0, POR_HEADER_SIZE);

        //printHexDump(b, "hex dump of the byte-array");

        if (nbytes == 0){
            throw new IOException();
        } else if ( nbytes < 491) {
           // size test
           dbgLog.fine("this file is NOT spss-por type");
            return false;
        }

        if (stream.markSupported()){
            stream.reset();
        }

        boolean DEBUG = false;

        //windows [0D0A]=>   [1310] = [CR/LF]
        //unix    [0A]  =>   [10]
        //mac     [0D]  =>   [13]
        // 3char  [0D0D0A]=> [131310] spss for windows rel 15
        // expected results
        // unix    case: [0A]   : [80], [161], [242], [323], [404], [485]
        // windows case: [0D0A] : [81], [163], [245], [327], [409], [491]
        //  : [0D0D0A] : [82], [165], [248], [331], [414], [495]

        // convert b into a ByteBuffer

        ByteBuffer buff = ByteBuffer.wrap(b);
        byte[] nlch = new byte[36];
        int pos1;
        int pos2;
        int pos3;
        int ucase = 0;
        int wcase = 0;
        int mcase = 0;
        int three = 0;
        int nolines = 6;
        int nocols = 80;
        for (int i = 0; i < nolines; ++i) {
            int baseBias = nocols * (i + 1);
            // 1-char case
            pos1 = baseBias + i;
            buff.position(pos1);
            dbgLog.finer("\tposition(1)=" + buff.position());
            int j = 6 * i;
            nlch[j] = buff.get();

            if (nlch[j] == 10) {
                ucase++;
            } else if (nlch[j] == 13) {
                mcase++;
            }

            // 2-char case
            pos2 = baseBias + 2 * i;
            buff.position(pos2);
            dbgLog.finer("\tposition(2)=" + buff.position());

            nlch[j + 1] = buff.get();
            nlch[j + 2] = buff.get();

            // 3-char case
            pos3 = baseBias + 3 * i;
            buff.position(pos3);
            dbgLog.finer("\tposition(3)=" + buff.position());

            nlch[j + 3] = buff.get();
            nlch[j + 4] = buff.get();
            nlch[j + 5] = buff.get();

            dbgLog.finer(i + "-th iteration position =" + nlch[j] + "\t" + nlch[j + 1] + "\t" + nlch[j + 2]);
            dbgLog.finer(i + "-th iteration position =" + nlch[j + 3] + "\t" + nlch[j + 4] + "\t" + nlch[j + 5]);

            if ((nlch[j + 3] == 13) && (nlch[j + 4] == 13) && (nlch[j + 5] == 10)) {
                three++;
            } else if ((nlch[j + 1] == 13) && (nlch[j + 2] == 10)) {
                wcase++;
            }

            buff.rewind();
        }
        if (three == nolines) {
            dbgLog.fine("0D0D0A case");
            windowsNewLine = false;
        } else if ((ucase == nolines) && (wcase < nolines)) {
            dbgLog.fine("0A case");
            windowsNewLine = false;
        } else if ((ucase < nolines) && (wcase == nolines)) {
            dbgLog.fine("0D0A case");
        } else if ((mcase == nolines) && (wcase < nolines)) {
            dbgLog.fine("0D case");
            windowsNewLine = false;
        }


        buff.rewind();
        int PORmarkPosition = POR_MARK_POSITION_DEFAULT;
        if (windowsNewLine) {
            PORmarkPosition = PORmarkPosition + 5;
        } else if (three == nolines) {
            PORmarkPosition = PORmarkPosition + 10;
        }

        byte[] pormark = new byte[8];
        buff.position(PORmarkPosition);
        buff.get(pormark, 0, 8);
        String pormarks = new String(pormark);

        dbgLog.fine("pormark[hex: 53 50 53 53 50 4F 52 54 == SPSSPORT] =>" +
                new String(Hex.encodeHex(pormark)) + "<-");

        if (pormarks.equals(POR_MARK)) {
            dbgLog.fine("this file is spss-por type");
            return true;
        } else {
            dbgLog.fine("this file is NOT spss-por type");
        }
        return false;
    }



    @Override
    public boolean canDecodeInput(BufferedInputStream stream) throws IOException {
        if (stream  == null){
            throw new IllegalArgumentException("file == null!");
        }
        
        dbgLog.fine("applying the por test\n");
        
        byte[] b = new byte[POR_HEADER_SIZE];
        
        if (stream.markSupported()){
            stream.mark(0);
        }
        
        int nbytes = stream.read(b, 0, POR_HEADER_SIZE);
        
        //printHexDump(b, "hex dump of the byte-array");

        if (nbytes == 0){
            throw new IOException();
        } else if ( nbytes < 491) {
           // size test
           dbgLog.fine("this file is NOT spss-por type");
            return false;
        }
        
        if (stream.markSupported()){
            stream.reset();
        }

        boolean DEBUG = false;
        
        //windows [0D0A]=>   [1310] = [CR/LF]
        //unix    [0A]  =>   [10]
        //mac     [0D]  =>   [13]
        // 3char  [0D0D0A]=> [131310] spss for windows rel 15
        // expected results
        // unix    case: [0A]   : [80], [161], [242], [323], [404], [485]
        // windows case: [0D0A] : [81], [163], [245], [327], [409], [491]
        //  : [0D0D0A] : [82], [165], [248], [331], [414], [495]
        
        // convert b into a ByteBuffer
        
        ByteBuffer buff = ByteBuffer.wrap(b);
        byte[] nlch = new byte[36];
        int pos1;
        int pos2;
        int pos3;
        int ucase = 0;
        int wcase = 0;
        int mcase = 0;
        int three = 0;
        int nolines = 6;
        int nocols = 80;
        for (int i = 0; i < nolines; ++i) {
            int baseBias = nocols * (i + 1);
            // 1-char case
            pos1 = baseBias + i;
            buff.position(pos1);
            dbgLog.finer("\tposition(1)=" + buff.position());
            int j = 6 * i;
            nlch[j] = buff.get();

            if (nlch[j] == 10) {
                ucase++;
            } else if (nlch[j] == 13) {
                mcase++;
            }

            // 2-char case
            pos2 = baseBias + 2 * i;
            buff.position(pos2);
            dbgLog.finer("\tposition(2)=" + buff.position());
            
            nlch[j + 1] = buff.get();
            nlch[j + 2] = buff.get();

            // 3-char case
            pos3 = baseBias + 3 * i;
            buff.position(pos3);
            dbgLog.finer("\tposition(3)=" + buff.position());
            
            nlch[j + 3] = buff.get();
            nlch[j + 4] = buff.get();
            nlch[j + 5] = buff.get();

            dbgLog.finer(i + "-th iteration position =" + nlch[j] + "\t" + nlch[j + 1] + "\t" + nlch[j + 2]);
            dbgLog.finer(i + "-th iteration position =" + nlch[j + 3] + "\t" + nlch[j + 4] + "\t" + nlch[j + 5]);
            
            if ((nlch[j + 3] == 13) && (nlch[j + 4] == 13) && (nlch[j + 5] == 10)) {
                three++;
            } else if ((nlch[j + 1] == 13) && (nlch[j + 2] == 10)) {
                wcase++;
            }

            buff.rewind();
        }
        if (three == nolines) {
            dbgLog.fine("0D0D0A case");
            windowsNewLine = false;
        } else if ((ucase == nolines) && (wcase < nolines)) {
            dbgLog.fine("0A case");
            windowsNewLine = false;
        } else if ((ucase < nolines) && (wcase == nolines)) {
            dbgLog.fine("0D0A case");
        } else if ((mcase == nolines) && (wcase < nolines)) {
            dbgLog.fine("0D case");
            windowsNewLine = false;
        }


        buff.rewind();
        int PORmarkPosition = POR_MARK_POSITION_DEFAULT;
        if (windowsNewLine) {
            PORmarkPosition = PORmarkPosition + 5;
        } else if (three == nolines) {
            PORmarkPosition = PORmarkPosition + 10;
        }

        byte[] pormark = new byte[8];
        buff.position(PORmarkPosition);
        buff.get(pormark, 0, 8);
        String pormarks = new String(pormark);

        //dbgLog.fine("pormark =>" + pormarks + "<-");
        dbgLog.fine("pormark[hex: 53 50 53 53 50 4F 52 54 == SPSSPORT] =>" +
                new String(Hex.encodeHex(pormark)) + "<-");

        if (pormarks.equals(POR_MARK)) {
            dbgLog.fine("this file is spss-por type");
            return true;
        } else {
            dbgLog.fine("this file is NOT spss-por type");
        }
        return false;
    }


    @Override
    public boolean canDecodeInput(File file) throws IOException {
        if (file ==null){
            throw new IllegalArgumentException("file == null!");
        }
        if (!file.canRead()){
            throw new IOException("cannot read the input file");
        }

        // set-up a FileChannel instance for a given file object
        FileChannel srcChannel = new FileInputStream(file).getChannel();

        // create a read-only MappedByteBuffer
        MappedByteBuffer buff = srcChannel.map(FileChannel.MapMode.READ_ONLY, 0, POR_HEADER_SIZE);

        //printHexDump(buff, "hex dump of the byte-buffer");

        buff.rewind();

        boolean DEBUG = false;
        

        dbgLog.fine("applying the spss-por test\n");

        // size test
        if (buff.capacity() < 491) {
            dbgLog.fine("this file is NOT spss-por type");
            return false;
        }

        //windows [0D0A]=>   [1310] = [CR/LF]
        //unix    [0A]  =>   [10]
        //mac     [0D]  =>   [13]
        // 3char  [0D0D0A]=> [131310] spss for windows rel 15
        // expected results
        // unix    case: [0A]   : [80], [161], [242], [323], [404], [485]
        // windows case: [0D0A] : [81], [163], [245], [327], [409], [491]
        //  : [0D0D0A] : [82], [165], [248], [331], [414], [495]

        buff.rewind();
        byte[] nlch = new byte[36];
        int pos1;
        int pos2;
        int pos3;
        int ucase = 0;
        int wcase = 0;
        int mcase = 0;
        int three = 0;
        int nolines = 6;
        int nocols = 80;
        for (int i = 0; i < nolines; ++i) {
            int baseBias = nocols * (i + 1);
            // 1-char case
            pos1 = baseBias + i;
            buff.position(pos1);
            dbgLog.finer("\tposition(1)=" + buff.position());
            int j = 6 * i;
            nlch[j] = buff.get();

            if (nlch[j] == 10) {
                ucase++;
            } else if (nlch[j] == 13) {
                mcase++;
            }

            // 2-char case
            pos2 = baseBias + 2 * i;
            buff.position(pos2);
            dbgLog.finer("\tposition(2)=" + buff.position());
            
            nlch[j + 1] = buff.get();
            nlch[j + 2] = buff.get();

            // 3-char case
            pos3 = baseBias + 3 * i;
            buff.position(pos3);
            dbgLog.finer("\tposition(3)=" + buff.position());
            
            nlch[j + 3] = buff.get();
            nlch[j + 4] = buff.get();
            nlch[j + 5] = buff.get();

            dbgLog.finer(i + "-th iteration position =" + nlch[j] + "\t" + nlch[j + 1] + "\t" + nlch[j + 2]);
            dbgLog.finer(i + "-th iteration position =" + nlch[j + 3] + "\t" + nlch[j + 4] + "\t" + nlch[j + 5]);
            
            if ((nlch[j + 3] == 13) && (nlch[j + 4] == 13) && (nlch[j + 5] == 10)) {
                three++;
            } else if ((nlch[j + 1] == 13) && (nlch[j + 2] == 10)) {
                wcase++;
            }

            buff.rewind();
        }
        if (three == nolines) {
            dbgLog.fine("0D0D0A case");
            windowsNewLine = false;
        } else if ((ucase == nolines) && (wcase < nolines)) {
            dbgLog.fine("0A case");
            windowsNewLine = false;
        } else if ((ucase < nolines) && (wcase == nolines)) {
            dbgLog.fine("0D0A case");
        } else if ((mcase == nolines) && (wcase < nolines)) {
            dbgLog.fine("0D case");
            windowsNewLine = false;
        }


        buff.rewind();
        int PORmarkPosition = POR_MARK_POSITION_DEFAULT;
        if (windowsNewLine) {
            PORmarkPosition = PORmarkPosition + 5;
        } else if (three == nolines) {
            PORmarkPosition = PORmarkPosition + 10;
        }

        byte[] pormark = new byte[8];
        buff.position(PORmarkPosition);
        buff.get(pormark, 0, 8);
        String pormarks = new String(pormark);

        dbgLog.fine("pormark =>" + pormarks + "<-");
        

        if (pormarks.equals(POR_MARK)) {
            dbgLog.fine("this file is spss-por type");
            return true;
        } else {
            dbgLog.fine("this file is NOT spss-por type");
        }
        return false;
    }

    @Override
    public StatDataFileReader createReaderInstance(Object ext){
        return new PORFileReader(this);
    }

}
