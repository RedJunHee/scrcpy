package com.genymobile.scrcpy.control;

import android.net.LocalSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import com.genymobile.scrcpy.util.Ln;

public final class ControlChannel {

    // 입력 로그가 지나치게 길어지는 것을 막기 위해 프리뷰 길이를 제한한다.
    private static final int LOG_TEXT_PREVIEW_LIMIT = 80;
    private static final int LOG_BYTE_PREVIEW_LIMIT = 64;

    private final BufferedReader reader;
    private final BufferedWriter writer;

    public ControlChannel(LocalSocket controlSocket) throws IOException {
        // 소켓 스트림을 텍스트 단위로 읽기 위해 UTF-8 기반 Reader/Writer로 래핑한다.
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * 한 줄 명령을 읽는다. EOF이면 null을 반환한다.
     */
    public String recv() throws IOException {
        // 컨트롤 소켓으로부터 입력이 올 때까지 대기 중임을 로그로 남긴다.
        Ln.d("컨트롤 소켓 수신 대기중");
        String line = reader.readLine();
        if (line == null) {
            // 소켓이 닫히면 EOF가 반환된다.
            Ln.d("컨트롤 소켓 EOF 수신");
            return null;
        }

        // 실제 수신된 텍스트와 바이트 정보를 함께 기록한다.
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        String bytePreview = toHexPreview(bytes, LOG_BYTE_PREVIEW_LIMIT);
        String textPreview = toTextPreview(line, LOG_TEXT_PREVIEW_LIMIT);
        Ln.d("컨트롤 소켓 수신: textLength=" + line.length() + ", byteLength=" + bytes.length
                + ", textPreview=\"" + textPreview + "\", bytePreview=" + bytePreview);
        return line;
    }

    /**
     * 한 줄 응답을 보낸다. 항상 \n으로 끝낸다.
     */
    public synchronized void send(String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private static String toTextPreview(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        String prefix = text.substring(0, limit);
        return prefix + "...(len=" + text.length() + ")";
    }

    private static String toHexPreview(byte[] bytes, int limit) {
        if (bytes == null || bytes.length == 0) {
            return "[]";
        }
        int max = bytes.length < limit ? bytes.length : limit;
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < max; ++i) {
            if (i > 0) {
                builder.append(' ');
            }
            int value = bytes[i] & 0xff;
            if (value < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(value));
        }
        if (bytes.length > limit) {
            builder.append(" ...len=").append(bytes.length);
        }
        builder.append(']');
        return builder.toString();
    }
}
