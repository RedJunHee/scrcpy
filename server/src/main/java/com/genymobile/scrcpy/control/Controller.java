package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.AsyncProcessor;
import com.genymobile.scrcpy.Options;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.util.Ln;

import android.os.SystemClock;
import android.util.Base64;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.StringTokenizer;

public class Controller implements AsyncProcessor {

    private static final int DEFAULT_DEVICE_ID = 0;

    private final int displayId;
    private final boolean supportsInputEvents;
    private final ControlChannel controlChannel;
    private final boolean powerOn;

    private final KeyCharacterMap charMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

    private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[1];
    private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[1];

    private Thread thread;

    public Controller(ControlChannel controlChannel, Options options) {
        this.displayId = options.getDisplayId();
        this.controlChannel = controlChannel;
        this.powerOn = options.getPowerOn();
        initPointers();

        supportsInputEvents = Device.supportsInputEvents(displayId);
        if (!supportsInputEvents) {
            Ln.w("Input events are not supported for secondary displays before Android 10");
        }
    }

    private void initPointers() {
        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        props.toolType = MotionEvent.TOOL_TYPE_FINGER;
        pointerProperties[0] = props;

        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.orientation = 0;
        coords.size = 0;
        pointerCoords[0] = coords;
    }

    private void control() throws IOException {
        // on start, power on the device
        if (powerOn && displayId == 0 && !Device.isScreenOn(displayId)) {
            Device.pressReleaseKeycode(KeyEvent.KEYCODE_POWER, displayId, Device.INJECT_MODE_ASYNC);
            // 입력과 디스플레이 전원 상태 충돌을 줄이기 위해 잠시 대기한다.
            SystemClock.sleep(500);
        }

        boolean alive = true;
        while (!Thread.currentThread().isInterrupted() && alive) {
            alive = handleEvent();
        }
    }

    @Override
    public void start(final TerminationListener listener) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    control();
                } catch (IOException e) {
                    Ln.e("Controller error", e);
                } finally {
                    Ln.d("Controller stopped");
                    listener.onTerminated(true);
                }
            }
        }, "control-recv");
        thread.start();
    }

    @Override
    public void stop() {
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

    private boolean handleEvent() throws IOException {
        String line;
        try {
            line = controlChannel.recv();
        } catch (IOException e) {
            // this is expected on close
            return false;
        }

        if (line == null) {
            return false;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            sendError("EMPTY_COMMAND");
            return true;
        }

        String command;
        String arguments;
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex == -1) {
            command = trimmed;
            arguments = "";
        } else {
            command = trimmed.substring(0, spaceIndex);
            arguments = trimmed.substring(spaceIndex + 1).trim();
        }

        command = command.toUpperCase(Locale.ENGLISH);

        if ("PING".equals(command)) {
            sendOk("PONG");
            return true;
        }

        if ("CLIP_GET".equals(command)) {
            handleClipboardGet();
            return true;
        }

        if ("CLIP_SET".equals(command)) {
            handleClipboardSet(arguments);
            return true;
        }

        // 입력이 필요한 커맨드는 디스플레이 지원 여부를 먼저 확인한다.
        if (!supportsInputEvents) {
            sendError("INPUT_NOT_SUPPORTED");
            return true;
        }

        if ("TAP".equals(command)) {
            handleTap(arguments);
        } else if ("SWIPE".equals(command) || "DRAG".equals(command)) {
            handleSwipe(arguments);
        } else if ("KEYCODE".equals(command)) {
            handleKeycode(arguments);
        } else if ("TEXT".equals(command)) {
            handleText(arguments);
        } else {
            sendError("UNKNOWN_COMMAND");
        }

        return true;
    }

    private void handleTap(String arguments) throws IOException {
        StringTokenizer tokenizer = new StringTokenizer(arguments);
        if (tokenizer.countTokens() < 2) {
            sendError("INVALID_ARGS");
            return;
        }

        Integer x = parseInt(tokenizer.nextToken());
        Integer y = parseInt(tokenizer.nextToken());
        if (x == null || y == null) {
            sendError("INVALID_COORDS");
            return;
        }

        float pressure = 1.0f;
        if (tokenizer.hasMoreTokens()) {
            Float parsedPressure = parseFloat(tokenizer.nextToken());
            if (parsedPressure == null) {
                sendError("INVALID_PRESSURE");
                return;
            }
            pressure = parsedPressure.floatValue();
        }

        int buttons = 0;
        if (tokenizer.hasMoreTokens()) {
            Integer parsedButtons = parseInt(tokenizer.nextToken());
            if (parsedButtons == null) {
                sendError("INVALID_BUTTONS");
                return;
            }
            buttons = parsedButtons.intValue();
        }

        if (tokenizer.hasMoreTokens()) {
            sendError("INVALID_ARGS");
            return;
        }

        boolean ok = injectTap(x.intValue(), y.intValue(), pressure, buttons);
        respond(ok, null);
    }

    private void handleSwipe(String arguments) throws IOException {
        StringTokenizer tokenizer = new StringTokenizer(arguments);
        if (tokenizer.countTokens() < 5) {
            sendError("INVALID_ARGS");
            return;
        }

        Integer x1 = parseInt(tokenizer.nextToken());
        Integer y1 = parseInt(tokenizer.nextToken());
        Integer x2 = parseInt(tokenizer.nextToken());
        Integer y2 = parseInt(tokenizer.nextToken());
        Integer durationMs = parseInt(tokenizer.nextToken());
        if (x1 == null || y1 == null || x2 == null || y2 == null || durationMs == null) {
            sendError("INVALID_ARGS");
            return;
        }

        if (tokenizer.hasMoreTokens()) {
            sendError("INVALID_ARGS");
            return;
        }

        boolean ok = injectSwipe(x1.intValue(), y1.intValue(), x2.intValue(), y2.intValue(), durationMs.intValue());
        respond(ok, null);
    }

    private void handleKeycode(String arguments) throws IOException {
        StringTokenizer tokenizer = new StringTokenizer(arguments);
        if (!tokenizer.hasMoreTokens()) {
            sendError("INVALID_ARGS");
            return;
        }

        Integer keyCode = parseInt(tokenizer.nextToken());
        if (keyCode == null) {
            sendError("INVALID_KEYCODE");
            return;
        }

        String action = "both";
        if (tokenizer.hasMoreTokens()) {
            action = tokenizer.nextToken();
        }

        if (tokenizer.hasMoreTokens()) {
            sendError("INVALID_ARGS");
            return;
        }

        boolean ok = injectKeycode(keyCode.intValue(), action);
        respond(ok, null);
    }

    private void handleText(String arguments) throws IOException {
        String decoded = decodeBase64(arguments);
        if (decoded == null) {
            sendError("INVALID_BASE64");
            return;
        }

        if (decoded.isEmpty()) {
            sendOk(null);
            return;
        }

        int injected = injectText(decoded);
        if (injected <= 0) {
            sendError("TEXT_NOT_SUPPORTED");
            return;
        }

        sendOk(null);
    }

    private void handleClipboardGet() throws IOException {
        String clipboardText = Device.getClipboardText();
        if (clipboardText == null) {
            sendError("CLIPBOARD_UNAVAILABLE");
            return;
        }

        String encoded = encodeBase64(clipboardText);
        sendOk(encoded);
    }

    private void handleClipboardSet(String arguments) throws IOException {
        String decoded = decodeBase64(arguments);
        if (decoded == null) {
            sendError("INVALID_BASE64");
            return;
        }

        boolean ok = Device.setClipboardText(decoded);
        respond(ok, ok ? null : "CLIPBOARD_SET_FAILED");
    }

    private boolean injectTap(int x, int y, float pressure, int buttons) {
        long downTime = SystemClock.uptimeMillis();
        boolean downOk = injectTouchEvent(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, pressure, buttons);
        long upTime = SystemClock.uptimeMillis();
        boolean upOk = injectTouchEvent(downTime, upTime, MotionEvent.ACTION_UP, x, y, pressure, buttons);
        return downOk && upOk;
    }

    private boolean injectSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        long downTime = SystemClock.uptimeMillis();
        boolean downOk = injectTouchEvent(downTime, downTime, MotionEvent.ACTION_DOWN, x1, y1, 1.0f, 0);
        if (!downOk) {
            return false;
        }

        int safeDuration = Math.max(0, durationMs);
        int steps = safeDuration > 0 ? Math.max(1, safeDuration / 16) : 0;
        if (steps > 0) {
            int stepDuration = safeDuration / steps;
            for (int i = 1; i < steps; ++i) {
                float progress = (float) i / (float) steps;
                int moveX = x1 + Math.round((x2 - x1) * progress);
                int moveY = y1 + Math.round((y2 - y1) * progress);
                long eventTime = downTime + (long) stepDuration * i;
                if (!injectTouchEvent(downTime, eventTime, MotionEvent.ACTION_MOVE, moveX, moveY, 1.0f, 0)) {
                    return false;
                }
                SystemClock.sleep(stepDuration);
            }
        }

        long upTime = SystemClock.uptimeMillis();
        return injectTouchEvent(downTime, upTime, MotionEvent.ACTION_UP, x2, y2, 1.0f, 0);
    }

    private boolean injectTouchEvent(long downTime, long eventTime, int action, int x, int y, float pressure, int buttons) {
        pointerCoords[0].x = x;
        pointerCoords[0].y = y;
        pointerCoords[0].pressure = pressure;

        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, 1, pointerProperties, pointerCoords, 0, buttons, 1f, 1f,
                DEFAULT_DEVICE_ID, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        return Device.injectEvent(event, displayId, Device.INJECT_MODE_ASYNC);
    }

    private boolean injectKeycode(int keyCode, String action) {
        String normalized = action == null ? "both" : action.toLowerCase(Locale.ENGLISH);
        if ("down".equals(normalized)) {
            return injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        }
        if ("up".equals(normalized)) {
            return injectKeyEvent(KeyEvent.ACTION_UP, keyCode);
        }
        if ("both".equals(normalized)) {
            return pressReleaseKeycode(keyCode);
        }
        return false;
    }

    private int injectText(String text) {
        int successCount = 0;
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (!injectChar(c)) {
                Ln.w("Could not inject char u+" + String.format("%04x", (int) c));
                continue;
            }
            successCount++;
        }
        return successCount;
    }

    private boolean injectChar(char c) {
        String decomposed = KeyComposition.decompose(c);
        char[] chars = decomposed != null ? decomposed.toCharArray() : new char[]{c};
        KeyEvent[] events = charMap.getEvents(chars);
        if (events == null) {
            return false;
        }

        for (int i = 0; i < events.length; ++i) {
            KeyEvent event = events[i];
            if (!Device.injectEvent(event, displayId, Device.INJECT_MODE_ASYNC)) {
                return false;
            }
        }
        return true;
    }

    private boolean injectKeyEvent(int action, int keyCode) {
        return Device.injectKeyEvent(action, keyCode, 0, 0, displayId, Device.INJECT_MODE_ASYNC);
    }

    private boolean pressReleaseKeycode(int keyCode) {
        return Device.pressReleaseKeycode(keyCode, displayId, Device.INJECT_MODE_ASYNC);
    }

    private void respond(boolean ok, String errorMessage) throws IOException {
        if (ok) {
            sendOk(null);
        } else if (errorMessage != null && !errorMessage.isEmpty()) {
            sendError(errorMessage);
        } else {
            sendError("INJECT_FAILED");
        }
    }

    private void sendOk(String payload) throws IOException {
        if (payload == null || payload.isEmpty()) {
            controlChannel.send("OK");
        } else {
            controlChannel.send("OK " + payload);
        }
    }

    private void sendError(String message) throws IOException {
        if (message == null || message.isEmpty()) {
            controlChannel.send("ERR");
        } else {
            controlChannel.send("ERR " + message);
        }
    }

    private Integer parseInt(String token) {
        try {
            return Integer.valueOf(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Float parseFloat(String token) {
        try {
            return Float.valueOf(Float.parseFloat(token));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String decodeBase64(String token) {
        if (token == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.decode(token, Base64.DEFAULT);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String encodeBase64(String text) {
        if (text == null) {
            return "";
        }

        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }
}
