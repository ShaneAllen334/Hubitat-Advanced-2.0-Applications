/**
 * Advanced Kid Presence Sensor 2.0
 */ 

definition(
    name: "Advanced Kid Presence Sensor 2.0",
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

String getHumanReadableStatus() {
    if (!targetPresence) {
        return "<span style='color:red; font-size:14px;'><b>⚠️ SYSTEM INACTIVE: Target virtual presence sensor not assigned.</b></span>"
    }

    def currentPres = targetPresence.currentValue("presence")
    def isAwayMode = (settings.awayModes && settings.awayModes.contains(location.mode))
    def isSchool = (schoolSwitch && schoolSwitch.currentValue("switch") == "on")
    def isSick = (sickSwitch && sickSwitch.currentValue("switch") == "on")

    if (currentPres == "present") {
        if (state.isFallbackPending) {
            return "<span style='color:#fd7e14; font-size:14px;'><b>⏳ PRESENT (COUNTDOWN ACTIVE): Kid is marked home, but parents are away and house is still. Pending automatic departure.</b></span>"
        }
        if (isSick && isSchool) {
            return "<span style='color:#fd7e14; font-size:14px;'><b>🤒 PRESENT (SICK MODE): Kid is home. School schedule is actively bypassed due to sickness.</b></span>"
        }
        if (isAwayMode) {
            return "<span style='color:#dc3545; font-size:14px;'><b>⚠️ CONFLICT: Kid is marked Present, but Hub is in Away Mode. Mode restrictions active.</b></span>"
        }
        return "<span style='color:#28a745; font-size:14px;'><b>🏠 PRESENT: Kid is home. System is monitoring for departure triggers.</b></span>"
    } else {
        if (isAwayMode) {
            return "<span style='color:#6c757d; font-size:14px;'><b>🚗 AWAY: Kid is departed. Hub is in an Away mode, which blocks standard switch arrivals.</b></span>"
        }
        if (isSchool && !isSick) {
            return "<span style='color:#007bff; font-size:14px;'><b>🏫 AWAY (SCHOOL): Kid is marked departed. School schedule is active.</b></span>"
        }
        return "<span style='color:#6c757d; font-size:14px;'><b>🚗 AWAY: Kid is departed. System is monitoring for arrival triggers.</b></span>"
    }
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
            
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            if (targetPresence) {
                def currentPres = targetPresence.currentValue("presence")
                def presColor = currentPres == "present" ? "#28a745" : "#6c757d"
                def presText = currentPres == "present" ? "Arrived (Present)" : "Away (Not Present)"
                
                def lastMsgTime = state.recentTriggerTime ? new Date(state.recentTriggerTime as Long).format("MM/dd hh:mm a", location.timeZone) : "N/A"
                def lastTrigger = state.recentTriggerSource ?: "N/A"
                
                def isSchool = schoolSwitch ? schoolSwitch.currentValue("switch").toUpperCase() : "UNCONFIGURED"
                def isSick = sickSwitch ? sickSwitch.currentValue("switch").toUpperCase() : "UNCONFIGURED"
                def schTime = schoolTime ? schoolTime : "08:00"
                def afterSchTime = afterSchoolTime ? afterSchoolTime : "15:00"
                
                def fallbackStatus = state.isFallbackPending ? "<span style='color:#fd7e14; font-weight:bold;'>COUNTDOWN ACTIVE</span>" : "Standby (Motion or Parents Present)"
                
                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                    .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                    .dash-val { text-align: left !important; padding-left: 15px !important; }
                </style>
                <table class="dash-table">
                    <thead><tr><th>Metric</th><th>Current Value</th></tr></thead>
                    <tbody>
                        <tr><td colspan="2" class="dash-subhead">Core Presence Status</td></tr>
                        <tr><td class="dash-hl">Target Sensor</td><td class="dash-val"><b>${targetPresence.displayName}</b></td></tr>
                        <tr><td class="dash-hl">Current State</td><td class="dash-val"><span style='color:${presColor}; font-weight:bold;'>${presText}</span></td></tr>
                        <tr><td class="dash-hl">Current Hub Mode</td><td class="dash-val"><span style='color:#004085; font-weight:bold;'>${location.mode}</span></td></tr>
                        
                        <tr><td colspan="2" class="dash-subhead">Smart Fallback Status</td></tr>
                        <tr><td class="dash-hl">Fallback Engine</td><td class="dash-val">${fallbackStatus}</td></tr>
                        
                        <tr><td colspan="2" class="dash-subhead">Schedule Variables</td></tr>
                        <tr><td class="dash-hl">School Mode Switch</td><td class="dash-val">${isSchool}</td></tr>
                        <tr><td class="dash-hl">Sick Mode Switch</td><td class="dash-val">${isSick}</td></tr>
                        <tr><td class="dash-hl">Target Departure Time</td><td class="dash-val">${schTime}</td></tr>
                        <tr><td class="dash-hl">After-School Arrival Time</td><td class="dash-val">${afterSchTime}</td></tr>
                        
                        <tr><td colspan="2" class="dash-subhead">Action History</td></tr>
                        <tr><td class="dash-hl">Last Trigger Source</td><td class="dash-val">${lastTrigger}</td></tr>
                        <tr><td class="dash-hl">Last Trigger Time</td><td class="dash-val">${lastMsgTime}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
                
                def logicPanel = "<div style='margin-top: 20px; padding: 15px; background: #e6f2ff; border-left: 5px solid #007bff; font-size: 13px; color: #004085;'>"
                logicPanel += "<h4 style='margin-top:0; border-bottom:1px solid #b8daff; padding-bottom:5px;'>Engine Diagnostics: Logic Matrix</h4>"
                logicPanel += "<div style='max-height: 400px; overflow-y: auto; border: 1px solid #b8daff;'><table class='dash-table' style='margin-top:0; background: white; color: #333;'><thead style='position: sticky; top: 0; box-shadow: 0 1px 2px rgba(0,0,0,0.1);'><tr><th>Filter / Module</th><th>Status</th><th>Effect</th><th>Diagnostic Output</th></tr></thead><tbody>"
                
                try {
                    if (state.sysDiagnostics && state.sysDiagnostics.size() > 0) {
                        state.sysDiagnostics.each { diag ->
                            def eff = diag.effect ?: "0"
                            def effColor = (eff.contains("BLOCK") || eff.contains("AWAY") || eff.contains("DEPART")) ? "red" : (eff.contains("ALLOW") || eff.contains("HOME") || eff.contains("MONITORING") ? "green" : "black")
                            if (diag.status == "PENDING") effColor = "orange"
                            def statusColor = (diag.status == "ACTIVE" || diag.status == "ON") ? "blue" : "gray"
                            logicPanel += "<tr><td style='font-weight:bold;'>${diag.name}</td><td style='color:${statusColor};'>${diag.status}</td><td style='color:${effColor}; font-weight:bold;'>${eff}</td><td>${diag.desc}</td></tr>"
                        }
                    } else {
                        logicPanel += "<tr><td colspan='4'>Waiting for initial routing evaluation...</td></tr>"
                    }
                } catch (e) {
                     logicPanel += "<tr><td colspan='4'><i>Failed to load matrix. Hit 'Reset Internal State Diagnostics'.</i></td></tr>"
                }
                
                logicPanel += "</tbody></table></div>"
                logicPanel += "<div style='margin-top:10px;'><b>Consensus & Confidence:</b> " + (state.matrixReasoning ?: "Waiting for consensus...") + "</div>"
                logicPanel += "</div>"

                paragraph logicPanel
                
            } else {
                paragraph "<i>Configure the target presence sensor below to activate dashboard.</i>"
            }
        }
        
        section("<b>1. System Target</b>", hideable: true, hidden: true) {
            input "targetPresence", "capability.presenceSensor", title: "Select Virtual Presence Sensor to Control", required: true, submitOnChange: true
        }

        section("<b>2. Home Triggers (Arrival)</b>", hideable: true, hidden: true) {
            paragraph "<i>These triggers will mark the sensor as 'Arrived'. By default, additional switches only work if the Hub is <b>not</b> in an Away mode. Lock codes and the Kid's Occupancy switch bypass mode restrictions.</i>"
            
            input "kidRoomSwitches", "capability.switch", title: "Kid's Virtual Room Occupancy Switch(es) (Turns ON = Arrived)", multiple: true, required: false
            input "occupancySwitches", "capability.switch", title: "Additional Arrival Switches (Good Night, etc.)", multiple: true, required: false
            
            paragraph "<hr>"
            input "arrivalLocks", "capability.lock", title: "Smart Locks (For Lock Code Arrival)", multiple: true, submitOnChange: true
            
            if (arrivalLocks) {
                def codeOptions = [:]
                arrivalLocks.each { lck ->
                    def codesStr = lck.currentValue("lockCodes")
                    if (codesStr) {
                        try {
                            def parsed = new groovy.json.JsonSlurper().parseText(codesStr)
                            parsed.each { slot, info ->
                                if (info.name) {
                                    def keyStr = info.name as String
                                    def valStr = "${lck.displayName}: ${info.name}" as String
                                    codeOptions[keyStr] = valStr
                                }
                            }
                        } catch (e) {
                            log.error "Failed to parse lock codes: ${e}"
                        }
                    }
                }
                
                if (codeOptions) {
                    input "kidLockCode", "enum", title: "Kid's Lock Code Name", options: codeOptions, required: false
                } else {
                    paragraph "<span style='color:red;'><i>No named lock codes found on the selected lock(s).</i></span>"
                }
            }
        }
        
        section("<b>3. Away Triggers (Departure)</b>", hideable: true, hidden: true) {
            paragraph "<i>If the Hub transitions to any of these modes, the sensor will automatically be marked as 'Away'.</i>"
            input "awayModes", "mode", title: "Global Away Modes", multiple: true, required: false
        }
        
        section("<b>4. School & Sick Mode Logic</b>", hideable: true, hidden: true) {
            paragraph "<i>At the specified time, if School Mode is ON and Sick Mode is OFF, the sensor will be automatically marked as 'Away'.</i>"
            
            input "schoolTime", "time", title: "School Departure Time", defaultValue: "08:00", required: true
            input "schoolSwitch", "capability.switch", title: "School Mode Virtual Switch", required: true
            input "sickSwitch", "capability.switch", title: "Sick Mode Virtual Switch", required: false
            
            paragraph "<hr>"
            paragraph "<i><b>After-School Auto-Arrival:</b> Listen for multiple motion events within a 10-minute window to automatically confirm arrival after a specific time (only when School mode is ON).</i>"
            
            input "afterSchoolMotion", "capability.motionSensor", title: "After-School Arrival Motion Sensors", multiple: true, required: false
            input "afterSchoolTime", "time", title: "After-School Window Start Time (e.g. 15:00/3:00PM)", defaultValue: "15:00", required: false
            input "afterSchoolHits", "number", title: "Required Motion Events (in 10 mins)", defaultValue: 3, required: false
        }
        
        section("<b>5. Smart Fallback Departure (Parents & Motion)</b>", hideable: true, hidden: true) {
            paragraph "<i>If all selected parents are away and no motion is detected for the specified time, the kid will automatically be marked as 'Away'.</i>"
            
            input "parentPresence", "capability.presenceSensor", title: "Select Parent Presence Sensors (e.g., Shane, Christy)", multiple: true, required: false
            input "motionSensors", "capability.motionSensor", title: "Select Activity Motion Sensors", multiple: true, required: false
            input "fallbackDelay", "number", title: "Inactivity Delay (Minutes)", defaultValue: 10, required: true
        }
        
        section("<b>Action History & Debugging</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true
            
            try {
                if (state.logHistory) {
                    def historyStr = state.logHistory.join("<br>")
                    paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
                } else {
                    paragraph "<i>No history logged yet.</i>"
                }
            } catch (e) {
                 paragraph "<i>History missing or resetting.</i>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }
        
        if (app.id) {
            section("<b>Global Actions & Overrides</b>", hideable: true, hidden: true) {
                input "testArriveBtn", "button", title: "👋 Force Arrival (Present)"
                input "testDepartBtn", "button", title: "🚗 Force Departure (Away)"
                input "testSchoolBtn", "button", title: "🏫 Test School Check Routine"
                input "clearStateBtn", "button", title: "⚠ Reset Internal State Diagnostics"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() {
    logInfo("Installed")
    state.logHistory = [] 
    state.sysDiagnostics = [] 
    state.motionEventsQueue = []
    initialize()
}

def updated() {
    logInfo("Updated")
    initialize()
}

def initialize() {
    if (!state.logHistory) state.logHistory = []
    if (!state.sysDiagnostics) state.sysDiagnostics = []
    if (!state.motionEventsQueue) state.motionEventsQueue = []
    state.isFallbackPending = false
    
    unsubscribe()
    unschedule()
    
    if (occupancySwitches) subscribe(occupancySwitches, "switch.on", "arrivalSwitchHandler")
    if (kidRoomSwitches) subscribe(kidRoomSwitches, "switch.on", "arrivalSwitchHandler")
    
    if (arrivalLocks) subscribe(arrivalLocks, "lock.unlocked", "lockArrivalHandler")
    if (awayModes) subscribe(location, "mode", "modeChangeHandler")
    
    if (schoolSwitch) subscribe(schoolSwitch, "switch", "logicStateChangeHandler")
    if (sickSwitch) subscribe(sickSwitch, "switch", "sickSwitchHandler") 
    
    if (parentPresence) subscribe(parentPresence, "presence", "fallbackTriggerHandler")
    if (motionSensors) subscribe(motionSensors, "motion", "fallbackTriggerHandler")
    
    if (afterSchoolMotion) subscribe(afterSchoolMotion, "motion.active", "afterSchoolMotionHandler")
    
    if (schoolTime && schoolSwitch) {
        schedule(schoolTime, "schoolDepartureCheck")
    }
    
    logAction("Advanced Kid Presence Sensor 2.0 Initialized.")
    evaluateFallbackCondition(null) 
    evaluateLogicMatrix()
}

void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard data manually refreshed.")
        evaluateLogicMatrix()
    }
    else if (btn == "testArriveBtn") {
        logAction("MANUAL OVERRIDE: Forcing Arrival.")
        setPresenceState("arrived", "Manual Button Override")
    } 
    else if (btn == "testDepartBtn") {
        logAction("MANUAL OVERRIDE: Forcing Departure.")
        setPresenceState("departed", "Manual Button Override")
    }
    else if (btn == "testSchoolBtn") {
        logAction("MANUAL OVERRIDE: Firing School Check Routine.")
        schoolDepartureCheck()
    }
    else if (btn == "resetActionHistory") {
        state.logHistory = []
        logInfo("Action History Cleared.")
    }
    else if (btn == "clearStateBtn") {
        logAction("EMERGENCY RESET: Purging logic state history.")
        state.sysDiagnostics = []
        state.motionEventsQueue = []
        state.recentTriggerSource = null
        state.isFallbackPending = false
        unschedule("executeFallbackDeparture")
        evaluateLogicMatrix()
    }
}

// --- Trigger Handlers ---

def logicStateChangeHandler(evt) {
    evaluateLogicMatrix()
}

def sickSwitchHandler(evt) {
    if (evt.value == "on") {
        if (targetPresence && targetPresence.currentValue("presence") != "present") {
            logAction("🤒 Sick switch turned ON while kid was marked Away. Assuming kid is home sick.")
            setPresenceState("arrived", "Sick Mode Turned ON")
        }
    }
    evaluateLogicMatrix()
}

def arrivalSwitchHandler(evt) {
    def devName = evt.device.displayName
    def isKidSwitch = kidRoomSwitches?.find { it.id == evt.device.id }
    
    logDebug("Arrival switch (${devName}) turned ON.")
    
    if (isHubInAwayMode()) {
        if (isKidSwitch) {
            logAction("🏠 Kid's Occupancy Switch (${devName}) turned ON while Hub in Away Mode. Bypassing mode restriction.")
        } else {
            logAction("🔕 Switch ${devName} turned ON, but Hub is in an Away mode. Arrival trigger blocked.")
            return
        }
    }
    
    setPresenceState("arrived", "Room Switch (${devName})")
}

def lockArrivalHandler(evt) {
    def codeName = ""
    
    if (evt.data) {
        try {
            def dataMap = new groovy.json.JsonSlurper().parseText(evt.data)
            if (dataMap?.codeName) {
                codeName = dataMap.codeName
            } else {
                def firstKey = dataMap.keySet().iterator().next()
                if (dataMap[firstKey]?.name) codeName = dataMap[firstKey].name
            }
        } catch (e) {
            logDebug("Could not parse lock event data JSON: ${e}")
        }
    }
    
    if (!codeName && evt.descriptionText && kidLockCode) {
        if (evt.descriptionText.contains(kidLockCode)) {
            codeName = kidLockCode
        }
    }
    
    if (codeName && kidLockCode && codeName == kidLockCode) {
        if (isHubInAwayMode()) {
            logAction("🏠 Lock code (${codeName}) used while Hub in Away Mode. Bypassing mode restriction for physical unlock.")
        }
        setPresenceState("arrived", "Lock Code (${codeName})")
    }
}

def modeChangeHandler(evt) {
    evaluateLogicMatrix()
    if (awayModes && awayModes.contains(evt.value)) {
        logAction("Global Hub Mode changed to Away (${evt.value}). Executing Departure.")
        setPresenceState("departed", "Hub Mode: ${evt.value}")
    }
}

def schoolDepartureCheck() {
    def isSchoolModeOn = (schoolSwitch && schoolSwitch.currentValue("switch") == "on")
    def isSickModeOn = (sickSwitch && sickSwitch.currentValue("switch") == "on")
    
    if (isSchoolModeOn) {
        if (isSickModeOn) {
            logAction("🤒 School check time reached. School Mode is ON, but Sick Mode is ON. Kid assumed home.")
        } else {
            logAction("🏫 School check time reached. School Mode is ON. Executing Departure.")
            setPresenceState("departed", "School Schedule")
        }
    } else {
        logAction("School check time reached, but School Mode is OFF. Departure bypassed.")
    }
    
    evaluateLogicMatrix()
}

// --- After-School Motion Logic ---

def afterSchoolMotionHandler(evt) {
    if (targetPresence?.currentValue("presence") == "present") return
    
    def isSchoolModeOn = (schoolSwitch && schoolSwitch.currentValue("switch") == "on")
    if (!isSchoolModeOn) return 
    if (!isAfterSchoolTime()) return 
    
    def nowTs = now()
    def tenMinsAgo = nowTs - (10 * 60 * 1000)
    
    def events = state.motionEventsQueue ?: []
    events << nowTs
    events = events.findAll { it > tenMinsAgo }
    state.motionEventsQueue = events
    
    def requiredHits = settings.afterSchoolHits != null ? settings.afterSchoolHits.toInteger() : 3
    
    logDebug("After-school motion detected (${evt.device.displayName}). Hit ${events.size()}/${requiredHits} in last 10m.")
    
    if (events.size() >= requiredHits) {
        logAction("🎒 Multiple after-school motion events detected (${requiredHits} within 10m). Executing Arrival.")
        setPresenceState("arrived", "After-School Motion Arrival")
        state.motionEventsQueue = [] 
    }
    
    evaluateLogicMatrix() 
}

boolean isAfterSchoolTime() {
    if (!afterSchoolTime) return false
    def targetTime = timeToday(afterSchoolTime, location.timeZone)
    def rightNow = new Date()
    return rightNow.after(targetTime)
}

// --- Fallback Departure Logic ---

def fallbackTriggerHandler(evt) {
    evaluateFallbackCondition(evt)
    evaluateLogicMatrix()
}

def evaluateFallbackCondition(evt) {
    if (!parentPresence || !motionSensors) return
    
    def anyParentHome = parentPresence.find { it.currentValue("presence") == "present" }
    def anyMotionActive = motionSensors.find { it.currentValue("motion") == "active" }
    
    if (!anyParentHome && !anyMotionActive) {
        def mins = settings.fallbackDelay != null ? settings.fallbackDelay.toInteger() : 10
        def delay = mins * 60
        if (!state.isFallbackPending) {
            def logMsg = "⏳ All parents departed and house is perfectly still. Starting ${mins}-minute fallback departure timer." as String
            
            if (evt?.name == "motion" && evt?.value == "inactive") {
                logMsg = "⏳ Motion cleared (all sensors inactive) while parents are still away. Restarting ${mins}-minute fallback timer." as String
            }
            
            logAction(logMsg)
            state.isFallbackPending = true
            runIn(delay, "executeFallbackDeparture")
        }
    } else {
        if (state.isFallbackPending) {
            def reason = anyParentHome ? "Parent arrived" : "Motion detected (e.g., dogs active)"
            logAction("🛑 ${reason}. Canceling fallback departure timer." as String)
            state.isFallbackPending = false
            unschedule("executeFallbackDeparture")
        }
    }
}

def executeFallbackDeparture() {
    state.isFallbackPending = false
    logAction("🚗 Fallback timer expired (Parents Away + No Motion). Executing Departure.")
    setPresenceState("departed", "Smart Fallback (Parents Away & No Motion)")
}

// --- Utility Functions & Matrix ---

boolean isHubInAwayMode() {
    if (!awayModes) return false
    return awayModes.contains(location.mode)
}

def evaluateLogicMatrix() {
    def diagList = []
    
    def curModeStr = location.mode?.toString() ?: "Unknown"
    
    if (isHubInAwayMode()) {
        diagList << [name: "Hub Mode Filter", status: "AWAY", effect: "BLOCK ARRIVALS", desc: "Current mode (${curModeStr}) is an Away mode. Standard switch arrivals are suppressed." as String]
    } else {
        diagList << [name: "Hub Mode Filter", status: "HOME", effect: "ALLOW ARRIVALS", desc: "Current mode (${curModeStr}) permits standard room switch arrivals." as String]
    }
    
    def isSchool = schoolSwitch?.currentValue("switch") == "on"
    def isSick = sickSwitch?.currentValue("switch") == "on"
    
    if (isSchool) {
        if (isSick) {
            diagList << [name: "School/Sick Routine", status: "SICK", effect: "STAY HOME", desc: "School Mode is ON, but Sick Mode overrides. Scheduled departures suspended." as String]
        } else {
            def t = schoolTime ? schoolTime.toString() : "08:00"
            diagList << [name: "School/Sick Routine", status: "SCHOOL", effect: ("DEPART AT " + t) as String, desc: "School Mode is ON and active. System will execute departure at target time." as String]
        }
    } else {
        diagList << [name: "School/Sick Routine", status: "OFF", effect: "NONE", desc: "School Mode is OFF. Educational schedules bypassed." as String]
    }
    
    if (afterSchoolMotion && afterSchoolTime) {
        def isAfter = isAfterSchoolTime()
        def hits = state.motionEventsQueue?.size() ?: 0
        def req = settings.afterSchoolHits ?: 3
        
        if (isSchool && isAfter && targetPresence?.currentValue("presence") != "present") {
            diagList << [name: "After-School Motion", status: "ACTIVE", effect: ("MONITORING (" + hits + "/" + req + ")") as String, desc: "Window active. Listening for multiple motion events to mark arrival." as String]
        } else {
            diagList << [name: "After-School Motion", status: "STANDBY", effect: "NONE", desc: "Outside time window, school mode off, or kid already marked present." as String]
        }
    } else {
        diagList << [name: "After-School Motion", status: "UNCONFIGURED", effect: "NONE", desc: "Missing motion sensors or time settings." as String]
    }

    if (parentPresence && motionSensors) {
        if (state.isFallbackPending) {
            diagList << [name: "Smart Fallback Routine", status: "PENDING", effect: "TIMER ACTIVE", desc: "Parents away and no motion. Departure timer running." as String]
        } else {
            diagList << [name: "Smart Fallback Routine", status: "STANDBY", effect: "NONE", desc: "Parents are home or motion is active. Timer paused." as String]
        }
    } else {
        diagList << [name: "Smart Fallback Routine", status: "UNCONFIGURED", effect: "NONE", desc: "Missing parent presence or motion sensors." as String]
    }

    state.sysDiagnostics = diagList
    
    if (targetPresence) {
        def trkStr = targetPresence.currentValue('presence')?.toString() ?: "Unknown"
        state.matrixReasoning = "Logic engine operational. Tracking target device as ${trkStr}." as String
    } else {
        state.matrixReasoning = "System awaiting target configuration." as String
    }
}

def setPresenceState(stateStr, reason) {
    if (!targetPresence) {
        log.warn "No target virtual presence sensor configured."
        return
    }
    
    def safeReason = reason?.toString() ?: "Unknown"
    def currentPresence = targetPresence.currentValue("presence")
    
    if (stateStr == "arrived") {
        if (currentPresence != "present") {
            logAction("📍 Updating Presence to: ARRIVED [Source: ${safeReason}]" as String)
            state.recentTriggerSource = safeReason
            state.recentTriggerTime = now()
            
            if (targetPresence.hasCommand("arrived")) targetPresence.arrived()
            else if (targetPresence.hasCommand("present")) targetPresence.present()
        } else {
            logDebug("Presence is already set to 'present'. Skipping update. [Source: ${safeReason}]" as String)
        }
    } else if (stateStr == "departed") {
        if (currentPresence != "not present") {
            logAction("🚗 Updating Presence to: DEPARTED [Source: ${safeReason}]" as String)
            state.recentTriggerSource = safeReason
            state.recentTriggerTime = now()
            
            if (targetPresence.hasCommand("departed")) targetPresence.departed()
            else if (targetPresence.hasCommand("away")) targetPresence.away()
        } else {
            logDebug("Presence is already set to 'not present'. Skipping update. [Source: ${safeReason}]" as String)
        }
    }
    
    evaluateLogicMatrix()
}

def logAction(msg) { 
    def safeMsg = msg?.toString() ?: "Unknown Action"
    if(txtEnable) log.info "${app.label}: ${safeMsg}"
    def h = state.logHistory ?: []
    def timeStr = new Date().format("MM/dd hh:mm a", location.timeZone)
    h.add(0, "[${timeStr}] ${safeMsg}" as String)
    if(h.size() > 30) h = h[0..29]
    state.logHistory = h 
}

def logInfo(msg) { 
    def safeMsg = msg?.toString() ?: ""
    if(txtEnable) log.info "${app.label}: ${safeMsg}" 
}

def logDebug(msg) {
    def safeMsg = msg?.toString() ?: ""
    if (debugEnable) log.debug "${app.label}: ${safeMsg}"
}
