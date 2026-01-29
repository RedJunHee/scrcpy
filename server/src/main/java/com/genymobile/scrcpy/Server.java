package com.genymobile.scrcpy;

import com.genymobile.scrcpy.control.ControlChannel;
import com.genymobile.scrcpy.control.Controller;
import com.genymobile.scrcpy.device.DesktopConnection;
import com.genymobile.scrcpy.util.Ln;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class Server {

    public static final String SERVER_PATH;

    static {
        String[] classPaths = System.getProperty("java.class.path").split(File.pathSeparator);
        // 관례상 framex는 classpath 첫 항목으로 framex-server.jar의 절대 경로를 전달한다.
        SERVER_PATH = classPaths[0];
    }

    private static class Completion {
        private int running;
        private boolean fatalError;

        Completion(int running) {
            this.running = running;
        }

        synchronized void addCompleted(boolean fatalError) {
            --running;
            if (fatalError) {
                this.fatalError = true;
            }
            if (running == 0 || this.fatalError) {
                Looper.getMainLooper().quitSafely();
            }
        }
    }

    private Server() {
        // not instantiable
    }

    private static void scrcpy(Options options) throws IOException {
        int scid = options.getScid();
        boolean tunnelForward = options.isTunnelForward();
        boolean control = options.getControl();
        boolean sendDummyByte = options.getSendDummyByte();

        // 서버 부팅 단계 시작을 명확히 남겨, 클라이언트와의 동기화 문제를 추적하기 쉽게 만든다.
        Ln.i("Server startup: validating control channel and preparing connection");

        if (!control) {
            Ln.e("Control channel is disabled; FrameX bridge server requires the control channel");
            return;
        }

        // 브리지 서버에서도 내부 시스템 서비스 초기화를 유지한다.
        // Workarounds는 내부 Android 서비스 접근성을 확보하기 위한 단계다.
        Workarounds.apply();

        // 비동기 처리 구성 요소를 수집해 종료 동기화를 단순화한다.
        List<AsyncProcessor> asyncProcessors = new ArrayList<>();

        // 실제 소켓 연결을 열기 전에 주요 옵션을 기록한다.
        Ln.i("Opening desktop connection: scid=" + scid
                + ", tunnelForward=" + tunnelForward
                + ", control=" + control
                + ", sendDummyByte=" + sendDummyByte);

        DesktopConnection connection = DesktopConnection.open(scid, tunnelForward, control, sendDummyByte);
        try {
            // 데스크톱 연결이 열린 시점을 명확히 남긴다.
            Ln.i("Desktop connection opened");

            ControlChannel controlChannel = connection.getControlChannel();
            // 제어 채널은 FrameX 브리지의 핵심 경로이므로 항상 가장 먼저 초기화한다.
            // ControlChannel을 확보했다는 로그를 남겨 채널 단절 구간을 좁힌다.
            Ln.i("Control channel acquired");
            Controller controller = new Controller(controlChannel, options);
            asyncProcessors.add(controller);

            // 비동기 구성 요소 초기화가 완료되었음을 알린다.
            Ln.i("Async processors prepared: count=" + asyncProcessors.size());

            final Completion completion = new Completion(asyncProcessors.size());
            for (int i = 0; i < asyncProcessors.size(); ++i) {
                AsyncProcessor asyncProcessor = asyncProcessors.get(i);
                asyncProcessor.start(new AsyncProcessor.TerminationListener() {
                    @Override
                    public void onTerminated(boolean fatalError) {
                        // 각 구성 요소 종료 시점과 오류 여부를 상세히 기록한다.
                        Ln.i("Async processor terminated: fatalError=" + fatalError);
                        completion.addCompleted(fatalError);
                    }
                });
            }

            // 메인 루프 진입 지점 기록.
            Ln.i("Main looper entering");
            Looper.loop(); // interrupted by the Completion implementation
            // Looper.loop() 이후 흐름은 종료 단계이므로 명확히 남긴다.
            Ln.i("Main looper exited");
        } finally {
            // 종료 처리 단계 시작을 명확히 기록한다.
            Ln.i("Server shutdown: stopping async processors");
            for (int i = 0; i < asyncProcessors.size(); ++i) {
                asyncProcessors.get(i).stop();
            }

            // 소켓 종료 전에 호출 흐름을 남겨 디버깅을 돕는다.
            Ln.i("Desktop connection shutdown requested");
            connection.shutdown();

            try {
                // 각 구성 요소의 종료 대기를 시작한다.
                Ln.i("Waiting for async processors to join");
                for (int i = 0; i < asyncProcessors.size(); ++i) {
                    asyncProcessors.get(i).join();
                }
            } catch (InterruptedException e) {
                // 종료 대기 중 인터럽트 발생은 경고로 기록한다.
                Ln.w("Interrupted while waiting for async processors", e);
            }

            // 모든 종료 처리가 끝난 후 연결을 닫는다.
            Ln.i("Desktop connection closing");
            connection.close();

            // 서버 종료 완료 로그.
            Ln.i("Server shutdown complete");
        }
    }

    private static void prepareMainLooper() {
        // Like Looper.prepareMainLooper(), but with quitAllowed set to true
        Looper.prepare();
        synchronized (Looper.class) {
            try {
                @SuppressLint("DiscouragedPrivateApi")
                Field field = Looper.class.getDeclaredField("sMainLooper");
                field.setAccessible(true);
                field.set(null, Looper.myLooper());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static void main(String... args) {
        int status = 0;
        try {
            internalMain(args);
        } catch (Throwable t) {
            Ln.e(t.getMessage(), t);
            status = 1;
        } finally {
            // By default, the Java process exits when all non-daemon threads are terminated.
            // The Android SDK might start some non-daemon threads internally, preventing the scrcpy server to exit.
            // So force the process to exit explicitly.
            System.exit(status);
        }
    }

    private static void internalMain(String... args) throws Exception {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Ln.e("Exception on thread " + t, e);
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(t, e);
                }
            }
        });

        prepareMainLooper();

        Options options = Options.parse(args);

        Ln.disableSystemStreams();
        Ln.initLogLevel(options.getLogLevel());

        Ln.i("Device: [" + Build.MANUFACTURER + "] " + Build.BRAND + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");

        scrcpy(options);
    }
}
