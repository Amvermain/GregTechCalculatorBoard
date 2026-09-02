# [ADR-012] 보드 사용성 개선 및 정밀 플로우 모델링 사양
# (Board Usability & Precision Flow Modeling Architecture)

| 메타데이터 항목 | 내용 |
| :--- | :--- |
| **문서 번호** | `ADR-012` |
| **상태 (Status)** | 🟢 `IMPLEMENTED` |
| **결정 일자** | 2026-09-02 |
| **대상 버전** | `v2.1.0-alpha.4` |
| **관련 ADR** | [ADR-004](ADR_004_CLEAN_ARCHITECTURE_AND_DOMAIN_DECOMPOSITION.md), [ADR-006](ADR_006_TURBINE_AND_MACHINE_PARALLEL_ENHANCEMENT.md), [ADR-007](ADR_007_HIERARCHICAL_PAGE_EXPLORER_AND_MACHINE_TEMPLATES.md) |
| **영향 범위** | `RecipeSearchDialog`, `CanvasInteractionHandler`, `RecipeNode`, `SupplyMode`, `JunctionSupplyDialog`, `FlowBalanceMatrixSolver`, `FlowSummaryAggregator`, `BoardManager` |

---

## 1. 맥락 및 배경 (Context)

대규모 멀티블록 및 모드팩(GTCEu Modern, Create, Thermal, AE2) 공정 라인 설계 시, 사용자 인터랙션의 편의성과 복잡 공정 표현력을 보강하기 위해 다음 5대 핵심 요구사항이 제기되었습니다:

1. **레시피 선택기 스크롤바 조작성**: 긴 레시피 목록 탐색 시 휠 외에 스크롤바 트랙 클릭 및 썸(Thumb) 드래그 미지원 문제 해결.
2. **캔버스 노드 격자 스냅 (Grid Snap)**: $16\text{px}$ 단위의 좌표 양자화를 통해 다중 노드 및 프레임 레이아웃 정렬 자동화 (`Ctrl` 드래그 및 보드 설정 토글 지원).
3. **머신 기본 하드웨어 설정(Preset) 관리 가시성**: 카테고리별 머신 프리셋(전압, 병렬, 코일 등)의 설정 및 조회 접근성 보장.
4. **커스텀 병렬/오버클록 임의 정수 지정**: 병렬 해치 규격($4, 16, 64$) 외에 소규모 배치나 밸런싱을 위한 비정형 정수($2, 3, 5$ 등)의 `customParallel` 수동 오버라이드 지원.
5. **외부 공급원/무한 공급 모드 정션 노드 확장**: 무한 자원(무한 물 해치, AE2 무한 셀) 또는 기저 공급량($R_{\text{ext}}\text{ units/s}$)을 정션 노드에서 직접 공급하여 상류 요구량 차단 및 순 결손량(Net Deficit) 자동 상쇄.

---

## 2. 아키텍처 결정 사항 (Decision Drivers)

### 2.1 스크롤바 인터랙션 상태 머신 (`RecipeSearchDialog`)
- **트랙 클릭 점프 및 썸 드래그**:
  $$O_{\text{new}} = \text{clamp}\left(\left\lfloor \frac{Y_{\text{mouse}} - Y_{\text{track}} - \frac{H_{\text{thumb}}}{2}}{H_{\text{track}} - H_{\text{thumb}}} \times O_{\text{max}} \right\rfloor, 0, O_{\text{max}}\right)$$
- `isDraggingScrollBar` 플래그와 `dragGrabOffsetY`를 통해 마우스 이벤트(`mouseClicked`, `mouseDragged`, `mouseReleased`)를 $O(1)$로 동기화.

### 2.2 격자 스냅(Grid Snap) 좌표 양자화 (`CanvasInteractionHandler`)
- **동작 조건**: `Screen.hasControlDown() || BoardManager.getInstance().isGridSnapEnabled()`
- **스냅 공식**:
  $$\Delta X_{\text{snapped}} = \text{round}\left(\frac{\Delta X_{\text{total}}}{G}\right) \times G, \quad \Delta Y_{\text{snapped}} = \text{round}\left(\frac{\Delta Y_{\text{total}}}{G}\right) \times G \quad (G = 16)$$
- 다중 선택된 모든 노드/프레임/노트의 원점(`dragStartPositions`) 대비 절대 변위를 적용하여 그룹 내부의 상대적 기하 배치를 유지.

### 2.3 외부 공급원 정션 도메인 모델 (`SupplyMode` & `RecipeNode`)
- `SupplyMode` (`NONE`, `INFINITE`, `FIXED_RATE`) enum 도입 및 NBT 직렬화/역직렬화 (`supplyMode`, `externalSupplyRate`, `customParallel`).
- `RecipeNode.getTotalParallel()`에서 `customParallel > 0`일 때 이를 우선 반환하도록 결합 해제.
- `JunctionSupplyDialog` 모달 UI를 통해 정션 우클릭 시 공급 모드 및 공급 속도를 직관적으로 설정.

### 2.4 수지 균형 엔진 및 요약 확장 (`FlowBalanceMatrixSolver` & `FlowSummaryAggregator`)
- **요구량 역전파 제어 (`calculateTotalConnectedPortDemand`)**:
  - `cNode.isInfiniteSupply()`: 상류 생산자로의 요구량 전파를 즉시 차단($\text{demand} = 0$).
  - `cNode.isExternalSupply()` ($R_{\text{ext}} > 0$): 하류 총 요구량 $D_{\text{down}}$에서 $R_{\text{ext}}$를 차감한 잔여량 $\max(0, D_{\text{down}} - R_{\text{ext}})$만 상류로 전파.
- **유효 공급량 산출 (`getEffectiveProducerOutputRate`)**:
  - `SupplyMode.INFINITE` 정션은 연결된 하류 포트 요구량의 $100\%$를 공급.
  - `SupplyMode.FIXED_RATE` 정션은 `incomingSupply + externalSupplyRate`를 하류로 공급.
- **보드 자원 요약 (`computeSummary`)**:
  - 정션 노드가 외부 공급 모드일 때 해당 공급량을 자원 생산량에 합산하여 순 원자재 결손량(Raw Input Deficit)에서 자동 차감.

---

## 3. 구현 내역 요약 (Implementation Summary)

| 모듈 / 파일 | 변경 유형 | 주요 변경 내용 |
| :--- | :---: | :--- |
| `SupplyMode.java` | **NEW** | `NONE`, `INFINITE`, `FIXED_RATE` 공급 모드 enum 선언 |
| `RecipeNode.java` | **MODIFY** | `supplyMode`, `externalSupplyRate`, `customParallel` 필드 및 getter/setter, `getTotalParallel()` 우선순위 연동 |
| `RecipeNodeSerializer.java` | **MODIFY** | `supplyMode`, `externalSupplyRate`, `customParallel` NBT 직렬화/역직렬화 |
| `FlowBalanceMatrixSolver.java` | **MODIFY** | 외부 공급 정션 상류 요구량 차감/차단 및 유효 공급량 산출, 편의 오버로드 추가 |
| `FlowSummaryAggregator.java` | **MODIFY** | 외부 공급 정션 공급량을 총 생산량에 합산하여 순 결손량 차감 |
| `RecipeSearchDialog.java` | **MODIFY** | 스크롤바 트랙 클릭 점프 및 썸 드래그(`mouseDragged`/`mouseReleased`) 구현 |
| `CanvasInteractionHandler.java` | **MODIFY** | 노드/프레임/노트 이동 및 크기 조절 시 $16\text{px}$ 격자 스냅 적용 |
| `JunctionSupplyDialog.java` | **NEW** | 정션 노드 외부 공급 모드 설정 모달 다이얼로그 |
| `MachineConfigDialog.java` | **MODIFY** | `parallelBox`와 `customParallel` 정수 입력 양방향 연동 |
| `BoardSettingsDialog.java` | **MODIFY** | HUD 탭에 격자 스냅 자동 활성화 토글 연동 |
| `BoardManager.java` | **MODIFY** | `gridSnapEnabled`, `gridSnapSize` 필드 및 NBT 영속화 |
| `NodeCardRenderer.java` | **MODIFY** | 정션 노드 카드에 무한 기호 $\infty$ 및 공급 속도 뱃지/테두리 렌더링 |
| `JunctionExternalSupplyTest.java` | **NEW** | 정션 외부 공급 직렬화 및 수지 균형/결손량 상쇄 단위 테스트 |
| `GridSnapAndUsabilityTest.java` | **NEW** | 격자 스냅 영속화 및 커스텀 병렬 오버라이드 단위 테스트 |

---

## 4. 검증 결과 (Verification Results)

- **단위 테스트 및 빌드 검증**:
  - `.\gradlew.bat clean build` 실행 완료 (총 11개 태스크 성공)
  - 총 538개 단위 테스트 $100\%$ 통과 (`BUILD SUCCESSFUL`)
  - 다국어 리소스 일관성 테스트(`testI18nCompletenessAndConsistency`, `testCodeKeysAllPresentInLanguageFiles`) $100\%$ 통과 (`en_us.json`, `ko_kr.json`, `zh_cn.json` 동기화 완료).
