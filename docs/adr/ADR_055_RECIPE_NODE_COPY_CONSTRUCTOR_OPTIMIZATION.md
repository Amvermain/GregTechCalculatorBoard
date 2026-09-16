# ADR-055: RecipeNode 직접 복제 생성자 도입 및 NBT 왕복 오버헤드 제거 명세
(RecipeNode Direct Copy Constructor Introduction & NBT Round-trip Overhead Elimination)

- **문서 번호**: ADR-055
- **대상 버전**: `v2.3.0`
- **상태**: `IMPLEMENTED`
- **결정/완료일**: 2026-09-14
- **주관 계층**:
  - Core Domain Layer (`api.model.RecipeNode`, `api.model.role.INodeRole`, `api.property.NodePropertyStore`)
  - Storage & Clipboard Layer (`api.storage.NodeClipboard`, `api.template.MachineHardwareTemplate`)

---

## 1. 개요 및 배경 (Motivation)

### 1.1 현황 분석 및 성능 병목 (Current Context & Overhead Analysis)

`GregTechCalculatorBoard`에서 노드를 복제하거나 클립보드에서 붙여넣을 때([`NodeClipboard.java`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/api/storage/NodeClipboard.java)), 또는 캔버스 템플릿을 인스턴스화할 때 노드 복제 연산이 빈번하게 수행됩니다.

과거 [`RecipeNode.java`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/api/model/RecipeNode.java)의 `copy()` 메서드는 다음과 같이 구현되어 있었습니다:

```java
public RecipeNode copy() {
    return deserializeNBT(serializeNBT());
}
```

또한 [`NodeClipboard.java`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/api/storage/NodeClipboard.java)의 붙여넣기 루프에서도 동일하게 `RecipeNode.deserializeNBT(origNode.serializeNBT())`를 호출하고 있었습니다.

### 1.2 문제점 분석 (Problem Analysis)

1. **불필요한 직렬화/역직렬화 I/O 오버헤드**:
   - 동일한 JVM 힙 메모리 상에서 단순히 객체를 복제하는데도 전체 노드 상태를 NBT `CompoundTag` 트리로 인코딩한 후 다시 파싱하여 디코딩하는 과정을 거쳤습니다.
   - 단일 노드 복제 시 수십 개의 임시 `CompoundTag`, `ListTag`, 문자열 키 인스턴스가 힙에 생성되고 즉시 버려져 가비지 컬렉터(GC)에 단기 객체 할당 압박을 가했습니다.
2. **대규모 클립보드 붙여넣기 시 화면 끊김 (Frame Drop)**:
   - 복합 공정 템플릿(30~50개 노드 및 수십 개의 배선)을 클립보드에서 붙여넣거나 복제할 때, 수백 번의 NBT 직렬화/역직렬화 왕복이 발생했습니다.
3. **불변 레코드의 이점 미활용**:
   - [ADR-050](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/docs/adr/ADR_050_IMMUTABLE_RECIPE_SPEC_AND_DYNAMIC_PORT_PROJECTION.md)을 통해 기본 레시피 명세가 불변 레코드인 `RecipeSpec`으로 전환되었음에도 NBT 왕복으로 인해 불변 객체마저 매번 분해 후 재구축했습니다.

### 1.3 설계 목표 (Design Goals)

- `RecipeNode`에 직접 복제 생성자 `public RecipeNode(RecipeNode other, String newId)` 및 `copyFieldsFrom`을 도입합니다.
- `INodeRole` 인터페이스에 `default INodeRole copy()` 및 `INodeRole copy(Set<FlowGraph> visitedGraphs, int depth)` 규격을 정의하여 4대 역할(Machine, Junction, BoundaryPin, SubPageModule)이 메모리 상에서 직접 복제되도록 구현합니다.
- `NodePropertyStore` 및 `NodePortVisibility`에 복사 메서드를 도입하여 메모리 내 직접 복제를 수행합니다.
- `RecipeNode.copy()` 및 `NodeClipboard.java`를 직접 복제 생성자 기반으로 전환하여 NBT 왕복 및 GC 할당을 완전히 제거합니다.

---

## 2. 대안 비교 및 검토 (Alternatives Considered)

| 비교 항목 | 대안 A: Java `Cloneable` / `clone()` 구현 | 대안 B (채택): 명시적 직접 복제 생성자 도입 | 대안 C: 현행 NBT 직렬화 왕복 유지 |
| :--- | :--- | :--- | :--- |
| **구현 방식** | `implements Cloneable`, `super.clone()` 얕은 복사 후 수동 필드 수정 | `public RecipeNode(RecipeNode other, String newId)` 생성자 | `deserializeNBT(serializeNBT())` |
| **타입 안정성** | `CloneNotSupportedException` 검사 예외 및 불안정한 얕은 복사 위험 | 강력한 컴파일 타임 타입 검사 및 명시적 깊은 복사 | 런타임 NBT 키 파싱 의존 |
| **메모리 할당** | 최소화됨 | 최소화됨 (NBT 태그 객체 할당 0) | 수십 개의 임시 NBT 태그 낭비 |
| **역할 컴포지션** | `INodeRole` 컴포지션 복제 시 순환 참조 위험 | `role.copy()`를 통해 단일 책임으로 안전하게 복제 | NBT 문자열 매핑 및 팩토리 재생성 |
| **평가** | Java 표준 비권장 패턴 | **최적안 (안전하고 명시적인 객체지향 설계)** | 성능 병목 지속 |

---

## 3. 핵심 유저 & 시스템 스토리 (User & System Stories)

| 시나리오 ID | 트리거 (Trigger) | 변경 전 동작 (Before) | 변경 후 동작 (After) |
| :--- | :--- | :--- | :--- |
| **US-01** | 플레이어가 노드 카드 선택 후 Ctrl+C ➔ Ctrl+V (복사/붙여넣기) | 노드 수만큼 NBT serialize/deserialize 왕복 발생, GC 부하 발생 | 직접 메모리 복사 생성자를 통해 지연 없이 즉각 복제 노드 생성 |
| **US-02** | 50개 노드가 포함된 복합 공정 프레임 복제 | 수천 개의 NBT 태그 객체가 힙에 순간 할당되어 프레임 드랍 유발 | 순수 Java 참조 복사 및 컬렉션 복제만으로 부드럽게 붙여넣기 완료 |
| **US-03** | 하드웨어 애드온이 장착된 복잡한 기계 노드 복제 | NBT 직렬화 후 문자열 키로 애드온 목록을 역직렬화 복원 | `NodePropertyStore` 및 애드온 목록이 깊은 복사되어 상태 100% 보존 |
| **US-04** | 불변 레시피 명세(`RecipeSpec`) 복제 | `RecipeSpec`을 NBT로 분해 후 새로 인스턴스화 | 불변 레코드 참조를 그대로 공유하여 메모리 점유 및 연산 낭비 0 |

---

## 4. 상세 기술 아키텍처 및 구현 내역 (Implementation Results)

### 4.1 필드별 복제 전략 매트릭스

| 필드 구분 | 필드명 | 복제 방식 (Copy Strategy) | 이유 |
| :--- | :--- | :--- | :--- |
| **식별자** | `id` | 새 UUID 할당 (`newId`) | 노드 식별자 고유성(Uniqueness) 보장 |
| **프리미티브/문자열** | `name`, `hasCustomName`, `posX`, `posY`, `cardWidth`, `cardHeight`, `isFlipped`, `isBaseNode` | 직접 대입 (Direct Assignment) | 값 타입 및 불변 String |
| **불변 레코드** | `baseSpec` (`RecipeSpec`) | 참조 공유 (`this.baseSpec = other.baseSpec`) | [ADR-050](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/docs/adr/ADR_050_IMMUTABLE_RECIPE_SPEC_AND_DYNAMIC_PORT_PROJECTION.md) 불변 객체이므로 안전하게 공유 가능 |
| **입출력 포트** | `inputs`, `outputs` | `IngredientStack::copy` 순회 복제 | 포트 스택 수량 변조 격리 |
| **포트 가시성** | `portVisibility` | `this.portVisibility.copyFrom(other.portVisibility)` | 가시성 비트셋 독립성 보장 |
| **동적 속성** | `properties` | `new NodePropertyStore(other.properties)` | 내부 속성 맵 복제 및 리스너 재등록 |
| **역할 컴포지션** | `role` (`INodeRole`) | `other.role.copy(visitedGraphs, depth)` 다형성 복제 | 각 역할별 고유 상태 독립 복제 및 순환 참조 방어 |

### 4.2 순환 참조 그래프 복제 방어 (Circular SubGraph Protection)
- `SubPageModuleNodeRole.copy` 및 `FlowGraph.copy`에 방문 집합(`visitedGraphs`, `IdentityHashMap` 기반)과 최대 깊이(`depth < 10`) 가드를 적용하여 상호 참조 서브모듈(A ➔ B ➔ A)에서도 무한 재귀 및 스택 오버플로우 없이 안전하게 복제 완료.

### 4.3 다형적 BoundaryPinNode 복제 보존
- `RecipeNode.copy(...)` 시 `this instanceof BoundaryPinNode`인 경우 `ModuleOutputPin` 또는 `ModuleInputPin` 인스턴스로 분기 생성하여 레이아웃 계산기 및 UI 렌더러에서의 `ClassCastException` 방지.

---

## 5. 검증 결과 (Verification Record)

- `RecipeNodeCopyTest.java`:
  - `testMachineNodeCopyEquivalenceAndIndependence`: 머신 노드 복제 동등성 및 참조 격리 검증 통과.
  - `testJunctionNodeCopyEquivalenceAndIndependence`: 정션 노드 복제 동등성 및 참조 격리 검증 통과.
  - `testBoundaryPinNodeCopyEquivalenceAndIndependence`: 경계 핀 노드 복제 및 다형성 검증 통과.
  - `testSubPageModuleNodeCopyWithCircularSubgraph`: 순환 서브모듈 복제 안전성 검증 통과.
  - `testNbtSerializationParityWithOriginal`: 원본 노드와 복제 노드 간의 NBT 직렬화 완전 일치 검증 통과.
- `GraphValidationAndRecursionTest`: 상호 참조 서브모듈 그래프 복제 및 직렬화 안전성 통과.
- `MassBalanceSolverTest` 및 전체 Gradle 유닛 테스트 100% 통과.
