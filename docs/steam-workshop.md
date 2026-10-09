# 스팀 창작마당 업로드 설정

Mechanics를 스팀 창작마당(Steam Workshop, 앱 1169040)에 올리는 방법입니다.

- 첫 업로드는 게임 안 업로더로 직접 합니다. 아이템과 태그가 이때 만들어집니다.
- 그 뒤의 업데이트는 GitHub Actions가 `steamcmd +workshop_build_item`으로 올립니다.
  - 매번 바뀌는 것: 콘텐츠(jar + `preview.png`), 미리보기 이미지, 제목, 설명, 변경 노트(영어).
  - 바뀌지 않는 것: 공개 범위, 태그.
- 워크플로 파일: `.github/workflows/release.yml`, `workshop-upload.yml`, `workshop-reupload.yml`

표기
- **(확인 안 됨)**: 직접 확인하지 못한 내용입니다. 첫 실행 때 확인합니다.
- **(보고됨)**: 다른 사람의 보고나 다른 버전의 코드로만 뒷받침되는 내용입니다.

## 1. 계정 요건

- 아이템은 본인 Steam 계정이 소유합니다. CI도 같은 계정으로 로그인합니다.
  - 파일·제목·설명은 아이템 소유자만 바꿀 수 있습니다. 공동 작업자는 못 바꿉니다. (보고됨)
- 그 계정이 Necesse를 소유해야 합니다. 소유하지 않으면 업로드가 "Access denied"로 거부됩니다. (보고됨)
- 스팀 창작마당 약관(Workshop legal agreement)에 동의해야 합니다.
  - 주소: https://steamcommunity.com/sharedfiles/workshoplegalagreement
  - 동의하기 전에 올린 아이템은 숨김 상태로 남습니다(Valve 구현 가이드).
- Steam Guard 모바일 인증기를 쓰는 계정이어야 합니다. CI가 그 `shared_secret`으로 로그인 코드를 만듭니다(4절).

## 2. 첫 업로드: 게임 안 업로더 (Windows 11)

### 준비
- Steam에서 Necesse를 설치하고, Steam에 그 계정으로 로그인해 둡니다.
- JDK 21을 설치합니다. CI와 같은 버전입니다(예: Eclipse Temurin 21).
- Gradle 8.14.3을 설치합니다. CI와 같은 버전입니다.
  - 저장소의 `gradlew.bat`은 Gradle 9.6.1을 받아 씁니다. 이 빌드가 Gradle 9에서 되는지는 확인하지 않았습니다. (확인 안 됨)
  - Gradle 8.14.3의 빌드 로그도 이 빌드가 Gradle 9.0과 호환되지 않는 기능을 쓴다고 경고합니다.
- `%APPDATA%\Necesse\mods\`에 Mechanics jar를 직접 넣어 두었다면 빼 둡니다.
  - 같은 모드 id가 둘이면 하나만 로드됩니다. 어느 쪽이 로드되는지는 확인하지 않았습니다.

### 빌드하고 게임 실행
PowerShell에서 저장소 폴더로 이동합니다. 올릴 버전을 체크아웃한 뒤 실행합니다.

```
git checkout v0.1.0
gradle clean runClient
```

- `runClient`가 하는 일
  - `buildModJar`로 `build\jar\Mechanics-1.3.3-<버전>.jar`를 만듭니다.
  - 저장소 폴더에 `steam_appid.txt`(1169040)를 만듭니다. Git이 무시하는 파일입니다.
  - Steam 클라이언트용 게임을 `-mod "build/jar/"` 인자로 실행합니다. 이 폴더의 jar가 개발 모드로 로드됩니다.
- 게임이 기본 경로(`C:\Program Files (x86)\Steam\steamapps\common\Necesse`)에 없으면 경로를 지정합니다.
  - 예: `gradle clean runClient -PgameDir="D:\SteamLibrary\steamapps\common\Necesse"`
  - 환경 변수 `NECESSE_DIR`로 지정해도 됩니다.
- 개발 모드 폴더 조건: jar 하나와 `preview.png` 말고는 아무것도 없어야 합니다(하위 폴더도 안 됨).
  - 1.3.3 `LoadedDevMod.validateDevFolderAndReturnJar`에서 확인했습니다.
  - 조건이 맞지 않으면 게임 로그에 '개발 모드는 하나의 jar 파일이 있는 디렉토리여야 합니다…' 경고가 남습니다(1.3.3 `DevModProvider`).
  - `clean`을 함께 실행하면 폴더가 깨끗해집니다.

### 게임 안에서 업로드
메뉴 이름은 1.3.3 한국어 언어 파일(`kr.lang`)에서 확인했습니다. 버튼이 보이는 조건과 화면 순서는 1.0.1 디컴파일 코드와 다른 모드 저자의 1.3.x 기록을 따른 것입니다. (보고됨)

1. 메인 메뉴에서 '모드'를 엽니다.
2. Mechanics를 고릅니다. 출처가 '본 게임 폴더에서 불러옴'(개발 모드)으로 표시됩니다.
3. '스팀 창작마당에 업로드'를 누릅니다.
   - 이 버튼은 개발 모드로 로드되고 미리보기 이미지가 있을 때만 나옵니다. (보고됨)
4. '새로운 모드 생성'을 고릅니다.
5. '설명 업데이트 (언제든 변경 가능합니다):'에 설명을 넣습니다. 비워 둬도 됩니다.
   - CI가 다음 업로드부터 `.github/workshop/description.bbcode`로 덮어씁니다.
6. '모드에 적합한 태그를 선택하세요:'에서 태그를 고릅니다.
   - 태그는 CI가 바꿀 수 없으니 여기서 정합니다.
7. 약관 동의 화면('링크 열기')과 '자신이 직접 만든 모드만 업로드 해야 합니다…' 확인을 통과합니다.
8. '모드 생성 및 업데이트 완료'가 나오면 끝입니다.

- 게임 업로더는 제목을 mod.info의 이름(Mechanics)으로 정합니다. (보고됨) CI는 다음 업로드부터 `title.txt`로 바꿉니다.
- 게임 업로더의 변경 노트는 "Mod version X for game version Y"로 고정입니다. (보고됨)
- 새 아이템은 숨김 상태로 시작합니다. (보고됨) 공개 범위는 창작마당 페이지에서 정합니다.

## 3. 아이템 ID와 `WORKSHOP_ITEM_ID`

1. 창작마당 아이템 페이지 주소에서 `id=` 뒤의 숫자가 아이템 ID입니다.
   - 예: `https://steamcommunity.com/sharedfiles/filedetails/?id=1234567890` → `1234567890`
2. GitHub 저장소에서 Settings → Secrets and variables → Actions → **Variables** 탭 → New repository variable을 누릅니다.
   - Name: `WORKSHOP_ITEM_ID`
   - Value: 아이템 ID(숫자만)
- **환경 변수가 아니라 저장소 변수로 만듭니다.** Release 실행의 사전 검사가 환경 밖에서 이 값을 읽습니다.
- 값이 비었거나 숫자가 아니면 실행이 실패합니다.

## 4. `shared_secret` 얻기

`shared_secret`은 Steam Guard 모바일 인증기가 로그인 코드를 만드는 비밀 키입니다. 공식 Steam 앱은 이 값을 보여주지 않습니다. (보고됨) 그래서 아래 방법 중 하나가 필요하고, 방법마다 위험이 있습니다.

> **경고**: 비밀번호와 `shared_secret`이 함께 있으면 Steam Guard를 통과해 계정에 완전히 접근할 수 있습니다. 이 둘은 GitHub 환경 시크릿에만 넣고 다른 곳에 저장하거나 붙여 넣지 않습니다.

### 방법
1. **Steam Desktop Authenticator(SDA)로 인증기를 옮기기**
   - 결과로 나오는 `.maFile`(JSON)에 `shared_secret`이 들어 있습니다.
   - 계정의 인증기는 하나뿐이라서 휴대폰 앱의 인증기를 대신하게 됩니다. 휴대폰 앱으로는 더 이상 코드와 확인을 받지 못합니다.
   - 등록할 때 나오는 복구 코드(R로 시작)를 반드시 따로 보관합니다. maFile과 복구 코드를 모두 잃으면 Steam 지원에 요청하는 수밖에 없습니다(SDA README).
   - SDA README는 이 프로그램이 **더 이상 지원·업데이트되지 않는다**고 밝힙니다. PC가 악성 코드에 감염되면 2단계 인증의 의미가 없어진다고도 경고합니다.
   - 계정을 훔치는 가짜 SDA가 돌고 있습니다. 받을 거라면 공식 저장소(`github.com/Jessecar96/SteamDesktopAuthenticator`)에서만 받습니다.
2. **steamguard-cli로 인증기 등록하기**
   - 오픈 소스 명령줄 도구입니다(`github.com/dyc3/steamguard-cli`). `steamguard setup`으로 같은 형식의 maFile을 만듭니다.
   - 휴대폰 앱을 대신한다는 점과 복구 코드 보관이 필요하다는 점은 SDA와 같습니다.
   - README는 이 도구가 사실상 베타라고 밝힙니다. 받을 거라면 그 저장소의 릴리스나 README가 안내하는 패키지 관리자에서만 받습니다.
3. **휴대폰의 공식 앱 데이터에서 꺼내기**
   - 루팅·탈옥이나 백업 분석이 필요해서 휴대폰의 보안을 낮춥니다.
   - 지금 버전의 앱에서 되는지 확인하지 않았습니다. (확인 안 됨)

### 공통
- maFile은 SDA·steamguard-cli의 암호화 기능으로 암호화해 두고 백업합니다.
  - maFile에는 `shared_secret` 말고도 거래 확인용 `identity_secret`과 복구 코드가 들어 있습니다.
  - GitHub에는 `shared_secret` 값(따옴표 없는 base64 문자열)만 넣습니다.
- 인증기를 옮기거나 제거하면 Steam이 거래·장터 이용을 한동안 제한할 수 있습니다. (확인 안 됨)
- 유출이 의심되면 다음을 합니다.
  - Steam 비밀번호를 바꿉니다.
  - 인증기를 제거하고 다시 등록해 `shared_secret`을 바꿉니다.
  - Steam Guard 관리 페이지에서 다른 모든 기기의 인증을 해제합니다.
  - GitHub 시크릿을 새 값으로 바꿉니다.

## 5. GitHub 환경 `steam-workshop`

Settings → Environments → New environment에서 이름을 `steam-workshop`으로 만들고 다음을 설정합니다.

- **Required reviewers**: 체크하지 않습니다(승인 없이 실행).
- **Deployment branches and tags**: `Selected branches and tags`를 고릅니다.
  - Add deployment branch or tag rule → Ref type `Branch` → Name pattern에 기본 브랜치 이름을 넣습니다.
  - 기본 브랜치 이름은 Settings → General → Default branch에서 확인합니다. 지금은 `ccr-fbfa803e-vxqf7h`입니다.
  - 기본 브랜치 이름을 바꾸면 이 규칙도 바꿉니다.
- **Environment secrets**: Add environment secret으로 셋을 넣습니다.

| 이름 | 값 |
| --- | --- |
| `STEAM_USERNAME` | Steam 로그인 아이디(프로필 이름 아님) |
| `STEAM_PASSWORD` | Steam 비밀번호 |
| `STEAM_SHARED_SECRET` | 4절의 `shared_secret` 값 |

- 업로드 job은 이 환경을 쓰므로 기본 브랜치에서 시작한 실행만 시크릿을 받습니다.
- job 안에서도 기본 브랜치가 아니면 실패하도록 한 번 더 검사합니다.

## 6. 업로드 실행

### 업로드 전에 저장소에 둘 파일
기본 브랜치의 최신 커밋에서 읽습니다. 예전 릴리스를 다시 올릴 때도 마찬가지입니다.

| 파일 | 내용 | 제한 |
| --- | --- | --- |
| `.github/workshop/title.txt` | 제목 한 줄 | 128바이트 |
| `.github/workshop/description.bbcode` | 설명(BBCode, 영어와 한국어를 한 설명에) | 7,999바이트 |

- 변경 노트는 릴리스 노트의 `## English` 부분을 BBCode로 바꾼 것입니다. 제한은 7,999바이트입니다.
  - Workshop upload job은 GitHub 릴리스 본문에서 가져옵니다. Release의 사전 검사는 같은 내용인 `.github/release-notes/<태그>.md`를 봅니다.
  - `## English`가 없거나 비었으면 실패합니다.
- 길이는 UTF-8 바이트로 셉니다. 한글 한 글자는 3바이트입니다. 넘으면 실패합니다.
- **v0.1.0 릴리스 노트의 영어 부분은 변환하면 9,411바이트라 이 제한을 넘습니다.**
  - 그래서 v0.1.0은 CI로 다시 올릴 수 없습니다.
  - 변경 노트의 실제 제한은 확인하지 못했습니다. Valve 문서에 상수(8000)는 있지만 "Unused"로 적혀 있습니다. (확인 안 됨)

### 새 릴리스와 함께 올리기
Actions → Release → Run workflow에서 다음처럼 실행합니다.
- Use workflow from: 기본 브랜치
- `tag`: 새 태그(예: `v0.2.0`)
- **Also upload to the Steam Workshop (default branch only)**: 체크

순서
1. 사전 검사: 기본 브랜치인지, `WORKSHOP_ITEM_ID`가 있는지, 제목·설명 파일과 영어 변경 노트가 제한 안에 있는지 봅니다.
2. 빌드와 테스트
3. GitHub 릴리스 생성
4. `workshop` job이 릴리스에 첨부된 jar를 올립니다.

- 사전 검사가 실패하면 릴리스를 만들지 않습니다.
- 태그를 푸시해서 시작한 실행은 업로드하지 않습니다.

### 기존 릴리스 다시 올리기
업로드만 실패했을 때나 예전 릴리스를 다시 올릴 때 씁니다.

Actions → Workshop re-upload → Run workflow에서 기본 브랜치를 고르고 `tag`(예: `v0.2.0`)를 넣어 실행합니다. 그 릴리스에 첨부된 jar와 그 릴리스 노트의 영어 부분을 올립니다.

## 7. 한국어 변경 노트 (수동)

CI는 영어 변경 노트만 올립니다. steamcmd에는 언어 키가 없어서 제목·설명·변경 노트가 영어로 저장됩니다.

- 한국어 변경 노트는 창작마당 아이템 페이지의 변경 사항(Change Notes)에서 해당 항목을 편집해 넣습니다.
  - 언어를 한국어로 골라 저장합니다.
- Steam에 손으로 붙이는 한국어 변경 노트는 릴리스 노트 한국어 섹션의 '변경 사항'과 '저장 호환' 두 절로 합니다.
  - 한국어 섹션 전체는 6절의 바이트 한도(7,999바이트)를 넘을 수 있고(v0.1.1: 전체 9,357바이트, 두 절 7,924바이트), '요약'·'설치'·'알려진 제한'은 다른 곳과 겹칩니다.
- Steam은 변경 노트를 언어별로 저장하고, 보는 사람의 언어에 맞는 노트를 보여줍니다.
  - 다른 아이템에서 그렇게 보이는 것을 관찰했습니다.
  - 웹 편집 화면에 언어 선택이 있다는 것은 보고뿐이고 확인하지 않았습니다. (확인 안 됨)
- 설명은 영어와 한국어를 한 설명(영어 칸)에 넣습니다.
  - 웹에서 한국어 설명을 따로 만들면, 한국어 사용자에게는 CI가 갱신하지 않는 그 설명이 보일 수 있습니다. (확인 안 됨)

## 8. 첫 CI 실행 때 확인할 것

- **로그**
  - steamcmd 출력에 `Committing update...` 뒤 `Success.`가 있고, `ERROR!`·`FAILED`가 없는지 봅니다.
  - 마지막에 `Uploaded <태그> to …` 알림이 나오는지 봅니다.
  - 이 문자열들은 steamcmd 바이너리에서 찾은 것이고 실제 출력 모양은 보지 못했습니다. (확인 안 됨)
    - 성공했는데 실패로 판정되면(또는 반대이면) `tools/workshop/check_steamcmd_log.py`의 규칙을 실제 로그에 맞게 고칩니다.
  - 아이디·비밀번호·인증 코드가 `***`로 가려져 있는지 봅니다.
  - Steam 시각 조회(QueryTime)가 실패하면 경고를 남기고 러너 시계로 코드를 만듭니다.
- **창작마당 페이지**
  - 제목이 `title.txt`와 같은지 봅니다.
  - 설명이 끝까지 잘리지 않고 나오는지 봅니다. 긴 값이 VDF에서 잘리지 않는지 확인하는 것입니다. (확인 안 됨)
  - 변경 노트를 확인합니다.
    - 영어인지
    - 제목·굵게·목록·표 서식이 제대로 보이는지
    - 따옴표와 `%APPDATA%\Necesse\mods\`의 역슬래시가 그대로 보이는지. VDF 이스케이프(`\\`, `\"`)를 확인하는 것입니다. (확인 안 됨)
    - 인라인 코드는 일반 글자로 바꿨습니다. Steam의 `[code]`는 블록이라 문장이 끊기기 때문입니다.
  - 미리보기 이미지가 바뀌었는지, 태그와 공개 범위가 그대로인지 봅니다.
- **콘텐츠**
  - 구독한 PC의 `steamapps\workshop\content\1169040\<ID>\`에 새 jar 하나와 `preview.png`만 있는지 봅니다.
  - 업데이트하면 콘텐츠가 통째로 바뀌어 이전 jar가 지워진다고 가정했습니다. (확인 안 됨)
  - 게임의 '모드' 메뉴에 '스팀 창작마당에서 불러옴'과 새 버전이 나오는지 봅니다.
- **계정**
  - GitHub 러너 위치에서 로그인했다는 Steam 알림이 올 수 있습니다. 예상된 것입니다.
  - 로그인이 실패해도 재시도하지 않습니다. 실패가 반복되면 Steam의 로그인 횟수 제한에 걸릴 수 있습니다.
