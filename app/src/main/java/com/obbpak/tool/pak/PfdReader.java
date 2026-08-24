package com.obbpak.tool.pak;

import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/** Seekable random reader over a SAF ParcelFileDescriptor. */
public final class PfdReader implements RandomReader {

    private final ParcelFileDescriptor pfd;
    private final FileInputStream fis;
    private final FileChannel ch;
    private final long len;

    public PfdReader(ParcelFileDescriptor pfd) throws IOException {
        this.pfd = pfd;
        this.fis = new FileInputStream(pfd.getFileDescriptor());
        this.ch = fis.getChannel();
        this.len = ch.size();
    }

    public void readFully(long pos, byte[] buf, int off, int count) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(buf, off, count);
        long p = pos;
        while (bb.hasRemaining()) {
            int n = ch.read(bb, p);
            if (n < 0) throw new IOException("Unexpected EOF");
            p += n;
        }
    }

    public long length() {
        return len;
    }
}
