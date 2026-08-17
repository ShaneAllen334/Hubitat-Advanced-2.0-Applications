/**
 * Advanced Air Quality Manager 2.0
 */ 

definition(
    name: "Advanced Air Quality Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Health & Wellness",
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
            def purifierState = isWholeHouse && mainPurifier ? mainPurifier.currentValue("switch")?.toUpperCase() : "N/A"
            
            def outAQIStr = getAQI(outdoorAQI) != null ? getAQI(outdoorAQI) : "--"
            def inAQIStr = isWholeHouse && indoorAQI ? (getAQI(indoorAQI) != null ? getAQI(indoorAQI) : "--") : "N/A (Multi-Zone)"
            
            def isHighPollen = isHighPollenMonth()
            def pollenStr = enableSeasonalPollen ? (isHighPollen ? "<span style='color:orange;'>High (Active Season)</span>" : "<span style='color:green;'>Low (Off-Season)</span>") : "Disabled"
            def currentLocMode = location.mode ?: "Unknown"
            
            def hvacNames = "Not Monitored"
            if (hvacThermostats) {
                hvacNames = hvacThermostats.collect { "${it.displayName}: <b>${it.currentValue('thermostatOperatingState')?.toUpperCase()}</b>" }.join("<br>")
                if (state.hvacScrubEnd && now() < state.hvacScrubEnd) {
                    def remain = ((state.hvacScrubEnd - now()) / 60000).toInteger()
                    hvacNames += "<br><span style='color:purple;'><b>Post-Cycle Scrub</b> (${remain}m left)</span>"
                }
            }

            def effectivenessStr = "Requires Indoor and Outdoor Sensors"
            def outVal = getAQI(outdoorAQI)
       
            if (isWholeHouse && indoorAQI && outdoorAQI) {
                def inVal = getAQI(indoorAQI)
                if (inVal != null && outVal != null) {
                    if (inVal < outVal) effectivenessStr = "<span style='color:green;'><b>Working Effectively</b> (Indoor is ${outVal - inVal} points cleaner)</span>"
                    else if (inVal == outVal) effectivenessStr = "<span style='color:orange;'><b>Neutral</b> (Indoor equals Outdoor)</span>"
                    else effectivenessStr = "<span style='color:red;'><b>Poor</b> (Indoor is ${inVal - outVal} points worse)</span>"
                }
            }
            
            def mTriggerNames = "None Setup"
            if (isWholeHouse && mainTriggerSwitch) {
                mTriggerNames = mainTriggerSwitch.collect { "${it.displayName}: <b>${it.currentValue('switch')?.toUpperCase()}</b>" }.join("<br>")
                if (state.mainScrubEnd && now() < state.mainScrubEnd) mTriggerNames += "<br><span style='color:blue;'><i>(Post-Scrubbing Active)</i></span>"
            }
            
            def mainVacCounterStr = "N/A"
            if (isWholeHouse && mainVacuumSensor) {
                def vId = mainVacuumSensor.id
                def vEvents = state.vacuumEvents?.get(vId) ?: []
                def mWindow = settings.mainVacuumWindow != null ? settings.mainVacuumWindow : 60
                def validEvents = vEvents.findAll { it >= (now() - (mWindow * 60000)) }
                
                if (state.vacuumScrubEnds?.get(vId) && now() < state.vacuumScrubEnds[vId]) {
                    def remain = ((state.vacuumScrubEnds[vId] - now()) / 60000).toInteger()
                    mainVacCounterStr = "<span style='color:purple;'><b>Active Vacuum Scrubbing</b> (${remain}m left)</span>"
                } else {
                    mainVacCounterStr = "${validEvents.size()} / 3 bumps (last ${mWindow}m)"
                }
            }
            
            def mPreventName = "None Setup"
            if (isWholeHouse && mainPreventSwitch) {
                mPreventName = "${mainPreventSwitch.displayName}: <b>${mainPreventSwitch.currentValue('switch')?.toUpperCase()}</b>"
            }
            if (isWholeHouse && mainContactSensor) {
                def openSensors = mainContactSensor.findAll { it.currentValue("contact") == "open" }
                if (openSensors) {
                    mPreventName += (mPreventName == "None Setup" ? "" : "<br>") + "<span style='color:red;'><b>Window/Door Open!</b></span>"
                }
            }

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
                    <tr><td class="dash-hl">Main Purifier State</td><td colspan="3" class="dash-val" style="color:${purifierState == 'ON' ? 'green' : 'black'};"><b>${purifierState}</b></td></tr>
                    <tr><td class="dash-hl">Current Reason</td><td colspan="3" class="dash-val"><i>${state.currentReason ?: "System Idle"}</i></td></tr>
                    
                    <tr><td colspan="4" class="dash-subhead">Air Quality Data</td></tr>
                    <tr><td class="dash-hl">Outdoor AQI</td><td colspan="3" class="dash-val">${outAQIStr}</td></tr>
                    <tr><td class="dash-hl">Indoor AQI</td><td colspan="3" class="dash-val">${inAQIStr}</td></tr>
                    <tr><td class="dash-hl">System Effectiveness</td><td colspan="3" class="dash-val">${effectivenessStr}</td></tr>
                    
                    ${isWholeHouse ? """
                    <tr><td colspan="4" class="dash-subhead">Main Logic Switches</td></tr>
                    <tr><td class="dash-hl">Trigger Switches</td><td colspan="3" class="dash-val">${mTriggerNames}</td></tr>
                    <tr><td class="dash-hl">Vacuum Status</td><td colspan="3" class="dash-val">${mainVacCounterStr}</td></tr>
                    <tr><td class="dash-hl">Prevent / Contact Switch</td><td colspan="3" class="dash-val">${mPreventName}</td></tr>
                    """ : ""}
                    
                    <tr><td colspan="4" class="dash-subhead">External Variables</td></tr>
                    <tr><td class="dash-hl">HVAC Status</td><td colspan="3" class="dash-val">${hvacNames}</td></tr>
                    <tr><td class="dash-hl">Seasonal Pollen Status</td><td colspan="3" class="dash-val">${pollenStr}</td></tr>
                    <tr><td class="dash-hl">Location Mode</td><td colspan="3" class="dash-val">${currentLocMode}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>Performance & Filter Tracking</b>", hideable: true) {
            def perfHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Purifier Zone</th><th>Filter Life</th><th>ACH</th><th>Run Time (Today)</th></tr></thead><tbody>"
            def today = new Date().format("yyyy-MM-dd", location.timeZone)

            if (isWholeHouse && mainPurifier) {
                def cadr = mainPurifierCADR ?: 450.0
                def sqft = houseSqFt ?: 500.0
                def ceiling = houseCeiling ?: 8.0
                def vol = sqft * ceiling
                def ach = vol > 0 ? String.format("%.1f", (cadr * 60.0) / vol) : "0.0"
                
                def histMins = state.runHistory?.get(today)?.get(mainPurifier.id) ?: 0.0
                def totalMins = histMins + liveRunMins(mainPurifier.id)
                
                def fMins = (state.filterWearMins?.get(mainPurifier.id) ?: 0.0) + totalMins
                def fMax = (mainFilterHours ?: 4380) * 60.0
                def fPct = Math.max(0.0, 100.0 - ((fMins / fMax) * 100.0))
                def fStr = fPct < 10 ? "<span style='color:red;'><b>${String.format("%.1f", fPct)}%</b></span>" : "${String.format("%.1f", fPct)}%"
                
                perfHTML += "<tr><td><b>Main House</b></td><td>${fStr}</td><td>${ach}</td><td>${String.format("%.1f", totalMins / 60.0)}h</td></tr>"
            } else if (!isWholeHouse) {
                def hasActiveZones = false
                def numZ = getNumZones()
                for (int i = 1; i <= numZ; i++) {
                    def purif = settings["z${i}Purifier"]
                    if (settings["enableZ${i}"] && purif) {
                        hasActiveZones = true
                        def cadr = settings["z${i}CADR"] ?: 100.0
                        def sqft = settings["z${i}SqFt"] ?: 150.0
                        def ceiling = settings["z${i}Ceiling"] ?: 8.0
                        def vol = sqft * ceiling
                        def ach = vol > 0 ? String.format("%.1f", (cadr * 60.0) / vol) : "0.0"
                        
                        def histMins = state.runHistory?.get(today)?.get(purif.id) ?: 0.0
                        def totalMins = histMins + liveRunMins(purif.id)
                        
                        def fMins = (state.filterWearMins?.get(purif.id) ?: 0.0) + totalMins
                        def fMax = (settings["z${i}FilterHours"] ?: 4380) * 60.0
                        def fPct = Math.max(0.0, 100.0 - ((fMins / fMax) * 100.0))
                        def fStr = fPct < 10 ? "<span style='color:red;'><b>${String.format("%.1f", fPct)}%</b></span>" : "${String.format("%.1f", fPct)}%"
                        
                        def zName = settings["z${i}Name"] ?: "Zone ${i}"
                        perfHTML += "<tr><td>${zName}</td><td>${fStr}</td><td>${ach}</td><td>${String.format("%.1f", totalMins / 60.0)}h</td></tr>"
                    }
                }
                if (!hasActiveZones) perfHTML += "<tr><td colspan='4'><i>No Zone Purifiers Configured</i></td></tr>"
            }
            
            perfHTML += "</tbody></table>"
            paragraph perfHTML
            input "resetMainFilter", "button", title: "Reset Main Filter to 100%"
            input "resetHistory", "button", title: "Clear Tracking History"
        }

        section("<b>7-Day Daily Breakdown</b>", hideable: true) {
            def statsHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Date</th><th>Total Run Time</th><th>Indoor Alerts</th><th>Outdoor Alerts</th></tr></thead><tbody>"
            
            def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
            def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
            sdf.setTimeZone(location.timeZone)
            
            for (int i = 0; i < 7; i++) {
                def dStr = sdf.format(new Date(now() - (i * 86400000L)))
                def dayMins = 0.0
                
                if (state.runHistory && state.runHistory[dStr]) {
                    state.runHistory[dStr].each { devId, mins ->
                        dayMins += (mins ?: 0.0)
                    }
                }
                
                if (dStr == todayStr) {
                    if (isWholeHouse && mainPurifier) {
                        dayMins += liveRunMins(mainPurifier.id)
                    } else if (!isWholeHouse) {
                        def numZ = getNumZones()
                        for (int z = 1; z <= numZ; z++) {
                            if (settings["enableZ${z}"] && settings["z${z}Purifier"]) {
                                dayMins += liveRunMins(settings["z${z}Purifier"].id)
                            }
                        }
                    }
                }
                
                def inAlerts = state.alertHistory?.get(dStr)?.indoor ?: 0
                def outAlerts = state.alertHistory?.get(dStr)?.outdoor ?: 0
                
                def displayDate = (dStr == todayStr) ? "<b>Today (${dStr})</b>" : dStr
                statsHTML += "<tr><td>${displayDate}</td><td>${String.format("%.1f", dayMins / 60.0)}h</td><td>${inAlerts}</td><td>${outAlerts}</td></tr>"
            }
            
            statsHTML += "</tbody></table>"
            paragraph statsHTML
        }

        if (!isWholeHouse) {
            section("<b>Zone Breakdown</b>", hideable: true) {
                def zoneHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Zone Name</th><th>AQI</th><th>Trigger Switches</th><th>Prevent/Open Window</th><th>Zone Purifier</th></tr></thead><tbody>"
                def hasZones = false
                def numZ = getNumZones()
                
                for (int i = 1; i <= numZ; i++) {
                    if (settings["enableZ${i}"] && settings["z${i}AQI"]) {
                        hasZones = true
                        def zName = settings["z${i}Name"] ?: "Zone ${i}"
                        def zAQI = getAQI(settings["z${i}AQI"]) ?: "--"
                        
                        def zTriggerNames = ""
                        if (settings["z${i}TriggerSwitch"]) {
                            zTriggerNames = settings["z${i}TriggerSwitch"].collect { "${it.displayName}: <b>${it.currentValue('switch')?.toUpperCase()}</b>" }.join("<br>")
                            def isScrubbing = state.scrubEndTimes?.get("z${i}") && now() < state.scrubEndTimes.get("z${i}")
                            if (isScrubbing) zTriggerNames += "<br><span style='color:blue;'><i>(Post-Scrubbing Active)</i></span>"
                        }
                        
                        def zVac = settings["z${i}VacuumSensor"]
                        if (zVac) {
                            def vId = zVac.id
                            def vEvents = state.vacuumEvents?.get(vId) ?: []
                            def zWindow = settings["z${i}VacuumWindow"] != null ? settings["z${i}VacuumWindow"] : 60
                            def validEvents = vEvents.findAll { it >= (now() - (zWindow * 60000)) }
                            
                            if (state.vacuumScrubEnds?.get(vId) && now() < state.vacuumScrubEnds[vId]) {
                                def remain = ((state.vacuumScrubEnds[vId] - now()) / 60000).toInteger()
                                zTriggerNames += (zTriggerNames ? "<br>" : "") + "<span style='color:purple;'><b>Vac Scrubbing</b> <i>(${remain}m left)</i></span>"
                            } else {
                                zTriggerNames += (zTriggerNames ? "<br>" : "") + "<span style='color:gray;'><i>Vac: ${validEvents.size()}/3 bumps (last ${zWindow}m)</i></span>"
                            }
                        }
                        if (!zTriggerNames) zTriggerNames = "None Setup"

                        def zPreventName = "None Setup"
                        if (settings["z${i}PreventSwitch"]) {
                            def pState = settings["z${i}PreventSwitch"].currentValue("switch")
                            zPreventName = "${settings["z${i}PreventSwitch"].displayName}: <b>${pState?.toUpperCase()}</b>"
                            
                            if (pState == "on") {
                                def overrideThresh = settings["z${i}OverrideLevel"] ?: 100
                                if (zAQI != "--" && zAQI.toInteger() >= overrideThresh) {
                                    zPreventName += "<br><span style='color:orange;'><i>(Bypassed: AQI > ${overrideThresh})</i></span>"
                                } else {
                                    zPreventName += "<br><span style='color:red;'><i>(Blocking Purifier)</i></span>"
                                }
                            }
                        }
                        if (settings["z${i}ContactSensor"]) {
                            def openSensors = settings["z${i}ContactSensor"].findAll { it.currentValue("contact") == "open" }
                            if (openSensors) {
                                zPreventName += (zPreventName == "None Setup" ? "" : "<br>") + "<span style='color:red;'><b>Window/Door Open!</b></span>"
                            }
                        }
                        
                        def zPurifierState = settings["z${i}Purifier"] ? settings["z${i}Purifier"].currentValue("switch")?.toUpperCase() : "No Device"
                        
                        zoneHTML += "<tr><td><b>${zName}</b></td><td>${zAQI}</td><td>${zTriggerNames}</td><td>${zPreventName}</td><td>${zPurifierState}</td></tr>"
                    }
                }
                zoneHTML += "</tbody></table>"
                if (hasZones) paragraph zoneHTML else paragraph "<i>No zones configured. Running on Main Sensor only.</i>"
            }
        }

        section("<b>Recent Action History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>1. App Control & Main Hardware</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Acts as the master configuration for your air quality logic. Set up your main sensors and base purifiers here.</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            if (!isWholeHouse) {
                input "numberOfZones", "enum", title: "Number of Zones to Configure", options: ["1","2","3","4","5","6","7","8","9","10","11","12"], defaultValue: "5", submitOnChange: true
            }

            paragraph "<b>HVAC System Integration</b>"
            input "hvacThermostats", "capability.thermostat", title: "Monitor HVAC Thermostats (Forces purifiers ON to catch dust when active)", multiple: true, required: false
            input "hvacOverrunTime", "number", title: "HVAC Overrun Scrub Time (Minutes to run after HVAC stops)", required: false, defaultValue: 15

            paragraph "<hr>"
            input "isWholeHouse", "bool", title: "<b>Use Single Full House Air Purifier?</b>", defaultValue: true, submitOnChange: true
            
            if (isWholeHouse) {
                input "indoorAQI", "capability.sensor", title: "Select Main Indoor AQI Sensor", required: false
                input "mainPurifier", "capability.switch", title: "Select Main House Air Purifier", required: false, multiple: false
                
                input "mainTriggerSwitch", "capability.switch", title: "Trigger Switches (e.g., Switch - Forces ON)", required: false, multiple: true
                input "mainScrubTime", "number", title: "Post-Cleaning Scrub Time (Minutes to run after standard trigger turns off)", required: false, defaultValue: 30
                
                paragraph "<b>Vacuum Integration & Smart Scrubbing</b>"
                input "mainVacuumSensor", "capability.accelerationSensor", title: "Vacuum Vibration Sensor (Detects 3 motions to trigger)", required: false
                input "mainVacuumWindow", "number", title: "Vacuum Motion Window (Minutes to detect 3 bumps)", required: false, defaultValue: 60
                input "mainVacuumTime", "number", title: "Vacuum Scrub Time (Minutes to run after detection regardless of AQI)", required: false, defaultValue: 15
                
                paragraph "<b>Overrides & Protection</b>"
                input "mainPreventSwitch", "capability.switch", title: "Prevent Switch (e.g., TV - Stops purifier)", required: false, multiple: false
                input "mainContactSensor", "capability.contactSensor", title: "Open Door/Window Sensors (Forces purifiers OFF to save energy)", required: false, multiple: true
                if (mainPreventSwitch) input "mainOverrideLevel", "number", title: "Emergency Override AQI (Overrides Prevent Switch)", required: false, defaultValue: 100
            
                paragraph "<b>Main Purifier Specifications</b>"
                input "mainPurifierCADR", "number", title: "Purifier CADR (CFM)", required: false, defaultValue: 450
                input "houseSqFt", "number", title: "Total Treated House Square Footage", required: false, defaultValue: 500
                input "houseCeiling", "decimal", title: "Ceiling Height (Feet)", required: false, defaultValue: 8.0
                input "mainFilterHours", "number", title: "Filter Lifespan (Hours)", required: false, defaultValue: 4380
            }
            
            paragraph "<b>Hardware Protection (Hysteresis)</b>"
            input "targetAQI", "number", title: "Target AQI (Turn ON if any room goes above this)", required: false, defaultValue: 50
            input "mainHysteresis", "number", title: "Hysteresis Buffer (Points below target to turn OFF, prevents short-cycling)", required: false, defaultValue: 10
        }

        section("<b>2. Operating Modes & Alerts</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Adapts purifier behavior based on location states, including suppressing noise during quiet times or running 24/7 during sickness.</div>"
            
            input "notifyDevice", "capability.notification", title: "Notification Device for Push Alerts", required: false, multiple: true
            
            input "notifyFilter", "bool", title: "Enable Low Filter Notifications", defaultValue: true, submitOnChange: true
            if (notifyFilter != false) {
                input "filterNotifyThreshold", "number", title: "Alert when Filter Life drops below (%)", defaultValue: 5, required: false
                input "randomFilterSwitch", "capability.switch", title: "🎲 Random Daily Reminder Switch (Any Filter Dirty)", required: false
            }

            paragraph "<b>AQI Push Alerts & Virtual Switches</b>"
            input "alertThresholdIndoor", "number", title: "Indoor AQI Push Alert Threshold", defaultValue: 100, required: false
            input "badIndoorAlertSwitch", "capability.switch", title: "High Indoor AQI Virtual Switch (Turns ON during alert, OFF after 30m safe)", required: false
            
            input "alertThresholdOutdoor", "number", title: "Outdoor AQI Push Alert Threshold", defaultValue: 150, required: false
            input "badOutdoorAlertSwitch", "capability.switch", title: "High Outdoor AQI Virtual Switch (Turns ON during alert, OFF after 30m safe)", required: false
            
            paragraph "<b>Return Home Reminders</b>"
            input "enableReturnReminders", "bool", title: "Send a push reminder if returning home and AQI is still harmful?", defaultValue: true, submitOnChange: true
            if (enableReturnReminders) {
                input "awayNightModes", "mode", title: "Select your 'Away/Night' Modes", multiple: true, required: false
                input "homeModes", "mode", title: "Select your 'Return/Home' Modes", multiple: true, required: false
            }

            paragraph "<hr>"
            input "allowedModes", "mode", title: "Allowed Modes (Leave blank to run 24/7)", multiple: true, required: false
            input "quietModes", "mode", title: "Quiet Modes (Prevents purifiers from turning on UNLESS emergency threshold is met)", multiple: true, required: false
            input "quietOverrideAQI", "number", title: "Emergency AQI Threshold during Quiet Modes", required: false, defaultValue: 150
            input "emergencyAlertDelay", "number", title: "Minutes sustained in Emergency AQI before sending push alert", required: false, defaultValue: 60
            
            paragraph "<b>High Activity (Continuous) & Health Priority</b>"
            paragraph "<div style='font-size:11px; color:#555;'><i>Use Continuous Run Modes for temporary events (e.g., parties, dusting). For everyday 24/7 use, see Occupancy Mode below.</i></div>"
            input "continuousRunModes", "mode", title: "Continuous Run Modes (Forces 24/7 Operation for temporary high activity periods)", multiple: true, required: false
            input "sickModeSwitch", "capability.switch", title: "Sick Mode Virtual Switch 1 (Forces 24/7 Operation)", multiple: true, required: false
            input "sickModeSwitch2", "capability.switch", title: "Sick Mode Virtual Switch 2", required: false
            input "sickModeSwitch3", "capability.switch", title: "Sick Mode Virtual Switch 3", required: false
            input "sickAwayModes", "mode", title: "Away Modes (Pauses Sick Mode automatically when vacant)", multiple: true, required: false
            
            paragraph "<b>24/7 Occupancy Mode</b>"
            paragraph "<div style='font-size:11px; color:#555;'><i><b>The Difference:</b> Unlike Continuous Mode, this runs purifiers baseline 24/7 while you are home, but safely pauses them when you leave to save filter life. While away, they act as a safety net, only kicking on if AQI hits an emergency level.</i></div>"
            input "enable247Home", "bool", title: "Enable 24/7 Occupancy Mode", defaultValue: false, submitOnChange: true
            if (enable247Home) {
                input "home247Modes", "mode", title: "Home Modes (Runs Baseline 24/7)", multiple: true, required: true
                input "away247Modes", "mode", title: "Away Modes (Pauses Operation)", multiple: true, required: true
                input "awayOverrideAQI", "number", title: "Emergency AQI Threshold while Away (Turns ON if exceeded)", defaultValue: 100, required: false
            }

            paragraph "<b>Energy Saving: Air Stagnation Timer</b>"
            paragraph "<div style='font-size:11px; color:#555;'><i>Runs brief circulation cycles if the air has been stagnant and purifiers idle for hours.</i></div>"
            input "enableStagnationCycle", "bool", title: "Enable Stagnation Cycles", required: false, defaultValue: false, submitOnChange: true
            if (enableStagnationCycle) {
                input "stagnationIdleHours", "number", title: "Hours idle before triggering a cycle", required: false, defaultValue: 4
                input "stagnationRunMins", "number", title: "Circulation Cycle Duration (Minutes)", required: false, defaultValue: 15
            }
        }

        section("<b>3. External Monitoring & Seasonal Pollen</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Utilizes outdoor AQI and a built-in Seasonal Pollen algorithm to shift baseline targets automatically during high pollen months in your specific state.</div>"
            input "outdoorAQI", "capability.sensor", title: "Outdoor AQI Sensor", required: false
            
            input "enableSeasonalPollen", "bool", title: "Enable Seasonal Pollen Algorithm", defaultValue: false, submitOnChange: true
            if (enableSeasonalPollen) {
                input "userState", "enum", title: "Select Your US State", options: ["AL":"Alabama", "AK":"Alaska", "AZ":"Arizona", "AR":"Arkansas", "CA":"California", "CO":"Colorado", "CT":"Connecticut", "DE":"Delaware", "FL":"Florida", "GA":"Georgia", "HI":"Hawaii", "ID":"Idaho", "IL":"Illinois", "IN":"Indiana", "IA":"Iowa", "KS":"Kansas", "KY":"Kentucky", "LA":"Louisiana", "ME":"Maine", "MD":"Maryland", "MA":"Massachusetts", "MI":"Michigan", "MN":"Minnesota", "MS":"Mississippi", "MO":"Missouri", "MT":"Montana", "NE":"Nebraska", "NV":"Nevada", "NH":"New Hampshire", "NJ":"New Jersey", "NM":"New Mexico", "NY":"New York", "NC":"North Carolina", "ND":"North Dakota", "OH":"Ohio", "OK":"Oklahoma", "OR":"Oregon", "PA":"Pennsylvania", "RI":"Rhode Island", "SC":"South Carolina", "SD":"South Dakota", "TN":"Tennessee", "TX":"Texas", "UT":"Utah", "VT":"Vermont", "VA":"Virginia", "WA":"Washington", "WV":"West Virginia", "WI":"Wisconsin", "WY":"Wyoming"], required: true
                input "pollenThresholdDrop", "number", title: "Drop Target AQI by this much during High Pollen months", required: false, defaultValue: 10
                
                paragraph "<b>Continuous Pollen Mode</b>"
                input "continuousPollen", "bool", title: "Run Purifiers 24/7 during High Pollen months?", defaultValue: false
            }
        }

        if (!isWholeHouse) {
            section("<b>4. Zone Configuration</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Toggle individual air quality monitoring zones. Click on an enabled zone below to expand its settings.</div>"
                
                def numZ = getNumZones()
                for (int i = 1; i <= numZ; i++) {
                    input "enableZ${i}", "bool", title: "<b>Enable Zone ${i}</b>", submitOnChange: true
                }
            }
            
            def numZ = getNumZones()
            for (int i = 1; i <= numZ; i++) {
                if (settings["enableZ${i}"]) {
                    def currentZoneName = settings["z${i}Name"] ?: "Zone ${i}"
                    section("<b>⚙️ ${currentZoneName} Configuration</b>", hideable: true, hidden: true) {
                        input "z${i}Name", "text", title: "Zone Name", required: false, defaultValue: "Zone ${i}"
                        input "z${i}AQI", "capability.sensor", title: "AQI Sensor", required: true
                
                        input "z${i}Purifier", "capability.switch", title: "Zone Air Purifier Switch", required: false
                        input "z${i}TriggerSwitch", "capability.switch", title: "Trigger Switches", required: false, multiple: true
                        input "z${i}ScrubTime", "number", title: "Post-Cleaning Scrub Time (Mins)", required: false, defaultValue: 30
                        
                        paragraph "<b>Vacuum Integration & Smart Scrubbing</b>"
                        input "z${i}VacuumSensor", "capability.accelerationSensor", title: "Zone Vacuum Sensor (Detects 3 motions to trigger)", required: false
                        input "z${i}VacuumWindow", "number", title: "Zone Vacuum Motion Window (Minutes to detect 3 bumps)", required: false, defaultValue: 60
                        input "z${i}VacuumTime", "number", title: "Zone Vacuum Scrub Time (Minutes to run after detection regardless of AQI)", required: false, defaultValue: 15
                        
                        paragraph "<b>Zone Purifier Specs</b>"
                        input "z${i}CADR", "number", title: "Purifier CADR (CFM)", required: false, defaultValue: 100
                        input "z${i}SqFt", "number", title: "Room Square Footage", required: false, defaultValue: 150
                        input "z${i}Ceiling", "decimal", title: "Ceiling Height (Feet)", required: false, defaultValue: 8.0
                        input "z${i}FilterHours", "number", title: "Filter Lifespan (Hours)", required: false, defaultValue: 4380
                        input "resetZ${i}Filter", "button", title: "Reset Zone ${i} Filter"
                        
                        paragraph "<b>Overrides & Protection</b>"
                        input "z${i}PreventSwitch", "capability.switch", title: "Prevent Switch (e.g., TV - Stops purifier)", required: false
                        input "z${i}ContactSensor", "capability.contactSensor", title: "Open Door/Window Sensors (Forces purifiers OFF to save energy)", required: false, multiple: true
                        if (settings["z${i}PreventSwitch"]) {
                            input "z${i}OverrideLevel", "number", title: "Emergency Override AQI", required: false, defaultValue: 100
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def getNumZones() {
    return (settings.numberOfZones != null) ? settings.numberOfZones.toInteger() : 5
}

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def systemEventHandler(evt) {
    try {
        runIn(2, "evaluateSystem", [overwrite: true])
    } catch (e) {
        if (!e.toString().contains("ObjectAlreadyExistsException")) {
            log.error "${app.label}: Error executing systemEventHandler: ${e}"
        }
    }
}

def hvacHandler(evt) {
    def stateStr = evt.value?.toLowerCase()
    logAction("HVAC System operating state changed to: ${stateStr?.capitalize()}")

    if (stateStr == "idle" || stateStr == "off") {
        def overrun = (settings.hvacOverrunTime != null) ? settings.hvacOverrunTime : 15
        state.hvacScrubEnd = now() + (overrun * 60000)
        logAction("HVAC cycle ended. Starting ${overrun}-minute dust/dander scrub.")
    } else {
        state.remove("hvacScrubEnd")
    }
    runIn(2, "evaluateSystem", [overwrite: true])
}

def isHvacRunning() {
    if (!settings.hvacThermostats) return false
    def running = false
    settings.hvacThermostats.each { t ->
        def opState = t.currentValue("thermostatOperatingState")?.toLowerCase()
        if (opState && opState != "idle" && opState != "off" && opState != "pending cool" && opState != "pending heat") {
            running = true
        }
    }
    return running
}

def contactHandler(evt) {
    def devId = evt.device.id
    if (evt.value == "open") {
        def opens = state.contactOpenTimes ?: [:]
        opens[devId] = now()
        state.contactOpenTimes = opens
        logAction("Contact Sensor Opened: Triggering 5-minute wait before pausing purifiers.")
        runIn(300, "evaluateSystem", [overwrite: true]) // Re-evaluate in 5 minutes
    } else {
        def opens = state.contactOpenTimes ?: [:]
        opens.remove(devId)
        state.contactOpenTimes = opens
        logAction("Contact Sensor Closed: Resuming normal purifier logic.")
        runIn(2, "evaluateSystem", [overwrite: true])
    }
}

def vacuumHandler(evt) {
    def devId = evt.device.id
    def currentTime = now()
    
    def windowMins = 60
    def scrubMins = 15
    
    if (settings.isWholeHouse != false && settings.mainVacuumSensor?.id == devId) {
        windowMins = settings.mainVacuumWindow != null ? settings.mainVacuumWindow : 60
        scrubMins = settings.mainVacuumTime != null ? settings.mainVacuumTime : 15
    } else {
        def numZ = getNumZones()
        for (int i = 1; i <= numZ; i++) {
            if (settings["enableZ${i}"] && settings["z${i}VacuumSensor"]?.id == devId) {
                windowMins = settings["z${i}VacuumWindow"] != null ? settings["z${i}VacuumWindow"] : 60
                scrubMins = settings["z${i}VacuumTime"] != null ? settings["z${i}VacuumTime"] : 15
                break
            }
        }
    }
    
    def vEnds = state.vacuumScrubEnds ? new HashMap(state.vacuumScrubEnds) : [:]
    def isAlreadyScrubbing = vEnds[devId] && currentTime < vEnds[devId]

    // IF ALREADY RUNNING: Every single bump resets the 15-minute timer!
    if (isAlreadyScrubbing) {
        vEnds[devId] = currentTime + (scrubMins * 60000)
        state.vacuumScrubEnds = vEnds
        logAction("Vacuum bump detected while scrubbing. Resetting post-scrub timer to ${scrubMins} minutes.")
        runIn(1, "evaluateSystem", [overwrite: true])
        return
    }
    
    // IF NOT RUNNING: Do the standard 3-bump check to start it
    def vEvents = state.vacuumEvents ? new HashMap(state.vacuumEvents) : [:]
    def currentEvents = vEvents[devId] ? new ArrayList(vEvents[devId]) : []
    
    currentEvents.add(currentTime)
    def windowMs = windowMins * 60000
    def validEvents = currentEvents.findAll { it >= (currentTime - windowMs) }
    
    if (validEvents.size() >= 3) {
        vEnds[devId] = currentTime + (scrubMins * 60000)
        state.vacuumScrubEnds = vEnds
        
        vEvents[devId] = [] 
        state.vacuumEvents = vEvents
        
        logAction("Vacuum running detected (3 bumps). Forcing purifier ON. Timer will reset on every future bump.")
        runIn(1, "evaluateSystem", [overwrite: true])
    } else {
        vEvents[devId] = validEvents
        state.vacuumEvents = vEvents
        runIn(1, "evaluateSystem", [overwrite: true])
    }
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.purifierStarts) state.purifierStarts = [:]
    if (!state.purifierStops) state.purifierStops = [:]
    if (!state.runHistory) state.runHistory = [:]
    if (!state.filterWearMins) state.filterWearMins = [:]
    if (!state.filterAlertSent) state.filterAlertSent = [:]
    if (!state.scrubEndTimes) state.scrubEndTimes = [:]
    if (!state.vacuumEvents) state.vacuumEvents = [:]
    if (!state.vacuumScrubEnds) state.vacuumScrubEnds = [:]
    if (!state.emergencyStartTimes) state.emergencyStartTimes = [:]
    if (!state.emergencyAlertSent) state.emergencyAlertSent = [:]
    if (!state.contactOpenTimes) state.contactOpenTimes = [:]
    if (!state.stagnationEnds) state.stagnationEnds = [:]
    if (!state.alertHistory) state.alertHistory = [:]
    
    if (state.outdoorInAlarm == null) state.outdoorInAlarm = false
    if (state.indoorInAlarm == null) state.indoorInAlarm = false
    if (state.recoveryTimerActive == null) state.recoveryTimerActive = false
    
    state.currentMode = location.mode
    state.currentReason = "System Initialized"
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", "systemEventHandler")
    
    if (sickModeSwitch) subscribe(sickModeSwitch, "switch", "systemEventHandler")
    if (sickModeSwitch2) subscribe(sickModeSwitch2, "switch", "systemEventHandler")
    if (sickModeSwitch3) subscribe(sickModeSwitch3, "switch", "systemEventHandler")

    if (hvacThermostats) subscribe(hvacThermostats, "thermostatOperatingState", "hvacHandler")

    subscribe(location, "mode", modeChangeHandler)
    
    if (outdoorAQI) {
        subscribe(outdoorAQI, "airQualityIndex", "systemEventHandler")
        subscribe(outdoorAQI, "aqi", "systemEventHandler")
        subscribe(outdoorAQI, "pm25", "systemEventHandler")
    }
    
    if (isWholeHouse) {
        if (indoorAQI) {
            subscribe(indoorAQI, "airQualityIndex", "systemEventHandler")
            subscribe(indoorAQI, "aqi", "systemEventHandler")
            subscribe(indoorAQI, "pm25", "systemEventHandler")
        }
        if (mainTriggerSwitch) subscribe(mainTriggerSwitch, "switch", "systemEventHandler")
        if (mainVacuumSensor) subscribe(mainVacuumSensor, "acceleration.active", "vacuumHandler")
        if (mainPreventSwitch) subscribe(mainPreventSwitch, "switch", "systemEventHandler")
        if (mainContactSensor) subscribe(mainContactSensor, "contact", "contactHandler")
        if (mainPurifier) subscribe(mainPurifier, "switch", "systemEventHandler") 
    }
    
    if (!isWholeHouse) {
        def numZ = getNumZones()
        for (int i = 1; i <= numZ; i++) {
            if (settings["enableZ${i}"]) {
                if (settings["z${i}AQI"]) {
                    subscribe(settings["z${i}AQI"], "airQualityIndex", "systemEventHandler")
                    subscribe(settings["z${i}AQI"], "aqi", "systemEventHandler")
                    subscribe(settings["z${i}AQI"], "pm25", "systemEventHandler")
                }
                if (settings["z${i}PreventSwitch"]) subscribe(settings["z${i}PreventSwitch"], "switch", "systemEventHandler")
                if (settings["z${i}ContactSensor"]) subscribe(settings["z${i}ContactSensor"], "contact", "contactHandler")
                if (settings["z${i}TriggerSwitch"]) subscribe(settings["z${i}TriggerSwitch"], "switch", "systemEventHandler")
                if (settings["z${i}VacuumSensor"]) subscribe(settings["z${i}VacuumSensor"], "acceleration.active", "vacuumHandler")
                if (settings["z${i}Purifier"]) subscribe(settings["z${i}Purifier"], "switch", "systemEventHandler")
            }
        }
    }
    
    schedule("0 0,15,30,45 * ? * *", evaluateSystem) // Evaluate every 15 minutes as a fallback for Stagnation & Timers
    schedule("0 0 0 * * ?", "midnightHandler")
    scheduleRandomReminders()
    
    logAction("App Initialized. Logic Engine Ready.")
    evaluateSystem()
}

// ------------------------------------------------------------------------------
// APPLICATION LOGIC
// ------------------------------------------------------------------------------

def appButtonHandler(btn) {
    if (btn == "resetHistory") {
        state.runHistory = [:]
        state.purifierStarts = [:]
        state.purifierStops = [:]
        state.vacuumEvents = [:]
        state.vacuumScrubEnds = [:]
        state.alertHistory = [:]
        logAction("Performance Tracking history cleared.")
    } else if (btn == "resetMainFilter") {
        if (mainPurifier) {
            def fw = state.filterWearMins ? new HashMap(state.filterWearMins) : [:]
            fw.remove(mainPurifier.id)
            state.filterWearMins = fw
        }
        def fa = state.filterAlertSent ? new HashMap(state.filterAlertSent) : [:]
        fa.remove(mainPurifier?.id)
        state.filterAlertSent = fa
        logAction("Main Purifier Filter reset to 100%.")
    } else if (btn.startsWith("resetZ")) {
        def zNum = btn.replace("resetZ", "").replace("Filter", "")
        def dev = settings["z${zNum}Purifier"]
        if (dev) {
            def fw = state.filterWearMins ? new HashMap(state.filterWearMins) : [:]
            fw.remove(dev.id)
            state.filterWearMins = fw
            
            def fa = state.filterAlertSent ? new HashMap(state.filterAlertSent) : [:]
            fa.remove(dev.id)
            state.filterAlertSent = fa
            logAction("Zone ${zNum} Filter reset to 100%.")
        }
    }
}

def getAQI(device) {
    if (!device) return null
    def aqi = device.currentValue("airQualityIndex")
    if (aqi == null) aqi = device.currentValue("aqi")
    if (aqi == null) aqi = device.currentValue("pm25")
    return aqi != null ? aqi.toInteger() : null
}

def isHighPollenMonth() {
    if (!enableSeasonalPollen || !userState) return false
    def month = new Date().format("MM", location.timeZone).toInteger()
    
    def south = ["AL", "AR", "FL", "GA", "LA", "MS", "NC", "SC", "TX"]
    def midwestNE = ["CT", "DE", "IL", "IN", "IA", "KS", "KY", "ME", "MD", "MA", "MI", "MN", "MO", "NE", "NH", "NJ", "NY", "ND", "OH", "PA", "RI", "SD", "TN", "VT", "VA", "WV", "WI"]
    def west = ["AK", "CA", "CO", "HI", "ID", "MT", "NV", "OR", "UT", "WA", "WY"]
    def sw = ["AZ", "NM", "OK", "TX"]

    if (userState in south) return month in [2, 3, 4, 5, 9, 10]
    else if (userState in midwestNE) return month in [4, 5, 6, 8, 9, 10]
    else if (userState in west) return month in [2, 3, 4, 5, 6]
    else if (userState in sw) return month in [3, 4, 5, 8, 9, 10]
    
    return false
}

def getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "The application is disabled via the Master Switch."
    if (allowedModes && !(allowedModes as List).contains(location.mode)) return "<span style='color:orange;'><b>App Disabled by Mode</b></span>"
    
    def sickSwitches = []
    if (sickModeSwitch) sickSwitches += (sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch])
    if (sickModeSwitch2) sickSwitches << sickModeSwitch2
    if (sickModeSwitch3) sickSwitches << sickModeSwitch3
    def isSickModeActive = sickSwitches.any { it?.currentValue("switch") == "on" }
    
    def isSickAway = sickAwayModes ? (sickAwayModes as List).contains(location.mode) : false
    
    def is247Home = enable247Home && home247Modes ? (home247Modes as List).contains(location.mode) : false
    def is247Away = enable247Home && away247Modes ? (away247Modes as List).contains(location.mode) : false

    if (isSickModeActive && isSickAway) return "<span style='color:orange;'><b>Sick Mode Paused:</b></span> System is in Away Mode."
    if (isSickModeActive && !isSickAway) return "<span style='color:red;'><b>Sick Mode Active:</b></span> Purifiers are locked ON for Health Priority."

    def isContinuousMode = continuousRunModes ? (continuousRunModes as List).contains(location.mode) : false
    
    if (isContinuousMode) return "<span style='color:green;'><b>Continuous Mode Active:</b></span> Purifiers are locked ON for high activity mode."
    if (is247Home) return "<span style='color:green;'><b>24/7 Occupancy Active:</b></span> Purifiers are running baseline while home."
    if (is247Away) return "<span style='color:orange;'><b>Occupancy Paused (Away):</b></span> Monitoring for Emergency AQI only."

    def isQuiet = quietModes ? (quietModes as List).contains(location.mode) : false
    def isPollenContinuous = (enableSeasonalPollen && continuousPollen && isHighPollenMonth())
    
    if (isQuiet) return "<span style='color:purple;'><b>Quiet Mode Active:</b></span> Purifiers are locked OFF unless emergency thresholds are met."
    if (isPollenContinuous) return "<span style='color:blue;'><b>Continuous Mode Active:</b></span> Running 24/7 due to High Seasonal Pollen."
    if (enableStagnationCycle) return "Monitoring actively. Enforcing Target AQI and Air Stagnation Cycles."
    
    return "Monitoring actively. System is enforcing Target AQI requirements."
}

def modeChangeHandler(evt) { 
    def oldMode = state.currentMode ?: location.mode
    def newMode = evt.value
    state.currentMode = newMode
    
    if (enableReturnReminders && awayNightModes && homeModes) {
        def awayList = awayNightModes instanceof List ? awayNightModes : [awayNightModes]
        def homeList = homeModes instanceof List ? homeModes : [homeModes]
        
        if (awayList.contains(oldMode) && homeList.contains(newMode)) {
            if (state.outdoorInAlarm || state.indoorInAlarm) {
                logAction("User returned home during an active AQI alarm. Scheduling 15-minute reminder.")
                runIn(15 * 60, "returnHomeReminder")
            }
        }
    }
    evaluateSystem() 
}

def returnHomeReminder() {
    if (state.outdoorInAlarm) {
        def currentOutAQI = getAQI(outdoorAQI) ?: 0
        triggerBadAQIAlert("Welcome back. Reminder: Outdoor AQI is still harmful (${currentOutAQI}).")
    }
    if (state.indoorInAlarm) {
        def mainIndoorAQIVal = isWholeHouse ? (getAQI(indoorAQI) ?: 0) : 0
        def highestZoneAQI = 0
        if (!isWholeHouse) {
            def numZ = getNumZones()
            for (int i = 1; i <= numZ; i++) {
                if (settings["enableZ${i}"] && settings["z${i}AQI"]) {
                    def aqiVal = getAQI(settings["z${i}AQI"])
                    if (aqiVal != null && aqiVal > highestZoneAQI) highestZoneAQI = aqiVal
                }
            }
        }
        def maxIndoorAQI = isWholeHouse ? Math.max(mainIndoorAQIVal, highestZoneAQI) : highestZoneAQI
        triggerBadAQIAlert("Welcome back. Reminder: Indoor AQI is still harmful (${maxIndoorAQI}).")
    }
}

def sendAlert(msg) {
    if (notifyDevice) notifyDevice.deviceNotification("AQI Alert: ${msg}")
    logAction("ALERT SENT: ${msg}")
}

def triggerBadAQIAlert(reasonMsg) {
    sendAlert(reasonMsg)
}

def recordAlert(type) {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    def aHist = state.alertHistory ? new HashMap(state.alertHistory) : [:]
    
    if (!aHist[today]) aHist[today] = [indoor: 0, outdoor: 0]
    
    def todayCounts = new HashMap(aHist[today])
    todayCounts[type] = (todayCounts[type] ?: 0) + 1
    aHist[today] = todayCounts
    
    def keys = aHist.keySet().sort().reverse()
    if (keys.size() > 7) aHist = aHist.subMap(keys[0..6])
    state.alertHistory = aHist
}

def liveRunMins(deviceId) { 
    return (state.purifierStarts && state.purifierStarts[deviceId]) ? (now() - state.purifierStarts[deviceId]) / 60000.0 : 0.0 
}

def saveRunTime(deviceId, runMins, currentAQI = 50) {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    def rHist = state.runHistory ? new HashMap(state.runHistory) : [:]
    if (!rHist[today]) rHist[today] = [:]
    
    rHist[today][deviceId] = (rHist[today][deviceId] ?: 0.0) + runMins
    
    def keys = rHist.keySet().sort().reverse()
    if (keys.size() > 7) rHist = rHist.subMap(keys[0..6])
    state.runHistory = rHist
    
    def wearMultiplier = currentAQI > 100 ? 1.5 : 1.0
    def fWear = state.filterWearMins ? new HashMap(state.filterWearMins) : [:]
    fWear[deviceId] = (fWear[deviceId] ?: 0.0) + (runMins * wearMultiplier)
    state.filterWearMins = fWear
}

def controlPurifier(device, command, currentAQI = 50) {
    if (!device) return
    def currentState = device.currentValue("switch")
    
    if (command == "on" && currentState != "on") {
        device.on()
        def starts = state.purifierStarts ? new HashMap(state.purifierStarts) : [:]
        starts[device.id] = now()
        state.purifierStarts = starts
        
        // Remove from stop tracking when running
        if (state.purifierStops?.get(device.id)) {
            def stops = state.purifierStops ? new HashMap(state.purifierStops) : [:]
            stops.remove(device.id)
            state.purifierStops = stops
        }
        
    } else if (command == "off" && currentState != "off") {
        device.off()
        if (state.purifierStarts && state.purifierStarts[device.id]) {
            def runMins = (now() - state.purifierStarts[device.id]) / 60000.0
            saveRunTime(device.id, runMins, currentAQI)
            
            def starts = state.purifierStarts ? new HashMap(state.purifierStarts) : [:]
            starts.remove(device.id)
            state.purifierStarts = starts
            
            checkFilterHealth(device.id)
        }
        
        // Record stop time for Stagnation Tracking
        def stops = state.purifierStops ? new HashMap(state.purifierStops) : [:]
        stops[device.id] = now()
        state.purifierStops = stops
    }
}

def checkFilterHealth(deviceId) {
    def fMins = state.filterWearMins?.get(deviceId) ?: 0.0
    def fMax = 4380 * 60.0
    
    if (isWholeHouse && mainPurifier && mainPurifier.id == deviceId) fMax = (mainFilterHours ?: 4380) * 60.0
    else if (!isWholeHouse) {
        def numZ = getNumZones()
        for (int i = 1; i <= numZ; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Purifier"]?.id == deviceId) {
                fMax = (settings["z${i}FilterHours"] ?: 4380) * 60.0
                break
            }
        }
    }
    
    def fPct = Math.max(0.0, 100.0 - ((fMins / fMax) * 100.0))
    def threshold = settings.filterNotifyThreshold != null ? settings.filterNotifyThreshold.toBigDecimal() : 5.0
    
    if (fPct <= threshold && !state.filterAlertSent?.get(deviceId)) {
        if (notifyFilter != false) sendAlert("Filter for device requires replacement (Below ${threshold}% Life Remaining).")
        def fa = state.filterAlertSent ? new HashMap(state.filterAlertSent) : [:]
        fa[deviceId] = true
        state.filterAlertSent = fa
    }
}

// --- MAIN LOGIC ENGINE ---
def evaluateSystem(evt = null) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
        turnOffAll()
        state.currentReason = "OFF: Master Switch is Disabled."
        return
    }
    if (allowedModes && !(allowedModes as List).contains(location.mode)) {
        turnOffAll()
        state.currentReason = "OFF: Current Location Mode is not allowed."
        return
    }

    // HVAC Scrubbing Timer Management
    if (state.hvacScrubEnd) {
        if (now() < state.hvacScrubEnd) {
            runIn(((state.hvacScrubEnd - now()) / 1000).toInteger() + 1, "evaluateSystem", [overwrite: false])
        } else {
            state.remove("hvacScrubEnd")
            logAction("HVAC post-cycle scrub complete.")
        }
    }

    def sickSwitches = []
    if (sickModeSwitch) sickSwitches += (sickModeSwitch instanceof List ? sickModeSwitch : [sickModeSwitch])
    if (sickModeSwitch2) sickSwitches << sickModeSwitch2
    if (sickModeSwitch3) sickSwitches << sickModeSwitch3
    
    def isSickModeActive = sickSwitches.any { it?.currentValue("switch") == "on" }
    def isSickAway = sickAwayModes ? (sickAwayModes as List).contains(location.mode) : false
    def forceSickMode = isSickModeActive && !isSickAway
    
    def isContinuousMode = continuousRunModes ? (continuousRunModes as List).contains(location.mode) : false
    def isQuiet = quietModes ? (quietModes as List).contains(location.mode) : false
    
    def is247Home = enable247Home && home247Modes ? (home247Modes as List).contains(location.mode) : false
    def is247Away = enable247Home && away247Modes ? (away247Modes as List).contains(location.mode) : false
    def aEmergency = settings.awayOverrideAQI != null ? settings.awayOverrideAQI : 100
    
    def isHvacActive = isHvacRunning()
    def isHvacScrubbing = state.hvacScrubEnd && now() < state.hvacScrubEnd

    def baseTarget = targetAQI ?: 50
    def hBuffer = mainHysteresis ?: 10
    
    def isHighPollen = isHighPollenMonth()
    
    def isPollenContinuous = false
    if (enableSeasonalPollen && isHighPollen) {
        baseTarget -= (pollenThresholdDrop ?: 10)
    }
    if (enableSeasonalPollen && continuousPollen && isHighPollen) {
        isPollenContinuous = true
    }

    def anyZoneNeedsPurification = false
    def highestZoneAQI = 0
    def activeZoneReasons = [] 
    def hasConfiguredZones = false
    
    if (!state.scrubEndTimes) state.scrubEndTimes = [:]
    if (!state.emergencyStartTimes) state.emergencyStartTimes = [:]
    if (!state.emergencyAlertSent) state.emergencyAlertSent = [:]
    if (!state.vacuumScrubEnds) state.vacuumScrubEnds = [:]
    if (!state.stagnationEnds) state.stagnationEnds = [:]

    if (!isWholeHouse) {
        def numZ = getNumZones()
        for (int i = 1; i <= numZ; i++) {
            if (settings["enableZ${i}"] && settings["z${i}AQI"]) {
                hasConfiguredZones = true
                def aqiVal = getAQI(settings["z${i}AQI"])
                if (aqiVal == null) continue
                if (aqiVal > highestZoneAQI) highestZoneAQI = aqiVal
                
                def zNeedsIt = false
                def zName = settings["z${i}Name"] ?: "Zone ${i}"
                def emergencyQ = settings["z${i}OverrideLevel"] ?: 100
                
                def isForcedOn = false
                def pState = settings["z${i}Purifier"]?.currentValue("switch")
                def currentThreshold = (pState == "on") ? (baseTarget - hBuffer) : baseTarget

                if (forceSickMode || isContinuousMode || is247Home) {
                    zNeedsIt = true
                    isForcedOn = true
                } else if (isQuiet) { 
                    if (aqiVal >= (quietOverrideAQI ?: 150)) zNeedsIt = true 
                } else if (is247Away) {
                    if (aqiVal >= aEmergency) zNeedsIt = true
                } else if (aqiVal > currentThreshold || isPollenContinuous) {
                    zNeedsIt = true
                }

                // HVAC forces zones ON
                if (isHvacActive || isHvacScrubbing) {
                    zNeedsIt = true
                    isForcedOn = true
                }

                def isScrubbing = false
                if (settings["z${i}TriggerSwitch"]) {
                    if (settings["z${i}TriggerSwitch"].any { it.currentValue("switch") == "on" }) {
                        zNeedsIt = true; isForcedOn = true
                        def se = state.scrubEndTimes ? new HashMap(state.scrubEndTimes) : [:]
                        se["z${i}"] = now() + ((settings["z${i}ScrubTime"] ?: 30) * 60000)
                        state.scrubEndTimes = se
                    } else if (state.scrubEndTimes?.get("z${i}")) {
                        if (now() < state.scrubEndTimes.get("z${i}")) {
                            zNeedsIt = true; isScrubbing = true
                            runIn(((state.scrubEndTimes.get("z${i}") - now()) / 1000).toInteger(), "evaluateSystem", [overwrite: false])
                        }
                    }
                }
                
                // Zone Vacuum check (Runs for full duration regardless of AQI)
                def zVacSensor = settings["z${i}VacuumSensor"]
                def isVacScrubbing = false
                if (zVacSensor && state.vacuumScrubEnds?.get(zVacSensor.id)) {
                    if (now() < state.vacuumScrubEnds[zVacSensor.id]) {
                        zNeedsIt = true; isForcedOn = true; isVacScrubbing = true
                        runIn(((state.vacuumScrubEnds[zVacSensor.id] - now()) / 1000).toInteger(), "evaluateSystem", [overwrite: false])
                    } else {
                        def ve = state.vacuumScrubEnds ? new HashMap(state.vacuumScrubEnds) : [:]
                        ve.remove(zVacSensor.id)
                        state.vacuumScrubEnds = ve
                    }
                }

                // Check Contact Sensors (Open Windows)
                def pBlockedByContact = false
                if (settings["z${i}ContactSensor"]) {
                    settings["z${i}ContactSensor"].each { sensor ->
                        if (sensor.currentValue("contact") == "open") {
                            def openTime = state.contactOpenTimes?.get(sensor.id)
                            if (openTime && (now() - openTime) >= 300000) { // 5 minutes
                                pBlockedByContact = true
                            }
                        }
                    }
                }

                // Stagnation Cycle Check
                def zCycleActive = false
                if (!zNeedsIt && enableStagnationCycle && !isQuiet && !pBlockedByContact && settings["z${i}Purifier"]) {
                     def stopTime = state.purifierStops?.get(settings["z${i}Purifier"].id) ?: now()
                     def maxIdleMs = (settings.stagnationIdleHours ?: 4) * 3600000
                     if ((now() - stopTime) > maxIdleMs) {
                         def stagEnds = state.stagnationEnds ? new HashMap(state.stagnationEnds) : [:]
                         if (!stagEnds["z${i}"]) {
                             stagEnds["z${i}"] = now() + ((settings.stagnationRunMins ?: 15) * 60000)
                             state.stagnationEnds = stagEnds
                             logAction("Air Stagnation: ${zName} has been idle for ${settings.stagnationIdleHours ?: 4} hours. Starting circulation cycle.")
                         }
                     }
                     
                     if (state.stagnationEnds?.get("z${i}") && now() < state.stagnationEnds.get("z${i}")) {
                         zNeedsIt = true
                         zCycleActive = true
                     } else if (state.stagnationEnds?.get("z${i}")) {
                         def stagEnds = state.stagnationEnds ? new HashMap(state.stagnationEnds) : [:]
                         stagEnds.remove("z${i}")
                         state.stagnationEnds = stagEnds
                     }
                }

                if (zNeedsIt && !isForcedOn && !isScrubbing && !isVacScrubbing && settings["z${i}PreventSwitch"]) {
                    if (settings["z${i}PreventSwitch"].currentValue("switch") == "on") {
                        if (aqiVal < emergencyQ) zNeedsIt = false 
                    }
                }
                
                // Open Window Absolutely Overrides (unless it's an extreme emergency AQI)
                if (pBlockedByContact && aqiVal < 300) {
                    zNeedsIt = false
                }
                
                if (zNeedsIt && aqiVal >= emergencyQ) {
                    def est = state.emergencyStartTimes ? new HashMap(state.emergencyStartTimes) : [:]
                    if (!est.get("z${i}")) {
                        est["z${i}"] = now()
                        state.emergencyStartTimes = est
                    }
                    def elapsedMins = (now() - state.emergencyStartTimes.get("z${i}")) / 60000.0
                    if (elapsedMins >= (emergencyAlertDelay ?: 60) && !state.emergencyAlertSent?.get("z${i}")) {
                        sendAlert("⚠️ High AQI: ${zName} has been at ${aqiVal} for over ${emergencyAlertDelay} minutes.")
                        def eas = state.emergencyAlertSent ? new HashMap(state.emergencyAlertSent) : [:]
                        eas["z${i}"] = true
                        state.emergencyAlertSent = eas
                    }
                } else {
                    def est = state.emergencyStartTimes ? new HashMap(state.emergencyStartTimes) : [:]
                    est.remove("z${i}")
                    state.emergencyStartTimes = est
                    
                    def eas = state.emergencyAlertSent ? new HashMap(state.emergencyAlertSent) : [:]
                    eas.remove("z${i}")
                    state.emergencyAlertSent = eas
                }

                if (settings["z${i}Purifier"]) {
                    if (zNeedsIt && pState != "on") {
                        controlPurifier(settings["z${i}Purifier"], "on", aqiVal)
                        logAction("BMS Command -> Turned ON ${zName} Purifier (AQI: ${aqiVal}${isScrubbing ? ' | Scrubbing' : ''}${isVacScrubbing ? ' | Vacuum Active' : ''}${isHvacActive ? ' | HVAC Active' : ''}${isHvacScrubbing ? ' | HVAC Scrubbing' : ''}${forceSickMode ? ' | Sick Mode' : ''}${isContinuousMode ? ' | Continuous Mode' : ''}${is247Home ? ' | 24/7 Occupancy Active' : ''})")
                    } else if (!zNeedsIt && pState != "off") {
                        controlPurifier(settings["z${i}Purifier"], "off", aqiVal)
                        def offReason = pBlockedByContact ? "Window/Door Open" : "AQI Satisfied or Away Mode"
                        logAction("BMS Command -> Turned OFF ${zName} Purifier (${offReason}: ${aqiVal})")
                    }
                }

                if (pBlockedByContact) {
                     activeZoneReasons << "${zName} (Window Open)"
                } else if (zNeedsIt) {
                    anyZoneNeedsPurification = true
                    if (forceSickMode) activeZoneReasons << "${zName} (Sick Mode)"
                    else if (isContinuousMode) activeZoneReasons << "${zName} (Continuous Mode)"
                    else if (is247Home) activeZoneReasons << "${zName} (24/7 Occupancy Mode)"
                    else if (isVacScrubbing) activeZoneReasons << "${zName} (Vacuum Active)"
                    else if (isScrubbing) activeZoneReasons << "${zName} (Scrubbing)"
                    else if (isHvacActive) activeZoneReasons << "${zName} (HVAC Active)"
                    else if (isHvacScrubbing) activeZoneReasons << "${zName} (HVAC Scrubbing)"
                    else if (isForcedOn) activeZoneReasons << "${zName} (Trigger Switch)"
                    else if (zCycleActive) activeZoneReasons << "${zName} (Air Stagnation)"
                    else if (is247Away) activeZoneReasons << "${zName} (Emergency Away Override)"
                    else activeZoneReasons << "${zName} (AQI: ${aqiVal})"
                }
            }
        }
    }

    def mainIndoorAQIVal = isWholeHouse ? (getAQI(indoorAQI) ?: 0) : 0
    
    if (isWholeHouse && mainPurifier) {
        def isMainForcedOn = false
        def isMainScrubbing = false
        def isMainVacScrubbing = false
        def mainHouseNeedsIt = false
        
        if (mainTriggerSwitch) {
            if (mainTriggerSwitch.any { it.currentValue("switch") == "on" }) {
                isMainForcedOn = true
                state.mainScrubEnd = now() + ((mainScrubTime ?: 30) * 60000)
            } else if (state.mainScrubEnd) {
                if (now() < state.mainScrubEnd) {
                    isMainForcedOn = true; isMainScrubbing = true
                    runIn(((state.mainScrubEnd - now()) / 1000).toInteger(), "evaluateSystem", [overwrite: false])
                }
            }
        }
        
        // Main Vacuum check (Runs for full duration regardless of AQI)
        if (mainVacuumSensor && state.vacuumScrubEnds?.get(mainVacuumSensor.id)) {
            if (now() < state.vacuumScrubEnds[mainVacuumSensor.id]) {
                isMainForcedOn = true
                isMainVacScrubbing = true
                mainHouseNeedsIt = true
                runIn(((state.vacuumScrubEnds[mainVacuumSensor.id] - now()) / 1000).toInteger(), "evaluateSystem", [overwrite: false])
            } else {
                def ve = state.vacuumScrubEnds ? new HashMap(state.vacuumScrubEnds) : [:]
                ve.remove(mainVacuumSensor.id)
                state.vacuumScrubEnds = ve
            }
        }

        def mState = mainPurifier.currentValue("switch")
        def currentMainThreshold = (mState == "on") ? (baseTarget - hBuffer) : baseTarget
        
        if (forceSickMode || isContinuousMode || is247Home) {
            mainHouseNeedsIt = true
            isMainForcedOn = true
        } else if (isQuiet) { 
            if (mainIndoorAQIVal >= (mainOverrideLevel ?: 100)) mainHouseNeedsIt = true 
        } else if (is247Away) {
            if (mainIndoorAQIVal >= aEmergency) mainHouseNeedsIt = true
        } else if (mainIndoorAQIVal > currentMainThreshold || isPollenContinuous) {
            mainHouseNeedsIt = true
        }
        
        if (isHvacActive || isHvacScrubbing) {
            isMainForcedOn = true
            mainHouseNeedsIt = true
        }
        
        // Check Contact Sensors (Open Windows)
        def mainBlockedByContact = false
        if (mainContactSensor) {
            mainContactSensor.each { sensor ->
                if (sensor.currentValue("contact") == "open") {
                    def openTime = state.contactOpenTimes?.get(sensor.id)
                    if (openTime && (now() - openTime) >= 300000) { // 5 minutes
                        mainBlockedByContact = true
                    }
                }
            }
        }
        
        def mainNeedsIt = anyZoneNeedsPurification || isMainForcedOn || mainHouseNeedsIt
        
        def mainCycleActive = false
        if (!mainNeedsIt && enableStagnationCycle && !isQuiet && !mainBlockedByContact) {
            def stopTime = state.purifierStops?.get(mainPurifier.id) ?: now()
            def maxIdleMs = (settings.stagnationIdleHours ?: 4) * 3600000
            if ((now() - stopTime) > maxIdleMs) {
                def stagEnds = state.stagnationEnds ? new HashMap(state.stagnationEnds) : [:]
                if (!stagEnds["main"]) {
                    stagEnds["main"] = now() + ((settings.stagnationRunMins ?: 15) * 60000)
                    state.stagnationEnds = stagEnds
                    logAction("Air Stagnation: Main House has been idle for ${settings.stagnationIdleHours ?: 4} hours. Starting circulation cycle.")
                }
            }
            
            if (state.stagnationEnds?.get("main") && now() < state.stagnationEnds.get("main")) {
                mainNeedsIt = true
                mainCycleActive = true
            } else if (state.stagnationEnds?.get("main")) {
                def stagEnds = state.stagnationEnds ? new HashMap(state.stagnationEnds) : [:]
                stagEnds.remove("main")
                state.stagnationEnds = stagEnds
            }
        }
        
        if (mainNeedsIt && !isMainForcedOn && !isMainScrubbing && !isMainVacScrubbing && mainPreventSwitch) {
            if (mainPreventSwitch.currentValue("switch") == "on") {
                def mOverride = mainOverrideLevel ?: 100
                if (mainIndoorAQIVal < mOverride && highestZoneAQI < mOverride) {
                    mainNeedsIt = false
                    mainCycleActive = false
                }
            }
        }

        // Open Window Absolutely Overrides (unless it's an extreme emergency AQI)
        if (mainBlockedByContact && mainIndoorAQIVal < 300) {
            mainNeedsIt = false
        }

        def currentActionReason = "Idle"
        if (mainNeedsIt) {
            if (forceSickMode) currentActionReason = "ON: Health Priority (Sick Mode Active)"
            else if (isContinuousMode) currentActionReason = "ON: High Activity (Continuous Mode Active)"
            else if (is247Home) currentActionReason = "ON: Occupancy (24/7 Home Mode Active)"
            else if (isMainVacScrubbing) currentActionReason = "ON: Vacuum Active (Regardless of AQI)"
            else if (isMainScrubbing) currentActionReason = "ON: Main Post-Cleaning Scrubbing Active"
            else if (isHvacActive) currentActionReason = "ON: HVAC System Running"
            else if (isHvacScrubbing) currentActionReason = "ON: HVAC Post-Cycle Scrub Active"
            else if (isMainForcedOn) currentActionReason = "ON: Forced by Main Trigger Switch"
            else if (isQuiet) currentActionReason = "ON: Emergency AQI Override during Quiet Mode"
            else if (is247Away) currentActionReason = "ON: Emergency AQI Override during Away Mode"
            else if (isPollenContinuous) currentActionReason = "ON: Continuous Run for High Seasonal Pollen"
            else if (mainHouseNeedsIt) currentActionReason = "ON: Main Indoor AQI (${mainIndoorAQIVal}) > Target (${currentMainThreshold})"
            else if (mainCycleActive) currentActionReason = "ON: Stagnation Circulation Cycle"
            else if (anyZoneNeedsPurification) currentActionReason = "ON: Triggered by " + activeZoneReasons.join(", ")
        } else {
            if (mainBlockedByContact) {
                currentActionReason = "OFF: Blocked by Open Window/Door (Saving Energy)"
            } else if (mainPreventSwitch && mainPreventSwitch.currentValue("switch") == "on") {
                currentActionReason = "OFF: Blocked by Prevent Switch (AQI: ${mainIndoorAQIVal})"
            } else if (is247Away) {
                currentActionReason = "OFF: Occupancy Paused (Away Mode Active, AQI < Emergency Level)"
            } else if (isQuiet) {
                currentActionReason = "OFF: Quiet Mode Active (AQI: ${mainIndoorAQIVal} < Emergency Level)"
            } else {
                def maxCurrent = Math.max(mainIndoorAQIVal, highestZoneAQI)
                currentActionReason = hasConfiguredZones ? "OFF: All zones and Main satisfied (Max AQI: ${maxCurrent} < Target ${currentMainThreshold})" : "OFF: Indoor AQI Satisfied (${mainIndoorAQIVal} < Target ${currentMainThreshold})"
            }
        }

        if (mainNeedsIt && mState != "on") {
            controlPurifier(mainPurifier, "on", Math.max(mainIndoorAQIVal, highestZoneAQI))
            logAction("BMS Command -> Main Purifier ON. Reason: ${currentActionReason}")
            state.currentReason = currentActionReason
        } else if (!mainNeedsIt && mState != "off") {
            controlPurifier(mainPurifier, "off", Math.max(mainIndoorAQIVal, highestZoneAQI))
            logAction("BMS Command -> Main Purifier OFF. Reason: ${currentActionReason}")
            state.currentReason = currentActionReason
        } else {
            state.currentReason = currentActionReason 
        }
    } else if (!isWholeHouse) {
        if (forceSickMode) {
            state.currentReason = "ON: Health Priority (Sick Mode Active)"
        } else if (isContinuousMode) {
            state.currentReason = "ON: High Activity (Continuous Mode Active)"
        } else if (is247Home) {
            state.currentReason = "ON: Occupancy (24/7 Home Mode Active)"
        } else if (anyZoneNeedsPurification) {
            state.currentReason = "MIXED: Active Zones -> " + activeZoneReasons.join(" | ")
        } else if (is247Away) {
            state.currentReason = "OFF: Occupancy Paused (Away Mode Active, AQI < Emergency Level)"
        } else {
            state.currentReason = "OFF: All zones satisfied (AQI < Target)."
        }
    }
    
    def currentOutAQI = getAQI(outdoorAQI) ?: 0
    def maxIndoorAQI = isWholeHouse ? Math.max(mainIndoorAQIVal, highestZoneAQI) : highestZoneAQI
    
    def outThreshold = alertThresholdOutdoor ?: 150
    def inThreshold = alertThresholdIndoor ?: 100
    
    def isOutdoorSafe = currentOutAQI < (outThreshold - 10)
    def isIndoorSafe = maxIndoorAQI < (inThreshold - 10)
    
    // --- AQI Alert & Virtual Switch Handling ---
    
    // Outdoor AQI Alarm Activation
    if (alertThresholdOutdoor != null && currentOutAQI >= outThreshold) {
        if (!state.outdoorInAlarm) {
            state.outdoorInAlarm = true
            recordAlert("outdoor")
            if (badOutdoorAlertSwitch && badOutdoorAlertSwitch.currentValue("switch") != "on") {
                badOutdoorAlertSwitch.on()
                logAction("High Outdoor AQI Virtual Switch turned ON.")
            }
            triggerBadAQIAlert("Outdoor AQI is harmful (${currentOutAQI}).")
        }
    }
    
    // Indoor AQI Alarm Activation
    if (alertThresholdIndoor != null && maxIndoorAQI >= inThreshold) {
        if (!state.indoorInAlarm) {
            state.indoorInAlarm = true
            recordAlert("indoor")
            if (badIndoorAlertSwitch && badIndoorAlertSwitch.currentValue("switch") != "on") {
                badIndoorAlertSwitch.on()
                logAction("High Indoor AQI Virtual Switch turned ON.")
            }
            triggerBadAQIAlert("Indoor AQI is harmful (${maxIndoorAQI}).")
        }
    }
    
    // --- 30-Minute Safe Recovery Logic ---
    if (state.outdoorInAlarm || state.indoorInAlarm) {
        if (isOutdoorSafe && isIndoorSafe) {
            if (!state.recoveryTimerActive) {
                state.recoveryTimerActive = true
                runIn(1800, "allSafeRecoveryHandler", [overwrite: true])
                logAction("AQI levels safe. Starting 30-minute recovery timer to clear alarms and switches.")
            }
        } else {
            if (state.recoveryTimerActive) {
                state.recoveryTimerActive = false
                unschedule("allSafeRecoveryHandler")
                logAction("AQI spiked again. 30-minute recovery timer cancelled.")
            }
        }
    }
}

def allSafeRecoveryHandler() {
    state.recoveryTimerActive = false
    
    if (state.outdoorInAlarm) {
        state.outdoorInAlarm = false
        if (badOutdoorAlertSwitch && badOutdoorAlertSwitch.currentValue("switch") != "off") {
            badOutdoorAlertSwitch.off()
        }
    }
    
    if (state.indoorInAlarm) {
        state.indoorInAlarm = false
        if (badIndoorAlertSwitch && badIndoorAlertSwitch.currentValue("switch") != "off") {
            badIndoorAlertSwitch.off()
        }
    }
    
    sendAlert("✅ Air Quality Cleared: All indoor and outdoor AQI have returned to safe levels for 30 minutes.")
    logAction("Recovery Complete: Virtual switches turned OFF and Safe Alert sent.")
}

def turnOffAll() {
    if (isWholeHouse && mainPurifier) controlPurifier(mainPurifier, "off")
    else if (!isWholeHouse) {
        def numZ = getNumZones()
        for (int i = 1; i <= numZ; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Purifier"]) controlPurifier(settings["z${i}Purifier"], "off")
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

// --- RANDOM DAILY REMINDER SCHEDULER & EXECUTORS ---
def midnightHandler() {
    logAction("SYSTEM: Executing daily midnight rollover.")
    scheduleRandomReminders()
}

def scheduleRandomReminders() {
    if (settings.randomFilterSwitch) {
        def rand = new java.util.Random()
        def h2 = rand.nextInt(11) + 8
        def m2 = rand.nextInt(60)
        schedule("0 ${m2} ${h2} * * ?", "fireRandomFilterReminder")
        logAction("SYSTEM: Scheduled daily random Filter reminder for ${h2}:${m2.toString().padLeft(2, '0')}")
    }
}

def fireRandomFilterReminder() {
    def anyDirty = false
    def threshold = settings.filterNotifyThreshold != null ? settings.filterNotifyThreshold.toBigDecimal() : 5.0
    
    if (isWholeHouse && mainPurifier) {
        def fMins = state.filterWearMins?.get(mainPurifier.id) ?: 0.0
        def fMax = (mainFilterHours ?: 4380) * 60.0
        def fPct = Math.max(0.0, 100.0 - ((fMins / fMax) * 100.0))
        if (fPct <= threshold) anyDirty = true
    } else if (!isWholeHouse) {
        def numZ = getNumZones()
        for (int i = 1; i <= numZ; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Purifier"]) {
                def pId = settings["z${i}Purifier"].id
                def fMins = state.filterWearMins?.get(pId) ?: 0.0
                def fMax = (settings["z${i}FilterHours"] ?: 4380) * 60.0
                def fPct = Math.max(0.0, 100.0 - ((fMins / fMax) * 100.0))
                if (fPct <= threshold) {
                    anyDirty = true
                    break
                }
            }
        }
    }
    
    if (anyDirty && settings.randomFilterSwitch) {
        settings.randomFilterSwitch.on()
        if (notifyFilter != false) {
            sendAlert("Daily Reminder: One or more air filters require replacement (Below ${threshold}% Life Remaining).")
        }
        runIn(600, "turnOffRandomFilterSwitch", [overwrite: true])
        logAction("Random daily Dirty Filter reminder activated for 10 minutes, and push notification sent.")
    }
}

def turnOffRandomFilterSwitch() {
    settings.randomFilterSwitch?.off()
}
