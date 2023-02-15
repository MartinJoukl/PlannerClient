package joukl.plannerexec.plannerclient.model;

import java.io.IOException;
import java.io.OutputStream;

public class NotClosingOutputStream extends OutputStream {

    /**
     * The underlying output stream.
     */
    private final OutputStream out;

    /**
     * Creates a new output stream that does not close the given output stream on a call to {@link #close()}.
     *
     * @param out the output stream
     */
    public NotClosingOutputStream(final OutputStream out) {
        this.out = out;
    }

    /*
     * DELEGATION TO OUTPUT STREAM
     */

    @Override
    public void close() throws IOException {
        // do nothing here, since we don't want to close the underlying input stream
    }

    @Override
    public void write(final int b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    public void realClose() throws IOException {
        out.close();
    }
}
