# 도메인 규칙

출처는 Necesse 1.3.3 서버판 `Server.jar` 디컴파일로 확인한 사실이다.

## 엔진 제약 (Claude 조사)

- **D1.** 타일 하나에 오브젝트는 레이어당 1개. 기본 레이어 4개:
  - `base` — 일반 오브젝트·벽·가구
  - `tile` — 카펫·풀 같은 바닥 장식
  - `wallDecor`
  - `tableDecor`

  오브젝트마다 들어갈 수 있는 레이어가 정해져 있다.
- **D2.** 모드가 새 오브젝트 레이어를 등록할 수 있다(`ObjectLayerRegistry.registerLayer`). 클라이언트 전용 모드는 불가. 렌더링·저장 자동 처리 여부는 미검증 — 구현 단계에서 검증 필요.
- **D3.** 오브젝트는 4방향 회전과 다중 타일(`MultiTile`)을 지원한다.
- **D4.** 상태를 가진 오브젝트는 `ObjectEntity`로 구현한다. 서버 틱 초당 20회, 저장·불러오기와 클라이언트 동기화 패킷을 지원한다.
- **D5.** 플레이어 주변·정착지(settlement) 영역 밖 리전은 일정 시간 뒤 언로드된다(기본 약 30초, 설정값 `unloadLevelsCooldown`). 언로드된 곳의 오브젝트는 틱이 돌지 않는다. 정착지 영역은 로드가 유지된다.
- **D6.** 와이어는 타일마다 4색(빨강·초록·파랑·노랑). 오브젝트는 `onWireUpdate`로 신호를 받을 수 있다(압력판·트랩·가로등·TNT 등이 사용).
- **D7.** 액체는 타일이며 양(volume) 개념이 없다. 액체 타일 6종:
  - `watertile`
  - `lavatile`
  - `liquidslimetile`
  - `liquidoozetile`
  - `spiritwatertile`
  - `quicksandtile`

  물은 해안 거리 기반으로 깊이와 해수/담수가 계산된다(`LiquidManager`).
- **D8.** 버킷으로 액체 타일을 퍼면 그 자리가 흙이 되고 해당 액체의 타일 아이템을 받는다(다시 배치 가능). 깊은 바다(깊이 -3 미만)는 불가. 무한 물 버킷이 존재한다.
- **D9.** `ObjectEntity`에 전용 UI 창을 붙일 수 있다(`ContainerRegistry.registerOEContainer`). 마우스 오버 툴팁 가능.
- **D10.** 월드는 2D 탑다운 타일 격자임. 높이 방향으로 블럭을 쌓는 개념이 없으므로, 여러 블럭으로 이루어진 구조는 평면 배치로만 만들 수 있음.

## 세션 중 수집한 규칙

- **S1.** 바닐라 벽 티어(`toolTier`):
  - 0 = 나무 계열(wood, pine, palm, willow, dryad, bamboo), stone, sandstone, swampstone, snowstone, granite, ice, brick
  - 1 = dungeon
  - 2 = deepstone
  - 6 = obsidian, deepsnowstone
  - 7 = basalt
  - 8 = deepswampstone
  - 9 = deepsandstone
  - 10 = crypt, spidercastle, dawn, dusk, ancientruin, raven, arcanic, factory
- **S2.** 바닐라 벽 중 광석·주괴로 만드는 벽은 없음. 나무 외 벽의 재료는 돌류(stone, granite, deepstone 등), 점토(brick), 흑요석(obsidian), 고철(factory), 서리 조각·깃털·마나 등.
- **S3.** 물결 셰이더(`LiquidShader`): 레벨 그리기에서 타일 단계의 액체 목록(`LevelTileLiquidDrawOptions`)에만 적용됨. 모든 타일의 `addDrawables`가 이 목록을 받으므로, 모드 타일(예: 탱크용 바닥타일)도 액체 그림을 제출해 셰이더를 거칠 수 있음. 셰이더는 리전별 액체 데이터 텍스처(깊이·해수·해안·투명도, `LiquidManager.updateTextures`)를 읽기 때문에, 액체가 아닌 칸은 그대로면 육지 값으로 처리됨 → 해당 칸 값을 바꾸는 패치(`@ModMethodPatch`)가 필요할 수 있음. 오브젝트는 정렬 그리기 단계라 셰이더 밖에서 그려짐 — 물 텍스처의 애니메이션 프레임은 빌려 쓸 수 있으나 셰이더 효과는 없음. GLSL 원본은 서버판에 없어 미확인.
- **S4.** 액체 그리기는 3단계: (1) 액체 아래 단계(셰이더 없음) — 액체 밑 지면 타일(`getUnderLiquidTile`)을 일반 텍스처로 그림 (2) 액체 단계(물결 셰이더) — 물 텍스처 4종(담수/해수 × 얕음/깊음, 애니메이션 프레임)을 리전 액체 데이터로 섞어 그림 (3) 액체 위 단계(셰이더 없음) — 다리, 옛 방식에서는 물 위 장식(물 타일은 일부 칸에 둥둥 뜬 스프라이트). 이후 오브젝트가 정렬 그리기 단계에서 그 위에 그려짐. 모드 쪽 연결 지점: `LevelDrawUtils.addTileBasedDrawProcesses`(private, 타일 단계 수집)에 `@ModMethodPatch`로 액체 목록 추가, `LiquidManager.updateTextures`에 패치로 탱크 내부 칸 텍스처 값 지정. 오브젝트의 `addDrawables`는 액체 목록을 받지 않음.
- **S5.** 바닐라 곡괭이(`CustomPickaxeToolItem`: 채굴 위력 toolDps / 곡괭이 티어 toolTier): wood 50/0, copper 65/0, iron 80/0, gold 95/0, frost 110/1, demonic 125/2, runic 140/3, ivy 155/4, quartz 170/5, tungsten 185/6, glacial 200/7, dryad 215/8, mycelium 230/9, ancientfossil 245/10, ice 245/10. nightsteel·spiderite 곡괭이는 없음.
- **S6.** 광석의 toolTier는 광석이 박힌 암석의 티어를 따름(`RockOreObject`). 암석 티어: rock·snowrock 0, graniterock 2, swamprock 3, sandstonerock 4, deeprock 5, deepsnowrock 6, basaltrock 7, deepswamprock 8, deepsandstonerock 9, spiderrock 10, 크립트 nightsteel 바위 4. 그래서 copper·iron·gold는 0~9 여러 티어에 존재하고, demonic은 광석 없이 주괴만 있음.
- **S7.** nightsteel·spiderite 주괴 제작품(FALLEN_ANVIL): 무기(nightrazorboomerang, nightpiercer, phantompopper, phantomcaller / causticexecutioner, arachnidwebbow, webweaver, empresscommand), 갑옷(투구 4종·흉갑·부츠/각반), spideritearrow, spideritearmorstand. 채굴 도구 없음.
- **S8.** 오브젝트는 `canPlaceOnLiquid` 값을 켜면 액체 타일 위에 놓을 수 있음(`GameObject.canPlace`의 "liquid" 검사).
- **S9.** 바닐라에 통나무 아무거나(anylog)를 연료로 쓰는 오브젝트 엔티티가 있음(`AnyLogFueledInventoryObjectEntity`, `AnyLogFueledProcessingTechInventoryObjectEntity`).
- **S10.** 해수/담수는 타일 종류가 아니라 위치로 정해짐: 같은 `watertile`이라도 `LiquidManager.isSaltWater(x, y)`(해안 거리 기반 계산)로 구분됨.

## 멀티블럭 탱크 구조 규칙 (사용자 정의, 규칙 빈틈 해소)

- 이름: 멀티블럭 탱크 (3-1)
- 테두리: 광물 벽·탱크 컨트롤러·탱크 밸브로 된 직사각형 (5-13)
- 내부: ① 전부 유리 블럭 ② 완전히 빔 ③ 바닥 전부 탱크용 바닥타일, 그 위엔 유리 블럭만 (5-5, 5-11)
- 크기: 내부 칸 기준 구조상 최소 ~ 5×5, 가로·세로 각각 (4-1, 7-1)
- 탱크 컨트롤러: 반드시 1개, 모서리 가능 (4-3, 4-5)
- 탱크 밸브: 개수 제한 없음·0개 가능, 모서리 불가 (4-4, 4-5)
- 인식: 블럭·바닥 타일 변경 시 자동 (5-1)
- 용량: 내부 칸 수 기본 용량 × 광물 벽 최저 티어(채굴 위력) 배율, 값 **`미정`** (5-8, 8-1, 8-4)
- 벽 파괴 → 비활성화, 유체 보존, 데이터는 컨트롤러에 (5-9)
- 컨트롤러 파괴 → 유체 소실 (5-10)
- 상태 표시: 컨트롤러 UI 창 + 내부 마우스 오버 툴팁, 툴팁 형식 `유체 이름 현재값/최대값`(커서 오른쪽) (5-4, 6-2)
- 유체 표현: 모든 내부 조건에서 표시, 유리 블럭은 유리 블럭 텍스처·유체는 물결 셰이더 (6-3, 6-7)

## 지하 파이프 레이어 규칙 (사용자 정의, 규칙 빈틈 해소)

- 파이프 전용 새 오브젝트 레이어 (9-1)
- 기본 파이프와 같은 칸에 겹치면 연결 (9-5)
- 탱크 밸브와는 같은 칸일 때만 연결, 펌프와는 연결 안 됨 (9-9)
- 파이프 철거 도구 또는 지하 파이프 아이템을 들었을 때만 보임(9-6, 10-6)
- 별도 아이템으로 배치 (10-3)
- 파이프 철거 도구로만 철거, 곡괭이 불가(10-1, 10-5)
- 벽·오브젝트·액체 타일 아래 어디든 지나감 (10-4)

## 유체 규칙 (사용자 정의, 규칙 빈틈 해소)

- 액체 6종: 물·용암·슬라임·우즈·영혼의 물·유사 (12-1)
- 해수·담수는 다른 유체 (12-2)
- 온도: 유체마다 고정, 값 **`미정`** (12-4)
- 기체: TODO (12-3)
- 펌프 티어별 제한: 수동=물, 화력=용암까지, 고급 화력부터 전부 (12-1)
- 파이프 티어가 운송 가능한 종류·온도·상태를 정함 (9-2)
- 해수·담수 용어 (12-5)
- 펌프가 탱크에서 끌어올 때도 티어 제한 적용 (12-6)
- 탱크 하나·파이프 망 하나에 한 유체만 (12-7)
