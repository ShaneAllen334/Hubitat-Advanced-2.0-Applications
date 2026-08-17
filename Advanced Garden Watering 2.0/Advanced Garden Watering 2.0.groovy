/**
 * Advanced Garden Watering 2.0
 */ 

definition(
    name: "Advanced Garden Watering 2.0",
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
}

String getHumanReadableStatus() {
    def zoneCount = settings.waterZones ? settings.waterZones.size() : (settings.manualZoneCount ? settings.manualZoneCount.toInteger() : 1)
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    
    if (state.isWatering) {
        def elapsedMins = (now() - (state.currentZoneStartTime ?: now())) / 60000.0
        def remaining = (state.activeCycleDuration ?: state.activeDuration ?: 0) - elapsedMins
        if (remaining < 0) remaining = 0.0 
        
        def zoneName = state.currentZoneName ?: "Unknown Zone"
        def weatherImpactStr = (state.customZoneDurations && state.customZoneDurations.size() > 0) ? "Manual Custom Override" : (state.activeWeatherImpact ?: "Standard Time Applied")
        def modeStr = settings.waterZones ? "Automated" : "Manual"
        def cycleStr = (state.totalCycles > 1) ? " [Cycle ${(state.currentCycleIndex ?: 0) + 1} of ${state.totalCycles}]" : ""
        
        return "<span style='color:blue; font-size:14px;'><b>💧 ACTIVE: Watering ${zoneName} (Zone ${state.currentZoneIndex + 1} of ${zoneCount})${cycleStr} [${modeStr} Mode]</b></span><br>" +
               "<span style='color:#333; font-size:13px;'>Time Remaining on this Zone: <b>${String.format('%.1f', remaining)} mins</b></span><br>" +
               "<span style='color:#555; font-size:12px;'><i>Weather Impact: ${weatherImpactStr}</i></span>"
    }

    def wateredToday = false
    if (state.lastWateredTime) {
        def lastWaterDay = new Date(state.lastWateredTime as Long).format("yyyy-MM-dd", location.timeZone)
        if (lastWaterDay == todayStr) wateredToday = true
    }
    
    if (wateredToday) {
        return "<span style='color:green; font-size:14px;'><b>✅ WATERING COMPLETE: Garden has already been watered today.</b></span>"
    }

    if (state.waitingForConfirmation) {
        return "<span style='color:purple; font-size:14px;'><b>🚰 WAITING ON USER: Please connect water and press confirmation button.</b></span>"
    }
    
    if (state.stormInterceptCount != null && state.stormInterceptCount > 0) {
        return "<span style='color:#17a2b8; font-size:14px;'><b>🌦️ PREDICTIVE HOLD: Watering deferred due to incoming storm forecast. Awaiting results.</b></span>"
    }

    if (state.weatherHoldActive) {
        return "<span style='color:orange; font-size:14px;'><b>⚠ WEATHER HOLD: Watering suspended due to recent rain or severe conditions.</b></span>"
    }
    
    if (state.manualAbortDate == todayStr) {
        return "<span style='color:orange;'><b>System Idle.</b></span> Watering was manually aborted today."
    }
    
    def today = new Date().format("EEEE", location.timeZone)
    if (settings.waterDays && settings.waterDays.contains(today)) {
        if (state.rainDelayCount && state.rainDelayCount > 0) {
            return "<span style='color:#17a2b8;'><b>🌧️ ACTIVE RAIN DELAY.</b></span> Delay ${state.rainDelayCount} of 4 in progress."
        }
        return "<span style='color:green;'><b>Tracking Stable Conditions.</b></span> Scheduled to prompt you today at ${settings.waterTime ? toDateTime(settings.waterTime).format("h:mm a", location.timeZone) : 'Unknown Time'}."
    }
    
    return "<span style='color:gray;'><b>System Idle.</b></span> Not scheduled to run today."
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            if (sensorTemp && sensorRainDaily) {
                def tP = getFloat(sensorTemp, ["temperature", "tempf"])
                def hP = getFloat(sensorHum, ["humidity"], 50.0)
                
                def t = tP ?: 0.0
                def h = hP ?: 0.0

                def rRate = getFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate"], 0.0)
                def rDaily = getFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
                def lux = 0.0
                if (sensorLux) {
                    if (sensorLux.currentValue("illuminance") != null) lux = getFloat(sensorLux, ["illuminance"], 0.0)
                    else lux = getFloat(sensorLux, ["solarRadiation", "solarradiation"], 0.0) * 126.7
                }
                def wind = getFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], 0.0)
        
                def vpd = state.currentVPD ?: 0.0
                def vpdColor = vpd < 0.5 ? "green" : (vpd < 1.0 ? "orange" : "red")
                
                def zoneCount = settings.waterZones ? settings.waterZones.size() : (settings.manualZoneCount ? settings.manualZoneCount.toInteger() : 1)
                def calcTime = state.lastCalculatedDuration != null ? state.lastCalculatedDuration : (settings.baseWaterTime ?: 0)
                def totalEstTime = calcTime * zoneCount
                def lastWaterStr = state.lastWateredTime ? new Date(state.lastWateredTime as Long).format("MM/dd hh:mm a", location.timeZone) : "Never"
                
                // Efficiency Metrics Math
                def waterSavedMins = 0
                def baseTotalTime = (settings.baseWaterTime ?: 15) * zoneCount
                if (calcTime < (settings.baseWaterTime ?: 15)) {
                    waterSavedMins = baseTotalTime - totalEstTime
                }
                def efficiencyRating = baseTotalTime > 0 ? Math.round(((baseTotalTime - totalEstTime) / baseTotalTime) * 100) : 0
                if (efficiencyRating < 0) efficiencyRating = 0
                
                def moistureStatus = "Unknown"
                def today = new Date().format("EEEE", location.timeZone)
                def currentDayStr = new Date().format("yyyy-MM-dd", location.timeZone)
                def wateredToday = false
                
                if (state.lastWateredTime) {
                    def lastWaterDay = new Date(state.lastWateredTime as Long).format("yyyy-MM-dd", location.timeZone)
                    if (lastWaterDay == currentDayStr) wateredToday = true
                }
                
                def rainSkip = settings.rainCreditThreshold != null ? settings.rainCreditThreshold.toFloat() : 0.1
                if (rDaily >= rainSkip) {
                    moistureStatus = "<span style='color:green; font-weight:bold;'>Satisfied (Rain Credit Met)</span>"
                } else if (state.weatherHoldActive) {
                    moistureStatus = "<span style='color:green; font-weight:bold;'>Satisfied (Weather Hold / Heavy Rain)</span>"
                } else if (state.manualAbortDate == currentDayStr) {
                    moistureStatus = "<span style='color:orange; font-weight:bold;'>Satisfied (Manually Aborted Today)</span>"
                } else if (wateredToday) {
                    moistureStatus = "<span style='color:green; font-weight:bold;'>Satisfied (Watered Today)</span>"
                } else if (settings.waterDays && settings.waterDays.contains(today)) {
                    moistureStatus = "<span style='color:orange; font-weight:bold;'>Needs Water (Scheduled Today)</span>"
                } else {
                    moistureStatus = "<span style='color:gray; font-weight:bold;'>Satisfied (Not Scheduled Today)</span>"
                }
                
                def activeZoneRow = ""
                if (state.isWatering) {
                    def elapsedMins = (now() - (state.currentZoneStartTime ?: now())) / 60000.0
                    def remaining = (state.activeCycleDuration ?: state.activeDuration ?: 0) - elapsedMins
                    if (remaining < 0) remaining = 0.0
                    def cycleStr = (state.totalCycles > 1) ? " (Cycle ${(state.currentCycleIndex ?: 0) + 1} of ${state.totalCycles})" : ""
                    activeZoneRow = "<tr><td class=\"dash-hl\" style=\"background-color:#cce5ff; color:#004085;\">ACTIVE WATERING</td><td colspan=\"2\" class=\"dash-val\" style=\"background-color:#cce5ff;\"><span style='color:blue; font-weight:bold;'>${state.currentZoneName ?: 'Unknown Zone'}</span>${cycleStr} &mdash; <b>${String.format('%.1f', remaining)} mins</b> remaining</td></tr>"
                }
                
                def valveRows = ""
                if (settings.waterZones || settings.mainValve) {
                    valveRows += "<tr><td colspan=\"3\" class=\"dash-subhead\">Valve Health & Status</td></tr>"
                    
                    def allValves = []
                    if (settings.mainValve) allValves << settings.mainValve
                    if (settings.waterZones) allValves.addAll(settings.waterZones)
                    
                    allValves.each { v ->
                        if (v) {
                            def vState = v.currentValue("valve") ?: "Unknown"
                            def vBatt = v.currentValue("battery") != null ? "${v.currentValue('battery')}%" : "Mains/N/A"
                            
                            def hStatus = v.currentValue("presence") ?: v.currentValue("healthStatus") ?: v.currentValue("DeviceWatch-DeviceStatus") ?: "Unknown"
                            def hString = hStatus.toString().toLowerCase()
                            def pColor = "gray"
                            if (hString in ["present", "online"]) pColor = "green"
                            else if (hString in ["not present", "offline"]) pColor = "red"
                            
                            def vColor = vState == "open" ? "blue" : (vState == "closed" ? "gray" : "red")
                            
                            valveRows += "<tr><td class=\"dash-hl\">${v.displayName}</td><td colspan=\"2\" class=\"dash-val\">State: <span style='color:${vColor}; font-weight:bold;'>${vState.capitalize()}</span> | Battery: <b>${vBatt}</b> | Health: <span style='color:${pColor}; font-weight:bold;'>${hStatus.toString().capitalize()}</span></td></tr>"
                        }
                    }
                }

                def soilRows = ""
                if (settings.enableSoilSensors && settings.soilSensors) {
                    def sData = evaluateSoilSensors()
                    soilRows += "<tr><td colspan=\"3\" class=\"dash-subhead\">Local Soil Moisture Network</td></tr>"
                    if (sData) {
                        def healthyCount = sData.validSensors
                        def faultCount = sData.faults.size()
                        soilRows += "<tr><td class=\"dash-hl\">Network Status</td><td colspan=\"2\" class=\"dash-val\"><b>${healthyCount}</b> Active Sensors | <b>${faultCount}</b> Faults</td></tr>"
                        
                        if (healthyCount > 0) {
                            soilRows += "<tr><td class=\"dash-hl\">Moisture Overview</td><td colspan=\"2\" class=\"dash-val\">Avg: <b>${String.format('%.1f', sData.avg)}%</b> | Driest: <b>${String.format('%.1f', sData.min)}%</b> | Wettest: <b>${String.format('%.1f', sData.max)}%</b></td></tr>"
                            soilRows += "<tr><td class=\"dash-hl\">Active Evaluation Strategy</td><td colspan=\"2\" class=\"dash-val\">${settings.soilStrategy} ➔ <span style='color:blue; font-weight:bold;'>${String.format('%.1f', sData.strategyValue)}%</span></td></tr>"
                        } else {
                            soilRows += "<tr><td class=\"dash-hl\">Moisture Overview</td><td colspan=\"2\" class=\"dash-val\" style=\"color:orange;\">Awaiting valid sensor data...</td></tr>"
                        }

                        if (faultCount > 0) {
                            def fStr = sData.faults.join("<br>")
                            soilRows += "<tr><td class=\"dash-hl\" style=\"color:red;\">Sensor Fault Matrix</td><td colspan=\"2\" class=\"dash-val\" style=\"color:red; font-size:12px;\">${fStr}</td></tr>"
                        }
                    }
                }

                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 30%; }
                    .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                    .dash-val { text-align: left !important; padding-left: 15px !important; }
                </style>
                <table class="dash-table">
                    <thead><tr><th>Metric</th><th>Current Value</th><th>Impact on Watering</th></tr></thead>
                    <tbody>
                        <tr><td colspan="3" class="dash-subhead">Resource Efficiency Metrics</td></tr>
                        <tr><td class="dash-hl">Water Optimization Rating</td><td><span style='color:green; font-weight:bold;'>${efficiencyRating}% Efficiency</span></td><td>Saved <b>${waterSavedMins} mins</b> vs base baseline</td></tr>
                        
                        <tr><td colspan="3" class="dash-subhead">Schedule & Prediction Status</td></tr>
                        ${activeZoneRow}
                        <tr><td class="dash-hl">Garden Moisture Needs</td><td colspan="2" class="dash-val">${moistureStatus}</td></tr>
                        <tr><td class="dash-hl">Calculated Time Per Zone</td><td colspan="2" class="dash-val"><span style='color:blue; font-weight:bold;'>${calcTime} minutes</span> (Total: ${totalEstTime}m)</td></tr>
                        <tr><td class="dash-hl">Last Watered Sequence</td><td colspan="2" class="dash-val">${lastWaterStr}</td></tr>
                        <tr><td class="dash-hl">Zones Configured</td><td colspan="2" class="dash-val"><b>${zoneCount}</b> ${settings.waterZones ? "Automated" : "Manual"} Zones</td></tr>
                        
                        ${valveRows}
                        ${soilRows}

                        <tr><td colspan="3" class="dash-subhead">Core Environmental Sensors</td></tr>
                        <tr><td class="dash-hl">Temperature</td><td><b>${String.format('%.1f', t)}°</b></td><td>Evaporation Driver</td></tr>
                        <tr><td class="dash-hl">VPD (Drying Power)</td><td><span style='color:${vpdColor}; font-weight:bold;'>${String.format('%.2f', vpd)} kPa</span></td><td>Drives Duration Multiplier</td></tr>
                        <tr><td class="dash-hl">Solar Radiation</td><td><b>${Math.round(lux)} lux</b></td><td>Ground Heating Potential</td></tr>
                        <tr><td class="dash-hl">Wind Dynamics</td><td><b>${wind}</b></td><td>${wind > (settings.windThreshold ?: 15) ? "<span style='color:red;'>Wind Drift High</span>" : "Drift Minimal"}</td></tr>
                        
                        <tr><td colspan="3" class="dash-subhead">Precipitation Accumulation & Forecast</td></tr>
                        <tr><td class="dash-hl">Today's Rain</td><td colspan="2" class="dash-val"><span style='color:blue; font-weight:bold;'>${rDaily}</span></td></tr>
                        <tr><td class="dash-hl">Current Rain Rate</td><td colspan="2" class="dash-val">${rRate}/hr</td></tr>
                        <tr><td class="dash-hl">Predictive Forecast Status</td><td colspan="2" class="dash-val">${(forecastRainSwitch?.currentValue("switch") == "on" || forecastStormSwitch?.currentValue("switch") == "on") ? "<span style='color:purple; font-weight:bold;'>Storm Imminent</span>" : "Clear Skies"}</td></tr>
                        
                        <tr><td colspan="3" class="dash-subhead">Historical Soil Tracking (Yesterday)</td></tr>
                        <tr><td class="dash-hl">Yesterday's Rain</td><td colspan="2" class="dash-val">${state.yesterdayRain ?: 0.0}</td></tr>
                        <tr><td class="dash-hl">Yesterday's Watering</td><td colspan="2" class="dash-val">${state.yesterdayWaterDuration ?: 0.0} mins</td></tr>
                        <tr><td class="dash-hl">Yesterday's Max Evap (VPD)</td><td colspan="2" class="dash-val">${String.format('%.2f', state.yesterdayMaxVPD ?: 0.0)} kPa</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML

                def logicPanel = "<div style='margin-top: 20px; padding: 15px; background: #e6f2ff; border-left: 5px solid #007bff; font-size: 13px; color: #004085;'>"
                logicPanel += "<h4 style='margin-top:0; border-bottom:1px solid #b8daff; padding-bottom:5px;'>Engine Diagnostics: Smart Duration Matrix</h4>"
                logicPanel += "<div style='max-height: 400px; overflow-y: auto; border: 1px solid #b8daff;'><table class='dash-table' style='margin-top:0; background: white; color: #333;'><thead style='position: sticky; top: 0; box-shadow: 0 1px 2px rgba(0,0,0,0.1);'><tr><th>Algorithm</th><th>Status</th><th>Effect</th><th>Diagnostic Output</th></tr></thead><tbody>"
                
                if (state.algoDiagnostics && state.algoDiagnostics.size() > 0) {
                    state.algoDiagnostics.each { diag ->
                        def eff = diag.effect ?: "0"
                        def effColor = eff.contains("+") || eff.startsWith("x1.") || eff.startsWith("x2.") ? "red" : (eff.contains("-") || (eff.startsWith("x0.")) ? "green" : "black")
                        def statusColor = diag.status == "ON" ? "green" : (diag.status == "ACTIVE" ? "blue" : (diag.status == "HOLD" ? "orange" : (diag.status == "ERR" ? "red" : "gray")))
                        logicPanel += "<tr><td style='font-weight:bold;'>${diag.name}</td><td style='color:${statusColor};'>${diag.status}</td><td style='color:${effColor}; font-weight:bold;'>${eff}</td><td>${diag.desc}</td></tr>"
                    }
                } else {
                    logicPanel += "<tr><td colspan='4'>Waiting for schedule evaluation...</td></tr>"
                }
                
                logicPanel += "</tbody></table></div>"
                logicPanel += "<div style='margin-top:10px;'><b>Consensus & Confidence:</b> " + (state.confidenceReasoning ?: "Waiting for consensus...") + "</div>"
                logicPanel += "</div>"

                paragraph logicPanel
                
            } else {
                paragraph "<i>Primary sensors missing. Click configuration below to assign devices.</i>"
            }
        }

        section("<b>7-Day Watering History</b>", hideable: true) {
            def statsHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Date</th><th>Total Run Time</th><th>Delays / Aborts</th></tr></thead><tbody>"
            
            def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
            def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
            sdf.setTimeZone(location.timeZone)
            
            for (int i = 0; i < 7; i++) {
                def dStr = sdf.format(new Date(now() - (i * 86400000L)))
                def dayMins = 0.0
                
                if (state.runHistory && state.runHistory[dStr]) {
                    dayMins = state.runHistory[dStr]
                }
                
                // Add currently active running time if it's today so the dashboard is live
                if (dStr == todayStr && state.isWatering && state.currentZoneStartTime) {
                    dayMins += (now() - state.currentZoneStartTime) / 60000.0
                }
                
                def alerts = state.alertHistory?.get(dStr) ?: 0
                def displayDate = (dStr == todayStr) ? "<b>Today (${dStr})</b>" : dStr
                
                statsHTML += "<tr><td>${displayDate}</td><td>${String.format("%.1f", dayMins)} mins</td><td>${alerts}</td></tr>"
            }
            
            statsHTML += "</tbody></table>"
            paragraph statsHTML
        }

        section("<b>1. Garden Zones (Valves or Manual)</b>", hideable: true, hidden: true) {
            paragraph "<i>If you have smart valves, select them below. If you water manually by moving a hose/sprinkler, leave the valves blank and set your Manual Zone count instead. The app will act as your smart assistant, notifying you when to swap zones.</i>"
            input "waterZones", "capability.valve", title: "Select Smart Water Valves (Optional)", multiple: true, required: false
            input "manualZoneCount", "number", title: "Number of Manual Zones (Ignored if smart valves are selected)", defaultValue: 1, required: true
            input "mainValve", "capability.valve", title: "Main Sprinkler Shutoff Valve (Optional Fail-Safe)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Opens at the start of the sequence and closes when everything is finished.</span>", required: false
        }

        section("<b>2. Primary Environment Sensors (Required)</b>", hideable: true, hidden: true) {
            input "sensorTemp", "capability.sensor", title: "Outdoor Temperature Sensor", required: true
            input "sensorHum", "capability.sensor", title: "Outdoor Humidity Sensor", required: true
            input "sensorRainDaily", "capability.sensor", title: "Daily Rain Accumulation Sensor", required: true
        }

        section("<b>3. Advanced Prediction Sensors (Optional)</b>", hideable: true, hidden: true) {
            input "sensorRain", "capability.sensor", title: "Instant Rain Rate Sensor (For live rain abort / active delays)", required: false
            input "sensorLux", "capability.illuminanceMeasurement", title: "Solar Radiation / Lux Sensor (For Evapotranspiration calc)", required: false
            input "sensorWind", "capability.sensor", title: "Wind Speed Sensor (For drift abort)", required: false
            
            paragraph "<hr><b>Predictive Forward-Looking Forecast Switches</b>"
            input "forecastRainSwitch", "capability.switch", title: "Rain Predicted Next Hour (Virtual Switch)", required: false
            input "forecastStormSwitch", "capability.switch", title: "Thunderstorm Probable (Virtual Switch)", required: false
        }
        
        section("<b>4. Watering Parameters</b>", hideable: true, hidden: true) {
            input "baseWaterTime", "number", title: "Base Watering Time Per Zone (Minutes) [Peak Summer Rate]", defaultValue: 15, required: true
            input "minWaterTime", "number", title: "Minimum Watering Time (Minutes)", defaultValue: 5, required: true
            input "maxWaterTime", "number", title: "Maximum Watering Time (Minutes)", defaultValue: 45, required: true
            input "rainCreditThreshold", "decimal", title: "Rain Skip Threshold (Inches/mm)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>If rain exceeds this amount, watering is aborted/skipped.</span>", defaultValue: 0.1, required: true
            
            paragraph "<hr><b>Cycle & Soak (Runoff Prevention)</b>"
            input "enableCycleSoak", "bool", title: "Enable Cycle & Soak", defaultValue: false
            input "maxCycleTime", "number", title: "Max Time Per Cycle (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>If calculated time exceeds this, it will be broken into multiple smaller watering cycles across all zones.</span>", defaultValue: 15
        }
        
        section("<b>5. Smart Logic & Overrides</b>", hideable: true, hidden: true) {
            input "enableSeasonalScaling", "bool", title: "<b>Dynamic Seasonal Scaling</b><br><span style='font-size: 12px; color: #555; font-weight: normal;'>Automatically scales the baseline time based on the calendar month (e.g., 100% in July, 60% in October).</span>", defaultValue: true, submitOnChange: true
            input "enableMakeBeforeBreak", "bool", title: "<b>Make-Before-Break Valve Logic</b><br><span style='font-size: 12px; color: #555; font-weight: normal;'>Prevents water hammer by opening the next valve before closing the previous one.</span>", defaultValue: true, submitOnChange: true
            input "enablePredictiveSkipping", "bool", title: "<b>Predictive Rain Skipping (Storm Harvester)</b><br><span style='font-size: 12px; color: #555; font-weight: normal;'>Aborts the watering schedule if a storm is actively forecasted, and re-evaluates hours later to see if it missed.</span>", defaultValue: true, submitOnChange: true
            
            paragraph "<hr>"
            input "enableYesterdayLogic", "bool", title: "Enable Historical Tracking<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Tracks yesterday's rain, temperature, and watering to calculate today's soil retention and moisture debt.</span>", defaultValue: true
            input "enableVPDLogic", "bool", title: "VPD Evaporation Tracking<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Dynamically adjusts watering time based on how dry the air has been.</span>", defaultValue: true
            input "enableRainDelay", "bool", title: "Active Rain Delay<br><span style='font-size: 12px; color: #555; font-weight: normal;'>If it is actively raining at your scheduled time, push the prompt back by 30 minutes (attempts up to 4 times) before giving up for the day.</span>", defaultValue: true
            input "enableLiveRainAbort", "bool", title: "Live Rain Abort<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Instantly shut down active watering (or notify you to stop) if precipitation begins *during* a cycle.</span>", defaultValue: true
            input "windThreshold", "decimal", title: "Wind Drift Limit (mph/kph)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Skip watering if wind exceeds this speed to prevent water waste.</span>", defaultValue: 15.0, required: true
            input "freezeThreshold", "decimal", title: "Freeze Protect Limit (Temp)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Do not water if temperature is below this threshold.</span>", defaultValue: 38.0, required: true
        }

        section("<b>6. Scheduling & Manual Confirmation</b>", hideable: true, hidden: true) {
            input "waterDays", "enum", title: "Allowed Watering Days", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: true
            input "waterTime", "time", title: "Primary Time of Day to Evaluate/Prompt (Morning)", required: true
            input "waterTimeEvening", "time", title: "Optional Secondary Evaluation Time (Evening)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Evaluates again later in the day if morning was skipped due to rain or storms.</span>", required: false
            input "waterPromptSwitch", "capability.switch", title: "Main Water Prompt Switch<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Turns on for 10 minutes to alert you to turn on the main water supply/hose.</span>", required: false
            
            paragraph "<hr><b>Physical Confirmation Button Configuration</b>"
            input "confirmationButton", "capability.pushableButton", title: "Select Button Device", required: true
            input "confirmationButtonNumber", "number", title: "Button Number to Press", defaultValue: 1, required: true
            input "confirmationButtonAction", "enum", title: "Button Action to Trigger", options: ["pushed", "held", "doubleTapped", "released"], defaultValue: "pushed", required: true
        }
        
        section("<b>7. Custom Notifications & Outputs</b>", hideable: true, hidden: true) {
            input "notifyDevices", "capability.notification", title: "Send Push Notification To (Zone Swaps, Completion, Abort, Reminders):", multiple: true, required: true
            input "switchStartPrompt", "capability.switch", title: "Virtual Start Switch<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Turns on for 10 minutes when the watering sequence officially begins.</span>", required: false
            input "switchZonePrompt", "capability.switch", title: "Virtual Zone Swap Prompt Switch<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Turns on for 10 minutes when it is time to move the hose/sprinkler to the NEXT zone.</span>", required: false
            input "switchCompletion", "capability.switch", title: "Virtual Completion Switch<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Turns on for 10 minutes when sequence finishes.</span>", required: false
        }
        
        section("<b>8. Local Soil Moisture Network</b>", hideable: true, hidden: true) {
            input "enableSoilSensors", "bool", title: "Enable Smart Soil Moisture Network Logic", defaultValue: false, submitOnChange: true
            
            if (enableSoilSensors) {
                paragraph "<i>Select up to 10 soil sensors. The engine will automatically detect and isolate faulty/stuck sensors from the math. (Note: Third Reality soil sensors report moisture via the Humidity attribute).</i>"
                input "soilSensors", "capability.sensor", title: "Select Soil Moisture Sensors", multiple: true, required: false
                input "soilStrategy", "enum", title: "Evaluation Strategy", options: ["Average (Combined)", "Worst Case (Driest)", "Best Case (Wettest)"], defaultValue: "Average (Combined)", required: true
                input "soilWetThreshold", "number", title: "Saturated Threshold (%)<br><span style='font-size:12px; color:#555;'>If the active strategy evaluates above this, watering is completely aborted.</span>", defaultValue: 60, required: true
                input "soilDryThreshold", "number", title: "Bone Dry Threshold (%)<br><span style='font-size:12px; color:#555;'>If the active strategy evaluates below this, base watering time is boosted by 25%.</span>", defaultValue: 25, required: true
            }
        }

        section("<b>Action History & Debugging</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true
            
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            } else {
                paragraph "<i>No history logged yet.</i>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }
        
        if (app.id) {
            section("<b>Global Actions & Overrides</b>", hideable: true, hidden: true) {
                def zCount = settings.waterZones ? settings.waterZones.size() : (settings.manualZoneCount ? settings.manualZoneCount.toInteger() : 1)
                
                paragraph "<b>Custom Manual Watering Override</b><br><span style='font-size:12px; color:#555;'>Set the exact minutes for each zone and press start. This bypasses the smart engine and Cycle & Soak. Leave blank to default to Base Water Time.</span>"
                
                for (int i = 1; i <= zCount; i++) {
                    def zName = settings.waterZones ? settings.waterZones[i-1].displayName : "Manual Zone ${i}"
                    input "customRunTime_${i}", "number", title: "Duration for ${zName} (Mins)", required: false, submitOnChange: true
                }
                
                input "startCustomRunBtn", "button", title: "▶️ Start Custom Watering Run"
                paragraph "<hr>"
                input "forceWaterBtn", "button", title: "▶️ Force Start Standard Sequence (Bypass Smart Checks)"
                input "skipZoneBtn", "button", title: "⏭️ Skip Current Zone"
                input "stopWaterBtn", "button", title: "⏹️ Stop Active Sequence / Clear Prompt"
                input "clearStateBtn", "button", title: "⚠ Reset Internal State"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.algoDiagnostics) state.algoDiagnostics = []
    if (!state.runHistory) state.runHistory = [:]
    if (!state.alertHistory) state.alertHistory = [:]
    if (!state.soilHealth) state.soilHealth = [:]
    
    state.isWatering = false
    state.currentZoneIndex = 0
    state.currentZoneStartTime = null
    state.currentZoneName = null
    state.totalCycles = 1
    state.currentCycleIndex = 0
    state.weatherHoldActive = false
    state.waitingForConfirmation = false
    state.rainDelayCount = 0
    state.customZoneDurations = []
    state.stormInterceptCount = 0
    
    subscribeMulti(sensorTemp, ["temperature", "tempf"], "envHandler")
    subscribeMulti(sensorHum, ["humidity"], "envHandler")
    subscribeMulti(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], "envHandler")
    subscribeMulti(sensorLux, ["illuminance", "solarradiation", "solarRadiation"], "envHandler")
    subscribeMulti(sensorWind, ["windSpeed", "windspeedmph", "wind"], "envHandler")
    subscribeMulti(sensorRain, ["rainRate", "hourlyrainin", "precipRate"], "rainLiveHandler")
    
    subscribe(location, "systemStart", "hubRebootHandler")
    
    if (confirmationButton) {
        def action = settings.confirmationButtonAction ?: "pushed"
        subscribe(confirmationButton, action, "confirmationHandler")
        logAction("✅ Subscribed to ${confirmationButton.displayName} | Listening for: ${action}")
    }
    
    scheduleDailyTime(settings.waterTime, "evaluateAndRunSchedule")
    if (settings.waterTimeEvening) {
        scheduleDailyTime(settings.waterTimeEvening, "evaluateAndRunEvening")
    }
    schedule("0 0 0 * * ?", "resetConfirmation") // Midnight reset
    
    logAction("Advanced Garden Watering Initialized.")
    queueEval()
}

def scheduleDailyTime(timeSetting, handlerMethod) {
    if (!timeSetting) return
    def tDate = toDateTime(timeSetting)
    def h = tDate.format("H", location.timeZone)
    def m = tDate.format("m", location.timeZone)
    def cronStr = "0 ${m} ${h} * * ?"
    schedule(cronStr, handlerMethod)
    logDebug("Scheduled ${handlerMethod} with cron: ${cronStr}")
}

def evaluateAndRunEvening() {
    logAction("Evening schedule triggered.")
    evaluateAndRunSchedule()
}

def isMetric() { return location?.temperatureScale == "C" }

def subscribeMulti(device, attrs, handler) {
    if (!device) return
    attrs.each { attr -> subscribe(device, attr, handler) }
}

def getFloat(device, attrs, fallbackStr = null) {
    if (!device) return fallbackStr
    for (attr in attrs) {
        def val = device.currentValue(attr)
        if (val != null) {
            try { return val.toString().replaceAll("[^\\d.-]", "").toFloat() } catch (e) {}
        }
    }
    return fallbackStr
}

def queueEval() {
    runIn(5, "updateEnvironmentalData")
}

def envHandler(evt) { queueEval() }

def rainLiveHandler(evt) {
    queueEval()
    if (state.isWatering && settings.enableLiveRainAbort) {
        def rRate = 0.0
        try { rRate = evt.value.toString().replaceAll("[^\\d.-]", "").toFloat() } catch(e) {}
        if (rRate > 0.05) {
            abortWatering("Live Rain Detected (${rRate}/hr)")
        }
    }
}

def hubRebootHandler(evt) {
    if (state.isWatering || state.waitingForConfirmation) {
        logAction("CRITICAL: Hub reboot detected while sequence was active or pending.")
        abortWatering("Hub rebooted during active watering. Emergency shutoff applied to prevent flooding.")
    }
}

def confirmationHandler(evt) {
    def nowTime = now()
    if (state.lastBtnPress && (nowTime - state.lastBtnPress) < 3000) {
        logAction("Ignoring rapid duplicate button press (debounce).")
        return
    }
    state.lastBtnPress = nowTime

    def targetBtn = (settings.confirmationButtonNumber ?: 1).toString()
    def evtVal = evt.value?.toString()?.replace(".0", "")
    
    logAction("🔘 RAW BUTTON DATA: Action: [${evt.name}] | Value: [${evt.value}] | Expected: [${targetBtn}]")
    
    if (evtVal == null || evtVal == "") evtVal = "1" 
    
    if (evtVal == targetBtn) {
        if (state.waitingForConfirmation) {
            logAction("User confirmed water connection via physical button. Proceeding to sequence validation.")
            state.waitingForConfirmation = false
            safeOff(waterPromptSwitch)
            unschedule("turnOffPromptSwitch")
            startWateringSequence()
        } else if (state.isWatering) {
            logAction("Button pressed while watering is active. Aborting sequence.")
            abortWatering("Manually aborted via physical button press.")
        } else {
            logAction("Confirmation button pressed, but no scheduled sequence was pending. Initiating forced start.")
            state.waitingForConfirmation = false
            safeOff(waterPromptSwitch)
            updateEnvironmentalData()
            
            state.forceBypass = true
            startWateringSequence()
            state.forceBypass = false
        }
    }
}

void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard data manually refreshed.")
        queueEval()
    }
    else if (btn == "startCustomRunBtn") { 
        logAction("MANUAL OVERRIDE: Starting per-zone custom run.")
        state.waitingForConfirmation = false
        safeOff(waterPromptSwitch)
        
        def zCount = settings.waterZones ? settings.waterZones.size() : (settings.manualZoneCount ? settings.manualZoneCount.toInteger() : 1)
        def customDurs = []
        
        for (int i = 1; i <= zCount; i++) {
            def keyName = "customRunTime_${i}".toString()
            def val = settings[keyName]
            
            def parsedVal = 15.0
            if (val != null && val.toString().trim() != "") {
                parsedVal = val.toString().toFloat()
            } else {
                parsedVal = settings.baseWaterTime != null ? settings.baseWaterTime.toFloat() : 15.0
            }
            
            logAction("Read Custom Time for Zone ${i}: ${parsedVal} mins")
            customDurs << parsedVal
        }
        
        state.customZoneDurations = customDurs
        startWateringSequence() 
    }
    else if (btn == "forceWaterBtn") { 
        logAction("MANUAL OVERRIDE: Forcing Standard Sequence. Bypassing manual confirmation.")
        state.customZoneDurations = []
        state.waitingForConfirmation = false
        safeOff(waterPromptSwitch)
        updateEnvironmentalData()
        
        state.forceBypass = true
        startWateringSequence() 
        state.forceBypass = false
    }
    else if (btn == "skipZoneBtn") {
        skipCurrentZone()
    }
    else if (btn == "stopWaterBtn") { 
        logAction("MANUAL OVERRIDE: Aborting Active Sequence / Prompt.")
        abortWatering("Manually stopped by user.")
    }
    else if (btn == "resetActionHistory") {
        state.actionHistory = []
    }
    else if (btn == "clearStateBtn") {
        logAction("EMERGENCY RESET: Purging history and stopping all zones.")
        state.isWatering = false
        state.currentZoneIndex = 0
        state.currentCycleIndex = 0
        state.currentZoneStartTime = null
        state.currentZoneName = null
        state.algoDiagnostics = []
        state.runHistory = [:]
        state.alertHistory = [:]
        state.lastAttemptDate = null
        state.manualAbortDate = null
        state.lastCalculatedDuration = settings.baseWaterTime
        state.weatherHoldActive = false
        state.waitingForConfirmation = false
        state.activeWeatherImpact = "None"
        state.rainDelayCount = 0
        state.stormInterceptCount = 0
        state.customZoneDurations = []
        state.soilHealth = [:]
        waterZones?.each { safeOff(it) }
        if (mainValve) safeOff(mainValve)
        safeOff(waterPromptSwitch)
        safeOff(switchStartPrompt)
        safeOff(switchZonePrompt)
        safeOff(switchCompletion)
        
        // --- BUG FIX APPLIED HERE ---
        // Unschedule all pending tasks to kill stuck loops, 
        // then call initialize() to rebuild the required daily schedules.
        unschedule()
        initialize()
    }
}

// Custom recording functionality for actual running time
def recordWateringTime(mins) {
    if (mins <= 0) return
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    def rHist = state.runHistory ? new HashMap(state.runHistory) : [:]
    rHist[todayStr] = (rHist[todayStr] ?: 0.0) + mins

    def keys = rHist.keySet().sort().reverse()
    if (keys.size() > 7) rHist = rHist.subMap(keys[0..6])
    state.runHistory = rHist
    
    state.todayWaterDuration = (state.todayWaterDuration ?: 0.0) + mins
}

def recordAlert() {
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    def aHist = state.alertHistory ? new HashMap(state.alertHistory) : [:]
    
    aHist[todayStr] = (aHist[todayStr] ?: 0) + 1
    
    def keys = aHist.keySet().sort().reverse()
    if (keys.size() > 7) aHist = aHist.subMap(keys[0..6])
    state.alertHistory = aHist
}

def skipCurrentZone() {
    if (!state.isWatering) return
    logAction("⏭️ MANUAL OVERRIDE: Skipping current zone/cycle.")
    unschedule("advanceZone")
    advanceZone()
}

def calculateVPD(tVal, rh) {
    def tC = isMetric() ? tVal : (tVal - 32.0) * (5.0 / 9.0)
    def svp = 0.61078 * Math.exp((17.27 * tC) / (tC + 237.3))
    def avp = svp * (rh / 100.0)
    return svp - avp
}

def evaluateSoilSensors() {
    if (!settings.enableSoilSensors || !settings.soilSensors) return null
    
    def data = [validSensors: 0, total: 0, min: 100.0, max: 0.0, avg: 0.0, strategyValue: 0.0, faults: []]
    def sum = 0.0
    def healthMap = state.soilHealth ?: [:]
    def currentTime = now()
    
    settings.soilSensors.each { s ->
        if (!s) return
        
        // Third Reality usually maps moisture to humidity in Hubitat natively
        def val = s.currentValue("humidity") 
        if (val == null) val = s.currentValue("moisture")
        if (val == null) val = s.currentValue("water")
        
        def floatVal = val != null ? val.toString().toFloat() : null
        def isFaulty = false
        def faultReason = ""
        
        if (floatVal == null) {
            isFaulty = true
            faultReason = "No data reported by device"
        } else {
            def sh = healthMap[s.id] ?: [lastVal: floatVal, lastChangeTime: currentTime]
            
            if (Math.abs(sh.lastVal - floatVal) > 0.5) {
                sh.lastVal = floatVal
                sh.lastChangeTime = currentTime
            }
            
            def hrsSinceChange = (currentTime - sh.lastChangeTime) / 3600000.0
            
            // Fault 1: Flatlining (Stuck) for 48 hours
            if (hrsSinceChange > 48.0) {
                isFaulty = true
                faultReason = "Sensor stuck (Unchanged for ${String.format('%.1f', hrsSinceChange)} hours)"
            }
            
            // Fault 2: Unrealistic dryness despite recent watering
            def recentlyWatered = (state.yesterdayWaterDuration ?: 0.0) > 0 || (state.todayWaterDuration ?: 0.0) > 0
            if (recentlyWatered && floatVal < 5.0) {
                isFaulty = true
                faultReason = "Unrealistic reading (Bone dry despite recent watering)"
            } 
            // Fault 3: Permanent saturation
            else if (floatVal > 99.0 && hrsSinceChange > 24.0) {
                isFaulty = true
                faultReason = "Unrealistic reading (Stuck at 100% saturation)"
            }
            
            healthMap[s.id] = sh
        }
        
        if (isFaulty) {
            data.faults << "<b>${s.displayName}</b>: ${faultReason}"
        } else {
            data.validSensors++
            sum += floatVal
            if (floatVal < data.min) data.min = floatVal
            if (floatVal > data.max) data.max = floatVal
        }
    }
    
    state.soilHealth = healthMap
    
    if (data.validSensors > 0) {
        data.avg = sum / data.validSensors
        def strat = settings.soilStrategy ?: "Average (Combined)"
        if (strat.contains("Worst")) data.strategyValue = data.min
        else if (strat.contains("Best")) data.strategyValue = data.max
        else data.strategyValue = data.avg
    } else {
        data.min = 0.0
        data.max = 0.0
    }
    
    return data
}

def updateEnvironmentalData() {
    def t = getFloat(sensorTemp, ["temperature", "tempf"], 0.0)
    def h = getFloat(sensorHum, ["humidity"], 50.0)
    def vpd = calculateVPD(t, h)
    state.currentVPD = vpd
    
    def rDaily = getFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
    
    if (t > (state.todayMaxTemp ?: 0.0)) state.todayMaxTemp = t
    if (vpd > (state.todayMaxVPD ?: 0.0)) state.todayMaxVPD = vpd
    if (rDaily > (state.todayRain ?: 0.0)) state.todayRain = rDaily
    
    calculateSmartDuration()
}

def calculateSmartDuration() {
    def base = settings.baseWaterTime != null ? settings.baseWaterTime.toFloat() : 15.0
    def diagList = []
    def confFactors = []
    def holdCondition = null
    
    // 1. DYNAMIC SEASONAL SCALING
    def month = new Date().format("MM", location.timeZone).toInteger()
    def seasonalMultiplier = 1.0
    
    if (settings.enableSeasonalScaling) {
        // Multipliers based on average evapotranspiration curve across the year
        def multipliers = [1:0.3, 2:0.4, 3:0.6, 4:0.8, 5:1.0, 6:1.0, 7:1.0, 8:1.0, 9:0.9, 10:0.6, 11:0.4, 12:0.3]
        seasonalMultiplier = multipliers[month] ?: 1.0
    }
    
    def scaledBase = base * seasonalMultiplier
    def duration = scaledBase
    
    diagList << [name: "Seasonal Scaling", status: (settings.enableSeasonalScaling ? "ACTIVE" : "OFF"), effect: "x${seasonalMultiplier}", desc: "Scaled base time from ${base}m to ${String.format('%.1f', scaledBase)}m for month ${month}."]
    base = scaledBase // Overwrite base so Rain/VPD math uses the scaled baseline
    
    def rDaily = getFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
    def t = getFloat(sensorTemp, ["temperature", "tempf"], 70.0)
    def wind = getFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], 0.0)
    def rainSkip = settings.rainCreditThreshold != null ? settings.rainCreditThreshold.toFloat() : 0.1
    
    def freezeLimit = settings.freezeThreshold != null ? settings.freezeThreshold.toFloat() : 38.0
    if (t <= freezeLimit) {
        holdCondition = "Temperature (${t}°) below freeze threshold (${freezeLimit}°)"
        diagList << [name: "Freeze Protection", status: "HOLD", effect: "ABORT", desc: holdCondition]
    } else {
        diagList << [name: "Freeze Protection", status: "ON", effect: "0m", desc: "Temperature safe (${t}°)."]
    }

    def windLimit = settings.windThreshold != null ? settings.windThreshold.toFloat() : 15.0
    if (wind > windLimit) {
        holdCondition = "Wind drift severe (${wind} > ${windLimit})"
        diagList << [name: "Wind Drift Limit", status: "HOLD", effect: "ABORT", desc: holdCondition]
    } else {
        diagList << [name: "Wind Drift Limit", status: "ON", effect: "0m", desc: "Wind drift minimal (${wind})."]
    }

    if (rDaily >= rainSkip) {
        holdCondition = "Rain accumulation (${rDaily}) exceeds skip threshold (${rainSkip})"
        diagList << [name: "Rain Credit Tracking", status: "HOLD", effect: "ABORT", desc: holdCondition]
    } else if (rDaily > 0) {
        def rainDeduct = (rDaily / rainSkip) * (base * 0.6) 
        duration -= rainDeduct
        diagList << [name: "Rain Credit Tracking", status: "ACTIVE", effect: "-${String.format('%.1f', rainDeduct)}m", desc: "Partial rain credit optimized for ${rDaily}."]
        confFactors << "Partial Rain Credit (-${String.format('%.1f', rainDeduct)}m)"
    } else {
        diagList << [name: "Rain Credit Tracking", status: "ON", effect: "0m", desc: "No recent rain recorded."]
    }
    
    if (settings.enableYesterdayLogic != false) {
        def yRain = state.yesterdayRain ?: 0.0
        def yWater = state.yesterdayWaterDuration ?: 0.0
        def yVPD = state.yesterdayMaxVPD ?: 0.0
        def liveVPD = state.currentVPD ?: 0.0 
        
        if (yWater >= (base * 0.75) && yVPD < 1.5 && liveVPD < 1.2) {
            holdCondition = "Retained deep watering from yesterday (${String.format('%.1f', yWater)}m). Live VPD is stable (${String.format('%.2f', liveVPD)})."
            diagList << [name: "Deep Watering Carryover", status: "HOLD", effect: "DEFER", desc: holdCondition]
        }
        else if (yWater > 0) {
            def wDeduct = base * 0.5 
            duration -= wDeduct
            diagList << [name: "Yesterday Watering Credit", status: "ACTIVE", effect: "-${String.format('%.1f', wDeduct)}m", desc: "Garden watered for ${String.format('%.1f', yWater)}m yesterday. Reducing today's load."]
            confFactors << "Yesterday Watered (-${String.format('%.1f', wDeduct)}m)"
        }
        else if (yRain >= (rainSkip * 2.0)) {
            holdCondition = "Saturated from yesterday's heavy rain (${yRain})"
            diagList << [name: "Yesterday Rain Carryover", status: "HOLD", effect: "ABORT", desc: holdCondition]
        }
        else if (yRain > 0) {
            def yRainDeduct = (yRain / rainSkip) * base
            if (yRainDeduct > base) yRainDeduct = base
            duration -= yRainDeduct
            diagList << [name: "Yesterday Rain Carryover", status: "ACTIVE", effect: "-${String.format('%.1f', yRainDeduct)}m", desc: "Soil retention carryover from yesterday's rain (${yRain})."]
            confFactors << "Yesterday Rain Carryover (-${String.format('%.1f', yRainDeduct)}m)"
        }
        else if (yVPD > 1.5 && rDaily < rainSkip) {
            def vpdAdd = base * 0.2
            duration += vpdAdd
            diagList << [name: "Yesterday Drought Debt", status: "ACTIVE", effect: "+${String.format('%.1f', vpdAdd)}m", desc: "High evaporation yesterday unmitigated by rain or watering."]
            confFactors << "Yesterday Drought Debt (+${String.format('%.1f', vpdAdd)}m)"
        } else {
            diagList << [name: "Historical Tracking", status: "ON", effect: "0m", desc: "Yesterday's data did not trigger a threshold adjustment."]
        }
    }

    if (settings.enableVPDLogic != false) {
        def vpd = state.currentVPD ?: 0.5
        def added = 0.0
        def msg = "Stable VPD"
        if (vpd > 1.5) { added = base * 0.25; msg = "High Evaporation Rate! Adding 25%"; confFactors << "High VPD (+${String.format('%.1f', added)}m)" }
        else if (vpd > 1.0) { added = base * 0.12; msg = "Moderate Evaporation. Adding 12%"; confFactors << "Moderate VPD (+${String.format('%.1f', added)}m)" }
        else if (vpd < 0.3) { added = -(base * 0.25); msg = "Low Evaporation (Ground saturated). Reducing 25%"; confFactors << "Low VPD (${String.format('%.1f', added)}m)" }
        
        duration += added
        diagList << [name: "Live VPD Tracking", status: "ACTIVE", effect: added > 0 ? "+${String.format('%.1f', added)}m" : "${String.format('%.1f', added)}m", desc: msg]
    } else {
        diagList << [name: "Live VPD Tracking", status: "OFF", effect: "--", desc: "Disabled in settings"]
    }
    
    // NEW SOIL MOISTURE SENSOR LOGIC OVERRIDE
    def soilData = evaluateSoilSensors()
    if (soilData && soilData.validSensors > 0) {
        def sValue = soilData.strategyValue
        def wetThresh = settings.soilWetThreshold != null ? settings.soilWetThreshold.toFloat() : 60.0
        def dryThresh = settings.soilDryThreshold != null ? settings.soilDryThreshold.toFloat() : 25.0
        
        if (sValue >= wetThresh) {
            holdCondition = "Soil network is adequately saturated (${String.format('%.1f', sValue)}% via ${settings.soilStrategy})"
            diagList << [name: "Soil Moisture Network", status: "HOLD", effect: "ABORT", desc: holdCondition]
        } else if (sValue <= dryThresh) {
            def boost = base * 0.25
            duration += boost
            diagList << [name: "Soil Moisture Network", status: "ACTIVE", effect: "+${String.format('%.1f', boost)}m", desc: "Soil extremely dry (${String.format('%.1f', sValue)}%). Boosting time."]
            confFactors << "Bone Dry Soil (+${String.format('%.1f', boost)}m)"
        } else {
            diagList << [name: "Soil Moisture Network", status: "ON", effect: "0m", desc: "Moisture at ${String.format('%.1f', sValue)}% (Optimal Range)."]
        }
    } else if (soilData && soilData.faults.size() > 0 && soilData.validSensors == 0) {
         diagList << [name: "Soil Moisture Network", status: "ERR", effect: "0m", desc: "All sensors failed or stuck. Ignored for math."]
    }

    def minT = settings.minWaterTime != null ? settings.minWaterTime.toFloat() : 5.0
    def maxT = settings.maxWaterTime != null ? settings.maxWaterTime.toFloat() : 45.0
    
    if (duration < minT) {
        diagList << [name: "Minimum Time Bounds", status: "ACTIVE", effect: "Clamp", desc: "Duration (${String.format('%.1f', duration)}m) hit minimum limit. Clamped to ${minT}m."]
        duration = minT
    } else if (duration > maxT) {
        diagList << [name: "Maximum Time Bounds", status: "ACTIVE", effect: "Clamp", desc: "Duration (${String.format('%.1f', duration)}m) hit maximum limit. Clamped to ${maxT}m."]
        duration = maxT
    }

    state.algoDiagnostics = diagList
    state.weatherHoldActive = (holdCondition != null)

    if (holdCondition) {
        state.confidenceReasoning = "Sequence prevented. " + holdCondition
        state.lastCalculatedDuration = 0
        state.activeWeatherImpact = "Sequence Aborted"
        return [allow: false, duration: 0, reason: holdCondition]
    } else {
        def impactStr = confFactors.size() > 0 ? confFactors.join(", ") : "Standard Schedule Applied"
        state.confidenceReasoning = "Sequence validated. Time adjusted by: " + impactStr
        state.activeWeatherImpact = impactStr
        state.lastCalculatedDuration = Math.round(duration)
        return [allow: true, duration: Math.round(duration), reason: "Clear"]
    }
}

def evaluateAndRunSchedule() {
    logAction("Scheduled evaluation triggered.")
    
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    
    if (state.isWatering) {
        logAction("Skipping schedule: Garden is actively being watered right now.")
        return
    }
    
    if (state.lastWateredTime) {
        def lastWaterDay = new Date(state.lastWateredTime as Long).format("yyyy-MM-dd", location.timeZone)
        if (lastWaterDay == todayStr) {
            logAction("Skipping schedule: Garden was already watered today.")
            return
        }
    }

    if (state.manualAbortDate == todayStr) {
        logAction("Skipping schedule: User manually aborted today. Pushing to next available day.")
        return
    }
    
    def dayOfWeek = new Date().format("EEEE", location.timeZone)
    if (settings.waterDays && !settings.waterDays.contains(dayOfWeek)) {
        logInfo("Today (${dayOfWeek}) is not a scheduled watering day. Skipping.")
        return
    }

    // 2. PREDICTIVE RAIN SKIPPING INTERCEPT
    def isRainExpected = (forecastRainSwitch?.currentValue("switch") == "on")
    def isStormExpected = (forecastStormSwitch?.currentValue("switch") == "on")
    
    if (settings.enablePredictiveSkipping && (isRainExpected || isStormExpected) && (state.stormInterceptCount ?: 0) == 0) {
        state.stormInterceptCount = 1
        recordAlert()
        logAction("🌦️ PREDICTIVE SKIP: Rain or Thunderstorm expected soon. Aborting immediate schedule and deferring evaluation by 4 hours to harvest free water.")
        sendAlert("🌦️ Garden Update: Watering paused. A storm is forecasted soon. The system will re-evaluate in 4 hours to see if Mother Nature did the work.")
        
        // Defer schedule by 4 hours (14400 seconds)
        runIn(14400, "evaluateAndRunSchedule")
        return
    }

    def rRate = getFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate"], 0.0)
    if (settings.enableRainDelay != false && rRate > 0.02) {
        def maxDelays = 4
        state.rainDelayCount = (state.rainDelayCount ?: 0) + 1
        
        if (state.rainDelayCount <= maxDelays) {
            logAction("🌧️ Active rain detected (${rRate}/hr) at scheduled time. Delaying watering prompt by 30 minutes (Attempt ${state.rainDelayCount} of ${maxDelays}).")
            recordAlert()
            runIn(1800, "evaluateAndRunSchedule")
            return
        } else {
            logAction("🌧️ Maximum rain delays reached (2 hours). Aborting schedule for today.")
            state.lastAttemptDate = todayStr
            recordAlert()
            sendAlert("🌱 Garden Warning: Watering skipped for today due to prolonged active rain.")
            return
        }
    }

    def calc = calculateSmartDuration()
    state.rainDelayCount = 0 
    
    if (!calc.allow) {
        logAction("Watering scheduled but aborted by smart engine: ${calc.reason}")
        recordAlert()
        sendAlert("🌱 Garden Warning: Scheduled watering skipped. ${calc.reason}")
        return
    }

    logAction("Schedule validated. Waiting for manual water connection confirmation. Duration per zone will be: ${calc.duration} minutes.")
    state.waitingForConfirmation = true
    
    if (waterPromptSwitch) {
        safeOn(waterPromptSwitch)
        runIn(600, "turnOffPromptSwitch")
    }
    
    // Customized Smart Alert based on Predictive Intercept status
    def isRecovery = (state.stormInterceptCount ?: 0) > 0
    def alertMsg = isRecovery ? 
        "💦 UPDATE: The forecasted storm missed us or underperformed. Watering is still required for ${calc.duration} mins per zone. Please press confirmation button to begin." : 
        "💦 ACTION REQUIRED: The garden is scheduled for watering! Duration: ${calc.duration} mins per zone. Please ensure your hose is connected and press the confirmation button to begin."
    
    sendAlert(alertMsg)
}

def turnOffPromptSwitch() {
    logAction("10-minute prompt timer expired. Turning off main prompt switch.")
    safeOff(waterPromptSwitch)
}

def turnOffStartPromptSwitch() {
    logAction("10-minute start timer expired. Turning off virtual start switch.")
    safeOff(switchStartPrompt)
}

def turnOffZonePromptSwitch() {
    logAction("10-minute zone prompt timer expired. Turning off zone prompt switch.")
    safeOff(switchZonePrompt)
}

def resetConfirmation() {
    if (state.waitingForConfirmation) {
        logAction("Midnight rollover: User never confirmed water connection. Resetting pending status.")
        state.waitingForConfirmation = false
        safeOff(waterPromptSwitch)
    }
    
    state.lastAttemptDate = null
    state.manualAbortDate = null
    state.rainDelayCount = 0
    state.stormInterceptCount = 0
    state.customZoneDurations = []
    
    logAction("Midnight rollover: Saving today's environmental variables into historical memory.")
    state.yesterdayRain = state.todayRain ?: 0.0
    state.yesterdayMaxTemp = state.todayMaxTemp ?: 0.0
    state.yesterdayMaxVPD = state.todayMaxVPD ?: 0.0
    state.yesterdayWaterDuration = state.todayWaterDuration ?: 0.0
    
    state.todayRain = 0.0
    state.todayMaxTemp = 0.0
    state.todayMaxVPD = 0.0
    state.todayWaterDuration = 0.0
}

def watchdogFailsafe() {
    logAction("CRITICAL: Watchdog Failsafe Timer Fired!")
    abortWatering("Sequence stalled or exceeded maximum safety runtime limits. Emergency hard abort triggered.")
}

def startWateringSequence() {
    def zoneCount = settings.waterZones ? settings.waterZones.size() : (settings.manualZoneCount ? settings.manualZoneCount.toInteger() : 1)
    
    if (zoneCount == 0) {
        logAction("Error: No zones configured (valves or manual).")
        return
    }
    
    if (state.isWatering) {
        logAction("Sequence already running.")
        return
    }

    def isCustom = (state.customZoneDurations != null && state.customZoneDurations.size() > 0)
    
    if (isCustom) {
        state.isWatering = true
        state.currentZoneIndex = 0
        state.currentCycleIndex = 0
        state.totalCycles = 1 
        
        state.activeCycleDuration = state.customZoneDurations[0]
        state.activeDuration = state.customZoneDurations[0]
        
        def totalExpectedMins = state.customZoneDurations.sum() + 30.0
        runIn(Math.round(totalExpectedMins * 60).toInteger(), "watchdogFailsafe")
        
        def modeStr = settings.waterZones ? "Automated" : "Manual Notification"
        logAction("Initiating Custom [${modeStr}] Sequence. Zones: ${zoneCount}. Total Time: ${state.customZoneDurations.sum()}m.")
        
        if (switchStartPrompt) {
            safeOff(switchStartPrompt)
            safeOn(switchStartPrompt)
            runIn(600, "turnOffStartPromptSwitch")
        }
        
        sendAlert("🚀 Custom Garden Watering Started. Using manually defined zone times.")
        
        if (mainValve) safeOn(mainValve)
        waterNextZone()
        
    } else {
        def calc = calculateSmartDuration()
        if (!calc.allow && !state.forceBypass) {
             logAction("Cannot start sequence: ${calc.reason}")
             sendAlert("🛑 Watering Aborted at confirmation: ${calc.reason}")
             return
        }
        def duration = calc.duration > 0 ? calc.duration : (settings.baseWaterTime ?: 15)
        
        def maxCycle = settings.maxCycleTime != null ? settings.maxCycleTime.toFloat() : 15.0
        if (settings.enableCycleSoak && duration > maxCycle) {
            state.totalCycles = Math.ceil(duration / maxCycle).toInteger()
            state.activeCycleDuration = duration / state.totalCycles
            logAction("Cycle & Soak Active: ${duration}m split into ${state.totalCycles} cycles of ${String.format('%.1f', state.activeCycleDuration)}m.")
        } else {
            state.totalCycles = 1
            state.activeCycleDuration = duration
        }
    
        state.isWatering = true
        state.currentZoneIndex = 0
        state.currentCycleIndex = 0
        state.activeDuration = duration
        
        def totalExpectedMins = (zoneCount * state.totalCycles * state.activeCycleDuration) + 30.0
        runIn(Math.round(totalExpectedMins * 60).toInteger(), "watchdogFailsafe")
        
        def modeStr = settings.waterZones ? "Automated" : "Manual Notification"
        logAction("Initiating [${modeStr}] Zone Sequence. Zones: ${zoneCount}, Total Time/Zone: ${duration}m.")
        
        if (switchStartPrompt) {
            safeOff(switchStartPrompt)
            safeOn(switchStartPrompt)
            runIn(600, "turnOffStartPromptSwitch")
        }
        
        sendAlert("🚀 Garden Watering Sequence Started. Expected duration: ${duration} mins per zone.")
        
        if (mainValve) safeOn(mainValve)
        waterNextZone()
    }
}

// 3. MAKE-BEFORE-BREAK VALVE SWITCHING
def waterNextZone() {
    if (!state.isWatering) return
    
    def isManualMode = (settings.waterZones == null || settings.waterZones.size() == 0)
    def zoneCount = isManualMode ? (settings.manualZoneCount ? settings.manualZoneCount.toInteger() : 1) : settings.waterZones.size()
    
    if (state.currentZoneIndex >= zoneCount) {
        finishWateringSequence()
        return
    }
    
    def dur = 15.0
    def isCustom = (state.customZoneDurations != null && state.customZoneDurations.size() > 0)
    
    if (isCustom && state.customZoneDurations.size() > state.currentZoneIndex) {
        dur = state.customZoneDurations[state.currentZoneIndex].toFloat()
        state.activeCycleDuration = dur 
        state.activeDuration = dur
    } else {
        dur = (state.activeCycleDuration ?: 15).toFloat()
    }
    
    state.currentZoneStartTime = now()
    
    if (isManualMode && (state.currentZoneIndex > 0 || state.currentCycleIndex > 0)) {
        if (switchZonePrompt) {
            logAction("Activating Virtual Zone Swap Prompt Switch for 10 minutes.")
            safeOff(switchZonePrompt) 
            safeOn(switchZonePrompt)
            runIn(600, "turnOffZonePromptSwitch")
        }
    }
    
    def cycleStr = (state.totalCycles > 1) ? " (Cycle ${state.currentCycleIndex + 1} of ${state.totalCycles})" : ""
    
    if (isManualMode) {
        state.currentZoneName = "Manual Zone ${state.currentZoneIndex + 1}"
        
        if (state.currentZoneIndex == 0 && state.currentCycleIndex == 0) {
            logAction("Zone 1 Started for ${String.format('%.1f', dur)} minutes${cycleStr}.")
        } else {
            logAction("Notifying user to switch to ${state.currentZoneName} for ${String.format('%.1f', dur)} minutes${cycleStr}.")
            sendAlert("🔄 ZONE SWAP REQUIRED: Please move your hose/sprinkler to ${state.currentZoneName} for ${String.format('%.1f', dur)} minutes${cycleStr}.")
        }
        
    } else {
        // Smart Valve Make-Before-Break Logic
        def currentZone = settings.waterZones[state.currentZoneIndex]
        state.currentZoneName = currentZone.displayName
        
        // Find the previously active zone for safe turn-off
        def prevZone = null
        if (state.currentZoneIndex > 0) {
            prevZone = settings.waterZones[state.currentZoneIndex - 1]
        } else if (state.currentCycleIndex > 0) {
            prevZone = settings.waterZones[settings.waterZones.size() - 1]
        }
        
        logAction("Turning ON Zone ${state.currentZoneIndex + 1}: ${state.currentZoneName} for ${String.format('%.1f', dur)} minutes${cycleStr}.")
        safeOn(currentZone)
        
        if (settings.enableMakeBeforeBreak && prevZone && prevZone.id != currentZone.id) {
            logAction("Make-Before-Break: Opening ${currentZone.displayName} and waiting 8 seconds before closing ${prevZone.displayName} to prevent water hammer.")
            runIn(8, "turnOffSpecificZone", [data: [zoneId: prevZone.id], overwrite: false])
        } else {
            // Standard shutoff for first zone or if feature is disabled
            settings.waterZones.each { if (it.id != currentZone.id) safeOff(it) }
        }
    }
    
    runIn(Math.round(dur * 60).toInteger(), "advanceZone")
}

def turnOffSpecificZone(data) {
    def z = settings.waterZones.find { it.id == data.zoneId }
    if (z) {
        safeOff(z)
        logDebug("Make-Before-Break: Safely closed ${z.displayName}.")
    }
}

def advanceZone() {
    if (!state.isWatering) return
    logAction("Zone ${state.currentZoneIndex + 1} (Cycle ${state.currentCycleIndex + 1}) complete.")
    
    if (state.currentZoneStartTime) {
        def ranMins = (now() - state.currentZoneStartTime) / 60000.0
        recordWateringTime(ranMins)
        state.currentZoneStartTime = null 
    }
    
    state.currentZoneIndex++
    
    def isManualMode = (settings.waterZones == null || settings.waterZones.size() == 0)
    def zoneCount = isManualMode ? (settings.manualZoneCount ? settings.manualZoneCount.toInteger() : 1) : settings.waterZones.size()
    
    if (state.currentZoneIndex >= zoneCount) {
        state.currentZoneIndex = 0
        state.currentCycleIndex++
        
        if (state.currentCycleIndex >= (state.totalCycles ?: 1)) {
            finishWateringSequence()
            return
        } else {
            logAction("Cycle ${state.currentCycleIndex} complete. Looping back to Zone 1 for Cycle ${state.currentCycleIndex + 1}.")
        }
    }
    
    waterNextZone()
}

def finishWateringSequence() {
    logAction("Watering sequence completely finished.")
    state.isWatering = false
    state.currentZoneIndex = 0
    state.currentCycleIndex = 0
    state.currentZoneStartTime = null
    state.currentZoneName = null
    state.customZoneDurations = []
    state.lastWateredTime = now()
    
    unschedule("watchdogFailsafe")
    
    def isManualMode = (settings.waterZones == null || settings.waterZones.size() == 0)
    
    if (!isManualMode) {
        settings.waterZones?.each { safeOff(it) }
    }
    
    if (mainValve) safeOff(mainValve)
    
    sendAlert("✅ Garden Watering Complete. All zones finished. Please TURN OFF the water supply if manually connected.")
    
    if (switchZonePrompt) {
        safeOff(switchZonePrompt)
        unschedule("turnOffZonePromptSwitch")
    }
    
    if (switchCompletion) {
        logAction("Activating Virtual Completion Switch for 10 minutes.")
        safeOn(switchCompletion)
        runIn(600, "turnOffCompletionSwitch")
    }
}

def abortWatering(reason) {
    state.lastAttemptDate = new Date().format("yyyy-MM-dd", location.timeZone)
    if (reason.contains("Manually")) {
        state.manualAbortDate = state.lastAttemptDate
    }
    recordAlert()
    
    if (state.waitingForConfirmation) {
        state.waitingForConfirmation = false
        state.customZoneDurations = []
        safeOff(waterPromptSwitch)
        unschedule("turnOffPromptSwitch")
        logAction("Prompt aborted by user before sequence started.")
        return
    }

    if (!state.isWatering) return
    
    logAction("🛑 WATERING ABORTED: ${reason}")
    state.isWatering = false
    
    if (state.currentZoneStartTime) {
        def ranMins = (now() - state.currentZoneStartTime) / 60000.0
        recordWateringTime(ranMins)
        state.currentZoneStartTime = null
    }
    
    state.currentZoneName = null
    state.customZoneDurations = []
    
    unschedule("advanceZone")
    unschedule("watchdogFailsafe")
    
    if (settings.waterZones) {
        settings.waterZones?.each { safeOff(it) }
    }
    
    if (mainValve) safeOff(mainValve)
    
    if (switchStartPrompt) {
        safeOff(switchStartPrompt)
        unschedule("turnOffStartPromptSwitch")
    }
    if (switchZonePrompt) {
        safeOff(switchZonePrompt)
        unschedule("turnOffZonePromptSwitch")
    }
    
    sendAlert("🛑 Garden Watering Aborted! Reason: ${reason} - PLEASE TURN OFF WATER IMMEDIATELY if manually connected.")
}

def turnOffCompletionSwitch() {
    logAction("Deactivating Virtual Completion Switch.")
    safeOff(switchCompletion)
}

def safeOn(dev) {
    if (dev && dev.currentValue("switch") != "on" && dev.currentValue("valve") != "open") {
        try { 
            if (dev.hasCommand("open")) dev.open()
            else dev.on() 
        } catch (e) { log.error "Failed to turn ON ${dev.displayName}: ${e.message}" }
    }
}

def safeOff(dev) {
    if (dev && dev.currentValue("switch") != "off" && dev.currentValue("valve") != "closed") {
        try { 
            if (dev.hasCommand("close")) dev.close()
            else dev.off() 
        } catch (e) { log.error "Failed to turn OFF ${dev.displayName}: ${e.message}" }
    }
}

def sendAlert(msg) {
    if (notifyDevices) {
        notifyDevices.each { it.deviceNotification(msg) }
        logAction("📣 Notification Sent: ${msg}")
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

def logDebug(msg) {
    if (debugEnable) log.debug "${app.label}: ${msg}"
}
