/**
 * Advanced Motion Lighting Manager 2.0
 *
 */
definition(
    name: "Advanced Motion Lighting Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true
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
                      
            input "btnGlobalSweep", "button", title: "🧹 Execute Global Sweep Now"
            input "btnClearOverrides", "button", title: "🔓 Clear All Manual Overrides"
            
            def children = getChildApps()
            if (children) {
                def tableHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; }
                </style>
                <table class="dash-table">
                    <thead>
                        <tr>
                            <th>Zone Name</th>
                            <th>Lights</th>
                            <th>Status, Overrides & Last Trigger</th>
                            <th>Time Left</th>
                        </tr>
                    </thead>
                    <tbody>
                """
                
                def healthData = []
                def renderedCount = 0

                children.each { child ->
                    try {
                        def z = child.getZoneStatus()
                    
                        if (z) {
                            def lightColor = z.light.contains("ON") ? "green" : "grey"
                            def rowBg = (renderedCount % 2 == 0) ? "#ffffff" : "#f9f9f9"
                    
                            if (z.health) healthData.addAll(z.health)

                            tableHTML += "<tr style='background-color: ${rowBg};'>"
                            tableHTML += "<td class='dash-hl'>${z.name}</td>"
                            tableHTML += "<td style='color: ${lightColor}; font-weight: bold;'>${z.light}</td>"
                            
                            def triggerText = z.lastTrigger ? "<br><span style='font-size: 11px; color: #666;'>Triggered by: ${z.lastTrigger}</span>" : ""
                            tableHTML += "<td>${z.status}${triggerText}</td>"
                            tableHTML += "<td>${z.timer}</td>"
                            tableHTML += "</tr>"
 
                            renderedCount++
                        }
                    } catch (e) { log.debug "Dashboard error: ${e.message}" }
                }
        
                tableHTML += "</tbody></table>"
                paragraph tableHTML
                
                if (enableGlobalHealth && healthData) {
                    def hTable = """
                    <div style='margin-top:20px; font-weight:bold; font-size:14px;'>Sensor Health & Battery Watchdog</div>
                    <table class="dash-table" style="font-size: 12px;">
                        <thead>
                            <tr>
                                <th>Device Name</th>
                                <th>Battery</th>
                                <th>Last Activity</th>
                            </tr>
                        </thead>
                        <tbody>
                    """
                    healthData.unique{ it.name }.each { h ->
                        def bColor = (h.battery && h.battery.toInteger() < 25) ? "red" : "black"
                        hTable += "<tr><td style='text-align: left; padding-left: 15px;'>${h.name}</td><td style='color: ${bColor}; font-weight: bold;'>${h.battery ?: '--'}%</td><td>${h.lastActivity}</td></tr>"
                    }
                    hTable += "</tbody></table>"
                    paragraph hTable
                }
            } else { paragraph "<i>No lighting zones created yet.</i>" }
        }
        
        section("<b>1. Master System Control & Integrations</b>", hideable: true, hidden: true) {
            input "masterEnableSwitch", "capability.switch", title: "Master Disable Switch", required: false
            input "visitorOverrideSwitch", "capability.switch", title: "Visitor Override Switch (Forces 6500K / 100%)", required: false
            input "enableGlobalHealth", "bool", title: "Enable Global Battery & Health Watchdog?", defaultValue: false, submitOnChange: true
            input "hueBridge", "capability.refresh", title: "Global Hue Bridge (For cleaner refreshing)", required: false, description: "Select your Hue Bridge here to apply it to ALL child zones. This reduces log warnings when refreshing Hue bulbs."
        }

        section("<b>2. Emergency Safety Override</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> If any of these sensors detect an event, the entire lighting system will automatically pause, allowing your safety apps to take over and preventing this app from turning lights off.</div>"
            input "smokeSensors", "capability.smokeDetector", title: "Smoke Detectors", multiple: true, required: false
            input "coSensors", "capability.carbonMonoxideDetector", title: "Carbon Monoxide (CO) Detectors", multiple: true, required: false
        }
        
        section("<b>3. Global Hub Variables (Color Temp & Dimmer Level)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Define your global Hub Variables here. Child rules can easily subscribe to these with a single toggle instead of typing the name out every time.</div>"
            input "globalCTVar", "string", title: "Global CT Hub Variable Name (Exact text)", required: false
            input "globalLevelVar", "string", title: "Global Dimmer Level Hub Variable Name (Exact text)", required: false
        }
        
        section("<b>4. Arrival Lighting Strategy</b>", hideable: true, hidden: true) {
            input "arrivalMode", "mode", title: "Trigger Mode (e.g., Arrival/Home)", multiple: false, required: false
            input "arrivalShadesSensors", "capability.contactSensor", title: "Shades Contact Sensors", multiple: true, required: false
            input "arrivalOvercastSwitch", "capability.switch", title: "Overcast Virtual Switch", required: false
            input "arrivalTimeout", "number", title: "Shade Open Timeout (Seconds)", defaultValue: 30
            input "arrivalDuration", "number", title: "Keep Arrival Lights On Duration (Minutes)", defaultValue: 10
            input "staggerDelay", "number", title: "Stagger Delay Between Zones (ms)", defaultValue: 500
            
            paragraph "<b>Time Restrictions for Arrival Trigger</b>"
            input "restrictArrivalByTime", "bool", title: "Enable Time-Based Restrictions for Arrival?", defaultValue: false, submitOnChange: true
            if (restrictArrivalByTime) {
                input "arrivalStartTimeType", "enum", title: "Start Time", options: ["Specific Time", "Sunrise", "Sunset"], required: true, submitOnChange: true
                if (arrivalStartTimeType == "Specific Time") {
                    input "arrivalStartTime", "time", title: "Select Start Time", required: true
                } else {
                    input "arrivalStartOffset", "number", title: "Offset (Minutes)", defaultValue: 0
                }
                
                input "arrivalEndTimeType", "enum", title: "End Time", options: ["Specific Time", "Sunrise", "Sunset"], required: true, submitOnChange: true
                if (arrivalEndTimeType == "Specific Time") {
                    input "arrivalEndTime", "time", title: "Select End Time", required: true
                } else {
                    input "arrivalEndOffset", "number", title: "Offset (Minutes)", defaultValue: 0
                }
            }
        }
        
        section("<b>Lighting Rules</b>") {
            app(name: "childApps", appName: "Advanced Motion Lighting Manager 2.0 (Child)", namespace: "ShaneAllen", title: "Create New Motion Lighting Rule", multiple: true)
        }
    }
}

def installed() { initialize() }
def updated() { unschedule(); unsubscribe(); initialize() }

def initialize() {
    if (arrivalMode) subscribe(location, "mode", modeChangeHandler)
    if (arrivalShadesSensors) subscribe(arrivalShadesSensors, "contact", shadesContactHandler)
    
    if (globalCTVar) subscribe(location, "variable.${globalCTVar}", globalCTHandler)
    if (globalLevelVar) subscribe(location, "variable.${globalLevelVar}", globalLevelHandler)
    
    if (visitorOverrideSwitch) subscribe(visitorOverrideSwitch, "switch", visitorOverrideHandler)
    
    // Safety Subscriptions
    if (smokeSensors) subscribe(smokeSensors, "smoke", emergencyHandler)
    if (coSensors) subscribe(coSensors, "carbonMonoxide", emergencyHandler)
}

def visitorOverrideHandler(evt) {
    log.info "Visitor Override is now ${evt.value}. Updating active zones."
    getChildApps().each { child ->
        child.handleVisitorOverride(evt.value == "on")
    }
}

def isVisitorOverrideActive() {
    return (visitorOverrideSwitch && visitorOverrideSwitch.currentValue("switch") == "on")
}

def isSystemPaused() {
    return (state.emergencyActive == true || (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off"))
}

String getHumanReadableStatus() {
    if (state.emergencyActive) {
        return "<span style='color:red; font-size:14px;'><b>🚨 CRITICAL: FIRE / CO ISOLATION ACTIVE. ALL LIGHTING PAUSED. 🚨</b></span>"
    } else if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") {
        return "<span style='color:orange;'><b>System Disabled:</b></span> The Master Disable Switch is turned OFF."
    } else if (state.arrivalPending) {
        return "<span style='color:blue;'><b>Arrival Mode Active:</b></span> Waiting for shades to open or timeout before triggering arrival lights."
    } else if (state.arrivalActive) {
        def timeLeft = ""
        if (state.arrivalEndTime && state.arrivalEndTime > now()) {
            def diff = state.arrivalEndTime - now()
            timeLeft = "${(diff / 60000).toInteger()}m ${((diff % 60000) / 1000).toInteger()}s remaining."
        } else {
            timeLeft = "Ending soon..."
        }
        return "<span style='color:purple;'><b>Arrival Mode Executing:</b></span> Global arrival lighting is ON. ${timeLeft}"
    } else if (isVisitorOverrideActive()) {
        return "<span style='color:orange;'><b>Visitor Override Active:</b></span> Forcing 6500K and 100% across all zones."
    } else {
        def count = getChildApps()?.size() ?: 0
        return "Operating normally. System is actively monitoring <b>${count}</b> lighting zones."
    }
}

def resolveTime(type, timeVal, offset) {
    if (type == "Specific Time" && timeVal) return toDateTime(timeVal)
    
    def astro = getSunriseAndSunset()
    if (type == "Sunrise") {
        def t = astro.sunrise
        if (offset) t = new Date(t.time + (offset.toInteger() * 60000))
        return t
    }
    if (type == "Sunset") {
        def t = astro.sunset
        if (offset) t = new Date(t.time + (offset.toInteger() * 60000))
        return t
    }
    return null
}

def isTimeInWindow(sTime, eTime) {
    def n = new Date()
    if (sTime < eTime) {
        return timeOfDayIsBetween(sTime, eTime, n, location.timeZone)
    } else {
        return (n.after(sTime) || n.before(eTime))
    }
}

def isArrivalAllowedByTime() {
    if (!restrictArrivalByTime) return true
    if (!arrivalStartTimeType || !arrivalEndTimeType) return true
    
    def sTime = resolveTime(arrivalStartTimeType, arrivalStartTime, arrivalStartOffset)
    def eTime = resolveTime(arrivalEndTimeType, arrivalEndTime, arrivalEndOffset)
    
    if (sTime && eTime) {
        return isTimeInWindow(sTime, eTime)
    }
    return true
}

def emergencyHandler(evt) {
    def isEmergency = false
    
    if (smokeSensors?.any { it.currentValue("smoke") == "detected" }) isEmergency = true
    if (coSensors?.any { it.currentValue("carbonMonoxide") == "detected" }) isEmergency = true

    if (isEmergency && !state.emergencyActive) {
        log.warn "🚨 EMERGENCY DETECTED! Pausing all lighting rules."
        state.emergencyActive = true
    } else if (!isEmergency && state.emergencyActive) {
        log.info "✅ Emergency cleared. Resuming lighting rules."
        state.emergencyActive = false
    }
}

def globalCTHandler(evt) {
    if (isSystemPaused()) return
    try {
        def newCT = Math.round(evt.value.toFloat()).toInteger()
        getChildApps().each { it.dynamicCTUpdate(newCT) }
    } catch (ex) { log.error "CT Var Error: ${ex.message}" }
}

def globalLevelHandler(evt) {
    if (isSystemPaused()) return
    try {
        def newLvl = Math.round(evt.value.toFloat()).toInteger()
        getChildApps().each { it.dynamicLevelUpdate(newLvl) }
    } catch (ex) { log.error "Level Var Error: ${ex.message}" }
}

def modeChangeHandler(evt) {
    if (isSystemPaused()) return
    if (evt.value == arrivalMode) {
        if (isArrivalAllowedByTime()) {
            state.arrivalPending = true
            runIn(arrivalTimeout ?: 30, "arrivalTimeoutCheck")
        } else {
            log.info "Arrival mode triggered, but ignored due to time restrictions."
        }
    }
}

def shadesContactHandler(evt) {
    if (isSystemPaused()) return
    if (state.arrivalPending && evt.value == "open") {
        unschedule("arrivalTimeoutCheck")
        state.arrivalPending = false
        if (arrivalOvercastSwitch?.currentValue("switch") == "on") triggerArrivalLights()
    }
}

def arrivalTimeoutCheck() {
    if (isSystemPaused()) return
    
    if (state.arrivalPending) {
        state.arrivalPending = false
        
        def totalSensors = arrivalShadesSensors?.size() ?: 0
        def openCount = arrivalShadesSensors?.count { it.currentValue("contact") == "open" } ?: 0
        def averageOpen = totalSensors > 0 ? (openCount / totalSensors) : 0
        
        def isMajorityOpen = averageOpen >= 0.5
        
        if (!isMajorityOpen || arrivalOvercastSwitch?.currentValue("switch") == "on") {
            triggerArrivalLights()
        } else {
            log.info "Arrival global trigger cancelled: Average shade state is OPEN (${(averageOpen * 100).toInteger()}% open)."
        }
    }
}

def triggerArrivalLights() {
    if (isSystemPaused()) return
    def children = getChildApps()
    def dur = arrivalDuration != null ? arrivalDuration : 10
    
    state.arrivalActive = true
    state.arrivalEndTime = now() + (dur * 60000)
    
    children.each { child ->
        if (child.isArrivalEnabled()) {
            child.turnOnArrival(dur)
            pauseExecution(staggerDelay ?: 500)
        }
    }
    runIn(dur * 60, "revertArrivalLights")
}

def revertArrivalLights() { 
    if (isSystemPaused()) return
    state.arrivalActive = false
    state.arrivalEndTime = null
    getChildApps().each { if (it.isArrivalEnabled()) it.revertFromArrival() } 
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        // Do nothing, framework refreshes naturally
    } else if (btn == "btnGlobalSweep") {
        getChildApps().each { child -> 
            try {
                child.executeParentSweep((new Random().nextInt(4500) + 500).toLong())
            } catch (e) {
                log.error "Failed to sweep ${child.label}: ${e.message}"
            }
        }
    } else if (btn == "btnClearOverrides") {
        getChildApps().each { child -> 
            try {
                child.clearManualOverride()
            } catch (e) {
                log.error "Failed to clear override on ${child.label}: ${e.message}"
            }
        }
    }
}
