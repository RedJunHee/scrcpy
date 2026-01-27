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
    // 브로드캐스트 탐지를 위한 UDP 응답기능을 기본 비활성화한다.
    private boolean udpDiscoveryEnabled;
    // UDP 리스너 포트는 기존 스크립트와 충돌을 피하기 위해 별도 기본값을 둔다.
    private int udpDiscoveryPort = 27184;
    // 브로드캐스트 응답/송신 본문은 간단한 식별 문자열로 고정한다.
    private String udpDiscoveryResponse = "FRAMEX_SERVER";
    // 서버에서 브로드캐스트 송신을 수행할지 여부를 제어한다.
    private boolean udpDiscoveryBroadcastEnabled;
    // 브로드캐스트 송신 주기(밀리초), 너무 짧으면 네트워크에 부담이 된다.
    private int udpDiscoveryBroadcastIntervalMs = 1000;
    // 브로드캐스트 대상 주소 기본값은 전체 브로드캐스트로 둔다.
    private String udpDiscoveryBroadcastAddress = "255.255.255.255";

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

    public boolean isUdpDiscoveryEnabled() {
        return udpDiscoveryEnabled;
    }

    public int getUdpDiscoveryPort() {
        return udpDiscoveryPort;
    }

    public String getUdpDiscoveryResponse() {
        return udpDiscoveryResponse;
    }

    public boolean isUdpDiscoveryBroadcastEnabled() {
        return udpDiscoveryBroadcastEnabled;
    }

    public int getUdpDiscoveryBroadcastIntervalMs() {
        return udpDiscoveryBroadcastIntervalMs;
    }

    public String getUdpDiscoveryBroadcastAddress() {
        return udpDiscoveryBroadcastAddress;
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
            } else if ("udp_discovery".equals(key)) {
                options.udpDiscoveryEnabled = Boolean.parseBoolean(value);
            } else if ("udp_discovery_port".equals(key)) {
                int parsedPort = Integer.parseInt(value);
                if (parsedPort <= 0 || parsedPort > 65535) {
                    throw new IllegalArgumentException("udp_discovery_port must be in 1..65535: " + parsedPort);
                }
                options.udpDiscoveryPort = parsedPort;
            } else if ("udp_discovery_response".equals(key)) {
                if (value == null || value.trim().isEmpty()) {
                    Ln.w("udp_discovery_response is empty, keep default value");
                } else {
                    options.udpDiscoveryResponse = value;
                }
            } else if ("udp_discovery_broadcast".equals(key)) {
                options.udpDiscoveryBroadcastEnabled = Boolean.parseBoolean(value);
            } else if ("udp_discovery_broadcast_interval_ms".equals(key)) {
                int parsedInterval = Integer.parseInt(value);
                if (parsedInterval <= 0) {
                    throw new IllegalArgumentException(
                            "udp_discovery_broadcast_interval_ms must be positive: " + parsedInterval);
                }
                options.udpDiscoveryBroadcastIntervalMs = parsedInterval;
            } else if ("udp_discovery_broadcast_address".equals(key)) {
                if (value == null || value.trim().isEmpty()) {
                    Ln.w("udp_discovery_broadcast_address is empty, keep default value");
                } else {
                    options.udpDiscoveryBroadcastAddress = value;
                }
            } else {
                // 미사용 옵션은 로그만 남기고 무시한다.
                Ln.w("Unknown server option: " + key);
            }
        }

        return options;
    }
}
