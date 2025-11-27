package hello1.koddata.client;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileState {

    public String name;
    public int receivedBytes;
    public int expectedBytes;

    public ByteBuffer receivedBuffer;


    public void doSave(){
        try(AsynchronousFileChannel afc = AsynchronousFileChannel.open(
                Path.of(URI.create(name)),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
        )){
            afc.write(receivedBuffer.flip(), receivedBuffer.limit());
        }catch (IOException ex){
            ex.printStackTrace();
        }
    }
}
