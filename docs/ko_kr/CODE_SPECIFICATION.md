# GregTech Calculator Board (GTCalcBoard) 세부 기술 명세서 시리즈

본 문서는 **GregTech Calculator Board (GTCalcBoard)**의 내부 아키텍처, 핵심 수학 엔진, 5대 그래프 알고리즘, 렌더링 파이프라인, UI 컴포넌트 와이어프레임, 멀티플레이어 동시성 락 프로토콜 및 영속화 스키마를 체계적으로 정리한 공식 개발자 기술 명세서(Master Index)입니다.

---

## 📌 문서 메타데이터 (Document Metadata)

| 항목 | 내용 |
| :--- | :--- |
| **문서 버전** | `2.0.0` (기준 커밋: `6c63984`) |
| **대상 플랫폼** | Minecraft 1.20.1 (Minecraft Forge 47.2.0+) |
| **의존성** | Java 17+, GregTech CEu Modern, EMI (Recipe Viewer) |
| **소프트 의존성** | FTB Teams (멀티플레이 팀 연동) |
| **작성 언어** | 한국어 (Korean) |

---

## 📑 전체 명세서 순서별 목차 (Table of Contents)

```
docs/spec/
├── [00] 시스템 아키텍처 개요 및 설계 원칙 ────────► 00_OVERVIEW.md
├── [01] 코어 도메인 모델 및 수용능력 매트릭스 ──────► 01_CORE_DOMAIN_AND_MODELS.md
├── [02] 수학적 연산 엔진 및 그래프 해석 알고리즘 ─────► 02_MATH_AND_ALGORITHMS.md
├── [03] UI 및 캔버스 렌더링 파이프라인 개요 ────────► 03_UI_AND_RENDERING_PIPELINE.md
│   ├── [03-01] 2D 캔버스 & 노드 카드 렌더링 ───────► 03_01_CANVAS_AND_NODE_CARDS.md
│   ├── [03-02] 기계 상세 설정 & 애드온 랙 UI ──────► 03_02_MACHINE_CONFIG_AND_ADDONS.md
│   ├── [03-03] 페이지 결산 & 전역 밸런스 대시보드 ──► 03_03_PAGE_SUMMARY_AND_DASHBOARD.md
│   └── [03-04] 레시피 검색 엔진 & 툴바/튜토리얼 ────► 03_04_RECIPE_SEARCH_AND_TOOLS.md
├── [04] 멀티플레이어 동시성 제어 및 네트워크 ────────► 04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md
└── [05] 외부 모드 연동 및 다국어 단위 시스템 ────────► 05_INTEGRATION_AND_I18N.md
```

---

## 📖 챕터별 핵심 내용 요약 및 바로가기

### 1. [[00] 시스템 아키텍처 개요 및 설계 원칙](spec/00_OVERVIEW.md)
* **비전 및 가치**: 인게임 완결성, 수학적 무결성, 무마찰 멀티플레이어 협업
* **4계층 아키텍처**: 프레젠테이션(UI), 코어 도메인/수학(Core), 멀티플레이어/영속화(Net/Server), 외부 통합(Integration) 계층 분리
* **헤드리스(Headless) 환경 독립성**: 순수 JVM 및 JUnit 단위 테스트 100% 독립 동작 보장

### 2. [[01] 코어 도메인 모델 및 결정론적 수용능력 매트릭스](spec/01_CORE_DOMAIN_AND_MODELS.md)
* **핵심 데이터 모델**: `GTVoltageTier`(15단계 티어), `IngredientStack`, `RecipeNode`, `FlowGraph`, `BoardPage`
* **결정론적 수용능력 매트릭스 (`CategoryCapabilityMatrix`)**: RFC-V2-005 기반 사전 베이킹 및 $O(1)$ 연역적 메타데이터 캐시
* **도메인 헬퍼 & 복합 모듈**: `CoilHelper`, `FlowGraphModuleHandler`(`Ctrl+G`), `BlueprintCodec`, `HistoryManager`

### 3. [[02] 수학적 연산 엔진 및 그래프 해석 알고리즘](spec/02_MATH_AND_ALGORITHMS.md)
* **오버클럭 수학 공식**: 표준/퍼펙트/무손실 속도 및 전력 계수, $1.0\text{ tick}$ 미만 서브틱 배치 승격 및 CPS 공식
* **그래프 해석기 5대 핵심 알고리즘 (`FlowGraphSolver`)**: 10-Pass Fixed-Point Bottleneck Relaxation, PortFlowStats, Dual-Pass Auto-Ratio, Net Balance, Shift-Drag 1:1 Match

### 4. [[03] UI 및 캔버스 렌더링 파이프라인 개요](spec/03_UI_AND_RENDERING_PIPELINE.md)
* **[[03-01] 2D 캔버스 & 노드 카드](spec/03_01_CANVAS_AND_NODE_CARDS.md)**: 2D 뷰포트 좌표 변환 수학, 3차 베지어 와이어 렌더링, 표준 및 복합 모듈 노드 카드 HTML 와이어프레임.
* **[[03-02] 기계 상세 설정 & 애드온 랙 UI](spec/03_02_MACHINE_CONFIG_AND_ADDONS.md)**: 병렬 수치 입력기, 퀵 프리셋, 장착 애드온 트레이, 코일/터빈/써멀/해치 카탈로그 브라우저 HTML 와이어프레임.
* **[[03-03] 페이지 결산 & 전역 밸런스 대시보드](spec/03_03_PAGE_SUMMARY_AND_DASHBOARD.md)**: 현재 페이지 실시간 결산 오버레이(`SummaryOverlay`), 다중 페이지 종합 수지 대시보드(`GlobalBalanceDashboardDialog`), 원자재/부산물 세부 기여도 팝업(`ItemContributionPopup`) HTML 와이어프레임.
* **[[03-04] 검색, 필터, HUD, 가이드 & 토스트](spec/03_04_RECIPE_SEARCH_AND_TOOLS.md)**: 파라메트릭 비동기 레시피 검색창, 카테고리 필터링 모달(`RecipeFilterDialog`), 상단/하단 탭바 및 툴바, 단축키 HUD 위젯(`HotkeyHudWidget`), 인게임 가이드북(`GuideDialog`), 전역 액션 토스트(`BoardToast`), 인터랙티브 온보딩 튜토리얼 HTML 와이어프레임.

### 5. [[04] 멀티플레이어 동시성 제어 및 네트워크 프로토콜](spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)
* **네트워크 파이프라인**: Forge `SimpleChannel` C2S 6종 / S2C 4종 패킷 규격
* **분산 동시성 제어 (`WorkspaceLockManager`)**: 임차권(Lease 5분) 소프트 락, 30초 하트비트, 낙관적 Revision 충돌 방지
* **서버 영속화 스키마 (`TeamBoardSavedData`)**: `<world>/data/gtcalcboard_workspaces.dat` NBT 태그 구조 및 `ITeamProvider`

### 6. [[05] 외부 모드 연동 및 다국어 단위 시스템](spec/05_INTEGRATION_AND_I18N.md)
* **모드 호환성 어댑터 시스템 (`com.gtceu.calcboard.compat`)**: SPI 우선순위 라우팅, 모드별 3단 분리 서브패키지(`gtceu`, `create`, `thermal`, `systeams`, `vanilla`), 동적 크롤러 오케스트레이션
* **EMI 레시피 뷰어 연동**: `CalcBoardEmiPlugin` 라이프사이클 훅, 매트릭스 비동기 베이킹 트리거, `EmiRecipeConverter`
* **시간 단위 환산 및 포맷팅**: `RateTimeUnit` (/t, /s, /min, /h, /d), `FormatUtil` 전력 접두사, 다국어 동기화

---

## 🛠️ 개발 및 검증 가이드
본 명세서의 코어 알고리즘 및 UI 로직을 수정한 경우, 반드시 `./gradlew test`를 실행하여 100% 단위 테스트 통과를 확인해야 합니다.
