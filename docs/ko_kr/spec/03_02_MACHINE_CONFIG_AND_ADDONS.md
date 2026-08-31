# [03-02] 기계 상세 설정 및 애드온 랙 UI (Machine Config & Addons)

> 📍 **GTCalcBoard 기술 명세서 시리즈**
> [[00] 시스템 개요](00_OVERVIEW.md) ➔ [[01] 코어 도메인](01_CORE_DOMAIN_AND_MODELS.md) ➔ [[02] 수학 엔진](02_MATH_AND_ALGORITHMS.md) ➔ [[03-01] 캔버스 & 노드 카드](03_01_CANVAS_AND_NODE_CARDS.md) ➔ **[03-02] 기계 설정 & 애드온** ➔ [[03-03] 페이지 결산 & 대시보드](03_03_PAGE_SUMMARY_AND_DASHBOARD.md) ➔ [[03-04] 검색 & 툴바](03_04_RECIPE_SEARCH_AND_TOOLS.md) ➔ [[04] 멀티플레이어](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] 외부 연동](05_INTEGRATION_AND_I18N.md)

---

## 1. 컴포넌트 아키텍처 개요 (`MachineConfigDialog`)

`MachineConfigDialog`는 캔버스에서 노드를 우클릭하거나 노드 카드의 `[ ⚙ ]` 버튼을 클릭했을 때 호출되는 모달 대화상자입니다.

```mermaid
flowchart TB
    subgraph Header["1. 기계 식별 & 워크스테이션"]
        direction LR
        TITLE["기계 명칭 / 아이콘 / 카테고리"] ~~~ WS_SELECT["연역적 워크스테이션 선택기 (1:N)"]
    end

    subgraph Section1["2. 기본 병렬 처리 설정"]
        direction LR
        PAR_INPUT["병렬 직접 입력 (1 ~ 4096)"] ~~~ PRESETS["퀵 프리셋: 1x, 4x, 16x, 64x, 256x"] ~~~ AUTO_MAX["[⚡ 자동 최대 병렬]"]
    end

    subgraph Section2["3. 장착 애드온 랙"]
        direction LR
        ACTIVE_LIST["장착된 칩 목록 (코일, 해치 등)"] ~~~ STATS_BADGE["시간/전력 승수, 병렬 뱃지"] ~~~ DEL_BTN["[✕] 1클릭 애드온 제거"]
    end

    subgraph Section3["4. 애드온 카탈로그 브라우저"]
        direction LR
        TABS["카테고리 탭: 코일, 해치, 로터 등"] ~~~ SEARCH["실시간 애드온 검색"] ~~~ CHIP_GRID["스마트 칩 그리드 (1클릭 장착)"]
    end

    Header --> Section1 --> Section2 --> Section3
```

---

## 2. 기계 설정 및 애드온 랙 UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Dialog Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 440px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Dialog Header -->
    <div style="background-color: #2a2e39; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 14px;">⚙</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 13px;">기계 공정 및 하드웨어 설정</span>
        <span style="background: #334155; color: #f1f5f9; padding: 1px 6px; border-radius: 3px; font-size: 10px; font-weight: bold;">LV</span>
      </div>
      <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 2px 7px; font-size: 11px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
    </div>
    <!-- Dialog Body -->
    <div style="padding: 12px; display: flex; flex-direction: column; gap: 10px;">
      <!-- Section A: Parallel Control -->
      <div style="background: #181d26; border: 1px solid #2d3748; border-radius: 4px; padding: 8px;">
        <div style="font-weight: bold; color: #38bdf8; font-size: 11px; margin-bottom: 6px;">기본 병렬 처리 수량 (Parallel Multiplier)</div>
        <div style="display: flex; align-items: center; gap: 6px;">
          <input type="text" value="16" style="background: #14171e; border: 1px solid #0284c7; border-radius: 3px; color: #38bdf8; font-weight: bold; width: 42px; padding: 2px 6px; font-size: 12px; text-align: center;" />
          <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; cursor: pointer;">1x</button>
          <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; cursor: pointer;">4x</button>
          <button style="background: #0284c7; border: 1px solid #38bdf8; border-radius: 3px; color: #ffffff; font-weight: bold; padding: 2px 6px; font-size: 10px; cursor: pointer;">16x</button>
          <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; cursor: pointer;">64x</button>
          <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; cursor: pointer;">256x</button>
          <button style="background: #7c2d12; border: 1px solid #ea580c; border-radius: 3px; color: #ffedd5; font-weight: bold; padding: 2px 6px; font-size: 10px; margin-left: auto; cursor: pointer;">⚡ 자동 최대</button>
        </div>
      </div>
      <!-- Section B: Installed Addons -->
      <div style="background: #181d26; border: 1px solid #2d3748; border-radius: 4px; padding: 8px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
          <span style="font-weight: bold; color: #a78bfa; font-size: 11px;">장착된 하드웨어 애드온 (2개)</span>
          <span style="color: #64748b; font-size: 10px;">클릭하여 제거</span>
        </div>
        <div style="display: flex; flex-direction: column; gap: 4px;">
          <!-- Item 1: Coil -->
          <div style="background: #1f2430; border: 1px solid #475569; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span>🧲</span>
              <span style="color: #f8fafc; font-weight: 500;">쿠프로니켈 코일 블록</span>
              <span style="background: #451a03; color: #fdba74; font-size: 9px; padding: 1px 4px; border-radius: 2px;">1,800 K</span>
            </div>
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #10b981; font-size: 10px;">전력 0.95x</span>
              <span style="color: #ef4444; font-weight: bold; cursor: pointer; padding: 0 4px;">✕</span>
            </div>
          </div>
          <!-- Item 2: Parallel Hatch -->
          <div style="background: #1f2430; border: 1px solid #475569; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span>⚡</span>
              <span style="color: #f8fafc; font-weight: 500;">EV 병렬 제어 해치</span>
              <span style="background: #1e1b4b; color: #c7d2fe; font-size: 9px; padding: 1px 4px; border-radius: 2px;">EV 티어</span>
            </div>
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #38bdf8; font-size: 10px;">병렬 16x</span>
              <span style="color: #ef4444; font-weight: bold; cursor: pointer; padding: 0 4px;">✕</span>
            </div>
          </div>
        </div>
      </div>
      <!-- Section C: Addon Catalog Browser -->
      <div style="background: #181d26; border: 1px solid #2d3748; border-radius: 4px; padding: 8px;">
        <div style="display: flex; gap: 4px; margin-bottom: 8px; flex-wrap: wrap;">
          <span style="background: #334155; color: #f8fafc; padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: bold; cursor: pointer;">전체</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">코일 (Coils)</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">병렬 해치</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">유지보수</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">터빈 로터</span>
          <span style="background: #1e293b; color: #94a3b8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">써멀 증강</span>
          <span style="background: #1e293b; color: #a855f7; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">+ 커스텀</span>
        </div>
        <!-- Search & Catalog Chips Grid -->
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 4px; max-height: 90px; overflow-y: hidden;">
          <div style="background: #14171e; border: 1px solid #334155; border-radius: 3px; padding: 3px 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <span style="color: #cbd5e1; font-size: 11px;">칸탈 코일 블록</span>
            <span style="color: #f59e0b; font-size: 9px;">2,700 K</span>
          </div>
          <div style="background: #14171e; border: 1px solid #334155; border-radius: 3px; padding: 3px 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <span style="color: #cbd5e1; font-size: 11px;">니크롬 코일 블록</span>
            <span style="color: #f59e0b; font-size: 9px;">3,600 K</span>
          </div>
          <div style="background: #14171e; border: 1px solid #334155; border-radius: 3px; padding: 3px 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <span style="color: #cbd5e1; font-size: 11px;">RTM 코일 블록</span>
            <span style="color: #f59e0b; font-size: 9px;">4,500 K</span>
          </div>
          <div style="background: #14171e; border: 1px solid #334155; border-radius: 3px; padding: 3px 6px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <span style="color: #cbd5e1; font-size: 11px;">HSS-G 코일 블록</span>
            <span style="color: #f59e0b; font-size: 9px;">5,400 K</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

## 3. 대체 레시피 인플레이스 전환 (`AlternativeRecipeFinder`)

캔버스에 이미 배치된 머신 노드의 레시피를 삭제하지 않고 동일 머신/동일 주요 산출물을 가진 대체 레시피로 즉시 교체합니다.

* **진입점**: 다이얼로그 상단 헤더의 `[🔄 레시피]` 버튼 (`74px` 컴팩트 버튼) 또는 노드 우클릭 컨텍스트 메뉴.
* **헤더 레이아웃 최적화**:
  - 다국어 번역 길이 차이에 따른 겹침 방지를 위해 상단 버튼 폭 최적화 (`[🔄 레시피]` 74px, `[싱글/멀티블록 모드]` 84px, `[Aa 1.0x]` 44px).
  - 다이얼로그 제목(`title`) 텍스트가 가용 폭을 초과할 경우 자동 말줄임표(`...`) 적용.
* **대체 레시피 우선순위 탐색**:
  1. **1순위**: 동일 머신 ID / 카테고리 + 동일 주 산출물(1차/2차 Output) 레시피.
  2. **2순위**: 동일 머신 카테고리의 모든 대체 레시피.
* **스마트 와이어 보존 (Smart Wire Preservation)**:
  - 교체 전/후 동일한 아이템/유체 식별자(`IngredientStack.getId()`)를 가진 포트의 와이어 연결을 자동 유지.
  - 새 레시피에서 제거된 재료 포트의 와이어만 안전하게 단선 처리.
  - `Undo/Redo` 히스토리 스택에 기록되어 단축키(`Ctrl+Z`)로 완전 롤백 지원.

---

## 4. UI 가독성 배율 옵션 (`FontScale`)

다양한 모니터 해상도와 시각 선호도에 대응하기 위해 5단계 UI 배율 기능을 제공합니다.

* **5단계 배율 모드**:
  - `MINI (0.75x)`: GUI Scale 4/Auto 등 고배율 환경용 초소형 모드.
  - `COMPACT (0.85x)`: 많은 정보와 애드온을 한눈에 조밀하게 표시.
  - `NORMAL (1.0x)`: 마인크래프트 기본 표준 크기.
  - `LARGE (1.15x)`: 고해상도 모니터용 확대 표시.
  - `HUGE (1.30x)`: 4K 환경 및 가독성 우선 환경용 최대 확대.
* **제어 인터랙션**:
  - 다이얼로그 우측 상단 `[Aa 1.0x]` 버튼: 좌클릭 순환 확대, 우클릭 순환 축소, 마우스 휠 스크롤 조절.
  - 키보드 단축키: `+` (확대), `-` (축소).
  - 중심점 Matrix Scaling & Virtual Mouse 역변환을 통해 레이아웃 왜곡 없이 100% 정밀한 클릭 판정 보장.

---

> ➡️ **다음 장으로 이동**: [[03-03] 페이지 결산 및 전역 밸런스 대시보드 UI](03_03_PAGE_SUMMARY_AND_DASHBOARD.md)
