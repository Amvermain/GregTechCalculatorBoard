# 아키텍처 결정 기록 (Architecture Decision Records, ADRs)

> 🌐 **Language / 언어**: [English](README_EN.md) | **한국어**

본 디렉터리는 **GregTech Calculator Board (GTCalcBoard)** 프로젝트의 핵심 기술적 의사결정 맥락(Context), 채택 이유(Why), 시스템 구조(Architecture), 그리고 결과 및 파급 효과(Consequences)를 영구히 기록하고 보존하는 **공식 아키텍처 결정 기록(ADR) 레지스트리**입니다.

모든 핵심 아키텍처 결정은 구현 완료 후 공식 시스템 사양서([`docs/ko_kr/spec/`](../ko_kr/spec/))로 체계화되어 반영되며, 본 문서는 58개 ADR 전체의 생명주기 및 공식 사양서 연계 맵을 제공합니다.

---

## 🏛 ADR 생명주기 및 상태 분류 (Lifecycle & Status)

```mermaid
stateDiagram-v2
    [*] --> PROPOSED : docs/rfc/ 제안 기안
    PROPOSED --> ACCEPTED : 리뷰 및 승인
    PROPOSED --> REJECTED : 기각 (결번 보존)
    ACCEPTED --> ACTIVE : 구현 완료 및 활성 아키텍처 적용
    ACTIVE --> SUPERSEDED : 후속 ADR로 대체/확장
    ACTIVE --> CONSOLIDATED : 공식 사양서 흡수 및 아카이브
```

| 상태 (Status) | 건수 | 설명 |
| :--- | :---: | :--- |
| 🟢 **`Active`** | **21건** | 현재 시스템의 구조, 인터페이스 및 동작을 직접 규정하는 활성 아키텍처 결정 |
| 🔄 **`Superseded`** | **5건** | 후속 ADR에 의해 설계, 모델 또는 알고리즘이 대체된 결정 |
| 📦 **`Retired / Consolidated`** | **31건** | 구현 완료 후 공식 시스템 사양서([`docs/ko_kr/spec/`](../ko_kr/spec/))에 완전히 통합·체계화된 결정 |
| ❌ **`Rejected`** | **1건** | 기술 검토 단계에서 기각되어 결번으로 영구 보존된 제안 (`RFC-046`) |
| **합계** | **58건** | 전체 등록 아키텍처 결정 및 결번 레코드 총합 |

---

## 🗺 핵심 서브시스템 진화 계보도 (Core Architecture Evolution Lineage)

### 1. 유량 솔버 및 수학 알고리즘 발전사 (Flow Solver & Mathematics)

```mermaid
flowchart TD
    ADR012["ADR-012<br/>기본 유량 모델 & 격자 스냅"] --> ADR014["ADR-014<br/>포트 플로우 O(1) 캐싱"]
    ADR014 --> ADR018["ADR-018<br/>와이어 듀티 변조 & 병목 시각화"]
    ADR014 --> ADR019["ADR-019<br/>보이드 싱크 유량 차감"]
    ADR018 --> ADR020["ADR-020<br/>고속 배치 & 정션 완충"]
    ADR020 --> ADR022["ADR-022<br/>Tarjan SCC 폐순환 자급 보호"]
    ADR022 --> ADR023["ADR-023<br/>1회(배치) 뷰 모드"]
    ADR022 --> ADR024["ADR-024 (대체됨)<br/>BFS Auto-Ratio"]
    ADR024 -.->|대체| ADR035["ADR-035<br/>2단계 선형 연립방정식 솔버"]
    ADR022 --> ADR032["ADR-032 (대체됨)<br/>결손 루프 발산 감지"]
    ADR032 -.->|대체| ADR033["ADR-033<br/>7대 공정 종합 발산 방어"]
    ADR020 --> ADR034["ADR-034<br/>정션 완충 배선 & 앵커"]
    ADR035 --> ADR041["ADR-041<br/>정션 균등/우선순위 계층 분배"]
    ADR034 --> ADR041
    ADR041 --> ADR044["ADR-044<br/>감쇠 순환 등비급수 해석적 수렴"]
    ADR035 --> ADR054["ADR-054<br/>솔버 제어 흐름 평탄화"]
    ADR044 --> ADR057["ADR-057<br/>TFG 대형 보일러 비선형 물리"]
    ADR035 --> ADR058["ADR-058<br/>솔버 음수 인덱스 방어"]
```

### 2. 캔버스 GUI & 인터랙션 발전사 (Canvas GUI & Interaction)

```mermaid
flowchart TD
    ADR007["ADR-007<br/>폴더블 페이지 탐색기"] --> ADR016["ADR-016<br/>BoardScreen 4대 서브시스템"]
    ADR016 --> ADR017["ADR-017<br/>독립 가상 뷰포트 배율"]
    ADR016 --> ADR025["ADR-025<br/>3-패널 통합 워크스페이스"]
    ADR025 --> ADR026["ADR-026<br/>LIFO 모달 다이얼로그 스택"]
    ADR025 --> ADR027["ADR-027<br/>CanvasInteractionState FSM"]
    ADR025 --> ADR030["ADR-030<br/>NodeLayoutBounds 히트박스"]
    ADR027 --> ADR039["ADR-039<br/>렌더링 최적화 & 정밀 캐시 무효화"]
    ADR025 --> ADR043["ADR-043<br/>1:1 전용 서브페이지 모듈 & 경계 핀"]
    ADR025 --> ADR048["ADR-048<br/>페이지 목표 전압 자동 프로비저닝"]
    ADR048 --> ADR049["ADR-049<br/>하드웨어 정합성 조정자 & UI 동기화"]
    ADR025 --> ADR053["ADR-053<br/>NodeInspectorPanel SRP 4대 분해"]
    ADR005["ADR-005 (대체됨)<br/>11단계 단일 튜토리얼"] -.->|대체| ADR056["ADR-056<br/>3-트랙 모듈형 아카데미 & 맥락 넛지"]
    ADR027 --> ADR058["ADR-058<br/>children 방어 복사 & 모달 핫키 격리"]
```

### 3. 도메인 모델, 확장성 & 네트워크 발전사 (Domain Models, SPI & Networking)

```mermaid
flowchart TD
    ADR001["ADR-001 / ADR-009<br/>데디케이티드 서버 계층 격리"] --> ADR004["ADR-004 (대체됨)<br/>4대 하위 클래스 분해"]
    ADR004 --> ADR010["ADR-010<br/>리플렉션 정적 캐싱"]
    ADR004 --> ADR011["ADR-011<br/>제어 흐름 평탄화"]
    ADR004 --> ADR029["ADR-029<br/>IModAdapter ISP & 확장 객체"]
    ADR029 --> ADR037["ADR-037<br/>api.spi 이전 & 도메인 순수성"]
    ADR037 --> ADR038["ADR-038<br/>부수효과 차단 & 시뮬레이션 순수성"]
    ADR037 --> ADR040["ADR-040<br/>동시성 안전화 & 갓 클래스 분해"]
    ADR037 --> ADR047["ADR-047<br/>Rule 5 결정론적 정규화 & 완전 일치"]
    ADR004 -.->|대체| ADR045["ADR-045<br/>RecipeNode INodeRole 컴포지션"]
    ADR045 --> ADR050["ADR-050<br/>불변 RecipeSpec & 지연 포트 투영"]
    ADR045 --> ADR055["ADR-055<br/>RecipeNode 직접 복제 생성자 최적화"]
    ADR031["ADR-031 (대체됨)<br/>공유 기계 풀 단순 스케일링"] -.->|대체| ADR042["ADR-042<br/>공유 풀 인플레이스 접기 & 비율 보존"]
    ADR003["ADR-003<br/>512KB C2S/S2C 분할 스트리밍"] --> ADR051["ADR-051<br/>api.team 모델 분리 & 계층 역전 해소"]
    ADR003 --> ADR052["ADR-052<br/>128청크/64MB DoS 방어 가드"]
```

---

## 🟢 활성 아키텍처 결정 레지스트리 (Active ADRs - 21건)

현재 시스템의 핵심 불변식, 데이터 구조, 연산 알고리즘 및 인터랙션을 직접 규정하는 활성 결정 목록입니다.

| 번호 | 문서 제목 (Title) | 대상 버전 | 주관 계층 | 연관 공식 사양서 | 핵심 결정 요약 |
| :---: | :--- | :---: | :--- | :--- | :--- |
| **[ADR-037](ADR_037_DOMAIN_PURITY_AND_DETERMINISTIC_DEDUCTION_REFACTORING.md)** | 도메인 엔티티 순수성 회복, 역방향 의존성 격리 및 결정론적 스펙 연역 무결성 개편 명세 | `v2.2.0-beta.2` | Core Domain (`api.spi`) | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | `IModAdapter`를 `api.spi`로 이전하여 API ➔ Compat 역참조 해소, `RecipeNode` 모드 특화 필드 제거 |
| **[ADR-038](ADR_038_LEGACY_CALCULATION_ALGORITHM_AND_SIMULATION_PURITY_REFACTORING.md)** | 레거시 계산 알고리즘 및 물리 시뮬레이션 순수성 개편 명세 | `v2.2.0-beta.2` | Solver & Physics (`api.solver`) | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 연산 중 포트 가변 조작 및 부수 효과 차단, UI 렌더링 중 캐시 무효화 격리 |
| **[ADR-039](ADR_039_RENDERING_LIFECYCLE_AND_PRECISION_CACHE_INVALIDATION.md)** | 렌더링 생명주기 최적화, 정밀 캐시 무효화 및 그래프 탐색 알고리즘 개편 명세 | `v2.2.0-beta.2` | Client GUI (`client.gui.*`) | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | 스티키 노트/프레임 이동 시 국소 바운즈만 갱신, 뷰어 포커스 리플렉션 캐싱 |
| **[ADR-040](ADR_040_RUNTIME_CONCURRENCY_REFLECTION_AND_GOD_CLASS_DECOMPOSITION.md)** | 런타임 동시성 무결성, 리플렉션 정적 최적화 및 갓 클래스 모듈화 명세 | `v2.2.0-beta.3` | Core & Compat (`api.*`, `compat.*`) | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 카탈로그 스레드 안전화, 텍스트 캐시 리로드 훅, 251개 리플렉션 캐싱, 7대 갓 클래스 분해 |
| **[ADR-041](ADR_041_JUNCTION_EQUAL_AND_PRIORITY_SPLITTING.md)** | 정션 노드 균등 분할 및 AE2 스타일 우선순위 유량 분배 시스템 명세 | `v2.2.0-beta.2` | Solver & Model (`api.model`, `api.solver`) | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 정션 노드 균등 분할(`1/N`) 및 선로 정수 우선순위(`priority`) 기반 계층 연쇄 유량 분배 |
| **[ADR-042](ADR_042_SHARED_MACHINE_POOL_IN_PLACE_FOLDING_AND_RATIO_PRESERVATION.md)** | 공유 기계 풀 비파괴 인플레이스 접기 및 비율 보존형 가상 머신 카드 명세 | `v2.2.0-beta.3` | Domain & GUI (`api.model`, `client.gui`) | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 토폴로지 비파괴형 프레임 접기, 가상 머신 카드 축소, 내부 레시피 비율 보존 스케일링 |
| **[ADR-043](ADR_043_DEDICATED_SUBPAGE_COMPOSITE_MODULE_AND_BOUNDARY_IO.md)** | 전용 서브페이지 기반 복합 공정 모듈 및 경계 I/O 핀 규격화 명세 | `v2.2.0-beta.3` | Domain & Storage (`api.model`, `api.storage`) | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 1:1 전용 서브페이지(`PageType.MODULE`) 격리, 더블클릭 내비게이션, 경계 I/O 핀 인터페이스 계약 |
| **[ADR-044](ADR_044_DAMPED_RECIRCULATION_LOOP_SOLVER_AND_STEADY_STATE_VISUALIZATION.md)** | 감쇠 순환 공정의 닫힌 형태 해석적 수렴 및 정상 상태 시각화 명세 | `v2.2.0-beta.3` | Solver & UI (`api.solver`, `client.gui`) | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 무한 등비급수 해석적 수렴($S = \frac{S_{\text{ext}}}{1-r}$), 정상 상태 가동 인디케이터 연동 |
| **[ADR-045](ADR_045_RECIPE_NODE_COMPOSITION_DECOMPOSITION.md)** | RecipeNode 역할 컴포지션 분해 및 불변 계산 스냅샷 아키텍처 | `v2.2.1` | Core Domain (`api.model.role`) | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | `RecipeNode` 슬림화, 4대 역할(`Machine`, `Module`, `Junction`, `BoundaryPin`) `INodeRole` 컴포지션 분해 |
| **[ADR-047](ADR_047_COMPAT_DETERMINISTIC_EXACT_MATCH_NORMALIZATION.md)** | 외부 모드 호환 계층 레거시 폴백 제거 및 Rule 5 결정론적 정규화 | `v2.2.1` | Compat SPI (`compat.*`) | [05. 외부 연동](../ko_kr/spec/05_INTEGRATION_AND_I18N.md) | `contains` 문자열 휴리스틱 완전 제거, 완전 일치 매핑 테이블 및 강타입 검사 전환 |
| **[ADR-048](ADR_048_PAGE_TARGET_VOLTAGE_AND_MULTIBLOCK_ENERGY_HATCH_PROVISIONING.md)** | 페이지별 목표 전압 티어 및 멀티블록 에너지 해치 자동 프로비저닝 명세 | `v2.2.1` | UI & Domain (`client.gui`, `api.storage`) | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | 페이지 단위 목표 전압(`defaultVoltageTier`), 노드 배치 시 자동 오버클록 및 에너지 해치 장착 |
| **[ADR-049](ADR_049_MACHINE_RECIPE_TRANSITION_RECONCILER_AND_UI_SYNC.md)** | 기계 및 레시피 변경 시 하드웨어 정합성 조정자 및 반응형 UI 동기화 명세 | `v2.2.1` | Domain & UI (`api.model`, `client.gui`) | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | 전환 멱등성 보정(`NodeHardwareReconciler`), 완전한 하드웨어 메멘토, 다이얼로그 `rebindUI` 동기화 |
| **[ADR-050](ADR_050_IMMUTABLE_RECIPE_SPEC_AND_DYNAMIC_PORT_PROJECTION.md)** | 불변 레시피 명세 및 동적 하드웨어 포트 프로젝션 아키텍처 명세 | `v2.2.1` | Core Domain (`api.model`, `api.spi`) | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 불변 `RecipeSpec` 도입, Core/Auxiliary 포트 정체성 분리 및 `IPortProjectionProvider` 지연 투영 |
| **[ADR-051](ADR_051_CLIENT_SERVER_LAYER_INVERSION_RESOLUTION.md)** | 팀 워크스페이스 공통 모델 분리 및 Client ➔ Server 계층 역전 해소 명세 | `v2.3.0` | Domain & Network (`api.team`, `client.team`) | [04. 멀티플레이어](../ko_kr/spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) | `TeamWorkspacePage` 및 `CommitLogEntry`를 `api.team`으로 이전하여 UI 5개 클래스 계층 역전 해소 |
| **[ADR-052](ADR_052_CHUNKED_PAYLOAD_DOS_DEFENSE_GUARD.md)** | 청크 페이로드 수신 상한 가드 및 서버 메모리 보호 명세 | `v2.3.0` | Server & Storage (`server.storage`) | [04. 멀티플레이어](../ko_kr/spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) | `ServerChunkedPayloadAssembler`에 128청크(64MB) 상한선 및 인덱스/크기 가드 추가 |
| **[ADR-053](ADR_053_NODE_INSPECTOR_PANEL_SRP_DECOMPOSITION.md)** | NodeInspectorPanel 단일 책임 원칙(SRP) 기반 4대 서브 컴포넌트 분해 명세 | `v2.3.0` | Client UI (`client.gui.inspector`) | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | 1,199라인 대형 패널을 `Machine`, `Junction`, `BoundaryPin`, `PageSettings` 서브 인스펙터로 SRP 분해 |
| **[ADR-054](ADR_054_CONTROL_FLOW_FLATTENING_AND_RULE1_COMPLIANCE.md)** | 솔버 및 뷰어 어댑터 핵심 모듈 제어 흐름 평탄화 및 Rule 1 준수 명세 | `v2.3.0` | Solver & Viewer (`api.solver`, `integration`) | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | `FlowSummaryAggregator`, `MassBalanceSolver` 등 핵심 연산 클래스 중첩 평탄화 및 조기 반환 적용 |
| **[ADR-055](ADR_055_RECIPE_NODE_COPY_CONSTRUCTOR_OPTIMIZATION.md)** | RecipeNode 직접 복제 생성자 도입 및 NBT 왕복 오버헤드 제거 명세 | `v2.3.0` | Core Domain (`api.model`, `api.storage`) | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | `RecipeNode.copy()` 및 클립보드 직렬화 왕복을 직접 메모리 복제 생성자로 전환 |
| **[ADR-056](ADR_056_MODULAR_ACADEMY_AND_CONTEXTUAL_TUTORIAL_ARCHITECTURE.md)** | 모듈형 아카데미 및 맥락형 튜토리얼 아키텍처 개편 명세 | `v2.3.0` | Client UI (`client.gui.tutorial`) | [03-04. 검색 & 도구](../ko_kr/spec/03_04_RECIPE_SEARCH_AND_TOOLS.md) | 3-트랙 온보딩(45초 스타터, 4대 아카데미 챕터, 맥락 넛지) 도입 및 누락 핵심 기능 통합 |
| **[ADR-057](ADR_057_TFG_LARGE_BOILER_BOOSTER_MECHANISM.md)** | TFG 대형 보일러 부스터 메커니즘 및 비선형 물리 모델 명세 | `v2.3.0` | Compat Physics (`compat.tfg`) | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | TFG 대형 보일러(LBB 480PU, LSB 1280PU) 9종 부스터, 수질 계층(1.5x), 480PU 초과 비선형 물/연료 곡선, Super Boiler |
| **[ADR-058](ADR_058_CANVAS_INTERACTION_AND_SOLVER_DEFENSIVE_STABILITY.md)** | 캔버스 인터랙션 생명주기 및 유량 솔버 방어적 안정성 명세 | `v2.3.0` | Client GUI & Solver (`client.gui`, `api.solver`) | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | `children()` 방어 복사본 반환, 유량 솔버 음수 인덱스 가드, 모달 활성 시 핫키 차단, `MachineNodeRole` NBT 정규화 |

---

## 🔄 대체된 결정 레지스트리 (Superseded Decisions - 5건)

시스템 요구사항 확장 및 아키텍처 고도화에 따라 후속 ADR에 의해 설계나 알고리즘이 대체된 결정 목록입니다.

| 번호 | 문서 제목 (Title) | 대상 버전 | 대체 ADR | 사양서 매핑 | 대체 사유 및 핵심 변경점 |
| :---: | :--- | :---: | :---: | :--- | :--- |
| **[ADR-004](ADR_004_CLEAN_ARCHITECTURE_AND_DOMAIN_DECOMPOSITION.md)** | 클린 아키텍처 정립 및 잔여 갓 클래스 책임 분해 | `v2.0.0` | **[ADR-045](ADR_045_RECIPE_NODE_COMPOSITION_DECOMPOSITION.md)** | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | `RecipeNode`의 4대 하위 클래스 상속/분기 구조를 `INodeRole` 인터페이스 기반 런타임 객체 컴포지션 구조로 전면 재편 |
| **[ADR-005](ADR_005_MULTIBLOCK_SELECTOR_TUTORIAL_INTEGRATION.md)** | 기계 및 멀티블록 선택 튜토리얼 통합 사양 | `v2.1.0-alpha.3` | **[ADR-056](ADR_056_MODULAR_ACADEMY_AND_CONTEXTUAL_TUTORIAL_ARCHITECTURE.md)** | [03-04. 검색 & 도구](../ko_kr/spec/03_04_RECIPE_SEARCH_AND_TOOLS.md) | 11단계 단일 일직선(Linear) 튜토리얼을 45초 Fast-Track 스타터, 4대 독립 아카데미 챕터 및 인게임 맥락형 넛지 체계로 전면 개편 |
| **[ADR-024](ADR_024_TARGET_OUTPUT_RATE_AND_FRACTIONAL_AUTO_RATIO.md)** | 목표 생산량 기반 기계 대수 자동 역산 및 정밀 소수점 Auto-Ratio 명세 | `v2.2.0-alpha.2` | **[ADR-035](ADR_035_TWO_STAGE_LINEAR_FLOW_SOLVER.md)** | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | DAG 전제 BFS 탐색 및 다회 클릭 수렴 방식의 Auto-Ratio를 2단계 선형 연립방정식(가우스-요르단) 단일 패스 결정론적 솔버로 전면 개편 |
| **[ADR-031](ADR_031_SHARED_MACHINE_POOL_AUTO_RATIO.md)** | 공유 기계 풀(Shared Machine Pool) 용량 기반 자동 비율 맞춤 명세 | `v2.2.0-alpha.4` | **[ADR-042](ADR_042_SHARED_MACHINE_POOL_IN_PLACE_FOLDING_AND_RATIO_PRESERVATION.md)** | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 단순 프레임 비율 스케일링 모델에서 토폴로지 비파괴 인플레이스 접기 및 내부 레시피 비율 보존형 가상 머신 카드 모델로 확장 |
| **[ADR-032](ADR_032_AUTO_RATIO_DIVERGENCE_ALERT_AND_GUIDANCE.md)** | 자동 비율 맞춤 폐순환 루프 발산 방어, 경고 뱃지 및 액션 가이드 툴팁 | `v2.2.0-alpha.4` | **[ADR-033](ADR_033_COMPREHENSIVE_DIVERGENCE_DEFENSE_MATRIX.md)** | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 2-노드 결손 루프 국소 방어 체계를 증식 루프, 촉매 감쇠, 8자 루프, 앵커 충돌 등 7대 발산 시나리오 포괄적 방어 매트릭스로 통합 흡수 |

---

## 📦 통합·보관된 결정 레지스트리 (Retired / Consolidated ADRs - 31건)

구현 완료 후 공식 시스템 사양서([`docs/ko_kr/spec/`](../ko_kr/spec/))의 해당 장으로 모든 기술 규격과 아키텍처 계약이 완전히 통합되어 상시 참조 사양으로 유지 관리되는 결정 목록입니다.

| 번호 | 문서 제목 (Title) | 적용 버전 | 연관 공식 사양서 | 핵심 요약 |
| :---: | :--- | :---: | :--- | :--- |
| **[ADR-001](ADR_001_COMPAT_GUI_HANDLER_ISOLATION_AND_SERVER_SAFETY.md)** | Dedicated Server 계층 격리 및 호환성 계층 무결성 강화 | `v2.0.0` | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 데디케이티드 서버 NoClassDefFoundError 방지를 위한 GUI 핸들러 클라이언트 완전 이전 |
| **[ADR-002](ADR_002_ARCHITECTURE_DOCS_RESTRUCTURING_AND_SPEC_MODERNIZATION.md)** | 아키텍처 및 기술 사양 문서 체계 대량 개편 및 최신화 | `v2.0.0` | [00. 시스템 개요](../ko_kr/spec/00_OVERVIEW.md) | 5계층 아키텍처, 5대 그래프 솔버, 와이어 공간 분할 색인 사양서 전면 개편 |
| **[ADR-003](ADR_003_MULTIPLAYER_NETWORK_INTEGRITY_AND_SYSTEM_STABILIZATION.md)** | 멀티플레이어 네트워크 무결성, 서버 동시성 안정화 및 원자적 데이터 영속화 사양 | `v2.0.0` | [04. 멀티플레이어](../ko_kr/spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) | 512KB C2S 분할 스트리밍, ATOMIC_MOVE 원자적 파일 저장소, 접속 종료 시 락 즉시 해제 |
| **[ADR-006](ADR_006_TURBINE_AND_MACHINE_PARALLEL_ENHANCEMENT.md)** | 터빈 발전기 소모품 모델링·독립 티어 분리 및 기계 가용 병렬 산출 명세 | `v2.1.0-alpha.3` | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 로터 마모율/수명 모델링, 로터 홀더/다이나모 해치 티어 분리, 윤활유 부스트 토글 |
| **[ADR-007](ADR_007_HIERARCHICAL_PAGE_EXPLORER_AND_MACHINE_TEMPLATES.md)** | 계층형 폴더블 페이지 탐색기 및 머신 하드웨어 템플릿 시스템 | `v2.1.0-alpha.3` | [03-04. 검색 & 도구](../ko_kr/spec/03_04_RECIPE_SEARCH_AND_TOOLS.md) | 다층 폴더 트리 사이드바, 실시간 검색, Ctrl+K 퀵 스위처, 하드웨어 스펙 보존형 레시피 복제 |
| **[ADR-008](ADR_008_AE2_AUTOCRAFTING_PLAN_AND_PRECISION_ETA_INTEGRATION.md)** | AE2 오토크래프팅 플랜 연동 및 패턴-페이지 기반 정밀 ETA 시스템 | `v2.1.0-alpha.3` | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | BoardPage ↔ AE2 가공 패턴 1:1 바인딩, ICraftingPlan 인터셉트 및 정밀 ETA/병목 산출 |
| **[ADR-009](ADR_009_HEADLESS_LAYER_ISOLATION_AND_SERVER_SAFETY.md)** | 헤드리스 계층 격리 및 데디케이티드 서버 안전성 강화 명세 | `v2.1.0-alpha.3` | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | API/Compat의 클라이언트 참조 해소, SearchableRecipe 도메인 승격 |
| **[ADR-010](ADR_010_STATIC_REFLECTION_CACHING_AND_CLEAN_EXCEPTION.md)** | 리플렉션 정적 캐싱 및 예외 처리 무결성 개편 명세 | `v2.1.0-alpha.3` | [05. 외부 연동](../ko_kr/spec/05_INTEGRATION_AND_I18N.md) | 동적 리플렉션의 static final 1회 캐싱, bare catch 제거 |
| **[ADR-011](ADR_011_CONTROL_FLOW_FLATTENING_AND_SELF_DESCRIPTIVE_CODE.md)** | 제어 흐름 평탄화 및 자기 서술적 클린 코드 정비 명세 | `v2.1.0-alpha.3` | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | 중첩 깊이 1~2단계 평탄화, 조기 반환 가드 적용, CanvasInteractionHandler 분해 |
| **[ADR-012](ADR_012_BOARD_USABILITY_AND_PRECISION_FLOW_MODELING.md)** | 보드 사용성 개선 및 정밀 플로우 모델링 사양 | `v2.1.0-alpha.4` | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 16px 격자 스냅, 커스텀 병렬 정수 지정, 외부/무한 공급원 정션 노드 확장 |
| **[ADR-013](ADR_013_MODULAR_COMBUSTION_COMPLEX_INTEGRATION.md)** | Star Technology 모듈러 연소 복합체 및 프레임 부스팅 발전 시스템 통합 명세 | `v2.2.1` | [05. 외부 연동](../ko_kr/spec/05_INTEGRATION_AND_I18N.md) | MCF 프레임 매크로 단일 노드 모델, 중앙 냉각수 단일 풀 소모, 최대 8대 도킹 모듈 슬롯 관리 |
| **[ADR-014](ADR_014_CANVAS_GRAPHICS_PIPELINE_AND_FLOW_SOLVER_OPTIMIZATION.md)** | 대규모 노드 캔버스 그래픽 파이프라인 및 포트 플로우 계산 최적화 명세 | `v2.1.0-alpha.5` | [03-01. 캔버스 & 노드](../ko_kr/spec/03_01_CANVAS_AND_NODE_CARDS.md) | O(1) 포트 플로우 캐싱, 위젯 O(1) 해시 맵, 뷰포트 AABB 컬링 |
| **[ADR-015](ADR_015_BACKGROUND_INDEXING_STABILIZATION_AND_PIPELINE_OPTIMIZATION.md)** | 백그라운드 레시피 인덱싱 파이프라인 및 머신 매트릭스 베이킹 최적화 명세 | `v2.1.0-beta.1` | [05. 외부 연동](../ko_kr/spec/05_INTEGRATION_AND_I18N.md) | Phase 3 클라이언트 프리징 해소, EMI 인덱싱 지연 베이킹, 비동기 스레드 동기화 안정화 |
| **[ADR-016](ADR_016_BOARD_SCREEN_MODULAR_DECOMPOSITION.md)** | BoardScreen 모듈화 분해 및 단일 책임 아키텍처 명세 | `v2.1.0-beta.1` | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | 모놀리식 BoardScreen을 4대 서브시스템(Renderer, Viewport, Action, Interaction)으로 분해 |
| **[ADR-017](ADR_017_RESPONSIVE_GUI_SCALE_AND_ADAPTIVE_LAYOUT.md)** | GUI 배율 독립 분리 및 멀티블록 카탈로그·툴바 반응형 레이아웃 | `v2.1.0-beta.1` | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | 보드 전용 가상 뷰포트 배율 변환 엔진, 멀티블록 카탈로그 가변 행 및 적응형 툴바 |
| **[ADR-018](ADR_018_RATE_BASED_WIRE_ANIMATION_AND_BOTTLENECK_VISUALIZATION.md)** | 처리량 포화도 기반 와이어 애니메이션 변조 및 실시간 병목 시각화 명세 | `v2.1.0-beta.2` | [03-01. 캔버스 & 노드](../ko_kr/spec/03_01_CANVAS_AND_NODE_CARDS.md) | 포화율(Supply/Demand) 기반 듀티 사이클 변조, 3단계 RGB 보간 및 결핍 경고 펄스 |
| **[ADR-019](ADR_019_BYPRODUCT_VOID_MANAGEMENT_AND_SINK_SYSTEM.md)** | 잉여 부산물 폐기 처리 및 보이드 싱크 시스템 명세 | `v2.1.0-beta.2` | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 캔버스 정션 보이드 싱크(VOID_SINK), 포트 단위 Mark as Void, 유량 수지 차감 연동 |
| **[ADR-020](ADR_020_HIGH_SPEED_FLOW_BATCHING_AND_EFFICIENCY_MODULATION.md)** | 고속 레시피 애니메이션 배치, 기계 효율 연동형 출력 와이어 흐름 및 분기 노드 우선 배분 명세 | `v2.2.0` | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 1초 미만 고속 레시피 파티클 묶음, 결핍 펄스 vs 기계 가동률(η) 비례 출력 감속 분리 |
| **[ADR-021](ADR_021_GREATE_KINETIC_TIER_ADAPTER_INTEGRATION.md)** | Greate 모드 연동을 위한 AbstractKineticModAdapter 계층 분리 및 티어드 회전 운동 기계 어댑터 명세 | `v2.2.0` | [05. 외부 연동](../ko_kr/spec/05_INTEGRATION_AND_I18N.md) | AbstractKineticModAdapter 계층 분리, GreateModAdapter 10단계 티어 매핑 |
| **[ADR-022](ADR_022_CLOSED_LOOP_RECIRCULATION_AND_SUPPLY_ALLOCATION.md)** | 폐쇄 순환 공정 자급 자원 보호 및 공급 우선 할당 알고리즘 명세 | `v2.2.0-alpha.2` | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | Tarjan SCC 기반 자급 순환 자원 불변식 감쇠 차단, Greedy Demand-Filling 엣지 할당 |
| **[ADR-023](ADR_023_PER_CRAFT_BATCH_VIEW_AND_STOICHIOMETRIC_LOOP_VERIFICATION.md)** | 레시피 1회(배치) 기준 뷰 모드 및 화학양론적 순환 루프 검증 명세 | `v2.2.0-alpha.2` | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 시간 단위 소거형 1회(1x) 뷰 모드, 화학양론적 포트 보존 및 밸런스 인디케이터 |
| **[ADR-025](ADR_025_UNIFIED_CANVAS_WORKSPACE_AND_CONTEXT_DRIVEN_UI.md)** | 3-패널 통합 워크스페이스 및 컨텍스트 중심 UI/UX 현대화 명세 | `v2.2.0-alpha.3` | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | 캔버스/노드 우클릭 컨텍스트 메뉴, 스마트 커넥트 추천, 비모달 우측 인스펙터 패널 |
| **[ADR-026](ADR_026_MODAL_DIALOG_STACK_AND_REGISTRY.md)** | 모달 다이얼로그 스택 및 레지스트리 아키텍처 | `v2.2.0-alpha.3` | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | IBoardModal 공통 인터페이스, LIFO 기반 ModalStack 및 다이얼로그 계층화 |
| **[ADR-027](ADR_027_CANVAS_INTERACTION_FINITE_STATE_MACHINE.md)** | 캔버스 인터랙션 유한 상태 머신 명세 | `v2.2.0-alpha.3` | [03. UI & 렌더링](../ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md) | CanvasInteractionState FSM 전면 도입, 불리언 플래그 제거, 결정론적 상태 전이 |
| **[ADR-028](ADR_028_COMPOSABLE_RECIPE_SEARCH_SPECIFICATION.md)** | 합성 가능한 레시피 검색 쿼리 명세 패턴 | `v2.2.0-alpha.3` | [03-04. 검색 & 도구](../ko_kr/spec/03_04_RECIPE_SEARCH_AND_TOOLS.md) | 검색 필터 로직 Specification Pattern 모듈화, And/Or/Not 합성 및 단락 평가 최적화 |
| **[ADR-029](ADR_029_MOD_ADAPTER_INTERFACE_SEGREGATION_AND_EXTENSIONS.md)** | IModAdapter 인터페이스 분리(ISP) 및 Extension Object 패턴 명세 | `v2.2.0-alpha.3` | [05. 외부 연동](../ko_kr/spec/05_INTEGRATION_AND_I18N.md) | IModAdapter 슬림화, 6대 도메인 Provider 분리 및 Extension Object 패턴 |
| **[ADR-030](ADR_030_UNIFIED_NODE_LAYOUT_BOUNDS_AND_HITBOX_MODEL.md)** | 노드 카드 레이아웃 바운즈 단일 출처화 및 통합 히트박스 모델 명세 | `v2.2.0-alpha.4` | [03-01. 캔버스 & 노드](../ko_kr/spec/03_01_CANVAS_AND_NODE_CARDS.md) | NodeLayoutBounds 기반 히트박스 일원화 및 슬림 모드 조작 간섭 해소 |
| **[ADR-033](ADR_033_COMPREHENSIVE_DIVERGENCE_DEFENSE_MATRIX.md)** | 포괄적 공정 발산 방어 매트릭스 및 상황별 진단 가이드 시스템 명세 | `v2.2.0-alpha.4` | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 증식 루프, 8자 루프, 촉매 감쇠 등 7대 발산 시나리오 자동 방어 및 상황별 진단 뱃지 |
| **[ADR-034](ADR_034_JUNCTION_BUFFER_AND_ANCHOR_SYSTEM.md)** | 정션 노드 동적 잉여/결핍 완충 배선 및 Auto-Ratio 유량 앵커 시스템 | `v2.2.0-alpha.4` | [01. 코어 도메인](../ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md) | 퀵 마커 컨텍스트 드래그, Void Sink 오버플로우 스필웨이, Fixed 정션 유량 앵커 |
| **[ADR-035](ADR_035_TWO_STAGE_LINEAR_FLOW_SOLVER.md)** | 2단계 선형 연립방정식 유량 솔버 및 정수 양자화 아키텍처 | `v2.2.0-alpha.4` | [02. 수학 & 알고리즘](../ko_kr/spec/02_MATH_AND_ALGORITHMS.md) | 가우스-요르단 소거법 기반 1단계 연속 유량 균형 연산 및 2단계 정수 양자화 수렴 보장 |
| **[ADR-036](ADR_036_KINETIC_GENERATOR_AND_ENERGY_CONVERTER_TAXONOMY.md)** | 회전 운동 동력원 및 에너지 상호 변환기 분류 체계, 가변 RPM 동적 산출 및 뷰어 UI 개편 명세 | `v2.2.0` | [05. 외부 연동](../ko_kr/spec/05_INTEGRATION_AND_I18N.md) | 회전 운동 4대 카테고리 분리, 풍차 베어링 가변 돛/RPM 수식 산출 |

---

## ❌ 기각/결번 레지스트리 (Rejected Proposals - 1건)

제안되었으나 시스템 안정성, 아키텍처 원칙 위반 또는 구조적 결함으로 인해 영구 기각되어 결번으로 관리되는 레코드입니다.

| 문서 번호 | 제안 제목 (Title) | 제안 버전 | 기안일 | 사양서 매핑 | 기각 사유 요약 |
| :---: | :--- | :---: | :---: | :--- | :--- |
| **[RFC-046](../rfc/RFC_046_BOARD_PAGE_PROVIDER_ABSTRACTION.md)** | 멀티 워크스페이스 통합 페이지 공급자 추상화 명세 | `v2.3.0` | 2026-09-11 | [04. 멀티플레이어](../ko_kr/spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) | ClientWorkspaceState 헤드리스 테스트 불필요 전제(이미 테스트 가능) 및 원격 페이지 지연 압축 해제 아키텍처를 파괴하는 메모리 결함으로 인해 영구 기각 |

---

## 💡 활성 RFC 제안 목록 (Active RFC Proposals in `docs/rfc/`)

구현 착수 전 기술 검토, 대안 비교 및 승인 대기 중인 RFC 제안 문서 목록입니다. 구현이 완료되면 공식 ADR로 승격되어 상단 레지스트리에 영구 보존됩니다.

*현재 활성 상태의 미결정 RFC가 없습니다.*
