package com.util.gif;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;

/**
 **********************************************************************
 *
 * @author yudungang
 *         2018/03/23
 *         GIF图片处理核心工具类
 **********************************************************************
 */

public class GifDecoder {
    static class GifFrame {

        public BufferedImage image;
        public int delay;

        public GifFrame(BufferedImage im, int del) {
            image = im;
            delay = del;
        }
    }

    public GifDecoder() {
        loopCount = 1;
        block = new byte[256];
        blockSize = 0;
        dispose = 0;
        lastDispose = 0;
        transparency = false;
        delay = 0;
    }

    public int getDelay(int n) {
        delay = -1;
        if (n >= 0 && n < frameCount)
            delay = ((GifFrame) frames.get(n)).delay;
        return delay;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public BufferedImage getImage() {
        return getFrame(0);
    }

    public int getLoopCount() {
        return loopCount;
    }

    protected void setPixels() {
        int dest[] = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        if (lastDispose > 0) {
            if (lastDispose == 3) {
                int n = frameCount - 2;
                if (n > 0)
                    lastImage = getFrame(n - 1);
                else
                    lastImage = null;
            }
            if (lastImage != null) {
                int prev[] = ((DataBufferInt) lastImage.getRaster().getDataBuffer()).getData();
                System.arraycopy(prev, 0, dest, 0, width * height);
                if (lastDispose == 2) {
                    Graphics2D g = image.createGraphics();
                    Color c = null;
                    if (transparency)
                        c = new Color(0, 0, 0, 0);
                    else
                        c = new Color(lastBgColor);
                    g.setColor(c);
                    g.setComposite(AlphaComposite.Src);
                    g.fill(lastRect);
                    g.dispose();
                }
            }
        }
        int pass = 1;
        int inc = 8;
        int iline = 0;
        for (int i = 0; i < ih; i++) {
            int line = i;
            if (interlace) {
                if (iline >= ih)
                    switch (++pass) {
                        case 2: // '\002'
                            iline = 4;
                            break;

                        case 3: // '\003'
                            iline = 2;
                            inc = 4;
                            break;

                        case 4: // '\004'
                            iline = 1;
                            inc = 2;
                            break;
                    }
                line = iline;
                iline += inc;
            }
            line += iy;
            if (line < height) {
                int k = line * width;
                int dx = k + ix;
                int dlim = dx + iw;
                if (k + width < dlim)
                    dlim = k + width;
                int sx = i * iw;
                for (; dx < dlim; dx++) {
                    int index = pixels[sx++] & 0xff;
                    int c = act[index];
                    if (c != 0)
                        dest[dx] = c;
                }

            }
        }

    }

    public BufferedImage getFrame(int n) {
        BufferedImage im = null;
        if (n >= 0 && n < frameCount)
            im = ((GifFrame) frames.get(n)).image;
        return im;
    }

    public Dimension getFrameSize() {
        return new Dimension(width, height);
    }

    public int read(BufferedInputStream is) {
        init();
        if (is != null) {
            in = is;
            readHeader();
            if (!err()) {
                readContents();
                if (frameCount < 0)
                    status = 1;
            }
        } else {
            status = 2;
        }
        try {
            is.close();
        } catch (IOException ioexception) {
        }
        return status;
    }

    public int read(InputStream is) {
        init();
        if (is != null) {
            if (!(is instanceof BufferedInputStream))
                is = new BufferedInputStream(is);
            in = (BufferedInputStream) is;
            readHeader();
            if (!err()) {
                readContents();
                if (frameCount < 0)
                    status = 1;
            }
        } else {
            status = 2;
        }
        try {
            is.close();
        } catch (IOException ioexception) {
        }
        return status;
    }

    public int read(String name) {
        status = 0;
        try {
            name = name.trim().toLowerCase();
            if (name.indexOf("file:") >= 0 || name.indexOf(":/") > 0) {
                URL url = new URL(name);
                in = new BufferedInputStream(url.openStream());
            } else {
                in = new BufferedInputStream(new FileInputStream(name));
            }
            status = read(in);
        } catch (IOException e) {
            status = 2;
        }
        return status;
    }

    protected void decodeImageData() {
        int NullCode = -1;
        int npix = iw * ih;
        if (pixels == null || pixels.length < npix)
            pixels = new byte[npix];
        if (prefix == null)
            prefix = new short[4096];
        if (suffix == null)
            suffix = new byte[4096];
        if (pixelStack == null)
            pixelStack = new byte[4097];
        int data_size = read();
        int clear = 1 << data_size;
        int end_of_information = clear + 1;
        int available = clear + 2;
        int old_code = NullCode;
        int code_size = data_size + 1;
        int code_mask = (1 << code_size) - 1;
        for (int code = 0; code < clear; code++) {
            prefix[code] = 0;
            suffix[code] = (byte) code;
        }

        int bits;
        int count;
        int first;
        int top;
        int bi;
        int pi;
        int datum = bits = count = first = top = pi = bi = 0;
        int i = 0;
        while (i < npix) {
            if (top == 0) {
                if (bits < code_size) {
                    if (count == 0) {
                        count = readBlock();
                        if (count <= 0)
                            break;
                        bi = 0;
                    }
                    datum += (block[bi] & 0xff) << bits;
                    bits += 8;
                    bi++;
                    count--;
                    continue;
                }
                int code = datum & code_mask;
                datum >>= code_size;
                bits -= code_size;
                if (code > available || code == end_of_information)
                    break;
                if (code == clear) {
                    code_size = data_size + 1;
                    code_mask = (1 << code_size) - 1;
                    available = clear + 2;
                    old_code = NullCode;
                    continue;
                }
                if (old_code == NullCode) {
                    pixelStack[top++] = suffix[code];
                    old_code = code;
                    first = code;
                    continue;
                }
                int in_code = code;
                if (code == available) {
                    pixelStack[top++] = (byte) first;
                    code = old_code;
                }
                for (; code > clear; code = prefix[code])
                    pixelStack[top++] = suffix[code];

                first = suffix[code] & 0xff;
                if (available >= 4096)
                    break;
                pixelStack[top++] = (byte) first;
                prefix[available] = (short) old_code;
                suffix[available] = (byte) first;
                if ((++available & code_mask) == 0 && available < 4096) {
                    code_size++;
                    code_mask += available;
                }
                old_code = in_code;
            }
            top--;
            pixels[pi++] = pixelStack[top];
            i++;
        }
        for (i = pi; i < npix; i++)
            pixels[i] = 0;

    }

    protected boolean err() {
        return status != 0;
    }

    protected void init() {
        status = 0;
        frameCount = 0;
        frames = new ArrayList();
        gct = null;
        lct = null;
    }

    protected int read() {
        int curByte = 0;
        try {
            curByte = in.read();
        } catch (IOException e) {
            status = 1;
        }
        return curByte;
    }

    protected int readBlock() {
        blockSize = read();
        int n = 0;
        if (blockSize > 0) {
            try {
                int count = 0;
                for (; n < blockSize; n += count) {
                    count = in.read(block, n, blockSize - n);
                    if (count == -1)
                        break;
                }

            } catch (IOException ioexception) {
            }
            if (n < blockSize)
                status = 1;
        }
        return n;
    }

    protected int[] readColorTable(int ncolors) {
        int nbytes = 3 * ncolors;
        int tab[] = null;
        byte c[] = new byte[nbytes];
        int n = 0;
        try {
            n = in.read(c);
        } catch (IOException ioexception) {
        }
        if (n < nbytes) {
            status = 1;
        } else {
            tab = new int[256];
            int i = 0;
            int j = 0;
            while (i < ncolors) {
                int r = c[j++] & 0xff;
                int g = c[j++] & 0xff;
                int b = c[j++] & 0xff;
                tab[i++] = 0xff000000 | r << 16 | g << 8 | b;
            }
        }
        return tab;
    }

    protected void readContents() {
        for (boolean done = false; !done && !err(); ) {
            int code = read();
            label0:
            switch (code) {
                case 0: // '\0'
                    break;

                case 44: // ','
                    readImage();
                    break;

                case 33: // '!'
                    code = read();
                    switch (code) {
                        case 249:
                            readGraphicControlExt();
                            break label0;

                        case 255:
                            readBlock();
                            String app = "";
                            for (int i = 0; i < 11; i++)
                                app = (new StringBuilder(String.valueOf(app))).append((char) block[i]).toString();

                            if (app.equals("NETSCAPE2.0"))
                                readNetscapeExt();
                            else
                                skip();
                            break;

                        default:
                            skip();
                            break;
                    }
                    break;

                case 59: // ';'
                    done = true;
                    break;

                default:
                    status = 1;
                    break;
            }
        }

    }

    protected void readGraphicControlExt() {
        read();
        int packed = read();
        dispose = (packed & 0x1c) >> 2;
        if (dispose == 0)
            dispose = 1;
        transparency = (packed & 1) != 0;
        delay = readShort() * 10;
        transIndex = read();
        read();
    }

    protected void readHeader() {
        String id = "";
        for (int i = 0; i < 6; i++)
            id = (new StringBuilder(String.valueOf(id))).append((char) read()).toString();

        if (!id.startsWith("GIF")) {
            status = 1;
            return;
        }
        readLSD();
        if (gctFlag && !err()) {
            gct = readColorTable(gctSize);
            bgColor = gct[bgIndex];
        }
    }

    protected void readImage() {
        ix = readShort();
        iy = readShort();
        iw = readShort();
        ih = readShort();
        int packed = read();
        lctFlag = (packed & 0x80) != 0;
        interlace = (packed & 0x40) != 0;
        lctSize = 2 << (packed & 7);
        if (lctFlag) {
            lct = readColorTable(lctSize);
            act = lct;
        } else {
            act = gct;
            if (bgIndex == transIndex)
                bgColor = 0;
        }
        int save = 0;
        if (transparency) {
            save = act[transIndex];
            act[transIndex] = 0;
        }
        if (act == null)
            status = 1;
        if (err())
            return;
        decodeImageData();
        skip();
        if (err())
            return;
        frameCount++;
        image = new BufferedImage(width, height, 2);
        setPixels();
        frames.add(new GifFrame(image, delay));
        if (transparency)
            act[transIndex] = save;
        resetFrame();
    }

    protected void readLSD() {
        width = readShort();
        height = readShort();
        int packed = read();
        gctFlag = (packed & 0x80) != 0;
        gctSize = 2 << (packed & 7);
        bgIndex = read();
        pixelAspect = read();
    }

    protected void readNetscapeExt() {
        do {
            readBlock();
            if (block[0] == 1) {
                int b1 = block[1] & 0xff;
                int b2 = block[2] & 0xff;
                loopCount = b2 << 8 | b1;
            }
        } while (blockSize > 0 && !err());
    }

    protected int readShort() {
        return read() | read() << 8;
    }

    protected void resetFrame() {
        lastDispose = this.dispose;
        lastRect = new Rectangle(ix, iy, iw, ih);
        lastImage = image;
        lastBgColor = bgColor;
        int dispose = 0;
        boolean transparency = false;
        int delay = 0;
        lct = null;
    }

    protected void skip() {
        do
            readBlock();
        while (blockSize > 0 && !err());
    }

    public static final int STATUS_OK = 0;
    public static final int STATUS_FORMAT_ERROR = 1;
    public static final int STATUS_OPEN_ERROR = 2;
    protected BufferedInputStream in;
    protected int status;
    protected int width;
    protected int height;
    protected boolean gctFlag;
    protected int gctSize;
    protected int loopCount;
    protected int gct[];
    protected int lct[];
    protected int act[];
    protected int bgIndex;
    protected int bgColor;
    protected int lastBgColor;
    protected int pixelAspect;
    protected boolean lctFlag;
    protected boolean interlace;
    protected int lctSize;
    protected int ix;
    protected int iy;
    protected int iw;
    protected int ih;
    protected Rectangle lastRect;
    protected BufferedImage image;
    protected BufferedImage lastImage;
    protected byte block[];
    protected int blockSize;
    protected int dispose;
    protected int lastDispose;
    protected boolean transparency;
    protected int delay;
    protected int transIndex;
    protected static final int MaxStackSize = 4096;
    protected short prefix[];
    protected byte suffix[];
    protected byte pixelStack[];
    protected byte pixels[];
    protected ArrayList frames;
    protected int frameCount;
}
