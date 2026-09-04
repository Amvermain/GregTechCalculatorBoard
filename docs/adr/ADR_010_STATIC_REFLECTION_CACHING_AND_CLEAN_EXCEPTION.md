# ADR-010: 리플렉션 정적 캐싱 및 예외 처리 무결성 개편 명세
*(Static Reflection Caching & Clean Exception Handling)*

| 메타데이터 항목 | 내용 |
| :--- | :--- |
| **문서 번호** | ADR-010 (구 RFC-010) |
| **상태** | 🟢 IMPLEMENTED |
| **대상 버전** | v2.1.0-alpha.3 |
| **결정 및 완료일자** | 2026-09-01 |
| **준수 원칙** | Clean Code Pillar 4 (예외 처리 무결성 & 리플렉션 정적 캐싱) |

---

## 1. 배경 및 맥락 (Context)

### 1.1 현황 및 식별된 안티패턴
1. **런타임 루프 내부 동적 리플렉션 오버헤드**:
   [TurbineRotorHelper](../../src/main/java/com/gtceu/calcboard/compat/gtceu/helper/TurbineRotorHelper.java), [ParallelHelper](../../src/main/java/com/gtceu/calcboard/compat/gtceu/helper/ParallelHelper.java), [CategoryCapabilityMatrix](../../src/main/java/com/gtceu/calcboard/api/catalog/CategoryCapabilityMatrix.java), [DynamicAddonCrawler](../../src/main/java/com/gtceu/calcboard/api/catalog/DynamicAddonCrawler.java), [MultiblockDetector](../../src/main/java/com/gtceu/calcboard/api/catalog/MultiblockDetector.java), [ThermalAugmentHelper](../../src/main/java/com/gtceu/calcboard/compat/thermal/helper/ThermalAugmentHelper.java) 등의 핵심 렌더링/스캔 루프에서 매 호출마다 Class.forName(...), getMethod(...), getField(...)를 반복 조회하여 JVM 리플렉션 메타데이터 조회 오버헤드가 발생했습니다.
2. **무분별한 	ry { ... } catch (Throwable ignored) {} 남발**:
   코드베이스 전반에서 방어적 프로그래밍을 명목으로 무분별한 빈 catch 블록이 사용되어, 예외가 정상적인 제어 흐름으로 은폐되고 런타임 NullPointerException이나 타입 불일치 버그 디버깅을 어렵게 만들었습니다.
3. **Headless 및 단위 테스트 환경에서의 초기화 무결성**:
   단위 테스트 환경(Headless)에서는 Forge / Minecraft FML 라이프사이클이 실행되지 않아 GTCEu static 초기화 시 GTCEu.getGameDir()이 
ull을 반환하여 ExceptionInInitializerError (LinkageError)가 발생할 수 있습니다. static {} 블록에서 Class.forName(name, false, classLoader)와 LinkageError 안전 캡처가 필수적이었습니다.

---

## 2. 의사결정 (Decision)

1. **리플렉션 대상의 static final 1회 캐싱 표준화**:
   - 모든 외부 모드 리플렉션 대상(Class<?>, Method, Field)을 클래스 로딩 시점(static {} 블록)에 1회만 안전하게 캐싱합니다.
   - 클래스 로딩 시 Class.forName(className, false, classLoader)를 사용하여 부수 효과를 차단하고, catch (ReflectiveOperationException | LinkageError ignored) {}를 통해 안전하게 
ull로 초기화합니다.
2. **런타임 연산 (1)$ 직접 호출 최적화 및 널 가드**:
   - 런타임 루프에서는 미리 캐시된 Method / Field 객체 호출 및 컴파일 타임 인터페이스를 우선 적용합니다.
   - 메서드/필드 호출 전 if (METHOD == null) return fallback; 조기 반환 가드를 적용하여 불필요한 예외 발생 및 스택 트레이스 생성을 원천 차단합니다.
3. **빈 catch (Throwable) 전면 제거 및 명시적 예외 처리**:
   - catch (Throwable)를 제거하고 명시적 ReflectiveOperationException | LinkageError로 한정합니다.
   - 익명 클래스 및 패키지 프라이빗 클래스의 리플렉션 호출을 위해 메서드/필드 호출 전 setAccessible(true)를 명시합니다.
4. **단일 책임 원칙(SRP) 및 얕은 메서드(Shallow Methods) 리팩토링**:
   - 중첩된 루프 및 조건문을 조기 반환(Early Return)과 1~2단계 뎁스의 헬퍼 메서드로 분리하여 코드 가독성과 유지보수성을 극대화합니다.

---

## 3. 리플렉션 생명주기 아키텍처

`mermaid
flowchart TD
    subgraph Class_Loading_Phase ["1. 클래스 로딩 단계 (static 초기화)"]
        CL["static {} 블록 실행"]
        CF["Class.forName(name, false, classLoader) & getMethod(...) 1회 조회"]
        SC["static final 필드에 Method/Field/Class 캐싱 완료"]
        FB["실패/미로드 시 null 저장 및 기능 안전 비활성화"]
        
        CL --> CF
        CF -->|성공| SC
        CF -->|ReflectiveOperationException / LinkageError| FB
    end

    subgraph Runtime_Execution_Phase ["2. 런타임 실행 단계 (루프 & 렌더링)"]
        RT["Helper 메서드 호출 (e.g. getRotorStats, getParallelStats)"]
        GC{"캐시된 Method / Field != null?"}
        INV["method.invoke(target, args) 즉시 실행 ((1)$)"]
        DF["기본값(Fallback) 즉시 반환 (No Exception Overhead)"]

        RT --> GC
        GC -->|True| INV
        GC -->|False| DF
    end

    SC -.->|참조| GC
`

---

## 4. 적용 대상 및 변경 내역

| 대상 클래스 | 변경 내용 |
| :--- | :--- |
| [TurbineRotorHelper](../../src/main/java/com/gtceu/calcboard/compat/gtceu/helper/TurbineRotorHelper.java) | TurbineRotorBehaviour, Material 리플렉션 메서드 static 캐싱, setAccessible(true) 추가, (1)$ 런타임 호출 |
| [ParallelHelper](../../src/main/java/com/gtceu/calcboard/compat/gtceu/helper/ParallelHelper.java) | GTRegistries.MACHINES, IMachineBlockEntity holder 클래스 static 캐싱, 프록시 캐시 분리, 인스턴스/정적 수치 추출 분리 |
| [CoilHelper](../../src/main/java/com/gtceu/calcboard/compat/gtceu/helper/CoilHelper.java) | esolveCoilTypeObject, xtractInt, discoverGTCEuCoils 리플렉션 최적화, bare catch 제거, shallow 메서드화 |
| [EnergyHatchHelper](../../src/main/java/com/gtceu/calcboard/compat/gtceu/helper/EnergyHatchHelper.java) | GTRegistries.MACHINES static 캐싱, dummy proxy 캐싱, setAccessible(true) 추가, 전수 LinkageError 방어 |
| [GTHatchHelper](../../src/main/java/com/gtceu/calcboard/compat/gtceu/helper/GTHatchHelper.java) | GTRegistries.MACHINES static 캐싱, 능력치 추출 리플렉션 최적화 및 얕은 메서드 분리 |
| [ReflectorHelper](../../src/main/java/com/gtceu/calcboard/compat/gtceu/helper/ReflectorHelper.java) | 재귀적 필드/메서드 탐색 리플렉션 모듈화, setAccessible(true) 추가, ReflectiveOperationException 한정 |
| [DynamicAddonCrawler](../../src/main/java/com/gtceu/calcboard/api/catalog/DynamicAddonCrawler.java) | GTRegistries.RECIPE_TYPES static 캐싱, 레시피 아웃풋 추출 루프 리플렉션 최적화 및 구조화 |
| [CategoryCapabilityMatrix](../../src/main/java/com/gtceu/calcboard/api/catalog/CategoryCapabilityMatrix.java) | GTRegistries.MACHINES.get static 메서드 캐싱, EMI/JEI 워크스테이션 스캐너 평탄화 |
| [MultiblockDetector](../../src/main/java/com/gtceu/calcboard/api/catalog/MultiblockDetector.java) | CoilWorkableElectricMultiblockMachine, StarTThreadingCapableMachine, GTRecipeModifiers static 캐싱 |
| [ThermalAugmentHelper](../../src/main/java/com/gtceu/calcboard/compat/thermal/helper/ThermalAugmentHelper.java) | DynamoBlockEntity, MachineBlockEntity, ThermalCoreConfig static 캐싱, 다이나모 키워드 분리 및 최적화 |

---

## 5. 결과 및 파급 효과 (Consequences)

- **성능 향상**: 렌더링 및 애드온 스캔 루프에서 매번 발생하던 동적 클래스 로딩/메서드 조회 오버헤드가 제거되어 (1)$ 직접 호출로 최적화되었습니다.
- **예외 은폐 제거**: 정상적인 제어 흐름에 예외 처리를 남용하던 안티패턴을 제거하고 명시적 null 검사/가드를 적용하여 런타임 안정성과 디버깅 용이성이 확보되었습니다.
- **Headless 무결성**: 단위 테스트 환경 등 외부 모드가 완전 초기화되지 않은 상태에서도 initialize=false 및 LinkageError 캡처를 통해 476개 단위 테스트가 100% 정상 통과되었습니다.
