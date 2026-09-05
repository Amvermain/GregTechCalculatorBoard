# GregTech Calculator Board 종합 기능 검증 체크리스트 (QA Test Checklist)

본 문서는 `GregTechCalculatorBoard`의 모든 주요 기능, 수학적 계산 솔버, 모드별 특화 호환 레이어(SPI), 물리 시뮬레이션 모델, UI/UX 및 다국어 지원 상태를 체계적으로 검증하기 위한 공식 QA 체크리스트입니다.

---

## 1. 캔버스 조작 & 인터랙티브 와이어링 (Canvas & Wiring)

### 1.1 캔버스 기본 제어
- [ ] **팬 이동(Pan)**: 마우스 우클릭 드래그로 캔버스가 부드럽게 이동하는지 확인
- [ ] **줌 제어(Zoom)**: 마우스 휠 스크롤로 $25\% \sim 200\%$ 범위 내 확대/축소가 정상 동작하는지 확인
- [ ] **원점 복귀**: `Space` 키 또는 원점 리셋 버튼 클릭 시 카메라가 $(0, 0)$으로 정렬되는지 확인
- [ ] **화면 맞춤 (`Home` / `F`)**: `Home` 또는 `F` 키 입력 시 모든 배치된 노드가 화면 중앙에 한눈에 들어오도록 줌/위치 자동 맞춤 확인
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
- [ ] **와이어 분기점 (Reroute Junction) & 목표 배치 소요 시간 (ET/DT)**:
  - [ ] 기존 와이어 선을 **마우스 더블 클릭** 시 비용 0의 [🔀 분기점 노드]가 와이어 중간에 즉시 삽입되는지 확인
  - [ ] 분기점 노드를 거쳐 여러 기계로 분기할 때 유량/아이템 배분이 정상 동작하는지 확인
  - [ ] 분기점에서 `Shift + 와이어 연결` 시 분기 유량 기준으로 비율이 자동 계산되는지 확인
  - [ ] **목표 배치 수량 (ET / DT) 계산**: 분기점 하단 뱃지 클릭 시 목표량 입력창이 활성화되며, 목표량 설정 시 실시간 생산 완료 소요 시간(`ET: xx.xs`) 또는 원자재 고갈 소요 시간(`DT: xx.xs`)이 뱃지에 표시되는지 확인
  - [ ] **분기점 공급 모드 & 리셋**: 분기점 우클릭 시 외부 공급 모드(무한/고정 유량) 설정창이 열리며, `Shift + 우클릭` 시 목표량이 0으로 초기화되는지 확인
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
- [ ] **미사용 I/O 포트 숨기기 및 선택 복원**:
  - [ ] 포트 우클릭 시 연결선이 분리되고 해당 포트가 숨김 처리되는지 확인
  - [ ] 숨겨진 포트가 있는 카드 하단에 알약 배지(`N개 출력 숨김`, `N개 입력 숨김`, `N개 포트 숨김`)가 정상 표시되는지 확인
  - [ ] 알약 배지 클릭 시 `HiddenPortsPopup` 드롭다운 팝업이 열리고 포트 목록(`[IN]`/`[OUT]`)이 노출되는지 확인
  - [ ] 팝업 항목 클릭 시 해당 포트가 카드에 가시 상태로 즉시 복원되는지 확인
  - [ ] 포트 숨김 설정이 파일 저장/불러오기, 멀티플레이어 동기화, 블루프린트 공유 시에도 영속 유지되는지 확인
- [ ] **스마트 인라인 텍스트 편집 엔진 (`InlineTextEditor`)**:
  - [ ] 기계 이름, 기계 대수, 병렬 수, 목표 배치 수량 클릭 시 인라인 편집 모드로 진입하는지 확인
  - [ ] 마우스 드래그를 통한 텍스트 범위 선택 및 더블클릭 전체 선택 확인
  - [ ] 방향키 커서 이동 및 `Shift + 방향키` 텍스트 범위 선택 확인
  - [ ] `Ctrl + 좌우 방향키` 단어 단위 점프 및 `Ctrl + Backspace/Delete` 단어 단위 삭제 확인
  - [ ] `Ctrl + A` 전체 선택 및 `Ctrl + C / X / V` 클립보드 복사/잘라내기/붙여넣기 확인
  - [ ] 인라인 편집 중 입력하는 키가 캔버스 전역 단축키로 오작동하지 않는지 확인
- [ ] **ArchUnit 클린 아키텍처 자동 정적 검증**:
  - [ ] `ArchitectureTest` JUnit 테스트 스위트가 아키텍처 위반 없이 100% 통과하는지 확인

---

## 2. 계산 솔버 & 오버클럭 & 수학 엔진 (Solver & Math Engine)

### 2.1 전압 티어 및 오버클럭 연산 (GTCEu)
- [ ] **전압 티어 차이 ($\Delta\text{Tier}$)**: ULV부터 MAX까지 $\Delta\text{Tier} = \max(0, \text{TargetTier} - \text{RecipeTier})$ 정상 계산 검증
- [ ] **오버클럭 모드별 수식**:
  - [ ] **표준 모드 (Standard)**: 1티어당 $4\times\text{ 전력 (EU/t)}$, $2\times\text{ 속도}$ ($\text{EnergyFactor}=4.0, \text{SpeedFactor}=2.0$)
  - [ ] **완벽 모드 (Perfect)**: 1티어당 $4\times\text{ 전력 (EU/t)}$, $4\times\text{ 속도}$ ($\text{EnergyFactor}=4.0, \text{SpeedFactor}=4.0$)
  - [ ] **무손실 모드 (Lossless)**: 1티어당 $1\times\text{ 전력 (EU/t)}$, $1\times\text{ 속도}$ ($\text{EnergyFactor}=1.0, \text{SpeedFactor}=1.0$)
- [ ] **1틱 미만 서브틱(Sub-tick) 배치 승격 연산 ($< 1.0\text{ Tick}$)**:
  - [ ] 고전압 오버클럭으로 소요 시간이 1틱 미만으로 단축될 때 틱당 배치 수 $\text{BatchesPerTick} = \frac{1.0}{\text{Duration}}$ 및 유효 소요 시간 $1.0\text{ tick}$ 고정 검증
  - [ ] $\text{Effective EU/t} = \text{Calculated EU/t} \times \text{BatchesPerTick}$ 비례 상승 검증
  - [ ] 초당 사이클 수 $\text{CPS} = 20.0 \times \text{BatchesPerTick} \times \text{Parallel} \times \text{MachineCount}$ 정밀 연산 검증
- [ ] **확률 부산물 전압 티어 부스트 (Tier Chance Boost)**:
  - [ ] 분쇄기/원심분리기 등에서 전압 티어 상승 시 $\text{Effective Chance} = \min(1.0, \text{BaseChance} + (\Delta\text{Tier} \times \text{TierChanceBoost}))$ 적용 검증
- [ ] **투입물 소모 확률 및 음수 티어 부스트 (Input Consumption Chance & Reduction Boost)**:
  - [ ] 확률적 투입물 유량 정밀 연산 검증: $\text{Rate} = \text{Amount} \times \text{EffectiveChance} \times \text{CPS}$
  - [ ] 부호 지원 티어 부스트 연산: $\text{Effective Chance} = \text{clamp}(0.0, 1.0, \text{BaseChance} + (\Delta\text{Tier} \times \text{TierChanceBoost}))$
  - [ ] Star Technology Cyclonic Sifter Netherite Mesh 소모율 검증 (ZPM 기본 $3\% \rightarrow 0.0025\text{/s}$, UV 오버클럭 $2.8\% \rightarrow 0.00467\text{/s}$)
  - [ ] 투입 포트 툴팁의 동적 소모 확률 및 부호 반영 티어 변동치($\%+.1f\%/\text{Tier}$) 표시 확인
  - [ ] `InputConsumptionChanceTest` JUnit 자동화 헤드리스 회귀 테스트 100% 통과 확인

### 2.2 가우스-요르단 폐루프 질량 보존 솔버 (`MassBalanceSolver`)
- [ ] **폐루프 선형 연립방정식 정식화 ($A\mathbf{x} = \mathbf{b}$)**:
  - [ ] 화학 폐루프 사이클(예: 에틸벤젠 공정 수소 재활용, 백금족 정제 순환선)에서 내부 연결 물질 $M$개의 질량 보존($\text{생산} - \text{소비} = 0$) 및 앵커 기계 대수 제약식 행렬 구성 검증
- [ ] **부분 피보팅(Partial Pivoting) 가우스-요르단 소거법 ($O(N^3)$)**:
  - [ ] 피봇 원소 $|A_{pk}| < 10^{-9}$ 특이행렬/미결정계 감지 및 안정적 수치해 산출 확인
  - [ ] 분기점(Reroute)을 통과하는 다중 루프에서도 순수 유효 기계 대수 벡터 $\mathbf{x}$가 완벽히 수렴하는지 확인
- [ ] **10-Pass 고정점 병목 완화 (Bottleneck Relaxation)**:
  - [ ] 상류 공급 부족 발생 시 모든 하류 기계의 정상 상태 가동률($\eta_v \in [0.0, 1.0]$)이 10-Pass 이내에 수렴($\Delta\eta < 10^{-4}$)하는지 확인
- [ ] **목표 배치 생산 소요 시간(ETA) 및 총 소요 자원 연산 (`ProductionETACalculator`)**:
  - [ ] 단말 노드의 목표 생산량 $A_{\text{target}}$ 기준 소요 시간 $T_{\text{ET}} = \frac{A_{\text{target}}}{\text{Rate}_{\text{in}}}$ 산출 검증
  - [ ] 전체 상류 노드의 총 소비 전력량 $E_{\text{total}} = \sum (n.\text{getTotalEUt}() \times 20 \times T_{\text{ET}})\text{ [EU]}$ 및 순 원자재 소요량 집계 확인

### 2.3 병렬(Parallel) 및 에너지 해치 전력 수용량 연산
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

---

## 3. 모드별 특화 물리 & 특수 계산 호환 레이어 (Mod Compatibility SPI Layer)

### 3.1 그렉텍 모던 (GTCEu Modern)
- [ ] **가열 코일(Heating Coil) 기계별 고유 물리 연역**:
  - [ ] **전기로 (EBF)**: 레시피 요구 온도 $T_{\text{recipe}}$ 대비 코일 온도 $T_{\text{coil}}$의 여유분 $\Delta T = \max(0, T_{\text{coil}} - T_{\text{recipe}})$ 산출, 900K 초과마다 전력 소모 $5\%$ 복합 할인 ($0.95^{\lfloor \Delta T / 900 \rfloor}$) 검증
  - [ ] **열분해로 (Pyrolyse Oven)**: 코일 속도 보너스 기반 소요 시간 단축 ($\text{DurationMult} = \frac{100.0}{\text{PyrolyseSpeed}\%}$) 검증
  - [ ] **크래킹 유닛 (Cracking Unit)**: 코일 에너지 보너스 기반 전력 할인 ($\text{EUtMult} = \frac{\text{CrackingEnergy}\%}{100.0}$) 검증
  - [ ] **대형 제련로 (Multi Smelter)**: 코일 티어에 따른 고유 병렬 수($32\text{x}, 64\text{x}, 128\text{x}\dots$) 자동 연동 검증
- [ ] **대형 증기/가스/플라즈마 터빈 & 로터 홀더 물리 (`GTTurbinePhysics`)**:
  - [ ] **로터 홀더 스루풋 캡**: 전압 티어(EV 4,096 EU/t base, 티어별 2배) 및 장착된 로터 파워($\text{RotorPower}\%$)에 따른 최대 발전 용량 $\lfloor \text{BaseCap} \times \frac{\text{RotorPower}}{100} \rfloor$ 연산 검증
  - [ ] **로터 효율 및 홀더 보너스**: 로터 재질 고유 효율($\text{Efficiency}\%$) + 대형 터빈 홀더 티어차 보너스($\Delta\text{Tier} \times 10\%$)를 통한 연료 소비 지속시간 스케일링 검증
  - [ ] **터빈 최적 병렬 수 자동 튜닝**: $\text{Parallel} = \lceil \frac{\text{HolderMaxEUt}}{\text{RecipeBaseEUt}} \rceil$ 자동 설정 검증
  - [ ] **터빈 유량 결손(Flow Deficit) 감지**: 투입 유량이 $100\%$ 미만일 때 발전 효율 감소 및 툴팁 결손 경고 확인
- [ ] **증기 보일러 및 쓰로틀 물리 (`GTBoilerPhysics`, `GTBoilerTier`)**:
  - [ ] **소형/대형 보일러 가속 계수**: LP Bronze ($120\text{ L/s}$), HP Steel ($360\text{ L/s}$), L-Bronze ($16\text{k/s}$), L-Steel ($36\text{k/s}$), L-Titanium ($64\text{k/s}$), L-Tungstensteel ($128\text{k/s}$) 고체/액체 연료 가속 계수 검증
  - [ ] **대형 보일러 쓰로틀($\theta \in [25\%, 100\%]$)**: $\text{Effective Speed} = \text{TierSpeed} \times \frac{\theta}{100.0}$ 비례 연산 확인
  - [ ] **물 $\rightarrow$ 증기 1:160 팽창비**: $\text{Water Rate (mB/t)} = \frac{\text{Steam Rate (mB/t)}}{160.0}$ 검증
- [ ] **핵융합로(Fusion Reactor) 물리 (`GTFusionHelper`)**:
  - [ ] 점화 기동 전력(Start EU) 기반 티어 판정 (Mk1 $\le 160\text{M}$, Mk2 $\le 320\text{M}$, Mk3 $\le 640\text{M}$, Mk4/Mk5)
  - [ ] 핵융합 특수 오버클럭 공식: 일반 기계($4\text{x}/2\text{x}$)와 달리 **$2\times\text{전력}, 2\times\text{속도}$ (에너지 계수 2.0, 속도 계수 2.0)** 적용 확인

### 3.2 크리에이트 (Create)
- [ ] **회전 속도(RPM) 기반 비선형 스케일링**:
  - [ ] 기본 $32\text{ RPM}$ 기준 속도 배수 $\text{SpeedFactor} = \max\left(0.01, \frac{\text{RPM}}{32.0}\right)$ 산출
  - [ ] 가공 시간: $\text{Duration} = \frac{\text{BaseDuration}}{\text{SpeedFactor}}$
  - [ ] 소모 동력: $\text{Effective SU} = \text{BaseSU} \times \text{SpeedFactor}$
- [ ] **팬 가공(Fan Processing) 특수 고정 물리**:
  - [ ] 세척(Splashing/Washing), 훈제(Smoking), 제련(Blasting), 영혼 가공(Haunting) 등은 RPM에 관계없이 인게임 고정 가공 시간(150 ticks = 7.5s) 유지 확인
- [ ] **공정 유형별 기본 SU 부하 계수 연역**:
  - [ ] Helve Hammer: 16x RPM ($512\text{ SU}$ at 32 RPM)
  - [ ] Press / Compacting / Rolling / Lathe / Centrifugation: 8x RPM ($256\text{ SU}$ at 32 RPM)
  - [ ] Milling / Mixing / Cutting / Vacuumizing: 4x RPM ($128\text{ SU}$ at 32 RPM)
  - [ ] Polishing: 2x RPM ($64\text{ SU}$ at 32 RPM)
- [ ] **키네틱 발전기 및 동력 변환 (Kinetic Generation)**:
  - [ ] 물레방아(256 SU), 대형 물레방아(512 SU), 풍차 베어링(512 SU), 크리에이티브 모터(16,384 SU) 발전량 검증
  - [ ] 스팀 엔진: 증기 $200\text{ mB/s}$ 소비 시 $+2,048\text{ SU}$ 동력 출력 검증
  - [ ] Alternator ($\text{SU} \rightarrow \text{FE}$) 및 Electric Motor ($\text{FE} \rightarrow \text{SU}$) 에너지 변환 검증

### 3.3 크리에이트: 뉴 에이지 (Create: New Age)
- [ ] **자석 링(Magnet Ring) 12슬롯 자력 합성**:
  - [ ] 코일 링에 장착된 최대 12개 자석 블록의 자력 합산: $\text{TotalStrength} = \sum_{i=1}^{12} F_{m, i}$
- [ ] **발전 코일(Generator Coil) $\text{SU} \rightarrow \text{FE}$ 변환 물리**:
  - [ ] 생산 전력: $\text{Generated FE/t} = \text{TotalStrength} \times |\text{RPM}| \times \text{suToEnergy}$ ($\text{suToEnergy}$는 런타임 설정값 연역, 기본 $\frac{15}{512}$)
  - [ ] 요구 회전력: $\text{Required SU/t} = (24.0 + \text{TotalStrength}) \times |\text{RPM}|$
- [ ] **카본 브러시 & 멀티블록 코일 BoM 자동 주입 및 모터(Basic/Advanced/Reinforced) 가공 연산**

### 3.4 써멀 시리즈 (Thermal Series)
- [ ] **업그레이드 키트(Upgrade Kit) 기본 스케일링**:
  - [ ] 스케일 팩터(1x ~ 4x)에 따른 기본 가공량/병렬 배수 및 증강 슬롯 수 확장 연동 검증
- [ ] **기계 vs 다이나모(발전기) 증강 분기 공식**:
  - [ ] 기계: $\text{Duration} = \frac{\text{BaseDuration}}{\text{ScaleFactor} \times \text{DurationMult}}$, $\text{Power} = \text{BasePower} \times \text{ScaleFactor} \times \text{PowerMult}$
  - [ ] 다이나모: 발전량 증가 시 지속시간 단축(연료 소모율 증가) 공식 $\text{Duration} = \text{BaseDuration} \times \frac{\text{FuelEnergyMult}}{\text{ScaleFactor} \times \text{PowerMult}}$

### 3.5 써멀 시스팀즈 (Thermal Systeams)
- [ ] **다이나모 $\leftrightarrow$ 보일러 1클릭 모드 전환**:
  - [ ] 연료의 Base Energy(RF)를 보존한 채 증기 생산 레시피로 실시간 동적 재작성 및 반대 방향 복원 검증
- [ ] **유체 비등(Boiling) 및 증기 비율 연산**:
  - [ ] `STEAM_RATIO_*` 및 `SPEED_*` 연동, 물/증류수 등 입력 유체 비율 `inToOutRatio = 0.25` (1 물 $\rightarrow$ 4 증기) 정확한 증기 산출 검증
- [ ] **증기 다이나모(Steam Dynamo)**: 증기 소비 $\rightarrow 400\text{ RF/t}$ 발전 및 증강 승수 합성 검증

### 3.6 스타 테크놀로지 (Star Technology)
- [ ] **스레딩 헬릭스(Threading Helix) 비선형 수학 공식**:
  - [ ] 속도 포인트: $\text{points} = \frac{\text{totalSpeed}}{100.0}$, $n = \frac{-1 + \sqrt{1 + 8 \cdot \text{points}}}{2}$
  - [ ] 소요 시간 승수: $\text{DurationMult} = 0.5^n \times \sqrt{\text{EffectiveParallels}}$
  - [ ] 전력 효율 승수: $\text{PowerMult} = \frac{30.0}{30.0 + \text{totalEfficiency}}$
  - [ ] 병렬 및 스레드 수: $\text{EffectiveParallels} = 1 + \lfloor \frac{\text{totalParallels}}{20} \rfloor$, $\text{EffectiveThreads} = 1 + \lfloor \frac{\text{totalThreading}}{5} \rfloor$
- [ ] **초고압 플라즈마 터빈(SPT, NPT) 모델**:
  - [ ] Supreme Plasma Turbine (SPT, 6x 병렬, $98,304\text{ EU/t}$ base)
  - [ ] Nyinsane Plasma Turbine (NPT, 12x 병렬, $196,608\text{ EU/t}$ base)

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

## 5. 멀티블록 및 공장 전체 BOM 자재 명세서 (Multiblock & Factory BOM)

- [ ] **3D 구조체 자동 스캔**:
  - [ ] 팩토리에 배치된 모든 멀티블록의 케이싱, 가열 코일, 해치/버스, 컨트롤러 수량이 정확히 집계되는지 확인
- [ ] **계층형 복합 모듈 재귀 BOM 산출 및 기계 대수 승격**:
  - [ ] 복합 모듈 내부 서브그래프의 기계 및 멀티블록 구조체 부품이 상위 모듈의 기계 대수 승수($P_{\text{child}} = P_{\text{parent}} \times M_{\text{module}}$)에 맞추어 비례 집계되는지 확인
- [ ] **공유 기계 풀 프레임 BOM 듀티 사이클 집계 및 정수 올림**:
  - [ ] 동일 물리 하드웨어를 공유하는 공정들의 누적 듀티 사이클 합산 및 올림($\lceil \sum \text{Duty} \rceil$)을 통해 중복 자재 없이 1회만 정확히 집계되는지 확인
- [ ] **단일 기계 전압 티어별 아이템 변환 및 출처 역추적 (`usedByMachines`)**:
  - [ ] 단일 기계 노드가 목표 전압 티어에 맞는 구체적 아이템(예: `LV Rock Breaker`)으로 자동 변환되고 툴팁에서 기여 공정 목록을 역추적할 수 있는지 확인
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

## 7. UI/UX & 다국어 & 설정 및 다이얼로그 (i18n & Dialogs)

- [ ] **인터랙티브 온보딩 튜토리얼 (Interactive Tutorial) & 고급 튜토리얼 (14단계 체계)**:
  - [ ] 9단계 기본 가이드 튜토리얼(노드 추가 $\rightarrow$ 분기점 목표 배치 및 실시간 생산 소요 시간 ET 계산 $\rightarrow$ 기계 선택기 $\rightarrow$ 모듈 패키징) 정상 진행 확인
  - [ ] 고급 튜토리얼(10단계 공유 머신 풀, 11단계 멀티블록 BOM 검사, 12단계 분기점 외부 공급 모드 $\infty$ / 고정 유량 및 재고 고갈 시간 DT 계산, 13단계 폴더 브라우저 및 파일 관리) 정상 가이드 및 실습 확인
  - [ ] 완료 시 축하 메시지 및 배지 획득 확인
- [ ] **공유 기계 풀 멀티블록 BOM 최적화 (`MultiblockBOMCalculator`)**:
  - [ ] 공유 기계 풀(`CanvasGroupFrame`) 내 동일 멀티블록들을 정수 올림된 물리 기계 대수(Quantized Ceil Count) 기준으로 집계하여 케이싱/코일/해치 소요 자재가 중복 계산되지 않는지 확인
- [ ] **EMI 슬롯 호버 동기화 하이라이트 & 툴팁 깊이 버퍼(Z-Index) 완전성**:
  - [ ] EMI 레시피 뷰어 위젯 내 슬롯에 마우스를 올렸을 때 캔버스 상의 해당 포트/와이어가 실시간으로 하이라이트되는지 확인
  - [ ] EMI 슬롯 및 보드 아이템에 마우스 호버 시 툴팁 배경과 텍스트가 깨끗하게 렌더링되며, 3D 아이템 모델이 툴팁 박스를 뚫고 돌출되지 않는지 확인
- [ ] **글로벌 밸런스 대시보드 (`B` 키)**:
  - [ ] 팩토리 전체의 투입 원자재(Raw Inputs)와 순생산물(Net Products) 수급 밸런스 점검창이 정확히 집계되는지 확인
- [ ] **보드 통합 설정 모달 (`[⚙ Settings]`)**:
  - [ ] 유체 단위(Auto/mB/B), 시간 단위, 싱글플레이 일시정지, 조화 정수비 한도 설정 저장 확인
- [ ] **스마트 자동 연결 필터 다이얼로그 (`AutoConnectFilterDialog`)**:
  - [ ] 다중 포트 연결 시 원하는 자원만 체크박스로 선택하여 연결되는지 확인
- [ ] **블루프린트 메타데이터 패키징 및 가져오기 미리보기 (`[📥] / [📤]`)**:
  - [ ] 제목/설명/태그 작성 및 가져오기 전 노드 수, 주요 원자재 요약 미리보기 확인
- [ ] **4대 언어 다국어 리소스 완전성 (i18n)**:
  - [ ] `en_us.json`, `ko_kr.json`, `zh_cn.json`, `ru_ru.json`의 번역 키 및 포맷 토큰(`%s`, `%d`)이 100% 동기화되어 누락된 키가 없는지 확인
  - [ ] `check_i18n.py` 검증 및 `testI18nCompletenessAndConsistency` 단위 테스트 통과 확인

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
