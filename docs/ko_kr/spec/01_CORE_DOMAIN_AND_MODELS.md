# [01] 코어 도메인 모델 및 결정론적 수용능력 매트릭스 (Core Domain & Models)

> 📍 **GTCalcBoard 기술 명세서 시리즈**
> [[00] 시스템 개요](00_OVERVIEW.md) ➔ **[01] 코어 도메인 모델** ➔ [[02] 수학 엔진 및 알고리즘](02_MATH_AND_ALGORITHMS.md) ➔ [[03] UI 및 렌더링 파이프라인](03_UI_AND_RENDERING_PIPELINE.md) ➔ [[04] 멀티플레이어 및 네트워크](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] 외부 연동 및 다국어](05_INTEGRATION_AND_I18N.md)

---

## 1. 핵심 데이터 모델 명세 (`com.gtceu.calcboard.api`)

### 1.1 `GTVoltageTier` (전압 티어 열거형)
그렉테크(GTCEu Modern)의 15개 전압 티어를 완벽히 정의합니다.

| 티어 (Tier) | 전압 (EU/t) | UI 축약명 | 테마 색상 (Hex ARGB) |
| :--- | :--- | :--- | :--- |
| `ULV` | 8 | ULV | `0xFF8C8C8C` |
| `LV` | 32 | LV | `0xFFDCDCDC` |
| `MV` | 128 | MV | `0xFFFF6464` |
| `HV` | 512 | HV | `0xFFFFFF64` |
| `EV` | 2,048 | EV | `0xFF6464FF` |
| `IV` | 8,192 | IV | `0xFFFF64FF` |
| `LuV` | 32,768 | LuV | `0xFF64FFFF` |
| `ZPM` | 131,072 | ZPM | `0xFFFF6464` |
| `UV` | 524,288 | UV | `0xFF64FF64` |
| `UHV` | 2,097,152 | UHV | `0xFFFF3232` |
| `UEV` | 8,388,608 | UEV | `0xFF64B4FF` |
| `UIV` | 33,554,432 | UIV | `0xFF32FF82` |
| `UXV` | 134,217,728 | UXV | `0xFFFF82FF` |
| `OpV` | 536,870,912 | OpV | `0xFF5050FF` |
| `MAX` | 2,147,483,647 | MAX | `0xFFFF8282` |

---

### 1.2 `IngredientStack` (재료 스택 모델)
입출력 포트를 통해 흐르는 아이템 및 유체 단위를 캡슐화합니다.

```java
public class IngredientStack {
    private final ResourceLocation id;        // 아이템/유체 고유 ID (예: gtceu:benzene)
    private final String displayName;          // 지역화된 표시 명칭 (예: "Benzene")
    private double amount;                     // 가동 1주기당 소모/생산량
    private final boolean isFluid;             // 유체 여부 (true: mB / false: 개수)
    private double chance;                     // 기본 획득 확률 (0.0 ~ 1.0)
    private double tierChanceBoost;            // 티어 상승당 추가 확률 (기본: 0.05 = +5%)
}
```

---

### 1.3 `RecipeNode` (순수 도메인 노드 모델)
캔버스에 배치되는 기계 카드, 발전기, 또는 복합 모듈의 전체 상태를 보유하며, 모드 특화 규칙은 `IModAdapter`로 위임합니다.

```mermaid
classDiagram
    class RecipeNode {
        +String id
        +double posX, posY
        +String name
        +List~IngredientStack~ inputs
        +List~IngredientStack~ outputs
        +double baseDurationTicks
        +double baseEUt
        +GTVoltageTier recipeTier
        +GTVoltageTier targetTier
        +OverclockMode overclockMode
        +double machineCount
        +int parallel
        +boolean isMultiblock
        +boolean isGenerator
        +boolean isFlipped
        +SteamMode steamMode
        +List~MachineAddon~ addons
        +ResourceLocation machineIcon
        +ResourceLocation recipeCategoryId
        +List~ResourceLocation~ availableWorkstations
        +NodePropertyStore properties
        +double efficiency
        +Set~Integer~ hiddenInputIndices
        +Set~Integer~ hiddenOutputIndices
        +hideInputPort(int) void
        +unhideInputPort(int) void
        +hideOutputPort(int) void
        +unhideOutputPort(int) void
        +getVisibleInputIndices() List~Integer~
        +getVisibleOutputIndices() List~Integer~
        +getTotalHiddenCount() int
        +setMachineIcon(icon) void
        +getEnergyType() EnergyType
        +getOverclockResult() OverclockResult
        +getSingleMachineEUt() double
        +getTotalEUt() double
        +getCyclesPerSecond() double
    }
```

* **클린 아키텍처 및 SPI 위임 (Pure Domain Model)**:
  - `RecipeNode`는 모든 모드(Create, Thermal, GTCEu, Vanilla 등)를 아우르는 **순수 계산 도메인 데이터 엔티티**입니다.
  - 특정 모드 전용 필드나 하드코딩된 분기를 일체 보유하지 않으며, 머신 아이콘 변경 이벤트(`setMachineIcon`), 물리적 에너지 형태 결정(`getEnergyType`), 단일 기계 소비/발전량 연산(`computeSingleMachinePower`), 노드 가동 유효성 검증(`validateNode`), 멀티블록 BOM 산출(`buildMultiblockBOM`) 등 모드 특화 동작은 `ModAdapterRegistry.getAdapterForNode(this)`를 통해 동적으로 위임됩니다.
* **`hiddenInputIndices` / `hiddenOutputIndices`**: 사용자가 카드의 복잡도를 줄이기 위해 비활성화/숨김 처리한 입출력 포트의 인덱스 집합. `RecipeNodeSerializer`를 통해 직렬화/역직렬화되며, 렌더러와 와이어 솔버는 `getVisibleInputIndices()` / `getVisibleOutputIndices()`를 참조하여 가시 포트만 배선 및 렌더링.
* **`isFlipped`**: 노드의 입력(좌)/출력(우) 포트 렌더링 방향을 좌우 수평 반전하여 복잡한 플로우차트의 배선 교차 최소화.
* **`efficiency` ($\eta \in [0.0, 1.0]$)**: 솔버(`FlowGraphSolver`, `MassBalanceSolver`)에 의해 상류 원자재 공급 제약 및 폐루프 순환 밸런스 하에서 계산된 기계의 실제 가동률.
* **`calculateEffectiveOutputRates()`**: 기계 대수, 병렬치, 오버클럭, 서브틱, 애드온 승수 및 티어 부산물 확률 부스트가 합성된 1초당 아이템/유체 생산 유량을 계산.

---

### 1.4 `NodePropertyStore` 및 `NodeProperties` (타입 세이프 확장 속성)
기존 클래스 필드를 비대화하지 않고, 모드별/기능별 특화 속성을 동적이고 타입 안전하게 관리하는 속성 저장소입니다.

```java
public class NodePropertyStore {
    private final Map<NodeProperty<?>, Object> properties = new HashMap<>();

    public <T> T get(NodeProperty<T> prop) {
        return (T) properties.getOrDefault(prop, prop.defaultValue());
    }

    public <T> void set(NodeProperty<T> prop, T value) {
        properties.put(prop, value);
    }
}
```

* **표준 속성 레지스트리 (`NodeProperties`)**:
  - `REQUIRED_REFLECTOR_TIER` (`Integer`, 기본값 `0`): 핵융합 반응기 반사판 요구 티어
  - `TURBINE_ROTOR_EFFICIENCY` (`Integer`, 기본값 `100`): 대형 터빈 로터 효율 (%)
  - `TURBINE_ROTOR_POWER` (`Integer`, 기본값 `100`): 대형 터빈 로터 파워 (%)
  - `TURBINE_ROTOR_NAME` (`String`, 기본값 `""`): 장착된 터빈 로터 이름
  - `TURBINE_HOLDER_BONUS` (`Integer`, 기본값 `0`): 로터 홀더 추가 효율 보너스 (%)
  - `CLEANROOM_TIER` (`Integer`, 기본값 `0`): 클린룸 요구 레벨
  - `EBF_TEMPERATURE` (`Integer`, 기본값 `0`): 전기로 작동 요구 온도 ($K$)
  - `BOILER_THROTTLE` (`Integer`, 기본값 `100`): 대형 보일러 가동 쓰로틀 비율 (25% ~ 100%)
  - `TARGET_BATCH_AMOUNT` (`Double`, 기본값 `0.0`): 단말/리라우트 노드 목표 배치 생산 수량
  - `TARGET_BATCH_TIME_SEC` (`Double`, 기본값 `0.0`): 희망 완료 제한 시간 (초 단위 역산 기준)

---

### 1.5 전담 계산 및 워크스테이션 해석 컴포넌트 (SRP 분해)

`RecipeNode`의 비대화를 방지하고 순수 POJO 엔티티 책임을 유지하기 위해, 유량 계산 및 워크스테이션 결정 로직을 독립 컴포넌트로 분해하였습니다:

* **`NodeRateCalculator`**:
  - 기계 대수, 병렬치, 오버클럭, 가동 주기, 서브틱 CPS, 애드온 승수 및 티어별 부산물 확률 부스트를 결합하여 초당 투입/산출 유량(`IngredientStack` Flow Rates)을 적분 연산합니다.
  - 단일 기계 수율(`getSingleMachineYieldPerSecond`) 및 실제 소비/생산량 계산 전담.
* **`NodeWorkstationResolver`**:
  - 전압 티어 변경 시 해당 티어에 대응하는 머신 워크스테이션(`ResourceLocation`)을 공식 레지스트리 및 캐시 매트릭스로부터 연역적 매칭.
  - 멀티블록 컨트롤러 유효성 및 티어별 기계 목록 필터링 전담.

---

### 1.6 `FlowGraph` 및 불변 컬렉션 캡슐화

`FlowGraph`는 캔버스 상의 전체 노드망 토폴로지를 캡슐화하며, 외부 수정에 의한 상태 불일치를 원천 차단합니다.

* **불변 뷰 캡슐화 (`Collections.unmodifiableList`)**:
  - `getNodes()` 및 `getEdges()`는 불변 뷰를 반환하여 외부에서의 임의 조작(`graph.getNodes().add(...)`)을 금지하고, 반드시 전용 메서드(`addNode`, `removeNode`, `connect`, `disconnect`)를 통해서만 변경되도록 강제합니다.
* **$O(1)$ 빠른 노드 색인 동기화 (`nodeMap`)**:
  - 노드 추가/삭제/클리어 시 내부 `Map<String, RecipeNode> nodeMap`이 완벽히 동기화되어 `getNode(id)` 질의를 $O(1)$ 시간에 보장합니다.
* **`ConnectionEdge` 불변 레코드**:
  ```java
  public record ConnectionEdge(String fromNodeId, int outputIndex, String toNodeId, int inputIndex)
  ```

---

### 1.7 `EnergyType` 및 `SteamMode` (다중 에너지 & 물리 모델)
다양한 기술 모드의 동력 및 에너지 시스템을 통합 관리합니다.

* **`EnergyType`**:
  - `ELECTRIC_EU`: GregTech 전력 (EU/t)
  - `KINETIC_SU`: Create 회전 운동 에너지 (SU, RPM)
  - `ELECTRIC_FE`: Thermal / Create New Age 전력 (RF/t, FE/t)
  - `HEAT_OR_SELF`: 스팀 보일러 및 연소기 (mB/s Steam 생성)
  - `NONE`: 무동력 / 패시브 레시피 (0 Power)
* **`SteamMode`**:
  - `NONE`: 일반 전기 모드
  - `LOW_PRESSURE`: 저압 스팀 가공 ($2.0\times$ 소요 시간, 1 EU = 2 mB Steam)
  - `HIGH_PRESSURE`: 고압 스팀 가공 ($1.0\times$ 소요 시간, 1 EU = 2 mB Steam)

---

## 2. 결정론적 수용 능력 매트릭스 (`CategoryCapabilityMatrix`)

불안정한 텍스트 툴팁 파싱 휴리스틱을 전면 배제하고, 게임 로딩 시 연역적 분석을 통해 빌드된 $O(1)$ 글로벌 불변 캐시 시스템입니다.

```mermaid
flowchart LR
    subgraph Bake["1. 사전 베이킹 파이프라인 (CategoryCapabilityMatrix.bake)"]
        GTR["GTRegistries.MACHINES 전수 순회\n(MachineDefinition.getRecipeTypes)"]
        INFO["EMI multiblock_info 구조 정의 스캔\n(구조 재료 내 ICoilType 검출)"]
        TAGS["Forge/Thermal 태그 인덱싱\n(thermal:lapidary_fuel 등)"]
        
        GTR & INFO & TAGS --> BUILDER["CategoryCapabilityMatrixBuilder"]
        BUILDER --> BAKE_PROCESS["1) 카테고리 ➔ 워크스테이션(1:N) 매핑\n2) 코일 지원 여부 판별\n3) 터빈/발전기 스펙 연역\n4) 병렬/유지보수 해치 수용능력 확정"]
        BAKE_PROCESS --> MATRIX[("CategoryCapabilityMatrix (불변 전역 맵)")]
    end

    subgraph Query["2. 런타임 O(1) 질의 파이프라인"]
        NODE["RecipeNode (레시피 선택/변환)"] --> GET["matrix.getCapability(recipeCategoryId)"]
        MATRIX --> GET
        GET --> INJECT["Node에 워크스테이션 목록 및 플래그 주입"]
        INJECT --> UI["MachineConfigDialog (유효 탭 즉시 렌더링)"]
        INJECT --> SOLVER["FlowGraphSolver (정밀 오버클럭/가열 연산)"]
    end
```

---

## 3. 복합 모듈 시스템 (`FlowGraphModuleHandler`)

다수의 복잡한 노드 그래프를 단일 복합 모듈 카드(`RecipeNode`)로 패키징(`Ctrl+G`)하거나 원래 서브그래프로 복원(`펼치기`)합니다.

```mermaid
flowchart LR
    subgraph Expanded["전개된 하위 그래프 (Sub-Graph)"]
        M1["기계 1 (원자재 처리)"] --> M2["기계 2 (중간 반응)"]
        M2 --> M3["기계 3 (정제)"]
        M3 -->|재활용 부산물| M1
    end
    
    subgraph Collapsed["단일 복합 모듈 카드 (Compound Module)"]
        CM["[모듈] 석유 정제 라인\nNet EU/t: -4,800 (EV)\n기계 12대 | 입력 2 | 출력 3"]
    end
    
    Expanded -- "그룹화 (Ctrl+G)" --> Collapsed
    Collapsed -- "펼치기 (Ctrl+G)" --> Expanded
```

1. **경계 I/O 자동 승격 (Boundary I/O Promotion)**: 내부 노드 간의 중간 연결선은 은닉되고, 외부와 연결된 원자재/최종 제품만 모듈 외곽 포트로 자동 승격.
2. **와이어 리매핑 (Wire Remapping)**: 외부에서 연결되어 있던 와이어의 `ConnectionEdge`가 신규 모듈 카드 포트로 재배선.
3. **비례 스케일링 (Proportional Scaling)**: 모듈 카드의 기계 대수를 변경하면 내부 하위 그래프의 모든 기계 대수와 유량이 동일 비율로 연동 스케일링.

---

## 4. 직렬화, 클립보드 및 디스크 관리 (`BlueprintCodec`, `BlueprintFileManager`, `NodeClipboard`)

### 4.1 `BlueprintCodec` (블루프린트 직렬화 코덱)
그래프의 토폴로지, 노드 좌표, 장착 애드온, 전압 티어, 뷰포트 및 메타데이터를 NBT로 변환한 후 GZIP 압축 및 Base64 인코딩을 수행합니다. 공유 및 채팅 식별성을 위해 제목 프리픽스가 포함된 포맷을 지원하며, 레거시 코드와 완벽히 상호 호환됩니다.

$$\text{Blueprint String} = \text{"GTBOARD:"} + [\text{Title} + \text{":"}] + \text{Base64}\Big(\text{GZIP}\big(\text{BlueprintPackage.serializeNBT()}\big)\Big)$$

### 4.2 `BlueprintFileManager` (디스크 블루프린트 파일 관리자)
* **저장 위치**: `<gameDir>/gtcalcboard/blueprints/`
* **파일 형식**: `.gtcb` (원자적 쓰기 `Atomic Move`를 통한 손상 방지 압축 NBT)
* **주요 기능**: 개별 블루프린트 저장, 로컬 블루프린트 목록 스캔 및 메타데이터 추출, 파일 삭제 및 운영체제 폴더 탐색기 연동.

### 4.3 `NodeClipboard` (클립보드 관리자)
* 선택된 노드군 및 노드 간의 내부 연결선을 클립보드에 복사(`Ctrl+C`) / 잘라내기(`Ctrl+X`).
* 붙여넣기(`Ctrl+V`) 시 새로운 고유 ID(UUID)를 발급하고, 마우스 커서 또는 캔버스 중심 기준 $+20\text{px}$ 오프셋을 적용하여 배치.

---

## 5. 실행 취소 / 다시 실행 (`HistoryManager`, `BoardCommand`)

커맨드 패턴(Command Pattern) 기반으로 모든 캔버스 조작을 단위 델타(Delta)로 기록합니다.

* **지원 커맨드 목록**: `MoveNodesCommand`, `AddConnectionCommand` / `RemoveConnectionCommand`, `AddNodesCommand` / `RemoveNodesCommand`, `ModifyPropertyCommand`, `GroupModuleCommand` / `ExpandModuleCommand`, `ResizeFrameCommand`.
* **성능 최적화**: 1,000단계 이상의 Undo/Redo 스택을 유지하면서도 2MB 미만의 메모리 사용.

---

## 6. 캔버스 그룹 프레임 및 공유 기계 풀 (`CanvasGroupFrame`)

시각적 그룹화 영역 및 복수 레시피 시간 분할 공유(Time-Sharing Machine Pool)를 관리합니다.

* **`isSharedMachineFrame`**: 프레임 내부의 모든 기계 레시피가 단일 물리 기계를 시간 분할하여 가동하는 모드.
* **가동 분담률 계산**: $\text{Total Duty} = \sum \text{machineCount}_i$, 필요 기계 대수 = $\lceil \text{Total Duty} \rceil$.
* **하드웨어 일괄 동기화 (`syncHardwareConfig`)**: 프레임 헤더 설정창을 통해 내부 모든 기계의 전압 티어, 오버클럭 모드, 병렬 수, 장착 애드온을 일괄 전파.
* **프레임 크기 자동 맞춤 (`autoFit`)**: 프레임 내에 속하거나 걸쳐 있는 모든 노드를 감싸도록 패딩 24px 기준으로 바운딩 박스 자동 계산.

---

## 7. 포트 식별 불변 레코드 (`PortRef`)

캔버스 상의 개별 입출력 슬롯/포트 위치를 특정하는 경량 불변 레코드입니다.

```java
public record PortRef(String nodeId, boolean isInput, int portIndex) {}
```

* **다중 포트 선택 및 범위 선택**: Windows 탐색기 방식의 `Ctrl + 클릭` 개별 토글, `Shift + 클릭` 연속 포트 범위 선택 지원.
* **번들 와이어 일괄 배선**: 다중 선택된 포트들로부터 단일 드래그로 다중 베지어 번들 곡선 생성 및 정션/머신 풀 일괄 배선 연동.

---

## 8. 카테고리별 기본 기계 프리셋 시스템 (`CategoryMachinePreset`, `CategoryMachinePresetManager`)

레시피 유형 및 카테고리(`categoryId`)별로 선호하는 기계 모델, 전압 티어, 병렬 수, 오버클럭 모드 및 장착 애드온 설정을 기억하고, 해당 카테고리의 신규 노드 배치 시 자동으로 설정을 적용합니다.

* **도메인 엔티티 (`CategoryMachinePreset`)**:
  - 특정 레시피 카테고리에 바인딩된 머신 아이콘, 멀티블록 여부, 목표 전압 티어, 병렬 수, 오버클럭 모드, 증기 모드, 하드웨어 애드온 목록, 노드 프로퍼티, 쓰레딩 설정을 캡슐화.
  - `applyTo(RecipeNode node)`: 신규 노드 생성 시 레시피의 최소 요구 전압 티어를 안전하게 보존하며 프리셋 설정을 주입.
* **프리셋 매니저 (`CategoryMachinePresetManager`)**:
  - 싱글톤 패턴 기반의 인메모리 레지스트리 및 NBT 영속화 관리 (`serializeNBT` / `deserializeNBT`).
  - 보드 설정 다이얼로그(`BoardSettingsDialog`) 및 머신 설정 다이얼로그(`MachineConfigDialog`)를 통한 CRUD 인터페이스 제공.

---

> ➡️ **다음 장으로 이동**: [[02] 수학적 연산 엔진 및 그래프 해석 알고리즘](02_MATH_AND_ALGORITHMS.md)
