/**
 * Advanced Appliance Manager 2.0
 */
definition(
    name: "Advanced Appliance Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        def applianceList = [
            [id: "refrigerator", name: "Refrigerator", hasTemp: true],
            [id: "chestFreezer", name: "Chest Freezer", hasTemp: true],
            [id: "hotWaterHeater", name: "Hot Water Heater", hasTemp: false],
            [id: "washerDryer", name: "Washer/Dryer", hasTemp: false],
            [id: "dishwasher", name: "Dishwasher", hasTemp: false],
            [id: "microwave", name: "Microwave", hasTemp: false]
        ]

        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 6px; text-align: center; vertical-align: middle; }
                .dash-table th { background-color: #343a40; color: white; font-size: 12px;}
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 10px !important; width: 18%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 10px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 10px !important; }
                
                /* Hardened CSS to override Hubitat Default Margins */
                .local-bar-wrapper { display: flex; align-items: center; margin: 5px 0; }
                .local-bar-label { width: 90px; text-align: right; margin-right: 10px; font-size: 11px; color: #495057; }
                .local-bar-track { flex-grow: 1; background: #e9ecef; height: 14px; border-radius: 3px; overflow: hidden; display: flex; align-items: stretch; margin: 0; padding: 0; }
                .local-bar-fill { margin: 0 !important; padding: 0 !important; border-radius: 3px; height: 100% !important; min-height: 10px; }
            </style>
            
            <table class="dash-table">
                <thead><tr><th>Appliance</th><th>State & Cycles</th><th>Power</th><th>Door</th><th>Temperatures</th><th>Health & Diagnostics</th></tr></thead>
                <tbody>
                    <tr><td colspan="6" class="dash-subhead">System Overview</td></tr>
            """
            
            applianceList.each { app ->
                def isConfigured = settings["${app.id}Power"] || settings["${app.id}Switch"]
                if (isConfigured) {
                    def pMeter = settings["${app.id}Power"]
                    def currentPower = pMeter ? (pMeter.currentValue('power')?.toString()?.toDouble() ?: 0.0) : 0.0
                    def isRunning = state["${app.id}_isRunning"] ?: false
                    def runThreshold = settings["${app.id}RunWatts"]?.toString()?.toDouble() ?: 15.0
                    
                    // --- 1. STATE & CYCLES ---
                    def switchState = settings["${app.id}Switch"] ? "<div style='font-size:11px; color:#555;'>Relay: <b>${settings["${app.id}Switch"].currentValue("switch")?.toUpperCase()}</b></div>" : ""
                    def statusStr = "<div style='color:gray; margin-top:2px;'>Idle</div>"
                    
                    if (state["${app.id}_ambientPaused"]) {
                        statusStr = "<div style='color:#17a2b8; font-weight:bold; margin-top:2px;'>Ambient Paused<br><span style='font-size:10px;'>Grid/Heat Wait</span></div>"
                    } else if (isRunning) {
                        def currentStage = state["${app.id}_currentStage"] ?: "Running"
                        def isVariable = settings["${app.id}VariableSpeed"] ?: false
                        
                        if (currentPower > runThreshold) {
                            if (isVariable && currentStage == "Defrost") {
                                statusStr = "<div style='color:#dc3545; font-weight:bold; margin-top:2px;'>Defrosting</div>"
                            } else if (isVariable) {
                                statusStr = "<div style='color:blue; font-weight:bold; margin-top:2px;'>Running<br><span style='font-size:10px;'>(${currentStage})</span></div>"
                            } else {
                                statusStr = "<div style='color:blue; font-weight:bold; margin-top:2px;'>Running</div>"
                            }
                        } else {
                            statusStr = "<div style='color:purple; font-weight:bold; margin-top:2px;'>Verifying<br>Cycle...</div>"
                        }
                    }
                    if (state["${app.id}_systemActionPending"]) statusStr = "<div style='color:orange; margin-top:2px;'>Command Pending</div>"
                    
                    def todayCnt = state["${app.id}_todayRunCount"] ?: 0
                    def cyclesStr = "<div style='font-size:10px; color:#555; margin-top:4px;'>Today's Cycles: <b>${todayCnt}</b></div>"
                    def fullStatusStr = "${switchState}${statusStr}${cyclesStr}"

                    
                    // --- 2. DOOR STATUS ---
                    def cState = "<div style='color:#ccc; font-size:11px;'>No Sensor</div>"
                    if (settings["${app.id}Contact"]) {
                        if (state["${app.id}_doorCurrentState"] == "open") {
                            def openSecs = state["${app.id}_doorLastOpenTime"] ? Math.round((now() - state["${app.id}_doorLastOpenTime"]) / 1000) : 0
                            def openMins = Math.floor(openSecs / 60).toInteger()
                            def remSecs = openSecs % 60
                            cState = "<div style='color:red; font-weight:bold;'>Open</div><div style='font-size:10px; color:#dc3545;'>(${openMins}m ${remSecs}s)</div>"
                        } else {
                            cState = "<div style='color:green;'>Closed</div>"
                        }
                    }

                    // --- 3. TEMPERATURES (Internal & Room) ---
                    def currentTemp = settings["${app.id}Temp"]?.currentValue("temperature") ?: "--"
                    def tempHistory = state["${app.id}_tempHistory"] ?: []
                    def sparkline = getSparkline(tempHistory)
                    def intTempStr = currentTemp != "--" ? "<div style='font-size:14px; font-weight:bold;'>${currentTemp}°</div>" : "<div style='color:#ccc; font-size:11px;'>No Internal Sensor</div>"
                    
                    def roomTemp = settings["${app.id}RoomTemp"]?.currentValue("temperature") ?: "--"
                    def roomStr = roomTemp != "--" ? "<div style='font-size:10px; color:#555;'>Room: <b>${roomTemp}°</b></div>" : ""
                    
                    def fullTempStr = "${intTempStr}${roomStr}${sparkline}"

                    // --- 4. HEALTH & PAUSES ---
                    def strikes = state["${app.id}_efficiencyStrikes"] ?: 0
                    def struggleStrikes = state["${app.id}_struggleCount"] ?: 0
                    def healthStr = "<div style='color:green;'>Normal</div>"
                    def isGracePeriod = state["${app.id}_restockGracePeriodUntil"] && now() < state["${app.id}_restockGracePeriodUntil"]
                    
                    if (state["${app.id}_efficiencyAlert"] || strikes >= 3) {
                        healthStr = "<div style='color:red; font-weight:bold;'>Door Open /<br>Leak Detected</div>"
                    } else if (strikes > 0) {
                        healthStr = "<div style='color:orange;'>Evaluating<br>Temp Drop...</div>"
                    } else if (struggleStrikes > 3) {
                        healthStr = "<div style='color:orange;'>Degraded<br>(Long Cycles)</div>"
                    } else if (state["${app.id}_tempCreepWarning"]) {
                        healthStr = "<div style='color:red;'>Temp Creep<br>Detected</div>"
                    } else if (isGracePeriod) {
                        healthStr = "<div style='color:blue;'>Restock<br>Cooling...</div>"
                    }
                    
                    def pauseInfo = ""
                    if (state["${app.id}_ambientPaused"]) {
                        def currentPauseMins = state["${app.id}_ambientPauseStartTime"] ? Math.round((now() - state["${app.id}_ambientPauseStartTime"]) / 60000.0) : 0
                        pauseInfo = "<div style='font-size:10px; color:#17a2b8; margin-top:4px;'>Paused: <b>${currentPauseMins}m</b> (Active)</div>"
                    } else if (state["${app.id}_lastPauseDuration"]) {
                        pauseInfo = "<div style='font-size:10px; color:gray; margin-top:4px;'>Last Pause: <b>${state["${app.id}_lastPauseDuration"]}m</b></div>"
                    }
                    def fullHealthStr = "${healthStr}${pauseInfo}"
                    
                    dashHTML += "<tr><td class='dash-hl'>${app.name}</td><td class='dash-val'>${fullStatusStr}</td><td>${currentPower} W</td><td>${cState}</td><td>${fullTempStr}</td><td>${fullHealthStr}</td></tr>"
                }
            }
            
            dashHTML += """
                </tbody>
            </table>
            """

            // --- APPLIANCE USAGE TRACKING ---
            def usageApps = applianceList.findAll { it.id in ["hotWaterHeater", "washerDryer", "dishwasher", "microwave"] }
            def hasUsage = usageApps.any { settings["${it.id}Power"] || settings["${it.id}Switch"] }
            
            if (hasUsage) {
                dashHTML += """
                <table class="dash-table" style="margin-top:20px;">
                    <thead><tr><th>Appliance</th><th>Today's Uses</th><th>7-Day Total</th></tr></thead>
                    <tbody>
                        <tr><td colspan="3" class="dash-subhead">Appliance Usage Tracking</td></tr>
                """
                usageApps.each { app ->
                    if (settings["${app.id}Power"] || settings["${app.id}Switch"]) {
                        def todayCnt = state["${app.id}_todayRunCount"] ?: 0
                        def sevenDayCnt = state["${app.id}_7DayRunCount"] ?: 0
                        dashHTML += "<tr><td class='dash-hl'>${app.name}</td><td><b>${todayCnt}</b> Cycles</td><td><b>${sevenDayCnt}</b> Cycles</td></tr>"
                    }
                }
                dashHTML += "</tbody></table>"
            }
            
            // --- DOOR ANALYTICS TABLE ---
            def hasContact = false
            applianceList.each { app -> if (settings["${app.id}Contact"]) hasContact = true }
            
            if (hasContact) {
                dashHTML += """
                <table class="dash-table" style="margin-top:20px;">
                    <thead><tr><th>Appliance</th><th>Today's Opens</th><th>Avg Opens/Day</th><th>Avg Open Duration</th></tr></thead>
                    <tbody>
                        <tr><td colspan="4" class="dash-subhead">Historical Door Analytics</td></tr>
                """
                applianceList.each { app ->
                    if (settings["${app.id}Contact"]) {
                        def todayOpens = state["${app.id}_doorOpenCountToday"] ?: 0
                        
                        def rawAvg = state["${app.id}_doorAvgDailyOpens"]?.toString()?.toDouble() ?: 0.0
                        def avgDaily = rawAvg > 0 ? (Math.round(rawAvg * 10) / 10.0).toString() : "--"
                        
                        def avgDuration = state["${app.id}_doorAvgDurationSecs"] ? "${Math.round(state["${app.id}_doorAvgDurationSecs"])}s" : "--"
                        
                        dashHTML += "<tr><td class='dash-hl'>${app.name}</td><td>${todayOpens}</td><td>${avgDaily}</td><td>${avgDuration}</td></tr>"
                    }
                }
                dashHTML += "</tbody></table>"
            }
            
            // --- AI LEARNING & BAR GRAPHS ---
            def coolingApps = applianceList.findAll { it.id == "refrigerator" || it.id == "chestFreezer" }
            def hasCooling = coolingApps.any { settings["${it.id}Power"] || settings["${it.id}Switch"] }

            if (hasCooling) {
                dashHTML += """
                <table class="dash-table" style="margin-top:20px;">
                    <thead><tr><th>Appliance</th><th>AI Learning Phase (14-Day Baseline)</th><th>Status</th></tr></thead>
                    <tbody>
                """
                
                coolingApps.each { app ->
                    if (settings["${app.id}Power"] || settings["${app.id}Switch"]) {
                        def learnedDays = state["${app.id}_learningDays"] ?: 0
                        def learnPct = Math.min(100, Math.round((learnedDays / 14.0) * 100))
                        def learnColor = learnedDays >= 14 ? "#28a745" : "#17a2b8"
                        def learnStatus = learnedDays >= 14 ? "<span style='color:green; font-weight:bold;'>Mature</span>" : "<span style='color:gray;'>Learning</span>"
                        
                        dashHTML += """
                            <tr>
                                <td class='dash-hl'>${app.name}</td>
                                <td style="text-align: left; padding: 10px;">
                                    <div style="font-size: 11px; margin-bottom: 4px; line-height: 1;">Day ${learnedDays} of 14</div>
                                    <div style="width: 100%; background: #e9ecef; height: 12px; border-radius: 3px; overflow: hidden; display: flex; align-items: stretch; margin: 0; padding: 0;">
                                        <div class="local-bar-fill" style="width: ${learnPct}%; background: ${learnColor};"></div>
                                    </div>
                                </td>
                                <td>${learnStatus}</td>
                            </tr>
                        """
                    }
                }
                
                dashHTML += """
                    </tbody>
                </table>
                """
                
                // --- THERMAL BATTERY ANALYTICS TABLE ---
                def thermalApps = coolingApps.findAll { settings["${it.id}EnableAmbientPause"] }
                if (thermalApps) {
                    dashHTML += """
                    <table class="dash-table" style="margin-top:20px;">
                        <thead><tr><th>Appliance</th><th>Total Thermal Battery Time</th><th>Total Compressor Run Time</th><th>Ratio (Pause/Run)</th></tr></thead>
                        <tbody>
                            <tr><td colspan="4" class="dash-subhead">Thermal Battery Performance Stats</td></tr>
                    """
                    thermalApps.each { app ->
                        def tPauseHr = state["${app.id}_totalAmbientPauseHours"] ?: 0.0
                        def tRunHr = state["${app.id}_totalRunHours"] ?: 0.0
                        
                        def fmtPause = String.format("%.1f hrs", tPauseHr)
                        def fmtRun = String.format("%.1f hrs", tRunHr)
                        def ratio = tRunHr > 0 ? String.format("%.2fx", tPauseHr / tRunHr) : "--"
                        
                        dashHTML += "<tr><td class='dash-hl'>${app.name}</td><td>${fmtPause}</td><td>${fmtRun}</td><td>${ratio}</td></tr>"
                    }
                    dashHTML += "</tbody></table>"
                }

                dashHTML += """
                <table class="dash-table" style="margin-top:20px;">
                    <thead><tr><th style="width: 25%;">Appliance</th><th>Weekly Cycle Comparison (Local Run Data)</th><th>National Avg (7-Day)</th></tr></thead>
                    <tbody>
                """
                
                coolingApps.each { app ->
                    if (settings["${app.id}Power"] || settings["${app.id}Switch"]) {
                        def currCycles = (state["${app.id}_7DayRunCount"] ?: 0).toInteger()
                        def prevCycles = (state["${app.id}_Prev7DayRunCount"] ?: 0).toInteger()
                        
                        def maxCycles = Math.max(currCycles, prevCycles)
                        if (maxCycles <= 0) maxCycles = 1 
                        
                        def currPct = Math.round((currCycles.toDouble() / maxCycles.toDouble()) * 100)
                        def prevPct = Math.round((prevCycles.toDouble() / maxCycles.toDouble()) * 100)
                        
                        def avgRunLength = state["${app.id}_baselineCycleMins"] ? Math.round(state["${app.id}_baselineCycleMins"]) : "--"
                        def natCycles = app.id == "refrigerator" ? "60-90 Cycles" : "100-140 Cycles"
                        def natLength = app.id == "refrigerator" ? "~45-90 mins" : "~15-30 mins"
                        
                        dashHTML += """
                            <tr>
                                <td class='dash-hl'>${app.name}<br><span style='font-size:10px; color:gray; font-weight:normal;'>Local Avg Length: <b>${avgRunLength}m</b></span></td>
                                <td style='padding: 10px;'>
                                    <div class='local-bar-wrapper'>
                                        <div class='local-bar-label'>Previous Wk (${prevCycles})</div>
                                        <div class='local-bar-track'>
                                            <div class='local-bar-fill' style='width: ${prevPct}%; background: #6c757d;'></div>
                                        </div>
                                    </div>
                                    <div class='local-bar-wrapper'>
                                        <div class='local-bar-label' style='font-weight: bold; color: black;'>Current Wk (${currCycles})</div>
                                        <div class='local-bar-track'>
                                            <div class='local-bar-fill' style='width: ${currPct}%; background: #007bff;'></div>
                                        </div>
                                    </div>
                                </td>
                                <td><span style='font-size:12px; color:#555;'>${natCycles}<br>${natLength}</span></td>
                            </tr>
                        """
                    }
                }
                dashHTML += "</tbody></table>"
            }
            
            paragraph dashHTML
        }

        section("<b>Recent Action History</b>", hideable: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>Global App Enablement & System Resets</b>", hideable: true, hidden: true) {
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            paragraph "<div style='font-size:13px; color:#555;'><b>Calibration:</b> Use this button to wipe all learned efficiency curves, idle power averages, door usage, and cycle history. Use this if you replace an appliance or service the compressor.</div>"
            input "btnResetLearning", "button", title: "⚠️ Factory Reset AI Learning Baselines"
        }

        applianceList.each { app ->
            section("<b>⚙️ ${app.name} Setup</b>", hideable: true, hidden: true) {
                input "${app.id}Switch", "capability.switch", title: "Smart Switch/Plug", required: false
                input "${app.id}Power", "capability.powerMeter", title: "Power Meter", required: false
                input "${app.id}Contact", "capability.contactSensor", title: "Door Open/Close Sensor", required: false
                
                paragraph "<b>Failsafe & Protection</b>"
                input "${app.id}AlwaysOn", "bool", title: "<b>Enable Always-On Protection</b> (Force ON immediately if turned OFF)", defaultValue: true
                
                if (app.hasTemp) {
                    paragraph "<b>Environment & AI Diagnostics</b>"
                    input "${app.id}Temp", "capability.temperatureMeasurement", title: "Internal Temperature Sensor", required: false
                    input "${app.id}RoomTemp", "capability.temperatureMeasurement", title: "Room Ambient Temp Sensor", required: false
                    input "${app.id}OutsideTemp", "capability.temperatureMeasurement", title: "Outdoor Temp Sensor", required: false
                    input "${app.id}TempThreshold", "decimal", title: "Critical Temp Threshold (°F)", required: false
                    input "${app.id}RestockGrace", "number", title: "Restock Grace Period (Mins to pause temp alerts after user access)", required: false, defaultValue: 120
                    input "${app.id}MaintenanceHours", "number", title: "Maintenance Interval (Run Hours)", required: false, defaultValue: 2000
                    paragraph "<div style='font-size:13px; color:#555;'><i>The system automatically calculates the ${app.name}'s cooling efficiency curve. If it draws continuous power but fails to meet its learned temperature drop rate for 3 consecutive 20-minute periods, it will trigger an Open Door/Cooling Leak alert.</i></div>"
                    
                    paragraph "<b>Ambient-Aware Cooling (Thermal Battery)</b>"
                    input "${app.id}EnableAmbientPause", "bool", title: "Enable Hot-Room Compressor Pause", defaultValue: false, submitOnChange: true
                    if (settings["${app.id}EnableAmbientPause"]) {
                        input "${app.id}AmbientPauseTemp", "decimal", title: "Pause when Room Temp hits (°F)", defaultValue: 82.0
                        input "${app.id}AmbientResumeTemp", "decimal", title: "Resume when Room Temp drops to (°F)", defaultValue: 78.0
                        input "${app.id}InternalFailsafeTemp", "decimal", title: "Max Internal Failsafe Temp (°F) (Forces ON)", defaultValue: 10.0
                        paragraph "<div style='font-size:11px; color:#555;'><i>The system will automatically suspend grid power during high ambient heat and will safely wait until the compressor is idle for 2+ minutes to prevent short-cycling.</i></div>"
                    }
                }

                paragraph "<b>Operational Tuning & Staging</b>"
                input "${app.id}RunWatts", "decimal", title: "Running Wattage Threshold (Low Stage)", required: false, defaultValue: 15.0
                
                input "${app.id}VariableSpeed", "bool", title: "Enable Variable Speed/Staging Tracking", defaultValue: false, submitOnChange: true
                if (settings["${app.id}VariableSpeed"]) {
                    input "${app.id}HighWatts", "decimal", title: "High Stage Threshold (Watts)", required: false, defaultValue: 120.0
                    input "${app.id}DefrostWatts", "decimal", title: "Defrost Cycle Threshold (Watts)", required: false, defaultValue: 350.0
                    paragraph "<div style='font-size:11px; color:#555;'><i>The system automatically detects Defrost stages to suspend efficiency alarms, as temperatures naturally rise during this cycle.</i></div>"
                }
                
                input "${app.id}StartDelay", "number", title: "Start Delay (Minutes)", required: false, defaultValue: 1
                input "${app.id}Debounce", "number", title: "Completion Debounce (Minutes)", required: false, defaultValue: 15
                input "${app.id}Spike", "decimal", title: "Power Spike Alert Threshold (Watts)", required: false, defaultValue: 5000.0

                if (app.id == "washerDryer" || app.id == "dishwasher") {
                    paragraph "<b>HVAC Synergy Nudges</b>"
                    input "${app.id}HVACNudge", "bool", title: "Alert if started during peak outdoor heat", defaultValue: false, submitOnChange: true
                    if (settings["${app.id}HVACNudge"]) {
                        input "${app.id}OutsideHotTemp", "decimal", title: "Peak Heat Threshold (°F)", defaultValue: 85.0
                        input "${app.id}OutsideTempRef", "capability.temperatureMeasurement", title: "Outdoor Temp Sensor", required: false
                    }

                    paragraph "<b>Cycle Actions</b>"
                    input "${app.id}FinishedSwitch", "capability.switch", title: "Cycle Complete Virtual Switch (Auto-off after 10m)", required: false
                }

                paragraph "<b>Alerts & Notifications</b>"
                input "${app.id}PushNotification", "capability.notification", title: "Push Notification Device(s)", required: false, multiple: true
                
                if (settings["${app.id}Contact"]) {
                    input "${app.id}DoorAlertMins", "number", title: "Alert if Door left open for (Minutes, 0 to disable)", required: false, defaultValue: 0
                }
                
                input "${app.id}PushEvents", "enum", title: "Events to Notify", options: ["cycle":"Cycle Complete", "health":"Health/Diagnostics", "spike":"Power Spike", "temp":"Temp Alert", "protection":"Always-On Failsafe Triggered", "door":"Door Left Open"], required: false, multiple: true
                input "${app.id}AlertModes", "mode", title: "Only Alert in these Modes", multiple: true, required: false
            }
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
    
    def applianceList = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    applianceList.each { key ->
        if (state["${key}_learningDays"] == null) state["${key}_learningDays"] = 0
        if (state["${key}_Prev7DayRunCount"] == null) state["${key}_Prev7DayRunCount"] = 0
        
        if (settings["${key}Switch"]) subscribe(settings["${key}Switch"], "switch", alwaysOnProtectionHandler)
        if (settings["${key}Power"]) subscribe(settings["${key}Power"], "power", powerHandler)
        if (settings["${key}Contact"]) subscribe(settings["${key}Contact"], "contact", contactHandler)
        
        if (key == "refrigerator" || key == "chestFreezer") {
            if (settings["${key}Temp"]) subscribe(settings["${key}Temp"], "temperature", tempHandler)
            if (settings["${key}RoomTemp"]) subscribe(settings["${key}RoomTemp"], "temperature", roomTempHandler)
            if (settings["${key}OutsideTemp"]) subscribe(settings["${key}OutsideTemp"], "temperature", outsideTempHandler)
        }
    }
    
    schedule("0 0 0 * * ?", resetDailyCounters)
    schedule("0 0 2 * * ?", dailyHealthCheck) 
    schedule("0 5 0 ? * SUN", resetWeeklyCounters)
    
    logAction("App Initialized. Advanced Appliance Manager Diagnostics Ready.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
    } else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    } else if (btn == "btnResetLearning") {
        state.remove("allClearPending")
        unschedule("sendAllClearNotification")
        
        def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
        appliances.each { key ->
            state["${key}_learningDays"] = 0
            state["${key}_avgCoolingRate"] = 0.0
            state["${key}_baselineCycleMins"] = 0.0
            state["${key}_idlePowerAvg"] = 0.0
            state["${key}_baselineAvg"] = 0.0
            state["${key}_baselineTemp"] = 0.0
            state["${key}_efficiencyStrikes"] = 0
            state["${key}_7DayRunCount"] = 0
            state["${key}_Prev7DayRunCount"] = 0
            state["${key}_todayRunCount"] = 0
            state["${key}_totalRunHours"] = 0.0
            state["${key}_totalAmbientPauseHours"] = 0.0
            state.remove("${key}_currentStage")
            state.remove("${key}_restockGracePeriodUntil")
            state.remove("${key}_compressorOffTime")
            state.remove("${key}_thermalRecovery")
            
            // Wipe Sparkline Trends & Pause tracking
            state["${key}_tempHistory"] = []
            state["${key}_ambientPaused"] = false
            state["${key}_pausePending"] = false
            state.remove("${key}_lastPauseDuration")
            
            // Wipe Stuck Cycle States
            state["${key}_isRunning"] = false
            state["${key}_stopPending"] = false
            state.remove("${key}_cycleStartTime")
            
            // Door reset
            state["${key}_doorOpenCountToday"] = 0
            state["${key}_doorOpenCountTotal"] = 0
            state["${key}_doorDaysTracked"] = 0
            state["${key}_doorTotalDurationSecs"] = 0.0
            state["${key}_doorTotalDailyOpens"] = 0
            state["${key}_doorAvgDailyOpens"] = 0.0
            state["${key}_doorAvgDurationSecs"] = 0.0
            
            // Clear Alert Tracking States
            state.remove("${key}_doorAlertActive")
            state.remove("${key}_tempWarningActive")
            state.remove("${key}_tempCreepWarning")
            state.remove("${key}_efficiencyAlert")
            state.remove("${key}_spikeWarning")
            state.remove("${key}_creepWarning")
            state.remove("${key}_struggleWarning")
        }
        logAction("User commanded a full factory reset of all AI learning baselines, cycle histories, and temperature trends.")
    }
}

// Local SVG Sparkline Generator
def getSparkline(history) {
    if (!history || history.size() < 2) return "<div style='color:#ccc; font-size:10px; margin-top:2px;'>Gathering Data...</div>"
    def minT = history.min() - 0.5
    def maxT = history.max() + 0.5
    def range = maxT - minT
    if (range == 0) range = 1.0

    def width = 80
    def height = 20
    def step = width / (history.size() - 1)

    def pointsStr = ""
    history.eachWithIndex { val, idx ->
        def x = idx * step
        def y = height - (((val - minT) / range) * height)
        pointsStr += "${x},${y} "
    }

    def firstT = history.first()
    def lastT = history.last()
    // Red if trending up, Green if dropping, Blue if perfectly stable
    def trendColor = lastT > firstT ? "#dc3545" : (lastT < firstT ? "#28a745" : "#007bff") 

    return "<svg width='${width}' height='${height}' viewBox='0 -2 ${width} ${height + 4}' style='margin-top:4px; overflow:visible;'><polyline points='${pointsStr.trim()}' fill='none' stroke='${trendColor}' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/></svg>"
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}

def contactHandler(evt) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    
    def devId = evt.device.id
    def val = evt.value
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    
    appliances.each { key ->
        if (settings["${key}Contact"]?.id == devId) {
            if (val == "open") {
                state["${key}_doorLastOpenTime"] = now()
                state["${key}_doorOpenCountToday"] = (state["${key}_doorOpenCountToday"] ?: 0) + 1
                state["${key}_doorCurrentState"] = "open"
                
                // Schedule the custom Door Open Alert Watchdog
                def alertMins = settings["${key}DoorAlertMins"]?.toString()?.toInteger() ?: 0
                if (alertMins > 0) {
                    runIn(alertMins * 60, "doorOpenAlert_${key}")
                }
                
            } else if (val == "closed") {
                state["${key}_doorCurrentState"] = "closed"
                unschedule("doorOpenAlert_${key}") // Cancel any pending door alarms
                
                if (state["${key}_doorAlertActive"]) {
                    state["${key}_doorAlertActive"] = false
                    logAction("✅ ${key.capitalize()} door was finally closed after triggering an alert.")
                    checkAllClearStatus() // Alert removed, check system
                }
                
                if (state["${key}_doorLastOpenTime"]) {
                    def durationSecs = (now() - state["${key}_doorLastOpenTime"]) / 1000.0
                    state["${key}_doorTotalDurationSecs"] = (state["${key}_doorTotalDurationSecs"] ?: 0.0) + durationSecs
                    
                    def totalOpens = (state["${key}_doorOpenCountTotal"] ?: 0) + 1
                    state["${key}_doorOpenCountTotal"] = totalOpens
                    
                    state["${key}_doorAvgDurationSecs"] = state["${key}_doorTotalDurationSecs"] / totalOpens
                    state["${key}_doorLastOpenTime"] = null
                    
                    // Add Restock Grace Period Logic - lowered threshold to 15s to be smarter about user access
                    if (durationSecs > 15) {
                        def graceMins = settings["${key}RestockGrace"]?.toInteger() ?: 120
                        state["${key}_restockGracePeriodUntil"] = now() + (graceMins * 60000)
                        logAction("${key.capitalize()} door was open for ${Math.round(durationSecs)}s. Assuming user access. Suppressing temp/efficiency alerts for ${graceMins} minutes.")
                    }
                }
            }
        }
    }
}

// Dedicated Door Watchdog Routes
def doorOpenAlert_refrigerator() { triggerDoorAlert("refrigerator", "Refrigerator") }
def doorOpenAlert_chestFreezer() { triggerDoorAlert("chestFreezer", "Chest Freezer") }
def doorOpenAlert_hotWaterHeater() { triggerDoorAlert("hotWaterHeater", "Hot Water Heater") }
def doorOpenAlert_washerDryer() { triggerDoorAlert("washerDryer", "Washer/Dryer") }
def doorOpenAlert_dishwasher() { triggerDoorAlert("dishwasher", "Dishwasher") }
def doorOpenAlert_microwave() { triggerDoorAlert("microwave", "Microwave") }

def triggerDoorAlert(key, name) {
    // Verify the door is still actually open before alarming
    if (state["${key}_doorCurrentState"] == "open") {
        state["${key}_doorAlertActive"] = true
        cancelAllClear() // Interruption
        
        def alertMins = settings["${key}DoorAlertMins"]?.toString()?.toInteger() ?: 0
        
        sendAlert("🚪 🚨 ${name} ALERT: The door has been left open for over ${alertMins} minutes! Please close it immediately.", key, "door")
        logAction("CRITICAL: ${name} door left open for ${alertMins} minutes. Push notification sent.")
        
        // Reschedule the alert to repeat again in the same interval until closed
        if (alertMins > 0) {
            runIn(alertMins * 60, "doorOpenAlert_${key}")
        }
    }
}

def alwaysOnProtectionHandler(evt) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    
    if (evt.value == "off") {
        def deviceId = evt.device.id
        def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
        
        appliances.each { key ->
            if (settings["${key}Switch"]?.id == deviceId && settings["${key}AlwaysOn"]) {
                
                // --- Ignore if we triggered an ambient pause ---
                if (state["${key}_ambientPaused"]) return 
                
                cancelAllClear() // Interruption
                logAction("🛡️ Always-On Failsafe Triggered: ${key.capitalize()} turned off unexpectedly. Forcing ON immediately.")
                settings["${key}Switch"]?.on()
                sendAlert("🚨 CRITICAL FAILSAFE: Your ${key.capitalize()} was turned OFF. The system has automatically forced it back ON.", key, "protection")
            }
        }
    }
}

def powerHandler(evt) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    
    def meterId = evt.device.id
    def currentPower = evt.value.toString().toDouble()
    def appliances = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer", "hotWaterHeater": "Hot Water Heater", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
    
    appliances.each { key, name ->
        if (settings["${key}Power"]?.id == meterId) {
            
            // Spike Detection
            def spikeThreshold = settings["${key}Spike"]?.toString()?.toDouble() ?: 5000
            if (currentPower > spikeThreshold) {
                if (!state["${key}_spikePending"]) {
                    state["${key}_spikePending"] = true
                    runIn(60, "confirmSpike_${key}")
                }
            } else if (currentPower < (spikeThreshold * 0.8)) {
                state["${key}_spikePending"] = false
                
                if (state["${key}_spikeWarning"]) {
                    state["${key}_spikeWarning"] = false 
                    checkAllClearStatus() // Alert removed, check system
                }
                unschedule("confirmSpike_${key}")
            }
            
            def runThreshold = settings["${key}RunWatts"]?.toString()?.toDouble() ?: 15.0
            def isCurrentlyRunning = (currentPower > runThreshold)
            def wasRunning = state["${key}_isRunning"] ?: false
            
            // Stage Classification (Variable Speed)
            if (settings["${key}VariableSpeed"]) {
                def highWatts = settings["${key}HighWatts"]?.toString()?.toDouble() ?: 120.0
                def defrostWatts = settings["${key}DefrostWatts"]?.toString()?.toDouble() ?: 350.0
                
                if (currentPower >= defrostWatts) {
                    state["${key}_currentStage"] = "Defrost"
                } else if (currentPower >= highWatts) {
                    state["${key}_currentStage"] = "High"
                } else if (currentPower > runThreshold) {
                    state["${key}_currentStage"] = "Low"
                }
            } else {
                state["${key}_currentStage"] = "Running"
            }
            
            // Learn Idle
            if (!isCurrentlyRunning && currentPower > 0.0) {
                def currentIdle = state["${key}_idlePowerAvg"]?.toString()?.toDouble() ?: currentPower
                state["${key}_idlePowerAvg"] = (currentIdle == 0.0) ? currentPower : ((currentIdle * 0.95) + (currentPower * 0.05))
            }
            
            if (isCurrentlyRunning) {
                if (state["${key}_stopPending"]) {
                    state["${key}_stopPending"] = false
                    unschedule("checkCycleComplete_${key}")
                }
                
                if (!wasRunning && !state["${key}_startPending"]) {
                    state["${key}_startPending"] = true
                    state["${key}_tentativeStartTime"] = now()
                    
                    def startDelayMins = settings["${key}StartDelay"]?.toString()?.toInteger() ?: 1
                    if (startDelayMins > 0) {
                        runIn(startDelayMins * 60, "confirmCycleStart_${key}")
                    } else {
                        startCycle(key) 
                    }
                } else if (wasRunning && state["${key}_cycleStartTime"]) {
                    // AUTO-HEAL: If the state is stuck running for more than 12 hours, reset it
                    def hoursRunning = (now() - state["${key}_cycleStartTime"]) / 3600000.0
                    if (hoursRunning > 12.0) {
                        logInfo("Ghost cycle detected for ${name} (${Math.round(hoursRunning)} hrs). Auto-healing state machine.")
                        state["${key}_cycleStartTime"] = now()
                        state["${key}_startPending"] = false
                    }
                }
            } else {
                if (state["${key}_startPending"]) {
                    state["${key}_startPending"] = false
                    unschedule("confirmCycleStart_${key}")
                    state.remove("${key}_tentativeStartTime")
                }
                
                if (wasRunning && !state["${key}_stopPending"]) {
                    state["${key}_stopPending"] = true
                    state["${key}_compressorOffTime"] = now() // Mark the time compressor ended
                    def debounceMins = settings["${key}Debounce"]?.toString()?.toInteger() ?: 15
                    runIn(debounceMins * 60, "checkCycleComplete_${key}")
                }
                
                // Execute pending Ambient Pause
                if (state["${key}_pausePending"]) {
                    checkIdleAndPause(key)
                }
            }
            
            if (currentPower > 10) {
                def currentAvg = state["${key}_avgPower"]?.toString()?.toDouble() ?: currentPower
                state["${key}_avgPower"] = (currentAvg * 0.95) + (currentPower * 0.05)
            }
        }
    }
}

def tempHandler(evt) {
    def deviceId = evt.device.id
    def currentTemp = evt.value.toString().toDouble()
    def targets = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer"]
    
    targets.each { key, name ->
        if (settings["${key}Temp"]?.id == deviceId) {
            
            // 1. Maintain Sparkline Temp History Array
            def tList = state["${key}_tempHistory"] ?: []
            tList.add(currentTemp)
            if (tList.size() > 30) tList = tList[-30..-1] // Keep last 30 data points for trend
            state["${key}_tempHistory"] = tList
            
            // --- Ambient Pause Failsafe ---
            if (state["${key}_ambientPaused"]) {
                def internalFailsafe = settings["${key}InternalFailsafeTemp"]?.toString()?.toDouble() ?: 10.0
                if (currentTemp >= internalFailsafe) {
                    resumeFromAmbientPause(key, "Internal temp reached failsafe limit (${currentTemp}°)")
                }
            }
            
            // 2. Critical Threshold Warning
            def threshold = settings["${key}TempThreshold"]?.toString()?.toDouble()
            if (threshold != null && currentTemp >= threshold) {
                if (!state["${key}_tempWarningActive"]) {
                    state["${key}_tempWarningActive"] = true
                    cancelAllClear() // Interruption
                    
                    sendAlert("🚨 ${name} CRITICAL TEMP: Current temp is ${currentTemp}°, exceeding the safe threshold of ${threshold}°!", key, "temp")
                }
            } else if (threshold != null && currentTemp < (threshold - 1.0)) {
                if (state["${key}_tempWarningActive"]) {
                    state["${key}_tempWarningActive"] = false
                    checkAllClearStatus() // Alert removed, check system
                }
            }
            
            // 3. Baseline Temp Creep Detection (Made significantly less strict)
            def avgTemp = state["${key}_avgTemp"]?.toString()?.toDouble() ?: currentTemp
            def baselineTemp = state["${key}_baselineTemp"]?.toString()?.toDouble() ?: avgTemp
            
            state["${key}_avgTemp"] = (avgTemp * 0.95) + (currentTemp * 0.05)
            
            def isGracePeriod = state["${key}_restockGracePeriodUntil"] && now() < state["${key}_restockGracePeriodUntil"]
            
            if (currentTemp > (baselineTemp + 12.0)) {
                if (!state["${key}_tempCreepWarning"] && !isGracePeriod) {
                    state["${key}_tempCreepWarning"] = true
                    cancelAllClear() // Interruption
                    
                    sendAlert("⚠️ ${name} TEMPERATURE CREEP: Baseline is ${Math.round(baselineTemp)}°, but average is creeping up (${currentTemp}°).", key, "health")
                }
            } else if (currentTemp <= (baselineTemp + 4.0)) {
                if (state["${key}_tempCreepWarning"]) {
                    state["${key}_tempCreepWarning"] = false
                    checkAllClearStatus() // Alert removed, check system
                }
            }
        }
    }
}

def confirmCycleStart_refrigerator() { startCycle("refrigerator") }
def confirmCycleStart_chestFreezer() { startCycle("chestFreezer") }
def confirmCycleStart_hotWaterHeater() { startCycle("hotWaterHeater") }
def confirmCycleStart_washerDryer()  { startCycle("washerDryer") }
def confirmCycleStart_dishwasher()   { startCycle("dishwasher") }
def confirmCycleStart_microwave()    { startCycle("microwave") }

def startCycle(key) {
    if (state["${key}_startPending"]) {
        state["${key}_startPending"] = false
        state["${key}_isRunning"] = true
        state["${key}_cycleStartTime"] = state["${key}_tentativeStartTime"] ?: now()
        
        // --- HVAC Synergy Check ---
        if ((key == "washerDryer" || key == "dishwasher") && settings["${key}HVACNudge"]) {
            def outSensor = settings["${key}OutsideTempRef"]
            if (outSensor) {
                def outTemp = outSensor.currentValue("temperature")?.toString()?.toDouble()
                def hotThresh = settings["${key}OutsideHotTemp"]?.toString()?.toDouble() ?: 85.0
                if (outTemp != null && outTemp >= hotThresh) {
                    def appName = key == "washerDryer" ? "Washer/Dryer" : "Dishwasher"
                    
                    // Check for nighttime suppression (7 PM to 5 AM)
                    Calendar cal = Calendar.getInstance(location.timeZone)
                    def currentHour = cal.get(Calendar.HOUR_OF_DAY)
                    def isNightWindow = (currentHour >= 19 || currentHour < 5)
                    
                    if (isNightWindow) {
                        logAction("HVAC Synergy Event: ${appName} started during heat (${outTemp}°), but suppressed alert due to nighttime window (7 PM - 5 AM).")
                    } else {
                        def msg = "🌡️ HVAC Synergy: ${appName} started while outside temp is ${outTemp}°. Running this now dumps heat into the house while AC is working hard."
                        sendAlert(msg, key, "health")
                        logAction("HVAC Synergy Event: ${appName} started during peak heat (${outTemp}°). Logged for power usage comparison.")
                    }
                }
            }
        }
        
        if (settings["${key}Temp"]) {
            state["${key}_cycleStartTemp"] = settings["${key}Temp"].currentValue("temperature")?.toString()?.toDouble()
            runIn(1200, "efficiencyWatchdog_${key}") 
        }
    }
}

def efficiencyWatchdog_refrigerator() { evaluateCoolingEfficiency("refrigerator", "Refrigerator") }
def efficiencyWatchdog_chestFreezer() { evaluateCoolingEfficiency("chestFreezer", "Chest Freezer") }

def evaluateCoolingEfficiency(key, name) {
    if (state["${key}_isRunning"]) {
        def currentTemp = settings["${key}Temp"]?.currentValue("temperature")?.toString()?.toDouble()
        def startTemp = state["${key}_cycleStartTemp"]?.toString()?.toDouble()
        def avgCoolingRate = state["${key}_avgCoolingRate"]?.toString()?.toDouble() ?: 0.0
        
        // Smart Defrost & Grace Period Evaluation Lockout
        def currentStage = state["${key}_currentStage"]
        def isGracePeriod = state["${key}_restockGracePeriodUntil"] && now() < state["${key}_restockGracePeriodUntil"]
        
        if (currentStage == "Defrost" || isGracePeriod) {
            def reason = isGracePeriod ? "a user-access grace period" : "a Defrost cycle"
            logAction("Diagnostic Paused: ${name} is in ${reason}. Resetting efficiency baseline.")
            state["${key}_cycleStartTemp"] = currentTemp
            state["${key}_cycleStartTime"] = now()
            
            def wasAlert = state["${key}_efficiencyAlert"]
            state["${key}_efficiencyStrikes"] = 0
            state["${key}_efficiencyAlert"] = false
            if (wasAlert) checkAllClearStatus()
            
            runIn(1200, "efficiencyWatchdog_${key}") 
            return
        }

        if (currentTemp != null && startTemp != null) {
            def runMins = (now() - state["${key}_cycleStartTime"]) / 60000.0
            def expectedDrop = runMins * avgCoolingRate
            def actualDrop = startTemp - currentTemp

            // --- SMART SENSOR & SETPOINT LOGIC ---
            def critTemp = settings["${key}TempThreshold"]?.toString()?.toDouble()
            def hasContactSensor = settings["${key}Contact"] != null
            def isDoorClosed = hasContactSensor ? (state["${key}_doorCurrentState"] == "closed") : false
            def isDoorOpen = hasContactSensor ? (state["${key}_doorCurrentState"] == "open") : false

            // If door is closed and temperature is safely below critical threshold, unit is just maintaining temp.
            if (hasContactSensor && isDoorClosed && critTemp != null && currentTemp < critTemp) {
                if (state["${key}_efficiencyStrikes"] > 0) logAction("Diagnostic Cleared: ${name} is holding safe temp with door closed.")
                
                def wasAlert = state["${key}_efficiencyAlert"]
                state["${key}_efficiencyStrikes"] = 0
                state["${key}_efficiencyAlert"] = false
                if (wasAlert) checkAllClearStatus()
                
                runIn(1200, "efficiencyWatchdog_${key}")
                return
            }

            if (avgCoolingRate > 0.0) {
                if (actualDrop < (expectedDrop * 0.3)) {
                    def strikes = (state["${key}_efficiencyStrikes"] ?: 0) + 1
                    state["${key}_efficiencyStrikes"] = strikes
                    
                    if (strikes == 3) {
                        state["${key}_efficiencyAlert"] = true
                        cancelAllClear() // Interruption
                        
                        def alertMsg = "🚪 ${name} DIAGNOSTIC ALERT: The compressor has run continuously for ${Math.round(runMins)} minutes with impaired cooling."
                        if (isDoorOpen) alertMsg += " The door is currently OPEN."
                        else if (isDoorClosed) alertMsg += " The door is CLOSED, indicating a hardware leak or airflow issue."
                        else alertMsg += " The door is likely open or there is a hardware leak."

                        sendAlert(alertMsg, key, "health")
                        logAction("CRITICAL DIAGNOSTIC: ${name} efficiency curve failed 3 consecutive checks (60 mins). Alert sent.")
                    } else if (strikes < 3) {
                        logAction("Diagnostic Warning: ${name} missed cooling target. (Strike ${strikes}/3). Waiting for recovery.")
                    }
                } else {
                    if (state["${key}_efficiencyStrikes"] > 0) logAction("Diagnostic Recovery: ${name} efficiency returned to normal. Clearing strikes.")
                    
                    def wasAlert = state["${key}_efficiencyAlert"]
                    state["${key}_efficiencyStrikes"] = 0
                    state["${key}_efficiencyAlert"] = false
                    if (wasAlert) checkAllClearStatus() // Alert removed, check system
                }
            } else {
                if (currentTemp >= (startTemp - 0.2)) {
                    def strikes = (state["${key}_efficiencyStrikes"] ?: 0) + 1
                    state["${key}_efficiencyStrikes"] = strikes
                    
                    if (strikes == 3) {
                        state["${key}_efficiencyAlert"] = true
                        cancelAllClear() // Interruption
                        
                        def alertMsg = "🚪 ${name} DIAGNOSTIC ALERT: The unit has run for 60 minutes with no thermal drop."
                        if (isDoorOpen) alertMsg += " The door is currently OPEN."
                        else if (isDoorClosed) alertMsg += " The door is CLOSED. Check hardware."
                        else alertMsg += " Please check for an open door."

                        sendAlert(alertMsg, key, "health")
                        logAction("CRITICAL DIAGNOSTIC: ${name} running with no thermal change for 3 consecutive checks. Alert sent.")
                    } else if (strikes < 3) {
                        logAction("Diagnostic Warning: ${name} zero thermal drop. (Strike ${strikes}/3).")
                    }
                } else {
                    if (state["${key}_efficiencyStrikes"] > 0) logAction("Diagnostic Recovery: ${name} thermal drop detected. Clearing strikes.")
                    
                    def wasAlert = state["${key}_efficiencyAlert"]
                    state["${key}_efficiencyStrikes"] = 0
                    state["${key}_efficiencyAlert"] = false
                    if (wasAlert) checkAllClearStatus() // Alert removed, check system
                }
            }
            runIn(1200, "efficiencyWatchdog_${key}") 
        }
    }
}

def confirmSpike_refrigerator() { executeSpikeAlert("refrigerator", "Refrigerator") }
def confirmSpike_chestFreezer() { executeSpikeAlert("chestFreezer", "Chest Freezer") }
def confirmSpike_hotWaterHeater() { executeSpikeAlert("hotWaterHeater", "Hot Water Heater") }
def confirmSpike_washerDryer()  { executeSpikeAlert("washerDryer", "Washer/Dryer") }
def confirmSpike_dishwasher()   { executeSpikeAlert("dishwasher", "Dishwasher") }
def confirmSpike_microwave()    { executeSpikeAlert("microwave", "Microwave") }

def executeSpikeAlert(key, name) {
    if (state["${key}_spikePending"]) {
        state["${key}_spikePending"] = false
        if (!state["${key}_spikeWarning"]) {
            state["${key}_spikeWarning"] = true
            cancelAllClear() // Interruption
            
            sendAlert("⚡ ${name} power spike has persisted for over 60 seconds! Check for failing components.", key, "spike")
            logAction("CRITICAL: ${name} power spike has persisted for over 60 seconds!")
        }
    }
}

def checkCycleComplete_refrigerator() { finishCycle("refrigerator", "Refrigerator") }
def checkCycleComplete_chestFreezer() { finishCycle("chestFreezer", "Chest Freezer") }
def checkCycleComplete_hotWaterHeater() { finishCycle("hotWaterHeater", "Hot Water Heater") }
def checkCycleComplete_washerDryer()  { finishCycle("washerDryer", "Washer/Dryer") }
def checkCycleComplete_dishwasher()   { finishCycle("dishwasher", "Dishwasher") }
def checkCycleComplete_microwave()    { finishCycle("microwave", "Microwave") }

def finishCycle(key, name) {
    if (state["${key}_stopPending"] && state["${key}_isRunning"]) {
        state["${key}_isRunning"] = false
        state["${key}_stopPending"] = false
        unschedule("efficiencyWatchdog_${key}")
        state["${key}_cycleEndTime"] = now()
        
        if (state["${key}_cycleStartTime"]) {
            def debounceMins = settings["${key}Debounce"]?.toString()?.toInteger() ?: 15
            def debounceMs = debounceMins * 60000
            
            def cycleDurationMs = now() - state["${key}_cycleStartTime"] - debounceMs 
            if (cycleDurationMs < 0) cycleDurationMs = 0
            
            def cycleDurationHours = cycleDurationMs / 3600000.0
            def cycleDurationMins = cycleDurationMs / 60000.0
            
            state["${key}_lastRunLengthMins"] = cycleDurationMins
            state["${key}_7DayRunCount"] = (state["${key}_7DayRunCount"] ?: 0) + 1
            state["${key}_todayRunCount"] = (state["${key}_todayRunCount"] ?: 0) + 1
            state["${key}_totalRunHours"] = (state["${key}_totalRunHours"] ?: 0.0) + cycleDurationHours
            
            if (cycleDurationMins > 1) {
                sendAlert("✅ Your ${name} cycle is complete! (Ran for ${Math.round(cycleDurationMins)} mins)", key, "cycle")
                logAction("${name} completed a cycle spanning ${Math.round(cycleDurationMins)} minutes.")
                
                if (key == "washerDryer" || key == "dishwasher") {
                    if (settings["${key}FinishedSwitch"]) {
                        settings["${key}FinishedSwitch"].on()
                        runIn(600, "turnOffSwitch_${key}")
                        logAction("${name} virtual cycle-complete switch turned ON for 10 minutes.")
                    }
                }
            }
            
            if (key == "refrigerator" || key == "chestFreezer") {
                trackCompressorHealth(key, name, cycleDurationMins)
                updateCoolingCurve(key)
                
                // Clear the thermal recovery flag once the subsequent cycle has finished
                state["${key}_thermalRecovery"] = false
            }
        }
    }
}

def turnOffSwitch_washerDryer() {
    if (settings["washerDryerFinishedSwitch"]) {
        settings["washerDryerFinishedSwitch"].off()
        logAction("Washer/Dryer virtual cycle-complete switch automatically turned OFF.")
    }
}

def turnOffSwitch_dishwasher() {
    if (settings["dishwasherFinishedSwitch"]) {
        settings["dishwasherFinishedSwitch"].off()
        logAction("Dishwasher virtual cycle-complete switch automatically turned OFF.")
    }
}

def updateCoolingCurve(key) {
    def endTemp = settings["${key}Temp"]?.currentValue("temperature")?.toString()?.toDouble()
    def startTemp = state["${key}_cycleStartTemp"]?.toString()?.toDouble()
    def runMins = state["${key}_lastRunLengthMins"]?.toString()?.toDouble()
    
    if (startTemp != null && endTemp != null && runMins != null && runMins > 5.0 && startTemp > endTemp) {
        
        def wasAlert = state["${key}_efficiencyAlert"]
        state["${key}_efficiencyStrikes"] = 0
        state["${key}_efficiencyAlert"] = false
        if (wasAlert) checkAllClearStatus() // Alert removed, check system
        
        def rate = (startTemp - endTemp) / runMins 
        def avgRate = state["${key}_avgCoolingRate"]?.toString()?.toDouble() ?: rate
        
        state["${key}_avgCoolingRate"] = (avgRate * 0.8) + (rate * 0.2)
    }
}

def trackCompressorHealth(key, name, cycleDurationMins) {
    if (cycleDurationMins < 5) return
    
    if (state["${key}_thermalRecovery"]) {
        logAction("${name} completed a thermal recovery cycle (${Math.round(cycleDurationMins)}m). Bypassing long-cycle health diagnostics to avoid false alarms.")
        return
    }
    
    def baselineDuration = state["${key}_baselineCycleMins"]?.toString()?.toDouble() ?: cycleDurationMins
    
    // Initial baseline setting
    if (baselineDuration == 0.0 || baselineDuration == cycleDurationMins) {
        state["${key}_baselineCycleMins"] = cycleDurationMins
        return
    } 

    // Update baseline slowly (rolling average: 90% old, 10% new)
    state["${key}_baselineCycleMins"] = (baselineDuration * 0.90) + (cycleDurationMins * 0.10)
    
    // Compensate for door opens and learning phase
    def learnedDays = state["${key}_learningDays"] ?: 0
    def todayOpens = state["${key}_doorOpenCountToday"] ?: 0
    
    // Calculate dynamic threshold multiplier - Loosened significantly
    def thresholdMultiplier = 1.75 // Default 75% over
    if (learnedDays < 7) thresholdMultiplier = 2.20 // 120% over during early learning
    else if (learnedDays < 14) thresholdMultiplier = 2.0 // 100% over during late learning
    
    // Add a flat buffer for door opens (increased padding to 3.0 mins per open)
    def doorBuffer = todayOpens * 3.0 
    
    // Absolute minimums before we care. Increased to prevent false positives.
    def minConcernMins = (key == "refrigerator") ? 90.0 : 75.0
    
    def allowedDuration = Math.max(minConcernMins, (baselineDuration * thresholdMultiplier) + doorBuffer)
    
    def requiredStrikes = (learnedDays < 14) ? 5 : 3
    def criticalStrikes = (learnedDays < 14) ? 10 : 7

    if (cycleDurationMins > allowedDuration) {
        state["${key}_struggleCount"] = (state["${key}_struggleCount"] ?: 0) + 1
        
        if (state["${key}_struggleCount"] == requiredStrikes) {
            state["${key}_struggleWarning"] = true
            cancelAllClear() // Interruption
            
            sendAlert("🧹 ${name} DIAGNOSTIC: Compressor is consistently running longer than its dynamic baseline (${Math.round(allowedDuration)}m limit). Ensure coils are clean and airflow is clear.", key, "health")
            logAction("${name} triggered Long Cycle warning (Strike ${requiredStrikes}).")
            
        } else if (state["${key}_struggleCount"] >= criticalStrikes) {
            cancelAllClear() // Interruption
            sendAlert("⚠️ ${name} CRITICAL DIAGNOSTIC: Unit is severely struggling to cool over multiple cycles. Hardware failure may be imminent.", key, "health")
            state["${key}_struggleCount"] = 0 
        }
    } else {
        // Gradual strike reduction if it returns to normal
        if (state["${key}_struggleCount"] > 0) {
            state["${key}_struggleCount"] = state["${key}_struggleCount"] - 1
            if (state["${key}_struggleCount"] < requiredStrikes) {
                if (state["${key}_struggleWarning"]) {
                    state["${key}_struggleWarning"] = false
                    checkAllClearStatus() // Alert removed, check system
                }
            }
        }
    }
}

def dailyHealthCheck() {
    def appliances = ["refrigerator": "Refrigerator", "chestFreezer": "Chest Freezer", "hotWaterHeater": "Hot Water Heater", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]

    appliances.each { key, name ->
        if (settings["${key}Power"] || settings["${key}Switch"]) {
            state["${key}_learningDays"] = (state["${key}_learningDays"] ?: 0) + 1
        }
        
        def avgPower = state["${key}_avgPower"]?.toString()?.toDouble() ?: 0.0
        def baselineAvg = state["${key}_baselineAvg"]?.toString()?.toDouble() ?: avgPower
        
        // ONLY check for power creep on motors that should have a constant baseline
        if (key in ["refrigerator", "chestFreezer", "hotWaterHeater"]) {
            if (baselineAvg == 0.0) {
                state["${key}_baselineAvg"] = avgPower
            } else {
                // Increased threshold from 20% to 50% to ignore normal high-load cycles
                if (avgPower > (baselineAvg * 1.50)) {
                    if (!state["${key}_creepWarning"]) {
                        state["${key}_creepWarning"] = true
                        cancelAllClear() // Interruption
                        sendAlert("⚠️ ${name} average power is creeping up (${Math.round(avgPower)}W vs baseline ${Math.round(baselineAvg)}W). A motor/vent check is recommended.", key, "health")
                    }
                } else {
                    if (state["${key}_creepWarning"]) {
                        state["${key}_creepWarning"] = false
                        checkAllClearStatus() // Alert removed, check system
                    }
                }
                // Moved outside the 'else' block so it can continuously learn and self-correct
                state["${key}_baselineAvg"] = (baselineAvg * 0.98) + (avgPower * 0.02)
            }
        }
        
        if (key == "refrigerator" || key == "chestFreezer") {
            def totalHours = state["${key}_totalRunHours"]?.toString()?.toDouble() ?: 0.0
            def maintenanceInterval = settings["${key}MaintenanceHours"]?.toString()?.toDouble() ?: 2000.0
            
            if (totalHours > maintenanceInterval) {
                sendAlert("🔧 Routine Maintenance: Your ${name} has reached ${Math.round(totalHours)} run hours. Consider scheduling a preventative maintenance check.", key, "health")
                settings["${key}MaintenanceHours"] = maintenanceInterval + 2000.0 
            }
            
            if (state["${key}_avgTemp"]) {
                def currentAvgTemp = state["${key}_avgTemp"].toString().toDouble()
                def tempBaseline = state["${key}_baselineTemp"]?.toString()?.toDouble() ?: currentAvgTemp
                state["${key}_baselineTemp"] = (tempBaseline * 0.90) + (currentAvgTemp * 0.10)
            }
        }
    }
}

def resetDailyCounters() {
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    appliances.each { key ->
        state["${key}_todayRunCount"] = 0
        
        if (settings["${key}Contact"]) {
            def daysTracked = (state["${key}_doorDaysTracked"] ?: 0) + 1
            state["${key}_doorDaysTracked"] = daysTracked
            
            def totalDailyOpens = (state["${key}_doorTotalDailyOpens"] ?: 0) + (state["${key}_doorOpenCountToday"] ?: 0)
            state["${key}_doorTotalDailyOpens"] = totalDailyOpens
            
            state["${key}_doorAvgDailyOpens"] = totalDailyOpens / daysTracked
            state["${key}_doorOpenCountToday"] = 0
        }
    }
}

def resetWeeklyCounters() {
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    appliances.each { key ->
        state["${key}_Prev7DayRunCount"] = state["${key}_7DayRunCount"] ?: 0
        state["${key}_7DayRunCount"] = 0
    }
}

def roomTempHandler(evt) {
    def deviceId = evt.device.id
    def currentTemp = evt.value.toString().toDouble()
    def targets = ["refrigerator", "chestFreezer"]
    
    targets.each { key ->
        if (settings["${key}RoomTemp"]?.id == deviceId) {
            def avg = state["${key}_dailyAvgRoomTemp"]?.toString()?.toDouble() ?: 0.0
            state["${key}_dailyAvgRoomTemp"] = (avg == 0.0) ? currentTemp : ((avg * 0.95) + (currentTemp * 0.05))
            
            // --- Evaluate Ambient Pause ---
            if (settings["${key}EnableAmbientPause"]) {
                def pauseThresh = settings["${key}AmbientPauseTemp"]?.toString()?.toDouble() ?: 82.0
                def resumeThresh = settings["${key}AmbientResumeTemp"]?.toString()?.toDouble() ?: 78.0

                if (currentTemp >= pauseThresh && !state["${key}_ambientPaused"] && !state["${key}_pausePending"]) {
                    state["${key}_pausePending"] = true
                    logAction("Thermal Battery: Room temp hit ${currentTemp}°. Queueing ${key} for power pause once compressor idles.")
                    checkIdleAndPause(key)
                } else if (currentTemp <= resumeThresh && (state["${key}_ambientPaused"] || state["${key}_pausePending"])) {
                    resumeFromAmbientPause(key, "Room cooled down to ${currentTemp}°")
                }
            }
        }
    }
}

def outsideTempHandler(evt) {
    def deviceId = evt.device.id
    def currentTemp = evt.value.toString().toDouble()
    def targets = ["refrigerator", "chestFreezer"]
  
    targets.each { key ->
        if (settings["${key}OutsideTemp"]?.id == deviceId) {
            def currentMax = state["${key}_dailyMaxOutsideTemp"]?.toString()?.toDouble() ?: -100.0
            if (currentTemp > currentMax) state["${key}_dailyMaxOutsideTemp"] = currentTemp
        }
    }
}

def sendAlert(msg, key, alertType) {
    def aModes = settings["${key}AlertModes"]
    if (aModes && !aModes.contains(location.mode)) return
    
    def pushDev = settings["${key}PushNotification"]
    def pushEvents = settings["${key}PushEvents"] ?: []
    
    if (pushDev && pushEvents.contains(alertType)) {
        // Map an emoji dynamically based on the appliance key
        def appEmoji = [
            "refrigerator": "🧊",
            "chestFreezer": "❄️",
            "hotWaterHeater": "♨️",
            "washerDryer": "🧺",
            "dishwasher": "🍽️",
            "microwave": "🍿"
        ][key] ?: "🔌"
        
        // Prepend the custom appliance emoji to the existing message 
        def finalMsg = "${appEmoji} ${msg}"
        
        pushDev.deviceNotification(finalMsg)
    }
}

def checkIdleAndPause_refrigerator() { checkIdleAndPause("refrigerator") }
def checkIdleAndPause_chestFreezer() { checkIdleAndPause("chestFreezer") }

def checkIdleAndPause(key) {
    def isRunning = state["${key}_isRunning"] ?: false
    def pMeter = settings["${key}Power"]
    def currentPower = pMeter ? (pMeter.currentValue('power')?.toString()?.toDouble() ?: 0.0) : 0.0
    def runThreshold = settings["${key}RunWatts"]?.toString()?.toDouble() ?: 15.0

    if (state["${key}_pausePending"] && currentPower < runThreshold && !isRunning) {
        def offTime = state["${key}_compressorOffTime"] ?: 0
        def timeSinceOff = (now() - offTime) / 60000.0
        
        if (timeSinceOff >= 2.0 || offTime == 0) {
            state["${key}_pausePending"] = false
            state["${key}_ambientPaused"] = true
            state["${key}_ambientPauseStartTime"] = now()
            settings["${key}Switch"]?.off()
            logAction("Thermal Battery: ${key} compressor has been idle for 2+ mins. Power cut to avoid high ambient heat. Tracking for power savings log.")
        } else {
            def waitMins = Math.ceil(2.0 - timeSinceOff).toInteger()
            if (waitMins < 1) waitMins = 1
            runIn(waitMins * 60, "checkIdleAndPause_${key}")
            logInfo("Thermal Battery: Waiting ${waitMins} more minute(s) for ${key} compressor to stabilize before pausing.")
        }
    }
}

def resumeFromAmbientPause(key, reason) {
    state["${key}_pausePending"] = false
    if (state["${key}_ambientPaused"]) {
        state["${key}_ambientPaused"] = false
        settings["${key}Switch"]?.on()
        
        def pauseDurationMins = (now() - (state["${key}_ambientPauseStartTime"] ?: now())) / 60000.0
        def pauseDurationHours = pauseDurationMins / 60.0
        
        state["${key}_totalAmbientPauseHours"] = (state["${key}_totalAmbientPauseHours"] ?: 0.0) + pauseDurationHours
        def pauseDuration = Math.round(pauseDurationMins)
        state["${key}_lastPauseDuration"] = pauseDuration
        
        // Flag the next cycle as a thermal recovery cycle so it doesn't trigger the long-cycle alarm
        state["${key}_thermalRecovery"] = true
        
        logAction("Thermal Battery: Resumed ${key} (${reason}). Unit was paused for ${pauseDuration} minutes.")
    }
}

// ==============================================================================
// GLOBAL SYSTEM ALL-CLEAR WATCHDOG
// ==============================================================================

def checkAllClearStatus() {
    def anyAlerts = false
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    
    appliances.each { key ->
        if (state["${key}_doorAlertActive"] || 
            state["${key}_efficiencyAlert"] || 
            state["${key}_spikeWarning"] || 
            state["${key}_tempWarningActive"] || 
            state["${key}_tempCreepWarning"] || 
            state["${key}_creepWarning"] ||
            state["${key}_struggleWarning"]) {
            
            anyAlerts = true
        }
    }
    
    // If every single alert in the system is resolved, queue the 60 min timer
    if (!anyAlerts) {
        if (!state.allClearPending) {
            state.allClearPending = true
            runIn(3600, "sendAllClearNotification") // 60 minutes
            logInfo("All active alerts have cleared. 60-minute all-clear countdown started.")
        }
    }
}

def cancelAllClear() {
    // If a new alert arrives while we are waiting the 60 mins, cancel the timer
    if (state.allClearPending) {
        unschedule("sendAllClearNotification")
        state.allClearPending = false
        logInfo("New alert detected. 60-minute all-clear countdown cancelled.")
    }
}

def sendAllClearNotification() {
    state.allClearPending = false
    def msg = "✅ System All Clear: All appliances have returned to normal operation and are running efficiently."
    logAction(msg)
    
    // Compile a unique list of ALL push devices used across all configured appliances
    def allPushDevs = []
    def appliances = ["refrigerator", "chestFreezer", "hotWaterHeater", "washerDryer", "dishwasher", "microwave"]
    
    appliances.each { key ->
        if (settings["${key}PushNotification"]) {
            settings["${key}PushNotification"].each { dev ->
                allPushDevs << dev
            }
        }
    }
    
    // Send the notification to each device only once
    allPushDevs.unique { it.id }.each { dev ->
        dev.deviceNotification(msg)
    }
}
