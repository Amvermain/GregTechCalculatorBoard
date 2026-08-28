# GregTech Calculator Board (GTCalcBoard)

<p align="center">
  <a href="README.md">English</a> | <b>한국어</b> | <a href="CHANGELOG_KR.md">변경 로그</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg" alt="Minecraft 1.20.1">
  <img src="https://img.shields.io/badge/Loader-Forge%2047.2.0+-orange.svg" alt="Forge">
  <img src="https://img.shields.io/badge/GregTech-CEu%20Modern-blue.svg" alt="GTCEu Modern">
  <img src="https://img.shields.io/badge/Recipe%20Viewer-EMI%20%2F%20JEI%20Supported-purple.svg" alt="EMI & JEI">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License">
</p>

그렉텍(GregTech CEu Modern), EMI, JEI 및 다양한 기술 모드 생태계를 위한 인게임 노드 그래프 계산기 및 플로우차트 에디터 모드입니다. 외부 스프레드시트나 웹 계산기에 의존하지 않고 마인크래프트 게임 내에서 복잡한 다단계 생산 공정을 직접 설계하고 수치를 완벽하게 맞출 수 있습니다.

---

## 주요 기능

### 1. 통합 플러그형 레시피 뷰어 SPI (EMI, JEI & 바닐라)
- **다중 레시피 뷰어 호환**: **EMI**, **JEI (Just Enough Items)**, **JEI++ (Just Enough Calculation / BoM)** 모드를 모두 지원하며 런타임에 최적의 뷰어를 자동 선출합니다.
- **즐겨찾기 사이드 독 (`[⭐ 즐겨찾기 (N) ▶]`)**: 화면 좌측 접이식 패널을 통해 북마크한 레시피를 1클릭 또는 드래그 앤 드롭으로 캔버스에 즉시 배치.
- **네이티브 레시피 추가 (`[+]` 버튼)**: EMI 레시피 화면에서 `[+]` 버튼을 클릭하면 활성 보드 캔버스에 노드가 즉시 생성됩니다.
- **순수 바닐라 폴백**: 레시피 뷰어 모드가 없는 환경에서도 바닐라 `RecipeManager` 기반으로 크래시 없이 노드 생성 및 조작 지원.

### 2. 인플레이스 대체 레시피 스위칭
- **원클릭 대체 레시피 전환**: 노드를 삭제하지 않고 기계 설정창의 `[🔄 Switch Recipe]` 버튼이나 우클릭 메뉴에서 동일 머신/산출물 대체 레시피로 즉시 교체.
- **스마트 와이어 보존 (Smart Wire Preservation)**: 레시피 교체 전후 동일한 아이템 및 유체 포트(`IngredientStack.getId()`)의 배선 연결을 끊지 않고 자동 유지.
- **완벽한 Undo / Redo**: 히스토리 델타 스택(`Ctrl + Z` / `Ctrl + Y`)과 완벽 연동.

### 3. 멀티블록 구조체 자동 분석 & 자재 명세서 (BOM) 시스템
- **자동 구조체 솔버 (`B` 단축키 / `[📦 BOM]`)**: 단일 탭 또는 전체 보드에 배치된 모든 멀티블록과 단일 기계의 소요 자재(케이싱, 코일, 해치, 컨트롤러)를 일괄 산출.
- **하이브리드 해치 오버라이드**: 듀얼 하위 티어 에너지 해치(`⚡ 1x Normal ↔ 2x 1-Tier Lower`) 토글 및 유틸리티 해치 장착 시 케이싱 자동 감산.
- **1-클릭 레시피 트리 목표 등록**: 산출된 자재 목록을 EMI Recipe Tree 또는 JEI++ 목표로 원클릭 등록하거나 클립보드 복사 지원.

### 4. 목표 배치 생산 소요 시간 (ETA) 산출 시스템
- **목표 생산량 지정**: 단말 및 리라우트(Reroute) 노드에 목표 배치 수량(예: `100x`, `1,000x`, `10 B`)을 지정.
- **실시간 완료 시간 산출**: 상류 공급 유량을 분석하여 예상 완료 시간(`ET: 24m 52s`) 배지 및 배치 완수 시 총 소요 전력(EU)과 원자재 총량 툴팁 표시.

### 5. 멀티플레이어 팀 공유 워크스페이스
- **실시간 협업 공정 설계**: 전용 서버에서 팀원들과 동일한 공정 보드를 실시간으로 공유하고 함께 편집할 수 있습니다.
- **FTB Teams, Phoenix Guilds & 바닐라 스코어보드 팀 연동**: 서버의 팀 생성, 권한 및 멤버십 시스템을 자동으로 인식합니다.
- **페이지별 편집 락 & 실시간 접속자 표시**: 동시 수정 충돌을 방지하는 임시 편집 락 및 팀원 접속 상태/현재 열람 중인 탭 표시.
- **무마찰 실시간 자동 저장**: 3초 비활성 디바운스 자동 저장 및 화면 종료 시 즉시 저장, 과거 버전 열람 및 개인 보드로 1클릭 복제(`Personal Board`) 지원.

### 6. 조건부 파라메트릭 레시피 검색 & 컨텍스트 자동 배선
- **불리언 및 태그 기반 검색 엔진**: 다중 키워드 AND(`&`/공백), OR(`|`), NOT(`!`), 모드명(`@gtceu`), 태그(`#logs`), 기계 종류(`[pyrolyse_oven]`), 정확 일치(`"..."`) 지원.
- **드래그 앤 검색 & 자동 배선**: 포트에서 빈 캔버스로 선을 끌어놓으면 소비/생산 공정을 자동 검색하고, 1클릭으로 노드 생성 및 와이어 자동 연결/비율 맞춤(`Shift`)을 완료합니다.

### 7. 멀티 모드 동력 & 물리 모델 지원
- **그렉텍 모던 (GTCEu Modern)**: ULV~MAX 15개 전압 티어, Standard/Perfect/Lossless 오버클럭, 서브틱 CPS 배치, 듀얼 에너지 해치 지원.
- **크리에이트 & 뉴에이지 (Create & New Age)**: 키네틱 발전기(대형 수차, 풍차, 스팀 엔진 SU/RPM), 전기 모터, 발전기 코일, 자석 링 연산.
- **써멀 시리즈 (Thermal Series)**: 다이내모 RF/t 발전량, 증강 키트 배수, 티어 업그레이드 키트 지원.
- **시스팀즈 & 보일러 (Systeams & Steam Boilers)**: 보일러 증기(mB/s) 출력, 증기 다이내모, LP/HP 증기 가공 기계 소모량 연산.

### 8. 접근성 & 캔버스 제어
- **5단계 UI 가독성 배율 (FontScale)**: 기계 설정창 우측 상단 `[Aa 1.0x]` 버튼, 휠 스크롤, `+`/`-` 키로 `0.75x`, `0.85x`, `1.0x`, `1.15x`, `1.30x` 실시간 확대/축소.
- **전역 유체 단위 표기 통일 (FluidUnitMode)**: 툴바 버튼 또는 `Shift+T` 단축키로 캔버스 전체 유체 표기를 `자동(Auto)`, `항상 mB`, `항상 버킷(B)`으로 일괄 전환.
- **페이지 탭 오버플로우 클릭 네비게이션**: 탭이 많을 때 좌/우 `«`, `»` 인디케이터 클릭 및 휠 스크롤로 매끄러운 탭 이동 지원.
- **Level of Detail (LOD) 성능 최적화**: 줌아웃 시 경량화 2D 렌더링으로 전환되어 1,200개 이상의 대규모 공정 노드에서도 부드러운 성능 유지.
- **싱글플레이 일시정지 토글**: `Pause: ON`과 `Pause: OFF`를 전환하여 공정 계획 중 게임 세계 일시정지 여부를 자유롭게 선택.

### 9. 다중 탭 프리셋 & 복합 모듈 (Subgraphs)
- **다중 탭 프리셋 관리**: 상단 탭 바로 공정 라인별 독립 보드를 관리.
- **복합 모듈화 압축 (`Ctrl + G`)**: 복잡한 하위 공정들을 단일 모듈 카드로 패키징(내부 부산물 자동 은닉, 비례 스케일링)하고 언제든 원상복구(`펼치기`) 가능.

### 10. 실행 취소 / 다시 실행 & 클립보드 시스템
- **작업 히스토리**: 노드 추가/삭제/이동, 와이어 연결, 속성 편집 등 모든 캔버스 조작에 대해 `Ctrl + Z`(취소) 및 `Ctrl + Y` / `Ctrl + Shift + Z`(재실행) 지원.
- **영역 선택 및 클립보드**: 드래그 박스 다중 선택, 일괄 이동, 잘라내기(`Ctrl + X`), 복사(`Ctrl + C`), 붙여넣기(`Ctrl + V`), 즉시 복제(`Ctrl + D`).

### 11. 기준 레시피 지정 & 정수 올림 비율 맞춤 (Auto Ratio)
- 핵심 기계나 최종 완성품을 기준(Anchor)으로 지정.
- **비율 맞춤(Auto Ratio)**: 기준 기계의 생산/소비량에 맞춰 연결된 기계 대수를 정수($1, 2, 3...$) 올림(`Ceiling`)하여 역추적 계산 ($\text{Supply} \ge \text{Demand}$).

### 12. 동적 애드온 및 하드웨어 최적화 (`DynamicAddonCrawler`)
- **무설정 애드온 자동 탐색**: 하드코딩 없이 런타임에 설치된 모든 그렉텍 애드온 및 모드팩 커스텀 코일/로터/해치/기계를 자동으로 감지하고 인덱싱.
- **발열 코일**: 쿠프로니켈(1800K)부터 트리늄(9000K+) 및 모드팩 커스텀 코일 온도를 자동 인식하여 처리 시간 단축 및 속도 페널티를 계산.
- **병렬 해치**: 일반 병렬 해치 및 정전력(Absolute) 병렬 해치 지원, 숫자 직접 입력(`Par`) 및 마우스 휠 스케일링.
- **설정형 유지보수 해치 (CMH)**: Max Speed(시간 0.9배 가속) 및 Max Eco(시간 1.1배 친환경) 유지보수 모드 지원.

---

## 조작법 및 단축키

| 기능 | 조작 방법 |
| :--- | :--- |
| 계산기 보드 열기 | 마인크래프트 조작 설정에서 키 지정 |
| 화면 이동 (Pan) | 우클릭 드래그 또는 휠 클릭 드래그 |
| 화면 줌 (Zoom) | 마우스 휠 (커서 중심 줌) |
| 빠른 레시피 추가 | 빈 캔버스에서 Space 키 또는 더블클릭 |
| 선택 박스 (Marquee) | 빈 공간 클릭 드래그로 사각형 영역 선택 (Shift + 클릭으로 개별 토글) |
| 다중 노드 이동 | 선택된 노드 중 하나를 잡고 드래그하여 일괄 이동 |
| 복합 모듈화 압축 | Ctrl + G |
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
| 와이어 / 포트 연결 해제 | 연결선 또는 포트 소켓 우클릭 |
| 인플레이스 레시피 전환 | 기계 설정창의 [🔄 Switch Recipe] 또는 노드 우클릭 메뉴 |
| 시간 단위계 순환 | T (/s -> /min -> /h -> /d -> /t) |
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
  - [JEI (Just Enough Items)](https://curseforge.com/minecraft/mc-mods/jei) (15.2.0+)
  - [JEI++ (Just Enough Calculation)](https://curseforge.com/minecraft/mc-mods/just-enough-calculation) (BOM 레시피 트리 목표 등록)
- **지원 산업 및 팩토리 모드**:
  - [GregTech CEu Modern](https://curseforge.com/minecraft/mc-mods/gregtech-ceu-modern) (1.7.0+ 또는 Star Technology 포크)
  - [Create](https://curseforge.com/minecraft/mc-mods/create) (0.5.1 / 6.0+)
  - [Create: New Age](https://curseforge.com/minecraft/mc-mods/create-new-age)
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
빌드된 파일은 `build/libs/gtcalcboard-1.20.1-2.0.0-alpha.11.jar`에 생성됩니다.

---

## 개발자 & 아키텍처 문서

- **[Architecture & Developer Guide (English)](docs/ARCHITECTURE.md)**: Internal solver engine, 4-tier architecture, multi-mod physical models, and rendering pipeline.
- **[아키텍처 및 개발자 가이드 (한국어)](docs/ARCHITECTURE_KR.md)**: 내부 엔진 구조, 4계층 아키텍처, 멀티 모드 물리 모델 및 렌더링 파이프라인.
- **[Detailed Code Specification (English)](docs/en_us/CODE_SPECIFICATION.md)**: Full system architecture, 5 graph algorithms, overclocking formulas, `CategoryCapabilityMatrix`, multiplayer concurrency lock protocol, and SavedData persistence schemas.
- **[세부 코드 명세서 (한국어)](docs/ko_kr/CODE_SPECIFICATION.md)**: 전체 시스템 아키텍처, 5대 그래프 알고리즘, 오버클럭 연산 공식, `CategoryCapabilityMatrix`, 멀티플레이 동시성 락 프로토콜 및 영속화 스키마.
- **[v2.0 개발 계획서 (Development Plan)](docs/DEVELOPMENT_PLAN_V2.md)**: 멀티플레이어 및 FTB Teams 연동 로드맵.

---

## Special Thanks (감사의 말)

- **The Reel One** - 계산기 보드의 사용성 개선을 위한 UX/UI 피드백, 디자인 제안 및 지속적인 테스트를 지원해 주셨습니다.

---

## 라이선스

MIT License - 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.
