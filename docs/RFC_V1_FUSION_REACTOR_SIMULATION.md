# RFC: GTCalcBoard v1.1.0 - 핵융합로 전용 시뮬레이션 & 시동 에너지 계산 (Fusion Reactor Simulation)

* **문서 번호**: RFC-008
* **대상 버전**: `v1.1.0` (또는 `v1.0.6+`)
* **상태**: `PROPOSED / PLANNING`
* **주제**: GregTech CEu Modern 핵융합로(Fusion Reactor Mk1/Mk2/Mk3) 시동 에너지(EU to Start), 지속 가동 전력, 반응로 티어 자동 판정 및 버퍼 계산

---

## 1. 개요 및 배경 (Motivation)

### 1.1 배경
그렉텍 후반 테크(LuV ~ UHV)의 핵심 공정인 **핵융합로(Fusion Reactor)**는 플라즈마 발전, 유로퓸(Europium), 아메리슘(Americium) 등 필수 원소를 생산하는 중추 시설입니다.
* 그러나 핵융합로는 일반 기계와 근본적으로 다른 전력 메커니즘을 가지고 있습니다:
  1. **시동 에너지 (`EU to Start`)**: 가동을 시작하기 위해 내부 버퍼에 미리 충전해 두어야 하는 막대한 점화 에너지 (예: $160\text{M} \sim 640\text{M EU}$).
  2. **지속 가동 전력 (`Running EU/t`)**: 점화 후 레시피가 돌아가는 동안 매 틱 소모되는 전력 (예: $16,384\text{ EU/t}$).
  3. **반응로 티어 제한**: 요구 시동 에너지에 따라 `Fusion Reactor Mk1(LuV)`, `Mk2(ZPM)`, `Mk3(UV)` 등 설치해야 하는 멀티블록 본체 티어가 엄격히 나뉨.
* 현재 GTCalcBoard는 시동 에너지를 파싱하지 않아 일반 기계처럼 표시되므로, 실제 인게임에서 전력 버퍼 부족으로 핵융합로가 켜지지 않는 문제가 발생합니다.

### 1.2 목표
1. **시동 에너지 파싱 & 영속화**: EMI/GTCEu 레시피 NBT(`eu_to_start`)를 자동 추출하여 `RecipeNode`에 영구 저장.
2. **반응로 티어 자동 판정 & 비주얼 뱃지**: 레시피에 필요한 최소 반응로 티어(`Mk1 / Mk2 / Mk3`) 및 시동 에너지를 플라즈마 테마 뱃지로 표출.
3. **요약 패널 핵융합 점화 버퍼 통계**: 전체 공정 가동에 필요한 총 점화 에너지 합계 및 권장 배터리/에너지 해치 구성 안내.

---

## 2. 사용자 핵심 유저 스토리 (User Stories)

| ID | 역할 | 행동 (Action) | 기대 효과 (Outcome) |
| :--- | :--- | :--- | :--- |
| **US-01** | 공정 설계자 | EMI에서 '플래티넘 플라즈마 핵융합' 레시피를 보드에 추가 | 노드 카드에 `[⚛️ Fusion Mk2 | ⚡ Start: 320M EU]` 뱃지와 지속 소모 $32,768\text{ EU/t}$가 명확히 분리 표기됨 |
| **US-02** | 공정 설계자 | 핵융합 노드의 전압 티어를 LuV에서 UV로 상향 조정 | 오버클럭에 따른 가공 시간 단축과 함께 인젝터 해치 수용 가능 여부를 시각적으로 확인 |
| **US-03** | 공정 설계자 | 우측 요약 패널의 전력 섹션을 확인 | 기지 전체 가동을 위해 필요한 **총 초기 점화 버퍼(예: 480,000,000 EU)**를 확인하여 발전소/배터리 버퍼 사전 계획 수립 |

---

## 3. 기능 상세 명세 (Feature Specification)

### 3.1 반응로 티어 판정 기준표

| 반응로 티어 | 전압 티어 | 최대 시동 에너지 용량 | 대표 생산물 |
| :--- | :--- | :--- | :--- |
| **Fusion Reactor Mk1** | `LuV (6) / ZPM` | 최대 **$160,000,000\text{ EU}$** ($160\text{M}$) | 헬륨 플라즈마, 유로퓸 |
| **Fusion Reactor Mk2** | `ZPM (7) / UV` | 최대 **$320,000,000\text{ EU}$** ($320\text{M}$) | 산소 플라즈마, 아메리슘 |
| **Fusion Reactor Mk3** | `UV (8) / UHV` | 최대 **$640,000,000\text{ EU}$** ($640\text{M+}$) | 철 플라즈마, 듀란 |

---

### 3.2 노드 카드 렌더링 디자인

```text
+-----------------------------------------------------------------------------------+
| ⚛️ Fusion Reactor: Helium-3 + Deuterium -> Helium Plasma                          |
| [ ⚛️ Fusion Mk1 ]  [ ⚡ Start: 160.00M EU ]                                       |
+-----------------------------------------------------------------------------------+
| [ Inputs ]                          | [ Conditions ]         | [ Outputs ]        |
| • 💧 Helium-3: 125 mB               | ⚡ Run: 16,384 EU/t     | • 💧 He Plasma: 125|
| • 💧 Deuterium: 125 mB              | ⏱ 1.6s (32 ticks)      |                    |
+-----------------------------------------------------------------------------------+
```

* **보라빛/플라즈마 네온 테두리**: 핵융합 노드는 일반 기계와 확연히 구분되는 플라즈마 글로우(Plasma Glow) 테마 적용.
* **시동 에너지 뱃지 (`[⚡ Start: 160M EU]`)**: 마우스 호버 시 정확한 원본 정밀 수치(`160,000,000 EU`) 표출.

---

### 3.3 우측 요약 패널 (`SummaryOverlay`) 통계 확장

```text
+-------------------------------------------------------------+
| ⚡ Total Power: 49,152.0 EU/t (LuV 1.5A)                     |
| ⚛️ Fusion Start Energy Buffer: 480.00M EU (480,000,000 EU)   |
|    • Mk1 Reactors: 2대 (총 320M EU)                          |
|    • Mk2 Reactors: 1대 (총 160M EU)                          |
+-------------------------------------------------------------+
```

---

## 4. 아키텍처 및 데이터 파이프라인

```mermaid
flowchart TD
    EMI["EMI GTRecipe Backing Data"] --> Extractor["EmiRecipeConverter.extractGTRecipeDetails"]
    Extractor -->|data.getLong('eu_to_start')| ParseEU["startEU & recipeTemp 파싱"]
    ParseEU --> Node["RecipeNode.setStartEU(startEU)"]

    Node --> Matrix["CategoryCapabilityMatrix (Fusion Tier 판정)"]
    Matrix --> Renderer["NodeCardRenderer (플라즈마 테마 & Start 뱃지)"]
    Node --> Summary["SummaryOverlay (총 점화 에너지 버퍼 집계)"]
```

---

## 5. 결론

본 RFC의 **핵융합로 전용 시뮬레이션**은 그렉텍 후반 테크의 상징인 핵융합 공정을 정확한 시동 점화 에너지와 전력 버퍼, 반응로 티어 조건까지 완벽하게 계산해 냄으로써, 하이엔드 테크 유저들에게 비교할 수 없는 정밀함과 만족도를 선사할 것입니다.
