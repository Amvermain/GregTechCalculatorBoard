# ADR-054: 솔버 및 뷰어 어댑터 핵심 모듈 제어 흐름 평탄화 및 Rule 1 준수 명세
(Control Flow Flattening & Rule 1 Compliance for Solver and Viewer Core Modules)

- **문서 번호**: ADR-054
- **대상 버전**: `v2.3.0`
- **상태**: `IMPLEMENTED`
- **결정/완료일**: 2026-09-14
- **주관 계층**:
  - Flow Solver & Engine Layer (`api.solver.FlowSummaryAggregator`, `api.solver.MassBalanceSolver`)
  - Recipe Viewer Integration Layer (`integration.emi.EmiRecipeViewerAdapter`)

---

## 1. 개요 및 배경 (Motivation)

### 1.1 현황 분석 및 Rule 1 위반 실태 (Current Context & Linter Results)

프로젝트 공통 에이전트 지침([`.agents/AGENTS.md`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/.agents/AGENTS.md))의 **Rule 1 (클린 코드 및 객체지향 설계 원칙)**은 다음과 같은 제어 흐름 규칙을 엄격히 규정하고 있습니다:
- **단일 책임 원칙 및 얕은 메서드(Shallow Methods)**: 하나의 메서드는 하나의 단일 책임만 수행하며 5~20줄 내외 크기 유지.
- **최대 중첩 깊이 제한**: 루프와 `if`문이 **3단계(Depth) 이상 깊게 중첩되는 구조를 엄격히 금지** (최대 1~2단계 권장).
- **조기 반환(Early Return / Guard Clauses)** 및 명확한 서술적 이름을 가진 헬퍼 메서드 분리.

그러나 정적 린터(`python tools/lint_agent_rules.py`) 전수 검사 결과, 수식 솔버 및 레시피 뷰어 연동 핵심 3대 파일에서 **총 54건의 Rule 1 과도 중첩 위반**이 검출되었습니다:

| 위반 파일 경로 | 위반 건수 | 주요 중첩 원인 및 위치 |
| :--- | :---: | :--- |
| [`api/solver/FlowSummaryAggregator.java`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/api/solver/FlowSummaryAggregator.java) | **16건** | 라인 259~393: 기계 목록 집계, 서브모듈 핵융합 티어 병합, 보이드 싱크 포트 순회 루프 (4~6단계 중첩) |
| [`api/solver/MassBalanceSolver.java`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/api/solver/MassBalanceSolver.java) | **13건** | 라인 92~322: 가우스 소거법 피벗 연산, 후진 대입법, 포트 연결 확인 및 정션 BFS/DFS 탐색 (4~7단계 중첩) |
| [`integration/emi/EmiRecipeViewerAdapter.java`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/integration/emi/EmiRecipeViewerAdapter.java) | **25건** | 라인 151~488: 즐겨찾기 레시피 탐색, 출력물 ID/이름 매칭, EMI 위젯 그룹 마우스 호버 감지 (4~7단계 중첩) |

### 1.2 문제점 분석 (Problem Analysis)

1. **가독성 및 유지보수성 저하**:
   - 중첩이 4~7단계에 달하면 들여쓰기 폭이 넓어져 코드 가로 스크롤이 발생하고, 특정 `if` 블록의 진입 조건과 예외 탈출 조건을 한눈에 파악하기 어렵습니다.
2. **복합 공정 솔버 회귀 위험**:
   - `MassBalanceSolver`의 행렬 연산과 `FlowSummaryAggregator`의 유량 수지 집계는 작은 조건 분기 실수로도 전체 선형 방정식 결과가 왜곡될 수 있습니다. 깊은 중첩 구조는 엣지 케이스 방어를 누락시키기 쉽습니다.
3. **CI 정적 검증 통과 불가**:
   - Rule 1 위반 파일들은 향후 해당 라인 수정 시 Fast Pre-Flight Check에서 즉각 차단되어 정상적인 패스트트랙 개발 루프를 방해합니다.

### 1.3 설계 목표 (Design Goals)

- 3대 핵심 파일의 모든 제어 흐름 중첩 깊이를 **최대 2단계 이하로 완전 평탄화(Flattening)**합니다.
- 조기 반환 가드(Guard Clauses)를 전면 도입하여 불필요한 else 및 들여쓰기를 제거합니다.
- 복잡한 내부 루프와 연산을 단일 책임의 서술적 얕은 헬퍼 메서드(10~15줄)로 추출합니다.
- `lint_agent_rules.py` 검사 결과 **Rule 1 위반 0건**을 달성하며, 기존 단위 테스트를 100% 동일한 수치로 통과합니다.

---

## 2. 대안 비교 및 검토 (Alternatives Considered)

| 비교 항목 | 대안 A: 린터 무시 주석 추가 | 대안 B (채택): 조기 가드 + 얕은 헬퍼 메서드 분리 | 대안 C: 람다/Stream API 전면 전환 |
| :--- | :--- | :--- | :--- |
| **구조** | 위반 라인마다 경고 억제 주석 부착 | 조기 반환(`continue`/`return`)과 헬퍼 메서드로 제어 흐름 재구성 | `for` 루프를 복잡한 `.filter().map().forEach()`로 교체 |
| **Rule 1 준수** | 규칙을 회피하는 안티패턴 | 클린 코드 및 규칙 100% 준수 | 람다 내부 중첩 시 여전히 위반 가능성 존재 |
| **성능 오버헤드** | 없음 | JIT 인라이닝으로 인한 런타임 오버헤드 0 | 람다 객체 생성 및 스트림 파이프라인 가비지 컬렉션 부하 발생 |
| **디버깅 용이성** | 여전히 깊은 중첩으로 디버깅 난항 | 스택 트레이스에 서술적 헬퍼 메서드명이 노출되어 디버깅 극대화 | 람다 스택 트레이스 난해함 |
| **평가** | 엄격히 금지 | **최적안 ([ADR-011](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/docs/adr/ADR_011_CONTROL_FLOW_FLATTENING_AND_SELF_DESCRIPTIVE_CODE.md) 표준 준수)** | 핫루프 솔버에서 GC 오버헤드 유발 |

---

## 3. 핵심 유저 & 시스템 스토리 (User & System Stories)

| 시나리오 ID | 대상 모듈 | 변경 전 동작 (Before) | 변경 후 동작 (After) |
| :--- | :--- | :--- | :--- |
| **US-01** | `FlowSummaryAggregator` | 6단계 중첩 루프에서 기계 대수 및 핵융합 EU 수치 집계 | `PowerAccumulator`, `FusionAccumulator`, 서술적 헬퍼로 분리되어 2단계 이하로 평탄화 |
| **US-02** | `MassBalanceSolver` | 7단계 중첩 루프에서 가우스 소거법 전진 소거 및 피벗 연산 수행 | `eliminateRow()`, `findSingleNonZeroColumn()`, `isPortConnectedForMaterial()` 헬퍼로 분리되어 알고리즘 가독성 향상 |
| **US-03** | `EmiRecipeViewerAdapter` | 7단계 중첩 if문에서 레시피 출력 아이템 ID/이름 매칭 | `findMatchingEmiOutputIndex()` 헬퍼 및 조기 `continue` 가드로 2단계 평탄화, 정적 리플렉션 캐싱 적용 |
| **US-04** | 전체 정적 검증 | `lint_agent_rules.py` 실행 시 54건의 에러와 함께 빌드 실패 | 3대 파일 전수 스캔 시 Rule 1 위반 **0건 (PASS)** |

---

## 4. 상세 구현 및 리팩토링 내역 (Implementation Results)

### 4.1 `FlowSummaryAggregator.java`
- `PowerAccumulator`, `FusionAccumulator` 헬퍼 클래스를 구성하여 전력 및 핵융합 수치 누산 분리.
- `aggregateSharedMachineFrames()`, `aggregateMachineBreakdown()`, `aggregateMachineMetrics()`, `aggregateVoidOutputs()` 등의 얕은 헬퍼 메서드로 분리하여 중첩 깊이를 2단계 이하로 평탄화.
- 린터 검사: Rule 1 위반 0건 (16건 해소).

### 4.2 `MassBalanceSolver.java`
- `accumulateProductionContributions()`, `accumulateConsumptionContributions()`로 입출력 기여도 루프 분리.
- `findSingleNonZeroColumn()`, `eliminateRow()`, `isPortConnectedForMaterial()` 등으로 가우스 소거 및 연결성 판별 로직 평탄화.
- 린터 검사: Rule 1 위반 0건 (13건 해소).

### 4.3 `EmiRecipeViewerAdapter.java`
- `CURRENT_PAGE_FIELD` 리플렉션 필드를 클래스 초기화 시점에 1회 안전하게 정적 캐싱.
- `collectFavoriteRecipeIds()`, `renderEmiOutputs()`, `sortEmiOutputs()`, `findMatchingEmiOutputIndex()`, `isEmiStackMatch()` 등으로 즐겨찾기 및 출력물 렌더링 평탄화.
- `registerBomItemResolution()`, `resolveRecipeForStack()`, `findRecipeFromWidgetGroups()`로 BoM 등록 및 호버 감지 평탄화.
- 린터 검사: Rule 1 위반 0건 (25건 해소).

---

## 5. 검증 결과 (Verification Record)

- `python tools/lint_agent_rules.py --path ...`: 대상 3대 파일 모두 0 Violations (PASS).
- `python tools/lint_agent_rules.py --diff`: 전수 0 Violations (PASS).
- `gradlew.bat test`: `MassBalanceSolverTest`, `RecipeNodeCopyTest`, 전체 유닛 테스트 100% 통과.
