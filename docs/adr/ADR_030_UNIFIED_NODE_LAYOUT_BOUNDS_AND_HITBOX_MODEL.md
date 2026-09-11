# ADR-030: 노드 카드 레이아웃 바운즈 단일 출처화 및 통합 히트박스 모델 명세
(Unified Node Layout Bounds & Single-Source Hitbox Architecture Specification)

- **문서 번호**: ADR-030
- **대상 버전**: `v2.2.0-alpha.4`
- **상태**: `IMPLEMENTED`
- **결정/완료일**: 2026-09-07
- **주관 계층**: Client GUI Layer (`client.gui.widget`, `client.gui.render`, `client.gui.layout`, `client.gui.interaction`)

---

## 1. 개요 및 배경 (Motivation)

### 1.1 현황 및 한계 분석 (AS-IS)
`GregTechCalculatorBoard`의 캔버스 GUI는 수십~수백 개의 노드가 동시 배치되는 고밀도 공정 환경을 지원하기 위해, 각 노드 내부의 버튼, 슬롯, 배지들을 개별 위젯 객체로 분할하지 않고 단일 `NodeWidget` 내에서 절차적 수학식으로 렌더링하고 판정하는 방식을 취해 왔습니다.

그러나 기능 확장(일반 모드 vs 슬림 카드 모드, 모듈 노드, 리루트 노드 등)에 따라 다음과 같은 구조적 결함이 존재했습니다:

1. **렌더링과 히트박스 판정의 이중 하드코딩 (Dual Offset Duplication)**:
   - 렌더러(`NodeCardRenderer`)와 이벤트 핸들러(`NodeWidget`, `IModGuiHandler`)가 레이아웃 기하학(Geometry) 상태를 공유하지 않고, 각자 독자적인 상대 오프셋 수학식을 하드코딩하여 독립 계산했습니다.
2. **조건부 레이아웃 변경 시 유령 히트박스(Ghost Hitbox) 발생**:
   - `slimCardMode` 활성화 시 렌더러는 기계 컨트롤(Row 2: 전압 티어, 오버클록 등)을 생략하고 입출력 포트 영역(`contentY`)을 상단으로 당겨서 렌더링했습니다.
   - 그러나 이벤트 판정 코드는 레이아웃 상태를 참조하지 않고 고정된 Row 2 오프셋을 그대로 평가하여, 상단으로 이동한 첫 번째 입력 포트의 물리 좌표(`y + 46`)가 보이지 않는 가상의 티어 버튼 좌표와 일치하게 되었습니다.
   - 이로 인해 슬림 모드에서 입력 슬롯 위 휠 스크롤 시 대안 재료가 순환되는 대신 보이지 않는 기계 전압 티어가 가로채어져 변경되는 조작 충돌이 발생했습니다.
3. **유지보수성 저하**:
   - 새로운 뷰 모드나 컨트롤 요소를 추가할 때마다 렌더러와 이벤트 처리기의 좌표 수학식을 수작업으로 동기화해야 하는 부담이 있었습니다.

### 1.2 설계 목표 (TO-BE)
- **단일 출처화(Single Source of Truth) 레이아웃 모델 구축**:
  - 노드의 상태(모드, 입출력 개수, 크기, 플립 등)를 반영하는 불변/캐시형 레이아웃 구조체 `NodeLayoutBounds` 도입.
- **렌더링과 히트박스의 완전 일치**:
  - `NodeCardRenderer`는 `NodeLayoutBounds`에 정의된 경계 사각형에 따라 그리고, 마우스 인터랙션(`NodeWidget`) 또한 동일한 경계 사각형을 기반으로 판정.
  - 슬림 모드에서는 Row 2 컨트롤 바운즈 자체가 `EMPTY`로 비워져 유령 히트박스 제거.
- **버전 캐시 기반 $O(1)$ 검증**:
  - `LayoutCacheKey`를 통해 노드 좌표, 크기, 플립 여부, 슬림 모드 상태 등이 변경되었을 때만 바운즈를 재계산하여 불필요한 연산 방지.

---

## 2. 세부 설계 및 결정 사항 (Architecture Decision)

### 2.1 단일 출처 레이아웃 아키텍처 파이프라인

```mermaid
flowchart TD
    subgraph State_Trigger ["상태 변경 트리거 (State Triggers)"]
        PosChange["노드 좌표/크기 이동"]
        ModeChange["카드 모드 전환 (Slim/Normal)"]
        PortChange["포트 가시성/개수 변경"]
        FlipChange["입출력 방향 반전 (Flip)"]
    end

    subgraph Layout_Engine ["레이아웃 산출 엔진 (Layout Engine)"]
        LayoutCalculator["NodeLayoutCalculator.compute(...)"]
        LayoutBounds["NodeLayoutBounds (불변 구조체)"]
        CacheKey["LayoutCacheKey (상태 해시 검증)"]
    end

    subgraph Consumers ["소비 계층 (Consumers)"]
        Renderer["NodeCardRenderer (드로잉 좌표 참조)"]
        Interaction["NodeWidget (클릭 / 호버 / 휠 히트박스)"]
        WireHandler["CanvasWireInteractionHandler (포트 소켓 앵커)"]
    end

    PosChange --> CacheKey
    ModeChange --> CacheKey
    PortChange --> CacheKey
    FlipChange --> CacheKey

    CacheKey -->|키 불일치 시 재계산| LayoutCalculator
    LayoutCalculator --> LayoutBounds
    LayoutBounds -->|Bounds 참조| Renderer
    LayoutBounds -->|contains(x,y) 판정| Interaction
    LayoutBounds -->|anchorX, anchorY 참조| WireHandler
```

### 2.2 핵심 데이터 모델 설계

#### 1) `NodeLayoutBounds` (`com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds`)
- 불변 레코드 `RectBounds(int x, int y, int width, int height)`:
  - `contains(double px, double py)`: 점 포함 여부 $O(1)$ 판정
  - `isEmpty()`: 폭 또는 높이가 0 이하인 빈 영역 판정
  - `EMPTY`: 비활성화된 컨트롤 영역을 나타내는 싱글톤 불변 객체
- 불변 레코드 `PortBounds(int portIndex, boolean isInput, RectBounds hitBox, RectBounds slotBounds, float anchorX, float anchorY)`:
  - 포트 히트박스, 슬롯 사각 영역, 연결선 소켓 앵커 좌표 캡슐화
- 전체 사각 영역 필드:
  - `cardBounds`, `headerBounds`, `machineIconBounds`, `nameBounds`
  - 기계 대수 행: `countMinusBtnBounds`, `countBoxBounds`, `countPlusBtnBounds`, `countHalfBtnBounds`, `countDoubleBtnBounds`, `moduleBadgeBounds`, `addonTrayBounds`
  - Row 2 컨트롤: `hasRow2Controls`, `tierBtnBounds`, `secondaryBtnBounds`, `configBtnBounds`
  - 구분선 및 포트 목록: `separatorY`, `contentStartY`, `inputPorts`, `outputPorts`, `hiddenPortsBadgeBounds`
  - 리루트 노드 전용: `targetBatchBadgeBounds`

#### 2) `NodeLayoutCalculator` (`com.gtceu.calcboard.client.gui.layout.NodeLayoutCalculator`)
- `RecipeNode`, 슬림 모드 플래그, 카운트 텍스트 폭, 타겟 배치 편집 상태를 기반으로 `NodeLayoutBounds`를 연산하는 순수 계산기.
- 슬림 모드 시 `hasRow2Controls = false`로 설정하고 Row 2 컨트롤 바운즈를 `RectBounds.EMPTY`로 할당.

#### 3) `NodeWidget`의 이벤트 디스패치 및 캐시 동기화
- `LayoutCacheKey` 레코드를 통해 이전 캐시 상태와 현재 상태(좌표, 플립, 슬림 모드, 포트 수 등)를 비교하여 변경 시에만 재계산 수행.
- `mouseScrolled`:
  1. `bounds.getHoveredInputPortIndex(mouseX, mouseY)`를 최우선 평가하여 대안 재료 순환에 우선 할당.
  2. `bounds.hasRow2Controls()`가 참일 때만 Row 2 컨트롤 스크롤 평가.
- `mouseClicked`:
  - 대수 증감 버튼, 카운트 입력 박스, Row 2 컨트롤 클릭 판정을 `bounds`의 각 영역 `contains`에 위임.

---

## 3. 결과 및 파급 효과 (Consequences)

### 3.1 긍정적 효과
- **조작 충돌 해소**: 슬림 카드 모드에서 첫 번째 입력 포트와 Row 2 티어 버튼 간의 좌표 겹침이 해소되어, 휠 스크롤 시 의도치 않게 전압 티어가 바뀌던 현상이 해결되었습니다.
- **코드 중복 제거**: `NodeWidget` 내부의 260여 줄에 달하던 상대 오프셋 하드코딩 수학식을 제거하고 `NodeLayoutBounds`로 단일 출처화했습니다.
- **안정적인 렌더링/판정 일치**: 렌더러와 이벤트 처리기가 동일한 기하학적 바운즈를 공유하므로 시각적 요소와 클릭 가능 영역의 불일치가 방지됩니다.

### 3.2 단위 테스트 검증
- 신규 단위 테스트 `NodeLayoutBoundsTest` 작성 및 통과:
  - 슬림 모드 유령 히트박스 방지 검증 (`testSlimModeGhostHitboxPrevention`)
  - 일반 모드 컨트롤 및 포트 분리 검증 (`testNormalModeControlsAndPortSeparation`)
  - 리루트 노드 바운즈 및 포트 앵커 검증 (`testRerouteNodeLayoutBounds`)
  - 포트 클램핑 히트박스 검증 (`testPortAnchorAndClampedHitbox`)
- 기존 GUI 인터랙션 및 포트 좌표 회귀 검증: `NodeFlipTest`, `SlimCardInteractionTest` 포함 전체 808개 단위 테스트 통과.
