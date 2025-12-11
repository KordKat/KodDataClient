package hello1.koddata.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileState {

    public String name;
    public int receivedBytes;
    public int expectedBytes;

    public ByteBuffer receivedBuffer;

    public void doSave() {
        try (AsynchronousFileChannel afc = AsynchronousFileChannel.open(
                Path.of(name),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            receivedBuffer.flip();                 // prepare for writing
            afc.write(receivedBuffer, 0);          // write file starting at offset 0
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
