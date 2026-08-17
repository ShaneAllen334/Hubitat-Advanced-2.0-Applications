/**
 * Advanced Lock Manager 2.0
 *
 */
definition(
    name: "Advanced Lock Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def globalStatus = isSystemPaused() ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            def emergencyStatus = ""
            if (smokeDetectors?.find { it.currentValue("smoke") != "clear" } || coDetectors?.find { it.currentValue("carbonMonoxide") != "clear" }) {
                emergencyStatus = "<br><span style='color: red; font-weight: bold;'>🚨 CRITICAL: FIRE / CO EMERGENCY ACTIVE. AUTO-LOCK SUSPENDED. 🚨</span>"
            }
            
            def queueStatus = ""
            if (atomicState.isProcessingQueue && atomicState.lockQueue?.size() > 0) {
                queueStatus = "<br><span style='color: #007bff; font-weight: bold;'>⏳ QUEUE ACTIVE: ${atomicState.lockQueue.size()} lock commands pending execution...</span>"
            }

            def statusExplanation = "${globalStatus}${emergencyStatus}${queueStatus}"
            
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"
            
            input "btnForceSync", "button", title: "🎯 Sync Locks Now"
            input "btnDeleteAll", "button", title: "🗑️ Nuke All Codes (Wipe Locks)"
            input "btnClearLogs", "button", title: "🧹 Clear Audit & History Logs"
            
            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:5px; margin-bottom:15px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; font-weight: normal; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 28%; }
            </style>
            """

            // TABLE 1: Lock Status
            dashHTML += "<b>Physical Lock Status</b><table class='dash-table'><thead><tr><th>Door</th><th>Current State</th><th>Last Action</th></tr></thead><tbody>"
            if (masterLocks) {
                masterLocks.each { lock ->
                    def lName = lock.displayName
                    def lState = lock.currentValue("lock")?.toUpperCase() ?: "UNKNOWN"
                    def stateColor = (lState == "UNLOCKED") ? "red" : (lState == "LOCKED" ? "green" : "black")
                    def lastAction = state.lastAction?."${lock.id}" ?: "Awaiting Sync..."
                    def pendingMsg = ""
                    
                    if (lState == "UNLOCKED") {
                        if (state["pendingAutoLock_${lock.id}"]) {
                            def cSensor = settings["contactSensor_${lock.id}"]
                            if (cSensor && cSensor.currentValue("contact") == "closed") {
                                pendingMsg = "<br><span style='color: orange; font-size: 11px;'><i>Locking in < 10s...</i></span>"
                            } else {
                                pendingMsg = "<br><span style='color: orange; font-size: 11px;'><i>Awaiting Door Close to Auto-Lock</i></span>"
                            }
                        } else {
                            def epoch = state["autoLockEpoch_${lock.id}"]
                            def delayMins = settings["autoLockTime_${lock.id}"] ?: 0
                            if (epoch && delayMins > 0) {
                                def targetTime = epoch + (delayMins * 60 * 1000)
                                def diffMs = targetTime - new Date().time
                                if (diffMs > 0) {
                                    def diffMins = Math.floor(diffMs / 60000).toInteger()
                                    def diffSecs = Math.round((diffMs % 60000) / 1000).toInteger()
                                    pendingMsg = "<br><span style='color: blue; font-size: 11px;'><i>Auto-Locking in ${diffMins}m ${diffSecs}s</i></span>"
                                }
                            }
                        }
                    }
                    dashHTML += "<tr><td class='dash-hl'>${lName}</td><td style='color: ${stateColor}; font-weight: bold;'>${lState}</td><td>${lastAction}${pendingMsg}</td></tr>"
                }
            } else {
                dashHTML += "<tr><td colspan='3' style='color: #888;'>No locks configured.</td></tr>"
            }
            dashHTML += "</tbody></table>"
            
            // TABLE 2: Hardware Health
            dashHTML += "<b>Hardware Health & Maintenance</b><table class='dash-table'><thead><tr><th>Door</th><th>Battery Level</th><th>Weekly Activity</th><th>Cycles Since Lube</th><th>Maintenance Status</th></tr></thead><tbody>"
            if (masterLocks) {
                masterLocks.each { lock ->
                    def lName = lock.displayName
                    def battery = lock.currentValue("battery")
                    def battStr = battery ? "${battery}%" : "N/A"
                    def battColor = (battery && battery < (settings["lowBatteryThreshold"] ?: 20)) ? "red" : "green"
                    
                    def dCycles = state.weeklyDoorCycles?."${lock.id}" ?: 0
                    def wCycles = state.weeklyLockCycles?."${lock.id}" ?: 0
                    def cCycles = state.cumulativeLockCycles?."${lock.id}" ?: 0
                    
                    def mStatus = "<span style='color: green;'>Healthy</span>"
                    def highCycles = settings["highCycleWarning"] ?: 1000
                    
                    if (battery && battery < (settings["lowBatteryThreshold"] ?: 20)) {
                        mStatus = "<span style='color: red; font-weight: bold;'>Replace Battery</span>"
                    } else if (cCycles >= highCycles) {
                        mStatus = "<span style='color: orange; font-weight: bold;'>High Wear (Lube Recommended)</span>"
                    }
                    dashHTML += "<tr><td class='dash-hl'>${lName}</td><td style='color: ${battColor}; font-weight: bold;'>${battStr}</td><td>${dCycles} Opens / ${wCycles} Unlocks</td><td>${cCycles}</td><td>${mStatus}</td></tr>"
                }
            } else {
                dashHTML += "<tr><td colspan='5' style='color: #888;'>No locks configured.</td></tr>"
            }
            dashHTML += "</tbody></table>"
            
            // TABLE 3: Authorization Status
            dashHTML += "<b>Dynamic Authorization Engine</b><table class='dash-table'><thead><tr><th>User Identity</th><th>Access Rights</th><th>Lock Programming Status</th></tr></thead><tbody>"
            def userCount = settings["numUsers"] ?: 1
            if (userCount > 0) {
                for (int i = 1; i <= (userCount as Integer); i++) {
                    def uName = settings["userName_${i}"] ?: "User ${i}"
                    def isAdmin = settings["userIsAdmin_${i}"] ?: false
                    def hasPrimary = settings["userPin_${i}"] ? true : false
                    def hasGhost = settings["userGhostPin_${i}"] ? true : false
                    
                    if (!hasPrimary && !hasGhost) {
                        dashHTML += "<tr><td class='dash-hl'>${uName}</td><td style='color: #888;'>Unconfigured</td><td>-</td></tr>"
                        continue
                    }
                    
                    def allowedLocks = settings["userLocks_${i}"]
                    def lockNames = allowedLocks ? allowedLocks.collect{it.displayName}.join(", ") : "All Locks"
                    def modes = settings["userModes_${i}"] ? settings["userModes_${i}"].join(", ") : "Always"
                    def tStart = settings["userStartTime_${i}"] ? new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", settings["userStartTime_${i}"]).format("h:mm a") : ""
                    def tEnd = settings["userEndTime_${i}"] ? new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", settings["userEndTime_${i}"]).format("h:mm a") : ""
                    def timeStr = (tStart && tEnd) ? "${tStart} - ${tEnd}" : "24/7"
                    def hasPresence = settings["userPresence_${i}"] ? "<br><b>Presence:</b> Linked" : ""
                    
                    if (isAdmin) {
                        modes = "<span style='color:#007bff;'>All (Admin Bypass)</span>"
                        timeStr = "<span style='color:#007bff;'>24/7 (Admin Bypass)</span>"
                    }
                    
                    def rightsStr = "<b>Locks:</b> ${lockNames}<br><b>Modes:</b> ${modes}<br><b>Hours:</b> ${timeStr}${hasPresence}"
                    
                    def progState = state.userProgrammed?."${i}"
                    def progColor = progState ? "green" : "orange"
                    def ghostStr = hasGhost ? (hasPrimary ? " + Ghost" : "Ghost Only") : "Primary"
                    def adminIcon = isAdmin ? "👑 " : ""
                    def progText = progState ? "ACTIVE (${ghostStr})" : "SUSPENDED (Codes Removed)"
                    
                    dashHTML += "<tr><td class='dash-hl'>${adminIcon}${uName}</td><td style='font-size: 11px;'>${rightsStr}</td><td style='color: ${progColor}; font-weight: bold;'>${progText}</td></tr>"
                }
            } else {
                 dashHTML += "<tr><td colspan='3' style='color: #888;'>No users configured.</td></tr>"
            }
            dashHTML += "</tbody></table>"
            
            // TABLE 4: Temporary Codes
            dashHTML += "<b>Active Temporary Codes</b><table class='dash-table'><thead><tr><th>Guest/Name</th><th>Target Lock(s)</th><th>Status</th></tr></thead><tbody>"
            def hasTemp = false
            def tempCountDash = settings["numTempEvents"] ?: 0
            
            for (int i = 1; i <= (tempCountDash as Integer); i++) {
                def eName = settings["eventName_${i}"]
                if (!eName) continue
                hasTemp = true
                def progState = state.tempProgrammed?."${i}"
                def targetLocks = settings["eventLocks_${i}"] ? settings["eventLocks_${i}"].collect{it.displayName}.join(", ") : "All Locks"
                def sCol = progState ? "green" : "orange"
                def sStr = progState ? "ACTIVE" : "PENDING / EXPIRED"
                dashHTML += "<tr><td class='dash-hl'>${eName} <span style='font-size:10px; color:#888;'>(App)</span></td><td>${targetLocks}</td><td style='color: ${sCol}; font-weight: bold;'>${sStr}</td></tr>"
            }
            
            if (!hasTemp) {
                dashHTML += "<tr><td colspan='3' style='color: #888;'>No temporary codes are currently configured.</td></tr>"
            }
            dashHTML += "</tbody></table>"

            paragraph dashHTML
        }

        section("<b>Access Audit Log (Last 10 Entries)</b>", hideable: true, hidden: true) {
            if (atomicState.accessLog && atomicState.accessLog.size() > 0) {
                def logText = "<table style='width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);'><thead><tr><th style='border: 1px solid #ccc; padding: 8px; text-align: center; background-color: #343a40; color: white;'>Date & Time</th><th style='border: 1px solid #ccc; padding: 8px; text-align: center; background-color: #343a40; color: white;'>Event Details</th></tr></thead><tbody>"
                atomicState.accessLog.each { entry ->
                    logText += "<tr><td style='border: 1px solid #ccc; padding: 8px; background-color: #f8f9fa; font-weight:bold; text-align: left; width: 35%;'>${entry.time}</td><td style='border: 1px solid #ccc; padding: 8px; text-align: left;'>${entry.event}</td></tr>"
                }
                logText += "</tbody></table>"
                paragraph logText
            } else {
                paragraph "<i>No access events recorded yet.</i>"
             }
        }
        
        section("<b>Application History</b>", hideable: true, hidden: true) {
            if (atomicState.historyLog && atomicState.historyLog.size() > 0) {
                def logText = atomicState.historyLog.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${logText}</span>"
            }
        }
        
        section("<b>Global Core Settings</b>", hideable: true, hidden: true) {
            input "masterLocks", "capability.lock", title: "Select Smart Locks to Manage", multiple: true, required: true, submitOnChange: true
            
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Pausing this stops the dynamic removal/injection of codes and disables auto-locking.</div>"
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false
            
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Dictates how often the app double-checks and syncs codes in the background. Note: Codes will still sync instantly if a lock is used or the house mode changes.</div>"
            input "syncInterval", "enum", title: "Background Sync Interval", required: true, defaultValue: "15", submitOnChange: true, options: [
                "5": "Every 5 Minutes (High Hub Load)",
                "10": "Every 10 Minutes",
                "15": "Every 15 Minutes (Recommended)",
                "30": "Every 30 Minutes",
                "60": "Every 1 Hour",
                "0": "Manual / Event-Driven Only"
            ]
            
            input "numUsers", "number", title: "Number of Identities/Users to Configure (1-20)", required: true, defaultValue: 1, range: "1..20", submitOnChange: true
        }
        
        if (masterLocks) {
            section("<b>1. Device Health & Maintenance Thresholds</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Smart locks are high-torque devices. Tracking their cycles and battery voltage prevents lockouts. Weekly counters automatically reset every Sunday at midnight for data tracking, while cumulative maintenance counters alert you when it's truly time to lubricate.</div>"
                
                input "lowBatteryThreshold", "number", title: "Critical Battery Threshold (%)", defaultValue: 20, required: true, description: "Alkaline batteries experience severe voltage drops below 20%, which can stall the motor."
                input "highCycleWarning", "number", title: "Maintenance Threshold (Cumulative Cycles)", defaultValue: 1000, required: true, description: "Deadbolts cycling past this number require powdered graphite lubrication (usually ~1000 cycles)."
                input "btnResetCounters", "button", title: "Clear Maintenance Alerts (Reset Counters)"
            }
            
            section("<b>2. Life Safety & Emergency Overrides</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> If smoke or carbon monoxide is detected, all auto-locking routines will be instantly suspended to ensure emergency egress.</div>"
                input "smokeDetectors", "capability.smokeDetector", title: "Smoke Detectors", multiple: true, required: false
                input "coDetectors", "capability.carbonMonoxideDetector", title: "Carbon Monoxide Detectors", multiple: true, required: false
            }

            section("<b>3. Auto-Lock & Door Sensors</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Configure automatic locking based on time and physical door state. If the timer expires while the door is open, the app will wait for it to close, then lock it after a 10-second grace period.</div>"
                masterLocks.each { lock ->
                    input "autoLockTime_${lock.id}", "number", title: "Auto-Lock Timer for ${lock.displayName} (Minutes, 0 to disable)", defaultValue: 0, required: true
                    input "contactSensor_${lock.id}", "capability.contactSensor", title: "Contact Sensor for ${lock.displayName}", required: false, description: "Highly recommended to prevent the deadbolt from throwing while the door is open. Also used to track weekly door cycles."
                }
            }
            
            section("<b>4. Safety Override (Shower Vulnerability Sync)</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> If motion is detected in selected showers, the system assumes you are vulnerable and instantly sweeps the house to lock all closed doors.</div>"
                input "showerSensors", "capability.motionSensor", title: "Shower Motion Sensors", multiple: true, required: false
            }
        }
        
        def userCount = settings["numUsers"] ?: 1
        if (userCount > 0 && userCount <= 20) {
            for (int i = 1; i <= (userCount as Integer); i++) {
                def uName = settings["userName_${i}"] ?: "User ${i}"
                
                section("<b>User: ${uName} Setup</b>", hideable: true, hidden: true) {
                    paragraph "<b>Primary Code</b>"
                    input "userName_${i}", "text", title: "User Name (e.g., Admin, Dog Walker)", required: false, defaultValue: "User ${i}", submitOnChange: true
                    input "userIsAdmin_${i}", "bool", title: "👑 Admin Bypass (Always grant 24/7 access, ignore restrictions)", defaultValue: false
                    input "userPresence_${i}", "capability.presenceSensor", title: "Link Virtual Presence Sensor (Auto-Arrive on Unlock)", required: false, description: "Sets this virtual sensor to 'present' when this specific user code is entered."
                    input "userSlot_${i}", "number", title: "Primary Lock Slot Position (1-30)", required: false, description: "The physical memory slot on the lock. MUST be unique."
                    input "userPin_${i}", "text", title: "Primary PIN Code (4-8 digits)", required: false
                    
                    paragraph "<hr>"
                    paragraph "<b>Ghost Code (Silent Entry)</b>"
                    paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> An optional secondary code. Using this code will unlock the door silently without triggering specific logging events. <i>Leave Primary blank to create a Ghost-Only identity.</i></div>"
                    input "userGhostSlot_${i}", "number", title: "Ghost Lock Slot Position (1-30)", required: false, description: "Must be a different slot than the Primary."
                    input "userGhostPin_${i}", "text", title: "Ghost PIN Code (4-8 digits)", required: false
                    
                    paragraph "<hr>"
                    paragraph "<b>Physical & Access Restrictions (The Dynamic Gate)</b>"
                    paragraph "<div style='font-size:13px; color:#555;'>Leave these blank if the user should have 24/7 access to all configured locks.</div>"
                    input "userLocks_${i}", "capability.lock", title: "Allowed Locks", multiple: true, required: false
                    input "userModes_${i}", "mode", title: "Allowed Modes", multiple: true, required: false
                    input "userStartTime_${i}", "time", title: "Start Time", required: false
                    input "userEndTime_${i}", "time", title: "End Time", required: false
                }
            }
        }

        section("<b>Temporary Codes</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Create temporary codes for guests, contractors, or parties. These codes will automatically inject into the locks at the start date/time and permanently delete themselves at the end date/time.</div>"
            input "numTempEvents", "number", title: "Number of Temporary Codes (0-10)", required: true, defaultValue: 0, range: "0..10", submitOnChange: true
        }
        
        def tempCount = settings["numTempEvents"] ?: 0
        if (tempCount > 0) {
            for (int i = 1; i <= (tempCount as Integer); i++) {
                def eName = settings["eventName_${i}"] ?: "Guest ${i}"
                
                section("<b>Schedule: ${eName} Setup</b>", hideable: true, hidden: true) {
                    paragraph "<b>Code Details</b>"
                    input "eventName_${i}", "text", title: "Guest Name (e.g., House Sitter)", required: false, submitOnChange: true
                    input "eventSlot_${i}", "number", title: "Lock Slot Position (Must be unique)", required: true
                    input "eventPin_${i}", "text", title: "PIN Code (4-8 digits)", required: true
                    input "eventLocks_${i}", "capability.lock", title: "Allowed Locks", multiple: true, required: false, description: "Leave blank to allow access to ALL locks."
                    
                    paragraph "<hr>"
                    paragraph "<b>Validity Window</b>"
                    input "eventStartDate_${i}", "date", title: "Start Date", required: true
                    input "eventStartTime_${i}", "time", title: "Start Time", required: true
                    input "eventEndDate_${i}", "date", title: "End Date", required: true
                    input "eventEndTime_${i}", "time", title: "End Time", required: true
                }
            }
        }
        
        if (masterLocks) {
            section("<b>System Notifications</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Get alerted when a lock requires preventative maintenance or a battery swap.</div>"
                input "notifyDevices", "capability.notification", title: "Send notifications to...", multiple: true, required: false
                input "notifyLowBattery", "bool", title: "Notify on Low Battery", defaultValue: true
                input "notifyHighWear", "bool", title: "Notify on High Wear (Lube Recommended)", defaultValue: true
                input "notifyTime", "time", title: "Daily Maintenance Check Time", required: true, defaultValue: "10:00"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() {
    log.info "Advanced Lock Manager Installed."
    initialize()
}

def updated() {
    log.info "Advanced Lock Manager Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    atomicState.historyLog = atomicState.historyLog ?: []
    atomicState.accessLog = atomicState.accessLog ?: []
    
    state.lastAction = state.lastAction ?: [:]
    state.userProgrammed = state.userProgrammed ?: [:]
    state.tempProgrammed = state.tempProgrammed ?: [:]
    
    // Initialize the true Atomic Execution Queue
    atomicState.lockQueue = []
    atomicState.isProcessingQueue = false
    
    // Setup Weekly Counters
    state.weeklyDoorCycles = state.weeklyDoorCycles ?: [:]
    state.weeklyLockCycles = state.weeklyLockCycles ?: [:]
    state.cumulativeLockCycles = state.cumulativeLockCycles ?: [:]
    
    if (masterLocks) {
        subscribe(masterLocks, "lock", lockHandler)
        
        masterLocks.each { lock ->
            def cSensor = settings["contactSensor_${lock.id}"]
            if (cSensor) {
                subscribe(cSensor, "contact", contactHandler)
            }
            state["pendingAutoLock_${lock.id}"] = false
        }
    }
    
    subscribe(location, "mode", modeChangeHandler)
    
    if (showerSensors) {
        subscribe(showerSensors, "motion.active", showerMotionHandler)
    }

    if (smokeDetectors) {
        subscribe(smokeDetectors, "smoke", emergencyAlarmHandler)
    }
    
    if (coDetectors) {
        subscribe(coDetectors, "carbonMonoxide", emergencyAlarmHandler)
    }
    
    // Scheduled Events
    schedule("0 0 0 ? * SUN", resetWeeklyCounters) // Reset counters Sunday at midnight
    
    if (settings["notifyTime"]) {
        schedule(settings["notifyTime"], dailyMaintenanceCheck)
    } else {
        schedule("0 0 10 ? * *", dailyMaintenanceCheck) // Default to 10:00 AM
    }
    
    // Apply User Selected Sync Interval
    def interval = settings["syncInterval"] ?: "15"
    if (interval != "0") {
        if (interval == "60") {
            schedule("0 0 * * * ?", evaluateSchedules) // Hourly
        } else {
            schedule("0 0/${interval} * * * ?", evaluateSchedules)
        }
    }
    
    // Ensure we force sync all codes whenever the app is updated/saved
    runIn(5, "forceSyncSchedules", [overwrite: true])
}

def forceSyncSchedules() {
    evaluateSchedules(true)
}

// --- UTILITY: LOGGER ---
def addToHistory(String msg) {
    def currentLog = atomicState.historyLog ?: []
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    currentLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    if (currentLog.size() > 20) currentLog = currentLog.take(20)
    atomicState.historyLog = currentLog
    
    log.info "HISTORY: " + msg.replaceAll("\\<.*?\\>", "")
}

def addToAccessLog(String msg) {
    def currentLog = atomicState.accessLog ?: []
    def timestamp = new Date().format("MM/dd hh:mm a", location.timeZone)
    def entry = [time: timestamp, event: msg]
    currentLog.add(0, entry)
    if (currentLog.size() > 10) currentLog = currentLog.take(10)
    atomicState.accessLog = currentLog
}

def isSystemPaused() {
    return (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off")
}

def isEmergencyActive() {
    def emergency = false
    if (smokeDetectors?.find { it.currentValue("smoke") != "clear" }) emergency = true
    if (coDetectors?.find { it.currentValue("carbonMonoxide") != "clear" }) emergency = true
    return emergency
}

// ==============================================================================
// COMMAND QUEUE ENGINE (BULLETPROOF ATOMIC ENGINE)
// ==============================================================================

def queueLockCommand(lockId, command, args) {
    // Pull from the live database immediately
    def currentQueue = atomicState.lockQueue ?: []
    currentQueue.add([id: lockId, cmd: command, args: args])
    
    // Write back to the live database immediately, bypassing memory cache
    atomicState.lockQueue = currentQueue 
    
    if (!atomicState.isProcessingQueue) {
        atomicState.isProcessingQueue = true
        runIn(1, "processQueue", [overwrite: true]) // Start worker immediately
    }
}

def processQueue() {
    def currentQueue = atomicState.lockQueue ?: []
    
    // If empty, turn off processor and exit safely
    if (currentQueue.size() == 0) {
        atomicState.isProcessingQueue = false
        return
    }
    
    // Pop the very first command off the stack
    def nextCmd = currentQueue.remove(0)
    
    // FORCIBLY write the shrunken array back to database immediately
    atomicState.lockQueue = currentQueue 
    
    def lock = masterLocks?.find { it.id == nextCmd.id }
    
    if (lock) {
        if (nextCmd.cmd == "setCode") {
            lock.setCode(nextCmd.args.slot as Integer, nextCmd.args.pin as String, nextCmd.args.name as String)
            log.info "QUEUE: Set Code sent to ${lock.displayName} (Slot: ${nextCmd.args.slot}, Name: ${nextCmd.args.name})"
        } else if (nextCmd.cmd == "deleteCode") {
            lock.deleteCode(nextCmd.args.slot as Integer)
            log.info "QUEUE: Delete Code sent to ${lock.displayName} (Slot: ${nextCmd.args.slot})"
        }
    }
    
    // 8 Seconds ensures that even the slowest Zigbee locks have time to fully accept the Name payload
    if (currentQueue.size() > 0) {
        runIn(8, "processQueue", [overwrite: true])
    } else {
        atomicState.isProcessingQueue = false
    }
}

// --- NUKE COMMAND ---
def nukeAllCodes() {
    if (!masterLocks) return
    
    // Purge any existing jobs in the database queue
    atomicState.lockQueue = []
    atomicState.isProcessingQueue = false
    
    masterLocks.each { lock ->
        for (int i = 1; i <= 30; i++) {
            queueLockCommand(lock.id, "deleteCode", [slot: i])
        }
    }
    
    // Clear local tracking so app knows it needs to resend
    state.userProgrammed = [:]
    state.tempProgrammed = [:]
}

// --- EMERGENCY HANDLER ---
def emergencyAlarmHandler(evt) {
    if (evt.value != "clear") {
        addToHistory("EMERGENCY: ${evt.device.displayName} detected ${evt.name}! Suspending all auto-lock operations.")
        if (masterLocks) {
            masterLocks.each { lock ->
                state["pendingAutoLock_${lock.id}"] = false
                state["autoLockEpoch_${lock.id}"] = new Date().time 
            }
        }
    } else {
        addToHistory("SAFETY: ${evt.device.displayName} is now clear.")
    }
}

// --- MAINTENANCE & RESET ---
def resetWeeklyCounters() {
    addToHistory("MAINTENANCE: Weekly cycle stats have been reset to zero. (Cumulative cycles untouched).")
    state.weeklyDoorCycles = [:]
    state.weeklyLockCycles = [:]
}

def dailyMaintenanceCheck() {
    if (!masterLocks) return
    
    def notifyList = []
    
    masterLocks.each { lock ->
        def battery = lock.currentValue("battery")
        def lowBattThresh = settings["lowBatteryThreshold"] ?: 20
        if (battery && battery < lowBattThresh && settings["notifyLowBattery"]) {
            notifyList << "${lock.displayName} battery is critically low (${battery}%)."
        }
        
        def cCycles = state.cumulativeLockCycles?."${lock.id}" ?: 0
        def highCycles = settings["highCycleWarning"] ?: 1000
        if (cCycles >= highCycles && settings["notifyHighWear"]) {
            notifyList << "${lock.displayName} has high wear (${cCycles} cycles). Lube recommended."
        }
    }
    
    if (notifyList.size() > 0 && settings["notifyDevices"]) {
        def msg = "Lock Manager Maintenance Alert:\n" + notifyList.join("\n")
        settings["notifyDevices"].each { it.deviceNotification(msg) }
        addToHistory("NOTIFICATIONS: Sent daily maintenance alerts.")
    }
}

// --- BUTTON HANDLER ---
def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        addToHistory("SYSTEM: Dashboard data manually refreshed.")
    } else if (btn == "btnForceSync") {
        addToHistory("SYSTEM: Manual Force Sync triggered.")
        evaluateSchedules(true)
    } else if (btn == "btnResetCounters") {
        addToHistory("SYSTEM: Manual maintenance reset triggered. Clearing cumulative wear alerts.")
        resetWeeklyCounters()
        state.cumulativeLockCycles = [:]
    } else if (btn == "btnDeleteAll") {
        addToHistory("SYSTEM: Master Wipe triggered. Purging slots 1-30 from all locks. This will take several minutes to complete across the mesh network.")
        nukeAllCodes()
    } else if (btn == "btnClearLogs") {
        atomicState.accessLog = []
        atomicState.historyLog = []
        log.info "Advanced Lock Manager: Audit and History Logs have been permanently wiped."
    }
}

// --- SAFETY OVERRIDE HANDLER (Motion Based) ---
def showerMotionHandler(evt) {
    if (isSystemPaused() || !masterLocks) return
    
    if (isEmergencyActive()) {
        addToHistory("SAFETY OVERRIDE ABORTED: Life Safety Emergency is active.")
        return
    }

    def sensorName = evt.device.displayName
    addToHistory("SAFETY OVERRIDE: Motion detected at ${sensorName}. Securing perimeter.")
    
    masterLocks.each { lock ->
        if (lock.currentValue("lock") == "unlocked") {
            def cSensor = settings["contactSensor_${lock.id}"]
            def isClosed = cSensor ? (cSensor.currentValue("contact") == "closed") : true
            
            if (isClosed) {
                addToHistory("SAFETY: ${lock.displayName} was unlocked and closed. Locking immediately.")
                lock.lock()
                state["pendingAutoLock_${lock.id}"] = false
            } else {
                addToHistory("SAFETY ALERT: Cannot secure ${lock.displayName} because it is OPEN. Queuing auto-lock.")
                state["pendingAutoLock_${lock.id}"] = true
            }
        }
    }
}

// --- DOOR SENSOR HANDLER (The Frame-Smash Protector & Cycle Tracker) ---
def contactHandler(evt) {
    if (isSystemPaused()) return
    
    def sensorId = evt.device.id
    def isClosed = (evt.value == "closed")
    
    masterLocks.each { lock ->
        def cSensor = settings["contactSensor_${lock.id}"]
        if (cSensor && cSensor.id == sensorId) {
            
            if (isClosed) {
                if (state["pendingAutoLock_${lock.id}"]) {
                    if (isEmergencyActive()) {
                        addToHistory("SECURITY: Auto-Lock queue aborted for ${lock.displayName} due to Life Safety Emergency.")
                        state["pendingAutoLock_${lock.id}"] = false
                        return
                    }
                    addToHistory("SECURITY: ${lock.displayName} closed. Executing Auto-Lock in 10 seconds.")
                    def epoch = new Date().time
                    state["autoLockEpoch_${lock.id}"] = epoch
                    runIn(10, "executeFinalAutoLock", [data: [lockId: lock.id, epoch: epoch], overwrite: false])
                }
            } else {
                // Tracking Door Open Cycles
                if (!state.weeklyDoorCycles) state.weeklyDoorCycles = [:]
                state.weeklyDoorCycles["${lock.id}"] = (state.weeklyDoorCycles["${lock.id}"] ?: 0) + 1
                
                // If door opens during the 10-second grace period, cancel the lock command
                if (state["pendingAutoLock_${lock.id}"]) {
                    state["autoLockEpoch_${lock.id}"] = new Date().time // Invalidate old timers
                    addToHistory("SECURITY: ${lock.displayName} reopened during 10s grace period. Auto-Lock suspended.")
                }
            }
        }
    }
}

def executeFinalAutoLock(data) {
    def lockId = data.lockId
    if (state["autoLockEpoch_${lockId}"] != data.epoch || isSystemPaused()) return
    
    def lock = masterLocks.find { it.id == lockId }
    
    if (isEmergencyActive()) {
        addToHistory("SECURITY: Auto-Lock aborted for ${lock?.displayName} due to active Life Safety Emergency!")
        state["pendingAutoLock_${lockId}"] = false
        return
    }

    if (lock && lock.currentValue("lock") == "unlocked") {
        addToHistory("SECURITY: Executing delayed Auto-Lock for ${lock.displayName}.")
        lock.lock()
        state["pendingAutoLock_${lockId}"] = false
    }
}

// --- LOCK EVENT HANDLER (The Auditor & Trigger) ---
def lockHandler(evt) {
    def lockId = evt.device.id
    def lockName = evt.device.displayName
    def action = evt.value 
    def desc = evt.descriptionText ?: ""
    
    def logMsg = ""
    def codeName = ""
    
    if (action == "unlocked") {
        // Track Lock Cycles
        if (!state.weeklyLockCycles) state.weeklyLockCycles = [:]
        state.weeklyLockCycles["${lockId}"] = (state.weeklyLockCycles["${lockId}"] ?: 0) + 1
        
        if (!state.cumulativeLockCycles) state.cumulativeLockCycles = [:]
        state.cumulativeLockCycles["${lockId}"] = (state.cumulativeLockCycles["${lockId}"] ?: 0) + 1
        
        // Parse ID from payload
        if (evt.data) {
            try {
                def dataMap = parseJson(evt.data)
                if (dataMap?.codeName) codeName = dataMap.codeName
            } catch (e) { }
        } 
        // Fallback string parsing
        if (!codeName && desc.contains("unlocked by")) {
            codeName = desc.split("unlocked by ")[1]?.trim()
        }
        
        if (codeName) {
            // Auto-Ghosting feature: Catch explicitly named Ghosts, or anyone named "Admin" / "Master"
            def isGhost = codeName.endsWith("(Ghost)") || codeName.toLowerCase().contains("admin") || codeName.toLowerCase().contains("master")
            logMsg = "Unlocked by <b>${codeName}</b>"
            
            if (isGhost) {
                addToAccessLog("<span style='color:#007bff;font-weight:bold;'>[SILENT]</span> ${lockName} unlocked by <b>${codeName}</b>")
                addToHistory("ACCESS: ${lockName} unlocked by ${codeName}. Bypassing automations.")
            } else {
                addToAccessLog("<span style='color:red;font-weight:bold;'>[UNLOCKED]</span> ${lockName} unlocked by <b>${codeName}</b>")
                addToHistory("ACCESS: ${lockName} unlocked by ${codeName}.")

                // --- PRESENCE SENSOR LINKING ---
                def uCount = settings["numUsers"] ?: 1
                for (int i = 1; i <= (uCount as Integer); i++) {
                    def uName = settings["userName_${i}"]
                    if (uName && codeName.trim() == uName.trim()) {
                        def pSensor = settings["userPresence_${i}"]
                        if (pSensor) {
                            if (pSensor.currentValue("presence") != "present") {
                                addToHistory("PRESENCE: ${uName} entered PIN. Updating presence sensor to Arrived.")
                                if (pSensor.hasCommand("arrived")) {
                                    pSensor.arrived()
                                } else if (pSensor.hasCommand("present")) {
                                    pSensor.present()
                                } else if (pSensor.hasCommand("setPresence")) {
                                    pSensor.setPresence("present")
                                } else {
                                    log.warn "Advanced Lock Manager: Linked presence sensor ${pSensor.displayName} does not support arrived/present commands. It must be a virtual presence device."
                                }
                            }
                        }
                        break // We found the user, no need to keep looping
                    }
                }
            }
        } else {
            logMsg = "Unlocked (Manual/Thumbturn)"
            addToAccessLog("<span style='color:orange;font-weight:bold;'>[UNLOCKED]</span> ${lockName} unlocked manually.")
            addToHistory("ACCESS: ${lockName} unlocked manually.")
        }
        
        // --- AUTO-LOCK TRIGGER ---
        def delayMins = settings["autoLockTime_${lockId}"] ?: 0
        if (delayMins > 0) {
            def epoch = new Date().time
            state["autoLockEpoch_${lockId}"] = epoch
            addToHistory("SECURITY: Auto-Lock timer started for ${lockName} (${delayMins} min).")
            runIn(delayMins * 60, "evaluateAutoLock", [data: [lockId: lockId, epoch: epoch], overwrite: false])
        }
        
    } else if (action == "locked") {
        logMsg = "Locked"
        addToAccessLog("<span style='color:green;font-weight:bold;'>[LOCKED]</span> ${lockName} was locked.")
        
        // Destroy any running auto-lock timers for this specific door
        state["autoLockEpoch_${lockId}"] = new Date().time 
        state["pendingAutoLock_${lockId}"] = false
    }
    
    state.lastAction["${lockId}"] = logMsg
}

def evaluateAutoLock(data) {
    def lockId = data.lockId
    if (state["autoLockEpoch_${lockId}"] != data.epoch || isSystemPaused()) return
    
    def lock = masterLocks.find { it.id == lockId }
    if (!lock || lock.currentValue("lock") != "unlocked") return

    if (isEmergencyActive()) {
        addToHistory("SECURITY: Auto-Lock aborted for ${lock.displayName} due to active Life Safety Emergency!")
        state["pendingAutoLock_${lockId}"] = false
        return
    }
    
    def cSensor = settings["contactSensor_${lockId}"]
    // If no sensor is configured, we assume the door is closed and blindly throw the deadbolt
    def isClosed = cSensor ? (cSensor.currentValue("contact") == "closed") : true 
    
    if (isClosed) {
        addToHistory("SECURITY: Auto-Lock timer expired. Door is closed. Locking ${lock.displayName}.")
        lock.lock()
        state["pendingAutoLock_${lockId}"] = false
    } else {
        addToHistory("SECURITY: Auto-Lock timer expired, but ${lock.displayName} is OPEN. Suspending lock command.")
        state["pendingAutoLock_${lockId}"] = true
    }
}

// --- DYNAMIC CODE INJECTION ENGINE ---
def modeChangeHandler(evt) {
    def currentMode = evt.value
    addToHistory("SYSTEM: Hub mode changed to ${currentMode}. Re-evaluating access schedules.")
    evaluateSchedules()
}

def evaluateSchedules(forceSync = false) {
    if (isSystemPaused() || !masterLocks) return
    
    // ==========================================
    // 1. EVALUATE STANDARD USER IDENTITIES
    // ==========================================
    def userCount = settings["numUsers"] ?: 1
    for (int i = 1; i <= (userCount as Integer); i++) {
        def uName = settings["userName_${i}"]
        def isAdmin = settings["userIsAdmin_${i}"] ?: false
        def pSlot = settings["userSlot_${i}"]
        def pPin = settings["userPin_${i}"]
        def gSlot = settings["userGhostSlot_${i}"]
        def gPin = settings["userGhostPin_${i}"]
        
        if (!uName) continue
        
        def isAllowed = true
        
        // Check Mode & Time Restrictions (Admin Bypasses This Entirely)
        if (!isAdmin) {
            def allowedModes = settings["userModes_${i}"]
            if (allowedModes && !allowedModes.contains(location.mode)) {
                isAllowed = false
            }
            
            def tStart = settings["userStartTime_${i}"]
            def tEnd = settings["userEndTime_${i}"]
            if (isAllowed && tStart && tEnd) {
                def between = timeOfDayIsBetween(tStart, tEnd, new Date(), location.timeZone)
                if (!between) {
                    isAllowed = false
                }
            }
        }
        
        def currentlyProgrammed = state.userProgrammed["${i}"] ?: false
        
        if (isAllowed && (!currentlyProgrammed || forceSync)) {
            if (!currentlyProgrammed) addToHistory("SECURITY: Access granted for ${uName}. Synchronizing Locks.")
            masterLocks.each { lock ->
                def allowedLocks = settings["userLocks_${i}"]
                def allowedIds = allowedLocks?.collect { it.id }
                def lockPermitted = (!allowedIds || allowedIds.contains(lock.id))
                
                if (lockPermitted) {
                    if (pSlot && pPin && lock.hasCommand("setCode")) {
                        queueLockCommand(lock.id, "setCode", [slot: pSlot, pin: pPin, name: uName])
                    }
                    if (gSlot && gPin && lock.hasCommand("setCode")) {
                        queueLockCommand(lock.id, "setCode", [slot: gSlot, pin: gPin, name: "${uName} (Ghost)"])
                    }
                } else {
                    if (pSlot && lock.hasCommand("deleteCode")) {
                        queueLockCommand(lock.id, "deleteCode", [slot: pSlot])
                    }
                    if (gSlot && lock.hasCommand("deleteCode")) {
                        queueLockCommand(lock.id, "deleteCode", [slot: gSlot])
                    }
                }
            }
            state.userProgrammed["${i}"] = true
        } 
        else if (!isAllowed && (currentlyProgrammed || forceSync)) {
            if (currentlyProgrammed) addToHistory("SECURITY: Access revoked for ${uName}. Deleting PIN(s).")
            masterLocks.each { lock ->
                if (pSlot && lock.hasCommand("deleteCode")) {
                    queueLockCommand(lock.id, "deleteCode", [slot: pSlot])
                }
                if (gSlot && lock.hasCommand("deleteCode")) {
                    queueLockCommand(lock.id, "deleteCode", [slot: gSlot])
                }
            }
            state.userProgrammed["${i}"] = false
        }
    }

    def nowMs = new Date().time

    // ==========================================
    // 2. EVALUATE MANUAL APP TEMP EVENTS
    // ==========================================
    def tempCount = settings["numTempEvents"] ?: 0
    for (int i = 1; i <= (tempCount as Integer); i++) {
        def eName = settings["eventName_${i}"]
        def eSlot = settings["eventSlot_${i}"]
        def ePin = settings["eventPin_${i}"]
        
        def startDateStr = settings["eventStartDate_${i}"]
        def startTimeIso = settings["eventStartTime_${i}"]
        def endDateStr = settings["eventEndDate_${i}"]
        def endTimeIso = settings["eventEndTime_${i}"]
        
        if (!eName || !eSlot || !ePin || !startDateStr || !startTimeIso || !endDateStr || !endTimeIso) continue
        
        def isAllowed = false
        try {
            def tFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            def timeOnlyFmt = new java.text.SimpleDateFormat("HH:mm")
            timeOnlyFmt.setTimeZone(location.timeZone)
            
            def sTimePart = timeOnlyFmt.format(tFormat.parse(startTimeIso))
            def eTimePart = timeOnlyFmt.format(tFormat.parse(endTimeIso))
            
            def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
            sdf.setTimeZone(location.timeZone)
            
            def startMs = sdf.parse("${startDateStr} ${sTimePart}").time
            def endMs = sdf.parse("${endDateStr} ${eTimePart}").time
            
            if (nowMs >= startMs && nowMs <= endMs) {
                isAllowed = true
            }
        } catch (Exception e) {
            log.warn "Advanced Lock Manager: Invalid date/time format for Code ${i}. Exception: ${e}"
            continue
        }
        
        def currentlyProgrammed = state.tempProgrammed["${i}"] ?: false
        
        if (isAllowed && (!currentlyProgrammed || forceSync)) {
            if (!currentlyProgrammed) addToHistory("TEMPORARY ACCESS: Window active for '${eName}'. Injecting code into locks.")
            masterLocks.each { lock ->
                def allowedLocks = settings["eventLocks_${i}"]
                def allowedIds = allowedLocks?.collect { it.id }
                if (!allowedIds || allowedIds.contains(lock.id)) {
                    if (lock.hasCommand("setCode")) {
                        queueLockCommand(lock.id, "setCode", [slot: eSlot, pin: ePin, name: eName])
                    }
                }
            }
            state.tempProgrammed["${i}"] = true
            
        } else if (!isAllowed && (currentlyProgrammed || forceSync)) {
            if (currentlyProgrammed) addToHistory("TEMPORARY ACCESS: Window expired for '${eName}'. Deleting temporary code.")
            masterLocks.each { lock ->
                if (lock.hasCommand("deleteCode")) {
                    queueLockCommand(lock.id, "deleteCode", [slot: eSlot])
                }
            }
            state.tempProgrammed["${i}"] = false
        }
    }
}
