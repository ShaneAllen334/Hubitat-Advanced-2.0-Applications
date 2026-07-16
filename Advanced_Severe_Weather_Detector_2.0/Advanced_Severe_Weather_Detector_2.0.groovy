/**
 * Advanced Severe Weather Detector 2.0
 */ 

definition(
    name: "Advanced Severe Weather Detector 2.0",
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

def renderTableHTML() {
    if (!state.tempHistory || state.tempHistory.size() == 0) {
        return "<h4 style='margin:0 0 10px 0; border-bottom:1px solid #ccc; padding-bottom:5px; color:#333;'>24-Hour Timeline</h4><div style='padding:10px; background:#f9f9f9; border:1px solid #ddd;'>Gathering history data... waiting for first sensor reports.</div>"
    }
    
    def tableHTML = """
    <h4 style="margin:0 0 10px 0; border-bottom:1px solid #ccc; padding-bottom:5px; color:#333; margin-top:20px;">24-Hour Local Data Table</h4>
    <div style="max-height: 350px; overflow-y: auto; border: 1px solid #eee; margin-bottom: 20px;">
        <table class="dash-table" style="margin-top:0;">
            <thead style="position: sticky; top: 0; box-shadow: 0 1px 2px rgba(0,0,0,0.1);">
                <tr>
                    <th>Time</th>
                    <th>Temp</th>
                    <th>Pressure</th>
                    <th>DP Spread</th>
                    <th>Max Hazard</th>
                </tr>
            </thead>
            <tbody>
    """
    
    def reversedHist = []
    state.tempHistory.each { reversedHist.add(it) }
    reversedHist = reversedHist.reverse()
    
    reversedHist.each { entry ->
        def entryTime = entry.time as Long
        def timeStr = new Date(entryTime).format("MM/dd HH:mm", location.timeZone)
        def tVal = String.format('%.1f', entry.value as Float)
        
        def findMatch = { hist, tTime -> 
            def match = hist?.find { Math.abs((it.time as Long) - tTime) < 120000 }
            return match ? String.format('%.2f', match.value as Float) : "--"
        }
        
        def pVal = findMatch(state.pressureHistory, entryTime)
        def sVal = findMatch(state.spreadHistory, entryTime)
        
        def probMatch = state.probHistory?.find { Math.abs((it.time as Long) - entryTime) < 120000 }
        def probVal = probMatch ? "${Math.round(probMatch.value as Float)}%" : "--"
        def pColor = probMatch && (probMatch.value as Float) > 50 ? 'red' : 'inherit'
        
        tableHTML += """
            <tr>
                <td style="color: #555;">${timeStr}</td>
                <td>${tVal}°</td>
                <td>${pVal}</td>
                <td>${sVal != "--" ? sVal + "°" : "--"}</td>
                <td style="font-weight: bold; color: ${pColor};">${probVal}</td>
            </tr>
        """
    }
    
    tableHTML += """
            </tbody>
        </table>
    </div>
    """
    return tableHTML
}

String getHumanReadableStatus() {
    def isStale = state.isStale ?: false
    if (isStale) return "<span style='color:red; font-size:14px;'><b>🚨 CRITICAL: SENSORS OFFLINE. Prediction Engine Halted. 🚨</b></span>"
    if (state.hardwareAnomalyActive) return "<span style='color:orange; font-size:14px;'><b>⚠ HARDWARE ANOMALY: Physically impossible sensor data rejected.</b></span>"
    
    def torState = state.tornadoState ?: "Clear"
    def tsState = state.tstormState ?: "Clear"
    def floodState = state.floodState ?: "Clear"
    def heatState = state.heatState ?: "Clear"
    
    if (torState == "WARNING") return "<span style='color:red;'><b>🌪️ TORNADO WARNING ACTIVE.</b></span> Destructive kinetic shear or pressure plunges detected."
    if (torState == "WATCH") return "<span style='color:orange;'><b>🌪️ TORNADO WATCH.</b></span> Elevated probability of extreme shear."
    if (tsState == "WARNING") return "<span style='color:red;'><b>⛈️ SEVERE THUNDERSTORM WARNING ACTIVE.</b></span> Destructive microbursts or severe lightning detected."
    if (tsState == "WATCH") return "<span style='color:orange;'><b>⛈️ SEVERE THUNDERSTORM WATCH.</b></span> Elevated probability of convection."
    if (floodState == "WARNING") return "<span style='color:red;'><b>🌊 FLASH FLOOD WARNING ACTIVE.</b></span> Critical rain rates on saturated ground."
    if (floodState == "WATCH") return "<span style='color:orange;'><b>🌊 FLASH FLOOD WATCH.</b></span> Elevated probability of flooding."
    if (heatState == "WARNING") return "<span style='color:red;'><b>🔥 SEVERE HEAT WARNING ACTIVE.</b></span> Dangerous apparent temperatures detected."
    if (heatState == "WATCH") return "<span style='color:orange;'><b>🔥 HEAT ADVISORY WATCH.</b></span> Elevated temperature and humidity levels."
    
    return "<span style='color:green;'><b>Tracking Stable Conditions.</b></span> The environment is currently clear."
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            if (sensorTemp && sensorHum && sensorPress) {
                def tP = getFloat(sensorTemp, ["temperature", "tempf"])
                def hP = getFloat(sensorHum, ["humidity"])
                def pP = getFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"])
                if (pP != null) pP += (settings.pressOffset ?: 0.0)
                
                def t = tP ?: 0.0
                def h = hP ?: 0.0
                def p = pP ?: 0.0

                if (settings.enableThermalSmoothing != false && state.smoothedTemp != null) {
                    t = state.smoothedTemp
                }

                def r = getFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], 0.0)
                
                def lux = "N/A"
                if (sensorLux) {
                    if (sensorLux.currentValue("illuminance") != null) {
                        lux = getFloat(sensorLux, ["illuminance"], 0.0)
                    } else if (sensorLux.currentValue("solarRadiation") != null || sensorLux.currentValue("solarradiation") != null) {
                        def rad = getFloat(sensorLux, ["solarRadiation", "solarradiation"], 0.0)
                        lux = rad * 126.7
                    }
                }
                
                def wind = getFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], "N/A")
                def windDir = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], "N/A")
       
                def strikes = state.recentStrikes ?: 0
                def recentLightDist = 999.0
                if (state.lightningHistory?.size() > 0) {
                    state.lightningHistory.each { if (it.value < recentLightDist) recentLightDist = it.value }
                }
                def recentLightDistStr = state.lightningHistory?.size() > 0 ? recentLightDist : "N/A"
                def lightVector = state.lightningVectorStr ?: "Gathering Data"
                
                def vpd = state.currentVPD ?: 0.0
                def ah = state.currentAH ?: 0.0
                def dp = state.currentDewPoint ?: 0.0
                def wb = state.currentWetBulb ?: 0.0
                def dpSpread = state.dewPointSpread ?: 0.0
                def pTrend = state.pressureTrendStr ?: "Stable"
                def tTrend = state.tempTrendStr ?: "Stable"
                def sTrend = state.spreadTrendStr ?: "Stable"
                def wTrend = state.windTrendStr ?: "Stable"
                def ahTrend = state.ahTrendStr ?: "Stable"
         
                def torProb = state.tornadoProb ?: 0
                def tsProb = state.tstormProb ?: 0
                def floodProb = state.floodProb ?: 0
                def heatProb = state.heatProb ?: 0
                
                def tsMultStr = state.ts_ml_mult ? String.format('%.2f', state.ts_ml_mult) : "1.00"
                def floodMultStr = state.flood_ml_mult ? String.format('%.2f', state.flood_ml_mult) : "1.00"
                def mlDesc = "Active [TS Multiplier: x${tsMultStr} | Flood Multiplier: x${floodMultStr}]"
                
                def gravWave = state.gravityWaveActive ? "<span style='color:red; font-weight:bold;'>DETECTED (Turbulence)</span>" : "Calm"
                def windLoadVal = state.currentWindLoad ?: 0.0
                def windLoadUnit = isMetric() ? "Pascals (Pa)" : "lb/ft² (PSF)"
                def wlOrange = isMetric() ? 478.0 : 10.0
                def wlRed = isMetric() ? 957.0 : 20.0
                def loadColor = windLoadVal > wlRed ? "red" : (windLoadVal > wlOrange ? "orange" : "black")

                def speedUnit = isMetric() ? "km/h" : "mph"
                def distUnit = isMetric() ? "km" : "mi"
                
                def lightStr = "None Recent"
                if (sensorLightning && strikes > 0) {
                    def vecColor = lightVector.contains("Approaching") ? "red" : (lightVector.contains("Departing") ? "green" : "orange")
                    lightStr = "${strikes} strikes (3hr) | Closest: ${recentLightDistStr} ${distUnit} | <span style='color:${vecColor}; font-weight:bold;'>${lightVector}</span>"
                }
                
                def wetCountRaw = [sensorLeak, sensorLeak2, sensorLeak3].count { it?.currentValue("water") == "wet" }
                def reqWets = settings.leakSensorRequiredCount ? settings.leakSensorRequiredCount.toInteger() : 2
                def rawLeakWet = (wetCountRaw >= reqWets)
       
                def leakWetStr = "DRY"
                if (sensorLeak || sensorLeak2 || sensorLeak3) {
                    if (state.dewRejectionActive) leakWetStr = "<span style='color:orange; font-weight:bold;'>DEW/IGNORED</span>"
                    else if (state.stuckLeakActive && rawLeakWet) leakWetStr = "<span style='color:orange; font-weight:bold;'>STUCK/IGNORED</span>"
                    else if (rawLeakWet) leakWetStr = "<span style='color:blue; font-weight:bold;'>WET</span>"
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
                    <thead><tr><th>Metric</th><th>Current Value</th><th>Calculated Trend / Status</th></tr></thead>
                    <tbody>
                        <tr><td colspan="3" class="dash-subhead">Severe Hazard Probabilities</td></tr>
                        <tr><td class="dash-hl">🌪️ Tornado / Shear</td><td colspan="2" class="dash-val"><span style='color:${torProb > 70 ? "red" : (torProb > 40 ? "orange" : "black")}; font-weight:bold;'>${torProb}%</span> <span style="font-size:11px; color:gray;">(Bypasses ML/Verification Gates)</span></td></tr>
                        <tr><td class="dash-hl">⛈️ Severe Thunderstorm</td><td colspan="2" class="dash-val"><span style='color:${tsProb > 70 ? "red" : (tsProb > 40 ? "orange" : "black")}; font-weight:bold;'>${tsProb}%</span></td></tr>
                        <tr><td class="dash-hl">🌊 Flash Flood</td><td colspan="2" class="dash-val"><span style='color:${floodProb > 70 ? "red" : (floodProb > 40 ? "orange" : "black")}; font-weight:bold;'>${floodProb}%</span></td></tr>
                        <tr><td class="dash-hl">🔥 Severe Heat</td><td colspan="2" class="dash-val"><span style='color:${heatProb > 70 ? "red" : (heatProb > 40 ? "orange" : "black")}; font-weight:bold;'>${heatProb}%</span></td></tr>
                        
                        <tr><td class="dash-hl">ML Engine Status</td><td colspan="2" class="dash-val" style="font-size:12px;">${settings.enableMLTuning ? mlDesc : "Disabled"}</td></tr>

                        <tr><td colspan="3" class="dash-subhead">Core Environmental Sensors</td></tr>
                        <tr><td class="dash-hl">Temperature</td><td><b>${String.format('%.1f', t)}°</b></td><td>${tTrend}</td></tr>
                        <tr><td class="dash-hl">Barometric Pressure</td><td><b>${String.format('%.2f', p)}</b></td><td>${pTrend}</td></tr>
                """
                
                if (settings.pressureSensorLocation == "Indoors") {
                    def anomalyDisplay = (state.indoorAnomalyStr && state.indoorAnomalyStr != "None") ? "<span style='color:orange; font-weight:bold;'>Interference Active:</span> ${state.indoorAnomalyStr}" : "Clear (No Interference)"
                    dashHTML += """<tr><td class="dash-hl" style="padding-left:30px !important; font-size: 12px; color: #666;">↳ Indoor Comp.</td><td colspan="2" class="dash-val" style="font-size: 12px;">${anomalyDisplay}</td></tr>"""
                }

                dashHTML += """
                        <tr><td class="dash-hl">Rain Gauge Rate</td><td><b>${r}/hr</b></td><td>Instant Drop Sensor: <b>${leakWetStr}</b></td></tr>
                        <tr><td class="dash-hl">Solar Radiation</td><td colspan="2" class="dash-val">${lux != "N/A" ? lux + " lux" : "--"}</td></tr>
                        <tr><td class="dash-hl">Wind Dynamics</td><td>${wind != "N/A" ? wind + " " + speedUnit : "--"} @ ${windDir != "N/A" ? windDir + "°" : "--"}</td><td>Shift: ${state.windShiftDetected ? "<span style='color:red; font-weight:bold;'>Active Shear</span>" : "Stable"}</td></tr>
                        <tr><td class="dash-hl">Lightning Vectoring</td><td colspan="2" class="dash-val">${lightStr}</td></tr>

                        <tr><td colspan="3" class="dash-subhead">Thermodynamic & Kinetic Calculations</td></tr>
                        <tr><td class="dash-hl">Dew Point Spread</td><td><b>${String.format('%.1f', dpSpread)}°</b></td><td>Convergence: ${sTrend}</td></tr>
                        <tr><td class="dash-hl">Absolute Humidity</td><td><b>${String.format('%.2f', ah)} g/m³</b></td><td>Advection Trend: ${ahTrend}</td></tr>
                        <tr><td class="dash-hl">Atmospheric Gravity Waves</td><td colspan="2" class="dash-val">${gravWave}</td></tr>
                        <tr><td class="dash-hl">Structural Wind Load</td><td colspan="2" class="dash-val"><span style='color:${loadColor}; font-weight:bold;'>${String.format('%.2f', windLoadVal)} ${windLoadUnit}</span></td></tr>
                        
                        <tr><td colspan="3" class="dash-subhead">Event Accumulation</td></tr>
                        <tr><td class="dash-hl">Today's Total Rain</td><td colspan="2" class="dash-val">${state.currentDayRain ?: 0.0}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML

                // === ALGORITHM DECISION MATRIX ===
                def logicPanel = "<div style='margin-top: 20px; padding: 15px; background: #fff3ed; border-left: 5px solid #d9534f; font-size: 13px; color: #843534;'>"
                logicPanel += "<h4 style='margin-top:0; border-bottom:1px solid #ebccd1; padding-bottom:5px;'>Engine Diagnostics: Severe Algorithm Matrix</h4>"
                logicPanel += "<div style='max-height: 400px; overflow-y: auto; border: 1px solid #ebccd1;'><table class='dash-table' style='margin-top:0; background: white; color: #333;'><thead style='position: sticky; top: 0; box-shadow: 0 1px 2px rgba(0,0,0,0.1);'><tr><th>Algorithm</th><th>Status</th><th>Effect</th><th>Diagnostic Output</th></tr></thead><tbody>"
                
                if (state.algoDiagnostics && state.algoDiagnostics.size() > 0) {
                    state.algoDiagnostics.each { diag ->
                        def eff = diag.effect ?: "0%"
                        def effColor = eff.contains("+") || eff.startsWith("x1.") || eff.startsWith("x2.") ? "red" : (eff.contains("-") || (eff.startsWith("x0.")) ? "green" : (eff == "0%" ? "gray" : "black"))
                        def statusColor = diag.status == "ON" ? "green" : (diag.status == "ACTIVE" ? "red" : (diag.status == "IGNORED" ? "gray" : "gray"))
                        logicPanel += "<tr><td style='font-weight:bold;'>${diag.name}</td><td style='color:${statusColor};'>${diag.status}</td><td style='color:${effColor}; font-weight:bold;'>${eff}</td><td>${diag.desc}</td></tr>"
                    }
                } else {
                    logicPanel += "<tr><td colspan='4'>Waiting for initial evaluation...</td></tr>"
                }
                
                logicPanel += "</tbody></table></div>"
                logicPanel += "<div style='margin-top:10px;'><b>Threat Reasoning:</b> " + (state.logicReasoning ?: "Waiting for consensus...") + "</div>"
                logicPanel += "</div>"

                paragraph logicPanel

                def visualWidgets = ""
                def dispMode = settings.historyDisplayMode ?: "Data Table"
                if (dispMode == "Data Table") {
                    visualWidgets += renderTableHTML()
                }
                paragraph visualWidgets
                
                def tWatch = switchTornadoWatch?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def tWarning = switchTornadoWarning?.currentValue("switch") == "on" ? "<span style='color:red; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                
                def tsWatch = switchThunderstormWatch?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def tsWarning = switchThunderstormWarning?.currentValue("switch") == "on" ? "<span style='color:red; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                
                def fWatch = switchFloodWatch?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def fWarning = switchFloodWarning?.currentValue("switch") == "on" ? "<span style='color:red; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"

                def hWatch = switchHeatWatch?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def hWarning = switchHeatWarning?.currentValue("switch") == "on" ? "<span style='color:red; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                
                paragraph "<div style='padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>" +
                          "<b>Virtual Outputs:</b> Tornado (Watch:[${tWatch}] Warning:[${tWarning}]) | T-Storm (Watch:[${tsWatch}] Warning:[${tsWarning}]) | Flood (Watch:[${fWatch}] Warning:[${fWarning}]) | Heat (Watch:[${hWatch}] Warning:[${hWarning}])</div>"

            } else {
                paragraph "<i>Primary sensors missing. Click configuration below to assign weather devices.</i>"
            }
        }
        
        section("<b>Alert Trigger Causes (Last 20)</b>", hideable: true) {
            if (state.alertCauseHistory && state.alertCauseHistory.size() > 0) {
                def causeStr = state.alertCauseHistory.join("<hr style='margin: 5px 0; border: 0; border-top: 1px solid #ddd;'>")
                paragraph "<div style='font-size: 12px; background: #fff; padding: 10px; border: 1px solid #ccc; border-radius: 4px; max-height: 250px; overflow-y: auto;'>${causeStr}</div>"
            } else {
                paragraph "<i>No predictive alerts have triggered yet.</i>"
            }
            input "resetCauseHistoryBtn", "button", title: "Clear Alert Causes"
        }

        section("<b>Action History & Debugging</b>", hideable: true) {
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

        section("<b>1. Primary Environment Sensors (Required)</b>", hideable: true, hidden: true) {
            input "sensorTemp", "capability.sensor", title: "Outdoor Temperature Sensor", required: true
            input "sensorHum", "capability.sensor", title: "Outdoor Humidity Sensor", required: true
            input "sensorPress", "capability.sensor", title: "Barometric Pressure Sensor", required: true
            
            input "pressureSensorLocation", "enum", title: "Pressure Sensor Location", options: ["Outdoors", "Indoors"], defaultValue: "Outdoors", required: true, submitOnChange: true
            if (pressureSensorLocation == "Indoors") {
                paragraph "<i>Indoor pressure sensors are susceptible to false readings from doors opening or HVAC systems running. Select devices below to compensate.</i>"
                input "hvacThermostat", "capability.thermostat", title: "HVAC Thermostat (Compensation)", required: false
                input "contactSensors", "capability.contactSensor", title: "Exterior Doors / Windows", multiple: true, required: false
            }
        }

        section("<b>2. Advanced Prediction Sensors (Optional)</b>", hideable: true, hidden: true) {
            input "sensorLux", "capability.illuminanceMeasurement", title: "Solar Radiation / Lux Sensor", required: false
            input "sensorWind", "capability.sensor", title: "Wind Speed Sensor", required: false
            input "sensorWindDir", "capability.sensor", title: "Wind Direction Sensor", required: false
            input "sensorLightning", "capability.sensor", title: "Lightning Detector", required: false
        }
        
        section("<b>3. Precipitation & Accumulation Sensors (Optional)</b>", hideable: true, hidden: true) {
            input "sensorRain", "capability.sensor", title: "Rain Rate Sensor", required: false
            input "sensorRainDaily", "capability.sensor", title: "Daily Rain Accumulation Sensor", required: false
        }

        section("<b>4. Instant 'First Drop' Sensors</b>", hideable: true, hidden: true) {
            input "sensorLeak", "capability.waterSensor", title: "Instant Rain Sensor 1", required: false
            input "sensorLeak2", "capability.waterSensor", title: "Instant Rain Sensor 2", required: false
            input "sensorLeak3", "capability.waterSensor", title: "Instant Rain Sensor 3", required: false
            input "leakSensorRequiredCount", "enum", title: "Number of Instant Sensors required", options: ["1", "2", "3"], defaultValue: "2", required: true
            input "enableDewRejection", "bool", title: "Dew & Frost Rejection", defaultValue: true
            input "dewSpreadThreshold", "decimal", title: "Dew Rejection DP Spread Threshold (°)", defaultValue: 3.0, required: true
            input "stuckLeakTimeout", "number", title: "Stuck Sensor Timeout (Minutes)", required: true, defaultValue: 60
        }
        
        section("<b>5. Algorithm Tuning & Toggles</b>", hideable: true, hidden: true) {
            input "pressOffset", "decimal", title: "Barometric MSLP Offset (inHg)", defaultValue: 0.0
            
            input "enableMLTuning", "bool", title: "Enable Machine Learning Tracking (T-Storm & Flood)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Dynamically adjusts multipliers for Floods and Thunderstorms based on local validation history. Explicitly bypasses Tornado logic to ensure life-safety thresholds remain rigid.</span>", defaultValue: true
            
            input "enableTornado", "bool", title: "Enable Tornado / Severe Shear DNA", defaultValue: true
            input "enableThunderstorm", "bool", title: "Enable Severe Thunderstorm DNA", defaultValue: true
            input "enableFlood", "bool", title: "Enable Flash Flood DNA", defaultValue: true
            input "enableHeat", "bool", title: "Enable Severe Heat Advisory DNA", defaultValue: true
            input "enableKineticWind", "bool", title: "Enable Kinetic Air Density & Structural Wind Load", defaultValue: true
            input "enableGravityWave", "bool", title: "Enable Atmospheric Gravity Wave Detection", defaultValue: true
            input "enableDryMicroburst", "bool", title: "Enable Dry Microburst / Virga Detection", defaultValue: true
            input "enableStormVectoring", "bool", title: "Forgiving Storm Vectoring", defaultValue: true
            
            input "enableSolarPlunge", "bool", title: "Enable Solar Plunge Velocity (Thunderhead Detection)", defaultValue: true
            input "enableGustFactor", "bool", title: "Enable Outflow Boundary (Gust Factor)", defaultValue: true
            input "enableAbsHumLogic", "bool", title: "Enable Moisture Advection (Absolute Humidity)", defaultValue: true
            
            input "enableThermalSmoothing", "bool", title: "Thermal Smoothing (Sun-Spike Protection)", defaultValue: true
            input "enableStateOptimization", "bool", title: "Aggressive Data Pruning (Hub Health)", defaultValue: true
            input "staleDataTimeout", "number", title: "Stale Data Timeout (Minutes)", defaultValue: 30
        }

        section("<b>6. Virtual Output Switches</b>", hideable: true, hidden: true) {
            paragraph "<i>Map virtual switches to toggle when a specific hazard enters Watch or Warning state.</i>"
            input "switchTornadoWatch", "capability.switch", title: "Tornado WATCH Switch", required: false
            input "switchTornadoWarning", "capability.switch", title: "Tornado WARNING Switch", required: false
            
            input "switchThunderstormWatch", "capability.switch", title: "T-Storm WATCH Switch", required: false
            input "switchThunderstormWarning", "capability.switch", title: "T-Storm WARNING Switch", required: false
            
            input "switchFloodWatch", "capability.switch", title: "Flood WATCH Switch", required: false
            input "switchFloodWarning", "capability.switch", title: "Flood WARNING Switch", required: false

            input "switchHeatWatch", "capability.switch", title: "Heat WATCH Switch", required: false
            input "switchHeatWarning", "capability.switch", title: "Heat WARNING Switch", required: false
            
            input "debounceMins", "number", title: "State Debounce Time (Minutes)", required: true, defaultValue: 5
        }
        
        section("<b>7. Custom Notifications & Announcements</b>", hideable: true, hidden: true) {
            input "notifyModes", "mode", title: "Only send alerts in these modes (Leave blank for all)", multiple: true, required: false
            input "notificationCooldown", "number", title: "General Notification Spam Cooldown (Minutes)", required: true, defaultValue: 60
            
            input "tornadoWatchThresh", "number", title: "Tornado Watch Prob Setpoint (%)", defaultValue: 60, required: true
            input "tornadoWarningThresh", "number", title: "Tornado Warning Prob Setpoint (%)", defaultValue: 85, required: true
            input "tstormWatchThresh", "number", title: "T-Storm Watch Prob Setpoint (%)", defaultValue: 60, required: true
            input "tstormWarningThresh", "number", title: "T-Storm Warning Prob Setpoint (%)", defaultValue: 85, required: true
            input "floodWatchThresh", "number", title: "Flood Watch Prob Setpoint (%)", defaultValue: 60, required: true
            input "floodWarningThresh", "number", title: "Flood Warning Prob Setpoint (%)", defaultValue: 85, required: true
            input "heatWatchThresh", "number", title: "Heat Watch Prob Setpoint (%)", defaultValue: 60, required: true
            input "heatWarningThresh", "number", title: "Heat Warning Prob Setpoint (%)", defaultValue: 85, required: true
            
            paragraph "<hr><b>Advanced Continuous Timers & Resets</b>"
            input "threatSustainMins", "number", title: "Threat Persistence / Hold Time (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>How long to hold a peak probability score before allowing it to drop. Prevents jittery drop-offs from fast-moving severe cells.</span>", required: true, defaultValue: 30
            input "probResetMins", "number", title: "Watch Re-Arm Cooldown (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Probabilities must stay BELOW watch thresholds for this many consecutive minutes before another watch is armed. Spiking above resets the counter.</span>", required: true, defaultValue: 120
            input "precipResetMins", "number", title: "Warning Re-Arm Cooldown (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Environment must be completely clear of all severe threats for this many consecutive minutes before a new WARNING announcement is allowed.</span>", required: true, defaultValue: 120
            input "clearDelayMins", "number", title: "All Clear Delay (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Must be completely clear for this many consecutive minutes before sending the All Clear announcement.</span>", required: true, defaultValue: 60
            input "alertDelaySeconds", "number", title: "Alert Execution Delay (Seconds)", required: true, defaultValue: 180
            
            paragraph "<hr><b>Tornado / Severe Shear Alerts</b>"
            input "tornadoWatchNotify", "capability.notification", title: "[WATCH] Send Push Notification To:", multiple: true, required: false
            input "tornadoWarningNotify", "capability.notification", title: "[WARNING] Send Push Notification To:", multiple: true, required: false
            
            paragraph "<hr><b>Severe Thunderstorm Alerts</b>"
            input "tstormWatchNotify", "capability.notification", title: "[WATCH] Send Push Notification To:", multiple: true, required: false
            input "tstormWarningNotify", "capability.notification", title: "[WARNING] Send Push Notification To:", multiple: true, required: false
            
            paragraph "<hr><b>Flash Flood Alerts</b>"
            input "floodWatchNotify", "capability.notification", title: "[WATCH] Send Push Notification To:", multiple: true, required: false
            input "floodWarningNotify", "capability.notification", title: "[WARNING] Send Push Notification To:", multiple: true, required: false

            paragraph "<hr><b>Severe Heat Alerts</b>"
            input "heatWatchNotify", "capability.notification", title: "[WATCH] Send Push Notification To:", multiple: true, required: false
            input "heatWarningNotify", "capability.notification", title: "[WARNING] Send Push Notification To:", multiple: true, required: false
            
            paragraph "<hr><b>Weather Cleared Alerts</b>"
            input "clearNotifyDevices", "capability.notification", title: "[ALL CLEAR] Send Push Notification To:", multiple: true, required: false
        }
        
        section("<b>8. UI Preferences</b>", hideable: true, hidden: true) {
            input "historyDisplayMode", "enum", title: "24-Hour History Display", options: ["Data Table", "Hidden"], defaultValue: "Data Table", required: true, submitOnChange: true
        }

        if (app.id) {
            section("<b>Global Actions & Overrides</b>", hideable: true, hidden: true) {
                input "forceEvalBtn", "button", title: "⚙️ Force Logic Evaluation"
                input "clearStateBtn", "button", title: "⚠ Reset Internal State & Timers"
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
    if (!state.alertCauseHistory) state.alertCauseHistory = []
    
    if (!state.tornadoState) state.tornadoState = "Clear"
    if (!state.tstormState) state.tstormState = "Clear"
    if (!state.floodState) state.floodState = "Clear"
    if (!state.heatState) state.heatState = "Clear"
    
    if (!state.tornadoLastChange) state.tornadoLastChange = 0
    if (!state.tstormLastChange) state.tstormLastChange = 0
    if (!state.floodLastChange) state.floodLastChange = 0
    if (!state.heatLastChange) state.heatLastChange = 0
    
    if (!state.lastHeartbeat) state.lastHeartbeat = now()
    if (!state.smoothedTemp) state.smoothedTemp = null
    state.alertPending = false
    
    ['tornado', 'tstorm', 'flood', 'heat'].each { pfx ->
        if (state."${pfx}WatchArmed" == null) state."${pfx}WatchArmed" = true
        if (state."${pfx}WarningArmed" == null) state."${pfx}WarningArmed" = true
        if (!state."${pfx}BelowSince") state."${pfx}BelowSince" = now()
        if (!state."${pfx}ClearSince") state."${pfx}ClearSince" = now()
        state."${pfx}UpgradeTime" = null
    }
    
    if (!state.globalClearSince) state.globalClearSince = now()
    if (state.allClearSent == null) state.allClearSent = true
    
    if (!state.ts_ml_mult) state.ts_ml_mult = 1.0
    if (!state.flood_ml_mult) state.flood_ml_mult = 1.0
    
    state.evalPending = false
    state.lastEvalTime = 0
    state.hardwareAnomalyActive = false
    
    if (!state.algoDiagnostics) state.algoDiagnostics = []
    
    if (!state.pressureHistory) state.pressureHistory = []
    if (!state.tempHistory) state.tempHistory = []
    if (!state.ahHistory) state.ahHistory = []
    if (!state.luxHistory) state.luxHistory = []
    if (!state.spreadHistory) state.spreadHistory = []
    if (!state.windHistory) state.windHistory = []
    if (!state.windDirHistory) state.windDirHistory = []
    
    if (!state.lightningHistory) state.lightningHistory = []
    if (!state.strikeCountHistory) state.strikeCountHistory = []
    if (!state.probHistory) state.probHistory = []
    
    if (!state.sevenDayRain) state.sevenDayRain = []
    if (!state.currentDayRain) state.currentDayRain = 0.0
    if (!state.currentDateStr) state.currentDateStr = new Date().format("yyyy-MM-dd", location.timeZone)
    
    subscribeMulti(sensorTemp, ["temperature", "tempf"], "tempHandler")
    subscribeMulti(sensorHum, ["humidity"], "stdHandler")
    subscribeMulti(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], "pressureHandler")
    subscribeMulti(sensorWind, ["windSpeed", "windspeedmph", "wind"], "windHandler")
    subscribeMulti(sensorWindDir, ["windDirection", "winddir", "windDir"], "windDirHandler")
    subscribeMulti(sensorLightning, ["lightningDistance", "distance"], "lightningHandler")
    subscribeMulti(sensorLightning, ["lightningStrikeCount", "strikeCount", "strikes"], "strikeCountHandler")
    subscribeMulti(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], "stdHandler")
    subscribeMulti(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], "stdHandler")
    subscribeMulti(sensorLux, ["illuminance", "solarradiation", "solarRadiation"], "luxHandler")
    
    [sensorLeak, sensorLeak2, sensorLeak3].each { dev ->
        if (dev) subscribe(dev, "water", "stdHandler")
    }

    if (settings.pressureSensorLocation == "Indoors") {
        if (hvacThermostat) subscribe(hvacThermostat, "thermostatOperatingState", "hvacHandler")
        if (contactSensors) subscribe(contactSensors, "contact", "contactHandler")
    }
    
    runEvery5Minutes("queueEval")
    logAction("Advanced Severe Weather Detection Initialized.")
    queueEval()
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

def tuneSevereML(category, falseAlarm) {
    if (settings.enableMLTuning == false) return
    
    if (category == "Thunderstorm") {
        if (falseAlarm) {
            state.ts_ml_mult -= 0.05
            if (state.ts_ml_mult < 0.60) state.ts_ml_mult = 0.60
            logAction("🧠 ML Tuning: T-Storm False Alarm (Threat cleared without severe verification). Desensitizing multiplier to x${String.format('%.2f', state.ts_ml_mult)}")
        } else {
            state.ts_ml_mult += 0.05
            if (state.ts_ml_mult > 1.20) state.ts_ml_mult = 1.20 
            logAction("🧠 ML Tuning: T-Storm Validated. Increasing sensitivity multiplier to x${String.format('%.2f', state.ts_ml_mult)}")
        }
    } else if (category == "Flood") {
        if (falseAlarm) {
            state.flood_ml_mult -= 0.05
            if (state.flood_ml_mult < 0.60) state.flood_ml_mult = 0.60
            logAction("🧠 ML Tuning: Flood False Alarm (Threat cleared without extreme rain rates). Desensitizing multiplier to x${String.format('%.2f', state.flood_ml_mult)}")
        } else {
            state.flood_ml_mult += 0.05
            if (state.flood_ml_mult > 1.40) state.flood_ml_mult = 1.40
            logAction("🧠 ML Tuning: Flood Validated. Increasing sensitivity multiplier to x${String.format('%.2f', state.flood_ml_mult)}")
        }
    }
}

void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard data manually refreshed.")
    }
    else if (btn == "forceEvalBtn") { 
        logAction("MANUAL OVERRIDE: Forcing logic evaluation."); evaluateWeather() 
    }
    else if (btn == "resetCauseHistoryBtn") {
        state.alertCauseHistory = []
        logAction("Alert cause history cleared.")
    }
    else if (btn == "clearStateBtn") {
        logAction("EMERGENCY RESET: Purging history and resetting continuous timers.")
        state.tornadoState = "Clear"
        state.tstormState = "Clear"
        state.floodState = "Clear"
        state.heatState = "Clear"
        
        state.tornadoLastChange = 0
        state.tstormLastChange = 0
        state.floodLastChange = 0
        state.heatLastChange = 0
        
        state.pressureHistory = []
        state.tempHistory = []
        state.ahHistory = []
        state.luxHistory = []
        state.spreadHistory = []
        state.windHistory = []
        state.windDirHistory = []
        state.lightningHistory = []
        state.strikeCountHistory = []
        state.probHistory = []
        state.sevenDayRain = []
        state.currentDayRain = 0.0
        state.recentStrikes = 0
        
        state.alertPending = false
        
        ['tornado', 'tstorm', 'flood', 'heat'].each { pfx ->
            state."${pfx}WatchArmed" = true
            state."${pfx}WarningArmed" = true
            state."${pfx}BelowSince" = now()
            state."${pfx}ClearSince" = now()
            state."${pfx}UpgradeTime" = null
        }
        state.globalClearSince = now()
        state.allClearSent = true
        state.alertCauseHistory = []
        
        state.lastContactOpenTime = null
        state.lastHvacChangeTime = null
        
        state.ts_ml_mult = 1.0
        state.flood_ml_mult = 1.0
        state.tsConfirmed = false
        state.floodConfirmed = false
        
        state.peakTorProb = 0
        state.peakTorTime = null
        state.peakTsProb = 0
        state.peakTsTime = null
        state.peakFloodProb = 0
        state.peakFloodTime = null
        state.peakHeatProb = 0
        state.peakHeatTime = null
        
        state.lastNotificationTime = 0
        state.lastNotificationSeverity = 0
        state.hardwareAnomalyActive = false
        
        unschedule("evalWrapper")
        state.evalPending = false
        
        state.algoDiagnostics = []
        
        safeOff(switchTornadoWatch); safeOff(switchTornadoWarning)
        safeOff(switchThunderstormWatch); safeOff(switchThunderstormWarning)
        safeOff(switchFloodWatch); safeOff(switchFloodWarning)
        safeOff(switchHeatWatch); safeOff(switchHeatWarning)
        evaluateWeather()
    }
    else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    }
}

def queueEval() {
    markActive()
    def lastEval = state.lastEvalTime ?: 0
    if ((now() - lastEval) > 60000) {
        state.evalPending = false
        evaluateWeather()
    } else if (!state.evalPending) {
        state.evalPending = true
        runIn(15, "evalWrapper")
    }
}

def evalWrapper() {
    state.evalPending = false
    evaluateWeather()
}

def markActive() { state.lastHeartbeat = now() }

def updateHistory(historyName, val, maxAgeMs) {
    if (val == null) return
    def cleanVal
    try { cleanVal = val.toString().replaceAll("[^\\d.-]", "").toFloat() } catch(e) { return }
    
    def hist = state."${historyName}" ?: []
    
    if (hist.size() > 0) {
        def lastVal = hist.last().value as Float
        def maxDelta = 999.0
        
        if (historyName == "pressureHistory") maxDelta = isMetric() ? 3.0 : 0.10 
        else if (historyName == "tempHistory") maxDelta = isMetric() ? 11.0 : 20.0 
        else if (historyName == "luxHistory") maxDelta = 150000.0 
        
        if (Math.abs(cleanVal - lastVal) > maxDelta) {
            logAction("⚠ Hardware Anomaly Rejected: '${historyName}' physically impossible jump (${cleanVal}).")
            state.hardwareAnomalyActive = true
            return 
        } else {
            state.hardwareAnomalyActive = false
        }
    }
    
    hist.add([time: now(), value: cleanVal])
    def cutoff = now() - maxAgeMs
    hist = hist.findAll { it.time >= cutoff }
    
    def maxPoints = settings.enableStateOptimization != false ? 144 : 300
    if (hist.size() > maxPoints) {
        hist = hist.drop(hist.size() - maxPoints)
    }
    
    state."${historyName}" = hist
}

def sensorHandler(evt) { queueEval() }
def stdHandler(evt) { queueEval() }
def tempHandler(evt) { updateHistory("tempHistory", evt.value, 86400000); queueEval() }
def pressureHandler(evt) { 
    def raw = 0.0
    try { raw = evt.value.toString().replaceAll("[^\\d.-]", "").toFloat() } catch(e) {}
    def cal = raw + (settings.pressOffset ?: 0.0)
    updateHistory("pressureHistory", cal, 86400000)
    queueEval() 
}
def windHandler(evt) { updateHistory("windHistory", evt.value, 86400000); queueEval() }
def windDirHandler(evt) { updateHistory("windDirHistory", evt.value, 86400000); queueEval() }

def lightningHandler(evt) { 
    if (evt.isStateChange) {
        def dist = evt.value.toString().replaceAll("[^\\d.-]", "").toFloat()
        if (dist > 0.0) { 
            updateHistory("lightningHistory", evt.value, 10800000) 
            queueEval() 
        } else {
            logDebug("Ignored 0km lightning strike distance update (likely EMI).")
        }
    }
}

def strikeCountHandler(evt) { 
    def dist = getFloat(sensorLightning, ["lightningDistance", "distance"], 999.0)
    if (dist > 0.0) { 
        updateHistory("strikeCountHistory", evt.value, 86400000)
        queueEval() 
    } else {
        logDebug("Ignored lightning strike count increment due to 0km distance (likely EMI).")
    }
}

def luxHandler(evt) { 
    def rawVal = 0.0
    try { rawVal = evt.value.toString().replaceAll("[^\\d.-]", "").toFloat() } catch(e) { return }
    
    if (evt.name == "solarRadiation" || evt.name == "solarradiation") {
        rawVal = rawVal * 126.7
    }
    updateHistory("luxHistory", rawVal, 86400000)
    queueEval() 
}

def contactHandler(evt) {
    if (evt.value == "open") {
        state.lastContactOpenTime = now()
        logDebug("Contact opened. Logging time for indoor pressure compensation.")
    }
    queueEval()
}

def hvacHandler(evt) {
    state.lastHvacChangeTime = now()
    logDebug("HVAC state changed to ${evt.value}. Logging time for indoor pressure compensation.")
    queueEval()
}

def logAlertCause(type, probScore, reasons) {
    def hist = state.alertCauseHistory ?: []
    def timeStr = new Date().format("MM/dd hh:mm a", location.timeZone)
    hist.add(0, "<b>[${timeStr}] ${type} Triggered (${probScore}%)</b><br><i>Primary Factors:</i> ${reasons}")
    if (hist.size() > 20) hist = hist[0..19]
    state.alertCauseHistory = hist
}

// === METEOROLOGICAL MATHEMATICS ===

def calculateAbsoluteHumidity(tVal, rh) {
    def tC = isMetric() ? tVal : (tVal - 32.0) * (5.0 / 9.0)
    def ah = (6.112 * Math.exp((17.67 * tC) / (tC + 243.5)) * rh * 2.1674) / (273.15 + tC)
    return ah
}

def getTrendData(hist, minTimeHr, maxLookbackHr = 1.0) {
    if (!hist || hist.size() < 2) return [rate: 0.0, diff: 0.0, str: "Gathering Data"]
    
    def cutoff = now() - (maxLookbackHr * 3600000).toLong()
    def recentHist = hist.findAll { it.time >= cutoff }
    
    if (recentHist.size() < 2) {
        if (hist.size() >= 2) {
            recentHist = [hist[hist.size() - 2], hist[hist.size() - 1]]
        } else {
            return [rate: 0.0, diff: 0.0, str: "Gathering Data"]
        }
    }
    
    def oldest = recentHist.first()
    def newest = recentHist.last()
    def diff = newest.value - oldest.value
    def timeSpanHr = (newest.time - oldest.time) / 3600000.0
    
    if (timeSpanHr < minTimeHr) {
         return [rate: 0.0, diff: diff, str: "Stable (<${Math.round(minTimeHr*60)}m data)"]
    }
    
    if (timeSpanHr > maxLookbackHr * 1.5) {
         return [rate: 0.0, diff: diff, str: "Gap Detected"]
    }
    
    def ratePerHour = diff / timeSpanHr
    return [rate: ratePerHour, diff: diff, str: "${diff > 0 ? '+' : ''}${String.format('%.2f', ratePerHour)}/hr"]
}

def getAccelerationData(hist) {
    if (!hist || hist.size() < 4) return 0.0
    def cutoff = now() - 1800000
    def recent = hist.findAll { it.time >= cutoff }
    def older = hist.findAll { it.time < cutoff && it.time >= now() - 3600000 }
    if (recent.size() < 2 || older.size() < 2) return 0.0
    
    def recentTrend = getTrendData(recent, 0.25, 0.5)
    def olderTrend = getTrendData(older, 0.25, 1.0) 
    
    if (Math.abs(recentTrend.diff) < 0.05 && Math.abs(olderTrend.diff) < 0.05) return 0.0
    
    return recentTrend.rate - olderTrend.rate
}

def getStormVectorData(hist) {
    if (!hist || hist.size() < 4) return [status: "Gathering", speed: 0.0, eta: -1]
    
    def cutoff = now() - 10800000
    def recentHist = hist.findAll { it.time >= cutoff }
    
    if (recentHist.size() < 4) return [status: "Gathering", speed: 0.0, eta: -1]
    
    def sorted = recentHist.sort { it.time }
    def mid = (sorted.size() / 2).toInteger()
    def older = sorted[0..(mid-1)]
    def newer = sorted[mid..(sorted.size()-1)]
    
    def oldDist = older.sum { it.value } / older.size()
    def newDist = newer.sum { it.value } / newer.size()
    def oldTime = older.sum { it.time } / older.size()
    def newTime = newer.sum { it.time } / newer.size()
    
    def timeDiffHr = (newTime - oldTime) / 3600000.0
    if (timeDiffHr <= 0) return [status: "Stalled", speed: 0.0, eta: -1]
    
    def distDiff = oldDist - newDist 
    def speedMph = distDiff / timeDiffHr
    
    if (speedMph > 2.0) {
        def etaHr = newDist / speedMph
        return [status: "Approaching", speed: speedMph, eta: etaHr * 60.0]
    } else if (speedMph < -2.0) {
        return [status: "Departing", speed: Math.abs(speedMph), eta: -1]
    }
    return [status: "Stalled/Lateral", speed: 0.0, eta: -1]
}

def detectGravityWaves(hist) {
    if (!hist || hist.size() < 6) return false
    def cutoff = now() - 1800000 
    def recent = hist.findAll { it.time >= cutoff }
    if (recent.size() < 6) return false
    
    int directionChanges = 0
    def lastDeltaSign = 0
    for (int i = 1; i < recent.size(); i++) {
        def delta = recent[i].value - recent[i-1].value
        if (Math.abs(delta) < 0.03) continue 
        def currentSign = delta > 0 ? 1 : -1
        if (lastDeltaSign != 0 && currentSign != lastDeltaSign) {
            directionChanges++
        }
        lastDeltaSign = currentSign
    }
    return (directionChanges >= 3)
}

def calculateAirDensity(tF, rh, pInHg) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def tK = tC + 273.15
    def pMb = pInHg * 33.8639
    def pPa = pMb * 100.0 
    def ePa = (rh / 100.0) * 6.1078 * Math.pow(10, (7.5 * tC) / (tC + 237.3)) * 100.0
    def rDry = 287.058
    def rVapor = 461.495
    def pDry = pPa - ePa
    return (pDry / (rDry * tK)) + (ePa / (rVapor * tK))
}

def calculateWindLoad(rho, windMph) {
    def v_ms = windMph * 0.44704
    def q_Pa = 0.5 * rho * (v_ms * v_ms)
    return isMetric() ? q_Pa : (q_Pa * 0.0208854) 
}

def calculateVPD(tVal, rh) {
    def tC = (tVal - 32.0) * (5.0 / 9.0)
    def svp = 0.61078 * Math.exp((17.27 * tC) / (tC + 237.3))
    def avp = svp * (rh / 100.0)
    return svp - avp
}

def calculateDewPoint(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def gamma = Math.log(rh / 100.0) + ((17.62 * tC) / (243.12 + tC))
    def dpC = (243.12 * gamma) / (17.62 - gamma)
    return (dpC * (9.0 / 5.0)) + 32.0
}

def calculateWetBulb(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def twC = tC * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(tC + rh) - Math.atan(rh - 1.676331) + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035
    return (twC * (9.0 / 5.0)) + 32.0
}

def getAngularDiff(angle1, angle2) {
    def diff = Math.abs(angle1 - angle2) % 360
    return diff > 180 ? 360 - diff : diff
}

def evaluateWeather() {
    state.lastEvalTime = now()
    
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    def currentHour = new Date().format("H", location.timeZone).toInteger()
    def isMorningTransition = (currentHour >= 4 && currentHour < 10)

    if (!state.currentDateStr) state.currentDateStr = todayStr
    
    if (state.currentDateStr != todayStr) {
        def yesterdayTotal = state.currentDayRain ?: 0.0
        def hist = state.sevenDayRain ?: []
        hist.add(0, [date: state.currentDateStr, amount: yesterdayTotal])
 
        if (hist.size() > 7) hist = hist[0..6]
        state.sevenDayRain = hist
        state.currentDateStr = todayStr
        state.currentDayRain = 0.0
    }
 
    def currentDaily = getFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
    if (currentDaily > (state.currentDayRain ?: 0.0)) {
       state.currentDayRain = currentDaily
    }

    if (!sensorTemp || !sensorHum || !sensorPress) return

    def staleMins = settings.staleDataTimeout ?: 30
    def isStale = (settings.enableStaleCheck != false) && ((now() - (state.lastHeartbeat ?: now())) > (staleMins * 60000))
    state.isStale = isStale
    
    def t = getFloat(sensorTemp, ["temperature", "tempf"], 0.0)
    def h = getFloat(sensorHum, ["humidity"], 0.0)
    def p = getFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], 0.0)
    p += (settings.pressOffset ?: 0.0)

    if (settings.enableThermalSmoothing != false) {
        def lastT = state.smoothedTemp != null ? state.smoothedTemp : t
        if (t > lastT && (t - lastT) > 2.0 && state.tempHistory?.size() > 0) {
            t = lastT + ((t - lastT) * 0.3)
        }
        state.smoothedTemp = t
    }

    def r = getFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], 0.0)
    def windVal = getFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], 0.0)
    def windGustVal = getFloat(sensorWind, ["windGust", "windgustmph"], windVal)
    def windDirVal = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], 0.0)
    
    def luxVal = getFloat(sensorLux, ["illuminance"], null)
    if (luxVal == null) {
        def rad = getFloat(sensorLux, ["solarRadiation", "solarradiation"], null)
        if (rad != null) luxVal = rad * 126.7
    }
    if (luxVal == null) luxVal = 0.0
    
    if (r >= 0.5 && state.currentDayRain >= 0.02) state.floodConfirmed = true
    if (recentStrikes > 0 || windVal > 30.0) state.tsConfirmed = true

    def isMet = isMetric()
    def th_pDropTornado  = isMet ? -1.35 : -0.04
    def th_pAccelTVS     = isMet ? -2.71 : -0.08
    def th_pRiseGust     = isMet ? 1.02 : 0.03
    def th_pDropFlood    = isMet ? -1.02 : -0.03
    def th_windTornado   = isMet ? 56.3 : 35.0
    def th_windGust      = isMet ? 32.2 : 20.0
    def th_windShift     = isMet ? 12.8 : 8.0
    def th_windGustBase  = isMet ? 35.4 : 22.0
    def th_tHigh         = isMet ? 29.4 : 85.0
    def th_dpHigh        = isMet ? 21.1 : 70.0
    def th_tDropGust     = isMet ? -1.1 : -2.0
    def th_tDropMicro    = isMet ? -5.5 : -10.0
    def th_dpSpreadMicro = isMet ? 13.8 : 25.0
    def th_rainWeekSat   = isMet ? 50.8 : 2.0
    def th_rainDaySat    = isMet ? 25.4 : 1.0
    def th_rainDayFlood  = isMet ? 38.1 : 1.5
    def rDiv             = isMet ? 50.8 : 2.0
    def aDiv             = isMet ? 76.2 : 3.0
    
    def strikeCountRaw = getFloat(sensorLightning, ["lightningStrikeCount", "strikeCount", "strikes"], null)
    def recentStrikes = 0
    
    if (strikeCountRaw != null) {
        def cutoff3Hr = now() - 10800000
        def validHist = state.strikeCountHistory?.findAll { it.time >= cutoff3Hr }
        if (validHist && validHist.size() > 0) {
            def lastVal = validHist.first().value as Float
            for (int i = 1; i < validHist.size(); i++) {
                def cVal = validHist[i].value as Float
                if (cVal >= lastVal) recentStrikes += (cVal - lastVal)
                else recentStrikes += cVal
                lastVal = cVal
            }
            def cRaw = strikeCountRaw as Float
            if (cRaw > lastVal) recentStrikes += (cRaw - lastVal)
            else if (cRaw < lastVal) recentStrikes += cRaw
        }
    } else {
        def cutoff3Hr = now() - 10800000
        recentStrikes = state.lightningHistory?.count { it.time >= cutoff3Hr } ?: 0
    }
    state.recentStrikes = Math.round(recentStrikes)

    def dp = calculateDewPoint(t, h)
    state.currentDewPoint = dp
    def dpSpread = t - dp
    if (dpSpread < 0) dpSpread = 0.0
    state.dewPointSpread = dpSpread
    updateHistory("spreadHistory", dpSpread, 86400000)
    
    def vpd = calculateVPD(t, h)
    state.currentVPD = vpd
    def wb = calculateWetBulb(t, h)
    state.currentWetBulb = wb

    def ah = calculateAbsoluteHumidity(t, h)
    state.currentAH = ah
    updateHistory("ahHistory", ah, 86400000)
    def ahTrendData = getTrendData(state.ahHistory, 0.25, 1.0)
    state.ahTrendStr = ahTrendData.str

    def pTrendData = getTrendData(state.pressureHistory, 0.25, 1.0)
    def pAccel = getAccelerationData(state.pressureHistory)
    def tTrendData = getTrendData(state.tempHistory, 0.25, 1.0)
    def sTrendData = getTrendData(state.spreadHistory, 0.25, 1.0)
    def wTrendData = getTrendData(state.windHistory, 0.25, 1.0)
    
    state.pressureTrendStr = pTrendData.str
    state.tempTrendStr = tTrendData.str
    state.spreadTrendStr = sTrendData.str
    state.windTrendStr = sensorWind ? wTrendData.str : "N/A"

    def airDensity = 1.225
    def windLoad = 0.0
    if (settings.enableKineticWind != false) {
        airDensity = calculateAirDensity(t, h, p)
        state.currentAirDensity = airDensity
        if (sensorWind) {
            windLoad = calculateWindLoad(airDensity, windVal)
            state.currentWindLoad = windLoad
        }
    }

    def indoorAnomalyActive = false
    def anomalyReasons = []
    
    if (settings.pressureSensorLocation == "Indoors") {
        def contactOpen = false
        if (settings.contactSensors) {
            settings.contactSensors.each { if (it.currentValue("contact") == "open") contactOpen = true }
        }
        
        if (contactOpen) {
            anomalyReasons << "Door/Window Open"
        } else if (state.lastContactOpenTime && (now() - state.lastContactOpenTime) < 600000) {
            anomalyReasons << "Door/Window Recently Opened"
        }
        
        def hvacState = settings.hvacThermostat?.currentValue("thermostatOperatingState")
        if (hvacState in ["heating", "cooling", "fan only"]) {
            anomalyReasons << "HVAC Running (${hvacState})"
        } else if (state.lastHvacChangeTime && (now() - state.lastHvacChangeTime) < 600000) {
            anomalyReasons << "HVAC Recently Cycled"
        }
        
        if (anomalyReasons.size() > 0) {
            indoorAnomalyActive = true
            state.indoorAnomalyStr = anomalyReasons.join(", ")
        } else {
            state.indoorAnomalyStr = "None"
        }
    } else {
        state.indoorAnomalyStr = "None"
    }

    def isGravityWave = false
    if (settings.enableGravityWave != false) {
        isGravityWave = detectGravityWaves(state.pressureHistory)
        if (isGravityWave && indoorAnomalyActive) isGravityWave = false
        state.gravityWaveActive = isGravityWave
    }

    def shiftMagnitude = 0.0
    def dpm = 0.0
    state.windShiftDetected = false
    state.shearStr = ""
    
    if (sensorWindDir && state.windDirHistory && state.windDirHistory.size() > 2) {
        def cutoff = now() - 300000 
        def pastReadings = state.windDirHistory.findAll { it.time >= cutoff }
        if (pastReadings.size() > 1) {
            def oldReading = pastReadings.first()
            def newReading = pastReadings.last()
            def timeMins = (newReading.time - oldReading.time) / 60000.0
            
            if (timeMins >= 0.5) { 
                shiftMagnitude = getAngularDiff(oldReading.value as Float, newReading.value as Float)
                dpm = shiftMagnitude / timeMins
                
                if (dpm >= 15.0 && windVal >= th_windShift) {
                    state.windShiftDetected = true
                    state.shearStr = "${String.format('%.1f', dpm)}°/min"
                }
            }
        }
    }

    def vectorData = getStormVectorData(state.lightningHistory)
    if (settings.enableStormVectoring != false && sensorLightning && recentStrikes > 0) {
        if (vectorData.status == "Approaching") state.lightningVectorStr = "Approaching @ ${String.format('%.1f', vectorData.speed)} mph"
        else if (vectorData.status == "Departing") state.lightningVectorStr = "Departing @ ${String.format('%.1f', vectorData.speed)} mph"
        else state.lightningVectorStr = vectorData.status
    } else {
        state.lightningVectorStr = "N/A"
    }

    def wetCountRaw = [sensorLeak, sensorLeak2, sensorLeak3].count { it?.currentValue("water") == "wet" }
    def reqWets = settings.leakSensorRequiredCount ? settings.leakSensorRequiredCount.toInteger() : 2
    def rawLeakWet = (wetCountRaw >= reqWets)
    
    if (rawLeakWet) {
        if (!state.leakWetStartTime) state.leakWetStartTime = now()
    } else {
        state.leakWetStartTime = null
    }

    def stuckLeakTimeoutMins = settings.stuckLeakTimeout ?: 60
    def stuckLeakActive = false
    if (rawLeakWet && state.leakWetStartTime && r == 0) {
        if ((now() - state.leakWetStartTime) > (stuckLeakTimeoutMins * 60000)) stuckLeakActive = true
    }
    state.stuckLeakActive = stuckLeakActive
    
    def dewRejectionActive = false
    if (rawLeakWet && settings.enableDewRejection != false && r == 0.0) {
        def checkLux = sensorLux ? (luxVal < 100) : true
        def checkWind = sensorWind ? (windVal < 2.0) : true
        def dpThresh = settings.dewSpreadThreshold != null ? settings.dewSpreadThreshold.toFloat() : 3.0
        if (checkLux && checkWind && dpSpread <= dpThresh) dewRejectionActive = true
    }
    state.dewRejectionActive = dewRejectionActive
    def leakWet = rawLeakWet && !stuckLeakActive && !dewRejectionActive

    def solarPlungeDetected = false
    def solarMsg = ""
    if (settings.enableSolarPlunge != false && sensorLux && state.luxHistory && state.luxHistory.size() > 2) {
        def earliestLuxTime = state.luxHistory.first().time as Long
        if ((now() - earliestLuxTime) >= 900000) {
            def cutoff = now() - 900000 
            def pastReadings = state.luxHistory.findAll { (it.time as Long) <= cutoff }
            if (pastReadings && pastReadings.size() > 0) {
                def oldLux = pastReadings.last().value as Float
                if (oldLux > 10000 && luxVal < (oldLux * 0.4) && (oldLux - luxVal) > 10000) {
                    if (tTrendData.rate > 0 && windGustVal < 10 && pTrendData.rate >= 0) {
                        solarMsg = "Plunge ignored (Shadow Rejection Filter: No supporting atmospheric shift)"
                    } else {
                        solarPlungeDetected = true
                        solarMsg = "Massive solar plunge detected! Dropped ${Math.round(oldLux - luxVal)} lux rapidly (Dense Anvil Cloud)."
                    }
                }
            }
        }
    }

    def gustFactorDetected = false
    def gustMsg = ""
    if (settings.enableGustFactor != false && sensorWind) {
        def cutoff = now() - 1800000 
        def recentWinds = state.windHistory?.findAll { (it.time as Long) >= cutoff }
        if (recentWinds && recentWinds.size() > 2) {
            def avgWind = recentWinds.sum { it.value as Float } / recentWinds.size()
            if (avgWind > 0 && windVal >= th_windGustBase && windVal >= (avgWind * 2.5)) {
                gustFactorDetected = true
                gustMsg = "Outflow Boundary: Wind spiked to ${windVal} (30m Avg: ${String.format('%.1f', avgWind)})"
            }
        }
    }

    def advectionDetected = false
    def advMsg = ""
    if (settings.enableAbsHumLogic != false && state.pressureHistory && state.pressureHistory.size() >= 2) {
        def pDropReq = isMorningTransition ? th_pDropTornado : th_pDropFlood 
        def pressureIsReporting = (pTrendData.str != "Gathering Data" && pTrendData.str != "Gap Detected")
        if (ahTrendData.rate > 0.3 && pressureIsReporting && pTrendData.rate <= pDropReq && !indoorAnomalyActive) {
            advectionDetected = true
            advMsg = "Rapid Moisture Advection: Absolute Humidity rising (${ahTrendData.str}) alongside falling pressure."
        }
    }

    // ==========================================================
    // DIAGNOSTIC MATRIX ENGINE
    // ==========================================================
    def diagList = []
    def torProb = 0.0
    def tsProb = 0.0
    def floodProb = 0.0
    def heatProb = 0.0
    
    def torFactors = 0
    def tsFactors = 0
    def floodFactors = 0
    def heatFactors = 0

    if (!isStale && !state.hardwareAnomalyActive) {
        // 1. TORNADO DNA
        if (settings.enableTornado != false) {
            def added = 0
            def msg = "Shear/Barometrics Stable"
            
            if (settings.enableGravityWave != false && isGravityWave) { 
                added += 15; msg = "Pre-storm Gravity Waves detected"; diagList << [name: "Tornado: Gravity Wave", status: "ON", effect: "+15%", desc: msg]; torFactors++ 
            } else if (settings.enableGravityWave != false && indoorAnomalyActive) {
                diagList << [name: "Tornado: Gravity Wave", status: "IGNORED", effect: "0%", desc: "Suppressed by active indoor interference: ${state.indoorAnomalyStr}"]
            }
            
            if (pTrendData.rate <= th_pDropTornado && state.windShiftDetected) { 
                if (indoorAnomalyActive) {
                    diagList << [name: "Tornado: Frontal Shear", status: "IGNORED", effect: "0%", desc: "Suppressed by active indoor interference: ${state.indoorAnomalyStr}"]
                } else {
                    added += 30; msg = "Violent Pressure Drop + Rapid Rotational Shear (${state.shearStr})"; diagList << [name: "Tornado: Frontal Shear", status: "ON", effect: "+30%", desc: msg]; torFactors++ 
                }
            }
            
            if (pAccel <= th_pAccelTVS && (!sensorWind || windVal >= th_windShift)) { 
                if (indoorAnomalyActive) {
                    diagList << [name: "Tornado: Pressure Accel", status: "IGNORED", effect: "0%", desc: "Suppressed by active indoor interference: ${state.indoorAnomalyStr}"]
                } else {
                    added += 50; msg = "Violent localized pressure plunge (TVS Signature)"; diagList << [name: "Tornado: Pressure Acceleration", status: "ACTIVE", effect: "+50%", desc: msg]; torFactors++ 
                }
            }
            
            def kineticThresh = isMet ? 140.0 : 3.0
            if (windLoad >= kineticThresh || (sensorWind && windVal >= th_windTornado)) { added += 40; msg = "Destructive kinetic wind force"; diagList << [name: "Tornado: Kinetic Wind", status: "ACTIVE", effect: "+40%", desc: msg]; torFactors++ }
            
            if (gustFactorDetected) { added += 25; msg = gustMsg; diagList << [name: "Tornado: Outflow Boundary", status: "ACTIVE", effect: "+25%", desc: msg]; torFactors++ }
            if (solarPlungeDetected) { added += 15; msg = solarMsg; diagList << [name: "Tornado: Solar Plunge", status: "ACTIVE", effect: "+15%", desc: msg]; torFactors++ }
            
            torProb += added
            
            def sustainMs = (settings.threatSustainMins ?: 30) * 60000
            if (torProb >= (state.peakTorProb ?: 0)) { 
                state.peakTorProb = torProb
                state.peakTorTime = now() 
            } else if (state.peakTorTime && (now() - state.peakTorTime) < sustainMs) { 
                torProb = state.peakTorProb
                diagList << [name: "Tornado Hysteresis", status: "ACTIVE", effect: "Hold", desc: "Holding peak probability for ${settings.threatSustainMins ?: 30} mins."] 
            } else { 
                state.peakTorProb = torProb 
            }
            
            if (torProb > 100) torProb = 100
        }

        // 2. THUNDERSTORM DNA
        if (settings.enableThunderstorm != false) {
            def added = 0
            def msg = "Convective Activity Stable"
            
            if (t > th_tHigh && dp > th_dpHigh) { 
                def capVal = isMorningTransition ? 5 : 15 
                added += capVal
                msg = isMorningTransition ? "High CAPE Proxy (Morning Suppressed)" : "High CAPE Proxy (Heat+Moisture)"
                diagList << [name: "T-Storm: Thermodynamics", status: "ON", effect: "+${capVal}%", desc: msg]; tsFactors++ 
            }
            
            if (settings.enableStormVectoring != false && vectorData.status == "Approaching") { 
                added += 40; msg = "Active core approaching"; diagList << [name: "T-Storm: Vectoring", status: "ACTIVE", effect: "+40%", desc: msg]; tsFactors++ 
            }
            
            if (recentStrikes > 0) { 
                def sScore = (recentStrikes / 10.0) * 40.0; if (sScore > 50) sScore = 50; 
                added += sScore; msg = "${recentStrikes} strikes (3hr)"; diagList << [name: "T-Storm: Lightning", status: "ON", effect: "+${Math.round(sScore)}%", desc: msg]; tsFactors++ 
            }
            
            if (windVal > th_windGust && pTrendData.rate >= th_pRiseGust && tTrendData.rate < th_tDropGust) { 
                if (indoorAnomalyActive) {
                    diagList << [name: "T-Storm: Gust Front", status: "IGNORED", effect: "0%", desc: "Pressure rise suppressed by active indoor interference: ${state.indoorAnomalyStr}"]
                } else {
                    added += 40; msg = "Gust Front / Mesohigh Pressure Jump"; diagList << [name: "T-Storm: Gust Front", status: "ACTIVE", effect: "+40%", desc: msg]; tsFactors++ 
                }
            }
            
            if (settings.enableDryMicroburst != false && dpSpread >= th_dpSpreadMicro && tTrendData.rate <= th_tDropMicro && gustFactorDetected && r == 0.0) { 
                added += 60; msg = "Virga Flash-Cooling / Dry Microburst"; diagList << [name: "T-Storm: Microburst", status: "ACTIVE", effect: "+60%", desc: msg]; tsFactors++ 
            }
            
            if (leakWet) { added += 30; msg = "First drop instant sensor wet"; diagList << [name: "T-Storm: Precipitation", status: "ON", effect: "+30%", desc: msg]; tsFactors++ }
            
            if (solarPlungeDetected) { added += 15; msg = solarMsg; diagList << [name: "T-Storm: Thunderhead / Anvil", status: "ACTIVE", effect: "+15%", desc: msg]; tsFactors++ }
            if (gustFactorDetected) { added += 30; msg = gustMsg; diagList << [name: "T-Storm: Outflow Boundary", status: "ACTIVE", effect: "+30%", desc: msg]; tsFactors++ }
            if (advectionDetected) { added += 25; msg = advMsg; diagList << [name: "T-Storm: Moisture Advection", status: "ACTIVE", effect: "+25%", desc: msg]; tsFactors++ }
            
            tsProb += added
            tsProb *= (state.ts_ml_mult ?: 1.0)
            
            // --- Multi-Key Verification Gates ---
            if (tsProb > 60 && tsFactors < 2) {
                tsProb = 60
                diagList << [name: "Two-Key Verification (T-Storm)", status: "ACTIVE", effect: "Capped at 60%", desc: "Requires at least 2 independent triggers for Watch levels. Current Triggers: ${tsFactors}"]
            }
            
            def warnThresh = settings.tstormWarningThresh ?: 85
            if (tsProb >= warnThresh && (tsFactors < 3 || recentStrikes == 0)) {
                tsProb = warnThresh - 1 
                diagList << [name: "Three-Key & Strike Verification", status: "ACTIVE", effect: "Capped below Warning", desc: "Requires 3 triggers AND active lightning for a Warning. Current factors: ${tsFactors}"]
            }
            
            def sustainMs = (settings.threatSustainMins ?: 30) * 60000
            if (tsProb >= (state.peakTsProb ?: 0)) { 
                state.peakTsProb = tsProb
                state.peakTsTime = now() 
            } else if (state.peakTsTime && (now() - state.peakTsTime) < sustainMs) { 
                tsProb = state.peakTsProb
                diagList << [name: "T-Storm Hysteresis", status: "ACTIVE", effect: "Hold", desc: "Holding peak probability for ${settings.threatSustainMins ?: 30} mins."] 
            } else { 
                state.peakTsProb = tsProb 
            }
            
            if (tsProb > 100) tsProb = 100
        }

        // 3. FLOOD DNA
        if (settings.enableFlood != false) {
            def added = 0
            def msg = "Accumulation & Rates Stable"
            
            def weeklyRain = state.sevenDayRain?.sum { it.amount as Float } ?: 0.0
            if (weeklyRain >= th_rainWeekSat && (r > 0 || state.currentDayRain >= th_rainDaySat)) {
                added += 25; diagList << [name: "Flood: Saturated Ground", status: "ACTIVE", effect: "+25%", desc: "Ground pre-saturated by ${String.format('%.1f', weeklyRain)} of recent rain"]; floodFactors++
            }
            
            if (state.currentDayRain >= th_rainDayFlood && pTrendData.rate <= th_pDropFlood) { 
                if (indoorAnomalyActive) {
                    diagList << [name: "Flood: Saturation", status: "IGNORED", effect: "0%", desc: "Pressure drop suppressed by indoor interference: ${state.indoorAnomalyStr}"]
                } else {
                    added += 30; msg = "Saturated Ground + Falling Pressure"; diagList << [name: "Flood: Saturation", status: "ON", effect: "+30%", desc: msg]; floodFactors++ 
                }
            }
            def rScore = (r / rDiv) * 60.0; if (rScore > 70) rScore = 70; added += rScore; if (rScore > 0) { diagList << [name: "Flood: Rain Rate", status: "ACTIVE", effect: "+${Math.round(rScore)}%", desc: "${String.format('%.2f', r)} /hr"]; floodFactors++ }
            def aScore = (state.currentDayRain / aDiv) * 40.0; if (aScore > 60) aScore = 60; added += aScore; if (aScore > 0) { diagList << [name: "Flood: Accumulation", status: "ON", effect: "+${Math.round(aScore)}%", desc: "${String.format('%.2f', state.currentDayRain)} total"]; floodFactors++ }
            
            if (advectionDetected) { added += 30; msg = advMsg; diagList << [name: "Flood: Moisture Advection", status: "ACTIVE", effect: "+30%", desc: msg]; floodFactors++ }
            
            floodProb += added
            floodProb *= (state.flood_ml_mult ?: 1.0)
            
            if (floodProb > 60 && floodFactors < 2) {
                floodProb = 60
                diagList << [name: "Two-Key Verification (Flood)", status: "ACTIVE", effect: "Capped at 60%", desc: "Requires at least 2 independent triggers. Current Triggers: ${floodFactors}"]
            }
            
            def sustainMs = (settings.threatSustainMins ?: 30) * 60000
            if (floodProb >= (state.peakFloodProb ?: 0)) { 
                state.peakFloodProb = floodProb
                state.peakFloodTime = now() 
            } else if (state.peakFloodTime && (now() - state.peakFloodTime) < sustainMs) { 
                floodProb = state.peakFloodProb
                diagList << [name: "Flood Hysteresis", status: "ACTIVE", effect: "Hold", desc: "Holding peak probability for ${settings.threatSustainMins ?: 30} mins."] 
            } else { 
                state.peakFloodProb = floodProb 
            }
            
            if (floodProb > 100) floodProb = 100
        }

        // 4. SEVERE HEAT DNA
        if (settings.enableHeat != false) {
            def added = 0
            def msg = "Heat Levels Normal"
            
            def heatBase = isMet ? 32.2 : 90.0
            def heatSevere = isMet ? 37.8 : 100.0
            def dpHigh = isMet ? 21.1 : 70.0
            
            if (t > heatBase) {
                added += 30
                msg = "Elevated Temperature (${String.format('%.1f', t)}°)"
                diagList << [name: "Heat: Base Temp", status: "ON", effect: "+30%", desc: msg]
                heatFactors++
            }
            if (t > heatSevere) {
                added += 30
                msg = "Dangerous Heat Threshold (${String.format('%.1f', t)}°)"
                diagList << [name: "Heat: Severe Temp", status: "ACTIVE", effect: "+30%", desc: msg]
                heatFactors++
            }
            if (dp > dpHigh) {
                added += 25
                msg = "Oppressive Humidity (DP: ${String.format('%.1f', dp)}°)"
                diagList << [name: "Heat: Moisture Load", status: "ACTIVE", effect: "+25%", desc: msg]
                heatFactors++
            }
            if (sensorLux && luxVal > 75000) {
                added += 15
                msg = "Intense Solar Radiation"
                diagList << [name: "Heat: Solar Index", status: "ON", effect: "+15%", desc: msg]
                heatFactors++
            }
            
            heatProb += added
            if (heatProb > 100) heatProb = 100
            
            def sustainMs = (settings.threatSustainMins ?: 30) * 60000
            if (heatProb >= (state.peakHeatProb ?: 0)) { 
                state.peakHeatProb = heatProb
                state.peakHeatTime = now() 
            } else if (state.peakHeatTime && (now() - state.peakHeatTime) < sustainMs) { 
                heatProb = state.peakHeatProb
                diagList << [name: "Heat Hysteresis", status: "ACTIVE", effect: "Hold", desc: "Holding peak probability for ${settings.threatSustainMins ?: 30} mins."] 
            } else { 
                state.peakHeatProb = heatProb 
            }
        }
    } else {
        if (state.hardwareAnomalyActive) {
            diagList << [name: "Engine Core", status: "OFFLINE", effect: "0%", desc: "Execution halted. Physically impossible hardware data detected."]
        } else {
            diagList << [name: "Engine Core", status: "OFFLINE", effect: "0%", desc: "Sensors Stale/Offline (No data received in ${staleMins} mins)"]
        }
    }

    state.tornadoProb = Math.round(torProb)
    state.tstormProb = Math.round(tsProb)
    state.floodProb = Math.round(floodProb)
    state.heatProb = Math.round(heatProb)
    state.algoDiagnostics = diagList

    def activeReasons = diagList.findAll { it.effect.contains("+") || it.effect.contains("-") || it.effect == "Hold" || it.effect.contains("Capped") }.collect { "${it.name} (${it.effect})" }
    state.logicReasoning = activeReasons.size() > 0 ? activeReasons.join(" | ") : "Stable conditions. No severe triggers active."
    
    def maxProb = Math.max(torProb, Math.max(tsProb, Math.max(floodProb, heatProb)))
    updateHistory("probHistory", maxProb, 86400000)

    // ==========================================================
    // ADVANCED CONTINUOUS TIMERS & TRANSITIONS
    // ==========================================================

    def targets = [:]
    def highestProbState = "Clear"
    def allClear = true

    ['Tornado', 'Thunderstorm', 'Flood', 'Heat'].each { haz ->
        def pfx = (haz == 'Thunderstorm') ? 'tstorm' : haz.toLowerCase()
        def prob = state."${pfx}Prob" ?: 0
        def wThresh = settings."${pfx}WatchThresh" ?: 60
        def aThresh = settings."${pfx}WarningThresh" ?: 85

        if (prob >= aThresh) { 
            targets[haz] = "WARNING"
            highestProbState = "WARNING"
            allClear = false
        } else if (prob >= wThresh) { 
            targets[haz] = "WATCH"
            if (highestProbState != "WARNING") highestProbState = "WATCH" 
            allClear = false
        } else { 
            targets[haz] = "Clear" 
        }
        
        if (prob < wThresh) {
            if (state."${pfx}BelowSince" == null) {
                state."${pfx}BelowSince" = now()
            } else if (!state."${pfx}WatchArmed" && (now() - state."${pfx}BelowSince") >= (settings.probResetMins ?: 120) * 60000) {
                state."${pfx}WatchArmed" = true
                logAction("${haz} probability stayed below watch threshold for ${settings.probResetMins ?: 120}m. Watch alerts re-armed.")
            }
        } else {
            if (!state."${pfx}WatchArmed" && state."${pfx}BelowSince" != null) {
                logAction("${haz} probability spiked above watch threshold before 120m elapsed. Resetting counter.")
            }
            state."${pfx}BelowSince" = null
        }
        
        if (targets[haz] == "Clear") {
            if (state."${pfx}ClearSince" == null) {
                state."${pfx}ClearSince" = now()
            } else if (!state."${pfx}WarningArmed" && (now() - state."${pfx}ClearSince") >= (settings.precipResetMins ?: 120) * 60000) {
                state."${pfx}WarningArmed" = true
                logAction("${haz} has been clear of severe thresholds for ${settings.precipResetMins ?: 120}m. Warning alerts re-armed.")
            }
        } else {
            state."${pfx}ClearSince" = null
        }
    }

    if (allClear) {
        if (state.globalClearSince == null) {
            state.globalClearSince = now()
        } else {
            def timeClear = now() - state.globalClearSince
            if (timeClear >= (settings.clearDelayMins ?: 60) * 60000 && !state.allClearSent) {
                state.allClearSent = true
                sendAlert("✅ Weather Update: All Severe Weather Conditions have been clear for ${settings.clearDelayMins ?: 60} minutes. All Clear.", settings.clearNotifyDevices, 0)
            }
        }
    } else {
        state.globalClearSince = null
        state.allClearSent = false
    }

    def debounceMs = (debounceMins ?: 5) * 60000
    def delayMs = (settings.alertDelaySeconds != null ? settings.alertDelaySeconds.toInteger() : 180) * 1000

    ['Tornado', 'Thunderstorm', 'Flood', 'Heat'].each { haz ->
        def target = targets[haz]
        def pfx = (haz == 'Thunderstorm') ? 'tstorm' : haz.toLowerCase()
        def current = state."${pfx}State" ?: "Clear"
        def timeSinceChange = now() - (state."${pfx}LastChange" ?: 0)
        
        def hazEmoji = ""
        if (haz == "Tornado") hazEmoji = "🌪️"
        else if (haz == "Thunderstorm") hazEmoji = "⛈️"
        else if (haz == "Flood") hazEmoji = "🌊"
        else if (haz == "Heat") hazEmoji = "🔥"
        
        def allowTransition = false
        def isUpgrade = (current == "Clear" && target != "Clear") || (current == "WATCH" && target == "WARNING")

        if (current != target && !isStale && !state.hardwareAnomalyActive) {
            
            // Wait for delay execution timer if the state is escalating
            if (isUpgrade) {
                if (state."${pfx}UpgradeTime" == null) {
                    state."${pfx}UpgradeTime" = now()
                    logAction("${haz} severity threshold reached. Starting ${(delayMs/1000).toInteger()}s verification delay.")
                    runIn((delayMs/1000).toInteger() > 0 ? (delayMs/1000).toInteger() : 1, "evalWrapper")
                } else if ((now() - state."${pfx}UpgradeTime") >= delayMs) {
                    allowTransition = true
                }
            } else {
                state."${pfx}UpgradeTime" = null // reset on downgrade attempt
                if (timeSinceChange >= debounceMs) { allowTransition = true } // Use debounce to filter rapid downgrades
            }
            
            if (allowTransition) {
                state."${pfx}UpgradeTime" = null
                logAction("${haz} shifted from ${current} to ${target}.")
                state."${pfx}State" = target
                state."${pfx}LastChange" = now()

                if (target == "Clear") {
                    safeOff(settings["switch${haz}Warning"])
                    safeOff(settings["switch${haz}Watch"])
                    
                    if (haz == "Thunderstorm" && state.peakTsProb >= 60) tuneSevereML("Thunderstorm", !state.tsConfirmed)
                    else if (haz == "Flood" && state.peakFloodProb >= 60) tuneSevereML("Flood", !state.floodConfirmed)
                    
                } else if (target == "WARNING") {
                    safeOn(settings["switch${haz}Warning"])
                    safeOff(settings["switch${haz}Watch"])
                    if (state."${pfx}WarningArmed") {
                        sendAlert("${hazEmoji} CRITICAL WARNING: ${haz} conditions actively detected!", settings."${pfx}WarningNotify", 3)
                        logAlertCause("${haz} WARNING", state."${pfx}Prob", state.logicReasoning)
                        state."${pfx}WarningArmed" = false 
                    } else {
                        logAction("Muted ${haz} WARNING alert: Has not been clear for ${settings.precipResetMins ?: 120}m.")
                    }
                } else if (target == "WATCH") {
                    safeOn(settings["switch${haz}Watch"])
                    safeOff(settings["switch${haz}Warning"])
                    if (state."${pfx}WatchArmed") {
                        sendAlert("${hazEmoji} WATCH: Elevated ${haz} probability detected.", settings."${pfx}WatchNotify", 2)
                        logAlertCause("${haz} WATCH", state."${pfx}Prob", state.logicReasoning)
                        state."${pfx}WatchArmed" = false 
                    } else {
                        logAction("Muted ${haz} WATCH alert: Has not been below threshold for ${settings.probResetMins ?: 120}m.")
                    }
                }
            }
        } else {
            state."${pfx}UpgradeTime" = null
        }
    }
}

def safeOn(dev) {
    if (dev && dev.currentValue("switch") != "on") {
        try { dev.on() } catch (e) { log.error "Failed to turn ON ${dev.displayName}: ${e.message}" }
    }
}

def safeOff(dev) {
    if (dev && dev.currentValue("switch") != "off") {
        try { dev.off() } catch (e) { log.error "Failed to turn OFF ${dev.displayName}: ${e.message}" }
    }
}

def sendAlert(msg, pushDevices, severity = 0) {
    if (settings.notifyModes && !settings.notifyModes.contains(location.mode)) {
        logDebug("Notification skipped: Current mode (${location.mode}) is not in allowed notification modes.")
        return
    }
    
    def cooldownMs = (settings.notificationCooldown ?: 60) * 60000
    def timeSinceLast = now() - (state.lastNotificationTime ?: 0)
    def lastSeverity = state.lastNotificationSeverity ?: 0
    
    if (timeSinceLast >= cooldownMs || severity > lastSeverity) {
        def sent = false
        if (pushDevices) {
            pushDevices.each { it.deviceNotification(msg) }
            sent = true
        }
        
        if (sent) {
            logAction("📣 Alert Sent: ${msg}")
            state.lastNotificationTime = now()
            state.lastNotificationSeverity = severity
        }
    } else {
        logAction("🔕 Alert Muted (General Anti-Spam Active): ${msg}")
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
