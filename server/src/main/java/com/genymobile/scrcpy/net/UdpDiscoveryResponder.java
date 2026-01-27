package com.genymobile.scrcpy.net;

import com.genymobile.scrcpy.AsyncProcessor;
import com.genymobile.scrcpy.util.Ln;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * UDP 브로드캐스트 탐지를 위한 간단한 응답 서버.
 * 수신 패킷이 들어오면 고정 응답 문자열을 송신한 뒤 계속 대기한다.
 */
public class UdpDiscoveryResponder implements AsyncProcessor {

    private static final int MAX_PACKET_SIZE = 1024;

    private final int port;
    private final String responseMessage;

    private Thread thread;
    private DatagramSocket socket;

    public UdpDiscoveryResponder(int port, String responseMessage) {
        this.port = port;
        this.responseMessage = responseMessage;
    }

    @Override
    public void start(final TerminationListener listener) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop(listener);
            }
        }, "udp-discovery");
        thread.start();
    }

    private void runLoop(TerminationListener listener) {
        DatagramSocket localSocket = null;
        try {
            localSocket = new DatagramSocket(null);
            localSocket.setReuseAddress(true);
            localSocket.setBroadcast(true);
            localSocket.bind(new InetSocketAddress(port));
            socket = localSocket;

            Ln.i("UDP discovery responder started on port " + port);

            byte[] buffer = new byte[MAX_PACKET_SIZE];
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    localSocket.receive(packet);
                } catch (SocketException e) {
                    if (localSocket.isClosed()) {
                        break;
                    }
                    throw e;
                }

                respond(localSocket, packet);
            }
        } catch (IOException e) {
            Ln.e("UDP discovery responder error", e);
        } finally {
            if (localSocket != null) {
                localSocket.close();
            }
            socket = null;
            Ln.i("UDP discovery responder stopped");
            listener.onTerminated(false);
        }
    }

    private void respond(DatagramSocket localSocket, DatagramPacket packet) throws IOException {
        // 응답은 수신 패킷의 발신 주소/포트로 직접 유니캐스트한다.
        byte[] responseBytes = responseMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket responsePacket = new DatagramPacket(
                responseBytes,
                responseBytes.length,
                packet.getAddress(),
                packet.getPort());
        localSocket.send(responsePacket);
    }

    @Override
    public void stop() {
        if (socket != null) {
            socket.close();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void join() throws InterruptedException {
        if (thread != null) {
            thread.join();
        }
    }
}
