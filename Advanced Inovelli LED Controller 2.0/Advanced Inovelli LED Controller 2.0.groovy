/**
 * Advanced Inovelli LED Controller 2.0
 */ 

definition(
    name: "Advanced Inovelli LED Controller 2.0",
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
    def tableHTML = """
    <h4 style="margin:0 0 10px 0; border-bottom:1px solid #ccc; padding-bottom:5px; color:#333; margin-top:20px;">Rule Priority Stack Matrix</h4>
    <div style="max-height: 350px; overflow-y: auto; border: 1px solid #eee; margin-bottom: 20px;">
        <table class="dash-table" style="margin-top:0;">
            <thead style="position: sticky; top: 0; box-shadow: 0 1px 2px rgba(0,0,0,0.1);">
                <tr>
                    <th>Priority</th>
                    <th>Rule Name</th>
                    <th>Type</th>
                    <th>Trigger State</th>
                    <th>Output (Color, Level, Effect)</th>
                </tr>
            </thead>
            <tbody>
    """
    
    def ruleMatrix = getActiveRuleMatrix()
    
    if (ruleMatrix.size() == 0) {
        tableHTML += "<tr><td colspan='5' style='color:#777; font-style:italic;'>No rules configured yet.</td></tr>"
    } else {
        ruleMatrix.each { r ->
            def statusColor = r.isActive ? "green" : "gray"
            def statusText = r.isActive ? "<b>ACTIVE</b>" : "Inactive"
            def isCurrent = r.isHighestActive ? "<span style='color:blue; font-weight:bold;'> [EXECUTING]</span>" : ""
            def rowBg = r.isHighestActive ? "background-color: #e6f2ff;" : ""
            
            tableHTML += """
                <tr style="${rowBg}">
                    <td style="font-weight: bold;">${r.priority}</td>
                    <td>${r.name}${isCurrent}</td>
                    <td>${r.type}</td>
                    <td style="color:${statusColor};">${statusText}</td>
                    <td><span style="font-weight:bold; color:${getHexColor(r.color)}; text-shadow: 1px 1px 1px #ccc;">${r.color}</span> @ ${r.level}% <br><span style="font-size:11px; color:#555;">[${r.effect}]</span></td>
                </tr>
            """
        }
    }
    
    tableHTML += """
            </tbody>
        </table>
    </div>
    """
    return tableHTML
}

String getHumanReadableStatus() {
    def baseText = ""
    if (!state.currentRuleName) {
        if (state.currentLevel == 0) {
            baseText = "<span style='color:gray;'><b>IDLE/SLEEP STANDBY.</b></span> No active rules."
        } else {
            baseText = "<span style='color:gray;'><b>STANDING BY.</b></span> No active rules. Default state processing."
        }
    } else {
        def cColor = state.currentColor ?: "Unknown"
        def cLevel = state.currentLevel ?: 0
        def cEffect = state.currentEffect ?: "Solid"
        def cRule = state.currentRuleName ?: "None"
        baseText = "<span style='color:blue;'><b>ACTIVE: ${cRule}.</b></span> Engine firing <span style='font-weight:bold; color:${getHexColor(cColor)};'>${cColor}</span> at ${cLevel}% with <b>${cEffect}</b> effect."
    }
    
    return baseText + "<br><span style='font-size: 12px; color: #555;'><i>(Each LED Location is independently evaluating its local motion vacancy to apply this logic.)</i></span>"
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff; margin-top: 10px;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
                      
            input "executeNowBtn", "button", title: "▶️ Force Execute LED Rules"
            
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 28%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
            </style>
            """
            paragraph dashHTML
            paragraph renderTableHTML()
        }
        
        section("<b>Action History & Debugging</b>", hideable: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true
            
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            } else {
                paragraph "<i>No history logged yet.</i>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>1. LED Locations & Proximity Wake-Up</b>", hideable: true, hidden: false) {
            paragraph "<i>Define your LED locations. Tie a specific motion sensor to an LED so it only wakes up when someone is actually in that specific room.</i>"
            input "numNodes", "number", title: "Number of LED Locations (1-5)", required: true, defaultValue: 1, range: "1..5", submitOnChange: true
            
            for (int i = 1; i <= (settings.numNodes ?: 1); i++) {
                paragraph "<div style='border-top: 1px solid #ccc; margin-top: 10px; padding-top: 10px;'><b>Location ${i}</b></div>"
                input "leds_${i}", "capability.switch", title: "Target LED Bars / Switches", multiple: true, required: false
                input "motion_${i}", "capability.motionSensor", title: "Local Proximity Wake-Up Sensor(s)", multiple: true, required: false
            }
            
            paragraph "<div style='margin-top: 10px;'><b>Proximity Settings</b></div>"
            input "motionSleepDelay", "number", title: "Vacant Sleep Delay (Minutes)", defaultValue: 5, description: "Wait this long after local motion stops before blacking out the LED at that location."
        }
        
        section("<b>2. Default / Base State</b>", hideable: true, hidden: true) {
            paragraph "<i>This state will automatically apply when no priority rules are actively triggered (and the room is occupied).</i>"
            input "defaultColor", "enum", title: "Base LED Color", options: getAvailableColors(), required: true, defaultValue: "Blue"
            input "defaultLevel", "number", title: "Base Brightness Level (0-100)", required: true, defaultValue: 30
            input "defaultEffect", "enum", title: "Base Effect", options: getAvailableEffects(), required: true, defaultValue: "Solid"
        }

        section("<b>3. Energy Efficiency & Sleep Rules</b>", hideable: true, hidden: true) {
            paragraph "<i>Maximize energy savings by shaping how the LEDs behave when the house is asleep or idle.</i>"
            
            paragraph "<b>House Mode Restrictions</b>"
            input "sleepModes", "mode", title: "Sleep/Away Modes (Engage Base Dimming & Alert Caps)", multiple: true, required: false, submitOnChange: true
            if (sleepModes) {
                input "sleepBaseLevel", "number", title: "Base Brightness during Sleep Modes (0-100)", defaultValue: 0, required: true, description: "Set to 0 to turn base LEDs completely off while sleeping."
                input "nightMaxLevel", "number", title: "Maximum Brightness for Active Alerts during Sleep (1-100)", defaultValue: 20, required: true, description: "Prevents blinding 100% alerts at 3 AM."
            }
            
            paragraph "<hr><b>Idle Standby</b>"
            input "idleTimeout", "number", title: "Idle Standby Timeout (Minutes)", defaultValue: 0, description: "0 = Disabled. If enabled, the base LED turns OFF if a location sits fully idle with no priority alerts for X minutes (even if occupied)."
        }

        for (int i = 1; i <= 10; i++) {
            def ruleTitle = settings["ruleName_${i}"] ?: "Priority Rule ${i}"
            def sectionHeader = settings["ruleEnable_${i}"] ? "<b>[${i}] ${ruleTitle}</b>" : "<b>Rule Set ${i} (Disabled)</b>"
            
            section(sectionHeader, hideable: true, hidden: true) {
                input "ruleEnable_${i}", "bool", title: "Enable this Rule", defaultValue: false, submitOnChange: true
                if (settings["ruleEnable_${i}"]) {
                    input "ruleName_${i}", "text", title: "Rule Dashboard Name", required: true, defaultValue: "Priority Rule ${i}", submitOnChange: true
                    input "rulePriority_${i}", "number", title: "Stack Priority (1 is highest, 10 is lowest)", required: true, defaultValue: i
                    
                    input "ruleType_${i}", "enum", title: "Trigger Type", options: ["Switch", "Mode"], submitOnChange: true, required: true
                    
                    if (settings["ruleType_${i}"] == "Switch") {
                        input "ruleSwitch_${i}", "capability.switch", title: "Target Virtual or Physical Switch", required: true
                        input "ruleSwitchState_${i}", "enum", title: "Active When Switch Is:", options: ["on", "off"], required: true, defaultValue: "on"
                    } else if (settings["ruleType_${i}"] == "Mode") {
                        input "ruleMode_${i}", "mode", title: "Active In These Modes", multiple: true, required: true
                    }
                    
                    input "ruleColor_${i}", "enum", title: "Output LED Color", options: getAvailableColors(), required: true, defaultValue: "White"
                    input "ruleLevel_${i}", "number", title: "Output Brightness Level (1-100)", required: true, defaultValue: 100
                    input "ruleEffect_${i}", "enum", title: "Output LED Effect", options: getAvailableEffects(), required: true, defaultValue: "Solid"
                }
            }
        }

        if (app.id) {
            section("<b>Global Actions & Overrides</b>", hideable: true, hidden: true) {
                input "clearStateBtn", "button", title: "⚠ Reset Internal State"
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
    
    // Global Rule State
    state.currentPriority = 999
    state.currentColor = null
    state.currentLevel = null
    state.currentEffect = null
    state.currentRuleName = null
    
    // Per-Device State Trackers
    if (!state.devColor) state.devColor = [:]
    if (!state.devLevel) state.devLevel = [:]
    if (!state.devEffect) state.devEffect = [:]
    
    subscribe(location, "mode", "evalHandler")
    
    def numNodes = settings.numNodes ?: 1
    for (int i = 1; i <= numNodes; i++) {
        if (settings["leds_${i}"]) subscribe(settings["leds_${i}"], "switch", "physicalInteractionHandler")
        if (settings["motion_${i}"]) subscribe(settings["motion_${i}"], "motion", "motionWakeHandler")
    }
    
    for (int i = 1; i <= 10; i++) {
        if (settings["ruleEnable_${i}"] && settings["ruleType_${i}"] == "Switch" && settings["ruleSwitch_${i}"]) {
            subscribe(settings["ruleSwitch_${i}"], "switch", "evalHandler")
        }
    }
    
    logAction("Advanced Inovelli LED Controller Initialized.")
    runIn(2, "evalWrapper")
}

def motionWakeHandler(evt) {
    def devId = evt.device.id.toString()
    def numNodes = settings.numNodes ?: 1
    def woken = false
    
    for (int i = 1; i <= numNodes; i++) {
        def mSensors = settings["motion_${i}"]
        if (mSensors?.any { it.id.toString() == devId }) {
            state."nodeLastMotion_${i}" = now()
            
            if (evt.value == "active" && state."nodeVacant_${i}") {
                state."nodeVacant_${i}" = false
                woken = true
                logAction("Motion detected at Location ${i}. Waking LED from Vacant Blackout.")
            }
        }
    }
    
    if (woken) {
        runIn(1, "evalWrapper")
    } else if (evt.value == "inactive") {
        def delaySec = (motionSleepDelay != null ? motionSleepDelay : 5) * 60
        runIn(delaySec + 2, "evalWrapper")
    }
}

def physicalInteractionHandler(evt) {
    def devId = evt.device.id.toString()
    def numNodes = settings.numNodes ?: 1
    def woken = false
    
    for (int i = 1; i <= numNodes; i++) {
        def leds = settings["leds_${i}"]
        if (leds?.any { it.id.toString() == devId }) {
            state."nodeLastActive_${i}" = now()
            state."nodeLastMotion_${i}" = now() // Pretend motion happened so it doesn't instantly sleep
            
            if (state."nodeVacant_${i}") {
                state."nodeVacant_${i}" = false
                woken = true
            }
        }
    }
    
    if (woken || !state.currentRuleName) {
        logDebug("Physical interaction detected. Waking Location from Standby/Sleep.")
        runIn(1, "evalWrapper")
    }
}

def evalWrapper() {
    evaluateLEDs(false)
}

void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard data manually refreshed.")
    }
    else if (btn == "executeNowBtn") { 
        logAction("MANUAL OVERRIDE: Forcing physical execution of current logic stack.") 
        evaluateLEDs(true) 
    }
    else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    }
    else if (btn == "clearStateBtn") {
        state.currentPriority = 999
        state.currentColor = null
        state.currentLevel = null
        state.currentEffect = null
        state.currentRuleName = null
        
        state.devColor = [:]
        state.devLevel = [:]
        state.devEffect = [:]
        
        def numNodes = settings.numNodes ?: 1
        for (int i = 1; i <= numNodes; i++) {
            state."nodeVacant_${i}" = false
            state."nodeLastMotion_${i}" = now()
            state."nodeLastActive_${i}" = now()
        }
        
        logAction("Internal state trackers forcefully reset.")
        evaluateLEDs(true)
    }
}

def evalHandler(evt) {
    logDebug("Trigger event received: ${evt.name} = ${evt.value}. Queuing evaluation.")
    runIn(1, "evalWrapper") 
}

def getAvailableColors() {
    return ["Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "White", "Off"]
}

def getAvailableEffects() {
    return ["Solid", "Fast Blink", "Slow Blink", "Pulse", "Chase", "Aurora", "Falling", "Rising", "Siren"]
}

def getHexColor(colorStr) {
    switch (colorStr) {
        case "Red": return "#ff0000"
        case "Orange": return "#ffa500"
        case "Yellow": return "#ffff00"
        case "Green": return "#008000"
        case "Cyan": return "#00ffff"
        case "Blue": return "#0000ff"
        case "Purple": return "#800080"
        case "Pink": return "#ffc0cb"
        case "White": return "#aaaaaa"
        case "Off": return "#000000"
        default: return "#0000ff"
    }
}

def getHubitatColorMap(colorStr, level) {
    def h = 0
    def s = 100
    switch (colorStr) {
        case "Red": h = 0; break
        case "Orange": h = 10; break
        case "Yellow": h = 16; break
        case "Green": h = 33; break
        case "Cyan": h = 50; break
        case "Blue": h = 66; break
        case "Purple": h = 75; break
        case "Pink": h = 83; break
        case "White": h = 0; s = 0; break
        case "Off": return [level: 0]
    }
    
    def l = level != null ? level : 100
    if (l < 0) l = 0
    if (l > 100) l = 100
    
    return [hue: h, saturation: s, level: l]
}

def getInovelliColorValue(colorStr) {
    switch (colorStr) {
        case "Red": return 0
        case "Orange": return 21
        case "Yellow": return 42
        case "Green": return 85
        case "Cyan": return 127
        case "Blue": return 170
        case "Purple": return 212
        case "Pink": return 234
        case "White": return 255
        default: return 170
    }
}

def getInovelliEffectValue(effectStr) {
    switch (effectStr) {
        case "Solid": return 1
        case "Fast Blink": return 2
        case "Slow Blink": return 3
        case "Pulse": return 4
        case "Chase": return 5
        case "Aurora": return 8
        case "Falling": return 11 // Maps to Fast Falling
        case "Rising": return 14 // Maps to Fast Rising
        case "Siren": return 18 // Maps to Fast Siren
        default: return 1
    }
}

def getActiveRuleMatrix() {
    def matrix = []
    def currentMode = location.mode
    
    for (int i = 1; i <= 10; i++) {
        if (settings["ruleEnable_${i}"]) {
            def rName = settings["ruleName_${i}"] ?: "Rule ${i}"
            def rPri = settings["rulePriority_${i}"] ?: i
            def rType = settings["ruleType_${i}"]
            def rColor = settings["ruleColor_${i}"] ?: "Blue"
            def rLevel = settings["ruleLevel_${i}"] ?: 100
            def rEffect = settings["ruleEffect_${i}"] ?: "Solid"
            
            def isActive = false
            
            if (rType == "Switch" && settings["ruleSwitch_${i}"]) {
                def targetState = settings["ruleSwitchState_${i}"] ?: "on"
                if (settings["ruleSwitch_${i}"].currentValue("switch") == targetState) {
                    isActive = true
                }
            } else if (rType == "Mode" && settings["ruleMode_${i}"]) {
                if (settings["ruleMode_${i}"].contains(currentMode)) {
                    isActive = true
                }
            }
            
            matrix << [
                id: i,
                name: rName,
                priority: rPri as Integer,
                type: rType,
                color: rColor,
                level: rLevel as Integer,
                effect: rEffect,
                isActive: isActive,
                isHighestActive: false
            ]
        }
    }
    
    matrix = matrix.sort { it.priority }
    
    def foundHighest = false
    matrix.each { r ->
        if (r.isActive && !foundHighest) {
            r.isHighestActive = true
            foundHighest = true
        }
    }
    
    return matrix
}

def evaluateLEDs(forcePhysical = false) {
    def numNodes = settings.numNodes ?: 1
    
    // 1. Determine Vacancy per Node
    for (int i = 1; i <= numNodes; i++) {
        def mSensors = settings["motion_${i}"]
        if (mSensors) {
            def anyActive = mSensors.any { it.currentValue("motion") == "active" }
            if (!anyActive) {
                def mTime = state."nodeLastMotion_${i}" ?: 0
                def timeoutMs = (motionSleepDelay != null ? motionSleepDelay : 5) * 60000
                if ((now() - mTime) >= timeoutMs) {
                    state."nodeVacant_${i}" = true
                } else {
                    state."nodeVacant_${i}" = false
                }
            } else {
                state."nodeVacant_${i}" = false
                state."nodeLastMotion_${i}" = now() // Self-healing
            }
        } else {
            state."nodeVacant_${i}" = false // No sensor = always occupied
        }
    }

    // 2. Evaluate Global Rule Stack
    def rules = getActiveRuleMatrix()
    def winningRule = rules.find { it.isActive }
    
    def globalColor
    def globalLevel
    def globalEffect
    def globalName
    def globalPriority
    
    def currentMode = location.mode
    def isSleepMode = sleepModes?.contains(currentMode)
    
    if (winningRule) {
        globalColor = winningRule.color
        globalLevel = winningRule.level
        globalEffect = winningRule.effect
        globalName = winningRule.name
        globalPriority = winningRule.priority
        
        // ENERGY RULE: Nighttime Alert Brightness Capping
        if (isSleepMode) {
            def maxLvl = nightMaxLevel != null ? nightMaxLevel : 20
            if (globalLevel > maxLvl) {
                globalLevel = maxLvl
                logDebug("Energy Savings: Capping alert brightness to ${maxLvl}% during Sleep Mode.")
            }
        }
        
    } else {
        globalColor = settings.defaultColor ?: "Blue"
        globalLevel = settings.defaultLevel != null ? settings.defaultLevel : 30
        globalEffect = settings.defaultEffect ?: "Solid"
        globalName = null
        globalPriority = 999
        
        // ENERGY RULE: Mode-Based Base Dimming
        if (isSleepMode) {
            globalLevel = sleepBaseLevel != null ? sleepBaseLevel : 0
            logDebug("Energy Savings: Sleep Mode active. Dropping base state to ${globalLevel}%.")
        } 
    }
    
    // Ensure device maps exist for Mesh Optimization
    if (!state.devColor) state.devColor = [:]
    if (!state.devLevel) state.devLevel = [:]
    if (!state.devEffect) state.devEffect = [:]
    
    def anyNodeChanged = false

    // 3. Process Logic Per Node
    for (int i = 1; i <= numNodes; i++) {
        def leds = settings["leds_${i}"]
        if (!leds) continue
        
        def nodeLevel = globalLevel
        def nodeColor = globalColor
        def nodeEffect = globalEffect
        
        // ENERGY RULE: Idle Standby Check per node
        if (!winningRule && idleTimeout && idleTimeout > 0) {
            def idleMs = idleTimeout * 60000
            def timeSinceActive = now() - (state."nodeLastActive_${i}" ?: now())
            
            if (timeSinceActive >= idleMs) {
                nodeLevel = 0 
                logDebug("Energy Savings: Node ${i} Idle Timeout reached. Turning off base LED.")
            } else {
                def remainingSec = Math.ceil((idleMs - timeSinceActive) / 1000).toInteger()
                if (remainingSec > 0) runIn(remainingSec + 1, "evalWrapper")
            }
        }
        
        // ENERGY RULE: Proximity / Motion Wake-Up Override
        if (state."nodeVacant_${i}") {
            nodeLevel = 0
            logDebug("Energy Savings: Node ${i} is vacant. Forcing LED to 0% (Total Blackout).")
        }
        
        // 4. Send Commands to Devices (With Mesh Optimization)
        leds.each { dev -> 
            def dId = dev.id.toString()
            def prevColor = state.devColor[dId]
            def prevLevel = state.devLevel[dId]
            def prevEffect = state.devEffect[dId]
            
            if (forcePhysical || prevColor != nodeColor || prevLevel != nodeLevel || prevEffect != nodeEffect) {
                anyNodeChanged = true
                
                try {
                    if (nodeColor == "Off" || nodeLevel == 0) {
                        if (dev.hasCommand("ledEffectAll")) {
                            dev.ledEffectAll(255, 0, 0, 0)
                        } else if (dev.hasCommand("off")) {
                            dev.off()
                        }
                    } else {
                        def colorMap = getHubitatColorMap(nodeColor, nodeLevel)
                        def inovColor = getInovelliColorValue(nodeColor)
                        def inovEffect = getInovelliEffectValue(nodeEffect)
                        
                        if (dev.hasCommand("ledEffectAll")) {
                            dev.ledEffectAll(inovEffect, inovColor, nodeLevel, 255)
                        } else if (dev.hasCommand("setColor")) {
                            dev.on()
                            dev.setColor(colorMap)
                        } else {
                            log.error "${dev.displayName} does not support setColor or ledEffectAll commands."
                        }
                    }
                } catch (e) {
                    log.error "Failed to set color/effect on ${dev.displayName}: ${e.message}"
                }
                
                // Update Local Mesh Tracker
                state.devColor[dId] = nodeColor
                state.devLevel[dId] = nodeLevel
                state.devEffect[dId] = nodeEffect
            }
        }
    }
    
    if (anyNodeChanged) {
        def cause = forcePhysical ? "Forced Execution" : "Stack Shifted"
        logAction("${cause}. Master Engine Output -> Color: ${globalColor} @ ${globalLevel}%, Effect: ${globalEffect} [Rule: ${globalName ?: 'Base Default'}]")
    } else {
        logDebug("Mesh Optimization: Stack evaluated but no physical changes required. Priority ${globalPriority} still holds. Suppressing radio transmissions.")
    }
    
    // Update global state trackers for the UI
    state.currentPriority = globalPriority
    state.currentColor = globalColor
    state.currentLevel = globalLevel
    state.currentEffect = globalEffect
    state.currentRuleName = globalName
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
def logDebug(msg) { if (debugEnable) log.debug "${app.label}: ${msg}" }
