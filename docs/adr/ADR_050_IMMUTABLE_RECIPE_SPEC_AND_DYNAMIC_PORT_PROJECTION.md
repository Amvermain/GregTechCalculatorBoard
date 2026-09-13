# ADR-050: 불변 레시피 명세 및 동적 하드웨어 포트 프로젝션 아키텍처 명세
(Immutable Recipe Specification & Dynamic Hardware Port Projection Architecture)

- **문서 번호**: ADR-050
- **대상 버전**: `v2.2.1`
- **상태**: `IMPLEMENTED`
- **결정/완료일**: 2026-09-13
- **주관 계층**:
  - Core Domain Layer (`api.model.RecipeNode`, `api.model.RecipeSpec`, `api.model.PortRole`, `api.model.ProjectedPort`)
  - Mod Adapter SPI Layer (`api.spi.IModAdapter`, `api.spi.extension.IPortProjectionProvider`)
  - Flow Solver & Engine Layer (`api.solver.linear.TwoStageLinearFlowSolver`, `api.model.NodeRateCalculator`)
  - Graph Topology Layer (`api.model.ConnectionEdge`, `api.model.FlowGraph`)
  - Client GUI Layer (`client.gui.render.NodeCardRenderer`, `client.gui.render.NodePortTooltipRenderer`, `client.gui.dialog.MachineConfigDialog`)

---

## 1. 개요 및 배경 (Motivation)

### 1.1 현황 및 당면 과제 (Current Architectural Smells)

기존 `GregTechCalculatorBoard`의 `RecipeNode`는 레시피 뷰어(EMI/JEI)에서 가져온 레시피의 재료 입출력 목록을 `List<IngredientStack> inputs`, `outputs` 필드에 직접 보관했습니다. 그러나 하드웨어 애드온(연소 엔진 산화제, 윤활유, 스팀 보일러 변환, 모듈러 연소 프레임 도킹 슬롯 등)이 설치되거나 변경될 때마다 이 가변(Mutable) 컬렉션을 직접 `clear()`, `removeIf()`, `add()`로 제자리 변조(In-place Mutation)하는 구조적 한계를 안고 있었습니다.

이러한 제자리 변조 방식은 다음과 같은 상태 불일치 및 데이터 유실 문제를 유발했습니다:

1. **불변 원본(Source of Truth)의 파괴 및 레시피 데이터 유실**:
   - 모듈러 연소 프레임(MCF) 등 특수 하드웨어로 전환 시 `node.getInputs().clear()`를 실행하면서 원래 플레이어가 지정했던 원본 레시피 명세가 완전히 지워져, 기계를 다시 일반 연소 발전기로 바꾸었을 때 원본 레시피로 안전하게 복원할 수 없었습니다.
2. **이전 레시피 재료의 상태 잔류 버그 (Stale Recipe Retention)**:
   - 기계를 전환할 때 기존 레시피에 있던 연료(예: `Rocket Fuel`)를 수동으로 제거하지 않으면 새 하드웨어가 장착된 상태에서도 이전 연료가 입력 목록에 그대로 남아 잘못된 유량 수지가 계산되었습니다.
3. **명령적 동기화(Imperative Sync)의 파편화 및 누수 위험**:
   - 상태가 동적으로 계산(Derived)되지 않고 물리적 리스트로 관리되므로, 다이얼로그 열기/닫기, 프로퍼티 변경, 애드온 추가/제거, Undo/Redo 등 모든 상호작용 진입점마다 수동 동기화 함수를 끼워 넣어야 했습니다.
4. **포트 인덱스 불안정성 및 배선(Wire) 꼬임 위험**:
   - 노드 간 연결선(`ConnectionEdge`)은 포트 인덱스(`fromSlot`, `toSlot`)에 의존하므로, 보조 유체 포트가 임의의 위치에 추가되면 기존 배선이 엉뚱한 포트로 연결되거나 무효화되는 취약점이 존재했습니다.

### 1.2 설계 목표 (Design Goals)

1. **불변 레시피 명세(Base Recipe Spec)와 동적 포트 프로젝션(Effective Port Projection)의 분리**:
   - 원본 레시피 명세를 절대 변조되지 않는 불변 레코드(`RecipeSpec`)로 보존하고, 솔버와 렌더러가 소비하는 입출력 포트는 현재 하드웨어 상태를 기반으로 순수 함수(Pure Function)에 의해 동적으로 투영되도록 설계합니다.
2. **포트 정체성(Port Identity)의 분리 (Core Ports vs Auxiliary Ports)**:
   - 레시피 고유의 주 공정 포트(Core Port)와 하드웨어 애드온에 의해 부착된 보조 포트(Auxiliary Port)를 분리하여, 애드온을 장착/해제해도 주 공정 배선 인덱스(0..N-1)가 흔들리지 않도록 보장합니다.
3. **명령형 수동 동기화 로직 제거**:
   - 반응형 무효화 플래그(`portsDirty`) 기반의 지연 캐싱(Lazy Evaluation) 모델로 전환하여 수동 동기화 누수를 방지합니다.
4. **결정론적 복원성 및 메멘토 무손실성**:
   - 애드온을 해제하거나 기계를 변경하면 별도의 정리 코드 없이도 기본 레시피가 100% 무손실로 자연 복원되도록 보장합니다.

---

## 2. 핵심 유저 & 시스템 스토리 (User & System Stories)

| 시나리오 ID | 트리거 (Trigger) | 변경 전 동작 (Before) | 변경 후 동작 (After) |
| :--- | :--- | :--- | :--- |
| **US-01** | 로켓 연료 레시피 노드를 MCF 프레임으로 기계 전환 | 로켓 연료가 입력에 잔류하거나, MCF 모듈 연료만 덮어써져 원래 로켓 연료 레시피 정보 영구 유실 | 원본 레시피는 `baseSpec`에 안전 보존되며, 런타임에는 MCF 도킹 모듈 연료 및 냉각수만 투영됨 |
| **US-02** | MCF 프레임을 다시 단일 로켓 엔진으로 재전환 | MCF 모듈 데이터가 지워지면서 빈 입력 노드가 됨 (플레이어가 레시피 재검색 필요) | 하드웨어 프로젝션이 해제되면서 원래의 불변 로켓 연료 레시피가 즉시 자동 복원됨 |
| **US-03** | 대형 연소 엔진에 산화제 부스트 애드온 장착 | `node.getInputs()`에 산소가 추가되어 기존 배선 포트 인덱스가 밀릴 위험 존재 | 주 연료 포트는 그대로 유지되고, 하단에 별도의 Auxiliary 포트가 동적으로 투영되어 배선 무결성 유지 |
| **US-04** | 산화제 부스트 애드온 제거 | 수동 동기화를 부르지 않으면 산소 입력 포트가 잔류함 | 애드온 제거 즉시 프로젝션 캐시가 무효화되어 산소 포트가 자연 소멸하고 동기화 누락 차단 |
| **US-05** | 레시피/하드웨어 변경에 대한 Ctrl+Z (Undo) | 제자리 변조된 컬렉션 복원이 불안정하여 이전 보조 유체 상태가 꼬임 | 스냅샷은 불변 `baseSpec`과 하드웨어 프로퍼티를 저장하므로 100% 무손실 결정론적 복구 달성 |

---

## 3. 시스템 아키텍처 명세 (Architecture Specification)

### 3.1 핵심 도메인 모델

1. **`RecipeSpec` (불변 레시피 명세 레코드)**:
   - `String recipeId`, `ResourceLocation categoryId`, `double baseDurationTicks`, `double baseEUt`, `List<IngredientStack> baseInputs`, `List<IngredientStack> baseOutputs`.
   - 생성자에서 불변 컬렉션 복사(`List.copyOf`)를 강제하여 외부 변조를 완전 차단.
   - NBT 직렬화/역직렬화 지원 (`serializeNBT`, `deserializeNBT`).

2. **`PortRole` 및 `ProjectedPort` (포트 정체성 캡슐화)**:
   - `PortRole`: `CORE_RECIPE` (주 공정, 인덱스 보존), `AUXILIARY_INPUT` (하드웨어 보조 입력), `AUXILIARY_OUTPUT` (하드웨어 보조 출력).
   - `ProjectedPort`: `(IngredientStack stack, PortRole role, int coreIndex, String sourceAddonId)`.

3. **`IPortProjectionProvider` (모드 어댑터 확장 SPI)**:
   - `List<ProjectedPort> projectInputPorts(RecipeNode node, RecipeSpec baseSpec)`
   - `List<ProjectedPort> projectOutputPorts(RecipeNode node, RecipeSpec baseSpec)`

4. **`GTCEuPortProjector` (GregTech CEu Modern 포트 프로젝터)**:
   - 일반 처리 기계: `baseSpec.baseInputs()`를 `CORE_RECIPE`로 매핑.
   - 스팀 모드 활성화 시: `AUXILIARY_INPUT` 역할의 증기(`gtceu:steam`) 포트 주입.
   - 대형 연소 엔진(`LCE` / `ECE`): 산소/액체산소 부스트 포트 주입.
   - Star Technology 모듈: 윤활유 및 산화제 포트 주입.
   - 모듈러 연소 프레임(`START_MCF`): 도킹된 모듈 연료 포트 + 중앙 냉각수 포트를 동적 생성하며 원본 `baseSpec`은 그대로 보존.

5. **`RecipeNode` 지연 캐싱 및 상태 복원**:
   - `baseSpec` 필드, `portsDirty` 플래그, `projectedInputs`/`projectedOutputs` 캐시.
   - 하드웨어 변경 시 `markPortsDirty()` 호출.
   - `restoreBaseRecipe()`: `baseSpec`의 원본 입출력을 `inputs`/`outputs`로 즉시 복원.
   - `syncProjectedPorts()`: 현재 하드웨어 사양을 기반으로 입출력 컬렉션을 프로젝션된 포트로 동기화.

6. **하위 호환성 NBT 듀얼 라이트**:
   - 새 세이브: `baseSpec` 태그와 하위 호환용 `inputs`/`outputs` 태그 동시 기록.
   - 구 세이브: `baseSpec` 태그가 없으면 로드된 `inputs`/`outputs`를 기반으로 `RecipeSpec`을 1회 재구성하여 무손실 마이그레이션.

---

## 4. 결과 및 파급 효과 (Consequences)

- **도메인 무결성 보장**: 레시피 전환과 기계 변경 시 제자리 컬렉션 파괴가 완전히 해소되어, 복합 기계(MCF)와 단일 기계 간의 자유로운 전환이 데이터 유실 없이 가능해졌습니다.
- **배선 앵커 안정화**: Core 포트의 인덱스가 0..N-1로 일정하게 유지되어 하드웨어 애드온의 장착/해제 시에도 주 공정 배선이 끊어지거나 밀리지 않습니다.
- **Undo/Redo 완성도 향상**: `SwitchRecipeCommand` 스냅샷에 `baseSpec`이 포함되어 실행 취소 및 재실행 시 보조 포트 상태까지 100% 결정론적으로 복원됩니다.
- **GUI 시각적 피드백**: 툴팁에 `[⚙ Hardware Auxiliary Input - <addon>]` 태그가 노출되어 하드웨어 애드온으로 주입된 보조 유체의 출처를 직관적으로 확인할 수 있습니다.
