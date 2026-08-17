/**
 * Advanced Game Room Manager 2.0
 *
 */
definition(
    name: "Advanced Game Room Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    oauth: true
)

preferences {
    page(name: "mainPage")
    page(name: "gamePage")
}

mappings {
    path("/log") { action: [GET: "serveScoreForm"] }
    path("/submit") { action: [GET: "handleScoreSubmit", POST: "handleScoreSubmit"] }
}

def mainPage() {
    if (!state.accessToken) {
        try {
            createAccessToken()
        } catch (e) {
            log.error "OAuth is not enabled. Please enable OAuth in the App Code page."
        }
    }
    
    ensureStateMaps()

    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
            
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Machine (Power)</th><th>Active Game / Table</th><th>#1 Overall</th><th>#1 Weekly</th></tr></thead>
                <tbody>
            """
            
            def numG = settings.numGames ?: 0
            if (numG > 0) {
                for (int i = 1; i <= (numG as Integer); i++) {
                    def gName = settings["gameName_${i}"] ?: "Machine ${i}"
                    def gSwitch = settings["gameSwitch_${i}"]
                    
                    def pwrState = "<span style='color: #7f8c8d; font-weight: normal; font-size: 12px;'>[OFF]</span>"
                    if (gSwitch) {
                        pwrState = gSwitch.currentValue("switch") == "on" ? "<span style='color: #27ae60; font-weight: bold; font-size: 12px;'>[ON]</span>" : "<span style='color: #7f8c8d; font-weight: normal; font-size: 12px;'>[OFF]</span>"
                    }
                    
                    def isMulti = settings["gameType_${i}"] == "Multi-Game (Arcade/Pinball)"
                    def mStats = state.gameStats["${i}"]
                    
                    def activeGame = gName
                    if (isMulti && mStats?.lastPlayed && mStats.lastPlayed != "Machine ${i}") {
                        activeGame = mStats.lastPlayed
                    }
                    
                    def gScores = mStats?.scores?."${activeGame}"
                    
                    def allList = gScores?.overall ?: []
                    def weekList = gScores?.weekly ?: []
                    
                    def allUser = allList.size() > 0 ? allList[0].user : "--"
                    def allScore = allList.size() > 0 ? allList[0].score : null
                    def allFmt = allScore != null ? "<b>${formatScore(allScore)}</b><br><span style='font-size: 11px; color: #555;'>by ${allUser}</span>" : "<span style='color: #aaa;'>No Data</span>"
                    
                    def weekUser = weekList.size() > 0 ? weekList[0].user : "--"
                    def weekScore = weekList.size() > 0 ? weekList[0].score : null
                    def weekFmt = weekScore != null ? "<b>${formatScore(weekScore)}</b><br><span style='font-size: 11px; color: #555;'>by ${weekUser}</span>" : "<span style='color: #aaa;'>No Data</span>"
                    
                    def displayGame = isMulti ? "<span style='color: #2980b9; font-weight: bold;'>${activeGame}</span>" : "<span style='color: #7f8c8d;'><i>(Single Game)</i></span>"
                    
                    dashHTML += "<tr><td class='dash-hl'>${gName} ${pwrState}</td><td>${displayGame}</td><td>${allFmt}</td><td>${weekFmt}</td></tr>"
                }
            } else {
                dashHTML += "<tr><td colspan='4' style='padding: 8px; text-align: center; color: #7f8c8d;'><i>No machines configured yet.</i></td></tr>"
            }
            dashHTML += "</tbody></table>"
            
            paragraph dashHTML
        }

        section("<b>1. External Web Logging (Player Portal)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides local and remote web links for players to log their high scores via their smartphones, view leaderboards, and read how-to guides.</div>"
            if (state.accessToken) {
                def localUrl = "${getFullLocalApiServerUrl()}/log?access_token=${state.accessToken}"
                def cloudUrl = "${getFullApiServerUrl()}/log?access_token=${state.accessToken}"
                
                paragraph "<b>Local Network Link:</b><br><a href='${localUrl}' target='_blank' style='font-size: 12px; word-break: break-all;'>${localUrl}</a>"
                paragraph "<b>Cloud/Remote Link:</b><br><a href='${cloudUrl}' target='_blank' style='font-size: 12px; word-break: break-all;'>${cloudUrl}</a>"
            } else {
                paragraph "<span style='color:red'><b>OAuth not enabled!</b> You must enable OAuth in the Hubitat App Code editor, then open this app again.</span>"
            }
        }

        section("<b>2. Weekly Chase Game (Room Triggers)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Monitors the room for occupancy and automatically announces a random 'Chase Game' for players to try and beat.</div>"
            
            if (state.currentChaseGame) {
                def cg = state.currentChaseGame
                paragraph "<div style='padding: 10px; background: #2c3e50; color: #ecf0f1; border-radius: 5px; border-left: 4px solid #f1c40f;'><b>🏆 Current Weekly Chase Game:</b> ${cg.gameName}<br><b>Score to Beat:</b> ${formatScore(cg.topScore)} (by ${cg.topUser})</div>"
            }
            
            input "occupiedSwitch", "capability.switch", title: "Game Room Occupied Switch", required: false
            
            def defChaseMsg = "Welcome to the Game Room! This week's Chase Game is %game%. The score to beat is %allScore% by %allUser%!"
            input "chaseGameMsg", "text", title: "Chase Game Announcement", defaultValue: defChaseMsg, required: true
            
            input "btnForceChase", "button", title: "🎲 Force Select New Chase Game", description: "Randomly pick a new Chase Game from the database right now."
        }

        section("<b>3. Global Audio Settings & Restrictions</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Controls the text-to-speech speaker routing, volume constraints, and mode-based operation restrictions.</div>"
            input "masterEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch", required: false, description: "If selected, the app will ONLY function when this switch is ON."
            input "guestModeSwitch", "capability.switch", title: "Guest Mode Switch", required: false, description: "When ON, standard 'Powering up' announcements and regular Chase Games are skipped. Instead, guests receive a welcoming greeting and one random challenge."
            input "requireInternet", "bool", title: "Require Internet for TTS?", defaultValue: true, description: "Runs a ping before speaking. If offline, it silently drops the audio to prevent Hubitat log errors."
            
            input "ttsSpeaker", "capability.speechSynthesis", title: "Game Room Speaker(s)", multiple: true, required: true
            input "ttsVolume", "number", title: "Announcement Default Volume (0-100)", required: false
            input "bootDelay", "number", title: "Announcement Boot Delay (seconds)", defaultValue: 45, required: true, description: "Delays initial announcements to allow the speaker to power up and connect to Wi-Fi."
            input "enableWakeupPad", "bool", title: "Enable Speaker Wake-Up Padding?", defaultValue: false
            
            paragraph "<b>Mode Restrictions</b>"
            input "allowedModes", "mode", title: "Allowed House Modes", multiple: true, required: false, description: "Only allow TTS announcements if the house is in one of these modes."
        }
        
        section("<b>4. Web Portal: How-To Guides</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Create custom instructional guides (e.g., 'How to play GunCon games') that will appear on a dedicated tab in the player web portal.</div>"
            input "numGuides", "number", title: "Number of Guides (0-10)", required: true, defaultValue: 0, range: "0..10", submitOnChange: true
            
            if ((settings.numGuides ?: 0) > 0) {
                for (int i = 1; i <= (settings.numGuides as Integer); i++) {
                    input "guideTitle_${i}", "text", title: "Guide ${i} Title", required: true, defaultValue: "How to Play..."
                    input "guideText_${i}", "textarea", title: "Guide ${i} Content", required: true, description: "Type your instructions here..."
                }
            }
        }

        section("<b>5. Machine Inventory Configuration</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Configures the number of arcades/tables in your game room to generate dedicated settings pages for each.</div>"
            input "numGames", "number", title: "Number of Machines (1-20)", required: true, defaultValue: 1, range: "1..20", submitOnChange: true
        }

        if ((settings.numGames ?: 0) > 0) {
            for (int i = 1; i <= (settings.numGames as Integer); i++) {
                section("<b>⚙️ ${settings["gameName_${i}"] ?: "Machine ${i}"}</b>", hideable: true, hidden: true) { 
                    href(name: "gameHref${i}", page: "gamePage", params: [gameNum: i], title: "Configure ${settings["gameName_${i}"] ?: "Machine ${i}"}") 
                }
            }
        }
        
        section("<b>6. Automated Weekly Resets</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Automatically flushes the 'Weekly Top 3' scores from the database at a designated time to keep the leaderboards fresh.</div>"
            input "enableAutoReset", "bool", title: "Enable Automated Weekly Reset?", defaultValue: false, submitOnChange: true
            if (enableAutoReset) {
                input "resetDay", "enum", title: "Reset Day", options: ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"], defaultValue: "SUN", required: true
                input "resetTime", "time", title: "Reset Time", required: true
            }
        }

        section("<b>7. Recent Action History & Logs</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.historyLog && state.historyLog.size() > 0) {
                def histHtml = "<div style='max-height: 250px; overflow-y: auto; background-color: #f4f4f4; border: 1px solid #ccc; padding: 10px; font-family: monospace; font-size: 12px; line-height: 1.4;'>"
                state.historyLog.each { logEntry ->
                    histHtml += "<div style='margin-bottom: 6px; border-bottom: 1px dashed #ddd; padding-bottom: 6px;'>${logEntry}</div>"
                }
                histHtml += "</div>"
                paragraph histHtml
            } else {
                paragraph "<i>No history logged yet.</i>"
            }
            input "btnResetWeekly", "button", title: "⚠️ Force Reset ALL Weekly Scores Now"
            input "btnResetHistory", "button", title: "Clear Action History"
        }
    }
}

def gamePage(params) {
    def gNum = params?.gameNum ?: state.currentGame ?: 1
    state.currentGame = gNum
    
    dynamicPage(name: "gamePage", title: "Machine Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Hardware & Identification") {
            input "gameName_${gNum}", "text", title: "Machine Name", defaultValue: "Arcade Cabinet", required: true, submitOnChange: true
            input "gameType_${gNum}", "enum", title: "Machine Type", options: ["Single Game (e.g. Pop A Shot)", "Multi-Game (Arcade/Pinball)"], defaultValue: "Single Game (e.g. Pop A Shot)", required: true, submitOnChange: true
            input "gameSwitch_${gNum}", "capability.switch", title: "Power Switch / Trigger", required: true
        }
        
        section("Database Management") {
            input "btnWipeMachine_${gNum}", "button", title: "🗑️ Wipe Database for ${settings["gameName_${gNum}"] ?: "This Machine"}"
        }
        
        section("Custom Announcement") {
            paragraph "<i>Variables:</i><br>• <b>%machine%</b> - Hardware Name<br>• <b>%game%</b> - Specific Game Name<br>• <b>%allUser%</b> - #1 Overall Champ<br>• <b>%allScore%</b> - #1 Overall Score<br>• <b>%weekUser%</b> - #1 Weekly Champ<br>• <b>%weekScore%</b> - #1 Weekly Score"
            
            def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
            def defaultMsg = isMulti ? 
                "The last game played on %machine% was %game%. The overall high score is %allScore%, held by %allUser%. This week's current leader is %weekUser% with a score of %weekScore%." : 
                "The overall high score on %machine% is %allScore%, held by %allUser%. This week's current leader is %weekUser% with a score of %weekScore%."
            
            input "customMsg_${gNum}", "text", title: "TTS Announcement String", required: true, defaultValue: defaultMsg
            input "btnTestGame_${gNum}", "button", title: "▶️ Test Announcement Audio"
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() {
    logInfo("Game Room Manager Installed.")
    initialize()
}

def updated() {
    logInfo("Game Room Manager Updated.")
    unsubscribe()
    unschedule()
    
    def numG = settings.numGames ?: 0
    if (numG > 0 && state.gameStats) {
        for (int i = 1; i <= (numG as Integer); i++) {
            def gName = settings["gameName_${i}"] ?: "Machine ${i}"
            def mStats = state.gameStats["${i}"]
            
            if (mStats) {
                if (mStats.lastPlayed == "Machine ${i}") mStats.lastPlayed = gName
                if (mStats.scores && mStats.scores["Machine ${i}"] && gName != "Machine ${i}") {
                    mStats.scores[gName] = mStats.scores["Machine ${i}"]
                    mStats.scores.remove("Machine ${i}")
                }
            }
        }
    }
    
    initialize()
}

def ensureStateMaps() {
    if (state.historyLog == null) state.historyLog = []
    if (atomicState.ttsQueue == null) atomicState.ttsQueue = []
    if (atomicState.isSpeaking == null) atomicState.isSpeaking = false
    if (state.gameStats == null) state.gameStats = [:]
}

def initialize() {
    ensureStateMaps()
    
    if (occupiedSwitch) {
        subscribe(occupiedSwitch, "switch.on", occupiedOnHandler)
    }
    
    def numG = settings.numGames ?: 0
    if (numG > 0) {
        for (int i = 1; i <= (numG as Integer); i++) {
            def sw = settings["gameSwitch_${i}"]
            if (sw) subscribe(sw, "switch.on", switchOnHandler)
            
            if (!state.gameStats["${i}"]) {
                state.gameStats["${i}"] = [lastPlayed: null, scores: [:]]
            }
        }
    }
    
    if (settings.enableAutoReset && settings.resetTime && settings.resetDay) {
        try {
            def scheduleTime = toDateTime(settings.resetTime)
            def h = scheduleTime.hours
            def m = scheduleTime.minutes
            def cronStr = "0 ${m} ${h} ? * ${settings.resetDay}"
            schedule(cronStr, "clearWeeklyScores")
            logInfo("Automated Weekly Reset Scheduled for ${settings.resetDay} at ${h}:${m}")
        } catch (e) {
            log.error "Failed to schedule automated reset: ${e}"
        }
    }
    logAction("App Initialized. Event engine ready.")
}

String getHumanReadableStatus() {
    if (settings.masterEnableSwitch && settings.masterEnableSwitch.currentValue("switch") == "off") {
        return "<span style='color:red;'><b>Disabled:</b></span> The application is disabled via the Master Switch."
    }
    if (settings.allowedModes && !(settings.allowedModes as List).contains(location.mode)) {
        return "<span style='color:orange;'><b>Disabled by Mode:</b></span> The current location mode is not selected in 'Allowed Modes'."
    }
    if (settings.guestModeSwitch && settings.guestModeSwitch.currentValue("switch") == "on") {
        return "<span style='color:blue;'><b>Guest Mode Active:</b></span> Standard power-up announcements are suppressed. Guests will receive custom greetings and high-score challenges."
    }
    
    def onCount = 0
    def numG = settings.numGames ?: 0
    for (int i = 1; i <= (numG as Integer); i++) {
        if (settings["gameSwitch_${i}"]?.currentValue("switch") == "on") onCount++
    }
    
    return "<span style='color:green;'><b>Operating Normally:</b></span> Monitoring ${numG} machine configurations. (${onCount} currently powered ON)."
}

// --- UTILITIES & NUMBER FORMATTING ---
def formatScore(val) {
    if (val == null) return "0"
    try {
        return String.format("%,d", val as Long)
    } catch(e) {
        return val.toString()
    }
}

def checkInternetStatus() {
    def isOnline = false
    try {
        httpGet([uri: "http://clients3.google.com/generate_204", timeout: 2]) { resp ->
            isOnline = true
        }
    } catch (e) {
        isOnline = false
    }
    return isOnline
}

def updateTop3(list, user, score) {
    if (!list) list = []
    list << [user: user, score: score as Long]
    list = list.sort { a, b -> (b.score as Long) <=> (a.score as Long) }
    
    def uniqueUsers = []
    def result = []
    list.each { entry ->
        if (!uniqueUsers.contains(entry.user)) {
            uniqueUsers << entry.user
            result << entry
        }
    }
    return result.take(3)
}

// --- CHASE GAME & GUEST MODE LOGIC ---
def selectNewChaseGame() {
    def eligibleGames = []
    state.gameStats?.each { gNum, mStats ->
        def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
        def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
        
        mStats.scores?.each { gameName, gScores ->
            if (gScores.overall && gScores.overall.size() > 0) {
                def displayName = isMulti ? gameName : mName
                eligibleGames << [gNum: gNum, gameName: displayName, topScore: gScores.overall[0].score as Long, topUser: gScores.overall[0].user]
            }
        }
    }
    
    if (eligibleGames.size() > 0) {
        def pick = eligibleGames[new Random().nextInt(eligibleGames.size())]
        state.currentChaseGame = pick
        state.chaseWeekOfYear = Calendar.getInstance(location.timeZone).get(Calendar.WEEK_OF_YEAR)
        logAction("SYSTEM: Selected new Weekly Chase Game: ${pick.gameName}")
    } else {
        logInfo("Could not select Chase Game - no games have overall scores logged yet.")
        state.currentChaseGame = null
    }
}

def occupiedOnHandler(evt) {
    if (settings.masterEnableSwitch && settings.masterEnableSwitch.currentValue("switch") != "on") return
    
    if (settings.allowedModes) {
        def modes = [settings.allowedModes].flatten().findAll{it}
        if (!modes.contains(location.mode)) return
    }

    def d = settings.bootDelay != null ? settings.bootDelay as Integer : 45

    if (settings.guestModeSwitch && settings.guestModeSwitch.currentValue("switch") == "on") {
        log.debug "Guest Mode is ON. Waiting ${d} seconds to verify occupancy before Guest Greeting."
        runIn(d, "executeGuestModeGreeting")
        return
    }
    
    log.debug "Room occupancy triggered. Waiting ${d} seconds to verify before Chase Game announcement."
    runIn(d, "executeOccupiedChaseGame")
}

def executeGuestModeGreeting() {
    def d = settings.bootDelay != null ? settings.bootDelay as Integer : 45
    // Validation: Ensure the switch hasn't turned back off during the delay
    if (occupiedSwitch && occupiedSwitch.currentValue("switch") != "on") {
        log.debug "Room occupancy turned off before ${d} seconds. Aborting guest greeting."
        return
    }

    ensureStateMaps()
    
    def greeting = "Welcome guests! Please enjoy your time in the game room and take care of the machines and tables. "
    
    def eligibleGames = []
    state.gameStats?.each { gNum, mStats ->
        def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
        def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"

        mStats.scores?.each { gameName, gScores ->
            if (gScores.overall && gScores.overall.size() > 0) {
                def displayName = isMulti ? gameName : mName
                eligibleGames << [gNum: gNum, gameName: displayName, topScore: gScores.overall[0].score as Long, topUser: gScores.overall[0].user]
            }
        }
    }

    def randomGameMsg = ""
    if (eligibleGames.size() > 0) {
        def pick = eligibleGames[new Random().nextInt(eligibleGames.size())]
        randomGameMsg = "If you are looking for a challenge, the overall high score on ${pick.gameName} is ${formatScore(pick.topScore)}, held by ${pick.topUser}. Good luck!"
    } else {
        randomGameMsg = "Be the first to set a high score today. Have fun!"
    }

    def finalMsg = greeting + randomGameMsg
    logAction("GUEST MODE: Queuing guest greeting and random high score challenge.")

    addToQueue(finalMsg, true)
}

def executeOccupiedChaseGame() {
    def d = settings.bootDelay != null ? settings.bootDelay as Integer : 45
    // Validation: Ensure the switch hasn't turned back off during the delay
    if (occupiedSwitch && occupiedSwitch.currentValue("switch") != "on") {
        log.debug "Room occupancy turned off before ${d} seconds. Aborting chase game announcement."
        return
    }

    ensureStateMaps()
    def currentWeek = Calendar.getInstance(location.timeZone).get(Calendar.WEEK_OF_YEAR)
    
    if (!state.currentChaseGame || state.chaseWeekOfYear != currentWeek) {
        selectNewChaseGame()
    }
    
    if (state.currentChaseGame) {
        def cg = state.currentChaseGame
        def rawMsg = settings.chaseGameMsg ?: "Welcome to the Game Room! This week's Chase Game is %game%. The score to beat is %allScore% by %allUser%!"
        def finalMsg = rawMsg.replace("%game%", cg.gameName)
                             .replace("%allUser%", cg.topUser.toString())
                             .replace("%allScore%", formatScore(cg.topScore))
        
        logAction("CHASE GAME: Queuing Chase Game to FRONT of queue: ${cg.gameName}")
        
        addToQueue(finalMsg, true)
    }
}

// --- WEB ENDPOINT LOGIC (PLAYER PORTAL & LIVE LEADERBOARDS) ---
def serveScoreForm() {
    def optionsHtml = ""
    def multiGameMap = [:]
    
    def numG = settings.numGames ?: 0
    if (numG > 0) {
        for (int i = 1; i <= (numG as Integer); i++) {
            def gName = settings["gameName_${i}"] ?: "Machine ${i}"
            def isMulti = settings["gameType_${i}"] == "Multi-Game (Arcade/Pinball)"
            optionsHtml += "<option value='${i}'>${gName}</option>"
            multiGameMap["${i}"] = isMulti
        }
    } else {
        optionsHtml = "<option value=''>No Machines Configured</option>"
    }

    def multiMapJson = new groovy.json.JsonBuilder(multiGameMap).toString()

    // Build Weekly Chase Game HTML Display Block
    def chaseHtml = ""
    if (state.currentChaseGame) {
        def cg = state.currentChaseGame
        chaseHtml += """
        <div class="chase-card">
            <div class="chase-header">&#127942; WEEKLY CHASE GAME: ${cg.gameName}</div>
            <div class="chase-body">
        """
        
        def chaseWeeklyList = []
        state.gameStats?.each { gNum, mStats ->
            mStats.scores?.each { gameName, gScores ->
                def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
                def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
                def displayName = isMulti ? gameName : mName
                if (displayName == cg.gameName && gScores.weekly) {
                    chaseWeeklyList = gScores.weekly
                }
            }
        }
        
        if (chaseWeeklyList && chaseWeeklyList.size() > 0) {
            chaseWeeklyList.eachWithIndex { entry, idx ->
                def medal = idx == 0 ? "&#129351;" : (idx == 1 ? "&#129352;" : "&#129353;")
                chaseHtml += "<div class='leader-row'><span>${medal} ${entry.user}</span><span class='score-val'>${formatScore(entry.score)}</span></div>"
            }
        } else {
            chaseHtml += "<div class='no-records'>No weekly scores logged yet this cycle!</div>"
        }
        chaseHtml += "</div></div>"
    }

    // Build Full Database Machine & Game Breakdown Layout
    def leaderboardHtml = ""
    if (state.gameStats && state.gameStats.size() > 0) {
        state.gameStats.each { gNum, mStats ->
            def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
            leaderboardHtml += """
            <div class="machine-card">
                <div class="machine-title">&#127918; ${mName}</div>
            """
            if (mStats.scores && mStats.scores.size() > 0) {
                mStats.scores.each { gameName, gScores ->
                    leaderboardHtml += """
                    <div class="game-section">
                        <div class="game-title">&#128377; ${gameName}</div>
                        <div class="table-header">
                            <span>Overall Top 3</span>
                            <span>Weekly Top 3</span>
                        </div>
                        <div class="table-body-grid">
                            <div class="score-col">
                    """
                    if (gScores.overall && gScores.overall.size() > 0) {
                        gScores.overall.eachWithIndex { entry, idx ->
                            def medal = idx == 0 ? "&#129351;" : (idx == 1 ? "&#129352;" : "&#129353;")
                            leaderboardHtml += "<div class='leader-row-inline'><span>${medal} ${entry.user}</span><span class='score-val-overall'>${formatScore(entry.score)}</span></div>"
                        }
                    } else {
                        leaderboardHtml += "<div class='no-records'>No records</div>"
                    }
                    
                    leaderboardHtml += """
                            </div>
                            <div class="score-col">
                    """
                    if (gScores.weekly && gScores.weekly.size() > 0) {
                        gScores.weekly.eachWithIndex { entry, idx ->
                            def medal = idx == 0 ? "&#129351;" : (idx == 1 ? "&#129352;" : "&#129353;")
                            leaderboardHtml += "<div class='leader-row-inline'><span>${medal} ${entry.user}</span><span class='score-val-weekly'>${formatScore(entry.score)}</span></div>"
                        }
                    } else {
                        leaderboardHtml += "<div class='no-records'>No records</div>"
                    }
                    leaderboardHtml += """
                            </div>
                        </div>
                    </div>
                    """
                }
            } else {
                leaderboardHtml += "<div class='no-records' style='padding: 10px 0;'>No distinct games logged yet.</div>"
            }
            leaderboardHtml += "</div>"
        }
    } else {
        leaderboardHtml = "<div class='no-records' style='padding: 20px;'>Leaderboards are empty. Log a game score to initialize!</div>"
    }

    // Build How-To Guides HTML
    def howToHtml = ""
    def numGds = settings.numGuides ?: 0
    if (numGds > 0) {
        for(int i = 1; i <= (numGds as Integer); i++) {
            def gTitle = settings["guideTitle_${i}"] ?: "Guide ${i}"
            def gText = settings["guideText_${i}"] ?: ""
            howToHtml += """
            <div class="machine-card">
                <div class="machine-title" style="color: #f1c40f;">&#128214; ${gTitle}</div>
                <div style="font-size: 14px; text-align: left; line-height: 1.6; color: #dcdcdc; font-family: sans-serif;">
                    ${gText.replace('\n', '<br>')}
                </div>
            </div>
            """
        }
    } else {
        howToHtml = "<div class='no-records' style='padding: 20px;'>No guides available. Ask the host if you need help!</div>"
    }

    def html = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <title>Arcade Management Portal</title>
        <style>
            body { background: #121212; color: #e0e0e0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 15px; text-align: center; margin: 0; }
            .wrapper { max-width: 550px; margin: 0 auto; padding-bottom: 50px; }
            
            /* Tabs Styling */
            .tab { display: flex; overflow: hidden; background-color: #1e1e1e; border-radius: 8px 8px 0 0; border: 1px solid #333; border-bottom: none; }
            .tab button { background-color: inherit; color: #aaa; float: left; border: none; outline: none; cursor: pointer; padding: 12px 10px; transition: 0.3s; font-size: 13px; flex-grow: 1; font-weight: bold; text-transform: uppercase; }
            .tab button:hover { background-color: #2a2a2a; color: #fff; }
            .tab button.active { background-color: #27ae60; color: white; }
            .tabcontent { display: none; padding: 20px; border: 1px solid #333; border-radius: 0 0 8px 8px; background-color: #1e1e1e; text-align: center; margin-bottom: 25px; }
            
            h2 { margin-top: 0; color: #27ae60; text-transform: uppercase; letter-spacing: 2px; font-size: 18px; margin-bottom: 20px; }
            h3 { color: #f1c40f; text-transform: uppercase; margin: 10px 0 15px 0; font-size: 18px; letter-spacing: 1px; }
            label { display: block; text-align: left; margin-bottom: 5px; font-size: 13px; color: #aaa; font-weight: bold; }
            select, input[type='text'], input[type='number'] { width: 100%; padding: 12px; margin-bottom: 18px; border: 1px solid #444; border-radius: 6px; background: #2a2a2a; color: #fff; font-size: 16px; box-sizing: border-box; }
            select:focus, input:focus { border-color: #27ae60; outline: none; }
            input[type='submit'] { width: 100%; padding: 14px; background: #27ae60; color: #fff; border: none; border-radius: 6px; font-size: 16px; font-weight: bold; cursor: pointer; text-transform: uppercase; transition: background 0.2s; }
            input[type='submit']:hover { background: #219653; }
            #subGameContainer { display: none; margin-bottom: 0px; padding-top: 5px; border-top: 1px dashed #444; }
            
            /* Chase Card Styling */
            .chase-card { background: #2c3e50; border-radius: 12px; border-left: 5px solid #f1c40f; padding: 15px; margin-bottom: 25px; text-align: left; border: 1px solid #34495e; box-shadow: 0 4px 12px rgba(0,0,0,0.4); }
            .chase-header { font-weight: bold; color: #f1c40f; font-size: 14px; text-transform: uppercase; margin-bottom: 10px; letter-spacing: 0.5px; }
            .chase-body { display: flex; flex-direction: column; gap: 5px; background: rgba(0,0,0,0.2); padding: 10px; border-radius: 6px; }
            
            /* Global Leaderboards & Cards */
            .machine-card { background: #1a1a1a; border: 1px solid #333; border-radius: 12px; padding: 15px; margin-bottom: 20px; text-align: left; box-shadow: 0 4px 12px rgba(0,0,0,0.5); }
            .machine-title { font-size: 16px; font-weight: bold; color: #3498db; border-bottom: 2px solid #2a2a2a; padding-bottom: 8px; margin-bottom: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
            .game-section { background: #232323; border-radius: 8px; padding: 12px; margin-bottom: 12px; border: 1px solid #2d2d2d; }
            .game-title { font-size: 14px; font-weight: bold; color: #e74c3c; margin-bottom: 10px; border-bottom: 1px solid #333; padding-bottom: 4px; }
            
            .table-header { display: grid; grid-template-columns: 1fr 1fr; border-bottom: 1px solid #3a3a3a; padding-bottom: 4px; margin-bottom: 8px; font-size: 10px; text-transform: uppercase; color: #999; font-weight: bold; }
            .table-body-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }
            .score-col { display: flex; flex-direction: column; gap: 5px; }
            
            .leader-row, .leader-row-inline { display: flex; justify-content: space-between; font-size: 13px; line-height: 1.4; }
            .score-val { color: #f1c40f; font-weight: bold; }
            .score-val-overall { color: #2ecc71; font-weight: bold; }
            .score-val-weekly { color: #e67e22; font-weight: bold; }
            .no-records { font-size: 12px; color: #7f8c8d; font-style: italic; padding: 2px 0; }
        </style>
        <script>
            const multiMap = ${multiMapJson};
            function checkMachineType() {
                const sel = document.getElementById("gNum").value;
                const container = document.getElementById("subGameContainer");
                const subInput = document.getElementById("subGame");
                if (multiMap[sel]) {
                    container.style.display = "block";
                    subInput.required = true;
                } else {
                    container.style.display = "none";
                    subInput.required = false;
                    subInput.value = "";
                }
            }
            function openTab(evt, tabName) {
                var i, tabcontent, tablinks;
                tabcontent = document.getElementsByClassName("tabcontent");
                for (i = 0; i < tabcontent.length; i++) {
                    tabcontent[i].style.display = "none";
                }
                tablinks = document.getElementsByClassName("tablinks");
                for (i = 0; i < tablinks.length; i++) {
                    tablinks[i].className = tablinks[i].className.replace(" active", "");
                }
                document.getElementById(tabName).style.display = "block";
                evt.currentTarget.className += " active";
            }
            window.onload = function() {
                checkMachineType();
                document.getElementById("defaultOpen").click();
            };
        </script>
    </head>
    <body>
        <div class="wrapper">
            <div class="tab">
                <button class="tablinks" onclick="openTab(event, 'LogScore')" id="defaultOpen">&#128221; Log Score</button>
                <button class="tablinks" onclick="openTab(event, 'Leaderboards')">&#127942; Leaderboards</button>
                <button class="tablinks" onclick="openTab(event, 'HowTo')">&#128214; How To</button>
            </div>
            
            <div id="LogScore" class="tabcontent">
                <h2>Log High Score</h2>
                <form action="submit" method="GET">
                    <input type="hidden" name="access_token" value="${state.accessToken}" />
                    
                    <label for="gNum">Select Cabinet / Machine</label>
                    <select id="gNum" name="gNum" required onchange="checkMachineType()">
                        ${optionsHtml}
                    </select>
                    
                    <div id="subGameContainer">
                        <label for="subGame">Specific Game Title / Table</label>
                        <input type="text" id="subGame" name="subGame" placeholder="e.g., Pac-Man or Addams Family" autocomplete="off" />
                    </div>
                    
                    <label for="player">Player Initials / Name</label>
                    <input type="text" id="player" name="player" placeholder="Enter name" required autocomplete="off" />
                    
                    <label for="score">Final Score</label>
                    <input type="number" id="score" name="score" placeholder="0" required />
                    
                    <input type="submit" value="Submit Score to Room" />
                </form>
            </div>
            
            <div id="Leaderboards" class="tabcontent" style="background: transparent; border: none; padding: 0;">
                ${chaseHtml}
                <h3>Live Leaderboards</h3>
                ${leaderboardHtml}
            </div>
            
            <div id="HowTo" class="tabcontent" style="background: transparent; border: none; padding: 0;">
                <h3>&#128214; Game Room Guides</h3>
                ${howToHtml}
            </div>
        </div>
    </body>
    </html>
    """
    render contentType: "text/html", data: html, status: 200
}

def handleScoreSubmit() {
    def gNum = params.gNum?.toString()
    def subGameRaw = params.subGame?.trim()
    def player = params.player?.trim()
    def score = params.score != null ? params.score.toLong() : null
    def html = ""

    if (!gNum || !player || score == null) {
        html = generateResultHtml("Error", "Missing required fields.", false)
        render contentType: "text/html", data: html, status: 400
        return
    }

    def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
    def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
    def activeGameName = isMulti && subGameRaw ? subGameRaw : mName

    def mStats = state.gameStats[gNum] ?: [lastPlayed: activeGameName, scores: [:]]
    def gScores = mStats.scores[activeGameName] ?: [overall: [], weekly: []]

    def currentOverallTop = gScores.overall.size() > 0 ? (gScores.overall[0].score as Long) : 0L
    def currentWeeklyTop = gScores.weekly.size() > 0 ? (gScores.weekly[0].score as Long) : 0L

    def beatOverallTop = false
    def beatWeeklyTop = false
    def msgList = []

    if (score > currentOverallTop) {
        beatOverallTop = true
        msgList << "New #1 Overall High Score!"
    }
    
    if (score > currentWeeklyTop) {
        beatWeeklyTop = true
        if (!beatOverallTop) msgList << "New #1 Weekly High Score!"
    }

    gScores.overall = updateTop3(gScores.overall, player, score)
    gScores.weekly = updateTop3(gScores.weekly, player, score)

    mStats.lastPlayed = activeGameName
    mStats.scores[activeGameName] = gScores
    state.gameStats[gNum] = mStats

    if (beatOverallTop || beatWeeklyTop) {
        logAction("WEB PORTAL: [${mName} - ${activeGameName}] ${player} logged score of ${formatScore(score)}. (${msgList.join(', ')})")
        
        def cg = state.currentChaseGame
        def displayCGName = isMulti ? activeGameName : mName
        
        if (cg && cg.gameName == displayCGName && score > (cg.topScore as Long)) {
            cg.topScore = score
            cg.topUser = player
            state.currentChaseGame = cg
        }

        def sw = settings["gameSwitch_${gNum}"]
        if (sw && sw.currentValue("switch") == "on") {
            def alertMsg = "Alert! ${player} just logged a massive new score of ${formatScore(score)} on ${activeGameName}. ${msgList.join(' ')}"
            addToQueue(alertMsg, false)
        }

        html = generateResultHtml("Score Saved!", "Congratulations ${player}! Your score of ${formatScore(score)} on ${activeGameName} has been recorded.", true)
    } else {
        logAction("WEB PORTAL: [${mName} - ${activeGameName}] ${player} logged ${formatScore(score)}, placing in the Top 3 or lower.")
        html = generateResultHtml("Score Logged", "Good try ${player}, your score of ${formatScore(score)} has been saved to the database.", true)
    }

    render contentType: "text/html", data: html, status: 200
}

def generateResultHtml(title, message, success) {
    def color = success ? "#27ae60" : "#c0392b"
    return """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <title>${title}</title>
        <style>
            body { background: #121212; color: #e0e0e0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 20px; text-align: center; }
            .container { max-width: 400px; margin: 50px auto; background: #1e1e1e; padding: 30px 20px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.5); border: 1px solid #333; }
            h2 { margin-top: 0; color: ${color}; }
            p { font-size: 16px; color: #ccc; margin-bottom: 30px;}
            a { display: inline-block; padding: 12px 24px; background: #333; color: #fff; text-decoration: none; border-radius: 6px; font-weight: bold; border: 1px solid #555; transition: background 0.3s;}
            a:hover { background: #444; }
        </style>
    </head>
    <body>
        <div class="container">
            <h2>${title}</h2>
            <p>${message}</p>
            <a href="log?access_token=${state.accessToken}">Return to Leaderboards</a>
        </div>
    </body>
    </html>
    """
}

// --- CORE EVENT HANDLER ---
def switchOnHandler(evt) {
    if (settings.masterEnableSwitch && settings.masterEnableSwitch.currentValue("switch") != "on") return
    
    if (settings.guestModeSwitch && settings.guestModeSwitch.currentValue("switch") == "on") {
        log.debug "Guest Mode is ON. Suppressing individual machine power-up announcement."
        return
    }
    
    ensureStateMaps()
    def deviceId = evt.device.id
    
    if (settings.allowedModes) {
        def modes = [settings.allowedModes].flatten().findAll{it}
        if (!modes.contains(location.mode)) {
            log.debug "TTS Announcement blocked. Current mode '${location.mode}' is not in allowed modes."
            return
        }
    }
    
    def d = settings.bootDelay != null ? settings.bootDelay as Integer : 45
    def numG = settings.numGames ?: 0
    for (int i = 1; i <= (numG as Integer); i++) {
        if (settings["gameSwitch_${i}"]?.id == deviceId) {
            log.debug "Machine ${i} switch triggered. Waiting ${d} seconds to confirm..."
            // Overwrite set to false so multiple machines can be queued at once
            runIn(d, "delayedQueueAnnouncement", [overwrite: false, data: [gNum: i]])
            return
        }
    }
}

def delayedQueueAnnouncement(data) {
    if (data && data.gNum) {
        def d = settings.bootDelay != null ? settings.bootDelay as Integer : 45
        // Validation: Ensure the specific machine switch is still on
        def sw = settings["gameSwitch_${data.gNum}"]
        if (sw && sw.currentValue("switch") != "on") {
            log.debug "Machine ${data.gNum} switch turned off before ${d} seconds. Aborting announcement."
            return
        }
        queueAnnouncement(data.gNum as Integer)
    }
}

def queueAnnouncement(int gNum) {
    ensureStateMaps()
    
    def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
    def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
    def mStats = state.gameStats["${gNum}"]
    
    def activeGameName = mName
    if (isMulti && mStats?.lastPlayed && mStats.lastPlayed != "Machine ${gNum}") {
        activeGameName = mStats.lastPlayed
    }
    
    def gScores = mStats?.scores?."${activeGameName}"
    
    def allList = gScores?.overall ?: []
    def weekList = gScores?.weekly ?: []
    
    def allUser = allList.size() > 0 ? allList[0].user : "Nobody"
    def allScore = allList.size() > 0 ? allList[0].score : 0L
    def weekUser = weekList.size() > 0 ? weekList[0].user : "Nobody"
    def weekScore = weekList.size() > 0 ? weekList[0].score : 0L
    
    def defaultFallback = isMulti ? 
        "The last game played on %machine% was %game%. The overall high score is %allScore%, held by %allUser%. This week's current leader is %weekUser% with a score of %weekScore%." : 
        "The overall high score on %machine% is %allScore%, held by %allUser%. This week's current leader is %weekUser% with a score of %weekScore%."

    def rawMsg = settings["customMsg_${gNum}"] ?: defaultFallback
    
    def finalMsg = rawMsg.replace("%machine%", mName.toString())
                         .replace("%game%", activeGameName.toString())
                         .replace("%allUser%", allUser.toString())
                         .replace("%allScore%", formatScore(allScore))
                         .replace("%weekUser%", weekUser.toString())
                         .replace("%weekScore%", formatScore(weekScore))
    
    logAction("QUEUED: [${mName}] - '${finalMsg}'")
    
    addToQueue(finalMsg, false)
}

// --- TTS QUEUE ENGINE ---
def addToQueue(String msg, boolean front = false) {
    ensureStateMaps()
    def q = atomicState.ttsQueue ?: []
    
    if (front) {
        q.add(0, msg)
    } else {
        q.add(msg)
    }
    atomicState.ttsQueue = q
    
    // Centralized lock: Actively claims isSpeaking before executing 
    // to prevent concurrent triggers from talking over each other
    if (atomicState.isSpeaking != true) {
        atomicState.isSpeaking = true
        processQueue()
    }
}

def processQueue() {
    ensureStateMaps()
    def q = atomicState.ttsQueue ?: []
    
    if (q.size() > 0) {
        if (settings.requireInternet && !checkInternetStatus()) {
            log.warn "No internet connection detected. Emptying TTS queue to prevent errors."
            atomicState.ttsQueue = []
            atomicState.isSpeaking = false
            return
        }
        
        def msgToSpeak = q[0]
        q = q.drop(1)
        atomicState.ttsQueue = q
        
        speakMessage(msgToSpeak)
        
        def delay = Math.max(5, (msgToSpeak.length() / 10).toInteger() + 2)
        runIn(delay, "processQueue", [overwrite: true])
        
    } else {
        // Queue is totally empty, safely release the lock
        atomicState.isSpeaking = false
    }
}

def speakMessage(String msg) {
    if (!ttsSpeaker) return
    def speakers = ttsSpeaker instanceof List ? ttsSpeaker : [ttsSpeaker]
    
    def safeMsg = msg.replace("&", "and")
    def finalMsg = safeMsg
    if (settings.enableWakeupPad) {
        finalMsg = ", , , " + safeMsg
    }
    
    speakers.each { spk ->
        try {
            def currentVol = spk.currentValue("volume")
            def targetVol = settings.ttsVolume != null ? (settings.ttsVolume as Integer) : currentVol
            
            if (targetVol != null) {
                spk.setVolume(targetVol)
                pauseExecution(1500)
            }
            
            spk.speak(finalMsg)
            
            try {
                if (spk.hasCommand("play")) spk.play()
            } catch (ex) {}
            
        } catch (e) {
            log.error "Announcer TTS Error sending to ${spk.displayName}: ${e}"
        }
    }
}

// --- LOGGING & BUTTON ACTIONS ---
def logAction(msg) { 
    if(settings.txtEnable) log.info "${app.label}: ${msg}"
    def h = state.historyLog ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.historyLog = h 
}

def logInfo(msg) { 
    if(settings.txtEnable) log.info "${app.label}: ${msg}" 
}

def appButtonHandler(btn) {
    ensureStateMaps()
    
    if (btn == "btnRefresh") {
        logInfo("Dashboard Refresh Triggered.")
    } else if (btn == "btnForceChase") {
        selectNewChaseGame()
    } else if (btn.startsWith("btnTestGame_")) {
        def gNum = btn.split("_")[1].toInteger()
        queueAnnouncement(gNum)
    } else if (btn == "btnResetWeekly") {
        clearWeeklyScores()
    } else if (btn == "btnResetHistory") {
        state.historyLog = []
        logAction("Action logging history cleared.")
    } else if (btn.startsWith("btnWipeMachine_")) {
        def gNum = btn.split("_")[1]
        state.gameStats.remove(gNum)
        logAction("SYSTEM: Database completely wiped for Machine ${gNum}.")
    }
}

def clearWeeklyScores() {
    state.gameStats?.each { gNum, mStats ->
        mStats.scores?.each { gameName, gScores ->
            gScores.weekly = []
        }
    }
    logAction("SYSTEM: All Weekly Scores have been wiped via automated or manual reset.")
    selectNewChaseGame() 
}
