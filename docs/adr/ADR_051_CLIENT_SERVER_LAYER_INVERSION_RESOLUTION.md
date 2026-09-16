# ADR-051: 팀 워크스페이스 공통 모델 분리 및 Client ➔ Server 계층 역전 해소 명세
(Common Team Workspace Model Separation & Client-to-Server Layer Inversion Resolution Specification)

- **문서 번호**: ADR-051
- **대상 버전**: `v2.3.0`
- **상태**: `IMPLEMENTED`
- **결정/완료일**: 2026-09-14
- **주관 계층**:
  - Common API Domain Layer (`api.team.TeamWorkspacePage`, `api.team.CommitLogEntry`)
  - Client GUI & State Layer (`client.team.ClientWorkspaceState`, `client.gui.BoardTeamSyncCoordinator`, `client.gui.dialog.MultiblockBOMDialog`, `client.gui.dialog.RecentSavesDialog`, `client.gui.widget.PageTabBarWidget`)
  - Network Packet Layer (`network.packet.c2s.*`, `network.packet.s2c.*`)
  - Server Storage Layer (`server.storage.TeamWorkspaceData`, `server.storage.TeamBoardSavedData`)

---

## 1. 개요 및 배경 (Motivation)

### 1.1 현황 분석 및 당면 과제 (Current Context & Smells)

`v2.2.0-beta.4` 및 `v2.2.1`에서 도입된 팀 워크스페이스 동기화 및 협업 시스템은 다수의 플레이어가 서버 상의 단일 워크스페이스에서 페이지를 공유하고 편집 로그를 추적할 수 있도록 지원합니다.

그러나 과거 팀 워크스페이스의 핵심 데이터 전송 모델(DTO)인 [`TeamWorkspacePage`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/api/team/TeamWorkspacePage.java)와 [`CommitLogEntry`](file:///d:/dev-ssd/modding/minecraft/GregTechCalculatorBoard/src/main/java/com/gtceu/calcboard/api/team/CommitLogEntry.java)가 **`com.gtceu.calcboard.server.storage` 패키지 내부에 배치**되어 있었습니다.

이로 인해 클라이언트 GUI 및 상태 관리 계층의 5개 핵심 클래스가 `server.*` 패키지를 직접 역참조하는 **계층 역전(Layer Inversion)** 문제가 발생하고 있었습니다:

```java
// ClientWorkspaceState.java
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;

// BoardTeamSyncCoordinator.java
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;

// MultiblockBOMDialog.java
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;

// RecentSavesDialog.java
import com.gtceu.calcboard.server.storage.CommitLogEntry;
// com.gtceu.calcboard.server.storage.TeamWorkspacePage remotePage = ...

// PageTabBarWidget.java
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
```

### 1.2 문제점 분석 (Problem Analysis)

1. **클린 아키텍처 및 계층 격리 원칙 위반**:
   - 아키텍처 원칙상 UI 및 클라이언트 전용 상태 계층(`client.*`)은 하위 도메인/API 계층(`api.*`)만을 참조해야 하며, 서버 전용 구현 계층(`server.*`)을 직접 참조해서는 안 됩니다.
   - 클라이언트 코드가 `server.*` 패키지를 직접 임포트할 경우, 정적 분석 도구나 모듈 의존성 검사기에서 아키텍처 결함으로 판정되며, 물리적 클라이언트/서버 빌드 분리 시 컴파일 의존성 꼬임이 발생합니다.
2. **모델 성격의 왜곡**:
   - `TeamWorkspacePage`와 `CommitLogEntry`는 서버 전용 로직(`ServerPlayer`, `ServerLevel` 등)을 전혀 포함하지 않는 순수 데이터 모델(POJO/DTO)입니다.
   - 이 모델들은 클라이언트 상태 관리(`ClientWorkspaceState`), 네트워크 직렬화(`S2CSyncWorkspacePacket`), 서버 영속화(`TeamWorkspaceData`) 모두에서 공통으로 사용되므로, 서버 내부 저장소 전용 모델이 아닌 공통 도메인 API 모델로 분류되어야 합니다.

### 1.3 설계 목표 (Design Goals)

- `TeamWorkspacePage`와 `CommitLogEntry`를 공통 패키지인 `com.gtceu.calcboard.api.team`으로 이전합니다.
- 클라이언트 계층(`client.gui.*`, `client.team.*`)의 `server.storage.*` 직접 임포트를 0건으로 해소합니다.
- 기존 네트워크 패킷 및 서버 저장소 클래스가 신규 공통 패키지를 참조하도록 재배선합니다.

---

## 2. 세부 설계 및 결정 사항 (Architecture Decision)

### 2.1 대안 비교 및 채택

| 비교 항목 | 대안 A: 클라이언트 전용 미러 DTO 생성 | 대안 B (채택): `api.team` 공통 패키지 이전 | 대안 C: `server.storage` 현행 유지 |
| :--- | :--- | :--- | :--- |
| **구조** | 클라이언트에 동일한 DTO 복제 후 패킷 핸들러에서 매핑 변환 | DTO를 `api.team`으로 이동하여 양 계층이 단일 모델 공유 | 기존 패키지 구조 그대로 유지 |
| **코드 중복** | 필드 및 메서드 중복 발생 (유지보수 비용 증가) | 코드 중복 0건 (단일 진실 공급원 유지) | 중복 없음 |
| **계층 정합성** | 계층 참조는 해소되나 매핑 오버헤드 발생 | 클라이언트/서버 모두 공통 API를 참조하여 완전 정합 | 클라이언트의 서버 직접 참조 지속 (스멜 방치) |
| **변환 비용** | 패킷 수신 시마다 매핑 객체 할당 오버헤드 | 추가 객체 할당 및 변환 비용 0 | 추가 비용 없음 |
| **평가** | 비효율적 과잉 설계 | **최적안 (클린 아키텍처 표준)** | 아키텍처 결함 지속 |

### 2.2 계층 구조 다이어그램

```mermaid
flowchart TD
    subgraph Before["변경 전 (As-Is: 계층 역전 발생)"]
        ClientUI_Old["Client UI & State Layer<br/>(ClientWorkspaceState, MultiblockBOMDialog 등)"]
        ServerStorage_Old["Server Storage Layer<br/>(TeamWorkspacePage, CommitLogEntry)"]
        ClientUI_Old -->|직접 역참조 (Layer Inversion)| ServerStorage_Old
    end

    subgraph After["변경 후 (To-Be: 클린 계층 분리)"]
        ClientUI_New["Client UI & State Layer<br/>(client.gui.*, client.team.*)"]
        CommonAPI["Common API Layer<br/>(com.gtceu.calcboard.api.team)"]
        ServerStorage_New["Server Storage Layer<br/>(server.storage.*)"]
        NetworkLayer["Network Packet Layer<br/>(network.packet.*)"]

        ClientUI_New -->|참조| CommonAPI
        ServerStorage_New -->|참조| CommonAPI
        NetworkLayer -->|참조| CommonAPI
    end
```

### 2.3 패키지 재배치 구조

```
com.gtceu.calcboard
├── api
│   └── team
│       ├── TeamWorkspacePage.java   <-- server.storage에서 이전 완료
│       └── CommitLogEntry.java        <-- server.storage에서 이전 완료
├── client
│   ├── gui
│   │   ├── BoardTeamSyncCoordinator.java
│   │   ├── dialog.MultiblockBOMDialog.java
│   │   ├── dialog.RecentSavesDialog.java
│   │   └── widget.PageTabBarWidget.java
│   └── team
│       └── ClientWorkspaceState.java
├── network
│   └── packet
│       ├── c2s.*
│       └── s2c.*
└── server
    ├── storage
    │   ├── TeamWorkspaceData.java
    │   └── TeamBoardSavedData.java
    └── lock
        └── WorkspaceLockManager.java
```

### 2.4 변경 적용 클래스 목록

| 클래스 위치 | 파일 경로 | 적용 내용 |
| :--- | :--- | :--- |
| **Common API** | `api/team/TeamWorkspacePage.java` | 공통 DTO 모델로 신설 (`package com.gtceu.calcboard.api.team`) |
| **Common API** | `api/team/CommitLogEntry.java` | 공통 DTO 모델로 신설 (`package com.gtceu.calcboard.api.team`) |
| **Client** | `client/team/ClientWorkspaceState.java` | `server.storage.*` 임포트를 `api.team.*`으로 교체 |
| **Client** | `client/gui/BoardTeamSyncCoordinator.java` | `server.storage.TeamWorkspacePage` 임포트를 `api.team.TeamWorkspacePage`로 교체 |
| **Client** | `client/gui/dialog/MultiblockBOMDialog.java` | `server.storage.TeamWorkspacePage` 임포트를 `api.team.TeamWorkspacePage`로 교체 |
| **Client** | `client/gui/dialog/RecentSavesDialog.java` | `CommitLogEntry` 임포트 갱신 및 라인 204 인라인 FQN `TeamWorkspacePage` 해소 |
| **Client** | `client/gui/widget/PageTabBarWidget.java` | `server.storage.TeamWorkspacePage` 임포트를 `api.team.TeamWorkspacePage`로 교체 |
| **Network** | `network/packet/s2c/S2CSyncWorkspacePacket.java` | `api.team.TeamWorkspacePage`, `api.team.CommitLogEntry` 임포트 |
| **Network** | `network/packet/s2c/S2CSyncCommitHistoryPacket.java` | `api.team.CommitLogEntry` 임포트 |
| **Network** | `network/packet/c2s/C2SChunkedCommitPacket.java` | `api.team.CommitLogEntry`, `api.team.TeamWorkspacePage` 명시적 임포트 |
| **Network** | `network/packet/c2s/C2SCommitWorkspacePacket.java` | `api.team.CommitLogEntry`, `api.team.TeamWorkspacePage` 명시적 임포트 |
| **Network** | `network/packet/c2s/C2SDeleteTeamPagePacket.java` | `api.team.CommitLogEntry`, `api.team.TeamWorkspacePage` 임포트 |
| **Network** | `network/packet/c2s/C2SRequestCommitHistoryPacket.java`| `api.team.CommitLogEntry` 임포트 |
| **Network** | `network/packet/c2s/C2SRequestWorkspacePacket.java` | `api.team.TeamWorkspacePage` 임포트 |
| **Network** | `network/packet/c2s/C2SRequestPageDataPacket.java` | `api.team.TeamWorkspacePage` 임포트 |
| **Server** | `server/storage/TeamWorkspaceData.java` | `api.team.TeamWorkspacePage`, `api.team.CommitLogEntry` 임포트 |
| **Architecture** | `test/.../ArchitectureTest.java` | Rule 6 `client_layer_should_not_depend_on_server` 영구 검증 규칙 추가 |
| **Test** | `test/.../TeamBoardStorageTest.java` | `api.team.*` 임포트 |
| **Test** | `test/.../MultiplayerLockConcurrencyTest.java` | `api.team.TeamWorkspacePage` 임포트 |
| **Test** | `test/.../WorkspaceCollaborationSyncTest.java` | 인라인 FQN `api.team.TeamWorkspacePage`로 교체 |
| **Test** | `test/.../MultiblockBOMTest.java` | 인라인 FQN `api.team.TeamWorkspacePage`로 교체 |

---

## 3. 결과 및 파급 효과 (Consequences)

### 3.1 긍정적 효과 및 구조 개선

1. **클라이언트 ➔ 서버 역방향 결합도 0건 달성**:
   - `src/main/java/com/gtceu/calcboard/client` 패키지 내 `com.gtceu.calcboard.server` 참조가 완전히 0건으로 정리되었습니다.
2. **ArchUnit 아키텍처 규칙 영구 방어**:
   - `ArchitectureTest.client_layer_should_not_depend_on_server` 규칙이 추가되어, 향후 클라이언트 계층에서 서버 패키지를 재참조할 경우 CI/테스트 빌드에서 즉각 차단됩니다.
3. **데이터 및 네트워크 직렬화 100% 호환**:
   - NBT 복합 태그 및 패킷 바이트 스트림 필드 정의가 동일하게 보존되어 세이브 및 네트워크 바이너리 호환성이 완벽히 유지됩니다.

### 3.2 검증 결과 (Verification)

- **정적 아키텍처 검증**: `python -c "import glob; bad = [f for f in glob.glob('src/main/java/com/gtceu/calcboard/client/**/*.java', recursive=True) if 'com.gtceu.calcboard.server' in open(f, encoding='utf-8').read()]; assert len(bad) == 0"` 통과 (0건).
- **ArchUnit 검증**: `ArchitectureTest` 내 6대 아키텍처 규칙 100% 통과.
- **단위 테스트 검증**: `TeamBoardStorageTest`, `MultiplayerLockConcurrencyTest`, `WorkspaceCollaborationSyncTest`, `MultiblockBOMTest` 전원 통과 (`BUILD SUCCESSFUL`).
