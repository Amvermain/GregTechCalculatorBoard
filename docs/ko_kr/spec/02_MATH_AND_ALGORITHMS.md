# [02] 수학적 연산 엔진 및 그래프 해석 알고리즘 (Math & Algorithms)

> 📍 **GTCalcBoard 기술 명세서 시리즈**
> [[00] 시스템 개요](00_OVERVIEW.md) ➔ [[01] 코어 도메인 모델](01_CORE_DOMAIN_AND_MODELS.md) ➔ **[02] 수학 엔진 및 알고리즘** ➔ [[03] UI 및 렌더링 파이프라인](03_UI_AND_RENDERING_PIPELINE.md) ➔ [[04] 멀티플레이어 및 네트워크](04_MULTIPLAYER_AND_NETWORK_PROTOCOL.md) ➔ [[05] 외부 연동 및 다국어](05_INTEGRATION_AND_I18N.md)

---

## 1. 오버클럭 및 특수 기계 물리 공식

GTCalcBoard는 그렉테크 및 기술 모드의 물리/에너지 수식을 100% 정밀하게 연산합니다.

### 1.1 전압 티어 차이 ($\Delta\text{Tier}$)
$$\Delta\text{Tier} = \max(0, \, \text{TargetTier.ordinal} - \text{RecipeTier.ordinal})$$

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

### 1.3 1틱 미만 서브틱(Sub-tick) 배치 연산 ($< 1.0\text{ Tick}$)

고전압 오버클럭으로 인해 레시피 소요 시간이 $1.0\text{ tick}$ ($0.05\text{ s}$) 미만으로 떨어질 때, 마인크래프트 게임 엔진 틱 주기에 맞추어 틱당 배치 횟수로 승격(Batching) 처리합니다:

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

> ➡️ **다음 장으로 이동**: [[03] UI 및 캔버스 렌더링 파이프라인](03_UI_AND_RENDERING_PIPELINE.md)
