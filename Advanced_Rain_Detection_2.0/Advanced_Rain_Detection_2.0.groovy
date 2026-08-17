/**
 * Advanced Rain Detection 2.0
 */ 

definition(
    name: "Advanced Rain Detection 2.0",
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
    page(name: "configPage")
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
                    <th>Rain Prob</th>
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
            def match = hist?.findAll { (it.time as Long) <= tTime }?.max { it.time as Long }
            if (!match && hist?.size() > 0) match = hist.min { it.time as Long }
            return match ? String.format('%.2f', match.value as Float) : "--"
        }
        
        def pVal = findMatch(state.pressureHistory, entryTime)
        def sVal = findMatch(state.spreadHistory, entryTime)
        
        def probMatch = state.probHistory?.findAll { (it.time as Long) <= entryTime }?.max { it.time as Long }
        if (!probMatch && state.probHistory?.size() > 0) probMatch = state.probHistory.min { it.time as Long }
        
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
    
    def ws = state.weatherState ?: "Clear"
    def prob = state.rainProbability ?: 0
    def conf = state.confidenceScore ?: 0
    
    def postRainMs = (settings.postRainCooldown != null ? settings.postRainCooldown.toInteger() : 120) * 60000
    def isSuppressed = state.lastRainEndTime && (now() - state.lastRainEndTime) < postRainMs
    
    if (ws == "Raining") return "<span style='color:blue;'><b>Active Heavy Precipitation Detected.</b></span> (Confidence: ${conf}%)"
    if (ws == "Sprinkling") return "<span style='color:#007bff;'><b>Light Rain / Sprinkling Detected.</b></span> (Confidence: ${conf}%)"
    if (prob >= (settings.notifyProbThreshold ?: 85) && !isSuppressed) return "<span style='color:orange;'><b>High Probability Threat Active.</b></span> Rain is highly likely (${prob}%). Monitoring for physical detection."
    
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
                def tP = getMedianFloat(sensorTemp, ["temperature", "tempf"])
                def hP = getMedianFloat(sensorHum, ["humidity"])
                def pP = getMedianFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"])
                if (pP != null) pP += (settings.pressOffset ?: 0.0)
                
                def t = tP ?: 0.0
                def h = hP ?: 0.0
                def p = pP ?: 0.0

                if (settings.enableThermalSmoothing != false && state.smoothedTemp != null) {
                    t = state.smoothedTemp
                }

                def r = getMedianFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], 0.0)
                
                def lux = 0.0
                if (sensorLux) {
                    def illVal = getMedianFloat(sensorLux, ["illuminance"], null)
                    if (illVal != null) {
                        lux = illVal
                    } else {
                        def radVal = getMedianFloat(sensorLux, ["solarRadiation", "solarradiation"], null)
                        if (radVal != null) lux = radVal * 126.7
                    }
                }
                
                def wind = getMedianFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], "N/A")
                def windDir = getMedianFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], "N/A")
       
                def strikes = state.recentStrikes ?: 0
                def recentLightDist = 999.0
                if (state.lightningHistory?.size() > 0) {
                    state.lightningHistory.each { if (it.value < recentLightDist) recentLightDist = it.value }
                }
                def recentLightDistStr = strikes > 0 ? recentLightDist : "N/A"
                def lightVector = state.lightningVectorStr ?: "Gathering Data"
                
                def rainWeek = getMedianFloat(sensorRainWeekly, ["rainWeekly", "weeklyrainin", "weeklyWater"], 0.0)
                
                def wetCountRaw = [sensorLeak, sensorLeak2, sensorLeak3].count { it?.currentValue("water") == "wet" }
                def reqWets = settings.leakSensorRequiredCount ? settings.leakSensorRequiredCount.toInteger() : 1
                def rawLeakWet = (wetCountRaw >= reqWets)
                
                def vpd = state.currentVPD ?: 0.0
                def ah = state.currentAH ?: 0.0
                def dp = state.currentDewPoint ?: 0.0
                def wb = state.currentWetBulb ?: 0.0
                def lcl = state.cloudBaseMeters ?: 0.0
                def dpSpread = state.dewPointSpread ?: 0.0
                def pTrend = state.pressureTrendStr ?: "Stable"
                def tTrend = state.tempTrendStr ?: "Stable"
                def sTrend = state.spreadTrendStr ?: "Stable"
                def ahTrend = state.ahTrendStr ?: "Stable"
                def luxTrend = state.luxTrendStr ?: "N/A"
                def windTrend = state.windTrendStr ?: "N/A"
                def dryingRate = state.dryingPotential ?: "N/A"
                def ttd = state.timeToDryStr ?: "N/A"
         
                def prob = state.rainProbability ?: 0
                def confScore = state.confidenceScore ?: 0
                def confReason = state.confidenceReasoning ?: "Gathering logic consensus..."
                def activeState = state.weatherState ?: "Clear"
                def clearTime = state.expectedClearTime ?: "N/A"
                def reasoning = state.logicReasoning ?: "Waiting for initial sensor readings..."

                def currentMultiplier = state.calibrationMultiplier ?: 1.0
                def calibWarning = ""
                if (settings.enableAutoCalibration != false) {
                    if (currentMultiplier < 1.0) {
                        calibWarning = "<br><span style='color: #856404; font-size: 11px;'>⚠ Global Auto-Cal Penalty (${String.format('%.2f', currentMultiplier)}x) applied due to past false positives.</span>"
                    } else if (currentMultiplier > 1.0) {
                        calibWarning = "<br><span style='color: green; font-size: 11px;'>⬈ Global Auto-Cal Bonus (${String.format('%.2f', currentMultiplier)}x) applied due to past missed rain.</span>"
                    }
                }

                def speedUnit = isMetric() ? "km/h" : "mph"
                def distUnit = isMetric() ? "km" : "mi"
                
                def solarExpectedStr = "--"
                if (sensorLux) {
                    def expectedLux = state.currentExpectedLux ? Math.round(state.currentExpectedLux) : 0
                    def elev = state.currentSunElevation ? Math.round(state.currentSunElevation) : 0
                    solarExpectedStr = "${lux != "N/A" ? Math.round(lux as Float) : 0} lux <span style='font-size:11px; color:gray;'>(Expected: ~${expectedLux} @ ${elev}°)</span>"
                }

                def lightStr = "None Recent"
                if (sensorLightning && strikes > 0) {
                    def vecColor = lightVector.contains("Approaching") ? "red" : (lightVector.contains("Departing") ? "green" : "orange")
                    lightStr = "${strikes} strikes (3hr) | Closest: ${recentLightDistStr} ${distUnit} | <span style='color:${vecColor}; font-weight:bold;'>${lightVector}</span>"
                }
       
                def totalLeakSensors = [sensorLeak, sensorLeak2, sensorLeak3].count { it != null }
                def leakWetStr = "Not Configured"
                
                if (totalLeakSensors > 0) {
                    def activeSensorsText = "${wetCountRaw} of ${totalLeakSensors} WET"
                    
                    if (!rawLeakWet) {
                        leakWetStr = "<span style='color:green;'>DRY</span> <span style='font-size:11px; color:gray;'>(${activeSensorsText} - Requires ${reqWets})</span>"
                    } else {
                        if (state.dewRejectionActive) {
                            leakWetStr = "<span style='color:orange; font-weight:bold;'>IGNORED</span> <span style='font-size:11px; color:gray;'>(Physical: ${activeSensorsText} | Reason: Dew/Frost conditions detected)</span>"
                        } else if (state.stuckLeakActive) {
                            leakWetStr = "<span style='color:orange; font-weight:bold;'>IGNORED</span> <span style='font-size:11px; color:gray;'>(Physical: ${activeSensorsText} | Reason: Stuck WET timeout without rain gauge confirmation)</span>"
                        } else if (state.leakWetVerifying) {
                            leakWetStr = "<span style='color:purple; font-weight:bold;'>VERIFYING</span> <span style='font-size:11px; color:gray;'>(Physical: ${activeSensorsText} | Reason: Waiting 60s to confirm real drop vs debris)</span>"
                        } else {
                            leakWetStr = "<span style='color:blue; font-weight:bold;'>TRIGGERED</span> <span style='font-size:11px; color:gray;'>(Physical: ${activeSensorsText} | Reason: Valid instant rain verified)</span>"
                        }
                    }
                }
                
                def vpdColor = vpd < 0.5 ? "red" : (vpd < 1.0 ? "orange" : "green")
                def spreadColor = dpSpread < 3.0 ? "red" : (dpSpread < 6.0 ? "orange" : "green")
                def probColor = prob > 70 ? "red" : (prob > 40 ? "orange" : "black")
                def stateColor = activeState == "Clear" ? "green" : "blue"
                
                def recordInfo = state.recordRain ?: [date: "None", amount: 0.0]

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
                        <tr><td colspan="3" class="dash-subhead">Prediction & System State</td></tr>
                        <tr><td class="dash-hl">Weather State</td><td colspan="2" class="dash-val"><span style='color:${stateColor}; font-weight:bold;'>${activeState.toUpperCase()}</span></td></tr>
                        <tr><td class="dash-hl">Precipitation Chance</td><td colspan="2" class="dash-val"><span style='color:${probColor}; font-weight:bold;'>${prob}%</span>${calibWarning}</td></tr>
                        <tr><td class="dash-hl">Prediction Confidence</td><td colspan="2" class="dash-val"><b>${confScore}%</b></td></tr>
                        <tr><td class="dash-hl">Estimated Clear Time</td><td colspan="2" class="dash-val">${clearTime}</td></tr>
                        <tr><td class="dash-hl">Active Logic Triggers</td><td colspan="2" class="dash-val" style="font-size:12px;"><i>${reasoning}</i></td></tr>

                        <tr><td colspan="3" class="dash-subhead">Engine Accuracy & Machine Learning Tracking</td></tr>
                        <tr><td class="dash-hl">False Positives (Cried Wolf)</td><td colspan="2" class="dash-val"><span style='color:orange; font-weight:bold;'>${state.falsePositiveCount ?: 0}</span> <i style='font-size:11px; color:gray;'>(Penalizes specific modules)</i></td></tr>
                        <tr><td class="dash-hl">Missed Rain (Failed Alert)</td><td colspan="2" class="dash-val"><span style='color:red; font-weight:bold;'>${state.falseNegativeCount ?: 0}</span> <i style='font-size:11px; color:gray;'>(Boosts global sensitivity)</i></td></tr>
                        <tr><td class="dash-hl">Time-Healing (Decay)</td><td colspan="2" class="dash-val"><b>Active</b> <i style='font-size:11px; color:gray;'>(All penalties/bonuses shrink 2% toward 1.0x daily)</i></td></tr>

                        <tr><td colspan="3" class="dash-subhead">Core Environmental Sensors (Aggregated)</td></tr>
                        <tr><td class="dash-hl">Temperature</td><td><b>${String.format('%.1f', t)}°</b></td><td>${tTrend}</td></tr>
                        <tr><td class="dash-hl">Humidity</td><td><b>${String.format('%.1f', h)}%</b></td><td>Abs Hum: ${String.format('%.2f', ah)} g/m³ (${ahTrend})</td></tr>
                        <tr><td class="dash-hl">Barometric Pressure</td><td><b>${String.format('%.2f', p)}</b></td><td>${pTrend}</td></tr>
                        <tr><td class="dash-hl">Rain Gauge Rate</td><td colspan="2" class="dash-val"><b>${r}/hr</b></td></tr>
                        <tr><td class="dash-hl">First Drop Sensor(s)</td><td colspan="2" class="dash-val">${leakWetStr}</td></tr>
                        <tr><td class="dash-hl">Solar Radiation</td><td colspan="2" class="dash-val">${solarExpectedStr}</td></tr>
                        <tr><td class="dash-hl">Wind Dynamics</td><td>${wind != "N/A" ? wind + " " + speedUnit : "--"} @ ${windDir != "N/A" ? windDir + "°" : "--"}</td><td>Shift: ${state.windShiftDetected ? "<span style='color:red;'>Active Front</span>" : "Stable"}</td></tr>
                        <tr><td class="dash-hl">Lightning Vectoring</td><td colspan="2" class="dash-val">${lightStr}</td></tr>

                        <tr><td colspan="3" class="dash-subhead">Thermodynamic Calculations</td></tr>
                        <tr><td class="dash-hl">Cloud Base (LCL)</td><td colspan="2" class="dash-val"><b>~${Math.round(lcl)}m</b></td></tr>
                        <tr><td class="dash-hl">VPD (Drying Power)</td><td><span style='color:${vpdColor}; font-weight:bold;'>${String.format('%.2f', vpd)} kPa</span></td><td class="dash-val">${dryingRate}</td></tr>
                        <tr><td class="dash-hl">Dew Point Spread</td><td><span style='color:${spreadColor}; font-weight:bold;'>${String.format('%.1f', dpSpread)}°</span></td><td>Convergence: ${sTrend}</td></tr>
                        <tr><td class="dash-hl">Wet-Bulb Temp</td><td colspan="2" class="dash-val">${String.format('%.1f', wb)}°</td></tr>
                        <tr><td class="dash-hl">Est. Time to Dry</td><td colspan="2" class="dash-val">${ttd}</td></tr>
                        
                        <tr><td colspan="3" class="dash-subhead">Accumulation & Hardware</td></tr>
                        <tr><td class="dash-hl">Today's Total Rain</td><td colspan="2" class="dash-val">${state.currentDayRain ?: 0.0} (Week: ${rainWeek})</td></tr>
                        <tr><td class="dash-hl">All-Time Record</td><td colspan="2" class="dash-val"><span style='color:blue; font-weight:bold;'>${recordInfo.amount}</span> <i style='font-size:11px; color:gray;'>(${recordInfo.date})</i></td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML

                def logicPanel = "<div style='margin-top: 20px; padding: 15px; background: #e6f2ff; border-left: 5px solid #007bff; font-size: 13px; color: #004085;'>"
                logicPanel += "<h4 style='margin-top:0; border-bottom:1px solid #b8daff; padding-bottom:5px;'>Engine Diagnostics: Algorithm Decision Matrix</h4>"
                logicPanel += "<div style='max-height: 400px; overflow-y: auto; border: 1px solid #b8daff;'><table class='dash-table' style='margin-top:0; background: white; color: #333;'><thead style='position: sticky; top: 0; box-shadow: 0 1px 2px rgba(0,0,0,0.1);'><tr><th>Algorithm</th><th>Status</th><th>Effect</th><th>Diagnostic Output</th></tr></thead><tbody>"
                
                if (state.algoDiagnostics && state.algoDiagnostics.size() > 0) {
                    state.algoDiagnostics.each { diag ->
                        def eff = diag.effect ?: "0%"
                        def effColor = eff.contains("+") || eff.startsWith("x1.") || eff.startsWith("x2.") ? "red" : (eff.contains("-") || (eff.startsWith("x0.")) ? "green" : "black")
                        def statusColor = diag.status == "ON" ? "green" : (diag.status == "ACTIVE" ? "blue" : "gray")
                        logicPanel += "<tr><td style='font-weight:bold;'>${diag.name}</td><td style='color:${statusColor};'>${diag.status}</td><td style='color:${effColor}; font-weight:bold;'>${eff}</td><td>${diag.desc}</td></tr>"
                    }
                } else {
                    logicPanel += "<tr><td colspan='4'>Waiting for initial evaluation...</td></tr>"
                }
                
                logicPanel += "</tbody></table></div>"
                logicPanel += "<div style='margin-top:10px;'><b>Consensus & Confidence:</b> " + (state.confidenceReasoning ?: "Waiting for consensus...") + "</div>"
                logicPanel += "</div>"

                paragraph logicPanel

                def visualWidgets = ""
                def dispMode = settings.historyDisplayMode ?: "Data Table"
                if (dispMode == "Data Table") {
                    visualWidgets += renderTableHTML()
                }
                paragraph visualWidgets
                
                def rainSw = switchRaining?.currentValue("switch") == "on" ? "<span style='color:blue; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def sprinkSw = switchSprinkling?.currentValue("switch") == "on" ? "<span style='color:blue; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def probSw = switchProbable?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                
                paragraph "<div style='padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>" +
                          "<b>Virtual Output Switches:</b> Probable Threat: [${probSw}] | Sprinkling: [${sprinkSw}] | Heavy Rain: [${rainSw}]</div>"

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
            input "sensorTemp", "capability.sensor", title: "Outdoor Temperature Sensor(s)", required: true, multiple: true
            input "sensorHum", "capability.sensor", title: "Outdoor Humidity Sensor(s)", required: true, multiple: true
            input "sensorPress", "capability.sensor", title: "Barometric Pressure Sensor(s)", required: true, multiple: true
            paragraph "<span style='font-size: 12px; color: #555;'><i>Tip: Select multiple sensors here. The app will automatically discard extreme outliers and use the mathematical median for highest accuracy.</i></span>"
        }

        section("<b>2. Advanced Prediction Sensors (Optional)</b>", hideable: true, hidden: true) {
            input "sensorLux", "capability.illuminanceMeasurement", title: "Solar Radiation / Lux Sensor(s)", required: false, multiple: true
            input "sensorWind", "capability.sensor", title: "Wind Speed Sensor(s)", required: false, multiple: true
            input "sensorWindDir", "capability.sensor", title: "Wind Direction Sensor(s)", required: false, multiple: true
            input "sensorLightning", "capability.sensor", title: "Lightning Detector", required: false
            if (sensorLightning) {
                input "lightningStrikeThreshold", "number", title: "Minimum Lightning Strikes", defaultValue: 10
            }
        }
        
        section("<b>3. Precipitation & Accumulation Sensors (Optional)</b>", hideable: true, hidden: true) {
            input "sensorRain", "capability.sensor", title: "Rain Rate Sensor(s)", required: false, multiple: true
            input "sensorRainDaily", "capability.sensor", title: "Daily Rain Accumulation Sensor(s)", required: false, multiple: true
            input "sensorRainWeekly", "capability.sensor", title: "Weekly Rain Accumulation Sensor", required: false
        }

        section("<b>4. Instant 'First Drop' Sensors</b>", hideable: true, hidden: true) {
            input "sensorLeak", "capability.waterSensor", title: "Instant Rain Sensor 1 (e.g., exposed leak sensor)", required: false
            input "sensorLeak2", "capability.waterSensor", title: "Instant Rain Sensor 2", required: false
            input "sensorLeak3", "capability.waterSensor", title: "Instant Rain Sensor 3", required: false
            input "leakSensorRequiredCount", "enum", title: "Number of Instant Sensors required to trigger", options: ["1", "2", "3"], defaultValue: "1", required: true
            input "stuckLeakTimeout", "number", title: "Stuck Sensor Timeout (Minutes)", required: true, defaultValue: 60
        }
        
        section("<b>5. Algorithm Tuning & Toggles</b>", hideable: true, hidden: true) {
            input "pressOffset", "decimal", title: "Barometric MSLP Offset (inHg)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Calibrates raw station pressure to Mean Sea Level Pressure (MSLP) to ensure an accurate atmospheric baseline.</span>", defaultValue: 0.0
            input "enableLCLLogic", "bool", title: "Cloud Base Height (LCL) Virga Detection<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Calculates the lifting condensation level. Penalizes probability if the cloud base is too high, causing rain to evaporate before reaching the ground.</span>", defaultValue: true
            input "enableAutoCalibration", "bool", title: "Machine-Learning Auto-Calibration<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Tracks false alarms. Specifically identifies and penalizes the exact logic modules that caused the false positive to tune the system to your microclimate.</span>", defaultValue: true
            input "enableSeasonalProfiling", "bool", title: "Seasonal Storm Profiling<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Adjusts probability multipliers based on temperature profiles (e.g., convective pop-up storms vs stratiform frontal rain).</span>", defaultValue: true
            input "enableAccelerationLogic", "bool", title: "Pressure Acceleration<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Detects rapid accelerations in barometric pressure drops, often preceding severe squalls.</span>", defaultValue: true
            input "enableAbsHumLogic", "bool", title: "Moisture Advection<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Calculates the physical mass of water in the air to detect incoming deep moisture systems.</span>", defaultValue: true
            input "enableDPLogic", "bool", title: "Dew Point Convergence Velocity<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Monitors how fast the temperature and dew point are converging to predict air saturation.</span>", defaultValue: true
            input "enableVPDLogic", "bool", title: "VPD (Vapor Pressure Deficit)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Uses Vapor Pressure Deficit to penalize rain probability when the air is too dry to support precipitation.</span>", defaultValue: true
            input "enableWetBulbLogic", "bool", title: "Wet-Bulb Cooling (Rain Shafts)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Detects sudden temperature crashes toward the wet-bulb limit, indicative of nearby rain shafts.</span>", defaultValue: true
            input "enablePressureLogic", "bool", title: "Barometric Pressure Trends<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Evaluates standard barometric rising and falling trends to predict frontal approaches.</span>", defaultValue: true
            input "enableSynergyLogic", "bool", title: "Algorithmic Synergy (Multipliers)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Applies multiplier bonuses when multiple independent atmospheric conditions (e.g., pressure drop + wind shift) align.</span>", defaultValue: true
            input "enableAstronomicalSolar", "bool", title: "Astronomical Clear-Sky Modeling<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Models the expected clear-sky solar radiation for your exact latitude/longitude and time of day to accurately detect thick cloud cover.</span>", defaultValue: true
            input "enableCloudLogic", "bool", title: "Basic Cloud Density Logic<br><span style='font-size: 12px; color: #555; font-weight: normal;'>A simpler alternative to astronomical modeling that tracks daily local lux peaks to estimate cloud density.</span>", defaultValue: true
            input "enableSolarPlunge", "bool", title: "Solar Plunge Velocity<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Tracks rapid drops in daylight to detect towering anvil clouds before precipitation starts.</span>", defaultValue: true
            input "enableWindLogic", "bool", title: "Wind Gust Fronts<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Detects sudden high wind gusts that often precede a storm front.</span>", defaultValue: true
            input "enableGustFactor", "bool", title: "Outflow Boundary (Gust Factor)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Calculates the ratio of immediate gusts vs the 30-minute rolling average to detect approaching squall lines.</span>", defaultValue: true
            input "enableWindTroughLogic", "bool", title: "Wind Troughing<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Detects sudden lulls in wind speed paired with dropping pressure, a precursor to storm stalling.</span>", defaultValue: true
            input "enableWindShiftLogic", "bool", title: "Wind Direction Shift Logic<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Monitors for sudden >45° wind direction shifts, a key indicator of frontal passage.</span>", defaultValue: true
            input "enableLightningVectoring", "bool", title: "Forgiving Storm Vectoring<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Analyzes the mathematical trend of lightning strike distances to calculate if a storm core is approaching, departing, or stalled.</span>", defaultValue: true
            input "enableLightningLogic", "bool", title: "Standard Lightning Proximity<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Uses standard distance thresholds for lightning to increase rain probability if strikes are nearby.</span>", defaultValue: true
            input "enableThermalSmoothing", "bool", title: "Thermal Smoothing<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Applies a smoothing algorithm to temperature readings to ignore false rapid heating caused by direct sunlight hitting the sensor.</span>", defaultValue: true
            input "enableTimeToDry", "bool", title: "Time-to-Dry Estimator<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Calculates real-time evaporation rates based on VPD, solar, and wind to estimate how long surfaces will remain wet.</span>", defaultValue: true
            input "enableDewRejection", "bool", title: "Dew & Frost Rejection<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Ignores instant leak sensors if conditions strongly suggest morning dew or frost rather than actual precipitation.</span>", defaultValue: true
            input "enableStateOptimization", "bool", title: "Aggressive Data Pruning<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Reduces the number of historical data points stored in memory to prioritize hub performance and database health.</span>", defaultValue: true
            input "enableStaleCheck", "bool", title: "Stale Data Protection<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Suspends the prediction engine if sensors stop reporting to prevent runaway logic loops.</span>", defaultValue: true
            input "staleDataTimeout", "number", title: "Stale Data Timeout (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>The number of minutes without sensor updates before the system considers the data stale.</span>", defaultValue: 30
        }

        section("<b>6. Virtual Output Switches</b>", hideable: true, hidden: true) {
            input "switchProbable", "capability.switch", title: "Rain Probable Switch", required: false
            input "switchSprinkling", "capability.switch", title: "Sprinkling / Light Rain Switch", required: false
            input "switchRaining", "capability.switch", title: "Heavy Rain Switch", required: false
            input "debounceMins", "number", title: "State Debounce Time (Minutes)", required: true, defaultValue: 5
            input "heavyRainThreshold", "decimal", title: "Heavy Rain Rate Threshold", required: true, defaultValue: 0.1
        }
        
        section("<b>7. Custom Notifications & Announcements</b>", hideable: true, hidden: true) {
            input "notifyModes", "mode", title: "Only send alerts in these modes (Leave blank for all)", multiple: true, required: false
            input "notificationCooldown", "number", title: "General Notification Spam Cooldown (Minutes)", required: true, defaultValue: 60
            input "notifyProbThreshold", "number", title: "Rain Probability Setpoint (%)", required: true, defaultValue: 85
            
            paragraph "<hr><b>Advanced Muting & Resets</b>"
            input "probSustainMins", "number", title: "Threat Persistence / Hold Time (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>How long to hold a peak probability score before allowing it to drop. Prevents jittery drop-offs from fast-moving pop-up storms.</span>", required: true, defaultValue: 30
            input "probResetMins", "number", title: "Rain Probable Reset Cooldown (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Probability must stay BELOW the threshold for this many consecutive minutes before another alert is armed. Spiking above resets the counter.</span>", required: true, defaultValue: 120
            input "precipResetMins", "number", title: "Rain/Sprinkle Announcement Cooldown (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Must be clear for this many consecutive minutes before a new Rain or Sprinkling announcement is allowed.</span>", required: true, defaultValue: 120
            input "clearDelayMins", "number", title: "All Clear Delay (Minutes)<br><span style='font-size: 12px; color: #555; font-weight: normal;'>Must be clear for this many consecutive minutes before sending the All Clear announcement.</span>", required: true, defaultValue: 60
            input "postRainCooldown", "number", title: "Post-Rain 'Probable' Suppression (Minutes)", required: true, defaultValue: 120
            input "alertDelaySeconds", "number", title: "Alert Delay (Seconds)", required: true, defaultValue: 180
            
            paragraph "<hr><b>Rain Probable Alerts</b>"
            input "probNotifyDevices", "capability.notification", title: "Send Push Notification To:", multiple: true, required: false
            
            paragraph "<hr><b>Sprinkling / Light Rain Alerts</b>"
            input "sprinkleNotifyDevices", "capability.notification", title: "Send Push Notification To:", multiple: true, required: false
            
            paragraph "<hr><b>Heavy Rain Alerts</b>"
            input "rainNotifyDevices", "capability.notification", title: "Send Push Notification To:", multiple: true, required: false
            
            paragraph "<hr><b>Weather Cleared Alerts</b>"
            input "clearNotifyDevices", "capability.notification", title: "Send Push Notification To:", multiple: true, required: false
        }
        
        section("<b>8. UI Preferences</b>", hideable: true, hidden: true) {
            input "historyDisplayMode", "enum", title: "24-Hour History Display", options: ["Data Table", "Hidden"], defaultValue: "Data Table", required: true, submitOnChange: true
        }
        
        if (app.id) {
            section("<b>Global Actions & Overrides</b>", hideable: true, hidden: true) {
                input "forceEvalBtn", "button", title: "⚙️ Force Logic Evaluation"
                input "resetRecordBtn", "button", title: "🗑️ Reset All-Time Rain Record"
                input "clearStateBtn", "button", title: "⚠ Reset Internal State & History"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE (100% Local Predictive Engine)
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.alertCauseHistory) state.alertCauseHistory = []
    
    if (!state.weatherState) state.weatherState = "Clear"
    if (!state.lastStateChange) state.lastStateChange = now()
    if (!state.lastHeartbeat) state.lastHeartbeat = now()
    if (!state.confidenceScore) state.confidenceScore = 0
    if (!state.confidenceReasoning) state.confidenceReasoning = "Initializing..."
    if (!state.smoothedTemp) state.smoothedTemp = null
    state.alertPending = false
    state.lastRainEndTime = null
    
    state.probArmed = true
    state.stormArmed = true
    state.probBelowSince = now()
    state.precipEndedAt = now()
    state.allClearSent = true
    
    if (!state.algoDiagnostics) state.algoDiagnostics = []
    
    if (state.falsePositiveCount == null) state.falsePositiveCount = 0
    if (state.falseNegativeCount == null) state.falseNegativeCount = 0
    if (state.calibrationMultiplier == null) state.calibrationMultiplier = 1.0
    if (state.algoWeights == null) state.algoWeights = [:]
    if (state.activeModulesThisPrediction == null) state.activeModulesThisPrediction = []
    
    if (state.activePrediction == null) state.activePrediction = false
    if (state.rainOccurredDuringPrediction == null) state.rainOccurredDuringPrediction = false
    state.probableStartTime = null
    state.probThresholdCrossedTime = null
    
    state.evalPending = false
    state.lastEvalTime = 0
    state.hardwareAnomalyActive = false
    
    if (!state.pressureHistory) state.pressureHistory = []
    if (!state.tempHistory) state.tempHistory = []
    if (!state.ahHistory) state.ahHistory = []
    if (!state.luxHistory) state.luxHistory = []
    if (!state.windHistory) state.windHistory = []
    if (!state.windDirHistory) state.windDirHistory = []
    if (!state.spreadHistory) state.spreadHistory = []
    
    if (!state.lightningHistory) state.lightningHistory = []
    if (!state.strikeCountHistory) state.strikeCountHistory = []
    if (!state.recentStrikes) state.recentStrikes = 0
    
    if (!state.probHistory) state.probHistory = []
    
    if (!state.sevenDayRain) state.sevenDayRain = []
    if (!state.recordRain) state.recordRain = [date: "None", amount: 0.0]
    if (!state.currentDayRain) state.currentDayRain = 0.0
    if (!state.currentDateStr) state.currentDateStr = new Date().format("yyyy-MM-dd", location.timeZone)
    
    subscribeMulti(sensorTemp, ["temperature", "tempf"], "tempHandler")
    subscribeMulti(sensorHum, ["humidity"], "stdHandler")
    subscribeMulti(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], "pressureHandler")
    subscribeMulti(sensorLux, ["illuminance", "solarradiation", "solarRadiation"], "luxHandler")
    subscribeMulti(sensorWind, ["windSpeed", "windspeedmph", "wind"], "windHandler")
    subscribeMulti(sensorWindDir, ["windDirection", "winddir", "windDir"], "windDirHandler")
    
    subscribeMulti(sensorLightning, ["lightningDistance", "distance"], "lightningHandler")
    subscribeMulti(sensorLightning, ["lightningStrikeCount", "strikeCount", "strikes"], "strikeCountHandler")
    
    subscribeMulti(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], "stdHandler")
    subscribeMulti(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], "stdHandler")
    
    [sensorLeak, sensorLeak2, sensorLeak3].each { dev ->
        if (dev) subscribe(dev, "water", "stdHandler")
    }
    
    runEvery5Minutes("queueEval")
    
    logAction("Advanced Rain Detection Initialized.")
    queueEval()
}

def isMetric() { return location?.temperatureScale == "C" }

def subscribeMulti(devices, attrs, handler) {
    if (!devices) return
    def devList = devices instanceof List ? devices : [devices]
    devList.each { dev ->
        attrs.each { attr -> subscribe(dev, attr, handler) }
    }
}

// Fallback logic for single hardware sensors like lightning
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

// Median filter for multiple sensors to reject outliers automatically
def getMedianFloat(devices, attrs, fallbackStr = null) {
    if (!devices) return fallbackStr
    def vals = []
    def devList = devices instanceof List ? devices : [devices]
    
    devList.each { dev ->
        for (attr in attrs) {
            def val = dev.currentValue(attr)
            if (val != null) {
                try { 
                    vals << val.toString().replaceAll("[^\\d.-]", "").toFloat()
                    break // Stop iterating attrs for this specific device if we got a valid reading
                } catch (e) {}
            }
        }
    }
    
    if (vals.size() == 0) return fallbackStr
    if (vals.size() == 1) return vals[0]
    
    vals.sort()
    if (vals.size() % 2 == 0) {
        return (vals[(vals.size().intdiv(2)) - 1] + vals[vals.size().intdiv(2)]) / 2.0
    } else {
        return vals[vals.size().intdiv(2)]
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

void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard data manually refreshed by user.")
    }
    else if (btn == "forceEvalBtn") { logAction("MANUAL OVERRIDE: Forcing logic evaluation."); evaluateWeather() }
    else if (btn == "resetRecordBtn") {
        logAction("MANUAL OVERRIDE: All-Time Rain Record Reset.")
        state.recordRain = [date: "None", amount: 0.0]
        evaluateWeather()
    }
    else if (btn == "resetCauseHistoryBtn") {
        state.alertCauseHistory = []
        logAction("Alert cause history cleared.")
    }
    else if (btn == "clearStateBtn") {
        logAction("EMERGENCY RESET: Purging history, records, and resetting switches.")
        state.weatherState = "Clear"
        state.pressureHistory = []
        state.tempHistory = []
        state.ahHistory = []
        state.luxHistory = []
        state.windHistory = []
        state.windDirHistory = []
        state.spreadHistory = []
        state.lightningHistory = []
        state.strikeCountHistory = []
        state.recentStrikes = 0
        state.probHistory = []
        state.sevenDayRain = []
        state.recordRain = [date: "None", amount: 0.0]
        state.currentDayRain = 0.0
        
        state.notifiedProb = false
        state.alertPending = false
        
        state.falsePositiveCount = 0
        state.falseNegativeCount = 0
        state.calibrationMultiplier = 1.0
        state.algoWeights = [:]
        state.activeModulesThisPrediction = []
        state.activePrediction = false
        state.rainOccurredDuringPrediction = false
        
        state.probableStartTime = null
        state.probThresholdCrossedTime = null
        state.lastRainEndTime = null
        
        state.peakProb = 0
        state.peakProbTime = null
        
        state.probArmed = true
        state.stormArmed = true
        state.probBelowSince = now()
        state.precipEndedAt = now()
        state.allClearSent = true
        
        state.lastNotificationTime = 0
        state.lastNotificationSeverity = 0
        state.hardwareAnomalyActive = false
        
        unschedule("evalWrapper")
        state.evalPending = false
        state.confidenceScore = 0
        state.confidenceReasoning = "System reset."
        state.smoothedTemp = null
        state.algoDiagnostics = []
        state.alertCauseHistory = []
        
        safeOff(switchSprinkling)
        safeOff(switchRaining)
        safeOff(switchProbable)
        evaluateWeather()
    }
    else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    }
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
            logAction("⚠ Hardware Anomaly: '${historyName}' physically impossible jump (${cleanVal}).")
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
def luxHandler(evt) { updateHistory("luxHistory", evt.value, 86400000); queueEval() }
def windHandler(evt) { updateHistory("windHistory", evt.value, 86400000); queueEval() }
def windDirHandler(evt) { updateHistory("windDirHistory", evt.value, 86400000); queueEval() }

def lightningHandler(evt) { 
    if (evt.isStateChange) {
        updateHistory("lightningHistory", evt.value, 10800000) 
        queueEval() 
    }
}

def strikeCountHandler(evt) { updateHistory("strikeCountHistory", evt.value, 86400000); queueEval() }

def logProbabilityHistory() { updateHistory("probHistory", state.rainProbability ?: 0, 86400000) }

def logAlertCause(type, probScore, reasons) {
    def hist = state.alertCauseHistory ?: []
    def timeStr = new Date().format("MM/dd hh:mm a", location.timeZone)
    hist.add(0, "<b>[${timeStr}] ${type} Alert Triggered (${probScore}%)</b><br><i>Primary Factors:</i> ${reasons}")
    if (hist.size() > 20) hist = hist[0..19]
    state.alertCauseHistory = hist
}

def calculateVPD(tVal, rh) {
    def tC = isMetric() ? tVal : (tVal - 32.0) * (5.0 / 9.0)
    def svp = 0.61078 * Math.exp((17.27 * tC) / (tC + 237.3))
    def avp = svp * (rh / 100.0)
    return svp - avp
}

def calculateAbsoluteHumidity(tVal, rh) {
    def tC = isMetric() ? tVal : (tVal - 32.0) * (5.0 / 9.0)
    def ah = (6.112 * Math.exp((17.67 * tC) / (tC + 243.5)) * rh * 2.1674) / (273.15 + tC)
    return ah
}

def calculateDewPoint(tVal, rh) {
    def tC = isMetric() ? tVal : (tVal - 32.0) * (5.0 / 9.0)
    def gamma = Math.log(rh / 100.0) + ((17.62 * tC) / (243.12 + tC))
    def dpC = (243.12 * gamma) / (17.62 - gamma)
    return isMetric() ? dpC : (dpC * (9.0 / 5.0)) + 32.0
}

def calculateWetBulb(tVal, rh) {
    def tC = isMetric() ? tVal : (tVal - 32.0) * (5.0 / 9.0)
    def twC = tC * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(tC + rh) - Math.atan(rh - 1.676331) + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035
    return isMetric() ? twC : (twC * (9.0 / 5.0)) + 32.0
}

def calculateSolarData(lat, lon) {
    def cal = Calendar.getInstance(location.timeZone)
    def dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    def hour = cal.get(Calendar.HOUR_OF_DAY) + (cal.get(Calendar.MINUTE) / 60.0)
    def gamma = (2.0 * Math.PI / 365.0) * (dayOfYear - 1 + (hour - 12) / 24.0)
    def eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma) - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma))
    def decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma) - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma) - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma)
    
    def timeOffset = eqTime + (4 * lon) - (location.timeZone.rawOffset / 60000.0)
    def trueSolarTime = hour * 60.0 + timeOffset
    def solarHourAngle = (trueSolarTime / 4.0) - 180.0
    
    def latRad = Math.toRadians(lat)
    def haRad = Math.toRadians(solarHourAngle)
    
    def sinZenith = Math.sin(latRad) * Math.sin(decl) + Math.cos(latRad) * Math.cos(decl) * Math.cos(haRad)
    def elevationAngle = Math.toDegrees(Math.asin(sinZenith))
    
    def maxLux = 0
    if (elevationAngle > 0) {
        maxLux = 110000 * Math.sin(Math.toRadians(elevationAngle))
    }
    return [elevation: elevationAngle, expectedLux: maxLux]
}

def getStormVectorData(hist) {
    if (!hist || hist.size() < 4) return [status: "Gathering Data", speed: 0.0, eta: -1]
    
    def cutoff = now() - 10800000
    def recentHist = hist.findAll { it.time >= cutoff }
    
    if (recentHist.size() < 4) return [status: "Gathering Data", speed: 0.0, eta: -1]
    
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

def getTrendData(hist, minTimeHr) {
    if (!hist || hist.size() < 2) return [rate: 0.0, diff: 0.0, str: "Gathering Data"]
    def oldest = hist.first()
    def newest = hist.last()
    def diff = newest.value - oldest.value
    def timeSpanHr = (newest.time - oldest.time) / 3600000.0
    if (timeSpanHr < minTimeHr) return [rate: 0.0, diff: diff, str: "Stable (<${Math.round(minTimeHr*60)}m data)"]
    def ratePerHour = diff / timeSpanHr
    return [rate: ratePerHour, diff: diff, str: "${diff > 0 ? '+' : ''}${String.format('%.2f', ratePerHour)}/hr"]
}

def getAccelerationData(hist) {
    if (!hist || hist.size() < 4) return 0.0
    def cutoff = now() - 1800000
    def recent = hist.findAll { it.time >= cutoff }
    def older = hist.findAll { it.time < cutoff && it.time >= now() - 3600000 }
    if (recent.size() < 2 || older.size() < 2) return 0.0
    
    def recentTrend = getTrendData(recent, 0.1)
    def olderTrend = getTrendData(older, 0.1)
    return recentTrend.rate - olderTrend.rate
}

def getAngularDiff(angle1, angle2) {
    def diff = Math.abs(angle1 - angle2) % 360
    return diff > 180 ? 360 - diff : diff
}

def evaluateWeather() {
    state.lastEvalTime = now()
    
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    def currentHour = new Date().format("H", location.timeZone).toInteger()
    def isMorningRadiationalCooling = (currentHour >= 4 && currentHour < 10)

    if (!state.currentDateStr) state.currentDateStr = todayStr
    
    if (state.currentDateStr != todayStr) {
        def yesterdayTotal = state.currentDayRain ?: 0.0
        def hist = state.sevenDayRain ?: []
        hist.add(0, [date: state.currentDateStr, amount: yesterdayTotal])
 
        if (hist.size() > 7) hist = hist[0..6]
        state.sevenDayRain = hist
 
        def record = state.recordRain ?: [date: "None", amount: 0.0]
        if (yesterdayTotal > (record.amount ?: 0.0)) {
            state.recordRain = [date: state.currentDateStr, amount: yesterdayTotal]
            logAction("🏆 New All-Time Record Rainfall! ${yesterdayTotal} on ${state.currentDateStr}")
        }
        
        // --- TIME-HEALING DECAY LOGIC (Daily Reset) ---
        if (settings.enableAutoCalibration != false) {
            def gMult = state.calibrationMultiplier ?: 1.0
            if (gMult > 1.0) state.calibrationMultiplier = Math.max(1.0, gMult - 0.02)
            else if (gMult < 1.0) state.calibrationMultiplier = Math.min(1.0, gMult + 0.02)
            
            def healedMods = []
            if (state.algoWeights) {
                state.algoWeights.each { k, v ->
                    if (v < 1.0) {
                        state.algoWeights[k] = Math.min(1.0, v + 0.02)
                        healedMods << k
                    }
                }
            }
            if (healedMods.size() > 0 || gMult != 1.0) {
                logAction("Time-Healing: Daily decay applied to multipliers toward baseline 1.0x.")
            }
        }
        
        state.currentDateStr = todayStr
        state.currentDayRain = 0.0
    }
 
    def currentDaily = getMedianFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
    if (currentDaily > (state.currentDayRain ?: 0.0)) {
       state.currentDayRain = currentDaily
    }

    if (!sensorTemp || !sensorHum || !sensorPress) return

    def staleMins = settings.staleDataTimeout != null ? settings.staleDataTimeout.toInteger() : 30
    def isStale = (settings.enableStaleCheck != false) && ((now() - (state.lastHeartbeat ?: now())) > (staleMins * 60000))
    state.isStale = isStale
    
    def t = getMedianFloat(sensorTemp, ["temperature", "tempf"])
    def h = getMedianFloat(sensorHum, ["humidity"])
    def p = getMedianFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"])
    
    if (p != null) p += (settings.pressOffset ?: 0.0)
    
    if (t == null) t = 0.0
    if (h == null) h = 0.0
    if (p == null) p = 0.0
    
    def metric = isMetric()
    def tDropAnomaly = metric ? 1.7 : 3.0
    def spreadCrit = metric ? 0.8 : 1.0 
    def spreadTight = metric ? 2.8 : 3.5 
    def spreadDew = metric ? 3.0 : 5.5 
    
    def sTrendConv = metric ? -1.1 : -2.0
    def sTrendRapid = metric ? -1.7 : -3.0
    def tTrendRapid = metric ? -1.7 : -3.0
    def tTrendSevere = metric ? -2.2 : -4.0
    def wbDiff = metric ? 1.7 : 3.0
    def pDropSevere = metric ? -1.35 : -0.06 
    def pDropMod = metric ? -0.68 : -0.04 
    def pAccelSevere = metric ? -1.0 : -0.03
    def pRiseStrong = metric ? 1.0 : 0.03
    def pDropMild = metric ? -0.34 : -0.02 
    def pRiseMild = metric ? 0.68 : 0.02
    
    def windCalm = metric ? 4.0 : 2.5 
    def windSteady = metric ? 8.0 : 5.0
    def windSpike = metric ? 16.0 : 10.0
    def windHigh = metric ? 24.0 : 15.0
    def windMult = metric ? 0.0186 : 0.03
    def lightNear = metric ? 16.0 : 10.0
    def lightAppr = metric ? 40.0 : 25.0

    def th_windGustBase = metric ? 32.1 : 20.0

    def smoothedAnomaly = false
    if (settings.enableThermalSmoothing != false) {
        def lastT = state.smoothedTemp != null ? state.smoothedTemp : t
        def delta = Math.abs(t - lastT)
        if (delta > tDropAnomaly && state.tempHistory?.size() > 0) {
            t = lastT + ((t - lastT) * 0.3)
            smoothedAnomaly = true
        }
        state.smoothedTemp = t
    }

    def r = getMedianFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], 0.0)
    
    def luxVal = 0.0
    if (sensorLux) {
        def illVal = getMedianFloat(sensorLux, ["illuminance"], null)
        if (illVal != null) {
            luxVal = illVal
        } else {
            def radVal = getMedianFloat(sensorLux, ["solarRadiation", "solarradiation"], null)
            if (radVal != null) luxVal = radVal * 126.7
            else luxVal = 0.0
        }
    }
    
    def windVal = getMedianFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], 0.0)
    def windGustVal = getMedianFloat(sensorWind, ["windGust", "windgustmph"], windVal)
    def windDirVal = getMedianFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], 0.0)
    
    def lightDist = getFloat(sensorLightning, ["lightningDistance", "distance"], 999.0)
    
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
    def strikeCount = state.recentStrikes

    def closestLightning = lightDist
    if (state.lightningHistory?.size() > 0) {
        state.lightningHistory.each { if (it.value < closestLightning) closestLightning = it.value }
    }
    
    def vpd = calculateVPD(t, h)
    state.currentVPD = vpd
    def dp = calculateDewPoint(t, h)
    state.currentDewPoint = dp
    def wb = calculateWetBulb(t, h)
    state.currentWetBulb = wb
    def ah = calculateAbsoluteHumidity(t, h)
    state.currentAH = ah
    updateHistory("ahHistory", ah, 86400000)
    
    // Cloud Base Calculation (LCL)
    def tC_LCL = isMetric() ? t : (t - 32.0) * (5.0 / 9.0)
    def dpC_LCL = isMetric() ? dp : (dp - 32.0) * (5.0 / 9.0)
    def lclMeters = 125.0 * (tC_LCL - dpC_LCL)
    if (lclMeters < 0) lclMeters = 0
    state.cloudBaseMeters = lclMeters
    
    def dpSpread = t - dp
    if (dpSpread < 0) dpSpread = 0.0
    state.dewPointSpread = dpSpread
    updateHistory("spreadHistory", dpSpread, 86400000)
    
    def pTrendData = getTrendData(state.pressureHistory, 0.25)
    def pAccel = getAccelerationData(state.pressureHistory)
    def tTrendData = getTrendData(state.tempHistory, 0.16)
    def sTrendData = getTrendData(state.spreadHistory, 0.16)
    def lTrendData = getTrendData(state.luxHistory, 0.16)
    def wTrendData = getTrendData(state.windHistory, 0.16)
    def ahTrendData = getTrendData(state.ahHistory, 0.25)
    
    state.pressureTrendStr = pTrendData.str
    state.tempTrendStr = tTrendData.str
    state.spreadTrendStr = sTrendData.str
    state.ahTrendStr = ahTrendData.str
    state.luxTrendStr = sensorLux ? lTrendData.str : "N/A"
    state.windTrendStr = sensorWind ? wTrendData.str : "N/A"

    def vectorData = getStormVectorData(state.lightningHistory)
    if (sensorLightning && strikeCount > 0) {
        if (vectorData.status == "Approaching") state.lightningVectorStr = "Approaching @ ${String.format('%.1f', vectorData.speed)} mph"
        else if (vectorData.status == "Departing") state.lightningVectorStr = "Departing @ ${String.format('%.1f', vectorData.speed)} mph"
        else state.lightningVectorStr = vectorData.status
    } else {
        state.lightningVectorStr = "N/A"
    }

    if (settings.enableAstronomicalSolar != false && sensorLux) {
        def targetLat = location.latitude ?: 39.8283
        def targetLon = location.longitude ?: -98.5795
        def solarMap = calculateSolarData(targetLat, targetLon)
        state.currentSunElevation = solarMap.elevation
        state.currentExpectedLux = solarMap.expectedLux
    } else {
        state.currentSunElevation = 0
        state.currentExpectedLux = 0
    }

    def isPressureVerified = true
    if (pTrendData.rate <= pDropMod || pAccel <= pAccelSevere) {
        if (!state.pressureDropStartTime) state.pressureDropStartTime = now()
        if ((now() - state.pressureDropStartTime) < 300000) { 
            isPressureVerified = false
        }
    } else {
        state.pressureDropStartTime = null
    }

    state.windShiftDetected = false
    if (sensorWindDir && settings.enableWindShiftLogic != false && state.windDirHistory && state.windDirHistory.size() > 5) {
        def cutoff = now() - 1800000
        def pastReading = state.windDirHistory.find { (it.time as Long) <= cutoff }
        if (pastReading) {
            def shift = getAngularDiff(pastReading.value as Float, windDirVal as Float)
            if (shift >= 45.0) state.windShiftDetected = true
        }
    }
    
    def wetCountRaw = [sensorLeak, sensorLeak2, sensorLeak3].count { it?.currentValue("water") == "wet" }
    def reqWets = settings.leakSensorRequiredCount ? settings.leakSensorRequiredCount.toInteger() : 1
    def rawLeakWet = (wetCountRaw >= reqWets)
    
    if (rawLeakWet) {
        if (!state.leakWetStartTime) state.leakWetStartTime = now()
    } else {
        state.leakWetStartTime = null
    }

    def stuckLeakTimeoutMins = settings.stuckLeakTimeout != null ? settings.stuckLeakTimeout.toInteger() : 60
    def stuckLeakActive = false
    if (rawLeakWet && state.leakWetStartTime && r == 0) {
        if ((now() - state.leakWetStartTime) > (stuckLeakTimeoutMins * 60000)) stuckLeakActive = true
    }
    state.stuckLeakActive = stuckLeakActive
    
    def leakDelayMet = false
    if (rawLeakWet) {
        if (wetCountRaw >= 3) {
            leakDelayMet = true
        } else if (state.leakWetStartTime && (now() - state.leakWetStartTime) >= 60000) {
            leakDelayMet = true
        } else {
            runIn(60, "evaluateWeather")
        }
    }
    state.leakWetVerifying = (rawLeakWet && !leakDelayMet && !stuckLeakActive)
    
    def leakWet = rawLeakWet && leakDelayMet && !stuckLeakActive
    def dewRejectionActive = false
    
    if (leakWet && settings.enableDewRejection != false) {
        def checkLux = sensorLux ? (luxVal < 100) : true
        def checkWind = sensorWind ? (windVal < windSteady) : true
        def isDewHours = (currentHour >= 20 || currentHour <= 9)
        
        if (checkLux && checkWind && dpSpread <= spreadDew && isDewHours) {
            leakWet = false
            dewRejectionActive = true
        }
    }
    state.dewRejectionActive = dewRejectionActive
    
    def evapIndex = vpd
    if (sensorWind) evapIndex += (windVal * windMult) 
    if (sensorLux) evapIndex += (luxVal / 80000.0)
 
    if (r > 0 || leakWet) state.dryingPotential = "<span style='color:blue;'>Raining (No Drying)</span>"
    else if (evapIndex < 0.3) state.dryingPotential = "<span style='color:red;'>Very Low (Ground stays wet)</span>"
    else if (evapIndex < 0.8) state.dryingPotential = "<span style='color:orange;'>Moderate (Slow drying)</span>"
    else if (evapIndex < 1.5) state.dryingPotential = "<span style='color:green;'>High (Good drying conditions)</span>"
    else state.dryingPotential = "<span style='color:#008800; font-weight:bold;'>Very High (Rapid evaporation)</span>"
    
    def ttdStr = "Dry"
    if (settings.enableTimeToDry != false) {
        def totalRain = state.currentDayRain ?: 0.0
        if (r > 0 || leakWet) {
            ttdStr = "Raining..."
        } else if (totalRain > 0 && evapIndex > 0) {
            def evapPerHour = evapIndex * 0.02
            if (evapPerHour < 0.01) evapPerHour = 0.01
            def hours = totalRain / evapPerHour
            if (hours > 72) ttdStr = "> 3 Days"
            else if (hours < 0.5) ttdStr = "< 30 mins"
            else ttdStr = "~${String.format('%.1f', hours)} hrs"
        } else if (totalRain > 0) {
            ttdStr = "Stagnant (No Evaporation)"
        }
    }
    state.timeToDryStr = ttdStr

    // --- Logic Interlock: State Awareness ---
    def isPostRainSuppressed = state.lastRainEndTime && (now() - state.lastRainEndTime) < ((settings.postRainCooldown != null ? settings.postRainCooldown.toInteger() : 120) * 60000)
    
    def probability = 0.0
    def activeFactors = 0
    def activeFactorNames = []
    def totalModelsEnabled = 0
    def vectorBypass = false
    def diagList = []
    
    def th_tPulse = metric ? 33.3 : 92.0 
    def th_dpPulse = metric ? 23.3 : 74.0 
    def isPulsePowderKeg = (t >= th_tPulse && dp >= th_dpPulse && pTrendData.rate > -0.04 && windVal < 15.0)

    if (state.algoWeights == null) state.algoWeights = [:]

    // ML Auto-Caliberation Helper
    def applyWeight = { name, addedVal ->
        if (addedVal <= 0 || settings.enableAutoCalibration == false) return addedVal
        def weights = state.algoWeights ?: [:]
        def w = weights[name] != null ? weights[name] : 1.0
        if (w < 1.0 && addedVal > 0) return Math.round(addedVal * w)
        return addedVal
    }
    def recordDiag = { name, addedVal, msg ->
        def weights = state.algoWeights ?: [:]
        def w = weights[name] != null ? weights[name] : 1.0
        def effStr = addedVal > 0 ? "+${addedVal}%" : "0%"
        if (w < 1.0 && addedVal > 0) effStr += " <span style='color:orange;'>(x${String.format('%.2f', w)} penalty)</span>"
        diagList << [name: name, status: "ON", effect: effStr, desc: msg]
    }

    // Evaluate probability ONLY if not currently raining and not in cooldown
    if (!isStale && !state.hardwareAnomalyActive && state.weatherState == "Clear" && !isPostRainSuppressed) {
        
        if (smoothedAnomaly) {
            diagList << [name: "Thermal Smoothing", status: "ON", effect: "0%", desc: "Spike Ignored: Temp mathematically smoothed to ${String.format('%.1f', t)}°"]
        } else if (settings.enableThermalSmoothing != false) {
            diagList << [name: "Thermal Smoothing", status: "ON", effect: "0%", desc: "Inactive (Raw temp is stable)"]
        }

        def solarPlungeDetected = false
        def gustFactorDetected = false

        if (settings.enableLCLLogic != false) {
            totalModelsEnabled++
            def added = 0
            def msg = "Stable (Cloud Base: ~${Math.round(lclMeters)}m)"
            if (lclMeters > 2500) {
                added -= 20
                msg = "Cloud base extremely high (>2500m). Strong Virga (Evaporation) penalty applied."
            }
            diagList << [name: "Cloud Base Height (LCL)", status: "ON", effect: added == 0 ? "0%" : "${added}%", desc: msg]
            probability += added
        } else { diagList << [name: "Cloud Base Height (LCL)", status: "OFF", effect: "--", desc: "Disabled"] }

        if (settings.enableDPLogic != false) {
            totalModelsEnabled++
            def added = 0
            def msg = "Stable (${String.format('%.1f', dpSpread)}° spread)"
            if (dpSpread <= spreadCrit) { 
                added += isMorningRadiationalCooling ? 15 : 40; 
                msg = isMorningRadiationalCooling ? "Critical: Air Saturated (Morning Suppressed)" : "Critical: Air Saturated"; 
                activeFactors++; activeFactorNames << "Dew Point Convergence" 
            }
            else if (dpSpread <= spreadTight) { 
                added += isMorningRadiationalCooling ? 5 : 20; 
                msg = isMorningRadiationalCooling ? "Spread tightening (Morning Suppressed)" : "Spread tightening"; 
                activeFactors++; activeFactorNames << "Dew Point Convergence" 
            }
            if (sTrendData.rate <= sTrendRapid) { 
                added += isMorningRadiationalCooling ? 10 : 30; 
                msg += " | Rapid Convergence" + (isMorningRadiationalCooling ? " (Suppressed)" : ""); 
                activeFactors++; activeFactorNames << "Dew Point Convergence" 
            }
            added = applyWeight("Dew Point Convergence", added)
            recordDiag("Dew Point Convergence", added, msg)
            probability += added
        } else { diagList << [name: "Dew Point Convergence", status: "OFF", effect: "--", desc: "Disabled"] }
        
        if (settings.enableAbsHumLogic != false) {
            totalModelsEnabled++
            def added = 0
            def msg = "Stable (AH Trend: ${ahTrendData.str})"
            if (ahTrendData.rate > 0.3 && pTrendData.rate <= pDropMild) {
                added += 35
                msg = "Rising humidity with falling pressure"
                activeFactors++; activeFactorNames << "Moisture Advection"
            }
            added = applyWeight("Moisture Advection", added)
            recordDiag("Moisture Advection", added, msg)
            probability += added
        } else { diagList << [name: "Moisture Advection", status: "OFF", effect: "--", desc: "Disabled"] }
        
        if (settings.enableVPDLogic != false) {
            totalModelsEnabled++
            def added = 0
            def msg = "Stable (${String.format('%.2f', vpd)} kPa)"
            
            if (vpd < 0.2) { 
                added += isMorningRadiationalCooling ? 5 : 20; 
                msg = isMorningRadiationalCooling ? "VPD extremely low (Morning Suppressed)" : "VPD extremely low (Air saturated)"; 
                activeFactors++; activeFactorNames << "VPD" 
            } else if (vpd > 1.0) { 
                def isCloudy = sensorLux ? (luxVal < 40000) : true 
                
                if (strikeCount > 0 && isCloudy) {
                    msg = "VPD High (Dry Air Penalty suspended due to lightning)"
                } else {
                    added -= 20; msg = "VPD High (Dry air penalty maintained: Solar overrides strikes)" 
                }
            }
            added = applyWeight("VPD", added)
            recordDiag("VPD", added, msg)
            probability += added
        } else { diagList << [name: "VPD", status: "OFF", effect: "--", desc: "Disabled"] }
        
        if (settings.enableWetBulbLogic != false) {
            totalModelsEnabled++
            def added = 0
            def msg = "Stable (Temp is ${String.format('%.1f', (t - wb))}° above Wet-Bulb)"
            if (tTrendData.rate <= tTrendSevere && (t - wb) <= wbDiff) { 
                added += 40; msg = "Rain Shaft Detected! Temp crashing toward WB"; activeFactors++; activeFactorNames << "Wet-Bulb Cooling" 
            }
            added = applyWeight("Wet-Bulb Cooling", added)
            recordDiag("Wet-Bulb Cooling", added, msg)
            probability += added
        } else { diagList << [name: "Wet-Bulb Cooling", status: "OFF", effect: "--", desc: "Disabled"] }
        
        if (settings.enablePressureLogic != false) {
            totalModelsEnabled++
            def added = 0
            def msg = "Stable (Trend: ${pTrendData.str})"
            if (pTrendData.rate <= pDropSevere) { 
                if (isPressureVerified) {
                    added += 30; msg = "Pressure dropping rapidly"; activeFactors++; activeFactorNames << "Barometric Pressure Trends"
                } else {
                    msg = "Pressure dropping rapidly (Verifying HVAC/Door anomaly...)"
                }
            }
            else if (pTrendData.rate <= pDropMod) { 
                if (isPressureVerified) {
                    added += 15; msg = "Pressure falling"; activeFactors++; activeFactorNames << "Barometric Pressure Trends" 
                } else {
                    msg = "Pressure falling (Verifying HVAC/Door anomaly...)"
                }
            }
            else if (pTrendData.rate > pRiseStrong) { added -= 30; msg = "Pressure rising strongly (Clearing)" }
            added = applyWeight("Barometric Pressure Trends", added)
            recordDiag("Barometric Pressure Trends", added, msg)
            probability += added
        } else { diagList << [name: "Barometric Pressure Trends", status: "OFF", effect: "--", desc: "Disabled"] }
        
        if (settings.enableAccelerationLogic != false) {
            totalModelsEnabled++
            def added = 0
            def msg = "Stable (Accel: ${String.format('%.2f', pAccel)})"
            if (pAccel <= pAccelSevere) { 
                if (isPressureVerified) {
                    added += 25; msg = "Drop velocity is drastically accelerating (Squall)"; activeFactors++; activeFactorNames << "Pressure Acceleration" 
                } else {
                    msg = "Drop velocity accelerating (Verifying HVAC/Door anomaly...)"
                }
            }
            added = applyWeight("Pressure Acceleration", added)
            recordDiag("Pressure Acceleration", added, msg)
            probability += added
        } else { diagList << [name: "Pressure Acceleration", status: "OFF", effect: "--", desc: "Disabled"] }
        
        if (sensorLux) {
            if (settings.enableAstronomicalSolar != false) {
                totalModelsEnabled++
                def added = 0
                def expLux = state.currentExpectedLux ?: 0
                def el = state.currentSunElevation ?: 0
                def msg = "Solar levels normal"
                if (el > 15 && expLux > 10000) {
                    def maxRatio = luxVal / expLux
                    msg = "Receiving ${Math.round(maxRatio*100)}% of expected clear-sky lux"
                    if (maxRatio < 0.25) { 
                        if (tTrendData.rate > 0 && windGustVal < 10) {
                            msg += " (Blocked: Shadow Rejection Active)"
                        } else {
                            added += 25; msg += " (Severe blocking)"; activeFactors++; activeFactorNames << "Astronomical Solar" 
                        }
                    }
                } else {
                    msg = "Sun too low for modeling (Elev: ${Math.round(el)}°)"
                }
                added = applyWeight("Astronomical Solar", added)
                recordDiag("Astronomical Solar", added, msg)
                probability += added
            } else if (settings.enableCloudLogic != false) {
                totalModelsEnabled++
                def added = 0
                def nowHour = new Date().format("H", location.timeZone).toInteger()
                def msg = "Cloud logic inactive"
                if (nowHour >= 10 && nowHour <= 16) { 
                    def recentPeak = state.luxHistory.max { it.value as Float }?.value as Float ?: 0.0
                    msg = "Tracking local peak (${Math.round(recentPeak)})"
                    if (recentPeak > 15000 && luxVal < (recentPeak * 0.35)) {
                        added += 20; msg = "Severe solar drop during peak hours"; activeFactors++; activeFactorNames << "Basic Cloud Logic"
                    }
                }
                added = applyWeight("Basic Cloud Logic", added)
                recordDiag("Basic Cloud Logic", added, msg)
                probability += added
            }
            
            if (settings.enableSolarPlunge != false) {
                totalModelsEnabled++
                def added = 0
                def msg = "Stable (No rapid drop)"
                
                def currentElev = state.currentSunElevation ?: 0
                
                if (currentElev > 10) {
                    def cutoff = now() - 900000 
                    def maxAge = now() - 3600000 
                    
                    def oldLuxReading = state.luxHistory?.find { (it.time as Long) <= cutoff && (it.time as Long) >= maxAge }
                    
                    if (oldLuxReading) {
                        def oldLux = oldLuxReading.value as Float
                        if (oldLux > 10000 && luxVal < (oldLux * 0.5) && (oldLux - luxVal) > 8000) {
                            if (tTrendData.rate > 0 && windGustVal < 10 && pTrendData.rate >= 0) {
                                msg = "Plunge ignored (Shadow Rejection Filter: No supporting atmospheric shift)"
                            } else {
                                solarPlungeDetected = true
                                if (isPulsePowderKeg) {
                                    added += 25
                                    msg = "Pulse Storm Initiation! Plunge in extreme heat/humidity."
                                    activeFactors++; activeFactorNames << "Solar Plunge Velocity"
                                } else {
                                    added += 30
                                    msg = "Plunge detected! Dropped ${Math.round(oldLux - luxVal)} lux rapidly."
                                    activeFactors++; activeFactorNames << "Solar Plunge Velocity"
                                }
                            }
                        }
                    } else {
                        msg = "Gathering baseline daytime data..."
                    }
                } else {
                    msg = "Inactive (Sun too low: ${Math.round(currentElev)}°)"
                }
                
                added = applyWeight("Solar Plunge Velocity", added)
                recordDiag("Solar Plunge Velocity", added, msg)
                probability += added
            } else { diagList << [name: "Solar Plunge Velocity", status: "OFF", effect: "--", desc: "Disabled"] }
            
        } else {
            diagList << [name: "Solar / Cloud Logic", status: "OFF", effect: "--", desc: "No Solar Sensor"]
        }
        
        if (sensorWind) {
            if (settings.enableWindTroughLogic != false) {
                totalModelsEnabled++
                def added = 0
                def oldestWind = state.windHistory?.size() > 0 ? (state.windHistory.first()?.value as Float ?: 0.0) : 0.0
                def msg = "Stable (Wind normal)"
                if (oldestWind > windSteady && windVal <= windCalm && pTrendData.rate <= pDropMild) {
                    added += 25; msg = "Sudden calm + dropping pressure (Storm stall precursor)"; activeFactors++; activeFactorNames << "Wind Troughing"
                }
                added = applyWeight("Wind Troughing", added)
                recordDiag("Wind Troughing", added, msg)
                probability += added
            } else { diagList << [name: "Wind Troughing", status: "OFF", effect: "--", desc: "Disabled"] }
            
            if (settings.enableWindLogic != false) {
                totalModelsEnabled++
                def added = 0
                def msg = "Stable (Gusts normal)"
                if (wTrendData.diff >= windSpike && state.windHistory?.size() > 0 && (state.windHistory.last()?.value as Float) > windHigh) {
                    added += 15; msg = "Sudden high wind gust detected"; activeFactors++; activeFactorNames << "Wind Gust Fronts"
                }
                added = applyWeight("Wind Gust Fronts", added)
                recordDiag("Wind Gust Fronts", added, msg)
                probability += added
            } else { diagList << [name: "Wind Gust Fronts", status: "OFF", effect: "--", desc: "Disabled"] }

            if (settings.enableGustFactor != false) {
                totalModelsEnabled++
                def added = 0
                def msg = ""
                def cutoff = now() - 1800000
                def recentWinds = state.windHistory?.findAll { (it.time as Long) >= cutoff }
                if (recentWinds && recentWinds.size() > 2) {
                    def avgWind = recentWinds.sum { it.value as Float } / recentWinds.size()
                    if (avgWind > 0 && windGustVal >= th_windGustBase && windGustVal >= (avgWind * 2.5)) {
                        gustFactorDetected = true
                        added += 25
                        msg = "Outflow Boundary: Gust spiked to ${String.format('%.1f', windGustVal)} (30m Avg Speed: ${String.format('%.1f', avgWind)})"
                        activeFactors++; activeFactorNames << "Outflow Boundary (Gust Factor)"
                    } else {
                        msg = "Stable (Current Gust: ${String.format('%.1f', windGustVal)}, Avg Speed: ${String.format('%.1f', avgWind)})"
                    }
                } else {
                    msg = "Gathering baseline data..."
                }
                added = applyWeight("Outflow Boundary (Gust Factor)", added)
                recordDiag("Outflow Boundary (Gust Factor)", added, msg)
                probability += added
            } else { diagList << [name: "Outflow Boundary (Gust Factor)", status: "OFF", effect: "--", desc: "Disabled"] }
        } else {
            diagList << [name: "Wind Velocity Logic", status: "OFF", effect: "--", desc: "No Wind Sensor"]
        }

        if (sensorWindDir) {
            if (settings.enableWindShiftLogic != false) {
                totalModelsEnabled++
                def added = 0
                def msg = "Direction stable"
                if (state.windShiftDetected) {
                    added += 20; msg = "Severe direction shift detected (Frontal Passage)"; activeFactors++; activeFactorNames << "Wind Direction Shift"
                }
                added = applyWeight("Wind Direction Shift", added)
                recordDiag("Wind Direction Shift", added, msg)
                probability += added
            } else { diagList << [name: "Wind Direction Shift", status: "OFF", effect: "--", desc: "Disabled"] }
        } else { diagList << [name: "Wind Direction Shift", status: "OFF", effect: "--", desc: "No Wind Dir Sensor"] }
        
        if (sensorLightning) {
            if (settings.enableLightningVectoring != false) {
                totalModelsEnabled++
                def added = 0
                def msg = "No Strikes"
                if (strikeCount > 0 && closestLightning != 999.0) {
                    msg = "Vector: ${state.lightningVectorStr}"
                    if (vectorData.status == "Approaching") {
                        added += 40; msg += " (Storm core approaching)"; activeFactors++; activeFactorNames << "Lightning Vectoring"
                    } else if (vectorData.status == "Departing") {
                        added -= 40; msg += " (Storm core departing)"
                        if (state.weatherState != "Raining" && state.weatherState != "Sprinkling") vectorBypass = true 
                    } else if (closestLightning <= lightNear) {
                        if (isPulsePowderKeg) {
                            added += 30; msg += " (Pulse Storm: In-situ vertical lightning growth)"; activeFactors++; activeFactorNames << "Lightning Vectoring"
                        } else {
                            added += 30; msg += " (Critical proximity: Stalled/Lateral)"; activeFactors++; activeFactorNames << "Lightning Vectoring"
                        }
                    }
                }
                added = applyWeight("Lightning Vectoring", added)
                recordDiag("Lightning Vectoring", added, msg)
                probability += added
            } else if (settings.enableLightningLogic != false) {
                totalModelsEnabled++
                def added = 0
                def msg = "No Strikes"
                if (strikeCount > 0 && closestLightning != 999.0) {
                    def reqStrikes = settings.lightningStrikeThreshold ?: 10
                    if (strikeCount >= reqStrikes) {
                        if (closestLightning <= lightNear) { added += 50; msg = "Critical proximity"; activeFactors++; activeFactorNames << "Lightning Proximity" }
                        else if (closestLightning <= lightAppr) { added += 25; msg = "Storms approaching"; activeFactors++; activeFactorNames << "Lightning Proximity" }
                    } else { msg = "Strikes below threshold (${reqStrikes})" }
                }
                added = applyWeight("Lightning Proximity", added)
                recordDiag("Lightning Proximity", added, msg)
                probability += added
            }
        } else { diagList << [name: "Lightning Logic", status: "OFF", effect: "--", desc: "No Lightning Sensor"] }
        
        if (settings.enableSeasonalProfiling != false) {
            totalModelsEnabled++
            def isConvective = t > 80.0
            def isStratiform = t < 60.0
            def mult = 1.0
            def desc = ""
            
            if (isConvective) {
                if (dpSpread <= spreadTight || solarPlungeDetected || gustFactorDetected) {
                    mult = 1.2
                    desc = "Convective Profile (>80°F): Pop-up thunderstorm conditions amplified (1.2x)"
                } else {
                    desc = "Convective Profile (>80°F): No active pop-up triggers."
                }
            } else if (isStratiform) {
                if (pTrendData.rate <= pDropMod || ahTrendData.rate > 0.3) {
                    mult = 1.2
                    desc = "Stratiform Profile (<60°F): Frontal rain/pressure drop amplified (1.2x)"
                } else {
                    desc = "Stratiform Profile (<60°F): No active frontal triggers."
                }
            } else {
                desc = "Transitional Profile (60-80°F): Standard physics applied."
            }
            
            if (mult > 1.0) {
                probability *= mult
                diagList << [name: "Seasonal Storm Profiling", status: "ACTIVE", effect: "x${String.format('%.1f', mult)}", desc: desc]
            } else {
                diagList << [name: "Seasonal Storm Profiling", status: "ON", effect: "1.0x", desc: desc]
            }
        } else {
            diagList << [name: "Seasonal Storm Profiling", status: "OFF", effect: "--", desc: "Disabled"]
        }

        if (settings.enableSynergyLogic != false) {
            totalModelsEnabled++
            def mult = 1.0
            def msgs = []
            if (settings.enableDPLogic != false && settings.enablePressureLogic != false && sTrendData.rate <= sTrendConv && pTrendData.rate <= pDropMod) {
                mult *= 1.3; msgs << "Squeeze Velocity + Barometric Drop (1.3x)"
            }
            if (settings.enableWetBulbLogic != false && settings.enableWindShiftLogic != false && tTrendData.rate <= tTrendRapid && sensorWindDir && state.windShiftDetected) {
                mult *= 1.2; msgs << "Temp Drop + Frontal Wind Shift (1.2x)"
            }
            if (settings.enableLightningLogic != false && settings.enableWindShiftLogic != false && sensorWindDir && state.windShiftDetected && strikeCount > 0) {
                mult *= 1.3; msgs << "Lightning + Frontal Wind Shift (1.3x)"
            }
            
            if (mult > 1.0) {
                diagList << [name: "Algorithmic Synergy", status: "ACTIVE", effect: "x${String.format('%.1f', mult)}", desc: msgs.join(" | ")]
                probability *= mult
            } else {
                diagList << [name: "Algorithmic Synergy", status: "ON", effect: "1.0x", desc: "No synergistic conditions met"]
            }
        } else { diagList << [name: "Algorithmic Synergy", status: "OFF", effect: "--", desc: "Disabled"] }
        
        // Push the active factor names to ML history array
        if (activeFactorNames.size() > 0) {
            state.activeModulesThisPrediction = activeFactorNames.unique()
        }
        
        if (settings.enableAutoCalibration != false) {
            def mult = state.calibrationMultiplier ?: 1.0
            if (mult < 1.0) {
                diagList << [name: "Global Auto-Calibration", status: "ACTIVE", effect: "x${String.format('%.2f', mult)}", desc: "Global penalty applied due to ${state.falsePositiveCount} false positives"]
                probability *= mult
            } else if (mult > 1.0) {
                diagList << [name: "Global Auto-Calibration", status: "ACTIVE", effect: "x${String.format('%.2f', mult)}", desc: "Global bonus applied due to ${state.falseNegativeCount} missed predictions"]
                probability *= mult
            } else {
                diagList << [name: "Global Auto-Calibration", status: "ON", effect: "1.0x", desc: "Tracking stable"]
            }
        } else { diagList << [name: "Global Auto-Calibration", status: "OFF", effect: "--", desc: "Disabled"] }

        probability = Math.round(probability)
        if (probability < 0) probability = 0
        if (probability > 100) probability = 100
        
        def sustainMs = (settings.probSustainMins != null ? settings.probSustainMins.toInteger() : 30) * 60000
        if (probability >= (state.peakProb ?: 0)) {
            state.peakProb = probability
            state.peakProbTime = now()
        } else if (state.peakProbTime && (now() - state.peakProbTime) < sustainMs) {
            probability = state.peakProb
            diagList << [name: "Threat Persistence (Hysteresis)", status: "ACTIVE", effect: "Hold", desc: "Holding peak probability for ${settings.probSustainMins != null ? settings.probSustainMins : 30} mins to prevent drop-off jitter."]
        } else {
            state.peakProb = probability
        }
        
        if (r >= 0.05 || leakWet || (r > 0 && probability > 60)) {
            probability = 100
            diagList << [name: "Physical Hardware Trigger", status: "ACTIVE", effect: "100%", desc: leakWet ? "Instant Leak Sensor is WET" : "Rain Gauge Tipped"]
            state.peakProb = 100
            state.peakProbTime = now()
            state.rainOccurredDuringPrediction = true 
        }
        
        if (dewRejectionActive) diagList << [name: "Dew Rejection", status: "ACTIVE", effect: "0%", desc: "Leak Sensor ignored (Morning Dew/Frost Detected)"]
        if (stuckLeakActive) diagList << [name: "Stuck Sensor Failsafe", status: "ACTIVE", effect: "0%", desc: "Leak Sensor ignored (Stuck WET without physical rain gauge confirmation)"]
        if (state.leakWetVerifying) diagList << [name: "Sensor Delay Timer", status: "ACTIVE", effect: "0%", desc: "First Drop Sensor is wet. Waiting 60 seconds to verify before triggering."]

    } else {
        probability = (state.weatherState == "Clear") ? 0 : 100
        
        if (probability == 100) {
            state.peakProb = 100
            state.peakProbTime = now()
            state.rainOccurredDuringPrediction = true 
        }
        
        if (state.hardwareAnomalyActive) {
            diagList << [name: "Engine Core", status: "OFFLINE", effect: "0%", desc: "Execution halted. Physically impossible hardware data detected."]
        } else if (isStale) {
            diagList << [name: "Engine Core", status: "OFFLINE", effect: "0%", desc: "Sensors Stale/Offline (No data received in ${staleMins} mins)"]
        } else if (isPostRainSuppressed) {
            diagList << [name: "Post-Rain Suppression", status: "ACTIVE", effect: "0%", desc: "Probability locked to 0% during post-rain cooldown."]
        } else if (state.weatherState != "Clear") {
            diagList << [name: "Active Precipitation", status: "ACTIVE", effect: "100%", desc: "Weather state is currently ${state.weatherState}."]
        }
    }
    
    state.rainProbability = Math.round(probability)
    state.algoDiagnostics = diagList

    def activeReasons = diagList.findAll { it.effect.contains("+") || it.effect.contains("-") || it.effect.startsWith("x1.") || it.effect.startsWith("x2.") || it.effect == "100%" || it.effect == "Hold" }.collect { "${it.name} (${it.effect})" }
    state.logicReasoning = activeReasons.size() > 0 ? activeReasons.join(" | ") : "Stable conditions. No triggers active."

    def conf = 50 
    def confRes = ""
    
    if (sensorLux) conf += 5
    if (sensorWind) conf += 5
    if (sensorWindDir) conf += 5
    if (sensorLeak || sensorLeak2 || sensorLeak3) conf += 5
    if (sensorRain) conf += 5
    
    def highAgreementThreshold = (totalModelsEnabled / 2).toInteger()
    if (highAgreementThreshold < 1) highAgreementThreshold = 1
    if (highAgreementThreshold > 3) highAgreementThreshold = 3
    
    if (isStale) {
        conf = 0
        confRes = "Zero confidence due to stale data."
    } else if (r >= 0.05 || leakWet || (r > 0 && probability > 60 && state.weatherState != "Clear")) {
        conf = 100
        if (leakWet && r <= 0) confRes = "100% Confirmed - Physical precipitation registered by instant Leak Sensor."
        else confRes = "100% Confirmed - Physical precipitation registered by Rain Gauge."
    } else {
        if (probability >= 60) {
            if (activeFactors >= highAgreementThreshold) {
                conf += 25
                confRes = "High agreement. Prediction driven by ${activeFactors} converging models (${activeFactorNames.unique().join(', ')})."
            } else if (activeFactors > 0) {
                conf += 5
                confRes = "Moderate agreement. High probability but relying on isolated factors (${activeFactorNames.unique().join(', ')})."
            }
        } else if (probability <= 20) {
            if (activeFactors == 0) {
                conf += 30
                confRes = "Strong agreement. All monitored metrics indicate stable, dry conditions."
            } else {
                conf += 10
                confRes = "Mostly stable. Low probability, but ${activeFactors} model (${activeFactorNames.unique().join(', ')}) shows minor fluctuations."
            }
        } else {
            conf += 10
            confRes = "Unsettled/Transitional environment. Conflicting or mild metrics preventing strong consensus."
        }
    }
    
    if (conf > 100) conf = 100
    state.confidenceScore = conf
    state.confidenceReasoning = confRes
    
    def probThreshold = settings.notifyProbThreshold != null ? settings.notifyProbThreshold.toInteger() : 85
    def probResetMs = (settings.probResetMins != null ? settings.probResetMins.toInteger() : 120) * 60000
    def delaySecs = settings.alertDelaySeconds != null ? settings.alertDelaySeconds.toInteger() : 180
    
    if (probability < probThreshold) {
        if (state.probBelowSince == null) {
            state.probBelowSince = now()
        } else if (!state.probArmed && (now() - state.probBelowSince) >= probResetMs) {
            state.probArmed = true
            logAction("Probability stayed below threshold for ${settings.probResetMins != null ? settings.probResetMins : 120}m. Probable alerts re-armed.")
        }
    } else {
        if (!state.probArmed && state.probBelowSince != null) {
            logAction("Probability spiked above threshold before 120m elapsed. Resetting counter.")
        }
        state.probBelowSince = null
    }

    if (probability >= probThreshold && !isStale && !state.hardwareAnomalyActive && !isPostRainSuppressed && state.weatherState == "Clear") {
        if (state.probThresholdCrossedTime == null) {
            state.probThresholdCrossedTime = now()
            logAction("Probability threshold reached (${probability}%). Starting ${delaySecs}s state debounce filter.")
        }
        
        def elapsedSecs = (now() - state.probThresholdCrossedTime) / 1000
        if (elapsedSecs >= delaySecs) {
            safeOn(switchProbable) 
            state.activePrediction = true 
            
            if (settings.enableAutoCalibration != false && !state.probableStartTime) {
                state.probableStartTime = now()
            }
            
            if (state.probArmed) {
                state.probArmed = false 
                logAction("Probability threshold sustained continuously for ${delaySecs}s. Triggering verified rain prediction alert.")
                sendAlert("☔ Weather Alert: Rain probability has reached ${Math.round(state.rainProbability)}%.", settings.probNotifyDevices, 1)
                logAlertCause("Rain Probable", Math.round(state.rainProbability), state.logicReasoning)
            }
        } else {
            def remainingSecs = Math.ceil(delaySecs - elapsedSecs).toInteger()
            runIn(remainingSecs > 0 ? remainingSecs : 1, "executeProbableAlert")
        }
    } else {
        safeOff(switchProbable)
        if (state.probThresholdCrossedTime != null) {
            logAction("Probability dropped below threshold or system state changed. Resetting debounce filter.")
            state.probThresholdCrossedTime = null
        }
        unschedule("executeProbableAlert")

        // Evaluate the end of the prediction cycle (Machine Learning Logic)
        if (state.activePrediction) {
            if (!state.rainOccurredDuringPrediction) {
                state.falsePositiveCount = (state.falsePositiveCount ?: 0) + 1
                
                def culprits = state.activeModulesThisPrediction ?: []
                if (culprits.size() > 0) {
                    culprits.each { mod ->
                        def weights = state.algoWeights ?: [:]
                        def oldW = weights[mod] != null ? weights[mod] : 1.0
                        weights[mod] = Math.max(0.5, oldW * 0.90) // 10% specific penalty to the algorithms that fired
                        state.algoWeights = weights
                        logAction("Auto-Calibration: Penalizing '${mod}' by 10% (New multiplier: ${String.format('%.2f', state.algoWeights[mod])}x)")
                    }
                } else {
                    // Fallback to global penalty if no specific algorithm crossed the threshold
                    def newMult = (state.calibrationMultiplier ?: 1.0) * 0.95 
                    if (newMult < 0.5) newMult = 0.5 
                    state.calibrationMultiplier = newMult
                    logAction("Auto-Calibration: Unattributed False positive detected. Global Penalty applied. New Weight: ${String.format('%.2f', newMult)}x")
                }
            }
            state.activePrediction = false
            state.rainOccurredDuringPrediction = false
            state.activeModulesThisPrediction = []
        }
    }
    
    def targetState = "Clear"
    def threshold = heavyRainThreshold != null ? heavyRainThreshold : 0.1
    
    if (!isStale && !state.hardwareAnomalyActive) {
        if (r >= threshold) {
            targetState = "Raining"
        } else if (r >= 0.05 || leakWet || (r > 0 && probability > 60)) {
            targetState = "Sprinkling"
        } else if (probability >= 90 && dpSpread <= spreadCrit) {
            targetState = "Sprinkling"
        }
    }

    // --- LOCK STATE: Prevent downgrade from Heavy Rain to Sprinkling ---
    if (state.weatherState == "Raining" && targetState == "Sprinkling") {
        targetState = "Raining"
    }
    
    if (state.hardwareAnomalyActive) {
        state.expectedClearTime = "Unknown (Hardware Anomaly Active)"
    } else if (isStale) {
        state.expectedClearTime = "Unknown (Sensors Offline)"
    } else if (targetState != "Clear") {
        if (pTrendData.rate > pRiseMild || vpd > 0.4 || dpSpread > spreadTight) {
            state.expectedClearTime = "~15-30 mins (Trends improving rapidly)"
        } else if (pTrendData.rate < pDropMild || dpSpread < 1.0) {
            state.expectedClearTime = "1+ Hour (Conditions worsening/stagnant)"
        } else {
            state.expectedClearTime = "~45 mins (Stable rain profile)"
        }
    } else {
        state.expectedClearTime = "Already Clear"
    }

    def currentState = state.weatherState
    def debounceMs = (settings.debounceMins != null ? settings.debounceMins.toInteger() : 5) * 60000
    def timeSinceChange = now() - (state.lastStateChange ?: 0)
    
    def allowTransition = false
    def reasonSuffix = ""
    
    if (isStale && currentState != "Clear") {
        targetState = "Clear"
        allowTransition = true
    } else if (currentState != targetState) {
        if (currentState == "Clear" && targetState != "Clear") { allowTransition = true }
        else if (currentState == "Sprinkling" && targetState == "Raining") { allowTransition = true }
        else if (timeSinceChange >= debounceMs) { allowTransition = true }
        else if (vectorBypass && targetState == "Clear") {
            allowTransition = true
            reasonSuffix = " [Vector Bypass: Instant clearing permitted as storm is departing.]"
        }
        else {
            reasonSuffix = " [Downgrade to ${targetState} delayed by Debounce timer: ${Math.ceil((debounceMs - timeSinceChange)/60000)}m remaining]"
        }
    }
    
    if (reasonSuffix != "") {
        state.confidenceReasoning += reasonSuffix
    }
    
    def precipResetMs = (settings.precipResetMins != null ? settings.precipResetMins.toInteger() : 120) * 60000
    def clearDelayMs = (settings.clearDelayMins != null ? settings.clearDelayMins.toInteger() : 60) * 60000

    if (state.weatherState == "Clear") {
        if (state.precipEndedAt != null) {
            def timeClear = now() - state.precipEndedAt
            
            if (timeClear >= clearDelayMs && !state.allClearSent) {
                state.allClearSent = true
                sendAlert("☀️ Weather Update: All rain conditions have been clear for ${settings.clearDelayMins != null ? settings.clearDelayMins : 60} minutes.", settings.clearNotifyDevices, 0)
            }
            
            if (timeClear >= precipResetMs && !state.stormArmed) {
                state.stormArmed = true
                logAction("Weather has been clear for ${settings.precipResetMins != null ? settings.precipResetMins : 120}m. Rain/Sprinkling alerts re-armed.")
            }
        }
    }
    
    if (allowTransition) {
        logAction("State changed from ${currentState} to ${targetState}.")
        
        if (targetState == "Clear") {
            state.lastRainEndTime = now()
            state.precipEndedAt = now()
            state.allClearSent = false
            safeOff(switchRaining)
            safeOff(switchSprinkling)
            logAction("Weather cleared. ${settings.clearDelayMins != null ? settings.clearDelayMins : 60}m All-Clear timer and ${settings.precipResetMins != null ? settings.precipResetMins : 120}m re-arm timer started.")
        } else {
            
            if (!state.activePrediction && currentState == "Clear") {
                state.falseNegativeCount = (state.falseNegativeCount ?: 0) + 1
                def newMult = (state.calibrationMultiplier ?: 1.0) * 1.05 
                if (newMult > 1.5) newMult = 1.5 // Cap global bonus at 1.5x
                state.calibrationMultiplier = newMult
                logAction("Auto-Calibration: Missed rain prediction (False Negative). Global Sensitivity boosted. New Weight: ${String.format('%.2f', newMult)}x")
            }

            state.precipEndedAt = null 
            
            if (targetState == "Raining") {
                safeOn(switchSprinkling) 
                safeOn(switchRaining)
                if (!isStale && state.stormArmed) {
                    sendAlert("⛈️ Weather Update: Heavy Rain detected. Probability: ${Math.round(probability)}%", settings.rainNotifyDevices, 3)
                    state.stormArmed = false 
                    logAlertCause("Heavy Rain", Math.round(probability), state.logicReasoning)
                } else if (!isStale) {
                    logAction("Muted Heavy Rain alert: Has not been clear for ${settings.precipResetMins != null ? settings.precipResetMins : 120}m or already alerted.")
                }
            } 
            else if (targetState == "Sprinkling") {
                safeOff(switchRaining)
                safeOn(switchSprinkling)
                if (!isStale && state.stormArmed) {
                    sendAlert("🌦️ Weather Update: Sprinkling detected. Probability: ${Math.round(probability)}%", settings.sprinkleNotifyDevices, 2)
                    state.stormArmed = false 
                    logAlertCause("Sprinkling", Math.round(probability), state.logicReasoning)
                } else if (!isStale) {
                    logAction("Muted Sprinkling alert: Has not been clear for ${settings.precipResetMins != null ? settings.precipResetMins : 120}m or already alerted.")
                }
            }
        }
        
        state.weatherState = targetState
        state.lastStateChange = now()
    }
    
    logProbabilityHistory()
}

def executeProbableAlert() {
    if (debugEnable) log.debug "${app.label}: executeProbableAlert() verification loop triggered."
    evaluateWeather()
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
    
    def cooldownMs = (settings.notificationCooldown != null ? settings.notificationCooldown.toInteger() : 60) * 60000
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
        logAction("🔕 Alert Muted (Anti-Spam Active): ${msg}")
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
