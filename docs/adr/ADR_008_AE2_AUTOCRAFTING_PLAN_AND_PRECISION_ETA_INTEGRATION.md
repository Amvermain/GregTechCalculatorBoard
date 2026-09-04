# ADR-008: AE2 오토크래프팅 플랜 연동 및 패턴-페이지 기반 정밀 ETA 시스템

| 메타데이터 항목 | 내용 |
| :--- | :--- |
| **문서 번호** | ADR-008 (RFC-008 승격) |
| **기능명** | AE2 Autocrafting Plan Integration & Pattern-Page Precision ETA Engine |
| **상태** | ACCEPTED / IMPLEMENTED |
| **대상 버전** | v2.1.0-alpha.3 |
| **결정 일자** | 2026-09-02 |
| **선행 요구사항** | ADR-007 (계층형 페이지 탐색기 및 머신 템플릿) |
| **연관 모드** | Applied Energistics 2 (AE2), GTCEu Modern, Thermal Series, Create |

---

## 1. 배경 및 의사결정 맥락 (Context)

- **AE2 오토크래프팅 시간 예측 부재**: AE2의 기본 크래프팅 시스템은 요청된 작업의 총 바이트 수(`bytes()`)와 재료 목록만 제공할 뿐, 해당 작업이 완료되기까지 실제로 얼마의 시간(Ticks/Seconds)이 소요될지 예측하지 못합니다.
- **모드 기계의 비선형 가공 시간**: GTCEu, Thermal, Create 등의 기계는 전압 티어, 오버클럭 횟수, 병렬 처리 해치, 가열 코일 온도 등에 따라 가공 시간과 병렬 처리량이 동적으로 변화합니다.
- **결정**: RFC-007 기반의 전용 계산기 페이지(`BoardPage`)와 AE2 가공 패턴을 1:1로 매핑(`PatternGraphRegistry`)하고, $O(K)$ 스케줄링 평가 엔진(`Ae2CraftingPlanEvaluator`)을 통해 정확한 틱 단위 ETA 및 병목 공정을 산출하여 AE2 크래프팅 확인창(`CraftConfirmScreen`)에 표시 및 딥링크를 제공하기로 결정했습니다.

---

## 2. 아키텍처 및 구현 명세 (Decision & Implementation)

### 2.1 계층 구조 및 모듈 분리

```mermaid
flowchart TD
    subgraph UI_Layer ["클라이언트 UI 계층"]
        A1["AE2 CraftConfirmScreen Hook"]
        A2["GTCalcBoard Canvas Screen & Dialogs"]
    end

    subgraph Service_Layer ["연산 및 스케줄링 계층"]
        B1["PatternGraphRegistry (패턴 ↔ BoardPage 매핑)"]
        B2["PageDurationEvaluator (페이지 단위 틱/유효 병렬도 산출)"]
        B3["Ae2CraftingPlanEvaluator (DAG CPM & Co-Processor 스케줄러)"]
        B4["Ae2PatternPageGenerator (Junction 포트 및 ae2 폴더 자동 생성)"]
    end

    subgraph AE2_Integration ["AE2 엔진 계층 (Soft-Dependency)"]
        C1["CraftConfirmMenu / PatternEncodingTermMenu"]
        C2["ICraftingPlan (patternTimes API)"]
    end

    C1 -->|1. ICraftingPlan 획득| C2
    C2 -->|2. patternTimes() 전달| B3
    B1 -->|3. 바인딩 페이지 스펙 제공| B2
    B2 -->|4. 단위 틱/병렬도 전달| B3
    B3 -->|5. 정밀 ETA 및 병목 산출| C1
    C1 -->|6. S2CAe2CraftingEtaPacket 전송| A1
    A1 -.->|7. 병목 라벨 클릭 딥링크| A2
```

### 2.2 핵심 컴포넌트

1. **`PatternId` & `PatternBindingEntry`**:
   - 결정론적 패턴 식별자 및 1:1 페이지 바인딩 레코드.
   - NBT 직렬화 및 역직렬화를 지원하여 세이브 파일(`BoardManager`)에 영구 보존.
2. **`PatternGraphRegistry`**:
   - 패턴 고유 키 ↔ 계산기 페이지(`BoardPage`) 간의 양방향 1:1 매핑 관리.
   - `IPageLifecycleListener`를 통해 페이지 삭제 시 자동 바인딩 해제.
3. **`Ae2PatternPageGenerator`**:
   - 패턴 아이템(`Shift + A`) 또는 패턴 인코딩 터미널의 `⚡ CalcBoard` 버튼 클릭 시 전용 페이지를 자동 생성.
   - 모든 생성 페이지는 기본 `"ae2"` 폴더(`folderPath = "ae2"`)에 배치되며, Input Junction 노드($x=60$)와 Output Junction 노드($x=560$)만 필요 수량에 맞추어 깔끔하게 배치 (더미 기계는 생성하지 않고 사용자가 실제 공정 배치).
4. **`PageDurationEvaluator` & `Ae2CraftingPlanEvaluator` (DAG CPM & Streaming Pipeline)**:
   - 페이지 내 배치된 기계들 중 임계 병목 기계(최장 단위 처리 시간 또는 $T_{\text{unit}} / P_{\text{eff}}$ 최댓값)를 동적으로 탐색하여 오버클럭 틱 및 유효 병렬도 추출.
   - **스트리밍 파이프라이닝 및 크리티컬 패스(DAG CPM) 스케줄링**:
     $$T_{\text{start}}(v) = \max_{(u, v) \in E} \left( T_{\text{start}}(u) + d_u \right)$$
     $$T_{\text{finish}}(v) = \max \left( T_{\text{start}}(v) + D_v, \ \max_{(u, v) \in E} \left( T_{\text{finish}}(u) + d_v \right) \right)$$
     $$\text{Total ETA} = \max_{v \in V} T_{\text{finish}}(v)$$
   - **다중 산출물 2-Pass 부산물 공제 (Byproduct Pool Deduction)**: 동일 공정/기계가 배출하는 부산물(예: 진공 동결기 헬륨-3, 원심분리기 부산물)을 선행 등록하여 하위 수요 자동 상계.
   - **설비 공유(Resource Contention) 직렬 누적**: 동일 계산기 페이지에 속한 복수 레시피는 단일 기계 공유로 인식하여 배치 직렬 누적($\sum \text{batches}$) 평가.
5. **UI 통합, 시각적 뱃지 & 투명한 이론치 안내 (Disclaimer)**:
   - `PageTabBarWidget`: AE2 연동 페이지 탭에 `§b⚡ ` 시안 뱃지 및 아이콘 표시.
   - `PageBrowserDrawer` & `QuickPageSwitcherDialog`: 목록 항목에 `§b[AE2] ` 태그 뱃지 표시.
   - `ToolbarWidget`: 상단 툴바에 `⚡ AE2 연동` 버튼 및 `PatternBindingDialog` 제공.
   - `CraftConfirmScreen`: 상단에 정밀 소요 시간 및 병목 공정 딥링크 버튼 렌더링.
   - **호버 툴팁 안내 각주 (Disclaimer)**: 공정별 독립 설비 가동 기준 이론치임을 명시하여 동일 기계/프로바이더 공유 시 지연 가능성을 사용자에게 투명하게 제공.
6. **입력 정션 노드 자원 고갈 시간 (Depletion Time / DT) 모드**:
   - 출력 포트만 연결된 입력 공급 정션 노드(`!hasIncoming && hasOutgoing`)에 대해 자원 고갈 속도($DT = \frac{\text{Amount}}{\text{Drain Rate}}$) 산출 및 시안색 뱃지 렌더링.

---

## 3. 결과 및 검증 (Consequences & Verification)

- **도메인 순수성 및 클린 아키텍처 준수**: `RecipeNode` 및 `FlowGraph`는 순수 모델로 유지되며, AE2 특화 로직은 `integration/ae2` 서브패키지에 완전 격리되었습니다.
- **다국어 리소스 동기화**: `en_us.json`, `ko_kr.json`, `zh_cn.json` 다국어 키 완벽 동기화.
- **단위 테스트 및 빌드 검증**: 5개 AE2 전용 테스트 스위트(`PatternGraphRegistryTest`, `Ae2PatternPageGeneratorTest`, `PageDurationEvaluatorTest`, `Ae2CraftingPlanEvaluatorTest`, `Ae2PlanSerializationTest`) 및 정션 DT 테스트를 포함한 **83개 단위 테스트 전수 통과 및 `.\gradlew.bat clean build` 성공 검증**.
