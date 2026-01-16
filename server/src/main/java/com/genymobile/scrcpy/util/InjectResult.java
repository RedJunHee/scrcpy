package com.genymobile.scrcpy.util;

public final class InjectResult {

    private final boolean ok;
    private final String error;

    private InjectResult(boolean ok, String error) {
        this.ok = ok;
        this.error = error;
    }

    public static InjectResult success() {
        // 성공 응답은 오류 문자열이 없다.
        return new InjectResult(true, null);
    }

    public static InjectResult failure(String error) {
        // 실패 응답은 간결한 오류 코드/메시지를 포함한다.
        return new InjectResult(false, error);
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        // null일 수 있으므로 호출 측에서 null 체크를 한다.
        return error;
    }
}
