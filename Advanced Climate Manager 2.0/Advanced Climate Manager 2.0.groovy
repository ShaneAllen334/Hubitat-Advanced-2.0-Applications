/**
 * Advanced Climate Manager 2.4
 *
 * v2.4 Changelog:
 * - Redesigned UI with modern KPI summary cards, status badges, and guided "How It Works / Recommended" callout boxes for every feature
 * - Added 500ms Event-Storm Debouncer (requestEvaluation) to batch simultaneous sensor events and prevent Z-Wave/Zigbee mesh collisions
 * - Added 3-second Z-Wave/Zigbee Setpoint Command Staggering so dual Cool/Heat setpoint updates never drop the second wireless packet
 * - Added optional Active Thermostat Polling (Every 5 or 10 mins) for thermostats that don't reliably push operating state changes
 * - Includes all v2.3 fixes (Earliest-Expiry Occupancy Timer, String ID Serialization, Delta-T Emergency Lockout, Half-Degree Aux Glide)
 */ 
definition(
    name: "Advanced Climate Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced BMS HVAC Controller with Honeywell Room Sensor Support, Guided UI & Mesh Debouncing",
    category: "Comfort",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

// ==============================================================================
// UI HELPER METHODS
// ==============================================================================

String uiHelpBox(String title, String body, String tip = null) {
    def tipHtml = tip ? "<div style='margin-top:6px; padding-top:6px; border-top:1px dashed #cbd5e1; color:#0f5132;'><b>💡 Recommended:</b> ${tip}</div>" : ""
    return """
    <div style='background-color:#f8fafc; border:1px solid #e2e8f0; border-left:4px solid #0284c7; padding:10px 12px; border-radius:6px; font-size:13px; color:#334155; margin-bottom:10px; line-height:1.45;'>
        <div style='font-weight:bold; color:#0f172a; margin-bottom:3px;'>ℹ️ ${title}</div>
        <div>${body}</div>
        ${tipHtml}
    </div>
    """
}

String uiBadge(String text, String bgColor, String textColor = "#ffffff") {
    return "<span style='background-color:${bgColor}; color:${textColor}; padding:3px 8px; border-radius:12px; font-size:12px; font-weight:bold; display:inline-block;'>${text}</span>"
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        // 1. LIVE BMS DASHBOARD
        section("") {
            def statusExplanation = getHumanReadableStatus()
            
            paragraph """
            <div style='background: linear-gradient(135deg, #1e293b 0%, #334155 100%); color:white; padding:14px 16px; border-radius:8px; margin-bottom:10px; box-shadow: 0 2px 5px rgba(0,0,0,0.15);'>
                <div style='font-size:18px; font-weight:bold; letter-spacing:0.3px;'>🌡️ Advanced Climate Manager <span style='font-size:12px; background:#0284c7; padding:2px 7px; border-radius:10px; vertical-align:middle;'>v2.4 BMS</span></div>
                <div style='margin-top:8px; font-size:13px; background:rgba(255,255,255,0.95); color:#1e293b; padding:10px 12px; border-radius:6px; border-left:5px solid #0284c7;'>
                    <b>Live System Status:</b> ${statusExplanation}
                </div>
            </div>
            """
            
            input "btnRefresh", "button", title: "🔄 Refresh Dashboard", width: 6
            input "btnEnforceModes", "button", title: "🎯 Clear Locks & Enforce Setpoints", width: 6

            if (thermostat) {
                // Gather Core Metrics
                def tstatTemp = thermostat.currentValue("temperature") ?: "--"
                def tstatHum = thermostat.currentValue("humidity") ?: "--"
                def tstatCool = thermostat.currentValue("coolingSetpoint") ?: "--"
                def tstatHeat = thermostat.currentValue("heatingSetpoint") ?: "--"
                def tstatMode = thermostat.currentValue("thermostatMode")?.toUpperCase() ?: "UNKNOWN"
                def tstatState = thermostat.currentValue("thermostatOperatingState")?.toUpperCase() ?: "IDLE"
                def tstatFan = thermostat.currentValue("thermostatFanMode")?.toUpperCase() ?: "UNKNOWN"
            
                def stateBadge = uiBadge(tstatState, "#64748b")
                if (tstatState == "COOLING") stateBadge = uiBadge("❄️ COOLING", "#0284c7")
                else if (tstatState == "HEATING") stateBadge = uiBadge("🔥 HEATING", "#ea580c")
                else if (tstatState.contains("AUX") || tstatState.contains("EMERGENCY")) stateBadge = uiBadge("⚡ AUX / EMERG HEAT", "#dc2626")
                else if (tstatState == "IDLE") stateBadge = uiBadge("⏸️ IDLE", "#16a34a")
          
                def avgTemp = getAverageTemp()
                def avgHum = getAverageHumidity()
                def currentLocMode = location.mode ?: "Unknown"

                // --- Timer Calculations for Dashboard ---
                def yoyoRemainingMinsRaw = (state.yoyoCooldownEnds && now() < state.yoyoCooldownEnds.toLong() && state.currentAction == "idle") ? (state.yoyoCooldownEnds.toLong() - now()) / 60000.0 : 0.0
                def yoyoMinsDash = yoyoCooldownMins != null ? yoyoCooldownMins.toInteger() : 15
                
                def isSmartModeAllowed = !averagingModes || (averagingModes as List).contains(location.mode)
                def globalHysActive = enableHysteresis && globalHysteresis
                
                // 1. Alignment & Hysteresis Row
                def alignmentStr = ""
                if (!enableAverageSync && !globalHysActive) {
                    alignmentStr = uiBadge("Disabled", "#94a3b8")
                } else if (!isSmartModeAllowed && !globalHysActive) {
                    alignmentStr = uiBadge("Paused by Mode (${currentLocMode})", "#f59e0b")
                } else if (state.alignmentLockout) {
                    alignmentStr = "${uiBadge("Lockout Active", "#dc2626")} <span style='font-size:12px;'>Waiting for wall temp to reach ${state.alignmentLockoutTarget}°</span>"
                } else if (enableHysteresis && state.activeHysteresis == "idle") {
                    alignmentStr = "${uiBadge("Floating in Deadband", "#0284c7")} <span style='font-size:12px; color:#555;'>Saving energy while within drift range</span>"
                } else if (enableHysteresis && state.activeHysteresis != "idle" && state.activeHysteresis != null) {
                    alignmentStr = "${uiBadge("Active Recovery (${state.activeHysteresis.capitalize()})", "#16a34a")} <span style='font-size:12px; color:#555;'>Pulling home back to target</span>"
                } else if (!isSmartModeAllowed) { 
                    alignmentStr = uiBadge("Ready (Global Hysteresis)", "#16a34a")
                } else {
                    alignmentStr = uiBadge("Ready & Syncing", "#16a34a")
                }

                // 2. Anti-Yo-Yo / Smart Debounce Row
                def debounceStr = uiBadge("Ready (No Cooldown)", "#64748b")
                if (yoyoMinsDash == 0) {
                    debounceStr = uiBadge("Disabled", "#94a3b8")
                } else if (yoyoRemainingMinsRaw > 0) {
                    def lockMode = state.lastCycleMode == "heating" || state.lastCycleMode == "auxHeating" ? "Heating locked out" : "Cooling locked out"
                    debounceStr = "${uiBadge("Active (${Math.round(yoyoRemainingMinsRaw)}m left)", "#f59e0b")} <span style='font-size:12px; color:#555;'>${lockMode} (Evading ambient temp)</span>"
                }

                // 3. Compressor Protection & Buffer Row
                def bufferStr = uiBadge("Standby (Idle / Satisfied)", "#64748b")
                if (state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
                    def runMinsDash = (now() - state.cycleStartTime) / 60000.0
                    def minRunDash = getMinRunTime()
                    
                    if (runMinsDash < minRunDash) {
                        def remaining = Math.max(0L, Math.round(minRunDash - runMinsDash))
                        if (state.isBuffering) {
                            bufferStr = "${uiBadge("Evasion Buffer (${remaining}m left)", "#dc2626")} <span style='font-size:12px; color:#555;'>Temp dropping fast; holding unit ON</span>"
                        } else {
                            bufferStr = "${uiBadge("Min-Run Active (${remaining}m left)", "#0284c7")} <span style='font-size:12px; color:#555;'>Enforcing minimum compressor cycle</span>"
                        }
                    } else {
                        bufferStr = uiBadge("Min-Run Satisfied (Ready to stop)", "#16a34a")
                    }
                }
                
                // 4. Live Idle Row
                def currentIdleStr = "<span style='color:#64748b;'>Compressor Currently Running</span>"
                if (state.currentAction == "idle" || tstatState == "IDLE") {
                    if (!state.idleStartTime) state.idleStartTime = now() 
                    def idleMinsRaw = (now() - state.idleStartTime) / 60000.0
                    def totalMins = idleMinsRaw.toInteger()
                    def h = (totalMins / 60).toInteger()
                    def m = totalMins % 60
                    def tStr = h > 0 ? "${h}h ${m}m" : "${m}m"
                    currentIdleStr = "<span style='color:#0284c7; font-weight:bold;'>${tStr}</span>"
                }
                
                def coastingStr = uiBadge("Idle", "#64748b")
                if (state.coastingEnds && now() < state.coastingEnds) {
                    def cRem = Math.max(0L, Math.round((state.coastingEnds - now()) / 60000))
                    coastingStr = "${uiBadge("Scavenging (${cRem}m left)", "#0284c7")} <span style='font-size:12px; color:#555;'>Blower harvesting leftover coil energy</span>"
                } else if (!enableThermalCoasting) {
                    coastingStr = uiBadge("Disabled", "#94a3b8")
                }
   
                def swapText = "Disabled (Manual Mode Selection)"
                if (enableAutoSwap) {
                    def safeSwapDB = autoSwapDeadband != null ? autoSwapDeadband.toBigDecimal() : 1.0
                    def isHysAllowedDash = enableHysteresis && (isSmartModeAllowed || globalHysteresis)
                    if (isHysAllowedDash) {
                        def drift = hysteresisDrift != null ? hysteresisDrift.toBigDecimal() : 1.0
                        if (safeSwapDB <= drift) safeSwapDB = drift + 0.5
                    }
   
                    def distToCool = (tstatCool != "--" && avgTemp > 0) ? Math.round(( (tstatCool.toBigDecimal() + safeSwapDB) - avgTemp.toBigDecimal() ) * 10) / 10.0 : 0
                    def distToHeat = (tstatHeat != "--" && avgTemp > 0) ? Math.round(( avgTemp.toBigDecimal() - (tstatHeat.toBigDecimal() - safeSwapDB) ) * 10) / 10.0 : 0
                    
                    def exactHeatSwap = tstatHeat != "--" ? (tstatHeat.toBigDecimal() - safeSwapDB).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : "--"
                    def exactCoolSwap = tstatCool != "--" ? (tstatCool.toBigDecimal() + safeSwapDB).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : "--"
                    
                    if (tstatMode == "HEAT") swapText = "<span style='color:#0284c7;'><b>↑ ${distToCool}°</b> until Auto-Swap to COOL (Triggers at ${exactCoolSwap}°)</span>"
                    else if (tstatMode == "COOL") swapText = "<span style='color:#dc2626;'><b>↓ ${distToHeat}°</b> until Auto-Swap to HEAT (Triggers at ${exactHeatSwap}°)</span>"
                    else swapText = "Thermostat not in Heat/Cool mode"
                }

                def deltaTStr = "Disabled or Sensors Not Selected"
                if (enableDeltaT && returnSensor && dischargeSensor) {
                    def retT = returnSensor.currentValue("temperature")
                    def disT = dischargeSensor.currentValue("temperature")
                    if (retT != null && disT != null) {
                        def dT = 0.0
                        if (tstatState == "COOLING") {
                            dT = Math.max(0.0d, (retT - disT).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                        } else if (tstatState == "HEATING") {
                            dT = Math.max(0.0d, (disT - retT).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                        } else {
                            dT = 0.0
                        }
            
                        def minCoolDT = minCoolingDeltaT != null ? minCoolingDeltaT.toBigDecimal() : 12.0
                        def minHeatDT = minHeatingDeltaT != null ? minHeatingDeltaT.toBigDecimal() : 15.0
                        def health = ""
                        if (state.deltaTShutoff) health = " " + uiBadge("EMERGENCY SHUTOFF", "#dc2626")
                        else if (tstatState == "COOLING" && dT < minCoolDT) health = " " + uiBadge("Low Coil Split", "#dc2626")
                        else if (tstatState == "HEATING" && dT < minHeatDT) health = " " + uiBadge("Low Heat Split", "#dc2626")
                        else if (tstatState in ["COOLING", "HEATING"]) health = " " + uiBadge("Healthy Split", "#16a34a")
                        else health = " <span style='color:gray;'>(System Idle)</span>"
                         
                        deltaTStr = "<b>${dT}°F</b> (Return: ${retT}° | Supply: ${disT}°)${health}"
                    } else {
                        deltaTStr = "Waiting for duct sensor reports..."
                    }
                }

                // Calculated Deadband Metric
                def minReqDB = (hardwareDeadbandMin != null ? hardwareDeadbandMin : 3.0).toBigDecimal()
                def currentDeadbandStr = "N/A"
                if (tstatCool != "--" && tstatHeat != "--") {
                    def gap = (tstatCool.toBigDecimal() - tstatHeat.toBigDecimal()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                    if (gap < minReqDB) {
                        currentDeadbandStr = "${uiBadge("${gap}° Gap (Conflict)", "#dc2626")}"
                    } else if (gap == minReqDB) {
                        currentDeadbandStr = "${uiBadge("${gap}° Gap (Min Enforced)", "#f59e0b")}"
                    } else {
                        currentDeadbandStr = "${uiBadge("${gap}° Gap (Healthy)", "#16a34a")}"
                    }
                }

                // Gather Maintenance
                def filterLifeStr = "Disabled"
                if (enableFilterTracker) {
                    def maxMins = (maxFilterHours ?: 300) * 60.0
                    def usedMins = state.filterRunMinutes ?: 0.0
                    def percentLeft = Math.max(0.0d, (100.0 - ((usedMins / maxMins) * 100.0)).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                    def alertThreshold = filterNotifyThreshold != null ? filterNotifyThreshold.toInteger() : 10
                    if (percentLeft < alertThreshold) filterLifeStr = uiBadge("${percentLeft}% (REPLACE SOON)", "#dc2626")
                    else filterLifeStr = uiBadge("${percentLeft}% Remaining", "#16a34a")
                }
                
                def hvacContactStr = "Not Configured"
                if (hvacCompanyName || hvacCompanyPhone) {
                    hvacContactStr = "<b>${hvacCompanyName ?: 'N/A'}</b> ${hvacCompanyPhone ? '(' + hvacCompanyPhone + ')' : ''}"
                }

                // --- 7-Day Compressor Runs Calculation ---
                def sevenDayRuns = 0
                def sevenDayRuntime = 0.0
                def max7Day = 0.0
                def min7Day = 9999.0
                
                if (state.runHistory) {
                    state.runHistory.each { date, data ->
                        sevenDayRuns += (data.runs ?: 0)
                        sevenDayRuntime += (data.cool ?: 0.0) + (data.heat ?: 0.0) + (data.aux ?: 0.0)
                        if (data.maxRun != null && data.maxRun > max7Day) max7Day = data.maxRun
                        if (data.minRun != null && data.minRun < min7Day) min7Day = data.minRun
                    }
                }
                if (min7Day == 9999.0) min7Day = 0.0
                
                def totalRunHours = (sevenDayRuntime / 60.0).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                def avgCycleMins = sevenDayRuns > 0 ? (sevenDayRuntime / sevenDayRuns).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0
                
                def highestStr = max7Day > 0 ? "${max7Day.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)}m" : "--"
                def lowestStr = min7Day > 0 ? "${min7Day.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)}m" : "--"
                def avgStr = avgCycleMins > 0 ? "${avgCycleMins}m" : "--"
                
                def compressorRunsStr = "<b>${sevenDayRuns} Cycles</b> (${totalRunHours} Total Hrs) &nbsp;|&nbsp; <span style='font-size:12px; color:#475569;'>Avg: <b>${avgStr}</b> &bull; Longest: <b>${highestStr}</b> &bull; Shortest: <b>${lowestStr}</b></span>"
                
                // --- SCORING & ENVIRONMENT CALCULATIONS ---
                def outTempStr = "--"
                def inOutDeltaStr = "--"
                def ambientLockoutStr = uiBadge("Normal Operation", "#16a34a")
                def comfortScoreStr = "--"
                def efficiencyScoreStr = "--"
                def outT = null

                if (outdoorTempSensor) {
                    outT = outdoorTempSensor.currentValue("temperature")
                    if (outT != null) {
                        outTempStr = "${outT}°F"
                        if (avgTemp > 0) {
                            def inOutD = Math.abs((avgTemp.toBigDecimal() - outT.toBigDecimal()).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                            inOutDeltaStr = "${inOutD}°F"
                        }
                        
                        if (enableAmbientLockout) {
                            def minCoolOut = minOutdoorCoolTemp != null ? minOutdoorCoolTemp.toBigDecimal() : 55.0
                            def maxHeatOut = maxOutdoorHeatTemp != null ? maxOutdoorHeatTemp.toBigDecimal() : 68.0
                            if (outT < minCoolOut) {
                                ambientLockoutStr = "${uiBadge("AC Locked Out", "#0284c7")} <span style='font-size:12px;'>Outdoor ${outT}°F is below ${minCoolOut}°F safety floor</span>"
                            } else if (outT > maxHeatOut) {
                                ambientLockoutStr = "${uiBadge("Heat Locked Out", "#f59e0b")} <span style='font-size:12px;'>Outdoor ${outT}°F is above ${maxHeatOut}°F safety ceiling</span>"
                            } else {
                                ambientLockoutStr = "${uiBadge("Safe Operating Window", "#16a34a")} <span style='font-size:12px; color:#555;'>(${minCoolOut}°F – ${maxHeatOut}°F)</span>"
                            }
                        } else {
                            ambientLockoutStr = uiBadge("Disabled", "#94a3b8")
                        }
                    }
                } else {
                    ambientLockoutStr = uiBadge("No Outdoor Sensor", "#94a3b8")
                }

                // 1. Comfort & Health Score (Dynamic Targets)
                def activeTargetT = 72.0G 
                def isManualHold = state.manualHoldEnds && now() < state.manualHoldEnds

                if (isManualHold) {
                    if (tstatMode == "COOL" && tstatCool != "--") activeTargetT = tstatCool.toBigDecimal()
                    else if (tstatMode == "HEAT" && tstatHeat != "--") activeTargetT = tstatHeat.toBigDecimal()
                    else if (tstatCool != "--" && tstatHeat != "--" && avgTemp > 0) {
                        def cSet = tstatCool.toBigDecimal()
                        def hSet = tstatHeat.toBigDecimal()
                        activeTargetT = Math.abs((avgTemp.toBigDecimal() - cSet).doubleValue()) < Math.abs((avgTemp.toBigDecimal() - hSet).doubleValue()) ? cSet : hSet
                    }
                } else {
                    def evalMode = location.mode
                    def trueCool = getBaseCoolTarget(evalMode)
                    def trueHeat = getBaseHeatTarget(evalMode)
                    
                    if (tstatMode == "COOL") activeTargetT = trueCool
                    else if (tstatMode == "HEAT") activeTargetT = trueHeat
                    else activeTargetT = Math.abs((avgTemp.toBigDecimal() - trueCool).doubleValue()) < Math.abs((avgTemp.toBigDecimal() - trueHeat).doubleValue()) ? trueCool : trueHeat
                }

                // Hum Target: State-Optimized seasonal curve
                def baseHum = 45.0
                def humidStates = ["AL", "AR", "FL", "GA", "HI", "KY", "LA", "MD", "MS", "NC", "OK", "SC", "TN", "TX", "VA", "WV"]
                def dryStates = ["AZ", "CO", "ID", "MT", "NM", "NV", "UT", "WY"]
                
                if (userState in humidStates) baseHum = 55.0
                else if (userState in dryStates) baseHum = 35.0
                
                def targetHum = baseHum 
                if (outT != null) {
                    if (outT < 10.0) targetHum = 25.0
                    else if (outT < 30.0) targetHum = 30.0
                    else if (outT < 50.0) targetHum = Math.min(40.0, baseHum)
                    else if (outT < 60.0) targetHum = Math.min(50.0, baseHum)
                    else targetHum = baseHum
                }

                def comfortScore = 100.0
                if (avgTemp > 0 && activeTargetT > 0) {
                    def tempDiff = (avgTemp.toBigDecimal() - activeTargetT.toBigDecimal()).doubleValue()
                    def tempPenalty = 0.0
                    
                    if (tstatMode == "COOL" && tempDiff < 0) {
                        tempPenalty = Math.abs(tempDiff) * 1.5 
                    } else if (tstatMode == "HEAT" && tempDiff > 0) {
                        tempPenalty = Math.abs(tempDiff) * 1.5
                    } else {
                        tempPenalty = Math.abs(tempDiff) * 5.0
                    }
                    comfortScore -= tempPenalty
                }
                if (avgHum > 0) {
                    def humPenalty = Math.abs((avgHum.toBigDecimal() - targetHum).doubleValue()) * 1.0
                    comfortScore -= humPenalty
                }
                comfortScore = Math.max(0.0d, Math.min(100.0d, comfortScore.doubleValue())).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                def comfortColor = comfortScore >= 85 ? "#16a34a" : (comfortScore >= 70 ? "#f59e0b" : "#dc2626")
                comfortScoreStr = uiBadge("${comfortScore}%", comfortColor)
                
                def targetDesc = "Target: ${activeTargetT}°F"
                def evalModeForDesc = location.mode
                def isAwayDesc = awayModes ? (awayModes as List).contains(evalModeForDesc) : false
                if (isAwayDesc && enableDeepAway && state.awayStartTime && (now() - state.awayStartTime) >= ((deepAwayDelayHours ?: 4) * 3600000L)) {
                    targetDesc += " (Deep Setback)"
                }

                // 2. Efficiency Score (Based on Filter, HVAC Delta-T, & Cycle Times)
                def efficiencyScore = 100.0
                
                def maxMinsE = (maxFilterHours ?: 300) * 60.0
                def usedMinsE = state.filterRunMinutes ?: 0.0
                def filterLifePct = Math.max(0.0d, (100.0 - ((usedMinsE / maxMinsE) * 100.0)).doubleValue())
                if (filterLifePct < 20.0) efficiencyScore -= (20.0 - filterLifePct)
                
                if (enableDeltaT && returnSensor && dischargeSensor && (tstatState == "COOLING" || tstatState == "HEATING")) {
                    def targetDT = tstatState == "COOLING" ? (minCoolingDeltaT != null ? minCoolingDeltaT.toDouble() : 12.0d) : (minHeatingDeltaT != null ? minHeatingDeltaT.toDouble() : 15.0d)
                    def retT = returnSensor.currentValue("temperature")
                    def disT = dischargeSensor.currentValue("temperature")
                    if (retT != null && disT != null) {
                        def actualDT = tstatState == "COOLING" ? Math.max(0.0d, (retT - disT).doubleValue()) : Math.max(0.0d, (disT - retT).doubleValue())
                        if (actualDT < targetDT && targetDT > 0) {
                            def dtPenalty = ((targetDT - actualDT) / targetDT) * 50.0
                            efficiencyScore -= dtPenalty
                        }
                    }
                }
                
                if (avgCycleMins > 0 && avgCycleMins < 15.0) {
                    efficiencyScore -= (15.0 - avgCycleMins.toDouble()) * 2.0
                }

                efficiencyScore = Math.max(0.0d, Math.min(100.0d, efficiencyScore.doubleValue())).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                def effColor = efficiencyScore >= 85 ? "#16a34a" : (efficiencyScore >= 70 ? "#f59e0b" : "#dc2626")
                efficiencyScoreStr = uiBadge("${efficiencyScore}%", effColor)

                // --- PERIMETER SAFETY (WINDOWS/DOORS) DASHBOARD DISPLAY ---
                def perimeterStr = uiBadge("Secure (All Closed)", "#16a34a")
                if (!enableWindowDefeat) {
                    perimeterStr = uiBadge("Disabled", "#94a3b8")
                } else {
                    def openList = contactSensors?.findAll { it.currentValue("contact") == "open" }
                    def openSensors = openList?.collect { "<b>" + it.displayName + "</b>" }?.join(", ")
                    
                    if (state.windowRecoveryTime && now() < state.windowRecoveryTime) {
                        def remSecs = Math.max(0L, Math.round((state.windowRecoveryTime - now()) / 1000)).toInteger()
                        def remMins = (remSecs / 60).toInteger()
                        def remSecsDisplay = (remSecs % 60).toString().padLeft(2, '0')
                        perimeterStr = "${uiBadge("Recovery (${remMins}:${remSecsDisplay})", "#0284c7")} <span style='font-size:12px;'>All closed; restoring HVAC soon</span>"
                    } else if (state.windowOpenHold) {
                        perimeterStr = "${uiBadge("HVAC LOCKED OFF", "#dc2626")} <span style='font-size:12px;'>Open: ${openSensors ?: 'Unknown'}</span>"
                    } else if (state.windowDefeatTime && now() < state.windowDefeatTime) {
                        def remSecs = Math.max(0L, Math.round((state.windowDefeatTime - now()) / 1000)).toInteger()
                        def remMins = (remSecs / 60).toInteger()
                        def remSecsDisplay = (remSecs % 60).toString().padLeft(2, '0')
                        perimeterStr = "${uiBadge("Shutdown Timer (${remMins}:${remSecsDisplay})", "#f59e0b")} <span style='font-size:12px;'>Open: ${openSensors}</span>"
                    } else if (openList && openList.size() > 0) {
                        perimeterStr = "${uiBadge("Open Detected", "#f59e0b")} <span style='font-size:12px;'>Open: ${openSensors}</span>"
                    }
                }
                
                // --- SERVICE MODE DASHBOARD DISPLAY ---
                def serviceStr = uiBadge("Normal Automation", "#16a34a")
                if (serviceSwitch && serviceSwitch.currentValue("switch") == "on") {
                    serviceStr = uiBadge("SERVICE LOCKOUT (OFF)", "#dc2626")
                } else if (state.deltaTShutoff) {
                    serviceStr = uiBadge("DELTA-T EMERGENCY LOCKOUT", "#dc2626")
                }

                // Unified Dashboard HTML with KPI Summary Cards
                def dashHTML = """
                <style>
                    .kpi-grid { display: flex; gap: 10px; margin-top: 8px; margin-bottom: 12px; flex-wrap: wrap; }
                    .kpi-card { flex: 1; min-width: 150px; background: #ffffff; border: 1px solid #cbd5e1; border-radius: 8px; padding: 10px 12px; text-align: center; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
                    .kpi-title { font-size: 11px; text-transform: uppercase; color: #64748b; font-weight: bold; margin-bottom: 4px; }
                    .kpi-val { font-size: 20px; font-weight: bold; color: #0f172a; }
                    .kpi-sub { font-size: 11px; color: #475569; margin-top: 3px; }
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top:6px; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
                    .dash-table th, .dash-table td { border: 1px solid #e2e8f0; padding: 8px 10px; text-align: center; }
                    .dash-table th { background-color: #1e293b; color: white; font-size: 12px; text-transform: uppercase; letter-spacing: 0.4px; }
                    .dash-hl { background-color: #f8fafc; font-weight:600; color: #1e293b; text-align: left !important; padding-left: 14px !important; width: 30%; }
                    .dash-subhead { background-color: #e2e8f0; font-weight: bold; text-align: left !important; padding-left: 14px !important; text-transform: uppercase; font-size: 11px; color: #334155; letter-spacing: 0.5px; }
                    .dash-val { text-align: left !important; padding-left: 14px !important; color: #1e293b; }
                </style>

                <div class="kpi-grid">
                    <div class="kpi-card">
                        <div class="kpi-title">Home Average</div>
                        <div class="kpi-val" style="color:#0284c7;">${avgTemp}°F</div>
                        <div class="kpi-sub">Humidity: <b>${avgHum}%</b></div>
                    </div>
                    <div class="kpi-card">
                        <div class="kpi-title">Hallway Thermostat</div>
                        <div class="kpi-val">${tstatTemp}°F</div>
                        <div class="kpi-sub">Humidity: <b>${tstatHum}%</b></div>
                    </div>
                    <div class="kpi-card">
                        <div class="kpi-title">Active Targets</div>
                        <div class="kpi-val"><span style="color:#0284c7;">${tstatCool}°</span> / <span style="color:#ea580c;">${tstatHeat}°</span></div>
                        <div class="kpi-sub">Mode: <b>${tstatMode}</b> (Fan: ${tstatFan})</div>
                    </div>
                    <div class="kpi-card">
                        <div class="kpi-title">Equipment State</div>
                        <div class="kpi-val" style="margin-top:4px;">${stateBadge}</div>
                        <div class="kpi-sub">Hub Mode: <b>${currentLocMode}</b></div>
                    </div>
                </div>

                <table class="dash-table">
                    <tbody>
                        <tr><td colspan="4" class="dash-subhead">🌤️ Environment, Comfort & Efficiency</td></tr>
                        <tr><td class="dash-hl">Outdoor Conditions</td><td colspan="3" class="dash-val"><b>${outTempStr}</b> &nbsp;|&nbsp; Indoor/Outdoor Split: <b>${inOutDeltaStr}</b></td></tr>
                        <tr><td class="dash-hl">Outdoor Compressor Lockout</td><td colspan="3" class="dash-val">${ambientLockoutStr}</td></tr>
                        <tr><td class="dash-hl">Comfort & Health Score</td><td colspan="3" class="dash-val">${comfortScoreStr} <span style='font-size:12px; color:#64748b; margin-left:6px;'>(${targetDesc} &bull; Ideal Hum: ${targetHum}%)</span></td></tr>
                        <tr><td class="dash-hl">System Efficiency Score</td><td colspan="3" class="dash-val">${efficiencyScoreStr} <span style='font-size:12px; color:#64748b; margin-left:6px;'>(Weighted by Coil Delta-T, Cycle Length & Filter Wear)</span></td></tr>

                        <tr><td colspan="4" class="dash-subhead">⚙️ Active BMS Protections & Diagnostics</td></tr>
                        <tr><td class="dash-hl">Room Alignment & Hysteresis</td><td colspan="3" class="dash-val">${alignmentStr}</td></tr>
                        <tr><td class="dash-hl">Compressor Min-Run Protection</td><td colspan="3" class="dash-val">${bufferStr}</td></tr>
                        <tr><td class="dash-hl">Smart Debounce (Anti-Yo-Yo)</td><td colspan="3" class="dash-val">${debounceStr}</td></tr>
                        <tr><td class="dash-hl">Thermal Coasting (Blower)</td><td colspan="3" class="dash-val">${coastingStr}</td></tr>
                        <tr><td class="dash-hl">Perimeter Doors & Windows</td><td colspan="3" class="dash-val">${perimeterStr}</td></tr>
                        <tr><td class="dash-hl">Safety / Service Lockout</td><td colspan="3" class="dash-val">${serviceStr}</td></tr>
                        <tr><td class="dash-hl">Auto-Changeover Distance</td><td colspan="3" class="dash-val">${swapText}</td></tr>
                        <tr><td class="dash-hl">Setpoint Deadband Gap</td><td colspan="3" class="dash-val">${currentDeadbandStr}</td></tr>
                        <tr><td class="dash-hl">Supply / Return Coil Delta-T</td><td colspan="3" class="dash-val">${deltaTStr}</td></tr>
                        <tr><td class="dash-hl">Current Off-Cycle Idle Time</td><td colspan="3" class="dash-val">${currentIdleStr}</td></tr>
                        
                        <tr><td colspan="4" class="dash-subhead">🛠️ Maintenance & 7-Day Telemetry</td></tr>
                        <tr><td class="dash-hl">7-Day Compressor Activity</td><td colspan="3" class="dash-val">${compressorRunsStr}</td></tr>
                        <tr><td class="dash-hl">Air Filter Life Remaining</td><td colspan="3" class="dash-val">${filterLifeStr} &nbsp;<span style='font-size:12px; color:#64748b;'>(Last Changed: ${state.lastFilterDate ?: "Not Recorded"})</span></td></tr>
                        <tr><td class="dash-hl">Last Professional Service</td><td colspan="3" class="dash-val">${state.lastServiceDate ?: "Not Recorded"} &nbsp;|&nbsp; Contractor: ${hvacContactStr}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
            } else {
                paragraph uiHelpBox("Welcome to Advanced Climate Manager!", "To get started, open <b>Section 0: Core Thermostat & Safety Controls</b> below and select your main thermostat. Once selected, this live BMS telemetry dashboard will automatically populate.")
            }
        }

        // 2. ZONE BREAKDOWN TABLE
        section("<b>📊 Live Room Zone Status (Click to Expand)</b>", hideable: true, hidden: false) {
            paragraph "<div style='font-size:12px; color:#475569; margin-bottom:6px;'>Shows every enabled room zone, its live sensor readings, battery health, occupancy state, and whether it is actively included in the home's temperature calculation right now.</div>"
            def zoneHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Zone Name</th><th>Temp</th><th>Humidity</th><th>Battery</th><th>Occupied?</th><th>Inclusion Status</th></tr></thead><tbody>"
            def hasZones = false
            def maxAgeMs = (staleSensorHours != null ? staleSensorHours.toLong() : 12L) * 3600000L
            def lowBatLimit = lowBatteryThreshold != null ? lowBatteryThreshold.toInteger() : 20
 
            def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
            def isSmartModeAllowed = !averagingModes || (averagingModes as List).contains(location.mode)
            
            for (int i = 1; i <= 5; i++) {
                def zTempDev = getZoneTempDevice(i)
                if (settings["enableZ${i}"] && zTempDev) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Zone ${i}"
                    
                    def tempState = zTempDev.currentState("temperature")
                    def tVal = tempState?.value != null ? tempState.value.toBigDecimal() : null
                    def lastUpdate = tempState?.date?.time ?: now()
                    
                    def isError = tVal == null || tVal < 40.0 || tVal > 100.0 || (now() - lastUpdate) > maxAgeMs
                    def zTempStr = tVal != null ? "<b>${tVal}°F</b>" : "--"
                    
                    def zHumDev = getZoneHumDevice(i)
                    def zHum = (zHumDev && zHumDev.currentValue("humidity") != null) ? zHumDev.currentValue("humidity") : "N/A"
                    
                    def batVal = zTempDev.currentValue("battery")
                    def batStr = batVal != null ? (batVal.toInteger() <= lowBatLimit ? uiBadge("${batVal}% LOW", "#dc2626") : "${batVal}%") : "N/A"
                    
                    def entSwitch = settings["z${i}EntertainmentSwitch"]
                    def isEntForced = entSwitch && entSwitch.currentValue("switch") == "on"
                    def hasOccSensors = hasZoneOccupancySensors(i)
                    
                    def isOccupied = "Always On"
                    def zStatus = uiBadge("Averaging", "#16a34a")
        
                    if (!isSmartModeAllowed) {
                        isOccupied = "Mode Paused"
                        zStatus = uiBadge("Ignored (Mode)", "#f59e0b")
                    } else if (isError) {
                        zStatus = uiBadge("Stale / Error", "#dc2626")
                        isOccupied = "N/A"
                    } else if (isNight && nightSensors) {
                        def isNightForced = nightSensors.any { it.id.toString() == zTempDev.id.toString() }
                        if (isNightForced) {
                            isOccupied = "Yes (Sleep Zone)"
                            zStatus = uiBadge("Averaging (Night)", "#0284c7")
                        } else {
                            isOccupied = "Skipped at Night"
                            zStatus = uiBadge("Ignored (Day Zone)", "#64748b")
                        }
                    } else if (isEntForced) {
                        isOccupied = "Yes (Override Switch)"
                        zStatus = uiBadge("Forced Active", "#0284c7")
                    } else if (enableOccupancy && hasOccSensors) {
                        if (isZoneOccupied(i)) {
                            isOccupied = "Yes (Active)"
                        } else {
                            isOccupied = "No (Empty)"
                            zStatus = uiBadge("Standby (Unoccupied)", "#64748b")
                        }
                    }
   
                    zoneHTML += "<tr><td><b>${zName}</b></td><td>${zTempStr}</td><td>${zHum == 'N/A' ? 'N/A' : zHum + '%'}</td><td>${batStr}</td><td>${isOccupied}</td><td>${zStatus}</td></tr>"
                }
            }
            
            zoneHTML += "</tbody></table>"
            if (hasZones) paragraph zoneHTML else paragraph "<i>No room zones enabled yet. Configure up to 5 rooms in the Zones section below.</i>"
        }
        
        // 3. HISTORICAL TELEMETRY & LOGS
        section("<b>📈 7-Day Runtime History, Cycle Logs & Maintenance Actions</b>", hideable: true, hidden: true) {
            if (enableFilterTracker) {
                paragraph "<b>Maintenance Quick Actions</b>"
                input "resetFilter", "button", title: "✅ Record Filter Change Today (Resets to 100%)", width: 6
                input "resetService", "button", title: "🛠️ Record Professional HVAC Service Today", width: 6
                paragraph "<hr>"
            }

            paragraph "<b>7-Day Daily Runtime Breakdown</b>"
            def runStatsHtml = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Date</th><th>Peak Outdoor<br>(Temp / Hum)</th><th>Cooling Hrs</th><th>Heating Hrs</th><th>Aux Heat Hrs</th><th>Total Cycles</th></tr></thead><tbody>"
            
            def keys = state.runHistory?.keySet()?.sort()?.reverse()
            if (keys) {
                keys.each { date ->
                    def data = state.runHistory[date]
                    def cMins = data.cool ?: 0.0; def hMins = data.heat ?: 0.0; def aMins = data.aux ?: 0.0
                    def cycles = data.runs ?: 0
                    def auxStyle = aMins > 0 ? "color:#dc2626; font-weight:bold;" : ""
                    
                    def maxT = data.maxOutTemp != null ? "${data.maxOutTemp}°F" : "--"
                    def maxH = data.maxOutHum != null ? "${data.maxOutHum}%" : "--"
                    def weatherStr = (maxT == "--" && maxH == "--") ? "--" : "${maxT} / ${maxH}"
                    
                    runStatsHtml += "<tr><td><b>${date}</b></td><td>${weatherStr}</td><td>${String.format('%.1f', cMins/60.0)}h</td><td>${String.format('%.1f', hMins/60.0)}h</td><td style='${auxStyle}'>${String.format('%.1f', aMins/60.0)}h</td><td>${cycles}</td></tr>"
                }
                runStatsHtml += "</tbody></table>"
                paragraph runStatsHtml
            } else {
                paragraph "<i>No daily runtime history recorded yet.</i>"
            }
            input "resetHistory", "button", title: "🗑️ Clear 7-Day History", width: 4

            paragraph "<hr><b>Last 10 Completed Equipment Cycles</b>"
            if (state.recentCycles) {
                paragraph "<div style='background:#f8fafc; padding:10px; border:1px solid #e2e8f0; border-radius:6px; font-size:12px; font-family:monospace;'>${state.recentCycles.join('<br>')}</div>"
            } else {
                paragraph "<i>No completed compressor cycles logged yet.</i>"
            }
            input "resetCycles", "button", title: "🗑️ Clear Cycle Log", width: 4

            paragraph "<hr><b>Last 10 Off-Cycle (Idle) Rest Periods</b>"
            if (state.recentIdleTimes) {
                paragraph "<div style='background:#f8fafc; padding:10px; border:1px solid #e2e8f0; border-radius:6px; font-size:12px; font-family:monospace;'>${state.recentIdleTimes.join('<br>')}</div>"
            } else {
                paragraph "<i>No idle rest periods logged yet.</i>"
            }
            input "resetIdleHistory", "button", title: "🗑️ Clear Idle Log", width: 4

            paragraph "<hr><b>Recent BMS Decision & Action Log (Last 30 Events)</b>"
            input "txtEnable", "bool", title: "Enable Hubitat Live Log Output (Info Logging)", defaultValue: true
            if (state.actionHistory) {
                paragraph "<div style='background:#f8fafc; padding:10px; border:1px solid #e2e8f0; border-radius:6px; font-size:12px; font-family:monospace;'>${state.actionHistory.join('<br>')}</div>"
            }
            input "resetActionHistory", "button", title: "🗑️ Clear Action Log", width: 4
        }

        // ==============================================================================
        // CONFIGURATION SECTIONS (WITH GUIDED EXPLANATIONS)
        // ==============================================================================

        section("<b>0. Core Thermostat, Polling & Safety Controls</b>", hideable: true, hidden: false) {
            paragraph uiHelpBox(
                "Main Hardware & Safety Overrides",
                "Select the primary wall thermostat that controls your HVAC equipment. You can also link optional virtual switches to pause the app or lock the HVAC unit completely OFF while a technician is servicing the unit.<br><br>" +
                "<b>Hardware Minimum Deadband:</b> Most digital thermostats (like Honeywell T6 Pro or Ecobee) enforce a mandatory gap between your Heating and Cooling setpoints (e.g., Heat at 68° and Cool at 71° = 3.0° gap). Setting this to match your thermostat's internal limit prevents the thermostat from rejecting setpoint changes.<br><br>" +
                "<b>Physical Wall Override (2-Hour Hold):</b> Anytime someone walks up to the wall thermostat and changes the temperature or mode manually, this app automatically pauses automation for <b>2 hours</b> so it never fights you.",
                "Set Hardware Deadband to 3.0°F for most thermostats (or 4.0°F–5.0°F if your thermostat requires a wider gap). Leave Polling disabled unless your thermostat is an older Z-Wave model that doesn't report when it turns on/off."
            )
            input "thermostat", "capability.thermostat", title: "<b>Select Main Thermostat</b>", required: false, multiple: false
            input "hardwareDeadbandMin", "decimal", title: "<b>Thermostat Hardware Minimum Deadband (°F)</b>", required: false, defaultValue: 3.0
            input "thermostatPollInterval", "enum", title: "<b>Active Thermostat Polling (Keep-Alive)</b> — Only needed for older Z-Wave thermostats that fail to push state updates automatically", options: ["0":"Disabled (Thermostat reports automatically - Recommended)", "5":"Poll Every 5 Minutes", "10":"Poll Every 10 Minutes"], required: false, defaultValue: "0"
            input "appEnableSwitch", "capability.switch", title: "<b>Master Automation Enable/Disable Switch (Optional)</b> — When OFF, app pauses all actions", required: false, multiple: false
            input "serviceSwitch", "capability.switch", title: "<b>HVAC Service / Force OFF Switch (Optional)</b> — When ON, locks HVAC OFF until turned off", required: false, multiple: false
            if (state.manualHoldEnds && now() < state.manualHoldEnds) input "releaseHold", "button", title: "🔓 Release Active 2-Hour Manual Hold Now"

            paragraph "<hr>"
            paragraph uiHelpBox(
                "Life Safety: Fire, Smoke & Carbon Monoxide Isolation",
                "If any linked Smoke or Carbon Monoxide detector triggers an alarm, this app immediately shuts down the HVAC compressor and forces the indoor blower fan to AUTO/OFF. This critical life-safety measure prevents the ductwork from circulating toxic smoke through bedrooms or feeding fresh oxygen to an active fire.",
                "Link all smart Smoke and CO detectors in your home."
            )
            input "smokeDetectors", "capability.smokeDetector", title: "Select Smoke Detectors", required: false, multiple: true
            input "coDetectors", "capability.carbonMonoxideDetector", title: "Select Carbon Monoxide (CO) Detectors", required: false, multiple: true
        }

        section("<b>1. Base Schedule Setpoints (Home, Away & Good Night)</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Mode-Based Comfort Targets & Energy Setbacks",
                "Defines your baseline Heating and Cooling targets based on your Hubitat Location Mode (e.g., Day/Home, Away, Night).<br><br>" +
                "• <b>Deep Away Setback:</b> When you leave for work or errands, the house uses standard Away temperatures so it can recover quickly when you return. If you remain Away longer than your delay threshold (e.g., 4+ hours on a trip), it automatically shifts to deeper energy-saving temperatures.<br>" +
                "• <b>Extended Arrival Cooling:</b> When returning home on a hot day after a long absence, walls and furniture hold soaked-in heat ('thermal mass'). If the house is hotter than your trigger threshold when you arrive, this runs a single extended cooling cycle down to your Hard Floor limit to pull heat out of the drywall and furniture.",
                "Home: 74° Cool / 68° Heat | Away: 78° Cool / 62° Heat | Good Night: 70° Cool / 66° Heat."
            )
            input "allowedModes", "mode", title: "<b>Master Allowed Modes</b> (Leave BLANK to run 24/7 in all modes)", multiple: true, required: false
            
            paragraph "<b>🏠 Home / Day Schedule</b>"
            input "homeModes", "mode", title: "Select 'Home' Modes (Acts as default fallback if no other mode matches)", multiple: true, required: false
            input "homeCoolingSetpoint", "decimal", title: "Home Cooling Target (°F)", required: false, defaultValue: 74
            input "homeHeatingSetpoint", "decimal", title: "Home Heating Target (°F)", required: false, defaultValue: 68
            
            paragraph "<hr><b>🚗 Away & Vacation Setbacks</b>"
            input "awayModes", "mode", title: "Select 'Away' Modes", multiple: true, required: false
            input "awayCoolingSetpoint", "decimal", title: "Standard Away Cooling Target (°F)", required: false, defaultValue: 78
            input "awayHeatingSetpoint", "decimal", title: "Standard Away Heating Target (°F)", required: false, defaultValue: 62
            
            input "enableDeepAway", "bool", title: "<b>Enable Deep Away Setback</b> (Extra savings during extended absences)", defaultValue: true, submitOnChange: true
            if (enableDeepAway) {
                input "deepAwayDelayHours", "number", title: "Hours Away before engaging Deep Setback", required: false, defaultValue: 4
                input "deepAwayCoolingSetpoint", "decimal", title: "Deep Away Cooling Target (°F)", required: false, defaultValue: 82
                input "deepAwayHeatingSetpoint", "decimal", title: "Deep Away Heating Target (°F)", required: false, defaultValue: 58
            }
 
            input "enableExtendedArrival", "bool", title: "<b>Enable Extended Arrival Cooling</b> (Pulls heat out of furniture/walls after long absences)", defaultValue: true, submitOnChange: true
            if (enableExtendedArrival) {
                input "eaAwayHours", "number", title: "Minimum Away Duration Required (Hours)", required: false, defaultValue: 5
                input "eaTempDiff", "decimal", title: "Degrees Above Home Target Required to Trigger (°F)", required: false, defaultValue: 4.0
                input "eaRunMins", "number", title: "Extra Cooling Extension Time (Minutes)", required: false, defaultValue: 45
                input "eaHardFloor", "decimal", title: "Safety Floor Temperature (°F) — Stops early if house reaches this temp", required: false, defaultValue: 70.0
            }
            
            paragraph "<hr><b>🌙 Good Night / Sleep Schedule</b>"
            input "nightModes", "mode", title: "Select 'Good Night' Modes", multiple: true, required: false
            input "nightCoolingSetpoint", "decimal", title: "Good Night Cooling Target (°F)", required: false, defaultValue: 70
            input "nightHeatingSetpoint", "decimal", title: "Good Night Heating Target (°F)", required: false, defaultValue: 66
            input "nightSensors", "capability.temperatureMeasurement", title: "<b>Dedicated Sleep Temperature Sensors (Optional)</b> — Select Master/Bedroom sensors to prioritize at night. Leave blank to average all enabled zones.", multiple: true, required: false
        }

        section("<b>2. Smart Room Averaging & Dynamic Occupancy (Global Settings)</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Multi-Room Temperature Averaging & Occupancy Weighting",
                "Instead of relying on a single hallway thermostat, the app calculates the true average temperature across your rooms.<br><br>" +
                "• <b>Dynamic Occupancy Weighting:</b> When enabled, rooms with Motion or Honeywell Occupancy sensors are only included in the home's average while occupied. If a room has no movement for your timeout period (e.g., 60 mins), it automatically drops into standby so you aren't paying to cool an empty guest room or office.<br>" +
                "• <b>Fallback Protection:</b> If <i>all</i> rooms become unoccupied at the same time, the app gracefully falls back to the main thermostat sensor.",
                "Enable Occupancy Weighting with a 60-minute timeout so someone reading quietly or watching a movie doesn't get dropped prematurely."
            )
            input "enableOccupancy", "bool", title: "<b>Enable Dynamic Occupancy Weighting</b> (Drop unoccupied rooms after timeout)", defaultValue: false, submitOnChange: true
            if (enableOccupancy) {
                input "occupancyTimeout", "number", title: "Inactivity Timeout (Minutes before an empty room is dropped from average)", required: false, defaultValue: 60
            }
            input "averagingModes", "mode", title: "<b>Modes Allowed to Use Room Averaging</b> (Leave blank for ALL modes)", multiple: true, required: false
        }

        for (int i = 1; i <= 5; i++) {
            def currentZoneName = settings["z${i}Name"] ?: "Zone ${i}"
            def isZEnabled = settings["enableZ${i}"] == true
            def badgeTxt = isZEnabled ? "✅ Enabled" : "⚪ Disabled"
            
            section("<b>🏠 Room Zone ${i}: ${currentZoneName} (${badgeTxt})</b>", hideable: true, hidden: true) {
                input "enableZ${i}", "bool", title: "<b>Enable Zone ${i}</b>", submitOnChange: true
                if (settings["enableZ${i}"]) {
                    paragraph uiHelpBox(
                        "Configuring ${currentZoneName}",
                        "• <b>Honeywell / Unified Room Sensor:</b> If you use a Honeywell Home / T9 / T10 Room Sensor (or an Ecobee/Zooz multi-sensor), select it in the first box to automatically link its Temperature, Humidity, Battery, and Occupancy/Motion in one click.<br>" +
                        "• <b>Standalone Sensors:</b> Alternatively, mix and match separate temperature, humidity, and motion sensors below.<br>" +
                        "• <b>Entertainment / Override Switch:</b> Link a TV plug, office light, or virtual switch to force this room to stay 'Occupied' whenever that switch is ON, even if you are sitting completely still."
                    )
                    input "z${i}Name", "text", title: "<b>Room / Zone Name</b> (e.g., Master Bedroom, Living Room, Office)", required: false, defaultValue: "Zone ${i}", submitOnChange: true
                    input "z${i}Honeywell", "capability.temperatureMeasurement", title: "<b>Unified Honeywell / Multi-Sensor</b> (Auto-links Temp, Humidity, Battery & Occupancy)", required: false
                    input "z${i}Temp", "capability.temperatureMeasurement", title: "Standalone Temperature Sensor (Optional if Unified Sensor selected above)", required: false
                    input "z${i}Hum", "capability.relativeHumidityMeasurement", title: "Standalone Humidity Sensor (Optional)", required: false
                    input "z${i}Motion", "capability.motionSensor", title: "Standalone Motion Sensor(s) (Optional)", required: false, multiple: true
                    input "z${i}Presence", "capability.presenceSensor", title: "Honeywell Occupancy / Presence Child Sensor(s) (Optional)", required: false, multiple: true
                    input "z${i}EntertainmentSwitch", "capability.switch", title: "Entertainment / Keep-Active Override Switch (Optional)", required: false
                }
            }
        }

        section("<b>3. Setpoint Alignment, Stage-1 Hysteresis & Anti-Yo-Yo</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "How the App Controls Your Physical Thermostat",
                "Most thermostats can only read their own built-in hallway sensor. This section bridges the gap between your rooms and the hallway wall unit:<br><br>" +
                "1. <b>Dynamic Setpoint Alignment:</b> If your bedrooms average 76°F, your target is 74°F, and the hallway thermostat reads 74°F, the hallway thermostat would normally sit idle. Alignment temporarily shifts the thermostat's target down by 2°F (to 72°F) so it turns on and cools the bedrooms until they reach 74°F.<br>" +
                "2. <b>Stage 1 Hysteresis (Energy Saver Deadband):</b> Prevents the unit from kicking on for tiny 0.2°F fluctuations. With a 1.0°F Drift and 0.5°F Recovery around a 74°F target, the system rests until the home reaches 75.0°F, then cools continuously down to 74.5°F before resting again.<br>" +
                "3. <b>Smart Debounce (Anti-Yo-Yo Cooldown):</b> After a cycle finishes, cold or warm air lingering in the hallway ductwork can trick the thermostat into re-triggering a few minutes later. Anti-Yo-Yo holds the setpoint safely out of reach for 15 minutes after shutoff.",
                "Enable all three: Max Shift = 3.0°F, Anti-Yo-Yo = 15 mins, Drift = 1.0°F, Recovery = 0.5°F."
            )
            input "enableAverageSync", "bool", title: "<b>Enable Dynamic Setpoint Alignment</b> (Shifts thermostat to match room average)", defaultValue: false, submitOnChange: true
            if (enableAverageSync) {
                input "maxSyncOffset", "decimal", title: "Maximum Allowed Setpoint Shift (°F) — Safety clamp", required: false, defaultValue: 3.0
            }

            paragraph "<hr>"
            input "yoyoCooldownMins", "number", title: "<b>Anti-Yo-Yo Cooldown Duration (Minutes)</b> — Minimum rest after a cycle ends (0 to disable)", required: false, defaultValue: 15
            
            paragraph "<hr>"
            input "enableHysteresis", "bool", title: "<b>Enable Stage 1 Hysteresis Deadband</b> (Prevents micro-cycling)", defaultValue: true, submitOnChange: true
            if (enableHysteresis) {
                input "globalHysteresis", "bool", title: "<b>Apply Hysteresis Globally</b> (Use drift deadband even when Room Averaging is off)", defaultValue: true
                input "hysteresisDrift", "decimal", title: "Allowed Temperature Drift Before Starting (°F)", required: false, defaultValue: 1.0
                input "hysteresisRecovery", "decimal", title: "Recovery Margin — Stop When Within X°F of Target (°F)", required: false, defaultValue: 0.5
            }
        }

        section("<b>4. App-Driven Auto Changeover (Heat ⇄ Cool Mode Swapping)</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Intelligent Seasonal Mode Changeover",
                "During spring and fall ('shoulder seasons'), chilly mornings and warm afternoons can cause a standard thermostat in AUTO mode to fight itself—running Heat in the morning and AC an hour later.<br><br>" +
                "When enabled, this app takes over switching between <b>HEAT</b> and <b>COOL</b> modes based on your <i>Calculated Home Average</i> plus a protective Changeover Deadband buffer.",
                "Enable if you want hands-free Heat/Cool switching; set Changeover Deadband to 1.0°F–1.5°F."
            )
            input "enableAutoSwap", "bool", title: "<b>Enable App-Driven Heat/Cool Mode Swapping</b>", defaultValue: false, submitOnChange: true
            if (enableAutoSwap) {
                input "autoSwapDeadband", "decimal", title: "Changeover Deadband Buffer (°F) — Extra margin required before flipping modes", required: false, defaultValue: 1.0
            }
        }

        section("<b>5. Compressor Minimum Runtime, Overshoot Cutoff & Thermal Coasting</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Protecting Your Compressor From Short-Cycling & Moisture Re-Evaporation",
                "Short-cycling (running for only 5–8 minutes) is the #1 killer of HVAC compressors and leaves your home feeling clammy because the AC coil never stays cold long enough to condense humidity out of the air.<br><br>" +
                "• <b>Minimum Run Time & Evasion Buffer:</b> If the hallway temperature drops too fast or starts too close to the setpoint, the app temporarily glides the setpoint ahead of the thermometer (up to your Setpoint Buffer limit) so the compressor achieves a full, healthy cycle.<br>" +
                "• <b>Max Comfort Overshoot Safety Cutoff:</b> Prevents the 'Freezer Room' effect! If a mild day causes the house to cool or heat past your target by more than your Overshoot Cutoff (e.g., 2.0°F), the app immediately aborts the minimum run timer and lets the unit shut off.<br>" +
                "• <b>Thermal Coasting:</b> When a cycle of 5+ minutes finishes, the indoor coil is still packed with free cold (or heat). Running the blower fan for 3 extra minutes scavenges that free energy into your rooms.",
                "Summer Min Run: 20–25 mins | Winter Min Run: 15 mins | Max Comfort Overshoot: 2.0°F | Thermal Coasting: 3 mins."
            )
            input "enableMinRuntime", "bool", title: "<b>Enable Compressor Minimum Run Time Protection</b>", defaultValue: true, submitOnChange: true
            if (enableMinRuntime) {
                input "summerMinRunTime", "number", title: "Summer Cooling Minimum Run Time (Minutes) — Removes humidity & protects AC", required: false, defaultValue: 25
                input "winterMinRunTime", "number", title: "Winter Heating Minimum Run Time (Minutes)", required: false, defaultValue: 15
                input "protectNightMinRun", "bool", title: "Enforce Minimum Run Time during Good Night mode", defaultValue: true
                input "delayModeChangeForMinRun", "bool", title: "Hold current cycle if Location Mode changes mid-run until Min Run completes", defaultValue: false
                input "tempDropThreshold", "decimal", title: "Rapid Temperature Shift Trigger (°F per minute)", required: false, defaultValue: 0.5
                input "setpointBuffer", "decimal", title: "Maximum Temporary Setpoint Evasion Buffer (°F)", required: false, defaultValue: 3.0
                input "maxComfortOvershoot", "decimal", title: "<b>Max Comfort Overshoot Safety Cutoff (°F)</b> — Aborts buffer early if rooms overshoot target by this amount", required: false, defaultValue: 2.0
                input "shortCycleThreshold", "decimal", title: "Proximity Trigger (°F) — Engage protection immediately if unit starts within X°F of target", required: false, defaultValue: 1.0
            }

            paragraph "<hr>"
            input "enableThermalCoasting", "bool", title: "<b>Enable Thermal Coasting</b> (Runs blower fan after 5+ min compressor cycles to harvest free coil energy)", defaultValue: true, submitOnChange: true
            if (enableThermalCoasting) {
                input "coastingDurationMins", "number", title: "Blower Scavenge Duration After Compressor Stops (Minutes)", required: false, defaultValue: 3
            }

            paragraph "<hr>"
            paragraph uiHelpBox(
                "Hardware Fault & Stuck-Setpoint Failsafes",
                "• <b>Hardware Trip Failsafe:</b> If your physical HVAC unit shuts itself off prematurely 3 times in an hour (e.g., due to a clogged drain float switch or high-pressure limit switch), this app immediately releases all software holds so it doesn't fight a hardware safety trip.<br>" +
                "• <b>Deadlock Failsafe:</b> Guarantees that temporary alignment or buffer offsets can never get stuck for more than 120 minutes."
            )
            input "enableTripFailsafe", "bool", title: "<b>Enable Hardware Trip Failsafe</b> (Releases locks if equipment short-cycles repeatedly)", defaultValue: true, submitOnChange: true
            if (enableTripFailsafe) {
                input "tripCycleCount", "number", title: "Short-Cycle Count to Trigger Trip Release", required: false, defaultValue: 3
                input "tripWindowMins", "number", title: "Rolling Time Window (Minutes)", required: false, defaultValue: 60
            }
            input "enableDeadlock", "bool", title: "<b>Enable Setpoint Deadlock Failsafe</b> (Auto-resets stuck offsets)", defaultValue: true, submitOnChange: true
            if (enableDeadlock) {
                input "deadlockTimeoutMins", "number", title: "Maximum Minutes Allowed Away From Base Target", required: false, defaultValue: 120
            }
        }

        section("<b>6. Heat Pump: Auxiliary / Emergency Heat Strip Suppression</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Stop Expensive Electric Heat Strips From Turning On Unnecessarily",
                "Heat pumps are 300% efficient, but most thermostats automatically kick on expensive 10kW–15kW electric resistance 'Aux Heat' strips whenever the target temperature is 2°F or more above the current room temperature (such as when recovering from Night or Away mode).<br><br>" +
                "When enabled, this feature 'glides' the heating setpoint upward in small steps (e.g., 1.5°F ahead of the current room temperature), keeping the gap just below the thermostat's internal Aux trigger threshold so the heat pump compressor does all the work.",
                "Enable if you have a Heat Pump with electric Aux strips; set Max Step to 1.5°F."
            )
            input "enableAuxSuppression", "bool", title: "<b>Enable Aux Heat Strip Suppression (Smart Setpoint Gliding)</b>", defaultValue: false, submitOnChange: true
            if (enableAuxSuppression) {
                input "maxHeatStep", "decimal", title: "Maximum Setpoint Step Above Current Wall Temp (°F) — Keep below your thermostat's Aux threshold (usually 1.5°F)", required: false, defaultValue: 1.5
            }
        }

        section("<b>7. Outdoor Temperature Lockouts & Coil Delta-T Diagnostics</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Outdoor Ambient Compressor Lockouts & State Humidity Curve",
                "• <b>Low-Ambient AC Lockout:</b> Running an air conditioner when it is below 55°F outside causes liquid refrigerant floodback, frozen evaporator coils, and 7-minute short-cycles. This automatically blocks Cooling calls when outdoor temps drop below your floor.<br>" +
                "• <b>High-Ambient Heat Lockout:</b> Blocks central Heating from kicking on if outdoor temperatures are warmer than your ceiling (e.g., 68°F).<br>" +
                "• <b>Regional Humidity Curve:</b> Selecting your US State calibrates the dashboard's ideal indoor humidity target based on regional climate and outdoor temperature to prevent window condensation in winter.",
                "Set Min Outdoor AC Temp to 55°F and Max Outdoor Heat Temp to 68°F."
            )
            input "userState", "enum", title: "<b>Select Your US State</b> (Calibrates seasonal indoor humidity targets)", options: ["AL":"Alabama", "AK":"Alaska", "AZ":"Arizona", "AR":"Arkansas", "CA":"California", "CO":"Colorado", "CT":"Connecticut", "DE":"Delaware", "FL":"Florida", "GA":"Georgia", "HI":"Hawaii", "ID":"Idaho", "IL":"Illinois", "IN":"Indiana", "IA":"Iowa", "KS":"Kansas", "KY":"Kentucky", "LA":"Louisiana", "ME":"Maine", "MD":"Maryland", "MA":"Massachusetts", "MI":"Michigan", "MN":"Minnesota", "MS":"Mississippi", "MO":"Missouri", "MT":"Montana", "NE":"Nebraska", "NV":"Nevada", "NH":"New Hampshire", "NJ":"New Jersey", "NM":"New Mexico", "NY":"New York", "NC":"North Carolina", "ND":"North Dakota", "OH":"Ohio", "OK":"Oklahoma", "OR":"Oregon", "PA":"Pennsylvania", "RI":"Rhode Island", "SC":"South Carolina", "SD":"South Dakota", "TN":"Tennessee", "TX":"Texas", "UT":"Utah", "VT":"Vermont", "VA":"Virginia", "WA":"Washington", "WV":"West Virginia", "WI":"Wisconsin", "WY":"Wyoming"], required: false, submitOnChange: true
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "<b>Outdoor Temperature Sensor</b> (Weather station or outdoor probe)", required: false
            input "outdoorHumSensor", "capability.relativeHumidityMeasurement", title: "<b>Outdoor Humidity Sensor (Optional)</b>", required: false
            
            input "enableAmbientLockout", "bool", title: "<b>Enable Outdoor Temperature Compressor Lockouts</b>", defaultValue: true, submitOnChange: true
            if (enableAmbientLockout) {
                input "minOutdoorCoolTemp", "decimal", title: "Minimum Outdoor Temp Allowed for AC Cooling (°F) — Blocks AC below this temp", required: false, defaultValue: 55.0
                input "maxOutdoorHeatTemp", "decimal", title: "Maximum Outdoor Temp Allowed for Heating (°F) — Blocks Heat above this temp", required: false, defaultValue: 68.0
            }

            paragraph "<hr>"
            paragraph uiHelpBox(
                "Supply & Return Duct Delta-T (Coil Health Monitoring)",
                "If you place temperature probes in your Return air plenum and Supply (Discharge) air duct, the app measures the exact temperature split across your indoor coil:<br>" +
                "• <b>Cooling Delta-T:</b> Supply air should be <b>14°F to 20°F colder</b> than Return air. If it drops below 12°F after 30 minutes of running, your coil may be icing over or low on refrigerant.<br>" +
                "• <b>Emergency Shutoff:</b> Optional failsafe that locks the unit OFF if Delta-T fails so ice doesn't destroy the compressor."
            )
            input "enableDeltaT", "bool", title: "<b>Enable Supply/Return Duct Delta-T Monitoring</b>", defaultValue: true, submitOnChange: true
            if (enableDeltaT) {
                input "returnSensor", "capability.temperatureMeasurement", title: "Return Air Duct Temperature Sensor", required: false
                input "dischargeSensor", "capability.temperatureMeasurement", title: "Supply (Discharge) Air Duct Temperature Sensor", required: false
                input "deltaTCheckDelay", "number", title: "Minutes into cycle before checking Delta-T (Allows coil to stabilize)", required: false, defaultValue: 30
                input "minCoolingDeltaT", "decimal", title: "Minimum Healthy Cooling Split (°F)", required: false, defaultValue: 12.0
                input "minHeatingDeltaT", "decimal", title: "Minimum Healthy Heating Split (°F)", required: false, defaultValue: 15.0
                input "emergencyShutoff", "bool", title: "<b>Emergency Shutoff:</b> Lock HVAC OFF if Delta-T falls below minimum", defaultValue: false
            }
        }

        section("<b>8. Open Window / Door Perimeter Defeat & Recovery</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Automatic Open Door / Window HVAC Pause",
                "Links contact sensors on exterior doors and windows. If any monitored door or window stays open longer than your delay timer (e.g., 3 minutes), the app saves your current mode and turns the HVAC OFF so you aren't cooling or heating the neighborhood.<br><br>" +
                "Once <i>all</i> monitored doors and windows are closed again, it waits the same delay period to ensure nobody is walking back and forth before restoring your HVAC.",
                "Set Delay to 3–5 minutes so brief trips in and out with groceries don't interrupt your system."
            )
            input "enableWindowDefeat", "bool", title: "<b>Enable Open Window / Door HVAC Shutoff</b>", defaultValue: true, submitOnChange: true
            if (enableWindowDefeat) {
                input "contactSensors", "capability.contactSensor", title: "Select Exterior Door & Window Contact Sensors", required: false, multiple: true
                input "contactDelay", "number", title: "Delay Before Shutting Off & Before Recovering (Minutes)", required: false, defaultValue: 3
            }
        }

        section("<b>9. Smart Filter Wear Tracker & Mesh Self-Healing</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Runtime-Weighted Air Filter Tracker & Z-Wave/Zigbee Self-Healing",
                "• <b>Smart Filter Tracker:</b> " +
                "Instead of a dumb 90-day calendar reminder, this tracks actual blower run hours (default 300 hours) weighted by Indoor/Outdoor Air Quality sensors.<br>" +
                "• <b>Routine Mesh Enforcement:</b> Wireless Z-Wave and Zigbee networks occasionally drop a command packet. Enabling Routine Enforcement quietly verifies and re-syncs your thermostat's setpoints every 15, 30, or 60 minutes."
            )
            input "enableFilterTracker", "bool", title: "<b>Enable Smart Air Filter & Service Tracking</b>", defaultValue: true, submitOnChange: true
            if (enableFilterTracker) {
                input "filterSize", "text", title: "Air Filter Dimensions (e.g., 20x25x1 or 20x25x4)", required: false
                input "maxFilterHours", "number", title: "Rated Filter Lifespan (Blower Run Hours — 300h for 1-inch, 600h for 4-inch media)", required: false, defaultValue: 300
                input "indoorIAQ", "capability.airQuality", title: "Indoor Air Quality Sensor (Optional — weights filter wear by dust/AQI)", required: false
                input "outdoorIAQ", "capability.airQuality", title: "Outdoor Air Quality Sensor (Optional)", required: false
                
                input "filterAlertSwitch", "capability.switch", title: "Filter Alert Virtual Switch (Optional — pulses ON for 10 mins daily when filter is low)", required: false
                if (filterAlertSwitch) {
                    input "filterAlertModes", "mode", title: "Modes Allowed to Trigger Filter Alert Switch", multiple: true, required: false
                }
                  
                paragraph "<hr><b>HVAC Contractor Contact Info (Displayed on Dashboard)</b>"
                input "hvacCompanyName", "text", title: "HVAC Company / Technician Name", required: false
                input "hvacCompanyPhone", "text", title: "HVAC Company Phone Number", required: false
            }

            paragraph "<hr>"
            input "enableEnforcement", "bool", title: "<b>Enable Routine Wireless Setpoint Enforcement (Self-Healing Mesh Loop)</b>", defaultValue: false, submitOnChange: true
            if (enableEnforcement) {
                input "enforcementInterval", "enum", title: "Verification Sweep Interval", options: ["15":"Every 15 Minutes", "30":"Every 30 Minutes (Recommended)", "60":"Every 1 Hour"], required: false, defaultValue: "30"
            }
        }

        section("<b>10. Push Notifications & Sensor Health Alerts</b>", hideable: true, hidden: true) {
            paragraph uiHelpBox(
                "Proactive Health & Maintenance Notifications",
                "Select your Hubitat mobile app or speech/notification devices to receive alerts only when something needs your attention: low room sensor batteries, a sensor that stopped reporting ('stale'), poor coil Delta-T, repeated short-cycling, or seasonal maintenance reminders."
            )
            input "notifyDevices", "capability.notification", title: "<b>Select Notification Devices (Phone / Pushover / TTS)</b>", required: false, multiple: true
            input "notifySensorHealth", "bool", title: "Notify on Room Sensor Low Battery or Stale/Offline Sensor", defaultValue: true
            input "lowBatteryThreshold", "number", title: "Low Battery Warning Threshold (%)", required: false, defaultValue: 20
            input "staleSensorHours", "number", title: "Stale Sensor Timeout (Hours without a temperature report)", required: false, defaultValue: 12
            input "notifyDeltaT", "bool", title: "Notify on Low Coil Delta-T (Freezing Coil / Low Refrigerant Warning)", defaultValue: true
            input "notifyFilter", "bool", title: "Notify when Air Filter Life drops below threshold", defaultValue: true
            input "filterNotifyThreshold", "number", title: "Filter Replacement Alert Threshold (%)", required: false, defaultValue: 10
            input "notifyMaintenance", "bool", title: "Send Spring (May 1) & Fall (Oct 1) Seasonal HVAC Checkup Reminders", defaultValue: true
            input "enableShortCycleNotify", "bool", title: "Notify when Short-Cycle or Hardware Trip Protection triggers", defaultValue: false
        }
    }
}

// ==============================================================================
// HONEYWELL, ZONE & DEBOUNCE HELPER METHODS
// ==============================================================================

def requestEvaluation() {
    // 500ms debounce batches simultaneous multi-sensor events into a single evaluation pass
    runInMillis(500, "evaluateSystem", [overwrite: true])
}

def refreshThermostat() {
    if (!thermostat) return
    if (thermostat.hasCommand("refresh")) {
        thermostat.refresh()
    } else if (thermostat.hasCommand("poll")) {
        thermostat.poll()
    }
}

def getZoneTempDevice(int i) {
    return settings["z${i}Honeywell"] ?: settings["z${i}Temp"]
}

def getZoneHumDevice(int i) {
    if (settings["z${i}Hum"]) return settings["z${i}Hum"]
    def hw = settings["z${i}Honeywell"]
    if (hw && (hw.hasCapability("RelativeHumidityMeasurement") || hw.currentValue("humidity") != null)) return hw
    def tDev = settings["z${i}Temp"]
    if (tDev && (tDev.hasCapability("RelativeHumidityMeasurement") || tDev.currentValue("humidity") != null)) return tDev
    return null
}

boolean hasZoneOccupancySensors(int i) {
    if (settings["z${i}Motion"] || settings["z${i}Presence"]) return true
    def hw = settings["z${i}Honeywell"]
    if (hw && (hw.hasCapability("MotionSensor") || hw.hasCapability("PresenceSensor") || hw.currentValue("motion") != null || hw.currentValue("presence") != null)) return true
    return false
}

boolean isZoneOccupied(int i) {
    def timeoutMins = occupancyTimeout != null ? occupancyTimeout.toLong() : 60L
    def timeoutMs = timeoutMins * 60000L
    def activeMap = state.zoneLastActive ?: [:]
    def occupied = false

    // 1. Check Standard Motion Sensors
    def motionDevs = settings["z${i}Motion"]
    if (motionDevs) {
        def mList = motionDevs instanceof List ? motionDevs : [motionDevs]
        if (mList.any { m -> 
            def devId = m.id.toString()
            m.currentValue("motion") == "active" || (activeMap[devId] && (now() - (activeMap[devId] as Long)) < timeoutMs) 
        }) {
            occupied = true
        }
    }

    // 2. Check Honeywell / Standalone Presence (Occupancy) Sensors
    def presDevs = settings["z${i}Presence"]
    if (!occupied && presDevs) {
        def pList = presDevs instanceof List ? presDevs : [presDevs]
        if (pList.any { p -> 
            def devId = p.id.toString()
            p.currentValue("presence") == "present" || (activeMap[devId] && (now() - (activeMap[devId] as Long)) < timeoutMs) 
        }) {
            occupied = true
        }
    }

    // 3. Check Unified Honeywell Room Sensor (supports Motion and/or Presence on the same device)
    def hw = settings["z${i}Honeywell"]
    if (!occupied && hw) {
        def devId = hw.id.toString()
        def hwMotionActive = hw.currentValue("motion") == "active"
        def hwPresActive = hw.currentValue("presence") == "present"
        def hwRecent = activeMap[devId] && (now() - (activeMap[devId] as Long)) < timeoutMs
        if (hwMotionActive || hwPresActive || hwRecent) {
            occupied = true
        }
    }

    return occupied
}

def scheduleOccupancyCheck() {
    if (!enableOccupancy) return
    def timeoutMins = occupancyTimeout != null ? occupancyTimeout.toLong() : 60L
    def timeoutMs = timeoutMins * 60000L
    def activeMap = state.zoneLastActive ?: [:]
    def currentTime = now()
    Long earliestExpiryMs = null

    activeMap.each { devId, lastActiveTs ->
        if (lastActiveTs != null) {
            long elapsed = currentTime - (lastActiveTs as Long)
            if (elapsed < timeoutMs) {
                long remainingMs = timeoutMs - elapsed
                if (earliestExpiryMs == null || remainingMs < earliestExpiryMs) {
                    earliestExpiryMs = remainingMs
                }
            }
        }
    }

    if (earliestExpiryMs != null) {
        int delaySecs = Math.max(5, Math.ceil(earliestExpiryMs / 1000.0d).toInteger() + 2)
        runIn(delaySecs, "occupancyExpirationSweep")
    }
}

def occupancyExpirationSweep() {
    logInfo("Occupancy timeout window reached. Re-evaluating active zone averages.")
    evaluateSystem()
    scheduleOccupancyCheck()
}

def pushSetpointsSafely(BigDecimal targetCool, BigDecimal targetHeat, String logMessage = null) {
    state.expectedCool = targetCool
    state.expectedHeat = targetHeat

    def curCool = thermostat.currentValue("coolingSetpoint")?.toBigDecimal()
    def curHeat = thermostat.currentValue("heatingSetpoint")?.toBigDecimal()
    def coolChanged = (curCool == null || Math.abs((curCool - targetCool).doubleValue()) >= 0.05)
    def heatChanged = (curHeat == null || Math.abs((curHeat - targetHeat).doubleValue()) >= 0.05)
    if (!coolChanged && !heatChanged) return

    state.lastCommandTime = now()
    unschedule("sendDelayedSetpoint")

    // Sequence commands and stagger by 3 seconds when both change so Z-Wave/Zigbee thermostats never drop the second command
    if (curCool != null && targetHeat > curCool) {
        // Moving up: raise cooling setpoint first to make room for heating setpoint
        if (coolChanged && heatChanged) {
            thermostat.setCoolingSetpoint(targetCool)
            runIn(3, "sendDelayedSetpoint", [data: [type: "heat", val: targetHeat.toDouble()]])
        } else if (coolChanged) {
            thermostat.setCoolingSetpoint(targetCool)
        } else if (heatChanged) {
            thermostat.setHeatingSetpoint(targetHeat)
        }
    } else if (curHeat != null && targetCool < curHeat) {
        // Moving down: lower heating setpoint first to make room for cooling setpoint
        if (heatChanged && coolChanged) {
            thermostat.setHeatingSetpoint(targetHeat)
            runIn(3, "sendDelayedSetpoint", [data: [type: "cool", val: targetCool.toDouble()]])
        } else if (heatChanged) {
            thermostat.setHeatingSetpoint(targetHeat)
        } else if (coolChanged) {
            thermostat.setCoolingSetpoint(targetCool)
        }
    } else {
        if (coolChanged && heatChanged) {
            thermostat.setCoolingSetpoint(targetCool)
            runIn(3, "sendDelayedSetpoint", [data: [type: "heat", val: targetHeat.toDouble()]])
        } else if (coolChanged) {
            thermostat.setCoolingSetpoint(targetCool)
        } else if (heatChanged) {
            thermostat.setHeatingSetpoint(targetHeat)
        }
    }

    if (logMessage) {
        logAction(logMessage)
    }
}

def sendDelayedSetpoint(data) {
    if (!thermostat || !data) return
    state.lastCommandTime = now()
    BigDecimal val = data.val.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    if (val.stripTrailingZeros().scale() <= 0) val = val.setScale(0, BigDecimal.ROUND_HALF_UP)
    if (data.type == "heat") {
        thermostat.setHeatingSetpoint(val)
    } else if (data.type == "cool") {
        thermostat.setCoolingSetpoint(val)
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.recentCycles) state.recentCycles = []
    if (!state.recentIdleTimes) state.recentIdleTimes = []
    if (state.filterRunMinutes == null) state.filterRunMinutes = 0.0
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    if (!state.runHistory) state.runHistory = [:]
    
    // Initialize Failsafe & Logic Trackers
    if (!state.shortCycleLog) state.shortCycleLog = []
    state.deviationStartTime = null
    state.rebootLockoutEnds = null
    if (state.awayStartTime == null && awayModes && (awayModes as List).contains(location.mode)) {
        state.awayStartTime = now()
    } else if (!awayModes || !(awayModes as List).contains(location.mode)) {
        state.awayStartTime = null
    }
    state.extendedArrivalEnds = null
    state.coastingEnds = null
    if (!state.idleStartTime) state.idleStartTime = now()
    
    if (!state.lastFilterDate) state.lastFilterDate = "Not Recorded"
    if (!state.lastServiceDate) state.lastServiceDate = "Not Recorded"
 
    state.isBuffering = false; state.modeDelayLogged = false
    state.manualHoldEnds = null; state.windowOpenHold = false; state.fireEmergency = false
    state.deltaTShutoff = false
    state.windowDefeatTime = null; state.windowRecoveryTime = null
    state.preDefeatMode = null
    state.currentCycleMaxDeltaT = null
    if (state.lastCycleMode == null) state.lastCycleMode = null
    
    state.expectedCool = thermostat?.currentValue("coolingSetpoint")?.toBigDecimal()
    state.expectedHeat = thermostat?.currentValue("heatingSetpoint")?.toBigDecimal()
    state.alignmentLockout = null; state.alignmentLockoutTarget = null
    state.activeHysteresis = "idle"
    state.lastCommandTime = null
    
    // Active Timer Adoption During App Save
    def tstatState = thermostat?.currentValue("thermostatOperatingState")?.toLowerCase() ?: ""
    if (tstatState == "cooling" || tstatState == "heating") {
        if (!state.cycleStartTime) state.cycleStartTime = now()
        state.currentAction = tstatState
        state.activeHysteresis = tstatState
        if (!state.cycleStartMode) state.cycleStartMode = location.mode
        if (!state.cycleStartCoolTarget) state.cycleStartCoolTarget = thermostat?.currentValue("coolingSetpoint")
        if (!state.cycleStartHeatTarget) state.cycleStartHeatTarget = thermostat?.currentValue("heatingSetpoint")
        if (state.startTemp == null) state.startTemp = thermostat?.currentValue("temperature")
        logAction("App Update: Adopted active ${tstatState} cycle to maintain compressor protection.")
        if (enableMinRuntime) runIn(60, "compressorWatchdog")
    } else if (tstatState.contains("aux") || tstatState.contains("emergency")) {
        if (!state.cycleStartTime) state.cycleStartTime = now()
        state.currentAction = "auxHeating"
    } else {
        state.cycleStartTime = null; state.currentAction = "idle"; state.cycleStartMode = null
        state.cycleStartCoolTarget = null; state.cycleStartHeatTarget = null
    }
    
    if (thermostat) {
        subscribe(thermostat, "thermostatOperatingState", "hvacStateHandler")
        subscribe(thermostat, "thermostatMode", "thermostatModeHandler")
        subscribe(thermostat, "coolingSetpoint", "setpointHandler")
        subscribe(thermostat, "heatingSetpoint", "setpointHandler")
        subscribe(thermostat, "temperature", "sensorHandler") 
    }
    
    if (enableDeltaT) {
        if (returnSensor) subscribe(returnSensor, "temperature", "sensorHandler")
        if (dischargeSensor) subscribe(dischargeSensor, "temperature", "sensorHandler")
    }
    
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", "weatherTrackingHandler")
    if (outdoorHumSensor) subscribe(outdoorHumSensor, "humidity", "weatherTrackingHandler")
    
    subscribe(location, "mode", "modeChangeHandler")
    subscribe(location, "systemStart", "hubRestartHandler")
    
    if (smokeDetectors) subscribe(smokeDetectors, "smoke", "smokeCoHandler")
    if (coDetectors) subscribe(coDetectors, "carbonMonoxide", "smokeCoHandler")
    
    if (serviceSwitch) subscribe(serviceSwitch, "switch", "serviceSwitchHandler")
    
    if (nightSensors) subscribe(nightSensors, "temperature", "sensorHandler")

    def activeMap = state.zoneLastActive ?: [:]
    for (int i = 1; i <= 5; i++) {
        if (settings["enableZ${i}"]) {
            def zTempDev = getZoneTempDevice(i)
            if (zTempDev) subscribe(zTempDev, "temperature", "sensorHandler")
            
            def zHumDev = getZoneHumDevice(i)
            if (zHumDev) subscribe(zHumDev, "humidity", "humidityHandler")
            
            def mDevs = settings["z${i}Motion"]
            if (mDevs) {
                subscribe(mDevs, "motion", "motionHandler")
                def mList = mDevs instanceof List ? mDevs : [mDevs]
                mList.each { m ->
                    if (m.currentValue("motion") == "active") activeMap[m.id.toString()] = now()
                }
            }
            
            def pDevs = settings["z${i}Presence"]
            if (pDevs) {
                subscribe(pDevs, "presence", "occupancyHandler")
                def pList = pDevs instanceof List ? pDevs : [pDevs]
                pList.each { p ->
                    if (p.currentValue("presence") == "present") activeMap[p.id.toString()] = now()
                }
            }
            
            if (settings["z${i}EntertainmentSwitch"]) subscribe(settings["z${i}EntertainmentSwitch"], "switch", "entertainmentSwitchHandler")
            
            def hw = settings["z${i}Honeywell"]
            if (hw) {
                subscribe(hw, "motion", "motionHandler")
                subscribe(hw, "presence", "occupancyHandler")
                if (hw.currentValue("motion") == "active" || hw.currentValue("presence") == "present") {
                    activeMap[hw.id.toString()] = now()
                }
            }
        }
    }
    state.zoneLastActive = activeMap
    
    if (enableWindowDefeat && contactSensors) subscribe(contactSensors, "contact", "contactHandler")
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", "enableSwitchHandler")
    
    schedule("0 0 10 * * ?", "dailyMaintenanceCheck") 
    schedule("0 0 8 * * ?", "scheduleRandomFilterAlert")
    
    if (enableEnforcement) {
        def interval = enforcementInterval ?: "30"
        if (interval == "15") runEvery15Minutes("routineSweep")
        else if (interval == "30") runEvery30Minutes("routineSweep")
        else if (interval == "60") runEvery1Hour("routineSweep")
    }

    if (thermostatPollInterval == "5") runEvery5Minutes("refreshThermostat")
    else if (thermostatPollInterval == "10") runEvery10Minutes("refreshThermostat")
    
    logAction("App Initialized. Modular BMS Engine v2.4 Ready.")
    
    smokeCoHandler([:])
    if (enableWindowDefeat) contactHandler([:]) 
    scheduleOccupancyCheck()
    evaluateSystem()
}

def entertainmentSwitchHandler(evt) {
    logInfo("Entertainment Switch ${evt.device.displayName} turned ${evt.value}. Re-evaluating zone averages.")
    requestEvaluation()
}

def weatherTrackingHandler(evt) {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    def history = state.runHistory ?: [:]
    def todayData = history[today] ?: [cool: 0.0, heat: 0.0, aux: 0.0, runs: 0, maxRun: 0.0, minRun: 9999.0, maxOutTemp: null, maxOutHum: null]
    
    def val = evt.value?.toBigDecimal()
    if (val != null) {
        if (evt.name == "temperature") {
            if (todayData.maxOutTemp == null || val > todayData.maxOutTemp) {
                todayData.maxOutTemp = val
            }
        } else if (evt.name == "humidity") {
            if (todayData.maxOutHum == null || val > todayData.maxOutHum) {
                todayData.maxOutHum = val
            }
        }
    }
    
    history[today] = todayData
    state.runHistory = history // Forceful DB Write
    
    if (evt.name == "temperature" && enableAmbientLockout) {
        requestEvaluation()
    }
}

def routineSweep() {
    if (state.fireEmergency || state.windowOpenHold || state.deltaTShutoff || state.isBuffering) return 
    logAction("Running routine setpoint enforcement sweep.")
    evaluateSystem()
}

def hubRestartHandler(evt) {
    state.rebootLockoutEnds = now() + 300000 
    logAction("CRITICAL: Hub reboot or power cycle detected. Enforcing a 5-minute compressor lockout to allow physical HVAC pressures to equalize.")
    
    def tstatState = thermostat?.currentValue("thermostatOperatingState")?.toLowerCase()
    def isRunning = tstatState == "cooling" || tstatState == "heating"
    
    if (isRunning && state.cycleStartTime != null) {
        logAction("Recovery: HVAC is actively running. Preserving cycle timer to maintain Compressor Protection.")
    } else {
        state.cycleStartTime = null
        state.currentAction = "idle"
        state.cycleStartMode = null
        state.cycleStartCoolTarget = null
        state.cycleStartHeatTarget = null
    }
    
    state.idleStartTime = now()
    state.isBuffering = false; state.windowOpenHold = false
    state.windowDefeatTime = null; state.windowRecoveryTime = null
    state.modeDelayLogged = false
    state.alignmentLockout = null; state.alignmentLockoutTarget = null
    state.activeHysteresis = isRunning ? tstatState : "idle"
    state.deviationStartTime = null
    state.coastingEnds = null
    state.extendedArrivalEnds = null
    state.yoyoCooldownEnds = null 
 
    unschedule()
  
    schedule("0 0 10 * * ?", "dailyMaintenanceCheck")
    schedule("0 0 8 * * ?", "scheduleRandomFilterAlert")
    
    if (isRunning && enableMinRuntime) {
        runIn(60, "compressorWatchdog")
    }
    
    if (enableEnforcement) {
        def interval = enforcementInterval ?: "30"
        if (interval == "15") runEvery15Minutes("routineSweep")
        else if (interval == "30") runEvery30Minutes("routineSweep")
        else if (interval == "60") runEvery1Hour("routineSweep")
    }

    if (thermostatPollInterval == "5") runEvery5Minutes("refreshThermostat")
    else if (thermostatPollInterval == "10") runEvery10Minutes("refreshThermostat")
    
    smokeCoHandler([:])
    runIn(300, "executePostRebootEval")
}

def executePostRebootEval() {
    logAction("Hub Reboot 5-minute lockout complete. Restoring normal BMS operations.")
    if (enableWindowDefeat) contactHandler([:])
    scheduleOccupancyCheck()
    evaluateSystem()
}

def humidityHandler(evt) {
    requestEvaluation() 
}

def smokeCoHandler(evt) {
    def isFire = false
    if (smokeDetectors && smokeDetectors.any { it.currentValue("smoke") == "detected" }) isFire = true
    if (coDetectors && coDetectors.any { it.currentValue("carbonMonoxide") == "detected" }) isFire = true
    
    if (isFire && !state.fireEmergency) {
        state.fireEmergency = true
        
        def tMode = thermostat?.currentValue("thermostatMode")
        if (tMode && tMode != "off") state.preDefeatMode = tMode
        
        logAction("CRITICAL EMERGENCY: Smoke/CO detected! Executing HVAC Fire Isolation.")
        
        if (thermostat) {
            state.lastCommandTime = now()
            thermostat.off()
            thermostat.setThermostatFanMode("auto")
        }
        
        if (notifyDevices) notifyDevices.deviceNotification("CRITICAL: Smoke/CO detected. HVAC shut down to prevent smoke spread.")
        evaluateSystem()
    } else if (!isFire && state.fireEmergency) {
        state.fireEmergency = false
        logAction("Emergency Cleared: Smoke/CO no longer detected. Releasing Fire Isolation.")
        
        if (state.preDefeatMode && thermostat) {
            state.lastCommandTime = now()
            thermostat.setThermostatMode(state.preDefeatMode)
            state.preDefeatMode = null
        }
        
        evaluateSystem()
    }
}

def serviceSwitchHandler(evt) {
    if (evt.value == "on") {
        logAction("BMS Command -> HVAC Service Mode ACTIVATED. Forcing HVAC OFF and suspending automation.")
        if (thermostat) {
            state.lastCommandTime = now()
            thermostat.off()
        }
    } else {
        logAction("BMS Command -> HVAC Service Mode DEACTIVATED. Restoring automation.")
    }
    evaluateSystem()
}

String getHumanReadableStatus() {
    def status = ""
    
    if (state.fireEmergency) {
        return "<span style='color:#dc2626; font-size:14px;'><b>🚨 CRITICAL: FIRE / CO ISOLATION ACTIVE. HVAC SHUT DOWN. 🚨</b></span>"
    }
    
    if (serviceSwitch && serviceSwitch.currentValue("switch") == "on") {
        return "<span style='color:#dc2626; font-size:14px;'><b>🛠️ SERVICE MODE ACTIVE. HVAC LOCKED OFF.</b></span>"
    }

    if (state.deltaTShutoff) {
        return "<span style='color:#dc2626; font-size:14px;'><b>❄️ EMERGENCY DELTA-T SHUTOFF ACTIVE. HVAC LOCKED OFF (Click 'Clear Locks & Enforce Setpoints' to reset).</b></span>"
    }
    
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") status = "The application is paused via the Master Enable/Disable Switch."
    else if (allowedModes && !(allowedModes as List).contains(location.mode)) status = "<span style='color:#d97706;'><b>App Paused by Mode:</b></span> Current mode (<b>${location.mode}</b>) is not in your Master Allowed Modes list."
    else if (state.rebootLockoutEnds && now() < state.rebootLockoutEnds) status = "<span style='color:#dc2626;'><b>Hub Reboot Lockout:</b></span> Allowing compressor pressures to equalize. Resumes in ${Math.round((state.rebootLockoutEnds - now()) / 60000)} minutes."
    else if (state.windowRecoveryTime && now() < state.windowRecoveryTime) status = "<span style='color:#0284c7;'><b>HVAC Recovery Pending:</b></span> Doors/windows closed. Restoring in ${Math.round((state.windowRecoveryTime - now()) / 60000)} minutes."
    else if (state.windowOpenHold) status = "<span style='color:#dc2626;'><b>HVAC Paused (OFF)</b></span> because a monitored exterior window or door is open."
    else if (state.manualHoldEnds && now() < state.manualHoldEnds) status = "<span style='color:#d97706;'><b>Manual Wall Override Active:</b></span> Automation paused for ${Math.round((state.manualHoldEnds - now()) / 60000)} more minutes."
    else if (state.isBuffering) {
        def runMins = state.cycleStartTime ? (now() - state.cycleStartTime) / 60000.0 : 0
        def remaining = Math.max(0L, Math.round(getMinRunTime() - runMins))
        status = "<span style='color:#0284c7;'><b>Compressor Protection Engaged:</b></span> Holding cycle ON to satisfy Minimum Run Time and prevent short-cycling (${remaining} mins remaining)."
    }
    else if (state.yoyoCooldownEnds && now() < state.yoyoCooldownEnds.toLong() && state.currentAction == "idle" && (yoyoCooldownMins == null || yoyoCooldownMins.toInteger() > 0)) status = "<span style='color:#d97706;'><b>Anti-Yo-Yo Cooldown Active:</b></span> Evading ambient hallway temperature to prevent immediate re-triggering."
    else if (state.extendedArrivalEnds && now() < state.extendedArrivalEnds) status = "<span style='color:#0284c7;'><b>Extended Arrival Cooling Active:</b></span> Pulling soaked heat out of home thermal mass (${Math.round((state.extendedArrivalEnds - now()) / 60000)} mins left, or until ${eaHardFloor ?: 70.0}°F)."
    else {
        def mode = thermostat?.currentValue("thermostatOperatingState")?.toLowerCase()
        if (mode?.contains("aux") || mode?.contains("emergency")) status = "<span style='color:#dc2626;'><b>WARNING: Auxiliary Heat Strips Active.</b></span> Running high-wattage backup resistance heat."
        else {
            def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
            def isAway = awayModes ? (awayModes as List).contains(location.mode) : false
            
            if (isNight) status = "Good Night sleep schedule active. Bedroom targets & compressor protections enforced."
            else if (isAway && enableDeepAway && state.awayStartTime && (now() - state.awayStartTime) >= ((deepAwayDelayHours ?: 4) * 3600000L)) {
                status = "<span style='color:#0284c7;'><b>Deep Away Setback Active:</b></span> Extended absence detected; deep energy-saving targets enforced."
            }
            else if (mode == "cooling" || mode == "heating") status = "Operating normally. Actively <b>${mode}</b> to satisfy active room zones."
            else if (state.coastingEnds && now() < state.coastingEnds) status = "<span style='color:#16a34a;'><b>Thermal Coasting Active:</b></span> Scavenging leftover coil energy with the indoor blower fan."
            else status = "System is <b>IDLE</b>. Active room temperatures are comfortably within target deadband."
        }
    }
    
    return status
}

BigDecimal getBaseCoolTarget(String evalMode = location.mode) {
    def isNight = nightModes ? (nightModes as List).contains(evalMode) : false
    def isAway = awayModes ? (awayModes as List).contains(evalMode) : false
    if (isNight) return (nightCoolingSetpoint != null ? nightCoolingSetpoint : 70.0).toBigDecimal()
    if (isAway) {
        if (enableDeepAway && state.awayStartTime && (now() - state.awayStartTime) >= ((deepAwayDelayHours ?: 4) * 3600000L)) {
            return (deepAwayCoolingSetpoint != null ? deepAwayCoolingSetpoint : 82.0).toBigDecimal()
        }
        return (awayCoolingSetpoint != null ? awayCoolingSetpoint : 78.0).toBigDecimal()
    }
    return (homeCoolingSetpoint != null ? homeCoolingSetpoint : 74.0).toBigDecimal()
}

BigDecimal getBaseHeatTarget(String evalMode = location.mode) {
    def isNight = nightModes ? (nightModes as List).contains(evalMode) : false
    def isAway = awayModes ? (awayModes as List).contains(evalMode) : false
    if (isNight) return (nightHeatingSetpoint != null ? nightHeatingSetpoint : 66.0).toBigDecimal()
    if (isAway) {
        if (enableDeepAway && state.awayStartTime && (now() - state.awayStartTime) >= ((deepAwayDelayHours ?: 4) * 3600000L)) {
            return (deepAwayHeatingSetpoint != null ? deepAwayHeatingSetpoint : 58.0).toBigDecimal()
        }
        return (awayHeatingSetpoint != null ? awayHeatingSetpoint : 62.0).toBigDecimal()
    }
    return (homeHeatingSetpoint != null ? homeHeatingSetpoint : 68.0).toBigDecimal()
}

BigDecimal getAverageTemp() {
    if (averagingModes && !(averagingModes as List).contains(location.mode)) {
        return thermostat?.currentValue("temperature") != null ? thermostat.currentValue("temperature").toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0G
    }

    def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
    def maxAgeMs = (staleSensorHours != null ? staleSensorHours.toLong() : 12L) * 3600000L
    
    if (isNight && nightSensors) {
        def nTotal = 0.0G
        def nCount = 0
        nightSensors.each { nSensor ->
            def tempState = nSensor.currentState("temperature")
            if (tempState != null && tempState.value != null) {
                def tVal = tempState.value.toBigDecimal()
                def lastUpdate = tempState.date?.time ?: now()
                if (tVal >= 40.0 && tVal <= 100.0 && (now() - lastUpdate) <= maxAgeMs) {
                    nTotal += tVal
                    nCount++
                } else {
                    logInfo("Ignored night sensor ${nSensor.displayName} due to stale or out-of-bounds data (${tVal}°).")
                }
            }
        }
        if (nCount > 0) return (nTotal / nCount).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    
    def total = 0.0G; def count = 0
    for (int i = 1; i <= 5; i++) {
        def tempDev = getZoneTempDevice(i)
        if (settings["enableZ${i}"] && tempDev) {
            def tempState = tempDev.currentState("temperature")
        
            def entSwitch = settings["z${i}EntertainmentSwitch"]
            def isEntForced = entSwitch && entSwitch.currentValue("switch") == "on"
            def hasOcc = hasZoneOccupancySensors(i)
            
            if (tempState != null && tempState.value != null) {
                def tVal = tempState.value.toBigDecimal()
                def lastUpdate = tempState.date?.time ?: now()
  
                if (tVal >= 40.0 && tVal <= 100.0 && (now() - lastUpdate) <= maxAgeMs) {
                    def isOccupied = hasOcc ? isZoneOccupied(i) : true
                    
                    if (!enableOccupancy || !hasOcc || isOccupied || isEntForced) {
                        total += tVal; count++
                    }
                } else {
                    logInfo("Ignored sensor ${tempDev.displayName} due to stale or out-of-bounds data (${tVal}°).")
                }
            }
        }
    }
    if (count == 0 && thermostat?.currentValue("temperature") != null) return thermostat.currentValue("temperature").toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    return count > 0 ? (total / count).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0G
}

BigDecimal getAverageHumidity() {
    if (averagingModes && !(averagingModes as List).contains(location.mode)) {
        return thermostat?.currentValue("humidity") != null ? thermostat.currentValue("humidity").toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0G
    }

    def total = 0.0G; def count = 0
    for (int i = 1; i <= 5; i++) {
        def humDev = getZoneHumDevice(i)
        if (settings["enableZ${i}"] && humDev) {
            if (humDev.currentValue("humidity") != null) { total += humDev.currentValue("humidity").toBigDecimal(); count++ }
        }
    }
    if (count == 0 && thermostat?.currentValue("humidity") != null) return thermostat.currentValue("humidity").toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    return count > 0 ? (total / count).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0G
}

def motionHandler(evt) { 
    def devId = evt.device.id.toString()
    def activeMap = state.zoneLastActive ?: [:]
    activeMap[devId] = now()
    state.zoneLastActive = activeMap

    if (evt.value == "active") { 
        requestEvaluation() 
    } else if (evt.value == "inactive") {
        scheduleOccupancyCheck()
    }
}

def occupancyHandler(evt) {
    def devId = evt.device.id.toString()
    def activeMap = state.zoneLastActive ?: [:]
    activeMap[devId] = now()
    state.zoneLastActive = activeMap

    if (evt.value == "present") {
        requestEvaluation()
    } else if (evt.value == "not present") {
        scheduleOccupancyCheck()
    }
}

def appButtonHandler(btn) {
    def todayStr = new Date().format("MM/dd/yyyy", location.timeZone)
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
        if (enableWindowDefeat) contactHandler([:])
    }
    else if (btn == "btnEnforceModes") {
        state.rebootLockoutEnds = null
        state.manualHoldEnds = null
        state.windowOpenHold = false
        state.deltaTShutoff = false
        state.windowDefeatTime = null
        state.windowRecoveryTime = null
        state.isBuffering = false
        state.preBufferCool = null
        state.preBufferHeat = null
        state.preDefeatMode = null
        unschedule("executeWindowDefeat")
        unschedule("releaseWindowDefeat")
        unschedule("releaseBuffer")
        state.alignmentLockout = null
        state.activeHysteresis = "idle"
        state.yoyoCooldownEnds = null
        state.deviationStartTime = null
        state.expectedCool = null
        state.expectedHeat = null
        state.extendedArrivalEnds = null 
        logAction("User manually enforced mode setpoints. Clearing all temporary locks and forcing target re-evaluation.")
        evaluateSystem()
    }
    else if (btn == "resetFilter") { 
        state.filterRunMinutes = 0.0
        state.lastFilterDate = todayStr
        state.filterAlertSent = false
        
        if (filterAlertSwitch && filterAlertSwitch.currentValue("switch") == "on") {
            filterAlertSwitch.off()
        }
        unschedule("turnOffFilterAlertSwitch")
        
        logAction("Filter logged as changed. Life reset to 100%. Alert switch reset if active.") 
    } 
    else if (btn == "resetService") {
        state.lastServiceDate = todayStr
        logAction("HVAC Service recorded for today.")
    }
    else if (btn == "releaseHold") { 
        state.manualHoldEnds = null
        state.preBufferCool = null
        state.preBufferHeat = null
        logAction("Manual Hold released by user.")
        evaluateSystem() 
    } 
    else if (btn == "resetHistory") { 
        state.runHistory = [:]
        logAction("Cycle Tracking history cleared.") 
    }
    else if (btn == "resetCycles") {
        state.recentCycles = []
        logAction("Compressor cycle history cleared.")
    }
    else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    }
    else if (btn == "resetIdleHistory") {
        state.recentIdleTimes = []
        logAction("Off-cycle (idle) history cleared.")
    }
}

def enableSwitchHandler(evt) { if (evt.value == "off") logAction("App Disabled."); else evaluateSystem() }

def modeChangeHandler(evt) { 
    state.manualHoldEnds = null
    def isAway = awayModes ? (awayModes as List).contains(evt.value) : false
    
    if (enableMinRuntime && state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
        def runMins = (now() - state.cycleStartTime) / 60000.0
        if (runMins < getMinRunTime()) {
            logAction("Mode changed to ${evt.value}, but Compressor Min Runtime is active. Physical unit operation will be aggressively held until complete.")
        }
    }

    if (isAway) {
        if (!state.awayStartTime) state.awayStartTime = now()
        if (state.extendedArrivalEnds) {
            state.extendedArrivalEnds = null
            logAction("House is Away. Aborting active Extended Arrival Cooling sequence.")
        }
    } else {
        // --- EXTENDED ARRIVAL COOLING TRIGGER ---
        if (state.awayStartTime && enableExtendedArrival) {
            def awayHours = (now() - state.awayStartTime) / 3600000.0
            def minAwayHrs = eaAwayHours != null ? eaAwayHours.toBigDecimal() : 5.0
            if (awayHours >= minAwayHrs) {
                def currentTemp = getAverageTemp()
                def targetCool = homeCoolingSetpoint != null ? homeCoolingSetpoint.toBigDecimal() : 74.0
                def reqDiff = eaTempDiff != null ? eaTempDiff.toBigDecimal() : 4.0
                
                if (currentTemp >= (targetCool + reqDiff)) {
                    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
                    
                    if (state.lastExtendedArrivalDate != todayStr) {
                        def baseMinRun = summerMinRunTime != null ? summerMinRunTime.toInteger() : 25
                        def extraRun = eaRunMins != null ? eaRunMins.toInteger() : 45
                        def totalRun = baseMinRun + extraRun
                        
                        state.extendedArrivalEnds = now() + (totalRun * 60000L)
                        state.lastExtendedArrivalDate = todayStr
                        logAction("Extended Arrival Cooling Triggered! House was away for ${String.format('%.1f', awayHours)} hours and temp is ${currentTemp}°. Forcing ${totalRun} mins of cooling (${baseMinRun}m min-run + ${extraRun}m extension).")
                    } else {
                        logInfo("Extended Arrival Cooling skipped: Feature already executed once today.")
                    }
                }
            }
        }
        state.awayStartTime = null
    }
    evaluateSystem() 
}

def thermostatModeHandler(evt) {
    if (state.fireEmergency || state.windowOpenHold || state.deltaTShutoff || (serviceSwitch && serviceSwitch.currentValue("switch") == "on")) {
        def tMode = thermostat.currentValue("thermostatMode")?.toLowerCase()
        if (tMode != "off") {
            logAction("WARNING: Manual override attempted while safety failsafe is active. Enforcing OFF.")
            evaluateSystem()
        }
        return 
    }
    
    if (state.isBuffering) return 
    
    // 15-second blindspot for incoming mode echoes
    if (state.lastCommandTime && (now() - state.lastCommandTime) < 15000) return 
    
    def newVal = evt.value?.toLowerCase()
    
    if (!state.manualHoldEnds || now() > state.manualHoldEnds) { 
        state.manualHoldEnds = now() + 7200000 // 2 Hours Hold
        state.preBufferCool = null
        state.preBufferHeat = null
        logAction("MANUAL OVERRIDE: Physical thermostat mode changed to ${newVal?.toUpperCase()}. Automation suspended for 2 Hours.") 
        evaluateSystem() 
    } else {
        state.manualHoldEnds = now() + 7200000 
        state.preBufferCool = null
        state.preBufferHeat = null
        logAction("MANUAL OVERRIDE: Thermostat mode adjusted again. 2-Hour hold timer reset.")
        evaluateSystem()
    }
}

def setpointHandler(evt) {
    if (state.fireEmergency || state.windowOpenHold || state.deltaTShutoff || (serviceSwitch && serviceSwitch.currentValue("switch") == "on")) {
        def tMode = thermostat.currentValue("thermostatMode")?.toLowerCase()
        if (tMode != "off") {
            logAction("WARNING: Setpoint override attempted while safety failsafe is active. Enforcing OFF.")
            evaluateSystem()
        }
        return 
    }
    
    if (state.isBuffering) return 
    
    // Block false manual holds during Evasion Glide
    def runMins = state.cycleStartTime ? (now() - state.cycleStartTime) / 60000.0 : 0
    if (enableMinRuntime && state.currentAction in ["cooling", "heating"] && runMins < getMinRunTime()) {
        return 
    }
    
    // 60-second blindspot for incoming setpoint echoes
    if (state.lastCommandTime && (now() - state.lastCommandTime) < 60000) return 
    
    def newVal = evt.value.toBigDecimal()
    def isManual = false
    
    if (evt.name == "coolingSetpoint" && state.expectedCool != null) {
        if (Math.abs((newVal - state.expectedCool.toBigDecimal()).doubleValue()) > 1.0) isManual = true
    }
    if (evt.name == "heatingSetpoint" && state.expectedHeat != null) {
        if (Math.abs((newVal - state.expectedHeat.toBigDecimal()).doubleValue()) > 1.0) isManual = true
    }
    
    if (isManual && (!state.manualHoldEnds || now() > state.manualHoldEnds)) { 
        state.manualHoldEnds = now() + 7200000 
        state.preBufferCool = null
        state.preBufferHeat = null
        logAction("MANUAL OVERRIDE: Physical thermostat changed to ${newVal}°. Automation suspended for 2 Hours.") 
        evaluateSystem() 
    } else if (isManual) {
        state.manualHoldEnds = now() + 7200000 
        state.preBufferCool = null
        state.preBufferHeat = null
        logAction("MANUAL OVERRIDE: Setpoint adjusted again. 2-Hour hold timer reset.")
        evaluateSystem()
    }
}

boolean checkComfortOvershootAbort() {
    if (!state.isBuffering) return false
    def maxOver = (maxComfortOvershoot != null ? maxComfortOvershoot.toBigDecimal() : 2.0G)
    def currentAvg = getAverageTemp()
    def baseCool = getBaseCoolTarget()
    def baseHeat = getBaseHeatTarget()

    if (state.currentAction == "cooling" && currentAvg > 0 && currentAvg <= (baseCool - maxOver)) {
        logAction("COMFORT SAFETY CUTOFF: Room average (${currentAvg}°) dropped ${maxOver}° below target (${baseCool}°). Releasing Compressor Buffer early to prevent over-cooling.")
        unschedule("releaseBuffer")
        releaseBuffer()
        return true
    } else if (state.currentAction == "heating" && currentAvg > 0 && currentAvg >= (baseHeat + maxOver)) {
        logAction("COMFORT SAFETY CUTOFF: Room average (${currentAvg}°) rose ${maxOver}° above target (${baseHeat}°). Releasing Compressor Buffer early to prevent over-heating.")
        unschedule("releaseBuffer")
        releaseBuffer()
        return true
    }
    return false
}

def evaluateSystem() {
    if (!thermostat) return
    
    // Check Service Switch & Safety Overrides first
    if (serviceSwitch && serviceSwitch.currentValue("switch") == "on") {
        if (thermostat.currentValue("thermostatMode") != "off") {
            state.lastCommandTime = now()
            thermostat.setThermostatMode("off")
        }
        return
    }
    
    if (state.fireEmergency || state.deltaTShutoff) {
        if (thermostat.currentValue("thermostatMode") != "off") {
            state.lastCommandTime = now()
            thermostat.setThermostatMode("off")
        }
        return
    }
    if (state.rebootLockoutEnds && now() < state.rebootLockoutEnds) return
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    if (state.windowOpenHold) return 
    
    // --- STUCK BUFFER & COMFORT OVERSHOOT FAILSAFE CHECK ---
    if (state.isBuffering && state.cycleStartTime) {
        if (checkComfortOvershootAbort()) return
        if ((now() - state.cycleStartTime) / 60000.0 >= getMinRunTime()) {
             logAction("CRITICAL: Buffer time limit exceeded but system failed to release. Force clearing locks.")
             state.isBuffering = false
             state.preBufferCool = null
             state.preBufferHeat = null
             unschedule("releaseBuffer")
        } else {
             return 
        }
    } else if (state.isBuffering) {
        state.isBuffering = false
    }
    
    def evalMode = location.mode
    def modeHoldMsg = ""
    
    def isMinRunActive = false
    if (enableMinRuntime && state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
        def runMinsCheck = (now() - state.cycleStartTime) / 60000.0
        if (runMinsCheck < getMinRunTime()) {
            isMinRunActive = true
        }
    }

    if (isMinRunActive && delayModeChangeForMinRun) {
        if (state.cycleStartMode && state.cycleStartMode != location.mode) {
            evalMode = state.cycleStartMode
            modeHoldMsg = " [Mode Change Delayed: Finishing Compressor Protection]"
            if (!state.modeDelayLogged) {
                logAction("Mode changed to ${location.mode}, but Compressor Protection is active. Simulating ${state.cycleStartMode} for the remaining run time.")
                state.modeDelayLogged = true
            }
        }
    }

    if (allowedModes && !(allowedModes as List).contains(evalMode)) return
    
    def isAway = awayModes ? (awayModes as List).contains(evalMode) : false
    def isNight = nightModes ? (nightModes as List).contains(evalMode) : false
    def isSmartModeAllowed = !averagingModes || (averagingModes as List).contains(evalMode)
    
    if (isAway && state.awayStartTime == null) {
        state.awayStartTime = now()
    } else if (!isAway) {
        state.awayStartTime = null
    }

    BigDecimal targetCool = getBaseCoolTarget(evalMode)
    BigDecimal targetHeat = getBaseHeatTarget(evalMode)
    def syncMessage = ""

    if (isAway && enableDeepAway) {
        def delayMs = (deepAwayDelayHours ?: 4) * 3600000L
        if (state.awayStartTime && (now() - state.awayStartTime) >= delayMs) {
            syncMessage = " [Deep Setback Active: >${deepAwayDelayHours ?: 4} Hrs]"
        }
    }
    
    BigDecimal baseCool = targetCool
    BigDecimal baseHeat = targetHeat

    // --- EXTENDED ARRIVAL COOLING ENFORCEMENT ---
    def isEAActive = state.extendedArrivalEnds && now() < state.extendedArrivalEnds
    if (isEAActive) {
        def currentAvg = getAverageTemp()
        def hardFloor = (eaHardFloor != null ? eaHardFloor : 70.0).toBigDecimal()
        
        if (currentAvg > 0 && currentAvg <= hardFloor) {
            logAction("Extended Arrival Cooling ABORTED: Hard floor of ${hardFloor}° reached.")
            state.extendedArrivalEnds = null
            isEAActive = false
        } else {
            targetCool = hardFloor 
            syncMessage += " [Extended Arrival Active: Cooling Thermal Mass]"
        }
    } else if (state.extendedArrivalEnds && now() >= state.extendedArrivalEnds) {
         state.extendedArrivalEnds = null 
         logAction("Extended Arrival Cooling successfully completed its runtime.")
    }

    BigDecimal baseSwapDB = enableAutoSwap ? (autoSwapDeadband != null ? autoSwapDeadband.toBigDecimal() : 1.0G) : 1.0G
    BigDecimal safeSwapDB = baseSwapDB
    BigDecimal hardwareDeadband = (hardwareDeadbandMin != null ? hardwareDeadbandMin : 3.0).toBigDecimal()
    
    def currentLocalTemp = thermostat.currentValue("temperature")?.toBigDecimal()
    def isManualHoldActive = state.manualHoldEnds && now() < state.manualHoldEnds
    def auxGlided = false

    if (isManualHoldActive) {
        targetCool = thermostat.currentValue("coolingSetpoint")?.toBigDecimal() ?: baseCool
        targetHeat = thermostat.currentValue("heatingSetpoint")?.toBigDecimal() ?: baseHeat
        syncMessage = " [Manual Hold Active - Resumes in ${Math.round((state.manualHoldEnds - now()) / 60000)} mins]"
    } else {
        // --- NORMAL AUTOMATION ENGINE ---
        if (currentLocalTemp != null) {
            if (state.alignmentLockout == "cooling" && currentLocalTemp >= (state.alignmentLockoutTarget ?: targetCool)) {
                state.alignmentLockout = null
                logAction("Local temperature recovered to ${state.alignmentLockoutTarget}°. Dynamic Setpoint Alignment re-enabled.")
            } else if (state.alignmentLockout == "heating" && currentLocalTemp <= (state.alignmentLockoutTarget ?: targetHeat)) {
                state.alignmentLockout = null
                logAction("Local temperature recovered to ${state.alignmentLockoutTarget}°. Dynamic Setpoint Alignment re-enabled.")
            }
        }
        
        // Stage 1: Smart Hysteresis Evaluator
        def isHysAllowed = enableHysteresis && (isSmartModeAllowed || globalHysteresis)
        
        if (isHysAllowed) {
            def drift = hysteresisDrift != null ? hysteresisDrift.toBigDecimal() : 1.0G
            if (safeSwapDB <= drift) { safeSwapDB = drift + 0.5G }
        }
        
        def yoyoMins = yoyoCooldownMins != null ? yoyoCooldownMins.toInteger() : 15
        def isYoYoCooldown = yoyoMins > 0 && state.yoyoCooldownEnds && now() < state.yoyoCooldownEnds.toLong() && state.currentAction == "idle"
   
        // Stage 1: Hysteresis & Deadband Active State
        if (isHysAllowed && thermostat.currentValue("temperature") != null) {
            def currentAvg = getAverageTemp()
            def drift = hysteresisDrift != null ? hysteresisDrift.toBigDecimal() : 1.0G
            def recovery = hysteresisRecovery != null ? hysteresisRecovery.toBigDecimal() : 0.5G
            
            // Keep Hysteresis aligned with physical compressor state so it cannot float to idle mid-run
            if (state.currentAction == "cooling" && state.activeHysteresis != "cooling") {
                state.activeHysteresis = "cooling"
            } else if (state.currentAction == "heating" && state.activeHysteresis != "heating") {
                state.activeHysteresis = "heating"
            }

            if (state.activeHysteresis == null || state.activeHysteresis == "idle") {
                if (currentAvg >= (baseCool + drift)) {
                    state.activeHysteresis = "cooling"
                    logAction("Stage 1 Hysteresis: Temp drifted to ${currentAvg}° (+${drift}° limit). Initiating Cooling Recovery to ${baseCool + recovery}°.")
                } else if (currentAvg <= (baseHeat - drift)) {
                    state.activeHysteresis = "heating"
                    logAction("Stage 1 Hysteresis: Temp drifted to ${currentAvg}° (-${drift}° limit). Initiating Heating Recovery to ${baseHeat - recovery}°.")
                }
            } else if (state.activeHysteresis == "cooling") {
                if (currentAvg <= (baseCool + recovery) && !isMinRunActive) {
                    state.activeHysteresis = "idle"
                    logAction("Stage 1 Hysteresis: Cooled to ${currentAvg}° (Within ${recovery}° of target). Satisfied and entering Idle.")
                }
            } else if (state.activeHysteresis == "heating") {
                if (currentAvg >= (baseHeat - recovery) && !isMinRunActive) {
                    state.activeHysteresis = "idle"
                    logAction("Stage 1 Hysteresis: Heated to ${currentAvg}° (Within ${recovery}° of target). Satisfied and entering Idle.")
                }
            }
        } else {
            state.activeHysteresis = "idle"
        }

        // Dynamic Setpoint Alignment 
        def isAlignmentActive = (enableAverageSync && isSmartModeAllowed)
        def runSetpointLogic = isAlignmentActive || isHysAllowed

        if (isYoYoCooldown && thermostat.currentValue("temperature") != null) {
            def tstatTemp = thermostat.currentValue("temperature").toBigDecimal()
            def yoyoRemainingMinsRaw = (state.yoyoCooldownEnds.toLong() - now()) / 60000.0
            
            // Thermal Evasion Glide - Maintain aggressive gap to prevent premature activation
            def evasionBuffer = setpointBuffer != null ? Math.max(3.0d, setpointBuffer.toDouble()).toBigDecimal() : 3.0G
            
            if (state.lastCycleMode == "heating" || state.lastCycleMode == "auxHeating") {
                def enforcedHeat = (tstatTemp - evasionBuffer).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                if (enforcedHeat < targetHeat) {
                    targetHeat = enforcedHeat
                }
                targetCool = (targetHeat + hardwareDeadband).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                syncMessage += " [Anti-Yo-Yo: Evasion Glide Active (Heat locked at ${targetHeat}° for ${Math.round(yoyoRemainingMinsRaw)}m)]"
            } else {
                def enforcedCool = (tstatTemp + evasionBuffer).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                if (enforcedCool > targetCool) {
                    targetCool = enforcedCool
                }
                targetHeat = (targetCool - hardwareDeadband).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                syncMessage += " [Anti-Yo-Yo: Evasion Glide Active (Cool locked at ${targetCool}° for ${Math.round(yoyoRemainingMinsRaw)}m)]"
            }
        } else if (runSetpointLogic && thermostat.currentValue("temperature") != null) {
            if (state.alignmentLockout) {
                syncMessage += " [Alignment Suspended: Awaiting Temp Recovery]"
            } else {
                def tstatTemp = thermostat.currentValue("temperature").toBigDecimal()
                def currentAvg = getAverageTemp()
                
                if (enableHysteresis && state.activeHysteresis == "idle" && !isMinRunActive) {
                    def halfDB = Math.max(1.5d, (hardwareDeadband / 2.0G).doubleValue()).toBigDecimal()
                    targetCool = (tstatTemp + halfDB).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    targetHeat = (tstatTemp - halfDB).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    syncMessage += " [Stage 1: Floating in Deadband]"
                } else {
                    def offset = 0.0G
                    if (isAlignmentActive) {
                        offset = (currentAvg - tstatTemp)
                    }
                    
                    def maxShift = (maxSyncOffset != null ? maxSyncOffset : 3.0).toBigDecimal()
                    if (offset > maxShift) offset = maxShift
                    if (offset < -maxShift) offset = -maxShift
                
                    def calcCool = (targetCool - offset).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    def calcHeat = (targetHeat - offset).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
            
                    if (!enableHysteresis) {
                        def coolSnapped = false
                        if (calcCool < baseCool && currentAvg <= baseCool && !isMinRunActive) coolSnapped = true
                        def heatSnapped = false
                        if (calcHeat > baseHeat && currentAvg >= baseHeat && !isMinRunActive) heatSnapped = true
                    
                        if (coolSnapped || heatSnapped) {
                            calcCool = baseCool
                            calcHeat = baseHeat
                            syncMessage += " [Alignment Satisfied: System Idle, Snapped to Base]"
                        } else if (offset != 0.0G) {
                            syncMessage += " [Alignment Active: Shifted by ${String.format('%.1f', -offset)}°]"
                        }
                    } else {
                        def hysMsg = state.activeHysteresis == "cooling" ? "[Stage 1: Active Cool Recovery]" : "[Stage 1: Active Heat Recovery]"
                        def shiftMsg = offset != 0.0G ? " (Shifted by ${String.format('%.1f', -offset)}°)" : ""
                        syncMessage += " ${hysMsg}${shiftMsg}"
                    }
                    
                    targetCool = calcCool
                    targetHeat = calcHeat
                    
                    // ANTI-YOYO CLAMP
                    if (targetCool <= (baseHeat + safeSwapDB)) {
                        targetCool = (baseHeat + safeSwapDB + 1.0G).setScale(0, BigDecimal.ROUND_HALF_UP)
                        targetHeat = (targetCool - hardwareDeadband).setScale(0, BigDecimal.ROUND_HALF_UP)
                        syncMessage += " [Clamped: Hit Heating Floor]"
                    }
                    else if (targetHeat >= (baseCool - safeSwapDB)) {
                        targetHeat = (baseCool - safeSwapDB - 1.0G).setScale(0, BigDecimal.ROUND_HALF_UP)
                        targetCool = (targetHeat + hardwareDeadband).setScale(0, BigDecimal.ROUND_HALF_UP)
                        syncMessage += " [Clamped: Hit Cooling Ceiling]"
                    }
                }
            }
        } else if (enableAverageSync && !isSmartModeAllowed && !isHysAllowed) {
            state.alignmentLockout = null
        }
        
        // AUX HEAT SUPPRESSION (GLIDING)
        if (enableAuxSuppression && currentLocalTemp != null) {
            def stepLimit = (maxHeatStep != null ? maxHeatStep : 1.5).toBigDecimal()
            if (targetHeat > (currentLocalTemp + stepLimit)) {
                targetHeat = (currentLocalTemp + stepLimit).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                auxGlided = true
                syncMessage += " [Aux Suppressed: Heat target glided to ${targetHeat}°]"
            }
        }
    }
    // --- END NORMAL AUTOMATION ENGINE ---

    // --- OUTDOOR TEMPERATURE COMPRESSOR LOCKOUT ---
    def isCoolLockedOutByOutdoor = false
    def isHeatLockedOutByOutdoor = false
    if (enableAmbientLockout && outdoorTempSensor && !isManualHoldActive) {
        def outTempVal = outdoorTempSensor.currentValue("temperature")?.toBigDecimal()
        if (outTempVal != null) {
            def minCoolOut = minOutdoorCoolTemp != null ? minOutdoorCoolTemp.toBigDecimal() : 55.0G
            def maxHeatOut = maxOutdoorHeatTemp != null ? maxOutdoorHeatTemp.toBigDecimal() : 68.0G
            def localRef = currentLocalTemp ?: 70.0G

            if (outTempVal < minCoolOut && state.currentAction != "cooling") {
                isCoolLockedOutByOutdoor = true
                def safeCoolLock = (localRef + 4.0G).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                if (targetCool < safeCoolLock) targetCool = safeCoolLock
                syncMessage += " [Outdoor Lockout: AC blocked below ${minCoolOut}°F (OSA: ${outTempVal}°F)]"
            }
            if (outTempVal > maxHeatOut && state.currentAction != "heating") {
                isHeatLockedOutByOutdoor = true
                def safeHeatLock = (localRef - 4.0G).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                if (targetHeat > safeHeatLock) targetHeat = safeHeatLock
                syncMessage += " [Outdoor Lockout: Heat blocked above ${maxHeatOut}°F (OSA: ${outTempVal}°F)]"
            }
        }
    }

    // --- COMPRESSOR RUN TIME PROTECTION (Always Runs, including Night Mode) ---
    def allowNightMinRun = (protectNightMinRun != false)
    if (enableMinRuntime && (!isNight || allowNightMinRun) && state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
        def runMins = (now() - state.cycleStartTime) / 60000.0
        if (runMins < getMinRunTime()) {
            
            def localTemp = thermostat.currentValue("temperature")?.toBigDecimal() ?: targetCool
            def buffer = setpointBuffer != null ? setpointBuffer.toBigDecimal() : 3.0G
            def maxOver = maxComfortOvershoot != null ? maxComfortOvershoot.toBigDecimal() : 2.0G
            def currentAvg = getAverageTemp()
            
            if (state.currentAction == "cooling") {
                if (currentAvg > 0 && currentAvg <= (baseCool - maxOver)) {
                    syncMessage += " [Comfort Overshoot Limit (${baseCool - maxOver}°) Reached: Allowing cycle to end]"
                } else {
                    def startCool = state.cycleStartCoolTarget != null ? state.cycleStartCoolTarget.toBigDecimal() : null
                    if (startCool != null && targetCool > startCool) {
                        targetCool = startCool
                        syncMessage += " [Min Run Strict Lock: Held Target at ${targetCool}°]"
                    } else {
                        def currentSet = thermostat.currentValue("coolingSetpoint")?.toBigDecimal()
                        if (currentSet != null && targetCool > currentSet) {
                            targetCool = currentSet
                            syncMessage += " [Min Run: Preventing Target Rise]"
                        }
                    }
                    
                    // Thermal Evasion Glide - Anchor floor off the lower of baseCool or localTemp so cold nights don't short-cycle
                    def safeRunTarget = (localTemp - 2.0G).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    if (targetCool >= localTemp || targetCool > safeRunTarget) {
                        targetCool = safeRunTarget
                        def anchorCool = Math.min(baseCool.doubleValue(), localTemp.doubleValue()).toBigDecimal()
                        def minAllowed = (anchorCool - buffer).setScale(0, BigDecimal.ROUND_HALF_UP)
                        
                        if (targetCool < minAllowed) {
                            targetCool = minAllowed
                            if (targetCool >= localTemp) {
                                 syncMessage += " [CRITICAL: Cannot protect compressor. Max buffer limit reached.]"
                            }
                            
                            if (enableAverageSync && isSmartModeAllowed && !state.alignmentLockout && !isManualHoldActive) {
                                state.alignmentLockout = "cooling"
                                state.alignmentLockoutTarget = baseCool
                                syncMessage += " [CRITICAL: Max Buffer Hit. Alignment ABORTED until temp recovers]"
                            } else {
                                syncMessage += " [Compressor Protection: Clamped to Max Buffer (-${buffer}°)]"
                            }
                        } else {
                            syncMessage += " [Compressor Protection: Evasion Glide forced below local temp (${targetCool}°) to maintain run]"
                        }
                    }
                }
            }
            else if (state.currentAction == "heating") {
                if (currentAvg > 0 && currentAvg >= (baseHeat + maxOver)) {
                    syncMessage += " [Comfort Overshoot Limit (${baseHeat + maxOver}°) Reached: Allowing cycle to end]"
                } else {
                    def startHeat = state.cycleStartHeatTarget != null ? state.cycleStartHeatTarget.toBigDecimal() : null
                    if (startHeat != null && targetHeat < startHeat && !auxGlided) {
                        targetHeat = startHeat
                        syncMessage += " [Min Run Strict Lock: Held Target at ${targetHeat}°]"
                    } else if (!auxGlided) {
                        def currentSet = thermostat.currentValue("heatingSetpoint")?.toBigDecimal()
                        if (currentSet != null && targetHeat < currentSet) {
                            targetHeat = currentSet
                            syncMessage += " [Min Run: Preventing Target Drop]"
                        }
                    }
                    
                    // Thermal Evasion Glide
                    def safeRunTarget = (localTemp + 2.0G).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    if ((targetHeat <= localTemp || targetHeat < safeRunTarget) && !auxGlided) {
                        targetHeat = safeRunTarget
                        def anchorHeat = Math.max(baseHeat.doubleValue(), localTemp.doubleValue()).toBigDecimal()
                        def maxAllowed = (anchorHeat + buffer).setScale(0, BigDecimal.ROUND_HALF_UP)
     
                        if (targetHeat > maxAllowed) {
                            targetHeat = maxAllowed
                            if (targetHeat <= localTemp) {
                                 syncMessage += " [CRITICAL: Cannot protect compressor. Max buffer limit reached.]"
                            }
                            
                            if (enableAverageSync && isSmartModeAllowed && !state.alignmentLockout && !isManualHoldActive) {
                                state.alignmentLockout = "heating"
                                state.alignmentLockoutTarget = baseHeat
                                syncMessage += " [CRITICAL: Max Buffer Hit. Alignment ABORTED until temp recovers]"
                            } else {
                                syncMessage += " [Compressor Protection: Clamped to Max Buffer (+${buffer}°)]"
                            }
                        } else {
                            syncMessage += " [Compressor Protection: Evasion Glide forced above local temp (${targetHeat}°) to maintain run]"
                        }
                    }
                }
            }

            if (enableAutoSwap && !isManualHoldActive) {
                def minCoolFloor = baseHeat + safeSwapDB + 1.5G 
                def maxHeatCeiling = baseCool - safeSwapDB - 1.5G
                
                if (state.currentAction == "cooling" && targetCool < minCoolFloor) {
                    targetCool = minCoolFloor.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    syncMessage += " [Compressor Protection: Protected against Heat Swap]"
                }
                else if (state.currentAction == "heating" && targetHeat > maxHeatCeiling) {
                    targetHeat = maxHeatCeiling.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    syncMessage += " [Compressor Protection: Protected against Cool Swap]"
                }
            }
        } else if (state.modeDelayLogged) {
            logAction("Minimum Runtime Satisfied. Mode Change Setpoint Block has been lifted.")
            state.modeDelayLogged = false
        }
    }
    
    // --- DEADLOCK FAILSAFE ---
    if (enableDeadlock && !isManualHoldActive) {
        def deadlockLimitMs = (deadlockTimeoutMins ?: 120) * 60000L
        
        def isIntentionalFloat = (enableHysteresis && state.activeHysteresis == "idle") || isCoolLockedOutByOutdoor || isHeatLockedOutByOutdoor
        def checkDeadlock = (targetCool != baseCool || targetHeat != baseHeat) && !isIntentionalFloat
        
        if (checkDeadlock) {
            if (!state.deviationStartTime) {
                state.deviationStartTime = now()
            } else if ((now() - state.deviationStartTime) > deadlockLimitMs) {
                
                def isRunning = state.currentAction in ["cooling", "heating"] && state.cycleStartTime != null
                def runMins = isRunning ? (now() - state.cycleStartTime) / 60000.0 : 999.0
                
                if (isRunning && runMins < getMinRunTime()) {
                    syncMessage += " [Deadlock Delayed: Waiting for Min Run Time]"
                } else {
                    logAction("CRITICAL FAILSAFE: Setpoints have been artificially altered from the base mode target (Cool: ${baseCool}°, Heat: ${baseHeat}°) for over ${deadlockTimeoutMins ?: 120} minutes. Releasing all locks.")
                    state.deviationStartTime = null
                    if (!isRunning) state.cycleStartTime = null
                    state.isBuffering = false
                    state.alignmentLockout = null
                    state.activeHysteresis = "idle"
                    state.yoyoCooldownEnds = null
                    unschedule("releaseBuffer")
                    
                    targetCool = baseCool
                    targetHeat = baseHeat
                    syncMessage += " [Failsafe: ${deadlockTimeoutMins ?: 120}-Min Deadlock Cleared]"
                }
            }
        } else {
            state.deviationStartTime = null
        }
    } else {
        state.deviationStartTime = null
    }
    
    // --- UNIVERSAL DEADBAND ENFORCER (Final Pass) ---
    int scalePrecision = auxGlided ? 1 : 0
    if ((targetCool - targetHeat) < hardwareDeadband) {
        if (state.currentAction == "cooling" || isHeatLockedOutByOutdoor) {
            targetHeat = (targetCool - hardwareDeadband).toBigDecimal().setScale(scalePrecision, BigDecimal.ROUND_HALF_UP)
        } else if (state.currentAction == "heating" || isCoolLockedOutByOutdoor || auxGlided) {
            targetCool = (targetHeat + hardwareDeadband).toBigDecimal().setScale(scalePrecision, BigDecimal.ROUND_HALF_UP)
        } else {
            targetHeat = (targetCool - hardwareDeadband).toBigDecimal().setScale(scalePrecision, BigDecimal.ROUND_HALF_UP)
        }
        if (!syncMessage.contains("Deadband")) syncMessage += " [Final Deadband Enforced]"
    }
    
    pushSetpointsSafely(targetCool, targetHeat, "BMS Command -> Pushing Setpoints to Thermostat: COOL ${targetCool}° | HEAT ${targetHeat}°${syncMessage}${modeHoldMsg}")

    def tMode = thermostat.currentValue("thermostatMode")?.toLowerCase()
    def isServiceMode = serviceSwitch && serviceSwitch.currentValue("switch") == "on"
    
    // --- AUTO-RECOVERY FROM OFF ---
    if (tMode == "off" && !state.fireEmergency && !state.windowOpenHold && !state.deltaTShutoff && !isServiceMode && !isManualHoldActive) {
        def currentAvg = getAverageTemp()
        def restoreMode = (Math.abs((currentAvg - targetCool).doubleValue()) < Math.abs((currentAvg - targetHeat).doubleValue())) ? "cool" : "heat"
        if (currentAvg >= targetCool && !isCoolLockedOutByOutdoor) restoreMode = "cool"
        else if (currentAvg <= targetHeat && !isHeatLockedOutByOutdoor) restoreMode = "heat"
        else if (isCoolLockedOutByOutdoor) restoreMode = "heat"
        else if (isHeatLockedOutByOutdoor) restoreMode = "cool"
        
        logAction("BMS Recovery -> Thermostat is OFF but no safety defeats are active. Auto-recovering to ${restoreMode.toUpperCase()} mode.")
        state.lastCommandTime = now()
        thermostat.setThermostatMode(restoreMode)
        tMode = restoreMode
    }
    
    def yoyoCheckMins = yoyoCooldownMins != null ? yoyoCooldownMins.toInteger() : 15
    def isYoYoDelayActive = yoyoCheckMins > 0 && state.yoyoCooldownEnds && now() < state.yoyoCooldownEnds.toLong() && state.currentAction == "idle"
    
    if (enableAutoSwap && !isManualHoldActive && !isYoYoDelayActive && !isMinRunActive) {
        def currentAvg = getAverageTemp()
   
        if (tMode == "heat" || tMode == "cool" || tMode == "auto") {
            if (currentAvg >= (targetCool + safeSwapDB) && tMode != "cool" && !isCoolLockedOutByOutdoor) {
                logAction("BMS Command -> Auto-Swap triggered. Switching thermostat to COOL mode. (Temp: ${currentAvg}°, Target: ${targetCool}°, Safe DB: ${safeSwapDB}°)")
                state.lastCommandTime = now()
                thermostat.setThermostatMode("cool")
            } else if (currentAvg <= (targetHeat - safeSwapDB) && tMode != "heat" && !isHeatLockedOutByOutdoor) {
                logAction("BMS Command -> Auto-Swap triggered. Switching thermostat to HEAT mode. (Temp: ${currentAvg}°, Target: ${targetHeat}°, Safe DB: ${safeSwapDB}°)")
                state.lastCommandTime = now()
                thermostat.setThermostatMode("heat")
            }
        }
    }
}

def endThermalCoasting() {
    state.coastingEnds = null
    
    if (thermostat) {
        logAction("Thermal Coasting complete. Restoring Fan to Auto.")
        thermostat.setThermostatFanMode("auto")
    }
}

def compressorWatchdog() {
    if (state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
        if (checkComfortOvershootAbort()) return
        def runMins = (now() - state.cycleStartTime) / 60000.0
        if (runMins < getMinRunTime()) {
            evaluateSystem() 
            runIn(60, "compressorWatchdog")
        } else {
            evaluateSystem() 
        }
    }
}

def contactHandler(evt) { 
    def anyOpen = contactSensors ? contactSensors.any { it.currentValue("contact") == "open" } : false
    def delayMins = contactDelay != null ? contactDelay.toInteger() : 3
    def delaySecs = delayMins * 60
    
    if (anyOpen) {
        if (state.windowRecoveryTime) {
            state.windowRecoveryTime = null
            unschedule("releaseWindowDefeat")
            logAction("Perimeter sensor opened during recovery countdown. Aborting recovery and maintaining HVAC lock.")
        }
        
        if (!state.windowOpenHold) { 
            if (!state.windowDefeatTime || now() > state.windowDefeatTime) {
                state.windowDefeatTime = now() + (delaySecs * 1000L)
                runIn(Math.max(1, delaySecs), "executeWindowDefeat") 
                logAction("Perimeter sensor opened. Starting ${delayMins}-minute HVAC shutdown timer.")
            }
        }
    } else { 
        if (state.windowDefeatTime) {
            state.windowDefeatTime = null
            unschedule("executeWindowDefeat")
            logAction("Perimeter sensors closed. HVAC shutdown timer cancelled.")
        }
        
        if (state.windowOpenHold) {
            if (!state.windowRecoveryTime || now() > state.windowRecoveryTime) {
                state.windowRecoveryTime = now() + (delaySecs * 1000L)
                runIn(Math.max(1, delaySecs), "releaseWindowDefeat")
                logAction("All perimeter sensors closed. Starting ${delayMins}-minute HVAC recovery timer before restoring normal operation.")
            }
        }
    }
    requestEvaluation()
}

def executeWindowDefeat() { 
    if (enableMinRuntime && state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
        def runMins = (now() - state.cycleStartTime) / 60000.0
        def minRun = getMinRunTime()
        if (runMins < minRun) {
            def remainingMins = minRun - runMins
            state.windowDefeatTime = now() + Math.round(remainingMins * 60000)
            logAction("Perimeter Defeat Delayed: Compressor protection is active. Waiting ${String.format('%.1f', remainingMins)} more minutes before shutting down HVAC.")
            runIn(Math.max(1, (remainingMins * 60).toInteger()), "executeWindowDefeat")
            return
        }
    }

    state.windowOpenHold = true 
    state.windowDefeatTime = null
    state.windowRecoveryTime = null
    if (state.isBuffering) { state.isBuffering = false; unschedule("releaseBuffer") }
    
    def tMode = thermostat.currentValue("thermostatMode")
    if (tMode && tMode != "off") state.preDefeatMode = tMode
    
    logAction("BMS Command -> Safety Defeat Active. Turning HVAC OFF.")
    state.lastCommandTime = now()
    thermostat.off() 
}

def releaseWindowDefeat() {
    logAction("Perimeter recovery timer complete. Releasing HVAC Safety Defeat.")
    state.windowOpenHold = false
    state.windowRecoveryTime = null
    
    if (state.preDefeatMode) {
        logAction("BMS Command -> Restoring thermostat mode to ${state.preDefeatMode.toUpperCase()}.")
        state.lastCommandTime = now()
        thermostat.setThermostatMode(state.preDefeatMode)
        state.preDefeatMode = null
    } else {
        def currentAvg = getAverageTemp()
        def baseCool = getBaseCoolTarget()
        def baseHeat = getBaseHeatTarget()
        def fallbackMode = (Math.abs((currentAvg - baseCool).doubleValue()) <= Math.abs((currentAvg - baseHeat).doubleValue())) ? "cool" : "heat"
        logAction("BMS Command -> No pre-defeat mode saved. Restoring thermostat mode to ${fallbackMode.toUpperCase()}.")
        state.lastCommandTime = now()
        thermostat.setThermostatMode(fallbackMode) 
    }
    
    evaluateSystem()
}

def hvacStateHandler(evt) {
    def stateVal = evt.value?.toLowerCase() ?: ""
    if (stateVal == "pending cool" || stateVal == "pending heat" || stateVal == "fan only" || stateVal == "vent economizer") {
        logInfo("Thermostat reported transitional operating state '${stateVal}'. Holding current cycle state.")
        return
    }

    def incomingAction = (stateVal.contains("aux") || stateVal.contains("emergency")) ? "auxHeating" : stateVal
    
    if (state.currentAction == incomingAction && state.cycleStartTime != null) {
        logInfo("Ignored duplicate '${stateVal}' event to protect active cycle timer.")
        return 
    }

    if (stateVal == "cooling" || stateVal == "heating" || stateVal.contains("aux") || stateVal.contains("emergency")) {
        if (state.coastingEnds) {
            state.coastingEnds = null
            unschedule("endThermalCoasting")
            thermostat.setThermostatFanMode("auto")
        }
        
        if (state.idleStartTime) {
            def idleMins = (now() - state.idleStartTime) / 60000.0
            trackIdleTime(idleMins)
            state.idleStartTime = null
        }
        
        state.cycleStartTime = now()
        state.cycleStartMode = location.mode
        state.modeDelayLogged = false
        state.startTemp = thermostat.currentValue("temperature")
        state.currentCycleMaxDeltaT = null 
        
        state.cycleStartCoolTarget = thermostat.currentValue("coolingSetpoint")
        state.cycleStartHeatTarget = thermostat.currentValue("heatingSetpoint")
        
        if (stateVal.contains("aux") || stateVal.contains("emergency")) { 
            state.currentAction = "auxHeating" 
        } else { 
            state.currentAction = stateVal 
            state.activeHysteresis = stateVal
        }
        state.isBuffering = false
        state.yoyoCooldownEnds = null 
        
        def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
        def allowNightMinRun = (protectNightMinRun != false)
        
        if (enableMinRuntime && (!isNight || allowNightMinRun) && state.currentAction in ["cooling", "heating"]) {
            
            runIn(60, "compressorWatchdog")
            
            def activeSetpoint = (state.currentAction == "cooling") ? thermostat.currentValue("coolingSetpoint") : thermostat.currentValue("heatingSetpoint")
            def threshold = shortCycleThreshold != null ? shortCycleThreshold.toBigDecimal() : 1.0G
            
            if (activeSetpoint != null && state.startTemp != null && Math.abs((state.startTemp.toBigDecimal() - activeSetpoint.toBigDecimal()).doubleValue()) <= threshold.doubleValue()) {
                logAction("BMS Command -> Compressor Protection Engaged! HVAC started within ${threshold}° of setpoint. Forcing minimum run time.")
                engageBuffer(0)
            } else {
                evaluateSystem()
            }
        }
        
        if (enableDeltaT && returnSensor && dischargeSensor) runIn(Math.max(1, ((deltaTCheckDelay ?: 30) * 60).toInteger()), "checkDeltaT")
    } else if (stateVal == "idle") {
        unschedule("checkDeltaT")
        unschedule("compressorWatchdog")
        
        state.cycleStartCoolTarget = null
        state.cycleStartHeatTarget = null
        
        def runMinutes = state.cycleStartTime ? (now() - state.cycleStartTime) / 60000.0 : 0.0
        
        if (state.currentAction in ["cooling", "heating", "auxHeating"]) {
            state.idleStartTime = now()
        }
        
        def yoyoMins = yoyoCooldownMins != null ? yoyoCooldownMins.toInteger() : 15
        if (state.currentAction == "cooling" || state.currentAction == "heating") {
            
            if (enableThermalCoasting && runMinutes >= 5.0) {
                def coastMins = coastingDurationMins != null ? coastingDurationMins.toInteger() : 3
                if (coastMins > 0) {
                    logAction("Thermal Coasting: Compressor cycle complete (${String.format('%.1f', runMinutes)}m). Forcing blower ON for ${coastMins} minutes to scavenge coil temperatures.")
                    thermostat.setThermostatFanMode("on")
                    runIn(Math.max(1, coastMins * 60), "endThermalCoasting")
                    state.coastingEnds = now() + (coastMins * 60000L)
                }
            }
            
            if (yoyoMins > 0) {
                state.yoyoCooldownEnds = now() + (yoyoMins * 60000L)
                logAction("${state.currentAction.capitalize()} cycle complete. Starting ${yoyoMins}-Minute Anti-Yo-Yo Cooldown.")
            }
        }

        if (state.isBuffering) releaseBuffer()
        
        if (state.cycleStartTime) {
            if (enableFilterTracker) processFilterWear(runMinutes)
            if (state.currentAction) trackEnergyStats(state.currentAction, runMinutes)
            
            if (state.currentAction && state.currentAction != "idle") {
                state.lastCycleMode = state.currentAction
                trackRecentCycle(state.currentAction, runMinutes, state.currentCycleMaxDeltaT)
            
                if (enableMinRuntime && state.currentAction in ["cooling", "heating"]) {
                    
                    if (runMinutes < getMinRunTime()) {
                        
                        logAction("WARNING: Short-cycle detected! Compressor ran for ${String.format('%.1f', runMinutes)} mins (Goal: ${getMinRunTime()} mins).")
                        if (enableShortCycleNotify && notifyDevices) {
                            notifyDevices.deviceNotification("HVAC Alert: Short-cycle detected. ${state.currentAction.capitalize()} ran for only ${String.format('%.1f', runMinutes)} minutes.")
                        }

                        if (enableTripFailsafe) {
                            def maxCycles = tripCycleCount != null ? tripCycleCount.toInteger() : 3
                            def windowMs = (tripWindowMins != null ? tripWindowMins.toLong() : 60L) * 60000L
                            
                            def scLog = state.shortCycleLog ?: []
                            scLog.add(now())
                            scLog = scLog.findAll { (now() - (it as Long)) <= windowMs }
                            state.shortCycleLog = scLog
   
                            if (scLog.size() >= maxCycles) {
                                logAction("CRITICAL FAILSAFE: ${maxCycles} Short-Cycles detected within ${tripWindowMins ?: 60} minutes! Hardware may be tripping a limit switch. Releasing protective locks.")
                                if (enableShortCycleNotify && notifyDevices) {
                                    notifyDevices.deviceNotification("HVAC CRITICAL ALERT: ${maxCycles} Short-cycles detected in under ${tripWindowMins ?: 60} minutes! Locks released to prevent hardware damage.")
                                }
                                state.shortCycleLog = []
                                state.cycleStartTime = null
                                state.isBuffering = false
                                state.alignmentLockout = null
                                state.activeHysteresis = "idle"
                                state.yoyoCooldownEnds = null
                                state.deviationStartTime = null
                                unschedule("releaseBuffer")
                            }
                        }
                    }
                }
            }
        }
        state.cycleStartTime = null; state.currentAction = "idle"; state.cycleStartMode = null; state.modeDelayLogged = false
        
        evaluateSystem()
    }
}

def trackEnergyStats(action, runMinutes) {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    def history = state.runHistory ?: [:]
    def todayData = history[today] ?: [cool: 0.0, heat: 0.0, aux: 0.0, runs: 0, maxRun: 0.0, minRun: 9999.0, maxOutTemp: null, maxOutHum: null]
    
    if (action == "cooling") todayData.cool += runMinutes
    if (action == "heating") todayData.heat += runMinutes
    if (action == "auxHeating") todayData.aux += runMinutes
    
    if (action in ["cooling", "heating", "auxHeating"]) {
        todayData.runs += 1
        
        if (runMinutes > todayData.maxRun) todayData.maxRun = runMinutes
        if (runMinutes < todayData.minRun) todayData.minRun = runMinutes
    }
    
    history[today] = todayData
    def keys = history.keySet().sort().reverse()
    if (keys.size() > 7) { history = history.subMap(keys[0..6]) }
    state.runHistory = history // Forceful DB Write
}

def sensorHandler(evt) {
    if (state.isBuffering) {
        if (checkComfortOvershootAbort()) return
    }
    
    if (enableDeltaT && returnSensor && dischargeSensor && state.currentAction in ["cooling", "heating"]) {
        def retT = returnSensor.currentValue("temperature")
        def disT = dischargeSensor.currentValue("temperature")
        if (retT != null && disT != null) {
            def dT = state.currentAction == "cooling" ? (retT - disT).doubleValue() : (disT - retT).doubleValue()
            dT = Math.max(0.0d, dT).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
            
            if (state.currentCycleMaxDeltaT == null || dT > state.currentCycleMaxDeltaT) {
                state.currentCycleMaxDeltaT = dT
            }
        }
    }
    
    def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
    def allowNightMinRun = (protectNightMinRun != false)
    
    if (!state.windowOpenHold && enableMinRuntime && (!isNight || allowNightMinRun) && state.currentAction in ["cooling", "heating"] && !state.isBuffering && state.cycleStartTime) {
        def runMins = (now() - state.cycleStartTime) / 60000.0
        if (runMins >= 1.0 && runMins < getMinRunTime()) {
            def curT = thermostat.currentValue("temperature")
            if (curT != null && state.startTemp != null) {
                def dropRate = ((state.currentAction == "cooling" ? state.startTemp - curT : curT - state.startTemp)) / runMins
                if (dropRate >= (tempDropThreshold != null ? tempDropThreshold.toDouble() : 0.5d)) {
                    engageBuffer(runMins)
                    return
                }
            }
        }
    }

    requestEvaluation()
}

def engageBuffer(runMins) {
    // Check Comfort Overshoot before locking into a buffer
    def maxOver = (maxComfortOvershoot != null ? maxComfortOvershoot.toBigDecimal() : 2.0G)
    def currentAvg = getAverageTemp()
    if (state.currentAction == "cooling" && currentAvg > 0 && currentAvg <= (getBaseCoolTarget() - maxOver)) {
        logAction("Skipped Buffer Engagement: Room average (${currentAvg}°) is already ${maxOver}° below target.")
        return
    } else if (state.currentAction == "heating" && currentAvg > 0 && currentAvg >= (getBaseHeatTarget() + maxOver)) {
        logAction("Skipped Buffer Engagement: Room average (${currentAvg}°) is already ${maxOver}° above target.")
        return
    }

    state.isBuffering = true
    state.preBufferCool = thermostat.currentValue("coolingSetpoint")?.toBigDecimal()
    state.preBufferHeat = thermostat.currentValue("heatingSetpoint")?.toBigDecimal()
    
    def bufferAmt = setpointBuffer != null ? setpointBuffer.toBigDecimal() : 3.0G
    def deadband = (hardwareDeadbandMin != null ? hardwareDeadbandMin : 3.0).toBigDecimal()
    def localTemp = thermostat.currentValue("temperature")?.toBigDecimal() ?: 72.0G
    
    if (state.currentAction == "cooling") { 
        def newCool = (localTemp - bufferAmt).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
        def newHeat = (thermostat.currentValue("heatingSetpoint")?.toBigDecimal() ?: 68.0G)
        
        if ((newCool - newHeat) < deadband) newHeat = (newCool - deadband).setScale(0, BigDecimal.ROUND_HALF_UP)
        
        pushSetpointsSafely(newCool, newHeat, "BMS Command -> Compressor Protection Engaged! Temperature dropping too fast. Target temporarily shifted to ${newCool}° to ensure minimum runtime.")
        runIn(10, "verifyBufferEnforcement", [data: [targetCool: newCool.toDouble(), targetHeat: newHeat.toDouble(), action: state.currentAction, retryCount: 0]])
    } else { 
        def newHeat = (localTemp + bufferAmt).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
        def newCool = (thermostat.currentValue("coolingSetpoint")?.toBigDecimal() ?: 72.0G)
 
        if ((newCool - newHeat) < deadband) newCool = (newHeat + deadband).setScale(0, BigDecimal.ROUND_HALF_UP)
        
        pushSetpointsSafely(newCool, newHeat, "BMS Command -> Compressor Protection Engaged! Temperature rising too fast. Target temporarily shifted to ${newHeat}° to ensure minimum runtime.")
        runIn(10, "verifyBufferEnforcement", [data: [targetCool: newCool.toDouble(), targetHeat: newHeat.toDouble(), action: state.currentAction, retryCount: 0]])
    }
    
    def remMins = (getMinRunTime()) - runMins
    runIn(Math.max(1, (remMins * 60).toInteger()), "releaseBuffer")
}

def verifyBufferEnforcement(data) {
    if (!state.isBuffering) return 
    
    def currentCool = thermostat.currentValue("coolingSetpoint")?.toBigDecimal()
    def currentHeat = thermostat.currentValue("heatingSetpoint")?.toBigDecimal()
    def targetCool = data.targetCool.toBigDecimal()
    def targetHeat = data.targetHeat.toBigDecimal()
    
    def retryNeeded = false
    if (data.action == "cooling" && (currentCool == null || Math.abs((currentCool - targetCool).doubleValue()) > 0.1)) retryNeeded = true
    if (data.action == "heating" && (currentHeat == null || Math.abs((currentHeat - targetHeat).doubleValue()) > 0.1)) retryNeeded = true
    
    if (retryNeeded) {
        def attempt = (data.retryCount ?: 0) + 1
        if (attempt <= 3) {
            logAction("WARNING: Thermostat failed to confirm protective setpoint (Attempt ${attempt}/3). Retrying wireless command...")
            pushSetpointsSafely(targetCool, targetHeat)
            runIn(15, "verifyBufferEnforcement", [data: [targetCool: targetCool.toDouble(), targetHeat: targetHeat.toDouble(), action: data.action, retryCount: attempt]])
        } else {
            logAction("CRITICAL ERROR: Thermostat failed to confirm protective setpoint after 3 attempts. Compressor protection may be compromised.")
            if (notifyDevices) notifyDevices.deviceNotification("HVAC ALERT: Thermostat is unresponsive to wireless commands. Compressor protection failed to engage.")
        }
    } else {
        if ((data.retryCount ?: 0) > 0) logAction("Success: Thermostat confirmed protective setpoint on attempt ${(data.retryCount ?: 0) + 1}.")
    }
}

def releaseBuffer() { 
    state.isBuffering = false
    logAction("Compressor Protection Buffer Complete. Reverting setpoint assignments.") 
    
    if (state.manualHoldEnds && now() < state.manualHoldEnds) {
        logAction("BMS Command -> Manual Hold active. Restoring user's pre-buffer targets.")
        if (state.preBufferCool != null && state.preBufferHeat != null) {
            pushSetpointsSafely(state.preBufferCool.toBigDecimal(), state.preBufferHeat.toBigDecimal())
        }
    }

    state.preBufferCool = null
    state.preBufferHeat = null
    evaluateSystem() 
}

def checkDeltaT() { 
    if (state.currentAction == "idle" || state.fireEmergency) return
    def retT = returnSensor?.currentValue("temperature")
    def disT = dischargeSensor?.currentValue("temperature")
    if (retT == null || disT == null) return
  
    def dT = 0.0G
    if (state.currentAction == "cooling") {
        dT = Math.max(0.0d, (retT - disT).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    } else if (state.currentAction == "heating") {
        dT = Math.max(0.0d, (disT - retT).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    
    def minDT = state.currentAction == "cooling" ? (minCoolingDeltaT != null ? minCoolingDeltaT.toBigDecimal() : 12.0G) : (minHeatingDeltaT != null ? minHeatingDeltaT.toBigDecimal() : 15.0G)
    if (dT < minDT) { 
        logAction("HVAC WARNING: Poor efficiency. Delta-T is ${String.format('%.1f', dT)}°F.")
        
        if (notifyDeltaT && notifyDevices) {
            if (!state.lastDeltaTAlert || (now() - state.lastDeltaTAlert) > 86400000L) { 
                notifyDevices.deviceNotification("HVAC Alert: Poor efficiency detected. Delta-T is ${String.format('%.1f', dT)}°F. Unit may be freezing up or low on refrigerant.")
                state.lastDeltaTAlert = now()
            }
        }
        
        if (emergencyShutoff) {
            state.deltaTShutoff = true
            state.lastCommandTime = now()
            logAction("EMERGENCY DELTA-T SHUTOFF: Turning HVAC OFF and locking system to prevent coil/compressor damage.")
            thermostat.off()
            return
        }
    } else {
        logAction("Delta-T Check Passed: ${String.format('%.1f', dT)}°F.")
    }
    
    runIn(Math.max(1, ((deltaTCheckDelay ?: 30) * 60).toInteger()), "checkDeltaT")
}

def processFilterWear(actualRunMinutes) { 
    def ind = indoorIAQ ? (indoorIAQ.currentValue("airQualityIndex") ?: 0) : 0
    def out = outdoorIAQ ? (outdoorIAQ.currentValue("airQualityIndex") ?: 0) : 0
    state.filterRunMinutes = (state.filterRunMinutes ?: 0.0) + (actualRunMinutes * (1.0 + (ind * 0.01) + (out * 0.002))) 
    
    if (notifyFilter && notifyDevices) {
        def maxMins = (maxFilterHours ?: 300) * 60.0
        def percentLeft = Math.max(0.0d, (100.0 - ((state.filterRunMinutes / maxMins) * 100.0)).doubleValue())
        def alertThreshold = filterNotifyThreshold != null ? filterNotifyThreshold.toInteger() : 10
        
        if (percentLeft < alertThreshold && !state.filterAlertSent) {
            notifyDevices.deviceNotification("HVAC Maintenance: Your air filter life is below ${alertThreshold}%. Please replace it soon to maintain efficiency.")
            state.filterAlertSent = true
        }
    }
}

def dailyMaintenanceCheck() {
    // 1. Check Sensor Health (Battery & Stale Reports)
    if (notifySensorHealth && notifyDevices) {
        def lowBatLimit = lowBatteryThreshold != null ? lowBatteryThreshold.toInteger() : 20
        def maxAgeMs = (staleSensorHours != null ? staleSensorHours.toLong() : 12L) * 3600000L
        def problemSensors = []

        for (int i = 1; i <= 5; i++) {
            def dev = getZoneTempDevice(i)
            if (settings["enableZ${i}"] && dev) {
                def bat = dev.currentValue("battery")
                if (bat != null && bat.toInteger() <= lowBatLimit) {
                    problemSensors << "${dev.displayName} (Battery: ${bat}%)"
                }
                def tempState = dev.currentState("temperature")
                def lastUpdate = tempState?.date?.time ?: now()
                if ((now() - lastUpdate) > maxAgeMs) {
                    problemSensors << "${dev.displayName} (No temp report in >${staleSensorHours ?: 12}h)"
                }
            }
        }
        if (problemSensors.size() > 0) {
            notifyDevices.deviceNotification("HVAC Sensor Alert: Check room sensor(s): ${problemSensors.unique().join(', ')}")
        }
    }

    // 2. Seasonal Reminders
    if (!notifyMaintenance || !notifyDevices) return

    def today = new Date()
    def month = today.format("MM", location.timeZone).toInteger()
    def day = today.format("dd", location.timeZone).toInteger()
    def year = today.format("yyyy", location.timeZone)

    // May 1st - Summer check
    if (month == 5 && day == 1 && state.lastMaintenanceAlert != "summer_${year}") {
        notifyDevices.deviceNotification("HVAC Reminder: Summer is approaching. It's time to schedule your AC maintenance check and clear the condensate drain line.")
        state.lastMaintenanceAlert = "summer_${year}"
    }
    
    // October 1st - Winter check
    if (month == 10 && day == 1 && state.lastMaintenanceAlert != "winter_${year}") {
        notifyDevices.deviceNotification("HVAC Reminder: Winter is approaching. It's time to schedule your Heating maintenance check and test the ignitor/elements.")
        state.lastMaintenanceAlert = "winter_${year}"
    }
}

def scheduleRandomFilterAlert() {
    if (!enableFilterTracker || !filterAlertSwitch) return
    
    def randomSeconds = new java.util.Random().nextInt(39600)
    runIn(Math.max(1, randomSeconds.toInteger()), "triggerFilterAlertSwitch")
    logInfo("Filter Alert Switch scheduled to trigger randomly in ${Math.round(randomSeconds / 60)} minutes.")
}

def triggerFilterAlertSwitch() {
    if (!enableFilterTracker || !filterAlertSwitch) return
    
    if (filterAlertModes && !(filterAlertModes as List).contains(location.mode)) {
        logInfo("Skipping filter alert switch: current mode (${location.mode}) is not in the allowed list.")
        return
    }
    
    def maxMins = (maxFilterHours ?: 300) * 60.0
    def usedMins = state.filterRunMinutes ?: 0.0
    def percentLeft = Math.max(0.0d, (100.0 - ((usedMins / maxMins) * 100.0)).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    def alertThreshold = filterNotifyThreshold != null ? filterNotifyThreshold.toInteger() : 10
    
    if (percentLeft < alertThreshold) {
        filterAlertSwitch.on()
        
        if (notifyFilter && notifyDevices) {
            notifyDevices.deviceNotification("Daily Reminder: Your HVAC air filter life is at ${percentLeft}% (Below ${alertThreshold}%). Please replace it soon.")
            logAction("Filter push notification sent successfully.")
        }
        
        logAction("Filter Alert Threshold Reached: Turning on virtual alert switch for 10 minutes.")
        runIn(600, "turnOffFilterAlertSwitch")
    }
}

def turnOffFilterAlertSwitch() {
    if (filterAlertSwitch) {
        filterAlertSwitch.off()
        logAction("Filter Alert Cycle Complete: Alert switch turned off.")
    }
}

def trackRecentCycle(action, runMinutes, maxDeltaT = null) {
    def list = state.recentCycles ?: []
    def timestamp = new Date().format("MM/dd hh:mm a", location.timeZone)
    def formattedTime = String.format("%.1f", runMinutes)
    
    def actionName = action.capitalize()
    if (action == "auxHeating") actionName = "Aux Heat"
    
    def dtString = maxDeltaT != null ? " | Peak ΔT: <b>${String.format('%.1f', maxDeltaT)}°</b>" : ""
    def cycleLog = "[${timestamp}] ${actionName} ran for <b>${formattedTime} minutes</b>${dtString}"
    
    list.add(0, cycleLog)
    if (list.size() > 10) { list = list[0..9] }
    state.recentCycles = list // Forceful DB Write
}

def trackIdleTime(idleMins) {
    def list = state.recentIdleTimes ?: []
    def timestamp = new Date().format("MM/dd hh:mm a", location.timeZone)
    
    def totalMins = idleMins.toInteger()
    def hrs = (totalMins / 60).toInteger()
    def mins = totalMins % 60
    def timeStr = hrs > 0 ? "${hrs}h ${mins}m" : "${mins}m"
    
    def idleLog = "[${timestamp}] System rested (Idle) for <b>${timeStr}</b>"
    
    list.add(0, idleLog)
    if (list.size() > 10) { list = list[0..9] }
    state.recentIdleTimes = list // Forceful DB Write
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size()>30)h=h[0..29]
    state.actionHistory=h 
}
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }

def getMinRunTime() {
    return state.currentAction == "cooling" ? (summerMinRunTime != null ? summerMinRunTime.toInteger() : 25) : (winterMinRunTime != null ? winterMinRunTime.toInteger() : 15)
}
