# Mechanics

Necesse용 유체 물류 모드입니다. 펌프가 액체 타일이나 탱크 밸브에서 유체를 끌어와 기본 파이프·지하 파이프를 거쳐 멀티블럭 탱크로 밀어냅니다.

- 대상 게임 버전: Necesse 1.3.3
- 현재 버전: 0.1.1 (사전 출시)
- 모드 id: `devp0tion.mechanics`, 작성자: DevP0tion

## 내용

- **유체**: 해수·담수(같은 물 타일을 위치로 구분), 용암, 슬라임 액체, 우즈, 정령의 물, 유사, 원유(모드 전용). 버킷 1개(액체 타일 1칸) = 10입니다.
- **파이프**: 기본 파이프와 지하 파이프. 바닐라 주괴 11종으로 만드는 광물 티어 11종이고, 광물 티어가 운송량과 운송 가능 유체를 정합니다. 지하 파이프는 벽·오브젝트·액체 타일·탱크 내부 아래로 지나갑니다.
- **펌프**: 수동 펌프(클릭), 화력 펌프와 고급 화력 펌프(통나무 연료). 펌프 아래의 액체 타일이나 직접 붙인 탱크 밸브의 탱크에서 끌어옵니다.
- **멀티블럭 탱크**: 광물 벽·탱크 밸브·탱크 컨트롤러 1개로 된 직사각형 테두리와 1×1 ~ 5×5 내부(전부 유리 블럭이거나 완전히 빔)로 이루어집니다. 탱크 하나에는 한 유체만 담깁니다.
- **공학 렌치**: 파이프 회수, 방향별 연결 또는 끊기, 파이프의 유체와 연결된 방향을 보여주는 툴팁.
- 모든 부품은 공학 작업대에서 만들고, 공학 작업대는 작업대에서 만듭니다.

버전별 내용, 수치, 알려진 제한은 [GitHub 릴리스](https://github.com/DevP0tion/Mechanics/releases)의 릴리스 노트(원본: [`.github/release-notes/`](.github/release-notes/))에 있습니다.

## 설치

- Necesse 1.3.3이 필요합니다.
- [GitHub 릴리스](https://github.com/DevP0tion/Mechanics/releases)에서 `Mechanics-1.3.3-<모드 버전>.jar`를 받아 게임 데이터 폴더의 `mods` 폴더에 넣습니다. 업데이트할 때는 이전 버전의 jar를 지웁니다.
  - Windows: `%APPDATA%\Necesse\mods\`
  - macOS: `~/Library/Application Support/Necesse/mods/`
  - Linux: `~/.config/Necesse/mods/`
- 새 모드는 기본으로 켜집니다. 메인 메뉴의 '모드'에서 켜고 끌 수 있고, 바꾼 뒤에는 게임을 다시 시작해야 합니다.
- 플레이어와 서버 모두 이 모드가 필요합니다(`clientside = false`). 전용 서버도 서버를 실행하는 사용자의 같은 `mods` 폴더를 씁니다.

## 빌드

필요한 것:

- JDK 21
- Gradle 8.14.3 (CI와 같은 버전). 저장소의 `gradlew`는 Gradle 9.6.1을 받아 쓰는데, 이 빌드가 Gradle 9에서 되는지는 확인하지 않았습니다.
- Necesse 1.3.3: Steam 게임 설치 폴더(`Necesse.jar`가 있음) 또는 전용 서버 패키지 폴더(`Server.jar`가 있음)

```sh
gradle -PgameDir="<Necesse 설치 폴더>" clean buildModJar check
```

- `-PgameDir`를 주지 않으면 환경 변수 `NECESSE_DIR`, 그다음 OS별 기본 Steam 설치 경로에서 찾습니다.
- `buildModJar`: `build/jar/Mechanics-1.3.3-<모드 버전>.jar`를 만듭니다.
- `check`: `coreTest`(게임에 의존하지 않는 core 로직 시험)와 `wrenchTest`(공학 렌치의 게임에 의존하지 않는 로직 시험)를 실행합니다.
- `runClient`: mod jar를 만들고 게임을 개발 모드로 실행합니다. Steam 게임 설치(`Necesse.jar`)가 필요합니다.

## 문서

- [docs/fluid-logistics/session.md](docs/fluid-logistics/session.md): 1차 설계 세션의 기획서 (역할, 범위, 고정 결정, 기록 위치).
- [docs/fluid-logistics/domain-rules.md](docs/fluid-logistics/domain-rules.md): 도메인 규칙. Necesse 1.3.3 서버판 디컴파일로 확인한 엔진 제약과, 세션 중 정한 탱크·지하 파이프·유체 규칙.
- [docs/fluid-logistics/decisions.md](docs/fluid-logistics/decisions.md): 1차 설계 세션의 결정 기록 (요소 목록, 결정 목록, 미결 목록).
- [docs/fluid-logistics/numbers.md](docs/fluid-logistics/numbers.md): 수치 설계 세션(2차) 기록 (기획서, 수치표, N 번호 결정 목록, 미결 목록, 참고 바닐라 수치).
- [docs/steam-workshop.md](docs/steam-workshop.md): 스팀 창작마당 업로드 설정 (첫 업로드, `WORKSHOP_ITEM_ID`, Steam 시크릿, CI 업로드).

## 확인 목록 (게임 안 확인 필요)

게임 안에서 아직 확인하지 않은 것들입니다. 전용 서버는 화면을 그리거나 키 입력을 받을 수 없어서, 그리기와 입력은 실제 클라이언트에서 확인해야 합니다.

### 0.1.1 미확인 항목 (요약)

**공유 벽의 탱크 밸브** (N33-1, N33-13, N33-14 — 자동 테스트로만 확인)

- [ ] 벽 하나를 함께 쓰는 두 탱크를 짓고 그 공유 벽에 탱크 밸브를 놓습니다. 설치가 거부되지 않고 두 탱크 모두 인식되는지(두 컨트롤러 창에 용량이 나오는지) 봅니다. 그 밸브의 광물 티어가 두 탱크의 다른 테두리보다 낮으면 두 탱크의 용량이 그 광물 티어에 맞게 줄어드는지 봅니다.
- [ ] 공유 벽의 밸브를 같은 칸의 지하 파이프로 펌프에 이어(지하 파이프는 탱크 내부 아래로 지나갈 수 있음) 밀어내고 끌어와 봅니다. 그 밸브를 통해서는 유체가 들어가지도 나오지도 않는지 봅니다.
- [ ] 탱크 A의 밸브를 같은 칸의 지하 파이프로 펌프에 이어 쓰다가, 옆에 탱크 B를 완성해 그 벽을 공유 벽으로 만듭니다. A에게도 그 밸브로 유체가 오가지 않는지 봅니다. 그다음 B의 테두리 벽 하나를 부숴 공유를 끝내고, 그 밸브가 다시 A의 밸브로 동작하는지 봅니다.
- [ ] 밸브 양쪽의 두 탱크를 모두 구조가 깨져 비활성으로 만들면(각 탱크의 테두리 벽 하나씩 부숨), 그 밸브가 유체를 받지도 내주지도 않는지 봅니다.
- [ ] 이웃 탱크의 컨트롤러가 언로드되어 그 탱크가 비활성인 동안에는 그 벽이 공유 벽이 아니라서, 밸브가 로드된 탱크의 밸브로 동작하는지 봅니다. 한쪽 컨트롤러만 언로드되는 배치가 필요해 만들기 어려울 수 있습니다.
- [ ] 공유 중 저장된 펌프를 다시 로드해 밸브가 제자리 공급원으로 돌아오는지, 뒤 밸브 칸만 언로드됐다 로드되면 다시 끌어오는지 (N35-1, N36-58)

**새 그리기** (N34 유리 블럭·채운 정도의 띠, 일반 벽인 밸브의 모습 — 실제 클라이언트에서 확인하지 않음)

- [ ] 인식된 탱크의 유리 블럭이 벽 윗면 높이(타일보다 16 px 위)의 유리 천장으로 그려지는지 봅니다. 이웃한 유리 블럭이 한 장의 판으로 이어지고 테두리는 판의 바깥 가장자리에만 있는지, 첫 내부 줄의 테두리가 북쪽 벽의 위 가장자리에 닿는지, 아래의 유체가 비쳐 보이는지 봅니다.
- [ ] 탱크를 채우며 북쪽 벽 안쪽 면의 띠가 채운 비율만큼 올라오는지(가득 차면 북쪽 벽 앞면 전체), 바닥 유체처럼 물결 셰이더로 움직이는지, 원유는 단색 띠인지 봅니다. 양이 0인 탱크와 구조가 깨져 비활성인 탱크에는 띠가 없는지 봅니다.
- [ ] 짓는 중인 탱크의 내부나 탱크 밖에 놓은 유리 블럭이 바닐라 벽 모양의 비쳐 보이는 유리 벽으로 그려지는지 봅니다. 벽·바위·다른 유리 블럭·탱크 부품 쪽으로는 이어지고 가구·문·파이프 쪽으로는 이어지지 않는지, 빛과 플레이어·커서가 뒤에 있을 때 흐려지는 것이 바닐라 벽과 같은지 봅니다.
- [ ] 탱크의 북쪽 벽 앞면 위에 커서를 두면 첫 내부 줄의 유리 블럭이 가리켜지는지(마우스 오버 영역이 그린 위치를 따름) 봅니다. 탱크 툴팁은 커서가 있는 타일 기준이라 내부 칸 위에서 나오고 북쪽 벽 앞면 위에서는 나오지 않는지 봅니다.
- [ ] 유리 블럭을 들고 인식된 탱크의 내부와 그 밖을 가리키면, 설치 미리보기가 각각 유리 천장과 유리 벽 모습인지 봅니다. 아이템 아이콘은 그대로인지 봅니다.
- [ ] 공학 렌치로 공유 벽의 밸브(일반 벽)를 가리켜도 툴팁이 없는지 봅니다.

**0.1.0 월드 열기** (실제로 열어 확인하지 않음)

- [ ] 0.1.0으로 저장한 월드(탱크·탱크 밸브·파이프·펌프가 있는 월드)를 0.1.1로 엽니다. 오류 없이 열리는지(게임 로그 포함), 탱크의 유체 양이 그대로인지, 밸브·파이프·펌프가 전처럼 유체를 옮기는지 봅니다.
- [ ] 0.1.0에서 공유 벽의 밸브 때문에 인식되지 않던 탱크가 있던 월드라면, 0.1.1에서 두 탱크 모두 인식되고 그 밸브는 일반 벽이 되는지 봅니다.

### 코드의 TODO(game) 전체

코드의 `TODO(game)` 주석 8곳과 [numbers.md](docs/fluid-logistics/numbers.md) 미결 목록의 note '구현 N34 반영'(①~④)을 옮긴 것입니다. 같은 확인을 가리키는 주석은 한 항목으로 합치고 출처를 모두 적었습니다.

**유리 블럭**

- [ ] 유리 천장: 올린 판과 북쪽 벽의 위 가장자리에 닿는 테두리, 이어진 판의 이음매, 천장의 빛(판은 자기 칸 위로 32 px까지 그리지만 자기 칸의 빛을 씀)이 자연스러운지.
  출처: `src/main/java/devp0tion/mechanics/objects/GlassBlockObject.java` (`GlassBlockObject` 클래스 주석), numbers.md ①
- [ ] 유리 천장 판 아래로 물결 셰이더의 바닥 유체와 채운 정도의 띠가 제대로 비쳐 보이는지.
  출처: `GlassBlockObject.java` (`GlassBlockObject` 클래스 주석, 'the pane over the shader fluid and the band'), `src/main/java/devp0tion/mechanics/client/TankFluidRendering.java` (`TankFluidRendering` 클래스 주석, 'the glass ceiling on top'), numbers.md ①
- [ ] 대각선으로만 이웃한 천장 유리(계단 모양 배치)에서 두 판의 테두리가 모서리끼리 만나는 모습(시트에 그 모서리 조각은 없음). 탱크용 바닥타일(아직 없음) 위에서만 생기는 배치라, 지금은 만들 수 없을 수 있습니다.
  출처: `src/main/java/devp0tion/mechanics/core/GlassBlockSprites.java` (`GlassBlockSprites` 클래스 주석, 천장 조각 고르기), numbers.md ①
- [ ] 유리 벽: 바닐라 벽·바위·가구 옆의 모습, 투명도, 빛, 흐려지기. 위아래로 쌓인 두 유리 벽 사이의 줄이 아래 유리 블럭의 빛과 정렬로 그려져도 어색하지 않은지.
  출처: `GlassBlockObject.java` (`GlassBlockObject` 클래스 주석), numbers.md ②
- [ ] 마우스 오버 영역: 유리 벽은 타일과 그 위 16 px, 유리 천장은 올린 판(테두리가 있으면 테두리까지)을 따르는지.
  출처: `GlassBlockObject.java` (`GlassBlockObject` 클래스 주석, 'the hover areas'), numbers.md ④

**탱크의 유체와 채운 정도의 띠**

- [ ] 탱크 바닥 유체: 유체 스프라이트, 물결 셰이더, 리전 경계에서의 모습.
  출처: `TankFluidRendering.java` (`TankFluidRendering` 클래스 주석)
- [ ] 탱크 바닥 유체가 바닐라 액체 타일의 깊은 물 모습으로 보이는지(`FLUID_HEIGHT` = `LiquidManager.minDepth`).
  출처: `TankFluidRendering.java` (`FLUID_HEIGHT` 필드 주석, `TankFluidRendering` 클래스 주석의 'the deep look')
- [ ] 채운 정도의 띠: 타일 단계 밖에서 켠 물결 셰이더가 제대로 도는지, 띠 높이로 자른 물 스프라이트가 어색하지 않은지.
  출처: `TankFluidRendering.java` (`TankFluidRendering` 클래스 주석), numbers.md ③
- [ ] 채운 정도의 띠의 색과 빛: 셰이더가 정점 색에 곱한 빛을 써서 띠가 첫 내부 칸의 빛만큼 어두워지는지(어두운 곳에서 바닥 유체와 같은 밝기인지), 북쪽 벽 앞면 위에서 셰이더의 투명도가 어떻게 보이는지.
  출처: `TankFluidRendering.java` (`addFillBand` 메서드 주석, `TankFluidRendering` 클래스 주석의 'its colour and light'), numbers.md ③

**파이프**

- [ ] 파이프 그리기: 연결된 방향의 연결부, 끊긴 방향의 끊김 표시(파이프, 펌프와 탱크 밸브 사이), 다른 유체로 막힌 방향의 표시, 같은 칸의 기본·지하 연결 표시(연결·끊김). 지하 파이프는 공학 렌치나 지하 파이프 아이템을 들었을 때만 보이고 모든 것 위에 그려지는지.
  출처: `src/main/java/devp0tion/mechanics/client/PipeRendering.java` (`PipeRendering` 클래스 주석)

**공학 렌치**

- [ ] 툴팁: 렌치를 들고 칸을 가리켰을 때 툴팁의 위치와 모습, 유체 줄이 나타나기까지의 지연(서버의 답을 기다림).
  출처: `src/main/java/devp0tion/mechanics/client/WrenchTooltip.java` (`WrenchTooltip` 클래스 주석)
- [ ] 모드 키: 조작 설정에 '공학 렌치 모드 전환'이 있고 키를 바꿀 수 있는지, 그 키(기본은 마우스 가운데 버튼)로 기본 모드와 지하 모드가 바뀌는지, 알림이 전환할 때만 나오는지, 렌치를 든 동안 피펫이 작동하지 않는지.
  출처: `src/main/java/devp0tion/mechanics/client/WrenchControls.java` (`WrenchControls` 클래스 주석)
