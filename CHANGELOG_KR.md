# 변경 로그 (Changelog)

<p align="center">
  <a href="CHANGELOG.md">English</a> | <b>한국어</b>
</p>

## [2.1.0-alpha.1] - 2026-08-30

### 신규 기능 (Added)
- **JEI Unofficial 완전 호환 및 전용 연동 지원**:
  - JEI Unofficial 레시피 뷰어 포크를 공식 지원하여 북마크 그룹(Bookmark Group) 생성 및 크래프팅 모드 연동을 지원합니다.
  - JEI++(Just Enough Calculation)와 JEI Unofficial 연동을 독립된 하위 시스템으로 분리하여 각 모드가 독립적으로 원활하게 동작하도록 개선했습니다.
- **멀티블록 BOM 부품 목록의 JEI / JEI++ 원클릭 전송**:
  - BOM 다이얼로그(`B` 단축키)에서 멀티블록 구조체에 필요한 본체, 케이싱, 해치 부품 목록을 JEI / JEI Unofficial 북마크 그룹 또는 JEI++ 계산 목표로 원클릭 내보내기할 수 있습니다.

### 개선 및 변경 (Changed & Improved)
- **JEI 레시피 슬롯 및 레이아웃 추출 안정화**:
  - JEI 15.20+ 및 JEI Unofficial 환경에서 바닐라 및 모드 레시피의 슬롯 레이아웃과 아이템 목록을 누락 없이 안전하게 파싱하도록 추출 엔진을 개선했습니다.

### 버그 수정 (Fixed)
- **JEI Unofficial 레시피 검색 창의 재료 누락 및 Anvil 오표시 수정**:
  - JEI Unofficial 환경에서 레시피 추가 다이얼로그를 열었을 때 슬롯 파싱 오류로 인해 모든 바닐라 레시피가 입력/출력 아이템 없이 `[Anvil] Anvil`로만 표시되던 인덱싱 오류를 해결했습니다.

## [2.0.0] - 2026-08-30

### 신규 기능 (Added)
- **Systeams 및 증기 보일러 다중 모드 지원 (`SysteamsModAdapter`, `SysteamsRecipeHandler`, `SysteamsModGuiHandler`)**:
  - 머신 카드에서 Systeams 증기 보일러 모드(증기 생산)와 써멀 다이나모 모드(RF 발전)를 클릭 한 번으로 상호 전환할 수 있는 기능 추가.
  - 물, 증류수 및 대체 비등 유체에 대한 보일러 열역학 물리 공식 및 증기 생산/소비 비율 연산 지원.
- **Thermal Series 증강 및 업그레이드 키트 시스템 고도화 (`ThermalAugmentHelper`, `ThermalModAdapter`, `ThermalModGuiHandler`)**:
  - 티어 업그레이드 키트(LV~EV, 1개 장착 시 기존 키트 자동 교체)와 일반 증강(최대 3슬롯)의 장착 규칙을 명확히 분리.
  - Shift+클릭을 통한 3개 일괄 장착 지원, 툴팁 내 잔여 슬롯 및 장착 상태 안내, 증강 배율에 따른 실시간 RF/t 소비량 및 사이클 시간 반영.
- **간체 중국어 (zh_cn) 다국어 번역 지원**:
  - 모든 UI 화면, 다이얼로그, 툴팁, 배지 텍스트에 대한 간체 중국어 번역 추가.

### 개선 및 최적화 (Changed & Improved)
- **우회/분기 노드(Reroute Junction) 유량 균형 및 공급 추적 정밀화 (`FlowBalanceMatrixSolver`)**:
  - 우회 노드를 통한 다중 입력 및 다중 출력 분기 시, 상위 생산자의 실제 가동 효율과 하위 소비자의 포트별 요구량을 정확히 추적하여 유량 병목 및 비율 왜곡 방지.
- **유체 태그 기반 대체 유체 자동 해석 및 레시피 변환 강화 (`EmiRecipeConverter`)**:
  - 유체 태그(Fluid Tag)로 지정된 레시피 입력 포트 변환 시, 설치된 모든 모드의 일치하는 대체 유체 식별자를 자동으로 탐색하여 배선 호환성 확장.
- **캔버스 상호작용 및 다이얼로그 반응성 개선**:
  - 자동 연결 필터 다이얼로그, 상단 툴바, 서머리 오버레이, 노드 카드의 렌더링 및 클릭 반응성 강화.

### 버그 수정 (Fixed)
- **써멀 다이나모 에너지 추출 및 기본 RF 연산 오류 수정**:
  - 특정 Thermal 및 Systeams 레시피 카테고리에서 기본 레시피 RF 에너지를 추출하지 못해 작업 시간이 0틱으로 계산되거나 처리량이 왜곡되던 문제 해결.
- **써멀 증강 중복 장착 시 배율 중첩 왜곡 수정**:
  - 동일한 증강을 여러 개 장착할 때 가산(Additive)되어야 할 배율이 승산(Compounding)으로 잘못 중첩 적용되던 현상 해결.

## [2.0.0-beta.1] - 2026-08-29

### 신규 기능 (Added)
- **블루프린트 메타데이터 작성 및 미리보기 가져오기/내보내기 다이얼로그 (`ExportBlueprintDialog`, `ImportBlueprintDialog`)**:
  - 블루프린트를 내보낼 때 제목, 상세 설명, 태그 등을 작성할 수 있는 전용 모달 다이얼로그 추가.
  - 블루프린트를 가져올 때 노드 수, 기계 수, 연결선 수, 주요 투입물/산출물 및 설명을 미리 확인하고 "새 페이지로 열기" 또는 "현재 페이지에 덮어쓰기"를 선택할 수 있는 미리보기 다이얼로그 지원.
- **스마트 자동 연결 선택 및 필터 다이얼로그 (`AutoConnectFilterDialog`)**:
  - 포트 드래그 후 다른 노드로 자동 연결할 때, 연결 가능한 여러 아이템/유체 중 원하는 자원만 체크박스로 선택하여 부분 연결할 수 있는 필터 다이얼로그 지원.
- **캔버스 화면 맞춤 (Fit to View) 기능 및 단축키 (`Home` / `F`) (`ToolbarWidget`, `HotkeyHudWidget`)**:
  - 상단 툴바 버튼 또는 단축키 `Home` / `F`를 눌러 캔버스에 배치된 모든 노드가 한눈에 들어오도록 줌 레벨과 캔버스 위치를 자동으로 중앙에 맞추는 기능 추가.
- **보드 환경설정 '비율 및 수지' 탭 추가 (`BoardSettingsDialog`)**:
  - 완벽 비율(Harmonize) 자동 맞춤 시 생성되는 최대 앵커 대수 상한(Max Harmonize Scale) 및 잉여 생산량 오차 허용 임계치(Surplus Tolerance)를 직관적으로 조절할 수 있는 설정 탭 추가.

### 개선 및 최적화 (Changed & Improved)
- **대형 워크스페이스 전송 안정성 및 온디맨드 스트리밍 최적화**:
  - 멀티플레이 전용 서버에서 대규모 블루프린트 저장 및 페이지 전환 시 네트워크 패킷 용량 초과를 방지하는 청크 스트리밍 프로토콜 적용.
  - 서버 접속 시 메타데이터를 먼저 수신하고 페이지를 전환할 때 필요한 데이터만 온디맨드로 스트리밍하여 접속 렉 완화.
- **캔버스 연결선(와이어) 호버 및 클릭 감지 성능 최적화 (Wire Spatial Indexing)**:
  - 수백 개의 연결선이 복잡하게 얽힌 대형 공장 플로우차트에서도 마우스 호버 및 클릭 판정을 공간 분할 인덱싱을 통해 렉 없이 즉각 반응하도록 성능 개선.
- **팀원 동시 편집 및 락 해제 반응성 개선**:
  - 팀원이 계산기 보드를 닫거나 서버 접속을 종료했을 때 편집 잠금(Edit Lock)이 지연 없이 즉시 해제되어 다른 팀원이 바로 편집할 수 있도록 개선.

### 버그 수정 (Fixed)
- **JEI 모드 GTCEu 분쇄기 등 전압별 확률형 부산물 포트 오류 수정**:
  - JEI를 통해 레시피를 가져올 때 기본 전압(LV/MV)에서 비활성화되어야 하는 0% 확률 부산물(생닭 분쇄 시 뼈가루/깃털 등)이 노드 출력 포트로 잘못 생성되던 문제 해결.
- **단일 기계 및 멀티블록 티어 전환 시 툴팁 및 연산 수치 왜곡 수정**:
  - 기계 티어 전환 시 내부 물리 파라미터가 즉시 갱신되지 않아 전력 소모량이 일시적으로 잘못 계산되던 현상 해결.
- **팀 페이지 삭제 권한 검증 버그 수정**:
  - 팀 관리자가 아닌 일반 팀원이 팀 페이지를 삭제하려 할 때 클라이언트와 서버 간 상태 불일치가 발생하던 문제 해결.

## [2.0.0-alpha.12] - 2026-08-29

### 신규 기능 (Added)
- **통합 보드 환경설정 모달 다이얼로그 (`BoardSettingsDialog`, `ToolbarWidget`)**:
  - 툴바 우측 상단 `[⚙ Settings]` 버튼을 통해 전역 유체 단위(Auto, Always mB, Always B), 시간 단위(/s, /t, /min, /h), 싱글플레이 게임 일시정지(Pause) 토글, 기본 오버클럭 모드 등을 손쉽게 설정하고 관리할 수 있는 전용 설정창 추가.

### 개선 및 최적화 (Changed & Improved)
- **호버 레시피 핫키 즉시 추가 기능 전면 강화 (`Shift + A`)**:
  - EMI 및 JEI 레시피 상세 화면뿐만 아니라 인벤토리 및 상자 열람 상태의 우측 사이드바 및 즐겨찾기/북마크 패널에서도 아이템에 마우스를 올리고 `Shift + A`를 누르면 해당 레시피가 계산 보드에 즉시 추가되도록 조작성 개선.
  - 검색창 텍스트 입력(EditBox 포커스) 상태를 방해하지 않으면서 다양한 화면 환경에서 `Shift + A` 단축키가 정확하게 반응하도록 안정화.
- **머신 설정 다이얼로그 멀티블록 모델 선택 연동 지원**:
  - 동일 레시피 카테고리를 지원하는 다양한 멀티블록 머신(대형 분쇄기, 대형 채굴기 등)이 설정창 상단 멀티블록 목록에 정상 노출되고 클릭 및 마우스 휠 스크롤로 손쉽게 전환할 수 있도록 개선.
- **`BoardScreen` 내 FTB Library 사이드바 버튼 자동 숨김 처리 (`ClientForgeEvents`)**:
  - 계산기 보드 열람 시 화면 좌상단을 가리던 FTB Quests, FTB Chunks, FTB Teams 등의 사이드바 버튼을 자동으로 숨겨 넓고 깔끔한 캔버스 작업 환경 제공 (EMI `[+]` 레시피 전송 기능은 100% 정상 보존).

### 버그 수정 (Fixed)
- **멀티블록 에너지 해치 및 병렬 해치(Parallel Hatch) 복합 오버클록 연산 버그 수정 (`GTCEuModAdapter`, `GTParallelHatchAddon`, `EnergyHatchHelper`)**:
  - 에너지 해치 장착 시 최대 수용 전압/전류를 초과하는 병렬 오버클럭 제한 및 다중 에너지 해치(Dual Energy Hatch) 티어 상승(1티어 승급) 연산 지원.
  - `GTParallelHatchAddon` 장착 시 기계의 기본 병렬(Base Parallel)과 해치 병렬이 중복 곱연산되던 왜곡 수정.
- **멀티블록 BoM 자재 명세서 선택적 부품(패러랠 해치 등) 자동 케이싱 환원 처리 (`GTCEuBOMHelper`, `MultiblockStructureCatalog`)**:
  - 기본 청사진 3D 형상에 예시로 포함되어 있던 패러럴 제어 해치(Elite Parallel Control Hatch 등)가 미장착 시 BoM에서 자동 제외되고 해당 멀티블록의 기본 구조 케이싱으로 환원(+1)되도록 수정.
  - 레시피상 불필요한 버스/해치 및 선택적 부품들의 지능적 BoM 정규화.
- **JEI / EMI 검색창 타이핑 시 계산기 보드 단축키(`B`) 충돌 버그 수정 (`ClientForgeEvents`)**:
  - 인벤토리, 상자 또는 레시피 뷰어 화면에서 JEI/EMI 검색창에 포커스를 두고 텍스트를 입력할 때 계산기 보드 열기 단축키(`B`)가 가로채져 보드가 강제로 열리던 문제 완전 해결.
- **JEI 환경 레시피 인덱싱 및 다이얼로그 연동 누락 수정 (`JeiRecipeViewerAdapter`)**:
  - JEI 모드 단독 실행 환경에서 레시피 검색 다이얼로그 열람 시 레시피 목록이 비정상적으로 지연되거나 누락되던 수집 로직 안정화.
- **JEI 모드 프로그래밍 회로(Programmed Circuit) 입력 포트 오인 등록 버그 수정 (`JeiRecipeConverter`, `GTCEuRecipeHandler`)**:
  - JEI에서 레시피를 가져올 때 모드 선택용 비소모 회로(`gtceu:programmed_circuit`, `gtceu:integrated_circuit`)가 실제 소비 원자재로 등록되어 `-0/s` 요구 포트가 생성되던 문제 해결.
- **스팀 기계 티어 전환 시 엉뚱한 머신(Large Miner 등)으로 아이콘이 변경되던 오류 수정 (`GTCEuModAdapter`)**:
  - LP/HP 스팀 기계에서 전기 티어로 전환하거나 티어를 변경할 때 올바른 동일 기계의 해당 티어 아이콘으로 안전하게 갱신되도록 해결.
- **다단계 레시피 추출기 및 리플렉션 무결성 강화 (`LayeredRecipeHelper`)**:
  - GTCEu 및 타 모드 레시피 변환 중 발생할 수 있는 레이아웃 누락 및 래퍼 객체 참조 오류 해결.

### 알려진 문제점 (Known Issues)
- **JEI 모드 GTCEu 분쇄기(Macerator) 등 LV/MV 티어에서의 확률형 부산물(Byproduct) 비정상 노출 이슈**:
  - JEI를 통해 레시피를 보드로 가져올 때, 기본 전압 티어(LV/MV)에서 비활성화되어야 하는 일부 GTCEu 레시피(예: 생닭 분쇄 시 뼈가루/깃털 등 0% 확률 부산물)의 출력이 노드 포트에 생성되는 현상이 확인되었습니다. (차기 릴리즈에서 정밀 수정 예정)

## [2.0.0-alpha.11] - 2026-08-28

### 신규 기능 (Added)
- **Phoenix Guilds (Teams) 모드 연동 지원 (`PhoenixGuildsProvider`, `TeamProviderRegistry`, `TeamProviderTest`)**:
  - 안전한 리플렉션(Safe Reflection) 기반으로 Phoenix Guilds ([CurseForge 1612085](https://www.curseforge.com/minecraft/mc-mods/phoenix-guilds) / `phoenix_guilds`) 모드와의 팀/길드 프로바이더 소프트 디펜던시 연동 구현.
  - 길드 소속 플레이어를 자동으로 감지하여 팀 공정 보드를 안전하게 격리하고, 길드 오피서 및 길드장(`GuildRank.OFFICER`, `GuildRank.OWNER`)에게 팀 캔버스 페이지 관리/삭제 권한 부여.
- **인플레이스 대체 레시피 교체 (In-Place Recipe Switching)**:
  - 머신 설정 다이얼로그의 `[🔄 Switch Recipe]` 버튼 또는 노드 우클릭 컨텍스트 메뉴를 통해 이미 캔버스에 배치된 머신 노드를 삭제하지 않고 동일 기계/동일 주요 산출물의 대체 레시피로 즉시 전환할 수 있는 기능 추가.
  - 레시피 전환 시 동일한 아이템 및 유체 포트의 와이어 연결이 끊어지지 않고 자동으로 유지(Smart Wire Preservation)되며, 단축키 `Ctrl+Z` / `Ctrl+Y`로 언제든 실행 취소 및 다시 실행 가능.
- **통합 레시피 뷰어 지원 확장 (JEI / JEI++ 및 바닐라 호환)**:
  - EMI뿐만 아니라 JEI(Just Enough Items) 및 JEI++(Just Enough Calculation) 모드가 설치된 환경에서도 레시피 색인, `[R]`/`[U]` 키를 통한 레시피/용도 조회, 북마크 연동 및 멀티블록 BoM 자재 명세서 등록 지원.
  - 레시피 뷰어 모드가 없는 순수 바닐라 환경에서도 바닐라 레시피 매니저 기반으로 기본 레시피 노드 생성 및 조작 지원.
- **전역 유체 단위 표기 통일 옵션 (Fluid Unit Mode)**:
  - 상단 툴바의 유체 단위 토글 버튼 또는 단축키 `Shift+T`를 통해 캔버스 전체의 유체 유량 표기 방식을 `자동(Auto)`, `항상 mB(Always mB)`, `항상 버킷(Always B)` 중 원하는 방식으로 일괄 통일할 수 있는 옵션 추가.
  - 선택된 유체 단위 설정은 게임 재접속 시에도 유지되도록 클라이언트 설정에 자동 영속화.
- **머신 설정 다이얼로그 5단계 가독성 UI 배율 조절 (Font Scale)**:
  - 머신 설정창 우측 상단의 `[Aa 1.0x]` 버튼을 통해 UI 배율을 5단계(`0.75x`, `0.85x`, `1.0x`, `1.15x`, `1.30x`)로 실시간 조절 지원.
  - 좌/우클릭, 마우스 휠 스크롤, 키보드 단축키 `+`/`-`로 손쉽게 배율을 조정할 수 있으며, 고해상도 모니터나 소형 화면에서도 정밀한 클릭 판정과 가독성 제공.

### 개선 및 변경 (Changed & Improved)
- **페이지 탭 바 오버플로우 클릭 네비게이션 개선**:
  - 다수의 캔버스 페이지가 생성되어 화면 너비를 초과할 때 양 끝에 나타나는 `«`, `»` 인디케이터를 마우스로 직접 클릭하여 탭을 좌우로 스크롤할 수 있도록 인터랙션 개선.
  - 탭 바 우측에 여유 공간(패딩 16px)을 추가하여 마지막 탭 및 `[+]` 신규 탭 생성 버튼이 화살표에 가려지지 않도록 레이아웃 보정.
- **단축키 안내 HUD(Hotkey HUD) 유체 단위 조작 안내 추가**:
  - 좌측 하단 단축키 안내 위젯에 `Shift+T` 유체 표기 단위 순환 단축키 가이드 추가.

### 버그 수정 (Fixed)
- **다수 페이지 생성 시 탭 바 우측 경계 잘림 및 스크롤 불가 버그 수정**:
  - 화면 너비를 초과하는 페이지 탭이 존재할 때 `»` 버튼을 클릭해도 스크롤이 동작하지 않고 마지막 탭의 오른쪽 테두리가 시저(Scissor) 영역 밖으로 잘리던 현상 해결.

## [2.0.0-alpha.10] - 2026-08-27

### 신규 기능 (Added)
- **목표 배치 생산 소요 시간(ETA / Estimated Time) 산출 및 타겟/리라우트 노드 시스템 (`RFC-005`, `ProductionETACalculator`, `NodeTargetBatchEditor`, `NodeProperties`, `ETACalculationTest`)**:
  - 단말 및 리라우트(Reroute) 노드에 원하는 목표 배치 생산 수량(예: `100x`, `1,000x`, `10 B`)을 지정할 수 있는 기능 추가.
  - 상류 공급 노드들의 초당 순 유입 속도를 기반으로 예상 완료 시간(목표 수량 / 유입 속도)을 실시간 산출하여 노드 하단에 `ET: 24m 52s` 배지 렌더링.
  - 노드 호버 시 해당 배치를 완수하는 동안 소비될 전체 전력량(EU) 및 상류 원자재 총 소비량 상세 툴팁 제공.
  - 인라인 클릭을 통한 빠른 수량 편집 및 `Shift + 클릭`을 통한 목표 수량 초기화 지원.
- **키네틱 발전기/모터 EMI 레시피 정식 등록 및 스트레스 유닛(SU) 검색 색인 강화 (`KineticGenerationEmiRecipe`, `CalcBoardEmiPlugin`, `CreateRecipeHandler`, `CreateNewAgeRecipeHandler`, `RecipeSearchEngine`)**:
  - 즐겨찾기(Favorites)에 Large Water Wheel 등의 키네틱 기계를 등록해도 가상 레시피가 연동되지 않던 문제를 EMI 정식 레시피 등록을 통해 해결하고, 검색창에서 `<su`, `<stress`, `large water wheel` 등 출력 및 기계명으로 즉시 검색되도록 색인 지원.
- **자석(Magnet) 및 다중 스택 애드온 클릭 상호작용 개선 (`MachineConfigDialog`, `CreateNewAgeModAdapter`)**:
  - 카탈로그 카드 좌클릭 시 빈 슬롯이 남아있으면 1개씩 순차 장착(+1), Shift+좌클릭 시 12개 슬롯 일괄 채우기(+12) 및 일괄 교체 지원.
  - 우클릭 시 해당 애드온 1개 제거(-1) 및 상단 `Clear Magnets` 버튼으로 전체 초기화 지원.

### 개선 및 변경 (Changed & Improved)
- **기계 설정창 최초 오픈 시 화면 멈춤(Stuttering) 및 렉 원천 제거 (`MachineConfigDialog`, `MachineAddonCatalog`, `MultiblockDetector`, `GTCEuMultiblockScanner`, `CoilHelper`, `TurbineRotorHelper`)**:
  - 설정창 진입 시 100,000개 EMI 레시피 및 10,000개 블록을 전수 선형 순회/리플렉션하던 병목을 카테고리 핀포인트 룩업 및 사전 키워드 스킵으로 최적화하여 1초간 발생하던 멈춤 현상 완전 해결.
  - 언어 코드 동기화 및 백그라운드 프리로드 생명주기 안정화.
- **기계 설정창 애드온 툴팁 지연 렌더링 적용 (`MachineConfigDialog`)**:
  - 애드온 카드의 긴 설명 텍스트가 다이얼로그 경계 밖에서 잘리던 UI 레이어링 버그를 지연 렌더링 방식으로 수정.

### 버그 수정 (Fixed)
- **Star Technology 온실(Greenhouse) 및 팜(Farm) 멀티블록 스레딩/코일 오판정 버그 수정 (`CategoryCapabilityMatrix`, `MultiblockDetector`, `StarTModAdapter`, `GTCEuThreadingTest`)**:
  - 농장/온실 구조물에 장식용 코일 블록이 포함되어 있어 가열 코일 멀티블록으로 오인되거나 기계 전환 시 스레딩 탭으로 자동 이동되던 문제 수정.
- **월드 미진입 및 컨피그/설정 화면 단축키 조작 시 크래시 방어 (`BoardScreen`, `ClientForgeEvents`, `NetworkHandler`)**:
  - 타이틀 화면, 모드 컨피그 화면(Configured 등), 조작 키설정(`KeyBindsScreen`) 등 월드에 진입하지 않은 상태(`mc.player == null`)에서 단축키가 눌렸을 때 보드가 열리며 발생하던 바닐라 `AbstractContainerScreen.containerTick()`의 NPE 크래시를 원천 차단.
  - 일시정지(`PauseScreen`), 옵션(`OptionsScreen`), 조작키 변경(`KeyBindsScreen`), 사망 화면(`DeathScreen`), 모드 컨피그 화면(`*config*`)에서 계산기 열기 단축키가 가로채지지 않도록 예외 처리.
  - `BoardScreen`의 `init()` 및 `containerTick()` 전반에 월드 및 플레이어 유효성 방어 가드를 추가하여 안전하게 종료되도록 보강.
  - 월드 로그아웃(`LoggingOut`) 시 열려 있던 `BoardScreen` 자동 닫기 및 네트워크 연결 종료 상태에서의 패킷 전송 예외 처리.
- **그렉텍 열원심분리기(Thermal Centrifuge) 및 바닐라 제작(Crafting Table) 레시피의 서멀(Thermal) 모드 오판정 버그 수정 (`ThermalAugmentHelper`, `GTCEuModAdapter`, `CreateModAdapter`, `VanillaModAdapter`, `ModAdapterRegistryTest`)**:
  - 기계 이름에 `thermal` 단어가 포함된 그렉텍 기계(Thermal Centrifuge)가 서멀 기계로 오인되어 RF 전력과 `⚡ Thermal` 뱃지로 표시되던 휴리스틱 문자열 검사 오류를 제거하고, 명시적 네임스페이스(`gtceu`, `minecraft`) 우선 검증 체계로 수정.
  - 서멀(Thermal Expansion) 또는 크리에이트(Create)가 바닐라 제작대 레시피에 보조 워크스테이션(Tinker Bench, Mechanical Crafter 등)을 등록했을 때 제작대 노드가 서멀/크리에이트 기계로 오판정되던 문제를 수정하여 바닐라 패시브(`🍃 Passive`)로 정상 판정되도록 수정.
- **HP Steam 모드에서 ULV/LV 전환 시 단일블록 기계 아이콘 복원 버그 수정 (`CategoryCapabilityMatrix`, `GTCEuModAdapter`, `GTCEuSteamProcessingTest`)**:
  - HP Steam 모드 해제 시 멀티블록 워크스테이션으로 강제 전환되던 현상을 수정하여 원래의 티어별 단일블록 기계 아이콘 복원.
- **스트레스 유닛(SU) 포트 드래그 생산자 검색 및 비활성화/중복 키네틱 레시피 수정 (`RecipeSearchDialog`, `RecipeSearchEngine`, `CalcBoardEmiPlugin`, `CreateRecipeHandler`, `CreateNewAgeRecipeHandler`, `RecipeSearchEngineTest`)**:
  - 노드의 스트레스 유닛(Stress Units) 입력 포트를 드래그하여 검색창을 열었을 때 가상 스트레스 ID 색인 누락으로 `No matching recipes found.`가 표시되던 문제를 수정하여 대형 수차, 풍차, 스팀 엔진 등의 발전기가 정상 추천되도록 개선.
  - 검색 목록에서 `[kinetic generation]`과 `[Create Kinetic]`으로 키네틱 레시피가 이중 중복 노출되던 문제 해결.
  - 모드팩에서 비활성화/숨김 처리되었거나 실제 SU 발전기가 아닌 아이템(Star Technology의 `Solar Heating Plate`, `Reinforced Motor` 등)이 레시피 목록에 노출되던 버그 수정.

## [2.0.0-alpha.9] - 2026-08-26

### 신규 기능 (Added)
- **EMI 네이티브 레시피 추가(`[+]`) 버튼 및 드래그 앤 드롭 연동 (`BoardEmiRecipeHandler`, `BoardEmiDragDropHandler`, `CalcBoardEmiPlugin`)**:
  - 계산기 보드가 열린 상태에서 EMI 레시피 뷰어를 볼 때 레시피 우하단의 네이티브 `[+]` 버튼을 클릭하여 해당 레시피를 보드 캔버스에 즉시 노드로 추가 지원.
  - EMI 레시피를 보드 화면으로 직접 드래그 앤 드롭하여 마우스 위치에 노드를 생성하는 인터랙션 지원.
- **멀티블록 구조체 자동 분석 및 자재 명세서 (BOM, Bill of Materials) 시스템 (`RFC-003`, `MultiblockBOMCalculator`, `MultiblockStructureCatalog`, `MultiblockBOMDialog`, `MultiblockBOMEmiRecipe`, `MultiblockBOMTest`)**:
  - 단축키 `B` 및 툴바 `[📦 BOM]` 버튼으로 전체/선택 페이지의 모든 멀티블록 및 단일 기계 자재 명세서 일괄 산출 다이얼로그 추가.
  - 케이싱, 코일, 해치/버스, 컨트롤러 분류 및 스택+잔여량(예: `3 stacks + 48 (240)`) 표기.
  - `[★ Register in EMI]` 버튼을 통해 EMI 가상 레시피 트리에 BOM 결과물을 원클릭 등록하여 EMI 제작 트리 역추적 지원.
  - `[📋 Copy List]` 텍스트 클립보드 내보내기 지원.
  - 듀얼 하위 티어 에너지 해치(`⚡ 1x Normal Energy Hatch ↔ 2x 1-Tier Lower Energy Hatches`) 실시간 토글 지원.
- **에너지 해치(Energy Hatch) 및 하이브리드 해치 오버라이드 시스템 (`RFC-004`, `GTEnergyHatchAddon`, `GTHatchAddon`, `EnergyHatchHelper`, `GTHatchHelper`)**:
  - 멀티블록 전압 티어 및 암페어(1A, 2A, 4A, 16A 등)를 결정하는 에너지 해치 애드온 장착 및 티어 오버클록 상한 자동 결정.
  - 입력/출력 아이템 버스 및 유체 해치(1x, 4x, 9x, 16x) 수용량 기반 동적 I/O 해치 계산 및 증류탑(Distillation Tower) 다중 유체 해치 제약 검증.
- **노드 수평 반전 (Horizontal Node Flip) 기능 (`RecipeNode`, `NodeCardRenderer`, `BoardHotkeyHandler`, `NodeFlipTest`)**:
  - 단축키 `Alt + F` 또는 노드 상단 반전 버튼(`[⇄]`)으로 노드의 입력(좌)/출력(우) 슬롯 방향을 수평 반전하여 복잡한 플로우차트의 와이어 교차 최소화.
- **생명주기 이벤트 버스(Lifecycle Event Bus) 및 애드온 훅 시스템 (`RFC-001`, `RecipeNodeEvent`, `FlowGraphEvent`, `MachineCatalogEvent`, `LifecycleEventBusTest`)**:
  - 캔버스 노드 생성, 수정, 삭제, 계산 전/후, 카탈로그 빌드 시점을 통지하는 Forge 이벤트 버스 연동.
- **그렉텍 대형 증기 보일러(Large Multiblock Boilers) 지원 및 쓰로틀(Throttle) 제어 (`GTBoilerTier`, `GTCEuRecipeHandler`, `GTCEuModAdapter`, `MachineConfigDialog`)**:
  - 대형 청동/강철/티타늄/텅스텐스틸 보일러 레시피(`gtceu:large_boiler`)를 정상 인식하고 대형 청동 보일러 기준 증기 생산량(800 mB/t = 16,000 mB/s) 및 물 소비량(5 mB/t = 100 mB/s, 1:160 비율) 자동 산출.
  - 대형 보일러 티어별 속도 배수 지원: L-Bronze 1.0x (800 mB/t), L-Steel 2.25x (1,800 mB/t), L-Titanium 4.0x (3,200 mB/t), L-Tungstensteel 8.0x (6,400 mB/t).
  - 기계 설정 창(`MachineConfigDialog`) 및 노드 카드에 25% ~ 100% 쓰로틀 슬라이더 및 프리셋 버튼 연동 (쓰로틀 비율에 따른 소요 시간, 연료 소비율, 증기 생산율 비례 스케일링).
- **그룹 프레임(CanvasGroupFrame) 선택 모델, 클립보드 및 실행 취소/다시 실행 완전 통합 (`BoardSelectionModel`, `CanvasGroupFrameRenderer`, `CanvasInteractionHandler`, `NodeClipboard`, `BoardCommand`, `CanvasGroupFrameTest`)**:
  - 마우스 드래그 영역 선택(Marquee Box Selection) 시 프레임 자동 포함 및 헤더 클릭 Shift/Ctrl 다중 선택 지원.
  - 프레임 단독 및 내부 노드 포함 클립보드 단축키(`Ctrl + C`, `Ctrl + V`, `Ctrl + X`, `Ctrl + D`, `Delete`) 지원 (복사/붙여넣기 시 내부 상대 좌표 유지).
  - 프레임 생성, 삭제, 이동, 크기 변경에 대한 실행 취소/다시 실행(`Undo / Redo`) 지원.
  - 다중 선택 상태에서 프레임 헤더 드래그 시 델타 중복 적용(Double-Delta) 방지.
- **Create: New Age 카본 브러시(Carbon Brushes) 발전기 코일 및 자석 BOM 자동 집계 (`MultiblockBOMCalculator`, `CreateNewAgeModAdapter`, `IModAdapter`, `CreateNewAgeTest`)**:
  - `IModAdapter.populateExtraBOMParts` SPI를 통해 카본 브러시 1대당 필수 컴패니언 블록인 `Generator Coil` (`create_new_age:generator_coil`) 1개를 BOM의 `COIL` 카테고리에 자동 산출.
  - 단일블록 및 키네틱 노드에 장착된 모든 애드온(자석 최대 12개, 서멀 증강 키트, 스레딩 등)을 기계 대수와 곱하여 BOM에 자동 집계.
  - 자석(`MAGNET`) 아이템을 `COIL` 카테고리로 분류하여 BOM 내 Coils 탭 및 All 탭에 정상 표시.
- **머플러 해치(Muffler Hatch) 및 유지보수 해치 카탈로그/BOM 지원 (`MultiblockStructureCatalog`, `MultiblockBOMCalculator`, `GTCEuModAdapter`)**:
  - 머플러 해치(LV~MAX) 및 유지보수/자동 유지보수 해치 카탈로그 등록 및 멀티블록 자동설계/BOM 연동.
- **Star Technology 멸균 정화 유지보수 해치 지원 (`StarTAddonCrawler`, `StarTModAdapter`, `MachineAddonTest`)**:
  - `start_core:sterile_cleaning_maintenance_hatch` (멸균 정화 유지보수 해치) 유지보수 애드온 지원 및 다국어 툴팁/명칭 추가.

### 개선 및 변경 (Changed & Improved)
- **백그라운드 데이터 인덱싱 및 메모리 점유율 개선**:
  - 대규모 레시피 및 멀티블록 구조체 색인 중 발생하던 일시적인 화면 끊김 및 메모리 사용량 최적화.
- **플로우 그래프 직렬화 페이로드 경량화 (`FlowGraphSerializer`)**:
  - 기본값 필드 생략을 통해 청사진 및 NBT 저장 데이터 크기 최적화.
- **UI 레이아웃 및 툴바 정렬 개선 (`BoardScreen`, `ToolbarWidget`)**:
  - 좌상단 툴바 여백 및 즐겨찾기 독(Favorites Dock) 배치 조정.

### 버그 수정 (Fixed)
- **자동 연결(Auto Connect) 시 리라우트 정션 우회 중복 연결 방지 (`ToolbarWidget`)**:
  - 리라우트 정션 허브를 통해 연결된 기계 간에 자동 연결 실행 시 불필요한 직결 와이어가 중복 생성되던 현상 수정.
- **대형 보일러 레시피 소요 시간 이중 가속 버그 수정 (`GTCEuRecipeHandler`, `GTCEuModAdapter`)**:
  - 소형 보일러 승격 가속 배수가 대형 보일러 전용 레시피에 중복 적용되어 사이클 시간이 0.05초(1틱)로 왜곡되고 용암 소모량이 폭증하던 연산 오류 해결.
- **검색창 및 모달 다이얼로그 텍스트 입력 중 'E' 키/단축키 간섭 버그 수정 (`RecipeSearchDialog`, `BoardScreen`, `MultiblockBOMDialog`, `GlobalBalanceDashboardDialog`)**:
  - 검색창 및 다이얼로그 텍스트 상자에 문자(E, B, T, F, J 등) 입력 시 인벤토리 닫기 키가 작동하여 화면이 닫히거나 전역 캔버스 단축키가 실행되던 키 이벤트 라우팅 오류 해결.

---

## [2.0.0-alpha.8] - 2026-08-25

### 신규 기능 (Added)
- **Star Technology 모드 및 스레딩 헬릭스(Threading Helix) 시스템 연동 (`StarTModAdapter`, `StarTAddonCrawler`, `StarTTurbineHelper`, `GTThreadingHelix`, `NodeThreadingConfig`, `MachineConfigDialog`, `NodeCardRenderer`)**:
  - 스레딩을 지원하는 모든 그렉텍 멀티블록 기계에 대해 기계 설정 창(`MachineConfigDialog`) 전용 `[🧵 Threading]` 서브 탭 추가 (Generalis, Velocitas, Efficienta, Parallelismus, Filum 포인트 배분).
  - 스레딩 빌더와 상단 `Active Addons` 슬롯 간 실시간 양방향 동기화 지원 (헬릭스 장착 시 종합 스탯 뱃지 및 `8x OpV Weaving Thread Helix`와 같이 수량 연동).
  - 애드온 아이콘 슬롯 및 캔버스 카드 트레이에 수량 뱃지(예: `8`) 렌더링 지원.
  - 플라즈마 터빈 모델 다변화 지원: 대형 플라즈마 터빈(LPT, 1x), 슈프림 플라즈마 터빈(SPT, 6x), 냥세인 플라즈마 터빈(NPT, 12x).
  - Star Technology 멀티블록 부스팅 특성(Traits) 지원: 윤활유 부스팅(+25% / +50% EU/t, 이황화텅스텐 소모) 및 냉각제 부스팅(+75% / +150% EU/t, 초상태 헬륨-3 / 오가네손 안정화 BEC 소모).
- **그렉텍 초기 증기 가공 기계(저압 청동 LP / 고압 강철 HP) 지원 및 증기 직결 소모 모드 (`SteamMode`, `RecipeNode`, `CategoryCapabilityMatrix`, `GTCEuGuiHandler`, `MachineConfigDialog`)**:
  - 증기 시대 가공 기계(분쇄기, 압축기, 합금 제련기, 화로, 추출기, 암석 분쇄기 등)를 워크스테이션으로 갖는 모든 그렉텍 레시피에 대해 증기 직결 소모 모드(`LP Steam`, `HP Steam`) 추가.
  - **그렉텍 증기 변환 공식 적용**: 1 EU = 2 mB Steam (2 L Steam).
  - **저압 청동 증기 (LP Steam)**: 가공 시간 2.0x (0.5x 속도), 전력망 비연결(0 EU/t), BaseEUt * 2 mB/t 증기(`gtceu:steam`) 유체 입력 슬롯 활성화.
  - **고압 강철 증기 (HP Steam)**: 가공 시간 1.0x (1.0x 기본 LV 속도), 전력망 비연결(0 EU/t), BaseEUt * 2 mB/t 증기(`gtceu:steam`) 유체 입력 슬롯 활성화.
  - **노드 카드 및 기계 설정 UI 연동**: 노드 카드 티어 버튼 순환(`[LP Steam] ↔ [HP Steam] ↔ [LV] ↔ ...`) 및 기계 설정 모달창(`MachineConfigDialog`)에서 원클릭 프리셋 전환 지원.
  - **보일러 직결 및 자동 비율 맞춤(Auto-Ratio)**: 증기 보일러(`gtceu:steam_boiler`) -> 증기 가공 기계로 유체 와이어를 직접 연결하여 `Shift + Connect` 대수 자동 최적화 완벽 지원.
- **즐겨찾기 상호작용 및 실시간 동기화 개선 (`RecipeSearchDialog`, `FavoritesDockWidget`)**:
  - 레시피 검색창의 각 검색 결과 행에 즐겨찾기 별표(⭐) 토글 버튼 추가 및 행 우클릭 즐겨찾기 등록/해제 지원.
  - 즐겨찾기 독(Favorites Dock)에서 항목 우클릭 시 즐겨찾기 즉시 제거 기능 추가.
  - 기계(워크스테이션) 아이템을 즐겨찾기했을 때 해당 기계에서 가공 가능한 모든 레시피가 검색창의 `[⭐ 즐겨찾기]` 필터에 정상 연동되도록 개선.

### 개선 및 변경 (Changed & Improved)
- **GTCEu 코어 및 Star Technology 아키텍처 모듈 분리 (`StarTModAdapter`, `ModAdapterRegistry`, `IModAdapter`)**:
  - Star Technology 고유의 기계 및 스레딩 로직을 `IModAdapter` SPI 구조 기반의 `StarTModAdapter`로 완전히 격리하여 모드 간 의존성 결합도 해소.
- **4GB 저메모리 환경을 위한 대규모 힙 메모리(RAM) 및 GC 최적화 (`RecipeSearchEngine`, `DynamicAddonCrawler`, `ClientForgeEvents`, `CategoryCapabilityMatrix`)**:
  - `SearchableRecipe` 구조를 플라이웨이트(Flyweight) 패턴으로 전면 리팩토링하고 `String.intern()` 풀링을 적용하여 8만 개 이상의 대규모 모드팩 레시피 인덱스 메모리 점유량을 **~320MB에서 ~14MB로 95% 이상 대폭 절감**.
  - 동적 애드온 크롤러 스캔 시 중복 아이템(`Set<Item>`)을 즉시 필터링하여 수십만 개의 불필요한 `ItemStack` 객체 할당 98% 제거.
  - 월드 퇴장(`ClientPlayerNetworkEvent.LoggingOut`) 시 레시피 검색 캐시, 기능 매트릭스, 애드온 카탈로그를 즉시 비우고 `System.gc()`를 호출하여 4GB 메모리 환경에서 월드 재로딩 시 발생하던 `OutOfMemoryError` 및 데이터팩 로드 실패 현상 원천 차단.
  - `FavoritesDockWidget`의 매 프레임 리스트 복제 할당 제거 및 `findRecipesForFavorite` 캐싱을 통해 렌더링 루프의 불필요한 GC 오버헤드와 스터터링 현상 해소.
- **가상 FE 아이템 슬롯 제거 및 전력 체계 정돈 (`CreateNewAgeRecipeHandler`, `CreateRecipeHandler`, `CreateNewAgeModAdapter`)**:
  - 발전기 코일, 탄소 브러시, 모터 등에서 중복 표시되던 가상 `FE` 아이템 입출력 슬롯 제거.
  - 슬롯에는 실제 물리 자원(회전력 SU, 재료, 유체)만 표시되고, 전력 수치는 노드 헤더 및 요약 오버레이에만 전력망 단위로 집계되도록 정돈.
- **Create 모드 포장된 팬(Encased Fan) 가공 시간 고정 적용 (`CreateModAdapter`, `CreateGuiHandler`)**:
  - Create 모드의 원작 고증에 따라 팬 블래스팅(Blasting), 팬 워싱(Splashing/Washing), 스모킹(Smoking), 헌팅(Haunting)의 가공 시간이 회전 속도(RPM)에 의해 단축되지 않고 고정 시간(기본 7.5초)으로 유지되도록 수정 (RPM은 바람 사거리 및 소비 SU에만 영향).
  - 팬 가공의 생산 속도를 올리려면 팬의 회전 속도를 올리는 것이 아니라 **팬의 대수(`대수: N`)**를 늘려야 함을 툴팁으로 안내.
- **그렉텍 증기 보일러 및 증기 생산 공정 완벽 연동 (`GTCEuRecipeHandler`, `GTCEuModAdapter`, `SysteamsModAdapter`)**:
  - `SysteamsModAdapter`가 `gtceu:steam_boiler` 카테고리를 가로채던 라우팅 버그 수정.
  - 그렉텍 증기 보일러(`gtceu:steam_boiler`) 레시피 등록 시 연료(용암 양동이, 석탄, 목탄 등) 및 물(`minecraft:water`) 소비와 증기(`gtceu:steam`) 생산량을 그렉텍 공식 비율(소형 청동 보일러 기준 120 L/s = 6 mB/t 증기, 1 mB 물 -> 160 mB 증기)로 정확히 자동 산출.
  - 용암 양동이 등 용기형 연료 사용 시 빈 양동이(`minecraft:bucket`) 부산물 자동 반환 지원.
  - 고압 강철 보일러(3x), 대형 청동 보일러(8x), 대형 강철 보일러(15x), 대형 티타늄 보일러(26.6x), 대형 텅스텐강 보일러(40x) 등 워크스테이션 변경 시의 연소 속도 및 증기 배출량 비례 스케일링 지원.
- **레시피 추가 단축키('A') 제거 (`ClientForgeEvents`, `KeyBindings`)**:
  - EMI/JEI 북마크 및 키 바인딩 충돌을 방지하기 위해 화면 단축키로 레시피를 추가하던 기능 제거.

### 버그 수정 (Fixed)
- **GTCEu 터빈 로터 재질 NBT 영속화 및 틴트 복원 (`GTRotorAddon`, `MachineAddon`, `TurbineRotorHelper`)**:
  - 월드 재접속 또는 메인 메뉴 이동 후 터빈 로터의 고유 재질 및 색상이 기본 뉴트로늄 텍스처로 초기화되던 버그 수정.
  - `MachineAddon` 직렬화 시 `ItemStack`의 전체 NBT(`GT.PartStats: {Material: ...}`)를 보존하고, 누락 시 동적 복구 로직 구축.
- **플라즈마 터빈 특성(Traits) 인식 및 유체 렌더링 수정 (`GTCEuAddonCrawler`, `StarTAddonCrawler`, `GTTurbineHelper`, `StarTTurbineHelper`, `IngredientRenderer`)**:
  - `isTurbine` 에너지 타입 조건 완화 및 `isCompatibleStarTTrait` 모델 판정 개선으로 SPT/NPT 부스팅 특성이 `[📜 Traits]` 탭에 정상 표시되도록 수정.
  - 부스팅 유체(`bec_og`, `superstate_helium_3`, `tungsten_disulfide`)의 네임스페이스 동적 조회를 통해 `AIR` 텍스처로 폴백되던 오류 수정.
- **스레딩 빌더 서브 탭 텍스트 겹침 수정 (`MachineConfigDialog`)**:
  - 서브 탭 버튼 레이블(`Sup`, `Spd`, `Par`, `Thrd`) 축약으로 텍스트 겹침 문제 해결.

---

## [2.0.0-alpha.7] - 2026-08-23

### 신규 기능 (Added)
- **Create: New Age 모드 연동 (`CreateNewAgeModAdapter`, `CreateNewAgeGuiHandler`, `CreateNewAgeRecipeHandler`, `CreateNewAgeAddonCrawler`, `CreateMagnetAddon`)**:
  - **발전기 코일 및 탄소 브러시 계산**:
    - 발전기 코일 링에 최대 12개의 자석 아이템 장착 및 Shift-클릭 일괄 장착 지원.
    - 발전기 코일 기본 부하(`24.0 SU/RPM`) 및 자석 강도에 따른 회전 부하(((24.0 + Strength) * RPM)) 계산.
    - 모드 설정(`suToEnergy`)을 런타임에 조회하여 실제 발전량(FE/t = Strength * RPM * suToEnergy) 계산.
    - 고글 스타일 GUI 헤더 및 에너지 통계(Base Stress, Total Stress, Efficiency, FE/t) 렌더링.
  - **모터 및 모터 확장기 계산**: 기본, 고급, 강화 모터의 SU 출력 및 FE 소비량 연산과 확장기 배율 지원.
  - **키네틱 과부하(Overstressed) 처리 (`FlowGraphSolver`)**: 회전력 결핍 시 즉시 정지(`0.0 efficiency`, `0 FE/t`, `0 SU/s`)하도록 계산 처리.
- **GTCEu 핵융합로(Fusion Reactor) 시뮬레이션 및 점화 에너지 집계 (`RecipeNode`, `FlowGraphSolver`, `SummaryOverlay`, `NodeBadgeRegistry`)**:
  - 레시피의 `eu_to_start` (점화 필요 EU) 데이터 추출 및 NBT 저장.
  - 점화 에너지에 따른 핵융합 티어(Mk1 <= 160M, Mk2 <= 320M, Mk3 > 320M EU) 및 최소 전압 티어(LuV/ZPM/UV) 자동 결정 및 전압 하한 클램핑.
  - 핵융합 반사판(`GTReflectorAddon`, T1~T3) 애드온 장착 및 스펙 연동.
  - 노드 카드 상단에 점화 버퍼 뱃지 표시.
  - 우측 요약 오버레이(`SummaryOverlay`)에 플로우차트 내 모든 핵융합로의 티어별 대수 및 총 점화 버퍼 합산(`⚛ Fusion Start Buffer`) 집계 및 툴팁 렌더링.
- **레시피 검색창 입출력 전용 접두사 및 가이드 패널 (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - `in:`, `input:`, `>` : 투입 재료 한정 검색.
  - `out:`, `output:`, `<`, `^` : 생산 결과물 한정 검색.
  - **검색창 좌측 프리픽스 가이드 독(Prefix Guide Dock)**:
    - 8개 핵심 접두사(`@`, `#`, `[`, `>`, `<`, `!`, `|`, `"`)를 표시하는 좌측 패널 추가.
    - 접두사 버튼 클릭 시 검색창에 자동 입력되는 빠른 삽입(Quick Insert) 지원.
    - 화면 해상도에 따른 반응형 중앙 정렬 레이아웃 적용.

### 개선 및 리팩토링 (Changed & Improved)
- **애드온 연역적 분석 구조 개선**:
  - 문자열 파싱 방식을 제거하고 공식 Java 인터페이스, 결정론적 NBT(`AugmentData`), 런타임 설정 조회를 사용하도록 변경.
  - `DynamicAddonCrawler` 및 `MachineConfigDialog` 다형성 구조 리팩토링.
  - Thermal Augment 최대 12슬롯 장착 지원.
- **코드 품질 및 Javadoc 표준화**:
  - 소스 코드 Javadoc 영문 표준화 및 주석 정리.

---

## [2.0.0-alpha.6] - 2026-08-23

### 신규 기능 (Added)
- **Create 모드 키네틱 시스템 및 스트레스 유닛(SU) 연동 (`CreateModAdapter`, `CreateRecipeHandler`, `CreateGuiHandler`)**:
  - 대형 물레방아, 물레방아, 풍차 베어링, 증기 기관, 핸드 크랭크, 크리에이티브 모터, 전기 모터 등 키네틱 발전기의 발전량 계산 지원.
  - 32 RPM 기준 회전 속도(RPM)에 따른 가공 시간 및 스트레스 소비량(기본 SU * RPM / 32) 비례 스케일링 적용.
  - 키네틱 노드 `create:stress_units` 입출력 포트 추가 및 와이어 연결, Auto-Ratio 자동 비율 계산 지원.
  - Create 가공 레시피(압축, 분쇄, 제분, 교반, 절단, 연마 등)를 SU 소비자 검색 목록에 색인.
  - Create Crafts & Additions의 알터네이터 및 전기 모터 SU/FE 변환 지원.
- **중계 및 분기점 노드 (`RecipeNode`, `FlowGraph`, `FlowGraphSolver`)**:
  - 32x32 크기의 무비용(0 EU/t, 0초) 중계 노드(Reroute & Junction Node) 추가.
  - 상류/하류 연결에 따른 입출력 규격 자동 지정.
  - 연결선(Bezier Wire) 더블클릭 시 해당 위치에 분기점 노드 자동 삽입.
  - Auto-Ratio 계산 시 중계 노드를 통한 유량 전달 지원.
- **캔버스 그룹 프레임 및 스티커 메모 (`CanvasGroupFrame`, `CanvasStickyNote`, `FrameEditDialog`, `NoteEditDialog`)**:
  - 8가지 색상의 그룹 프레임(`CanvasGroupFrame`) 추가 (헤더 드래그 일괄 이동, 크기 조절, 프레임 내 노드 모듈화 지원).
  - 다중 노드 선택 후 `Ctrl + G` 입력 시 바운딩 박스 기반 그룹 프레임 생성.
  - 캔버스 독립 스티커 메모(`CanvasStickyNote`) 추가 (텍스트 자동 줄바꿈, 색상 변경, 더블클릭 편집).
  - 프레임 내외 드래그 시 포함/제외 자동 처리.
  - 그룹 프레임 및 메모를 포함한 복합 모듈 패키징 및 펼치기 지원.
- **캔버스 4버튼 퀵 액션 마커 ([🔍] [🔀] [🖼] [📝]) (`BoardScreen`, `CanvasInteractionHandler`)**:
  - 캔버스 빈 공간 클릭 또는 와이어 드롭 시 4개 액션(레시피 검색, 분기점 노드, 그룹 프레임, 스티커 메모) 버튼 표시.
- **노드 카드 제어기 및 다중 에너지 지원 (`EnergyType`, `NodeCardRenderer`, `NodeWidget`)**:
  - `EnergyType` 모델 추가 (`ELECTRIC_EU`, `ELECTRIC_FE`, `HEAT_OR_SELF`, `KINETIC_SU`, `MANA`).
  - 써멀 보일러(`HEAT_OR_SELF`): 전압 티어 및 EU/t 표기 대신 보일러 배너와 증기 생산량 표기 적용.
  - 써멀 다이나모(`ELECTRIC_FE`): 다이나모 배너 및 RF/t 표기 적용, 4 RF = 1 EU 비율로 요약 계산 반영.
- **인터랙티브 튜토리얼 갱신 (`TutorialStep`, `TutorialManager`, `TutorialOverlay`)**:
  - 중계 노드, 그룹 프레임, 퀵 액션, 하드웨어 설정창, 복합 모듈 기능을 포함하는 7단계 튜토리얼 코스로 개편.
- **Systeams 런타임 리플렉션 연동 (`SysteamsModAdapter`, `ThermalModAdapter`)**:
  - Systeams 설정(`STEAM_RATIO_*`, `SPEED_*`) 및 레시피를 런타임에 조회하도록 연동.
  - 증기 다이나모(`systeams:steam`)를 증기 소비 발전기로 분류.

### 개선 및 버그 수정 (Fixed & Changed)
- **애드온 설정창 로딩 속도 개선 및 실시간 동기화 (`DynamicAddonCrawler`, `MachineConfigDialog`)**:
  - UI 오픈 시 동기 레시피 순회를 제거하고 레지스트리 검색과 백그라운드 NBT 검색으로 분리하여 오픈 지연 해소.
  - 장착된 애드온 삭제, 전체 삭제(Clear All), 카탈로그 카드 우클릭 제거 시 카탈로그 캐시 갱신 처리.
- **다이나모 및 보일러 스펙 계산 개선 (`ThermalAugmentHelper`, `DynamicAddonCrawler`)**:
  - `ThermalFuel`, `SteamFuel` 클래스 상속 계층 및 Forge 태그, KubeJS NBT 태그(`DynEA`, `DynP`, `DynEM`)를 기반으로 스펙을 계산하도록 수정.
  - KubeJS 업그레이드 키트(`kubejs:lv/mv/hv/ev_upgrade_kit`, `arc_kit`, `mci_kit`) 색인 및 비활성화 아이템 제외 처리.
- **컨텍스트 레시피 검색 필터링 수정 (`RecipeSearchDialog`)**:
  - 특정 아이템/유체 검색 상태에서 기계 카테고리(`[chemical reactor]`) 검색 시 일치하지 않는 레시피가 노출되던 문제 수정.
- **단일블록 기계 설정창 클릭 처리 수정 (`MachineConfigDialog`)**:
  - 단일블록 써멀 기계 및 보일러에서 키트/애드온 카드 클릭이 동작하지 않던 문제 수정.
- **다이나모 및 보일러 툴팁 수정 (`BoardTooltipRenderer`)**:
  - 다이나모 호버 시 소비량으로 표기되던 툴팁을 발전량(`Total Power Generation: +400.00 RF/t`)으로 수정.
  - 보일러 호버 시 증기 생산량(`Total Steam: +30,000.00 mB/s`) 툴팁 추가.
- **복합 모듈 노드 카드 전력 표기 수정 (`NodeCardRenderer`)**:
  - 복합 모듈 카드 2행에서 순 발전/소비 전력 수치(`+320.00 EU/t (Gen)` 등)가 누락되던 렌더링 조건문 수정.

---

## [2.0.0-alpha.5] - 2026-08-22

### 신규 기능 (Added)
- **EMI 기본 레시피(Default Recipe) 우선 정렬 및 아이콘 표시 (`FavoritesDockWidget`, `RecipeSearchDialog`)**:
  - EMI에서 설정한 기본 레시피(`Ctrl + 좌클릭`)를 즐겨찾기 서브 메뉴 및 검색 결과에서 우선 정렬되도록 반영.
  - 검색창 및 즐겨찾기 메뉴에서 기본 레시피에 별표(`★`) 배지와 하이라이트 테두리 적용.
- **공백 포함 카테고리 검색 지원 (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - `[gas turbine]`, `[large chemical reactor]` 등 공백이 포함된 대괄호 카테고리 검색 지원.
  - 괄호 없이 검색어를 입력해도 카테고리 구문 일치 항목이 상단에 배치되도록 개선.
- **보드 열람 팀원 현황(Presence) 및 툴팁 표시 (`TeamPresenceTracker`, `WorkspaceTabBarWidget`)**:
  - 보드를 열고 있는 팀원 수를 상단에 표시하고, 마우스 호버 시 팀원 닉네임과 열람 중인 탭 페이지를 툴팁으로 안내.
  - 개인 보드와 팀 보드 탭 전환 시 열람 상태가 갱신되도록 연동.

### 개선 및 버그 수정 (Fixed & Changed)
- **멀티플레이 팀 워크스페이스 격리 및 LAN 지원 (`FTBTeamsProvider`, `ClientWorkspaceState`)**:
  - FTB Teams 파티 및 개인 플레이어의 팀 UUID를 분리하여 타 팀 워크스페이스와의 간섭 방지.
  - LAN 환경(`IntegratedServer.isPublished()`)에서 협업 워크스페이스 동기화 지원.
- **편집 잠금 상태(Lock Badge) 갱신 및 플레이어 이름 표시 (`WorkspaceTabBarWidget`, `BoardScreen`)**:
  - 상단 잠금 배지가 현재 활성 탭 페이지의 상태를 추적하도록 수정.
  - 잠금 배지 및 알림에 UUID 대신 플레이어 닉네임(`Locked by <이름>`)이 표시되도록 개선.
- **팀 페이지 삭제 권한 검증 (`FTBTeamsProvider`, `TeamProviderRegistry`)**:
  - FTB Teams 랭크(`OWNER`, `OFFICER`) 및 LAN 호스트 여부를 확인하여 팀 관리자 권한을 가진 플레이어만 팀 페이지를 삭제할 수 있도록 서버 검증 추가.
- **즐겨찾기 독 로딩 처리 개선 (`FavoritesDockWidget`, `RecipeSearchDialog`)**:
  - 즐겨찾기 독 로딩을 전체 검색 인덱싱과 분리하여 EMI 로딩 완료 시 즉시 표시되도록 개선.
  - 백그라운드 인덱싱 대기 로직을 정규 EMI API 확인 방식으로 단순화.
- **상단 메뉴 빈 영역 조작 및 툴팁 위치 보정 (`CanvasInteractionHandler`, `WorkspaceTabBarWidget`)**:
  - 상단 헤더 및 툴바의 빈 공간을 클릭/드래그할 때 캔버스 조작이 가능하도록 처리.
  - 폰트 렌더링 글리치(`\uFE0F`) 수정 및 상단 툴팁이 화면 위로 잘리지 않도록 위치 보정.
- **검색 결과 0건 시 프리즈 문제 수정 (`RecipeSearchDialog`)**:
  - 검색 결과가 없을 때 `render()`에서 검색이 반복 호출되던 문제 수정.
  - 병렬 처리 구간에서 스레드 안전하지 않은 호출을 분리하고 검색 디바운스 적용.
- **EMI 오버레이 핸들러 정리 (`CalcBoardEmiOverlayHandler`)**:
  - 사용되지 않는 EMI 오버레이 핸들러 클래스 제거.

---

## [2.0.0-alpha.4] - 2026-08-21

### 신규 기능 (Added)
- **즐겨찾기 도크(Favorites Dock) 추가 (`FavoritesDockWidget`, `BoardScreen`)**:
  - 보드 좌측 접이식 패널을 통해 즐겨찾기 등록된 아이템을 클릭하거나 드래그하여 노드로 배치할 수 있는 기능 추가.
- **즐겨찾기 레시피 탐색 및 EMI 프리뷰 연동 (`FavoritesDockWidget`, `RecipeHoverPreviewRenderer`)**:
  - 즐겨찾기 항목 호버 시 생산 레시피 목록 및 EMI 프리뷰 카드를 표시하고, 프리뷰 내 슬롯 호버 툴팁 및 R/U 키 조회 지원.
  - 서브 패널 간 커서 이동 시 호버 상태가 유지되도록 이동 영역 보정.
- **단계별 로딩 상태 및 진행률 표시 추가 (`MachineConfigDialog`, `RecipeSearchDialog`, `FavoritesDockWidget`)**:
  - 머신 하드웨어 설정창, 레시피 검색창, 즐겨찾기 도크에 초기 인덱싱 단계(Phase) 및 진행률 바 표시 추가.
- **대괄호 카테고리 검색 지원 (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - 검색창에 `[smelting]`, `[pyro]`, `%smelting` 등을 입력하여 카테고리 및 기계 기준으로 레시피를 필터링할 수 있도록 지원.
- **카테고리 필터 연동 (`FavoritesDockWidget`, `RecipeFilterConfig`)**:
  - 필터 다이얼로그에서 비활성화한 카테고리가 즐겨찾기 레시피 탐색 및 자동 노드 선택에서 제외되도록 반영.

### 개선 및 버그 수정 (Fixed & Changed)
- **GTCEu 고티어 코일 스탯 공식 수정 (`CoilHelper`, `MachineAddon`)**:
  - `Abyssal Alloy Coil` 등 고티어 및 커스텀 코일의 Cracker 전력 할인율(`0.07x`), Pyrolyse 가공 속도, LCR, Multi Smelter 병렬 계산식을 GTCEu 공식에 맞게 수정.
  - `ICoilType.getTier()`를 우선 참조하도록 변경하여 티어 역산 오차 수정.
- **레시피 검색 성능 개선 (`RecipeSearchDialog`, `RecipeSearchEngine`)**:
  - 검색 시 발생하던 불필요한 리스트 복사를 제거하고 병렬 처리를 적용하여 검색 반응 속도 개선.
- **검색 결과 가중치 및 정렬 개선 (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - 검색어 입력 시 이름 및 카테고리 일치도가 높은 레시피가 우선 표시되도록 랭킹 점수 계산 로직 개선.
- **EMI 레시피 화면 `[+]` 버튼 연동 (`CalcBoardEmiOverlayHandler`)**:
  - EMI `RecipeFillButtonWidget` 클릭 또는 단축키 입력 시 레시피를 보드에 추가하고 보드 화면으로 전환되도록 지원.
- **`BoardScreen` 화면 계층 수정 (`BoardScreen`, `BoardContainerMenu`)**:
  - `Screen` 상속 구조를 정리하여 인벤토리 관련 사이드바 아이콘 오버레이가 보드 화면에 겹쳐 표시되던 문제 수정.
- **언어 파일 정리 (`en_us.json`, `ko_kr.json`)**:
  - `en_us.json` 중복 키 정리 및 한국어/영어 리소스 키 동기화.

---

## [2.0.0-alpha.3] - 2026-08-21

### 신규 기능 (Added)
- **CategoryCapabilityMatrix 연역 분석 엔진 도입 (`CategoryCapabilityMatrix`, `CategoryCapability`)**:
  - `EmiRecipe`의 내부 슬롯 구성, 그렉텍 자바 객체 리플렉션(`GTRecipe`), Forge `Capability` 및 태그 분석을 통해 기계가 가진 특성을 런타임에 안전하게 추출.병렬 해치, 유지보수 해치, 써멀 증강에 대한 결정론적 수용 능력 판별 지원.
  - 싱글블록, 멀티블록, 터빈, 발전기, 여과기 카테고리를 포괄하는 전용 단위 테스트 슈트(`CategoryCapabilityMatrixTest`) 추가.
- **워크스테이션 기반 멀티블록 수용 능력 연역 매핑 (`MultiblockDetector`, `EmiRecipeConverter`)**:
  - `multiblock_info` 구조체 스캔을 통해 대형 화학 반응기(LCR), 열분해로, EBF, 분해로 등 코일 멀티블록을 자동 식별.
  - 연역된 카테고리를 `MultiblockDetector.registerCoilCategory` 및 `registerTurbineCategory`와 양방향 동기화.
- **써멀 기계 업그레이드 킷(1개 제한/교체) 및 일반 증강 3슬롯 중복 장착(Stacking) 지원 (`RecipeNode`, `MachineConfigDialog`, `ThermalAugmentHelper`)**:
  - 기계당 업그레이드 킷(LV~EV, 6x~48x 병렬)은 단 1개만 장착되며, 다른 킷 클릭 시 기존 킷이 자동으로 교체됩니다.
  - 일반 증강(ARC, MCI 등)은 최대 3슬롯 내에서 동일 증강의 중복 장착(Stacking)을 지원합니다. (예: 3x EV MCI = 4.096x 연료 에너지 / 순수 NBT `AugmentData.Type: Dynamo_Fuel` 태그 파싱).
  - 카탈로그 카드 배지(`✔ x2`, `✔ x3`, `3/3`), 마우스 좌클릭(+1)/우클릭(-1) 및 상단 활성 슬롯 클릭 개별 해제 기능을 지원합니다.

### 개선 및 변경 (Changed)
- **휴리스틱 기반 애드온 감지 로직 완전 제거 (Rule 5 애드온 스펙 결정론적 연역 원칙)**:
  - 코드베이스 전반에서 툴팁 텍스트 파싱, 아이템 이름 검사, ID 부분 문자열 매칭(`contains("rotor")`, `contains("coil")`, `id.getPath()`) 휴리스틱을 완전히 제거.
  - 공식 태그(`gtceu:circuits/*`, `thermal:upgrade_kit`), NBT 실수/정수 데이터(`AugmentData`), 런타임 리플렉션을 통한 결정론적 수치 추출로 전환 (`CoilHelper`, `TurbineRotorHelper`, `ParallelHelper`).
- **반응형 다이얼로그 로딩 오버레이 & 실시간 상태 갱신 UX (`MachineConfigDialog`, `RecipeSearchDialog`)**:
  - 백그라운드 인덱싱 진행 중 섹션 단위의 깔끔한 다크 로딩 오버레이와 애니메이션 표시.
  - 베이킹 완료 즉시 `[♨ Coil]`, `[⚡ Parallel]`, `[🔧 Maint]` 탭 및 카탈로그 카드가 창을 껐다 켤 필요 없이 실시간 자동 갱신(Live Auto-Populate)되도록 개선.
- **레시피 검색 인덱스 병렬화 및 초고속화 (250x 속도 향상, 2분 -> 1초 미만) (`RecipeSearchDialog`, `RecipeSearchEngine`)**:
  - 전체 레시피 인덱싱 시 O(N^2) 중복 순회를 제거하고 멀티코어 `parallelStream`을 도입하여 170,000+개 레시피 인덱싱 시간을 1초 미만으로 단축했습니다.
  - 유체 타입 판정 시 레지스트리 맵 조회 대신 `FluidEmiStack` 인스턴스 검사로 최적화했습니다.
- **동적 애드온 크롤러 20ms 즉시 추출 및 비활성 모드 아이템 누출 차단 (`DynamicAddonCrawler`)**:
  - 무거운 리플렉션 루프를 걷어내고 `EmiRecipe.getOutputs()`에서 NBT가 보존된 `EmiStack`을 직접 추출하도록 개선하여 크롤링 시간을 20ms 미만으로 단축했습니다.
  - Forge 아이템 레지스트리 무조건 스캔을 제거하여 비활성화된 더미 써멀 아이템(`thermal_extra` 등)이 노출되는 문제를 원천 차단했습니다.

### 버그 수정 (Fixed)
- **대형 화학 반응기(LCR) 및 멀티블록 코일 탭 누락 버그 해결 (`CategoryCapabilityMatrix`, `MultiblockDetector`)**:
  - GTCEu에서 싱글블록과 멀티블록의 레시피 카테고리가 분리되어 있어 발생하던 LCR 코일 미노출 문제를 전수 연역 매트릭스를 통해 완전 해결.
- **EMI 및 RecipeManager 비동기 생명주기 동기화 (`CalcBoardEmiPlugin`, `ClientForgeEvents`, `RecipeSearchDialog`)**:
  - 월드 접속 시 백그라운드 비동기 폴링(200ms 간격)을 도입하여 EMI 워커 스레드 빌드 완료 시점에 맞추어 매트릭스 사전 베이킹 수행.
  - 조기 `bake(null)` 호출로 인해 카테고리 매트릭스가 0개로 비워지던 레이스 컨디션 버그 해결.

---

## [2.0.0-alpha.2] - 2026-08-20

### 신규 기능 (Added)
- **6단계 대화형 튜토리얼 개편 및 전용 더미 레시피 도입 (`TutorialManager`, `RecipeSearchDialog`)**:
  - 튜토리얼을 6단계로 전면 개편: 1단계(보일러 추가), 2단계(드래그 앤 검색 터빈 스폰 및 자동 배선), 3단계(와이어 끊기 실습), 4단계(Shift-드래그 1:1 비율 자동 맞춤), 5단계(공정 요약 및 Ctrl+G 복합 모듈 압축), 6단계(튜토리얼 완료).
  - 전용 튜토리얼 탭(`🎓 Tutorial`)을 통한 유저 캔버스 보호 및 0ms 즉시 로딩되는 맞춤형 더미 레시피(`스팀 터빈 (튜토리얼)`, `보일러 (튜토리얼)`, `증기 엔진 (튜토리얼)`)를 제공하여 렉 없이 깔끔한 온보딩 지원.
- **아이템/유체 타입 분리 인덱싱 및 검색 가중치 개선 (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - 레시피 인덱스를 유체 ID와 아이템 ID로 엄격히 분리하여 유체 포트 드래그 시 아이템 레시피가 혼입되는 현상 방지.
  - 유체 ID 완벽 일치(+1,000점) 및 발전기/가공기 카테고리 우선 가중치 알고리즘 적용.

### 개선 및 변경 (Changed)
- **단축키 체계 및 가이드 문서 정비 (`GuideDialog`, `en_us.json`, `ko_kr.json`, `README.md`, `README_KR.md`)**:
  - 복합 모듈화 버튼 삭제 및 단축키 전환에 맞춰 인게임 가이드북 5장 및 튜토리얼 안내 문구를 `Ctrl + G`로 전면 일원화.
  - README.md / README_KR.md 문서 전반의 이모지 제거, 건조한 기술 문서 톤 적용 및 동적 애드온 크롤러 내용 보강.

### 버그 수정 (Fixed)
- **검색창 자동 배선 시 튜토리얼 2단계 전환 누락 수정 (`RecipeSearchDialog`)**:
  - 검색창에서 노드 스폰 및 자동 와이어 연결 시 `TutorialManager.onWireConnected()` 이벤트 호출이 누락되어 3단계로 넘어가지 않던 현상 수정.
  - 자동 배선 완료 직후 캔버스 위젯 및 베지어 곡선 즉시 갱신(`rebuildWidgets`) 처리.

---

## [2.0.0-alpha.1] - 2026-08-20

### 신규 기능 (Added)
- **멀티플레이어 팀 공유 워크스페이스 아키텍처 구축**:
  - **클라이언트-서버 실시간 동기화 프로토콜**: 양방향 네트워크 패킷(`C2S`/`S2C`)을 통해 멀티플레이어 서버에서 팀원들과 동일한 공정 차트를 실시간으로 함께 확인하고 협업할 수 있도록 시스템을 구축했습니다.
  - **모듈식 멀티 팀 프로바이더 백엔드 (`ITeamProvider`, `TeamProviderRegistry`)**:
    - **FTB Teams** 소프트 디펜던시 완벽 연동 (리플렉션을 통한 런타임 안정성 보장).
    - **바닐라 스코어보드 팀(Vanilla Scoreboard)** 및 단독 월드 공용 워크스페이스 자동 폴백 지원.
  - **세분화된 페이지별 편집 권한(Lock) & 충돌 방지 (`WorkspaceLockManager`, `S2CLockResultPacket`)**:
    - 캔버스/기계 조작 시 자동으로 해당 페이지의 편집 권한(Lock)을 획득하여 동시 편집으로 인한 데이터 충돌을 원천 방지합니다.
    - 페이지 전환, 창 닫기 또는 시간 초과 시 자동으로 락이 반환됩니다.
  - **실시간 팀원 접속 상태 및 뷰포트 표시 (`S2CBroadcastPresencePacket`)**:
    - 현재 워크스페이스에 접속 중인 팀원들의 닉네임과 열람 중인 페이지 탭을 상단에 실시간으로 표시합니다.
  - **무마찰 디바운스 자동 동기화 (Figma/Google Docs 스타일)**:
    - 3초 비활성 디바운스 자동 저장 및 탭 이동/창 닫기 시 즉시 커밋 트리거.
    - 서버 측 커밋 스쿼싱(Squashing)을 통해 히스토리 로그가 불필요하게 비대해지는 것을 방지했습니다.
  - **저장 기록 열람 및 개인 보드로 복제 (Fork) (`RecentSavesDialog`)**:
    - 팀 보드의 과거 커밋 히스토리를 확인하고, 과거 특정 시점의 공정을 개인 보드(`[👤 Personal Board]`) 탭으로 완벽하게 복제/불러올 수 있습니다.
  - **팀 오너 및 서버 관리자(Admin) 전용 페이지 삭제 권한 제어 (`C2SDeleteTeamPagePacket`)**:
    - 팀 페이지 삭제 권한을 **팀 오너(Owner), 관리자(Officer) 및 서버 OP**로 제한하여 무단 삭제를 방지하고, 마지막 1개 남은 페이지는 보존되도록 보호합니다.
- **싱글플레이어 게임 일시정지 토글 도입 (`BoardScreen`, `BoardManager`, `ToolbarWidget`)**:
  - 싱글플레이 툴바에 **`[⏸ Pause: ON]` / `[▶ Pause: OFF]`** 토글 버튼을 추가했습니다 (`isPauseScreen` 동적 연동).
  - 보드를 열어 계산하는 동안 게임을 일시정지할지, 아니면 백그라운드 공장이 계속 돌아가게 둘지 자유롭게 선택할 수 있습니다.
- **파라메트릭 & 조건부 레시피 검색 엔진 구축 (`RecipeSearchEngine`, `RecipeSearchDialog`)**:
  - 다중 키워드 AND(`&` / 공백), OR(`|`), NOT(`!`), 모드 접두사(`@gtceu`), 태그(`#logs`), 기계/카테고리(`[pyrolyse_oven]`), 따옴표 정확 일치(`"..."`)를 지원하는 고성능 검색 엔진을 구현했습니다.
  - 기계 이름, 카테고리 다국어 번역명, 레시피 ID, 입/출력 품목 전체를 실시간 인덱싱하여 원하는 공정을 즉각 찾아냅니다.
- **컨텍스트 드래그 앤 검색 & 자동 노드 배선 도입 (`CanvasInteractionHandler`, `RecipeSearchDialog`)**:
  - 출력/입력 포트에서 와이어를 끌어 빈 캔버스에 놓으면 해당 품목을 소비(출력 출발) 또는 생산(입력 출발)하는 레시피 검색창이 자동 필터링되어 팝업됩니다.
  - 레시피 선택 시 마우스 위치에 노드가 생성됨과 동시에 **매칭 포트 간 와이어가 1클릭으로 자동 연결**됩니다.
  - `Shift + 드래그` 시 1:1 완벽 자동 비율 맞춤(Auto-Ratio) 및 `Ctrl + Z` 일괄 취소를 지원합니다.

### 개선 및 변경 (Changed)
- **환경 적응형 컴팩트 상단 헤더 레이아웃 (`BoardScreen`, `WorkspaceTabBarWidget`, `PageTabBarWidget`)**:
  - 싱글플레이어나 모드가 없는 서버에서는 불필요한 팀 협업 상단 바를 완전히 숨기고 페이지 탭을 최상단으로 끌어올려 넓은 캔버스 작업 공간을 확보했습니다.
  - 실시간 자동 저장 도입에 따라 공간을 차지하던 수동 저장 버튼을 정리했습니다.
- **정숙한 백그라운드 협업 동기화**:
  - 일상적인 백그라운드 동기화 및 락 획득 시 뜨던 불필요한 화면 하단 토스트 알림을 제거하고 중요 충돌/오류 상황에서만 알림이 뜨도록 개선했습니다.

### 버그 수정 및 다국어 검증 (Fixed)
- **다국어(i18n) 100% 무결성 동기화**:
  - 영문(`en_us.json`)과 한국어(`ko_kr.json`)의 340개 전체 번역 키 및 포맷 토큰 일치율 100% 달성.

---

## [1.0.6] - 2026-08-20

### 신규 기능 (Added)
- **대상 페이지 종합 수지 대시보드 (Global Balance Dashboard) 도입 (`GlobalBalanceDashboardDialog`, `GlobalBalanceAggregator`)**:
  - 복수의 프리셋 페이지(Page/Tab) 간 종합 생산량, 소비량 및 총 전력 수지를 실시간으로 집계하는 인터랙티브 대시보드 모달을 추가했습니다 (`B` 단축키 및 툴바 `[📊 종합 수지]` 버튼).
  - **포함 대상 페이지 선택**: 좌측 사이드바에서 포함할 페이지를 자유롭게 체크/언체크하여 특정 공정 구역들만의 수지를 선택 집계할 수 있습니다.
  - **순 유량 탭 필터링 & 검색**: 결손(Deficit), 잉여(Surplus), 균형(Balanced) 탭 및 검색창을 통해 수천 개의 공정 품목 중 병목 원인을 즉각 식별할 수 있습니다.
  - **품목별 페이지 기여도 드릴다운 (`ItemContributionPopup`)**: 품목 클릭 시 해당 품목을 어느 페이지에서 얼마나 생산하고 소비하는지 상세 breakdown 팝업을 제공합니다.
  - **종합 전력 수지 & 전압 티어 뱃지**: 전체 공정의 순 전력(EU/t), 발전/소비 세부 내역, 최고 전압 티어(Tier) 뱃지 및 툴팁을 제공합니다.

### 개선 및 변경 (Changed)
- **포트 유량 표기 개선 및 색상 코딩 (`FormatUtil`, `NodeCardRenderer`)**:
  - 단위계와 공급/소비량 표기에서 슬래시(`/`) 중복으로 인한 가독성 저하를 해결하기 위해 `+3.48M -3.2M/s +` 형태의 명확한 플러스/마이너스 및 색상 코딩 표기로 개편했습니다 (잉여: 청록/회색, 결손: 금색/빨간색).
  - 긴 유량 텍스트가 카드 경계 밖으로 벗어나지 않도록 노드 카드의 기본 최소 너비를 `235px`에서 `245px`로 확장했습니다.
- **GTCEu Modern 공식 / Star Technology Fork 빌드 전환 지원 (`build.gradle`, `gradle.properties`)**:
  - `gradle.properties`의 `gtceu_variant` 설정을 통해 일반 공식 GTCEu Modern(CurseMaven)과 Star Technology 전용 포크(FlatDir) 간 의존성을 자유롭게 전환할 수 있도록 지원합니다.

### 버그 수정 (Fixed)
- **글로벌 대시보드 품목 행 텍스트 겹침 현상 수정 (`GlobalBalanceDashboardDialog`)**:
  - 고정 좌표로 인해 유량 수치와 생산/소비 세부 내역이 겹쳐서 출력되던 현상을 우측 -> 좌측 동적 체이닝 레이아웃으로 개편하여 글자 겹침을 원천 해결했습니다.
  - 대시보드 기본 가로 너비를 `580px`로 확장하고, 기계 대수가 수억 대 단위로 커질 때 SI 접두사로 깔끔하게 축약 표기되도록 개선했습니다.

---

## [1.0.5] - 2026-08-20

### 신규 기능 및 성능 개선 (Added & Improved)
- **대규모 공정 LOD (Level of Detail) 렌더링 도입 (`NodeCardRenderer`, `BoardScreen`)**:
  - 캔버스 줌 레벨이 0.28 미만으로 축소될 때, 무거운 3D 아이템 모델과 텍스트를 생략하고 경량 2D 카드 블록으로 자동 전환하여 1,200개 이상의 노드가 배치된 대규모 공정 차트에서도 프레임 저하 없이 원활하게 탐색할 수 있도록 개선했습니다.
  - LOD 모드 전환 전후의 입/출력 포트 연결점 좌표를 정렬하여 줌 인/아웃 시 와이어 연결선 위치가 어긋나지 않도록 처리했습니다.
- **연결선(와이어) 및 펄스 점 단일 배치 렌더링 가속 (`ConnectionRenderer`)**:
  - 수백 개의 베지에 곡선 파이프라인과 흐름 애니메이션 점들을 단 1번의 Draw Call(`QUADS`)로 전송하여 렌더링 부하를 대폭 줄였습니다.

### 버그 수정 (Fixed)
- **3D 아이템 모델 Z-Index 뚫고 나옴 현상 수정 (`BoardScreen`, `NodeCardRenderer`)**:
  - 마인크래프트 전역 3D 렌더 버퍼에 쌓인 아이템 모델이 다른 UI 창이나 노드 위로 튀어나오던 현상을 방지하기 위해 노드별 레이어 버퍼 플러시 및 깊이 버퍼 클리어 처리를 적용했습니다.
- **월드 로딩 시 `Cannot retrieve all materials before registration` 크래시 수정 (`DynamicAddonCrawler`)**:
  - `MaterialRegistryManager`에 대한 비안전 리플렉션 호출을 제거하여 월드 진입 시 발생하던 `IllegalStateException` 크래시를 수정했습니다.
- **가이드북 탭 텍스트 잘림 현상 수정 (`GuideDialog`, `ko_kr.json`, `en_us.json`)**:
  - 가이드북 다이얼로그 및 사이드바 너비를 확장하고 탭 텍스트를 간결화하여 카테고리 텍스트가 잘리지 않도록 개선했습니다.

### 문서 및 번역 정리 (Changed)
- **문서 및 가이드 설명 정제 (`README.md`, `README_KR.md`, `ko_kr.json`, `en_us.json`)**:
  - README 및 인게임 가이드의 설명을 담백하고 명확한 기술 문서 형태로 정리했습니다.

---

## [1.0.4-fix2] - 2026-08-20

### 버그 수정 (Fixed)
- **월드 로딩 및 EMI 등록 단계의 GTCEu 머티리얼 레지스트리 조기 접근 크래시 수정 (`DynamicAddonCrawler`, `CalcBoardEmiPlugin`)**:
  - EMI 등록 단계(`EmiPlugin.register`) 등 타이틀 화면 로딩 중에 백그라운드 프리캐싱이 돌아 GTCEu 머티리얼 레지스트리(`GTRegistries.MATERIALS`)를 조기 참조하여, 월드 진입 시 `Cannot retrieve all materials before registration` 에러와 함께 튕기던 크래시를 완벽히 해결했습니다.
  - 인게임 월드에 완전히 접속하기 전에는 GTCEu 내부 레지스트리를 일체 건드리지 않도록 안전 격리 가드를 추가했습니다.
- **레시피 검색창 첫 진입 시 빈 화면/미일치 오류 해결 (`RecipeSearchDialog`)**:
  - EMI 레시피 인덱싱이 비동기로 완료되었음에도 첫 진입 시 캐시 동기화가 어긋나 `No matching recipes found.`가 뜨던 문제를 해결했습니다.
  - 백그라운드 인덱싱이 끝나는 즉시 화면이 실시간으로 자동 감지하여 목록을 채우며, 로딩 중에는 `Loading recipes...`가 명확히 표시됩니다.
- **텍스트/이름 파싱 100% 전면 제거 및 순수 바이트코드/API 스펙 추출 완성 (`ParallelHelper`)**:
  - 아이템 이름 매칭, 툴팁 정규식 파싱 등 문자열 참조를 완전히 들어내고, `MetaMachine.getCurrentParallel()` 및 `modifyRecipe` 오버라이딩 바이트코드 구조 분석을 통해서만 일반 병렬 해치와 절대 병렬 해치(고정 전력)를 완벽하게 감별하도록 개편했습니다.
- **하드코딩된 리터럴 텍스트 전수 조사 및 다국어화 (`MachineConfigDialog`, `ko_kr.json`, `en_us.json`)**:
  - 코일 온도, 병렬/속도/시간/전력 배율 및 고정 전력 텍스트 등 남아있던 모든 고정 문자열을 정식 다국어 키로 등록하여 완벽하게 로컬라이징했습니다.

### 개선 및 변경 (Changed)
- **애드온 및 레시피 검색창 진입 지연(렉) 0ms 비동기 프리캐싱 최적화 (`MachineAddonCatalog`, `BoardScreen`, `GregTechCalcBoard`)**:
  - 애드온 창 진입 시 메인 렌더러 스레드를 블로킹하던 동기 `refresh()` 및 `preloadFuture.join()`을 전면 삭제하고 100% 논블로킹 비동기 프리로딩으로 전환하여 지연 시간 0ms로 즉각 열리도록 최적화했습니다.
- **튜토리얼 실행 시 기존 작업물 보호 및 전용 새 페이지 자동 생성 (`TutorialManager`)**:
  - 튜토리얼을 시작할 때 기존 페이지의 노드들을 지우지 않고, `🎓 튜토리얼` 전용 신규 페이지 탭을 자동으로 생성하여 열어줌으로써 사용자의 기존 설계 데이터를 완벽하게 보호하도록 개선했습니다.

---

## [1.0.4-fix] - 2026-08-19

### 버그 수정 (Fixed)
- **동일 아이템 다중 출력 슬롯 누적 합산 및 덮어쓰기 오류 수정 (`RecipeNode`, `FlowGraphSolver`)**:
  - 레시피 출력에 같은 아이템이 여러 슬롯(예: 100% 1개 + 85% 1개)으로 나뉘어 있을 때, 내부 맵 처리 과정에서 마지막 슬롯의 수치로 덮어써지면서 전체 생산량이 깎이던 문제를 고쳤습니다.
  - 이 때문에 실제로는 흑자인데도 공정 요약(Process Summary)에 마이너스 적자로 잘못 집계되던 현상이 해결되었습니다.
- **월드 로딩 시 MaterialRegistryManager 충돌 해결 (`MachineAddonCatalog`, `CoilHelper`, `GregTechCalcBoard`)**:
  - 게임 기동이나 싱글플레이 월드 진입 시 리소스팩 리로드 단계에서 애드온 크롤러가 너무 일찍 실행되면서, GTCEu 재질 등록 전에 클래스가 로딩되어 터지던 `IllegalStateException` 크래시를 잡았습니다.
  - 무차별적인 리플렉션을 걷어내고, 인게임에서 계산기 창을 실제로 열 때 카탈로그를 지연 로딩(Lazy Loading)하도록 바꿔서 안정성을 높였습니다.
- **쿠프로니켈 코일 75% 속도 페널티 및 레벨 계산 교정 (`CoilHelper`, `MachineConfigDialog`)**:
  - 쿠프로니켈(1800K) 코일을 쓸 때 열분해로나 화학반응기에서 속도가 75%로 떨어지는 페널티(처리 시간 1.33배)가 누락되던 점을 바로잡았습니다.
  - 속도가 느려지는 페널티는 빨간색, 시간 단축 보너스는 하늘색, 전력 절감 보너스는 노란색으로 카드 배지 색상을 구분해서 보기 쉽게 정리했습니다.
- **터빈 로터 인스턴스 API 연동 및 고유 스펙 크롤링 오류 수정 (`DynamicAddonCrawler`, `TurbineRotorHelper`)**:
  - `TurbineRotorBehaviour` 메서드가 인스턴스 메서드임에도 static으로 호출되어 NPE가 발생하고 모든 로터가 100% 기본값으로 나오거나 엔더리움 내구도 수치가 효율(9180%)로 오인되던 문제를 고쳤습니다.
  - 이제 셸라이트(220%), 엔더리움(180% 효율/300% 출력), HSS 계열 등 각 재질별 고유 효율과 파워가 인게임과 100% 동일하게 정상 추출됩니다.
- **티어별 확률 증가가 없는 고정 확률 부산물 연역 처리 (`EmiRecipeConverter`)**:
  - 그렉텍 레시피면 무조건 티어당 +5%씩 확률이 오를 거라고 넘겨짚던 하드코딩 추측을 뺐습니다.
  - GTCEu 레시피 데이터에서 `tierChanceBoost`를 직접 읽어와서, 오버클럭을 해도 확률이 그대로 유지되는 레시피(써투스 크리스탈 85% 등)가 실제 인게임 수치와 동일하게 계산되도록 맞췄습니다.

### 개선 및 변경 (Changed)
- **설정 가능한 정비 해치(CMH) 실제 가공 시간 및 고장 발생률 프리셋 반영 (`DynamicAddonCrawler`)**:
  - 기존의 0.95배 같은 애매한 중간값 대신, 인게임 UI의 2대 프리셋을 명확히 분리 등록했습니다:
    - **가속 모드**: 가공 시간 **0.9배** (10% 단축), 정비 문제 발생률 **3.0배** (3x break rate, 전력 1.0x 동일)
    - **절약 모드**: 가공 시간 **1.1배** (10% 지연), 정비 문제 발생률 **0.2배** (0.2x break rate, 전력 1.0x 동일)
- **대형 터빈 멀티블록 전용 발전량 및 홀더 티어 효율 연동 (`RecipeNode`)**:
  - 대형 증기 터빈(HV: 1,024 EU/t), 대형 가스 터빈(EV: 4,096 EU/t), 대형 플라즈마 터빈(IV: 16,384 EU/t), 부스트 플라즈마 터빈(NPT: 12x 병렬 196,608 EU/t)의 터빈별 기본 티어와 발전량을 정확히 구분했습니다.
  - 기본 티어보다 높은 로터 홀더를 장착할 때마다 티어당 +10%의 효율 보너스(가동 시간 연장 및 연료 절감)와 2배 발전량 승수가 실제 인게임 물리와 100% 일치하도록 반영했습니다.
- **타입 기반 동적 코일/병렬 해치 분석 체계 전환 (`CoilHelper`, `ParallelHelper`, `DynamicAddonCrawler`)**:
  - 이름이나 키워드 텍스트 매칭에 의존하던 방식을 치우고, `ICoilType` / `IParallelHatch` 인터페이스와 클래스 구조를 직접 확인하는 방식으로 바꿨습니다.
  - 이 덕분에 계단이나 슬랩 같은 엉뚱한 건축 블록이 코일로 잘못 등록되는 일이 없어졌습니다.

---

## [1.0.4] - 2026-08-19

### 신규 기능 (Added)
- **인터랙티브 하드웨어 애드온 설정 다이얼로그 (`MachineConfigDialog`, `MachineAddon`)**:
  - **인터랙티브 3D 아이템 아이콘 랙 (장착 슬롯)**: 기존 텍스트 목록을 10슬롯 3D 아이템 트레이로 개편하여 장착된 가열 코일, 메인터넌스 해치, 터빈 로터, 특성들을 한눈에 시각적으로 확인.
  - **원클릭 슬롯 해제 & 전체 해제**: 장착 슬롯 아이콘을 클릭하여 즉시 해제하거나 `[✕ 전체 해제]` 버튼으로 모든 애드온을 한번에 초기화.
  - **통합 카테고리 필터 & 실시간 검색창**: `전체`, `♨ 코일`, `⚡ 병렬`, `🔧 정비`, `⚙ 특성`, `+ 커스텀` 탭과 실시간 텍스트 검색(`Search...`) 지원.
  - **깔끔한 비주얼 카탈로그 카드**: 3D 아이템 아이콘, 정밀 스펙 배지, 장착 완료(`✔`) 배지 및 호버 시 다이내믹 하이라이트 제공.
- **플로우차트 캔버스 노드 카드 애드온 상태 노출 (`NodeCardRenderer`, `BoardTooltipRenderer`, `NodeWidget`)**:
  - **노드 카드 우측 상단 미니 3D 애드온 트레이**: 캔버스 상의 레시피 노드 카드(`Count` 줄 우측)에 장착된 애드온들의 실제 3D 아이템 아이콘(14x14)을 직접 렌더링.
  - **상세 하드웨어 구성 진단 툴팁**: 미니 애드온 아이콘 또는 `[⚙ 1x (+1)]` 버튼에 마우스를 올리면 멀티블록/싱글블록 모드, 유효 병렬, 장착된 모든 애드온 목록 및 실시간 보너스 스펙이 일목요연하게 툴팁으로 표시.
  - **원클릭 다이얼로그 진입**: 미니 애드온 아이콘이나 병렬 버튼 클릭 시 즉시 하드웨어 설정 다이얼로그 오픈.
- **전체 모드 대상 동적 애드온 & 코일 전수조사 시스템 (`DynamicAddonCrawler`)**:
  - **Java Reflection 엔진**: GTCEu뿐만 아니라 KubeJS, Star Technology 등 모든 모드팩의 `Block` 및 `ICoilType` / `CoilType` 객체를 런타임 메모리에서 직접 리플렉션 탐색.
  - **다중 플래그 툴팁 정밀 분석**: Advanced / Normal 툴팁을 종합 분석하여 열 용량, 가공 속도, 전력 소모, 병렬 수치를 100% 동적 추출.
  - **모드팩 커스텀 코일 완벽 지원**: `kubejs:zalloy_coil_block` (13499K), `kubejs:magmada_alloy_coil_block` (16199K), `draconium`, `awakened`, `infinity`, `hypogen`, `eternity` 등 모든 확장 코일 자동 지원.
- **`Multiblock Info` 레시피 기반 동적 멀티블록 감지 (`MultiblockDetector`)**:
  - 이름 문자열 기반 추측을 전면 제거하고, EMI의 `multiblock_info` 구조 조합식 및 GTCEu 머신 정의를 100% 데이터 기반으로 직접 스캔하여 대형 혼합기(`Large Mixer`), 대형 화학 반응기 등 멀티블록 변형 지원 여부를 완벽 식별.

### 개선 및 변경 (Changed)
- **코일 온도 스펙 표기 표준화**: 모든 가열 코일 배지에 기본 열 용량(`♨ [온도]K`)이 항상 최우선 표시되도록 통일하고, 기계 맞춤형 전력 할인/속도 보너스를 함께 표기.
- **카탈로그 카드 레이아웃 정리**: 카드 하단의 불필요한 `[ + + Install ]` 텍스트를 제거하여 텍스트 잘림 현상을 해결하고 아이템 이름과 스펙의 가시성 대폭 향상.
- **마우스 휠 스크롤 편의성 개선**: 노드 카드의 병렬 버튼 위에서 휠 스크롤로 병렬 수치가 의도치 않게 바뀌던 핸들러를 제거하여 전압 티어 및 대체 재료 변경 전용으로 정돈.

### 버그 수정 (Fixed)
- **터빈 로터 스펙 및 대형 터빈 발전량 연산 불일치 수정 (`RecipeNode`, `DynamicAddonCrawler`, `NodeCardRenderer`)**:
  - **셸라이트(Scheelite) 로터 정밀 지원**: 효율 220%, 파워 225%, 최대 한계 64,000 EU/t 스펙을 로터 데이터베이스 및 동적 크롤러에 완벽 등록.
  - **IV 대형 가스 터빈(LGT) 발전량 & 병렬 일치화**: IV 로터 홀더(8,192V)와 셸라이트 로터(225%) 조합 시 인게임과 동일하게 정확히 `36,864 EU/t (4.5A IV)`, `1,152 병렬`, `4.40초 (220% 효율)`, `523.6 mB/s`로 일치하도록 연산 엔진 수정.
  - **발전기 노드 카드 로터 배지 동기화**: 발전기 노드 카드에서 로터 장착 시 `⚙ 220%` 로 즉시 표시되고, 로터 자체를 중복 애드온(`(+1)`)으로 카운트하던 표시 중복 해결.
- **조작 설정(Key Binds) 메뉴 단축키 캡처 충돌 수정 (`GregTechCalcBoard`, `KeyBindings`)**: 마인크래프트의 조작 설정(`KeyBindsScreen`), 옵션, 채팅창, 일시정지 화면에서 단축키를 변경하거나 입력할 때 계산기 보드가 강제로 열리던 문제 완전 해결.
- **텍스트 입력 필드 포커스 보호**: 검색창이나 텍스트 박스(`EditBox`) 입력 중 단축키가 가로채지지 않도록 보호.
- **애드온 툴팁 줄바꿈 및 Shift 미누름 시 누락 해결**: Shift를 누르지 않아도 모든 코일/애드온 보너스가 즉시 노출되도록 개선하고, 긴 한 줄 텍스트를 깔끔한 다단 줄바꿈(`\n`)으로 정돈.
- **중복 ✕ 아이콘 수정**: 애드온 툴팁 하단 해제 안내 문구의 `✕ ✕ Remove` 중복 표기 수정.
- **크롤러 오분류 필터링**: 코일 및 메인터넌스 탭에 전압 코일 부품(`lv_coil`..`uhv_coil`), 핵융합 코일, 커버(`maintenance_detector_cover`) 등이 잘못 들어가던 문제 필터링 강화.

---

## [1.0.3] - 2026-08-18

### 신규 기능 (Added)
- **대화형 온보딩 튜토리얼 & 환영 다이얼로그 (`TutorialManager`, `TutorialOverlay`)**:
  - **환영 모달 (`WelcomeTutorialDialog`)**: 보드를 처음 사용하는 유저에게 인터랙티브 튜토리얼 시작 여부를 묻는 친절한 안내 창을 제공합니다.
  - **5단계 핵심 실기 튜토리얼 가이드**:
    1. **1단계. 레시피 노드 추가**: 캔버스 빠른 추가 마커 `[+]` 또는 상단 툴바 `[➕ 추가]` 버튼으로 노드 배치.
    2. **2단계. 기본 와이어 연결**: 출력 포트(초록색)에서 입력 포트(파란색)로 드래그하여 유체/아이템 파이프라인 생성.
    3. **3단계. Shift + 드래그 1:1 자동 비율**: `Shift`를 누른 채 연결하여 공급-소비 속도에 맞춰 목표 기계 대수를 1:1로 자동 맞춤.
    4. **4단계. 기준 마스터 지정 & 자동 비율**: 최종 완제품 노드에 `[🎯]` 기준 앵커를 켜고 `[⚖ 비율]` 버튼을 눌러 전체 선행 공정 일괄 동기화.
    5. **5단계. 와이어 절단 & 캔버스 초기화**: 연결선 곡선 또는 포트 우클릭으로 연결 해제 및 `[🗑 초기화]`로 보드 리셋.
  - 반투명 튜토리얼 안내 카드, 펄스 애니메이션 목표 하이라이트 링, 건너뛰기 및 언제든지 재시작 지원.
- **캔버스 컨텍스트 빠른 레시피 추가 마커 (`[+]`)**:
  - 캔버스 빈 공간을 마우스 좌클릭하면 클릭한 좌표에 부드러운 반투명 **`[+]` 빠른 추가 버튼**이 동적으로 생성됩니다.
  - `[+]` 버튼을 클릭하면 레시피 검색창이 열리고, 선택한 레시피 노드가 **마우스가 있던 해당 캔버스 좌표에 즉시 스폰**되어 깔끔한 공정 배치가 가능합니다.
- **경량 벡터 / 델타 기반 실행 취소(Undo) & 다시 실행(Redo) 시스템 (`HistoryManager`, `BoardCommand`)**:
  - **초경량 Command/Delta 메모리 구조**: 무거운 전체 그래프 스냅샷 대신 상대 이동 델타(`dx, dy`), 추가/삭제된 노드/와이어 참조, 속성 이전값/새값만을 기록하여 메모리 오버헤드를 극소화했습니다.
  - **단축키**: `Ctrl + Z` (실행 취소), `Ctrl + Y` 또는 `Ctrl + Shift + Z` (다시 실행).
  - **상단 툴바 버튼**: `[↶ 실행취소]`, `[↷ 다시실행]` 버튼 추가 (스택 상태에 따른 활성/비활성화 색상 적용).
  - **전체 캔버스 조작 완벽 지원**: 노드 단일/다중 이동, 노드 추가/삭제, 와이어 연결/절단(Shift 연결 시 대수 스케일링 포함), 인라인/버튼 속성 편집(기계 대수, 전압 티어, OC 모드, 병렬 수, 이름 변경, 마스터 앵커, 터빈 로터), 복합 모듈화 및 펼치기, 자동 연결, 자동 비율, 최대 처리량 최적화.
  - **탭(Page)별 독립 히스토리 관리**: 각 탭 페이지마다 독립적인 Undo/Redo 스택을 유지하여 현재 작업 중인 탭의 내역만 안전하게 조작합니다.
- **접이식 단축키 안내 HUD (`HotkeyHudWidget`)**:
  - 캔버스 우측 하단에 접이식 단축키 HUD를 추가했습니다.
  - 언제든지 **`H`** 키를 눌러 HUD를 열고 닫으며 필수 단축키(`Ctrl+Z`, `Ctrl+Y`, `Ctrl+C/V/X/D`, `Shift+와이어`, `H`, `Delete`, `R/U` 등)를 인게임에서 실시간으로 확인할 수 있습니다.
- **동적 포트 및 와이어 가이드 툴팁**:
  - 입출력 포트에 마우스를 올릴 때 상세 포트 정보 및 연결 팁을 툴팁으로 안내합니다.
  - 와이어 드래그 중 실시간 상황별 툴팁 렌더링:
    - 일반 드래그: *"클릭하여 파이프라인 연결"*
    - **`Shift + 드래그`**: *"✨ Shift 연결: 1:1 생산-소비 속도에 맞춰 목표 기계 대수 자동 계산"*
- **페이지 탭 관리 & 삭제 확인 다이얼로그 (`PageTabBarWidget`, `DeletePageConfirmDialog`)**:
  - **탭 더블클릭 / 우클릭 이름 변경**: 탭 헤더를 더블클릭하거나 우클릭하여 원하는 공정 이름으로 인라인 타이핑 수정 가능.
  - **가로 드래그 & 마우스 휠 스크롤**: 탭이 많아져 화면 너비를 벗어날 때 마우스 드래그나 휠로 부드럽게 가로 스크롤 지원.
  - **페이지 삭제 확인 모달**: `[x]` 버튼 클릭 시 중요한 공정 데이터가 실수로 삭제되지 않도록 확인 다이얼로그를 띄워 안전하게 보호.
- **전력 표시 단위 전환 모드 (`PowerDisplayMode`)**:
  - 공정 요약창의 전력 합계 행을 클릭하여 원하는 단위로 순환 전환 가능:
    - **`EU/t`** (기본 그렉텍 틱당 EU)
    - **`EU/s`** (초당 EU)
    - **`RF/t` / `FE/t`** (써멀 및 타 모드 호환 FE/RF 단위)
- **내장 인터랙티브 매뉴얼 가이드북 다이얼로그 (`GuideDialog`)**:
  - 상단 툴바에 **`[📖 도움말]`** 버튼을 추가하여 외부 모드 의존성 없이 언제든지 보드 화면 위에서 6개 챕터의 상세 가이드북을 열어볼 수 있습니다.
- **드래그 다중 선택 / 일괄 이동 / 클립보드 단축키**:
  - **드래그 영역 박스(Marquee Box) 선택**: 캔버스 빈 공간에서 좌클릭 드래그로 반투명 영역을 만들어 여러 노드를 한번에 선택.
  - **Shift + 클릭 다중 선택**: 원하는 노드를 개별 추가/제외.
  - **일괄 드래그 이동**: 선택된 노드 중 하나를 잡고 드래그하면 내부 와이어 연결과 상대 좌표를 온전히 유지하며 함께 이동.
  - **클립보드 단축키**:
    - `Ctrl + X`: 선택한 노드들과 내부 연결선을 클립보드에 복사하고 캔버스에서 즉시 잘라내기(Cut).
    - `Ctrl + C`: 선택한 노드들과 내부 연결선을 클립보드에 복사.
    - `Ctrl + V`: 마우스 커서 위치에 새 UUID로 와이어와 함께 복제 붙여넣기.
    - `Ctrl + D`: 선택한 노드들을 즉시 복제(Duplicate).
    - `Delete` / `Backspace`: 선택한 노드들과 연결선 일괄 삭제.
    - `Ctrl + A`: 전체 노드 선택.
- **노드 이름 인라인 직접 수정 기능 (`NodeNameEditor`)**:
  - 노드 헤더 타이틀을 더블클릭하여 원하는 공정 이름(예: `황산 생산 1라인`, `니트로벤젠 터빈 발전소`)으로 키보드로 직접 수정하고 영구 저장.
- **복합 모듈화(Compound Module) & 펼치기(Expand)**:
  - **`[📦 모듈화 (Group)]`**: 캔버스에 복잡하게 연결된 수많은 공정 설비들을 단일 복합 모듈 카드로 압축.
  - **내부 자가 소모 부산물 자동 캡슐화**: 내부에서 100% 자체 소비되는 중간 부산물은 숨기고, 순수 외부 원자재, 최종 생산물, 총 소비/발전 전력, 총 포함 기계 대수만 노출.
  - **`[⤢ 펼치기 / Expand]`**: 모듈 카드의 우측 상단 펼치기 버튼을 누르면 원래의 개별 설비들과 내부 와이어 연결선들로 온전히 원상복구.
  - **선택 노드 부분 모듈화**: 다중 선택된 노드들만 모듈로 묶고 외부 연결선은 새 모듈 포트로 자동 재배선.
- **소프트 의존성 모드 호환성 격리 가드 (`ModCompatHelper`)**:
  - GregTech(`gtceu`), Thermal(`thermal`), EnderIO(`enderio`) 설치 여부에 따라 전용 코드를 안전하게 격리 실행.

### 버그 수정 및 개선 (Fixes & Improvements)
- **Z-Index & Depth Buffer 충돌 완전 해결**:
  - 노드 카드 간 겹침 시 뒤쪽 카드의 3D 아이템 모델이나 유체 스프라이트가 앞쪽 카드를 뚫고 비치던 현상 완전 방지 (`RenderSystem.disableDepthTest` 및 노드별 Z-Offset 분리 적용).
  - 요약창(SummaryOverlay), 툴바, 상단 탭 바, 가이드북/검색 모달이 캔버스 노드 위로 항상 최상단에 안정적으로 렌더링되도록 개선.
- **복합 모듈 기계 대수 통계 오류 수정**:
  - 요약창(SummaryOverlay)에서 복합 모듈이 포함되어 있을 때 1대로 집계되던 문제를 수정하여 모듈 내부의 실제 기계 대수(예: 58대)로 정확히 합산되고, 마우스 호버 시 세부 기계 구성이 정상 출력됩니다.
- **모듈 카드 텍스트 겹침 완전 해결 & 미니멀 UI**:
  - 모듈 카드의 1번째 줄 대수 표시(`📦 58대`) 및 2번째 줄 태그(`[📦 모듈]`)를 컴팩트하게 정돈하여 텍스트 충돌을 방지하고 불필요한 컨트롤을 숨겨 깔끔한 외관을 제공합니다.
- **전수 다국어 로컬라이제이션 및 번역 감사 (i18n)**:
  - 모든 UI 요소, 툴팁, 튜토리얼 텍스트, 메시지를 `ko_kr.json`과 `en_us.json`에 1:1로 완전 동기화하여 하드코딩 문자열을 완전히 제거했습니다.
- **상단 툴바 레이아웃 간소화 및 정돈**:
  - 중복되거나 불필요한 버튼을 정리하고 마우스 휠 스크롤 및 직관적인 툴팁을 추가하여 사용 편의성을 개선했습니다.

---

## [1.0.2] - 2026-08-18

### 신규 기능 (Added)
- **네이티브 그렉텍 터빈 로터 스캐너 & 3D 비주얼 선택창**:
  - **48종+ 런타임 실시간 스캔**: 하드코딩 없이 그렉텍 기본 로터뿐만 아니라 KubeJS, Thermal, EnderIO 등 모드팩이 추가한 모든 특수 재질 로터를 런타임에 100% 동적으로 감지합니다.
  - **3D 비주얼 아이템 그리드 모달 (`[⚙ 220%]` 클릭)**: 각 로터의 고유 텍스처 색상(청록색 엔더륨, 갈색 드래곤스틸, 황금색 셸라이트 등)을 3D 아이템으로 렌더링하며 효율 배지와 실시간 검색창을 제공합니다.
  - **완벽한 한국어 지원**: 언어 설정에 맞춰 `트리타늄 터빈 로터`, `터빈 효율: 220%`, `터빈 출력: 220%`, `클릭하여 터빈에 장착` 등 완벽한 다국어 툴팁을 출력합니다.
- **로터 파워 & 티어 기반 정격 병렬 자동 도출**:
  - 그렉텍 대형 터빈의 실제 바이트코드 공식(V_max = V_holder * P_rotor / 100.0)을 완벽하게 적용했습니다.
  - 로터(효율/파워) 또는 로터홀더 전압 티어(`EV`, `IV`, `LuV`, `ZPM` 등)를 선택/변경하는 즉시 **터빈이 낼 수 있는 최대 정격 병렬 수(Par)와 발전량(+EU/t)이 1초 만에 자동 계산되어 입력**됩니다.
  - *예: 니트로벤젠 기본 32 EU/t + 셸라이트 로터 (파워 450%) + IV 로터홀더 -> **1,152x 병렬, +36,864.0 EU/t, 4.00초 주기, 288.0 mB/s 소모율** 완벽 일치!*
- **Thermal Dynamo & Non-GT Generator Support (써멀 다이나모 및 타 모드 발전기 완벽 지원)**:
  - 써멀의 보석 다이나모(`Lapidary Dynamo`), 증기 다이나모(`Stirling Dynamo`), 마그마 다이나모(`Magmatic Dynamo`) 등 모든 다이나모 레시피의 RF/FE 발전량(`300,000 RF`)을 그렉텍 EU로 정밀 자동 변환하여 연소 시간과 초당 소모율을 정확히 계산합니다.
  - 무의미한 해시 문자열 ID 대신 `보석 다이나모 (다이아몬드)` / `Lapidary Dynamo (Diamond)`와 같이 스마트한 기계 및 연료 이름을 자동으로 명명합니다.
- **발전기 전용 에메랄드 테마 패널**:
  - 발전기 카드는 일반 소비 기계와 확연히 구별되도록 **딥 에메랄드 다크 배경 (`#122218`)**, **에너지 그린 테두리 (`#33AA66`)**, **`[발전기]` 뱃지**, **`§a⚡` 아이콘**으로 렌더링됩니다.
- **Machine Item Icon on Header (패널 좌측 상단 기계 아이템 아이콘)**:
  - 각 레시피 노드 카드의 좌측 상단에 해당 공정을 수행하는 실제 기계 블록/아이템(화학 반응기, 증류탑, 대형 터빈 등)의 3D 아이콘을 표시하여 공정을 직관적으로 식별할 수 있습니다.
- **Total Machines Needed Statistics (총 필요 기계 대수 통계 및 상세 툴팁)**:
  - 공정 요약창에 현재 세팅 기준 전체 라인 가동에 필요한 **"총 기계 대수 (Total Machines)"**를 실시간으로 합산하여 보여줍니다.
  - 마우스 호버 시 각 기계 종류별 필요 대수 목록(예: 화학 반응기 3대, 증류탑 2대, 대형 가스 터빈 1대...)을 툴팁으로 상세히 안내합니다.
- **Interactive Panel Width Resizing (패널 크기/너비 자유 조절)**:
  - 각 노드 카드의 우측 하단 리사이즈 핸들(`⤡`)을 마우스로 잡고 드래그하여 패널 너비(160 ~ 400px)를 자유자재로 조절할 수 있습니다. 긴 레시피 이름이나 많은 입출력 슬롯도 잘림 없이 시원하게 확인할 수 있습니다.
- **병렬 수 자유 입력 & 마우스 휠 조절**:
  - **직접 숫자 타이핑 (클릭)**: `Par` 버튼을 클릭하여 `1152` 같은 대규모 멀티블록/터빈 병렬 수를 원하는 대로 직접 키보드로 입력할 수 있습니다.
  - **마우스 휠 조절**: `Par` 버튼 위에서 마우스 휠을 굴려 2배 증가/감소 또는 `Shift + 휠`로 +1/-1 정밀 조절이 가능합니다.
  - **우클릭 1/2배수 조절**: 우클릭 시 즉시 절반으로 줄어듭니다.
- **정수 대수 올림 & 병목 방지 보장 (`Supply >= Demand`)**:
  - 실제 공장 건축에 맞춰 Auto Ratio 및 Shift+드래그 계산 시 모든 기계 대수가 **정수(1, 2, 3...)로 올림(`Ceiling`)**되도록 변경했습니다.
  - 상류 공급 기계의 생산 속도가 하류 소비 기계의 요구 속도보다 **언제나 크거나 같도록(Supply >= Demand)** 정수 올림하여 공정에서 원료 부족으로 인한 병목(Starvation)이 발생하지 않습니다.
- **양방향 Shift+드래그 비율 자동 맞춤 & 다중 공급 부족분 보충 (Deficit Matching)**:
  - **순방향 (출력 -> 입력)**: Shift를 누른 채 출력 포트에서 입력 포트로 선을 연결하면, 공급량에 맞춰 뒤 기계의 대수를 안전하게 자동 계산합니다 (소비자 대수는 공급량을 초과하지 않도록 내림).
  - **역방향 (입력 -> 출력)**: Shift를 누른 채 입력(요구량) 포트에서 출력 포트로 선을 역드래그하면, 뒤 기계의 소비 요구량에 맞춰 앞 기계의 대수를 올림으로 맞춥니다.
  - **기존 공급량 차감 및 부족분 보충 (폐액 재활용 루프 지원)**: 폐액 재활용 등으로 이미 일부 공급되고 있는 입력 포트에 신규 생산 기계를 Shift+드래그로 연결하면, **기존에 들어오고 있는 공급량을 뺀 '순수 부족분(Deficit)'만을 채우도록 앞 기계 대수가 지능적으로 계산**됩니다!

### 성능 최적화 및 편의성 (Performance & Polish)
- **인메모리 사전 인덱싱 기반 고속 레시피 검색 최적화**:
  - 검색창 오픈 시 레시피의 이름/입출력/ID를 메모리에 사전 인덱싱(`SearchableRecipe`)하여, 수만 개 레시피 환경에서도 타이핑 시 프레임 드랍(렉) 및 GC 스파이크 없이 부드럽게 검색됩니다.
- **Z-Index & 모달 가림 현상 해결**:
  - 로터 선택창 및 레시피 검색창의 렌더링 우선순위(`translate(0, 0, 600)`) 및 불투명 배경을 적용하여, 캔버스의 노드나 텍스트가 모달 위로 비치거나 겹치는 현상을 완전히 제거했습니다.

### 버그 수정 (Fixed)
- **발전기 전력 생산 및 시간 고정 메커니즘**:
  - 증기 터빈, 가스 터빈, 디젤 발전기 등의 발전 레시피를 보드에 추가했을 때 소비 전력으로 취급되어 총 EU/t 소모량이 증가하던 문제를 수정했습니다.
  - 발전기/터빈은 티어를 올려도 시간 단축 오버클럭(OC)이 발생하지 않고 **기본 연소 시간(Duration)을 유지한 채 병렬(Parallels)로만 발전량이 선형 증가**하도록 그렉텍 실제 메커니즘과 일치시켰습니다.
  - 기계 카드에 `[발전기]` 뱃지와 초록색 `+EU/t`로 표시되며, 우측 요약 패널에서 총 소비 전력에서 발전량을 정상 차감합니다.

---

## [1.0.1] - 2026-08-17

### 버그 수정 (Fixed)
- **Auto Ratio 대수 폭증 버그 해결**:
  - 증류탑, 증기 크래킹 등 1개 기계에서 여러 부산물이 나오는 다중 출력/분기 트리에서 상류 기계 대수가 수백 대로 튀던 문제를 단방향 파동 전파(Anchor-Outward Wavefront Balancer) 알고리즘으로 전면 개편하여 완벽히 해결했습니다.
- **기계 대수 키보드 입력 수정**:
  - 기계 대수 숫자 상자에서 `Backspace` 및 `Delete` 키가 정상 작동하지 않던 버그를 수정했습니다.

### 신규 기능 & 개선 (Added)
- **공정 요약창 마우스 휠 세로 스크롤**:
  - 원자재/생산물 목록이 화면 높이를 초과할 때 마우스 휠로 위아래 스크롤하여 최종 생산물까지 확인할 수 있도록 스크롤바와 영역 클리핑을 추가했습니다.
- **UI 다국어 번역 및 동적 버튼 크기**:
  - 상단 툴바 메뉴와 기계 카드 내부의 텍스트(`일반/퍼펙트 OC`, `병렬` 등)에 다국어(I18n) 번역을 적용하고, 글자 길이에 맞춰 버튼 너비가 유동적으로 조절되도록 개선했습니다.

---

## [1.0.0] - 2026-08-17

### 최초 릴리즈 (Initial Release)
- EMI 및 GregTech CEu Modern 레시피 연동 지원 (EMI에서 `A` 또는 `[⚡]` 클릭 시 보드에 즉시 추가).
- 무한 캔버스 줌/팬 및 드래그 앤 드롭 파이프라인 연결.
- 그렉텍 전압 티어(ULV~MAX), 표준/퍼펙트 오버클럭 및 병렬 처리 계산 지원.
- 기준 마스터 기계(`🎯`) 지정 및 Auto Ratio 연쇄 비례 계산.
- 최대 전압 티어 상한(`Cap: LV~MAX`) 및 최대 처리량 최적화(`[Flow]`).
- NBT GZIP Base64 블루프린트 공유 코드(`GTBOARD:...`) 및 `Ctrl+C`/`Ctrl+V` 클립보드 공유.
- 실시간 총 소비 전력, 필요 원자재 및 순 생산물/부산물 요약 패널.
