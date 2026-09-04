# [03-03] 페이지 결산 및 전역 밸런스 대시보드 UI (Summary & Dashboard)

> 📍 **GTCalcBoard 기술 명세서 시리즈**
> [[00] 시스템 개요](00_OVERVIEW.md) ➔ [[01] 코어 도메인](01_CORE_DOMAIN_AND_MODELS.md) ➔ [[02] 수학 엔진](02_MATH_AND_ALGORITHMS.md) ➔ [[03-01] 캔버스 & 노드 카드](03_01_CANVAS_AND_NODE_CARDS.md) ➔ [[03-02] 기계 설정 & 애드온](03_02_MACHINE_CONFIG_AND_ADDONS.md) ➔ **[03-03] 페이지 결산 & 대시보드** ➔ [[03-04] 검색 & 툴바](03_04_RECIPE_SEARCH_AND_TOOLS.md) ➔ [[04] 멀티플레이어](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] 외부 연동](05_INTEGRATION_AND_I18N.md)

---

## 1. 현재 페이지 결산 오버레이 (`SummaryOverlay`)

화면 우측에 상시 또는 접이식(Collapsed)으로 배치되어, 현재 활성화된 보드 페이지의 총 전력 소비/생산량, 가동 기계 수, 외부 순수 원자재 요구량 및 순수 생산 제품을 실시간으로 결산합니다.

* **전역 UI 상태 영속화 (`BoardManager`)**: 사용자가 패널 헤더 `[»]`를 클릭하여 접거나 펼치면, 해당 상태가 `BoardManager.summaryOverlayCollapsed` NBT 태그에 전역 저장되어 다른 페이지 탭 전환 및 보드 창 재진입 후에도 그대로 유지됩니다.

### 1.1 `SummaryOverlay` UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Summary Panel Container -->
  <div style="background-color: #1a1e26; border: 1.5px solid #3d4455; border-radius: 6px; width: 260px; box-shadow: 0 4px 16px rgba(0,0,0,0.5); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #242934; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 6px; font-weight: bold; color: #ffae00;">
        <span>⚡</span>
        <span style="color: #ffffff;">현재 페이지 공정 결산</span>
      </div>
      <span style="color: #94a3b8; font-weight: bold; cursor: pointer;" title="패널 접기">»</span>
    </div>
    <!-- 2. Power & Machine Totals -->
    <div style="padding: 8px 10px; background: #14171e; border-bottom: 1px solid #2d3748; display: flex; flex-direction: column; gap: 4px;">
      <div style="display: flex; justify-content: space-between;">
        <span style="color: #fbbf24; font-weight: 500;">총 소비 전력:</span>
        <span style="color: #f8fafc; font-weight: bold;">⚡ -4,800 EU/t <span style="color: #38bdf8; font-size: 10px;">(EV)</span></span>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="color: #cbd5e1;">가동 기계 대수:</span>
        <span style="color: #e2e8f0; font-weight: 500;">12대 <span style="color: #64748b; font-size: 10px;">(상세)</span></span>
      </div>
    </div>
    <!-- 3. Balance Lists -->
    <div style="padding: 10px; display: flex; flex-direction: column; gap: 8px;">
      <!-- Raw Inputs Section -->
      <div>
        <div style="font-size: 11px; font-weight: bold; color: #f87171; margin-bottom: 4px; display: flex; align-items: center; gap: 4px;">
          <span>▼</span> 순수 소모 원자재 (Raw Inputs)
        </div>
        <div style="background: #1f2430; border-radius: 4px; padding: 4px 6px; display: flex; flex-direction: column; gap: 3px; font-size: 11px;">
          <div style="display: flex; justify-content: space-between;">
            <span style="color: #cbd5e1;">벤젠</span>
            <span style="color: #f87171; font-weight: 500;">-100 mB/s</span>
          </div>
          <div style="display: flex; justify-content: space-between;">
            <span style="color: #cbd5e1;">수소</span>
            <span style="color: #f87171; font-weight: 500;">-2.0k mB/s</span>
          </div>
        </div>
      </div>
      <!-- Net Outputs Section -->
      <div>
        <div style="font-size: 11px; font-weight: bold; color: #34d399; margin-bottom: 4px; display: flex; align-items: center; gap: 4px;">
          <span>▲</span> 순수 최종 생산물 (Net Products)
        </div>
        <div style="background: #1f2430; border-radius: 4px; padding: 4px 6px; display: flex; flex-direction: column; gap: 3px; font-size: 11px;">
          <div style="display: flex; justify-content: space-between;">
            <span style="color: #cbd5e1;">에틸렌</span>
            <span style="color: #34d399; font-weight: bold;">+200 mB/s</span>
          </div>
          <div style="display: flex; justify-content: space-between;">
            <span style="color: #cbd5e1;">부산물 슬래그</span>
            <span style="color: #34d399; font-weight: bold;">+50 mB/s</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

### 1.2 폐기된 부산물 관리 및 원클릭 보이드/복원 UI (ADR-019)
- **순수 최종 생산물(Net Products) 행 보이드 버튼 `[🗑️]`**:
  - Net Products 목록의 각 항목에 마우스 호버 시 우측 끝에 `[🗑️]`(휴지통) 버튼이 노출됩니다.
  - 클릭 시 해당 물질을 출력하는 모든 활성 기계의 해당 포트가 `setOutputPortVoided(true)`로 일괄 마킹되며, 즉시 순 생산품 목록에서 제거되어 보이드 섹션으로 이동합니다.
- **접이식 `🗑️ 폐기된 부산물 (Voided Byproducts)` 섹션**:
  - 보이드 처리된 잉여 부산물이 1종 이상 존재할 때 Net Products 하단에 보라색 헤더를 가진 접이식 섹션으로 렌더링됩니다.
  - 폐기된 각 유량 항목 호버 시 `[↩️]`(되돌리기) 복원 버튼이 노출되며, 클릭 시 즉시 보이드 마킹이 해제되어 원래의 Net Products 결산으로 1클릭 복구됩니다.

---

## 2. 전역 종합 수지 대시보드 (`GlobalBalanceDashboardDialog`)

단축키 `B`를 통해 호출되는 전사적 팩토리 공정 대시보드입니다. 다중 보드 페이지를 선택하여 전체 공장의 전력 및 재료 수지를 크로스 매칭 집계합니다.

### 2.1 `GlobalBalanceDashboardDialog` UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Dashboard Dialog Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 560px; box-shadow: 0 8px 24px rgba(0,0,0,0.6); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #2a2e39; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 14px;">📊</span>
        <span style="font-weight: bold; color: #ffffff; font-size: 13px;">전역 공정 수지 대시보드 (Global Balance)</span>
      </div>
      <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 2px 7px; font-size: 11px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
    </div>
    <!-- Main Layout: Sidebar + Content -->
    <div style="display: flex; min-height: 220px;">
      <!-- Left Sidebar: Page Selector -->
      <div style="width: 170px; background: #181d26; border-right: 1px solid #2d3748; padding: 10px; display: flex; flex-direction: column; gap: 6px;">
        <div style="font-weight: bold; color: #94a3b8; font-size: 11px; margin-bottom: 2px;">집계 대상 페이지 선택</div>
        <div style="display: flex; flex-direction: column; gap: 4px;">
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer; background: #1e293b; padding: 4px 6px; border-radius: 3px;">
            <input type="checkbox" checked style="cursor: pointer;" />
            <span style="color: #f8fafc; font-size: 11px;">1. 원유 정제 라인</span>
          </label>
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer; background: #1e293b; padding: 4px 6px; border-radius: 3px;">
            <input type="checkbox" checked style="cursor: pointer;" />
            <span style="color: #f8fafc; font-size: 11px;">2. 에틸렌 & 폴리머</span>
          </label>
          <label style="display: flex; align-items: center; gap: 6px; cursor: pointer; background: #1e293b; padding: 4px 6px; border-radius: 3px;">
            <input type="checkbox" checked style="cursor: pointer;" />
            <span style="color: #f8fafc; font-size: 11px;">3. 대형 가스터빈 발전</span>
          </label>
        </div>
        <!-- Global Power Card -->
        <div style="margin-top: auto; background: #14171e; border: 1px solid #334155; border-radius: 4px; padding: 6px;">
          <div style="color: #fbbf24; font-size: 10px; font-weight: bold;">전역 전력 밸런스</div>
          <div style="color: #34d399; font-size: 11px; font-weight: bold;">+12,400 EU/t (순수 잉여)</div>
        </div>
      </div>
      <!-- Right Main: Filter Tabs & Material Balance List -->
      <div style="flex: 1; padding: 10px; display: flex; flex-direction: column; gap: 8px;">
        <!-- Filter Tabs & Search Box -->
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <div style="display: flex; gap: 4px;">
            <span style="background: #0284c7; color: #ffffff; padding: 2px 8px; border-radius: 3px; font-size: 10px; font-weight: bold; cursor: pointer;">전체</span>
            <span style="background: #1e293b; color: #f87171; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">부족 (2)</span>
            <span style="background: #1e293b; color: #34d399; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">잉여 (4)</span>
            <span style="background: #1e293b; color: #38bdf8; padding: 2px 6px; border-radius: 3px; font-size: 10px; cursor: pointer;">완전일치 (1)</span>
          </div>
          <input type="text" placeholder="재료 검색..." style="background: #14171e; border: 1px solid #334155; border-radius: 3px; color: #cbd5e1; padding: 2px 6px; font-size: 10px; width: 90px;" />
        </div>
        <!-- Material Balance Table -->
        <div style="display: flex; flex-direction: column; gap: 4px;">
          <!-- Item Row 1 (Deficit) -->
          <div style="background: #181d26; border: 1px solid #7f1d1d; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #ef4444;">●</span>
              <span style="color: #f8fafc; font-weight: 500;">원유 (Oil)</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="color: #f87171; font-weight: bold;">-800 mB/s (부족)</span>
              <span style="color: #64748b; font-size: 10px;">기여도 ➔</span>
            </div>
          </div>
          <!-- Item Row 2 (Surplus) -->
          <div style="background: #181d26; border: 1px solid #064e3b; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #10b981;">●</span>
              <span style="color: #f8fafc; font-weight: 500;">폴리에틸렌 (Polyethylene)</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="color: #34d399; font-weight: bold;">+120 /s (잉여)</span>
              <span style="color: #64748b; font-size: 10px;">기여도 ➔</span>
            </div>
          </div>
          <!-- Item Row 3 (Balanced) -->
          <div style="background: #181d26; border: 1px solid #0369a1; border-radius: 3px; padding: 4px 8px; display: flex; align-items: center; justify-content: space-between; cursor: pointer;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="color: #38bdf8;">●</span>
              <span style="color: #f8fafc; font-weight: 500;">에틸렌 가스</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="color: #38bdf8; font-weight: bold;">0.00 (완전 균형)</span>
              <span style="color: #64748b; font-size: 10px;">기여도 ➔</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

## 3. 항목별 세부 기여도 팝업 (`ItemContributionPopup`)

대시보드에서 특정 재료 항목을 클릭했을 때, 해당 재료가 어떤 페이지에서 생산되고 소비되는지 $O(N)$으로 분해 분석합니다.

### 3.1 `ItemContributionPopup` UI 와이어프레임

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Popup Container -->
  <div style="background-color: #1e293b; border: 1.5px solid #0284c7; border-radius: 6px; width: 320px; box-shadow: 0 8px 24px rgba(2,132,199,0.3); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #0f172a; padding: 6px 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #334155;">
      <div style="font-weight: bold; color: #38bdf8; font-size: 11px;">[에틸렌 가스] 페이지별 생산/소비 기여도</div>
      <span style="color: #94a3b8; cursor: pointer; font-size: 10px;">✕</span>
    </div>
    <!-- 2. Breakdown Rows -->
    <div style="padding: 8px; display: flex; flex-direction: column; gap: 4px;">
      <!-- Page 1 -->
      <div style="background: #14171e; border-radius: 3px; padding: 4px 6px; display: flex; justify-content: space-between;">
        <span style="color: #cbd5e1; font-size: 11px;">1. 원유 정제 라인:</span>
        <span style="color: #34d399; font-weight: bold; font-size: 11px;">+500 mB/s (생산)</span>
      </div>
      <!-- Page 2 -->
      <div style="background: #14171e; border-radius: 3px; padding: 4px 6px; display: flex; justify-content: space-between;">
        <span style="color: #cbd5e1; font-size: 11px;">2. 에틸렌 & 폴리머:</span>
        <span style="color: #f87171; font-weight: bold; font-size: 11px;">-500 mB/s (소비)</span>
      </div>
      <div style="border-top: 1px solid #334155; margin: 4px 0;"></div>
      <!-- Net Balance -->
      <div style="display: flex; justify-content: space-between; padding: 0 4px;">
        <span style="color: #94a3b8; font-size: 11px;">크로스 페이지 순수 수지:</span>
        <span style="color: #38bdf8; font-weight: bold; font-size: 11px;">0.00 mB/s (100% 매칭)</span>
      </div>
    </div>
  </div>
</div>

---

## 4. 멀티블록 구조체 자재 명세서 다이얼로그 (`MultiblockBOMDialog`)

단축키 `M` 또는 `Shift + B` (및 툴바 `[📦 BOM]` 버튼)를 통해 호출되는 자재 명세서(BOM) 다이얼로그입니다. 현재 페이지 또는 전체 페이지의 모든 멀티블록 및 단일 기계가 요구하는 구조체 블록, 코일, 해치/버스, 컨트롤러 자재를 일괄 집계합니다.

* **카테고리 탭 필터링**: `All`, `Casings`, `Coils`, `Hatches & Buses`, `Controllers`.
* **스택 단위 환산 표기**: `3 stacks + 48 (240 items)`와 같이 인벤토리 수납 단위 자동 변환.
* **듀얼 에너지 해치 토글**: `⚡ 1x Normal Energy Hatch ↔ 2x 1-Tier Lower Energy Hatches` 실시간 부품 교체.
* **다층 복합 모듈 재귀 스케일링**: 중첩된 복합 모듈 노드의 기계 대수($M_{\text{module}}$)를 재귀 곱연산하여 내부 실제 기계/멀티블록 부품 자재를 비례 집계.
* **공유 기계 풀 프레임 듀티 통합**: 공유 프레임 내 동일 물리 기계를 공유하는 공정들의 듀티 사이클 합산 및 실효 기계 대수 올림($\lceil \sum \text{Duty} \rceil$)을 BOM에 1회만 정확히 반영.
* **단일 기계 전압 티어 아이템 연역 및 출처 추적**: 단일 기계(예: Rock Breaker)의 목표 티어별 구체적 아이템(`gtceu:lv_rock_breaker`) 분기 및 기여 공정 목록(`usedByMachines`) 실시간 제공.
* **EMI 원클릭 등록 (`[★ Register in EMI]`)**: 전체 소요 자재를 EMI 가상 레시피 트리의 루트 노드로 등록하여 제작 트리 역추적 지원.
* **클립보드 내보내기 (`[📋 Copy List]`)**: 텍스트 형식으로 클립보드에 자재 목록 복사.

---

> ➡️ **다음 장으로 이동**: [[03-04] 레시피 검색 엔진, 툴바 및 튜토리얼 UI](03_04_RECIPE_SEARCH_AND_TOOLS.md)
