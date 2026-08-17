/**
 * Advanced House Announcements 2.0
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced House Announcements 2.0",
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
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            // Re-evaluate moods on page load to keep dashboard fresh
            evaluateMoods()
            
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"

            // --- Unified Dashboard HTML ---
            def currentLocMode = location.mode ?: "Unknown"
            def isLocNight = currentLocMode?.toLowerCase()?.contains("night")
            def defaultSpkrs = defaultZooz ? defaultZooz.collect { it.displayName }.join(", ") : "None Selected"
            def chimeStatus = (settings.enableGlobalChime != false) ? (globalChimeFile ?: "Not Configured") : "Disabled"
            def delayStatus = (settings.enableGlobalChime != false) ? "${globalDelay ?: 1500} ms" : "N/A"
            def doorChimeStatus = doorChimeSensors ? "Target: ${doorChimeDevices ? doorChimeDevices.collect{it.displayName}.join(', ') : 'None Selected'}" : "Not Configured"
            
            // --- PSYCHOLOGICAL AUTO-REGULATION DASHBOARD DISPLAY ---
            def m1 = settings.moodVarU1 ? (getGlobalVar(settings.moodVarU1)?.value ?: "😐") : "N/A"
            def m2 = settings.moodVarU2 ? (getGlobalVar(settings.moodVarU2)?.value ?: "😐") : "N/A"
            def m3 = settings.moodVarU3 ? (getGlobalVar(settings.moodVarU3)?.value ?: "😐") : "N/A"
            
            def n1 = settings.userName1 ?: "Shane"
            def n2 = settings.userName2 ?: "Christy"
            def n3 = settings.userName3 ?: "Leanne"
            
            def moodIcons = ""
            if (m1 != "N/A") moodIcons += "${n1}: <b>${m1}</b> "
            if (m2 != "N/A") moodIcons += (moodIcons ? "| " : "") + "${n2}: <b>${m2}</b> "
            if (m3 != "N/A") moodIcons += (moodIcons ? "| " : "") + "${n3}: <b>${m3}</b>"
            if (!moodIcons) moodIcons = "No Users Configured"
            
            def isMoodMuted = state.isMoodMuted ?: false
            def moodStatus = isMoodMuted ? "<span style='color:blue; font-weight:bold;'>Active (Silencing selected alerts)</span>" : "<span style='color:gray;'>Idle (Moods Normal)</span>"

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
                <thead><tr><th colspan="2">Global Audio Overview</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Current Location Mode</td><td class="dash-val">${currentLocMode}</td></tr>
                    <tr><td class="dash-hl">Global Alert Chime File</td><td class="dash-val">${chimeStatus}</td></tr>
                    <tr><td class="dash-hl">Voice Delay / Buffer</td><td class="dash-val">${delayStatus}</td></tr>
                    <tr><td class="dash-hl">Dedicated Door Chime</td><td class="dash-val">${doorChimeStatus}</td></tr>
                    <tr><td class="dash-hl">Psychological Auto-Reg</td><td class="dash-val">${moodIcons}<br><span style='font-size:12px; color:#555;'>Result: ${moodStatus}</span></td></tr>
                    <tr><td colspan="2" class="dash-subhead">Global/Default Configuration</td></tr>
                    <tr><td class="dash-hl">Default Speaker(s)</td><td class="dash-val">${defaultSpkrs}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>Room Presence Breakdown</b>", hideable: true) {
            def roomHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Room Name</th><th>Occupied?</th><th>Status</th><th>Target Speakers</th></tr></thead><tbody>"
            def hasRooms = false
            
            def occTimeout = settings.occupancyTimeout != null ? settings.occupancyTimeout : 2
            def maxAgeMs = occTimeout * 60000 
            
            // Show Default Zone in dashboard always active
            if (settings.defaultZooz) {
                hasRooms = true
                def dOccupied = "N/A"
                def dStatus = "<span style='color:green;'>Always Active (Global)</span>"
                
                if (isLocNight) {
                    dStatus = "<span style='color:orange;'>Check Mode Restrictions</span>"
                } 
                
                def defaultSpkrsDash = settings.defaultZooz ? settings.defaultZooz.collect { it.displayName }.join("<br>") : "None"
                roomHTML += "<tr><td><b>Default (Global)</b></td><td>${dOccupied}</td><td>${dStatus}</td><td><span style='font-size:12px;'>${defaultSpkrsDash}</span></td></tr>"
            }
 
            def weatherTitles = [
                "sprinkling":"Sprinkling", 
                "raining":"Raining", 
                "predictedRain":"Predicted Rain",
                "tstormWatch":"T-Storm Watch", 
                "tstormWarning":"T-Storm Warning", 
                "tornadoWatch":"Tornado Watch", 
                "tornadoWarning":"Tornado Warning", 
                "floodWatch":"Flood Watch", 
                "floodWarning":"Flood Warning",
                "heatWatch":"Heat Watch",
                "heatWarning":"Heat Warning",
                "overcastOn":"Overcast On",
                "overcastOff":"Overcast Off"
            ]

            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    hasRooms = true
                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    def rMotion = settings["roomMotion_${i}"]
                    def gnSwitch = settings["roomGNSwitch_${i}"]
                    def forceSwitch = settings["roomForceSwitch_${i}"]
                    def spkrs = settings["roomZooz_${i}"] ? settings["roomZooz_${i}"].collect { it.displayName }.join("<br>") : "None"
                    
                    def isSleep = gnSwitch && gnSwitch.currentValue("switch") == "on"
                    def isForcedActive = forceSwitch && forceSwitch.currentValue("switch") == "on"
                    def lastActive = state."roomLastActive_${i}" ?: 0
                    
                    // Failsafe live check
                    if (rMotion?.any { it.currentValue("motion") == "active" }) {
                        lastActive = now()
                    }
                    
                    def isOccupied = "No"
                    def rStatus = "<span style='color:gray;'>Idle</span>"
       
                    if (isForcedActive) {
                        isOccupied = "Yes (Forced)"
                        rStatus = "<span style='color:blue;'>Active (Force Switch ON)</span>"
                    } else if (isSleep) {
                        isOccupied = "No (Sleeping)"
                        
                        def bypassedWeather = []
                        weatherTitles.each { k, v ->
                            if (settings["${k}Switch"] && settings["${k}BypassRooms"]?.contains(i.toString())) {
                                bypassedWeather << v
                            }
                        }
                        def mBypass = settings.motionBypassRooms?.contains(i.toString())
                        def modeBypass = settings.modeBypassRooms?.contains(i.toString())
                        def busBypass = settings.busBypassRooms?.contains(i.toString())
                        def aBypass = settings.applianceBypassRooms?.contains(i.toString())
                        def tBypass = settings.trashBypassRooms?.contains(i.toString())
                        def gBypass = settings.gardenBypassRooms?.contains(i.toString())
                        def airBypass = settings.airQualityBypassRooms?.contains(i.toString())
                        def dbBypass = settings.doorbellBypassRooms?.contains(i.toString())
                        def fBypass = settings.filterBypassRooms?.contains(i.toString())
                        def dtBypass = settings.dehumidifierTankBypassRooms?.contains(i.toString())
                        
                        if (bypassedWeather || mBypass || modeBypass || busBypass || aBypass || tBypass || gBypass || airBypass || dbBypass || fBypass || dtBypass) {
                            def allowedAlerts = []
                            if (mBypass) allowedAlerts << "Motion"
                            if (modeBypass) allowedAlerts << "Mode"
                            if (busBypass) allowedAlerts << "Bus"
                            if (aBypass) allowedAlerts << "Appliance"
                            if (tBypass) allowedAlerts << "Trash"
                            if (gBypass) allowedAlerts << "Garden"
                            if (airBypass) allowedAlerts << "Air Quality"
                            if (dbBypass) allowedAlerts << "Doorbell"
                            if (fBypass) allowedAlerts << "Filter"
                            if (dtBypass) allowedAlerts << "Tank"
                            if (bypassedWeather) allowedAlerts.addAll(bypassedWeather)
                            rStatus = "<span style='color:purple;'>Asleep (${allowedAlerts.join(', ')} Allowed)</span>"
                        } else {
                            rStatus = "<span style='color:red;'>Asleep (All Alerts Blocked)</span>"
                        }
                    } else if (isLocNight) {
                        isOccupied = "No (Night Mode)"
                        rStatus = "<span style='color:orange;'>Motion Ignored (Night Mode)</span>"
                    } else if ((now() - lastActive) <= maxAgeMs) {
                        isOccupied = "Yes"
                        def remainingMins = Math.max(0, Math.round((maxAgeMs - (now() - lastActive)) / 60000))
                        rStatus = "<span style='color:green;'>Active (${remainingMins}m until Idle)</span>"
                    }
    
                    roomHTML += "<tr><td><b>${rName}</b></td><td>${isOccupied}</td><td>${rStatus}</td><td><span style='font-size:12px;'>${spkrs}</span></td></tr>"
                }
            }
            
            roomHTML += "</tbody></table>"
            if (hasRooms) paragraph roomHTML else paragraph "<i>No rooms configured yet.</i>"
        }

        section("<b>Recent Action History</b>", hideable: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            } else {
                paragraph "<i>No recent announcements logged.</i>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>App Control</b>", hideable: true, hidden: true) {
            input "masterEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
        }
        
        section("<b>🛑 Visitor Mode (Selective Muting)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> When this virtual switch is ON, any announcement categories selected below will be silently ignored.</div>"
            input "visitorSwitch", "capability.switch", title: "Visitor Virtual Switch", required: false, submitOnChange: true
            
            if (visitorSwitch) {
                def muteOptions = [
                    "Mailbox", 
                    "Outdoor Motion", 
                    "Weather", 
                    "Mode Changes", 
                    "Door Chimes", 
                    "Bus", 
                    "Appliance", 
                    "Trash", 
                    "Garden", 
                    "Air Quality", 
                    "Doorbell", 
                    "Filter Reminders", 
                    "Dehumidifier Tanks"
                ]
                input "visitorMuteOptions", "enum", title: "Select Announcements to Mute When Visitor Switch is ON:", options: muteOptions, multiple: true, required: false
            }
        }

        section("<b>15. 🧠 Psychological Comfort (Mood Sync)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Reads the mood of up to 3 users from the House Management dashboard. If the configured number of users are stressed, angry, or sick, the system automatically mutes the selected announcement categories to provide a quiet, peaceful environment.</div>"
            input "userName1", "text", title: "User 1 Name", defaultValue: "Shane", required: false
            input "moodVarU1", "hubVariable", title: "User 1 Mood Variable", required: false
            input "userName2", "text", title: "User 2 Name", defaultValue: "Christy", required: false
            input "moodVarU2", "hubVariable", title: "User 2 Mood Variable", required: false
            input "userName3", "text", title: "User 3 Name", defaultValue: "Leanne", required: false
            input "moodVarU3", "hubVariable", title: "User 3 Mood Variable", required: false
            
            def muteOptions = [
                "Mailbox", "Outdoor Motion", "Weather", "Mode Changes", "Door Chimes", 
                "Bus", "Appliance", "Trash", "Garden", "Air Quality", "Doorbell", 
                "Filter Reminders", "Dehumidifier Tanks"
            ]
            input "moodMuteOptions", "enum", title: "Select Announcements to Mute When Users are Sick/Stressed:", options: muteOptions, multiple: true, required: false
            input "moodMuteThreshold", "enum", title: "How many users must be negative to trigger mute?", options: ["1":"1 User (Anyone)", "2":"2 Users", "3":"3 Users"], defaultValue: "1"
        }

        section("<b>0. 🔔 Global Audio Setup</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Configure the standard alert chime and delay that will play before ALL voice announcements.</div>"
            input "enableGlobalChime", "bool", title: "Enable Pre-Chime Announcement", defaultValue: true, submitOnChange: true
            
            if (settings.enableGlobalChime != false) {
                input "globalChimeFile", "number", title: "Global Chime File # (e.g., 10)", required: true
                input "globalDelay", "number", title: "Delay before voice (Milliseconds)", defaultValue: 1500, required: true, description: "1000ms = 1 second."
            }
        }

        section("<b>1. 📍 Intelligent Room Tracking (Presence Engine)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Routes announcements to the room you are currently in AND always to the Default Speaker(s).</div>"
            
            input "occupancyTimeout", "number", title: "Room Occupancy Timeout (Minutes)", defaultValue: 2, required: true, submitOnChange: true
            
            paragraph "<div style='background-color:#fff3cd; padding:10px; border-radius:5px; border-left:5px solid #ffc107; margin-bottom: 10px;'>"
            input "defaultZooz", "capability.chime", title: "<b>Default / Global Zooz Speaker(s)</b>", multiple: true, required: true, description: "ALWAYS plays on these speakers."
            input "defaultSonos", "capability.musicPlayer", title: "<b>Default / Global Sonos Speaker(s)</b>", multiple: true, required: false
            input "defaultRoku", "capability.audioVolume", title: "<b>Default / Global Roku TV(s)</b>", multiple: true, required: false
            input "defaultModes", "mode", title: "Active Modes for Default Speaker", multiple: true, required: false, description: "Leave blank to allow in all modes."
            paragraph "</div>"

            for (int i = 1; i <= 5; i++) {
                input "enableRoom_${i}", "bool", title: "<b>Configure Room ${i}</b>", defaultValue: false, submitOnChange: true
                if (settings["enableRoom_${i}"]) {
                    input "roomName_${i}", "text", title: "Room Name (e.g., Living Room)", required: true
                    input "roomMotion_${i}", "capability.motionSensor", title: "Motion Sensor(s) for this Room", multiple: true, required: true
                    input "roomGNSwitch_${i}", "capability.switch", title: "Good Night / Sleep Switch", required: false, description: "If ON, room is 'Asleep'. Standard alerts are blocked to prevent waking you up."
                    input "roomForceSwitch_${i}", "capability.switch", title: "Force Active Switch", required: false, description: "If ON, this room is permanently active."
                    input "roomZooz_${i}", "capability.chime", title: "► Target Zooz Speaker(s) for this Room", multiple: true, required: true
                    input "roomSonos_${i}", "capability.musicPlayer", title: "🎵 Target Sonos Speaker(s) to Mute", multiple: true, required: false
                    input "roomRoku_${i}", "capability.audioVolume", title: "📺 Target Roku TV(s) to Mute", multiple: true, required: false
                    input "roomModes_${i}", "mode", title: "Active Modes for this Room's Speaker", multiple: true, required: false, description: "Leave blank to allow in all modes."
                    paragraph "<hr>"
                }
            }
        }

        section("<b>2. 📬 Mailbox Announcements</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers the globally configured chime, followed by the selected voice file, routed to the active room.</div>"
            
            input "mailSwitch", "capability.switch", title: "Mail Virtual Switch Trigger", required: false, submitOnChange: true
            if (mailSwitch) {
                input "mailMsgFile", "number", title: "Voice Announcement File # (e.g., 20)", required: true
                input "mailModes", "mode", title: "Modes to ALLOW Mail Announcements", multiple: true, required: false, description: "Leave blank to allow 24/7."
                input "testBtn_mail", "button", title: "🔊 Test Mail Sequence (Plays on ALL Speakers)"
            }
        }

        section("<b>3. 🏃 Outdoor Motion Announcements</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers an alert sequence based on outdoor motion activity. Routes to the active room. Includes debounce to prevent spam.</div>"
            
            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }
            
            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "motionBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Motion Alerts even if their Good Night switch is ON (Sleep Mode):", options: roomOptions, multiple: true, required: false
            }

            input "motionDebounce", "number", title: "Global Motion Cooldown (Minutes)", defaultValue: 5, required: true, description: "Wait this long before re-announcing motion in the same zone."
            
            // --- PUSH NOTIFICATIONS ---
            paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>📱 Push Notifications (Independent of Audio)</b></div>"
            input "pushSensors", "capability.motionSensor", title: "Select Motion Sensors for Push Notifications", multiple: true, required: false, submitOnChange: true
            if (pushSensors) {
                input "pushDevices", "capability.notification", title: "Notification Device(s) to Send Push To", multiple: true, required: true
                input "pushModes", "mode", title: "Modes to ALLOW Push Notifications (e.g., Away)", multiple: true, required: false
            }
            
            // --- AUDIO NOTIFICATIONS ---
            paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🔊 Audio Announcements</b></div>"
            input "outdoorMotion", "capability.motionSensor", title: "Select Outdoor Motion Sensors for Audio", required: false, multiple: true, submitOnChange: true
            
            if (outdoorMotion) {
                outdoorMotion.each { dev ->
                    paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>${dev.displayName} Config</b></div>"
                    input "outContact_${dev.id}", "capability.contactSensor", title: "🚪 Suppress if these doors are open (or opened in last 5 mins)", multiple: true, required: false, description: "Prevents announcements if you just walked outside."
                    input "muteWeatherSwitches_${dev.id}", "capability.switch", title: "⛈️ Suppress motion if these Weather/Rain switches are ON", multiple: true, required: false, description: "Prevents false alerts caused by heavy rain or storms."
                    input "outMsgFile_${dev.id}", "number", title: "Voice Announcement File #", required: true
                    input "outModes_${dev.id}", "mode", title: "Modes to ALLOW this Motion Announcement", multiple: true, required: false
                    input "testBtn_outMotion_${dev.id}", "button", title: "🔊 Test Audio Sequence (Plays on ALL Speakers)"
                }
            }
        }

        section("<b>4. ⛈️ Weather & Severe Alerts</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers immediate announcements when these weather virtual switches turn ON. Routes to the active room(s).</div>"
            
            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }

            def weatherAlerts = [
                "Sprinkling": "sprinkling",
                "Raining": "raining",
                "Predicted Rain": "predictedRain",
                "Thunderstorm Watch": "tstormWatch",
                "Thunderstorm Warning": "tstormWarning",
                "Tornado Watch": "tornadoWatch",
                "Tornado Warning": "tornadoWarning",
                "Flood Watch": "floodWatch",
                "Flood Warning": "floodWarning",
                "Heat Watch": "heatWatch",
                "Heat Warning": "heatWarning",
                "Overcast On": "overcastOn",
                "Overcast Off": "overcastOff"
            ]
            
            weatherAlerts.each { title, key ->
                input "${key}Switch", "capability.switch", title: "${title} Virtual Switch", required: false, submitOnChange: true
                if (settings["${key}Switch"]) {
                    input "${key}MsgFile", "number", title: "↳ ${title} Voice File #", required: true
                    input "${key}Modes", "mode", title: "↳ Modes to ALLOW ${title} Alerts", multiple: true, required: false, description: "Leave blank for 24/7 (Recommended for Warnings)."
                    
                    if (roomOptions) {
                        input "${key}BypassRooms", "enum", title: "↳ Rooms to ALWAYS play this alert even if Good Night is ON:", options: roomOptions, multiple: true, required: false
                    }
                    input "testBtn_weather_${key}", "button", title: "🔊 Test ${title} Sequence (Plays on ALL Speakers)"
                    paragraph "<hr style='border-top: 1px dashed #ccc;'>"
                }
            }
        }

        section("<b>5. 🏠 Mode Change Announcements</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers a specific voice announcement whenever the house mode changes (e.g., Home, Away, Night).</div>"
            
            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }
            
            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "modeBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Mode Alerts even if their Good Night switch is ON (Sleep Mode):", options: roomOptions, multiple: true, required: false
            }

            input "modeAnnounceSelection", "mode", title: "Select Location Modes to Announce When Activated", multiple: true, submitOnChange: true
            
            if (modeAnnounceSelection) {
                modeAnnounceSelection.each { md ->
                    def safeMd = md.replace(" ", "")
                    paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>Mode: ${md} Config</b></div>"
                    input "modeMsgFile_${safeMd}", "number", title: "Voice File # for ${md} Activation", required: true
                    input "testBtn_mode_${safeMd}", "button", title: "🔊 Test Mode Sequence (Plays on ALL Speakers)"
                }
            }
        }

        section("<b>6. 🚪 Dedicated Door Open Chimes</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Plays a specific chime sound whenever selected doors are opened. Routes to dedicated chime devices rather than standard room voice speakers.</div>"

            input "doorChimeSensors", "capability.contactSensor", title: "Select Doors to Trigger Chime", required: false, multiple: true, submitOnChange: true
            
            if (doorChimeSensors) {
                input "doorChimeDebounce", "number", title: "Door Chime Cooldown (Seconds)", defaultValue: 15, required: true, description: "Wait this long before allowing the same door to trigger another chime."
                input "doorChimeDevices", "capability.chime", title: "Dedicated Door Chime Device(s)", required: true, multiple: true, description: "Select the custom devices that will play the door chimes."
                input "doorChimeFile", "number", title: "Door Chime File # (e.g., 21)", required: true
                input "doorChimeModes", "mode", title: "Modes to ALLOW Door Chimes", multiple: true, required: false
                input "testBtn_doorChime", "button", title: "🔊 Test Dedicated Door Chime"
            }
        }

        section("<b>7. 🚌 Bus Announcements</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers announcements when specific bus tracking virtual switches turn ON. Routes to the active room(s).</div>"

            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }
            
            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "busBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Bus Alerts even if their Good Night switch is ON (Sleep Mode):", options: roomOptions, multiple: true, required: false
            }

            def busAlerts = [
                "bus20": "Bus 20 Minutes Away",
                "bus10": "Bus 10 Minutes Away",
                "busArrived": "Bus Arrived"
            ]

            busAlerts.each { key, title ->
                input "${key}Switch", "capability.switch", title: "${title} Virtual Switch", required: false, submitOnChange: true
                if (settings["${key}Switch"]) {
                    input "${key}MsgFile", "number", title: "↳ ${title} Voice File #", required: true
                    input "${key}Modes", "mode", title: "↳ Modes to ALLOW ${title} Alerts", multiple: true, required: false
                    input "testBtn_bus_${key}", "button", title: "🔊 Test ${title} Sequence (Plays on ALL Speakers)"
                    paragraph "<hr style='border-top: 1px dashed #ccc;'>"
                }
            }
        }

        section("<b>8. 🧺 Appliance Alerts</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers announcements when specific appliance virtual switches turn ON indicating a cycle is complete. Routes to the active room(s).</div>"

            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }

            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "applianceBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Appliance Alerts even if their Good Night switch is ON:", options: roomOptions, multiple: true, required: false
            }

            def applianceAlerts = [
                "washerDryer": "Washer/Dryer Complete",
                "dishwasher": "Dishwasher Complete"
            ]

            applianceAlerts.each { key, title ->
                input "${key}Switch", "capability.switch", title: "${title} Virtual Switch", required: false, submitOnChange: true
                if (settings["${key}Switch"]) {
                    input "${key}MsgFile", "number", title: "↳ ${title} Voice File #", required: true
                    input "${key}Modes", "mode", title: "↳ Modes to ALLOW ${title} Alerts", multiple: true, required: false
                    input "testBtn_appliance_${key}", "button", title: "🔊 Test ${title} Sequence"
                    paragraph "<hr style='border-top: 1px dashed #ccc;'>"
                }
            }
        }

        section("<b>9. 🗑️ Trash Announcements & Reminders</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers announcements when the specific trash virtual switches turn ON. Routes to the active room(s).</div>"

            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }

            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "trashBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Trash Alerts even if their Good Night switch is ON:", options: roomOptions, multiple: true, required: false
            }

            paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>Trash Emptied Event</b></div>"
            input "trashEmptiedSwitch", "capability.switch", title: "Trash Emptied Virtual Switch", required: false, submitOnChange: true
            if (settings.trashEmptiedSwitch) {
                input "trashEmptiedMsgFile", "number", title: "↳ Trash Emptied Voice File #", required: true
                input "trashEmptiedModes", "mode", title: "↳ Modes to ALLOW Trash Emptied Alert", multiple: true, required: false
                input "testBtn_trash_emptied", "button", title: "🔊 Test Trash Emptied Sequence"
            }

            paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>Take Bin to Curb Reminder</b></div>"
            input "trashReminderSwitch", "capability.switch", title: "Take Bin to Curb Virtual Switch", required: false, submitOnChange: true
            if (settings.trashReminderSwitch) {
                input "trashReminderMsgFile", "number", title: "↳ Reminder Voice File #", required: true
                input "trashReminderModes", "mode", title: "↳ Modes to ALLOW Reminder", multiple: true, required: false
                input "testBtn_trash_reminder", "button", title: "🔊 Test Trash Reminder Sequence"
            }
        }

        section("<b>10. 🌻 Garden Announcements</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers announcements when specific garden/watering virtual switches turn ON. Routes to the active room(s).</div>"

            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }

            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "gardenBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Garden Alerts even if their Good Night switch is ON:", options: roomOptions, multiple: true, required: false
            }

            def gardenAlerts = [
                "smartWatering": "Smart Watering Enabled",
                "swapGardenZone": "Swap Garden Zone",
                "wateringComplete": "Watering Complete",
                "needsWatering": "Garden Needs Watering"
            ]

            gardenAlerts.each { key, title ->
                input "${key}Switch", "capability.switch", title: "${title} Virtual Switch", required: false, submitOnChange: true
                if (settings["${key}Switch"]) {
                    input "${key}MsgFile", "number", title: "↳ ${title} Voice File #", required: true
                    input "${key}Modes", "mode", title: "↳ Modes to ALLOW ${title} Alerts", multiple: true, required: false
                    input "testBtn_garden_${key}", "button", title: "🔊 Test ${title} Sequence"
                    paragraph "<hr style='border-top: 1px dashed #ccc;'>"
                }
            }
        }

        section("<b>11. 💨 Air Quality Announcements</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Monitors indoor and outdoor air quality virtual switches. Triggers alerts when they turn ON, and a 'Returned to Normal' alert when BOTH return to OFF. Routes to the active room(s).</div>"

            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }

            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "airQualityBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Air Quality Alerts even if their Good Night switch is ON:", options: roomOptions, multiple: true, required: false
            }

            input "indoorAirSwitch", "capability.switch", title: "Indoor Air Quality Poor Virtual Switch", required: false, submitOnChange: true
            if (settings.indoorAirSwitch) {
                input "indoorAirMsgFile", "number", title: "↳ Indoor Air Poor Voice File #", required: true
                input "testBtn_air_indoor", "button", title: "🔊 Test Indoor Air Poor"
            }

            input "outdoorAirSwitch", "capability.switch", title: "Outdoor Air Quality Poor Virtual Switch", required: false, submitOnChange: true
            if (settings.outdoorAirSwitch) {
                input "outdoorAirMsgFile", "number", title: "↳ Outdoor Air Poor Voice File #", required: true
                input "testBtn_air_outdoor", "button", title: "🔊 Test Outdoor Air Poor"
            }

            if (settings.indoorAirSwitch || settings.outdoorAirSwitch) {
                paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>Air Quality Returned to Normal</b></div>"
                input "airNormalMsgFile", "number", title: "Air Returned to Normal Voice File #", required: true
                input "airQualityModes", "mode", title: "Modes to ALLOW Air Quality Alerts", multiple: true, required: false
                input "testBtn_air_normal", "button", title: "🔊 Test Air Normal"
            }
        }

        section("<b>12. 🔔 Doorbell Announcements</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Monitors a specific button press on a device. When triggered, it randomly selects and plays one of up to 5 configured announcements. Routes to the active room(s).</div>"

            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }

            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "doorbellBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Doorbell Alerts even if their Good Night switch is ON:", options: roomOptions, multiple: true, required: false
            }

            input "doorbellButton", "capability.pushableButton", title: "Select Doorbell Button Device", required: false, submitOnChange: true
            
            if (settings.doorbellButton) {
                input "doorbellButtonNumber", "number", title: "Button Number to Monitor", defaultValue: 1, required: true
                input "doorbellModes", "mode", title: "Modes to ALLOW Doorbell Alerts", multiple: true, required: false
                
                paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>Random Announcement Files</b></div>"
                paragraph "<i>Enter up to 5 file numbers. The app will randomly select one each time the doorbell is pressed.</i>"
                input "doorbellMsgFile_1", "number", title: "Voice File # 1", required: true
                input "doorbellMsgFile_2", "number", title: "Voice File # 2", required: false
                input "doorbellMsgFile_3", "number", title: "Voice File # 3", required: false
                input "doorbellMsgFile_4", "number", title: "Voice File # 4", required: false
                input "doorbellMsgFile_5", "number", title: "Voice File # 5", required: false
                
                input "testBtn_doorbell", "button", title: "🔊 Test Doorbell (Random Selection)"
            }
        }

        section("<b>13. 🧹 Filter Reminders</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers announcements when filter replacement/maintenance virtual switches turn ON for Air Purifiers, HVAC Systems, or Dehumidifiers. Routes to the active room(s).</div>"

            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }

            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "filterBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Filter Alerts even if their Good Night switch is ON:", options: roomOptions, multiple: true, required: false
            }

            input "filterModes", "mode", title: "Modes to ALLOW Filter Reminders", multiple: true, required: false

            // Air Purifier Filter (1)
            paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>Air Purifier Filter</b></div>"
            input "airPurifierSwitch", "capability.switch", title: "Air Purifier Filter Switch", required: false, submitOnChange: true
            if (settings.airPurifierSwitch) {
                input "airPurifierMsgFile", "number", title: "↳ Air Purifier Voice File #", required: true
                input "testBtn_filter_airPurifier", "button", title: "🔊 Test Air Purifier Filter Sequence"
                paragraph "<hr style='border-top: 1px dashed #ccc;'>"
            }

            // HVAC Filters (Up to 3)
            paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>HVAC Filters (Up to 3)</b></div>"
            for (int i = 1; i <= 3; i++) {
                input "hvacSwitch_${i}", "capability.switch", title: "HVAC Filter ${i} Switch", required: false, submitOnChange: true
                if (settings["hvacSwitch_${i}"]) {
                    input "hvacMsgFile_${i}", "number", title: "↳ HVAC Filter ${i} Voice File #", required: true
                    input "testBtn_filter_hvac_${i}", "button", title: "🔊 Test HVAC Filter ${i} Sequence"
                    paragraph "<hr style='border-top: 1px dashed #ccc;'>"
                }
            }

            // Dehumidifier Filter (1)
            paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>Dehumidifier Filter</b></div>"
            input "dehumidifierSwitch", "capability.switch", title: "Dehumidifier Filter Switch", required: false, submitOnChange: true
            if (settings.dehumidifierSwitch) {
                input "dehumidifierMsgFile", "number", title: "↳ Dehumidifier Voice File #", required: true
                input "testBtn_filter_dehumidifier", "button", title: "🔊 Test Dehumidifier Filter Sequence"
                paragraph "<hr style='border-top: 1px dashed #ccc;'>"
            }
        }

        section("<b>14. 💧 Dehumidifier Tanks</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Triggers announcements when a dehumidifier tank full virtual switch turns ON. Routes to the active room(s).</div>"

            def roomOptions = [:]
            for (int i = 1; i <= 5; i++) {
                if (settings["enableRoom_${i}"]) {
                    roomOptions[i.toString()] = settings["roomName_${i}"] ?: "Room ${i}"
                }
            }

            if (roomOptions) {
                paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>🌙 Good Night / Sleep Mode Bypass</b></div>"
                input "dehumidifierTankBypassRooms", "enum", title: "Select rooms that should ALWAYS receive Tank Alerts even if their Good Night switch is ON:", options: roomOptions, multiple: true, required: false
            }

            input "dehumidifierTankModes", "mode", title: "Modes to ALLOW Tank Alerts", multiple: true, required: false

            // Dehumidifier Tank (1)
            paragraph "<div style='background:#e9ecef; padding:5px; margin-top:10px;'><b style='color:#1a73e8;'>Dehumidifier Tank</b></div>"
            input "dehumidifierTankSwitch", "capability.switch", title: "Dehumidifier Tank Full Switch", required: false, submitOnChange: true
            if (settings.dehumidifierTankSwitch) {
                input "dehumidifierTankMsgFile", "number", title: "↳ Dehumidifier Tank Full Voice File #", required: true
                input "testBtn_tank", "button", title: "🔊 Test Dehumidifier Tank Sequence"
                paragraph "<hr style='border-top: 1px dashed #ccc;'>"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { 
    logAction("Installed")
    initialize() 
}

def updated() { 
    logAction("Updated")
    unsubscribe()
    initialize() 
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    // Clear out active restore states upon initialization
    atomicState.sonosToRestore = []
    atomicState.rokuToRestore = []
    
    // Ensure mood mute state is initialized
    if (state.isMoodMuted == null) state.isMoodMuted = false

    // Subscribe to Standard Alerts
    if (mailSwitch) subscribe(mailSwitch, "switch.on", mailHandler)
    if (outdoorMotion) subscribe(outdoorMotion, "motion.active", motionHandler)
    if (pushSensors) subscribe(pushSensors, "motion.active", pushMotionHandler)
    if (doorChimeSensors) subscribe(doorChimeSensors, "contact.open", doorChimeHandler)
    if (settings.doorbellButton) subscribe(settings.doorbellButton, "pushed", doorbellHandler)
    
    // Subscribe to Trash Alerts
    if (settings.trashEmptiedSwitch) subscribe(settings.trashEmptiedSwitch, "switch.on", trashEmptiedHandler)
    if (settings.trashReminderSwitch) subscribe(settings.trashReminderSwitch, "switch.on", trashReminderHandler)
    
    // Subscribe to associated contact sensors for motion suppression
    def allContacts = []
    if (outdoorMotion) {
        outdoorMotion.each { dev ->
            def contacts = settings["outContact_${dev.id}"]
            if (contacts) allContacts.addAll(contacts)
        }
    }
    if (allContacts) {
        allContacts.unique { it.id }.each { cDev -> subscribe(cDev, "contact.open", contactOpenHandler) }
    }
    
    // Subscribe to Weather Alerts
    def weatherKeys = [
        "sprinkling", "raining", "predictedRain", "tstormWatch", "tstormWarning", 
        "tornadoWatch", "tornadoWarning", "floodWatch", "floodWarning", 
        "heatWatch", "heatWarning", "overcastOn", "overcastOff"
    ]
    weatherKeys.each { key ->
        if (settings["${key}Switch"]) subscribe(settings["${key}Switch"], "switch.on", weatherHandler)
    }

    // Subscribe to Bus Alerts
    def busKeys = ["bus20", "bus10", "busArrived"]
    busKeys.each { key ->
        if (settings["${key}Switch"]) subscribe(settings["${key}Switch"], "switch.on", busHandler)
    }
    
    // Subscribe to Appliance Alerts
    def applianceKeys = ["washerDryer", "dishwasher"]
    applianceKeys.each { key ->
        if (settings["${key}Switch"]) subscribe(settings["${key}Switch"], "switch.on", applianceHandler)
    }

    // Subscribe to Garden Alerts
    def gardenKeys = ["smartWatering", "swapGardenZone", "wateringComplete", "needsWatering"]
    gardenKeys.each { key ->
        if (settings["${key}Switch"]) subscribe(settings["${key}Switch"], "switch.on", gardenHandler)
    }
    
    // Subscribe to Air Quality Alerts
    if (settings.indoorAirSwitch) subscribe(settings.indoorAirSwitch, "switch", airQualityHandler)
    if (settings.outdoorAirSwitch) subscribe(settings.outdoorAirSwitch, "switch", airQualityHandler)

    // Subscribe to Filter Reminders
    if (settings.airPurifierSwitch) subscribe(settings.airPurifierSwitch, "switch.on", filterHandler)
    if (settings.dehumidifierSwitch) subscribe(settings.dehumidifierSwitch, "switch.on", filterHandler)
    for (int i = 1; i <= 3; i++) {
        if (settings["hvacSwitch_${i}"]) subscribe(settings["hvacSwitch_${i}"], "switch.on", filterHandler)
    }

    // Subscribe to Dehumidifier Tanks
    if (settings.dehumidifierTankSwitch) subscribe(settings.dehumidifierTankSwitch, "switch.on", dehumidifierTankHandler)

    // Subscribe to Mode Changes
    subscribe(location, "mode", modeChangeHandler)
    
    // Subscribe to Room Presence Engine
    for (int i = 1; i <= 5; i++) {
        if (settings["enableRoom_${i}"] && settings["roomMotion_${i}"]) {
            subscribe(settings["roomMotion_${i}"], "motion.active", roomMotionHandler)
        }
    }
    
    // Subscribe to Mood Logic
    if (settings.moodVarU1) subscribe(location, "variable:${settings.moodVarU1}", "moodChangeHandler")
    if (settings.moodVarU2) subscribe(location, "variable:${settings.moodVarU2}", "moodChangeHandler")
    if (settings.moodVarU3) subscribe(location, "variable:${settings.moodVarU3}", "moodChangeHandler")
    
    if (masterEnableSwitch) subscribe(masterEnableSwitch, "switch", enableSwitchHandler)
    
    evaluateMoods()
    logAction("Advanced House Announcements Initialized.")
}

def isSystemEnabled() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def isCategoryMuted(String category) {
    def mutedByVisitor = false
    if (visitorSwitch && visitorSwitch.currentValue("switch") == "on") {
        if (visitorMuteOptions && (visitorMuteOptions as List).contains(category)) {
            mutedByVisitor = true
        }
    }
    
    def mutedByMood = false
    if (state.isMoodMuted) {
        if (moodMuteOptions && (moodMuteOptions as List).contains(category)) {
            mutedByMood = true
        }
    }
    
    if (mutedByVisitor) {
        logInfo("${category} announcement muted by Visitor Switch.")
        return true
    }
    if (mutedByMood) {
        logInfo("${category} announcement muted by Psychological Auto-Regulation (Mood Sync).")
        return true
    }
    
    return false
}

def moodChangeHandler(evt) {
    logAction("Mood logic triggered. Variable updated to ${evt.value}. Re-evaluating psychological auto-regulation.")
    evaluateMoods()
}

def evaluateMoods() {
    def targetNegativeMoods = ["🥵", "🤯", "🤬", "🤪", "🤢", "💩", "🤒", "🤕", "🤐"]
    def negCount = 0
    if (settings.moodVarU1 && targetNegativeMoods.contains(getGlobalVar(settings.moodVarU1)?.value?.toString())) negCount++
    if (settings.moodVarU2 && targetNegativeMoods.contains(getGlobalVar(settings.moodVarU2)?.value?.toString())) negCount++
    if (settings.moodVarU3 && targetNegativeMoods.contains(getGlobalVar(settings.moodVarU3)?.value?.toString())) negCount++
    
    def threshold = settings.moodMuteThreshold ? settings.moodMuteThreshold.toInteger() : 1
    if (negCount >= threshold) {
        if (!state.isMoodMuted) {
            state.isMoodMuted = true
            logAction("Mood Auto-Regulation: >=${threshold} negative moods detected. Muting selected announcement categories.")
        }
    } else {
        if (state.isMoodMuted) {
            state.isMoodMuted = false
            logAction("Mood Auto-Regulation: Moods improved. Restoring normal announcements.")
        }
    }
}

String getHumanReadableStatus() {
    def status = ""
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") {
        status = "<span style='color:red;'><b>System Disabled:</b></span> The application is turned off via the Master Switch."
    } else if (visitorSwitch && visitorSwitch.currentValue("switch") == "on") {
        status = "<span style='color:#fd7e14;'><b>Active (Visitor Mode ON):</b></span> System is monitoring, but selected announcement categories are actively muted."
    } else if (state.isMoodMuted) {
        status = "<span style='color:blue;'><b>Active (Mood Sync Active):</b></span> System is monitoring, but selected announcements are silenced due to negative user moods."
    } else {
        status = "<span style='color:green;'><b>Active & Monitoring:</b></span> System is ready to route announcements."
    }
    return status
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
    } else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logInfo("Action logging history cleared.")
    } else if (btn == "testBtn_mail") {
        def msgFile = settings.mailMsgFile
        def chimeFile = settings.globalChimeFile
        def delayMs = settings.globalDelay ?: 1500
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, chimeFile, msgFile, delayMs)
    } else if (btn.startsWith("testBtn_weather_")) {
        def key = btn.replace("testBtn_weather_", "")
        def msgFile = settings["${key}MsgFile"]
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn.startsWith("testBtn_bus_")) {
        def key = btn.replace("testBtn_bus_", "")
        def msgFile = settings["${key}MsgFile"]
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn.startsWith("testBtn_appliance_")) {
        def key = btn.replace("testBtn_appliance_", "")
        def msgFile = settings["${key}MsgFile"]
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn.startsWith("testBtn_garden_")) {
        def key = btn.replace("testBtn_garden_", "")
        def msgFile = settings["${key}MsgFile"]
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn.startsWith("testBtn_air_")) {
        def key = btn.replace("testBtn_air_", "")
        def msgFile = null
        if (key == "indoor") msgFile = settings.indoorAirMsgFile
        else if (key == "outdoor") msgFile = settings.outdoorAirMsgFile
        else if (key == "normal") msgFile = settings.airNormalMsgFile
        
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn.startsWith("testBtn_mode_")) {
        def safeMd = btn.replace("testBtn_mode_", "")
        def msgFile = settings["modeMsgFile_${safeMd}"]
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn.startsWith("testBtn_outMotion_")) {
        def devId = btn.split("_")[2]
        def msgFile = settings["outMsgFile_${devId}"]
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn.startsWith("testBtn_filter_")) {
        def msgFile = null
        if (btn == "testBtn_filter_airPurifier") {
            msgFile = settings.airPurifierMsgFile
        } else if (btn == "testBtn_filter_dehumidifier") {
            msgFile = settings.dehumidifierMsgFile
        } else if (btn.startsWith("testBtn_filter_hvac_")) {
            def parts = btn.replace("testBtn_filter_hvac_", "").tokenize("_")
            def fIndex = parts[0]
            msgFile = settings["hvacMsgFile_${fIndex}"]
        }
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn == "testBtn_tank") {
        def msgFile = settings.dehumidifierTankMsgFile
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && msgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
    } else if (btn == "testBtn_doorbell") {
        def msgFiles = []
        for (int i = 1; i <= 5; i++) {
            if (settings["doorbellMsgFile_${i}"] != null) msgFiles << settings["doorbellMsgFile_${i}"]
        }
        if (msgFiles) {
            def randomMsg = msgFiles[new java.util.Random().nextInt(msgFiles.size())]
            def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
            logAction("TEST BUTTON: Firing random Doorbell audio [File ${randomMsg}].")
            if (targetDevs.zooz) playSequentialAudio(targetDevs, settings.globalChimeFile, randomMsg, settings.globalDelay ?: 1500)
        } else {
            log.error "TEST FAILED: No Doorbell message files configured."
        }
    } else if (btn == "testBtn_doorChime") {
        if (settings.doorChimeFile != null && settings.doorChimeDevices) playSequentialAudio([zooz: settings.doorChimeDevices], null, settings.doorChimeFile, 0)
    } else if (btn == "testBtn_trash_emptied") {
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && settings.trashEmptiedMsgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, settings.trashEmptiedMsgFile, settings.globalDelay ?: 1500)
    } else if (btn == "testBtn_trash_reminder") {
        def targetDevs = [zooz: getAllZooz(), sonos: getAllSonos(), roku: getAllRokus()]
        if (targetDevs.zooz && settings.trashReminderMsgFile != null) playSequentialAudio(targetDevs, settings.globalChimeFile, settings.trashReminderMsgFile, settings.globalDelay ?: 1500)
    }
}

def enableSwitchHandler(evt) { 
    if (evt.value == "off") logAction("App Disabled via Master Switch."); 
    else logAction("App Enabled via Master Switch.") 
}

// --- EVENT RECORDERS ---

def contactOpenHandler(evt) {
    if (!isSystemEnabled()) return
    atomicState."lastOpened_${evt.device.id}" = now()
    logInfo("Door/Window opened: ${evt.device.displayName}. Associated motion alerts suppressed for 5 minutes.")
}

// --- ROOM PRESENCE ENGINE ---

def roomMotionHandler(evt) {
    if (!isSystemEnabled()) return
    
    for (int i = 1; i <= 5; i++) {
        if (settings["enableRoom_${i}"] && settings["roomMotion_${i}"]?.find { it.id == evt.device.id }) {
            state."roomLastActive_${i}" = now()
            logInfo("Presence Engine: ${settings["roomName_${i}"]} is now ACTIVE.")
        }
    }
}

def getTargetDevices(String eventGroup = "standard", String subKey = null) {
    def zoozTargets = []
    def sonosTargets = []
    def rokuTargets = []
    def currentMode = location.mode
    def isNightMode = currentMode?.toLowerCase()?.contains("night")
    def timeoutMs = (settings.occupancyTimeout != null ? settings.occupancyTimeout : 2) * 60000

    // Check standard configured rooms
    for (int i = 1; i <= 5; i++) {
        if (settings["enableRoom_${i}"]) {
            def lastActive = state."roomLastActive_${i}" ?: 0
            def isSleep = settings["roomGNSwitch_${i}"] && settings["roomGNSwitch_${i}"].currentValue("switch") == "on"
            def isForced = settings["roomForceSwitch_${i}"] && settings["roomForceSwitch_${i}"].currentValue("switch") == "on"
            
            // Failsafe: Check if the sensor is currently stuck 'active'
            if (settings["roomMotion_${i}"]?.any { it.currentValue("motion") == "active" }) {
                lastActive = now()
                state."roomLastActive_${i}" = now()
            }

            def isRoomEligible = false
            
            if (isForced) {
                isRoomEligible = true
            } else if (isSleep) {
                if (eventGroup == "weather" && subKey && settings["${subKey}BypassRooms"]?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "motion" && settings.motionBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "mode" && settings.modeBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "bus" && settings.busBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "appliance" && settings.applianceBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "trash" && settings.trashBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "garden" && settings.gardenBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "airQuality" && settings.airQualityBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "doorbell" && settings.doorbellBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "filter" && settings.filterBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                } else if (eventGroup == "dehumidifierTank" && settings.dehumidifierTankBypassRooms?.contains(i.toString())) {
                    isRoomEligible = true
                }
            } else if (!isNightMode && (now() - lastActive) <= timeoutMs) {
                isRoomEligible = true
            }

            if (isRoomEligible) {
                def modes = settings["roomModes_${i}"]
                if (!modes || (modes as List).contains(currentMode)) {
                    if (settings["roomZooz_${i}"]) zoozTargets.addAll(settings["roomZooz_${i}"])
                    if (settings["roomSonos_${i}"]) sonosTargets.addAll(settings["roomSonos_${i}"])
                    if (settings["roomRoku_${i}"]) rokuTargets.addAll(settings["roomRoku_${i}"])
                }
            }
        }
    }
    
    // ALWAYS play on Default Speaker (respecting mode restrictions)
    def defModes = settings.defaultModes
    if (!defModes || (defModes as List).contains(currentMode)) {
        if (settings.defaultZooz) zoozTargets.addAll(settings.defaultZooz)
        if (settings.defaultSonos) sonosTargets.addAll(settings.defaultSonos)
        if (settings.defaultRoku) rokuTargets.addAll(settings.defaultRoku)
    }

    return [zooz: zoozTargets.unique { it.id }, sonos: sonosTargets.unique { it.id }, roku: rokuTargets.unique { it.id }]
}

def getAllZooz() {
    def all = []
    if (settings.defaultZooz) all.addAll(settings.defaultZooz)
    for (int i = 1; i <= 5; i++) {
        if (settings["enableRoom_${i}"] && settings["roomZooz_${i}"]) all.addAll(settings["roomZooz_${i}"])
    }
    if (settings.doorChimeDevices) all.addAll(settings.doorChimeDevices)
    return all.unique { it.id }
}

def getAllSonos() {
    def all = []
    if (settings.defaultSonos) all.addAll(settings.defaultSonos)
    for (int i = 1; i <= 5; i++) {
        if (settings["enableRoom_${i}"] && settings["roomSonos_${i}"]) all.addAll(settings["roomSonos_${i}"])
    }
    return all.unique { it.id }
}

def getAllRokus() {
    def all = []
    if (settings.defaultRoku) all.addAll(settings.defaultRoku)
    for (int i = 1; i <= 5; i++) {
        if (settings["enableRoom_${i}"] && settings["roomRoku_${i}"]) all.addAll(settings["roomRoku_${i}"])
    }
    return all.unique { it.id }
}

// --- EVENT HANDLERS ---

def doorbellHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Doorbell")) return
    
    def targetBtnNum = settings.doorbellButtonNumber ?: 1
    if (evt.value.toString() != targetBtnNum.toString()) return
    
    if (settings.doorbellModes && !(settings.doorbellModes as List).contains(location.mode)) {
        logInfo("Doorbell alert suppressed. House is not in an allowed mode.")
        return
    }
    
    def msgFiles = []
    for (int i = 1; i <= 5; i++) {
        if (settings["doorbellMsgFile_${i}"] != null) msgFiles << settings["doorbellMsgFile_${i}"]
    }
    
    if (!msgFiles) {
        log.warn "Doorbell pressed, but no message files are configured."
        return
    }
    
    def randomMsg = msgFiles[new java.util.Random().nextInt(msgFiles.size())]
    logAction("Doorbell Pressed. Selected random file [${randomMsg}]. Determining target speakers...")
    
    def targetDevs = getTargetDevices("doorbell")
    if (targetDevs.zooz) {
        playSequentialAudio(targetDevs, settings.globalChimeFile, randomMsg, settings.globalDelay ?: 1500)
    } else {
        logInfo("No valid target speakers found for Doorbell Alert (Check your active mode restrictions).")
    }
}

def airQualityHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Air Quality")) return

    def alertModes = settings.airQualityModes
    if (alertModes && !(alertModes as List).contains(location.mode)) {
        logInfo("Air Quality alert suppressed. House is not in an allowed mode.")
        return
    }

    def devId = evt.device.id
    def inState = settings.indoorAirSwitch ? settings.indoorAirSwitch.currentValue("switch") == "on" : false
    def outState = settings.outdoorAirSwitch ? settings.outdoorAirSwitch.currentValue("switch") == "on" : false

    def msgFile = null
    def alertTitle = ""

    if (evt.value == "on") {
        state.airQualityPoor = true
        if (settings.indoorAirSwitch?.id == devId) {
            msgFile = settings.indoorAirMsgFile
            alertTitle = "Indoor Air Quality Poor"
        } else if (settings.outdoorAirSwitch?.id == devId) {
            msgFile = settings.outdoorAirMsgFile
            alertTitle = "Outdoor Air Quality Poor"
        }
    } else if (evt.value == "off") {
        if (!inState && !outState && state.airQualityPoor) {
            state.airQualityPoor = false
            msgFile = settings.airNormalMsgFile
            alertTitle = "Air Quality Returned to Normal"
        } else if (!inState && !outState) {
            state.airQualityPoor = false
        }
    }

    if (msgFile != null && alertTitle != "") {
        logAction("Air Quality Event Triggered: ${alertTitle}. Determining target speakers...")
        def targetDevs = getTargetDevices("airQuality")
        if (targetDevs.zooz) {
            def delayMs = settings.globalDelay ?: 1500
            playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, delayMs)
        } else {
            logInfo("No valid target speakers found for Air Quality Alert (Check your active mode restrictions).")
        }
    } else if (evt.value == "off" && (inState || outState)) {
         logInfo("Air quality improved for ${evt.device.displayName}, but other sensor is still poor. 'Normal' announcement pending.")
    }
}

def filterHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Filter Reminders")) return

    def alertModes = settings.filterModes
    if (alertModes && !(alertModes as List).contains(location.mode)) return

    def devId = evt.device.id
    def matchedMsgFile = null
    def matchedTitle = ""

    if (settings.airPurifierSwitch?.id == devId) {
        matchedMsgFile = settings.airPurifierMsgFile
        matchedTitle = "Air Purifier Filter Replacement"
    } else if (settings.dehumidifierSwitch?.id == devId) {
        matchedMsgFile = settings.dehumidifierMsgFile
        matchedTitle = "Dehumidifier Filter Replacement"
    } else {
        for (int i = 1; i <= 3; i++) {
            if (settings["hvacSwitch_${i}"]?.id == devId) {
                matchedMsgFile = settings["hvacMsgFile_${i}"]
                matchedTitle = "HVAC Filter ${i} Replacement"
                break
            }
        }
    }

    if (matchedMsgFile != null) {
        logAction("Filter Event Triggered: ${matchedTitle}. Determining target speakers...")
        def targetDevs = getTargetDevices("filter")
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, matchedMsgFile, settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Filter Reminder (Check your active mode restrictions).")
        }
    }
}

def dehumidifierTankHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Dehumidifier Tanks")) return

    def alertModes = settings.dehumidifierTankModes
    if (alertModes && !(alertModes as List).contains(location.mode)) return

    def devId = evt.device.id
    def matchedMsgFile = null
    def matchedTitle = ""

    if (settings.dehumidifierTankSwitch?.id == devId) {
        matchedMsgFile = settings.dehumidifierTankMsgFile
        matchedTitle = "Dehumidifier Tank Full"
    }

    if (matchedMsgFile != null) {
        logAction("Tank Event Triggered: ${matchedTitle}. Determining target speakers...")
        def targetDevs = getTargetDevices("dehumidifierTank")
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, matchedMsgFile, settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Dehumidifier Tank Alert (Check your active mode restrictions).")
        }
    }
}

def gardenHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Garden")) return
    
    def devId = evt.device.id
    def matchedKey = null
    def matchedTitle = ""
    
    if (settings.smartWateringSwitch?.id == devId) { matchedKey = "smartWatering"; matchedTitle = "Smart Watering Enabled" }
    else if (settings.swapGardenZoneSwitch?.id == devId) { matchedKey = "swapGardenZone"; matchedTitle = "Swap Garden Zone" }
    else if (settings.wateringCompleteSwitch?.id == devId) { matchedKey = "wateringComplete"; matchedTitle = "Watering Complete" }
    else if (settings.needsWateringSwitch?.id == devId) { matchedKey = "needsWatering"; matchedTitle = "Garden Needs Watering" }
    
    if (!matchedKey) return
    
    def alertModes = settings["${matchedKey}Modes"]
    if (alertModes && !(alertModes as List).contains(location.mode)) return
    
    logAction("Garden Event Triggered: ${matchedTitle}. Determining target speakers...")
    def msgFile = settings["${matchedKey}MsgFile"]
    
    if (msgFile != null) {
        def targetDevs = getTargetDevices("garden", matchedKey)
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, msgFile, settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Garden Alert (Check your active mode restrictions).")
        }
    }
}

def trashEmptiedHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Trash")) return
    if (settings.trashEmptiedModes && !(settings.trashEmptiedModes as List).contains(location.mode)) return
    
    logAction("Trash Emptied Event Triggered. Determining target speakers...")
    if (settings.trashEmptiedMsgFile != null) {
        def targetDevs = getTargetDevices("trash")
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, settings.trashEmptiedMsgFile, settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Trash Emptied Alert (Check your active mode restrictions).")
        }
    }
}

def trashReminderHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Trash")) return
    if (settings.trashReminderModes && !(settings.trashReminderModes as List).contains(location.mode)) return
    
    logAction("Trash Reminder Event Triggered. Determining target speakers...")
    if (settings.trashReminderMsgFile != null) {
        def targetDevs = getTargetDevices("trash")
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, settings.trashReminderMsgFile, settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Trash Reminder Alert (Check your active mode restrictions).")
        }
    }
}

def applianceHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Appliance")) return
    
    def devId = evt.device.id
    def matchedKey = null
    def matchedTitle = ""
    
    if (settings.washerDryerSwitch?.id == devId) { matchedKey = "washerDryer"; matchedTitle = "Washer/Dryer Complete" }
    else if (settings.dishwasherSwitch?.id == devId) { matchedKey = "dishwasher"; matchedTitle = "Dishwasher Complete" }
    
    if (!matchedKey) return
    
    if (settings["${matchedKey}Modes"] && !(settings["${matchedKey}Modes"] as List).contains(location.mode)) return
    
    logAction("Appliance Event Triggered: ${matchedTitle}. Determining target speakers...")
    if (settings["${matchedKey}MsgFile"] != null) {
        def targetDevs = getTargetDevices("appliance", matchedKey)
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, settings["${matchedKey}MsgFile"], settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Appliance Alert (Check your active mode restrictions).")
        }
    }
}

def doorChimeHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Door Chimes")) return
    
    def devId = evt.device.id
    def cooldownMs = (settings.doorChimeDebounce != null ? settings.doorChimeDebounce : 15) * 1000
    
    def lastChime = atomicState."lastDoorChime_${devId}" ?: 0
    if ((now() - (lastChime as long)) < cooldownMs) return 
    if (settings.doorChimeModes && !(settings.doorChimeModes as List).contains(location.mode)) return
    
    atomicState."lastDoorChime_${devId}" = now()
    logAction("Door Opened: ${evt.device.displayName}. Sending chime to dedicated devices...")
    
    if (settings.doorChimeFile != null && settings.doorChimeDevices) {
        playSequentialAudio([zooz: settings.doorChimeDevices], null, settings.doorChimeFile, 0)
    }
}

def modeChangeHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Mode Changes")) return
    
    def safeMd = evt.value.replace(" ", "")
    if (settings.modeAnnounceSelection?.contains(evt.value) && settings["modeMsgFile_${safeMd}"] != null) {
        logAction("Mode changed to ${evt.value}. Determining target speakers...")
        def targetDevs = getTargetDevices("mode")
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, settings["modeMsgFile_${safeMd}"], settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Mode Change (Check your active mode restrictions).")
        }
    }
}

def busHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Bus")) return
    
    def devId = evt.device.id
    def matchedKey = null
    def matchedTitle = ""
    
    if (settings.bus20Switch?.id == devId) { matchedKey = "bus20"; matchedTitle = "Bus 20 Minutes Away" }
    else if (settings.bus10Switch?.id == devId) { matchedKey = "bus10"; matchedTitle = "Bus 10 Minutes Away" }
    else if (settings.busArrivedSwitch?.id == devId) { matchedKey = "busArrived"; matchedTitle = "Bus Arrived" }
    
    if (!matchedKey) return
    if (settings["${matchedKey}Modes"] && !(settings["${matchedKey}Modes"] as List).contains(location.mode)) return
    
    logAction("Bus Event Triggered: ${matchedTitle}. Determining target speakers...")
    if (settings["${matchedKey}MsgFile"] != null) {
        def targetDevs = getTargetDevices("bus", matchedKey)
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, settings["${matchedKey}MsgFile"], settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Bus Event (Check your active mode restrictions).")
        }
    }
}

def weatherHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Weather")) return
    
    def devId = evt.device.id
    def matchedKey = null
    def matchedTitle = ""
    
    if (settings.sprinklingSwitch?.id == devId) { matchedKey = "sprinkling"; matchedTitle = "Sprinkling" }
    else if (settings.rainingSwitch?.id == devId) { matchedKey = "raining"; matchedTitle = "Raining" }
    else if (settings.predictedRainSwitch?.id == devId) { matchedKey = "predictedRain"; matchedTitle = "Predicted Rain" }
    else if (settings.tstormWatchSwitch?.id == devId) { matchedKey = "tstormWatch"; matchedTitle = "Thunderstorm Watch" }
    else if (settings.tstormWarningSwitch?.id == devId) { matchedKey = "tstormWarning"; matchedTitle = "Thunderstorm Warning" }
    else if (settings.tornadoWatchSwitch?.id == devId) { matchedKey = "tornadoWatch"; matchedTitle = "Tornado Watch" }
    else if (settings.tornadoWarningSwitch?.id == devId) { matchedKey = "tornadoWarning"; matchedTitle = "Tornado Warning" }
    else if (settings.floodWatchSwitch?.id == devId) { matchedKey = "floodWatch"; matchedTitle = "Flood Watch" }
    else if (settings.floodWarningSwitch?.id == devId) { matchedKey = "floodWarning"; matchedTitle = "Flood Warning" }
    else if (settings.heatWatchSwitch?.id == devId) { matchedKey = "heatWatch"; matchedTitle = "Heat Watch" }
    else if (settings.heatWarningSwitch?.id == devId) { matchedKey = "heatWarning"; matchedTitle = "Heat Warning" }
    else if (settings.overcastOnSwitch?.id == devId) { matchedKey = "overcastOn"; matchedTitle = "Overcast On" }
    else if (settings.overcastOffSwitch?.id == devId) { matchedKey = "overcastOff"; matchedTitle = "Overcast Off" }
    
    if (!matchedKey) return
    if (settings["${matchedKey}Modes"] && !(settings["${matchedKey}Modes"] as List).contains(location.mode)) return
    
    logAction("Weather Event Triggered: ${matchedTitle}. Determining target speakers...")
    if (settings["${matchedKey}MsgFile"] != null) {
        def targetDevs = getTargetDevices("weather", matchedKey)
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, settings["${matchedKey}MsgFile"], settings.globalDelay ?: 1500)
        } else {
            logInfo("No valid target speakers found for Weather Event (Check your active mode restrictions).")
        }
    }
}

def mailHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Mailbox")) return
    if (settings.mailModes && !(settings.mailModes as List).contains(location.mode)) return
    
    logAction("Mailbox Opened. Determining target speakers...")
    if (settings.mailMsgFile != null) {
        def targetDevs = getTargetDevices("mail")
        if (targetDevs.zooz) {
            playSequentialAudio(targetDevs, settings.globalChimeFile, settings.mailMsgFile, settings.globalDelay ?: 1500)
        } else {
             logInfo("No valid target speakers found for Mail Event (Check your active mode restrictions).")
        }
    }
}

def motionHandler(evt) {
    if (!isSystemEnabled()) return
    if (isCategoryMuted("Outdoor Motion")) return
    
    def devId = evt.device.id
    def associatedContacts = settings["outContact_${devId}"]
    
    if (associatedContacts) {
        def isDoorActive = false
        for (c in associatedContacts) {
            def lastOpen = atomicState."lastOpened_${c.id}" ?: 0
            if (c.currentValue("contact") == "open" || (now() - (lastOpen as long)) <= 300000) {
                isDoorActive = true
                break
            }
        }
        if (isDoorActive) return
    }

    def weatherSwitches = settings["muteWeatherSwitches_${devId}"]
    if (weatherSwitches?.find { it.currentValue("switch") == "on" }) return
    if (settings["outModes_${devId}"] && !(settings["outModes_${devId}"] as List).contains(location.mode)) return
    
    def cooldownMs = (settings.motionDebounce != null ? settings.motionDebounce : 5) * 60000
    
    def lastAlert = atomicState."lastAlert_${devId}" ?: 0
    if ((now() - (lastAlert as long)) < cooldownMs) return 
    
    if (settings["outMsgFile_${devId}"] != null) {
        logAction("Motion active at ${evt.device.displayName}. Determining target speakers...")
        def targetDevs = getTargetDevices("motion")
        if (targetDevs.zooz) {
            atomicState."lastAlert_${devId}" = now()
            playSequentialAudio(targetDevs, settings.globalChimeFile, settings["outMsgFile_${devId}"], settings.globalDelay ?: 1500)
        } else {
             logInfo("No valid target speakers found for Motion Event (Check your active mode restrictions).")
        }
    }
}

def pushMotionHandler(evt) {
    if (!isSystemEnabled()) return
    // Push notifications intentionally ignore Visitor Mute, only stopping audio
    if (settings.pushModes && !(settings.pushModes as List).contains(location.mode)) return
    
    def devId = evt.device.id
    def cooldownMs = (settings.motionDebounce != null ? settings.motionDebounce : 5) * 60000
    
    def lastPush = atomicState."lastPush_${devId}" ?: 0
    if ((now() - (lastPush as long)) < cooldownMs) return 
    
    atomicState."lastPush_${devId}" = now()
    
    def msg = "Alert: Motion detected by ${evt.device.displayName}."
    logAction("Push Notification Event: ${msg}")
    
    if (settings.pushDevices) {
        settings.pushDevices.each { it.deviceNotification(msg) }
    }
}

// --- AUDIO ROUTING ENGINE ---

def playSequentialAudio(devicesMap, chimeNum, msgNum, delayMs) {
    if (!devicesMap) return

    def zoozDevs = devicesMap.zooz ?: []
    def sonosDevs = devicesMap.sonos ?: []
    def rokuDevs = devicesMap.roku ?: []

    // Retrieve active tracking lists to ensure back-to-back announcements don't overwrite pause state
    List currentSonosRestore = atomicState.sonosToRestore ? (atomicState.sonosToRestore as List) : []
    List currentRokuRestore = atomicState.rokuToRestore ? (atomicState.rokuToRestore as List) : []

    // 1. Mute associated Roku TVs
    rokuDevs.each { rDev ->
        try {
            rDev.mute()
            if (!currentRokuRestore.contains(rDev.id.toString())) {
                currentRokuRestore << rDev.id.toString()
            }
        } catch (e) { log.error "Failed to mute Roku TV ${rDev.displayName}: ${e}" }
    }

    // 2. Mute associated Sonos speakers (or retain if already muted by an active announcement chain)
    sonosDevs.each { sDev ->
        try {
            def status = (sDev.currentValue("status") ?: sDev.currentValue("transportStatus"))?.toString()?.toLowerCase()
            def isPlaying = (status == "playing" || status == "transitioning")
            def isMuted = (sDev.currentValue("mute")?.toString()?.toLowerCase() == "muted")
            def alreadyTracking = currentSonosRestore.contains(sDev.id.toString())

            // SMART FIX: Mute instead of pause to keep live radio and hub file streams connected
            if ((isPlaying && !isMuted) || alreadyTracking) {
                if (!alreadyTracking) {
                    sDev.mute()
                    currentSonosRestore << sDev.id.toString()
                }
            }
        } catch (e) { log.error "Failed to mute Sonos ${sDev.displayName}: ${e}" }
    }

    atomicState.sonosToRestore = currentSonosRestore.unique()
    atomicState.rokuToRestore = currentRokuRestore.unique()

    // 3. Play Announcements on Zooz Devices with Z-Wave mesh pacing
    def validZooz = [zoozDevs].flatten().findAll{it}
    if (validZooz) {
        if (settings.enableGlobalChime != false && chimeNum != null) {
            validZooz.each { zDev -> 
                playZoozSound(zDev, chimeNum)
                pauseExecution(400) // Increased buffer: gives the mesh time to beam/wake FLiRS devices
            }
            
            // Add a 500ms internal pad to the user's delay to account for Z-Wave radio wake-up time.
            // This prevents the announcement from overwriting the chime if the device was asleep.
            pauseExecution((delayMs as Long) + 500) 
        }
        
        if (msgNum != null) {
            validZooz.each { zDev -> 
                playZoozSound(zDev, msgNum)
                pauseExecution(400) // Increased buffer to prevent Z-Wave collisions
            }
        }
    }

    // 4. Schedule/extend automatic media restoration
    if (atomicState.sonosToRestore || atomicState.rokuToRestore) {
        def totalWaitSeconds = Math.max(5, ((delayMs / 1000).toInteger() + 8))
        runIn(totalWaitSeconds, "restoreMedia", [overwrite: true])
    }
}

def restoreMedia() {
    List sonosIds = atomicState.sonosToRestore ? (atomicState.sonosToRestore as List) : []
    List rokuIds = atomicState.rokuToRestore ? (atomicState.rokuToRestore as List) : []

    if (sonosIds) {
        def allSonos = getAllSonos()
        allSonos.findAll { it.id.toString() in sonosIds }.each { sDev ->
            try { 
                sDev.unmute() 
                logInfo("Unmuted Sonos on ${sDev.displayName}")
            } catch (e) { log.error "Failed to unmute Sonos ${sDev.displayName}: ${e}" }
        }
        atomicState.sonosToRestore = []
    }

    if (rokuIds) {
        def allRokus = getAllRokus()
        allRokus.findAll { it.id.toString() in rokuIds }.each { rDev ->
            try { 
                rDev.unmute() 
                logInfo("Unmuted Roku TV on ${rDev.displayName}")
            } catch (e) { log.error "Failed to unmute Roku TV ${rDev.displayName}: ${e}" }
        }
        atomicState.rokuToRestore = []
    }
}

def playZoozSound(zDev, soundNum) {
    if (!zDev || soundNum == null) return false
    
    def played = false
    def isNumeric = soundNum.toString().isNumber()
    def trackNum = isNumeric ? soundNum.toString().toInteger() : null

    try {
        def cmds = zDev.supportedCommands?.collect { it.name } ?: []
        if (cmds.contains("playSound") && trackNum != null) { zDev.playSound(trackNum); played = true } 
        else if (cmds.contains("playTrack")) { zDev.playTrack(soundNum.toString()); played = true } 
        else if (cmds.contains("chime") && trackNum != null) { zDev.chime(trackNum); played = true } 
        else {
            try {
                if (trackNum != null) { zDev.playSound(trackNum); played = true }
            } catch (e1) {
                try {
                    if (trackNum != null) { zDev.chime(trackNum); played = true }
                } catch (e2) {
                    log.error "ZOOZ ENGINE ERROR: ${zDev.displayName} does not support standard audio commands."
                }
            }
        }
    } catch (e) { log.error "Failed to play sound on Zooz device ${zDev.displayName}: ${e}" }
    return played
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
