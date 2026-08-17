/**
 * Advanced Mini Split Manager 2.0 
 */ 

definition(
    name: "Advanced Mini Split Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomConfigPage")
}

def formatRuntime(secs) {
    if (!secs || secs == 0) return "0.0h"
    return String.format("%.1fh", secs / 3600.0)
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
            
            def globalStatus = (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") ? 
                "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : 
                "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            
            def statusExplanation = "${globalStatus}"

            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>Global System Status:</b> ${statusExplanation}</div>"
            
            input "btnForceSync", "button", title: "⚡ Manually Force Hardware Sync"
            input "btnResetMaint", "button", title: "🧹 Reset ALL Filter/Maintenance Timers"

            def hasZones = false
            def outTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null
            def currentModeStr = location.mode
            def numZones = settings.zoneCount?.toInteger() ?: 2

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 28%; }
                .dash-subhead { background-color: #dbeafe; font-weight: bold; text-align: left !important; padding: 8px 15px !important; color: #1e3a8a; }
                .dash-th-dark { background-color: #2d3748 !important; color: white; }
                .badge-good { color: green; font-weight: bold; }
                .badge-warn { color: #d97706; font-weight: bold; }
                .badge-alert { color: red; font-weight: bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Zone</th><th>Current State</th><th>Climate & Target</th><th>BMS Guards & Maint</th></tr></thead>
                <tbody>
            """

            for (int i = 1; i <= numZones; i++) {
                if (settings["enableZ${i}"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
                    def currentMode = state."currentMode_z${i}" ?: "OFF"
                    def tempDev = settings["z${i}Temp"]
                    def humDev = settings["z${i}Humidity"]
                    def datDev = settings["z${i}DATSensor"]
                    
                    def loadShareModes = settings["z${i}LoadShareModes"]
                    def isLoadSharing = loadShareModes && (loadShareModes as List).contains(currentModeStr)
                    
                    def glideOffset = 0
                    if (settings.enableSolarGlide && outTemp != null && outTemp > 90.0) {
                        glideOffset = Math.floor((outTemp.toBigDecimal() - 90.0) / 5.0).toInteger()
                    }

                    // Calculate Active Setpoint
                    def activeSetpoint = "--"
                    
                    switch(currentMode) {
                        case "OCC COOL": activeSetpoint = "${(settings["z${i}OccCool"] ?: 74) + glideOffset}°"; break
                        case "UNOCC COOL": activeSetpoint = "${(settings["z${i}UnoccCool"] ?: 78) + glideOffset}°"; break
                        case "NIGHT COOL": activeSetpoint = "${(settings["z${i}NightCool"] ?: 74) + glideOffset}°"; break
                        case "OCC HEAT": activeSetpoint = "${settings["z${i}OccHeat"] ?: 68}°"; break
                        case "UNOCC HEAT": activeSetpoint = "${settings["z${i}UnoccHeat"] ?: 62}°"; break
                        case "NIGHT HEAT": activeSetpoint = "${settings["z${i}NightHeat"] ?: 68}°"; break
                        case "FAILSAFE OFF": activeSetpoint = "Failsafe (Doors Open)"; break
                        case "OFF": activeSetpoint = "OFF (Away > 24h)"; break
                    }
                    if (glideOffset > 0 && currentMode.contains("COOL")) activeSetpoint += " <span style='font-size:10px;color:orange;'>(+${glideOffset}° Glide)</span>"

                    def isRunning = (currentMode != "OFF" && currentMode != "FAILSAFE OFF" && currentMode != "BMS COMPRESSOR LOCKOUT" && currentMode != "CHANGEOVER DELAY")
                    
                    // Maintenance Tracker
                    def maxMins = 250.0 * 60.0
                    def usedSecs = state."lifetimeRuntimeSecs_z${i}" ?: 0
                    def usedMins = usedSecs / 60.0
                    def percentLeft = Math.max(0.0, 100.0 - ((usedMins / maxMins) * 100)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                    
                    def filterColor = percentLeft < 15.0 ? "red" : "green"
                    def maintDisplay = "<span style='font-size:11px; color:${filterColor};'>Filter Life: ${percentLeft}%</span>"
                    if(percentLeft < 5.0) maintDisplay += "<br><span style='font-size:10px; color:red; font-weight:bold;'>⚠️ CLEAN FILTER SOON</span>"

                    // Delta T & DAT Logic
                    def deltaTDisplay = "--"
                    if (datDev) {
                        def currentDat = datDev.currentValue("temperature")
                        def datStr = currentDat != null ? "${currentDat}°" : "--°"
                        def deltaT = state."deltaT_z${i}" ?: 0.0
                        def deltaTDelayMins = settings.deltaTDelay != null ? settings.deltaTDelay : 30
                        def deltaColor = (isRunning && Math.abs(deltaT) < 12.0 && (now() - (state."modeStartTime_z${i}" ?: 0) > (deltaTDelayMins * 60000))) ? "red" : "green"
                        deltaTDisplay = "DAT: ${datStr} | <b><span style='color:${deltaColor};'>ΔT: ${String.format('%.1f', deltaT)}°</span></b>"
                    }

                    // Mode & Status Displays
                    def modeColor = "gray"
                    if (currentMode.contains("COOL")) modeColor = "#007BFF"
                    if (currentMode.contains("HEAT")) modeColor = "#FF4500"
                    if (currentMode.contains("LOCKOUT") || currentMode.contains("DELAY") || currentMode == "FAILSAFE OFF") modeColor = "red"
                    
                    def modeDisplay = "<b><span style='color:${modeColor};'>${currentMode}</span></b>"
                    
                    if (isLoadSharing) {
                        modeDisplay += "<br><span style='font-size:11px; color:#007BFF; font-weight:bold;'>🤝 Load Sharing Active</span>"
                    } else if (currentMode == "FAILSAFE OFF") {
                        modeDisplay += "<br><span style='font-size:11px; color:red; font-weight:bold;'>Window/Door Open!</span>"
                    } else if (currentMode == "BMS COMPRESSOR LOCKOUT") {
                        def left = Math.ceil(((state."compressorLockoutTime_z${i}" ?: now()) - now()) / 60000).toInteger()
                        modeDisplay += "<br><span style='font-size:11px; color:red;'>Rest Timer: ${left}m left</span>"
                    } else if (currentMode == "CHANGEOVER DELAY") {
                        modeDisplay += "<br><span style='font-size:11px; color:red;'>Changeover Guard Active</span>"
                    } else if (isRunning) {
                        def activeMins = Math.floor((now() - (state."modeStartTime_z${i}" ?: now())) / 60000).toInteger()
                        modeDisplay += "<br><span style='font-size:11px; color:#888;'>Active for ${activeMins}m</span>"
                    } else {
                        modeDisplay += "<br><span style='font-size:11px; color:#888;'>Standby / OFF</span>"
                    }

                    dashHTML += "<tr><td class='dash-hl'>${zName}</td>"
                    dashHTML += "<td>${modeDisplay}</td>"
                    dashHTML += "<td style='font-size:12px;'>🎯 <b>Target: ${activeSetpoint}</b><br>🌡️ ${tempDev?.currentValue("temperature")}° | 💧 ${humDev?.currentValue("humidity")}%<br>${deltaTDisplay}</td>"
                    dashHTML += "<td>${maintDisplay}</td></tr>"
                }
            }
            dashHTML += "</tbody></table>"
            
            if (hasZones) {
                // Global Analytics Table
                dashHTML += """
                <table class="dash-table" style="margin-top: 15px;">
                    <tbody>
                        <tr><td colspan="2" class="dash-subhead">Global Analytics</td></tr>
                        <tr>
                            <td class="dash-hl" style="width: 50%;">Current Outdoor Temp</td><td style="width: 50%; text-align: left; padding-left: 15px;">${outTemp != null ? outTemp + '°F' : '--'}</td>
                        </tr>
                    </tbody>
                </table>
                """
                
                // Detailed 7-Day Performance Tracking
                def perfHTML = ""
                def todayDate = new Date().format("yyyy-MM-dd", location.timeZone)
                def maxT = state.dailyMaxTemp != null ? "${state.dailyMaxTemp}°" : "--"
                def maxH = state.dailyMaxHum != null ? "${state.dailyMaxHum}%" : "--"
                def todayOSA = "${maxT} / ${maxH}"

                for (int i = 1; i <= numZones; i++) {
                    if (settings["enableZ${i}"]) {
                        def zName = settings["z${i}Name"] ?: "Room ${i}"
                        perfHTML += """
                        <table class="dash-table" style="margin-top: 15px;">
                            <thead>
                                <tr><th colspan="5" class="dash-subhead">${zName} - 7-Day Compressor Cycle Statistics</th></tr>
                                <tr>
                                    <th class="dash-th-dark">Date</th>
                                    <th class="dash-th-dark">Max OSA<br>(Temp / Hum)</th>
                                    <th class="dash-th-dark">Cooling</th>
                                    <th class="dash-th-dark">Heating</th>
                                    <th class="dash-th-dark">Total Cycles</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>${todayDate} (Today)</td>
                                    <td>${todayOSA}</td>
                                    <td>${formatRuntime(state."dailyCoolSecs_z${i}" ?: 0)}</td>
                                    <td>${formatRuntime(state."dailyHeatSecs_z${i}" ?: 0)}</td>
                                    <td>${state."dailyCycles_z${i}" ?: 0}</td>
                                </tr>
                        """
                        
                        def hist = state."history_z${i}" ?: []
                        hist.each { h ->
                            def hTemp = h.maxOsaTemp != null ? "${h.maxOsaTemp}°" : "--"
                            def hHum = h.maxOsaHum != null ? "${h.maxOsaHum}%" : "--"
                            perfHTML += """
                                <tr>
                                    <td>${h.date}</td>
                                    <td>${hTemp} / ${hHum}</td>
                                    <td>${formatRuntime(h.coolSecs ?: 0)}</td>
                                    <td>${formatRuntime(h.heatSecs ?: 0)}</td>
                                    <td>${h.cycles ?: 0}</td>
                                </tr>
                            """
                        }
                        perfHTML += "</tbody></table>"

                        // 15-CYCLE COOLING DELTA T MAX HISTORY TABLE
                        if (settings["z${i}DATSensor"]) {
                            def activeCycleMax = state."currentCycleMaxDeltaT_z${i}" ?: 0.0
                            def activeMode = state."currentMode_z${i}" ?: "OFF"
                            def isCurrentlyCooling = (activeMode.contains("COOL"))
                            def currentPeakStr = isCurrentlyCooling ? "${String.format('%.1f', activeCycleMax)}°F (In Progress)" : "Idle / Standby"

                            perfHTML += """
                            <table class="dash-table" style="margin-top: 15px;">
                                <thead>
                                    <tr><th colspan="5" class="dash-subhead">${zName} - Last 15 Cooling Cycles Peak ΔT Performance</th></tr>
                                    <tr>
                                        <th class="dash-th-dark" style="width: 10%;">#</th>
                                        <th class="dash-th-dark" style="width: 25%;">Timestamp</th>
                                        <th class="dash-th-dark" style="width: 25%;">Cooling Mode</th>
                                        <th class="dash-th-dark" style="width: 15%;">Duration</th>
                                        <th class="dash-th-dark" style="width: 25%;">Max ΔT Achieved</th>
                                    </tr>
                                </thead>
                                <tbody>
                            """
                            
                            if (isCurrentlyCooling) {
                                def deltaColor = activeCycleMax >= 14.0 ? "badge-good" : (activeCycleMax >= 10.0 ? "badge-warn" : "badge-alert")
                                perfHTML += """
                                    <tr style="background-color: #f0fdf4;">
                                        <td><b>Active</b></td>
                                        <td>Now</td>
                                        <td><b>${activeMode}</b></td>
                                        <td>--</td>
                                        <td><span class="${deltaColor}"><b>${String.format('%.1f', activeCycleMax)}°F</b></span> <i>(Live)</i></td>
                                    </tr>
                                """
                            }

                            def deltaHist = state."deltaTMaxHistory_z${i}" ?: []
                            if (deltaHist.size() > 0) {
                                deltaHist.eachWithIndex { entry, idx ->
                                    def dVal = (entry.maxDeltaT ?: 0.0) as Float
                                    def badgeClass = dVal >= 14.0 ? "badge-good" : (dVal >= 10.0 ? "badge-warn" : "badge-alert")
                                    perfHTML += """
                                        <tr>
                                            <td>${idx + 1}</td>
                                            <td>${entry.time}</td>
                                            <td>${entry.mode}</td>
                                            <td>${entry.duration}</td>
                                            <td><span class="${badgeClass}">${String.format('%.1f', dVal)}°F</span></td>
                                        </tr>
                                    """
                                }
                            } else if (!isCurrentlyCooling) {
                                perfHTML += "<tr><td colspan='5' style='color:#777;'><i>No completed cooling cycles recorded yet.</i></td></tr>"
                            }
                            perfHTML += "</tbody></table>"
                        }
                    }
                }
                
                dashHTML += perfHTML
                paragraph dashHTML
                
                input "btnClearHistory", "button", title: "🗑️ Clear Tracking & Delta-T History"
            } else {
                paragraph "<i>No zones configured yet.</i>"
            }
        }

        section("<b>1. Manage & Configure Zones</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Add, remove, and configure individual mini-split zones. Expand this section to view all configured rooms.</div>"
            
            input "zoneCount", "number", title: "Number of Zones to Configure", defaultValue: 2, submitOnChange: true
            
            paragraph "<hr>"
            def numZonesConfig = settings.zoneCount?.toInteger() ?: 2
            for (int i = 1; i <= numZonesConfig; i++) {
                def zName = settings["z${i}Name"] ?: "Zone ${i}"
                href(name: "roomPage${i}", page: "roomConfigPage", params: [roomId: i], title: "▶ Configure ${zName}", description: settings["enableZ${i}"] ? "Active" : "Disabled")
            }
        }

        section("<b>2. Global Energy Optimizations</b>", hideable: true, hidden: true) {
            input "enableSolarGlide", "bool", title: "<b>Enable Weather Setpoint Gliding</b>", defaultValue: true, description: "Dynamically raises your cooling setpoints when outdoor temperatures exceed 90°F."
            
            paragraph "<hr>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Global Outdoor Temperature Sensor", required: false, submitOnChange: true
            input "outdoorHumSensor", "capability.relativeHumidityMeasurement", title: "Global Outdoor Humidity Sensor (Optional)", required: false, submitOnChange: true
        }

        section("<b>3. Away Mode Handling (24 Hour OFF)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> If the home stays in one of these modes for more than 24 hours continuously, the units will turn OFF entirely.</div>"
            input "awayModes", "mode", title: "Away Modes", multiple: true, required: false
        }
            
        section("<b>4. Active Alerts & Notifications</b>", hideable: true, hidden: true) {
            input "notificationDevice", "capability.notification", title: "Select Notification Devices", multiple: true, required: false
            
            paragraph "<b>Filter Alerts</b>"
            input "notifyFilter", "bool", title: "Notify on Filter Replacement Needed", defaultValue: true
            input "filterAlertPercent", "number", title: "Filter Alert Threshold (% Life Remaining)", defaultValue: 15
            
            paragraph "<b>System Efficiency (Delta-T) Alerts</b>"
            input "notifyDeltaT", "bool", title: "Notify on Bad Delta-T (Poor Efficiency/Freezing Coil)", defaultValue: true
            input "deltaTDelay", "number", title: "Delta-T Alarm Delay (Minutes)", defaultValue: 30, description: "Wait this long after the unit turns on before triggering an alarm."
        }

        section("<b>5. Action History & Debugging</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false
            if (state.actionHistory) paragraph "<span style='font-size: 13px; font-family: monospace;'>${state.actionHistory.join("<br>")}</span>"
        }
    }
}

def roomConfigPage(params) {
    if (params?.roomId) state.editingRoom = params.roomId
    def i = state.editingRoom ?: 1
    def zName = settings["z${i}Name"] ?: "Zone ${i}"

    dynamicPage(name: "roomConfigPage", title: "<b>Configuration: ${zName}</b>", install: false, uninstall: false) {
        section("<b>▶ Basic Setup</b>") {
            input "enableZ${i}", "bool", title: "<b>Enable this Zone</b>", submitOnChange: true
             
            if (settings["enableZ${i}"]) {
                input "z${i}Name", "text", title: "Zone Name", defaultValue: "Zone ${i}", submitOnChange: true
            }
        }

        if (settings["enableZ${i}"]) {
            section("<b>1. Climate Sensors & Environment</b>", hideable: true, hidden: true) {
                input "z${i}Temp", "capability.temperatureMeasurement", title: "Main Room Temperature Sensor", required: true
                input "z${i}DATSensor", "capability.temperatureMeasurement", title: "Discharge Air Temp (DAT) Sensor (For Delta T)", required: false, submitOnChange: true
                input "z${i}Humidity", "capability.relativeHumidityMeasurement", title: "Humidity Sensor", required: false, submitOnChange: true
            }

            section("<b>2. Occupancy & Schedules</b>", hideable: true, hidden: true) {
                input "z${i}OccSwitch", "capability.switch", title: "Room Occupied Switch", required: true
                input "z${i}ExtraOccSwitches", "capability.switch", title: "Additional Occupancy Switches", multiple: true, required: false
                input "z${i}NightSwitch", "capability.switch", title: "Good Night Switch", required: false
                
                paragraph "<hr>"
                input "z${i}LoadShareModes", "mode", title: "Load Sharing Modes (Force 'Occupied' setpoints during these Location Modes)", multiple: true, required: false
            }

            section("<b>3. Operating Offsets & Calibration</b>", hideable: true, hidden: true) {
                input "z${i}Deadband", "decimal", title: "Deadband / Swing (°F)", defaultValue: 2.0
                input "z${i}CoolOffset", "number", title: "Ceiling Cooling Offset (Subtract from Target)", defaultValue: 3, submitOnChange: true
                input "z${i}HeatOffset", "number", title: "Ceiling Heating Offset (Add to Target)", defaultValue: 4, submitOnChange: true
            }

            section("<b>4. BMS Protection Guards</b>", hideable: true, hidden: true) {
                input "z${i}CompressorRest", "number", title: "Compressor Rest Delay (Minutes)", defaultValue: 3, required: true
                input "z${i}ChangeoverDelay", "number", title: "Auto-Changeover Guard (Minutes)", defaultValue: 60, required: true
                input "z${i}EnforceState", "number", title: "RF State Enforcement Interval (Minutes)", defaultValue: 30, description: "Re-transmits the active RF code periodically to fix dropped packets."
                
                paragraph "<hr>"
                paragraph "<b>Open Door / Window Defeat</b>"
                input "z${i}Contacts", "capability.contactSensor", title: "Window/Door Failsafe Contacts", multiple: true, required: false
                input "z${i}ContactTimeout", "number", title: "Failsafe Timeout (Minutes)", defaultValue: 5
            }

            section("<b>5. Base Setpoints & Targets</b>", hideable: true, hidden: true) {
                paragraph "<b>Preferred Setpoints (Occupied)</b>"
                input "z${i}OccCool", "number", title: "Occupied Cooling Target (°F)", defaultValue: 74, submitOnChange: true
                input "z${i}OccHeat", "number", title: "Occupied Heating Target (°F)", defaultValue: 68, submitOnChange: true
                
                paragraph "<b>Preferred Setpoints (Good Night)</b>"
                input "z${i}NightCool", "number", title: "Night Cooling Target (°F)", defaultValue: 74, submitOnChange: true
                input "z${i}NightHeat", "number", title: "Night Heating Target (°F)", defaultValue: 68, submitOnChange: true

                paragraph "<b>Preferred Setpoints (Unoccupied)</b>"
                input "z${i}UnoccCool", "number", title: "Unoccupied Cooling Target (°F)", defaultValue: 78, submitOnChange: true
                input "z${i}UnoccHeat", "number", title: "Unoccupied Heating Target (°F)", defaultValue: 62, submitOnChange: true
            }

            section("<b>6. Broadlink Integration & IR Codes</b>", hideable: true, hidden: true) {
                def cOff = settings["z${i}CoolOffset"] != null ? settings["z${i}CoolOffset"] : 3
                def hOff = settings["z${i}HeatOffset"] != null ? settings["z${i}HeatOffset"] : 4

                input "z${i}Broadlink", "capability.actuator", title: "Broadlink Device", required: true, submitOnChange: true
                
                if (settings["z${i}Broadlink"]) {
                    def broadlinkDev = settings["z${i}Broadlink"]
                    def codeOptions = []
                    
                    try {
                        // Instruct the driver to cache its codes in the device data space
                        broadlinkDev.cacheCodesForApp(true)
                        def codesStr = broadlinkDev.getDataValue("codes")
                        if (codesStr) {
                            def parsed = new groovy.json.JsonSlurper().parseText(codesStr)
                            if (parsed instanceof Map) {
                                codeOptions = parsed.keySet().sort()
                            }
                        }
                    } catch (e) {
                        log.warn "Could not fetch saved codes from Broadlink device: ${e.message}"
                    }

                    if (!codeOptions) {
                        paragraph "<span style='color:red;'><b>No saved codes found!</b> Make sure you have learned and saved codes on the Broadlink device.</span>"
                    } else {
                        paragraph "<div style='font-size:13px; color:green;'><b>✅ Successfully loaded ${codeOptions.size()} saved codes from the remote.</b> Select them below:</div>"
                    }

                    def recOccC = (settings["z${i}OccCool"] ?: 74) - cOff
                    def recOccH = (settings["z${i}OccHeat"] ?: 68) + hOff
                    def recNightC = (settings["z${i}NightCool"] ?: 74) - cOff
                    def recNightH = (settings["z${i}NightHeat"] ?: 68) + hOff
                    def recUnoccC = (settings["z${i}UnoccCool"] ?: 78) - cOff
                    def recUnoccH = (settings["z${i}UnoccHeat"] ?: 62) + hOff

                    input "z${i}CodeOccCool", "enum", title: "Occupied Cool Code (Rec: COOL at ${recOccC}°)", options: codeOptions, required: false
                    input "testBtn_${i}_CodeOccCool", "button", title: "▶ Test Occupied Cool Code"

                    input "z${i}CodeOccHeat", "enum", title: "Occupied Heat Code (Rec: HEAT at ${recOccH}°)", options: codeOptions, required: false
                    input "testBtn_${i}_CodeOccHeat", "button", title: "▶ Test Occupied Heat Code"

                    input "z${i}CodeNightCool", "enum", title: "Night Cool Code (Rec: COOL at ${recNightC}°)", options: codeOptions, required: false
                    input "testBtn_${i}_CodeNightCool", "button", title: "▶ Test Night Cool Code"

                    input "z${i}CodeNightHeat", "enum", title: "Night Heat Code (Rec: HEAT at ${recNightH}°)", options: codeOptions, required: false
                    input "testBtn_${i}_CodeNightHeat", "button", title: "▶ Test Night Heat Code"

                    input "z${i}CodeUnoccCool", "enum", title: "Unoccupied Cool Code (Rec: COOL at ${recUnoccC}°)", options: codeOptions, required: false
                    input "testBtn_${i}_CodeUnoccCool", "button", title: "▶ Test Unoccupied Cool Code"

                    input "z${i}CodeUnoccHeat", "enum", title: "Unoccupied Heat Code (Rec: HEAT at ${recUnoccH}°)", options: codeOptions, required: false
                    input "testBtn_${i}_CodeUnoccHeat", "button", title: "▶ Test Unoccupied Heat Code"

                    input "z${i}CodeOff", "enum", title: "System OFF Code", description: "Only runs when Away > 24hr or Doors Open", options: codeOptions, required: false
                    input "testBtn_${i}_CodeOff", "button", title: "▶ Test OFF Code"
                }
            }
            
            section("<b>7. Maintenance & Alerts</b>", hideable: true, hidden: true) {
                input "btnResetMaint_z${i}", "button", title: "🧹 Reset ${zName} Filter Timer"
                input "z${i}FilterAlertSwitch", "capability.switch", title: "Filter Alert Virtual Switch", required: false, submitOnChange: true
                if (settings["z${i}FilterAlertSwitch"]) {
                    input "z${i}FilterAlertModes", "mode", title: "Modes to allow Filter Alert Switch", multiple: true, required: false
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL BMS LOGIC ENGINE
// ==============================================================================

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    // Performance Tracking Initializers
    state.currentDateStr = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.dailyMaxTemp) state.dailyMaxTemp = outdoorTempSensor?.currentValue("temperature") ?: null
    if (!state.dailyMaxHum) state.dailyMaxHum = outdoorHumSensor?.currentValue("humidity") ?: null
    
    schedule("0 0 0 * * ?", midnightRollover) // Runs at 12:00 AM every day
    schedule("0 0 8 * * ?", scheduleRandomFilterAlerts) // Runs at 8:00 AM every day
    
    def numZones = settings.zoneCount?.toInteger() ?: 2
    for (int i = 1; i <= numZones; i++) {
        if (!state."currentMode_z${i}") state."currentMode_z${i}" = "OFF"
        state."lastStatUpdate_z${i}" = now()
        state."deltaT_z${i}" = 0.0
        state."currentCycleMaxDeltaT_z${i}" = 0.0
        state."filterNotified_z${i}" = false
        state."deltaTNotified_z${i}" = false
        
        // BMS Trackers
        state."lastOffTime_z${i}" = now()
        state."lastCoolTime_z${i}" = 0
        state."lastHeatTime_z${i}" = 0
        state."lastRfSentTime_z${i}" = 0
        
        // Performance Variables
        if (!state."dailyCoolSecs_z${i}") state."dailyCoolSecs_z${i}" = 0
        if (!state."dailyHeatSecs_z${i}") state."dailyHeatSecs_z${i}" = 0
        if (!state."dailyCycles_z${i}") state."dailyCycles_z${i}" = 0
        if (!state."history_z${i}") state."history_z${i}" = []
        if (!state."deltaTMaxHistory_z${i}") state."deltaTMaxHistory_z${i}" = []
    }
    
    subscribe(location, "mode", modeChangeHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", masterSwitchHandler)
    
    for (int i = 1; i <= numZones; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", sensorHandler)
            if (settings["z${i}DATSensor"]) subscribe(settings["z${i}DATSensor"], "temperature", sensorHandler)
            if (settings["z${i}Humidity"]) subscribe(settings["z${i}Humidity"], "humidity", sensorHandler)
            if (settings["z${i}OccSwitch"]) subscribe(settings["z${i}OccSwitch"], "switch", sensorHandler)
            def extraOccSwitches = settings["z${i}ExtraOccSwitches"]
            if (extraOccSwitches) {
                subscribe(extraOccSwitches, "switch", sensorHandler)
            }
            if (settings["z${i}NightSwitch"]) subscribe(settings["z${i}NightSwitch"], "switch", sensorHandler)
            if (settings["z${i}Contacts"]) subscribe(settings["z${i}Contacts"], "contact", contactHandler)
        }
    }
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", sensorHandler)
    if (outdoorHumSensor) subscribe(outdoorHumSensor, "humidity", sensorHandler)
    
    runEvery5Minutes("evaluateAllRooms")
    logAction("Advanced Mini Split Manager (Continuous Edition) Initialized.")
    evaluateAllRooms()
}

def midnightRollover() {
    def numZones = settings.zoneCount?.toInteger() ?: 2
    def yesterdayDate = (new Date() - 1).format("yyyy-MM-dd", location.timeZone)
    def maxT = state.dailyMaxTemp
    def maxH = state.dailyMaxHum
    
    for (int i = 1; i <= numZones; i++) {
        def zh = state."history_z${i}" ?: []
        zh.add(0, [
            date: yesterdayDate,
            maxOsaTemp: maxT,
            maxOsaHum: maxH,
            coolSecs: state."dailyCoolSecs_z${i}" ?: 0,
            heatSecs: state."dailyHeatSecs_z${i}" ?: 0,
            cycles: state."dailyCycles_z${i}" ?: 0
        ])
        if (zh.size() > 7) zh = zh[0..6]
        state."history_z${i}" = zh
        
        state."dailyCoolSecs_z${i}" = 0
        state."dailyHeatSecs_z${i}" = 0
        state."dailyCycles_z${i}" = 0
    }
    
    state.dailyMaxTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null
    state.dailyMaxHum = outdoorHumSensor ? outdoorHumSensor.currentValue("humidity") : null
    
    state.currentDateStr = new Date().format("yyyy-MM-dd", location.timeZone)
    logDebug("Performed midnight rollover of performance statistics.")
}

def scheduleRandomFilterAlerts() {
    if (!isMasterEnabled()) return
    def numZones = settings.zoneCount?.toInteger() ?: 2
    for (int i = 1; i <= numZones; i++) {
        if (settings["enableZ${i}"] && settings["z${i}FilterAlertSwitch"]) {
            def randomSeconds = new java.util.Random().nextInt(39600)
            runIn(randomSeconds, "triggerFilterAlertSwitch", [data: [room: i], overwrite: false])
        }
    }
}

def triggerFilterAlertSwitch(data) {
    if (!isMasterEnabled()) return
    def roomId = data.room
    def alertSwitch = settings["z${roomId}FilterAlertSwitch"]
    if (!alertSwitch) return
    
    def alertModes = settings["z${roomId}FilterAlertModes"]
    if (alertModes && !(alertModes as List).contains(location.mode)) return
    
    def runtimeHrs = (state."lifetimeRuntimeSecs_z${roomId}" ?: 0) / 3600.0
    def maxHrs = 250.0
    def percentLeft = Math.max(0.0, 100.0 - ((runtimeHrs / maxHrs) * 100)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    def alertThreshold = settings.filterAlertPercent != null ? settings.filterAlertPercent : 15.0
    
    if (percentLeft < alertThreshold) {
        safeOn(alertSwitch)
        if (settings.notifyFilter != false && notificationDevice) {
            notificationDevice.deviceNotification("Daily Reminder: ${settings["z${roomId}Name"]} filter is at ${percentLeft}% life remaining. Please clean/replace it soon.")
        }
        logAction("${settings["z${roomId}Name"]}: Filter Alert Threshold Reached. Turning on virtual alert switch.")
        runIn(600, "turnOffFilterAlertSwitch", [data: [room: roomId], overwrite: false])
    }
}

def turnOffFilterAlertSwitch(data) {
    def alertSwitch = settings["z${data.room}FilterAlertSwitch"]
    if (alertSwitch) {
        safeOff(alertSwitch)
        logAction("${settings["z${data.room}Name"]}: Filter Alert Cycle Complete.")
    }
}

def isMasterEnabled() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def masterSwitchHandler(evt) {
    logAction("Master Enable Switch turned ${evt.value}.")
    if (evt.value == "on") {
        runEvery5Minutes("evaluateAllRooms")
        schedule("0 0 0 * * ?", midnightRollover)
        schedule("0 0 8 * * ?", scheduleRandomFilterAlerts)
        evaluateAllRooms()
    } else {
        unschedule() 
    }
}

def modeChangeHandler(evt) {
    if (!isMasterEnabled()) return
    logAction("Location mode changed to: ${evt.value}")
    
    def isAway = awayModes && (awayModes as List).contains(evt.value)
    if (isAway) {
        if (!state.awayStartTime) {
            state.awayStartTime = now()
            logAction("System entered Away Mode. 24-hour OFF timer started.")
        }
    } else {
        if (state.awayStartTime) {
            state.awayStartTime = null
            logAction("System returned from Away Mode. 24-hour OFF timer canceled.")
        }
    }
    
    evaluateAllRooms()
}

def sensorHandler(evt) { runIn(2, "evaluateAllRooms") }
def contactHandler(evt) { runIn(2, "evaluateAllRooms") }

def evaluateAllRooms() {
    if (!isMasterEnabled()) return
    
    // Failsafe catch for Hubitat missed cron jobs
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    if (state.currentDateStr != todayStr) {
        if (state.currentDateStr != null) midnightRollover()
        state.currentDateStr = todayStr
    }

    // 24-Hour Away Timer State Keeper
    def currentModeStr = location.mode
    def isAway = awayModes && (awayModes as List).contains(currentModeStr)
    if (isAway) {
        if (!state.awayStartTime) state.awayStartTime = now()
    } else {
        state.awayStartTime = null
    }
    
    // Update daily max OSA
    def outTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null
    if (outTemp != null && (!state.dailyMaxTemp || outTemp > state.dailyMaxTemp)) state.dailyMaxTemp = outTemp
    
    def outHum = outdoorHumSensor ? outdoorHumSensor.currentValue("humidity") : null
    if (outHum != null && (!state.dailyMaxHum || outHum > state.dailyMaxHum)) state.dailyMaxHum = outHum
    
    def numZones = settings.zoneCount?.toInteger() ?: 2
    for (int i = 1; i <= numZones; i++) {
        if (settings["enableZ${i}"]) evaluateRoom(i)
    }
}

def evaluateRoom(roomId) {
    if (!isMasterEnabled()) return

    def currentModeStr = location.mode
    def tempDev = settings["z${roomId}Temp"]
    def datDev = settings["z${roomId}DATSensor"]
    
    def temp = tempDev ? (tempDev.currentValue("temperature") ?: 72.0).toFloat() : 72.0
    def outTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null
    
    def cMode = state."currentMode_z${roomId}" ?: "OFF"

    // Delta T Check & Peak Cycle Max Tracker
    if (datDev) {
        def dat = (datDev.currentValue("temperature") ?: temp).toFloat()
        if (cMode.contains("COOL")) {
            def curDelta = (temp - dat).toFloat()
            state."deltaT_z${roomId}" = curDelta
            def curMax = state."currentCycleMaxDeltaT_z${roomId}" != null ? (state."currentCycleMaxDeltaT_z${roomId}" as Float) : 0.0f
            if (curDelta > curMax) state."currentCycleMaxDeltaT_z${roomId}" = curDelta
        } else if (cMode.contains("HEAT")) {
            state."deltaT_z${roomId}" = (dat - temp).toFloat()
        } else {
            state."deltaT_z${roomId}" = 0.0
        }
    }

    def isOcc = (settings["z${roomId}OccSwitch"]?.currentValue("switch") == "on")
    def extraOccSwitches = settings["z${roomId}ExtraOccSwitches"]
    if (extraOccSwitches && extraOccSwitches.any { it.currentValue("switch") == "on" }) isOcc = true
    
    def isNight = (settings["z${roomId}NightSwitch"]?.currentValue("switch") == "on")
    
    def loadShareModes = settings["z${roomId}LoadShareModes"]
    if (loadShareModes && (loadShareModes as List).contains(currentModeStr)) isOcc = true

    def targetMode = determineTargetMode(roomId, temp, isOcc, isNight, outTemp, cMode)
    def currentMode = state."currentMode_z${roomId}" ?: "OFF"
    
    // BMS GUARDS: Rest Timer & Changeover
    if (targetMode != "OFF" && targetMode != "FAILSAFE OFF") {
        
        // A. Anti-Short Cycle
        def restMins = settings["z${roomId}CompressorRest"] ?: 3
        def lastOff = state."lastOffTime_z${roomId}" ?: 0
        if (currentMode == "OFF" || currentMode == "FAILSAFE OFF") {
            def elapsedMins = (now() - lastOff) / 60000
            if (elapsedMins < restMins) {
                if (currentMode != "BMS COMPRESSOR LOCKOUT") {
                    logAction("${settings["z${roomId}Name"]}: BMS Guard - Compressor Rest Delay active. Delaying start.")
                    state."currentMode_z${roomId}" = "BMS COMPRESSOR LOCKOUT"
                    state."compressorLockoutTime_z${roomId}" = lastOff + (restMins * 60000)
                }
                runIn(60, "evaluateAllRooms")
                return
            }
        }
        
        // B. Changeover Delay
        def changeDelay = settings["z${roomId}ChangeoverDelay"] ?: 60
        def lastC = state."lastCoolTime_z${roomId}" ?: 0
        def lastH = state."lastHeatTime_z${roomId}" ?: 0
        
        def wantsCool = targetMode.contains("COOL")
        def wantsHeat = targetMode.contains("HEAT")
        
        if (wantsCool && lastH > 0 && ((now() - lastH) / 60000) < changeDelay) {
            if (currentMode != "CHANGEOVER DELAY") {
                logAction("${settings["z${roomId}Name"]}: BMS Guard - Changeover Delay preventing immediate switch to Cool.")
                state."currentMode_z${roomId}" = "CHANGEOVER DELAY"
                executeRFCommand(roomId, "OFF", true)
            }
            runIn(60, "evaluateAllRooms")
            return
        }
        if (wantsHeat && lastC > 0 && ((now() - lastC) / 60000) < changeDelay) {
            if (currentMode != "CHANGEOVER DELAY") {
                if (currentMode.contains("COOL")) recordCoolingCycleMaxDeltaT(roomId, currentMode)
                logAction("${settings["z${roomId}Name"]}: BMS Guard - Changeover Delay preventing immediate switch to Heat.")
                state."currentMode_z${roomId}" = "CHANGEOVER DELAY"
                executeRFCommand(roomId, "OFF", true)
            }
            runIn(60, "evaluateAllRooms")
            return
        }
    }
    
    updateRoomStats(roomId, (currentMode != "OFF" && currentMode != "FAILSAFE OFF" && !currentMode.contains("LOCKOUT") && !currentMode.contains("DELAY")))

    if (currentMode != targetMode) {
        def wasRunning = (currentMode != "OFF" && currentMode != "FAILSAFE OFF" && !currentMode.contains("LOCKOUT") && !currentMode.contains("DELAY"))
        def willRun = (targetMode != "OFF" && targetMode != "FAILSAFE OFF" && !targetMode.contains("LOCKOUT") && !targetMode.contains("DELAY"))
        
        if (!wasRunning && willRun) {
            state."dailyCycles_z${roomId}" = (state."dailyCycles_z${roomId}" ?: 0) + 1
        }

        // DELTA T RECORDING
        def wasCooling = currentMode.contains("COOL")
        def willBeCooling = targetMode.contains("COOL")

        if (wasCooling && (!willBeCooling || targetMode == "OFF" || targetMode == "FAILSAFE OFF")) {
            recordCoolingCycleMaxDeltaT(roomId, currentMode)
        }
        if (willBeCooling && !wasCooling) {
            state."currentCycleMaxDeltaT_z${roomId}" = 0.0f
        }

        def zName = settings["z${roomId}Name"] ?: "Room ${roomId}"
        logAction("${zName}: Mode changing from ${currentMode} to ${targetMode}.")
        
        if (targetMode == "OFF" || targetMode == "FAILSAFE OFF") state."lastOffTime_z${roomId}" = now()
        if (targetMode.contains("COOL")) state."lastCoolTime_z${roomId}" = now()
        if (targetMode.contains("HEAT")) state."lastHeatTime_z${roomId}" = now()
        
        state."currentMode_z${roomId}" = targetMode
        state."modeStartTime_z${roomId}" = now()
        
        executeRFCommand(roomId, targetMode, true)
    } else {
        if (targetMode.contains("COOL")) state."lastCoolTime_z${roomId}" = now()
        if (targetMode.contains("HEAT")) state."lastHeatTime_z${roomId}" = now()
        
        // BMS State Enforcement (Blind Sync)
        def enforceMins = settings["z${roomId}EnforceState"] != null ? settings["z${roomId}EnforceState"] : 30
        if (enforceMins > 0 && targetMode != "BMS COMPRESSOR LOCKOUT" && targetMode != "CHANGEOVER DELAY") {
            def lastSent = state."lastRfSentTime_z${roomId}" ?: 0
            if (((now() - lastSent) / 60000) >= enforceMins) {
                if (targetMode == "OFF" && currentMode == "OFF") {
                    state."lastRfSentTime_z${roomId}" = now()
                } else {
                    logAction("${settings["z${roomId}Name"]}: BMS Guard - State Enforcement active. Re-transmitting ${targetMode} code.")
                    executeRFCommand(roomId, targetMode, true)
                }
            }
        }
    }
}

def recordCoolingCycleMaxDeltaT(roomId, modeName) {
    def datDev = settings["z${roomId}DATSensor"]
    if (!datDev) return
    
    def maxDelta = state."currentCycleMaxDeltaT_z${roomId}" != null ? (state."currentCycleMaxDeltaT_z${roomId}" as Float) : 0.0f
    if (maxDelta <= 0.0f) {
        maxDelta = state."deltaT_z${roomId}" != null ? (state."deltaT_z${roomId}" as Float) : 0.0f
    }
    
    if (maxDelta > 0.0f) {
        def hist = state."deltaTMaxHistory_z${roomId}" ?: []
        def startMs = state."modeStartTime_z${roomId}" ?: now()
        def durationMins = Math.max(1, Math.round((now() - startMs) / 60000)).toInteger()
        
        def entry = [
            time: new Date().format("MM/dd hh:mm a", location.timeZone),
            mode: modeName,
            maxDeltaT: (Math.round(maxDelta * 10.0) / 10.0).toFloat(),
            duration: "${durationMins}m"
        ]
        hist.add(0, entry)
        if (hist.size() > 15) hist = hist[0..14]
        state."deltaTMaxHistory_z${roomId}" = hist
        logAction("${settings["z${roomId}Name"]}: Recorded cooling cycle Peak ΔT of ${entry.maxDeltaT}°F (${modeName}, ${durationMins}m run).")
    }
    state."currentCycleMaxDeltaT_z${roomId}" = 0.0f
}

def determineTargetMode(roomId, temp, isOcc, isNight, outTemp, lastMode) {
    
    // 1. Contact Failsafe Defeat
    def contacts = settings["z${roomId}Contacts"]
    if (contacts && contacts.any { it.currentValue("contact") == "open" }) {
        def anyOpen = contacts.find { it.currentValue("contact") == "open" }
        def openTime = anyOpen.events(max: 1)[0]?.date?.time ?: now()
        def timeoutMs = (settings["z${roomId}ContactTimeout"] ?: 5) * 60000
        if ((now() - openTime) >= timeoutMs) return "FAILSAFE OFF"
    }

    // 2. 24-Hour Away Rule
    if (state.awayStartTime && (now() - state.awayStartTime) >= 86400000) {
        return "OFF"
    }

    // 3. Continuous Running Logic (6 strict modes)
    def glideOffset = 0
    if (settings.enableSolarGlide && outTemp != null && outTemp.toBigDecimal() > 90.0) {
        glideOffset = Math.floor((outTemp.toBigDecimal() - 90.0) / 5.0).toInteger()
    }

    def occC = (settings["z${roomId}OccCool"] ?: 74) + glideOffset
    def occH = settings["z${roomId}OccHeat"] ?: 68
    def nightC = (settings["z${roomId}NightCool"] ?: 74) + glideOffset
    def nightH = settings["z${roomId}NightHeat"] ?: 68
    def unoccC = (settings["z${roomId}UnoccCool"] ?: 78) + glideOffset
    def unoccH = settings["z${roomId}UnoccHeat"] ?: 62

    def activeC = isNight ? nightC : (isOcc ? occC : unoccC)
    def activeH = isNight ? nightH : (isOcc ? occH : unoccH)
    def prefix = isNight ? "NIGHT" : (isOcc ? "OCC" : "UNOCC")

    def deadband = settings["z${roomId}Deadband"] ?: 2.0
    def wantsHeat = false
    
    // Hysteresis prevents rapid toggling between heat/cool
    if (lastMode.contains("HEAT")) {
        if (temp >= (activeC + deadband)) wantsHeat = false
        else wantsHeat = true
    } else if (lastMode.contains("COOL")) {
        if (temp <= (activeH - deadband)) wantsHeat = true
        else wantsHeat = false
    } else {
        // Boot-up or coming from OFF
        if (Math.abs(temp - activeH) < Math.abs(temp - activeC)) wantsHeat = true
        else wantsHeat = false
    }

    return wantsHeat ? "${prefix} HEAT" : "${prefix} COOL"
}

def executeRFCommand(roomId, targetMode, forceAction = false) {
    def broadlinkDev = settings["z${roomId}Broadlink"]
    if (!broadlinkDev) return
    
    def targetCode = null
    switch(targetMode) {
        case "OCC COOL": targetCode = settings["z${roomId}CodeOccCool"]; break
        case "OCC HEAT": targetCode = settings["z${roomId}CodeOccHeat"]; break
        case "NIGHT COOL": targetCode = settings["z${roomId}CodeNightCool"]; break
        case "NIGHT HEAT": targetCode = settings["z${roomId}CodeNightHeat"]; break
        case "UNOCC COOL": targetCode = settings["z${roomId}CodeUnoccCool"]; break
        case "UNOCC HEAT": targetCode = settings["z${roomId}CodeUnoccHeat"]; break
        case "FAILSAFE OFF": 
        case "OFF": targetCode = settings["z${roomId}CodeOff"]; break
    }

    if (forceAction && targetCode) {
        state."lastRfSentTime_z${roomId}" = now()
        try {
            broadlinkDev.sendSavedCode(targetCode)
            logDebug("Sent RF code '${targetCode}' to ${broadlinkDev.displayName} for ${targetMode}")
            
            if (targetMode != "OFF") {
                runIn(30, "retransmitRFCommand", [data: [room: roomId, code: targetCode, mode: targetMode], overwrite: false])
            }
        } catch (e) { log.error "RF Error Room ${roomId}: ${e.message}" }
    }
}

def retransmitRFCommand(data) {
    def broadlinkDev = settings["z${data.room}Broadlink"]
    if (broadlinkDev && data.code) {
        try {
            broadlinkDev.sendSavedCode(data.code)
            logAction("Reliability Sync: Sent duplicate RF code '${data.code}' for ${data.mode}")
        } catch (e) { log.error "RF Retransmit Error: ${e.message}" }
    }
}

def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        return
    } else if (btn == "btnForceSync") {
        logAction("MANUAL OVERRIDE: Forcing hardware sync...")
        evaluateAllRooms()
    } else if (btn == "btnResetMaint") {
        def numZones = settings.zoneCount?.toInteger() ?: 2
        for (int i = 1; i <= numZones; i++) {
            state."lifetimeRuntimeSecs_z${i}" = 0
            state."filterNotified_z${i}" = false
            def alertSwitch = settings["z${i}FilterAlertSwitch"]
            if (alertSwitch && alertSwitch.currentValue("switch") == "on") safeOff(alertSwitch)
        }
        logAction("ALL Maintenance timers manually reset.")
    } else if (btn.startsWith("btnResetMaint_z")) {
        def roomId = btn.split("_z")[1]
        state."lifetimeRuntimeSecs_z${roomId}" = 0
        state."filterNotified_z${roomId}" = false
        def alertSwitch = settings["z${roomId}FilterAlertSwitch"]
        if (alertSwitch && alertSwitch.currentValue("switch") == "on") safeOff(alertSwitch)
        logAction("Zone ${roomId} maintenance timer manually reset.")
    } else if (btn == "btnClearHistory") {
        def numZones = settings.zoneCount?.toInteger() ?: 2
        for (int i = 1; i <= numZones; i++) {
            state."history_z${i}" = []
            state."deltaTMaxHistory_z${i}" = []
            state."currentCycleMaxDeltaT_z${i}" = 0.0
            state."dailyCoolSecs_z${i}" = 0
            state."dailyHeatSecs_z${i}" = 0
            state."dailyCycles_z${i}" = 0
        }
        state.dailyMaxTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null
        state.dailyMaxHum = outdoorHumSensor ? outdoorHumSensor.currentValue("humidity") : null
        logAction("Performance tracking and history cleared manually.")
    }
    
    if (btn.startsWith("testBtn_")) {
        def parts = btn.split("_")
        if (parts.size() == 3) {
            def roomId = parts[1]
            def codeSuffix = parts[2]
            def targetCode = settings["z${roomId}${codeSuffix}"]
            def broadlinkDev = settings["z${roomId}Broadlink"]

            if (broadlinkDev && targetCode) {
                logInfo("TEST COMMAND: Sending ${codeSuffix} to ${broadlinkDev.displayName}")
                try { broadlinkDev.sendSavedCode(targetCode) } catch (e) { log.error "Test RF Error: ${e.message}" }
            }
        }
    }
}

def safeOn(dev) { if (dev && dev.currentValue("switch") != "on") { try { dev.on() } catch (e) {} } }
def safeOff(dev) { if (dev && dev.currentValue("switch") != "off") { try { dev.off() } catch (e) {} } }

def updateRoomStats(roomId, isRunning) {
    def lastUpdate = state."lastStatUpdate_z${roomId}" ?: now()
    def nowMs = now()
    def deltaSecs = ((nowMs - lastUpdate) / 1000).toLong()
    
    if (deltaSecs > 600 || deltaSecs < 0) deltaSecs = 0

    state."lastStatUpdate_z${roomId}" = nowMs
    def currentMode = state."currentMode_z${roomId}" ?: "OFF"
    
    if (isRunning) {
        state."lifetimeRuntimeSecs_z${roomId}" = (state."lifetimeRuntimeSecs_z${roomId}" ?: 0) + deltaSecs
        if (currentMode.contains("COOL")) {
            state."dailyCoolSecs_z${roomId}" = (state."dailyCoolSecs_z${roomId}" ?: 0) + deltaSecs
        } else if (currentMode.contains("HEAT")) {
            state."dailyHeatSecs_z${roomId}" = (state."dailyHeatSecs_z${roomId}" ?: 0) + deltaSecs
        }
    }
    
    def runtimeHrs = (state."lifetimeRuntimeSecs_z${roomId}" ?: 0) / 3600.0
    def percentLeft = Math.max(0.0, 100.0 - ((runtimeHrs / 250.0) * 100)).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
    def deltaT = state."deltaT_z${roomId}" ?: 0.0

    if (notificationDevice) {
        def alertThreshold = settings.filterAlertPercent != null ? settings.filterAlertPercent : 15.0
        
        if (settings.notifyFilter != false && percentLeft <= alertThreshold && !state."filterNotified_z${roomId}") {
            notificationDevice.deviceNotification("BMS Alert: ${settings["z${roomId}Name"]} filter is at ${percentLeft}% life remaining. Please clean/replace it soon.")
            state."filterNotified_z${roomId}" = true
        }
        
        if (settings.notifyDeltaT != false && isRunning) {
            def deltaTDelayMs = (settings.deltaTDelay != null ? settings.deltaTDelay : 30) * 60000
            
            // --- NEW LOGIC: Calculate current setpoint and determine if the target is met ---
            def tempDev = settings["z${roomId}Temp"]
            def currentTemp = tempDev ? (tempDev.currentValue("temperature") ?: 72.0).toFloat() : null
            def outTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null
            def glideOffset = 0
            
            if (settings.enableSolarGlide && outTemp != null && outTemp.toBigDecimal() > 90.0) {
                glideOffset = Math.floor((outTemp.toBigDecimal() - 90.0) / 5.0).toInteger()
            }
            
            def activeSetpoint = null
            switch(currentMode) {
                case "OCC COOL": activeSetpoint = (settings["z${roomId}OccCool"] ?: 74) + glideOffset; break
                case "UNOCC COOL": activeSetpoint = (settings["z${roomId}UnoccCool"] ?: 78) + glideOffset; break
                case "NIGHT COOL": activeSetpoint = (settings["z${roomId}NightCool"] ?: 74) + glideOffset; break
                case "OCC HEAT": activeSetpoint = settings["z${roomId}OccHeat"] ?: 68; break
                case "UNOCC HEAT": activeSetpoint = settings["z${roomId}UnoccHeat"] ?: 62; break
                case "NIGHT HEAT": activeSetpoint = settings["z${roomId}NightHeat"] ?: 68; break
            }
            
            def isTargetSatisfied = false
            if (currentTemp != null && activeSetpoint != null) {
                if (currentMode.contains("COOL") && currentTemp <= activeSetpoint) {
                    isTargetSatisfied = true
                } else if (currentMode.contains("HEAT") && currentTemp >= activeSetpoint) {
                    isTargetSatisfied = true
                }
            }

            // --- DELTA-T SMART EVALUATION ---
            if (isTargetSatisfied || Math.abs(deltaT) >= 12.0) {
                // The compressor is naturally idling because the room reached its temperature,
                // OR the Delta T is healthy and performing well. Suppress alerts and clear timers.
                state."deltaTWarningStart_z${roomId}" = null
                state."deltaTNotified_z${roomId}" = false
            } else {
                // The room is missing its target AND the Delta T is weak. Start the warning timer.
                if (!state."deltaTWarningStart_z${roomId}") {
                    state."deltaTWarningStart_z${roomId}" = nowMs
                } else if ((nowMs - state."deltaTWarningStart_z${roomId}") > deltaTDelayMs) {
                    // Timer exceeded the allowed delay without the Delta-T recovering.
                    if (!state."deltaTNotified_z${roomId}") {
                        notificationDevice.deviceNotification("BMS Critical: ${settings["z${roomId}Name"]} Delta T is severely low (${String.format('%.1f', deltaT)}°). The room is struggling to reach its target of ${activeSetpoint}° without expected cooling/heating power. Check for frozen coils, dirty filter, or low refrigerant.")
                        state."deltaTNotified_z${roomId}" = true
                    }
                }
            }
        } else {
            state."deltaTWarningStart_z${roomId}" = null
            state."deltaTNotified_z${roomId}" = false
        }
    }
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
