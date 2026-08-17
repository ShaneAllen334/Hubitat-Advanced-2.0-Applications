/**
 * Advanced Mode Manager 2.0
 */
definition(
    name: "Advanced Mode Manager 2.0",
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
        
        section("") {
            input "refreshBtn", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #8e44ad; margin-top:10px;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"

            input "sweepModeBtn", "button", title: "Sweep Mode (Force Immediate Enforcement)"

            def currentMode = location.mode ?: "Unknown"
       
            // Standard Pending Mode Transition
            def pendingActionStr = "<span style='color:gray;'>None (Stable)</span>"
            if (state.pendingTargetMode && state.pendingTargetTime) {
                def remainingMins = Math.max(0, Math.round((state.pendingTargetTime - now()) / 60000))
                pendingActionStr = "<span style='color:#e67e22;'><b>Shifting to '${state.pendingTargetMode}' in ${remainingMins} minutes</b></span>"
            } 
            
            // Virtual Switch Pending Transitions
            def vsPendingStr = ""
            for (int i = 1; i <= 5; i++) {
                if (state["vsPendingMode${i}"] && state["vsPendingTime${i}"]) {
                    def remain = Math.max(0, Math.round((state["vsPendingTime${i}"] - now()) / 60000))
                    def pMode = state["vsPendingMode${i}"]
                    vsPendingStr += "<div style='color:#1abc9c;'><b>Rule ${i}: Shifting to '${pMode}' in ${remain} mins</b></div>"
                } else if (state["vsPendingMode${i}"]) {
                    vsPendingStr += "<div style='color:#e67e22;'><b>Rule ${i}: Waiting for motion to stop...</b></div>"
                }
            }
            if (vsPendingStr == "") vsPendingStr = "<span style='color:gray;'>None</span>"
            
            // Auto Away Specific Status
            def autoAwayStr = "<span style='color:gray;'>Disabled</span>"
            if (settings.autoAwayEnable) {
                if (state.autoAwayPending) {
                    def aaRemaining = state.autoAwayTargetTime ? Math.max(0, Math.round((state.autoAwayTargetTime - now()) / 60000)) : "..."
                    def aaTarget = settings.aaTargetMode ?: "Away"
                    autoAwayStr = "<span style='color:#e74c3c;'><b>Countdown Active: Shifting to '${aaTarget}' in ${aaRemaining} minutes</b></span>"
                } else {
                    autoAwayStr = "<span style='color:#2ecc71;'><b>Monitoring Conditions...</b></span>"
                }
            }

            // Build dynamic rows pulling directly from the Auto Away Presence Sensors
            def presenceRows = ""
            if (settings.aaPresenceSensors) {
                settings.aaPresenceSensors.each { p ->
                    def pVal = p.currentValue("presence") ?: "unknown"
                    def pColor = pVal == "present" ? "#2ecc71" : "#e74c3c"
                    def pText = pVal == "present" ? "Present" : "Departed"
                    presenceRows += "<tr><td class='dash-hl'>Presence: ${p.displayName}</td><td class='dash-val' style='color:${pColor}; font-size: 14px;'>${pText}</td></tr>"
                }
            }

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-val { text-align: left !important; padding-left: 15px !important; font-weight:bold; }
            </style>
            
            <table class="dash-table">
                <thead><tr><th colspan="2">Real-Time Mode Metrics</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Current Hub Mode</td><td class="dash-val" style="color:#8e44ad; font-size: 16px;">${currentMode}</td></tr>
                    <tr><td class="dash-hl">Pending Mode Transition</td><td class="dash-val">${pendingActionStr}</td></tr>
                    <tr><td class="dash-hl">Pending Switch Transition</td><td class="dash-val">${vsPendingStr}</td></tr>
                </tbody>
            </table>
            
            <table class="dash-table" style="margin-top: 20px;">
                <thead><tr><th colspan="2">Auto Away Metrics</th></tr></thead>
                <tbody>
                    ${presenceRows}
                    <tr><td class="dash-hl">Auto Away Status</td><td class="dash-val">${autoAwayStr}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
            
            if (state.pendingTargetMode || state.autoAwayPending) input "abortTransition", "button", title: "Abort Pending Transition"
        }

        // ==============================================================================
        // RECENT ACTION HISTORY DROP MENU
        // ==============================================================================
        section("<b>Recent Action History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
     
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<div style='background-color:#f8f9fa; padding:10px; border:1px solid #ccc; font-size: 13px; font-family: monospace; max-height: 200px; overflow-y: auto;'>${historyStr}</div>"
            }
            input "clearHistory", "button", title: "Clear Action History"
        }

        // ==============================================================================
        // DEVICE CONTROL PER MODE DROP MENU
        // ==============================================================================
        section("<b>Device Control per Mode</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Instant Enforcement:</b> Define what devices should turn on, turn off, or lock the exact moment a specific mode becomes active.</div>"
            
            input "numDeviceRules", "enum", title: "How many Device Control Rules?", options: [1,2,3,4,5], defaultValue: 5, submitOnChange: true
            
            int devRules = (settings.numDeviceRules ?: 5).toInteger()
            for (int i = 1; i <= devRules; i++) {
                paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #2ecc71; margin-top:15px;'><b>Device Control Rule ${i}</b></div>"
                input "dcEnable${i}", "bool", title: "<b>Enable Device Control ${i}</b>", defaultValue: false
                input "dcMode${i}", "mode", title: "<b>[TRIGGER]</b> When mode becomes...", required: false
                input "dcSwitchesOff${i}", "capability.switch", title: "Turn OFF these switches", required: false, multiple: true
                input "dcSwitchesOn${i}", "capability.switch", title: "Turn ON these switches", required: false, multiple: true
                
                input "dcLocksLock${i}", "capability.lock", title: "Lock these doors", required: false, multiple: true
                input "dcLockDelayMins${i}", "number", title: "Delay time before locking doors (minutes)", required: false, defaultValue: 0
                
                input "dcGarageClose${i}", "capability.garageDoorControl", title: "Close these garages", required: false, multiple: true
                
                // --- DELAYED ACTIONS ---
                paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #34495e; margin-top:10px;'><b>Delayed Actions</b></div>"
                input "dcDelayedSwitchesOn${i}", "capability.switch", title: "Turn ON these switches after a delay", required: false, multiple: true
                input "dcDelayMins${i}", "number", title: "Delay time (minutes)", required: false, defaultValue: 5
            }
        }

        // ==============================================================================
        // MODE TO MODE TRANSITIONS DROP MENU
        // ==============================================================================
        section("<b>Mode to Mode Transitions</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Automated Timers:</b> Configure delayed transitions between modes. Example: When mode changes to 'Arrival', wait 15 minutes, then change to 'Home'. This timer will abort if the mode changes to anything else before the timer finishes.</div>"
            
            input "numTransRules", "enum", title: "How many Transition Rules?", options: [1,2,3,4,5], defaultValue: 5, submitOnChange: true
            
            int transRules = (settings.numTransRules ?: 5).toInteger()
            for (int i = 1; i <= transRules; i++) {
                paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #3498db; margin-top:15px;'><b>Transition Rule ${i}</b></div>"
                input "transEnable${i}", "bool", title: "<b>Enable Transition Rule ${i}</b>", defaultValue: false
                input "transTriggerMode${i}", "mode", title: "<b>[TRIGGER]</b> If mode becomes...", required: false
                input "transDelay${i}", "number", title: "<b>[TIMER]</b> Wait this many minutes", required: false, defaultValue: 15
                input "transTargetMode${i}", "mode", title: "<b>[TRANSITION]</b> Then change mode to...", required: false
            }
        }

        // ==============================================================================
        // VIRTUAL SWITCH TRANSITIONS DROP MENU
        // ==============================================================================
        section("<b>Virtual Switch Transitions</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Smart Switching:</b> Trigger transitions based on switch states. You can choose to bypass motion requirements for immediate debounced transitions.</div>"
            
            input "numVsRules", "enum", title: "How many Virtual Switch Rules?", options: [1,2,3,4,5], defaultValue: 5, submitOnChange: true
            
            int vsRules = (settings.numVsRules ?: 5).toInteger()
            for (int i = 1; i <= vsRules; i++) {
                paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #1abc9c; margin-top:15px;'><b>Virtual Switch Rule ${i}</b></div>"
                input "vsTransEnable${i}", "bool", title: "<b>Enable Virtual Switch Rule ${i}</b>", defaultValue: false
                
                input "vsCondMode${i}", "mode", title: "<b>[CONDITION]</b> ONLY execute if current mode is...", required: false, multiple: true, description: "Leave blank to allow any mode"
                
                input "vsUseTime${i}", "bool", title: "Restrict execution to specific times?", defaultValue: false, submitOnChange: true
                if (settings["vsUseTime${i}"]) {
                    input "vsTimeStart${i}", "time", title: "<b>[CONDITION]</b> ONLY execute between this time...", required: false
                    input "vsTimeEnd${i}", "time", title: "<b>[CONDITION]</b> ...and this time", required: false
                }
                
                input "vsTransSwitches${i}", "capability.switch", title: "<b>[TRIGGER]</b> Select Switches...", required: false, multiple: true
                input "vsTransTriggerType${i}", "enum", title: "When switches turn...", required: false, defaultValue: "All ON", options: ["All ON", "All OFF"]
                input "vsTransTargetMode${i}", "mode", title: "<b>[TRANSITION]</b> Change mode to...", required: false
                
                input "vsUseMotion${i}", "bool", title: "Use Motion Sensor Condition?", defaultValue: true, submitOnChange: true
                
                if (settings["vsUseMotion${i}"] != false) {
                    input "vsTransMotion${i}", "capability.motionSensor", title: "Wait for these motion sensors to stop", required: false, multiple: true
                }
                input "vsTransDebounce${i}", "number", title: "Debounce/Delay Timer (Minutes)", required: false, defaultValue: 5
            }
        }

        // ==============================================================================
        // DOOR LOCK TRANSITIONS DROP MENU
        // ==============================================================================
        section("<b>Door Lock Transitions</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Door Lock Transitions:</b> Instantly change modes when specific user codes unlock a door. Select multiple locks and codes for unified triggering.</div>"
            
            input "numLockRules", "enum", title: "How many Lock Rules?", options: [1,2,3,4,5], defaultValue: 5, submitOnChange: true
            
            int lockRules = (settings.numLockRules ?: 5).toInteger()
            for (int i = 1; i <= lockRules; i++) {
                paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #e67e22; margin-top:15px;'><b>Door Lock Transition Rule ${i}</b></div>"
                input "lockTransEnable${i}", "bool", title: "<b>Enable Lock Transition Rule ${i}</b>", defaultValue: false, submitOnChange: true
                input "lockTransDevice${i}", "capability.lock", title: "Select Door Lock(s)", required: false, multiple: true, submitOnChange: true
                
                def lockCodesOptions = [:]
                if (settings["lockTransDevice${i}"]) {
                    settings["lockTransDevice${i}"].each { lockDev ->
                        def lockCodesStr = lockDev.currentValue("lockCodes")
                        if (lockCodesStr) {
                            try {
                                def parsed = new groovy.json.JsonSlurper().parseText(lockCodesStr)
                                parsed.each { slot, data ->
                                    if (data.name) {
                                        lockCodesOptions[slot.toString()] = "${data.name} (Slot ${slot})"
                                    }
                                }
                            } catch (e) {
                                log.error "Error parsing lock codes for rule ${i}: ${e}"
                            }
                        }
                    }
                }
                
                input "lockTransCodeSlot${i}", "enum", title: "Select Lock Code(s) / User(s)", required: false, multiple: true, options: lockCodesOptions
                input "lockTransCondMode${i}", "mode", title: "<b>[CONDITION]</b> ONLY execute if current mode is...", required: false, multiple: true, description: "Leave blank to allow any mode"
                input "lockTransTargetMode${i}", "mode", title: "<b>[TRANSITION]</b> Then change mode to...", required: false
            }
        }

        // ==============================================================================
        // BUTTON CONTROLLER MODE MAPPING DROP MENU
        // ==============================================================================
        section("<b>Button Controller Mode Mapping</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Physical Override:</b> Change modes instantly using a physical button or remote. You can restrict these button commands to only work if the hub is currently in a specific mode.</div>"
            
            input "numBtnRules", "enum", title: "How many Button Rules?", options: [1,2,3,4,5], defaultValue: 5, submitOnChange: true
            
            int btnRules = (settings.numBtnRules ?: 5).toInteger()
            for (int i = 1; i <= btnRules; i++) {
                paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #9b59b6; margin-top:15px;'><b>Button Rule ${i}</b></div>"
                input "btnEnable${i}", "bool", title: "<b>Enable Button Rule ${i}</b>", defaultValue: false
                input "btnDevice${i}", "capability.pushableButton", title: "Button Device", required: false
                input "btnNum${i}", "number", title: "Button Number", required: false, defaultValue: 1
                input "btnAction${i}", "enum", title: "Button Action", required: false, defaultValue: "pushed", options: [
                    "pushed": "Pushed", 
                    "held": "Held", 
                    "doubleTapped": "Double Tapped", 
                    "released": "Released"
                ]
                input "btnCondMode${i}", "mode", title: "<b>[CONDITION]</b> ONLY execute if current mode is...", required: false, multiple: true, description: "Leave blank to allow any mode"
                input "btnTargetMode${i}", "mode", title: "<b>[TRANSITION]</b> Then change mode to...", required: false
            }
        }

        // ==============================================================================
        // AUTO AWAY
        // ==============================================================================
        section("<b>Auto Away</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Smart Departure:</b> Automatically transition to an Away mode when everyone leaves, optionally checked against motion, and Guest Mode is disabled.</div>"
            
            input "autoAwayEnable", "bool", title: "<b>Enable Auto Away Logic</b>", defaultValue: false, submitOnChange: true
            
            input "aaAllowedModes", "mode", title: "<b>[CONDITION]</b> Only execute if current mode is...", required: false, multiple: true
            input "aaTargetMode", "mode", title: "<b>[TRANSITION]</b> Change Mode To...", required: false, submitOnChange: true
            
            input "aaPresenceSensors", "capability.presenceSensor", title: "Arrival Sensors (All must be departed)", required: false, multiple: true, submitOnChange: true
            input "aaGuestSwitch", "capability.switch", title: "Guest Virtual Switch (Must be OFF)", required: false, multiple: false
            
            input "aaUseMotion", "bool", title: "Require motion to be inactive?", defaultValue: true, submitOnChange: true
            
            if (settings.aaUseMotion != false) {
                input "aaMotionSensors", "capability.motionSensor", title: "Motion Sensors to Monitor", required: false, multiple: true
            }
            
            input "aaDelayTime", "number", title: "Delay Timer (Minutes to wait after conditions are met before triggering)", required: false, defaultValue: 15
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    clearPendingTransition()
    state.autoAwayPending = false
    state.autoAwayTargetTime = null
    state.lastModeChangeTime = now()
    
    // Clear out any old virtual switch state caches
    for (int i = 1; i <= 5; i++) {
        state["vsPendingMode${i}"] = null
        state["vsPendingTime${i}"] = null
    }
    
    // Subscribe to Mode Changes
    subscribe(location, "mode", "modeChangeHandler")
    
    // Subscribe to Auto Away Sensors
    if (settings["autoAwayEnable"]) {
        subscribe(settings["aaPresenceSensors"], "presence", "autoAwayEvalHandler")
        if (settings["aaUseMotion"] != false) subscribe(settings["aaMotionSensors"], "motion", "autoAwayEvalHandler")
        if (settings["aaGuestSwitch"]) {
            subscribe(settings["aaGuestSwitch"], "switch", "autoAwayEvalHandler")
        }
    }
   
    for (int i = 1; i <= 5; i++) {
        // Subscribe to Button Controllers
        if (settings["btnEnable${i}"] && settings["btnDevice${i}"] && settings["btnAction${i}"]) {
            subscribe(settings["btnDevice${i}"], settings["btnAction${i}"], "buttonHandler")
        }

        // Subscribe to Door Lock Transitions
        if (settings["lockTransEnable${i}"] && settings["lockTransDevice${i}"]) {
            subscribe(settings["lockTransDevice${i}"], "lock", "lockTransitionHandler")
        }

        // Subscribe to Virtual Switch Transitions (ON & OFF)
        if (settings["vsTransEnable${i}"] && settings["vsTransSwitches${i}"]) {
            subscribe(settings["vsTransSwitches${i}"], "switch", "vsSwitchHandler")
        }
        if (settings["vsTransEnable${i}"] && settings["vsUseMotion${i}"] != false && settings["vsTransMotion${i}"]) {
            subscribe(settings["vsTransMotion${i}"], "motion", "vsMotionHandler")
        }
    }
    
    logAction("⚙️ Mode Manager Initialized.")
}

String getHumanReadableStatus() {
    if (state.autoAwayPending) {
        def aaRemaining = state.autoAwayTargetTime ? Math.max(0, Math.round((state.autoAwayTargetTime - now()) / 60000)) : "..."
        return "Auto Away conditions met. Countdown active (${aaRemaining} mins remaining)."
    }
    if (state.pendingTargetMode) return "Countdown Active. Monitoring timers and waiting to transition."
    return "Idle. Waiting for a trigger mode to activate."
}

def appButtonHandler(btn) {
    if (btn == "refreshBtn") {
        // The dynamic page automatically refreshes upon button click, pulling latest state.
    }
    else if (btn == "abortTransition") { 
        if (state.autoAwayPending) {
            logAction("🛑 User manually aborted Auto Away transition.")
            unschedule("executeAutoAway")
            state.autoAwayPending = false
            state.autoAwayTargetTime = null
        }
        if (state.pendingTargetMode) {
            logAction("🛑 User manually aborted timer transition to '${state.pendingTargetMode}'.")
            clearPendingTransition() 
        }
    }
    else if (btn == "clearHistory") { 
        state.actionHistory = []
        logAction("🗑️ Action History cleared by user.") 
    }
    else if (btn == "sweepModeBtn") { 
        logAction("⚡ Manual Sweep Triggered. Enforcing rules for current mode...")
        executeSweep() 
    }
}

// ------------------------------------------------------------------------------
// HANDLERS
// ------------------------------------------------------------------------------

def buttonHandler(evt) {
    def devId = evt.device.id
    def action = evt.name
    def btnNum = evt.value.toString()
    def currentMode = location.mode

    for (int i = 1; i <= 5; i++) {
        if (settings["btnEnable${i}"] && settings["btnDevice${i}"]?.id == devId) {
            if (settings["btnAction${i}"] == action && settings["btnNum${i}"].toString() == btnNum) {
                def conditionModes = settings["btnCondMode${i}"]
                def targetMode = settings["btnTargetMode${i}"]
                
                if (!conditionModes || conditionModes.contains(currentMode)) {
                    logAction("🔘 Button Rule ${i} Triggered: '${evt.device.displayName}' (Button ${btnNum} ${action}). Transitioning mode to '${targetMode}'.")
                    if (state.pendingTargetMode != null) clearPendingTransition()
                    location.setMode(targetMode)
                } else {
                    logAction("🚫 Button Rule ${i} Skipped: '${evt.device.displayName}' pressed, but current mode '${currentMode}' is not an allowed condition.")
                }
            }
        }
    }
}

def lockTransitionHandler(evt) {
    if (evt.value != "unlocked") return
    
    def currentMode = location.mode
    
    // --- 10 MINUTE FLIP-FLOP PREVENTION ---
    if (currentMode?.toLowerCase()?.contains("away") && state.lastModeChangeTime) {
        def elapsed = now() - state.lastModeChangeTime
        if (elapsed < 600000) { // 600,000 ms = 10 minutes
            def remainingMins = Math.max(1, Math.round((600000 - elapsed) / 60000))
            logAction("🚫 Lock rule skipped: Mode recently changed to '${currentMode}'. Preventing flip-flop for another ${remainingMins} minute(s).")
            return
        }
    }
    
    def data = evt.data
    def codeId = null
    
    if (data) {
        if (data instanceof Map) {
            codeId = data.codeId?.toString()
        } else if (data instanceof String || data instanceof GString) {
            try {
                def parsed = new groovy.json.JsonSlurper().parseText(data)
                codeId = parsed.codeId?.toString()
            } catch (e) {
                log.error "Failed to parse lock event data: ${e}"
            }
        }
    }
    
    if (!codeId) {
        def lastName = evt.device.currentValue("lastCodeName")
        if (lastName) {
            def lockCodesStr = evt.device.currentValue("lockCodes")
            if (lockCodesStr) {
                try {
                    def parsed = new groovy.json.JsonSlurper().parseText(lockCodesStr)
                    parsed.each { slot, lockData ->
                        if (lockData.name == lastName) {
                            codeId = slot.toString()
                        }
                    }
                } catch (e) {
                    log.error "Error parsing lock codes during fallback: ${e}"
                }
            }
        }
    }
    
    if (!codeId) {
        logAction("⚠️ Silent Failure: Lock unlocked, but no codeId found in event or fallback logic. Payload was: ${data}")
        return
    }
    
    def devId = evt.device.id
    
    for (int i = 1; i <= 5; i++) {
        if (settings["lockTransEnable${i}"] && settings["lockTransDevice${i}"]?.find { it.id == devId }) {
            def selectedSlots = settings["lockTransCodeSlot${i}"]
            def slotList = selectedSlots instanceof List ? selectedSlots : [selectedSlots]
            
            if (slotList.contains(codeId)) {
                def condModes = settings["lockTransCondMode${i}"]
                def targetMode = settings["lockTransTargetMode${i}"]
                
                if (!condModes || condModes.contains(currentMode)) {
                    logAction("🔐 Lock Rule ${i} Triggered: '${evt.device.displayName}' unlocked via code slot ${codeId}. Transitioning mode to '${targetMode}'.")
                    if (state.pendingTargetMode != null) clearPendingTransition()
                    location.setMode(targetMode)
                    break
                } else {
                    logAction("🚫 Lock Rule ${i} Skipped: Unlocked via slot ${codeId}, but current mode '${currentMode}' is not an allowed condition.")
                }
            }
        }
    }
}

def vsSwitchHandler(evt) {
    def currentMode = location.mode
    
    for (int i = 1; i <= 5; i++) {
        if (settings["vsTransEnable${i}"] && settings["vsTransSwitches${i}"]?.find { it.id == evt.device.id }) {
            
            // Check Mode Condition
            def condModes = settings["vsCondMode${i}"]
            if (condModes && !condModes.contains(currentMode)) {
                logAction("🚫 Virtual Switch Rule ${i} Skipped: Current mode '${currentMode}' is not allowed.")
                continue 
            }
            
            // Check Time Window Condition
            if (settings["vsUseTime${i}"]) {
                def startTime = settings["vsTimeStart${i}"]
                def endTime = settings["vsTimeEnd${i}"]
                
                if (startTime && endTime) {
                    def startObj = timeToday(startTime, location.timeZone)
                    def endObj = timeToday(endTime, location.timeZone)
                    def nowObj = new Date()
                    
                    def isTimeValid = false
                    
                    if (startObj > endObj) {
                        // Time spans across midnight (e.g., 7:00 PM to 5:00 AM)
                        isTimeValid = (nowObj >= startObj || nowObj <= endObj)
                    } else {
                        // Standard same-day window (e.g., 8:00 AM to 5:00 PM)
                        isTimeValid = (nowObj >= startObj && nowObj <= endObj)
                    }
                    
                    if (!isTimeValid) {
                        logAction("🚫 Virtual Switch Rule ${i} Skipped: Current time is outside the allowed window.")
                        continue 
                    }
                }
            }
            
            def triggerType = settings["vsTransTriggerType${i}"] ?: "All ON"
            def conditionMet = false
            
            if (triggerType == "All ON") {
                conditionMet = settings["vsTransSwitches${i}"].every { it.currentValue("switch") == "on" }
            } else if (triggerType == "All OFF") {
                conditionMet = settings["vsTransSwitches${i}"].every { it.currentValue("switch") == "off" }
            }
            
            if (conditionMet) {
                def target = settings["vsTransTargetMode${i}"]
                if (!target) continue
                
                def useMotion = settings["vsUseMotion${i}"] != false
                def motionSensors = settings["vsTransMotion${i}"]
                
                if (useMotion && motionSensors && motionSensors.any { it.currentValue("motion") == "active" }) {
                    logAction("💡 Virtual Switch Rule ${i} Pending: State condition met, but motion is active. Waiting for clear to transition to '${target}'.")
                    state["vsPendingMode${i}"] = target
                    state["vsPendingTime${i}"] = null 
                } else {
                    def debounce = settings["vsTransDebounce${i}"] ?: 5
                    logAction("💡 Virtual Switch Rule ${i} Triggered: Switching to '${target}' in ${debounce} minutes.")
                    state["vsPendingMode${i}"] = target
                    state["vsPendingTime${i}"] = now() + (debounce * 60000)
                    runIn(debounce * 60, "executeVsTransition${i}", [overwrite: true])
                }
            }
        }
    }
}

def vsMotionHandler(evt) {
    for (int i = 1; i <= 5; i++) {
        if (state["vsPendingMode${i}"] != null && settings["vsTransMotion${i}"]?.find { it.id == evt.device.id }) {
            def allQuiet = settings["vsTransMotion${i}"].every { it.currentValue("motion") != "active" }
            if (allQuiet) {
                def debounce = settings["vsTransDebounce${i}"] ?: 5
                def pendingMode = state["vsPendingMode${i}"]
                logAction("⏲️ Virtual Switch Rule ${i} Debounce: Motion cleared. Transitioning to '${pendingMode}' in ${debounce}m.")
                state["vsPendingTime${i}"] = now() + (debounce * 60000)
                runIn(debounce * 60, "executeVsTransition${i}", [overwrite: true])
            } else {
                logAction("🛑 Virtual Switch Rule ${i} Debounce Canceled: Motion detected again in zone.")
                unschedule("executeVsTransition${i}")
                state["vsPendingTime${i}"] = null
            }
        }
    }
}

// Wrapper execution functions to isolate timers for each of the 5 rules
def executeVsTransition1() { finalizeVsTransition(1) }
def executeVsTransition2() { finalizeVsTransition(2) }
def executeVsTransition3() { finalizeVsTransition(3) }
def executeVsTransition4() { finalizeVsTransition(4) }
def executeVsTransition5() { finalizeVsTransition(5) }

def finalizeVsTransition(ruleIdx) {
    def target = state["vsPendingMode${ruleIdx}"]
    if (target) {
        def useMotion = settings["vsUseMotion${ruleIdx}"] != false
        def allQuiet = settings["vsTransMotion${ruleIdx}"]?.every { it.currentValue("motion") != "active" }
        
        // Final sanity check before transition
        if (!useMotion || settings["vsTransMotion${ruleIdx}"] == null || allQuiet) {
            logAction("💡 Virtual Switch Rule ${ruleIdx} Complete: Transitioning mode to '${target}'.")
            state["vsPendingMode${ruleIdx}"] = null
            state["vsPendingTime${ruleIdx}"] = null
            location.setMode(target)
        } else {
            logAction("🛑 Virtual Switch Rule ${ruleIdx} Aborted: Timer finished, but motion became active again at the last second.")
            state["vsPendingTime${ruleIdx}"] = null
        }
    }
}

def modeChangeHandler(evt) {
    def newMode = evt.value
    state.lastModeChangeTime = now()
    
    if (state.pendingTargetMode != null) {
        logAction("⚠️ Failsafe Intervention: Mode was externally changed to '${newMode}'. Aborting pending timer for '${state.pendingTargetMode}'.")
        clearPendingTransition()
    }
    
    // Evaluate Auto Away on Mode Change
    if (settings["autoAwayEnable"]) checkAutoAwayConditions()
    
    // 1. Process Instant Device Controls
    for (int i = 1; i <= 5; i++) {
        if (settings["dcEnable${i}"] && settings["dcMode${i}"] == newMode) {
            logAction("⚙️ Checking Device Control Rule ${i} for mode '${newMode}'...")
            enforceDevices(i)
        }
    }

    // 2. Process Transition Timers
    for (int i = 1; i <= 5; i++) {
        if (settings["transEnable${i}"] && settings["transTriggerMode${i}"] == newMode) {
            def delayMins = settings["transDelay${i}"] ?: 0
            
            if (delayMins > 0) {
                def target = settings["transTargetMode${i}"]
                state.pendingTriggerMode = newMode
                state.pendingTargetMode = target
                state.pendingTargetTime = now() + (delayMins * 60000)
                state.pendingRuleNumber = i
                logAction("⏳ Transition Rule ${i} Initiated: Mode is '${newMode}'. Starting ${delayMins}-minute timer to transition to '${target}'.")
                runIn((delayMins * 60).toInteger(), "executeTransition")
                break 
            }
        }
    }
}

// ------------------------------------------------------------------------------
// AUTO AWAY ENGINE
// ------------------------------------------------------------------------------

def autoAwayEvalHandler(evt) {
    checkAutoAwayConditions()
}

def checkAutoAwayConditions() {
    if (!settings["autoAwayEnable"]) return

    def currentMode = location.mode
    def allowed = settings["aaAllowedModes"]?.contains(currentMode)
    
    def guestOff = !settings["aaGuestSwitch"] || settings["aaGuestSwitch"].currentValue("switch") == "off"
    
    def allGone = settings["aaPresenceSensors"] ? settings["aaPresenceSensors"].every { it.currentValue("presence") != "present" } : true
    
    def allQuiet = true
    if (settings["aaUseMotion"] != false && settings["aaMotionSensors"]) {
        allQuiet = settings["aaMotionSensors"].every { it.currentValue("motion") != "active" }
    }

    if (allowed && guestOff && allGone && allQuiet) {
        if (!state.autoAwayPending) {
            def delayMins = settings["aaDelayTime"] ?: 15
            logAction("🚗 Auto Away Initialized: Arrival/Motion conditions met. Starting ${delayMins}-minute countdown to '${settings.aaTargetMode}'.")
            state.autoAwayPending = true
            state.autoAwayTargetTime = now() + (delayMins * 60000)
            runIn(delayMins * 60, "executeAutoAway", [overwrite: true])
        }
    } else {
        if (state.autoAwayPending) {
            logAction("🛑 Auto Away Interrupted: Conditions no longer met (Presence/Motion detected). Countdown aborted.")
            unschedule("executeAutoAway")
            state.autoAwayPending = false
            state.autoAwayTargetTime = null
        }
    }
}

def executeAutoAway() {
    def allowed = settings["aaAllowedModes"]?.contains(location.mode)
    def guestOff = !settings["aaGuestSwitch"] || settings["aaGuestSwitch"].currentValue("switch") == "off"
    def allGone = settings["aaPresenceSensors"] ? settings["aaPresenceSensors"].every { it.currentValue("presence") != "present" } : true
    
    def allQuiet = true
    if (settings["aaUseMotion"] != false && settings["aaMotionSensors"]) {
        allQuiet = settings["aaMotionSensors"].every { it.currentValue("motion") != "active" }
    }

    if (allowed && guestOff && allGone && allQuiet) {
        def target = settings["aaTargetMode"]
        logAction("🚗 Auto Away Complete: Timer finished successfully. Transitioning mode to '${target}'.")
        state.autoAwayPending = false
        state.autoAwayTargetTime = null
        location.setMode(target)
    } else {
        logAction("⚠️ Auto Away Aborted: Timer finished, but conditions (Presence/Motion) changed at the exact last second.")
        state.autoAwayPending = false
        state.autoAwayTargetTime = null
    }
}

// ------------------------------------------------------------------------------
// EXECUTION LOGIC
// ------------------------------------------------------------------------------

def executeSweep() {
    def currentMode = location.mode
    def matchFound = false
    
    for (int i = 1; i <= 5; i++) {
        if (settings["dcEnable${i}"] && settings["dcMode${i}"] == currentMode) {
            logAction("⚡ Manual Sweep -> Evaluating Device Control Rule ${i} for '${currentMode}'...")
            enforceDevices(i)
            matchFound = true
        }
    }
    if (!matchFound) logAction("🤷 Manual Sweep -> No Device Control rules found to execute for mode '${currentMode}'.")
}

def executeTransition() {
    def ruleIdx = state.pendingRuleNumber
    if (location.mode == state.pendingTriggerMode && ruleIdx != null) {
        def target = state.pendingTargetMode
        logAction("⏳ Transition Rule ${ruleIdx} Complete: Timer finished. Transitioning mode from '${location.mode}' to '${target}'.")
        clearPendingTransition()
        location.setMode(target) 
    } else {
        logAction("⚠️ Transition Rule ${ruleIdx} Failsafe: Mode changed during the countdown. Aborting transition to '${state.pendingTargetMode}'.")
        clearPendingTransition()
    }
}

def enforceDevices(ruleIdx) {
    def logMsg = "⚡ Device Control Rule ${ruleIdx} Executed -> "
    def actionsTaken = false
    
    if (settings["dcSwitchesOff${ruleIdx}"]) { settings["dcSwitchesOff${ruleIdx}"].off(); logMsg += "[Switches OFF] "; actionsTaken = true }
    if (settings["dcSwitchesOn${ruleIdx}"]) { settings["dcSwitchesOn${ruleIdx}"].on(); logMsg += "[Switches ON] "; actionsTaken = true }
    if (settings["dcGarageClose${ruleIdx}"]) { settings["dcGarageClose${ruleIdx}"].close(); logMsg += "[Garage Closed] "; actionsTaken = true }
    
    if (settings["dcLocksLock${ruleIdx}"]) {
        def lockDelay = settings["dcLockDelayMins${ruleIdx}"] ?: 0
        if (lockDelay > 0) {
            def delaySecs = (lockDelay * 60).toInteger()
            runIn(delaySecs, "lockDelayedDoors", [data: [ruleIdx: ruleIdx], overwrite: false])
            logMsg += "[Locks: ${lockDelay}m Delay Started] "
            actionsTaken = true
        } else {
            settings["dcLocksLock${ruleIdx}"].lock()
            logMsg += "[Doors Locked] "
            actionsTaken = true
        }
    }
    
    if (settings["dcDelayedSwitchesOn${ruleIdx}"] && settings["dcDelayMins${ruleIdx}"] != null) {
        def delayMins = settings["dcDelayMins${ruleIdx}"]
        def delaySecs = (delayMins * 60).toInteger()
        runIn(delaySecs, "turnOnDelayedSwitches", [data: [ruleIdx: ruleIdx], overwrite: false])
        logMsg += "[Delayed Switches: ${delayMins}m Timer Started] "
        actionsTaken = true
    }
    
    if (actionsTaken) {
        logAction(logMsg)
    } else {
        logAction("⚡ Device Control Rule ${ruleIdx} Executed -> No devices configured for this rule.")
    }
}

// ------------------------------------------------------------------------------
// UTILITY FUNCTIONS
// ------------------------------------------------------------------------------

def lockDelayedDoors(data) {
    def ruleIdx = data?.ruleIdx
    if (ruleIdx && settings["dcLocksLock${ruleIdx}"]) {
        settings["dcLocksLock${ruleIdx}"].lock()
        logAction("🔐 Delayed Lock Action Triggered: Doors locked for Device Control Rule ${ruleIdx} after scheduled delay.")
    }
}

def turnOnDelayedSwitches(data) {
    def ruleIdx = data?.ruleIdx
    if (ruleIdx && settings["dcDelayedSwitchesOn${ruleIdx}"]) {
        settings["dcDelayedSwitchesOn${ruleIdx}"].on()
        logAction("⚡ Delayed Switch Action Triggered: Switches turned ON for Device Control Rule ${ruleIdx} after scheduled delay.")
    }
}

def clearPendingTransition() {
    unschedule("executeTransition")
    state.pendingTargetMode = null
    state.pendingTriggerMode = null
    state.pendingTargetTime = null
    state.pendingRuleNumber = null
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if (h.size() > 30) h = h[0..29]
    state.actionHistory = h
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
