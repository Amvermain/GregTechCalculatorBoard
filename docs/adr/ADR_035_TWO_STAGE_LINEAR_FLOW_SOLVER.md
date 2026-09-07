# ADR-035: 2단계 선형 연립방정식 유량 솔버 및 정수 양자화 아키텍처
# (Two-Stage Linear Flow Balance Solver & Integer Quantization Architecture)

* **문서 번호**: ADR-035
* **상태 (Status)**: `IMPLEMENTED`
* **적용 버전 (Implemented Version)**: `v2.2.0-alpha.4`
* **결정/완료 일자 (Decided Date)**: 2026-09-08
* **선행 ADR**: [ADR-022](ADR_022_CLOSED_LOOP_RECIRCULATION_AND_SUPPLY_ALLOCATION.md), [ADR-031](ADR_031_SHARED_MACHINE_POOL_AUTO_RATIO.md), [ADR-034](ADR_034_JUNCTION_BUFFER_AND_ANCHOR_SYSTEM.md)

---

## 1. 개요 및 배경 (Context & Motivation)

기존 `AutoRatioEngine`은 초기 비순환 유향 그래프(DAG) 트리를 전제로 설계된 BFS 탐색 알고리즘을 기반으로 동작했습니다. 여기에 폐순환 재순환 루프, 정션 노드 고정 드레인/서플라이, 공유 기계 풀(Shared Machine Pool) 등 복합 제약 조건이 누적되면서 다음과 같은 구조적 한계가 발생했습니다:

1. **외생적 반복 클릭 문제 (다회 클릭 수렴 현상)**:
   - 병목 해결(`resolveBottlenecksPass`)과 완화 루프(`executeRelaxationPasses`)가 분리되어, 기계 대수가 변경된 후 다운스트림 및 순환 루프로 변경된 유량이 즉시 재전파되지 못했습니다.
   - 이로 인해 플레이어가 인게임에서 Auto-Ratio 버튼을 3~4회 이상 연속으로 클릭해야만 점진적으로 유량이 전파되어 수렴하는 사용자 경험 저하가 발생했습니다.
2. **국소 휴리스틱 가드의 오탐**:
   - `ProcessStabilityAnalyzer`의 단일 기계 배율 곱셈 기반 사이클 이득($\rho$) 판별 방식은 정상적인 순환 완충 공정을 발산으로 오탐하거나, 정상적인 연산을 조기에 차단하는 부작용을 유발했습니다.

본 ADR은 이러한 기술 부채를 청산하고, **2단계 선형 연립방정식 유량 솔버(Two-Stage Linear Flow Balance Solver)**를 구축하여 단 1회 실행으로 결정론적 유량 균형을 보장하도록 개편했습니다.

---

## 2. 결정 사항 (Decisions)

### 2.1 2단계 물류 수지 모델 (Two-Stage Architecture)

1. **1단계: 연속 선형 연립방정식 유량 해석 (Stage 1: Continuous Linear Solve)**
   - 그래프 내 모든 활성 기계 노드를 미지수($x_i \ge 0$)로 모델링.
   - 각 자원 포트 네트워크(Disjoint-Set 기반 분리) 단위의 질량 보존 방정식($\sum P_i x_i - \sum C_j x_j = \text{Constant}$) 수립.
   - 기준 기계(Anchor) 제약식($x_{\text{anchor}} = K$) 및 공유 기계 풀 용량 제약식($\sum_{i \in \text{Pool}} x_i = C_{\text{pool}}$) 결합.
   - 외부 의존성 없는 순수 Java $O(N^3)$ 가우스-조던 소거기(`GaussJordanEliminator`)로 부분 피보팅(Partial Pivoting) 및 잉여(Surplus, $0 \cdot x = -c, c > 0$) 허용 소거를 수행하여 이상적인 연속 가동 배율 도출.

2. **2단계: 이산 정수 양자화 (Stage 2: Discrete Quantization)**
   - 폐순환 루프(Cycle) 및 상류 노드는 자원 결핍 방지를 위해 정수 올림(`Math.ceil`) 적용.
   - 순수 하류 종단 노드(Pure Downstream)는 과잉 생산 방지를 위해 정수 내림(`Math.floor`) 적용.
   - 정수 모드(`integerCounts = false`)인 경우 1단계 연속 해를 그대로 채택.

### 2.2 패키지 및 모듈 구성

* `com.gtceu.calcboard.api.solver.linear.GaussJordanEliminator`: 부분 피보팅 및 공급 과잉 잉여 허용 선형 방정식 소거기.
* `com.gtceu.calcboard.api.solver.linear.LinearEquationSystem`: 위상 정렬 기반 미지수 인덱싱 및 행렬 $A$, 벡터 $b$ 캡슐화 객체.
* `com.gtceu.calcboard.api.solver.linear.TwoStageLinearFlowSolver`: Disjoint-Set 포트 네트워크 분리, 2단계 파이프라인 조율 엔진.
* `com.gtceu.calcboard.api.solver.AutoRatioEngine`: 1차 고속 선형 솔버 호출 및 실패 시 기존 안정성 엔진 폴백 구조.

---

## 3. 결과 및 영향 (Consequences)

* **단 1회 클릭 수렴**: 순환 루프 및 복합 정션 공정에서 사용자가 단 1회 클릭하는 것만으로 전체 공정이 즉시 안정 평형 상태로 계산됩니다.
* **정밀한 질량 보존 보장**: 폐순환 루프 내부의 각 기계 대수가 물리적 수급 요구량을 완벽하게 만족하도록 결정론적으로 계산됩니다.
* **하위 호환성 100% 유지**: 기존 공개 API 시그니처(`FlowGraph.autoRatioFromAnchor`, `AutoRatioEngine`) 및 기존 테스트 스위트와의 100% 호환성을 유지합니다.

---

## 4. 검증 결과 (Verification Results)

* **수학 엔진 단위 테스트 (`GaussJordanEliminatorTest`)**: 정방 행렬, 잉여 공급, 단일 미지수, 불능 시스템 100% 통과.
* **선형 솔버 통합 테스트 (`TwoStageLinearFlowSolverTest`)**: 선형 체인, 정션 드레인 앵커, 폐순환 루프, Nether Star 복합 재순환 4개 시나리오 100% 통과.
* **포괄적 발산 매트릭스 테스트 (`ComprehensiveDivergenceMatrixTest`)**: 9개 공정 전수 통과.
* **전체 솔버 단위 테스트 전수 검증 (`com.gtceu.calcboard.api.solver.*`)**: 100% 통과 (`BUILD SUCCESSFUL`).
* **정적 규칙 린터 (`tools/lint_agent_rules.py --diff`)**: 0건 위반.
* **다국어 4개국어 동기화 (`tools/check_i18n.py`)**: 1,185개 키 100% 일치 (0 Errors).
