# RFC: GTCalcBoard v1.1.0 - 레시피 검색 UX 고도화 (부산물 호버 미리보기 & 레시피 타입 필터링)

* **문서 번호**: RFC-004
* **대상 버전**: `v1.1.0` (또는 `v1.0.6+`)
* **상태**: `PROPOSED / PLANNING`
* **주제**: 레시피 검색 결과 입출력/부산물 전체 호버 미리보기 툴팁 및 레시피 타입/기계 블랙리스트 필터 설정

---

## 1. 개요 및 배경 (Motivation)

### 1.1 배경
현재 GTCalcBoard의 레시피 검색 다이얼로그(`RecipeSearchDialog`)는 방대한 그렉텍 레시피를 검색할 수 있으나, 다음과 같은 실사용자 UX 한계가 지적되었습니다:

1. **부산물 및 입출력 확인 불가**:
   * 검색 결과 목록에는 대표 출력물(Main Product) 또는 기계 이름만 표시됩니다.
   * 예: '중유(Heavy Oil) 정제'를 검색할 때 대표 산출물이 '재(Ash)'로 잡히면 목록에 재 아이콘만 떠서, 실제로 중유가 나오는지 노드를 캔버스에 추가해 보기 전까지 알 수 없음.
2. **비공정 레시피 노이즈 (검색 결과 오염)**:
   * '월드 상호작용(World Interactions)', '유체 드럼 캡슐화(Fluid Encapsulation)', '원목 도끼 벌목' 등 실제 공정 라인 계산에 쓰이지 않는 레시피들이 검색 결과에 섞여 나와 가독성을 떨어뜨림.

### 1.2 목표
1. **레시피 호버 오버뷰 툴팁 (`RecipeHoverOverview`)**: 검색 결과 목록에 마우스를 올렸을 때 **모든 입력물 $\rightarrow$ 소모 전력/시간 $\rightarrow$ 모든 주산출물 및 부산물**을 직관적인 툴팁 카드로 즉시 표출.
2. **레시피 타입 / 기계 필터 & 블랙리스트 설정 (`RecipeTypeFilterSettings`)**: 설정 메뉴 및 검색창에서 불필요한 레시피 타입(World Interaction, Encapsulation 등)을 체크박스로 손쉽게 제외/필터링.

---

## 2. 사용자 핵심 유저 스토리 (User Stories)

| ID | 역할 | 행동 (Action) | 기대 효과 (Outcome) |
| :--- | :--- | :--- | :--- |
| **US-01** | 공정 설계자 | 검색창에서 레시피 항목에 마우스를 올림 (Hover) | 노드를 캔버스에 추가하지 않고도 모든 입력 원자재, 소모 EU/t, 소요 시간, 주산출물 및 모든 부산물을 한눈에 확인 |
| **US-02** | 공정 설계자 | 레시피 타입 필터에서 'World Interaction', 'Fluid Encapsulation'을 체크 해제 | 검색 결과에서 드럼 채우기/비우기 등 불필요한 노이즈 레시피가 깔끔하게 숨겨짐 |
| **US-03** | 모드팩 유저 | 설정 창에서 특정 커스텀 레시피 카테고리를 영구 제외 | 게임을 재시작해도 블랙리스트 설정이 로컬 설정(`gtcalcboard-client.toml`)에 안전하게 저장/유지 |

---

## 3. 기능 상세 명세 (Feature Specification)

### 3.1 레시피 호버 오버뷰 툴팁 (`RecipeHoverOverviewTooltip`)

검색창 목록에서 특정 레시피 위에 마우스 커서를 올리면 다음과 같은 콤팩트 카드형 툴팁이 렌더링됩니다:

```text
+-----------------------------------------------------------------------------------+
| ⚙ Chemical Reactor: Polyethylene Synthesis                                      |
+-----------------------------------------------------------------------------------+
| [ Inputs (입력물) ]                 | [ Details (조건) ]     | [ Outputs (산출물) ]  |
| • 💧 Ethylene: 1,000 mB             | ⚡ 30 EU/t (LV)        | • 🧱 Polyethylene: 144 |
| • 💧 Oxygen: 100 mB                 | ⏱ 15.0s (300 ticks)   | • 💧 Diluted Acid: 50 mB|
| • 📦 Tiny Catalyst: 1               | 🧪 Chemical Recipe     |   (부산물 20% 확률)     |
+-----------------------------------------------------------------------------------+
```

* **렌더링 요소**:
  1. **헤더**: 레시피 표시 이름 및 기계/카테고리명 (`⚙ Chemical Reactor`).
  2. **좌측 (Inputs)**: 소모되는 모든 아이템 및 유체(mB/s 또는 1회 소모량).
  3. **중앙 (Stats)**: 기본 전압 티어, 소모 EU/t, 소요 시간(초 및 틱).
  4. **우측 (Outputs)**: 생산되는 주산출물 및 확률성 부산물(확률 % 표기).

---

### 3.2 레시피 타입 필터 & 블랙리스트 설정 (`RecipeTypeFilter`)

#### 1. 기본 제외 프리셋 (Default Blacklist)
공정 계산에 거의 사용되지 않는 기본 카테고리는 초기 상태에서 기본 비활성화 권장:
* `gtceu:world_interaction` (월드 블록 상호작용)
* `gtceu:fluid_canning` / `fluid_encapsulation` (단순 드럼/캡슐 포장)
* `gtceu:primitive_blast_furnace` (석탄/목탄 연소 원시 용광로 - 전기 용광로와 혼동 방지 토글)

#### 2. 검색창 상단 빠른 필터 바 (Quick Filter Chips)
```text
+-----------------------------------------------------------------------------------+
| 🔍 [ Search: Heavy Oil                      ]  [⚙ 필터 설정]  [✓ 제외 필터 활성화]  |
| [✓ 화학 반응기] [✓ 증류탑] [✓ 열분해로] [✗ 드럼 캡슐화] [✗ 월드 상호작용] ...        |
+-----------------------------------------------------------------------------------+
| • Heavy Oil Fractionation (Distillation Tower)     -> [💧 Heavy Oil: 300 mB, ...] |
| • Vacuum Distillation (Vacuum Tower)               -> [💧 Heavy Oil: 150 mB, ...] |
+-----------------------------------------------------------------------------------+
```

#### 3. 영속화 설정 (`gtcalcboard-client.toml`)
```toml
[search_filters]
# 제외할 레시피 타입 ID 목록
excluded_recipe_types = [
    "gtceu:world_interaction",
    "gtceu:fluid_canning"
]
# 호버 툴팁 표시 여부 및 딜레이 (ms)
enable_hover_overview = true
hover_overview_delay_ms = 150
```

---

## 4. 아키텍처 및 구현 파이프라인

```mermaid
flowchart TD
    Registry["GTCEu & Vanilla Recipe Registry"] --> FilterEngine["RecipeFilterEngine"]
    Config["Config: excluded_recipe_types"] --> FilterEngine
    SearchQuery["User Query Input"] --> SearchEngine["RecipeSearchEngine"]
    FilterEngine -->|필터링된 레시피 목록| SearchEngine

    SearchEngine --> SearchResults["검색 결과 리스트 UI"]
    SearchResults -->|Hover Event (150ms)| OverviewRenderer["RecipeHoverOverviewRenderer"]
    OverviewRenderer --> TooltipHUD["입출력 & 부산물 툴팁 렌더링"]
```

---

## 5. UI / UX 세부 인터랙션

1. **즉각적인 시각적 피드백**:
   * 마우스 오버 시 $150\text{ms}$ 딜레이 후 툴팁이 부드럽게 나타나 마우스 이동 중 깜빡임 방지.
2. **부산물 강조 (Byproduct Highlighting)**:
   * $100\%$ 확정 산출물은 흰색/초록색, 확률성 부산물은 주황색/보라색(`§d(20% 확률)`)으로 직관적 구분.
3. **간편한 원클릭 토글**:
   * 검색창 내 `[⚙ 필터]` 버튼을 눌러 모달 창을 띄우거나, 상단 칩을 클릭하여 특정 기계군을 켜고 끌 수 있음.

---

## 6. 결론

본 RFC의 **부산물 호버 미리보기**와 **레시피 타입 필터링**은 사용자가 복잡한 화학/정제 공정 레시피를 탐색할 때 겪는 불필요한 시행착오(노드를 꺼내보고 지우는 행위)를 완전히 없애고, 최적의 공정을 초고속으로 찾아낼 수 있는 강력한 탐색 경험을 제공할 것입니다.
