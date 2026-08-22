# RFC-005: 확장형 노드 프로퍼티 & 선언적 특수 기계 컴포넌트 시스템 (Extensible Node Property & Special Capability System)

* **문서 번호**: RFC-005
* **상태**: `PROPOSED / PLANNING`
* **주제**: 하드코딩 필드 증식을 차단하는 타입 안전한 `NodePropertyStore`, 선언적 `IRecipePropertyExtractor` 파이프라인 및 자동 뱃지 렌더링 인프라

---

## 1. 개요 및 배경 (Motivation)

### 1.1 현재 시스템의 구조적 한계 (Field Proliferation)
현재 특수 기계(터빈 발전기, EBF 용광로, 향후 핵융합로, Create 회전기기, 써멀 기계 등)가 추가될 때마다:
* `RecipeNode.java`에 `rotorEfficiency`, `rotorPower`, `recipeTemperature`, `startEU`, `rpm`, `cleanroom` 등의 필드가 무분별하게 누적되고 있습니다.
* `EmiRecipeConverter.java`, `NodeCardRenderer.java`, `CategoryCapabilityMatrix.java` 곳곳에 `if (isGenerator) ... else if (isFusion) ...` 식의 **하드코딩된 분기(Hardcoded Branching)**가 급증하여 유지보수 비용과 버그 위험이 상승합니다.

### 1.2 핵심 목표
1. **타입 안전한 동적 프로퍼티 컨테이너 (`NodePropertyStore`)**: 기계 종류별로 필요한 특수 데이터만 동적으로 보관하고 NBT 직렬화를 자동화.
2. **선언적 데이터 추출 파이프라인 (`IRecipePropertyExtractor`)**: 레시피 소스(GTCEu, Create, Thermal 등)에서 특수 메타데이터(온도, 시동 전력, RPM, 클린룸 등)를 파싱하는 로직을 모듈형으로 분리.
3. **자동 뱃지 & 툴팁 렌더링 파이프라인 (`NodeBadgeRegistry`)**: 노드가 가진 프로퍼티를 감지하여 UI 뱃지와 툴팁을 자동으로 조립 렌더링.

---

## 2. 시스템 아키텍처 (System Architecture)

### 2.1 전체 파이프라인 구조도

```mermaid
flowchart TD
    subgraph INGESTION["1. 레시피 파싱 단계 (EmiRecipeConverter)"]
        RECIPE["EmiRecipe / GTRecipe NBT"]
        PIPELINE["RecipeExtractorPipeline"]
        EXT_FUSION["FusionPropertyExtractor (eu_to_start -> FUSION_START_EU)"]
        EXT_EBF["EbfPropertyExtractor (ebf_temp -> EBF_TEMPERATURE)"]
        EXT_CLEAN["CleanroomExtractor (cleanroom -> CLEANROOM_REQUIRED)"]
        EXT_CREATE["CreateExtractor (rpm -> KINETIC_RPM)"]

        RECIPE --> PIPELINE
        PIPELINE --> EXT_FUSION & EXT_EBF & EXT_CLEAN & EXT_CREATE
    end

    subgraph STORAGE["2. 노드 데이터 저장소 (RecipeNode)"]
        STORE["NodePropertyStore (Key-Value Dynamic Map)"]
        EXT_FUSION & EXT_EBF & EXT_CLEAN & EXT_CREATE -->|set(Key, Value)| STORE
    end

    subgraph PRESENTATION["3. UI & 계산 레이어"]
        STORE --> BADGE_PIPE["NodeBadgeRegistry (자동 뱃지 생성)"]
        STORE --> OVERCLOCK["Overclock & Calculation Engine"]

        BADGE_PIPE --> BADGE_FUSION["[⚛️ Fusion Mk2 | ⚡ 320M EU]"]
        BADGE_PIPE --> BADGE_EBF["[Coil: Kanthal (1800K)]"]
        BADGE_PIPE --> BADGE_CLEAN["[Cleanroom Required]"]
    end
```

---

## 3. 핵심 인터페이스 및 데이터 구조

### 3.1 타입 안전한 프로퍼티 키 (`NodePropertyKey<T>`)

```java
public final class NodePropertyKey<T> {
    private final String id;
    private final Class<T> type;
    private final T defaultValue;
    private final BiConsumer<CompoundTag, T> serializer;
    private final Function<CompoundTag, T> deserializer;

    // 표준 정의 목록
    public static final NodePropertyKey<Long> FUSION_START_EU = 
        NodePropertyKey.ofLong("fusion_start_eu", 0L);
    public static final NodePropertyKey<Integer> EBF_TEMPERATURE = 
        NodePropertyKey.ofInt("ebf_temperature", 0);
    public static final NodePropertyKey<Boolean> CLEANROOM_REQUIRED = 
        NodePropertyKey.ofBoolean("cleanroom_required", false);
    public static final NodePropertyKey<Integer> CREATE_RPM = 
        NodePropertyKey.ofInt("create_rpm", 128);
    public static final NodePropertyKey<TurbineRotorData> TURBINE_ROTOR = 
        NodePropertyKey.of("turbine_rotor", TurbineRotorData.class, TurbineRotorData.DEFAULT, ...);
}
```

---

### 3.2 레시피 프로퍼티 추출기 인터페이스 (`IRecipePropertyExtractor`)

새로운 특수 기계나 모드가 추가될 때, 코어 코드를 전혀 건드리지 않고 이 인터페이스 구현체만 등록하면 됩니다.

```java
public interface IRecipePropertyExtractor {
    /** 추출기가 처리할 모드 ID 및 우선순위 */
    String getTargetModId();
    
    /** 레시피 객체 및 NBT에서 프로퍼티를 추출하여 store에 주입 */
    void extract(Object backingRecipe, CompoundTag recipeDataTag, NodePropertyStore store);
}
```

* **예시: `GTFusionPropertyExtractor`**:
  ```java
  public class GTFusionPropertyExtractor implements IRecipePropertyExtractor {
      @Override
      public void extract(Object backingRecipe, CompoundTag tag, NodePropertyStore store) {
          if (tag != null && tag.contains("eu_to_start")) {
              long startEU = tag.getLong("eu_to_start");
              store.set(NodeProperties.FUSION_START_EU, startEU);
          }
      }
  }
  ```

---

### 3.3 선언적 노드 뱃지 렌더러 (`INodeBadgeProvider`)

노드 카드 상단에 뱃지를 달 때도 `if/else` 없이 Store를 기반으로 선언적으로 공급됩니다:

```java
public interface INodeBadgeProvider {
    /** 해당 노드의 PropertyStore를 검사하여 유효한 뱃지 컴포넌트 목록 반환 */
    List<NodeBadge> createBadges(RecipeNode node, NodePropertyStore store);
}
```

* **동작 원리**:
  - `store.has(NodeProperties.FUSION_START_EU)`가 참이면 $\rightarrow$ `FusionBadge(startEU)` 자동 생성.
  - `store.has(NodeProperties.EBF_TEMPERATURE)`가 참이면 $\rightarrow$ `CoilBadge(temp)` 자동 생성.
  - `store.has(NodeProperties.CLEANROOM_REQUIRED)`가 참이면 $\rightarrow$ `CleanroomBadge` 자동 생성.

---

## 4. 기대 효과 및 장점

1. **`RecipeNode.java`의 비대화 종식 (Zero Field Bloat)**:
   - 기계가 100종류로 늘어나도 `RecipeNode`의 클래스 크기와 메모리 사용량은 일정하게 유지됨.
2. **100% 개방-폐쇄 원칙 (OCP - Open/Closed Principle)**:
   - 새로운 모드나 기계(예: Create, Mekanism, Botania) 지원 시, **`IRecipePropertyExtractor`와 `INodeBadgeProvider` 1개씩만 추가**하면 끝남.
3. **NBT 직렬화 및 청사진(Blueprint) 호환성 자동화**:
   - `NodePropertyKey`에 정의된 직렬화 람다에 의해 클립보드 공유 및 월드 세이브 저장 시 모든 특수 데이터가 누락 없이 안전하게 보존.

---

## 5. 결론

본 RFC의 **확장형 노드 프로퍼티 & 특수 컴포넌트 시스템**은 향후 그렉텍뿐만 아니라 Star Technology의 Create, Thermal, Mekanism 등 모든 복합 테크 모드의 기계 스펙을 단 하나의 일관된 아키텍처로 수용할 수 있는 가장 견고한 초석이 될 것입니다.
