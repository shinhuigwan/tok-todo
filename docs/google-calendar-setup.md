# Google Calendar 연결 안내

이 설정은 한 번 만든 뒤 회사 PC와 집 PC에서 같은 `credentials.json`을 선택하면 됩니다. 두 PC에서는 반드시 같은 Google 계정으로 로그인하세요.

## 1. Google Cloud 프로젝트 만들기

1. [Google Cloud Console](https://console.cloud.google.com/)에 로그인합니다.
2. 새 프로젝트를 만들고 이름을 `톡todo`로 지정합니다.
3. `API 및 서비스`에서 **Google Calendar API**를 찾아 사용 설정합니다.

## 2. OAuth 동의 화면 설정

1. `Google Auth Platform`의 앱 설정을 엽니다.
2. 앱 이름과 지원 이메일을 입력합니다.
3. 개인 Gmail 계정이라면 대상 유형을 `외부`로 설정합니다.
4. 앱을 테스트 상태로 두고 본인의 Google 계정을 테스트 사용자로 추가합니다.

개인용으로 본인 계정만 사용하는 동안에는 앱을 공개 게시할 필요가 없습니다. 다른 사람에게 배포할 경우 Google의 OAuth 앱 검증이 필요할 수 있습니다.

## 3. 데스크톱 OAuth 파일 받기

1. `Google Auth Platform > 클라이언트`에서 새 OAuth 클라이언트를 만듭니다.
2. 애플리케이션 유형으로 **데스크톱 앱**을 선택합니다.
3. 생성된 클라이언트의 JSON 파일을 다운로드합니다.
4. 파일 이름은 달라도 괜찮습니다. 톡todo에서 `Google 연결`을 누르고 이 파일을 선택합니다.

이 JSON 파일은 GitHub, 메신저, 공개 폴더에 올리지 마세요. 회사 PC로 옮길 때는 본인만 접근할 수 있는 안전한 방법을 사용하세요.

## 4. 두 PC 연결하기

1. 회사 PC의 톡todo에서 `Google 연결`을 누르고 JSON 파일을 선택합니다.
2. 브라우저에서 사용할 Google 계정을 선택하고 Calendar 접근을 허용합니다.
3. 집 PC에서도 같은 절차를 진행하고 같은 Google 계정으로 로그인합니다.
4. 앱의 `지금 동기화`를 누르면 `톡todo 업무일지` 캘린더가 자동으로 생성되거나 기존 캘린더를 찾아 사용합니다.

PC끼리 직접 연결하지 않으므로 한쪽 PC가 꺼져 있어도 됩니다. 각 PC는 마지막으로 저장한 로컬 데이터를 유지하며, 인터넷 연결 후 `지금 동기화`를 누르면 변경 내용을 주고받습니다.

## 동기화 규칙

- 서로 다른 업무와 날짜는 자동으로 합쳐집니다.
- 같은 업무나 같은 날짜의 업무일지를 양쪽에서 수정하면 마지막으로 저장한 내용이 적용됩니다.
- Google Calendar에서 톡todo 전용 이벤트를 삭제하면 다음 동기화 때 앱에서도 제거됩니다.
- 실수로 덮어쓴 경우 `%APPDATA%\TokTodo\data.backup.json`에서 직전 로컬 데이터를 확인할 수 있습니다.

## 연결 문제

- 회사 방화벽이 `accounts.google.com`, `oauth2.googleapis.com`, `www.googleapis.com` 접속을 차단하면 동기화할 수 없습니다.
- 회사 보안 정책에서 개인 Google 계정 또는 미승인 프로그램 사용을 금지할 수 있습니다.
- `Google 계정 연결이 필요합니다`가 반복되면 `%APPDATA%\TokTodo\token.json`을 삭제하고 다시 연결하세요.
