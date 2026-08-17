/**
 * Advanced Soundbar Command 2.0
 */ 

definition(
    name: "Advanced Soundbar Command 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "",
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
            def zoneHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Zone Name</th><th>TV Entertainment Switch</th><th>Broadlink Target</th><th>Last Command Time</th></tr></thead><tbody>"
            def hasZones = false
            
            for (int i = 1; i <= 3; i++) {
                if (settings["enableZ${i}"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Zone ${i}"
                    
                    def tvSwitchState = settings["z${i}Switch"] ? settings["z${i}Switch"].currentValue("switch")?.toUpperCase() : "No Switch"
                    def tvSwitchColor = tvSwitchState == "ON" ? "green" : "black"

                    def broadlinkState = settings["z${i}Broadlink"] ? settings["z${i}Broadlink"].displayName : "No Hub Selected"
                    
                    // FIXED: Converted GString map key to String for proper retrieval
                    def lastCmdTime = state.lastCommandTime?.get("z${i}".toString()) ?: "<span style='color:gray;'>None Sent</span>"
                    
                    zoneHTML += "<tr><td><b>${zName}</b></td><td style='color:${tvSwitchColor};'><b>${tvSwitchState}</b></td><td>${broadlinkState}</td><td>${lastCmdTime}</td></tr>"
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
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Acts as the master configuration to enable the logic engine and set restricted modes where Soundbar automations are ignored.</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            paragraph "<hr>"
            input "restrictedModes", "mode", title: "Restricted Modes (Ignore TV switch changes during these modes)", multiple: true, required: false
        }

        section("<b>2. Soundbar Zones Configuration</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Toggle individual TV/Soundbar zones (Up to 3). Click on an enabled zone below to expand its settings.</div>"
            
            for (int i = 1; i <= 3; i++) {
                input "enableZ${i}", "bool", title: "<b>Enable Zone ${i}</b>", submitOnChange: true
            }
        }
            
        for (int i = 1; i <= 3; i++) {
            if (settings["enableZ${i}"]) {
                def currentZoneName = settings["z${i}Name"] ?: "Zone ${i}"
                section("<b>⚙️ ${currentZoneName} Configuration</b>", hideable: true, hidden: true) {
                    input "z${i}Name", "text", title: "Zone Name", required: false, defaultValue: "TV Soundbar ${i}"
                    
                    paragraph "<b>App Manual Broadlink Override</b>"
                    input "z${i}ForceOn", "button", title: "🟢 Send Command Manually"
                    
                    paragraph "<hr><b>Trigger Device</b>"
                    input "z${i}Switch", "capability.switch", title: "TV Entertainment Switch (Sends command when turned ON)", required: true
                    
                    paragraph "<b>Broadlink Configuration</b>"
                    // submitOnChange is vital here so the app can fetch the codes immediately after you select the hub
                    input "z${i}Broadlink", "capability.actuator", title: "Select Broadlink Hub/Device", required: true, submitOnChange: true
                    
                    if (settings["z${i}Broadlink"]) {
                        input "z${i}CmdMethod", "enum", title: "Broadlink Command Method", options: ["sendSavedCode", "push", "on"], defaultValue: "push", required: true, submitOnChange: true
                        
                        if (settings["z${i}CmdMethod"] != "on") {
                            def learnedCodes = getLearnedCodes(settings["z${i}Broadlink"])
                            
                            if (learnedCodes) {
                                input "z${i}CmdOn", "enum", title: "Select Learned Code to Send", options: learnedCodes, required: true
                            } else {
                                paragraph "<i style='color:red; font-size: 13px;'>Could not automatically extract learned codes from this driver's attributes. Please type the exact code name manually below.</i>"
                                input "z${i}CmdOn", "text", title: "Saved Code String to Send", required: true
                            }
                        } else {
                            paragraph "<i style='color:gray; font-size: 13px;'>The app will send a standard 'on()' capability directly to the device.</i>"
                        }
                    }
                    
                    paragraph "<b>Command Execution Delay</b>"
                    input "z${i}Delay", "number", title: "Delay (in seconds) before sending command after TV switch turns ON", required: true, defaultValue: 0
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "<span style='color:red;'><b>App Disabled by Master Switch.</b></span>"
    if (restrictedModes && (restrictedModes as List).contains(location.mode)) return "<span style='color:orange;'><b>Restricted Mode Active (Ignoring Switches).</b></span>"
    return "Monitoring TV switches actively."
}

// Helper function to extract learned codes dynamically based on popular community drivers
def getLearnedCodes(device) {
    if (!device) return null
    def options = []
    
    try {
        // 1. Check for the 'tomw' Broadlink Driver specifically
        // This driver hides codes in state variables but exposes them to Apps via DataValues 
        // if cacheCodesForApp(true) is invoked.
        if (device.hasCommand("cacheCodesForApp")) {
            device.cacheCodesForApp(true)
        }
        
        def dataCodes = device.getDataValue("codes")
        if (dataCodes) {
            def parsed = new groovy.json.JsonSlurper().parseText(dataCodes)
            if (parsed instanceof Map) options = parsed.keySet().toList()
        }
        
        // 2. Fallback to scanning standard attributes used by other Hubitat Broadlink drivers
        if (!options) {
            def codesAttr = device.currentValue("savedCodes") ?: device.currentValue("codes") ?: device.currentValue("learnedCodes")
            
            if (codesAttr) {
                if (codesAttr instanceof String && (codesAttr.startsWith("{") || codesAttr.startsWith("["))) {
                    def parsed = new groovy.json.JsonSlurper().parseText(codesAttr)
                    if (parsed instanceof Map) options = parsed.keySet().toList()
                    else if (parsed instanceof List) options = parsed
                } else if (codesAttr instanceof Map) {
                    options = codesAttr.keySet().toList()
                } else if (codesAttr instanceof List) {
                    options = codesAttr
                } else if (codesAttr instanceof String && codesAttr.contains(",")) {
                    // Fallback for simple comma-separated strings
                    options = codesAttr.split(",").collect { it.trim() }
                }
            }
        }
    } catch (e) {
        log.warn "${app.label}: Could not parse learned codes from device: ${e}"
    }
    
    return options?.sort() ?: null
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.lastCommandTime) state.lastCommandTime = [:]
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", "systemEventHandler")
    subscribe(location, "mode", modeChangeHandler)
    
    for (int i = 1; i <= 3; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Switch"]) {
            subscribe(settings["z${i}Switch"], "switch", "tvSwitchHandler")
        }
    }
    
    logAction("App Initialized. Logic Engine Ready.")
}

def appButtonHandler(btn) {
    if (btn == "resetActionHistory") {
        state.actionHistory = []
    }
    
    // Check if any of the Force Command buttons were clicked
    for (int i = 1; i <= 3; i++) {
        if (btn == "z${i}ForceOn") {
            logAction("App UI Override: Forcing Zone ${i} Command")
            executeCommand(i)
        }
    }
}

def systemEventHandler(evt) {
    logAction("System Master Switch changed to: ${evt.value}")
}

def modeChangeHandler(evt) { 
    def newMode = evt.value
    state.currentMode = newMode
    logAction("Location Mode changed to: ${newMode}")
}

def tvSwitchHandler(evt) {
    def devId = evt.device.id
    def switchVal = evt.value

    // 1. Check Global Application Switch Restrictions
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
        logAction("Ignored TV switch change - Master App Switch is OFF.")
        return
    }
    
    // 2. Check Global Location Mode Restrictions
    if (restrictedModes && (restrictedModes as List).contains(location.mode)) {
        logAction("Ignored TV switch change - Location Mode (${location.mode}) is restricted.")
        return
    }

    // 3. Process Logic for matching zones (Only triggers when turning ON)
    if (switchVal == "on") {
        for (int i = 1; i <= 3; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Switch"]?.id == devId) {
                def delaySeconds = settings["z${i}Delay"] ?: 0
                
                if (delaySeconds > 0) {
                    logAction("TV Switch (ON) detected for Zone ${i}. Delaying Broadlink command by ${delaySeconds} seconds.")
                    runIn(delaySeconds, "delayedCommandWrapper", [data: [zoneNum: i]])
                } else {
                    executeCommand(i)
                }
            }
        }
    } else {
        logAction("TV Switch turned OFF. Ignoring command execution based on app configuration.")
    }
}

def delayedCommandWrapper(data) {
    executeCommand(data.zoneNum)
}

def executeCommand(int i) {
    def zName = settings["z${i}Name"] ?: "Zone ${i}"
    def blDevice = settings["z${i}Broadlink"]
    def method = settings["z${i}CmdMethod"] ?: "push"
    
    if (!blDevice) {
        log.error "${app.label}: Broadlink device not found for ${zName}"
        return
    }

    def cmdString = settings["z${i}CmdOn"]
    
    try {
        if (method == "on") {
            logAction("Sending direct 'on()' capability to Broadlink Hub for ${zName}")
            blDevice.on()
        } else if (cmdString) {
            logAction("Triggering Broadlink '${method}' with code '${cmdString}' for ${zName}")
            if (method == "sendSavedCode") {
                blDevice.sendSavedCode(cmdString)
            } else if (method == "push") {
                blDevice.push(cmdString)
            }
        } else {
            log.warn "${app.label}: Missing command string for Zone ${i}"
        }
        
        // FIXED: Clone state map and convert key to String to ensure Hubitat saves it properly
        def tracking = state.lastCommandTime ? state.lastCommandTime.clone() : [:]
        tracking["z${i}".toString()] = "[SENT] at " + new Date().format("h:mm a", location.timeZone)
        state.lastCommandTime = tracking
        
    } catch (e) {
        log.error "${app.label}: Error executing Broadlink command for Zone ${i}: ${e}"
        logAction("ERROR: Failed to send command to ${blDevice.displayName}")
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
