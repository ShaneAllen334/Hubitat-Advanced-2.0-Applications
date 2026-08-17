/**
 * Advanced Mold & Dehumidification Manager 2.0
 *
 */
definition(
    name: "Advanced Mold & Dehumidification Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        def maxZones = (settings.numZones ?: 1) as int
        if (maxZones > 15) maxZones = 15

        section("") {
        
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
            
            def globalAvg = getHouseAverageHum()
            def houseMode = isWholeHouseAveragingTriggered(globalAvg) ? "<span style='color:red;'>TRIGGERED</span>" : "Stable"
            def sysStatus = "App is running normally. Averaging System is ${houseMode}."
            if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") sysStatus = "<span style='color:red;'><b>DISABLED</b></span> (Master Switch is OFF)"
            else if (isWinterShieldActive()) sysStatus = "<span style='color:blue;'><b>WINTER SHIELD ACTIVE</b></span>"

            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>System Status:</b> ${sysStatus}</div>"
            
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 20%; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Room & Status</th><th>Timer</th><th>Shower Hits</th><th>Environment / Goal</th><th>System Reason & Action</th><th>Prolonged Mold Risk (ASHRAE)</th></tr></thead>
                <tbody>
            """
            
            def hasZones = false

            for (int i = 1; i <= maxZones; i++) {
                if (settings["z${i}Name"]) {
                
                    hasZones = true
                    def zData = calculateZoneState(i)
                    
                    def pwrBadge = ""
                    if (zData.isLeaking) { pwrBadge = "<span style='background:#c0392b; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>EMERGENCY (WET)</span>" }
                    else if (zData.windowOpen) { pwrBadge = "<span style='background:#34495e; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>PAUSED (WINDOW)</span>" }
                    else if (zData.tankFull) { pwrBadge = "<span style='background:#e74c3c; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>TANK FULL</span>" }
                    else if (zData.acSynergyActive) { pwrBadge = "<span style='background:#8e44ad; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>AC SYNERGY (IDLE)</span>" }
                    else if (zData.hardwareOn) {
                        if (zData.status.contains("Shower")) pwrBadge = "<span style='background:#3498db; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (SHOWER)</span>"
                        else if (zData.status.contains("Compressor")) pwrBadge = "<span style='background:#f39c12; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (PROTECT)</span>"
                        else pwrBadge = "<span style='background:#27ae60; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (ACTIVE)</span>"
                    } 
                    else { pwrBadge = "<span style='background:#95a5a6; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>OFF (STANDBY)</span>" }
                    
                    def filterHtml = ""
                    if (zData.filterEnabled) {
                        def fColor = zData.filterHealth <= zData.filterThresh ? "#e74c3c" : "#27ae60"
                        filterHtml = "<br><span style='font-size:11px; color:${fColor};'><b>Filter: ${zData.filterHealth}%</b></span>"
                    }

                    def actionHtml = ""
                    if (zData.tankFull) {
                        actionHtml += "<div style='margin-top:4px;'><span style='color:#e74c3c; font-weight:bold; font-size:11px;'>Action: Reset Tank</span></div>"
                    }
                    if (zData.filterEnabled && zData.filterHealth <= zData.filterThresh) {
                        actionHtml += "<div style='margin-top:4px;'><span style='color:#e74c3c; font-weight:bold; font-size:11px;'>Action: Clean Filter</span></div>"
                    }

                    def timerDisplay = zData.countdown ?: "<span style='color:gray;'>--</span>"
                    
                    def showerHitsDisplay = "<span style='color:lightgray;'>N/A</span>"
                    if (settings["z${i}EnableShower"]) {
                        def reqHits = settings["z${i}ShowerHits"] ?: 2
                        def windowMins = settings["z${i}ShowerWindow"] ?: 3
                        def windowMs = windowMins * 60000
                        def hitHistory = state["z${i}MotionHits"] ?: []
                        def validHits = hitHistory.findAll { (now() - it.toLong()) <= windowMs }.size()
                        def hitColor = validHits >= reqHits ? "green" : "black"
                        showerHitsDisplay = "<span style='color:${hitColor};'><b>${validHits} / ${reqHits}</b><br><span style='font-size:11px;color:gray;'>(in ${windowMins}m)</span></span>"
                    } else {
                        showerHitsDisplay = "<span style='color:gray;'>Disabled</span>"
                    }

                    def logicDisplay = "<span style='color:#333;'><b>Reason:</b> ${zData.status}</span>"
                    if (zData.showerTracker && zData.showerTracker != "Idle") {
                        def trackerColor = "#3498db" 
                        if (zData.showerTracker == "Grace Period") trackerColor = "#e67e22"
                        else if (zData.showerTracker.toString().contains("Verifying")) trackerColor = "#f39c12"
                        
                        logicDisplay += "<br><div style='margin-top:4px;'><span style='color:${trackerColor}; font-weight:bold;'>Shower Tracking: ${zData.showerTracker}</span></div>"
                    }
                    
                    def envDisplay = "<b>${zData.currHum}% RH</b>"
                    if (zData.currTemp) {
                        envDisplay += " / ${zData.currTemp}°F"
                        if (zData.dewPoint != null) {
                            def spreadColor = zData.spread <= 3.0 ? "#cc0000" : (zData.spread <= 6.0 ? "#e67e22" : "#27ae60")
                            envDisplay += "<br><small style='color:#555;'>Dew Pt: ${zData.dewPoint}°F<br>Spread: <span style='color:${spreadColor}; font-weight:bold;'>+${zData.spread}°F</span> away</small>"
                        }
                    }
                    
                    def riskHtml = "<span style='color:${zData.riskColor}; font-weight:bold;'>${zData.riskLevel}</span><br><small style='color:#555;'>Moisture Load: ${zData.riskHours} hrs</small>"
                    
                    dashHTML += "<tr>"
                    dashHTML += "<td class='dash-hl'><b>${settings["z${i}Name"]}</b><br><div style='margin-top:6px; margin-bottom:4px;'>${pwrBadge}</div>${filterHtml}${actionHtml}</td>"
                    dashHTML += "<td>${timerDisplay}</td>"
                    dashHTML += "<td>${showerHitsDisplay}</td>"
                    dashHTML += "<td>${envDisplay}<br><small style='color:#666;'>On: ${zData.trigger}% | Off: ${zData.target}%</small></td>"
                    dashHTML += "<td style='text-align:left;'>${logicDisplay}<br><small style='color:#7f8c8d; margin-top:4px; display:inline-block;'>Run Today: ${zData.duration}</small></td>"
                    dashHTML += "<td>${riskHtml}</td>"
                    dashHTML += "</tr>"
                }
            }
            if (!hasZones) dashHTML += "<tr><td colspan='6'><i>No rooms configured yet. Set total rooms below.</i></td></tr>"
            dashHTML += "</tbody></table>"
            paragraph dashHTML
            
            def outTemp = outdoorTempSensor?.currentValue("temperature")
            def outHum = outdoorHumSensor?.currentValue("humidity")
            def outDp = calculateDewPoint(outTemp, outHum)
            def outDpDisplay = outDp != null ? "${outDp}°F" : "N/A"
            
            paragraph "<div style='font-size: 13px; background: #f8f9fa; padding: 10px; border-radius: 5px; border: 1px solid #ccc; margin-top: 10px;'><b>Outdoor Dew Point:</b> ${outDpDisplay} | <b>House Avg:</b> ${globalAvg}%</div>"
        }

        section("<b>🌬️ Maintenance & Resets (Filters & Tanks)</b>", hideable: true, hidden: true) {
            def hasMaintenanceItems = false
            
            for (int i = 1; i <= maxZones; i++) {
                if (settings["z${i}Name"]) {
                    def zData = calculateZoneState(i)
                    if (zData.tankFull) {
                        hasMaintenanceItems = true
                        input "btnResetTank_${i}", "button", title: "💧 Clear ${settings["z${i}Name"]} Tank"
                    }
                }
            }

            for (int i = 1; i <= maxZones; i++) {
                if (settings["z${i}Name"] && settings["z${i}EnableFilter"]) {
                    hasMaintenanceItems = true
                    def zData = calculateZoneState(i)
                    input "btnResetFilter_${i}", "button", title: "Reset ${settings["z${i}Name"]} Filter (${zData.filterHealth}%)"
                }
            }
            
            if (!hasMaintenanceItems) paragraph "<i>No tanks are currently full, and no filters are tracking. Check individual room settings to enable filter tracking.</i>"
        }

        section("<b>Shower Analytics (Last 10 Sessions)</b>", hideable: true, hidden: true) {
            def hasShowers = false
            for (int i = 1; i <= maxZones; i++) {
                if (settings["z${i}Name"] && settings["z${i}EnableShower"]) {
                    hasShowers = true
                    def sName = settings["z${i}Name"]
                    def logList = state["z${i}SessionLog"] ?: []
                    
                    if (logList.size() > 0) {
                        def logText = "<div style='margin-bottom: 15px;'><b>${sName}</b><br><table class='dash-table' style='margin-top:4px;'>"
                        logText += "<thead><tr><th>Date & Time</th><th>Duration</th><th>Volume</th><th>Est. Cost</th></tr></thead><tbody>"
                        
                        logList.each { entry ->
                            logText += "<tr><td>${entry.time}</td><td><b>${entry.duration}</b></td><td style='color: #3498db;'>${entry.gallons}</td><td style='color: #27ae60;'><b>${entry.cost}</b></td></tr>"
                        }
                        logText += "</tbody></table></div>"
                        paragraph logText
                    } else {
                        paragraph "<b>${sName}:</b> <i>No shower data recorded yet.</i>"
                    }
                }
            }
            if (!hasShowers) paragraph "<i>No zones currently have Shower Mode enabled.</i>"
            input "clearShowerDataBtn", "button", title: "Clear All Shower Financial Data"
        }

        section("<b>Event History (Last 25 Events)</b>", hideable: true, hidden: true) {
            def hist = state.eventLog ?: []
            paragraph "<div style='font-size: 13px; font-family: monospace; max-height: 250px; overflow-y: auto; background: #f9f9f9; padding: 8px; border: 1px solid #ccc; border-radius: 4px;'>${hist.join("<br>")}</div>"
            input "clearHistoryBtn", "button", title: "Clear History Log"
        }

        section("<b>App Control & Diagnostics</b>", hideable: true, hidden: true) {
            input "numZones", "number", title: "<b>How many rooms/zones do you want to configure?</b>", defaultValue: 1, submitOnChange: true
            paragraph "<hr>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable Switch", required: false
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "globalTankSwitch", "capability.switch", title: "Global Tank Full Output Switch", required: false
        }

        section("<b>Global Advanced Logic (AC Synergy & Winter Shield)</b>", hideable: true, hidden: true) {
            input "enableAvgSystem", "bool", title: "<b>Enable Averaging System</b>", defaultValue: false, submitOnChange: true
            
            if (enableAvgSystem) {
                input "avgHumThreshold", "number", title: "House Average Humidity Threshold (%)", defaultValue: 65
                input "avgHumDeadband", "number", title: "Averaging Deadband (%)", defaultValue: 5
            }

            paragraph "<hr>"
            
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor (Required for Winter Shield)", required: false, submitOnChange: true
            input "outdoorHumSensor", "capability.relativeHumidityMeasurement", title: "Outdoor Humidity Sensor", required: false
            
            input "enableWinterShield", "bool", title: "<b>Enable Dynamic Winter Shield</b>", defaultValue: false, submitOnChange: true
            if (enableWinterShield) {
                paragraph "<div style='font-size:12px; color:#555;'>Uses a sliding scale based on the outdoor temperature to prevent window sweat in older homes while saving energy.</div>"
                input "winterTempThreshold", "number", title: "Activate Shield when Outdoor Temp drops below (°F)", defaultValue: 45
            }
        }

        section("<b>Zone Specific Mold Alerts</b>", hideable: true, hidden: true) {
            input "enableZoneAlerts", "bool", title: "<b>Enable Room-Specific Mold Warnings?</b>", defaultValue: false, submitOnChange: true
            
            if (enableZoneAlerts) {
                input "zoneAlertDevices", "capability.notification", title: "Notification Devices for Room Alerts", multiple: true, required: false
                paragraph "<hr>"
                for (int i = 1; i <= maxZones; i++) {
                    if (settings["z${i}Name"]) {
                        input "z${i}EnableAlert", "bool", title: "Enable Alert for <b>${settings["z${i}Name"]}</b>?", submitOnChange: true
                        if (settings["z${i}EnableAlert"]) {
                            input "z${i}AlertThresh", "number", title: "  ↳ Alert Threshold (%)", defaultValue: 60
                            input "z${i}AlertHours", "number", title: "  ↳ Time Required Above Threshold (Hours)", defaultValue: 24
                        }
                    }
                }
            }
        }

        for (int i = 1; i <= maxZones; i++) {
            def currentZoneName = settings["z${i}Name"] ?: "Room ${i}"
            section("<b>⚙️ ${currentZoneName} Configuration</b>", hideable: true, hidden: true) {
                input "z${i}Name", "text", title: "Room Name", submitOnChange: true
                
                if (settings["z${i}Name"]) {
                    paragraph "<b>Primary Hardware & Modes</b>"
                
                    input "z${i}Hum", "capability.relativeHumidityMeasurement", title: "Humidity Sensor", required: true
                    input "z${i}Temp", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true
                    input "z${i}Dehum", "capability.switch", title: "Dehumidifier(s)", multiple: true, required: true
                    input "z${i}Modes", "mode", title: "Allowed Modes (Leaves Dehum OFF in unselected modes)", multiple: true, required: false
                    input "z${i}LightMode", "bool", title: "Light Control & Monitor Mode (Bypass strict compressor protections)", defaultValue: false, submitOnChange: true
                    
                    input "z${i}EnableFilter", "bool", title: "Enable Filter Life Tracking", defaultValue: false, submitOnChange: true
                    if (settings["z${i}EnableFilter"]) {
                        input "z${i}FilterLife", "number", title: "  ↳ Filter Life Expectancy (Run Hours)", defaultValue: 720
                        input "z${i}FilterAlertThresh", "number", title: "  ↳ Filter Alert Threshold (%)", defaultValue: 10
                    }

                    paragraph "<b>Interlocks & Context</b>"
                    
                    input "z${i}EnableInterlocks", "bool", title: "Enable Interlocks & Context (Leaks, Windows, TV, AC)", defaultValue: false, submitOnChange: true
                    
                    if (settings["z${i}EnableInterlocks"]) {
                        input "z${i}Leak", "capability.waterSensor", title: "↳ Emergency Water Leak Sensors (Forces ON if wet)", multiple: true, required: false
                        input "z${i}Window", "capability.contactSensor", title: "↳ Window/Door Interlock (Forces OFF if open)", multiple: true, required: false
                        input "z${i}TV", "capability.switch", title: "↳ TV / Media Switch", multiple: true, required: false, submitOnChange: true
                        if (settings["z${i}TV"]) {
                            input "z${i}TVOffset", "number", title: "  ↳ TV ON Setpoint Increase (%)", defaultValue: 5
                        }
                        
                        input "z${i}AcSynergy", "bool", title: "↳ <b>Enable AC Latent Heat Intercept</b> (Pause dehum if Central AC is actively cooling the house)", defaultValue: true
                        if (settings["z${i}AcSynergy"]) {
                            input "z${i}Thermostat", "capability.thermostat", title: "  ↳ Central Thermostat Device", required: true
                        }
                    }

                    paragraph "<b>Shower Mode (No-Exhaust Smart Sweep)</b>"
                    input "z${i}EnableShower", "bool", title: "Enable Shower Smart Sweep", defaultValue: false, submitOnChange: true
                    if (settings["z${i}EnableShower"]) {
                        input "z${i}ShowerMotion", "capability.motionSensor", title: "↳ Shower Motion Sensor(s)", multiple: true, required: true
                        
                        paragraph "<i>Trigger Conditions (Any of these will engage Shower Mode)</i>"
                        input "z${i}ShowerHumTrigger", "number", title: "↳ 1. High Humidity Trigger (%)", defaultValue: 70
                        input "z${i}ShowerDelay", "number", title: "↳ 2. Continuous Motion Required (Seconds)", defaultValue: 30
                        input "z${i}ShowerHits", "number", title: "↳ 3. Required Motion Active Events", defaultValue: 2
                        input "z${i}ShowerWindow", "number", title: "   Within Time Window (Minutes)", defaultValue: 3
                        
                        input "z${i}ShowerMaxTime", "number", title: "↳ Failsafe Max Run Time (Mins)", defaultValue: 60, description: "Maximum time the unit will run if the bathroom fails to equalize with the house average."
                        
                        paragraph "<i>Volume & Financial Tracking Options</i>"
                        input "z${i}OutMotion", "capability.motionSensor", title: "↳ Out of Shower Motion Sensor (Optional)", required: false
                        input "z${i}GuestSwitch", "capability.switch", title: "↳ Guest Mode Switch (Disable warning lights)", required: false
                        input "z${i}Light", "capability.switch", title: "↳ Bathroom Light (For warnings & auto-off)", required: false
                        input "z${i}FlowRate", "decimal", title: "↳ Showerhead Flow Rate (GPM)", defaultValue: 2.5
                        input "z${i}CostPerGallon", "decimal", title: "↳ Cost per Gallon of Hot Water (\$)", defaultValue: 0.03
                        input "z${i}Warn1", "number", title: "↳ 1st Warning Flashes (Minutes)", defaultValue: 5
                        input "z${i}Warn2", "number", title: "↳ 2nd Warning Flashes (Minutes)", defaultValue: 8
                        input "z${i}Warn3", "number", title: "↳ 3rd Warning Flashes (Minutes)", defaultValue: 10
                        input "z${i}GracePeriod", "number", title: "↳ Grace Period (Minutes)", defaultValue: 2
                        input "z${i}MinDuration", "number", title: "↳ Min Duration to Log (Seconds)", defaultValue: 60
                        input "z${i}LockoutPeriod", "number", title: "↳ Post-Shower Tracking Lockout (Minutes)", defaultValue: 2
                    }

                    paragraph "<b>Tank Full Diagnostics</b>"
                    input "z${i}TankMethod", "enum", title: "Detection Method", options: ["None", "Power Meter", "Humidity Stall"], submitOnChange: true
                    if (settings["z${i}TankMethod"] == "Power Meter") {
                        input "z${i}Power", "capability.powerMeter", title: "↳ Smart Plug with Power Meter", multiple: true, required: true
                        input "z${i}ActiveWatts", "number", title: "↳ Active Compressor Watts Threshold", defaultValue: 100
                    } else if (settings["z${i}TankMethod"] == "Humidity Stall") {
                        if (!settings["z${i}AcSynergy"]) {
                            input "z${i}Thermostat", "capability.thermostat", title: "↳ HVAC Thermostat (Needed to prevent false alarms from AC)", required: true
                        }
                        input "z${i}StallMins", "number", title: "↳ Stall Time Limit (Mins)", defaultValue: 90
                        input "z${i}StallDrop", "number", title: "↳ Required Drop (%)", defaultValue: 5
                    }

                    paragraph "<b>Humidity Targets</b>"
                    if (settings.enableWinterShield) {
                        def wThresh = settings.winterTempThreshold ?: 45
                        paragraph "<div style='font-size:13px; color:#3498db; background-color:#eaf2f8; padding:8px; border-radius:4px; border-left:4px solid #3498db;'><b>❄️ Winter Shield Enabled globally:</b> If outdoor temp drops below ${wThresh}°F, the target will dynamically slide to prevent window sweat.</div>"
                    }

                    input "z${i}Trigger", "number", title: "Trigger Humidity (Turn ON Point %)", defaultValue: 60
                    input "z${i}Target", "number", title: "Target Humidity (Turn OFF Point %)", defaultValue: 55
                    input "z${i}OffDelay", "number", title: "Extended Run Time (Minutes to run after reaching target)", defaultValue: 0
                    
                    input "z${i}CompProtect", "bool", title: "Enable Compressor Protection", defaultValue: true, submitOnChange: true
                    if (settings["z${i}CompProtect"]) {
                        input "z${i}MinRun", "number", title: "↳ Minimum Run Time (Minutes)", defaultValue: 15
                    }
                }
            }
        }
        
        section("<b>Global System Notifications</b>", hideable: true, hidden: true) {
            input "leakNotifyDevices", "capability.notification", title: "🚨 Emergency Leak Notification Devices", multiple: true, required: false
            input "tankNotifyDevices", "capability.notification", title: "🚰 Tank Full Notification Devices", multiple: true, required: false
            input "filterNotifyDevices", "capability.notification", title: "🌬️ Filter Alert Notification Devices", multiple: true, required: false
            
            paragraph "<hr>"
            input "randomTankSwitch", "capability.switch", title: "🎲 Random Daily Reminder Switch (Any Tank Full)", required: false
            input "randomFilterSwitch", "capability.switch", title: "🎲 Random Daily Reminder Switch (Any Filter Dirty)", required: false

            paragraph "<hr>"
            input "notifyStartTime", "time", title: "Quiet Window Start", required: false
            input "notifyEndTime", "time", title: "Quiet Window End", required: false
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard manual refresh triggered by user.")
        evaluateZones()
    } else if (btn == "clearShowerDataBtn") {
        for (int i = 1; i <= 15; i++) {
            state["z${i}SessionLog"] = []
        }
        logAction("All tracked shower session data logs have been cleared.")
    } else if (btn == "clearHistoryBtn") {
        state.eventLog = []
        logAction("Event history log cleared.")
    } else {
        for (int i = 1; i <= 15; i++) {
            if (btn == "btnResetTank_${i}") {
                resetTankFullFlag(i, "Manual Dashboard Reset")
                evaluateZones() 
            }
            if (btn == "btnResetFilter_${i}") {
                state["z${i}FilterRunMs"] = 0
                state["z${i}FilterNotified"] = false
                logAction("${settings["z${i}Name"]}: Filter life has been reset to 100%.")
                evaluateZones()
            }
        }
    }
}

def calculateDewPoint(tempF, hum) {
    if (tempF == null || hum == null) return null
    def tempC = (tempF - 32.0) * 5.0 / 9.0
    def a = 17.625
    def b = 243.04
    def alpha = Math.log(hum / 100.0) + ((a * tempC) / (b + tempC))
    def dpC = (b * alpha) / (a - alpha)
    def dpF = (dpC * 9.0 / 5.0) + 32.0
    return Math.round(dpF * 10.0) / 10.0
}

def getHouseAverageHum() {
    def total = 0.0
    def count = 0
    for (int i = 1; i <= 15; i++) {
        if (settings["z${i}Name"] && settings["z${i}Hum"]) {
            def h = settings["z${i}Hum"].currentValue("humidity")
            if (h != null) { total += h; count++ }
        }
    }
    return count > 0 ? (Math.round((total / count) * 10.0) / 10.0) : 0
}

def isWinterShieldActive() {
    if (!enableWinterShield || !outdoorTempSensor) return false
    def outT = outdoorTempSensor.currentValue("temperature")
    def wThresh = settings.winterTempThreshold ?: 45
    return (outT != null && outT <= wThresh)
}

def calculateDynamicWinterTarget(outTemp) {
    if (outTemp == null) return 35
    def target = Math.round((outTemp * 0.5) + 20)
    if (target > 45) target = 45
    if (target < 25) target = 25
    return target
}

def isWholeHouseAveragingTriggered(currentAvg) {
    if (!enableAvgSystem) return false
    def wasTriggered = (state.averagingActive == true)
    def threshold = avgHumThreshold ?: 65
    def db = avgHumDeadband ?: 5
    if (currentAvg >= threshold) return true
    if (wasTriggered && currentAvg > (threshold - db)) return true
    return false
}

def resetTankFullFlag(i, reason) {
    def roomName = settings["z${i}Name"] ?: "Room ${i}"
    state["z${i}TankFull"] = false
    state["z${i}StallBaseRH"] = null
    state["z${i}StallStart"] = null 
    state["z${i}TankMaxRH"] = null
    state["z${i}TankNotified"] = false
    logAction("${roomName}: Tank Full flag CLEARED. Reason: ${reason}")
}

def midnightHandler() {
    logAction("SYSTEM: Executing daily midnight rollover for runtimes.")
    for (int i = 1; i <= 15; i++) {
        if (!settings["z${i}Name"]) continue
        state["z${i}YesterdayRunMs"] = state["z${i}DailyRunMs"] ?: 0
        state["z${i}DailyRunMs"] = 0
    }
    scheduleRandomReminders()
}

def showerMotionHandler(evt) {
    for (int i = 1; i <= 15; i++) {
        if (settings["z${i}Name"] && settings["z${i}EnableShower"]) {
            def mList = settings["z${i}ShowerMotion"] instanceof List ? settings["z${i}ShowerMotion"] : (settings["z${i}ShowerMotion"] ? [settings["z${i}ShowerMotion"]] : [])
            if (mList.any { it.id == evt.device.id }) {
                handleShowerMotion(i, evt.value)
            }
        }
    }
}

def outMotionHandler(evt) {
    for (int i = 1; i <= 15; i++) {
        if (settings["z${i}Name"] && settings["z${i}EnableShower"]) {
            def outList = settings["z${i}OutMotion"] instanceof List ? settings["z${i}OutMotion"] : (settings["z${i}OutMotion"] ? [settings["z${i}OutMotion"]] : [])
            if (outList.any { it.id == evt.device.id }) {
                handleOutMotion(i, evt.value)
            }
        }
    }
}

def handleShowerMotion(zone, motionState) {
    def sName = settings["z${zone}Name"]

    if (motionState == "active") {
        if (!state["z${zone}ShowerActive"]) {
            
            def delaySecs = settings["z${zone}ShowerDelay"] != null ? settings["z${zone}ShowerDelay"] : 30
            if (!state["z${zone}ShowerMotionStart"]) {
                state["z${zone}ShowerMotionStart"] = now()
                state["z${zone}ShowerStatus"] = "Verifying..."
                runIn(delaySecs, "verifyShowerStart", [data: [zone: zone], overwrite: false])
            }
            
            def reqHits = settings["z${zone}ShowerHits"] ?: 2
            def windowMins = settings["z${zone}ShowerWindow"] ?: 3
            def windowMs = windowMins * 60000
            
            def hitHistory = state["z${zone}MotionHits"] ? new ArrayList(state["z${zone}MotionHits"]) : []
            def cutoff = now() - windowMs
            hitHistory = hitHistory.findAll { it.toLong() > cutoff } 
            
            hitHistory.add(now()) 
            state["z${zone}MotionHits"] = hitHistory
            
            if (hitHistory.size() >= reqHits) {
                state["z${zone}MotionHits"] = [] 
                startShowerSession(zone)
            } else {
                evaluateZones() 
            }
            
        } else {
            state["z${zone}ExpectedEndTime"] = 0 
            state["z${zone}ShowerStatus"] = "Active"
            evaluateZones()
        }
    } else {
        state["z${zone}ShowerMotionStart"] = null 
        
        if (state["z${zone}ShowerActive"]) {
            def grace = settings["z${zone}GracePeriod"] ?: 2
            state["z${zone}ShowerStatus"] = "Grace Period"
            state["z${zone}ShowerInactiveTime"] = now()
            state["z${zone}ExpectedEndTime"] = now() + (grace * 60000)
            runIn((grace * 60) + 1, "endShowerGeneric", [data: [zone: zone], overwrite: false])
            evaluateZones()
        } else {
            if (state["z${zone}ShowerStatus"] == "Verifying...") {
                state["z${zone}ShowerStatus"] = "Idle"
                evaluateZones()
            }
            def windowMins = settings["z${zone}ShowerWindow"] ?: 3
            runIn((windowMins * 60) + 1, "clearMotionHitsGeneric", [data: [zone: zone], overwrite: false])
        }
    }
}

def verifyShowerStart(data) {
    def zone = data.zone
    if (state["z${zone}ShowerActive"]) return 
    
    def startTime = state["z${zone}ShowerMotionStart"]
    def delaySecs = settings["z${zone}ShowerDelay"] != null ? settings["z${zone}ShowerDelay"] : 30
    
    if (startTime && (now() - startTime.toLong()) >= ((delaySecs * 1000) - 2000)) {
        startShowerSession(zone)
    } else {
        state["z${zone}ShowerStatus"] = "Idle"
        evaluateZones()
    }
}

def clearMotionHitsGeneric(data) {
    def zone = data.zone
    if (!state["z${zone}ShowerActive"]) {
        def hitHistory = state["z${zone}MotionHits"] ? new ArrayList(state["z${zone}MotionHits"]) : []
        def windowMins = settings["z${zone}ShowerWindow"] ?: 3
        def cutoff = now() - (windowMins * 60000)
        hitHistory = hitHistory.findAll { it.toLong() > cutoff }
        state["z${zone}MotionHits"] = hitHistory
        
        if (hitHistory.size() == 0 && state["z${zone}ShowerStatus"]?.toString()?.contains("Verifying")) {
            state["z${zone}ShowerStatus"] = "Idle"
            evaluateZones()
        }
    }
}

def startShowerSession(zone) {
    def maxRunTime = settings["z${zone}ShowerMaxTime"] ?: 60
    def sName = settings["z${zone}Name"]
    def lockout = settings["z${zone}LockoutPeriod"] ?: 2

    if (!state["z${zone}ShowerActive"]) {
        def lastEndTime = state["z${zone}ShowerEndTime"] ?: 0
        if (now() - lastEndTime < (lockout * 60 * 1000)) {
            logAction("${sName}: Shower start ignored (Blocked by ${lockout}-minute Lockout Period).")
            return 
        }
    }

    state["z${zone}ExpectedEndTime"] = 0 
    state["z${zone}PostShowerPhase"] = false
    state["z${zone}LightOffTime"] = 0

    def isAlreadyActive = state["z${zone}ShowerActiveUntil"] && now() < state["z${zone}ShowerActiveUntil"]
    state["z${zone}ShowerActiveUntil"] = now() + (maxRunTime * 60000)

    if (!state["z${zone}ShowerActive"]) {
        state["z${zone}ShowerActive"] = true
        state["z${zone}ShowerStartTime"] = now()
        state["z${zone}ShowerStatus"] = "Active"
        
        state["z${zone}ShowerPeakReached"] = false
        
        logAction("${sName}: Shower Mode initialized.")

        def w1 = settings["z${zone}Warn1"] ?: 5
        def w2 = settings["z${zone}Warn2"] ?: 8
        def w3 = settings["z${zone}Warn3"] ?: 10

        state["z${zone}Warn1Time"] = now() + (w1 * 60000)
        state["z${zone}Warn2Time"] = now() + (w2 * 60000)
        state["z${zone}Warn3Time"] = now() + (w3 * 60000)

        runIn(w1 * 60, "triggerWarningGeneric", [data: [zone: zone, tier: 1, flashes: 1], overwrite: false])
        runIn(w2 * 60, "triggerWarningGeneric", [data: [zone: zone, tier: 2, flashes: 2], overwrite: false])
        runIn(w3 * 60, "triggerWarningGeneric", [data: [zone: zone, tier: 3, flashes: 3], overwrite: false])
    } else {
        state["z${zone}ExpectedEndTime"] = 0
        state["z${zone}ShowerStatus"] = "Active"
    }

    if (!isAlreadyActive) {
        logAction("Dynamic Smart Sweep initialized in ${sName} (Max failsafe: ${maxRunTime} mins).")
        runIn(1, "evaluateZones", [overwrite: true])
    }
}

def handleOutMotion(zone, motionState) {
    if (motionState == "active") {
        if (state["z${zone}ShowerStatus"] == "Grace Period") {
            def inactiveDuration = now() - (state["z${zone}ShowerInactiveTime"] ?: now())
            if (inactiveDuration > 30000) { 
                logAction("${settings["z${zone}Name"]}: Presence outside shower detected. Terminating session.")
                state["z${zone}ExpectedEndTime"] = 0 
                terminateShower(zone, true)
            } else {
                logAction("${settings["z${zone}Name"]}: Out-of-shower motion ignored (Grace Period buffer active to prevent false termination).")
            }
        }
        if (state["z${zone}PostShowerPhase"]) {
            state["z${zone}LightOffTime"] = 0 
        }
    } else {
        if (state["z${zone}PostShowerPhase"]) {
            state["z${zone}LightOffTime"] = now() + 300000
            runIn(300, "checkPostShowerLightGeneric", [data: [zone: zone], overwrite: false])
        }
    }
}

def endShowerGeneric(data) {
    def zone = data.zone
    if (state["z${zone}ShowerStatus"] == "Grace Period") {
        if (state["z${zone}ExpectedEndTime"] && now() >= (state["z${zone}ExpectedEndTime"] - 60000)) {
            terminateShower(zone, false)
        }
    }
}

def triggerWarningGeneric(data) {
    def zone = data.zone
    if (state["z${zone}ShowerStatus"] == "Active") {
        def targetTime = state["z${zone}Warn${data.tier}Time"] ?: 0
        if (targetTime && now() >= (targetTime - 60000)) {
            triggerFlash(zone, data.flashes)
        }
    }
}

def terminateShower(zone, earlyTerminate = false) {
    def sName = settings["z${zone}Name"] ?: "Room ${zone}"
    def startTime = state["z${zone}ShowerStartTime"] ?: now()
    def minDuration = settings["z${zone}MinDuration"] ?: 60
    
    def endTime = earlyTerminate ? (state["z${zone}ShowerInactiveTime"] ?: now()) : now()
    def graceSecs = earlyTerminate ? 0 : ((settings["z${zone}GracePeriod"] ?: 2) * 60)
    
    def totalMillis = endTime - startTime - (graceSecs * 1000)
    if (totalMillis < 0) totalMillis = 0 
    
    def totalSecs = (totalMillis / 1000) as Integer
    def mins = (totalSecs / 60) as Integer
    def secs = totalSecs % 60
    def durationStr = "${mins}m ${secs}s"
    
    if (totalSecs < minDuration) {
        logAction("${sName}: Shower session discarded (Under ${minDuration}s filter). Lockout bypassed.")
        state["z${zone}ShowerEndTime"] = 0 
    } else {
        def gpm = settings["z${zone}FlowRate"]?.toBigDecimal() ?: 2.5
        def gallonsUsed = (totalSecs / 60.0) * gpm
        def gallonsStr = "${gallonsUsed.setScale(1, BigDecimal.ROUND_HALF_UP)} gal"
        
        def costFactor = settings["z${zone}CostPerGallon"]?.toBigDecimal() ?: 0.03
        def totalCost = gallonsUsed * costFactor
        def costStr = "\$" + totalCost.setScale(2, BigDecimal.ROUND_HALF_UP)
        
        def entry = [time: new Date(startTime).format("MM/dd hh:mm a", location.timeZone), duration: durationStr, gallons: gallonsStr, cost: costStr]
        def logs = state["z${zone}SessionLog"] ?: []
        logs.add(0, entry)
        state["z${zone}SessionLog"] = logs.take(10)

        logAction("${sName}: Shower Finished. Duration: ${durationStr} | ${gallonsStr} | ${costStr}")
        state["z${zone}ShowerEndTime"] = now()
    }
    
    state["z${zone}ShowerActive"] = false
    state["z${zone}ShowerStatus"] = "Idle"
    
    state["z${zone}Warn1Time"] = 0
    state["z${zone}Warn2Time"] = 0
    state["z${zone}Warn3Time"] = 0

    state["z${zone}PostShowerPhase"] = true
    def light = settings["z${zone}Light"]
    if (light && light.hasCommand("refresh")) light.refresh()
    
    state["z${zone}LightOffTime"] = now() + 300000
    runIn(300, "checkPostShowerLightGeneric", [data: [zone: zone], overwrite: false])
}

def checkPostShowerLightGeneric(data) {
    def zone = data.zone
    if (state["z${zone}PostShowerPhase"]) {
        if (state["z${zone}LightOffTime"] && now() >= (state["z${zone}LightOffTime"] - 60000)) {
            verifyAndTurnOffLight(zone)
        }
    }
}

def verifyAndTurnOffLight(zone) {
    if (!state["z${zone}PostShowerPhase"]) return 
    
    def pMotion = settings["z${zone}ShowerMotion"]
    def sMotion = settings["z${zone}OutMotion"]
    
    def pList = pMotion instanceof List ? pMotion : (pMotion ? [pMotion] : [])
    def sList = sMotion instanceof List ? sMotion : (sMotion ? [sMotion] : [])
    
    def pActive = pList.any { it.currentValue("motion") == "active" }
    def sActive = sList.any { it.currentValue("motion") == "active" }
    
    if (!pActive && !sActive) {
        def light = settings["z${zone}Light"]
        if (light) {
            light.off()
            if (light.hasCommand("refresh")) {
                 runInMillis(2000, "refreshLight", [data: [zone: zone], overwrite: false]) 
            }
            logAction("${settings["z${zone}Name"]}: No motion for 5 mins post-shower. Light OFF & Refreshed.")
        }
        state["z${zone}PostShowerPhase"] = false 
    }
}

def refreshLight(data) {
    settings["z${data.zone}Light"]?.refresh()
}

def triggerFlash(zone, flashes) {
    if (!state["z${zone}ShowerActive"] || (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off")) return
    
    def guest = settings["z${zone}GuestSwitch"]
    if (guest && guest.currentValue("switch") == "on") {
        logAction("${settings["z${zone}Name"]}: Guest mode active. Skipping shower warning flashes.")
        return
    }
    
    def light = settings["z${zone}Light"]
    if (!light) return

    logAction("${settings["z${zone}Name"]}: Shower warning. Flashing ${flashes}x.")
    
    def initialState = light.currentValue("switch") ?: "on"
    def cycleTime = 2000 

    for (int i = 0; i < flashes; i++) {
        runInMillis((i * cycleTime) + 500, "turnLightOff", [data: [zone: zone], overwrite: false])
        runInMillis((i * cycleTime) + 1500, "turnLightOn", [data: [zone: zone], overwrite: false])
    }
    
    runInMillis((flashes * cycleTime) + 1000, "restoreLightState", [data: [zone: zone, targetState: initialState], overwrite: false])
}

def turnLightOff(data) { settings["z${data.zone}Light"]?.off() }
def turnLightOn(data) { settings["z${data.zone}Light"]?.on() }
def restoreLightState(data) {
    def light = settings["z${data.zone}Light"]
    if (data.targetState == "off") light?.off() else light?.on()
}

def evaluateZones() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return

    def globalAvg = getHouseAverageHum()
    def avgTrigger = isWholeHouseAveragingTriggered(globalAvg)
    state.averagingActive = avgTrigger
    def winterActive = isWinterShieldActive()
    def anyTankFull = false
    
    def outTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null

    for (int i = 1; i <= 15; i++) {
        if (!settings["z${i}Name"]) continue
        def roomName = settings["z${i}Name"]
        
        def h = settings["z${i}Hum"]?.currentValue("humidity")
        def t = settings["z${i}Temp"]?.currentValue("temperature")
        
        def dehum = settings["z${i}Dehum"]
        def isHardwareOn = dehum ? dehum.any { it.currentValue("switch") == "on" } : false
        
        if (!state["z${i}LastMathCalc"]) state["z${i}LastMathCalc"] = now()
        def durationMs = now() - state["z${i}LastMathCalc"]
        if (durationMs > 1000 && isHardwareOn && !state["z${i}TankFull"]) {
            state["z${i}DailyRunMs"] = (state["z${i}DailyRunMs"] ?: 0) + durationMs
            
            if (settings["z${i}EnableFilter"]) {
                state["z${i}FilterRunMs"] = (state["z${i}FilterRunMs"] ?: 0) + durationMs
            }
        }
        state["z${i}LastMathCalc"] = now()

        def interlocksEnabled = settings["z${i}EnableInterlocks"]
        
        def leakSensors = interlocksEnabled ? settings["z${i}Leak"] : null
        def isLeaking = leakSensors ? leakSensors.any { it.currentValue("water") == "wet" } : false
        
        def windows = interlocksEnabled ? settings["z${i}Window"] : null
        def windowOpen = windows ? windows.any { it.currentValue("contact") == "open" } : false
        
        def tvs = interlocksEnabled ? settings["z${i}TV"] : null
        def isTvOn = tvs ? tvs.any { it.currentValue("switch") == "on" } : false
        
        def isCooling = settings["z${i}Thermostat"]?.currentValue("thermostatOperatingState")?.toLowerCase()?.contains("cool") ?: false
        
        def allowedModes = settings["z${i}Modes"]
        def modeRestricted = allowedModes ? !allowedModes.contains(location.mode) : false
        def isLightMode = settings["z${i}LightMode"] ?: false

        def tvOffset = isTvOn ? (settings["z${i}TVOffset"] ?: 5) : 0
        def targetPoint = (settings["z${i}Target"] ?: 55) + tvOffset
        def triggerPoint = (settings["z${i}Trigger"] ?: 60) + tvOffset
        
        def activeTarget = targetPoint
        if (winterActive) {
            activeTarget = calculateDynamicWinterTarget(outTemp)
        }

        if (h != null && h > activeTarget) {
            state["z${i}BelowTargetStart"] = null
        }

        if (h != null) {
            if (h > 60) {
                state["z${i}HighHumMinutes"] = (state["z${i}HighHumMinutes"] ?: 0) + 5
            } else if (h < 55) {
                def tempVal = (state["z${i}HighHumMinutes"] ?: 0) - 10
                state["z${i}HighHumMinutes"] = tempVal > 0 ? tempVal : 0
            }
            
            if (enableZoneAlerts && settings["z${i}EnableAlert"]) {
                def zThresh = settings["z${i}AlertThresh"] ?: 60
                def zReqHours = settings["z${i}AlertHours"] ?: 24
                
                if (h >= zThresh) {
                    if (!state["z${i}HumAlertStart"]) state["z${i}HumAlertStart"] = now()
                    else if (now() - state["z${i}HumAlertStart"] >= (zReqHours * 3600000) && !state["z${i}HumAlertNotified"]) {
                        sendNotification("MOLD WARNING: ${roomName} humidity has been at or above ${zThresh}% for over ${zReqHours} hours. High risk of mold growth.", "zoneAlert")
                        state["z${i}HumAlertNotified"] = true
                    }
                } else {
                    state["z${i}HumAlertStart"] = null
                    state["z${i}HumAlertNotified"] = false
                }
            }
        }

        if (settings["z${i}EnableFilter"]) {
            def filterLifeHrs = settings["z${i}FilterLife"] ?: 720
            if (filterLifeHrs <= 0) filterLifeHrs = 720
            
            def filterRunHrs = (state["z${i}FilterRunMs"] ?: 0) / 3600000.0
            def filterHealth = 100.0 - ((filterRunHrs / filterLifeHrs) * 100.0)
            if (filterHealth < 0) filterHealth = 0
            
            def alertThresh = settings["z${i}FilterAlertThresh"] ?: 10

            if (filterHealth <= alertThresh && !state["z${i}FilterNotified"]) {
                sendNotification("🌬️ FILTER ALERT: ${roomName} dehumidifier filter health is at ${Math.round(filterHealth)}% (Threshold: ${alertThresh}%). Please clean or replace.", "filter")
                state["z${i}FilterNotified"] = true
            } else if (filterHealth > alertThresh) {
                state["z${i}FilterNotified"] = false
            }
        }

        if (!isLightMode) {
            def method = settings["z${i}TankMethod"]
            if (method == "Power Meter" && isHardwareOn) {
                def powerMeters = settings["z${i}Power"]
                def watts = powerMeters ? powerMeters.sum { it.currentValue("power") ?: 0 } : 0
                def threshold = settings["z${i}ActiveWatts"] ?: 100
                
                if (watts < threshold) {
                    if (!state["z${i}LowPowerStart"]) state["z${i}LowPowerStart"] = now()
                    else if ((now() - state["z${i}LowPowerStart"]) > 300000) { 
                        state["z${i}TankFull"] = true
                    }
                } else {
                    state["z${i}LowPowerStart"] = null
                    if (state["z${i}TankFull"]) resetTankFullFlag(i, "Power Meter Spike (Compressor On)")
                }
            } 
            else if (method == "Humidity Stall") {
                if (isHardwareOn && !state["z${i}TankFull"]) {
                    if (!state["z${i}StallStart"]) {
                        state["z${i}StallStart"] = now()
                        state["z${i}StallBaseRH"] = h
                    } else if (!isCooling) {
                        def baseRH = state["z${i}StallBaseRH"]
                        def reqDrop = settings["z${i}StallDrop"] ?: 5
                        def limitMins = settings["z${i}StallMins"] ?: 90
                
                        if (baseRH != null && (baseRH - h) >= reqDrop) {
                            state["z${i}StallStart"] = now()
                            state["z${i}StallBaseRH"] = h 
                        } else if ((now() - state["z${i}StallStart"]) > (limitMins * 60000)) {
                            state["z${i}TankFull"] = true
                            state["z${i}TankMaxRH"] = h 
                        }
                    }
                } else if (!isHardwareOn) {
                    state["z${i}StallStart"] = null
                }
                
                if (state["z${i}TankFull"]) {
                    def maxRH = state["z${i}TankMaxRH"] ?: h
                    if (h > maxRH) state["z${i}TankMaxRH"] = h 
                    else if (h <= (maxRH - 3) && !isCooling) {
                        resetTankFullFlag(i, "Humidity Drop (AC Off) - Auto Clear")
                    }
                }
            }
            
            if (state["z${i}TankFull"]) {
                anyTankFull = true
                if (!state["z${i}TankNotified"]) {
                    sendNotification("🚰 TANK FULL: ${roomName} requires emptying.", "tank")
                    state["z${i}TankNotified"] = true
                }
            }
        } else {
            state["z${i}TankFull"] = false
            state["z${i}TankNotified"] = false
        }

        def shouldRun = false
        def reason = "All targets met."
        def showerActive = state["z${i}ShowerActiveUntil"] && now() < state["z${i}ShowerActiveUntil"]
        
        if (settings["z${i}EnableShower"] && !showerActive) {
            def showerHumTrigger = settings["z${i}ShowerHumTrigger"] ?: 70
            if (h != null && h >= showerHumTrigger) {
                logAction("${roomName}: Humidity spike detected (${h}% >= ${showerHumTrigger}%). Engaging Shower Mode.")
                startShowerSession(i)
                showerActive = true 
            }
        }
        
        def acSynergyPause = false
        if (isCooling && settings["z${i}AcSynergy"] && !showerActive && !isLeaking) {
            acSynergyPause = true
        }
        state["z${i}AcSynergyPause"] = acSynergyPause
        
        if (isLeaking) {
            shouldRun = true
            reason = "EMERGENCY: Water Leak Detected."
            if (!state["z${i}LeakNotified"]) {
                sendNotification("CRITICAL ALERT: Water leak detected in ${roomName}. Dehumidifier forced ON.", "leak")
                state["z${i}LeakNotified"] = true
            }
        } else {
            state["z${i}LeakNotified"] = false
            
            if (windowOpen) {
                shouldRun = false
                reason = "Window/Door Open."
            } else if (state["z${i}TankFull"]) {
                shouldRun = true 
                reason = "TANK FULL (Idling pending empty)."
            } else if (modeRestricted) {
                shouldRun = false
                reason = "Location Mode Restricted."
            } else if (acSynergyPause) {
                shouldRun = false
                reason = "AC Synergy (Latent Heat Intercept: Paused while AC cools house)."
            } else {
                
                if (showerActive && h != null) {
                    def sweepTarget = activeTarget > (globalAvg + 5.0) ? activeTarget : (globalAvg + 5.0)
                    
                    if (h > sweepTarget) {
                        state["z${i}ShowerPeakReached"] = true
                    }
                    
                    if (h <= sweepTarget && state["z${i}ShowerPeakReached"]) {
                        logAction("${roomName} humidity (${h}%) has normalized with house average (${sweepTarget}%). Smart Sweep complete.")
                        state["z${i}ShowerActiveUntil"] = null
                        showerActive = false
                        
                        if (state["z${i}ShowerActive"]) {
                            terminateShower(i, true)
                        }
                    }
                }

                if (!shouldRun && showerActive) {
                    shouldRun = true
                    def diffMins = Math.round((state["z${i}ShowerActiveUntil"] - now()) / 60000)
                    def leftMins = diffMins > 0 ? diffMins : 0
                    reason = "Smart Shower Sweep (Extracting vapor)."
                }
                else if (!shouldRun && winterActive) {
                    def wPoint = activeTarget
                    def wDB = winterShieldDB ?: 3
                    if (h >= wPoint) { shouldRun = true; reason = "Winter Window Shield Active (${wPoint}% goal)." }
                    else if (isHardwareOn && h > (wPoint - wDB)) { shouldRun = true; reason = "Maintaining Winter Shield Deadband." }
                }
                else if (!shouldRun && avgTrigger) {
                    shouldRun = true
                    reason = "Averaging System Triggered (House Avg: ${globalAvg}%)."
                }
                else if (!shouldRun) {
                    if (h >= triggerPoint) { 
                        shouldRun = true 
                        reason = "Humidity (${h}%) hit trigger (${triggerPoint}%)." + (isTvOn ? " (TV Active)" : "") 
                    }
                    else if (isHardwareOn && h > targetPoint) { 
                        shouldRun = true 
                        reason = "Running until target (${targetPoint}%) is reached."
                    }
                }
            }
        }
        
        if (!shouldRun && isHardwareOn && !isLeaking && !windowOpen && !modeRestricted && !state["z${i}TankFull"] && !acSynergyPause) {
            if (h != null && h <= activeTarget) {
                def offDelay = settings["z${i}OffDelay"] ?: 0
                if (offDelay > 0) {
                    if (!state["z${i}BelowTargetStart"]) state["z${i}BelowTargetStart"] = now()
                    def elapsed = now() - state["z${i}BelowTargetStart"]
                    if (elapsed < (offDelay * 60000)) {
                        shouldRun = true
                        reason = "Target reached. Extended run."
                    }
                }
            }
        }

        if (!shouldRun && isHardwareOn && settings["z${i}CompProtect"] && !isLeaking && !windowOpen && !acSynergyPause) {
            def minRunMs = (settings["z${i}MinRun"] ?: 15) * 60000
            def runTimeMs = state["z${i}CycleStart"] ? (now() - state["z${i}CycleStart"]) : 0
            if (runTimeMs < minRunMs) {
                shouldRun = true
                reason = "Compressor Protection Active"
            }
        }

        if (shouldRun) {
            if (state["z${i}TankFull"]) {
                if (!isHardwareOn) dehum?.each { it.on() }
                state["z${i}LogicNote"] = "Tank Full (Waiting)"
            } else {
                if (!isHardwareOn) dehum?.each { it.on() }
                state["z${i}LogicNote"] = "Active: ${reason}"
            }
            if (!state["z${i}CycleStart"]) state["z${i}CycleStart"] = now()
        } else {
            if (isHardwareOn) {
                dehum?.each { it.off() }
                state["z${i}CycleStart"] = null 
                state["z${i}LogicNote"] = acSynergyPause ? "AC Synergy (Latent Heat Intercept)" : "Idle"
            } else {
                state["z${i}LogicNote"] = acSynergyPause ? "AC Synergy (Latent Heat Intercept)" : "Idle"
                state["z${i}CycleStart"] = null 
            }
        }
    }
    
    if (globalTankSwitch) {
        if (anyTankFull && globalTankSwitch.currentValue("switch") == "off") globalTankSwitch.on()
        else if (!anyTankFull && globalTankSwitch.currentValue("switch") == "on") globalTankSwitch.off()
    }
}

def calculateZoneState(zone) {
    def currHum = settings["z${zone}Hum"]?.currentValue("humidity") ?: 0
    def currTemp = settings["z${zone}Temp"]?.currentValue("temperature")
    
    def dehum = settings["z${zone}Dehum"]
    def isHardwareOn = dehum ? dehum.any { it.currentValue("switch") == "on" } : false
    
    def interlocksEnabled = settings["z${zone}EnableInterlocks"]
    def leakSensors = interlocksEnabled ? settings["z${zone}Leak"] : null
    def isLeaking = leakSensors ? leakSensors.any { it.currentValue("water") == "wet" } : false
    
    def windows = interlocksEnabled ? settings["z${zone}Window"] : null
    def windowOpen = windows ? windows.any { it.currentValue("contact") == "open" } : false
    
    def tvs = interlocksEnabled ? settings["z${zone}TV"] : null
    def isTvOn = tvs ? tvs.any { it.currentValue("switch") == "on" } : false
    
    def inDP = calculateDewPoint(currTemp, currHum)
    def tvOffset = isTvOn ? (settings["z${zone}TVOffset"] ?: 5) : 0
    
    def target = (settings["z${zone}Target"] ?: 55) + tvOffset
    def trigger = (settings["z${zone}Trigger"] ?: 60) + tvOffset
    
    if (isWinterShieldActive()) {
        def outT = outdoorTempSensor?.currentValue("temperature")
        target = calculateDynamicWinterTarget(outT) - (winterShieldDB ?: 3)
        trigger = calculateDynamicWinterTarget(outT)
    }
    
    long tMs = state["z${zone}DailyRunMs"] ?: 0
    def tHrs = (tMs / 3600000).toInteger()
    def tMins = ((tMs % 3600000) / 60000).toInteger()
    def durStr = "${tHrs}h ${tMins}m"
    
    def filterEnabled = settings["z${zone}EnableFilter"] ?: false
    def filterHealth = 100
    def filterThresh = settings["z${zone}FilterAlertThresh"] ?: 10
    if (filterEnabled) {
        def filterLifeHrs = settings["z${zone}FilterLife"] ?: 720
        if (filterLifeHrs <= 0) filterLifeHrs = 720
        def filterRunHrs = (state["z${zone}FilterRunMs"] ?: 0) / 3600000.0
        filterHealth = Math.round(100.0 - ((filterRunHrs / filterLifeHrs) * 100.0))
        if (filterHealth < 0) filterHealth = 0
    }
    
    def countdownStr = ""
    if (isHardwareOn) {
        if (state["z${zone}ShowerActiveUntil"] && now() < state["z${zone}ShowerActiveUntil"]) {
            def calcLeft = Math.round((state["z${zone}ShowerActiveUntil"] - now()) / 60000)
            def left = calcLeft > 0 ? calcLeft : 0
            countdownStr = "<span style='color:#e67e22; font-weight:bold;'>${left}m (Max Failsafe)</span>"
        } else if (state["z${zone}BelowTargetStart"] && settings["z${zone}OffDelay"]) {
            def offDelayMs = (settings["z${zone}OffDelay"] ?: 0) * 60000
            def runTimeMs = now() - state["z${zone}BelowTargetStart"]
            if (runTimeMs < offDelayMs) {
                def calcLeft = Math.round((offDelayMs - runTimeMs) / 60000)
                def left = calcLeft > 0 ? calcLeft : 0
                countdownStr = "<span style='color:#3498db; font-weight:bold;'>${left}m (Extended)</span>"
            }
        } else if (state["z${zone}CycleStart"] && settings["z${zone}CompProtect"]) {
            def minRunMs = (settings["z${zone}MinRun"] ?: 15) * 60000
            def runTimeMs = now() - state["z${zone}CycleStart"]
            if (runTimeMs < minRunMs) {
                def calcLeft = Math.round((minRunMs - runTimeMs) / 60000)
                def left = calcLeft > 0 ? calcLeft : 0
                countdownStr = "<span style='color:#e67e22; font-weight:bold;'>${left}m (Min-Run)</span>"
            }
        }
    }
    
    def allowedModes = settings["z${zone}Modes"]
    def modeRestricted = allowedModes ? !allowedModes.contains(location.mode) : false
    def isLightMode = settings["z${zone}LightMode"] ?: false
    def statusNote = state["z${zone}LogicNote"] ?: "Standby"
    if (!isHardwareOn && modeRestricted) statusNote = "Mode Restricted"
    if (isLightMode) statusNote = "[Light Mode] " + statusNote

    def hrsAbove = (state["z${zone}HighHumMinutes"] ?: 0) / 60.0
    def riskLevel = "Optimal (No Risk)"
    def riskColor = "#27ae60"
    
    if (hrsAbove > 72) { riskLevel = "Critical (Mold Likely)"; riskColor = "#c0392b" }
    else if (hrsAbove > 48) { riskLevel = "High Risk"; riskColor = "#e67e22" }
    else if (hrsAbove > 24) { riskLevel = "Elevated (Monitor)"; riskColor = "#f39c12" }

    return [
        currHum: currHum,
        currTemp: currTemp,
        dewPoint: inDP,
        spread: (currTemp != null && inDP != null) ? (Math.round((currTemp - inDP) * 10.0) / 10.0) : null,
        target: target,
        trigger: trigger,
        duration: durStr, 
        status: statusNote, 
        countdown: countdownStr,
        showerTracker: state["z${zone}ShowerStatus"] ?: "Idle",
        hardwareOn: isHardwareOn,
        tankFull: state["z${zone}TankFull"] ?: false,
        filterEnabled: filterEnabled,
        filterHealth: filterHealth,
        filterThresh: filterThresh,
        windowOpen: windowOpen,
        isLeaking: isLeaking,
        riskLevel: riskLevel,
        riskColor: riskColor,
        riskHours: String.format("%.1f", hrsAbove),
        acSynergyActive: state["z${zone}AcSynergyPause"] ?: false
    ]
}

def sendNotification(msg, type = "general") {
    def shouldSend = true
    if (type != "leak" && settings.notifyStartTime && settings.notifyEndTime) {
        shouldSend = timeOfDayIsBetween(settings.notifyStartTime, settings.notifyEndTime, new Date(), location.timeZone)
    }

    if (shouldSend) {
        def devices = []
        if (type == "leak") devices = settings.leakNotifyDevices
        else if (type == "tank") devices = settings.tankNotifyDevices
        else if (type == "filter") devices = settings.filterNotifyDevices
        else if (type == "zoneAlert") devices = settings.zoneAlertDevices
        
        if (devices) devices.each { it.deviceNotification(msg) }
    }
    logAction(msg)
}

def logAction(msg) {
    if (txtEnable) log.info "${app.label}: ${msg}"
    def hist = state.eventLog ?: []
    hist.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    state.eventLog = hist.take(25)
}

def installed() { initialize() }
def updated() { initialize() }

def hubRebootHandler(evt) {
    logAction("SYSTEM ALERT: Hub reboot detected. Re-initializing.")
    initialize()
    evaluateZones()
}

def handleMasterSwitch(evt) {
    if (evt.value == "off") {
        logAction("Master Enable Switch turned OFF. App suspended. Halting active dehumidifiers...")
        for (int i = 1; i <= 15; i++) {
            if (settings["z${i}Name"]) {
                settings["z${i}Dehum"]?.each { it.off() }
                state["z${i}LogicNote"] = "Master Disabled"
                state["z${i}CycleStart"] = null
            }
        }
    } else {
        logAction("Master Enable Switch turned ON. Resuming operations.")
        evaluateZones()
    }
}

def initialize() {
    unschedule()
    subscribe(location, "systemStart", "hubRebootHandler")
    subscribe(location, "mode", "handleEnvChange")
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", "handleMasterSwitch")
    
    for (int i = 1; i <= 15; i++) { 
        if (settings["z${i}Name"]) {
            if (settings["z${i}Hum"]) subscribe(settings["z${i}Hum"], "humidity", "handleEnvChange") 
            if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", "handleEnvChange")
            
            if (settings["z${i}EnableInterlocks"]) {
                if (settings["z${i}Leak"]) subscribe(settings["z${i}Leak"], "water", "handleEnvChange")
                if (settings["z${i}TV"]) subscribe(settings["z${i}TV"], "switch", "handleEnvChange")
                if (settings["z${i}Window"]) subscribe(settings["z${i}Window"], "contact", "handleEnvChange")
                if (settings["z${i}AcSynergy"] && settings["z${i}Thermostat"]) subscribe(settings["z${i}Thermostat"], "thermostatOperatingState", "handleEnvChange")
            }
            
            if (settings["z${i}TankMethod"] == "Humidity Stall" && !settings["z${i}AcSynergy"] && settings["z${i}Thermostat"]) {
                subscribe(settings["z${i}Thermostat"], "thermostatOperatingState", "handleEnvChange")
            }
            if (settings["z${i}Power"]) subscribe(settings["z${i}Power"], "power", "handleEnvChange")
            
            if (settings["z${i}EnableShower"]) {
                if (settings["z${i}ShowerMotion"]) subscribe(settings["z${i}ShowerMotion"], "motion", "showerMotionHandler")
                if (settings["z${i}OutMotion"]) subscribe(settings["z${i}OutMotion"], "motion", "outMotionHandler")
                
                state["z${i}ShowerStatus"] = state["z${i}ShowerStatus"] ?: "Idle"
                state["z${i}ShowerActive"] = state["z${i}ShowerActive"] ?: false
                state["z${i}SessionLog"] = state["z${i}SessionLog"] ?: []
                state["z${i}PostShowerPhase"] = false
            }
        }
    }
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", "handleEnvChange")
    if (outdoorHumSensor) subscribe(outdoorHumSensor, "humidity", "handleEnvChange")
    
    schedule("0 0 0 * * ?", "midnightHandler")
    runEvery5Minutes("evaluateZones")
    
    scheduleRandomReminders()
}

def handleEnvChange(evt) {
    runInMillis(1000, "evaluateZones", [overwrite: true])
}

def scheduleRandomReminders() {
    def rand = new java.util.Random()
    
    if (settings.randomTankSwitch) {
        def h = rand.nextInt(11) + 8
        def m = rand.nextInt(60)
        schedule("0 ${m} ${h} * * ?", "fireRandomTankReminder")
        logAction("SYSTEM: Scheduled daily random Tank reminder for ${h}:${m.toString().padLeft(2, '0')}")
    }
    
    if (settings.randomFilterSwitch) {
        def h2 = rand.nextInt(11) + 8
        def m2 = rand.nextInt(60)
        schedule("0 ${m2} ${h2} * * ?", "fireRandomFilterReminder")
        logAction("SYSTEM: Scheduled daily random Filter reminder for ${h2}:${m2.toString().padLeft(2, '0')}")
    }
}

def fireRandomTankReminder() {
    def anyTankFull = false
    def fullNames = []
    
    for (int i = 1; i <= 15; i++) {
        if (state["z${i}TankFull"]) {
            anyTankFull = true
            fullNames << (settings["z${i}Name"] ?: "Room ${i}")
        }
    }
    
    if (anyTankFull && settings.randomTankSwitch) {
        settings.randomTankSwitch.on()
        sendNotification("Daily Reminder: Water tanks in ${fullNames.join(', ')} require emptying.", "tank")
        runIn(600, "turnOffRandomTankSwitch", [overwrite: true])
        logAction("Random daily Tank Full reminder activated for 10 minutes, and push notification sent.")
    }
}

def turnOffRandomTankSwitch() {
    settings.randomTankSwitch?.off()
}

def fireRandomFilterReminder() {
    def anyDirty = false
    def dirtyNames = []
    
    for (int i = 1; i <= 15; i++) {
        if (settings["z${i}EnableFilter"]) {
            def filterLifeHrs = settings["z${i}FilterLife"] ?: 720
            if (filterLifeHrs <= 0) filterLifeHrs = 720
            
            def filterRunHrs = (state["z${i}FilterRunMs"] ?: 0) / 3600000.0
            def filterHealth = 100.0 - ((filterRunHrs / filterLifeHrs) * 100.0)
            def thresh = settings["z${i}FilterAlertThresh"] ?: 10
            
            if (filterHealth <= thresh) {
                anyDirty = true
                dirtyNames << (settings["z${i}Name"] ?: "Room ${i}")
            }
        }
    }
    
    if (anyDirty && settings.randomFilterSwitch) {
        settings.randomFilterSwitch.on()
        sendNotification("Daily Reminder: Filters in ${dirtyNames.join(', ')} require cleaning/replacement.", "filter")
        runIn(600, "turnOffRandomFilterSwitch", [overwrite: true])
        logAction("Random daily Dirty Filter reminder activated for 10 minutes, and push notification sent.")
    }
}

def turnOffRandomFilterSwitch() {
    settings.randomFilterSwitch?.off()
}
