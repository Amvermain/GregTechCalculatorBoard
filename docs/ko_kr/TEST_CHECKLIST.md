# GregTech Calculator Board 종합 기능 검증 체크리스트 (QA Test Checklist)

본 문서는 `GregTechCalculatorBoard`의 모든 주요 기능, 계산 솔버, 모드별 호환 레이어(SPI), UI/UX 및 다국어 지원 상태를 체계적으로 검증하기 위한 공식 QA 체크리스트입니다.

---

## 1. 캔버스 조작 & 인터랙티브 와이어링 (Canvas & Wiring)

### 1.1 캔버스 기본 제어
- [ ] **팬 이동(Pan)**: 마우스 우클릭 드래그로 캔버스가 부드럽게 이동하는지 확인
- [ ] **줌 제어(Zoom)**: 마우스 휠 스크롤로 $25\% \sim 200\%$ 범위 내 확대/축소가 정상 동작하는지 확인
- [ ] **원점 복귀**: `Space` 키 또는 원점 리셋 버튼 클릭 시 카메라가 $(0, 0)$으로 정렬되는지 확인
- [ ] **선택 및 조작**:
  - [ ] 단일 노드 클릭 선택 및 드래그 이동
  - [ ] 캔버스 드래그 박스를 통한 다중 노드 선택 및 일괄 이동
  - [ ] `Ctrl + A` 전체 선택 동작
  - [ ] `Delete` / `Backspace` 키로 선택된 노드/프레임 일괄 삭제
  - [ ] `Ctrl + Z` (실행 취소) 및 `Ctrl + Y` (다시 실행) 히스토리 정상 동작

### 1.2 와이어 연결 & 비율 연산
- [ ] **기본 와이어링**: 출력 포트(초록) 클릭 $\rightarrow$ 입력 포트(파랑) 클릭 시 곡선 와이어가 연결되는지 확인
- [ ] **✨ Shift + 와이어 연결 (1:1 자동 비율 맞춤)**:
  - [ ] `Shift`를 누른 채 연결 시 공급 기계의 생산 속도와 소비 기계의 소비 속도를 분석하여 대상 기계 대수(Machine Count)가 1:1로 자동 스케일링되는지 확인 (예: 500 mB/s 공급 $\rightarrow$ 100 mB/s 소비 기계 연결 시 대수가 **5.0대**로 자동 설정)
- [ ] **와이어 분기점 (Reroute Junction)**:
  - [ ] 기존 와이어 선을 **마우스 더블 클릭** 시 비용 0의 [🔀 분기점 노드]가 와이어 중간에 즉시 삽입되는지 확인
  - [ ] 분기점 노드를 거쳐 여러 기계로 분기할 때 유량/아이템 배분이 정상 동작하는지 확인
  - [ ] 분기점에서 `Shift + 와이어 연결` 시 분기 유량 기준으로 비율이 자동 계산되는지 확인
- [ ] **와이어 절단**: 연결선 위를 직접 우클릭하거나 연결된 포트를 우클릭하여 즉시 절단되는지 확인
- [ ] **포트 드래그 빈 공간 검색 (Drag-to-Search)**:
  - [ ] 포트에서 빈 캔버스로 드래그 앤 드롭 시 4버튼 퀵 마커(🔍 검색, ➕ 분기점, 📋 복사 등)가 생성되는지 확인
  - [ ] 🔍 클릭 시 해당 아이템/유체를 소모/생산하는 레시피 검색창이 자동으로 필터링되어 열리는지 확인
  - [ ] 레시피 선택 시 노드가 배치되며 와이어가 자동 연결되는지 확인

### 1.3 페이지 탭 & 저장/공유
- [ ] **페이지 탭 관리**:
  - [ ] 상단 `+` 버튼으로 새 캔버스 페이지 추가
  - [ ] 탭 클릭 시 페이지 전환 (페이지별 카메라 위치 및 줌 레벨 기억)
  - [ ] 탭 우클릭으로 이름 인라인 변경
  - [ ] `x` 버튼으로 탭 삭제 (마지막 탭 보호)
  - [ ] **탭 오버플로우 네비게이션**: 탭이 많을 때 좌/우 `«`, `»` 인디케이터 클릭 및 휠 스크롤로 매끄러운 탭 이동 확인, 마지막 탭 우측 16px 패딩 보정 확인
- [ ] **저장 & 불러오기**: `💾 저장` / `📂 불러오기` 시 모든 탭, 노드, 프레임, 메모, 배선이 NBT 파일(`calcboard_save.nbt`)에 안전하게 저장 및 복원되는지 확인
- [ ] **블루프린트 공유**:
  - [ ] `📋 공유` 클릭 시 팩토리 전체가 압축 텍스트 코드로 클립보드에 복사되는지 확인
  - [ ] `📥 가져오기` 클릭 시 공유 코드를 붙여넣어 다른 월드/플레이어의 배치가 100% 동일하게 복원되는지 확인

### 1.4 대체 레시피 스위칭 & UI 가독성 (Recipe Switcher & Accessibility)
- [ ] **인플레이스 레시피 전환 (Switch Recipe)**:
  - [ ] 머신 설정 다이얼로그 또는 노드 우클릭에서 `[🔄 Switch Recipe]` 클릭 시 동일 머신/산출물 대체 레시피 목록 표시 확인
  - [ ] 레시피 교체 시 동일 아이템/유체 포트의 와이어가 끊기지 않고 자동 보존되는지 확인
  - [ ] `Ctrl+Z` (Undo) / `Ctrl+Y` (Redo) 시 레시피 및 와이어 상태가 완벽하게 복원되는지 확인
- [ ] **UI 가독성 5단계 배율 (FontScale)**:
  - [ ] 머신 설정창 `[Aa 1.0x]` 버튼 클릭(좌/우), 휠 스크롤, `+`/`-` 키보드 단축키로 0.75x ~ 1.30x 배율이 즉시 적용되는지 확인
  - [ ] 배율 변경 시에도 중심점 Matrix Scaling & Virtual Mouse 역변환으로 클릭 판정이 정확한지 확인
- [ ] **전역 유체 단위 모드 (FluidUnitMode)**:
  - [ ] 상단 툴바의 유체 단위 버튼 또는 `Shift+T` 단축키로 `Auto` ➔ `Always mB` ➔ `Always B` 순환 확인
  - [ ] 캔버스 전체 노드 및 툴팁 유체 표기가 일괄 통일되어 렌더링되는지 확인

---

## 2. 계산 솔버 & 오버클럭 & 병렬 엔진 (Solver & Overclocking)

### 2.1 전압 티어 및 오버클럭 연산 (GTCEu)
- [ ] **전압 티어 변경**: ULV부터 MAX까지 티어 변경 시 전압(V), 소요 시간(Duration), 기본 전력(EU/t)이 정상 반영되는지 확인
- [ ] **오버클럭 모드**:
  - [ ] **표준 오버클럭 (Standard)**: 1티어당 $4\times\text{ EU/t}$, $2\times\text{ 속도}$ ($50\%$ 에너지 효율)
  - [ ] **완전 오버클럭 (Perfect)**: 1티어당 $4\times\text{ EU/t}$, $4\times\text{ 속도}$ ($100\%$ 에너지 효율)
  - [ ] **무손실 오버클럭 (Lossless)**: 1티어당 $2\times\text{ EU/t}$, $2\times\text{ 속도}$
  - [ ] **서브틱 연산 (Subtick Execution)**: 가공 시간이 1틱 미만으로 떨어질 때 틱당 가공 배치 수(`batchesPerTick`)로 정상 환산되는지 확인

### 2.2 병렬(Parallel) 및 전력 요구량 비례 연산
- [ ] **병렬 전력 스케일링**:
  - [ ] $\text{총 소비 전력(EU/t)} = \text{오버클럭 EU/t} \times \text{병렬 수(Parallel)}$ 연산 검증
  - [ ] **4x 병렬 검증**: 1티어 높은 전력($4\times\text{ EU/t}$)이 정확히 요구되는지 확인 (예: EV 1,920 EU/t $\rightarrow$ 4x 병렬 시 7,680 EU/t, IV급 전력)
  - [ ] **16x 병렬 검증**: 2티어 높은 전력($16\times\text{ EU/t}$)이 정확히 요구되는지 확인 (예: EV 1,920 EU/t $\rightarrow$ 16x 병렬 시 30,720 EU/t, LuV급 전력)
- [ ] **병렬 제어 해치 (Parallel Control Hatch)**:
  - [ ] 4x, 16x, 64x, 256x 병렬 제어 해치 장착 시 생산량 및 소비 전력이 비례하여 스케일링되는지 확인
- [ ] **에너지 해치 전력 수용량 연산**:
  - [ ] `GTEnergyHatchAddon` 장착 시 전압 $\times$ 암페어(1A, 2A, 4A, 16A) 합산 전력(`totalEUtCapacity`) 산출 검증
  - [ ] **듀얼 해치 승급**: 동일 티어 에너지 해치 2개 장착 시 +1 전압 티어 승급(Dual Hatch Overclock) 적용 확인
  - [ ] **비대칭 해치**: 비대칭 해치(예: 16A EV + 1A IV) 장착 시 최대 수용 전력 기반 티어 산정 확인

### 2.3 순환 의존성 & 그래프 밸런싱
- [ ] **순환 루프 해결 (Cycle Detection)**:
  - [ ] 공정 루프(A $\rightarrow$ B $\rightarrow$ C $\rightarrow$ A) 연결 시 무한 루프 에러 없이 자가 소비량을 제외한 순생산량(Net Output)이 정확히 산출되는지 확인
- [ ] **시간 단위 토글 (RateTimeUnit)**:
  - [ ] `/s`(초당), `/m`(분당), `/h`(시간당), `/t`(틱당), `/batch`(배치당) 전환 시 노드 카드 및 요약창의 모든 숫자가 일관되게 환산되는지 확인
- [ ] **기계 대수 고정 (Pinning)**:
  - [ ] 특정 노드의 대수를 고정(Pin)했을 때 상하위 기계의 가동률($\%$)과 부족/잉여량이 직관적으로 표시되는지 확인

---

## 3. 모드별 특화 호환 레이어 (Mod Compatibility SPI Layer)

### 3.1 그렉텍 모던 (GTCEu Modern)
- [ ] **가열 코일 (Heating Coils)**:
  - [ ] Cupronickel부터 Trinium까지 코일 변경 시 레시피 요구 온도 충족 검증
  - [ ] 코일 온도 여유분에 따른 EUt 할인율 및 가공 속도 보너스 반영 검증
- [ ] **증기 멀티블록 (Steam Multiblocks)**:
  - [ ] 증기 보일러, 증기 그라인더, 증기 오븐 등 증기 멀티블록 선택 시 병렬 곱연산 없이 정격 64 mB/t (고압) 및 32 mB/t (저압) 정격 유량 고정 연산 확인
- [ ] **대형 터빈 & 로터 (Large Turbines & Rotors)**:
  - [ ] 대형 증기/가스/플라즈마 터빈의 기본 발전량 및 로터 재질별 효율($\%$), 파워($\%$), 내구도 연동 확인
- [ ] **클린룸 & 핵융합 (Cleanroom & Fusion)**:
  - [ ] 클린룸 요구 티어 뱃지 표시 및 핵융합로 반사판(Reflector) 티어별 전력 보너스 연동 확인

### 3.2 써멀 & 시스팀즈 (Thermal Series & Systeams)
- [ ] **써멀 증강 키트 (Thermal Augments)**:
  - [ ] Scale(용량/스케일), DynamoPower(발전량), DynamoEnergy(연료 효율) 증강 장착 시 연역적 수치 반영 확인
- [ ] **보일러 수급 연산 (Systeams Boilers)**:
  - [ ] 보일러 가동 시 연료 효율 증강에 따른 물 소비량 및 증기 생산량 배율 연산 확인
- [ ] **증기 다이내모 (Steam Dynamos)**:
  - [ ] 증기 소비량 대비 RF/FE 발전량 계산 및 다이내모 오버클럭 검증

### 3.3 크리에이트 & 뉴에이지 (Create & Create: New Age)
- [ ] **회전력 전파 (Stress Units & RPM)**:
  - [ ] 대형 물레방아, 물레방아, 풍차 베어링, 증기 엔진 등의 발전 SU/t 연산 확인
  - [ ] 팬, 믹서, 분쇄 휠 등 가공 기계의 RPM 기반 속도 및 SU 소비량 연산 확인
- [ ] **과부하(Overstress) 시뮬레이션**:
  - [ ] 공급 SU보다 소비 SU가 초과할 경우 전체 네트워크 효율이 0%로 정지되는 Create 고유 물리 법칙 시뮬레이션 확인
- [ ] **발전기 코일 & 탄소 브러시 & 자석**:
  - [ ] 장착된 자석 블록의 자력 등급 및 수량에 따른 정확한 FE/t 발전량 계산 확인
- [ ] **모터(기본/고급/강화)**:
  - [ ] 전력(FE) 소비량 대비 출력 SU/RPM 계산 확인

### 3.4 스타 테크놀로지 (Star Technology)
- [ ] **GCU 및 Threading 연산**:
  - [ ] GCU 복합 모듈 연산 및 Threading Helix Co-Processor 병렬 가동률 반영 확인

---

## 4. 그룹 프레임 & 복합 모듈화 (Group Frames & Compound Modules)

### 4.1 그룹 프레임 (Group Frames)
- [ ] **프레임 생성 (`Ctrl + G`)**: 다중 선택된 노드들을 감싸는 프레임 박스 생성 및 헤더 드래그로 내부 노드 일괄 이동 확인
- [ ] **제목 및 메모**: 프레임 제목 인라인 수정 및 스티키 메모(Sticky Note) 텍스트 작성/수정 확인
- [ ] **테마 색상 (🎨)**: 팔레트 버튼으로 프레임 외곽선 및 배경 테마 색상 변경 확인
- [ ] **프레임 해제**: 프레임 삭제 시 내부 노드는 유지되고 프레임만 안전하게 제거되는지 확인

### 4.2 복합 모듈 패키징 (Compound Module Packaging)
- [ ] **모듈 압축 (Collapse to Module)**:
  - [ ] 프레임 헤더의 `📦` 버튼 클릭 시 내부 전체 공정이 **단일 모듈 카드**로 즉시 압축되는지 확인
  - [ ] **포트 집약 (Port Aggregation)**: 프레임 외부와 연결된 입력/출력 포트만 모듈 카드의 외곽 포트로 깔끔하게 집약되는지 확인
  - [ ] **내부 자가 순환 은닉**: 프레임 내부 노드 간의 중간 자재 와이어는 카드 내부에 캡슐화되어 외부로 노출되지 않는지 확인
  - [ ] **통합 전력/기계 대수 집계**: 모듈 카드 상단에 내부 기계들의 총 소비 전력 및 총 기계 대수 합산 표시 확인
- [ ] **모듈 펼치기 (Expand)**:
  - [ ] 모듈 카드의 `⤢ 펼치기` 클릭 시 기존 내부 노드들의 위치와 와이어링 배선이 **100% 원본 그대로 복원**되는지 확인

---

## 5. 멀티블록 BOM 자재 명세서 (Multiblock Construction BOM)

- [ ] **3D 구조체 자동 스캔**:
  - [ ] 팩토리에 배치된 모든 멀티블록의 케이싱, 가열 코일, 해치/버스, 컨트롤러 수량이 정확히 집계되는지 확인
- [ ] **듀얼 해치 최적화 옵션**:
  - [ ] `1x 정규 티어 해치` vs `2x 하위 티어 해치` 옵션 전환 시 BOM 목록이 실시간으로 갱신되는지 확인
- [ ] **자재 필터링 & 스택 환산**:
  - [ ] 전체 / 케이싱 / 코일 / 해치 / 컨트롤러 필터 탭 동작 확인
  - [ ] 총 수량 옆에 마인크래프트 인벤토리 스택 환산치(예: `2st + 15개`)가 정확히 표시되는지 확인
- [ ] **쇼핑 리스트 클립보드 복사 (`📋`)**:
  - [ ] 텍스트 쇼핑 리스트가 깔끔한 마크다운 형식으로 클립보드에 복사되는지 확인
- [ ] **EMI 연동 (`Register in EMI`)**:
  - [ ] 버튼 클릭 시 소요 자재들이 EMI Recipe Tree / 즐겨찾기 패널에 즉시 일괄 등록되는지 확인

---

## 6. 레시피 검색 & 즐겨찾기 (Recipe Search & Favorites)

### 6.1 고급 프리픽스 및 불리언 검색 엔진
- [ ] **모드 필터 (`@`)**: `@gtceu`, `@thermal`, `@create_new_age` 등 특정 모드 레시피만 필터링 확인
- [ ] **태그 필터 (`#`)**: `#forge:ingots`, `#forge:dusts` 등 아이템 태그 검색 확인
- [ ] **기계/카테고리 필터 (`[` 또는 `%`)**: `[macerator]`, `[pyrolyse_oven]` 등 기계별 필터링 확인
- [ ] **투입/생산 필터 (`in:`, `out:`, `>`, `<`)**:
  - [ ] `in:iron`, `>polyethylene` (투입 재료 필터)
  - [ ] `out:steel`, `<oxygen` (생산물 필터)
- [ ] **불리언 연산자 (`!`, `|`, `"..."`)**:
  - [ ] `!charcoal` (특정 단어 제외 NOT)
  - [ ] `oil | creosote` (OR 검색)
  - [ ] `"heavy fuel"` (정확한 구문 검색)

### 6.2 다국어 및 현지화 검색
- [ ] **양방향 검색 (Bilingual Search)**:
  - [ ] 한국어 상태에서 한국어 명칭(예: `"물레방아"`, `"고급 모터"`, `"탄소 브러시"`)으로 정상 검색되는지 확인
  - [ ] 영문 fallback 명칭(예: `"water wheel"`, `"motor"`, `"coil"`)으로도 동시에 검색되는지 확인

### 6.3 즐겨찾기 도크 (Favorites Dock)
- [ ] **별표(⭐) 토글**: 검색창 및 기계 카드에서 별표 클릭 시 즐겨찾기에 등록/해제되는지 확인
- [ ] **드래그 앤 드롭 배치**: 우측 즐겨찾기 도크에서 레시피를 캔버스로 드래그하여 즉시 배치 확인

---

## 7. UI/UX & 다국어 & 인터랙티브 튜토리얼 (i18n & Tutorial)

- [ ] **15초 인터랙티브 튜토리얼 (Interactive Tutorial)**:
  - [ ] 상단 튜토리얼 시작 시 1단계(노드 추가)부터 7단계(모듈 패키징)까지 단계별 하이라이트와 인터랙션 가이드가 정상 진행되는지 확인
  - [ ] 완료 시 축하 메시지 및 배지 획득 확인
- [ ] **글로벌 밸런스 대시보드 (`B` 키)**:
  - [ ] 팩토리 전체의 투입 원자재(Raw Inputs)와 순생산물(Net Products) 수급 밸런스 점검창이 정확히 집계되는지 확인
- [ ] **다국어 리소스 완전성 (i18n)**:
  - [ ] `en_us.json`과 `ko_kr.json`의 번역 키 및 포맷 토큰(`%s`, `%d`)이 100% 동기화되어 누락된 키(Missing Translation Key)가 없는지 확인
  - [ ] `testI18nCompletenessAndConsistency` 단위 테스트 통과 확인

---

## 8. 즉시 테스트 가능한 공식 프리셋 블루프린트 코드 (Ready-to-Use Test Presets)

인게임에서 직접 노드를 하나하나 배치할 필요 없이, 아래의 압축 블루프린트 코드를 복사한 뒤 계산기 보드 상단 툴바의 **`📥 가져오기 (Import)`** 버튼을 누르면 즉시 복합 테스트 그래프가 캔버스에 생성됩니다.

### 📌 프리셋 1: 석유화학 & 증류탑 16x 병렬 루프 (GTCEu)
* **검증 대상**: 16x 병렬 제어 해치, LuV 에너지 해치 전압 연산, 증류탑 4개 부산물 유체 배선, 열분해 크래커 연결, 그룹 프레임
```text
GTBOARD:H4sIAAAAAAAA/81Xz2/jRBSeNE2apomoKhaQ4ODDHtiDpfxw0mQ5kNKm20htqdp02Z7CZGacjNbxWPa43XAB7REhceaC+FP2T+ICV3jjiTc/1mmyP4TooVXtmXnf+973vXnOI7SNMq6gLMgjhNJZVOjjgB2FPpZcuC0LRT/baEuE0gtltCqVRVk8EqErW7+2ovfwgAyxS9jXf+oNObTp4hFD6W+5s40K2JHMd+HIWxbk1AloeyAJCx8L7uTQBqdz/2/KscdQ5vj0unMEobPcVZGjc9PoARGuxNxl9AyTIfw9VEAmMTm8RJ+8PqpHfe443B30fD6AmDw4drjnMQqAC6OZ7THsFMrx4EzQ0GFwXF5if8BklzMfbZw9TaOch33sOMxRSzXuh7UGrjYxtk2bVW3TKlt9s1GvlUyL1O3Gfs2y90kVjvIZ4R6Lj0qhHR48YS4DmoUPoYrilvnEEeQ5RIfYV92D86ODy6MUyvPgG6jIuXocpXDJfKgFgxQ2PRE8a3XQhJk8wT49YXwwVHS8BNrvhP88kFElJ7Qv4QYAKjCDcVcxv9M+bR92LzuHvfa1jnLTuonruqszOcSSDYQ/7tDlfBeAy9CRvK/yei2JXZCEcTRZaFzyQRptK+TfcSqHEOKvPMp7vvCYLzkLIM0tpcj2tWx9rzGgRZHeJIl0cypS/9X9Is2fMHw7No5DlqzVXZ3gUK3q2bBKlz7h8ZxypwDGaAWAU1W0lQActepNALOPlwDgKwBsnWNvKIc4MXpRh3H1Eh168dmSuDcr4hYumQ0e9MfGExwkBt/TgfzJut4ABxpB4ovFzoEpBekrOWyk0V7s3kiUnqO8iNCuaik+Js9Bje3IAxfgRBa1FJpGHwUjpkBdTJ0PO4ojPHCZ5ORYwNrZ5vOphsVue3G03hBLMsyhHeixxOeekiwqlusvjPjMALzrC2gEF+IuwgRxi5CYwwg87GqcQOIenSh+msCU0MxIUDBjJooPuAn4sctGnmoxoQ8Yt3JQSk9FOAROJIYM0+hjMmQjTrBz5TFGZzLPop0I0hl+oaz323zdvoihG3CW9IVjnKgkjS8hrUdZVGShnGJsleLmCr3soB8IR7UvRfskdhLtUfC2bXPCmUuUgaiu+3KCc2TSk6Z9GhL0xgBvHLDFBNEyQaTeXhDQ6jrBVdif9NrFm8gJb3u6vSZqoXAaPjXKB0ZU/Tek8DZlTyhw7iCSwIDpe3u+MtNjFLNL0N7DYQptdYJTaMXqCtuMhJqGZJL8kUIfdYKjEDunCqBemiDyZOG+s04f6ALG6tREP3oX6U3FVZglZzqe/OeD0Tqj0Gd6M+WBhPs2ElFPqgK83zCkizw7De1OpqFm2a5V6xXLpDX4ZbFSxWywft2s7ZOm1SS0sl/uL0xD7Q86DcnkaeiPxGloKT2r56Hu8nnoHtLnJqJUrIW9o5mlRle3gTVnot+XzETnSTNRav2ZaO9QtUBGjRWz0SRZolf3Fmeke14nT/nRXf0/Gt1+XOHjzJVkeJQYe0cHCdQCHXb+yVu7uThL5ntaeONkyfcM65dZGVtV067XGqZVaVKzT1jdtBqWTUv1Bm7apQUHn3xQB798lejgXxIdPE/Je9l2kd1kr25pX6xv0J9Q7MSs7cMJsRF1so9j92p4ram4BFDSmZ0SjTe7hHHH5dBQt9rcPRfNAOrjqv3U0GkG8Ud+LmJ2rY/W9Zr5eoJJowwRjvD/+erl31mUuVOstX5+NjN8PGyWcAWXrZJplylA6rOq2ahY1MRNWsHVOqUV2oRxR3IJWv78gsHUGV/iRvQVoRK+drkEqw4j0bRGaMoudiffItG/Pwgxij2yjXbAXC6MITz+VoDr3vbFKNLoWlxBftzt0Bc6n6wUeus6BKZRFuQ/2YvmIq+3PTHyekWZjaxJehaThP4FWTLUuRoSAAA=
```

---

### 📌 프리셋 2: 크리에이트 회전력 & 뉴에이지 전기 발전
* **검증 대상**: 대형 물레방아 회전력(SU) 발전, 발전기 코일 FE 변환, 고급 모터 구동, Create 전용 카드 제어
```text
GTBOARD:H4sIAAAAAAAA/81WQY/bRBSebDZpkg0IBBUcQPIBIS6W1rHTOFUF2e4mELq7h03CtqdoPH5ORnU81niyaThVnDjyCxA/hZ/CT+DSM7yxk90NJKpLW9QcHHv85nvvfe/7bNcIqZJSJHxIaoSQYpnUPZrAyVxSxUXUcUj6q5I7Yq7iuUqjCmVSpjMxj1TneXYfF9iURgy++TNbqJD9iM6A1AdKQpIYo4irpErqNFQgI8S+gqSiochHTAJVcD9JA8dzHVghe9zfcWdfLWMg+/1h9wzLKvNIV5XmLJK7TESK8gj8M8qm+H+si1zVw/Em+XSFGVI5gfECT+V4MQUIC6TKk17I4xh8bKc+uwWwbqpAKjw5E/48BASsKY2hhhwk2Tv9oUgqMZU0DCHUoVkLX/i+xZq2G5iHFmWm41kts93wAtNzbNe2/UY7cClCSWA8hjVUgRzw5FuIAIcgJEK9J65AslCwp5gdcw+GR+cnRxcnBVLjyUOc17leTlu4AImTAmxhPxbJ4845WXFTY1T63wGfTDUhz3AWCyGfJiqd82oWn0wUg/m/ycEKdTWT5VBzX3vUP+8O+8fjwSjL8qTzZD31D7JOjnHvRMhl39+JWSB1JHMeKu7pxq4V8+GpjjQudaRxqSOLpKprv+S+mmKSFzVSi6WIQSoOCTZ6Ryu2O7oWI3lVEccvEfFer7tVupVAYKn3A8iGfesyE2mpdzrqn9yo9J3yTh63fJ6hjCNYjCm2NllrcswEf03LFEe7PGPZbR8CZpkObTVMh+GZZ9me6R26FprHudd0g03PaKw3aRr5+1bT/LTVNB9nAt/kZtMxB93T7vHwAi3T6+awzFbEXX55/7pn4xjD3rpZfns3nvj/r4X/i1mof6Ur8cczoQX5dt4vdpv6bmA5ZgMaaBjXYabnW74Jlt107MOWZ7P2S94v5LWs8vyPV7fKJjNvwir/5HqHVY5WYcaZDsttlV/I2hPlQCLQWnwZAw/X0suK/f5GeAJ5enDrRWY0rYYxGBnm18amafVKr6uPmxWuP80qKcO5PibyPT7zCadISkyEQv714teDMiktNFGdn79a9ZfqDyzXbrbattmyWMt0AtYw3Ra0TadtteCe3XAZxe+bkuIKNf3ZI1S84sz40uiGwJTkjKulMVgmCmZo3Wmqng7v3DBKo9Xw08sfhZitzVIlB+i6CGG0vvRE9iroXylmqVhzkYUN8qjvP8saKiuRbc3DYJGU0QervWQjc77tWzPnm8rtzBlJj9ckkb8B5GpqOdELAAA=
```

---

### 📌 프리셋 3: 3D 멀티블록 BOM 자재 명세서 & 복합 모듈 (GTCEu)
* **검증 대상**: 전기로(EBF) + 대형 화학 반응기(LCR) 복합 공정, 멀티블록 BOM 창(`Shift + B`), 복합 모듈 카드 압축(`📦`) 및 펼치기(`⤢`)
```text
GTBOARD:H4sIAAAAAAAA/81WTW/bRhBdWbZMfRhOA7uXtgAPQdGiIGBLiqT4UtuyXKmwEsNW0gQoIiyXQ2lhkkssl3bUQ4Geey/af9Of1Et7bWf5ASmu7bhxClgXgruzs2/evHlihZAyWQmEA1GFELJUIjWbRnAQS6q4CHZfkeRXJqsiVmGskqhCiZSoL+JAff1Huo8LbEoDBvmCQZYD6gOpnioAzxwEE6HKpEY9BTLA1OcQGToT+WiiGMQ7kQ4bcx1mkCXuXLm+rGYhkOXBqDdERCXqOCJIARXJw5BK6nngDWNP8dDjINONTSYpO8MMvQDkZHYMkkGgcM8pkvXIB43oODuLqw8KpDaITmM7UgkDuhLO8LmZAvLPx5AkGk+pYlODVJE6JnmYBg9fmMfiAmSRVKRQQiYv+rISeehkpM4Rztla8YUzcMhKckmBrIX6XBfLUxTBFomx54cg6QRS+tcgVlel0cxdDbRINsKZFN4sgtMQwFngoUBWB9ERdl3qYkeauaXhiyJZ8+kkAMXZocBYkrRrfRAdxNQ70vCSSAyT4HrAsNhRSjqurTPBvREkmFUs8fCqgQjYFHzOqHcZQYlUE7aG9E3vudr97W0NbaSdM/u6DvMLpHh770vd2CzbVY1N0vVclzMOAZvpVYMYjCqYCDkjtUVqtJZ48B7irgykCG7Q9obPA0D5uWqHY+SivK/ZWlS4rlAEimKgM6Rsis+uhpUhSET5adpr0PxLzsa2RyM1dmPEwaBAyjw69HgYgoNF1PyFJHkpBWLwaCic2ANMWlFUTkDNJWCE88kopMgfNd3t9jY0HWur3QKrCXbdslvArI6z1X7M7OaTRr2FqSQwHkKeqkCqPPpGs06xMXjVmjjHhnmCneHtePfpaO/pwd7JQYFUeLSPYnyql5MSTkCi+QCWsByK6OXuIIWOI8aodPrAJ1NNyi/Ygwshz7LBzXpwI0MIMxXCSNNe7R31uqOTQXfce57e9Sr3P4M8SOvpZgrCWX0H9zXkVU+orWss5JL5uJeFm/s63DxMw4ukrGv5jjtqitf9WSGVUAqcHsUhwsJXtSnr0XhNck2+7dOv7+jTm6daaB5Ekfkux/4kd+bswPhf3n1zxH1w8enNLt6/Ny4+vZOL9++vi/fvjYv/b58o/8HAPe2745yKsQSqO3FHA+9fY+Bb9U6jA07DoluPbau5xdDAG21mNTptaFG3RcGxLxl4/4MauHoPA7+aoQ9h4Ndxf42BH+lws5uFmydp+K0N/EeSO3XJlZgwV2vKzE6u1RT8t3OlCuTvWW//0PzKPOqeZCb9uXnZto8lSoFp+kwPpWK6QprzKsz9Z0OTB1EISUj+5a+JXrrdH/vt1FMkK0x4Qv798/d/lcjKhWZl96dfF+ztUb3tdjrtTt0CG7atZh061pNm27YajWaraTcpqzcoGqriCoX9WR8lYmkpmkNQqOdYO0lX+CGOAvpOaZpoaPfs9zl/NMhan7z+IISfj0yZVHH0gpSDlH90FVcKP5HsrXjACnkwcN6kFZWUSI/ejpwSTkN2NoP6ModK/gEwJFAokQ0AAA==
```

---

### 📌 프리셋 4: 써멀 보일러 & 증기 다이내모 발전 루프 (Thermal / Systeams)
* **검증 대상**: 압축 보일러 물 $\rightarrow$ 증기 연산, 증기 다이내모 FE 발전, 유체 파이프라인 수급 밸런스
```text
GTBOARD:H4sIAAAAAAAA/71VTW/bRhAdSZZCyQoSBClg5KRDrwREWYpIHxLZ+qhdKE5hK1+XCCvuSFqY5BLLlV3n1mP/QH9Pf1IP7bkdckXbSRxERT50kbgczrz35j2xBlCFciQ5JjUAKFagPmMJDlaKaSGjXhuyTxXuyJWOVzqrKlSgwkK5inTvd3OfDvwli3x8+pc5sGArYiFC+VQjC6tQZ4FGFVHTc0ystAdsL7SPq70kLbCgKPgHJ1v6MqYGo/GLowEBqIjolvlvPjf/FaO5t86/F4oIfcXmeu8iLTIYPj59D0cJfvBlpBkV8WfMX9J3PwWyninoJjzSS1QhC/ZmUgSopr4MY4VJQnoWoCqSUSDiGDmBroc3WuTQC2CJ5JnkqwCpZU0ztUA9EaigOH5ZAitmigUBBmmpwfyj5/GW63Qe2y7zZnbbcVq25zR925t3WNvxnZnb7FIrhb6IMW9FGxDJTxghrVoqGnVXnqPyA+mf0XSafTrZPx7snwwKUBPJAbniOD3OKJygIj8gUdiKZfK6d2ygl6DmM8UPUSyWqSR/kPAXUp0lOnPTWvgds+YbskyNUgQxhbO4nKSK1w+H+5Pp85Pp6XA8MoPe5Pu24L4h06cdLaS6POKfbluAOgm6CrSYpeSuvPGgf13aOMhKS1BNCbwSXC9pzD81qMVKxqi0wITY3knDMXxxtSv42nkpjoa3mtWaS7LB3hzNxm9cbhST7xXTTeLxMI8Hv6TZcpr1+UbBcNus22ztNm0fOQXDmz22vW6rZe82ERnnXnfe2f1MMApfFAz9f4Lx4IawU6PO+5HYHo6H/cnJUX86Gm6QiFv6fSoL9cwAjUFWtGkKegPI7V6ZK2qT285wP8hNZ4D+fG05SQo9+Th9jezPumE/aRg0Vz8MrMZo2LhaS/7WSpUrbvYPuJkdSlD2ZSDVv38/fVuB8kUqQu+3wzX2zFUOd3bnHB2763c6dptx13Y91rR5Z+Y6LnKXzzoWlLXQ5NSdibH7msov8oIojqWMKYzLzA+9sz+vlWLReqHZ5Tspw9z+Vdim+NDLKXNMzbjcmisZZvbbSAIiJ6Ij/qshU9HSPLqZLhVy9vrZNdTXOVT4D2E11DFICAAA=
```

