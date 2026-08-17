/**
 * Advanced School Pickup and Drop Off 2.0 
 *
 */
definition(
    name: "Advanced School Pickup and Drop Off 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "manageHolidaysPage")
}

String getHumanReadableStatus() {
    if (settings.masterEnableSwitch && settings.masterEnableSwitch.currentValue("switch") == "off") {
        return "<span style='color:red;'><b>PAUSED.</b></span> Master Switch is OFF. All routines disabled."
    }
    // Check Sick Day BEFORE School Day so the UI correctly highlights that a sick day is overriding the schedule
    if (settings.sickDaySwitch && settings.sickDaySwitch.currentValue("switch") == "on") {
        return "<span style='color:#ff8c00;'><b>SICK DAY.</b></span> Skipping bus routines for today."
    }
    if (settings.schoolDaySwitch && settings.schoolDaySwitch.currentValue("switch") != "on") {
        return "<span style='color:#888;'><b>NO SCHOOL TODAY.</b></span> Schedules are suppressed."
    }
    if (state.earlyArrivalDetected) {
        return "<span style='color:#1e90ff;'><b>EARLY ARRIVAL DETECTED.</b></span> Afternoon routines cancelled."
    }
    return "<span style='color:green;'><b>SYSTEM ACTIVE.</b></span> Tracking schedules normally."
}

String getFormattedTimerDisplay(timeInput, boolean isPm) {
    if (!timeInput) return "<span style='color:#888;'><i>Not Configured</i></span>"
    try {
        Date baseDate = timeToday(timeInput, location.timeZone)
        String baseStr = baseDate.format("h:mm a", location.timeZone)
        
        int offset = getSmartOffsetMinutes(!isPm)
        if (settings.enableSmartLearning && offset != 0) {
            Date adjDate = baseDate
            use(groovy.time.TimeCategory) { adjDate = adjDate + offset.minutes }
            String adjStr = adjDate.format("h:mm a", location.timeZone)
            String sign = offset > 0 ? "+" : ""
            return "<s>${baseStr}</s> &rarr; <b style='color:#0066cc;'>${adjStr}</b> <span style='color:#0066cc; font-size:11px;'>(${sign}${offset}m learned shift)</span>"
        }
        return "<b>${baseStr}</b>"
    } catch (e) {
        return "<i>Invalid Time</i>"
    }
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            if (app.id && settings.schoolDaySwitch) {
                def amCountdown = getCountdownText(settings.amStage3, false)
                def pmCountdown = getCountdownText(settings.pmStage3, true)
                
                long totalAm = (state.amTotalDoorTime ?: 0) as Long
                long countAm = (state.amDoorCount ?: 0) as Long
                def amAvg = "N/A"
                if (countAm > 0) {
                    long avgAmSecs = totalAm.intdiv(countAm)
                    amAvg = "${avgAmSecs.intdiv(60)}m ${avgAmSecs % 60}s"
                }

                long totalPm = (state.pmTotalDoorTime ?: 0) as Long
                long countPm = (state.pmDoorCount ?: 0) as Long
                def pmAvg = "N/A"
                if (countPm > 0) {
                    long avgPmSecs = totalPm.intdiv(countPm)
                    pmAvg = "${avgPmSecs.intdiv(60)}m ${avgPmSecs % 60}s"
                }

                int reqDays = settings.minLearningDays != null ? settings.minLearningDays as int : 10
                int amPts = state.amLearnedTimes?.size() ?: 0
                int pmPts = state.pmLearnedTimes?.size() ?: 0

                if (settings.enableSmartLearning) {
                    if (amPts >= reqDays) {
                        amAvg += "<br><span style='color: #0066cc; font-size: 11px;'><b>Auto-Shift: ${getSmartOffsetStr(true)}</b> (${amPts}/${reqDays} met)</span>"
                    } else {
                        amAvg += "<br><span style='color: #888888; font-size: 11px;'><i>Learning: ${amPts}/${reqDays} days</i></span>"
                    }
                    
                    if (pmPts >= reqDays) {
                        pmAvg += "<br><span style='color: #0066cc; font-size: 11px;'><b>Auto-Shift: ${getSmartOffsetStr(false)}</b> (${pmPts}/${reqDays} met)</span>"
                    } else {
                        pmAvg += "<br><span style='color: #888888; font-size: 11px;'><i>Learning: ${pmPts}/${reqDays} days</i></span>"
                    }
                }
                
                def df = new java.text.SimpleDateFormat("yyyy-MM-dd")
                df.setTimeZone(location.timeZone)
                def todayStr = df.format(new Date())
                
                def upcomingStr = "None"
                def allUpcoming = []
                
                if (state.offDates) allUpcoming += state.offDates.findAll { it >= todayStr }
                if (state.offRanges) allUpcoming += state.offRanges.findAll { it.end >= todayStr }.collect { it.label }
                if (state.calculatedAutoDates) allUpcoming += state.calculatedAutoDates.findAll { it.val >= todayStr }.collect { "${it.label} (${it.val})" }
                if (state.calculatedAutoRanges) allUpcoming += state.calculatedAutoRanges.findAll { it.end >= todayStr }.collect { "${it.label} (${it.start} to ${it.end})" }
                
                if (allUpcoming.size() > 0) {
                    upcomingStr = allUpcoming.sort().take(4).join("<br>")
                    if (allUpcoming.size() > 4) upcomingStr += "<br><i>(+${allUpcoming.size() - 4} more)</i>"
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
                    <thead><tr><th>Event Status</th><th>Stage 3 Countdown</th><th>Avg Door Time</th></tr></thead>
                    <tbody>
                        <tr><td colspan="3" class="dash-subhead">Live Tracker & Averages</td></tr>
                        <tr><td class="dash-hl">Morning Pickup</td><td style="color:#aa0000; font-weight:bold; font-size: 15px;">${amCountdown}</td><td>${amAvg}</td></tr>
                        <tr><td class="dash-hl">Afternoon Drop-off</td><td style="color:#aa0000; font-weight:bold; font-size: 15px;">${pmCountdown}</td><td>${pmAvg}</td></tr>
                        
                        <tr><td colspan="3" class="dash-subhead">Learned Active Schedules (Base &rarr; Active)</td></tr>
                        <tr>
                            <td class="dash-hl">Morning Pickup Timers</td>
                            <td colspan="2" class="dash-val" style="font-size:12px;">
                                <b>Stage 1:</b> ${getFormattedTimerDisplay(settings.amStage1, false)}<br>
                                <b>Stage 2:</b> ${getFormattedTimerDisplay(settings.amStage2, false)}<br>
                                <b>Stage 3:</b> ${getFormattedTimerDisplay(settings.amStage3, false)}<br>
                                <b>Clear/Off:</b> ${getFormattedTimerDisplay(settings.amClear, false)}
                            </td>
                        </tr>
                        <tr>
                            <td class="dash-hl">Afternoon Drop-off Timers</td>
                            <td colspan="2" class="dash-val" style="font-size:12px;">
                                <b>Stage 1:</b> ${getFormattedTimerDisplay(settings.pmStage1, true)}<br>
                                <b>Stage 2:</b> ${getFormattedTimerDisplay(settings.pmStage2, true)}<br>
                                <b>Stage 3:</b> ${getFormattedTimerDisplay(settings.pmStage3, true)}<br>
                                <b>Clear/Off:</b> ${getFormattedTimerDisplay(settings.pmClear, true)}
                            </td>
                        </tr>

                        <tr><td colspan="3" class="dash-subhead">Local Calendar & State</td></tr>
                        <tr><td class="dash-hl">Today's Local Status</td><td colspan="2" class="dash-val"><b>${state.localCalendarStatus ?: "Unknown"}</b></td></tr>
                        <tr><td class="dash-hl">Upcoming Off-Days</td><td colspan="2" class="dash-val" style="font-size:12px;">${upcomingStr}</td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
                
                def mstSw = settings.masterEnableSwitch?.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : (settings.masterEnableSwitch ? "<span style='color:red;'>OFF</span>" : "N/A")
                def schSw = settings.schoolDaySwitch?.currentValue("switch") == "on" ? "<span style='color:green; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                def sckSw = settings.sickDaySwitch?.currentValue("switch") == "on" ? "<span style='color:orange; font-weight:bold;'>ON</span>" : "<span style='color:gray;'>OFF</span>"
                
                paragraph "<div style='padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>" +
                          "<b>Virtual Switches:</b> Master: [${mstSw}] | School Day: [${schSw}] | Sick Day: [${sckSw}]</div>"

            } else {
                paragraph "<i>Configuration incomplete. Please assign the required switches and times below.</i>"
            }
        }

        section("<b>Action History & Debugging</b>", hideable: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true
            
            if (state.actionHistory) {
                def historyText = "<div style='margin-top: 10px; padding: 10px; background: #fff; border-radius: 4px; font-size: 12px; border: 1px solid #ccc; max-height: 180px; overflow-y: auto;'>"
                historyText += "<ul style='margin: 0; padding-left: 20px; color: #555;'>"
                state.actionHistory.each { entry -> historyText += "<li style='margin-bottom: 4px;'>${entry}</li>" }
                historyText += "</ul></div>"
                paragraph historyText
            } else {
                paragraph "<i>No recent activity logged. Waiting for events...</i>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>1. Master Control & Conditions</b>", hideable: true, hidden: true) {
            paragraph "MASTER SWITCH: If this switch is selected and turned OFF, the entire application is completely disabled."
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false

            input "schoolDaySwitch", "capability.switch", title: "School Day Virtual Switch", required: true
            
            paragraph "<hr><b>Local Calendar Management</b><br>Define single dates or entire date ranges where school is out. The system will automatically turn the School Day Switch off on these days and clean them up when they expire."
            href(name: "manageHolidaysLink", page: "manageHolidaysPage", title: "🗓️ Manage Holidays & Off-Days", description: "Click here to setup automatic annual holidays or add custom manual off-days.")

            input "allowedModes", "mode", title: "Allowed Location Modes", multiple: true, required: false
            input "sickDaySwitch", "capability.switch", title: "Sick Day Virtual Switch", required: false
            input "busLight", "capability.colorControl", title: "Select Hue Light", required: true
        }

        section("<b>2. Weather & Umbrella Warning</b>", hideable: true, hidden: true) {
            input "weatherDevice", "capability.sensor", title: "Weather Sensor (e.g., OpenWeatherMap)", required: false
            input "rainCondition", "text", title: "Weather condition indicating rain", defaultValue: "Rain"
            input "rainSwitch", "capability.switch", title: "Rain Virtual Switch", required: false
            input "sprinklingSwitch", "capability.switch", title: "Sprinkling Virtual Switch", required: false
        }

        section("<b>3. Notifications</b>", hideable: true, hidden: true) {
            input "notifyPhones", "capability.notification", title: "Send Push Alerts to Phones", multiple: true, required: false
            
            paragraph "<hr><b>Custom Notification Messages</b>"
            input "amImminentMsg", "text", title: "AM Imminent Message", defaultValue: "The school bus is imminent! Time to head to the door.", required: true
            input "pmArrivedMsg", "text", title: "PM Arrived Message", defaultValue: "The school bus has arrived at the afternoon stop.", required: true
            input "amMissedMsg", "text", title: "AM Missed Bus Message", defaultValue: "WARNING: The bus arrived, but the door/motion conditions were not met!", required: true
            input "pmMissedMsg", "text", title: "PM Missed Bus Message", defaultValue: "CRITICAL: The school bus dropped off, but the front door hasn't opened!", required: true
            input "notifyOnSave", "bool", title: "Send Push Notification when safe arrival/departure is saved? (Includes recorded time)", defaultValue: true
        }

        section("<b>4. Safe Arrival & Departure (Sensors)</b>", hideable: true, hidden: true) {
            input "doorSensor", "capability.contactSensor", title: "Front Door Sensor", required: false
            input "motionSensor", "capability.motionSensor", title: "Outside Drop Location Motion Sensor (Optional)", required: false
            input "luxThreshold", "number", title: "Max Lux Threshold for Motion", defaultValue: 1000
            input "ignorePmMotion", "bool", title: "Ignore motion requirement in the afternoon?", defaultValue: false
            input "departureTimeout", "number", title: "Minutes after AM Stage 3 before 'Missed Bus' alert", defaultValue: 5
            input "arrivalTimeout", "number", title: "Minutes to wait for door/motion before afternoon alert", defaultValue: 15
        }

        section("<b>5. Early Dismissal & Lock Overrides</b>", hideable: true, hidden: true) {
            input "earlyDismissalSwitch", "capability.switch", title: "Early Dismissal Virtual Switch", required: false
            input "earlyOffset", "number", title: "Minutes to shift afternoon schedule earlier", defaultValue: 120
            
            paragraph "<hr><b>Smart Lock Early Arrival Detection</b><br>If a designated code is entered after 12:00 PM, the system will assume an early release and automatically cancel the afternoon bus routines."
            input "frontDoorLock", "capability.lock", title: "Select Door Lock", required: false, submitOnChange: true
            
            if (frontDoorLock) {
                def lockCodes = getLockCodesMap(frontDoorLock)
                if (lockCodes) {
                    input "kidLockCode", "enum", title: "Select the Lock Code to trigger Early Arrival", options: lockCodes, required: false
                } else {
                    paragraph "<i>No lock codes found or the lock does not support code reporting.</i>"
                }
            }
        }

        section("<b>6. Integration & External Overrides</b>", hideable: true, hidden: true) {
            paragraph "<b>Freeze other applications during sequence</b><br>Select a Virtual Switch here. This app will turn it ON during an active bus sequence. You can use that switch in your other apps to 'freeze' or 'disable' them until the sequence completes."
            input "overrideSwitch", "capability.switch", title: "State Override Switch (Freezes external apps)", required: false
        }

        section("<b>7. Smart Learning (Auto-Adjust)</b>", hideable: true, hidden: true) {
            input "enableSmartLearning", "bool", title: "Enable Smart Time Learning", defaultValue: false
            input "minLearningDays", "number", title: "Required school days before adjusting", defaultValue: 10
            
            if (enableSmartLearning) {
                def amPts = state.amLearnedTimes?.size() ?: 0
                def pmPts = state.pmLearnedTimes?.size() ?: 0
                def reqDays = settings.minLearningDays != null ? settings.minLearningDays as int : 10
                
                def amShift = amPts >= reqDays ? getSmartOffsetStr(true) : "Learning Phase"
                def pmShift = pmPts >= reqDays ? getSmartOffsetStr(false) : "Learning Phase"
                
                paragraph "<b>Learning Status:</b><br>AM Data Points: ${amPts}/${reqDays} (Current Shift: ${amShift})<br>PM Data Points: ${pmPts}/${reqDays} (Current Shift: ${pmShift})"
            }

            paragraph "<hr><b>New School Year / School Transfer Reset</b><br>Clear all stored door averages and learned shifts to re-learn schedules for a new school year or new school."
            input "resetLearningBtn", "button", title: "🧹 Clear All Learned Times & Averages"
        }

        section("<b>8. Morning Pickup Schedule</b>", hideable: true, hidden: true) {
            input "amStage1", "time", title: "Time for Stage 1 (Get Ready)", required: false
            input "amColor1", "enum", title: "Stage 1 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Green"
            input "amSwitch1", "capability.switch", title: "Stage 1 Virtual Switch Trigger (Optional)", required: false
            
            paragraph "<hr>"
            input "amStage2", "time", title: "Time for Stage 2 (Almost Here)", required: false
            input "amColor2", "enum", title: "Stage 2 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Yellow"
            input "amSwitch2", "capability.switch", title: "Stage 2 Virtual Switch Trigger (Optional)", required: false
            
            paragraph "<hr>"
            input "amStage3", "time", title: "Time for Stage 3 (Bus Imminent)", required: false
            input "amColor3", "enum", title: "Stage 3 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Red"
            input "amSwitch3", "capability.switch", title: "Stage 3 Virtual Switch Trigger (Optional)", required: false
            
            paragraph "<hr>"
            input "amClear", "time", title: "Time to turn light OFF & reset", required: false
        }

        section("<b>9. Afternoon Drop-Off Schedule</b>", hideable: true, hidden: true) {
            input "pmStage1", "time", title: "Time for Stage 1 (Bus left school)", required: false
            input "pmColor1", "enum", title: "Stage 1 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Green"
            input "pmSwitch1", "capability.switch", title: "Stage 1 Virtual Switch Trigger (Optional)", required: false
            
            paragraph "<hr>"
            input "pmStage2", "time", title: "Time for Stage 2 (Approaching neighborhood)", required: false
            input "pmColor2", "enum", title: "Stage 2 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Yellow"
            input "pmSwitch2", "capability.switch", title: "Stage 2 Virtual Switch Trigger (Optional)", required: false
            
            paragraph "<hr>"
            input "pmStage3", "time", title: "Time for Stage 3 (At the stop)", required: false
            input "pmColor3", "enum", title: "Stage 3 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Red"
            input "pmSwitch3", "capability.switch", title: "Stage 3 Virtual Switch Trigger (Optional)", required: false
            
            paragraph "<hr>"
            input "pmClear", "time", title: "Time to turn light OFF & reset", required: false
        }

        if (app.id) {
            section("<b>Global Actions, Testing & Overrides</b>", hideable: true, hidden: true) {
                input "evalSchoolDayBtn", "button", title: "📅 Evaluate Today's School Day Status"
                input "resetStatsBtn", "button", title: "🗑️ Reset Average Door Statistics"
                input "resetLearningBtn", "button", title: "🧹 Clear All Learned Times & Averages"
                
                paragraph "<hr><b>Rapid Testing (Cycles every 30s)</b>"
                input "testAmBtn", "button", title: "Test Morning Pickup Routine"
                input "testPmBtn", "button", title: "Test Afternoon Drop-Off Routine"
                input "stopTestBtn", "button", title: "Stop Active Test & Turn Off Light"
            }
        }
    }
}

def manageHolidaysPage() {
    dynamicPage(name: "manageHolidaysPage", title: "<b>Manage Holidays & Off-Days</b>", install: false, uninstall: false) {
        
        if (!state.offDates) state.offDates = []
        if (!state.offRanges) state.offRanges = []

        section("<b>Set & Forget: Annual Auto-Calculated Holidays</b>") {
            paragraph "Enable these toggles to have the system automatically calculate the dates mathematically for the current school year. These will seamlessly shift year over year without manual updating."
            
            input "autoLaborDay", "bool", title: "Labor Day (1st Monday in Sep)", defaultValue: false, submitOnChange: true
            input "autoVeteransDay", "bool", title: "Veterans Day (Nov 11)", defaultValue: false, submitOnChange: true
            input "autoThanksgiving", "bool", title: "Thanksgiving Week (Mon-Fri of Thanksgiving)", defaultValue: false, submitOnChange: true
            input "autoWinterBreak", "bool", title: "Winter Break (Dec 20 - Jan 2)", defaultValue: false, submitOnChange: true
            input "autoMLK", "bool", title: "MLK Jr. Day (3rd Monday in Jan)", defaultValue: false, submitOnChange: true
            input "autoMardiGras", "bool", title: "Mardi Gras (Mon & Tue before Ash Wednesday)", defaultValue: false, submitOnChange: true
            input "autoPresidents", "bool", title: "Presidents' Day (3rd Monday in Feb)", defaultValue: false, submitOnChange: true
            input "autoSpringBreak", "bool", title: "Spring Break (Week before Easter)", defaultValue: false, submitOnChange: true
            input "autoMemorialDay", "bool", title: "Memorial Day (Last Monday in May)", defaultValue: false, submitOnChange: true
            
            input "calcAutoHolidaysBtn", "button", title: "🔄 Force Auto-Calculation Now"
        }

        section("<b>Manual Single Off-Days (Overrides & Teacher Workdays)</b>") {
            paragraph "Use the calendar picker to add specific, non-standard dates."
            input "newOffDate", "date", title: "Select Date", required: false
            input "addOffDateBtn", "button", title: "➕ Add Single Date"
            
            if (state.offDates.size() > 0) {
                def table = "<table style='width:100%; border-collapse: collapse; font-size: 14px; margin-bottom: 10px;'>"
                state.offDates.sort().each { date -> table += "<tr><td style='padding:5px; border-bottom:1px solid #ccc;'>📅 ${date}</td></tr>" }
                table += "</table>"
                paragraph table
                
                input "dateToRemove", "enum", title: "Select a single date to delete", options: state.offDates.sort(), required: false
                input "removeOffDateBtn", "button", title: "❌ Delete Single Date"
            } else {
                paragraph "<i>No manual single dates configured.</i>"
            }
        }
        
        section("<b>Manual Off-Day Ranges (Custom Breaks)</b>") {
            paragraph "Add non-standard multi-day breaks."
            input "newRangeStart", "date", title: "Select Start Date", required: false
            input "newRangeEnd", "date", title: "Select End Date", required: false
            input "addRangeBtn", "button", title: "➕ Add Date Range"
            
            if (state.offRanges.size() > 0) {
                def table = "<table style='width:100%; border-collapse: collapse; font-size: 14px; margin-bottom: 10px;'>"
                state.offRanges.sort { it.start }.each { range -> table += "<tr><td style='padding:5px; border-bottom:1px solid #ccc;'>📅 ${range.label}</td></tr>" }
                table += "</table>"
                paragraph table
                
                def rangeLabels = state.offRanges.collect { it.label }.sort()
                input "rangeToRemove", "enum", title: "Select a date range to delete", options: rangeLabels, required: false
                input "removeRangeBtn", "button", title: "❌ Delete Date Range"
            } else {
                paragraph "<i>No manual date ranges configured.</i>"
            }
        }
    }
}

def installed() {
    log.debug "Installed Advanced School Pickup and Drop Off"
    initialize()
}

def updated() {
    log.debug "Updated Advanced School Pickup and Drop Off"
    unsubscribe()
    unschedule()
    
    initialize()
}

def initialize() {
    state.amTotalDoorTime = state.amTotalDoorTime ?: 0
    state.amDoorCount = state.amDoorCount ?: 0
    state.pmTotalDoorTime = state.pmTotalDoorTime ?: 0
    state.pmDoorCount = state.pmDoorCount ?: 0
    
    // Smart Learning state variables
    state.amLearnedTimes = state.amLearnedTimes ?: []
    state.pmLearnedTimes = state.pmLearnedTimes ?: []
    if (!state.lastResetYear) state.lastResetYear = new Date().format("yyyy").toInteger()
    
    // Local Calendar State
    if (!state.offDates) state.offDates = []
    if (!state.offRanges) state.offRanges = []
    if (!state.calculatedAutoDates) state.calculatedAutoDates = []
    if (!state.calculatedAutoRanges) state.calculatedAutoRanges = []
    
    state.isTesting = false
    state.lightActiveByApp = state.lightActiveByApp ?: false 
    state.actionHistory = state.actionHistory ?: []
    state.earlyArrivalDetected = state.earlyArrivalDetected ?: false

    logAction("System Initialized/Updated.")

    if (settings.doorSensor) {
        subscribe(settings.doorSensor, "contact.open", doorOpenedHandler)
    }
    
    if (settings.motionSensor) {
        subscribe(settings.motionSensor, "motion.active", motionActiveHandler)
        subscribe(settings.motionSensor, "illuminance", luxHandler) // Dynamically dismiss motion if sun rises mid-routine
    }

    if (settings.frontDoorLock) {
        subscribe(settings.frontDoorLock, "lock.unlocked", lockHandler)
    }
    
    // Listen for the Sick Day switch to turn ON or OFF
    if (settings.sickDaySwitch) {
        subscribe(settings.sickDaySwitch, "switch", sickDayHandler)
    }

    // New unified daily scheduling engine
    schedule("0 1 0 ? * *", "scheduleDailyEvents")
    
    generateAnnualAutoHolidays()
    cleanupOldHolidays()
    evaluateSchoolDay()
    scheduleDailyEvents() 
}

// --- Logging & Helper Methods ---

def logAction(String msg) {
    if(txtEnable) log.info "${app.label}: ${msg}"
    def timeString = new Date().format("MM/dd h:mm a", location.timeZone)
    def entry = "<b>${timeString}</b>: ${msg}"
    def list = state.actionHistory ?: []
    list.add(0, entry)
    if (list.size() > 30) list = list[0..29] 
    state.actionHistory = list
}

def isSystemEnabled() {
    if (settings.masterEnableSwitch && settings.masterEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def isSchoolDay() {
    return settings.schoolDaySwitch?.currentValue("switch") == "on"
}

def isSickDay() {
    return settings.sickDaySwitch?.currentValue("switch") == "on"
}

def isAllowedMode() {
    if (!settings.allowedModes) return true
    return settings.allowedModes.contains(location.mode)
}

def isItRaining() {
    if (settings.rainSwitch && settings.rainSwitch.currentValue("switch") == "on") return true
    if (settings.sprinklingSwitch && settings.sprinklingSwitch.currentValue("switch") == "on") return true
    String currentCondition = settings.weatherDevice?.currentValue("weather") ?: ""
    return currentCondition.contains(settings.rainCondition ?: "Rain")
}

def isMotionUsable() {
    if (!settings.motionSensor) return false
    try {
        def luxValue = settings.motionSensor.currentValue("illuminance")
        if (luxValue != null) {
            int maxLux = settings.luxThreshold != null ? settings.luxThreshold as int : 1000
            if ((luxValue as int) > maxLux) {
                return false // Lux is too high, ignore motion sensor completely
            }
        }
    } catch (e) {
        // Ignore error if sensor doesn't support illuminance
    }
    return true
}

// --- Lock Integration Methods ---

def getLockCodesMap(lockDevice) {
    def codes = [:]
    try {
        def lockCodesJson = lockDevice.currentValue("lockCodes")
        if (lockCodesJson) {
            def parsedCodes = new groovy.json.JsonSlurper().parseText(lockCodesJson)
            parsedCodes.each { slot, data ->
                codes[slot] = "${data.name} (Slot ${slot})"
            }
        }
    } catch (e) {
        log.error "Error parsing lock codes: ${e}"
    }
    return codes
}

def lockHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (evt.value == "unlocked") {
        if (!settings.kidLockCode) return
        
        String targetSlot = settings.kidLockCode.toString()
        String dataStr = evt.data?.toString() ?: ""
        boolean codeMatched = false
        
        def match = dataStr =~ /codeId['"]?\s*[:=]\s*['"]?(\d+)['"]?/
        if (match) {
            if (match[0][1] == targetSlot) {
                codeMatched = true
            }
        }
        
        if (codeMatched) {
            checkEarlyArrival()
        } else {
            if(debugEnable) log.debug "Door unlocked, but payload (${dataStr}) did not match slot ${targetSlot}"
        }
    }
}

def checkEarlyArrival() {
    Calendar cal = Calendar.getInstance(location.timeZone)
    cal.setTime(new Date())
    int hour = cal.get(Calendar.HOUR_OF_DAY)
    
    // Trigger if unlocked anytime 12:00 PM or later
    if (hour >= 12) {
        logAction("Early arrival detected via Lock Code! Disabling PM routines for the rest of today.")
        state.earlyArrivalDetected = true
        state.hasArrived = true
        state.waitingForArrival = false
        
        unschedule("pmSetStage1")
        unschedule("pmSetStage2")
        unschedule("pmSetStage3")
        turnLightOff()
    }
}

def sickDayHandler(evt) {
    if (!isSystemEnabled()) return 
    logAction("Sick Day switch changed to ${evt.value}. Re-evaluating School Day status.")
    evaluateSchoolDay()
}

// --- Dynamic Unified Scheduling Engine ---

def scheduleDailyEvents() {
    state.earlyArrivalDetected = false

    Calendar cal = Calendar.getInstance(location.timeZone)
    int currentYear = cal.get(Calendar.YEAR)
    int currentMonth = cal.get(Calendar.MONTH) // 0-indexed, 7 is August
    
    // Annual Smart Learning Reset
    if (currentMonth == 7 && state.lastResetYear != currentYear) {
        logAction("Annual Smart Learning Reset triggered (August). Clearing historical averages.")
        state.amLearnedTimes = []
        state.pmLearnedTimes = []
        state.lastResetYear = currentYear
    }

    // Auto Holiday Generational Sweep (Runs every August)
    if (currentMonth == 7 && state.lastAutoHolidayYear != currentYear) {
        logAction("August transition detected. Generating dynamic auto-holidays for the new school year.")
        generateAnnualAutoHolidays()
        state.lastAutoHolidayYear = currentYear
    }
    
    cleanupOldHolidays()
    evaluateSchoolDay()

    scheduleTodayEvent(settings.amStage1, "amSetStage1", false)
    scheduleTodayEvent(settings.amStage2, "amSetStage2", false)
    scheduleTodayEvent(settings.amStage3, "amSetStage3", false)
    scheduleTodayEvent(settings.amClear, "amTurnLightOff", false)

    scheduleTodayEvent(settings.pmStage1, "pmSetStage1", true)
    scheduleTodayEvent(settings.pmStage2, "pmSetStage2", true)
    scheduleTodayEvent(settings.pmStage3, "pmSetStage3", true)
    scheduleTodayEvent(settings.pmClear, "pmTurnLightOff", true)
}

def scheduleTodayEvent(timeInput, handlerMethod, isPm = false) {
    if (!timeInput) return
    Date scheduledTime = timeToday(timeInput, location.timeZone)
    
    if (isPm && settings.earlyDismissalSwitch?.currentValue("switch") == "on") {
        int offset = settings.earlyOffset ?: 120
        use(groovy.time.TimeCategory) { scheduledTime = scheduledTime - offset.minutes }
    }
    
    int smartOffset = getSmartOffsetMinutes(!isPm)
    if (smartOffset != 0) {
        use(groovy.time.TimeCategory) { scheduledTime = scheduledTime + smartOffset.minutes }
    }
    
    if (scheduledTime.after(new Date())) {
        runOnce(scheduledTime, handlerMethod, [overwrite: true])
    }
}

def amTurnLightOff() { turnLightOff() }
def pmTurnLightOff() { turnLightOff() }

// ------------------------------------

def getEpochTime(timeStr, isPm = false) {
    if (!timeStr) return new Date().time + 3600000 
    
    Date target = timeToday(timeStr, location.timeZone)
    
    if (isPm && settings.earlyDismissalSwitch?.currentValue("switch") == "on") {
        int offset = settings.earlyOffset ?: 120
        use(groovy.time.TimeCategory) { target = target - offset.minutes }
    }
    
    int smartOffset = getSmartOffsetMinutes(!isPm)
    if (smartOffset != 0) {
        use(groovy.time.TimeCategory) { target = target + smartOffset.minutes }
    }
    
    return target.time
}

def getCountdownText(timeStr, isPm = false) {
    if (!timeStr) return "Not Configured"
    def now = new Date()
    def target = timeToday(timeStr, location.timeZone)
    
    if (isPm && settings.earlyDismissalSwitch?.currentValue("switch") == "on") {
        int offset = settings.earlyOffset ?: 120
        use(groovy.time.TimeCategory) { target = target - offset.minutes }
    }
    
    int smartOffset = getSmartOffsetMinutes(!isPm)
    if (smartOffset != 0) {
        use(groovy.time.TimeCategory) { target = target + smartOffset.minutes }
    }
    
    if (target.before(now)) return "Completed Today"
    
    long diff = target.time - now.time
    long hours = diff.intdiv(3600000)
    long mins = (diff % 3600000).intdiv(60000)
    
    if (hours > 0) return "${hours}h ${mins}m"
    return "${mins} Minutes"
}

// --- Smart Learning Core Methods ---

def recordSmartTime(boolean isAm) {
    Calendar cal = Calendar.getInstance(location.timeZone)
    int minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    
    def list = isAm ? (state.amLearnedTimes ?: []) : (state.pmLearnedTimes ?: [])
    list << minOfDay
    if (list.size() > 14) list = list.drop(list.size() - 14) // Keep trailing 14 days
    
    if (isAm) state.amLearnedTimes = list else state.pmLearnedTimes = list
    logAction("Smart Learning: Recorded ${isAm ? 'Morning' : 'Afternoon'} data point at min ${minOfDay}")
}

def getSmartOffsetMinutes(boolean isAm) {
    if (!settings.enableSmartLearning) return 0
    def list = isAm ? state.amLearnedTimes : state.pmLearnedTimes
    int minDays = settings.minLearningDays != null ? settings.minLearningDays as int : 10
    
    if (!list || list.size() < minDays) return 0
    
    int sum = 0
    list.each { sum += it }
    int avgMinOfDay = sum.intdiv(list.size())
    
    def targetInput = isAm ? settings.amStage3 : settings.pmStage3
    if (!targetInput) return 0
    
    Date targetDate = timeToday(targetInput, location.timeZone)
    Calendar tCal = Calendar.getInstance(location.timeZone)
    tCal.setTime(targetDate)
    int targetMinOfDay = tCal.get(Calendar.HOUR_OF_DAY) * 60 + tCal.get(Calendar.MINUTE)
    
    return avgMinOfDay - targetMinOfDay
}

def getSmartOffsetStr(boolean isAm) {
    int offset = getSmartOffsetMinutes(isAm)
    if (offset == 0) return "None (Using Default)"
    if (offset > 0) return "+${offset} mins"
    return "${offset} mins"
}

def getHueColor(colorName) {
    switch(colorName) {
        case "Red": return [hue: 100, saturation: 100]
        case "Green": return [hue: 39, saturation: 100]
        case "Blue": return [hue: 65, saturation: 100]
        case "Yellow": return [hue: 16, saturation: 100]
        case "Orange": return [hue: 10, saturation: 100]
        case "Purple": return [hue: 75, saturation: 100]
        case "Pink": return [hue: 90, saturation: 100]
        case "White": return [hue: 0, saturation: 0]
        default: return [hue: 39, saturation: 100]
    }
}

// --- Dynamic Date Algorithms ---

def getNthDayOfMonth(year, month, dayOfWeek, n) {
    Calendar cal = Calendar.getInstance(location.timeZone)
    cal.set(year, month, 1, 0, 0, 0)
    int firstDay = cal.get(Calendar.DAY_OF_WEEK)
    int offset = dayOfWeek - firstDay
    if (offset < 0) offset += 7
    int day = 1 + offset + (n - 1) * 7
    cal.set(Calendar.DAY_OF_MONTH, day)
    return cal.getTime()
}

def getLastDayOfMonth(year, month, dayOfWeek) {
    Calendar cal = Calendar.getInstance(location.timeZone)
    cal.set(year, month, 1, 0, 0, 0)
    int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    cal.set(Calendar.DAY_OF_MONTH, maxDay)
    int lastDay = cal.get(Calendar.DAY_OF_WEEK)
    int offset = lastDay - dayOfWeek
    if (offset < 0) offset += 7
    cal.set(Calendar.DAY_OF_MONTH, maxDay - offset)
    return cal.getTime()
}

def getEaster(year) {
    // Computus Algorithm
    int a = year % 19
    int b = (int)(year / 100)
    int c = year % 100
    int d = (int)(b / 4)
    int e = b % 4
    int f = (int)((b + 8) / 25)
    int g = (int)((b - f + 1) / 3)
    int h = (19 * a + b - d - g + 15) % 30
    int i = (int)(c / 4)
    int k = c % 4
    int l = (32 + 2 * e + 2 * i - h - k) % 7
    int m = (int)((a + 11 * h + 22 * l) / 451)
    int month = (int)((h + l - 7 * m + 114) / 31)
    int day = ((h + l - 7 * m + 114) % 31) + 1
    
    Calendar cal = Calendar.getInstance(location.timeZone)
    cal.set(year, month - 1, day, 0, 0, 0)
    return cal.getTime()
}

def addDays(Date date, int days) {
    Calendar c = Calendar.getInstance(location.timeZone)
    c.setTime(date)
    c.add(Calendar.DAY_OF_MONTH, days)
    return c.getTime()
}

def generateAnnualAutoHolidays() {
    Calendar cal = Calendar.getInstance(location.timeZone)
    int currentYear = cal.get(Calendar.YEAR)
    int currentMonth = cal.get(Calendar.MONTH) // 0-indexed, 7 = Aug
    
    // Fall term of year, Spring term of year+1
    int schoolYearStart = (currentMonth < 7) ? currentYear - 1 : currentYear
    int schoolYearEnd = schoolYearStart + 1
    
    def newAutoDates = []
    def newAutoRanges = []
    def df = new java.text.SimpleDateFormat("yyyy-MM-dd")
    df.setTimeZone(location.timeZone)
    
    if (settings.autoLaborDay) newAutoDates << [val: df.format(getNthDayOfMonth(schoolYearStart, 8, Calendar.MONDAY, 1)), label: "Labor Day"]
    if (settings.autoVeteransDay) newAutoDates << [val: "${schoolYearStart}-11-11", label: "Veterans Day"]
    
    if (settings.autoThanksgiving) {
        Date tDay = getNthDayOfMonth(schoolYearStart, 10, Calendar.THURSDAY, 4)
        newAutoRanges << [start: df.format(addDays(tDay, -3)), end: df.format(addDays(tDay, 1)), label: "Thanksgiving Break"]
    }
    
    if (settings.autoWinterBreak) {
        newAutoRanges << [start: "${schoolYearStart}-12-20", end: "${schoolYearEnd}-01-02", label: "Winter Break"]
    }
    
    if (settings.autoMLK) newAutoDates << [val: df.format(getNthDayOfMonth(schoolYearEnd, 0, Calendar.MONDAY, 3)), label: "MLK Jr. Day"]
    if (settings.autoPresidents) newAutoDates << [val: df.format(getNthDayOfMonth(schoolYearEnd, 1, Calendar.MONDAY, 3)), label: "Presidents' Day"]
    
    if (settings.autoMardiGras || settings.autoSpringBreak) {
        Date easter = getEaster(schoolYearEnd)
        if (settings.autoMardiGras) {
            newAutoRanges << [start: df.format(addDays(easter, -48)), end: df.format(addDays(easter, -47)), label: "Mardi Gras"]
        }
        if (settings.autoSpringBreak) {
            newAutoRanges << [start: df.format(addDays(easter, -6)), end: df.format(addDays(easter, -2)), label: "Spring Break"]
        }
    }
    
    if (settings.autoMemorialDay) newAutoDates << [val: df.format(getLastDayOfMonth(schoolYearEnd, 4, Calendar.MONDAY)), label: "Memorial Day"]
    
    state.calculatedAutoDates = newAutoDates
    state.calculatedAutoRanges = newAutoRanges
    log.debug "Auto-Calculated Holiday Roster updated for School Year ${schoolYearStart}-${schoolYearEnd}"
}

// --- Local Calendar Handling ---

def evaluateSchoolDay() {
    def df = new java.text.SimpleDateFormat("yyyy-MM-dd")
    df.setTimeZone(location.timeZone)
    def todayStr = df.format(new Date())

    Calendar cal = Calendar.getInstance(location.timeZone)
    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)

    boolean isOff = false
    def reason = "School Day"

    if (isWeekend) {
        isOff = true
        reason = "Weekend"
    } else if (settings.sickDaySwitch && settings.sickDaySwitch.currentValue("switch") == "on") {
        isOff = true
        reason = "Sick Day Active"
    } else {
        // 1. Check auto calculated dates
        if (!isOff && state.calculatedAutoDates) {
            def matched = state.calculatedAutoDates.find { it.val == todayStr }
            if (matched) {
                isOff = true
                reason = "Auto: ${matched.label}"
            }
        }
        
        // 2. Check auto calculated ranges
        if (!isOff && state.calculatedAutoRanges) {
            def matched = state.calculatedAutoRanges.find { todayStr >= it.start && todayStr <= it.end }
            if (matched) {
                isOff = true
                reason = "Auto: ${matched.label}"
            }
        }

        // 3. Check manual off dates
        if (!isOff && state.offDates?.contains(todayStr)) {
            isOff = true
            reason = "Configured Off-Day"
        }
        
        // 4. Check manual off ranges
        if (!isOff && state.offRanges) {
            for (range in state.offRanges) {
                if (todayStr >= range.start && todayStr <= range.end) {
                    isOff = true
                    reason = "Holiday Range (${range.start} to ${range.end})"
                    break
                }
            }
        }
    }

    if (isOff) {
        if (settings.schoolDaySwitch?.currentValue("switch") != "off") {
            settings.schoolDaySwitch?.off()
            logAction("Local Calendar evaluated: Marked as OFF-DAY (${reason}).")
        }
        state.localCalendarStatus = "<span style='color:red;'>Off-Day (${reason})</span>"
    } else {
        if (settings.schoolDaySwitch?.currentValue("switch") != "on") {
            settings.schoolDaySwitch?.on()
            logAction("Local Calendar evaluated: Marked as SCHOOL DAY.")
        }
        state.localCalendarStatus = "<span style='color:green;'>Active School Day</span>"
    }
}

def cleanupOldHolidays() {
    def df = new java.text.SimpleDateFormat("yyyy-MM-dd")
    df.setTimeZone(location.timeZone)
    def todayStr = df.format(new Date())

    if (state.offDates) state.offDates = state.offDates.findAll { it >= todayStr }
    if (state.offRanges) state.offRanges = state.offRanges.findAll { it.end >= todayStr }
}

def appButtonHandler(btn) {
    if (btn == "evalSchoolDayBtn") {
        logAction("Manual Local Calendar Evaluation Triggered.")
        cleanupOldHolidays()
        evaluateSchoolDay()
    } else if (btn == "calcAutoHolidaysBtn") {
        logAction("Manual force-recalculation of Annual Auto Holidays triggered.")
        generateAnnualAutoHolidays()
        cleanupOldHolidays()
        evaluateSchoolDay()
    } else if (btn == "resetStatsBtn") {
        logAction("Reset Average Door Statistics.")
        state.amTotalDoorTime = 0
        state.amDoorCount = 0
        state.pmTotalDoorTime = 0
        state.pmDoorCount = 0
    } else if (btn == "clearLogBtn") {
        state.actionHistory = []
    } else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    } else if (btn == "refreshDashboardBtn") {
        logAction("Dashboard data manually refreshed.")
    } else if (btn == "testAmBtn") {
        logAction("Initiating Morning TEST")
        startAmTest()
    } else if (btn == "testPmBtn") {
        logAction("Initiating Afternoon TEST")
        startPmTest()
    } else if (btn == "stopTestBtn") {
        logAction("Stopped Active TEST")
        turnLightOff()
    } else if (btn == "resetLearningBtn") {
        logAction("Manual Reset: All Learned Times & Door Averages cleared for New School Year/School.")
        state.amLearnedTimes = []
        state.pmLearnedTimes = []
        state.amTotalDoorTime = 0
        state.amDoorCount = 0
        state.pmTotalDoorTime = 0
        state.pmDoorCount = 0
        scheduleDailyEvents()
    } else if (btn == "addOffDateBtn") {
        if (settings.newOffDate) {
            def list = state.offDates ?: []
            if (!list.contains(settings.newOffDate)) {
                list << settings.newOffDate
                state.offDates = list
                logAction("Added manual single off-day: ${settings.newOffDate}")
                cleanupOldHolidays()
                evaluateSchoolDay()
            }
            app.updateSetting("newOffDate", [type: "date", value: ""])
        }
    } else if (btn == "addRangeBtn") {
        if (settings.newRangeStart && settings.newRangeEnd) {
            if (settings.newRangeStart <= settings.newRangeEnd) {
                def list = state.offRanges ?: []
                def rangeObj = [start: settings.newRangeStart, end: settings.newRangeEnd, label: "${settings.newRangeStart} to ${settings.newRangeEnd}"]
                if (!list.find { it.label == rangeObj.label }) {
                    list << rangeObj
                    state.offRanges = list
                    logAction("Added manual holiday range: ${rangeObj.label}")
                    cleanupOldHolidays()
                    evaluateSchoolDay()
                }
            } else {
                logAction("Error: Range start must be before end date.")
            }
            app.updateSetting("newRangeStart", [type: "date", value: ""])
            app.updateSetting("newRangeEnd", [type: "date", value: ""])
        }
    } else if (btn == "removeOffDateBtn") {
        if (settings.dateToRemove) {
            state.offDates = (state.offDates ?: []) - settings.dateToRemove
            logAction("Removed configured manual date: ${settings.dateToRemove}")
            app.updateSetting("dateToRemove", [type: "enum", value: ""])
            evaluateSchoolDay()
        }
    } else if (btn == "removeRangeBtn") {
        if (settings.rangeToRemove) {
            state.offRanges = (state.offRanges ?: []).findAll { it.label != settings.rangeToRemove }
            logAction("Removed configured manual date range: ${settings.rangeToRemove}")
            app.updateSetting("rangeToRemove", [type: "enum", value: ""])
            evaluateSchoolDay()
        }
    }
}

// --- STATE CAPTURE ENGINE ---
def captureLightState(dev) {
    if (!state.savedLightState) state.savedLightState = [:]
    
    state.savedLightState = [
        switch: dev.currentValue("switch"),
        hue: dev.currentValue("hue"),
        saturation: dev.currentValue("saturation"),
        level: dev.currentValue("level"),
        colorTemperature: dev.currentValue("colorTemperature")
    ]
    if (debugEnable) log.debug "Captured previous state for ${dev.displayName}: ${state.savedLightState}"
}

def restoreLightState(dev) {
    if (!state.savedLightState) {
        dev.off()
        return
    }
    
    def saved = state.savedLightState
    if (saved.switch == "on") {
        if (saved.colorTemperature) {
            dev.setColorTemperature(saved.colorTemperature, saved.level)
        } else if (saved.hue != null && saved.saturation != null) {
            dev.setColor([hue: saved.hue, saturation: saved.saturation, level: saved.level])
        } else {
            dev.on()
            if (saved.level) dev.setLevel(saved.level)
        }
        if (debugEnable) log.debug "Restored ${dev.displayName} to ON state."
    } else {
        dev.off()
        if (debugEnable) log.debug "Restored ${dev.displayName} to OFF state."
    }
    
    state.savedLightState = [:] 
}

// --- Dynamic Sequence Engine ---

def startLightingSequence(colorName, isRainOverride, nextTargetEpoch, triggerSwitch = null) {
    if (settings.overrideSwitch && settings.overrideSwitch.currentValue("switch") != "on") {
        settings.overrideSwitch.on()
        if (settings.busLight) captureLightState(settings.busLight)
    }

    state.lightActiveByApp = true 

    // Turn on the virtual switch for this stage to trigger House Announcements app
    if (triggerSwitch) {
        logAction("Triggering Virtual Switch: ${triggerSwitch.displayName}")
        triggerSwitch.on()
    }

    if (isRainOverride) {
        logAction("Precipitation detected: Showing Blue for 10 seconds before starting timer.")
        def blueMap = getHueColor("Blue")
        if (settings.busLight) settings.busLight.setColor([hue: blueMap.hue, saturation: blueMap.saturation, level: 80])
        runIn(10, "applyNormalStageColor", [data: [color: colorName, target: nextTargetEpoch], overwrite: true])
    } else {
        applyNormalStageColor([color: colorName, target: nextTargetEpoch])
    }
}

def applyNormalStageColor(data) {
    String colorName = data.color
    long nextTargetEpoch = data.target
    
    def colorMap = getHueColor(colorName)
    state.currentStageColor = colorMap
    
    if (settings.busLight) settings.busLight.setColor([hue: colorMap.hue, saturation: colorMap.saturation, level: 80])
}


// --- Testing Routines ---

def startAmTest() {
    if (!isSystemEnabled()) return
    state.isTesting = true
    state.waitingForDeparture = true 
    state.hasDeparted = false
    state.amDoorTriggered = false
    state.amMotionTriggered = false
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor1 ?: "Green", isItRaining(), nextTarget, settings.amSwitch1)
    runIn(30, "testAmStage2", [overwrite: true])
}

def testAmStage2() {
    if (!state.isTesting) return
    if (state.hasDeparted) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor2 ?: "Yellow", isItRaining(), nextTarget, settings.amSwitch2)
    runIn(30, "testAmStage3", [overwrite: true])
}

def testAmStage3() {
    if (!state.isTesting) return
    if (state.hasDeparted) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor3 ?: "Red", isItRaining(), nextTarget, settings.amSwitch3)
    String msg = "TEST: ${settings.amImminentMsg ?: 'The school bus is imminent! Time to head to the door.'}"
    if (settings.notifyPhones) settings.notifyPhones.deviceNotification(msg)
    runIn(30, "turnLightOff", [overwrite: true])
}

def startPmTest() {
    if (!isSystemEnabled()) return
    state.isTesting = true
    state.waitingForArrival = true
    state.hasArrived = false
    state.pmDoorTriggered = false
    state.pmMotionTriggered = false
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor1 ?: "Green", isItRaining(), nextTarget, settings.pmSwitch1)
    runIn(30, "testPmStage2", [overwrite: true])
}

def testPmStage2() {
    if (!state.isTesting) return
    if (state.hasArrived) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor2 ?: "Yellow", isItRaining(), nextTarget, settings.pmSwitch2)
    runIn(30, "testPmStage3", [overwrite: true])
}

def testPmStage3() {
    if (!state.isTesting) return
    if (state.hasArrived) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor3 ?: "Red", isItRaining(), nextTarget, settings.pmSwitch3)
    String msg = "TEST: ${settings.pmArrivedMsg ?: 'The school bus has arrived at the afternoon stop.'}"
    if (settings.notifyPhones) settings.notifyPhones.deviceNotification(msg)
    runIn(30, "turnLightOff", [overwrite: true])
}

// --- Morning Routines ---

def amSetStage1() {
    state.earlyArrivalDetected = false // Clear flag at start of day

    if (!isSystemEnabled()) { logAction("AM Stage 1 Aborted: Master Switch is OFF"); return }
    if (!isSchoolDay()) { logAction("AM Stage 1 Aborted: Today is not a School Day"); return }
    if (isSickDay()) { logAction("AM Stage 1 Aborted: Sick Day is active"); return }
    if (!isAllowedMode()) { logAction("AM Stage 1 Aborted: Location Mode '${location.mode}' is not allowed"); return }

    logAction("AM Stage 1 Started Successfully.")
    state.waitingForDeparture = true
    state.hasDeparted = false
    state.amDoorTriggered = false
    state.amMotionTriggered = false
    state.amTrackTime = null

    long nextTarget = getEpochTime(settings.amStage2, false)
    startLightingSequence(settings.amColor1 ?: "Green", isItRaining(), nextTarget, settings.amSwitch1)
}

def amSetStage2() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasDeparted) { logAction("AM Stage 2 Skipped: Student already departed."); return }
    
    logAction("AM Stage 2 Started.")
    long nextTarget = getEpochTime(settings.amStage3, false)
    startLightingSequence(settings.amColor2 ?: "Yellow", isItRaining(), nextTarget, settings.amSwitch2)
}

def amSetStage3() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasDeparted) { logAction("AM Stage 3 Skipped: Student already departed."); return }
    
    logAction("AM Stage 3 Started. Awaiting conditions.")
    state.amTrackTime = new Date().time
    
    if (settings.doorSensor || settings.motionSensor) {
        int delaySeconds = (settings.departureTimeout ?: 5) * 60
        runIn(delaySeconds, "checkMissedBus")
    }

    long nextTarget = getEpochTime(settings.amClear, false)
    startLightingSequence(settings.amColor3 ?: "Red", isItRaining(), nextTarget, settings.amSwitch3)

    String msg = settings.amImminentMsg ?: "The school bus is imminent! Time to head to the door."
    if (settings.notifyPhones) settings.notifyPhones.deviceNotification(msg)
}

// --- Afternoon Routines ---

def pmSetStage1() {
    if (!isSystemEnabled()) { logAction("PM Stage 1 Aborted: Master Switch is OFF"); return }
    if (!isSchoolDay()) { logAction("PM Stage 1 Aborted: Not a School Day"); return }
    if (isSickDay()) { logAction("PM Stage 1 Aborted: Sick Day is active"); return }
    if (!isAllowedMode()) { logAction("PM Stage 1 Aborted: Location Mode '${location.mode}' is not allowed"); return }
    if (state.earlyArrivalDetected) { logAction("PM Stage 1 Aborted: Early arrival detected via Smart Lock."); return }

    logAction("PM Stage 1 Started Successfully.")
    
    state.waitingForArrival = true
    state.hasArrived = false
    state.pmDoorTriggered = false
    state.pmMotionTriggered = false
    state.pmTrackTime = null

    long nextTarget = getEpochTime(settings.pmStage2, true)
    startLightingSequence(settings.pmColor1 ?: "Green", isItRaining(), nextTarget, settings.pmSwitch1)
}

def pmSetStage2() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasArrived || state.earlyArrivalDetected) { logAction("PM Stage 2 Skipped: Student already arrived."); return }
    
    logAction("PM Stage 2 Started.")
    long nextTarget = getEpochTime(settings.pmStage3, true)
    startLightingSequence(settings.pmColor2 ?: "Yellow", isItRaining(), nextTarget, settings.pmSwitch2)
}

def pmSetStage3() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasArrived || state.earlyArrivalDetected) { logAction("PM Stage 3 Skipped: Student already arrived."); return }
    
    logAction("PM Stage 3 Started. Awaiting conditions.")
    state.pmTrackTime = new Date().time
    
    if (settings.doorSensor || settings.motionSensor) {
        int delaySeconds = (settings.arrivalTimeout ?: 15) * 60
        runIn(delaySeconds, "checkSafeArrival")
    }

    long nextTarget = getEpochTime(settings.pmClear, true)
    startLightingSequence(settings.pmColor3 ?: "Red", isItRaining(), nextTarget, settings.pmSwitch3)

    String msg = settings.pmArrivedMsg ?: "The school bus has arrived at the afternoon stop."
    if (settings.notifyPhones) settings.notifyPhones.deviceNotification(msg)
}

// --- Door and Security Routines ---

def doorOpenedHandler(evt) {
    if (!isSystemEnabled()) return
    boolean needAmMotion = isMotionUsable()
    boolean needPmMotion = (isMotionUsable() && !settings.ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived && !state.pmDoorTriggered) {
        state.pmDoorTriggered = true
        if (needPmMotion && !state.pmMotionTriggered) {
            logAction("Front door opened. Waiting for outside motion (Afternoon)...")
        } else if (!needPmMotion && settings.motionSensor) {
            logAction("Front door opened. Motion requirement skipped (Lux too high).")
        }
        checkArrivalDepartureConditions()
    }
    
    if (state.waitingForDeparture && !state.hasDeparted && !state.amDoorTriggered) {
        state.amDoorTriggered = true
        if (needAmMotion && !state.amMotionTriggered) {
            logAction("Front door opened. Waiting for outside motion (Morning)...")
        } else if (!needAmMotion && settings.motionSensor) {
            logAction("Front door opened. Motion requirement skipped (Lux too high).")
        }
        checkArrivalDepartureConditions()
    }
}

def motionActiveHandler(evt) {
    if (!isSystemEnabled()) return
    boolean needPmMotion = (isMotionUsable() && !settings.ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived && !state.pmMotionTriggered) {
        state.pmMotionTriggered = true
        if (needPmMotion && !state.pmDoorTriggered) {
            logAction("Outside motion detected. Waiting for front door (Afternoon)...")
        }
        checkArrivalDepartureConditions()
    }

    if (state.waitingForDeparture && !state.hasDeparted && !state.amMotionTriggered) {
        state.amMotionTriggered = true
        if (!state.amDoorTriggered) {
            logAction("Outside motion detected. Waiting for front door (Morning)...")
        }
        checkArrivalDepartureConditions()
    }
}

def luxHandler(evt) {
    if (!isSystemEnabled()) return
    checkArrivalDepartureConditions()
}

def checkArrivalDepartureConditions() {
    boolean needAmMotion = isMotionUsable()
    boolean needPmMotion = (isMotionUsable() && !settings.ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived) {
        boolean pmCondition = needPmMotion ? (state.pmDoorTriggered && state.pmMotionTriggered) : state.pmDoorTriggered
        if (pmCondition) {
            long diffSecs = state.pmTrackTime ? ((new Date().time - state.pmTrackTime) / 1000) : 0
            String durStr = "${diffSecs.intdiv(60)}m ${diffSecs % 60}s"
            logAction("Arrival conditions met! Safe drop-off recorded. (Time to door: ${durStr})")
            
            state.waitingForArrival = false
            state.hasArrived = true
            
            if (settings.enableSmartLearning) {
                recordSmartTime(false) // false for PM
            }
            
            if (state.pmTrackTime && !state.isTesting) {
                state.pmTotalDoorTime = (state.pmTotalDoorTime ?: 0) + diffSecs
                state.pmDoorCount = (state.pmDoorCount ?: 0) + 1
                state.pmTrackTime = null
            }
            
            if (settings.notifyOnSave && settings.notifyPhones) {
                settings.notifyPhones.deviceNotification("Safe drop-off logged! Recorded door time: ${durStr}.")
            }

            if (isAllowedMode() || state.isTesting) {
                if (settings.busLight) settings.busLight.setColor([hue: 39, saturation: 100, level: 100]) 
            }
        
            runIn(10, "turnLightOff")
            unschedule("checkSafeArrival")
            
            scheduleDailyEvents()
        }
    }

    if (state.waitingForDeparture && !state.hasDeparted) {
        boolean amCondition = needAmMotion ? (state.amDoorTriggered && state.amMotionTriggered) : state.amDoorTriggered
        if (amCondition) {
            long diffSecs = state.amTrackTime ? ((new Date().time - state.amTrackTime) / 1000) : 0
            String durStr = "${diffSecs.intdiv(60)}m ${diffSecs % 60}s"
            logAction("Departure conditions met! Safe pickup recorded. (Time to door: ${durStr})")
            
            state.waitingForDeparture = false
            state.hasDeparted = true
            
            if (settings.enableSmartLearning) {
                recordSmartTime(true) // true for AM
            }
            
            if (state.amTrackTime && !state.isTesting) {
                state.amTotalDoorTime = (state.amTotalDoorTime ?: 0) + diffSecs
                state.amDoorCount = (state.amDoorCount ?: 0) + 1
                state.amTrackTime = null
            }

            if (settings.notifyOnSave && settings.notifyPhones) {
                settings.notifyPhones.deviceNotification("Safe pickup logged! Recorded door time: ${durStr}.")
            }

            if (isAllowedMode() || state.isTesting) {
                if (settings.busLight) settings.busLight.setColor([hue: 39, saturation: 100, level: 100]) 
            }
            runIn(5, "turnLightOff")
            unschedule("checkMissedBus")
            
            scheduleDailyEvents()
        }
    }
}

def checkMissedBus() {
    if (!isSystemEnabled()) return
    boolean needAmMotion = isMotionUsable()

    if (state.waitingForDeparture && !state.hasDeparted) {
        logAction("WARNING: Missed bus alert triggered!")
        String defaultMsg = needAmMotion ? "WARNING: The bus arrived, but the door/motion conditions were not met!" : "WARNING: The bus arrived, but the front door never opened!"
        String alertMsg = settings.amMissedMsg ?: defaultMsg
        if (settings.notifyPhones) settings.notifyPhones.deviceNotification(alertMsg)
        state.waitingForDeparture = false
        state.amTrackTime = null
    }
}

def checkSafeArrival() {
    if (!isSystemEnabled()) return
    boolean needPmMotion = (isMotionUsable() && !settings.ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived) {
        logAction("CRITICAL: Missed drop-off alert triggered!")
        String defaultMsg = needPmMotion ? "CRITICAL: The bus dropped off, but the door/motion conditions were not met!" : "CRITICAL: The school bus dropped off, but the front door hasn't opened!"
        String alertMsg = settings.pmMissedMsg ?: defaultMsg
        if (settings.notifyPhones) settings.notifyPhones.deviceNotification(alertMsg)
        state.waitingForArrival = false
        state.pmTrackTime = null
    }
}

def turnLightOff() {
    if (!state.lightActiveByApp && !state.isTesting) {
        return 
    }

    state.lightActiveByApp = false 
    state.isTesting = false
    state.waitingForArrival = false
    state.waitingForDeparture = false
    
    // Reset all trigger switches so they are ready for the next event
    [settings.amSwitch1, settings.amSwitch2, settings.amSwitch3, settings.pmSwitch1, settings.pmSwitch2, settings.pmSwitch3].each { switchDev ->
        if (switchDev && switchDev.currentValue("switch") == "on") {
            switchDev.off()
        }
    }
    
    if (settings.busLight) restoreLightState(settings.busLight)
    if (settings.overrideSwitch) settings.overrideSwitch.off()
    
    unschedule("testAmStage2")
    unschedule("testAmStage3")
    unschedule("testPmStage2")
    unschedule("testPmStage3")
}
