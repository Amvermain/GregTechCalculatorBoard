# GregTech Calculator Board (GTCalcBoard)

<p align="center">
  <a href="README.md">English</a> | <b>한국어</b> | <a href="CHANGELOG_KR.md">변경 로그</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg" alt="Minecraft 1.20.1">
  <img src="https://img.shields.io/badge/Loader-Forge%2047.2.0+-orange.svg" alt="Forge">
  <img src="https://img.shields.io/badge/GregTech-CEu%20Modern-blue.svg" alt="GTCEu Modern">
  <img src="https://img.shields.io/badge/Recipe%20Viewer-EMI%20%2F%20JEI%20Supported-purple.svg" alt="EMI & JEI">
  <a href="https://discord.gg/NaJWk3UjJN"><img src="https://img.shields.io/badge/Discord-Join%20Community-5865F2?logo=discord&logoColor=white" alt="Discord"></a>
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License">
</p>

그렉텍(GregTech CEu Modern), EMI, JEI 및 다양한 기술 모드 생태계를 위한 인게임 노드 그래프 계산기 및 플로우차트 에디터 모드입니다. 외부 스프레드시트나 웹 계산기에 의존하지 않고 마인크래프트 게임 내에서 복잡한 다단계 생산 공정을 직접 설계하고 수치를 완벽하게 맞출 수 있습니다.

---

## 주요 기능

### 1. 직관적인 노드 캔버스 & 플로우차트 설계
- **플러그형 레시피 뷰어 SPI 연동**: **EMI**, **JEI**, **JEI++** 모드와 플러그형 구조로 자동 연동되며, 레시피 뷰어가 없는 환경에서도 순수 바닐라 폴백으로 작동합니다. 화면 좌측 접이식 **즐겨찾기 사이드 독 (`[⭐ 즐겨찾기 (N) ▶]`)** 또는 EMI의 네이티브 `[+]` 버튼으로 레시피를 즉시 배치할 수 있습니다.
- **실시간 드래그 앤 검색 & 자동 배선**: 포트에서 빈 캔버스로 선을 끌어놓으면 소비/생산 공정을 자동 검색하고, 1클릭으로 노드 생성 및 와이어 자동 연결/정수 올림 비율 맞춤(`Shift`)을 완료합니다.
- **인플레이스 대체 레시피 스위칭**: 노드를 삭제하지 않고 기계 설정창의 `[🔄 Switch Recipe]` 버튼이나 우클릭 메뉴에서 동일 머신/산출물 대체 레시피로 즉시 교체하며, 포트 배선(`IngredientStack.getId()`)은 자동으로 보존됩니다.
- **포트 다중 선택 & 번들 일괄 배선**: 마키 박스 드래그, `Ctrl`/`Shift` 다중 선택으로 여러 포트를 동시에 잡고 마우스 커서로 모여드는 다중 베지어 번들 곡선을 드래그하여 연결할 수 있습니다.
- **미사용 I/O 포트 숨기기 & 선택 복원**: 입출력 포트를 우클릭하여 불필요한 포트를 숨겨 복잡한 다출력 머신 카드를 깔끔하게 정리하며, 하단 알약 배지를 클릭하여 원하는 포트를 원클릭으로 다시 표시할 수 있습니다.
- **캔버스 네비게이션 & 서브그래프**: 16px 정밀 격자 흡착(`G`), 키보드 퀵 페이지 스위처(`Ctrl + K`), 계층형 폴더 트리 드로어, 복합 서브모듈(`Ctrl + Shift + G`), 그룹 프레임(`Ctrl + G`), 그리고 완전한 실행 취소/다시 실행(`Ctrl + Z` / `Ctrl + Y`)을 지원합니다.

### 2. 깊이 있는 그렉텍 & 멀티 모드 계산 엔진
- **그렉텍 모던 (GTCEu Modern)**: ULV~MAX 15개 전압 티어, Standard/Perfect/Lossless 오버클럭, 서브틱 CPS 배치, 듀얼 에너지 해치 계산을 지원합니다.
- **동적 하드웨어 애드온 & 멀티블록 연동**: 발열 코일(온도 보너스 및 속도 페널티), 병렬 해치 및 정전력(Absolute) 해치, 설정형 유지보수 해치(Max Speed / Max Eco), 터빈 로터 스펙을 하드코딩 없이 런타임에 연역 계산합니다.
- **멀티 모드 동력 & 물리 모델**: **Create** & **Create: New Age**의 키네틱 발전기(대형 수차, 풍차, 스팀 엔진 SU/RPM)와 발전기 코일, **Thermal Series**의 다이내모 발전량 및 증강/티어 키트, **Systeams** / 보일러의 증기(mB/s) 소모량을 정확히 모델링합니다.
- **기준 기계(Anchor) 지정 & 정수 올림 역추적**: 병목 또는 목표 완성품 기계를 닻(Anchor)으로 지정하면 상류 공정 기계 대수를 정수($1, 2, 3...$) 올림(`Ceiling`)하여 $\text{Supply} \ge \text{Demand}$가 되도록 역추적 연산합니다.

### 3. 공장 전체 멀티블록 자재 명세서 (BOM) & 공유 기계 풀
- **자동 구조체 솔버 (BOM, `B` 단축키)**: 단일 탭, 공유 기계 풀 프레임, 복합 서브모듈에 배치된 모든 멀티블록과 단일 기계의 소요 자재(케이싱, 코일, 해치, 컨트롤러)를 일괄 산출합니다. 산출 목록을 EMI Recipe Tree, JEI++ 계산 목표로 등록하거나 클립보드로 복사할 수 있습니다.
- **공유 기계 풀 (Time-Sharing Frame)**: 여러 레시피를 하나의 프레임(`Ctrl + Shift + S`)으로 묶어 단일 물리 기계의 시간 분할 가동률(`Total Duty %`)과 올림(`Ceil`) 필요 기계 대수를 실시간 계산하고 중복 없는 BOM을 산출합니다.
- **하드웨어 사양 일괄 동기화**: 프레임 헤더 설정으로 전압 티어, 오버클럭 모드, 병렬 수, 애드온 사양을 프레임 내부 모든 기계에 원클릭으로 동기화합니다.

### 4. AE2 오토크래프팅 연동 & 목표 배치 생산 완료 시간 (ETA)
- **AE2 오토크래프팅 플랜 연동**: 공정 보드 페이지를 AE2 가공 패턴(Processing Pattern)과 1:1로 직접 매핑(`[💠 Bind AE2 Pattern]`)하여 AE2 제작 계획 확인 화면(`CraftConfirmScreen`) 상단에 예상 총 소요 시간(ETA), 소요 전력(EU), 병목 단계 배지를 오버레이로 표시합니다.
- **DAG 파이프라인 & 크리티컬 패스 ETA**: 다단계 오토크래프팅 작업을 유향 비순환 그래프(DAG)로 모델링하여 기계 병렬 수, 배치 주기, 파이프라인 지연 시간을 종합 반영한 정확한 완료 소요 시간을 계산합니다.
- **목표 배치 생산 수량 & 실시간 유량 ETA**: 단말 및 리라우트 노드에 목표 수량(예: `100x`, `1,000x`, `10 B`)을 지정하여 예상 잔여 완료 시간, 총 소요 전력, 원자재 총량을 실시간 산출합니다.

### 5. 실시간 멀티플레이어 팀 공유 워크스페이스
- **실시간 팀 협업 공정 설계**: 전용 서버에서 팀원들과 동일한 공정 플로우차트를 서브세컨드 동기화로 실시간 동시 설계할 수 있습니다.
- **팀 시스템 연동**: **FTB Teams**, **Phoenix Guilds**, **바닐라 스코어보드 팀** 시스템을 자동 감지하여 팀 권한과 멤버십을 연동합니다.
- **페이지별 동시 편집 락 & 실시간 접속자 표시**: 동시 수정 충돌을 방지하는 임시 편집 락과 실시간 팀원 열람 인디케이터, 3초 비활성 디바운스 자동 저장 및 개인 보드 복제(`Personal Board`)를 지원합니다.

### 6. 유량 포화도 분석 & 반응형 UI
- **유량 포화도 기반 와이어 애니메이션**: 솔버의 실시간 공급 포화율($R = \text{Supply} / \text{Demand}$)에 따라 배선 펄스 속도, 간헐적 정지(Duty Cycle Stutter), RGB 색상(Cyan $\to$ Amber $\to$ Crimson)을 변조하며, 원료 결핍 기계에 호박색 펄스 외곽선 글로우를 표시하여 공정 병목을 즉시 식별합니다.
- **정션 보이드 싱크 & 포트 단위 보이드 마킹**: 정션 노드를 무한 보이드 싱크(`SupplyMode.VOID_SINK`)로 설정하거나 출력 포트를 `Alt + 우클릭`하여 배관 구조를 끊지 않고 잉여 부산물을 흡수 및 순 생산품 결산에서 제외할 수 있습니다.
- **반응형 툴바 & 적응형 UI**: 좁은 해상도에서 타이틀 자동 축약 및 초과 버튼 오버플로우 메뉴화, 고밀도 애드온 리스트 뷰(`[▦ / ☰]`), 5단계 UI 가독성 배율(`[Aa 1.0x]`), 대규모 공정(1,200+ 노드)을 위한 LOD 줌아웃 최적화를 지원합니다.

---

## 조작법 및 단축키

| 기능 | 단축키 / 조작 | 설명 |
| :--- | :--- | :--- |
| **화면 이동 / 줌** | 우클릭·휠 클릭 드래그 / 마우스 휠 | 캔버스 자유 이동 및 커서 중심 확대/축소 |
| **빠른 레시피 추가** | `Space` 키 또는 빈 공간 더블클릭 | 레시피 검색 및 노드 생성 창 열기 |
| **단축키 안내 HUD** | `H` | 인게임 실시간 단축키 가이드 오버레이 토글 |
| **멀티블록 BOM 창** | `B` | 공장 전체 자재 명세서 다이얼로그 열기 |
| **퀵 페이지 스위처** | `Ctrl + K` | 퍼지 검색으로 보드 페이지 간 즉시 전환 |
| **실행 취소 / 다시 실행** | `Ctrl + Z` / `Ctrl + Y` (또는 `Ctrl + Shift + Z`) | 캔버스 조작 이전 상태로 되돌리기 / 재실행 |

<details>
<summary><b>전체 조작법 및 단축키 목록 (클릭하여 펼치기)</b></summary>

| 기능 | 조작 방법 |
| :--- | :--- |
| 계산기 보드 열기 | 마인크래프트 조작 설정에서 키 지정 |
| 화면 이동 (Pan) | 우클릭 드래그 또는 휠 클릭 드래그 |
| 화면 줌 (Zoom) | 마우스 휠 (커서 중심 줌) |
| 빠른 레시피 추가 | 빈 캔버스에서 Space 키 또는 더블클릭 |
| 선택 박스 (Marquee) | 빈 공간 클릭 드래그로 사각형 영역 선택 (Shift + 클릭으로 개별 토글) |
| 다중 노드 이동 | 선택된 노드 중 하나를 잡고 드래그하여 일괄 이동 |
| 프레임 생성 | Ctrl + G |
| 공유 기계 풀 생성 | Ctrl + Shift + S |
| 복합 모듈화 압축 | Ctrl + Shift + G |
| 퀵 페이지 스위처 | Ctrl + K (퍼지 검색 및 즉시 점프) |
| 16px 격자 스냅 토글 | G 키 또는 좌하단 HUD 체크박스 |
| 실행 취소 / 다시 실행 | Ctrl + Z (실행 취소) / Ctrl + Y 또는 Ctrl + Shift + Z (다시 실행) |
| 클립보드 작업 | Ctrl + C (복사) / Ctrl + X (잘라내기) / Ctrl + V (붙여넣기) / Ctrl + D (즉시 복제) |
| 전체 노드 선택 | Ctrl + A |
| 선택 노드 삭제 | Delete 또는 Backspace |
| 기계 이름 직접 수정 | 기계 카드 헤더 이름 텍스트 더블클릭 |
| 단축키 안내 HUD 토글 | H |
| 멀티블록 BOM 다이얼로그 토글 | B |
| 파이프라인 연결 | 포트 클릭 드래그 -> 대상 포트에 드롭 |
| 드래그 앤 검색 & 자동 연결 | 포트에서 빈 캔버스로 드래그 -> 레시피 선택 |
| 스마트 자동 맞춤 연결 | Shift + 드래그 연결 (정수 올림 1:1 맞춤) |
| 목표 생산량 역산 대화창 | 출력 포트 Ctrl + 좌클릭 |
| 맥락형 컨텍스트 메뉴 | 빈 캔버스, 노드, 포트, 선택 영역 우클릭 |
| 언리얼 패닝 및 대각선 이동 | 우클릭 드래그 또는 WASD / 방향키 (Shift 가속) |
| 와이어 / 포트 연결 해제 | 연결선 또는 포트 소켓 우클릭 |
| 보이드 처리 토글 | 출력 포트 Alt + 우클릭 |
| 인플레이스 레시피 전환 | 기계 설정창의 [🔄 Switch Recipe] 또는 노드 우클릭 메뉴 |
| 시간 단위계 순환 | T (/s -> /min -> /h -> /d -> /t -> 1x 1회 배치) |
| 전역 유체 단위계 순환 | Shift + T (Auto -> Always mB -> Always B) |
| 기계 설정창 UI 배율 조절 | [Aa 1.0x] 버튼 클릭(좌/우), 휠 스크롤, 또는 [+] / [-] 키 |
| 기준 기계(Anchor) 지정 | 기계 카드 헤더의 닻(Anchor) 아이콘 클릭 |
| 기계 대수 직접 수정 | 대수 박스 클릭 -> 숫자 입력 -> Enter / Esc, 또는 [-] / [+] / [/2] / [x2] |
| 병렬 수치(Par) 조절 | [Par] 클릭 후 숫자 입력, 마우스 휠 스케일, 우클릭 시 1/2 |
| 터빈 로터 장착 | 로터 버튼 클릭 -> 로터 선택창에서 선택 |
| 전압 티어 변경 | 전압 티어 버튼(LV, MV, HV...) 클릭 또는 마우스 휠 |
| 오버클록 모드 전환 | [STD OC] / [PERF OC] 버튼 클릭 |
| 텍스트 블루프린트 공유 | 상단 툴바의 [공유] / [가져오기] 버튼 클릭 |
| 레시피 / 사용처 조회 | 포트에 마우스 올리고 R(레시피) 또는 U(사용처) 키 |
| 탭 및 툴바 스크롤 | 마우스 휠 또는 [«] / [»] 오버플로우 화살표 클릭 |

</details>

---

## 설치 및 서버 호환성 안내 (Client & Server Optional)

그렉텍 계산기 보드는 클라이언트와 서버 양쪽 모두에서 완전히 선택적으로 설치하여 동작할 수 있습니다:

- **클라이언트에만 설치된 경우 (바닐라/모드 없는 서버 접속)**: 서버에 본 모드가 설치되어 있지 않아도 자유롭게 서버에 접속하여 개인 계산 보드를 정상 사용할 수 있습니다.
- **서버에만 설치된 경우 (모드 없는 클라이언트 접속)**: 서버 관리자가 서버에 모드를 설치해도 모드가 없는 일반 유저들의 접속이 차단되지 않습니다.
- **클라이언트와 서버 양쪽에 모두 설치된 경우**: 두 곳 모두에 설치되면 실시간 멀티플레이어 팀 워크스페이스 동기화 기능이 활성화되어 팀원들과 실시간으로 공정을 함께 설계할 수 있습니다.

---

## 요구 사양 및 모드 호환성 (Requirements & Mod Compatibility)

- **마인크래프트**: `1.20.1`
- **모드 로더**: `Minecraft Forge (47.2.0+)`
- **필수 모드**: **없음** (완전 독립 구동 가능, 0 필수 종속성)
- **지원 레시피 뷰어**:
  - [EMI](https://curseforge.com/minecraft/mc-mods/emi) (1.1.24+ / 권장)
  - [JEI (Just Enough Items)](https://curseforge.com/minecraft/mc-mods/jei) (15.2.0+) / [JEI Unofficial](https://curseforge.com/minecraft/mc-mods/jei-unofficial)
  - [JEI++ (Just Enough Calculation)](https://curseforge.com/minecraft/mc-mods/just-enough-calculation) (BOM 레시피 트리 목표 등록)
- **지원 산업 및 팩토리 모드**:
  - [GregTech CEu Modern](https://curseforge.com/minecraft/mc-mods/gregtech-ceu-modern) (1.7.0+ 또는 Star Technology 포크)
  - [Applied Energistics 2 (AE2)](https://curseforge.com/minecraft/mc-mods/applied-energistics-2) (오토크래프팅 패턴 바인딩 & DAG 완료 시간 연산)
  - [Create](https://curseforge.com/minecraft/mc-mods/create) (0.5.1 / 6.0+)
  - [Create: New Age](https://curseforge.com/minecraft/mc-mods/create-new-age)
  - [Greate](https://curseforge.com/minecraft/mc-mods/greate) (티어별 키네틱 기계 및 샤프트 연동)
  - [Thermal Series](https://curseforge.com/minecraft/mc-mods/thermal-expansion) (Expansion, Foundation, Cultivation)
  - [Systeams](https://curseforge.com/minecraft/mc-mods/systeams)
- **멀티플레이어 팀 동기화 (선택)**:
  - [FTB Teams](https://curseforge.com/minecraft/mc-mods/ftb-teams-forge) (전용 서버 멀티플레이어 팀 워크스페이스 공유)
  - [Phoenix Guilds](https://curseforge.com/minecraft/mc-mods/phoenix-guilds) (팀 및 길드 시스템 연동)

---

## 소스코드 빌드

```bash
git clone https://github.com/Amvermain/GregTechCalculatorBoard.git
cd GregTechCalculatorBoard
./gradlew build
```
컴파일된 jar 파일은 `build/libs/gtcalcboard-1.20.1-2.2.0-alpha.2.jar` 경로에 생성됩니다.

---

## 개발자 & 아키텍처 문서

- **[Architecture & Developer Guide (English)](docs/ARCHITECTURE.md)**: Internal solver engine, 4-tier architecture, multi-mod physical models, and rendering pipeline.
- **[아키텍처 및 개발자 가이드 (한국어)](docs/ARCHITECTURE_KR.md)**: 내부 엔진 구조, 4계층 아키텍처, 멀티 모드 물리 모델 및 렌더링 파이프라인.
- **[Detailed Code Specification (English)](docs/en_us/CODE_SPECIFICATION.md)**: Full system architecture, 5 graph algorithms, overclocking formulas, `CategoryCapabilityMatrix`, multiplayer concurrency lock protocol, and SavedData persistence schemas.
- **[세부 코드 명세서 (한국어)](docs/ko_kr/CODE_SPECIFICATION.md)**: 전체 시스템 아키텍처, 5대 그래프 알고리즘, 오버클럭 연산 공식, `CategoryCapabilityMatrix`, 멀티플레이 동시성 락 프로토콜 및 영속화 스키마.
- **[아키텍처 결정 기록 (ADR)](docs/adr/README.md)**: 시스템 아키텍처 결정 내역 및 변경 기록.

---

## 커뮤니티 & 지원 (Community & Support)

- **Discord**: [공식 디스코드 서버 가입](https://discord.gg/NaJWk3UjJN) - 공장 청사진 공유, 질문 및 커뮤니티 소통.
- **이슈 트래커**: [GitHub Issues](https://github.com/Amvermain/GregTechCalculatorBoard/issues) - 버그 제보 및 기능 제안.

---

## Special Thanks (감사의 말)

- **The Reel One** - 계산기 보드의 사용성 개선을 위한 UX/UI 피드백, 디자인 제안 및 지속적인 테스트를 지원해 주셨습니다.
- **rafaelpnsm** - 계산기 보드의 사용성 개선을 위한 UX/UI 피드백, 디자인 제안 및 지속적인 테스트를 지원해 주셨습니다.

---

## Inspirations (영감을 받은 프로젝트)

- [SatisFlow](https://satisflow.app/) - 노드 그래프 기반의 Satisfactory 웹 공정 계산기 및 플로우차트 플래너.
- [Foreman 2](https://github.com/DanielKote/Foreman2) - Factorio의 노드 기반 시각적 플로우차트 생산 라인 계산기.
- [Helmod](https://mods.factorio.com/mod/helmod) - Factorio의 대표적인 인게임 공정 계산 및 레시피 매트릭스 솔버 모드.

---

## 라이선스

MIT License - 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.
