/**
 * Advanced Motion Lighting Manager 2.0 (Child) 
 *
 */
definition(
    name: "Advanced Motion Lighting Manager 2.0 (Child)",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    parent: "ShaneAllen:Advanced Motion Lighting Manager 2.0",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {

        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getRuleStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>Rule Status:</b> ${statusExplanation}</div>"
            
            if (isUtilityOnly) {
                paragraph "<div style='background-color:#d4edda; padding:10px; border-radius:5px; border-left:5px solid #28a745; font-size: 14px;'><b>🛠️ Utility Mode Active</b><br>Standard motion and lighting features are bypassed. Configure your Power Recovery settings in Section 8 below.</div>"
            } else if (motionSensors || primaryContactSensors) {
                def mState = isPrimaryActive() ? "<span style='color: blue; font-weight: bold;'>ACTIVE</span>" : "<span style='color: grey;'>INACTIVE</span>"
                def lState = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" }) ? "<span style='color: green; font-weight: bold;'>ON</span>" : "OFF"
                def modeInfo = location.mode
                
                def oList = getActiveOverrides()
                def oText = oList ? oList.join("<br>") : "<span style='color: green;'>None</span>"
                
                def lastTriggerStr = (enableTriggerTracking && atomicState.lastTriggerSource) ? atomicState.lastTriggerSource : "--"
                def timerText = "--"
                if (atomicState.stdTaskTime && atomicState.stdTaskTime > now()) {
                    def diff = atomicState.stdTaskTime - now()
                    timerText = "${(diff / 60000).toInteger()}m ${((diff % 60000) / 1000).toInteger()}s"
                    if (atomicState.arrivalActive) timerText += " <span style='color: purple; font-weight: bold;'>(Arrival)</span>"
                }

                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 6px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; }
                </style>
                <table class="dash-table">
                    <thead><tr><th>Trigger</th><th>Light State</th><th>Mode</th><th>Last Activated By</th><th>Time Left</th><th>Active Overrides / Blocks</th></tr></thead>
                    <tbody>
                        <tr><td class="dash-hl">${mState}</td><td><b>${lState}</b></td><td>${modeInfo}</td><td>${lastTriggerStr}</td><td><b>${timerText}</b></td><td>${oText}</td></tr>
                    </tbody>
                </table>
                """

                if (luxSensor) {
                    def curLux = luxSensor.currentValue("illuminance") ?: 0
                    dashHTML += "<div style='background-color:#d4edda; padding:10px; margin-top:10px; border-radius:5px; border-left:5px solid #28a745; font-size: 13px;'><b>Current Light Level:</b> ${curLux} lx</div>"
                }
                
                paragraph dashHTML
            } else {
                paragraph "<i>Configure sensors below to see the live dashboard.</i>"
            }
        }

        section("<b>1. Core Configuration</b>", hideable: true, hidden: false) {
            label title: "Rule Name", required: true
            
            input "isUtilityOnly", "bool", title: "<b>🛠️ Enable Utility-Only Mode?</b> (Check this to bypass standard lighting settings and ONLY use Power/RF Recovery features)", defaultValue: false, submitOnChange: true
            
            if (!isUtilityOnly) {
                input "pauseApp", "bool", title: "<b>⏸️ Pause this Lighting Rule?</b>", defaultValue: false, submitOnChange: true
                input "activeModes", "mode", title: "Active Modes (Allowed to turn ON)", multiple: true, required: false
                
                input "dynamicAdjust", "bool", title: "Adjust lights dynamically if Hub Variable / Virtual Dimmer changes while ON?", defaultValue: true, submitOnChange: true
                input "lightType", "enum", title: "What type of lights are we controlling?", options: ["Simple On/Off", "Adjustable Bulb / Dimmer", "Color / CT Bulb"], required: true, submitOnChange: true
                
                if (dynamicAdjust && lightType == "Color / CT Bulb") {
                    input "dynamicTransition", "number", title: "Dynamic Shift Fade Time (Seconds)", defaultValue: 5, description: "How slowly the bulbs will fade to the new setpoint."
                }
                
                if (lightType == "Simple On/Off") {
                    input "switches", "capability.switch", title: "Switches to control", multiple: true, required: true
                } else if (lightType == "Adjustable Bulb / Dimmer") {
                    input "dimmers", "capability.switchLevel", title: "Dimmers to control", multiple: true, required: true
                } else if (lightType == "Color / CT Bulb") {
                    input "colorBulbs", "capability.colorTemperature", title: "Color/CT Bulbs to control", multiple: true, required: true
                }
                
                if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                    input "isSmartBulbOnRelay", "bool", title: "Are these smart bulbs on a power-cutting Smart Switch?", defaultValue: false, submitOnChange: true
                    if (isSmartBulbOnRelay) {
                        input "relaySwitch", "capability.switch", title: "The Relay providing power", required: true
                        input "relayDelay", "number", title: "Boot Delay (ms)", defaultValue: 1500
                    }
                }
                
                paragraph "<hr>"
                paragraph "<b>Optional Button / Override Control</b>"
                input "optButton", "capability.pushableButton", title: "Select Button Device (Overrides Lights)", required: false, submitOnChange: true
                if (optButton) {
                    input "optButtonNum", "number", title: "Button Number", required: true, defaultValue: 1
                    input "optButtonAction", "enum", title: "Button Action", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "pushed"
                    paragraph "<div style='font-size: 12px; color: #666;'><i>Note: Double-Tapping this button will automatically override the lights to 100% brightness and 6500K for the duration of the manual timer. (Requires a toggle action other than doubleTapped).</i></div>"
                    input "optButtonModes", "mode", title: "Allowed Modes for Button", multiple: true, required: false
                    input "toggleSyncLights", "capability.switch", title: "Group Toggle Sync (Check these lights before toggling)", multiple: true, required: false, description: "Select the other lights controlled by this button to prevent flip-flopping."
                }
            }
        }
        
        if (!isUtilityOnly) {
            section("<b>2. Triggers & Intelligence</b>", hideable: true, hidden: true) {
                input "enableVacancyMode", "bool", title: "<b>Strict Vacancy Mode (Manual-On / Auto-Off)?</b>", defaultValue: false, description: "If enabled, motion will NEVER turn lights on, but will hold them on and turn them off when empty."
                
                input "motionSensors", "capability.motionSensor", title: "Primary Motion Sensors", multiple: true, required: false, submitOnChange: true
                input "primaryContactSensors", "capability.contactSensor", title: "Primary Contact Sensors (e.g., Doors)", multiple: true, required: false, submitOnChange: true
                
                if (motionSensors && motionSensors.size() > 1) {
                    input "requireAllMotion", "bool", title: "Require ALL selected sensors to trigger to turn ON?", defaultValue: false, submitOnChange: true
                    if (requireAllMotion) {
                        input "requireAllMotionWindow", "number", title: "Time Window (Seconds)", defaultValue: 10, description: "All sensors must detect motion within this time frame to turn lights on."
                    }
                }
                
                input "enableTriggerTracking", "bool", title: "Enable 'Last Triggered By' Intelligence?", defaultValue: false
                input "motionDebounceSeconds", "number", title: "Debounce (Seconds of continuous motion required)", defaultValue: 0
                input "luxSensor", "capability.illuminanceMeasurement", title: "Illuminance (Lux) Sensor", required: false
                input "luxThreshold", "number", title: "Lux Threshold (Only ON if below this)", required: false
                input "enableActiveLuxPolling", "bool", title: "Enable Active Lux Polling (Sunset Fade-In)?", defaultValue: false, description: "Continually checks if occupied and turns on lights once the sun goes down."
                input "enableActiveLuxShutoff", "bool", title: "Enable Active Lux Shutoff?", defaultValue: false, description: "Forcefully shuts lights OFF if natural sunlight enters the room while occupied."
            }
            
            section("<b>3. Turn ON & Transitions</b>", hideable: true, hidden: true) {
                if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                    input "useGlobalLevel", "bool", title: "Use Parent's Global Dimmer Variable?", defaultValue: false, submitOnChange: true
                    if (!useGlobalLevel) {
                        input "useLevelDimmer", "bool", title: "Follow a Virtual Dimmer for Level?", defaultValue: false, submitOnChange: true
                        if (useLevelDimmer) {
                            input "levelDimmer", "capability.switchLevel", title: "Select Virtual Dimmer to follow", required: true
                        } else {
                            input "useLevelVar", "bool", title: "Use a Custom Hub Variable for Level?", defaultValue: false, submitOnChange: true
                            if (useLevelVar) {
                                input "levelVarName", "string", title: "Hub Variable Name (Exact text)"
                            } else {
                                input "defaultLevel", "number", title: "Default Dim Level (%)", defaultValue: 100
                            }
                        }
                    }
                    input "enableDaylightHarvesting", "bool", title: "Enable Daylight Harvesting (Proportional Dimming)?", defaultValue: false, description: "Automatically dims lights proportionally based on the amount of natural sunlight available."
                }
                
                if (lightType == "Color / CT Bulb") {
                    input "useGlobalCT", "bool", title: "Use Parent's Global CT Variable?", defaultValue: false, submitOnChange: true
                    if (!useGlobalCT) {
                        input "useCTVar", "bool", title: "Use a Custom Hub Variable for Color Temp?", defaultValue: false, submitOnChange: true
                        if (useCTVar) {
                            input "ctVarName", "string", title: "Hub Variable Name (Exact text)"
                        } else {
                            input "defaultCT", "number", title: "Default Color Temp (K)", defaultValue: 2700
                        }
                    }
                }
                input "defaultDelay", "number", title: "Standard Turn Off Delay (Minutes)", defaultValue: 5, required: true
            }
            
            section("<b>4. Turn OFF Behaviors</b>", hideable: true, hidden: true) {
                input "blindOffSwitches", "capability.switch", title: "Unreliable Switches (Always send OFF command regardless of known state)", multiple: true, required: false
                input "enableWalkThrough", "bool", title: "Enable Walk-Through Micro-Timeout?", defaultValue: false, description: "If motion lasts less than 15 seconds, the lights will shut off in 30 seconds instead of the standard delay."
                input "gracePeriod", "number", title: "Grace Period after Manual Off (Seconds)", defaultValue: 15
                input "manualTimeoutMinutes", "number", title: "Manual Override Motion Shutoff Timer (Minutes)", defaultValue: 30, description: "If a light is manually turned on, wait this long after motion stops before turning off."

                input "dimBeforeOff", "bool", title: "Dim to 1% before turning off?", defaultValue: true, description: "Disable this for bulbs that don't need a forced soft-fade."
                input "turnOffOnModes", "mode", title: "Force OFF when Hub enters these Modes", multiple: true
                
                if (isSmartBulbOnRelay) {
                    input "turnOffRelay", "bool", title: "Turn off Relay Power when lights turn off?", defaultValue: true
                }
                
                paragraph "<b>Absolute Force-Off (Sweep)</b>"
                input "enableForceOff", "bool", title: "Enable Absolute Force-Off?", defaultValue: false, submitOnChange: true
                if (enableForceOff) input "forceOffMinutes", "number", title: "Sweep Delay (Minutes)", defaultValue: 60
            }

            section("<b>5. Advanced Restrictions</b>", hideable: true, hidden: true) {
                input "disableOnSwitches", "capability.switch", title: "Switches to Disable Turning ON", multiple: true, required: false
                input "disableOffSwitches", "capability.switch", title: "Switches to Disable Turning OFF", multiple: true, required: false
                input "goodNightSwitch", "capability.switch", title: "Nap Time / Hard-Lock Switch", required: false
                
                paragraph "<b>Operational Time Window</b>"
                input "restrictByTime", "bool", title: "Enable Time-Based Gate?", defaultValue: false, submitOnChange: true
                if (restrictByTime) {
                    input "timeLogic", "enum", title: "Window Logic", options: ["Only run DURING this window", "Block execution DURING this window"], defaultValue: "Only run DURING this window", submitOnChange: true
                    
                    input "startTimeType", "enum", title: "Start Time", options: ["Specific Time", "Sunrise", "Sunset"], required: true, submitOnChange: true
                    if (startTimeType == "Specific Time") {
                        input "startTime", "time", title: "Select Start Time", required: true
                    } else {
                        input "startOffset", "number", title: "Offset (Minutes)", defaultValue: 0
                    }
                    
                    input "endTimeType", "enum", title: "End Time", options: ["Specific Time", "Sunrise", "Sunset"], required: true, submitOnChange: true
                    if (endTimeType == "Specific Time") {
                        input "endTime", "time", title: "Select End Time", required: true
                    } else {
                        input "endOffset", "number", title: "Offset (Minutes)", defaultValue: 0
                    }
                }
                
                paragraph "<b>Window & Shade Logic</b>"
                input "contactSensors", "capability.contactSensor", title: "Window Contacts (Do NOT turn on if OPEN)", multiple: true, required: false
                input "shadeSensors", "capability.contactSensor", title: "Shade Contacts (Do NOT turn on if OPEN)", multiple: true, required: false
                input "contactDebounceSeconds", "number", title: "Window/Shade Debounce (Seconds)", defaultValue: 120, description: "Wait this long of continuous state before reacting to window/shade changes. Prevents flickering from wind/fans.", submitOnChange: true
                input "turnOffOnContactOpen", "bool", title: "Force Lights OFF if Window/Shade Contacts OPEN?", defaultValue: false, description: "If enabled, opening a window or shade while lights are ON (and Overcast/Lux is OFF) will instantly turn them off."
                input "overcastSwitch", "capability.switch", title: "Virtual Overcast Switch (Ignores open windows/shades)", required: false
                
                input "useLuxContactOverride", "bool", title: "Enable Lux Override for Open Windows/Shades?", defaultValue: false, submitOnChange: true
                if (useLuxContactOverride) {
                    input "luxContactVar", "string", title: "Hub Variable for Lux Threshold (Optional)", required: false
                    input "luxContactThreshold", "number", title: "Static Lux Threshold", required: false
                }
                
                paragraph "<b>Keep-Alive / Overrides (Keeps lights ON, won't trigger ON)</b>"
                input "keepAliveMotionSensors", "capability.motionSensor", title: "Secondary Motion Sensors", multiple: true, required: false
                input "keepAliveVibrationSensors", "capability.accelerationSensor", title: "Keep-Alive Vibration Sensors (Chair/Bed)", multiple: true, required: false
                input "keepAliveSwitches", "capability.switch", title: "Keep-Alive Switches (e.g., TV is ON)", multiple: true, required: false
                
                paragraph "<b>Override Exceptions</b>"
                input "ignoreOverrideSwitches", "capability.switch", title: "Ignore Manual Overrides when these are ON", multiple: true, required: false, description: "e.g., A virtual switch turned on by a Shower Monitor. Prevents manual holds."
            }

            section("<b>6. System Health & Maintenance</b>", hideable: true, hidden: true) {
                input "logEnable", "bool", title: "Enable Debug Logging (Auto-disables after 30 min)", defaultValue: false
                input "enableHealthWatchdog", "bool", title: "Report Battery & Health to Parent?", defaultValue: false
                
                input "enableRoutineRefresh", "bool", title: "Enable Routine Device Refresh?", defaultValue: false, submitOnChange: true, description: "Periodically polls devices to force state updates."
                if (enableRoutineRefresh) {
                    input "refreshInterval", "enum", title: "Refresh Interval (Minutes)", options: ["5", "10", "15", "30", "60"], defaultValue: "15", required: true
                }
            }
            
            section("<b>7. Arrival Lighting Strategy</b>", hideable: true, hidden: true) {
                input "enableArrivalLighting", "bool", title: "Enable Full Arrival Lighting?", defaultValue: false, submitOnChange: true, description: "If enabled, the parent app will force these lights ON when the Arrival scenario is triggered."
                if (enableArrivalLighting) {
                    input "arrivalDayDelay", "number", title: "Daytime Arrival Delay (Seconds)", defaultValue: 90, description: "Wait this long before turning on arrival lights during the day."
                    if (lightType == "Color / CT Bulb") {
                        input "arrivalColorOverride", "bool", title: "Override color to 6500K during Arrival?", defaultValue: true, submitOnChange: true, description: "Lights will revert to their assigned color temp when the arrival timer ends."
                    
                        if (arrivalColorOverride) {
                            input "arrivalTransitionTime", "number", title: "Revert Transition Time (Seconds)", defaultValue: 3
                        }
                    }
                }
            }
        }

        section("<b>8. Power Outage & Recovery</b>", hideable: true, hidden: (isUtilityOnly ? false : true)) {
            if (!isUtilityOnly) {
                input "enablePowerRecovery", "bool", title: "Enable Power Outage Recovery / Soft Fade?", defaultValue: false, submitOnChange: true, description: "Automatically detects if lights restore from an outage and slowly puts them back into the correct ON or OFF state."
                if (enablePowerRecovery) {
                    input "recoveryTransition", "number", title: "Recovery Soft-Fade Time (Seconds)", defaultValue: 30
                    input "enablePowerBlipDetection", "bool", title: "Detect Bulb-Only Power Flashes?", defaultValue: false, submitOnChange: true
                    if (enablePowerBlipDetection) {
                        input "blipInactivityThreshold", "number", title: "Required Inactivity Threshold (Minutes)", defaultValue: 60
                    }
                }
            }
            
            paragraph "<b>RF / Non-Reporting Device Recovery</b>"
            input "enableRFRecovery", "bool", title: "Enable Blind OFF for RF/Non-Reporting Lights?", defaultValue: false, submitOnChange: true, description: "For RF fans/lights (like Bond) that turn on after an outage but don't report state."
            if (enableRFRecovery) {
                input "rfRecoverySwitches", "capability.switch", title: "Select RF Switches/Lights", multiple: true, required: true
                input "rfRecoveryDelay", "number", title: "Reconnection Wait Time (Minutes)", defaultValue: 5
            }
        }
    }
}

def installed() { initialize() }
def updated() { 
    unsubscribe()
    unschedule()
    
    if (isPaused()) {
        log.warn "Rule is currently PAUSED. Schedules cleared."
    }
    
    if (logEnable) {
        log.debug "Debug logging enabled for 30 minutes."
        runIn(1800, "logsOff")
    }
    initialize() 
}

def initialize() {
    atomicState.historyLog = atomicState.historyLog ?: []
    atomicState.appTurnedOn = atomicState.appTurnedOn ?: false
    atomicState.manuallyTurnedOn = atomicState.manuallyTurnedOn ?: false
    atomicState.maxOverrideActive = atomicState.maxOverrideActive ?: false
    atomicState.arrivalActive = atomicState.arrivalActive ?: false
    atomicState.offRetryCount = 0
    atomicState.lastDynamicUpdate = atomicState.lastDynamicUpdate ?: 0
    atomicState.lastMotionEvents = [:]
    if (isPrimaryActive()) atomicState.lastPrimaryActiveTime = now()
    
    subscribe(location, "systemStart", bootHandler)

    if (isUtilityOnly) {
        debugLog("Initialized in Utility-Only Mode. Subscribed to system boot events only.")
        return 
    }

    if (motionSensors) subscribe(motionSensors, "motion", triggerHandler)
    if (primaryContactSensors) subscribe(primaryContactSensors, "contact", primaryContactHandler)
    
    if (keepAliveMotionSensors) subscribe(keepAliveMotionSensors, "motion", keepAliveHandler)
    if (keepAliveVibrationSensors) subscribe(keepAliveVibrationSensors, "acceleration", keepAliveHandler)
    if (keepAliveSwitches) subscribe(keepAliveSwitches, "switch", keepAliveHandler)
    
    if (disableOnSwitches) subscribe(disableOnSwitches, "switch", restrictionHandler)
    if (disableOffSwitches) subscribe(disableOffSwitches, "switch", restrictionHandler)
    if (contactSensors) subscribe(contactSensors, "contact", restrictionHandler)
    if (shadeSensors) subscribe(shadeSensors, "contact", restrictionHandler)
    if (overcastSwitch) subscribe(overcastSwitch, "switch", restrictionHandler)
    if (goodNightSwitch) subscribe(goodNightSwitch, "switch", restrictionHandler)
    
    if (optButton && optButtonAction) {
        subscribe(optButton, optButtonAction, buttonActionHandler)
        if (optButtonAction != "doubleTapped") {
            subscribe(optButton, "doubleTapped", buttonDoubleTapOverrideHandler)
        }
    }
    
    subscribe(location, "mode", modeChangeHandler)
    
    if (switches) { subscribe(switches, "switch.off", physicalOffHandler); subscribe(switches, "switch.on", physicalOnHandler) }
    if (dimmers) { subscribe(dimmers, "switch.off", physicalOffHandler); subscribe(dimmers, "switch.on", physicalOnHandler) }
    if (colorBulbs) { subscribe(colorBulbs, "switch.off", physicalOffHandler); subscribe(colorBulbs, "switch.on", physicalOnHandler) }
    
    if (dynamicAdjust) {
        def dimmersToSub = []
        def varsToSub = []
        
        if (useLevelDimmer && levelDimmer) dimmersToSub << levelDimmer
        if (useLevelVar && levelVarName) varsToSub << levelVarName
        if (useGlobalLevel && parent?.globalLevelVar) varsToSub << parent.globalLevelVar
        if (useCTVar && ctVarName) varsToSub << parent.globalCTVar
        if (useGlobalCT && parent?.globalCTVar) varsToSub << parent.globalCTVar
        
        dimmersToSub.findAll { it }.unique { it.id }.each { dev ->
            subscribe(dev, "level", dynamicAdjustmentHandler)
        }
        varsToSub.findAll { it }.unique().each { vName ->
            subscribe(location, "variable:${vName}", dynamicAdjustmentHandler)
        }
    }
    
    schedule("0 0/15 * * * ?", "watchdogAudit") 
   
    if (enableRoutineRefresh && refreshInterval) {
        def staggerSec = Math.abs(app.id.hashCode() % 60)
        if (refreshInterval == "60") {
            schedule("${staggerSec} 0 * * * ?", "routineRefreshHandler")
        } else {
            schedule("${staggerSec} 0/${refreshInterval} * * * ?", "routineRefreshHandler")
        }
    }
    
    runIn(10, "bootSync")
}

def isRuleBlocked() {
    return (disableOnSwitches?.any { it.currentValue("switch") == "on" }) || 
           (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on")
}

def getActiveOverrides() {
    def overrides = []
    if (isUtilityOnly) return overrides
    
    if (parent?.isVisitorOverrideActive()) {
        overrides << "<span style='color: orange;'><b>Visitor Override Active (100% / 6500K)</b></span>"
    }
    
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) {
        def blocking = disableOffSwitches.findAll { it.currentValue("switch") == "on" }*.displayName.join(", ")
        overrides << "<span style='color: darkred;'><b>Keeping ON:</b> ${blocking}</span>"
    }
    if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") overrides << "<span style='color: darkblue;'><b>Keeping ON: Nap Lock Active</b></span>"
    if (isKeepAliveActive()) overrides << "<span style='color: #00aadd;'><b>Keeping ON: Keep-Alive Active</b></span>"
    
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) {
        def blocking = disableOnSwitches.findAll { it.currentValue("switch") == "on" }*.displayName.join(", ")
        overrides << "<span style='color: red;'><b>Blocked ON:</b> ${blocking}</span>"
    }
    if (activeModes && !activeModes.contains(location.mode)) overrides << "<span style='color: orange;'><b>Blocked ON: Mode Restriction</b></span>"
    if (enableVacancyMode) overrides << "<span style='color: orange;'><b>Blocked ON: Vacancy Mode</b></span>"
    
    if (!atomicState.appTurnedOn && requireAllMotion && motionSensors?.size() > 1) {
        def currentMap = atomicState.lastMotionEvents ?: [:]
        def windowTime = requireAllMotionWindow != null ? requireAllMotionWindow : 10
        def windowMs = windowTime * 1000
        def cutoffTime = now() - windowMs
        def activeCount = 0
        motionSensors.each { sensor ->
            if (currentMap[sensor.id] && currentMap[sensor.id] >= cutoffTime) activeCount++
        }
        if (activeCount > 0 && activeCount < motionSensors.size()) {
            overrides << "<span style='color: #17a2b8;'><b>Pending Multi-Sensor: (${activeCount}/${motionSensors.size()})</b></span>"
        }
    }
    
    def timeBlocked = false
    if (restrictByTime && startTimeType && endTimeType) {
        def sTime = resolveTime(startTimeType, startTime, startOffset)
        def eTime = resolveTime(endTimeType, endTime, endOffset)
    
        if (sTime && eTime) {
            def isInside = isTimeInWindow(sTime, eTime)
            timeBlocked = (timeLogic == "Block execution DURING this window") ? isInside : !isInside
        }
    }
    if (timeBlocked) overrides << "<span style='color: orange;'><b>Blocked ON: Time Window</b></span>"
    
    def totalContacts = (contactSensors?.size() ?: 0) + (shadeSensors?.size() ?: 0)
    def openSensors = (contactSensors?.findAll { it.currentValue("contact") == "open" } ?: []) + 
                      (shadeSensors?.findAll { it.currentValue("contact") == "open" } ?: [])
    def averageOpen = totalContacts > 0 ? (openSensors.size() / totalContacts) : 0
    
    if (averageOpen >= 0.5) {
        def luxOverrideActive = false
        if (useLuxContactOverride && luxSensor) {
            def curLux = luxSensor.currentValue("illuminance") ?: 0
            def targetLux = luxContactThreshold ?: 0
            if (luxContactVar) {
                def hVar = getGlobalVar(luxContactVar)
                if (hVar != null) targetLux = hVar.value.toInteger()
            }
            if (curLux < targetLux) luxOverrideActive = true
        }
        if ((overcastSwitch && overcastSwitch.currentValue("switch") == "on") || luxOverrideActive) {
            overrides << "<span style='color: purple;'><b>Window/Shade Bypass Active</b></span>"
        } else {
            def sensorNames = openSensors*.displayName.join(", ")
            overrides << "<span style='color: orange;'><b>Blocked ON:</b> Majority Open (${sensorNames})</span>"
        }
    }
    
    if (atomicState.maxOverrideActive) overrides << "<span style='color: #0055aa;'><b>Max Manual Override (Double Tap)</b></span>"
    else if (atomicState.manuallyTurnedOn) overrides << "<span style='color: #0055aa;'><b>Manual ON Override</b></span>"
    
    if (atomicState.gracePeriodEnd && now() < atomicState.gracePeriodEnd) overrides << "<span style='color: #856404;'><b>Manual OFF (Grace Period)</b></span>"
    if (atomicState.arrivalActive) overrides << "<span style='color: #800080;'><b>Arrival Override Active</b></span>"
    
    return overrides
}

String getRuleStatus() {
    if (isUtilityOnly) {
        return "<span style='color:green;'><b>Utility Mode:</b></span> Standing by for hub reboots / power events."
    } else if (parent?.isSystemPaused()) {
        return "<span style='color:red;'><b>PAUSED:</b></span> Global System Pause is active."
    } else if (pauseApp) {
        return "<span style='color:red;'><b>PAUSED:</b></span> Rule is manually disabled."
    } else if (atomicState.arrivalActive) {
        return "<span style='color:purple;'><b>Arrival Mode:</b></span> Specialized arrival lighting logic overrides are active."
    } else if (atomicState.maxOverrideActive) {
        return "<span style='color:blue;'><b>Max Override:</b></span> Lights forced to 100% / 6500K via Double Tap."
    } else if (atomicState.manuallyTurnedOn) {
        return "<span style='color:blue;'><b>Manual Override:</b></span> Lights triggered manually. Standard timeouts apply."
    } else {
        def anyOn = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" })
        if (anyOn) {
            if (isPrimaryActive() || isKeepAliveActive()) return "Zone occupied. Maintaining active status."
            else return "Zone unoccupied. Evaluating timeout schedules."
        }
        return "Operating normally. Monitoring zone for activity."
    }
}

def handleVisitorOverride(isActive) {
    if (isPaused() || isUtilityOnly) return
    
    def anyOn = (switches?.any { it.currentValue("switch") == "on" } || 
                 dimmers?.any { it.currentValue("switch") == "on" } || 
                 colorBulbs?.any { it.currentValue("switch") == "on" })
                 
    if (anyOn) {
        debugLog("Visitor Override toggled to ${isActive}. Adjusting active lights.")
        applyLightingSettings(3) 
    }
}

def debugLog(msg) {
    if (logEnable) log.debug msg
}

def logsOff() {
    log.warn "Debug logging automatically disabled."
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

def isPaused() {
    return (pauseApp == true || parent?.isSystemPaused() == true)
}

def watchdogAudit() {
    if (isPaused() || isUtilityOnly) return
    
    def anyOn = (switches?.any { it.currentValue("switch") == "on" } || 
                 dimmers?.any { it.currentValue("switch") == "on" } || 
                 colorBulbs?.any { it.currentValue("switch") == "on" })
        
    def isOccupied = isPrimaryActive() || isKeepAliveActive()
    
    if (!anyOn && atomicState.appTurnedOn) {
        debugLog("Watchdog: State mismatch. Lights are off but app state was ON. Resetting.")
        atomicState.appTurnedOn = false
        atomicState.manuallyTurnedOn = false
        atomicState.maxOverrideActive = false
        atomicState.arrivalActive = false
        cancelAllTurnOffTimers()
    }
  
    if (anyOn && !isOccupied && !atomicState.arrivalActive && atomicState.appTurnedOn) {
        if (!atomicState.stdTaskTime || now() > atomicState.stdTaskTime) {
            debugLog("Watchdog: Orphaned timer detected. Restarting turn off process.")
            startTurnOffTimer()
        }
    }
}

def shouldSkipRefresh(dev) {
    if (!parent?.hueBridge) return false
    def type = dev.typeName?.toLowerCase() ?: ""
    def name = dev.name?.toLowerCase() ?: ""
    return type.contains("hue") || name.contains("hue")
}

def routineRefreshHandler() {
    if (isPaused() || isUtilityOnly) return
    debugLog("Executing staggered routine device refresh...")
    
    def devicesToRefresh = []
    if (switches) devicesToRefresh += switches.findAll { it.hasCommand("refresh") && !shouldSkipRefresh(it) }
    if (dimmers) devicesToRefresh += dimmers.findAll { it.hasCommand("refresh") && !shouldSkipRefresh(it) }
    if (colorBulbs) devicesToRefresh += colorBulbs.findAll { it.hasCommand("refresh") && !shouldSkipRefresh(it) }
    
    devicesToRefresh = devicesToRefresh.findAll { it }.unique { it.id }
    if (devicesToRefresh.size() == 0) return
  
    def staggerMs = 0
    def delayIncrement = 2500 
    
    devicesToRefresh.each { dev ->
        if (staggerMs == 0) {
            try { dev.refresh() } catch(e){}
        } else {
            runInMillis(staggerMs, "executeDelayedRefresh", [data: [devId: dev.id]])
        }
        staggerMs += delayIncrement
    }
}

def executeDelayedRefresh(data) {
    def devId = data?.devId
    if (!devId) return
    
    def targetDev = null
    if (switches) targetDev = switches.find { it.id == devId }
    if (!targetDev && dimmers) targetDev = dimmers.find { it.id == devId }
    if (!targetDev && colorBulbs) targetDev = colorBulbs.find { it.id == devId }
    
    if (targetDev && targetDev.hasCommand("refresh")) {
        debugLog("Staggered refresh executing for: ${targetDev.displayName}")
        try { targetDev.refresh() } catch(e){}
    }
}

def dynamicAdjustmentHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    if (atomicState.arrivalActive) return
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    
    if (atomicState.appTurnedOn) {
        def nowMs = now()
        def lastUpdate = atomicState.lastDynamicUpdate ?: 0
        
        if (nowMs - lastUpdate >= 300000) {
            atomicState.lastDynamicUpdate = nowMs
            def randomStagger = new Random().nextInt(2000) + 100
            runInMillis(randomStagger, "applyDynamicSettings")
        }
    }
}

def applyDynamicSettings() {
    if (isPaused() || isUtilityOnly || atomicState.maxOverrideActive) return
    def trans = dynamicTransition != null ? dynamicTransition : 5
    applyLightingSettings(trans)
}

def buttonDoubleTapOverrideHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    if (optButtonModes && !optButtonModes.contains(location.mode)) return
    
    debugLog("Received doubleTapped event for button ${evt.value}. Target button is ${optButtonNum}")
    
    if (evt.value == optButtonNum?.toString()) {
        debugLog("Double Tap Override Triggered! Forcing to 100% / 6500K.")
        atomicState.manuallyTurnedOn = true
        atomicState.appTurnedOn = true
        atomicState.maxOverrideActive = true
        atomicState.arrivalActive = false
        atomicState.stdTaskTime = null
        
        cancelAllTurnOffTimers()
        startTurnOffTimer()
        manageForceOffTimer() 
        
        if (lightType == "Color / CT Bulb") {
            colorBulbs?.each { 
                try { 
                    it.setColorTemperature(6500, 100) 
                } catch(e) { 
                    log.error "Override CT Exception: ${e}" 
                } 
            }
        } else if (lightType == "Adjustable Bulb / Dimmer") {
            dimmers?.each { 
                try { 
                    it.setLevel(100) 
                } catch(e) { 
                    log.error "Override Dim Exception: ${e}" 
                } 
            }
        } else if (lightType == "Simple On/Off") {
            switches?.each { 
                try { it.on() } catch(e) { log.error "Override Switch Exception: ${e}" } 
            }
        }
    }
}

def buttonActionHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    if (optButtonModes && !optButtonModes.contains(location.mode)) return
    if (evt.value == optButtonNum?.toString()) {
        def anyOn = false
        if (lightType == "Simple On/Off") anyOn = switches?.any { it.currentValue("switch") == "on" }
        else if (lightType == "Adjustable Bulb / Dimmer") anyOn = dimmers?.any { it.currentValue("switch") == "on" }
        else if (lightType == "Color / CT Bulb") anyOn = colorBulbs?.any { it.currentValue("switch") == "on" }

        if (toggleSyncLights?.any { it.currentValue("switch") == "on" }) {
            anyOn = true
        }

        if (anyOn) {
            def gp = gracePeriod != null ? gracePeriod : 15
            atomicState.gracePeriodEnd = now() + (gp * 1000)
            
            atomicState.appTurnedOn = false
            atomicState.manuallyTurnedOn = false
            atomicState.maxOverrideActive = false
            atomicState.arrivalActive = false
            atomicState.stdTaskTime = null
        
            cancelAllTurnOffTimers()
            sendOffCommands()
        } else {
            atomicState.manuallyTurnedOn = true
            atomicState.appTurnedOn = true
            atomicState.maxOverrideActive = false
    
            startTurnOffTimer()
            manageForceOffTimer() 
            
            if (lightType == "Color / CT Bulb" || lightType == "Adjustable Bulb / Dimmer") {
                applyLightingSettings() 
            } else if (lightType == "Simple On/Off") {
                switches?.each { 
                    try { it.on() } catch(e) { log.error "Button trigger exception: ${e}" } 
                }
            }
        }
    }
}

def isPrimaryActive() {
    if (isUtilityOnly) return false
    def mActive = motionSensors?.any { it.currentValue("motion") == "active" }
    def cActive = primaryContactSensors?.any { it.currentValue("contact") == "open" }
    return (mActive || cActive)
}

def isKeepAliveActive() {
    if (isUtilityOnly) return false
    def mActive = keepAliveMotionSensors?.any { it.currentValue("motion") == "active" }
    def vActive = keepAliveVibrationSensors?.any { it.currentValue("acceleration") == "active" }
    def sActive = keepAliveSwitches?.any { it.currentValue("switch") == "on" }
    return (mActive || vActive || sActive)
}

def bootHandler(evt) { runIn(30, "bootSync", [overwrite: true]) }

def bootSync() {
    if (isPaused()) return

    if (enableRFRecovery && rfRecoverySwitches) {
        def rfDelay = rfRecoveryDelay != null ? rfRecoveryDelay : 5
        debugLog("RF Recovery: Hub rebooted. Scheduling blind OFF command for RF devices in ${rfDelay} minutes.")
        runIn(rfDelay * 60, "executeRFRecoveryOff")
    }
    
    if (isUtilityOnly) return 

    def anyOn = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" })
    
    def isOccupied = isPrimaryActive() || isKeepAliveActive()
    def isGoodNight = goodNightSwitch && goodNightSwitch.currentValue("switch") == "on"

    if (anyOn) {
        if (isGoodNight || !isOccupied) {
            if (enablePowerRecovery) {
                debugLog("Power Recovery: Zone should be OFF. Executing soft-fade off.")
                def recTrans = recoveryTransition != null ? recoveryTransition : 30
                softFadeOff(recTrans)
            } else {
                def randomStagger = new Random().nextInt(5000) + 500
                runInMillis(randomStagger, "sendOffCommands")
            }
        } else {
            if (enablePowerRecovery) {
                debugLog("Power Recovery: Zone should be ON. Slowly restoring levels.")
                def recTrans = recoveryTransition != null ? recoveryTransition : 30
                applyLightingSettings(recTrans)
            } else {
                evaluateTurnOn()
            }
        }
    } else {
        // STRICT KEEP-ALIVE FIX: Only allow primary triggers to initiate turn-on during a reboot.
        if (isPrimaryActive() && !isGoodNight) {
            evaluateTurnOn()
        }
    }
}

def executeRFRecoveryOff() {
    if (isPaused()) return
    
    def isOccupied = isPrimaryActive() || isKeepAliveActive()
    def isGoodNight = !isUtilityOnly && goodNightSwitch && goodNightSwitch.currentValue("switch") == "on"
    
    if (!isOccupied || isGoodNight) {
        debugLog("RF Recovery: Executing delayed blind OFF command for RF/Non-Reporting devices.")
        rfRecoverySwitches?.each { 
            try { 
                it.off() 
            } catch(e) { 
                log.error "Exception sending RF Recovery OFF to ${it.displayName}: ${e}" 
            } 
        }
    } else {
        debugLog("RF Recovery: Skipped blind OFF command because the zone is currently occupied.")
    }
}

def softFadeOff(transitionSec) {
    if (isUtilityOnly) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) {
        debugLog("Soft-Fade aborted: Block-Off switch is active.")
        return
    }
    
    atomicState.appTurnedOn = false
    atomicState.manuallyTurnedOn = false
    atomicState.maxOverrideActive = false
    atomicState.arrivalActive = false
    atomicState.stdTaskTime = null
    cancelAllTurnOffTimers()

    def t = transitionSec != null ? transitionSec : 15
    def needsDelay = false

    dimmers?.each {
        if (it.currentValue("switch") == "on") {
            try { it.setLevel(0, t) } catch(e){}
            needsDelay = true
        }
    }
    colorBulbs?.each {
        if (it.currentValue("switch") == "on") {
            try { it.setLevel(0, t) } catch(e){}
            needsDelay = true
        }
    }

    if (needsDelay) {
        runIn(t + 2, "executeFinalOffCommands")
    } else {
        executeFinalOffCommands()
    }
}

def primaryContactHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    
    if (evt.value == "open") {
        atomicState.lastPrimaryActiveTime = now()
        atomicState.motionStartTime = now() // Walk-through logic track
        if (atomicState.gracePeriodEnd && now() < atomicState.gracePeriodEnd) return
        
        if (enableTriggerTracking) {
            atomicState.lastTriggerSource = evt.displayName
        }
        
        def isBlocked = isRuleBlocked()
        
        if (atomicState.manuallyTurnedOn) {
            startTurnOffTimer() 
        } else if (!isBlocked) {
            cancelAllTurnOffTimers()
            evaluateTurnOn()
        } else {
            debugLog("Contact opened, but rule is Blocked ON. Ignoring to allow existing countdowns to finish.")
        }
    } else {
        runIn(1, "evaluatePrimaryOff")
    }
}

def triggerHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    def isActiveEvent = (evt.value == "active")
    
    if (isActiveEvent) {
        atomicState.lastPrimaryActiveTime = now()
        atomicState.motionStartTime = now() // Walk-through logic track
        if (atomicState.gracePeriodEnd && now() < atomicState.gracePeriodEnd) return
        
        if (enableTriggerTracking) {
            atomicState.lastTriggerSource = evt.displayName
        }
        
        if (requireAllMotion && motionSensors && motionSensors.size() > 1) {
            def currentMap = atomicState.lastMotionEvents ?: [:]
            currentMap[evt.device.id] = now()
            atomicState.lastMotionEvents = currentMap

            if (!atomicState.appTurnedOn && !atomicState.manuallyTurnedOn && !atomicState.arrivalActive) {
                def windowTime = requireAllMotionWindow != null ? requireAllMotionWindow : 10
                def windowMs = windowTime * 1000
                def cutoffTime = now() - windowMs
                def allTriggered = true

                motionSensors.each { sensor ->
                    def lastTime = currentMap[sensor.id]
                    if (!lastTime || lastTime < cutoffTime) {
                        allTriggered = false
                    }
                }

                if (!allTriggered) {
                    debugLog("Multi-Sensor Check: Aborting ON. Waiting for other sensors.")
                    return 
                }
            }
        }
        
        def isBlocked = isRuleBlocked()
        
        if (atomicState.manuallyTurnedOn) {
            startTurnOffTimer() 
        } else if (!isBlocked) {
            cancelAllTurnOffTimers()
            if (motionDebounceSeconds && !atomicState.appTurnedOn) runIn(motionDebounceSeconds, "evaluateTurnOn")
            else evaluateTurnOn()
        } else {
            debugLog("Motion detected, but rule is Blocked ON. Ignoring motion to allow existing countdowns to finish.")
        }
    } else {
        runIn(1, "evaluatePrimaryOff")
    }
}

def evaluatePrimaryOff() {
    if (isPaused() || isUtilityOnly) return
    if (!isPrimaryActive() && !isKeepAliveActive()) {
        startTurnOffTimer()
    }
}

def keepAliveHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    def isActiveEvent = (evt.value == "active" || evt.value == "on")
    
    if (isActiveEvent) {
        atomicState.lastPrimaryActiveTime = now()
        
        if (atomicState.manuallyTurnedOn) {
            startTurnOffTimer()
        } else if (atomicState.appTurnedOn) { 
            cancelAllTurnOffTimers()
        }
    } else {
        runIn(1, "evaluateKeepAliveOff")
    }
}

def evaluateKeepAliveOff() {
    if (isPaused() || isUtilityOnly) return
    if (atomicState.appTurnedOn && !isPrimaryActive() && !isKeepAliveActive()) {
        startTurnOffTimer()
    }
}

def restrictionHandler(evt) {
    if (isPaused() || isUtilityOnly) return

    def isContactOrShade = (contactSensors?.any { it.id == evt.device?.id } || shadeSensors?.any { it.id == evt.device?.id })

    if (isContactOrShade) {
        def delay = settings.contactDebounceSeconds != null ? settings.contactDebounceSeconds : 120
        if (delay > 0) {
            unschedule("evaluateContactState")
            runIn(delay, "evaluateContactState")
        } else {
            evaluateContactState()
        }
        return
    }

    if (evt.value == "off" && disableOffSwitches?.any { it.id == evt.device?.id }) {
        evaluatePrimaryOff()
        return
    }

    def isOvercastEventOff = (evt.value == "off" && overcastSwitch?.id && evt.device?.id == overcastSwitch.id)
    
    if (turnOffOnContactOpen && isOvercastEventOff) {
        evaluateContactState()
        return
    }

    if (isPrimaryActive()) evaluateTurnOn()
}

def evaluateContactState() {
    if (isPaused() || isUtilityOnly) return

    def totalContacts = (contactSensors?.size() ?: 0) + (shadeSensors?.size() ?: 0)
    def openContacts = (contactSensors?.count { it.currentValue("contact") == "open" } ?: 0) + 
                       (shadeSensors?.count { it.currentValue("contact") == "open" } ?: 0)
    def averageOpen = totalContacts > 0 ? (openContacts / totalContacts) : 0
    def isMajorityOpen = averageOpen >= 0.5

    if (turnOffOnContactOpen && isMajorityOpen) {
        def overcastActive = (overcastSwitch?.currentValue("switch") == "on")
        def luxOverrideActive = false

        if (useLuxContactOverride && luxSensor) {
            def curLux = luxSensor.currentValue("illuminance") ?: 0
            def targetLux = luxContactThreshold ?: 0
            if (luxContactVar) {
                def hVar = getGlobalVar(luxContactVar)
                if (hVar != null) targetLux = hVar.value.toInteger()
            }
            if (curLux < targetLux) luxOverrideActive = true
        }

        if (!overcastActive && !luxOverrideActive) {
            def isLightOn = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" })

            if (isLightOn) {
                if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) {
                    debugLog("Majority of Windows/Shades are open, but lights are blocked from turning off.")
                    return
                }
                debugLog("Majority of Windows/Shades are open and overrides are off. Forcing lights OFF.")
                atomicState.appTurnedOn = false
                atomicState.manuallyTurnedOn = false
                atomicState.maxOverrideActive = false
                atomicState.arrivalActive = false
                atomicState.stdTaskTime = null
                cancelAllTurnOffTimers()
                sendOffCommands()
                return 
            }
        }
    }

    if (isPrimaryActive()) evaluateTurnOn()
}

def manageForceOffTimer() {
    if (enableForceOff && forceOffMinutes) {
        def delay = forceOffMinutes * 60
        runIn(delay, "forceOffHandler")
    }
}

def forceOffHandler() {
    if (isPaused() || isUtilityOnly) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) {
        debugLog("Absolute Force-Off bypassed: Block-Off switch is active.")
        return
    }
    
    debugLog("Absolute Force-Off: Sweep delay reached. Forcing lights OFF.")
    atomicState.appTurnedOn = false
    atomicState.manuallyTurnedOn = false
    atomicState.maxOverrideActive = false
    atomicState.arrivalActive = false
    atomicState.stdTaskTime = null
    cancelAllTurnOffTimers()
    sendOffCommands()
}

def startTurnOffTimer() {
    if (atomicState.arrivalActive || isUtilityOnly) return 
    
    unschedule("processTurnOff")
    
    def manualTime = manualTimeoutMinutes != null ? manualTimeoutMinutes : 30
    def defaultTime = defaultDelay != null ? defaultDelay : 5
    def delay = atomicState.manuallyTurnedOn ? manualTime : defaultTime

    // WALK-THROUGH LOGIC (Micro-Timeout)
    if (enableWalkThrough && !atomicState.manuallyTurnedOn && atomicState.motionStartTime) {
        def activeDuration = now() - atomicState.motionStartTime
        if (activeDuration < 15000) { 
            debugLog("Walk-Through detected (Motion lasted < 15s). Engaging 30-second micro-timeout.")
            delay = 0.5 
        }
    }

    atomicState.stdTaskTime = now() + (delay * 60000).toLong()
    
    if (delay == 0) {
        debugLog("Turn off timer is 0. Turning off immediately.")
        processTurnOff()
    } else {
        runIn((delay * 60).toInteger(), "processTurnOff")
        debugLog("Turn off timer started for ${delay} minutes.")
    }
}

def cancelAllTurnOffTimers() { 
    unschedule("processTurnOff")
    unschedule("executeFinalOffCommands") 
    unschedule("verifyTurnOff") 
    unschedule("forceOffHandler")
    unschedule("pollLuxWhileActive") 
    
    if (!atomicState.arrivalActive) {
        atomicState.stdTaskTime = null 
    }
}

def pollLuxWhileActive() {
    if (isPaused() || isUtilityOnly) return
    if (!isPrimaryActive() && !isKeepAliveActive()) return 
    
    // ACTIVE LUX SHUTOFF LOGIC
    if (atomicState.appTurnedOn) {
        if (enableActiveLuxShutoff && luxSensor && luxThreshold) {
            def curLux = luxSensor.currentValue("illuminance") ?: 0
            def targetLux = luxThreshold ?: 0
            if (curLux > (targetLux + 15)) {
                if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) {
                    debugLog("Active Lux Shutoff triggered, but ignored because Block-Off switch is active.")
                    return
                }
                debugLog("Active Lux Shutoff: Sun came out (${curLux} lx). Forcing lights off to save energy.")
                processTurnOff()
                return
            }
        }
        if (enableActiveLuxShutoff || enableActiveLuxPolling) {
            runIn(60, "pollLuxWhileActive")
        }
        return 
    }
    
    // STRICT KEEP-ALIVE FIX: If lights are OFF, we only evaluate a lux fade-in if there is PRIMARY motion.
    if (!isPrimaryActive()) {
        if (enableActiveLuxPolling) runIn(60, "pollLuxWhileActive")
        return
    }
    
    def shouldTurnOn = false
    if (luxSensor) {
        def curLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = luxThreshold ?: 0
        if (targetLux != null && curLux < targetLux) shouldTurnOn = true
        
        def totalContacts = (contactSensors?.size() ?: 0) + (shadeSensors?.size() ?: 0)
        def openContacts = (contactSensors?.count { it.currentValue("contact") == "open" } ?: 0) + 
                           (shadeSensors?.count { it.currentValue("contact") == "open" } ?: 0)
        def averageOpen = totalContacts > 0 ? (openContacts / totalContacts) : 0
        def isMajorityOpen = averageOpen >= 0.5

        if (useLuxContactOverride && isMajorityOpen) {
            def overrideLux = luxContactVar ? getGlobalVar(luxContactVar)?.value?.toInteger() : (luxContactThreshold ?: 0)
            if (curLux < overrideLux) shouldTurnOn = true
        }
    }
    
    if (shouldTurnOn) evaluateTurnOn()
    else runIn(60, "pollLuxWhileActive")
}

def evaluateTurnOn() {
    if (isPaused() || isUtilityOnly) return
    
    // STRICT VACANCY MODE
    if (enableVacancyMode) {
        debugLog("Vacancy Mode Active: Ignoring motion for turn-on. Waiting for manual trigger.")
        return 
    }
    
    if (atomicState.arrivalActive) {
        def anyOn = (switches?.any { it.currentValue("switch") == "on" } || 
                     dimmers?.any { it.currentValue("switch") == "on" } || 
                     colorBulbs?.any { it.currentValue("switch") == "on" })
        
        if (!anyOn) {
            debugLog("Arrival Fallback: Arrival is active but lights are OFF. Motion detected, enforcing Arrival ON state.")
            executeArrivalLighting()
        } else {
            debugLog("Arrival is active. Skipping standard motion evaluation to preserve Arrival lighting.")
        }
        return 
    }
    
    atomicState.offRetryCount = 0 
    
    if (activeModes && !activeModes.contains(location.mode)) return
    
    if (luxSensor) {
        def currentLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = luxThreshold ?: 0
        if (targetLux != null && currentLux >= targetLux) {
            if (enableActiveLuxPolling || enableActiveLuxShutoff) runIn(60, "pollLuxWhileActive")
            return
        }
    }
    
    if (isRuleBlocked()) return
    
    if (restrictByTime && startTimeType && endTimeType) {
        def sTime = resolveTime(startTimeType, startTime, startOffset)
        def eTime = resolveTime(endTimeType, endTime, endOffset)
        if (sTime && eTime) {
            def isInside = isTimeInWindow(sTime, eTime)
            def shouldBlock = (timeLogic == "Block execution DURING this window") ? isInside : !isInside
            if (shouldBlock) return
        }
    }
    
    def luxOverrideActive = false
    def totalContacts = (contactSensors?.size() ?: 0) + (shadeSensors?.size() ?: 0)
    def openContacts = (contactSensors?.count { it.currentValue("contact") == "open" } ?: 0) + 
                       (shadeSensors?.count { it.currentValue("contact") == "open" } ?: 0)
    def averageOpen = totalContacts > 0 ? (openContacts / totalContacts) : 0
    def isMajorityOpen = averageOpen >= 0.5
    
    if (useLuxContactOverride && luxSensor && isMajorityOpen) {
        def curLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = luxContactThreshold ?: 0
        if (luxContactVar) {
            def hVar = getGlobalVar(luxContactVar)
            if (hVar != null) targetLux = hVar.value.toInteger()
        }
        if (curLux < targetLux) {
            luxOverrideActive = true
        }
    }
 
    if (isMajorityOpen && overcastSwitch?.currentValue("switch") != "on" && !luxOverrideActive) {
        if ((enableActiveLuxPolling || enableActiveLuxShutoff) && luxSensor) runIn(60, "pollLuxWhileActive")
        return
    }

    atomicState.appTurnedOn = true
    atomicState.lastAutoCommand = now()
    
    if (enableActiveLuxShutoff && luxSensor) runIn(60, "pollLuxWhileActive")
    
    applyLightingSettings()
    manageForceOffTimer() 
}

def getTargetLevel() {
    if (parent?.isVisitorOverrideActive()) return 100

    def baseLevel = defaultLevel != null ? defaultLevel : 100
    
    if (useGlobalLevel && parent?.globalLevelVar) {
        def hubVar = getGlobalVar(parent.globalLevelVar)
        if (hubVar != null && hubVar.value != null) baseLevel = hubVar.value.toInteger()
    } else if (useLevelDimmer && levelDimmer) {
        def curLvl = levelDimmer.currentValue("level")
        if (curLvl != null) baseLevel = curLvl.toInteger()
    } else if (useLevelVar && levelVarName) {
        def hubVar = getGlobalVar(levelVarName)
        if (hubVar != null && hubVar.value != null) baseLevel = hubVar.value.toInteger()
    }
    
    // DAYLIGHT HARVESTING PROPORTIONAL DIMMING
    if (enableDaylightHarvesting && luxSensor && luxThreshold) {
        def curLux = luxSensor.currentValue("illuminance") ?: 0
        def target = luxThreshold ?: 0
        if (curLux < target && target > 0) {
            def deficit = 1.0 - (curLux / target)
            def calculatedLevel = Math.round(deficit * baseLevel).toInteger()
            return calculatedLevel < 10 ? 10 : calculatedLevel
        } else if (curLux >= target) {
            return 10
        }
    }
    
    return baseLevel
}

def getTargetColorTemp() {
    if (parent?.isVisitorOverrideActive()) return 6500

    if (useGlobalCT && parent?.globalCTVar) {
        def hubVar = getGlobalVar(parent.globalCTVar)
        if (hubVar != null && hubVar.value != null) return hubVar.value.toInteger()
    } else if (useCTVar && ctVarName) {
        def hubVar = getGlobalVar(ctVarName)
        if (hubVar != null && hubVar.value != null) return hubVar.value.toInteger()
    }
    return defaultCT != null ? defaultCT : 2700
}

def applyLightingSettings(transition = null) {
    if (isPaused() || isUtilityOnly) return
    atomicState.lastAutoCommand = now() 
    def t = (transition != null) ? transition : null

    def needsRelayBoot = false
    if ((lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") && isSmartBulbOnRelay) {
        if (relaySwitch?.currentValue("switch") != "on") {
            try { 
                relaySwitch.on() 
                runIn(3, "executeRefresh")
            } catch(e) { log.error "Relay Boot Exception: ${e}" }
            needsRelayBoot = true
        }
    }

    if (needsRelayBoot) {
        def rDelay = relayDelay != null ? relayDelay : 1500
        runInMillis(rDelay, "applyLightingSettingsCore", [data: [t: t]])
    } else {
        applyLightingSettingsCore([t: t])
    }
}

def applyLightingSettingsCore(data) {
    if (isPaused() || isUtilityOnly) return
    atomicState.lastAutoCommand = now()
    def t = data?.t
    def isRetry = data?.isRetry ?: false
    def refreshNeeded = false

    if (lightType == "Simple On/Off") {
        switches?.each { 
            if (isRetry || it.currentValue("switch") != "on") {
                try { 
                    it.on()
                    refreshNeeded = true
                } catch(e) { log.error "Exception turning on ${it.displayName}: ${e}" }
            }
        }
    } else if (lightType == "Adjustable Bulb / Dimmer") {
        def lvl = getTargetLevel()
        dimmers?.each { 
            if (isRetry || it.currentValue("switch") != "on" || it.currentValue("level") != lvl) { 
                try {
                    if (t != null) it.setLevel(lvl, t) else it.setLevel(lvl)
                    refreshNeeded = true
                } catch(e) { log.error "Exception setting level for ${it.displayName}: ${e}" }
            }
        }
    } else if (lightType == "Color / CT Bulb") {
        def lvl = getTargetLevel()
        def ct = getTargetColorTemp()
        
        colorBulbs?.each { 
            if (isRetry || it.currentValue("switch") != "on" || it.currentValue("level") != lvl || it.currentValue("colorTemperature") != ct) { 
                try {
                    if (t != null) {
                        it.setColorTemperature(ct, lvl, t)
                    } else {
                        it.setColorTemperature(ct, lvl)
                    }
                    refreshNeeded = true
                } catch(e) { log.error "Exception setting color/level for ${it.displayName}: ${e}" }
            }
        }
    }

    if (isSmartBulbOnRelay && !isRetry && (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb")) {
        runInMillis(4000, "applyLightingSettingsCore", [data: [t: t, isRetry: true]])
    }

    if (refreshNeeded && !isRetry) {
        runIn(8, "executeRefresh")
    }
}

def processTurnOff() {
    if (isPaused() || isUtilityOnly) return
    
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    
    if (isPrimaryActive() || isKeepAliveActive()) {
        if (atomicState.manuallyTurnedOn) startTurnOffTimer() 
        return 
    }
    
    atomicState.appTurnedOn = false
    atomicState.manuallyTurnedOn = false
    atomicState.maxOverrideActive = false
    atomicState.arrivalActive = false
    atomicState.stdTaskTime = null
    
    sendOffCommands()
    runIn(10, "verifyTurnOff")
}

def sendOffCommands() {
    if (isPaused() || isUtilityOnly) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) {
        debugLog("Turn-off aborted: Block-Off switch is active.")
        return
    }
    
    atomicState.lastAutoOffCommand = now() 
    def needsDimDelay = false
    
    if (dimBeforeOff != false) {
        dimmers?.each { 
            if (it.currentValue("switch") == "on") {
                try { it.setLevel(1) } catch(e) {}
                needsDimDelay = true
            }
        }
        colorBulbs?.each { 
            if (it.currentValue("switch") == "on") {
                try { it.setLevel(1) } catch(e) {}
                needsDimDelay = true
            }
        }
    }

    if (needsDimDelay) {
        runInMillis(1000, "executeFinalOffCommands")
    } else {
        executeFinalOffCommands()
    }
}

def executeFinalOffCommands() {
    if (isPaused() || atomicState.appTurnedOn || isUtilityOnly) return 
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return // Ultimate safeguard
    
    atomicState.lastAutoOffCommand = now() 
    def refreshNeeded = false
    def staggerMs = 0
    def delayIncrement = 400

    switches?.each { dev -> 
        def needsBlindOff = blindOffSwitches?.find { b -> b.id == dev.id }
        if (needsBlindOff || dev.currentValue("switch") != "off") { 
            if (staggerMs == 0) {
                try { 
                    dev.off()
                    refreshNeeded = true 
                } catch(e) { log.error "Exception turning off ${dev.displayName}: ${e}" }
            } else {
                runInMillis(staggerMs, "executeDelayedCommand", [data: [devId: dev.id, cmd: "off"]])
            }
            staggerMs += delayIncrement
        } 
    }
    dimmers?.each { dev -> 
        def needsBlindOff = blindOffSwitches?.find { b -> b.id == dev.id }
        if (needsBlindOff || dev.currentValue("switch") != "off") { 
            if (staggerMs == 0) {
                try { 
                    dev.off()
                    refreshNeeded = true
                } catch(e) { log.error "Exception turning off ${dev.displayName}: ${e}" }
            } else {
                runInMillis(staggerMs, "executeDelayedCommand", [data: [devId: dev.id, cmd: "off"]])
            }
            staggerMs += delayIncrement
        } 
    }
    colorBulbs?.each { dev -> 
        def needsBlindOff = blindOffSwitches?.find { b -> b.id == dev.id }
        if (needsBlindOff || dev.currentValue("switch") != "off") { 
            if (staggerMs == 0) {
                try { 
                    dev.off()
                    refreshNeeded = true
                } catch(e) { log.error "Exception turning off ${dev.displayName}: ${e}" }
            } else {
                runInMillis(staggerMs, "executeDelayedCommand", [data: [devId: dev.id, cmd: "off"]])
            }
            staggerMs += delayIncrement
        } 
    }
    
    if (isSmartBulbOnRelay && turnOffRelay && relaySwitch?.currentValue("switch") != "off") {
        try { 
            relaySwitch?.off() 
            atomicState.lastAutoOffCommand = now()
        } catch(e) { 
            log.error "Exception turning off Relay: ${e}" 
        }
    }
    
    if (refreshNeeded) {
        runIn(8, "executeRefresh")
    }
}

def executeRefresh() {
    if (isUtilityOnly) return
    atomicState.lastAutoCommand = now() 
    atomicState.lastAutoOffCommand = now() 

    if (lightType == "Simple On/Off") {
        switches?.each { 
            if (it.hasCommand("refresh") && !shouldSkipRefresh(it)) {
                try { it.refresh() } catch(e) {}
            }
        }
    }
}

def verifyTurnOff() {
    if (isPaused() || isUtilityOnly) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) {
        atomicState.offRetryCount = 0
        return
    }
    
    if (isPrimaryActive() || isKeepAliveActive()) {
        atomicState.offRetryCount = 0
        return 
    }

    def devicesToRetry = []
    if (switches) devicesToRetry += switches.findAll { it.currentValue("switch") == "on" }
    if (dimmers) devicesToRetry += dimmers.findAll { it.currentValue("switch") == "on" }
    if (colorBulbs) devicesToRetry += colorBulbs.findAll { it.currentValue("switch") == "on" }
    
    def relayNeedsOff = false
    if (isSmartBulbOnRelay && turnOffRelay && relaySwitch?.currentValue("switch") == "on") {
        relayNeedsOff = true
    }
    
    if ((devicesToRetry.size() > 0 || relayNeedsOff) && atomicState.offRetryCount < 3) {
        atomicState.offRetryCount++
        debugLog("verifyTurnOff: Checking ${devicesToRetry.size()} orphaned devices.")
        
        devicesToRetry.each { dev ->
            if (dev.hasCommand("refresh") && !shouldSkipRefresh(dev)) {
                try { dev.refresh() } catch(e) {}
            }
            runInMillis(2000, "executeDelayedCommand", [data: [devId: dev.id, cmd: "off"]])
        }
        
        if (relayNeedsOff) {
            try { relaySwitch?.off() } catch(e) {}
        }
        
        runIn(12, "verifyTurnOff")
    } else { 
        atomicState.offRetryCount = 0 
    }
}

def physicalOnHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    if (ignoreOverrideSwitches?.any { it.currentValue("switch") == "on" }) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return

    if (enablePowerRecovery && enablePowerBlipDetection) {
        def lastActive = atomicState.lastPrimaryActiveTime ?: 0
        def timeSinceActive = now() - lastActive
        def thresholdTime = blipInactivityThreshold != null ? blipInactivityThreshold : 60
        def thresholdMs = thresholdTime * 60000
        
        if (timeSinceActive > thresholdMs && !isPrimaryActive()) {
            debugLog("Power Blip Detected: ${evt.device.displayName} turned on, but zone inactive for ${timeSinceActive/60000} mins. Initiating soft recovery.")
            def recTrans = recoveryTransition != null ? recoveryTransition : 30
            softFadeOff(recTrans)
            return 
        }
    }

    def debounceTime = isSmartBulbOnRelay ? 10000 : 5000 
    def isOutsideAppDebounce = (now() - (atomicState.lastAutoCommand ?: 0) > debounceTime) 
    
    if (!isOutsideAppDebounce) return
    if (atomicState.appTurnedOn && !evt.isPhysical()) return
    
    atomicState.manuallyTurnedOn = true
    atomicState.appTurnedOn = true
    atomicState.maxOverrideActive = false
    cancelAllTurnOffTimers() 
    startTurnOffTimer() 
    manageForceOffTimer() 
    
    if (lightType == "Color / CT Bulb" || lightType == "Adjustable Bulb / Dimmer") {
        runIn(1, "syncManualBulbs")
    }
}

def physicalOffHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    if (ignoreOverrideSwitches?.any { it.currentValue("switch") == "on" }) return
    if (isRuleBlocked()) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return

    def debounceTime = isSmartBulbOnRelay ? 10000 : 5000 
    def isOutsideAppDebounce = (now() - (atomicState.lastAutoOffCommand ?: 0) > debounceTime) 
    
    if (!isOutsideAppDebounce) return
    
    if (!atomicState.arrivalActive) {
        atomicState.appTurnedOn = false
        atomicState.manuallyTurnedOn = false
        atomicState.maxOverrideActive = false
        def gp = gracePeriod != null ? gracePeriod : 15
        atomicState.gracePeriodEnd = now() + (gp * 1000)
        cancelAllTurnOffTimers()
    } else {
        debugLog("Physical OFF detected, but Arrival Mode is actively locking the zone. Retaining state for motion fallback.")
    }
}

def syncManualBulbs() {
    if (isPaused() || isUtilityOnly) return
    
    if (atomicState.arrivalActive) return
    
    atomicState.lastAutoCommand = now() 
    
    def t = null
    def lvl = getTargetLevel()

    if (lightType == "Adjustable Bulb / Dimmer") {
        dimmers?.each {
            if (it.currentValue("switch") == "on") {
                try {
                    if (t != null) it.setLevel(lvl, t) else it.setLevel(lvl)
                } catch(e) { log.error "Exception syncing dimmer ${it.displayName}: ${e}" }
            }
        }
    } else if (lightType == "Color / CT Bulb") {
        def ct = getTargetColorTemp()
        colorBulbs?.each {
            if (it.currentValue("switch") == "on") {
                try {
                    if (t != null) {
                        it.setColorTemperature(ct, lvl, t)
                    } else {
                        it.setColorTemperature(ct, lvl)
                    }
                } catch(e) { log.error "Exception syncing color bulb ${it.displayName}: ${e}" }
            }
        }
    }
}

def modeChangeHandler(evt) {
    if (isPaused() || isUtilityOnly) return
    def forceOffList = [turnOffOnModes].flatten().findAll { it }
    
    if (forceOffList.contains(evt.value)) {
        cancelAllTurnOffTimers() 
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger, "processTurnOff")
    }
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        debugLog("Live Dashboard Refreshed")
    }
}

def isArrivalEnabled() {
    return enableArrivalLighting == true
}

def turnOnArrival(durationMin = 30) {
    if (isPaused() || isUtilityOnly) return
    
    atomicState.arrivalDurationMin = durationMin

    def isOvercast = (overcastSwitch?.currentValue("switch") == "on")
    def sunInfo = getSunriseAndSunset()
    def nowTime = new Date()
    def isNight = false
    
    if (sunInfo.sunrise && sunInfo.sunset) {
        isNight = (nowTime.after(sunInfo.sunset) || nowTime.before(sunInfo.sunrise))
    }

    if (isNight || isOvercast) {
        def stagger = new Random().nextInt(2500) + 100
        runInMillis(stagger, "executeArrivalLighting")
    } else {
        def delay = arrivalDayDelay != null ? arrivalDayDelay : 90
        debugLog("Daytime Arrival triggered. Delaying ${delay} seconds to allow shades/windows to process.")
        runIn(delay, "evalAndExecuteArrivalLighting")
    }
}

def evalAndExecuteArrivalLighting() {
    if (isPaused() || isUtilityOnly) return

    def totalContacts = (contactSensors?.size() ?: 0) + (shadeSensors?.size() ?: 0)
    def openContacts = (contactSensors?.count { it.currentValue("contact") == "open" } ?: 0) + 
                       (shadeSensors?.count { it.currentValue("contact") == "open" } ?: 0)
    
    def averageOpen = totalContacts > 0 ? (openContacts / totalContacts) : 0
    def isMajorityOpen = averageOpen >= 0.5

    if (isMajorityOpen) {
        def luxOverrideActive = false
        if (useLuxContactOverride && luxSensor) {
            def curLux = luxSensor.currentValue("illuminance") ?: 0
            def targetLux = luxContactThreshold ?: 0
            if (luxContactVar) {
                def hVar = getGlobalVar(luxContactVar)
                if (hVar != null) targetLux = hVar.value.toInteger()
            }
            if (curLux < targetLux) luxOverrideActive = true
        }

        if (!luxOverrideActive) {
            debugLog("Arrival Lighting Cancelled: The majority of shades/windows are open (${(averageOpen * 100).toInteger()}%) and there is sufficient natural light.")
            return 
        }
    }

    def stagger = new Random().nextInt(2500) + 100
    runInMillis(stagger, "executeArrivalLighting")
}

def executeArrivalLighting() {
    atomicState.arrivalActive = true
    atomicState.appTurnedOn = true
    atomicState.lastAutoCommand = now() 
    
    def durSec = (atomicState.arrivalDurationMin ?: 30) * 60
    atomicState.stdTaskTime = now() + (durSec * 1000)
    
    runIn(durSec, "revertFromArrival") 
    
    if (lightType == "Color / CT Bulb" && arrivalColorOverride) {
        def needsRelayBoot = false
        if (isSmartBulbOnRelay && relaySwitch?.currentValue("switch") != "on") {
            try { relaySwitch.on() } catch(e){}
            needsRelayBoot = true
        }
        
        if (needsRelayBoot) {
            def rDelay = relayDelay != null ? relayDelay : 1500
            runInMillis(rDelay, "executeArrivalColorOverride")
        } else {
            executeArrivalColorOverride()
        }
    } else {
        applyLightingSettings()
    }
}

def executeArrivalColorOverride() {
    colorBulbs?.each { try { it.setColorTemperature(6500, 100, 0) } catch(e){} }
}

def revertFromArrival() {
    if (isPaused() || isUtilityOnly) return
    atomicState.arrivalActive = false
    unschedule("revertFromArrival") 
    
    if (lightType == "Color / CT Bulb" && arrivalColorOverride) {
        def arrTrans = arrivalTransitionTime != null ? arrivalTransitionTime : 3
        applyLightingSettings(arrTrans)
    } else if (lightType == "Adjustable Bulb / Dimmer") {
        applyLightingSettings()
    }
    
    startTurnOffTimer()
}

def executeParentSweep(delayMs = 0) {
    if (isPaused() || isUtilityOnly || atomicState.arrivalActive) return 
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    
    if (!isPrimaryActive() && !isKeepAliveActive()) {
        if (delayMs > 0) {
            runInMillis(delayMs, "processTurnOff")
        } else {
            processTurnOff()
        }
    }
}

def clearManualOverride() {
    if (isPaused() || isUtilityOnly) return
    if (atomicState.manuallyTurnedOn) {
        atomicState.manuallyTurnedOn = false
        atomicState.maxOverrideActive = false
        if (!isPrimaryActive() && !isKeepAliveActive()) {
            startTurnOffTimer()
           } else {
            cancelAllTurnOffTimers()
        }
        return true
    }
    return false
}

def getZoneStatus() {
    if (isUtilityOnly) {
        return [
            name: app.label ?: "Utility Rule",
            light: "N/A",
            motion: "N/A",
            status: "<span style='color: green; font-weight: bold;'>Utility/Recovery Mode</span>",
            lastTrigger: "--",
            timer: "--",
            health: []
        ]
    }

    def isLightOn = (switches?.any { it.currentValue("switch") == "on" } || dimmers?.any { it.currentValue("switch") == "on" } || colorBulbs?.any { it.currentValue("switch") == "on" })
    
    def primaryActive = isPrimaryActive()
    def keepAliveActive = isKeepAliveActive()
    
    def healthData = []
    if (enableHealthWatchdog) {
        def checkList = []
        if (motionSensors) checkList.addAll(motionSensors)
        if (primaryContactSensors) checkList.addAll(primaryContactSensors)
        if (keepAliveMotionSensors) checkList.addAll(keepAliveMotionSensors)
        if (keepAliveVibrationSensors) checkList.addAll(keepAliveVibrationSensors)
        if (contactSensors) checkList.addAll(contactSensors)
        if (shadeSensors) checkList.addAll(shadeSensors)
        
        checkList.unique().findAll { it }.each { d ->
            healthData << [name: d.displayName, battery: d.currentValue("battery"), lastActivity: d.getLastActivity()?.format("MM-dd HH:mm")]
        }
    }

    def statusText = "Standby"
    
    if (parent?.isSystemPaused()) statusText = "<span style='color: red; font-weight: bold;'>PAUSED (Global)</span>"
    else if (pauseApp) statusText = "<span style='color: red; font-weight: bold;'>PAUSED (Rule)</span>"
    else if (atomicState.arrivalActive) statusText = "<span style='color: purple; font-weight: bold;'>Arrival Mode Active</span>"
    else if (atomicState.maxOverrideActive) statusText = "<span style='color: blue; font-weight: bold;'>Max Override (100% / 6500K)</span>"
    else if (isLightOn && (primaryActive || keepAliveActive)) statusText = "Occupied"
    else if (isLightOn && !primaryActive && !keepAliveActive) statusText = "Counting Down"
    else if (!isLightOn && primaryActive) statusText = "Trigger Ignored"

    def oList = getActiveOverrides()
    if (oList) {
        statusText += "<br>" + oList.join("<br>")
    }

    def timerText = "--"
    if ((isLightOn || atomicState.arrivalActive) && atomicState.stdTaskTime && atomicState.stdTaskTime > now()) {
        def diff = atomicState.stdTaskTime - now()
        timerText = "${(diff / 60000).toInteger()}m ${((diff % 60000) / 1000).toInteger()}s"
        if (atomicState.arrivalActive) timerText += "<br><span style='font-size: 11px; color: purple; font-weight:bold;'>(Arrival)</span>"
    }
  
    def lightDetails = isLightOn ? "ON" : "OFF"
    
    if (isLightOn) {
        if (lightType == "Adjustable Bulb / Dimmer") {
            def activeDev = dimmers?.find { it.currentValue("switch") == "on" }
            if (activeDev) {
                def lvl = activeDev.currentValue("level")
                if (lvl != null) lightDetails += " <span style='font-size: 11px; color: #666;'><br>(${lvl}%)</span>"
            }
        } else if (lightType == "Color / CT Bulb") {
            def activeDev = colorBulbs?.find { it.currentValue("switch") == "on" }
            if (activeDev) {
                def lvl = activeDev.currentValue("level")
                def ct = activeDev.currentValue("colorTemperature")
                def extras = []
                if (lvl != null) extras << "${lvl}%"
                if (ct != null) extras << "${ct}K"
                if (extras) lightDetails += " <span style='font-size: 11px; color: #666;'><br>(" + extras.join(" @ ") + ")</span>"
            }
        }
    }
    
    return [
        name: app.label ?: "Unnamed Zone",
        light: lightDetails,
        motion: primaryActive ? "ACTIVE" : (keepAliveActive ? "KEEP-ALIVE" : "INACTIVE"),
        status: statusText,
        lastTrigger: enableTriggerTracking ? atomicState.lastTriggerSource : null,
        timer: timerText,
        health: healthData
    ]
}

def dynamicCTUpdate(newCT) {
    if (isPaused() || isUtilityOnly) return
    if (atomicState.arrivalActive || atomicState.maxOverrideActive) return 
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    
    if (!useGlobalCT) return
    if (lightType == "Color / CT Bulb") {
        def lvl = getTargetLevel()
        
        colorBulbs?.each { bulb ->
            if (bulb.currentValue("switch") == "on") {
                try { bulb.setColorTemperature(newCT, lvl) } catch(e){}
            }
        }
    }
}

def dynamicLevelUpdate(newLvl) {
    if (isPaused() || isUtilityOnly) return
    if (atomicState.arrivalActive || atomicState.maxOverrideActive) return 
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) return
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    
    if (!useGlobalLevel) return
    if (lightType == "Adjustable Bulb / Dimmer") {
        dimmers?.each { b ->
            if (b.currentValue("switch") == "on") {
                try { b.setLevel(newLvl) } catch(e){}
            }
        }
    } else if (lightType == "Color / CT Bulb") {
        def ct = getTargetColorTemp()
        colorBulbs?.each { bulb ->
            if (bulb.currentValue("switch") == "on") {
                try { 
                    bulb.setColorTemperature(ct, newLvl)
                } catch(e){}
            }
        }
    }
}

def resolveTime(type, timeStr, offset) {
    if (type == "Specific Time") {
        return timeToday(timeStr)
    } else if (type == "Sunrise" || type == "Sunset") {
        def sunInfo = getSunriseAndSunset()
        def baseTime = (type == "Sunrise") ? sunInfo.sunrise : sunInfo.sunset
        if (baseTime && offset) {
            return new Date(baseTime.time + (offset.toInteger() * 60000))
        }
        return baseTime
    }
    return null
}

def isTimeInWindow(sTime, eTime) {
    if (!sTime || !eTime) return false
    def now = new Date()
    
    if (sTime.before(eTime)) {
        return now.after(sTime) && now.before(eTime)
    } else {
        return now.after(sTime) || now.before(eTime)
    }
}

def executeDelayedCommand(data) {
    def devId = data?.devId
    def cmd = data?.cmd
    
    def targetDev = switches?.find { it.id == devId } ?: dimmers?.find { it.id == devId } ?: colorBulbs?.find { it.id == devId }
    if (targetDev) {
        try { 
            if (cmd == "off") targetDev.off()
            if (cmd == "on") targetDev.on()
        } catch(e) { log.error "Delayed command exception: ${e}" }
    }
}
