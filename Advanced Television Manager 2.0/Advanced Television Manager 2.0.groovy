/**
 * Advanced Television Manager 2.0
 */
definition(
    name: "Advanced Television Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleThreaded: true
)

preferences {
    page(name: "mainPage")
    page(name: "tvPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            if (numTVs > 0) {
                input "btnRefresh", "button", title: "🔄 Refresh Data"
                
                def globalStatus = (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") ?
                    "<span style='color: red; font-weight: bold;'>PAUSED</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
                
                paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                          "<b>System Status:</b> ${globalStatus}</div>"
                          
                input "btnForceOffAll", "button", title: "🛑 Force Off All TVs"

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
                    <thead><tr><th>Television</th><th>State & Acoustics</th><th>Watch Time</th><th>Media & Telemetry</th></tr></thead>
                    <tbody>
                """
                
                def nowTime = new Date().time
                
                for (int i = 1; i <= (numTVs as Integer); i++) {
                    def tvName = settings["tvName_${i}"] ?: "TV ${i}"
                    def tv = getPrimaryDevice(i)
                    
                    if (!tv) {
                        dashHTML += "<tr><td class='dash-hl'>${tvName}</td><td colspan='3' style='color:#888;'>Not Configured</td></tr>"
                        continue
                    }
               
                    def isTrulyOn = isTvActuallyOn(tv, i)
                    def powerState = isTrulyOn ? "ON" : "STANDBY / OFF"
                    def pwrColor = isTrulyOn ? "green" : "red"
                    def tvOnTime = state.tvOnTime?."${i}" ?: nowTime
                    
                    def currentApp = "Unknown"
                    if (isTrulyOn) {
                        currentApp = tv.currentValue("application") ?: tv.currentValue("mediaInputSource") ?: "Unknown"
                    } else {
                        currentApp = "Screen Off"
                    }

                    // --- Mood Formatting ---
                    def viewerMoods = getViewerMoodsHtml(i)
                    def isSick = isAnyoneSick(i)
                    def tvNameCell = "<b>${tvName}</b>"
                    if (viewerMoods) tvNameCell += "<br><div style='margin-top:4px;'>${viewerMoods}</div>"
                    
                    // --- Acoustic Management Live Data ---
                    def acousticText = ""
                    if (settings["enableAcousticMgmt_${i}"]) {
                        def activeAcoustics = []
                        
                        if (isSick) {
                            activeAcoustics << "<span style='color:#c0392b; font-weight:bold;'>Suspended (Sick Mode)</span>"
                        } else {
                            def thermo = settings["mainThermostat_${i}"]
                            if (thermo) {
                                def tState = thermo.currentValue("thermostatOperatingState")
                                if (tState in ["heating", "cooling", "fan only"]) {
                                    def st_dev = state.noiseStart?."${i}_hvac" ?: nowTime
                                    def delayReq = (st_dev as Long) < (tvOnTime as Long) ? 300000 : 0 // HVAC is instant if TV is already on
                                    def st = Math.max(st_dev as Long, tvOnTime as Long)
                                    def pending = (nowTime - st) < delayReq
                                    def delayLabel = delayReq == 300000 ? "Pending Delay (5m)" : "Pending Delay"
                                    if (pending) activeAcoustics << "HVAC (${tState.capitalize()}): ${delayLabel}"
                                    else activeAcoustics << "HVAC (${tState.capitalize()}): +${settings["hvacVolumeBoost_${i}"] ?: 3}"
                                } else activeAcoustics << "HVAC (Idle)"
                            }
                            
                            def dish = settings["dishwasher_${i}"]
                            if (dish) {
                                def dishPwr = 0.0
                                try { dishPwr = (dish.currentValue("power") ?: 0.0) as Float } catch(e) {}
                                def dThresh = (settings["dishwasherThreshold_${i}"] ?: 15) as Float
                                def debounceMins = (settings["dishwasherDebounce_${i}"] ?: 5) as Long
                                def lastActive = state."dishLastActive_${i}" ?: 0
                                def isDebouncing = (dishPwr <= dThresh) && ((nowTime - lastActive) < (debounceMins * 60000))
                                
                                if (dishPwr > dThresh || isDebouncing) {
                                    def st_dev = state.noiseStart?."${i}_dish" ?: nowTime
                                    def delayReq = (st_dev as Long) < (tvOnTime as Long) ? 300000 : 58000
                                    def st = Math.max(st_dev as Long, tvOnTime as Long)
                                    def pending = (nowTime - st) < delayReq
                                    def pwrStr = dishPwr > dThresh ? "${dishPwr}W" : "Paused"
                                    def delayLabel = delayReq == 300000 ? "Pending Delay (5m)" : "Pending Delay"
                                    if (pending) activeAcoustics << "Dishwasher (${pwrStr}): ${delayLabel}"
                                    else activeAcoustics << "Dishwasher (${pwrStr}): +${settings["dishwasherBoost_${i}"] ?: 4}"
                                } else {
                                    activeAcoustics << "Dishwasher (Idle)"
                                }
                            }
                            
                            def vac = settings["vacuum_${i}"]
                            if (vac) {
                                if (vac.currentValue("switch") == "on") {
                                    def st_dev = state.noiseStart?."${i}_vac" ?: nowTime
                                    def delayReq = (st_dev as Long) < (tvOnTime as Long) ? 300000 : 58000
                                    def st = Math.max(st_dev as Long, tvOnTime as Long)
                                    def pending = (nowTime - st) < delayReq
                                    def delayLabel = delayReq == 300000 ? "Pending Delay (5m)" : "Pending Delay"
                                    if (pending) activeAcoustics << "Vacuum (ON): ${delayLabel}"
                                    else activeAcoustics << "Vacuum (ON): +${settings["vacuumBoost_${i}"] ?: 10}"
                                } else activeAcoustics << "Vacuum (OFF)"
                            }
                            
                            def ap = settings["airPurifier_${i}"]
                            if (ap) {
                                if (ap.currentValue("switch") == "on") {
                                    def sickSwitches = [settings["sickModeSwitch_${i}"]].flatten().findAll{it}
                                    def sickModeOn = sickSwitches.any { it.currentValue("switch") == "on" }
                                    def modeTag = sickModeOn ? " (Sick Mode)" : ""
                                    
                                    def st_dev = state.noiseStart?."${i}_ap" ?: nowTime
                                    def delayReq = (st_dev as Long) < (tvOnTime as Long) ? 300000 : 58000
                                    def st = Math.max(st_dev as Long, tvOnTime as Long)
                                    def pending = (nowTime - st) < delayReq
                                    def delayLabel = delayReq == 300000 ? "Pending Delay (5m)" : "Pending Delay"
                                    if (pending) activeAcoustics << "Purifier (ON)${modeTag}: ${delayLabel}"
                                    else activeAcoustics << "Purifier (ON)${modeTag}: +${settings["airPurifierBoost_${i}"] ?: 2}"
                                } else activeAcoustics << "Purifier (OFF)"
                            }
                            
                            def dehum = settings["dehumidifier_${i}"]
                            if (dehum) {
                                if (dehum.currentValue("switch") == "on") {
                                    def st_dev = state.noiseStart?."${i}_dehum" ?: nowTime
                                    def delayReq = (st_dev as Long) < (tvOnTime as Long) ? 300000 : 58000
                                    def st = Math.max(st_dev as Long, tvOnTime as Long)
                                    def pending = (nowTime - st) < delayReq
                                    def delayLabel = delayReq == 300000 ? "Pending Delay (5m)" : "Pending Delay"
                                    if (pending) activeAcoustics << "Dehum (ON): ${delayLabel}"
                                    else activeAcoustics << "Dehum (ON): +${settings["dehumidifierBoost_${i}"] ?: 3}"
                                } else activeAcoustics << "Dehum (OFF)"
                            }
                        }
                        
                        if (activeAcoustics.size() > 0) {
                            def currentBoost = state.currentVolumeBoost?."${i}" ?: 0
                            def boostDisplay = (isTrulyOn && !isSick) ? "<strong style='color:#c0392b;'>Active Boost: +${currentBoost}</strong>" : ""
                            if (!isTrulyOn) boostDisplay = "<span style='color:#7f8c8d;'>(TV OFF - Suspended)</span>"
                            acousticText = "<div style='margin-top: 6px; padding-top: 6px; border-top: 1px dotted #ccc; font-size:11px; color:#555;'><b>Acoustics:</b> ${activeAcoustics.join(' | ')}<br>${boostDisplay}</div>"
                        }
                    }
                    
                    def watchMins = state.watchTimeToday?."${i}" ?: 0
                    def watchDisplay = "${(watchMins / 60).toInteger()}h ${watchMins % 60}m"
                    
                    // --- Time Limit Extensions ---
                    if (settings["enableTimeLimits_${i}"]) {
                        def isGuestMode = settings["globalGuestSwitch"]?.currentValue("switch") == "on"
                        def maxTv = settings["tvMaxLimitMins_${i}"]
                        def ext = state.tvTimeExtended?."${i}" ?: 0
                        def limitText = ""
                        
                        if (isGuestMode) {
                            limitText += "<br><span style='color:#27ae60; font-size:11px;'><b>🎉 Guest Mode Bypassed</b></span>"
                        } else {
                            if (maxTv) {
                                def totalAllowedTv = maxTv + ext
                                def remainTv = totalAllowedTv - watchMins
                                if (remainTv < 0) remainTv = 0
                                limitText += "<br><span style='color:#e67e22; font-size:11px;'>TV Limit: ${(remainTv/60).toInteger()}h ${remainTv%60}m left</span>"
                            }
                            
                            def limitedApps = settings["appLimitList_${i}"]
                            def appLimit = settings["appLimitMins_${i}"]
                            if (limitedApps && appLimit && isTrulyOn && limitedApps.contains(currentApp)) {
                                def appMins = settings["enforceGlobalAppLimits"] ? (state.globalAppTimeWatched?."${currentApp}" ?: 0) : (state.appTimeWatched?."${i}"?."${currentApp}" ?: 0)
                                def totalAllowedApp = appLimit + ext
                                def remainApp = totalAllowedApp - appMins
                                if (remainApp < 0) remainApp = 0
                                def scopeText = settings["enforceGlobalAppLimits"] ? "Global App Limit" : "App Limit"
                                limitText += "<br><span style='color:#c0392b; font-size:11px;'>${scopeText}: ${(remainApp/60).toInteger()}h ${remainApp%60}m left</span>"
                            }
                        }
                        watchDisplay += limitText
                    }
                    
                    def topApp = "None"
                    def topTime = 0
                    if (state.appStats?."${i}") {
                        state.appStats["${i}"].each { app, time ->
                            if (time > topTime) { topApp = app; topTime = time }
                        }
                    }
                    
                    def mediaText = "<b>Top:</b> ${topApp}"
                    if (settings["tvType_${i}"] == "Roku TV" && isTrulyOn) {
                        def tData = state.rokuTelemetry?."${i}"
                        if (tData) {
                            def appTag = tData.appName ? "App: ${tData.appName} (${tData.appId ?: '---'})" : (tData.appId ? "AppID: ${tData.appId}" : "AppID: ---")
                            def contentTag = tData.contentId ? "ID: ${tData.contentId}" : "ID: ---"
                            def typeTag = tData.mediaType ? "Type: ${tData.mediaType}" : ""
                            mediaText += "<div style='margin-top: 6px; padding: 4px; background-color: #e8f4f8; border: 1px solid #3498db; border-radius: 3px; font-size: 10px; font-family: monospace; color: #333;'><b>Roku ECP Telemetry:</b><br>${appTag}<br>${contentTag}<br>${typeTag}</div>"
                        }
                    }
                    
                    dashHTML += "<tr><td class='dash-hl'>${tvNameCell}</td><td><span style='color: ${pwrColor}; font-weight:bold;'>${powerState}</span><br><span style='font-size:11px; color:#555;'>${currentApp}</span>${acousticText}</td><td>${watchDisplay}</td><td>${mediaText}</td></tr>"
                    
                    // Conditionally append Mood/Mode Explanation Row
                    if (isSick) {
                        dashHTML += "<tr><td colspan='4' style='background-color: #f8d7da; color: #721c24; font-size: 11px; text-align: left; padding: 6px 15px; border-top:none;'><b>🤒 SICK / EXHAUSTED MODE ACTIVE:</b> Morning News and Daily Weather automations are automatically aborted to allow rest. Smart Acoustic Volume Boosting is suspended to maintain a quiet environment.</td></tr>"
                    } else if (viewerMoods) {
                        dashHTML += "<tr><td colspan='4' style='background-color: #e2e3e5; color: #383d41; font-size: 11px; text-align: left; padding: 6px 15px; border-top:none;'><b>MOOD TRACKING ACTIVE:</b> Standard automation routing and active acoustic rules apply.</td></tr>"
                    }
                }
                dashHTML += "</tbody></table>"
                paragraph dashHTML
                
            } else {
                paragraph "<i>Configure televisions below to see live system status.</i>"
            }
        }
        
        section("<b>Application History (Last 20 Events)</b>", hideable: true, hidden: true) {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. Logs will appear as the system takes action.</i>"
            }
            input "resetHistory", "button", title: "Clear Action History"
        }
        
        section("<b>BMS Integrity & System Robustness</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Elevates the application from simple convenience to a robust Building Management Engine, ensuring commands land safely and preventing apps from fighting each other.</div>"
            
            input "bmsPriorityLock", "capability.switch", title: "Global Priority Lock Switch", required: false
            paragraph "<div style='font-size:11px; color:#555; padding: 5px 10px; background-color: #f8f9fa; border-left: 3px solid #6c757d; margin-top: -10px; margin-bottom: 15px;'><b>System Behavior:</b> Turns ON automatically when Severe Weather modes are active. Use this switch as a restriction in other apps.</div>"
            
            input "bmsMeshJitter", "bool", title: "Enable Mesh Optimization (Surge Staggering)", defaultValue: false
            paragraph "<div style='font-size:11px; color:#555; padding: 5px 10px; background-color: #f8f9fa; border-left: 3px solid #6c757d; margin-top: -10px; margin-bottom: 15px;'><b>System Behavior:</b> Adds a random 500ms-2000ms delay between device commands during global events to prevent Zigbee/Z-Wave network flooding.</div>"
            
            input "bmsHeartbeat", "bool", title: "Enable Device Heartbeat & Retries", defaultValue: false
            paragraph "<div style='font-size:11px; color:#555; padding: 5px 10px; background-color: #f8f9fa; border-left: 3px solid #6c757d; margin-top: -10px; margin-bottom: 15px;'><b>System Behavior:</b> Actively verifies if TV power commands actually executed. It will retry the command up to 3 times.</div>"
            
            input "bmsNightlyMaintenance", "bool", title: "Enable Nightly Driver Maintenance", defaultValue: false
            paragraph "<div style='font-size:11px; color:#555; padding: 5px 10px; background-color: #f8f9fa; border-left: 3px solid #6c757d; margin-top: -10px; margin-bottom: 15px;'><b>System Behavior:</b> Forcefully calls refresh() and initialize() on all linked TV drivers every night at 3:00 AM.</div>"

            input "tvRefreshInterval", "number", title: "Active TV Polling Interval (Minutes)", defaultValue: 5, required: true
            paragraph "<div style='font-size:11px; color:#555; padding: 5px 10px; background-color: #f8f9fa; border-left: 3px solid #6c757d; margin-top: -10px; margin-bottom: 15px;'><b>System Behavior:</b> Sends a proactive <code>refresh()</code> command to all TVs to prevent them from reporting stale power or app states.</div>"
        }
        
        section("<b>Global Settings & Modes</b>", hideable: true, hidden: true) {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false, description: "Select a virtual switch to act as a global pause. If the switch is OFF, the entire TV application will halt."
            input "enableAutoInternetCheck", "bool", title: "Enable Automatic Internet Ping Check", defaultValue: true, description: "Will ping an external site to verify internet before launching web apps."
            input "internetStatusSwitch", "capability.switch", title: "Global Internet Status Switch (Optional Override)", required: false, description: "If ON, internet is forced true. Used by Morning and Weather routines to fallback to OTA Antennas."
            input "numTVs", "number", title: "Number of Televisions to Configure (1-10)", required: true, defaultValue: 1, range: "1..10", submitOnChange: true
            input "enforceGlobalAppLimits", "bool", title: "Enforce House-Wide Application Limits", defaultValue: false, description: "If enabled, time spent on restricted apps is combined across all TVs."
            input "globalGuestSwitch", "capability.switch", title: "Global Guest Mode Switch (Limit Bypass)", required: false, description: "When this switch is ON, all TV and Application Time Limits are temporarily ignored."
        }
        
        section("<b>Severe Weather & Emergency Override</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Forces configured TVs to power on and tune to a specific broadcast channel or streaming app during a severe weather alert.</div>"
            input "enableWeatherAlert", "bool", title: "<b>Enable Severe Weather Overrides</b>", defaultValue: false, submitOnChange: true
            if (enableWeatherAlert) {
                
                def tvOpts = [:]
                if (numTVs) {
                    for (int i = 1; i <= (numTVs as Integer); i++) {
                        tvOpts["${i}"] = settings["tvName_${i}"] ?: "TV ${i}"
                    }
                }
                input "weatherAlertTVs", "enum", title: "Select TVs to activate during Weather Alerts", options: tvOpts, multiple: true, required: false
                
                input "weatherSwitch", "capability.switch", title: "Virtual Storm / Weather Alert Switches", multiple: true, required: false
                input "weatherChannel", "text", title: "Emergency Broadcast Channel (OTA)", required: false
                input "weatherAppSwitch", "capability.switch", title: "OR Emergency App Switch", required: false
                input "weatherTimeout", "number", title: "Auto-Restore Timeout (Minutes)", defaultValue: 0, description: "Set to 0 to keep the TV on indefinitely."
                
                input "testStormBtn", "button", title: "Test Storm TV Alert (ON)"
                input "testStormOffBtn", "button", title: "Test Storm TV Alert (OFF)"
            }
        }
        
        if (numTVs > 0 && numTVs <= 10) {
            for (int i = 1; i <= (numTVs as Integer); i++) {
                def tvName = settings["tvName_${i}"] ?: "TV ${i}"
                section("<b>⚙️ ${tvName}</b>") {
                    href(name: "tvHref${i}", page: "tvPage", params: [tvNum: i], title: "Configure ${tvName}", description: "Click to set up routines, acoustic management, and tracking for this screen.")
                }
            }
        }
    }
}

def tvPage(params) {
    def tNum = params?.tvNum ?: state.currentTV ?: 1
    state.currentTV = tNum
    def currentName = settings["tvName_${tNum}"] ?: "TV ${tNum}"
    
    dynamicPage(name: "tvPage", title: "${currentName} Setup", install: false, uninstall: false, previousPage: "mainPage") {
        
        section("<b>Identification</b>", hideable: true, hidden: true) {
            input "tvName_${tNum}", "text", title: "Custom TV Name", required: false, defaultValue: "TV ${tNum}", submitOnChange: true
        }
        
            section("<b>Biological & Mood Integration</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Links viewer moods to the TV. If a viewer is Sick/Exhausted (🤕, 🤒, 🩹, 🥶, 🥵, 🤢, 💩, 🤐), the TV will abort loud news routines and disable volume boosting to prioritize rest.</div>"
            for (int v = 1; v <= 3; v++) {
                input "viewerName${v}_${tNum}", "text", title: "Viewer ${v} Name (e.g., Shane)", required: false
                input "viewerMoodVar${v}_${tNum}", "hubVariable", title: "Link Mood Variable for Viewer ${v}", required: false
            }
          }
        
        section("<b>Control Devices</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Assigns the core hardware endpoints used to power, command, and adjust volume on the television screen.</div>"
            input "tvType_${tNum}", "enum", title: "Television Brand / Ecosystem", options: ["Roku TV", "LG WebOS", "Samsung Smart TV", "Sony Bravia", "Android TV / Google TV", "Apple TV", "Generic / Other"], defaultValue: "Roku TV", submitOnChange: true
            input "tv_${tNum}", "capability.switch", title: "Television Device", required: true
            input "tvPlug_${tNum}", "capability.switch", title: "Smart Plug / Relay Powering TV (Optional)", required: false
            input "tvPlugPowerMonitor_${tNum}", "bool", title: "Does this plug monitor energy (Watts)?", defaultValue: false, submitOnChange: true
            if (settings["tvPlugPowerMonitor_${tNum}"]) {
                input "tvPlugActiveWatts_${tNum}", "number", title: "Active ON Threshold (Watts)", defaultValue: 30, required: true
            }
            input "roomOccupancySwitch_${tNum}", "capability.switch", title: "Room Occupancy Virtual Switch (Force 5m Sync & Manage Ent Power)", required: false
            input "entSwitch_${tNum}", "capability.switch", title: "Pre-Requisite Entertainment Power Switch", required: false

            // --- Paired Roku Stick (Delegation) ---
            paragraph "<hr><b>External Media Player Delegation</b>"
            paragraph "<div style='font-size:12px; color:#555;'>Pair an external Roku stick alongside your main TV. Installed apps are auto-pulled directly from the device for selection below.</div>"
            input "enablePairedRoku_${tNum}", "bool", title: "<b>Pair an external Roku Streaming Stick?</b>", defaultValue: false, submitOnChange: true
            if (settings["enablePairedRoku_${tNum}"]) {
                input "pairedRoku_${tNum}", "capability.switch", title: "Paired Roku Device", required: true
                input "pairedRokuIp_${tNum}", "text", title: "Paired Roku IP Address (Auto-Discovered or Manual Override)", required: false, submitOnChange: true
                input "pairedRokuInput_${tNum}", "enum", title: "TV Input Choice for Roku", options: ["HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4", "AV", "Component", "InputHDMI1", "InputHDMI2", "InputHDMI3", "InputHDMI4"], defaultValue: "HDMI 1", required: true
                
                def pairedApps = state.pairedRokuApps?."${tNum}" ?: []
                paragraph "<b>Fetched Applications:</b><br>${pairedApps.size() > 0 ? pairedApps.join(', ') : '<i>No apps fetched yet. Click the button below to pull apps automatically.</i>'}"
                
                input "refreshPairedAppsBtn_${tNum}", "button", title: "🔄 Fetch / Refresh Apps from Paired Roku"
                
                if (pairedApps.size() > 0) {
                    input "rokuDelegatedApps_${tNum}", "enum", title: "Select Apps Handled by Paired Roku", options: pairedApps, multiple: true, submitOnChange: true
                }
            }

            // --- Roku Advanced LAN Telemetry ---
            if (settings["tvType_${tNum}"] == "Roku TV") {
                paragraph "<hr><i>Note: The app will automatically extract your main TV's IP Address from the device driver. You only need to type an IP below if auto-discovery fails.</i>"
                input "rokuIp_${tNum}", "text", title: "Roku IP Address (Optional Override)", required: false
            }
        }
        
        section("<b>Safety & Security Interruption (Smart Pause & Auto-Mute)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically pauses or mutes this specific TV when a monitored safety contact opens or a doorbell is pressed. Restores when clear.</div>"
            input "enableSafetyMute_${tNum}", "bool", title: "<b>Enable Security/Doorbell Interruption</b>", defaultValue: false, submitOnChange: true
            if (settings["enableSafetyMute_${tNum}"]) {
                input "muteContacts_${tNum}", "capability.contactSensor", title: "Safety Contacts", multiple: true, required: false
                input "doorbellButtons_${tNum}", "capability.pushableButton", title: "Doorbell Buttons", multiple: true, required: false
                input "doorbellMuteTime_${tNum}", "number", title: "Doorbell Interruption Duration (Seconds)", defaultValue: 60, required: true
            }
        }

        section("<b>Application & TV Time Limits</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Actively monitors watch-time limits and can automatically enforce screen-offs or redirect users to custom menus when daily quotas are reached.</div>"
            input "enableTimeLimits_${tNum}", "bool", title: "<b>Enable Time Limits</b>", defaultValue: false, submitOnChange: true
            if (settings["enableTimeLimits_${tNum}"]) {
                
                def savedAppsList = state.savedApps?."${tNum}" ?: []
                paragraph "<b>Saved Applications:</b><br>${savedAppsList.size() > 0 ? savedAppsList.join(', ') : '<i>No apps detected yet. Let the TV run.</i>'}"
                
                if (savedAppsList.size() > 0) {
                    input "appLimitList_${tNum}", "enum", title: "Select Apps to Limit", options: savedAppsList, multiple: true, submitOnChange: true
                    if (settings["appLimitList_${tNum}"]) {
                        input "appLimitMins_${tNum}", "number", title: "Time Limit for Selected Apps (Minutes/Day)", required: true
                        input "appLimitAction_${tNum}", "enum", title: "Action when limit reached", options: ["Turn Off TV", "Launch Specific App / Menu"], defaultValue: "Turn Off TV", submitOnChange: true
                        
                        if (settings["appLimitAction_${tNum}"] == "Launch Specific App / Menu") {
                            input "appLimitTargetMethod_${tNum}", "enum", title: "Launch Method", options: ["setApplication (Launch App)", "keyPress (Remote Button)"], defaultValue: "setApplication (Launch App)", submitOnChange: true
                            if (settings["appLimitTargetMethod_${tNum}"] == "setApplication (Launch App)") {
                                input "appLimitTargetApp_${tNum}", "enum", title: "Select Target App", options: savedAppsList, submitOnChange: true
                                input "appLimitCustomApp_${tNum}", "text", title: "OR Custom Target App", required: false
                            } else {
                                input "appLimitTargetKey_${tNum}", "text", title: "KeyPress Command", required: true, defaultValue: "Home"
                            }
                        }
                    }
                    
                    input "clearAppsBtn_${tNum}", "button", title: "Clear Entire Saved Apps List"
                    input "deleteApp_${tNum}", "enum", title: "Delete Individual App from List", options: savedAppsList, submitOnChange: true
                    if (settings["deleteApp_${tNum}"]) input "confirmDeleteAppBtn_${tNum}", "button", title: "Confirm Delete [${settings["deleteApp_${tNum}"]}]"
                }
                
                paragraph "<hr><b>Global TV Limits & Extensions</b>"
                input "tvMaxLimitMins_${tNum}", "number", title: "Maximum TV Limit (Minutes/Day)", required: false
                input "tvLimitAction_${tNum}", "enum", title: "Action when TV limit reached", options: ["Turn Off TV", "Launch Specific App / Menu"], defaultValue: "Turn Off TV", submitOnChange: true
                
                if (settings["tvLimitAction_${tNum}"] == "Launch Specific App / Menu") {
                    input "tvLimitTargetMethod_${tNum}", "enum", title: "Launch Method", options: ["setApplication (Launch App)", "keyPress (Remote Button)"], defaultValue: "setApplication (Launch App)", submitOnChange: true
                    if (settings["tvLimitTargetMethod_${tNum}"] == "setApplication (Launch App)") {
                        input "tvLimitTargetApp_${tNum}", "enum", title: "Select Target App", options: savedAppsList, submitOnChange: true
                        input "tvLimitCustomApp_${tNum}", "text", title: "OR Custom Target App", required: false
                    } else {
                        input "tvLimitTargetKey_${tNum}", "text", title: "KeyPress Command", required: true, defaultValue: "Home"
                    }
                }
                
                input "extend30mBtn_${tNum}", "button", title: "Extend Time by 30 Minutes"
                input "extend1hrBtn_${tNum}", "button", title: "Extend Time by 1 Hour"
                input "extendSwitch_${tNum}", "capability.switch", title: "Virtual Switch to Extend Time", required: false
            }
        }

        section("<b>TV Show Favorites (Auto-Tune & Turn Off)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Schedule up to 2 shows to automatically power on the TV, tune to the channel/app, and turn the TV off when the show ends.</div>"
            for (int s = 1; s <= 2; s++) {
                input "enableShow_${tNum}_${s}", "bool", title: "<b>Enable TV Show Schedule ${s}</b>", defaultValue: false, submitOnChange: true
                if (settings["enableShow_${tNum}_${s}"]) {
                    input "showName_${tNum}_${s}", "text", title: "Friendly Show Name (For Logging)", required: false
                    
                    if (settings["tvType_${tNum}"] == "Roku TV" || settings["enablePairedRoku_${tNum}"]) {
                        paragraph "<div style='background: #e8f4f8; border: 1px solid #3498db; padding: 10px; border-radius: 5px;'><b>🎯 Smart Capture</b><br>If your show is playing on the TV right now, click the button below to instantly extract and save the routing data.</div>"
                        input "captureFavoriteBtn_${tNum}_${s}", "button", title: "🎯 Auto-Capture Current Stream to Slot ${s}"
                        input "showIsDeepLink_${tNum}_${s}", "bool", title: "Use Deep-Link Routing", defaultValue: false, submitOnChange: true
                        
                        if (settings["enablePairedRoku_${tNum}"]) {
                            input "showRouteToPairedRoku_${tNum}_${s}", "bool", title: "Route this show to Paired Roku?", defaultValue: false, submitOnChange: true
                        }
                    }
                    
                    if (settings["showIsDeepLink_${tNum}_${s}"]) {
                        input "favoriteAppId_${tNum}_${s}", "text", title: "App ID", required: true
                        input "favoriteContentId_${tNum}_${s}", "text", title: "Content ID", required: false
                        input "favoriteMediaType_${tNum}_${s}", "text", title: "Media Type", required: false, defaultValue: "movie"
                    } else {
                        input "showChannel_${tNum}_${s}", "text", title: "Channel or Input", required: true
                    }
                    
                    input "showTimeStart_${tNum}_${s}", "time", title: "Show Start Time", required: true
                    input "showTimeEnd_${tNum}_${s}", "time", title: "Show End Time", required: true
                    input "showModes_${tNum}_${s}", "mode", title: "Only Run in These Modes", multiple: true, required: false
                    input "showDays_${tNum}_${s}", "enum", title: "Days of the Week", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                    input "testShowBtn_${tNum}_${s}", "button", title: "Test Start Show ${s} Now"
                }
            }
        }
        
        section("<b>Morning Dashboard / Routine</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically fires up the TV to a specific news/weather channel when motion is detected in the morning.</div>"
            input "enableMorningRoutine_${tNum}", "bool", title: "<b>Enable Morning Routine</b>", defaultValue: false, submitOnChange: true
            if (settings["enableMorningRoutine_${tNum}"]) {
                input "morningGoodNightSwitch_${tNum}", "capability.switch", title: "Good Night Virtual Switch (Must be OFF to run)", required: false
                input "morningMotion1_${tNum}", "capability.motionSensor", title: "Primary Motion Sensor", required: false
                input "morningMotion2_${tNum}", "capability.motionSensor", title: "Secondary Motion Sensor (Optional: Must trigger within 30s of Primary)", required: false
                
                input "morningTimeStart_${tNum}", "time", title: "Routine Allowed Start Time", required: false
                input "morningTimeEnd_${tNum}", "time", title: "Routine Allowed End Time", required: false
                input "morningDays_${tNum}", "enum", title: "Allowed Days", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                input "morningModes_${tNum}", "mode", title: "Allowed Modes", multiple: true, required: false
                
                input "morningSourceType_${tNum}", "enum", title: "Media Source Type", options: ["Live TV Channel Only", "App (Fallback to Live TV if no Internet)", "App Only"], defaultValue: "Live TV Channel Only", submitOnChange: true
                
                if (settings["morningSourceType_${tNum}"] == "Live TV Channel Only") {
                    input "morningChannel_${tNum}", "text", title: "Morning News Channel (OTA)", required: false
                } else if (settings["morningSourceType_${tNum}"] == "App (Fallback to Live TV if no Internet)") {
                    input "morningAppSwitch_${tNum}", "capability.switch", title: "Morning App Switch", required: false
                    input "morningChannel_${tNum}", "text", title: "Fallback Morning News Channel (OTA)", required: false
                } else {
                    input "morningAppSwitch_${tNum}", "capability.switch", title: "Morning App Switch", required: false
                }
                
                input "morningAutoMute_${tNum}", "bool", title: "Auto-Mute TV 30s after turning on?", defaultValue: false

                input "morningDuration_${tNum}", "number", title: "Routine Duration (Minutes)", required: false
                input "testMorningBtn_${tNum}", "button", title: "Test Morning Routine Now"
                input "testMorningOffBtn_${tNum}", "button", title: "Stop Morning Routine Test"
            }
        }

        section("<b>Morning/Evening Weather Routine</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically fires up the TV to the weather channel when motion is detected. Incorporates a 4-hour cooldown so it can gracefully run once in the morning and once in the evening.</div>"
            input "enableDailyWeatherRoutine_${tNum}", "bool", title: "<b>Enable Daily Weather Routine</b>", defaultValue: false, submitOnChange: true
            if (settings["enableDailyWeatherRoutine_${tNum}"]) {
                input "dailyWeatherMotion_${tNum}", "capability.motionSensor", title: "Trigger Motion Sensor", required: false
                input "dailyWeatherTimeStart_${tNum}", "time", title: "Routine Allowed Start Time", required: false
                input "dailyWeatherTimeEnd_${tNum}", "time", title: "Routine Allowed End Time", required: false
                input "dailyWeatherDays_${tNum}", "enum", title: "Allowed Days", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: false
                input "dailyWeatherModes_${tNum}", "mode", title: "Allowed Modes", multiple: true, required: false
                
                input "dailyWeatherSourceType_${tNum}", "enum", title: "Media Source Type", options: ["Live TV Channel Only", "App (Fallback to Live TV if no Internet)", "App Only"], defaultValue: "Live TV Channel Only", submitOnChange: true
                
                if (settings["dailyWeatherSourceType_${tNum}"] == "Live TV Channel Only") {
                    input "dailyWeatherChannel_${tNum}", "text", title: "Weather Channel (OTA)", required: false
                } else if (settings["dailyWeatherSourceType_${tNum}"] == "App (Fallback to Live TV if no Internet)") {
                    input "dailyWeatherAppSwitch_${tNum}", "capability.switch", title: "Weather App Switch", required: false
                    input "dailyWeatherChannel_${tNum}", "text", title: "Fallback Weather Channel (OTA)", required: false
                } else {
                    input "dailyWeatherAppSwitch_${tNum}", "capability.switch", title: "Weather App Switch", required: false
                }
                
                input "dailyWeatherAutoMute_${tNum}", "bool", title: "Auto-Mute TV 30s after turning on?", defaultValue: false

                input "dailyWeatherDuration_${tNum}", "number", title: "Routine Duration (Minutes)", required: false
                input "testDailyWeatherBtn_${tNum}", "button", title: "Test Weather Routine Now"
                input "testDailyWeatherOffBtn_${tNum}", "button", title: "Stop Weather Routine Test"
            }
        }

        section("<b>Acoustic Management (Environmental Sync)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Intelligently boosts your TV volume relative to loud background appliances (dishwashers, HVAC) to maintain clarity, or forcefully turns those appliances off while watching.</div>"
            input "enableAcousticMgmt_${tNum}", "bool", title: "<b>Enable Smart Acoustic Management</b>", defaultValue: false, submitOnChange: true
            if (settings["enableAcousticMgmt_${tNum}"]) {
                input "tvNoiseSwitches_${tNum}", "capability.switch", title: "FORCE OFF: Appliances to disable when TV runs", multiple: true, required: false
                
                paragraph "<b>Dynamic Volume Boost Engine</b>"
                input "mainThermostat_${tNum}", "capability.thermostat", title: "Room Thermostat", required: false
                input "hvacVolumeBoost_${tNum}", "number", title: "HVAC Volume Boost (Relative Units)", defaultValue: 3
                
                input "dishwasher_${tNum}", "capability.powerMeter", title: "Dishwasher Power Monitor", required: false
                if (settings["dishwasher_${tNum}"]) {
                    input "dishwasherThreshold_${tNum}", "number", title: "Active Power Threshold (Watts)", defaultValue: 15, required: true
                    input "dishwasherDebounce_${tNum}", "number", title: "Idle Debounce (Minutes)", defaultValue: 5, required: true
                    input "dishwasherBoost_${tNum}", "number", title: "Dishwasher Volume Boost (Units)", defaultValue: 4
                }
                
                input "vacuum_${tNum}", "capability.switch", title: "Robot Vacuum Switch / Power State", required: false
                input "vacuumBoost_${tNum}", "number", title: "Vacuum Volume Boost (Units)", defaultValue: 10
                
                input "airPurifier_${tNum}", "capability.switch", title: "Air Purifier Switch / Power State", required: false, submitOnChange: true
                if (settings["airPurifier_${tNum}"]) {
                    input "sickModeSwitch_${tNum}", "capability.switch", title: "Sick Mode / Air Quality Override Switches", multiple: true, required: false
                }
                input "airPurifierBoost_${tNum}", "number", title: "Air Purifier Volume Boost (Units)", defaultValue: 2
                
                input "dehumidifier_${tNum}", "capability.switch", title: "Dehumidifier Switch / Power State", required: false
                input "dehumidifierBoost_${tNum}", "number", title: "Dehumidifier Volume Boost (Units)", defaultValue: 3
            }
        }
        
        section("<b>Lighting & Environmental Sync (Auto-Sync)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically turns off designated lights when you start watching TV. (Lights will not automatically restore when TV turns off).</div>"
            input "enableLightingSync_${tNum}", "bool", title: "<b>Enable Environmental Sync</b>", defaultValue: false, submitOnChange: true
            if (settings["enableLightingSync_${tNum}"]) {
                input "tvLights_${tNum}", "capability.switch", title: "Target Lights to turn OFF", multiple: true, required: false
                input "enforceLightingSync_${tNum}", "bool", title: "Continuously Enforce (Keep lights off while TV is on)", defaultValue: false
                input "evaluateRoomBtn_${tNum}", "button", title: "Evaluate Room Now"
            }
        }

        section("<b>Cinema Intermission (Smart Pause)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically gently brightens selected lights when you pause a movie or show, then restores them when you hit play.</div>"
            input "enableIntermission_${tNum}", "bool", title: "<b>Enable Cinema Intermission</b>", defaultValue: false, submitOnChange: true
            if (settings["enableIntermission_${tNum}"]) {
                input "intermissionLights_${tNum}", "capability.switch", title: "Intermission Lights (Switches or Dimmers)", multiple: true, required: true
                input "intermissionLevel_${tNum}", "number", title: "Pause Brightness Level (Ignored for basic switches)", defaultValue: 25, required: true, range: "1..100"
                input "intermissionDelay_${tNum}", "number", title: "Pause Delay (Seconds) before lights turn up", defaultValue: 10, required: true
                
                paragraph "<b>Daylight Restrictions</b>"
                input "intermissionBlinds_${tNum}", "capability.contactSensor", title: "Room Blinds (If OPEN, pause lights won't turn on)", multiple: true, required: false
                input "intermissionOvercast_${tNum}", "capability.switch", title: "Overcast Virtual Switch (If ON, ignores the open blinds)", required: false
            }
        }

        section("<b>Accent & Fireplace Sync (Cozy Mode)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Turns ON specific accent lights (like a fireplace) to a desired level and color temp when the TV turns on, if conditions are right.</div>"
            input "enableCozyMode_${tNum}", "bool", title: "<b>Enable Cozy Mode</b>", defaultValue: false, submitOnChange: true
            if (settings["enableCozyMode_${tNum}"]) {
                input "cozyLights_${tNum}", "capability.colorTemperature", title: "Accent Lights (e.g., Fireplace)", multiple: true, required: false
                input "cozyLevel_${tNum}", "number", title: "Target Dim Level (%)", defaultValue: 50, required: true, range: "1..100"
                input "cozyCTVar_${tNum}", "string", title: "Hub Variable Name for Color Temp (Optional)", required: false
                input "cozyOvercast_${tNum}", "capability.switch", title: "Overcast Virtual Switch", required: false
                input "cozyBlinds_${tNum}", "capability.contactSensor", title: "Room Blinds (Closed = Active)", multiple: true, required: false
                input "cozyOffWithTv_${tNum}", "bool", title: "Turn OFF these lights when TV turns off?", defaultValue: true
            }
        }

        section("<b>Dynamic Glare Reduction (Active Sweeper)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Intelligently monitors adjacent environmental zones to eliminate screen glare. If peripheral lighting is active while the TV is running, this engine evaluates motion telemetry in those adjacent zones and automatically extinguishes the lights when the area becomes vacant.</div>"
            input "enableSweeper_${tNum}", "bool", title: "<b>Enable Dynamic Glare Reduction</b>", defaultValue: false, submitOnChange: true
            if (settings["enableSweeper_${tNum}"]) {
                input "sweepTimeout_${tNum}", "number", title: "Zone Vacancy Timeout (Minutes)", defaultValue: 3, required: true
                for (int l = 1; l <= 5; l++) {
                    input "sweepLight_${tNum}_${l}", "capability.switch", title: "Peripheral Light Zone ${l}", required: false
                    input "sweepMotion_${tNum}_${l}", "capability.motionSensor", title: "Zone ${l} Occupancy Sensor(s)", multiple: true, required: false
                }
            }
        }
        
        section("<b>Music & Audio Sync (Sonos)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically pauses whole-house or background music when you start watching TV, and resumes when done.</div>"
            input "enableMusicSync_${tNum}", "bool", title: "<b>Enable Music Sync</b>", defaultValue: false, submitOnChange: true
            if (settings["enableMusicSync_${tNum}"]) {
                input "sonos_${tNum}", "capability.musicPlayer", title: "Room Music Player (Sonos)", required: false
                input "sonosResumeModes_${tNum}", "mode", title: "Allowed Modes for Auto-Resume", multiple: true, required: false
                input "sonosResumeTimeStart_${tNum}", "time", title: "Auto-Resume Start Time", required: false
                input "sonosResumeTimeEnd_${tNum}", "time", title: "Auto-Resume End Time", required: false
            }
        }
        
        section("<b>Power Management & Motion Timeout</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically shuts off the TV if the room is empty to save power. If multiple sensors are selected, all must be inactive for the set time.</div>"
            input "enableMotionTimeout_${tNum}", "bool", title: "<b>Enable Inactivity Timeout</b>", defaultValue: false, submitOnChange: true
            if (settings["enableMotionTimeout_${tNum}"]) {
                input "visitorSwitch_${tNum}", "capability.switch", title: "Visitor / Guest Virtual Switch (Bypass Timeout)", required: false
                input "motionSensor_${tNum}", "capability.motionSensor", title: "Room Motion Sensor(s)", multiple: true, required: false
                input "motionTimeout_${tNum}", "number", title: "Timeout Delay (Minutes)", required: false
            }
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.watchTimeToday = state.watchTimeToday ?: [:]
    state.appStats = state.appStats ?: [:]
    state.historyLog = state.historyLog ?: []
    state.lastMotionTime = state.lastMotionTime ?: [:]
    state.morningRoutineRunDate = state.morningRoutineRunDate ?: [:]
    state.morningSensorTimes = state.morningSensorTimes ?: [:]
    state.dailyWeatherLastRun = state.dailyWeatherLastRun ?: [:]
    state.intermissionActive = state.intermissionActive ?: [:]        
    state.intermissionSavedState = state.intermissionSavedState ?: [:]
    state.weatherAlertActive = false
    state.evaluatedPowerState = state.evaluatedPowerState ?: [:]
    state.pausedSonos = state.pausedSonos ?: [:]
    state.noiseSwitchesPaused = state.noiseSwitchesPaused ?: [:]
    state.currentVolumeBoost = state.currentVolumeBoost ?: [:]
    state.cozyLightsActivatedByTv = state.cozyLightsActivatedByTv ?: [:]
    state.tvOnTime = state.tvOnTime ?: [:]
    state.noiseStart = state.noiseStart ?: [:]
    
    // Time limit states
    state.savedApps = state.savedApps ?: [:]
    state.pairedRokuApps = state.pairedRokuApps ?: [:]
    state.appTimeWatched = state.appTimeWatched ?: [:]
    state.globalAppTimeWatched = state.globalAppTimeWatched ?: [:] 
    state.tvTimeExtended = state.tvTimeExtended ?: [:]
    state.lastAppLogged = state.lastAppLogged ?: [:]
    
    // Power State Trackers
    state.plugWasOffBeforeShow = state.plugWasOffBeforeShow ?: [:]
    state.plugWasOffBeforeMorning = state.plugWasOffBeforeMorning ?: [:]
    state.plugWasOffBeforeDailyWeather = state.plugWasOffBeforeDailyWeather ?: [:]
    state.plugWasOffBeforeWeather = state.plugWasOffBeforeWeather ?: [:]
    
    state.tvWasOffBeforeMorning = state.tvWasOffBeforeMorning ?: [:]
    state.tvWasOffBeforeDailyWeather = state.tvWasOffBeforeDailyWeather ?: [:]
    state.tvWasOffBeforeShow = state.tvWasOffBeforeShow ?: [:]
    state.tvWasOffBeforeWeather = state.tvWasOffBeforeWeather ?: [:]
    
    // Telemetry Tracker
    state.rokuTelemetry = state.rokuTelemetry ?: [:]
    
    unschedule("trackUsageStep")
    unschedule("nightlyMaintenance")
    unschedule("refreshTVs")
    unschedule("occupancySync")
    
    trackUsageStep()
 
    def refMins = (settings["tvRefreshInterval"] ?: 5) as Integer
    if (refMins > 0 && refMins < 60) {
        schedule("0 0/${refMins} * * * ?", "refreshTVs")
    } else if (refMins >= 60) {
        schedule("0 0 * * * ?", "refreshTVs") // Fallback to hourly if an abnormally high value is set
    }
    
    runEvery5Minutes("occupancySync")
    
    schedule("0 0 0 * * ?", "midnightReset")
    schedule("0 * * * * ?", "checkTvShows")
    
    if (settings["bmsNightlyMaintenance"]) {
        schedule("0 0 3 * * ?", "nightlyMaintenance")
    }
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableTimeLimits_${i}"] && settings["extendSwitch_${i}"]) {
            subscribe(settings["extendSwitch_${i}"], "switch", extendSwitchHandler)
        }
    }
    
    if (settings["enableWeatherAlert"] && settings["weatherSwitch"]) {
        subscribe(settings["weatherSwitch"], "switch", weatherSwitchHandler)
    }

    subscribe(location, "voiceButlerTvCmd", "voiceButlerCmdHandler")
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        def plug = settings["tvPlug_${i}"]
        
        if (tv) {
            subscribe(tv, "switch", tvPowerEvaluator)
            subscribe(tv, "power", tvPowerEvaluator)
            subscribe(tv, "mediaInputSource", tvAppHandler)
            subscribe(tv, "application", tvAppHandler)
            subscribe(tv, "transportStatus", tvAppHandler)
        }
        
        if (plug) {
            subscribe(plug, "switch", tvPowerEvaluator)
            if (settings["tvPlugPowerMonitor_${i}"]) {
                subscribe(plug, "power", tvPowerEvaluator)
            }
        }
        
        if (settings["enableMotionTimeout_${i}"] && settings["motionSensor_${i}"]) {
            subscribe(settings["motionSensor_${i}"], "motion", tvMotionHandler)
        }
        
        if (settings["enableMorningRoutine_${i}"]) {
            if (settings["morningMotion1_${i}"]) subscribe(settings["morningMotion1_${i}"], "motion", morningMotionHandler)
            if (settings["morningMotion2_${i}"]) subscribe(settings["morningMotion2_${i}"], "motion", morningMotionHandler)
        }
        
        if (settings["enableDailyWeatherRoutine_${i}"] && settings["dailyWeatherMotion_${i}"]) {
            subscribe(settings["dailyWeatherMotion_${i}"], "motion", dailyWeatherMotionHandler)
        }
        
        if (settings["enableAcousticMgmt_${i}"]) {
            if (settings["mainThermostat_${i}"]) subscribe(settings["mainThermostat_${i}"], "thermostatOperatingState", acousticDeviceHandler)
            if (settings["dishwasher_${i}"]) subscribe(settings["dishwasher_${i}"], "power", acousticDeviceHandler)
            if (settings["vacuum_${i}"]) subscribe(settings["vacuum_${i}"], "switch", acousticDeviceHandler)
            if (settings["airPurifier_${i}"]) subscribe(settings["airPurifier_${i}"], "switch", acousticDeviceHandler)
            if (settings["dehumidifier_${i}"]) subscribe(settings["dehumidifier_${i}"], "switch", acousticDeviceHandler)
        }
        
        if (settings["enableCozyMode_${i}"] && settings["cozyOvercast_${i}"]) {
            subscribe(settings["cozyOvercast_${i}"], "switch", cozyOvercastHandler)
        }
        
        if (settings["enableSafetyMute_${i}"]) {
            if (settings["muteContacts_${i}"]) subscribe(settings["muteContacts_${i}"], "contact", contactHandler)
            if (settings["doorbellButtons_${i}"]) subscribe(settings["doorbellButtons_${i}"], "pushed", buttonHandler)
        }
    }
    
    runIn(2, "syncToVoiceButler", [overwrite: true])
}

// --- BIOLOGICAL / MOOD ENGINE ---
def getViewerMoodsHtml(i) {
    def html = ""
    for (int v = 1; v <= 3; v++) {
        def vName = settings["viewerName${v}_${i}"]
        def vVar = settings["viewerMoodVar${v}_${i}"]
        if (vName && vVar) {
            def mood = getGlobalVar(vVar)?.value ?: ""
            if (mood) html += "<div style='font-size: 11px; color: #555;'><b>${vName}:</b> <span style='font-size: 14px;'>${mood}</span></div>"
        }
    }
    return html
}

def isAnyoneSick(i) {
    def sickEmojis = ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]
    for (int v = 1; v <= 3; v++) {
        def vVar = settings["viewerMoodVar${v}_${i}"]
        if (vVar) {
            def mood = getGlobalVar(vVar)?.value?.toString()
            if (mood && sickEmojis.contains(mood)) return true
        }
    }
    return false
}

// --- ACTIVE INTERNET CHECK (CAPTIVE PORTAL PING) ---
def checkInternetConnection() {
    def hasInternet = true
    if (settings["internetStatusSwitch"]) {
        hasInternet = settings["internetStatusSwitch"].currentValue("switch") == "on"
    } else if (settings["enableAutoInternetCheck"] != false) {
        try {
            httpGet([uri: "http://clients3.google.com/generate_204", timeout: 4]) { resp ->
                hasInternet = (resp.status == 204 || resp.status == 200)
            }
        } catch(e) {
            log.warn "Internet connection test failed: ${e.message}"
            hasInternet = false
        }
    }
    return hasInternet
}

// --- Fetch Installed Apps from Paired Roku Stick via ECP ---
def fetchPairedRokuApps(i) {
    def ip = getPairedRokuIp(i)
    if (!ip) {
        addToHistory("${getTvName(i)}: Cannot fetch paired Roku apps — IP address not found.")
        return
    }
    
    try {
        def params = [uri: "http://${ip}:8060/query/apps", timeout: 5]
        httpGet(params) { resp ->
            if (resp.status == 200 && resp.data) {
                def apps = []
                def appXml = resp.data.toString()
                def matcher = appXml =~ /<app[^>]*id="([^"]+)"[^>]*>([^<]+)<\/app>/
                while (matcher.find()) {
                    def appId = matcher.group(1)
                    def appName = matcher.group(2).trim()
                    if (appName) apps << appName
                }
                if (!state.pairedRokuApps) state.pairedRokuApps = [:]
                state.pairedRokuApps["${i}"] = apps.unique().sort()
                addToHistory("${getTvName(i)}: Successfully loaded ${apps.size()} apps from Paired Roku.")
            }
        }
    } catch(e) {
        addToHistory("${getTvName(i)}: Failed to fetch apps from Paired Roku at ${ip}: ${e}")
    }
}

def getPairedRokuIp(i) {
    def manualIp = settings["pairedRokuIp_${i}"]
    if (manualIp) return manualIp
    
    def rokuDev = settings["pairedRoku_${i}"]
    if (!rokuDev) return null
    
    def ip = rokuDev.getDataValue("ip") ?: rokuDev.currentValue("networkAddress") ?: rokuDev.getDataValue("networkAddress") ?: rokuDev.currentValue("ip")
    return ip
}

// --- Universal Async Device Command Dispatcher ---
def asyncDeviceCommand(data) {
    def tvNum = data.tvNum
    def settingName = data.settingName
    def command = data.command
    def value1 = data.value1
    def value2 = data.value2
    def targetId = data.targetId?.toString()

    def devices = settings[settingName]
    def dev = null
    
    if (devices instanceof List) {
        dev = devices.find { it.id?.toString() == targetId }
    } else {
        // If it's a single device input, bypass the ID check and use it directly.
        dev = devices 
    }

    if (dev) {
        if (value2 != null) dev."$command"(value1, value2)
        else if (value1 != null) dev."$command"(value1)
        else dev."$command"()
    } else {
        log.error "BMS Engine: asyncDeviceCommand failed to resolve device for ${settingName}"
    }
}

// --- Auto-IP Discovery Helper ---
def getRokuIp(i) {
    def manualIp = settings["rokuIp_${i}"]
    if (manualIp) return manualIp
    
    def tv = settings["tv_${i}"]
    if (!tv) return null
    
    def queryUrl = tv.getDataValue("Query/active-app")
    if (queryUrl) {
        def ipMatch = queryUrl =~ /http:\/\/([0-9\.]+):/
        if (ipMatch) return ipMatch[0][1]
    }
    
    def ip = tv.getDataValue("ip") ?: tv.currentValue("networkAddress") ?: tv.getDataValue("networkAddress") ?: tv.currentValue("ip")
    return ip
}

// --- Roku Async HTTP XML Polling ---
def pollRokuTelemetry(i) {
    def ip = getRokuIp(i)
    if (!ip) return
    
    try {
        def paramsApp = [uri: "http://${ip}:8060/query/active-app", timeout: 5]
        asynchttpGet("rokuAppResponseHandler", paramsApp, [tvNum: i])
        
        def paramsMedia = [uri: "http://${ip}:8060/query/media-player", timeout: 5]
        asynchttpGet("rokuMediaResponseHandler", paramsMedia, [tvNum: i])
    } catch (e) {
        log.warn "Roku Telemetry Polling failed for TV ${i}: ${e}"
    }
}

def rokuAppResponseHandler(response, data) {
    if (response.hasError() || !response.data) return
    def i = data.tvNum
    def appIdMatch = response.data =~ /<app id="([^"]+)"/
    def appId = appIdMatch ? appIdMatch[0][1] : null
    
    def appNameMatch = response.data =~ /<app[^>]*>([^<]+)<\/app>/
    def appName = appNameMatch ? appNameMatch[0][1] : null
    
    if (!state.rokuTelemetry) state.rokuTelemetry = [:]
    if (!state.rokuTelemetry["${i}"]) state.rokuTelemetry["${i}"] = [:]
    state.rokuTelemetry["${i}"].appId = appId
    state.rokuTelemetry["${i}"].appName = appName
}

def rokuMediaResponseHandler(response, data) {
    if (response.hasError() || !response.data) return
    def i = data.tvNum
    
    def contentMatch = response.data =~ /contentId="([^"]+)"/
    def contentId = contentMatch ? contentMatch[0][1] : null
    
    def mediaMatch = response.data =~ /mediaType="([^"]+)"/
    def mediaType = mediaMatch ? mediaMatch[0][1] : null
    
    if (!state.rokuTelemetry) state.rokuTelemetry = [:]
    if (!state.rokuTelemetry["${i}"]) state.rokuTelemetry["${i}"] = [:]
    state.rokuTelemetry["${i}"].contentId = contentId
    state.rokuTelemetry["${i}"].mediaType = mediaType
}

// --- Deep Link Execution Engine ---
def executeDeepLink(data) {
    def i = data.tvNum
    def appId = data.appId
    def contentId = data.contentId
    def mediaType = data.mediaType
    def usePairedRoku = data.usePairedRoku ?: false

    def ip = usePairedRoku ? getPairedRokuIp(i) : getRokuIp(i)
    if (ip && appId) {
        def uri = "http://${ip}:8060/launch/${appId}"
        if (contentId) uri += "?contentId=${contentId}"
        if (mediaType) uri += "&mediaType=${mediaType}"
        
        try {
            def params = [uri: uri, timeout: 5]
            asynchttpPost("rokuLaunchHandler", params, [tvNum: i])
            addToHistory("${getTvName(i)}: Executing Roku Deep-Link to App ${appId}.")
        } catch(e) {
            log.error "Deep-Link execution failed: ${e}"
        }
    } else {
        addToHistory("${getTvName(i)}: Deep-Link failed (Missing Roku IP or App ID).")
    }
}

def rokuLaunchHandler(response, data) {
    if (response.hasError()) log.warn "Roku Launch Error: HTTP ${response.status}"
}

// --- Event Handlers ---
def cozyOvercastHandler(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def isOn = evt.value == "on"

    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableCozyMode_${i}"] && settings["cozyOvercast_${i}"]?.id == deviceId) {
            def tv = getPrimaryDevice(i)
            if (isTvActuallyOn(tv, i)) {
                if (isOn && !state.cozyLightsActivatedByTv["${i}"]) {
                    def targetLevel = settings["cozyLevel_${i}"] ?: 50
                    def ctVarName = settings["cozyCTVar_${i}"]
                    def targetCT = null

                    if (ctVarName) {
                        def hubVar = getGlobalVar(ctVarName)
                        if (hubVar != null && hubVar.value != null) targetCT = hubVar.value.toInteger()
                    }

                    addToHistory("${getTvName(i)}: Weather became overcast. Activating Cozy Mode dynamically.")
                    def cozyLights = settings["cozyLights_${i}"]
                    if (cozyLights) {
                        cozyLights.eachWithIndex { bulb, idx ->
                            if (targetCT != null && bulb.hasCommand("setColorTemperature")) {
                                runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "cozyLights_${i}", command: "setColorTemperature", value1: targetCT, value2: targetLevel, targetId: bulb.id], overwrite: false])
                            } else {
                                runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "cozyLights_${i}", command: "setLevel", value1: targetLevel, targetId: bulb.id], overwrite: false])
                            }
                        }
                    }
                    state.cozyLightsActivatedByTv["${i}"] = true
                }
            }
        }
    }
}

def extendSwitchHandler(evt) {
    def isOn = evt.value == "on"
    if (!isOn) return
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableTimeLimits_${i}"] && settings["extendSwitch_${i}"]?.id == deviceId) {
            if (!state.tvTimeExtended) state.tvTimeExtended = [:]
            state.tvTimeExtended["${i}"] = (state.tvTimeExtended["${i}"] ?: 0) + 30
            addToHistory("${getTvName(i)}: Time limit extended by 30 minutes via switch.")
            
            runIn(30, "turnOffExtendSwitch", [data: [tvNum: i]])
        }
    }
    syncToVoiceButler()
}

def turnOffExtendSwitch(data) {
    def i = data.tvNum
    def sw = settings["extendSwitch_${i}"]
    if (sw && sw.currentValue("switch") == "on") {
        sw.off()
    }
}

def checkInstantTimeLimit(i, currentApp) {
    if (!settings["enableTimeLimits_${i}"]) return false
    def isGuestMode = settings["globalGuestSwitch"]?.currentValue("switch") == "on"
    if (isGuestMode) return false

    def ext = state.tvTimeExtended?."${i}" ?: 0
    
    // 1. Check App Limit
    def limitedApps = settings["appLimitList_${i}"]
    def appLimit = settings["appLimitMins_${i}"]
    if (limitedApps && appLimit && limitedApps.contains(currentApp)) {
        def appMins = settings["enforceGlobalAppLimits"] ? (state.globalAppTimeWatched?."${currentApp}" ?: 0) : (state.appTimeWatched?."${i}"?."${currentApp}" ?: 0)
        if (appMins >= (appLimit + ext)) {
            enforceLimitAction(i, "appLimit")
            return true
        }
    }

    // 2. Check Global TV Limit
    def maxTv = settings["tvMaxLimitMins_${i}"]
    if (maxTv) {
        def watchMins = state.watchTimeToday?."${i}" ?: 0
        if (watchMins >= (maxTv + ext)) {
            enforceLimitAction(i, "tvLimit")
            return true
        }
    }
    
    return false
}

def enforceLimitAction(i, limitType) {
    def tv = getPrimaryDevice(i)
    if (!tv) return
    
    def actionType = settings["${limitType}Action_${i}"]
    
    if (actionType == "Launch Specific App / Menu" || actionType == "Return to Home Menu") {
        def method = settings["${limitType}TargetMethod_${i}"] ?: "keyPress (Remote Button)"
        def target = ""
        
        if (method == "setApplication (Launch App)") {
            target = settings["${limitType}CustomApp_${i}"] ?: settings["${limitType}TargetApp_${i}"] ?: "Home"
            addToHistory("${getTvName(i)}: Time Limit Reached. Launching App [${target}].")
            if (tv.hasCommand("setApplication")) tv.setApplication(target)
            else if (tv.hasCommand("home")) tv.home()
            else issuePowerCommand(i, "off", 1)
        } else {
            target = settings["${limitType}TargetKey_${i}"] ?: "Home"
            addToHistory("${getTvName(i)}: Time Limit Reached. Sending KeyPress [${target}].")
            if (tv.hasCommand("keyPress")) tv.keyPress(target)
            else if (tv.hasCommand("home")) tv.home()
            else issuePowerCommand(i, "off", 1)
        }
    } else {
        addToHistory("${getTvName(i)}: Time Limit Reached. Powering OFF.")
        issuePowerCommand(i, "off", 1)
    }

    sendLocationEvent(name: "voiceButlerTvAlert", value: "limit_reached", data: getTvName(i), isStateChange: true)
}

// --- BMS Integrity Engines ---
def nightlyMaintenance() {
    log.info "BMS: Executing Nightly Driver Maintenance."
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        if (tv) {
            if (tv.hasCommand("refresh")) tv.refresh()
            pauseExecution(1000)
            if (tv.hasCommand("initialize")) tv.initialize()
        }
    }
}

def verifyPowerState(data) {
    def i = data.tvNum
    def action = data.action
    def attempt = data.attempt
    def tv = getPrimaryDevice(i)
    
    def isOn = isTvActuallyOn(tv, i)
    def success = (action == "on" && isOn) || (action == "off" && !isOn)
    
    if (!success) {
        if (attempt < 3) {
            addToHistory("⚠️ ${getTvName(i)}: Heartbeat verification failed (${action}). Retrying attempt ${attempt + 1}...")
            issuePowerCommand(i, action, attempt + 1)
        } else {
            addToHistory("❌ ${getTvName(i)}: Comms Failure. Device did not respond to ${action} command after 3 attempts. Check network connection.")
        }
    } else {
        if (attempt > 1) addToHistory("✅ ${getTvName(i)}: Connection recovered on attempt ${attempt}.")
    }
}

def issuePowerCommand(i, action, attempt = 1) {
    def tv = getPrimaryDevice(i)
    if (!tv) return
    
    if (settings["bmsMeshJitter"]) pauseExecution(new Random().nextInt(2000) + 500)
    
    if (action == "on") tv.on() else tv.off()
    
    if (settings["bmsHeartbeat"]) {
        runIn(10, "verifyPowerState", [data: [tvNum: i, action: action, attempt: attempt]])
    }
}

// --- Central Routine Handlers (Plug + TV Sequencing) ---
def triggerRoutine(i, targetString, source, slotNum = null) {
    def entSw = settings["entSwitch_${i}"]

    if (entSw) {
        if (entSw.currentValue("switch") == "on") {
            addToHistory("${getTvName(i)}: Entertainment Switch is already ON. Proceeding immediately.")
            processTriggerRoutineWrapper([tvNum: i, targetString: targetString, source: source, slotNum: slotNum])
        } else {
            def delay = (source == "weather") ? 120 : 300
            def delayText = (source == "weather") ? "2 minutes (Emergency Rush)" : "5 minutes"
            
            addToHistory("${getTvName(i)}: Pre-Routine started. Turning ON Entertainment Switch. Waiting ${delayText} for boot...")
            entSw.on()
            runIn(delay, "processTriggerRoutineWrapper", [data: [tvNum: i, targetString: targetString, source: source, slotNum: slotNum], overwrite: false])
        }
    } else {
        processTriggerRoutine(i, targetString, source, slotNum)
    }
}

def processTriggerRoutineWrapper(data) {
    def i = data.tvNum as Integer
    processTriggerRoutine(i, data.targetString, data.source, data.slotNum)
}

def processTriggerRoutine(i, targetString, source, slotNum = null) {
    def plug = settings["tvPlug_${i}"]
    def tv = getPrimaryDevice(i)
    
    def isPlugOff = plug && plug.currentValue("switch") == "off"
    def isTvOff = !isTvActuallyOn(tv, i)
    
    // Save state memory to restore after routine
    if (source == "morning") {
        state.plugWasOffBeforeMorning["${i}"] = isPlugOff
        state.tvWasOffBeforeMorning["${i}"] = isTvOff
    } else if (source == "dailyweather") {
        state.plugWasOffBeforeDailyWeather["${i}"] = isPlugOff
        state.tvWasOffBeforeDailyWeather["${i}"] = isTvOff
    } else if (source == "show") {
        state.plugWasOffBeforeShow["${i}_${slotNum}"] = isPlugOff
        state.tvWasOffBeforeShow["${i}_${slotNum}"] = isTvOff
    }
    
    if (isPlugOff) {
        addToHistory("${getTvName(i)}: Powering on smart plug for routine.")
        plug.on()
        runIn(20, "executeTvPowerOn", [data: [tvNum: i, channel: targetString, source: source, slotNum: slotNum], overwrite: false])
    } else {
        executeTvPowerOn([tvNum: i, channel: targetString, source: source, slotNum: slotNum])
    }
}

def executeTvPowerOn(data) {
    def i = data.tvNum as Integer
    def tv = getPrimaryDevice(i)
    
    if (!isTvActuallyOn(tv, i)) {
        issuePowerCommand(i, "on", 1)
        runIn(18, "executeMediaAction", [data: data, overwrite: false])
    } else {
        runIn(4, "executeMediaAction", [data: data, overwrite: false])
    }
}

def executeMediaAction(data) {
    def i = data.tvNum as Integer
    def target = data.channel
    def source = data.source
    def slotNum = data.slotNum

    // --- INTERNET CHECK & SOURCE OVERRIDE FOR MORNING / WEATHER ---
    def appSwitch = null
    def hasInternet = checkInternetConnection()

    if (source == "weather") {
        appSwitch = settings["weatherAppSwitch"]
    } else if (source == "morning") {
        def sType = settings["morningSourceType_${i}"] ?: "Live TV Channel Only"
        if (sType == "App (Fallback to Live TV if no Internet)") {
            if (hasInternet) {
                appSwitch = settings["morningAppSwitch_${i}"]
                target = null
            } else {
                appSwitch = null
                target = settings["morningChannel_${i}"]
                addToHistory("${getTvName(i)}: No internet detected. Falling back to Live TV.")
            }
        } else if (sType == "App Only") {
            appSwitch = settings["morningAppSwitch_${i}"]
            target = null
        } else {
            appSwitch = null
            target = settings["morningChannel_${i}"]
        }
    } else if (source == "dailyweather") {
        def sType = settings["dailyWeatherSourceType_${i}"] ?: "Live TV Channel Only"
        if (sType == "App (Fallback to Live TV if no Internet)") {
            if (hasInternet) {
                appSwitch = settings["dailyWeatherAppSwitch_${i}"]
                target = null
            } else {
                appSwitch = null
                target = settings["dailyWeatherChannel_${i}"]
                addToHistory("${getTvName(i)}: No internet detected. Falling back to Live TV.")
            }
        } else if (sType == "App Only") {
            appSwitch = settings["dailyWeatherAppSwitch_${i}"]
            target = null
        } else {
            appSwitch = null
            target = settings["dailyWeatherChannel_${i}"]
        }
    }
    // -------------------------------------------------------------

    def routeToRoku = false
    if (source == "show" && slotNum != null && settings["showRouteToPairedRoku_${i}_${slotNum}"]) {
        routeToRoku = true
    } else if (settings["enablePairedRoku_${i}"] && settings["pairedRoku_${i}"]) {
        def delApps = settings["rokuDelegatedApps_${i}"]
        if (delApps instanceof List && target) {
            if (delApps.contains(target.toString().trim())) routeToRoku = true
        } else if (delApps && target) {
            if (delApps.toString().equalsIgnoreCase(target.toString().trim())) routeToRoku = true
        }
    }

    if (routeToRoku) {
        addToHistory("${getTvName(i)}: Delegating content [${target ?: 'Deep Link'}] to Paired Roku.")
        def rokuInput = settings["pairedRokuInput_${i}"]
        if (rokuInput) {
            executeSetChannel([tvNum: i, channel: rokuInput])
        }

        if (source == "show" && slotNum != null && settings["showIsDeepLink_${i}_${slotNum}"]) {
            def appId = settings["favoriteAppId_${i}_${slotNum}"]
            def contentId = settings["favoriteContentId_${i}_${slotNum}"]
            def mediaType = settings["favoriteMediaType_${i}_${slotNum}"]
            runIn(8, "executeDeepLink", [data: [tvNum: i, appId: appId, contentId: contentId, mediaType: mediaType, usePairedRoku: true], overwrite: false])
        } else if (target) {
            runIn(8, "launchPairedRokuApp", [data: [tvNum: i, app: target], overwrite: false])
        }
    } else {
        // Deep Link Handling for Scheduled Shows (Native TV)
        if (source == "show" && slotNum != null && settings["showIsDeepLink_${i}_${slotNum}"]) {
            def appId = settings["favoriteAppId_${i}_${slotNum}"]
            def contentId = settings["favoriteContentId_${i}_${slotNum}"]
            def mediaType = settings["favoriteMediaType_${i}_${slotNum}"]
            executeDeepLink([tvNum: i, appId: appId, contentId: contentId, mediaType: mediaType, usePairedRoku: false])
        } else {
            if (appSwitch) {
                addToHistory("${getTvName(i)}: Launching application via switch [${appSwitch.displayName}].")
                appSwitch.on()
            } else if (target) {
                executeSetChannel(data)
            }
        }
    }
    
    // --- Auto-Mute Scheduling ---
    if (source == "morning" && settings["morningAutoMute_${i}"]) {
        runIn(30, "autoMuteTv", [data: [tvNum: i], overwrite: false])
    } else if (source == "dailyweather" && settings["dailyWeatherAutoMute_${i}"]) {
        runIn(30, "autoMuteTv", [data: [tvNum: i], overwrite: false])
    }
}

def autoMuteTv(data) {
    def i = data.tvNum
    def tv = getPrimaryDevice(i)
    if (tv && isTvActuallyOn(tv, i)) {
        if (tv.hasCommand("mute")) tv.mute()
        else if (tv.hasCommand("setMute")) tv.setMute(true)
        addToHistory("${getTvName(i)}: Auto-Muted TV per routine settings.")
    }
}

def launchPairedRokuApp(data) {
    def i = data.tvNum as Integer
    def appName = data.app
    def roku = settings["pairedRoku_${i}"]
    if (roku && roku.hasCommand("setApplication")) {
        roku.setApplication(appName)
        addToHistory("${getTvName(i)}: Launched ${appName} on Paired Roku.")
    }
}

def endRoutine(i, source, slotNum = null) {
    def tv = getPrimaryDevice(i)
    def cutTvPower = false
    
    if (source == "weather" && state.tvWasOffBeforeWeather["${i}"]) cutTvPower = true
    else if (source == "morning" && state.tvWasOffBeforeMorning["${i}"]) cutTvPower = true
    else if (source == "dailyweather" && state.tvWasOffBeforeDailyWeather["${i}"]) cutTvPower = true
    else if (source == "show" && state.tvWasOffBeforeShow["${i}_${slotNum}"]) cutTvPower = true
    
    if (tv && isTvActuallyOn(tv, i)) {
        if (cutTvPower) {
            addToHistory("${getTvName(i)}: Routine (${source}) ended. TV was originally OFF, powering OFF.")
            issuePowerCommand(i, "off", 1)
        } else {
            addToHistory("${getTvName(i)}: Routine (${source}) ended. TV was originally ON, leaving it running.")
        }
    }
    runIn(8, "evaluatePlugShutdown", [data: [tvNum: i, source: source, slotNum: slotNum], overwrite: false])
}

def evaluatePlugShutdown(data) {
    def i = data.tvNum as Integer
    def source = data.source
    def slotNum = data.slotNum
    
    def plug = settings["tvPlug_${i}"]
    def entSw = settings["entSwitch_${i}"]
    def occSwitch = settings["roomOccupancySwitch_${i}"]
    
    def cutPlugPower = false
    if (source == "weather" && state.plugWasOffBeforeWeather["${i}"]) cutPlugPower = true
    else if (source == "morning" && state.plugWasOffBeforeMorning["${i}"]) cutPlugPower = true
    else if (source == "dailyweather" && state.plugWasOffBeforeDailyWeather["${i}"]) cutPlugPower = true
    else if (source == "show" && state.plugWasOffBeforeShow["${i}_${slotNum}"]) cutPlugPower = true
    
    // Safety check: if TV is supposed to stay ON, do not cut plug power
    def cutTvPower = false
    if (source == "weather" && state.tvWasOffBeforeWeather["${i}"]) cutTvPower = true
    else if (source == "morning" && state.tvWasOffBeforeMorning["${i}"]) cutTvPower = true
    else if (source == "dailyweather" && state.tvWasOffBeforeDailyWeather["${i}"]) cutTvPower = true
    else if (source == "show" && state.tvWasOffBeforeShow["${i}_${slotNum}"]) cutTvPower = true
    
    if (cutPlugPower && cutTvPower && plug) {
        addToHistory("${getTvName(i)}: Cutting power to smart plug (was off before routine).")
        plug.off()
        
        if (source == "weather") state.plugWasOffBeforeWeather["${i}"] = false
        else if (source == "morning") state.plugWasOffBeforeMorning["${i}"] = false
        else if (source == "dailyweather") state.plugWasOffBeforeDailyWeather["${i}"] = false
        else if (source == "show") state.plugWasOffBeforeShow["${i}_${slotNum}"] = false
    }

    // --- Entertainment Switch End-Of-Show Evaluation ---
    if (entSw) {
        if (!occSwitch || occSwitch.currentValue("switch") == "on") {
            addToHistory("${getTvName(i)}: Routine ended but Room Occupancy is active. Leaving Entertainment Switch ON.")
        } else {
            addToHistory("${getTvName(i)}: Routine ended and Room Occupancy is OFF. Turning Entertainment Switch OFF.")
            entSw.off()
        }
    }
}

// --- Scheduled TV Shows ---
def checkTvShows() {
    if (isSystemPaused()) return
    def now = new Date()
    def today = now.format("EEEE", location.timeZone)
    def currentTime = now.format("HH:mm", location.timeZone)

    for (int i = 1; i <= (numTVs as Integer); i++) {
        for (int s = 1; s <= 2; s++) {
            if (settings["enableShow_${i}_${s}"]) {
                def days = settings["showDays_${i}_${s}"]
                if (days && !days.contains(today)) continue

                def modes = settings["showModes_${i}_${s}"]
                if (modes && !modes.contains(location.mode)) continue

                def startStr = settings["showTimeStart_${i}_${s}"]
                if (startStr) {
                    def startFormatted = timeToday(startStr, location.timeZone).format("HH:mm", location.timeZone)
                    if (currentTime == startFormatted) {
                        startTvShow(i, s)
                    }
                }

                def endStr = settings["showTimeEnd_${i}_${s}"]
                if (endStr) {
                    def endFormatted = timeToday(endStr, location.timeZone).format("HH:mm", location.timeZone)
                    if (currentTime == endFormatted) {
                        endTvShow(i, s)
                    }
                }
            }
        }
    }
}

def startTvShow(i, s) {
    def showName = settings["showName_${i}_${s}"] ?: "TV Show ${s}"
    def target = settings["showChannel_${i}_${s}"]
    
    addToHistory("${getTvName(i)}: Starting scheduled show [${showName}].")
    triggerRoutine(i, target, "show", s)
}

def endTvShow(i, s) {
    def showName = settings["showName_${i}_${s}"] ?: "TV Show ${s}"
    addToHistory("${getTvName(i)}: Scheduled show [${showName}] ended.")
    endRoutine(i, "show", s)
}

// --- TV State & Power Evaluator ---
def refreshTVs() {
    if (isSystemPaused()) return
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        if (tv && tv.hasCommand("refresh")) tv.refresh()
        
        if (settings["tvType_${i}"] == "Roku TV" && getRokuIp(i) && isTvActuallyOn(tv, i)) {
            pollRokuTelemetry(i)
        }
    }
}

def occupancySync() {
    if (isSystemPaused()) return
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def occSwitch = settings["roomOccupancySwitch_${i}"]
        if (occSwitch && occSwitch.currentValue("switch") == "on") {
            def tv = getPrimaryDevice(i)
            if (tv && tv.hasCommand("refresh")) {
                tv.refresh()
            }
            if (settings["tvType_${i}"] == "Roku TV" && getRokuIp(i) && isTvActuallyOn(tv, i)) {
                pollRokuTelemetry(i)
            }
        }
    }
}

// --- BULLETPROOF ENGINE EVALUATOR ---
def isTvActuallyOn(tv, i) {
    def plug = settings["tvPlug_${i}"]
    if (plug && plug.currentValue("switch") == "off") {
        return false // If the physical power is cut via plug, force off
    }

    // Hardware Source of Truth: If the plug is monitoring wattage, check it first.
    if (plug && settings["tvPlugPowerMonitor_${i}"]) {
        def plugWatts = 0.0
        try { plugWatts = (plug.currentValue("power") ?: 0.0) as Float } catch(e) {}
        def thresh = (settings["tvPlugActiveWatts_${i}"] ?: 30) as Float
        if (plugWatts >= thresh) {
            return true // TV is physically drawing enough power to be on, bypass all other checks
        }
    }

    if (!tv) return false
    
    // Ensure we handle nulls and capitalization inconsistencies from custom drivers
    def sw = (tv.currentValue("switch") ?: "unknown").toString().toLowerCase().trim()
    def pwr = (tv.currentValue("power") ?: "unknown").toString().toLowerCase().trim()
    def app = tv.currentValue("application") ?: tv.currentValue("mediaInputSource") ?: "unknown"
    def cleanApp = app.toString().trim().toLowerCase()
    def transport = (tv.currentValue("transportStatus") ?: "unknown").toString().toLowerCase().trim()

    if (transport == "playing") return true

    if (cleanApp.contains("hdmi") || cleanApp.contains("android tv") || cleanApp.contains("live tv") || cleanApp.contains("antenna") || cleanApp.contains("tuner") || cleanApp.contains("av") || cleanApp == "tv" || cleanApp.contains("cable") || cleanApp.contains("satellite")) {
        return true
    }

    if (settings["tvType_${i}"] == "Roku TV" && state.rokuTelemetry?."${i}") {
        def tAppId = state.rokuTelemetry["${i}"].appId?.toString()?.toLowerCase()
        if (tAppId?.startsWith("tvinput.")) {
            return true 
        }
    }

    // Explicit Off-State Catchers
    if (sw == "off" || pwr in ["off", "standby", "poweroff", "displayoff", "headless", "suspend"]) {
        return false
    }
    
    def idleApps = [
        "none", "home", "ambient", "screen saver", "roku dynamic menu", 
        "backdrops", "roku media player", "art gallery", "com.apple.tvidlescreen", 
        "backdrop", "unknown/home"
    ] 
    
    if (idleApps.contains(cleanApp)) return false
    
    // If we survived the off-switches and idle apps, check if either the switch or power indicates it is actively on
    if (sw == "on" || pwr in ["on", "poweron", "ready", "playing"]) return true
    
    return false
}

def tvPowerEvaluator(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def primary = getPrimaryDevice(i)
        def plug = settings["tvPlug_${i}"]
        
        if (primary?.id == deviceId || plug?.id == deviceId) {
            def tvName = getTvName(i)
            
            if (plug && plug.currentValue("switch") == "off" && primary?.currentValue("switch") == "on") {
                addToHistory("⚠️ ${tvName}: State Mismatch! Power switch is OFF but TV driver is stuck ON. Auto-correcting driver state.")
                try { primary.off() } catch(e) {}
            }
            
            def isTrulyOn = isTvActuallyOn(primary, i)
            def lastEvaluatedState = state.evaluatedPowerState["${i}"] ?: false
            
            if (isTrulyOn && !lastEvaluatedState) {
                state.evaluatedPowerState["${i}"] = true
                state.tvOnTime["${i}"] = new Date().time
                addToHistory("${tvName}: Power State changed to ON.")
                
                def currentApp = primary.currentValue("application") ?: primary.currentValue("mediaInputSource") ?: "Unknown"
                
                if (checkInstantTimeLimit(i, currentApp)) {
                    continue // Skip the rest of the power-on logic if it was just turned off
                }
                
                if (settings["tvType_${i}"] == "Roku TV" && getRokuIp(i) && primary.currentValue("switch") == "on") pollRokuTelemetry(i)
                
                if (settings["enableAcousticMgmt_${i}"]) {
                    def noiseSwitches = settings["tvNoiseSwitches_${i}"]
                    if (noiseSwitches) {
                        def sickSwitches = [settings["sickModeSwitch_${i}"]].flatten().findAll{it}
                        def sickModeOn = sickSwitches.any { it.currentValue("switch") == "on" }
                        def apId = settings["airPurifier_${i}"]?.id
                        
                        def activeNoise = noiseSwitches.findAll { 
                            if (it.currentValue("switch") != "on") return false
                            if (sickModeOn && apId && it.id == apId) {
                                addToHistory("${tvName}: Sick Mode is ON. Allowing high-performance air filtration to continue running.")
                                return false
                            }
                            return true
                        }
                        if (activeNoise) {
                            addToHistory("${tvName}: Background noise detected. Turning OFF: ${activeNoise.join(', ')}")
                            activeNoise.eachWithIndex { dev, idx -> 
                                runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "tvNoiseSwitches_${i}", command: "off", targetId: dev.id], overwrite: false])
                            } 
                            state.noiseSwitchesPaused["${i}"] = activeNoise.collect { it.id }
                        } else {
                            state.noiseSwitchesPaused["${i}"] = []
                        }
                    }
                    evaluateAcoustics(i) 
                }
                
                if (settings["enableLightingSync_${i}"]) {
                    def lights = settings["tvLights_${i}"]
                    if (lights) {
                        addToHistory("${tvName}: Environment sync. Delaying 2s to turn OFF lights.")
                        runIn(2, "delayedLightTurnOff", [data: [tvNum: i], overwrite: false])
                    }
                }

                if (settings["enableCozyMode_${i}"]) {
                    def cozyLights = settings["cozyLights_${i}"]
                    if (cozyLights) {
                        def overcast = settings["cozyOvercast_${i}"]
                        def blinds = settings["cozyBlinds_${i}"]
                        def isOvercast = overcast && overcast.currentValue("switch") == "on"
                        def blindsClosed = blinds && blinds.any { it.currentValue("contact") == "closed" }

                        if (isOvercast || blindsClosed) {
                            def targetLevel = settings["cozyLevel_${i}"] ?: 50
                            def ctVarName = settings["cozyCTVar_${i}"]
                            def targetCT = null

                            if (ctVarName) {
                                def hubVar = getGlobalVar(ctVarName)
                                if (hubVar != null && hubVar.value != null) targetCT = hubVar.value.toInteger()
                            }

                            if (targetCT != null) {
                                addToHistory("${tvName}: Cozy Mode conditions met. Setting accent lights to ${targetLevel}% and ${targetCT}K.")
                                cozyLights.eachWithIndex { bulb, idx ->
                                    if (bulb.hasCommand("setColorTemperature")) {
                                        runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "cozyLights_${i}", command: "setColorTemperature", value1: targetCT, value2: targetLevel, targetId: bulb.id], overwrite: false])
                                    } else {
                                        runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "cozyLights_${i}", command: "setLevel", value1: targetLevel, targetId: bulb.id], overwrite: false])
                                    }
                                }
                            } else {
                                addToHistory("${tvName}: Cozy Mode conditions met. Setting accent lights to ${targetLevel}%.")
                                cozyLights.eachWithIndex { bulb, idx -> 
                                    runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "cozyLights_${i}", command: "setLevel", value1: targetLevel, targetId: bulb.id], overwrite: false])
                                }
                            }
                            
                            state.cozyLightsActivatedByTv["${i}"] = true
                        } else {
                            state.cozyLightsActivatedByTv["${i}"] = false
                        }
                    }
                }
                
                if (settings["enableSweeper_${i}"]) {
                    runIn(4, "executeSweeperDelay", [data: [tvNum: i, isPeriodic: false], overwrite: false])
                }
                
                if (settings["enableMusicSync_${i}"]) {
                    def sonos = settings["sonos_${i}"]
                    if (sonos) {
                        def sStatus = sonos.currentValue("transportStatus") ?: sonos.currentValue("status")
                        if (sStatus == "playing") {
                            addToHistory("${tvName}: Auto-pausing Sonos for TV audio.")
                            sonos.pause()
                            state.pausedSonos["${i}"] = true
                        } else {
                            state.pausedSonos["${i}"] = false
                        }
                    }
                }
                 
            } else if (!isTrulyOn && lastEvaluatedState) {
                state.evaluatedPowerState["${i}"] = false
                state.currentVolumeBoost["${i}"] = 0 
                state.tvOnTime["${i}"] = null
                
                if (state.intermissionActive["${i}"]) {
                    state.intermissionActive["${i}"] = false
                    unschedule("activateIntermission")
                }
                
                if (state.noiseStart) {
                    state.noiseStart.findAll { it.key.startsWith("${i}_") }.each { state.noiseStart[it.key] = null }
                }
                
                addToHistory("${tvName}: Power State changed to OFF.")

                if (settings["enableAcousticMgmt_${i}"]) {
                    def noiseSwitches = settings["tvNoiseSwitches_${i}"]
                    def pausedIds = state.noiseSwitchesPaused["${i}"] ?: []
                    if (noiseSwitches && pausedIds) {
                        def toRestore = noiseSwitches.findAll { pausedIds.contains(it.id) }
                        if (toRestore) {
                             addToHistory("${tvName}: Restoring background appliances: ${toRestore.join(', ')}")
                            toRestore.eachWithIndex { dev, idx -> 
                                runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "tvNoiseSwitches_${i}", command: "on", targetId: dev.id], overwrite: false])
                            } 
                        }
                         state.noiseSwitchesPaused["${i}"] = []
                    }
                }

                if (settings["enableCozyMode_${i}"] && settings["cozyOffWithTv_${i}"] && state.cozyLightsActivatedByTv["${i}"]) {
                    def cozyLights = settings["cozyLights_${i}"]
                    if (cozyLights) {
                        addToHistory("${tvName}: TV shutting down. Turning OFF Cozy Mode lights.")
                        cozyLights.eachWithIndex { dev, idx -> 
                            runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "cozyLights_${i}", command: "off", targetId: dev.id], overwrite: false])
                        }
                    }
                    state.cozyLightsActivatedByTv["${i}"] = false
                }
                
                if (settings["enableMusicSync_${i}"]) {
                    def sonos = settings["sonos_${i}"]
                    if (sonos && state.pausedSonos["${i}"]) {
                         state.pausedSonos["${i}"] = false
                        def allowedModes = settings["sonosResumeModes_${i}"]
                        def startTime = settings["sonosResumeTimeStart_${i}"]
                        def endTime = settings["sonosResumeTimeEnd_${i}"]
                        def modeOk = !allowedModes || allowedModes.contains(location.mode)
                        def timeOk = true
                        
                        if (startTime && endTime) timeOk = timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)
                        
                        if (modeOk && timeOk) {
                             addToHistory("${tvName}: Conditions met. Auto-resuming Sonos.")
                            sonos.play()
                        }
                    }
                 }
            }
        }
    }
    syncToVoiceButler()
}

// --- Smart Acoustic Management Engine ---
def acousticDeviceHandler(evt) {
    if (isSystemPaused()) return
    def devId = evt.device.id
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (!settings["enableAcousticMgmt_${i}"]) continue
        
        def isMatch = false
        if (settings["mainThermostat_${i}"]?.id == devId) isMatch = true
        else if (settings["dishwasher_${i}"]?.id == devId) isMatch = true
        else if (settings["vacuum_${i}"]?.id == devId) isMatch = true
        else if (settings["airPurifier_${i}"]?.id == devId) isMatch = true
        else if (settings["dehumidifier_${i}"]?.id == devId) isMatch = true
        
        if (isMatch) evaluateAcoustics(i)
    }
}

def triggerAcousticEval(data) {
    evaluateAcoustics(data.tvNum)
}

def evaluateAcoustics(i) {
    def primary = getPrimaryDevice(i)
    if (!isTvActuallyOn(primary, i)) {
        state.currentVolumeBoost["${i}"] = 0
        return
    }
    
    def now = new Date().time
    
    // Ensure tvOnTime is robustly initialized even if tvPowerEvaluator missed the first beat
    if (!state.tvOnTime) state.tvOnTime = [:]
    def tvOnTime = state.tvOnTime["${i}"]
    if (!tvOnTime) {
        tvOnTime = now
        state.tvOnTime["${i}"] = tvOnTime
    }
    
    def maxBoost = 0
    def needsReval = false
    def nextRevalTime = 60
    
    def checkBoost = { devKey, isActive, boostVal, activeDelayMs ->
        if (isActive) {
            if (!state.noiseStart) state.noiseStart = [:]
            
            def isNewInit = false
            if (!state.noiseStart["${i}_${devKey}"]) {
                state.noiseStart["${i}_${devKey}"] = now
                isNewInit = true
            }
            
            if (isNewInit && (now - (tvOnTime as Long)) < 5000) {
                state.noiseStart["${i}_${devKey}"] = (tvOnTime as Long) - 10000
            }
            
            def devStart = state.noiseStart["${i}_${devKey}"] as Long
            def tvStart = tvOnTime as Long
            
            def requiredDelayMs = (devStart < tvStart) ? 300000 : activeDelayMs
            def referenceTime = Math.max(devStart, tvStart)
            
            if ((now - referenceTime) >= requiredDelayMs) {
                maxBoost = Math.max(maxBoost, boostVal as Integer)
            } else {
                needsReval = true
                def timeRemainingSec = Math.ceil(((referenceTime + requiredDelayMs) - now) / 1000) as Integer
                if (timeRemainingSec > 0 && (nextRevalTime == 60 || timeRemainingSec < nextRevalTime)) {
                    nextRevalTime = timeRemainingSec + 2
                }
            }
        } else {
            if (state.noiseStart && state.noiseStart["${i}_${devKey}"]) {
                state.noiseStart["${i}_${devKey}"] = null
            }
        }
    }

    // HVAC Evaluation
    def thermo = settings["mainThermostat_${i}"]
    def hvacRunning = thermo && thermo.currentValue("thermostatOperatingState") in ["heating", "cooling", "fan only"]
    checkBoost("hvac", hvacRunning, settings["hvacVolumeBoost_${i}"] ?: 3, 0)
    
    // Dishwasher Evaluation
    def dish = settings["dishwasher_${i}"]
    def dishRunning = false
    if (dish) {
        def dishPwr = 0.0
        try { dishPwr = (dish.currentValue("power") ?: 0.0) as Float } catch(e) {}
        def dThresh = (settings["dishwasherThreshold_${i}"] ?: 15) as Float
        def debounceMins = (settings["dishwasherDebounce_${i}"] ?: 5) as Long
        
        if (dishPwr > dThresh) {
            state."dishLastActive_${i}" = now
            dishRunning = true
        } else {
            def lastActive = state."dishLastActive_${i}" ?: 0
            if ((now - lastActive) < (debounceMins * 60000)) {
                dishRunning = true
            }
        }
    }
    checkBoost("dish", dishRunning, settings["dishwasherBoost_${i}"] ?: 4, 58000)
    
    // Vacuum Evaluation
    def vac = settings["vacuum_${i}"]
    def vacRunning = vac && vac.currentValue("switch") == "on"
    checkBoost("vac", vacRunning, settings["vacuumBoost_${i}"] ?: 10, 58000)
    
    // Air Purifier Evaluation
    def ap = settings["airPurifier_${i}"]
    def apRunning = ap && ap.currentValue("switch") == "on"
    checkBoost("ap", apRunning, settings["airPurifierBoost_${i}"] ?: 2, 58000)

    // Dehumidifier Evaluation
    def dehum = settings["dehumidifier_${i}"]
    def dehumRunning = dehum && dehum.currentValue("switch") == "on"
    checkBoost("dehum", dehumRunning, settings["dehumidifierBoost_${i}"] ?: 3, 58000)
    
    // --- BIOLOGICAL / MOOD OVERRIDE ---
    if (isAnyoneSick(i)) {
        maxBoost = 0
        needsReval = false
    }
    
    if (needsReval) {
        runIn(nextRevalTime, "triggerAcousticEval_${i}", [overwrite: true])
    }
    
    def currentBoost = state.currentVolumeBoost["${i}"] ?: 0
    def diff = maxBoost - currentBoost
    
    if (diff != 0) {
        def direction = diff > 0 ? "up" : "down"
        def amount = Math.abs(diff)
        
        def action = direction == "up" ? "Boosting" : "Reducing"
        addToHistory("${getTvName(i)}: Smart Acoustic Adjustment. ${action} volume by ${amount} units. (New Requirement Limit: ${maxBoost})")
        
        adjustVolumeRelative(i, amount, direction)
        state.currentVolumeBoost["${i}"] = maxBoost
    }
}

// --- Volume Control Engine ---
def adjustVolumeRelative(i, amount, direction) {
    def tv = getPrimaryDevice(i)
    if (!tv) return
    
    def amountInt = amount as Integer
    
    // 1. Prefer iterative volumeUp / volumeDown (Works best for Roku / Smart TVs)
    if (tv.hasCommand("volumeUp") && tv.hasCommand("volumeDown")) {
        def cmd = direction == "up" ? "volumeUp" : "volumeDown"
        executeVolumeStep([tvNum: i, command: cmd, key: null, remaining: amountInt])
        return
    } 
    
    // 2. Fallback to IR / Network KeyPress commands
    if (tv.hasCommand("keyPress")) {
        def keyStr = direction == "up" ? "VolumeUp" : "VolumeDown"
        executeVolumeStep([tvNum: i, command: "keyPress", key: keyStr, remaining: amountInt])
        return
    }
    
    // 3. Final Fallback: Absolute Level Setting
    if (tv.hasCommand("setLevel") || tv.hasCommand("setVolume")) {
        def currentLevelObj = tv.currentValue("level") ?: tv.currentValue("volume") ?: 50
        def currentLevel = currentLevelObj as Integer
        def newLevel = direction == "up" ? currentLevel + amountInt : currentLevel - amountInt
        
        if (newLevel < 0) newLevel = 0
        if (newLevel > 100) newLevel = 100
        
        if (tv.hasCommand("setLevel")) tv.setLevel(newLevel)
        else tv.setVolume(newLevel)
    } else {
        addToHistory("⚠️ TV ${i} does not support recognized volume commands.")
    }
}

// Dedicated recursive engine to ensure perfect step counts without dropping packets
def executeVolumeStep(data) {
    def i = data.tvNum
    def cmd = data.command
    def keyStr = data.key
    def remaining = data.remaining as Integer
    
    def tv = getPrimaryDevice(i)
    if (tv && remaining > 0) {
        
        // Execute the current step
        if (keyStr) {
            tv."$cmd"(keyStr)
        } else {
            tv."$cmd"()
        }
        
        // If we have more steps to go, chain the next execution safely
        if (remaining > 1) {
            runInMillis(800, "executeVolumeStep", [data: [tvNum: i, command: cmd, key: keyStr, remaining: remaining - 1]])
        }
    }
}

// --- Secondary Feature Handlers ---
def delayedLightTurnOff(data) {
    def i = data.tvNum
    def lights = settings["tvLights_${i}"]
    if (lights) {
        // Robust enforcement: do not check state, immediately command to turn OFF
        lights.eachWithIndex { dev, idx -> 
            runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "tvLights_${i}", command: "off", targetId: dev.id], overwrite: false])
        }
    }
}

def executeSweeperDelay(data) {
    executeSweeper(data.tvNum, data.isPeriodic)
}

def executeSweeper(i, isPeriodic) {
    if (!settings["enableSweeper_${i}"]) return
    def tv = getPrimaryDevice(i)
    if (!isTvActuallyOn(tv, i)) return
    
    def sweptDevices = []
    def bypassedDevices = []
    def sweepTimeout = settings["sweepTimeout_${i}"] ?: 3
    def timeoutMs = sweepTimeout * 60000
    
    for (int l = 1; l <= 5; l++) {
        def light = settings["sweepLight_${i}_${l}"]
        def motions = settings["sweepMotion_${i}_${l}"]
        
        if (light && light.currentValue("switch") == "on") {
            def canTurnOff = false
            
            if (motions) {
                def motionList = motions instanceof List ? motions : [motions]
                def anyActive = motionList.any { it.currentValue("motion") == "active" }
                
                if (!anyActive) {
                    def allTimeoutMet = motionList.every { m ->
                        def motionState = m.currentState("motion")
                        if (motionState?.value == "inactive") {
                            def inactiveSince = motionState.date?.time ?: new Date().time
                            return (new Date().time - inactiveSince) >= timeoutMs
                        }
                        return false 
                    }
                    if (allTimeoutMet) {
                        canTurnOff = true
                    }
                }
            } else {
                canTurnOff = true 
            }
            
            if (canTurnOff) {
                runInMillis(300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "sweepLight_${i}_${l}", command: "off", targetId: light.id], overwrite: false])
                sweptDevices << light.displayName
            } else {
                 bypassedDevices << light.displayName
            }
        }
    }
    
    if (sweptDevices) {
        addToHistory("${getTvName(i)}: Dynamic Glare Reduction - Extinguished zone lights: ${sweptDevices.join(', ')}")
    }
    
    if (bypassedDevices && !isPeriodic) {
        addToHistory("${getTvName(i)}: Dynamic Glare Reduction bypassed (Zone Occupied or timeout not met): ${bypassedDevices.join(', ')}")
    }
}

def evaluateRoomLights(i) {
    def tv = getPrimaryDevice(i)
    if (isTvActuallyOn(tv, i)) {
        def actionTaken = false
        
        if (settings["enableLightingSync_${i}"]) {
            def lights = settings["tvLights_${i}"]
            if (lights) {
                addToHistory("${getTvName(i)}: Room Evaluation - Forcing lights OFF.")
                lights.eachWithIndex { dev, idx -> 
                    runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "tvLights_${i}", command: "off", targetId: dev.id], overwrite: false])
                }
                actionTaken = true
            }
        }
        
        if (settings["enableAcousticMgmt_${i}"]) {
            def noiseSwitches = settings["tvNoiseSwitches_${i}"]
            if (noiseSwitches) {
                def sickSwitches = [settings["sickModeSwitch_${i}"]].flatten().findAll{it}
                def sickModeOn = sickSwitches.any { it.currentValue("switch") == "on" }
                def apId = settings["airPurifier_${i}"]?.id
                
                def activeNoise = noiseSwitches.findAll { 
                    if (it.currentValue("switch") != "on") return false
                    if (sickModeOn && apId && it.id == apId) {
                        addToHistory("${getTvName(i)}: Room Evaluation bypassed for Air Purifier due to active Sick Mode.")
                        return false
                    }
                    return true
                }
                if (activeNoise) {
                    addToHistory("${getTvName(i)}: Room Evaluation - Forcing background appliances OFF.")
                    activeNoise.eachWithIndex { dev, idx -> 
                        runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "tvNoiseSwitches_${i}", command: "off", targetId: dev.id], overwrite: false])
                    }
                    
                    def existingPaused = state.noiseSwitchesPaused["${i}"] ?: []
                    def newPaused = activeNoise.collect { it.id }
                    state.noiseSwitchesPaused["${i}"] = (existingPaused + newPaused).unique()
                    
                    actionTaken = true
                }
            }
        }
        
        if (settings["enableSweeper_${i}"]) {
             executeSweeper(i, false)
             actionTaken = true
        }
        
        if (!actionTaken) {
             addToHistory("${getTvName(i)}: Room Evaluation - Assigned devices are already off or sync is disabled.")
        }
        
    } else {
        addToHistory("${getTvName(i)}: Room Evaluation ignored (TV not active).")
    }
}

def syncToVoiceButler() {
    def payload = [:]
    def tvCount = settings.numTVs ? settings.numTVs as Integer : 0
    for (int i = 1; i <= tvCount; i++) {
        def tv = getPrimaryDevice(i)
        if (tv) {
            def isOn = isTvActuallyOn(tv, i)
            def currentApp = "Unknown"
            if (isOn) {
                currentApp = tv.currentValue("application") ?: tv.currentValue("mediaInputSource") ?: "Unknown"
            } else {
                currentApp = "Screen Off"
            }
            
            def watchMins = state.watchTimeToday?."${i}" ?: 0
            def maxTv = settings["tvMaxLimitMins_${i}"] ?: 0
            def ext = state.tvTimeExtended?."${i}" ?: 0
            
            def limitedApps = settings["appLimitList_${i}"] ?: []
            def appLimit = settings["appLimitMins_${i}"] ?: 0
            def currentAppMins = 0
            if (limitedApps.contains(currentApp)) {
                currentAppMins = settings["enforceGlobalAppLimits"] ? (state.globalAppTimeWatched?."${currentApp}" ?: 0) : (state.appTimeWatched?."${i}"?."${currentApp}" ?: 0)
            }

            // Force all numbers to Integers and strings to Strings to ensure clean JSON parsing on the other side
            payload["${i}"] = [
                name: (settings["tvName_${i}"] ?: "TV ${i}").toString(),
                isOn: isOn,
                app: currentApp.toString(),
                watchMins: watchMins.toInteger(),
                maxTv: maxTv.toInteger(),
                ext: ext.toInteger(),
                appLimit: appLimit.toInteger(),
                currentAppMins: currentAppMins.toInteger(),
                isAppLimited: limitedApps.contains(currentApp)
            ]
        }
    }
    
    def jsonString = new groovy.json.JsonBuilder(payload).toString()
    
    sendLocationEvent(name: "tvManagerSync", value: jsonString, isStateChange: true)
    if (settings.enableDebug) log.debug "TV MANAGER: Synced Live Dashboard data to Voice Butler."
}

def trackUsageStep() {
    if (isSystemPaused()) return
    def isGuestMode = settings["globalGuestSwitch"]?.currentValue("switch") == "on"
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def tv = getPrimaryDevice(i)
        
        if (isTvActuallyOn(tv, i)) {
            
            def currentApp = tv.currentValue("application") ?: tv.currentValue("mediaInputSource") ?: "Unknown/Home"
            
            // Increment Base Stats
            state.watchTimeToday["${i}"] = (state.watchTimeToday["${i}"] ?: 0) + 5
            if (!state.appStats["${i}"]) state.appStats["${i}"] = [:]
            state.appStats["${i}"][currentApp] = (state.appStats["${i}"][currentApp] ?: 0) + 5
            
            // --- TIME LIMIT ENFORCEMENT ---
            if (settings["enableTimeLimits_${i}"]) {
                
                // Track Unique Apps automatically
                if (currentApp != "Unknown" && currentApp != "Unknown/Home" && currentApp != "Screen Off") {
                    if (!state.savedApps) state.savedApps = [:]
                    def savedList = state.savedApps["${i}"] ?: []
                    if (!savedList.contains(currentApp)) {
                        savedList.add(currentApp)
                        if (savedList.size() > 15) savedList = savedList.drop(1)
                        state.savedApps["${i}"] = savedList
                    }
                }
                
                def maxTv = settings["tvMaxLimitMins_${i}"]
                def ext = state.tvTimeExtended?."${i}" ?: 0
                def totalAllowedTv = maxTv ? (maxTv + ext) : null
                def limitedApps = settings["appLimitList_${i}"]
                def limitEnforced = false
                
                // Enforce App Limit
                if (limitedApps && limitedApps.contains(currentApp)) {
                    if (!state.appTimeWatched["${i}"]) state.appTimeWatched["${i}"] = [:]
                    def appMins = (state.appTimeWatched["${i}"][currentApp] ?: 0) + 5
                    state.appTimeWatched["${i}"][currentApp] = appMins
                    
                    if (!state.globalAppTimeWatched) state.globalAppTimeWatched = [:]
                    def globalAppMins = (state.globalAppTimeWatched[currentApp] ?: 0) + 5
                    state.globalAppTimeWatched[currentApp] = globalAppMins
                    
                    def appLimit = settings["appLimitMins_${i}"]
                    def minsToEvaluate = settings["enforceGlobalAppLimits"] ? globalAppMins : appMins
                    
                    if (!isGuestMode && appLimit && minsToEvaluate >= (appLimit + ext)) {
                        enforceLimitAction(i, "appLimit")
                        limitEnforced = true
                    }
                }
                
                if (!isGuestMode && !limitEnforced && totalAllowedTv && state.watchTimeToday["${i}"] >= totalAllowedTv) {
                    enforceLimitAction(i, "tvLimit")
                }
            }
            
            if (settings["enableSweeper_${i}"]) {
                executeSweeper(i, true)
            }
            
            // Continuous Environment Lighting Sync Enforcer
            if (settings["enableLightingSync_${i}"] && settings["enforceLightingSync_${i}"]) {
                def lights = settings["tvLights_${i}"]
                if (lights) {
                    def activeLights = lights.findAll { it.currentValue("switch") == "on" }
                    if (activeLights) {
                        addToHistory("${getTvName(i)}: Environmental Sync Enforcement - Turning OFF lights.")
                        activeLights.eachWithIndex { dev, idx -> 
                            runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "tvLights_${i}", command: "off", targetId: dev.id], overwrite: false])
                        }
                    }
                }
            }
        }
    }
   
    syncToVoiceButler()
    runIn(300, "trackUsageStep") 
}

def voiceButlerCmdHandler(evt) {
    def cmd = evt.value
    def idx = evt.data
    if (cmd == "off") {
        issuePowerCommand(idx, "off", 1)
        addToHistory("VOICE BUTLER: Remote shutdown command received for TV ${idx}.")
        runIn(2, "syncToVoiceButler")
    } else if (cmd == "extend") {
        if (!state.tvTimeExtended) state.tvTimeExtended = [:]
        state.tvTimeExtended["${idx}"] = (state.tvTimeExtended["${idx}"] ?: 0) + 30
        addToHistory("VOICE BUTLER: Remote time extension (+30m) received for TV ${idx}.")
        syncToVoiceButler()
    }
}

def midnightReset() {
    state.watchTimeToday = [:]
    state.appStats = [:]
    state.appTimeWatched = [:]
    state.globalAppTimeWatched = [:] 
    state.tvTimeExtended = [:]
    syncToVoiceButler()
}

def tvAppHandler(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def evtName = evt.name
    def evtValue = evt.value?.toString()?.toLowerCase()
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        def primary = getPrimaryDevice(i)
        if (primary?.id == deviceId) {
            
            tvPowerEvaluator(evt)

            // --- Cinema Intermission Logic ---
            if (evtName == "transportStatus" && settings["enableIntermission_${i}"]) {
                if (evtValue == "paused") {
                    def delay = settings["intermissionDelay_${i}"] ?: 10
                    runIn(delay, "activateIntermission", [data: [tvNum: i], overwrite: true])
                } else if (evtValue == "playing") {
                    unschedule("activateIntermission")
                    if (state.intermissionActive["${i}"]) {
                        restoreIntermission(i)
                    }
                } else if (evtValue == "stopped") {
                    unschedule("activateIntermission")
                    if (state.intermissionActive["${i}"]) {
                        restoreIntermission(i)
                    }
                }
            }

            if (isTvActuallyOn(primary, i)) {
                // Always grab the highest-fidelity name directly from the device instead of relying on the raw event value
                def appName = primary.currentValue("application") ?: primary.currentValue("mediaInputSource") ?: "Unknown"
                
                if (checkInstantTimeLimit(i, appName)) continue // Stop evaluation if the app exceeded time limits and triggered an action
                
                if (state.lastAppLogged?."${i}" != appName) {
                    addToHistory("${getTvName(i)}: Content/Input changed to [${appName}].")
                    state.lastAppLogged["${i}"] = appName
                    if (settings["tvType_${i}"] == "Roku TV" && getRokuIp(i)) pollRokuTelemetry(i)
                    syncToVoiceButler()
                }
            }
        }
    }
}

def tvMotionHandler(evt) {
    def deviceId = evt.device.id
    def isActive = evt.value == "active"
    def now = new Date().time

    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableMotionTimeout_${i}"]) {
            def sensors = settings["motionSensor_${i}"]
            if (sensors?.any { it.id == deviceId }) {
                if (isActive) {
                    state.lastMotionTime["${i}"] = now
                    unschedule("executeTvTimeout_${i}") // Clear pending shutdown if movement detected
                } else {
                    def timeout = settings["motionTimeout_${i}"]
                    if (timeout) runIn(timeout * 60, "executeTvTimeout_${i}", [overwrite: true])
                }
            }
        }
    }
}

def executeTvTimeout(data) {
    if (isSystemPaused()) return
    def i = data.tvNum
    if (!settings["enableMotionTimeout_${i}"]) return
    
    def tv = getPrimaryDevice(i)
    def timeout = settings["motionTimeout_${i}"]
    if (!tv || !timeout || !isTvActuallyOn(tv, i)) return

    // --- NEW: Visitor Mode Bypass ---
    def visitorSw = settings["visitorSwitch_${i}"]
    if (visitorSw && visitorSw.currentValue("switch") == "on") {
        addToHistory("${getTvName(i)}: Motion timeout bypassed (Visitor Switch is ON).")
        return
    }
    
    // Final safety check: ensure none of the selected sensors are active
    def motionSensors = settings["motionSensor_${i}"]
    if (motionSensors) {
        def anyActive = motionSensors.any { it.currentValue("motion") == "active" }
        if (anyActive) return
    }

    def lastMotion = state.lastMotionTime["${i}"] ?: 0
    def now = new Date().time
    if ((now - lastMotion) >= (timeout * 60000) - 2000) {
        addToHistory("${getTvName(i)}: No motion detected. Powering OFF.")
        issuePowerCommand(i, "off", 1)
    }
}

def morningMotionHandler(evt) {
    if (evt.value != "active" || isSystemPaused()) return
    def deviceId = evt.device.id
    def now = new Date().time
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (!settings["enableMorningRoutine_${i}"]) continue
        
        // --- SICK MODE OVERRIDE ---
        if (isAnyoneSick(i)) {
            addToHistory("${getTvName(i)}: Morning routine bypassed (Sick/Exhausted mode active).")
            continue
        }
        
        def m1 = settings["morningMotion1_${i}"]
        def m2 = settings["morningMotion2_${i}"]
        
        if (m1?.id == deviceId || m2?.id == deviceId) {
            
            // Track exactly when this specific sensor fired
            if (!state.morningSensorTimes) state.morningSensorTimes = [:]
            state.morningSensorTimes["${i}_${deviceId}"] = now
            
            // If the user configured TWO sensors, enforce the 30-second rule
            if (m1 && m2) {
                def otherDeviceId = (m1.id == deviceId) ? m2.id : m1.id
                def otherTime = state.morningSensorTimes["${i}_${otherDeviceId}"] as Long ?: 0
                
                if ((now - otherTime) > 30000) {
                    // The other sensor hasn't triggered yet, or it's been more than 30 seconds. Wait for it.
                    continue
                }
            }
            
            // Enforce Good Night Switch condition
            def gnSwitch = settings["morningGoodNightSwitch_${i}"]
            if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
                continue // Good Night switch is ON, abort routine
            }

            def today = new Date().format("yyyy-MM-dd", location.timeZone)
            if (state.morningRoutineRunDate["${i}"] == today) continue 
            
            def allowedModes = settings["morningModes_${i}"]
            if (allowedModes && !allowedModes.contains(location.mode)) continue
            
            def startTime = settings["morningTimeStart_${i}"]
            def endTime = settings["morningTimeEnd_${i}"]
            if (startTime && endTime && !timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)) continue
            
            // All conditions met - execute the routine
            state.morningRoutineRunDate["${i}"] = today
            def target = null // Will be resolved dynamically by executeMediaAction
            def duration = settings["morningDuration_${i}"]
            
            triggerRoutine(i, target, "morning")
            
            if (duration) {
                runIn((duration * 60) + 15, "endMorningRoutine", [data: [tvNum: i], overwrite: false])
            }
        }
    }
}

def endMorningRoutine(data) {
    def i = data.tvNum as Integer
    addToHistory("${getTvName(i)}: Morning routine duration met.")
    endRoutine(i, "morning")
}

def dailyWeatherMotionHandler(evt) {
    if (evt.value != "active" || isSystemPaused()) return
    def deviceId = evt.device.id
    def now = new Date().time
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableDailyWeatherRoutine_${i}"] && settings["dailyWeatherMotion_${i}"]?.id == deviceId) {
            
            // --- SICK MODE OVERRIDE ---
            if (isAnyoneSick(i)) {
                addToHistory("${getTvName(i)}: Daily Weather routine bypassed (Sick/Exhausted mode active).")
                continue
            }

            // Allow it to run again if it's been at least 4 hours (to support morning AND evening)
            def lastRun = state.dailyWeatherLastRun["${i}"] ?: 0
            if ((now - lastRun) < 14400000) continue // 4 hours in milliseconds
            
            def allowedModes = settings["dailyWeatherModes_${i}"]
            if (allowedModes && !allowedModes.contains(location.mode)) continue
            
            def allowedDays = settings["dailyWeatherDays_${i}"]
            if (allowedDays) {
                def today = new Date().format("EEEE", location.timeZone)
                if (!allowedDays.contains(today)) continue
            }
            
            def startTime = settings["dailyWeatherTimeStart_${i}"]
            def endTime = settings["dailyWeatherTimeEnd_${i}"]
            
            if (startTime && endTime && !timeOfDayIsBetween(timeToday(startTime, location.timeZone), timeToday(endTime, location.timeZone), new Date(), location.timeZone)) continue
            
            state.dailyWeatherLastRun["${i}"] = now
            def target = null // Will be resolved dynamically by executeMediaAction
            def duration = settings["dailyWeatherDuration_${i}"]
            
            triggerRoutine(i, target, "dailyweather")
            
            if (duration) {
                runIn((duration * 60) + 15, "endDailyWeatherRoutine", [data: [tvNum: i], overwrite: false])
            }
        }
    }
}

def endDailyWeatherRoutine(data) {
    def i = data.tvNum as Integer
    addToHistory("${getTvName(i)}: Daily weather routine duration met.")
    endRoutine(i, "dailyweather")
}

def weatherSwitchHandler(evt) {
    if (isSystemPaused() || !settings["enableWeatherAlert"]) return
    
    def isOn = evt.value == "on"
    if (!isOn) {
        // Check if ANY of the weather switches are still active
        def anyStillOn = false
        def weatherSwitches = settings["weatherSwitch"]
        if (weatherSwitches) {
            def swList = weatherSwitches instanceof List ? weatherSwitches : [weatherSwitches]
            anyStillOn = swList.any { it.currentValue("switch") == "on" }
        }
        
        if (!anyStillOn) {
            endWeatherAlert()
        }
        return
    }
    
    state.weatherAlertActive = true
    state.tvWasOffBeforeWeather = [:]
    if (settings["bmsPriorityLock"]) settings["bmsPriorityLock"].on()
    
    def selectedTvs = settings["weatherAlertTVs"] ?: []
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (!selectedTvs.contains(i.toString())) continue // Skip TVs not selected for the alert
        
        def tv = getPrimaryDevice(i)
        def target = settings["weatherChannel"]
        def appSwitch = settings["weatherAppSwitch"]
        
        if (tv) {
            if (!isTvActuallyOn(tv, i)) {
                state.tvWasOffBeforeWeather["${i}"] = true
                triggerRoutine(i, target, "weather")
            } else {
                state.tvWasOffBeforeWeather["${i}"] = false
                if (target || appSwitch) {
                    runInMillis(4000, "executeMediaAction", [data: [tvNum: i, channel: target, source: "weather"], overwrite: false])
                }
            }
         }
    }
    def timeout = settings["weatherTimeout"] ?: 0
    if (timeout > 0) runIn(timeout * 60, "endWeatherAlert", [overwrite: true])
}

def endWeatherAlert() {
    if (!state.weatherAlertActive) return
    state.weatherAlertActive = false
    unschedule("endWeatherAlert")
    
    def selectedTvs = settings["weatherAlertTVs"] ?: []
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (!selectedTvs.contains(i.toString())) continue
        
        def tv = getPrimaryDevice(i)
        if (tv && state.tvWasOffBeforeWeather["${i}"]) {
            endRoutine(i, "weather")
        }
    }
    state.tvWasOffBeforeWeather = [:]
    
    // Unlock priority since Weather is the only system controlling it now
    if (settings["bmsPriorityLock"]) settings["bmsPriorityLock"].off()
}

def executeSetChannel(data) {
    def i = data.tvNum
    def tv = settings["tv_${i}"]
    if (tv) {
        def currentInput = tv.currentValue("mediaInputSource")
        if (currentInput != "Antenna TV" && currentInput != "InputTuner" && currentInput != "Tuner" && currentInput != "TV") {
            if (tv.hasCommand("input_Tuner")) tv.input_Tuner()
            else if (tv.hasCommand("keyPress")) tv.keyPress("InputTuner")
            else if (tv.hasCommand("setInputSource")) tv.setInputSource("TV")
        }
        runInMillis(6000, "finalizeSetChannel", [data: [tvNum: i, channel: data.channel], overwrite: false])
    }
}

def finalizeSetChannel(data) {
     def i = data.tvNum
    def tv = settings["tv_${i}"]
    if (tv) {
        def cleanChannel = data.channel.toString().trim()
        if (tv.hasCommand("tuneChannel")) {
            tv.tuneChannel(cleanChannel)
        } else if (tv.hasCommand("setChannel")) {
             try {
                tv.setChannel(cleanChannel as Number)
            } catch(e) {
                log.error "Could not set channel: ${e}"
            }
        }
    }
}

def contactHandler(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def action = evt.value == "open" ? "mute" : "unmute"
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableSafetyMute_${i}"] && settings["muteContacts_${i}"]?.any { it.id == deviceId }) {
            interruptTV(i, action)
        }
    }
}

def buttonHandler(evt) {
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numTVs as Integer); i++) {
        if (settings["enableSafetyMute_${i}"] && settings["doorbellButtons_${i}"]?.any { it.id == deviceId }) {
            interruptTV(i, "mute")
            def muteTime = settings["doorbellMuteTime_${i}"] ?: 60
            runIn(muteTime as Integer, "restoreDoorbellTV", [data: [tvNum: i], overwrite: false])
        }
    }
}

def restoreDoorbellTV(data) {
    interruptTV(data.tvNum, "unmute")
}

def interruptTV(i, act) {
    def tv = getPrimaryDevice(i)
    
    if (isTvActuallyOn(tv, i)) {
        if (act == "mute") {
            if (tv.hasCommand("mute")) tv.mute()
            else if (tv.hasCommand("setMute")) tv.setMute(true)
        } else if (act == "unmute") {
            if (tv.hasCommand("unmute")) tv.unmute()
            else if (tv.hasCommand("setMute")) tv.setMute(false)
            else if (tv.hasCommand("mute")) tv.mute() // IR Toggle Fallback
        }
        pauseExecution(300)
    }
}

def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    log.info "HISTORY: [${timestamp}] ${cleanMsg}"
}

def getTvName(tNum) {
    return settings["tvName_${tNum}"] ?: "TV ${tNum}"
}

def testMorningRoutine(i) {
    def target = null // Re-evaluated dynamically during execution
    def duration = settings["morningDuration_${i}"]
    
    addToHistory("${getTvName(i)}: Morning routine TEST initiated via button.")
    triggerRoutine(i, target, "morning")
    
    if (duration) {
        runIn((duration * 60) + 15, "endMorningRoutine", [data: [tvNum: i], overwrite: false])
    }
}

def stopMorningRoutineTest(i) {
    addToHistory("${getTvName(i)}: Morning routine TEST stopped via button.")
    endRoutine(i, "morning")
}

def testDailyWeatherRoutine(i) {
    def target = null
    def duration = settings["dailyWeatherDuration_${i}"]
    
    addToHistory("${getTvName(i)}: Weather routine TEST initiated via button.")
    triggerRoutine(i, target, "dailyweather")
    
    if (duration) {
        runIn((duration * 60) + 15, "endDailyWeatherRoutine", [data: [tvNum: i], overwrite: false])
    }
}

def stopDailyWeatherRoutineTest(i) {
    addToHistory("${getTvName(i)}: Weather routine TEST stopped via button.")
    endRoutine(i, "dailyweather")
}

def activateIntermission(data) {
    def i = data.tvNum
    def lights = settings["intermissionLights_${i}"]
    if (!lights) return
    
    def tv = getPrimaryDevice(i)
    if (!isTvActuallyOn(tv, i)) return // Safety check
    
    // --- Daylight / Ambient Light Check ---
    def overcast = settings["intermissionOvercast_${i}"]
    def blinds = settings["intermissionBlinds_${i}"]
    
    def isOvercast = overcast && overcast.currentValue("switch") == "on"
    def areBlindsOpen = blinds && blinds.any { it.currentValue("contact") == "open" }
    
    if (areBlindsOpen && !isOvercast) {
        addToHistory("${getTvName(i)}: Media Paused. Intermission lights bypassed (Natural daylight detected).")
        return // Stop here, do not activate lights
    }
    // --------------------------------------
    
    addToHistory("${getTvName(i)}: Media Paused. Activating Cinema Intermission.")
    
    // Save current state before modifying
    def savedStates = [:]
    lights.each { bulb ->
        savedStates[bulb.id] = [
            switch: bulb.currentValue("switch"),
            level: bulb.currentValue("level") // Will safely return null if it's just a switch
        ]
    }
    
    if (!state.intermissionSavedState) state.intermissionSavedState = [:]
    state.intermissionSavedState["${i}"] = savedStates
    state.intermissionActive["${i}"] = true
    
    def targetLevel = settings["intermissionLevel_${i}"] ?: 25
    lights.eachWithIndex { bulb, idx -> 
        if (bulb.hasCommand("setLevel")) {
            runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "intermissionLights_${i}", command: "setLevel", value1: targetLevel, targetId: bulb.id], overwrite: false])
        } else {
            runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "intermissionLights_${i}", command: "on", targetId: bulb.id], overwrite: false])
        }
    }
}

def restoreIntermission(i) {
    def lights = settings["intermissionLights_${i}"]
    if (!lights) return
    
    addToHistory("${getTvName(i)}: Media Resumed. Restoring lights from Intermission.")
    
    def savedStates = state.intermissionSavedState["${i}"] ?: [:]
    
    lights.eachWithIndex { bulb, idx -> 
        def saved = savedStates[bulb.id]
        if (saved) {
            if (saved.switch == "off") {
                runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "intermissionLights_${i}", command: "off", targetId: bulb.id], overwrite: false])
            } else {
                if (bulb.hasCommand("setLevel") && saved.level != null) {
                    def lvl = saved.level ?: 100
                    runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "intermissionLights_${i}", command: "setLevel", value1: lvl, targetId: bulb.id], overwrite: false])
                } else {
                    runInMillis(idx * 300, "asyncDeviceCommand", [data: [tvNum: i, settingName: "intermissionLights_${i}", command: "on", targetId: bulb.id], overwrite: false])
                }
            }
        }
    }
    state.intermissionActive["${i}"] = false
}

// --- Dynamic Wrappers for Motion Timeouts ---
def executeTvTimeout_1() { executeTvTimeout([tvNum: 1]) }
def executeTvTimeout_2() { executeTvTimeout([tvNum: 2]) }
def executeTvTimeout_3() { executeTvTimeout([tvNum: 3]) }
def executeTvTimeout_4() { executeTvTimeout([tvNum: 4]) }
def executeTvTimeout_5() { executeTvTimeout([tvNum: 5]) }
def executeTvTimeout_6() { executeTvTimeout([tvNum: 6]) }
def executeTvTimeout_7() { executeTvTimeout([tvNum: 7]) }
def executeTvTimeout_8() { executeTvTimeout([tvNum: 8]) }
def executeTvTimeout_9() { executeTvTimeout([tvNum: 9]) }
def executeTvTimeout_10() { executeTvTimeout([tvNum: 10]) }

// --- Dynamic Wrappers for Acoustic Management ---
def triggerAcousticEval_1() { evaluateAcoustics(1) }
def triggerAcousticEval_2() { evaluateAcoustics(2) }
def triggerAcousticEval_3() { evaluateAcoustics(3) }
def triggerAcousticEval_4() { evaluateAcoustics(4) }
def triggerAcousticEval_5() { evaluateAcoustics(5) }
def triggerAcousticEval_6() { evaluateAcoustics(6) }
def triggerAcousticEval_7() { evaluateAcoustics(7) }
def triggerAcousticEval_8() { evaluateAcoustics(8) }
def triggerAcousticEval_9() { evaluateAcoustics(9) }
def triggerAcousticEval_10() { evaluateAcoustics(10) }

// --- Device Helpers ---
def getPrimaryDevice(i) {
    return settings["tv_${i}"]
}
