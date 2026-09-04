# [03-04] 레시피 검색, 필터링, 단축키 HUD, 가이드북 및 토스트 UI (Search & Tools)

> 📍 **GTCalcBoard 기술 명세서 시리즈**
> [[00] 시스템 개요](00_OVERVIEW.md) ➔ [[01] 코어 도메인](01_CORE_DOMAIN_AND_MODELS.md) ➔ [[02] 수학 엔진](02_MATH_AND_ALGORITHMS.md) ➔ [[03-01] 캔버스 & 노드 카드](03_01_CANVAS_AND_NODE_CARDS.md) ➔ [[03-02] 기계 설정 & 애드온](03_02_MACHINE_CONFIG_AND_ADDONS.md) ➔ [[03-03] 페이지 결산 & 대시보드](03_03_PAGE_SUMMARY_AND_DASHBOARD.md) ➔ **[03-04] 검색, 도구 & 가이드** ➔ [[04] 멀티플레이어](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] 외부 연동](05_INTEGRATION_AND_I18N.md)

---

## 1. 비동기 레시피 검색 엔진 및 다이얼로그 (`RecipeSearchDialog`)

수만 개의 레시피를 실시간으로 검색하고 캔버스에 즉시 노드로 스폰할 수 있는 통합 검색 다이얼로그입니다.

* **파라메트릭 다중 토큰 검색**:
  - `&` / 공백 (AND), `|` (OR), `!` (NOT)
  - `@gtceu` (모드명 필터), `#logs` (태그 필터), `[chemical_reactor]` (카테고리 필터), `"exact"` (정확 일치)
* **드래그 앤 검색 (Drag to Search)**: 빈 포트에서 와이어를 끌어 캔버스 빈 공간에 드롭하면 해당 재료를 소모/생산하는 레시피를 자동 검색하고 1클릭으로 노드 생성 및 자동 배선 완료.
* **스크롤바 인터랙션 상태 머신 (ADR-012)**:
  - **트랙 클릭 점프 (Track Click Jump)**: 스크롤바 트랙의 빈 영역 클릭 시 클릭한 높이 비율에 맞추어 목록이 즉각 비례 이동.
  - **썸 드래그 (Thumb Dragging)**: 마우스 좌클릭 후 윈도우/다이얼로그 영역을 벗어나더라도 드래그 상태를 안전하게 캡처하여 끊김 없는 부드러운 스크롤 이동 보장.
  - **마우스 휠 가속**: 휠 1틱당 정확한 행 단위($48\text{px}$) 스크롤.

### 1.1 `RecipeSearchDialog` UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Search Dialog Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 460px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #2a2e39; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 14px;">🔍</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 13px;">레시피 검색 및 노드 생성</span>
      </div>
      <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 2px 7px; font-size: 11px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
    </div>
    <!-- 2. Search Input & Filter Button -->
    <div style="padding: 10px 12px; background: #181d26; border-bottom: 1px solid #2d3748; display: flex; gap: 8px; align-items: center;">
      <input type="text" value="@gtceu benzene" style="background: #14171e; border: 1px solid #0284c7; border-radius: 3px; color: #38bdf8; padding: 4px 8px; font-size: 12px; flex: 1;" />
      <button style="background: #1e293b; border: 1px solid #475569; border-radius: 3px; color: #cbd5e1; padding: 4px 8px; font-size: 11px; cursor: pointer;">🔍 타입/카테고리 필터</button>
    </div>
    <!-- 3. Search Results List -->
    <div style="padding: 10px; display: flex; flex-direction: column; gap: 6px;">
      <!-- Result 1 -->
      <div style="background: #14171e; border: 1px solid #334155; border-radius: 4px; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span>⚗️</span>
          <div>
            <div style="color: #f8fafc; font-weight: bold; font-size: 12px;">벤젠 크래킹 ➔ 에틸렌 & 가솔린</div>
            <div style="color: #64748b; font-size: 10px;">대형 화학 반응기 | 소요 시간: 1.25s | 소모 전력: 480 EU/t (LV)</div>
          </div>
        </div>
        <button style="background: #0284c7; border: 1px solid #38bdf8; border-radius: 3px; color: #ffffff; padding: 3px 8px; font-size: 10px; font-weight: bold; cursor: pointer;">+ 노드 생성</button>
      </div>
      <!-- Result 2 -->
      <div style="background: #14171e; border: 1px solid #334155; border-radius: 4px; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span>⚡</span>
          <div>
            <div style="color: #f8fafc; font-weight: bold; font-size: 12px;">원유 고압 증류 공정</div>
            <div style="color: #64748b; font-size: 10px;">증류탑 (Distillation Tower) | 소요 시간: 4.00s | 소모 전력: 1,920 EU/t (MV)</div>
          </div>
        </div>
        <button style="background: #0284c7; border: 1px solid #38bdf8; border-radius: 3px; color: #ffffff; padding: 3px 8px; font-size: 10px; font-weight: bold; cursor: pointer;">+ 노드 생성</button>
      </div>
    </div>
  </div>
</div>

---

## 2. 레시피 카테고리 필터링 모달 (`RecipeFilterDialog`)

검색 결과에서 불필요한 EMI 레시피 카테고리를 제외하거나 특정 공정만 활성화하기 위한 전용 필터 모달입니다.

### 2.1 `RecipeFilterDialog` UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Filter Dialog Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 340px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #2a2e39; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 6px;">
        <span>🔍</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 12px;">레시피 카테고리 필터</span>
      </div>
      <span style="color: #94a3b8; font-weight: bold; cursor: pointer; font-size: 11px;">✕</span>
    </div>
    <!-- 2. Search & Select All/None Buttons -->
    <div style="padding: 8px; background: #181d26; border-bottom: 1px solid #2d3748; display: flex; flex-direction: column; gap: 6px;">
      <input type="text" placeholder="카테고리 검색..." style="background: #14171e; border: 1px solid #334155; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 11px;" />
      <div style="display: flex; gap: 4px;">
        <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 2px; color: #cbd5e1; padding: 1px 6px; font-size: 10px; cursor: pointer;">전체 선택</button>
        <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 2px; color: #cbd5e1; padding: 1px 6px; font-size: 10px; cursor: pointer;">전체 해제</button>
      </div>
    </div>
    <!-- 3. Category Checkbox List -->
    <div style="padding: 8px; display: flex; flex-direction: column; gap: 4px; max-height: 120px;">
      <label style="display: flex; align-items: center; justify-content: space-between; background: #14171e; padding: 3px 6px; border-radius: 3px; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 6px;">
          <input type="checkbox" checked style="cursor: pointer;" />
          <span style="color: #f8fafc; font-size: 11px;">화학 반응기 (Chemical Reactor)</span>
        </div>
        <span style="color: #64748b; font-size: 10px;">342개</span>
      </label>
      <label style="display: flex; align-items: center; justify-content: space-between; background: #14171e; padding: 3px 6px; border-radius: 3px; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 6px;">
          <input type="checkbox" checked style="cursor: pointer;" />
          <span style="color: #f8fafc; font-size: 11px;">증류탑 (Distillation Tower)</span>
        </div>
        <span style="color: #64748b; font-size: 10px;">88개</span>
      </label>
      <label style="display: flex; align-items: center; justify-content: space-between; background: #14171e; padding: 3px 6px; border-radius: 3px; cursor: pointer;">
        <div style="display: flex; align-items: center; gap: 6px;">
          <input type="checkbox" style="cursor: pointer;" />
          <span style="color: #94a3b8; font-size: 11px;">원심분리기 (Centrifuge)</span>
        </div>
        <span style="color: #64748b; font-size: 10px;">194개</span>
      </label>
    </div>
  </div>
</div>

---

## 3. 상단 탭 바, 하단 툴바 및 단축키 안내 HUD (`HotkeyHudWidget`)

### 3.1 탭 바 & 툴바 UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Combined Toolbar Widget -->
  <div style="display: flex; flex-direction: column; gap: 8px; width: 460px;">
    <!-- Top Workspace & Page Tab Bar -->
    <div style="background: #1e222b; border: 1px solid #3d4455; border-radius: 4px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between;">
      <div style="display: flex; gap: 4px;">
        <span style="background: #0284c7; color: #ffffff; font-weight: bold; padding: 3px 8px; border-radius: 3px; font-size: 11px;">👤 개인: 벤젠 공정</span>
        <span style="background: #14171e; color: #94a3b8; padding: 3px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">+ 새 탭</span>
        <span style="background: #1e1b4b; border: 1px solid #4338ca; color: #c7d2fe; padding: 3px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">👥 팀 공유 (FTB)</span>
      </div>
      <span style="background: #064e3b; color: #6ee7b7; padding: 1px 6px; border-radius: 2px; font-size: 10px;">● 동기화 완료</span>
    </div>
    <!-- Bottom Quick Action Toolbar -->
    <div style="background: #1e222b; border: 1px solid #3d4455; border-radius: 4px; padding: 4px 8px; display: flex; gap: 6px; align-items: center;">
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 3px 8px; font-size: 11px; cursor: pointer;">🔍 레시피 검색</button>
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 3px 8px; font-size: 11px; cursor: pointer;">⚡ 스마트 자동 연결</button>
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #fbbf24; padding: 3px 8px; font-size: 11px; cursor: pointer;">📊 전역 대시보드 (B)</button>
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #a78bfa; padding: 3px 8px; font-size: 11px; cursor: pointer;">📖 가이드북 (F1)</button>
      <button style="background: #252a36; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; padding: 3px 8px; font-size: 11px; cursor: pointer; margin-left: auto;">⚙ 환경설정</button>
    </div>
  </div>
</div>

### 3.1.1 적응형 반응형 툴바 및 오버플로우 메뉴 (`ToolbarWidget`, ADR-017)
* **3단계 적응형 타이틀 축약**: 화면 너비에 따라 타이틀을 자동 축약하여 버튼 가용 공간을 극대화합니다:
  - $\ge 560\text{px}$: 풀 타이틀 `"§6그렉텍 계산기 보드 §7⚙"`
  - $420\sim 559\text{px}$: 축약 타이틀 `"§6GT 보드 §7⚙"` (다국어 `gui.gtcalcboard.title_short` 1:1 지원)
  - $< 420\text{px}$: 마이크로 아이콘 `"§6📟 §7⚙"`
* **컴팩트 $22\text{px}$ 아이콘 모드**: 툴바 표시 모드(`AUTO`, `COMPACT`, `FULL`)에 따라 텍스트 라벨을 숨기고 사각 아이콘 버튼으로 전환.
* **초과 버튼 `[...]` 오버플로우 드롭다운 메뉴**: 툴바 가용 너비를 초과하는 버튼을 절삭하지 않고 오버플로우 팝업 메뉴로 안전하게 격리 집약.
* **미니맵 충돌 방지 여백**: JourneyMap 등 외부 미니맵과의 겹침을 방지하기 위해 우측 $16\text{px}$ 안전 패딩을 자동 확보.

---

### 3.2 단축키 안내 HUD (`HotkeyHudWidget`)

화면 좌측 하단에 상주하며, 주요 키보드 단축키를 안내합니다. 우측 상단의 `[-]` 버튼 또는 `[?]` 칩 클릭으로 접거나 펼칠 수 있습니다.

* **전역 UI 상태 영속화 (`BoardManager`)**: 패널 접기/펼침 상태가 `BoardManager.hotkeyHudExpanded` NBT 태그에 전역 저장되어 보드 창을 닫았다 열어도 사용자의 뷰 설정이 그대로 유지됩니다.

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Hotkey HUD Widget Container -->
  <div style="background-color: #0b1120; border: 1.5px solid #1e293b; border-radius: 5px; width: 220px; box-shadow: 0 4px 16px rgba(0,0,0,0.5); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #1e293b; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between;">
      <span style="font-weight: bold; color: #38bdf8; font-size: 11px;">단축키 안내 (Hotkeys)</span>
      <span style="color: #64748b; font-size: 10px; cursor: pointer;" title="접기">✕</span>
    </div>
    <!-- 2. Key Lines -->
    <div style="padding: 6px 8px; display: flex; flex-direction: column; gap: 4px; font-size: 10px;">
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">B</span>
        <span style="color: #94a3b8;">전역 밸런스 대시보드</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">T</span>
        <span style="color: #94a3b8;">시간 단위 순환 (/s, /min, /h, /d, /t)</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Shift+T</span>
        <span style="color: #38bdf8;">유체 단위 순환 (Auto, mB, B)</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Space</span>
        <span style="color: #94a3b8;">레시피 검색창 열기</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Shift+Drag</span>
        <span style="color: #94a3b8;">1:1 유량 정합 자동 맞춤</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Ctrl+G</span>
        <span style="color: #f5d0fe;">복합 모듈화 / 펼치기</span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="background: #1e293b; color: #f1f5f9; padding: 0 4px; border-radius: 2px; font-family: monospace;">Ctrl+Z / Y</span>
        <span style="color: #94a3b8;">실행 취소 / 다시 실행</span>
      </div>
    </div>
  </div>
</div>

### 3.3 좌측 상단 접이식 EMI 즐겨찾기 독 및 서브 플라이아웃 (`FavoritesDockWidget`)

검색창을 열지 않고도 기본 보드 화면에서 EMI 즐겨찾기(`A` 키) 레시피를 실시간으로 확인하고 1클릭으로 캔버스에 배치할 수 있는 전용 사이드 독입니다.

* **좌측 상단 배치**: `x = 8`, `y = 52` (상단 툴바 바로 아래).
* **접이식 토글 & 전역 영속화**:
  - `[⭐ 즐겨찾기 (N) ◀/▶]` 버튼으로 펼치기/접기 지원.
  - UI 상태(`favoritesDockExpanded`)는 `BoardManager`를 통해 NBT에 영구 보존.
* **1:1 아이템/레시피 매핑 & 우측 서브 플라이아웃 탐색 (`Sub-flyout Recipe Selector`)**:
  - **레시피 즐겨찾기**: 1클릭 또는 드래그로 즉시 캔버스에 배치.
  - **아이템 즐겨찾기**: 마우스 호버/선택 시 우측에 **생산 레시피 서브 패널**(`185px`)이 자동으로 열려, 해당 아이템을 만드는 모든 기계별/티어별 레시피를 휠 스크롤로 확인하고 원하는 레시피를 선택/드래그하여 배치 가능.
* **원클릭 & 드래그 앤 드롭 스폰**:
  - 아이템/레시피 클릭: 캔버스의 다음 적절한 위치에 노드 자동 생성.
  - 마우스 드래그: 원하는 레시피를 마우스로 끌어 캔버스 원하는 좌표에 드롭하여 즉시 배치.

#### `FavoritesDockWidget` UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <div style="display: flex; gap: 8px; align-items: flex-start;">
    <!-- 1. Main Favorites Dock -->
    <div style="background: rgba(15, 23, 42, 0.95); border: 1.5px solid #38bdf8; border-radius: 4px; width: 145px; box-shadow: 0 4px 12px rgba(0,0,0,0.6); overflow: hidden;">
      <!-- Header -->
      <div style="background: #1e293b; padding: 4px 8px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155;">
        <span style="color: #ffd700; font-weight: bold; font-size: 11px;">⭐ 즐겨찾기 (4)</span>
        <span style="color: #94a3b8; font-size: 10px; cursor: pointer;">◀</span>
      </div>
      <!-- List Items -->
      <div style="padding: 4px; display: flex; flex-direction: column; gap: 3px;">
        <div style="background: #2a3649; border: 1px solid #38bdf8; border-radius: 3px; padding: 3px 5px; display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <div style="display: flex; align-items: center; gap: 6px;">
            <span>🧊</span>
            <span style="color: #ffffff; font-size: 11px; font-weight: bold;">강철 프레임</span>
          </div>
          <span style="color: #ffd700; font-size: 10px;">▶</span>
        </div>
        <div style="background: #141b2a; border: 1px solid #232d3d; border-radius: 3px; padding: 3px 5px; display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <div style="display: flex; align-items: center; gap: 6px;">
            <span>📦</span>
            <span style="color: #cbd5e1; font-size: 11px;">영혼 주입 블록</span>
          </div>
          <span style="color: #94a3b8; font-size: 10px;">▶</span>
        </div>
        <div style="background: #141b2a; border: 1px solid #232d3d; border-radius: 3px; padding: 3px 5px; display: flex; justify-content: space-between; align-items: center; cursor: pointer;">
          <div style="display: flex; align-items: center; gap: 6px;">
            <span>⚗️</span>
            <span style="color: #cbd5e1; font-size: 11px;">황산 제조</span>
          </div>
          <span style="color: #64748b; font-size: 10px;">1</span>
        </div>
      </div>
    </div>

    <!-- 2. Sub-flyout Recipe Selector -->
    <div style="background: rgba(11, 17, 30, 0.98); border: 1.5px solid #38bdf8; border-radius: 4px; width: 185px; box-shadow: 0 6px 16px rgba(0,0,0,0.8); overflow: hidden;">
      <div style="background: #172033; padding: 4px 8px; border-bottom: 1px solid #334155;">
        <span style="color: #38bdf8; font-weight: bold; font-size: 11px;">강철 프레임 (3)</span>
      </div>
      <div style="padding: 4px; display: flex; flex-direction: column; gap: 3px;">
        <div style="background: #1e293b; border: 1px solid #ffd700; border-radius: 3px; padding: 3px 6px; cursor: pointer;">
          <div style="color: #ffd700; font-size: 11px; font-weight: bold;">조립기 (Assembler)</div>
          <div style="color: #94a3b8; font-size: 9px;">0.5s | 30 EU/t (LV)</div>
        </div>
        <div style="background: #131c2e; border-radius: 3px; padding: 3px 6px; cursor: pointer;">
          <div style="color: #ffffff; font-size: 11px;">수작업 조합 (Crafting)</div>
          <div style="color: #94a3b8; font-size: 9px;">0.0s | 0 EU/t</div>
        </div>
        <div style="background: #131c2e; border-radius: 3px; padding: 3px 6px; cursor: pointer;">
          <div style="color: #ffffff; font-size: 11px;">포징 해머 (Forge Hammer)</div>
          <div style="color: #94a3b8; font-size: 9px;">1.0s | 16 EU/t (LV)</div>
        </div>
      </div>
    </div>
  </div>
</div>

---

## 4. 인게임 가이드북 & 매뉴얼 다이얼로그 (`GuideDialog`)

단축키 `F1` 또는 툴바의 가이드북 버튼을 통해 호출되는 인터랙티브 매뉴얼입니다. 8개의 체계적인 카테고리 탭과 시각적 키캡(Keycap) 하이라이트를 통해 핵심 기능을 안내합니다.

* **사이드바 8대 카테고리**:
  1. `기본 조작 (BASICS)`: 화면 이동, 줌, 노드 선택 및 드래그
  2. `레시피 검색 (SEARCH)`: EMI 검색 및 드래그 앤 검색
  3. `와이어 배선 (WIRING)`: 베지어 와이어 연결 및 자석 스냅
  4. `비율 맞춤 (MASTER)`: 1:1 유량 정합 및 Auto-Ratio
  5. `복합 모듈 (MODULE)`: 서브 그래프 패키징 (`Ctrl+G`)
  6. `팀 협업 (TEAM)`: FTB Teams 동시 편집 및 분산 락
  7. `페이지 관리 (PAGES)`: 다중 탭 분할 및 전역 대시보드
  8. `단축키 (SHORTCUTS)`: 전체 핫키 일람

### 4.1 `GuideDialog` UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Guide Dialog Container -->
  <div style="background-color: #121722; border: 1.5px solid #3d4b66; border-radius: 6px; width: 500px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #1c2433; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #2e3a4e;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span>📖</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 13px;">GTCalcBoard 종합 가이드북</span>
      </div>
      <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 2px 7px; font-size: 11px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
    </div>
    <!-- Main Body: Sidebar + Guide Content -->
    <div style="display: flex; min-height: 200px;">
      <!-- Left Sidebar Tabs -->
      <div style="width: 145px; background: #0f131c; border-right: 1px solid #232d3d; padding: 8px; display: flex; flex-direction: column; gap: 3px;">
        <span style="background: #0284c7; color: #ffffff; font-weight: bold; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">기본 조작 (BASICS)</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">레시피 검색</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">와이어 배선</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">비율 자동 맞춤</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">복합 모듈 (Ctrl+G)</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">팀 협업 & 락</span>
        <span style="background: #161b26; color: #94a3b8; padding: 4px 8px; border-radius: 3px; font-size: 11px; cursor: pointer;">단축키 일람</span>
      </div>
      <!-- Right Content View -->
      <div style="flex: 1; padding: 12px; display: flex; flex-direction: column; gap: 8px;">
        <div style="font-weight: bold; color: #38bdf8; font-size: 13px; border-bottom: 1px solid #232d3d; padding-bottom: 4px;">1. 캔버스 뷰포트 조작 및 노드 선택</div>
        <div style="color: #cbd5e1; font-size: 11px; line-height: 1.5;">
          • <span style="background: #2a3447; border: 1px solid #4a5c7d; border-radius: 2px; padding: 1px 4px; color: #ffffaa; font-family: monospace;">우클릭 드래그</span> 또는 <span style="background: #2a3447; border: 1px solid #4a5c7d; border-radius: 2px; padding: 1px 4px; color: #ffffaa; font-family: monospace;">휠 드래그</span>로 캔버스를 무한히 이동합니다.<br/>
          • <span style="background: #2a3447; border: 1px solid #4a5c7d; border-radius: 2px; padding: 1px 4px; color: #ffffaa; font-family: monospace;">마우스 휠</span>로 줌 인/아웃 (0.25x ~ 2.5x)을 조절합니다.<br/>
          • <span style="background: #2a3447; border: 1px solid #4a5c7d; border-radius: 2px; padding: 1px 4px; color: #ffffaa; font-family: monospace;">좌클릭 드래그</span>로 영역 내 노드를 일괄 다중 선택합니다.
        </div>
      </div>
    </div>
  </div>
</div>

---

## 5. 전역 토스트 알림 시스템 (`BoardToast`)

모든 UI 레이어 최상단(화면 하단 중앙 `screenHeight - 48px`)에 부드러운 페이드 애니메이션($2,200\text{ms}$ 노출, $250\text{ms}$ 페이드 인/아웃)으로 액션 피드백을 전달합니다.

* **트리거 상황**:
  - 클립보드 복사/잘라내기/붙여넣기 (`Ctrl+C / V`)
  - 자동 저장 완료 (`Auto-saved successfully`)
  - 멀티플레이 동시 편집 락 획득/반납/만료
  - 블루프린트 가져오기/내보내기 완료

### 5.1 `BoardToast` UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Toast Floating Card -->
  <div style="background: rgba(24, 28, 38, 0.95); border: 1.5px solid #4e5d7a; border-radius: 4px; padding: 6px 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.6); display: inline-flex; align-items: center; gap: 8px;">
    <span style="color: #38bdf8; font-size: 13px;">✔</span>
    <span style="color: #ffffff; font-weight: bold; font-size: 11px;">클립보드에 12개의 노드가 복사되었습니다. (Ctrl+V로 붙여넣기)</span>
  </div>
</div>

---

## 6. 인터랙티브 온보딩 튜토리얼 (`tutorial/*`)

신규 사용자가 계산기 보드의 핵심 기능(노드 생성, 와이어 배선, 비율 맞춤, 모듈화, 기계 전환, 공유 기계 풀)을 쉽게 익힐 수 있도록 단계별 가이드 말풍선과 UI 하이라이트 글로우(Glow) 효과를 제공합니다.

* **`TutorialManager`**: 싱글톤 튜토리얼 상태 관리자. 단계별 목표 달성 감지(`TutorialStep`).
* **`TutorialOverlay`**: 타겟 노드, 버튼, 포트 주변에 펄스 애니메이션 테두리와 안내 가이드 팝업 렌더링.

### 6.1 14단계 대화형 튜토리얼 시퀀스 (14-Step Tutorial Progression Sequence)

온보딩 튜토리얼은 신규 사용자를 위한 기본 튜토리얼(1~9단계)과 파워 유저를 위한 고급 튜토리얼(10~13단계)의 14단계 체계(`TutorialStep`)로 구성됩니다:

| 단계 (Step) | 식별자 (Identifier) | 코스 구분 | 학습 내용 및 완료 조건 |
| :---: | :--- | :---: | :--- |
| **Step 1** | `STEP_1_ADD_RECIPE` | 기본 | 툴바 레시피 추가 버튼 클릭 및 캔버스에 노드 배치 |
| **Step 2** | `STEP_2_DRAG_TO_SEARCH` | 기본 | 보일러 출력 포트에서 드래그하여 증기 터빈 입력 포트와 배선 연결 |
| **Step 3** | `STEP_3_JUNCTION` | 기본 | 증기 배선 와이어를 클릭하여 분기점(Junction) 노드 자동 삽입 |
| **Step 4** | `STEP_4_SHIFT_WIRING` | 기본 | `Shift + 드래그` 배선으로 1:1 유량 비율 자동 정합(Auto-Ratio) 실습 |
| **Step 5** | `STEP_5_JUNCTION_ETA` | 기본 | 분기점 하단 뱃지 클릭 ➔ 목표 배치 수량(1,000 mB) 지정 및 실시간 예상 생산 완료 시간(`ET: xx.xs`) 확인 |
| **Step 6** | `STEP_6_MACHINE_SELECTOR` | 기본 | 기계 카드 아이콘 클릭 ➔ `MachineSelectorDialog`에서 전기로 ➔ EBF 기계 전환 및 능력 뱃지 확인 |
| **Step 7** | `STEP_7_MACHINE_CONFIG` | 기본 | 기계 설정 모달(`C` 키)을 열어 전압 티어, 가열 코일, 오버클럭 사양 확인 |
| **Step 8** | `STEP_8_GROUP_FRAME` | 기본 | 마우스 드래그로 노드들을 다중 선택하고 프레임 그룹 생성 |
| **Step 9** | `STEP_9_COMPOUND_MODULE`| 기본 | 프레임 접기 버튼 클릭으로 복합 모듈 축약 및 전개 실습 (기본 튜토리얼 완료) |
| **Step 10** | `STEP_10_SHARED_MACHINE` | 고급 | 절단기 3대를 공유 머신 풀 프레임으로 묶어 통합 물리 기계 대수 및 가동률 점유율(`Total Duty %`) 확인 |
| **Step 11** | `STEP_11_BOM_INSPECTION` | 고급 | 툴바 `[📦 BOM]` 버튼을 클릭하여 공유 기계 풀이 정수 올림 처리된 멀티블록 자재 청구서 확인 |
| **Step 12** | `STEP_12_JUNCTION_SUPPLY` | 고급 | 분기점 노드 우클릭 ➔ 외부 공급 모드(무한 공급 $\infty$ / 고정 유량 한도) 지정 및 투입 원자재 고갈 시간(`DT: xx.xs`) 확인 |
| **Step 13** | `STEP_13_FOLDER_BROWSER` | 고급 | 툴바 `[📁]` 버튼을 클릭하여 좌측 계층형 페이지 탐색기 드로어를 열고 페이지 구조 관리 실습 (고급 튜토리얼 완료) |
| **Step 14** | `COMPLETED` | 완료 | 모든 실습 완료 및 튜토리얼 완주 배지 획득 |

---

## 7. 계층형 페이지 탐색기 드로어 (`PageBrowserDrawer`) (ADR-012)

화면 좌측에서 슬라이드 인/아웃되는 계층형 디렉터리 사이드바로, 수백 개의 캔버스 페이지를 폴더 트리 구조로 탐색하고 관리합니다.

* **가상 디렉터리 트리**: `folderPath` 기반의 무한 깊이 가상 폴더 트리 생성, 폴더 접기/펼치기 및 실시간 필터 검색.
* **페이지 드래그 앤 드롭 이동**: 페이지 항목을 다른 폴더로 드래그하여 `folderPath` 즉시 갱신 및 NBT 동기화.
* **우클릭 컨텍스트 메뉴**: 신규 페이지 생성, 신규 폴더 생성, 이름 변경, 복제, 삭제 기능 제공.

---

## 8. 키보드 퀵 페이지 스위처 (`QuickPageSwitcherDialog`) (ADR-012)

단축키 `Ctrl + K`로 호출되는 IDE 스타일의 경량 팝업 모달로, 페이지 이름 및 폴더 경로를 퍼지(Fuzzy) 검색하여 원하는 캔버스 페이지로 즉시 점프합니다.

* **퍼지 검색 및 실시간 하이라이팅**: 영문/한글 자모 일치 및 폴더 경로 접두사 매칭.
* **키보드 탐색**: `↑` / `↓` 화살표 키로 항목 이동, `Enter` 키로 즉시 페이지 전환, `Esc` 키로 닫기.

---

> ➡️ **다음 장으로 이동**: [[04] 멀티플레이어 동시성 제어 및 네트워크 프로토콜](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)
