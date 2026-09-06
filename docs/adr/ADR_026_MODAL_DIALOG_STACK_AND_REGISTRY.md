# ADR-026: 모달 다이얼로그 스택 및 레지스트리 아키텍처
# (Modal Dialog Stack & Registry Architecture)

- **문서 번호**: ADR-026
- **대상 버전**: `v2.2.0-alpha.3`
- **상태**: `IMPLEMENTED`
- **결정/완료일**: 2026-09-06
- **주관 계층**: Client GUI Dialog Layer (`client.gui.dialog`, `client.gui.dialog.modal`)

---

## 1. 개요 및 배경 (Motivation)

`GregTechCalculatorBoard`의 GUI 계층은 설정, 레시피 검색, 멀티블록 BOM, 글로벌 수지 대시보드 등 총 26종의 대화형 모달 다이얼로그를 지원합니다. 그러나 기존 다이얼로그 라이프사이클과 이벤트 디스패치를 총괄하던 [`BoardDialogManager.java`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/client/gui/dialog/BoardDialogManager.java)는 다음과 같은 구조적 한계를 지니고 있었습니다:

1. **공통 다이얼로그 추상화 인터페이스의 부재**:
   - 26종의 다이얼로그 클래스가 공통 기반 인터페이스 없이 각각 독자적인 시그니처(`render(...)`, `isVisible()`, `close()`)를 사용하여 다형성 확립이 불가했음.
2. **반복적인 선형 if-else 블록 누적 (400여 줄의 보일러플레이트)**:
   - `isAnyModalOpen()`, `renderModals()`, `handleMouseClicked()`, `handleMouseReleased()`, `handleMouseDragged()`, `handleMouseScrolled()`, `handleKeyPressed()`, `handleCharTyped()` 등 모든 사용자 입력 및 렌더링 루프마다 26개의 `if (dialog != null && dialog.isVisible())` 조건문이 반복 복사되어 있었음.
3. **개방-폐쇄 원칙(OCP) 위배**:
   - 신규 다이얼로그 추가 시 `BoardDialogManager` 내부의 10여 개 이벤트 디스패처 메서드에 하드코딩된 분기문을 추가해야 하여 결합도가 높았음.
4. **모달 중첩(Stacking) 및 이벤트 탈취 제어의 부재**:
   - 다이얼로그 위에 하위 다이얼로그가 팝업될 때, Z-Order 및 포커스 관리가 메서드 내 if문의 순서에 전적으로 의존하여 포커스 탈취 및 모달 닫기 순서의 역전 현상이 발생할 수 있었음.

---

## 2. 세부 설계 및 결정 사항 (Architecture Decision)

### 2.1 IBoardModal 계약 수립
모든 모달 다이얼로그가 준수해야 할 표준 계약을 `com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal` 인터페이스로 정의하고, 26종 전수에 일괄 구현을 적용했습니다:
- `isVisible()`: 모달 활성화 여부 확인.
- `close()`: 모달 종료 및 리소스 정리.
- `renderModal(ModalRenderContext context)`: `GuiGraphics`, 화면 크기, 마우스 좌표, `partialTicks`를 불변 레코드로 캡슐화한 표준 렌더링 진입점.
- `mouseClicked`, `mouseReleased`, `mouseDragged`, `mouseScrolled`, `keyPressed`, `charTyped`: 가상 마우스/키보드 입력 이벤트 디스패치.
- `requiresBackdropDim()`: 중앙 딤 처리 요구 플래그 (기존 다이얼로그 시각 호환성을 위해 `false` 기본값 유지).
- `closesOnOutsideClick()`: 배경 클릭 시 자동 닫힘 여부.

### 2.2 LIFO 기반 ModalStack 아키텍처
활성 모달들을 계층적 스택(`Deque<IBoardModal>`)으로 관리하는 `ModalStack`을 도입했습니다:
- **LIFO 입력 격리 ($O(1)$)**: 모든 사용자 입력(`mouseClicked`, `keyPressed`, `charTyped` 등)은 스택 최상단(Top)의 모달에만 독점 전달되며, 하위 모달이나 배경 캔버스로의 클릭 유출(Click-through)을 원천 차단.
- **ESC 순차 닫힘**: 최상단 모달부터 역순으로 안전하게 닫히며 스택에서 자동 프루닝(Pruning).
- **자동 비활성 정리 (`pruneInactiveModals`)**: 모달 내부 로직에 의해 `visible = false`로 변경된 모달은 스택 순회 시 즉각 LIFO 덱에서 제거.

### 2.3 BoardDialogManager 평탄화 및 하위 호환성 유지
- `BoardDialogManager` 내부의 400여 줄에 달하던 26-way if-else 사다리를 전면 제거하고, `modalStack.dispatch...()` 단일 위임 구조로 평탄화.
- 기존 외부 코드의 `dialog.open(...)` 직접 호출과의 100% 하위 호환성을 보장하기 위해 `syncActiveModals()` 동기화 브리지를 배치하여, 활성화된 모달이 스택에 없을 경우 자동으로 스택에 푸시되도록 설계.

```mermaid
graph TD
    subgraph Screen["Client GUI Screen"]
        BS["BoardScreen"]
    end

    subgraph Manager["Modal Subsystem"]
        BDM["BoardDialogManager"]
        MS["ModalStack (ArrayDeque&lt;IBoardModal&gt;)"]
    end

    subgraph Contract["Core Modal Contract"]
        IBM["&lt;&lt;interface&gt;&gt; IBoardModal"]
    end

    subgraph Concrete["26 Concrete Dialogs"]
        D1["RecipeSearchDialog"]
        D2["MultiblockBOMDialog"]
        D3["GlobalBalanceDashboardDialog"]
        D4["MachineConfigDialog"]
        D5["DeletePageConfirmDialog"]
        D_ALL["... 21 Other Dialogs"]
    end

    BS -->|Input / Render Events| BDM
    BDM -->|LIFO Dispatch| MS
    MS -->|Top-most Dispatch| IBM
    IBM <|.. D1
    IBM <|.. D2
    IBM <|.. D3
    IBM <|.. D4
    IBM <|.. D5
    IBM <|.. D_ALL
```

---

## 3. 구현 산출물 및 파일 매핑

| 패키지 / 모듈 | 클래스 / 파일 | 역할 |
| :--- | :--- | :--- |
| `client.gui.dialog.modal` | `IBoardModal.java` | 모달 다이얼로그 표준 계약 인터페이스 |
| `client.gui.dialog.modal` | `ModalRenderContext.java` | 화면 크기, 마우스, 틱 정보 불변 레코드 |
| `client.gui.dialog.modal` | `ModalStack.java` | LIFO 스택 기반 이벤트 라우팅 및 상태 관리자 |
| `client.gui.dialog.modal` | `ModalId.java` | 타입 세이프 모달 식별자 |
| `client.gui.dialog` | `BoardDialogManager.java` | 400여 줄 if-else 제거 및 ModalStack 기반 평탄화 |
| `client.gui.dialog.*` | 26개 Concrete Dialog 클래스 | `IBoardModal` 구현 및 `renderModal` 위임 연동 |

---

## 4. 검증 결과

- **단위 테스트 (`ModalStackTest`)**:
  - `testLifoPushAndPop`: LIFO 스택 push, pop, top 조회 및 closeAll 정상 동작.
  - `testRepushMovesToTop`: 이미 스택에 존재하는 모달 재호출 시 최상단 승격 검증.
  - `testPruneInactiveModals`: 비활성화된 모달 자동 프루닝 검증.
  - `testEventDispatchToTopModalOnly`: 최상단 모달 독점 이벤트 디스패치 및 하위 격리 검증.
  - `testEscapeClosesModal`: ESC 입력 시 최상단 모달 순차 닫힘 검증.
  - `testOutsideClickClosesModal`: 외곽 클릭 자동 닫힘 검증.
  - `testBoardDialogManagerIntegration`: `BoardDialogManager`와 `ModalStack` 연동 및 생명주기 검증.
  - **결과**: 7개 테스트 케이스 전원 통과 (`BUILD SUCCESSFUL`).
- **전체 GUI 회귀 테스트**:
  - `com.gtceu.calcboard.client.gui.*` 전체 테스트 스위트 100% 통과 (0 회귀 결함).
- **정적 규칙 린터 (`lint_agent_rules.py`)**: 33개 파일 전수 검사 통과 (0 Violations).
- **다국어 패리티 (`check_i18n.py`)**: 4개 국어(en_us, ko_kr, ru_ru, zh_cn) 1099개 키 100% 일치.
