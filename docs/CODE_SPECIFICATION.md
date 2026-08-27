# GregTech Calculator Board (GTCalcBoard) 세부 기술 명세서 시리즈

본 문서는 **GregTech Calculator Board (GTCalcBoard)**의 내부 아키텍처, 핵심 수학 엔진, 5대 그래프 알고리즘, 렌더링 파이프라인, UI 컴포넌트 와이어프레임, 멀티플레이어 동시성 락 프로토콜 및 영속화 스키마를 체계적으로 정리한 공식 개발자 기술 명세서(Master Index)입니다.

---

## 🌐 언어 선택 (Language Selection)

* 🇰🇷 **[한국어 명세서 전체 보기 (Korean Edition)](ko_kr/CODE_SPECIFICATION.md)**
* 🇺🇸 **[English Specification (English Edition)](en_us/CODE_SPECIFICATION.md)**

---

## 📌 문서 메타데이터 (Document Metadata)

| 항목 | 내용 |
| :--- | :--- |
| **문서 버전** | `2.0.0` (기준 커밋: `6c63984`) |
| **대상 플랫폼** | Minecraft 1.20.1 (Minecraft Forge 47.2.0+) |
| **의존성** | Java 17+, GregTech CEu Modern, EMI (Recipe Viewer) |
| **소프트 의존성** | FTB Teams (멀티플레이 팀 연동) |
| **지원 언어** | 한국어 (`ko_kr`), 영어 (`en_us`) |

---

## 📑 전체 명세서 순서별 목차 (Table of Contents)

```
docs/
├── ko_kr/ (한국어 문서)
│   ├── CODE_SPECIFICATION.md                # 📑 [한국어 마스터 인덱스]
│   └── spec/
│       ├── 00_OVERVIEW.md                   # 🏛️ [00] 시스템 아키텍처 개요 및 설계 원칙
│       ├── 01_CORE_DOMAIN_AND_MODELS.md     # 📦 [01] 코어 도메인 모델 및 수용능력 매트릭스
│       ├── 02_MATH_AND_ALGORITHMS.md        # 🧮 [02] 수학적 연산 엔진 및 그래프 해석 알고리즘
│       ├── 03_UI_AND_RENDERING_PIPELINE.md  # 🎨 [03] UI 및 캔버스 렌더링 파이프라인 개요
│       │   ├── 03_01_CANVAS_AND_NODE_CARDS.md       # 🖼️ [03-01] 2D 캔버스 & 노드 카드 렌더링
│       │   ├── 03_02_MACHINE_CONFIG_AND_ADDONS.md   # ⚙️ [03-02] 기계 상세 설정 & 애드온 랙 UI
│       │   ├── 03_03_PAGE_SUMMARY_AND_DASHBOARD.md  # 📊 [03-03] 페이지 결산 & 전역 밸런스 대시보드
│       │   └── 03_04_RECIPE_SEARCH_AND_TOOLS.md     # 🔍 [03-04] 검색, 필터, HUD, 가이드 & 토스트 UI
│       ├── 04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md # 🌐 [04] 멀티플레이어 동시성 제어 및 네트워크
│       └── 05_INTEGRATION_AND_I18N.md       # 🔌 [05] 외부 모드 연동 및 다국어 단위 시스템
└── en_us/ (English Edition)
    ├── CODE_SPECIFICATION.md                # 📑 [English Master Index]
    └── spec/
        ├── 00_OVERVIEW.md                   # 🏛️ [00] System Architecture & Design Principles
        ├── 01_CORE_DOMAIN_AND_MODELS.md     # 📦 [01] Core Domain Models & Capability Matrix
        ├── 02_MATH_AND_ALGORITHMS.md        # 🧮 [02] Math Engine & Graph Solving Algorithms
        ├── 03_UI_AND_RENDERING_PIPELINE.md  # 🎨 [03] UI & Rendering Pipeline Overview
        │   ├── 03_01_CANVAS_AND_NODE_CARDS.md       # 🖼️ [03-01] 2D Canvas & Node Card Rendering
        │   ├── 03_02_MACHINE_CONFIG_AND_ADDONS.md   # ⚙️ [03-02] Machine Config & Addon Rack UI
        │   ├── 03_03_PAGE_SUMMARY_AND_DASHBOARD.md  # 📊 [03-03] Page Summary & Dashboard UI
        │   └── 03_04_RECIPE_SEARCH_AND_TOOLS.md     # 🔍 [03-04] Recipe Search, Tools & Guide UI
        ├── 04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md # 🌐 [04] Multiplayer Concurrency & Network
        └── 05_INTEGRATION_AND_I18N.md       # 🔌 [05] External Mod Integrations & i18n Units
```

---

## 📖 한국어 챕터별 핵심 내용 바로가기

1. **[[00] 시스템 아키텍처 개요 및 설계 원칙](ko_kr/spec/00_OVERVIEW.md)**
2. **[[01] 코어 도메인 모델 및 결정론적 수용능력 매트릭스](ko_kr/spec/01_CORE_DOMAIN_AND_MODELS.md)**
3. **[[02] 수학적 연산 엔진 및 그래프 해석 알고리즘](ko_kr/spec/02_MATH_AND_ALGORITHMS.md)**
4. **[[03] UI 및 캔버스 렌더링 파이프라인 개요](ko_kr/spec/03_UI_AND_RENDERING_PIPELINE.md)**
   * **[[03-01] 2D 캔버스 & 노드 카드](ko_kr/spec/03_01_CANVAS_AND_NODE_CARDS.md)**
   * **[[03-02] 기계 상세 설정 & 애드온 랙 UI](ko_kr/spec/03_02_MACHINE_CONFIG_AND_ADDONS.md)**
   * **[[03-03] 페이지 결산 & 전역 밸런스 대시보드](ko_kr/spec/03_03_PAGE_SUMMARY_AND_DASHBOARD.md)**
   * **[[03-04] 검색, 필터, HUD, 가이드 & 토스트](ko_kr/spec/03_04_RECIPE_SEARCH_AND_TOOLS.md)**
5. **[[04] 멀티플레이어 동시성 제어 및 네트워크 프로토콜](ko_kr/spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)**
6. **[[05] 외부 모드 연동 및 다국어 단위 시스템](ko_kr/spec/05_INTEGRATION_AND_I18N.md)**

---

## 📖 English Chapter Links

1. **[[00] System Architecture & Design Principles](en_us/spec/00_OVERVIEW.md)**
2. **[[01] Core Domain Models & Capability Matrix](en_us/spec/01_CORE_DOMAIN_AND_MODELS.md)**
3. **[[02] Math Engine & Graph Solving Algorithms](en_us/spec/02_MATH_AND_ALGORITHMS.md)**
4. **[[03] UI & Rendering Pipeline Overview](en_us/spec/03_UI_AND_RENDERING_PIPELINE.md)**
   * **[[03-01] 2D Canvas & Node Card Rendering](en_us/spec/03_01_CANVAS_AND_NODE_CARDS.md)**
   * **[[03-02] Machine Config & Addon Rack UI](en_us/spec/03_02_MACHINE_CONFIG_AND_ADDONS.md)**
   * **[[03-03] Page Summary & Dashboard UI](en_us/spec/03_03_PAGE_SUMMARY_AND_DASHBOARD.md)**
   * **[[03-04] Recipe Search, Tools & Guide UI](en_us/spec/03_04_RECIPE_SEARCH_AND_TOOLS.md)**
5. **[[04] Multiplayer Concurrency & Network Protocols](en_us/spec/04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md)**
6. **[[05] External Mod Integrations & i18n Units](en_us/spec/05_INTEGRATION_AND_I18N.md)**
