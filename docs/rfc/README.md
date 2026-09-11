# 활성 아키텍처 제안 (Active RFCs)

본 디렉터리는 **GregTech Calculator Board (GTCalcBoard)**의 새로운 주요 기능 기획, 아키텍처 대안 탐색 및 승인을 위한 **과도기적 제안 문서(Request for Comments, RFC)** 보관소입니다.

---

## 🏛 RFC 생명주기 및 승격 규칙 (Lifecycle & Promotion)

```mermaid
flowchart LR
    DRAFT["1. RFC 기안 (docs/rfc/)"] --> REVIEW["2. 설계 검토 및 승인"]
    REVIEW --> IMPL["3. TDD 구현 및 단위 테스트"]
    IMPL --> PROMOTE["4. 공식 ADR 승격 (docs/adr/)"]
    PROMOTE --> CLEANUP["5. 임시 RFC 문서 정리"]
```

1. **기안 (`PROPOSED`)**: 새로운 주요 기능이나 아키텍처 개편을 추진할 때 `RFC_<NUMBER>_<NAME>.md` 형식으로 작성합니다.
2. **승인 (`ACCEPTED`)**: 설계 리뷰를 거쳐 구현 목표 버전과 방향이 확정됩니다.
3. **구현 및 승격 (`docs/adr/`)**:
   - 단위 테스트 통과 및 기능 구현이 완료되면 공식 **아키텍처 결정 기록(ADR)**으로 승격되어 [`docs/adr/`](../adr/)에 영구 보존됩니다.
   - 승격 상세 절차는 [`.agents/skills/rfc-implementation/SKILL.md`](../../.agents/skills/rfc-implementation/SKILL.md)를 참조하십시오.

---

## 📋 현재 활성 RFC 목록

| 문서 번호 | RFC 제목 | 상태 (Status) | 목표 버전 | 기안일 | 핵심 제안 요약 |
| :---: | :--- | :---: | :---: | :---: | :--- |
| **[RFC-013](RFC_013_MODULAR_COMBUSTION_COMPLEX_INTEGRATION.md)** | Star Technology 모듈러 연소 복합체(Modular Combustion Complex) 및 프레임 부스팅 발전 시스템 통합 명세 | 🟡 `PARTIALLY_IMPLEMENTED` | `v2.2.0` | 2026-09-02 | Trait 기반 물리/승수(5A~12A, 냉각 1.2x/1.4x) 및 머신 설정 UI 통합 완료(Phase 1), 부수 유체 입력 주입 대기(Phase 2) |
| **[RFC-045](RFC_045_RECIPE_NODE_COMPOSITION_DECOMPOSITION.md)** | RecipeNode 역할 컴포지션 분해 및 불변 계산 스냅샷 아키텍처 명세 | ⚪ `PROPOSED` | `v2.3.0` | 2026-09-11 | RecipeNode를 순수 캔버스 엔티티로 슬림화하고 4대 역할(Machine, Module, Junction, BoundaryPin)을 INodeRole 컴포지션으로 분리, NBT 100% 역호환 및 불변 계산 스냅샷 모델 연계 |
| **[RFC-046](RFC_046_BOARD_PAGE_PROVIDER_ABSTRACTION.md)** | 멀티 워크스페이스 통합 페이지 공급자 추상화 명세 | ⚪ `PROPOSED` | `v2.3.0` | 2026-09-11 | MultiblockBOMDialog 및 전역 UI의 ClientWorkspaceState 정적 싱글톤 결합을 IBoardPageProvider SPI 인터페이스로 추상화하여 로컬/원격 페이지 투명 공급 및 헤드리스 테스트 용이성 확보 |
| **[RFC-047](RFC_047_COMPAT_DETERMINISTIC_EXACT_MATCH_NORMALIZATION.md)** | 외부 모드 호환 계층 레거시 폴백 제거 및 Rule 5 결정론적 정규화 명세 | ⚪ `PROPOSED` | `v2.3.0` | 2026-09-11 | Create 시퀀스 조립, 스레딩 모디파이어, 에너지 해치 오프라인 티어, 서멀 다이내모 등 과거 작성된 5개 폴백의 문자열 contains 휴리스틱을 완전 제거하고 ResourceLocation Exact Match 테이블 및 강타입 클래스 검사로 100% 전환 |
