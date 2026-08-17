/**
 * Advanced Sunrise Wake Lighting 2.0
 */ 

definition(
    name: "Advanced Sunrise Wake Lighting 2.0",
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
                <thead><tr><th>Metric</th><th colspan="5">Current Value</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Master App Switch</td><td colspan="5" class="dash-val"><b>${masterState}</b></td></tr>
                    
                    <tr><td colspan="6" class="dash-subhead">Room Configuration & Status</td></tr>
                    <tr>
                        <th style="background-color:#6c757d; font-size:12px;">Room Name</th>
                        <th style="background-color:#6c757d; font-size:12px;">Wake Times</th>
                        <th style="background-color:#6c757d; font-size:12px;">Good Night</th>
                        <th style="background-color:#6c757d; font-size:12px;">Work/School</th>
                        <th style="background-color:#6c757d; font-size:12px;">Sick Mode</th>
                        <th style="background-color:#6c757d; font-size:12px;">Wake Status</th>
                    </tr>
            """
            
            def hasZones = false
            def numR = getNumRooms()
            
            for (int i = 1; i <= numR; i++) {
                if (settings["enableR${i}"] && settings["r${i}_lights"]) {
                    hasZones = true
                    def rName = settings["r${i}_name"] ?: "Room ${i}"
                    
                    def wDayTime = settings["r${i}_weekdayTime"] ? new Date(timeToday(settings["r${i}_weekdayTime"]).time).format("h:mm a", location.timeZone) : "--:--"
                    def wEndTime = settings["r${i}_weekendTime"] ? new Date(timeToday(settings["r${i}_weekendTime"]).time).format("h:mm a", location.timeZone) : "--:--"
                    def altTimeTxt = (settings["r${i}_useAltTime"] && settings["r${i}_altTime"]) ? new Date(timeToday(settings["r${i}_altTime"]).time).format("h:mm a", location.timeZone) : "--:--"
                    
                    def timeTxt = "WD: ${wDayTime}<br>WE: ${wEndTime}"
                    if (settings["r${i}_useAltTime"]) timeTxt += "<br>ALT: ${altTimeTxt}"
                    
                    def gnSwitch = settings["r${i}_gnSwitch"]
                    def switchState = gnSwitch ? gnSwitch.currentValue("switch")?.toUpperCase() : "N/A"
                    def switchColor = (switchState == "ON") ? "green" : (switchState == "OFF" ? "red" : "black")
                    
                    def activeDaySwitch = settings["r${i}_activeDaySwitch"]
                    def activeDayState = activeDaySwitch ? activeDaySwitch.currentValue("switch")?.toUpperCase() : "N/A"
                    def activeDayColor = (activeDayState == "ON") ? "green" : (activeDayState == "OFF" ? "red" : "black")
                    
                    def sickSwitch = settings["r${i}_sickSwitch"]
                    def sickState = sickSwitch ? sickSwitch.currentValue("switch")?.toUpperCase() : "N/A"
                    def sickColor = (sickState == "ON") ? "red" : (sickState == "OFF" ? "green" : "black")
                    
                    def fadeStatus = "<i>WAITING</i>"
                    if (state["r${i}_isSnoozing"]) {
                        fadeStatus = "<span style='color: #9c27b0; font-weight: bold;'>SNOOZING</span>"
                    } else if (state["r${i}_isHolding"]) {
                        fadeStatus = "<span style='color: #007bff; font-weight: bold;'>HOLDING AT 1%</span>"
                    } else if (state["r${i}_isFading"]) {
                        def curPct = state["r${i}_currentPct"] ?: 0
                        def curTemp = state["r${i}_currentTemp"] ?: 2500
                        fadeStatus = "<span style='color: orange; font-weight: bold;'>FADING (${curPct}% at ${curTemp}K)</span>"
                    }

                    dashHTML += """
                    <tr>
                        <td><b>${rName}</b></td>
                        <td style="font-size: 12px;">${timeTxt}</td>
                        <td style="color:${switchColor}; font-weight:bold;">${switchState}</td>
                        <td style="color:${activeDayColor}; font-weight:bold;">${activeDayState}</td>
                        <td style="color:${sickColor}; font-weight:bold;">${sickState}</td>
                        <td>${fadeStatus}</td>
                    </tr>
                    """
                }
            }
            
            if (!hasZones) {
                dashHTML += "<tr><td colspan='6'><i>No rooms configured or enabled yet.</i></td></tr>"
            }
            
            dashHTML += "</tbody></table>"
            paragraph dashHTML
        }

        section("<b>Recent Action History</b>", hideable: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
            input "btnForceReset", "button", title: "Reset Room State Variables"
        }

        section("<b>1. Global App Control</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Master toggles for the sunrise logic engine.</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            input "numberOfRooms", "enum", title: "Number of Rooms to Configure", options: ["1","2","3","4","5"], defaultValue: "3", submitOnChange: true
        }
        
        section("<b>2. Room Selection</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Toggle individual sunrise rooms. Click on an enabled room below to expand its settings.</div>"
            def numR = getNumRooms()
            for (int i = 1; i <= numR; i++) {
                input "enableR${i}", "bool", title: "<b>Enable Room ${i}</b>", submitOnChange: true
            }
        }

        def numR = getNumRooms()
        for (int i = 1; i <= numR; i++) {
            if (settings["enableR${i}"]) {
                def currentRoomName = settings["r${i}_name"] ?: "Room ${i}"
                section("<b>⚙️ ${currentRoomName} Configuration</b>", hideable: true, hidden: true) {
                    input "r${i}_name", "text", title: "Custom Room Name", required: false, defaultValue: "Room ${i}", submitOnChange: true
                    input "r${i}_lights", "capability.colorTemperature", title: "Wake Lights (Must support Color Temp)", multiple: true, required: true
                    
                    if (settings["r${i}_lights"]) {
                        paragraph "<b>Timing & Limits</b>"
                        input "r${i}_weekdayTime", "time", title: "Weekday Start Time", required: false
                        input "r${i}_weekendTime", "time", title: "Weekend Start Time", required: false
                        input "r${i}_holdMins", "number", title: "Pre-Fade Hold Duration (Minutes at 1%)", defaultValue: 5, required: true
                        input "r${i}_duration", "number", title: "Total Fade Duration (Minutes)", defaultValue: 30, required: true
                        
                        input "r${i}_maxLevel", "number", title: "Max Brightness Ceiling (%)", defaultValue: 100, range: "1..100", required: true
                        
                        input "r${i}_useVarTemp", "bool", title: "Sync Color Temp to Hub Variable?", defaultValue: false, submitOnChange: true
                        if (settings["r${i}_useVarTemp"]) {
                            input "r${i}_varTempName", "text", title: "Hub Variable Name (e.g., currentSunTemp)", required: true
                        } else {
                            input "r${i}_maxTemp", "number", title: "Max Color Temp Ceiling (K)", defaultValue: 6500, range: "2500..9000", required: true
                        }

                        paragraph "<b>Energy Savings</b>"
                        input "r${i}_autoOffMins", "number", title: "Auto-Turn Off after fade completion (Minutes, 0 to disable)", defaultValue: 30, required: true
                        
                        paragraph "<b>Snooze Button (Optional)</b>"
                        input "r${i}_snoozeBtn", "capability.pushableButton", title: "Physical Snooze Button", required: false
                        input "r${i}_snoozeMins", "number", title: "Snooze Duration (Minutes)", defaultValue: 9, required: false
                        
                        paragraph "<b>Controls & Handoffs</b>"
                        input "r${i}_gnSwitch", "capability.switch", title: "Virtual 'Good Night' Switch", required: true
                        input "r${i}_sickSwitch", "capability.switch", title: "Virtual 'Sick' Switch (Optional)", required: false
                        input "r${i}_sunriseStateSwitch", "capability.switch", title: "Sunrise Active State Switch", required: false
                        
                        input "r${i}_activeDaySwitch", "capability.switch", title: "Work/School Day Switch (Optional)", required: false, submitOnChange: true
                        if (settings["r${i}_activeDaySwitch"]) {
                            input "r${i}_bypassWeekend", "bool", title: "Ignore this switch on Weekends?", defaultValue: true
                            input "r${i}_useAltTime", "bool", title: "Setup Alternate Wake Time when switch is OFF (Day Off)?", defaultValue: false, submitOnChange: true
                            if (settings["r${i}_useAltTime"]) {
                                input "r${i}_altTime", "time", title: "Alternate 'Day Off' Wake Time", required: true
                            }
                        }

                        input "r${i}_modes", "mode", title: "Active Modes (Optional)", multiple: true, required: false
                        
                        input "r${i}_notifier", "capability.notification", title: "Wake Up Notification Device(s)", multiple: true, required: false
                        input "r${i}_wakeMsg", "text", title: "Wake Up Message", defaultValue: "Time to wake up!", required: false
                    }
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def getNumRooms() {
    return (settings.numberOfRooms != null) ? settings.numberOfRooms.toInteger() : 3
}

def isWeekday() {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def day = new Date().format("EEEE", tz)
    return !(day == "Saturday" || day == "Sunday")
}

def isWeekend() {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def day = new Date().format("EEEE", tz)
    return (day == "Saturday" || day == "Sunday")
}

def installed() { 
    logInfo("Installed")
    initialize() 
}

def updated() { 
    logInfo("Updated")
    unsubscribe()
    unschedule()
    initialize() 
}

def getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
        return "<span style='color:red;'><b>App Disabled:</b></span> Master Switch is OFF."
    }
    
    def activeCount = 0
    def numR = getNumRooms()
    for (int i = 1; i <= numR; i++) {
        if (settings["enableR${i}"] && settings["r${i}_lights"]) activeCount++
    }
    
    if (activeCount == 0) return "Awaiting configuration. Please enable and setup at least one room."
    
    def fadingCount = 0
    for (int i = 1; i <= numR; i++) {
        if (state["r${i}_isFading"]) fadingCount++
    }
    
    if (fadingCount > 0) return "<span style='color:orange;'><b>Active:</b></span> Executing sunrise fades in ${fadingCount} room(s)."
    
    return "Monitoring actively. Awaiting scheduled start times for ${activeCount} configured room(s)."
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    def numR = getNumRooms()
    for (int rNum = 1; rNum <= numR; rNum++) {
        state["r${rNum}_isFading"] = false
        state["r${rNum}_isSnoozing"] = false
        state["r${rNum}_isHolding"] = false
        state["r${rNum}_justResumed"] = false
        state["r${rNum}_currentPct"] = 0
        state["r${rNum}_currentTemp"] = 0
        
        if (settings["enableR${rNum}"] && settings["r${rNum}_lights"]) {
            
            if (settings["r${rNum}_weekdayTime"]) {
                schedule(settings["r${rNum}_weekdayTime"], "room${rNum}WeekdayHandler")
            }
            if (settings["r${rNum}_weekendTime"]) {
                schedule(settings["r${rNum}_weekendTime"], "room${rNum}WeekendHandler")
            }
            if (settings["r${rNum}_activeDaySwitch"] && settings["r${rNum}_useAltTime"] && settings["r${rNum}_altTime"]) {
                schedule(settings["r${rNum}_altTime"], "room${rNum}AltHandler")
            }
            
            if (settings["r${rNum}_gnSwitch"]) {
                subscribe(settings["r${rNum}_gnSwitch"], "switch.off", "room${rNum}SwitchOffHandler")
            }

            subscribe(settings["r${rNum}_lights"], "switch.off", "room${rNum}LightOffHandler")
            
            if (settings["r${rNum}_snoozeBtn"]) {
                subscribe(settings["r${rNum}_snoozeBtn"], "pushed", "room${rNum}SnoozeHandler")
            }
        }
    }
    logAction("App Initialized. Logic Engine Ready.")
}

def appButtonHandler(btn) {
    if (btn == "resetActionHistory") {
        state.actionHistory = []
        logInfo("Action History cleared.")
    } else if (btn == "btnForceReset") {
        def numR = getNumRooms()
        for (int rNum = 1; rNum <= numR; rNum++) {
            state["r${rNum}_isFading"] = false
            state["r${rNum}_isSnoozing"] = false
            state["r${rNum}_isHolding"] = false
            state["r${rNum}_currentPct"] = 0
            state["r${rNum}_currentTemp"] = 0
        }
        logAction("SYSTEM: State variables manually reset by user.")
    }
}

// ------------------------------------------------------------------------------
// ROOM EVENT HANDLERS (Supports up to 5 Rooms)
// ------------------------------------------------------------------------------
def room1WeekdayHandler() { if (isWeekday()) startFadeProcess(1, 'weekday') }
def room1WeekendHandler() { if (isWeekend()) startFadeProcess(1, 'weekend') }
def room1AltHandler()     { startFadeProcess(1, 'alt') }
def room1EndHold()        { endHold(1) }
def room1FadeLoop()       { fadeLoopProcess(1) }
def room1AutoOff()        { executeAutoOff(1) }
def room1SwitchOffHandler(evt) { handleSwitchOff(1) }
def room1LightOffHandler(evt)  { handleLightOff(1, evt) }
def room1SnoozeHandler(evt)    { handleSnooze(1) }
def room1ResumeFade()     { resumeFade(1) }

def room2WeekdayHandler() { if (isWeekday()) startFadeProcess(2, 'weekday') }
def room2WeekendHandler() { if (isWeekend()) startFadeProcess(2, 'weekend') }
def room2AltHandler()     { startFadeProcess(2, 'alt') }
def room2EndHold()        { endHold(2) }
def room2FadeLoop()       { fadeLoopProcess(2) }
def room2AutoOff()        { executeAutoOff(2) }
def room2SwitchOffHandler(evt) { handleSwitchOff(2) }
def room2LightOffHandler(evt)  { handleLightOff(2, evt) }
def room2SnoozeHandler(evt)    { handleSnooze(2) }
def room2ResumeFade()     { resumeFade(2) }

def room3WeekdayHandler() { if (isWeekday()) startFadeProcess(3, 'weekday') }
def room3WeekendHandler() { if (isWeekend()) startFadeProcess(3, 'weekend') }
def room3AltHandler()     { startFadeProcess(3, 'alt') }
def room3EndHold()        { endHold(3) }
def room3FadeLoop()       { fadeLoopProcess(3) }
def room3AutoOff()        { executeAutoOff(3) }
def room3SwitchOffHandler(evt) { handleSwitchOff(3) }
def room3LightOffHandler(evt)  { handleLightOff(3, evt) }
def room3SnoozeHandler(evt)    { handleSnooze(3) }
def room3ResumeFade()     { resumeFade(3) }

def room4WeekdayHandler() { if (isWeekday()) startFadeProcess(4, 'weekday') }
def room4WeekendHandler() { if (isWeekend()) startFadeProcess(4, 'weekend') }
def room4AltHandler()     { startFadeProcess(4, 'alt') }
def room4EndHold()        { endHold(4) }
def room4FadeLoop()       { fadeLoopProcess(4) }
def room4AutoOff()        { executeAutoOff(4) }
def room4SwitchOffHandler(evt) { handleSwitchOff(4) }
def room4LightOffHandler(evt)  { handleLightOff(4, evt) }
def room4SnoozeHandler(evt)    { handleSnooze(4) }
def room4ResumeFade()     { resumeFade(4) }

def room5WeekdayHandler() { if (isWeekday()) startFadeProcess(5, 'weekday') }
def room5WeekendHandler() { if (isWeekend()) startFadeProcess(5, 'weekend') }
def room5AltHandler()     { startFadeProcess(5, 'alt') }
def room5EndHold()        { endHold(5) }
def room5FadeLoop()       { fadeLoopProcess(5) }
def room5AutoOff()        { executeAutoOff(5) }
def room5SwitchOffHandler(evt) { handleSwitchOff(5) }
def room5LightOffHandler(evt)  { handleLightOff(5, evt) }
def room5SnoozeHandler(evt)    { handleSnooze(5) }
def room5ResumeFade()     { resumeFade(5) }

// ------------------------------------------------------------------------------
// APPLICATION LOGIC
// ------------------------------------------------------------------------------

def startFadeProcess(rNum, triggerType = 'manual') {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
        logInfo("Sunrise aborted - Master App Switch is OFF.")
        return
    }

    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    def gnSwitch = settings["r${rNum}_gnSwitch"]
    def sickSwitch = settings["r${rNum}_sickSwitch"]
    def activeDaySwitch = settings["r${rNum}_activeDaySwitch"]
    def modes = settings["r${rNum}_modes"]

    if (gnSwitch && gnSwitch.currentValue("switch") != "on") {
        logInfo("${rName}: Skipped. Good Night switch is OFF.")
        return
    }

    if (activeDaySwitch) {
        def switchIsOn = (activeDaySwitch.currentValue("switch") == "on")
        def bypassWeekend = settings["r${rNum}_bypassWeekend"] != null ? settings["r${rNum}_bypassWeekend"] : true
        
        if (triggerType == 'weekend' && bypassWeekend) {
            // Permitted
        } else if (triggerType == 'weekday' && !switchIsOn) {
            logAction("SKIPPED: ${rName} weekday sunrise aborted. Work/School Day switch is OFF.")
            return
        } else if (triggerType == 'alt' && switchIsOn) {
            logAction("SKIPPED: ${rName} alternate time ignored. Work/School Day switch is ON.")
            return
        } else if (triggerType == 'manual' && !switchIsOn) {
            logAction("SKIPPED: ${rName} sunrise aborted. Work/School Day switch is OFF.")
            return
        }
    } else if (triggerType == 'alt') {
        return
    }

    if (sickSwitch && sickSwitch.currentValue("switch") == "on") {
        logAction("SKIPPED: ${rName} sunrise aborted. Sick mode is active.")
        return
    }
    
    if (modes && !modes.contains(location.mode)) {
        logInfo("${rName}: Skipped. Hub is not in an active mode.")
        return
    }
    
    logAction("SUNRISE: ${rName} simulation started (Trigger: ${triggerType}).")
    
    def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
    if (stateSwitch) stateSwitch.on()
    
    state["r${rNum}_isFading"] = true
    state["r${rNum}_isSnoozing"] = false
    state["r${rNum}_currentPct"] = 1
    state["r${rNum}_currentTemp"] = 2500
    
    def lights = settings["r${rNum}_lights"]
 
    lights.each { light ->
        // Use setLevel to 1% with 0 transition time instead of on() to avoid previous-state flash
        light.setLevel(1, 0)
        if (light.hasCommand("setColorTemperature")) {
            light.setColorTemperature(2500)
        }
    }
    
    def holdMins = settings["r${rNum}_holdMins"] != null ? settings["r${rNum}_holdMins"] : 5
    if (holdMins > 0) {
        state["r${rNum}_isHolding"] = true
        runIn(holdMins * 60, "room${rNum}EndHold")
        logAction("SUNRISE: ${rName} holding at 1% for ${holdMins} minutes.")
    } else {
        state["r${rNum}_isHolding"] = false
        state["r${rNum}_startMs"] = new Date().time
        runIn(60, "room${rNum}FadeLoop")
    }
}

def endHold(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    state["r${rNum}_isHolding"] = false
    state["r${rNum}_startMs"] = new Date().time
    logAction("SUNRISE: ${rName} pre-fade hold complete. Starting transition.")
    fadeLoopProcess(rNum)
}

def fadeLoopProcess(rNum) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
        handleSwitchOff(rNum)
        return
    }

    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    
    if (!state["r${rNum}_isFading"] || state["r${rNum}_isSnoozing"] || state["r${rNum}_isHolding"]) return
    
    def lights = settings["r${rNum}_lights"]
    def expectedLevel = state["r${rNum}_currentPct"] ?: 1
    
    if (state["r${rNum}_justResumed"]) {
        state["r${rNum}_justResumed"] = false
    } else {
        if (expectedLevel > 2) {
            def actualLevel = lights[0].currentValue("level")?.toInteger() ?: expectedLevel
            if (actualLevel > expectedLevel + 15 || actualLevel < expectedLevel - 15) {
                logAction("MANUAL OVERRIDE: ${rName} lights were manually adjusted. Aborting fade.")
                state["r${rNum}_isFading"] = false
                def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
                if (stateSwitch) stateSwitch.off()
                return
            }
        }
    }
    
    def durationMins = settings["r${rNum}_duration"] ?: 30
    def startMs = state["r${rNum}_startMs"] ?: new Date().time
    def maxLevel = settings["r${rNum}_maxLevel"] ?: 100
    
    def elapsedMs = new Date().time - startMs
    def elapsedMins = elapsedMs / 60000
    
    def progress = elapsedMins / durationMins
    if (progress >= 1.0) progress = 1.0
    
    def newLevel = (1 + (progress * (maxLevel - 1))).toInteger()
    state["r${rNum}_currentPct"] = newLevel
    
    def newTemp = 2500
    if (settings["r${rNum}_useVarTemp"] && settings["r${rNum}_varTempName"]) {
        def hubVar = getGlobalVar(settings["r${rNum}_varTempName"])
        newTemp = hubVar ? hubVar.value.toInteger() : 2500
    } else {
        def maxTemp = settings["r${rNum}_maxTemp"] ?: 6500
        newTemp = (2500 + (progress * (maxTemp - 2500))).toInteger()
    }
    state["r${rNum}_currentTemp"] = newTemp
    
    lights.each { light ->
        light.setLevel(newLevel)
        if (light.hasCommand("setColorTemperature")) {
            light.setColorTemperature(newTemp)
        }
    }
    
    if (progress < 1.0) {
        runIn(60, "room${rNum}FadeLoop")
    } else {
        state["r${rNum}_isFading"] = false
        logAction("SUNRISE: ${rName} complete. Reached ${newLevel}% at ${newTemp}K.")
        
        def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
        if (stateSwitch) stateSwitch.off()
        
        def notifier = settings["r${rNum}_notifier"]
        def wakeMsg = settings["r${rNum}_wakeMsg"] ?: "Time to wake up!"
        if (notifier) {
            logAction("NOTIFICATION: Sending wake alert for ${rName}.")
            notifier*.deviceNotification(wakeMsg)
        }

        def autoOff = settings["r${rNum}_autoOffMins"] ?: 0
        if (autoOff > 0) {
            runIn(autoOff * 60, "room${rNum}AutoOff")
            logAction("SUNRISE: ${rName} scheduled for Energy Savings Auto-Off in ${autoOff} minutes.")
        }
    }
}

def executeAutoOff(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    logAction("ENERGY SAVINGS: Automatically turning off ${rName} lights to conserve power.")
    settings["r${rNum}_lights"]?.each { it.off() }
}

def handleLightOff(rNum, evt) {
    if (state["r${rNum}_isFading"]) {
        def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
        logAction("MANUAL OVERRIDE: ${evt.device.displayName} in ${rName} was turned off. Aborting sunrise.")
        
        state["r${rNum}_isFading"] = false
        state["r${rNum}_isSnoozing"] = false
        state["r${rNum}_isHolding"] = false
        
        def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
        if (stateSwitch) stateSwitch.off()
        
        unschedule("room${rNum}EndHold")
        unschedule("room${rNum}FadeLoop")
        unschedule("room${rNum}ResumeFade")
        unschedule("room${rNum}AutoOff")
    }
}

def handleSnooze(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    
    if (!state["r${rNum}_isFading"]) return
    if (state["r${rNum}_isSnoozing"]) return
    
    state["r${rNum}_isSnoozing"] = true
    def snoozeMins = settings["r${rNum}_snoozeMins"] ?: 9
    
    unschedule("room${rNum}EndHold")
    unschedule("room${rNum}FadeLoop")
    unschedule("room${rNum}AutoOff")
    
    def lights = settings["r${rNum}_lights"]
    lights?.each { it.setLevel(1) }
    
    logAction("SNOOZE: ${rName} snoozed for ${snoozeMins} minutes.")
    
    runIn(snoozeMins * 60, "room${rNum}ResumeFade")
}

def resumeFade(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    
    state["r${rNum}_isSnoozing"] = false
    state["r${rNum}_justResumed"] = true
    
    def snoozeMins = settings["r${rNum}_snoozeMins"] ?: 9
    state["r${rNum}_startMs"] = state["r${rNum}_startMs"] + (snoozeMins * 60000)
    
    logAction("RESUME: ${rName} snooze ended. Resuming fade.")
    fadeLoopProcess(rNum)
}

def handleSwitchOff(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    
    logAction("GOOD NIGHT OFF: ${rName} switch turned off. Halting logic.")
    
    def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
    if (stateSwitch) stateSwitch.off()
    
    state["r${rNum}_isFading"] = false
    state["r${rNum}_isSnoozing"] = false
    state["r${rNum}_isHolding"] = false
    state["r${rNum}_currentPct"] = 0
    state["r${rNum}_currentTemp"] = 0
    
    unschedule("room${rNum}EndHold")
    unschedule("room${rNum}FadeLoop")
    unschedule("room${rNum}ResumeFade")
    unschedule("room${rNum}AutoOff")
    
    def lights = settings["r${rNum}_lights"]
    if (lights) lights.each { it.off() }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ? new ArrayList(state.actionHistory) : []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}
