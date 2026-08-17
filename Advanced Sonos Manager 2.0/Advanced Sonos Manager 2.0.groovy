/**
 * Advanced Sonos Manager 2.0
 */
definition(
    name: "Advanced Sonos Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Audio",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        def optControlPanel = settings.enableControlPanel == null ? true : settings.enableControlPanel
        def optPowerManagement = settings.enablePowerManagement == null ? true : settings.enablePowerManagement
        def optFavorites = settings.enableFavorites == null ? true : settings.enableFavorites
        def maxZones = settings.numZones ?: 5
        
        def appIsDisabled = (settings.appEnableSwitch && settings.appEnableSwitch.currentValue("switch") == "off")

        def hasZones = false
        def activeZoneOptions = [:]

        // ========================================================
        // REPORTING & CONTROL DASHBOARDS
        // ========================================================
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"

            if (appIsDisabled) {
                paragraph "<div style='padding: 12px; background-color: #f8d7da; border-left: 5px solid #dc3545; border-radius: 5px; color: #721c24; margin-bottom: 15px;'><b>⚠️ SYSTEM DISABLED:</b> The Master Application Switch is currently OFF. All background automations and overrides are suspended.</div>"
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
                <thead><tr><th>Zone Name</th><th>Main Power</th><th>Status</th><th>Volume</th><th>Now Playing</th></tr></thead>
                <tbody>
            """

            for (int i = 1; i <= maxZones; i++) {
                if (settings["enableZ${i}"] && settings["z${i}Speaker"]) {
                    hasZones = true
                    def spk = settings["z${i}Speaker"]
                    def sw = settings["z${i}Switch"]
                    def gnLock = settings["z${i}GoodNightSwitch"]
                    def customName = settings["z${i}Name"]
                    def resolvedName = customName ?: (spk.label ?: "Zone ${i}")
                    
                    activeZoneOptions["${i}"] = resolvedName
                    
                    def isLocked = gnLock && (gnLock.currentValue("switch") == "on")
                    def isEventMuted = (state.doorbellMutedSpks?.contains(spk.id)) || (state.doorOpenMutedSpks?.contains(spk.id))
                    
                    def zoneNameStr = "<b>${resolvedName}</b>"
                    if (isLocked) zoneNameStr += "<br><span style='color:purple; font-size:10px; text-transform:uppercase;'>🌙 GN Override Active</span>"

                    def isPoweredOn = sw ? (sw.currentValue("switch") == "on") : true
                    def pwrStatus = sw ? (isPoweredOn ? "<span style='color:green;'>ON</span>" : "<span style='color:red;'>OFF</span>") : "N/A"
                    
                    def playStatus = spk.currentValue("status")?.toUpperCase() ?: "UNKNOWN"
                    def isMuted = spk.currentValue("mute") == "muted"
                    
                    def statusIcon = "⚪"
                    def statusText = playStatus
                    def statusColor = "gray"
                    
                    if (!isPoweredOn) {
                        statusIcon = "🔌❌"
                        statusText = "POWER CUT"
                        statusColor = "red"
                    } else if (isEventMuted) {
                        statusIcon = "🔇"
                        statusText = "EVENT MUTE"
                        statusColor = "red"
                    } else if (isMuted) {
                        statusIcon = "🔇"
                        statusText = "MUTED"
                        statusColor = "orange"
                    } else if (playStatus == "PLAYING") {
                        statusIcon = "▶️"
                        statusColor = "blue"
                    } else if (playStatus == "PAUSED") {
                        statusIcon = "⏸️"
                        statusColor = "orange"
                    } else if (playStatus == "STOPPED") {
                        statusIcon = "⏹️"
                        statusColor = "gray"
                    }
                    
                    def vol = spk.currentValue("volume") ?: "--"
                    def trackTitle = spk.currentValue("trackDescription")
                    if (!trackTitle || trackTitle.trim() == "") trackTitle = "Idle / Streaming"
                    
                    dashHTML += "<tr><td class='dash-hl'>${zoneNameStr}</td><td><b>${pwrStatus}</b></td><td style='color:${statusColor}; font-weight:bold;'>${statusIcon} ${statusText}</td><td>${vol}%</td><td style='text-align:left; font-weight:bold; font-size:13px;'>${trackTitle}</td></tr>"
                }
            }
            dashHTML += "</tbody></table>"
  
            if (hasZones) {
                paragraph dashHTML
            } else {
                paragraph "<i>Please configure your Sonos zones below to populate the dashboard.</i>"
            }
        }

        if (hasZones && optControlPanel) {
            section("<b>Active Control Panel</b>", hideable: true) {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Remotely control your zones or manage Virtual Favorites.</div>"
                
                paragraph "<div style='background-color:#dc3545; color:white; padding:8px 10px; font-weight:bold; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px; margin-bottom:5px;'>🚨 Global Emergency Override</div>"
                input "btnPauseAll", "button", title: "🛑 PAUSE ALL ZONES INSTANTLY", width: 12

                input "activeZoneControl", "enum", title: "Select Zone to Control", options: activeZoneOptions, submitOnChange: true
                
                if (activeZoneControl) {
                    def targetSpk = settings["z${activeZoneControl}Speaker"]
                    def targetSw = settings["z${activeZoneControl}Switch"]
                    def targetName = settings["z${activeZoneControl}Name"] ?: (targetSpk ? targetSpk.label : "Zone ${activeZoneControl}")
                    
                    if (targetSpk) {
                        def cpPwr = targetSw ? (targetSw.currentValue("switch") == "on" ? "ON" : "OFF") : "N/A"
                        def cpState = targetSpk.currentValue("status")?.toUpperCase() ?: "UNKNOWN"
                        def cpVol = targetSpk.currentValue("volume") ?: "--"
                        def cpTrack = targetSpk.currentValue("trackDescription") ?: "Idle / Unknown"
                        
                        def cpHTML = """
                        <table class="dash-table" style="margin-bottom: 5px;">
                            <thead><tr><th colspan="4">🎯 Active Target: ${targetName}</th></tr></thead>
                            <tbody>
                                <tr>
                                    <td width="20%"><div style="font-size: 11px; color: #888; text-transform: uppercase; margin-bottom: 4px;"><b>Main Power</b></div><div style="font-size: 16px; font-weight:bold; color:${cpPwr=='ON'?'green':'red'};">${cpPwr}</div></td>
                                    <td width="20%"><div style="font-size: 11px; color: #888; text-transform: uppercase; margin-bottom: 4px;"><b>Status</b></div><div style="font-size: 16px; font-weight:bold; color:${cpState=='PLAYING'?'blue':(cpState=='PAUSED'?'orange':'gray')};">${cpState}</div></td>
                                    <td width="20%"><div style="font-size: 11px; color: #888; text-transform: uppercase; margin-bottom: 4px;"><b>Volume</b></div><div style="font-size: 16px; font-weight:bold;">${cpVol}%</div></td>
                                    <td width="40%"><div style="font-size: 11px; color: #888; text-transform: uppercase; margin-bottom: 4px;"><b>Now Playing</b></div><div style="font-size: 12px; font-weight:bold; line-height:1.2;">${cpTrack}</div></td>
                                </tr>
                            </tbody>
                        </table>
                        """
                        paragraph cpHTML
                    }

                    paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>🎛️ Basic Transport</div>"
                    input "btnPlay", "button", title: "▶️ Play", width: 2
                    input "btnPause", "button", title: "⏸ Pause", width: 2
                    input "btnPrev", "button", title: "⏮ Prev", width: 2
                    input "btnNext", "button", title: "⏭ Next", width: 2
                    input "btnVolDown", "button", title: "🔉 Vol -5%", width: 2
                    input "btnVolUp", "button", title: "🔊 Vol +5%", width: 2
                    
                    paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>🎚️ Advanced Controls</div>"
                    input "btnShuffle", "button", title: "🔀 Toggle Shuffle", width: 3
                    input "btnRepeat", "button", title: "🔁 Toggle Repeat", width: 3
                    input "btnNightMode", "button", title: "🌙 Toggle Night Mode", width: 3
                    input "btnSpeechEnhance", "button", title: "🗣️ Toggle Speech Enhance", width: 3
                    
                    paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>⏳ Sleep Timer</div>"
                    input "sleepTimerMins", "number", title: "Minutes until Pause", required: false, width: 6
                    input "sleepTimerFade", "number", title: "Fade-Out Duration (Sec)", required: false, width: 3
                    input "btnSleep", "button", title: "⏳ Start Timer", width: 3
                    if (state.sleepTimers && state.sleepTimers[activeZoneControl]) {
                        paragraph "<span style='color:orange;'><i>Sleep Timer currently active for this zone.</i></span>"
                        input "btnCancelSleep", "button", title: "Cancel Timer"
                    }

                    if (optFavorites) {
                        paragraph "<div style='background-color:#343a40; color:white; padding:8px 10px; font-weight:bold; margin-top:10px; border-radius:3px 3px 0 0; text-transform: uppercase; letter-spacing: 1px;'>⭐ Favorites Management</div>"
                        paragraph "<div style='font-size:13px; color:#555;'>Save the current Track URI and volume to a Virtual Switch, or manage existing ones.</div>"
                        input "btnSaveFav", "button", title: "➕ Save Current Track as Virtual Switch", width: 12
                        
                        // SYNC CHILD LABELS TO INTERNAL STATE
                        def favOptions = [:]
                        def favoritesNeedSave = false
                        if (state.savedFavorites) {
                            state.savedFavorites.each { dni, data -> 
                                def child = getChildDevice(dni)
                                if (child) {
                                    def currentLabel = child.label ?: child.name
                                    // If user renamed it in Hubitat, sync it to the app
                                    if (currentLabel && currentLabel != data.name) {
                                        data.name = currentLabel
                                        favoritesNeedSave = true
                                    }
                                }
                                favOptions[dni] = data.name 
                            }
                            if (favoritesNeedSave) {
                                state.savedFavorites = state.savedFavorites // Force save state map
                            }
                        }
                        
                        if (favOptions) {
                            paragraph "<hr>"
                            input "favToEdit", "enum", title: "Select Favorite to Edit Volume", options: favOptions, required: false, width: 4, submitOnChange: true
                            input "newFavVol", "number", title: "New Start Volume (%)", range: "1..100", required: false, width: 4
                            input "btnUpdateFavVol", "button", title: "💾 Update Volume", width: 4
                            
                            if (favToEdit && state.savedFavorites[favToEdit]) {
                                def curSavedVol = state.savedFavorites[favToEdit].vol
                                paragraph "<div style='font-size:12px; color:#007bff;'>Current saved volume for this favorite: <b>${curSavedVol}%</b></div>"
                            }
                            
                            paragraph "<hr>"
                            input "favToDelete", "enum", title: "Select Favorite to Delete", options: favOptions, required: false, width: 6
                            input "btnDeleteFav", "button", title: "🗑️ Delete Selected", width: 6
                        }
                    }
                } else {
                    paragraph "<div style='padding: 10px; background-color: #e9ecef; border-left: 5px solid #007bff; border-radius: 5px;'><b>Note:</b> Select a zone from the dropdown above to reveal the transport controls and Favorites Virtual Switch generator.</div>"
                }
            }
        }

        section("<b>Recent Action History</b>", hideable: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        // ========================================================
        // GLOBAL CONTROLS & MODULE TOGGLES
        // ========================================================
        section("<b>1. Global Controls & Module Toggles</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Manage global permissions and enable or disable major app features. Disabling a feature removes its configuration options and halts its background processing.</div>"
            
            input "numZones", "number", title: "<b>Number of Zones to Configure</b><br><span style='font-size:12px; font-weight:normal; color:#555;'>Determines the total number of individual speaker zones available for setup.</span>", defaultValue: 5, required: true, submitOnChange: true
            paragraph "<hr>"
            
            input "appEnableSwitch", "capability.switch", title: "<b>Master App Enable/Disable Switch</b><br><span style='font-size:12px; font-weight:normal; color:#555;'>Master kill-switch. When toggled off, immediately suspends all background routines and automations.</span>", required: false
            paragraph "<hr>"
            
            input "enableControlPanel", "bool", title: "<b>Enable Active Control Panel</b><br><span style='font-size:12px; font-weight:normal; color:#555;'>Reveals the Active Control Panel on the dashboard for remote transport control and virtual favorite management.</span>", defaultValue: true, submitOnChange: true
            input "enablePowerManagement", "bool", title: "<b>Enable Smart Power Automation</b><br><span style='font-size:12px; font-weight:normal; color:#555;'>Activates automated power handling, allowing smart plugs to intelligently cycle on/off based on Hubitat location modes.</span>", defaultValue: true, submitOnChange: true
            input "enableSelfHealing", "bool", title: "<b>Enable Self-Healing on Hub Reboot</b><br><span style='font-size:12px; font-weight:normal; color:#555;'>Automatically re-syncs physical hardware states to match scheduled software states following a hub reboot or power loss.</span>", defaultValue: true, submitOnChange: true
            input "enableFavorites", "bool", title: "<b>Enable Virtual Switch Favorites</b><br><span style='font-size:12px; font-weight:normal; color:#555;'>Enables the creation of Virtual Switches that dynamically store and recall specific track URIs and volume levels.</span>", defaultValue: true, submitOnChange: true
        }

        // ========================================================
        // SYSTEM CONFIGURATION
        // ========================================================

        for (int i = 1; i <= maxZones; i++) {
            def secTitle = settings["z${i}Name"] ? "<b>⚙️ ${settings["z${i}Name"]}</b>" : "<b>⚙️ Zone ${i}</b>"
            section(secTitle, hideable: true, hidden: true) {
                input "enableZ${i}", "bool", title: "<b>Enable Zone ${i}</b>", submitOnChange: true
                if (settings["enableZ${i}"]) {
                    input "z${i}Name", "text", title: "Custom Zone Name", required: false, submitOnChange: true
                    input "z${i}Speaker", "capability.musicPlayer", title: "Select Sonos Speaker", required: true
                    
                    if (optPowerManagement) {
                        input "z${i}Switch", "capability.switch", title: "Select Smart Power Plug", required: false
                    }
                    
                    paragraph "<b>Protection & Overrides</b>"
                    input "z${i}GoodNightSwitch", "capability.switch", title: "Good Night Override Switch (Aborts all automations when ON)", required: false
                    input "z${i}MaxVol", "number", title: "Maximum Volume Cap (%) - Hardware Protection", required: false, range: "1..100"

                    if (optPowerManagement) {
                        paragraph "<b>Power Automation & Startup Settings</b>"
                        input "z${i}TurnOnModes", "mode", title: "Modes to Power ON this Zone", multiple: true, required: false
                        input "z${i}TurnOffModes", "mode", title: "Modes to Power OFF this Zone", multiple: true, required: false
                        
                        input "z${i}StartVol", "number", title: "Default Target Startup Volume (%)", required: false, range: "1..100"
                        input "z${i}AutoResume", "bool", title: "Auto-Resume Playback on Boot", defaultValue: false
                    }
                }
            }
        }

        section("<b>2. Event Overrides & Muting</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Temporarily mutes playing speakers during real-world events. Zones locked by Good Night switches will be ignored to preserve privacy.</div>"
            input "enableEventOverrides", "bool", title: "<b>Enable Event Muting</b>", defaultValue: false, submitOnChange: true
            if (enableEventOverrides) {
                input "doorbellButton", "capability.pushableButton", title: "Doorbell Button", required: false
                input "doorbellButtonNum", "number", title: "Doorbell Button Number", required: false, defaultValue: 1
                input "doorbellMuteTime", "number", title: "Seconds to Mute for Doorbell", required: false, defaultValue: 30
                paragraph "<hr>"
                input "doorSensors", "capability.contactSensor", title: "Perimeter Doors (Mutes when Open)", required: false, multiple: true
            }
        }

        section("<b>3. Dynamic Ambient Noise Compensation</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically detects noisy household appliances and boosts the volume of actively playing speakers to compensate. It dynamically calculates the maximum requested boost if multiple appliances are running simultaneously, respects volume caps, and reverts to the original volume once the noise stops.</div>"
            input "enableAmbientComp", "bool", title: "<b>Enable Dynamic Ambient Noise Compensation</b>", defaultValue: false, submitOnChange: true
            if (enableAmbientComp) {
                input "ambientModes", "mode", title: "Allowed Modes (Leave blank for all)", multiple: true, required: false
                
                def zoneOpts = [:]
                for (int i = 1; i <= maxZones; i++) {
                    if (settings["enableZ${i}"] && settings["z${i}Speaker"]) {
                        zoneOpts["${i}"] = settings["z${i}Name"] ?: settings["z${i}Speaker"].label ?: "Zone ${i}"
                    }
                }
                input "ambientZones", "enum", title: "Select Allowed Zones to Auto-Boost", options: zoneOpts, multiple: true, required: true
                
                input "ambientMaxCap", "number", title: "Maximum Volume Cap (%) (Do not boost if already at or above this volume)", defaultValue: 80, required: true
                input "ambientDelay", "number", title: "Delay before reacting to noise (Seconds)", defaultValue: 60, required: true
                
                paragraph "<b>Noisy Appliance Triggers</b>"
                input "ambientDishwasher", "capability.switch", title: "Dishwasher Switch", required: false
                input "ambientDishwasherBoost", "number", title: "Dishwasher Boost Amount (+%)", defaultValue: 10, required: false
                
                input "ambientRobovac", "capability.switch", title: "Robo-Vacuum Switch", required: false
                input "ambientRobovacBoost", "number", title: "Robo-Vacuum Boost Amount (+%)", defaultValue: 15, required: false
                
                input "ambientDehum", "capability.switch", title: "Dehumidifier Switch", required: false
                input "ambientDehumBoost", "number", title: "Dehumidifier Boost Amount (+%)", defaultValue: 10, required: false
                
                input "ambientHVAC", "capability.thermostat", title: "HVAC System", required: false
                input "ambientHVACBoost", "number", title: "HVAC Boost Amount (+%)", defaultValue: 12, required: false
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

String getHumanReadableStatus() {
    def appIsDisabled = (settings.appEnableSwitch && settings.appEnableSwitch.currentValue("switch") == "off")
    if (appIsDisabled) return "<span style='color:red;'><b>System Disabled:</b> Master Application Switch is currently OFF. Automations suspended.</span>"
    if (state.doorbellMutedSpks || state.doorOpenMutedSpks) return "<span style='color:orange;'><b>Event Override Active:</b> Intercepting playback (Muted via doorbell/door sensor trigger).</span>"
    if (state.currentBoostAmt && state.currentBoostAmt > 0) return "<span style='color:blue;'><b>Ambient Compensation Active:</b> Volumes dynamically boosted by +${state.currentBoostAmt}% to offset appliance noise.</span>"
    return "<span style='color:green;'>System is operating normally. Monitoring active zones.</span>"
}

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def isAppEnabled() {
    if (settings.appEnableSwitch && settings.appEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def isZoneLocked(zNum) {
    def sw = settings["z${zNum}GoodNightSwitch"]
    return (sw && sw.currentValue("switch") == "on")
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.savedFavorites) state.savedFavorites = [:]
    if (!state.sleepTimers) state.sleepTimers = [:]
    if (!state.doorbellMutedSpks) state.doorbellMutedSpks = []
    if (!state.doorOpenMutedSpks) state.doorOpenMutedSpks = []
    if (!state.preBoostVols) state.preBoostVols = [:]
    if (state.currentBoostAmt == null) state.currentBoostAmt = 0
    
    if (settings.enablePowerManagement != false) {
        subscribe(location, "mode", modeChangeHandler)
        runEvery1Hour("hourlyStateEnforcement")
    }

    if (settings.enableSelfHealing != false) {
        subscribe(location, "systemStart", systemStartHandler)
    }
    
    if (settings.enableFavorites != false) {
        getChildDevices().each { child -> subscribe(child, "switch", childSwitchHandler) }
    }

    if (settings.enableEventOverrides) {
        if (settings.doorbellButton) subscribe(settings.doorbellButton, "pushed", doorbellHandler)
        if (settings.doorSensors) subscribe(settings.doorSensors, "contact", doorHandler)
    }
    
    if (settings.enableAmbientComp) {
        if (settings.ambientDishwasher) subscribe(settings.ambientDishwasher, "switch", ambientNoiseHandler)
        if (settings.ambientRobovac) subscribe(settings.ambientRobovac, "switch", ambientNoiseHandler)
        if (settings.ambientDehum) subscribe(settings.ambientDehum, "switch", ambientNoiseHandler)
        if (settings.ambientHVAC) subscribe(settings.ambientHVAC, "thermostatOperatingState", ambientNoiseHandler)
    }

    def maxZones = settings.numZones ?: 5
    for (int i = 1; i <= maxZones; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Speaker"] && settings["z${i}MaxVol"]) {
            subscribe(settings["z${i}Speaker"], "volume", volumeHandler)
        }
    }

    logAction("App Initialized. Advanced Sonos Engine Ready.")
}

// --- BUTTON & DASHBOARD HANDLERS ---

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
        return
    }

    if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
        return
    }

    if (btn == "btnPauseAll") {
        logAction("🚨 EMERGENCY PAUSE ALL TRIGGERED 🚨")
        def maxZones = settings.numZones ?: 5
        for (int i = 1; i <= maxZones; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Speaker"]) settings["z${i}Speaker"].pause()
        }
        return
    }

    if (!isAppEnabled()) {
        logAction("Master Switch is OFF. Control Panel actions ignored.")
        return
    }

    if (settings.enableControlPanel == false) return

    if (activeZoneControl && btn.startsWith("btn")) {
        def zNum = activeZoneControl
        def spk = settings["z${zNum}Speaker"]
        if (!spk) return

        if (btn == "btnPlay") { spk.play(); logAction("Command -> Sent PLAY to ${spk.label}") }
        if (btn == "btnPause") { spk.pause(); logAction("Command -> Sent PAUSE to ${spk.label}") }
        if (btn == "btnNext") { spk.nextTrack(); logAction("Command -> Sent NEXT TRACK to ${spk.label}") }
        if (btn == "btnPrev") { spk.previousTrack(); logAction("Command -> Sent PREV TRACK to ${spk.label}") }
        if (btn == "btnVolUp") { def v = (spk.currentValue("volume") ?: 0) + 5; spk.setLevel(v > 100 ? 100 : v); logAction("Command -> Vol UP on ${spk.label}") }
        if (btn == "btnVolDown") { def v = (spk.currentValue("volume") ?: 0) - 5; spk.setLevel(v < 0 ? 0 : v); logAction("Command -> Vol DOWN on ${spk.label}") }
        
        if (btn == "btnShuffle") { 
            if (spk.hasCommand("setShuffle")) { spk.setShuffle(true); logAction("Command -> Shuffle toggled on ${spk.label}") } 
            else logAction("Warning: ${spk.label} driver does not support setShuffle.")
        }
        if (btn == "btnRepeat") { 
            if (spk.hasCommand("setRepeat")) { spk.setRepeat(true); logAction("Command -> Repeat toggled on ${spk.label}") } 
            else logAction("Warning: ${spk.label} driver does not support setRepeat.")
        }
        if (btn == "btnNightMode") { 
            if (spk.hasCommand("setNightMode")) { spk.setNightMode(true); logAction("Command -> Night Mode toggled on ${spk.label}") } 
            else logAction("Warning: ${spk.label} driver does not support native setNightMode.")
        }
        if (btn == "btnSpeechEnhance") { 
            if (spk.hasCommand("setSpeechEnhancement")) { spk.setSpeechEnhancement(true); logAction("Command -> Speech Enhance toggled on ${spk.label}") } 
            else logAction("Warning: ${spk.label} driver does not support native setSpeechEnhancement.")
        }
        
        if (btn == "btnSleep" && sleepTimerMins) {
            state.sleepTimers[zNum] = true
            def fadeDur = sleepTimerFade ?: 0
            runIn((sleepTimerMins * 60).toInteger(), "executeSleepTimer", [data: [spkId: spk.id, zNum: zNum, fade: fadeDur], overwrite: false])
            logAction("Command -> Sleep Timer started for ${spk.label} (${sleepTimerMins} mins, ${fadeDur}s fade).")
        }
        
        if (btn == "btnCancelSleep") {
            state.sleepTimers[zNum] = false
            logAction("Command -> Sleep Timer cancelled for ${spk.label}.")
        }

        if (settings.enableFavorites != false) {
            if (btn == "btnSaveFav") createFavoriteVirtualSwitch(spk)
            if (btn == "btnDeleteFav" && settings.favToDelete) {
                def dni = settings.favToDelete
                def favName = state.savedFavorites[dni]?.name ?: "Unknown"
                try { deleteChildDevice(dni) } catch (e) { }
                state.savedFavorites.remove(dni)
                app.removeSetting("favToDelete")
                logAction("Command -> Deleted Favorite Virtual Switch: [${favName}]")
            }
            if (btn == "btnUpdateFavVol" && settings.favToEdit && settings.newFavVol != null) {
                def dni = settings.favToEdit
                def newVol = settings.newFavVol
                if (state.savedFavorites[dni]) {
                    state.savedFavorites[dni].vol = newVol
                    def favName = state.savedFavorites[dni].name
                    logAction("Command -> Updated volume for Favorite [${favName}] to ${newVol}%.")
                    
                    // Clear the fields after saving
                    app.removeSetting("favToEdit")
                    app.removeSetting("newFavVol")
                }
            }
        }
    }
}

// --- VOLUME FADING ENGINE ---

def startVolumeFadeWrapper(data) {
    startVolumeFade(data.spkId, data.targetVol, data.durationSec, data.isFadeOut)
}

def startVolumeFade(spkId, targetVol, durationSec, isFadeOut = false) {
    def spk = getSpeakerById(spkId)
    if (!spk) return
    def curVol = spk.currentValue("volume") ?: 0
    if (curVol == targetVol) return
    
    def hasNativeFade = false
    if (spk.hasCommand("setLevel")) {
        def setLevelCmd = spk.getSupportedCommands().find { it.name == "setLevel" }
        if (setLevelCmd && setLevelCmd.arguments?.size() > 1) {
            hasNativeFade = true
        }
    }
    
    if (hasNativeFade) {
        logAction("Using native hardware volume fade for ${spk.label}")
        spk.setLevel(targetVol, durationSec)
        if (isFadeOut) {
            runIn(durationSec + 1, "finalizeNativeFadeOut", [data: [spkId: spk.id, origVol: curVol], overwrite: false])
        }
        return
    }
    
    def stepDelay = 3
    def steps = Math.max((durationSec / stepDelay).toInteger(), 1)
    
    if (steps > 5) {
        steps = 5
        stepDelay = (durationSec / steps).toInteger()
    }
    
    def volDiff = targetVol - curVol
    def stepAmount = volDiff / steps

    state["fade_${spkId}"] = [
        current: curVol, target: targetVol, stepAmt: stepAmount, 
        stepsLeft: steps, isFadeOut: isFadeOut, origVol: curVol
    ]
    
    runIn(stepDelay, "processVolumeFade", [data: [spkId: spkId, delay: stepDelay], overwrite: false])
}

def finalizeNativeFadeOut(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        spk.pause()
        logAction("Fade-out complete for ${spk.label}. Paused.")
        runIn(2, "restoreVolume", [data: [spkId: data.spkId, origVol: data.origVol], overwrite: false])
    }
}

def processVolumeFade(data) {
    def spkId = data.spkId
    def spk = getSpeakerById(spkId)
    def fadeData = state["fade_${spkId}"]
    if (!spk || !fadeData) return
    
    fadeData.stepsLeft = fadeData.stepsLeft - 1
    fadeData.current = fadeData.current + fadeData.stepAmt
    
    def newVol = fadeData.current.toInteger()
    if (newVol < 0) newVol = 0
    if (newVol > 100) newVol = 100
    
    spk.setLevel(newVol)
    
    if (fadeData.stepsLeft > 0) {
        state["fade_${spkId}"] = fadeData
        runIn(data.delay, "processVolumeFade", [data: [spkId: spkId, delay: data.delay], overwrite: false])
    } else {
        spk.setLevel(fadeData.target)
        if (fadeData.isFadeOut) {
            spk.pause()
            logAction("Software Fade-out complete for ${spk.label}. Paused.")
            runIn(2, "restoreVolume", [data: [spkId: spkId, vol: fadeData.origVol], overwrite: false]) 
        } else {
            logAction("Software Fade-in complete for ${spk.label}.")
        }
        state.remove("fade_${spkId}")
    }
}

// --- BMS HARDWARE PROTECTION ---

def volumeHandler(evt) {
    if (!isAppEnabled()) return
    def spk = evt.device
    def vol = evt.value.toInteger()
    
    def maxZones = settings.numZones ?: 5
    for (int i = 1; i <= maxZones; i++) {
        if (settings["z${i}Speaker"]?.id == spk.id) {
            def maxVol = settings["z${i}MaxVol"]
            if (maxVol && vol > maxVol) {
                logAction("Hardware Protection Activated! Reduced ${spk.label} from ${vol}% to ${maxVol}%.")
                spk.setLevel(maxVol)
            }
            break
        }
    }
}

// --- EVENT OVERRIDES ---

def doorbellHandler(evt) {
    if (!isAppEnabled() || !settings.enableEventOverrides) return
    def btnNum = settings.doorbellButtonNum ?: 1
    
    if (evt.value == btnNum.toString()) {
        logAction("Doorbell rang! Muting applicable playing speakers.")
        def mutedSpks = []
        
        def maxZones = settings.numZones ?: 5
        for(int i = 1; i <= maxZones; i++) {
            def spk = settings["z${i}Speaker"]
            if (spk && !isZoneLocked(i)) {
                if (spk.currentValue("status") == "playing" && spk.currentValue("mute") != "muted") {
                    spk.mute()
                    mutedSpks << spk.id
                }
            }
        }
        state.doorbellMutedSpks = mutedSpks
        runIn(settings.doorbellMuteTime ?: 30, "restoreDoorbellMute", [overwrite: false])
    }
}

def restoreDoorbellMute() {
    state.doorbellMutedSpks?.each { id -> getSpeakerById(id)?.unmute() }
    state.doorbellMutedSpks = []
    logAction("Doorbell mute timeout finished. Restoring volumes.")
}

def doorHandler(evt) {
    if (!isAppEnabled() || !settings.enableEventOverrides) return
    def anyOpen = settings.doorSensors.any { it.currentValue("contact") == "open" }
    
    if (anyOpen) {
        if (!state.doorOpenMuted) {
            logAction("Monitored door opened! Muting applicable speakers.")
            def mutedSpks = []
            def maxZones = settings.numZones ?: 5
            for(int i = 1; i <= maxZones; i++) {
                def spk = settings["z${i}Speaker"]
                if (spk && !isZoneLocked(i)) {
                    if (spk.currentValue("status") == "playing" && spk.currentValue("mute") != "muted") {
                        spk.mute()
                        mutedSpks << spk.id
                    }
                }
            }
            state.doorOpenMuted = true
            state.doorOpenMutedSpks = mutedSpks
        }
    } else {
        if (state.doorOpenMuted) {
            logAction("Doors closed. Restoring volumes.")
            state.doorOpenMutedSpks?.each { id -> getSpeakerById(id)?.unmute() }
            state.doorOpenMuted = false
            state.doorOpenMutedSpks = []
        }
    }
}

// --- DYNAMIC AMBIENT NOISE COMPENSATION ---

def ambientNoiseHandler(evt) {
    if (!isAppEnabled() || !settings.enableAmbientComp) return
    if (settings.ambientModes && !(settings.ambientModes as List).contains(location.mode)) return
    
    def delaySecs = settings.ambientDelay != null ? settings.ambientDelay : 60
    runIn(delaySecs, "evaluateAmbientNoise", [overwrite: true])
}

def evaluateAmbientNoise() {
    if (!isAppEnabled() || !settings.enableAmbientComp) return
    if (settings.ambientModes && !(settings.ambientModes as List).contains(location.mode)) return

    def requiredBoost = 0
    
    if (settings.ambientDishwasher && settings.ambientDishwasher.currentValue("switch") == "on") {
        requiredBoost = Math.max(requiredBoost, settings.ambientDishwasherBoost ?: 10)
    }
    if (settings.ambientRobovac && settings.ambientRobovac.currentValue("switch") == "on") {
        requiredBoost = Math.max(requiredBoost, settings.ambientRobovacBoost ?: 15)
    }
    if (settings.ambientDehum && settings.ambientDehum.currentValue("switch") == "on") {
        requiredBoost = Math.max(requiredBoost, settings.ambientDehumBoost ?: 10)
    }
    if (settings.ambientHVAC) {
        def hvacState = settings.ambientHVAC.currentValue("thermostatOperatingState")?.toLowerCase()
        if (hvacState == "cooling" || hvacState == "heating" || hvacState == "fan only") {
            requiredBoost = Math.max(requiredBoost, settings.ambientHVACBoost ?: 12)
        }
    }
    
    if (requiredBoost > 0) {
        if (state.currentBoostAmt == requiredBoost) return 
        
        logAction("Ambient Noise Detected! Evaluating allowed zones for a +${requiredBoost}% volume boost.")
        
        settings.ambientZones?.each { zStr ->
            def zNum = zStr.toInteger()
            if (isZoneLocked(zNum)) return
            
            def spk = settings["z${zNum}Speaker"]
            if (spk && spk.currentValue("status")?.toLowerCase() == "playing") {
                def curVol = spk.currentValue("volume")?.toInteger() ?: 0
                def maxCap = settings.ambientMaxCap ?: 80
                
                if (curVol >= maxCap) {
                    logAction("Ambient Noise: ${spk.label} is already at or above cap (${curVol}% >= ${maxCap}%). Skipping boost.")
                    return
                }
                
                if (state.preBoostVols[zStr] == null) {
                    state.preBoostVols[zStr] = curVol
                }
                
                def baseVol = state.preBoostVols[zStr]
                def newVol = baseVol + requiredBoost
                if (newVol > maxCap) newVol = maxCap
                
                spk.setLevel(newVol)
                logAction("Ambient Noise: Boosted ${spk.label} to ${newVol}% (Base: ${baseVol}%).")
            }
        }
        state.currentBoostAmt = requiredBoost
    } else {
        if (state.currentBoostAmt > 0) {
            logAction("Ambient Noise Cleared. Reverting boosted zones to original volumes.")
            settings.ambientZones?.each { zStr ->
                def zNum = zStr.toInteger()
                def spk = settings["z${zNum}Speaker"]
                def origVol = state.preBoostVols[zStr]
                
                if (spk && origVol != null) {
                    spk.setLevel(origVol)
                    logAction("Ambient Noise: Reverted ${spk.label} to ${origVol}%.")
                }
            }
            state.preBoostVols = [:]
            state.currentBoostAmt = 0
        }
    }
}

def restoreVolume(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) spk.setLevel(data.vol)
}

def executeSleepTimer(data) {
    def zNumSafe = data.zNum.toInteger()
    if (!state.sleepTimers[zNumSafe]) return
    
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        if (data.fade && data.fade > 0) {
            logAction("Sleep Timer Executed: Initiating Fade-Out for ${spk.label}.")
            startVolumeFade(spk.id, 0, data.fade, true)
        } else {
            spk.pause()
            logAction("Sleep Timer Executed: Paused ${spk.label}.")
        }
    }
    state.sleepTimers[zNumSafe] = false
}

// --- FAVORITES & VIRTUAL SWITCH GENERATOR ---

def createFavoriteVirtualSwitch(speaker) {
    if (settings.enableFavorites == false) return

    def trackUri = speaker.currentValue("trackUri")
    def trackDataStr = speaker.currentValue("trackData")
    
    if (!trackUri && trackDataStr) {
        try {
            def json = new groovy.json.JsonSlurper().parseText(trackDataStr)
            trackUri = json?.trackUri ?: json?.enqueuedUri ?: json?.uri
        } catch (e) { }
    }
    
    if (!trackUri) trackUri = speaker.currentValue("uri")
    if (!trackUri) trackUri = speaker.currentValue("enqueuedUri")

    def trackName = speaker.currentValue("trackDescription")
    if (!trackName || trackName.trim() == "") trackName = speaker.currentValue("name")
    if (!trackName || trackName.trim() == "") {
        if (trackDataStr) {
            try {
                def json = new groovy.json.JsonSlurper().parseText(trackDataStr)
                trackName = json?.station ?: json?.title ?: ""
            } catch (e) { }
        }
    }
    if (!trackName || trackName.trim() == "") trackName = "Custom Stream (${now().toString().substring(8)})"
    
    def curVol = speaker.currentValue("volume") ?: 20
    
    if (!trackUri) {
        logAction("ERROR: Cannot create favorite. No track URI detected on ${speaker.label}.")
        return
    }

    def safeName = trackName.replaceAll("[^a-zA-Z0-9 ]", "").trim()
    if (safeName.length() > 30) safeName = safeName.substring(0, 30)

    def dni = "SONOS_FAV_${app.id}_${now()}"
    def label = "Sonos Fav - ${safeName}"
    
    try {
        def child = addChildDevice("hubitat", "Virtual Switch", dni, [label: label, name: label, isComponent: false])
        if (!state.savedFavorites) state.savedFavorites = [:]
        
        state.savedFavorites[dni] = [uri: trackUri, speakerId: speaker.id, name: trackName, vol: curVol, timestamp: now()]
        subscribe(child, "switch", childSwitchHandler)
        logAction("SUCCESS: Created Virtual Switch [${label}] to recall current track at ${curVol}%.")
    } catch (e) { logAction("ERROR: Failed to create virtual switch. Check hub logs.") }
}

def childSwitchHandler(evt) {
    if (settings.enableFavorites == false) return

    if (evt.value == "on") {
        def dni = evt.device.deviceNetworkId
        def favData = state.savedFavorites[dni]
        
        if (favData) {
            def speaker = getSpeakerById(favData.speakerId)
            if (speaker) {
                logAction("Triggered Favorite via Switch. Setting volume to ${favData.vol}% and playing [${favData.name}] on ${speaker.label}")
                if (favData.vol != null) speaker.setLevel(favData.vol)
                speaker.setTrack(favData.uri)
                runIn(2, "triggerPlayOnFav", [data: [spkId: speaker.id], overwrite: false])
            }
        }
        runIn(3, "turnOffChild", [data: [dni: dni], overwrite: false])
    }
}

def triggerPlayOnFav(data) { getSpeakerById(data.spkId)?.play() }
def turnOffChild(data) { getChildDevice(data.dni)?.off() }

def getSpeakerById(id) { 
    def maxZones = settings.numZones ?: 5
    for (int i = 1; i <= maxZones; i++) { 
        if (settings["z${i}Speaker"]?.id == id) return settings["z${i}Speaker"] 
    }
    return null 
}

// --- POWER MANAGEMENT LOGIC & SELF-HEALING ENGINE ---

def hourlyStateEnforcement() {
    if (!isAppEnabled() || settings.enablePowerManagement == false) return
    def currentMode = location.mode?.toString()
    def delayMult = 0
    def zonesFixed = false
    
    def maxZones = settings.numZones ?: 5
    for (int i = 1; i <= maxZones; i++) {
        if (settings["enableZ${i}"] && settings["z${i}Switch"]) {
            if (isZoneLocked(i)) continue
            
            def sw = settings["z${i}Switch"]
            def onModes = [settings["z${i}TurnOnModes"]].flatten().findAll { it }
            def offModes = [settings["z${i}TurnOffModes"]].flatten().findAll { it }
            def currentState = sw.currentValue("switch")
            
            if (onModes && onModes.contains(currentMode) && currentState != "on") {
                def delay = delayMult * 6 + 1
                runIn(delay, "enforcePowerState", [data: [zNum: i, state: "on"], overwrite: false])
                delayMult++
                zonesFixed = true
            } else if (offModes && offModes.contains(currentMode) && currentState != "off") {
                def delay = delayMult * 6 + 1
                runIn(delay, "enforcePowerState", [data: [zNum: i, state: "off"], overwrite: false])
                delayMult++
                zonesFixed = true
            }
        }
    }
    if (zonesFixed) {
        logAction("Hourly Enforcement: Correcting missing power states...")
    }
}

def enforcePowerState(data) {
    def sw = settings["z${data.zNum.toInteger()}Switch"]
    if (sw) {
        if (data.state == "on") {
             sw.on()
            logAction("Hourly Enforcement: Turned ON Zone ${data.zNum}.")
        } else {
            sw.off()
            logAction("Hourly Enforcement: Turned OFF Zone ${data.zNum}.")
        }
    }
}

def modeChangeHandler(evt) {
    if (!isAppEnabled() || settings.enablePowerManagement == false) return
    syncSystemToMode(evt.value, "Mode Change")
}

def systemStartHandler(evt) {
    if (!isAppEnabled() || settings.enablePowerManagement == false || settings.enableSelfHealing == false) return
    logAction("🔄 Hub Reboot/Power Outage Detected. Waiting 60s for mesh networks to settle before Self-Healing Sync...")
    runIn(60, "executeSelfHealingSync", [overwrite: false])
}

def executeSelfHealingSync() {
    logAction("🔄 Initiating Self-Healing Sync now...")
    syncSystemToMode(location.mode?.toString(), "Self-Healing")
}

def syncSystemToMode(currentMode, triggerSource) {
    def zonesToTurnOn = []
    def zonesToTurnOff = []

    def maxZones = settings.numZones ?: 5
    for (int i = 1; i <= maxZones; i++) {
        if (settings["enableZ${i}"]) {
            if (isZoneLocked(i)) {
                logAction("${triggerSource} Engine skipping Zone ${i} (Good Night Override Active).")
                continue
            }
            
            def onModes = [settings["z${i}TurnOnModes"]].flatten().findAll { it }
            def offModes = [settings["z${i}TurnOffModes"]].flatten().findAll { it }
            
            if (onModes && onModes.contains(currentMode)) zonesToTurnOn << i
            else if (offModes && offModes.contains(currentMode)) zonesToTurnOff << i
        }
    }

    if (zonesToTurnOn) {
        logAction("${triggerSource}: Mode is ${currentMode}. Initiating Startup sequence for active zones.")
        powerUpSpecificZones(zonesToTurnOn)
    } 
    
    if (zonesToTurnOff) {
        logAction("${triggerSource}: Mode is ${currentMode}. Initiating Failsafe Pause & Shutdown for inactive zones.")
        gracefulShutdownSpecificZones(zonesToTurnOff)
    }
}

def powerUpSpecificZones(zones) {
    def delayMult = 0
    zones.each { i ->
        if (settings["z${i}Switch"]) {
            def delay = delayMult * 6 + 1
            runIn(delay, "executeStaggeredPowerOn", [data: [zNum: i], overwrite: false])
            delayMult++
        }
    }
    if(zones) logAction("Master power switches scheduled to turn ON (Staggered).")
}

def executeStaggeredPowerOn(data) {
    def i = data.zNum.toInteger()
    def sw = settings["z${i}Switch"]
    def spk = settings["z${i}Speaker"]
    
    // Turn on the relay first
    if (sw) sw.on()
    
    // Schedule Volume and Resume with a 60-second delay to ensure speaker is online
    if (spk) {
        if (settings["z${i}StartVol"]) {
            def targetVol = settings["z${i}StartVol"]
            runIn(60, "setStartupVolume", [data: [spkId: spk.id, vol: targetVol], overwrite: false])
        }
        
        if (settings["z${i}AutoResume"]) {
            runIn(60, "triggerAutoResume", [data: [spkId: spk.id], overwrite: false])
        }
    }
}

def gracefulShutdownSpecificZones(zones) {
    def commandsSent = false
    zones.each { i ->
        if (settings["z${i}Speaker"] && settings["z${i}Switch"]) {
            if (settings["z${i}Switch"].currentValue("switch") == "on" && settings["z${i}Speaker"].currentValue("status") == "playing") {
                settings["z${i}Speaker"].pause()
                commandsSent = true
                logAction("Failsafe: Paused ${settings['z${i}Speaker'].label} prior to power cut.")
            }
        }
    }
    runIn(commandsSent ? 5 : 1, "executePowerCutSpecificZones", [data: [zonesToCut: zones], overwrite: false])
}

def executePowerCutSpecificZones(data) {
    def zones = data.zonesToCut
    def delayMult = 0
    zones.each { i ->
        if (settings["z${i}Switch"]) {
            def delay = delayMult * 6 + 1
            runIn(delay, "executeStaggeredPowerOff", [data: [zNum: i], overwrite: false])
            delayMult++
        }
    }
    logAction("Master power switches successfully scheduled to turn OFF (Staggered).")
}

def executeStaggeredPowerOff(data) {
    settings["z${data.zNum.toInteger()}Switch"]?.off()
}

def setStartupVolume(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        spk.setLevel(data.vol)
        logAction("Startup Volume Normalization: Set ${spk.label} to ${data.vol}%")
    }
}

def triggerAutoResume(data) {
    def spk = getSpeakerById(data.spkId)
    if (spk) {
        spk.play()
        logAction("Auto-Resume: Resumed playback on ${spk.label} after boot up.")
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
