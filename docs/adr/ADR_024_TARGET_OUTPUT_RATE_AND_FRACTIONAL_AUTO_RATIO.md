# ADR-024: 목표 생산량 기반 기계 대수 자동 역산 및 정밀 소수점 Auto-Ratio
# (Target Output Rate Inverse Solver & Fractional Auto-Ratio Precision Scaling)

- **문서 번호**: ADR-024
- **상태**: `APPROVED` (구현 및 검증 완료)
- **결정일**: 2026-09-05
- **타겟 버전**: `v2.2.0-alpha.2`
- **주관 계층**: Calculation Engine & User Interface (`api.solver`, `api.model`, `api.storage`, `client.gui.util`, `client.gui.dialog`, `client.gui.interaction`)

---

## 1. 맥락 및 문제점 (Context & Problem Statement)

본 계산기 모드(`GregTechCalculatorBoard`)는 연속 유량(Continuous Flow Rate) 모델을 기반으로 기계 노드의 입출력 유량을 계산합니다:

$$\text{Rate}_{\text{single}} = \text{Amount} \times \text{Chance}_{\text{eff}} \times \text{CPS}_{\text{single}} \quad [\text{items/s} \text{ 또는 } \text{mB/s}]$$
$$\text{Rate}_{\text{total}} = \text{Rate}_{\text{single}} \times N$$

기존 상단 툴바의 **[⚖ Auto Ratio]** 기능은 마스터 앵커(Master Anchor, ⌖)를 기준으로 상/하류 기계들의 대수를 동기화할 때, 모든 기계 대수를 정수(`integerCounts = true`, `CountRoundingMode.CEIL`)로 강제 올림하는 구조를 가졌습니다.

이로 인해 12초당 1개($1/12\text{s} \approx 0.0833/\text{s}$)와 같은 저속 정밀 목표 생산 공정을 설정할 때 다음과 같은 기술적 문제가 발생했습니다:
1. **앵커 및 상류 기계의 강제 정수 올림**: 사용자가 앵커 대수로 $0.0833$대를 의도했더라도 Auto-Ratio 실행 즉시 $N = 1.0$으로 올림되고, 상류 기계들도 모두 $1.0$대로 올림되어 요구량이 최대 12배까지 과잉 왜곡되었습니다.
2. **목표 생산량 역산 인터페이스 부재**: 플레이어가 특정 출력 포트에 원하는 절대 속도($R_{\text{target}}$)를 입력하여 필요한 기계 대수($N$)를 $O(1)$로 역산하는 기능이 부재하여, 수동 암산 후 실수 대수를 직접 기입해야 했습니다.

---

## 2. 결정 사항 (Decision)

### 2.1 목표 생산량 역산 인터페이스 및 분수/단위 파서 (`TargetOutputRateDialog`, `TargetRateParser`)
- **포트 상호작용 확장**: 노드 카드의 출력 포트를 `Ctrl + 좌클릭` 시 "목표 생산량 설정(Target Output Rate)" 모달을 표시합니다.
- **`TargetRateParser` 구현**:
  - 분수 표기 지원: `1/12s`, `1/60s`, `5/2min`
  - 시간 단위 지원: `/s`, `/min`, `/h`, `/t`, `/d`
  - 유체 단위 지원: `mB/s`, `B/min`, `mB/min`
  - 입력 문자열을 $O(1)$ 정규식 파싱하여 초당 유량($R_{\text{target}}$ [items/s 또는 mB/s])으로 표준화 변환.
- **대수 역산 공식 ($O(1)$)**:
  $$N_{\text{req}} = \frac{R_{\text{target}}}{R_{\text{single}}}$$
  $$N_{\text{final}} = \max\left(0.0001, \frac{\lfloor N_{\text{req}} \times 10{,}000 + 0.5 \rfloor}{10{,}000}\right)$$
- **Undo/Redo 및 앵커 승격**: `ModifyPropertyCommand.machineCount`를 통해 변경 이력을 영속화하며, [Base Anchor로 지정] 옵션을 기본 제공합니다.

### 2.2 정밀 소수점 Auto-Ratio (`FlowGraphSolver.autoRatioFractional`)
- `FlowBalanceMatrixSolver.autoRatioFromAnchor`에 소수점 스케일링 모드(`integerCounts = false`)를 추가 지원합니다.
- 상류 기계 대수 산출 시 정수 올림을 생략하고 소수점 4자리 유효숫자(`0.0001` 단위) 정밀도로 비율을 동기화합니다:
  $$N_{\text{upstream}} = \max\left(0.0001, \frac{\lfloor N_{\text{needed}} \times 10{,}000 + 0.5 \rfloor}{10{,}000}\right)$$

### 2.3 앵커 노드 소수점 대수 보존 (`preserveFractionalAnchor`)
- 일반 정수 Auto-Ratio 실행 시에도, 앵커 노드 본인이 이미 소수점 대수($|N - \text{round}(N)| > 10^{-4}$)를 갖고 있는 경우 강제 정수 올림되지 않도록 보존하는 로직을 적용합니다:
  ```java
  double currentAnchorCount = anchor.getMachineCount();
  boolean isAnchorAlreadyFractional = Math.abs(currentAnchorCount - Math.round(currentAnchorCount)) > 1e-4;
  double targetAnchorCount;
  if (!integerCounts || (isAnchorAlreadyFractional && BoardManager.getInstance().isPreserveFractionalAnchor())) {
      targetAnchorCount = Math.max(0.0001, Math.round(currentAnchorCount * 10000.0) / 10000.0);
  } else {
      targetAnchorCount = quantizeMachineCount(graph, anchor, currentAnchorCount, CountRoundingMode.CEIL, true);
  }
  ```

### 2.4 UI 핫키 및 환경설정 연동 (`ToolbarWidget`, `BoardSettingsDialog`, `BoardManager`)
- **툴바 버튼 확장**:
  - `Click`: 기본 비율 맞춤 (환경설정 기본값 적용).
  - `Alt + Click`: 정밀 소수점 강제 맞춤 (`autoRatioFractional`, 툴바 라벨 `§b⚡` 표시).
  - `Shift + Click`: 무손실 정수 최소공배수 맞춤 (`autoRatioHarmonized`).
- **환경설정 NBT 영속화**:
  - `BoardManager.autoRatioFractionalDefault`: 기본 Auto-Ratio 모드(정수 vs 소수점) 설정.
  - `BoardManager.preserveFractionalAnchor`: 소수점 앵커 대수 보존 여부 설정.
  - `BoardSettingsDialog`의 `SettingsTab.RATIO` 영역에 토글 버튼 및 체크박스 배치.

---

## 3. 결과 및 영향 (Consequences)

### 3.1 긍정적 영향
1. **저속/정밀 목표 생산 공정 완벽 지원**: $0.0833/\text{s}$와 같은 저속 공정에서 기계 대수 왜곡 없이 정확한 원자재 공급 비율을 자동으로 도출 가능.
2. **직관적인 목표 기반 워크플로우**: 기계 대수를 암산할 필요 없이 포트 `Ctrl+Click`으로 "12초당 1개", "분당 100mB" 등 실제 목표치를 바로 기입 가능.
3. **기존 워크플로우와의 하위 호환성 유지**: 기본 모드가 정수 모드일 경우 기존 Auto-Ratio 동작이 $100\%$ 보존되며, `Alt+Click` 또는 설정 변경을 통해서만 정밀 모드가 활성화됨.
4. **i18n 4대 국어 동기화**: 신규 UI 및 툴팁 키 14개 항목이 한국어, 영어, 중국어 간체, 러시아어에 $1:1$ 완전 일치 동기화 완료.

### 3.2 검증 결과
- 신규 단위 테스트 `TargetRateParserTest` 4종 작성 및 통과 (분수, 유체, 단위 변환, 잘못된 형식 방어).
- 신규 단위 테스트 `FractionalAutoRatioTest` 3종 작성 및 통과 (저속 정밀 스케일링, 앵커 소수점 보존, 앵커 소수점 강제 올림 모드).
- 전체 668개 단위 테스트 100% 통과 (`BUILD SUCCESSFUL in 17s`).
- 초고속 정적 린터 (`lint_agent_rules.py --diff`) 0 Violations, 다국어 검증(`check_i18n.py`) 0 Errors.
