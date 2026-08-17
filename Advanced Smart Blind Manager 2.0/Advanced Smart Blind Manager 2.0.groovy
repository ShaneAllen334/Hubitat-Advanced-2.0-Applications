/**
 * Advanced Smart Blind Manager 2.0
 * 
 */
definition(
    name: "Advanced Smart Blind Manager 2 0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def globalStatus = (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            if (dndSwitch && dndSwitch.currentValue("switch") == "on") globalStatus += " <span style='color: #e83e8c; font-weight:bold;'>(DND LOCKED)</span>"
            if (state.summerLatchActive) globalStatus += " <span style='color: #3498db; font-weight:bold;'>(COOLING LATCH ACTIVE)</span>"
            if (state.thermalOverrideActive) globalStatus += " <span style='color: #e67e22; font-weight:bold;'>(THERMAL OVERRIDE ACTIVE)</span>"
            if (state.morningGraceExpireTime && state.morningGraceExpireTime > new Date().time) globalStatus += " <span style='color: #2ecc71; font-weight:bold;'>(GRACE PERIOD ACTIVE)</span>"

            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #3498db;'>" +
                      "<b>System Status:</b> ${globalStatus}</div>"

            if (numRooms > 0) {
                def outTemp = outdoorTempSensor ? "${outdoorTempSensor.currentValue('temperature')}°" : "--°"
                def outLux = outdoorLuxSensor ? "${outdoorLuxSensor.currentValue('illuminance')} lx" : "-- lx"
                def avgTemp = getAverageIndoorTemp()
                def hvac = mainThermostat ? mainThermostat.currentValue("thermostatOperatingState")?.capitalize() : "--"
                
                def sunPosStr = "--"
                if (enableSolarTracking && state.currentSunPos) {
                    def compassDir = getCompassDirection(state.currentSunPos.azimuth)
                    sunPosStr = "Az: ${state.currentSunPos.azimuth}° (${compassDir}) | El: ${state.currentSunPos.elevation}°"
                }

                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 20%; }
                    .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                    .dash-val { text-align: left !important; padding-left: 15px !important; }
                </style>
                <table class="dash-table">
                    <thead><tr><th colspan="4">Live Environmental Metrics</th></tr></thead>
                    <tbody>
                        <tr><td class="dash-hl">Outdoor Weather</td><td colspan="3" class="dash-val">${outTemp} | ${outLux}</td></tr>
                        <tr><td class="dash-hl">House Avg Temp</td><td colspan="3" class="dash-val">${avgTemp}°</td></tr>
                        <tr><td class="dash-hl">HVAC State</td><td colspan="3" class="dash-val">${hvac}</td></tr>
                        <tr><td class="dash-hl">Solar Position</td><td colspan="3" class="dash-val" style="color: #d35400; font-weight:bold;">${sunPosStr}</td></tr>
                        
                        <tr><td colspan="4" class="dash-subhead">Zone Breakdown</td></tr>
                        <tr style="background-color: #f1f3f5; font-weight: bold;">
                            <td>Room</td><td>Environment</td><td>Verified State</td><td>Target & Locks</td>
                        </tr>
                """
                
                def now = new Date().time

                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    def dirDisplay = ""
                    
                    def dirType = settings["dirType_${i}"] ?: "Compass (N/E/S/W)"
                    if (dirType == "Exact Azimuth (°)" && settings["windowAzimuth_${i}"] != null) {
                        dirDisplay = "Facing: ${settings["windowAzimuth_${i}"]}°"
                    } else if (dirType == "Compass (N/E/S/W)" && settings["direction_${i}"]) {
                        dirDisplay = "Facing: ${settings["direction_${i}"]}"
                    } else {
                        dirDisplay = "Facing: Unset"
                    }
                    
                    def blind = settings["blind_${i}"]
                    def rNameDisplay = "<b>${rName}</b><br><span style='font-size: 11px; color: #555;'>${dirDisplay}</span>"
                    
                    if (!blind) {
                        dashHTML += "<tr><td>${rNameDisplay}</td><td style='color: #888;'>-</td><td style='color: #888;'>Not Configured</td><td>-</td></tr>"
                        continue
                    }
                    
                    def tSensor = settings["tempSensor_${i}"]
                    def lSensor = settings["luxSensor_${i}"]
                    def rTemp = tSensor ? "${tSensor.currentValue('temperature')}°" : "--°"
                    def rLux = lSensor ? "${lSensor.currentValue('illuminance')} lx" : "-- lx"
                    def envDisplay = "<b>${rTemp}</b><br><span style='font-size: 11px; color: #555;'>${rLux}</span>"
                    
                    def vState = state.verifiedState?."${i}"?.toUpperCase() ?: "UNKNOWN"
                    def stateColor = (vState == "OPEN") ? "green" : (vState == "CLOSED" ? "blue" : "black")
                 
                    def tState = state.targetState?."${i}"?.toUpperCase() ?: "UNKNOWN"
                    def tReason = state.targetReason?."${i}" ?: "Awaiting Initial Sync..."
                    
                    def locks = []
                    if (state.manualHold?."${i}") {
                        def holdExpiry = state.manualHoldExpireTime?."${i}" ?: 0
                        if (holdExpiry > now) {
                            def timeLeftSecs = ((holdExpiry - now) / 1000).toInteger()
                            def timeLeftMins = (timeLeftSecs / 60).toInteger()
                            def remSecs = timeLeftSecs % 60
                            locks << "<span style='color: red; font-weight: bold;'>Manual Hold (${timeLeftMins}m ${remSecs}s)</span>"
                        } else {
                            locks << "<span style='color: red; font-weight: bold;'>Manual Hold</span>"
                        }
                    }
                    
                    if (state.windLock?."${i}") locks << "<span style='color: orange; font-weight: bold;'>Storm Shield</span>"
                    if (settings["goodNightSwitch_${i}"]?.currentValue("switch") == "on") locks << "<span style='color: darkblue; font-weight: bold;'>Nap Lock</span>"
                    if (dndSwitch && dndSwitch.currentValue("switch") == "on") locks << "<span style='color: #e83e8c; font-weight: bold;'>DND Lock</span>"
                    
                    // Anti-Yo-Yo Cooldown Timer
                    def lastMove = state.lastAutoMoveTime["${i}"] ?: 0
                    def debounceMillis = (environmentalDebounce != null ? environmentalDebounce.toInteger() : 15) * 60000
                    if ((now - lastMove) < debounceMillis) {
                        def timeLeftSecs = ((debounceMillis - (now - lastMove)) / 1000).toInteger()
                        def timeLeftMins = (timeLeftSecs / 60).toInteger()
                        def remSecs = timeLeftSecs % 60
                        locks << "<span style='color: #888; font-size: 11px;'>Cooldown: ${timeLeftMins}m ${remSecs}s</span>"
                    }
                    
                    // Master Timeout / Retry Timer
                    def cmdStart = state.commandStartTime["${i}"] ?: 0
                    def timeoutMins = settings["retryTimeoutMinutes"] != null ? settings["retryTimeoutMinutes"].toInteger() : 15
                    def timeoutMillis = timeoutMins * 60000
                    if (state.targetState["${i}"] != state.verifiedState["${i}"] && (now - cmdStart) < timeoutMillis && cmdStart > 0 && tReason != "TIMEOUT FAILED") {
                         def timeLeftSecs = ((timeoutMillis - (now - cmdStart)) / 1000).toInteger()
                         def timeLeftMins = (timeLeftSecs / 60).toInteger()
                         def remSecs = timeLeftSecs % 60
                         locks << "<span style='color: #d35400; font-size: 11px;'>Syncing: ${timeLeftMins}m ${remSecs}s left</span>"
                    }

                    def lockStr = locks ? locks.join("<br>") : ""
                    def targetDisplay = "<b>${tState}</b><br><span style='font-size: 11px; color: #555;'>${tReason}</span>"
                    if (lockStr) targetDisplay += "<br>${lockStr}"
                    
                    dashHTML += "<tr><td>${rNameDisplay}</td><td>${envDisplay}</td><td style='color: ${stateColor}; font-weight: bold;'>${vState}</td><td>${targetDisplay}</td></tr>"
                }

                dashHTML += "</tbody></table>"
                paragraph dashHTML

            } else {
                paragraph "<i>Please configure rooms in Section 6 below to populate the dashboard.</i>"
            }
        }

        section("<b>Manual Controls & Quick Actions</b>", hideable: true) {
            input "btnForceAllOpen", "button", title: "🔼 Force ALL Blinds OPEN (Engage Manual Hold)"
            input "btnForceAllClose", "button", title: "🔽 Force ALL Blinds CLOSE (Engage Manual Hold)"
            input "btnReleaseAllHolds", "button", title: "❌ Release All Manual Holds Now (And Sync House)"
            input "btnResetSummerLatch", "button", title: "❄️ Reset Daily Average Temp Cooling Latch"
            input "btnForceSync", "button", title: "🔄 Force System Re-evaluation & Sync Now"
        }
  
        if (enableTelemetryTracking) {
            section("<b>Hardware Health & Telemetry</b>", hideable: true, hidden: true) {
                def telText = "<table class='dash-table' style='margin-top:0px;'>"
                telText += "<thead><tr><th>Room ID</th><th>24H Traffic<br><span style='font-size:10px; font-weight:normal;'>(Commands / Moves)</span></th><th>24H Anomalies<br><span style='font-size:10px; font-weight:normal;'>(Retries / Wiggles / Fails)</span></th><th>All-Time Reliability Score</th></tr></thead><tbody>"
                
                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def tData = state.telemetry?."${i}"
                    if (!tData || !tData.today) continue
                    
                    def cmdsT = tData.today.commands ?: 0
                    def opnT = tData.today.opens ?: 0
                    def clsT = tData.today.closes ?: 0
                    def movesT = opnT + clsT
                    
                    def retT = tData.today.retries ?: 0
                    def wigT = tData.today.wiggles ?: 0
                    def failT = tData.today.timeouts ?: 0
                    
                    // Health Calculator (Overall)
                    def cmdsA = tData.overall?.commands ?: 0
                    def failA = tData.overall?.timeouts ?: 0
                    def wigA = tData.overall?.wiggles ?: 0
                    def retA = tData.overall?.retries ?: 0
                    
                    def penalty = (failA * 100) + (wigA * 2) + (retA * 1)
                    def maxPoints = cmdsA > 0 ? cmdsA * 100 : 100
                    def healthPct = cmdsA > 0 ? Math.max(0, Math.round(100 - ((penalty / maxPoints) * 100))) : 100
                    
                    def healthBadge = ""
                    if (cmdsA == 0) healthBadge = "<span style='color:#888; font-weight:bold;'>N/A (No Data)</span>"
                    else if (healthPct >= 95) healthBadge = "<span style='color:green; font-weight:bold;'>${healthPct}% (Excellent)</span>"
                    else if (healthPct >= 85) healthBadge = "<span style='color:#8bc34a; font-weight:bold;'>${healthPct}% (Good)</span>"
                    else if (healthPct >= 70) healthBadge = "<span style='color:orange; font-weight:bold;'>${healthPct}% (Degraded)</span>"
                    else healthBadge = "<span style='color:red; font-weight:bold;'>${healthPct}% (Critical)</span>"

                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    
                    def trafficStr = "<b>${cmdsT}</b> Cmds <br> <span style='color:gray; font-size:11px;'>(${movesT} Verified Moves)</span>"
                    
                    def anomalyColor = (failT > 0 || wigT > 0 || retT > 2) ? "color: #d32f2f; font-weight:bold;" : "color: #333;"
                    def errorStr = "<span style='${anomalyColor}'>${retT}R / ${wigT}W / ${failT}F</span>"
                    
                    telText += "<tr><td><b>${rName}</b></td><td>${trafficStr}</td><td>${errorStr}</td><td>${healthBadge}</td></tr>"
                }
                telText += "</tbody></table>"
                paragraph telText
            
                input "btnResetTelemetry", "button", title: "🧹 Reset Telemetry Data"
            }
        }
    
        section("<b>Recent Action History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "btnResetActionHistory", "button", title: "🧹 Clear Action History"
            
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.collect { entry -> 
                    def splitIdx = entry.indexOf(']') + 1
                    if (splitIdx > 0) {
                        return "<b>${entry.substring(0, splitIdx)}</b> ${entry.substring(splitIdx + 1)}"
                    } else return entry
                }.join("<br>")
                
                paragraph "<span style='font-size: 13px; font-family: monospace; background-color: #f9f9f9; padding: 10px; display: block; border: 1px solid #ddd; border-radius: 4px;'>${logText}</span>"
            } else {
                paragraph "<i>No history available yet. The log will populate as the app takes action.</i>"
            }
        }
        
        section("<b>1. Global App Control & Core Setup</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Base structural settings for the blind engine.</div>"
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false, description: "The Global Pause. ON = Application Runs. OFF = Application Paused."
            input "dndSwitch", "capability.switch", title: "Global Do Not Disturb (DND) Override Switch", required: false, description: "ON = Close all blinds & lock system. OFF = Resume automation."
            input "numRooms", "number", title: "Number of Rooms to Configure (1-12)", required: true, defaultValue: 1, range: "1..12", submitOnChange: true
            input "retryTimeoutMinutes", "number", title: "Max Sync Retry Duration (Minutes)", defaultValue: 15, required: true
            input "manualHoldTimeout", "number", title: "Auto-Release Manual Hold (Minutes, 0 = Never)", defaultValue: 120, required: true
            input "masterBlind", "capability.windowShade", title: "Master Bond Device (For 'Open All' / 'Close All')", required: false
        }
        
        section("<b>2. Base Operating Modes</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Define how your home's Location Modes interact with global shade positions and holds.</div>"
            input "activeModes", "mode", title: "Master Active Modes (App only runs in these)", multiple: true, required: false
            input "openOnModes", "mode", title: "Modes that trigger Global Open", multiple: true, required: false
            input "closeOnModes", "mode", title: "Modes that trigger Global Close", multiple: true, required: false
            
            paragraph "<b>Manual Hold Release Triggers</b>"
            input "autoReleaseHoldModes", "mode", title: "Specific Modes that Auto-Release Manual Holds", multiple: true, required: false
            
            input "morningGracePeriod", "number", title: "Morning Grace Period (Minutes)", defaultValue: 60, required: true, description: "Blocks environmental locks for X minutes after a morning Open routine fires to allow natural light in."
        }
        
        section("<b>3. Time & Solar Integration</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Connects the physical sun position and time of day to your blinds.</div>"
            input "enableSolarTracking", "bool", title: "<b>Enable Solar Geometry Engine</b>", defaultValue: false, submitOnChange: true, description: "Calculates precise sun position relative to your configured windows."
            
            paragraph "<hr>"
            input "useSunriseSunset", "bool", title: "<b>Enable Sunrise/Sunset Automations</b>", defaultValue: false, submitOnChange: true
            
            if (useSunriseSunset) {
                input "releaseHoldSunrise", "bool", title: "Auto-Release Manual Holds at Sunrise?", defaultValue: false
                input "sunriseOffset", "number", title: "Sunrise Offset (Minutes, +/-)", defaultValue: 0
                input "sunriseModes", "mode", title: "Modes allowed for Auto-Sunrise Open", multiple: true, required: false
                
                input "releaseHoldSunset", "bool", title: "Auto-Release Manual Holds at Sunset?", defaultValue: false
                input "sunsetOffset", "number", title: "Sunset Offset (Minutes, +/-)", defaultValue: 0
                input "sunsetModes", "mode", title: "Modes allowed for Auto-Sunset/Time Close", multiple: true, required: false
                input "sunsetDeadband", "number", title: "Sunset Deadband / Motor Saver (Minutes)", defaultValue: 30
                
                input "darkArrivalLockout", "bool", title: "Enable Dark Arrival Lockout?", defaultValue: true
            }
        }
        
        section("<b>4. Exterior Weather & Master Overrides</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Hardware sensors that force the home to lockdown to protect from storms or extreme solar radiation.</div>"
            input "windSensor", "capability.sensor", title: "Weather Station / Wind Sensor", required: false
            input "windThreshold", "number", title: "Storm Shield Wind Threshold (mph)", defaultValue: 15
    
            input "outdoorLuxSensor", "capability.illuminanceMeasurement", title: "Master Outdoor Lux Sensor", required: false
            input "highSolarRadiationThreshold", "number", title: "High Solar Radiation Threshold (Lux)", defaultValue: 10000
            input "luxHysteresis", "number", title: "Solar Radiation Hysteresis (Deadband Lux)", defaultValue: 500
            
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor", required: false
            input "outdoorHighTempThreshold", "number", title: "Outdoor High Temp Lockout (°F)", defaultValue: 92
        }
        
        section("<b>5. Thermal Defense & BMS Interlocks</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Links the shades with your HVAC to actively defend against heat intrusion or harvest free solar heating.</div>"
            input "mainThermostat", "capability.thermostat", title: "Main Thermostat (Syncs blinds with AC/Heat states)", required: false
            
            input "environmentalDebounce", "number", title: "Environmental Anti-Yo-Yo Hold Time (Minutes)", defaultValue: 15
            input "tempHysteresis", "decimal", title: "Temperature Hysteresis (Deadband °)", defaultValue: 1.0
       
            input "activeCoolingDefense", "bool", title: "<b>Active Cooling Defense</b> (Close sun-facing blinds when AC cools)", defaultValue: true
            
            paragraph "<b>BMS Synergy & HVAC Enhancements</b>"
            input "enableBlowerScavenge", "bool", title: "<b>Enable HVAC Blower Scavenging</b> (Runs HVAC fan 15m when sun-facing blinds close to mix air)", defaultValue: true
            input "enablePredictiveLatch", "bool", title: "<b>Enable Predictive Thermal Latch</b> (Closes blinds before house gets hot based on morning outdoor temps)", defaultValue: true
            input "enableWinterCeiling", "bool", title: "<b>Enable Winter Deadband Ceiling</b> (Prevents winter sun from accidentally triggering the AC)", defaultValue: true
            
            paragraph "<hr>"
            
            input "thermalOverrideSwitch", "capability.switch", title: "Thermal Lockdown Override Switch (Turn ON to bypass for the day)", required: false
            input "thermalOverrideButton", "capability.pushableButton", title: "Thermal Lockdown Override Button (Push to bypass for the day)", required: false
            if (thermalOverrideButton) {
                input "thermalOverrideButtonNum", "number", title: "Override Button Number", defaultValue: 1, required: false
            }

            input "summerEnergyMode", "bool", title: "<b>Summer Mode</b> (Close shades to block heat)", defaultValue: false, submitOnChange: true
            if (summerEnergyMode) {
                input "summerTempThreshold", "number", title: "Summer Indoor Temp Threshold (°)", defaultValue: 75
                input "summerLatchEnable", "bool", title: "Enable Full Cooling Latch (Keep ALL blinds closed for the day if Average Temp threshold is hit)", defaultValue: true
                input "thermalLatchIndicator", "capability.switch", title: "Thermal Latch Indicator (Virtual Switch)", required: false
                input "summerOutdoorTempThreshold", "number", title: "Summer Outdoor Temp Trigger (Preemptive °)", defaultValue: 82, required: false
                input "summerAllowedModes", "mode", title: "Modes allowed for Summer Mode", multiple: true, required: false
            }
            
            input "winterHeatingMode", "bool", title: "<b>Winter Mode</b> (Open shades to harvest free solar heat)", defaultValue: false, submitOnChange: true
            if (winterHeatingMode) {
                input "winterTempThreshold", "number", title: "Winter Indoor Temp Threshold (Open if below this °)", defaultValue: 68
                input "winterOutdoorTempThreshold", "number", title: "Winter Outdoor Temp Trigger (Preemptive °)", defaultValue: 45, required: false
                input "winterMaxOutdoorTemp", "number", title: "Winter Max Outdoor Temp Lockout (°)", defaultValue: 75, required: false
                input "winterAllowedModes", "mode", title: "Modes allowed for Winter Mode", multiple: true, required: false
            }
        }
        
        section("<b>6. Advanced Settings & Toggles</b>", hideable: true, hidden: true) {
            input "enableTelemetryTracking", "bool", title: "Enable Hardware Telemetry & Health Tracking Dashboard", defaultValue: false, submitOnChange: true
        }
        
        if (numRooms > 0 && numRooms <= 12) {
            section("<b>7. Individual Zone Configurations</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:13px; color:#555;'>Select a room to configure its hardware, geometry, and sensors.</div>"
                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def roomNum = i
                    def rName = settings["roomName_${i}"] ?: "Zone ${i}"
                    href(name: "roomHref${i}", page: "roomPage", params: [roomNum: i], title: "⚙️ Configure ${rName}", description: "Click to edit room parameters.")
                }
            }
        }
    }
}

def roomPage(params) {
    def rNum = params?.roomNum ?: state.currentRoom ?: 1
    state.currentRoom = rNum
    def currentName = settings["roomName_${rNum}"] ?: "Zone ${rNum}"
    
    dynamicPage(name: "roomPage", title: "${currentName} Setup", install: false, uninstall: false, previousPage: "mainPage") {
        
        section("<b>Room Identification</b>") {
            input "roomName_${rNum}", "text", title: "Custom Room Name", required: false, defaultValue: "Zone ${rNum}", submitOnChange: true
        }
        
        section("<b>Control Devices & Geometry</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Map your specific blind hardware and define which direction the window faces so the Solar Engine can track it.</div>"
            
            input "blind_${rNum}", "capability.windowShade", title: "Blind / Shade Device (Bond)", required: false
            input "blindSensor_${rNum}", "capability.contactSensor", title: "Blind State Sensor (Manual override detection)", required: false
            input "sensorDebounce_${rNum}", "number", title: "Sensor Stabilization Time (Seconds)", defaultValue: 15, required: false, description: "Increase this if ceiling fans or drafts cause false manual overrides."
            
            paragraph "<hr>"
            input "dirType_${rNum}", "enum", title: "Direction Configuration Type", options: ["Compass (N/E/S/W)", "Exact Azimuth (°)", "None"], defaultValue: "Compass (N/E/S/W)", submitOnChange: true
            
            if (settings["dirType_${rNum}"] == "Exact Azimuth (°)") {
                input "windowAzimuth_${rNum}", "number", title: "Window Azimuth Degree (0-360)", required: true, defaultValue: 180
            } else if (settings["dirType_${rNum}"] == "Compass (N/E/S/W)") {
                input "direction_${rNum}", "enum", title: "Window Facing Direction", options: ["North", "South", "East", "West"], required: true
            }
        }
        
        section("<b>Physical Buttons / Remotes</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><i><b>Button Actions:</b><br>• <b>Push:</b> Toggles blind (Open/Close) & engages Manual Hold.<br>• <b>Hold:</b> Releases Manual Hold & resumes automation.</i></div>"
            input "roomButton_${rNum}", "capability.pushableButton", title: "Room Button Controller", required: false
            input "buttonNumber_${rNum}", "number", title: "Button Number", defaultValue: 1, required: false
            input "buttonModes_${rNum}", "mode", title: "Allowed Modes for Button", multiple: true, required: false
        }
        
        section("<b>Sensors & Triggers</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Local sensors used to override global logic based on exact room conditions.</div>"
            input "tempSensor_${rNum}", "capability.temperatureMeasurement", title: "Indoor Temperature Sensor", required: false
            input "luxSensor_${rNum}", "capability.illuminanceMeasurement", title: "Indoor Lux (Light) Sensor", required: false
            input "contactSensor_${rNum}", "capability.contactSensor", title: "Window Open/Close Sensor (Prevents closing on open windows)", required: false
        }
        
        section("<b>Overrides (Hard-Locks)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Switches that instantly force the blind to close and ignore all automation until turned off.</div>"
            input "goodNightSwitch_${rNum}", "capability.switch", title: "Nap Time / Good Night Hard-Lock Switch", required: false
        }
    }
}

def installed() {
    log.info "Smart Blind Controller Installed."
    initialize()
}

def updated() {
    log.info "Smart Blind Controller Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.targetState = state.targetState ?: [:]
    state.targetReason = state.targetReason ?: [:]
    state.verifiedState = state.verifiedState ?: [:]
    state.manualHold = state.manualHold ?: [:]
    state.manualHoldExpireTime = state.manualHoldExpireTime ?: [:]
    state.lastAutoMoveTime = state.lastAutoMoveTime ?: [:] 
    state.windLock = state.windLock ?: [:]
    state.historyLog = state.historyLog ?: []
    state.telemetry = state.telemetry ?: [:]
    state.commandStartTime = state.commandStartTime ?: [:]
    
    // Wipe daily locks on initialize so they don't carry over during a power outage/reboot
    state.summerLatchActive = false
    state.coolingLock = [:]
    state.thermalOverrideActive = false
    state.morningGraceExpireTime = state.morningGraceExpireTime ?: 0
    updateThermalIndicator(false)
    
    if (enableSolarTracking && !state.currentSunPos) {
        state.currentSunPos = calculateSolarPosition()
    }
    
    for (int i = 1; i <= 12; i++) {
        if (!state.telemetry["${i}"] || !state.telemetry["${i}"].today) {
            state.telemetry["${i}"] = [
                today: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
                overall: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
                history: []
            ]
        }
    }
    
    if (useSunriseSunset) {
        scheduleAstro()
        schedule("0 1 0 * * ?", scheduleAstro) 
    }
    
    if (enableSolarTracking) schedule("0 0/15 * * * ?", "evaluateSolarChanges")
    
    schedule("0 0 0 * * ?", "midnightReset") 
    runIn(10, "bootSync", [overwrite: true]) 
    
    subscribe(location, "mode", modeHandler)
    if (mainThermostat) subscribe(mainThermostat, "thermostatOperatingState", hvacHandler)
    
    if (dndSwitch) {
        subscribe(dndSwitch, "switch.on", dndSwitchOnHandler)
        subscribe(dndSwitch, "switch.off", dndSwitchOffHandler)
    }

    if (thermalOverrideSwitch) subscribe(thermalOverrideSwitch, "switch.on", thermalOverrideHandler)
    if (thermalOverrideButton) subscribe(thermalOverrideButton, "pushed", thermalOverrideBtnHandler)
    
    if (windSensor) subscribe(windSensor, "windSpeed", weatherHandler)
    if (outdoorLuxSensor) subscribe(outdoorLuxSensor, "illuminance", weatherHandler)
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", weatherHandler)
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["tempSensor_${i}"]) subscribe(settings["tempSensor_${i}"], "temperature", tempHandler)
        if (settings["luxSensor_${i}"]) subscribe(settings["luxSensor_${i}"], "illuminance", luxHandler)
        if (settings["contactSensor_${i}"]) subscribe(settings["contactSensor_${i}"], "contact", windowContactHandler)
        
        if (settings["roomButton_${i}"]) {
            subscribe(settings["roomButton_${i}"], "pushed", buttonPushedHandler)
            subscribe(settings["roomButton_${i}"], "held", buttonHeldHandler)
        }
        
        if (settings["goodNightSwitch_${i}"]) {
            subscribe(settings["goodNightSwitch_${i}"], "switch.on", hardLockOnHandler)
            subscribe(settings["goodNightSwitch_${i}"], "switch.off", hardLockOffHandler)
        }
        
        if (settings["blindSensor_${i}"]) subscribe(settings["blindSensor_${i}"], "contact", blindSensorHandler)
    }
}

def updateThermalIndicator(isActive) {
    if (settings["thermalLatchIndicator"]) {
        def currState = settings["thermalLatchIndicator"].currentValue("switch")
        if (isActive && currState != "on") {
            settings["thermalLatchIndicator"].on()
        } else if (!isActive && currState != "off") {
            settings["thermalLatchIndicator"].off()
        }
    }
}

def startMorningGracePeriod() {
    def graceMins = settings["morningGracePeriod"] != null ? settings["morningGracePeriod"].toInteger() : 60
    if (graceMins > 0) {
        state.morningGraceExpireTime = new Date().time + (graceMins * 60000)
        addToHistory("GLOBAL: Morning Grace Period activated. Thermal/Environmental locks suspended for ${graceMins} minutes.")
        runIn(graceMins * 60, "endMorningGracePeriod", [overwrite: true])
    }
}

def endMorningGracePeriod() {
    state.morningGraceExpireTime = 0
    addToHistory("GLOBAL: Morning Grace Period ended. Resuming normal environmental evaluation.")
    if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
}

// --- GLOBAL DND HANDLERS ---
def dndSwitchOnHandler(evt) {
    addToHistory("GLOBAL: Do Not Disturb engaged. Closing all blinds and locking system.")
    operateAllShades("close", true, "Global Do Not Disturb Active")
}

def dndSwitchOffHandler(evt) {
    addToHistory("GLOBAL: Do Not Disturb released. System resuming normal operations.")
    if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
}

// --- THERMAL OVERRIDE HANDLERS ---
def thermalOverrideHandler(evt) {
    addToHistory("THERMAL OVERRIDE: Switch activated. Bypassing thermal lockdown for the rest of the day.")
    activateThermalOverride()
}

def thermalOverrideBtnHandler(evt) {
    def targetBtn = settings["thermalOverrideButtonNum"]?.toString() ?: "1"
    if (evt.value == targetBtn) {
        addToHistory("THERMAL OVERRIDE: Button pushed. Bypassing thermal lockdown for the rest of the day.")
        activateThermalOverride()
    }
}

def activateThermalOverride() {
    state.thermalOverrideActive = true
    state.summerLatchActive = false
    state.coolingLock = [:]
    updateThermalIndicator(false)
    if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
}

// --- SOLAR GEOMETRY MATH ---
def calculateSolarPosition() {
    def lat = (location.latitude ?: 32.5393).toDouble()
    def lon = (location.longitude ?: -86.2078).toDouble()
    
    Calendar cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
    int dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    double hour = cal.get(Calendar.HOUR_OF_DAY) + (cal.get(Calendar.MINUTE) / 60.0)
    
    double declination = 23.45 * Math.sin(Math.toRadians((360.0 / 365.0) * (dayOfYear + 284.0)))
    
    double b = Math.toRadians((360.0 / 365.0) * (dayOfYear - 81.0))
    double eot = 9.87 * Math.sin(2 * b) - 7.53 * Math.cos(b) - 1.5 * Math.sin(b)
    
    double tzOffsetHours = (location.timeZone?.getOffset(cal.getTimeInMillis()) ?: 0) / 3600000.0
    double timeOffset = eot + (4.0 * lon) - (60.0 * tzOffsetHours)
    double tst = hour + (timeOffset / 60.0)
    
    double ha = (tst - 12.0) * 15.0
    
    double latRad = Math.toRadians(lat)
    double decRad = Math.toRadians(declination)
    double haRad = Math.toRadians(ha)
    
    double elevationRad = Math.asin(Math.sin(latRad) * Math.sin(decRad) + Math.cos(latRad) * Math.cos(decRad) * Math.cos(haRad))
    double elevation = Math.toDegrees(elevationRad)
    
    double azimuthRad = Math.acos((Math.sin(decRad) - Math.sin(elevationRad) * Math.sin(latRad)) / (Math.cos(elevationRad) * Math.cos(latRad)))
    double azimuth = Math.toDegrees(azimuthRad)
    
    if (Double.isNaN(azimuth)) azimuth = 180.0
    if (ha > 0) azimuth = 360.0 - azimuth
    
    return [azimuth: azimuth.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP), elevation: elevation.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)]
}

def getCompassDirection(azimuth) {
    def val = Math.floor((azimuth / 22.5) + 0.5).toInteger()
    def arr = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"]
    return arr[val % 16]
}

def evaluateSolarChanges() {
    if (isSystemPaused()) return
    state.currentSunPos = calculateSolarPosition()
    runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
}

// --- TELEMETRY HELPERS ---
def logTelemetryEvent(roomNum, eventType) {
    if (!enableTelemetryTracking) return
    if (!state.telemetry) state.telemetry = [:]
    
    if (!state.telemetry["${roomNum}"] || !state.telemetry["${roomNum}"].today) {
        state.telemetry["${roomNum}"] = [
            today: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
            overall: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
            history: []
        ]
    }
    
    state.telemetry["${roomNum}"].today[eventType] = (state.telemetry["${roomNum}"].today[eventType] ?: 0) + 1
    state.telemetry["${roomNum}"].overall[eventType] = (state.telemetry["${roomNum}"].overall[eventType] ?: 0) + 1
}

def get7DayMetric(roomNum, metric) {
    def tData = state.telemetry?."${roomNum}"
    if (!tData || !tData.today) return 0
    def total = (tData.today?."${metric}" ?: 0)
    if (tData.history) {
        tData.history.each { dayMap ->
            total += (dayMap?."${metric}" ?: 0)
        }
    }
    return total
}

// --- MANUAL HOLD TIMEOUT ENGINE ---
def engageManualHold(roomNum) {
    state.manualHold["${roomNum}"] = true
    
    def timeoutMinutes = settings["manualHoldTimeout"] != null ? settings["manualHoldTimeout"].toInteger() : 120
    if (timeoutMinutes > 0) {
        def expireTime = new Date().time + (timeoutMinutes * 60000)
        state.manualHoldExpireTime["${roomNum}"] = expireTime
        runIn(timeoutMinutes * 60, "autoReleaseHold", [data: [roomNum: roomNum], overwrite: false])
    } else {
        state.manualHoldExpireTime["${roomNum}"] = 0
    }
}

def autoReleaseHold(data) {
    def rNum = data.roomNum
    if (state.manualHold["${rNum}"]) {
        def expireTime = state.manualHoldExpireTime["${rNum}"] ?: 0
        def now = new Date().time
        if (now >= (expireTime - 5000)) { // 5 second buffer to allow precise firing
            state.manualHold["${rNum}"] = false
            state.manualHoldExpireTime["${rNum}"] = 0
            addToHistory("${getRoomName(rNum)}: Manual Hold auto-released after timeout. Syncing room state.")
            syncSingleRoom(rNum, true)
        }
    }
}

def midnightReset() {
    state.manualHold = [:]
    state.manualHoldExpireTime = [:]
    state.summerLatchActive = false
    state.coolingLock = [:]
    state.thermalOverrideActive = false
    
    updateThermalIndicator(false)
    
    if (thermalOverrideSwitch && thermalOverrideSwitch.currentValue("switch") == "on") {
        try { thermalOverrideSwitch.off() } catch(e) {}
    }
    
    if (enableTelemetryTracking && state.telemetry) {
        for (int i = 1; i <= 12; i++) {
            if (state.telemetry["${i}"] && state.telemetry["${i}"].today) {
                if (!state.telemetry["${i}"].history) state.telemetry["${i}"].history = []
                def todayCopy = state.telemetry["${i}"].today.clone()
                state.telemetry["${i}"].history.add(0, todayCopy)
                if (state.telemetry["${i}"].history.size() > 7) state.telemetry["${i}"].history = state.telemetry["${i}"].history.take(7)
                state.telemetry["${i}"].today = [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0]
            }
        }
    }
    
    if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
}

def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        log.info "Dashboard data manually refreshed by user."
    } else if (btn == "btnForceAllOpen") {
        for (int i = 1; i <= (numRooms as Integer); i++) {
            engageManualHold(i)
        }
        addToHistory("GLOBAL: 'Force ALL Open' button pressed. Engaging Manual Hold for the entire house.")
        operateAllShades("open", true, "App Global Force Open (Manual Hold)")
    } else if (btn == "btnForceAllClose") {
        for (int i = 1; i <= (numRooms as Integer); i++) {
            engageManualHold(i)
        }
        addToHistory("GLOBAL: 'Force ALL Close' button pressed. Engaging Manual Hold for the entire house.")
        operateAllShades("close", true, "App Global Force Close (Manual Hold)")
    } else if (btn == "btnReleaseAllHolds") {
        state.manualHold = [:]
        state.manualHoldExpireTime = [:]
        addToHistory("GLOBAL: 'Release All Holds' button pressed. Wiping holds and auto-syncing house.")
        if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    } else if (btn == "btnResetSummerLatch") {
        state.summerLatchActive = false
        state.coolingLock = [:]
        updateThermalIndicator(false)
        addToHistory("GLOBAL: 'Reset Cooling Latch' button pressed. Latch removed, syncing house.")
        if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    } else if (btn == "btnForceSync") {
        addToHistory("GLOBAL: 'Force Sync' button pressed. Re-evaluating and syncing all rooms.")
        if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
    } else if (btn == "btnResetTelemetry") {
        for (int i = 1; i <= 12; i++) {
            state.telemetry["${i}"] = [
                today: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
                overall: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
                history: []
            ]
        }
        addToHistory("TELEMETRY: Hardware health data has been manually wiped clean.")
    } else if (btn == "btnResetActionHistory") {
        state.historyLog = []
        addToHistory("GLOBAL: Action history manually cleared by user.")
    }
}

def executeSyncStaggered(data) {
    syncSingleRoom(data.roomNum, data.ignoreDebounce ?: false)
}

def bootSync() {
    addToHistory("SYSTEM REBOOT: Hub restarted. Auto-syncing the entire house to recover missed events.")
    if (!isSystemPaused()) {
        for (int i = 1; i <= (numRooms as Integer); i++) {
            def bSensor = settings["blindSensor_${i}"]
            if (bSensor) state.verifiedState["${i}"] = bSensor.currentValue("contact")
            else state.verifiedState["${i}"] = state.targetState["${i}"] ?: "unknown"
        }
        runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
    }
}

def windowContactHandler(evt) {
    def deviceId = evt.device.id
    def isClosed = evt.value == "closed"
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["contactSensor_${i}"]?.id == deviceId) {
            if (isClosed) {
                def rName = getRoomName(i)
                addToHistory("${rName}: Physical window was closed. Re-evaluating room state to recover any blocked actions.")
                runIn(5, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: true], overwrite: false])
            }
        }
    }
}

def getAverageIndoorTemp() {
    def totalTemp = 0.0
    def count = 0
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def tSensor = settings["tempSensor_${i}"]
        if (tSensor) {
            totalTemp += (tSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0)
            count++
        }
    }
    return count > 0 ? (totalTemp / count).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 70.0
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def tz = location.timeZone ?: TimeZone.getDefault()
    def timestamp = new Date().format("MM/dd HH:mm:ss", tz)
    
    state.historyLog.add(0, "[${timestamp}] ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
    log.info "HISTORY: [${timestamp}] ${msg}"
}

def getRoomName(rNum) {
    return settings["roomName_${rNum}"] ?: "Room ${rNum}"
}

def scheduleAstro() {
    def sunInfo = getSunriseAndSunset()
    if (sunInfo && sunInfo.sunrise) {
        def sRiseOffset = sunriseOffset != null ? sunriseOffset.toInteger() : 0
        def sunriseTime = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
        if (sunriseTime.after(new Date())) runOnce(sunriseTime, executeSunrise, [overwrite: true])
    }
    if (sunInfo && sunInfo.sunset) {
        def sSetOffset = sunsetOffset != null ? sunsetOffset.toInteger() : 0
        def sunsetTime = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
        if (sunsetTime.after(new Date())) runOnce(sunsetTime, executeSunset, [overwrite: true])
    }
}

def weatherHandler(evt) {
    if (isSystemPaused()) return
    def eventName = evt.name
    
    if (eventName == "windSpeed") {
        def currentWind = evt.value?.toBigDecimal() ?: 0.0
        def threshold = windThreshold != null ? windThreshold.toBigDecimal() : 15.0
        
        if (currentWind >= threshold) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                def contact = settings["contactSensor_${i}"]
                if (contact && contact.currentValue("contact") == "open" && !state.windLock["${i}"]) {
                    state.windLock["${i}"] = true
                    def rName = getRoomName(i)
                    addToHistory("STORM SHIELD ACTIVE: High wind (${currentWind} mph). Forced ${rName} blind open.")
                    singleBlindAction(i, "open", true, "Storm Shield (Wind: ${currentWind}mph >= ${threshold}mph)", true) 
                }
            }
        } else {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                if (state.windLock["${i}"]) {
                   state.windLock["${i}"] = false
                   addToHistory("${getRoomName(i)}: Storm Shield lock lifted. Restoring room state.")
                   runIn(i * 2, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: true], overwrite: false])
                }
            }
        }
    } else if (eventName == "illuminance") {
        def oldVal = state.lastOutLux ?: 0
        def newVal = evt.value?.toInteger() ?: 0
        if (Math.abs(newVal - oldVal) >= 200) {
            state.lastOutLux = newVal
            runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
        }
    } else if (eventName == "temperature") {
        def oldVal = state.lastOutTemp ?: 0.0
        def newVal = evt.value?.toBigDecimal() ?: 0.0
        if (Math.abs(newVal - oldVal) >= 0.5) {
            state.lastOutTemp = newVal
            runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
        }
    }
}

def hvacHandler(evt) {
    if (isSystemPaused()) return
    runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
}

def luxHandler(evt) {
    if (isSystemPaused()) return
    def oldVal = state.lastIndLux ?: 0
    def newVal = evt.value?.toInteger() ?: 0
    if (Math.abs(newVal - oldVal) >= 200) {
        state.lastIndLux = newVal
        runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    }
}

def tempHandler(evt) {
    if (isSystemPaused()) return
    def oldVal = state.lastIndTemp ?: 0.0
    def newVal = evt.value?.toBigDecimal() ?: 0.0
    if (Math.abs(newVal - oldVal) >= 0.5) {
        state.lastIndTemp = newVal
        runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    }
}

def isButtonAllowed(roomNum) {
    def allowedModes = settings["buttonModes_${roomNum}"]
    if (allowedModes && !allowedModes.contains(location.mode)) {
        addToHistory("${getRoomName(roomNum)}: Physical Button ignored. Hub is not in an allowed mode.")
        return false
    }
    return true
}

def buttonPushedHandler(evt) {
    def deviceId = evt.device.id
    def btnVal = evt.value
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def btn = settings["roomButton_${i}"]
        def targetBtn = settings["buttonNumber_${i}"]?.toString() ?: "1"
        
        if (btn && btn.id == deviceId && btnVal == targetBtn) {
            if (!isButtonAllowed(i)) return
            
            def rName = getRoomName(i)
            def currentState = state.verifiedState["${i}"] ?: state.targetState["${i}"] ?: "closed"
            def nextAction = (currentState == "open" || currentState == "opening") ? "close" : "open"
            
            addToHistory("${rName}: Physical Button PUSHED. Toggling blind to ${nextAction.toUpperCase()} and engaging Manual Hold.")
            engageManualHold(i)
            singleBlindAction(i, nextAction, true, "Physical Button Toggle", true) 
        }
    }
}

def buttonHeldHandler(evt) {
    def deviceId = evt.device.id
    def btnVal = evt.value
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def btn = settings["roomButton_${i}"]
        def targetBtn = settings["buttonNumber_${i}"]?.toString() ?: "1"
        
        if (btn && btn.id == deviceId && btnVal == targetBtn) {
            if (!isButtonAllowed(i)) return
            
            def rName = getRoomName(i)
            if (state.manualHold["${i}"]) {
                addToHistory("${rName}: Physical Button HELD. Releasing Manual Hold and syncing room state.")
                state.manualHold["${i}"] = false
                state.manualHoldExpireTime["${i}"] = 0
                syncSingleRoom(i, true)
            } else {
                addToHistory("${rName}: Physical Button HELD, but no manual hold was active. Ignoring.")
            }
        }
    }
}

def modeHandler(evt) {
    def currentMode = evt.value
    
    if (autoReleaseHoldModes?.contains(currentMode)) {
        addToHistory("GLOBAL: Mode changed to ${currentMode}. Auto-releasing all manual holds.")
        state.manualHold = [:]
        state.manualHoldExpireTime = [:]
    }
    
    if (isSystemPaused()) return
   
    if (activeModes && !activeModes.contains(currentMode)) return
    
    if (openOnModes?.contains(currentMode)) {
        if (darkArrivalLockout && isDarkOut()) {
            addToHistory("GLOBAL: Mode changed to ${currentMode}, but Dark Arrival Lockout blocked OPEN command.")
        } else if (isExteriorUnsafeToOpen()) {
            
        } else {
            addToHistory("GLOBAL: Mode changed to ${currentMode}. Global OPEN routine triggered.")
            startMorningGracePeriod()
            operateAllShades("open", false, "Global Open Mode")
        }
    }
    else if (closeOnModes?.contains(currentMode)) {
        addToHistory("GLOBAL: Mode changed to ${currentMode}. Global CLOSE routine triggered.")
        operateAllShades("close", false, "Global Close Mode")
    }
}

def executeSunrise() {
    if (isSystemPaused()) return
    
    if (releaseHoldSunrise) {
        addToHistory("GLOBAL: Sunrise triggered. Auto-releasing all manual holds.")
        state.manualHold = [:]
        state.manualHoldExpireTime = [:]
    }
    
    if (activeModes && !activeModes.contains(location.mode)) return
    if (sunriseModes && !sunriseModes.contains(location.mode)) return
    if (isExteriorUnsafeToOpen()) return 
    
    addToHistory("GLOBAL: Sunrise routine triggered.")
    startMorningGracePeriod()
    operateAllShades("open", false, "Sunrise Routine")
}

def executeSunset() {
    if (isSystemPaused()) return
    
    if (releaseHoldSunset) {
        addToHistory("GLOBAL: Sunset triggered. Auto-releasing all manual holds.")
        state.manualHold = [:]
        state.manualHoldExpireTime = [:]
    }
    
    if (activeModes && !activeModes.contains(location.mode)) return
    if (sunsetModes && !sunsetModes.contains(location.mode)) return
    
    addToHistory("GLOBAL: Sunset routine triggered. Closing all eligible blinds.")
    operateAllShades("close", false, "Sunset Routine")
}

// --- DYNAMIC PREDICTIVE THERMAL ENGINE & ORCHESTRATOR ---
def orchestrateHouseSync(data = null) {
    def ignoreDebounce = data?.ignoreDebounce ?: false
    if (isSystemPaused()) return
    
    def houseTargets = [:]
    def allNeedToClose = true
    def eligibleCount = 0
    def allReasons = []
    def actionRequiredCount = 0 
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def roomTarget = determineRoomTarget(i)
        houseTargets[i] = roomTarget
        
        if (settings["blind_${i}"]) {
            if (roomTarget.locked) {
                allNeedToClose = false 
            } else {
                eligibleCount++
                if (roomTarget.action != "close") {
                    allNeedToClose = false
                }
                if (roomTarget.reason && !allReasons.contains(roomTarget.reason)) {
                    allReasons << roomTarget.reason
                }
                
                def currentState = settings["blindSensor_${i}"]?.currentValue("contact") ?: state.verifiedState["${i}"]
                def expectedState = (roomTarget.action == "close") ? "closed" : roomTarget.action
                
                if (currentState != expectedState || state.targetState["${i}"] != roomTarget.action) {
                    actionRequiredCount++ 
                } else if (state.targetReason["${i}"] != roomTarget.reason) {
                    state.targetReason["${i}"] = roomTarget.reason
                }
            }
        }
    }
    
    if (actionRequiredCount == 0) return
    
    if (eligibleCount > 0 && allNeedToClose && masterBlind) {
        def combinedReason = allReasons.join(" / ")
        addToHistory("ORCHESTRATOR: Entire house evaluated to CLOSE. Intercepting and routing to Master Blind.")
        operateAllShades("close", false, combinedReason)
    } else {
        def delayMultiplier = 0
        houseTargets.each { rNum, target ->
            if (!target.locked && target.action) {
                def delaySec = delayMultiplier * 2
                runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: rNum, action: target.action, reason: target.reason, ignoreDebounce: ignoreDebounce], overwrite: false])
                delayMultiplier++
            }
        }
    }
}

def determineRoomTarget(roomNum) {
    def target = [action: null, reason: null, locked: false]
    def blind = settings["blind_${roomNum}"]
    if (!blind) {
        target.locked = true
        return target
    }
    
    if (state.manualHold["${roomNum}"]) {
        target.locked = true
        target.reason = "Manual Hold Active"
        return target
    }
    
    if (state.windLock["${roomNum}"]) {
        target.locked = true
        target.reason = "Storm Shield Wind Lock"
        return target
    }

    if (dndSwitch && dndSwitch.currentValue("switch") == "on") {
        target.locked = true
        target.reason = "Global Do Not Disturb Active"
        return target
    }
    
    def gnSwitch = settings["goodNightSwitch_${roomNum}"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        target.locked = true
        target.reason = "Nap Time/Hard Lock Active"
        return target
    }
    
    def currentMode = location.mode
    def isNight = isDarkOut()

    if (isNight && (!sunsetModes || sunsetModes.contains(currentMode))) {
        target.action = "close"
        target.reason = "Nighttime Secure"
        return target
    }
    
    if (closeOnModes?.contains(currentMode)) {
        target.action = "close"
        target.reason = "Global Close Mode"
        return target
    }

    def shouldBeOpen = false
    
    if (openOnModes?.contains(currentMode) && !isExteriorUnsafeToOpen()) {
        shouldBeOpen = true
    } else if (useSunriseSunset && !isNight && (!sunriseModes || sunriseModes.contains(currentMode)) && !isExteriorUnsafeToOpen()) {
        shouldBeOpen = true
    }

    // Morning Grace Period Intercept
    def isGraceActive = (state.morningGraceExpireTime ?: 0) > new Date().time
    if (isGraceActive) {
        target.action = "open"
        target.reason = "Morning Grace Period Active"
        return target
    }

    def envTarget = evaluateEnvironmentTarget(roomNum, isNight, currentMode)
    if (envTarget.action) {
        target.action = envTarget.action
        target.reason = envTarget.reason
        return target
    }

    if (shouldBeOpen) {
        target.action = "open"
        target.reason = "Normal Daytime Condition"
        return target
    }

    if (isExteriorUnsafeToOpen() && state.targetState["${roomNum}"] != "open") {
        target.action = "close"
        target.reason = "Blocked: Exterior conditions unsafe (High Temp or Lux)"
        return target
    }

    target.action = state.targetState["${roomNum}"] ?: "close" 
    target.reason = state.targetReason["${roomNum}"] ?: "Maintaining State"
    return target
}

def evaluateEnvironmentTarget(roomNum, isNight, currentHubMode) {
    def target = [action: null, reason: null]
    
    def isSunFacing = false
    def dirName = "these"
    
    def dirType = settings["dirType_${roomNum}"] ?: "Compass (N/E/S/W)"
    def dirSelection = settings["direction_${roomNum}"]
    def roomAzimuth = null

    if (dirType == "Exact Azimuth (°)") {
        roomAzimuth = settings["windowAzimuth_${roomNum}"] != null ? settings["windowAzimuth_${roomNum}"].toBigDecimal() : null
        dirName = roomAzimuth != null ? "${roomAzimuth}°" : "these"
    } else if (dirType == "Compass (N/E/S/W)" && dirSelection) {
        dirName = dirSelection
        if (enableSolarTracking) {
            if (dirSelection == "North") roomAzimuth = 0.0
            else if (dirSelection == "East") roomAzimuth = 90.0
            else if (dirSelection == "South") roomAzimuth = 180.0
            else if (dirSelection == "West") roomAzimuth = 270.0
        }
    }

    if (enableSolarTracking && state.currentSunPos && roomAzimuth != null) {
        def sunPos = state.currentSunPos
        if (sunPos.elevation > 0) {
            def diff = Math.abs(sunPos.azimuth - roomAzimuth)
            if (diff > 180) diff = 360 - diff
            if (diff <= 60) {
                isSunFacing = true
            }
        }
    } else if (!enableSolarTracking) {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def hour = new Date().format("HH", tz).toInteger()
        def isMorning = (hour < 12)
        
        if (dirSelection == "South") isSunFacing = true
        if (dirSelection == "East" && isMorning) isSunFacing = true
        if (dirSelection == "West" && !isMorning) isSunFacing = true
    }
    
    def tempSensor = settings["tempSensor_${roomNum}"]
    def currentTemp = tempSensor ? (tempSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0) : 70.0
    def outTemp = outdoorTempSensor ? (outdoorTempSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0) : 70.0
    def hvacState = mainThermostat ? (mainThermostat.currentValue("thermostatOperatingState") ?: "idle") : "idle"
    
    def luxHysteresisOffset = settings["luxHysteresis"] != null ? settings["luxHysteresis"].toInteger() : 500
    def tempHysteresisOffset = settings["tempHysteresis"] != null ? settings["tempHysteresis"].toBigDecimal() : 1.0
    def currentRoomReason = state.targetReason["${roomNum}"] ?: ""
    
    def outLux = outdoorLuxSensor ? (outdoorLuxSensor.currentValue("illuminance")?.toInteger() ?: 0) : 0
    def highRadiationLimit = settings["highSolarRadiationThreshold"] != null ? settings["highSolarRadiationThreshold"].toInteger() : 10000
    
    def isHighRadiation = false
    if (outdoorLuxSensor) {
        if (currentRoomReason?.startsWith("High Solar")) {
            isHighRadiation = (outLux >= (highRadiationLimit - luxHysteresisOffset))
        } else {
            isHighRadiation = (outLux >= highRadiationLimit)
        }
    }

    if (!isNight && isHighRadiation && isSunFacing) {
        target.action = "close"
        target.reason = "High Solar Radiation: Closing ${dirName} blinds to block damage [Lux: ${outLux} >= ${highRadiationLimit}]"
        return target
    }

    def coolingDefense = settings["activeCoolingDefense"] != null ? settings["activeCoolingDefense"] : true
    if (coolingDefense && hvacState == "cooling" && !isNight && isSunFacing) {
        target.action = "close"
        target.reason = "HVAC Active Cooling Defense: Closing ${dirName} blinds to assist AC [State: ${hvacState.capitalize()}]"
        return target
    }

    def maxWinterOut = settings["winterMaxOutdoorTemp"] != null ? settings["winterMaxOutdoorTemp"].toBigDecimal() : 75.0
    def isActuallyWinter = outdoorTempSensor ? (outTemp <= maxWinterOut) : true
    def winterThresh = settings["winterTempThreshold"] != null ? settings["winterTempThreshold"].toBigDecimal() : 68.0
    def indoorWinterTrigger = currentTemp <= winterThresh
    def winterOutThresh = settings["winterOutdoorTempThreshold"] != null ? settings["winterOutdoorTempThreshold"].toBigDecimal() : null
    
    def outdoorWinterTrigger = false
    if (winterOutThresh != null) {
        if (currentRoomReason?.startsWith("Winter Mode")) {
            outdoorWinterTrigger = (outTemp <= (winterOutThresh + tempHysteresisOffset))
        } else {
            outdoorWinterTrigger = (outTemp <= winterOutThresh)
        }
    }

    if (winterHeatingMode && !isNight && isActuallyWinter) {
        if (!winterAllowedModes || winterAllowedModes.contains(currentHubMode)) {
            
            // BMS INTERLOCK: Winter Deadband Ceiling
            if (settings["enableWinterCeiling"] && mainThermostat) {
                def currentCoolSet = mainThermostat.currentValue("coolingSetpoint")?.toBigDecimal() ?: 74.0
                if (currentTemp >= (currentCoolSet - 1.0) && isSunFacing) {
                    target.action = "close"
                    target.reason = "BMS Synergy (Winter Ceiling): Room at ${currentTemp}°, approaching AC setpoint (${currentCoolSet}°). Blocking solar heat."
                    return target
                }
            }

            if (indoorWinterTrigger || outdoorWinterTrigger) {
                if (isSunFacing && (hvacState == "heating" || hvacState == "idle")) {
                    target.action = "open"
                    def wReason = indoorWinterTrigger ? "In: ${currentTemp}° <= ${winterThresh}°" : "Out: ${outTemp}° <= ${winterOutThresh}°"
                    target.reason = "Winter Mode: Opening ${dirName} facing blinds to harvest active solar heat [${wReason}]"
                    return target
                }
            }
        }
    }

    def summerThresh = settings["summerTempThreshold"] != null ? settings["summerTempThreshold"].toBigDecimal() : 75.0
    def indoorSummerTrigger = currentTemp >= summerThresh
    def summerOutThresh = settings["summerOutdoorTempThreshold"] != null ? settings["summerOutdoorTempThreshold"].toBigDecimal() : null
    
    def outdoorSummerTrigger = false
    if (summerOutThresh != null) {
        if (currentRoomReason?.startsWith("Summer Mode")) {
            outdoorSummerTrigger = (outTemp >= (summerOutThresh - tempHysteresisOffset))
        } else {
            outdoorSummerTrigger = (outTemp >= summerOutThresh)
        }
    }

    if (summerEnergyMode && !isNight && !state.thermalOverrideActive) {
        if (!summerAllowedModes || summerAllowedModes.contains(currentHubMode)) {
            def avgTemp = getAverageIndoorTemp()
            def houseIsHot = avgTemp >= summerThresh

            // PREDICTIVE THERMAL LATCH
            if (settings["enablePredictiveLatch"] && !state.summerLatchActive) {
                def tz = location.timeZone ?: TimeZone.getDefault()
                def hour = new Date().format("HH", tz).toInteger()
                if (hour >= 7 && hour <= 13) {
                    if (outTemp >= (summerThresh - 2.0)) {
                         state.summerLatchActive = true
                         updateThermalIndicator(true)
                         addToHistory("PREDICTIVE LATCH: Outdoor temp reached ${outTemp}° before 1 PM. Preemptively locking house down to protect thermal mass.")
                    }
                }
            }

            if (houseIsHot && settings["summerLatchEnable"]) {
                if (!state.summerLatchActive) {
                    state.summerLatchActive = true
                    updateThermalIndicator(true)
                }
            }

            if (indoorSummerTrigger || outdoorSummerTrigger || houseIsHot || state.summerLatchActive) {
                def sReason = ""
                if (state.summerLatchActive) {
                    sReason = "Average Temp Cooling Latch Engaged for the day"
                } else if (houseIsHot) {
                    sReason = "House Average Hot (Avg: ${avgTemp}° >= ${summerThresh}°)"
                } else if (indoorSummerTrigger) {
                    sReason = "Local Room Hot (In: ${currentTemp}° >= ${summerThresh}°)"
                } else {
                    sReason = "High Outdoor Temp (Out: ${outTemp}° >= ${summerOutThresh}°)"
                }

                if (hvacState != "heating") {
                    
                    if (state.summerLatchActive || houseIsHot) {
                        state.coolingLock["${roomNum}"] = true
                        target.action = "close"
                        target.reason = "Summer Full Latch: Defending entire house against heat [${sReason}]"
                        return target
                    }

                    if (indoorSummerTrigger) {
                        state.coolingLock["${roomNum}"] = true
                        target.action = "close"
                        target.reason = "Summer Mode: Defending against local heat [${sReason}]"
                        return target
                    }
                    
                    if (isSunFacing) {
                        state.coolingLock["${roomNum}"] = true
                        target.action = "close"
                        target.reason = "Summer Active Defense: Blocking ${dirName} sun [${sReason}]"
                        return target
                    }
                }
            }
            
            if (state.coolingLock["${roomNum}"]) {
                target.action = "close"
                target.reason = "Cooling Lockdown Active (Thermal limit was exceeded earlier today)"
                return target
            }
        }
    } 
    
    return target
}

def syncSingleRoom(roomNum, ignoreDebounce = false) {
    def target = determineRoomTarget(roomNum)
    if (!target.locked && target.action) {
        singleBlindAction(roomNum, target.action, false, target.reason, ignoreDebounce)
    }
}

// --- HARD-LOCK OVERRIDE HANDLERS ---
def hardLockOnHandler(evt) {
    def deviceId = evt.device.id
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            addToHistory("${getRoomName(i)}: NAP TIME / HARD-LOCK ENGAGED. Room forced closed.")
            singleBlindAction(i, "close", true, "Nap Time/Hard Lock Active", true) 
        }
    }
}

def hardLockOffHandler(evt) {
    def deviceId = evt.device.id
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            addToHistory("${getRoomName(i)}: Hard-Lock released. Syncing room state.")
            syncSingleRoom(i, true) 
        }
    }
}

// --- UTILITY & VERIFICATION ---
def isDarkOut() {
    if (!useSunriseSunset) return false
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset || !sunInfo.sunrise) return false
    def now = new Date()
    return (now >= sunInfo.sunset || now <= sunInfo.sunrise)
}

def isExteriorUnsafeToOpen() {
    def outTemp = outdoorTempSensor ? (outdoorTempSensor.currentValue("temperature")?.toBigDecimal() ?: 0.0) : 0.0
    def outLux = outdoorLuxSensor ? (outdoorLuxSensor.currentValue("illuminance")?.toInteger() ?: 0) : 0
    def limitTemp = settings["outdoorHighTempThreshold"] != null ? settings["outdoorHighTempThreshold"].toBigDecimal() : 92.0
    def limitLux = settings["highSolarRadiationThreshold"] != null ? settings["highSolarRadiationThreshold"].toInteger() : 10000
    
    if (outdoorTempSensor && (outTemp >= limitTemp)) {
        return true
    }
    if (outdoorLuxSensor && (outLux >= limitLux)) {
        return true
    }
    return false
}

def isWithinSunsetDeadband() {
    if (!useSunriseSunset || !sunsetDeadband) return false
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset) return false
    def now = new Date()
    def deadbandStart = new Date(sunInfo.sunset.time - (sunsetDeadband.toInteger() * 60000))
    return (now >= deadbandStart && now < sunInfo.sunset)
}

def singleBlindAction(roomNum, action, bypassLock = false, reason = "Automated Sync", ignoreDebounce = false) {
    def rName = getRoomName(roomNum)
    def blind = settings["blind_${roomNum}"]
    
    if (!blind) {
        log.warn "${rName}: ABORTED. No Blind Device is selected in the app settings!"
        return
    }
    
    if (state.windLock["${roomNum}"] && action == "close") {
        log.warn "${rName}: ABORTED CLOSE. Storm Shield wind lock is active."
        return
    }
    
    if (!bypassLock && settings["goodNightSwitch_${roomNum}"]?.currentValue("switch") == "on") {
        return
    }
    
    if (action == "open" && !bypassLock && isWithinSunsetDeadband()) {
        return
    }
    
    if (action == "close" && settings["contactSensor_${roomNum}"]?.currentValue("contact") == "open") {
        if (state.targetState["${roomNum}"] != action) addToHistory("${rName}: Aborted CLOSE command. Physical window is OPEN.")
        return
    }

    def currentState = settings["blindSensor_${roomNum}"]?.currentValue("contact") ?: state.verifiedState["${roomNum}"]
    def mappedActionState = (action == "close") ? "closed" : action
    
    if (currentState == mappedActionState && state.targetState["${roomNum}"] == action) {
        if (state.targetReason["${roomNum}"] != reason) {
            state.targetReason["${roomNum}"] = reason
        }
        return 
    }

    state.targetReason["${roomNum}"] = reason

    if (!bypassLock && !ignoreDebounce) {
        def now = new Date().time
        def lastMove = state.lastAutoMoveTime["${roomNum}"] ?: 0
        def debounceMillis = (environmentalDebounce != null ? environmentalDebounce.toInteger() : 15) * 60000
        
        if ((now - lastMove) < debounceMillis) {
            def timeLeft = ((debounceMillis - (now - lastMove)) / 1000).toInteger()
            
            if (state.targetState["${roomNum}"] != action) {
                addToHistory("${rName}: ${action.toUpperCase()} delayed. Anti-Yo-Yo cooldown active (${(timeLeft/60).toInteger()}m left).")
            }
            
            runIn(timeLeft + 2, "executeSyncStaggered", [data: [roomNum: roomNum, ignoreDebounce: false], overwrite: true])
            return
        }
    }
    
    if (state.targetState["${roomNum}"] != action) {
        state.commandStartTime["${roomNum}"] = new Date().time
    }
    
    state.targetState["${roomNum}"] = action
    state.lastAutoMoveTime["${roomNum}"] = new Date().time
    
    addToHistory("${rName}: Executing ${action.toUpperCase()} command. Reason: ${reason}")
    
    // HVAC BLOWER SCAVENGING SYNERGY
    if (action == "close" && reason.toLowerCase().contains("defense") && currentState != "closed") {
        if (settings["enableBlowerScavenge"] && mainThermostat) {
            def fanMode = mainThermostat.currentValue("thermostatFanMode")
            if (fanMode != "on") {
                mainThermostat.setThermostatFanMode("on")
                addToHistory("${rName}: BMS Synergy (Scavenging). Running central HVAC blower for 15m to distribute trapped solar heat.")
                runIn(900, "revertHvacFanAuto", [overwrite: true])
            }
        }
    }
    
    logTelemetryEvent(roomNum, "commands")
    
    if (action == "open") blind.open() else blind.close()
    
    state.retryCount = 0
    runIn(90, "verifyAndRetry", [overwrite: true]) 
}

def revertHvacFanAuto() {
    if (mainThermostat) {
        mainThermostat.setThermostatFanMode("auto")
        addToHistory("GLOBAL: BMS Synergy (Scavenging) complete. HVAC blower restored to Auto.")
    }
}

// --- BLIND SENSOR DEBOUNCE HANDLERS ---
def blindSensorHandler(evt) {
    def deviceId = evt.device.id
    def now = new Date().time
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["blindSensor_${i}"]?.id == deviceId) {
            
            // --- Sensor Health & Auto-Bypass Logic ---
            if (!state.sensorFlapCount) state.sensorFlapCount = [:]
            if (!state.lastSensorFlapTime) state.lastSensorFlapTime = [:]
            
            def lastFlap = state.lastSensorFlapTime["${i}"] ?: 0
            
            // If the sensor changes state again within 2 minutes, count it as a flap
            if ((now - lastFlap) < 120000) { 
                state.sensorFlapCount["${i}"] = (state.sensorFlapCount["${i}"] ?: 0) + 1
            } else {
                
                state.sensorFlapCount["${i}"] = 1
            }
            state.lastSensorFlapTime["${i}"] = now
            
            // If it flaps 5 times quickly, it's environmental noise or a hardware failure
            if (state.sensorFlapCount["${i}"] >= 5) {
                if (state.sensorFlapCount["${i}"] == 5) {
                    def rName = getRoomName(i)
                    addToHistory("SENSOR HEALTH ALERT: ${rName} blind sensor is rapidly flapping. Temporarily bypassing manual hold detection to prevent system lockup.")
                }
                
                return
            }
            
            // --- Configurable Stabilization Timer ---
            def debounceSecs = settings["sensorDebounce_${i}"] != null ? settings["sensorDebounce_${i}"].toInteger() : 15
            runIn(debounceSecs, "evalSensor${i}", [overwrite: true])
        }
    }
}

// Fixed routing functions to handle up to 12 rooms with dynamic overwrites cleanly
def evalSensor1() { evaluateSensorEvent(1) }
def evalSensor2() { evaluateSensorEvent(2) }
def evalSensor3() { evaluateSensorEvent(3) }
def evalSensor4() { evaluateSensorEvent(4) }
def evalSensor5() { evaluateSensorEvent(5) }
def evalSensor6() { evaluateSensorEvent(6) }
def evalSensor7() { evaluateSensorEvent(7) }
def evalSensor8() { evaluateSensorEvent(8) }
def evalSensor9() { evaluateSensorEvent(9) }
def evalSensor10() { evaluateSensorEvent(10) }
def evalSensor11() { evaluateSensorEvent(11) }
def evalSensor12() { evaluateSensorEvent(12) }

def evaluateSensorEvent(i) {
    def sensor = settings["blindSensor_${i}"]
    if (!sensor) return
    
    def actualState = sensor.currentValue("contact")
    def verified = state.verifiedState["${i}"]
    def target = state.targetState["${i}"]
    def rName = getRoomName(i)
    def expectedState = (target == "close") ? "closed" : target

    def now = new Date().time
    def lastMove = state.lastAutoMoveTime["${i}"] ?: 0
    
    if ((now - lastMove) < 90000) {
        if (actualState == expectedState) {
            if (state.verifiedState["${i}"] != actualState) {
                state.verifiedState["${i}"] = actualState
                if (actualState == "open") logTelemetryEvent(i, "opens")
                if (actualState == "closed") logTelemetryEvent(i, "closes")
            }
            return 
        }
        
        // FIX: Prevent 90-second manual override lockout.
        // If the blind hasn't reached its target yet, we ignore the sensor to allow transit time.
        // However, if the blind previously reached its target (verified == expectedState),
        // but the actualState has now changed, this is a valid manual override! Do not return early.
        if (verified != expectedState) {
            return
        }
    }
    
    if (actualState == expectedState) {
        if (state.verifiedState["${i}"] != actualState) {
            state.verifiedState["${i}"] = actualState
            if (actualState == "open") logTelemetryEvent(i, "opens")
            if (actualState == "closed") logTelemetryEvent(i, "closes")
        }
        return 
    }
    
    if (verified == "closed" && actualState == "open") {
        logTelemetryEvent(i, "opens")
        addToHistory("${rName}: Blind was manually opened. Activating Manual Hold.")
        engageManualHold(i)
        state.targetState["${i}"] = "open"
        state.targetReason["${i}"] = "Manual Physical Override"
        state.verifiedState["${i}"] = actualState 
    } else if (verified == "open" && actualState == "closed") {
        logTelemetryEvent(i, "closes")
        addToHistory("${rName}: Blind was manually closed. Activating Manual Hold.")
        engageManualHold(i)
        state.targetState["${i}"] = "close"
        state.targetReason["${i}"] = "Manual Physical Override"
        state.verifiedState["${i}"] = actualState 
    }
}

def executeStaggeredCommand(data) {
    singleBlindAction(data.roomNum, data.action, false, data.reason ?: "Automated Sync", data.ignoreDebounce ?: false)
}

def operateAllShades(action, force = false, reason = "Global Command") {
    if (action == "open" && !force && isWithinSunsetDeadband()) {
        addToHistory("GLOBAL: Aborted global OPEN command (within sunset deadband).")
        return
    }

    def allEligible = true
    def roomsToCommand = []
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def blind = settings["blind_${i}"]
        def windowContact = settings["contactSensor_${i}"]
        def gnSwitch = settings["goodNightSwitch_${i}"]
        
        if (!blind) continue
        if (!force && state.manualHold["${i}"]) { allEligible = false; continue }
        if (state.windLock["${i}"]) { allEligible = false; continue } 
        if (gnSwitch && gnSwitch.currentValue("switch") == "on") { allEligible = false; continue }
        if (action == "close" && windowContact && windowContact.currentValue("contact") == "open") { allEligible = false; continue }
        
        if (state.targetState["${i}"] != action) {
            state.commandStartTime["${i}"] = new Date().time
        }
        
        state.targetState["${i}"] = action
        state.targetReason["${i}"] = reason
        roomsToCommand << i
    }
    
    if (roomsToCommand.size() == 0) return
    
    if (allEligible && masterBlind) {
        addToHistory("GLOBAL: Master Blind device triggered. Executing ${action.toUpperCase()} on all rooms.")
        if (action == "open") masterBlind.open() else masterBlind.close()
        
        state.masterRetryCount = 0
        runIn(90, "verifyMasterAndRetry", [data: [action: action, reason: reason], overwrite: true])
    } else {
        def delayMultiplier = 0
        
        roomsToCommand.each { rNum ->
            def delaySec = delayMultiplier * 2
            runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: rNum, action: action, reason: reason, ignoreDebounce: true], overwrite: false])
            delayMultiplier++
        }
        state.retryCount = 0
        runIn((roomsToCommand.size() * 2) + 90, "verifyAndRetry", [overwrite: true])
    }
}

// FIX: Fixes the race condition by canceling master retries if the target shifts
def verifyMasterAndRetry(data) {
    if (isSystemPaused()) return
    
    def action = data.action
    def reason = data.reason
    def needsMasterRetry = false
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def target = state.targetState["${i}"]
        // FIX: Skip this room if the target state has changed since the master command was originally issued
        if (!target || target != action) continue 
        
        def blindSensor = settings["blindSensor_${i}"]
        if (blindSensor) {
            def currentState = blindSensor.currentValue("contact")
            def expectedState = (target == "close") ? "closed" : target
            
            if (currentState != expectedState) {
                needsMasterRetry = true
                break 
            }
        }
    }
    
    if (needsMasterRetry) {
        state.masterRetryCount = (state.masterRetryCount ?: 0) + 1
        
        if (state.masterRetryCount <= 2) {
            addToHistory("GLOBAL: Some blinds failed to sync. Retrying Master ${action.toUpperCase()} command (${state.masterRetryCount + 1}/3).")
            if (action == "open") masterBlind.open() else masterBlind.close()
            runIn(90, "verifyMasterAndRetry", [data: data, overwrite: true])
        } else {
            addToHistory("GLOBAL: Master Blind failed after 3 attempts. Falling back to individual shade sync.")
            
            def delayMultiplier = 0
            for (int i = 1; i <= (numRooms as Integer); i++) {
                def target = state.targetState["${i}"]
                // Only fallback-sync if the target still matches the master action
                if (!target || target != action || state.manualHold["${i}"]) continue
                
                def delaySec = delayMultiplier * 2
                runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: i, action: target, reason: reason + " (Master Fallback)", ignoreDebounce: true], overwrite: false])
                delayMultiplier++
            }
            
            runIn((delayMultiplier * 2) + 90, "verifyAndRetry", [overwrite: true])
        }
    } else {
        addToHistory("GLOBAL: Master Blind sync verified successfully.")
    }
}

// --- VERIFY & PERSISTENT RETRY LOOP ---
def verifyAndRetry() {
    if (isSystemPaused()) return
    def needsRetry = false
    
    def timeoutMinutes = settings["retryTimeoutMinutes"] != null ? settings["retryTimeoutMinutes"].toInteger() : 15
    def timeoutMillis = timeoutMinutes * 60000
    def now = new Date().time
    
    def delayMultiplier = 0
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def target = state.targetState["${i}"]
        if (!target) continue
        
        def blindSensor = settings["blindSensor_${i}"]
        if (blindSensor) {
            def currentState = blindSensor.currentValue("contact")
            def expectedState = (target == "close") ? "closed" : target
            
            if (currentState != expectedState) {
                
                def startTime = state.commandStartTime["${i}"] ?: now
        
                if ((now - startTime) >= timeoutMillis) {
                    if (state.targetReason["${i}"] != "TIMEOUT FAILED") {
                        def rName = getRoomName(i)
                        addToHistory("TIMEOUT ERROR: ${rName} failed to reach ${target.toUpperCase()} after ${timeoutMinutes} minutes. Abandoning retries.")
                        state.targetReason["${i}"] = "TIMEOUT FAILED"
                        logTelemetryEvent(i, "timeouts")
                    }
                    continue 
                }
  
                needsRetry = true 
                
                def lastMove = state.lastAutoMoveTime["${i}"] ?: 0
        
                if ((now - lastMove) >= 90000) {
                    def delaySec = delayMultiplier * 3 
                    def tReason = state.targetReason["${i}"] ?: "Persistent Retry Sync"
            
                    logTelemetryEvent(i, "retries")
                    
                    if (target == "open") {
                        runIn(delaySec, "executeWiggleOpen", [data: [roomNum: i, reason: tReason], overwrite: false])
                    } else if (target == "close") {
                        runIn(delaySec, "executeWiggleClose", [data: [roomNum: i, reason: tReason], overwrite: false])
                    }
                    delayMultiplier++
                }
            } else {
                state.verifiedState["${i}"] = currentState
            }
        } else {
            state.verifiedState["${i}"] = target
        }
    }
    
    if (needsRetry) {
        runIn(90, "verifyAndRetry", [overwrite: true]) 
    }
}

// FIX: Sets verified state to "wiggling" so the sensor handler ignores the reverse movement
def executeWiggleOpen(data) {
    def rNum = data.roomNum
    def blind = settings["blind_${rNum}"]
    if (!blind) return
    
    logTelemetryEvent(rNum, "wiggles")
    addToHistory("${getRoomName(rNum)}: Wiggle maneuver engaged. Forcing CLOSE, then re-issuing OPEN. Reason: ${data.reason}")
    
    // FIX: Temporarily clear verified state to prevent triggering a false manual hold during the reverse movement
    state.verifiedState["${rNum}"] = "wiggling" 
    if (blind.hasCommand("close")) blind.close()
    
    state.lastAutoMoveTime["${rNum}"] = new Date().time
    runIn(5, "finalizeWiggleOpen", [data: [roomNum: rNum], overwrite: false])
}

def finalizeWiggleOpen(data) {
    def blind = settings["blind_${data.roomNum}"]
    // FIX: Reset the move timer so the 90-second transit window applies to the actual final movement
    state.lastAutoMoveTime["${data.roomNum}"] = new Date().time 
    if (blind && blind.hasCommand("open")) blind.open()
}

def executeWiggleClose(data) {
    def rNum = data.roomNum
    def blind = settings["blind_${rNum}"]
    if (!blind) return
    
    logTelemetryEvent(rNum, "wiggles")
    addToHistory("${getRoomName(rNum)}: Wiggle maneuver engaged. Forcing OPEN, then re-issuing CLOSE. Reason: ${data.reason}")
    
    // FIX: Temporarily clear verified state
    state.verifiedState["${rNum}"] = "wiggling"
    if (blind.hasCommand("open")) blind.open()
    
    state.lastAutoMoveTime["${rNum}"] = new Date().time
    runIn(5, "finalizeWiggleClose", [data: [roomNum: rNum], overwrite: false])
}

def finalizeWiggleClose(data) {
    def blind = settings["blind_${data.roomNum}"]
    // FIX: Reset the move timer
    state.lastAutoMoveTime["${data.roomNum}"] = new Date().time 
    if (blind && blind.hasCommand("close")) blind.close()
}
