package com.genymobile.scrcpy.framex;

import com.genymobile.scrcpy.util.Ln;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * FrameX 등록 API 호출 전용 서비스 클래스.
 * 네트워크 호출을 분리해 컨트롤러와의 결합도를 낮춘다.
 */
public class FramexRegisterService {

    private static final String REGISTER_URL = "https://framex-use-androidr-db.redjoon10.workers.dev/register";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    /**
     * 등록 API 결과를 담는 값 객체.
     * 외부 상태를 변경하지 않는다.
     */
    public static class RegisterResult {
        private final boolean ok;
        private final String reason;
        // 실패 시 로그로 남긴 메시지를 그대로 보관해 CONNECT 응답에 포함한다.
        private final String failureLog;

        public RegisterResult(boolean ok, String reason, String failureLog) {
            this.ok = ok;
            this.reason = reason;
            this.failureLog = failureLog;
        }

        public boolean isOk() {
            return ok;
        }

        public String getReason() {
            return reason;
        }

        public String getFailureLog() {
            return failureLog;
        }
    }

    public RegisterResult register(String licenseKey, String machineId, String androidUuid) {
        if (isBlank(licenseKey) || isBlank(machineId) || isBlank(androidUuid)) {
            String logMessage = "FrameX 등록 실패: 필수 필드 누락";
            Ln.w(logMessage);
            return new RegisterResult(false, "MISSING_FIELDS", logMessage);
        }

        HttpURLConnection connection = null;
        try {
            connection = openConnection();
            String payload = buildPayload(licenseKey, machineId, androidUuid);
            writePayload(connection, payload);

            int responseCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                if (parseOkFlag(responseBody)) {
                    return new RegisterResult(true, null, null);
                }
                String logMessage = "FrameX 등록 실패: 응답 ok=false";
                Ln.w(logMessage);
                return new RegisterResult(false, "REGISTER_NOT_OK", logMessage);
            }

            String logMessage = "FrameX 등록 실패: http=" + responseCode + ", bodyLen=" + responseBody.length();
            Ln.w(logMessage);
            return new RegisterResult(false, "HTTP_" + responseCode, logMessage);
        } catch (IOException e) {
            String logMessage = "FrameX 등록 통신 오류";
            Ln.w(logMessage, e);
            return new RegisterResult(false, "IO_ERROR", logMessage);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(REGISTER_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        return connection;
    }

    private void writePayload(HttpURLConnection connection, String payload) throws IOException {
        OutputStream outputStream = null;
        try {
            outputStream = connection.getOutputStream();
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private String readResponseBody(HttpURLConnection connection) throws IOException {
        InputStream inputStream = null;
        try {
            if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }
            return readStream(inputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private String readStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private String buildPayload(String licenseKey, String machineId, String androidUuid) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"licenseKey\":\"");
        builder.append(escapeJson(licenseKey));
        builder.append("\",\"machineId\":\"");
        builder.append(escapeJson(machineId));
        builder.append("\",\"androidUUID\":\"");
        builder.append(escapeJson(androidUuid));
        builder.append("\"}");
        return builder.toString();
    }

    private boolean parseOkFlag(String responseBody) {
        if (responseBody == null) {
            return false;
        }
        String compact = responseBody.replace(" ", "");
        compact = compact.replace("\n", "");
        compact = compact.replace("\r", "");
        return compact.contains("\"ok\":true");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        return escaped;
    }

    private boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); ++i) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
