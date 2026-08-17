/**
 * Advanced Smart Messaging 2.0
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

definition(
    name: "Advanced Smart Messaging 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    oauth: [displayName: "ASM 2.0", displayLink: ""]
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #4f46e5;'>" +
                      "<b>Advanced Smart Messaging 2.0</b><br>100% Local. No tracking. Messages are automatically wiped every night at 3:00 AM.</div>"
        }

        section("<b>OAuth Setup</b>", hideable: true, hidden: false) {
            if (!state.accessToken) {
                createAccessToken()
                paragraph "Token created. Please click 'Done' to install, then reopen this app."
            } else {
                def localUri = getFullLocalApiServerUrl() + "/chat?access_token=${state.accessToken}"
                def cloudUri = getFullApiServerUrl() + "/chat?access_token=${state.accessToken}"
                
                href(title: "Open Local Messaging", url: localUri, style: "external", description: "Use on your home network.")
                href(title: "Open Cloud Messaging", url: cloudUri, style: "external", description: "Use when away from home.")
            }
        }

        section("<b>Notifications & Profile Routing</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Select the notification devices (e.g., Hubitat Mobile App) for each profile so they get pinged when receiving a direct or group message.</div>"
            input "notifyU1", "capability.notification", title: "Profile 1 Notification Device", multiple: true, required: false
            input "notifyU2", "capability.notification", title: "Profile 2 Notification Device", multiple: true, required: false
            input "notifyU3", "capability.notification", title: "Profile 3 Notification Device", multiple: true, required: false
            input "notifyU4", "capability.notification", title: "Profile 4 Notification Device", multiple: true, required: false
        }
        
        section("<b>Sonos & Room Announcements</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Link Sonos speakers and motion sensors to rooms. These rooms will appear in your chat dropdown. If the room is active, messaging the room will broadcast a TTS message directly to that speaker.</div>"
            
            input "allowedModes", "mode", title: "<b>Only allow TTS during these modes</b> (Leave blank for all)", multiple: true, required: false
            input "motionTimeout", "number", title: "<b>Motion Hold Time</b> (Minutes to keep room active after motion stops)", defaultValue: 5, required: false
            
            (1..4).each { i ->
                input "speaker${i}", "capability.speechSynthesis", title: "Room ${i} Speaker (Sonos)", required: false
                input "speaker${i}Name", "text", title: "Room ${i} Name (e.g. Kitchen)", required: false
                input "speaker${i}Motion", "capability.motionSensor", title: "Room ${i} Motion Sensor", required: false
                input "speaker${i}Vol", "number", title: "Room ${i} TTS Volume (1-100)", required: false, defaultValue: 50
            }
        }
        
        section("<b>Hub Variable Sync (Optional)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Syncs the mood selected in the chat app directly to a Hubitat Hub Variable (String) so other apps (like Iron AI or your Dashboard) can read it, and vice versa.</div>"
            input "moodVarU1", "hubVariable", title: "Profile 1 Mood Variable (String)", required: false
            input "moodVarU2", "hubVariable", title: "Profile 2 Mood Variable (String)", required: false
            input "moodVarU3", "hubVariable", title: "Profile 3 Mood Variable (String)", required: false
            input "moodVarU4", "hubVariable", title: "Profile 4 Mood Variable (String)", required: false
        }
    }
}

mappings {
    path("/chat") { action: [ GET: "renderDashboard" ] }
    path("/js/app.js") { action: [ GET: "serveJs" ] }
    path("/api/data") { action: [ GET: "getData" ] }
    path("/api/messages") { action: [ POST: "sendMessage" ] }
    path("/api/messages/clear") { action: [ POST: "manualClearMessages" ] }
    path("/api/users") { action: [ POST: "updateUsers" ] }
    path("/api/users/mood") { action: [ POST: "updateUserMoodApi" ] }
}

def installed() {
    log.info "Installed Advanced Smart Messaging 2.0"
    initialize()
}

def updated() {
    log.info "Updated Advanced Smart Messaging 2.0"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (!state.messages) state.messages = []
    if (!state.lastUpdate) state.lastUpdate = now()
    
    // Force reset profiles if missing or empty
    if (!state.users || state.users.size() == 0) {
        state.users = [
            [id: "u1", name: "Shane", avatar: "", disabled: false, pin: "", mood: "", moodTimestamp: 0],
            [id: "u2", name: "Christy", avatar: "", disabled: false, pin: "", mood: "", moodTimestamp: 0],
            [id: "u3", name: "Leanne", avatar: "", disabled: false, pin: "", mood: "", moodTimestamp: 0],
            [id: "u4", name: "User 4", avatar: "", disabled: false, pin: "", mood: "", moodTimestamp: 0]
        ]
    } else {
        // Ensure the pin and mood fields get added to existing profiles
        state.users.each { 
            if (it.pin == null) it.pin = "" 
            if (!it.containsKey('mood')) it.mood = ""
            if (!it.containsKey('moodTimestamp')) it.moodTimestamp = 0
        }
    }
    
    // Subscribe to external Hub Variable changes (so the Dashboard can update the Chat app's moods)
    if (settings.moodVarU1) subscribe(location, "variable:${settings.moodVarU1}", "externalMoodHandler")
    if (settings.moodVarU2) subscribe(location, "variable:${settings.moodVarU2}", "externalMoodHandler")
    if (settings.moodVarU3) subscribe(location, "variable:${settings.moodVarU3}", "externalMoodHandler")
    if (settings.moodVarU4) subscribe(location, "variable:${settings.moodVarU4}", "externalMoodHandler")
    
    // Wipe all messages every night at 3:00 AM to prevent DB bloat
    schedule("0 0 3 * * ?", nightlyWipe) 
    
    // Background 24-hour mood expiration check
    runEvery1Hour("checkMoods")
    
    // Sync current values on startup
    syncMoodVariables()
}

def externalMoodHandler(evt) {
    // If the Hub Variable is changed by another app, pull it into state here.
    syncMoodVariables()
}

def getHubVarSafe(varName) {
    if (!varName) return null
    try {
        def v = getGlobalVar(varName.toString())
        return v ? v.value : null
    } catch(e) { return null }
}

def syncMoodVariables() {
    if (!state.users) return
    def nowMs = now()
    for (int i = 1; i <= 4; i++) {
        def vName = settings["moodVarU${i}"]
        def u = state.users.find { it.id == "u${i}" }
        
        if (vName) {
            def val = getHubVarSafe(vName)
            if (val != null) {
                if (u && u.mood != val.toString()) {
                    u.mood = val.toString()
                    u.moodTimestamp = nowMs
                }
            }
        }
    }
    checkMoods() // Run expirations while we're syncing
}

def checkMoods() {
    if (!state.users) return
    def nowMs = now()
    state.users.each { u ->
        if (u.mood && u.mood != "" && u.mood != "😐") { 
            if (!u.moodTimestamp) u.moodTimestamp = nowMs
            if ((nowMs - u.moodTimestamp) > (24L * 60L * 60L * 1000L)) {
                u.mood = "😐" 
                u.moodTimestamp = nowMs 
                log.info "Advanced Smart Messaging: User ${u.name} mood expired (>24 hours). Reset to neutral."
                
                // Clear the Hub Variable if configured
                def uIdx = u.id.toString().replace("u", "").toUpperCase()
                def moodVarName = settings["moodVarU${uIdx}"]
                if (moodVarName) {
                    try { setGlobalVar(moodVarName.toString(), "😐") } catch(e) {} 
                }
            }
        }
    }
}

def updateUserMoodApi() {
    def data = request.JSON
    if (state.users) {
        def user = state.users.find { it.id == data.userId }
        if (user) {
            user.mood = data.mood
            user.moodTimestamp = now()
            
            // Sync to Hub Variable if configured
            def uIdx = data.userId.toString().replace("u", "").toUpperCase()
            def moodVarName = settings["moodVarU${uIdx}"]
            if (moodVarName) {
                try {
                    setGlobalVar(moodVarName.toString(), data.mood)
                    log.info "Advanced Smart Messaging: Synced mood to Hub Variable for ${data.userId}: ${data.mood}"
                } catch(e) {
                    log.warn "Failed to set Hub Variable ${moodVarName}: ${e}"
                }
            }
            
            return render(contentType: "application/json", data: JsonOutput.toJson([status: "success"]))
        }
    }
    render contentType: "application/json", data: JsonOutput.toJson([status: "error"])
}

def nightlyWipe() {
    state.messages = []
    state.lastUpdate = now()
    log.info "Advanced Smart Messaging: Nightly wipe executed. All messages deleted."
}

def enforceLimits() {
    // Hard limit to 60 messages total to keep the database extremely lightweight
    if (state.messages && state.messages.size() > 60) {
        // Keep only the newest 60
        state.messages = state.messages.drop(state.messages.size() - 60)
    }
}

def sendUserNotification(userId, msg, senderId) {
    // Do not notify the person who sent the message
    if (userId == senderId) return 
    
    def devInput = settings["notify${userId.toUpperCase()}"]
    if (devInput) {
        devInput.each { dev ->
            try {
                if (dev.hasCommand("deviceNotification")) {
                    dev.deviceNotification(msg)
                } else if (dev.hasCommand("speak")) {
                    dev.speak(msg)
                }
            } catch (e) { log.warn "Failed to send notification to ${dev.displayName}: ${e}" }
        }
    }
}

// Check if a room is currently occupied (accounting for the timeout hold)
def isRoomActive(sNum) {
    def mot = settings["speaker${sNum}Motion"]
    if (!mot) return true // Default to true if no sensor is linked
    
    try {
        def currState = mot.currentState("motion")
        if (!currState) return true 
        
        // If currently active, room is active
        if (currState.value == "active") return true
        
        // If inactive, check how long ago it changed
        if (currState.date) {
            def timeoutMins = settings.motionTimeout != null ? settings.motionTimeout as Integer : 5
            def timeoutMs = timeoutMins * 60 * 1000
            def elapsed = now() - currState.date.time
            
            // Still consider it active if the elapsed time is less than the timeout
            if (elapsed <= timeoutMs) return true
        }
    } catch (e) {
        log.warn "Advanced Smart Messaging: Error checking motion state: ${e}"
        return true // Default to true on error so TTS doesn't fail
    }
    
    return false
}

// --- API Endpoints ---

def getData() {
    syncMoodVariables() // Force sync to ensure UI always loads with latest external mood changes
    try {
        def frontendUsers = state.users ? state.users.collect { it } : []
        
        // Inject Room Speakers natively into the frontend user list and check motion
        (1..4).each { i ->
            if (settings["speaker${i}"] && settings["speaker${i}Name"]) {
                def roomActive = isRoomActive(i) 
                frontendUsers << [id: "s${i}", name: settings["speaker${i}Name"], disabled: false, isSpeaker: true, isActive: roomActive]
            }
        }
        
        def resp = [
            users: frontendUsers,
            messages: state.messages ?: [],
            lastUpdate: state.lastUpdate
        ]
        render contentType: "application/json", data: JsonOutput.toJson(resp)
    } catch(err) {
        log.error "getData Error: ${err}"
        render contentType: "application/json", data: JsonOutput.toJson([error: true, message: err.message])
    }
}

def sendMessage() {
    def data = request.JSON
    if (!state.messages) state.messages = []
    
    def nowObj = new Date()
    def timeStr = nowObj.format("h:mm a")
    
    def newMsg = [
        id: "msg_${now()}",
        from: data.from,
        to: data.to,
        text: data.text,
        timestamp: now(),
        timeStr: timeStr
    ]
    
    state.messages << newMsg
    enforceLimits()
    state.lastUpdate = now()
    
    // ----------------------------------------------------
    // Targeted Notification & TTS Logic
    // ----------------------------------------------------
    def senderName = state.users.find { it.id == data.from }?.name ?: "Someone"
    
    if (data.to.startsWith("s") && data.to.length() <= 2) {
        // Speaker / Room Broadcast
        def sNum = data.to.replace("s", "")
        def spkr = settings["speaker${sNum}"]
        def vol = settings["speaker${sNum}Vol"] != null ? settings["speaker${sNum}Vol"] as Integer : 50
        
        // Mode validation
        def isAllowedMode = !settings.allowedModes || settings.allowedModes.contains(location.mode)
        
        if (spkr) {
            if (!isAllowedMode) {
                log.info "Advanced Smart Messaging: TTS skipped to ${spkr.displayName}, current mode (${location.mode}) is not permitted."
            } else {
                def roomActive = isRoomActive(sNum)
                
                if (roomActive) {
                    def msgToSend = "${senderName} says: ${data.text}"
                    try {
                        // playTextAndRestore is ideal for Sonos to resume music after the TTS finishes
                        if (spkr.hasCommand("playTextAndRestore")) {
                            spkr.playTextAndRestore(msgToSend, vol)
                        } else {
                            // Fallback for non-Sonos TTS speakers
                            if (spkr.hasCommand("setVolume")) spkr.setVolume(vol)
                            spkr.speak(msgToSend)
                        }
                        log.info "Advanced Smart Messaging: Sent TTS broadcast to ${spkr.displayName}"
                    } catch (e) {
                        log.error "Advanced Smart Messaging: Failed to send TTS to ${spkr.displayName}: ${e}"
                    }
                } else {
                    log.info "Advanced Smart Messaging: ${spkr.displayName} room is inactive, skipping TTS."
                }
            }
        }
    } else if (data.to == "all") {
        // Group Message: Notify everyone (except the sender)
        def alertText = "Household Chat - ${senderName}: ${data.text}"
        sendUserNotification("u1", alertText, data.from)
        sendUserNotification("u2", alertText, data.from)
        sendUserNotification("u3", alertText, data.from)
        sendUserNotification("u4", alertText, data.from)
    } else {
        // Direct Message: Notify only the specific recipient
        def alertText = "Private Message - ${senderName}: ${data.text}"
        sendUserNotification(data.to, alertText, data.from)
    }
    
    render contentType: "application/json", data: JsonOutput.toJson([status: "success", message: newMsg])
}

def updateUsers() {
    def data = request.JSON
    state.users = data
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def manualClearMessages() {
    state.messages = []
    state.lastUpdate = now()
    log.info "Advanced Smart Messaging: Manual wipe executed."
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

// ============================================================================
// FRONTEND RENDERING
// ============================================================================

def renderDashboard() {
    def token = params.access_token ?: state.accessToken
    def html = new StringBuilder()
    html << getHtmlHead()
    html << getHtmlLayout()
    html << getHtmlModals()
    
    html << "<script src=\"js/app.js?access_token=${token}\"></script>\n"
    html << "</body></html>"
    
    render contentType: "text/html", data: html.toString()
}

def serveJs() {
    render contentType: "application/javascript", data: getJsCode()
}

def getHtmlHead() {
    return '''
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <link rel="icon" href="data:,">
    <title>Smart Messaging</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body, header, main, footer { transition: background-color 0.4s, border-color 0.4s, color 0.4s; }
        ::-webkit-scrollbar { display: none; }
        .hide-scrollbar::-webkit-scrollbar { display: none; }
        .bubble-enter { animation: popIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards; transform-origin: bottom; opacity: 0; transform: scale(0.9) translateY(10px); }
        @keyframes popIn { to { opacity: 1; transform: scale(1) translateY(0); } }
        /* Safe area padding for mobile notch/home bars */
        .pb-safe { padding-bottom: env(safe-area-inset-bottom); }
    </style>
</head>
<body class="fixed inset-0 flex flex-col overflow-hidden text-gray-800 bg-[#f4f4f5] dark:bg-gray-950 dark:text-gray-100 antialiased">
    <div id="fancyAlertModal" class="fixed inset-0 bg-gray-900 bg-opacity-60 backdrop-blur-sm hidden z-[100] flex justify-center items-center opacity-0 transition-opacity duration-300">
        <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-sm p-6 transform scale-95 transition-transform duration-300 border border-gray-200 dark:border-gray-700 text-center" id="fancyAlertCard">
            <div id="fancyAlertIcon" class="text-5xl mb-4"></div>
            <h3 id="fancyAlertTitle" class="text-xl font-bold mb-2 text-gray-900 dark:text-white"></h3>
            <p id="fancyAlertMsg" class="text-sm text-gray-500 dark:text-gray-400 mb-6"></p>
            <button onclick="closeFancyAlert()" class="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-bold transition shadow-md">Got It</button>
        </div>
    </div>
'''
}

def getHtmlLayout() {
    return '''
    <!-- Header -->
    <header class="bg-white dark:bg-gray-900 shadow-sm z-20 shrink-0 flex flex-col border-b border-gray-200 dark:border-gray-800">
        <div class="flex justify-between items-center px-4 py-3">
            <div class="flex items-center gap-3 min-w-0">
                <div class="w-10 h-10 rounded-xl bg-indigo-600 flex items-center justify-center text-white shadow-sm shrink-0">
                    <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"></path></svg>
                </div>
                <div class="truncate leading-tight flex-1 min-w-0">
                    <div class="text-[10px] font-bold uppercase tracking-widest text-indigo-500">Local Chat</div>
                    <div class="text-lg font-extrabold text-gray-900 dark:text-white truncate">Smart Messaging</div>
                </div>
            </div>
            <div class="flex items-center gap-2 shrink-0">
                <button onclick="handleMoodBtnClick()" class="p-2 bg-gray-50 dark:bg-gray-800 rounded-full text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 transition flex items-center justify-center" title="Set Mood">
                    <span class="text-lg leading-none mt-[1px]">😀</span>
                </button>
                <button onclick="manualClear()" class="p-2 bg-gray-50 dark:bg-gray-800 rounded-full text-red-500 hover:bg-red-50 dark:hover:bg-gray-700 transition" title="Wipe Data">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                </button>
                <button onclick="openSettingsModal()" class="p-2 bg-gray-50 dark:bg-gray-800 rounded-full text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 transition" title="User Settings">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                </button>
            </div>
        </div>
        
        <!-- Controls Bar -->
        <div class="flex px-4 py-2 bg-gray-50 dark:bg-gray-800/50 border-t border-gray-100 dark:border-gray-800 items-center justify-between gap-3">
            <div class="flex flex-col flex-1 min-w-0">
                <label class="text-[10px] font-bold text-gray-400 dark:text-gray-500 uppercase">You Are</label>
                <select id="activeUserSelect" onchange="changeActiveUser(this.value)" class="bg-transparent font-bold text-sm text-gray-800 dark:text-gray-200 outline-none w-full appearance-none cursor-pointer">
                    <option value="">Select User...</option>
                </select>
            </div>
            <div class="h-6 w-px bg-gray-300 dark:bg-gray-700 shrink-0"></div>
            <div class="flex flex-col flex-1 min-w-0 pl-1">
                <label class="text-[10px] font-bold text-gray-400 dark:text-gray-500 uppercase">Chat With</label>
                <select id="threadSelect" onchange="changeThread(this.value)" class="bg-transparent font-bold text-sm text-indigo-600 dark:text-indigo-400 outline-none w-full appearance-none cursor-pointer">
                    <option value="all">Household Group</option>
                </select>
            </div>
        </div>
    </header>

    <!-- Chat Area -->
    <main id="chatArea" class="flex-1 overflow-y-auto p-4 space-y-4 flex flex-col relative pb-safe">
        <!-- Messages will render here -->
    </main>

    <!-- Input Bar -->
    <footer class="bg-white dark:bg-gray-900 border-t border-gray-200 dark:border-gray-800 shrink-0 pb-safe">
        <form onsubmit="handleSend(event)" class="flex items-end gap-2 p-3 sm:p-4">
            <div class="flex-1 bg-gray-100 dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-2 flex items-center min-h-[44px]">
                <textarea id="messageInput" rows="1" class="w-full bg-transparent text-sm text-gray-900 dark:text-gray-100 placeholder-gray-500 outline-none resize-none px-2 max-h-32" placeholder="Type a message..." oninput="autoGrow(this)" onkeydown="checkEnter(event)"></textarea>
            </div>
            <button type="submit" id="sendBtn" class="w-11 h-11 bg-indigo-600 hover:bg-indigo-700 text-white rounded-full flex items-center justify-center shadow-md transition transform active:scale-95 shrink-0 disabled:opacity-50 disabled:cursor-not-allowed">
                <svg class="w-5 h-5 ml-1" fill="currentColor" viewBox="0 0 20 20"><path d="M10.894 2.553a1 1 0 00-1.788 0l-7 14a1 1 0 001.169 1.409l5-1.429A1 1 0 009 15.571V11a1 1 0 112 0v4.571a1 1 0 00.725.962l5 1.428a1 1 0 001.17-1.408l-7-14z"></path></svg>
            </button>
        </form>
    </footer>
'''
}

def getHtmlModals() {
    return '''
    <!-- Settings Modal -->
    <div id="settingsModal" class="fixed inset-0 bg-gray-900 bg-opacity-60 backdrop-blur-sm hidden z-50 flex justify-center items-center">
        <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto border border-gray-200 dark:border-gray-800">
            <h2 class="text-xl font-bold mb-2 text-gray-900 dark:text-white">Profile Setup</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400 mb-5">Configure user names, PINs, and visibility.</p>
            <form onsubmit="submitUsers(event)">
                <div id="userSetupList" class="space-y-4 mb-6"></div>
                <div class="flex justify-between items-center pt-4 border-t border-gray-100 dark:border-gray-800">
                    <button type="button" onclick="manualClear()" class="px-4 py-2.5 bg-red-50 text-red-600 rounded-lg text-sm font-semibold hover:bg-red-100 dark:bg-red-900/30 dark:text-red-400 dark:hover:bg-red-900/50 transition">Clear All Messages</button>
                    <div class="flex gap-2">
                        <button type="button" onclick="closeSettingsModal()" class="px-4 py-2.5 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded-lg text-sm font-semibold hover:bg-gray-200 dark:hover:bg-gray-700 transition">Cancel</button>
                        <button type="submit" class="px-4 py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-semibold hover:bg-indigo-700 transition">Save</button>
                    </div>
                </div>
            </form>
        </div>
    </div>
    
    <!-- Mood Modal -->
    <div id="moodModal" class="fixed inset-0 bg-gray-900 bg-opacity-60 backdrop-blur-sm hidden z-[60] flex justify-center items-center">
        <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-xl w-full max-w-sm sm:max-w-md p-5 border border-gray-200 dark:border-gray-800 max-h-[90vh] overflow-y-auto">
            <h3 class="text-center font-bold text-gray-800 dark:text-gray-200 mb-1">How are you feeling?</h3>
            <p class="text-xs text-center text-gray-500 mb-4">Moods automatically reset to neutral after 24 hours.</p>
            <input type="hidden" id="moodUserId">
            <div class="grid grid-cols-5 sm:grid-cols-6 gap-2 mb-4" id="moodGrid"></div>

            <div class="mt-4 pt-3 border-t border-gray-200 dark:border-gray-700">
                <h4 class="text-xs font-bold text-gray-700 dark:text-gray-300 uppercase tracking-wider mb-2">Mood Meaning Guide</h4>
                <div class="overflow-x-auto">
                    <table class="w-full text-[11px] text-left border-collapse">
                        <thead>
                            <tr class="border-b border-gray-200 dark:border-gray-700 text-gray-400">
                                <th class="py-1 px-1.5 font-bold">Category</th>
                                <th class="py-1 px-1.5 font-bold">Emojis</th>
                                <th class="py-1 px-1.5 font-bold">Meaning</th>
                            </tr>
                        </thead>
                        <tbody class="divide-y divide-gray-100 dark:divide-gray-800 text-gray-600 dark:text-gray-300">
                            <tr>
                                <td class="py-1 px-1.5 font-semibold text-emerald-500">Motivated</td>
                                <td class="py-1 px-1.5">🔥 😎 😀 🥳 🥰 😂 🤠</td>
                                <td class="py-1 px-1.5">High energy & upbeat</td>
                            </tr>
                            <tr>
                                <td class="py-1 px-1.5 font-semibold text-gray-500">Neutral</td>
                                <td class="py-1 px-1.5">😐 (or none)</td>
                                <td class="py-1 px-1.5">Standard baseline</td>
                            </tr>
                            <tr>
                                <td class="py-1 px-1.5 font-semibold text-amber-500">Low Energy</td>
                                <td class="py-1 px-1.5">😴 🥱 🥺 🤡</td>
                                <td class="py-1 px-1.5">Tired / low battery</td>
                            </tr>
                            <tr>
                                <td class="py-1 px-1.5 font-semibold text-orange-500">Frazzled</td>
                                <td class="py-1 px-1.5">🤯 🤬 🤔 🤪 😤 😫</td>
                                <td class="py-1 px-1.5">Stressed / chaotic / zany</td>
                            </tr>
                            <tr>
                                <td class="py-1 px-1.5 font-semibold text-red-500">Sick / Exhausted</td>
                                <td class="py-1 px-1.5">🤕 🤒 🩹 🥶 🥵 🤢 💩 🤐</td>
                                <td class="py-1 px-1.5">Unwell / fever / pain</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

            <button onclick="setMood('😐')" class="mt-4 w-full py-2 bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 rounded-lg text-sm font-semibold hover:bg-gray-200 dark:hover:bg-gray-700 transition">Set to Neutral</button>
            <button onclick="closeMoodModal()" class="mt-2 w-full py-2 bg-white dark:bg-gray-900 text-gray-500 border border-gray-200 dark:border-gray-700 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-800 transition">Cancel</button>
        </div>
    </div>
'''
}

def getJsCode() {
    return """
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('access_token');
    
    // Dynamically find the correct app path so data requests don't get lost
    const basePath = window.location.pathname.replace('/chat', '');
    
    let users = [];
    let messages = [];
    let activeUserId = localStorage.getItem('asm_active_user') || '';
    let activeThread = localStorage.getItem('asm_active_thread') || 'all';

    const MOODS = ['😀','😂','🥰','😎','🤔','😴','🤪','🤐','🤯','🥳','🥶','🥵','🤢','🥺','🤬','🤠','🤡','👽','👻','💩','😤','😫'];

    // Fetch initial data
    async function fetchData() {
        try {
            const res = await fetch(`\${basePath}/api/data?access_token=\${token}`);
            const data = await res.json();
            if (!data.error) {
                users = data.users || [];
                messages = data.messages || [];
                updateUI();
            }
        } catch (e) {
            console.error("Failed to fetch data", e);
        }
    }

    function updateUI() {
        renderDropdowns();
        renderMessages();
    }

    function renderDropdowns() {
        const userSelect = document.getElementById('activeUserSelect');
        const threadSelect = document.getElementById('threadSelect');
        
        const currUser = userSelect.value || activeUserId;
        const currThread = threadSelect.value || activeThread;

        userSelect.innerHTML = '<option value="">Select User...</option>';
        threadSelect.innerHTML = '<option value="all">Household Group</option>';

        users.forEach(u => {
            if (!u.disabled) {
                let moodDisplay = u.mood ? ` \${u.mood}` : '';
                
                // Do not allow someone to act *as* a speaker
                if (!u.isSpeaker) {
                    userSelect.innerHTML += `<option value="\${u.id}">\${u.name}\${moodDisplay}</option>`;
                }
                
                // Format the display name differently if it's a room with motion awareness
                let displayName = u.name;
                if (u.isSpeaker) {
                    const statusText = u.isActive ? '🟢 Active' : '⚪ Inactive';
                    displayName = `🔊 \${u.name} (\${statusText})`;
                } else {
                    displayName = `\${u.name}\${moodDisplay}`;
                }
                
                threadSelect.innerHTML += `<option value="\${u.id}">\${displayName}</option>`;
            }
        });

        if (currUser) userSelect.value = currUser;
        if (currThread) threadSelect.value = currThread;
    }

    function changeActiveUser(val) {
        if (!val) {
            activeUserId = '';
            localStorage.removeItem('asm_active_user');
            renderMessages();
            return;
        }

        const selectedUser = users.find(u => u.id === val);
        
        // Check for password/PIN
        if (selectedUser && selectedUser.pin && selectedUser.pin.trim() !== '') {
            const enteredPin = prompt(`Please enter the PIN for \${selectedUser.name}:`);
            if (enteredPin !== selectedUser.pin) {
                showFancyAlert('Access Denied', 'Incorrect PIN entered.', '🔒');
                // Revert dropdown visual back to the previous user
                document.getElementById('activeUserSelect').value = activeUserId || '';
                return;
            }
        }

        activeUserId = val;
        localStorage.setItem('asm_active_user', val);
        renderMessages();
    }

    function changeThread(val) {
        activeThread = val;
        localStorage.setItem('asm_active_thread', val);
        renderMessages();
    }

    function renderMessages() {
        const chatArea = document.getElementById('chatArea');
        chatArea.innerHTML = '';

        const filtered = messages.filter(m => 
            activeThread === 'all' ? m.to === 'all' : 
            (m.to === activeThread && m.from === activeUserId) || (m.from === activeThread && m.to === activeUserId)
        );

        filtered.forEach(m => {
            const isMe = m.from === activeUserId;
            const sender = users.find(u => u.id === m.from);
            const senderName = sender ? sender.name : 'Unknown';
            const senderMood = sender && sender.mood ? ` \${sender.mood}` : '';

            const div = document.createElement('div');
            div.className = `flex w-full bubble-enter \${isMe ? 'justify-end' : 'justify-start'}`;
            
            div.innerHTML = `
                <div class="flex flex-col max-w-[75%] \${isMe ? 'items-end' : 'items-start'}">
                    \${!isMe && activeThread === 'all' ? `<span class="text-xs text-gray-500 ml-1 mb-1">\${senderName}\${senderMood}</span>` : ''}
                    <div class="px-4 py-2 rounded-2xl \${isMe ? 'bg-indigo-600 text-white rounded-br-none' : 'bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-bl-none'} shadow-sm text-sm whitespace-pre-wrap">\${m.text}</div>
                    <span class="text-[10px] text-gray-400 mt-1 mx-1">\${m.timeStr}</span>
                </div>
            `;
            chatArea.appendChild(div);
        });

        chatArea.scrollTo(0, chatArea.scrollHeight);
    }

    async function handleSend(e) {
        e.preventDefault();
        if (!activeUserId) return showFancyAlert('Error', 'Please select who you are in the top left first.', '👤');
        
        const input = document.getElementById('messageInput');
        const text = input.value.trim();
        if (!text) return;

        input.value = '';
        input.style.height = 'auto';

        try {
            await fetch(`\${basePath}/api/messages?access_token=\${token}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ from: activeUserId, to: activeThread, text })
            });
            fetchData();
        } catch (e) {
            console.error("Failed to send", e);
        }
    }

    function checkEnter(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend(e);
        }
    }

    function autoGrow(el) {
        el.style.height = 'auto';
        el.style.height = (el.scrollHeight) + 'px';
    }

    // --- MOOD MATRIX LOGIC ---

    function handleMoodBtnClick() {
        if (!activeUserId) {
            showFancyAlert('Select a User', 'Please select who you are in the top left dropdown before setting your mood.', 'info');
        } else {
            openMoodSelector(activeUserId);
        }
    }

    function openMoodSelector(userId) {
        document.getElementById("moodUserId").value = userId;
        const grid = document.getElementById("moodGrid"); 
        let html = "";
        const u = users.find(x => x.id === userId); 
        const currentMood = u ? u.mood : "";
        
        for(let i=0; i<MOODS.length; i++) {
            const m = MOODS[i]; 
            const activeClass = (m === currentMood) ? "bg-indigo-100 ring-2 ring-indigo-500 dark:bg-indigo-900" : "hover:bg-gray-100 dark:hover:bg-gray-800";
            html += `<button onclick="setMood('\${m}')" class="text-2xl p-2 rounded-xl transition \${activeClass}">\${m}</button>`;
        }
        grid.innerHTML = html; 
        document.getElementById("moodModal").classList.remove("hidden");
    }

    function closeMoodModal() { 
        document.getElementById("moodModal").classList.add("hidden"); 
    }

    async function setMood(moodEmoji) {
        const userId = document.getElementById("moodUserId").value; 
        closeMoodModal();
        
        const u = users.find(x => x.id === userId); 
        if (u) {
            u.mood = moodEmoji;
        }
        
        renderDropdowns(); 
        renderMessages();
        
        try { 
            await fetch(`\${basePath}/api/users/mood?access_token=\${token}`, { 
                method: "POST", 
                headers: { "Content-Type": "application/json" }, 
                body: JSON.stringify({ userId: userId, mood: moodEmoji }) 
            }); 
        } catch(e) { 
            console.error("Failed to update mood", e); 
        }
    }

    // --- SETTINGS MODAL LOGIC ---

    function openSettingsModal() {
        const modal = document.getElementById('settingsModal');
        const list = document.getElementById('userSetupList');
        list.innerHTML = '';
        
        let uIndex = 1;
        users.forEach((u) => {
            // Ignore injected speakers from rendering in the frontend config modal
            if (u.isSpeaker) return; 
            
            list.innerHTML += `
                <div class="p-3 bg-gray-50 dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 flex flex-col gap-2">
                    <div class="flex items-center justify-between">
                        <label class="text-xs text-gray-500 font-bold uppercase">Profile \${uIndex++}</label>
                        <div class="flex items-center gap-2">
                            <input type="checkbox" id="setupDisabled_\${u.id}" \${u.disabled ? 'checked' : ''} class="w-4 h-4 rounded border-gray-300">
                            <label class="text-[10px] text-gray-500 uppercase font-bold">Hide</label>
                        </div>
                    </div>
                    <div class="flex gap-2">
                        <div class="flex-1">
                            <input type="text" id="setupName_\${u.id}" value="\${u.name}" placeholder="Name" class="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded px-2 py-1.5 text-sm outline-none text-gray-900 dark:text-white">
                        </div>
                        <div class="w-24">
                            <input type="password" id="setupPin_\${u.id}" value="\${u.pin || ''}" placeholder="PIN (Opt)" class="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded px-2 py-1.5 text-sm outline-none text-gray-900 dark:text-white">
                        </div>
                    </div>
                </div>
            `;
        });
        
        modal.classList.remove('hidden');
    }

    function closeSettingsModal() {
        document.getElementById('settingsModal').classList.add('hidden');
    }

    async function submitUsers(e) {
        e.preventDefault();
        
        // Strip out the injected speakers so they don't overwrite the core user config
        const newUsers = users.filter(u => !u.isSpeaker).map(u => ({
            id: u.id,
            name: document.getElementById(`setupName_\${u.id}`).value,
            pin: document.getElementById(`setupPin_\${u.id}`).value,
            avatar: u.avatar,
            disabled: document.getElementById(`setupDisabled_\${u.id}`).checked,
            mood: u.mood || "",
            moodTimestamp: u.moodTimestamp || 0
        }));

        try {
            await fetch(`\${basePath}/api/users?access_token=\${token}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(newUsers)
            });
            // Immediately sync logic and refresh view
            fetchData();
            closeSettingsModal();
        } catch (e) {
            console.error("Failed to save users", e);
        }
    }

    async function manualClear() {
        if (!confirm('Are you sure you want to delete all messages? This cannot be undone.')) return;
        try {
            await fetch(`\${basePath}/api/messages/clear?access_token=\${token}`, { method: 'POST' });
            closeSettingsModal();
            fetchData();
        } catch (e) {
            console.error(e);
        }
    }

    function showFancyAlert(title, msg, icon) {
        document.getElementById('fancyAlertTitle').innerText = title;
        document.getElementById('fancyAlertMsg').innerText = msg;
        document.getElementById('fancyAlertIcon').innerText = icon || '⚠️';
        
        const modal = document.getElementById('fancyAlertModal');
        const card = document.getElementById('fancyAlertCard');
        modal.classList.remove('hidden');
        
        setTimeout(() => {
            modal.classList.remove('opacity-0');
            card.classList.remove('scale-95');
        }, 10);
    }

    function closeFancyAlert() {
        const modal = document.getElementById('fancyAlertModal');
        const card = document.getElementById('fancyAlertCard');
        modal.classList.add('opacity-0');
        card.classList.add('scale-95');
        setTimeout(() => modal.classList.add('hidden'), 300);
    }

    // Refresh chat every 15 seconds to check for new messages and save hub CPU
    setInterval(fetchData, 15000);
    fetchData();
    """
}
