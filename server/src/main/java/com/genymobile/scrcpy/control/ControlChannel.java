package com.genymobile.scrcpy.control;

import android.net.LocalSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class ControlChannel {

    private final BufferedReader reader;
    private final BufferedWriter writer;

    public ControlChannel(LocalSocket controlSocket) throws IOException {
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * 한 줄 명령을 읽는다. EOF이면 null을 반환한다.
     */
    public String recv() throws IOException {
        return reader.readLine();
    }

    /**
     * 한 줄 응답을 보낸다. 항상 \n으로 끝낸다.
     */
    public synchronized void send(String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }
}
