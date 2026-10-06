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
- **기록 위치**: 이 파일 (기획서·수치표·결정 목록·미결 목록) + 기존 Design canvas에 보드 3개 추가 (수치표, 진행 단계, 수치 미결).
- **결정 번호**: N주기-번호 (예: N1-1). 1차 결정 (1-1~13-6)과 구분.
- **진행 규칙**:
  - 질문 위젯을 띄우기 전에 질문·선택지 문구의 오타를 검수함 (사용자 요청).
  - 설명이 필요하면 설명과 위젯을 같은 턴에 두고, 설명이 너무 길면 아티팩트에 넣음.
- **위임**: 문서·캔버스 작업은 자식 에이전트에 위임.
- **종료 단어**: `끝`

## 수치표

- 행은 1차 세션에서 존재가 결정된 것만 둔다.
- 사용자가 정하기 전의 값은 모두 **`미정`**.
- 괄호 안 번호는 근거 결정 (1차 결정은 decisions.md, N주기 결정은 이 파일).

### 1. 멀티블럭 탱크 용량 (8-1, 8-4, 8-7)

용량 = 기본 용량 (내부 칸 수로 정함) × 테두리 광물 벽 최저 티어의 배율 (8-1)

| 항목 | 값 |
| --- | --- |
| 칸당 기본 용량 | **`미정`** |
| nightsteel·spiderite 위력 비교 방법 (8-7) | **`미정`** |

| 광물 벽 | 티어 = 채굴 위력 (8-4) | 배율 |
| --- | --- | --- |
| copper | 65 | **`미정`** |
| iron | 80 | **`미정`** |
| gold | 95 | **`미정`** |
| demonic | 125 | **`미정`** |
| ivy | 155 | **`미정`** |
| tungsten | 185 | **`미정`** |
| glacial | 200 | **`미정`** |
| mycelium | 230 | **`미정`** |
| ancientfossil | 245 | **`미정`** |
| nightsteel | **`미정`** (무기 피해 비교, 8-7) | **`미정`** |
| spiderite | **`미정`** (무기 피해 비교, 8-7) | **`미정`** |

### 2. 파이프 티어 (9-2, 9-10, 9-11)

재료는 주괴 11종, 티어 기준은 광물 벽과 같음 (9-11). 연결된 망 전체가 가장 낮은 티어의 조건을 따름 (9-10).

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
| nightsteel | **`미정`** (8-7) | **`미정`** | **`미정`** | **`미정`** | **`미정`** |
| spiderite | **`미정`** (8-7) | **`미정`** | **`미정`** | **`미정`** | **`미정`** |

기체 자체는 범위 밖 (TODO, 12-3).

### 3. 펌프 (11-3, 11-4, 11-7, 11-8, 12-1)

| 펌프 | 티어 | 작동 (11-3) | 다룰 수 있는 유체 (12-1) | 끌어오는 양 | 속도 (주기) | 연료 소모 |
| --- | --- | --- | --- | --- | --- | --- |
| 수동 펌프 | 1 | 손으로 작동, 클릭할 때마다 펌프 | 물 (해수·담수) | **`미정`** | **`미정`** | **`미정`** |
| 화력 펌프 | 2 | 통나무 (anylog) 연료 (11-7), 와이어로 켜고 끔 | 용암까지 | **`미정`** | **`미정`** | **`미정`** |
| 고급 화력 펌프 | 3 | 통나무 (anylog) 연료 (11-8), 와이어로 켜고 끔 | 모든 유체 | **`미정`** | **`미정`** | **`미정`** |

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

**재료 미정**

| 요소 | 제작 장소 | 재료 | 수량 | 근거 |
| --- | --- | --- | --- | --- |
| 공학 작업대 | 1티어 작업대 (`workstationduo`) | **`미정`** | **`미정`** | 13-2 |
| 탱크 컨트롤러 | 공학 작업대 | **`미정`** | **`미정`** | 13-1, 13-3 |
| 탱크 밸브 | 공학 작업대 | **`미정`** | **`미정`** | 13-1, 13-3 |
| 유리 블럭 | 공학 작업대 | **`미정`** | **`미정`** | 13-1, 13-3 |
| 렌치 | 공학 작업대 | **`미정`** | **`미정`** | 13-1, 13-3 |
| 지하 파이프 | 공학 작업대 | **`미정`** | **`미정`** | 13-1, 13-3 |

**재료 확정 (1차 결정)**

| 요소 | 제작 장소 | 재료 | 수량 | 근거 |
| --- | --- | --- | --- | --- |
| 광물 벽 (11종) | 공학 작업대 | 해당 광물 주괴 | 주괴 1개 → 벽 1개 | 8-2, 13-1 |
| 파이프 (티어 11종) | 공학 작업대 | 해당 광물 주괴 (11종) | **`미정`** | 9-11, 13-1 (지하 파이프 재료는 13-3에 따라 위 표에서 따로 `미정`) |
| 수동 펌프 | 공학 작업대 | 나무·돌 | **`미정`** | 11-4, 13-1 |
| 화력 펌프 | 공학 작업대 | 구리·철 | **`미정`** | 11-4, 13-1 |
| 고급 화력 펌프 | 공학 작업대 | 데모닉·철 | **`미정`** | 11-4, 13-1 |

## 결정 목록

결정마다 한 줄, N주기 번호 (N1-1, N1-2, …)를 붙인다. 바뀐 결정은 지우지 않고 '대체됨' 표시 후 새 줄을 추가한다.

(아직 없음)

## 미결 목록

태그: `[미정]`, `[문제]`, `[규칙 빈틈]`, `[확인]`, `note`

- `[미정]` 멀티블럭 탱크 용량: 칸당 기본 용량, 광물 벽별 배율 (8-1, 수치표 1).
- `[미정]` nightsteel·spiderite 벽 위력 값과 비교 방법 — 파이프 티어에도 같은 값을 씀 (8-7, 9-11, 수치표 1·2, 참고 V1).
- `[미정]` 파이프 티어별 운송 조건: 운송량·최대 온도·운송 가능 종류·상태 (9-2, 9-11, 수치표 2).
- `[미정]` 펌프 티어별 수치: 끌어오는 양·속도 (주기)·연료 소모 (11-3, 수치표 3).
- `[미정]` 유체 표시 이름 (해수·담수 제외)과 온도 값 (12-4, 12-5, 수치표 4).
- `[미정]` 재료·수량: 공학 작업대·탱크 컨트롤러·탱크 밸브·유리 블럭·렌치·지하 파이프의 재료와 수량, 파이프·펌프의 수량 (9-11, 11-4, 13-2, 13-3, 수치표 5).
- `[확인]` 수동 펌프 (손으로 작동, 11-3)의 '연료 소모' 칸을 어떻게 둘지 (수치표 3).
- `note` 바닐라에 아이템 `wrench` (와이어·논리 게이트용)가 이미 있음. 같은 아이템 stringID를 다시 등록하면 예외가 남 (`GameRegistry.registerObj`) → 모드 렌치는 다른 stringID가 필요 (구현 단계, 참고 V5). 표시 이름 '렌치'와는 별개.

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
