/**
 * Advanced Mail Monitor 2.0
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Mail Monitor 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "",
    category: "Convenience",
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
            
            if (mailSensors && mailSwitch) {
                def switchState = mailSwitch.currentValue("switch")?.toUpperCase() ?: "UNKNOWN"
                def indicatorText = (switchState == "ON") ? "MAIL WAITING" : "EMPTY / WAITING FOR DELIVERY"
                def statusExplanation = (switchState == "ON") ? "<span style='color:red;'><b>${indicatorText}</b></span>" : "<span style='color:green;'><b>${indicatorText}</b></span>"
                
                paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'>" +
                          "<b>System Status:</b> ${statusExplanation}</div>"
                
                input "btnClearMail", "button", title: "🎯 Manually Clear Mail Status & Lights"

                def tDelivery = state.todayDeliveryTime ?: "--:-- --"
                def tRetrieval = state.todayRetrievalTime ?: "--:-- --"
                def tWalk = state.lastRetrievalWalkTime ? formatSeconds(state.lastRetrievalWalkTime) : "--"
                def avgDel = state.avgDeliveryTime ? minutesToTimeStr(state.avgDeliveryTime) : "--:-- --"
                def avgRet = state.avgRetrievalTime ? minutesToTimeStr(state.avgRetrievalTime) : "--:-- --"

                def currentTempStr = "--"
                if (tempSensor || outsideTempSensor) {
                    def currentTemp
                    if (tempSensor) {
                        currentTemp = tempSensor.currentValue("temperature")
                    } else {
                        def outTemp = outsideTempSensor.currentValue("temperature")
                        currentTemp = outTemp ? (outTemp.toDouble() + (tempOffset ?: 20)) : null
                    }
                    if (currentTemp) currentTempStr = "${currentTemp}° ${outsideTempSensor && !tempSensor ? '(Est.)' : ''}"
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
                    <thead><tr><th>Metric</th><th>Today</th><th>Historical Average</th></tr></thead>
                    <tbody>
                        <tr><td class="dash-hl">Mail Delivery</td><td style='color: green;'><b>${tDelivery}</b></td><td>${avgDel}</td></tr>
                        <tr><td class="dash-hl">Mail Retrieval</td><td style='color: blue;'><b>${tRetrieval}</b></td><td>${avgRet}</td></tr>
                        <tr><td class="dash-hl">Retrieval Trip Time</td><td colspan="2" class="dash-val" style='color: purple;'><b>${tWalk}</b></td></tr>
                        
                        <tr><td colspan="3" class="dash-subhead">Environment & Hardware</td></tr>
                        <tr><td class="dash-hl">Mailbox Internal Temp</td><td colspan="2" class="dash-val">${currentTempStr}</td></tr>
                """
                
                mailSensors.each { sensor ->
                    def contactState = sensor.currentValue("contact")?.toUpperCase() ?: "UNKNOWN"
                    def contactColor = (contactState == "OPEN") ? "red" : "green"
                    def batt = sensor.currentValue("battery") ?: "N/A"
                    def battColor = "green"
                    if (batt != "N/A") {
                        if (batt.toInteger() <= 15) battColor = "red"
                        else if (batt.toInteger() <= 50) battColor = "orange"
                        batt = "${batt}%"
                    }
                    dashHTML += "<tr><td class='dash-hl'>${sensor.displayName}</td><td colspan='2' class='dash-val'>Status: <span style='color:${contactColor}; font-weight:bold;'>${contactState}</span> | Battery: <span style='color:${battColor}; font-weight:bold;'>${batt}</span></td></tr>"
                }

                dashHTML += """
                    </tbody>
                </table>
                """
                paragraph dashHTML
            } else {
                paragraph "<i>Please configure your sensors below to populate the dashboard.</i>"
            }
        }
    
        section("<b>📋 Application History</b>", hideable: true, hidden: true) {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${logText}</span>"
            } else {
                paragraph "<i>No history available yet. The log will populate as events occur.</i>"
            }
        }
        
        section("<b>1. Device Configuration</b>", hideable: true, hidden: true) {
            input "mailSensors", "capability.contactSensor", title: "Mailbox Door Sensor(s)", multiple: true, required: true
            input "tempSensor", "capability.temperatureMeasurement", title: "Internal Mailbox Temperature Sensor (Preferred)", required: false
            input "outsideTempSensor", "capability.temperatureMeasurement", title: "OR Outside Air Temperature Sensor (Fallback)", required: false
            input "tempOffset", "number", title: "Estimated Mailbox Heat Offset (° added to outside temp)", defaultValue: 20, required: false
            input "tempThreshold", "number", title: "Temperature Alert Threshold (°)", defaultValue: 90, required: false
            input "mailSwitch", "capability.switch", title: "Virtual Mail Indicator Switch", required: true
            input "deliveryLockout", "number", title: "State Change Lockout (Minutes)", defaultValue: 2, required: true
        }

        section("<b>2. Delivery Time Restrictions</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Restricts 'Mail Delivered' events to a specific time window. This prevents false deliveries when you drop off outgoing mail in the morning or evening.</div>"
            input "enableDeliveryWindow", "bool", title: "<b>Enable Delivery Time Window</b>", defaultValue: false, submitOnChange: true
            if (enableDeliveryWindow) {
                input "deliveryStartTime", "time", title: "Delivery Window Start Time", required: true
                input "deliveryEndTime", "time", title: "Delivery Window End Time", required: true
            }
        }

        section("<b>3. Home Activity Tracking</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Monitors home presence and exterior doors to calculate your 'Retrieval Trip Time' and prevent false mail retrieval triggers if nobody has left the house.</div>"
            input "exteriorDoors", "capability.contactSensor", title: "Exterior Doors (Front, Garage, etc.)", multiple: true, required: false
            input "arrivalSensors", "capability.presenceSensor", title: "Arrival Sensors (Mobile Phones)", multiple: true, required: false
            
            input "enableSecondaryCheck", "bool", title: "<b>Enable False Retrieval Protection</b>", defaultValue: false, submitOnChange: true
            if (enableSecondaryCheck) {
                input "activityTimeWindow", "number", title: "Activity Time Window (Minutes)", defaultValue: 10, required: true
            }
        }

        section("<b>4. Visual Indicators & Lights</b>", hideable: true, hidden: true) {
            input "indicatorLight", "capability.colorControl", title: "Standard RGB Lights", required: false, multiple: true
            
            input "inovelliSwitches", "capability.pushableButton", title: "Inovelli Red Series Switches", required: false, multiple: true, submitOnChange: true
            if (inovelliSwitches) {
                input "inovelliTarget", "enum", title: "Inovelli Target LEDs", required: true, defaultValue: "All", options: [
                    "All":"All LEDs", "7":"LED 7 (Top)", "6":"LED 6", "5":"LED 5", "4":"LED 4 (Middle)", "3":"LED 3", "2":"LED 2", "1":"LED 1 (Bottom)"
                ]
            }

            input "deliveryColor", "enum", title: "Color when Mail is Delivered", required: false, defaultValue: "Green", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"]
            input "lightLevel", "number", title: "Indicator Light Level (%)", defaultValue: 100, required: false, range: "1..100"
            input "retrievalLightAction", "enum", title: "Action when Mail is Retrieved", required: false, defaultValue: "Turn Off", options: ["Turn Off", "Leave On"]
        }
        
        section("<b>5. Integration & External Overrides</b>", hideable: true, hidden: true) {
            paragraph "<div style='background-color:#f8f9fa; padding:10px; border-left:3px solid #6c757d; font-size:13px; color:#555; margin-bottom:10px;'>" +
                      "<b>State Override Switch</b><br>" +
                      "Utilize this virtual switch to pause shared lighting applications during a delivery event. When mail is delivered, this application activates the switch. Configure external motion-lighting rules to yield to this state, ensuring mail notifications are not inadvertently disabled by room inactivity.</div>"
            input "overrideSwitch", "capability.switch", title: "State Override Switch", required: false
            
            paragraph "<div style='background-color:#f8f9fa; padding:10px; border-left:3px solid #6c757d; font-size:13px; color:#555; margin-bottom:10px;'>" +
                      "<b>Priority Yield Switch</b><br>" +
                      "Assign a switch linked to a higher-priority automation (e.g., a critical security sequence or primary routine). If active, the system logs the delivery but suppresses visual indicators until the priority sequence completes, preventing hardware conflicts.</div>"
            input "priorityYieldSwitch", "capability.switch", title: "Priority Yield Switch", required: false
        }

        section("<b>6. Notifications</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Sends silent push notifications to your mobile devices upon delivery or retrieval.</div>"
            input "pushDevices", "capability.notification", title: "Push Notification Devices", multiple: true, required: false
            input "sendPushDelivery", "bool", title: "Push on Delivery?", defaultValue: false
            input "sendPushRetrieval", "bool", title: "Push on Retrieval?", defaultValue: false
        }

        section("<b>⚙️ 7. System Settings & Reset</b>", hideable: true, hidden: true) {
            input "activeModes", "mode", title: "Active Modes", multiple: true, required: false
            input "btnForceReset", "button", title: "Reset Historical Averages & Logs"
        }
    }
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    state.historyLog = state.historyLog ?: []
    
    subscribe(mailSensors, "contact.open", "sensorOpenHandler")
    
    if (tempSensor) {
        subscribe(tempSensor, "temperature", "tempHandler")
    } else if (outsideTempSensor) {
        subscribe(outsideTempSensor, "temperature", "tempHandler")
    }
    
    if (exteriorDoors) subscribe(exteriorDoors, "contact.open", "homeActivityHandler")
    if (arrivalSensors) subscribe(arrivalSensors, "presence.present", "homeActivityHandler")
    if (priorityYieldSwitch) subscribe(priorityYieldSwitch, "switch", "priorityYieldHandler")
    
    if (inovelliSwitches) subscribe(inovelliSwitches, "switch.off", "inovelliMailSwitchOffHandler")
}

// ------------------------------------------------------------------------------
// APP HANDLERS
// ------------------------------------------------------------------------------

def inovelliMailSwitchOffHandler(evt) {
    if (mailSwitch && mailSwitch.currentValue("switch") == "on") {
        log.info "Switch '${evt.device.displayName}' turned off, but Mail is still waiting. Re-applying Mail LED indicator."
        setLightColor([evt.device], deliveryColor, lightLevel ?: 100, inovelliTarget ?: "All")
    }
}

def priorityYieldHandler(evt) {
    if (evt.value == "off") {
        if (mailSwitch.currentValue("switch") == "on") {
            log.info "Priority sequence ended. Mail is waiting. Activating indicators."
            addToHistory("RESUMING: Priority ended. Turning on mail lights.")
            
            if (indicatorLight) captureLightState(indicatorLight)
            
            if (overrideSwitch && overrideSwitch.currentValue("switch") != "on") {
                overrideSwitch.on()
            }
            
            if (indicatorLight) setLightColor(indicatorLight, deliveryColor, lightLevel ?: 100, "All")
            if (inovelliSwitches) setLightColor(inovelliSwitches, deliveryColor, lightLevel ?: 100, inovelliTarget ?: "All")
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "btnClearMail") {
        log.info "Manually clearing mail status..."
        if (mailSwitch) mailSwitch.off()
        
        if (indicatorLight) {
            if (retrievalLightAction == "Turn Off") restoreLightState(indicatorLight)
        }
        if (inovelliSwitches) {
            inovelliSwitches.each { device -> 
                def target = inovelliTarget ?: "All"
                if (target == "All") {
                    if (device.hasCommand("ledEffectAll")) device.ledEffectAll(255, 0, 0, 0)
                } else {
                    if (device.hasCommand("ledEffectOne")) device.ledEffectOne(target, 255, 0, 0, 0)
                }
            }
        }
 
        if (overrideSwitch) overrideSwitch.off()
        
        state.lastValidStateChange = 0 
        addToHistory("MANUAL CLEAR: System reset via app dashboard.")
        
    } else if (btn == "btnForceReset") {
        log.info "Resetting historical data..."
        state.historyLog = []
        state.avgDeliveryTime = null
        state.avgRetrievalTime = null
        state.deliveryCount = 0
        state.retrievalCount = 0
        state.todayDeliveryTime = null
        state.todayRetrievalTime = null
        state.lastRetrievalWalkTime = null
        addToHistory("SYSTEM WIPE: Historical data reset.")
    }
}

def homeActivityHandler(evt) { state.lastHomeActivity = new Date().time }

def sensorOpenHandler(evt) {
    try {
        def now = new Date().time

        // 1. BULLETPROOF DUAL-SENSOR LOCK
        // atomicState writes directly to the DB to prevent milliseconds-apart parallel execution.
        def lastEvt = atomicState.lastSensorEvent ?: 0
        if ((now - lastEvt) < 5000) return 
        atomicState.lastSensorEvent = now

        def switchState = mailSwitch?.currentValue("switch") ?: "off"
        def tz = location.timeZone ?: TimeZone.getDefault()
        
        // 2. HARD LOCKOUT CHECK (For bouncing doors)
        def lastStateChange = state.lastValidStateChange ?: 0
        def lockoutMillis = (deliveryLockout != null ? deliveryLockout.toInteger() : 2) * 60000
        
        if ((now - lastStateChange) < lockoutMillis) {
            addToHistory("DIAGNOSTIC: Ignored. Opened during ${deliveryLockout}m lockout.")
            return
        }

        // 3. DELIVERY WINDOW RESTRICTION CHECK (Re-written for raw time math)
        if (switchState != "on" && enableDeliveryWindow && deliveryStartTime && deliveryEndTime) {
            try {
                def startTime = timeToday(deliveryStartTime, tz).time
                def endTime = timeToday(deliveryEndTime, tz).time
                def isWithinWindow = false
                
                if (endTime < startTime) {
                    // Window crosses midnight
                    isWithinWindow = (now >= startTime || now <= endTime)
                } else {
                    // Standard window
                    isWithinWindow = (now >= startTime && now <= endTime)
                }
                
                if (!isWithinWindow) {
                    addToHistory("IGNORED: Opened outside of delivery window.")
                    return 
                }
            } catch (timeErr) {
                log.error "Time parse error: ${timeErr}"
                addToHistory("ERROR: Time check failed. Processing anyway to prevent missed mail.")
            }
        }

        // --- PAST ALL GATES: PROCESS THE EVENT ---
        def currentTimeStr = new Date().format("h:mm a", tz)
        def currentMinutes = getMinutesSinceMidnight(new Date(), tz)

        if (switchState == "on") {
            // --- MAIL RETRIEVAL LOGIC ---
            if (enableSecondaryCheck && (exteriorDoors || arrivalSensors)) {
                def lastActivity = state.lastHomeActivity ?: 0
                def window = (activityTimeWindow ?: 10) * 60000
                
                if ((now - lastActivity) > window) {
                    state.lastValidStateChange = now
                    
                    if (sendPushDelivery) sendMessage("📫 More mail was delivered!")
                    addToHistory("SECONDARY DELIVERY: No home activity detected.")
                    return
                }
            }

            def tripTimeStr = ""
            if ((exteriorDoors || arrivalSensors) && state.lastHomeActivity) {
                def timeDiff = now - state.lastHomeActivity
                if (timeDiff <= 900000) { 
                    def totalSecs = Math.round(timeDiff / 1000).toInteger()
                    state.lastRetrievalWalkTime = totalSecs
                    tripTimeStr = " (Trip Time: ${formatSeconds(totalSecs)})"
                }
            }

            mailSwitch.off()
            state.lastValidStateChange = now
            state.todayRetrievalTime = currentTimeStr
            updateAverage("retrieval", currentMinutes)
            addToHistory("RETRIEVAL DETECTED.${tripTimeStr}")
      
            if (retrievalLightAction == "Turn Off") {
                if (indicatorLight) restoreLightState(indicatorLight)
                if (inovelliSwitches) {
                    inovelliSwitches.each { device -> 
                        def target = inovelliTarget ?: "All"
                        if (target == "All") {
                            if (device.hasCommand("ledEffectAll")) device.ledEffectAll(255, 0, 0, 0)
                        } else {
                            if (device.hasCommand("ledEffectOne")) device.ledEffectOne(target, 255, 0, 0, 0)
                        }
                    }
                }
            }
            
            if (overrideSwitch) overrideSwitch.off()
            if (sendPushRetrieval) sendMessage("📬 Mail retrieved!")
     
        } else {
            // --- MAIL DELIVERY LOGIC ---
            mailSwitch.on()
            
            state.lastValidStateChange = now
            state.todayDeliveryTime = currentTimeStr
            updateAverage("delivery", currentMinutes)
            addToHistory("DELIVERY DETECTED.")
            
            if (sendPushDelivery) sendMessage("📫 Mail delivered!")

            if (priorityYieldSwitch && priorityYieldSwitch.currentValue("switch") == "on") {
                addToHistory("YIELD: Priority sequence active. Delaying lights.")
                return 
            }
            
            if (indicatorLight) captureLightState(indicatorLight)
     
            if (overrideSwitch && overrideSwitch.currentValue("switch") != "on") {
                overrideSwitch.on()
            }
            
            if (indicatorLight) setLightColor(indicatorLight, deliveryColor, lightLevel ?: 100, "All")
            if (inovelliSwitches) setLightColor(inovelliSwitches, deliveryColor, lightLevel ?: 100, inovelliTarget ?: "All")
        }
        
    } catch (Exception e) {
        log.error "Mail Monitor CRITICAL ERROR: ${e.message}"
        try { addToHistory("CRITICAL ERROR: ${e.message}") } catch(e2) {}
    }
}

// === STATE CAPTURE ENGINE ---
def captureLightState(devices) {
    if (!state.savedLightStates) state.savedLightStates = [:]
    
    devices.each { dev ->
        state.savedLightStates[dev.id] = [
            switch: dev.currentValue("switch"),
            hue: dev.currentValue("hue"),
            saturation: dev.currentValue("saturation"),
            level: dev.currentValue("level"),
            colorTemperature: dev.currentValue("colorTemperature")
        ]
        log.info "Captured previous state for ${dev.displayName}: ${state.savedLightStates[dev.id]}"
    }
}

def restoreLightState(devices) {
    if (!state.savedLightStates) return
    
    devices.each { dev ->
        def saved = state.savedLightStates[dev.id]
        if (saved) {
            if (saved.switch == "on") {
                if (saved.colorTemperature) {
                    dev.setColorTemperature(saved.colorTemperature, saved.level)
                } else if (saved.hue != null && saved.saturation != null) {
                    dev.setColor([hue: saved.hue, saturation: saved.saturation, level: saved.level])
                } else {
                    dev.on()
                    if (saved.level) dev.setLevel(saved.level)
                }
                log.info "Restored ${dev.displayName} to ON state."
            } else {
                dev.off()
                log.info "Restored ${dev.displayName} to OFF state."
            }
        } else {
            dev.off() 
        }
    }
    state.savedLightStates = [:] 
}

def setLightColor(devices, colorName, level, target = "All") {
    def inovelliHue = 0 
    def standardHue = 0
    def standardSat = 100
    
    switch(colorName) {
        case "White": inovelliHue = 255; standardSat = 0; break 
        case "Red": inovelliHue = 0; standardHue = 0; break 
        case "Green": inovelliHue = 85; standardHue = 33; break 
        case "Blue": inovelliHue = 170; standardHue = 66; break 
        case "Yellow": inovelliHue = 42; standardHue = 16; break 
        case "Orange": inovelliHue = 14; standardHue = 10; break 
        case "Purple": inovelliHue = 191; standardHue = 75; break 
        case "Pink": inovelliHue = 234; standardHue = 83; break 
    }
    
    devices.each { device -> 
        if (device.hasCommand("ledEffectAll") || device.hasCommand("ledEffectOne")) {
            if (target == "All") {
                if (device.hasCommand("ledEffectAll")) device.ledEffectAll(1, inovelliHue, level as Integer, 255) 
            } else {
                if (device.hasCommand("ledEffectOne")) device.ledEffectOne(target, 1, inovelliHue, level as Integer, 255)
            }
        } else {
            device.on() 
            device.setColor([hue: standardHue, saturation: standardSat, level: level as Integer])
        }
    }
}

def tempHandler(evt) {
    def currentTemp = evt.numericValue ?: evt.value.toDouble()
    if (evt.device.id == outsideTempSensor?.id) currentTemp += (tempOffset ?: 20)

    if (mailSwitch.currentValue("switch") == "on" && currentTemp >= (tempThreshold ?: 90)) {
        def today = new Date().format("yyyy-MM-dd", location.timeZone ?: TimeZone.getDefault())
        if (state.lastTempAlertDate != today) {
            sendMessage("🌡️ Warning: Box is estimated to be ${currentTemp}°. Get mail soon!")
            state.lastTempAlertDate = today
        }
    }
}

def sendMessage(msg) { settings.pushDevices ? settings.pushDevices*.deviceNotification(msg) : sendPush(msg) }

def updateAverage(type, currentMinutes) {
    def count = state."${type}Count" ?: 0
    def currentAvg = state."avg${type.capitalize()}Time" ?: currentMinutes
    state."avg${type.capitalize()}Time" = ((currentAvg * count) + currentMinutes) / (count + 1)
    state."${type}Count" = count + 1
}

def getMinutesSinceMidnight(date, tz) {
    return (new Date(date.time).format("H", tz).toInteger() * 60) + new Date(date.time).format("m", tz).toInteger()
}

def minutesToTimeStr(minutesNum) {
    if (!minutesNum) return "--:-- --"
    int totalMins = Math.round(minutesNum.toDouble()).toInteger() 
    int h = (totalMins / 60).toInteger()
    int m = totalMins % 60
  
    def ampm = h >= 12 ? "PM" : "AM"
    h = h % 12 ?: 12
    return "${h}:${m < 10 ? '0'+m : m} ${ampm}"
}

def formatSeconds(totalSecs) {
    if (!totalSecs) return "--"
    int m = (totalSecs / 60).toInteger()
    int s = totalSecs % 60
    if (m > 0) return "${m}m ${s}s"
    return "${s}s"
}

def addToHistory(msg) {
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone ?: TimeZone.getDefault())
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
}
