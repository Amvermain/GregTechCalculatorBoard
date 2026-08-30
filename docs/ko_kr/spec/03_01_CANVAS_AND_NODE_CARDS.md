# [03-01] 2D 캔버스 뷰포트 및 노드 카드 렌더링 (Canvas & Node Cards)

> 📍 **GTCalcBoard 기술 명세서 시리즈**
> [[00] 시스템 개요](00_OVERVIEW.md) ➔ [[01] 코어 도메인](01_CORE_DOMAIN_AND_MODELS.md) ➔ [[02] 수학 엔진](02_MATH_AND_ALGORITHMS.md) ➔ **[03-01] 캔버스 & 노드 카드** ➔ [[03-02] 기계 설정 & 애드온](03_02_MACHINE_CONFIG_AND_ADDONS.md) ➔ [[03-03] 페이지 결산 & 대시보드](03_03_PAGE_SUMMARY_AND_DASHBOARD.md) ➔ [[03-04] 검색 & 툴바](03_04_RECIPE_SEARCH_AND_TOOLS.md) ➔ [[04] 멀티플레이어](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] 외부 연동](05_INTEGRATION_AND_I18N.md)

---

## 1. 2D 뷰포트 좌표 변환 수학 (`BoardScreen`)

무한 2D 캔버스 평면과 마인크래프트 화면 픽셀 좌표계 간의 정밀한 변환 수식입니다.

$$\text{CanvasX} = \frac{\text{ScreenX} - \text{PanX}}{\text{Zoom}}, \quad \text{CanvasY} = \frac{\text{ScreenY} - \text{PanY}}{\text{Zoom}}$$
$$\text{ScreenX} = (\text{CanvasX} \times \text{Zoom}) + \text{PanX}, \quad \text{ScreenY} = (\text{CanvasY} \times \text{Zoom}) + \text{PanY}$$

* **줌(Zoom) 범위**: $0.25 \times \dots 2.5 \times$ (마우스 휠 스크롤 기준 지수 스케일링)
* **팬(Pan) 제어**: 마우스 우클릭 드래그 또는 휠 클릭 드래그
* **그리드 간격**: $20\text{px} \times \text{Zoom}$ 단위 도트 배경 렌더링

---

## 2. 3차 베지어 와이어 렌더링 (`ConnectionRenderer`)

출력 포트 $(x_1, y_1)$에서 입력 포트 $(x_2, y_2)$로 연결되는 와이어를 Cubic Bézier Spline으로 렌더링합니다.

### 2.1 제어점(Control Points) 및 궤적 수식
$$dx = |x_2 - x_1|, \quad \text{offset} = \max\left(40.0, \, \frac{dx}{2.0}\right)$$
$$P_0 = (x_1, y_1), \quad P_1 = (x_1 + \text{offset}, y_1), \quad P_2 = (x_2 - \text{offset}, y_2), \quad P_3 = (x_2, y_2)$$
$$B(t) = (1-t)^3 P_0 + 3(1-t)^2 t P_1 + 3(1-t) t^2 P_2 + t^3 P_3 \quad (t \in [0.0, 1.0])$$

### 2.2 와이어 상태 색상 및 24분할 히트 테스팅
* **균형 (Balanced)**: `0xFF38BDF8` (시안), 유량 흐름 펄스 애니메이션
* **결손 (Deficit)**: `0xFFEF4444` (레드), 원자재 부족 경고 점멸
* **초과 (Surplus)**: `0xFF10B981` (에메랄드), 안전 잉여

---

## 3. 고성능 렌더링 파이프라인 및 공간 분할 색인

### 3.1 Single-Pass Batch Rendering
프레임당 다수의 개별 드로우콜 및 `glClear`를 배제하고, 화면에 보이는 뷰포트 영역(Culling Box) 내의 노드와 와이어만을 선별하여 단일 지오메트리 패스로 일괄 렌더링합니다.

### 3.2 $128 \times 128$ AABB 균일 그리드 공간 분할 와이어 색인 (`WireSpatialIndex`)
1,000개 이상의 복잡한 와이어 네트워크에서 마우스 호버 및 클릭 감지를 $O(E)$ 전수 검사에서 **$O(\log E)$ 공간 분할 색인**으로 최적화합니다.

```mermaid
flowchart LR
    CUR["마우스 커서 (CanvasX, CanvasY)"] --> GRID["WireSpatialIndex (128x128 Grid Cell 해시)"]
    GRID --> CANDIDATES["반경 내 인접 셀의 후보 와이어 3~5개 선별"]
    CANDIDATES --> HIT["24분할 베지어 정밀 히트 테스팅 (<= 6.0px / Zoom)"]
    HIT --> ACTION["선택/삭제/툴팁 팝업 즉시 반응"]
```

---

## 4. 노드 카드 UI 와이어프레임 (`NodeCardRenderer`)

### 3.1 표준 기계 노드 카드 (Standard Recipe Node Card)

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Card Container -->
  <div style="background-color: #1e222b; border: 1.5px solid #3d4455; border-radius: 6px; width: 340px; box-shadow: 0 4px 12px rgba(0,0,0,0.5); overflow: hidden;">
    <!-- 1. Card Header -->
    <div style="background-color: #2a2e39; padding: 6px 8px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #353c4d;">
      <div style="display: flex; align-items: center; gap: 6px; font-weight: bold; color: #ffde59;">
        <span style="font-size: 14px;">★</span>
        <span style="color: #ffffff;">대형 화학 반응기</span>
      </div>
      <div style="display: flex; align-items: center; gap: 4px;">
        <span style="background: #222834; border: 1px solid #4a5568; border-radius: 3px; padding: 1px 5px; font-size: 10px; color: #ffeb3b; cursor: pointer;">🎯</span>
        <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
      </div>
    </div>
    <!-- Card Body -->
    <div style="padding: 8px;">
      <!-- 2. Machine Count & Mini Addons Row -->
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px;">
        <div style="display: flex; align-items: center; gap: 4px;">
          <span style="color: #8892b0; font-size: 11px;">대수:</span>
          <button style="background: #181d26; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; width: 18px; height: 18px; line-height: 14px; text-align: center; cursor: pointer; font-size: 11px;">-</button>
          <div style="background: #14171e; border: 1px solid #3d4455; border-radius: 3px; padding: 1px 6px; color: #ffffaa; font-weight: bold; font-size: 11px; text-align: center; min-width: 32px;">1.00</div>
          <button style="background: #181d26; border: 1px solid #3d4455; border-radius: 3px; color: #cbd5e1; width: 18px; height: 18px; line-height: 14px; text-align: center; cursor: pointer; font-size: 11px;">+</button>
          <button style="background: #181d26; border: 1px solid #3d4455; border-radius: 3px; color: #94a3b8; padding: 1px 4px; font-size: 10px; cursor: pointer;">/2</button>
          <button style="background: #181d26; border: 1px solid #3d4455; border-radius: 3px; color: #94a3b8; padding: 1px 4px; font-size: 10px; cursor: pointer;">x2</button>
        </div>
        <!-- Mini Addons Tray -->
        <div style="display: flex; gap: 3px;">
          <span style="background: #181d26; border: 1px solid #354054; border-radius: 3px; padding: 1px 4px; font-size: 10px; color: #e2e8f0;" title="Cupronickel Coil">🧲</span>
          <span style="background: #181d26; border: 1px solid #354054; border-radius: 3px; padding: 1px 4px; font-size: 10px; color: #38bdf8;" title="4x Parallel Control">⚡</span>
        </div>
      </div>
      <!-- 3. Tier, Overclock & Config Row -->
      <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 8px;">
        <span style="background: #334155; border: 1px solid #64748b; border-radius: 3px; padding: 1px 6px; font-size: 10px; font-weight: bold; color: #f1f5f9;">LV</span>
        <span style="background: #1e293b; border: 1px solid #475569; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #94a3b8;">표준 OC</span>
        <span style="background: #0f172a; border: 1px solid #0284c7; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #38bdf8; margin-left: auto;">⚙ 4x (+2)</span>
      </div>
      <!-- 4. Power & Timing Summary -->
      <div style="background: #14171e; border-radius: 4px; padding: 4px 6px; display: flex; justify-content: space-between; align-items: center; font-size: 11px; margin-bottom: 8px;">
        <span style="color: #fbbf24; font-weight: 500;">⚡ -480 EU/t <span style="color: #10b981; font-size: 10px;">(100%)</span></span>
        <span style="color: #38bdf8;">1.25s <span style="color: #94a3b8; font-size: 10px;">(0.80/s)</span></span>
      </div>
      <div style="border-top: 1px solid #353c4d; margin: 6px 0 8px 0;"></div>
      <!-- 5. Input & Output Sockets/Slots -->
      <div style="display: flex; justify-content: space-between; gap: 8px; font-size: 11px;">
        <!-- Left: Inputs -->
        <div style="display: flex; flex-direction: column; gap: 5px; flex: 1;">
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 8px; height: 8px; background-color: #10b981; border: 1.5px solid #34d399; border-radius: 50%; box-shadow: 0 0 4px #10b981;" title="입력 포트 (균형)"></div>
            <span style="color: #cbd5e1;">벤젠</span>
            <span style="color: #94a3b8; font-size: 10px; margin-left: auto;">100 mB/s</span>
          </div>
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 8px; height: 8px; background-color: #10b981; border: 1.5px solid #34d399; border-radius: 50%; box-shadow: 0 0 4px #10b981;" title="입력 포트 (균형)"></div>
            <span style="color: #cbd5e1;">수소</span>
            <span style="color: #94a3b8; font-size: 10px; margin-left: auto;">2.0k mB/s</span>
          </div>
        </div>
        <!-- Right: Outputs -->
        <div style="display: flex; flex-direction: column; gap: 5px; flex: 1; text-align: right;">
          <div style="display: flex; align-items: center; justify-content: flex-end; gap: 5px;">
            <span style="color: #94a3b8; font-size: 10px; margin-right: auto;">200 mB/s</span>
            <span style="color: #cbd5e1;">에틸렌</span>
            <div style="width: 8px; height: 8px; background-color: #38bdf8; border: 1.5px solid #7dd3fc; border-radius: 50%; box-shadow: 0 0 4px #38bdf8;" title="출력 포트 (균형)"></div>
          </div>
          <div style="display: flex; align-items: center; justify-content: flex-end; gap: 5px;">
            <span style="color: #94a3b8; font-size: 10px; margin-right: auto;">50 mB/s</span>
            <span style="color: #cbd5e1;">부산물</span>
            <div style="width: 8px; height: 8px; background-color: #38bdf8; border: 1.5px solid #7dd3fc; border-radius: 50%; box-shadow: 0 0 4px #38bdf8;" title="출력 포트 (균형)"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

### 3.2 복합 모듈 카드 (Compound Module Card)

<div style="background-color: #14171e; padding: 16px; border-radius: 8px; display: inline-block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e0e0e0; font-size: 12px; line-height: 1.4;">
  <!-- Module Card Container -->
  <div style="background-color: #1d172e; border: 1.5px solid #9955ff; border-radius: 6px; width: 340px; box-shadow: 0 4px 12px rgba(153,85,255,0.25); overflow: hidden;">
    <!-- 1. Header -->
    <div style="background-color: #2a1c42; padding: 6px 8px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #3d2a5e;">
      <div style="display: flex; align-items: center; gap: 6px; font-weight: bold; color: #d8b4fe;">
        <span style="font-size: 14px;">📦</span>
        <span style="color: #ffffff;">석유 정제 & 분별 증류 라인</span>
      </div>
      <div style="display: flex; align-items: center; gap: 4px;">
        <span style="background: #352055; border: 1px solid #7744aa; border-radius: 3px; padding: 1px 5px; font-size: 10px; color: #ddaaff; cursor: pointer;" title="모듈 펼치기 (Expand)">⤢</span>
        <span style="background: #222834; border: 1px solid #4a5568; border-radius: 3px; padding: 1px 5px; font-size: 10px; color: #ffeb3b; cursor: pointer;">🎯</span>
        <span style="background: rgba(255,68,68,0.25); border: 1px solid #ff4444; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #ff6b6b; cursor: pointer; font-weight: bold;">✕</span>
      </div>
    </div>
    <!-- Body -->
    <div style="padding: 8px;">
      <!-- 2. Controls & Machines Count Badge -->
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px;">
        <div style="display: flex; align-items: center; gap: 4px;">
          <span style="color: #c084fc; font-size: 11px;">스케일:</span>
          <button style="background: #2a1c42; border: 1px solid #581c87; border-radius: 3px; color: #e9d5ff; width: 18px; height: 18px; line-height: 14px; text-align: center; cursor: pointer; font-size: 11px;">-</button>
          <div style="background: #14171e; border: 1px solid #581c87; border-radius: 3px; padding: 1px 6px; color: #f5d0fe; font-weight: bold; font-size: 11px; text-align: center; min-width: 32px;">1.00</div>
          <button style="background: #2a1c42; border: 1px solid #581c87; border-radius: 3px; color: #e9d5ff; width: 18px; height: 18px; line-height: 14px; text-align: center; cursor: pointer; font-size: 11px;">+</button>
          <button style="background: #2a1c42; border: 1px solid #581c87; border-radius: 3px; color: #d8b4fe; padding: 1px 4px; font-size: 10px; cursor: pointer;">/2</button>
          <button style="background: #2a1c42; border: 1px solid #581c87; border-radius: 3px; color: #d8b4fe; padding: 1px 4px; font-size: 10px; cursor: pointer;">x2</button>
        </div>
        <span style="background: #3b0764; border: 1px solid #9333ea; border-radius: 3px; padding: 1px 6px; font-size: 10px; color: #f3e8ff; font-weight: bold;">📦 18대 기계 내장</span>
      </div>
      <!-- 3. Power & Module Tag -->
      <div style="background: #14171e; border-radius: 4px; padding: 4px 6px; display: flex; justify-content: space-between; align-items: center; font-size: 11px; margin-bottom: 8px;">
        <span style="color: #fbbf24; font-weight: 500;">⚡ -4,800 EU/t <span style="color: #c084fc; font-size: 10px;">(EV 최고 부하)</span></span>
        <span style="color: #d8b4fe; font-weight: bold;">[ 📦 복합 모듈 ]</span>
      </div>
      <div style="border-top: 1px solid #3d2a5e; margin: 6px 0 8px 0;"></div>
      <!-- 4. Promoted Boundary I/O -->
      <div style="display: flex; justify-content: space-between; gap: 8px; font-size: 11px;">
        <!-- Left: Net External Inputs -->
        <div style="display: flex; flex-direction: column; gap: 5px; flex: 1;">
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 8px; height: 8px; background-color: #10b981; border: 1.5px solid #34d399; border-radius: 50%;" title="외부 입력 포트"></div>
            <span style="color: #e2e8f0;">원유</span>
            <span style="color: #a855f7; font-size: 10px; margin-left: auto;">1.0k mB/s</span>
          </div>
        </div>
        <!-- Right: Net External Outputs -->
        <div style="display: flex; flex-direction: column; gap: 5px; flex: 1; text-align: right;">
          <div style="display: flex; align-items: center; justify-content: flex-end; gap: 5px;">
            <span style="color: #a855f7; font-size: 10px; margin-right: auto;">400 mB/s</span>
            <span style="color: #e2e8f0;">디젤</span>
            <div style="width: 8px; height: 8px; background-color: #38bdf8; border: 1.5px solid #7dd3fc; border-radius: 50%;" title="외부 출력 포트"></div>
          </div>
          <div style="display: flex; align-items: center; justify-content: flex-end; gap: 5px;">
            <span style="color: #a855f7; font-size: 10px; margin-right: auto;">200 mB/s</span>
            <span style="color: #e2e8f0;">LPG</span>
            <div style="width: 8px; height: 8px; background-color: #38bdf8; border: 1.5px solid #7dd3fc; border-radius: 50%;" title="외부 출력 포트"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

---

## 4. 페이지 탭 바 오버플로우 네비게이션 (`PageTabBarWidget`)

다수의 보드 페이지(탭)가 생성되어 화면 너비를 초과할 때 부드러운 스크롤 및 클릭 네비게이션을 제공합니다.

* **좌/우 오버플로우 인디케이터 클릭**:
  - 화면 좌측 `«` 영역 클릭 시 이전 탭 방향으로 좌측 스크롤 (`scrollX -= 80px`).
  - 화면 우측 `»` 영역 클릭 시 다음 탭 방향으로 우측 스크롤 (`scrollX += 80px`).
* **마우스 휠 스크롤 지원**: 탭 바 영역 위에서 휠 업/다운으로 빠른 가로 스크롤.
* **여백 및 시저(Scissor) 보정**: 우측 끝에 16px 패딩을 부여하여 마지막 탭 및 `[+]` 신규 탭 생성 버튼이 `»` 화살표에 가려지지 않고 깔끔하게 렌더링되도록 보정.

---

> ➡️ **다음 장으로 이동**: [[03-02] 기계 상세 설정 및 애드온 랙 UI](03_02_MACHINE_CONFIG_AND_ADDONS.md)
