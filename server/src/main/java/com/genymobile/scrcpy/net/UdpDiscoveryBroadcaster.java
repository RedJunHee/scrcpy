package com.genymobile.scrcpy.net;

import com.genymobile.scrcpy.AsyncProcessor;
import com.genymobile.scrcpy.util.Ln;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * UDP 브로드캐스트로 디바이스 존재를 주기적으로 알리는 구성요소.
 * 지정된 주소/포트로 고정 문자열을 송신한다.
 */
public class UdpDiscoveryBroadcaster implements AsyncProcessor {

    private final String broadcastAddress;
    private final int port;
    private final String message;
    private final int intervalMs;

    private Thread thread;
    private DatagramSocket socket;

    public UdpDiscoveryBroadcaster(String broadcastAddress, int port, String message, int intervalMs) {
        this.broadcastAddress = broadcastAddress;
        this.port = port;
        this.message = message;
        this.intervalMs = intervalMs;
    }

    @Override
    public void start(final TerminationListener listener) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop(listener);
            }
        }, "udp-broadcast");
        thread.start();
    }

    private void runLoop(TerminationListener listener) {
        DatagramSocket localSocket = null;
        try {
            localSocket = new DatagramSocket(null);
            localSocket.setReuseAddress(true);
            localSocket.setBroadcast(true);
            localSocket.bind(new InetSocketAddress(0));
            socket = localSocket;

            InetAddress targetAddress = InetAddress.getByName(broadcastAddress);
            Ln.i("UDP discovery broadcaster started: " + broadcastAddress + ":" + port);

            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(payload, payload.length, targetAddress, port);
                localSocket.send(packet);
                sleepInterval();
            }
        } catch (IOException e) {
            Ln.e("UDP discovery broadcaster error", e);
        } finally {
            if (localSocket != null) {
                localSocket.close();
            }
            socket = null;
            Ln.i("UDP discovery broadcaster stopped");
            listener.onTerminated(false);
        }
    }

    private void sleepInterval() {
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
