# ADR-023: 레시피 1회(배치) 기준 뷰 모드 및 화학양론적 순환 루프 검증
# (Per-Craft Batch View & Stoichiometric Recirculation Loop Verification)

- **문서 번호**: ADR-023
- **상태**: `APPROVED` (구현 및 검증 완료)
- **결정일**: 2026-09-05
- **타겟 버전**: `v2.2.0-alpha.2`
- **주관 계층**: UI Rendering & Pure Calculation Domain (`client.gui.render`, `client.gui.util`, `api.type`, `api.model`, `api.solver`)

---

## 1. 맥락 및 문제점 (Context & Problem Statement)

본 계산기 모드(`GregTechCalculatorBoard`)는 연속 흐름(Continuous Flow Rate, 단위: `/s`, `/t`, `/min`, `/h`, `/d`) 기반의 정상 상태(Steady-state) 물리 모델로 설계되어 있습니다.

$$\text{Rate} = A \times \text{CPS}_{\text{eff}} \times N \times \eta \quad [\text{items/s} \text{ 또는 } \text{mB/s}]$$

그러나 다단계 화학 공정(백금족 금속 분리 정제선, 바이어 알루미나 공정, 질산/황산 폐쇄 재활용 회로 등)을 설계할 때, 플레이어는 공장의 물리적 규모(기계 대수 $N$, 오버클럭 티어)를 결정하기 전에 **"공정 체인이 화학양론적으로 닫힌 순환 루프(Closed Stoichiometric Loop)를 형성하는가?"**를 먼저 검증하고자 합니다.

기계마다 기본 작업 시간(Duration, 예: $0.35\text{s}$, $1.2\text{s}$, $7.5\text{s}$, $15.0\text{s}$)이 달라, 연속 시간 유량으로 환산할 경우 $+57.14\text{ mB/s}$, $-83.33\text{ mB/s}$와 같은 무한소수가 노드 카드에 표시되어 1:1 정수 반응비를 직관적으로 파악할 수 없었습니다.

---

## 2. 결정 사항 (Decision)

### 2.1 계산 모델: 모델 B (기계별 독립 1회 레시피 뷰) 채택
- 연속 유량 엔진을 교란하지 않고, 뷰 모드 상에서 시간 단위, 기계 대수($N$), 오버클럭 CPS를 소거하고 각 노드의 순수 1회 레시피 기준 투입/산출량($A_{\text{in}}, A_{\text{out}}$)을 표시합니다.
- 상류 기계의 1회 생산량과 하류 기계의 1회 요구량이 일치할 경우 완전 일치 초록색 체크(`§2✔`)를 렌더링합니다.

### 2.2 단위 열거형 확장 (`RateTimeUnit`)
- `RateTimeUnit.PER_RECIPE("1x", 1.0, "gui.gtcalcboard.unit.per_recipe")` 상수 추가.
- `isRecipeBatchMode()` 메서드를 통해 1회 배치 모드 여부를 $O(1)$로 판별.
- 툴바 및 설정 다이얼로그의 시간 단위 순환 목록(`/t` $\to$ `/s` $\to$ `/min` $\to$ `/h` $\to$ `/d` $\to$ `1x`)에 완벽히 통합.

### 2.3 배치 전용 포트 통계 산출 (`FlowSummaryAggregator` & `FlowGraph`)
- 도메인 순수성을 유지하며 $O(|E|)$ 선형 시간으로 배치 포트 상태를 산출:
  - `getBatchInputPortStats(FlowGraph graph, RecipeNode node, int inputIndex)`
  - `getBatchOutputPortStats(FlowGraph graph, RecipeNode node, int outputIndex)`
- Fan-out/Fan-in 분기 및 Reroute(경유점) 통과 시에도 상류 1회 생산량과 하류 1회 요구량의 화학양론적 보존 법칙을 충실히 반영.

### 2.4 포맷터 및 노드 카드 렌더링 캐시 (`FormatUtil`, `NodeCardTextCache`)
- `FormatUtil.formatRecipeBatchAmount`: 시간 접미사 없이 정량(SU, mB, B, 아이템 수량) 포맷팅.
- `NodeCardTextCache.updatePowerAndDuration`: 배치 모드 활성화 시 CPS 수치를 `(1x)`로 교체.
- `NodeCardTextCache.computeBatchInputPortData` / `computeBatchOutputPortData`: 일치(`§2✔`), 결손(`§c⚠`), 잉여(`§b+`) 시각화.

---

## 3. 결과 및 영향 (Consequences)

### 3.1 긍정적 영향
1. **화학양론적 반응비 즉시 검증**: 복잡한 화학 라인 및 폐쇄 순환 회로 구성 시, 시간이나 기계 대수를 맞추기 전에도 레시피 정량비의 오류를 $0.1$초 만에 시각적으로 감지 가능.
2. **도메인 무결성 100% 보존**: 물리 도메인 솔버(`FlowBalanceMatrixSolver`)의 연속 유량 엔진을 일체 수정하지 않고, 순수 뷰 단위 확장 및 포트 통계 레이어로 격리하여 기존 기능의 회귀 위험을 원천 차단.
3. **i18n 다국어 지원**: 한국어(`1회당 (레시피)`), 영어(`Per Craft (1x)`), 중국어 간체(`单次配方 (1x)`), 러시아어(`За рецепт (1x)`) 100% 일치.

### 3.2 검증 결과
- 신규 단위 테스트 `StoichiometricBatchViewTest` 3종 작성 및 통과 (1:1 반응비 일치, Fan-out 분기 결손, Reroute 경유).
- `UiFormattingTest`에 `PER_RECIPE` 단위 순환 및 포맷팅 검증 추가.
- 전체 659개 단위 테스트 100% 통과 (`BUILD SUCCESSFUL in 17s`).
- 초고속 정적 린터 (`lint_agent_rules.py --diff`) 0 Violations, 다국어(`check_i18n.py`) 0 Errors.
