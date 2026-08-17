/**
 * Advanced Voice Butler 2.0
 */ 

definition(
    name: "Advanced Voice Butler 2.0",
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

String getHumanReadableStatus() {
    def dndActive = (settings.switchDND && settings.switchDND.currentValue("switch") == "on")
    def modeMuted = (settings.quietModes && settings.quietModes.contains(location.mode))
    def visitorActive = (settings.visitorSwitch && settings.visitorSwitch.currentValue("switch") == "on")
    def moodActive = (state.isMoodMuted)
    
    if (modeMuted) {
        return "<span style='color:orange; font-size:14px;'><b>🌙 QUIET MODE: Standard announcements & Doorbell completely suspended (Intruder alerts will still override).</b></span>"
    }
    if (dndActive) {
        return "<span style='color:#e83e8c; font-size:14px;'><b>🍽️ DO NOT DISTURB ACTIVE: Doorbell automatically routing to 'No Answer'. Standard announcements active.</b></span>"
    }
    if (visitorActive) {
        return "<span style='color:#fd7e14; font-size:14px;'><b>🛑 VISITOR MODE ACTIVE: System is monitoring, but selected announcement categories are actively muted.</b></span>"
    }
    if (moodActive) {
        return "<span style='color:blue; font-size:14px;'><b>🧠 MOOD SYNC ACTIVE: Selected announcements are silenced due to negative user moods.</b></span>"
    }
    
    def vol = state.currentCalculatedVolume ?: settings.defaultVolume ?: 50
    return "<span style='color:green; font-size:14px;'><b>🔊 SYSTEM ACTIVE: Monitoring perimeters, rooms, and arrivals. Routing local audio at ${vol}% volume.</b></span>"
}

def isCategoryMuted(String category) {
    def mutedByVisitor = false
    if (settings.visitorSwitch && settings.visitorSwitch.currentValue("switch") == "on") {
        if (settings.visitorMuteOptions && (settings.visitorMuteOptions as List).contains(category)) {
            mutedByVisitor = true
        }
    }
    
    def mutedByMood = false
    if (state.isMoodMuted) {
        if (settings.moodMuteOptions && (settings.moodMuteOptions as List).contains(category)) {
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

def moodChangeHandler(evt) {
    logAction("Mood logic triggered. Variable updated to ${evt.value}. Re-evaluating psychological auto-regulation.")
    evaluateMoods()
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Data"
            
            evaluateMoods()
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            if (doorbellAudioDevices || intruderAudioDevices || roomAudio_1 || roomAudio_2 || roomAudio_3 || arrivalAudioDevices || commonAudioDevices) {
                def vol = state.currentCalculatedVolume ?: settings.defaultVolume ?: 50
                def lastMsg = state.lastMessage ?: "None"
                def lastMsgTime = state.lastMessageTime ? new Date(state.lastMessageTime as Long).format("MM/dd hh:mm a", location.timeZone) : "N/A"
                def lastTrigger = state.lastTriggerSource ?: "N/A"
                
                def dndStatus = (settings.switchDND && settings.switchDND.currentValue("switch") == "on") ? "<span style='color:#e83e8c; font-weight:bold;'>ACTIVE (Doorbell No-Answer)</span>" : "<span style='color:green;'>Inactive (Ready)</span>"
                def visStatus = (settings.visitorSwitch && settings.visitorSwitch.currentValue("switch") == "on") ? "<span style='color:#fd7e14; font-weight:bold;'>ACTIVE (Selective Muting)</span>" : "<span style='color:green;'>Inactive</span>"
                
                def isAway = settings.awayModes?.contains(location.mode)
                def occupancyStatus = isAway ? "<span style='color:#dc3545; font-weight:bold;'>Away (No-Answer Mode Active)</span>" : "<span style='color:#28a745; font-weight:bold;'>Home (Greetings Active)</span>"
                
                def roomAudioTotal = (roomAudio_1?.size() ?: 0) + (roomAudio_2?.size() ?: 0) + (roomAudio_3?.size() ?: 0)
                def arrivalTotal = arrivalAudioDevices?.size() ?: 0
                def commonTotal = commonAudioDevices?.size() ?: 0
                def totalSpeakers = (doorbellAudioDevices?.size() ?: 0) + (intruderAudioDevices?.size() ?: 0) + roomAudioTotal + arrivalTotal + commonTotal

                // Calculate Resident Tracking Rows
                def residentHTML = ""
                def hasUsers = false
                for (int u = 1; u <= 3; u++) {
                    def uName = settings["user${u}_Name"]
                    if (uName) {
                        hasUsers = true
                        def isHome = false
                        def statusText = "Unknown"
                        
                        def savedState = state["userPresence_${u}"]
                        def source = state["userPresenceSource_${u}"] ?: "Unknown"
                        
                        if (savedState == "Home") {
                            isHome = true
                            statusText = "Home (${source})"
                        } else if (savedState == "Away") {
                            isHome = false
                            statusText = "Away (${source})"
                        } else {
                            isHome = !(settings.awayModes?.contains(location.mode))
                            statusText = isHome ? "Assumed Home" : "Away (Global Mode)"
                        }
                        
                        def statusColor = isHome ? "#28a745" : "#6c757d"
                        residentHTML += "<tr><td class='dash-hl'>👤 ${uName}</td><td colspan='2' class='dash-val'><span style='color:${statusColor}; font-weight:bold;'>${statusText}</span></td></tr>"
                    }
                }
                if (hasUsers) {
                    residentHTML = "<tr><td colspan='3' class='dash-subhead'>Resident Occupancy Status</td></tr>" + residentHTML
                }

                // --- MOOD SYNC DASHBOARD DISPLAY ---
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
                    <thead><tr><th>Metric</th><th>Current Value</th><th>System Impact</th></tr></thead>
                    <tbody>
                        <tr><td colspan="3" class="dash-subhead">Local Audio Engine Status</td></tr>
                        <tr><td class="dash-hl">Current Hub Mode</td><td colspan="2" class="dash-val"><span style='color:#004085; font-weight:bold;'>${location.mode}</span></td></tr>
                        <tr><td class="dash-hl">Global Occupancy</td><td colspan="2" class="dash-val">${occupancyStatus}</td></tr>
                        <tr><td class="dash-hl">Do Not Disturb (DND)</td><td colspan="2" class="dash-val">${dndStatus}</td></tr>
                        <tr><td class="dash-hl">Visitor Mode</td><td colspan="2" class="dash-val">${visStatus}</td></tr>
                        <tr><td class="dash-hl">Psychological Auto-Reg</td><td colspan="2" class="dash-val">${moodIcons}<br><span style='font-size:12px; color:#555;'>Result: ${moodStatus}</span></td></tr>
                        <tr><td class="dash-hl">Active Output Volume</td><td colspan="2" class="dash-val"><span style='color:blue; font-weight:bold;'>${vol}%</span></td></tr>
                        <tr><td class="dash-hl">Active Output Devices</td><td colspan="2" class="dash-val"><b>${totalSpeakers}</b> Audio Devices Configured</td></tr>

                        ${residentHTML}

                        <tr><td colspan="3" class="dash-subhead">Announcement History</td></tr>
                        <tr><td class="dash-hl">Last Played Announcement</td><td colspan="2" class="dash-val"><i>"${lastMsg}"</i></td></tr>
                        <tr><td class="dash-hl">Last Trigger Source</td><td colspan="2" class="dash-val">${lastTrigger}</td></tr>
                        <tr><td class="dash-hl">Last Announcement Time</td><td colspan="2" class="dash-val">${lastMsgTime}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML

                def logicPanel = "<div style='margin-top: 20px; padding: 15px; background: #e6f2ff; border-left: 5px solid #007bff; font-size: 13px; color: #004085;'>"
                logicPanel += "<h4 style='margin-top:0; border-bottom:1px solid #b8daff; padding-bottom:5px;'>Engine Diagnostics: Audio Routing Matrix</h4>"
                logicPanel += "<div style='max-height: 400px; overflow-y: auto; border: 1px solid #b8daff;'><table class='dash-table' style='margin-top:0; background: white; color: #333;'><thead style='position: sticky; top: 0; box-shadow: 0 1px 2px rgba(0,0,0,0.1);'><tr><th>Filter / Module</th><th>Status</th><th>Effect</th><th>Diagnostic Output</th></tr></thead><tbody>"
                
                if (state.algoDiagnostics && state.algoDiagnostics.size() > 0) {
                    state.algoDiagnostics.each { diag ->
                        def eff = diag.effect ?: "0"
                        def effColor = eff.contains("MUTE") || eff.contains("ABORT") ? "red" : (eff.contains("%") ? "blue" : "black")
                        def statusColor = diag.status == "ON" ? "green" : (diag.status == "ACTIVE" ? "blue" : (diag.status == "HOLD" ? "orange" : "gray"))
                        logicPanel += "<tr><td style='font-weight:bold;'>${diag.name}</td><td style='color:${statusColor};'>${diag.status}</td><td style='color:${effColor}; font-weight:bold;'>${eff}</td><td>${diag.desc}</td></tr>"
                    }
                } else {
                    logicPanel += "<tr><td colspan='4'>Waiting for initial audio routing evaluation...</td></tr>"
                }
                
                logicPanel += "</tbody></table></div>"
                logicPanel += "<div style='margin-top:10px;'><b>Consensus & Confidence:</b> " + (state.confidenceReasoning ?: "Waiting for consensus...") + "</div>"
                logicPanel += "</div>"

                paragraph logicPanel
                
            } else {
                paragraph "<i>No audio devices selected. Click configuration below to assign local playback speakers.</i>"
            }
        }
        
        section("<b>0. 🛑 Visitor Mode (Selective Muting)</b>", hideable: true, hidden: false) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> When this virtual switch is ON, any announcement categories selected below will be silently ignored.</div>"
            input "visitorSwitch", "capability.switch", title: "Visitor Virtual Switch", required: false, submitOnChange: true
            
            if (settings.visitorSwitch) {
                def muteOptions = [
                    "Doorbell & No Answer", 
                    "Intruder Alerts", 
                    "Room Greetings (GN/GM)", 
                    "Arrival Greetings", 
                    "Simple Arrivals", 
                    "Calendar Events", 
                    "Safe Workouts", 
                    "Bedtime Alerts", 
                    "Departure Announcements", 
                    "Meal Time", 
                    "Missed Events",
                    "Weather All Clear",
                    "Trash Alerts",
                    "Mood Broadcasts"
                ]
                input "visitorMuteOptions", "enum", title: "Select Announcements to Mute When Visitor Switch is ON:", options: muteOptions, multiple: true, required: false
            }
        }

        section("<b>0b. 🧠 Psychological Comfort (Mood Sync)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Reads the mood of up to 3 users from the House Management dashboard. If the configured number of users are stressed, angry, or sick, the system automatically mutes selected announcement categories to provide a quiet environment.</div>"
            input "userName1", "text", title: "User 1 Name", defaultValue: "Shane", required: false
            input "moodVarU1", "hubVariable", title: "User 1 Mood Variable", required: false
            input "userName2", "text", title: "User 2 Name", defaultValue: "Christy", required: false
            input "moodVarU2", "hubVariable", title: "User 2 Mood Variable", required: false
            input "userName3", "text", title: "User 3 Name", defaultValue: "Leanne", required: false
            input "moodVarU3", "hubVariable", title: "User 3 Mood Variable", required: false
            
            def muteOptions = [
                "Doorbell & No Answer", "Intruder Alerts", "Room Greetings (GN/GM)", 
                "Arrival Greetings", "Simple Arrivals", "Calendar Events", 
                "Safe Workouts", "Bedtime Alerts", "Departure Announcements", 
                "Meal Time", "Missed Events", "Weather All Clear", "Trash Alerts", "Mood Broadcasts"
            ]
            input "moodMuteOptions", "enum", title: "Select Announcements to Mute When Users are Sick/Stressed:", options: muteOptions, multiple: true, required: false
            input "moodMuteThreshold", "enum", title: "How many users must be negative to trigger mute?", options: ["1":"1 User (Anyone)", "2":"2 Users", "3":"3 Users"], defaultValue: "1"
        }

        section("<b>1. Local Audio Setup & Output Devices</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Local Storage Config:</b> Files uploaded to Hubitat File Manager can be referenced by filename (e.g. <code>greeting_1.mp3</code>) or full URL.</div>"
            input "localBaseUrl", "text", title: "Hubitat Local Storage Base URL", defaultValue: "http://127.0.0.1:8080/local/", required: true, description: "Default local path for hub files."
            
            paragraph "<hr><b>Global / Indoor Speakers</b>"
            input "commonAudioDevices", "capability.speechSynthesis", title: "Select Common / Inside Speakers (For Presence Arrivals, Missed Arrivals & Bedtimes)", multiple: true, required: false
            input "commonVolume", "number", title: "Common / Inside Speakers Volume (%)", defaultValue: 50, required: true
            
            paragraph "<hr><b>Specialty Speakers</b>"
            input "doorbellAudioDevices", "capability.speechSynthesis", title: "Select Front Door / Outdoor Audio Devices (For Guests & Departures)", multiple: true, required: false
            input "intruderAudioDevices", "capability.speechSynthesis", title: "Select Security / Perimeter Audio Devices (For Intruder Alerts)", multiple: true, required: false
            
            paragraph "<hr><b>Chime Configuration</b>"
            input "chimeFile", "text", title: "Pre-Announcement Chime File / URL (Optional)<br><span style='font-size: 12px; color: #555;'>e.g., chime.mp3 or http://127.0.0.1:8080/local/chime.mp3</span>", required: false
            input "chimeDelay", "number", title: "Delay Between Chime and Announcement (Seconds)", defaultValue: 2, required: true
            
            paragraph "<hr><b>Media & TV Interruption</b>"
            input "mediaTVs", "capability.audioVolume", title: "Select TVs to Mute during announcements (e.g., Roku TVs)", multiple: true, required: false
            input "mediaMuteDelay", "number", title: "Auto-Unmute Delay (Seconds)", defaultValue: 8, required: true, description: "Time before restoring the TV volume."
        }

        section("<b>2. Volume & Quiet Modes</b>", hideable: true, hidden: true) {
            input "defaultVolume", "number", title: "Standard Daytime Volume (%)", defaultValue: 60, required: true
            input "nightVolume", "number", title: "Nighttime Volume (%)", defaultValue: 30, required: true
            input "intruderVolume", "number", title: "Intruder Alert Override Volume (%)<br><span style='font-size: 12px; color: #555;'>Used strictly for nighttime motion alerts to scare intruders.</span>", defaultValue: 100, required: true
            input "quietModes", "mode", title: "Mute Announcements During These Modes (Intruders ignore this)", multiple: true, required: false
            input "switchDND", "capability.switch", title: "Do Not Disturb Switch (Routes doorbell to No-Answer)", required: false
        }

        section("<b>3. Doorbell Greetings & Responses</b>", hideable: true, hidden: true) {
            input "doorbells", "capability.pushableButton", title: "Select Doorbells / Buttons", multiple: true, required: false
            input "doorbellCooldown", "number", title: "Doorbell Cooldown (Seconds)", defaultValue: 15, required: true
            
            paragraph "<hr><b>Mode-Based Response Logic</b><br><span style='font-size: 12px; color: #555;'>Configure how the Butler responds to the doorbell based on the current mode.</span>"
            
            input "greetingModes", "mode", title: "Standard Greeting Modes", multiple: true, required: true
            input "awayModes", "mode", title: "Immediate Away Modes", multiple: true, required: true
            
            paragraph "<hr><b>Standard Wait Timeout</b>"
            input "doorContacts", "capability.contactSensor", title: "Front Door Contact Sensor(s)", multiple: true, required: false
            input "noAnswerDelay", "number", title: "No Answer Timeout (Minutes)", defaultValue: 3, required: true
            
            paragraph "<hr><b>Doorbell Greetings File Mapping</b>"
            def greetings = getGreetingMessages()
            for (int i = 0; i < greetings.size(); i++) {
                def num = i + 1
                paragraph "<div style='background:#f8f9fa; padding:8px; border-left:3px solid #007bff; margin-top:5px;'><b>Greeting #${num}:</b> <code>${greetings[i]}</code></div>"
                input "greetingFile_${num}", "text", title: "Local MP3 File/URL for Greeting #${num}", required: false
            }
            
            paragraph "<hr><b>No Answer / Leave a Message File Mapping</b>"
            def noAnswers = getNoAnswerMessages()
            for (int i = 0; i < noAnswers.size(); i++) {
                def num = i + 1
                paragraph "<div style='background:#f8f9fa; padding:8px; border-left:3px solid #6c757d; margin-top:5px;'><b>No Answer #${num}:</b> <code>${noAnswers[i]}</code></div>"
                input "noAnswerFile_${num}", "text", title: "Local MP3 File/URL for No Answer #${num}", required: false
            }
        }
        
        section("<b>4. Intruder Motion Security Alerts</b>", hideable: true, hidden: true) {
            paragraph "<i>If motion is detected under the active security parameter boundary, the Butler will broadcast a high-volume deterrent sequence from local storage.</i>"
            input "motionSensors", "capability.motionSensor", title: "Select Outdoor/Security Motion Sensors", multiple: true, required: false
            
            input "activationType", "enum", title: "Security Activation Parameter Matrix", options: ["Time Only", "Mode Only", "Both Time and Mode", "Either Time or Mode"], defaultValue: "Time Only", required: true, submitOnChange: true
            
            if (settings.activationType == "Time Only" || settings.activationType == "Both Time and Mode" || settings.activationType == "Either Time or Mode") {
                input "nightStartTime", "time", title: "Nighttime Schedule Start", required: true
                input "nightEndTime", "time", title: "Nighttime Schedule End", required: true
            }
            if (settings.activationType == "Mode Only" || settings.activationType == "Both Time and Mode" || settings.activationType == "Either Time or Mode") {
                input "securityModes", "mode", title: "Arm Intruder Alerts in these Modes", multiple: true, required: true
            }
            
            input "motionCooldown", "number", title: "Motion Alert Cooldown (Seconds)", defaultValue: 60, required: true
            
            paragraph "<hr><b>Exemptions & Overrides</b>"
            input "exemptDoors", "capability.contactSensor", title: "Select Exempt Doors (Max 2)<br><span style='font-size: 12px; color: #555;'>If these doors are currently open, or were opened within the last 5 minutes, intruder alerts will be temporarily bypassed.</span>", multiple: true, required: false
            
            paragraph "<hr><b>Defensive Deterrent File Mapping</b>"
            def intruders = getIntruderMessages()
            for (int i = 0; i < intruders.size(); i++) {
                def num = i + 1
                paragraph "<div style='background:#fff5f5; padding:8px; border-left:3px solid #dc3545; margin-top:5px;'><b>Intruder Alert #${num}:</b> <code>${intruders[i]}</code></div>"
                input "intruderFile_${num}", "text", title: "Local MP3 File/URL for Intruder Alert #${num}", required: false
            }
        }

        section("<b>5. Room Greetings (Good Night / Good Morning)</b>", hideable: true, hidden: true) {
            paragraph "<i>Map virtual switches to trigger Good Night (Switch ON) or Good Morning (Switch OFF) phrases for up to 3 distinct rooms. Announcements will trigger 45 seconds after the switch is flipped.</i>"
            
            def gnPhrases = getGoodNightMessages()
            def gmPhrases = getGoodMorningMessages()

            for (int r = 1; r <= 3; r++) {
                paragraph "<hr><div style='font-size: 16px; font-weight: bold; color: #0056b3;'>Room ${r} Configuration</div>"
                input "roomSwitch_${r}", "capability.switch", title: "Room ${r} Virtual Trigger Switch", required: false
                input "roomAudio_${r}", "capability.speechSynthesis", title: "Room ${r} Audio Devices", multiple: true, required: false
                
                if (app.id) {
                    input "testRoom${r}_gn", "button", title: "🌙 Test Room ${r} Good Night Audio & Volume (Plays Instantly)"
                    input "testRoom${r}_gm", "button", title: "☀️ Test Room ${r} Good Morning Audio & Volume (Plays Instantly)"
                }

                input "room${r}_gn_vol", "number", title: "Room ${r} Good Night Volume (%)", defaultValue: 30, required: true
                input "room${r}_gm_vol", "number", title: "Room ${r} Good Morning Volume (%)", defaultValue: 50, required: true

                paragraph "<div style='background:#f4f6f9; padding:8px; border-left:3px solid #6c757d; margin-top:10px;'><b>Personalized Occupancy Files (Optional)</b><br><span style='font-size: 12px; color: #555;'>Assign distinct audio files based on who is currently home. If specific files are missing or no tracked users are present, the system will fall back to the generic lists below.</span></div>"
                
                input "room${r}_u1_name", "text", title: "User 1 Name", required: false, submitOnChange: true
                input "room${r}_u1_presence", "capability.presenceSensor", title: "User 1 Presence Sensor", required: false
                input "room${r}_u2_name", "text", title: "User 2 Name", required: false, submitOnChange: true
                input "room${r}_u2_presence", "capability.presenceSensor", title: "User 2 Presence Sensor", required: false

                def u1Name = settings["room${r}_u1_name"] ?: "User 1"
                def u2Name = settings["room${r}_u2_name"] ?: "User 2"
                
                // --- USER 1 ONLY PHRASES ---
                paragraph "<b>${u1Name} ONLY Home - Good Night Files</b>"
                def u1GN = ["Good night ${u1Name}. Rest well.", "Sleep tight, ${u1Name}. The house is secure.", "Have a peaceful night's sleep, ${u1Name}."]
                for(int i=1; i<=3; i++) {
                    paragraph "<div style='background:#f0f8ff; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested #${i}:</b> <code>${u1GN[i-1]}</code></div>"
                    input "room${r}_u1_gn_${i}", "text", title: "File/URL for ${u1Name} Good Night #${i}", required: false
                }

                paragraph "<b>${u1Name} ONLY Home - Good Morning Files</b>"
                def u1GM = ["Good morning ${u1Name}. I hope you slept well.", "Rise and shine, ${u1Name}. It is a new day.", "Good morning ${u1Name}. All overnight systems report clear."]
                for(int i=1; i<=3; i++) {
                    paragraph "<div style='background:#fffdf0; padding:8px; border-left:3px solid #ffc107; margin-top:5px;'><b>Suggested #${i}:</b> <code>${u1GM[i-1]}</code></div>"
                    input "room${r}_u1_gm_${i}", "text", title: "File/URL for ${u1Name} Good Morning #${i}", required: false
                }

                // --- USER 2 ONLY PHRASES ---
                paragraph "<br><b>${u2Name} ONLY Home - Good Night Files</b>"
                def u2GN = ["Good night ${u2Name}. Rest well.", "Sleep tight, ${u2Name}. The house is secure.", "Have a peaceful night's sleep, ${u2Name}."]
                for(int i=1; i<=3; i++) {
                    paragraph "<div style='background:#f0f8ff; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested #${i}:</b> <code>${u2GN[i-1]}</code></div>"
                    input "room${r}_u2_gn_${i}", "text", title: "File/URL for ${u2Name} Good Night #${i}", required: false
                }

                paragraph "<b>${u2Name} ONLY Home - Good Morning Files</b>"
                def u2GM = ["Good morning ${u2Name}. I hope you slept well.", "Rise and shine, ${u2Name}. It is a new day.", "Good morning ${u2Name}. All overnight systems report clear."]
                for(int i=1; i<=3; i++) {
                    paragraph "<div style='background:#fffdf0; padding:8px; border-left:3px solid #ffc107; margin-top:5px;'><b>Suggested #${i}:</b> <code>${u2GM[i-1]}</code></div>"
                    input "room${r}_u2_gm_${i}", "text", title: "File/URL for ${u2Name} Good Morning #${i}", required: false
                }

                // --- BOTH USERS PHRASES ---
                paragraph "<br><b>BOTH Users Home - Good Night Files</b>"
                def bothGN = ["Good night ${u1Name} and ${u2Name}. Rest well.", "Sleep tight, ${u1Name} and ${u2Name}. The house is secure.", "Have a peaceful night's sleep, ${u1Name} and ${u2Name}."]
                for(int i=1; i<=3; i++) {
                    paragraph "<div style='background:#f0f8ff; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested #${i}:</b> <code>${bothGN[i-1]}</code></div>"
                    input "room${r}_both_gn_${i}", "text", title: "File/URL for Both Good Night #${i}", required: false
                }

                paragraph "<b>BOTH Users Home - Good Morning Files</b>"
                def bothGM = ["Good morning ${u1Name} and ${u2Name}. I hope you both slept well.", "Rise and shine, ${u1Name} and ${u2Name}. It is a new day.", "Good morning ${u1Name} and ${u2Name}. All systems report clear."]
                for(int i=1; i<=3; i++) {
                    paragraph "<div style='background:#fffdf0; padding:8px; border-left:3px solid #ffc107; margin-top:5px;'><b>Suggested #${i}:</b> <code>${bothGM[i-1]}</code></div>"
                    input "room${r}_both_gm_${i}", "text", title: "File/URL for Both Good Morning #${i}", required: false
                }

                paragraph "<br><b>Generic / Fallback Good Night File Mapping (Switch ON)</b>"
                for (int i = 0; i < 5; i++) {
                    def num = i + 1
                    paragraph "<div style='background:#f0f8ff; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested #${num}:</b> <code>${gnPhrases[i]}</code></div>"
                    input "room${r}_gn_${num}", "text", title: "Local MP3 File/URL for Fallback Good Night #${num}", required: false
                }
                
                paragraph "<b>Generic / Fallback Good Morning File Mapping (Switch OFF)</b>"
                for (int i = 0; i < 5; i++) {
                    def num = i + 1
                    paragraph "<div style='background:#fffdf0; padding:8px; border-left:3px solid #ffc107; margin-top:5px;'><b>Suggested #${num}:</b> <code>${gmPhrases[i]}</code></div>"
                    input "room${r}_gm_${num}", "text", title: "Local MP3 File/URL for Fallback Good Morning #${num}", required: false
                }
            }
        }

        section("<b>6. Personalized Arrival Greetings (Lock Codes)</b>", hideable: true, hidden: true) {
            paragraph "<i>Greet up to 3 users when they arrive home. Triggered instantly by lock code, or delayed 10 minutes if triggered by a presence sensor or secondary occupancy switch.</i>"
            
            input "arrivalAudioDevices", "capability.speechSynthesis", title: "Select Exterior / Door Speakers (For direct lock-code greetings)", multiple: true, required: false
            
            paragraph "<hr>"
            input "arrivalLocks", "capability.lock", title: "Select Smart Locks for Arrival Tracking", multiple: true, submitOnChange: true
            input "arrivalCooldown", "number", title: "Arrival Greeting Cooldown per user (Minutes)<br><span style='font-size: 12px; color: #555;'>Time before a user can be greeted again. Also prevents the 10-minute delayed greeting if a lock code was just used.</span>", defaultValue: 15, required: true

            def codeOptions = [:]
            if (arrivalLocks) {
                arrivalLocks.each { lck ->
                    def codesStr = lck.currentValue("lockCodes")
                    if (codesStr) {
                        try {
                            def parsed = new groovy.json.JsonSlurper().parseText(codesStr)
                            parsed.each { slot, info ->
                                if (info.name) codeOptions[info.name] = "${lck.displayName}: ${info.name}"
                            }
                        } catch (e) {
                            log.error "Failed to parse lock codes: ${e}"
                        }
                    }
                }
            }

            for (int u = 1; u <= 3; u++) {
                paragraph "<hr><div style='font-size: 16px; font-weight: bold; color: #28a745;'>User ${u} Configuration</div>"
                input "user${u}_Name", "text", title: "User ${u} Name", required: false, submitOnChange: true
                input "user${u}_Presence", "capability.presenceSensor", title: "User ${u} Presence Sensor (Primary Tracking)", required: false
                input "user${u}_Switches", "capability.switch", title: "User ${u} Alternate Occupancy Switches (e.g., Room Sensor, Good Night Switch)", multiple: true, required: false
                
                if (codeOptions) {
                    input "user${u}_LockCode", "enum", title: "User ${u} Lock Code Name", options: codeOptions, required: false
                } else {
                    paragraph "<span style='color:red;'><i>No lock codes found. Please select a smart lock above to populate available codes.</i></span>"
                }
                
                def userName = settings["user${u}_Name"] ?: "User ${u}"
                
                if (app.id) {
                    input "testArrival_user${u}", "button", title: "👋 Test ${userName} Direct Arrival Greeting"
                    input "testMissed_user${u}", "button", title: "🕰️ Test ${userName} Missed Arrival Greeting"
                }
                
                paragraph "<b>${userName}'s Direct Arrival Greetings (Played immediately upon unlocking)</b>"
                def personalizedPhrases = [
                    "Welcome home, ${userName}. It is good to see you.",
                    "Hello ${userName}. The house is secure and systems are active.",
                    "Welcome back, ${userName}. I hope you had a good time away.",
                    "Greetings ${userName}. I have disarmed the perimeter for your arrival.",
                    "Hello ${userName}. Adjusting the home to your preferences now."
                ]
                
                for (int i = 0; i < 5; i++) {
                    def num = i + 1
                    paragraph "<div style='background:#e8f4f8; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested #${num}:</b> <code>${personalizedPhrases[i]}</code></div>"
                    input "user${u}_greeting_${num}", "text", title: "Local MP3 File/URL for Arrival #${num}", required: false
                }
                
                paragraph "<b>${userName}'s Missed Arrival Greeting (Played after 10 mins on Common Speakers)</b>"
                paragraph "<div style='background:#e8f4f8; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested Phrase:</b> <code>${userName}, I apologize for missing your arrival. Welcome Home!</code></div>"
                input "user${u}_missed", "text", title: "Local MP3 File/URL for Missed Arrival", required: false
            }
        }
        
        section("<b>7. Simple Presence Arrivals</b>", hideable: true, hidden: true) {
            paragraph "<i>When a presence sensor arrives home, immediately play a specific local file using the <b>Common Speakers and Volume</b> configured in Section 1.</i>"
            
            input "simpleArrivalModes", "mode", title: "Allowed Modes for Presence Arrivals", multiple: true, required: false
            
            for (int i = 1; i <= 3; i++) {
                paragraph "<hr><div style='font-size: 14px; font-weight: bold; color: #17a2b8;'>Presence User ${i}</div>"
                input "simplePresence_${i}", "capability.presenceSensor", title: "User ${i} Presence Sensor", required: false
                input "simpleArrivalFile_${i}", "text", title: "Arrival File (e.g., arrived.mp3)", required: false
                
                if (app.id) {
                    input "testSimpleArrival_${i}", "button", title: "👋 Test User ${i} Presence Arrival"
                }
            }
        }

        section("<b>8. Calendar Announcements</b>", hideable: true, hidden: true) {
            paragraph "<i>Monitor virtual switches to trigger calendar event announcements on the Common Speakers using the Common Volume.</i>"
            input "calendarModes", "mode", title: "Allowed Modes for Calendar Announcements", multiple: true, required: false
            
            input "calendarSwitch_1hr", "capability.switch", title: "1 Hour Warning Virtual Switch", required: false
            input "calendarFile_1hr", "text", title: "Local MP3 File/URL for 1 Hour Warning", required: false
            
            paragraph "<hr>"
            
            input "calendarSwitch_5m", "capability.switch", title: "5 Minute Warning Virtual Switch", required: false
            input "calendarFile_5m", "text", title: "Local MP3 File/URL for 5 Minute Warning", required: false
            
            if (app.id) {
                input "testCalendar_1hr", "button", title: "📅 Test 1 Hour Calendar Warning"
                input "testCalendar_5m", "button", title: "📅 Test 5 Minute Calendar Warning"
            }
        }

        section("<b>9. Safe Workouts</b>", hideable: true, hidden: true) {
            paragraph "<i>Announce to the household when a user starts or ends a workout. Announcements will only play if the user is currently 'Away' (not present) and the hub is in an allowed mode. Announcements route to the Common Speakers.</i>"
            
            input "safeWorkoutModes", "mode", title: "Allowed Modes for Safe Workouts", multiple: true, required: false
            
            for (int i = 1; i <= 3; i++) {
                paragraph "<hr><div style='font-size: 14px; font-weight: bold; color: #fd7e14;'>User ${i} Safe Workout</div>"
                input "swUser${i}_Name", "text", title: "User ${i} Name", required: false
                input "swPresence_${i}", "capability.presenceSensor", title: "User ${i} Presence Sensor", required: false
                
                input "swStartSwitch_${i}", "capability.switch", title: "User ${i} Start Workout Virtual Switch", required: false
                input "swStartFile_${i}", "text", title: "Local MP3 File/URL for Start Workout", required: false
                
                input "swEndSwitch_${i}", "capability.switch", title: "User ${i} End Workout Virtual Switch", required: false
                input "swEndFile_${i}", "text", title: "Local MP3 File/URL for End Workout", required: false
                
                if (app.id) {
                    input "testSWStart_${i}", "button", title: "🏃 Test User ${i} Start Workout"
                    input "testSWEnd_${i}", "button", title: "🛑 Test User ${i} End Workout"
                }
            }
        }

        section("<b>10. Smart Bedtime Announcements</b>", hideable: true, hidden: true) {
            paragraph "<i>Monitor early bedtime virtual switches (e.g., triggered by a sleep manager). When turned on, the system instructs the user to go to bed early on the Common Speakers. Announcements queue automatically if multiple switches trigger simultaneously.</i>"
            input "bedtimeModes", "mode", title: "Allowed Modes for Bedtime Announcements", multiple: true, required: false
            
            for (int i = 1; i <= 3; i++) {
                paragraph "<hr><div style='font-size: 14px; font-weight: bold; color: #6610f2;'>User ${i} Bedtime Configuration</div>"
                input "bedtimeName_${i}", "text", title: "User ${i} Name", required: false, submitOnChange: true
                input "bedtimeSwitch_${i}", "capability.switch", title: "User ${i} Bedtime Virtual Switch", required: false
                
                def bName = settings["bedtimeName_${i}"] ?: "User ${i}"
                
                paragraph "<div style='background:#f4f0ff; padding:8px; border-left:3px solid #6610f2; margin-top:5px;'><b>Suggested Phrase:</b> <code>${bName}, your sleep metrics indicate you should head to bed early tonight. Please begin your bedtime routine.</code></div>"
                
                input "bedtimeFile_${i}", "text", title: "Local MP3 File/URL for ${bName}'s Bedtime Alert", required: false
                
                if (app.id) {
                    input "testBedtime_${i}", "button", title: "🛌 Test ${bName} Bedtime Alert (Instantly Plays)"
                }
            }
        }

        section("<b>11. Smart Departure Announcements</b>", hideable: true, hidden: true) {
            paragraph "<i>Monitors a 'Work Day' or 'School Day' virtual switch. If ON and the designated door opens during the configured window, it plays a departure announcement on the Outside/Doorbell Speakers (Limited to 1 time per day per user).</i>"
            
            input "departureDoors", "capability.contactSensor", title: "Select Departure Door(s)", multiple: true, required: false
            
            for (int i = 1; i <= 3; i++) {
                paragraph "<hr><div style='font-size: 14px; font-weight: bold; color: #e83e8c;'>User ${i} Departure Configuration</div>"
                input "depName_${i}", "text", title: "User ${i} Name", required: false, submitOnChange: true
                input "depSwitch_${i}", "capability.switch", title: "User ${i} Work/School Day Switch", required: false
                input "depPresence_${i}", "capability.presenceSensor", title: "User ${i} Presence Sensor (Optional)", required: false
                input "depStart_${i}", "time", title: "Departure Window Start", required: false
                input "depEnd_${i}", "time", title: "Departure Window End", required: false
                
                def dName = settings["depName_${i}"] ?: "User ${i}"
                
                def depPhrases = [
                    "Have a great day, ${dName}!",
                    "Wishing you a wonderful day, ${dName}!",
                    "Goodbye ${dName}! The house will remain secure while you are away.",
                    "Take care and see you later, ${dName}!",
                    "Safe travels, ${dName}!"
                ]
                
                for (int j = 0; j < 5; j++) {
                    def num = j + 1
                    paragraph "<div style='background:#fdf4f8; padding:8px; border-left:3px solid #e83e8c; margin-top:5px;'><b>Suggested Phrase #${num}:</b> <code>${depPhrases[j]}</code></div>"
                    input "depFile_${i}_${num}", "text", title: "Local MP3 File/URL for Departure #${num}", required: false
                }
                
                if (app.id) {
                    input "testDep_${i}", "button", title: "👋 Test ${dName} Departure (Instantly Plays)"
                    input "resetDepState_${i}", "button", title: "🔄 Reset ${dName} Daily Departure Flag"
                }
            }
        }

        section("<b>12. Meal Time Announcements</b>", hideable: true, hidden: true) {
            paragraph "<i>Monitor a virtual switch for Meal Time. When turned ON, the system announces that meal time has started on the Common Speakers. When OFF, it announces meal time has concluded.</i>"
            input "mealTimeModes", "mode", title: "Allowed Modes for Meal Time Announcements", multiple: true, required: false
            
            input "mealTimeSwitch", "capability.switch", title: "Meal Time Virtual Switch", required: false
            
            paragraph "<hr><b>Meal Time Started (Switch ON)</b>"
            def mealOnPhrases = [
                "Meal Time has been enabled, enjoy your food.",
                "Dinner is ready. Please gather in the dining room.",
                "The family meal is ready.",
                "Attention: It is time to eat.",
                "Meal time has begun. Bon appétit!"
            ]
            paragraph "<div style='background:#e8f4f8; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested ON Phrase:</b> <code>${mealOnPhrases[0]}</code></div>"
            input "mealTimeFileOn", "text", title: "Local MP3 File/URL for Meal Time ON", required: false
            
            if (app.id) {
                input "testMealTimeOn", "button", title: "🍽️ Test Meal Time ON Announcement"
            }
            
            paragraph "<hr><b>Meal Time Ended (Switch OFF)</b>"
            def mealOffPhrases = [
                "Meal time has ended.",
                "Meal time has concluded. The dining area is now clear.",
                "Meal time mode is now disabled.",
                "Thank you. Meal time is over.",
                "Meal time is complete. Returning to standard operations."
            ]
            paragraph "<div style='background:#fdf4f8; padding:8px; border-left:3px solid #e83e8c; margin-top:5px;'><b>Suggested OFF Phrase:</b> <code>${mealOffPhrases[0]}</code></div>"
            input "mealTimeFileOff", "text", title: "Local MP3 File/URL for Meal Time OFF", required: false
            
            if (app.id) {
                input "testMealTimeOff", "button", title: "🍽️ Test Meal Time OFF Announcement"
            }
        }

        section("<b>13. Missed Events & Overnight Activity Alerts</b>", hideable: true, hidden: true) {
            paragraph "<i>Notify the household of events that occurred while they were asleep or away.</i>"
            
            paragraph "<hr><b>Overnight Motion Camera Check</b>"
            input "enableMorningMotionAlert", "bool", title: "Enable Overnight Motion Alert", defaultValue: false, submitOnChange: true
            
            if (settings.enableMorningMotionAlert) {
                input "overnightMotionSensors", "capability.motionSensor", title: "Select Overnight Motion Sensors to monitor", multiple: true, required: true
                input "overnightDoors", "capability.contactSensor", title: "Doors that must be CLOSED to trigger alert (e.g., Exterior Doors)", multiple: true, required: false
                input "morningAlertRoom", "enum", title: "Which Room's Good Morning triggers this?", options: ["Room 1", "Room 2", "Room 3", "All Rooms"], defaultValue: "Room 1", required: true
                
                paragraph "<div style='background:#fffdf0; padding:8px; border-left:3px solid #ffc107; margin-top:5px;'><b>Suggested Phrase:</b> <code>Motion was detected outside last night. Please review the security cameras for more information.</code></div>"
                input "overnightAlertFile", "text", title: "Local MP3 File/URL for Morning Motion Alert", required: true
                
                if (app.id) {
                    input "testMorningAlertBtn", "button", title: "☀️ Test Morning Camera Check Alert"
                }
            }

            paragraph "<hr><b>Away Doorbell Missed Alert</b>"
            input "enableAwayDoorbellAlert", "bool", title: "Enable Missed Doorbell Alert on Arrival", defaultValue: false, submitOnChange: true
            
            if (settings.enableAwayDoorbellAlert) {
                input "awayDoorbellDelay", "number", title: "Delay after arrival before announcing (Minutes)", defaultValue: 2, required: true
                
                paragraph "<div style='background:#e8f4f8; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested Phrase:</b> <code>While you were away, someone visited the house. Please check the cameras for a message.</code></div>"
                input "awayDoorbellAlertFile", "text", title: "Local MP3 File/URL for Missed Doorbell", required: true
                
                if (app.id) {
                    input "testMissedDoorbellBtn", "button", title: "🕰️ Test Missed Visitor Alert"
                }
            }
        }

        section("<b>14. Weather All Clear Announcements</b>", hideable: true, hidden: true) {
            paragraph "<i>Monitors multiple weather alert virtual switches. If any switch was ON, and then ALL switches are turned OFF, the system announces an 'All Clear' on the Common Speakers.</i>"
            input "weatherModes", "mode", title: "Allowed Modes for Weather Announcements", multiple: true, required: false
            
            input "weatherSwitches", "capability.switch", title: "Weather Alert Virtual Switches", multiple: true, required: false
            
            paragraph "<div style='background:#e8f4f8; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested Phrase:</b> <code>The severe weather alerts have expired. All clear.</code></div>"
            input "weatherAllClearFile", "text", title: "Local MP3 File/URL for Weather All Clear", required: false
            
            if (app.id) {
                input "testWeatherAllClear", "button", title: "🌤️ Test Weather All Clear Announcement"
            }
        }
        
        section("<b>15. Trash & Garbage Alerts</b>", hideable: true, hidden: true) {
            paragraph "<i>Monitor virtual switches to trigger announcements for a Dirty Trash Can or a Full Trash Can on the Common Speakers.</i>"
            input "trashModes", "mode", title: "Allowed Modes for Trash Announcements", multiple: true, required: false
            
            paragraph "<hr><b>Dirty Can Alert</b>"
            input "trashDirtySwitch", "capability.switch", title: "Dirty Can Virtual Switch", required: false
            paragraph "<div style='background:#fffdf0; padding:8px; border-left:3px solid #ffc107; margin-top:5px;'><b>Suggested Phrase:</b> <code>The trash can needs to be cleaned.</code></div>"
            input "trashDirtyFile", "text", title: "Local MP3 File/URL for Dirty Can", required: false
            
            if (app.id) {
                input "testTrashDirtyBtn", "button", title: "🗑️ Test Dirty Can Alert"
            }
            
            paragraph "<hr><b>Can Full Alert</b>"
            input "trashFullSwitch", "capability.switch", title: "Can Full Virtual Switch", required: false
            paragraph "<div style='background:#fffdf0; padding:8px; border-left:3px solid #ffc107; margin-top:5px;'><b>Suggested Phrase:</b> <code>The trash can is full and needs to be emptied.</code></div>"
            input "trashFullFile", "text", title: "Local MP3 File/URL for Can Full", required: false
            
            if (app.id) {
                input "testTrashFullBtn", "button", title: "🗑️ Test Can Full Alert"
            }
        }

        section("<b>16. Mood Broadcast Announcements</b>", hideable: true, hidden: true) {
            paragraph "<i>Monitor the users' mood variables. If a user transitions into one of the allowed mood categories, the system will play a personalized announcement on the Common Speakers.</i>"
            
            for (int i = 1; i <= 3; i++) {
                paragraph "<hr><div style='font-size: 14px; font-weight: bold; color: #17a2b8;'>User ${i} Mood Broadcast</div>"
                input "mbName_${i}", "text", title: "User ${i} Name", required: false, submitOnChange: true
                input "mbVar_${i}", "hubVariable", title: "User ${i} Mood Variable", required: false
                
                def mbName = settings["mbName_${i}"] ?: "User ${i}"
                
                input "mbCats_${i}", "enum", title: "Announce when ${mbName} enters these mood states:", options: ["Motivated", "Low Energy", "Frazzled", "Sick"], multiple: true, submitOnChange: true
                
                if (settings["mbCats_${i}"]) {
                    def cats = settings["mbCats_${i}"] as List
                    
                    if (cats.contains("Motivated")) {
                        paragraph "<div style='background:#e8f4f8; padding:8px; border-left:3px solid #17a2b8; margin-top:5px;'><b>Suggested Motivated Phrase:</b><br><code>Attention household: ${mbName} is feeling highly motivated and upbeat! Let's match that energy!</code></div>"
                        input "mbFile_Motivated_${i}", "text", title: "Local MP3 File/URL for Motivated Mood", required: false
                    }
                    if (cats.contains("Low Energy")) {
                        paragraph "<div style='background:#fffdf0; padding:8px; border-left:3px solid #ffc107; margin-top:5px;'><b>Suggested Low Energy Phrase:</b><br><code>Please be advised, ${mbName} is currently operating on low energy. Please keep volume down and offer support.</code></div>"
                        input "mbFile_LowEnergy_${i}", "text", title: "Local MP3 File/URL for Low Energy Mood", required: false
                    }
                    if (cats.contains("Frazzled")) {
                        paragraph "<div style='background:#fdf4f8; padding:8px; border-left:3px solid #e83e8c; margin-top:5px;'><b>Suggested Frazzled Phrase:</b><br><code>Warning: ${mbName} is currently feeling stressed or frazzled. Proceed with caution and provide space.</code></div>"
                        input "mbFile_Frazzled_${i}", "text", title: "Local MP3 File/URL for Frazzled Mood", required: false
                    }
                    if (cats.contains("Sick")) {
                        paragraph "<div style='background:#fff5f5; padding:8px; border-left:3px solid #dc3545; margin-top:5px;'><b>Suggested Sick Phrase:</b><br><code>Notice: ${mbName} is feeling unwell. Please maintain a quiet environment to assist with recovery.</code></div>"
                        input "mbFile_Sick_${i}", "text", title: "Local MP3 File/URL for Sick Mood", required: false
                    }
                }
                if (app.id) {
                    input "testMoodBroadcast_${i}", "button", title: "🗣️ Test ${mbName} Mood Broadcast"
                }
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
                input "testVoiceBtn", "button", title: "🔊 Test Doorbell Local Audio"
                input "testNoAnswerBtn", "button", title: "🔊 Test No-Answer Local Audio"
                input "testIntruderBtn", "button", title: "🚨 Test Intruder Local Audio"
                input "testChimeBtn", "button", title: "🔔 Test Chime Sound"
                input "clearStateBtn", "button", title: "⚠ Reset Internal State & Cooldowns"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.algoDiagnostics) state.algoDiagnostics = []
    if (!state.cooldownMap) state.cooldownMap = [:]
    if (!state.bedtimeQueue) state.bedtimeQueue = []
    state.bedtimeProcessing = false
    if (state.weatherAlertActive == null) state.weatherAlertActive = false
    if (state.isMoodMuted == null) state.isMoodMuted = false
    
    state.waitingForDoorAnswer = false
    state.overnightMotionDetected = false
    state.missedAwayDoorbell = false
    
    if (doorbells) subscribe(doorbells, "pushed", "doorbellHandler")
    if (doorContacts) subscribe(doorContacts, "contact.open", "doorAnsweredHandler")
    if (motionSensors) subscribe(motionSensors, "motion.active", "motionHandler")
    if (switchDND) subscribe(switchDND, "switch", "dndHandler")
    if (exemptDoors) subscribe(exemptDoors, "contact.open", "exemptDoorHandler")
    
    // Subscribe to Hub Mode changes for state resets
    subscribe(location, "mode", "modeChangeHandler")

    // Subscribe to Room Switches
    for (int r = 1; r <= 3; r++) {
        if (settings["roomSwitch_${r}"]) subscribe(settings["roomSwitch_${r}"], "switch", "roomSwitchHandler")
    }

    // Subscribe to Lock Arrival Events (Section 6)
    if (arrivalLocks) subscribe(arrivalLocks, "lock.unlocked", "lockArrivalHandler")
    for (int u = 1; u <= 3; u++) {
        if (settings["user${u}_Presence"]) subscribe(settings["user${u}_Presence"], "presence", "presenceHandler")
        if (settings["user${u}_Switches"]) subscribe(settings["user${u}_Switches"], "switch.on", "userSwitchHandler")
    }
    
    // Subscribe to Simple Presence Arrivals (Section 7)
    for (int i = 1; i <= 3; i++) {
        if (settings["simplePresence_${i}"]) subscribe(settings["simplePresence_${i}"], "presence", "simplePresenceArrivalHandler")
    }
    
    // Subscribe to Calendar Announcements (Section 8)
    if (calendarSwitch_1hr) subscribe(calendarSwitch_1hr, "switch.on", "calendar1hrHandler")
    if (calendarSwitch_5m) subscribe(calendarSwitch_5m, "switch.on", "calendar5mHandler")

    // Subscribe to Safe Workout Switches (Section 9)
    for (int i = 1; i <= 3; i++) {
        if (settings["swStartSwitch_${i}"]) subscribe(settings["swStartSwitch_${i}"], "switch.on", "safeWorkoutStartHandler")
        if (settings["swEndSwitch_${i}"]) subscribe(settings["swEndSwitch_${i}"], "switch.on", "safeWorkoutEndHandler")
    }

    // Subscribe to Smart Bedtime Switches (Section 10)
    for (int i = 1; i <= 3; i++) {
        if (settings["bedtimeSwitch_${i}"]) subscribe(settings["bedtimeSwitch_${i}"], "switch.on", "bedtimeSwitchHandler")
    }

    // Subscribe to Departure Doors (Section 11)
    if (departureDoors) subscribe(departureDoors, "contact.open", "departureDoorHandler")

    // Subscribe to Meal Time Switch (Section 12)
    if (mealTimeSwitch) subscribe(mealTimeSwitch, "switch", "mealTimeSwitchHandler")

    // Subscribe to Overnight Motion Sensors (Section 13)
    if (settings.enableMorningMotionAlert && settings.overnightMotionSensors) {
        subscribe(settings.overnightMotionSensors, "motion.active", "overnightMotionHandler")
    }

    // Subscribe to Weather Alert Switches (Section 14)
    if (weatherSwitches) subscribe(weatherSwitches, "switch", "weatherSwitchHandler")
    
    // Subscribe to Trash Alert Switches (Section 15)
    if (trashDirtySwitch) subscribe(trashDirtySwitch, "switch.on", "trashDirtyHandler")
    if (trashFullSwitch) subscribe(trashFullSwitch, "switch.on", "trashFullHandler")

    // Subscribe to Mood Logic (Section 0b - Auto-Regulation)
    if (settings.moodVarU1) subscribe(location, "variable:${settings.moodVarU1}", "moodChangeHandler")
    if (settings.moodVarU2) subscribe(location, "variable:${settings.moodVarU2}", "moodChangeHandler")
    if (settings.moodVarU3) subscribe(location, "variable:${settings.moodVarU3}", "moodChangeHandler")
    
    // Subscribe to Mood Broadcast Variables (Section 16)
    for (int i = 1; i <= 3; i++) {
        if (settings["mbVar_${i}"]) subscribe(location, "variable:${settings["mbVar_${i}"]}", "moodBroadcastHandler")
    }

    evaluateMoods()
    logAction("Advanced Voice Butler Initialized.")
    evaluateRoutingMatrix()
}

void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard data manually refreshed.")
        evaluateRoutingMatrix()
    }
    else if (btn.startsWith("testRoom")) {
        def rNum = btn.substring(8, 9).toInteger() 
        def tType = btn.substring(10) 
        logAction("MANUAL OVERRIDE: Testing Room ${rNum} ${tType == 'gn' ? 'Good Night' : 'Good Morning'} (Immediate execution).")
        executeRoomAnnouncement([roomNum: rNum, type: tType])
    }
    else if (btn.startsWith("testArrival_user")) {
        def uNum = btn.substring(16).toInteger()
        logAction("MANUAL OVERRIDE: Testing User ${uNum} Lock Arrival Greeting.")
        testUserArrival(uNum, false)
    }
    else if (btn.startsWith("testMissed_user")) {
        def uNum = btn.substring(15).toInteger()
        logAction("MANUAL OVERRIDE: Testing User ${uNum} Missed Lock Arrival.")
        testUserArrival(uNum, true)
    }
    else if (btn.startsWith("testSimpleArrival_")) {
        def uNum = btn.substring(18).toInteger()
        logAction("MANUAL OVERRIDE: Testing Simple Presence Arrival for User ${uNum}.")
        def fileName = settings["simpleArrivalFile_${uNum}"]
        def speakerList = settings.commonAudioDevices
        def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
        if (fileName && speakerList) {
            processAnnouncement("Test User ${uNum} Arrived", fileName, "Test: Simple Presence", true, speakerList, false, customVol)
        } else {
            log.warn "Missing audio file or Common Speaker configuration for Simple Presence User ${uNum}."
        }
    }
    else if (btn.startsWith("testSWStart_")) {
        def uNum = btn.substring(12).toInteger()
        logAction("MANUAL OVERRIDE: Testing Safe Workout Start for User ${uNum}.")
        testSafeWorkout(uNum, "start")
    }
    else if (btn.startsWith("testSWEnd_")) {
        def uNum = btn.substring(10).toInteger()
        logAction("MANUAL OVERRIDE: Testing Safe Workout End for User ${uNum}.")
        testSafeWorkout(uNum, "end")
    }
    else if (btn.startsWith("testBedtime_")) {
        def uNum = btn.substring(12).toInteger()
        logAction("MANUAL OVERRIDE: Testing Bedtime Alert for User ${uNum}.")
        testBedtime(uNum)
    }
    else if (btn.startsWith("testDep_")) {
        def uNum = btn.substring(8).toInteger()
        logAction("MANUAL OVERRIDE: Testing Departure Announcement for User ${uNum}.")
        testDeparture(uNum)
    }
    else if (btn.startsWith("resetDepState_")) {
        def uNum = btn.substring(14).toInteger()
        def tz = location.timeZone
        def todayStr = new Date().format("yyyy-MM-dd", tz)
        state.remove("depAnnounced_${uNum}_${todayStr}")
        logAction("MANUAL OVERRIDE: Cleared daily departure lockout flag for User ${uNum}.")
    }
    else if (btn == "testMealTimeOn") {
        logAction("MANUAL OVERRIDE: Testing Meal Time ON Announcement.")
        testMealTime(true)
    }
    else if (btn == "testMealTimeOff") {
        logAction("MANUAL OVERRIDE: Testing Meal Time OFF Announcement.")
        testMealTime(false)
    }
    else if (btn == "testWeatherAllClear") {
        logAction("MANUAL OVERRIDE: Testing Weather All Clear Announcement.")
        testWeatherAllClear()
    }
    else if (btn == "testTrashDirtyBtn") {
        logAction("MANUAL OVERRIDE: Testing Dirty Can Alert.")
        testTrashAlert("dirty")
    }
    else if (btn == "testTrashFullBtn") {
        logAction("MANUAL OVERRIDE: Testing Can Full Alert.")
        testTrashAlert("full")
    }
    else if (btn.startsWith("testMoodBroadcast_")) {
        def uNum = btn.substring(18).toInteger()
        logAction("MANUAL OVERRIDE: Testing Mood Broadcast for User ${uNum}.")
        testMoodBroadcast(uNum)
    }
    else if (btn == "testCalendar_1hr") {
        logAction("MANUAL OVERRIDE: Testing 1 Hour Calendar Warning.")
        playCalendarAnnouncement("1hr")
    }
    else if (btn == "testCalendar_5m") {
        logAction("MANUAL OVERRIDE: Testing 5 Minute Calendar Warning.")
        playCalendarAnnouncement("5m")
    }
    else if (btn == "testMorningAlertBtn") {
        logAction("MANUAL OVERRIDE: Testing Morning Camera Check Alert.")
        def roomNum = (settings.morningAlertRoom && settings.morningAlertRoom.startsWith("Room")) ? settings.morningAlertRoom.substring(5).toInteger() : 1
        executeMorningMotionAlert([roomNum: roomNum])
    }
    else if (btn == "testMissedDoorbellBtn") {
        logAction("MANUAL OVERRIDE: Testing Away Doorbell Missed Alert.")
        executeMissedDoorbellAlert()
    }
    else if (btn == "testVoiceBtn") { 
        logAction("MANUAL OVERRIDE: Firing test doorbell announcement.")
        def selected = getRandomAudioItem("greeting")
        if (selected) {
            processAnnouncement(selected.text, selected.file, "System Test (Doorbell)", true, doorbellAudioDevices, false)
        } else {
            log.warn "No configured local files found for Greetings."
        }
    }
    else if (btn == "testNoAnswerBtn") { 
        logAction("MANUAL OVERRIDE: Firing test no-answer announcement.")
        def selected = getRandomAudioItem("noAnswer")
        if (selected) {
            processAnnouncement(selected.text, selected.file, "System Test (No Answer)", true, doorbellAudioDevices, false)
        } else {
            log.warn "No configured local files found for No Answer."
        }
    }
    else if (btn == "testIntruderBtn") { 
        logAction("MANUAL OVERRIDE: Firing test intruder announcement.")
        def selected = getRandomAudioItem("intruder")
        if (selected) {
            processAnnouncement(selected.text, selected.file, "System Test (Intruder)", true, intruderAudioDevices, true)
        } else {
            log.warn "No configured local files found for Intruder Alerts."
        }
    }
    else if (btn == "testChimeBtn") {
        logAction("MANUAL OVERRIDE: Firing test chime.")
        def chimeUrl = buildFullAudioUrl(settings.chimeFile)
        if (chimeUrl != "") {
            def targetDevices = doorbellAudioDevices ?: intruderAudioDevices
            def devList = [targetDevices].flatten().findAll { it }
            if (devList) {
                def vol = settings.defaultVolume ?: 50
                devList.each { speaker ->
                    logAction("🔔 Playing test chime on ${speaker.displayName}: '${chimeUrl}' at ${vol}% volume.")
                    playAudioOnDevice(speaker, chimeUrl, vol, false)
                }
            } else {
                log.warn "No local audio devices configured for chime test."
            }
        } else {
            log.warn "No chime file configured to test."
        }
    }
    else if (btn == "resetActionHistory") {
        state.actionHistory = []
    }
    else if (btn == "clearStateBtn") {
        logAction("EMERGENCY RESET: Purging history, departure logs, and cooldown trackers.")
        state.algoDiagnostics = []
        state.cooldownMap = [:]
        state.bedtimeQueue = []
        state.bedtimeProcessing = false
        state.lastMessage = null
        state.lastTriggerSource = null
        state.waitingForDoorAnswer = false
        state.overnightMotionDetected = false
        state.missedAwayDoorbell = false
        state.weatherAlertActive = false
        
        def keysToRemove = state.keySet().findAll { it.startsWith("depAnnounced_") }
        keysToRemove.each { state.remove(it) }
        
        unschedule("noAnswerTimeoutHandler")
        unschedule("processBedtimeQueue")
        unschedule("executeMissedDoorbellAlert")
        unschedule("executeMorningMotionAlert")
        evaluateRoutingMatrix()
    }
}

// --- Section 16: Mood Broadcast Engine ---

String getMoodCategory(String emoji) {
    def motivated = ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]
    def lowEnergy = ["😴", "🥱", "🥺", "🤡"]
    def frazzled = ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]
    def sick = ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]
    
    if (motivated.contains(emoji)) return "Motivated"
    if (lowEnergy.contains(emoji)) return "Low Energy"
    if (frazzled.contains(emoji)) return "Frazzled"
    if (sick.contains(emoji)) return "Sick"
    return null
}

def moodBroadcastHandler(evt) {
    def varName = evt.name.replace("variable:", "")
    def moodEmoji = evt.value
    
    if (!moodEmoji || moodEmoji == "😐" || moodEmoji == "") return 
    
    if (isCategoryMuted("Mood Broadcasts")) return
    
    def category = getMoodCategory(moodEmoji)
    if (!category) return
    
    for (int i = 1; i <= 3; i++) {
        if (settings["mbVar_${i}"] == varName) {
            def allowedCats = settings["mbCats_${i}"] ?: []
            if (allowedCats.contains(category)) {
                def file = settings["mbFile_${category.replace(" ", "")}_${i}"]
                def name = settings["mbName_${i}"] ?: "User ${i}"
                
                // Check cooldown so it doesn't spam if they flip through emojis
                def cdKey = "mood_broadcast_${i}_${category}"
                if (isOnCooldown(cdKey, 15 * 60 * 1000)) { // 15 minute cooldown per user per category
                    logDebug("Mood broadcast for ${name} (${category}) on cooldown.")
                    return
                }
                
                setCooldown(cdKey)
                
                if (file) {
                    logAction("🧠 ${name} entered ${category} mood state. Playing broadcast on Common Speakers.")
                    def speakerList = settings.commonAudioDevices
                    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
                    
                    if (speakerList) {
                        processAnnouncement("${name} Mood: ${category}", file.trim(), "Mood Broadcast", false, speakerList, false, customVol)
                    } else {
                        log.warn "Mood broadcast triggered, but no Common Speakers configured in Section 1."
                    }
                } else {
                    log.warn "${name} entered ${category} mood, but no audio file is configured for this broadcast."
                }
            }
        }
    }
}

def testMoodBroadcast(userNum) {
    def name = settings["mbName_${userNum}"] ?: "User ${userNum}"
    def allowedCats = settings["mbCats_${userNum}"] ?: []
    
    if (allowedCats.size() == 0) {
        log.warn "No mood categories enabled for ${name} to test."
        return
    }
    
    // Test the first allowed category selected in the UI
    def catToTest = allowedCats[0]
    def file = settings["mbFile_${catToTest.replace(" ", "")}_${userNum}"]
    def speakerList = settings.commonAudioDevices
    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
    
    if (file && speakerList) {
        processAnnouncement("${name} Mood: ${catToTest}", file.trim(), "Test: Mood Broadcast", true, speakerList, false, customVol)
    } else {
        log.warn "Missing audio file or Common Speaker configuration for ${name} Mood Broadcast test."
    }
}

// --- Section 15: Trash & Garbage Engine ---

def trashDirtyHandler(evt) {
    handleTrashAlert("dirty")
}

def trashFullHandler(evt) {
    handleTrashAlert("full")
}

def handleTrashAlert(type) {
    if (isCategoryMuted("Trash Alerts")) return

    if (settings.trashModes && !settings.trashModes.contains(location.mode)) {
        logDebug("Trash alert skipped: Current mode (${location.mode}) is not permitted.")
        return
    }

    def isDirty = (type == "dirty")
    def file = isDirty ? settings.trashDirtyFile : settings.trashFullFile
    def eventName = isDirty ? "Dirty Can Alert" : "Can Full Alert"
    
    if (file) {
        logAction("🗑️ ${eventName} triggered. Playing announcement on Common Speakers.")
        def speakerList = settings.commonAudioDevices
        def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
        
        if (speakerList) {
            processAnnouncement(eventName, file.trim(), "Trash Alerts", false, speakerList, false, customVol)
        } else {
            log.warn "${eventName} triggered, but no Common Speakers configured in Section 1."
        }
    } else {
        log.warn "${eventName} switch turned ON, but no audio file is configured."
    }
}

def testTrashAlert(type) {
    def isDirty = (type == "dirty")
    def file = isDirty ? settings.trashDirtyFile : settings.trashFullFile
    def eventName = isDirty ? "Dirty Can Alert" : "Can Full Alert"
    def speakerList = settings.commonAudioDevices
    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
    
    if (file && speakerList) {
        processAnnouncement(eventName, file.trim(), "Test: Trash Alert", true, speakerList, false, customVol)
    } else {
        log.warn "Missing audio file or Common Speaker configuration to test ${eventName}."
    }
}

// --- Section 14: Weather All Clear Engine ---

def weatherSwitchHandler(evt) {
    def isAnyOn = false
    if (settings.weatherSwitches) {
        // Check if ANY of the monitored switches are currently ON
        isAnyOn = settings.weatherSwitches.any { it.currentValue("switch") == "on" }
    }
    
    if (evt.value == "on") {
        if (!state.weatherAlertActive) {
            logAction("🌩️ Weather alert switch turned ON. Arming 'All Clear' monitor.")
            state.weatherAlertActive = true
        }
    } else if (evt.value == "off") {
        // If armed, and NO switches are currently on, trigger the All Clear
        if (state.weatherAlertActive && !isAnyOn) {
            state.weatherAlertActive = false // Reset flag immediately to prevent duplicate triggers
            
            if (isCategoryMuted("Weather All Clear")) return 

            // --- MODE CHECK ---
            if (settings.weatherModes && !settings.weatherModes.contains(location.mode)) {
                logDebug("Weather All Clear announcement skipped: Current mode (${location.mode}) is not permitted.")
                return
            }
            
            def file = settings.weatherAllClearFile
            def speakerList = settings.commonAudioDevices
            def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
            
            if (file && speakerList) {
                logAction("🌤️ All weather alert switches are now OFF. Playing 'All Clear' on Common Speakers.")
                processAnnouncement("Weather All Clear", file.trim(), "Weather Alerts", false, speakerList, false, customVol)
            } else {
                log.warn "Weather All Clear triggered, but missing audio file or Common Speakers configuration."
            }
        }
    }
}

def testWeatherAllClear() {
    def file = settings.weatherAllClearFile
    def speakerList = settings.commonAudioDevices
    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
    
    if (file && speakerList) {
        processAnnouncement("Weather All Clear", file.trim(), "Test: Weather", true, speakerList, false, customVol)
    } else {
        log.warn "Missing audio file or Common Speaker configuration to test Weather All Clear."
    }
}

// --- Section 13: Overnight & Missed Events Engine ---

def overnightMotionHandler(evt) {
    if (!settings.enableMorningMotionAlert) return

    // Optional: Only flag if in a quiet mode or during typical night hours
    def isNightMode = settings.quietModes?.contains(location.mode)
    def hour = new Date().format("H", location.timeZone).toInteger()
    def isNightTime = (hour >= 21 || hour < 7)

    if (!isNightMode && !isNightTime) return // Disregard daytime motion for this specific alert

    def doorsClosed = true
    if (settings.overnightDoors) {
        doorsClosed = !settings.overnightDoors.any { it.currentValue("contact") == "open" }
    }

    if (doorsClosed) {
        state.overnightMotionDetected = true
        logAction("🌙 Overnight motion detected on ${evt.device.displayName} while doors were closed. Flagged for morning alert.")
    }
}

def executeMorningMotionAlert(data) {
    if (isCategoryMuted("Missed Events")) return 
    
    def roomNum = data.roomNum
    def targetDevices = settings["roomAudio_${roomNum}"]
    def file = settings.overnightAlertFile
    def customVol = settings["room${roomNum}_gm_vol"] ?: 50

    if (file && targetDevices) {
        logAction("⚠️ Playing Morning Motion Camera Check alert in Room ${roomNum}.")
        processAnnouncement("Morning Motion Camera Check", file.trim(), "Overnight Motion", true, targetDevices, false, customVol)
    } else {
        log.warn "Morning motion alert triggered, but missing audio file or room audio devices."
    }
}

def triggerMissedDoorbellAlert() {
    if (state.missedAwayDoorbell && settings.enableAwayDoorbellAlert) {
        def delayMins = settings.awayDoorbellDelay != null ? settings.awayDoorbellDelay.toInteger() : 2
        logAction("🏠 Arrival detected. Scheduling missed doorbell alert in ${delayMins} minutes.")
        runIn(delayMins * 60, "executeMissedDoorbellAlert")
        state.missedAwayDoorbell = false
    }
}

def executeMissedDoorbellAlert() {
    if (isCategoryMuted("Missed Events")) return 

    def file = settings.awayDoorbellAlertFile
    def speakerList = settings.commonAudioDevices
    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null

    if (file && speakerList) {
        logAction("⚠️ Playing Missed Visitor Alert on Common Speakers.")
        processAnnouncement("Missed Visitor Alert", file.trim(), "Away Doorbell", false, speakerList, false, customVol)
    } else {
        log.warn "Missed doorbell alert triggered but missing file or common speakers."
    }
}

// --- Section 12: Meal Time Engine ---

def mealTimeSwitchHandler(evt) {
    if (isCategoryMuted("Meal Time")) return 

    // --- MODE CHECK ---
    if (settings.mealTimeModes && !settings.mealTimeModes.contains(location.mode)) {
        logDebug("Meal Time announcement skipped: Current mode (${location.mode}) is not permitted.")
        return
    }

    def isMealTime = (evt.value == "on")
    def file = isMealTime ? settings.mealTimeFileOn : settings.mealTimeFileOff
    def statusStr = isMealTime ? "ON" : "OFF"
    def eventName = isMealTime ? "Meal Time Started" : "Meal Time Concluded"
    
    if (file) {
        logAction("🍽️ Meal Time switch turned ${statusStr}. Playing announcement on Common Speakers.")
        def speakerList = settings.commonAudioDevices
        def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
        
        if (speakerList) {
            processAnnouncement(eventName, file.trim(), "Meal Time", false, speakerList, false, customVol)
        } else {
            log.warn "Meal Time triggered, but no Common Speakers configured in Section 1."
        }
    } else {
        log.warn "Meal Time switch turned ${statusStr}, but no audio file is configured for this state."
    }
}

def testMealTime(isOn) {
    def file = isOn ? settings.mealTimeFileOn : settings.mealTimeFileOff
    def statusStr = isOn ? "ON" : "OFF"
    def eventName = isOn ? "Meal Time Started" : "Meal Time Concluded"
    def speakerList = settings.commonAudioDevices
    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
    
    if (file && speakerList) {
        processAnnouncement(eventName, file.trim(), "Test: Meal Time ${statusStr}", true, speakerList, false, customVol)
    } else {
        log.warn "Missing audio file or Common Speaker configuration to test Meal Time ${statusStr}."
    }
}

// --- Section 11: Smart Departure Engine ---

def departureDoorHandler(evt) {
    if (evt.value != "open") return
    if (isCategoryMuted("Departure Announcements")) return 
    
    def tz = location.timeZone
    def todayStr = new Date().format("yyyy-MM-dd", tz)
    
    for (int i = 1; i <= 3; i++) {
        def wSwitch = settings["depSwitch_${i}"]
        if (wSwitch && wSwitch.currentValue("switch") == "on") {
            def startStr = settings["depStart_${i}"]
            def endStr = settings["depEnd_${i}"]
            
            if (startStr && endStr) {
                def sTime = timeToday(startStr, tz)
                def eTime = timeToday(endStr, tz)
                def nowTime = new Date()
                
                if (timeOfDayIsBetween(sTime, eTime, nowTime, tz)) {
                    def stateKey = "depAnnounced_${i}_${todayStr}"
                    
                    if (!state[stateKey]) {
                        
                        // NEW LOGIC: Verify the user is actually present before triggering the announcement
                        def name = settings["depName_${i}"] ?: "User ${i}"
                        def pSensor = settings["depPresence_${i}"]
                        
                        if (pSensor && pSensor.currentValue("presence") != "present") {
                            logDebug("Departure conditions met for ${name}, but they are currently away. Skipping announcement.")
                            continue // Skip to the next user
                        }

                        state[stateKey] = true // Mark as announced today
                        
                        def candidates = []
                        for (int j = 1; j <= 5; j++) {
                            def f = settings["depFile_${i}_${j}"]
                            if (f && f.trim() != "") candidates << f.trim()
                        }
                        
                        if (candidates.size() > 0) {
                            def selectedFile = candidates[new Random().nextInt(candidates.size())]
                            logAction("👋 ${name} departure conditions met. Playing announcement on Outside/Doorbell Speakers.")
                            
                            def speakerList = settings.doorbellAudioDevices
                            if (speakerList) {
                                processAnnouncement("${name} Departure", selectedFile, "Smart Departure", false, speakerList, false)
                            } else {
                                log.warn "Departure triggered for ${name}, but no Front Door / Outdoor Speakers configured in Section 1."
                            }
                        } else {
                            log.warn "Departure conditions met for ${name}, but no audio files are configured."
                        }
                    }
                }
            }
        }
    }
}

def testDeparture(userNum) {
    def name = settings["depName_${userNum}"] ?: "User ${userNum}"
    def candidates = []
    
    for (int j = 1; j <= 5; j++) {
        def f = settings["depFile_${userNum}_${j}"]
        if (f && f.trim() != "") candidates << f.trim()
    }
    
    if (candidates.size() > 0) {
        def selectedFile = candidates[new Random().nextInt(candidates.size())]
        def speakerList = settings.doorbellAudioDevices
        if (speakerList) {
            processAnnouncement("${name} Departure", selectedFile, "Test: Smart Departure", true, speakerList, false)
        } else {
            log.warn "No Front Door / Outdoor Speakers configured to test departure."
        }
    } else {
        log.warn "Missing audio file configuration for Departure User ${userNum}."
    }
}

// --- Section 10: Smart Bedtime Engine ---

def bedtimeSwitchHandler(evt) {
    if (isCategoryMuted("Bedtime Alerts")) return 

    // --- MODE CHECK ---
    if (settings.bedtimeModes && !settings.bedtimeModes.contains(location.mode)) {
        logDebug("Bedtime announcement skipped: Current mode (${location.mode}) is not permitted.")
        return
    }

    def devId = evt.device.id
    for (int i = 1; i <= 3; i++) {
        if (settings["bedtimeSwitch_${i}"]?.id == devId) {
            def name = settings["bedtimeName_${i}"] ?: "User ${i}"
            def file = settings["bedtimeFile_${i}"]
            if (file) {
                logAction("🛌 Early bedtime recommendation triggered for ${name}. Adding to queue.")
                def q = state.bedtimeQueue ?: []
                q << [userNum: i, name: name, file: file]
                state.bedtimeQueue = q
                
                if (!state.bedtimeProcessing) {
                    state.bedtimeProcessing = true
                    processBedtimeQueue()
                }
            } else {
                log.warn "Bedtime recommendation for ${name} triggered, but no audio file is configured."
            }
        }
    }
}

def processBedtimeQueue() {
    def q = state.bedtimeQueue ?: []
    if (q.size() > 0) {
        def item = q.remove(0)
        state.bedtimeQueue = q
        
        def speakerList = settings.commonAudioDevices
        def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
        
        if (speakerList) {
            logAction("🔊 Playing Bedtime announcement for ${item.name} on Common Speakers.")
            processAnnouncement("${item.name} Bedtime Alert", item.file.trim(), "Early Bedtime Alert", false, speakerList, false, customVol)
        } else {
            log.warn "Bedtime queue processing, but no Common Speakers configured in Section 1."
        }
        
        if (q.size() > 0) {
            runIn(15, "processBedtimeQueue") // Hold for 15 seconds before playing the next queued item
        } else {
            state.bedtimeProcessing = false
        }
    } else {
        state.bedtimeProcessing = false
    }
}

def testBedtime(userNum) {
    def name = settings["bedtimeName_${userNum}"] ?: "User ${userNum}"
    def file = settings["bedtimeFile_${userNum}"]
    def speakerList = settings.commonAudioDevices
    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
    
    if (file && speakerList) {
        processAnnouncement("${name} Bedtime Alert", file.trim(), "Test: Early Bedtime Alert", true, speakerList, false, customVol)
    } else {
        log.warn "Missing audio file or Common Speaker configuration to test Bedtime for User ${userNum}."
    }
}

// --- Section 9: Safe Workouts Engine ---

def safeWorkoutStartHandler(evt) {
    handleSafeWorkout(evt, "start")
}

def safeWorkoutEndHandler(evt) {
    handleSafeWorkout(evt, "end")
}

def handleSafeWorkout(evt, type) {
    if (isCategoryMuted("Safe Workouts")) return 

    if (settings.safeWorkoutModes && !settings.safeWorkoutModes.contains(location.mode)) {
        logDebug("Safe workout event ignored: Current mode (${location.mode}) is not permitted.")
        return
    }
    
    def devId = evt.device.id
    def userNum = 0
    
    for (int i = 1; i <= 3; i++) {
        def sw = type == "start" ? settings["swStartSwitch_${i}"] : settings["swEndSwitch_${i}"]
        if (sw?.id == devId) {
            userNum = i
            break
        }
    }
    
    if (userNum == 0) return
    
    def presenceDev = settings["swPresence_${userNum}"]
    def userName = settings["swUser${userNum}_Name"] ?: "User ${userNum}"
    
    // If the user is present (NOT in away mode), silently abort
    if (presenceDev && presenceDev.currentValue("presence") == "present") {
        logAction("🛑 Safe workout announcement skipped: ${userName} is currently present (Not in away mode).")
        return
    } else if (!presenceDev) {
        log.warn "Safe workout announcement skipped: No presence sensor configured for ${userName}."
        return
    }
    
    def fileName = type == "start" ? settings["swStartFile_${userNum}"] : settings["swEndFile_${userNum}"]
    def actionStr = type == "start" ? "Started" : "Ended"
    
    if (fileName && settings.commonAudioDevices) {
        logAction("🏃 ${userName} is away and ${actionStr.toLowerCase()} a workout. Playing announcement on Common Speakers.")
        
        // This array enforces strict playback solely on the designated Common Speakers. 
        def speakerList = [settings.commonAudioDevices].flatten().findAll { it }
        def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
        
        processAnnouncement("${userName} Workout ${actionStr}", fileName.trim(), "Safe Workout", false, speakerList, false, customVol)
    } else {
        log.warn "Safe workout event for User ${userNum}, but no audio file or Common Speaker is configured."
    }
}

def testSafeWorkout(userNum, type) {
    def fileName = type == "start" ? settings["swStartFile_${userNum}"] : settings["swEndFile_${userNum}"]
    def userName = settings["swUser${userNum}_Name"] ?: "User ${userNum}"
    def eventName = type == "start" ? "${userName} Workout Started" : "${userName} Workout Ended"
    def speakerList = settings.commonAudioDevices
    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
    
    if (fileName && speakerList) {
        processAnnouncement(eventName, fileName.trim(), "Test: Safe Workout", true, speakerList, false, customVol)
    } else {
        log.warn "Missing audio file or Common Speaker configuration for Safe Workout User ${userNum}."
    }
}

// --- Section 8: Calendar Announcement Engine ---

def calendar1hrHandler(evt) {
    logAction("📅 1 Hour Calendar Switch Triggered.")
    playCalendarAnnouncement("1hr")
}

def calendar5mHandler(evt) {
    logAction("📅 5 Minute Calendar Switch Triggered.")
    playCalendarAnnouncement("5m")
}

def playCalendarAnnouncement(type) {
    if (isCategoryMuted("Calendar Events")) return 

    // --- MODE CHECK ---
    if (settings.calendarModes && !settings.calendarModes.contains(location.mode)) {
        logDebug("Calendar announcement skipped: Current mode (${location.mode}) is not permitted.")
        return
    }

    def fileName = type == "1hr" ? settings.calendarFile_1hr : settings.calendarFile_5m
    def eventName = type == "1hr" ? "1 Hour Calendar Warning" : "5 Minute Calendar Warning"
    def speakerList = settings.commonAudioDevices
    def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
    
    if (fileName && speakerList) {
        processAnnouncement(eventName, fileName.trim(), "Calendar Event", false, speakerList, false, customVol)
    } else {
        log.warn "Calendar warning triggered for ${type}, but missing audio file or Common Speakers configuration."
    }
}

// --- Section 7: Simple Presence Arrival Engine ---

def simplePresenceArrivalHandler(evt) {
    if (evt.value != "present") return
    if (isCategoryMuted("Simple Arrivals")) return 
    
    if (settings.simpleArrivalModes && !settings.simpleArrivalModes.contains(location.mode)) {
        logDebug("Simple presence arrival ignored: Current mode (${location.mode}) is not permitted.")
        return
    }
    
    def devId = evt.device.id
    def userNum = 0
    for (int i = 1; i <= 3; i++) {
        if (settings["simplePresence_${i}"]?.id == devId) {
            userNum = i
            break
        }
    }
    
    if (userNum == 0) return
    
    def cdKey = "simple_arrival_${userNum}"
    if (isOnCooldown(cdKey, 5 * 60 * 1000)) { // 5-minute cooldown
        logDebug("Simple presence arrival for User ${userNum} ignored (Cooldown Active).")
        return
    }
    
    setCooldown(cdKey)
    def fileName = settings["simpleArrivalFile_${userNum}"]
    
    if (fileName && settings.commonAudioDevices) {
        logAction("📍 Presence sensor for User ${userNum} arrived. Playing localized arrival file on Common Speakers.")
        def speakerList = [settings.commonAudioDevices].flatten().findAll { it }
        def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
        processAnnouncement("User ${userNum} Arrived", fileName.trim(), "Presence Arrival", false, speakerList, false, customVol)
    } else {
        log.warn "Presence sensor arrived for User ${userNum}, but no audio file or Common Speaker is configured."
    }
}

// --- Section 6: Lock Arrival Tracking Engine ---

def testUserArrival(userNum, isMissed) {
    def userName = settings["user${userNum}_Name"] ?: "User ${userNum}"
    
    if (isMissed) {
        def file = settings["user${userNum}_missed"]
        def targetDevices = settings.commonAudioDevices ?: settings.arrivalAudioDevices ?: settings.doorbellAudioDevices
        def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
        
        if (file && file.trim() != "") {
            processAnnouncement("${userName} Missed Arrival", file.trim(), "Test: Missed Arrival", true, targetDevices, false, customVol)
        } else {
            log.warn "No missed arrival audio file configured for ${userName}."
        }
    } else {
        def candidates = []
        for (int i = 1; i <= 5; i++) {
            def file = settings["user${userNum}_greeting_${i}"]
            if (file && file.trim() != "") candidates << file.trim()
        }
        def targetDevices = settings.arrivalAudioDevices ?: settings.doorbellAudioDevices
        if (candidates.size() > 0) {
            def selectedFile = candidates[new Random().nextInt(candidates.size())]
            processAnnouncement("${userName} Arrival", selectedFile, "Test: Arrival", true, targetDevices, false)
        } else {
            log.warn "No direct arrival audio files configured for ${userName}."
        }
    }
}

def presenceHandler(evt) {
    def devId = evt.device.id
    def isPresent = (evt.value == "present")
    
    for (int u = 1; u <= 3; u++) {
        if (settings["user${u}_Presence"]?.id == devId) {
            handleUserStatusChange(u, isPresent, "Sensor")
        }
    }
}

def userSwitchHandler(evt) {
    def devId = evt.device.id
    for (int u = 1; u <= 3; u++) {
        def switches = settings["user${u}_Switches"]
        if (switches && switches.find { it.id == devId }) {
            handleUserStatusChange(u, true, "Switch")
        }
    }
}

def handleUserStatusChange(u, isHome, source) {
    def userName = settings["user${u}_Name"] ?: "User ${u}"
    def currentState = state["userPresence_${u}"]
    
    if (isHome) {
        if (currentState != "Home") {
            state["userPresence_${u}"] = "Home"
            state["userPresenceSource_${u}"] = "Sensor"
            state["userHomeTime_${u}"] = now()
            logAction("📍 ${userName} status changed to Home via ${source}. Starting 10-minute missed arrival timer.")
            runIn(600, "executeMissedArrival", [data: [userNum: u]])
            
            // Check for missed doorbell rings while the house was away
            triggerMissedDoorbellAlert()
        }
    } else {
        state["userPresence_${u}"] = "Away"
        state["userPresenceSource_${u}"] = "Departure"
        logAction("🚗 ${userName} has departed. Resetting arrival cooldowns and timers.")
        def cdKey = "arrival_user_${u}"
        def map = state.cooldownMap ?: [:]
        map.remove(cdKey)
        state.cooldownMap = map
    }
}

def modeChangeHandler(evt) {
    if (settings.awayModes?.contains(evt.value)) {
        logAction("House mode changed to Away (${evt.value}). Marking all users as Away.")
        for (int u = 1; u <= 3; u++) {
            state["userPresence_${u}"] = "Away"
            state["userPresenceSource_${u}"] = "Global Mode"
        }
    } else {
        // If the house is coming OUT of an away mode (e.g., Return Home mode)
        triggerMissedDoorbellAlert()
    }
}

def lockArrivalHandler(evt) {
    def codeName = ""
    
    if (evt.data) {
        try {
            def dataMap = new groovy.json.JsonSlurper().parseText(evt.data)
            if (dataMap?.codeName) {
                codeName = dataMap.codeName
            } else {
                def firstKey = dataMap.keySet().iterator().next()
                if (dataMap[firstKey]?.name) codeName = dataMap[firstKey].name
            }
        } catch (e) {
            logDebug("Could not parse lock event data JSON: ${e}")
        }
    }
    
    if (!codeName && evt.descriptionText) {
        for (int u = 1; u <= 3; u++) {
            def configuredCode = settings["user${u}_LockCode"]
            if (configuredCode && evt.descriptionText.contains(configuredCode)) {
                codeName = configuredCode
            }
        }
    }
    
    if (codeName) {
        for (int u = 1; u <= 3; u++) {
            if (settings["user${u}_LockCode"] == codeName) {
                triggerArrivalGreeting(u, "Lock Code (${codeName})")
            }
        }
    }
}

def triggerArrivalGreeting(userNum, source) {
    if (isCategoryMuted("Arrival Greetings")) return 

    def userName = settings["user${userNum}_Name"] ?: "User ${userNum}"
    
    def isAlreadyHome = (state["userPresence_${userNum}"] == "Home")
    def homeTime = state["userHomeTime_${userNum}"] ?: 0
    def recentlyArrived = (now() - homeTime) < (15 * 60 * 1000) 
    
    if (isAlreadyHome) {
        if (!recentlyArrived || state["userPresenceSource_${userNum}"] == "Lock Code") {
            logAction("🏠 ${userName} unlocked the door, but is already marked as Home. Skipping arrival greeting.")
            return
        }
    }
    
    state["userPresence_${userNum}"] = "Home"
    state["userPresenceSource_${userNum}"] = "Lock Code"
    state["userHomeTime_${userNum}"] = now()
    
    // Check for missed doorbell rings while the house was away
    triggerMissedDoorbellAlert()
    
    def cdKey = "arrival_user_${userNum}"
    def cdMins = settings.arrivalCooldown != null ? settings.arrivalCooldown.toInteger() : 15
    
    if (isOnCooldown(cdKey, cdMins * 60 * 1000)) {
        logDebug("Arrival greeting for User ${userNum} ignored (Cooldown active).")
        return
    }
    
    setCooldown(cdKey)
    
    def candidates = []
    for (int i = 1; i <= 5; i++) {
        def file = settings["user${userNum}_greeting_${i}"]
        if (file && file.trim() != "") {
            candidates << file.trim()
        }
    }
    
    def targetDevices = settings.arrivalAudioDevices ?: settings.doorbellAudioDevices
    
    if (candidates.size() > 0) {
        def selectedFile = candidates[new Random().nextInt(candidates.size())]
        logAction("🏠 ${userName} arrived via ${source}. Playing direct personalized greeting.")
        processAnnouncement("${userName} Arrival", selectedFile, "Arrival: ${userName}", true, targetDevices, false)
    } else {
        log.warn "${userName} arrived, but no local audio files are configured for their greeting."
    }
}

def executeMissedArrival(data) {
    if (isCategoryMuted("Arrival Greetings")) return 

    def userNum = data.userNum
    def cdKey = "arrival_user_${userNum}"
    def cdMins = settings.arrivalCooldown != null ? settings.arrivalCooldown.toInteger() : 15
    
    if (isOnCooldown(cdKey, cdMins * 60 * 1000)) {
        logDebug("Missed arrival check for User ${userNum} aborted. A lock code greeting recently played.")
        return
    }
    
    if (state["userPresence_${userNum}"] != "Home") {
        logDebug("Missed arrival check for User ${userNum} aborted. User is no longer Home.")
        return
    }
    
    def file = settings["user${userNum}_missed"]
    if (file && file.trim() != "") {
        def userName = settings["user${userNum}_Name"] ?: "User ${userNum}"
        def targetDevices = settings.commonAudioDevices ?: settings.arrivalAudioDevices ?: settings.doorbellAudioDevices
        def customVol = settings.commonVolume != null ? settings.commonVolume.toInteger() : null
        
        logAction("🏠 10 minutes passed without lock code for ${userName}. Playing Missed Arrival phrase on Common Speakers.")
        setCooldown(cdKey)
        processAnnouncement("${userName} Missed Arrival", file.trim(), "Missed Arrival: ${userName}", true, targetDevices, false, customVol)
    } else {
        log.warn "User ${userNum} triggered missed arrival, but no audio file is configured for it."
    }
}

// --- Secondary Logic Handlers ---

def exemptDoorHandler(evt) {
    state.lastExemptDoorOpenTime = now()
    logAction("🚪 Exempt door (${evt.device.displayName}) opened. Intruder alerts temporarily paused for 5 minutes.")
}

def roomSwitchHandler(evt) {
    def devId = evt.device.id
    def isNight = evt.value == "on"
    def roomNum = 0
    
    for (int r = 1; r <= 3; r++) {
        if (settings["roomSwitch_${r}"]?.id == devId) {
            roomNum = r
            break
        }
    }
    
    if (roomNum == 0) return
    
    if (isCategoryMuted("Room Greetings (GN/GM)")) return 
    
    def type = isNight ? "gn" : "gm"
    def logType = isNight ? "Good Night" : "Good Morning"
    
    logAction("Room ${roomNum} switch flipped to ${evt.value}. Queuing ${logType} announcement in 45 seconds.")
    runIn(45, "executeRoomAnnouncement", [data: [roomNum: roomNum, type: type]])
}

def executeRoomAnnouncement(data) {
    if (isCategoryMuted("Room Greetings (GN/GM)")) return 
    
    def roomNum = data.roomNum
    def type = data.type
    def logType = (type == "gn") ? "Good Night" : "Good Morning"
    
    def targetDevices = settings["roomAudio_${roomNum}"]
    if (!targetDevices) {
        logDebug("Room ${roomNum} ${logType} delayed execution aborted: No audio devices assigned.")
        return
    }

    def customVol = settings["room${roomNum}_${type}_vol"] ?: ((type == "gn") ? 30 : 50)
    
    // --- OCCUPANCY LOGIC ---
    def p1 = settings["room${roomNum}_u1_presence"]
    def p2 = settings["room${roomNum}_u2_presence"]
    def u1_home = p1 ? (p1.currentValue("presence") == "present") : false
    def u2_home = p2 ? (p2.currentValue("presence") == "present") : false
    
    def conditionPrefix = ""
    def contextMsg = ""
    
    if (u1_home && u2_home) {
        conditionPrefix = "room${roomNum}_both_${type}"
        contextMsg = "Both Users Home"
    } else if (u1_home && !u2_home) {
        conditionPrefix = "room${roomNum}_u1_${type}"
        contextMsg = "User 1 Only Home"
    } else if (!u1_home && u2_home) {
        conditionPrefix = "room${roomNum}_u2_${type}"
        contextMsg = "User 2 Only Home"
    }
    
    def candidates = []
    if (conditionPrefix != "") {
        for (int i = 1; i <= 3; i++) {
            def file = settings["${conditionPrefix}_${i}"]
            if (file && file.trim() != "") {
                candidates << file.trim()
            }
        }
    }
    
    def selectedFile = null
    if (candidates.size() > 0) {
        selectedFile = candidates[new Random().nextInt(candidates.size())]
    }

    // --- FALLBACK LOGIC ---
    if (!selectedFile) {
        candidates = []
        for (int i = 1; i <= 5; i++) {
            def file = settings["room${roomNum}_${type}_${i}"]
            if (file && file.trim() != "") {
                candidates << file.trim()
            }
        }
        if (candidates.size() > 0) {
            selectedFile = candidates[new Random().nextInt(candidates.size())]
            contextMsg = (u1_home || u2_home) ? "Fallback (Specific files missing)" : "Generic (No users home / unconfigured)"
        }
    }
    
    if (selectedFile) {
        logAction("🔊 Playing Room ${roomNum} ${logType} phrase at ${customVol}% [Context: ${contextMsg}].")
        
        // Pass true for forceOverride (4th param) to ensure manual room switches bypass global Quiet Modes
        processAnnouncement("Room ${roomNum} ${logType}", selectedFile, "Room ${roomNum} Switch", true, targetDevices, false, customVol)
    } else {
        log.warn "Room ${roomNum} switch triggered, but no local audio files configured for ${logType}."
    }
    
    // --- MORNING MOTION CHECK INJECTION ---
    if (type == "gm" && state.overnightMotionDetected && settings.enableMorningMotionAlert) {
        def targetRoomStr = settings.morningAlertRoom ?: "Room 1"
        if (targetRoomStr == "All Rooms" || targetRoomStr == "Room ${roomNum}") {
            logAction("Overnight motion was flagged. Scheduling camera check alert for Room ${roomNum} in 15 seconds.")
            runIn(15, "executeMorningMotionAlert", [data: [roomNum: roomNum]])
            state.overnightMotionDetected = false // Clear flag so it doesn't duplicate if other rooms wake up later
        }
    }
}

def doorbellHandler(evt) {
    if (isCategoryMuted("Doorbell & No Answer")) return 

    def devName = evt.device.displayName
    
    def cdKey = "db_${evt.device.id}"
    def cdSecs = settings.doorbellCooldown != null ? settings.doorbellCooldown.toInteger() : 15
    if (isOnCooldown(cdKey, cdSecs * 1000)) {
        logDebug("Doorbell event for ${devName} ignored (Cooldown Active).")
        return
    }
    
    def currentMode = location.mode
    def isQuietMode = settings.quietModes?.contains(currentMode)
    
    // Strict Killswitch: If it's Night/Quiet mode, nobody should be there. 
    // Ignore the doorbell entirely.
    if (isQuietMode) {
        logAction("Doorbell rung during Quiet Mode (${currentMode}). Ignoring completely (Zero people expected).")
        return
    }

    def isGreetingMode = settings.greetingModes?.contains(currentMode)
    def isAwayMode = settings.awayModes?.contains(currentMode)
    def dndActive = (settings.switchDND && settings.switchDND.currentValue("switch") == "on")

    if (!isGreetingMode && !isAwayMode && !dndActive) {
        logAction("Doorbell rung, but mode (${currentMode}) is not set for greetings or away messages. Ignoring.")
        return
    }

    // Since we already killed Quiet Mode above, DND here only affects daytime/evening hours
    if (isAwayMode || dndActive) {
        def logContext = isAwayMode ? "Immediate Away" : "DND Active"
        logAction("Doorbell rung while in ${logContext}. Immediately playing 'No Answer' prompt.")
        
        // --- MISSED DOORBELL INJECTION ---
        if (isAwayMode && settings.enableAwayDoorbellAlert) {
            state.missedAwayDoorbell = true
            logAction("Flagging missed doorbell for arrival announcement.")
        }
        
        def selected = getRandomAudioItem("noAnswer")
        if (selected) {
            setCooldown(cdKey)
            // Process the No Answer prompt normally
            processAnnouncement(selected.text, selected.file, "Doorbell: ${logContext}", false, doorbellAudioDevices, false)
        } else {
            log.warn "Doorbell triggered in ${logContext}, but no local audio file is linked for No Answer."
        }
        return
    }

    // Default to standard greeting mode behavior
    def selected = getRandomAudioItem("greeting")
    if (selected) {
        setCooldown(cdKey)
        processAnnouncement(selected.text, selected.file, "Doorbell: ${devName}", false, doorbellAudioDevices, false)
        
        if (settings.doorContacts) {
            state.waitingForDoorAnswer = true
            def delayMins = settings.noAnswerDelay != null ? settings.noAnswerDelay.toInteger() : 3
            def delaySecs = delayMins * 60
            runIn(delaySecs, "noAnswerTimeoutHandler")
            logAction("Doorbell rung. Waiting ${delayMins} minutes for the door to be answered.")
        }
    } else {
        log.warn "Doorbell triggered, but no local audio file is configured for Greetings."
    }
}

def doorAnsweredHandler(evt) {
    if (state.waitingForDoorAnswer) {
        state.waitingForDoorAnswer = false
        unschedule("noAnswerTimeoutHandler")
        logAction("Front door opened. Canceling 'No Answer' guest prompt.")
    }
}

def noAnswerTimeoutHandler() {
    if (isCategoryMuted("Doorbell & No Answer")) return 

    if (!state.waitingForDoorAnswer) return
    state.waitingForDoorAnswer = false
    
    logAction("Door was not answered in time. Firing 'No Answer' prompt.")
    def selected = getRandomAudioItem("noAnswer")
    if (selected) {
        processAnnouncement(selected.text, selected.file, "Doorbell Timeout", false, doorbellAudioDevices, false)
    }
}

def motionHandler(evt) {
    if (evt.value != "active") return
    if (isCategoryMuted("Intruder Alerts")) return 
    
    def type = settings.activationType ?: "Time Only"
    def timeValid = false
    def modeValid = false
    
    if (type == "Time Only" || type == "Both Time and Mode" || type == "Either Time or Mode") {
        if (settings.nightStartTime && settings.nightEndTime) {
            def startTime = timeToday(settings.nightStartTime, location.timeZone)
            def endTime = timeToday(settings.nightEndTime, location.timeZone)
            timeValid = timeOfDayIsBetween(startTime, endTime, new Date(), location.timeZone)
        }
    }
    
    if (type == "Mode Only" || type == "Both Time and Mode" || type == "Either Time or Mode") {
        if (settings.securityModes && settings.securityModes.contains(location.mode)) {
            modeValid = true
        }
    }
    
    def securityArmed = false
    if (type == "Time Only") securityArmed = timeValid
    else if (type == "Mode Only") securityArmed = modeValid
    else if (type == "Both Time and Mode") securityArmed = (timeValid && modeValid)
    else if (type == "Either Time or Mode") securityArmed = (timeValid || modeValid)
    
    if (!securityArmed) {
        logDebug("Motion event ignored. Parameters outside defined security profile matrix.")
        return
    }
    
    // EXEMPTION CHECK
    if (settings.exemptDoors) {
        def doorCurrentlyOpen = settings.exemptDoors.find { it.currentValue("contact") == "open" }
        def lastOpen = state.lastExemptDoorOpenTime ?: 0
        def recentlyOpened = (now() - lastOpen) < (5 * 60 * 1000)
        
        if (doorCurrentlyOpen || recentlyOpened) {
            logAction("🛡️ Intruder motion ignored: An exempt door is currently open or was opened within the last 5 minutes.")
            return
        }
    }
    
    def devName = evt.device.displayName
    def cdKey = "mo_${evt.device.id}"
    def cdSecs = settings.motionCooldown != null ? settings.motionCooldown.toInteger() : 60
    if (isOnCooldown(cdKey, cdSecs * 1000)) {
        logDebug("Intruder motion event for ${devName} ignored (Cooldown Active).")
        return
    }
    
    def selected = getRandomAudioItem("intruder")
    if (selected) {
        setCooldown(cdKey)
        processAnnouncement(selected.text, selected.file, "Intruder Motion: ${devName}", true, intruderAudioDevices, true)
    } else {
        log.warn "Intruder motion detected, but no local audio file is linked for Intruder Alerts."
    }
}

def dndHandler(evt) {
    evaluateRoutingMatrix()
    logAction("Do Not Disturb state changed to: ${evt.value.toUpperCase()}")
}

// --- Cooldown Tracking ---

def isOnCooldown(key, durationMs) {
    def map = state.cooldownMap ?: [:]
    def lastTime = map[key] ?: 0
    return (now() - lastTime) < durationMs
}

def setCooldown(key) {
    def map = state.cooldownMap ?: [:]
    map[key] = now()
    state.cooldownMap = map
}

// --- Local File Routing Helpers ---

String buildFullAudioUrl(String rawFile) {
    if (!rawFile) return ""
    rawFile = rawFile.trim()
    if (rawFile.startsWith("http://") || rawFile.startsWith("https://")) {
        return rawFile
    }
    def base = settings.localBaseUrl ?: "http://127.0.0.1:8080/local/"
    if (!base.endsWith("/")) base += "/"
    return base + rawFile
}

Map getRandomAudioItem(String type) {
    def candidates = []
    def phraseList = []
    def prefix = ""
    
    if (type == "greeting") {
        phraseList = getGreetingMessages()
        prefix = "greetingFile_"
    } else if (type == "noAnswer") {
        phraseList = getNoAnswerMessages()
        prefix = "noAnswerFile_"
    } else if (type == "intruder") {
        phraseList = getIntruderMessages()
        prefix = "intruderFile_"
    }
    
    for (int i = 0; i < phraseList.size(); i++) {
        def num = i + 1
        def configuredFile = settings["${prefix}${num}"]
        if (configuredFile && configuredFile.trim() != "") {
            candidates << [text: phraseList[i], file: configuredFile.trim()]
        }
    }
    
    if (candidates.size() == 0) {
        for (int i = 0; i < phraseList.size(); i++) {
            def num = i + 1
            def defaultName = "${type}_${num}.mp3"
            candidates << [text: phraseList[i], file: defaultName]
        }
    }
    
    if (candidates.size() > 0) {
        return candidates[new Random().nextInt(candidates.size())]
    }
    return null
}

// --- Core Routing Engine ---

def evaluateRoutingMatrix() {
    def diagList = []
    def allowAudio = true
    def reasonStr = ""
    def calculatedVol = settings.defaultVolume ?: 50
    
    if (settings.quietModes && settings.quietModes.contains(location.mode)) {
        allowAudio = false
        reasonStr = "Blocked by Quiet Mode (${location.mode})."
        diagList << [name: "Location Mode Filter", status: "HOLD", effect: "MUTE", desc: "Current mode (${location.mode}) is flagged as Quiet. Standard audio suppressed."]
    } else {
        diagList << [name: "Location Mode Filter", status: "ON", effect: "PASS", desc: "Current mode (${location.mode}) allows standard audio."]
    }
    
    def isNight = false
    def hour = new Date().format("H", location.timeZone).toInteger()
    if (hour >= 21 || hour < 7) isNight = true 
    
    if (isNight && settings.nightVolume) {
        calculatedVol = settings.nightVolume
        diagList << [name: "Dynamic Volume Engine", status: "ACTIVE", effect: "${calculatedVol}%", desc: "Nighttime hours detected. Volume reduced for standard tasks."]
    } else {
        calculatedVol = settings.defaultVolume ?: 50
        diagList << [name: "Dynamic Volume Engine", status: "ON", effect: "${calculatedVol}%", desc: "Standard daytime volume profile applied."]
    }

    state.currentCalculatedVolume = calculatedVol
    state.algoDiagnostics = diagList
    
    if (allowAudio) {
        state.confidenceReasoning = "Audio pipeline open. Standard announcements will play at ${calculatedVol}%."
    } else {
        state.confidenceReasoning = "Audio pipeline CLOSED for standard announcements. " + reasonStr
    }
    
    return [allow: allowAudio, volume: calculatedVol, reason: reasonStr]
}

def processAnnouncement(messageText, rawFileName, sourceName, forceOverride, targetDevices, isIntruder, customVol = null) {
    def devList = [targetDevices].flatten().findAll { it }
    
    if (!devList) {
        logAction("Error: Audio routed but no local audio devices are configured for this module.")
        return
    }

    def matrix = evaluateRoutingMatrix()
    
    if (!matrix.allow && !forceOverride) {
        logAction("🔕 Suppressed Message: '${messageText}' (${matrix.reason})")
        return
    }
    
    if (forceOverride && !matrix.allow) {
        logAction("🚨 OVERRIDE ACTIVE: Bypassing DND/Quiet Mode restrictions.")
    }
    
    def vol = isIntruder ? (settings.intruderVolume ?: 100) : (customVol != null ? customVol.toInteger() : matrix.volume)
    def fileUrl = buildFullAudioUrl(rawFileName)
    def chimeUrl = buildFullAudioUrl(settings.chimeFile)
    
    logAction("🗣️ Preparing Local Audio Sequence: (Source: ${sourceName})")
    
    state.lastMessage = messageText
    state.lastMessageTime = now()
    state.lastTriggerSource = sourceName
    
    // --- TV INTERRUPT LOGIC ---
    if (settings.mediaTVs) {
        logDebug("Muting Media TVs for incoming announcement.")
        settings.mediaTVs.each { tv ->
            if (tv.hasCommand("mute")) tv.mute()
        }
        
        // Schedule auto-unmute
        def unmuteDelay = settings.mediaMuteDelay != null ? settings.mediaMuteDelay.toInteger() : 8
        runIn(unmuteDelay, "unmuteMediaTVs")
    }
    
    try {
        devList.each { speaker ->
            if (chimeUrl != "") {
                logDebug("Playing Pre-Announcement Chime Track on ${speaker.displayName}: ${chimeUrl}")
                // HARDENED FIX: Use restore=true for the chime. Otherwise it forces playTrack() which instantly wipes the stream.
                playAudioOnDevice(speaker, chimeUrl, vol, true) 
            }
        }
        
        if (chimeUrl != "") {
            def delaySecs = settings.chimeDelay != null ? settings.chimeDelay.toInteger() : 2
            logDebug("Holding local audio thread for ${delaySecs} seconds for chime buffer...")
            pauseExecution(delaySecs * 1000)
        }
        
        devList.each { speaker ->
            logAction("🔊 Playing local MP3 on ${speaker.displayName}: '${fileUrl}' at ${vol}% volume.")
            playAudioOnDevice(speaker, fileUrl, vol, true) 
        }
    } catch (e) {
        log.error "Local audio execution error: ${e.message}"
    }
}

def unmuteMediaTVs() {
    if (settings.mediaTVs) {
        logDebug("Restoring audio for Media TVs.")
        settings.mediaTVs.each { tv ->
            if (tv.hasCommand("unmute")) tv.unmute()
        }
    }
}

def playAudioOnDevice(device, audioUrl, volume, restore = true) {
    if (!device || !audioUrl) return
    
    def vol = volume != null ? volume.toInteger() : 50
    
    try {
        def cmds = device.supportedCommands?.collect { it.name } ?: []
        
        // --- HARDENED SONOS & STREAM DETECTION ---
        // Streams (like Sonos Radio) often report as "playing_stream" instead of "playing". 
        // We look broadly at all status values to ensure we capture the stream.
        def cStatus = device.currentValue("status")?.toString()?.toLowerCase()
        def tStatus = device.currentValue("transportStatus")?.toString()?.toLowerCase()
        def pStatus = device.currentValue("playbackStatus")?.toString()?.toLowerCase()
        
        def isPlaying = [cStatus, tStatus, pStatus].any { it != null && (it.contains("playing") || it == "playing_stream") }
        def trackUri = device.currentValue("trackUri")?.toString()
        
        // Ensure we preserve the stream even if they paused their radio prior to the announcement
        def hasMedia = (trackUri != null && trackUri.trim() != "" && !trackUri.contains(audioUrl))
        def shouldRestore = restore && (isPlaying || hasMedia)

        if (shouldRestore && cmds.contains("playTrackAndRestore")) {
            logDebug("Hardened Audio: Native playTrackAndRestore on ${device.displayName} (isPlaying: ${isPlaying}, hasMedia: ${hasMedia})")
            
            // CRITICAL FIX: Do NOT call device.setVolume(vol) before native restore. 
            // It overwrites the device's original state, causing it to restore back to the loud announcement volume.
            try {
                device.playTrackAndRestore(audioUrl, vol)
            } catch (e1) {
                // Fallback if the driver's playTrackAndRestore doesn't support the volume parameter
                device.playTrackAndRestore(audioUrl)
            }
        } else {
            // It is safe to explicitly set the volume if we are NOT using the native restore function
            if (device.hasCommand("setVolume")) {
                device.setVolume(vol)
            }
            
            if (cmds.contains("playTrack")) {
                device.playTrack(audioUrl)
            } else if (cmds.contains("playSound")) {
                device.playSound(audioUrl)
            } else if (cmds.contains("speak")) {
                device.speak(audioUrl)
            } else {
                log.error "Device ${device.displayName} does not support standard audio track playback commands."
            }
        }
    } catch (e) {
        log.error "Failed to play audio track on ${device.displayName}: ${e}"
    }
}

// --- Voice Phrase Lists ---

def getGreetingMessages() {
    return [
        "Welcome. The residents have been notified of your arrival.",
        "Greetings. Please wait a moment while I announce you to the household.",
        "Hello. The doorbell has been rung, and someone will be with you shortly.",
        "Welcome to the residence. The homeowners are being alerted to your presence.",
        "Good day. Please hold while I inform the household that you are here.",
        "Greetings. Your arrival has been registered. Please wait.",
        "Hello. I have notified the family that you are waiting at the door.",
        "Welcome. Someone will attend to the door momentarily.",
        "Good day. Thank you for visiting; the residents have been notified.",
        "Hello there. Please give the homeowners a moment to answer the door."
    ]
}

def getNoAnswerMessages() {
    return [
        "I apologize, but the residents are currently unable to come to the door. Please note that audio and video are being recorded, so you may leave a message.",
        "Regrettably, no one is available to answer the door at this moment. You are being recorded, so please leave a message.",
        "I am sorry, but the household cannot receive guests right now. This system is recording, please leave your message at the door.",
        "Pardon me, but the homeowners are unable to attend to the door. Our security system is recording, so you may leave a message if you wish.",
        "My apologies, but there is no one available to answer at this time. Please leave a message, as your audio and video are currently being recorded.",
        "I beg your pardon, but the residents cannot come to the door. Please state your business, as this surveillance system is recording.",
        "Unfortunately, no one is able to receive you right now. You may leave a spoken message, as our recording system is active.",
        "I apologize for the inconvenience, but the household is unavailable. Please leave a message for the residents; you are being recorded.",
        "I am sorry, but we cannot answer the door at this time. Our perimeter cameras are recording, so please leave your message now.",
        "Regrettably, the residents are occupied. This area is under audio and video surveillance, so please feel free to leave a message."
    ]
}

def getIntruderMessages() {
    return [
        "Warning. Unauthorized motion detected. Authorities are being notified.",
        "Security alert. You are trespassing. Leave the premises immediately.",
        "Intruder detected. All security cameras are actively recording.",
        "Perimeter breach. Activating defensive security protocols.",
        "Warning. You are on private property. Private security has been dispatched.",
        "Motion sensor triggered in a restricted zone. Vacate now.",
        "Alert. Unauthorized presence detected on the grounds.",
        "Security system activated. Leave the area immediately to avoid prosecution.",
        "Warning. This property is under continuous surveillance.",
        "Trespassing detected. Law enforcement is en route to this location."
    ]
}

def getGoodNightMessages() {
    return [
        "Good night. Rest well.",
        "Sleep tight. The house is secure.",
        "Have a peaceful night's sleep.",
        "Good night. I will monitor the perimeter until morning.",
        "Sweet dreams. All systems are operating nominally."
    ]
}

def getGoodMorningMessages() {
    return [
        "Good morning. I hope you slept well.",
        "Rise and shine. It's a new day.",
        "Good morning. All overnight systems report clear.",
        "Welcome to a new day. The house is ready.",
        "Good morning. Have a wonderful day ahead."
    ]
}

// --- Utility ---

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
