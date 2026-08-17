/**
 * Advanced Lighting Circadian Rhythm Manager 2.0
 */

definition(
    name: "Advanced Lighting Circadian Rhythm Manager 2.0",
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
        
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #f39c12;'>" +
                      "<b>Engine Status:</b> ${statusExplanation}</div>"

            // Gather Core Metrics
            def currentCT = state.calculatedCT ?: "--"
            def currentLevel = state.calculatedLevel ?: "--"
            
            def overrideModeStr = overrideMode ?: "Normal"
            
            // Sun Position Math for Dashboard
            def sunData = getSunriseAndSunset()
            def riseTime = sunData.sunrise ? sunData.sunrise.format("h:mm a", location.timeZone) : "--"
            def setTime = sunData.sunset ? sunData.sunset.format("h:mm a", location.timeZone) : "--"
        
            def phase = "Nighttime"
            if (sunData.sunrise && sunData.sunset) {
                def nowMs = now()
        
                def riseMs = sunData.sunrise.time
                def setMs = sunData.sunset.time
                def solarNoonMs = riseMs + ((setMs - riseMs) / 2)
                
                if (nowMs >= riseMs && nowMs < solarNoonMs) phase = "Morning (Ramping Up)"
                else if (nowMs >= solarNoonMs && nowMs < setMs) phase = "Afternoon (Ramping Down)"
                else if (nowMs >= setMs) phase = "Post-Sunset (Locked to Night Settings)"
                else phase = "Pre-Sunrise (Locked to Night Settings)"
            }

            // Unified Dashboard HTML
            def levelOutputDisplay = "<span style='color:red;'>Not Configured</span>"
            if (levelOutputType == "Virtual Dimmer Device" && outDimmer) levelOutputDisplay = outDimmer.displayName
            else if (levelOutputType == "Hub Variable" && levelVariable) levelOutputDisplay = levelVariable
            else if (levelOutputType == "Both") levelOutputDisplay = "${levelVariable ?: 'Missing Var'} & ${outDimmer ? outDimmer.displayName : 'Missing Device'}"

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; font-weight:bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th colspan="2">Real-Time Lighting Metrics</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Calculated Color Temp</td><td class="dash-val" style="color:#e67e22; font-size: 16px;">${currentCT}K</td></tr>
                    <tr><td class="dash-hl">Calculated Dimmer Level</td><td class="dash-val" style="color:#f1c40f; font-size: 16px;">${currentLevel}%</td></tr>
                    <tr><td class="dash-hl">Active Override Mode</td><td class="dash-val">${overrideModeStr}</td></tr>
                    
                    <tr><td colspan="2" class="dash-subhead">Solar Positioning & Weather</td></tr>
                
                    <tr><td class="dash-hl">Current Solar Phase</td><td class="dash-val">${phase}</td></tr>
                    <tr><td class="dash-hl">Local Sunrise</td><td class="dash-val">${riseTime}</td></tr>
                    <tr><td class="dash-hl">Local Sunset</td><td class="dash-val">${setTime}</td></tr>
                    
                    <tr><td colspan="2" class="dash-subhead">System Connections</td></tr>
   
                    <tr><td class="dash-hl">Target CT Variable</td><td class="dash-val">${ctVariable ?: "<span style='color:red;'>Not Configured</span>"}</td></tr>
                    <tr><td class="dash-hl">Target Level Output</td><td class="dash-val">${levelOutputDisplay}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>App Control & Master Kill Switch</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The master toggle for the application. If disabled, the app stops updating the Hub Variables completely, allowing you to manually control your lights without interference.</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
        }

        section("<b>1. Manual Overrides</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Instantly locks the color temperature and dimmer to a specific value, bypassing the sun logic entirely. Useful for tasks requiring bright white light at night, or forcing cozy lighting during a dark storm.</div>"
            input "overrideMode", "enum", title: "<b>Operating Mode</b>", options: ["Normal (Track Sun)", "Force Cool & Bright (6500K / Max Level)", "Force Warm & Dim (2500K / Min Level)"], required: true, defaultValue: "Normal (Track Sun)", submitOnChange: true
        }

        section("<b>2. Output Destinations</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The app continuously calculates the perfect Kelvin and Brightness percentage and injects them into your chosen destinations.</div>"
            
            input "ctVariable", "text", title: "Exact Name of Color Temp Hub Variable (Required)", required: true
            
            paragraph "<b>Dimmer Level Destination:</b>"
            input "levelOutputType", "enum", title: "Where should the app send the calculated Dimmer Level?", options: ["Hub Variable", "Virtual Dimmer Device", "Both"], required: true, defaultValue: "Hub Variable", submitOnChange: true
            
            if (levelOutputType == "Hub Variable" || levelOutputType == "Both") {
                input "levelVariable", "text", title: "Exact Name of Dimmer Level Hub Variable", required: true
            }
            if (levelOutputType == "Virtual Dimmer Device" || levelOutputType == "Both") {
                input "outDimmer", "capability.switchLevel", title: "Select Virtual Dimmer Device", required: true, multiple: false
            }
        }

        section("<b>3. Circadian Boundaries & Curves</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Defines the absolute floor and ceiling for your color temperature and brightness. Also allows you to invert the dimming logic so lights get dimmer as the evening approaches.</div>"
            
            paragraph "<b>Color Temperature Range:</b>"
            input "minCT", "number", title: "Minimum Warmth (Kelvin) - Used at Night", required: true, defaultValue: 2500
            input "maxCT", "number", title: "Maximum Coolness (Kelvin) - Used at Solar Noon", required: true, defaultValue: 6500
            
            paragraph "<b>Dimmer Level Range:</b>"
            input "minLevel", "number", title: "Minimum Brightness (%)", required: true, defaultValue: 10
            input "maxLevel", "number", title: "Maximum Brightness (%)", required: true, defaultValue: 100
            input "dimCurveType", "enum", title: "Dimmer Tracking Logic", options: [
                "Standard (Bright Midday, Dim Night)", 
                "Inverted (Dim Midday, Bright Night)"
            ], required: true, defaultValue: "Standard (Bright Midday, Dim Night)"
        }

        section("<b>4. Update Frequency</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> How often the app recalculates the sun's position and updates your outputs. 15 minutes provides a smooth, unnoticeable transition throughout the day.</div>"
            input "updateInterval", "enum", title: "Calculation Interval", options: ["1":"Every 1 Minute", "5":"Every 5 Minutes", "15":"Every 15 Minutes", "30":"Every 30 Minutes"], required: false, defaultValue: "15"
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
    }
}

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    state.calculatedCT = null
    state.calculatedLevel = null
    state.lastOverrideState = false
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", enableSwitchHandler)
    
    // Schedule the heartbeat sweep
    def interval = updateInterval ?: "15"
    if (interval == "1") runEvery1Minute(routineSweep)
    else if (interval == "5") runEvery5Minutes(routineSweep)
    else if (interval == "15") runEvery15Minutes(routineSweep)
    else if (interval == "30") runEvery30Minutes(routineSweep)
    
    logAction("Circadian Engine Initialized. Sun tracking active.")
    evaluateSystem()
}

def enableSwitchHandler(evt) { 
    if (evt.value == "off") {
        logAction("Circadian App Paused via Master Switch.")
    } else {
        evaluateSystem() 
    }
}

def routineSweep() {
    evaluateSystem()
}

def getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "The application is suspended via the Master Switch."
    if (overrideMode == "Force Cool & Bright (6500K / Max Level)") return "<span style='color:blue;'><b>Manual Override:</b></span> Locked to 6500K and Max Brightness."
    if (overrideMode == "Force Warm & Dim (2500K / Min Level)") return "<span style='color:#d35400;'><b>Manual Override:</b></span> Locked to 2500K and Min Brightness."
    
    return "Tracking normally. Calculating color and brightness curves based on solar position."
}

def evaluateSystem() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    if (!ctVariable) return // CT Var is the only absolute requirement
    
    def targetCT = 2700 // Fallback default
    def targetLevel = 100 // Fallback default
    
    // 1. Handle Manual Overrides
    if (overrideMode == "Force Cool & Bright (6500K / Max Level)") {
        targetCT = 6500
        targetLevel = maxLevel != null ? maxLevel : 100
        if (txtEnable && (state.calculatedCT != targetCT || state.calculatedLevel != targetLevel)) {
            logAction("Override Active: Forcing 6500K / Max Level.")
        }
    } 
    else if (overrideMode == "Force Warm & Dim (2500K / Min Level)") {
        targetCT = 2500
        targetLevel = minLevel != null ? minLevel : 10
        if (txtEnable && (state.calculatedCT != targetCT || state.calculatedLevel != targetLevel)) {
            logAction("Override Active: Forcing 2500K / Min Level.")
        }
    } 
    // 2. Handle Normal Sun Tracking
    else {
        targetCT = calculateNaturalCT()
        targetLevel = calculateNaturalLevel()
    }
    
    // Determine if changes meet the threshold to avoid network/mesh spam
    def ctDiff = state.calculatedCT != null ? Math.abs((state.calculatedCT as Integer) - targetCT) : 999
    def levelDiff = state.calculatedLevel != null ? Math.abs((state.calculatedLevel as Integer) - targetLevel) : 999
    
    def isOverride = (overrideMode == "Force Cool & Bright (6500K / Max Level)" || overrideMode == "Force Warm & Dim (2500K / Min Level)")
    
    // Force an immediate update if a Manual Override is toggled on/off
    def forceUpdate = (isOverride != state.lastOverrideState)
    state.lastOverrideState = isOverride
    
    // 3. Push to Outputs (Only if thresholds are met or a forceUpdate is triggered)
    if (forceUpdate || ctDiff >= 100) {
        state.calculatedCT = targetCT
        def success = setGlobalVar(ctVariable, targetCT)
        if (success) {
            logAction("BMS Command -> CT Variable '${ctVariable}' updated to ${targetCT}K")
        } else {
            logAction("ERROR: Hubitat rejected the update for '${ctVariable}'. Check exact spelling and ensure it is a Number variable.")
        }
    }
    
    if (forceUpdate || levelDiff >= 5) {
        state.calculatedLevel = targetLevel
        
        // Push to Hub Variable if selected
        if (levelOutputType == "Hub Variable" || levelOutputType == "Both") {
            if (levelVariable) {
                def success = setGlobalVar(levelVariable, targetLevel)
                if (success) {
                    logAction("BMS Command -> Level Variable '${levelVariable}' updated to ${targetLevel}%")
                } else {
                    logAction("ERROR: Hubitat rejected the update for '${levelVariable}'. Check exact spelling and ensure it is a Number variable.")
                }
            }
        }
        
        // Push to Virtual Dimmer if selected
        if (levelOutputType == "Virtual Dimmer Device" || levelOutputType == "Both") {
            if (outDimmer && outDimmer.currentValue("level") != targetLevel) {
                outDimmer.setLevel(targetLevel)
                logAction("BMS Command -> Virtual Dimmer '${outDimmer.displayName}' updated to ${targetLevel}%")
            }
        }
    }
}

def calculateNaturalCT() {
    def sunData = getSunriseAndSunset()
    def minTemp = minCT != null ? minCT : 2500
    def maxTemp = maxCT != null ? maxCT : 6500
    
    if (!sunData.sunrise || !sunData.sunset) {
        logInfo("Could not retrieve Hubitat solar data. Defaulting to max coolness.")
        return maxTemp
    }
    
    def nowMs = now()
    def riseMs = sunData.sunrise.time
    def setMs = sunData.sunset.time
    def solarNoonMs = riseMs + ((setMs - riseMs) / 2)
    def calculatedTemp = minTemp
    
    if (nowMs < riseMs) {
        calculatedTemp = minTemp
    } 
    else if (nowMs >= riseMs && nowMs <= solarNoonMs) {
        def percentage = (nowMs - riseMs) / (solarNoonMs - riseMs)
        calculatedTemp = minTemp + ((maxTemp - minTemp) * percentage)
    } 
    else if (nowMs > solarNoonMs && nowMs <= setMs) {
        def percentage = (nowMs - solarNoonMs) / (setMs - solarNoonMs)
        calculatedTemp = maxTemp - ((maxTemp - minTemp) * percentage)
    } 
    else if (nowMs > setMs) {
        calculatedTemp = minTemp
    }
    
    return (Math.round(calculatedTemp / 50.0) * 50).toInteger()
}

def calculateNaturalLevel() {
    def sunData = getSunriseAndSunset()
    def rawMin = minLevel != null ? minLevel : 10
    def rawMax = maxLevel != null ? maxLevel : 100
    
    // Determine bounds based on inverse selection
    def isStandard = (dimCurveType == "Standard (Bright Midday, Dim Night)")
    def nightLevel = isStandard ? rawMin : rawMax
    def noonLevel = isStandard ? rawMax : rawMin
    
    if (!sunData.sunrise || !sunData.sunset) {
        return nightLevel
    }
    
    def nowMs = now()
    def riseMs = sunData.sunrise.time
    def setMs = sunData.sunset.time
    def solarNoonMs = riseMs + ((setMs - riseMs) / 2)
    def calculatedLvl = nightLevel
    
    if (nowMs < riseMs) {
        calculatedLvl = nightLevel
    } 
    else if (nowMs >= riseMs && nowMs <= solarNoonMs) {
        def percentage = (nowMs - riseMs) / (solarNoonMs - riseMs)
        calculatedLvl = nightLevel + ((noonLevel - nightLevel) * percentage)
    } 
    else if (nowMs > solarNoonMs && nowMs <= setMs) {
        def percentage = (nowMs - solarNoonMs) / (setMs - solarNoonMs)
        calculatedLvl = noonLevel - ((noonLevel - nightLevel) * percentage)
    } 
    else if (nowMs > setMs) {
        calculatedLvl = nightLevel
    }
    
    return Math.round(calculatedLvl).toInteger()
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}
def logInfo(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}
