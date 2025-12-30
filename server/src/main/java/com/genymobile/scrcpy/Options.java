package com.genymobile.scrcpy;

import com.genymobile.scrcpy.util.Ln;

import java.util.Locale;

public class Options {

    private Ln.Level logLevel = Ln.Level.DEBUG;
    private int scid = -1; // 31-bit non-negative value, or -1
    private boolean tunnelForward;
    private boolean control = true;
    private int displayId;
    private boolean powerOn = true;
    private boolean sendDummyByte = true;

    public Ln.Level getLogLevel() {
        return logLevel;
    }

    public int getScid() {
        return scid;
    }

    public boolean isTunnelForward() {
        return tunnelForward;
    }

    public boolean getControl() {
        return control;
    }

    public int getDisplayId() {
        return displayId;
    }

    public boolean getPowerOn() {
        return powerOn;
    }

    public boolean getSendDummyByte() {
        return sendDummyByte;
    }

    /**
     * FrameX 브리지 서버에 필요한 최소 옵션만 파싱한다.
     * 기존 scrcpy 옵션은 의도적으로 무시한다.
     */
    public static Options parse(String... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing client version");
        }

        String clientVersion = args[0];
        if (!clientVersion.equals(BuildConfig.VERSION_NAME)) {
            throw new IllegalArgumentException(
                    "The server version (" + BuildConfig.VERSION_NAME + ") does not match the client " + "(" + clientVersion + ")");
        }

        Options options = new Options();

        for (int i = 1; i < args.length; ++i) {
            String arg = args[i];
            int equalIndex = arg.indexOf('=');
            if (equalIndex == -1) {
                throw new IllegalArgumentException("Invalid key=value pair: \"" + arg + "\"");
            }
            String key = arg.substring(0, equalIndex);
            String value = arg.substring(equalIndex + 1);
            if ("scid".equals(key)) {
                int scid = Integer.parseInt(value, 0x10);
                if (scid < -1) {
                    throw new IllegalArgumentException("scid may not be negative (except -1 for 'none'): " + scid);
                }
                options.scid = scid;
            } else if ("log_level".equals(key)) {
                options.logLevel = Ln.Level.valueOf(value.toUpperCase(Locale.ENGLISH));
            } else if ("tunnel_forward".equals(key)) {
                options.tunnelForward = Boolean.parseBoolean(value);
            } else if ("control".equals(key)) {
                options.control = Boolean.parseBoolean(value);
            } else if ("display_id".equals(key)) {
                options.displayId = Integer.parseInt(value);
            } else if ("power_on".equals(key)) {
                options.powerOn = Boolean.parseBoolean(value);
            } else if ("send_dummy_byte".equals(key)) {
                options.sendDummyByte = Boolean.parseBoolean(value);
            } else {
                // 미사용 옵션은 로그만 남기고 무시한다.
                Ln.w("Unknown server option: " + key);
            }
        }

        return options;
    }
}
