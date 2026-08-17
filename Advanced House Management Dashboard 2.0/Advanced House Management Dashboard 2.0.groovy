/**
 * Advanced House Management Dashboard 2.0
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

definition(
    name: "Advanced House Management Dashboard 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    oauth: [displayName: "AHM Dashboard", displayLink: ""]
)

preferences {
    page(name: "mainPage")
}

def syncMoodVariables() {
    if (!state.users) return
    def nowMs = now()
    
    // Array of emojis that represent "Sick/Exhausted" in your UI
    def sickEmojis = ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]
    
    for (int i = 1; i <= 4; i++) {
        def vName = settings["moodVarU${i}"]
        def u = state.users.find { it.id == "u${i}" }
        
        if (vName) {
            def val = getHubVarSafe(vName)
            if (val != null) {
                if (u && u.mood != val.toString()) {
                    def oldMood = u.mood
                    u.mood = val.toString()
                    u.moodTimestamp = nowMs
                    
                    // NEW: Auto-enable Sick Switch if mood changes TO a sick emoji
                    if (sickEmojis.contains(u.mood) && !sickEmojis.contains(oldMood)) {
                        def sw = settings["sickSwitchU${i}"]
                        if (sw && sw.currentValue("switch") != "on") {
                            log.info "External mood sync: Profile ${i} set to sick (${u.mood}). Auto-enabling sick switch."
                            try { sw.on() } catch(e) { log.error "Failed to turn on sick switch: ${e}" }
                        }
                    }
                }
            }
        }
        
        // 24-Hour Mood Expiration Timeout Check
        if (u && u.mood && u.mood != "") {
            if (!u.moodTimestamp) u.moodTimestamp = nowMs
            if ((nowMs - u.moodTimestamp) > (24L * 60L * 60L * 1000L)) {
                u.mood = ""
                u.moodTimestamp = 0
                if (vName) {
                    try { setGlobalVar(vName.toString(), "") } catch(e) {}
                }
                log.info "Profile ${i} mood expired (>24 hours). Reset to neutral."
            }
        }
    }
}

def getHumanReadableStatus() {
    return "System Online. Dashboard active and monitoring ${state.users?.size() ?: 0} profiles."
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        syncMoodVariables()
    }
}

def mainPage() {
    syncMoodVariables()
    
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            try {
                def statusExplanation = getHumanReadableStatus()
                if (statusExplanation) paragraph "<b>System Status:</b> ${statusExplanation}"
            } catch(e) {}

            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>Advanced House Management Dashboard</b><br>Expand the sections below to configure your system.</div>"
        }

        section("<b>OAuth Setup</b>", hideable: true, hidden: false) {
            if (!state.accessToken) {
                createAccessToken()
                paragraph "Token created. Please click 'Done' to install, then reopen this app."
            } else {
                def localUri = getFullLocalApiServerUrl() + "/dashboard?access_token=${state.accessToken}"
                def cloudUri = getFullApiServerUrl() + "/dashboard?access_token=${state.accessToken}"
                
                href(title: "Open Local Dashboard", url: localUri, style: "external", description: "Use on your home network.")
                href(title: "Open Cloud Dashboard", url: cloudUri, style: "external", description: "Use when away from home.")
            }
        }

        section("<b>1. Dashboard Configuration</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Configures general dashboard display settings like the top-left calendar name.</div>"
            input "calendarName", "text", title: "Calendar Name (Top Left)", defaultValue: "Family Calendar", required: true
        }

        section("<b>2. Notifications & Alerts</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Select notification devices (e.g., Push or Audio) and what activities they should announce.</div>"
            input "notifyDevices", "capability.notification", title: "Select Notification Devices", multiple: true, required: false
            
            input "notifyNewEvents", "bool", title: "Notify when a New Event is created", defaultValue: true
            input "notifyNewMessages", "bool", title: "Notify when a New Message is posted", defaultValue: true
            input "notifyNewMeals", "bool", title: "Notify when the Menu is updated", defaultValue: true
            input "notifyNewPolls", "bool", title: "Notify when a New Poll is created", defaultValue: true
            
            paragraph "<b>Upcoming Event Alerts:</b>"
            input "notifyUpcomingEvents", "bool", title: "Notify before an Event starts", defaultValue: false
            input "notifyEventMinutes", "number", title: "Minutes before event to alert", defaultValue: 15
            input "notifyEventStart", "bool", title: "Notify exactly at Event start time", defaultValue: false
            
            paragraph "<b>Event Automation Triggers (Virtual Switches):</b>"
            input "eventSwitch1Hour", "capability.switch", title: "1 Hour Before Event Switch", required: false
            input "eventSwitch5Min", "capability.switch", title: "5 Minutes Before Event Switch", required: false
        }

        section("<b>3. Weather Station Integration</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Displays a professional weather ticker. Sun position is calculated automatically based on Hubitat location coordinates.</div>"
            input "weatherStation", "capability.sensor", title: "Select Weather Station Device", required: false
            input "luxDevice", "capability.illuminanceMeasurement", title: "Illuminance (Lux) Sensor", required: false
            input "overcastSwitch", "capability.switch", title: "Overcast Virtual Switch", required: false
        }

        section("<b>4. User Sensors, Locks & Health</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Link presence, lock codes, and health data to each profile slot.</div>"
            input "presenceU1", "capability.presenceSensor", title: "Presence Sensor for Profile 1", required: false
            input "presenceU2", "capability.presenceSensor", title: "Presence Sensor for Profile 2", required: false
            input "presenceU3", "capability.presenceSensor", title: "Presence Sensor for Profile 3", required: false
            input "presenceU4", "capability.presenceSensor", title: "Presence Sensor for Profile 4", required: false
            
            input "awayMode", "mode", title: "Away Mode (Marks lock-code users as Away)", required: false
            
            paragraph "<b>Entry Lock Codes</b>"
            input "presenceLocks", "capability.lockCodes", title: "Select Locks to read Entry Codes", multiple: true, submitOnChange: true
            if (presenceLocks) {
                def lockCodeOptions = [:]
                presenceLocks.each { lock ->
                    def codesJson = lock.currentValue("lockCodes")
                    if (codesJson) {
                        try {
                            def codes = new JsonSlurper().parseText(codesJson?.toString())
                            codes.each { slot, data ->
                                lockCodeOptions["${lock.id}-${slot}"] = "${lock.displayName} - ${data.name}"
                            }
                        } catch(e) { log.warn "Failed to parse lock codes for ${lock.displayName}" }
                    }
                }
                if (lockCodeOptions) {
                    input "u1LockCode", "enum", title: "Profile 1 Lock Code", options: lockCodeOptions, required: false, multiple: true
                    input "u2LockCode", "enum", title: "Profile 2 Lock Code", options: lockCodeOptions, required: false, multiple: true
                    input "u3LockCode", "enum", title: "Profile 3 Lock Code", options: lockCodeOptions, required: false, multiple: true
                    input "u4LockCode", "enum", title: "Profile 4 Lock Code", options: lockCodeOptions, required: false, multiple: true
                }
            }
            
            paragraph "<b>Health, Sleep & Effort Variables</b>"
            input "sickSwitchU1", "capability.switch", title: "Profile 1 Sick Switch (Virtual)", required: false
            input "visitorSwitchU1", "capability.switch", title: "Profile 1 Visitor Override Switch (Virtual)", required: false
            input "sleepVarU1", "hubVariable", title: "Profile 1 Sleep Score Variable (Number)", required: false
            input "effortVarU1", "hubVariable", title: "Profile 1 Effort Score Variable (Number)", required: false
            input "weightVarU1", "hubVariable", title: "Profile 1 Weight Variable (Number)", required: false
            input "stepsVarU1", "hubVariable", title: "Profile 1 Steps Variable (Number)", required: false
            input "moodVarU1", "hubVariable", title: "Profile 1 Mood Variable (String) - Syncs to Iron AI", required: false
            
            paragraph "---"
            input "sickSwitchU2", "capability.switch", title: "Profile 2 Sick Switch (Virtual)", required: false
            input "visitorSwitchU2", "capability.switch", title: "Profile 2 Visitor Override Switch (Virtual)", required: false
            input "sleepVarU2", "hubVariable", title: "Profile 2 Sleep Score Variable (Number)", required: false
            input "effortVarU2", "hubVariable", title: "Profile 2 Effort Score Variable (Number)", required: false
            input "weightVarU2", "hubVariable", title: "Profile 2 Weight Variable (Number)", required: false
            input "stepsVarU2", "hubVariable", title: "Profile 2 Steps Variable (Number)", required: false
            input "moodVarU2", "hubVariable", title: "Profile 2 Mood Variable (String) - Syncs to Iron AI", required: false
            
            paragraph "---"
            input "sickSwitchU3", "capability.switch", title: "Profile 3 Sick Switch (Virtual)", required: false
            input "visitorSwitchU3", "capability.switch", title: "Profile 3 Visitor Override Switch (Virtual)", required: false
            input "sleepVarU3", "hubVariable", title: "Profile 3 Sleep Score Variable (Number)", required: false
            input "effortVarU3", "hubVariable", title: "Profile 3 Effort Score Variable (Number)", required: false
            input "weightVarU3", "hubVariable", title: "Profile 3 Weight Variable (Number)", required: false
            input "stepsVarU3", "hubVariable", title: "Profile 3 Steps Variable (Number)", required: false
            input "moodVarU3", "hubVariable", title: "Profile 3 Mood Variable (String)", required: false
            
            paragraph "---"
            input "sickSwitchU4", "capability.switch", title: "Profile 4 Sick Switch (Virtual)", required: false
            input "visitorSwitchU4", "capability.switch", title: "Profile 4 Visitor Override Switch (Virtual)", required: false
            input "sleepVarU4", "hubVariable", title: "Profile 4 Sleep Score Variable (Number)", required: false
            input "effortVarU4", "hubVariable", title: "Profile 4 Effort Score Variable (Number)", required: false
            input "weightVarU4", "hubVariable", title: "Profile 4 Weight Variable (Number)", required: false
            input "stepsVarU4", "hubVariable", title: "Profile 4 Steps Variable (Number)", required: false
            input "moodVarU4", "hubVariable", title: "Profile 4 Mood Variable (String)", required: false
        }

        section("<b>5. Alerts & Warnings (Virtual/Physical Switches)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> When these switches turn 'ON', highly visible alert banners will override the top of the dashboard.</div>"
            input "thermalLockdownSwitch", "capability.switch", title: "Thermal Lockdown Switch", required: false
            input "alertTornado", "capability.switch", title: "Tornado Warning Switch", required: false
            input "alertTornadoWatch", "capability.switch", title: "Tornado Watch Switch", required: false
            input "alertThunderstorm", "capability.switch", title: "Thunderstorm Warning Switch", required: false
            input "alertThunderstormWatch", "capability.switch", title: "Thunderstorm Watch Switch", required: false
            input "alertRain", "capability.switch", title: "Rain Alert Switch", required: false
            input "alertSprinkler", "capability.switch", title: "Sprinkling (Light Rain) Switch", required: false
        }

        section("<b>6. Live Device Monitoring (Top Banner)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Selected devices will appear dynamically below the header when active/open.</div>"
            input "selectedLocks", "capability.lock", title: "Monitor Locks (Shows when Unlocked)", multiple: true, required: false
            input "selectedMotion", "capability.motionSensor", title: "Monitor Motion (Shows when Active)", multiple: true, required: false
            input "monitorThermostat", "capability.thermostat", title: "Monitor Thermostat (Shows when Heating/Cooling)", required: false
            input "mailSwitch", "capability.switch", title: "Mail Arrival Virtual Switch (Shows when ON)", required: false
        }

        section("<b>7. Full Device Status Tab</b>", hideable: true, hidden: true) {
            input "monitorLights", "capability.switch", title: "Monitor Lights", multiple: true, required: false
            input "monitorLocks", "capability.lock", title: "Monitor Locks", multiple: true, required: false
            input "monitorContact", "capability.contactSensor", title: "Monitor Contact Sensors", multiple: true, required: false
            input "monitorMotion", "capability.motionSensor", title: "Monitor Motion Sensors", multiple: true, required: false
        }

        section("<b>8. Music Tab (Sonos)</b>", hideable: true, hidden: true) {
            input "sonosSpeakers", "capability.musicPlayer", title: "Select Sonos Speakers", multiple: true, submitOnChange: true
            if (sonosSpeakers) {
                sonosSpeakers.each { spk ->
                    paragraph "<b>Favorites for ${spk.displayName}</b>"
                    input "fav1_${spk.id}", "capability.switch", title: "Favorite 1", required: false
                    input "fav2_${spk.id}", "capability.switch", title: "Favorite 2", required: false
                    input "fav3_${spk.id}", "capability.switch", title: "Favorite 3", required: false
                    input "fav4_${spk.id}", "capability.switch", title: "Favorite 4", required: false
                    input "fav5_${spk.id}", "capability.switch", title: "Favorite 5", required: false
                    input "fav6_${spk.id}", "capability.switch", title: "Favorite 6", required: false
                }
            }
        }
    }
}

mappings {
    path("/dashboard") { action: [ GET: "renderDashboard" ] }
    path("/js/core.js") { action: [ GET: "serveJsCore" ] }
    path("/js/render.js") { action: [ GET: "serveJsRender" ] }
    path("/js/api.js") { action: [ GET: "serveJsApi" ] }
    path("/api/data") { action: [ GET: "getData" ] }
    path("/api/status") { action: [ GET: "getStatusApi" ] }
    path("/api/events") { action: [ POST: "addEvent" ] }
    path("/api/events/remove") { action: [ POST: "removeEvent" ] }
    path("/api/users") { action: [ POST: "updateUsers" ] }
    path("/api/users/mood") { action: [ POST: "updateUserMoodApi" ] }
    path("/api/users/sick") { action: [ POST: "toggleUserSickApi" ] }
    path("/api/users/visitor") { action: [ POST: "toggleUserVisitorApi" ] }
    path("/api/groceries") { action: [ POST: "addGrocery" ] }
    path("/api/groceries/clear") { action: [ POST: "clearGroceries" ] }
    path("/api/todos") { action: [ POST: "addTodoApi" ] }
    path("/api/todos/toggle") { action: [ POST: "toggleTodoApi" ] }
    path("/api/todos/clear") { action: [ POST: "clearTodosApi" ] }
    path("/api/menu") { action: [ POST: "updateMenu" ] }
    path("/api/menu/vote") { action: [ POST: "voteMenuApi" ] }
    path("/api/notes") { action: [ POST: "addNote" ] }
    path("/api/notes/reply") { action: [ POST: "addNoteReply" ] }
    path("/api/notes/:id") { action: [ DELETE: "deleteNote" ] }
    path("/api/polls") { action: [ POST: "addPoll" ] }
    path("/api/polls/vote") { action: [ POST: "votePollApi" ] }
    path("/api/polls/:id") { action: [ DELETE: "deletePoll" ] }
    path("/api/music/control") { action: [ POST: "musicControlApi" ] }
}

def installed() {
    log.info "Installed Advanced House Management Dashboard"
    initialize()
}

def updated() {
    log.info "Updated Advanced House Management Dashboard"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (!state.events) state.events = []
    if (!state.groceries) state.groceries = []
    if (!state.todos) state.todos = []
    if (!state.menu) state.menu = [mon: "", tue: "", wed: "", thu: "", fri: "", sat: "", sun: ""]
    if (!state.menuVotes) state.menuVotes = [mon: [up:[], down:[]], tue: [up:[], down:[]], wed: [up:[], down:[]], thu: [up:[], down:[]], fri: [up:[], down:[]], sat: [up:[], down:[]], sun: [up:[], down:[]]]
    if (!state.userNotes) state.userNotes = []
    if (!state.polls) state.polls = []
    
    if (!state.notesLastWiped) state.notesLastWiped = now()
    if (!state.menuLastSaved) state.menuLastSaved = now()
    
    if (!state.users) {
        state.users = [
            [id: "u1", name: "Shane", avatar: "", disabled: false, note: "", isAdult: true, mood: "", moodTimestamp: 0],
            [id: "u2", name: "Christy", avatar: "", disabled: false, note: "", isAdult: true, mood: "", moodTimestamp: 0],
            [id: "u3", name: "User 3", avatar: "", disabled: false, note: "", isAdult: false, mood: "", moodTimestamp: 0],
            [id: "u4", name: "User 4", avatar: "", disabled: false, note: "", isAdult: false, mood: "", moodTimestamp: 0]
        ]
    } else {
        state.users.each { 
            if (!it.containsKey('isAdult')) it.isAdult = true 
            if (!it.containsKey('mood')) it.mood = ""
            if (!it.containsKey('moodTimestamp')) it.moodTimestamp = 0
        }
    }
    
    if (!state.userPresence) {
        state.userPresence = [u1: "not present", u2: "not present", u3: "not present", u4: "not present"]
    }
    
    if (!state.sickPaused) state.sickPaused = [u1: false, u2: false, u3: false, u4: false]
    if (!state.ignoreNextSickEvent) state.ignoreNextSickEvent = [u1: false, u2: false, u3: false, u4: false]
    
    if (!state.lastUpdates) {
        state.lastUpdates = [events: 0, notes: 0, menu: 0, polls: 0, todos: 0]
    } else if (!state.lastUpdates.todos) {
        state.lastUpdates.todos = 0
    }
    
    if (weatherStation) subscribe(weatherStation, "temperature", tempHandler)
    if (presenceLocks) subscribe(presenceLocks, "lock.unlocked", lockHandler)
    if (awayMode) subscribe(location, "mode", modeHandler)
    
    if (presenceU1) subscribe(presenceU1, "presence", presenceHandler)
    if (presenceU2) subscribe(presenceU2, "presence", presenceHandler)
    if (presenceU3) subscribe(presenceU3, "presence", presenceHandler)
    if (presenceU4) subscribe(presenceU4, "presence", presenceHandler)
    
    if (sickSwitchU1) subscribe(sickSwitchU1, "switch", sickSwitchHandler)
    if (sickSwitchU2) subscribe(sickSwitchU2, "switch", sickSwitchHandler)
    if (sickSwitchU3) subscribe(sickSwitchU3, "switch", sickSwitchHandler)
    if (sickSwitchU4) subscribe(sickSwitchU4, "switch", sickSwitchHandler)
    
    if (settings.moodVarU1) subscribe(location, "variable:${settings.moodVarU1}", "externalMoodHandler")
    if (settings.moodVarU2) subscribe(location, "variable:${settings.moodVarU2}", "externalMoodHandler")
    if (settings.moodVarU3) subscribe(location, "variable:${settings.moodVarU3}", "externalMoodHandler")
    if (settings.moodVarU4) subscribe(location, "variable:${settings.moodVarU4}", "externalMoodHandler")
    
    schedule("0 0 0 * * ?", resetHighLow) 
    schedule("0 * * * * ?", checkEventAlerts) 
}

def externalMoodHandler(evt) {
    syncMoodVariables()
}

def sendNotificationMsg(msg) {
    if (settings.notifyDevices) {
        def finalMsg = "Dashboard: ${msg}"
        settings.notifyDevices.each { dev ->
            try {
                if (dev.hasCommand("deviceNotification")) {
                    dev.deviceNotification(finalMsg)
                } else if (dev.hasCommand("speak")) {
                    dev.speak(finalMsg)
                } else {
                    log.warn "Device ${dev.displayName} does not support standard notification commands."
                }
            } catch (e) { log.warn "Failed to send notification to ${dev.displayName}: ${e}" }
        }
    }
}

def presenceHandler(evt) {
    def devId = evt.deviceId.toString()
    def val = evt.value?.toString()
    
    def uId = null
    if (presenceU1 && presenceU1.id == devId) uId = "u1"
    else if (presenceU2 && presenceU2.id == devId) uId = "u2"
    else if (presenceU3 && presenceU3.id == devId) uId = "u3"
    else if (presenceU4 && presenceU4.id == devId) uId = "u4"
    
    if (uId) {
        state.userPresence[uId] = val 
        def sw = settings["sickSwitch${uId.toUpperCase()}"]
        
        if (sw) {
            if (val == "not present") {
                if (sw.currentValue("switch") == "on") {
                    state.sickPaused[uId] = true
                    state.ignoreNextSickEvent[uId] = true
                    sw.off()
                    log.info "User ${uId} departed. Pausing sick status."
                } else {
                    state.sickPaused[uId] = false
                }
            } else if (val == "present") {
                if (state.sickPaused[uId] == true) {
                    state.ignoreNextSickEvent[uId] = true
                    sw.on()
                    state.sickPaused[uId] = false
                    log.info "User ${uId} arrived. Resuming sick status."
                }
            }
        }
    }
}

def sickSwitchHandler(evt) {
    def devId = evt.deviceId.toString()
    def val = evt.value?.toString()
    
    def uId = null
    if (sickSwitchU1 && sickSwitchU1.id == devId) uId = "u1"
    else if (sickSwitchU2 && sickSwitchU2.id == devId) uId = "u2"
    else if (sickSwitchU3 && sickSwitchU3.id == devId) uId = "u3"
    else if (sickSwitchU4 && sickSwitchU4.id == devId) uId = "u4"
    
    if (uId) {
        if (state.ignoreNextSickEvent[uId]) {
            state.ignoreNextSickEvent[uId] = false
            return
        }
        state.sickPaused[uId] = false
    }
}

def checkEventAlerts() {
    try {
        def now = new Date()
        def tz = location.timeZone ?: TimeZone.getDefault()
        def todayStr = now.format("yyyy-MM-dd", tz)
        def alertMins = settings.notifyEventMinutes?.toInteger() ?: 0
        
        if (!state.notifiedTracker) state.notifiedTracker = [:]
        
        def todayEvents = state.events?.findAll { e ->
            if (e.exceptions?.contains(todayStr)) return false
            if (!e.recurrence || e.recurrence == "none") return e.date == todayStr
            
            try {
                def eDate = Date.parse("yyyy-MM-dd", e.date)
                def tDate = Date.parse("yyyy-MM-dd", todayStr)
                if (tDate < eDate) return false
                if (e.recurrence == "weekly") return ((tDate.time - eDate.time) / (1000*60*60*24) % 7) == 0
                if (e.recurrence == "monthly") return eDate.date == tDate.date
                if (e.recurrence == "yearly") return eDate.month == tDate.month && eDate.date == tDate.date
            } catch(err) { return false }
            return false
        }
        
        todayEvents?.each { evt ->
            if (evt.time) {
                try {
                    def evtTimeObj = Date.parse("yyyy-MM-dd HH:mm", "${todayStr} ${evt.time}", tz)
                    def diffMins = (evtTimeObj.getTime() - now.getTime()) / 60000.0
                    
                    def trackerKey = "${evt.id}_${todayStr}"
                    if (!state.notifiedTracker[trackerKey]) state.notifiedTracker[trackerKey] = [start: false, alert: false, hour: false, five: false]
                    
                    if (settings.notifyEventStart == true || settings.notifyEventStart == "true") {
                        if (diffMins <= 0 && diffMins > -2 && !state.notifiedTracker[trackerKey].start) {
                            sendNotificationMsg("Event starting now: ${evt.title}")
                            state.notifiedTracker[trackerKey].start = true
                        }
                    }
                    
                    if (settings.notifyUpcomingEvents == true || settings.notifyUpcomingEvents == "true") {
                        if (alertMins > 0 && diffMins <= alertMins && diffMins > (alertMins - 2) && !state.notifiedTracker[trackerKey].alert) {
                            sendNotificationMsg("Upcoming event in ${alertMins} mins: ${evt.title}")
                            state.notifiedTracker[trackerKey].alert = true
                        }
                    }

                    if (settings.eventSwitch1Hour && diffMins <= 60 && diffMins > 58 && !state.notifiedTracker[trackerKey].hour) {
                        try { settings.eventSwitch1Hour.on(); runIn(4, "turnOff1HourSwitch") } catch(e){ log.error "Failed 1Hr switch: ${e}" }
                        state.notifiedTracker[trackerKey].hour = true
                    }

                    if (settings.eventSwitch5Min && diffMins <= 5 && diffMins > 3 && !state.notifiedTracker[trackerKey].five) {
                        try { settings.eventSwitch5Min.on(); runIn(4, "turnOff5MinSwitch") } catch(e){ log.error "Failed 5Min switch: ${e}" }
                        state.notifiedTracker[trackerKey].five = true
                    }
                } catch (e) { log.debug "Time parse skipped for event ${evt.title}" }
            }
        }
    } catch(e) { log.error "checkEventAlerts Error: ${e}" }
}

def turnOff1HourSwitch() { try { settings.eventSwitch1Hour?.off() } catch(e){} }
def turnOff5MinSwitch() { try { settings.eventSwitch5Min?.off() } catch(e){} }

def modeHandler(evt) {
    if (settings.awayMode && evt.value?.toString() == settings.awayMode?.toString()) {
        state.userPresence.u1 = "not present"
        state.userPresence.u2 = "not present"
        state.userPresence.u3 = "not present"
        state.userPresence.u4 = "not present"
    }
}

def lockHandler(evt) {
    if (evt.value?.toString() == "unlocked") {
        try {
            def data = evt.data ? new JsonSlurper().parseText(evt.data?.toString()) : null
            if (data && data.code) {
                def lockId = evt.deviceId.toString()
                def slot = data.code.toString()
                def matchKey = "${lockId}-${slot}"
                
                if (settings.u1LockCode?.contains(matchKey)) state.userPresence.u1 = "present"
                if (settings.u2LockCode?.contains(matchKey)) state.userPresence.u2 = "present"
                if (settings.u3LockCode?.contains(matchKey)) state.userPresence.u3 = "present"
                if (settings.u4LockCode?.contains(matchKey)) state.userPresence.u4 = "present"
            }
        } catch(e) { log.warn "Error parsing lock event data: ${e}" }
    }
}

def tempHandler(evt) {
    try {
        def currentTemp = evt.value.toString().toDouble()
        if (state.todaysHigh == null || currentTemp > state.todaysHigh) state.todaysHigh = currentTemp
        if (state.todaysLow == null || currentTemp < state.todaysLow) state.todaysLow = currentTemp
    } catch(e) { log.error "Error parsing temperature: ${e}" }
}

def resetHighLow() {
    state.todaysHigh = null
    state.todaysLow = null
    state.notifiedTracker = [:] 
    cleanupEvents()
}

def cleanupEvents() {
    def oneMonthAgo = new Date() - 30
    def cutoffStr = oneMonthAgo.format("yyyy-MM-dd")
    if (state.events) {
        state.events.removeAll { evt -> 
            (evt.recurrence == null || evt.recurrence == "none") && evt.date < cutoffStr 
        }
    }
}

def getSolarData() {
    try {
        if (location.latitude == null || location.longitude == null) return [dir: "--", isNight: false]
        double lat = location.latitude.toString().toDouble()
        double lon = location.longitude.toString().toDouble()
        
        long now = new Date().getTime()
        def tzOffset = location.timeZone ? (location.timeZone.getOffset(now) / 3600000.0) : 0.0
        
        Calendar cal = location.timeZone ? Calendar.getInstance(location.timeZone) : Calendar.getInstance()
        double day = cal.get(Calendar.DAY_OF_YEAR)
        double h = cal.get(Calendar.HOUR_OF_DAY) + (cal.get(Calendar.MINUTE) / 60.0) + (cal.get(Calendar.SECOND) / 3600.0)
        
        double b = (360.0 / 365.0) * (day - 81.0)
        double bRad = Math.toRadians(b)
        double eot = 9.87 * Math.sin(2 * bRad) - 7.53 * Math.cos(bRad) - 1.5 * Math.sin(bRad)
        
        double lst = h + (lon / 15.0) - tzOffset + (eot / 60.0)
        double ha = (lst - 12.0) * 15.0
        double decl = 23.45 * Math.sin(Math.toRadians((360.0 / 365.0) * (day - 81.0)))
        
        double haRad = Math.toRadians(ha)
        double declRad = Math.toRadians(decl)
        double latRad = Math.toRadians(lat)
        
        double sinAlt = Math.sin(declRad) * Math.sin(latRad) + Math.cos(declRad) * Math.cos(latRad) * Math.cos(haRad)
        double altRad = Math.asin(sinAlt)
        double altDegrees = Math.toDegrees(altRad)
        
        double cosAz = (Math.sin(declRad) - Math.sin(altRad) * Math.sin(latRad)) / (Math.cos(altRad) * Math.cos(latRad))
        if (cosAz > 1.0) cosAz = 1.0
        if (cosAz < -1.0) cosAz = -1.0
        
        double azRad = Math.acos(cosAz)
        double az = Math.toDegrees(azRad)
        if (Math.sin(haRad) > 0) az = 360.0 - az
        
        String[] dirs = ["N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"]
        int index = (int) Math.round((az % 360) / 45.0)
        
        return [dir: dirs[index], isNight: (altDegrees < -0.83)]
    } catch (e) {
        log.error "Solar Calculation Error: ${e}"
        return [dir: "--", isNight: false]
    }
}

def getHubVarSafe(varName) {
    if (!varName) return null
    try {
        def v = getGlobalVar(varName.toString())
        return v ? v.value : null
    } catch(e) { return null }
}

// --- API Endpoints ---

def getData() {
    syncMoodVariables()
    try {
        def resp = [
            events: state.events ?: [],
            users: state.users ?: [],
            status: getStatus()
        ]
        render contentType: "application/json", data: JsonOutput.toJson(resp)
    } catch(err) {
        log.error "getData Error: ${err}"
        render contentType: "application/json", data: JsonOutput.toJson([error: true, message: "Groovy Exception in getData: ${err.message}"])
    }
}

def getStatusApi() {
    syncMoodVariables()
    try {
        render contentType: "application/json", data: JsonOutput.toJson(getStatus())
    } catch(err) {
        log.error "getStatusApi Error: ${err}"
        render contentType: "application/json", data: JsonOutput.toJson([error: true, message: "Groovy Exception in getStatusApi: ${err.message}"])
    }
}

def getStatus() {
    try {
        def locks = []
        if (selectedLocks) {
            locks = [selectedLocks].flatten().findAll{it}.collect { [name: it.displayName?.toString(), status: it.currentValue("lock")?.toString()] }.findAll { it.status == "unlocked" }
        }
        
        def motion = []
        if (selectedMotion) {
            motion = [selectedMotion].flatten().findAll{it}.collect { [name: it.displayName?.toString(), status: it.currentValue("motion")?.toString()] }.findAll { it.status == "active" }
        }
        
        def thermostatState = null
        if (monitorThermostat) {
            def opState = monitorThermostat.currentValue("thermostatOperatingState")?.toString()
            if (opState == "heating" || opState == "cooling") {
                thermostatState = [name: monitorThermostat.displayName?.toString(), state: opState]
            }
        }
        
        def mailArrived = false
        if (mailSwitch) {
            mailArrived = (mailSwitch.currentValue("switch")?.toString() == "on")
        }
        
        def userPresence = [
            u1: presenceU1 ? presenceU1.currentValue("presence")?.toString() : (state.userPresence?.u1 ?: "not present"),
            u2: presenceU2 ? presenceU2.currentValue("presence")?.toString() : (state.userPresence?.u2 ?: "not present"),
            u3: presenceU3 ? presenceU3.currentValue("presence")?.toString() : (state.userPresence?.u3 ?: "not present"),
            u4: presenceU4 ? presenceU4.currentValue("presence")?.toString() : (state.userPresence?.u4 ?: "not present")
        ]

        def sleepScores = [
            u1: getHubVarSafe(settings.sleepVarU1),
            u2: getHubVarSafe(settings.sleepVarU2),
            u3: getHubVarSafe(settings.sleepVarU3),
            u4: getHubVarSafe(settings.sleepVarU4)
        ]
        
        def effortScores = [
            u1: getHubVarSafe(settings.effortVarU1),
            u2: getHubVarSafe(settings.effortVarU2),
            u3: getHubVarSafe(settings.effortVarU3),
            u4: getHubVarSafe(settings.effortVarU4)
        ]

        def weightScores = [
            u1: getHubVarSafe(settings.weightVarU1),
            u2: getHubVarSafe(settings.weightVarU2),
            u3: getHubVarSafe(settings.weightVarU3),
            u4: getHubVarSafe(settings.weightVarU4)
        ]
        
        def stepsScores = [
            u1: getHubVarSafe(settings.stepsVarU1),
            u2: getHubVarSafe(settings.stepsVarU2),
            u3: getHubVarSafe(settings.stepsVarU3),
            u4: getHubVarSafe(settings.stepsVarU4)
        ]

        def sickStatus = [
            u1: sickSwitchU1 ? (sickSwitchU1.currentValue("switch")?.toString() == "on" || state.sickPaused?.u1) : false,
            u2: sickSwitchU2 ? (sickSwitchU2.currentValue("switch")?.toString() == "on" || state.sickPaused?.u2) : false,
            u3: sickSwitchU3 ? (sickSwitchU3.currentValue("switch")?.toString() == "on" || state.sickPaused?.u3) : false,
            u4: sickSwitchU4 ? (sickSwitchU4.currentValue("switch")?.toString() == "on" || state.sickPaused?.u4) : false
        ]

        def visitorStatus = [
            u1: visitorSwitchU1 ? (visitorSwitchU1.currentValue("switch")?.toString() == "on") : false,
            u2: visitorSwitchU2 ? (visitorSwitchU2.currentValue("switch")?.toString() == "on") : false,
            u3: visitorSwitchU3 ? (visitorSwitchU3.currentValue("switch")?.toString() == "on") : false,
            u4: visitorSwitchU4 ? (visitorSwitchU4.currentValue("switch")?.toString() == "on") : false
        ]
        
        def warnings = []
        if (thermalLockdownSwitch && thermalLockdownSwitch.currentValue("switch")?.toString() == "on") warnings << [type: "thermal", msg: "❄️ THERMAL LOCKDOWN"]
        if (alertTornado && alertTornado.currentValue("switch")?.toString() == "on") warnings << [type: "critical", msg: "TORNADO WARNING"]
        if (alertTornadoWatch && alertTornadoWatch.currentValue("switch")?.toString() == "on") warnings << [type: "severe", msg: "TORNADO WATCH"]
        if (alertThunderstorm && alertThunderstorm.currentValue("switch")?.toString() == "on") warnings << [type: "severe", msg: "THUNDERSTORM WARNING"]
        if (alertThunderstormWatch && alertThunderstormWatch.currentValue("switch")?.toString() == "on") warnings << [type: "watch", msg: "THUNDERSTORM WATCH"]
        if (alertRain && alertRain.currentValue("switch")?.toString() == "on") warnings << [type: "info", msg: "Rain Detected"]
        if (alertSprinkler && alertSprinkler.currentValue("switch")?.toString() == "on") warnings << [type: "info", msg: "SPRINKLING"]

        def isOvercast = overcastSwitch ? overcastSwitch.currentValue("switch")?.toString() == "on" : false
        def solar = getSolarData()

        def weather = [:]
        if (weatherStation || luxDevice) {
            def curTemp = weatherStation ? weatherStation.currentValue("temperature")?.toString() : null
            if (curTemp != null) {
                try {
                    def ct = curTemp.toDouble()
                    if (state.todaysHigh == null || ct > state.todaysHigh) state.todaysHigh = ct
                    if (state.todaysLow == null || ct < state.todaysLow) state.todaysLow = ct
                } catch(e) {}
            }
            
            def luxVal = luxDevice ? luxDevice.currentValue("illuminance")?.toString() : (weatherStation ? weatherStation.currentValue("illuminance")?.toString() : null)
            
            weather = [
                temp: curTemp != null ? curTemp : "--",
                high: state.todaysHigh != null ? state.todaysHigh.toString() : "--",
                low: state.todaysLow != null ? state.todaysLow.toString() : "--",
                wind: weatherStation?.currentValue("windSpeed") != null ? weatherStation.currentValue("windSpeed").toString() : "0",
                windDir: weatherStation?.currentValue("windDirection") != null ? weatherStation.currentValue("windDirection").toString() : "",
                rain: weatherStation?.currentValue("rain") != null ? weatherStation.currentValue("rain").toString() : (weatherStation?.currentValue("dailyRain") != null ? weatherStation.currentValue("dailyRain").toString() : "0.0"),
                rainRate: weatherStation?.currentValue("rainRate") != null ? weatherStation.currentValue("rainRate").toString() : "0.0",
                lightning: weatherStation?.currentValue("lightning") != null ? weatherStation.currentValue("lightning").toString() : (weatherStation?.currentValue("lightningCount") != null ? weatherStation.currentValue("lightningCount").toString() : "0"),
                lux: luxVal != null ? luxVal : "--",
                sun: solar.dir
            ]
        }
        
        def devLights = monitorLights ? monitorLights.collect { [name: it.displayName?.toString(), status: it.currentValue("switch")?.toString()] } : []
        def devLocksAll = monitorLocks ? monitorLocks.collect { [name: it.displayName?.toString(), status: it.currentValue("lock")?.toString()] } : []
        def devContact = monitorContact ? monitorContact.collect { [name: it.displayName?.toString(), status: it.currentValue("contact")?.toString()] } : []
        def devMotionAll = monitorMotion ? monitorMotion.collect { [name: it.displayName?.toString(), status: it.currentValue("motion")?.toString()] } : []
        
        def speakersData = []
        if (sonosSpeakers) {
            sonosSpeakers.each { spk ->
                def favs = []
                (1..6).each { i ->
                    def favSwitch = settings["fav${i}_${spk.id}"]
                    if (favSwitch) {
                        favs << [index: i, name: favSwitch.displayName?.toString(), status: favSwitch.currentValue("switch")?.toString()]
                    }
                }
                speakersData << [
                    id: spk.id.toString(),
                    name: spk.displayName?.toString(),
                    status: spk.currentValue("playbackStatus")?.toString() ?: "stopped",
                    track: spk.currentValue("trackDescription")?.toString() ?: "No Track",
                    level: spk.currentValue("level")?.toString() ?: "0",
                    favorites: favs
                ]
            }
        }

        if (!state.menuLastSaved) state.menuLastSaved = now()
        if (now() - state.menuLastSaved > (7L * 24 * 60 * 60 * 1000)) {
            state.menuVotes = [mon: [up:[], down:[]], tue: [up:[], down:[]], wed: [up:[], down:[]], thu: [up:[], down:[]], fri: [up:[], down:[]], sat: [up:[], down:[]], sun: [up:[], down:[]]]
            state.menuLastSaved = now()
        }
        
        if (!state.lastUpdates) state.lastUpdates = [events: 0, notes: 0, menu: 0, polls: 0, todos: 0]

        return [
            calendarName: settings.calendarName ?: "My Calendar",
            mode: location.mode ?: "Unknown Mode",
            locks: locks,
            motion: motion,
            thermostat: thermostatState,
            mailArrived: mailArrived,
            userPresence: userPresence,
            sleepScores: sleepScores,
            effortScores: effortScores,
            weightScores: weightScores,
            stepsScores: stepsScores,
            sickStatus: sickStatus,
            visitorStatus: visitorStatus,
            warnings: warnings,
            isOvercast: isOvercast,
            isNight: solar.isNight,
            weather: weather,
            groceries: state.groceries ?: [],
            todos: state.todos ?: [],
            menu: state.menu ?: [mon: "", tue: "", wed: "", thu: "", fri: "", sat: "", sun: ""],
            menuVotes: state.menuVotes ?: [mon: [up:[], down:[]], tue: [up:[], down:[]], wed: [up:[], down:[]], thu: [up:[], down:[]], fri: [up:[], down:[]], sat: [up:[], down:[]], sun: [up:[], down:[]]],
            userNotes: state.userNotes ?: [],
            polls: state.polls ?: [],
            devices: [lights: devLights, locks: devLocksAll, contact: devContact, motion: devMotionAll],
            music: speakersData,
            lastUpdates: state.lastUpdates
        ]
    } catch(err) {
        log.error "Dashboard Status JSON Build Error: ${err}"
        return [error: true, message: "Internal Status Parsing Error"]
    }
}

def updateUserMoodApi() {
    def data = request.JSON
    def sickEmojis = ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]
    
    if (state.users) {
        def user = state.users.find { it.id == data.userId }
        if (user) {
            def oldMood = user.mood
            user.mood = data.mood
            user.moodTimestamp = now()
            
            def uIdx = data.userId.toString().replace("u", "").toUpperCase()
            
            // NEW: Auto-enable Sick Switch if mood changes TO a sick emoji
            if (sickEmojis.contains(user.mood) && !sickEmojis.contains(oldMood)) {
                def sw = settings["sickSwitchU${uIdx}"]
                if (sw && sw.currentValue("switch") != "on") {
                    log.info "Dashboard mood set to sick (${user.mood}) for ${data.userId}. Turning on sick switch."
                    try { sw.on() } catch(e) { log.error "Failed to turn on sick switch: ${e}" }
                }
            }
            
            // Sync Mood to Hub Variable so Iron AI can read it natively
            def moodVarName = settings["moodVarU${uIdx}"]
            if (moodVarName) {
                setGlobalVar(moodVarName.toString(), data.mood)
                log.info "Mood synced to Hub Variable for ${data.userId}: ${data.mood}"
            }
            
            return render(contentType: "application/json", data: JsonOutput.toJson([status: "success"]))
        }
    }
    render contentType: "application/json", data: JsonOutput.toJson([status: "error"])
}

def toggleUserSickApi() {
    def data = request.JSON
    if (data && data.userId) {
        def uId = data.userId.toString().toLowerCase()
        def sw = settings["sickSwitch${uId.toUpperCase()}"]
        if (sw) {
            if (data.state == true || data.state == "true") {
                sw.on()
            } else {
                sw.off()
            }
        }
    }
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def toggleUserVisitorApi() {
    def data = request.JSON
    if (data && data.userId) {
        def uId = data.userId.toString().toLowerCase()
        def sw = settings["visitorSwitch${uId.toUpperCase()}"]
        if (sw) {
            if (data.state == true || data.state == "true") {
                sw.on()
            } else {
                sw.off()
            }
        }
    }
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}


def addEvent() {
    def data = request.JSON
    if (!state.events) state.events = []
    
    if (data.id && data.id != "") {
        def evt = state.events.find { it.id == data.id }
        if (evt) {
            evt.title = data.title
            evt.date = data.date
            evt.time = data.time ?: ""
            evt.type = data.type ?: "event"
            evt.userId = data.userId ?: "u1"
            evt.description = data.description ?: ""
            evt.location = data.location ?: ""
            evt.recurrence = data.recurrence ?: "none"
            evt.isCountdown = data.isCountdown ?: false
        }
        state.lastUpdates.events = now()
        render contentType: "application/json", data: JsonOutput.toJson([status: "success", event: evt])
    } else {
        def newEvent = [
            id: "evt_${now()}",
            title: data.title,
            date: data.date,
            time: data.time ?: "",
            type: data.type ?: "event",
            userId: data.userId ?: "u1",
            description: data.description ?: "",
            location: data.location ?: "",
            recurrence: data.recurrence ?: "none",
            isCountdown: data.isCountdown ?: false,
            exceptions: []
        ]
        state.events << newEvent
        state.lastUpdates.events = now()
        if (settings.notifyNewEvents == true || settings.notifyNewEvents == "true") sendNotificationMsg("New Calendar Event Added: ${data.title}")
        render contentType: "application/json", data: JsonOutput.toJson([status: "success", event: newEvent])
    }
}

def removeEvent() {
    def data = request.JSON
    def id = data.id
    def deleteType = data.deleteType ?: 'all'
    def instanceDate = data.instanceDate
    
    if (state.events) {
        def tempEvents = state.events
        if (deleteType == 'single' && instanceDate) {
            def evt = tempEvents.find { it.id == id }
            if (evt) {
                if (!evt.exceptions) evt.exceptions = []
                if (!evt.exceptions.contains(instanceDate)) {
                    evt.exceptions << instanceDate
                }
            }
        } else {
            tempEvents.removeAll { it.id == id }
        }
        state.events = tempEvents
    }
    state.lastUpdates.events = now()
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def updateUsers() {
    def data = request.JSON
    state.users = data
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def updateMenu() {
    state.menu = request.JSON
    state.menuVotes = [mon: [up:[], down:[]], tue: [up:[], down:[]], wed: [up:[], down:[]], thu: [up:[], down:[]], fri: [up:[], down:[]], sat: [up:[], down:[]], sun: [up:[], down:[]]]
    state.menuLastSaved = now()
    state.lastUpdates.menu = now()
    if (settings.notifyNewMeals == true || settings.notifyNewMeals == "true") sendNotificationMsg("The Weekly Menu has been updated")
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def voteMenuApi() {
    def data = request.JSON
    if (!state.menuVotes) state.menuVotes = [mon: [up:[], down:[]], tue: [up:[], down:[]], wed: [up:[], down:[]], thu: [up:[], down:[]], fri: [up:[], down:[]], sat: [up:[], down:[]], sun: [up:[], down:[]]]
    
    def dayVotes = state.menuVotes[data.day]
    if (dayVotes != null) {
        dayVotes.up.remove(data.userId)
        dayVotes.down.remove(data.userId)
        
        if (data.type == "up") dayVotes.up << data.userId
        else if (data.type == "down") dayVotes.down << data.userId
    }
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def enforceNoteLimits() {
    if (!state.notesLastWiped) state.notesLastWiped = now()
    if (now() - state.notesLastWiped > (7L * 24 * 60 * 60 * 1000)) {
        state.userNotes = []
        state.notesLastWiped = now()
    }
    
    while(true) {
        int count = 0
        state.userNotes.each { n ->
            count++
            if (n.replies) count += n.replies.size()
        }
        if (count > 20 && state.userNotes.size() > 0) {
            state.userNotes.remove(0)
        } else {
            break
        }
    }
}

def addNote() {
    def data = request.JSON
    def currentNotes = state.userNotes ?: []
    currentNotes << [
        id: "note_${now()}", 
        text: data.text, 
        authorId: data.authorId, 
        timestamp: new Date().getTime(),
        dateStr: new Date().format("MMM d, h:mm a"),
        replies: []
    ]
    state.userNotes = currentNotes
    enforceNoteLimits()
    state.lastUpdates.notes = now()
    if (settings.notifyNewMessages == true || settings.notifyNewMessages == "true") sendNotificationMsg("A new message was posted to the board")
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def addNoteReply() {
    def data = request.JSON
    if (!state.userNotes) state.userNotes = []
    def note = state.userNotes.find { it.id == data.noteId }
    if (note) {
        if (!note.replies) note.replies = []
        note.replies << [
            id: "reply_${now()}",
            text: data.text,
            authorId: data.authorId,
            timestamp: new Date().getTime(),
            dateStr: new Date().format("MMM d, h:mm a")
        ]
    }
    enforceNoteLimits()
    state.lastUpdates.notes = now()
    if (settings.notifyNewMessages == true || settings.notifyNewMessages == "true") sendNotificationMsg("Someone replied to a message on the board")
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def deleteNote() {
    def id = params.id
    if (state.userNotes) state.userNotes.removeAll { it.id == id }
    state.lastUpdates.notes = now()
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def addGrocery() {
    def data = request.JSON
    if (!state.groceries) state.groceries = []
    
    if (data.action == "toggle") {
        def item = state.groceries.find { it.id == data.id }
        if (item) item.checked = data.checked
        render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
        return
    }
    
    def newItem = [
        id: "groc_${now()}",
        name: data.name,
        store: data.store ?: "Any",
        addedBy: data.userId ?: "u1",
        checked: false
    ]
    state.groceries << newItem
    render contentType: "application/json", data: JsonOutput.toJson([status: "success", item: newItem])
}

def clearGroceries() {
    def data = request.JSON
    if (!state.groceries) state.groceries = []
    
    if (data.action == "checked") {
        state.groceries.removeAll { it.checked == true }
    } else if (data.action == "all") {
        state.groceries = []
    }
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def addTodoApi() {
    def data = request.JSON
    if (!state.todos) state.todos = []
    def newItem = [
        id: "todo_${now()}",
        text: data.text,
        userId: data.userId,
        checked: false,
        timestamp: now()
    ]
    state.todos << newItem
    state.lastUpdates.todos = now()
    render contentType: "application/json", data: JsonOutput.toJson([status: "success", item: newItem])
}

def toggleTodoApi() {
    def data = request.JSON
    if (!state.todos) state.todos = []
    def item = state.todos.find { it.id == data.id }
    if (item) item.checked = data.checked
    state.lastUpdates.todos = now()
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def clearTodosApi() {
    def data = request.JSON
    if (!state.todos) state.todos = []
    if (data.userId) {
        state.todos.removeAll { it.userId == data.userId && it.checked == true }
    } else {
        state.todos.removeAll { it.checked == true }
    }
    state.lastUpdates.todos = now()
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def addPoll() {
    def data = request.JSON
    if (!state.polls) state.polls = []
    if (state.polls.size() >= 2) {
        return render(contentType: "application/json", data: JsonOutput.toJson([status: "error", message: "Max 2 active polls allowed."]))
    }
    
    def formattedOptions = []
    data.options.eachWithIndex { optText, idx ->
        if (optText && optText.trim() != "") {
            formattedOptions << [id: "opt_${idx}", text: optText, votes: []]
        }
    }
    
    state.polls << [
        id: "poll_${now()}",
        question: data.question,
        options: formattedOptions,
        authorId: data.authorId,
        timestamp: new Date().getTime()
    ]
    state.lastUpdates.polls = now()
    if (settings.notifyNewPolls == true || settings.notifyNewPolls == "true") sendNotificationMsg("New Poll Created: ${data.question}")
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def votePollApi() {
    def data = request.JSON
    if (!state.polls) state.polls = []
    def poll = state.polls.find { it.id == data.pollId }
    if (poll) {
        poll.options.each { it.votes.removeAll { v -> v == data.userId } }
        def opt = poll.options.find { it.id == data.optionId }
        if (opt) opt.votes << data.userId
    }
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def deletePoll() {
    def id = params.id
    if (state.polls) state.polls.removeAll { it.id == id }
    state.lastUpdates.polls = now()
    render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
}

def musicControlApi() {
    def data = request.JSON
    def action = data.action
    def spkId = data.spkId
    
    try {
        log.info "Executing music action: ${action} for speaker ${spkId}"
        if (action == "favorite") {
            def favSwitch = settings["fav${data.favIndex}_${spkId}"]
            if (favSwitch) favSwitch.on()
        } else {
            def spk = sonosSpeakers?.find { it.id.toString() == spkId.toString() }
            if (spk) {
                if (action == "play") spk.play()
                else if (action == "pause") spk.pause()
                else if (action == "setLevel") spk.setLevel(data.level as Integer)
            }
        }
        render contentType: "application/json", data: JsonOutput.toJson([status: "success"])
    } catch(e) {
        log.error "Music API Error: ${e}"
        render contentType: "application/json", data: JsonOutput.toJson([status: "error", message: e.message])
    }
}

// ============================================================================
// FRONTEND RENDERING & CHUNKED JAVASCRIPT
// ============================================================================

def renderDashboard() {
    def token = params.access_token ?: state.accessToken
    def html = new StringBuilder()
    html << getHtmlHead()
    html << getHtmlLayout()
    html << getHtmlViews1()
    html << getHtmlViews2()
    html << getHtmlViews3()
    html << getHtmlModals1()
    html << getHtmlModals2()
    
    html << "<script src=\"js/core.js?access_token=${token}\"></script>\n"
    html << "<script src=\"js/render.js?access_token=${token}\"></script>\n"
    html << "<script src=\"js/api.js?access_token=${token}\"></script>\n"
    html << "</body></html>"
    
    render contentType: "text/html", data: html.toString()
}

def serveJsCore() {
    render contentType: "application/javascript", data: getJsInit()
}

def serveJsRender() {
    def js = new StringBuilder()
    js << getJsRendering1()
    js << getJsRendering2()
    js << getJsRendering3()
    js << getJsRendering4()
    render contentType: "application/javascript", data: js.toString()
}

def serveJsApi() {
    def js = new StringBuilder()
    js << getJsApi1()
    js << getJsApi2()
    js << getJsApi3()
    render contentType: "application/javascript", data: js.toString()
}

def getHtmlHead() {
    return '''
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <link rel="icon" href="data:,">
    <title>Advanced House Management</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body, header, main, footer, .calendar-cell, .calendar-header { transition: background-color 0.4s, border-color 0.4s, color 0.4s; }
        .calendar-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 1px; border-radius: 0.5rem; overflow: hidden; }
        .calendar-cell { min-height: 85px; padding: 0.35rem; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }
        .calendar-header { text-align: center; font-weight: 600; padding: 0.5rem 0; font-size: 0.75rem; text-transform: uppercase; }
        .event-badge { font-size: 0.7rem; padding: 0.25rem 0.35rem; border-radius: 0.375rem; margin-top: 3px; display: flex; align-items: center; gap: 4px; cursor: pointer; transition: opacity 0.2s; box-shadow: 0 1px 2px rgba(0,0,0,0.05); max-width: 100%; }
        .event-badge:hover { opacity: 0.8; }
        .event-type-event { background-color: #3b82f6; color: white; }
        .event-type-birthday { background-color: #ec4899; color: white; }
        .avatar-sm { width: 16px; height: 16px; border-radius: 50%; object-fit: cover; background-color: rgba(255,255,255,0.3); }
        ::-webkit-scrollbar { display: none; }
        .hide-scrollbar::-webkit-scrollbar { display: none; }
        @media (max-width: 767px) {
            .ticker-wrap { display: flex; overflow: hidden; width: 100%; white-space: nowrap; }
            .ticker-content { display: inline-flex; animation: ticker 35s linear infinite; }
            .ticker-content:hover { animation-play-state: paused; }
            @keyframes ticker { 0% { transform: translate3d(0, 0, 0); } 100% { transform: translate3d(-50%, 0, 0); } }
        }
        @media (min-width: 768px) {
            .ticker-content > div:nth-child(2) { display: none; }
            .ticker-content { width: 100%; justify-content: center; display: flex; }
            .calendar-cell { min-height: 100px; padding: 0.5rem; }
            .event-badge { font-size: 0.8rem; padding: 0.35rem 0.5rem; }
        }
    </style>
</head>
<body id="themeBody" class="fixed inset-0 flex flex-col overflow-hidden text-gray-800 bg-gray-100">
    <div id="sleepOverlay" class="fixed inset-0 bg-black z-[100] hidden cursor-pointer transition-opacity duration-700"></div>
    <div id="screensaverOverlay" class="fixed inset-0 bg-gray-950 text-white z-[90] hidden cursor-pointer flex flex-col p-6 sm:p-12 transition-opacity duration-700 overflow-hidden">
        <div class="flex justify-between items-start shrink-0">
            <div><div id="ssTime" class="text-6xl sm:text-8xl font-light tracking-tight">--:--</div><div id="ssDate" class="text-xl sm:text-3xl text-gray-400 font-medium mt-2">--</div></div>
            <div id="ssWeather" class="text-right"></div>
        </div>
        <div class="mt-8 sm:mt-12 flex-1 grid grid-cols-1 sm:grid-cols-3 gap-6 sm:gap-10 min-h-0">
            <div class="flex flex-col min-h-0">
                <h2 class="text-sm font-bold text-gray-500 uppercase tracking-widest border-b border-gray-800 pb-2 mb-4 shrink-0 flex items-center"><span class="mr-2 text-xl">📅</span> Upcoming</h2>
                <div id="ssEvents" class="space-y-4 overflow-y-auto hide-scrollbar flex-1 pb-10"></div>
            </div>
            <div class="flex flex-col min-h-0">
                <h2 class="text-sm font-bold text-gray-500 uppercase tracking-widest border-b border-gray-800 pb-2 mb-4 shrink-0 flex items-center"><span class="mr-2 text-xl">🛡️</span> Security & Devices</h2>
                <div id="ssDevices" class="space-y-3 overflow-y-auto hide-scrollbar flex-1 pb-10"></div>
            </div>
            <div class="flex flex-col min-h-0">
                <h2 class="text-sm font-bold text-gray-500 uppercase tracking-widest border-b border-gray-800 pb-2 mb-4 shrink-0 flex items-center"><span class="mr-2 text-xl">💬</span> Message Board</h2>
                <div id="ssNotes" class="space-y-3 overflow-y-auto hide-scrollbar flex-1 pb-10"></div>
            </div>
        </div>
    </div>
    <div id="warningBanner" class="hidden flex-col z-30 shrink-0 w-full shadow-lg"></div>
'''
}

def getHtmlLayout() {
    return '''
    <header id="themeHeader" class="bg-white shadow-sm z-20 shrink-0 flex flex-col border-b border-gray-200">
        <div class="flex justify-between items-center px-4 py-3">
            <div id="headerTitle" class="truncate leading-tight flex-1 min-w-0 mr-2">Loading...</div>
            <div id="userFilters" class="flex items-center gap-1.5 sm:gap-2 shrink-0 pt-1 pl-1"></div>
        </div>
        <div id="themeToolbar" class="flex justify-between items-center px-4 py-2 bg-gray-50 border-t border-gray-100">
            <div class="flex items-center space-x-2">
                <button onclick="openSettingsModal()" id="themeSettingsBtn" class="p-2 bg-white shadow-sm rounded-full text-gray-600 hover:bg-gray-100 transition" title="Users & Notes">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                </button>
                <button onclick="handleMoodBtnClick()" id="themeMoodBtn" class="p-2 bg-white shadow-sm rounded-full text-gray-600 hover:bg-gray-100 transition flex items-center justify-center" title="Set Mood">
                    <span class="text-lg leading-none mt-[1px]">😀</span>
                </button>
            </div>
            <div id="calendarControls" class="flex space-x-1 sm:space-x-2 items-center">
                <button onclick="prevMonth()" id="themePrevBtn" class="p-2 bg-white shadow-sm rounded-full text-gray-600 hover:bg-gray-100 transition"><svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path></svg></button>
                <button onclick="nextMonth()" id="themeNextBtn" class="p-2 bg-white shadow-sm rounded-full text-gray-600 hover:bg-gray-100 transition"><svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path></svg></button>
                <button onclick="openEventModal()" id="btnNewContext" class="px-3 sm:px-4 py-1.5 sm:py-2 bg-indigo-600 text-white rounded-lg text-sm font-semibold shadow hover:bg-indigo-700 transition">+ New</button>
            </div>
        </div>
        <div id="themeTabsRow" class="flex justify-center items-center px-2 py-2 bg-gray-50 border-t border-gray-100 w-full overflow-x-auto hide-scrollbar">
            <div class="flex p-1 bg-gray-200/60 rounded-lg w-full max-w-3xl min-w-[300px] space-x-1 overflow-x-auto hide-scrollbar" id="viewTabsBg">
                <button onclick="switchView('calendar')" id="tabCalendar" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center bg-white shadow-sm rounded-md text-indigo-600 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"></path></svg><span id="dot_calendar" class="hidden absolute top-1 right-2 w-2.5 h-2.5 bg-red-500 rounded-full shadow-sm"></span></button>
                <button onclick="switchView('health')" id="tabHealth" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center text-gray-500 hover:text-gray-700 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"></path></svg></button>
                <button onclick="switchView('grocery')" id="tabGrocery" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center text-gray-500 hover:text-gray-700 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63-.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"></path></svg></button>
                <button onclick="switchView('menu')" id="tabMenu" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center text-gray-500 hover:text-gray-700 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"></path></svg><span id="dot_menu" class="hidden absolute top-1 right-2 w-2.5 h-2.5 bg-red-500 rounded-full shadow-sm"></span></button>
                <button onclick="switchView('notes')" id="tabNotes" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center text-gray-500 hover:text-gray-700 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"></path></svg><span id="dot_notes" class="hidden absolute top-1 right-2 w-2.5 h-2.5 bg-red-500 rounded-full shadow-sm"></span></button>
                <button onclick="switchView('polls')" id="tabPolls" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center text-gray-500 hover:text-gray-700 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"></path></svg><span id="dot_polls" class="hidden absolute top-1 right-2 w-2.5 h-2.5 bg-red-500 rounded-full shadow-sm"></span></button>
                
                <button onclick="switchView('todos')" id="tabTodos" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center text-gray-500 hover:text-gray-700 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg><span id="dot_todos" class="hidden absolute top-1 right-2 w-2.5 h-2.5 bg-red-500 rounded-full shadow-sm"></span></button>

                <button onclick="switchView('devices')" id="tabDevices" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center text-gray-500 hover:text-gray-700 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"></path></svg></button>
                <button onclick="switchView('music')" id="tabMusic" class="relative min-w-[50px] flex-1 py-2 flex justify-center items-center text-gray-500 hover:text-gray-700 transition-all"><svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3"></path></svg></button>
            </div>
        </div>
        <div id="deviceBanner" class="hidden flex space-x-2 overflow-x-auto py-1 hide-scrollbar bg-gray-50 border-t border-gray-100 px-4"></div>
    </header>
'''
}

def getHtmlViews1() {
    return '''
    <main id="calendarMain" class="flex-1 min-h-0 overflow-y-auto p-2 sm:p-4 z-10 w-full block sm:flex sm:flex-col">
        <div class="flex flex-col sm:flex-row gap-3 sm:gap-4 mb-4 shrink-0">
            <div id="cardEvents" class="flex-1 bg-white border border-gray-200 rounded-xl p-3 shadow-sm flex flex-col min-h-[120px] sm:min-h-[140px]">
                <h3 id="eventsCardHeader" class="text-xs font-bold text-gray-400 uppercase tracking-wide border-b border-gray-100 pb-2 mb-2 shrink-0 flex justify-between items-center">
                    <span id="eventsCardTitle">Today's Events</span>
                    <button onclick="toggleAllEvents()" id="eventsCardToggle" class="text-indigo-500 hover:text-indigo-600 focus:outline-none normal-case text-[10px]">View All</button>
                </h3>
                <div id="todaysEventsCard" class="space-y-2 overflow-y-auto hide-scrollbar flex-1 min-h-0 touch-pan-y"></div>
            </div>
            <div id="cardCountdowns" class="flex-1 bg-white border border-gray-200 rounded-xl p-3 shadow-sm flex flex-col min-h-[120px] sm:min-h-[140px] hidden">
                <h3 id="countdownCardHeader" class="text-xs font-bold text-indigo-400 uppercase tracking-wide border-b border-gray-100 pb-2 mb-2 shrink-0 flex justify-between items-center">
                    <span id="countdownCardTitle">Countdowns</span>
                </h3>
                <div id="countdownsCardContent" class="space-y-2 overflow-y-auto hide-scrollbar flex-1 min-h-0 touch-pan-y"></div>
            </div>
        </div>
        <div id="calendarGridWrapper" class="calendar-grid shadow-sm bg-gray-200 shrink-0">
            <div class="calendar-header bg-gray-50 text-gray-500" id="ch0">Sun</div><div class="calendar-header bg-gray-50 text-gray-500" id="ch1">Mon</div><div class="calendar-header bg-gray-50 text-gray-500" id="ch2">Tue</div><div class="calendar-header bg-gray-50 text-gray-500" id="ch3">Wed</div><div class="calendar-header bg-gray-50 text-gray-500" id="ch4">Thu</div><div class="calendar-header bg-gray-50 text-gray-500" id="ch5">Fri</div><div class="calendar-header bg-gray-50 text-gray-500" id="ch6">Sat</div>
        </div>
        <div id="calendarDays" class="calendar-grid shadow-sm mb-4 bg-gray-200 sm:flex-1 sm:min-h-[400px]" style="grid-auto-rows: minmax(85px, 1fr);"></div>
    </main>
    
    <main id="groceryMain" class="flex-1 min-h-0 hidden flex flex-col z-10 w-full relative">
        <div class="px-4 py-3 shrink-0 flex space-x-2 overflow-x-auto hide-scrollbar border-b border-gray-200 bg-white" id="grocFilterBar"><div id="groceryStoreFilters" class="flex space-x-2"></div></div>
        <div class="mx-4 mt-3 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border bg-white" id="grocAddCard">
            <div class="flex gap-2">
                <input type="text" id="grocName" class="flex-1 p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800" placeholder="Add grocery item...">
                <button onclick="submitGrocery()" class="px-5 py-2 bg-indigo-600 text-white rounded-lg font-bold text-lg shadow-sm leading-none">+</button>
            </div>
            <div class="flex gap-2">
                <select id="grocStore" class="flex-1 p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800">
                    <option value="Any">Any Store</option><option value="Aldi">Aldi</option><option value="Publix">Publix</option><option value="Costco">Costco</option><option value="Walmart">Walmart</option><option value="Farmers Market">Farmers Market</option>
                </select>
                <select id="grocUser" class="flex-1 p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800"></select>
            </div>
        </div>
        <div id="groceryList" class="flex-1 min-h-0 overflow-y-auto px-4 py-3 space-y-2 pb-24 overscroll-contain touch-pan-y"></div>
        <div class="px-4 py-3 shrink-0 flex justify-between border-t z-20 bg-white w-full" id="grocActionBar">
            <button onclick="clearGroceries('checked')" class="text-sm font-semibold text-gray-600 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition" id="btnGrocClearChk">Clear Checked</button>
            <button onclick="clearGroceries('all')" class="text-sm font-semibold text-red-600 px-4 py-2 border border-red-200 bg-red-50 rounded-lg hover:bg-red-100 transition" id="btnGrocClearAll">Clear All</button>
        </div>
    </main>
'''
}

def getHtmlViews2() {
    return '''
    <main id="todosMain" class="flex-1 min-h-0 hidden flex flex-col z-10 w-full relative">
        <div class="mx-4 mt-3 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border bg-white" id="todoAddCard">
            <div class="flex gap-2">
                <input type="text" id="todoName" class="flex-1 p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800" placeholder="Select a user to add a task...">
                <button onclick="submitTodo()" class="px-5 py-2 bg-indigo-600 text-white rounded-lg font-bold text-lg shadow-sm leading-none">+</button>
            </div>
        </div>
        <div id="todoList" class="flex-1 min-h-0 overflow-y-auto px-4 py-3 space-y-2 pb-24 overscroll-contain touch-pan-y"></div>
        <div class="px-4 py-3 shrink-0 flex justify-between border-t z-20 bg-white w-full" id="todoActionBar">
            <button onclick="clearTodos('checked')" class="text-sm font-semibold text-gray-600 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition" id="btnTodoClearChk">Clear Checked</button>
        </div>
    </main>

    <main id="menuMain" class="flex-1 min-h-0 overflow-y-auto hidden flex flex-col z-10 w-full relative p-4 space-y-3 pb-20 overscroll-contain touch-pan-y">
        <div class="flex items-center gap-2 p-3 bg-gray-50 rounded-xl border border-gray-200 shrink-0" id="menuActorBar">
            <span class="text-sm font-bold text-gray-700" id="menuActorLbl">Acting As:</span>
            <select id="menuActor" class="flex-1 p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-white border-gray-300 text-gray-800"></select>
        </div>
        <div class="bg-white rounded-xl shadow-sm border p-4 space-y-4 shrink-0" id="menuContainer"></div>
        <button id="btnSaveMenu" onclick="saveMenu()" class="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-bold shadow transition text-lg shrink-0">Save Weekly Menu</button>
    </main>

    <main id="pollsMain" class="flex-1 min-h-0 overflow-y-auto hidden flex flex-col z-10 w-full relative p-4 space-y-4 pb-20 overscroll-contain touch-pan-y">
        <div id="pollsList" class="space-y-4 shrink-0"></div>
        <div id="pollsAddCard" class="bg-white rounded-xl shadow-sm border p-4 shrink-0">
            <h3 class="font-bold text-gray-700 mb-3" id="pollsAddHeader">Create New Poll (Max 2)</h3>
            <input type="text" id="pollQuestion" placeholder="Question..." class="w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800 mb-2">
            <div class="grid grid-cols-2 gap-2 mb-2">
                <input type="text" id="pollOpt1" placeholder="Option 1" class="w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800">
                <input type="text" id="pollOpt2" placeholder="Option 2" class="w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800">
            </div>
            <div class="grid grid-cols-2 gap-2 mb-3">
                <input type="text" id="pollOpt3" placeholder="Option 3 (Optional)" class="w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800">
                <input type="text" id="pollOpt4" placeholder="Option 4 (Optional)" class="w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800">
            </div>
            <div class="flex gap-2">
                <select id="pollAuthor" class="flex-1 p-2 rounded-lg text-sm border bg-gray-50 border-gray-300 text-gray-800"></select>
                <button onclick="submitPoll()" class="px-6 py-2 bg-indigo-600 text-white rounded-lg font-bold text-sm shadow-sm transition hover:bg-indigo-700">Create</button>
            </div>
        </div>
    </main>

    <main id="notesMain" class="flex-1 min-h-0 hidden flex flex-col z-10 w-full relative">
        <div class="mx-4 mt-4 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border bg-white" id="notesAddCard">
            <textarea id="newNoteText" class="w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800" placeholder="Type a message or note..." rows="2"></textarea>
            <div class="flex justify-between items-center mt-1">
                <select id="noteAuthor" class="p-2 rounded-lg text-sm border bg-gray-50 border-gray-300 text-gray-800"></select>
                <button onclick="submitNote()" class="px-6 py-2 bg-indigo-600 text-white rounded-lg font-bold text-sm shadow-sm transition hover:bg-indigo-700">Post Note</button>
            </div>
        </div>
        <div id="notesList" class="flex-1 min-h-0 overflow-y-auto px-4 py-4 space-y-4 overscroll-contain touch-pan-y pb-20"></div>
    </main>
'''
}

def getHtmlViews3() {
    return '''
    <main id="healthMain" class="flex-1 overflow-y-auto hidden flex flex-col z-10 w-full relative p-4 space-y-4 pb-20 min-h-0">
        <div id="healthContent" class="space-y-4 shrink-0"></div>
    </main>

    <main id="devicesMain" class="flex-1 overflow-y-auto hidden flex flex-col z-10 w-full relative p-4 space-y-4 pb-20 min-h-0">
        <div id="devicesList" class="space-y-6 shrink-0"></div>
    </main>

    <main id="musicMain" class="flex-1 overflow-y-auto hidden flex flex-col z-10 w-full relative p-4 space-y-4 pb-20 min-h-0">
        <div id="musicContent" class="mt-2 shrink-0"></div>
    </main>

    <footer id="weatherFooter" class="hidden bg-slate-900 text-white py-2.5 shrink-0 z-20 font-medium ticker-wrap border-t-2 border-indigo-500"></footer>

    <button id="fabToggleLayout" onclick="toggleMaximize()" class="fixed bottom-20 right-6 z-[95] bg-indigo-600 text-white p-3.5 rounded-full shadow-[0_8px_20px_rgba(0,0,0,0.4)] hidden md:hidden opacity-90 hover:opacity-100 transition-all transform hover:scale-105 active:scale-95">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path id="fabMaxIcon" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 11.25h-4.5m4.5 0v-4.5m0 4.5L15 15"></path>
        </svg>
    </button>
'''
}

def getHtmlModals1() {
    return '''
    <div id="eventModal" class="fixed inset-0 bg-gray-900 bg-opacity-40 backdrop-blur-sm hidden z-50 flex justify-center items-end sm:items-center">
        <div class="bg-white rounded-t-2xl sm:rounded-2xl shadow-xl w-full max-w-md p-6" id="mdlEventBg">
            <h2 class="text-xl font-bold mb-4 text-gray-900" id="eventModalTitle">Add Event</h2>
            <form onsubmit="submitEvent(event)">
                <input type="hidden" id="eventId">
                <div class="mb-3">
                    <label class="block text-sm font-semibold text-gray-500 mb-1">Title</label>
                    <input type="text" id="eventTitle" required class="w-full border-gray-300 rounded-lg p-2 border bg-gray-50 focus:ring-2 focus:ring-indigo-500 text-gray-800">
                </div>
                <div class="mb-3 grid grid-cols-2 gap-3">
                    <div><label class="block text-sm font-semibold text-gray-500 mb-1">Date</label><input type="date" id="eventDate" required class="w-full border-gray-300 rounded-lg p-2 border bg-gray-50 text-gray-800"></div>
                    <div><label class="block text-sm font-semibold text-gray-500 mb-1">Time (Optional)</label><input type="time" id="eventTime" class="w-full border-gray-300 rounded-lg p-2 border bg-gray-50 text-gray-800"></div>
                </div>
                <div class="mb-3 grid grid-cols-2 gap-3">
                    <div><label class="block text-sm font-semibold text-gray-500 mb-1">Type</label><select id="eventType" class="w-full border-gray-300 rounded-lg p-2 border bg-gray-50 text-gray-800"><option value="event">Event</option><option value="birthday">Birthday</option></select></div>
                    <div><label class="block text-sm font-semibold text-gray-500 mb-1">Assigned To</label><select id="eventUserId" class="w-full border-gray-300 rounded-lg p-2 border bg-gray-50 text-gray-800"></select></div>
                </div>
                <div class="mb-3 grid grid-cols-2 gap-3">
                    <div><label class="block text-sm font-semibold text-gray-500 mb-1">Recurrence</label><select id="eventRecurrence" class="w-full border-gray-300 rounded-lg p-2 border bg-gray-50 text-gray-800"><option value="none">Does not repeat</option><option value="weekly">Every Week</option><option value="monthly">Every Month</option><option value="yearly">Every Year</option></select></div>
                    <div><label class="block text-sm font-semibold text-gray-500 mb-1">Location</label><input type="text" id="eventLocation" placeholder="Address..." class="w-full border-gray-300 rounded-lg p-2 border bg-gray-50 text-gray-800"></div>
                </div>
                <div class="mb-4"><label class="block text-sm font-semibold text-gray-500 mb-1">Description</label><textarea id="eventDesc" rows="2" class="w-full border-gray-300 rounded-lg p-2 border bg-gray-50 text-gray-800"></textarea></div>
                <div class="mb-4 flex items-center space-x-2 border-t pt-3 border-gray-200 dark:border-gray-700" id="evtCountdownGroup">
                    <input type="checkbox" id="eventIsCountdown" class="w-4 h-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500">
                    <label class="text-sm font-semibold text-gray-700 dark:text-gray-300">Display as Dashboard Countdown</label>
                </div>
                <div class="flex justify-end space-x-3 pt-2 border-t border-gray-100 dark:border-gray-800">
                    <button type="button" onclick="closeEventModal()" class="px-5 py-2 bg-white border border-gray-300 rounded-lg text-sm font-semibold text-gray-700" id="btnEvtCan">Cancel</button>
                    <button type="submit" id="submitEventBtn" class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-semibold">Save Event</button>
                </div>
            </form>
        </div>
    </div>

    <div id="viewEventModal" class="fixed inset-0 bg-gray-900 bg-opacity-40 backdrop-blur-sm hidden z-50 flex justify-center items-center">
        <div class="bg-white rounded-2xl shadow-xl w-full max-w-sm p-6 relative" id="mdlViewBg">
            <button onclick="closeViewModal()" class="absolute top-4 right-4 text-gray-400 hover:text-gray-600"><svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg></button>
            <div id="viewEventContent" class="mb-6"></div>
            <div class="flex justify-between border-t border-gray-100 pt-4">
                <div>
                    <button id="deleteEventBtn" class="px-4 py-2 bg-red-50 text-red-600 rounded-lg text-sm font-semibold hover:bg-red-100 transition mr-2">Delete</button>
                    <div id="deleteRecurGroup" class="hidden inline-flex space-x-2 mr-2">
                        <button id="deleteSingleBtn" class="px-3 py-2 bg-red-50 text-red-600 rounded-lg text-xs font-semibold hover:bg-red-100 transition">Delete This</button>
                        <button id="deleteAllBtn" class="px-3 py-2 bg-red-100 text-red-700 rounded-lg text-xs font-bold hover:bg-red-200 transition">Delete All</button>
                    </div>
                </div>
                <div>
                    <button id="editEventBtn" class="px-4 py-2 bg-indigo-50 text-indigo-600 rounded-lg text-sm font-semibold hover:bg-indigo-100 transition mr-2">Edit</button>
                    <button onclick="closeViewModal()" class="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg text-sm font-semibold hover:bg-gray-200 transition" id="btnViewClose">Close</button>
                </div>
            </div>
        </div>
    </div>

    <div id="settingsModal" class="fixed inset-0 bg-gray-900 bg-opacity-40 backdrop-blur-sm hidden z-50 flex justify-center items-center">
        <div class="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto" id="mdlSettingsBg">
            <h2 class="text-xl font-bold mb-2 text-gray-900" id="txtSettingsTitle">User Setup</h2>
            <p class="text-sm text-gray-500 mb-5">Configure users and adult permissions.</p>
            <form onsubmit="submitUsers(event)">
                <div id="userSetupList" class="space-y-4 mb-6"></div>
                <div class="flex justify-end space-x-3 pt-4 border-t border-gray-100 dark:border-gray-800">
                    <button type="button" onclick="closeSettingsModal()" class="px-5 py-2.5 bg-white border border-gray-300 rounded-lg text-sm font-semibold text-gray-700" id="btnSetCan">Cancel</button>
                    <button type="submit" class="px-5 py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-semibold">Save</button>
                </div>
            </form>
        </div>
    </div>
'''
}

def getHtmlModals2() {
    return '''
    <div id="fancyAlertModal" class="fixed inset-0 bg-gray-900 bg-opacity-60 backdrop-blur-sm hidden z-[100] flex justify-center items-center opacity-0 transition-opacity duration-300">
        <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-sm p-6 transform scale-95 transition-transform duration-300 border border-gray-200 dark:border-gray-700 text-center" id="fancyAlertCard">
            <div id="fancyAlertIcon" class="text-5xl mb-4"></div>
            <h3 id="fancyAlertTitle" class="text-xl font-bold mb-2 text-gray-900 dark:text-white"></h3>
            <p id="fancyAlertMsg" class="text-sm text-gray-500 dark:text-gray-400 mb-6"></p>
            <button onclick="closeFancyAlert()" class="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-bold transition shadow-md">Got It</button>
        </div>
    </div>

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

            <div id="sickToggleContainer" class="mt-4 mb-2 pt-4 border-t border-gray-200 dark:border-gray-700 flex justify-between items-center hidden">
                <span class="text-sm font-bold text-gray-700 dark:text-gray-300">Mark as Sick</span>
                <label class="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" id="sickToggleInput" class="sr-only peer" onchange="toggleSickMode(this.checked)">
                    <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-red-500"></div>
                </label>
            </div>
            
            <div id="visitorToggleContainer" class="mb-2 pt-2 border-t border-gray-200 dark:border-gray-700 flex justify-between items-center hidden">
                <span class="text-sm font-bold text-gray-700 dark:text-gray-300">Visitor Override</span>
                <label class="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" id="visitorToggleInput" class="sr-only peer" onchange="toggleVisitorMode(this.checked)">
                    <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-500"></div>
                </label>
            </div>

            <button onclick="setMood('')" class="mt-4 w-full py-2 bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 rounded-lg text-sm font-semibold hover:bg-gray-200 dark:hover:bg-gray-700 transition">Clear Mood</button>
            <button onclick="closeMoodModal()" class="mt-2 w-full py-2 bg-white dark:bg-gray-900 text-gray-500 border border-gray-200 dark:border-gray-700 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-800 transition">Cancel</button>
        </div>
    </div>
'''
}

def getJsInit() {
    return '''
        function getApiUrl(endpoint) {
            var url = new URL(window.location.href);
            var pathBase = url.pathname.replace(/\\/dashboard\\/?$/, "");
            return pathBase + endpoint + url.search;
        }
        
        var isLocal = !window.location.hostname.includes("hubitat.com");

        var events = []; window.displayEvents = []; var users = []; var groceries = []; var todos = [];
        var menuData = { mon: "", tue: "", wed: "", thu: "", fri: "", sat: "", sun: "" };
        var menuVotes = { mon: {up:[], down:[]}, tue: {up:[], down:[]}, wed: {up:[], down:[]}, thu: {up:[], down:[]}, fri: {up:[], down:[]}, sat: {up:[], down:[]}, sun: {up:[], down:[]} };
        var userNotes = []; var polls = []; var currentDate = new Date(); var currentMusicSpk = null;
        var activeFilterId = 'ALL'; var currentIsNight = false; var currentView = 'calendar'; var activeStoreFilter = 'ALL';
        var idleTime = 0; var isScreensaverActive = false; var isSleepActive = false; window.isTyping = false;
        
        var MOODS = ['😀','😂','🥰','😎','🤔','😴','🤪','🤐','🤯','🥳','🥶','🥵','🤢','🥺','🤬','🤠','🤡','👽','👻','💩','😤','😫'];

        window.isMaximized = false;
        window.toggleMaximize = function() {
            window.isMaximized = !window.isMaximized;
            var header = document.getElementById("themeHeader");
            var footer = document.getElementById("weatherFooter");
            var icon = document.getElementById("fabMaxIcon");
            
            if (window.isMaximized) {
                header.style.display = "none";
                footer.style.display = "none";
                icon.setAttribute("d", "M9 9V4.5M9 9H4.5M9 9L3.75 3.75M9 15v4.5M9 15H4.5M9 15l-5.25 5.25M15 9h4.5M15 9V4.5M15 9l5.25-5.25M15 15h4.5M15 15v4.5m0-4.5l5.25 5.25");
            } else {
                header.style.display = "";
                footer.style.display = "";
                icon.setAttribute("d", "M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 11.25h-4.5m4.5 0v-4.5m0 4.5L15 15");
                if(window.currentStatus) renderStatus(window.currentStatus);
            }
        };

        // --- MOBILE SWIPE GESTURES ---
        var touchStartX = 0;
        var touchStartY = 0;
        var touchEndX = 0;
        var touchEndY = 0;

        document.addEventListener('touchstart', function(e) {
            if (window.isTyping || e.target.closest('textarea') || e.target.closest('input') || e.target.closest('#eventModal') || e.target.closest('#settingsModal') || e.target.closest('#viewEventModal') || e.target.closest('#moodModal')) return;
            touchStartX = e.changedTouches[0].screenX;
            touchStartY = e.changedTouches[0].screenY;
        }, { passive: true });

        document.addEventListener('touchend', function(e) {
            if (window.isTyping || e.target.closest('textarea') || e.target.closest('input') || e.target.closest('#eventModal') || e.target.closest('#settingsModal') || e.target.closest('#viewEventModal') || e.target.closest('#moodModal')) return;
            touchEndX = e.changedTouches[0].screenX;
            touchEndY = e.changedTouches[0].screenY;
            handleSwipe();
        }, { passive: true });

        function handleSwipe() {
            var diffX = touchEndX - touchStartX;
            var diffY = touchEndY - touchStartY;
            if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 70) {
                var viewOrder = ['calendar', 'health', 'grocery', 'menu', 'notes', 'polls', 'todos'];
                if (isLocal) viewOrder.push('devices', 'music');
                
                var currentIndex = viewOrder.indexOf(currentView);
                if (currentIndex !== -1) {
                    if (diffX < 0) {
                        if (currentIndex < viewOrder.length - 1) switchView(viewOrder[currentIndex + 1]);
                    } else {
                        if (currentIndex > 0) switchView(viewOrder[currentIndex - 1]);
                    }
                }
            }
        }

        // --- GLOBAL UI HELPERS ---
        function getAvatarHtml(user, sizeClass, fallbackTextClass, extraClasses) {
            if (!user) return "";
            var ex = extraClasses ? " " + extraClasses : "";
            var displayLetter = user.name ? user.name.charAt(0).toUpperCase() : "U";
            var tag = user.avatar ? "<img src='" + user.avatar + "' class='" + sizeClass + " rounded-full object-cover shrink-0 border border-gray-200 dark:border-gray-700" + ex + "'>" : "<div class='" + sizeClass + " rounded-full flex items-center justify-center font-bold text-gray-500 bg-gray-200 " + fallbackTextClass + " shrink-0 border border-gray-300 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300" + ex + "'>" + displayLetter + "</div>";
            
            var isTired = false;
            if (window.currentStatus && window.currentStatus.sleepScores && window.currentStatus.sleepScores[user.id] !== null) {
                var sScore = parseInt(window.currentStatus.sleepScores[user.id]);
                if (!isNaN(sScore) && sScore < 60) isTired = true;
            }
            
            var effortScore = 0;
            if (window.currentStatus && window.currentStatus.effortScores && window.currentStatus.effortScores[user.id] !== null) {
                var rawEffort = parseInt(window.currentStatus.effortScores[user.id]);
                if (!isNaN(rawEffort)) effortScore = rawEffort;
            }
            
            var isSick = false;
            if (window.currentStatus && window.currentStatus.sickStatus && window.currentStatus.sickStatus[user.id] === true) {
                isSick = true;
            }

            var isVisitor = false;
            if (window.currentStatus && window.currentStatus.visitorStatus && window.currentStatus.visitorStatus[user.id] === true) {
                isVisitor = true;
            }

            var moodEmoji = user.mood;
            if (isSick) moodEmoji = "🤒";

            var badgeSize = (sizeClass.includes('w-4') || sizeClass.includes('avatar-sm')) ? 'text-[8px] -top-0.5 -left-0.5' : 'text-[12px] -top-1 -left-1';
            var moodBadge = moodEmoji ? "<span class='absolute " + badgeSize + " leading-none z-10 rounded-full shadow-sm bg-white dark:bg-gray-900 border border-gray-100 dark:border-gray-800 p-[1px]'>" + moodEmoji + "</span>" : "";
            
            var sleepBadgeSize = (sizeClass.includes('w-4') || sizeClass.includes('avatar-sm')) ? 'text-[10px] -top-1 -right-1' : 'text-[14px] -top-1 -right-1';
            var sleepBadge = isTired ? "<span class='absolute " + sleepBadgeSize + " leading-none z-10 drop-shadow-md'>💤</span>" : "";
            
            var effortBadgeSize = (sizeClass.includes('w-4') || sizeClass.includes('avatar-sm')) ? 'text-[8px] -bottom-1 -right-1' : 'text-[10px] -bottom-1 -right-1';
            var effortBadge = effortScore > 0 ? "<span class='absolute " + effortBadgeSize + " leading-none z-10 drop-shadow-md bg-white dark:bg-gray-800 rounded-full px-0.5 text-orange-500 font-bold border border-gray-100 dark:border-gray-700'>⚡" + effortScore + "</span>" : "";
            
            var visitorBadgeSize = (sizeClass.includes('w-4') || sizeClass.includes('avatar-sm')) ? 'text-[10px] -bottom-1 -left-1' : 'text-[14px] -bottom-1 -left-1';
            var visitorBadge = isVisitor ? "<span class='absolute " + visitorBadgeSize + " leading-none z-10 drop-shadow-md'>🛎️</span>" : "";

            return "<div class='relative inline-flex shrink-0'>" + tag + moodBadge + sleepBadge + effortBadge + visitorBadge + "</div>";
        }
        window.getAvatarHtml = getAvatarHtml;

        function showAlert(title, msg, type) {
            var modal = document.getElementById("fancyAlertModal"); var card = document.getElementById("fancyAlertCard");
            document.getElementById("fancyAlertTitle").innerText = title; document.getElementById("fancyAlertMsg").innerText = msg;
            var iconEl = document.getElementById("fancyAlertIcon");
            if (type === 'success') { iconEl.innerHTML = "✅"; } else if (type === 'error') { iconEl.innerHTML = "⚠️"; } else { iconEl.innerHTML = "ℹ️"; }
            modal.classList.remove("hidden");
            setTimeout(function() { modal.classList.remove("opacity-0"); card.classList.remove("scale-95"); }, 10);
        }
        window.showAlert = showAlert;

        function closeFancyAlert() {
            var modal = document.getElementById("fancyAlertModal"); var card = document.getElementById("fancyAlertCard");
            modal.classList.add("opacity-0"); card.classList.add("scale-95");
            setTimeout(function() { modal.classList.add("hidden"); }, 300);
        }
        window.closeFancyAlert = closeFancyAlert;

        function openMoodSelector(userId) {
            document.getElementById("moodUserId").value = userId;
            var grid = document.getElementById("moodGrid"); var html = "";
            var u = users.find(function(x){ return x.id === userId }); var currentMood = u ? u.mood : "";
            for(var i=0; i<MOODS.length; i++) {
                var m = MOODS[i]; var activeClass = (m === currentMood) ? "bg-indigo-100 ring-2 ring-indigo-500 dark:bg-indigo-900" : "hover:bg-gray-100 dark:hover:bg-gray-800";
                html += "<button onclick='setMood(\\"" + m + "\\")' class='text-2xl p-2 rounded-xl transition " + activeClass + "'>" + m + "</button>";
            }
            grid.innerHTML = html; 

            var isSick = (window.currentStatus && window.currentStatus.sickStatus && window.currentStatus.sickStatus[userId] === true);
            var sickInput = document.getElementById("sickToggleInput");
            if (sickInput) sickInput.checked = isSick;
            document.getElementById("sickToggleContainer").classList.remove("hidden");

            var isVisitor = (window.currentStatus && window.currentStatus.visitorStatus && window.currentStatus.visitorStatus[userId] === true);
            var visitorInput = document.getElementById("visitorToggleInput");
            if (visitorInput) visitorInput.checked = isVisitor;
            document.getElementById("visitorToggleContainer").classList.remove("hidden");

            document.getElementById("moodModal").classList.remove("hidden");
        }
        window.openMoodSelector = openMoodSelector;

        function closeMoodModal() { document.getElementById("moodModal").classList.add("hidden"); }
        window.closeMoodModal = closeMoodModal;

        async function setMood(moodEmoji) {
            var userId = document.getElementById("moodUserId").value; closeMoodModal();
            var u = users.find(function(x){ return x.id === userId }); 
            if (u) {
                u.mood = moodEmoji;
                u.moodTimestamp = Date.now();
            }
            renderUserFilters(); 
            if (currentView === 'calendar') { renderCalendar(); renderTopCards(); } else if (currentView === 'health') { renderHealth(); } else if (currentView === 'grocery') { renderGroceries(); } else if (currentView === 'notes') { renderNotes(); }
            try { await fetch(getApiUrl("/api/users/mood"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: userId, mood: moodEmoji }) }); } catch(e) {}
        }
        window.setMood = setMood;

        async function toggleSickMode(isSick) {
            var userId = document.getElementById("moodUserId").value;
            if (window.currentStatus && window.currentStatus.sickStatus) {
                window.currentStatus.sickStatus[userId] = isSick;
            }
            renderUserFilters();
            if (currentView === 'calendar') { renderCalendar(); renderTopCards(); } else if (currentView === 'health') { renderHealth(); } else if (currentView === 'grocery') { renderGroceries(); } else if (currentView === 'notes') { renderNotes(); } else if (currentView === 'todos') { renderTodos(); } else if (currentView === 'polls') { renderPolls(); }
            try {
                await fetch(getApiUrl("/api/users/sick"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: userId, state: isSick }) });
            } catch(e) {}
        }
        window.toggleSickMode = toggleSickMode;

        async function toggleVisitorMode(isVisitor) {
            var userId = document.getElementById("moodUserId").value;
            if (window.currentStatus && window.currentStatus.visitorStatus) {
                window.currentStatus.visitorStatus[userId] = isVisitor;
            }
            renderUserFilters();
            if (currentView === 'calendar') { renderCalendar(); renderTopCards(); } else if (currentView === 'health') { renderHealth(); } else if (currentView === 'grocery') { renderGroceries(); } else if (currentView === 'notes') { renderNotes(); } else if (currentView === 'todos') { renderTodos(); } else if (currentView === 'polls') { renderPolls(); }
            try {
                await fetch(getApiUrl("/api/users/visitor"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: userId, state: isVisitor }) });
            } catch(e) {}
        }
        window.toggleVisitorMode = toggleVisitorMode;

        window.showAllEvents = false;
        window.toggleAllEvents = function() {
            window.showAllEvents = !window.showAllEvents;
            var title = document.getElementById("eventsCardTitle"); var btn = document.getElementById("eventsCardToggle");
            if(title) title.innerText = window.showAllEvents ? "This Month's Events" : "Today's Events";
            if(btn) btn.innerText = window.showAllEvents ? "Show Today" : "View All";
            renderTopCards();
        };

        window.handleMoodBtnClick = function() {
            if (activeFilterId === 'ALL' || activeFilterId === null) {
                showAlert("Select a User", "Please select a user profile at the top before setting a mood or health status.", "info");
            } else {
                openMoodSelector(activeFilterId);
            }
        };

        window.handleUserFilterClick = function(id) {
            toggleUserFilter(id);
        };

        window.toggleUserFilter = function(filterId) {
            activeFilterId = filterId;
            renderUserFilters(); 
            renderTopCards(); 
            renderCalendar();
            
            if (window.currentStatus) {
                renderStatus(window.currentStatus);
            }
            
            if (filterId !== 'ALL') {
                var mAct = document.getElementById("menuActor"); if(mAct) mAct.value = filterId;
                var nAuth = document.getElementById("noteAuthor"); if(nAuth) nAuth.value = filterId;
            }
            if (currentView === 'health') renderHealth();
            if (currentView === 'grocery') renderGroceries(); 
            if (currentView === 'menu') renderMenu(); 
            if (currentView === 'polls') renderPolls();
            if (currentView === 'todos') renderTodos();
        };

        function resetIdle() {
            idleTime = 0;
            if (isScreensaverActive || isSleepActive) {
                document.getElementById('screensaverOverlay').classList.add('hidden');
                document.getElementById('sleepOverlay').classList.add('hidden');
                isScreensaverActive = false; isSleepActive = false;
                if (window.currentStatus) { renderStatus(window.currentStatus); }
            }
        }
        ['touchstart', 'mousemove', 'mousedown', 'keydown', 'scroll', 'click'].forEach(function(evt) { document.addEventListener(evt, resetIdle, true); });
        document.addEventListener('focusin', function(e) { if(['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target.tagName)) window.isTyping = true; });
        document.addEventListener('focusout', function(e) { window.isTyping = false; });

        setInterval(function() {
            idleTime += 1000;
            if (idleTime >= 600000 && !isSleepActive) {
                document.getElementById('sleepOverlay').classList.remove('hidden'); document.getElementById('screensaverOverlay').classList.add('hidden'); isSleepActive = true;
            } else if (idleTime >= 300000 && idleTime < 600000 && !isScreensaverActive) {
                renderScreensaver(); document.getElementById('screensaverOverlay').classList.remove('hidden'); isScreensaverActive = true;
                idleTime = 300000;
            } else if (isScreensaverActive && !isSleepActive) { updateClock(); }
        }, 1000);

        function updateClock() {
            var now = new Date();
            var ssTimeElem = document.getElementById("ssTime"); if(ssTimeElem) ssTimeElem.innerText = now.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
            var ssDateElem = document.getElementById("ssDate"); if(ssDateElem) ssDateElem.innerText = now.toLocaleDateString([], {weekday: 'long', month: 'long', day: 'numeric'});
        }
        
        function getMapUrl(address) {
            var isApple = /iPhone|iPad|iPod|Macintosh/i.test(navigator.userAgent);
            if(isApple) return "maps://?q=" + encodeURIComponent(address);
            return "https://www.google.com/maps/search/?api=1&query=" + encodeURIComponent(address);
        }

        setInterval(function() {
            if (typeof pollStatus !== 'undefined') pollStatus();
        }, 15000); 
    '''
}

def getJsRendering1() {
    return '''
        function applyTheme(isNight) {
            currentIsNight = isNight;
            var body = document.getElementById("themeBody"); var header = document.getElementById("themeHeader"); var toolbar = document.getElementById("themeToolbar");
            var dbanner = document.getElementById("deviceBanner"); var tabsRow = document.getElementById("themeTabsRow"); var tabsBg = document.getElementById("viewTabsBg");
            var cardE = document.getElementById("cardEvents"); var evtHeader = document.getElementById("eventsCardHeader"); var gridW = document.getElementById("calendarGridWrapper");
            var cardC = document.getElementById("cardCountdowns"); var countHeader = document.getElementById("countdownCardHeader");
            var btnS = document.getElementById("themeSettingsBtn"); var btnM = document.getElementById("themeMoodBtn"); var btnP = document.getElementById("themePrevBtn"); var btnN = document.getElementById("themeNextBtn");
            var m1 = document.getElementById("mdlEventBg"); var m2 = document.getElementById("mdlViewBg"); var m3 = document.getElementById("mdlSettingsBg");
            
            var grocFBar = document.getElementById("grocFilterBar"); var grocAdd = document.getElementById("grocAddCard"); var grocAction = document.getElementById("grocActionBar");
            var gName = document.getElementById("grocName"); var gStore = document.getElementById("grocStore"); var gUser = document.getElementById("grocUser");
            
            var todoAdd = document.getElementById("todoAddCard"); var tName = document.getElementById("todoName"); var todoAction = document.getElementById("todoActionBar"); var btnTodoClearChk = document.getElementById("btnTodoClearChk");

            var menuBar = document.getElementById("menuActorBar"); var menuLbl = document.getElementById("menuActorLbl"); var menuAct = document.getElementById("menuActor"); var menuAdd = document.getElementById("menuContainer");
            var notesAdd = document.getElementById("notesAddCard"); var notesArea = document.getElementById("newNoteText"); var notesAuth = document.getElementById("noteAuthor");
            var pollsAdd = document.getElementById("pollsAddCard"); var pollsQ = document.getElementById("pollQuestion"); var pollsO1 = document.getElementById("pollOpt1"); var pollsO2 = document.getElementById("pollOpt2"); var pollsO3 = document.getElementById("pollOpt3"); var pollsO4 = document.getElementById("pollOpt4"); var pollsAuth = document.getElementById("pollAuthor");
            var tSettings = document.getElementById("txtSettingsTitle"); var eTitle = document.getElementById("eventModalTitle"); var btnEvtCan = document.getElementById("btnEvtCan"); var btnViewClose = document.getElementById("btnViewClose"); var btnSetCan = document.getElementById("btnSetCan"); var btnGrocClearChk = document.getElementById("btnGrocClearChk");

            if (!isLocal) {
                if(document.getElementById('tabDevices')) document.getElementById('tabDevices').style.display = 'none';
                if(document.getElementById('tabMusic')) document.getElementById('tabMusic').style.display = 'none';
            } else {
                if(document.getElementById('tabDevices')) document.getElementById('tabDevices').style.display = '';
                if(document.getElementById('tabMusic')) document.getElementById('tabMusic').style.display = '';
            }

            if(isNight) {
                body.className = "fixed inset-0 flex flex-col overflow-hidden text-gray-100 bg-gray-950";
                header.className = "bg-gray-900 shadow-sm z-20 shrink-0 flex flex-col border-b border-gray-800";
                toolbar.className = "flex justify-between items-center px-4 py-2 bg-gray-900 border-t border-gray-800";
                if(tabsRow) tabsRow.className = "flex justify-center items-center px-2 py-2 bg-gray-900 border-t border-gray-800 w-full overflow-x-auto hide-scrollbar";
                if(tabsBg) tabsBg.className = "flex p-1 bg-gray-800 rounded-lg w-full max-w-3xl min-w-[300px] space-x-1 overflow-x-auto hide-scrollbar";
                dbanner.className = "hidden flex space-x-2 overflow-x-auto py-1 hide-scrollbar bg-gray-800 border-t border-gray-700 px-4";
                
                if(cardE) cardE.className = "flex-1 bg-gray-900 border border-gray-800 rounded-xl p-3 shadow-sm flex flex-col min-h-[120px] sm:min-h-[140px]";
                if(evtHeader) evtHeader.className = "text-xs font-bold text-gray-400 uppercase tracking-wide border-b border-gray-700 pb-2 mb-2 shrink-0 flex justify-between items-center";
                
                if(cardC) cardC.className = "flex-1 bg-gray-900 border border-gray-800 rounded-xl p-3 shadow-sm flex flex-col min-h-[120px] sm:min-h-[140px] " + (cardC.classList.contains("hidden") ? "hidden" : "");
                if(countHeader) countHeader.className = "text-xs font-bold text-indigo-500 uppercase tracking-wide border-b border-gray-700 pb-2 mb-2 shrink-0 flex justify-between items-center";

                if(gridW) gridW.className = "calendar-grid shadow-sm shrink-0 bg-gray-800";
                [btnS, btnM, btnP, btnN].forEach(function(b) { if(b) b.className = "p-2 bg-gray-800 shadow-sm rounded-full text-gray-300 hover:bg-gray-700 transition flex items-center justify-center"; });
                [m1, m2, m3].forEach(function(m) { if(m) m.className = "bg-gray-900 rounded-t-2xl sm:rounded-2xl shadow-xl w-full max-w-md p-6 border border-gray-800"; });
                if(m2) m2.className = "bg-gray-900 rounded-2xl shadow-xl w-full max-w-sm p-6 relative border border-gray-800";
                if(m3) m3.className = "bg-gray-900 rounded-2xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto border border-gray-800";
                
                var tHead = document.querySelector("#headerTitle"); if(tHead) tHead.className = "truncate leading-tight flex-1 min-w-0 mr-2 text-white";
                if(tSettings) tSettings.className = "text-xl font-bold mb-2 text-white"; if(eTitle) eTitle.className = "text-xl font-bold mb-4 text-white";
                for(var i=0; i<7; i++) { var ch = document.getElementById("ch" + i); if(ch) ch.className = "calendar-header bg-gray-900 text-gray-400"; }

                if(grocFBar) grocFBar.className = "px-4 py-3 shrink-0 flex space-x-2 overflow-x-auto hide-scrollbar border-b border-gray-800 bg-gray-900";
                if(grocAdd) grocAdd.className = "mx-4 mt-3 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border border-gray-800 bg-gray-900";
                if(grocAction) grocAction.className = "px-4 py-3 shrink-0 flex justify-between border-t border-gray-800 bg-gray-900 z-20";
                
                var nightInput = "flex-1 p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-800 border-gray-700 text-gray-200 placeholder-gray-500";
                if(gName) gName.className = nightInput; if(gStore) gStore.className = nightInput; if(gUser) gUser.className = nightInput;
                
                if(todoAdd) todoAdd.className = "mx-4 mt-3 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border border-gray-800 bg-gray-900";
                if(tName) tName.className = nightInput;
                if(todoAction) todoAction.className = "px-4 py-3 shrink-0 flex justify-between border-t border-gray-800 bg-gray-900 z-20";

                if(menuBar) menuBar.className = "flex items-center gap-2 p-3 bg-gray-900 rounded-xl border border-gray-800 shrink-0";
                if(menuLbl) menuLbl.className = "text-sm font-bold text-gray-300"; if(menuAct) menuAct.className = nightInput;
                if(menuAdd) menuAdd.className = "bg-gray-900 rounded-xl shadow-sm border border-gray-800 p-4 space-y-4 shrink-0";
                if(notesAdd) notesAdd.className = "mx-4 mt-4 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border border-gray-800 bg-gray-900";
                if(notesArea) notesArea.className = "w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-800 border-gray-700 text-gray-200 placeholder-gray-500";
                if(notesAuth) notesAuth.className = nightInput;
                if(pollsAdd) pollsAdd.className = "bg-gray-900 rounded-xl shadow-sm border border-gray-800 p-4 shrink-0";
                var pbH = document.getElementById("pollsAddHeader"); if(pbH) pbH.className = "font-bold text-gray-300 mb-3";
                if(pollsQ) pollsQ.className = "w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-800 border-gray-700 text-gray-200 mb-2 placeholder-gray-500";
                [pollsO1, pollsO2, pollsO3, pollsO4, pollsAuth].forEach(function(p){ if(p) p.className = nightInput; });

                var btnStyleNight = "px-5 py-2 bg-gray-800 border border-gray-700 rounded-lg text-sm font-semibold text-gray-300 hover:bg-gray-700";
                if(btnEvtCan) btnEvtCan.className = btnStyleNight; if(btnViewClose) btnViewClose.className = "px-4 py-2 bg-gray-800 text-gray-300 rounded-lg text-sm font-semibold hover:bg-gray-700 transition";
                if(btnSetCan) btnSetCan.className = btnStyleNight; 
                if(btnGrocClearChk) btnGrocClearChk.className = "text-sm font-semibold text-gray-300 px-4 py-2 border border-gray-700 bg-gray-800 rounded-lg hover:bg-gray-700 transition";
                if(btnTodoClearChk) btnTodoClearChk.className = "text-sm font-semibold text-gray-300 px-4 py-2 border border-gray-700 bg-gray-800 rounded-lg hover:bg-gray-700 transition";
            } else {
                body.className = "fixed inset-0 flex flex-col overflow-hidden text-gray-800 bg-[#f4f4f5]";
                header.className = "bg-white shadow-sm z-20 shrink-0 flex flex-col border-b border-gray-200";
                toolbar.className = "flex justify-between items-center px-4 py-2 bg-gray-50 border-t border-gray-100";
                if(tabsRow) tabsRow.className = "flex justify-center items-center px-2 py-2 bg-gray-50 border-t border-gray-100 w-full overflow-x-auto hide-scrollbar";
                if(tabsBg) tabsBg.className = "flex p-1 bg-gray-200/60 rounded-lg w-full max-w-3xl min-w-[300px] space-x-1 overflow-x-auto hide-scrollbar";
                dbanner.className = "hidden flex space-x-2 overflow-x-auto py-1 hide-scrollbar bg-gray-50 border-t border-gray-100 px-4";
                
                if(cardE) cardE.className = "flex-1 bg-white border border-gray-200 rounded-xl p-3 shadow-sm flex flex-col min-h-[120px] sm:min-h-[140px]";
                if(evtHeader) evtHeader.className = "text-xs font-bold text-gray-400 uppercase tracking-wide border-b border-gray-100 pb-2 mb-2 shrink-0 flex justify-between items-center";
                
                if(cardC) cardC.className = "flex-1 bg-white border border-gray-200 rounded-xl p-3 shadow-sm flex flex-col min-h-[120px] sm:min-h-[140px] " + (cardC.classList.contains("hidden") ? "hidden" : "");
                if(countHeader) countHeader.className = "text-xs font-bold text-indigo-400 uppercase tracking-wide border-b border-gray-100 pb-2 mb-2 shrink-0 flex justify-between items-center";

                if(gridW) gridW.className = "calendar-grid shadow-sm shrink-0 bg-gray-200";
                [btnS, btnM, btnP, btnN].forEach(function(b) { if(b) b.className = "p-2 bg-white shadow-sm rounded-full text-gray-600 hover:bg-gray-100 transition flex items-center justify-center"; });
                [m1, m2, m3].forEach(function(m) { if(m) m.className = "bg-white rounded-t-2xl sm:rounded-2xl shadow-xl w-full max-w-md p-6 text-gray-800"; });
                if(m2) m2.className = "bg-white rounded-2xl shadow-xl w-full max-w-sm p-6 relative text-gray-800";
                if(m3) m3.className = "bg-white rounded-2xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto text-gray-800";
                
                var tHeadLight = document.querySelector("#headerTitle"); if(tHeadLight) tHeadLight.className = "truncate leading-tight flex-1 min-w-0 mr-2 text-gray-900";
                if(tSettings) tSettings.className = "text-xl font-bold mb-2 text-gray-900"; if(eTitle) eTitle.className = "text-xl font-bold mb-4 text-gray-900";
                for(var j=0; j<7; j++) { var chl = document.getElementById("ch" + j); if(chl) chl.className = "calendar-header bg-gray-50 text-gray-500"; }

                if(grocFBar) grocFBar.className = "px-4 py-3 shrink-0 flex space-x-2 overflow-x-auto hide-scrollbar border-b border-gray-200 bg-white";
                if(grocAdd) grocAdd.className = "mx-4 mt-3 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border border-gray-200 bg-white";
                if(grocAction) grocAction.className = "px-4 py-3 shrink-0 flex justify-between border-t border-gray-200 bg-white z-20";
                
                var lightInput = "flex-1 p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800 placeholder-gray-400";
                if(gName) gName.className = lightInput; if(gStore) gStore.className = lightInput; if(gUser) gUser.className = lightInput;
                
                if(todoAdd) todoAdd.className = "mx-4 mt-3 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border border-gray-200 bg-white";
                if(tName) tName.className = lightInput;
                if(todoAction) todoAction.className = "px-4 py-3 shrink-0 flex justify-between border-t border-gray-200 bg-white z-20";

                if(menuBar) menuBar.className = "flex items-center gap-2 p-3 bg-gray-50 rounded-xl border border-gray-200 shrink-0";
                if(menuLbl) menuLbl.className = "text-sm font-bold text-gray-700"; if(menuAct) menuAct.className = lightInput;
                if(menuAdd) menuAdd.className = "bg-white rounded-xl shadow-sm border border-gray-200 p-4 space-y-4 shrink-0";
                if(notesAdd) notesAdd.className = "mx-4 mt-4 p-3 rounded-xl shadow-sm shrink-0 flex flex-col gap-2 border border-gray-200 bg-white";
                if(notesArea) notesArea.className = "w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800 placeholder-gray-400";
                if(notesAuth) notesAuth.className = lightInput;
                if(pollsAdd) pollsAdd.className = "bg-white rounded-xl shadow-sm border border-gray-200 p-4 shrink-0";
                var pbH2 = document.getElementById("pollsAddHeader"); if(pbH2) pbH2.className = "font-bold text-gray-700 mb-3";
                if(pollsQ) pollsQ.className = "w-full p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 bg-gray-50 border-gray-300 text-gray-800 mb-2 placeholder-gray-400";
                [pollsO1, pollsO2, pollsO3, pollsO4, pollsAuth].forEach(function(p){ if(p) p.className = lightInput; });

                var btnStyleLight = "px-5 py-2 bg-white border border-gray-300 rounded-lg text-sm font-semibold text-gray-700 hover:bg-gray-50";
                if(btnEvtCan) btnEvtCan.className = btnStyleLight; if(btnViewClose) btnViewClose.className = "px-4 py-2 bg-gray-100 text-gray-700 rounded-lg text-sm font-semibold hover:bg-gray-200 transition";
                if(btnSetCan) btnSetCan.className = btnStyleLight; 
                if(btnGrocClearChk) btnGrocClearChk.className = "text-sm font-semibold text-gray-600 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition";
                if(btnTodoClearChk) btnTodoClearChk.className = "text-sm font-semibold text-gray-600 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition";
            }
            switchView(currentView);
        }

        function switchView(view) {
            currentView = view;
            var tabs = { calendar: document.getElementById("tabCalendar"), health: document.getElementById("tabHealth"), grocery: document.getElementById("tabGrocery"), menu: document.getElementById("tabMenu"), notes: document.getElementById("tabNotes"), polls: document.getElementById("tabPolls"), todos: document.getElementById("tabTodos") };
            var mains = { calendar: document.getElementById("calendarMain"), health: document.getElementById("healthMain"), grocery: document.getElementById("groceryMain"), menu: document.getElementById("menuMain"), notes: document.getElementById("notesMain"), polls: document.getElementById("pollsMain"), todos: document.getElementById("todosMain") };
            
            if (isLocal) {
                tabs.devices = document.getElementById("tabDevices"); tabs.music = document.getElementById("tabMusic");
                mains.devices = document.getElementById("devicesMain"); mains.music = document.getElementById("musicMain");
            } else {
                if (view === 'devices' || view === 'music') { view = 'calendar'; currentView = 'calendar'; }
            }

            var controls = document.getElementById("calendarControls");
            var activeClassTab = currentIsNight ? "bg-gray-700 text-indigo-400 shadow-sm" : "bg-white text-indigo-600 shadow-sm";
            var inactiveClassTab = currentIsNight ? "text-gray-400 hover:text-gray-200" : "text-gray-500 hover:text-gray-700";
            var baseTabClass = "relative min-w-[50px] flex-1 py-2 flex justify-center items-center rounded-md transition-all ";

            for(var key in tabs) {
                if(tabs[key]) tabs[key].className = baseTabClass + (key === view ? activeClassTab : inactiveClassTab);
                if(mains[key]) { 
                    if(key === view) {
                        mains[key].classList.remove("hidden", "!hidden");
                        if (key !== 'calendar' && key !== 'health') mains[key].classList.add("flex");
                    } else {
                        mains[key].classList.add("!hidden");
                        if (key !== 'calendar' && key !== 'health') mains[key].classList.remove("flex");
                    }
                }
            }

            if (!isLocal) {
                if(document.getElementById('tabDevices')) document.getElementById('tabDevices').style.display = 'none';
                if(document.getElementById('tabMusic')) document.getElementById('tabMusic').style.display = 'none';
            } else {
                if(document.getElementById('tabDevices')) document.getElementById('tabDevices').style.display = '';
                if(document.getElementById('tabMusic')) document.getElementById('tabMusic').style.display = '';
            }

            var fab = document.getElementById("fabToggleLayout");
            if (fab) {
                if (view === 'grocery' || view === 'notes' || view === 'todos') {
                    fab.classList.remove("hidden");
                } else {
                    fab.classList.add("hidden");
                    if (window.isMaximized) window.toggleMaximize();
                }
            }

            if (window.currentStatus && window.currentStatus.lastUpdates) {
                localStorage.setItem("lastViewedServer_" + view, window.currentStatus.lastUpdates[view]);
            }
            if (typeof updateRedDots === 'function') updateRedDots();

            if(view === 'calendar') {
                controls.classList.remove("hidden"); if (window.currentStatus) renderStatus(window.currentStatus);
            } else {
                controls.classList.add("hidden"); var titleText = "";
                if(view === 'health') { titleText = "Health Metrics"; renderHealth(); } 
                else if(view === 'grocery') { titleText = "Grocery List"; renderGroceries(); } 
                else if(view === 'menu') { titleText = "Weekly Menu"; renderMenu(); } 
                else if(view === 'notes') { titleText = "Message Board"; renderNotes(); } 
                else if(view === 'polls') { titleText = "Family Polls"; renderPolls(); }
                else if(view === 'todos') { titleText = "To-Do List"; renderTodos(); }
                else if(view === 'devices') { titleText = "House Devices"; renderDevices(); }
                else if(view === 'music') { titleText = "Sonos Audio"; renderMusic(); }
                document.getElementById("headerTitle").innerHTML = "<div class='text-xs sm:text-sm font-semibold uppercase tracking-wide opacity-50'>Household</div><div class='text-xl sm:text-2xl font-bold leading-none mt-0.5'>" + titleText + "</div>";
            }
        }
'''
}

def getJsRendering2() {
    return '''
        function updateFormDropdowns() {
            var adultOptions = ""; var allOptions = "";
            for(var i=0; i<users.length; i++) {
                if(!users[i].disabled && users[i].name) {
                    allOptions += "<option value='" + users[i].id + "'>" + users[i].name + "</option>";
                    if(users[i].isAdult) { adultOptions += "<option value='" + users[i].id + "'>" + users[i].name + "</option>"; }
                }
            }
            var el1 = document.getElementById("grocUser"); if(el1) el1.innerHTML = adultOptions;
            var el2 = document.getElementById("eventUserId"); if(el2) el2.innerHTML = "<option value='all'>All Users</option>" + adultOptions;
            var el3 = document.getElementById("menuActor"); if(el3) el3.innerHTML = allOptions; 
            var el4 = document.getElementById("noteAuthor"); if(el4) el4.innerHTML = allOptions;
            var el5 = document.getElementById("pollAuthor"); if(el5) el5.innerHTML = adultOptions;
        }

        function renderUserFilters() {
            var container = document.getElementById("userFilters"); if(!container) return;
            var html = "";
            var activeClass = currentIsNight ? "ring-2 ring-indigo-400 opacity-100" : "ring-2 ring-indigo-600 opacity-100";
            var inactiveClass = "opacity-40 hover:opacity-100";
            var allIsSelected = (activeFilterId === 'ALL' || activeFilterId === null);
            var allRingClass = allIsSelected ? activeClass : inactiveClass;
            var allBubbleBg = currentIsNight ? "bg-gray-800" : "bg-gray-200";
            html += "<div class='relative cursor-pointer shrink-0' onclick='handleUserFilterClick(\\"ALL\\")'><div class='w-8 h-8 rounded-full flex items-center justify-center font-bold text-gray-500 " + allBubbleBg + " text-[9px] transition-all " + allRingClass + "'>ALL</div></div>";

            for(var i=0; i<users.length; i++) {
                var u = users[i]; if(u.disabled || !u.name) continue;
                var isSelected = (activeFilterId === u.id); var ringClass = isSelected ? activeClass : inactiveClass;
                var isHome = false;
                if(window.currentStatus && window.currentStatus.userPresence) { isHome = window.currentStatus.userPresence[u.id] === "present"; }
                var statusColor = isHome ? "bg-emerald-500" : "bg-gray-400";
                var ringColor = currentIsNight ? "ring-gray-900" : "ring-white";
                var statusDot = "<span class='absolute bottom-0 right-0 block h-2.5 w-2.5 rounded-full ring-2 " + ringColor + " " + statusColor + " z-20'></span>";
                
                var avatarTag = getAvatarHtml(u, 'w-8 h-8', 'text-xs', 'transition-all ' + ringClass);
                html += "<div class='relative cursor-pointer shrink-0' onclick='handleUserFilterClick(\\"" + u.id + "\\")'>" + avatarTag + statusDot + "</div>";
            }
            container.innerHTML = html;
        }

        function buildDisplayEvents() {
            window.displayEvents = []; var nowD = new Date();
            var viewStart = new Date(nowD.getFullYear() - 1, nowD.getMonth(), 1); 
            var viewEnd = new Date(nowD.getFullYear() + 2, nowD.getMonth(), 1); 
            events.forEach(function(e) {
                var exceptions = e.exceptions || [];
                if (!e.recurrence || e.recurrence === 'none') {
                    if (!exceptions.includes(e.date)) { window.displayEvents.push(Object.assign({}, e, { instanceDate: e.date })); }
                } else {
                    var cur = new Date(e.date + 'T00:00:00'); var failsafe = 0;
                    while (cur <= viewEnd && failsafe < 500) {
                        failsafe++;
                        var dStr = cur.getFullYear() + "-" + String(cur.getMonth()+1).padStart(2,'0') + "-" + String(cur.getDate()).padStart(2,'0');
                        if (cur >= viewStart && !exceptions.includes(dStr)) { window.displayEvents.push(Object.assign({}, e, { instanceDate: dStr })); }
                        if (e.recurrence === 'weekly') cur.setDate(cur.getDate() + 7);
                        else if (e.recurrence === 'monthly') cur.setMonth(cur.getMonth() + 1);
                        else if (e.recurrence === 'yearly') cur.setFullYear(cur.getFullYear() + 1);
                        else break;
                    }
                }
            });
        }

        function renderTopCards() {
            var today = new Date(); today.setHours(0,0,0,0);
            var todayStr = today.getFullYear() + "-" + String(today.getMonth() + 1).padStart(2, "0") + "-" + String(today.getDate()).padStart(2, "0");
            var endOfMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0);
            var endOfMonthStr = endOfMonth.getFullYear() + "-" + String(endOfMonth.getMonth() + 1).padStart(2, "0") + "-" + String(endOfMonth.getDate()).padStart(2, "0");
            
            var tEventsHtml = ""; 
            var sortedEvents = window.displayEvents.slice().sort(function(a,b){ return a.instanceDate.localeCompare(b.instanceDate); });
            for(var e=0; e<sortedEvents.length; e++) {
                var evt = sortedEvents[e];
                if(window.showAllEvents) { 
                    if(evt.instanceDate < todayStr || evt.instanceDate > endOfMonthStr) continue; 
                } else { 
                    if(evt.instanceDate !== todayStr) continue; 
                }
                
                if(activeFilterId !== 'ALL' && activeFilterId !== null && evt.userId !== activeFilterId && evt.userId !== 'all') continue;
                var user = (evt.userId === 'all') ? {id: 'all', name: 'All'} : users.find(function(u){ return u.id === evt.userId }); 
                if(user && user.id !== 'all' && user.disabled) continue;

                var icon = evt.type === 'birthday' ? '🎁' : '📅'; var uName = user ? user.name : "Unknown";
                var cardBg = currentIsNight ? "bg-gray-800 border-gray-700 hover:bg-gray-700 text-gray-200" : "bg-gray-50 border-gray-100 hover:bg-gray-100 text-gray-700";
                var dateDisplay = "";
                if (window.showAllEvents && evt.instanceDate !== todayStr) {
                    var dParts = evt.instanceDate.split('-');
                    if (dParts.length === 3) { dateDisplay = " <span class='text-[10px] text-indigo-400 font-bold ml-1'>(" + parseInt(dParts[1]) + "/" + parseInt(dParts[2]) + ")</span>"; }
                }
                var avatarTag = getAvatarHtml(user, 'w-6 h-6', 'text-[10px]', '');
                tEventsHtml += "<div class='flex items-center space-x-2 text-sm p-2 rounded border cursor-pointer " + cardBg + "' data-id='" + evt.id + "' data-inst='" + evt.instanceDate + "' onclick='viewEvent(event, this.dataset.id, this.dataset.inst)'>" +
                    "<span>" + icon + "</span><span class='font-bold truncate flex-1'>" + evt.title + dateDisplay + "</span><div class='flex items-center space-x-1 shrink-0 ml-2'>" + avatarTag + "</div></div>";
            }
            if(tEventsHtml === "") tEventsHtml = "<div class='text-sm text-gray-400 italic mt-1'>No events scheduled.</div>";
            var targetE = document.getElementById("todaysEventsCard"); if(targetE) targetE.innerHTML = tEventsHtml;
            
            var countdownHtml = "";
            var processedIds = {};
            var cdEvents = window.displayEvents.filter(function(ev) { return ev.isCountdown === true && ev.instanceDate >= todayStr; });
            cdEvents.sort(function(a,b) { return a.instanceDate.localeCompare(b.instanceDate); });
            
            for(var c=0; c<cdEvents.length; c++) {
                var cdEvt = cdEvents[c];
                if(processedIds[cdEvt.id]) continue; 
                processedIds[cdEvt.id] = true;
                
                var cUser = (cdEvt.userId === 'all') ? {id: 'all', name: 'All'} : users.find(function(u){ return u.id === cdEvt.userId }); 
                if(cUser && cUser.id !== 'all' && cUser.disabled) continue;
                var cIcon = cdEvt.type === 'birthday' ? '🎂' : '⏳';
                var cBg = currentIsNight ? "bg-gray-800 border-gray-700 hover:bg-gray-700 text-gray-200" : "bg-gray-50 border-gray-100 hover:bg-gray-100 text-gray-700";
                
                var instD = new Date(cdEvt.instanceDate + 'T00:00:00');
                var diffDays = Math.ceil((instD.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
                var dLabel = diffDays === 0 ? "Today!" : (diffDays === 1 ? "Tomorrow" : diffDays + " days");
                var dColor = diffDays <= 3 ? (currentIsNight ? "text-pink-400" : "text-pink-500") : (currentIsNight ? "text-indigo-400" : "text-indigo-600");
                if (diffDays === 0) dColor = "text-red-500 font-black animate-pulse";
                
                var cAvatar = getAvatarHtml(cUser, 'w-6 h-6', 'text-[10px]', '');
                countdownHtml += "<div class='flex items-center space-x-2 text-sm p-2 rounded border cursor-pointer transition " + cBg + "' data-id='" + cdEvt.id + "' data-inst='" + cdEvt.instanceDate + "' onclick='viewEvent(event, this.dataset.id, this.dataset.inst)'>" +
                    "<span class='text-lg leading-none'>" + cIcon + "</span><span class='font-bold truncate flex-1'>" + cdEvt.title + "</span><span class='text-xs uppercase tracking-wider font-extrabold " + dColor + "'>" + dLabel + "</span><div class='flex items-center space-x-1 shrink-0 ml-2'>" + cAvatar + "</div></div>";
            }
            
            var cCard = document.getElementById("cardCountdowns");
            var cTarget = document.getElementById("countdownsCardContent");
            if(countdownHtml === "") {
                if(cCard) cCard.classList.add("hidden");
            } else {
                if(cTarget) cTarget.innerHTML = countdownHtml;
                if(cCard) cCard.classList.remove("hidden");
            }
        }
'''
}

def getJsRendering3() {
    return '''
        function renderCalendar() {
            var year = currentDate.getFullYear(); var month = currentDate.getMonth();
            var firstDay = new Date(year, month, 1).getDay(); var daysInMonth = new Date(year, month + 1, 0).getDate();
            var calendarDays = document.getElementById("calendarDays"); calendarDays.innerHTML = "";
            var cellBg = currentIsNight ? "bg-gray-900 border-gray-800" : "bg-white border-gray-100";
            var emptyBg = currentIsNight ? "bg-gray-950/40" : "bg-gray-50/50";
            var textStyle = currentIsNight ? "text-gray-400" : "text-gray-500";
            for(var i = 0; i < firstDay; i++) { calendarDays.innerHTML += "<div class='calendar-cell " + emptyBg + "'></div>"; }
            
            var today = new Date(); var todayStr = today.getFullYear() + "-" + String(today.getMonth() + 1).padStart(2, "0") + "-" + String(today.getDate()).padStart(2, "0");
            for(var d = 1; d <= daysInMonth; d++) {
                var dayStr = year + "-" + String(month + 1).padStart(2, "0") + "-" + String(d).padStart(2, "0");
                var dayEventsHtml = "";
                for(var e=0; e<window.displayEvents.length; e++) {
                    var evt = window.displayEvents[e];
                    if(evt.instanceDate !== dayStr) continue;
                    if(activeFilterId !== 'ALL' && activeFilterId !== null && evt.userId !== activeFilterId && evt.userId !== 'all') continue;
                    var user = (evt.userId === 'all') ? {id: 'all', name: 'All'} : users.find(function(u){ return u.id === evt.userId }); 
                    if(user && user.id !== 'all' && user.disabled) continue;
                    var avatarTag = getAvatarHtml(user, 'avatar-sm', 'text-[8px]', '');
                    dayEventsHtml += "<div class='event-badge event-type-" + evt.type + "' data-id='" + evt.id + "' data-inst='" + evt.instanceDate + "' onclick='viewEvent(event, this.dataset.id, this.dataset.inst)'>" + avatarTag + "<span class='truncate min-w-0'>" + evt.title + "</span></div>";
                }
                var isToday = (dayStr === todayStr);
                var dateCircle = isToday ? "<span class='w-7 h-7 flex items-center justify-center bg-indigo-600 text-white rounded-full text-sm font-bold shadow-md'>" + d + "</span>" : "<span class='text-sm font-semibold mt-1 mr-1 " + textStyle + "'>" + d + "</span>";
                calendarDays.innerHTML += "<div class='calendar-cell cursor-pointer hover:opacity-80 transition " + cellBg + "' data-date='" + dayStr + "' onclick='openEventModal(this.dataset.date)'><div class='flex justify-end mb-1'>" + dateCircle + "</div><div class='flex-1 flex flex-col gap-1 overflow-y-auto hide-scrollbar w-full min-w-0'>" + dayEventsHtml + "</div></div>";
            }
            renderTopCards();
        }

        function renderStatus(status) {
            if(currentView !== 'calendar') return;
            var dateStr = new Date(currentDate.getFullYear(), currentDate.getMonth()).toLocaleString("default", { month: "long", year: "numeric" });
            var modeBadge = "<div class='inline-flex items-center px-2 py-0.5 mt-1 rounded text-[10px] font-bold bg-gray-200 text-gray-800 dark:bg-gray-800 dark:text-gray-300 uppercase tracking-widest'>" + (status.mode || "Unknown Mode") + "</div>";
            document.getElementById("headerTitle").innerHTML = "<div class='text-xs sm:text-sm font-semibold uppercase tracking-wide opacity-50'>" + (status.calendarName || "My Calendar") + "</div><div class='text-xl sm:text-2xl font-bold leading-none mt-0.5'>" + dateStr + "</div>" + modeBadge;
            
            var warnHtml = "";
            if (status.warnings) {
                for(var w=0; w<status.warnings.length; w++) {
                    var warn = status.warnings[w];
                    var bgClass = warn.type === 'critical' ? 'bg-red-600 text-white animate-pulse' : warn.type === 'severe' ? 'bg-orange-500 text-white animate-pulse' : warn.type === 'thermal' ? 'bg-cyan-500 text-white shadow-[0_0_15px_rgba(6,182,212,0.6)]' : warn.type === 'watch' ? 'bg-yellow-400 text-yellow-900' : 'bg-blue-500 text-white';
                    warnHtml += "<div class='w-full px-4 py-2 text-center font-bold uppercase tracking-widest text-sm shadow " + bgClass + "'>" + warn.msg + "</div>";
                }
            }
            var warningBanner = document.getElementById("warningBanner");
            if(warnHtml === "") { warningBanner.classList.add("hidden"); } else { warningBanner.classList.remove("hidden"); warningBanner.innerHTML = warnHtml; }

            var html = "";

            if (status.locks) { for(var i=0; i<status.locks.length; i++) { html += "<div class='flex items-center space-x-2 bg-red-100 text-red-700 px-3 py-1 rounded-full text-xs font-bold shrink-0'><div class='w-2 h-2 bg-red-500 rounded-full animate-pulse'></div><span>" + status.locks[i].name + " Unlocked</span></div>"; } }
            if (status.motion) { for(var i=0; i<status.motion.length; i++) { html += "<div class='flex items-center space-x-2 bg-amber-100 text-amber-700 px-3 py-1 rounded-full text-xs font-bold shrink-0'><div class='w-2 h-2 bg-amber-500 rounded-full animate-pulse'></div><span>" + status.motion[i].name + " Active</span></div>"; } }
            if (status.thermostat) {
                var isHeat = status.thermostat.state === 'heating';
                var tColor = isHeat ? 'bg-orange-100 text-orange-700' : 'bg-cyan-100 text-cyan-700';
                var tDot = isHeat ? 'bg-orange-500' : 'bg-cyan-500';
                var tIcon = isHeat ? '🔥' : '❄️';
                var tLabel = isHeat ? 'Heating' : 'Cooling';
                html += "<div class='flex items-center space-x-2 px-3 py-1 rounded-full text-xs font-bold shrink-0 " + tColor + "'><div class='w-2 h-2 rounded-full animate-pulse " + tDot + "'></div><span>" + tIcon + " " + status.thermostat.name + " " + tLabel + "</span></div>";
            }
            if (status.mailArrived) {
                html += "<div class='flex items-center space-x-2 bg-blue-100 text-blue-700 px-3 py-1 rounded-full text-xs font-bold shrink-0'><div class='w-2 h-2 bg-blue-500 rounded-full animate-pulse'></div><span>📬 Mail Has Arrived</span></div>";
            }
            var banner = document.getElementById("deviceBanner");
            if(html === "") { banner.classList.add("hidden"); } else { banner.classList.remove("hidden"); banner.innerHTML = html; }

            var weatherIcon = status.isOvercast ? "☁️" : "☀️"; var weatherText = status.isOvercast ? "Overcast" : "Sunny"; var innerContent = "";
            if (status.weather && status.weather.temp && status.weather.temp !== "--") {
                var wData = status.weather;
                var items = [
                    "<span class='flex items-center text-blue-100 font-bold'><span class='mr-1.5 text-base'>" + weatherIcon + "</span>" + weatherText + "</span>",
                    "<span class='flex items-center'><svg class='w-4 h-4 mr-1 text-blue-400' fill='none' stroke='currentColor' viewBox='0 0 24 24'><path stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z'></path></svg>" + wData.temp + "°F</span>",
                    "<span class='text-gray-300'><span class='text-gray-500 mr-1'>H/L:</span>" + wData.high + "° / " + wData.low + "°</span>",
                    "<span class='text-gray-300'><span class='text-gray-500 mr-1'>Wind:</span>" + wData.wind + " mph " + wData.windDir + "</span>",
                    "<span class='text-gray-300'><span class='text-gray-500 mr-1'>Rain:</span>" + wData.rain + " in (" + wData.rainRate + " in/hr)</span>",
                    "<span class='text-gray-300'><span class='text-gray-500 mr-1'>Sun:</span>" + wData.sun + "</span>",
                    "<span class='text-gray-300'><span class='text-gray-500 mr-1'>Lux:</span>" + wData.lux + "</span>"
                ];
                if(wData.lightning && wData.lightning !== "0" && wData.lightning !== 0) { items.push("<span class='flex items-center text-yellow-400'><svg class='w-4 h-4 mr-1' fill='currentColor' viewBox='0 0 20 20'><path fill-rule='evenodd' d='M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z' clip-rule='evenodd'></path></svg>" + wData.lightning + " strikes</span>"); }
                var baseBlock = "<div class='flex space-x-8 items-center px-4 shrink-0 min-w-max'>" + items.join("") + "</div>";
                innerContent = "<div class='ticker-content'>" + baseBlock + baseBlock + "</div>";
            } else {
                var fallback = "<div class='flex space-x-8 items-center px-4 shrink-0 min-w-max'><span class='flex items-center text-blue-100 font-bold'><span class='mr-1.5 text-base'>" + weatherIcon + "</span>" + weatherText + "</span></div>";
                innerContent = "<div class='ticker-content'>" + fallback + fallback + "</div>";
            }
            
            var wf = document.getElementById("weatherFooter");
            if (wf.getAttribute("data-weather") !== innerContent) { wf.innerHTML = innerContent; wf.setAttribute("data-weather", innerContent); }
            wf.classList.remove("hidden");
        }
'''
}

def getJsRendering4() {
    return '''
        function renderHealth() {
            var content = document.getElementById("healthContent");
            if (!content) return;
            var html = "";
            var isAll = (activeFilterId === 'ALL' || activeFilterId === null);

            var renderUserHealth = function(u) {
                if (u.disabled || !u.name) return "";
                var uSleep = (window.currentStatus && window.currentStatus.sleepScores && window.currentStatus.sleepScores[u.id] != null) ? window.currentStatus.sleepScores[u.id] : "--";
                var uEffort = (window.currentStatus && window.currentStatus.effortScores && window.currentStatus.effortScores[u.id] != null) ? window.currentStatus.effortScores[u.id] : "--";
                var uWeight = (window.currentStatus && window.currentStatus.weightScores && window.currentStatus.weightScores[u.id] != null) ? window.currentStatus.weightScores[u.id] : "--";
                var uSteps = (window.currentStatus && window.currentStatus.stepsScores && window.currentStatus.stepsScores[u.id] != null) ? window.currentStatus.stepsScores[u.id] : "--";

                var cardBg = currentIsNight ? "bg-gray-800 border-gray-700 text-gray-200" : "bg-white border-gray-200 text-gray-800";
                var statBg = currentIsNight ? "bg-gray-900 border-gray-700" : "bg-gray-50 border-gray-100";

                return "<div class='p-4 rounded-xl border shadow-sm mb-4 " + cardBg + "'>" +
                    "<div class='flex items-center mb-4'>" + getAvatarHtml(u, 'w-10 h-10', 'text-sm', '') + "<h3 class='text-lg font-bold ml-3'>" + u.name + "'s Metrics</h3></div>" +
                    "<div class='grid grid-cols-2 gap-3'>" +
                    "<div class='p-4 rounded-lg border flex flex-col items-center justify-center " + statBg + "'><span class='text-3xl mb-1'>💤</span><span class='text-[10px] uppercase font-bold text-gray-500'>Sleep Score</span><span class='text-xl font-black mt-1'>" + uSleep + "</span></div>" +
                    "<div class='p-4 rounded-lg border flex flex-col items-center justify-center " + statBg + "'><span class='text-3xl mb-1'>⚡</span><span class='text-[10px] uppercase font-bold text-gray-500'>Effort Score</span><span class='text-xl font-black mt-1'>" + uEffort + "</span></div>" +
                    "<div class='p-4 rounded-lg border flex flex-col items-center justify-center " + statBg + "'><span class='text-3xl mb-1'>👣</span><span class='text-[10px] uppercase font-bold text-gray-500'>Steps</span><span class='text-xl font-black mt-1'>" + uSteps + "</span></div>" +
                    "<div class='p-4 rounded-lg border flex flex-col items-center justify-center " + statBg + "'><span class='text-3xl mb-1'>⚖️</span><span class='text-[10px] uppercase font-bold text-gray-500'>Weight (lbs)</span><span class='text-xl font-black mt-1'>" + uWeight + "</span></div>" +
                    "</div></div>";
            };

            for(var i=0; i<users.length; i++) {
                var u = users[i];
                if (!isAll && u.id !== activeFilterId) continue;
                html += renderUserHealth(u);
            }

            if (html === "") html = "<div class='text-center text-gray-500 italic mt-6'>No health data available. Select a user or connect variables.</div>";
            content.innerHTML = html;
        }
        window.renderHealth = renderHealth;

        function renderScreensaver() {
            var today = new Date(); var tStr = today.getFullYear() + "-" + String(today.getMonth() + 1).padStart(2, "0") + "-" + String(today.getDate()).padStart(2, "0");
            var tomorrow = new Date(today); tomorrow.setDate(tomorrow.getDate() + 1); var tomStr = tomorrow.getFullYear() + "-" + String(tomorrow.getMonth() + 1).padStart(2, "0") + "-" + String(tomorrow.getDate()).padStart(2, "0");
            
            var upcoming = window.displayEvents.filter(function(e) { return e.instanceDate === tStr || e.instanceDate === tomStr; });
            upcoming.sort(function(a,b) { return a.instanceDate.localeCompare(b.instanceDate); });
            var evHtml = "";
            for(var i=0; i<upcoming.length; i++) {
                var ev = upcoming[i]; var u = (ev.userId === 'all') ? {id:'all', name:'All Users'} : users.find(function(user){ return user.id === ev.userId }); 
                if(u && u.id !== 'all' && u.disabled) continue;
                var label = ev.instanceDate === tStr ? "TODAY" : "TOMORROW"; var icon = ev.type === "birthday" ? "🎁" : "📅";
                evHtml += "<div class='flex items-center space-x-4 bg-gray-900 border border-gray-800 p-4 rounded-xl'><div class='text-3xl'>" + icon + "</div><div><div class='text-sm text-indigo-400 font-bold'>" + label + "</div><div class='text-xl font-medium text-white'>" + ev.title + "</div><div class='text-gray-400 text-sm'>" + (u ? u.name : "Unknown") + "</div></div></div>";
            }
            if(evHtml === "") evHtml = "<div class='text-gray-500 italic'>No upcoming events scheduled.</div>";
            document.getElementById("ssEvents").innerHTML = evHtml;

            var devHtml = "";
            if (window.currentStatus && window.currentStatus.locks) {
                window.currentStatus.locks.forEach(function(l) {
                    devHtml += "<div class='flex items-center text-red-400 bg-red-950/40 p-4 rounded-xl border border-red-900/50 mb-3'><span class='text-2xl mr-4'>🔓</span><span class='font-bold text-lg'>" + l.name + " is Unlocked</span></div>";
                });
            }
            if (window.currentStatus && window.currentStatus.motion) {
                window.currentStatus.motion.forEach(function(m) {
                    devHtml += "<div class='flex items-center text-amber-400 bg-amber-950/40 p-4 rounded-xl border border-amber-900/50 mb-3'><span class='text-2xl mr-4'>🏃</span><span class='font-bold text-lg'>" + m.name + " is Active</span></div>";
                });
            }
            if (window.currentStatus && window.currentStatus.thermostat) {
                var ts = window.currentStatus.thermostat;
                var isH = ts.state === 'heating';
                var tsCol = isH ? 'text-orange-400 bg-orange-950/40 border-orange-900/50' : 'text-cyan-400 bg-cyan-950/40 border-cyan-900/50';
                var tsIco = isH ? '🔥' : '❄️';
                devHtml += "<div class='flex items-center p-4 rounded-xl border mb-3 " + tsCol + "'><span class='text-2xl mr-4'>" + tsIco + "</span><span class='font-bold text-lg'>" + ts.name + " is " + (isH ? 'Heating' : 'Cooling') + "</span></div>";
            }
            if (window.currentStatus && window.currentStatus.mailArrived) {
                devHtml += "<div class='flex items-center text-blue-400 bg-blue-950/40 p-4 rounded-xl border border-blue-900/50 mb-3'><span class='text-2xl mr-4'>📬</span><span class='font-bold text-lg'>Mail Has Arrived</span></div>";
            }
            if (devHtml === "") devHtml = "<div class='text-gray-600 italic bg-gray-900 border border-gray-800 p-4 rounded-xl flex items-center'><span class='text-emerald-500 mr-3 text-xl'>✅</span> All secure. No activity.</div>";
            document.getElementById("ssDevices").innerHTML = devHtml;

            var notesHtml = "";
            var recentNotes = userNotes.slice().sort((a,b) => b.timestamp - a.timestamp).slice(0, 5);
            recentNotes.forEach(function(n) {
                var u = users.find(user => user.id === n.authorId); var uName = u ? u.name : "Someone";
                notesHtml += "<div class='bg-gray-900 border border-gray-800 p-4 rounded-xl mb-3'><div class='flex justify-between items-center text-xs text-gray-500 mb-2'><span class='font-bold text-gray-300'>" + uName + "</span><span>" + n.dateStr + "</span></div><div class='text-sm text-gray-200 line-clamp-2'>" + n.text + "</div></div>";
            });
            if (notesHtml === "") notesHtml = "<div class='text-gray-600 italic'>No recent notes.</div>";
            document.getElementById("ssNotes").innerHTML = notesHtml;
        }

        function renderDevices() {
            if (!window.currentStatus || !window.currentStatus.devices) return;
            var devs = window.currentStatus.devices;
            var html = "";
            
            var boxBg = currentIsNight ? "#1f2937" : "#ffffff";
            var boxBorder = currentIsNight ? "#374151" : "#e5e7eb";
            var titleColor = currentIsNight ? "#f3f4f6" : "#111827";
            var itemBg = currentIsNight ? "#374151" : "#f9fafb";
            var itemBorder = currentIsNight ? "#4b5563" : "#e5e7eb";
            var nameColor = currentIsNight ? "#ffffff" : "#111827";
            var statusColor = currentIsNight ? "#9ca3af" : "#6b7280";

            var renderGroup = function(title, arr, onVal, iconOn, iconOff, activeClass, inactiveClass) {
                if (!arr || arr.length === 0) return "";
                var gHtml = "<div class='p-4 rounded-xl shadow-sm mb-6' style='background-color: " + boxBg + "; border: 1px solid " + boxBorder + ";'><h3 class='font-bold text-xl mb-4 pb-2' style='color: " + titleColor + "; border-bottom: 1px solid " + boxBorder + "; margin-bottom: 1rem;'>" + title + "</h3><div class='grid grid-cols-1 sm:grid-cols-2 gap-3'>";
                arr.forEach(function(d) {
                    var isActive = (d.status === onVal);
                    var icon = isActive ? iconOn : iconOff;
                    var cColor = isActive ? activeClass : inactiveClass;
                    gHtml += "<div class='flex items-center p-3 rounded-xl shadow-sm transition' style='background-color: " + itemBg + "; border: 1px solid " + itemBorder + ";'><div class='flex items-center justify-center w-10 h-10 rounded-full mr-3 shrink-0 " + cColor + " text-xl'>" + icon + "</div><div class='flex flex-col truncate min-w-0'><span class='font-bold text-sm truncate' style='color: " + nameColor + ";'>" + d.name + "</span><span class='text-[10px] uppercase font-bold tracking-wider' style='color: " + statusColor + ";'>" + (d.status || "unknown") + "</span></div></div>";
                });
                gHtml += "</div></div>"; return gHtml;
            };

            html += renderGroup("Locks", devs.locks, "unlocked", "🔓", "🔒", "bg-red-100 text-red-600 dark:bg-red-900/50 dark:text-red-400", "bg-emerald-100 text-emerald-600 dark:bg-emerald-900/50 dark:text-emerald-400");
            html += renderGroup("Motion Sensors", devs.motion, "active", "🏃", "⏸️", "bg-amber-100 text-amber-600 dark:bg-amber-900/50 dark:text-amber-400", "bg-gray-200 text-gray-500 dark:bg-gray-800 dark:text-gray-400");
            html += renderGroup("Contact Sensors", devs.contact, "open", "🚪", "🚪", "bg-amber-100 text-amber-600 dark:bg-amber-900/50 dark:text-amber-400", "bg-emerald-100 text-emerald-600 dark:bg-emerald-900/50 dark:text-emerald-400");
            html += renderGroup("Lights / Switches", devs.lights, "on", "💡", "💡", "bg-yellow-100 text-yellow-600 dark:bg-yellow-900/50 dark:text-yellow-400", "bg-gray-200 text-gray-500 dark:bg-gray-800 dark:text-gray-400");

            if (html === "") html = "<div class='text-center text-gray-500 italic mt-10'>No local devices configured. Setup in Hubitat App.</div>";
            document.getElementById("devicesList").innerHTML = html;
        }

        function renderMusic() {
            if (!window.currentStatus || !window.currentStatus.music || window.currentStatus.music.length === 0) {
                document.getElementById("musicContent").innerHTML = "<div class='text-center text-gray-500 italic mt-10'>No Sonos speakers configured. Setup in Hubitat App.</div>"; return;
            }
            var speakers = window.currentStatus.music;
            if (!currentMusicSpk) currentMusicSpk = speakers[0].id;
            
            var spk = speakers.find(function(s) { return s.id === currentMusicSpk; }); if (!spk) spk = speakers[0];

            var cardBg = currentIsNight ? "#1f2937" : "#ffffff";
            var cardBorder = currentIsNight ? "#374151" : "#e5e7eb";
            var textColor = currentIsNight ? "#ffffff" : "#111827";
            var mutedColor = currentIsNight ? "#9ca3af" : "#6b7280";
            
            var favBg = currentIsNight ? "#374151" : "#f9fafb";
            var favBorder = currentIsNight ? "#4b5563" : "#e5e7eb";
            var favText = currentIsNight ? "#f3f4f6" : "#374151";

            var favBgActive = currentIsNight ? "#312e81" : "#eef2ff"; 
            var favBorderActive = currentIsNight ? "#6366f1" : "#6366f1"; 
            var favTextActive = currentIsNight ? "#a5b4fc" : "#4338ca"; 

            var selectHtml = "<div class='mb-4'><select onchange='window.currentMusicSpk=this.value; renderMusic();' class='w-full p-3 rounded-xl font-bold shadow-sm' style='background-color: " + cardBg + "; border: 1px solid " + cardBorder + "; color: " + textColor + ";'>";
            speakers.forEach(function(s) {
                var sel = (s.id === spk.id) ? "selected" : "";
                selectHtml += "<option value='" + s.id + "' " + sel + ">" + s.name + "</option>";
            });
            selectHtml += "</select></div>";

            var isPlaying = (spk.status === "playing");
            var playBtn = "<button onclick='controlMusic(\\"" + spk.id + "\\", \\"play\\", null, null)' class='w-16 h-16 flex items-center justify-center rounded-full shadow-lg transition transform hover:scale-105' style='background-color: #4f46e5; color: #ffffff;'><svg class='w-8 h-8 ml-1' fill='currentColor' viewBox='0 0 20 20'><path fill-rule='evenodd' d='M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z' clip-rule='evenodd'></path></svg></button>";
            var pauseBtn = "<button onclick='controlMusic(\\"" + spk.id + "\\", \\"pause\\", null, null)' class='w-16 h-16 flex items-center justify-center rounded-full shadow-lg transition transform hover:scale-105' style='background-color: #f59e0b; color: #ffffff;'><svg class='w-8 h-8' fill='currentColor' viewBox='0 0 20 20'><path fill-rule='evenodd' d='M18 10a8 8 0 11-16 0 8 8 0 0116 0zM7 8a1 1 0 012 0v4a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v4a1 1 0 102 0V8a1 1 0 00-1-1z' clip-rule='evenodd'></path></svg></button>";
            
            var favHtml = "";
            if (spk.favorites && spk.favorites.length > 0) {
                favHtml += "<div class='mt-6 pt-4' style='border-top: 1px solid " + cardBorder + ";'><h4 class='text-xs font-bold uppercase tracking-wider mb-3' style='color: " + mutedColor + ";'>Virtual Favorites</h4><div class='grid grid-cols-1 sm:grid-cols-2 gap-3'>";
                spk.favorites.forEach(function(f) {
                    var isActive = f.status === "on";
                    var bg = isActive ? favBgActive : favBg;
                    var border = isActive ? favBorderActive : favBorder;
                    var col = isActive ? favTextActive : favText;
                    var ring = isActive ? "box-shadow: 0 0 0 2px " + favBorderActive + ";" : "";
                    
                    favHtml += "<button onclick='controlMusic(\\"" + spk.id + "\\", \\"favorite\\", null, " + f.index + ")' class='p-3 rounded-lg font-semibold text-sm transition break-words whitespace-normal transform hover:opacity-80 active:scale-95' style='background-color: " + bg + "; border: 1px solid " + border + "; color: " + col + "; " + ring + "'>" + f.name + "</button>";
                });
                favHtml += "</div></div>";
            }
            
            var displayTrack = spk.track;
            if (!displayTrack || displayTrack.replace(/[,\\s]/g, '') === '') displayTrack = "No Track Playing";

            var html = selectHtml + "<div class='p-6 rounded-2xl border shadow-sm' style='background-color: " + cardBg + "; border-color: " + cardBorder + ";'><div class='text-center mb-6'><div class='w-24 h-24 mx-auto rounded-2xl flex items-center justify-center mb-4 shadow-inner' style='background-color: " + favBg + ";'><svg class='w-12 h-12' style='color: #818cf8;' fill='none' stroke='currentColor' viewBox='0 0 24 24'><path stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3'></path></svg></div><h2 class='text-xl font-bold px-4 break-words' style='color: " + textColor + ";'>" + displayTrack + "</h2><p class='text-sm uppercase tracking-widest mt-1 font-semibold' style='color: #6366f1;'>" + spk.status + "</p></div><div class='flex justify-center items-center gap-6 mb-6'>" + (isPlaying ? pauseBtn : playBtn) + "</div><div class='flex items-center gap-3'><svg class='w-5 h-5' style='color: " + mutedColor + ";' fill='none' stroke='currentColor' viewBox='0 0 24 24'><path stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M15.536 8.464a5 5 0 010 7.072M18.364 5.636a9 9 0 010 12.728M5 10v4a2 2 0 002 2h2l4 4V4L9 8H7a2 2 0 00-2 2z'></path></svg><input type='range' min='0' max='100' value='" + spk.level + "' onchange='controlMusic(\\"" + spk.id + "\\", \\"setLevel\\", this.value, null)' class='w-full h-2 rounded-lg appearance-none cursor-pointer' style='background-color: " + (currentIsNight ? '#374151' : '#e5e7eb') + "; accent-color: #4f46e5;'></div>" + favHtml + "</div>";
            document.getElementById("musicContent").innerHTML = html;
        }
'''
}

def getJsApi1() {
    return '''
        window.updateRedDots = function() {
            if (!window.currentStatus || !window.currentStatus.lastUpdates) return;
            var upd = window.currentStatus.lastUpdates;
            
            var checkDot = function(v, t) {
                var lv = localStorage.getItem("lastViewedServer_" + v) || 0;
                var dot = document.getElementById("dot_" + v);
                if (dot) {
                    if (t > lv && currentView !== v) dot.classList.remove("hidden");
                    else dot.classList.add("hidden");
                }
            };
            
            checkDot('calendar', upd.events);
            checkDot('notes', upd.notes);
            checkDot('menu', upd.menu);
            checkDot('polls', upd.polls);
            checkDot('todos', upd.todos);
        };

        async function fetchData(retryCount = 0) {
            try {
                var res = await fetch(getApiUrl("/api/data")); var data = await res.json();
                if(data.error) throw new Error(data.message); if(!res.ok) throw new Error("HTTP Error " + res.status);
                events = data.events || []; users = data.users || []; groceries = data.status.groceries || []; todos = data.status.todos || [];
                menuData = data.status.menu || { mon: "", tue: "", wed: "", thu: "", fri: "", sat: "", sun: "" };
                menuVotes = data.status.menuVotes || { mon: {up:[], down:[]}, tue: {up:[], down:[]}, wed: {up:[], down:[]}, thu: {up:[], down:[]}, fri: {up:[], down:[]}, sat: {up:[], down:[]}, sun: {up:[], down:[]} };
                userNotes = data.status.userNotes || []; polls = data.status.polls || []; window.currentStatus = data.status || {};
                
                buildDisplayEvents(); applyTheme(window.currentStatus.isNight || false); updateFormDropdowns();
                renderStatus(window.currentStatus); renderUserFilters(); renderCalendar(); updateRedDots();
                if(currentView === 'health') renderHealth(); if(currentView === 'grocery') renderGroceries(); if(currentView === 'menu') renderMenu(); if(currentView === 'notes') renderNotes(); if(currentView === 'polls') renderPolls(); if(currentView === 'todos') renderTodos();
                if(currentView === 'devices') renderDevices(); if(currentView === 'music') renderMusic();
            } catch (error) { 
                console.error("Error loading data", error); 
                if (retryCount < 2) { setTimeout(function() { fetchData(retryCount + 1); }, 1500); } 
                else { document.getElementById("headerTitle").innerHTML = "<div class='text-red-500 font-bold text-sm truncate'>" + error.message + "</div><div class='text-[10px] text-gray-400 font-semibold uppercase tracking-wide mt-0.5'>Check Hub/Token</div>"; }
            }
        }

        async function pollStatus() {
            if (window.isTyping) return;
            try {
                var res = await fetch(getApiUrl("/api/status")); var status = await res.json();
                if(status.error) throw new Error(status.message);
                if(res.ok) {
                    var oldUpdates = window.currentStatus ? window.currentStatus.lastUpdates : null;
                    var oldLocks = window.currentStatus ? JSON.stringify(window.currentStatus.locks) : "[]";
                    var oldMotion = window.currentStatus ? JSON.stringify(window.currentStatus.motion) : "[]";
                    var oldThermostat = window.currentStatus ? JSON.stringify(window.currentStatus.thermostat) : "null";
                    var oldWarnings = window.currentStatus ? JSON.stringify(window.currentStatus.warnings) : "[]";
                    var oldSick = window.currentStatus ? JSON.stringify(window.currentStatus.sickStatus) : "{}";
                    
                    var wakeTriggered = false;

                    if (oldUpdates && status.lastUpdates) {
                        if (status.lastUpdates.events > oldUpdates.events) wakeTriggered = true;
                        if (status.lastUpdates.notes > oldUpdates.notes) wakeTriggered = true;
                    }
                    
                    if (oldLocks !== JSON.stringify(status.locks || []) || 
                        oldMotion !== JSON.stringify(status.motion || []) ||
                        oldThermostat !== JSON.stringify(status.thermostat || null) ||
                        oldWarnings !== JSON.stringify(status.warnings || []) ||
                        oldSick !== JSON.stringify(status.sickStatus || {})) {
                        wakeTriggered = true;
                    }

                    window.currentStatus = status; groceries = status.groceries || []; todos = status.todos || todos; menuData = status.menu || menuData; menuVotes = status.menuVotes || menuVotes; userNotes = status.userNotes || []; polls = status.polls || [];
                    if(status.isNight !== currentIsNight) applyTheme(status.isNight);
                    renderUserFilters(); updateRedDots();

                    if (wakeTriggered && isSleepActive) {
                        isSleepActive = false;
                        document.getElementById('sleepOverlay').classList.add('hidden');
                        document.getElementById('screensaverOverlay').classList.remove('hidden');
                        isScreensaverActive = true;
                        idleTime = 300000;
                    }

                    if (currentView === 'calendar') { renderStatus(status); renderTopCards(); } 
                    else if (currentView === 'health') { renderHealth(); }
                    else if (currentView === 'grocery') { renderGroceries(); } 
                    else if (currentView === 'menu') { renderMenu(); } 
                    else if (currentView === 'notes') { renderNotes(); } 
                    else if (currentView === 'polls') { renderPolls(); }
                    else if (currentView === 'todos') { renderTodos(); }
                    else if (currentView === 'devices') { renderDevices(); }
                    else if (currentView === 'music') { renderMusic(); }
                    
                    if (isScreensaverActive) renderScreensaver();
                }
            } catch (error) { console.log("Status poll failed: " + error); }
        }
        
        function prevMonth() { currentDate.setMonth(currentDate.getMonth() - 1); renderCalendar(); pollStatus(); }
        function nextMonth() { currentDate.setMonth(currentDate.getMonth() + 1); renderCalendar(); pollStatus(); }
        window.prevMonth = prevMonth; window.nextMonth = nextMonth;
        
        async function submitEvent(e) {
            e.preventDefault();
            var payload = { id: document.getElementById("eventId").value, title: document.getElementById("eventTitle").value, date: document.getElementById("eventDate").value, time: document.getElementById("eventTime").value, type: document.getElementById("eventType").value, userId: document.getElementById("eventUserId").value, description: document.getElementById("eventDesc").value, location: document.getElementById("eventLocation").value, recurrence: document.getElementById("eventRecurrence").value, isCountdown: document.getElementById("eventIsCountdown").checked };
            try { var res = await fetch(getApiUrl("/api/events"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); if(res.ok) { closeEventModal(); fetchData(); showAlert("Event Saved", "Your event was successfully scheduled.", "success"); } } catch (error) { console.error(error); }
        }
        window.submitEvent = submitEvent;

        async function deleteEventReq(id, deleteType, instanceDate) {
            try { 
                if (deleteType === 'single') {
                    var ev = events.find(function(e) { return e.id === id; });
                    if (ev) {
                        if (!ev.exceptions) ev.exceptions = [];
                        ev.exceptions.push(instanceDate);
                    }
                } else {
                    events = events.filter(function(e) { return e.id !== id; });
                }
                buildDisplayEvents();
                renderTopCards();
                renderCalendar();
                closeViewModal();
                
                var res = await fetch(getApiUrl("/api/events/remove"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ id: id, deleteType: deleteType, instanceDate: instanceDate }) }); 
                if(res.ok) { fetchData(); showAlert("Event Deleted", "The event has been removed.", "success"); } 
            } catch (error) { console.error(error); }
        }
        window.deleteEventReq = deleteEventReq;

        function setStoreFilter(store) { activeStoreFilter = store; renderGroceries(); }
        window.setStoreFilter = setStoreFilter;
        
        function renderGroceries() {
            var listElem = document.getElementById("groceryList"); 
            if(!listElem) return;
            var scrollPos = listElem.scrollTop;
            var html = ""; var filtered = groceries;
            if(activeStoreFilter !== 'ALL') { filtered = groceries.filter(function(g) { return g.store === activeStoreFilter; }); }
            filtered.sort(function(a, b) { return (a.checked === b.checked) ? 0 : a.checked ? 1 : -1; });
            for(var i=0; i<filtered.length; i++) {
                var g = filtered[i]; var u = users.find(function(user){ return user.id === g.addedBy });
                var avatarTag = getAvatarHtml(u, 'w-7 h-7', 'text-[10px]', '');
                var textStyle = g.checked ? "line-through text-gray-400 dark:text-gray-500" : (currentIsNight ? "text-gray-100" : "text-gray-800");
                var boxClass = currentIsNight ? "bg-gray-800 border-gray-700" : "bg-white border-gray-200";
                html += "<div class='flex items-center justify-between p-3 rounded-xl border shadow-sm " + boxClass + "'><div class='flex items-center space-x-3 overflow-hidden min-w-0'><input type='checkbox' class='w-5 h-5 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 shrink-0' " + (g.checked ? "checked" : "") + " onclick='toggleGrocery(\\"" + g.id + "\\", this.checked)'><div class='flex flex-col truncate min-w-0'><span class='font-bold text-sm truncate " + textStyle + "'>" + g.name + "</span><span class='text-xs font-semibold uppercase tracking-wider text-indigo-500'>" + g.store + "</span></div></div><div class='shrink-0 ml-2'>" + avatarTag + "</div></div>";
            }
            if(html === "") html = "<div class='text-center text-gray-400 italic mt-6'>List is empty.</div>";
            
            if(listElem.innerHTML !== html) { 
                listElem.innerHTML = html; 
                listElem.scrollTop = scrollPos;
            }

            var filterContainer = document.getElementById("groceryStoreFilters"); var filtersHtml = ""; var filters = ["ALL", "Any", "Aldi", "Publix", "Costco", "Walmart", "Farmers Market"];
            for(var j=0; j<filters.length; j++) {
                var f = filters[j]; var activeClass = (activeStoreFilter === f) ? "bg-indigo-600 text-white border-indigo-600" : (currentIsNight ? "bg-gray-800 text-gray-300 border-gray-700 hover:bg-gray-700" : "bg-white text-gray-600 border border-gray-200 hover:bg-gray-50");
                filtersHtml += "<button onclick='setStoreFilter(\\"" + f + "\\")' class='px-4 py-1.5 rounded-full text-xs font-bold whitespace-nowrap shadow-sm transition border " + activeClass + "'>" + f + "</button>";
            }
            if(filterContainer && filterContainer.innerHTML !== filtersHtml) { filterContainer.innerHTML = filtersHtml; }
        }

        async function submitGrocery() {
            var nameField = document.getElementById("grocName"); var name = nameField.value; if(!name) return;
            var payload = { action: "add", name: name, store: document.getElementById("grocStore").value, userId: document.getElementById("grocUser").value };
            try { 
                nameField.value = ""; 
                groceries.push({ id: "temp_" + Date.now(), name: name, store: payload.store, addedBy: payload.userId, checked: false });
                renderGroceries();
                var res = await fetch(getApiUrl("/api/groceries"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); 
                if(res.ok) pollStatus(); 
            } catch (e) { console.error(e); }
        }
        window.submitGrocery = submitGrocery;

        async function toggleGrocery(id, checked) {
            var payload = { action: "toggle", id: id, checked: checked };
            try { var g = groceries.find(function(i) { return i.id === id; }); if(g) g.checked = checked; renderGroceries(); await fetch(getApiUrl("/api/groceries"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); } catch (e) { console.error(e); }
        }
        window.toggleGrocery = toggleGrocery;

        async function clearGroceries(actionType) {
            try { var res = await fetch(getApiUrl("/api/groceries/clear"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ action: actionType }) }); if(res.ok) pollStatus(); } catch (e) { console.error(e); }
        }
        window.clearGroceries = clearGroceries;
'''
}

def getJsApi2() {
    return '''
        function renderTodos() {
            var listElem = document.getElementById("todoList"); 
            if(!listElem) return;
            var scrollPos = listElem.scrollTop;
            var html = "";
            var filtered = todos;
            var isAll = (activeFilterId === 'ALL' || activeFilterId === null);
            
            if(!isAll) filtered = todos.filter(function(t) { return t.userId === activeFilterId; });
            filtered.sort(function(a, b) { return (a.checked === b.checked) ? b.timestamp - a.timestamp : a.checked ? 1 : -1; });

            if (filtered.length === 0) {
                html = "<div class='text-center text-gray-400 italic mt-6'>" + (isAll ? "Viewing all tasks. Select a specific user above to add a new task." : "No tasks for this user. Add one above!") + "</div>";
            } else {
                for(var i=0; i<filtered.length; i++) {
                    var t = filtered[i]; var u = users.find(function(user){ return user.id === t.userId });
                    var avatarTag = isAll ? getAvatarHtml(u, 'w-7 h-7', 'text-[10px]', '') : "";
                    var textStyle = t.checked ? "line-through text-gray-400 dark:text-gray-500" : (currentIsNight ? "text-gray-100" : "text-gray-800");
                    var boxClass = currentIsNight ? "bg-gray-800 border-gray-700" : "bg-white border-gray-200";
                    html += "<div class='flex items-center justify-between p-3 rounded-xl border shadow-sm " + boxClass + "'><div class='flex items-center space-x-3 overflow-hidden min-w-0'><input type='checkbox' class='w-5 h-5 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 shrink-0' " + (t.checked ? "checked" : "") + " onclick='toggleTodo(\\"" + t.id + "\\", this.checked)'><span class='font-bold text-sm truncate " + textStyle + "'>" + t.text + "</span></div><div class='shrink-0 ml-2'>" + avatarTag + "</div></div>";
                }
            }
            if(listElem.innerHTML !== html) { 
                listElem.innerHTML = html; 
                listElem.scrollTop = scrollPos;
            }
            
            var nameField = document.getElementById("todoName");
            if (isAll) { nameField.placeholder = "Select a specific user above to add tasks..."; nameField.disabled = true; }
            else { nameField.placeholder = "Add a new task..."; nameField.disabled = false; }
        }
        window.renderTodos = renderTodos;

        async function submitTodo() {
            var isAll = (activeFilterId === 'ALL' || activeFilterId === null);
            if(isAll) { showAlert("Select a User", "Please select a specific user profile at the top to assign this task.", "info"); return; }
            var nameField = document.getElementById("todoName"); var name = nameField.value; if(!name) return;
            var payload = { text: name, userId: activeFilterId };
            try { 
                nameField.value = ""; 
                todos.push({ id: "temp_" + Date.now(), text: payload.text, userId: payload.userId, checked: false, timestamp: Date.now() });
                renderTodos();
                var res = await fetch(getApiUrl("/api/todos"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); 
                if(res.ok) pollStatus(); 
            } catch (e) { console.error(e); }
        }
        window.submitTodo = submitTodo;

        async function toggleTodo(id, checked) {
            var payload = { id: id, checked: checked };
            try { var t = todos.find(function(i) { return i.id === id; }); if(t) t.checked = checked; renderTodos(); await fetch(getApiUrl("/api/todos/toggle"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); } catch (e) { console.error(e); }
        }
        window.toggleTodo = toggleTodo;

        async function clearTodos(actionType) {
            var payload = {};
            if(activeFilterId !== 'ALL' && activeFilterId !== null) { payload.userId = activeFilterId; }
            try { var res = await fetch(getApiUrl("/api/todos/clear"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); if(res.ok) pollStatus(); } catch (e) { console.error(e); }
        }
        window.clearTodos = clearTodos;

        function renderMenu() {
            var container = document.getElementById("menuContainer"); var days = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]; var labels = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]; var html = "";
            var inputClass = currentIsNight ? "bg-gray-800 border-gray-700 text-gray-200" : "bg-gray-50 border-gray-300 text-gray-800";
            var btnClass = currentIsNight ? "bg-gray-800 hover:bg-gray-700 border-gray-700 text-gray-300" : "bg-gray-100 hover:bg-gray-200 border-gray-200 text-gray-600";
            
            var actorId = (activeFilterId !== 'ALL' && activeFilterId !== null) ? activeFilterId : (document.getElementById("menuActor") ? document.getElementById("menuActor").value : null);
            var actingUser = users.find(function(u){ return u.id === actorId; });
            if (!actingUser && users.length > 0) actingUser = users[0];
            
            var isAdult = actingUser ? actingUser.isAdult : false;
            var readOnlyAttr = isAdult ? "" : "readonly";
            var opacityClass = isAdult ? "" : "opacity-60 pointer-events-none";

            for(var i=0; i<days.length; i++) {
                var d = days[i]; var val = menuData[d] || ""; var vData = menuVotes[d] || {up:[], down:[]}; var upCount = vData.up.length; var dnCount = vData.down.length;
                html += "<div><label class='block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1 ml-1'>" + labels[i] + "</label><div class='flex gap-2'><input type='text' id='menu_" + d + "' value='" + val + "' " + readOnlyAttr + " class='flex-1 p-2 rounded-lg text-sm border focus:ring-2 focus:ring-indigo-500 " + inputClass + " " + opacityClass + "'><button onclick='voteMenu(\\"" + d + "\\", \\"up\\")' class='px-3 rounded-lg border text-sm font-bold flex items-center gap-1 transition " + btnClass + "'>👍 " + upCount + "</button><button onclick='voteMenu(\\"" + d + "\\", \\"down\\")' class='px-3 rounded-lg border text-sm font-bold flex items-center gap-1 transition " + btnClass + "'>👎 " + dnCount + "</button></div></div>";
            }
            container.innerHTML = html;
            
            var btnSave = document.getElementById("btnSaveMenu");
            if (btnSave) {
                btnSave.style.display = isAdult ? "block" : "none";
            }
        }

        async function saveMenu() {
            var actorId = (activeFilterId !== 'ALL' && activeFilterId !== null) ? activeFilterId : document.getElementById("menuActor").value;
            var u = users.find(function(x){ return x.id === actorId; });
            
            if (!u || !u.isAdult) { showAlert("Access Denied", "Only adult profiles can save or edit the weekly menu!", "error"); return; }
            var days = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]; var payload = {};
            for(var i=0; i<days.length; i++) { payload[days[i]] = document.getElementById("menu_" + days[i]).value; }
            try { var res = await fetch(getApiUrl("/api/menu"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); if(res.ok) { showAlert("Success!", "Menu Saved Successfully! Previous votes have been reset.", "success"); pollStatus(); } } catch (e) { console.error(e); }
        }
        window.saveMenu = saveMenu;

        async function voteMenu(day, type) {
            var actorId = document.getElementById("menuActor").value; if (!actorId) { showAlert("Hold up", "Please select a profile to vote!", "info"); return; }
            var payload = { day: day, type: type, userId: actorId };
            try { var res = await fetch(getApiUrl("/api/menu/vote"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); if(res.ok) pollStatus(); } catch (e) { console.error(e); }
        }
        window.voteMenu = voteMenu;

        function renderNotes() {
            var listElem = document.getElementById("notesList"); 
            if(!listElem) return;
            var scrollPos = listElem.scrollTop;
            var html = ""; var sortedNotes = userNotes.slice().sort(function(a,b) { return b.timestamp - a.timestamp; });
            var boxClass = currentIsNight ? "bg-gray-800 border-gray-700 text-gray-200" : "bg-white border-gray-200 text-gray-800"; var dateClass = currentIsNight ? "text-gray-400" : "text-gray-500";
            var repBoxClass = currentIsNight ? "bg-gray-900 border-indigo-900" : "bg-gray-50 border-indigo-200"; var btnClass = currentIsNight ? "text-indigo-400 hover:text-indigo-300" : "text-indigo-600 hover:text-indigo-800"; var inputClass = currentIsNight ? "bg-gray-700 border-gray-600 text-gray-200" : "bg-white border-gray-300 text-gray-800";
            for(var i=0; i<sortedNotes.length; i++) {
                var n = sortedNotes[i]; var u = users.find(function(user){ return user.id === n.authorId }); var authorName = u ? u.name : "Unknown"; 
                var avatarTag = getAvatarHtml(u, 'w-8 h-8', 'text-[10px]', '');
                var repliesHtml = "";
                if (n.replies && n.replies.length > 0) {
                    repliesHtml += "<div class='mt-3 space-y-2'>";
                    for(var r=0; r<n.replies.length; r++) {
                        var rep = n.replies[r]; var ru = users.find(function(user){ return user.id === rep.authorId }); var rName = ru ? ru.name : "Unknown";
                        repliesHtml += "<div class='ml-4 pl-3 py-1.5 border-l-2 text-sm " + repBoxClass + "'><div class='flex justify-between items-baseline mb-0.5'><span class='font-bold text-xs'>" + rName + "</span><span class='text-[9px] uppercase font-semibold " + dateClass + "'>" + rep.dateStr + "</span></div><p class='whitespace-pre-wrap'>" + rep.text + "</p></div>";
                    }
                    repliesHtml += "</div>";
                }
                html += "<div class='flex items-start p-3 rounded-xl border shadow-sm space-x-3 " + boxClass + "'><div>" + avatarTag + "</div><div class='flex-1 min-w-0'><div class='flex justify-between items-baseline mb-1'><span class='font-bold text-sm'>" + authorName + "</span><span class='text-[10px] uppercase font-semibold " + dateClass + "'>" + n.dateStr + "</span></div><p class='text-sm whitespace-pre-wrap'>" + n.text + "</p>" + repliesHtml + "<div class='mt-2'><button onclick='document.getElementById(\\"rep_" + n.id + "\\").classList.toggle(\\"hidden\\")' class='text-xs font-bold transition " + btnClass + "'>Reply</button><div id='rep_" + n.id + "' class='hidden mt-2 flex gap-2'><input type='text' id='txt_" + n.id + "' class='flex-1 p-1.5 rounded border text-xs " + inputClass + "' placeholder='Write a reply...'><button onclick='submitReply(\\"" + n.id + "\\")' class='px-3 py-1.5 bg-indigo-600 text-white rounded font-bold text-xs shadow-sm'>Send</button></div></div></div><button onclick='deleteNote(\\"" + n.id + "\\")' class='shrink-0 text-red-400 hover:text-red-600 p-1'><svg class='w-4 h-4' fill='none' stroke='currentColor' viewBox='0 0 24 24'><path stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16'></path></svg></button></div>";
            }
            if(html === "") html = "<div class='text-center text-gray-400 italic mt-6'>No notes yet. Be the first to post!</div>"; 
            
            if(listElem.innerHTML !== html) { 
                listElem.innerHTML = html; 
                listElem.scrollTop = scrollPos;
            }
        }

        async function submitNote() {
            var txtObj = document.getElementById("newNoteText"); var txt = txtObj.value; if(!txt) return;
            var payload = { text: txt, authorId: document.getElementById("noteAuthor").value };
            try { 
                txtObj.value = ""; 
                userNotes.push({ id: "temp_" + Date.now(), text: payload.text, authorId: payload.authorId, timestamp: Date.now(), dateStr: new Date().toLocaleString([], {month:'short', day:'numeric', hour:'numeric', minute:'2-digit'}), replies: [] });
                renderNotes();
                var res = await fetch(getApiUrl("/api/notes"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); 
                if(res.ok) pollStatus(); 
            } catch (e) { console.error(e); }
        }
        window.submitNote = submitNote;
        
        async function submitReply(noteId) {
            var txtObj = document.getElementById("txt_" + noteId); var txt = txtObj.value; if(!txt) return;
            var payload = { noteId: noteId, text: txt, authorId: document.getElementById("noteAuthor").value };
            try { var res = await fetch(getApiUrl("/api/notes/reply"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); if(res.ok) pollStatus(); } catch (e) { console.error(e); }
        }
        window.submitReply = submitReply;
        
        async function deleteNote(id) {
            try { var res = await fetch(getApiUrl("/api/notes/" + id), { method: "DELETE" }); if(res.ok) pollStatus(); } catch (e) { console.error(e); }
        }
        window.deleteNote = deleteNote;
'''
}

def getJsApi3() {
    return '''
        function renderPolls() {
            var listElem = document.getElementById("pollsList"); var addCard = document.getElementById("pollsAddCard"); var html = "";
            var actorId = (activeFilterId !== 'ALL' && activeFilterId !== null) ? activeFilterId : (document.getElementById("menuActor") ? document.getElementById("menuActor").value : null);
            var actingUser = users.find(function(u){ return u.id === actorId; });
            if (!actingUser && users.length > 0) actingUser = users[0];
            var isAdult = actingUser ? actingUser.isAdult : false;

            if (polls.length >= 2 || !isAdult) addCard.classList.add("hidden"); else addCard.classList.remove("hidden");
            
            var boxClass = currentIsNight ? "bg-gray-900 border-gray-800" : "bg-white border-gray-200"; var textClass = currentIsNight ? "text-gray-200" : "text-gray-900";
            var optBgClass = currentIsNight ? "bg-gray-800 border-gray-700 text-gray-300 hover:bg-gray-700" : "bg-gray-50 border-gray-200 text-gray-700 hover:bg-gray-100";
            var fillClass = currentIsNight ? "bg-indigo-900" : "bg-indigo-100"; var activeOutline = currentIsNight ? "ring-2 ring-indigo-500" : "ring-2 ring-indigo-600";
            for (var i=0; i<polls.length; i++) {
                var p = polls[i]; var author = users.find(function(u){ return u.id === p.authorId; }); var authorName = author ? author.name : "Unknown";
                var totalVotes = 0; p.options.forEach(function(o){ totalVotes += o.votes.length; });
                var optsHtml = "";
                p.options.forEach(function(o){
                    var pct = totalVotes > 0 ? Math.round((o.votes.length / totalVotes) * 100) : 0;
                    var userVoted = actorId && o.votes.includes(actorId); var styleStr = userVoted ? " " + activeOutline : "";
                    optsHtml += "<button onclick='submitPollVote(\\"" + p.id + "\\", \\"" + o.id + "\\")' class='relative w-full overflow-hidden p-3 border rounded-lg flex justify-between items-center text-sm font-bold mb-2 transition " + optBgClass + styleStr + "'><div class='absolute left-0 top-0 bottom-0 " + fillClass + "' style='width: " + pct + "%'></div><span class='relative z-10'>" + o.text + "</span><span class='relative z-10 text-xs opacity-70'>" + pct + "% (" + o.votes.length + ")</span></button>";
                });
                var delHtml = ""; if (isAdult) { delHtml = "<button onclick='deletePoll(\\"" + p.id + "\\")' class='text-xs text-red-500 font-bold'>Delete Poll</button>"; }
                html += "<div class='p-4 rounded-xl border shadow-sm " + boxClass + "'><div class='flex justify-between items-start mb-3'><div class='font-bold text-lg " + textClass + "'>" + p.question + "</div>" + delHtml + "</div>" + optsHtml + "<div class='text-xs text-gray-500 text-right mt-2'>Created by " + authorName + "</div></div>";
            }
            if (html === "" && polls.length === 0) html = "<div class='text-center text-gray-400 italic mt-6'>No active polls. Create one below!</div>";
            listElem.innerHTML = html;
        }

        async function submitPoll() {
            var q = document.getElementById("pollQuestion").value; var o1 = document.getElementById("pollOpt1").value; var o2 = document.getElementById("pollOpt2").value; var o3 = document.getElementById("pollOpt3").value; var o4 = document.getElementById("pollOpt4").value; var author = document.getElementById("pollAuthor").value;
            if (!q || !o1 || !o2) { showAlert("Missing Details", "Please provide a question and at least 2 options.", "info"); return; }
            var payload = { question: q, options: [o1, o2, o3, o4], authorId: author };
            try {
                var res = await fetch(getApiUrl("/api/polls"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); var data = await res.json();
                if (data.status === "error") { showAlert("Whoops!", data.message, "error"); return; }
                document.getElementById("pollQuestion").value = ""; document.getElementById("pollOpt1").value = ""; document.getElementById("pollOpt2").value = ""; document.getElementById("pollOpt3").value = ""; document.getElementById("pollOpt4").value = "";
                pollStatus(); showAlert("Poll Created", "Your new family poll is live.", "success");
            } catch (e) { console.error(e); }
        }
        window.submitPoll = submitPoll;

        async function submitPollVote(pollId, optId) {
            var actorId = document.getElementById("menuActor").value; if (!actorId) { showAlert("Hold up", "Please select a user profile to vote!", "info"); return; }
            var payload = { pollId: pollId, optionId: optId, userId: actorId };
            try { var res = await fetch(getApiUrl("/api/polls/vote"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }); if(res.ok) pollStatus(); } catch (e) { console.error(e); }
        }
        window.submitPollVote = submitPollVote;

        async function deletePoll(id) {
            try { var res = await fetch(getApiUrl("/api/polls/" + id), { method: "DELETE" }); if(res.ok) pollStatus(); } catch (e) { console.error(e); }
        }
        window.deletePoll = deletePoll;
        
        async function controlMusic(spkId, action, value, favIndex) {
            try {
                await fetch(getApiUrl("/api/music/control"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ spkId: spkId, action: action, level: value, favIndex: favIndex }) });
                setTimeout(pollStatus, 1500); 
            } catch (e) { console.error(e); }
        }
        window.controlMusic = controlMusic;

        function formatTimeForView(t24) {
            if(!t24) return "";
            var parts = t24.split(":");
            var h = parseInt(parts[0], 10);
            var m = parts[1];
            var ampm = h >= 12 ? "PM" : "AM";
            h = h % 12; if (h === 0) h = 12;
            return h + ":" + m + " " + ampm;
        }

        function openEventModal(dateStr, editId) {
            document.getElementById("eventModal").classList.remove("hidden");
            if (editId) {
                var evt = events.find(function(e) { return e.id === editId; });
                if(evt) {
                    document.getElementById("eventId").value = evt.id; document.getElementById("eventTitle").value = evt.title; document.getElementById("eventDate").value = evt.date; document.getElementById("eventTime").value = evt.time || ""; document.getElementById("eventType").value = evt.type; document.getElementById("eventLocation").value = evt.location || ""; document.getElementById("eventDesc").value = evt.description || ""; document.getElementById("eventRecurrence").value = evt.recurrence || "none"; document.getElementById("eventIsCountdown").checked = evt.isCountdown || false; document.getElementById("eventModalTitle").innerText = "Edit Event Base"; document.getElementById("submitEventBtn").innerText = "Update Event";
                    var userOptions = "<option value='all' " + (evt.userId === 'all' ? "selected" : "") + ">All Users</option>"; 
                    for(var i=0; i<users.length; i++) { if(users[i].name && !users[i].disabled && users[i].isAdult) { var sel = (users[i].id === evt.userId) ? "selected" : ""; userOptions += "<option value='" + users[i].id + "' " + sel + ">" + users[i].name + "</option>"; } }
                    document.getElementById("eventUserId").innerHTML = userOptions; return;
                }
            }
            document.getElementById("eventId").value = ""; document.getElementById("eventTitle").value = ""; document.getElementById("eventLocation").value = ""; document.getElementById("eventTime").value = ""; document.getElementById("eventDesc").value = ""; document.getElementById("eventRecurrence").value = "none"; document.getElementById("eventIsCountdown").checked = false; document.getElementById("eventModalTitle").innerText = "Add Event"; document.getElementById("submitEventBtn").innerText = "Save Event";
            if(dateStr && typeof dateStr === 'string') { document.getElementById("eventDate").value = dateStr; } else if (!document.getElementById("eventDate").value) { var t = new Date(); document.getElementById("eventDate").value = t.getFullYear() + "-" + String(t.getMonth() + 1).padStart(2, "0") + "-" + String(t.getDate()).padStart(2, "0"); }
            var userOptionsNew = "<option value='all'>All Users</option>"; 
            for(var i=0; i<users.length; i++) { if(users[i].name && !users[i].disabled && users[i].isAdult) { userOptionsNew += "<option value='" + users[i].id + "'>" + users[i].name + "</option>"; } }
            document.getElementById("eventUserId").innerHTML = userOptionsNew;
        }
        window.openEventModal = openEventModal;
        
        function closeEventModal() { document.getElementById("eventModal").classList.add("hidden"); }
        window.closeEventModal = closeEventModal;

        function viewEvent(e, id, instDate) {
            e.stopPropagation(); var evt = events.find(function(ev){ return ev.id === id });
            if (!evt) return;
            var user = (evt.userId === 'all') ? {name: 'All Users'} : users.find(function(u){ return u.id === evt.userId }); 
            var userName = user ? user.name : "Unknown";
            var titleClass = currentIsNight ? "text-white" : "text-gray-900"; var forClass = currentIsNight ? "text-indigo-400" : "text-indigo-600"; 
            
            var cleanDate = instDate || evt.date;
            var displayDate = cleanDate;
            
            if (evt.time) displayDate += " at " + formatTimeForView(evt.time);

            var html = "<div class='flex items-center space-x-2 mb-2'><span class='text-xs font-bold px-2 py-1 rounded uppercase tracking-wide text-white " + (evt.type === 'birthday' ? 'bg-pink-500' : 'bg-blue-500') + "'>" + evt.type + "</span><span class='text-sm text-gray-500 font-medium'>" + displayDate + "</span></div><h3 class='text-2xl font-bold mb-1 " + titleClass + "'>" + evt.title + "</h3><p class='text-sm font-semibold mb-3 " + forClass + "'>For: " + userName + "</p>";
            
            if(evt.location) { 
                var mapsUrl = getMapUrl(evt.location); 
                var locBgClass = currentIsNight ? "bg-indigo-900/50 text-indigo-300 border-indigo-800 hover:bg-indigo-900/80" : "bg-indigo-50 text-indigo-600 border-indigo-100 hover:bg-indigo-100"; 
                html += "<div class='mb-3'><a href='" + mapsUrl + "' target='_blank' class='inline-flex items-center text-xs font-semibold border rounded-lg px-2.5 py-1.5 transition " + locBgClass + "'><svg class='w-3.5 h-3.5 mr-1' fill='none' stroke='currentColor' viewBox='0 0 24 24'><path stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z'></path><path stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M15 11a3 3 0 11-6 0 3 3 0 016 0z'></path></svg>" + evt.location + "</a></div>"; 
            }
            if(evt.description) { var descBg = currentIsNight ? "bg-gray-800 border-gray-700 text-gray-200" : "bg-gray-50 border-gray-100 text-gray-700"; html += "<div class='p-3 rounded-lg border text-sm whitespace-pre-wrap " + descBg + "'>" + evt.description + "</div>"; }
            document.getElementById("viewEventContent").innerHTML = html;
            
            if (evt.recurrence && evt.recurrence !== 'none') { 
                document.getElementById("deleteEventBtn").classList.add("hidden"); 
                document.getElementById("deleteRecurGroup").classList.remove("hidden"); 
                document.getElementById("deleteSingleBtn").onclick = function() { deleteEventReq(id, 'single', cleanDate); }; 
                document.getElementById("deleteAllBtn").onclick = function() { deleteEventReq(id, 'all', cleanDate); }; 
            } else { 
                document.getElementById("deleteEventBtn").classList.remove("hidden"); 
                document.getElementById("deleteRecurGroup").classList.add("hidden"); 
                document.getElementById("deleteEventBtn").onclick = function() { deleteEventReq(id, 'all', cleanDate); }; 
            }
            document.getElementById("editEventBtn").onclick = function() { closeViewModal(); openEventModal(null, id); };
            document.getElementById("viewEventModal").classList.remove("hidden");
        }
        window.viewEvent = viewEvent;
        
        function closeViewModal() { document.getElementById("viewEventModal").classList.add("hidden"); }
        window.closeViewModal = closeViewModal;

        function openSettingsModal() {
            var html = "";
            for(var i=0; i<4; i++) {
                var u = users[i] || { name: "", avatar: "", note: "", disabled: false, isAdult: true, mood: "" }; var chk = u.disabled ? "checked" : ""; var chkAdult = u.isAdult ? "checked" : ""; var safeName = (u.name && u.name !== "undefined") ? u.name : ""; var safeNote = (u.note && u.note !== "undefined") ? u.note : ""; var inputBgClass = "bg-gray-50 text-gray-900 border-gray-300 dark:bg-gray-700 dark:text-gray-100 dark:border-gray-600"; 
                html += "<div class='p-4 border border-gray-300 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 shadow-sm'><div class='flex justify-between items-center mb-3'><label class='block text-xs font-extrabold text-gray-700 dark:text-gray-300 uppercase tracking-wider'>Profile " + (i+1) + "</label><div class='flex gap-4'><label class='flex items-center space-x-1.5 cursor-pointer'><input type='checkbox' id='uIsAdult" + i + "' " + chkAdult + " class='w-4 h-4 rounded border-gray-400 text-indigo-600 focus:ring-indigo-500'><span class='text-[11px] font-bold text-gray-700 dark:text-gray-300 uppercase tracking-wide'>Adult</span></label><label class='flex items-center space-x-1.5 cursor-pointer'><input type='checkbox' id='uDisabled" + i + "' " + chk + " class='w-4 h-4 rounded border-gray-400 text-indigo-600 focus:ring-indigo-500'><span class='text-[11px] font-bold text-gray-700 dark:text-gray-300 uppercase tracking-wide'>Disable</span></label></div></div><div class='flex items-start space-x-4'><div class='shrink-0'><img id='preview" + i + "' src='" + (u.avatar || "data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=") + "' class='w-12 h-12 rounded-full object-cover bg-gray-200 dark:bg-gray-700'></div><div class='flex-1 space-y-2'><input type='text' id='uName" + i + "' value='" + safeName + "' placeholder='Name' class='w-full rounded p-2 border text-sm " + inputBgClass + "'><input type='text' id='uNote" + i + "' value='" + safeNote + "' placeholder='Add a short status note...' class='w-full rounded p-2 border text-sm " + inputBgClass + "'><input type='text' id='uAvatar" + i + "' value='" + (u.avatar || "") + "' placeholder='Avatar Image URL (Local or Web)' onchange='document.getElementById(\\"preview" + i + "\\").src = this.value' class='w-full rounded p-2 border text-sm " + inputBgClass + "'></div></div></div>";
            }
            document.getElementById("userSetupList").innerHTML = html; document.getElementById("settingsModal").classList.remove("hidden");
        }
        window.openSettingsModal = openSettingsModal;

        function closeSettingsModal() { document.getElementById("settingsModal").classList.add("hidden"); }
        window.closeSettingsModal = closeSettingsModal;

        async function submitUsers(e) {
            e.preventDefault(); var newUsers = [];
            for(var i = 0; i < 4; i++) { 
                newUsers.push({ 
                    id: "u" + (i+1), 
                    name: document.getElementById("uName" + i).value, 
                    avatar: document.getElementById("uAvatar" + i).value, 
                    note: document.getElementById("uNote" + i).value, 
                    disabled: document.getElementById("uDisabled" + i).checked, 
                    isAdult: document.getElementById("uIsAdult" + i).checked, 
                    mood: (users[i] ? users[i].mood : ""),
                    moodTimestamp: (users[i] ? users[i].moodTimestamp : 0)
                }); 
            }
            try { var res = await fetch(getApiUrl("/api/users"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(newUsers) }); if(res.ok) { closeSettingsModal(); fetchData(); showAlert("Saved", "User profiles updated successfully.", "success"); } } catch (error) { console.error(error); }
        }
        window.submitUsers = submitUsers;

        fetchData();
    '''
}
