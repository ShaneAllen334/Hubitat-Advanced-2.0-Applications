/**
 * Advanced Reading Light Enable 2.0
 */ 

definition(
    name: "Advanced Reading Light Enable 2.0",
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
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"

            def masterState = appEnableSwitch ? appEnableSwitch.currentValue("switch")?.toUpperCase() : "ON (NO SWITCH)"
            def currentLocMode = location.mode ?: "Unknown"

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
                <thead><tr><th>Metric</th><th colspan="3">Current Value</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Master App Switch</td><td colspan="3" class="dash-val"><b>${masterState}</b></td></tr>
                    <tr><td class="dash-hl">Location Mode</td><td colspan="3" class="dash-val">${currentLocMode}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>Zone Status Breakdown</b>", hideable: true) {
            def zoneHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Zone Name</th><th>Virtual Override</th><th>Target Lights</th><th>Time Remaining</th></tr></thead><tbody>"
            def hasZones = false
            
            for (int i = 1; i <= 4; i++) {
                if (settings["enableZ${i}"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Zone ${i}"
                    
                    def vSwitchState = settings["z${i}OverrideSwitch"] ? settings["z${i}OverrideSwitch"].currentValue("switch")?.toUpperCase() : "No Switch"
                    def vSwitchColor = vSwitchState == "ON" ? "green" : "black"

                    def lightsState = settings["z${i}Lights"] ? settings["z${i}Lights"][0].currentValue("switch")?.toUpperCase() : "No Lights"
                    
                    def remainStr = "<span style='color:gray;'>Inactive</span>"
                    
                    if (state["timeout_z${i}"]) {
                        def remainMs = state["timeout_z${i}"].toLong() - now()
                        if (remainMs > 0) {
                            def remainMins = Math.ceil(remainMs / 60000.0).toInteger()
                            remainStr = "<span style='color:purple;'><b>Active</b> (${remainMins}m left)</span>"
                        }
                    }
                    
                    zoneHTML += "<tr><td><b>${zName}</b></td><td style='color:${vSwitchColor};'><b>${vSwitchState}</b></td><td>${lightsState}</td><td>${remainStr}</td></tr>"
                }
            }
            zoneHTML += "</tbody></table>"
            if (hasZones) paragraph zoneHTML else paragraph "<i>No zones configured.</i>"
        }

        section("<b>Recent Action History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>1. Global Application Settings</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Acts as the master configuration to enable the logic engine and set blackout modes (like Away Mode).</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            paragraph "<hr>"
            input "restrictedModes", "mode", title: "Restricted Modes (Disable all reading lights and ignore button presses when these modes activate)", multiple: true, required: false
        }

        section("<b>2. Reading Zones Configuration</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Toggle individual reading zones. Click on an enabled zone below to expand its settings.</div>"
            
            for (int i = 1; i <= 4; i++) {
                input "enableZ${i}", "bool", title: "<b>Enable Zone ${i}</b>", submitOnChange: true
            }
        }
            
        for (int i = 1; i <= 4; i++) {
            if (settings["enableZ${i}"]) {
                def currentZoneName = settings["z${i}Name"] ?: "Zone ${i}"
                section("<b>⚙️ ${currentZoneName} Configuration</b>", hideable: true, hidden: true) {
                    input "z${i}Name", "text", title: "Zone Name", required: false, defaultValue: "Reading Area ${i}"
                    
                    paragraph "<b>App Manual Overrides</b>"
                    input "z${i}ForceOn", "button", title: "🟢 Force ON"
                    input "z${i}ForceOff", "button", title: "🔴 Force OFF"
                    
                    paragraph "<hr><b>Activation Trigger (Turn ON / Toggle)</b>"
                    input "z${i}Btn", "capability.pushableButton", title: "Trigger Button Device", required: true
                    input "z${i}BtnNum", "number", title: "Button Number", required: true, defaultValue: 1
                    input "z${i}BtnAction", "enum", title: "Button Action Required", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "pushed"
                    input "z${i}BtnToggle", "bool", title: "Make this button a Toggle (Turn OFF if already ON)?", required: false, defaultValue: false, submitOnChange: true
                    
                    if (!settings["z${i}BtnToggle"]) {
                        paragraph "<b>Deactivation Trigger (Turn OFF)</b>"
                        input "z${i}OffBtn", "capability.pushableButton", title: "Turn OFF: Trigger Button Device (Optional)", required: false, submitOnChange: true
                        if (settings["z${i}OffBtn"]) {
                            input "z${i}OffBtnNum", "number", title: "Turn OFF: Button Number", required: true, defaultValue: 1
                            input "z${i}OffBtnAction", "enum", title: "Turn OFF: Button Action Required", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "held"
                        }
                    }
                    
                    paragraph "<b>Virtual Switch Link</b>"
                    input "z${i}OverrideSwitch", "capability.switch", title: "Reading Light Enabled Virtual Switch (Turns ON when active)", required: false
                    
                    paragraph "<b>Target Lighting</b>"
                    input "z${i}LightType", "enum", title: "Light Type", options: ["Dimmable / Color / Temp", "Simple On/Off Switch"], defaultValue: "Dimmable / Color / Temp", submitOnChange: true
                    input "z${i}Lights", "capability.switch", title: "Select Target Lights", required: true, multiple: true
                    
                    if (settings["z${i}LightType"] != "Simple On/Off Switch") {
                        input "z${i}Level", "number", title: "Brightness Percentage (1-100)", range: "1..100", required: true, defaultValue: 80
                        
                        input "z${i}LightMode", "enum", title: "Lighting Mode", options: ["Color Temperature (Kelvin)", "Color (RGB)"], defaultValue: "Color Temperature (Kelvin)", submitOnChange: true
                        if (settings["z${i}LightMode"] == "Color (RGB)") {
                            input "z${i}ColorHue", "number", title: "Hue (0-100)", range: "0..100", required: true, defaultValue: 0
                            input "z${i}ColorSat", "number", title: "Saturation (0-100)", range: "0..100", required: true, defaultValue: 100
                        } else {
                            input "z${i}ColorTemp", "number", title: "Color Temperature (Kelvin, e.g., 2700 for warm)", required: true, defaultValue: 3000
                        }
                    }
                    
                    paragraph "<b>Automation Safety Timer</b>"
                    input "z${i}Timeout", "decimal", title: "Auto-Off Timer (Hours before it turns itself off)", required: true, defaultValue: 2.0
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { 
    logInfo("Updated")
    state.remove("zoneTimeouts") // Destroys the corrupted ghost map
    unsubscribe()
    unschedule()
    initialize() 
}

def getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "<span style='color:red;'><b>App Disabled by Master Switch.</b></span>"
    if (restrictedModes && (restrictedModes as List).contains(location.mode)) return "<span style='color:orange;'><b>Restricted Mode Active (Lights Locked Off).</b></span>"
    return "Monitoring button triggers actively."
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    state.remove("zoneTimeouts")
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", "systemEventHandler")
    subscribe(location, "mode", modeChangeHandler)
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}Btn"] && settings["z${i}BtnAction"]) {
                subscribe(settings["z${i}Btn"], settings["z${i}BtnAction"], "buttonHandler")
            }
            if (settings["z${i}OffBtn"] && settings["z${i}OffBtnAction"]) {
                subscribe(settings["z${i}OffBtn"], settings["z${i}OffBtnAction"], "buttonHandler")
            }
            if (settings["z${i}OverrideSwitch"]) {
                subscribe(settings["z${i}OverrideSwitch"], "switch", "overrideSwitchHandler")
            }
            if (settings["z${i}Lights"]) {
                subscribe(settings["z${i}Lights"], "switch.off", "physicalLightHandler")
            }
        }
    }
    
    schedule("0 * * * * ?", evaluateSystem)
    
    logAction("App Initialized. Logic Engine Ready.")
    evaluateSystem()
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        state.remove("zoneTimeouts")
        logAction("Manual UI Refresh. Ghost maps forcibly purged.")
    }
    if (btn == "resetActionHistory") {
        state.actionHistory = []
    }
    
    for (int i = 1; i <= 4; i++) {
        if (btn == "z${i}ForceOn") {
            logAction("App UI Override: Forcing Zone ${i} ON")
            activateZone(i)
        } else if (btn == "z${i}ForceOff") {
            logAction("App UI Override: Forcing Zone ${i} OFF")
            deactivateZone(i, "Manual App UI Override")
        }
    }
}

def systemEventHandler(evt) {
    try {
        runIn(1, "evaluateSystem", [overwrite: true])
    } catch (e) {
        log.error "${app.label}: Error executing systemEventHandler: ${e}"
    }
}

def overrideSwitchHandler(evt) {
    def devId = evt.device.id
    if (evt.value == "off") {
        for (int i = 1; i <= 4; i++) {
            if (settings["enableZ${i}"] && settings["z${i}OverrideSwitch"]?.id == devId) {
                if (state["timeout_z${i}"]) {
                    logAction("Virtual Switch for Zone ${i} was turned OFF manually. Clearing logic.")
                    deactivateZone(i, "Manual Override Switched Off")
                }
            }
        }
    }
}

def modeChangeHandler(evt) { 
    def newMode = evt.value
    state.currentMode = newMode
    
    if (restrictedModes && (restrictedModes as List).contains(newMode)) {
        logAction("Location Mode changed to Restricted Mode (${newMode}). Forcing all Reading Lights OFF.")
        for (int i = 1; i <= 4; i++) {
            if (settings["enableZ${i}"] && state["timeout_z${i}"]) {
                deactivateZone(i, "Restricted Mode Activated")
            }
        }
    }
}

def buttonHandler(evt) {
    def devId = evt.device.id
    def btnAction = evt.name
    def btnNum = evt.value?.toString()

    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
        logAction("Ignored button press - Master App Switch is OFF.")
        return
    }
    
    if (restrictedModes && (restrictedModes as List).contains(location.mode)) {
        logAction("Ignored button press - Location Mode (${location.mode}) is restricted.")
        return
    }

    for (int i = 1; i <= 4; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}Btn"]?.id == devId && settings["z${i}BtnAction"] == btnAction && settings["z${i}BtnNum"]?.toString() == btnNum) {
                if (settings["z${i}BtnToggle"] && state["timeout_z${i}"]) {
                    deactivateZone(i, "Toggle Button Pressed")
                } else {
                    activateZone(i)
                }
            }
            
            if (!settings["z${i}BtnToggle"] && settings["z${i}OffBtn"]?.id == devId && settings["z${i}OffBtnAction"] == btnAction && settings["z${i}OffBtnNum"]?.toString() == btnNum) {
                deactivateZone(i, "Deactivation Button Pressed")
            }
        }
    }
}

def physicalLightHandler(evt) {
    def devId = evt.device.id
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableZ${i}"] && state["timeout_z${i}"]) {
            
            // STATE MISMATCH GUARD
            def vSwitch = settings["z${i}OverrideSwitch"]
            if (vSwitch && vSwitch.currentValue("switch") == "off") {
                state.remove("timeout_z${i}")
                continue
            }

            def zoneLights = settings["z${i}Lights"]
            def isMatch = false
            if (zoneLights) {
                zoneLights.each { if (it.id == devId) isMatch = true }
            }
            
            if (isMatch) {
                logAction("Target light turned OFF externally. Clearing Zone ${i} timer and resetting logic.")
                deactivateZone(i, "External/Physical Switch Turned Off")
            }
        }
    }
}

def activateZone(int i) {
    def zName = settings["z${i}Name"] ?: "Zone ${i}"
    def vSwitch = settings["z${i}OverrideSwitch"]
    def lights = settings["z${i}Lights"]
    def lightType = settings["z${i}LightType"] ?: "Dimmable / Color / Temp"
    def timeoutHours = settings["z${i}Timeout"] != null ? settings["z${i}Timeout"].toFloat() : 2.0

    if (vSwitch && vSwitch.currentValue("switch") != "on") {
        vSwitch.on()
    }

    if (lights) {
        lights.on()
        
        if (lightType != "Simple On/Off Switch") {
            def lvl = settings["z${i}Level"] ?: 80
            def mode = settings["z${i}LightMode"] ?: "Color Temperature (Kelvin)"
            
            lights.setLevel(lvl)

            if (mode == "Color Temperature (Kelvin)" && settings["z${i}ColorTemp"]) {
                lights.setColorTemperature(settings["z${i}ColorTemp"])
            } else if (mode == "Color (RGB)" && settings["z${i}ColorHue"] != null && settings["z${i}ColorSat"] != null) {
                lights.setColor([hue: settings["z${i}ColorHue"], saturation: settings["z${i}ColorSat"], level: lvl])
            }
        }
    }

    state["timeout_z${i}"] = now() + (timeoutHours * 3600000).toLong()

    logAction("Reading Mode Activated for ${zName}. Auto-off scheduled in ${timeoutHours} hours.")
    runIn(1, "evaluateSystem", [overwrite: true])
}

def deactivateZone(int i, String reason) {
    def zName = settings["z${i}Name"] ?: "Zone ${i}"
    def vSwitch = settings["z${i}OverrideSwitch"]
    def lights = settings["z${i}Lights"]

    state.remove("timeout_z${i}")

    // ---------------------------------------------------------
    // THE GOLDEN RULE / STATE MISMATCH GUARD
    // ---------------------------------------------------------
    boolean isBackgroundEvent = (reason == "Auto-Off Timer Expired" || reason == "External/Physical Switch Turned Off" || reason == "Restricted Mode Activated")
    
    if (isBackgroundEvent && vSwitch && vSwitch.currentValue("switch") == "off") {
        logAction("Mismatch Guard: Bypassed light shutoff for ${zName} because Virtual Switch is already OFF. (${reason})")
        return
    }
    // ---------------------------------------------------------

    if (vSwitch && vSwitch.currentValue("switch") != "off") vSwitch.off()
    
    if (lights) {
        lights.each { light ->
            if (light.currentValue("switch") != "off") {
                light.off()
            }
        }
    }

    logAction("Reading Mode Deactivated for ${zName}. Reason: ${reason}")
}

// --- SYSTEM HOUSEKEEPING ---
def evaluateSystem() {
    def currentTime = now()

    for (int i = 1; i <= 4; i++) {
        if (settings["enableZ${i}"] && state["timeout_z${i}"]) {
            
            // STATE MISMATCH GUARD
            def vSwitch = settings["z${i}OverrideSwitch"]
            if (vSwitch && vSwitch.currentValue("switch") == "off") {
                state.remove("timeout_z${i}")
                continue
            }

            if (currentTime >= state["timeout_z${i}"].toLong()) {
                deactivateZone(i, "Auto-Off Timer Expired")
            }
        }
    }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ? new ArrayList(state.actionHistory) : []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
