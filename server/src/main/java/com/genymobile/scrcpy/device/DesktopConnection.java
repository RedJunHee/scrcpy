package com.genymobile.scrcpy.device;

import com.genymobile.scrcpy.control.ControlChannel;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.Closeable;
import java.io.IOException;

public final class DesktopConnection implements Closeable {

    // 데스크톱 측과 소켓 이름 규칙을 맞추기 위해 접두어를 고정한다.
    private static final String SOCKET_NAME_PREFIX = "framex";

    private final LocalSocket controlSocket;
    private final ControlChannel controlChannel;

    private DesktopConnection(LocalSocket controlSocket) throws IOException {
        this.controlSocket = controlSocket;
        controlChannel = controlSocket != null ? new ControlChannel(controlSocket) : null;
    }

    private static LocalSocket connect(String abstractName) throws IOException {
        LocalSocket localSocket = new LocalSocket();
        localSocket.connect(new LocalSocketAddress(abstractName));
        return localSocket;
    }

    private static String getSocketName(int scid) {
        if (scid == -1) {
            // SCID가 없으면 서버 단독 실행을 단순화하기 위해 접두어만 사용한다.
            return SOCKET_NAME_PREFIX;
        }

        return SOCKET_NAME_PREFIX + String.format("_%08x", scid);
    }

    public static DesktopConnection open(int scid, boolean tunnelForward, boolean control, boolean sendDummyByte) throws IOException {
        String socketName = getSocketName(scid);

        LocalSocket controlSocket = null;
        try {
            if (tunnelForward) {
                try (LocalServerSocket localServerSocket = new LocalServerSocket(socketName)) {
                    if (control) {
                        controlSocket = localServerSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            controlSocket.getOutputStream().write(0);
                        }
                    }
                }
            } else {
                if (control) {
                    controlSocket = connect(socketName);
                }
            }
        } catch (IOException | RuntimeException e) {
            if (controlSocket != null) {
                controlSocket.close();
            }
            throw e;
        }

        return new DesktopConnection(controlSocket);
    }

    public void shutdown() throws IOException {
        if (controlSocket != null) {
            controlSocket.shutdownInput();
            controlSocket.shutdownOutput();
        }
    }

    public void close() throws IOException {
        if (controlSocket != null) {
            controlSocket.close();
        }
    }

    public ControlChannel getControlChannel() {
        return controlChannel;
    }
}
