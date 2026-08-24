package com.obbpak.tool.pak;

import java.io.IOException;

/** Seekable random reader abstraction (SAF fd or plain file). */
public interface RandomReader {

    void readFully(long pos, byte[] buf, int off, int count) throws IOException;

    long length();
}
