# UDP 탐지 규격서 (FrameX bridge)

## 1. 개요

이 문서는 FrameX 브리지 서버의 UDP 탐지 규격을 정의한다.
브로드캐스트 송신과 응답 수신을 단순화하기 위해 고정 문자열 기반의 최소 규격을 사용한다.

## 2. 전송 계층

- 프로토콜: UDP
- 인코딩: UTF-8 문자열

## 3. 기본 포트

- 기본 포트: 27184
- 포트 변경은 서버 옵션 `udp_discovery_port`로 제어한다.

## 4. 메시지 형식

- 페이로드는 고정 문자열 한 줄로 구성한다.
- 기본 문자열: `FRAMEX_SERVER`
- 변경은 서버 옵션 `udp_discovery_response`로 제어한다.

### 4.1 요청 패킷

- 길이 제한은 명시하지 않는다.
- 서버는 포트로 수신되는 모든 UDP 패킷에 대해 응답 또는 브로드캐스트 동작을 수행한다.

### 4.2 응답 패킷

- 서버는 수신한 UDP 패킷의 발신 주소/포트로 유니캐스트 응답을 송신한다.
- 응답 문자열은 `udp_discovery_response` 값으로 결정된다.

## 5. 브로드캐스트 송신 규칙

- 서버가 브로드캐스트 송신을 활성화하면, 지정된 주소로 주기적으로 패킷을 송신한다.
- 기본 브로드캐스트 주소는 `255.255.255.255` 이다.
- 송신 주기는 `udp_discovery_broadcast_interval_ms`로 제어하며, 기본값은 1000ms이다.

## 6. 서버 옵션 요약

- `udp_discovery` (true/false)
  - UDP 수신 응답 기능 활성화 여부
- `udp_discovery_port` (1..65535)
  - UDP 포트 지정
- `udp_discovery_response` (문자열)
  - 응답/브로드캐스트 메시지
- `udp_discovery_broadcast` (true/false)
  - 브로드캐스트 송신 기능 활성화 여부
- `udp_discovery_broadcast_interval_ms` (정수)
  - 브로드캐스트 송신 주기
- `udp_discovery_broadcast_address` (문자열)
  - 브로드캐스트 대상 주소

## 7. 참고 실행 예시

```bash
adb shell CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 2.1 \
  scid=12345678 log_level=info control=true \
  udp_discovery=true udp_discovery_broadcast=true \
  udp_discovery_port=27184 udp_discovery_response=FRAMEX_SERVER
```

## 8. 호환성

- 이 규격은 FrameX 브리지 서버 전용이다.
- scrcpy 기본 서버와는 별도 규격이다.
