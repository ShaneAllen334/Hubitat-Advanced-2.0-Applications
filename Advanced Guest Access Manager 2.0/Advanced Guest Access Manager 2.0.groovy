/**
 * Advanced Guest Access Manager 2.0
 */
definition(
    name: "Advanced Guest Access Manager 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        // Fetch existing codes map for UI rendering (Dropdowns only, hidden from dashboard)
        def existingCodesMap = [:]
        def lockCodesParsed = null
        if (mainLocks) {
            def firstLock = mainLocks[0]
            def lockCodesStr = firstLock.currentValue("lockCodes")
            if (lockCodesStr) {
                try {
                    lockCodesParsed = parseJson(lockCodesStr)
                    lockCodesParsed.each { slot, data ->
                        def name = data.name ?: "Unknown"
                        existingCodesMap[slot.toString()] = "Slot ${slot}: ${name}"
                    }
                } catch (e) {
                    log.warn "Could not parse lockCodes: ${e}"
                }
            }
        }

        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"

            if (mainLocks) {
                def currentLocMode = location.mode ?: "Unknown"
                def guestSwitchStatus = guestSwitch ? guestSwitch.currentValue("switch")?.toUpperCase() : "NOT CONFIGURED"
                def enableSwitchStatus = enableSwitch ? enableSwitch.currentValue("switch")?.toUpperCase() : "NOT CONFIGURED"
                
                def guestColor = guestSwitchStatus == "ON" ? "green" : "black"
                def enableColor = enableSwitchStatus == "ON" ? "green" : "red"
                
                def allAway = true
                def presenceStr = "Not Configured"
                if (presenceSensors) {
                    def presentCount = presenceSensors.count { it.currentValue("presence") == "present" }
                    def totalCount = presenceSensors.size()
                    allAway = presentCount == 0
                    presenceStr = allAway ? "<span style='color:blue;'>All Away (${totalCount} Tracked)</span>" : "<span style='color:green;'>Occupied (${presentCount}/${totalCount} Home)</span>"
                }

                // Unified Dashboard HTML
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
                    <thead><tr><th>Metric</th><th colspan="3">Current Value</th></tr></thead>
                    <tbody>
                        <tr><td class="dash-hl">Location Mode</td><td colspan="3" class="dash-val"><b>${currentLocMode}</b></td></tr>
                        <tr><td class="dash-hl">Presence Sensors</td><td colspan="3" class="dash-val">${presenceStr}</td></tr>
                        <tr><td class="dash-hl">Guest Virtual Switch</td><td colspan="3" class="dash-val"><b style='color:${guestColor};'>${guestSwitchStatus}</b></td></tr>
                        <tr><td class="dash-hl">App Enable Switch</td><td colspan="3" class="dash-val"><b style='color:${enableColor};'>${enableSwitchStatus}</b></td></tr>
                        <tr><td colspan="4" class="dash-subhead">Lock Diagnostics</td></tr>
                """
                
                mainLocks.each { lock ->
                    def lStatus = lock.currentValue("lock")?.toUpperCase() ?: "UNKNOWN"
                    def lColor = lStatus == "LOCKED" ? "green" : "red"
                    def batt = lock.currentValue("battery") != null ? "${lock.currentValue("battery")}%" : "N/A"
                    dashHTML += "<tr><td class='dash-hl'>${lock.displayName}</td><td colspan='2' style='color:${lColor}; font-weight:bold;'>${lStatus}</td><td>Battery: ${batt}</td></tr>"
                }

                // --- NEW APP-MANAGED CODES SECTION ---
                dashHTML += "<tr><td colspan='4' class='dash-subhead'>App-Managed Guest Codes</td></tr>"
                def hasManaged = false
                
                for (int i = 1; i <= 5; i++) {
                    if (settings["enableG${i}"]) {
                        def slot = settings["guestSlot${i}"]
                        def name = settings["guestName${i}"] ?: "Guest ${i}"
                        
                        if (slot != null) {
                            hasManaged = true
                            def isActive = false
                            
                            // Check if this slot is physically on the lock
                            if (lockCodesParsed && lockCodesParsed[slot.toString()]) {
                                isActive = true
                            }
                            
                            def statusText = isActive ? "<span style='color:green; font-weight:bold;'>Active</span>" : "<span style='color:gray;'>Not Sent</span>"
                            def expiryText = "Permanent"
                            
                            // Calculate Live Expiry Timer
                            if (state.guestExpiries && state.guestExpiries[i.toString()]) {
                                def expTime = state.guestExpiries[i.toString()]
                                if (now() < expTime) {
                                    def timeLeft = expTime - now()
                                    def hrs = Math.floor(timeLeft / 3600000).toInteger()
                                    def mins = Math.floor((timeLeft % 3600000) / 60000).toInteger()
                                    expiryText = "<span style='color:orange;'>Expires in <b>${hrs}h ${mins}m</b></span>"
                                } else {
                                    expiryText = "<span style='color:red;'>Expired (Pending Removal)</span>"
                                }
                            }
                            
                            dashHTML += "<tr><td class='dash-hl'>${name} (Slot ${slot})</td><td colspan='2'>${statusText}</td><td>${expiryText}</td></tr>"
                        }
                    }
                }
                
                if (!hasManaged) {
                    dashHTML += "<tr><td colspan='4'><i>No guest codes managed by this app yet. Configure below.</i></td></tr>"
                }
                
                dashHTML += """
                    </tbody>
                </table>
                """
                paragraph dashHTML
            } else {
                paragraph "<i>Please select your main locks below to populate the dashboard.</i>"
            }
        } // End of unified dashboard section

        section("<b>Recent Action History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<div style='background-color:#f9f9f9; padding:10px; border:1px solid #ddd; border-radius:5px;'><span style='font-size: 13px; font-family: monospace;'>${historyStr}</span></div>"
            } else {
                paragraph "<i>No recent actions logged.</i>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        section("<b>1. Hardware Integration</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Connects the app to your physical locks and virtual switches.</div>"
            input "mainLocks", "capability.lock", title: "Select Main Door Locks", required: true, multiple: true
            input "guestSwitch", "capability.switch", title: "Select Guest Mode Virtual Switch", required: false, multiple: false
            input "enableSwitch", "capability.switch", title: "Select Master Enable Switch (App operates ONLY when ON)", required: false, multiple: false
        }

        section("<b>2. Guest Code Management</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Maps existing lock codes to guest logic, or allows you to push new PIN codes directly to your physical locks with optional Auto-Expiration.</div>"
            
            for (int i = 1; i <= 5; i++) {
                def currentGuestName = settings["guestName${i}"] ?: "Guest Slot ${i}"
                input "enableG${i}", "bool", title: "<b>Enable ${currentGuestName}</b>", submitOnChange: true
                
                if (settings["enableG${i}"]) {
                    input "guestName${i}", "text", title: "Guest Name", required: false, defaultValue: "Guest ${i}", submitOnChange: true
                    
                    input "useExisting${i}", "bool", title: "Map to an existing lock code?", defaultValue: false, submitOnChange: true
                    
                    if (settings["useExisting${i}"]) {
                        input "guestSlot${i}", "enum", title: "Select Existing Code to Map", options: existingCodesMap, required: true, submitOnChange: true
                        paragraph "<div style='font-size:12px; color:#555;'><i>This guest is mapped to an existing code. Unlocking with this code will trigger the arrival sequence.</i></div>"
                    } else {
                        input "guestSlot${i}", "number", title: "Lock Slot Number", required: false
                        input "guestPin${i}", "text", title: "PIN Code", required: false
                        input "guestExpiry${i}", "number", title: "Auto-Expire Code in (Hours) - Leave blank to keep permanently", required: false
                        
                        input "btnSet${i}", "button", title: "Push Code to Locks"
                        input "btnDel${i}", "button", title: "Delete Code from Locks"
                    }
                    paragraph "<hr>"
                }
            }
        }

        section("<b>3. Arrival Automation Logic</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Defines the exact conditions required for the app to engage Guest Mode upon a door unlock.</div>"
            
            input "presenceSensors", "capability.presenceSensor", title: "Select Presence Sensors (Everyone must be away to trigger arrival)", required: false, multiple: true
            input "awayModes", "mode", title: "Select 'Away' Modes (App only triggers if the house is currently in one of these modes)", multiple: true, required: false
            input "arrivalMode", "mode", title: "Mode to change to upon Guest Arrival", multiple: false, required: false
            
            paragraph "<b>Auto-Revert (Ending Guest Mode)</b>"
            paragraph "<div style='font-size:12px; color:#555;'><i>Guest Mode will automatically turn OFF if a homeowner presence sensor arrives, or if a generated guest code expires. You can also specify modes below to force a reset.</i></div>"
            input "revertModes", "mode", title: "Modes that cancel Guest Mode (e.g., 'Away', 'Night')", multiple: true, required: false
        }
        
        section("<b>4. Alerts & Notifications</b>", hideable: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Sends push notifications when guest codes are used or when Guest Mode automatically reverts.</div>"
            input "notifyDevices", "capability.notification", title: "Select Notification Devices", required: false, multiple: true
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
    if (!state.guestExpiries) state.guestExpiries = [:]
    state.lastModeChangeTime = now()
    
    if (mainLocks) {
        subscribe(mainLocks, "lock", lockHandler)
    }
    
    if (presenceSensors) {
        subscribe(presenceSensors, "presence", presenceHandler)
    }
    
    subscribe(location, "mode", modeChangeHandler)
    
    logAction("App Initialized. Guest Access Engine Ready.")
}

def isDisabled() {
    // App ONLY operates if enableSwitch is present and ON
    if (!enableSwitch) return false 
    return enableSwitch.currentValue("switch") == "off"
}

String getHumanReadableStatus() {
    if (isDisabled()) return "<span style='color:red;'><b>App Disabled:</b> The Master Enable Switch is currently OFF.</span>"
    if (!mainLocks) return "Awaiting hardware configuration."
    if (!presenceSensors) return "<span style='color:orange;'><b>Warning:</b> No presence sensors selected. The automation logic will not function properly without tracking occupancy.</span>"
    if (!awayModes) return "<span style='color:orange;'><b>Warning:</b> No 'Away' modes selected. The system doesn't know when to allow the guest trigger.</span>"
    
    if (guestSwitch && guestSwitch.currentValue("switch") == "on") {
        return "<span style='color:green;'><b>Guest Mode Active:</b></span> The guest virtual switch is currently ON."
    }
    
    def presentCount = presenceSensors.count { it.currentValue("presence") == "present" }
    def isAwayMode = awayModes ? (awayModes as List).contains(location.mode) : false

    if (presentCount > 0) return "Automation paused. The house is currently occupied."
    if (!isAwayMode) return "Automation paused. The house is empty, but the Location Mode is not set to an authorized Away mode."
    
    return "<span style='color:blue;'><b>System Armed:</b></span> House is empty and in an Away mode. Awaiting guest code entry to trigger arrival sequence."
}

def lockHandler(evt) {
    if (isDisabled()) {
        logInfo("App is disabled by master switch. Ignoring lock event.")
        return
    }

    if (evt.value == "unlocked") {
        def currentMode = location.mode
        
        // --- 10 MINUTE FLIP-FLOP PREVENTION ---
        if (currentMode?.toLowerCase()?.contains("away") && state.lastModeChangeTime) {
            def elapsed = now() - state.lastModeChangeTime
            if (elapsed < 600000) { // 600,000 ms = 10 minutes
                def remainingMins = Math.max(1, Math.round((600000 - elapsed) / 60000))
                logAction("🚫 Guest arrival skipped: Mode recently changed to '${currentMode}'. Preventing flip-flop for another ${remainingMins} minute(s).")
                return
            }
        }

        def isGuest = false
        def matchedName = "Unknown Guest"
        
        // Parse the code data if provided by the lock driver
        if (evt.data) {
            try {
                def dataMap = parseJson(evt.data)
                if (dataMap && dataMap.codeId) {
                    def cid = dataMap.codeId.toString()
                    
                    // Match the reported code ID to our configured slots
                    for (int i = 1; i <= 5; i++) {
                        if (settings["enableG${i}"] && settings["guestSlot${i}"]?.toString() == cid) {
                            isGuest = true
                            matchedName = settings["guestName${i}"] ?: "Guest ${i}"
                            break
                        }
                    }
                }
            } catch (e) {
                logInfo("Notice: Lock payload not in standard JSON format. Defaulting to description text.")
            }
        }
        
        // Fallback: If JSON parsing fails but the driver pushed the name into descriptionText
        if (!isGuest && evt.descriptionText) {
            for (int i = 1; i <= 5; i++) {
                def gName = settings["guestName${i}"]
                if (settings["enableG${i}"] && gName && evt.descriptionText.contains(gName)) {
                    isGuest = true
                    matchedName = gName
                    break
                }
            }
        }

        if (isGuest) {
            logAction("Security Event: Door unlocked by authorized guest (${matchedName}). Evaluating automation conditions.")
            executeGuestArrival(matchedName)
        }
    }
}

def executeGuestArrival(guestName) {
    if (isDisabled()) return

    def allAway = false
    if (presenceSensors) {
        allAway = presenceSensors.every { it.currentValue("presence") == "not present" }
    }
    
    def isAwayMode = awayModes ? (awayModes as List).contains(location.mode) : false
    
    if (allAway && isAwayMode) {
        logAction("Trigger Condition Met: House is empty and in ${location.mode} mode.")
        
        if (arrivalMode && location.mode != arrivalMode) {
            location.setMode(arrivalMode)
            logAction("BMS Command -> Changed Location Mode to ${arrivalMode}.")
        }
        
        if (guestSwitch && guestSwitch.currentValue("switch") != "on") {
            guestSwitch.on()
            logAction("BMS Command -> Turned ON Virtual Guest Switch.")
        }
        
        sendAlert("Guest Access: '${guestName}' has arrived. House set to Guest Mode.")
    } else {
        logAction("Automation Aborted: House is not empty or not in an Away mode. (Occupied: ${!allAway} | Correct Mode: ${isAwayMode})")
    }
}

// --- AUTO REVERT LOGIC ---

def presenceHandler(evt) {
    if (isDisabled()) return

    if (evt.value == "present") {
        if (guestSwitch && guestSwitch.currentValue("switch") == "on") {
            guestSwitch.off()
            logAction("Homeowner Arrival Detected (${evt.device.displayName}). Guest Mode Virtual Switch turned OFF.")
            sendAlert("Guest Access: Homeowner arrival detected. Guest Mode turned OFF.")
        }
    }
}

def modeChangeHandler(evt) {
    if (isDisabled()) return

    state.lastModeChangeTime = now()
    def newMode = evt.value

    if (revertModes && (revertModes as List).contains(newMode)) {
        if (guestSwitch && guestSwitch.currentValue("switch") == "on") {
            guestSwitch.off()
            logAction("Location mode changed to ${newMode}. Guest Mode Virtual Switch turned OFF.")
            sendAlert("Guest Access: System Mode changed to ${newMode}. Guest Mode turned OFF.")
        }
    }
}

def expireCodeSlot(data) {
    def slotNum = data.slotNum
    def slot = settings["guestSlot${slotNum}"]
    def name = settings["guestName${slotNum}"] ?: "Guest ${slotNum}"
    
    // Note: Expiration and code deletion occurs even if the master disable switch is ON 
    // to maintain hardware security and ensure temporary codes don't persist improperly.
    if (mainLocks && slot != null) {
        mainLocks.each { lock -> 
            if (lock.hasCommand("deleteCode")) lock.deleteCode(slot) 
        }
        logAction("BMS Command -> Scheduled Expiry triggered: Deleted code for '${name}' from Slot ${slot}.")
        
        if (state.guestExpiries) state.guestExpiries.remove(slotNum.toString())
        
        // Remove from UI
        app.removeSetting("guestSlot${slotNum}")
        app.removeSetting("guestPin${slotNum}")
        app.removeSetting("guestExpiry${slotNum}")
        
        if (guestSwitch && guestSwitch.currentValue("switch") == "on" && !isDisabled()) {
            guestSwitch.off()
            logAction("Guest Mode Switch turned OFF due to code expiration.")
        }
        
        sendAlert("Guest Access: The PIN code for '${name}' has expired and was removed from the locks. Guest Mode ended.")
    }
}

// --- BUTTONS & HELPERS ---

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
    } else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logAction("Action logging history cleared.")
    } else if (btn.startsWith("btnSet")) {
        def slotNum = btn.replace("btnSet", "").toInteger()
        def slot = settings["guestSlot${slotNum}"]
        def pin = settings["guestPin${slotNum}"]
        def name = settings["guestName${slotNum}"] ?: "Guest ${slotNum}"
        def expHrs = settings["guestExpiry${slotNum}"]
        
        if (mainLocks && slot != null && pin) {
            mainLocks.each { lock -> 
                if (lock.hasCommand("setCode")) lock.setCode(slot, pin, name) 
            }
            
            def expireMsg = ""
            if (!state.guestExpiries) state.guestExpiries = [:]
            
            if (expHrs) {
                def expSecs = (expHrs * 3600).toInteger()
                // Store the epoch timestamp for the dashboard timer
                state.guestExpiries[slotNum.toString()] = now() + (expHrs * 3600000)
                runIn(expSecs, expireCodeSlot, [data: [slotNum: slotNum], overwrite: false])
                expireMsg = " (Will auto-expire in ${expHrs} hours)"
            } else {
                state.guestExpiries.remove(slotNum.toString())
            }
            
            logAction("BMS Command -> Pushed code for '${name}' to Slot ${slot} on all locks.${expireMsg}")
        } else {
            logAction("Error: Missing Lock, Slot, or PIN to set code for Slot ${slotNum}.")
        }
    } else if (btn.startsWith("btnDel")) {
        def slotNum = btn.replace("btnDel", "").toInteger()
        def slot = settings["guestSlot${slotNum}"]
        
        if (mainLocks && slot != null) {
            mainLocks.each { lock -> 
                if (lock.hasCommand("deleteCode")) lock.deleteCode(slot) 
            }
            
            if (state.guestExpiries) state.guestExpiries.remove(slotNum.toString())
            
            logAction("BMS Command -> Deleted code from Slot ${slot} on all locks.")
            
            // Wipe the input setting so it no longer triggers rendering on the dashboard
            app.removeSetting("guestSlot${slotNum}")
            app.removeSetting("guestPin${slotNum}")
            app.removeSetting("guestExpiry${slotNum}")
            
        } else {
            logAction("Error: Missing Lock or Slot to delete code for Slot ${slotNum}.")
        }
    }
}

def sendAlert(msg) {
    if (notifyDevices) {
        notifyDevices.deviceNotification(msg)
    }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size()>30) h=h[0..29]
    state.actionHistory=h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
