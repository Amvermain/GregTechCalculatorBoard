# ADR-056: 모듈형 아카데미 및 맥락형 튜토리얼 아키텍처 개편 명세
(Modular Academy & Contextual In-Game Tutorial Architecture Specification)

- **문서 번호**: ADR-056
- **대상 버전**: `v2.3.0`
- **상태**: `IMPLEMENTED`
- **결정/완료일**: 2026-09-15
- **주관 계층**:
  - Client UI & Tutorial Engine Layer (`client.gui.tutorial.*`, `client.gui.dialog.TutorialLauncherDialog`, `client.gui.tutorial.model.*`)
  - Screen Context API (`client.gui.api.IBoardScreenContext`, `client.gui.BoardScreen`)

---

## 1. 개요 및 배경 (Motivation)

### 1.1 현황 및 문제 진단
본 모드(`GregTechCalculatorBoard`)는 v2.0.0부터 v2.2.2에 이르기까지 복합 공정 선형 솔버, 공유 기계 풀, 1:1 서브페이지 복합 모듈, 정션 유량 분배 계층, 페이지별 전압 자동 프로비저닝 등 방대한 기능 확장이 이루어졌습니다.

그러나 과거 [ADR-005](ADR_005_MULTIBLOCK_SELECTOR_TUTORIAL_INTEGRATION.md) 기반의 단일 13단계 일직선(Linear) 구조 튜토리얼 시스템([`TutorialManager`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/client/gui/tutorial/TutorialManager.java))은 다음과 같은 한계를 가지고 있었습니다:

1. **핵심 가치 누락**: 전체 자동 비율 맞춤(`Alt + R`), 와이어 우선순위(마우스 휠), 정션 분배 모드, 부산물 보이드, 정상 상태 재순환 루프 스케일링, 복합 모듈 1:1 서브페이지(더블클릭 진입 및 바운더리 핀), 전역 밸런스 대시보드(`B`), 페이지 설정(`Alt + P`) 등이 기존 튜토리얼에서 다루어지지 않았습니다.
2. **인지 과부하**: 누락된 기능들을 선형 스텝으로 추가할 경우 30단계를 넘어가 플레이어의 이탈률이 급증합니다.
3. **재학습 불가**: 특정 고급 주제(예: 루프 해결, 서브페이지 모듈 등)만 복습하고 싶어도 이전의 모든 기초 단계를 처음부터 다시 진행해야 했습니다.

### 1.2 설계 목표 (Design Goals)
* **초고속 온보딩 (Fast-Track, ~45초)**: 노드 배치 ➔ 드래그 배선 ➔ `Alt + R` 자동 비율 맞춤 ➔ `T` 유량 단위 전환의 핵심 가치 흐름을 45초 만에 완주.
* **주제별 독립 아카데미 (4대 모듈형 챕터)**: 계산/솔버, 배선/정션, 공장 모듈화/BOM, 워크스페이스 관리로 분리하여 원하는 주제를 즉시 선택 실습.
* **인게임 맥락형 넛지 (Contextual In-Game Nudges)**: 튜토리얼 모달을 열지 않더라도 특정 상황(루프 형성, 잉여 유량, 긴 배선 등) 감지 시 1회성 담백한 토스트 힌트 제공.
* **100% 하위 호환성 보장**: 기존 `TutorialStepTest`의 14개 선형 스텝 enum 및 테스트 규격을 보존하여 리그레션을 원천 방지.

---

## 2. 3-트랙 온보딩 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                 UI / Interaction Layer                      │
│  TutorialLauncherDialog ── TutorialOverlay                  │
│  ContextualNudgeToast   ── HotkeyHudWidget                  │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                  Core Tutorial Engine                       │
│  TutorialManager (상태 머신) ── ContextualNudgeManager (감지기)│
│  TutorialTrackRegistry (트랙/챕터 메타데이터)                 │
└──────┬───────────────────────┼──────────────────────────────┘
       │                       │
┌──────▼──────────────┐ ┌──────▼──────────────┐ ┌─────────────▼─────────┐
│ Track 1: Fast-Track │ │ Track 2: 4대 챕터   │ │ Track 3: 맥락형 넛지   │
│ (45초 초고속 온보딩) │ │ (주제별 독립 실습) │ │ (5대 인게임 상황 힌트)│
└─────────────────────┘ └─────────────────────┘ └───────────────────────┘
```

### 2.1 신규 도메인 모델 및 규격
* [`TutorialTrackType`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/client/gui/tutorial/model/TutorialTrackType.java): `FAST_TRACK`, `ACADEMY_CHAPTER` 열거형.
* [`ITutorialChapter`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/client/gui/tutorial/model/ITutorialChapter.java): 챕터 식별자, 제목/설명 컴포넌트, 아이콘 텍스처, 스텝 정의 목록 및 진입 액션 규격.
* [`TutorialChapterStepDef`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/client/gui/tutorial/model/TutorialChapterStepDef.java): 스텝 번호, 다국어 키, 캔버스 초기화 `Consumer<BoardPage>` 함수형 레코드.
* [`ContextualNudge`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/client/gui/tutorial/model/ContextualNudge.java): 넛지 ID, 트리거 타입, 안내 메시지, 단축키 힌트, 관련 아카데미 챕터 ID 불변 레코드.

---

## 3. 상세 트랙 구성 명세

### 3.1 Track 1: 초고속 스타터 (Fast-Track, 4단계)
| 단계 | 명칭 | 유저 조작 | 자동 검증 및 전이 조건 |
| :---: | :--- | :--- | :--- |
| **1** | 레시피 노드 배치 | `Space` 또는 검색창을 눌러 보일러 노드 배치 | 노드 1개 캔버스 추가 시 전이 |
| **2** | 드래그 검색 배선 | 보일러의 증기 포트에서 드래그하여 증기 터빈 노드 연결 | 보일러 ➔ 터빈 간 와이어 연결 시 전이 |
| **3** | **전체 자동 비율 맞춤** | `Alt + R` 키를 눌러 터빈 대수 자동 계산 | `onAutoRatioTriggered()` 수신 시 전이 |
| **4** | 속도 단위 전환 및 완료 | `T` 키를 눌러 초당(/s) ➔ 분당(/min) 전환 | `onRateUnitToggled()` 시 완료 화면 표시 |

### 3.2 Track 2: 주제별 대화형 아카데미 (4대 독립 챕터)
1. **챕터 1: 비율 계산과 솔버 (ch1_solver)**:
   - 스텝 1: 최종 노드 우클릭 ➔ `⌖ 기준 앵커(Anchor)` 지정 (`onAnchorConfigured()`).
   - 스텝 2: `Alt + Shift + R` 조화 정수 비율 최적화 (`onIntegerRatioTriggered()`).
   - 스텝 3: `⚠ Damped` 루프 경고 확인 및 `Shift + 우클릭` 정상 상태 스케일링 (`onLoopScaled()`).
2. **챕터 2: 배선과 정션 미세 제어 (ch2_wiring)**:
   - 스텝 1: 연결선 더블클릭 정션 삽입 (`onJunctionInserted()`).
   - 스텝 2: 연결선 마우스 휠 굴림으로 우선순위 `[1]`, `[2]` 지정 (`onWirePriorityChanged()`).
   - 스텝 3: 출력 포트 `Shift + 우클릭` 부산물 보이드 설정 (`onPortVoidConfigured()`).
   - 스텝 4: 입력 포트 마우스 휠 굴림 대체 재료(Tag) 순환 (`onPortTagCycled()`).
3. **챕터 3: 공장 모듈화와 BOM (ch3_packaging)**:
   - 스텝 1: 저가동률 노드 선택 후 `Ctrl + Shift + S` 공유 기계 풀 병합 (`onSharedMachineFramed()`).
   - 스텝 2: 프레임 선택 후 `Ctrl + Shift + G` 복합 모듈 압축 (`onModuleGrouped()`).
   - 스텝 3: 모듈 카드 더블클릭 1:1 서브페이지 진입 및 바운더리 핀 확인 후 Esc 복귀 (`onSubpageExited()`).
   - 스텝 4: `Shift + B` 자재 명세서(BOM) 중복 없는 1대분 집계 확인 (`onBOMOpened()`).
4. **챕터 4: 프로젝트 및 워크스페이스 관리 (ch4_workspace)**:
   - 스텝 1: `Alt + P` 목표 전압 HV 및 에너지 해치 자동 장착 설정 (`onPageSettingsConfigured()`).
   - 스텝 2: `Tab` 키로 계층형 폴더 브라우저 열기 (`onFolderBrowserOpened()`).
   - 스텝 3: `B` 키로 글로벌 밸런스 대시보드 열람 (`onGlobalBalanceOpened()`).

### 3.3 Track 3: 인게임 맥락형 넛지 (Contextual In-Game Nudges)
* 계정별/세션별 1회성 노출 및 토글 옵션 지원.
* 우측 하단 5초간 토스트 노출 후 자동 만료.
* 튜토리얼 진행 중에는 방해 방지를 위해 트리거 자동 차단.

| 넛지 ID | 트리거 조건 | 힌트 내용 |
| :--- | :--- | :--- |
| `nudge_auto_connect` | 2개 이상의 노드가 배치되었으나 배선이 없을 때 | Shift+C 일괄 자동 연결 안내 |
| `nudge_wire_reroute` | 연결선 길이가 250px 이상 길어질 때 | 더블클릭 Junction 삽입 안내 |
| `nudge_loop_damped` | 감쇄 루프(`⚠ Damped`) 형성 감지 시 | Shift+우클릭 정상 상태 스케일링 안내 |
| `nudge_byproduct_void`| 출력 포트에 소비되지 않는 잉여 유량이 남을 때 | Shift+우클릭 부산물 보이드 안내 |
| `nudge_module_subpage` | 복합 모듈 카드가 캔버스에 존재할 때 | 더블클릭 1:1 서브페이지 진입 안내 |

---

## 4. UI 컴포넌트 구조

* **[`TutorialLauncherDialog`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/client/gui/dialog/TutorialLauncherDialog.java)**:
  - 상단 툴바 `[▶ 튜토리얼]` 버튼 클릭 시 호출.
  - 최상단 Fast-Track 카드, 하단 2x2 아카데미 챕터 그리드, 하단 넛지 활성화 체크박스 및 `[H]` 단축키 HUD 링크 제공.
* **[`ContextualNudgeToast`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/client/gui/tutorial/ContextualNudgeToast.java)**:
  - 캔버스 우측 하단 플로팅 미니 토스트.
  - 닫기 `[✕]` 버튼 및 관련 챕터 바로가기 클릭 지원.

---

## 5. 검증 및 테스트 결과

* **단위 테스트**:
  - `TutorialTrackTest`: Fast-Track 4단계 및 아카데미 4대 챕터의 전이 조건, 캔버스 격리 및 진행 완주 검증 통과 (100%).
  - `ContextualNudgeTest`: 5대 트리거 감지, 1회성 노출 플래그, 비활성화 토글 및 만료 로직 검증 통과 (100%).
  - `TutorialStepTest`: 기존 14단계 선형 튜토리얼 하위 호환성 100% 유지 검증 통과.
* **다국어(i18n) 및 린터 검증**:
  - 4개 국어(`en_us`, `ko_kr`, `ru_ru`, `zh_cn`) 총 1,420개 키 1:1 완전 패리티 확인 (`check_i18n.py`, 0 Errors).
  - 유니코드 이형 선택자(VS16 / `\uFE0F`) 무결성 100% 준수.
  - `lint_agent_rules.py --diff`: 규칙 위반 0건 통과.
* **전체 빌드 및 테스트**: `./gradlew.bat test` 전체 테스트 슈트 100% 성공.
