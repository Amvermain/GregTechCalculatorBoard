# GregTech Calculator Board (GTCalcBoard)

<p align="center">
  <a href="README.md">English</a> | <b>한국어</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg" alt="Minecraft 1.20.1">
  <img src="https://img.shields.io/badge/Loader-NeoForge%20%2F%20Forge%2047.2.0+-orange.svg" alt="Forge">
  <img src="https://img.shields.io/badge/GregTech-CEu%20Modern-blue.svg" alt="GTCEu Modern">
  <img src="https://img.shields.io/badge/EMI-Supported-purple.svg" alt="EMI">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License">
</p>

그렉텍(GregTech CEu Modern) 공정 짤 때마다 엑셀이나 외부 계산기 사이트 왔다 갔다 하기 귀찮아서 만든 인게임 노드 그래프 계산기 모드입니다. 팩토리오나 셰이프즈처럼 무한 캔버스에 레시피들을 올려두고 선으로 슥슥 연결하면, 필요한 기계 대수와 전력, 잉여/부족 원자재를 알아서 딱 맞게 계산해 줍니다.

---

## 주요 기능

### 1. 인터랙티브 노드 캔버스
- 마우스 휠과 드래그로 넓은 화면을 편하게 줌/팬하면서 공정을 배치할 수 있습니다.
- 각 기계 카드에서 처리 시간, 오버클럭 단계, 정확한 EU/t 소모량, 초당 생산/소비량을 바로 확인합니다.
- EMI 완전 연동: 레시피 창에서 바로 보드로 가져오거나, 포트에 마우스 올리고 `R`(레시피) / `U`(사용처) 키를 눌러 바로 찾아볼 수 있습니다.

### 2. 스마트 연결선 & 자동 연결
- 출력 포트에서 입력 포트로 마우스를 끌어다 놓으면 깔끔한 곡선 파이프라인이 연결됩니다.
- **자동 연결(Auto Connect)** 버튼을 누르면 보드에 올려둔 기계들 중 아이템/유체가 서로 맞는 포트들을 알아서 한 번에 싹 이어줍니다.
- 선이나 포트를 우클릭하면 바로 연결이 끊어집니다.

### 3. 기준 레시피 지정 & 자동 비율 맞춤 (Auto Ratio)
- 생산량을 맞추고 싶은 핵심 기계나 최종 완성품을 **기준(Base)**으로 지정할 수 있습니다.
- **비율 맞춤(Auto Ratio)** 버튼을 누르면 지정한 기준 기계의 생산/소비량에 맞춰 연결된 앞뒤 모든 기계 대수를 소수점 단위까지 정확하게 역산해서 맞춰줍니다.

### 4. 최대 처리량 최적화 (Max Flow) & 전압 티어 제한
- **티어 상한(Cap: LV ~ MAX)**을 걸어둘 수 있어서, 현재 테크 단계에서 못 만드는 상위 티어 기계로 계산되는 걸 방지할 수 있습니다.
- **최대 처리(Max Flow)** 버튼을 누르면 설정한 상한 티어까지 오버클럭하고, 대형 멀티블록용 병렬(4x, 8x, 16x...)로 묶어서 기계 대수를 최소화해 줍니다.

### 5. 블루프린트 공유 기능
- **공유(Ctrl + C)**: 현재 화면의 배치, 전선, 기계 대수, 티어 설정을 한 줄짜리 공유 코드(`GTBOARD:...`)로 클립보드에 복사합니다.
- **가져오기(Ctrl + V)**: 다른 사람이 공유한 코드를 복사하고 붙여넣기만 하면 1초 만에 그대로 불러옵니다.
- 디스코드나 커뮤니티에 공정 설계 공유할 때 편하게 쓸 수 있습니다.

### 6. 실시간 공정 요약
- 전체 공정 가동 시 총 소모 전력(EU/t)과 최고 요구 전압을 실시간으로 표시합니다.
- 외부에서 넣어줘야 하는 순수 원자재 투입량과 최종 생산물/부산물 목록을 깔끔하게 요약해 줍니다.

---

## 조작법 및 단축키

| 기능 | 조작 방법 |
| :--- | :--- |
| 계산기 보드 열기 | 마인크래프트 조작 설정에서 키 지정 |
| 화면 이동 (Pan) | 우클릭 드래그 또는 휠 클릭 드래그 |
| 화면 줌 (Zoom) | 마우스 휠 |
| 파이프라인 연결 | 출력 포트 클릭 드래그 -> 입력 포트에 드롭 |
| **비율 자동 맞춤 연결** | **Shift + 출력 포트 드래그 -> 입력 포트에 드롭** (앞 기계 생산량에 맞춰 뒤 기계 대수 자동 계산) |
| 연결 끊기 | 전선이나 포트 소켓 우클릭 |
| 기준 기계 지정 | 노드 상단 `[Base]` 아이콘 클릭 |
| 기계 대수 직접 입력 | 숫자 박스 클릭 -> 숫자 타이핑 -> `Enter` 또는 `Esc` |
| 대수 빠른 조절 | `[-]` -1, `[+]` +1, `[/2]` 절반, `[x2]` 2배 |
| 전압 티어 변경 | 티어 버튼 클릭 또는 마우스 휠 스크롤 |
| 오버클럭 모드 전환 | `[STD OC]` / `[PERF OC]` 버튼 클릭 |
| 병렬 수 조절 | `[Par: 1x]` 버튼 클릭 |
| 블루프린트 복사 | 상단 `[Share]` 버튼 또는 `Ctrl + C` |
| 블루프린트 불러오기 | 상단 `[Import]` 버튼 또는 `Ctrl + V` |
| EMI 레시피/사용처 조회 | 포트에 마우스 올리고 `R` (레시피) / `U` (사용처) |
| 상단 툴바 스크롤 | 툴바 위에서 마우스 휠 또는 클릭 드래그 |

---

## 요구 사항

- **마인크래프트**: `1.20.1`
- **모드 로더**: `Forge (47.2.0+)` / `NeoForge`
- **필수 모드**: 없음
- **권장 모드**:
  - [GregTech CEu Modern](https://curseforge.com/minecraft/mc-mods/gregtech-ceu-modern)
  - [EMI](https://curseforge.com/minecraft/mc-mods/emi)

> 참고: 그렉텍에 대한 강제적인 의존성은 없으나, 본 모드는 그렉텍(GTCEu)의 전압 티어와 오버클럭 메커니즘을 기준으로 설계되었으며 GregTech CEu Modern이 설치되지 않은 환경에서는 충분히 테스트되지 않았습니다.

---

## 소스코드 빌드

```bash
git clone https://github.com/Amvermain/GregTechCalculatorBoard.git
cd GregTechCalculatorBoard
./gradlew build
```
빌드된 파일은 `build/libs/gtcalcboard-1.20.1-1.0.1.jar`에 생성됩니다.

---

## 라이선스

MIT License - 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.
