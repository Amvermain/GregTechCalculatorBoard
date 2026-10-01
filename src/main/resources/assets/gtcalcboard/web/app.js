(() => {
    "use strict";

    const canvas = document.getElementById("boardCanvas");
    const ctx = canvas.getContext("2d");
    const tooltip = document.getElementById("tooltip");
    const connectionBadge = document.getElementById("connectionBadge");
    const connectionText = document.getElementById("connectionText");
    const pageTitleBadge = document.getElementById("pageTitleBadge");
    const pageTitleText = document.getElementById("pageTitleText");
    const pageBrowserMenu = document.getElementById("pageBrowserMenu");
    const pageSearchInput = document.getElementById("pageSearchInput");
    const chkLiveFollow = document.getElementById("chkLiveFollow");
    const pageListContainer = document.getElementById("pageListContainer");
    const pageSelectorWrapper = pageTitleBadge ? pageTitleBadge.closest(".page-selector-wrapper") : null;
    const nodeCountElem = document.getElementById("nodeCount");
    const wireCountElem = document.getElementById("wireCount");
    const powerBalanceElem = document.getElementById("powerBalance");
    const zoomLevelElem = document.getElementById("zoomLevel");
    const lastUpdatedElem = document.getElementById("lastUpdated");
    const btnUnitMode = document.getElementById("btnUnitMode");
    const tabPersonal = document.getElementById("tabPersonal");
    const tabTeam = document.getElementById("tabTeam");
    const teamTabLabel = document.getElementById("teamTabLabel");

    let currentPageId = "";
    let currentWorkspace = "LOCAL";
    let hasTeam = false;
    let localPages = [];
    let teamPages = [];
    let liveFollow = true;
    let availablePages = [];
    let currentUnitMode = localStorage.getItem("gtcalcboard_unit_mode") || "auto";

    function updateUnitModeUI() {
        if (!btnUnitMode) return;
        const labels = {
            auto: "Unit: Auto",
            b: "Unit: B",
            mb: "Unit: mB"
        };
        btnUnitMode.textContent = labels[currentUnitMode] || "Unit: Auto";
    }

    if (btnUnitMode) {
        updateUnitModeUI();
        btnUnitMode.addEventListener("click", () => {
            if (currentUnitMode === "auto") {
                currentUnitMode = "b";
            } else if (currentUnitMode === "b") {
                currentUnitMode = "mb";
            } else {
                currentUnitMode = "auto";
            }
            localStorage.setItem("gtcalcboard_unit_mode", currentUnitMode);
            updateUnitModeUI();
            requestRender();
        });
    }

    let boardData = {
        version: 1,
        pageId: "",
        pageTitle: "Untitled Page",
        timestamp: Date.now(),
        viewport: { panX: 40.0, panY: 40.0, zoom: 1.0 },
        nodes: [],
        connections: [],
        frames: [],
        stickyNotes: []
    };

    let panX = 40.0;
    let panY = 40.0;
    let zoom = 1.0;
    let isDragging = false;
    let dragStartX = 0;
    let dragStartY = 0;
    let mouseX = 0;
    let mouseY = 0;
    let hoveredObject = null;
    const iconCache = new Map();

    function resizeCanvas() {
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        canvas.width = Math.max(100, Math.floor(rect.width * dpr));
        canvas.height = Math.max(100, Math.floor(rect.height * dpr));
        ctx.scale(dpr, dpr);
        requestRender();
    }

    window.addEventListener("resize", resizeCanvas);

    function requestRender() {
        requestAnimationFrame(render);
    }

    function getIconImage(type, id) {
        if (!id) return null;
        const key = type + ":" + id;
        if (iconCache.has(key)) {
            return iconCache.get(key);
        }
        const img = new Image();
        const endpoint = type === "fluid" ? "/api/icon/fluid?id=" : "/api/icon/item?id=";
        img.src = endpoint + encodeURIComponent(id);
        img.onload = () => requestRender();
        img.onerror = () => {
            img.failed = true;
            setTimeout(() => {
                iconCache.delete(key);
            }, 2000);
            requestRender();
        };
        iconCache.set(key, img);
        return img;
    }

    function render() {
        const dpr = window.devicePixelRatio || 1;
        const w = canvas.width / dpr;
        const h = canvas.height / dpr;

        ctx.save();
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.fillStyle = "#090d16";
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Grid background
        ctx.scale(dpr, dpr);
        drawGrid(w, h);

        ctx.translate(panX, panY);
        ctx.scale(zoom, zoom);
        ctx.imageSmoothingEnabled = false;

        // 1. Frames (background layer)
        drawFrames();

        // 2. Sticky notes
        drawStickyNotes();

        // 3. Connections (wires)
        drawConnections();

        // 4. Nodes
        drawNodes();

        ctx.restore();
    }

    function drawGrid(w, h) {
        ctx.save();
        const gridSize = 24 * zoom;
        const startX = (panX % gridSize + gridSize) % gridSize;
        const startY = (panY % gridSize + gridSize) % gridSize;

        ctx.strokeStyle = "rgba(30, 41, 59, 0.4)";
        ctx.lineWidth = 1;
        ctx.beginPath();
        for (let x = startX; x < w; x += gridSize) {
            ctx.moveTo(x, 0);
            ctx.lineTo(x, h);
        }
        for (let y = startY; y < h; y += gridSize) {
            ctx.moveTo(0, y);
            ctx.lineTo(w, y);
        }
        ctx.stroke();
        ctx.restore();
    }

    function drawFrames() {
        if (!boardData.frames) return;
        for (const f of boardData.frames) {
            const colorHex = f.color ? "#" + (f.color & 0x00FFFFFF).toString(16).padStart(6, "0") : "#3b82f6";
            ctx.save();

            if (f.isSharedMachine && f.viewMode === "EMBEDDED_PANEL") {
                drawSharedMachineEmbeddedPanel(f, colorHex);
                ctx.restore();
                continue;
            }
            if (f.isSharedMachine && f.viewMode === "FOLDED_CARD") {
                drawSharedMachineFoldedCard(f, colorHex);
                ctx.restore();
                continue;
            }

            ctx.fillStyle = colorHex + "1a";
            ctx.strokeStyle = colorHex + "aa";
            ctx.lineWidth = 2;
            roundRect(ctx, f.posX, f.posY, f.width, f.height, 8);
            ctx.fill();
            ctx.stroke();

            // Header bar
            ctx.fillStyle = colorHex + "44";
            roundRect(ctx, f.posX, f.posY, f.width, 24, [8, 8, 0, 0]);
            ctx.fill();

            ctx.fillStyle = "#f8fafc";
            ctx.font = "bold 12px sans-serif";
            ctx.fillText(f.title || "Group Frame", f.posX + 8, f.posY + 16);
            ctx.restore();
        }
    }

    function drawSharedMachineEmbeddedPanel(f, colorHex) {
        ctx.fillStyle = "rgba(20, 23, 30, 0.95)";
        ctx.strokeStyle = colorHex + "cc";
        ctx.lineWidth = 2;
        roundRect(ctx, f.posX, f.posY, f.width, f.height, 8);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = colorHex + "33";
        roundRect(ctx, f.posX, f.posY, f.width, 24, [8, 8, 0, 0]);
        ctx.fill();
        ctx.strokeStyle = colorHex + "55";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(f.posX, f.posY + 24);
        ctx.lineTo(f.posX + f.width, f.posY + 24);
        ctx.stroke();

        const icon = getIconImage("item", f.sharedMachineId);
        if (icon && icon.complete && icon.naturalWidth > 0) {
            ctx.drawImage(icon, f.posX + 4, f.posY + 4, 16, 16);
        } else {
            ctx.fillStyle = "#ffffff";
            ctx.font = "bold 11px sans-serif";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText("↔", f.posX + 12, f.posY + 12);
        }

        const machineName = f.sharedMachineName || f.title || "Shared Machine";
        const tierName = f.sharedTier ? " [" + f.sharedTier + "]" : "";
        const titleText = machineName + tierName + " <공유 기계 풀>";

        ctx.fillStyle = "#ffffff";
        ctx.font = "bold 11px sans-serif";
        ctx.textAlign = "left";
        ctx.textBaseline = "middle";
        ctx.fillText(truncateText(ctx, titleText, f.width - 40), f.posX + 24, f.posY + 12);

        ctx.fillStyle = "#181f2a";
        ctx.fillRect(f.posX + 1, f.posY + 24, f.width - 2, 20);
        ctx.strokeStyle = "#334155";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(f.posX, f.posY + 44);
        ctx.lineTo(f.posX + f.width, f.posY + 44);
        ctx.stroke();

        ctx.fillStyle = "#94a3b8";
        ctx.font = "10px sans-serif";
        ctx.textAlign = "left";
        ctx.textBaseline = "middle";
        ctx.fillText("Count:", f.posX + 6, f.posY + 34);

        const countStr = (f.targetCapacity || 1.0).toFixed(2);
        const countBoxW = Math.max(30, ctx.measureText(countStr).width + 8);
        ctx.fillStyle = "#1e293b";
        ctx.strokeStyle = "#475569";
        roundRect(ctx, f.posX + 42, f.posY + 27, countBoxW, 14, 2);
        ctx.fill();
        ctx.stroke();
        ctx.fillStyle = "#ffffaa";
        ctx.font = "bold 9.5px sans-serif";
        ctx.textAlign = "center";
        ctx.fillText(countStr, f.posX + 42 + countBoxW / 2, f.posY + 34);

        const duty = f.totalDuty || 0;
        const req = f.requiredMachines || 1;
        const dutyStr = (duty * 100.0).toFixed(1) + "% (" + req + "x)";
        const isDeficit = duty > (f.targetCapacity || 1.0) + 0.001;
        ctx.fillStyle = isDeficit ? "#f87171" : "#34d399";
        ctx.font = "bold 10px sans-serif";
        ctx.textAlign = "left";
        ctx.fillText(dutyStr, f.posX + 46 + countBoxW + 6, f.posY + 34);

        const eut = f.totalEUt || 0;
        const eutStr = eut.toFixed(1) + " EU/t";
        ctx.fillStyle = "#fcd34d";
        ctx.font = "bold 10px sans-serif";
        ctx.textAlign = "right";
        ctx.fillText(eutStr, f.posX + f.width - 8, f.posY + 34);
    }

    function drawSharedMachineFoldedCard(f, colorHex) {
        ctx.fillStyle = "#0d131f";
        ctx.strokeStyle = colorHex + "dd";
        ctx.lineWidth = 2;
        roundRect(ctx, f.posX, f.posY, f.width, f.height, 6);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = "#070b12";
        roundRect(ctx, f.posX, f.posY, f.width, 22, [6, 6, 0, 0]);
        ctx.fill();
        ctx.strokeStyle = "#1e293b";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(f.posX, f.posY + 22);
        ctx.lineTo(f.posX + f.width, f.posY + 22);
        ctx.stroke();

        const icon = getIconImage("item", f.sharedMachineId);
        if (icon && icon.complete && icon.naturalWidth > 0) {
            ctx.drawImage(icon, f.posX + 3, f.posY + 3, 16, 16);
        } else {
            ctx.fillStyle = "#94a3b8";
            ctx.font = "bold 10px sans-serif";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText("↔", f.posX + 11, f.posY + 11);
        }

        const machineName = f.sharedMachineName || f.title || "Shared Machine";
        const tierName = f.sharedTier ? " [" + f.sharedTier + "]" : "";
        const titleText = machineName + tierName + " <공유 기계 풀>";
        ctx.fillStyle = "#f8fafc";
        ctx.font = "bold 10px sans-serif";
        ctx.textAlign = "left";
        ctx.textBaseline = "middle";
        ctx.fillText(truncateText(ctx, titleText, f.width - 30), f.posX + 23, f.posY + 11);

        const duty = f.totalDuty || 0;
        const req = f.requiredMachines || 1;
        const dutyStr = (duty * 100.0).toFixed(1) + "% (" + req + "x)";
        const isDeficit = duty > (f.targetCapacity || 1.0) + 0.001;
        ctx.fillStyle = isDeficit ? "#f87171" : "#34d399";
        ctx.font = "bold 9.5px sans-serif";
        ctx.textAlign = "left";
        ctx.fillText(dutyStr, f.posX + 6, f.posY + 32);

        const eut = f.totalEUt || 0;
        const eutStr = eut.toFixed(1) + " EU/t";
        ctx.fillStyle = "#fcd34d";
        ctx.textAlign = "right";
        ctx.fillText(eutStr, f.posX + f.width - 6, f.posY + 32);

        if (f.foldedPorts) {
            const inList = f.foldedPorts.inputs || [];
            const outList = f.foldedPorts.outputs || [];
            const maxP = Math.max(inList.length, outList.length);
            for (let i = 0; i < maxP; i++) {
                const py = f.posY + 44 + i * 18 + 8;
                if (i < inList.length) {
                    const port = inList[i];
                    ctx.fillStyle = "#38bdf8";
                    ctx.beginPath();
                    ctx.arc(f.posX + 5, py, 3, 0, Math.PI * 2);
                    ctx.fill();

                    const portIcon = getIconImage(port.type === "FLUID" ? "fluid" : "item", port.id);
                    if (portIcon && portIcon.complete && portIcon.naturalWidth > 0) {
                        ctx.drawImage(portIcon, f.posX + 10, py - 7, 14, 14);
                    }
                    ctx.fillStyle = "#94a3b8";
                    ctx.font = "8.5px sans-serif";
                    ctx.textAlign = "left";
                    ctx.fillText(formatRate(port.ratePerSec, port.type), f.posX + 27, py);
                }
                if (i < outList.length) {
                    const port = outList[i];
                    ctx.fillStyle = "#f59e0b";
                    ctx.beginPath();
                    ctx.arc(f.posX + f.width - 5, py, 3, 0, Math.PI * 2);
                    ctx.fill();

                    const portIcon = getIconImage(port.type === "FLUID" ? "fluid" : "item", port.id);
                    if (portIcon && portIcon.complete && portIcon.naturalWidth > 0) {
                        ctx.drawImage(portIcon, f.posX + f.width - 24, py - 7, 14, 14);
                    }
                    ctx.fillStyle = "#94a3b8";
                    ctx.font = "8.5px sans-serif";
                    ctx.textAlign = "right";
                    ctx.fillText(formatRate(port.ratePerSec, port.type), f.posX + f.width - 27, py);
                }
            }
        }
    }

    function drawStickyNotes() {
        if (!boardData.stickyNotes) return;
        for (const n of boardData.stickyNotes) {
            const colorHex = n.color ? "#" + (n.color & 0x00FFFFFF).toString(16).padStart(6, "0") : "#f59e0b";
            ctx.save();
            ctx.fillStyle = colorHex + "26";
            ctx.strokeStyle = colorHex + "aa";
            ctx.lineWidth = 1.5;
            roundRect(ctx, n.posX, n.posY, n.width, n.height, 6);
            ctx.fill();
            ctx.stroke();

            // Note title
            ctx.fillStyle = colorHex;
            ctx.font = "bold 12px sans-serif";
            ctx.fillText(n.title || "Note", n.posX + 8, n.posY + 18);

            // Note content
            ctx.fillStyle = "#e2e8f0";
            ctx.font = "11px sans-serif";
            const lines = (n.content || "").split("\n");
            let lineY = n.posY + 34;
            for (const line of lines) {
                if (lineY > n.posY + n.height - 8) break;
                ctx.fillText(line, n.posX + 8, lineY);
                lineY += 16;
            }
            ctx.restore();
        }
    }

    function drawConnections() {
        if (!boardData.connections) return;
        for (const c of boardData.connections) {
            if (c.isInternalFolded) continue;

            let p1 = null;
            let fromDirX = 1;
            if (c.fromFoldedFrame) {
                const fromFrame = findFrame(c.fromFoldedFrame);
                if (!fromFrame) continue;
                const pIdx = c.fromFoldedPortIndex || 0;
                p1 = { x: fromFrame.posX + fromFrame.width - 5, y: fromFrame.posY + 44 + pIdx * 18 + 8 };
                fromDirX = 1;
            } else {
                const fromNode = findNode(c.fromNode);
                if (!fromNode) continue;
                const fromIdx = parsePortIndex(c.fromPort);
                p1 = getNodeOutputPortPos(fromNode, fromIdx);
                fromDirX = fromNode.isFlipped ? -1 : 1;
            }

            let p2 = null;
            let toDirX = -1;
            if (c.toFoldedFrame) {
                const toFrame = findFrame(c.toFoldedFrame);
                if (!toFrame) continue;
                const pIdx = c.toFoldedPortIndex || 0;
                p2 = { x: toFrame.posX + 5, y: toFrame.posY + 44 + pIdx * 18 + 8 };
                toDirX = -1;
            } else {
                const toNode = findNode(c.toNode);
                if (!toNode) continue;
                const toIdx = parsePortIndex(c.toPort);
                p2 = getNodeInputPortPos(toNode, toIdx);
                toDirX = toNode.isFlipped ? 1 : -1;
            }

            const dx = Math.max(40, Math.abs(p2.x - p1.x) * 0.5);
            const cp1x = p1.x + fromDirX * dx;
            const cp1y = p1.y;
            const cp2x = p2.x + toDirX * dx;
            const cp2y = p2.y;

            ctx.save();
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
            ctx.strokeStyle = "#38bdf8";
            ctx.lineWidth = 2.5;
            ctx.stroke();

            // Midpoint flow badge
            const midX = 0.125 * p1.x + 0.375 * cp1x + 0.375 * cp2x + 0.125 * p2.x;
            const midY = 0.125 * p1.y + 0.375 * cp1y + 0.375 * cp2y + 0.125 * p2.y;
            drawFlowBadge(midX, midY, c.flowRate, c.unit);

            ctx.restore();
        }
    }

    function drawFlowBadge(x, y, rate, unit) {
        let text;
        if (unit === "L/s" || unit === "mB/s" || unit === "B/s") {
            text = formatFluidRate(rate);
        } else if (unit === "su") {
            text = formatStressRate(rate);
        } else if (unit === "items/s") {
            text = formatItemRate(rate);
        } else {
            text = rate + (unit ? " " + unit : "");
        }
        ctx.font = "bold 9px sans-serif";
        const tw = ctx.measureText(text).width;
        ctx.fillStyle = "rgba(15, 23, 42, 0.9)";
        ctx.strokeStyle = "#334155";
        ctx.lineWidth = 1;
        roundRect(ctx, x - tw / 2 - 4, y - 7, tw + 8, 14, 4);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = "#38bdf8";
        ctx.fillText(text, x - tw / 2, y + 3);
    }

    function drawNodes() {
        if (!boardData.nodes) return;
        for (const n of boardData.nodes) {
            if (n.isFoldedInFrame) {
                continue;
            }
            if (n.isEmbedded) {
                drawEmbeddedSubCard(n);
                continue;
            }
            if (n.type === "JUNCTION" || n.type === "PIN") {
                drawJunctionNode(n);
            } else {
                drawNodeCard(n);
            }
        }
    }

    function drawEmbeddedSubCard(n) {
        const dims = resolveNodeDimensions(n);
        const cardW = dims.w;
        const cardH = dims.h;
        const isHovered = hoveredObject && hoveredObject.type === "node" && hoveredObject.node.id === n.id;
        ctx.save();

        ctx.fillStyle = isHovered ? "#172033" : "#101623";
        ctx.strokeStyle = isHovered ? "#38bdf8" : "#232d3f";
        ctx.lineWidth = isHovered ? 1.5 : 1;
        roundRect(ctx, n.posX, n.posY, cardW, cardH, 4);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = "#070b12";
        roundRect(ctx, n.posX, n.posY, cardW, 16, [4, 4, 0, 0]);
        ctx.fill();
        ctx.strokeStyle = "#1e293b";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(n.posX, n.posY + 16);
        ctx.lineTo(n.posX + cardW, n.posY + 16);
        ctx.stroke();

        const recipeName = n.recipeName || n.title || "Recipe";
        ctx.fillStyle = "#e2e8f0";
        ctx.font = "bold 9.5px sans-serif";
        ctx.textAlign = "left";
        ctx.textBaseline = "middle";
        ctx.fillText(truncateText(ctx, recipeName, cardW - 55), n.posX + 6, n.posY + 8);

        const count = (n.metrics && typeof n.metrics.machineCount === "number") ? n.metrics.machineCount : 1.0;
        const countStr = count.toFixed(2) + "x";
        ctx.fillStyle = "#fcd34d";
        ctx.font = "bold 9px sans-serif";
        ctx.textAlign = "right";
        ctx.fillText(countStr, n.posX + cardW - 6, n.posY + 8);

        const inputs = n.inputs || [];
        const outputs = n.outputs || [];
        const maxRows = Math.max(inputs.length, outputs.length);

        for (let i = 0; i < maxRows; i++) {
            const rowY = n.posY + 16 + i * 16;
            const py = rowY + 8;

            if (i < inputs.length) {
                const port = inputs[i];
                const isFluid = port.type === "FLUID";
                const portColor = isFluid ? "#06b6d4" : "#38bdf8";

                drawPortPin(ctx, n.posX + 4, py, portColor);

                const icon = getIconImage(isFluid ? "fluid" : "item", port.id);
                if (icon && icon.complete && icon.naturalWidth > 0) {
                    ctx.drawImage(icon, n.posX + 8, rowY + 1, 14, 14);
                } else {
                    drawPortFallbackIcon(ctx, n.posX + 8, rowY + 1, isFluid);
                }

                ctx.fillStyle = "#94a3b8";
                ctx.font = "8.5px sans-serif";
                ctx.textAlign = "left";
                ctx.textBaseline = "middle";
                ctx.fillText(formatRate(port.ratePerSec, port.type), n.posX + 25, py);
            }

            if (inputs.length > 0 && outputs.length > 0) {
                ctx.fillStyle = "#475569";
                ctx.font = "8px sans-serif";
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText("──>", n.posX + cardW / 2, py);
            }

            if (i < outputs.length) {
                const port = outputs[i];
                const isFluid = port.type === "FLUID";
                const portColor = isFluid ? "#06b6d4" : "#f59e0b";

                ctx.fillStyle = "#94a3b8";
                ctx.font = "8.5px sans-serif";
                ctx.textAlign = "right";
                ctx.textBaseline = "middle";
                ctx.fillText(formatRate(port.ratePerSec, port.type), n.posX + cardW - 25, py);

                const icon = getIconImage(isFluid ? "fluid" : "item", port.id);
                if (icon && icon.complete && icon.naturalWidth > 0) {
                    ctx.drawImage(icon, n.posX + cardW - 22, rowY + 1, 14, 14);
                } else {
                    drawPortFallbackIcon(ctx, n.posX + cardW - 22, rowY + 1, isFluid);
                }

                drawPortPin(ctx, n.posX + cardW - 4, py, portColor);
            }
        }

        ctx.restore();
    }


    function drawJunctionNode(n) {
        const isHovered = hoveredObject && hoveredObject.type === "node" && hoveredObject.node.id === n.id;
        ctx.save();

        const bg = isHovered ? "#334155" : "#1e293b";
        const border = isHovered ? "#38bdf8" : "#64748b";

        ctx.fillStyle = bg;
        ctx.strokeStyle = border;
        ctx.lineWidth = isHovered ? 2 : 1.5;
        roundRect(ctx, n.posX, n.posY, n.width, n.height, 6);
        ctx.fill();
        ctx.stroke();

        const firstIn = (n.inputs && n.inputs.length > 0) ? n.inputs[0] : null;
        const firstOut = (n.outputs && n.outputs.length > 0) ? n.outputs[0] : null;
        const stack = firstOut || firstIn;
        if (stack && stack.id) {
            const iconType = stack.type === "FLUID" ? "fluid" : "item";
            const icon = getIconImage(iconType, stack.id);
            if (icon && icon.complete && icon.naturalWidth > 0) {
                ctx.drawImage(icon, n.posX + 6, n.posY + 6, 20, 20);
            } else {
                ctx.fillStyle = "#38bdf8";
                ctx.font = "bold 12px sans-serif";
                ctx.fillText("↔", n.posX + 10, n.posY + 20);
            }
        } else {
            ctx.fillStyle = "#94a3b8";
            ctx.font = "bold 12px sans-serif";
            ctx.fillText("↔", n.posX + 10, n.posY + 20);
        }

        const pIn = getNodeInputPortPos(n, 0);
        const pOut = getNodeOutputPortPos(n, 0);
        ctx.fillStyle = "#38bdf8";
        ctx.beginPath();
        ctx.arc(pIn.x, pIn.y, 3, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.arc(pOut.x, pOut.y, 3, 0, Math.PI * 2);
        ctx.fill();

        ctx.restore();

        if (n.linkedSource) {
            drawLinkedBadge(n);
        }
    }

    function resolveLinkedBadgeVisual(n) {
        const ls = n.linkedSource;
        if (!ls) return null;
        if (ls.isBroken) {
            return { text: "⚠ Broken Link", border: "#ef4444", bg: "#450a0a", textCol: "#fca5a5" };
        }
        if (ls.isCircular) {
            return { text: "⚠ Circular Loop", border: "#a855f7", bg: "#3b0764", textCol: "#e9d5ff" };
        }
        if (ls.isStarved) {
            const bound = (n.outputs && n.outputs[0]) || (n.inputs && n.inputs[0]);
            const allocStr = formatRate(ls.allocatedInputRate || 0, bound?.type);
            const demandStr = formatRate(ls.demandRate || 0, bound?.type);
            return { text: `⚠ ${allocStr} / ${demandStr}`, border: "#f97316", bg: "#431407", textCol: "#fed7aa" };
        }
        const pageTitle = ls.pageName || ls.pageId || "Page";
        return { text: `🔗 ${pageTitle}`, border: "#38bdf8", bg: "#082f49", textCol: "#bae6fd" };
    }

    function getLinkedBadgeBounds(n) {
        if (!n || !n.linkedSource) return null;
        const visual = resolveLinkedBadgeVisual(n);
        if (!visual) return null;

        ctx.save();
        ctx.font = "bold 9px sans-serif";
        const tw = ctx.measureText(visual.text).width;
        ctx.restore();

        const badgeW = Math.max(36, tw + 8);
        const badgeX = n.posX + (n.width || 32) / 2 - badgeW / 2;
        const badgeY = n.posY - 14;
        return { x: badgeX, y: badgeY, w: badgeW, h: 12, visual, textWidth: tw };
    }

    function drawLinkedBadge(n) {
        const bounds = getLinkedBadgeBounds(n);
        if (!bounds) return;

        const isBadgeHovered = hoveredObject && hoveredObject.type === "linked_badge" && hoveredObject.node.id === n.id;
        const border = isBadgeHovered ? "#ffffff" : bounds.visual.border;

        ctx.save();
        ctx.fillStyle = bounds.visual.bg;
        ctx.strokeStyle = border;
        ctx.lineWidth = isBadgeHovered ? 1.5 : 1;
        roundRect(ctx, bounds.x, bounds.y, bounds.w, bounds.h, 3);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = bounds.visual.textCol;
        ctx.font = "bold 9px sans-serif";
        ctx.fillText(bounds.visual.text, bounds.x + 4, bounds.y + 9);
        ctx.restore();
    }

    function resolveNodeDimensions(n) {
        if (!n) return { w: 32, h: 32 };
        if (n.isEmbedded) {
            const inputCount = n.inputs ? n.inputs.length : 0;
            const outputCount = n.outputs ? n.outputs.length : 0;
            const maxRows = Math.max(inputCount, outputCount);
            const autoHeight = Math.max(40, 16 + maxRows * 16 + 4);
            return { w: n.width || 245, h: n.height || autoHeight };
        }
        if (n.type === "JUNCTION" || n.type === "PIN") {
            return { w: n.width || 32, h: n.height || 32 };
        }
        const inputCount = n.inputs ? n.inputs.length : 0;
        const outputCount = n.outputs ? n.outputs.length : 0;
        const maxRows = Math.max(inputCount, outputCount);
        const isModule = n.type === "COMPOUND";
        const contentStartY = isModule ? 62 : 80;
        const autoHeight = contentStartY + Math.max(1, maxRows) * 18 + 8;
        // Normalize width: default in-game card width is 245; ignore legacy 300 padding
        const w = (n.width && n.width !== 300) ? n.width : 245;
        const h = Math.max(n.height || 0, autoHeight);
        return { w, h };
    }

    function drawSlotPlate(ctx, x, y, w, h) {
        ctx.fillStyle = "#030712";
        ctx.strokeStyle = "#334155";
        ctx.lineWidth = 1;
        roundRect(ctx, x, y, w, h, 2);
        ctx.fill();
        ctx.stroke();
    }

    function drawNodeCard(n) {
        const dims = resolveNodeDimensions(n);
        const cardW = dims.w;
        const cardH = dims.h;
        const isHovered = hoveredObject && hoveredObject.type === "node" && hoveredObject.node.id === n.id;
        ctx.save();

        // 1. Card background with sleek border
        ctx.fillStyle = isHovered ? "#141c2b" : "#0d131f";
        ctx.strokeStyle = isHovered ? "#38bdf8" : "#243247";
        ctx.lineWidth = isHovered ? 2 : 1;
        roundRect(ctx, n.posX, n.posY, cardW, cardH, 6);
        ctx.fill();
        ctx.stroke();

        // 2. Node header bar (height: 22)
        ctx.fillStyle = "#070b12";
        roundRect(ctx, n.posX, n.posY, cardW, 22, [6, 6, 0, 0]);
        ctx.fill();
        ctx.strokeStyle = "#1e293b";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(n.posX, n.posY + 22);
        ctx.lineTo(n.posX + cardW, n.posY + 22);
        ctx.stroke();

        // 3. Machine Icon (Slot style)
        drawSlotPlate(ctx, n.posX + 3, n.posY + 3, 16, 16);
        const machineIcon = getIconImage("item", n.machineId);
        if (machineIcon && machineIcon.complete && machineIcon.naturalWidth > 0) {
            ctx.drawImage(machineIcon, n.posX + 3, n.posY + 3, 16, 16);
        } else {
            ctx.fillStyle = "#94a3b8";
            ctx.font = "bold 10px sans-serif";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText("⚙", n.posX + 11, n.posY + 11);
        }

        // 4. Node Title
        ctx.fillStyle = "#f8fafc";
        ctx.font = "bold 10px sans-serif";
        ctx.textAlign = "left";
        ctx.textBaseline = "middle";
        const titleX = n.posX + 23;
        const badgeW = n.tier ? 28 : 0;
        const maxTitleW = cardW - 32 - badgeW;
        ctx.fillText(truncateText(ctx, stripFormatting(n.title || "Machine"), maxTitleW), titleX, n.posY + 11);

        // 5. Tier badge in header
        if (n.tier) {
            const tierColor = getTierColor(n.tier);
            ctx.fillStyle = tierColor + "26";
            ctx.strokeStyle = tierColor;
            ctx.lineWidth = 1;
            const bx = n.posX + cardW - badgeW - 4;
            roundRect(ctx, bx, n.posY + 3, badgeW, 16, 3);
            ctx.fill();
            ctx.stroke();

            ctx.fillStyle = tierColor;
            ctx.font = "bold 8.5px sans-serif";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText(n.tier, bx + badgeW / 2, n.posY + 11);
        }

        // 6. Metrics / Controls rows
        if (n.metrics) {
            // Row 1: Machine count & Overclock mode (y + 26 ~ y + 42)
            const count = n.metrics.machineCount || 1;
            const countStr = count + "x";
            const ocMode = n.metrics.overclockMode || "STANDARD";

            ctx.font = "bold 9.5px sans-serif";
            ctx.textAlign = "left";
            ctx.textBaseline = "middle";

            // Count badge
            const countW = ctx.measureText(countStr).width + 8;
            ctx.fillStyle = "#1e293b";
            ctx.strokeStyle = "#334155";
            ctx.lineWidth = 1;
            roundRect(ctx, n.posX + 6, n.posY + 26, countW, 15, 3);
            ctx.fill();
            ctx.stroke();
            ctx.fillStyle = "#38bdf8";
            ctx.fillText(countStr, n.posX + 10, n.posY + 33);

            // Parallel / OC mode badge
            let curX = n.posX + 6 + countW + 4;
            if (n.metrics.parallel && n.metrics.parallel > 1) {
                const parStr = n.metrics.parallel + "P";
                const parW = ctx.measureText(parStr).width + 8;
                ctx.fillStyle = "#1e293b";
                ctx.strokeStyle = "#334155";
                roundRect(ctx, curX, n.posY + 26, parW, 15, 3);
                ctx.fill();
                ctx.stroke();
                ctx.fillStyle = "#a855f7";
                ctx.fillText(parStr, curX + 4, n.posY + 33);
                curX += parW + 4;
            }

            if (ocMode && ocMode !== "STANDARD") {
                const ocStr = ocMode.replace("_", " ");
                ctx.font = "8px sans-serif";
                ctx.fillStyle = "#94a3b8";
                ctx.fillText(ocStr, curX, n.posY + 33);
            }

            // Row 2: Power & Duration (y + 46 ~ y + 62)
            const eut = typeof n.metrics.eut === "number" ? n.metrics.eut : (parseFloat(n.metrics.eut) || 0);
            const eutStr = formatPower(eut);
            const dur = typeof n.metrics.durationSec === "number" ? Math.round(n.metrics.durationSec * 1000) / 1000 : (n.metrics.durationSec || 0);
            const durStr = dur + "s" + (n.metrics.efficiency ? " (" + Math.round(n.metrics.efficiency * 100) + "%)" : "");

            ctx.font = "9px sans-serif";
            ctx.textAlign = "left";
            ctx.textBaseline = "middle";
            ctx.fillStyle = eut < 0 ? "#f97316" : (eut > 0 ? "#10b981" : "#94a3b8");
            ctx.fillText(eutStr, n.posX + 6, n.posY + 54);

            ctx.textAlign = "right";
            ctx.fillStyle = "#94a3b8";
            ctx.fillText(durStr, n.posX + cardW - 6, n.posY + 54);

            // Separator line at y + 76 (or y + 58 for module)
            const sepY = n.posY + (n.type === "COMPOUND" ? 58 : 76);
            ctx.strokeStyle = "#1e293b";
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(n.posX + 4, sepY);
            ctx.lineTo(n.posX + cardW - 4, sepY);
            ctx.stroke();
        }

        // 7. Ports
        drawNodePorts(n, dims);

        ctx.restore();
    }

    function drawNodePorts(n, dims) {
        const cardW = dims.w;
        const isModule = n.type === "COMPOUND";
        const startY = n.posY + (isModule ? 62 : 80);
        const rowH = 18;

        const inputs = n.inputs || [];
        const outputs = n.outputs || [];
        const hasBoth = inputs.length > 0 && outputs.length > 0;
        const halfW = hasBoth ? (cardW - 12) / 2 : (cardW - 12);

        if (hasBoth) {
            const maxRows = Math.max(inputs.length, outputs.length);
            const sepHeight = maxRows * rowH;
            ctx.strokeStyle = "rgba(51, 65, 85, 0.35)";
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(n.posX + cardW / 2, startY);
            ctx.lineTo(n.posX + cardW / 2, startY + sepHeight - 2);
            ctx.stroke();
        }

        // 1. Render Input Ports (Left side, or right side if flipped)
        for (let i = 0; i < inputs.length; i++) {
            const port = inputs[i];
            const py = startY + i * rowH;
            const isFluid = port.type === "FLUID";
            const portColor = isFluid ? "#06b6d4" : (port.type === "STRESS" ? "#a855f7" : "#f59e0b");

            const pinX = n.isFlipped ? n.posX + cardW : n.posX;
            const pinY = py + 8;
            drawPortPin(ctx, pinX, pinY, portColor);

            const slotX = n.isFlipped ? (n.posX + cardW - 20) : (n.posX + 4);
            const slotY = py;
            drawSlotPlate(ctx, slotX, slotY, 16, 16);

            const icon = getIconImage(isFluid ? "fluid" : "item", port.id);
            if (icon && icon.complete && icon.naturalWidth > 0) {
                if (isFluid) {
                    ctx.save();
                    roundRect(ctx, slotX + 1, slotY + 1, 14, 14, 2);
                    ctx.clip();
                    ctx.drawImage(icon, slotX + 1, slotY + 1, 14, 14);
                    ctx.restore();
                } else {
                    ctx.drawImage(icon, slotX + 1, slotY + 1, 14, 14);
                }
            } else {
                drawPortFallbackIcon(ctx, slotX, slotY, isFluid);
            }

            const textX = n.isFlipped ? (slotX - 4) : (slotX + 19);
            ctx.textAlign = n.isFlipped ? "right" : "left";
            ctx.textBaseline = "middle";

            const maxTextW = halfW - 25;
            const rateStr = formatRate(port.ratePerSec, port.type);
            const nameStr = stripFormatting(port.displayName || port.id || "");
            const fullText = nameStr + " " + rateStr;

            ctx.fillStyle = "#cbd5e1";
            ctx.font = "9px sans-serif";
            ctx.fillText(truncateText(ctx, fullText, maxTextW), textX, py + 8);
        }

        // 2. Render Output Ports (Right side, or left side if flipped)
        for (let i = 0; i < outputs.length; i++) {
            const port = outputs[i];
            const py = startY + i * rowH;
            const isFluid = port.type === "FLUID";
            const portColor = isFluid ? "#06b6d4" : (port.type === "STRESS" ? "#a855f7" : "#f59e0b");

            const pinX = n.isFlipped ? n.posX : n.posX + cardW;
            const pinY = py + 8;
            drawPortPin(ctx, pinX, pinY, portColor);

            const slotX = n.isFlipped ? (n.posX + 4) : (n.posX + cardW - 20);
            const slotY = py;
            drawSlotPlate(ctx, slotX, slotY, 16, 16);

            const icon = getIconImage(isFluid ? "fluid" : "item", port.id);
            if (icon && icon.complete && icon.naturalWidth > 0) {
                if (isFluid) {
                    ctx.save();
                    roundRect(ctx, slotX + 1, slotY + 1, 14, 14, 2);
                    ctx.clip();
                    ctx.drawImage(icon, slotX + 1, slotY + 1, 14, 14);
                    ctx.restore();
                } else {
                    ctx.drawImage(icon, slotX + 1, slotY + 1, 14, 14);
                }
            } else {
                drawPortFallbackIcon(ctx, slotX, slotY, isFluid);
            }

            const textX = n.isFlipped ? (slotX + 19) : (slotX - 4);
            ctx.textAlign = n.isFlipped ? "left" : "right";
            ctx.textBaseline = "middle";

            const maxTextW = halfW - 25;
            const rateStr = formatRate(port.ratePerSec, port.type);
            const nameStr = stripFormatting(port.displayName || port.id || "");
            const fullText = rateStr + " " + nameStr;

            ctx.fillStyle = "#cbd5e1";
            ctx.font = "9px sans-serif";
            ctx.fillText(truncateText(ctx, fullText, maxTextW), textX, py + 8);
        }
    }

    function drawPortPin(ctx, x, y, color) {
        ctx.fillStyle = "#020617";
        ctx.beginPath();
        ctx.arc(x, y, 4.5, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.arc(x, y, 3, 0, Math.PI * 2);
        ctx.fill();
    }

    function trimZeros(valStr) {
        if (!valStr || !valStr.includes(".")) return valStr;
        return valStr.replace(/(\.\d*?[1-9])0+$/g, "$1").replace(/\.0+$/g, "");
    }

    function formatFluidRate(val, mode = currentUnitMode) {
        if (typeof val !== "number" || isNaN(val)) val = 0;
        if (val === 0) return mode === "b" ? "0 B/s" : "0 mB/s";

        const abs = Math.abs(val);
        const sign = val < 0 ? "-" : "";

        if (mode === "mb") {
            if (abs >= 1e9) {
                return sign + trimZeros((abs / 1e9).toFixed(2)) + " G mB/s";
            } else if (abs >= 1e6) {
                return sign + trimZeros((abs / 1e6).toFixed(2)) + " M mB/s";
            } else if (abs >= 1e4) {
                return sign + trimZeros((abs / 1e3).toFixed(2)) + " k mB/s";
            } else if (abs >= 100) {
                return sign + trimZeros(abs.toFixed(1)) + " mB/s";
            } else if (abs >= 1) {
                return sign + trimZeros(abs.toFixed(2)) + " mB/s";
            } else {
                return sign + trimZeros(abs.toFixed(3)) + " mB/s";
            }
        } else if (mode === "b") {
            const b = abs / 1000.0;
            if (b >= 1e6) {
                return sign + trimZeros((b / 1e6).toFixed(2)) + " MB/s";
            } else if (b >= 1e3) {
                return sign + trimZeros((b / 1e3).toFixed(2)) + " kB/s";
            } else if (b >= 100) {
                return sign + trimZeros(b.toFixed(1)) + " B/s";
            } else if (b >= 1) {
                return sign + trimZeros(b.toFixed(2)) + " B/s";
            } else {
                return sign + trimZeros(b.toFixed(3)) + " B/s";
            }
        } else {
            // "auto" mode: >= 1000 mB scales to Buckets (B, kB, MB)
            if (abs >= 1000.0) {
                const b = abs / 1000.0;
                if (b >= 1e6) {
                    return sign + trimZeros((b / 1e6).toFixed(2)) + " MB/s";
                } else if (b >= 1e3) {
                    return sign + trimZeros((b / 1e3).toFixed(2)) + " kB/s";
                } else if (b >= 100) {
                    return sign + trimZeros(b.toFixed(1)) + " B/s";
                } else if (b >= 1) {
                    return sign + trimZeros(b.toFixed(2)) + " B/s";
                } else {
                    return sign + trimZeros(b.toFixed(3)) + " B/s";
                }
            } else {
                if (abs >= 100) {
                    return sign + trimZeros(abs.toFixed(1)) + " mB/s";
                } else if (abs >= 1) {
                    return sign + trimZeros(abs.toFixed(2)) + " mB/s";
                } else {
                    return sign + trimZeros(abs.toFixed(3)) + " mB/s";
                }
            }
        }
    }

    function formatItemRate(val) {
        if (typeof val !== "number" || isNaN(val)) val = 0;
        if (val === 0) return "0/s";

        const abs = Math.abs(val);
        const sign = val < 0 ? "-" : "";

        if (abs >= 1e6) {
            return sign + trimZeros((abs / 1e6).toFixed(2)) + "M/s";
        } else if (abs >= 1e4) {
            return sign + trimZeros((abs / 1e3).toFixed(2)) + "k/s";
        } else if (abs >= 100) {
            return sign + trimZeros(abs.toFixed(1)) + "/s";
        } else if (abs >= 1) {
            return sign + trimZeros(abs.toFixed(2)) + "/s";
        } else {
            return sign + trimZeros(abs.toFixed(3)) + "/s";
        }
    }

    function formatStressRate(val) {
        if (typeof val !== "number" || isNaN(val)) val = 0;
        const abs = Math.abs(val);
        const sign = val < 0 ? "-" : "";
        if (abs >= 1e6) return sign + trimZeros((abs / 1e6).toFixed(2)) + "M SU";
        if (abs >= 1e3) return sign + trimZeros((abs / 1e3).toFixed(1)) + "k SU";
        return sign + trimZeros(abs.toFixed(0)) + " SU";
    }

    function formatRate(val, type) {
        if (type === "FLUID") return formatFluidRate(val);
        if (type === "STRESS") return formatStressRate(val);
        return formatItemRate(val);
    }

    function formatPower(eut) {
        if (typeof eut !== "number" || isNaN(eut)) eut = 0;
        if (eut === 0) return "0 EU/t";
        const abs = Math.abs(eut);
        const sign = eut < 0 ? "-" : "+";
        if (abs >= 1e6) {
            return sign + trimZeros((abs / 1e6).toFixed(2)) + "M EU/t";
        } else if (abs >= 1e4) {
            return sign + trimZeros((abs / 1e3).toFixed(2)) + "k EU/t";
        } else if (abs >= 100) {
            return sign + trimZeros(abs.toFixed(1)) + " EU/t";
        } else {
            return sign + trimZeros(abs.toFixed(2)) + " EU/t";
        }
    }

    function drawPortFallbackIcon(ctx, sx, sy, isFluid) {
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        if (isFluid) {
            ctx.fillStyle = "#06b6d4";
            ctx.font = "10px sans-serif";
            ctx.fillText("💧", sx + 9, sy + 9);
        } else {
            ctx.fillStyle = "#f59e0b";
            ctx.font = "bold 9px sans-serif";
            ctx.fillText("⬡", sx + 9, sy + 9);
        }
    }

    function roundRect(ctx, x, y, width, height, radius) {
        if (typeof radius === "undefined") radius = 5;
        if (typeof radius === "number") {
            radius = [radius, radius, radius, radius];
        }
        ctx.beginPath();
        ctx.moveTo(x + radius[0], y);
        ctx.lineTo(x + width - radius[1], y);
        ctx.quadraticCurveTo(x + width, y, x + width, y + radius[1]);
        ctx.lineTo(x + width, y + height - radius[2]);
        ctx.quadraticCurveTo(x + width, y + height, x + width - radius[2], y + height);
        ctx.lineTo(x + radius[3], y + height);
        ctx.quadraticCurveTo(x, y + height, x, y + height - radius[3]);
        ctx.lineTo(x, y + radius[0]);
        ctx.quadraticCurveTo(x, y, x + radius[0], y);
        ctx.closePath();
    }

    function stripFormatting(text) {
        if (!text) return "";
        return String(text).replace(/§\s*[0-9a-fk-or]/gi, "").replace(/\s{2,}/g, " ").trim();
    }

    function truncateText(ctx, text, maxW) {
        if (!text) return "";
        if (ctx.measureText(text).width <= maxW) return text;
        let truncated = text;
        while (truncated.length > 1 && ctx.measureText(truncated + "…").width > maxW) {
            truncated = truncated.slice(0, -1);
        }
        return truncated + "…";
    }

    function getTierColor(tier) {
        switch (tier) {
            case "ULV": return "#94a3b8";
            case "LV":  return "#38bdf8";
            case "MV":  return "#f59e0b";
            case "HV":  return "#f97316";
            case "EV":  return "#8b5cf6";
            case "IV":  return "#3b82f6";
            case "LuV": return "#ec4899";
            case "ZPM": return "#10b981";
            case "UV":  return "#84cc16";
            case "UHV": return "#e11d48";
            default:    return "#38bdf8";
        }
    }

    function findNode(id) {
        return (boardData.nodes || []).find(n => n.id === id);
    }

    function findFrame(id) {
        return (boardData.frames || []).find(f => f.id === id);
    }

    function parsePortIndex(portStr) {
        if (!portStr) return 0;
        const parts = portStr.split("_");
        return parts.length > 1 ? parseInt(parts[1], 10) || 0 : 0;
    }

    function getNodeInputPortPos(node, portIdx) {
        const dims = resolveNodeDimensions(node);
        if (node.isEmbedded) {
            return {
                x: node.posX + 4,
                y: node.posY + 16 + portIdx * 16 + 8
            };
        }
        if (node.type === "JUNCTION" || node.type === "PIN") {
            return {
                x: node.posX + (node.isFlipped ? dims.w : 0),
                y: node.posY + dims.h / 2
            };
        }
        const isModule = node.type === "COMPOUND";
        const startY = node.posY + (isModule ? 62 : 80);
        const px = node.isFlipped ? (node.posX + dims.w) : node.posX;
        return { x: px, y: startY + portIdx * 18 + 8 };
    }

    function getNodeOutputPortPos(node, portIdx) {
        const dims = resolveNodeDimensions(node);
        if (node.isEmbedded) {
            return {
                x: node.posX + dims.w - 4,
                y: node.posY + 16 + portIdx * 16 + 8
            };
        }
        if (node.type === "JUNCTION" || node.type === "PIN") {
            return {
                x: node.posX + (node.isFlipped ? 0 : dims.w),
                y: node.posY + dims.h / 2
            };
        }
        const isModule = node.type === "COMPOUND";
        const startY = node.posY + (isModule ? 62 : 80);
        const px = node.isFlipped ? node.posX : (node.posX + dims.w);
        return { x: px, y: startY + portIdx * 18 + 8 };
    }

    function screenToCanvas(sx, sy) {
        return {
            x: (sx - panX) / zoom,
            y: (sy - panY) / zoom
        };
    }

    function fitView() {
        const nodes = boardData.nodes || [];
        const frames = boardData.frames || [];
        const notes = boardData.stickyNotes || [];

        if (nodes.length === 0 && frames.length === 0 && notes.length === 0) {
            resetView();
            return;
        }

        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;

        for (const n of nodes) {
            const dims = resolveNodeDimensions(n);
            minX = Math.min(minX, n.posX);
            minY = Math.min(minY, n.posY);
            maxX = Math.max(maxX, n.posX + dims.w);
            maxY = Math.max(maxY, n.posY + dims.h);
        }
        for (const f of frames) {
            minX = Math.min(minX, f.posX);
            minY = Math.min(minY, f.posY);
            maxX = Math.max(maxX, f.posX + f.width);
            maxY = Math.max(maxY, f.posY + f.height);
        }
        for (const n of notes) {
            minX = Math.min(minX, n.posX);
            minY = Math.min(minY, n.posY);
            maxX = Math.max(maxX, n.posX + n.width);
            maxY = Math.max(maxY, n.posY + n.height);
        }

        const padding = 60;
        const dpr = window.devicePixelRatio || 1;
        const viewW = canvas.width / dpr;
        const viewH = canvas.height / dpr;

        const boundsW = Math.max(100, maxX - minX);
        const boundsH = Math.max(100, maxY - minY);

        const zoomX = (viewW - padding * 2) / boundsW;
        const zoomY = (viewH - padding * 2) / boundsH;
        zoom = Math.min(1.5, Math.max(0.15, Math.min(zoomX, zoomY)));

        panX = (viewW - boundsW * zoom) / 2 - minX * zoom;
        panY = (viewH - boundsH * zoom) / 2 - minY * zoom;

        updateZoomUI();
        requestRender();
    }

    function resetView() {
        panX = 40.0;
        panY = 40.0;
        zoom = 1.0;
        updateZoomUI();
        requestRender();
    }

    function updateZoomUI() {
        zoomLevelElem.textContent = Math.round(zoom * 100) + "%";
    }

    let clickStartX = 0;
    let clickStartY = 0;

    canvas.addEventListener("mousedown", (e) => {
        if (e.button === 0) {
            isDragging = true;
            dragStartX = e.clientX - panX;
            dragStartY = e.clientY - panY;
            clickStartX = e.clientX;
            clickStartY = e.clientY;
        }
    });

    window.addEventListener("mousemove", (e) => {
        const rect = canvas.getBoundingClientRect();
        mouseX = e.clientX - rect.left;
        mouseY = e.clientY - rect.top;

        if (isDragging) {
            panX = e.clientX - dragStartX;
            panY = e.clientY - dragStartY;
            requestRender();
            return;
        }

        checkHover(mouseX, mouseY);
    });

    window.addEventListener("mouseup", (e) => {
        if (e.button === 0) {
            isDragging = false;
        }
    });

    canvas.addEventListener("click", (e) => {
        const dist = Math.hypot(e.clientX - clickStartX, e.clientY - clickStartY);
        if (dist > 5) return;

        const rect = canvas.getBoundingClientRect();
        const sx = e.clientX - rect.left;
        const sy = e.clientY - rect.top;
        const pt = screenToCanvas(sx, sy);

        if (boardData.nodes) {
            for (const n of boardData.nodes) {
                if (n.linkedSource && n.linkedSource.pageId && !n.linkedSource.isBroken) {
                    const bBounds = getLinkedBadgeBounds(n);
                    if (bBounds && pt.x >= bBounds.x && pt.x <= bBounds.x + bBounds.w && pt.y >= bBounds.y && pt.y <= bBounds.y + bBounds.h) {
                        selectPage(n.linkedSource.pageId, true);
                        return;
                    }
                }
            }
        }
    });

    canvas.addEventListener("wheel", (e) => {
        e.preventDefault();
        const factor = e.deltaY < 0 ? 1.15 : 0.87;
        const newZoom = Math.min(4.0, Math.max(0.15, zoom * factor));

        const pt = screenToCanvas(mouseX, mouseY);
        panX = mouseX - pt.x * newZoom;
        panY = mouseY - pt.y * newZoom;
        zoom = newZoom;

        updateZoomUI();
        requestRender();
        checkHover(mouseX, mouseY);
    }, { passive: false });

    window.addEventListener("keydown", (e) => {
        if (e.key === "f" || e.key === "F") {
            fitView();
        } else if (e.key === "Home") {
            resetView();
        } else if (e.key === "r" || e.key === "R") {
            fetchBoardData();
        } else if (e.key === "+" || e.key === "=") {
            zoom = Math.min(4.0, zoom * 1.2);
            updateZoomUI();
            requestRender();
        } else if (e.key === "-" || e.key === "_") {
            zoom = Math.max(0.15, zoom / 1.2);
            updateZoomUI();
            requestRender();
        }
    });

    document.getElementById("btnFit").addEventListener("click", fitView);
    document.getElementById("btnReset").addEventListener("click", resetView);
    document.getElementById("btnRefresh").addEventListener("click", fetchBoardData);
    document.getElementById("btnZoomIn").addEventListener("click", () => {
        zoom = Math.min(4.0, zoom * 1.2);
        updateZoomUI();
        requestRender();
    });
    document.getElementById("btnZoomOut").addEventListener("click", () => {
        zoom = Math.max(0.15, zoom / 1.2);
        updateZoomUI();
        requestRender();
    });

    function checkHover(sx, sy) {
        const pt = screenToCanvas(sx, sy);
        let found = null;

        if (boardData.nodes) {
            for (const n of boardData.nodes) {
                if (n.linkedSource) {
                    const bBounds = getLinkedBadgeBounds(n);
                    if (bBounds && pt.x >= bBounds.x && pt.x <= bBounds.x + bBounds.w && pt.y >= bBounds.y && pt.y <= bBounds.y + bBounds.h) {
                        found = { type: "linked_badge", node: n, bounds: bBounds };
                        break;
                    }
                }
            }
            if (!found) {
                for (const n of boardData.nodes) {
                    if (n.isFoldedInFrame) continue;
                    const dims = resolveNodeDimensions(n);
                    if (pt.x >= n.posX && pt.x <= n.posX + dims.w && pt.y >= n.posY && pt.y <= n.posY + dims.h) {
                        found = { type: "node", node: n };
                        break;
                    }
                }
            }
            if (!found && boardData.frames) {
                for (const f of boardData.frames) {
                    if (f.isSharedMachine && pt.x >= f.posX && pt.x <= f.posX + f.width && pt.y >= f.posY && pt.y <= f.posY + f.height) {
                        if (f.viewMode === "EMBEDDED_PANEL" && pt.y > f.posY + 44) continue;
                        found = { type: "frame", frame: f };
                        break;
                    }
                }
            }
        }

        if (found !== hoveredObject) {
            hoveredObject = found;
            requestRender();
            updateTooltip(sx, sy);
        } else if (found) {
            positionTooltip(sx, sy);
        } else {
            hideTooltip();
        }

        const isClickableBadge = found && found.type === "linked_badge" && found.node.linkedSource && found.node.linkedSource.pageId && !found.node.linkedSource.isBroken;
        canvas.style.cursor = isClickableBadge ? "pointer" : "default";
    }

    function updateTooltip(sx, sy) {
        if (!hoveredObject) {
            hideTooltip();
            return;
        }

        if (hoveredObject.type === "frame") {
            const f = hoveredObject.frame;
            const duty = f.totalDuty || 0;
            const cap = f.targetCapacity || 1.0;
            const isDeficit = duty > cap + 0.001;
            const dutyColor = isDeficit ? "var(--accent-red)" : "var(--accent-emerald)";
            const tierStr = f.sharedTier ? ` [${f.sharedTier}]` : "";

            let html = `
                <div class="tooltip-header">
                    <span class="tooltip-title">${escapeHtml((f.sharedMachineName || f.title || "Shared Machine") + tierStr)}</span>
                    <span class="tooltip-tier" style="color:var(--accent-sky); border-color:var(--accent-sky)">SHARED</span>
                </div>
                <div class="tooltip-section">
                    <div class="tooltip-row"><span class="tooltip-label">Target Capacity:</span><span class="tooltip-val" style="color:#ffffaa;">${cap.toFixed(2)}x</span></div>
                    <div class="tooltip-row"><span class="tooltip-label">Total Duty:</span><span class="tooltip-val" style="color:${dutyColor};">${(duty * 100).toFixed(1)}% (${f.requiredMachines || 1}x)</span></div>
                    <div class="tooltip-row"><span class="tooltip-label">Power Consumption:</span><span class="tooltip-val" style="color:var(--accent-amber);">${(f.totalEUt || 0).toFixed(1)} EU/t</span></div>
                    <div class="tooltip-row"><span class="tooltip-label">Sub-recipes:</span><span class="tooltip-val">${f.containedNodeIds ? f.containedNodeIds.length : 0} nodes</span></div>
                    <div class="tooltip-row"><span class="tooltip-label">View Mode:</span><span class="tooltip-val">${f.viewMode || "NORMAL"}</span></div>
                </div>
            `;
            tooltip.innerHTML = html;
            tooltip.classList.remove("hidden");
            positionTooltip(sx, sy);
            return;
        }

        if (hoveredObject.type === "linked_badge") {
            const n = hoveredObject.node;
            const ls = n.linkedSource;
            const bound = (n.outputs && n.outputs[0]) || (n.inputs && n.inputs[0]);
            const boundType = bound?.type;

            let statusColor = "#38bdf8";
            let statusTitle = "Linked Junction Source";
            if (ls.isBroken) {
                statusColor = "#ef4444";
                statusTitle = "Broken Virtual Link";
            } else if (ls.isCircular) {
                statusColor = "#a855f7";
                statusTitle = "Circular Virtual Link Loop";
            } else if (ls.isStarved) {
                statusColor = "#f97316";
                statusTitle = "Supply Starvation";
            }

            let html = `
                <div class="tooltip-header">
                    <span class="tooltip-title">${statusTitle}</span>
                    <span class="tooltip-tier" style="color:${statusColor}; border-color:${statusColor}">LINK</span>
                </div>
                <div class="tooltip-section">
                    <div class="tooltip-row"><span class="tooltip-label">Source Page:</span><span class="tooltip-val">${escapeHtml(ls.pageName || ls.pageId || "Unknown")}</span></div>
                    <div class="tooltip-row"><span class="tooltip-label">Source Junction:</span><span class="tooltip-val">${escapeHtml(ls.nodeName || ls.nodeId || "Unknown")}</span></div>
            `;

            if (ls.priority && ls.priority > 0) {
                html += `    <div class="tooltip-row"><span class="tooltip-label">Priority:</span><span class="tooltip-val" style="color:var(--accent-amber);">P${ls.priority}</span></div>\n`;
            }

            if (ls.metrics) {
                const totalProd = formatRate(ls.metrics.totalProduction || 0, boundType);
                const totalUsage = formatRate(ls.metrics.totalUsage || 0, boundType);
                const available = formatRate(ls.metrics.availableSurplus || 0, boundType);
                const availColor = (ls.metrics.availableSurplus || 0) <= 0 ? "var(--accent-red)" : "var(--accent-sky)";
                html += `
                    <div class="tooltip-row"><span class="tooltip-label">Source Production:</span><span class="tooltip-val" style="color:var(--accent-emerald);">+${totalProd}</span></div>
                    <div class="tooltip-row"><span class="tooltip-label">Source Usage:</span><span class="tooltip-val" style="color:var(--accent-amber);">${(ls.metrics.totalUsage || 0) > 0 ? "-" : ""}${totalUsage}</span></div>
                    <div class="tooltip-row"><span class="tooltip-label">Available Surplus:</span><span class="tooltip-val" style="color:${availColor};">${available}</span></div>
                `;
            }

            if (ls.demandRate !== undefined) {
                const demandStr = formatRate(ls.demandRate || 0, boundType);
                const allocStr = formatRate(ls.allocatedInputRate || 0, boundType);
                html += `
                    <div class="tooltip-row"><span class="tooltip-label">Allocated / Demand:</span><span class="tooltip-val">${allocStr} / ${demandStr}</span></div>
                `;
            }

            if (!ls.isBroken && ls.pageId) {
                html += `
                    <div class="tooltip-row" style="margin-top:6px; font-size:10px; color:var(--text-muted); font-style:italic;">
                        Click badge to jump to source page
                    </div>
                `;
            }

            html += `</div>`;
            tooltip.innerHTML = html;
            tooltip.classList.remove("hidden");
            positionTooltip(sx, sy);
            return;
        }

        if (hoveredObject.type !== "node") {
            hideTooltip();
            return;
        }

        const n = hoveredObject.node;
        if (n.type === "JUNCTION" || n.type === "PIN") {
            const bound = (n.outputs && n.outputs[0]) || (n.inputs && n.inputs[0]);
            let html = `
                <div class="tooltip-header">
                    <span class="tooltip-title">${escapeHtml(n.title || (n.type === "PIN" ? "Boundary Pin" : "Reroute Junction"))}</span>
                    <span class="tooltip-tier" style="color:#38bdf8; border-color:#38bdf8">${n.type}</span>
                </div>
            `;
            if (bound) {
                const rateText = formatRate(bound.ratePerSec, bound.type);
                html += `
                    <div class="tooltip-section">
                        <div class="tooltip-row"><span class="tooltip-label">Ingredient:</span><span class="tooltip-val">${escapeHtml(bound.displayName || bound.id)}</span></div>
                        <div class="tooltip-row"><span class="tooltip-label">Rate:</span><span class="tooltip-val">${rateText}</span></div>
                    </div>
                `;
            }
            if (n.linkedSource) {
                const ls = n.linkedSource;
                const boundType = bound?.type;
                html += `
                    <div class="tooltip-section" style="border-top:1px solid var(--border-color); margin-top:6px; padding-top:6px;">
                        <div class="tooltip-row"><strong style="color:#38bdf8; font-size:11px;">Linked Supply Source:</strong></div>
                        <div class="tooltip-row"><span class="tooltip-label">Page:</span><span class="tooltip-val">${escapeHtml(ls.pageName || ls.pageId || "Unknown")}</span></div>
                `;
                if (ls.metrics) {
                    const totalProd = formatRate(ls.metrics.totalProduction || 0, boundType);
                    const totalUsage = formatRate(ls.metrics.totalUsage || 0, boundType);
                    const available = formatRate(ls.metrics.availableSurplus || 0, boundType);
                    const availColor = (ls.metrics.availableSurplus || 0) <= 0 ? "var(--accent-red)" : "var(--accent-sky)";
                    html += `
                        <div class="tooltip-row"><span class="tooltip-label">Total Prod:</span><span class="tooltip-val" style="color:var(--accent-emerald);">+${totalProd}</span></div>
                        <div class="tooltip-row"><span class="tooltip-label">Total Usage:</span><span class="tooltip-val" style="color:var(--accent-amber);">${(ls.metrics.totalUsage || 0) > 0 ? "-" : ""}${totalUsage}</span></div>
                        <div class="tooltip-row"><span class="tooltip-label">Available:</span><span class="tooltip-val" style="color:${availColor};">${available}</span></div>
                    `;
                }
                html += `</div>`;
            }
            if (n.exportTargets && n.exportTargets.length > 0) {
                html += `
                    <div class="tooltip-section" style="border-top:1px solid var(--border-color); margin-top:6px; padding-top:6px;">
                        <div class="tooltip-row"><strong style="color:#a855f7; font-size:11px;">Export Targets (${n.exportTargets.length}):</strong></div>
                `;
                for (const target of n.exportTargets) {
                    html += `
                        <div class="tooltip-row" style="font-size:10px;">
                            <span class="tooltip-label">${escapeHtml(target.pageName || target.pageId)}:</span>
                            <span class="tooltip-val">Priority ${target.priority}</span>
                        </div>
                    `;
                }
                html += `</div>`;
            }
            tooltip.innerHTML = html;
            tooltip.classList.remove("hidden");
            positionTooltip(sx, sy);
            return;
        }

        const tierColor = getTierColor(n.tier || "LV");

        let html = `
            <div class="tooltip-header">
                <span class="tooltip-title">${escapeHtml(n.title || "Machine")}</span>
                <span class="tooltip-tier" style="color:${tierColor}; border-color:${tierColor}">${escapeHtml(n.tier || "LV")}</span>
            </div>
            <div class="tooltip-section">
                <div class="tooltip-row"><span class="tooltip-label">Machine Count:</span><span class="tooltip-val">${n.metrics?.machineCount || 1}</span></div>
                <div class="tooltip-row"><span class="tooltip-label">Total Power:</span><span class="tooltip-val">${formatPower(n.metrics?.eut || 0)}</span></div>
                <div class="tooltip-row"><span class="tooltip-label">Duration:</span><span class="tooltip-val">${n.metrics?.durationSec || 0}s</span></div>
                <div class="tooltip-row"><span class="tooltip-label">Parallel:</span><span class="tooltip-val">${n.metrics?.parallel || 1}x</span></div>
                <div class="tooltip-row"><span class="tooltip-label">Overclock Mode:</span><span class="tooltip-val">${escapeHtml(n.metrics?.overclockMode || "STANDARD")}</span></div>
            </div>
        `;

        if (n.inputs && n.inputs.length > 0) {
            html += `<div class="tooltip-io-list"><strong style="color:var(--accent-amber); font-size:11px;">Inputs:</strong>`;
            for (const inPort of n.inputs) {
                const rateText = formatRate(inPort.ratePerSec, inPort.type);
                const amtText = inPort.type === "FLUID" ? formatFluidRate(inPort.amount).replace(/\/s$/, "") : inPort.amount;
                html += `
                    <div class="tooltip-io-item">
                        <span>${escapeHtml(inPort.displayName || inPort.id)}</span>
                        <span class="tooltip-val">${amtText} (${rateText})</span>
                    </div>
                `;
            }
            html += `</div>`;
        }

        if (n.outputs && n.outputs.length > 0) {
            html += `<div class="tooltip-io-list"><strong style="color:var(--accent-emerald); font-size:11px;">Outputs:</strong>`;
            for (const outPort of n.outputs) {
                const rateText = formatRate(outPort.ratePerSec, outPort.type);
                const amtText = outPort.type === "FLUID" ? formatFluidRate(outPort.amount).replace(/\/s$/, "") : outPort.amount;
                html += `
                    <div class="tooltip-io-item">
                        <span>${escapeHtml(outPort.displayName || outPort.id)}</span>
                        <span class="tooltip-val">${amtText} (${rateText})</span>
                    </div>
                `;
            }
            html += `</div>`;
        }

        tooltip.innerHTML = html;
        tooltip.classList.remove("hidden");
        positionTooltip(sx, sy);
    }

    function positionTooltip(sx, sy) {
        const offset = 14;
        let tx = sx + offset;
        let ty = sy + offset;

        const tw = tooltip.offsetWidth;
        const th = tooltip.offsetHeight;
        const ww = window.innerWidth;
        const wh = window.innerHeight;

        if (tx + tw > ww - 10) tx = sx - tw - offset;
        if (ty + th > wh - 10) ty = sy - th - offset;

        tooltip.style.left = Math.max(10, tx) + "px";
        tooltip.style.top = Math.max(10, ty) + "px";
    }

    function hideTooltip() {
        tooltip.classList.add("hidden");
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    function updateWorkspaceTabsUI() {
        if (!tabPersonal || !tabTeam) return;
        tabPersonal.classList.toggle("active", currentWorkspace === "LOCAL");
        tabTeam.classList.toggle("active", currentWorkspace === "TEAM");
        if (hasTeam) {
            tabTeam.style.display = "inline-flex";
        } else {
            tabTeam.style.display = "none";
        }
    }

    function switchWorkspace(target) {
        if (currentWorkspace === target) return;
        currentWorkspace = target;
        updateWorkspaceTabsUI();
        currentPageId = "";
        fetchPagesList().then(() => {
            return fetchBoardData("");
        }).then(() => {
            fitView();
        });
    }

    if (tabPersonal) {
        tabPersonal.addEventListener("click", () => {
            switchWorkspace("LOCAL");
        });
    }

    if (tabTeam) {
        tabTeam.addEventListener("click", () => {
            switchWorkspace("TEAM");
        });
    }

    async function fetchBoardData(pageId) {
        try {
            const targetId = pageId || (liveFollow ? "" : currentPageId);
            const params = new URLSearchParams();
            if (targetId) params.set("pageId", targetId);
            if (currentWorkspace) params.set("workspace", currentWorkspace.toLowerCase());
            const query = params.toString() ? ("?" + params.toString()) : "";
            const resp = await fetch("/api/board" + query, { cache: "no-store" });
            if (!resp.ok) throw new Error("HTTP " + resp.status);
            boardData = await resp.json();
            if (boardData.pageId) currentPageId = boardData.pageId;
            if (boardData.workspace) {
                currentWorkspace = boardData.workspace;
                updateWorkspaceTabsUI();
            }
            if (boardData.hasTeam !== undefined) {
                hasTeam = boardData.hasTeam;
                if (boardData.teamName && teamTabLabel) {
                    teamTabLabel.textContent = "Team: " + boardData.teamName;
                }
                updateWorkspaceTabsUI();
            }
            updateBoardUI();
            requestRender();
        } catch (err) {
            console.warn("Failed to fetch board data:", err);
        }
    }

    function updateBoardUI() {
        let titleText = boardData.pageTitle || "Untitled Page";
        if (currentWorkspace === "TEAM") {
            titleText = "[Team] " + titleText;
        }
        if (pageTitleText) {
            pageTitleText.textContent = titleText;
        } else if (pageTitleBadge) {
            pageTitleBadge.textContent = titleText;
        }

        const nodes = boardData.nodes || [];
        const conns = boardData.connections || [];
        nodeCountElem.textContent = nodes.length + " Nodes";
        wireCountElem.textContent = conns.length + " Wires";

        let totalPower = 0;
        for (const n of nodes) {
            if (n.metrics && typeof n.metrics.eut === "number") {
                totalPower += n.metrics.eut;
            }
        }
        powerBalanceElem.textContent = formatPower(totalPower);
        lastUpdatedElem.textContent = "Last sync: " + new Date().toLocaleTimeString();
    }

    async function fetchPagesList() {
        try {
            const wsParam = currentWorkspace ? ("?workspace=" + currentWorkspace.toLowerCase()) : "";
            const resp = await fetch("/api/pages" + wsParam, { cache: "no-store" });
            if (!resp.ok) return;
            const data = await resp.json();
            hasTeam = !!data.hasTeam;
            if (data.teamName && teamTabLabel) {
                teamTabLabel.textContent = "Team: " + data.teamName;
            }
            localPages = data.localPages || [];
            teamPages = data.teamPages || [];
            availablePages = (currentWorkspace === "TEAM") ? teamPages : (data.pages || localPages);
            updateWorkspaceTabsUI();
            renderPageList(pageSearchInput ? pageSearchInput.value : "");
        } catch (e) {
            console.warn("Failed to fetch pages:", e);
        }
    }

    function renderPageList(filterText) {
        if (!pageListContainer) return;
        pageListContainer.innerHTML = "";

        const query = (filterText || "").trim().toLowerCase();
        const filtered = availablePages.filter(p => !query || (p.title && p.title.toLowerCase().includes(query)) || (p.folder && p.folder.toLowerCase().includes(query)));

        if (filtered.length === 0) {
            const empty = document.createElement("div");
            empty.style.cssText = "padding:12px; text-align:center; color:var(--text-muted); font-size:11px;";
            empty.textContent = "No pages found";
            pageListContainer.appendChild(empty);
            return;
        }

        const folderMap = new Map();
        for (const p of filtered) {
            const folder = p.folder || "";
            if (!folderMap.has(folder)) {
                folderMap.set(folder, []);
            }
            folderMap.get(folder).push(p);
        }

        for (const [folder, pages] of folderMap) {
            if (folder) {
                const groupTitle = document.createElement("div");
                groupTitle.className = "page-list-group-title";
                groupTitle.textContent = "📁 " + folder;
                pageListContainer.appendChild(groupTitle);
            }

            for (const p of pages) {
                const item = document.createElement("div");
                item.className = "page-item" + (p.id === currentPageId ? " active" : "");

                const info = document.createElement("div");
                info.className = "page-item-info";

                const icon = document.createElement("span");
                icon.className = "page-item-icon";
                icon.textContent = p.isModule ? "🧩" : (p.isPinned ? "📌" : "📄");

                const title = document.createElement("span");
                title.className = "page-item-title";
                title.textContent = p.title || "Untitled";

                info.appendChild(icon);
                info.appendChild(title);

                const badges = document.createElement("div");
                badges.className = "page-item-badges";

                if (p.isActive) {
                    const activeTag = document.createElement("span");
                    activeTag.className = "page-item-active-tag";
                    activeTag.textContent = "● IN-GAME";
                    badges.appendChild(activeTag);
                }

                const countBadge = document.createElement("span");
                countBadge.className = "page-item-node-count";
                countBadge.textContent = (p.nodeCount || 0) + " nodes";
                badges.appendChild(countBadge);

                item.appendChild(info);
                item.appendChild(badges);

                item.addEventListener("click", () => {
                    selectPage(p.id, true);
                });

                pageListContainer.appendChild(item);
            }
        }
    }

    function selectPage(pageId, isManual) {
        currentPageId = pageId;
        if (isManual) {
            setLiveFollow(false);
        }
        fetchBoardData(pageId).then(() => {
            fitView();
        });
        closePageBrowser();
    }

    function setLiveFollow(enabled) {
        liveFollow = enabled;
        if (chkLiveFollow) {
            chkLiveFollow.checked = enabled;
        }
    }

    function togglePageBrowser() {
        if (!pageBrowserMenu) return;
        const isOpen = !pageBrowserMenu.classList.contains("hidden");
        if (isOpen) {
            closePageBrowser();
        } else {
            openPageBrowser();
        }
    }

    function openPageBrowser() {
        if (!pageBrowserMenu) return;
        pageBrowserMenu.classList.remove("hidden");
        if (pageSelectorWrapper) {
            pageSelectorWrapper.classList.add("open");
        }
        fetchPagesList();
        if (pageSearchInput) {
            pageSearchInput.value = "";
            pageSearchInput.focus();
        }
    }

    function closePageBrowser() {
        if (pageBrowserMenu) {
            pageBrowserMenu.classList.add("hidden");
        }
        if (pageSelectorWrapper) {
            pageSelectorWrapper.classList.remove("open");
        }
    }

    if (pageTitleBadge) {
        pageTitleBadge.addEventListener("click", (e) => {
            e.stopPropagation();
            togglePageBrowser();
        });
    }

    if (pageBrowserMenu) {
        pageBrowserMenu.addEventListener("click", (e) => {
            e.stopPropagation();
        });
    }

    if (pageSearchInput) {
        pageSearchInput.addEventListener("input", (e) => {
            renderPageList(e.target.value);
        });
    }

    if (chkLiveFollow) {
        chkLiveFollow.addEventListener("change", (e) => {
            setLiveFollow(e.target.checked);
            if (liveFollow) {
                fetchBoardData("").then(() => fitView());
            }
        });
    }

    document.addEventListener("click", (e) => {
        if (pageSelectorWrapper && !pageSelectorWrapper.contains(e.target)) {
            closePageBrowser();
        }
    });

    function initSSE() {
        try {
            const sse = new EventSource("/api/events");

            sse.onopen = () => {
                connectionBadge.className = "badge badge-live";
                connectionText.textContent = "Live";
            };

            sse.addEventListener("connected", () => {
                connectionBadge.className = "badge badge-live";
                connectionText.textContent = "Live";
            });

            sse.addEventListener("board_updated", (evt) => {
                try {
                    const data = evt.data ? JSON.parse(evt.data) : null;
                    const updatedId = data ? data.pageId : null;
                    const eventWs = data ? data.workspace : null;
                    if (eventWs && liveFollow && currentWorkspace !== eventWs) {
                        currentWorkspace = eventWs;
                        updateWorkspaceTabsUI();
                    }
                    if (liveFollow || (updatedId && updatedId === currentPageId)) {
                        fetchBoardData(liveFollow ? "" : currentPageId);
                    }
                } catch (e) {
                    fetchBoardData();
                }
                fetchPagesList();
            });

            sse.onerror = () => {
                connectionBadge.className = "badge badge-connecting";
                connectionText.textContent = "Reconnecting...";
            };
        } catch (e) {
            console.warn("SSE unsupported or failed, polling fallback", e);
            setInterval(() => {
                fetchBoardData();
                fetchPagesList();
            }, 5000);
        }
    }

    // Initialize
    resizeCanvas();
    updateWorkspaceTabsUI();
    fetchPagesList();
    fetchBoardData().then(() => {
        fitView();
    });
    initSSE();
})();

