/**
 * Advanced Climate Manager 2.0
 */ 
definition(
    name: "Advanced Climate Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Comfort",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
                      
            input "btnEnforceModes", "button", title: "🎯 Enforce Mode Setpoints"

            if (thermostat) {
                // Gather Core Metrics
                def tstatTemp = thermostat.currentValue("temperature") ?: "--"
                def tstatHum = thermostat.currentValue("humidity") ?: "--"
                def tstatCool = thermostat.currentValue("coolingSetpoint") ?: "--"
                def tstatHeat = thermostat.currentValue("heatingSetpoint") ?: "--"
                def tstatMode = thermostat.currentValue("thermostatMode")?.toUpperCase() ?: "UNKNOWN"
                def tstatState = thermostat.currentValue("thermostatOperatingState")?.toUpperCase() ?: "IDLE"
                def tstatFan = thermostat.currentValue("thermostatFanMode")?.toUpperCase() ?: "UNKNOWN"
            
                def stateColor = "black"
                if (tstatState == "COOLING") stateColor = "blue"
                if (tstatState == "HEATING") stateColor = "#d9534f" 
                if (tstatState.contains("AUX") || tstatState.contains("EMERGENCY")) stateColor = "red" 
         
                def avgTemp = getAverageTemp()
                def avgHum = getAverageHumidity()
                
                // Gather Diagnostics
                def currentLocMode = location.mode ?: "Unknown"

                // --- Timer Calculations for Dashboard ---
                def yoyoRemainingMinsRaw = (state.yoyoCooldownEnds && now() < state.yoyoCooldownEnds.toLong() && state.currentAction == "idle") ? (state.yoyoCooldownEnds.toLong() - now()) / 60000.0 : 0.0
                def yoyoMinsDash = yoyoCooldownMins != null ? yoyoCooldownMins.toInteger() : 15
                
                def isSmartModeAllowed = !averagingModes || (averagingModes as List).contains(location.mode)
                def globalHysActive = enableHysteresis && globalHysteresis
                
                // 1. Alignment & Hysteresis Row
                def alignmentStr = ""
                if (!enableAverageSync && !globalHysActive) {
                    alignmentStr = "<span style='color:gray;'>Disabled</span>"
                } else if (!isSmartModeAllowed && !globalHysActive) {
                    alignmentStr = "<span style='color:orange;'><b>Disabled by Mode (${currentLocMode})</b></span>"
                } else if (state.alignmentLockout) {
                    alignmentStr = "<span style='color:red;'><b>Aborted (Waiting for local temp to reach ${state.alignmentLockoutTarget}°)</b></span>"
                } else if (enableHysteresis && state.activeHysteresis == "idle") {
                    alignmentStr = "<span style='color:blue;'><b>Floating in Deadband (System Idle)</b></span>"
                } else if (enableHysteresis && state.activeHysteresis != "idle") {
                    alignmentStr = "<span style='color:green;'><b>Active Recovery (${state.activeHysteresis.capitalize()})</b></span>"
                } else if (!isSmartModeAllowed) { 
                    alignmentStr = "<span style='color:green;'>Ready (Global Hysteresis Active)</span>"
                } else {
                    alignmentStr = "<span style='color:green;'>Ready</span>"
                }

                // 2. Anti-Yo-Yo / Smart Debounce Row
                def debounceStr = "<span style='color:gray;'>Idle (Ready)</span>"
                if (yoyoMinsDash == 0) {
                    debounceStr = "<span style='color:gray;'>Disabled</span>"
                } else if (yoyoRemainingMinsRaw > 0) {
                    def lockMode = state.lastCycleMode == "heating" || state.lastCycleMode == "auxHeating" ? "Heating locked out" : "Cooling locked out"
                    def proportion = yoyoMinsDash > 0 ? (yoyoRemainingMinsRaw / yoyoMinsDash) : 0
                    def currentGlide = (2.5 * proportion).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                    debounceStr = "<span style='color:orange;'><b>Active (${Math.round(yoyoRemainingMinsRaw)}m remaining)</b></span><br><span style='font-size:12px; color:#555;'>${lockMode} (Live Glide Offset: ${currentGlide}°)</span>"
                }

                def bufferStr = "<span style='color:gray;'>Inactive</span>"
                if (state.isBuffering && state.cycleStartTime) {
                    def elapsedMins = (now() - state.cycleStartTime) / 60000.0
                    def remaining = Math.max(0, Math.round(getMinRunTime() - elapsedMins))
                    bufferStr = "<span style='color:blue;'><b>Engaged (${remaining} mins remaining)</b></span>"
                }
                
                def coastingStr = "<span style='color:gray;'>Idle</span>"
                if (state.coastingEnds && now() < state.coastingEnds) {
                    def cRem = Math.max(0, Math.round((state.coastingEnds - now()) / 60000))
                    coastingStr = "<span style='color:blue;'><b>Active (${cRem}m remaining)</b></span>"
                } else if (!enableThermalCoasting) {
                    coastingStr = "<span style='color:gray;'>Disabled</span>"
                }
   
                def swapText = "N/A (Disabled)"
                if (enableAutoSwap) {
                    def safeSwapDB = autoSwapDeadband ?: 1.0
                    def isHysAllowedDash = enableHysteresis && (isSmartModeAllowed || globalHysteresis)
                    if (isHysAllowedDash) {
                        def drift = hysteresisDrift ?: 1.0
                        if (safeSwapDB <= drift) safeSwapDB = drift + 0.5
                    }
   
                    def distToCool = tstatCool != "--" ? Math.round(( (tstatCool.toBigDecimal() + safeSwapDB) - avgTemp.toBigDecimal() ) * 10) / 10.0 : 0
                    def distToHeat = tstatHeat != "--" ? Math.round(( avgTemp.toBigDecimal() - (tstatHeat.toBigDecimal() - safeSwapDB) ) * 10) / 10.0 : 0
                    
                    def exactHeatSwap = tstatHeat != "--" ? (tstatHeat.toBigDecimal() - safeSwapDB).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : "--"
                    def exactCoolSwap = tstatCool != "--" ? (tstatCool.toBigDecimal() + safeSwapDB).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : "--"
                    
                    if (tstatMode == "HEAT") swapText = "<span style='color:blue;'>↑ ${distToCool}° until Swap to COOL (DB: ${safeSwapDB}° | Triggers at ${exactCoolSwap}°)</span>"
                    else if (tstatMode == "COOL") swapText = "<span style='color:red;'>↓ ${distToHeat}° until Swap to HEAT (DB: ${safeSwapDB}° | Triggers at ${exactHeatSwap}°)</span>"
                    else swapText = "Thermostat not in Heat/Cool mode"
                }

                def deltaTStr = "N/A (Disabled or Missing Sensors)"
                if (enableDeltaT && returnSensor && dischargeSensor) {
                    def retT = returnSensor.currentValue("temperature")
                    def disT = dischargeSensor.currentValue("temperature")
                    if (retT != null && disT != null) {
                        def dT = 0.0
                        if (tstatState == "COOLING") {
                            dT = Math.max(0.0, (retT - disT).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                        } else if (tstatState == "HEATING") {
                            dT = Math.max(0.0, (disT - retT).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                        } else {
                            dT = 0.0
                        }
            
                        def health = ""
                        if (tstatState == "COOLING" && dT < (minCoolingDeltaT ?: 12.0)) health = " <span style='color:red;'>(Warning: Low)</span>"
                        else if (tstatState == "HEATING" && dT < (minHeatingDeltaT ?: 15.0)) health = " <span style='color:red;'>(Warning: Low)</span>"
                        else if (tstatState in ["COOLING", "HEATING"]) health = " <span style='color:green;'>(Good)</span>"
                        else health = " <span style='color:gray;'>(System Idle)</span>"
                         
                        deltaTStr = "${dT}°F (Return: ${retT}° | Supply: ${disT}°)${health}"
                    } else {
                        deltaTStr = "Waiting for sensor data..."
                    }
                }

                // Calculated Deadband Metric
                def currentDeadbandStr = "N/A"
                if (tstatCool != "--" && tstatHeat != "--") {
                    def gap = (tstatCool.toBigDecimal() - tstatHeat.toBigDecimal()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                    if (gap < 3.0) {
                        currentDeadbandStr = "<span style='color:red;'><b>${gap}° (Violation - Conflict Detected)</b></span>"
                    } else if (gap == 3.0) {
                        currentDeadbandStr = "<span style='color:orange;'><b>${gap}° (Minimum Enforced)</b></span>"
                    } else {
                        currentDeadbandStr = "<span style='color:green;'>${gap}° (Healthy Gap)</span>"
                    }
                }

                // Gather Maintenance
                def filterLifeStr = "Disabled"
                if (enableFilterTracker) {
                    def maxMins = (maxFilterHours ?: 300) * 60
                    def usedMins = state.filterRunMinutes ?: 0.0
                    def percentLeft = Math.max(0.0, 100.0 - ((usedMins / maxMins) * 100)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                    filterLifeStr = "${percentLeft}%"
                    def alertThreshold = filterNotifyThreshold != null ? filterNotifyThreshold : 10
                    if (percentLeft < alertThreshold) filterLifeStr = "<span style='color:red; font-weight:bold;'>${percentLeft}% (Change Soon)</span>"
                }
                
                def hvacContactStr = "Not Configured"
                if (hvacCompanyName || hvacCompanyPhone) {
                    hvacContactStr = "${hvacCompanyName ?: 'N/A'} ${hvacCompanyPhone ? '(' + hvacCompanyPhone + ')' : ''}"
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
                
                def compressorRunsStr = "${sevenDayRuns} Cycles (${totalRunHours} Total Hours)<br><span style='font-size:12px; color:#555;'>Avg: <b>${avgStr}</b> | Highest: <b>${highestStr}</b> | Shortest: <b>${lowestStr}</b></span>"
                
                // --- SCORING & ENVIRONMENT CALCULATIONS ---
                def outTempStr = "--"
                def inOutDeltaStr = "--"
                def comfortScoreStr = "--"
                def efficiencyScoreStr = "--"
                def outT = null

                if (outdoorTempSensor) {
                    outT = outdoorTempSensor.currentValue("temperature")
                    if (outT != null) {
                        outTempStr = "${outT}°"
                        def inOutD = Math.abs((avgTemp.toBigDecimal() - outT.toBigDecimal()).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                        inOutDeltaStr = "${inOutD}°"
                    }
                }

                // 1. Comfort & Health Score (Dynamic Targets)
                
                // Temp Target: Ignore temporary offsets (like compressor protection) and use true mode targets
                def activeTargetT = 72.0 
                def isManualHold = state.manualHoldEnds && now() < state.manualHoldEnds

                if (isManualHold) {
                    // If a user manually changed the thermostat, evaluate against their manual target
                    if (tstatMode == "COOL" && tstatCool != "--") activeTargetT = tstatCool.toBigDecimal()
                    else if (tstatMode == "HEAT" && tstatHeat != "--") activeTargetT = tstatHeat.toBigDecimal()
                    else if (tstatCool != "--" && tstatHeat != "--") {
                        def cSet = tstatCool.toBigDecimal()
                        def hSet = tstatHeat.toBigDecimal()
                        activeTargetT = Math.abs(avgTemp.toBigDecimal() - cSet) < Math.abs(avgTemp.toBigDecimal() - hSet) ? cSet : hSet
                    }
                } else {
                    // Otherwise, pull the true target from the app settings to ignore dynamic shifts
                    def evalMode = location.mode
                    def isAway = awayModes ? (awayModes as List).contains(evalMode) : false
                    def isNight = nightModes ? (nightModes as List).contains(evalMode) : false
                    
                    def trueCool = homeCoolingSetpoint ?: 74.0
                    def trueHeat = homeHeatingSetpoint ?: 68.0
                    
                    if (isNight) {
                        trueCool = nightCoolingSetpoint ?: 70.0
                        trueHeat = nightHeatingSetpoint ?: 66.0
                    } else if (isAway) {
                        trueCool = awayCoolingSetpoint ?: 78.0
                        trueHeat = awayHeatingSetpoint ?: 62.0
                        
                        if (enableDeepAway && state.awayStartTime && (now() - state.awayStartTime) >= ((deepAwayDelayHours ?: 4) * 3600000)) {
                            trueCool = deepAwayCoolingSetpoint ?: 82.0
                            trueHeat = deepAwayHeatingSetpoint ?: 58.0
                        }
                    }
                    
                    if (tstatMode == "COOL") activeTargetT = trueCool
                    else if (tstatMode == "HEAT") activeTargetT = trueHeat
                    else activeTargetT = Math.abs(avgTemp.toBigDecimal() - trueCool) < Math.abs(avgTemp.toBigDecimal() - trueHeat) ? trueCool : trueHeat
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
                    def tempDiff = avgTemp.toBigDecimal() - activeTargetT
                    def tempPenalty = 0.0
                    
                    // Reduce penalty if the room is over-conditioned (e.g., colder than target in Cool mode)
                    if (tstatMode == "COOL" && tempDiff < 0) {
                        tempPenalty = Math.abs(tempDiff) * 1.5 
                    } else if (tstatMode == "HEAT" && tempDiff > 0) {
                        tempPenalty = Math.abs(tempDiff) * 1.5
                    } else {
                        // Standard penalty for being outside the desired comfort range
                        tempPenalty = Math.abs(tempDiff) * 5.0
                    }
                    comfortScore -= tempPenalty
                }
                if (avgHum > 0) {
                    def humPenalty = Math.abs(avgHum.toBigDecimal() - targetHum) * 1.0
                    comfortScore -= humPenalty
                }
                comfortScore = Math.max(0.0, Math.min(100.0, comfortScore)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                def comfortColor = comfortScore >= 85 ? "green" : (comfortScore >= 70 ? "orange" : "red")
                comfortScoreStr = "<span style='color:${comfortColor}; font-weight:bold;'>${comfortScore}%</span>"
                
                def targetDesc = "Targeting ${activeTargetT}°F"
                def evalModeForDesc = location.mode
                def isAwayDesc = awayModes ? (awayModes as List).contains(evalModeForDesc) : false
                if (isAwayDesc && enableDeepAway && state.awayStartTime && (now() - state.awayStartTime) >= ((deepAwayDelayHours ?: 4) * 3600000)) {
                    targetDesc += " (Deep Setback)"
                }

                // 2. Efficiency Score (Based on Filter, HVAC Delta-T, & Cycle Times)
                def efficiencyScore = 100.0
                
                // 2a. Filter Penalty
                def maxMinsE = (maxFilterHours ?: 300) * 60
                def usedMinsE = state.filterRunMinutes ?: 0.0
                def filterLifePct = Math.max(0.0, 100.0 - ((usedMinsE / maxMinsE) * 100))
                if (filterLifePct < 20.0) efficiencyScore -= (20.0 - filterLifePct)
                
                // 2b. Equipment Delta-T Penalty
                if (enableDeltaT && returnSensor && dischargeSensor && (tstatState == "COOLING" || tstatState == "HEATING")) {
                    def targetDT = tstatState == "COOLING" ? (minCoolingDeltaT ?: 12.0) : (minHeatingDeltaT ?: 15.0)
                    def retT = returnSensor.currentValue("temperature")
                    def disT = dischargeSensor.currentValue("temperature")
                    if (retT != null && disT != null) {
                        def actualDT = tstatState == "COOLING" ? Math.max(0.0, (retT - disT).doubleValue()) : Math.max(0.0, (disT - retT).doubleValue())
                        if (actualDT < targetDT) {
                            def dtPenalty = ((targetDT - actualDT) / targetDT) * 50.0
                            efficiencyScore -= dtPenalty
                        }
                    }
                }
                
                // 2c. Short Cycle Penalty
                if (avgCycleMins > 0 && avgCycleMins < 15.0) {
                    efficiencyScore -= (15.0 - avgCycleMins) * 2.0
                }

                efficiencyScore = Math.max(0.0, Math.min(100.0, efficiencyScore)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                def effColor = efficiencyScore >= 85 ? "green" : (efficiencyScore >= 70 ? "orange" : "red")
                efficiencyScoreStr = "<span style='color:${effColor}; font-weight:bold;'>${efficiencyScore}%</span>"

                // --- SICK MODE CALCULATIONS FOR DASHBOARD ---
                def s1State = "N/A"
                if (sickModeSwitch) {
                    def s1List = sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch]
                    s1State = s1List.any { it?.currentValue("switch") == "on" } ? "ON" : "OFF"
                }
                
                def s2State = sickModeSwitch2 ? (sickModeSwitch2.currentValue("switch")?.toUpperCase() ?: "UNKNOWN") : "N/A"
                def s3State = sickModeSwitch3 ? (sickModeSwitch3.currentValue("switch")?.toUpperCase() ?: "UNKNOWN") : "N/A"
                
                def isSickModeOn = (s1State == "ON" || s2State == "ON" || s3State == "ON")
                
                def sickModeDetails = ""

                if (state.fireEmergency) {
                    sickModeDetails = "<span style='color:red;'><b>LOCKED OUT (Fire/CO Emergency)</b> - Fan is strictly isolated and turned OFF for safety.</span>"
                } else if (state.windowOpenHold) {
                    sickModeDetails = "<span style='color:orange;'><b>Overridden (Window/Door Open)</b> - HVAC is locked OFF.</span>"
                } else if (isSickModeOn) {
                    if (tstatFan != "ON" && tstatFan != "CIRCULATE") {
                        sickModeDetails = "<span style='color:red;'><b>ACTIVE (Command Sent) but Fan is ${tstatFan}</b> - Waiting for thermostat to accept command or HVAC to idle.</span>"
                    } else {
                        sickModeDetails = "<span style='color:blue;'><b>ACTIVE (Fan Forced ON)</b> - Continuously filtering air.</span>"
                    }
                } else {
                    sickModeDetails = "<span style='color:gray;'>Idle (Switches OFF)</span>"
                }

                def sickSwitchesStr = ""
                if (s1State != "N/A") sickSwitchesStr += "Sw 1: <b>${s1State}</b> "
                if (s2State != "N/A") sickSwitchesStr += (sickSwitchesStr ? "| " : "") + "Sw 2: <b>${s2State}</b> "
                if (s3State != "N/A") sickSwitchesStr += (sickSwitchesStr ? "| " : "") + "Sw 3: <b>${s3State}</b>"
                if (!sickSwitchesStr) sickSwitchesStr = "No Switches Configured"

                def sickFinalHtml = "${sickSwitchesStr}<br><span style='font-size:12px; color:#555;'>Result: ${sickModeDetails}</span>"
                
                // --- MOOD SYNC DASHBOARD DISPLAY ---
                def m1 = moodVarU1 ? (getGlobalVar(moodVarU1)?.value ?: "😐") : "N/A"
                def m2 = moodVarU2 ? (getGlobalVar(moodVarU2)?.value ?: "😐") : "N/A"
                def m3 = moodVarU3 ? (getGlobalVar(moodVarU3)?.value ?: "😐") : "N/A"
                
                def n1 = userName1 ?: "Shane"
                def n2 = userName2 ?: "Christy"
                def n3 = userName3 ?: "Leanne"
                
                def moodIcons = ""
                if (m1 != "N/A") moodIcons += "${n1}: <b>${m1}</b> "
                if (m2 != "N/A") moodIcons += (moodIcons ? "| " : "") + "${n2}: <b>${m2}</b> "
                if (m3 != "N/A") moodIcons += (moodIcons ? "| " : "") + "${n3}: <b>${m3}</b>"
                if (!moodIcons) moodIcons = "No Users Configured"
                
                def moodStatus = state.moodCoolingActive ? "<span style='color:blue; font-weight:bold;'>Active (-2.0° Cooling Offset)</span>" : "<span style='color:gray;'>Idle (Moods Normal)</span>"
                def moodDashStr = "${moodIcons}<br><span style='font-size:12px; color:#555;'>Result: ${moodStatus}</span>"

                // Unified Dashboard HTML
                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 28%; }
                    .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                    .dash-val { text-align: left !important; padding-left: 15px !important; }
                </style>
                <table class="dash-table">
                    <thead><tr><th>Metric</th><th>Calculated Average (Rooms)</th><th>Thermostat Sensor</th><th>Target Setpoint</th></tr></thead>
                    <tbody>
                        <tr><td class="dash-hl">Temperature</td><td><b>${avgTemp}°</b></td><td>${tstatTemp}°</td><td>Cool: ${tstatCool}° | Heat: ${tstatHeat}°</td></tr>
                        <tr><td class="dash-hl">Humidity</td><td><b>${avgHum}%</b></td><td>${tstatHum}%</td><td>--</td></tr>
                        <tr><td class="dash-hl">HVAC State</td><td><b>Mode: ${tstatMode}</b></td><td colspan="2" style="color:${stateColor};"><b>Status: ${tstatState}</b> (Fan: ${tstatFan})</td></tr>
                        
                        <tr><td colspan="4" class="dash-subhead">Environment & Scoring</td></tr>
                        <tr><td class="dash-hl">Outside Temp</td><td colspan="3" class="dash-val">${outTempStr}</td></tr>
                        <tr><td class="dash-hl">Indoor/Outdoor Delta-T</td><td colspan="3" class="dash-val">${inOutDeltaStr}</td></tr>
                        <tr><td class="dash-hl">Comfort & Health Score</td><td colspan="3" class="dash-val">${comfortScoreStr} <span style='font-size:11px; color:gray;'>(${targetDesc} & ${targetHum}% Hum)</span></td></tr>
                        <tr><td class="dash-hl">System Efficiency Score</td><td colspan="3" class="dash-val">${efficiencyScoreStr} <span style='font-size:11px; color:gray;'>(Based on Hardware Delta-T, Cycles, & Filter)</span></td></tr>

                        <tr><td colspan="4" class="dash-subhead">Internal Diagnostics</td></tr>
                        <tr><td class="dash-hl">Location Mode</td><td colspan="3" class="dash-val">${currentLocMode}</td></tr>
                        <tr><td class="dash-hl">Auto-Swap Distance</td><td colspan="3" class="dash-val">${swapText}</td></tr>
                        <tr><td class="dash-hl">Calculated Deadband</td><td colspan="3" class="dash-val">${currentDeadbandStr}</td></tr>
                        <tr><td class="dash-hl">Live Delta-T</td><td colspan="3" class="dash-val">${deltaTStr}</td></tr>
                        <tr><td class="dash-hl">Dynamic Alignment Status</td><td colspan="3" class="dash-val">${alignmentStr}</td></tr>
                        <tr><td class="dash-hl">Psychological Auto-Reg</td><td colspan="3" class="dash-val">${moodDashStr}</td></tr>
                        <tr><td class="dash-hl">Anti-Yo-Yo (Debounce)</td><td colspan="3" class="dash-val">${debounceStr}</td></tr>
                        <tr><td class="dash-hl">Compressor Protection</td><td colspan="3" class="dash-val">${bufferStr}</td></tr>
                        <tr><td class="dash-hl">Thermal Coasting (Blower)</td><td colspan="3" class="dash-val">${coastingStr}</td></tr>
                        <tr><td class="dash-hl">Sick Mode (Continuous Fan)</td><td colspan="3" class="dash-val">${sickFinalHtml}</td></tr>
                        
                        <tr><td colspan="4" class="dash-subhead">Maintenance & Service</td></tr>
                        <tr><td class="dash-hl">7-Day Compressor Runs</td><td colspan="3" class="dash-val">${compressorRunsStr}</td></tr>
                        <tr><td class="dash-hl">Filter Life Remaining</td><td colspan="3" class="dash-val">${filterLifeStr}</td></tr>
                        <tr><td class="dash-hl">Last Filter Change</td><td colspan="3" class="dash-val">${state.lastFilterDate ?: "Not Recorded"}</td></tr>
                        <tr><td class="dash-hl">Last HVAC Service</td><td colspan="3" class="dash-val">${state.lastServiceDate ?: "Not Recorded"}</td></tr>
                        <tr><td class="dash-hl">Service Contact</td><td colspan="3" class="dash-val">${hvacContactStr}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
            } else {
                paragraph "<i>Please select a thermostat below to populate the dashboard.</i>"
            }
        }

        if (enableFilterTracker) {
            section("<b>Maintenance Quick Actions</b>", hideable: true) {
                input "resetFilter", "button", title: "Record Filter Change Today (Resets Life to 100%)"
                input "resetService", "button", title: "Record HVAC Service Today"
             }
        }

        section("<b>Zone Breakdown</b>", hideable: true) {
            def zoneHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Zone Name</th><th>Temp</th><th>Humidity</th><th>Occupied?</th><th>Status</th></tr></thead><tbody>"
            def timeoutMs = (occupancyTimeout ?: 60) * 60000
            def hasZones = false
            def maxAgeMs = 24 * 60 * 60 * 1000 
 
            def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
            def isSmartModeAllowed = !averagingModes || (averagingModes as List).contains(location.mode)
            
            for (int i = 1; i <= 5; i++) {
                if (settings["enableZ${i}"] && settings["z${i}Temp"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Zone ${i}"
                    def zTempDev = settings["z${i}Temp"]
                    
                    def tempState = zTempDev.currentState("temperature")
                    def tVal = tempState?.value != null ? tempState.value.toBigDecimal() : null
                    def lastUpdate = tempState?.date?.time ?: now()
                    
                    def isError = tVal == null || tVal < 40.0 || tVal > 100.0 || (now() - lastUpdate) > maxAgeMs
                    def zTempStr = tVal != null ? "${tVal}°" : "--"
                    
                    def zHum = settings["z${i}Hum"] ? (settings["z${i}Hum"].currentValue("humidity") ?: "--") : "N/A"
                    def zMotion = settings["z${i}Motion"]
                    def entSwitch = settings["z${i}EntertainmentSwitch"]
                    def isEntForced = entSwitch && entSwitch.currentValue("switch") == "on"
                    
                    def isOccupied = "N/A"
                    def zStatus = "<span style='color:green;'>Averaging</span>"
        
                    if (!isSmartModeAllowed) {
                        isOccupied = "N/A (Mode Restriction)"
                        zStatus = "<span style='color:orange;'>Ignored (Mode Restriction)</span>"
                    } else if (isError) {
                        zStatus = "<span style='color:red;'>Sensor Error (Ignored)</span>"
                        isOccupied = "N/A"
                    } else if (isNight) {
                        def nSwitch = settings["z${i}NightSwitch"]
                        def isNightForced = nSwitch && nSwitch.currentValue("switch") == "on"
                        if (isNightForced) {
                            isOccupied = "Yes (Night Lock)"
                            zStatus = "<span style='color:blue;'>Averaging (Night Lock)</span>"
                        } else {
                            isOccupied = "N/A (Night Mode)"
                            zStatus = "<span style='color:gray;'>Ignored (Not Night Room)</span>"
                        }
                    } else if (isEntForced) {
                        isOccupied = "Yes (Entertainment)"
                        zStatus = "<span style='color:blue;'>Averaging (Entertainment)</span>"
                    } else if (enableOccupancy && zMotion) {
                        def mList = zMotion instanceof List ? zMotion : [zMotion]
                        def anyOccupied = mList.any { m -> state.zoneLastActive && state.zoneLastActive[m.id] && (now() - state.zoneLastActive[m.id]) < timeoutMs }
                        if (anyOccupied) {
                            isOccupied = "Yes"
                        } else {
                            isOccupied = "No"
                            zStatus = "<span style='color:gray;'>Ignored (Empty)</span>"
                        }
                    }
    
                    zoneHTML += "<tr><td><b>${zName}</b></td><td>${zTempStr}</td><td>${zHum}%</td><td>${isOccupied}</td><td>${zStatus}</td></tr>"
                }
            }
            
            zoneHTML += "</tbody></table>"
            if (hasZones) paragraph zoneHTML else paragraph "<i>No zones configured yet.</i>"
        }
        
        section("<b>7-Day Compressor Cycle Statistics</b>", hideable: true) {
            def runStatsHtml = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Date</th><th>Max OSA<br>(Temp / Hum)</th><th>Cooling</th><th>Heating</th><th>Aux Heat</th><th>Total Cycles</th></tr></thead><tbody>"
            
            def keys = state.runHistory?.keySet()?.sort()?.reverse()
            if (keys) {
                keys.each { date ->
                    def data = state.runHistory[date]
                    def cMins = data.cool ?: 0.0; def hMins = data.heat ?: 0.0; def aMins = data.aux ?: 0.0
                    def cycles = data.runs ?: 0
                    def auxStyle = aMins > 0 ? "color:red; font-weight:bold;" : ""
                    
                    def maxT = data.maxOutTemp != null ? "${data.maxOutTemp}°" : "--"
                    def maxH = data.maxOutHum != null ? "${data.maxOutHum}%" : "--"
                    def weatherStr = (maxT == "--" && maxH == "--") ? "--" : "${maxT} / ${maxH}"
                    
                    runStatsHtml += "<tr><td>${date}</td><td>${weatherStr}</td><td>${String.format('%.1f', cMins/60.0)}h</td><td>${String.format('%.1f', hMins/60.0)}h</td><td style='${auxStyle}'>${String.format('%.1f', aMins/60.0)}h</td><td>${cycles}</td></tr>"
                }
                runStatsHtml += "</tbody></table>"
                paragraph runStatsHtml
            } else {
                paragraph "<i>No tracking data available yet.</i>"
            }
            input "resetHistory", "button", title: "Clear Tracking History"
        }

        section("<b>Recent Action History</b>", hideable: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>Last 10 Compressor Cycles</b>", hideable: true) {
            if (state.recentCycles) {
                def cycleStr = state.recentCycles.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${cycleStr}</span>"
            } else {
                paragraph "<i>No completed cycles logged yet.</i>"
            }
            input "resetCycles", "button", title: "Clear Cycle History"
        }

        section("<b>App Control & Main HVAC System</b>", hideable: true, hidden: true) {
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            input "thermostat", "capability.thermostat", title: "Select Main Thermostat", required: false, multiple: false
            if (state.manualHoldEnds && now() < state.manualHoldEnds) input "releaseHold", "button", title: "Release Manual Hold"
        }
        
        section("<b>Safety: Fire & Smoke Isolation</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Instantly shuts down the HVAC and blower if smoke or carbon monoxide is detected to prevent spreading smoke or feeding oxygen to a fire. This failsafe overrides all other logic.</div>"
            input "smokeDetectors", "capability.smokeDetector", title: "Select Smoke Detectors", required: false, multiple: true
            input "coDetectors", "capability.carbonMonoxideDetector", title: "Select CO Detectors", required: false, multiple: true
        }

        section("<b>Health: Sick Mode (Continuous Filtration)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> When the assigned switch is turned on, the app forces the HVAC fan to run 24/7 to continuously filter the air. Restores to Auto when turned off.</div>"
            input "sickModeSwitch", "capability.switch", title: "Select Sick Mode Switch 1", required: false, multiple: true
            input "sickModeSwitch2", "capability.switch", title: "Select Sick Mode Switch 2", required: false
            input "sickModeSwitch3", "capability.switch", title: "Select Sick Mode Switch 3", required: false
        }

        section("<b>1. App-Driven Auto Changeover</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Takes control of deciding whether your house needs Heating or Cooling away from the thermostat. It automatically swaps modes based on your home's <i>Calculated Average Temperature</i> rather than the single wall sensor.</div>"
            input "enableAutoSwap", "bool", title: "<b>Enable App-Driven Mode Swapping</b>", defaultValue: false, submitOnChange: true
            if (enableAutoSwap) {
                input "autoSwapDeadband", "decimal", title: "Changeover Deadband (°F) - Prevents rapid mode swapping", required: false, defaultValue: 1.0
            }
        }

        section("<b>2. Zones & Dynamic Occupancy (Global Settings)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Connects motion sensors to temperature sensors. If a room has no motion for the set timeout, it is mathematically dropped from the home's average temperature to stop wasting energy on empty rooms.</div>"
            input "enableOccupancy", "bool", title: "<b>Enable Dynamic Occupancy Weighting</b>", defaultValue: false, submitOnChange: true
            if (enableOccupancy) {
                input "occupancyTimeout", "number", title: "Minutes of no motion before dropping room (Debounce Timer)", required: false, defaultValue: 60
            }
            paragraph "<hr>"
            input "averagingModes", "mode", title: "<b>Modes to Use Room Averaging</b> (Leave blank for ALL modes. If current mode is not selected, the app will control strictly based on the main thermostat's temp sensor)", multiple: true, required: false
            paragraph "<div style='font-size:13px; color:#555;'>Click on a zone below to expand its settings.</div>"
        }

        for (int i = 1; i <= 5; i++) {
            def currentZoneName = settings["z${i}Name"] ?: "Zone ${i}"
            
            section("<b>⚙️ ${currentZoneName}</b>", hideable: true, hidden: true) {
                input "enableZ${i}", "bool", title: "<b>Enable Zone ${i}</b>", submitOnChange: true
                if (settings["enableZ${i}"]) {
                    input "z${i}Name", "text", title: "Zone Name", required: false, defaultValue: "Zone ${i}"
                    input "z${i}Temp", "capability.temperatureMeasurement", title: "Temp Sensor", required: false
                    input "z${i}Hum", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (Optional)", required: false
                    input "z${i}Motion", "capability.motionSensor", title: "Motion Sensor(s) (Optional)", required: false, multiple: true
                    input "z${i}NightSwitch", "capability.switch", title: "Good Night Virtual Switch (Keeps active in Night Mode)", required: false
                    input "z${i}EntertainmentSwitch", "capability.switch", title: "Entertainment Switch (Keeps active if ON)", required: false
                }
            }
        }

        section("<b>2b. Dynamic Setpoint Alignment & Deadband</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically shifts the physical thermostat's target to force it to run based on the Average Home Temp.</div>"
            input "enableAverageSync", "bool", title: "<b>Enable Dynamic Setpoint Alignment</b>", defaultValue: false, submitOnChange: true
            if (enableAverageSync) {
                input "maxSyncOffset", "decimal", title: "Maximum Allowed Shift (°F) - Safety limit", required: false, defaultValue: 3.0
            }

            paragraph "<b>Smart Debounce (Anti-Yo-Yo Delay)</b>"
            paragraph "<div style='font-size:13px; color:#555;'>Prevents short-cycling by temporarily gliding the setpoint right after a cycle ends. Works globally with or without averaging.</div>"
            input "yoyoCooldownMins", "number", title: "Anti-Yo-Yo Cooldown (Minutes) [Set to 0 to disable]", required: false, defaultValue: 15
            
            paragraph "<b>Stage 1: Smart Deadband & Hysteresis (Energy Saver)</b>"
            paragraph "<div style='font-size:13px; color:#555;'>Prevents micro-cycling. E.g., if setpoint is 70° and allowed drift is 1.0°, the system ignores the average until it hits 71.0°, then cools until it recovers to 70.5°.</div>"
            input "enableHysteresis", "bool", title: "<b>Enable Stage 1 Hysteresis Deadband</b>", defaultValue: true, submitOnChange: true
            if (enableHysteresis) {
                input "globalHysteresis", "bool", title: "<b>Enable Global Hysteresis</b> (Apply money-saving drift to Main Thermostat even when Room Averaging is disabled)", defaultValue: true
                input "hysteresisDrift", "decimal", title: "Allowed Drift Before Starting (°F)", required: false, defaultValue: 1.0
                input "hysteresisRecovery", "decimal", title: "Stop When Within X° of Setpoint", required: false, defaultValue: 0.5
            }
        }

        section("<b>3. Heat Pump: Aux Heat Suppression</b>", hideable: true, hidden: true) {
             paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Tricks thermostats into NOT using expensive Aux heat. It does this by 'gliding' the setpoint up 1 degree at a time, keeping the target just out of reach of the thermostat's internal Aux-trigger threshold.</div>"
            input "enableAuxSuppression", "bool", title: "<b>Enable Aux Heat Suppression</b>", defaultValue: false, submitOnChange: true
            if (enableAuxSuppression) {
                input "maxHeatStep", "decimal", title: "Max Setpoint Step (°F) (Keep below your thermostat's Aux threshold, usually 2°)", required: false, defaultValue: 1.5
            }
        }

        section("<b>4. Open Window / Door Defeat</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically intercepts and shuts off the HVAC if a monitored door or window is left open past the delay threshold. Restores normal operation once closed.</div>"
            input "enableWindowDefeat", "bool", title: "<b>Enable Window/Door Defeat</b>", defaultValue: true, submitOnChange: true
            if (enableWindowDefeat) {
                input "contactSensors", "capability.contactSensor", title: "Select Perimeter Contact Sensors", required: false, multiple: true
                input "contactDelay", "number", title: "Minutes to wait before shutting off HVAC", required: false, defaultValue: 3
            }
        }

        section("<b>5. Smart Filter & Maintenance Tracking</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Tracks exact blower runtime multiplied by air quality dust conditions to accurately predict filter life, logs physical service dates, and stores your HVAC technician's contact info.</div>"
            input "enableFilterTracker", "bool", title: "<b>Enable Maintenance Tracking Logic</b>", defaultValue: true, submitOnChange: true
            if (enableFilterTracker) {
                input "filterSize", "text", title: "Filter Size", required: false
                input "maxFilterHours", "number", title: "Baseline Filter Life (Fan Run Hours)", required: false, defaultValue: 300
                input "indoorIAQ", "capability.airQuality", title: "Indoor AQI Sensor", required: false
                input "outdoorIAQ", "capability.airQuality", title: "Outdoor AQI Sensor", required: false
                
                input "filterAlertSwitch", "capability.switch", title: "Filter Alert Virtual Switch (Turns ON for 10 mins daily randomly between 8AM-7PM when filter needs replacing)", required: false
                if (filterAlertSwitch) {
                    input "filterAlertModes", "mode", title: "Modes to allow Filter Alert Switch", multiple: true, required: false
                }
                
                if (state.filterRunMinutes != null) {
                    def maxMins = (maxFilterHours ?: 300) * 60
                    def usedMins = state.filterRunMinutes ?: 0.0
                    def percentLeft = Math.max(0.0, 100.0 - ((usedMins / maxMins) * 100)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                
                    paragraph "<b>Filter Life Remaining:</b> ${percentLeft}%"
                    paragraph "<b>Last Filter Change:</b> ${state.lastFilterDate ?: 'Not Recorded'}"
                }
                  
                paragraph "<hr>"
                paragraph "<b>HVAC System Service Tracking</b>"
                input "hvacCompanyName", "text", title: "HVAC Company Name", required: false
                input "hvacCompanyPhone", "text", title: "HVAC Company Phone Number", required: false
                paragraph "<b>Last HVAC Service:</b> ${state.lastServiceDate ?: 'Not Recorded'}"
            }
        }

        section("<b>6. Efficiency: Delta-T, Run Time Protection & Coasting</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> <b>1) Delta-T:</b> Monitors system health by measuring the temperature drop across your HVAC coil. <b>2) Run Time Protection:</b> Protects compressors from short-cycles. <b>3) Thermal Coasting:</b> Runs blower after AC stops to scavenge free cold air.</div>"
            input "enableDeltaT", "bool", title: "<b>Enable Delta-T Logic</b>", defaultValue: true, submitOnChange: true
            if (enableDeltaT) {
                input "returnSensor", "capability.temperatureMeasurement", title: "Return Air Sensor", required: false
                input "dischargeSensor", "capability.temperatureMeasurement", title: "Discharge (Supply) Air Sensor", required: false
                
                input "deltaTCheckDelay", "number", title: "Minutes before checking Delta-T", required: false, defaultValue: 30
                input "minCoolingDeltaT", "decimal", title: "Min Cooling Delta-T (°F)", required: false, defaultValue: 12.0
                input "minHeatingDeltaT", "decimal", title: "Min Heating Delta-T (°F)", required: false, defaultValue: 15.0
                input "emergencyShutoff", "bool", title: "Emergency Shutoff if Delta-T fails", defaultValue: false
            }
            
            paragraph "<b>Oversized Unit Protection</b>"
            input "enableMinRuntime", "bool", title: "<b>Enable Min Run Time Protection</b>", defaultValue: true, submitOnChange: true
         
            if (enableMinRuntime) {
                input "summerMinRunTime", "number", title: "Summer Min Run Time (Cooling) (minutes)", required: false, defaultValue: 25
                input "winterMinRunTime", "number", title: "Winter Min Run Time (Heating) (minutes)", required: false, defaultValue: 15
                input "delayModeChangeForMinRun", "bool", title: "Delay Mode Change Setpoints until min run time completes", defaultValue: false
                input "tempDropThreshold", "decimal", title: "Max Temp Drop per Min", required: false, defaultValue: 0.5
                input "setpointBuffer", "decimal", title: "Temporary Setpoint Buffer (°F)", required: false, defaultValue: 2.0
                input "shortCycleThreshold", "decimal", title: "Short-Cycle Degree Threshold (°F)", required: false, defaultValue: 1.0
            }

            paragraph "<b>Thermal Momentum (Smart Coasting)</b>"
            input "enableThermalCoasting", "bool", title: "<b>Enable Thermal Coasting</b> (Runs blower after cycle completes to harvest remaining coil temperature)", defaultValue: true, submitOnChange: true
            if (enableThermalCoasting) {
                input "coastingDurationMins", "number", title: "Minutes to run Blower after Compressor stops", required: false, defaultValue: 3
            }

            paragraph "<b>Hardware Trip & Deadlock Protections</b>"
            input "enableTripFailsafe", "bool", title: "<b>Enable Hardware Trip Failsafe</b> (Protects against repeated short-cycles)", defaultValue: true, submitOnChange: true
            if (enableTripFailsafe) {
                input "tripCycleCount", "number", title: "Max Short-Cycles before Trip", required: false, defaultValue: 3
                input "tripWindowMins", "number", title: "Rolling Window (Minutes)", required: false, defaultValue: 60
            }
            input "enableDeadlock", "bool", title: "<b>Enable Setpoint Deadlock Failsafe</b> (Protects against stuck targets)", defaultValue: true, submitOnChange: true
            if (enableDeadlock) {
                input "deadlockTimeoutMins", "number", title: "Max Minutes allowed deviated from Base", required: false, defaultValue: 120
            }
        }

        section("<b>7. Routine Setpoint Enforcement</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Acts as a Self-Healing loop. Periodically wakes up and re-transmits the correct setpoints to the thermostat to ensure no wireless commands were dropped by your Z-Wave/Zigbee mesh network.</div>"
            input "enableEnforcement", "bool", title: "<b>Enable Routine Enforcement</b>", defaultValue: false, submitOnChange: true
            if (enableEnforcement) {
                input "enforcementInterval", "enum", title: "Check Interval", options: ["15":"Every 15 Minutes", "30":"Every 30 Minutes", "60":"Every 1 Hour"], required: false, defaultValue: "30"
            }
        }
        
        section("<b>8. Environment & Scoring</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Computes comfort and efficiency scores based on indoor vs. outdoor conditions and system health. Ensure you select an outdoor sensor to enable the Delta-T and Scoring features on the dashboard.</div>"
            input "userState", "enum", title: "Select Your US State (Optimizes Baseline Humidity)", options: ["AL":"Alabama", "AK":"Alaska", "AZ":"Arizona", "AR":"Arkansas", "CA":"California", "CO":"Colorado", "CT":"Connecticut", "DE":"Delaware", "FL":"Florida", "GA":"Georgia", "HI":"Hawaii", "ID":"Idaho", "IL":"Illinois", "IN":"Indiana", "IA":"Iowa", "KS":"Kansas", "KY":"Kentucky", "LA":"Louisiana", "ME":"Maine", "MD":"Maryland", "MA":"Massachusetts", "MI":"Michigan", "MN":"Minnesota", "MS":"Mississippi", "MO":"Missouri", "MT":"Montana", "NE":"Nebraska", "NV":"Nevada", "NH":"New Hampshire", "NJ":"New Jersey", "NM":"New Mexico", "NY":"New York", "NC":"North Carolina", "ND":"North Dakota", "OH":"Ohio", "OK":"Oklahoma", "OR":"Oregon", "PA":"Pennsylvania", "RI":"Rhode Island", "SC":"South Carolina", "SD":"South Dakota", "TN":"Tennessee", "TX":"Texas", "UT":"Utah", "VT":"Vermont", "VA":"Virginia", "WA":"Washington", "WV":"West Virginia", "WI":"Wisconsin", "WY":"Wyoming"], required: false, submitOnChange: true
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor", required: false
            input "outdoorHumSensor", "capability.relativeHumidityMeasurement", title: "Outdoor Humidity Sensor (Optional)", required: false
        }

        section("<b>9. Psychological Comfort (Mood Sync)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Reads the mood of up to 3 users from the Advanced House Management dashboard. If the configured number of users are stressed, angry, or sick, the system automatically drops the cooling setpoint by 2°F to provide immediate physical relief.</div>"
            input "userName1", "text", title: "User 1 Name", defaultValue: "Shane", required: false
            input "moodVarU1", "hubVariable", title: "User 1 Mood Variable", required: false
            input "userName2", "text", title: "User 2 Name", defaultValue: "Christy", required: false
            input "moodVarU2", "hubVariable", title: "User 2 Mood Variable", required: false
            input "userName3", "text", title: "User 3 Name", defaultValue: "Leanne", required: false
            input "moodVarU3", "hubVariable", title: "User 3 Mood Variable", required: false
            
            input "moodMuteThreshold", "enum", title: "How many users must be negative to trigger the setpoint offset?", options: ["1":"1 User (Anyone)", "2":"2 Users", "3":"3 Users"], defaultValue: "2"
        }

        section("<b>Base Operating Modes & Ranges</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The foundation of the BMS. Sets your default targets based on Hubitat Location Modes. <i>Note: 'Good Night' mode strictly locks these temperatures for maximum comfort, bypassing economy features.</i></div>"
            paragraph "<i>Leave 'Allowed Modes (Overall App)' BLANK to allow the app to run 24/7. Otherwise, make sure to select every single mode you want the app to function in.</i>"
            input "allowedModes", "mode", title: "Allowed Modes (Overall App) [Master Override]", multiple: true, required: false
            
            paragraph "<b>Home</b>"
            input "homeModes", "mode", title: "Select 'Home' modes (If left blank, these settings act as the default fallback)", multiple: true, required: false
            input "homeCoolingSetpoint", "decimal", title: "Home Cooling Setpoint", required: false, defaultValue: 74
            input "homeHeatingSetpoint", "decimal", title: "Home Heating Setpoint", required: false, defaultValue: 68
            
            paragraph "<b>Away</b>"
            input "awayModes", "mode", title: "Select 'Away' modes", multiple: true, required: false
            input "awayCoolingSetpoint", "decimal", title: "Away Cooling Setpoint", required: false, defaultValue: 78
            input "awayHeatingSetpoint", "decimal", title: "Away Heating Setpoint", required: false, defaultValue: 62
            
            input "enableDeepAway", "bool", title: "<b>Enable Deep Away Setback (Dynamic)</b>", defaultValue: true, submitOnChange: true
            if (enableDeepAway) {
                input "deepAwayDelayHours", "number", title: "Hours unoccupied before dropping to Deep Setback", required: false, defaultValue: 4
                input "deepAwayCoolingSetpoint", "decimal", title: "Deep Away Cooling Setpoint", required: false, defaultValue: 82
                input "deepAwayHeatingSetpoint", "decimal", title: "Deep Away Heating Setpoint", required: false, defaultValue: 58
            }
 
            input "enableExtendedArrival", "bool", title: "<b>Enable Extended Arrival Cooling</b> (Pulls down thermal mass after long absences)", defaultValue: true, submitOnChange: true
            if (enableExtendedArrival) {
                input "eaAwayHours", "number", title: "Minimum Away Time (Hours)", required: false, defaultValue: 5
                input "eaTempDiff", "decimal", title: "Degrees above target to trigger", required: false, defaultValue: 4.0
                input "eaRunMins", "number", title: "Forced Cooling Duration (Minutes)", required: false, defaultValue: 45
                input "eaHardFloor", "decimal", title: "Hard Floor Limit (°F) - Abort if house hits this temp", required: false, defaultValue: 70.0
            }
            
            paragraph "<b>Good Night (Strict)</b>"
            input "nightModes", "mode", title: "Select 'Good Night' modes", multiple: true, required: false
            input "nightCoolingSetpoint", "decimal", title: "Good Night Cooling Setpoint", required: false, defaultValue: 70
            input "nightHeatingSetpoint", "decimal", title: "Good Night Heating Setpoint", required: false, defaultValue: 66
        }

        section("<b>Alerts & Routine Notifications</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Centralized notification hub. The app will quietly monitor system health and only notify you when routine maintenance is required or if critical efficiency drops are detected.</div>"
            input "notifyDevices", "capability.notification", title: "Select Notification Devices", required: false, multiple: true
            input "notifyDeltaT", "bool", title: "Notify on Bad Delta-T (Poor Efficiency/Freezing Coil)", defaultValue: true
            input "notifyFilter", "bool", title: "Notify when Filter Life drops below threshold", defaultValue: true
            input "filterNotifyThreshold", "number", title: "Filter Life Alert Threshold (%)", required: false, defaultValue: 10
            input "notifyMaintenance", "bool", title: "Notify for Summer/Winter Maintenance Reminders", defaultValue: true
            input "enableShortCycleNotify", "bool", title: "Notify on Short-Cycle (Hardware Protection Triggered)", defaultValue: false, submitOnChange: true
        }
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
    if (state.filterRunMinutes == null) state.filterRunMinutes = 0.0
    if (!state.zoneLastActive) state.zoneLastActive = [:]
    if (!state.runHistory) state.runHistory = [:]
    
    // Initialize Failsafe & Logic Trackers
    if (!state.shortCycleLog) state.shortCycleLog = []
    state.deviationStartTime = null
    state.rebootLockoutEnds = null
    state.awayStartTime = null
    state.extendedArrivalEnds = null
    state.coastingEnds = null
    
    if (!state.lastFilterDate) state.lastFilterDate = "Not Recorded"
    if (!state.lastServiceDate) state.lastServiceDate = "Not Recorded"
 
    state.isBuffering = false; state.cycleStartTime = null; state.currentAction = "idle"; state.cycleStartMode = null; state.modeDelayLogged = false
    state.manualHoldEnds = null; state.windowOpenHold = false; state.fireEmergency = false
    state.currentCycleMaxDeltaT = null
    state.lastCycleMode = null
    
    state.expectedCool = null; state.expectedHeat = null
    state.alignmentLockout = null; state.alignmentLockoutTarget = null
    state.activeHysteresis = "idle"
    state.lastCommandTime = null
    if (state.moodCoolingActive == null) state.moodCoolingActive = false
    
    if (thermostat) {
        subscribe(thermostat, "thermostatOperatingState", hvacStateHandler)
        subscribe(thermostat, "coolingSetpoint", setpointHandler)
        subscribe(thermostat, "heatingSetpoint", setpointHandler)
        subscribe(thermostat, "temperature", sensorHandler) 
    }
    
    if (enableDeltaT) {
        if (returnSensor) subscribe(returnSensor, "temperature", sensorHandler)
        if (dischargeSensor) subscribe(dischargeSensor, "temperature", sensorHandler)
    }
    
    // Subscribe to outdoor weather continuously to catch the daily maxes
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", weatherTrackingHandler)
    if (outdoorHumSensor) subscribe(outdoorHumSensor, "humidity", weatherTrackingHandler)
    
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", hubRestartHandler)
    
    if (smokeDetectors) subscribe(smokeDetectors, "smoke", smokeCoHandler)
    if (coDetectors) subscribe(coDetectors, "carbonMonoxide", smokeCoHandler)
    
    if (sickModeSwitch) subscribe(sickModeSwitch, "switch", sickModeHandler)
    if (sickModeSwitch2) subscribe(sickModeSwitch2, "switch", sickModeHandler)
    if (sickModeSwitch3) subscribe(sickModeSwitch3, "switch", sickModeHandler)
    
    if (moodVarU1) subscribe(location, "variable:${moodVarU1}", "moodChangeHandler")
    if (moodVarU2) subscribe(location, "variable:${moodVarU2}", "moodChangeHandler")
    if (moodVarU3) subscribe(location, "variable:${moodVarU3}", "moodChangeHandler")
    
    for (int i = 1; i <= 5; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", sensorHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler)
            // Note: Entertainment Switch does not require subscription, it is polled on evaluation
        }
    }
    
    if (enableWindowDefeat && contactSensors) subscribe(contactSensors, "contact", contactHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", enableSwitchHandler)
    
    schedule("0 0 10 * * ?", dailyMaintenanceCheck) 
    schedule("0 0 8 * * ?", scheduleRandomFilterAlert)
    
    if (enableEnforcement) {
        def interval = enforcementInterval ?: "30"
        if (interval == "15") runEvery15Minutes(routineSweep)
        else if (interval == "30") runEvery30Minutes(routineSweep)
        else if (interval == "60") runEvery1Hour(routineSweep)
    }
    
    runEvery5Minutes(sickModeEnforcer)
    
    logAction("App Initialized. Modular BMS Engine Ready.")
    
    // Check initial smoke/co status
    smokeCoHandler([:])
    evaluateSystem()
}

def weatherTrackingHandler(evt) {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.runHistory) state.runHistory = [:]
    
    // Ensure structure exists for the current day
    if (!state.runHistory[today]) {
        state.runHistory[today] = [cool: 0.0, heat: 0.0, aux: 0.0, runs: 0, maxRun: 0.0, minRun: 9999.0, maxOutTemp: null, maxOutHum: null]
    }
    
    def val = evt.value?.toBigDecimal()
    if (val != null) {
        if (evt.name == "temperature") {
            if (state.runHistory[today].maxOutTemp == null || val > state.runHistory[today].maxOutTemp) {
                state.runHistory[today].maxOutTemp = val
            }
        } else if (evt.name == "humidity") {
            if (state.runHistory[today].maxOutHum == null || val > state.runHistory[today].maxOutHum) {
                state.runHistory[today].maxOutHum = val
            }
        }
    }
}

def routineSweep() {
    if (state.fireEmergency || state.windowOpenHold || state.isBuffering) return 
    logAction("Running routine setpoint enforcement sweep.")
    evaluateSystem()
}

def sickModeEnforcer() {
    if (state.fireEmergency || !thermostat) return

    def sickSwitches = []
    if (sickModeSwitch) sickSwitches += (sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch])
    if (sickModeSwitch2) sickSwitches << sickModeSwitch2
    if (sickModeSwitch3) sickSwitches << sickModeSwitch3
    def anyOn = sickSwitches.any { it?.currentValue("switch") == "on" }

    if (anyOn) {
        def tstatFan = thermostat.currentValue("thermostatFanMode")?.toUpperCase()
        if (tstatFan != "ON" && tstatFan != "CIRCULATE") {
            logAction("Sick Mode Enforcer: Detected Fan is ${tstatFan} while Sick Mode is ACTIVE. Re-sending Fan ON command.")
            thermostat.setThermostatFanMode("on")
        }
    }
}

def hubRestartHandler(evt) {
    state.rebootLockoutEnds = now() + 300000 // 5-minute compressor lockout
    logAction("CRITICAL: Hub reboot or power cycle detected. Enforcing a 5-minute compressor lockout to allow physical HVAC pressures to equalize.")
    
    def tstatState = thermostat?.currentValue("thermostatOperatingState")?.toLowerCase()
    def isRunning = tstatState == "cooling" || tstatState == "heating"
    
    if (isRunning && state.cycleStartTime != null) {
        logAction("Recovery: HVAC is actively running. Preserving cycle timer to maintain Compressor Protection.")
    } else {
        state.cycleStartTime = null
        state.currentAction = "idle"
        state.cycleStartMode = null
    }
    
    state.isBuffering = false; state.windowOpenHold = false; 
    state.modeDelayLogged = false
    state.alignmentLockout = null; state.alignmentLockoutTarget = null
    state.activeHysteresis = "idle"
    state.deviationStartTime = null
    state.coastingEnds = null
    state.extendedArrivalEnds = null
    state.yoyoCooldownEnds = null 
 
    unschedule()
  
    schedule("0 0 10 * * ?", dailyMaintenanceCheck)
    schedule("0 0 8 * * ?", scheduleRandomFilterAlert)
    
    if (isRunning && enableMinRuntime) {
        runIn(60, compressorWatchdog)
    }
    
    if (enableEnforcement) {
        def interval = enforcementInterval ?: "30"
        if (interval == "15") runEvery15Minutes(routineSweep)
        else if (interval == "30") runEvery30Minutes(routineSweep)
        else if (interval == "60") runEvery1Hour(routineSweep)
    }
    
    runEvery5Minutes(sickModeEnforcer)
    
    smokeCoHandler([:])
    runIn(300, executePostRebootEval)
}

def executePostRebootEval() {
    logAction("Hub Reboot 5-minute lockout complete. Restoring normal BMS operations.")
    evaluateSystem()
}

def humidityHandler(evt) {
    evaluateSystem() 
}

def smokeCoHandler(evt) {
    def isFire = false
    if (smokeDetectors && smokeDetectors.any { it.currentValue("smoke") == "detected" }) isFire = true
    if (coDetectors && coDetectors.any { it.currentValue("carbonMonoxide") == "detected" }) isFire = true
    
    if (isFire && !state.fireEmergency) {
        state.fireEmergency = true
        logAction("CRITICAL EMERGENCY: Smoke/CO detected! Executing HVAC Fire Isolation.")
        
        if (thermostat) {
            thermostat.off()
            thermostat.setThermostatFanMode("auto")
        }
        
        if (notifyDevices) notifyDevices.deviceNotification("CRITICAL: Smoke/CO detected. HVAC shut down to prevent smoke spread.")
        evaluateSystem()
    } else if (!isFire && state.fireEmergency) {
        state.fireEmergency = false
        logAction("Emergency Cleared: Smoke/CO no longer detected. Releasing Fire Isolation.")
        evaluateSystem()
    }
}

def sickModeHandler(evt) {
    if (state.fireEmergency || !thermostat) return 
    
    def sickSwitches = []
    if (sickModeSwitch) sickSwitches += (sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch])
    if (sickModeSwitch2) sickSwitches << sickModeSwitch2
    if (sickModeSwitch3) sickSwitches << sickModeSwitch3
    def anyOn = sickSwitches.any { it?.currentValue("switch") == "on" }
   
    if (anyOn) {
        logAction("Sick Mode Activated: Forcing HVAC Fan ON for continuous filtration.")
        thermostat.setThermostatFanMode("on")
        runIn(15, verifySickModeFan, [data: [expected: "on", attempt: 1]])
    } else {
        logAction("Sick Mode Deactivated: Restoring HVAC Fan to Auto.")
        thermostat.setThermostatFanMode("auto")
        runIn(15, verifySickModeFan, [data: [expected: "auto", attempt: 1]])
    }
    evaluateSystem()
}

def moodChangeHandler(evt) {
    logAction("Mood logic triggered. Variable updated to ${evt.value}. Re-evaluating psychological auto-regulation.")
    evaluateSystem()
}

def verifySickModeFan(data) {
    if (state.fireEmergency || !thermostat) return
    def tstatFan = thermostat.currentValue("thermostatFanMode")?.toLowerCase()
    
    if (data.expected == "on" && tstatFan != "on" && tstatFan != "circulate") {
        if (data.attempt <= 3) {
            logAction("WARNING: Thermostat rejected Fan ON command (Attempt ${data.attempt}/3). Retrying...")
            thermostat.setThermostatFanMode("on")
            runIn(15, verifySickModeFan, [data: [expected: "on", attempt: data.attempt + 1]])
        } else {
            logAction("Sick Mode Error: Thermostat continues to reject Fan ON command. System will automatically retry every 5 minutes and immediately upon HVAC Idle via the Enforcer.")
        }
    } else if (data.expected == "auto" && tstatFan != "auto") {
         if (data.attempt <= 3) {
            logAction("WARNING: Thermostat rejected Fan AUTO command (Attempt ${data.attempt}/3). Retrying...")
            thermostat.setThermostatFanMode("auto")
            runIn(15, verifySickModeFan, [data: [expected: "auto", attempt: data.attempt + 1]])
        }
    }
}

String getHumanReadableStatus() {
    def status = ""
    
    def sickSwitches = []
    if (sickModeSwitch) sickSwitches += (sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch])
    if (sickModeSwitch2) sickSwitches << sickModeSwitch2
    if (sickModeSwitch3) sickSwitches << sickModeSwitch3
    def isSickMode = sickSwitches.any { it?.currentValue("switch") == "on" }
    def sickStr = isSickMode ? "<br><span style='color:#17a2b8;'><b>Health:</b> Sick Mode Active (Continuous Fan Filtration)</span>" : ""
    
    if (state.fireEmergency) {
        return "<span style='color:red; font-size:14px;'><b>🚨 CRITICAL: FIRE / CO ISOLATION ACTIVE. HVAC SHUT DOWN. 🚨</b></span>" + sickStr
    }
    
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") status = "The application is disabled via the Master Switch."
    else if (allowedModes && !(allowedModes as List).contains(location.mode)) status = "<span style='color:orange;'><b>App Disabled by Mode:</b></span> The current location mode (<b>${location.mode}</b>) is not selected in your 'Allowed Modes' setting."
    else if (state.rebootLockoutEnds && now() < state.rebootLockoutEnds) status = "<span style='color:red;'><b>Hub Reboot Lockout:</b></span> Allowing HVAC pressures to equalize. Resumes in ${Math.round((state.rebootLockoutEnds - now()) / 60000)} minutes."
    else if (state.windowOpenHold) status = "<span style='color:red;'><b>HVAC is OFF</b></span> because a monitored perimeter window or door is open."
    else if (state.manualHoldEnds && now() < state.manualHoldEnds) status = "<span style='color:orange;'><b>Automation Paused (Manual Hold):</b></span> Resumes in ${Math.round((state.manualHoldEnds - now()) / 60000)} minutes."
    else if (state.isBuffering) {
        def runMins = state.cycleStartTime ? (now() - state.cycleStartTime) / 60000.0 : 0
        def remaining = Math.max(0, Math.round(getMinRunTime() - runMins))
        status = "<span style='color:blue;'><b>Compressor Protection Engaged:</b></span> The system is locked ON to satisfy the Minimum Run Time and prevent hardware damage. (${remaining} mins remaining until shut down allowed)"
    }
    else if (state.yoyoCooldownEnds && now() < state.yoyoCooldownEnds.toLong() && state.currentAction == "idle" && (yoyoCooldownMins == null || yoyoCooldownMins.toInteger() > 0)) status = "<span style='color:orange;'><b>Smart Debounce Active:</b></span> Actively locking out targets to prevent short cycling."
    else if (state.extendedArrivalEnds && now() < state.extendedArrivalEnds) status = "<span style='color:blue;'><b>Extended Arrival Cooling Active:</b></span> Stripping heat from thermal mass. Resumes normal operation in ${Math.round((state.extendedArrivalEnds - now()) / 60000)} minutes (or if temp hits ${eaHardFloor ?: 70.0}°)."
    else {
        def mode = thermostat?.currentValue("thermostatOperatingState")?.toLowerCase()
        if (mode?.contains("aux") || mode?.contains("emergency")) status = "<span style='color:red;'><b>WARNING: Auxiliary Heat is Active.</b></span> The system is currently running the high-power resistance heat strips."
        else {
            def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
            def isAway = awayModes ? (awayModes as List).contains(location.mode) : false
            
            if (isNight) status = "Good Night mode is active. Setpoints strictly locked."
            else if (isAway && enableDeepAway && state.awayStartTime && (now() - state.awayStartTime) >= ((deepAwayDelayHours ?: 4) * 3600000)) {
                status = "<span style='color:blue;'><b>Deep Away Setback Active:</b></span> House is unoccupied for extended duration. Setpoints deeply reduced to save energy."
            }
            else if (mode == "cooling" || mode == "heating") status = "Operating normally. Currently ${mode} to satisfy the average room requirements."
            else if (state.coastingEnds && now() < state.coastingEnds) status = "<span style='color:green;'><b>Thermal Coasting Active:</b></span> Scavenging free cold/heat from coils with blower fan."
            else status = "System is IDLE. Averaged zone temperatures are currently within the comfort range."
        }
    }
    
    return status + sickStr
}

def getAverageTemp() {
    if (averagingModes && !(averagingModes as List).contains(location.mode)) {
        return thermostat?.currentValue("temperature") != null ? thermostat.currentValue("temperature").toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0
    }

    def total = 0.0; def count = 0
    def timeoutMs = (occupancyTimeout ?: 60) * 60000
    def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
    def maxAgeMs = 24 * 60 * 60 * 1000 
    
    for (int i = 1; i <= 5; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Temp"]) {
            def tempDev = settings["z${i}Temp"]
            def motionDevs = settings["z${i}Motion"]
            def tempState = tempDev.currentState("temperature")
        
            def entSwitch = settings["z${i}EntertainmentSwitch"]
            def isEntForced = entSwitch && entSwitch.currentValue("switch") == "on"
            
            if (tempState != null && tempState.value != null) {
                def tVal = tempState.value.toBigDecimal()
                def lastUpdate = tempState.date?.time ?: now()
  
                if (tVal >= 40.0 && tVal <= 100.0 && (now() - lastUpdate) <= maxAgeMs) {
                    if (isNight) {
                        def nightSwitch = settings["z${i}NightSwitch"]
             
                        if (nightSwitch && nightSwitch.currentValue("switch") == "on") {
                            total += tVal; count++
                        }
                    } else {
                        def isOccupied = false
                        if (motionDevs) {
 
                            def mList = motionDevs instanceof List ? motionDevs : [motionDevs]
                            isOccupied = mList.any { m -> state.zoneLastActive && state.zoneLastActive[m.id] && (now() - state.zoneLastActive[m.id]) < timeoutMs }
                        }
                        
                         if (!enableOccupancy || !motionDevs || isOccupied || isEntForced) {
                            total += tVal; count++
                        }
                    }
                } else {
                    logAction("WARNING: Ignored sensor ${tempDev.displayName} due to stale or out-of-bounds data (${tVal}°).")
                }
            }
        }
    }
    if (count == 0 && thermostat?.currentValue("temperature") != null) return thermostat.currentValue("temperature").toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    return count > 0 ? (total / count).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0
}

def getAverageHumidity() {
    if (averagingModes && !(averagingModes as List).contains(location.mode)) {
        return thermostat?.currentValue("humidity") != null ? thermostat.currentValue("humidity").toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0
    }

    def total = 0.0; def count = 0
    for (int i = 1; i <= 5; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Hum"]) {
            def humDev = settings["z${i}Hum"]
            if (humDev.currentValue("humidity") != null) { total += humDev.currentValue("humidity"); count++ }
        }
    }
    return count > 0 ? (total / count).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 0.0
}

def motionHandler(evt) { 
    if (evt.value == "active") { 
        if (!state.zoneLastActive) state.zoneLastActive = [:]
        state.zoneLastActive[evt.device.id] = now()
        evaluateSystem() 
    } 
}

def appButtonHandler(btn) {
    def todayStr = new Date().format("MM/dd/yyyy", location.timeZone)
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
    }
    else if (btn == "btnEnforceModes") {
        state.rebootLockoutEnds = null
        state.manualHoldEnds = null
        state.windowOpenHold = false
        state.isBuffering = false
        state.preBufferCool = null
        state.preBufferHeat = null
        unschedule(releaseBuffer)
        state.alignmentLockout = null
        state.activeHysteresis = "idle"
        state.yoyoCooldownEnds = null
        state.deviationStartTime = null
        state.expectedCool = null
        state.expectedHeat = null
        state.extendedArrivalEnds = null // Clear manual EA override
        logAction("User manually enforced mode setpoints. Clearing all temporary locks and forcing target re-evaluation.")
        evaluateSystem()
    }
    else if (btn == "resetFilter") { 
        state.filterRunMinutes = 0.0
        state.lastFilterDate = todayStr
        state.filterAlertSent = false
        
        // Interrupt filter alert switch if currently active
        if (filterAlertSwitch && filterAlertSwitch.currentValue("switch") == "on") {
            filterAlertSwitch.off()
        }
        unschedule(turnOffFilterAlertSwitch)
        
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
}

def enableSwitchHandler(evt) { if (evt.value == "off") logAction("App Disabled."); else evaluateSystem() }

def modeChangeHandler(evt) { 
    state.manualHoldEnds = null
    def isAway = awayModes ? (awayModes as List).contains(evt.value) : false
    
    if (isAway) {
        if (!state.awayStartTime) state.awayStartTime = now()
    } else {
        // --- EXTENDED ARRIVAL COOLING TRIGGER ---
        if (state.awayStartTime && enableExtendedArrival) {
            def awayHours = (now() - state.awayStartTime) / 3600000.0
            if (awayHours >= (eaAwayHours ?: 5)) {
                def currentTemp = getAverageTemp()
                def targetCool = homeCoolingSetpoint ?: 74.0
                
                if (currentTemp >= (targetCool + (eaTempDiff ?: 4.0))) {
                    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
                    
                    if (state.lastExtendedArrivalDate != todayStr) {
                        state.extendedArrivalEnds = now() + ((eaRunMins ?: 45) * 60000)
                        state.lastExtendedArrivalDate = todayStr
                        logAction("Extended Arrival Cooling Triggered! House was away for ${String.format('%.1f', awayHours)} hours and temp is ${currentTemp}°. Forcing ${eaRunMins ?: 45} mins of cooling.")
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

def setpointHandler(evt) {
    if (state.fireEmergency || state.windowOpenHold || state.isBuffering) return 
    
    // 15-second blindspot for incoming setpoint echoes right after the BMS sends a command.
    if (state.lastCommandTime && (now() - state.lastCommandTime) < 15000) {
        return 
    }
    
    def newVal = evt.value.toBigDecimal()
    def isManual = false
    
    if (evt.name == "coolingSetpoint" && state.expectedCool != null) {
        if (Math.abs(newVal - state.expectedCool) > 1.0) isManual = true
    }
    if (evt.name == "heatingSetpoint" && state.expectedHeat != null) {
        if (Math.abs(newVal - state.expectedHeat) > 1.0) isManual = true
    }
    
    if (isManual && (!state.manualHoldEnds || now() > state.manualHoldEnds)) { 
        state.manualHoldEnds = now() + 7200000 // 2 Hours Hold
        state.preBufferCool = null
        state.preBufferHeat = null
        logAction("MANUAL OVERRIDE: Physical thermostat changed to ${newVal}°. Automation suspended for 2 Hours.") 
        evaluateSystem() 
    } else if (isManual) {
        state.manualHoldEnds = now() + 7200000 // Reset 2 Hours Hold
        state.preBufferCool = null
        state.preBufferHeat = null
        logAction("MANUAL OVERRIDE: Setpoint adjusted again. 2-Hour hold timer reset.")
        evaluateSystem()
    }
}

def evaluateSystem() {
    if (!thermostat) return
    if (state.fireEmergency) {
        if (thermostat.currentValue("thermostatMode") != "off") thermostat.setThermostatMode("off")
        return
    }
    if (state.rebootLockoutEnds && now() < state.rebootLockoutEnds) return
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    if (state.windowOpenHold || state.isBuffering) return 
    
    // --- STUCK BUFFER FAILSAFE CHECK ---
    if (state.isBuffering && state.cycleStartTime) {
        if ((now() - state.cycleStartTime) / 60000.0 >= getMinRunTime()) {
             logAction("CRITICAL: Buffer time limit exceeded but system failed to release. Force clearing locks.")
             state.isBuffering = false
             state.preBufferCool = null
             state.preBufferHeat = null
             unschedule(releaseBuffer)
        } else {
             return // Wait for buffer to clear normally
        }
    }
    
    def evalMode = location.mode
    def modeHoldMsg = ""
    
    if (enableMinRuntime && delayModeChangeForMinRun && state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
        def runMins = (now() - state.cycleStartTime) / 60000.0
        if (runMins < getMinRunTime()) {
            if (state.cycleStartMode && state.cycleStartMode != location.mode) {
                evalMode = state.cycleStartMode
                modeHoldMsg = " [Mode Change Delayed: Finishing Compressor Protection]"
                if (!state.modeDelayLogged) {
                    logAction("Mode changed to ${location.mode}, but Compressor Protection is active. Simulating ${state.cycleStartMode} for the remaining run time.")
                    state.modeDelayLogged = true
                }
            }
        }
    }

    if (allowedModes && !(allowedModes as List).contains(evalMode)) return
    
    def isAway = awayModes ? (awayModes as List).contains(evalMode) : false
    def isNight = nightModes ? (nightModes as List).contains(evalMode) : false
    def isHome = homeModes ? (homeModes as List).contains(evalMode) : false
    def isSmartModeAllowed = !averagingModes || (averagingModes as List).contains(evalMode)
    
    if (isAway && state.awayStartTime == null) {
        state.awayStartTime = now()
    } else if (!isAway) {
        state.awayStartTime = null
    }

    def targetCool = homeCoolingSetpoint ?: 74.0; def targetHeat = homeHeatingSetpoint ?: 68.0
    def syncMessage = ""

    if (isNight) { 
        targetCool = nightCoolingSetpoint ?: 70.0
        targetHeat = nightHeatingSetpoint ?: 66.0 
    } else if (isAway) { 
        targetCool = awayCoolingSetpoint ?: 78.0
        targetHeat = awayHeatingSetpoint ?: 62.0 
        
        if (enableDeepAway) {
            def delayMs = (deepAwayDelayHours ?: 4) * 3600000
            if (state.awayStartTime && (now() - state.awayStartTime) >= delayMs) {
                targetCool = deepAwayCoolingSetpoint ?: 82.0
                targetHeat = deepAwayHeatingSetpoint ?: 58.0
                syncMessage = " [Deep Setback Active: >${deepAwayDelayHours ?: 4} Hrs]"
            }
        }
    } else if (isHome || (!isNight && !isAway)) {
        targetCool = homeCoolingSetpoint ?: 74.0
        targetHeat = homeHeatingSetpoint ?: 68.0 
    }
    
    def baseCool = targetCool
    def baseHeat = targetHeat
    
    // --- PSYCHOLOGICAL AUTO-REGULATION (MOOD) ---
    def targetNegativeMoods = ["🥵", "🤯", "🤬", "🤪", "🤢", "💩", "🤒", "🤕", "🤐"]
    def negCount = 0
    if (settings.moodVarU1 && targetNegativeMoods.contains(getGlobalVar(settings.moodVarU1)?.value?.toString())) negCount++
    if (settings.moodVarU2 && targetNegativeMoods.contains(getGlobalVar(settings.moodVarU2)?.value?.toString())) negCount++
    if (settings.moodVarU3 && targetNegativeMoods.contains(getGlobalVar(settings.moodVarU3)?.value?.toString())) negCount++
    
    def threshold = settings.moodMuteThreshold != null ? settings.moodMuteThreshold.toInteger() : 2
    
    if (negCount >= threshold) {
        targetCool = (targetCool - 2.0).toBigDecimal()
        baseCool = (baseCool - 2.0).toBigDecimal()
        syncMessage += " [Mood Auto-Reg: -2.0° Cooling Offset]"
        if (!state.moodCoolingActive) {
            state.moodCoolingActive = true
            logAction("Mood Auto-Regulation: >=${threshold} negative moods detected. Lowering cooling setpoint by 2°F.")
        }
    } else {
        if (state.moodCoolingActive) {
            state.moodCoolingActive = false
            logAction("Mood Auto-Regulation: Moods improved. Restoring normal cooling setpoints.")
        }
    }

    // --- EXTENDED ARRIVAL COOLING ENFORCEMENT ---
    def isEAActive = state.extendedArrivalEnds && now() < state.extendedArrivalEnds
    if (isEAActive) {
        def currentAvg = getAverageTemp()
        def hardFloor = eaHardFloor ?: 70.0
        
        if (currentAvg <= hardFloor) {
            logAction("Extended Arrival Cooling ABORTED: Hard floor of ${hardFloor}° reached.")
            state.extendedArrivalEnds = null
            isEAActive = false
        } else {
            // Force the target down to the floor to ensure continuous compressor run
            targetCool = hardFloor 
            syncMessage += " [Extended Arrival Active: Cooling Thermal Mass]"
        }
    } else if (state.extendedArrivalEnds && now() >= state.extendedArrivalEnds) {
         state.extendedArrivalEnds = null // Clear expired timer
         logAction("Extended Arrival Cooling successfully completed its runtime.")
    }

    def baseSwapDB = enableAutoSwap ? (autoSwapDeadband ?: 1.0) : 1.0
    def safeSwapDB = baseSwapDB
    
    def currentLocalTemp = thermostat.currentValue("temperature")?.toBigDecimal()
    def isManualHoldActive = state.manualHoldEnds && now() < state.manualHoldEnds

    if (isManualHoldActive) {
        // Retrieve physical targets, ignoring normal BMS automation changes
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
            def drift = hysteresisDrift ?: 1.0
            if (safeSwapDB <= drift) { safeSwapDB = drift + 0.5 }
        }
        
        def yoyoMins = yoyoCooldownMins != null ? yoyoCooldownMins.toInteger() : 15
        def isYoYoCooldown = yoyoMins > 0 && state.yoyoCooldownEnds && now() < state.yoyoCooldownEnds.toLong() && state.currentAction == "idle"
    
        // Stage 1: Hysteresis & Deadband Active State
        if (isHysAllowed && thermostat.currentValue("temperature") != null) {
            def currentAvg = getAverageTemp()
            def drift = hysteresisDrift ?: 1.0
            def recovery = hysteresisRecovery ?: 0.5
            
            if (state.activeHysteresis == null || state.activeHysteresis == "idle") {
                if (currentAvg >= (baseCool + drift)) {
                    state.activeHysteresis = "cooling"
                    logAction("Stage 1 Hysteresis: Temp drifted to ${currentAvg}° (+${drift}° limit). Initiating Cooling Recovery to ${baseCool + recovery}°.")
                } else if (currentAvg <= (baseHeat - drift)) {
                    state.activeHysteresis = "heating"
                    logAction("Stage 1 Hysteresis: Temp drifted to ${currentAvg}° (-${drift}° limit). Initiating Heating Recovery to ${baseHeat - recovery}°.")
                }
            } else if (state.activeHysteresis == "cooling") {
                 if (currentAvg <= (baseCool + recovery)) {
                    state.activeHysteresis = "idle"
                    logAction("Stage 1 Hysteresis: Cooled to ${currentAvg}° (Within ${recovery}° of target). Satisfied and entering Idle.")
                }
            } else if (state.activeHysteresis == "heating") {
                if (currentAvg >= (baseHeat - recovery)) {
                    state.activeHysteresis = "idle"
                    logAction("Stage 1 Hysteresis: Heated to ${currentAvg}° (Within ${recovery}° of target). Satisfied and entering Idle.")
                }
            }
        } else {
            state.activeHysteresis = "idle"
        }

        // Dynamic Setpoint Alignment (Or Pure Global Hysteresis enforcement)
        def isAlignmentActive = (enableAverageSync && isSmartModeAllowed)
        def runSetpointLogic = isAlignmentActive || isHysAllowed

        if (isYoYoCooldown && thermostat.currentValue("temperature") != null) {
            def tstatTemp = thermostat.currentValue("temperature").toBigDecimal()
            def yoyoRemainingMinsRaw = (state.yoyoCooldownEnds.toLong() - now()) / 60000.0
            def proportion = Math.max(0.0, Math.min(1.0, yoyoRemainingMinsRaw / yoyoMins))
            def glideOffset = (2.5 * proportion).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)

            if (state.lastCycleMode == "heating" || state.lastCycleMode == "auxHeating") {
                targetHeat = (tstatTemp - glideOffset).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                targetCool = (targetHeat + 3.0).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                syncMessage += " [Anti-Yo-Yo Gliding: Heat Target lowered to enforce delay (${Math.round(yoyoRemainingMinsRaw)}m | Offset: -${glideOffset}°)]"
            } else {
                targetCool = (tstatTemp + glideOffset).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                targetHeat = (targetCool - 3.0).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                syncMessage += " [Anti-Yo-Yo Gliding: Cool Target raised to enforce delay (${Math.round(yoyoRemainingMinsRaw)}m | Offset: +${glideOffset}°)]"
            }
        } else if (runSetpointLogic && thermostat.currentValue("temperature") != null) {
            if (state.alignmentLockout) {
                syncMessage += " [Alignment Suspended: Awaiting Temp Recovery]"
            } else {
                def tstatTemp = thermostat.currentValue("temperature").toBigDecimal()
                def currentAvg = getAverageTemp()
                
                if (enableHysteresis && state.activeHysteresis == "idle") {
                    targetCool = (tstatTemp + 1.5).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    targetHeat = (tstatTemp - 1.5).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    syncMessage += " [Stage 1: Floating in Deadband]"
                } else {
                    def offset = 0.0
                    if (isAlignmentActive) {
                        offset = (currentAvg - tstatTemp)
                    }
                    
                    def maxShift = maxSyncOffset ?: 3.0
                    
                    if (offset > maxShift) offset = maxShift
                    if (offset < -maxShift) offset = -maxShift
                
                    def calcCool = (targetCool - offset).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    def calcHeat = (targetHeat - offset).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
            
                    if (!enableHysteresis) {
                        def coolSnapped = false
                        if (calcCool < baseCool && currentAvg <= baseCool) coolSnapped = true
                        def heatSnapped = false
                        if (calcHeat > baseHeat && currentAvg >= baseHeat) heatSnapped = true
                    
                        if (coolSnapped || heatSnapped) {
                            calcCool = baseCool
                            calcHeat = baseHeat
                            syncMessage += " [Alignment Satisfied: System Idle, Snapped to Base]"
                        } else if (offset != 0.0) {
                            syncMessage += " [Alignment Active: Shifted by ${String.format('%.1f', -offset)}°]"
                        }
                    } else {
                        def hysMsg = state.activeHysteresis == "cooling" ? "[Stage 1: Active Cool Recovery]" : "[Stage 1: Active Heat Recovery]"
                        def shiftMsg = offset != 0.0 ? " (Shifted by ${String.format('%.1f', -offset)}°)" : ""
                        syncMessage += " ${hysMsg}${shiftMsg}"
                    }
                    
                    targetCool = calcCool
                    targetHeat = calcHeat
                    
                    // ANTI-YOYO CLAMP
                    if (targetCool <= (baseHeat + safeSwapDB)) {
                        targetCool = baseHeat + safeSwapDB + 1.0
                        targetHeat = targetCool - 3.0 
                        syncMessage += " [Clamped: Hit Heating Floor]"
                    }
                    else if (targetHeat >= (baseCool - safeSwapDB)) {
                        targetHeat = baseCool - safeSwapDB - 1.0
                        targetCool = targetHeat + 3.0 
                        syncMessage += " [Clamped: Hit Cooling Ceiling]"
                    }
                }
            }
        } else if (enableAverageSync && !isSmartModeAllowed && !isHysAllowed) {
            state.alignmentLockout = null
        }
        
        // AUX HEAT SUPPRESSION (GLIDING)
        if (enableAuxSuppression && currentLocalTemp != null) {
            def stepLimit = maxHeatStep ?: 1.5
            if (targetHeat > (currentLocalTemp + stepLimit)) {
                targetHeat = (currentLocalTemp + stepLimit).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                syncMessage += " [Aux Suppressed: Heat target glided to ${targetHeat}°]"
            }
        }
    }
    // --- END NORMAL AUTOMATION ENGINE ---

    // --- COMPRESSOR RUN TIME PROTECTION (Always Runs) ---
    if (enableMinRuntime && state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
        def runMins = (now() - state.cycleStartTime) / 60000.0
        if (runMins < getMinRunTime()) {
            
            def localTemp = thermostat.currentValue("temperature")?.toBigDecimal() ?: targetCool
            def buffer = setpointBuffer ?: 2.0
            
            if (state.currentAction == "cooling") {
                if (targetCool > thermostat.currentValue("coolingSetpoint").toBigDecimal()) {
                    targetCool = thermostat.currentValue("coolingSetpoint").toBigDecimal()
                    syncMessage += " [Compressor Protection: Lockout Prevented Setpoint Rise]"
                }
                if (targetCool >= localTemp) {
                    targetCool = localTemp - 1.0
                    def minAllowed = baseCool - buffer
                    
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
                        syncMessage += " [Compressor Protection: Pushed below local temp to maintain run]"
                    }
                }
            }
            else if (state.currentAction == "heating") {
                if (targetHeat < thermostat.currentValue("heatingSetpoint").toBigDecimal()) {
                    targetHeat = thermostat.currentValue("heatingSetpoint").toBigDecimal()
                    syncMessage += " [Compressor Protection: Lockout Prevented Setpoint Drop]"
                }
                if (targetHeat <= localTemp) {
                    targetHeat = localTemp + 1.0
                    def maxAllowed = baseHeat + buffer
 
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
                        syncMessage += " [Compressor Protection: Pushed above local temp to maintain run]"
                    }
                }
            }

            if (enableAutoSwap && !isManualHoldActive) {
                def minCoolFloor = baseHeat + safeSwapDB + 1.5 
                def maxHeatCeiling = baseCool - safeSwapDB - 1.5
                
                if (state.currentAction == "cooling" && targetCool < minCoolFloor) {
                    targetCool = minCoolFloor.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    syncMessage += " [Compressor Protection: Protected against Heat Swap]"
                }
                else if (state.currentAction == "heating" && targetHeat > maxHeatCeiling) {
                    targetHeat = maxHeatCeiling.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
                    syncMessage += " [Compressor Protection: Protected against Cool Swap]"
                }
            }
        }
    }
    
    // --- DEADLOCK FAILSAFE ---
    if (enableDeadlock && !isManualHoldActive) {
        def deadlockLimitMs = (deadlockTimeoutMins ?: 120) * 60000
        if (targetCool != baseCool || targetHeat != baseHeat) {
            if (!state.deviationStartTime) {
                state.deviationStartTime = now()
            } else if ((now() - state.deviationStartTime) > deadlockLimitMs) {
                
                // Check if we need to protect an active cycle before clearing locks
                def isRunning = state.currentAction in ["cooling", "heating"] && state.cycleStartTime != null
                def runMins = isRunning ? (now() - state.cycleStartTime) / 60000.0 : 999.0
                
                if (isRunning && runMins < getMinRunTime()) {
                    // Delay Deadlock to protect the compressor
                    syncMessage += " [Deadlock Delayed: Waiting for Min Run Time]"
                } else {
                    logAction("CRITICAL FAILSAFE: Setpoints have been artificially altered from the base mode target (Cool: ${baseCool}°, Heat: ${baseHeat}°) for over ${deadlockTimeoutMins ?: 120} minutes. Releasing all locks.")
                    state.deviationStartTime = null
                    if (!isRunning) state.cycleStartTime = null // Prevent wiping active run tracker
                    state.isBuffering = false
                    state.alignmentLockout = null
                    state.activeHysteresis = "idle"
                    state.yoyoCooldownEnds = null
                    unschedule(releaseBuffer)
                    
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
    def hardwareDeadband = 3.0
    if ((targetCool - targetHeat) < hardwareDeadband) {
        if (state.currentAction == "cooling") {
            targetHeat = (targetCool - hardwareDeadband).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
        } else if (state.currentAction == "heating") {
            targetCool = (targetHeat + hardwareDeadband).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
        } else {
            targetHeat = (targetCool - hardwareDeadband).toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP)
        }
        if (!syncMessage.contains("Deadband")) syncMessage += " [Final Deadband Enforced]"
    }
    
    if (thermostat.currentValue("coolingSetpoint") != targetCool || thermostat.currentValue("heatingSetpoint") != targetHeat) {
        state.expectedCool = targetCool; state.expectedHeat = targetHeat
        state.lastCommandTime = now()
        thermostat.setCoolingSetpoint(targetCool)
        thermostat.setHeatingSetpoint(targetHeat)
        logAction("BMS Command -> Pushing Setpoints to Thermostat: COOL ${targetCool}° | HEAT ${targetHeat}°${syncMessage}${modeHoldMsg}")
    }
    
    // Evaluated safely at root scope to avoid missing property exceptions from Groovy
    def yoyoCheckMins = yoyoCooldownMins != null ? yoyoCooldownMins.toInteger() : 15
    def isYoYoDelayActive = yoyoCheckMins > 0 && state.yoyoCooldownEnds && now() < state.yoyoCooldownEnds.toLong() && state.currentAction == "idle"
    
    if (enableAutoSwap && !isManualHoldActive && !isYoYoDelayActive) {
        def currentAvg = getAverageTemp()
        def tMode = thermostat.currentValue("thermostatMode")?.toLowerCase()
    
        if (tMode == "heat" || tMode == "cool" || tMode == "auto") {
            if (currentAvg >= (targetCool + safeSwapDB) && tMode != "cool") {
                logAction("BMS Command -> Auto-Swap triggered. Switching thermostat to COOL mode. (Temp: ${currentAvg}°, Target: ${targetCool}°, Safe DB: ${safeSwapDB}°)")
                thermostat.setThermostatMode("cool")
            } else if (currentAvg <= (targetHeat - safeSwapDB) && tMode != "heat") {
                logAction("BMS Command -> Auto-Swap triggered. Switching thermostat to HEAT mode. (Temp: ${currentAvg}°, Target: ${targetHeat}°, Safe DB: ${safeSwapDB}°)")
                thermostat.setThermostatMode("heat")
            }
        }
    }
}

def endThermalCoasting() {
    state.coastingEnds = null
    
    def sickSwitches = []
    if (sickModeSwitch) sickSwitches += (sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch])
    if (sickModeSwitch2) sickSwitches << sickModeSwitch2
    if (sickModeSwitch3) sickSwitches << sickModeSwitch3
    def isSickMode = sickSwitches.any { it?.currentValue("switch") == "on" }
    
    if (!isSickMode && thermostat) {
        logAction("Thermal Coasting complete. Restoring Fan to Auto.")
        thermostat.setThermostatFanMode("auto")
    }
}

def compressorWatchdog() {
    if (state.currentAction in ["cooling", "heating"] && state.cycleStartTime) {
        def runMins = (now() - state.cycleStartTime) / 60000.0
        if (runMins < getMinRunTime()) {
            evaluateSystem() 
            runIn(60, compressorWatchdog)
        } else {
            evaluateSystem() 
        }
    }
}

def contactHandler(evt) { 
    def anyOpen = contactSensors ? contactSensors.any { it.currentValue("contact") == "open" } : false
    if (anyOpen && !state.windowOpenHold) { 
        runIn((contactDelay ?: 3) * 60, executeWindowDefeat) 
    } else if (!anyOpen && state.windowOpenHold) { 
        logAction("Windows closed. Releasing HVAC Safety Defeat.")
        state.windowOpenHold = false; unschedule(executeWindowDefeat); evaluateSystem() 
    } else if (!anyOpen) unschedule(executeWindowDefeat) 
}

def executeWindowDefeat() { state.windowOpenHold = true; if (state.isBuffering) { state.isBuffering = false; unschedule(releaseBuffer) }; logAction("BMS Command -> Safety Defeat Active. Turning HVAC OFF."); thermostat.off() }

def hvacStateHandler(evt) {
    def stateVal = evt.value?.toLowerCase() ?: ""
    def incomingAction = (stateVal.contains("aux") || stateVal.contains("emergency")) ? "auxHeating" : stateVal
    
    if (state.currentAction == incomingAction && state.cycleStartTime != null) {
        logInfo("Ignored duplicate '${stateVal}' event to protect active cycle timer.")
        return 
    }

    if (stateVal == "cooling" || stateVal == "heating" || stateVal.contains("aux") || stateVal.contains("emergency")) {
        if (state.coastingEnds) {
            state.coastingEnds = null
            unschedule(endThermalCoasting)
            
            def sickSwitches = []
            if (sickModeSwitch) sickSwitches += (sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch])
            if (sickModeSwitch2) sickSwitches << sickModeSwitch2
            if (sickModeSwitch3) sickSwitches << sickModeSwitch3
            def isSickMode = sickSwitches.any { it?.currentValue("switch") == "on" }
            
            if (!isSickMode) thermostat.setThermostatFanMode("auto")
        }
        
        state.cycleStartTime = now()
        state.cycleStartMode = location.mode
        state.modeDelayLogged = false
        state.startTemp = thermostat.currentValue("temperature")
        state.currentCycleMaxDeltaT = null // Reset peak Delta-T tracker
        
        if (stateVal.contains("aux") || stateVal.contains("emergency")) { 
            state.currentAction = "auxHeating" 
        } else { 
            state.currentAction = stateVal 
        }
        state.isBuffering = false
        state.yoyoCooldownEnds = null // FIX: Instantly clear debounce timer if unit physically starts cooling/heating
        
        def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
        
        if (enableMinRuntime && !isNight && state.currentAction in ["cooling", "heating"]) {
            
            runIn(60, compressorWatchdog)
            
            def activeSetpoint = (state.currentAction == "cooling") ? thermostat.currentValue("coolingSetpoint") : thermostat.currentValue("heatingSetpoint")
            def threshold = shortCycleThreshold ?: 1.0
            
            if (activeSetpoint != null && Math.abs(state.startTemp - activeSetpoint) <= threshold) {
                logAction("BMS Command -> Compressor Protection Engaged! HVAC started within ${threshold}° of setpoint. Forcing minimum run time.")
                engageBuffer(0)
            }
        }
        
        if (enableDeltaT && returnSensor && dischargeSensor) runIn((deltaTCheckDelay ?: 30) * 60, checkDeltaT)
    } else if (stateVal == "idle" || stateVal == "pending cool" || stateVal == "pending heat") {
        unschedule(checkDeltaT)
        unschedule(compressorWatchdog)
        
        def yoyoMins = yoyoCooldownMins != null ? yoyoCooldownMins.toInteger() : 15
        if (state.currentAction == "cooling" || state.currentAction == "heating") {
            
            // --- THERMAL COASTING HOOK ---
            if (enableThermalCoasting) {
                logAction("Thermal Coasting: Compressor cycle complete. Forcing blower ON for ${coastingDurationMins ?: 3} minutes to scavenge coil temperatures.")
                thermostat.setThermostatFanMode("on")
                runIn((coastingDurationMins ?: 3) * 60, endThermalCoasting)
                state.coastingEnds = now() + ((coastingDurationMins ?: 3) * 60000)
            }
            
            if (yoyoMins > 0) {
                state.yoyoCooldownEnds = now() + (yoyoMins * 60000)
                logAction("${state.currentAction.capitalize()} cycle complete. Starting ${yoyoMins}-Minute Anti-Yo-Yo Cooldown.")
            }
        }

        if (state.isBuffering) releaseBuffer()
        
        if (state.cycleStartTime) {
            def runMinutes = (now() - state.cycleStartTime) / 60000.0
            if (enableFilterTracker) processFilterWear(runMinutes)
            if (state.currentAction) trackEnergyStats(state.currentAction, runMinutes)
            
            if (state.currentAction && state.currentAction != "idle") {
                state.lastCycleMode = state.currentAction
                trackRecentCycle(state.currentAction, runMinutes, state.currentCycleMaxDeltaT)
            
                if (enableMinRuntime && state.currentAction in ["cooling", "heating"]) {
                    
                    if (runMinutes < getMinRunTime()) {
                        
                        def tripTriggered = false
                        if (enableTripFailsafe) {
                            def maxCycles = tripCycleCount ?: 3
                            def windowMs = (tripWindowMins ?: 60) * 60000
                            
                            def scLog = state.shortCycleLog ?: []
                            scLog.add(now())
                            scLog = scLog.findAll { (now() - it) <= windowMs }
                            state.shortCycleLog = scLog
    
                            if (scLog.size() >= maxCycles) {
                                tripTriggered = true
                                logAction("CRITICAL FAILSAFE: ${maxCycles} Short-Cycles detected within ${tripWindowMins ?: 60} minutes! Hardware may be tripping a limit switch. Releasing protective locks.")
                                if (enableShortCycleNotify && notifyDevices) {
                                    notifyDevices.deviceNotification("HVAC ALERT: ${maxCycles} Short-cycles detected in under ${tripWindowMins ?: 60} minutes! Locks released to prevent hardware damage.")
                                }
                                state.shortCycleLog = []
                                state.cycleStartTime = null
                                state.isBuffering = false
                                state.alignmentLockout = null
                                state.activeHysteresis = "idle"
                                state.yoyoCooldownEnds = null
                                state.deviationStartTime = null
                                unschedule(releaseBuffer)
                            }
                        }
                        
                        if (!tripTriggered) {
                            logAction("WARNING: Short-cycle detected! Compressor ran for ${String.format('%.1f', runMinutes)} mins (Goal: ${getMinRunTime()} mins).")
                            if (enableShortCycleNotify && notifyDevices) {
                                notifyDevices.deviceNotification("HVAC Alert: Short-cycle detected. ${state.currentAction.capitalize()} ran for only ${String.format('%.1f', runMinutes)} minutes.")
                            }
                        }
                    }
                }
            }
        }
        state.cycleStartTime = null; state.currentAction = "idle"; state.cycleStartMode = null; state.modeDelayLogged = false
        
        // --- IDLE RE-ASSERTION CATCH FOR SICK MODE ---
        def sickSwitches = []
        if (sickModeSwitch) sickSwitches += (sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch])
        if (sickModeSwitch2) sickSwitches << sickModeSwitch2
        if (sickModeSwitch3) sickSwitches << sickModeSwitch3
        def isSickMode = sickSwitches.any { it?.currentValue("switch") == "on" }
        
        if (isSickMode) {
            def tstatFan = thermostat.currentValue("thermostatFanMode")?.toUpperCase()
            if (tstatFan != "ON" && tstatFan != "CIRCULATE") {
                logAction("HVAC returned to Idle. Re-asserting Sick Mode Fan ON command.")
                thermostat.setThermostatFanMode("on")
            }
        }
        
        evaluateSystem()
    }
}

def trackEnergyStats(action, runMinutes) {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.runHistory) state.runHistory = [:]
    
    // Safety structure check
    if (!state.runHistory[today]) state.runHistory[today] = [cool: 0.0, heat: 0.0, aux: 0.0, runs: 0, maxRun: 0.0, minRun: 9999.0, maxOutTemp: null, maxOutHum: null]
    if (!state.runHistory[today].containsKey("maxOutTemp")) state.runHistory[today].maxOutTemp = null
    if (!state.runHistory[today].containsKey("maxOutHum")) state.runHistory[today].maxOutHum = null
    
    if (action == "cooling") state.runHistory[today].cool += runMinutes
    if (action == "heating") state.runHistory[today].heat += runMinutes
    if (action == "auxHeating") state.runHistory[today].aux += runMinutes
    
    if (action in ["cooling", "heating", "auxHeating"]) {
        state.runHistory[today].runs = (state.runHistory[today].runs ?: 0) + 1
        
        if (state.runHistory[today].maxRun == null) state.runHistory[today].maxRun = 0.0
        if (state.runHistory[today].minRun == null) state.runHistory[today].minRun = 9999.0
        
        if (runMinutes > state.runHistory[today].maxRun) state.runHistory[today].maxRun = runMinutes
        if (runMinutes < state.runHistory[today].minRun) state.runHistory[today].minRun = runMinutes
    }
    
    def keys = state.runHistory.keySet().sort().reverse()
    if (keys.size() > 7) { state.runHistory = state.runHistory.subMap(keys[0..6]) }
}

def sensorHandler(evt) {
    evaluateSystem()
    
    // --- DELTA-T PEAK TRACKER ---
    if (enableDeltaT && returnSensor && dischargeSensor && state.currentAction in ["cooling", "heating"]) {
        def retT = returnSensor.currentValue("temperature")
        def disT = dischargeSensor.currentValue("temperature")
        if (retT != null && disT != null) {
            def dT = state.currentAction == "cooling" ? (retT - disT).doubleValue() : (disT - retT).doubleValue()
            dT = Math.max(0.0, dT).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
            
            if (state.currentCycleMaxDeltaT == null || dT > state.currentCycleMaxDeltaT) {
                state.currentCycleMaxDeltaT = dT
            }
        }
    }
    // -------------------------------
    
    def isNight = nightModes ? (nightModes as List).contains(location.mode) : false
    
    if (state.windowOpenHold || !enableMinRuntime || isNight || state.currentAction == "idle" || state.isBuffering || !state.cycleStartTime) return
    if (state.currentAction == "auxHeating") return
    
    def runMins = (now() - state.cycleStartTime) / 60000.0
    if (runMins < 1.0 || runMins >= getMinRunTime()) return 
    def dropRate = ((state.currentAction == "cooling" ? state.startTemp - thermostat.currentValue("temperature") : thermostat.currentValue("temperature") - state.startTemp)) / runMins
    if (dropRate >= (tempDropThreshold ?: 0.5)) engageBuffer(runMins)
}

def engageBuffer(runMins) {
    state.isBuffering = true
    state.preBufferCool = thermostat.currentValue("coolingSetpoint")?.toBigDecimal()
    state.preBufferHeat = thermostat.currentValue("heatingSetpoint")?.toBigDecimal()
    
    def bufferAmt = setpointBuffer ?: 2.0
    def deadband = 3.0 
    
    if (state.currentAction == "cooling") { 
        def newCool = (thermostat.currentValue("coolingSetpoint")?.toBigDecimal() ?: 72.0) - bufferAmt
        def newHeat = (thermostat.currentValue("heatingSetpoint")?.toBigDecimal() ?: 68.0)
        
        if ((newCool - newHeat) < deadband) newHeat = newCool - deadband
        
        state.expectedCool = newCool; state.expectedHeat = newHeat
        state.lastCommandTime = now()
        thermostat.setCoolingSetpoint(newCool)
        if (thermostat.currentValue("heatingSetpoint") != newHeat) thermostat.setHeatingSetpoint(newHeat)
        
        logAction("BMS Command -> Compressor Protection Engaged! Temperature dropping too fast. Target temporarily shifted to ${newCool}° to ensure minimum runtime.")
        runIn(10, verifyBufferEnforcement, [data: [targetCool: newCool, targetHeat: newHeat, action: state.currentAction, retryCount: 0]])
    } else { 
        def newHeat = (thermostat.currentValue("heatingSetpoint")?.toBigDecimal() ?: 68.0) + bufferAmt
        def newCool = (thermostat.currentValue("coolingSetpoint")?.toBigDecimal() ?: 72.0)
 
        if ((newCool - newHeat) < deadband) newCool = newHeat + deadband
        
        state.expectedHeat = newHeat; state.expectedCool = newCool
        state.lastCommandTime = now()
        thermostat.setHeatingSetpoint(newHeat)
        if (thermostat.currentValue("coolingSetpoint") != newCool) thermostat.setCoolingSetpoint(newCool)
        
        logAction("BMS Command -> Compressor Protection Engaged! Temperature rising too fast. Target temporarily shifted to ${newHeat}° to ensure minimum runtime.")
        runIn(10, verifyBufferEnforcement, [data: [targetCool: newCool, targetHeat: newHeat, action: state.currentAction, retryCount: 0]])
    }
    
    runIn((((getMinRunTime()) - runMins) * 60).toInteger(), releaseBuffer)
}

def verifyBufferEnforcement(data) {
    if (!state.isBuffering) return // Abort if buffer already released
    
    def currentCool = thermostat.currentValue("coolingSetpoint")?.toBigDecimal()
    def currentHeat = thermostat.currentValue("heatingSetpoint")?.toBigDecimal()
    
    def retryNeeded = false
    if (data.action == "cooling" && currentCool != data.targetCool) retryNeeded = true
    if (data.action == "heating" && currentHeat != data.targetHeat) retryNeeded = true
    
    if (retryNeeded) {
        def attempt = (data.retryCount ?: 0) + 1
        if (attempt <= 3) {
            logAction("WARNING: Thermostat failed to confirm protective setpoint (Attempt ${attempt}/3). Retrying Zigbee command...")
            if (data.action == "cooling") {
                thermostat.setCoolingSetpoint(data.targetCool)
                if (currentHeat != data.targetHeat) thermostat.setHeatingSetpoint(data.targetHeat)
            } else {
                thermostat.setHeatingSetpoint(data.targetHeat)
                if (currentCool != data.targetCool) thermostat.setCoolingSetpoint(data.targetCool)
            }
            runIn(15, verifyBufferEnforcement, [data: [targetCool: data.targetCool, targetHeat: data.targetHeat, action: data.action, retryCount: attempt]])
        } else {
            logAction("CRITICAL ERROR: Thermostat failed to confirm protective setpoint after 3 attempts. Compressor protection may be compromised.")
            if (notifyDevices) notifyDevices.deviceNotification("HVAC ALERT: Thermostat is unresponsive to Zigbee commands. Compressor protection failed to engage.")
        }
    } else {
        if ((data.retryCount ?: 0) > 0) logAction("Success: Thermostat confirmed protective setpoint on attempt ${(data.retryCount ?: 0) + 1}.")
    }
}

def releaseBuffer() { 
    state.isBuffering = false
    
    // BUG FIX: Removed the Anti-Yo-Yo timer application here so it doesn't lock out the AC while it's trying to satisfy the room average.
    
    logAction("Compressor Protection Buffer Complete. Reverting setpoint assignments.") 
    
    if (state.manualHoldEnds && now() < state.manualHoldEnds) {
        logAction("BMS Command -> Manual Hold active. Restoring user's pre-buffer targets.")
        if (state.preBufferCool != null) thermostat.setCoolingSetpoint(state.preBufferCool)
        if (state.preBufferHeat != null) thermostat.setHeatingSetpoint(state.preBufferHeat)
    }

    state.preBufferCool = null
    state.preBufferHeat = null
    evaluateSystem() 
}

def checkDeltaT() { 
    if (state.currentAction == "idle" || state.fireEmergency) return
    def retT = returnSensor.currentValue("temperature")
    def disT = dischargeSensor.currentValue("temperature")
    if (retT == null || disT == null) return
  
    def dT = 0.0
    if (state.currentAction == "cooling") {
        dT = Math.max(0.0, (retT - disT).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    } else if (state.currentAction == "heating") {
        dT = Math.max(0.0, (disT - retT).doubleValue()).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    
    if (dT < (state.currentAction == "cooling" ? (minCoolingDeltaT ?: 12.0) : (minHeatingDeltaT ?: 15.0))) { 
        logAction("HVAC WARNING: Poor efficiency. Delta-T is ${String.format('%.1f', dT)}°F.")
        
        if (notifyDeltaT && notifyDevices) {
            if (!state.lastDeltaTAlert || (now() - state.lastDeltaTAlert) > 86400000) { 
                notifyDevices.deviceNotification("HVAC Alert: Poor efficiency detected. Delta-T is ${String.format('%.1f', dT)}°F. Unit may be freezing up or low on refrigerant.")
                state.lastDeltaTAlert = now()
            }
        }
        
        if (emergencyShutoff) thermostat.off() 
    } else {
        logAction("Delta-T Check Passed: ${String.format('%.1f', dT)}°F.")
    }
    
    runIn((deltaTCheckDelay ?: 30) * 60, checkDeltaT)
}

def processFilterWear(actualRunMinutes) { 
    def ind = indoorIAQ ? (indoorIAQ.currentValue("airQualityIndex") ?: 0) : 0
    def out = outdoorIAQ ? (outdoorIAQ.currentValue("airQualityIndex") ?: 0) : 0
    state.filterRunMinutes += (actualRunMinutes * (1.0 + (ind * 0.01) + (out * 0.002))) 
    
    if (notifyFilter && notifyDevices) {
        def maxMins = (maxFilterHours ?: 300) * 60
        def percentLeft = Math.max(0.0, 100.0 - ((state.filterRunMinutes / maxMins) * 100))
        def alertThreshold = filterNotifyThreshold != null ? filterNotifyThreshold : 10
        
        if (percentLeft < alertThreshold && !state.filterAlertSent) {
            notifyDevices.deviceNotification("HVAC Maintenance: Your air filter life is below ${alertThreshold}%. Please replace it soon to maintain efficiency.")
            state.filterAlertSent = true
        }
    }
}

def dailyMaintenanceCheck() {
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
    
    // Generate a random time between 0 and 39600 seconds (11 hours). 
    // This spans from the 8:00 AM cron execution out to 7:00 PM.
    def randomSeconds = new java.util.Random().nextInt(39600)
    runIn(randomSeconds, triggerFilterAlertSwitch)
    logInfo("Filter Alert Switch scheduled to trigger randomly in ${Math.round(randomSeconds / 60)} minutes.")
}

def triggerFilterAlertSwitch() {
    if (!enableFilterTracker || !filterAlertSwitch) return
    
    // Mode Check
    if (filterAlertModes && !(filterAlertModes as List).contains(location.mode)) {
        logInfo("Skipping filter alert switch: current mode (${location.mode}) is not in the allowed list.")
        return
    }
    
    def maxMins = (maxFilterHours ?: 300) * 60
    def usedMins = state.filterRunMinutes ?: 0.0
    def percentLeft = Math.max(0.0, 100.0 - ((usedMins / maxMins) * 100)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    def alertThreshold = filterNotifyThreshold != null ? filterNotifyThreshold : 10
    
    if (percentLeft < alertThreshold) {
        filterAlertSwitch.on()
        
        if (notifyFilter != false && notifyDevices) {
            notifyDevices.deviceNotification("Daily Reminder: Your HVAC air filter life is at ${percentLeft}% (Below ${alertThreshold}%). Please replace it soon.")
        }
        
        logAction("Filter Alert Threshold Reached: Turning on virtual alert switch for 10 minutes, and push notification sent.")
        runIn(600, turnOffFilterAlertSwitch)
    }
}

def turnOffFilterAlertSwitch() {
    if (filterAlertSwitch) {
        filterAlertSwitch.off()
        logAction("Filter Alert Cycle Complete: Alert switch turned off.")
    }
}

def trackRecentCycle(action, runMinutes, maxDeltaT = null) {
    if (!state.recentCycles) state.recentCycles = []
    def timestamp = new Date().format("MM/dd hh:mm a", location.timeZone)
    def formattedTime = String.format("%.1f", runMinutes)
    
    def actionName = action.capitalize()
    if (action == "auxHeating") actionName = "Aux Heat"
    
    def dtString = maxDeltaT != null ? " | Peak ΔT: <b>${String.format('%.1f', maxDeltaT)}°</b>" : ""
    def cycleLog = "[${timestamp}] ${actionName} ran for <b>${formattedTime} minutes</b>${dtString}"
    
    state.recentCycles.add(0, cycleLog)
    if (state.recentCycles.size() > 10) {
        state.recentCycles = state.recentCycles[0..9]
    }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size()>30)h=h[0..29]
    state.actionHistory=h 
}
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }

// HELPER: Grabs the dynamic run time based on current action
def getMinRunTime() {
    return state.currentAction == "cooling" ? (summerMinRunTime ?: 25) : (winterMinRunTime ?: 15)
}
