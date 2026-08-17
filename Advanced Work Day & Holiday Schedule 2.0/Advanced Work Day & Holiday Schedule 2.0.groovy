/**
 * Advanced Work Day & Holiday Schedule 2.0
 */

definition(
    name: "Advanced Work Day & Holiday Schedule 2.0",
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
    page(name: "manageDatesPage")
}

String getHumanReadableStatus() {
    def s = state.scheduleState ?: "Unknown"
    def r = state.scheduleReason ?: "Waiting for initial evaluation..."
    
    if (s == "on") return "<span style='color:green;'><b>ACTIVE WORK DAY.</b></span> ${r}"
    if (s == "off" && r.contains("Sick")) return "<span style='color:#dc3545;'><b>SICK DAY OVERRIDE.</b></span> Feel better! Work schedule is suppressed."
    if (s == "off" && (r.contains("Holiday") || r.contains("Vacation"))) return "<span style='color:#007bff;'><b>HOLIDAY / PTO ACTIVE.</b></span> Relax, the work schedule is suppressed."
    if (s == "off") return "<span style='color:orange;'><b>OFF HOURS.</b></span> ${r}"
    
    return "<span style='color:gray;'><b>INITIALIZING.</b></span> Please configure schedule."
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            if (workSwitch) {
                def dfDay = new java.text.SimpleDateFormat("EEEE, MMMM d")
                dfDay.setTimeZone(location.timeZone)
                def currentDayStr = dfDay.format(new Date())
                
                def activeDaysStr = workDays ? workDays.join(", ") : "None Selected"
                
                def hoursStr = "All Day (24 Hrs)"
                if (!settings.allDayWork && settings.startTime && settings.endTime) {
                    def dfTime = new java.text.SimpleDateFormat("h:mm a")
                    dfTime.setTimeZone(location.timeZone)
                    def sTime = dfTime.format(timeToday(startTime, location.timeZone))
                    def eTime = dfTime.format(timeToday(endTime, location.timeZone))
                    hoursStr = "${sTime} - ${eTime}"
                }

                def holidayCount = observedHolidays ? observedHolidays.size() : 0
                
                def nextPTO = "None Scheduled"
                if (state.customSpecific && state.customSpecific.size() > 0) {
                    nextPTO = "📅 " + state.customSpecific.sort().first()
                    if (state.customSpecific.size() > 1) nextPTO += " <i>(+${state.customSpecific.size() - 1} more)</i>"
                }

                def currentState = state.scheduleState ?: "Unknown"
                def reason = state.scheduleReason ?: "Pending evaluation"
                def stateColor = currentState == "on" ? "green" : (reason.contains("Sick") ? "#dc3545" : (reason.contains("Holiday") || reason.contains("Vacation") ? "blue" : "orange"))
                
                def switchSw = workSwitch.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def sickSwStatus = sickSwitch ? (sickSwitch.currentValue("switch") == "on" ? "<span style='color:#dc3545; font-weight:bold;'>ACTIVE</span>" : "<span style='color:gray;'>Inactive</span>") : "<i>Not Configured</i>"

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
                    <thead><tr><th>Metric</th><th colspan="2">Status</th></tr></thead>
                    <tbody>
                        <tr><td colspan="3" class="dash-subhead">Schedule State</td></tr>
                        <tr><td class="dash-hl">Virtual Switch</td><td colspan="2" class="dash-val"><span style='color:${stateColor}; font-weight:bold;'>${currentState.toUpperCase()}</span></td></tr>
                        <tr><td class="dash-hl">Sick Override</td><td colspan="2" class="dash-val">${sickSwStatus}</td></tr>
                        <tr><td class="dash-hl">Active Reason</td><td colspan="2" class="dash-val"><i>${reason}</i></td></tr>
                        <tr><td class="dash-hl">Current Day</td><td colspan="2" class="dash-val"><b>${currentDayStr}</b></td></tr>

                        <tr><td colspan="3" class="dash-subhead">Configuration Overview</td></tr>
                        <tr><td class="dash-hl">Active Work Days</td><td colspan="2" class="dash-val" style="font-size:12px;">${activeDaysStr}</td></tr>
                        <tr><td class="dash-hl">Work Hours</td><td colspan="2" class="dash-val"><b>${hoursStr}</b></td></tr>
                        <tr><td class="dash-hl">Observed Holidays</td><td colspan="2" class="dash-val">${holidayCount} US Holidays Selected</td></tr>
                        <tr><td class="dash-hl">Upcoming PTO</td><td colspan="2" class="dash-val">${nextPTO}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
                
                paragraph "<div style='padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>" +
                          "<b>Virtual Output Targets:</b> Work Mode Switch: [${switchSw}]</div>"

                href(name: "manageDatesLink", page: "manageDatesPage", title: "🗓️ Manage Vacation Dates (PTO)", description: "Click here to add or remove specific PTO dates using the calendar.")
                
            } else {
                paragraph "<i>Configuration incomplete. Click section 1 below to assign the virtual switch.</i>"
            }
        }

        section("<b>Action History & Debugging</b>", hideable: true) {
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

        section("<b>1. Virtual Switch Target (Required)</b>", hideable: true, hidden: true) {
            paragraph "Select the virtual switch that will turn ON during work hours and OFF during holidays/off-hours."
            input "workSwitch", "capability.switch", title: "Work Mode Switch", required: true, multiple: false
        }
        
        section("<b>2. Standard Work Week</b>", hideable: true, hidden: true) {
            input "workDays", "enum", title: "Active Work Days", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: true, defaultValue: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
            
            input "allDayWork", "bool", title: "Enable All Day (Ignore Start/End Times)", defaultValue: false, submitOnChange: true
            
            if (!settings.allDayWork) {
                input "startTime", "time", title: "Work Day Start Time", required: true
                input "endTime", "time", title: "Work Day End Time", required: true
            } else {
                paragraph "<i>Switch will remain ON for the entire 24-hour period on active work days.</i>"
            }
        }

        section("<b>3. Standard US Holidays</b>", hideable: true, hidden: true) {
            paragraph "<i>Select the holidays your workplace observes. The system automatically calculates their exact dates each year.</i>"
            input "observedHolidays", "enum", title: "<b>Select Holidays to Observe</b>", options: [
                "New Year's Day", 
                "Martin Luther King Jr. Day", 
                "Presidents' Day", 
                "Memorial Day", 
                "Juneteenth", 
                "Independence Day", 
                "Labor Day", 
                "Columbus Day", 
                "Veterans Day", 
                "Thanksgiving Day", 
                "Day After Thanksgiving (Black Friday)",
                "Christmas Eve",
                "Christmas Day"
            ], multiple: true, required: false
        }
        
        section("<b>4. Overrides (Sick Day)</b>", hideable: true, hidden: true) {
            paragraph "Select a virtual switch that, when ON, will force your work schedule to OFF (e.g., if you wake up sick)."
            input "sickSwitch", "capability.switch", title: "Sick Day Override Switch", required: false, multiple: false
        }
        
        if (app.id) {
            section("<b>Global Actions & Overrides</b>", hideable: true, hidden: true) {
                input "forceEvalBtn", "button", title: "⚙️ Force Logic Evaluation"
            }
        }
    }
}

def manageDatesPage() {
    dynamicPage(name: "manageDatesPage", title: "<b>Manage Vacation Dates</b>", install: false, uninstall: false) {
        
        if (!state.customSpecific) state.customSpecific = []

        section("<b>Active Vacation Dates</b>") {
            if (state.customSpecific.size() == 0) {
                paragraph "<i>No vacation dates currently configured.</i>"
            } else {
                def table = "<table style='width:100%; border-collapse: collapse; font-size: 14px;'>"
                table += "<tr style='background-color:#eee;'><th style='padding:5px; text-align:left;'>Scheduled PTO (YYYY-MM-DD)</th></tr>"
                state.customSpecific.sort().each { date -> 
                    table += "<tr><td style='padding:5px; border-bottom:1px solid #ccc;'>📅 ${date}</td></tr>" 
                }
                table += "</table>"
                paragraph table
            }
        }

        section("<b>Add a Vacation Date</b>") {
            paragraph "Use the calendar picker to select a date you will be off."
            input "newSpecificDate", "date", title: "Select Date", required: false
            input "addSpecificBtn", "button", title: "➕ Add Vacation Date"
        }

        section("<b>Remove a Date</b>") {
            if (state.customSpecific.size() > 0) {
                input "dateToRemove", "enum", title: "Select a configured date to delete", options: state.customSpecific.sort(), required: false
                input "removeDateBtn", "button", title: "❌ Delete Selected Date"
            } else {
                paragraph "<i>No dates available to remove.</i>"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() {
    logInfo("Installed and initializing schedule.")
    if (!state.customSpecific) state.customSpecific = []
    if (!state.actionHistory) state.actionHistory = []
    initialize()
}

def updated() {
    logInfo("Updated configuration. Re-evaluating schedule.")
    unsubscribe()
    unschedule()
    if (!state.customSpecific) state.customSpecific = []
    if (!state.actionHistory) state.actionHistory = []
    initialize()
}

def initialize() {
    cleanupOldDates() 
    
    // Subscribe to the sick switch so it triggers immediately when flipped
    if (sickSwitch) {
        subscribe(sickSwitch, "switch", sickSwitchHandler)
    }
    
    // Check the schedule every 1 minute to ensure exact start/end times hit
    runEvery1Minute("evaluateSchedule")
    
    // Run the cleanup routine every day at 12:05 AM
    schedule("0 5 0 * * ?", cleanupOldDates)
    
    evaluateSchedule()
}

def sickSwitchHandler(evt) {
    logAction("Sick switch turned ${evt.value.toUpperCase()}. Re-evaluating schedule.")
    evaluateSchedule()
}

def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard data manually refreshed.")
    } else if (btn == "forceEvalBtn") {
        logAction("MANUAL OVERRIDE: Forcing logic evaluation.")
        cleanupOldDates()
        evaluateSchedule()
    } else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    } else if (btn == "addSpecificBtn") {
        if (settings.newSpecificDate) {
            def list = state.customSpecific ?: []
            if (!list.contains(settings.newSpecificDate)) {
                list << settings.newSpecificDate
                state.customSpecific = list
                logAction("Added vacation date: ${settings.newSpecificDate}")
            }
            app.updateSetting("newSpecificDate", [type: "date", value: ""])
        }
    } else if (btn == "removeDateBtn") {
        if (settings.dateToRemove) {
            def target = settings.dateToRemove
            def sList = state.customSpecific ?: []
            
            if (sList.contains(target)) sList.remove(target)
            
            state.customSpecific = sList
            logAction("Removed custom date: ${target}")
            
            app.updateSetting("dateToRemove", [type: "enum", value: ""])
        }
    }
}

def cleanupOldDates() {
    if (!state.customSpecific) return
    
    def dfSpecific = new java.text.SimpleDateFormat("yyyy-MM-dd")
    dfSpecific.setTimeZone(location.timeZone)
    def todayStr = dfSpecific.format(new Date())
    
    def initialSize = state.customSpecific.size()
    
    // Lexical string comparison works perfectly for YYYY-MM-DD formats
    def activeDates = state.customSpecific.findAll { it >= todayStr }
    
    if (activeDates.size() != initialSize) {
        state.customSpecific = activeDates
        logAction("Cleaned up expired vacation dates. Remaining active dates: ${activeDates.size()}")
    }
}

def evaluateSchedule() {
    if (!workSwitch) return

    def dfDay = new java.text.SimpleDateFormat("EEEE")
    dfDay.setTimeZone(location.timeZone)
    def currentDay = dfDay.format(new Date())

    // Safety check to ensure workDays is defined before checking contains
    def isWorkDay = workDays ? workDays.contains(currentDay) : false
    def isOffDay = isHoliday()
    def isSick = sickSwitch?.currentValue("switch") == "on"

    def targetState = "off"
    def reason = "Off Hours/Weekend"

    if (isSick) {
        targetState = "off"
        reason = "Sick Day Override Active"
    } else if (isOffDay) {
        targetState = "off"
        reason = "Holiday/Vacation Day"
    } else if (isWorkDay) {
        if (settings.allDayWork) {
            targetState = "on"
            reason = "Active Work Day (All-Day Mode)"
        } else if (settings.startTime && settings.endTime) {
            // Evaluates times dynamically
            def currTime = now()
            def start = timeToday(startTime, location.timeZone).time
            def end = timeToday(endTime, location.timeZone).time

            if (currTime >= start && currTime <= end) {
                targetState = "on"
                reason = "Active Work Hours"
            } else {
                targetState = "off"
                reason = "Outside Work Hours"
            }
        } else {
            reason = "Work Hours Not Configured"
        }
    }

    state.scheduleState = targetState
    state.scheduleReason = reason

    def currentState = workSwitch.currentValue("switch")
    
    if (currentState != targetState) {
        logAction("Schedule transition: Turning ${targetState.toUpperCase()} (${reason})")
        if (targetState == "on") {
            safeOn(workSwitch)
        } else {
            safeOff(workSwitch)
        }
    }
}

def isHoliday() {
    def cal = Calendar.getInstance(location.timeZone)
    cal.setTime(new Date())
    
    int month = cal.get(Calendar.MONTH) // 0-based: Jan=0, Dec=11
    int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2, Thu=5
    
    def hList = settings.observedHolidays ?: []

    // 1. Dynamic US Holidays Matcher
    if (hList.contains("New Year's Day") && month == Calendar.JANUARY && dayOfMonth == 1) return true
    if (hList.contains("Martin Luther King Jr. Day") && month == Calendar.JANUARY && dayOfWeek == Calendar.MONDAY && dayOfMonth >= 15 && dayOfMonth <= 21) return true
    if (hList.contains("Presidents' Day") && month == Calendar.FEBRUARY && dayOfWeek == Calendar.MONDAY && dayOfMonth >= 15 && dayOfMonth <= 21) return true
    if (hList.contains("Memorial Day") && month == Calendar.MAY && dayOfWeek == Calendar.MONDAY && dayOfMonth >= 25) return true
    if (hList.contains("Juneteenth") && month == Calendar.JUNE && dayOfMonth == 19) return true
    if (hList.contains("Independence Day") && month == Calendar.JULY && dayOfMonth == 4) return true
    if (hList.contains("Labor Day") && month == Calendar.SEPTEMBER && dayOfWeek == Calendar.MONDAY && dayOfMonth <= 7) return true
    if (hList.contains("Columbus Day") && month == Calendar.OCTOBER && dayOfWeek == Calendar.MONDAY && dayOfMonth >= 8 && dayOfMonth <= 14) return true
    if (hList.contains("Veterans Day") && month == Calendar.NOVEMBER && dayOfMonth == 11) return true
    if (hList.contains("Thanksgiving Day") && month == Calendar.NOVEMBER && dayOfWeek == Calendar.THURSDAY && dayOfMonth >= 22 && dayOfMonth <= 28) return true
    if (hList.contains("Day After Thanksgiving (Black Friday)") && month == Calendar.NOVEMBER && dayOfWeek == Calendar.FRIDAY && dayOfMonth >= 23 && dayOfMonth <= 29) return true
    if (hList.contains("Christmas Eve") && month == Calendar.DECEMBER && dayOfMonth == 24) return true
    if (hList.contains("Christmas Day") && month == Calendar.DECEMBER && dayOfMonth == 25) return true

    // 2. Custom Specific Dates Matcher (YYYY-MM-DD)
    def dfSpecific = new java.text.SimpleDateFormat("yyyy-MM-dd")
    dfSpecific.setTimeZone(location.timeZone)
    def todaySpecific = dfSpecific.format(new Date())
    
    if (state.customSpecific?.contains(todaySpecific)) return true

    return false
}

// === HARDWARE SAFE WRAPPERS ===
def safeOn(dev) {
    if (dev && dev.currentValue("switch") != "on") {
        try { dev.on() } catch (e) { log.error "Failed to turn ON ${dev.displayName}: ${e.message}" }
    }
}

def safeOff(dev) {
    if (dev && dev.currentValue("switch") != "off") {
        try { dev.off() } catch (e) { log.error "Failed to turn OFF ${dev.displayName}: ${e.message}" }
    }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}

def logDebug(msg) {
    if (debugEnable) log.debug "${app.label}: ${msg}"
}
