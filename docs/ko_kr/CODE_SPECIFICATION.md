# GregTech Calculator Board (GTCalcBoard) 세부 기술 명세서 시리즈

본 문서는 **GregTech Calculator Board (GTCalcBoard)**의 내부 아키텍처, 핵심 수학 엔진, 5대 그래프 알고리즘, 폐루프 질량 보존 가우스-요르단 선형 솔버, 렌더링 파이프라인, UI 컴포넌트 와이어프레임, 2계층 온디맨드 멀티플레이어 스트리밍 프로토콜 및 영속화 스키마를 체계적으로 정리한 공식 개발자 기술 명세서(Master Index)입니다.

---

## 📌 문서 메타데이터 (Document Metadata)

| 항목 | 내용 |
| :--- | :--- |
| **문서 버전** | `v2.0.0` (ADR-001 ~ ADR-004 정합 완료) |
| **대상 플랫폼** | Minecraft 1.20.1 (Minecraft Forge 47.2.0+) |
| **의존성** | Java 17+, GregTech CEu Modern, EMI / JEI (Recipe Viewer) |
| **소프트 의존성** | FTB Teams, Phoenix Guilds (멀티플레이 팀 연동) |
| **작성 언어** | 한국어 (Korean) |
| **ADR 색인** | **[아키텍처 결정 기록 보관소 (docs/adr/)](../adr/README.md)** |

---

## 📑 전체 명세서 순서별 목차 (Table of Contents)

```
docs/ko_kr/
├── CODE_SPECIFICATION.md                    # 📑 [한국어 마스터 인덱스]
├── TEST_CHECKLIST.md                        # 🧪 [한국어 종합 QA 체크리스트]
├── ARCHITECTURE.md                          # 🏛️ [한국어 아키텍처 가이드]
└── spec/
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
* **5계층 아키텍처**: UI, Core, Compat SPI, Server/Network, Integration SPI 계층 분리
* **헤드리스(Headless) 환경 독립성**: 순수 JVM 및 JUnit 단위 테스트 100% 독립 동작 보장

### 2. [[01] 코어 도메인 모델 및 결정론적 수용능력 매트릭스](spec/01_CORE_DOMAIN_AND_MODELS.md)
* **핵심 데이터 모델**: `GTVoltageTier`(15단계 티어), `IngredientStack`, `RecipeNode`(순수 도메인 엔티티), `FlowGraph`(불변 뷰 캡슐화)
* **결정론적 수용능력 매트릭스 (`CategoryCapabilityMatrix`)**: 사전 베이킹 및 $O(1)$ 연역적 메타데이터 캐시
* **도메인 헬퍼 & 복합 모듈**: `FlowGraphModuleHandler`(`Ctrl+G`), `BlueprintCodec`, `HistoryManager`

### 3. [[02] 수학적 연산 엔진 및 그래프 해석 알고리즘](spec/02_MATH_AND_ALGORITHMS.md)
* **오버클럭 수학 공식**: 표준/퍼펙트/무손실 속도 및 전력 계수, $1.0\text{ tick}$ 미만 서브틱 배치 승격 및 CPS 공식
* **폐루프 질량 보존 가우스-요르단 솔버 (`MassBalanceSolver`)**: 부분 피보팅 기반 선형 연립방정식 해 ($A\mathbf{x}=\mathbf{b}$) 도출
* **그래프 해석기 5대 핵심 알고리즘 (`FlowGraphSolver`)**: 10-Pass Fixed-Point Bottleneck Relaxation, PortFlowStats, Dual-Pass Auto-Ratio, Net Balance, Shift-Drag 1:1 Match

### 4. [[03] UI 및 캔버스 렌더링 파이프라인 개요](spec/03_UI_AND_RENDERING_PIPELINE.md)
* **[[03-01] 2D 캔버스 & 노드 카드](spec/03_01_CANVAS_AND_NODE_CARDS.md)**: 2D 뷰포트 좌표 변환 수학, Single-Pass Batch Render, `WireSpatialIndex` AABB 그리드 공간 분할, 3차 베지어 와이어 렌더링.
* **[[03-02] 기계 상세 설정 & 애드온 랙 UI](spec/03_02_MACHINE_CONFIG_AND_ADDONS.md)**: 병렬 수치 입력기, 퀵 프리셋, 장착 애드온 트레이, 코일/터빈/써멀/해치 카탈로그 브라우저.
* **[[03-03] 페이지 결산 & 전역 밸런스 대시보드](spec/03_03_PAGE_SUMMARY_AND_DASHBOARD.md)**: 현재 페이지 실시간 결산 오버레이(`SummaryOverlay`), 다중 페이지 종합 수지 대시보드(`GlobalBalanceDashboardDialog`), 원자재/부산물 세부 기여도 팝업(`ItemContributionPopup`).
* **[[03-04] 검색, 필터, HUD, 가이드 & 토스트](spec/03_04_RECIPE_SEARCH_AND_TOOLS.md)**: 파라메트릭 비동기 레시피 검색창, 카테고리 필터링 모달(`RecipeFilterDialog`), 상단/하단 탭바 및 툴바, 단축키 HUD 위젯, 인게임 가이드북, 전역 액션 토스트, 인터랙티브 온보딩 튜토리얼.

### 5. [[04] 멀티플레이어 동시성 제어 및 네트워크 프로토콜](spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)
* **네트워크 파이프라인**: Forge `SimpleChannel` C2S 8종 / S2C 9종 패킷 규격
* **2계층 온디맨드 페이징 & 512KB 청킹 스트리밍**: 대용량 NBT 분할 전송 및 Netty 2MB 오버플로우 방지
* **분산 임차권 락 & 낙관적 Revision 검증**: `WorkspaceLockManager` 및 `TeamBoardSavedData` NBT 영속화

### 6. [[05] 외부 모드 연동 및 다국어 단위 시스템](spec/05_INTEGRATION_AND_I18N.md)
* **통합 레시피 뷰어 SPI**: EMI, JEI, Vanilla 런타임 우선순위 선출
* **7대 모드 어댑터**: GTCEu (physics 분리), Create, Create New Age, Thermal Series, Systeams, Star Technology, Vanilla
* **결정론적 3단계 연역 원칙 (Rule 5)**: API/리플렉션, 물리 시뮬레이션, NBT/TagKey 직접 검사
