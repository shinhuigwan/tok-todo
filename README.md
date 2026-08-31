# 톡todo

일정과 일일업무일지를 한곳에서 관리하는 Android·Windows 앱입니다. Windows 앱은 회사 PC와 집 PC에서 같은 Google 계정으로 로그인하면 전용 Google Calendar를 통해 내용을 양방향 동기화합니다.

## Windows 앱

### 포함된 기능

- 날짜별 업무 타이핑, 시간·분류 지정
- 완료 상태 변경 및 삭제
- 날짜별 일일업무일지 작성
- 완료한 업무를 업무일지에 자동 추가
- 보고용 텍스트를 클립보드로 복사
- 인터넷이 없어도 로컬 저장 후 나중에 동기화
- `톡todo 업무일지` 전용 Google Calendar 자동 생성
- 회사 PC·집 PC 간 일정과 업무일지 양방향 동기화
- 동기화 전 로컬 데이터 자동 백업

### 설치 파일 받기

1. GitHub 저장소의 `Actions`를 엽니다.
2. `Build 톡todo Windows installer` 작업을 실행합니다.
3. 완료된 작업의 `tok-todo-windows-installer` artifact를 내려받습니다.
4. 압축을 풀고 `TokTodo-1.0.0.exe`를 실행합니다.

설치 파일에는 실행에 필요한 Java가 포함되므로 PC에 Java를 따로 설치할 필요가 없습니다. Windows가 게시자를 확인할 수 없다는 안내를 표시할 수 있으며, 회사 PC에서는 사내 프로그램 설치 정책을 먼저 확인해야 합니다.

### Google Calendar 연결

Google 비밀번호나 OAuth 비밀키는 저장소에 올리지 않습니다. 처음 한 번 Google Cloud에서 개인용 데스크톱 OAuth 설정 파일을 만든 후, 각 PC의 앱에서 `Google 연결`을 눌러 같은 파일을 선택해야 합니다.

자세한 순서는 [Google Calendar 연결 안내](docs/google-calendar-setup.md)를 참고하세요.

동기화 데이터와 로그인 토큰은 `%APPDATA%\TokTodo`에 저장됩니다. `data.backup.json`은 가장 최근 로컬 데이터의 백업입니다.

### Windows 앱 로컬 빌드

- JDK 17과 Gradle 8.9 이상을 설치합니다.
- `build-windows.cmd`를 실행합니다.
- 결과: `desktop/build/installer/TokTodo-1.0.0.exe`

또는 `gradle :desktop:run`으로 설치하지 않고 실행할 수 있습니다.

## Android 앱

기존 음성 중심 Android 테스트 앱도 계속 포함됩니다.

앱 아이콘은 캘린더·마이크·완료 체크를 결합한 보라색 생산성 앱 아이콘이며, 홈의 음성 입력 버튼은 작은 화면에서도 캘린더가 빨리 보이도록 컴팩트한 66dp 높이로 구성했습니다.

### 포함된 기능

- Android 한국어 음성 인식 화면 호출
- `오늘`, `내일`, `모레`, 요일, `8월 25일` 날짜 분석
- `오전 10시`, `오후 3시 30분`, `저녁`, `3시 반` 시간 분석
- `30분 전`, `1시간 전`, `1일 전` 알림 분석
- 음성 인식 완료 즉시 날짜·시간·알림 분석 및 자동 저장
- 시간이 없는 일정은 오전 9시가 아닌 입력 당시의 현지 시각으로 저장
- 월간 캘린더의 날짜별 미완료(빨강)·완료(파랑) 개수 표시
- 날짜 선택 시 상세 할 일 카드 표시
- 상세 카드에서 완료·미완료 상태 전환 및 삭제
- 제목·원래 음성 문장·카테고리를 대상으로 하는 키워드 검색
- 검색 결과 선택 시 해당 날짜의 캘린더와 상세 일정으로 바로 이동
- SharedPreferences 기반 기기 내 저장
- AlarmManager와 로컬 알림

### 휴대폰 테스트

1. `tok-todo.apk`를 Android 8.0 이상 휴대폰에 설치합니다.
2. 처음 실행할 때 알림 권한을 허용합니다.
3. `음성으로 일정 추가`를 누릅니다.
4. “내일 오후 3시 치과 예약 30분 전에 알려줘”라고 말합니다.
5. 음성 인식이 끝나면 일정이 즉시 저장되는지 확인합니다.
6. 캘린더에서 해당 날짜의 빨간 미완료 숫자를 확인합니다.
7. 날짜를 누르고 상세 카드에서 완료로 바꾼 뒤 숫자가 파란색으로 이동하는지 확인합니다.

음성 인식은 휴대폰에 설치된 Google 또는 제조사 음성 인식 서비스를 사용합니다. 서비스가 없거나 네트워크 상태에 따라 동작하지 않으면 홈의 직접 입력란을 사용할 수 있습니다.

### GitHub Actions에서 임시 APK 만들기

프로젝트를 GitHub 저장소의 루트에 올린 다음 `Actions > Build 톡todo test APK > Run workflow`를 실행합니다. 완료 후 `tok-todo-apk` artifact를 내려받습니다. 테스트용 debug APK이므로 별도의 서명 secret이 필요하지 않습니다.

### Android 로컬 빌드

- Android Studio에서 폴더를 열고 JDK 17과 Android SDK 35를 선택합니다.
- `Build > Build APK(s)`를 실행합니다.
- Gradle이 PATH에 있으면 `build-apk.cmd`를 실행할 수도 있습니다.

로컬 APK 경로: `app/build/outputs/apk/debug/app-debug.apk`

> 현재 Google Calendar 동기화는 Windows 앱에 적용되어 있습니다. Android 앱은 기존 로컬 저장 방식이며, 모바일 동기화는 후속 버전에서 연결할 예정입니다.
