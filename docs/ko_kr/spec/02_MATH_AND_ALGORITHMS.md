# [02] 수학적 연산 엔진 및 그래프 해석 알고리즘 (Math & Algorithms)

> 📍 **GTCalcBoard 기술 명세서 시리즈**
> [[00] 시스템 개요](00_OVERVIEW.md) ➔ [[01] 코어 도메인 모델](01_CORE_DOMAIN_AND_MODELS.md) ➔ **[02] 수학 엔진 및 알고리즘** ➔ [[03] UI 및 렌더링 파이프라인](03_UI_AND_RENDERING_PIPELINE.md) ➔ [[04] 멀티플레이어 및 네트워크](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] 외부 연동 및 다국어](05_INTEGRATION_AND_I18N.md)

---

## 1. 오버클럭 및 특수 기계 물리 공식

GTCalcBoard는 그렉테크 및 기술 모드의 물리/에너지 수식을 100% 정밀하게 연산합니다.

### 1.1 전압 티어 차이 ($\Delta\text{Tier}$)
$$
\Delta\text{Tier} = \begin{cases} 
\max(0, \, \text{TargetTier.ordinal} - \text{RecipeTier.ordinal} - 1) & (\text{RecipeTier} = \text{ULV}) \\
\max(0, \, \text{TargetTier.ordinal} - \text{RecipeTier.ordinal}) & (\text{otherwise})
\end{cases}
$$

---

### 1.2 오버클럭 모드별 소요 시간 및 전력 공식

$\Delta\text{Tier} > 0$일 때 에너지 배수(Energy Multiplier)와 속도 배수(Speed Multiplier)는 다음과 같이 결정됩니다:

$$
\text{Energy Factor} = 4.0^{\Delta\text{Tier}}
$$

$$
\text{Speed Factor} = \begin{cases} 
2.0^{\Delta\text{Tier}} & \text{(STANDARD Mode)} \\
4.0^{\Delta\text{Tier}} & \text{(PERFECT Mode)} \\
1.0 & \text{(LOSSLESS Mode)}
\end{cases}
$$

* **STANDARD 모드**: 일반 단일/멀티블록 기계 ($1\text{티어당 } 4\times\text{전력}, 2\times\text{속도}$)
* **PERFECT 모드**: 완벽 오버클럭 지원 멀티블록 ($1\text{티어당 } 4\times\text{전력}, 4\times\text{속도}$)
* **LOSSLESS 모드**: 속도 불변, 에너지 보존 ($1\text{티어당 } 1\times\text{전력}, 1\times\text{속도}$)

$$
\text{Calculated Duration (ticks)} = \frac{\text{BaseDurationTicks}}{\text{Speed Factor}}
$$

$$
\text{Calculated EU/t} = \text{BaseEUt} \times \text{Energy Factor}
$$

---

### 1.3 1틱 미만 서브틱(Sub-tick) 배치 및 싱글/멀티블록 오버클록 분기 ($< 1.0\text{ Tick}$)

고전압 오버클록으로 인해 레시피 소요 시간이 $1.0\text{ tick}$ ($0.05\text{ s}$) 미만으로 떨어질 때, 싱글블록과 멀티블록 기계의 하드웨어 특성에 따라 다음과 같이 분기 처리합니다:

1. **싱글블록 기계 (Singleblock Machines - Early Break)**:
   - 싱글블록 기계는 서브틱 병렬 가공을 지원하지 않습니다.
   - 따라서 오버클록 루프 중 소요 시간이 $1.0\text{ tick}$ 이하($\text{duration} \le 1.0$)에 도달하면 즉시 오버클록 연산을 조기 종료(Early Break)합니다.
   - 지속 시간은 $\max(1.0, \, \lfloor \text{duration} \rfloor) = 1.0\text{ tick}$으로 고정되며, 불필요한 추가 전압 승수($\text{Energy Factor}$) 증가나 전력(EU/t) 폭증을 방지합니다:
     $$\text{BatchesPerTick} = 1.0, \quad \text{Effective Duration} = 1.0\text{ tick} \quad (0.05\text{ s})$$
     $$\text{Cycles Per Second (CPS)} = 20.0 \times \text{Parallel} \times \text{MachineCount}$$

2. **멀티블록 기계 (Multiblock Machines - Subtick Parallel)**:
   - 멀티블록 기계는 틱당 $1.0\text{ tick}$ 도달 후에도 추가 상위 전압 티어에 대해 서브틱 병렬 가공을 지원합니다:
     $$\text{BatchesPerTick} = \frac{1.0}{\text{Calculated Duration (ticks)}}, \quad \text{Effective Duration} = 1.0\text{ tick}$$
     $$\text{Effective EU/t} = \text{Calculated EU/t} \times \text{BatchesPerTick}$$
     $$\text{Cycles Per Second (CPS)} = 20.0 \times \text{BatchesPerTick} \times \text{Parallel} \times \text{MachineCount}$$

---

### 1.4 하드웨어 애드온 승수 합성 (Addon Compounding)

장착된 모든 애드온 $a \in \text{InstalledAddons}$에 대해 기간 및 전력 승수를 복합 합성합니다:

$$\text{Total Duration} = \text{Effective Duration} \times \prod_{a \in \text{Addons}} a.\text{getDurationMultiplier}()$$
$$\text{Total EU/t} = \text{Effective EU/t} \times \prod_{a \in \text{Addons}} a.\text{getEutMultiplier}()$$

#### 가열 코일 (Heating Coil) 기계별 보너스 연역 수식
- **전기로 (EBF, Electric Blast Furnace)**:
  레시피 요구 온도 $T_{\text{recipe}}$, 코일 온도 $T_{\text{coil}}$일 때:
  $$\Delta T_{\text{excess}} = \max(0, \, T_{\text{coil}} - T_{\text{recipe}})$$
  $$\text{EUt Multiplier} = 0.95^{\lfloor \Delta T_{\text{excess}} / 900 \rfloor}$$
  *(900K 초과 온도마다 전력 소모 $5\%$ 복합 할인)*

- **열분해로 (Pyrolyse Oven)**:
  $$\text{Duration Multiplier} = \frac{100.0}{\text{PyrolyseSpeedPercent}}$$

- **크래킹 유닛 (Cracking Unit)**:
  $$\text{EUt Multiplier} = \frac{\text{CrackingEnergyPercent}}{100.0}$$

- **화학 반응기 (Large Chemical Reactor / ECR / ICR)**:
  $$\text{Duration Multiplier} = \frac{100.0}{\text{ChemicalSpeedPercent}}, \quad \text{EUt Multiplier} = \frac{\text{ChemicalEnergyPercent}}{100.0}$$

- **대형 제련로 (Multi Smelter)**:
  $$\text{Parallel} = \text{SmelterParallel} \quad (\text{기본 } 32\text{x}, 64\text{x}, 128\text{x}\dots)$$

#### 대형 증기/가스/플라즈마 터빈 로터, 독립 티어 및 내구도 소모율 공식 (ADR-006)
- **로터 홀더 및 다이나모 해치 독립 티어 분리 (Decoupled Tiers)**:
  로터 홀더 티어 전압 $V_{\text{holder}}$, 다이나모 해치 티어 전압 $V_{\text{dynamo}}$, 암페어 $A_{\text{dynamo}}$일 때:
  $$\text{Cap}_{\text{holder}} = V_{\text{holder}} \times 2.0 \quad (\text{단위: EU/t, 최대 유량 한계})$$
  $$\text{Cap}_{\text{dynamo}} = V_{\text{dynamo}} \times A_{\text{dynamo}} \quad (\text{단위: EU/t, 발전 출력 상한})$$
  $$\text{P}_{\text{max, turbine}} = \min(\text{Cap}_{\text{holder}}, \, \text{Cap}_{\text{dynamo}})$$

- **터빈 로터 효율 및 발전량 계산**:
  로터 기본 효율 $E_{\text{rotor}}$, 로터 파워 $P_{\text{rotor}}$, 로터 홀더 보너스 $B_{\text{holder}} = \max(0, (\text{HolderTier} - \text{BaseTier}) \times 10\%)$, 윤활유 부스트 승수 $M_{\text{boost}} \in \{1.0, 1.25, 1.50\}$일 때:
  $$\text{RotorEffMult} = \max\left(1.0, \, \frac{E_{\text{rotor}}}{100.0} \times \left(1.0 + \frac{B_{\text{holder}}}{100.0}\right) \times M_{\text{boost}}\right)$$
  $$\text{Calculated Output EU/t} = \min\left(\text{P}_{\text{max, turbine}}, \, \text{BaseRecipeEUt} \times \frac{P_{\text{rotor}}}{100.0} \times M_{\text{boost}}\right)$$
  $$\text{Total Parallel} = \left\lfloor \frac{\text{Calculated Output EU/t}}{\text{BaseRecipeEUt}} \right\rfloor$$

- **로터 내구도 소모율(Wear Rate) 및 수명($T_{\text{lifespan}}$) 공식**:
  로터 기본 내구도 $D_{\text{rotor}}$, 초당 내구도 소모율 $\text{Loss}_{\text{sec}}$:
  $$\text{Loss}_{\text{sec}} = \text{BaseLossRate} \times \left(\frac{\text{ActualFlowRate}}{\text{OptimalFlowRate}}\right) \times \frac{1.0}{M_{\text{boost}}}$$
  $$T_{\text{lifespan}} = \frac{D_{\text{rotor}}}{\text{Loss}_{\text{sec}}} \quad (\text{단위: 초})$$
  $$\text{Rotor Replacement Rate (Items/hour)} = \frac{3600.0}{T_{\text{lifespan}}} \times \text{MachineCount}$$

#### 공급 전력 기반 기계 가용 최대 병렬 ($P_{\max}$) 도출 수식
장착된 에너지 해치 티어 전압 $V_{\text{hatch}}$, 암페어 $A_{\text{hatch}}$, 단일 레시피 소비 전력 $E_{\text{recipe}}$일 때:
$$P_{\max} = \min\left(\text{ConfiguredParallel}, \, \left\lfloor \frac{V_{\text{hatch}} \times A_{\text{hatch}}}{E_{\text{recipe}}} \right\rfloor\right)$$
$\text{ConfiguredParallel} > P_{\max}$일 경우 캔버스 카드에 하드웨어 용량 초과 경고 뱃지를 렌더링합니다.

---

### 1.5 확률 부산물 전압 티어 부스트 (Tier Chance Boost)

원심분리기, 분쇄기 등 확률적 부산물을 생성하는 레시피에서 전압 티어가 상승할 때마다 획득 확률을 보정합니다:

$$\text{Effective Chance} = \min\Big(1.0, \, \text{BaseChance} + (\Delta\text{Tier} \times \text{TierChanceBoost})\Big)$$
$$\text{Single Machine Expected Output Rate (per sec)} = \text{Amount} \times \text{Effective Chance} \times \text{CPS}$$

---

### 1.6 증기 보일러 및 쓰로틀 ($\theta \in [0.25, 1.0]$) 공식

- **소형 보일러 (Small Boilers)**: LP Bronze ($120\text{ L/s} = 6\text{ mB/t}$), HP Steel ($360\text{ L/s} = 18\text{ mB/t}$)
- **대형 멀티블록 보일러 (Large Boilers)**: Bronze ($16\text{k/s}$), Steel ($36\text{k/s}$), Titanium ($64\text{k/s}$), Tungstensteel ($128\text{k/s}$)

$$\text{Effective Speed Multiplier} = \text{TierSpeedMultiplier} \times \theta$$
$$\text{Steam Rate (mB/t)} = \text{BaseSteamRate} \times \text{Effective Speed Multiplier}$$
$$\text{Water Rate (mB/t)} = \frac{\text{Steam Rate (mB/t)}}{160.0} \quad (1\text{mB 물} \rightarrow 160\text{mB 증기})$$

---

## 2. 모듈러 솔버 아키텍처 및 5대 그래프 알고리즘

GTCalcBoard의 연산 엔진은 단일 책임 원칙에 따라 Facade 패턴인 `FlowGraphSolver` 아래 4개의 전담 서브엔진으로 분해되어 있습니다:
- `MassBalanceSolver`: 폐루프 가우스-요르단 질량 보존 선형 방정식 연산.
- `FlowBalanceMatrixSolver`: 10-Pass 고정점 병목 완화, 자동 비율 전파(AutoRatio BFS) 및 조화 정수 비율(Harmonize) 연산.
- `FlowGraphTopologyAnalyzer`: 유향 그래프 위상 정렬, 순환 사이클 감지(`CycleDetector`) 및 상/하류 서브그래프 추출.
- `FlowSummaryAggregator`: 전체 프로세스 수지 요약(`BalanceSummary`), 총 전력/에너지 델타 및 포트 흐름 통계 집계.

```mermaid
flowchart LR
    MBS["[신규] 폐루프 질량 보존 솔버<br/>(Gauss-Jordan Ax = b)"] --> DUAL["1. Dual-Pass BFS Auto-Ratio<br/>(양방향 기계 대수 자동 전파)"]
    DUAL --> BOTTLENECK["2. 10-Pass Relaxation<br/>(피드백 루프 가동률 수렴)"]
    BOTTLENECK --> PORT["3. Port Flow Statistics<br/>(포트별 공급/수요 통계)"]
    PORT --> SUMMARY["4. Process Summary<br/>(순수 원자재/전력 결산)"]
```

---

### [핵심 엔진] 폐루프 질량 보존 가우스-요르단 선형 연립방정식 솔버 (`MassBalanceSolver`)

부산물이 다시 상류로 순환하는 화학 공정(예: 에틸벤젠 공정의 수소 재활용, 백금족 정제 순환선) 및 다중 노드 폐루프 사이클에서 **질량 보존 법칙(Mass Conservation Law)**을 완벽히 만족하는 기계 대수 벡터 $\mathbf{x} = [x_1, x_2, \dots, x_N]^T$를 정확한 선형대수학 수치해석으로 계산합니다.

#### 1. 선형 방정식 정식화
각 재료 $i$ ($1 \le i \le M$)에 대해, 외부 순수 유출량 $d_i$와 기계별 순수 생성/소비 계수 행렬 $S \in \mathbb{R}^{M \times N}$ 사이의 관계식:

$$\sum_{j=1}^N S_{ij} x_j = d_i \quad \Longleftrightarrow \quad A\mathbf{x} = \mathbf{b}$$

여기서 $S_{ij}$는 $j$번째 단일 기계가 초당 생산하는 재료 $i$의 양($>0$) 또는 소비하는 양($<0$)입니다.

#### 2. 가우스-요르단 부분 피보팅 (Gauss-Jordan with Partial Pivoting)
수치적 안정성을 위해 매 피봇 단계 $k$에서 열의 최대 절대값을 갖는 행을 선택하여 행 교환(Row Swapping)을 수행합니다:

$$p = \arg\max_{i \ge k} |A_{ik}|$$

피봇 원소 $|A_{pk}| < \epsilon$ ($10^{-9}$)인 경우 특이행렬(Singular Matrix) 또는 미결정계(Under-determined System)로 판정하고 최소제곱(Least-Squares) 또는 BFS 폴백 파이프라인으로 전환합니다.

소거 연산 ($O(N^3)$):
$$A_{ij} \leftarrow A_{ij} - \frac{A_{ik}}{A_{kk}} A_{kj}, \quad b_i \leftarrow b_i - \frac{A_{ik}}{A_{kk}} b_k \quad (\forall i \ne k)$$

---

### [알고리즘 1] 10-Pass Fixed-Point Bottleneck Relaxation (병목 효율 해석기)

상류 공급 부족이 존재하는 유향 그래프에서 모든 기계의 정상 상태 가동률($\eta_v \in [0.0, 1.0]$)을 수치적으로 수렴 계산합니다.

1. **초기화**: 모든 노드 $v \in V$의 효율을 $\eta_v^{(0)} = 1.0$으로 설정.
2. **반복 수렴 ($k = 1 \dots 10$)**:
   - 생산자의 현재 유효 공급량: $\text{Supply}_{P_j} = \text{NominalOutputRate}_{P_j} \times \eta_{P_j}^{(k-1)}$
   - 비례 분배된 입력 공급량:
     $$\text{IncomingSupply}_i = \sum_{P_j} \min\left(\text{NominalDemand}_{C, i}, \, \text{Supply}_{P_j} \times \frac{\text{NominalDemand}_{C, i}}{\text{TotalDemand}_{P_j}}\right)$$
   - 소비자 기계 $C$의 새로운 효율: $\eta_C^{(k)} = \min_{i} \left(\frac{\text{IncomingSupply}_i}{\text{NominalDemand}_{C, i}}, \, 1.0\right)$
3. **수렴 조기 종료**: $\max_{v} |\eta_v^{(k)} - \eta_v^{(k-1)}| < 10^{-4}$ 만족 시 반복 즉시 종료.

---

### [알고리즘 2] 포트 흐름 통계 (`PortFlowStats`)

- **입력 포트**: 결손(`isInputDeficit`, 공급 < 수요 $-0.001$), 정합(`isBalanced`, $\pm 0.001$), 잉여(`isInputSurplus`, 공급 > 수요 $+0.001$)
- **출력 포트**: 잉여(`isOutputSurplus`, 생산 > 하류소비 $+0.001$), 결손(`isOutputDeficit`, 생산 < 하류소비 $-0.001$)

---

### [알고리즘 3] Dual-Pass BFS Auto-Ratio (자동 비율 전파)

기준(Anchor) 노드를 고정하고 상류(수요 충족)와 하류(부산물 처리)로 기계 대수를 전파하며, 사이클 방지 가드($\max(50, |V| \times 5)$, 방문 $\le 3$회)를 적용합니다.

---

### [알고리즘 4] 공정 요약 및 Net Balance (`BalanceSummary`)

$$\text{Net EU/t} = \sum_{g \in \text{Generators}} g.\text{getEffectiveEUt}() - \sum_{m \in \text{Consumers}} m.\text{getEffectiveEUt}()$$

$$\Delta_{\text{material}} = \sum \text{Output Rates} - \sum \text{Input Rates}$$

---

### [알고리즘 5] 목표 배치 생산 소요 시간(ETA) 및 총 소요 자원 연산 (`ProductionETACalculator`)

단말 노드의 목표 수량 $A_{\text{target}}$과 순 유입 속도 $\text{Rate}_{\text{in}}$ 기준:

$$T_{\text{ET}} = \frac{A_{\text{target}}}{\text{Rate}_{\text{in}}} \quad [\text{초}]$$
$$E_{\text{total}} = \sum_{n \in \text{UpstreamNodes}} \left( n.\text{getTotalEUt}() \times 20 \times T_{\text{ET}} \right) \quad [\text{EU}]$$
$$C_{\text{raw}}(M) = \text{UnconnectedInputRate}(M) \times T_{\text{ET}} \quad [\text{Items / mB}]$$

---

### [알고리즘 6] 무한/고정 외부 공급 유량 모델링 및 역전파 차단 수학 (`FlowBalanceMatrixSolver`, `FlowSummaryAggregator`) (ADR-012)

정션 및 공급 노드에 외부 자원 공급(`SupplyMode`)이 설정된 경우, 상류 요구량 역전파를 결정론적으로 제어하고 원자재 결손량을 자동 상쇄합니다:

1. **무한 공급 모드 (`SupplyMode.INFINITE`)**:
   - 하류 연결 노드의 요구량 $D_{\text{down}}$에 관계없이 상류 노드로 전파되는 요구량을 0으로 차단합니다:
     $$\text{Demand}_{\text{upstream}} = 0$$
   - 하류 포트에 필요한 전체 유량을 외부 무한 공급으로 충당하므로, 상류 기계의 추가 증설 요구를 억제합니다.
2. **고정 공급 모드 (`SupplyMode.FIXED_RATE`)**:
   - 지정된 초당 외부 공급량 $R_{\text{ext}}$을 초과하는 잔여 수요분만 상류로 역전파합니다:
     $$\text{Demand}_{\text{upstream}} = \max(0.0, \, D_{\text{down}} - R_{\text{ext}})$$
3. **공정 수지 요약 결손량 상쇄 (`FlowSummaryAggregator`)**:
   - 그래프 전체의 미연결 원자재 요구량($\text{RawDeficit}$) 집계 시, 외부 공급 노드의 유효 공급량($\min(D_{\text{down}}, R_{\text{ext}})$ 또는 $\text{INFINITE}$)을 차감하여 순수 외부 조달 필요량만을 결산에 반영합니다.

---

### [알고리즘 7] AE2 오토크래프팅 플랜 평가 및 크리티컬 패스 병렬 파이프라인 ETA (`Ae2CraftingPlanEvaluator`) (ADR-008)

AE2 패턴과 바인딩된 다중 서브페이지 공정망에 대해, $O(K)$ 위상 정렬 DAG(Directed Acyclic Graph)를 기반으로 병렬 실행 시간 및 파이프라인 지연 시간을 정확히 적분합니다:

1. **단일 노드 배치 실행 시간 ($T_{\text{batch}}$) 및 병렬 가동 횟수 ($N_{\text{runs}}$)**:
   $$\text{EffectiveParallel} = \text{node.getParallel}() \times \text{node.getMachineCount}()$$
   $$N_{\text{runs}} = \left\lceil \frac{\text{RequiredQuantity}}{\text{RecipeOutputAmount} \times \text{EffectiveParallel}} \right\rceil$$
   $$T_{\text{node}} = N_{\text{runs}} \times \text{node.getEffectiveDurationSeconds}()$$
2. **크리티컬 패스 및 파이프라인 지연 시간 ($T_{\text{pipeline}}$)**:
   상류 노드 집합 $\text{Pred}(u)$에 대해:
   $$T_{\text{start}}(u) = \max_{p \in \text{Pred}(u)} \left( T_{\text{start}}(p) + \text{FirstBatchDuration}(p) \right)$$
   $$T_{\text{finish}}(u) = T_{\text{start}}(u) + T_{\text{node}}(u)$$
   $$\text{Total ETA} = \max_{u \in \text{TerminalNodes}} T_{\text{finish}}(u)$$

---

### [알고리즘 8] 계층형 복합 모듈 재귀 BOM 산출 및 기계 대수 승격 연산 (`MultiblockBOMCalculator`)

다층으로 중첩된 복합 모듈(`isModule()`) 및 공유 기계 풀 프레임(`isSharedMachineFrame()`)을 포함하는 플로우 그래프에서 물리적 자재 소요량(BOM)을 단일 패스로 평탄화(Flatten)하여 정밀 집계합니다:

1. **다층 모듈 부모 승수 재귀 전파 (`flattenNodesAndFrames`)**:
   루트 노드로부터 하위 서브그래프 $G_{\text{sub}}$로 탐색할 때, 모듈 노드의 기계 대수 $M_{\text{module}}$를 부모 승수 $P$에 누적 곱연산합니다:
   $$P_{\text{child}} = P_{\text{parent}} \times \max(1.0, \, M_{\text{module}})$$
   리프 노드 $n$에 도달했을 때 실효 기계 대수를 $n.\text{getMachineCount}() \times P$로 스케일링 복제하여 모듈 컨테이너는 부품 목록에서 배제하고 내부 실제 가동 기계들만을 자재 집계 대상으로 평탄화합니다.
2. **공유 기계 풀 프레임 듀티 통합 및 정수 대수 올림**:
   공유 프레임에 포함된 기계 노드군 $\{n_1, n_2, \dots, n_k\}$에 대해 총 듀티 사이클 합을 계산하고 1대 이상의 정수 물리 기계 대수로 올림 처리합니다:
   $$M_{\text{req}} = \max\left(1, \, \left\lceil \sum_{i=1}^{k} n_i.\text{getMachineCount}() - 10^{-5} \right\rceil\right)$$
   대표 마스터 노드에만 $M_{\text{req}}$ 대수를 적용하고 종속 슬레이브 노드는 중복 자재 집계에서 제외합니다.
3. **단일 기계 티어형 아이템 자동 분기 및 출처 역추적 (`usedByMachines`)**:
   단일 기계의 경우 지정된 전압 티어(LV~MAX)에 대응하는 구체적 아이템 ID(예: `gtceu:lv_rock_breaker`)로 연역 변환하며, 동일 부품을 요구하는 모든 기계 이름과 대수를 `usedByMachines` 목록에 명시합니다.

---

### [알고리즘 9] 잉여 부산물 보이드 싱크 및 포트 보이드 질량 보존 수식 (`FlowBalanceMatrixSolver`, `FlowSummaryAggregator`) (ADR-019)

석유 분별, 산 분해, 화학 공정 등에서 발생하는 잉여 부산물 소각을 위해 물리적 정션 보이드 싱크(`SupplyMode.VOID_SINK`) 및 포트 직접 보이드 마킹(`isOutputPortVoided`)을 통합 처리합니다:

1. **질량 보존 및 순 결산 수식 (Mass Balance with Void Sinks)**:
   전체 공정 그래프의 특정 물질 $s$에 대해:
   $$\Delta(s) = P(s) - C(s) - V(s)$$
   - $P(s) = \sum \text{Produced}(s)$: 총 생산량
   - $C(s) = \sum \text{Consumed}(s)$: 총 소비량
   - $\text{netSurplus}(s) = \max(0, \, P(s) - C(s))$: 소비량을 초과하는 순 잉여분
   - $V(s) = \min\Big(\text{netSurplus}(s), \, V_{\text{marked}}(s) + V_{\text{sink}}(s)\Big)$: 유효 보이드 유량
   - $\text{NetOutput}(s) = \text{netSurplus}(s) - V(s)$: 최종 결산창(`SummaryOverlay`)의 순 생산물 유량

2. **결손 상태($P(s) < C(s)$) 시 보이드 배제**:
   결손 상태인 물질은 보이드 대상이 되지 않으며, 다운스트림 정상 소비자가 절대적인 1순위 우선권을 가집니다. 오직 실수요를 초과하는 순 잉여분($\text{netSurplus} > 0$)만 $V(s)$ 한도 내에서 폐기 처리됩니다.

3. **1:N 분기 연결 시 소비자 우선순위 엄격 격리 (`getConnectedConsumerDemand`)**:
   하나의 출력 포트가 여러 정상 기계들과 `VOID_SINK` 정션으로 동시에 분기 연결된 1:N 토폴로지 환경에서:
   - `FlowBalanceMatrixSolver.getConnectedConsumerDemand()`는 `consumer.isVoidSink()` 대상의 수요를 **엄격히 0으로 강제 반환**합니다.
   - 포트 출력 분배 및 병목 효율 연산 시 `VOID_SINK`는 총 수요(`totalPortDemand`) 집계 대상에서 완전히 배제되므로, 정상 소비 기계가 공급받아야 할 유량을 가로채거나 굶주리게(Input Starvation) 만드는 현상을 수학적으로 원천 차단합니다.
   - 오직 정상 소비자들이 필요로 하는 실수요를 모두 충족하고 남은 잔여 유량($P - \sum C_{\text{normal}}$)만이 보이드 싱크의 유효 흡수량($V_{\text{sink}}$)으로 할당됩니다.

---

> ➡️ **다음 장으로 이동**: [[03] UI 및 캔버스 렌더링 파이프라인](03_UI_AND_RENDERING_PIPELINE.md)
