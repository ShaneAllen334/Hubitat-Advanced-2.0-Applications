/**
 * Advanced Room Manager 2.0
 */

definition(
    name: "Advanced Room Manager 2.0",
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

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Room Occupancy</b>", install: true, uninstall: true) {
        
        def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5

        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
            
            def globalStatus = (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${globalStatus}</div>"
            
            def hasZones = false

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; vertical-align: top; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 20%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; font-size: 11px; color: #495057; }
                .dash-val { text-align: left !important; }
            </style>
            """
            
            def statusText = dashHTML + "<table class='dash-table'>"
            statusText += "<thead><tr><th>Room</th><th>State & Timers</th><th>Sensors</th><th>Devices</th></tr></thead>"
            statusText += "<tbody>"

            for (int i = 1; i <= maxRooms; i++) {
                if (settings["enableZ${i}"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
              
                    def isOccupied = getRoomOccupancyState(i)
                    def currentState = state."currentRoomStates_z${i}"
                    def mDevs = settings["z${i}Motion"]
                    def vDevs = settings["z${i}Vibration"]
        
                    def devs = settings["z${i}Switches"] ?: []
                    def partDevs = settings["z${i}PartialSwitches"] ?: []
                    def softDevs = settings["z${i}SoftKillDevices"] ?: []
                    def pMonitor = settings["z${i}PowerMonitor"]
                    def oSwitch = settings["z${i}OverrideSwitch"]
        
                    def gnSwitch = settings["z${i}GoodNightSwitch"]
                    def speakerDev = settings["z${i}Sonos"]
                    
                    def allAuxDevs = getAllAuxDevsForRoom(i)
          
                    def statusAdditions = []
                    def sensorDetails = []
               
                    def maxRemainingMs = 0
                    def isHardActive = false

                    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
                        sensorDetails << "<span style='color:purple; font-weight:bold; font-size:11px;'>🌙 Good Night: ON (Locked)</span>"
                        isHardActive = true
                    }

                    if (mDevs) {
                        mDevs.each { dev ->
                            def val = dev.currentValue("motion")
                            def color = val == "active" ? "blue" : "gray"
                            def fw = val == "active" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>🏃 ${dev.displayName}: ${val}</span>"
                            if (val == "active") isHardActive = true
                        }

                        def mTimeout = (settings["z${i}Timeout"] ?: 15) * 60000
                        def mLast = state."zoneLastActive_z${i}" ?: null
                        def mReqHits = settings["z${i}MotionActivationHits"] ?: 1
                        def mHits = state."motionHitCount_z${i}" ?: 0
              
                        if (mLast && !mDevs.any{it.currentValue("motion") == "active"}) {
                            def mLeft = mTimeout - (now() - mLast)
                            if (mLeft > maxRemainingMs) maxRemainingMs = mLeft
                        }
                        
                        if (settings["z${i}TurnOnTriggers"]?.contains("Motion Hit Count") && mReqHits > 1 && mHits > 0) {
                            def windowMs = (settings["z${i}MotionActivationWindow"] ?: 3) * 60000
                            def hitStart = state."motionHitStartTime_z${i}" ?: (mLast ?: now())
                            def windowLeft = (hitStart + windowMs) - now()
                            
                            if (windowLeft > 0) {
                                def secsLeft = Math.ceil(windowLeft / 1000).toInteger()
                                statusAdditions << "<span style='color:purple; font-size:11px;'>Motion Hits: ${mHits}/${mReqHits} (Resets in ${secsLeft}s)</span>"
                            }
                        }
                        
                        if (settings["z${i}TurnOnTriggers"]?.contains("Continuous Motion")) {
                            def activeSince = state."motionActiveSince_z${i}" ?: null
                            if (activeSince) {
                                def reqMins = settings["z${i}MotionContinuousDuration"] ?: 3
                                def left = (activeSince + (reqMins * 60000)) - now()
                                if (left > 0) {
                                    def secsLeft = Math.ceil(left / 1000).toInteger()
                                    statusAdditions << "<span style='color:teal; font-size:11px;'>Continuous Motion: ${secsLeft}s left</span>"
                                } else {
                                    def activeMins = Math.floor((now() - activeSince) / 60000).toInteger()
                                    statusAdditions << "<span style='color:teal; font-size:11px;'>Continuous Motion: Active for ${activeMins}m</span>"
                                }
                            }
                        }
                    }

                    if (vDevs) {
                        vDevs.each { dev ->
                            def val = dev.currentValue("acceleration")
                            def color = val == "active" ? "blue" : "gray"
                            def fw = val == "active" ? "bold" : "normal"
                            sensorDetails << "<span style='color:${color}; font-weight:${fw}; font-size:11px;'>📳 ${dev.displayName}: ${val}</span>"
                            if (val == "active") isHardActive = true
                        }

                        def vTimeout = (settings["z${i}VibeTimeout"] ?: 5) * 60000
                        def vLast = state."vibeLastActive_z${i}" ?: null
                        def vReqHits = settings["z${i}VibeActivationHits"] ?: 1
                        def vHits = state."vibeHitCount_z${i}" ?: 0

                        if (vLast && !vDevs.any{it.currentValue("acceleration") == "active"}) {
                            def vLeft = vTimeout - (now() - vLast)
                            if (vLeft > maxRemainingMs) maxRemainingMs = vLeft
                        }
                        
                        if (settings["z${i}TurnOnTriggers"]?.contains("Vibration") && vReqHits > 1 && vHits > 0) {
                            def windowMs = (settings["z${i}VibeActivationWindow"] ?: 3) * 60000
                            def hitStart = state."vibeHitStartTime_z${i}" ?: (vLast ?: now())
                            def windowLeft = (hitStart + windowMs) - now()
                            if (windowLeft > 0) {
                                def secsLeft = Math.ceil(windowLeft / 1000).toInteger()
                                statusAdditions << "<span style='color:purple; font-size:11px;'>Vibe Hits: ${vHits}/${vReqHits} (Resets in ${secsLeft}s)</span>"
                            }
                        }
                    }
                    
                    if (oSwitch && oSwitch.currentValue("switch") == "on") {
                        def switchMode = state."switchIsManual_z${i}" ? "Manual" : "Auto-Sync"
                        sensorDetails << "<span style='color:orange; font-weight:bold; font-size:11px;'>🔘 Override Switch: ON (${switchMode})</span>"
                    }

                    if (pMonitor) {
                        def currentDraw = pMonitor.currentValue("power") ?: 0.0
                        def safeThresh = settings["z${i}ActiveWattageThreshold"] ?: 15.0
                        if (currentDraw > safeThresh) {
                            statusAdditions << "<span style='color:red; font-size:11px; font-weight:bold;'>Power Lock Active (${currentDraw}W)</span>"
                            isHardActive = true
                        }
                    }

                    if (!isOccupied && state."waitForClear_z${i}") {
                        statusAdditions << "<span style='color:purple; font-size:11px; font-weight:bold;'>Force Off: Waiting for sensors to clear...</span>"
                    }

                    if (!isOccupied && currentState == "empty") {
                        def cooldownMins = settings["z${i}UnoccupiedCooldown"] != null ? settings["z${i}UnoccupiedCooldown"].toInteger() : 5
                        if (cooldownMins > 0 && state."unoccupiedLockoutTime_z${i}") {
                            def lockedTime = state."unoccupiedLockoutTime_z${i}"
                            def timeLeft = (lockedTime + (cooldownMins * 60000)) - now()
                            if (timeLeft > 0) {
                                def minsLeft = Math.ceil(timeLeft / 60000).toInteger()
                                if (minsLeft < 1) minsLeft = 1
                                statusAdditions << "<span style='color:brown; font-size:11px; font-weight:bold;'>⏳ Cooldown: Auto-triggers blocked for ~${minsLeft}m</span>"
                            }
                        }
                    }

                    if (state."switchIsManual_z${i}") {
                        def isManualOnMode = (settings["z${i}OccupancyMode"] == "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)")
                        def oTimeout = isManualOnMode ? (settings["z${i}Timeout"] ?: 15) : settings["z${i}OverrideTimeout"]
                        
                        if (oTimeout && oTimeout > 0 && !isHardActive) {
                            def lastM = state."zoneLastActive_z${i}" ?: 0
                            def lastV = state."vibeLastActive_z${i}" ?: 0
                            def maxLast = Math.max(lastM ?: 0, lastV ?: 0)
                            if (maxLast > 0) {
                                def timeLeft = (oTimeout * 60000) - (now() - maxLast)
                                if (timeLeft > 0) {
                                    def minsLeft = Math.ceil(timeLeft / 60000).toInteger()
                                    def label = isManualOnMode ? "Auto-Off" : "Override Auto-Off"
                                    statusAdditions << "<span style='color:blue; font-size:11px;'>${label}: ~${minsLeft}m</span>"
                                }
                            }
                        }
                    }
                    
                    def restrictionReason = getRoomRestrictionReason(i)

                    if (!isOccupied && settings["z${i}Sweeper"]) {
                        def allHard = (devs + partDevs).unique { it.id }
                        def allSoft = (softDevs + (settings["z${i}PartialSoftKillDevices"] ?: [])).unique { it.id }
                        
                        def anyOn = false
                        if (allHard?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (allSoft?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (allAuxDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                        
                        if (anyOn) {
                            def lastAct = state."zoneLastActive_z${i}" ?: now()
                            def timeoutMs = (settings["z${i}SweeperTimeout"] ?: 60) * 60000
                            def sweepMinsLeft = Math.ceil(((lastAct + timeoutMs) - now()) / 60000).toInteger()
                            if (sweepMinsLeft > 0) {
                                statusAdditions << "<span style='color:teal; font-size:11px;'>🧹 Sweeper: ~${sweepMinsLeft}m until auto-off</span>"
                            } else {
                                statusAdditions << "<span style='color:teal; font-size:11px;'>🧹 Sweeper: Pending shutdown...</span>"
                            }
                        }
                    }

                    if (restrictionReason && settings["z${i}AbsoluteSweeper"]) {
                        def allHard = (devs + partDevs).unique { it.id }
                        def allSoft = (softDevs + (settings["z${i}PartialSoftKillDevices"] ?: [])).unique { it.id }
                        
                        def anyOn = false
                        if (allHard?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (allSoft?.any { it.currentValue("switch") == "on" }) anyOn = true
                        if (allAuxDevs?.any { it.currentValue("switch") == "on" }) anyOn = true
                        
                        if (anyOn) {
                            def lastM = state."zoneLastActive_z${i}" ?: 0
                            def lastV = state."vibeLastActive_z${i}" ?: 0
                            def lastAct = Math.max(lastM ?: 0, lastV ?: 0)
                            if (lastAct == 0) lastAct = now()
                            
                            def timeoutMs = (settings["z${i}AbsoluteSweeperTimeout"] ?: 120) * 60000
                            def sweepMinsLeft = Math.ceil(((lastAct + timeoutMs) - now()) / 60000).toInteger()
                            if (sweepMinsLeft > 0) {
                                statusAdditions << "<span style='color:red; font-size:11px;'>🚨 Absolute Sweeper: ~${sweepMinsLeft}m until auto-off</span>"
                            } else {
                                statusAdditions << "<span style='color:red; font-size:11px;'>🚨 Absolute Sweeper: Pending shutdown...</span>"
                            }
                        }
                    }

                    if (isOccupied && !isHardActive && maxRemainingMs > 0) {
                        def mins = Math.floor(maxRemainingMs / 60000).toInteger()
                        def secs = Math.floor((maxRemainingMs % 60000) / 1000).toInteger()
                        def timeStr = mins > 0 ? "${mins}m ${secs}s" : "${secs}s"
                        statusAdditions << "<span style='color:#888; font-size:11px;'>Unoccupied in: ${timeStr}</span>"
                    }

                    if (!isOccupied && state."shutdownDelayActive_z${i}") {
                         statusAdditions << "<span style='color:orange; font-size:11px;'>Safe Shutdown Sequence...</span>"
                    }
                    
                    if (allAuxDevs && allAuxDevs.any { it.currentValue("switch") == "on" }) {
                        if (currentState == "occupied" || currentState == "partial") {
                            statusAdditions << "<span style='color:#007BFF; font-size:11px; font-weight:bold;'>🎮 Aux Active (Tethered to Room)</span>"
                        } else {
                            def auxMins = settings["z${i}AuxTimeout"] ?: 60
                            def lastMotion = state."zoneLastActive_z${i}" ?: now()
                            def minsLeft = Math.ceil(( (lastMotion + (auxMins * 60000)) - now() ) / 60000).toInteger()
                            if (minsLeft > 0) {
                                statusAdditions << "<span style='color:#007BFF; font-size:11px; font-weight:bold;'>🎮 Aux Timeout in ~${minsLeft}m</span>"
                            } else {
                                statusAdditions << "<span style='color:#007BFF; font-size:11px; font-weight:bold;'>🎮 Aux Shutdown Pending...</span>"
                            }
                        }
                    }

                    def dailyStats = state."dailyStats_z${i}" ?: [:]
                    def todayStr = getTodayDateString()
                    def tStats = dailyStats[todayStr] ?: [occTime: 0, unoccTime: 0, count: 0]
                    
                    def lastUpd = state."lastStatUpdate_z${i}" ?: now()
                    def liveDelta = ((now() - lastUpd) / 1000).toLong()
                    if (liveDelta > 600) liveDelta = 0 
                    
                    def liveTodayOcc = tStats.occTime + (isOccupied ? liveDelta : 0)
                    def liveTodayUnocc = tStats.unoccTime + (!isOccupied ? liveDelta : 0)

                    def weeklyOcc = isOccupied ? liveDelta : 0
                    def weeklyUnocc = !isOccupied ? liveDelta : 0
                    def weeklyCount = 0

                    dailyStats.each { k, v ->
                        weeklyOcc += v.occTime
                        weeklyUnocc += v.unoccTime
                        weeklyCount += v.count
                    }

                    def statHtml = "<div style='margin-top: 8px; padding-top: 5px; border-top: 1px dotted #ccc; font-size:11px; color:#444;'>"
                    statHtml += "<table style='width:100%; border:none; font-size:11px; line-height:1.2;'>"
                    statHtml += "<tr><td style='width: 30%;'></td><td style='width: 25%;'><b>Occupied</b></td><td style='width: 25%;'><b>Empty</b></td><td style='width: 20%;'><b>Visits</b></td></tr>"
                    statHtml += "<tr><td><b>Today:</b></td><td>${formatDuration(liveTodayOcc)}</td><td>${formatDuration(liveTodayUnocc)}</td><td>${tStats.count}x</td></tr>"
                    statHtml += "<tr><td><b>7-Day:</b></td><td>${formatDuration(weeklyOcc)}</td><td>${formatDuration(weeklyUnocc)}</td><td>${weeklyCount}x</td></tr>"
                    statHtml += "</table></div>"

                    def stateColor = restrictionReason ? "orange" : (currentState == "partial" ? "#D2691E" : (isOccupied ? "green" : "black"))
                    def stateLabel = restrictionReason ? "PAUSED (${restrictionReason})" : (currentState == "partial" ? "PARTIAL OCC" : (isOccupied ? "OCCUPIED" : "EMPTY"))
                    def isOccupiedDisplay = "<b><span style='color:${stateColor};'>${stateLabel}</span></b>${statHtml}"
                    
                    def sensorListDisplay = sensorDetails ? sensorDetails.join("<br>") : "<span style='color:gray; font-size:11px;'>None Monitored</span>"

                    def devNames = []
                    def allUnifiedPowerDevs = (devs + partDevs + allAuxDevs).findAll { it != null }.unique { it.id }
                    
                    if (allUnifiedPowerDevs) {
                        allUnifiedPowerDevs.each { dev ->
                            def isAuxOnly = allAuxDevs.any { it.id == dev.id } && !(devs + partDevs).any { it.id == dev.id }
                            def onColor = isAuxOnly ? "#007BFF" : "green"
                            def prefix = isAuxOnly ? "🎮" : "⚡"
                            def auxLabel = isAuxOnly ? " (Aux)" : ""
                            
                            def devState = dev.currentValue("switch") == "on" ? "<span style='color:${onColor}; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                            devNames << "<span style='font-size:11px;'>${prefix} ${dev.displayName}${auxLabel}: ${devState}</span>"
                        }
                    }

                    def allSoftDevs = (softDevs + (settings["z${i}PartialSoftKillDevices"] ?: [])).findAll { it != null }.unique { it.id }
                    if (allSoftDevs) {
                        allSoftDevs.each { dev ->
                            def devState = dev.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                            devNames << "<span style='font-size:11px;'>💻 ${dev.displayName}: ${devState}</span>"
                        }
                    }
                    
                    if (speakerDev) {
                        def sState = speakerDev.currentValue("status") ?: speakerDev.currentValue("state") ?: "unknown"
                        devNames << "<span style='font-size:11px;'>🎵 ${speakerDev.displayName}: <span style='color:purple; font-weight:bold;'>${sState.toUpperCase()}</span></span>"
                    }

                    def fullDeviceColumn = devNames ? devNames.join("<br>") : "<span style='color:gray; font-size:11px;'>None Configured</span>"

                    statusText += "<tr>"
                    statusText += "<td class='dash-hl'>${zName}</td>"
                    statusText += "<td>${isOccupiedDisplay}</td>"
                    statusText += "<td>${sensorListDisplay}</td>"
                    statusText += "<td class='dash-val'>${fullDeviceColumn}</td>"
                    statusText += "</tr>"

                    if (statusAdditions) {
                        statusText += "<tr><td colspan='4' class='dash-subhead'>Active Conditions: ${statusAdditions.join(' &nbsp;|&nbsp; ')}</td></tr>"
                    }
                }
            }
            
            statusText += "</tbody></table>"
            
            if (hasZones) {
                paragraph statusText
            } else {
                paragraph "<i>No rooms configured yet. Click a room below to begin.</i>"
            }
        }

        section("<b>1. Room Management</b>", hideable: true, hidden: false) {
            paragraph "<div style='font-size:13px; color:#555;'>Click a room below to configure its sensors, smart plugs, timeouts, and rules.</div>"
            for (int i = 1; i <= maxRooms; i++) {
                def zName = settings["z${i}Name"] ?: "Room ${i}"
                def statusTag = settings["enableZ${i}"] ? " <span style='color:green;'>(Active)</span>" : ""
                href(name: "roomPage${i}", page: "roomConfigPage", params: [roomId: i], title: "⚙️ Configure ${zName}${statusTag}", description: "")
            }
        }

        section("<b>2. Individual Room Overrides</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Manually force individual rooms to instantly switch states.</div>"
            def hasActive = false
            for (int i = 1; i <= maxRooms; i++) {
                if (settings["enableZ${i}"]) {
                    hasActive = true
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
                    input "forceEmptyBtn_${i}", "button", title: "⏏️ Force EMPTY: ${zName}"
                    input "forceOccBtn_${i}", "button", title: "⚡ Force FULL OCC: ${zName}"
                    input "forcePartBtn_${i}", "button", title: "💡 Force PARTIAL OCC: ${zName}"
                }
            }
            if (!hasActive) paragraph "<i>No active rooms available.</i>"
        }

        section("<b>3. Global Configuration & Restrictions</b>", hideable: true, hidden: true) {
            input "numRooms", "number", title: "Number of Rooms to Configure (1-12)", defaultValue: 5, range: "1..12", submitOnChange: true
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            paragraph "<b>Global Mode Overrides</b>"
            input "restrictedModes", "mode", title: "Restricted Modes (Pause all occupancy rules)", multiple: true, required: false
            input "forceOffModes", "mode", title: "Force OFF Modes (Immediately turns off all rooms when entering these modes)", multiple: true, required: false
            
            paragraph "<b>Global Guest Mode Overrides</b>"
            input "guestSwitch", "capability.switch", title: "Global Guest Switch (Forces ALL rooms to FULL Occupied)", required: false, submitOnChange: true
            if (settings["guestSwitch"]) {
                input "guestModes", "mode", title: "↳ Allowed Modes for Guest Switch (Leave blank for all modes)", multiple: true, required: false
            }

            paragraph "<b>Global Manual Controls</b>"
            input "forceAllOccupiedBtn", "button", title: "Force All Rooms FULL Occupied"
            input "forceAllEmptyBtn", "button", title: "Force All Rooms Unoccupied"
            input "clearAllStatesBtn", "button", title: "⚠ EMERGENCY: Clear All States & Reset App"
            
            paragraph "<b>Data Management</b>"
            input "forceSync", "bool", title: "Manually Force Hardware Sync (Pushes ON/OFF commands immediately)", defaultValue: false, submitOnChange: true
            if (settings["forceSync"]) {
                if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
                    log.warn "Force Sync aborted: Master Switch is OFF."
                } else {
                    logAction("MANUAL OVERRIDE: Forcing hardware sync...")
                    evaluateRooms(true)
                }
                app.updateSetting("forceSync", false)
            }
        }

        section("<b>4. Action History & Debugging</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging (Auto-disables after 30 min)", defaultValue: false, submitOnChange: true
            
            if (state.actionHistory) {
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${state.actionHistory.join("<br>")}</span>"
            }
        }
    }
}

def roomConfigPage(params) {
    if (params?.roomId) state.editingRoom = params.roomId
    def i = state.editingRoom ?: 1
    def zName = settings["z${i}Name"] ?: "Room ${i}"

    dynamicPage(name: "roomConfigPage", title: "<b>⚙️ Configuration: ${zName}</b>", install: false, uninstall: false) {
        
        section("<b>General Setup</b>") {
            input "enableZ${i}", "bool", title: "<b>Enable this Room</b>", submitOnChange: true
            if (settings["enableZ${i}"]) {
                input "z${i}Name", "text", title: "Room Name", required: false, defaultValue: "Room ${i}", submitOnChange: true
            }
        }
             
        if (settings["enableZ${i}"]) {

            section("<b>1. Triggers & Sensors</b>", hideable: true, hidden: true) {
                input "z${i}OccupancyMode", "enum", title: "Room Occupancy Mode", options: ["Auto-ON / Auto-OFF (Standard)", "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)"], required: true, defaultValue: "Auto-ON / Auto-OFF (Standard)", submitOnChange: true
                
                if (settings["z${i}OccupancyMode"] != "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)") {
                    input "z${i}TurnOnTriggers", "enum", title: "Turn ON Triggers (What makes the room occupied?)", options: ["Virtual Switch", "Motion Hit Count", "Continuous Motion", "Vibration"], multiple: true, required: true, defaultValue: ["Virtual Switch", "Motion Hit Count", "Vibration"], submitOnChange: true
                } else {
                    paragraph "<span style='color:blue;'><b>Manual-ON / Auto-OFF Mode Active:</b> This room will ONLY turn on when the Virtual Override Switch is flipped or an Override Button is pushed. Motion and vibration sensors will only be used to turn the room OFF after the configured timeouts.</span>"
                }

                input "z${i}TurnOffTriggers", "enum", title: "Turn OFF Triggers (What makes the room empty?)", options: ["Motion/Vibe Timeout", "Virtual Switch OFF"], multiple: true, required: true, defaultValue: ["Motion/Vibe Timeout", "Virtual Switch OFF"], submitOnChange: true

                input "z${i}UnoccupiedCooldown", "number", title: "Unoccupied Cooldown (Minutes room must stay empty before it can be automatically occupied again)", required: true, defaultValue: 5
                input "z${i}GoodNightSwitch", "capability.switch", title: "Room Good Night Switch (Locks room as Occupied & ignores Mode changes)", required: false, submitOnChange: true
                
                input "z${i}Motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false, submitOnChange: true
                if (settings["z${i}Motion"]) {
                    input "z${i}MotionTarget", "enum", title: "↳ Motion Trigger Mode", options: ["FULL", "PARTIAL"], required: true, defaultValue: "FULL"
                    input "z${i}MotionGracePeriod", "number", title: "↳ Motion Inactive Grace Period (Seconds before officially confirming empty)", required: true, defaultValue: 15
                    
                    if (settings["z${i}OccupancyMode"] != "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)") {
                        if (settings["z${i}TurnOnTriggers"]?.contains("Motion Hit Count")) {
                            input "z${i}MotionActivationWindow", "number", title: "↳ Hit Count Window (Minutes)", required: true, defaultValue: 3
                            input "z${i}MotionActivationHits", "number", title: "↳ Required Active Hits", required: true, defaultValue: 1
                        }
                        if (settings["z${i}TurnOnTriggers"]?.contains("Continuous Motion")) {
                            input "z${i}MotionContinuousDuration", "number", title: "↳ Continuous Motion Duration (Minutes before turning ON)", required: true, defaultValue: 3
                        }
                    }

                    if (settings["z${i}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
                        input "z${i}Timeout", "number", title: "↳ Motion Empty Timeout (Minutes before turning OFF)", required: true, defaultValue: 15
                    }
                }
                
                input "z${i}Vibration", "capability.accelerationSensor", title: "Vibration Sensors (e.g. Chair/Bed)", multiple: true, required: false, submitOnChange: true
                if (settings["z${i}Vibration"]) {
                    input "z${i}VibeTarget", "enum", title: "↳ Vibration Trigger Mode", options: ["FULL", "PARTIAL"], required: true, defaultValue: "FULL"
                    
                    if (settings["z${i}OccupancyMode"] != "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)") {
                        if (settings["z${i}TurnOnTriggers"]?.contains("Vibration")) {
                            input "z${i}VibeActivationWindow", "number", title: "↳ Activation Window (Minutes to count hits)", required: true, defaultValue: 3
                            input "z${i}VibeActivationHits", "number", title: "↳ Required Active Hits to trigger Occupied", required: true, defaultValue: 1
                        }
                    }

                    if (settings["z${i}TurnOffTriggers"]?.contains("Motion/Vibe Timeout")) {
                        input "z${i}VibeTimeout", "number", title: "↳ Vibration Empty Timeout (Minutes before turning OFF)", required: true, defaultValue: 5
                    }
                }
            }
            
            section("<b>2. Devices & Switches</b>", hideable: true, hidden: true) {
                
                paragraph "<b>Virtual Override Switch</b>"
                input "z${i}OverrideSwitch", "capability.switch", title: "Virtual Override Switch", required: false, submitOnChange: true
                if (settings["z${i}OverrideSwitch"] && (settings["z${i}TurnOnTriggers"]?.contains("Virtual Switch") || settings["z${i}OccupancyMode"] == "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)")) {
                    if (settings["z${i}OccupancyMode"] != "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)") {
                        input "z${i}OverrideTimeout", "number", title: "↳ Auto-Off Timeout (Minutes of NO movement before turning switch OFF)", required: false, defaultValue: 120
                    } else {
                        paragraph "<i>Note: Manual Auto-Off Timeout uses the standard Motion Empty Timeout defined above.</i>"
                    }
                }
                
                paragraph "<hr><b>Physical Toggle Button & Aux Controls</b>"
                input "z${i}OverrideButton", "capability.pushableButton", title: "Physical Remote / Button Controller", required: false, submitOnChange: true
                if (settings["z${i}OverrideButton"]) {
                    input "z${i}OverrideButtonNum", "number", title: "↳ FULL Occupancy Button Number", required: true, defaultValue: 1
                    input "z${i}OverrideButtonAction", "enum", title: "↳ FULL Occupancy Button Action", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "pushed"
                    input "z${i}PartialOverrideButtonAction", "enum", title: "↳ PARTIAL Occupancy Button Action", options: ["pushed", "held", "doubleTapped", "released"], required: true, defaultValue: "doubleTapped"
                    
                    paragraph "<b>Auxiliary Machine / Device Controls (Mapped to the same Physical Remote)</b>"
                    
                    for (int a = 1; a <= 6; a++) {
                        input "z${i}Aux${a}Device", "capability.switch", title: "↳ Aux Device Group ${a}", multiple: true, required: false, submitOnChange: true
                        if (settings["z${i}Aux${a}Device"]) {
                            input "z${i}Aux${a}BtnNum", "number", title: "   ↳ Button Number", required: false
                            input "z${i}Aux${a}BtnAction", "enum", title: "   ↳ Trigger Action", options: ["pushed", "held", "doubleTapped", "released"], required: false, defaultValue: "pushed"
                        }
                    }
                    
                    def hasAux = (1..6).any { settings["z${i}Aux${it}Device"] }
                    if (hasAux) {
                        input "z${i}AuxTimeout", "number", title: "↳ Auto-Off Timeout for Aux Devices (Minutes of NO motion)", required: false, defaultValue: 60
                    }
                }
                
                paragraph "<hr><b>FULL Occupancy Devices</b>"
                input "z${i}Switches", "capability.switch", title: "Hard Power Relays (Smart Plugs, Wall Switches)", multiple: true, required: false, submitOnChange: true
                input "z${i}SoftKillDevices", "capability.switch", title: "Network Devices (PCs, TVs, APIs) for Graceful Shutdown", multiple: true, required: false, submitOnChange: true
                
                paragraph "<hr><b>PARTIAL Occupancy Devices</b> (Triggered via Double Tap)"
                input "z${i}PartialSwitches", "capability.switch", title: "Partial Mode Hard Power Relays", multiple: true, required: false, submitOnChange: true
                input "z${i}PartialSoftKillDevices", "capability.switch", title: "Partial Mode Network Devices", multiple: true, required: false, submitOnChange: true
            }

            section("<b>3. Advanced Device Control & Sweepers</b>", hideable: true, hidden: true) {
                def allSoftDevs = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                if (allSoftDevs) {
                    paragraph "<b>Two-Stage Graceful Shutdown</b>"
                    input "z${i}HardKillDelay", "number", title: "Delay before cutting Hard Power (Seconds)", defaultValue: 60, required: true, description: "Provides time for operating systems to shut down and screens to run pixel refresh."
                    paragraph "<hr>"
                }
                
                paragraph "<b>Power Failsafe Override</b>"
                input "z${i}PowerMonitor", "capability.powerMeter", title: "Failsafe Power Monitor (Prevents shutdown if device is active)", required: false, submitOnChange: true
                if (settings["z${i}PowerMonitor"]) {
                    input "z${i}ActiveWattageThreshold", "decimal", title: "↳ Protection Threshold (Watts)", required: true, defaultValue: 15.0, description: "If the monitor reads above this wattage, the room will ignore empty timeouts and stay ON."
                }

                paragraph "<hr><b>Manual Mode / Unoccupied Sweeper</b>"
                input "z${i}Sweeper", "bool", title: "Enable Unoccupied Sweeper (Turns off stranded devices left ON in empty rooms)", defaultValue: false, submitOnChange: true
                if (settings["z${i}Sweeper"]) {
                    input "z${i}SweeperTimeout", "number", title: "↳ Sweeper Timeout (Minutes of NO motion before turning OFF)", required: true, defaultValue: 60
                }
                 
                input "z${i}AbsoluteSweeper", "bool", title: "Enable Absolute Sweeper (Works even when room rules are PAUSED/Restricted)", defaultValue: false, submitOnChange: true
                if (settings["z${i}AbsoluteSweeper"]) {
                    input "z${i}AbsoluteSweeperTimeout", "number", title: "↳ Absolute Sweeper Timeout (Minutes of NO motion/vibe/presence before turning OFF)", required: true, defaultValue: 120
                }
                
                paragraph "<hr><b>Rogue Device Strict Enforcement</b>"
                input "z${i}StrictEnforcement", "bool", title: "Enable Rogue Device Protection (Force devices OFF after 5 mins if turned on physically, forcing the use of authorized app buttons)", defaultValue: false, submitOnChange: true
            }

            section("<b>4. Audio & Speaker Integration</b>", hideable: true, hidden: true) {
                input "z${i}Sonos", "capability.musicPlayer", title: "Room Speaker (Sonos/Media Player)", required: false, submitOnChange: true
                if (settings["z${i}Sonos"]) {
                    input "z${i}AudioMode", "enum", title: "↳ Audio Source", options: ["Track URI", "Favorite Virtual Switch"], required: true, defaultValue: "Track URI", submitOnChange: true
                    
                    if (settings["z${i}AudioMode"] == "Favorite Virtual Switch") {
                        input "z${i}FullFavSwitch", "capability.switch", title: "↳ FULL Occupancy Favorite Switch", required: false
                        input "z${i}PartialFavSwitch", "capability.switch", title: "↳ PARTIAL Occupancy Favorite Switch", required: false
                    } else {
                        input "z${i}FullTrackURI", "text", title: "↳ FULL Occupancy Track URI", required: false
                        input "z${i}PartialTrackURI", "text", title: "↳ PARTIAL Occupancy Track URI", required: false
                    }
                    
                    input "z${i}TargetVolume", "number", title: "↳ Target Volume (1-100)", required: true, defaultValue: 30
                    input "z${i}FadeInTime", "number", title: "↳ Volume Fade-In Duration (Seconds)", required: true, defaultValue: 5
                }
            }
            
            section("<b>5. Room Scheduling & Mode Restrictions</b>", hideable: true, hidden: true) {
                input "z${i}OperatingModes", "mode", title: "Active Modes (Leave blank for all modes)", multiple: true, required: false
                input "z${i}ActiveDays", "enum", title: "Active Days (Leave blank for all days)", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                input "z${i}StartTime", "time", title: "Active Start Time (Optional)", required: false
                input "z${i}EndTime", "time", title: "Active End Time (Optional)", required: false
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def getAllAuxDevsForRoom(roomId) {
    def devs = []
    for (int a = 1; a <= 6; a++) {
        def auxGroup = settings["z${roomId}Aux${a}Device"]
        if (auxGroup) devs += auxGroup
    }
    return devs.findAll { it != null }.unique { it.id }
}

def installed() { logInfo("Installed"); initialize() }
def updated() { 
    logInfo("Updated"); 
    unsubscribe(); 
    unschedule(); 
    initialize();
    if (debugEnable) {
        log.info "Debug logging enabled for 30 minutes."
        runIn(1800, "disableDebugLog")
    }
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    
    for (int i = 1; i <= maxRooms; i++) {
        if (!state."currentRoomStates_z${i}") state."currentRoomStates_z${i}" = "unknown"
        if (!state."dailyStats_z${i}") state."dailyStats_z${i}" = [:]
        
        // Wipe old flags
        atomicState.remove("cancelPush_z${i}")
        atomicState."lastButtonEvent_z${i}" = 0
        atomicState."lastPartialTap_z${i}" = 0
        atomicState."lastFullTap_z${i}" = 0
        
        if (!state."roomOccupiedSince_z${i}" && (state."currentRoomStates_z${i}" == "occupied" || state."currentRoomStates_z${i}" == "partial")) {
            state."roomOccupiedSince_z${i}" = now()
        }
        
        state."lastStatUpdate_z${i}" = now()
        state."motionHitCount_z${i}" = 0
        state."vibeHitCount_z${i}" = 0
        
        state.remove("occStats_z${i}")
        state.remove("roomStatsOccupiedSince_z${i}")
        state.remove("roomStatsUnoccupiedSince_z${i}")
        state.remove("roomStatsTotalSecs_z${i}")
    }
    
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", hubRestartHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", masterSwitchHandler)
    if (guestSwitch) subscribe(guestSwitch, "switch", guestSwitchHandler)
    
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}OverrideSwitch"]) subscribe(settings["z${i}OverrideSwitch"], "switch", sensorHandler)
            if (settings["z${i}GoodNightSwitch"]) subscribe(settings["z${i}GoodNightSwitch"], "switch", sensorHandler)
            if (settings["z${i}Motion"]) subscribe(settings["z${i}Motion"], "motion", motionHandler)
            if (settings["z${i}Vibration"]) subscribe(settings["z${i}Vibration"], "acceleration", vibrationHandler)
            
            if (settings["z${i}OverrideButton"]) {
                def fullAction = settings["z${i}OverrideButtonAction"] ?: "pushed"
                def partialAction = settings["z${i}PartialOverrideButtonAction"] ?: "doubleTapped"
                
                def actions = [fullAction, partialAction]
                for (int a = 1; a <= 6; a++) {
                    if (settings["z${i}Aux${a}Device"] && settings["z${i}Aux${a}BtnAction"]) {
                        actions << settings["z${i}Aux${a}BtnAction"]
                    }
                }
                
                actions.unique().each { act ->
                    subscribe(settings["z${i}OverrideButton"], act, buttonHandler)
                }
            }
            
            if (settings["z${i}Sweeper"] || settings["z${i}AbsoluteSweeper"] || settings["z${i}StrictEnforcement"]) {
                def allHard = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                allHard = allHard.unique { it.id }
                if (allHard) subscribe(allHard, "switch", physicalSwitchHandler)
                
                def allSoft = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                allSoft = allSoft.unique { it.id }
                if (allSoft) subscribe(allSoft, "switch", physicalSwitchHandler)
            }

            // Always subscribe to Aux devices so physically interacting with them resets the global room clock
            def allAux = getAllAuxDevsForRoom(i)
            if (allAux) subscribe(allAux, "switch", physicalSwitchHandler)
        }
    }
    
    runEvery5Minutes("evaluateRooms")
    
    logAction("Advanced Room Occupancy Initialized (Event-Driven Mode Active).")
    evaluateRooms(false)
}

def isMasterEnabled() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def masterSwitchHandler(evt) {
    logAction("Master Enable Switch turned ${evt.value}.")
    if (evt.value == "on") {
        evaluateRooms(true)
    } else {
        unschedule() 
    }
}

def guestSwitchHandler(evt) {
    if (!isMasterEnabled()) return
    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    
    if (evt.value == "on") {
        def allowedModes = settings["guestModes"]
        if (allowedModes && !(allowedModes as List).contains(location.mode)) {
            logAction("Global Guest Switch turned ON, but ignored (Mode '${location.mode}' is not in allowed modes).")
            return
        }
        
        logAction("GLOBAL GUEST SWITCH ACTIVATED: Forcing ALL configured rooms to FULL OCCUPIED.")
        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableZ${i}"]) {
                forceRoomState(i, "occupied")
            }
        }
        evaluateRooms(true)
    } else {
        logAction("Global Guest Switch turned OFF. Rooms will now resume normal timeout rules.")
        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableZ${i}"]) {
                 state."switchIsManual_z${i}" = false 
            }
        }
        evaluateRooms(true)
    }
}

// === CENTRALIZED MANUAL STATE FORCERS ===
def forceRoomEmpty(i) {
    def oSwitch = settings["z${i}OverrideSwitch"]
    if (oSwitch) {
        state."expectedSwitchBehavior_z${i}" = "manual_off"
        state."expectedSwitchBehaviorTime_z${i}" = now()
        safeOff(oSwitch)
    }
    
    state."switchIsManual_z${i}" = false
    state."isPartial_z${i}" = false
    state."waitForClear_z${i}" = true
    state."unoccupiedLockoutTime_z${i}" = now()

    state."motionHitCount_z${i}" = 0
    state."vibeHitCount_z${i}" = 0
    state."motionHitStartTime_z${i}" = null
    state."vibeHitStartTime_z${i}" = null
    state."zoneLastActive_z${i}" = null
    state."vibeLastActive_z${i}" = null
    state."motionActiveSince_z${i}" = null
    state."roomOccupiedSince_z${i}" = null
    
    // Set state to empty BEFORE initiating shutdown to pass safety checks
    state."currentRoomStates_z${i}" = "empty"
    initiateRoomShutdown(i)
    turnOffAuxDevices(i)
    handleSpeakerPause(i)
}

def forceRoomState(i, targetState) {
    def oSwitch = settings["z${i}OverrideSwitch"]
    
    state."switchIsManual_z${i}" = true
    state."isPartial_z${i}" = (targetState == "partial")
    state."zoneLastActive_z${i}" = now()
    state."unoccupiedLockoutTime_z${i}" = null
    state."roomOccupiedSince_z${i}" = now()
    
    if (targetState == "partial") {
        turnRoomPartialDevicesOn(i)
        turnOffNonPartialDevices(i) 
        
        if (oSwitch) {
            state."expectedSwitchBehavior_z${i}" = "manual_on"
            state."expectedSwitchBehaviorTime_z${i}" = now()
            safeOn(oSwitch)
        }
    } else {
        if (oSwitch) {
            state."expectedSwitchBehavior_z${i}" = "manual_on"
            state."expectedSwitchBehaviorTime_z${i}" = now()
            safeOn(oSwitch)
        }
        turnRoomDevicesOn(i)
    }
    
    state."currentRoomStates_z${i}" = targetState
    handleSpeakerPlayback(i, targetState)
}

def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logDebug("Dashboard manually refreshed by user.")
        return
    }

    if (btn == "clearAllStatesBtn") {
        log.warn "EMERGENCY RESET: Purging all application states, trackers, and timers."
        state.clear()
        atomicState.clear()
        unschedule()
        initialize()
        return
    } 
    
    if (!isMasterEnabled()) {
        log.warn "Master Switch is OFF. Manual app overrides are blocked."
        return
    }
    
    if (btn.startsWith("forceEmptyBtn_")) {
        def i = btn.split("_")[1].toInteger()
        def zName = settings["z${i}Name"] ?: "Room ${i}"
        logAction("MANUAL OVERRIDE: Forcing ${zName} to EMPTY.")
        forceRoomEmpty(i)
        evaluateRooms(true)
        return
    }
 
    if (btn.startsWith("forceOccBtn_")) {
        def i = btn.split("_")[1].toInteger()
        def zName = settings["z${i}Name"] ?: "Room ${i}"
        logAction("MANUAL OVERRIDE: Forcing ${zName} to FULL OCCUPIED.")
        forceRoomState(i, "occupied")
        evaluateRooms(true)
        return
    }
    
    if (btn.startsWith("forcePartBtn_")) {
        def i = btn.split("_")[1].toInteger()
        def zName = settings["z${i}Name"] ?: "Room ${i}"
        logAction("MANUAL OVERRIDE: Forcing ${zName} to PARTIAL OCCUPIED.")
        forceRoomState(i, "partial")
        evaluateRooms(true)
        return
    }

    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    if (btn == "forceAllOccupiedBtn") {
        logAction("GLOBAL MANUAL OVERRIDE: Forcing ALL configured rooms to FULL OCCUPIED.")
        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableZ${i}"]) {
                forceRoomState(i, "occupied")
            }
        }
        evaluateRooms(true)
    } else if (btn == "forceAllEmptyBtn") {
        logAction("GLOBAL MANUAL OVERRIDE: Forcing ALL configured rooms to EMPTY.")
        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableZ${i}"]) {
                forceRoomEmpty(i)
            }
        }
        evaluateRooms(true)
    }
}

// === ENHANCED RECOVERY LOGIC ===
def hubRestartHandler(evt) {
    if (!isMasterEnabled()) return
    logAction("SYSTEM REBOOT / POWER OUTAGE DETECTED: Recovering previous room states and forcing hardware sync...")
    
    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    for (int i = 1; i <= maxRooms; i++) { 
        state."shutdownDelayActive_z${i}" = false 
        
        if (settings["enableZ${i}"]) {
            def lastKnownState = state."currentRoomStates_z${i}"
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            
            if (lastKnownState == "occupied") {
                 logAction("RECOVERY: ${zName} was FULL OCCUPIED before outage. Restoring ON state.")
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch && state."switchIsManual_z${i}") safeOn(oSwitch)
                turnRoomDevicesOn(i)
            } else if (lastKnownState == "partial") {
                 logAction("RECOVERY: ${zName} was PARTIAL OCCUPIED before outage. Restoring PARTIAL ON state.")
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch && state."switchIsManual_z${i}") safeOn(oSwitch)
                turnRoomPartialDevicesOn(i)
            } else if (lastKnownState == "empty") {
                logAction("RECOVERY: ${zName} was EMPTY before outage. Restoring OFF state.")
                forceRoomEmpty(i)
            }
        }
    }
    
    logAction("Pausing live sensor evaluations for 2 minutes to allow Zigbee/Z-Wave meshes to stabilize...")
    unschedule("evaluateRooms")
    runIn(120, "resumeNormalOperations")
}

def resumeNormalOperations() {
    logAction("Mesh stabilization complete. Resuming standard occupancy evaluations.")
    runEvery5Minutes("evaluateRooms")
    evaluateRooms(true)
}

def modeChangeHandler(evt) {
    if (!isMasterEnabled()) return
    logAction("Location mode changed to: ${evt.value}")
    
    def isForceOff = forceOffModes && (forceOffModes as List).contains(evt.value)
    if (isForceOff) {
        logAction("GLOBAL FORCE OFF TRIGGERED: Mode changed to ${evt.value}.")
        def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableZ${i}"] && state."currentRoomStates_z${i}" != "empty") {
                def gnSwitch = settings["z${i}GoodNightSwitch"]
                if (!(gnSwitch && gnSwitch.currentValue("switch") == "on")) {
                    forceRoomEmpty(i)
                }
            }
        }
    }
    
    evaluateRooms(false)
}

def buttonHandler(evt) {
    if (!isMasterEnabled()) return
    def btnVal = evt.value ? evt.value.toInteger() : 1
    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableZ${i}"] && settings["z${i}OverrideButton"]?.id == evt.device.id) {
            def zName = settings["z${i}Name"] ?: "Room ${i}"
            def restriction = getRoomRestrictionReason(i)
            
            if (restriction) {
                logAction("${zName}: Override Button pressed, but ignored. Room (and Aux) is currently PAUSED due to: ${restriction}")
                return
            }

            def targetBtn = (settings["z${i}OverrideButtonNum"] ?: 1).toInteger()
            def fullAction = settings["z${i}OverrideButtonAction"] ?: "pushed"
            def partialAction = settings["z${i}PartialOverrideButtonAction"] ?: "doubleTapped"
            
            def isAuxHandled = false
            
            for (int a = 1; a <= 6; a++) {
                if (settings["z${i}Aux${a}Device"] && btnVal == (settings["z${i}Aux${a}BtnNum"] ?: -1).toInteger() && evt.name == settings["z${i}Aux${a}BtnAction"]) {
                    toggleAuxDevices(i, a)
                    atomicState."lastButtonEvent_z${i}" = now()
                    isAuxHandled = true
                }
            }
            
            if (!isAuxHandled && btnVal == targetBtn) {
                // Record the physical button press timestamp to lock out rogue switch relays
                atomicState."lastButtonEvent_z${i}" = now()
                
                def currentState = state."currentRoomStates_z${i}"

                if (evt.name == partialAction) {
                    // Update timestamp FIRST so it's ready for any simultaneous threads
                    atomicState."lastPartialTap_z${i}" = now()
                    
                    if (currentState == "occupied") {
                        logAction("${zName}: Override Button PARTIAL Action. Stepping down from FULL to PARTIAL Occupancy.")
                        forceRoomState(i, "partial")
                        runIn(1, "evalR${i}")
                    } else if (currentState == "partial") {
                        logAction("${zName}: Override Button PARTIAL Action (Toggle OFF). Forcing EMPTY.")
                        forceRoomEmpty(i)
                        runIn(1, "evalR${i}")
                    } else {
                        logAction("${zName}: Override Button PARTIAL Action. Forcing PARTIAL Occupancy.")
                        forceRoomState(i, "partial")
                        runIn(1, "evalR${i}")
                    }
                } 
                else if (evt.name == fullAction) {
                    // Update timestamp FIRST so the delayed execution can properly compare it
                    atomicState."lastFullTap_z${i}" = now()
                    
                    logDebug("${zName}: Button ${fullAction} detected. Scheduling FULL execution check in 1500ms...")
                    runInMillis(1500, "delayedFullActionR${i}")
                }
            }
        }
    }
}

def physicalSwitchHandler(evt) {
    if (!isMasterEnabled()) return
    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    
    if (evt.value == "on") {
        def isAppCommanded = false
        def cmdTime = state."appCommandedOn_${evt.device.id}" ?: 0
        if (now() - cmdTime < 10000) { // 10 second grace period for network delays
            isAppCommanded = true
        }

        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableZ${i}"]) {
                def allHard = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                def allSoft = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                def allAux = getAllAuxDevsForRoom(i)
                
                if ((allHard.find { it.id == evt.device.id }) || (allSoft.find { it.id == evt.device.id }) || (allAux.find { it.id == evt.device.id })) {
                    if (isAppCommanded) {
                        state."zoneLastActive_z${i}" = now()
                    } else {
                        def zName = settings["z${i}Name"] ?: "Room ${i}"
                        logDebug("External/Physical turn on detected for ${evt.device.displayName} in ${zName}")
                        
                        if (settings["z${i}StrictEnforcement"]) {
                            logAction("🚨 Strict Enforcement: ${evt.device.displayName} in ${zName} was turned on externally. Scheduling forced shutdown in 5 minutes.")
                            runIn(300, "forceRogueDeviceOff", [data: [deviceId: evt.device.id, roomId: i], overwrite: false])
                        } else {
                            state."zoneLastActive_z${i}" = now()
                        }
                    }
                }
            }
        }
    }
}

def forceRogueDeviceOff(data) {
    def devId = data.deviceId
    def roomId = data.roomId
    if (!devId || !roomId) return

    def allDevs = (settings["z${roomId}Switches"] ?: []) +
                  (settings["z${roomId}PartialSwitches"] ?: []) +
                  (settings["z${roomId}SoftKillDevices"] ?: []) +
                  (settings["z${roomId}PartialSoftKillDevices"] ?: []) +
                  getAllAuxDevsForRoom(roomId)

    def dev = allDevs.find { it.id == devId }
    if (dev && dev.currentValue("switch") == "on") {
        logAction("🚨 Strict Enforcement: 5 minutes elapsed. Forcing rogue device ${dev.displayName} OFF.")
        try { dev.off() } catch(e){}
    }
}

def motionHandler(evt) {
    if (!isMasterEnabled()) return
    def isActive = (evt.value == "active")
    logDebug("Motion event received from ${evt.device.displayName}: ${evt.value}")
    
    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableZ${i}"]) {
            def mDevs = settings["z${i}Motion"]
            if (mDevs && mDevs.find { it.id == evt.device.id }) {
                
                if (isActive) {
                    unschedule("verifyInactiveR${i}") 
                    state."zoneLastActive_z${i}" = now()
                    
                    if (!state."motionActiveSince_z${i}") {
                        state."motionActiveSince_z${i}" = now()
                        def contMins = settings["z${i}MotionContinuousDuration"] ?: 3
                        runIn(contMins * 60, "evalR${i}")
                    }
                 
                    def count = (state."motionHitCount_z${i}" ?: 0) + 1
                    state."motionHitCount_z${i}" = count
                    
                    def windowMs = (settings["z${i}MotionActivationWindow"] ?: 3) * 60000
                    if (count == 1 && windowMs > 0) {
                        state."motionHitStartTime_z${i}" = now()
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetMotionZ${i}")
                    }
          
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}") 
                } else {
                    state."zoneLastActive_z${i}" = now()

                    if (!mDevs.any { it.currentValue("motion") == "active" }) {
                        def graceSecs = settings["z${i}MotionGracePeriod"] != null ? settings["z${i}MotionGracePeriod"].toInteger() : 15
                        if (graceSecs > 0) {
                            runIn(graceSecs, "verifyInactiveR${i}")
                        } else {
                            processMotionInactive(i)
                        }
                    }
                }
            }
        }
    }
}

def processMotionInactive(roomId) {
    if (!isMasterEnabled()) return
    def mDevs = settings["z${roomId}Motion"]
    if (mDevs && !mDevs.any { it.currentValue("motion") == "active" }) {
        if (state."motionActiveSince_z${roomId}") {
            state."motionActiveSince_z${roomId}" = null
        }

        state."zoneLastActive_z${roomId}" = now()
  
        if (settings["z${roomId}TurnOffTriggers"]?.contains("Motion/Vibe Timeout") || settings["z${roomId}OccupancyMode"] == "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)") {
            def timeoutSecs = (settings["z${roomId}Timeout"] ?: 15) * 60
            runIn(timeoutSecs, "evalR${roomId}")
        } else {
            runIn(1, "evalR${roomId}")
        }
    }
}

def vibrationHandler(evt) {
    if (!isMasterEnabled()) return
    def isActive = (evt.value == "active")
    logDebug("Vibration event received from ${evt.device.displayName}: ${evt.value}")
    
    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableZ${i}"]) {
            def vDevs = settings["z${i}Vibration"]
            if (vDevs && vDevs.find { it.id == evt.device.id }) {
                
                if (isActive) {
                    state."vibeLastActive_z${i}" = now()
                    
                    def count = (state."vibeHitCount_z${i}" ?: 0) + 1
                    state."vibeHitCount_z${i}" = count
         
                    def windowMs = (settings["z${i}VibeActivationWindow"] ?: 3) * 60000
                    if (count == 1 && windowMs > 0) {
                        state."vibeHitStartTime_z${i}" = now() 
                        def windowSecs = (windowMs / 1000).toInteger()
                        runIn(windowSecs, "resetVibeZ${i}")
                    }
                    
                    unschedule("evalR${i}")
                    runIn(1, "evalR${i}")
               
                } else {
                    state."vibeLastActive_z${i}" = now() 

                    if (!vDevs.any { it.currentValue("acceleration") == "active" }) {
                        if (settings["z${i}TurnOffTriggers"]?.contains("Motion/Vibe Timeout") || settings["z${i}OccupancyMode"] == "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)") {
                            def timeoutSecs = (settings["z${i}VibeTimeout"] ?: 5) * 60
                            runIn(timeoutSecs, "evalR${i}")
                        } else {
                            runIn(1, "evalR${i}")
                        }
                    }
                }
            }
        }
    }
}

def sensorHandler(evt) {
    if (!isMasterEnabled()) return
  
    if (evt.name == "switch") {
        def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
        for (int i = 1; i <= maxRooms; i++) {
            if (settings["enableZ${i}"]) {
                // HARDWARE RACE CONDITION INTERCEPTOR
                if (settings["z${i}OverrideSwitch"]?.id == evt.device.id) {
                    // Send this switch evaluation to a 1-second holding cell.
                    // This allows us to definitively check if it was paired with a physical button press.
                    runIn(1, "processVirtualSwitchEvent", [data: [room: i, value: evt.value]])
                }
                
                if (settings["z${i}GoodNightSwitch"]?.id == evt.device.id && evt.value == "on") {
                    logDebug("${settings["z${i}Name"]}: Good Night Switch activated. App will evaluate target state...")
                }
            }
        }
    }
    runIn(1, "evalR_All")
}

def processVirtualSwitchEvent(data) {
    def i = data.room
    def val = data.value
    def zName = settings["z${i}Name"] ?: "Room ${i}"

    // Check if a physical button double/single tap occurred at the exact same time
    def lastBtn = atomicState."lastButtonEvent_z${i}" ?: 0
    if (now() - lastBtn < 3000) {
        logDebug("${zName}: Switch Relay event IGNORED. A physical button tap took priority.")
        return
    }

    def expected = state."expectedSwitchBehavior_z${i}"
    def expectedTime = state."expectedSwitchBehaviorTime_z${i}" ?: 0
    def withinWindow = (now() - expectedTime) < 5000 
    def isExpectedEcho = false
    
    if (withinWindow) {
        if ((expected == "auto" && val == "off") || (expected == "manual_off" && val == "off")) {
            isExpectedEcho = true
        } else if ((expected == "auto" && val == "on") || (expected == "manual_on" && val == "on")) {
            isExpectedEcho = true
        }
    }
    
    if (isExpectedEcho) {
        if (expected == "auto") {
            state."switchIsManual_z${i}" = false
        } else if (expected == "manual_on") {
            state."switchIsManual_z${i}" = true
        } else if (expected == "manual_off") {
            state."switchIsManual_z${i}" = false
        }
    } else {
        if (val == "on") {
            if (state."currentRoomStates_z${i}" == "occupied" || state."currentRoomStates_z${i}" == "partial") {
                logDebug("${zName}: Ignored Virtual Switch ON event. Room is already active and handling its own state.")
            } else {
                logAction("${zName}: Virtual Switch manually turned ON. Forcing FULL Occupancy.")
                forceRoomState(i, "occupied")
            }
        } else {
            logAction("${zName}: Virtual Switch manually turned OFF externally.")
            forceRoomEmpty(i)
        }
    }
    runIn(1, "evalR${i}")
}

def getRoomOccupancyState(roomId) {
    def mDevs = settings["z${roomId}Motion"]
    def vDevs = settings["z${roomId}Vibration"]
    def oSwitch = settings["z${roomId}OverrideSwitch"]
    def gnSwitch = settings["z${roomId}GoodNightSwitch"]
    def pMonitor = settings["z${roomId}PowerMonitor"]
    
    def occMode = settings["z${roomId}OccupancyMode"]
    def isManualOnMode = (occMode == "Manual-ON / Auto-OFF (Motion/Vibe only turns OFF)")
    
    if (mDevs && !mDevs.any { it.currentValue("motion") == "active" }) {
        if (state."motionActiveSince_z${roomId}") {
            state."motionActiveSince_z${roomId}" = null
        }
    }

    if (state."waitForClear_z${roomId}") {
        def isClear = true
        
        if (mDevs && mDevs.any { it.currentValue("motion") == "active" }) isClear = false
        if (vDevs && vDevs.any { it.currentValue("acceleration") == "active" }) isClear = false
        
        if (oSwitch && oSwitch.currentValue("switch") == "on") {
            def expected = state."expectedSwitchBehavior_z${roomId}"
            def expectedTime = state."expectedSwitchBehaviorTime_z${roomId}" ?: 0
            if (!(expected == "manual_off" && (now() - expectedTime) < 5000)) {
                state."waitForClear_z${roomId}" = null
                isClear = true
            }
        }

        if (!isClear) {
            return false 
        } else {
            state."waitForClear_z${roomId}" = null
        }
    }

    def isOccupied = false
    
    def cooldownMins = settings["z${roomId}UnoccupiedCooldown"] != null ? settings["z${roomId}UnoccupiedCooldown"].toInteger() : 5
    if (cooldownMins > 0 && state."currentRoomStates_z${roomId}" == "empty") {
        def lockedTime = state."unoccupiedLockoutTime_z${roomId}" ?: 0
        if (lockedTime > 0 && (now() - lockedTime) < (cooldownMins * 60000)) {
            if (pMonitor) {
                def currentDraw = pMonitor.currentValue("power") ?: 0.0
                def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
                if (currentDraw > safeThresh) return true
            }
            if (gnSwitch && gnSwitch.currentValue("switch") == "on") return true
            
            if (oSwitch && oSwitch.currentValue("switch") == "on") {
                def expected = state."expectedSwitchBehavior_z${roomId}"
                def expectedTime = state."expectedSwitchBehaviorTime_z${roomId}" ?: 0
                if (!(expected == "manual_off" && (now() - expectedTime) < 5000)) {
                    return true
                }
            }
            
            return false 
        }
    }

    if (pMonitor) {
        def currentDraw = pMonitor.currentValue("power") ?: 0.0
        def safeThresh = settings["z${roomId}ActiveWattageThreshold"] ?: 15.0
  
        if (currentDraw > safeThresh) {
            state."zoneLastActive_z${roomId}" = now() 
            return true
        }
    }
    
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        return true
    }
    
    def isHardActive = false
    def maxStuckTime = 6 * 3600000 
    
    def graceSecs = settings["z${roomId}MotionGracePeriod"] != null ? settings["z${roomId}MotionGracePeriod"].toInteger() : 15
    def graceMs = graceSecs * 1000
    
    if (mDevs) {
        mDevs.each { dev ->
            if (dev.currentValue("motion") == "active") {
                try {
                    def lastEvent = dev.events(max: 1)
                    def lastEventTime = (lastEvent && lastEvent.size() > 0) ? lastEvent[0].date.time : now()
                    if ((now() - lastEventTime) > maxStuckTime) {
                        log.warn "⚠️ DEADMAN SWITCH: Motion sensor ${dev.displayName} in Room ${roomId} has been stuck ACTIVE for over 6 hours. Ignoring."
                    } else {
                        isHardActive = true
                    }
                } catch (e) { isHardActive = true }
            }
        }
    }
    
    if (!isHardActive && state."zoneLastActive_z${roomId}") {
        if ((now() - state."zoneLastActive_z${roomId}") <= graceMs) isHardActive = true
    }
    
    if (vDevs && vDevs.any { it.currentValue("acceleration") == "active" }) isHardActive = true

    def wasAlreadyOccupied = (state."currentRoomStates_z${roomId}" == "occupied" || state."currentRoomStates_z${roomId}" == "partial")
    def onTriggers = settings["z${roomId}TurnOnTriggers"] ?: ["Virtual Switch", "Motion Hit Count", "Continuous Motion", "Vibration"]
    def offTriggers = settings["z${roomId}TurnOffTriggers"] ?: ["Motion/Vibe Timeout", "Virtual Switch OFF"]

    // FIRM MANUAL HOLD: Decoupled from Virtual Switch requirement.
    if (!isOccupied && state."switchIsManual_z${roomId}") {
        def oTimeout = isManualOnMode ? (settings["z${roomId}Timeout"] ?: 15) : (settings["z${roomId}OverrideTimeout"] ?: 120)
        
        if (oTimeout > 0) {
            if (!isHardActive) {
                def maxLast = Math.max(state."zoneLastActive_z${roomId}" ?: 0, state."vibeLastActive_z${roomId}" ?: 0)
                if (maxLast == 0) maxLast = now()
                
                if ((now() - maxLast) > (oTimeout * 60000)) {
                    logAction("${settings["z${roomId}Name"]}: Manual/Auto-Off Override timed out due to inactivity.")
                    if (oSwitch) safeOff(oSwitch)
                    state."switchIsManual_z${roomId}" = false
                } else {
                    isOccupied = true
                }
            } else {
                isOccupied = true 
            }
        } else {
            isOccupied = true 
        }
    }

    if (!isManualOnMode) {
        if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Continuous Motion") && mDevs) {
            def activeSince = state."motionActiveSince_z${roomId}"
            def reqMins = settings["z${roomId}MotionContinuousDuration"] ?: 3
            if (activeSince && (now() - activeSince) >= (reqMins * 60000)) {
                isOccupied = true
            }
        }

        if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Motion Hit Count") && mDevs) {
            def reqHits = (settings["z${roomId}MotionActivationHits"] ?: 1).toInteger()
            def hitCount = state."motionHitCount_z${roomId}" ?: 0
        
            if (hitCount >= reqHits) {
                isOccupied = true
            }
        }

        if (!isOccupied && !wasAlreadyOccupied && onTriggers.contains("Vibration") && vDevs) {
            def reqHits = (settings["z${roomId}VibeActivationHits"] ?: 1).toInteger()
            def hitCount = state."vibeHitCount_z${roomId}" ?: 0
            
            if (hitCount >= reqHits) {
                isOccupied = true
            }
        }
    }

    if (wasAlreadyOccupied && !isOccupied) {
        def holdOccupied = false
        
        // Secondary catch for normal sensor timeouts
        if (offTriggers.contains("Motion/Vibe Timeout") || isManualOnMode) {
            def mTimeoutMs = (settings["z${roomId}Timeout"] ?: 15) * 60000
            def vTimeoutMs = (settings["z${roomId}VibeTimeout"] ?: 5) * 60000
            def mLastActive = state."zoneLastActive_z${roomId}" ?: 0
            def vLastActive = state."vibeLastActive_z${roomId}" ?: 0
            
            if (mDevs && (mDevs.any { it.currentValue("motion") == "active" } || (mLastActive && (now() - mLastActive) < mTimeoutMs))) holdOccupied = true
            if (vDevs && (vDevs.any { it.currentValue("acceleration") == "active" } || (vLastActive && (now() - vLastActive) < vTimeoutMs))) holdOccupied = true
        }
        
        if (offTriggers.contains("Virtual Switch OFF")) {
            if (oSwitch && oSwitch.currentValue("switch") == "on" && state."switchIsManual_z${roomId}") {
                holdOccupied = true
            }
        }
        
        if (holdOccupied) {
            isOccupied = true
        }
    }
    
    return isOccupied
}

def getRoomRestrictionReason(roomId) {
    def gnSwitch = settings["z${roomId}GoodNightSwitch"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        return null 
    }

    def currentMode = location.mode
    
    if (forceOffModes && (forceOffModes as List).contains(currentMode)) {
        return "Force Off Mode"
    }
    
    if (restrictedModes && (restrictedModes as List).contains(currentMode)) {
        return "Global Mode"
    }
    
    def roomModes = settings["z${roomId}OperatingModes"]
    if (roomModes && !(roomModes as List).contains(currentMode)) {
        return "Room Mode"
    }
    
    def activeDays = settings["z${roomId}ActiveDays"]
    if (activeDays) {
        def df = new java.text.SimpleDateFormat("EEEE")
        df.setTimeZone(location.timeZone)
        def day = df.format(new Date())
        if (!activeDays.contains(day)) return "Day"
    }
    
    def startTime = settings["z${roomId}StartTime"]
    def endTime = settings["z${roomId}EndTime"]
    if (startTime && endTime) {
        def currTime = now()
        def start = timeToday(startTime, location.timeZone).time
        def end = timeToday(endTime, location.timeZone).time
        
        def isTimeActive = false
        if (start <= end) {
            isTimeActive = (currTime >= start && currTime <= end)
        } else {
            isTimeActive = (currTime >= start || currTime <= end)
        }
        if (!isTimeActive) return "Time"
    }
    
    return null
}

def evaluateRooms(boolean forceSync = false) {
    if (!isMasterEnabled()) return
    def maxRooms = settings.numRooms != null ? settings.numRooms.toInteger() : 5
    
    for (int i = 1; i <= maxRooms; i++) {
        if (settings["enableZ${i}"]) {
            def zName = settings["z${i}Name"] ?: "Room ${i}"
          
            def restriction = getRoomRestrictionReason(i)
            
            if (restriction) {
                updateRoomStats(i, false)
                turnOffAuxDevices(i) // Ensure aux devices die if the mode restricts the room
                state."currentRoomStates_z${i}" = "restricted"
                state."isPartial_z${i}" = false
                state."roomOccupiedSince_z${i}" = null
 
                if (settings["z${i}AbsoluteSweeper"]) {
                    def allHard = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                    def allSoft = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                    def allAux = getAllAuxDevsForRoom(i)
                    
                    def anyOn = false
                    if (allHard?.any { it.currentValue("switch") == "on" }) anyOn = true
                    if (allSoft?.any { it.currentValue("switch") == "on" }) anyOn = true
                    if (allAux?.any { it.currentValue("switch") == "on" }) anyOn = true
                  
                    if (anyOn) {
                        def lastM = state."zoneLastActive_z${i}" ?: 0
                        def lastV = state."vibeLastActive_z${i}" ?: 0
                        def lastAct = Math.max(lastM ?: 0, lastV ?: 0)
                        
                        if (lastAct == 0) {
                            state."zoneLastActive_z${i}" = now() 
                        } else {
                            def timeoutMs = (settings["z${i}AbsoluteSweeperTimeout"] ?: 120) * 60000

                            if ((now() - lastAct) >= timeoutMs) {
                                logAction("${zName}: 🚨 ABSOLUTE SWEEPER activated! Devices left ON in paused room with no motion for ${(settings["z${i}AbsoluteSweeperTimeout"] ?: 120)} minutes. Forcing shutdown.")
                                initiateRoomShutdown(i)
                                turnOffAuxDevices(i)
                                state."zoneLastActive_z${i}" = now() 
                            }
                        }
                    }
                }

                state."motionHitCount_z${i}" = 0
                state."vibeHitCount_z${i}" = 0
                state."motionHitStartTime_z${i}" = null
                state."vibeHitStartTime_z${i}" = null
                state."motionActiveSince_z${i}" = null 
                
                if (!settings["z${i}AbsoluteSweeper"]) {
                    state."zoneLastActive_z${i}" = null 
                    state."vibeLastActive_z${i}" = null 
                }
                
                def oSwitch = settings["z${i}OverrideSwitch"]
                if (oSwitch && oSwitch.currentValue("switch") == "on") {
                    state."expectedSwitchBehavior_z${i}" = "auto"
                    state."expectedSwitchBehaviorTime_z${i}" = now()
                    safeOff(oSwitch)
                }
                
                continue
            }

            def isOccupied = getRoomOccupancyState(i)
            def currentState = state."currentRoomStates_z${i}"
            
            updateRoomStats(i, (currentState == "occupied" || currentState == "partial"))
            
            def targetState = "empty"
            if (isOccupied) {
                // If the room is waking up organically (via sensors, not a manual button push)
                if (!state."switchIsManual_z${i}") {
                    
                    // Only evaluate sensor targets if the room is currently EMPTY (Initial Wake-up)
                    if (currentState == "empty") {
                        def mActive = settings["z${i}Motion"]?.any { it.currentValue("motion") == "active" }
                        def vActive = settings["z${i}Vibration"]?.any { it.currentValue("acceleration") == "active" }

                        def lastM = state."zoneLastActive_z${i}" ?: 0
                        def lastV = state."vibeLastActive_z${i}" ?: 0

                        def motionCaused = mActive || (now() - lastM < 10000)
                        def vibeCaused = vActive || (now() - lastV < 10000)

                        def wantsFull = false
                        def wantsPartial = false
                        
                        if (motionCaused) {
                            if (settings["z${i}MotionTarget"] == "PARTIAL") wantsPartial = true
                            else wantsFull = true
                        }
                        if (vibeCaused) {
                            if (settings["z${i}VibeTarget"] == "PARTIAL") wantsPartial = true
                            else wantsFull = true
                        }

                        // If a FULL sensor woke it up, it starts FULL. Otherwise, if only PARTIAL woke it, it starts PARTIAL.
                        if (wantsFull) {
                            state."isPartial_z${i}" = false
                        } else if (wantsPartial) {
                            state."isPartial_z${i}" = true
                        }
                    }
                }
                
                targetState = state."isPartial_z${i}" ? "partial" : "occupied"
            }
            
            def stateChanged = (currentState != targetState)

            if (stateChanged || forceSync) {
                state."currentRoomStates_z${i}" = targetState
                
                if (targetState == "occupied" || targetState == "partial") {
                    if (stateChanged) {
                        def today = getTodayDateString()
                        def stats = state."dailyStats_z${i}" ?: [:]
                        def daily = stats[today] ?: [occTime: 0, unoccTime: 0, count: 0]
                        daily.count += 1
                        stats[today] = daily
                        state."dailyStats_z${i}" = stats
                        
                        state."roomOccupiedSince_z${i}" = now()
                    }

                    if (!forceSync) {
                        def modeName = targetState == "partial" ? "PARTIAL OCCUPANCY" : "FULL OCCUPANCY"
                        logAction("${zName} is now in ${modeName}. Powering ON designated devices.")
                        handleSpeakerPlayback(i, targetState)
                    }
                    
                    state."shutdownDelayActive_z${i}" = false
                    
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    if (oSwitch && oSwitch.currentValue("switch") != "on") {
                        def expected = state."expectedSwitchBehavior_z${i}"
                        def expectedTime = state."expectedSwitchBehaviorTime_z${i}" ?: 0
                        def isPendingOn = (expected == "manual_on" && (now() - expectedTime) < 5000)
                        
                        if (!isPendingOn) {
                            state."expectedSwitchBehavior_z${i}" = "auto"
                            state."expectedSwitchBehaviorTime_z${i}" = now()
                            runIn(1, "syncVirtualSwitchOn", [data: [room: i]])
                        }
                    }
                    
                    if (targetState == "partial") {
                        turnRoomPartialDevicesOn(i)
                        if (!forceSync) turnOffNonPartialDevices(i)
                    } else {
                        turnRoomDevicesOn(i)
                    }

                } else {
                    if (!forceSync) {
                        logAction("${zName} is now EMPTY. Initiating shutdown sequence.")
                        handleSpeakerPause(i)
                    }
                    
                    state."unoccupiedLockoutTime_z${i}" = now() 
                    state."isPartial_z${i}" = false
                    state."roomOccupiedSince_z${i}" = null
                    
                    if (state."motionActiveSince_z${i}") {
                        state."motionActiveSince_z${i}" = null
                    }
                
                    def oSwitch = settings["z${i}OverrideSwitch"]
                    if (oSwitch && oSwitch.currentValue("switch") == "on") {
                        def expected = state."expectedSwitchBehavior_z${i}"
                        def expectedTime = state."expectedSwitchBehaviorTime_z${i}" ?: 0
                        def isPendingOff = (expected == "manual_off" && (now() - expectedTime) < 5000)
                        
                        if (!isPendingOff) {
                            state."expectedSwitchBehavior_z${i}" = "auto"
                            state."expectedSwitchBehaviorTime_z${i}" = now()
                            runIn(1, "syncVirtualSwitchOff", [data: [room: i]])
                        }
                    }
              
                    initiateRoomShutdown(i)
                    turnOffAuxDevices(i)
                }
            } else if (currentState == "empty" && settings["z${i}Sweeper"]) {
                def allHard = (settings["z${i}Switches"] ?: []) + (settings["z${i}PartialSwitches"] ?: [])
                def allSoft = (settings["z${i}SoftKillDevices"] ?: []) + (settings["z${i}PartialSoftKillDevices"] ?: [])
                def allAux = getAllAuxDevsForRoom(i)
                
                def anyOn = false
                if (allHard?.any { it.currentValue("switch") == "on" }) anyOn = true
                if (allSoft?.any { it.currentValue("switch") == "on" }) anyOn = true
                if (allAux?.any { it.currentValue("switch") == "on" }) anyOn = true
            
                if (anyOn) {
                    def lastAct = state."zoneLastActive_z${i}"
                    if (!lastAct) {
                        lastAct = now()
                        state."zoneLastActive_z${i}" = lastAct
                    }
                    def timeoutMs = (settings["z${i}SweeperTimeout"] ?: 60) * 60000
                    if ((now() - lastAct) >= timeoutMs) {
                        logAction("${zName}: 🧹 Sweeper activated! Stranded devices were left ON with no motion for ${(settings["z${i}SweeperTimeout"] ?: 60)} minutes. Forcing shutdown.")
                        
                        if (state."motionActiveSince_z${i}") {
                            state."motionActiveSince_z${i}" = null
                        }
                        
                        initiateRoomShutdown(i)
                        turnOffAuxDevices(i)
                        state."zoneLastActive_z${i}" = now() 
                    }
                }
            }

            // Un-tethered Aux device timeout evaluation:
            checkAuxDeviceTimeouts(i)
        }
    }
}

// === SONOS / SPEAKER INTEGRATION ===
def handleSpeakerPlayback(roomId, mode) {
    def speaker = settings["z${roomId}Sonos"]
    if (!speaker) return
    
    def audioMode = settings["z${roomId}AudioMode"] ?: "Track URI"
    def uri = mode == "occupied" ? settings["z${roomId}FullTrackURI"] : settings["z${roomId}PartialTrackURI"]
    def favSwitch = mode == "occupied" ? settings["z${roomId}FullFavSwitch"] : settings["z${roomId}PartialFavSwitch"]
    
    def targetVol = settings["z${roomId}TargetVolume"] != null ? settings["z${roomId}TargetVolume"].toInteger() : 30
    def fadeSecs = settings["z${roomId}FadeInTime"] != null ? settings["z${roomId}FadeInTime"].toInteger() : 0
    
    logAction("${settings["z${roomId}Name"]}: Starting speaker playback for ${mode} mode.")
    
    if (fadeSecs > 0) {
        speaker.setVolume(0)
        
        if (audioMode == "Favorite Virtual Switch" && favSwitch) {
            try { favSwitch.on() } catch (e) { log.error "Failed to turn on Favorite Switch: ${e.message}" }
        } else if (uri) {
            try { speaker.playTrack(uri) } catch (e) { log.error "Failed to playTrack on speaker: ${e.message}" }
        } else {
            try { speaker.play() } catch (e) { log.error "Failed to play on speaker: ${e.message}" }
        }
        
        state."fadeStep_z${roomId}" = 0
        state."targetVol_z${roomId}" = targetVol
        // Force decimal math so 30 / 60 = 0.5 instead of 0
        state."volPerStep_z${roomId}" = (targetVol * 1.0) / fadeSecs
        
        runIn(1, "fadeVolumeUp", [data: [room: roomId]])
    } else {
        speaker.setVolume(targetVol)
        
        if (audioMode == "Favorite Virtual Switch" && favSwitch) {
            try { favSwitch.on() } catch (e) { log.error "Failed to turn on Favorite Switch: ${e.message}" }
        } else if (uri) {
            try { speaker.playTrack(uri) } catch (e) { log.error "Failed to playTrack on speaker: ${e.message}" }
        } else {
            try { speaker.play() } catch (e) { log.error "Failed to play on speaker: ${e.message}" }
        }
    }
}

def fadeVolumeUp(data) {
    def roomId = data.room
    def speaker = settings["z${roomId}Sonos"]
    if (!speaker) return
    
    def step = (state."fadeStep_z${roomId}" ?: 0) + 1
    def maxStep = settings["z${roomId}FadeInTime"] != null ? settings["z${roomId}FadeInTime"].toInteger() : 0
    def targetVol = state."targetVol_z${roomId}" ?: 30
    def volPerStep = state."volPerStep_z${roomId}" ?: ((targetVol * 1.0) / maxStep)
    
    if (step <= maxStep) {
        def currentTarget = Math.round(step * volPerStep).toInteger()
        if (currentTarget > targetVol) currentTarget = targetVol
        // Ensure we don't send endless 0s during the first few seconds of a long fade
        if (currentTarget < 1) currentTarget = 1
        
        try { speaker.setVolume(currentTarget) } catch (e) { log.error "Failed to setVolume on speaker: ${e.message}" }
        
        state."fadeStep_z${roomId}" = step
        if (step < maxStep) {
            runIn(1, "fadeVolumeUp", [data: [room: roomId]])
        }
    }
}

def handleSpeakerPause(roomId) {
    def speaker = settings["z${roomId}Sonos"]
    if (!speaker) return
    logAction("${settings["z${roomId}Name"]}: Pausing speaker due to empty room.")
    try { speaker.pause() } catch (e) { log.error "Failed to pause speaker: ${e.message}" }
    
    if (settings["z${roomId}AudioMode"] == "Favorite Virtual Switch") {
        def fullSwitch = settings["z${roomId}FullFavSwitch"]
        def partSwitch = settings["z${roomId}PartialFavSwitch"]
        if (fullSwitch) { try { fullSwitch.off() } catch(e){} }
        if (partSwitch) { try { partSwitch.off() } catch(e){} }
    }
}

def turnRoomDevicesOn(roomId) {
    def hardDevs = settings["z${roomId}Switches"]
    if (hardDevs) hardDevs.each { safeOn(it) }
    
    def partHardDevs = settings["z${roomId}PartialSwitches"]
    if (partHardDevs) partHardDevs.each { safeOn(it) }
    
    def allSoft = (settings["z${roomId}SoftKillDevices"] ?: []) + (settings["z${roomId}PartialSoftKillDevices"] ?: [])
    if (allSoft) runIn(2, "executeSoftBoot", [data: [room: roomId, mode: "full"], overwrite: false])
}

def turnRoomPartialDevicesOn(roomId) {
    def partHardDevs = settings["z${roomId}PartialSwitches"]
    if (partHardDevs) partHardDevs.each { safeOn(it) }
    
    def partSoftDevs = settings["z${roomId}PartialSoftKillDevices"]
    if (partSoftDevs) runIn(2, "executeSoftBoot", [data: [room: roomId, mode: "partial"], overwrite: false])
}

def turnOffNonPartialDevices(roomId) {
    def fullHard = settings["z${roomId}Switches"] ?: []
    def partHard = settings["z${roomId}PartialSwitches"] ?: []
    fullHard.each { dev ->
        if (!partHard.any { it.id == dev.id }) safeOff(dev)
    }
    
    def fullSoft = settings["z${roomId}SoftKillDevices"] ?: []
    def partSoft = settings["z${roomId}PartialSoftKillDevices"] ?: []
    fullSoft.each { dev ->
        if (!partSoft.any { it.id == dev.id }) safeOff(dev)
    }
}

def executeSoftBoot(data) {
    if (!isMasterEnabled()) return
    def roomId = data.room
    def stateCheck = state."currentRoomStates_z${roomId}"
    
    if (stateCheck == "occupied" || stateCheck == "partial") {
        if (data.mode == "partial") {
            def softDevs = settings["z${roomId}PartialSoftKillDevices"]
            softDevs?.each { safeOn(it) }
        } else {
            def softDevs = (settings["z${roomId}SoftKillDevices"] ?: []) + (settings["z${roomId}PartialSoftKillDevices"] ?: [])
            softDevs.unique { it.id }?.each { safeOn(it) }
        }
    }
}

def initiateRoomShutdown(roomId) {
    if (!isMasterEnabled()) return
    def softDevs = (settings["z${roomId}SoftKillDevices"] ?: []) + (settings["z${roomId}PartialSoftKillDevices"] ?: [])
    softDevs = softDevs.unique { it.id }
    
    def hardDevs = (settings["z${roomId}Switches"] ?: []) + (settings["z${roomId}PartialSwitches"] ?: [])
    hardDevs = hardDevs.unique { it.id }
    
    def delaySecs = settings["z${roomId}HardKillDelay"] ?: 0

    if (softDevs && softDevs.any { it.currentValue("switch") != "off" }) {
        logAction("Sending Graceful Shutdown commands to sensitive electronics in Room ${roomId}.")
        softDevs.each { safeOff(it) }
        
        if (hardDevs && delaySecs > 0) {
            logAction("Waiting ${delaySecs} seconds for sensitive devices to shut down before cutting hard power.")
            state."shutdownDelayActive_z${roomId}" = true 
            runIn(delaySecs, "executeHardKill", [data: [room: roomId], overwrite: false])
            return 
        } else if (hardDevs) {
            executeHardKill([room: roomId])
            return
        }
    } else {
        executeHardKill([room: roomId])
        return
    }
}

def executeHardKill(data) {
    if (!isMasterEnabled()) return
    def roomId = data.room
    
    if (state."currentRoomStates_z${roomId}" != "empty") {
        logAction("Hard kill aborted. Room ${roomId} became occupied during the shutdown delay.")
        state."shutdownDelayActive_z${roomId}" = false
        return
    }

    state."shutdownDelayActive_z${roomId}" = false
    
    def hardDevs = (settings["z${roomId}Switches"] ?: []) + (settings["z${roomId}PartialSwitches"] ?: [])
    hardDevs = hardDevs.unique { it.id }
    
    if (hardDevs) {
        logAction("Cutting hard power to managed relays in Room ${roomId}.")
        hardDevs.each { safeOff(it) }
    }
}

// === HARDWARE SAFE & AUX WRAPPERS ===
def safeOn(dev) {
    if (dev) {
        state."appCommandedOn_${dev.id}" = now() // Track intent even if already on
        if (dev.currentValue("switch") != "on") {
            try { dev.on() } catch (e) { log.error "Failed to turn ON ${dev.displayName}: ${e.message}" }
        }
    }
}

def safeOff(dev) {
    if (dev && dev.currentValue("switch") != "off") {
        try { dev.off() } catch (e) { log.error "Failed to turn OFF ${dev.displayName}: ${e.message}" }
    }
}

def toggleAuxDevices(roomId, auxNum) {
    def devs = settings["z${roomId}Aux${auxNum}Device"]
    if (!devs) return
    def anyOn = devs.any { it.currentValue("switch") == "on" }
    if (anyOn) {
        logAction("${settings["z${roomId}Name"]}: Aux Button ${auxNum} toggled OFF manually.")
        devs.each { safeOff(it) }
    } else {
        logAction("${settings["z${roomId}Name"]}: Aux Button ${auxNum} toggled ON manually.")
        state."zoneLastActive_z${roomId}" = now() // Inject a fresh clock
        devs.each { safeOn(it) }
    }
}

def checkAuxDeviceTimeouts(roomId) {
    def timeoutMins = settings["z${roomId}AuxTimeout"]
    if (!timeoutMins) return
    
    // Prevent isolated Aux devices from turning off while the whole room is formally occupied
    def currentState = state."currentRoomStates_z${roomId}"
    if (currentState == "occupied" || currentState == "partial") return

    def allAux = getAllAuxDevsForRoom(roomId)
    
    if (allAux && allAux.any { it.currentValue("switch") == "on" }) {
        def lastMotion = state."zoneLastActive_z${roomId}"
        if (!lastMotion) {
            lastMotion = now()
            state."zoneLastActive_z${roomId}" = lastMotion
        }
        if ((now() - lastMotion) > (timeoutMins * 60000)) {
            logAction("${settings["z${roomId}Name"]}: Aux devices auto-off due to ${(timeoutMins)} minutes of no motion.")
            allAux.each { safeOff(it) }
        }
    }
}

def turnOffAuxDevices(roomId) {
    def allAux = getAllAuxDevsForRoom(roomId)
    allAux?.each { safeOff(it) }
}

// === LOGGING ===
def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }

def disableDebugLog() {
    log.info "Auto-disabling debug logging."
    app.updateSetting("debugEnable", [value: "false", type: "bool"])
}

def logDebug(msg) {
    if (debugEnable) log.debug "${app.label}: ${msg}"
}

// ==============================================================================
// OCCUPANCY STATS & TRACKING WRAPPERS
// ==============================================================================

def getTodayDateString() {
    def df = new java.text.SimpleDateFormat("yyyy-MM-dd")
    df.setTimeZone(location.timeZone)
    return df.format(new Date())
}

def formatDuration(long secs) {
    if (secs < 60) return "${secs}s"
    def mins = (secs / 60).toInteger()
    if (mins < 60) return "${mins}m"
    def hrs = (mins / 60).toInteger()
    def remMins = mins % 60
    return "${hrs}h ${remMins}m"
}

def updateRoomStats(roomId, isOccupied) {
    def lastUpdate = state."lastStatUpdate_z${roomId}" ?: now()
    def nowMs = now()
    def deltaSecs = ((nowMs - lastUpdate) / 1000).toLong()
    
    if (deltaSecs > 300) deltaSecs = 300 
    if (deltaSecs < 0) deltaSecs = 0

    def today = getTodayDateString()
    def stats = state."dailyStats_z${roomId}" ?: [:]
    def daily = stats[today] ?: [occTime: 0, unoccTime: 0, count: 0]

    if (isOccupied) {
        daily.occTime += deltaSecs
    } else {
        daily.unoccTime += deltaSecs
    }

    stats[today] = daily
    
    def keysToRemove = []
    def cal = Calendar.getInstance(location.timeZone)
    cal.add(Calendar.DAY_OF_YEAR, -7)
    def sevenDaysAgoStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(cal.getTime())
    
    stats.each { k, v ->
        if (k < sevenDaysAgoStr) keysToRemove << k
    }
    keysToRemove.each { stats.remove(it) }
 
    state."dailyStats_z${roomId}" = stats
    state."lastStatUpdate_z${roomId}" = nowMs
}

// ==============================================================================
// SCHEDULING WRAPPERS (Prevents cross-room overwriting in Hubitat memory)
// ==============================================================================
def evalR_All() { evaluateRooms(false) }

def evalR1() { evaluateRooms(false) }
def evalR2() { evaluateRooms(false) }
def evalR3() { evaluateRooms(false) }
def evalR4() { evaluateRooms(false) }
def evalR5() { evaluateRooms(false) }
def evalR6() { evaluateRooms(false) }
def evalR7() { evaluateRooms(false) }
def evalR8() { evaluateRooms(false) }
def evalR9() { evaluateRooms(false) }
def evalR10() { evaluateRooms(false) }
def evalR11() { evaluateRooms(false) }
def evalR12() { evaluateRooms(false) }

def verifyInactiveR1() { processMotionInactive(1) }
def verifyInactiveR2() { processMotionInactive(2) }
def verifyInactiveR3() { processMotionInactive(3) }
def verifyInactiveR4() { processMotionInactive(4) }
def verifyInactiveR5() { processMotionInactive(5) }
def verifyInactiveR6() { processMotionInactive(6) }
def verifyInactiveR7() { processMotionInactive(7) }
def verifyInactiveR8() { processMotionInactive(8) }
def verifyInactiveR9() { processMotionInactive(9) }
def verifyInactiveR10() { processMotionInactive(10) }
def verifyInactiveR11() { processMotionInactive(11) }
def verifyInactiveR12() { processMotionInactive(12) }

def resetMotionZ1() { state."motionHitCount_z1" = 0 }
def resetMotionZ2() { state."motionHitCount_z2" = 0 }
def resetMotionZ3() { state."motionHitCount_z3" = 0 }
def resetMotionZ4() { state."motionHitCount_z4" = 0 }
def resetMotionZ5() { state."motionHitCount_z5" = 0 }
def resetMotionZ6() { state."motionHitCount_z6" = 0 }
def resetMotionZ7() { state."motionHitCount_z7" = 0 }
def resetMotionZ8() { state."motionHitCount_z8" = 0 }
def resetMotionZ9() { state."motionHitCount_z9" = 0 }
def resetMotionZ10() { state."motionHitCount_z10" = 0 }
def resetMotionZ11() { state."motionHitCount_z11" = 0 }
def resetMotionZ12() { state."motionHitCount_z12" = 0 }

def syncVirtualSwitchOn(data) {
    def dev = settings["z${data.room}OverrideSwitch"]
    if (dev) safeOn(dev)
}

def syncVirtualSwitchOff(data) {
    def dev = settings["z${data.room}OverrideSwitch"]
    if (dev) safeOff(dev)
}

def delayedFullActionR1() { executeDelayedFullAction(1) }
def delayedFullActionR2() { executeDelayedFullAction(2) }
def delayedFullActionR3() { executeDelayedFullAction(3) }
def delayedFullActionR4() { executeDelayedFullAction(4) }
def delayedFullActionR5() { executeDelayedFullAction(5) }
def delayedFullActionR6() { executeDelayedFullAction(6) }
def delayedFullActionR7() { executeDelayedFullAction(7) }
def delayedFullActionR8() { executeDelayedFullAction(8) }
def delayedFullActionR9() { executeDelayedFullAction(9) }
def delayedFullActionR10() { executeDelayedFullAction(10) }
def delayedFullActionR11() { executeDelayedFullAction(11) }
def delayedFullActionR12() { executeDelayedFullAction(12) }

def executeDelayedFullAction(roomId) {
    def lastPartial = atomicState."lastPartialTap_z${roomId}" ?: 0
    def lastFull = atomicState."lastFullTap_z${roomId}" ?: 0

    // If a partial tap happened within 3 seconds BEFORE OR AFTER this full tap, it was a ghost push.
    if (lastPartial > 0 && Math.abs(lastPartial - lastFull) < 3000) {
        logDebug("Delayed FULL action cancelled: A PARTIAL tap (double tap) was detected simultaneously.")
        return
    }
    
    def currentState = state."currentRoomStates_z${roomId}"
    def zName = settings["z${roomId}Name"] ?: "Room ${roomId}"

    if (currentState == "occupied") {
        logAction("${zName}: Override Button FULL Action (Toggle OFF). Forcing EMPTY.")
        forceRoomEmpty(roomId)
        runIn(1, "evalR${roomId}")
    } else {
        logAction("${zName}: Override Button FULL Action. Forcing FULL Occupancy.")
        forceRoomState(roomId, "occupied")
        runIn(1, "evalR${roomId}")
    }
}

// ==============================================================================
// MISSING VIBRATION RESET WRAPPERS
// ==============================================================================
def resetVibeZ1() { state."vibeHitCount_z1" = 0 }
def resetVibeZ2() { state."vibeHitCount_z2" = 0 }
def resetVibeZ3() { state."vibeHitCount_z3" = 0 }
def resetVibeZ4() { state."vibeHitCount_z4" = 0 }
def resetVibeZ5() { state."vibeHitCount_z5" = 0 }
def resetVibeZ6() { state."vibeHitCount_z6" = 0 }
def resetVibeZ7() { state."vibeHitCount_z7" = 0 }
def resetVibeZ8() { state."vibeHitCount_z8" = 0 }
def resetVibeZ9() { state."vibeHitCount_z9" = 0 }
def resetVibeZ10() { state."vibeHitCount_z10" = 0 }
def resetVibeZ11() { state."vibeHitCount_z11" = 0 }
def resetVibeZ12() { state."vibeHitCount_z12" = 0 }
