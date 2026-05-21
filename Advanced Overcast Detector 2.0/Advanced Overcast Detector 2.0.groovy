/**
 * Advanced Overcast Detector 2.0
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Overcast Detector 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        // --- EXPOSED SECTIONS ---
        
        section("Live System Dashboard") {
            input name: "refreshBtn", type: "button", title: "🔄 Refresh Data"
            
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

            def statusExplanation = getHumanReadableStatus()
            
            dashHTML += "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff; margin-bottom: 10px; font-size: 14px;'>" +
                        "<b>System Status:</b> ${statusExplanation}</div>"
                        
            def currentLux = primaryLuxSensor ? "${getAggregateLux()} lx" : "-- lx"
            def oLimit = getSmartOvercastThreshold()
            def oReason = useSmartThresholds ? "Smart Scaled" : "Static"
            def cLimit = useDynamicClear ? getDynamicClearThreshold() : getSmartClearThreshold()
            def cReason = useDynamicClear ? "Auto-Curve" : (useSmartThresholds ? "Smart Scaled" : "Static")
            def epcsb = peakClearLux ?: 10000
            def dailyMax = state.dailyMaxLux ?: 0
            def vSwitch = targetSwitch ? targetSwitch.currentValue("switch")?.toUpperCase() : "NOT SET"
            def vDim = targetDimmer ? (targetDimmer.currentValue("switch") == "on" ? "${targetDimmer.currentValue('level')}%" : "OFF") : "NOT SET"

            dashHTML += """
            <table class="dash-table">
                <thead><tr><th>Metric</th><th colspan="3">Current Value & Logic</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Outdoor Lux</td><td colspan="3" class="dash-val"><b>${currentLux}</b> (Daily Max: ${dailyMax} lx)</td></tr>
                    
                    <tr><td colspan="4" class="dash-subhead">Calculated Thresholds</td></tr>
                    <tr><td class="dash-hl">Overcast Drop Target</td><td colspan="3" class="dash-val">${oLimit} lx (${oReason})</td></tr>
                    <tr><td class="dash-hl">Clear Sky Target</td><td colspan="3" class="dash-val">${cLimit} lx (${cReason})</td></tr>
                    <tr><td class="dash-hl">Expected Peak Baseline</td><td colspan="3" class="dash-val">${epcsb} lx</td></tr>
                    
                    <tr><td colspan="4" class="dash-subhead">Target Outputs</td></tr>
                    <tr><td class="dash-hl">Virtual Switch</td><td colspan="3" class="dash-val"><b>${vSwitch}</b></td></tr>
                    <tr><td class="dash-hl">Proportional Dimmer</td><td colspan="3" class="dash-val"><b>${vDim}</b></td></tr>
                </tbody>
            </table>
            """

            if (numRooms && numRooms > 0) {
                dashHTML += "<div style='margin-top: 20px; font-weight: bold; font-size: 14px; color: #343a40;'>Smart Room Darkness Targets</div>"
                dashHTML += "<table class='dash-table' style='margin-top: 5px;'>"
                dashHTML += "<thead><tr><th>Room Name</th><th>Daily Max</th><th>Days Learned</th><th>Learned Setpoint</th><th>Target Variable</th></tr></thead><tbody>"
                
                for (int i = 1; i <= numRooms; i++) {
                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    def rData = state.roomData ? state.roomData["${i}"] : null
                    def dMax = rData?.dailyMax ?: 0
                    def setpt = rData?.currentSetpoint ?: (settings["roomBaseLux_${i}"] ?: 0)
                    def vName = settings["roomVar_${i}"] ?: "Not Configured"
                    
                    def daysLearned = rData?.peakHistory ? rData.peakHistory.size() : 0
                    def learningStatus = ""
                    def reqDays = (settings.learningDaysReq ?: "30").toInteger()
                    
                    if (useSmartLearning) {
                        learningStatus = (daysLearned >= reqDays) ? "<span style='color: green; font-weight: bold;'>${daysLearned}/${reqDays} (Active)</span>" : "<span style='color: #d2691e;'>${daysLearned}/${reqDays} (Learning)</span>"
                    } else {
                        learningStatus = "<span style='color: #888;'>Disabled</span>"
                    }
                
                    dashHTML += "<tr><td class='dash-hl'>${rName}</td><td>${dMax} lx</td><td>${learningStatus}</td><td style='color: #b8860b; font-weight: bold;'>${setpt} lx</td><td style='color: #555;'>${vName}</td></tr>"
                }
                dashHTML += "</tbody></table>"
                dashHTML += generateRoomBarGraph()
            }
            
            paragraph dashHTML
        }
        
        section("Weather Event & Cloud History") {
            if (state.cloudHistory && state.cloudHistory.size() > 0) {
                def tableHtml = "<table class='dash-table'>"
                tableHtml += "<thead><tr><th>Event Start</th><th>Duration</th><th>Lux Drop</th><th>Lowest Point</th></tr></thead><tbody>"
                
                state.cloudHistory.each { event ->
                    tableHtml += "<tr>"
                    tableHtml += "<td>${event.time}</td>"
                    tableHtml += "<td><b>${event.duration}</b></td>"
                    tableHtml += "<td style='color: #d2691e;'>-${event.drop}</td>"
                    tableHtml += "<td style='color: #555;'>${event.minLux}</td>"
                    tableHtml += "</tr>"
                }
                tableHtml += "</tbody></table>"
                
                if (state.activeCloudEvent) {
                    tableHtml += "<div style='margin-top: 8px; font-size: 12px; color: blue; font-weight: bold;'>☁️ Potential Clouding in progress...</div>"
                }
                
                paragraph tableHtml
            } else {
                paragraph "<i>No passing clouds or sudden drops recorded yet.</i>"
            }
        }
        
        section("24-Hour Lux Trend vs. Expected Solar Baseline") {
            paragraph "<div style='font-size:13px; color:#555; margin-bottom: 10px;'><b>What it does:</b> Draws a local, internet-free chart mapping your actual outdoor sensors against the theoretical clear-sky solar curve to visually identify overcast conditions.</div>"
            if (state.luxHistory && state.luxHistory.size() > 2) {
                paragraph generateLocalLineChart()
            } else {
                paragraph "<i>Collecting data... Graph will appear once enough data points are gathered.</i>"
            }
        }
        
        // --- HIDDEN/COLLAPSIBLE SECTIONS ---
        
        section("<b>1. Application History (Last 20 Events)</b>", hideable: true, hidden: true) {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as the app takes action.</i>"
            }
            input "clearHistoryBtn", "button", title: "🗑️ Clear History Log"
        }

        section("<b>2. Sensor & Control Targets</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Links the outdoor physical hardware to the virtual switches that will drive your lighting automations.</div>"
            paragraph "<b>Outdoor Sensor Array:</b>"
            
            input "primaryLuxSensor", "capability.illuminanceMeasurement", title: "Primary Outdoor Lux Sensor", required: true
            input "auxLuxSensor1", "capability.illuminanceMeasurement", title: "Auxiliary Outdoor Lux Sensor 1", required: false
            
            input "averageSensors", "bool", title: "Average active outdoor sensors?", defaultValue: true,
                description: "If ON, the app will average the Primary and Aux sensors. If OFF, it will only use the Primary Sensor for logic (but will still graph both)."
            
            input "sensorInterval", "number", title: "Sensor Update Interval (Minutes)", defaultValue: 15,
                description: "How often your sensor reports data. The app dynamically scales its math windows based on this limitation so it doesn't miss sudden drops."
            
            paragraph "<hr><b>Control Targets:</b> Select one or both. The Virtual Switch handles binary logic (ON/OFF). The Virtual Dimmer scales brightness based on storm severity."
            input "targetSwitch", "capability.switch", title: "Virtual Switch (ON = Overcast/Dark)"
            input "targetDimmer", "capability.switchLevel", title: "Virtual Dimmer (Proportional Brightness)"
            
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch"
            input "activeModes", "mode", title: "Active Modes (App only runs in these)", multiple: true
        }

        section("<b>3. Hysteresis & Thresholds (The Deadband)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Establishes your 'Deadband' to prevent the system from rapidly yo-yoing the lights when clouds quickly pass by.</div>"
            
            input "useSmartThresholds", "bool", title: "Enable Smart Threshold Scaling?", defaultValue: true, submitOnChange: true,
                description: "Automatically scales your Overcast and Clear Sky limits proportionally as the Expected Peak Clear-Sky Brightness changes with the seasons."
                
            input "overcastThreshold", "number", title: "Base Overcast Drop Threshold (Lux)", defaultValue: 2000,
                description: "If lux drops below this, start the Overcast timer. (Acts as the baseline ratio if Smart Thresholds are enabled)."
                
            input "clearThreshold", "number", title: "Base Clear Sky Recovery Threshold (Lux)", defaultValue: 4000,
                description: "If lux rises above this, start the Clear Sky timer. (Acts as the baseline ratio if Smart Thresholds are enabled)."
                
            input "debounceTime", "number", title: "Anti-Yo-Yo Debounce Time (Minutes)", defaultValue: 10,
                description: "How long the sky must stay below/above the threshold before flipping the virtual outputs."
                
            input "useDynamicClear", "bool", title: "Enable Automatic Time-of-Year & Time-of-Day Adjustments?", defaultValue: true, submitOnChange: true,
                description: "If enabled, the Clear Sky Recovery Threshold dynamically curves based on solar position and season to prevent evening/winter yo-yoing."
        }

        section("<b>4. Universal Darkness (Nighttime Logic)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Overrides the lux sensors after sunset to ensure your motion lighting automations function properly all night long.</div>"
            input "useAstro", "bool", title: "Apply Nighttime Logic?", defaultValue: true, submitOnChange: true
            
            if (useAstro) {
                input "nightAction", "enum", title: "When the sun sets, force the virtual outputs:", options: ["Turn OFF (Clear/Night)", "Turn ON (Dark/Overcast)", "Do Nothing (Leave as is)"], defaultValue: "Turn ON (Dark/Overcast)",
                    description: "Select 'Turn ON' to ensure your motion lighting automations function properly all night."
                input "sunriseOffset", "number", title: "Sunrise Offset (Minutes, +/-)", defaultValue: 0
                input "sunsetOffset", "number", title: "Sunset Offset (Minutes, +/-)", defaultValue: 0
            }
        }
        
        section("<b>5. Smart Room Darkness Detection</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Connects indoor rooms to dynamically track natural light. The app will calculate a seasonal 'Darkness Threshold' for each room and output it to a Hub Variable for your lighting automations.</div>"
            input "numRooms", "number", title: "Number of Smart Rooms to Configure (0-12)", defaultValue: 0, submitOnChange: true, range: "0..12"
            
            if (numRooms && numRooms > 0) {
                for (int i = 1; i <= numRooms; i++) {
                    def rmNum = i
                    paragraph "<hr><b>Room ${rmNum} Configuration</b>"
                    input "roomName_${rmNum}", "string", title: "Room Name"
                    input "roomLux_${rmNum}", "capability.illuminanceMeasurement", title: "Indoor Lux Sensor"
                    input "roomShades_${rmNum}", "capability.contactSensor", title: "Shade Contact Sensor(s) (Closed = Ignored)", multiple: true
                    input "roomLights_${rmNum}", "capability.switch", title: "Room Lights (ON = Ignored)", multiple: true
                    
                    input "roomPeakLux_${rmNum}", "number", title: "Expected Peak Brightness (Lux)", 
                        description: "What does this room's sensor typically read at peak daylight on a clear day?"
                    input "roomBaseLux_${rmNum}", "number", title: "Base Darkness Threshold (Lux)", 
                        description: "The brightness level at which you consider this room 'dark' (The app will scale this ratio automatically)."
                    
                    input "roomVar_${rmNum}", "string", title: "Target Hub Variable Name (Number)", 
                        description: "Exact string name of the Hub Variable. The app will write the daily calculated setpoint here."
                }
            }
        }

        section("<b>6. Smart Peak Brightness Learning (Solar Baseline)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically tracks daily peak brightness to adapt to changing seasons. It logs the daily max, averages the data, and <b>automatically overwrites</b> the setting below.</div>"
            
            input "useSmartLearning", "bool", title: "Enable Smart Learning Mode", defaultValue: true, submitOnChange: true,
                description: "Silently drops bad weather days, learns your true solar peak, and automatically updates the Expected Peak setting below."
                
            input "learningDaysReq", "enum", title: "Averaging Window (Days)", options: ["10", "20", "30"], defaultValue: "30", submitOnChange: true,
                description: "How many days of clear data the app averages together to calculate the true peak."

            input "learningThresholdPct", "number", title: "Smart Learning Minimum Threshold (%)", defaultValue: 80, range: "1..100", submitOnChange: true,
                description: "Only logs the daily max if it reaches at least this percentage of the historical average. Lower this (e.g., 50%) to be more forgiving, raise it (e.g., 80%) to strictly filter out cloudy days."
                
            input "peakClearLux", "number", title: "Expected Peak Clear-Sky Brightness (Lux)", defaultValue: 10000, submitOnChange: true,
                description: "<b>[DYNAMIC]</b> This value acts as your manual starting fallback. Once Smart Learning gathers enough data, the app will automatically update this setting for you."
        }
        
        section("<b>7. Proportional Dimming Setup</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Maps the virtual dimmer level using a logarithmic curve for natural eye perception.</div>"
            input "heavyStormLux", "number", title: "Heavy Storm Limit (Lux)", defaultValue: 500,
                description: "If lux drops to this level, the dimmer hits Max Brightness."
            input "maxDimLevel", "number", title: "Max Brightness Level (%)", defaultValue: 100, range: "1..100"
            input "minDimLevel", "number", title: "Min Brightness Level (%)", defaultValue: 20, range: "1..100",
                description: "The starting brightness when it just barely crosses the Overcast threshold."
            input "nightDimLevel", "number", title: "Nighttime Brightness Level (%)", defaultValue: 100, range: "1..100"
        }
        
        section("<b>8. Math and Algorithm (Under the Hood)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:14px; color:#333; margin-bottom: 5px;'><b>1. Smart Threshold Scaling</b></div>" +
                      "<div style='font-size:13px; color:#555;'>Instead of static thresholds, the app calculates a proportional ratio. It divides your Base Overcast Threshold by 10,000 (the standard clear sky assumption) and multiplies it by your Expected Peak Brightness. This ensures the threshold adapts up or down as the seasons change.</div>"
                      
            paragraph "<div style='font-size:14px; color:#333; margin-bottom: 5px; margin-top: 15px;'><b>2. Auto-Curve (Dynamic Clear Target)</b></div>" +
                      "<div style='font-size:13px; color:#555;'>The recovery threshold uses two trigonometric arcs to prevent yo-yoing in the evening or winter:<br>• <b>Time-of-Day Arc:</b> A sine wave spanning from sunrise to sunset, peaking at 1.0 during solar noon.<br>• <b>Seasonal Arc:</b> A cosine wave calculating the current Day of the Year offset against the Summer Solstice. The base threshold is mathematically multiplied by these curves to lower the recovery requirement naturally as the sun drops.</div>"

            paragraph "<div style='font-size:14px; color:#333; margin-bottom: 5px; margin-top: 15px;'><b>3. Logarithmic Dimming</b></div>" +
                      "<div style='font-size:13px; color:#555;'>Human eyes perceive light intensity logarithmically, not linearly. The proportional dimmer uses a <code>Math.log10</code> equation to calculate the space between your Overcast limit and Storm limit. This maps the dimmer level smoothly, ensuring natural-feeling brightness transitions rather than jarring, linear jumps.</div>"

            paragraph "<div style='font-size:14px; color:#333; margin-bottom: 5px; margin-top: 15px;'><b>4. Predictive Cloud Event Detection</b></div>" +
                      "<div style='font-size:13px; color:#555;'>The app tracks a rolling 'Recent Peak'. If the ambient lux drops by more than 30% from this peak within a short time window (scaled based on your sensor's reporting interval), it logs a 'Potential Clouding' event. It actively waits to see if the light recovers before officially triggering the Overcast targets, allowing it to differentiate between a passing cloud and a true storm.</div>"

            paragraph "<div style='font-size:14px; color:#333; margin-bottom: 5px; margin-top: 15px;'><b>5. Smart Learning Bad-Weather Filter</b></div>" +
                      "<div style='font-size:13px; color:#555;'>To learn your true solar baseline, the app logs the maximum lux reading every day. When the sun sets, it evaluates this peak against a percentage of your historical rolling average. If the peak was lower than your configured Minimum Threshold (meaning it was likely a cloudy or rainy day), the data point is discarded. Only clear days are averaged into the permanent memory array.</div>"
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

String getHumanReadableStatus() {
    if (isSystemPaused()) return "<span style='color:red;'><b>PAUSED (Master Switch Off)</b></span>"
    if (!isModeAllowed()) return "<span style='color:orange;'><b>PAUSED (Restricted Mode)</b></span>"
    if (state.isNight && useAstro) return "<span style='color:purple;'><b>Nighttime Hard-Lock Active</b></span>"
    
    def sState = state.currentCondition ?: "Awaiting Sync..."
    def pendingMsg = ""
    if (state.pendingOvercast) pendingMsg = " (Verifying Weather Event...)"
    if (state.pendingClear) pendingMsg = " (Verifying Clear...)"
    
    def color = "black"
    if (sState == "Overcast") color = "blue"
    if (sState == "Clear" || sState == "Assumed Clear (Boot)") color = "green"
    if (sState == "Nighttime") color = "purple"
    
    return "<span style='color:${color};'><b>${sState}</b></span><span style='color:#555; font-size: 12px;'>${pendingMsg}</span>"
}

def installed() {
    log.info "Advanced Overcast Detector Installed."
    initialize()
}

def updated() {
    log.info "Advanced Overcast Detector Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.historyLog = state.historyLog ?: []
    state.luxHistory = state.luxHistory ?: []
    state.cloudHistory = state.cloudHistory ?: []
    state.peakLuxHistory = state.peakLuxHistory ?: [] 
    state.dailyMaxLux = state.dailyMaxLux ?: 0
    state.activeCloudEvent = null
    state.currentCondition = "Evaluating..."
    state.pendingOvercast = false
    state.pendingClear = false
    state.isNight = false
    state.lastLuxCheckTime = now()
    state.lastLuxValue = null
    state.dipReason = null
    
    state.recentPeakLux = null
    state.recentPeakTime = null
    
    if (!state.roomData) state.roomData = [:]
    def configuredRooms = numRooms ?: 0
    for (int i = 1; i <= configuredRooms; i++) {
        if (!state.roomData["${i}"]) {
            state.roomData["${i}"] = [dailyMax: 0, peakHistory: [], currentSetpoint: settings["roomBaseLux_${i}"] ?: 0]
        }
        def rLux = settings["roomLux_${i}"]
        if (rLux) subscribe(rLux, "illuminance", roomLuxHandler)
    }
    
    if (primaryLuxSensor) subscribe(primaryLuxSensor, "illuminance", luxHandler)
    if (auxLuxSensor1) subscribe(auxLuxSensor1, "illuminance", luxHandler)

    subscribe(location, "mode", modeHandler)
   
    if (useAstro) {
        scheduleAstro()
        schedule("0 1 0 * * ?", scheduleAstro)
        checkInitialAstroState()
    } else {
        state.isNight = false
    }
    
    runEvery15Minutes(logGraphData)
    forceImmediateEvaluation()
}

def appButtonHandler(btn) {
    if (btn == "refreshBtn") {
        log.info "Manual Data Refresh Requested."
    }
    if (btn == "clearHistoryBtn") {
        state.historyLog = []
        log.info "Application history cleared."
    }
}

def getSmartOvercastThreshold() {
    def baseOver = overcastThreshold ?: 2000
    if (!useSmartThresholds) return baseOver
    def currentPeak = peakClearLux ?: 10000
    def ratioBase = (currentPeak > 0) ? currentPeak : 10000 
    def ratio = baseOver / 10000.0 
    return (currentPeak * ratio).toInteger()
}

def getSmartClearThreshold() {
    def baseClear = clearThreshold ?: 4000
    if (!useSmartThresholds) return baseClear
    def currentPeak = peakClearLux ?: 10000
    def ratio = baseClear / 10000.0
    return (currentPeak * ratio).toInteger()
}

def closeActiveCloudEvent() {
    if (!state.activeCloudEvent) return
    
    def endTime = now()
    def durationSecs = ((endTime - state.activeCloudEvent.startTime) / 1000).toInteger() 
    def durationStr = ""
    
    if (durationSecs < 60) {
        durationStr = "${durationSecs} sec"
    } else {
        def mins = (durationSecs / 60).toInteger()
        def secs = durationSecs % 60
        durationStr = "${mins}m ${secs}s"
    }
    
    def maxDrop = state.activeCloudEvent.startLux - state.activeCloudEvent.minLux
    def eventTime = new Date(state.activeCloudEvent.startTime).format("MM/dd HH:mm", location.timeZone)
    
    def newEvent = [
        time: eventTime,
        duration: durationStr,
        drop: "${maxDrop} lx",
        minLux: "${state.activeCloudEvent.minLux} lx"
    ]
   
    if (!state.cloudHistory) state.cloudHistory = []
    state.cloudHistory.add(0, newEvent)
    if (state.cloudHistory.size() > 15) state.cloudHistory = state.cloudHistory.take(15)
    
    state.activeCloudEvent = null
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
    
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    log.info "HISTORY: [${timestamp}] ${cleanMsg}"
}

def roomLuxHandler(evt) {
    if (state.currentCondition == "Overcast" || state.isNight) return 
    
    def devId = evt.device.id
    def lux = evt.value.toInteger()
    def configuredRooms = numRooms ?: 0
    
    for (int i = 1; i <= configuredRooms; i++) {
        def rLux = settings["roomLux_${i}"]
        if (rLux && rLux.id == devId) {
            def shades = settings["roomShades_${i}"]
            def lights = settings["roomLights_${i}"]
            
            def shadesClosed = shades ? shades.any { it.currentValue("contact") == "closed" } : false
            def lightsOn = lights ? lights.any { it.currentValue("switch") == "on" } : false
            
            if (!shadesClosed && !lightsOn) {
                def rData = state.roomData ? state.roomData["${i}"] : null
                if (rData) {
                    if (lux > (rData.dailyMax ?: 0)) {
                        rData.dailyMax = lux
                    }
                }
            }
            break 
        }
    }
}

def getAggregateLux() {
    if (!primaryLuxSensor) return 0
    if (!averageSensors) return primaryLuxSensor.currentValue("illuminance")?.toInteger() ?: 0
    
    def sensors = [primaryLuxSensor, auxLuxSensor1].findAll { it != null }
    def values = sensors.collect { it.currentValue("illuminance")?.toInteger() ?: 0 }
    
    if (values.size() == 0) return 0
    // With only max 2 sensors, we just perform a direct average without dropping outliers
    return (values.sum() / values.size()).toInteger()
}

def getDynamicClearThreshold() {
    def baseClear = getSmartClearThreshold()
    def baseOvercast = getSmartOvercastThreshold()
    
    if (!useDynamicClear) return baseClear
    
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunrise || !sunInfo.sunset) return baseClear
    
    def nowTime = new Date().time
    def sunrise = sunInfo.sunrise.time
    def sunset = sunInfo.sunset.time
    
    if (nowTime < sunrise || nowTime > sunset) return baseClear
    
    def totalDaylightMillis = sunset - sunrise
    def currentDaylightMillis = nowTime - sunrise
    def dayPercentage = currentDaylightMillis / totalDaylightMillis
    def timeMultiplier = Math.sin(dayPercentage * Math.PI)
    
    def cal = Calendar.getInstance()
    def dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    def seasonalOffset = ((dayOfYear - 172) / 365.0) * (Math.PI * 2)
    def seasonMultiplier = 0.7 + (0.3 * Math.cos(seasonalOffset))
    
    def dynamicLimit = (baseClear * timeMultiplier * seasonMultiplier).toInteger()
    def safeMinimum = baseOvercast + 500
    
    return Math.max(dynamicLimit, safeMinimum)
}

def logGraphData() {
    if (!primaryLuxSensor) return
    
    def timestamp = new Date().format("HH:mm", location.timeZone)
    def nowTime = now()
    
    def s1 = primaryLuxSensor?.currentValue("illuminance")?.toInteger() ?: 0
    def s2 = auxLuxSensor1?.currentValue("illuminance")?.toInteger() ?: 0
    
    def expectedLux = 0
   
    def sunInfo = getSunriseAndSunset()
    if (sunInfo && sunInfo.sunrise && sunInfo.sunset) {
        def sr = sunInfo.sunrise.time
        def ss = sunInfo.sunset.time
        
        if (nowTime >= sr && nowTime <= ss) {
            def fraction = (nowTime - sr) / (ss - sr)
            def peak = peakClearLux ?: 10000
            expectedLux = (peak * Math.sin(fraction * Math.PI)).toInteger()
        }
    }
    
    if (!state.luxHistory) state.luxHistory = []
    state.luxHistory.add([time: timestamp, s1: s1, s2: s2, expected: expectedLux])
    
    if (state.luxHistory.size() > 96) {
        state.luxHistory = state.luxHistory.drop(1)
    }
}

def generateLocalLineChart() {
    if (!state.luxHistory || state.luxHistory.size() < 2) return "<i>Collecting data for local graph...</i>"
    
    def width = 600
    def height = 250
    def maxLux = state.luxHistory.collect { 
        [it.s1 ?: (it.lux ?: 0), it.s2 ?: 0, it.expected ?: 0].max() 
    }.max() ?: 1000
    
    if (maxLux < 1000) maxLux = 1000

    def svg = "<svg width='100%' viewBox='0 0 ${width} ${height}' style='background:#fcfcfc; border:1px solid #ccc; border-radius:5px;'>"
   
    svg += "<line x1='0' y1='${height/2}' x2='${width}' y2='${height/2}' stroke='#eee' stroke-width='1'/>"
    svg += "<text x='5' y='15' font-size='11' fill='#888' font-family='sans-serif'>${maxLux} lx</text>"
    svg += "<text x='5' y='${height - 5}' font-size='11' fill='#888' font-family='sans-serif'>0 lx</text>"

    def xStep = width / (state.luxHistory.size() - 1)

    def makePolyline = { dataKey, color, strokeDash ->
        def pts = []
        state.luxHistory.eachWithIndex { pt, i ->
            def x = (i * xStep).toInteger()
            def val = pt[dataKey] ?: (dataKey == 's1' ? (pt.lux ?: 0) : 0)
            def y = height - ((val / maxLux) * height).toInteger()
            pts << "${x},${y}"
        }
        return "<polyline points='${pts.join(' ')}' fill='none' stroke='${color}' stroke-width='2' stroke-dasharray='${strokeDash}' stroke-linejoin='round'/>"
    }

    if (primaryLuxSensor) svg += makePolyline("s1", "rgba(54,162,235,1)", "none")
    if (auxLuxSensor1) svg += makePolyline("s2", "rgba(255,99,132,1)", "none")
    
    svg += makePolyline("expected", "rgba(255,159,64,0.8)", "5,5")
    svg += "</svg>"
    
    def legend = "<div style='font-size:12px; margin-top:8px; text-align:center; font-family:sans-serif;'>"
    if (primaryLuxSensor) legend += "<span style='color:rgba(54,162,235,1); font-weight:bold; margin-right:10px;'>■ Primary</span>"
    if (auxLuxSensor1) legend += "<span style='color:rgba(255,99,132,1); font-weight:bold; margin-right:10px;'>■ Aux 1</span>"
    legend += "<span style='color:rgba(255,159,64,1); font-weight:bold;'>■ Expected</span>"
    legend += "</div>"

    return "<div style='width:100%; max-width:600px; margin:auto;'>${svg}${legend}</div>"
}

def generateRoomBarGraph() {
    def configuredRooms = numRooms ?: 0
    if (configuredRooms == 0) return ""

    def html = "<div style='margin-top: 20px; font-family: sans-serif;'>"
    html += "<div style='font-weight: bold; font-size: 14px; margin-bottom: 10px;'>24-Hour Max Lux by Room</div>"
    
    def maxGlobal = 100 
    for (int i = 1; i <= configuredRooms; i++) {
        def rData = state.roomData ? state.roomData["${i}"] : null
        if (rData && rData.dailyMax > maxGlobal) maxGlobal = rData.dailyMax
    }

    for (int i = 1; i <= configuredRooms; i++) {
        def rName = settings["roomName_${i}"] ?: "Room ${i}"
        def rData = state.roomData ? state.roomData["${i}"] : null
        def dMax = rData?.dailyMax ?: 0
        def pct = (dMax / maxGlobal) * 100
        
        html += "<div style='margin-top: 8px; font-size: 12px; font-weight: bold; color: #444;'>${rName} <span style='font-weight:normal; color:#888;'>(${dMax} lx)</span></div>"
        html += "<div style='width: 100%; height: 16px; background: #e0e0e0; border-radius: 4px; overflow: hidden; border: 1px solid #ccc;'>"
        html += "<div style='width: ${pct}%; height: 100%; background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%); transition: width 0.5s ease;'></div>"
        html += "</div>"
    }
    html += "</div>"
    return html
}

def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def isModeAllowed() {
    if (!activeModes) return true
    return activeModes.contains(location.mode)
}

def modeHandler(evt) {
    if (!isModeAllowed()) {
        addToHistory("GLOBAL: Hub entered restricted mode (${evt.value}). Pausing detector.")
        unschedule("triggerOvercast")
        unschedule("triggerClear")
        state.pendingOvercast = false
        state.pendingClear = false
    } else {
        addToHistory("GLOBAL: Hub entered allowed mode (${evt.value}). Resuming detector.")
        evaluateLuxCondition()
    }
}

def updateDimmerLevel(currentLux) {
    if (!targetDimmer || isSystemPaused() || !isModeAllowed() || state.isNight) return

    def overLimit = getSmartOvercastThreshold()
    def stormLimit = heavyStormLux ?: 500
    def maxLvl = maxDimLevel ?: 100
    def minLvl = minDimLevel ?: 20

    def targetLevel = minLvl
    
    if (currentLux <= stormLimit) {
        targetLevel = maxLvl
    } else if (currentLux >= overLimit) {
        targetLevel = minLvl
    } else {
        def luxRange = overLimit - stormLimit
        def levelRange = maxLvl - minLvl
        def luxDrop = overLimit - currentLux
        
        def curve = Math.log10(1 + 9 * (luxDrop / luxRange))
        def calcLevel = minLvl + (curve * levelRange)
        targetLevel = Math.round(calcLevel).toInteger()
    }

    def currentDimmerLevel = targetDimmer.currentValue("level")?.toInteger() ?: 0
    def currentDimmerState = targetDimmer.currentValue("switch")

    if (currentDimmerState != "on" || Math.abs(currentDimmerLevel - targetLevel) > 2) {
        addToHistory("DIMMER: Dynamic adjustment to ${targetLevel}% (Lux: ${currentLux}).")
        targetDimmer.setLevel(targetLevel)
    }
}

def luxHandler(evt) {
    evaluateLuxCondition()
}

def forceImmediateEvaluation() {
    if (!primaryLuxSensor || isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    def lux = getAggregateLux()
    def overLimit = getSmartOvercastThreshold()
    def clearLimit = getDynamicClearThreshold()
    def prevState = state.currentCondition
    
    if (lux <= overLimit) {
        state.currentCondition = "Overcast"
        if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
        if (targetDimmer) updateDimmerLevel(lux)
    } else if (lux >= clearLimit) {
        state.currentCondition = "Clear"
        if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
        if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
    } else {
        state.currentCondition = "Assumed Clear (Boot)"
        addToHistory("SYSTEM BOOT: Booted inside deadband (${lux} lx). Assuming clear state to prevent stuck lights.")
        if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
        if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
    }
}

def evaluateLuxCondition() {
    if (isSystemPaused() || !isModeAllowed()) return
    if (state.isNight && useAstro) return 
    
    if (!primaryLuxSensor) return
    
    def lux = getAggregateLux()
    def overLimit = getSmartOvercastThreshold()
    def clearLimit = getDynamicClearThreshold()
    def debounceSecs = (debounceTime ?: 10) * 60
    def intervalMins = sensorInterval ?: 15
    
    def timeNow = now()
    def timeDeltaMins = state.lastLuxCheckTime ? (timeNow - state.lastLuxCheckTime) / 60000 : 0
    def luxDrop = state.lastLuxValue ? (state.lastLuxValue - lux) : 0
    
    if (!state.dailyMaxLux || lux > state.dailyMaxLux) {
        state.dailyMaxLux = lux
    }
    
    def stalePeakMillis = (intervalMins * 3) * 60000 
    def detectionWindowMins = (intervalMins * 2) 
    
    if (!state.recentPeakLux) {
        state.recentPeakLux = lux
        state.recentPeakTime = timeNow
    }

    if (!state.activeCloudEvent) {
        if (lux > state.recentPeakLux || (timeNow - state.recentPeakTime > stalePeakMillis)) {
            state.recentPeakLux = lux
            state.recentPeakTime = timeNow
        }
    }

    def dropFromPeak = state.recentPeakLux - lux
    def peakDropPercentage = state.recentPeakLux > 0 ? (dropFromPeak / state.recentPeakLux) : 0
    def timeFromPeakMins = (timeNow - state.recentPeakTime) / 60000

    if ((peakDropPercentage >= 0.30 || dropFromPeak > 15000) && timeFromPeakMins <= detectionWindowMins) {
        if (!state.activeCloudEvent) {
            state.activeCloudEvent = [startTime: timeNow, startLux: state.recentPeakLux, minLux: lux]
            state.dipReason = "Potential Clouding"
            addToHistory("ANALYSIS: Potential Clouding detected. Tracking as active weather event.")
        } else if (lux < state.activeCloudEvent.minLux) {
            state.activeCloudEvent.minLux = lux 
        }
    } else if (luxDrop > 0 && timeDeltaMins > intervalMins && lux <= overLimit && !state.pendingOvercast) {
        state.dipReason = "Gradual Fade"
    }
    
    if (state.activeCloudEvent) {
        def recoveryAmount = lux - state.activeCloudEvent.minLux
        def totalDrop = state.activeCloudEvent.startLux - state.activeCloudEvent.minLux
        
        if (lux >= state.activeCloudEvent.startLux || recoveryAmount >= (totalDrop * 0.50)) {
            closeActiveCloudEvent()
            addToHistory("ANALYSIS: Potential Clouding event passed and logged to history.")
            state.recentPeakLux = lux 
            state.recentPeakTime = timeNow
        }
    }
    
    state.lastLuxValue = lux
    state.lastLuxCheckTime = timeNow

    if (state.currentCondition == "Overcast") {
        updateDimmerLevel(lux)
    }
    
    if (lux <= overLimit && state.currentCondition != "Overcast") {
        if (state.pendingClear) {
            unschedule("triggerClear")
            state.pendingClear = false
            if (state.dipReason == "Potential Clouding") {
                addToHistory("Sky darkened back to ${lux} lx rapidly. Passing cloud verified. Canceled Clear.")
            } else {
                addToHistory("Sky darkened back to ${lux} lx. Canceled Clear Verification.")
            }
        }
        
        if (!state.pendingOvercast) {
            state.pendingOvercast = true
            runIn(debounceSecs, "triggerOvercast", [overwrite: true])
            def causeStr = (state.dipReason == "Potential Clouding") ? "Monitoring for Storm vs Cloud..." : "Monitoring for Overcast..."
            addToHistory("Lux dropped to ${lux}. Starting ${(debounceSecs/60).toInteger()}m verification. ${causeStr}")
        }
    } 
    else if (lux >= clearLimit && state.currentCondition != "Clear" && state.currentCondition != "Assumed Clear (Boot)") {
        if (state.pendingOvercast) {
            unschedule("triggerOvercast")
            state.pendingOvercast = false
            if (state.dipReason == "Potential Clouding") {
                addToHistory("Sky brightened to ${lux} lx rapidly. Logged as POTENTIAL CLOUDING. Canceled Overcast Verification.")
            } else {
                addToHistory("Sky brightened back to ${lux} lx. Canceled Overcast Verification.")
            }
        }
        
        if (!state.pendingClear) {
            state.pendingClear = true
            runIn(debounceSecs, "triggerClear", [overwrite: true])
            addToHistory("Lux rose to ${lux}. Starting ${(debounceSecs/60).toInteger()}m Clear verification timer.")
        }
    }
    else if (lux > overLimit && lux < clearLimit) {
        if (state.pendingOvercast) {
            unschedule("triggerOvercast")
            state.pendingOvercast = false
            addToHistory("Lux recovered into deadband (${lux} lx). Canceled Overcast verification.")
        }
        if (state.pendingClear) {
            unschedule("triggerClear")
            state.pendingClear = false
            addToHistory("Lux dropped into deadband (${lux} lx). Canceled Clear verification.")
        }
    }
}

def triggerOvercast() {
    if (isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    def prevState = state.currentCondition
    state.pendingOvercast = false
    state.currentCondition = "Overcast"
    
    if (state.dipReason == "Potential Clouding") {
        addToHistory("CONFIRMED: Lux remained low. Logged as STORM or HEAVY OVERCAST. Activating targets.")
    } else {
        addToHistory("CONFIRMED: Conditions remained Overcast. Activating targets.")
    }
    
    if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
    if (targetDimmer && primaryLuxSensor) updateDimmerLevel(getAggregateLux())
}

def triggerClear() {
    if (isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
 
    def prevState = state.currentCondition
    state.pendingClear = false
    state.currentCondition = "Clear"
    
    addToHistory("CONFIRMED: Conditions remained Clear. Deactivating targets.")
    
    if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
    if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
}

def scheduleAstro() {
    def sunInfo = getSunriseAndSunset()
    if (sunInfo && sunInfo.sunrise) {
        def sRiseOffset = sunriseOffset ? sunriseOffset.toInteger() : 0
        def sunriseTime = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
        if (sunriseTime.after(new Date())) runOnce(sunriseTime, executeSunrise, [overwrite: true])
    }
    
    if (sunInfo && sunInfo.sunset) {
        def sSetOffset = sunsetOffset ? sunsetOffset.toInteger() : 0
        def sunsetTime = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
        if (sunsetTime.after(new Date())) runOnce(sunsetTime, executeSunset, [overwrite: true])
    }
}

def checkInitialAstroState() {
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset || !sunInfo.sunrise) return
    def now = new Date()
    
    def sRiseOffset = sunriseOffset ? sunriseOffset.toInteger() : 0
    def sSetOffset = sunsetOffset ? sunsetOffset.toInteger() : 0
    def actualSunrise = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
    def actualSunset = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
    
    if (now >= actualSunset || now <= actualSunrise) {
        state.isNight = true
        state.currentCondition = "Nighttime"
        addToHistory("ASTRO BOOT: Currently nighttime. Applying Nighttime Logic.")
        enforceNightAction()
    } else {
        state.isNight = false
    }
}

def executeSunset() {
    if (!useAstro) return
  
    if (state.activeCloudEvent) closeActiveCloudEvent()
    
    def reqDays = (settings.learningDaysReq ?: "30").toInteger()
    def thresholdPct = (settings.learningThresholdPct ?: 80) / 100.0
    def rejectRuleText = "${settings.learningThresholdPct ?: 80}% minimum threshold rule"
    
    // --- SMART LEARNING: EVALUATE OUTDOOR DAILY MAX & AUTO-UPDATE INPUT ---
    if (useSmartLearning && state.dailyMaxLux && state.dailyMaxLux > 100) {
        def baseline = peakClearLux ?: 10000
        def lowerBound = baseline * thresholdPct 
        
        if (state.peakLuxHistory.size() < 3 || state.dailyMaxLux >= lowerBound) {
            state.peakLuxHistory.add(state.dailyMaxLux)
            
            def overflow = state.peakLuxHistory.size() - reqDays
            if (overflow > 0) state.peakLuxHistory = state.peakLuxHistory.drop(overflow)
            
            // Re-calculate the average peak
            def newLearnedPeak = (state.peakLuxHistory.sum() / state.peakLuxHistory.size()).toInteger()
            
            // DYNAMICALLY OVERWRITE THE USER SETTING IN THE UI
            app.updateSetting("peakClearLux", [type: "number", value: newLearnedPeak])
            
            log.info "SMART LEARNING (OUTDOOR): Daily max of ${state.dailyMaxLux} lx added. Peak Brightness setting updated to ${newLearnedPeak} lx."
        } else {
            log.info "SMART LEARNING (OUTDOOR): Daily max of ${state.dailyMaxLux} lx rejected (${rejectRuleText})."
        }
    }
    state.dailyMaxLux = 0
    
    // --- SMART LEARNING: EVALUATE INDOOR ROOMS & EXPORT HUB VARIABLES ---
    def configuredRooms = numRooms ?: 0
    for (int i = 1; i <= configuredRooms; i++) {
        def rData = state.roomData ? state.roomData["${i}"] : null
        if (useSmartLearning && rData && rData.dailyMax && rData.dailyMax > 10) { 
            def basePeak = settings["roomPeakLux_${i}"] ?: 1000
            def baseTarget = settings["roomBaseLux_${i}"] ?: 100
            
            def rBaseline = rData.peakHistory.size() > 0 ? (rData.peakHistory.sum() / rData.peakHistory.size()) : basePeak
            def rLowerBound = rBaseline * thresholdPct
            
            if (rData.peakHistory.size() < 3 || rData.dailyMax >= rLowerBound) {
                rData.peakHistory.add(rData.dailyMax)
               
                def overflow = rData.peakHistory.size() - reqDays
                if (overflow > 0) rData.peakHistory = rData.peakHistory.drop(overflow)
                
                log.info "SMART LEARNING (ROOM ${i}): Daily max of ${rData.dailyMax} lx added."
            } else {
                log.info "SMART LEARNING (ROOM ${i}): Daily max of ${rData.dailyMax} lx rejected (${rejectRuleText})."
            }
            
            def currentPeak = rData.peakHistory.size() >= reqDays ? (rData.peakHistory.sum() / rData.peakHistory.size()) : basePeak
            def ratio = baseTarget / basePeak
            def newSetpoint = (currentPeak * ratio).toInteger()
            rData.currentSetpoint = newSetpoint
            
            def varName = settings["roomVar_${i}"]
            if (varName) {
                try {
                    setGlobalVar(varName, newSetpoint)
                    log.info "ROOM ${i}: Exported new setpoint (${newSetpoint}) to Hub Variable: ${varName}."
                } catch (e) {
                    log.error "ROOM ${i}: Failed to set Hub Variable '${varName}'. Ensure it is created and spelled correctly in your hub settings."
                }
            }
        }
        if (rData) rData.dailyMax = 0
    }
    
    state.isNight = true
    state.currentCondition = "Nighttime"
    state.pendingOvercast = false
    state.pendingClear = false
    unschedule("triggerOvercast")
    unschedule("triggerClear")
    
    addToHistory("ASTRO: Sun has set. Suspending lux detection and applying Nighttime Logic.")
    enforceNightAction()
}

def enforceNightAction() {
    if (isModeAllowed() && !isSystemPaused()) {
        def action = nightAction ?: "Turn ON (Dark/Overcast)"
        
        if (action == "Turn OFF (Clear/Night)") {
            if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
            if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
            addToHistory("ASTRO: Forced Virtual Targets OFF for nighttime.")
        } 
        else if (action == "Turn ON (Dark/Overcast)") {
            if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
            if (targetDimmer) {
                def nLevel = nightDimLevel ?: 100
                targetDimmer.setLevel(nLevel)
            }
            addToHistory("ASTRO: Forced Virtual Targets ON for nighttime.")
        }
    }
}

def executeSunrise() {
    if (!useAstro) return
    
    state.isNight = false
    addToHistory("ASTRO: Sun has risen. Resuming Overcast detection.")
    forceImmediateEvaluation()
}
