# 수치 설계 세션 (2차)

1차 세션 기록: [session.md](session.md) · [domain-rules.md](domain-rules.md) · [decisions.md](decisions.md)

## 기획서

사용자 확인을 받은 기획서다.

- **역할**: 사용자가 설계하고, Claude는 진행자다. 정하지 않은 값은 모두 **`미정`**.
- **범위**: 1차 세션의 미정 수치 전부.
  - 탱크 용량 (칸당 기본 용량·티어 배율)
  - nightsteel·spiderite 벽 위력 값 (무기 피해 비교)
  - 파이프 티어별 운송 조건 (운송량·온도·종류·상태)
  - 펌프 티어별 수치 (끌어오는 양·속도 등)
  - 유체별 온도 값과 이름
  - 재료 (탱크 컨트롤러·탱크 밸브·유리 블럭·렌치·지하 파이프·공학 작업대)
- **범위 밖**: TODO 4건 — 탱크용 바닥타일, 공장, 전기, 기체.
- **고정 결정**: 1차 세션 결정. 단, 필요하면 어느 것이든 다시 열 수 있음 (다시 열 때마다 확인 질문).
- **도메인 규칙**: 기존 [domain-rules.md](domain-rules.md) + 바닐라 수치 조사 (이 파일의 '참고 바닐라 수치') + 세션 중 수집.
- **질문 순서**: 진행 단계 순서 (게임 초반 요소부터 후반 요소로). 언제든 변경 가능.
- **기록 위치**: 이 파일 (기획서·수치표·결정 목록·미결 목록) + 기존 Design canvas('Fluid Architecture')에 보드 3개 추가 (수치표, 진행 단계, 수치 미결) + 별도 아티팩트 'Code Architecture'(코드 구조, N7-5).
- **결정 번호**: N주기-번호 (예: N1-1). 1차 결정 (1-1~13-6)과 구분.
- **진행 규칙**:
  - 질문 위젯을 띄우기 전에 질문·선택지 문구의 오타를 검수함 (사용자 요청).
  - 설명이 필요하면 설명과 위젯을 같은 턴에 두고, 설명이 너무 길면 아티팩트에 넣음.
- **위임**: 문서·캔버스 작업은 자식 에이전트에 위임.
- **종료 단어**: `끝`

## 수치표

단위: 유체 양은 버킷 1개(액체 타일 1칸) = 10 (N2-1)

- 행은 1차 세션에서 존재가 결정된 것만 둔다.
- 사용자가 정하기 전의 값은 모두 **`미정`**.
- 괄호 안 번호는 근거 결정 (1차 결정은 decisions.md, N주기 결정은 이 파일).

### 1. 멀티블럭 탱크 용량 (8-1, 8-4, 8-7)

용량 = 기본 용량 (내부 칸 수로 정함) × 테두리 광물 벽 최저 티어의 배율 (8-1) — **일부 대체됨 (N13-5: 밸브도 최저 티어 계산에 포함)**

용량 = 칸 수 × 40 × 최저 배율 (N4-4)

최저 배율 = 테두리의 광물 벽과 밸브 중 가장 낮은 티어의 배율, 탱크 전체에 하나 (벽마다 따로 적용하지 않음). 밸브 티어 = 제작에 쓴 광물 벽의 티어, 컨트롤러는 최고 티어로 취급해 최저 배율을 낮추지 않음 (N13-5)

| 항목 | 값 |
| --- | --- |
| 칸당 기본 용량 | 40 (N4-1) |
| nightsteel·spiderite 위력 비교 방법 (8-7) | — (순서로 대체, N4-3) |

| 광물 벽 | 티어 = 채굴 위력 (8-4) | 배율 | 벽 채굴 티어 |
| --- | --- | --- | --- |
| copper | 65 | 1 (N4-2) | 0 (N5-1) |
| iron | 80 | 2 (N4-2) | 0 (N5-1) |
| gold | 95 | 3 (N4-2) | 0 (N5-1) |
| demonic | 125 | 5 (N4-2) | 2 (N5-1) |
| ivy | 155 | 8 (N4-2) | 4 (N5-1) |
| tungsten | 185 | 12 (N4-2) | 6 (N5-1) |
| glacial | 200 | 17 (N4-2) | 7 (N5-1) |
| mycelium | 230 | 23 (N4-2) | 9 (N5-1) |
| ancientfossil | 245 | 30 (N4-2) | 10 (N5-1) |
| nightsteel | — (순서로 대체, N4-3) | 38 (N4-2) | 10 (N5-2) |
| spiderite | — (순서로 대체, N4-3) | 47 (N4-2) | 10 (N5-2) |

### 2. 파이프 티어 (9-2, 9-10, 9-11)

재료는 주괴 11종, 티어 기준은 광물 벽과 같음 (9-11). 연결된 망 전체가 가장 낮은 티어의 조건을 따름 (9-10). — **일부 대체됨 (N12-3, N12-4, N13-1)**

용량은 파이프 블럭별로 자기 티어의 운송량 (N12-3). 온도·종류·상태는 망의 가장 낮은 티어 조건을 따르고, 조건을 넘는 유체가 들어오면 그 파이프가 파손 (N12-4, N12-5). 망 = 유체가 실제로 도달한 파이프의 집합, 빈 파이프는 어느 망에도 속하지 않음 (N13-1).

| 재료 (주괴) | 티어 = 채굴 위력 (9-11) | 운송량 | 최대 온도 | 운송 가능 종류 | 상태 (액체·기체) |
| --- | --- | --- | --- | --- | --- |
| copper | 65 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| iron | 80 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| gold | 95 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| demonic | 125 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| ivy | 155 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| tungsten | 185 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| glacial | 200 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| mycelium | 230 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| ancientfossil | 245 | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| nightsteel | 순서상 ancientfossil 뒤 (N4-3) | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| spiderite | 순서상 ancientfossil 뒤 (N4-3) | **`미정`** | **`미정`** | **`미정`** | **`미정`** |

기체 자체는 범위 밖 (TODO, 12-3).

### 3. 펌프 (11-3, 11-4, 11-7, 11-8, 12-1)

| 펌프 | 티어 | 작동 (11-3) | 다룰 수 있는 유체 (12-1) | 끌어오는 양 | 속도 (주기) | 연료 소모 |
| --- | --- | --- | --- | --- | --- | --- |
| 수동 펌프 | 1 | 손으로 작동, 클릭할 때마다 펌프 | 물 (해수·담수) | 클릭당 20 (N3-2) | 클릭 간격 20틱 = 1초 (N3-3) | 해당 없음 (N1-2) |
| 화력 펌프 | 2 | 통나무 (anylog) 연료 (11-7), 와이어로 켜고 끔 | 용암까지 | 20 (N6-1) | 20틱마다 (N6-1) | 통나무 1개 = 5초 (N6-2) |
| 고급 화력 펌프 | 3 | 통나무 (anylog) 연료 (11-8), 와이어로 켜고 끔 | 모든 유체 | 40 (N6-3) | 20틱마다 (N6-3) | 통나무 1개 = 5초 (N6-4) |

전동 펌프 (4티어~, 전기)는 범위 밖 (TODO).

### 4. 유체 (12-1, 12-2, 12-4, 12-5)

| 유체 | 바닐라 타일 (D7, S10) | 표시 이름 | 온도 (유체마다 고정, 12-4) |
| --- | --- | --- | --- |
| 물 (해수) | `watertile` (해수 위치) | 해수 (12-5) | **`미정`** |
| 물 (담수) | `watertile` (담수 위치) | 담수 (12-5) | **`미정`** |
| 용암 | `lavatile` | **`미정`** | **`미정`** |
| 슬라임 | `liquidslimetile` | **`미정`** | **`미정`** |
| 우즈 | `liquidoozetile` | **`미정`** | **`미정`** |
| 영혼의 물 | `spiritwatertile` | **`미정`** | **`미정`** |
| 유사 | `quicksandtile` | **`미정`** | **`미정`** |

### 5. 재료 (레시피) (8-2, 9-11, 11-4, 13-1, 13-2, 13-3)

모드 요소는 모두 공학 작업대에서 제작 (13-1). 공학 작업대 자체의 제작법은 1티어 작업대 `workstationduo`에 둠 (13-2).

**재료 (N10까지 모두 확정)**

| 요소 | 제작 장소 | 재료 | 수량 | 근거 |
| --- | --- | --- | --- | --- |
| 공학 작업대 | 1티어 작업대 (`workstationduo`) | 통나무 10, 아무 돌(anystone) 10, 구리 주괴 10, 철 주괴 10 (N1-1, N8-3) | — | 13-2 |
| 탱크 컨트롤러 | 공학 작업대 | 아무 광물 벽 1, 탱크 밸브 2, 철 주괴 10, 구리 주괴 10, glass 10 (N10-2) | — | 13-1, 13-3 |
| 탱크 밸브 | 공학 작업대 | 아무 광물 벽 1, 아무 파이프 2 (N10-3) | — | 13-1, 13-3 |
| 유리 블럭 | 공학 작업대 | glass 5 → 1 (N10-4) | — | 13-1, 13-3 |
| 렌치 | 공학 작업대 | 철 주괴 10 → 1 (N9-4) | — | 13-1, 13-3 |
| 지하 파이프 | 공학 작업대 | 기본 파이프 1 + 같은 주괴 1 → 1 (N9-2, N10-1) | — | 13-1, 13-3 |

**재료 확정 (1차 결정)**

| 요소 | 제작 장소 | 재료 | 수량 | 근거 |
| --- | --- | --- | --- | --- |
| 광물 벽 (11종) | 공학 작업대 | 해당 광물 주괴 | 주괴 1개 → 벽 1개 | 8-2, 13-1 |
| 파이프 (티어 11종) | 공학 작업대 | 해당 광물 주괴 (11종) | 주괴 1 → 1 (N9-1) | 9-11, 13-1 (지하 파이프 재료는 위 표, N9-2, N10-1) |
| 수동 펌프 | 공학 작업대 | 통나무 20, 아무 돌(anystone) 20 (N3-1, N8-3) | — | 11-4, 13-1 |
| 화력 펌프 | 공학 작업대 | 구리 주괴 10, 철 주괴 10 (N9-3) | — | 11-4, 13-1 |
| 고급 화력 펌프 | 공학 작업대 | 화력 펌프 1, 데모닉 주괴 10, 철 주괴 10 (N9-3) | — | 11-4, 13-1 |

## 결정 목록

결정마다 한 줄, N주기 번호 (N1-1, N1-2, …)를 붙인다. 바뀐 결정은 지우지 않고 '대체됨' 표시 후 새 줄을 추가한다.

- N1-1. 공학 작업대 재료: 통나무 10, 돌 10, 구리 주괴 10, 철 주괴 10 (1티어 작업대 workstationduo에서 제작, 13-2).
- N1-2. 수동 펌프의 '연료 소모'는 해당 없음 (클릭으로 작동, 11-3).
- N2-1. 유체 양의 단위: 버킷 1개(액체 타일 1칸) = 10.
- N2-2. 수동 펌프 재료의 '나무'는 아무 통나무(anylog). 재료 = 통나무 + 돌, 수량 미정.
- N3-1. 수동 펌프 재료: 통나무 20, 돌 20.
- N3-2. 수동 펌프 클릭 1회당 끌어오는 양: 20 (버킷 2개분, N2-1 단위).
- N3-3. 수동 펌프 클릭 간격: 20틱(1초).
- N4-1. 내부 1칸당 기본 용량: 40 (버킷 4개분, N2-1 단위).
- N4-2. 광물 벽 배율: copper 1을 기준으로, copper→iron→gold는 +1씩, demonic부터는 증가값이 +2, +3, …으로 1씩 커짐. 결과(정수): copper 1, iron 2, gold 3, demonic 5, ivy 8, tungsten 12, glacial 17, mycelium 23, ancientfossil 30, nightsteel 38, spiderite 47.
- N4-3. nightsteel·spiderite는 ancientfossil 뒤에 nightsteel → spiderite 순으로 이어짐. 1차 결정 8-7(무기 피해 비교로 위력 값 정함)은 이 순서로 대체되어 위력 값이 필요 없음.
- N4-4. (정리) 용량 = 내부 칸 수 × 40 × 테두리 광물 벽 중 가장 낮은 배율 (8-1, N4-1, N4-2). 예: copper 1칸 = 40, spiderite 25칸 = 47,000. — **일부 대체됨 (N13-5: 밸브도 최저 티어 계산에 포함, 컨트롤러는 최고 티어로 취급)**
- N5-1. 광물 벽 자체를 캘 때 필요한 곡괭이 티어 = 그 광물 곡괭이의 티어(toolTier): copper 0, iron 0, gold 0, demonic 2, ivy 4, tungsten 6, glacial 7, mycelium 9, ancientfossil 10 (S5).
- N5-2. 곡괭이가 없는 nightsteel·spiderite 벽의 채굴 티어 = ancientfossil과 같음(10).
- N5-3. 모드 버전(mod.info): 0.1.0.
- N5-4. 모드 설명 문구(mod.info): "A mechanical engineering mod: pump, pipe and store fluids in multiblock tanks."
- N5-5. 구현 1차 범위(사용자 결정): 프로젝트 뼈대 + 게임과 독립된 핵심 로직·테스트 + 공학 작업대. 광물 벽·탱크 부품·파이프·펌프 오브젝트는 이후.
- N6-1. 화력 펌프 성능 = 수동 펌프와 같음: 20틱(1초)마다 20을 자동으로 끌어옴 (N3-2, N3-3 값 사용).
- N6-2. 화력 펌프 연료: 통나무 1개로 5초(100틱) 작동 → 통나무 1개당 100 (버킷 10개분).
- N6-3. 고급 화력 펌프 성능 = 화력 펌프의 2배, 한 번에 2배: 20틱마다 40.
- N6-4. 고급 화력 펌프 연료 = 화력 펌프와 같음: 통나무 1개로 5초 → 통나무 1개당 200.
- N7-1. 유체를 움직이는 주체는 펌프: 펌프가 파이프 망을 통해 밀어냄. 파이프는 유체를 데이터로만 담고 자체 로직이 없음. 코드 구조: 최상위 클래스 `LiquidStorage`를 두고 펌프·탱크·파이프가 모두 상속. 최적화: 밀어낸 유체로 이미 채워진 파이프는 업데이트하지 않고 가장 마지막(끝단) 파이프만 업데이트. 중간에 파이프가 끊어진 경우도 처리. 망 관리자가 청크(리전) 로딩 시스템과 연동(D5).
- N7-2. 목적지가 여럿이면 균등 분배, 나누어떨어지지 않으면 파이프 경로 거리가 가까운 곳에 더 많이.
- N7-3. 파이프 1칸이 데이터로 담는 양 = 그 파이프 티어의 운송량(값은 파이프 티어 주제에서). 탱크 밸브: 기본은 들어오는 유체만 자동으로 받음, 와이어 신호로 켜고 끔. 자동 내보내기는 TODO.
- N7-4. 목적지가 없거나 모두 가득 차면 펌프는 멈춤(연료도 소모하지 않음).
- N7-5. 코드 구조 아키텍처만 그리는 별도 아티팩트 페이지 "Code Architecture"를 새로 만듦 (설계 캔버스는 사용자가 "Fluid Architecture"로 이름 붙임).
- N8-1. 두 멀티블럭 탱크는 벽을 공유할 수 있음. 공유 벽에는 컨트롤러를 둘 수 없음: 컨트롤러를 설치했을 때 동시에 두 탱크로 인식되면 설치 전에 거부.
- N8-2. 공학 작업대 영문 이름: Engineering Workbench (확정).
- N8-3. 공학 작업대(N1-1)·수동 펌프(N3-1) 재료의 '돌'은 아무 돌(anystone).
- N8-4. 구현 2차 진행: N8 반영, 화력 펌프 수치(N6), LiquidStorage 구조·파이프 망 핵심 로직(N7), 광물 벽 11종(문·창문 없이 벽만). 탱크 부품·펌프·파이프 게임 오브젝트는 이후.
- N9-1. 기본 파이프: 주괴 1개 → 파이프 1개 (주괴 11종 공통, 9-11).
- N9-2. 지하 파이프: 기본 파이프 1개 + 그 기본 파이프를 만들 때 쓴 주괴 1개 (출력 수량 확인 중).
- N9-3. 화력 펌프: 구리 주괴 10, 철 주괴 10. 고급 화력 펌프: 화력 펌프 1개 + 데모닉 주괴 10 + 철 주괴 10.
- N9-4. 렌치: 철 주괴 10 → 렌치 1개.
- N10-1. 지하 파이프 레시피 출력: 1개 (N9-2 확정: 기본 파이프 1 + 같은 주괴 1 → 지하 파이프 1).
- N10-2. 탱크 컨트롤러: 아무 광물 벽 1, 탱크 밸브 2, 철 주괴 10, 구리 주괴 10, 바닐라 glass 10.
- N10-3. 탱크 밸브: 아무 광물 벽 1, 아무 파이프 2 (기본·지하 파이프 모두 포함, 주괴 종류 무관).
- N10-4. 유리 블럭: 바닐라 glass 5 → 유리 블럭 1.
- N11-1. 탱크 밸브가 두 탱크의 공유 벽에 놓이게 되면 설치 거부 (N8-1의 컨트롤러 규칙과 같음).
- N11-2. 탱크를 다시 조립해 용량이 저장량보다 작아지면 초과분은 소실. — 구체화 (N13-4)
- N11-3. 와이어 신호가 들어오면 밸브·펌프는 꺼짐(평소에는 켜짐). 이 내용을 밸브·펌프의 아이템 설명에 첨부.
- N11-4. 펌프가 끌어올 곳이 둘 이상이 되는 자리(액체 타일 위 + 밸브에 붙음, 또는 밸브 2개에 붙음)에는 설치 거부.
- N11-5. 구현 3차 진행: 탱크 시스템(컨트롤러·밸브·유리 블럭 오브젝트와 레시피, 구조 인식, 데이터 저장, UI·툴팁, 유체 렌더링 패치). 파이프·펌프·렌치 게임 오브젝트는 4차(파이프 운송량 결정 후).
- N11-6. 설치가 거부되는 경우는 모두 해당 아이템의 설명에 첨부 (컨트롤러: N8-1, 밸브: N11-1, 펌프: N11-4).
- N12-1. 파이프를 제거(회수)하면 그 칸에 들어 있던 유체는 소실.
- N12-2. 다른 유체가 든 두 망은 연결되지 않음(인접 자동 연결·렌치 연결 모두). — 의미 확정 (N13-2: 설치는 허용, 맞닿는 면만 연결 안 됨)
- N12-3. 파이프 용량은 망 전체가 아니라 파이프 블럭별로 관리: 각 파이프는 자기 티어의 운송량만큼 담음 (1차 9-10의 용량 부분 대체, N7-3 구체화).
- N12-4. 온도·종류·상태 조건은 망의 가장 낮은 티어를 따르되, 조건을 넘는 유체가 들어오면 그 파이프가 파손됨. 단, 유체가 아직 도달하지 않은 파이프는 같은 망으로 취급하지 않음.
- N12-5. 파손된 파이프는 사라지고 드롭 없음.
- N13-1. (점검 #1) '망'은 도달 기준으로 정의: 망 = 유체가 실제로 도달한 파이프의 집합. 유체가 아직 도달하지 않은 파이프(빈 파이프)는 어느 망에도 속하지 않음. 망당 단일 유체(12-7)·다른 유체 망 연결 안 됨(N12-2)·티어 조건(N12-4) 모두 이 정의를 따름 (1차 9-10의 남은 '연결된 망 전체' 문구와 수치표 2 머리말 대체).
- N13-2. (점검 #5) 서로 다른 유체가 만나도 설치는 항상 허용: 두 유체가 맞닿는 면은 자동으로 연결되지 않고 막힌 끝으로 취급. N12-2의 '연결되지 않음'의 의미를 이것으로 확정. 설치 거부가 아니므로 N11-6의 거부 목록에 넣지 않음.
- N13-3. (점검 #7) 다른 블럭(벽·밸브·유리·바닥) 설치나 두 번째 탱크 조립으로 나중에 생기는 충돌은 허용하고, 먼저 있던 쪽 우선으로 처리: ① 기존 밸브·컨트롤러가 공유 벽에 걸리게 되면 원래 탱크에 그대로 속하고, 새 탱크는 그 요소를 자기 것으로 세지 않음 ② 기존 펌프 옆에 밸브를 놓아 공급원이 둘이 되면 펌프는 원래 공급원을 유지. 그 요소 자체를 놓을 때의 설치 거부(N8-1, N11-1, N11-4)는 그대로 적용.
- N13-4. (점검 #8) 탱크가 무효·비활성인 동안은 유체를 전부 보존. 다시 유효한 탱크로 인식되는 순간 그 탱크 용량을 넘는 양은 소실 — 확장 도중 잠깐 생긴 더 작은 유효 사각형이어도 같음. N11-2의 구체화이며 현재 코드 동작과 같음.
- N13-5. (점검 #16) 최저 티어 계산: ① 컨트롤러는 최고 티어로 취급해 최저 배율을 낮추지 않음 ② 밸브는 제작에 쓴 광물 벽의 티어를 가짐. 밸브 아이템은 하나로 두고, 티어는 아이템 데이터에 저장·툴팁에 표시·설치 후에도 유지. 밸브 레시피(N10-3)는 그대로 ③ 배율은 탱크 전체에 하나: 테두리의 광물 벽과 밸브 중 가장 낮은 티어의 배율 (벽마다 따로 적용하지 않음) (1차 8-1, N4-4 일부 대체).

## 미결 목록

태그: `[미정]`, `[문제]`, `[규칙 빈틈]`, `[확인]`, `note`

- `[미정]` 파이프 티어별 운송 조건: 운송량·최대 온도·운송 가능 종류·상태 (9-2, 9-11, 수치표 2).
- `[미정]` 유체 표시 이름 (해수·담수 제외)과 온도 값 (12-4, 12-5, 수치표 4).
- `note` 바닐라에 아이템 `wrench` (와이어·논리 게이트용)가 이미 있음. 같은 아이템 stringID를 다시 등록하면 예외가 남 (`GameRegistry.registerObj`) → 모드 렌치는 다른 stringID가 필요 (구현 단계, 참고 V5). 표시 이름 '렌치'와는 별개.
- `note` 배율 순서는 1차 결정 8-4의 채굴 위력 순서와 같고, nightsteel·spiderite가 그 뒤에 붙음 — 파이프 티어(9-11)도 같은 순서를 따름.
- `[미정]` 탱크 밸브 자동 내보내기 (N7-3, TODO).
- `note` 구현 1차분 완료: 프로젝트 뼈대·핵심 로직(테스트 57개)·공학 작업대, Server.jar 컴파일·서버 로딩 확인 (커밋 0c34ae5).
- `note` 구현 3차분 완료: 탱크 시스템 — 컨트롤러·밸브·유리 블럭 오브젝트와 레시피, 구조 인식, 데이터 저장, UI·툴팁, 유체 렌더링 패치 (N11-5, 커밋 8af85e1).
- `note` 다른 블럭(벽·밸브·유리·바닥) 설치로 공유 벽이 생겨 기존 컨트롤러가 두 탱크에 걸리게 될 때의 처리 (N8-1은 컨트롤러 설치만 거부) → N13-3으로 해소 (먼저 있던 쪽 우선).
- `[확인]` 탱크 부품(컨트롤러·밸브·유리 블럭) 영문 이름과 영문 아이템 설명 (구현 시 임시 번역).
- `[미정]` 구조 점검 미결 ([구조 점검 결과](https://claude.ai/artifact/6PfHzggXbGXm87cxNqZMdN), 27건 중 #1·#5·#7·#8·#16은 N13-1~N13-5로 결정): #2 파이프 파손 대상·연쇄, #3 운송량이 칸당 저장량으로만 쓰임, #4 펌프–밸브 직접 연결의 렌치 끊기, #6 대체된 결정을 참조하는 곳 (수치표 2 머리말은 N13-1로 갱신), #9 리전 언로드와 단일 유체, #10 파이프 비우기, #11 깊은 바다 펌프의 파이프 연결, #12 빈 내부·유리만 판정에 포함되는 레이어, #13 컨트롤러가 부서지는 경로·탱크 옮기기, #14 렌치로 끊은 상태의 저장·초기화, #15 표시·세부 동작 4건, #17 버킷 하나로 무한 유체, #18 용량과 처리량의 배율, #19 주괴 변환과 하위 벽, #20 유리 내부의 이점 없음, #21 데모닉 단계 전 펌프·밸브 끄기, #22 경로 캐시 무효화 범위, #23 망 재구성 비용, #24 탱크 재검증 비용, #25 멀티플레이 동기화 범위, #26 지하 파이프 레이어의 상태 엔티티 없음, #27 5-1 재인식용 범용 변경 훅 없음, 탱크 리전 경계 (D5, 로드 순서에 따라 비활성으로 남을 수 있음).
- `note` 구현 반영 대기 (N13-2, 파이프 구현 차수): 커밋된 코드는 다른 유체 망을 잇게 되는 파이프 설치를 거부함(`WOULD_MIX_FLUIDS`) → 설치는 허용하고 두 유체가 맞닿는 면만 자동으로 연결하지 않도록 바꿔야 함. 망을 연결 기준에서 도달 기준으로 바꾸는 N13-1도 같은 차수에서 반영.
- `note` 구현 반영 대기 (N13-3): 탱크 레지스트리(`TankRegistry`)에서 먼저 있던 탱크 우선 — 나중에 공유 벽에 걸린 기존 밸브·컨트롤러는 원래 탱크 소속을 유지하고 새 탱크는 세지 않음. 펌프의 원래 공급원 유지는 펌프 오브젝트 구현(4차) 때 반영.
- `note` 구현 반영 대기 (N13-5): 밸브 아이템에 재료 광물 벽의 티어를 저장(툴팁 표시, 설치 후 유지)하고 최저 티어 계산에 밸브를 포함, 컨트롤러는 계속 제외(최고 티어 취급). 현재 코드는 광물 벽 칸만 셈 (`TankStructure.validate`의 TODO).
- `note` N13-5 구현 확인: '아무 광물 벽'을 재료로 받는 Necesse 레시피가 소모된 벽 종류를 제작된 밸브 아이템에 넘길 수 있는지 구현 단계에서 확인.

## 참고 바닐라 수치 (조사)

출처: Necesse 1.3.3 디컴파일 소스. 사실만 적음 — 설계 값이 아님.

### V1. 무기 피해 (8-7 nightsteel·spiderite 위력 비교용)

- 기본 피해 = 아이템 클래스의 `attackDamage.setBaseValue(…)`. 강화 단계 값 (`setUpgradedValue`)은 생략.
- enchantCost = `ToolItem(int enchantCost, …)` 생성자 첫 인자 (무기·도구 공통).
- 공격 시간 = `attackAnimTime.setBaseValue(…)` (ms). '—'는 클래스에서 설정하지 않음.

**nightsteel·spiderite 무기** (모두 `FALLEN_ANVIL` 제작, S7)

| 무기 | 주괴 | 종류 | 기본 피해 | 공격 시간 | enchantCost | 제작 주괴 수 | 클래스 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| nightrazorboomerang | nightsteel | 부메랑 | 70 | 300 | 1900 | 5 | `NightRazorBoomerangToolItem` |
| nightpiercer | nightsteel | 대궁 | 124 | 500 | 1900 | 20 | `NightPiercerGreatBowProjectileToolItem` |
| phantompopper | nightsteel | 마법 | 59 | 600 | 1900 | 16 | `PhantomPopperProjectileToolItem` |
| phantomcaller | nightsteel | 소환 | 27 | — | 1900 | 12 | `PhantomCallerSummonToolItem` |
| causticexecutioner | spiderite | 근접 (검 패키지, 대검 루트 테이블) | 55 | 300 | 1900 | 18 | `CausticExecutionerToolItem` |
| arachnidwebbow | spiderite | 활 | 45 | 450 | 1900 | 15 | `ArachnidWebBowToolItem` |
| webweaver | spiderite | 마법 | 37 | 1000 | 1900 | 20 | `WebWeaverToolItem` |
| empresscommand | spiderite | 소환 | 20 | — | 1900 | 25 | `EmpressCommandToolItem` |

**이전 광물의 곡괭이·무기** (`ItemRegistry` + 각 아이템 클래스; '—'는 해당 광물의 그 종류 무기가 바닐라에 없음; '기타'는 일부만 적음)

| 광물 | 곡괭이 채굴 위력 (8-4) | 곡괭이 피해 | 검 | 활 | 대궁 | 기타 | 무기 enchantCost |
| --- | --- | --- | --- | --- | --- | --- | --- |
| copper | 65 | 12 | 20 | 17 | — | — | 200 |
| iron | 80 | 14 | 23 | 20 | — | 대검 50 | 300 |
| gold | 95 | 16 | 26 | 23 | 52 | — | 350 |
| demonic | 125 | 20 | 34 | 30 | — | — | 400 |
| ivy | 155 | 24 | 47 | 52 | 85 | 대검 100 | 850 |
| tungsten | 185 | 28 | 65 | 60 | 120 | 부메랑 60 | 1300 |
| glacial | 200 | 30 | — | 60 | — | 대검 180, 부메랑 38 | 1450 |
| mycelium | 230 | 34 | — | — | 160 | — | 1600 |
| ancientfossil | 245 | 36 | — | — | — | — | (무기 없음) |
| nightsteel | (곡괭이 없음) | — | — | — | 124 | 부메랑 70, 마법 59, 소환 27 | 1900 |
| spiderite | (곡괭이 없음) | — | 55 (causticexecutioner) | 45 | — | 마법 37, 소환 20 | 1900 |

- 공격 시간: 검 300 (copper~tungsten 공통), 활 700 (copper) → 600 (iron·gold) → 500 (demonic~glacial), 대궁 600 (ivy·tungsten·mycelium), gold 대궁 700.
- 곡괭이 enchantCost (`CustomPickaxeToolItem` 일곱째 인자): copper 200, iron 400, gold 450, demonic 600, ivy 700, tungsten 800, glacial 900, mycelium 1000, ancientfossil 1000.
- 도끼 enchantCost (`CustomAxeToolItem`): copper 200 → ancientfossil 1400 (frost·runic·quartz·dryad 포함 등록 순서대로 100씩 증가). 도끼 피해: copper 12 ~ ancientfossil 30.

### V2. 연료 (통나무)

| 오브젝트 엔티티 | 사용 오브젝트 | 통나무 1개 연소 시간 | 비고 |
| --- | --- | --- | --- |
| `AnyLogFueledInventoryObjectEntity` (`getNextFuelBurnTime`) | campfire, cookingpot, roastingstation (`CampfireObjectEntity`가 상속) | 120000 ms = 120초 | 연료 칸 2 |
| `ProcessingForgeObjectEntity` (`AnyLogFueledProcessingTechInventoryObjectEntity` 상속) | forge | 40000 ms = 40초 (`logFuelTime`) | 레시피 1회 8000 ms = 8초 (`recipeProcessTime`) → 통나무 1개로 5회 제련 |

- 시간 단위는 ms (`WorldEntity` 시간). 서버 틱은 초당 20회 (D4).
- anylog 통나무: oaklog, sprucelog, pinelog, palmlog, willowlog, maplelog, birchlog, bamboo, deadwoodlog, dryadlog (`MatItem(500, "anylog")`).

### V3. 스택 크기 (`ItemRegistry`)

| 아이템 | 스택 | 출처 |
| --- | --- | --- |
| 주괴 11종 (copperbar ~ spideritebar) | 250 | `MatItem(250, …)` |
| 광석 (copperore 등) | 500 | `MatItem(500, …)` |
| 액체 타일 아이템 6종 (watertile 등, 버킷으로 얻음) | 250 | `LiquidTile` 생성자 `stackSize = 250` (일반 타일 기본값 500, `GameTile`) |
| stone, granite, sandstone, snowstone, deepstone | 5000 | `StonePlaceableItem(5000)` |
| 통나무 (anylog) | 500 | `MatItem(500, "anylog")` |
| glass | 500 | `MatItem(500)` |
| quartz, obsidian | 250 | `MatItem(250, …)` |
| clay | 500 | `MatItem(500)` |
| upgradeshard, alchemyshard | 1000 | `MatItem(1000, …)` |

주괴 brokerValue (`ItemRegistry.registerItem` 셋째 인자): copper 4, iron 6, gold 10, demonic 10, ivy 12, tungsten·glacial·mycelium·ancientfossil 20, nightsteel 24, spiderite 28.

### V4. 레시피 재료 규모 (`Recipes.java`, 결과 1개 기준 — 다르면 표시)

**1티어 작업대 (`WORKSTATION`, workstationduo)**

- workstationduo: anylog 10 (손 제작도 가능)
- storagebox: anylog 8
- forge: anystone 20
- campfire: anylog 10, anystone 20
- roastingstation: anylog 10
- ironanvil: ironbar 5
- carpentersbench: anylog 10, ironbar 5
- cookingpot: clay 10, ironbar 4
- compostbin: anylog 20, ironbar 5
- incinerator: ironbar 10, clay 10
- grainmill: anylog 20, wool 10, ironbar 10
- bucket: ironbar 3
- woodwall: anylog 2 / stonewall: stone 5 / brickwall: clay 2
- woodfloor: anylog 1 / stonefloor: stone 1
- torch ×4: anylog 1, anysapling 1

**2티어 (`DEMONIC_WORKSTATION`)** — workstationduo를 demonicbar 5로 업그레이드 (`WorkstationDuoObject.getStationUpgrade`)

- demonicbar: copperbar 3 또는 ironbar 2 또는 goldbar 1
- wire ×10: copperbar 1
- wrench (바닐라): ironbar 10 / cutter: ironbar 10
- 논리 게이트 (andgate 등): copperbar 1, wire 5 / timergate 등: ironbar 2, wire 10
- rocklever: anystone 10, wire 5 / stonepressureplate: stone 10, wire 5
- apiary: anylog 20, ironbar 10, honey 5
- landscapingstation: anystone 20, anylog 15, firemone 4
- messageinabottle: piratemap 1, glass 4

**3티어 (`TUNGSTEN_WORKSTATION`)** — demonicworkstationduo를 tungstenbar 8, quartz 4로 업그레이드 (`DemonicWorkstationDuoObject.getStationUpgrade`)

- obsidianwall: obsidian 2
- fireworkdispenser: ironbar 5, wire 10
- tnt: dynamitestick 4, wire 10
- deepstonepressureplate 등: 해당 돌 10, wire 5

**모루 (도구·무기 주괴 수)**

- 업그레이드 사슬: ironanvil → demonicanvil (demonicbar 5) → tungstenanvil (tungstenbar 6, quartz 8) → fallenanvil (anytier1essence 10, upgradeshard 10)
- `IRON_ANVIL`: copper·iron·gold 곡괭이 = 주괴 8, anylog 1 / coppersword = copperbar 10, anylog 1
- `DEMONIC_ANVIL`: demonic·ivy 곡괭이 = 주괴 10
- `TUNGSTEN_ANVIL`: tungsten·glacial·mycelium·ancientfossil 곡괭이 = 주괴 16 / tungstensword = tungstenbar 12
- `FALLEN_ANVIL`: nightsteel·spiderite 무기 = 주괴 5~25 (V1 표), 흉갑 = 주괴 16

**용광로 (`FORGE`)**

- 주괴: 광석 4 → 주괴 1 (copper, iron, gold, ivy, tungsten, glacial, mycelium, ancientfossil, nightsteel, spiderite). demonicbar는 광석 없이 작업대·모루에서 다른 주괴로 만듦 (위).
- glass: sandtile 1 → glass 1 (sandtile은 `ALCHEMY`에서 anystone 5)

### V5. 기타 사실

- 바닐라 아이템 `wrench`가 있음 (`WrenchPlaceableItem`, 와이어·논리 게이트 카테고리, 스택 1, `DEMONIC_WORKSTATION`에서 ironbar 10). 같은 stringID를 다시 등록하면 `GameRegistry.registerObj`에서 "Tried to register duplicate …" 예외.
- 바닐라 재료 아이템 `glass` (유리)가 있음 (V4 용광로 제작법).
- wire는 `DEMONIC_WORKSTATION` 제작품 (copperbar 1 → wire 10).
