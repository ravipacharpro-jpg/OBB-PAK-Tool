package com.obbpak.tool.pak;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/** Seekable writer over an output FileChannel (patched pak output). */
public final class ChannelWriter {

    private final FileChannel ch;

    public ChannelWriter(FileChannel ch) {
        this.ch = ch;
    }

    public void seekWrite(long pos, byte[] data) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(data);
        long p = pos;
        while (bb.hasRemaining()) {
            int n = ch.write(bb, p);
            if (n < 0) throw new IOException("Write failed");
            p += n;
        }
    }

    public long length() throws IOException {
        return ch.size();
    }
}
