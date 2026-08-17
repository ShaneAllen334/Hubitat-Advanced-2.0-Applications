/**
 * Advanced Room Good Night Manager 2.0
 */
definition(
    name: "Advanced Room Good Night Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
        def anyAsmEnabled = false
        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableASM${i}"]) anyAsmEnabled = true
        }
        
        section() {
            input "refreshDataBtn", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
                      
            input "forceEvalBtn", "button", title: "⚡ Force Global Sync Evaluation"
            
            def cssHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 30%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
            </style>
            """
            
            def hasConfiguredRooms = false
            def dashHTML = cssHTML
            
            for (int i = 1; i <= maxRooms; i++) {
                if (settings["enableRoom${i}"]) {
                    hasConfiguredRooms = true
                    def rName = settings["roomName${i}"] ?: "Room ${i}"
                    
                    def tSensor = settings["tempSensor${i}"]
                    def hSensor = settings["humSensor${i}"]
                    def cTemp = tSensor ? tSensor.currentValue("temperature") : null
                    def cHum = hSensor ? hSensor.currentValue("humidity") : null
                    def sleepQuality = calculateSleepSuitability(cTemp, cHum)
                    
                    def overrideFadeInUri = settings["audioFadeInUri${i}"]
                    def tonightTrack = "Not Generated Yet"
                    def aType = settings["audioSourceType${i}"] ?: "uri"
                    
                    if (overrideFadeInUri) {
                        tonightTrack = "Override URI Active"
                    } else if (aType == "uri" && state."nextUri${i}") {
                        tonightTrack = state."nextUri${i}"
                    } else if (aType == "switch" && state."nextSwitchId${i}") {
                        def nId = state."nextSwitchId${i}"
                        for(int u = 1; u <= 5; u++) {
                            def s = settings["audioSwitch${i}_${u}"]
                            if (s?.id == nId) tonightTrack = s.displayName
                        }
                    }
                    
                    if (isAnyoneInRoomSick(i)) tonightTrack = "<span style='color:red;'>Audio Disabled (Sick Mode)</span>"
                    
                    def sw = settings["roomSwitch${i}"]
                    def isAsleep = (sw?.currentValue("switch") == "on")
                    def titleColor = isAsleep ? "#2e154f" : "#007bff"
                    
                    def expStdFan = "App Released"
                    def expLights = "App Released"
                    def expAudio = "App Released"
                    def orchStatus = "Disabled"
                    
                    if (settings["enableOrchestrator${i}"]) {
                         def isWin = isWithinOrchestratorWindow(i)
                         def uCount = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
                         def actU = 0
                         def readyU = 0
                         def lWait = 0
                         def allE = true
                         def anyE = false
                         
                         for(int u = 1; u <= uCount; u++) {
                             def tuId = "${i}_${u}"
                             def pMatTest = settings["pressureMat_${tuId}"]
                             def presenceDev = settings["occupantPresence_${tuId}"]
                             def cStateCheck = state.sleepState["${tuId}"] ?: "EMPTY"
                             def isAway = (presenceDev && presenceDev.currentValue("presence") == "not present")
                             
                             if (pMatTest) {
                                 if (isAway && cStateCheck == "EMPTY") continue
                                 
                                 actU++
                                 def tCState = state.sleepState["${tuId}"] ?: "EMPTY"
                                 def tMState = pMatTest.currentValue("contact") ?: pMatTest.currentValue("presence")
                                 
                                 if (tCState != "EMPTY" || tMState == "closed" || tMState == "present") allE = false
                                 if (tCState == "EMPTY" || tCState == "BATHROOM TRIP") anyE = true
                                 
                                 if (tCState == "IN BED" || tCState == "SLEEPING") {
                                      def tInBed = state.inBedTime?."${tuId}" ?: 0
                                      if (tInBed > 0) {
                                          def rMin = calculateDynamicDelay(i)
                                          def elap = new Date().time - tInBed
                                          if (elap >= rMin * 60000) readyU++
                                          else {
                                              def rem = (rMin * 60000) - elap
                                              if (rem > lWait) lWait = rem
                                          }
                                      }
                                 }
                             }
                         }
                         
                         if (isAsleep) {
                             if (allE && actU > 0) {
                                 if (settings["enableAutoWake${i}"]) {
                                     if (settings["autoWakeNightLock${i}"] && !isWakeTimeReached(i)) {
                                         orchStatus = "<span style='color:#007bff; font-weight:bold;'>🌙 Night Lock Active (Auto-Wake Blocked)</span>"
                                     } else {
                                         def lockoutMins = settings["autoWakeLockout${i}"] != null ? settings["autoWakeLockout${i}"].toInteger() : 120
                                         def timeSinceGn = new Date().time - (state."goodNightStartTime${i}" ?: 0)
                                         
                                         if (timeSinceGn < (lockoutMins * 60000)) {
                                             orchStatus = "<span style='color:orange;'>Initial Lockout Active: Wait ${(lockoutMins - (timeSinceGn/60000).toInteger())}m</span>"
                                         } else if (state."autoWakePending${i}") {
                                             def remain = (((state."autoWakePending${i}" as Long) - new Date().time) / 1000).toInteger()
                                             if (remain > 0) orchStatus = "<span style='color:orange;'>Wake Pending: ${(remain/60).toInteger()}m ${(remain%60).toInteger()}s</span>"
                                             else orchStatus = "Waking Room..."
                                         } else {
                                             orchStatus = "All Beds Empty (Auto-Wake Triggering...)"
                                         }
                                     }
                                 } else {
                                     orchStatus = "All Beds Empty (Auto-Wake Disabled)"
                                 }
                             }
                             else orchStatus = "Monitoring Sleep"
                         } else {
                             if (!isWin) {
                                 orchStatus = "Outside Allowed Time Window"
                             } else {
                                 if (actU > 0 && anyE) orchStatus = "Awaiting All Occupants in Bed"
                                 else if (readyU >= actU) orchStatus = "Ready (Waiting for Cron Sync)"
                                 else if (lWait > 0) orchStatus = "Settle Delay: ${Math.round(lWait/60000)}m remaining until Auto-Sleep"
                             }
                         }
                    }
                    
                    def synergyActive = state."roomIsSleeping${i}"
                    def synStatusText = "<span style='color:gray;'>Standby</span>"
                    if (synergyActive) {
                        if (state."synergySoftRampActive${i}") synStatusText = "<span style='color:orange; font-weight:bold;'>Soft-Ramp Failsafe Active (Ceiling Normal, Towers OFF)</span>"
                        else if (state."synergyStage2Active${i}") synStatusText = "<span style='color:#6f42c1; font-weight:bold;'>Stage 2 Active (Tower Fans OFF)</span>"
                        else if (state."synergyStage1Time${i}") synStatusText = "<span style='color:#6f42c1; font-weight:bold;'>Stage 1 Active (Ceiling Fan Staged)</span>"
                        else synStatusText = "<span style='color:#007bff;'>Awaiting 1 AM Deep Sleep Threshold</span>"
                    }
                    def synergyText = synStatusText

                    if (isAsleep) {
                        def isFadingInAudio = state."fadeInActive${i}"
                        def isFadingAudio = state."fadeActive${i}"
                        def isFadingLight = state."lightFadeActive${i}"
                        
                        expLights = isFadingLight ? "Fading Out" : (settings["roomLightsOn${i}"] ? "OFF / Bedtime Plugs ON" : "OFF")
                        
                        def minVolDisp = settings["audioFadeMinVol${i}"] != null ? settings["audioFadeMinVol${i}"] : 0
                        def targetVolDisp = settings["audioVolume${i}"] != null ? settings["audioVolume${i}"] : 30
                        
                        if (isAnyoneInRoomSick(i)) {
                            expAudio = "OFF (Sick Mode Override)"
                            if (isFadingLight) expLights = "OFF (Instant Off due to Sick Mode)"
                        } else if (isFadingInAudio) {
                            expAudio = "Fading In to ${targetVolDisp}%"
                        } else if (isFadingAudio) {
                            expAudio = "Fading Out to ${minVolDisp}%"
                        } else {
                            expAudio = "PLAYING (Unless Timer Ended)"
                        }

                        if (synergyActive) {
                            if (settings["sleepFansOff${i}"] && (state."synergyStage2Active${i}" || state."synergySoftRampActive${i}")) {
                                expStdFan = "<span style='color:#007bff; font-weight:bold;'>Forced OFF (Synergy Controlled)</span>"
                            } else if (cTemp != null) {
                                def stdSet = settings["fanSetpoint${i}"]
                                expStdFan = stdSet ? (cTemp >= stdSet ? "ON" : "OFF") : "Not Configured"
                            } else {
                                expStdFan = "Awaiting Temp Data"
                            }
                        } else if (cTemp != null) {
                            def stdSet = settings["fanSetpoint${i}"]
                            expStdFan = stdSet ? (cTemp >= stdSet ? "ON" : "OFF") : "Not Configured"
                        } else {
                            expStdFan = "Awaiting Temp Data"
                        }
                    } else {
                        expStdFan = "App Released (Room Awake)"
                    }
                    
                    def asmRows = ""
                    if (settings["enableASM${i}"]) {
                        def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
                        for (int u = 1; u <= numUsers; u++) {
                            def uId = "${i}_${u}"
                            if (settings["pressureMat_${uId}"]) {
                                asmRows += generateHtmlTile(uId)
                            }
                        }
                    }
                    
                    dashHTML += """
                    <table class="dash-table" style="margin-bottom: 25px;">
                        <thead>
                            <tr><th colspan='2' style='background-color:${titleColor}; text-align:left; padding-left:15px; font-size:14px;'>${rName} - ${isAsleep ? '🌙 ASLEEP' : '☀️ AWAKE'}</th></tr>
                        </thead>
                        <tbody>
                            <tr><td colspan='2' class="dash-subhead">Live Environment</td></tr>
                            <tr><td class="dash-hl">Current Temp</td><td class="dash-val">${cTemp != null ? cTemp + '°F' : '--'}</td></tr>
                            <tr><td class="dash-hl">Humidity</td><td class="dash-val">${cHum != null ? cHum + '%' : '--'}</td></tr>
                            <tr><td class="dash-hl">Environment</td><td class="dash-val">${sleepQuality}</td></tr>
                            <tr><td class="dash-hl">Tonight's Audio</td><td class="dash-val"><span style='font-size:10px; font-family:monospace; word-break:break-all;'>${tonightTrack}</span></td></tr>
                            
                            <tr><td colspan='2' class="dash-subhead">Expected States</td></tr>
                            <tr><td class="dash-hl">Auto-Orchestrator</td><td class="dash-val"><b>${orchStatus}</b></td></tr>
                            <tr><td class="dash-hl">Staged Sleep Synergy</td><td class="dash-val">${synergyText}</td></tr>
                            <tr><td class="dash-hl">Standard/Bed Fans</td><td class="dash-val">${expStdFan}</td></tr>
                            <tr><td class="dash-hl">Lights/Shades</td><td class="dash-val">${expLights}</td></tr>
                            <tr><td class="dash-hl">Audio Track</td><td class="dash-val">${expAudio}</td></tr>
                            ${asmRows}
                        </tbody>
                    </table>
                    """
                }
            }
            
            if (hasConfiguredRooms) {
                paragraph dashHTML
            } else {
                paragraph dashHTML + "<i>Please enable and configure a room below to populate the dashboard.</i>"
            }
        }

        if (anyAsmEnabled) {
            section("<b>Override Controls</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:13px; color:#555;'>Manually override room occupancy states for Auto-Orchestrator testing or error correction.</div>"
                input "btnForceEmpty_ALL", "button", title: "🛌 Force ALL Rooms EMPTY"
                input "btnForceOcc_ALL", "button", title: "😴 Force ALL Rooms OCCUPIED"
                for (int i = 1; i <= maxRooms; i++) {
                    if (settings["enableRoom${i}"] && settings["enableASM${i}"]) {
                        def rName = settings["roomName${i}"] ?: "Room ${i}"
                        input "btnForceEmpty_${i}", "button", title: "Force ${rName} EMPTY"
                        input "btnForceOcc_${i}", "button", title: "Force ${rName} OCCUPIED"
                    }
                }
                paragraph "<hr>"
                input "clearMlBtn", "button", title: "🧠 Clear Learned AI Sleep Patterns"
                input "clearScoresBtn", "button", title: "🗑️ Clear All Historical Sleep Scores"
            }

            section("<b>📊 Historical Sleep Scores & Profiles</b>", hideable: true, hidden: true) {
                for (int i = 1; i <= maxRooms; i++) {
                    if (settings["enableRoom${i}"] && settings["enableASM${i}"]) {
                        def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
                        for (int u = 1; u <= numUsers; u++) {
                            def uId = "${i}_${u}"
                            def uName = settings["userName_${uId}"] ?: "User ${uId}"
                            def rName = settings["roomName${i}"] ?: "Room ${i}"
                            
                            def avgScore = state.avgSleepScore?."${uId}" ?: "--"
                            def history = state.sleepScoreHistory?."${uId}" ?: []
                            def targetMins = (settings["targetSleepHours_${uId}"] != null ? settings["targetSleepHours_${uId}"].toDouble() : 7.5) * 60
                            
                            def profileHtml = """
                            <table class="dash-table" style="margin-bottom: 15px;">
                                <thead>
                                    <tr><th colspan='2' style='background-color:#495057; text-align:left; padding-left:15px; font-size:13px;'>${uName} History (${rName} - Avg BMS: ${avgScore}%)</th></tr>
                                </thead>
                                <tbody>
                            """
                            if (history.size() > 0) {
                                history.each { entry ->
                                    profileHtml += generateProfessionalHistoryTile(entry, targetMins)
                                }
                            } else {
                                profileHtml += "<tr><td colspan='2' class='dash-val' style='text-align:center !important; font-size:12px; color:#666;'><i>Not enough data. Valid sessions will appear here.</i></td></tr>"
                            }
                            profileHtml += "</tbody></table>"
                            paragraph profileHtml
                        }
                    }
                }
            }
        }
        
        section("<b>Command History (Last 20)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a transparent, rolling log of every command the system evaluates and sends.</div>"
            def logList = state.eventLog ?: []
            if (logList.size() > 0) {
                def logHtml = logList.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${logHtml}</span>"
            } else {
                paragraph "<i>No commands logged yet. Turn a Good Night switch on to begin tracking.</i>"
            }
            input "clearLogBtn", "button", title: "Clear Command History"
        }

        section("<b>Global Life-Safety Override</b>", hideable: true, hidden: true) {
            paragraph "<i>If smoke or carbon monoxide is detected, the app will instantly kill all fans (to prevent spreading smoke), turn all configured room lights to 100% for egress, and stop all audio playback.</i>"
            input "emergencyAlarms", "capability.smokeDetector", title: "Smoke Detectors", multiple: true, required: false
            input "emergencyCO", "capability.carbonMonoxideDetector", title: "Carbon Monoxide Detectors", multiple: true, required: false
        }

        section("<b>Global Settings & Logs</b>", hideable: true, hidden: true) {
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            input "numRooms", "enum", title: "<b>Number of Rooms to Configure</b>", options: ["1","2","3","4","5"], defaultValue: "1", submitOnChange: true
            
            input "enableMatPolling", "bool", title: "<b>Enable Pressure Mat Keep-Alive (Refresh)</b>", defaultValue: false, submitOnChange: true
            if (settings["enableMatPolling"]) {
                input "matPollingInterval", "enum", title: "Polling Interval", options: ["5":"5 Minutes", "10":"10 Minutes", "15":"15 Minutes"], defaultValue: "15"
            }
            input "switchDebounceDelay", "number", title: "Switch Debounce/Delay (Seconds)", defaultValue: 3, required: true
            input "enablePeriodicEnforcement", "bool", title: "<b>Enable Periodic State Enforcement</b>", defaultValue: true
            input "txtLogEnable", "bool", title: "Enable Action Logging (Info)", defaultValue: true
            input "debugLogEnable", "bool", title: "Enable Debug Logging", defaultValue: false
        }
        
        for (int i = 1; i <= maxRooms; i++) {
            def rName = settings["roomName${i}"] ?: "Room ${i}"
            
            section("<b>${rName} Configuration</b>", hideable: true, hidden: true) {
                input "enableRoom${i}", "bool", title: "<b>Enable ${rName}</b>", submitOnChange: true
                
                if (settings["enableRoom${i}"]) {
                    input "roomName${i}", "text", title: "Custom Room Name", defaultValue: "Room ${i}", submitOnChange: true
                    input "roomSwitch${i}", "capability.switch", title: "${rName} Good Night Virtual Switch", required: true
                    
                    paragraph "<b>Manual Controls</b>"
                    input "restartRoomBtn_${i}", "button", title: "🔄 Restart ${rName} Good Night Sequence"
                    
                    paragraph "<b>Good Night Toggle Button</b>"
                    input "gnButton${i}", "capability.pushableButton", title: "Toggle Button Device", required: false
                    input "gnButtonNum${i}", "number", title: "Button Number", required: false, defaultValue: 1
                    input "gnButtonAction${i}", "enum", title: "Button Action", options: ["pushed":"Pushed", "doubleTapped":"Double Tapped", "held":"Held", "released":"Released"], required: false, defaultValue: "pushed"
                    input "gnButtonModes${i}", "mode", title: "Only Allow in These Modes", multiple: true, required: false
                    
                    paragraph "<b>1. Climate & Environment</b>"
                    input "tempSensor${i}", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true
                    input "humSensor${i}", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (Optional - for sleep rating)", required: false
                    
                    paragraph "<b>Standard ON/OFF Fans</b>"
                    input "fanSetpoint${i}", "decimal", title: "Turn ON Standard Fans if Temp reaches (°F)", required: false
                    input "roomFans${i}", "capability.switch", title: "Standard Fans (Select up to 2)", multiple: true, required: false
                    
                    paragraph "<b>2. Lighting & Shades</b>"
                    input "roomLights${i}", "capability.switch", title: "Lights to Turn OFF immediately (if fade disabled)", multiple: true, required: false
                    input "enableLightFade${i}", "bool", title: "<b>Enable Smooth Light Fade-Out</b>", submitOnChange: true
                    if (settings["enableLightFade${i}"]) {
                        input "lightFadeDuration${i}", "number", title: "Light Fade Duration (Minutes)", defaultValue: 15, required: true
                    }
                    input "roomLightsOn${i}", "capability.switch", title: "Lights/Plugs to Turn ON (Turns OFF when waking)", multiple: true, required: false
                    
                    paragraph "<b>Delayed Shut-Off (e.g., Nightlights/RGB)</b>"
                    input "delayedOffSwitches${i}", "capability.switch", title: "Switches to turn OFF later", multiple: true, required: false
                    input "delayedOffTime${i}", "number", title: "Delay Time (Minutes)", required: false, defaultValue: 120
                    input "pauseLightingEnforcement${i}", "capability.switch", title: "Pause Lighting Enforcement Switches", required: false, multiple: true
                    input "shadeContact${i}", "capability.contactSensor", title: "Shade Open/Close Contact Sensor", required: false
                    input "roomShade${i}", "capability.windowShade", title: "Window Shade to Close", required: false
                    
                    paragraph "<b>Reading Lights</b>"
                    input "numReadingLights${i}", "enum", title: "<b>Number of Reading Lights</b>", options: ["0","1","2"], defaultValue: "0", submitOnChange: true
                    def numLights = settings["numReadingLights${i}"] ? settings["numReadingLights${i}"].toInteger() : 0
                    for (int l = 1; l <= numLights; l++) {
                        paragraph "<b>Reading Light ${l}</b>"
                        input "readingLight${l}_${i}", "capability.switchLevel", title: "Reading Light ${l} (Dimmer)", required: false
                        input "readingButton${l}_${i}", "capability.pushableButton", title: "Button for Light ${l}", required: false
                        input "readingButtonNum${l}_${i}", "number", title: "Button Number", required: false, defaultValue: 1
                        input "readingLevel${l}_${i}", "number", title: "Dim Level (%)", required: false, defaultValue: 30
                        input "readingTimeout${l}_${i}", "number", title: "Timeout (Minutes)", required: false, defaultValue: 60
                        input "readingModes${l}_${i}", "mode", title: "Only Allow in These Modes", multiple: true, required: false
                    }
                    
                    paragraph "<b>3. Sonos Audio Polish</b>"
                    input "roomSpeakerPower${i}", "capability.switch", title: "Sonos Speaker Power Plug (Optional)", required: false
                    input "roomSpeaker${i}", "capability.musicPlayer", title: "Sonos Speaker", required: false
                    input "audioStartDelay${i}", "number", title: "Audio Start Delay (Minutes)", required: false, defaultValue: 8
                    
                    paragraph "<b>Audio Track Configuration</b>"
                    paragraph "<div style='font-size:12px; color:#555;'><b>Tip:</b> For short local files, upload them to your Hub's File Manager and use the local link (e.g., http://HUB-IP/local/rain.mp3). Enable Repeat below to loop them endlessly.</div>"
                    input "audioSourceType${i}", "enum", title: "Audio Source Type", options: ["uri":"Direct Audio URIs", "switch":"Sonos Favorite Virtual Switches"], defaultValue: "uri", submitOnChange: true
                    if ((settings["audioSourceType${i}"] ?: "uri") == "uri") {
                        input "enableAudioRepeat${i}", "bool", title: "🔁 Enable Audio Repeat (Looping)", defaultValue: true
                        paragraph "<b>Track Loop Intervals (Seconds)</b><br><i style='font-size:12px; color:#555;'>Enter track length in seconds to forcefully loop it. Leave blank for no loop.</i>"
                        input "audioUri${i}_1", "text", title: "Audio URI 1", required: false
                        input "audioLoopInterval${i}_1", "number", title: "Loop Interval for URI 1", required: false
                        input "audioUri${i}_2", "text", title: "Audio URI 2", required: false
                        input "audioLoopInterval${i}_2", "number", title: "Loop Interval for URI 2", required: false
                        input "audioUri${i}_3", "text", title: "Audio URI 3", required: false
                        input "audioLoopInterval${i}_3", "number", title: "Loop Interval for URI 3", required: false
                    } else {
                        input "audioSwitch${i}_1", "capability.switch", title: "Favorite Switch 1", required: false
                        input "audioSwitch${i}_2", "capability.switch", title: "Favorite Switch 2", required: false
                        input "audioSwitch${i}_3", "capability.switch", title: "Favorite Switch 3", required: false
                    }
            
                    paragraph "<b>Audio Volume & Fade-In (Start of Sleep)</b>"
                    input "audioVolume${i}", "number", title: "Target Nighttime Volume (1-100)", required: false, defaultValue: 30
                    input "enableAudioFadeIn${i}", "bool", title: "<b>Enable Smooth Audio Fade-In</b>", submitOnChange: true
                    if (settings["enableAudioFadeIn${i}"]) {
                        input "audioFadeInDuration${i}", "number", title: "Fade-In Duration (Minutes)", defaultValue: 5, required: true
                        input "audioFadeInStartVol${i}", "number", title: "Starting Volume (%)", defaultValue: 1, required: true
                        input "audioFadeInUri${i}", "text", title: "Optional Override URI", required: false
                    }

                    paragraph "<b>Audio Timer & Fade-Out</b>"
                    input "audioTimer${i}", "number", title: "Total Sleep Timer: Stop audio after X minutes", required: false, submitOnChange: true
                    input "audioHardStopTime${i}", "time", title: "Hard Stop Time (Secondary Failsafe)", required: false
                    if (settings["audioTimer${i}"]) {
                        input "enableAudioFade${i}", "bool", title: "<b>Enable Smooth Fade-Out</b>", submitOnChange: true
                        if (settings["enableAudioFade${i}"]) {
                            input "audioFadeDuration${i}", "number", title: "Fade-Out Duration (Minutes)", defaultValue: 15, required: true
                            input "audioFadeMinVol${i}", "number", title: "Minimum Fade Volume (%)", defaultValue: 1, required: true
                        }
                    }
                  
                    input "enableASM${i}", "bool", title: "<b>Enable Sleep Metrics & AI Tracking</b>", submitOnChange: true
                    if (settings["enableASM${i}"]) {
                        paragraph "<b>4. Sleep Metrics & Biological Tracking</b>"
                        
                        input "enableDaytimeLockout${i}", "bool", title: "☀️ Enable Daytime Lockout (Block daytime bed usage from ruining sleep scores)?", defaultValue: true, submitOnChange: true
                        if (settings["enableDaytimeLockout${i}"]) {
                            input "daytimeLockoutStart${i}", "time", title: "Lockout START Time (e.g., 8:00 AM)", required: true, defaultValue: "08:00"
                            input "daytimeLockoutEnd${i}", "time", title: "Lockout END Time (e.g., 7:00 PM)", required: true, defaultValue: "19:00"
                        }
                        
                        input "synergySwitch${i}", "capability.switch", title: "Sleep Synergy Virtual Switch (Ceiling Fan Stage 1)", required: false
                        input "sleepFansOff${i}", "bool", title: "Turn OFF Standard Tower Fans during Stage 2?", defaultValue: true
                        input "synergySoftRamp_${i}", "bool", title: "Enable Soft-Ramp Restless Recovery (Stage 1 Reverts First)?", defaultValue: true
                        
                        input "enableOrchestrator${i}", "bool", title: "<b>Enable Auto-Orchestrator?</b>", defaultValue: true, submitOnChange: true
                        if (settings["enableOrchestrator${i}"]) {
                            input "goodNightDelayMin${i}", "number", title: "Minimum In-Bed Delay (Mins)", defaultValue: 5
                            input "goodNightDelayMax${i}", "number", title: "Maximum In-Bed Delay (Mins)", defaultValue: 30
                            input "expectedBedtime${i}", "time", title: "Expected Bedtime Setpoint", required: false
                            input "orchestratorStartTime${i}", "time", title: "Weekday Allowed Start Time", required: false
                            input "orchestratorEndTime${i}", "time", title: "Weekday Allowed End Time", required: false
                            input "orchestratorStartTimeWeekend${i}", "time", title: "Weekend Allowed Start Time (Optional)", required: false
                            input "orchestratorEndTimeWeekend${i}", "time", title: "Weekend Allowed End Time (Optional)", required: false
                        }

                        input "enableAutoWake${i}", "bool", title: "<b>Enable Auto-Wake?</b>", defaultValue: true, submitOnChange: true
                        if (settings["enableAutoWake${i}"]) {
                            input "autoWakeDelay${i}", "number", title: "Auto-Wake Delay (Minutes)", defaultValue: 5
                            input "autoWakeLockout${i}", "number", title: "Initial Wake Lockout (Minutes)", defaultValue: 120
                            input "autoWakeNightLock${i}", "bool", title: "🌙 Night Lock (Block Auto-Wake before Morning Schedule)?", defaultValue: true, submitOnChange: true
                        }
                        
                        input "motionSensor_${i}", "capability.motionSensor", title: "Fallback Room Motion Sensor", required: true
                        input "bathroomMotion_${i}", "capability.motionSensor", title: "Fallback En-Suite Bathroom Motion", required: false
                        
                        input "fallAsleepThreshold${i}", "number", title: "Fall Asleep Duration (Minutes)", defaultValue: 15
                        input "exitBedThreshold${i}", "number", title: "Bed Exit Delay (Minutes)", defaultValue: 5
                        input "stitchingWindow${i}", "number", title: "Standard Stitching Window (Minutes)", defaultValue: 15
                        input "enableSettlingLock${i}", "bool", title: "🔒 Enable Settling Lock?", defaultValue: true, submitOnChange: true
                        if (settings["enableSettlingLock${i}"]) input "settlingLockTime${i}", "number", title: "Settling Lock Duration (Mins)", defaultValue: 30
                        
                        input "enableGhostFilter${i}", "bool", title: "👻 Enable Pre-Emptive Presence Lockout?", defaultValue: true
                        input "enableTeleportFilter${i}", "bool", title: "🚫 Enable Teleportation Filter?", defaultValue: true
                        if (settings["enableTeleportFilter${i}"]) input "teleportWindow${i}", "number", title: "Teleportation Window (Mins)", defaultValue: 10
                        
                        input "enableAntiBounce${i}", "bool", title: "🛏️ Enable Sustained Entry Verification?", defaultValue: true, submitOnChange: true
                        if (settings["enableAntiBounce${i}"]) input "antiBounceWait${i}", "number", title: "Verification Wait Time (Mins)", defaultValue: 3
                        
                        input "enableCrossTalk${i}", "bool", title: "Enable Multi-User Kinetic Shielding?", defaultValue: true, submitOnChange: true
                        if (settings["enableCrossTalk${i}"]) input "crossTalkDeafenTime${i}", "number", title: "Shield Duration (Seconds)", defaultValue: 60, required: true

                        input "numOccupants${i}", "enum", title: "<b>Number of Occupants in Room</b>", options: ["1", "2"], defaultValue: "1", submitOnChange: true
                        
                        def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
                        for (int u = 1; u <= numUsers; u++) {
                            def uId = "${i}_${u}"
                            paragraph "<b>Occupant ${u} Settings</b>"
                            input "userName_${uId}", "text", title: "Occupant ${u} Name", defaultValue: "User ${u}", submitOnChange: true
                            input "occupantPresence_${uId}", "capability.presenceSensor", title: "🏠 Presence Sensor (Ignore occupant if AWAY)", required: false
                            input "pressureMat_${uId}", "capability.contactSensor", title: "Bed Presence Sensor / Pressure Mat (Required)", required: true
                            input "vibrationSensor_${uId}", "capability.accelerationSensor", title: "Kinetic Sensor (Mattress Frame - Optional)", required: false
                            input "vibrationSensor2_${uId}", "capability.accelerationSensor", title: "Kinetic Sensor (Headboard - Optional)", required: false
                            
                            paragraph "<b>Analog Pressure & Posture Tracking</b>"
                            input "enableAnalogPressure_${uId}", "bool", title: "Enable Analog Pressure Tracking?", defaultValue: false, submitOnChange: true
                            if (settings["enableAnalogPressure_${uId}"]) {
                                input "pressureDeltaThreshold_${uId}", "number", title: "Movement Delta Threshold", defaultValue: 15
                                input "pressureSittingThreshold_${uId}", "number", title: "Sitting Absolute Threshold", defaultValue: 80
                                input "autoReadingLight_${uId}", "bool", title: "Auto-Turn On Reading Light When Sitting?", defaultValue: false
                                if (settings["autoReadingLight_${uId}"]) {
                                    input "linkedReadingLight_${uId}", "enum", title: "Link to Reading Light #", options: ["1","2"], required: true
                                }
                            }
                            
                            input "notificationDevice_${uId}", "capability.notification", title: "Notification Device for ${settings["userName_${uId}"] ?: "Occupant"}", required: false, multiple: true
                            if (settings["notificationDevice_${uId}"]) {
                                input "poorSleepThreshold_${uId}", "number", title: "Poor Sleep Score Threshold (%)", defaultValue: 75
                            }
                            
                            input "sleepScoreVariable_${uId}", "hubVariable", title: "Link Hub Variable for Sleep Score", required: false, multiple: false
                            input "effortScoreInputVar_${uId}", "hubVariable", title: "Link Hub Variable for Daily Workout Effort Score", required: false, multiple: false
                            input "moodVariable_${uId}", "hubVariable", title: "Link Hub Variable for Mood (Syncs with Dashboard)", required: false, multiple: false
                            
                            input "enableML_${uId}", "bool", title: "Enable AI Learning?", defaultValue: true
                            if (settings["enableML_${uId}"]) {
                                input "tailoredBedtimeSwitch_${uId}", "capability.switch", title: "Tailored Bedtime Trigger Switch (Turns ON at tailored bedtime, OFF after 10m)", required: false
                            }
                            
                            input "enableClinicalScoring_${uId}", "bool", title: "Enable Clinical Scoring (BMS)?", submitOnChange: true
                            if (settings["enableClinicalScoring_${uId}"]) input "targetSleepHours_${uId}", "decimal", title: "Target Sleep Hours", defaultValue: 7.5
                            
                            input "enableAdvancedStages_${uId}", "bool", title: "Enable EWMA Sleep Stages?", defaultValue: true
                            input "enableCircadianScaling_${uId}", "bool", title: "Enable Circadian Movement Scaling?", defaultValue: true
                            
                            input "enableSmartAlarm_${uId}", "bool", title: "⏰ Enable Predictive Smart Alarm?", defaultValue: false, submitOnChange: true
                            if (settings["enableSmartAlarm_${uId}"]) {
                                input "smartAlarmSwitch_${uId}", "capability.switch", title: "Smart Alarm Trigger Switch", required: true
                                input "weekdayWakeStart_${uId}", "time", title: "Expected Wake Start (Mon-Fri)", required: true
                                input "weekdayWakeEnd_${uId}", "time", title: "Expected Wake End (Mon-Fri)", required: true
                                input "weekendWakeStart_${uId}", "time", title: "Expected Wake Start (Sat-Sun)", required: true
                                input "weekendWakeEnd_${uId}", "time", title: "Expected Wake End (Sat-Sun)", required: true
                            }
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

String getHumanReadableStatus() {
    def isFire = false
    if (emergencyAlarms && emergencyAlarms.any { it.currentValue("smoke") == "detected" }) isFire = true
    if (emergencyCO && emergencyCO.any { it.currentValue("carbonMonoxide") == "detected" }) isFire = true
    if (isFire) return "<span style='color:red; font-size:14px;'><b>🚨 CRITICAL: FIRE / CO ISOLATION ACTIVE. ALARMS TRIGGERED. 🚨</b></span>"
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "The application is disabled via the Master Enable Switch."
    
    def asleepCount = 0
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableRoom${i}"]) {
            def sw = settings["roomSwitch${i}"]
            if (sw?.currentValue("switch") == "on") asleepCount++
        }
    }
    if (asleepCount > 0) return "<span style='color:#2e154f;'><b>Active:</b> ${asleepCount} room(s) currently ASLEEP. Monitoring environment and biologicals.</span>"
    else return "<span style='color:green;'><b>Standby:</b> All configured rooms are AWAKE.</span>"
}

def appButtonHandler(btn) {
    if (btn == "refreshDataBtn") logInfo("Manual UI Data Refresh Triggered.")
    else if (btn == "clearLogBtn") { state.eventLog = []; logInfo("Cleared command history log.") }
    else if (btn == "forceEvalBtn") periodicEnforcementHandler()
    else if (btn == "clearMlBtn") { state.learnedBedTimes = [:]; state.learnedWakeTimes = [:]; logInfo("Cleared all learned AI sleep patterns.") }
    else if (btn == "clearScoresBtn") { state.sleepScoreHistory = [:]; state.avgSleepScore = [:]; logInfo("Cleared all historical sleep scores for all users.") }
    else if (btn == "btnForceEmpty_ALL") forceResetAllBeds()
    else if (btn == "btnForceOcc_ALL") forceAllBedsOccupied()
    else if (btn.startsWith("btnForceEmpty_")) forceRoomBedsState(btn.split("_")[1], "EMPTY")
    else if (btn.startsWith("btnForceOcc_")) forceRoomBedsState(btn.split("_")[1], "IN BED")
    else if (btn.startsWith("restartRoomBtn_")) {
        def rNum = btn.split("_")[1].toInteger()
        restartRoomSequence(rNum)
    }
}

def restartRoomSequence(roomNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    if (!state."roomAsleepStatus${roomNum}") {
        logInfo("${rName}: Restart Sequence requested, but room is NOT asleep. Ignoring command.")
        return
    }
    
    logInfo("${rName}: Manual Restart Sequence Button Pressed. Re-triggering Good Night Sequence.")
    
    // Clear existing timers/fades
    state.remove("fadeActive${roomNum}")
    state.remove("fadeInActive${roomNum}")
    state.remove("lightFadeActive${roomNum}")
    unschedule("lightFadeCompleteRoom${roomNum}")
    unschedule("startAudioFadeInRoom${roomNum}")
    unschedule("startAudioFadeRoom${roomNum}")
    unschedule("playDelayedAudioRoom${roomNum}")
    unschedule("stopAudioRoom${roomNum}")
    unschedule("applyDelayedVolumeRoom${roomNum}")
    unschedule("delayedOffRoom${roomNum}")
    unschedule("audioLoopHandlerRoom${roomNum}")
    
    // Stop speaker and allow 1 second for hardware state to settle
    def speaker = settings["roomSpeaker${roomNum}"]
    if (speaker) {
        if (speaker.hasCommand("stop")) speaker.stop()
        pauseExecution(1000)
    }
    
    // Re-execute Good Night sequence (prepNextAudio is called inside executeRoomGoodNight)
    executeRoomGoodNight(roomNum)
}

def forceAllBedsOccupied() {
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) forceRoomBedsState(i.toString(), "IN BED")
    logInfo("ASM: Forced all beds to OCCUPIED via manual UI override.")
}

def forceRoomBedsState(rNum, targetState) {
    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    def now = new Date().time
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        if (targetState == "EMPTY") {
            state.sleepState["${uId}"] = "EMPTY"
            state.inBedTime["${uId}"] = null
            state.asleepTime["${uId}"] = null
            state.pendingExit["${uId}"] = 0
            state.movements["${uId}"] = 0
            state.posture["${uId}"] = "LYING_FLAT"
            updateRoomSleepState(rNum)
        } else if (targetState == "IN BED") {
            state.sleepState["${uId}"] = "IN BED"
            state.inBedTime["${uId}"] = now
            state.latencyClockStart["${uId}"] = now
            state.asleepTime["${uId}"] = null
            state.pendingExit["${uId}"] = 0
            if (!state.sessionStartTime["${uId}"]) state.sessionStartTime["${uId}"] = now
            updateRoomSleepState(rNum)
        }
    }
    logInfo("ASM: Forced Room ${rNum} beds to ${targetState}.")
}

def installed() { logInfo("Installed and initialized."); initialize() }
def updated() { logInfo("Updated. Re-initializing."); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.eventLog) state.eventLog = []
    subscribe(location, "systemStart", hubRebootHandler)
    if (enablePeriodicEnforcement) runEvery10Minutes("periodicEnforcementHandler")
    if (emergencyAlarms) subscribe(emergencyAlarms, "smoke.detected", emergencyHandler)
    if (emergencyCO) subscribe(emergencyCO, "carbonMonoxide.detected", emergencyHandler)
    
    def anyAsmEnabled = false
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) if (settings["enableASM${i}"]) anyAsmEnabled = true
    
    if (anyAsmEnabled) {
        ensureStateMaps()
        schedule("0 0 12 * * ?", "middayReset")
        schedule("0 0/5 * * * ?", "orchestrateRooms") 
        schedule("0 0 14 * * ?", "scheduleEveningNotifications")
        
        runIn(5, "scheduleEveningNotifications")
        
        for (int i = 1; i <= maxRooms; i++) {
            def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
            for (int u = 1; u <= numUsers; u++) {
                def varName = settings["effortScoreInputVar_${i}_${u}"]
                if (varName) subscribe(location, "variable:${varName}", effortScoreInputHandler)
            }
        }
    }
    
    if (settings.enableMatPolling) {
        def pInt = settings.matPollingInterval ? settings.matPollingInterval.toInteger() : 15
        if (pInt == 5) runEvery5Minutes("refreshPressureMats")
        else if (pInt == 10) runEvery10Minutes("refreshPressureMats")
        else runEvery15Minutes("refreshPressureMats")
    }
    
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableRoom${i}"]) {
            if (settings["roomSwitch${i}"]) subscribe(settings["roomSwitch${i}"], "switch", roomSwitchHandler)
            if (settings["tempSensor${i}"]) subscribe(settings["tempSensor${i}"], "temperature", tempHandler)
            if (settings["gnButton${i}"]) subscribe(settings["gnButton${i}"], (settings["gnButtonAction${i}"] ?: "pushed"), goodNightButtonHandler)
            
            def numLights = settings["numReadingLights${i}"] ? settings["numReadingLights${i}"].toInteger() : 0
            for (int l = 1; l <= numLights; l++) {
                if (settings["readingButton${l}_${i}"]) subscribe(settings["readingButton${l}_${i}"], "pushed", readingButtonHandler)
            }
            if (settings["audioHardStopTime${i}"]) schedule(settings["audioHardStopTime${i}"], "hardStopAudioRoom${i}")
            prepNextAudio(i)
            
            if (settings["enableASM${i}"]) {
                if (settings["motionSensor_${i}"]) subscribe(settings["motionSensor_${i}"], "motion", fallbackMotionHandler)
                if (settings["bathroomMotion_${i}"]) subscribe(settings["bathroomMotion_${i}"], "motion", fallbackBathroomMotionHandler)
                
                def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
                for (int u = 1; u <= numUsers; u++) {
                    def uId = "${i}_${u}"
                    if (settings["pressureMat_${uId}"]) {
                        subscribe(settings["pressureMat_${uId}"], "contact", pressureMatHandler)
                        subscribe(settings["pressureMat_${uId}"], "presence", pressureMatHandler) 
                        if (settings["enableAnalogPressure_${uId}"]) subscribe(settings["pressureMat_${uId}"], "pressure", analogPressureHandler)
                    }
                    def vSensor = settings["vibrationSensor_${uId}"]
                    if (vSensor) subscribe(vSensor, "acceleration", vibrationHandler)
                    def vSensor2 = settings["vibrationSensor2_${uId}"]
                    if (vSensor2) subscribe(vSensor2, "acceleration", vibrationHandler)
                }
            }
        }
    }
}

// --- EFFORT SCORE SYNC HANDLER ---
def effortScoreInputHandler(evt) {
    def varName = evt.name
    def val = evt.value
    if (!val || !val.toString().isNumber()) return
    
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) {
        def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            if (settings["effortScoreInputVar_${i}_${u}"] == varName) {
                state.dailyEffortScore["${uId}"] = val.toInteger()
                if (txtLogEnable) log.debug "ASM: Updated daily effort score for ${uId} to ${val} pts."
            }
        }
    }
    
    scheduleEveningNotifications()
}

// --- THERMAL STAGING SYNC ---
def updateRoomSleepState(rNum) {
    if (!settings["enableASM${rNum}"]) return
    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    def allAsleep = true
    def anyInBed = false
    
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        def presenceDev = settings["occupantPresence_${uId}"]
        def isAway = (presenceDev && presenceDev.currentValue("presence") == "not present")
        
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        
        if (isAway && cState == "EMPTY") continue 
        
        if (cState == "IN BED" || cState == "BATHROOM TRIP" || cState == "PENDING ENTRY") allAsleep = false
        if (cState != "EMPTY") anyInBed = true
    }
    
    if (!anyInBed) allAsleep = false
    def currentlySleeping = state."roomIsSleeping${rNum}" ?: false
    
    if (allAsleep && !currentlySleeping) {
        state."roomIsSleeping${rNum}" = true
        logInfo("ASM Synergy: All occupants in Room ${rNum} are now SLEEPING. Waiting for Deep Sleep threshold to trigger Staged Synergy.")
        evaluateThermalStaging(rNum)
        
    } else if (!allAsleep && currentlySleeping) {
        state."roomIsSleeping${rNum}" = false
        logInfo("ASM Synergy: Room ${rNum} is no longer fully asleep. Releasing Staged Synergy Tracking.")
        
        state.remove("synergyStage1Time${rNum}")
        state.remove("synergyStage2Active${rNum}")
        state.remove("synergySoftRampActive${rNum}")
        
        def synSw = settings["synergySwitch${rNum}"]
        if (synSw && synSw.currentValue("switch") != "off") synSw.off()
        evaluateFans(rNum)
    }
}

def evaluateThermalStaging(rNum) {
    if (!state."roomIsSleeping${rNum}") return
    
    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    def isDeepSleep = true
    def now = new Date().time
    
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        def presenceDev = settings["occupantPresence_${uId}"]
        def isAway = (presenceDev && presenceDev.currentValue("presence") == "not present")
        
        if (isAway && cState == "EMPTY") continue
        
        if (cState != "SLEEPING") { isDeepSleep = false; break }
        
        if (settings["enableAdvancedStages_${uId}"]) {
            // Live calculate EWMA instead of relying on static sleep stage state
            def lastMove = state.lastMoveTimeForEwma["${uId}"] ?: 0
            def diffMins = (now - lastMove) / 60000.0
            def decay = Math.pow(0.5, diffMins / 10.0)
            def liveEwma = (state.ewmaMovement["${uId}"] ?: 0.0) * decay
            
            def asleepStart = state.asleepTime["${uId}"] ?: now
            def hoursAsleep = (now - asleepStart) / 3600000.0
            def stageThreshold = 1.1
            if (hoursAsleep <= 3.0) stageThreshold = 1.5
            else if (hoursAsleep >= 5.0) stageThreshold = 0.8
            
            if (liveEwma > stageThreshold) { isDeepSleep = false; break }
        } else {
            def lastMove = state.lastMoveTimeForEwma["${uId}"] ?: 0
            if ((now - lastMove) < (5 * 60000)) { isDeepSleep = false; break } 
        }
    }
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    def hour = cal.get(Calendar.HOUR_OF_DAY)
    def isSynergyWindow = (hour >= 1 && hour < 10) 
    
    if (isDeepSleep && isSynergyWindow) {
        state.remove("synergySoftRampActive${rNum}") 
        
        def synSw = settings["synergySwitch${rNum}"]
        if (!state."synergyStage1Time${rNum}") {
            state."synergyStage1Time${rNum}" = now
            if (synSw && synSw.currentValue("switch") != "on") synSw.on()
            logInfo("ASM Synergy: Phase 1 - Deep Sleep threshold met. Triggering Synergy Switch (Ceiling Fan Staged).")
        }
        
        def stage1Time = state."synergyStage1Time${rNum}" ?: 0
        if (stage1Time > 0 && (now - stage1Time) >= 3600000) { 
            if (!state."synergyStage2Active${rNum}" && settings["sleepFansOff${rNum}"]) {
                state."synergyStage2Active${rNum}" = true
                settings["roomFans${rNum}"]?.each { fan -> if(fan.currentValue("switch") != "off") fan.off() }
                logInfo("ASM Synergy: Phase 2 - 1 hour has passed with undisturbed deep sleep. Tower fans forced OFF.")
            }
        }
        
    } else if (!isDeepSleep && state."synergyStage1Time${rNum}") {
        if (settings["synergySoftRamp_${rNum}"] && state."synergyStage2Active${rNum}" && !state."synergySoftRampActive${rNum}") {
            logInfo("ASM Synergy: RESTLESSNESS DETECTED. Initiating Soft-Ramp Recovery. Reverting Ceiling Fan (Phase 1) first. Tower fans hold OFF for 10 mins.")
            state."synergySoftRampActive${rNum}" = now
            
            state.remove("synergyStage1Time${rNum}")
            def synSw = settings["synergySwitch${rNum}"]
            if (synSw && synSw.currentValue("switch") != "off") synSw.off()
            
            runIn(600, "evaluateSoftRampFailsafe", [data: [roomNum: rNum], overwrite: false])
        } else if (!state."synergySoftRampActive${rNum}") {
            logInfo("ASM Synergy: RESTLESSNESS DETECTED. Hard reverting all Thermal Staging to prioritize sleep quality.")
            state.remove("synergyStage1Time${rNum}")
            state.remove("synergyStage2Active${rNum}")
            def synSw = settings["synergySwitch${rNum}"]
            if (synSw && synSw.currentValue("switch") != "off") synSw.off()
            evaluateFans(rNum)
        }
    }
}

def evaluateSoftRampFailsafe(data) {
    def rNum = data.roomNum
    if (!state."synergySoftRampActive${rNum}") return 
    
    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    def isDeepSleep = true
    def now = new Date().time
    
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        def presenceDev = settings["occupantPresence_${uId}"]
        def isAway = (presenceDev && presenceDev.currentValue("presence") == "not present")
        
        if (isAway && cState == "EMPTY") continue
        
        if (cState != "SLEEPING") { isDeepSleep = false; break }
        
        if (settings["enableAdvancedStages_${uId}"]) {
            // Live calculate EWMA instead of relying on static sleep stage state
            def lastMove = state.lastMoveTimeForEwma["${uId}"] ?: 0
            def diffMins = (now - lastMove) / 60000.0
            def decay = Math.pow(0.5, diffMins / 10.0)
            def liveEwma = (state.ewmaMovement["${uId}"] ?: 0.0) * decay
            
            def asleepStart = state.asleepTime["${uId}"] ?: now
            def hoursAsleep = (now - asleepStart) / 3600000.0
            def stageThreshold = 1.1
            if (hoursAsleep <= 3.0) stageThreshold = 1.5
            else if (hoursAsleep >= 5.0) stageThreshold = 0.8
            
            if (liveEwma > stageThreshold) { isDeepSleep = false; break }
        } else {
            def lastMove = state.lastMoveTimeForEwma["${uId}"] ?: 0
            if ((now - lastMove) < (5 * 60000)) { isDeepSleep = false; break } 
        }
    }
    
    if (!isDeepSleep) {
        logInfo("ASM Synergy: Soft-Ramp Timer Expired. Restlessness continues. Hard reverting Phase 2 (Tower Fans ON).")
        state.remove("synergyStage2Active${rNum}")
        state.remove("synergySoftRampActive${rNum}")
        evaluateFans(rNum)
    } else {
        logInfo("ASM Synergy: Soft-Ramp Successful. Deep sleep resumed. Retaining Phase 2 (Tower Fans OFF).")
        state.remove("synergySoftRampActive${rNum}")
        evaluateThermalStaging(rNum) 
    }
}

// --- PRESSURE MAT KEEP-ALIVE ---
def refreshPressureMats() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    def refreshedCount = 0
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableRoom${i}"] && settings["enableASM${i}"]) {
            def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
            for (int u = 1; u <= numUsers; u++) {
                def pMat = settings["pressureMat_${i}_${u}"]
                if (pMat && pMat.hasCommand("refresh")) { pMat.refresh(); refreshedCount++; pauseExecution(500) }
            }
        }
    }
    if (refreshedCount > 0 && settings.txtLogEnable) log.debug "ASM Keep-Alive: Sent refresh() command to ${refreshedCount} pressure mat(s)."
}

// --- LIFE SAFETY EMERGENCY OVERRIDE ---
def emergencyHandler(evt) {
    logInfo("🚨 EMERGENCY: ${evt.name.toUpperCase()} DETECTED BY ${evt.device.displayName}! INITIATING LIFE-SAFETY OVERRIDE 🚨")
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableRoom${i}"]) {
            state.remove("delayedOffCompleted${i}")
            state.remove("fadeActive${i}")
            state.remove("fadeInActive${i}")
            state.remove("lightFadeActive${i}")
            state.remove("autoWakePending${i}")
            unschedule("lightFadeCompleteRoom${i}")
            unschedule("startAudioFadeInRoom${i}")
            unschedule("startAudioFadeRoom${i}")
            unschedule("playDelayedAudioRoom${i}")
            unschedule("stopAudioRoom${i}")
            unschedule("applyDelayedVolumeRoom${i}")
            unschedule("delayedOffRoom${i}")
            unschedule("audioLoopHandlerRoom${i}")
            
            settings["roomFans${i}"]?.off()
            settings["roomSpeaker${i}"]?.stop()
            
            settings["roomLights${i}"]?.each { lgt -> if (lgt.hasCommand("setLevel")) lgt.setLevel(100); else lgt.on() }
            settings["roomLightsOn${i}"]?.each { lgt -> if (lgt.hasCommand("setLevel")) lgt.setLevel(100); else lgt.on() }
            logInfo("Room ${i}: Emergency Protocol Executed - Fans OFF, Audio STOPPED, Lights 100%.")
            if (settings["enableASM${i}"]) forceAsmUsersAwake(i)
        }
    }
}

// --- GOOD NIGHT BUTTON TOGGLE ENGINE ---
def goodNightButtonHandler(evt) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    def btnId = evt.device.id
    def btnNum = evt.value
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1

    for (int i = 1; i <= maxRooms; i++) {
        if (!settings["enableRoom${i}"]) continue
        def confBtn = settings["gnButton${i}"]
        def confNum = settings["gnButtonNum${i}"]?.toString() ?: "1"
        def confAction = settings["gnButtonAction${i}"] ?: "pushed"

        if (confBtn && confBtn.id == btnId && evt.name == confAction && btnNum == confNum) {
            def lastPress = state."lastBtnPress${i}" ?: 0
            if (now() - lastPress < 3000) { logInfo("Room ${i}: Hardware button bounce detected. Ignoring duplicate."); return }
            state."lastBtnPress${i}" = now()

            def rModes = settings["gnButtonModes${i}"]
            if (rModes && !(rModes as List).contains(location.mode)) return

            def sw = settings["roomSwitch${i}"]
            if (sw) {
                if (sw.currentValue("switch") == "on") { 
                    logInfo("Room ${i}: Toggle button pressed. Turning OFF.")
                    sw.off() 
                } 
                else { 
                    def switchLockout = state."gnSwitchLockout_${i}" ?: 0
                    if (now() < switchLockout) {
                        def remain = ((switchLockout - now()) / 1000).toInteger()
                        logInfo("Room ${i}: Toggle button pressed but BLOCKED. 5-minute cool-down active (${remain}s remaining).")
                        return
                    }
                    logInfo("Room ${i}: Toggle button pressed. Turning ON.")
                    sw.on() 
                }
            }
        }
    }
}

// --- READING LIGHT BUTTON ENGINE ---
def readingButtonHandler(evt) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    def btnId = evt.device.id
    def btnNum = evt.value
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1

    for (int i = 1; i <= maxRooms; i++) {
        if (!settings["enableRoom${i}"]) continue
        def numLights = settings["numReadingLights${i}"] ? settings["numReadingLights${i}"].toInteger() : 0
        for (int l = 1; l <= numLights; l++) {
            def confBtn = settings["readingButton${l}_${i}"]
            def confNum = settings["readingButtonNum${l}_${i}"]?.toString() ?: "1"
            if (confBtn && confBtn.id == btnId && btnNum == confNum) { toggleReadingMode(i, l); return }
        }
    }
}

def toggleReadingMode(roomNum, lightNum, forceOn = false, forceOff = false) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def rLight = settings["readingLight${lightNum}_${roomNum}"]
    def rLevel = settings["readingLevel${lightNum}_${roomNum}"] != null ? settings["readingLevel${lightNum}_${roomNum}"].toInteger() : 30
    def rTimeout = settings["readingTimeout${lightNum}_${roomNum}"] != null ? settings["readingTimeout${lightNum}_${roomNum}"].toInteger() : 60
    def rModes = settings["readingModes${lightNum}_${roomNum}"]

    if (!rLight) return
    if (rModes && !(rModes as List).contains(location.mode)) return

    def isActive = state."readingModeActive_${roomNum}_${lightNum}"

    if (forceOff && isActive) {
        logInfo("${rName}: Reading Light ${lightNum} OFF (Auto-Posture Update).")
        endReadingMode(roomNum, lightNum)
        return
    }
    if (forceOn && !isActive) {
        logInfo("${rName}: Reading Light ${lightNum} ON (Auto-Posture Trigger). Level: ${rLevel}%, Timer: ${rTimeout}m.")
        state."readingModeActive_${roomNum}_${lightNum}" = true
        rLight.setLevel(rLevel)
        runIn(rTimeout * 60, "readingTimeoutRoom${roomNum}Light${lightNum}")
        return
    }
    if (!forceOn && !forceOff) {
        if (isActive) {
            logInfo("${rName}: Reading Light ${lightNum} OFF (Toggled manually).")
            endReadingMode(roomNum, lightNum)
        } else {
            logInfo("${rName}: Reading Light ${lightNum} ON. Level: ${rLevel}%, Timer: ${rTimeout}m.")
            state."readingModeActive_${roomNum}_${lightNum}" = true
            rLight.setLevel(rLevel)
            runIn(rTimeout * 60, "readingTimeoutRoom${roomNum}Light${lightNum}")
        }
    }
}

def readingTimeoutRoom1Light1() { endReadingMode(1, 1) }
def readingTimeoutRoom1Light2() { endReadingMode(1, 2) }
def readingTimeoutRoom2Light1() { endReadingMode(2, 1) }
def readingTimeoutRoom2Light2() { endReadingMode(2, 2) }
def readingTimeoutRoom3Light1() { endReadingMode(3, 1) }
def readingTimeoutRoom3Light2() { endReadingMode(3, 2) }
def readingTimeoutRoom4Light1() { endReadingMode(4, 1) }
def readingTimeoutRoom4Light2() { endReadingMode(4, 2) }
def readingTimeoutRoom5Light1() { endReadingMode(5, 1) }
def readingTimeoutRoom5Light2() { endReadingMode(5, 2) }

def endReadingMode(roomNum, lightNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def rLight = settings["readingLight${lightNum}_${roomNum}"]
    
    state."readingModeActive_${roomNum}_${lightNum}" = false
    unschedule("readingTimeoutRoom${roomNum}Light${lightNum}")
    if (rLight) rLight.off()
    
    def anyReadingOn = false
    def numLights = settings["numReadingLights${roomNum}"] ? settings["numReadingLights${roomNum}"].toInteger() : 0
    for (int l = 1; l <= numLights; l++) {
        if (state."readingModeActive_${roomNum}_${l}") anyReadingOn = true
    }
    
    if (!anyReadingOn && !state."lightFadeActive${roomNum}") {
        def numUsers = settings["numOccupants${roomNum}"] ? settings["numOccupants${roomNum}"].toInteger() : 1
        for (int u = 1; u <= numUsers; u++) {
            def tuId = "${roomNum}_${u}"
            if (state.sleepState["${tuId}"] == "IN BED") {
                state.latencyClockStart["${tuId}"] = new Date().time
                logInfo("ASM: Room is now dark. Initiating true sleep latency clock for Occupant ${tuId}.")
            }
        }
    }
}

def isReadingLightActive(roomNum, lightDeviceId) {
    def numLights = settings["numReadingLights${roomNum}"] ? settings["numReadingLights${roomNum}"].toInteger() : 0
    for (int l = 1; l <= numLights; l++) {
        if (state."readingModeActive_${roomNum}_${l}") {
            def rLight = settings["readingLight${l}_${roomNum}"]
            if (rLight && rLight.id == lightDeviceId) return true
        }
    }
    return false
}

// --- PERIODIC STATE ENFORCEMENT ---
def periodicEnforcementHandler() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    def anyoneAsleep = false
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    
    for (int i = 1; i <= maxRooms; i++) if (state."roomAsleepStatus${i}") { anyoneAsleep = true; break }
    
    if (anyoneAsleep) {
        if (txtLogEnable) log.debug "PERIODIC ENFORCEMENT: Waking up to verify system state..."
        
        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableRoom${i}"] && state."roomAsleepStatus${i}") {
                def rName = settings["roomName${i}"] ?: "Room ${i}"
                def pauseEnforce = settings["pauseLightingEnforcement${i}"]
                def isPaused = pauseEnforce?.any { it.currentValue("switch") == "on" }
                
                if (state."lightFadeActive${i}") {
                    if (txtLogEnable) log.debug "ENFORCEMENT: Skipping ${rName} light checks (Smooth Light Fade active)."
                } else if (!isPaused) {
                    settings["roomLights${i}"]?.each { lgt -> 
                        if (lgt.currentValue("switch") == "on") {
                            if (isReadingLightActive(i, lgt.id)) return 
                            if (lgt.hasCommand("setLevel")) { lgt.setLevel(1); pauseExecution(400) }
                            lgt.off()
                            logInfo("ENFORCEMENT: ${rName} light [${lgt.displayName}] was ON. Forced OFF.")
                        }
                    }
                    
                    def delaySwitches = settings["delayedOffSwitches${i}"]
                    def delayComplete = state."delayedOffCompleted${i}"
                    settings["roomLightsOn${i}"]?.each { lgt ->
                        if (lgt.currentValue("switch") == "off") {
                            def isDelayedLgt = delaySwitches?.find { it.id == lgt.id }
                            if (isDelayedLgt && delayComplete) {
                            } else {
                                lgt.on()
                                logInfo("ENFORCEMENT: ${rName} bedtime light [${lgt.displayName}] was OFF. Forced ON.")
                            }
                        }
                    }
                }
                
                def shadeContact = settings["shadeContact${i}"]
                def shade = settings["roomShade${i}"]
                if (shadeContact && shade && shadeContact.currentValue("contact") == "open") {
                    shade.close()
                    logInfo("ENFORCEMENT: ${rName} shade contact was OPEN. Forced CLOSE.")
                }

                evaluateFans(i)
                evaluateThermalStaging(i)
            }
        }
    }
}

// --- NIGHTTIME MESH RECOVERY ---
def hubRebootHandler(evt) {
    logInfo("SYSTEM BOOT: Hub reboot detected. Scheduling nighttime recovery scan in 60s...")
    runIn(60, "executeNighttimeRecovery")
}

def executeNighttimeRecovery() {
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    def recoveryTriggered = false
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableRoom${i}"] && state."roomAsleepStatus${i}") {
            logInfo("RECOVERY: Room ${i} was ASLEEP before power loss. Re-applying Good Night environment...")
            def sw = settings["roomSwitch${i}"]
            if (sw && sw.currentValue("switch") != "on") { sw.on(); pauseExecution(500) }
            executeRoomGoodNight(i)
            recoveryTriggered = true
        }
    }
    if (!recoveryTriggered) logInfo("RECOVERY: Scan complete. No rooms were actively asleep prior to power loss.")
}

def calculateSleepSuitability(cTemp, cHum) {
    if (cTemp == null) return "<span style='color:gray;'>Awaiting Sensor Data...</span>"
    def tempStatus = ""
    def humStatus = ""
    def color = "green"
    
    if (cTemp < 60.0) { tempStatus = "Too Cold"; color = "blue" }
    else if (cTemp >= 60.0 && cTemp <= 69.0) { tempStatus = "Optimal Temp" }
    else { tempStatus = "Too Warm"; color = "red" }
    
    if (cHum != null) {
        if (cHum < 30.0) humStatus = " & Dry"
        else if (cHum >= 30.0 && cHum <= 50.0) humStatus = " & Ideal Humidity"
        else { humStatus = " & Humid"; color = "orange" }
    }
    
    def finalStatus = tempStatus + humStatus
    if (finalStatus.contains("Optimal Temp") && (humStatus == "" || humStatus.contains("Ideal"))) return "<span style='color:green; font-weight:bold;'>Perfect 🌙</span>"
    return "<span style='color:${color};'>${finalStatus}</span>"
}

// --- SWITCH DEBOUNCE ---
def roomSwitchHandler(evt) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    def roomNum = null
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableRoom${i}"] && settings["roomSwitch${i}"]?.id == evt.device.id) { roomNum = i; break }
    }
    if (!roomNum) return
    def debounceSecs = settings.switchDebounceDelay != null ? settings.switchDebounceDelay.toInteger() : 3

    if (debounceSecs > 0) {
        if (txtLogEnable) log.debug "Debounce active: Delaying eval for Room ${roomNum} by ${debounceSecs}s."
        runIn(debounceSecs, "commitRoomSwitch${roomNum}")
    } else commitRoomSwitch(roomNum)
}

def commitRoomSwitch1() { commitRoomSwitch(1) }
def commitRoomSwitch2() { commitRoomSwitch(2) }
def commitRoomSwitch3() { commitRoomSwitch(3) }
def commitRoomSwitch4() { commitRoomSwitch(4) }
def commitRoomSwitch5() { commitRoomSwitch(5) }

def isWakeTimeReached(roomNum) {
    def numUsers = settings["numOccupants${roomNum}"] ? settings["numOccupants${roomNum}"].toInteger() : 1
    def earliestWakeMins = 1440 
    def hasValidWakeTime = false
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    def currentMins = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
    
    if (currentMins >= 17 * 60) return false 
    
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${roomNum}_${u}"
        def lTime = getLearnedTime(uId, true) 
        
        if (lTime == null && settings["enableSmartAlarm_${uId}"]) {
            def isWeekend = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            def wStart = isWeekend ? settings["weekendWakeStart_${uId}"] : settings["weekdayWakeStart_${uId}"]
            if (wStart) {
                def wDate = timeToday(wStart, tz)
                def wCal = Calendar.getInstance(tz)
                wCal.setTime(wDate)
                lTime = (wCal.get(Calendar.HOUR_OF_DAY) * 60) + wCal.get(Calendar.MINUTE)
            }
        }
        
        if (lTime == null && settings["enableOrchestrator${roomNum}"]) {
             def isWeekend = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
             def oEnd = isWeekend && settings["orchestratorEndTimeWeekend${roomNum}"] ? settings["orchestratorEndTimeWeekend${roomNum}"] : settings["orchestratorEndTime${roomNum}"]
             if (oEnd) {
                 def oDate = timeToday(oEnd, tz)
                 def oCal = Calendar.getInstance(tz)
                 oCal.setTime(oDate)
                 lTime = (oCal.get(Calendar.HOUR_OF_DAY) * 60) + oCal.get(Calendar.MINUTE)
             }
        }
        
        if (lTime != null) {
            hasValidWakeTime = true
            if (lTime < earliestWakeMins) earliestWakeMins = lTime
        }
    }
    
    if (!hasValidWakeTime) earliestWakeMins = 360 
    return currentMins >= earliestWakeMins
}

def commitRoomSwitch(roomNum) {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    def sw1 = settings["roomSwitch${roomNum}"]
    state.remove("autoWakePending${roomNum}")
    
    def isNowAsleep = (sw1?.currentValue("switch") == "on")
    def wasAsleep = state."roomAsleepStatus${roomNum}" ?: false
    def now = new Date().time
    
    if (isNowAsleep && !wasAsleep) {
        def switchLockout = state."gnSwitchLockout_${roomNum}" ?: 0
        if (now < switchLockout) {
            def remain = ((switchLockout - now) / 1000).toInteger()
            logInfo("Room ${roomNum}: Good Night switch activation BLOCKED. 5-minute cool-down active (${remain}s remaining).")
            sw1.off() 
            return
        }
        
        state."roomAsleepStatus${roomNum}" = true
        state.remove("wakeLockout_${roomNum}") 
        logInfo("Room ${roomNum}: Good Night Triggered. Engaging Environment.")
        executeRoomGoodNight(roomNum)
        if (settings["enableASM${roomNum}"]) forceAsmUsersInBed(roomNum)
        
    } else if (!isNowAsleep && wasAsleep) {
        state."roomAsleepStatus${roomNum}" = false
        logInfo("Room ${roomNum}: Good Night Wake Up Triggered. Engaging 30-Minute Wake Lockout & 5-Minute Switch Cooldown.")
        
        state."wakeLockout_${roomNum}" = now + (30 * 60000) 
        state."gnSwitchLockout_${roomNum}" = now + (5 * 60000)
        
        endRoomGoodNight(roomNum)
        if (settings["enableASM${roomNum}"]) forceAsmUsersAwake(roomNum)
    }
}

def executeRoomGoodNight(roomNum) {
    unschedule("lightFadeCompleteRoom${roomNum}")
    unschedule("delayedOffRoom${roomNum}")
    unschedule("audioLoopHandlerRoom${roomNum}")
    state.remove("delayedOffCompleted${roomNum}")
    state."goodNightStartTime${roomNum}" = new Date().time

    def roomHasSick = isAnyoneInRoomSick(roomNum)
    if (roomHasSick) logInfo("Room ${roomNum}: Sickness detected. Bypassing delayed/fade sequences and audio playback.")

    // Ensure a fresh track is selected/prepared every time Good Night runs or restarts
    prepNextAudio(roomNum)

    def audioDelaySecs = (settings["audioStartDelay${roomNum}"] != null ? settings["audioStartDelay${roomNum}"].toInteger() : 8) * 60
    def lights = settings["roomLights${roomNum}"]
    
    def delaySwitches = settings["delayedOffSwitches${roomNum}"]
    def delayMins = settings["delayedOffTime${roomNum}"]
    if (roomHasSick) delayMins = 0 // Instant off for delayed lights
    
    if (delaySwitches && delayMins > 0) {
        runIn(delayMins.toInteger() * 60, "delayedOffRoom${roomNum}")
    } else if (delaySwitches) {
        executeDelayedOff(roomNum)
    }

    def doLightFade = settings["enableLightFade${roomNum}"]
    if (roomHasSick) doLightFade = false // Instant off for primary lights
    
    def lightFadeMins = settings["lightFadeDuration${roomNum}"]

    if (doLightFade && lightFadeMins && lights) {
        def lightFadeMinsInt = lightFadeMins.toInteger()
        state."lightFadeActive${roomNum}" = true
        lights.each { lgt ->
            if (isReadingLightActive(roomNum, lgt.id)) return 
            if (lgt.hasCommand("setLevel")) lgt.setLevel(0, (lightFadeMinsInt * 60))
            else lgt.off()
        }
        runIn((lightFadeMinsInt * 60) + 5, "lightFadeCompleteRoom${roomNum}")
    } else if (lights) { 
        lights.each { lgt ->
            if (isReadingLightActive(roomNum, lgt.id)) return 
            if (lgt.hasCommand("setLevel")) { lgt.setLevel(1); pauseExecution(400) }
             lgt.off()
        }
    }
    
    settings["roomLightsOn${roomNum}"]?.each { lgt -> if (lgt.currentValue("switch") != "on") lgt.on() }
    
    def shadeContact = settings["shadeContact${roomNum}"]
    def shade = settings["roomShade${roomNum}"]
    if (shadeContact && shade && shadeContact.currentValue("contact") == "open") shade.close()
    
    def speakerPower = settings["roomSpeakerPower${roomNum}"]
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    
    if (roomHasSick) {
        // Prevent audio playback and ensure the speaker isn't running
        def speaker = settings["roomSpeaker${roomNum}"]
        if (speaker && speaker.hasCommand("stop")) speaker.stop()
        if (speakerPower && speakerPower.currentValue("switch") != "off") speakerPower.off()
    } else {
        // Normal Audio Handling
        if (settings["roomSpeaker${roomNum}"] || audioType == "switch") {
            if (speakerPower) {
                if (speakerPower.hasCommand("refresh")) { speakerPower.refresh(); pauseExecution(1000) }
                if (speakerPower.currentValue("switch") == "off") {
                    speakerPower.on()
                    runIn(Math.max(audioDelaySecs, 120), "playDelayedAudioRoom${roomNum}")
                } else {
                    if (audioDelaySecs > 0) runIn(audioDelaySecs, "playDelayedAudioRoom${roomNum}")
                    else executeAudioPlay(roomNum)
                }
            } else {
                if (audioDelaySecs > 0) runIn(audioDelaySecs, "playDelayedAudioRoom${roomNum}")
                else executeAudioPlay(roomNum)
             }
        }
    }
    evaluateFans(roomNum)
}

def lightFadeCompleteRoom1() { executeLightFadeComplete(1) }
def lightFadeCompleteRoom2() { executeLightFadeComplete(2) }
def lightFadeCompleteRoom3() { executeLightFadeComplete(3) }
def lightFadeCompleteRoom4() { executeLightFadeComplete(4) }
def lightFadeCompleteRoom5() { executeLightFadeComplete(5) }

def executeLightFadeComplete(roomNum) {
    state.remove("lightFadeActive${roomNum}")
    settings["roomLights${roomNum}"]?.each { lgt -> if (!isReadingLightActive(roomNum, lgt.id) && lgt.currentValue("switch") != "off") lgt.off() }
    logInfo("Room ${roomNum}: Smooth Light Fade-Out complete.")
    def numUsers = settings["numOccupants${roomNum}"] ? settings["numOccupants${roomNum}"].toInteger() : 1
    for (int u = 1; u <= numUsers; u++) {
        def tuId = "${roomNum}_${u}"
        if (state.sleepState["${tuId}"] == "IN BED") state.latencyClockStart["${tuId}"] = new Date().time
    }
}

def delayedOffRoom1() { executeDelayedOff(1) }
def delayedOffRoom2() { executeDelayedOff(2) }
def delayedOffRoom3() { executeDelayedOff(3) }
def delayedOffRoom4() { executeDelayedOff(4) }
def delayedOffRoom5() { executeDelayedOff(5) }

def executeDelayedOff(roomNum) {
    settings["delayedOffSwitches${roomNum}"]?.each { sw -> if (sw.currentValue("switch") != "off") sw.off() }
    state."delayedOffCompleted${roomNum}" = true
}

def playDelayedAudioRoom1() { executeAudioPlay(1) }
def playDelayedAudioRoom2() { executeAudioPlay(2) }
def playDelayedAudioRoom3() { executeAudioPlay(3) }
def playDelayedAudioRoom4() { executeAudioPlay(4) }
def playDelayedAudioRoom5() { executeAudioPlay(5) }

def executeAudioPlay(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    def doFadeIn = settings["enableAudioFadeIn${roomNum}"]
    def fadeInMins = settings["audioFadeInDuration${roomNum}"] != null ? settings["audioFadeInDuration${roomNum}"].toInteger() : 0
    def fadeInStartVol = settings["audioFadeInStartVol${roomNum}"] != null ? settings["audioFadeInStartVol${roomNum}"].toInteger() : 1
    def overrideFadeInUri = settings["audioFadeInUri${roomNum}"]
    def setVol = settings["audioVolume${roomNum}"] != null ? settings["audioVolume${roomNum}"].toInteger() : 30

    if (speaker) {
        if (doFadeIn && fadeInMins > 0) speaker.setVolume(fadeInStartVol)
        else if (setVol != null) speaker.setVolume(setVol)

        if (settings["enableAudioRepeat${roomNum}"] && speaker.hasCommand("setRepeat")) {
            speaker.setRepeat("all")
        }

        def loopSecs = 0

        if (overrideFadeInUri) {
            speaker.playTrack(overrideFadeInUri)
        } else if (audioType == "uri") {
            def trackToPlay = state."nextUri${roomNum}"
            if (trackToPlay) { 
                speaker.playTrack(trackToPlay)
                state."lastUri${roomNum}" = trackToPlay
                
                // Map the active track to its specific loop interval setting
                if (trackToPlay == settings["audioUri${roomNum}_1"]) {
                    loopSecs = settings["audioLoopInterval${roomNum}_1"] != null ? settings["audioLoopInterval${roomNum}_1"].toInteger() : 0
                } else if (trackToPlay == settings["audioUri${roomNum}_2"]) {
                    loopSecs = settings["audioLoopInterval${roomNum}_2"] != null ? settings["audioLoopInterval${roomNum}_2"].toInteger() : 0
                } else if (trackToPlay == settings["audioUri${roomNum}_3"]) {
                    loopSecs = settings["audioLoopInterval${roomNum}_3"] != null ? settings["audioLoopInterval${roomNum}_3"].toInteger() : 0
                }
            }
        } else if (audioType == "switch") {
            def switchToTurnOnId = state."nextSwitchId${roomNum}"
            if (switchToTurnOnId) {
                for(int u = 1; u <= 5; u++) {
                    def sw = settings["audioSwitch${roomNum}_${u}"]
                    if (sw?.id == switchToTurnOnId) {
                        sw.on()
                        state."lastSwitchId${roomNum}" = switchToTurnOnId
                        if (!doFadeIn && setVol != null) runIn(30, "applyDelayedVolumeRoom${roomNum}")
                        break
                    }
                }
            }
        }
        
        // Loop Scheduling logic for custom track restarts
        if (audioType == "uri" && !overrideFadeInUri) {
             if (loopSecs >= 5) {
                 runIn(loopSecs, "audioLoopHandlerRoom${roomNum}")
             }
        }
        
        if (doFadeIn && fadeInMins > 0) {
            state."fadeInActive${roomNum}" = true
            state."fadeInStep${roomNum}" = 0
            state."fadeInMaxSteps${roomNum}" = fadeInMins
            state."fadeInStartVol${roomNum}" = fadeInStartVol
            state."fadeInTargetVol${roomNum}" = setVol
            runIn(60, "startAudioFadeInRoom${roomNum}")
        }
        
        def sTimerSetting = settings["audioTimer${roomNum}"]
        if (sTimerSetting) {
            def sTimer = sTimerSetting.toInteger()
            def fadeEnabled = settings["enableAudioFade${roomNum}"]
            def fadeOutMins = settings["audioFadeDuration${roomNum}"] != null ? settings["audioFadeDuration${roomNum}"].toInteger() : 0
            
            if (fadeEnabled && fadeOutMins > 0) {
                if (fadeOutMins > sTimer) fadeOutMins = sTimer
                def fadeStartDelaySecs = (sTimer - fadeOutMins) * 60
                
                state."fadeStep${roomNum}" = 0
                state."fadeMaxSteps${roomNum}" = fadeOutMins
                state."fadeInitialVol${roomNum}" = setVol
                
                if (fadeStartDelaySecs > 0) runIn(fadeStartDelaySecs, "startAudioFadeRoom${roomNum}")
                else {
                    state."fadeActive${roomNum}" = true
                    startAudioFadeRoom(roomNum)
                }
            }
            runIn(sTimer * 60, "stopAudioRoom${roomNum}") 
        }
    }
}

// --- CUSTOM AUDIO LOOP ENGINE ---
def audioLoopHandlerRoom1() { executeAudioLoop(1) }
def audioLoopHandlerRoom2() { executeAudioLoop(2) }
def audioLoopHandlerRoom3() { executeAudioLoop(3) }
def audioLoopHandlerRoom4() { executeAudioLoop(4) }
def audioLoopHandlerRoom5() { executeAudioLoop(5) }

def executeAudioLoop(roomNum) {
    if (!state."roomAsleepStatus${roomNum}") return
    if (state."fadeActive${roomNum}") return // Prevent track restarts if sleep timer is actively fading out
    
    def speaker = settings["roomSpeaker${roomNum}"]
    def trackToPlay = state."lastUri${roomNum}"
    def loopSecs = 0
    
    if (trackToPlay) {
        if (trackToPlay == settings["audioUri${roomNum}_1"]) {
            loopSecs = settings["audioLoopInterval${roomNum}_1"] != null ? settings["audioLoopInterval${roomNum}_1"].toInteger() : 0
        } else if (trackToPlay == settings["audioUri${roomNum}_2"]) {
            loopSecs = settings["audioLoopInterval${roomNum}_2"] != null ? settings["audioLoopInterval${roomNum}_2"].toInteger() : 0
        } else if (trackToPlay == settings["audioUri${roomNum}_3"]) {
            loopSecs = settings["audioLoopInterval${roomNum}_3"] != null ? settings["audioLoopInterval${roomNum}_3"].toInteger() : 0
        }
    }
    
    if (speaker && loopSecs >= 5 && trackToPlay) {
        speaker.playTrack(trackToPlay)
        if (txtLogEnable) log.info "${app.label}: Room ${roomNum}: Custom audio loop interval reached. Re-playing track."
        runIn(loopSecs, "audioLoopHandlerRoom${roomNum}")
    }
}

def startAudioFadeInRoom1() { audioFadeInStepHandler(1) }
def startAudioFadeInRoom2() { audioFadeInStepHandler(2) }
def startAudioFadeInRoom3() { audioFadeInStepHandler(3) }
def startAudioFadeInRoom4() { audioFadeInStepHandler(4) }
def startAudioFadeInRoom5() { audioFadeInStepHandler(5) }

def audioFadeInStepHandler(roomNum) {
    if (!state."fadeInActive${roomNum}") return
    def step = (state."fadeInStep${roomNum}" ?: 0) + 1
    def maxSteps = state."fadeInMaxSteps${roomNum}" != null ? state."fadeInMaxSteps${roomNum}" .toInteger() : 5
    def startVol = state."fadeInStartVol${roomNum}" != null ? state."fadeInStartVol${roomNum}" .toInteger() : 1
    def targetVol = state."fadeInTargetVol${roomNum}" != null ? state."fadeInTargetVol${roomNum}".toInteger() : 30
    def speaker = settings["roomSpeaker${roomNum}"]
    
    if (step >= maxSteps) {
        state.remove("fadeInActive${roomNum}")
        if (speaker) speaker.setVolume(targetVol)
        return
    }
    state."fadeInStep${roomNum}" = step
    if (speaker) speaker.setVolume(Math.round(startVol + (((targetVol - startVol).toDouble() / maxSteps) * step)).toInteger())
    runIn(60, "startAudioFadeInRoom${roomNum}")
}

def startAudioFadeRoom1() { audioFadeStepHandler(1) }
def startAudioFadeRoom2() { audioFadeStepHandler(2) }
def startAudioFadeRoom3() { audioFadeStepHandler(3) }
def startAudioFadeRoom4() { audioFadeStepHandler(4) }
def startAudioFadeRoom5() { audioFadeStepHandler(5) }

def audioFadeStepHandler(roomNum) {
    state."fadeActive${roomNum}" = true
    def step = (state."fadeStep${roomNum}" ?: 0) + 1
    def maxSteps = state."fadeMaxSteps${roomNum}" != null ? state."fadeMaxSteps${roomNum}".toInteger() : 15
    def initialVol = state."fadeInitialVol${roomNum}" != null ? state."fadeInitialVol${roomNum}".toInteger() : 30
    def minVol = settings["audioFadeMinVol${roomNum}"] != null ? settings["audioFadeMinVol${roomNum}"].toInteger() : 0
    def speaker = settings["roomSpeaker${roomNum}"]
    
    if (step >= maxSteps) {
        state.remove("fadeActive${roomNum}")
        if (speaker) speaker.setVolume(minVol)
        return
    }
    state."fadeStep${roomNum}" = step
    if (speaker) speaker.setVolume(Math.round(initialVol - (((initialVol - minVol).toDouble() / maxSteps) * step)).toInteger())
    runIn(60, "startAudioFadeRoom${roomNum}")
}

def stopAudioRoom1() { executeAudioStop(1) }
def stopAudioRoom2() { executeAudioStop(2) }
def stopAudioRoom3() { executeAudioStop(3) }
def stopAudioRoom4() { executeAudioStop(4) }
def stopAudioRoom5() { executeAudioStop(5) }

def executeAudioStop(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    state.remove("fadeActive${roomNum}")
    unschedule("startAudioFadeRoom${roomNum}")
    unschedule("audioLoopHandlerRoom${roomNum}")
    
    if (speaker) {
        if (settings["enableAudioRepeat${roomNum}"] && speaker.hasCommand("setRepeat")) {
            speaker.setRepeat("off")
        }
        if (speaker.hasCommand("stop")) speaker.stop()
    }
}

def hardStopAudioRoom1() { executeHardStop(1) }
def hardStopAudioRoom2() { executeHardStop(2) }
def hardStopAudioRoom3() { executeHardStop(3) }
def hardStopAudioRoom4() { executeHardStop(4) }
def hardStopAudioRoom5() { executeHardStop(5) }

def executeHardStop(roomNum) {
    if (state."roomAsleepStatus${roomNum}") {
        logInfo("Room ${roomNum}: Audio Hard Stop Time reached. Forcing stop.")
        executeAudioStop(roomNum)
    }
}

def applyDelayedVolumeRoom1() { executeDelayedVolume(1) }
def applyDelayedVolumeRoom2() { executeDelayedVolume(2) }
def applyDelayedVolumeRoom3() { executeDelayedVolume(3) }
def applyDelayedVolumeRoom4() { executeDelayedVolume(4) }
def applyDelayedVolumeRoom5() { executeDelayedVolume(5) }

def executeDelayedVolume(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    def setVol = settings["audioVolume${roomNum}"] != null ? settings["audioVolume${roomNum}"].toInteger() : null
    if (speaker && setVol != null) speaker.setVolume(setVol)
}

def endRoomGoodNight(roomNum) {
    logInfo("Room ${roomNum}: Executing Wake-Up routine (shutting down fans and restoring audio).")
    state.remove("delayedOffCompleted${roomNum}")
    state.remove("fadeActive${roomNum}")
    state.remove("fadeInActive${roomNum}")
    state.remove("lightFadeActive${roomNum}")
    state.remove("autoWakePending${roomNum}")
    unschedule("lightFadeCompleteRoom${roomNum}")
    unschedule("startAudioFadeInRoom${roomNum}")
    unschedule("startAudioFadeRoom${roomNum}")
    unschedule("playDelayedAudioRoom${roomNum}")
    unschedule("stopAudioRoom${roomNum}")
    unschedule("applyDelayedVolumeRoom${roomNum}")
    unschedule("delayedOffRoom${roomNum}")
    unschedule("audioLoopHandlerRoom${roomNum}")
    
    settings["roomFans${roomNum}"]?.off()
    settings["roomLightsOn${roomNum}"]?.each { lgt -> if (lgt.currentValue("switch") != "off") lgt.off() }

    def speaker = settings["roomSpeaker${roomNum}"]
    if (speaker) {
        if (settings["enableAudioRepeat${roomNum}"] && speaker.hasCommand("setRepeat")) {
            speaker.setRepeat("off")
        }
        if (speaker.hasCommand("stop")) speaker.stop()
        def setVol = settings["audioVolume${roomNum}"] != null ? settings["audioVolume${roomNum}"].toInteger() : 30
        if (speaker.hasCommand("setVolume")) speaker.setVolume(setVol)
    }
    prepNextAudio(roomNum) 
}

def tempHandler(evt) {
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableRoom${i}"] && settings["tempSensor${i}"]?.id == evt.device.id) {
            if (state."roomAsleepStatus${i}") evaluateFans(i)
        }
    }
}

def evaluateFans(roomNum) {
    if (!state."roomAsleepStatus${roomNum}") return 
    
    if (state."roomIsSleeping${roomNum}" && settings["sleepFansOff${roomNum}"] && (state."synergyStage2Active${roomNum}" || state."synergySoftRampActive${roomNum}")) {
        return 
    }
    
    def sensor = settings["tempSensor${roomNum}"]
    def currentTemp = sensor ? sensor.currentValue("temperature") : null
    if (currentTemp != null) {
        def stdSetpoint = settings["fanSetpoint${roomNum}"]
        def stdFans = settings["roomFans${roomNum}"]
        if (stdSetpoint && stdFans) {
            if (currentTemp >= stdSetpoint) stdFans.each { if (it.currentValue("switch") != "on") it.on() }
            else stdFans.each { if (it.currentValue("switch") != "off") it.off() }
        }
    }
}

def prepNextAudio(roomNum) {
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    if (audioType == "uri") {
        state.remove("nextSwitchId${roomNum}")
        def uris = []
        for(int u = 1; u <= 5; u++) { def uri = settings["audioUri${roomNum}_${u}"]; if (uri) uris << uri }
        if (uris.size() > 0) {
            if (uris.size() == 1) state."nextUri${roomNum}" = uris[0]
            else {
                def lastPlayed = state."lastUri${roomNum}"
                def availableUris = uris.findAll { it != lastPlayed }
                if (availableUris.size() == 0) availableUris = uris 
                state."nextUri${roomNum}" = availableUris[new Random().nextInt(availableUris.size())]
            }
        } else state.remove("nextUri${roomNum}")
    } else if (audioType == "switch") {
        state.remove("nextUri${roomNum}")
        def switches = []
        for(int u = 1; u <= 5; u++) { def sw = settings["audioSwitch${roomNum}_${u}"]; if (sw) switches << sw.id }
        if (switches.size() > 0) {
            if (switches.size() == 1) state."nextSwitchId${roomNum}" = switches[0]
            else {
                def lastPlayed = state."lastSwitchId${roomNum}"
                def availableSwitches = switches.findAll { it != lastPlayed }
                if (availableSwitches.size() == 0) availableSwitches = switches 
                state."nextSwitchId${roomNum}" = availableSwitches[new Random().nextInt(availableSwitches.size())]
            }
        } else state.remove("nextSwitchId${roomNum}")
    }
}

// ==============================================================================
// ADVANCED SLEEP METRICS (BIOLOGICAL ORCHESTRATION ENGINE)
// ==============================================================================

def ensureStateMaps() {
    if (state.sleepState == null) state.sleepState = [:]
    if (state.inBedTime == null) state.inBedTime = [:]
    if (state.latencyClockStart == null) state.latencyClockStart = [:]
    if (state.asleepTime == null) state.asleepTime = [:]
    if (state.lastSessionInBed == null) state.lastSessionInBed = [:]
    if (state.lastSessionAsleep == null) state.lastSessionAsleep = [:]
    if (state.lastRoomMotionTime == null) state.lastRoomMotionTime = [:]
    if (state.lastExitTime == null) state.lastExitTime = [:]
    if (state.pendingExit == null) state.pendingExit = [:]
    if (state.movements == null) state.movements = [:]
    if (state.deafenedUntil == null) state.deafenedUntil = [:]
    if (state.bathroomTrips == null) state.bathroomTrips = [:]
    if (state.bathroomDuration == null) state.bathroomDuration = [:]
    if (state.pendingEntryTime == null) state.pendingEntryTime = [:]
    if (state.sessionResumedTime == null) state.sessionResumedTime = [:]
    if (state.sessionStartTime == null) state.sessionStartTime = [:]
    if (state.deepSleepDuration == null) state.deepSleepDuration = [:]
    if (state.lastStillStartTime == null) state.lastStillStartTime = [:]
    if (state.sleepLatency == null) state.sleepLatency = [:]
    if (state.weightedMovementPenalty == null) state.weightedMovementPenalty = [:]
    if (state.ewmaMovement == null) state.ewmaMovement = [:]
    if (state.lastMoveTimeForEwma == null) state.lastMoveTimeForEwma = [:]
    if (state.currentSleepStage == null) state.currentSleepStage = [:]
    if (state.smartAlarmTriggeredDate == null) state.smartAlarmTriggeredDate = [:]
    if (state.learnedBedTimes == null) state.learnedBedTimes = [:]
    if (state.learnedWakeTimes == null) state.learnedWakeTimes = [:]
    if (state.sleepScoreHistory == null) state.sleepScoreHistory = [:]
    if (state.avgSleepScore == null) state.avgSleepScore = [:]
    if (state.matOpenTime == null) state.matOpenTime = [:]
    if (state.lastPressure == null) state.lastPressure = [:]
    if (state.posture == null) state.posture = [:]
    if (state.dailyEffortScore == null) state.dailyEffortScore = [:]
}

def isWithinSleepTrackingWindow(rNum) {
    if (settings["enableDaytimeLockout${rNum}"] == false) return true
    
    // NEW: If any occupant in this room is sick, bypass the daytime lockout so they can rest
    if (isAnyoneInRoomSick(rNum)) return true
    
    def tStart = settings["daytimeLockoutStart${rNum}"] ?: "08:00"
    def tEnd = settings["daytimeLockoutEnd${rNum}"] ?: "19:00"
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def start = timeToday(tStart, tz).time
    def end = timeToday(tEnd, tz).time
    def nowTime = new Date().time
    
    if (start < end) {
        if (nowTime >= start && nowTime < end) return false 
    } else { 
        if (nowTime >= start || nowTime < end) return false
    }
    return true
}

def forceAsmUsersInBed(rNum) {
    if (!settings["enableASM${rNum}"]) return
    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    def now = new Date().time
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        
        def presenceDev = settings["occupantPresence_${uId}"]
        if (presenceDev && presenceDev.currentValue("presence") == "not present") {
             logInfo("ASM Sync: Occupant ${uId} is AWAY. Skipping forced bed entry.")
             continue
        }
        
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        if (cState == "EMPTY" || cState == "PENDING ENTRY") {
            
            if (cState == "EMPTY" && !isWithinSleepTrackingWindow(rNum)) {
                 logInfo("ASM Sync: Blocking forced bed entry for Occupant ${uId} due to active Daytime Lockout.")
                 continue
            }
            
            def wakeLockoutTime = state."wakeLockout_${rNum}" ?: 0
            if (cState == "EMPTY" && now < wakeLockoutTime) {
                 logInfo("ASM Sync: Blocking forced bed entry for Occupant ${uId} due to active Wake Lockout.")
                 continue
            }
            
            logInfo("ASM Sync: Forcing Occupant ${uId} IN BED.")
            state.sleepState["${uId}"] = "IN BED"
            state.inBedTime["${uId}"] = now
            state.latencyClockStart["${uId}"] = now
            if (!state.sessionStartTime["${uId}"]) state.sessionStartTime["${uId}"] = now
            
            state.asleepTime["${uId}"] = null
            state.sessionResumedTime["${uId}"] = 0
            state.movements["${uId}"] = 0
            state.weightedMovementPenalty["${uId}"] = 0.0
            state.bathroomTrips["${uId}"] = 0
            state.bathroomDuration["${uId}"] = 0
            state.deepSleepDuration["${uId}"] = 0
            
            def baseThresh = settings["fallAsleepThreshold${rNum}"] != null ? settings["fallAsleepThreshold${rNum}"].toInteger() : 15
            if (isUserSick(uId)) baseThresh = Math.min(2, baseThresh)
            def delaySecs = baseThresh * 60
            
            state."evalSleepTime_${uId}" = now + (delaySecs * 1000)
            runIn(delaySecs, "evaluateSleepState", [data: [uId: uId], overwrite: false])
        }
    }
    updateRoomSleepState(rNum)
}

def forceAsmUsersAwake(rNum) {
    if (!settings["enableASM${rNum}"]) return
    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        if (cState != "EMPTY") {
            logInfo("ASM Sync: Forcing Occupant ${uId} AWAKE.")
            forceBedExit(uId, rNum, true)
        }
    }
}

def isWithinOrchestratorWindow(roomNum) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    def day = cal.get(Calendar.DAY_OF_WEEK)
    def hour = cal.get(Calendar.HOUR_OF_DAY)
    
    def isWeekendSession = false
    if (day == Calendar.FRIDAY && hour >= 12) isWeekendSession = true
    else if (day == Calendar.SATURDAY) isWeekendSession = true
    else if (day == Calendar.SUNDAY && hour < 12) isWeekendSession = true

    def tStart = isWeekendSession && settings["orchestratorStartTimeWeekend${roomNum}"] ? settings["orchestratorStartTimeWeekend${roomNum}"] : settings["orchestratorStartTime${roomNum}"]
    def tEnd = isWeekendSession && settings["orchestratorEndTimeWeekend${roomNum}"] ? settings["orchestratorEndTimeWeekend${roomNum}"] : settings["orchestratorEndTime${roomNum}"]
    
    if (!tStart || !tEnd) return true 
    
    def start = timeToday(tStart, tz)
    def end = timeToday(tEnd, tz)
    def nowTime = new Date().time
    
    if (start.time >= end.time) return (nowTime >= start.time || nowTime <= end.time)
    else return (nowTime >= start.time && nowTime <= end.time)
}

// --- TAILORED RECOVERY & SLEEP NOTIFICATIONS ---
def scheduleEveningNotifications() {
    if (appEnableSwitch?.currentValue("switch") == "off") return
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    def tz = location.timeZone ?: TimeZone.getDefault()

    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableRoom${i}"] && settings["enableASM${i}"]) {
            def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
            for (int u = 1; u <= numUsers; u++) {
                def uId = "${i}_${u}"
                if (settings["enableML_${uId}"]) {
                    def uName = settings["userName_${uId}"] ?: "Occupant ${uId}"
                    def threshold = settings["poorSleepThreshold_${uId}"] != null ? settings["poorSleepThreshold_${uId}"].toInteger() : 75
                    
                    def history = state.sleepScoreHistory["${uId}"] ?: []
                    def lastScore = history.size() > 0 ? history[0].score : 85
                    def effortScore = state.dailyEffortScore?."${uId}" ?: 0
                    
                    def needsAdjustment = false
                    def adjustment = 0
                    def reason = ""
                    def isSick = isUserSick(uId)
                    
                    if (isSick) {
                        needsAdjustment = true
                        adjustment = 60
                        reason = "to rest your body and help fight off your illness"
                    } else if (effortScore > 75) {
                        needsAdjustment = true
                        adjustment = 45
                        reason = "due to high physical strain (${effortScore} pts)"
                    } else if (lastScore < 60) {
                        needsAdjustment = true
                        adjustment = 45
                        reason = "to recover from last night's low sleep score (${lastScore}%)"
                    } else if (lastScore < threshold || effortScore > 50) {
                        needsAdjustment = true
                        adjustment = 30
                        if (effortScore > 50 && lastScore < threshold) reason = "due to moderate workout strain and lower sleep recovery"
                        else if (effortScore > 50) reason = "due to moderate workout strain (${effortScore} pts)"
                        else reason = "to help recover from last night's sleep score (${lastScore}%)"
                    }
                    
                    if (needsAdjustment) {
                        def learnedBedMins = getLearnedTime(uId, false)
                        if (learnedBedMins) {
                            def recommendedMins = learnedBedMins - adjustment
                            
                            def cal = Calendar.getInstance(tz)
                            cal.set(Calendar.HOUR_OF_DAY, 12)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.add(Calendar.MINUTE, recommendedMins)
                            def runTime = cal.getTime()
                            
                            def nowTime = new Date().getTime()
                            def targetTime = runTime.getTime()

                            if (targetTime > nowTime) {
                                def learnedTimeStr = formatTimeFromMinutes(learnedBedMins, true)
                                runOnce(runTime, "sendTailoredBedtimeNotification", [data: [uId: uId, uName: uName, learnedTimeStr: learnedTimeStr, reason: reason], overwrite: false])
                                logInfo("ASM Scheduler: Scheduled tailored bedtime triggers for ${uName} at ${runTime.format('h:mm a', tz)}.")
                            } else if ((nowTime - targetTime) <= 3600000) { 
                                def learnedTimeStr = formatTimeFromMinutes(learnedBedMins, true)
                                logInfo("ASM Scheduler: Target time of ${runTime.format('h:mm a', tz)} passed recently. Triggering immediately for ${uName}.")
                                sendTailoredBedtimeNotification([uId: uId, uName: uName, learnedTimeStr: learnedTimeStr, reason: reason])
                            }
                        }
                    }
                }
            }
        }
    }
}

def sendTailoredBedtimeNotification(data) {
    if (!data) return
    def uId = data.uId
    def sw = settings["tailoredBedtimeSwitch_${uId}"]
    def notifiers = settings["notificationDevice_${uId}"]

    if (sw) {
        sw.on()
        runIn(600, "turnOffTailoredSwitch", [data: [uId: uId], overwrite: false])
        logInfo("ASM Scheduler: Turned ON tailored bedtime switch for User ${uId}. Auto-off scheduled in 10 minutes.")
    }

    if (notifiers) {
        def msg = "Hey ${data.uName}, it's time to wind down. Your normal learned bedtime is ${data.learnedTimeStr}, but we recommend turning in now ${data.reason}."
        try {
            if (notifiers instanceof Collection || notifiers instanceof Object[]) {
                notifiers.each { it.deviceNotification(msg) }
            } else {
                notifiers.deviceNotification(msg)
            }
            logInfo("ASM Notification: Sent tailored bedtime advisory to ${data.uName} via ${notifiers}.")
        } catch (e) {
            log.error "Failed to send notification to ${data.uName}: ${e.message}"
        }
    }
}

def turnOffTailoredSwitch(data) {
    def sw = settings["tailoredBedtimeSwitch_${data.uId}"]
    if (sw && sw.currentValue("switch") != "off") {
        sw.off()
        logInfo("ASM Scheduler: 10 minute tailored bedtime window ended. Turned OFF switch for User ${data.uId}.")
    }
}

def orchestrateRooms() {
    if (appEnableSwitch?.currentValue("switch") == "off") return
    def now = new Date().time
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    
    for (int i = 1; i <= maxRooms; i++) {
        if (!settings["enableRoom${i}"] || !settings["enableASM${i}"] || !settings["enableOrchestrator${i}"]) continue
        
        def sw = settings["roomSwitch${i}"]
        def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
        def activeUsers = 0
        def readyUsers = 0
        def allEmpty = true
        
        for(int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            def pMat = settings["pressureMat_${uId}"]
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            def presenceDev = settings["occupantPresence_${uId}"]
            def isAway = (presenceDev && presenceDev.currentValue("presence") == "not present")
            
            if (pMat) {
                if (isAway && cState == "EMPTY") continue
                
                activeUsers++
                def mState = pMat.currentValue("contact") ?: pMat.currentValue("presence")
                
                if (cState != "EMPTY" || mState == "closed" || mState == "present") allEmpty = false
                
                if (cState == "EMPTY" && (mState == "closed" || mState == "present")) {
                    if (!isWithinSleepTrackingWindow(i)) continue 
                    def wakeLockoutTime = state."wakeLockout_${i}" ?: 0
                    if (now < wakeLockoutTime) continue 
                    
                    logInfo("ASM RECOVERY: Occupant ${uId} mat is CLOSED but logically EMPTY. Re-syncing.")
                    state.sleepState["${uId}"] = "IN BED"
                    state.inBedTime["${uId}"] = now
                    state.latencyClockStart["${uId}"] = now
                    if (!state.sessionStartTime["${uId}"]) state.sessionStartTime["${uId}"] = now
                    
                    def baseThresh = settings["fallAsleepThreshold${i}"] != null ? settings["fallAsleepThreshold${i}"].toInteger() : 15
                    if (isUserSick(uId)) baseThresh = Math.min(2, baseThresh)
                    def delaySecs = baseThresh * 60
                    
                    state."evalSleepTime_${uId}" = now + (delaySecs * 1000)
                    runIn(delaySecs, "evaluateSleepState", [data: [uId: uId], overwrite: false])
                    cState = "IN BED" 
                }
                
                def inBedTime = state.inBedTime?."${uId}" ?: 0
                if ((cState == "IN BED" || cState == "SLEEPING") && inBedTime > 0 && (now - inBedTime) >= (calculateDynamicDelay(i) * 60000)) {
                    readyUsers++
                }
            }
        }

        if (isWithinOrchestratorWindow(i) && sw && sw.currentValue("switch") != "on" && activeUsers > 0 && readyUsers >= activeUsers) {
            logInfo("ASM ORCHESTRATOR: Room ${i} occupants asleep within window. Engaging Good Night.")
            sw.on() 
        }

        if (settings["enableAutoWake${i}"] && sw && sw.currentValue("switch") == "on" && activeUsers > 0 && allEmpty) {
            def isNightLocked = settings["autoWakeNightLock${i}"] && !isWakeTimeReached(i)
            if (isNightLocked) {
            } else {
                def lockoutMillis = (settings["autoWakeLockout${i}"] != null ? settings["autoWakeLockout${i}"].toInteger() : 120) * 60000
                if ((now - (state."goodNightStartTime${i}" ?: 0)) >= lockoutMillis && !state."autoWakePending${i}") {
                    logInfo("ASM CRON SWEEP: Room ${i} ASLEEP but all beds EMPTY. Scheduling Auto-Wake sequence.")
                    
                    def wakeDelay = settings["autoWakeDelay${i}"] != null ? settings["autoWakeDelay${i}"].toInteger() : 5
                    if (wakeDelay > 0) {
                        state."autoWakePending${i}" = new Date().time + (wakeDelay * 60000)
                        runIn(wakeDelay * 60, "executeAutoWake", [data: [roomNum: i], overwrite: false])
                    } else sw.off()
                }
            }
        }
    }
}

def analogPressureHandler(evt) {
    if (appEnableSwitch?.currentValue("switch") == "off") return
    def uId = getUserIdFromDevice(evt.device.id, "pressureMat")
    if (!uId || !settings["enableAnalogPressure_${uId}"]) return
    
    def rNum = uId.split('_')[0]
    def currentPressure = evt.value as BigDecimal
    def lastPressure = state.lastPressure?."${uId}" ?: currentPressure
    
    def delta = Math.abs(currentPressure - lastPressure)
    state.lastPressure["${uId}"] = currentPressure
    
    def movementDeltaThreshold = settings["pressureDeltaThreshold_${uId}"] ?: 15
    def sittingAbsoluteThreshold = settings["pressureSittingThreshold_${uId}"] ?: 80
    
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    
    if (cState == "IN BED" || cState == "SLEEPING" || cState == "PENDING ENTRY") {
        if (delta >= movementDeltaThreshold) {
            def mockSensorType = (delta > 40) ? "headboard" : "frame"
            processUserMovement(uId, rNum, mockSensorType)
        }
        
        def currentPosture = state.posture?."${uId}" ?: "LYING_FLAT"
        if (currentPressure >= sittingAbsoluteThreshold && currentPosture != "SITTING") {
            state.posture["${uId}"] = "SITTING"
            if (cState == "SLEEPING") {
                state.sleepState["${uId}"] = "IN BED"
                updateRoomSleepState(rNum)
            }
            if (settings["autoReadingLight_${uId}"]) {
                def lNum = settings["linkedReadingLight_${uId}"]
                if (lNum) toggleReadingMode(rNum, lNum.toInteger(), true, false)
            }
        } else if (currentPressure < sittingAbsoluteThreshold && currentPosture == "SITTING") {
            state.posture["${uId}"] = "LYING_FLAT"
            state.latencyClockStart["${uId}"] = new Date().time
            if (settings["autoReadingLight_${uId}"]) {
                def lNum = settings["linkedReadingLight_${uId}"]
                if (lNum) toggleReadingMode(rNum, lNum.toInteger(), false, true)
            }
        }
    }
}

def pressureMatHandler(evt) {
    if (appEnableSwitch?.currentValue("switch") == "off") return
    def uId = getUserIdFromDevice(evt.device.id, "pressureMat")
    if (!uId) return

    def presenceDev = settings["occupantPresence_${uId}"]
    if (presenceDev && presenceDev.currentValue("presence") == "not present") return 
    
    def now = new Date().time
    def rNum = uId.split('_')[0]
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    def isWin = settings["enableOrchestrator${rNum}"] ? isWithinOrchestratorWindow(rNum) : true

    if (evt.value == "closed" || evt.value == "present") { 
        state.pendingExit["${uId}"] = 0
        state.remove("autoWakePending${rNum}")
        state.remove("matOpenTime${uId}")
        
        if (cState == "IN BED" || cState == "SLEEPING") {
            def lastMove = state.lastMoveTimeForEwma["${uId}"] ?: 0
            if (lastMove == 0 || (now - lastMove) >= 30000) processUserMovement(uId, rNum, "headboard")
        } else { 
            if (cState == "EMPTY") {
                if (!isWithinSleepTrackingWindow(rNum)) return 
                def wakeLockoutTime = state."wakeLockout_${rNum}" ?: 0
                if (now < wakeLockoutTime) return 
                if (!state.sessionStartTime["${uId}"]) state.sessionStartTime["${uId}"] = now
            }

            def lastExit = state.lastExitTime?."${uId}" ?: 0
            def stitchMillis = (settings["stitchingWindow${rNum}"] != null ? settings["stitchingWindow${rNum}"].toInteger() : 15) * 60000
            def isExtendedTrip = (lastExit > 0 && (now - lastExit) < (4 * 3600000))

            if (((lastExit > 0 && (now - lastExit) < stitchMillis) || cState == "BATHROOM TRIP" || isExtendedTrip)) {
                if (cState == "BATHROOM TRIP" || (lastExit > 0 && (now - lastExit) >= stitchMillis)) {
                    state.bathroomTrips["${uId}"] = (state.bathroomTrips["${uId}"] ?: 0) + 1
                    def tripMins = ((now - lastExit) / 60000).toInteger()
                    state.bathroomDuration["${uId}"] = (state.bathroomDuration["${uId}"] ?: 0) + (tripMins > 240 ? 0 : tripMins)
                }
                state.sleepState["${uId}"] = "IN BED"
                state.sessionResumedTime["${uId}"] = now 
                updateRoomSleepState(rNum)
                
                def baseThresh = settings["fallAsleepThreshold${rNum}"] != null ? settings["fallAsleepThreshold${rNum}"].toInteger() : 15
                if (isUserSick(uId)) baseThresh = Math.min(2, baseThresh)
                else if (isNearLearnedTime(uId, false, 60)) baseThresh = Math.min(3, baseThresh)
                else if (settings["enableML_${uId}"] && getLearnedTime(uId, false) != null) baseThresh = Math.max(30, baseThresh)
                
                state."evalSleepTime_${uId}" = now + (baseThresh * 60000)
                runIn(baseThresh * 60, "evaluateSleepState", [data: [uId: uId], overwrite: false])
                
            } else {
                state.inBedTime["${uId}"] = now
                state.latencyClockStart["${uId}"] = now
                state.sessionResumedTime["${uId}"] = 0
                state.movements["${uId}"] = 0
                state.weightedMovementPenalty["${uId}"] = 0.0
                state.asleepTime["${uId}"] = null
                state.bathroomTrips["${uId}"] = 0
                state.bathroomDuration["${uId}"] = 0
                
                if (settings["enableAntiBounce${rNum}"]) {
                    def abWait = settings["antiBounceWait${rNum}"] != null ? settings["antiBounceWait${rNum}"].toInteger() : 3
                    state.sleepState["${uId}"] = "PENDING ENTRY"
                    state.pendingEntryTime["${uId}"] = now
                    state."verifyEntryTime_${uId}" = now + (abWait * 60000)
                    runIn(abWait * 60, "verifyBedEntry", [data: [uId: uId, roomNum: rNum], overwrite: false])
                } else {
                    state.sleepState["${uId}"] = "IN BED"
                    updateRoomSleepState(rNum)
                    
                    def baseThresh = settings["fallAsleepThreshold${rNum}"] != null ? settings["fallAsleepThreshold${rNum}"].toInteger() : 15
                    if (isUserSick(uId)) baseThresh = Math.min(2, baseThresh)
                    else if (isNearLearnedTime(uId, false, 60)) baseThresh = Math.min(3, baseThresh)
                    else if (settings["enableML_${uId}"] && getLearnedTime(uId, false) != null) baseThresh = Math.max(30, baseThresh)
                    
                    state."evalSleepTime_${uId}" = now + (baseThresh * 60000)
                    runIn(baseThresh * 60, "evaluateSleepState", [data: [uId: uId], overwrite: false])
                }
            }
        }
        
    } else if (evt.value == "open" || evt.value == "not present") { 
        def isMotionActive = (settings["motionSensor_${rNum}"]?.currentValue("motion") == "active")
        def recentMotion = (now - (state.lastRoomMotionTime["${rNum}"] ?: 0)) <= (15 * 60000)
        state.matOpenTime["${uId}"] = now
        state.posture["${uId}"] = "LYING_FLAT" 
        
        if (settings["enableGhostFilter${rNum}"] && !isMotionActive && !recentMotion && isWin) {
            runIn(1800, "ghostFailsafeCheck", [data: [uId: uId, roomNum: rNum], overwrite: false])
        } else {
            state.pendingExit["${uId}"] = now
            def thresh = settings["exitBedThreshold${rNum}"] != null ? settings["exitBedThreshold${rNum}"].toInteger() : 5
            
            if (isNearLearnedTime(uId, true, 45)) thresh = 0 
            else if (settings["enableAdvancedStages_${uId}"] && state.currentSleepStage["${uId}"] == "LIGHT" && isWithinWakeWindow(uId)) thresh = 0 
            
            if (thresh == 0) forceBedExit(uId, rNum, false)
            else {
                def safeThresh = Math.max(1, thresh)
                state."verifyExitTime_${uId}" = now + (safeThresh * 60000)
                runIn(safeThresh * 60, "evaluateBedExit", [data: [uId: uId, roomNum: rNum, thresh: safeThresh], overwrite: false])
            }
        }
    }
}

def ghostFailsafeCheck(data) {
    def uId = data.uId
    def pMat = settings["pressureMat_${uId}"]
    def mState = pMat ? (pMat.currentValue("contact") ?: pMat.currentValue("presence")) : "closed"
    if (mState == "open" || mState == "not present") {
        if (state.sleepState["${uId}"] ?: "EMPTY" != "EMPTY") {
            state.lastExitTime["${uId}"] = state.matOpenTime["${uId}"] ?: new Date().time
            forceBedExit(uId, data.roomNum, true)
        }
    }
}

def verifyBedEntry(data) {
    def uId = data.uId
    if (new Date().time < (state."verifyEntryTime_${uId}" ?: 0) - 2000) return 

    if (state.sleepState["${uId}"] == "PENDING ENTRY") {
        def pMat = settings["pressureMat_${uId}"]
        def mState = pMat ? (pMat.currentValue("contact") ?: pMat.currentValue("presence")) : "open"
        
        if (mState == "closed" || mState == "present") {
            state.sleepState["${uId}"] = "IN BED"
            state.latencyClockStart["${uId}"] = new Date().time
            updateRoomSleepState(data.roomNum)
            
            def baseThresh = settings["fallAsleepThreshold${data.roomNum}"] != null ? settings["fallAsleepThreshold${data.roomNum}"].toInteger() : 15
            if (isUserSick(uId)) baseThresh = Math.min(2, baseThresh)
            else if (isNearLearnedTime(uId, false, 60)) baseThresh = Math.min(3, baseThresh)
            else if (settings["enableML_${uId}"] && getLearnedTime(uId, false) != null) baseThresh = Math.max(30, baseThresh)
            
            state."evalSleepTime_${uId}" = new Date().time + (baseThresh * 60000)
            runIn(baseThresh * 60, "evaluateSleepState", [data: [uId: uId], overwrite: false])
        } else {
            state.sleepState["${uId}"] = "EMPTY"
            state.inBedTime["${uId}"] = null
        }
    }
}

def vibrationHandler(evt) {
    if (appEnableSwitch?.currentValue("switch") == "off") return
    if (evt.value != "active") return
    
    def deviceId = evt.device.id
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    def uId = null
    def sensorType = "frame"
    
    for (int i = 1; i <= maxRooms; i++) {
        def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
        for (int u = 1; u <= numUsers; u++) {
            def tuId = "${i}_${u}"
            if (settings["vibrationSensor2_${tuId}"]?.id == deviceId) { uId = tuId; sensorType = "headboard"; break } 
            else if (settings["vibrationSensor_${tuId}"]?.id == deviceId) { uId = tuId; sensorType = "frame"; break }
        }
        if (uId) break
    }
    
    if (!uId) return
    def now = new Date().time
    def rNum = uId.split('_')[0]
    
    if (now < (state.deafenedUntil?."${uId}" ?: 0)) return
    
    def pMat = settings["pressureMat_${uId}"]
    if (pMat && (pMat.currentValue("contact") == "open" || pMat.currentValue("presence") == "not present")) return 

    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    if (cState == "IN BED" || cState == "SLEEPING" || cState == "PENDING ENTRY") {
        processUserMovement(uId, rNum, sensorType)
        state.pendingExit["${uId}"] = 0 
    }
}

def processUserMovement(uId, rNum, sensorType = "frame") {
    def now = new Date().time
    def lastMove = state.lastMoveTimeForEwma["${uId}"] ?: 0
    def isHeadboard = (sensorType == "headboard")
    def movementWeight = isHeadboard ? 2.5 : 0.6
    
    if (settings["enableAdvancedStages_${uId}"]) {
        def diffMins = (now - lastMove) / 60000.0
        def decay = Math.pow(0.5, diffMins / 10.0) 
        state.ewmaMovement["${uId}"] = (state.ewmaMovement["${uId}"] ?: 0.0) * decay + movementWeight
        
        def asleepStart = state.asleepTime["${uId}"] ?: now
        def hoursAsleep = (now - asleepStart) / 3600000.0
        
        def stageThreshold = 1.1
        if (hoursAsleep <= 3.0) stageThreshold = 1.5
        else if (hoursAsleep >= 5.0) stageThreshold = 0.8
        
        state.currentSleepStage["${uId}"] = state.ewmaMovement["${uId}"] > stageThreshold ? "LIGHT" : "DEEP"
    }
    
    if (lastMove > 0 && (now - lastMove) < 15000) {
        state.lastMoveTimeForEwma["${uId}"] = now
        return
    }
    
    state.movements["${uId}"] = (state.movements["${uId}"] ?: 0) + 1
    
    def penalty = isHeadboard ? 0.35 : 0.15
    if (settings["enableCircadianScaling_${uId}"]) {
        def hour = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault()).get(Calendar.HOUR_OF_DAY)
        if (hour >= 3 && hour <= 8) penalty *= 0.5 
    }
    state.weightedMovementPenalty["${uId}"] = (state.weightedMovementPenalty["${uId}"] ?: 0.0) + penalty
    state.lastMoveTimeForEwma["${uId}"] = now
    
    if (settings["enableSmartAlarm_${uId}"] && settings["smartAlarmSwitch_${uId}"]) {
        def todayStr = new Date().format("yyyy-MM-dd", location.timeZone ?: TimeZone.getDefault())
        if (state.smartAlarmTriggeredDate["${uId}"] != todayStr && isWithinWakeWindow(uId)) {
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if (cState == "SLEEPING" || cState == "IN BED") {
                settings["smartAlarmSwitch_${uId}"].on()
                state.smartAlarmTriggeredDate["${uId}"] = todayStr
            }
        }
    }
    
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    if (cState == "SLEEPING") {
        def stillStart = state.lastStillStartTime["${uId}"] ?: state.asleepTime["${uId}"] ?: now
        def gap = now - stillStart
        if (gap >= 1800000) state.deepSleepDuration["${uId}"] = (state.deepSleepDuration["${uId}"] ?: 0) + (gap / 60000).toInteger()
        state.lastStillStartTime["${uId}"] = now
    } else if (cState == "IN BED") {
        def threshMins = settings["fallAsleepThreshold${rNum}"] != null ? settings["fallAsleepThreshold${rNum}"].toInteger() : 15
        if (isUserSick(uId)) threshMins = Math.min(2, threshMins)
        def delaySecs = threshMins * 60
        state."evalSleepTime_${uId}" = now + (delaySecs * 1000)
        runIn(delaySecs, "evaluateSleepState", [data: [uId: uId], overwrite: false])
    }
    
    evaluateThermalStaging(rNum)
}

def evaluateSleepState(data) {
    def uId = data.uId
    if (new Date().time < (state."evalSleepTime_${uId}" ?: 0) - 2000) return 
    def rNum = uId.split('_')[0]
    def pMat = settings["pressureMat_${uId}"]
    if (pMat && (pMat.currentValue("contact") == "open" || pMat.currentValue("presence") == "not present")) return

    if (state.sleepState["${uId}"] == "IN BED" && state.posture["${uId}"] != "SITTING") {
        def now = new Date().time
        def lastVib = state.lastMoveTimeForEwma["${uId}"] ?: 0
        
        def threshMins = settings["fallAsleepThreshold${rNum}"] != null ? settings["fallAsleepThreshold${rNum}"].toInteger() : 15
        if (isUserSick(uId)) threshMins = Math.min(2, threshMins)
        
        if ((now - lastVib) >= (threshMins * 60000)) {
            def inBed = state.inBedTime["${uId}"] ?: now
            def latencyStart = state.latencyClockStart["${uId}"] ?: inBed
            def actualSleepTime = (lastVib > latencyStart) ? lastVib : now
            
            if (!state.asleepTime["${uId}"]) {
                state.asleepTime["${uId}"] = actualSleepTime
                state.sleepLatency["${uId}"] = ((actualSleepTime - latencyStart) / 60000).toInteger()
                if (state.sleepLatency["${uId}"] < 0) state.sleepLatency["${uId}"] = 0
            }
            
            state.sleepState["${uId}"] = "SLEEPING"
            state.lastStillStartTime["${uId}"] = now 
            
            updateRoomSleepState(rNum)
            evaluateThermalStaging(rNum)
        }
    }
}

def isWithinWakeWindow(uId) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    def isWeekend = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    def wStart = isWeekend ? settings["weekendWakeStart_${uId}"] : settings["weekdayWakeStart_${uId}"]
    def wEnd = isWeekend ? settings["weekendWakeEnd_${uId}"] : settings["weekdayWakeEnd_${uId}"]

    if (!wStart || !wEnd) return false
    def start = timeToday(wStart, tz)
    def end = timeToday(wEnd, tz)
    def nowTime = new Date().time
    
    if (start.time > end.time) return (nowTime >= start.time || nowTime <= end.time)
    else return (nowTime >= start.time && nowTime <= end.time)
}

def evaluateBedExit(data) {
    def uId = data.uId
    if (new Date().time < (state."verifyExitTime_${uId}" ?: 0) - 2000) return
    def pending = state.pendingExit["${uId}"] ?: 0
    if (state.sleepState["${uId}"] == "EMPTY") return
    if (pending > 0 && (new Date().time - pending) >= ((data.thresh ?: 5) * 60000)) forceBedExit(uId, data.roomNum)
}

def forceBedExit(uId, rNum, keepExistingTime = false) {
    if ((state.sleepState["${uId}"] ?: "EMPTY") == "EMPTY") return 

    def now = new Date().time
    def exitTime = now
    
    if (keepExistingTime) {
        if (state.pendingExit["${uId}"] > 0) exitTime = state.pendingExit["${uId}"]
        else if (state.sleepState["${uId}"] == "EMPTY" && state.lastExitTime["${uId}"]) exitTime = state.lastExitTime["${uId}"]
    }
    
    state.lastExitTime["${uId}"] = exitTime
    def inBed = state.inBedTime["${uId}"] ?: exitTime
    def bathDur = state.bathroomDuration["${uId}"] ?: 0
    
    state.lastSessionInBed["${uId}"] = Math.max(0, (((exitTime - inBed) / 60000).toInteger() - bathDur))
    
    if (state.asleepTime["${uId}"]) {
        state.lastSessionAsleep["${uId}"] = Math.max(0, (((exitTime - state.asleepTime["${uId}"]) / 60000).toInteger() - bathDur))
        if (state.sleepState["${uId}"] == "SLEEPING") {
             def stillStart = state.lastStillStartTime["${uId}"] ?: state.asleepTime["${uId}"] ?: exitTime
             def gap = exitTime - stillStart
             if (gap >= 2700000) state.deepSleepDuration["${uId}"] = (state.deepSleepDuration["${uId}"] ?: 0) + (gap / 60000).toInteger()
             
             if (state.lastSessionInBed["${uId}"] > 45) {
                 recordSleepData(uId, inBed, exitTime)
                 recordSleepScoreHistory(uId)
                 def targetVar = settings["sleepScoreVariable_${uId}"]
                 if (targetVar) {
                     def history = state.sleepScoreHistory["${uId}"]
                     def finalLockedScore = (history && history.size() > 0) ? history[0].score : calculateEfficiencyScore(uId)
                     setGlobalVar(targetVar, finalLockedScore)
                 }
             }
        }
    }
    
    state.sleepState["${uId}"] = "EMPTY"
    state.pendingExit["${uId}"] = 0
    updateRoomSleepState(rNum)
    
    if (settings["enableAutoWake${rNum}"]) {
         def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
         def allEmpty = true
         for (int u = 1; u <= numUsers; u++) { if (state.sleepState["${rNum}_${u}"] != "EMPTY") { allEmpty = false; break } }
         def sw = settings["roomSwitch${rNum}"]
         
         if (allEmpty && sw && sw.currentValue("switch") == "on") {
             def isNightLocked = settings["autoWakeNightLock${rNum}"] && !isWakeTimeReached(rNum)
             if (isNightLocked) {
             } else {
                 def lockoutMillis = (settings["autoWakeLockout${rNum}"] != null ? settings["autoWakeLockout${rNum}"].toInteger() : 120) * 60000
                 def timeSinceGn = new Date().time - (state."goodNightStartTime${rNum}" ?: 0)
                 
                 if (timeSinceGn >= lockoutMillis) {
                     def wakeDelay = settings["autoWakeDelay${rNum}"] != null ? settings["autoWakeDelay${rNum}"].toInteger() : 5
                     if (wakeDelay > 0) {
                         state."autoWakePending${rNum}" = new Date().time + (wakeDelay * 60000)
                         runIn(wakeDelay * 60, "executeAutoWake", [data: [roomNum: rNum], overwrite: false])
                     } else sw.off()
                 }
             }
         }
    }
}

def executeAutoWake(data) {
    def rNum = data.roomNum
    if (new Date().time < (state."autoWakePending${rNum}" ?: 0) - 2000) return 
    state.remove("autoWakePending${rNum}")
    
    if (settings["autoWakeNightLock${rNum}"] && !isWakeTimeReached(rNum)) return
    
    def sw = settings["roomSwitch${rNum}"]
    if (sw && sw.currentValue("switch") == "on") {
         def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
         def allEmpty = true
         for (int u = 1; u <= numUsers; u++) { if (state.sleepState["${rNum}_${u}"] != "EMPTY") { allEmpty = false; break } }
         if (allEmpty) sw.off()
    }
}

def fallbackMotionHandler(evt) {
    if (appEnableSwitch?.currentValue("switch") == "off" || evt.value != "active") return
    def rNum = getRoomNumFromDevice(evt.device.id, "motionSensor")
    if (!rNum) return
    state.lastRoomMotionTime["${rNum}"] = new Date().time

    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        if (cState == "EMPTY" || cState == "BATHROOM TRIP") continue
        
        def lockMillis = (settings["settlingLockTime${rNum}"] != null ? settings["settlingLockTime${rNum}"].toInteger() : 30) * 60000
        def lockStart = Math.max((state.inBedTime?."${uId}" ?: 0) as Long, (state.sessionResumedTime?."${uId}" ?: 0) as Long)
        if (lockStart > 0 && (new Date().time - lockStart) < lockMillis) continue

        def mState = settings["pressureMat_${uId}"]?.currentValue("contact") ?: settings["pressureMat_${uId}"]?.currentValue("presence")
        if (mState == "closed" || mState == "present") processUserMovement(uId, rNum, "frame")
        
        if (settings["enableCrossTalk${rNum}"]) {
            def partnerId = (u == 1) ? "${rNum}_2" : "${rNum}_1"
            if (state.sleepState["${partnerId}"] != null && state.sleepState["${partnerId}"] != "EMPTY") {
                state.deafenedUntil["${partnerId}"] = new Date().time + ((settings["crossTalkDeafenTime${rNum}"] != null ? settings["crossTalkDeafenTime${rNum}"].toInteger() : 60) * 1000)
            }
        }
    }
}

def fallbackBathroomMotionHandler(evt) {
    if (appEnableSwitch?.currentValue("switch") == "off" || evt.value != "active") return
    def rNum = getRoomNumFromDevice(evt.device.id, "bathroomMotion")
    if (!rNum) return
    
    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        if ((state.sleepState["${uId}"] == "IN BED" || state.sleepState["${uId}"] == "SLEEPING") && (state.pendingExit["${uId}"] ?: 0) > 0) {
            state.lastExitTime["${uId}"] = state.pendingExit["${uId}"]
            state.sleepState["${uId}"] = "BATHROOM TRIP"
            state.pendingExit["${uId}"] = 0
            updateRoomSleepState(rNum)
            
            def stitchSecs = (settings["stitchingWindow${rNum}"] != null ? settings["stitchingWindow${rNum}"].toInteger() : 15) * 60
            state."bathroomTime_${uId}" = new Date().time + (stitchSecs * 1000)
            runIn(stitchSecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: rNum], overwrite: false])
        }
    }
}

def evaluateBathroomTimeout(data) {
    def uId = data.uId
    if (new Date().time < (state."bathroomTime_${uId}" ?: 0) - 2000) return 
    if (state.sleepState["${uId}"] == "BATHROOM TRIP") {
        state.bathroomTrips["${uId}"] = Math.max(0, (state.bathroomTrips["${uId}"] ?: 1) - 1)
        forceBedExit(uId, data.roomNum, true)
    }
}

def getUserIdFromDevice(id, type) {
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) {
        def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
        for (int u = 1; u <= numUsers; u++) {
            if (type == "vibrationSensor" && (settings["vibrationSensor_${i}_${u}"]?.id == id || settings["vibrationSensor2_${i}_${u}"]?.id == id)) return "${i}_${u}"
            else if (type == "pressureMat" && settings["pressureMat_${i}_${u}"]?.id == id) return "${i}_${u}"
        }
    }
    return null
}

def getRoomNumFromDevice(id, type) {
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) { if (settings["${type}_${i}"]?.id == id) return "${i}" }
    return null
}

def formatTimeFromMinutes(mins, isBedtime) {
    if (mins == null) return "Learning..."
    def totalMins = isBedtime ? (12 * 60) + mins : mins
    def h = (totalMins / 60).toInteger() % 24
    def m = totalMins % 60
    def ampm = h >= 12 ? "PM" : "AM"
    def dispH = h % 12
    if (dispH == 0) dispH = 12
    return "${dispH}:${m.toString().padLeft(2, '0')} ${ampm}"
}

def isUserSick(uId) {
    def moodVarName = settings["moodVariable_${uId}"]
    if (!moodVarName) return false
    def valObj = getGlobalVar(moodVarName)
    if (!valObj || !valObj.value) return false
    def currentMood = valObj.value.toString()
    def sickEmojis = ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]
    return sickEmojis.contains(currentMood)
}

def isAnyoneInRoomSick(rNum) {
    def numUsers = settings["numOccupants${rNum}"] ? settings["numOccupants${rNum}"].toInteger() : 1
    for (int u = 1; u <= numUsers; u++) {
        if (isUserSick("${rNum}_${u}")) return true
    }
    return false
}

def calculateEfficiencyScore(uId) {
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    
    if (cState == "EMPTY") {
        def history = state.sleepScoreHistory["${uId}"] ?: []
        if (history.size() > 0) return history[0].score
        return 0
    }
    
    def bathDur = state.bathroomDuration["${uId}"] ?: 0
    def rNum = uId.split('_')[0]
    
    def inBed = cState == "EMPTY" ? (state.lastSessionInBed["${uId}"] ?: 0) : Math.max(0, (((new Date().time - (state.inBedTime?."${uId}" ?: new Date().time)) / 60000).toInteger() - bathDur))
    def asleep = cState == "EMPTY" ? (state.lastSessionAsleep["${uId}"] ?: 0) : Math.max(0, (((new Date().time - (state.asleepTime?."${uId}" ?: new Date().time)) / 60000).toInteger() - bathDur))
    
    if (!inBed || inBed < 45) return 0
    
    def efficiency = (asleep.toDouble() / inBed.toDouble()) * 100.0
    def moves = state.movements?."${uId}" ?: 0
    
    def rawPenalty = state.weightedMovementPenalty?."${uId}" != null ? state.weightedMovementPenalty["${uId}"].toDouble() : (moves * 0.15)
    def movementPenalty = Math.min(20.0, rawPenalty) 
    
    def finalScore = efficiency - movementPenalty

    if (settings["enableClinicalScoring_${uId}"]) {
        def targetHours = settings["targetSleepHours_${uId}"] != null ? settings["targetSleepHours_${uId}"].toDouble() : 7.5
        if (isUserSick(uId)) targetHours += 1.5
        
        def durationRatio = Math.min(1.0, asleep / (targetHours * 60))
        finalScore = (finalScore * 0.6) + ((durationRatio * 100.0) * 0.4)

        def latency = state.sleepLatency["${uId}"] ?: 0
        if (latency >= 30 && latency < 60) finalScore -= 5.0
        else if (latency >= 60) finalScore -= 12.0 
        
        def cTemp = settings["tempSensor${rNum}"] ? settings["tempSensor${rNum}"].currentValue("temperature") : null
        def cHum = settings["humSensor${rNum}"] ? settings["humSensor${rNum}"].currentValue("humidity") : null
        
        if (cTemp != null && cTemp > 72.0) finalScore -= Math.min(8.0, (cTemp - 72.0) * 1.5)
        if (cHum != null && cHum > 55.0) finalScore -= 2.0 
    }
    return Math.max(0, Math.min(100, Math.round(finalScore).toInteger()))
}

def recordSleepScoreHistory(uId) {
    def score = calculateEfficiencyScore(uId)
    if (score == 0 && (state.lastSessionInBed["${uId}"] ?: 0) < 60) return 

    def duration = state.lastSessionAsleep["${uId}"] ?: 0
    def latency = state.sleepLatency["${uId}"] ?: 0
    def moves = state.movements["${uId}"] ?: 0
    def deepSleep = state.deepSleepDuration["${uId}"] ?: 0
    def lightSleep = Math.max(0, duration - deepSleep)
    def dateStr = new Date().format("MMM dd", location.timeZone ?: TimeZone.getDefault())
    def startTimeStr = state.inBedTime?."${uId}" ? new Date(state.inBedTime["${uId}"] as Long).format("h:mm a") : "--:--"
    def endTimeStr = state.lastExitTime?."${uId}" ? new Date(state.lastExitTime["${uId}"] as Long).format("h:mm a") : "--:--"

    def histList = state.sleepScoreHistory["${uId}"] ?: []
    
    if (histList.size() > 0 && histList[0].date == dateStr) {
        def prev = histList[0]
        def totalDuration = prev.duration + duration
        
        if (totalDuration > 0) {
            prev.score = Math.round(((prev.score * prev.duration) + (score * duration)) / totalDuration).toInteger()
        }
        
        prev.duration = totalDuration
        prev.moves += moves
        prev.deepSleep = (prev.deepSleep ?: 0) + deepSleep
        prev.lightSleep = (prev.lightSleep ?: 0) + lightSleep
        prev.endTime = endTimeStr 
    } else {
        histList.add(0, [
            date: dateStr, 
            score: score, 
            duration: duration, 
            latency: latency, 
            moves: moves,
            deepSleep: deepSleep,
            lightSleep: lightSleep,
            startTime: startTimeStr,
            endTime: endTimeStr
        ])
    }
    
    if (histList.size() > 7) histList = histList.take(7)
    state.sleepScoreHistory["${uId}"] = histList
    
    def sum = 0
    histList.each { sum += it.score }
    state.avgSleepScore["${uId}"] = Math.round(sum / histList.size()).toInteger()
}

def generateProfessionalHistoryTile(entry, targetMins) {
    def scoreColor = entry.score >= 85 ? "#28a745" : (entry.score >= 70 ? "#17a2b8" : (entry.score >= 50 ? "#ffc107" : "#dc3545"))
    def fmtDur = { m -> m >= 60 ? "${(m/60).toInteger()}h ${m%60}m" : "${m}m" }
    
    def insights = []
    if (entry.latency >= 45) insights << "Prolonged onset latency (${entry.latency}m)"
    if (entry.moves >= 60) insights << "Elevated kinetic restlessness"
    if (entry.duration < (targetMins - 60)) insights << "Sub-optimal sleep duration"
    def insightHtml = insights.size() > 0 ? insights.join(" • ") : "Optimal sleep architecture achieved."

    return """
    <tr>
        <td class='dash-hl' style='width: 25%; font-size:16px; border-right: 3px solid ${scoreColor};'>
            <b>${entry.date}</b><br>
            <span style='font-size:11px; color:#666;'>${entry.startTime} - ${entry.endTime}</span>
        </td>
        <td class='dash-val' style='padding: 10px 15px !important;'>
            <div style='display:flex; justify-content:space-between; align-items:center; margin-bottom:5px;'>
                <span style='font-size: 22px; font-weight: bold; color: ${scoreColor};'>${entry.score}% BMS</span>
                <span style='font-size: 13px; color:#495057;'><b>Total:</b> ${fmtDur(entry.duration)}</span>
            </div>
            
            <div style='display:flex; gap: 15px; font-size: 12px; color: #555; background: #f8f9fa; padding: 6px; border-radius: 4px; margin-bottom: 5px;'>
                <span><b>Deep:</b> ${fmtDur(entry.deepSleep ?: 0)}</span>
                <span><b>Light:</b> ${fmtDur(entry.lightSleep ?: 0)}</span>
                <span><b>Awake:</b> ${fmtDur(entry.latency)}</span>
                <span><b>Kinetic Events:</b> ${entry.moves}</span>
            </div>
            
            <div style='font-size:11px; color:#666; font-style: italic;'>
                ${insightHtml}
            </div>
        </td>
    </tr>
    """
}

def generateHtmlTile(uId) {
    ensureStateMaps()
    def uName = settings["userName_${uId}"] ?: "Occupant"
    
    // --- 1. MOOD FETCHING & NAME BADGE ---
    def moodVarName = settings["moodVariable_${uId}"]
    def currentMood = ""
    if (moodVarName) {
        def valObj = getGlobalVar(moodVarName)
        if (valObj && valObj.value) currentMood = valObj.value.toString()
    }
    
    if (currentMood) uName += " ${currentMood}"
    else if (isUserSick(uId)) uName += " 🤒"
    
    // --- 2. MODE EXPLANATION ROW ---
    def modeAdjustmentsRow = ""
    if (isUserSick(uId)) {
        modeAdjustmentsRow = """
        <tr>
            <td class="dash-hl">Active User Mode</td>
            <td class="dash-val">
                <span style="color:red; font-weight:bold;">🤒 SICK / RECOVERY MODE ACTIVE</span><br>
                <span style="font-size:11px; color:#666; line-height:1.2; display:block; margin-top:3px;">
                    <b>System Changes:</b> +1.5h added to sleep target, 2m rapid sleep detection, daytime lockouts bypassed for naps, ambient audio disabled, and instant light shutoff enforced.
                </span>
            </td>
        </tr>"""
    } else if (currentMood) {
        modeAdjustmentsRow = """
        <tr>
            <td class="dash-hl">Active User Mode</td>
            <td class="dash-val">
                <span style="color:#007bff; font-weight:bold;">${currentMood} STANDARD TRACKING</span><br>
                <span style="font-size:11px; color:#666;"><b>System Changes:</b> Normal physiological tracking and schedule orchestrator constraints active.</span>
            </td>
        </tr>"""
    }

    def status = state.sleepState?."${uId}" ?: "EMPTY"
    def posture = state.posture?."${uId}" ?: "LYING_FLAT"
    def score = calculateEfficiencyScore(uId)
    
    def vSens = settings["vibrationSensor_${uId}"]
    def vSens2 = settings["vibrationSensor2_${uId}"]
    def pMat = settings["pressureMat_${uId}"]
    
    def vSensState = "none"
    if (vSens?.currentValue("acceleration") == "active" || vSens2?.currentValue("acceleration") == "active") vSensState = "active"
    else if (vSens || vSens2) vSensState = "inactive"
    
    def pMatState = pMat ? (pMat.currentValue("contact") ?: pMat.currentValue("presence") ?: "open") : "none"
    
    def presenceDev = settings["occupantPresence_${uId}"]
    def presState = presenceDev ? presenceDev.currentValue("presence").toUpperCase() : "N/A"
    if (presenceDev && presenceDev.currentValue("presence") == "not present") pMatState = "AWAY"
    
    def inBedTimeStr = state.inBedTime?."${uId}" ? new Date((state.inBedTime."${uId}") as Long).format("h:mm a", location.timeZone) : "--:--"
    def exitTimeStr = state.lastExitTime?."${uId}" ? new Date((state.lastExitTime."${uId}") as Long).format("h:mm a", location.timeZone) : "--:--"
    
    def now = new Date().time
    def rNum = uId.split('_')[0]
    def wLock = state."wakeLockout_${rNum}" ?: 0
    def liveInBed = 0
    def liveAsleep = 0

    if (status == "IN BED" || status == "SLEEPING" || status == "PENDING ENTRY" || status == "BATHROOM TRIP") {
        if (state.inBedTime?."${uId}") liveInBed = ((now - state.inBedTime."${uId}") / 60000).toInteger()
        if (state.asleepTime?."${uId}") liveAsleep = ((now - state.asleepTime."${uId}") / 60000).toInteger()
    } else {
        liveInBed = state.lastSessionInBed?."${uId}" ?: 0
        liveAsleep = state.lastSessionAsleep?."${uId}" ?: 0
    }

    def deepSleep = state.deepSleepDuration?."${uId}" ?: 0
    if (status == "SLEEPING") {
         def stillStart = state.lastStillStartTime["${uId}"] ?: state.asleepTime["${uId}"] ?: now
         if ((now - stillStart) >= 2700000) deepSleep += ((now - stillStart) / 60000).toInteger()
    }

    def lightSleep = Math.max(0, liveAsleep - deepSleep)
    def awakeTime = Math.max(0, liveInBed - liveAsleep)
    
    def timerInfo = ""
    if (status == "EMPTY" && wLock > now) {
        def remain = ((wLock - now) / 60000).toInteger()
        timerInfo = "<tr><td class='dash-hl'>AI Timer</td><td class='dash-val'><span style='color:orange; font-weight:bold;'>⏳ Wake Lockout: ${remain}m remaining</span></td></tr>"
    } 
    else if (status == "PENDING ENTRY" && state."verifyEntryTime_${uId}") {
        def remain = (((state."verifyEntryTime_${uId}" as Long) - now) / 1000).toInteger()
        if (remain > 0) timerInfo = "<tr><td class='dash-hl'>AI Timer</td><td class='dash-val'><span style='color:orange; font-weight:bold;'>⏳ Verifying Entry: ${(remain/60).toInteger()}m ${(remain%60).toInteger()}s</span></td></tr>"
    } 
    else if (state.pendingExit?."${uId}" > 0 && state."verifyExitTime_${uId}") {
        def remain = (((state."verifyExitTime_${uId}" as Long) - now) / 1000).toInteger()
        if (remain > 0) timerInfo = "<tr><td class='dash-hl'>AI Timer</td><td class='dash-val'><span style='color:red; font-weight:bold;'>⏳ Verifying Exit: ${(remain/60).toInteger()}m ${(remain%60).toInteger()}s</span></td></tr>"
    } 
    else if (status == "BATHROOM TRIP" && state."bathroomTime_${uId}") {
        def remain = (((state."bathroomTime_${uId}" as Long) - now) / 1000).toInteger()
        if (remain > 0) timerInfo = "<tr><td class='dash-hl'>AI Timer</td><td class='dash-val'><span style='color:#007bff; font-weight:bold;'>🚽 Awaiting Return: ${(remain/60).toInteger()}m ${(remain%60).toInteger()}s</span></td></tr>"
    } 
    else if (status == "IN BED" && state."evalSleepTime_${uId}") {
        def remain = (((state."evalSleepTime_${uId}" as Long) - now) / 1000).toInteger()
        if (remain > 0) timerInfo = "<tr><td class='dash-hl'>Sleep Countdown</td><td class='dash-val'><span style='color:#28a745; font-weight:bold;'>💤 Falling Asleep In: ${(remain/60).toInteger()}m ${(remain%60).toInteger()}s</span></td></tr>"
    }
    
    def fmtDur = { m -> m >= 60 ? "${(m/60).toInteger()}h ${m%60}m" : "${m}m" }
    
    def effortVarName = settings["effortScoreInputVar_${uId}"]
    def liveEffort = 0
    if (effortVarName) {
        def val = getGlobalVar(effortVarName)?.value
        if (val != null && val.toString().isNumber()) {
            liveEffort = val.toInteger()
        }
    }
    if (liveEffort == 0 && state.dailyEffortScore?."${uId}") {
        liveEffort = state.dailyEffortScore["${uId}"]
    }
    
    def mlRows = ""
    def recommendationRow = ""
    def effortRow = ""
    
    if (settings["enableML_${uId}"]) {
        mlRows = """
        <tr><td class="dash-hl">AI Schedule: Weekday</td><td class="dash-val">Bed: ${formatTimeFromMinutes(getLearnedTime(uId, false, "weekday"), true)} | Wake: ${formatTimeFromMinutes(getLearnedTime(uId, true, "weekday"), false)}</td></tr>
        <tr><td class="dash-hl">AI Schedule: Weekend</td><td class="dash-val">Bed: ${formatTimeFromMinutes(getLearnedTime(uId, false, "weekend"), true)} | Wake: ${formatTimeFromMinutes(getLearnedTime(uId, true, "weekend"), false)}</td></tr>
        """
        
        effortRow = "<tr><td class='dash-hl'>Daily Workout Strain</td><td class='dash-val'>⚡ <b>${liveEffort}</b> pts</td></tr>"
        
        def learnedBedMins = getLearnedTime(uId, false)
        if (learnedBedMins) {
            def history = state.sleepScoreHistory["${uId}"] ?: []
            def lastScore = history.size() > 0 ? history[0].score : 85
            def threshold = settings["poorSleepThreshold_${uId}"] != null ? settings["poorSleepThreshold_${uId}"].toInteger() : 75
            def adjustment = 0
            def recReason = "Stable baseline."
            
            if (isUserSick(uId)) {
                adjustment = 60
                recReason = "<span style='color:red; font-weight:bold;'>Sick Mode Enabled</span>. 60m earlier."
            } else if (liveEffort > 75) {
                adjustment = 45
                recReason = "<span style='color:#ffc107; font-weight:bold;'>High physical strain (${liveEffort} pts)</span>. 45m earlier."
            } else if (lastScore < 60) {
                adjustment = 45
                recReason = "<span style='color:#dc3545; font-weight:bold;'>Poor recovery (${lastScore}%)</span>. 45m earlier."
            } else if (lastScore < threshold || liveEffort > 50) {
                adjustment = 30
                if (liveEffort > 50 && lastScore < threshold) {
                    recReason = "Moderate strain & lower recovery. 30m earlier."
                } else if (liveEffort > 50) {
                    recReason = "Moderate strain (${liveEffort} pts). 30m earlier."
                } else {
                    recReason = "Moderate recovery (${lastScore}%). 30m earlier."
                }
            }
            
            def recommendedTime = formatTimeFromMinutes(learnedBedMins - adjustment, true)
            if (adjustment > 0) {
                recommendationRow = "<tr><td class='dash-hl'>Tailored Bedtime</td><td class='dash-val'><b>${recommendedTime}</b> <span style='font-size:11px; color:#666;'><br>${recReason}</span></td></tr>"
            } else {
                recommendationRow = "<tr><td class='dash-hl'>Tailored Bedtime</td><td class='dash-val'><span style='color:green;'><b>${recommendedTime}</b></span> <span style='font-size:11px; color:#666;'><br>${recReason}</span></td></tr>"
            }
        } else {
            recommendationRow = "<tr><td class='dash-hl'>Tailored Bedtime</td><td class='dash-val'><span style='color:#666;'><i>Learning baseline...</i></span></td></tr>"
        }
    }

    def postureText = (status != "EMPTY" && settings["enableAnalogPressure_${uId}"]) ? " | Posture: ${posture.replace('_', ' ')}" : ""

    return """
    <tr><td colspan='2' class="dash-subhead" style="background-color:#1e293b; color:white;">Sleep Metrics: ${uName} - ${status.toUpperCase()} (Live BMS: ${score}%)${postureText}</td></tr>
    ${modeAdjustmentsRow}
    ${timerInfo}
    ${mlRows}
    ${effortRow}
    ${recommendationRow}
    <tr><td class="dash-hl">Session Timeline</td><td class="dash-val">In: ${inBedTimeStr} | Out: ${exitTimeStr}</td></tr>
    <tr><td class="dash-hl">Stage Profiles</td><td class="dash-val">Deep: ${fmtDur(deepSleep)} | Light: ${fmtDur(lightSleep)} | Awake: ${fmtDur(awakeTime)}</td></tr>
    <tr><td class="dash-hl">Hardware Telemetry</td><td class="dash-val">Vib: ${vSensState.toUpperCase()} | Mat: ${pMatState.toUpperCase()} | Pres: ${presState}</td></tr>
    """
}

def forceResetAllBeds() {
    def maxRooms = settings.numRooms ? settings.numRooms.toInteger() : 1
    for (int i = 1; i <= maxRooms; i++) {
        def numUsers = settings["numOccupants${i}"] ? settings["numOccupants${i}"].toInteger() : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            state.sleepState["${uId}"] = "EMPTY"
            state.inBedTime["${uId}"] = null
            state.latencyClockStart["${uId}"] = null
            state.asleepTime["${uId}"] = null
            state.pendingExit["${uId}"] = 0
            state.movements["${uId}"] = 0
            state.bathroomTrips["${uId}"] = 0
            state.bathroomDuration["${uId}"] = 0
            state.deepSleepDuration["${uId}"] = 0
            state.weightedMovementPenalty["${uId}"] = 0.0
            state.posture["${uId}"] = "LYING_FLAT"
            state.dailyEffortScore["${uId}"] = 0
        }
        updateRoomSleepState(i)
    }
}

def middayReset() {
    forceResetAllBeds()
}

def calculateDynamicDelay(roomNum) {
    def minDelay = settings["goodNightDelayMin${roomNum}"] != null ? settings["goodNightDelayMin${roomNum}"].toDouble() : 5.0
    def maxDelay = settings["goodNightDelayMax${roomNum}"] != null ? settings["goodNightDelayMax${roomNum}"].toDouble() : 30.0
    def targetTime = settings["expectedBedtime${roomNum}"]

    if (!targetTime) return Math.round(maxDelay).toInteger()

    def tz = location.timeZone ?: TimeZone.getDefault()
    def calNow = Calendar.getInstance(tz)
    def nowMins = (calNow.get(Calendar.HOUR_OF_DAY) * 60) + calNow.get(Calendar.MINUTE)
    
    def targetDate = timeToday(targetTime, tz)
    def calTarget = Calendar.getInstance(tz)
    calTarget.setTime(targetDate)
    def targetMins = (calTarget.get(Calendar.HOUR_OF_DAY) * 60) + calTarget.get(Calendar.MINUTE)

    def diffMins = Math.abs(nowMins - targetMins)
    if (diffMins > 720) diffMins = 1440 - diffMins

    def maxDeviation = 120.0
    if (diffMins > maxDeviation) diffMins = maxDeviation

    def ratio = diffMins / maxDeviation
    return Math.round(minDelay + (ratio * (maxDelay - minDelay))).toInteger()
}

def logInfo(msg) {
    if (txtLogEnable) log.info "${app.label}: ${msg}"
    def hist = state.eventLog ?: []
    def timeStamp = new Date().format("MM/dd hh:mm:ss a", location.timeZone)
    hist.add(0, "[${timeStamp}] ${msg}")
    if (hist.size() > 20) hist = hist.take(20)
    state.eventLog = hist
}

def recordSleepData(uId, inBedMillis, exitMillis) {
    if (!settings["enableML_${uId}"]) return
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def calIn = Calendar.getInstance(tz)
    calIn.setTimeInMillis(inBedMillis as Long)
    
    def calOut = Calendar.getInstance(tz)
    calOut.setTimeInMillis(exitMillis as Long)
    
    def isWeekendIn = (calIn.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY || calIn.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY)
    def isWeekendOut = (calOut.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || calOut.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    
    def inHour = calIn.get(Calendar.HOUR_OF_DAY)
    def inMin = calIn.get(Calendar.MINUTE)
    def inMinsFromNoon = inHour >= 12 ? ((inHour - 12) * 60) + inMin : ((inHour + 12) * 60) + inMin
    
    def outMinsFromMidnight = (calOut.get(Calendar.HOUR_OF_DAY) * 60) + calOut.get(Calendar.MINUTE)
    
    def typeIn = isWeekendIn ? "weekend" : "weekday"
    def typeOut = isWeekendOut ? "weekend" : "weekday"
    
    def inKey = "${uId}_${typeIn}"
    def outKey = "${uId}_${typeOut}"
    
    def inList = state.learnedBedTimes[inKey] ?: []
    def outList = state.learnedWakeTimes[outKey] ?: []
    
    inList.add(inMinsFromNoon)
    outList.add(outMinsFromMidnight)
    
    if (inList.size() > 14) inList.remove(0)
    if (outList.size() > 14) outList.remove(0)
    
    state.learnedBedTimes[inKey] = inList
    state.learnedWakeTimes[outKey] = outList
}

def getLearnedTime(uId, isWakeTime, specificType = null) {
    if (!settings["enableML_${uId}"]) return null
    def type = specificType
    def rNum = uId.split('_')[0]
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    
    if (!type) {
        if (isWakeTime) type = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) ? "weekend" : "weekday"
        else type = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) ? "weekend" : "weekday"
    }
    
    def key = "${uId}_${type}"
    def list = isWakeTime ? (state.learnedWakeTimes?."${key}" ?: []) : (state.learnedBedTimes?."${key}" ?: [])
    
    if (list.size() == 0) return null
    
    def sum = 0
    list.each { sum += it }
    def learnedMins = (sum / list.size()).toInteger()
    
    def baselineMins = null
    
    if (isWakeTime) {
        def wStart = (type == "weekend") ? settings["weekendWakeStart_${uId}"] : settings["weekdayWakeStart_${uId}"]
        if (wStart) {
            def d = timeToday(wStart, tz)
            def c = Calendar.getInstance(tz); c.setTime(d)
            baselineMins = (c.get(Calendar.HOUR_OF_DAY) * 60) + c.get(Calendar.MINUTE)
        } else {
            def oEnd = (type == "weekend" && settings["orchestratorEndTimeWeekend${rNum}"]) ? settings["orchestratorEndTimeWeekend${rNum}"] : settings["orchestratorEndTime${rNum}"]
            if (oEnd) {
                def d = timeToday(oEnd, tz)
                def c = Calendar.getInstance(tz); c.setTime(d)
                baselineMins = (c.get(Calendar.HOUR_OF_DAY) * 60) + c.get(Calendar.MINUTE)
            } else baselineMins = 360 
        }
    } else {
        def bTime = settings["expectedBedtime${rNum}"]
        if (bTime) {
            def d = timeToday(bTime, tz)
            def c = Calendar.getInstance(tz); c.setTime(d)
            def h = c.get(Calendar.HOUR_OF_DAY)
            def m = c.get(Calendar.MINUTE)
            baselineMins = h >= 12 ? ((h - 12) * 60) + m : ((h + 12) * 60) + m
        } else baselineMins = 600 
    }
    
    if (baselineMins != null) {
        def diff = learnedMins - baselineMins
        if (diff > 720) diff -= 1440
        else if (diff < -720) diff += 1440
        
        if (diff > 120) learnedMins = baselineMins + 120
        else if (diff < -120) learnedMins = baselineMins - 120
        
        if (learnedMins < 0) learnedMins += 1440
        if (learnedMins >= 1440 && isWakeTime) learnedMins -= 1440
    }
    
    return learnedMins
}

def isNearLearnedTime(uId, isWakeTime, windowMins = 45) {
    if (!settings["enableML_${uId}"]) return false
    def avgTime = getLearnedTime(uId, isWakeTime)
    if (avgTime == null) return false
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    
    def currentMins = 0
    if (isWakeTime) {
        currentMins = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
    } else {
        def inHour = cal.get(Calendar.HOUR_OF_DAY)
        def inMin = cal.get(Calendar.MINUTE)
        currentMins = inHour >= 12 ? ((inHour - 12) * 60) + inMin : ((inHour + 12) * 60) + inMin
    }
    
    return Math.abs(currentMins - avgTime) <= windowMins
}
