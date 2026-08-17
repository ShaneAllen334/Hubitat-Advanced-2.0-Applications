/*
 * Advanced Meal Time Manager 2.0
 *
 */
definition(
    name: "Advanced Meal Time Manager 2.0",
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
    page(name: "speakerMappingPage")
    page(name: "lightMappingPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Meal Time Manager</b>", install: true, uninstall: true) {
        
        section("<b>Live System Dashboard</b>") {
            input "btnRefreshData", "button", title: "🔄 Refresh Dashboard Data"
            
            def sysStatus = state.mealTimeActive ? "<b style='color:#27ae60;'>ACTIVE (${state.activeMealType?.capitalize()} in Progress)</b>" : "<b style='color:#555;'>IDLE (Waiting for occupants)</b>"
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>System Status:</b> ${sysStatus}</div>"

            def reqChairs = settings.minChairs ?: 2
            def reqVibes = settings.minTotalVibes ?: 5
            def occCount = state.occupiedCount ?: 0
            def totalVibes = state.totalVibes ?: 0
            
            def chairStr = state.mealTimeActive ? "<b style='color:green;'>${occCount} Occupied</b>" : "${occCount} Occupied (Requires ${reqChairs})"
            def vibeStr = state.mealTimeActive ? "<b style='color:green;'>${totalVibes} Human Movements</b>" : "${totalVibes} Human Movements (Requires ${reqVibes})"
            
            def modeStatus = isModeAllowed() ? "<b style='color:green;'>Allowed</b>" : "<b style='color:red;'>Restricted</b>"
            
            def currentWin = getCurrentTimeWindow()
            def timeStatus = currentWin != "none" ? "<b style='color:green;'>${currentWin.capitalize()} Window Active</b>" : "<b style='color:#e67e22;'>Outside Meal Windows</b>"
            
            def guestActive = (guestSwitch && guestSwitch.currentValue("switch") == "on")
            def guestStr = guestActive ? "<b style='color:#c0392b;'>PAUSED (Automation Disabled)</b>" : "Inactive (Normal Operation)"
            
            def chefStatusStr = "<span style='color:#555;'>Disabled (Anyone can trigger)</span>"
            if (settings.chefChairSelect && settings.chefChairSelect != "None") {
                if (state.chefIsSeated) chefStatusStr = "<b style='color:green;'>Seated (${settings.chefChairSelect})</b>"
                else chefStatusStr = "<b style='color:#e67e22;'>Waiting for Chef (${settings.chefChairSelect})</b>"
            }
            
            // Dynamic Countdown Calculation
            def countdownStr = "Cleared"
            if (state.shutoffPending && state.expectedEndTime) {
                long remainingMs = state.expectedEndTime - now()
                if (remainingMs > 0) {
                    long remSecs = remainingMs / 1000
                    long m = remSecs / 60
                    long s = remSecs % 60
                    String sStr = s < 10 ? "0${s}" : "${s}"
                    countdownStr = "<b style='color:#c0392b;'>Ending in ${m}:${sStr}</b>"
                } else {
                    countdownStr = "<b style='color:#c0392b;'>Ending Now...</b>"
                }
            }
            
            // Pending Light Shutoffs Status
            def pendingLightsCount = state.pendingLightOffs?.size() ?: 0
            def pendingLightsStr = pendingLightsCount > 0 ? "<b style='color:#e67e22;'>${pendingLightsCount} queued (Waiting for motion to clear)</b>" : "None Queued"

            def liveStats = state.dashboardSeatStats ?: "No recent data"
            def calibStr = state.chairBaselines ? "<b style='color:green;'>3D Spatial Vectors Locked</b>" : "<span style='color:#555;'>No 3D Data / Standard Sensors</span>"

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
                .dash-sub { background-color: #e9ecef; font-weight: bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Metric</th><th colspan="3">Current Value</th></tr></thead>
                <tbody>
                    <tr><td colspan="4" class="dash-sub">Trigger Conditions & Overrides</td></tr>
                    <tr><td class="dash-hl">Operating Mode</td><td colspan="3" class="dash-val">${modeStatus}</td></tr>
                    <tr><td class="dash-hl">Current Meal Window</td><td colspan="3" class="dash-val">${timeStatus}</td></tr>
                    <tr><td class="dash-hl">Guest Mode Override</td><td colspan="3" class="dash-val">${guestStr}</td></tr>
                    <tr><td class="dash-hl">Chef's Chair Interlock</td><td colspan="3" class="dash-val">${chefStatusStr}</td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Family Lifestyle Metrics (Resets Sundays)</td></tr>
                    <tr><td class="dash-hl">Meals Recorded This Week</td><td colspan="3" class="dash-val" style="font-size:16px;"><b>${state.weeklyMealCount ?: 0}</b></td></tr>
                    <tr><td class="dash-hl">Average Meal Duration</td><td colspan="3" class="dash-val" style="font-size:16px; color:#2980b9;"><b>${getAverageMealDurationStr()}</b></td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Physical Tracking (Rolling Window)</td></tr>
                    <tr><td class="dash-hl">Current Seat Status</td><td colspan="3" class="dash-val">${chairStr}</td></tr>
                    <tr><td class="dash-hl">Accumulated Energy</td><td colspan="3" class="dash-val">${vibeStr}</td></tr>
                    <tr><td class="dash-hl">Live Sensor Data</td><td colspan="3" class="dash-val" style="font-family:monospace; font-size:12px;">${liveStats}</td></tr>
                    <tr><td class="dash-hl">Auto-End Countdown</td><td colspan="3" class="dash-val">${countdownStr}</td></tr>
                    <tr><td class="dash-hl">Pending Smart Light Shutoffs</td><td colspan="3" class="dash-val">${pendingLightsStr}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>System Controls</b>", hideable: true, hidden: true) {
            input "btnCalibrateChairs", "button", title: "🪑 Calibrate Empty Chairs (For Samsung 3D Sensors)"
            input "btnResetStats", "button", title: "📊 Reset Weekly Meal Stats"
            input "btnForceDinner", "button", title: "▶️ Force Start Dinner"
            input "btnForceEnd", "button", title: "⏹️ Force End Meal Time"
        }

        section("<b>System Event History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
        }

        section("<b>Seat Monitoring Hardware</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Select up to 4 sensors mounted to your dining chairs. The app will automatically route them to the 3D physics engine if they support it.</div>"
            input "chair1", "capability.accelerationSensor", title: "Chair Sensor 1", required: false
            input "chair2", "capability.accelerationSensor", title: "Chair Sensor 2", required: false
            input "chair3", "capability.accelerationSensor", title: "Chair Sensor 3", required: false
            input "chair4", "capability.accelerationSensor", title: "Chair Sensor 4", required: false
        }
        
        section("<b>The 'Chef's Chair' Interlock</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Homework Filter:</b> Prevent the meal from starting until a specific chair (e.g., Mom or Dad) is occupied. Kids can sit at the table all afternoon without triggering the house.</div>"
            input "chefChairSelect", "enum", title: "Designate the Chef's Chair", options: ["None", "Chair 1", "Chair 2", "Chair 3", "Chair 4"], defaultValue: "None", required: true, submitOnChange: true
        }
        
        section("<b>Anti-False Alarm Filters (Physics & Accumulation)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Samsung 3D Mode (The 'Roomba' Filter):</b> If using Samsung Multi-Sensors, the app checks the 3D spatial shift. Micro-vibrations are ignored.</div>"
            input "spatialThreshold", "number", title: "Samsung 3D Tilt Threshold", description: "Minimum spatial shift required to count as human weight (Default: 30).", defaultValue: 30, required: true
            
            paragraph "<div style='font-size:13px; color:#555;'><b>Accumulator (The 'Kick' Filter):</b> Prevents accidental bumps from triggering a meal by counting multiple valid movements over a rolling window.</div>"
            input "vibeWindow", "number", title: "Tracking Window (Minutes)", defaultValue: 5, required: true
            input "minChairs", "number", title: "Minimum Occupied Chairs to Trigger", defaultValue: 2, required: true
            input "minVibesPerChair", "number", title: "Min Events per Chair", description: "How many times must a chair register weight/movement to be 'Occupied'? (Default: 2)", defaultValue: 2, required: true
            input "minTotalVibes", "number", title: "Min Total Events Combined", description: "Total valid events required across all occupied chairs. (Default: 5)", defaultValue: 5, required: true
        }

        section("<b>Timeout & Guest Override</b>", hideable: true, hidden: true) {
            input "inactiveTimeout", "number", title: "Standard Empty Table Timeout (Minutes)", description: "How long must ALL chairs remain still before Meal Time ends? (Default: 5)", defaultValue: 5, required: true
            
            paragraph "<div style='font-size:13px; color:#555;'><b>Guest Mode Filter:</b> If enabled, all meal automation functionality is completely paused. This allows guests to enjoy their meal without automated lights or timers interfering.</div>"
            input "guestSwitch", "capability.switch", title: "Guest Mode Virtual Switch", required: false
        }
        
        section("<b>Global Mode Restrictions</b>", hideable: true, hidden: true) {
            input "allowedModes", "mode", title: "Allowed Modes for Automation (Leave blank for all)", multiple: true, required: false
        }

        section("<b>🔕 Do Not Disturb Settings</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Link a virtual Do Not Disturb switch. The app will turn it ON during meal times and revert it when the meal ends (only if it wasn't already ON).</div>"
            input "dndSwitch", "capability.switch", title: "Virtual Do Not Disturb (DND) Switch", required: false
        }
        
        section("<b>📺 TV Conflict Override (Optional)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>If you have another app that turns lights back ON when the TV is turned OFF, link the TV switch here. This app will turn the TV OFF immediately, wait for the other app to fire, and then execute its own Light OFF commands.</div>"
            input "tvSwitch", "capability.switch", title: "TV Switch", required: false
            input "tvLightDelay", "number", title: "Delay before turning lights OFF (Seconds)", description: "Applies only if the TV was ON. (Default: 5)", defaultValue: 5, required: false
        }

        section("<b>🔔 'Call to Meal' Announcement Engine</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Map a button controller to manually broadcast meal time announcements to active rooms. <br><b>Push = Dinner</b></div>"
            input "mealButton", "capability.pushableButton", title: "Call to Meal Button Controller", required: false
            input "mealButtonNum", "number", title: "Button Number on Controller", defaultValue: 1, required: true
            
            input "dinnerPayload", "text", title: "Dinner Track/Chime #", description: "e.g., 3"
            
            paragraph "<b>Output Hardware</b>"
            input "soundDevices", "capability.audioNotification", title: "Sound File/MP3/Zooz Players", multiple: true, required: false
            input "audioVolume", "number", title: "Master Announcement Volume Level (%)", defaultValue: 65, range: "1..100"
            
            if (soundDevices) {
                href(name: "speakerMappingPageLink", page: "speakerMappingPage", title: "▶ Smart Room Presence Audio Routing", description: "Map your speakers to motion sensors to avoid broadcasting to empty rooms.")
            }
        }

        section("<b>🎵 Meal Time Ambiance</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Automatically start background music or trigger an ambiance switch after a delay during meal time. It will be paused/turned off when the meal ends.</div>"
            input "ambianceDelay", "number", title: "Delay before starting ambiance (Minutes)", defaultValue: 5, required: false
            
            input "ambianceSpeaker", "capability.musicPlayer", title: "Ambiance Speaker", required: false, submitOnChange: true
            if (ambianceSpeaker) {
                input "ambianceAudioMode", "enum", title: "↳ Audio Source", options: ["Track URI", "Favorite Virtual Switch"], required: true, defaultValue: "Track URI", submitOnChange: true
                
                if (ambianceAudioMode == "Favorite Virtual Switch") {
                    input "ambianceFavSwitch", "capability.switch", title: "↳ Favorite Virtual Switch", required: false
                } else {
                    input "ambianceTrack", "text", title: "↳ Music Track URI/File to play (Optional)", required: false
                }
            }
            
            input "ambianceVolume", "number", title: "Target Ambiance Volume (%)", range: "1..100", required: false
            input "ambianceFadeEnable", "bool", title: "Fade-in Volume (Starts at 5% and gently ramps up)", defaultValue: false, required: false
            input "ambianceSwitch", "capability.switch", title: "Ambiance Switch (Turns ON)", required: false
        }
        
        section("<b>🍽️ DINNER Configuration</b>", hideable: true, hidden: true) {
            input "dinnerStartTime", "time", title: "Dinner Start Time", required: false
            input "dinnerEndTime", "time", title: "Dinner End Time", required: false
            
            paragraph "<b>Dinner ON Actions</b>"
            input "dinnerMealSwitch", "capability.switch", title: "Virtual Dinner Switch (Turns ON)", required: false
            input "dinnerOnLights", "capability.switch", title: "Standard Lights to Turn ON", multiple: true, required: false
            
            input "dinnerDimLights", "capability.switchLevel", title: "Dimmable/Color Lights to Turn ON", multiple: true, required: false
            input "dinnerLightLevel", "number", title: "↳ Brightness Level (%)", range: "1..100", required: false
            input "dinnerLightColor", "enum", title: "↳ Color / Temp", options: ["Warm White (2700K)", "Soft White (3000K)", "Daylight (5000K)", "Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink"], required: false

            input "dinnerLockDoors", "capability.lock", title: "Doors to Lock", multiple: true, required: false
            input "dinnerPauseSpeakers", "capability.musicPlayer", title: "Speakers/Media to Pause", multiple: true, required: false
            
            paragraph "<b>Dinner OFF Actions</b>"
            input "dinnerOffLights", "capability.switch", title: "Lights to Turn OFF (When Meal Starts)", multiple: true, required: false, submitOnChange: true
            input "mealEndOffLights", "capability.switch", title: "Lights to Turn OFF (When Meal Ends)", multiple: true, required: false, submitOnChange: true
            
            if (dinnerOffLights || mealEndOffLights) {
                href(name: "lightMappingPageLink", page: "lightMappingPage", title: "💡 Smart Light Shutoff Mapping", description: "Map motion sensors to prevent lights from slamming off while someone is still in the room.")
            }
        }
    }
}

def speakerMappingPage() {
    return dynamicPage(name: "speakerMappingPage", title: "<b>Smart Room Presence Audio Routing</b>", install: false, uninstall: false) {
        section() { paragraph "<i>Assign one or multiple motion sensors to your speakers.</i>" }
        def allSpeakers = []
        if (settings.soundDevices) allSpeakers += settings.soundDevices
        allSpeakers = allSpeakers.unique { it.id }
        
        if (allSpeakers) {
            allSpeakers.each { speaker ->
                section("<b>Routing for: ${speaker.displayName}</b>", hideable: true, hidden: true) {
                    input "isAlwaysOn_${speaker.id}", "bool", title: "🎙️ Make this a Master Always-Announce Speaker", defaultValue: false, submitOnChange: true
                    if (!settings["isAlwaysOn_${speaker.id}"]) {
                        input "motionMap_${speaker.id}", "capability.motionSensor", title: "🚶 Required Motion Sensors (Active within 5 mins)", required: false, multiple: true
                        input "nightSwitch_${speaker.id}", "capability.switch", title: "🌙 Room 'Good Night' Override Switch", required: false
                    }
                }
            }
        }
    }
}

def lightMappingPage() {
    return dynamicPage(name: "lightMappingPage", title: "<b>Smart Light Shutoff Mapping</b>", install: false, uninstall: false) {
        section() { paragraph "<i>Assign motion sensors to your lights. The app will wait for motion to stop before turning them off. Perfect for kitchen lights when walking to the table.</i>" }
        def allLights = []
        if (settings.dinnerOffLights) allLights += settings.dinnerOffLights
        if (settings.mealEndOffLights) allLights += settings.mealEndOffLights
        allLights = allLights.unique { it.id }
        
        if (allLights) {
            allLights.each { light ->
                section("<b>Graceful Shutoff for: ${light.displayName}</b>", hideable: true, hidden: true) {
                    input "lightMotionMap_${light.id}", "capability.motionSensor", title: "🚶 Required Motion Sensors (Wait until inactive)", required: false, multiple: true
                }
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
    if (state.mealTimeActive == null) state.mealTimeActive = false
    if (state.shutoffPending == null) state.shutoffPending = false
    if (state.activeMealType == null) state.activeMealType = "none"
    if (state.chefIsSeated == null) state.chefIsSeated = false
    if (!state.seatHistory) state.seatHistory = [:]
    if (!state.chairBaselines) state.chairBaselines = [:]
    if (state.appTurnedOnDND == null) state.appTurnedOnDND = false
    if (state.pendingLightOffs == null) state.pendingLightOffs = [:]
    
    // Lifestyle Metrics Initialization
    if (state.weeklyMealCount == null) state.weeklyMealCount = 0
    if (state.weeklyMealDurationMs == null) state.weeklyMealDurationMs = 0
    
    // Schedule Auto-Reset for Sunday at Midnight
    schedule("0 0 0 ? * SUN", resetWeeklyStats)
    
    // Auto-Detect and Subscribe based on capabilities
    def chairs = [chair1, chair2, chair3, chair4]
    chairs.each { c ->
        if (c) {
            subscribe(c, "acceleration", combinedAccelerationHandler)
            
            if (c.hasAttribute("threeAxis")) {
                subscribe(c, "threeAxis", chairAxisHandler)
                logAction("HARDWARE: ${c.displayName} running in Hybrid Mode (3D Spatial + Standard Vibration).")
            } else {
                logAction("HARDWARE: ${c.displayName} running in Standard Vibration Mode.")
            }
        }
    }
    
    // Subscribe to Call-to-Meal Button
    if (settings.mealButton) {
        subscribe(settings.mealButton, "pushed", "mealButtonHandler")
    }
    
    // Subscribe to Room Motion Sensors for Audio Routing
    def audioMotionSensors = []
    settings.each { key, val ->
        if (key.startsWith("motionMap_") && val) {
            if (val instanceof List) audioMotionSensors.addAll(val)
            else audioMotionSensors << val
        }
    }
    if (audioMotionSensors) {
        audioMotionSensors = audioMotionSensors.unique { it.id }
        subscribe(audioMotionSensors, "motion.active", "motionActiveHandler")
    }
    
    // Subscribe to Motion Sensors for Smart Light Shutoff
    def lightMotionSensors = []
    settings.each { key, val ->
        if (key.startsWith("lightMotionMap_") && val) {
            if (val instanceof List) lightMotionSensors.addAll(val)
            else lightMotionSensors << val
        }
    }
    if (lightMotionSensors) {
        lightMotionSensors = lightMotionSensors.unique { it.id }
        subscribe(lightMotionSensors, "motion.inactive", "lightMotionHandler")
    }
    
    evaluateSeats() 
    logAction("App Initialized.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefreshData") {
        evaluateSeats()
        logAction("MANUAL: Dashboard data refreshed.")
    } else if (btn == "btnCalibrateChairs") {
        calibrateChairs()
    } else if (btn == "btnResetStats") {
        resetWeeklyStats()
    } else if (btn == "btnForceDinner") {
        logAction("MANUAL OVERRIDE: Dinner Forced ON.")
        startMealTime("dinner")
    } else if (btn == "btnForceEnd") {
        logAction("MANUAL OVERRIDE: Meal Time Forced OFF.")
        endMealTime()
    }
}

// ------------------------------------------------------------------------------
// SMART LIGHT SHUTOFF LOGIC
// ------------------------------------------------------------------------------

def queueLightOff(light) {
    if (!light) return
    def mappedSensors = settings["lightMotionMap_${light.id}"]
    
    if (mappedSensors) {
        def sensorList = mappedSensors instanceof List ? mappedSensors : [mappedSensors]
        def activeSensors = sensorList.findAll { it.currentValue("motion") == "active" }
        
        if (activeSensors.size() > 0) {
            def p = state.pendingLightOffs ?: [:]
            p[light.id] = true
            state.pendingLightOffs = p
            logAction("Delaying shutoff for ${light.displayName} - Mapped motion sensor is currently active.")
            return
        }
    }
    
    // If no sensors are mapped, or all are inactive, shut off immediately
    light.off()
}

def lightMotionHandler(evt) {
    if (evt.value == "inactive") {
        def p = state.pendingLightOffs ?: [:]
        if (!p || p.size() == 0) return
        
        def allLights = []
        if (settings.dinnerOffLights) allLights.addAll(settings.dinnerOffLights)
        if (settings.mealEndOffLights) allLights.addAll(settings.mealEndOffLights)
        
        def lightsTurnedOff = []
        
        p.each { lightId, isPending ->
            if (isPending) {
                def light = allLights.find { it.id == lightId }
                if (light) {
                    def mappedSensors = settings["lightMotionMap_${light.id}"]
                    def sensorList = mappedSensors instanceof List ? mappedSensors : [mappedSensors]
                    
                    def stillActive = sensorList?.any { it.currentValue("motion") == "active" }
                    
                    if (!stillActive) {
                        light.off()
                        lightsTurnedOff << lightId
                        logAction("AUTOMATION: Motion cleared. Turned off ${light.displayName}.")
                    }
                } else {
                    // Light was removed from settings but stuck in state
                    lightsTurnedOff << lightId
                }
            }
        }
        
        lightsTurnedOff.each { p.remove(it) }
        state.pendingLightOffs = p
    }
}

// ------------------------------------------------------------------------------
// AUDIO ROUTING & CALL TO MEAL LOGIC
// ------------------------------------------------------------------------------

def mealButtonHandler(evt) {
    def targetBtn = settings.mealButtonNum ? settings.mealButtonNum.toString() : "1"
    if (evt.value == targetBtn) {
        if (evt.name == "pushed") {
            logAction("Call to Meal Button: Pushed (Dinner)")
            executeAnnouncement(settings.dinnerPayload)
        }
    }
}

def executeAnnouncement(payload) {
    if (!payload) return
    playSoundFile(payload)
}

def motionActiveHandler(evt) { 
    state["motionLastActive_${evt.deviceId}"] = now() 
}

def shouldSpeakerAnnounce(device) {
    if (settings["isAlwaysOn_${device.id}"]) return true
    def overrideSw = settings["nightSwitch_${device.id}"]
    if (overrideSw && overrideSw.currentValue("switch") == "on") return true
    
    def mappedSensors = settings["motionMap_${device.id}"]
    if (!mappedSensors) return true 
    
    def sensorList = mappedSensors instanceof List ? mappedSensors : [mappedSensors]
    def isActive = false
    for (sensor in sensorList) {
        if (sensor.currentValue("motion") == "active") {
            isActive = true
            break
        }
        def lastActive = state["motionLastActive_${sensor.id}"] ?: 0
        if (now() - lastActive <= 300000) { // 5 minutes
            isActive = true
            break
        }
    }
    return isActive
}

def playSoundFile(url, force = false) {
    if (!url || !settings.soundDevices) return
    def vol = settings.audioVolume ?: 65
    try {
        settings.soundDevices.each { player ->
            if (force || shouldSpeakerAnnounce(player)) {
                if (player.hasCommand("setVolume")) player.setVolume(vol)
                if (url.trim().isInteger() && player.hasCommand("playSound")) {
                    player.playSound(url.trim().toInteger())
                } else if (player.hasCommand("playTrack")) {
                    player.playTrack(url.trim())
                } else if (player.hasCommand("chime")) {
                    player.chime(url.trim().toInteger())
                }
            } else {
                logAction("Sound file suppressed on ${player.displayName} - Room Empty.")
            }
        }
        logAction("Audio Track Announcement Dispatched.")
    } catch (e) { log.error "Audio routing failed: ${e}" }
}

// ------------------------------------------------------------------------------
// HARDWARE CALIBRATION
// ------------------------------------------------------------------------------

def calibrateChairs() {
    def baselines = [:]
    def chairs = [chair1, chair2, chair3, chair4]
    chairs.each { c ->
        if (c && c.hasAttribute("threeAxis")) {
            def xyz = c.currentValue("threeAxis")
            if (xyz) {
                baselines[c.id] = [x: xyz.x as Integer, y: xyz.y as Integer, z: xyz.z as Integer]
                logAction("Calibrated 3D baseline for ${c.displayName}: ${xyz}")
            }
        }
    }
    state.chairBaselines = baselines
    logAction("SYSTEM: All 3D spatial baselines locked in.")
}

// ------------------------------------------------------------------------------
// LIFESTYLE METRICS HELPERS
// ------------------------------------------------------------------------------

def resetWeeklyStats() {
    state.weeklyMealCount = 0
    state.weeklyMealDurationMs = 0
    logAction("SYSTEM: Weekly Family Lifestyle metrics have been reset.")
}

String getAverageMealDurationStr() {
    if (!state.weeklyMealCount || state.weeklyMealCount == 0) return "N/A"
    long avgMs = (state.weeklyMealDurationMs ?: 0) / state.weeklyMealCount
    long totalMins = avgMs / 60000
    long hrs = totalMins / 60
    long mins = totalMins % 60
    if (hrs > 0) return "${hrs}h ${mins}m"
    return "${mins}m"
}

// ------------------------------------------------------------------------------
// TIME & MODE HELPERS
// ------------------------------------------------------------------------------

boolean isModeAllowed() {
    if (!settings.allowedModes) return true
    return (settings.allowedModes as List).contains(location.mode)
}

boolean isTimeInWindow(startTimeStr, endTimeStr) {
    if (!startTimeStr || !endTimeStr) return false
    
    def t0 = timeToday(startTimeStr, location.timeZone)
    def t1 = timeToday(endTimeStr, location.timeZone)
    def now = new Date()
    
    if (t1.time < t0.time) { 
        return (now.time >= t0.time || now.time <= t1.time)
    } else {
        return (now.time >= t0.time && now.time <= t1.time)
    }
}

String getCurrentTimeWindow() {
    if (isTimeInWindow(settings.dinnerStartTime, settings.dinnerEndTime)) return "dinner"
    return "none"
}

int getTimeoutSeconds() {
    return (settings.inactiveTimeout ?: 5) * 60
}

// ------------------------------------------------------------------------------
// SENSOR PHYSICS & TRACKING
// ------------------------------------------------------------------------------

def combinedAccelerationHandler(evt) {
    if (evt.value == "active") {
        recordValidEvent(evt.device.id)
    } else if (evt.value == "inactive") {
        evaluateSeats() 
    }
}

def chairAxisHandler(evt) {
    def c = evt.device
    def xyz = c.currentValue("threeAxis")
    if (!xyz) return

    def baselines = state.chairBaselines ?: [:]
    def base = baselines[c.id]
    if (!base) return 

    int curX = xyz.x as Integer
    int curY = xyz.y as Integer
    int curZ = xyz.z as Integer

    int devX = Math.abs(curX - base.x)
    int devY = Math.abs(curY - base.y)
    int devZ = Math.abs(curZ - base.z)
    int maxDev = Math.max(devX, Math.max(devY, devZ))

    int thresh = settings.spatialThreshold ?: 30

    if (maxDev >= thresh) {
        recordValidEvent(c.id)
    }
}

def recordValidEvent(devId) {
    def history = state.seatHistory ?: [:]
    def devEvents = history[devId] ?: []
    
    long nowMs = now()
    
    if (devEvents.size() > 0 && (nowMs - devEvents.last()) < 2000) return
    
    devEvents << nowMs
    
    if (devEvents.size() > 50) devEvents = devEvents.drop(devEvents.size() - 50)
    
    history[devId] = devEvents
    state.seatHistory = history
    
    evaluateSeats()
}

// ------------------------------------------------------------------------------
// MEAL LOGIC EVALUATION
// ------------------------------------------------------------------------------

def evaluateSeats() {
    def history = state.seatHistory ?: [:]
    long cutoff = now() - ((settings.vibeWindow ?: 5) * 60 * 1000)
    
    int occupiedChairsCount = 0
    int totalValidVibes = 0
    int liveActiveCount = 0
    
    boolean chefSeated = false
    def targetedChefChair = null
    
    if (settings.chefChairSelect == "Chair 1") targetedChefChair = chair1
    else if (settings.chefChairSelect == "Chair 2") targetedChefChair = chair2
    else if (settings.chefChairSelect == "Chair 3") targetedChefChair = chair3
    else if (settings.chefChairSelect == "Chair 4") targetedChefChair = chair4
    
    if (!targetedChefChair || settings.chefChairSelect == "None") {
        chefSeated = true 
    }
    
    def chairList = [chair1, chair2, chair3, chair4]
    def debugStrings = []
    
    chairList.each { chair ->
        if (chair) {
            if (chair.currentValue("acceleration") == "active") {
                liveActiveCount++
            }
            
            def devId = chair.id
            def events = history[devId] ?: []
            events = events.findAll { it >= cutoff } 
            history[devId] = events 
            
            int vibeCount = events.size()
            int reqPerChair = settings.minVibesPerChair ?: 2
            boolean thisChairOccupied = false
            
            if (vibeCount >= reqPerChair) {
                occupiedChairsCount++
                totalValidVibes += vibeCount
                thisChairOccupied = true
            }
            
            if (targetedChefChair && chair.id == targetedChefChair.id && thisChairOccupied) {
                chefSeated = true
            }
            
            debugStrings << "${chair.displayName}: ${vibeCount}"
        }
    }
    
    state.seatHistory = history 
    state.dashboardSeatStats = debugStrings.join(" | ")
    state.occupiedCount = occupiedChairsCount
    state.totalVibes = totalValidVibes
    state.chefIsSeated = chefSeated
    
    boolean isGuestModeActive = (settings.guestSwitch && settings.guestSwitch.currentValue("switch") == "on")
    if (isGuestModeActive) {
        return // Automation paused; dashboard stats update but logic halts
    }
    
    int reqChairs = settings.minChairs ?: 2
    int reqTotal = settings.minTotalVibes ?: 5

    // 1. CANCEL SHUTOFF IF ACTIVITY RESUMES
    if (totalValidVibes >= reqTotal && state.shutoffPending) {
        logAction("Table activity detected in rolling window. Canceling auto-end timer.")
        unschedule("endMealTime")
        state.shutoffPending = false
        state.expectedEndTime = null
    }
    
    // 2. TRIGGER NEW MEAL LOGIC
    if (!state.mealTimeActive) {
        if (occupiedChairsCount >= reqChairs && totalValidVibes >= reqTotal) {
            if (!chefSeated) {
                logAction("Seats occupied, but waiting for Chef's Chair to trigger meal.")
            } else {
                String win = getCurrentTimeWindow()
                
                if (win != "none" && isModeAllowed()) {
                    logAction("TRIGGER: ${occupiedChairsCount} chairs occupied, Chef is seated. Initiating ${win.capitalize()}.")
                    startMealTime(win)
                } else {
                    if (!isModeAllowed()) logAction("Seats occupied, but ignored due to Mode restrictions.")
                    else if (win == "none") logAction("Seats occupied, but currently outside of Dinner window.")
                }
            }
        }
    } 
    // 3. END MEAL LOGIC (Checks accumulator instead of instant 'liveActiveCount')
    else if (state.mealTimeActive && totalValidVibes < reqTotal && !state.shutoffPending) {
        int delaySeconds = getTimeoutSeconds()
        int delayMinutes = delaySeconds / 60
        
        logAction("Accumulated activity dropped below threshold. Scheduling ${state.activeMealType?.capitalize()} end in ${delayMinutes} minutes.")
        state.shutoffPending = true
        state.expectedEndTime = now() + (delaySeconds * 1000)
        runIn(delaySeconds, "endMealTime", [overwrite: true])
    }
}

def startMealTime(String mealType) {
    state.mealTimeActive = true
    state.activeMealType = mealType
    state.shutoffPending = false
    state.expectedEndTime = null
    state.mealStartTime = now() 
    
    logAction("AUTOMATION: Executing ${mealType.capitalize()} ON routines.")
    
    // DND Switch Logic
    if (settings.dndSwitch) {
        if (settings.dndSwitch.currentValue("switch") != "on") {
            logAction("DND Switch is OFF. Turning ON for meal time.")
            settings.dndSwitch.on()
            state.appTurnedOnDND = true
        } else {
            logAction("DND Switch is already ON. Skipping automatic control.")
            state.appTurnedOnDND = false
        }
    }
    
    // Fire the Ambiance Delay Logic
    int delayMins = settings.ambianceDelay != null ? settings.ambianceDelay : 5
    if (settings.ambianceSpeaker || settings.ambianceSwitch) {
        if (delayMins > 0) {
            logAction("Scheduling Meal Time Ambiance to start in ${delayMins} minutes.")
            runIn(delayMins * 60, "startAmbiance")
        } else {
            startAmbiance()
        }
    }
    
    boolean delayOffLights = false
    if (settings.tvSwitch) {
        if (settings.tvSwitch.currentValue("switch") == "on") {
            logAction("TV is ON. Turning it off and delaying light shutoff by ${settings.tvLightDelay ?: 5} seconds.")
            delayOffLights = true
        }
        settings.tvSwitch.off() 
    }
    
    if (mealType == "dinner") {
        if (dinnerMealSwitch) dinnerMealSwitch.on()
        
        // Standard Lights ON
        if (dinnerOnLights) dinnerOnLights.on()
        
        // Dimmable & Color Lights Handler
        if (dinnerDimLights) {
            dinnerDimLights.each { light ->
                if (settings.dinnerLightLevel) {
                    if (light.hasCommand("setLevel")) light.setLevel(settings.dinnerLightLevel)
                } else {
                    if (light.hasCommand("on")) light.on()
                }

                if (settings.dinnerLightColor) {
                    def ct = null
                    def hue = 0
                    def sat = 100
                    switch(settings.dinnerLightColor) {
                        case "Warm White (2700K)": ct = 2700; break
                        case "Soft White (3000K)": ct = 3000; break
                        case "Daylight (5000K)": ct = 5000; break
                        case "Red": hue = 0; break
                        case "Orange": hue = 10; break
                        case "Yellow": hue = 16; break
                        case "Green": hue = 33; break
                        case "Blue": hue = 66; break
                        case "Purple": hue = 75; break
                        case "Pink": hue = 83; break
                    }
                    if (ct != null && light.hasCommand("setColorTemperature")) {
                        light.setColorTemperature(ct)
                    } else if (ct == null && light.hasCommand("setColor")) {
                        light.setColor([hue: hue, saturation: sat, level: settings.dinnerLightLevel ?: 100])
                    }
                }
            }
        }
        
        if (dinnerLockDoors) dinnerLockDoors.lock()
        pauseAudio(dinnerPauseSpeakers)
        
        if (delayOffLights && dinnerOffLights) {
            runIn(settings.tvLightDelay ?: 5, "turnOffDinnerLights")
        } else if (dinnerOffLights) {
            dinnerOffLights.each { queueLightOff(it) }
        }
    }
}

// ------------------------------------------------------------------------------
// AMBIANCE EXECUTION
// ------------------------------------------------------------------------------

def startAmbiance() {
    logAction("AUTOMATION: Starting Meal Time Ambiance.")
    if (ambianceSwitch) {
        ambianceSwitch.on()
    }
    
    if (ambianceSpeaker) {
        try {
            int targetVol = settings.ambianceVolume ? settings.ambianceVolume as int : 50
            def audioMode = settings.ambianceAudioMode ?: "Track URI"
            
            if (settings.ambianceFadeEnable) {
                int startVol = 5
                ambianceSpeaker.setVolume(startVol)
                
                if (audioMode == "Favorite Virtual Switch" && settings.ambianceFavSwitch) {
                    settings.ambianceFavSwitch.on()
                } else if (settings.ambianceTrack) {
                    ambianceSpeaker.playTrack(settings.ambianceTrack)
                } else {
                    ambianceSpeaker.play()
                }
                
                int stepAmt = Math.max(1, (targetVol - startVol) / 5 as int)
                logAction("Initiating volume fade-in from ${startVol}% to ${targetVol}%.")
                runIn(3, "rampAmbianceVolume", [data: [currentVol: startVol, targetVol: targetVol, step: stepAmt]])
            } else {
                if (settings.ambianceVolume) ambianceSpeaker.setVolume(targetVol)
                
                if (audioMode == "Favorite Virtual Switch" && settings.ambianceFavSwitch) {
                    settings.ambianceFavSwitch.on()
                } else if (settings.ambianceTrack) {
                    ambianceSpeaker.playTrack(settings.ambianceTrack)
                } else {
                    ambianceSpeaker.play()
                }
            }
        } catch (e) { log.error "Failed to play ambiance on ${ambianceSpeaker.displayName}: ${e}" }
    }
}

def rampAmbianceVolume(Map data) {
    if (!state.mealTimeActive) return // Abort ramp if the meal was suddenly ended
    
    int currentVol = data.currentVol
    int targetVol = data.targetVol
    int step = data.step
    
    currentVol += step
    
    if (currentVol >= targetVol) {
        ambianceSpeaker.setVolume(targetVol)
        logAction("Ambiance volume reached target (${targetVol}%).")
    } else {
        ambianceSpeaker.setVolume(currentVol)
        runIn(3, "rampAmbianceVolume", [data: [currentVol: currentVol, targetVol: targetVol, step: step]])
    }
}

def turnOffDinnerLights() {
    logAction("AUTOMATION: TV delay finished. Evaluating Dinner lights OFF.")
    if (dinnerOffLights) {
        dinnerOffLights.each { queueLightOff(it) }
    }
}

def pauseAudio(speakers) {
    if (!speakers) return
    speakers.each { speaker ->
        try {
            if (speaker.hasCommand("pause")) speaker.pause()
        } catch (e) { log.error "Failed to pause speaker ${speaker.displayName}: ${e}" }
    }
}

def endMealTime() {
    String mealType = state.activeMealType ?: "none"
    state.mealTimeActive = false
    state.activeMealType = "none"
    state.shutoffPending = false
    state.expectedEndTime = null
    
    unschedule("turnOffDinnerLights")
    unschedule("startAmbiance")
    
    // Execute pending Meal End Off Lights
    if (settings.mealEndOffLights) {
        settings.mealEndOffLights.each { queueLightOff(it) }
    }
    
    // DND Switch Reversal Logic
    if (settings.dndSwitch && state.appTurnedOnDND) {
        logAction("Meal time ended. Turning DND Switch OFF (App originally turned it ON).")
        settings.dndSwitch.off()
        state.appTurnedOnDND = false
    }
    
    // Pause Ambiance if still actively playing
    if (ambianceSpeaker) {
        try {
            def status = ambianceSpeaker.currentValue("status")
            if (status == "playing") {
                logAction("AUTOMATION: Ambiance still playing. Issuing Pause command.")
                ambianceSpeaker.pause()
            }
        } catch (e) { log.error "Failed to pause ambiance on ${ambianceSpeaker.displayName}: ${e}" }
        
        if (settings.ambianceAudioMode == "Favorite Virtual Switch" && settings.ambianceFavSwitch) {
            try { settings.ambianceFavSwitch.off() } catch(e){}
        }
    }
    
    if (ambianceSwitch) ambianceSwitch.off()
    
    if (state.mealStartTime) {
        long durationMs = now() - state.mealStartTime
        state.weeklyMealDurationMs = (state.weeklyMealDurationMs ?: 0) + durationMs
        state.weeklyMealCount = (state.weeklyMealCount ?: 0) + 1
        state.mealStartTime = null
        logAction("Meal Metrics Logged: Duration was ${Math.round(durationMs / 60000)} minutes.")
    }
    
    if (mealType != "none") {
        logAction("AUTOMATION: ${mealType.capitalize()} has concluded. Executing OFF routines.")
    } else {
        logAction("AUTOMATION: Meal Time manually forced off.")
    }
    
    if (dinnerMealSwitch) dinnerMealSwitch.off()
    
    state.seatHistory = [:]
    evaluateSeats()
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
