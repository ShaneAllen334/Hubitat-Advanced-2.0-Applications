/**
 * Advanced Trash Day Reminder 2.0
 */

definition(
    name: "Advanced Trash Day Reminder 2.0",
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

def renderTableHTML() {
    def nPickup = state.nextPickupStr ?: "Recalculating Operational Target..."
    def isRecycleWeek = checkRecyclingWeek()
    def recycleStr = isRecycleWeek ? "<span style='color:#3498db; font-weight:bold;'>Active Vector (Trash + Recycling)</span>" : "Standard Sequence (Trash Only)"
    def schedMode = state.predictiveActive ? "Predictive AI" : "Static Schedule"
    
    def battLvl = binMultiSensor ? binMultiSensor.currentValue("battery") : null
    def battDisplay = battLvl != null ? (battLvl <= 15 ? "<span style='color:#e74c3c; font-weight:bold;'>${battLvl}% (LOW)</span>" : "${battLvl}%") : "Offline"
    def sensorHealthStr = state.isSensorDead ? "<span style='color:red; font-weight:bold;'>OFFLINE</span>" : "<span style='color:green; font-weight:bold;'>ONLINE</span>"
    
    int fillPct = getFillPct()
    def fillBarColor = fillPct > 85 ? "red" : (fillPct > 50 ? "orange" : "green")
    
    int bagCount = state.lidOpens ?: 0
    int maxBags = settings.estimatedMaxOpens ?: 8
    
    def ecoScore = getEcoScoreBadge()
    def hygStatus = state.hygieneStatus ?: "Clean ✨"

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
        <thead><tr><th>Metric</th><th>Current Value</th><th>Status / Calculated Detail</th></tr></thead>
        <tbody>
            <tr><td colspan="3" class="dash-subhead">Intercept Target & Logistics</td></tr>
            <tr><td class="dash-hl">Target Timeline</td><td colspan="2" class="dash-val"><b>${nPickup}</b></td></tr>
            <tr><td class="dash-hl">Active Vector</td><td colspan="2" class="dash-val">${recycleStr}</td></tr>
            <tr><td class="dash-hl">Scheduling Mode</td><td colspan="2" class="dash-val">${schedMode}</td></tr>

            <tr><td colspan="3" class="dash-subhead">Volume & Environmental Audit</td></tr>
            <tr><td class="dash-hl">Mass Metrics (Fill Level)</td><td><b><span style='color:${fillBarColor}'>${fillPct}% (${bagCount}/${maxBags} Bags)</span></b></td><td>Envelope Stack: ${ecoScore}</td></tr>
            <tr><td class="dash-hl">Container Hygiene</td><td colspan="2" class="dash-val">${hygStatus}</td></tr>

            <tr><td colspan="3" class="dash-subhead">System Performance</td></tr>
            <tr><td class="dash-hl">Hardware Subsystem</td><td><b>${sensorHealthStr}</b></td><td>Power Vector Core: ${battDisplay}</td></tr>
            <tr><td class="dash-hl">Current Surface Profile</td><td colspan="2" class="dash-val">${settings.drivewaySurface ?: "Smooth Pavement"}</td></tr>
        </tbody>
    </table>
    """
    return dashHTML
}

String getDashStatusHTML() {
    if (state.isSensorDead) return "<span style='color:red; font-size:14px;'><b>🚨 CRITICAL: SENSORS OFFLINE. Prediction Engine Halted. 🚨</b></span>"
    
    if (state.isNagging || (trashSwitch && trashSwitch.currentValue("switch") == "on")) {
        return "<span style='color:red;'><b>PENDING DISPATCH:</b></span> Deploy container to curb side zone immediately."
    } else if (state.binStatus == "curb_full") {
        return "<span style='color:orange;'><b>CONTAINER AT CURB:</b></span> Awaiting processing fleet intercept."
    } else if (state.binStatus == "curb_emptied") {
        return "<span style='color:green;'><b>DISPOSAL VERIFIED:</b></span> Container structural matrix cleared. Return to base."
    } else if (state.binStatus == "curb_missed") {
        return "<span style='color:purple;'><b>SLA DISRUPTION REPORTED:</b></span> Fleet missed scheduled intercept window."
    }
    return "<span style='color:green;'><b>Tracking Stable Operations.</b></span> Container secured at resting baseline posture."
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefreshData", "button", title: "🔄 Refresh Data"
            
            checkSensorHealth()
            def statusExplanation = getDashStatusHTML()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            paragraph renderTableHTML()
            
            def tSwitch = trashSwitch?.currentValue("switch") == "on" ? "<span style='color:red; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
            def eSwitch = switchEmptyTrigger?.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON (Post-Empty)</span>" : "<span style='color:gray;'>OFF</span>"
            def hSwitch = switchHygiene?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON (Dirty)</span>" : "<span style='color:gray;'>OFF</span>"
            def fSwitch = switchFull?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON (Full)</span>" : "<span style='color:gray;'>OFF</span>"
            
            paragraph "<div style='padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>" +
                      "<b>Virtual Outputs:</b> Task Monitor: [${tSwitch}] | Post-Empty: [${eSwitch}] <br>" +
                      "<b>Alert Outputs:</b> Hygiene Alert: [${hSwitch}] | Full Alert: [${fSwitch}]</div>"
        }

        if (app.id) {
            section("<b>Global Actions & Overrides</b>", hideable: true, hidden: true) {
                input "btnCalibrate", "button", title: "📐 Calibrate 3D Spatial Baseline"
                input "btnCreateChild", "button", title: "🖥️ Re-link Dashboard Child Tile"
                
                paragraph "<hr>"
                input "btnSetHouse", "button", title: "🏠 Force State: House"
                input "btnSetCurbFull", "button", title: "📦 Force State: Curb (Full)"
                input "btnSetCurbEmptied", "button", title: "✅ Force State: Curb (Emptied)"
                input "btnSetMissed", "button", title: "⚠️ Force State: Missed Pickup"
                
                paragraph "<hr>"
                input "btnResetAI", "button", title: "🧠 Reset AI Predictive Memory"
                input "btnWashBin", "button", title: "🧽 Clear Hygiene Status (Log Wash)"
                input "btnHoliday", "button", title: state.holidayShift ? "Cancel Holiday Shift" : "Force Holiday Shift"
            }
        }
        
        section("<b>Action History & Debugging</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            } else {
                paragraph "<i>No history logged yet.</i>"
            }
            input "btnResetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>1. Schedule Mapping & Prediction AI</b>", hideable: true, hidden: true) {
            input "trashDays", "enum", title: "Municipal Disposal Sequence Target Day(s)", options: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"], multiple: true, required: true, submitOnChange: true
            input "pickupTime", "time", title: "Baseline Routine Static Target Time", required: true, submitOnChange: true
            input "reminderTime", "time", title: "Reminder Time (Day Before Pickup)", required: true, submitOnChange: true
            
            input "usePredictiveTiming", "bool", title: "Enable AI Telemetry Collection (Predictive Timing)", defaultValue: true, submitOnChange: true
            input "enableRecycling", "bool", title: "Enable Bi-Weekly Alternating Recycling Matrix", defaultValue: false, submitOnChange: true
            if (enableRecycling) {
                input "recycleWeek", "enum", title: "Target Recycling Matrix Routine Day:", options: ["Even Weeks", "Odd Weeks"], required: true
            }
            
            input "autoHoliday", "bool", title: "Enable Automatic Calendar Interruption Routing", defaultValue: true, submitOnChange: true
            if (autoHoliday) {
                input "selectedHolidays", "enum", title: "Recognized Interruption Holidays", options: ["New Year's Day", "Memorial Day", "Independence Day", "Labor Day", "Thanksgiving", "Christmas"], multiple: true, required: true, defaultValue: ["New Year's Day", "Memorial Day", "Independence Day", "Labor Day", "Thanksgiving", "Christmas"]
            }
            
            paragraph "<hr><b>Automated Failsafes</b>"
            input "enableAutoReturn", "bool", title: "Enable Automated 8:00 PM Return (Failsafe)", defaultValue: true, submitOnChange: true
            if (enableAutoReturn) {
                input "occupiedModes", "mode", title: "Select Occupied Location Modes (Required for Auto-Return)", multiple: true, required: true
            }
        }

        section("<b>2. Hardware Sensors & Physics Learning</b>", hideable: true, hidden: true) {
            input "binMultiSensor", "capability.threeAxis", title: "Samsung Multipurpose Sensor Hardware Target", required: true, submitOnChange: true
            input "estimatedMaxOpens", "number", title: "Calculated Envelope Mass Volumetric Limits (Bags to Full)", defaultValue: 8, required: true
            
            paragraph "<hr><b>Action Learning & Calibration</b>"
            paragraph "<i>To train the AI, tap a training button below, physically perform the action as normal, then tap <b>Finish Training</b>. This builds a custom physics profile for each movement. Empties from the truck are automatically managed and don't need training.</i>"
            
            if (state.learnMode && state.learnMode != "none") {
                def modeText = state.learnMode == "lid" ? "Lifting Lid" : (state.learnMode == "curb" ? "Walking to Curb" : "Walking to House")
                paragraph "<div style='color:red; font-weight:bold; font-size:15px; border:2px solid red; padding:10px; border-radius:5px;'>🔴 Recording Telemetry for: ${modeText}... Please perform the action now.</div>"
                input "btnStopTraining", "button", title: "⏹️ Finish Training"
            } else {
                input "btnTrainLid", "button", title: "🔴 Train: Lifting Lid"
                input "btnTrainCurb", "button", title: "🔴 Train: Walk to Curb"
                input "btnTrainHouse", "button", title: "🔴 Train: Walk to House"
            }
            
            input "drivewaySurface", "enum", title: "Active Driveway Surface Friction/Jolt Profile", options: ["Smooth Pavement", "Mixed / Patchy", "Fine Gravel", "Chunky / Rough Gravel", "Custom (Learned / Manual)"], defaultValue: "Smooth Pavement", required: true, submitOnChange: true
            
            if (drivewaySurface == "Custom (Learned / Manual)") {
                input "transitCurbTime", "number", title: "Walk to Curb: Min Time (s)", defaultValue: 10, required: false
                input "transitCurbTilt", "number", title: "Walk to Curb: Tilt Threshold", defaultValue: 150, required: false
                input "transitHouseTime", "number", title: "Walk to House: Min Time (s)", defaultValue: 10, required: false
                input "transitHouseTilt", "number", title: "Walk to House: Tilt Threshold", defaultValue: 150, required: false
                input "lidOpenThreshold", "number", title: "Lid Inversion Tilt Threshold", defaultValue: 450, required: false
            }
        }

        section("<b>3. Extreme Weather Overrides & Failsafes</b>", hideable: true, hidden: true) {
            paragraph "<i>Virtual input sensors block data fluctuations during highly kinetic weather incidents.</i>"
            input "swTornado", "capability.switch", title: "Tornado Incident Emergency Switch", required: false
            input "swThunderstorm", "capability.switch", title: "Severe Thunderstorm Alarm Switch", required: false
            input "swRain", "capability.switch", title: "Active Precipitation Sensor Switch", required: false
            input "swSprinkle", "capability.switch", title: "Light Precipitation Sensor Switch", required: false
            
            input "enableFallenBin", "bool", title: "Enable Structural Dislocation (Fallen Bin) Notifications", defaultValue: true, submitOnChange: true
        }

        section("<b>4. Push Notifications & Alert Routing</b>", hideable: true, hidden: true) {
            paragraph "<i>Route specific alerts to preferred mobile devices. Explanations will automatically include informative emojis.</i>"
            
            input "notifyReminderDevices", "capability.notification", title: "🔔 Send Pre-Collection Reminders To:", multiple: true, required: false
            input "notifyEmptiedDevices", "capability.notification", title: "🚛 Send Successful Pickup (Emptied) Alerts To:", multiple: true, required: false
            input "notifyReturnedDevices", "capability.notification", title: "🏠 Send Returned Home Alerts To:", multiple: true, required: false
            input "notifyMissedDevices", "capability.notification", title: "⚠️ Send Missed Pickup Alerts To:", multiple: true, required: false
            input "notifyFullDevices", "capability.notification", title: "📦 Send Container Full/Saturation Warnings To:", multiple: true, required: false
            input "notifyErrorDevices", "capability.notification", title: "🚨 Send Error, Dislocation, & Bio-Hazard Alerts To:", multiple: true, required: false
        }
        
        section("<b>5. Virtual Automation Integrations</b>", hideable: true, hidden: true) {
            input "trashSwitch", "capability.switch", title: "Task Monitor Switch (Turns ON when container needs to be taken out)", required: false
            input "switchEmptyTrigger", "capability.switch", title: "Post-Pickup Switch (Turns ON for 10 minutes when bin is emptied)", required: false
            input "switchHygiene", "capability.switch", title: "Hygiene Alert Switch (Turns ON when bin needs washing)", required: false
            input "switchFull", "capability.switch", title: "Bin Full Alert Switch (Turns ON when container is full)", required: false
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
    if (!state.binStatus) state.binStatus = "house"
    if (state.holidayShift == null) state.holidayShift = false
    if (state.autoHolidayTriggered == null) state.autoHolidayTriggered = false
    if (!state.lidOpens) state.lidOpens = 0
    if (!state.lastLidOpenTime) state.lastLidOpenTime = 0
    if (!state.lastWashed) state.lastWashed = now()
    if (!state.maxTempSinceWash) state.maxTempSinceWash = 70
    if (state.isSensorDead == null) state.isSensorDead = false
    if (!state.learnMode) state.learnMode = "none"
    
    if (!state.historyPutOut) state.historyPutOut = []
    if (!state.historyEmptied) state.historyEmptied = []
    if (!state.historyReturned) state.historyReturned = []
    if (!state.historyFullness) state.historyFullness = []
    
    if (trashSwitch) subscribe(trashSwitch, "switch.off", ackHandler)
    
    if (binMultiSensor) {
        subscribe(binMultiSensor, "threeAxis", axisSpatialHandler)
        subscribe(binMultiSensor, "acceleration.active", binMoveActiveHandler)
        subscribe(binMultiSensor, "acceleration.inactive", binMoveInactiveHandler)
        subscribe(binMultiSensor, "temperature", tempHandler)
    }
    
    schedule("0 5 0 * * ?", updateSchedule) 
    schedule("0 0 20 * * ?", autoReturnCheck) // 8:00 PM Daily Check
    
    updateSchedule()
    updateHygiene()
    checkSensorHealth()
    pushChildUpdate()
    logAction("App Initialized.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefreshData") {
        checkSensorHealth()
        logAction("MANUAL: Dashboard data refreshed.")
    } else if (btn == "btnSetHouse") {
        state.binStatus = "house"
        state.lidOpens = 0
        logAction("MANUAL OVERRIDE: Bin forced to 'House'.")
    } else if (btn == "btnSetCurbFull") {
        state.binStatus = "curb_full"
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        logAction("MANUAL OVERRIDE: Bin forced to 'At Curb (Full)'.")
    } else if (btn == "btnSetCurbEmptied") {
        state.binStatus = "curb_emptied"
        state.lidOpens = 0
        logAction("MANUAL OVERRIDE: Bin forced to 'At Curb (Emptied)'.")
    } else if (btn == "btnSetMissed") {
        state.binStatus = "curb_missed"
        state.missedTime = now()
        logAction("MANUAL OVERRIDE: Bin forced to 'Missed Pickup'.")
    } else if (btn == "btnResetAI") {
        state.historyEmptied = []
        state.predictiveActive = false
        updateSchedule()
        logAction("SYSTEM OVERRIDE: Predictive AI History Cleared. Resetting to baseline schedule.")
    } else if (btn == "btnHoliday") {
        state.holidayShift = !state.holidayShift
        logAction("MANUAL: Holiday Shift " + (state.holidayShift ? "Activated" : "Deactivated"))
        updateSchedule()
    } else if (btn == "btnWashBin") {
        state.lastWashed = now()
        def tempVal = binMultiSensor?.currentValue("temperature")
        state.maxTempSinceWash = tempVal != null ? tempVal : 70
        updateHygiene()
        logAction("MAINTENANCE: Bin hygiene manually reset to 'Clean'.")
    } else if (btn == "btnCalibrate") {
        calibrateSpatialBaseline()
    } else if (btn == "btnTrainLid") {
        state.learnMode = "lid"
        state.learnStartTime = now()
        state.learnMaxTilt = 0
        logAction("DIAGNOSTICS: Lid Calibration Started. Please open and close the lid.")
    } else if (btn == "btnTrainCurb") {
        state.learnMode = "curb"
        state.learnStartTime = now()
        state.learnMaxTilt = 0
        logAction("DIAGNOSTICS: Curb Walk Calibration Started. Please walk the bin to the road.")
    } else if (btn == "btnTrainHouse") {
        state.learnMode = "house"
        state.learnStartTime = now()
        state.learnMaxTilt = 0
        logAction("DIAGNOSTICS: House Walk Calibration Started. Please walk the bin back to the house.")
    } else if (btn == "btnStopTraining") {
        long duration = (now() - (state.learnStartTime ?: now())) / 1000
        int recordedMaxTilt = state.learnMaxTilt ?: 0
        
        if (state.learnMode == "lid") {
            int calcTilt = Math.max(100, (recordedMaxTilt * 0.85).toInteger())
            app.updateSetting("lidOpenThreshold", [type: "number", value: calcTilt])
            logAction("DIAGNOSTICS: Lid Calibration Saved! Max Tilt: ${recordedMaxTilt}. Threshold set to: ${calcTilt}.")
        } else if (state.learnMode == "curb") {
            int calcTime = Math.max(3, (duration * 0.75).toInteger())
            int calcTilt = Math.max(50, (recordedMaxTilt * 0.75).toInteger())
            app.updateSetting("transitCurbTime", [type: "number", value: calcTime])
            app.updateSetting("transitCurbTilt", [type: "number", value: calcTilt])
            logAction("DIAGNOSTICS: Curb Calibration Saved! Walk time: ${duration}s. Max Tilt: ${recordedMaxTilt}. Set to >${calcTime}s / >${calcTilt}.")
        } else if (state.learnMode == "house") {
            int calcTime = Math.max(3, (duration * 0.75).toInteger())
            int calcTilt = Math.max(50, (recordedMaxTilt * 0.75).toInteger())
            app.updateSetting("transitHouseTime", [type: "number", value: calcTime])
            app.updateSetting("transitHouseTilt", [type: "number", value: calcTilt])
            logAction("DIAGNOSTICS: House Calibration Saved! Walk time: ${duration}s. Max Tilt: ${recordedMaxTilt}. Set to >${calcTime}s / >${calcTilt}.")
        }
        
        app.updateSetting("drivewaySurface", [type: "enum", value: "Custom (Learned / Manual)"])
        state.learnMode = "none"
    } else if (btn == "btnCreateChild") {
        def childDev = getChildDevice("trash_dash_${app.id}")
        if (!childDev) {
            try {
                childDev = addChildDevice("hubitat", "Virtual Device", "trash_dash_${app.id}", null, [name: "Trash Dashboard", label: "Trash Dashboard Tile"])
                logAction("SYSTEM: Virtual Dashboard Device Created successfully.")
            } catch (e) { log.error "Failed to create child device. ${e}" }
        } else {
            logAction("SYSTEM: Virtual Dashboard Device already exists.")
        }
    } else if (btn == "btnResetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    }
    pushChildUpdate()
}

// ------------------------------------------------------------------------------
// NOTIFICATION ENGINE & DASHBOARD UPDATES
// ------------------------------------------------------------------------------

def sendAlert(msg, pushDevices) {
    if (pushDevices) {
        pushDevices*.deviceNotification(msg)
    }
}

def pushChildUpdate() {
    evaluateFullnessSwitch()
    evaluateHygieneSwitch()
    
    def childDev = getChildDevice("trash_dash_${app.id}")
    if (childDev) {
        def html = renderTableHTML()
        childDev.sendEvent(name: "htmlTile", value: html, descriptionText: "Updated Dashboard HTML")
    }
}

def evaluateFullnessSwitch() {
    if (switchFull) {
        int pct = getFillPct()
        if (pct >= 100) {
            if (switchFull.currentValue("switch") != "on") {
                try { switchFull.on() } catch(e) { log.error "Failed to turn ON Full Switch: ${e.message}" }
            }
        } else {
            if (switchFull.currentValue("switch") != "off") {
                try { switchFull.off() } catch(e) { log.error "Failed to turn OFF Full Switch: ${e.message}" }
            }
        }
    }
}

def evaluateHygieneSwitch() {
    if (switchHygiene) {
        if (state.hygieneStatus != null && state.hygieneStatus != "Clean ✨") {
            if (switchHygiene.currentValue("switch") != "on") {
                try { switchHygiene.on() } catch(e) { log.error "Failed to turn ON Hygiene Switch: ${e.message}" }
            }
        } else {
            if (switchHygiene.currentValue("switch") != "off") {
                try { switchHygiene.off() } catch(e) { log.error "Failed to turn OFF Hygiene Switch: ${e.message}" }
            }
        }
    }
}

// ------------------------------------------------------------------------------
// RECYCLING 
// ------------------------------------------------------------------------------

boolean checkRecyclingWeek() {
    if (!settings.enableRecycling || !settings.recycleWeek) return false
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    
    long offset = tz.getOffset(cal.getTimeInMillis())
    long localTime = cal.getTimeInMillis() + offset
    long daysSinceEpoch = (localTime / 86400000L) as Long
    long totalWeeks = ((daysSinceEpoch + 4) / 7) as Long
    
    boolean isEven = (totalWeeks % 2 == 0)
    
    if (settings.recycleWeek == "Even Weeks" && isEven) return true
    if (settings.recycleWeek == "Odd Weeks" && !isEven) return true
    return false
}

// ------------------------------------------------------------------------------
// DATA COMPILATION LOGIC
// ------------------------------------------------------------------------------

int getFillPct() {
    int maxOpens = settings.estimatedMaxOpens ?: 8
    int currentOpens = state.lidOpens ?: 0
    if (maxOpens <= 0) maxOpens = 1
    
    float rawPct = (currentOpens.toFloat() / maxOpens.toFloat()) * 100
    return Math.min(Math.round(rawPct), 100)
}

def checkSensorHealth() {
    boolean isDead = false
    if (binMultiSensor) {
        def lastEvt = binMultiSensor.currentState("temperature")?.date
        if (lastEvt) {
            long hrsSince = (now() - lastEvt.time) / 3600000
            if (hrsSince > 48) isDead = true
        } else { isDead = true }
    }
    state.isSensorDead = isDead
}

def tempHandler(evt) {
    state.isSensorDead = false 
    def t = evt.numericValue
    if (t != null && t > (state.maxTempSinceWash ?: 0)) state.maxTempSinceWash = t
}

def updateHygiene() {
    long daysSince = state.lastWashed ? ((now() - state.lastWashed) / 86400000) as Integer : 0
    int maxT = state.maxTempSinceWash ?: 70
    String status = "Clean ✨"
    
    if (daysSince > 30 || (maxT > 90 && daysSince > 14)) status = "Bio-Hazard ☣️"
    else if (daysSince > 21 || (maxT > 85 && daysSince > 10)) status = "Gross 🤢"
    else if (daysSince > 14 || (maxT > 80 && daysSince > 7)) status = "Needs Washing 🧽"
    
    if (status != state.hygieneStatus && (status == "Bio-Hazard ☣️" || status == "Gross 🤢")) {
        sendAlert("🚨 BIN MAINTENANCE: Your exterior waste bin has reached a hygiene level of ${status.replaceAll(/[^a-zA-Z -]/, '')}. Please sanitize it soon.", notifyErrorDevices)
    }
    state.hygieneStatus = status
    pushChildUpdate()
}

def recordTelemetryTime(String stateKey) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    cal.setTime(new Date())
    int minutes = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
    def list = state[stateKey] ?: []
    list.add(0, minutes)
    if (list.size() > 10) list = list[0..9]
    state[stateKey] = list
}

String getEcoScoreBadge() {
    def hist = state.historyFullness ?: []
    if (hist.size() == 0) return "<span style='color:grey;'>Awaiting Processing Cycles</span>"
    int avgFill = Math.round(hist.sum() / hist.size()) as Integer
    if (avgFill <= 25) return "Rating A+ [Eco-Warrior]"
    if (avgFill <= 50) return "Rating A [Optimal]"
    if (avgFill <= 75) return "Rating B [Nominal]"
    if (avgFill <= 99) return "Rating C [Elevated]"
    return "Rating F [Saturation]"
}

// ------------------------------------------------------------------------------
// OPERATIONAL BALANCING & CRITICAL PHYSICS OVERRIDES
// ------------------------------------------------------------------------------

Map getAxisMap(def eventValue = null) {
    def val = eventValue ?: binMultiSensor?.currentValue("threeAxis")
    if (!val) return null
    try {
        if (val instanceof Map || val.respondsTo("getX")) {
            return [x: val.x as Integer, y: val.y as Integer, z: val.z as Integer]
        } else {
            def str = val.toString()
            def matcher = str =~ /(-?\d+)[^\d-]+(-?\d+)[^\d-]+(-?\d+)/
            if (matcher.find()) {
                return [x: matcher[0][1] as Integer, y: matcher[0][2] as Integer, z: matcher[0][3] as Integer]
            }
        }
    } catch (e) { log.error "Axis Parse Error: ${e}" }
    return null
}

boolean isWithinAllowedTransitWindow() {
    if (state.binStatus == "curb_missed") return true
    if (!state.nextPickupMs) return true 
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def nowCal = Calendar.getInstance(tz)
    nowCal.setTime(new Date())
    
    def pickupCal = Calendar.getInstance(tz)
    pickupCal.setTime(new Date(state.nextPickupMs as Long))
    
    nowCal.set(Calendar.HOUR_OF_DAY, 0); nowCal.set(Calendar.MINUTE, 0); nowCal.set(Calendar.SECOND, 0); nowCal.set(Calendar.MILLISECOND, 0)
    pickupCal.set(Calendar.HOUR_OF_DAY, 0); pickupCal.set(Calendar.MINUTE, 0); pickupCal.set(Calendar.SECOND, 0); pickupCal.set(Calendar.MILLISECOND, 0)
    
    long diffDays = (nowCal.getTimeInMillis() - pickupCal.getTimeInMillis()) / 86400000
    return (diffDays >= -1 && diffDays <= 1)
}

def calibrateSpatialBaseline() {
    if (!binMultiSensor) {
        logAction("ERROR: Cannot calibrate, no sensor selected.")
        return
    }
    
    def xyz = getAxisMap()
    if (xyz) {
        def dominantAxis = "x"
        def maxVal = -1
        def axes = ["x": xyz.x, "y": xyz.y, "z": xyz.z]
        
        axes.each { k, v ->
            int absV = Math.abs(v as Integer)
            if (absV > maxVal) {
                maxVal = absV
                dominantAxis = k
            }
        }
        
        state.activeAxis = dominantAxis
        state.baselineValue = axes[dominantAxis]
        state.baselineX = xyz.x
        state.baselineY = xyz.y
        state.baselineZ = xyz.z
        state.isSensorDead = false 
        
        logAction("SYSTEM: 3D Calibration complete. Locked to global [${dominantAxis.toUpperCase()}] axis. Value: ${state.baselineValue}")
        pushChildUpdate()
    } else {
        logAction("ERROR: 3D Calibration failed. Could not read coordinates from sensor.")
    }
}

def getSurfaceProfile() {
    def profile = [transitTime: 10, transitTilt: 150, lidTilt: 450]
    if (settings.drivewaySurface == "Smooth Pavement") {
        profile = [transitTime: 8, transitTilt: 120, lidTilt: 450]
    } else if (settings.drivewaySurface == "Mixed / Patchy") {
        profile = [transitTime: 12, transitTilt: 150, lidTilt: 450]
    } else if (settings.drivewaySurface == "Fine Gravel") {
        profile = [transitTime: 15, transitTilt: 180, lidTilt: 450]
    } else if (settings.drivewaySurface == "Chunky / Rough Gravel") {
        profile = [transitTime: 20, transitTilt: 220, lidTilt: 450]
    } else if (settings.drivewaySurface == "Custom (Learned / Manual)") {
        if (state.binStatus == "house") {
            profile.transitTime = settings.transitCurbTime ?: 10
            profile.transitTilt = settings.transitCurbTilt ?: 150
        } else {
            profile.transitTime = settings.transitHouseTime ?: 10
            profile.transitTilt = settings.transitHouseTilt ?: 150
        }
        profile.lidTilt = settings.lidOpenThreshold ?: 450
    }
    return profile
}

def binMoveActiveHandler(evt) {
    state.isSensorDead = false
    if (!state.motionStartTime) {
        state.motionStartTime = now()
        state.maxRelativeDevDuringMotion = 0
        state.wasFlippedDuringMotion = false
        state.hasTriggeredMechanicalDump = false
        
        // NEW FIX: Reset our manual tilt timers when motion starts
        state.firstTiltTime = null
        state.lastTiltTime = null
        
        if (state.lastKnownXYZ) {
            state.restX = state.lastKnownXYZ.x
            state.restY = state.lastKnownXYZ.y
            state.restZ = state.lastKnownXYZ.z
        }
    }
}

def axisSpatialHandler(evt) {
    def xyz = getAxisMap(evt.value) 
    if (!xyz) return
    
    if (!state.motionStartTime) {
        if (state.lastKnownXYZ) {
            int dx = Math.abs((xyz.x as Integer) - state.lastKnownXYZ.x)
            int dy = Math.abs((xyz.y as Integer) - state.lastKnownXYZ.y)
            int dz = Math.abs((xyz.z as Integer) - state.lastKnownXYZ.z)
            int jump = Math.max(dx, Math.max(dy, dz))
            
            if (jump > 150) {
                state.motionStartTime = now()
                state.maxRelativeDevDuringMotion = jump
                state.wasFlippedDuringMotion = false
                state.hasTriggeredMechanicalDump = false
                
                // NEW FIX: Reset our manual tilt timers when retroactive motion jump happens
                state.firstTiltTime = null
                state.lastTiltTime = null
                
                state.restX = state.lastKnownXYZ.x
                state.restY = state.lastKnownXYZ.y
                state.restZ = state.lastKnownXYZ.z
            } else {
                state.lastKnownXYZ = [x: xyz.x as Integer, y: xyz.y as Integer, z: xyz.z as Integer]
                return
            }
        } else {
            state.lastKnownXYZ = [x: xyz.x as Integer, y: xyz.y as Integer, z: xyz.z as Integer]
            return
        }
    }
    
    int curX = xyz.x as Integer
    int curY = xyz.y as Integer
    int curZ = xyz.z as Integer
    
    int rX = state.restX != null ? state.restX : (state.baselineX ?: 0)
    int rY = state.restY != null ? state.restY : (state.baselineY ?: 0)
    int rZ = state.restZ != null ? state.restZ : (state.baselineZ ?: 0)
    
    int devX = Math.abs(curX - rX)
    int devY = Math.abs(curY - rY)
    int devZ = Math.abs(curZ - rZ)
    int maxRelative = Math.max(devX, Math.max(devY, devZ))
    
    if (maxRelative > (state.maxRelativeDevDuringMotion ?: 0)) {
        state.maxRelativeDevDuringMotion = maxRelative
    }

    // NEW FIX: Record the exact timestamps of when the bin was physically tilted
    def profile = getSurfaceProfile()
    if (maxRelative >= profile.transitTilt) {
        if (!state.firstTiltTime) state.firstTiltTime = now()
        state.lastTiltTime = now()
    }

    if (state.learnMode && state.learnMode != "none") {
        state.learnMaxTilt = Math.max(state.learnMaxTilt ?: 0, maxRelative)
    }

    if ((state.binStatus == "curb_full" || state.binStatus == "curb_missed") && maxRelative >= 1800) {
        if (!state.isCurrentlyDumped && !state.hasTriggeredMechanicalDump) {
            state.hasTriggeredMechanicalDump = true
            state.isCurrentlyDumped = true
            processTruckDump()
        }
    }

    boolean isFlipped = false
    if (state.activeAxis && state.baselineValue != null) {
        int currentDom = xyz[state.activeAxis] as Integer
        int baselineDom = state.baselineValue as Integer
        isFlipped = (baselineDom > 0 && currentDom < -200) || (baselineDom < 0 && currentDom > 200)
        if (isFlipped) state.wasFlippedDuringMotion = true
    }

    if (isFlipped && (state.binStatus == "curb_full" || state.binStatus == "curb_missed")) {
        if (!state.isCurrentlyDumped && !state.hasTriggeredMechanicalDump) {
            state.isCurrentlyDumped = true
            processTruckDump()
        }
    } else if (!isFlipped && !state.hasTriggeredMechanicalDump) {
        state.isCurrentlyDumped = false
    }
}

def binMoveInactiveHandler(evt) {
    if (settings.enableFallenBin) runIn(300, "fallenBinCheck", [overwrite: true])
    if (!state.motionStartTime) return
    
    if (state.learnMode && state.learnMode != "none") {
        logAction("Learning Mode Active: Tracking profile data, ignoring standard physics processing.")
        state.motionStartTime = null
        return
    }

    long rawHardwareDurationSec = (now() - state.motionStartTime) / 1000
    
    // NEW FIX: Calculate how long it was physically tilted, ignoring the 15-second hardware timeout
    long sustainedTiltDuration = 0
    if (state.firstTiltTime && state.lastTiltTime) {
        sustainedTiltDuration = (state.lastTiltTime - state.firstTiltTime) / 1000
    }

    def profile = getSurfaceProfile()
    int maxTilt = state.maxRelativeDevDuringMotion ?: 0
    
    if (state.hasTriggeredMechanicalDump) {
        logAction("Motion Event Ended: Dump completed early via mechanical identification threshold.")
        state.motionStartTime = null
        state.hasTriggeredMechanicalDump = false
        return
    }
    
    boolean isCurrentlyFlipped = false
    def xyz = getAxisMap()
    
    if (state.activeAxis && state.baselineValue != null && xyz) {
        int baselineDom = state.baselineValue as Integer
        int currentDom = xyz[state.activeAxis] as Integer
        isCurrentlyFlipped = (baselineDom > 0 && currentDom < -200) || (baselineDom < 0 && currentDom > 200)
    }
    
    state.motionStartTime = null 
    logAction("Motion Event Ended: Raw Sensor Time: ${rawHardwareDurationSec}s | True Physical Tilt Time: ${sustainedTiltDuration}s | Max Tilt: ${maxTilt}")
    
    boolean isValidTransit = false
    
    // NEW FIX: Evaluates the "True Physical Tilt Time" instead of the flawed raw sensor duration
    if (sustainedTiltDuration >= profile.transitTime) {
        isValidTransit = true 
    }

    if (isValidTransit) {
        logAction("Action Processed: Sustained Transit Matrix Tracked.")
        processValidTransit()
        return
    }

    if (maxTilt >= profile.lidTilt) {
        long lastOpen = state.lastLidOpenTime ?: 0
        if (now() - lastOpen > 60000) { 
            state.lidOpens = (state.lidOpens ?: 0) + 1
            state.lastLidOpenTime = now()
            logAction("Action Processed: Trash Toss (Lid Vertical). Capacity: (${state.lidOpens}).")
            if (getFillPct() >= 100) sendAlert("📦 WARNING: The exterior trash bin is now at maximum capacity.", notifyFullDevices)
            pushChildUpdate()
        } else {
            logAction("Action Processed: Ignored duplicate lid open (cooldown active).")
        }
        return
    }
    
    logAction("Action Processed: Ignored bump/wind oscillation.")
}

def fallenBinCheck() {
    def xyz = getAxisMap()
    if (!xyz || !state.baselineValue || !state.activeAxis) return
    int curDom = xyz[state.activeAxis] as Integer
    
    boolean severeSwitch = (swTornado?.currentValue("switch") == "on" || swThunderstorm?.currentValue("switch") == "on")
    boolean wetSwitch = (swRain?.currentValue("switch") == "on" || swSprinkle?.currentValue("switch") == "on")
    
    if (severeSwitch) return

    if (Math.abs(curDom) < 400 && !state.isCurrentlyDumped) {
        if (state.binStatus == "curb_emptied") return
        if (wetSwitch) {
            sendAlert("🚨 SEVERE WEATHER ALERT: Your outdoor trash bin was knocked over by the weather.", notifyErrorDevices)
        } else {
            sendAlert("🚨 BIN ALERT: Your outdoor trash bin is sideways or open. The weather is calm, so check for animals or mishaps.", notifyErrorDevices)
        }
    }
}

def processTruckDump() {
    logAction("AUTOMATION: Kinetic Mechanical Displacement Inversion Confirmed. Matrix Cleared.")
    
    def histFull = state.historyFullness ?: []
    histFull.add(0, getFillPct())
    if (histFull.size() > 10) histFull = histFull[0..9]
    state.historyFullness = histFull
    
    recordTelemetryTime("historyEmptied")
    state.binStatus = "curb_emptied"
    state.lastDumpTime = now() 
    state.lidOpens = 0  
    
    if (switchEmptyTrigger) {
        try { switchEmptyTrigger.on() } catch (e) { log.error "Failed to turn ON Post-Empty Switch: ${e.message}" }
        runIn(600, "turnOffEmptySwitch")
    }

    updateSchedule()
    
    sendAlert("🚛 SUCCESS: The garbage truck has officially emptied your bin!", notifyEmptiedDevices)
    pushChildUpdate()
}

def turnOffEmptySwitch() {
    if (switchEmptyTrigger) {
        try { switchEmptyTrigger.off() } catch (e) { log.error "Failed to turn OFF Post-Empty Switch: ${e.message}" }
        logAction("AUTOMATION: 10-Minute Post-Empty switch sequence complete.")
        pushChildUpdate()
    }
}

def processValidTransit() {
    if (state.binStatus == "house") {
        if (!isWithinAllowedTransitWindow()) return
        logAction("AUTOMATION: Container moved to curb side perimeter.")
        recordTelemetryTime("historyPutOut")
        
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        
        state.binStatus = "curb_full" 
        pushChildUpdate()
    }
    else if (state.binStatus == "curb_emptied" || state.binStatus == "curb_missed") {
        if (state.binStatus == "curb_emptied" && state.lastDumpTime) {
            long msSinceDump = now() - state.lastDumpTime
            if (msSinceDump < 120000) return
        }
        
        logAction("AUTOMATION: Container safely docked at structural anchor station.")
        recordTelemetryTime("historyReturned")
        state.binStatus = "house" 
        state.lidOpens = 0 
        
        sendAlert("🏠 RETURNED: The trash bin has been returned to the house safely.", notifyReturnedDevices)
        pushChildUpdate()
    }
}

def autoReturnCheck() {
    if (!settings.enableAutoReturn) return
    
    if (state.binStatus == "curb_emptied") {
        if (settings.occupiedModes && settings.occupiedModes.contains(location.mode)) {
            logAction("AUTOMATION: 8:00 PM Failsafe triggered. Container assumed docked at house due to occupied mode.")
            state.binStatus = "house"
            state.lidOpens = 0
            recordTelemetryTime("historyReturned")
            pushChildUpdate()
        } else {
            logAction("AUTOMATION: 8:00 PM Auto-Return Skipped. Location mode (${location.mode}) is not marked as occupied.")
        }
    }
}

def ackHandler(evt) {
    if (state.isNagging) {
        state.isNagging = false
        state.binStatus = "curb_full"
        logAction("System Acknowledged: Task Monitor Switch turned OFF manually.")
        pushChildUpdate()
    }
}

String getHumanReadableStatus() {
    if (trashSwitch && trashSwitch.currentValue("switch") == "on" || state.isNagging) return "Pending"
    if (state.binStatus == "curb_full") return "At Curb"
    if (state.binStatus == "curb_emptied") return "Emptied"
    if (state.binStatus == "curb_missed") return "Missed"
    return "Secured Base"
}

// ------------------------------------------------------------------------------
// PREDICTIVE SCHEDULING ENGINE
// ------------------------------------------------------------------------------

boolean isHolidayShiftRequired(Calendar targetPickup) {
    if (!settings.autoHoliday || !settings.selectedHolidays) return false
    int year = targetPickup.get(Calendar.YEAR)
    def tz = location.timeZone ?: TimeZone.getDefault()
    def validHolidays = settings.selectedHolidays as List
    def holidayDates = []
    
    if (validHolidays.contains("New Year's Day")) {
        def ny = Calendar.getInstance(tz); ny.set(year, Calendar.JANUARY, 1, 0, 0, 0); ny.set(Calendar.MILLISECOND, 0); holidayDates << ny
    }
    if (validHolidays.contains("Memorial Day")) {
        def mem = Calendar.getInstance(tz); mem.set(year, Calendar.MAY, 31, 0, 0, 0); mem.set(Calendar.MILLISECOND, 0)
        while(mem.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { mem.add(Calendar.DAY_OF_MONTH, -1) }; holidayDates << mem
    }
    if (validHolidays.contains("Independence Day")) {
        def ind = Calendar.getInstance(tz); ind.set(year, Calendar.JULY, 4, 0, 0, 0); ind.set(Calendar.MILLISECOND, 0); holidayDates << ind
    }
    if (validHolidays.contains("Labor Day")) {
        def lab = Calendar.getInstance(tz); lab.set(year, Calendar.SEPTEMBER, 1, 0, 0, 0); lab.set(Calendar.MILLISECOND, 0)
        while(lab.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { lab.add(Calendar.DAY_OF_MONTH, 1) }; holidayDates << lab
    }
    if (validHolidays.contains("Thanksgiving")) {
        def tg = Calendar.getInstance(tz); tg.set(year, Calendar.NOVEMBER, 1, 0, 0, 0); tg.set(Calendar.MILLISECOND, 0)
        int thursdays = 0
        while(thursdays < 4) {
            if (tg.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY) thursdays++
            if (thursdays < 4) tg.add(Calendar.DAY_OF_MONTH, 1)
        }; holidayDates << tg
    }
    if (validHolidays.contains("Christmas")) {
        def xmas = Calendar.getInstance(tz); xmas.set(year, Calendar.DECEMBER, 25, 0, 0, 0); xmas.set(Calendar.MILLISECOND, 0); holidayDates << xmas
    }
    
    def weekStart = targetPickup.clone()
    while(weekStart.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { weekStart.add(Calendar.DAY_OF_MONTH, -1) }
    weekStart.set(Calendar.HOUR_OF_DAY, 0); weekStart.set(Calendar.MINUTE, 0); weekStart.set(Calendar.SECOND, 0)
    
    def targetEnd = targetPickup.clone()
    targetEnd.set(Calendar.HOUR_OF_DAY, 23); targetEnd.set(Calendar.MINUTE, 59)
    
    for (cal in holidayDates) {
        if (cal.getTimeInMillis() >= weekStart.getTimeInMillis() && cal.getTimeInMillis() <= targetEnd.getTimeInMillis()) return true
    }
    return false
}

def updateSchedule() {
    checkSensorHealth()
    if (!trashDays || !pickupTime || !reminderTime) return
    
    try {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def now = new Date()
        def cal = Calendar.getInstance(tz)
        cal.setTime(now)
        int currentDayNum = cal.get(Calendar.DAY_OF_WEEK) 
        
        def parsedTime = timeToday(pickupTime, tz)
        def pCal = Calendar.getInstance(tz)
        pCal.setTime(parsedTime)
        int pHour = pCal.get(Calendar.HOUR_OF_DAY)
        int pMin = pCal.get(Calendar.MINUTE)
        
        if (settings.usePredictiveTiming && state.historyEmptied && state.historyEmptied.size() >= 2 && !state.isSensorDead) {
            def avgMins = Math.round(state.historyEmptied.sum() / state.historyEmptied.size()) as Integer
            pHour = avgMins.intdiv(60)
            pMin = avgMins % 60
            state.predictiveActive = true
        } else { state.predictiveActive = false }
        
        def dayMap = ["Sunday":1, "Monday":2, "Tuesday":3, "Wednesday":4, "Thursday":5, "Friday":6, "Saturday":7]
        long nextPickupMs = Long.MAX_VALUE
        boolean shiftApplied = false
        
        trashDays.each { dayName ->
            int targetDay = dayMap[dayName]
            int daysToAdd = targetDay - currentDayNum
            if (daysToAdd < 0) daysToAdd += 7
            
            def testCal = Calendar.getInstance(tz)
            testCal.setTime(now)
            testCal.add(Calendar.DAY_OF_YEAR, daysToAdd)
            testCal.set(Calendar.HOUR_OF_DAY, pHour); testCal.set(Calendar.MINUTE, pMin); testCal.set(Calendar.SECOND, 0)
            
            if (daysToAdd == 0 && testCal.getTimeInMillis() <= now.time) testCal.add(Calendar.DAY_OF_YEAR, 7)
            
            if (settings.autoHoliday && isHolidayShiftRequired(testCal)) {
                testCal.add(Calendar.DAY_OF_YEAR, 1)
                shiftApplied = true
            } else if (state.holidayShift) { testCal.add(Calendar.DAY_OF_YEAR, 1) }
            if (testCal.getTimeInMillis() < nextPickupMs) nextPickupMs = testCal.getTimeInMillis()
        }
        
        state.autoHolidayTriggered = shiftApplied
        def pickupDate = new Date(nextPickupMs)
        
        def rTimeParsed = timeToday(reminderTime, tz)
        def rCal = Calendar.getInstance(tz)
        rCal.setTime(rTimeParsed)
        
        def reminderCal = Calendar.getInstance(tz)
        reminderCal.setTimeInMillis(nextPickupMs)
        reminderCal.add(Calendar.DAY_OF_YEAR, -1)
        reminderCal.set(Calendar.HOUR_OF_DAY, rCal.get(Calendar.HOUR_OF_DAY))
        reminderCal.set(Calendar.MINUTE, rCal.get(Calendar.MINUTE))
        reminderCal.set(Calendar.SECOND, 0)
        def reminderDate = reminderCal.getTime()
        
        state.nextPickupStr = pickupDate.format("EEEE, MMM d 'at' h:mm a", tz)
        state.nextReminderStr = reminderDate.format("EEEE, MMM d 'at' h:mm a", tz)
        state.nextPickupMs = nextPickupMs
        
        unschedule("triggerReminder")
        unschedule("autoResetHandler")
        
        if (reminderDate.time > now.time) runOnce(reminderDate, "triggerReminder", [overwrite: true])
        runOnce(new Date(nextPickupMs + 7200000), "autoResetHandler", [overwrite: true])
        pushChildUpdate()
    } catch (e) { log.error "Schedule Calculation Error: ${e}" }
}

def triggerReminder() {
    checkSensorHealth()
    if (state.binStatus == "curb_full" && !state.isSensorDead) {
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        pushChildUpdate()
        return
    }

    if (trashSwitch) trashSwitch.on()
    state.isNagging = true
    if (state.binStatus != "curb_missed") state.binStatus = "house" 
    
    String finalMsg = "🔔 REMINDER: It is time to take the trash out to the road!"
    if (checkRecyclingWeek()) finalMsg = "🔔 REMINDER: It is time to take the trash AND the recycling out to the road!"
    
    sendAlert(finalMsg, notifyReminderDevices)
    pushChildUpdate()
}

def autoResetHandler() {
    state.isNagging = false
    if (trashSwitch) trashSwitch.off()
    
    if (state.binStatus == "curb_full" && !state.isSensorDead) {
        state.binStatus = "curb_missed"
        state.missedTime = now()
        sendAlert("⚠️ ALERT: The garbage truck missed your scheduled pickup today!", notifyMissedDevices)
    } else {
        state.binStatus = "house" 
        state.lidOpens = 0
    }
    if (state.holidayShift) state.holidayShift = false
    updateSchedule()
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
