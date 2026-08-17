/**
 * Advanced Fitness Tracker 2.0
 */
definition(
    name: "Advanced Fitness Tracker 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    iconUrl: "",
    iconX2Url: "",
    oauth: true
)

preferences {
    page(name: "mainPage")
}

mappings {
    path("/ui") {
        action: [
            GET: "renderWebUI",
            POST: "handleWebAction"
        ]
    }
}

// ==============================================================================
// HUB CONFIGURATION UI
// ==============================================================================
def mainPage() {
    ensureStateMaps()

    dynamicPage(name: "mainPage", title: "Advanced Fitness Tracker 2.0: Hub Setup", install: true, uninstall: true) {
        
        section("<b>📱 Web App Access & Integrations</b>", hideable: true, hidden: true) {
            if (!state.accessToken) {
                input "btnEnableOAuth", "button", title: "🔐 Generate API Token"
            } else {
                input "btnRevokeOAuth", "button", title: "Revoke API Token"
                def localUri = "${getFullLocalApiServerUrl()}/ui?access_token=${state.accessToken}"
                def cloudUri = "${getApiServerUrl()}/${hubUID}/apps/${app.id}/ui?access_token=${state.accessToken}"
                paragraph "<b>Local Web Dashboard (Home Wi-Fi):</b><br><span style='font-family:monospace; font-size:12px; word-wrap:break-word;'><a href='${localUri}' target='_blank'>${localUri}</a></span>"
                paragraph "<b>Cloud Web Dashboard (Cellular):</b><br><span style='font-family:monospace; font-size:12px; word-wrap:break-word;'><a href='${cloudUri}' target='_blank'>${cloudUri}</a></span>"
                
                input "liftingAppUrl", "text", title: "<b>Link to Weight Lifting App</b><br>Paste the CLOUD URL of the Weight Lifting app here to cross-link them.", required: false
            }
        }

        section("<b>⚙️ Global Settings</b>", hideable: true, hidden: true) {
            input "numProfiles", "enum", title: "<b>Number of Profiles to Configure</b>", options: ["1","2","3","4","5"], defaultValue: "1", submitOnChange: true
            input "dogNames", "text", title: "<b>Dog Names (Comma Separated)</b>", defaultValue: "Sly, Goldie", submitOnChange: true
            input "txtLogEnable", "bool", title: "Enable Action Logging (Info)", defaultValue: true
        }
        
        def maxProfiles = settings.numProfiles ? settings.numProfiles.toInteger() : 1
        
        for (int i = 1; i <= maxProfiles; i++) {
            section("<b>👤 Profile ${i} Configuration</b>", hideable: true, hidden: true) {
                input "userName_${i}", "text", title: "Profile Name", defaultValue: "User ${i}", submitOnChange: true
                input "userAge_${i}", "number", title: "Age", defaultValue: 35, required: true
                input "userGender_${i}", "enum", title: "Gender", options: ["Male", "Female"], defaultValue: "Male", required: true
                input "userHeight_${i}", "number", title: "Height (Inches - Used for Stride Estimation)", defaultValue: 70, required: true
                
                paragraph "<b>Hub Variable Links</b>"
                input "tempSensor_${i}", "capability.temperatureMeasurement", title: "Weather/Temp Sensor (Used for Heat Index & K9 Safety)", required: false
                input "dailyVar_${i}", "hubVariable", title: "Daily Workout Hub Variable", required: false
                input "varType_${i}", "enum", title: "Variable Data Type", options: ["String (Yes/No)", "Boolean (true/false)"], defaultValue: "String (Yes/No)", required: true
                input "sleepScoreVar_${i}", "hubVariable", title: "Link Sleep Score Variable", required: false
                input "effortScoreVar_${i}", "hubVariable", title: "Effort Score Output Variable (Pushed to Lifting App)", required: false
                input "stepVar_${i}", "hubVariable", title: "Daily Total Steps Hub Variable", required: false
                input "weightVar_${i}", "hubVariable", title: "Body Weight Hub Variable", required: false
                input "moodVar_${i}", "hubVariable", title: "Link Mood Variable (String) - For Psychological Auto-Regulation", required: false
                
                input "liftingSyncVar_${i}", "hubVariable", title: "Lifting Sync Variable (String) - Receives workouts from Weight Lifting app", required: false
                
                paragraph "<b>🩹 Recovery Concierge (6:30 PM Check)</b>"
                input "pushDevice_${i}", "capability.notification", title: "Push Notification Device (e.g., Your Phone)", required: false
                input "recoveryThreshold_${i}", "number", title: "Effort Threshold for a 'Hard Day'", defaultValue: 60, required: true
                
                paragraph "<b>Presence & Automation Overrides</b>"
                input "startWorkoutSwitch_${i}", "capability.switch", title: "Workout Started Virtual Switch", required: false
                input "workoutSwitch_${i}", "capability.switch", title: "Workout Completed Virtual Switch (Turns off after 10m)", required: false
                
                input "btnClearAll_${i}", "button", title: "🗑️ Factory Reset All Data for ${settings["userName_${i}"] ?: "User ${i}"}"
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    ensureStateMaps()
    schedule("0 0 0 * * ?", "midnightReset")
    
    // Fires every day at exactly 6:30 PM (18:30)
    schedule("0 30 18 * * ?", "eveningRecoveryCheck")
    
    // Subscribe to Lifting Sync Variables
    def maxProfiles = settings.numProfiles ? settings.numProfiles.toInteger() : 1
    for (int i = 1; i <= maxProfiles; i++) {
        def vName = settings["liftingSyncVar_${i}"]
        if (vName) subscribe(location, "variable:${vName}", "liftingSyncHandler")
    }
}

def appButtonHandler(btn) {
    ensureStateMaps()

    if (btn == "btnEnableOAuth") {
        try { if (!state.accessToken) createAccessToken() } catch (e) {}
        return
    } else if (btn == "btnRevokeOAuth") {
        state.remove("accessToken")
        return
    }
    
    def maxProfiles = settings.numProfiles ? settings.numProfiles.toInteger() : 1
    for (int i = 1; i <= maxProfiles; i++) {
        if (btn == "btnClearAll_${i}") {
            state.yearlyTally.remove(i.toString())
            state.weeklyLog.remove(i.toString())
            state.yearlySteps.remove(i.toString())
            state.stepHistory.remove(i.toString())
            state.prs.remove(i.toString())
            state.weightHistory.remove(i.toString())
            state.remove("recoveryRx_${i}")
            
            def pIdStr = i.toString()
            def keysToRemove = state.jointPrs.findAll { k,v -> k.contains("_" + pIdStr + "_") || k.endsWith("_" + pIdStr) }.collect { it.key }
            keysToRemove.each { state.jointPrs.remove(it) }
        }
    }
}

// -------------------------------------------------------------
// RECOVERY CONCIERGE LOGIC
// -------------------------------------------------------------
def eveningRecoveryCheck() {
    logInfo("Running 6:30 PM Evening Recovery Check...")
    def maxProfiles = settings.numProfiles ? settings.numProfiles.toInteger() : 1
    
    for (int i = 1; i <= maxProfiles; i++) {
        def pId = i.toString()
        def uName = settings["userName_${i}"] ?: "Profile ${i}"
        
        def effortVarName = settings["effortScoreVar_${pId}"]
        def currentEffort = 0
        if (effortVarName) {
            def val = getGlobalVar(effortVarName)?.value
            if (val != null && val.toString().isNumber()) currentEffort = val.toInteger()
        }
        
        def threshold = settings["recoveryThreshold_${pId}"] != null ? settings["recoveryThreshold_${pId}"].toInteger() : 60
        
        if (currentEffort >= threshold) {
            def tools = [
                "15 minutes in the Massage Chair to flush out lactic acid 💺", 
                "a 10-minute Foam Rolling session targeting tight fascia 🧻", 
                "using the Theragun on your heaviest hit muscle groups 🔫", 
                "a 20-minute TENS unit session for localized recovery ⚡", 
                "a 3-minute Cold Shower to drastically drop inflammation 🥶"
            ]
            def rnd = new java.util.Random()
            def selectedTool = tools[rnd.nextInt(tools.size())]
            
            def msg = "🔥 High Strain Detected (${currentEffort} pts)! Evening Recovery Prescription: We recommend ${selectedTool}."
            
            // Save to state so it appears on the Web Dashboard
            state."recoveryRx_${pId}" = msg
            
            // Send Push Notification if configured
            def pushDev = settings["pushDevice_${pId}"]
            if (pushDev) {
                logInfo("Sending Recovery Nudge to ${uName}")
                pushDev.deviceNotification(msg)
            }
        }
    }
}

def midnightReset() {
    ensureStateMaps() 
    def maxProfiles = settings.numProfiles ? settings.numProfiles.toInteger() : 1
    for (int i = 1; i <= maxProfiles; i++) {
        def pId = i.toString()
        def dailyVarName = settings["dailyVar_${pId}"]
        if (dailyVarName) {
            def isString = (settings["varType_${pId}"] == "String (Yes/No)")
            setGlobalVar(dailyVarName, isString ? "No" : false)
        }
        def effortVarName = settings["effortScoreVar_${pId}"]
        if (effortVarName) setGlobalVar(effortVarName, "0")
        def stepVarName = settings["stepVar_${pId}"]
        if (stepVarName) setGlobalVar(stepVarName, "0")
        
        // Clear the recovery banner for the new day
        state.remove("recoveryRx_${pId}")
    }
}

def liftingSyncHandler(evt) {
    def val = evt.value
    if (!val || val == "" || val == "IDLE") return
    
    try {
        def slurp = new groovy.json.JsonSlurper()
        def data = slurp.parseText(val)
        
        if (data.action == "logWorkout") {
            // Extract the profile ID directly from the JSON payload (Bypasses Hubitat's evt.name limitations)
            def pId = data.profile?.toString()
            
            if (pId) {
                ensureStateMaps()
                def now = new Date().time
                def dateStr = new Date().format("MMM dd, h:mm a", location.timeZone ?: TimeZone.getDefault())
                def uniqueId = now.toString()
                
                // 1. Save the workout to history
                def logList = state.weeklyLog?."${pId}" ?: []
                logList.add(0, [id: uniqueId, timestamp: now, date: dateStr, type: "Weight Lifting", dist: null, dur: data.dur.toString(), pace: "N/A", effort: data.effort.toInteger(), steps: 0, dogs: [], effStr: "Moderate"])
                if (logList.size() > 30) logList = logList.take(30)
                state.weeklyLog["${pId}"] = logList
                
                state.yearlyTally["${pId}"] = (state.yearlyTally?."${pId}" ?: 0) + 1
                
                // 2. Set the Daily Workout Yes/No Variable
                def dailyVarName = settings["dailyVar_${pId}"]
                if (dailyVarName) {
                    def isString = (settings["varType_${pId}"] == "String (Yes/No)")
                    setGlobalVar(dailyVarName, isString ? "Yes" : true)
                }
                
                // 3. Keep the AFT effort variable perfectly in sync by recalculating the day's total
                def effortVarName = settings["effortScoreVar_${pId}"]
                if (effortVarName) {
                    def tz = location.timeZone ?: TimeZone.getDefault()
                    def todayStartMs = timeToday("00:00", tz).time
                    def remainingToday = logList.findAll { it.timestamp >= todayStartMs }
                    def totalEffortToday = 0
                    remainingToday.each { totalEffortToday += (it.effort ?: 0) }
                    setGlobalVar(effortVarName, totalEffortToday.toString())
                }
                
                logInfo("Synced Weight Lifting workout for Profile ${pId}: ${data.dur} mins, ${data.effort} effort.")
                
                // 4. Reset the incoming sync variable back to IDLE
                def syncVarName = settings["liftingSyncVar_${pId}"]
                if (syncVarName) {
                    setGlobalVar(syncVarName, "IDLE")
                }
            }
        }
    } catch (e) {
        log.error "Error parsing lifting sync data: ${e}"
    }
}

def ensureStateMaps() {
    if (state.yearlyTally == null) state.yearlyTally = [:]
    if (state.weeklyLog == null) state.weeklyLog = [:]
    if (state.yearlySteps == null) state.yearlySteps = [:]
    if (state.stepHistory == null) state.stepHistory = [:]
    if (state.prs == null) state.prs = [:]
    if (state.jointPrs == null) state.jointPrs = [:]
    if (state.dogYearlyDist == null) state.dogYearlyDist = [:]
    if (state.weightHistory == null) state.weightHistory = [:]
    
    def yearStr = new Date().format("yyyy", location.timeZone ?: TimeZone.getDefault())
    if (state.currentYear != yearStr) {
        state.currentYear = yearStr
        state.yearlyTally = [:]
        state.yearlySteps = [:] 
        state.dogYearlyDist = [:]
    }
}

// -------------------------------------------------------------
// HEAT INDEX & WEATHER GOVERNOR LOGIC
// -------------------------------------------------------------
def getHeatIndex(t, rh) {
    if (t == null || rh == null) return 70.0
    if (t < 80) return t
    
    def hi = 0.5 * (t + 61.0 + ((t - 68.0) * 1.2) + (rh * 0.094))
    if (hi >= 80) {
        hi = -42.379 + 2.04901523*t + 10.14333127*rh - 0.22475541*t*rh - 0.00683783*t*t - 0.05481717*rh*rh + 0.00122874*t*t*rh + 0.00085282*t*rh*rh - 0.00000199*t*t*rh*rh
        if (rh < 13 && t >= 80 && t <= 112) {
            hi -= ((13 - rh) / 4) * Math.sqrt((17 - Math.abs(t - 95)) / 17)
        } else if (rh > 85 && t >= 80 && t <= 87) {
            hi += ((rh - 85) / 10) * ((87 - t) / 5)
        }
    }
    return hi
}

def getHeatMultiplier(pId, wType) {
    def tSensor = settings["tempSensor_${pId}"]
    def tempFactor = 1.0
    // "Skating" left for historical backward compatibility
    def outdoorTypes = ["Running", "Mountain Biking", "Outdoor Skating", "Skating", "Hiking", "Walking", "Dog Walking", "Disk Golf", "Pickleball"]
    if (tSensor && (wType in outdoorTypes)) {
        def t = tSensor.currentValue("temperature")?.toDouble() ?: 70.0
        def rh = tSensor.currentValue("humidity")?.toDouble() ?: 50.0
        def hi = getHeatIndex(t, rh)
        
        if (hi >= 100) tempFactor = 1.30
        else if (hi >= 90) tempFactor = 1.20
        else if (hi >= 80) tempFactor = 1.10
        else if (t <= 32) tempFactor = 1.15
        else if (t <= 45) tempFactor = 1.05
    }
    return tempFactor
}
// -------------------------------------------------------------

def getWeightFactor(pId) {
    def wHist = state.weightHistory?."${pId}" ?: []
    def latestWeight = 150.0
    if (wHist.size() > 0) {
        latestWeight = wHist.last().weight.toDouble()
    }
    return Math.max(0.5, latestWeight / 150.0) 
}

def triggerStartSwitch(pIdStr) {
    def startSw = settings["startWorkoutSwitch_${pIdStr}"]
    if (startSw) {
        logInfo("Turning ON start switch for profile ${pIdStr}.")
        startSw.on()
    }
}

def bankTrackerSegment(tracker, pId, nowMs) {
    if (tracker.status != "RUNNING") return tracker
    def segmentMs = nowMs - tracker.start
    def segMins = segmentMs / 60000.0
    if (segMins <= 0) return tracker
    
    def oldMult = tracker.speedMult ?: 1.0
    def cadence = getIntensityMultiplierLiveCadence(tracker.type)
    def intensityMult = getIntensityMultiplier(tracker.type)
    
    def segSteps = Math.round(cadence * oldMult * segMins)
    def segDist = 0.0
    
    if (tracker.type in ["Weight Lifting", "Disk Golf", "Pickleball", "Kayaking", "YMCA Fitness Class", "Peloton", "Spin Class", "Indoor Rock Climbing", "Other"]) {
        segDist = 0.0
    } else if (tracker.type == "Mountain Biking") {
        segDist = segMins / (5.0 / oldMult)
    } else if (tracker.type == "Outdoor Skating" || tracker.type == "Indoor Skating" || tracker.type == "Skating") {
        segDist = segMins / (7.0 / oldMult)
    } else {
        def uHeight = settings["userHeight_${pId}"] != null ? settings["userHeight_${pId}"].toDouble() : 70.0
        def strideInches = uHeight * 0.413
        if (cadence > 0) segDist = (strideInches * segSteps) / 63360.0
    }
    
    def age = settings["userAge_${pId}"] != null ? settings["userAge_${pId}"].toInteger() : 35
    def gender = settings["userGender_${pId}"] ?: "Male"
    def sleepScoreFactor = calculateSleepScoreFactorLive(pId)
    def ageFactor = 1.0 + (age / 150.0)
    def genderFactor = (gender == "Female") ? 1.05 : 1.0
    def weightFactor = getWeightFactor(pId)
    
    def moodVar = settings["moodVar_${pId}"]
    def moodStr = ""
    if (moodVar) {
        def val = getGlobalVar(moodVar)?.value
        if (val) moodStr = val.toString()
    }
    
    def moodEffortFactor = 1.0
    if (moodStr in ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]) moodEffortFactor = 0.95
    else if (moodStr in ["😴", "🥱", "🥺", "🤡"]) moodEffortFactor = 1.10
    else if (moodStr in ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]) moodEffortFactor = 1.15
    else if (moodStr in ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]) moodEffortFactor = 1.20
    
    def tempFactor = getHeatMultiplier(pId, tracker.type)
    
    def segEffort = segMins * intensityMult * oldMult * sleepScoreFactor * ageFactor * genderFactor * tempFactor * weightFactor * moodEffortFactor
    
    tracker.acc = (tracker.acc ?: 0) + segmentMs
    tracker.accSteps = (tracker.accSteps ?: 0) + segSteps
    tracker.accDistMiles = (tracker.accDistMiles ?: 0.0) + segDist
    tracker.accEffort = (tracker.accEffort ?: 0.0) + segEffort
    tracker.start = nowMs
    
    return tracker
}

def getLearnedSpeedMultiplier(pId, wType) {
    if (!wType) return 1.0
    def logs = state.weeklyLog?."${pId}" ?: []
    def pastLogs = logs.findAll { it.type == wType && it.dist && it.dur && it.dist.toString().toDouble() > 0 }
    
    if (pastLogs.size() == 0) return 1.0 

    def totalDist = 0.0
    def totalDur = 0.0
    
    pastLogs.take(15).each {
        totalDist += it.dist.toString().toDouble()
        totalDur += it.dur.toString().toDouble()
    }
    
    if (totalDur <= 0 || totalDist <= 0) return 1.0
    
    def avgDistPerMin = totalDist / totalDur
    def speedMult = 1.0

    if (wType == "Mountain Biking") {
        speedMult = avgDistPerMin * 5.0
    } else if (wType == "Outdoor Skating" || wType == "Indoor Skating" || wType == "Skating") {
        speedMult = avgDistPerMin * 7.0
    } else if (wType in ["Weight Lifting", "Disk Golf", "Pickleball", "Kayaking", "YMCA Fitness Class", "Peloton", "Spin Class", "Indoor Rock Climbing", "Other"]) {
        return 1.0
    } else {
        def uHeight = settings["userHeight_${pId}"] != null ? settings["userHeight_${pId}"].toDouble() : 70.0
        def strideInches = uHeight * 0.413
        def cadence = getIntensityMultiplierLiveCadence(wType)
        if (cadence > 0 && strideInches > 0) {
            speedMult = (avgDistPerMin * 63360.0) / (strideInches * cadence)
        }
    }
    
    speedMult = Math.max(0.5, Math.min(2.5, speedMult))
    return Math.round(speedMult * 10) / 10.0
}

def logWeight(pId, weightVal) {
    ensureStateMaps()
    def wHist = state.weightHistory?."${pId}" ?: []
    def now = new Date().time
    def dateStr = new Date().format("MMM dd", location.timeZone ?: TimeZone.getDefault())
    wHist.add([timestamp: now, date: dateStr, weight: weightVal])
    
    wHist = wHist.sort { it.timestamp }
    if (wHist.size() > 30) wHist = wHist.takeRight(30)
    state.weightHistory["${pId}"] = wHist
    
    def wVarName = settings["weightVar_${pId}"]
    if (wVarName) {
        setGlobalVar(wVarName, weightVal.toString())
    }
}

def getEffortMultiplier(effortStr) {
    if (!effortStr) return 1.0
    if (effortStr.contains("Easy")) return 0.8
    if (effortStr.contains("Hard")) return 1.2
    if (effortStr.contains("Extreme")) return 1.5
    return 1.0
}

def getSmartPaceMultiplier(profileId, wType, currentDist, currentDur) {
    if (!wType) return 1.0
    if (!currentDist || currentDist <= 0 || !currentDur || currentDur <= 0 || wType.contains("+")) return 1.0
    
    def logs = state.weeklyLog?."${profileId}" ?: []
    def pastLogs = logs.findAll { it.type == wType && it.dist && it.dur && it.dist.toString().toDouble() > 0 }
    if (pastLogs.size() == 0) return 1.0 

    def totalDist = 0.0
    def totalDur = 0.0
    
    pastLogs.take(15).each {
        totalDist += it.dist.toString().toDouble()
        totalDur += it.dur.toString().toDouble()
    }
    
    if (totalDist <= 0) return 1.0

    def avgPace = totalDur / totalDist
    def currentPace = currentDur / currentDist
    def multiplier = avgPace / currentPace
    
    return Math.max(0.5, Math.min(2.0, multiplier))
}

def deleteWorkout(profileId, workoutId) {
    ensureStateMaps()
    def logList = state.weeklyLog?."${profileId}" ?: []
    def entryToRemove = logList.find { (it.id && it.id.toString() == workoutId.toString()) || (it.timestamp && it.timestamp.toString() == workoutId.toString()) }
    
    if (entryToRemove) {
        logList.remove(entryToRemove)
        state.weeklyLog["${profileId}"] = logList
        
        def currentTally = state.yearlyTally?."${profileId}" ?: 0
        if (currentTally > 0) state.yearlyTally["${profileId}"] = currentTally - 1
        
        if (entryToRemove.steps) {
            def currentYSteps = state.yearlySteps?."${profileId}" ?: 0
            if (currentYSteps >= entryToRemove.steps) {
                state.yearlySteps["${profileId}"] = currentYSteps - entryToRemove.steps
            }
        }
        
        if (entryToRemove.dogs && entryToRemove.dist) {
            def dDist = entryToRemove.dist.toString().toDouble()
            entryToRemove.dogs.each { d ->
                def currentD = state.dogYearlyDist?."${d}" ?: 0.0
                if (currentD >= dDist) state.dogYearlyDist["${d}"] = currentD - dDist
            }
        }
        
        def tz = location.timeZone ?: TimeZone.getDefault()
        def todayStartMs = timeToday("00:00", tz).time
        def remainingToday = logList.findAll { it.timestamp >= todayStartMs }
        
        def dailyVarName = settings["dailyVar_${profileId}"]
        def effortVarName = settings["effortScoreVar_${profileId}"]
        def stepVarName = settings["stepVar_${profileId}"]
        
        if (remainingToday.size() == 0) {
            if (dailyVarName) {
                def isString = (settings["varType_${profileId}"] == "String (Yes/No)")
                setGlobalVar(dailyVarName, isString ? "No" : false)
            }
            if (effortVarName) setGlobalVar(effortVarName, "0")
        } else {
            def totalEffortToday = 0
            remainingToday.each { totalEffortToday += (it.effort ?: 0) }
            if (effortVarName) setGlobalVar(effortVarName, totalEffortToday.toString())
        }
        
        if (stepVarName && entryToRemove.timestamp >= todayStartMs) {
            def curVal = getGlobalVar(stepVarName)?.value
            def startVal = (curVal != null && curVal.toString().isNumber()) ? curVal.toInteger() : 0
            def newVal = Math.max(0, startVal - (entryToRemove.steps ?: 0))
            setGlobalVar(stepVarName, newVal.toString())
        }
    }
}

def editWorkout(profileId, workoutId, wType, wDist, wDur, effMultStr) {
    ensureStateMaps()
    def logList = state.weeklyLog?."${profileId}" ?: []
    def entry = logList.find { (it.id && it.id.toString() == workoutId.toString()) || (it.timestamp && it.timestamp.toString() == workoutId.toString()) }

    if (entry) {
        def safeType = wType ?: entry.type
        def safeEffortStr = effMultStr ?: "Moderate"
        
        def oldDist = entry.dist ? entry.dist.toString().toDouble() : 0.0
        def oldSteps = entry.steps ?: 0
        def oldEffort = entry.effort ?: 0

        def newDistD = (wDist != null && wDist.toString() != "") ? wDist.toString().toDouble() : oldDist
        def newDurN = (wDur != null && wDur.toString() != "") ? wDur.toString().toInteger() : (entry.dur ? entry.dur.toString().toInteger() : 0)

        def finalPace = "N/A"
        if (newDistD > 0 && newDurN > 0) {
            def paceDec = newDurN / newDistD
            def pMins = Math.floor(paceDec).toInteger()
            def pSecs = Math.round((paceDec - pMins) * 60).toInteger()
            if (pSecs == 60) { pMins += 1; pSecs = 0 }
            finalPace = "${pMins}:${pSecs.toString().padLeft(2, '0')} /mi"
        }

        def cadenceMult = getIntensityMultiplierLiveCadence(safeType)
        def newSteps = Math.round(newDurN * cadenceMult)

        def effMult = getEffortMultiplier(safeEffortStr)
        def baseEffort = calculateEffortScoreLive(profileId, safeType, newDurN / 1.0)
        def smartMult = getSmartPaceMultiplier(profileId, safeType, newDistD, newDurN)
        def newEffort = Math.round(baseEffort * effMult * smartMult).toInteger() 

        state.yearlySteps["${profileId}"] = Math.max(0, (state.yearlySteps?."${profileId}" ?: 0) - oldSteps + newSteps)

        if (entry.dogs) {
            entry.dogs.each { d ->
                state.dogYearlyDist["${d}"] = Math.max(0.0, (state.dogYearlyDist?."${d}" ?: 0.0) - oldDist + newDistD)
            }
        }

        entry.type = safeType
        entry.dist = newDistD > 0 ? newDistD.toString() : null
        entry.dur = newDurN > 0 ? newDurN.toString() : null
        entry.pace = finalPace
        entry.steps = newSteps
        entry.effort = newEffort
        entry.effStr = safeEffortStr

        def tz = location.timeZone ?: TimeZone.getDefault()
        def todayStartMs = timeToday("00:00", tz).time
        if (entry.timestamp >= todayStartMs) {
            def effortVarName = settings["effortScoreVar_${profileId}"]
            if (effortVarName) {
                def remainingToday = logList.findAll { it.timestamp >= todayStartMs }
                def totalEffortToday = remainingToday.sum { it.effort ?: 0 } ?: 0
                setGlobalVar(effortVarName, totalEffortToday.toString())
            }
            def stepVarName = settings["stepVar_${profileId}"]
            if (stepVarName) {
                def curVal = getGlobalVar(stepVarName)?.value
                def startVal = (curVal != null && curVal.toString().isNumber()) ? curVal.toInteger() : 0
                setGlobalVar(stepVarName, Math.max(0, startVal - oldSteps + newSteps).toString())
            }
        }
        state.weeklyLog["${profileId}"] = logList
    }
}

def getIntensityMultiplier(wType) {
    if (!wType) return 1.0
    switch(wType) {
        case "Running": return 2.0
        case "Treadmill Running": return 2.0
        case "Mountain Biking": return 1.8
        case "Peloton": return 1.8
        case "Spin Class": return 1.8
        case "Indoor Rock Climbing": return 1.6
        case "Weight Lifting": return 1.5
        case "Pickleball": return 1.5
        case "YMCA Fitness Class": return 1.5
        case "Outdoor Skating": return 1.5
        case "Indoor Skating": return 1.5
        case "Skating": return 1.5 // Historical backward compatibility
        case "Hiking": return 1.4
        case "Kayaking": return 1.3
        case "Disk Golf": return 1.1
        case "Walking": return 1.0
        case "Treadmill Walking": return 1.0
        case "Dog Walking": return 1.0
        default: return 1.2
    }
}

def processDailySteps(pId, totalDeviceSteps) {
    ensureStateMaps()
    def now = new Date().time
    def todayStart = timeToday("00:00", location.timeZone ?: TimeZone.getDefault()).time

    def logList = state.weeklyLog?."${pId}" ?: []
    def todayWorkouts = logList.findAll { it.timestamp >= todayStart }
    def appTrackedSteps = 0
    todayWorkouts.each { appTrackedSteps += (it.steps ?: 0) }

    def extraSteps = totalDeviceSteps - appTrackedSteps
    if (extraSteps < 0) extraSteps = 0

    def sHist = state.stepHistory?."${pId}" ?: []
    sHist.removeAll { it.timestamp >= todayStart }
    sHist.add([timestamp: now, total: totalDeviceSteps, extra: extraSteps])
    if (sHist.size() > 14) sHist = sHist.take(14)
    state.stepHistory["${pId}"] = sHist

    state.yearlySteps["${pId}"] = (state.yearlySteps?."${pId}" ?: 0) + extraSteps

    def stepVar = settings["stepVar_${pId}"]
    if (stepVar) setGlobalVar(stepVar, totalDeviceSteps.toString())
}

def calculateWeeklySteps(pId) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def total = 0
    for (int d = 0; d < 7; d++) {
        def dayStart = timeToday("00:00", tz).time - (d * 86400000)
        def dayEnd = dayStart + 86400000

        def eod = (state.stepHistory?."${pId}" ?: []).find { it.timestamp >= dayStart && it.timestamp < dayEnd }
        if (eod) {
            total += eod.total
        } else {
            def wLogs = (state.weeklyLog?."${pId}" ?: []).findAll { it.timestamp >= dayStart && it.timestamp < dayEnd }
            total += (wLogs.sum { it.steps ?: 0 } ?: 0)
        }
    }
    return total
}

def paceToSeconds(paceStr) {
    if (!paceStr || paceStr == "N/A") return null
    try {
        def timePart = paceStr.split(" ")[0]
        def parts = timePart.split(":")
        return (parts[0].toInteger() * 60) + parts[1].toInteger()
    } catch (e) { return null }
}

def checkAndUpdatePRs(profiles, wType, dist, dur, paceStr) {
    if (!wType || !dist || dist <= 0 || wType.contains("+")) return
    def paceSecs = paceToSeconds(paceStr)
    if (!paceSecs) return

    def isJoint = (profiles.size() > 1)
    def pKey = isJoint ? "joint_" + profiles.sort().join("_") : profiles[0].toString()
    def prMap = isJoint ? state.jointPrs : state.prs

    if (!prMap[pKey]) prMap[pKey] = [:]
    def userPrs = prMap[pKey]

    def dateStr = new Date().format("MMM dd, yyyy", location.timeZone ?: TimeZone.getDefault())

    if (wType in ["Running", "Walking", "Hiking", "Dog Walking", "Treadmill Running", "Treadmill Walking"]) {
        if (dist >= 1.0) {
            def current1m = userPrs["fastest_1m"]
            if (!current1m || paceSecs < current1m.paceSecs) {
                userPrs["fastest_1m"] = [val: paceStr, paceSecs: paceSecs, date: dateStr, type: wType]
            }
        }
        if (dist >= 3.1) {
            def current5k = userPrs["fastest_5k"]
            if (!current5k || paceSecs < current5k.paceSecs) {
                userPrs["fastest_5k"] = [val: paceStr, paceSecs: paceSecs, date: dateStr, type: wType]
            }
        }
        def prCat = (wType in ["Running", "Treadmill Running"]) ? "Running" : "Walking"
        def currentLongest = userPrs["longest_${prCat}"]
        if (!currentLongest || dist > currentLongest.dist) {
            userPrs["longest_${prCat}"] = [val: "${dist} mi", dist: dist, date: dateStr]
        }
    }
}

def turnOffWorkoutSwitch(data) {
    def pId = data?.pId
    if (pId) {
        def wSwitch = settings["workoutSwitch_${pId}"]
        if (wSwitch) {
            logInfo("Auto-turning off workout virtual switch for profile ${pId}.")
            wSwitch.off()
        }
    }
}

def executeWorkoutSave(profileId, wType, wDist, wDur, overridePace, allParticipants = null, effortMult = 1.0, selectedDogs = [], effMultStr = "Moderate") {
    ensureStateMaps()
    def now = new Date().time
    def dateStr = new Date().format("MMM dd, h:mm a", location.timeZone ?: TimeZone.getDefault())
    def uniqueId = now.toString()
    
    def tracker = state."tracker_${profileId}"
    def isLiveTracker = (tracker?.status == "PENDING_SAVE" && (tracker?.displayType == wType || tracker?.type == wType))
    
    def distVal = wDist ? wDist.toString().replaceAll("[^\\d.]", "") : null
    def durVal = wDur ? wDur.toString().replaceAll("[^\\d.]", "") : null
    
    def finalPace = overridePace
    if (!finalPace && distVal && durVal && distVal.toDouble() > 0) {
        def paceDec = durVal.toDouble() / distVal.toDouble()
        def pMins = Math.floor(paceDec).toInteger()
        def pSecs = Math.round((paceDec - pMins) * 60).toInteger()
        if (pSecs == 60) { pMins += 1; pSecs = 0 }
        finalPace = "${pMins}:${pSecs.toString().padLeft(2, '0')} /mi"
    }
    
    def durNum = durVal ? durVal.toInteger() : 0
    def cadenceMult = getIntensityMultiplierLiveCadence(wType)
    
    def baseEffort = isLiveTracker ? (tracker.estEffort ?: 0) : calculateEffortScoreLive(profileId, wType, durNum / 1.0)
    
    def smartPaceMult = 1.0
    if (wType && !wType.contains("+")) {
        smartPaceMult = getSmartPaceMultiplier(profileId, wType, distVal?.toDouble() ?: 0.0, durNum)
    }
    def effortPoints = Math.round(baseEffort * effortMult * smartPaceMult).toInteger()
    
    def wSteps = isLiveTracker ? (tracker.estSteps ?: 0) : Math.round(durNum * cadenceMult)
    
    if (!allParticipants || profileId == allParticipants[0].toString()) {
        if (selectedDogs && distVal) {
            def distD = distVal.toDouble()
            selectedDogs.each { d ->
                state.dogYearlyDist["${d}"] = (state.dogYearlyDist?."${d}" ?: 0.0) + distD
            }
        }
    }
    
    def logList = state.weeklyLog?."${profileId}" ?: []
    logList.add(0, [id: uniqueId, timestamp: now, date: dateStr, type: wType, dist: distVal, dur: durVal, pace: finalPace, effort: effortPoints, steps: wSteps, dogs: selectedDogs, effStr: effMultStr])
    if (logList.size() > 30) logList = logList.take(30)
    state.weeklyLog["${profileId}"] = logList
    
    state.yearlyTally["${profileId}"] = (state.yearlyTally?."${profileId}" ?: 0) + 1
    state.yearlySteps["${profileId}"] = (state.yearlySteps?."${profileId}" ?: 0) + wSteps
    
    def dailyVarName = settings["dailyVar_${profileId}"]
    if (dailyVarName) {
        def isString = (settings["varType_${profileId}"] == "String (Yes/No)")
        setGlobalVar(dailyVarName, isString ? "Yes" : true)
    }
    
    def stepVarName = settings["stepVar_${profileId}"]
    if (stepVarName) {
        def curVal = getGlobalVar(stepVarName)?.value
        def startVal = (curVal != null && curVal.toString().isNumber()) ? curVal.toInteger() : 0
        setGlobalVar(stepVarName, (startVal + wSteps).toString())
    }
    
    def effortVarName = settings["effortScoreVar_${profileId}"]
    if (effortVarName) {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def todayStartMs = timeToday("00:00", tz).time
        def remainingToday = logList.findAll { it.timestamp >= todayStartMs }
        def totalEffortToday = 0
        remainingToday.each { totalEffortToday += (it.effort ?: 0) }
        setGlobalVar(effortVarName, totalEffortToday.toString())
    }
    
    def wSwitch = settings["workoutSwitch_${profileId}"]
    if (wSwitch) {
        logInfo("Turning ON end switch for ${profileId}.")
        wSwitch.on()
        runIn(600, "turnOffWorkoutSwitch", [data: [pId: profileId.toString()]])
    }
    
    def startSw = settings["startWorkoutSwitch_${profileId}"]
    if (startSw) startSw.off()
    
    if (allParticipants) {
        if (allParticipants.size() > 1 && profileId == allParticipants[0]) {
            checkAndUpdatePRs(allParticipants, wType, distVal?.toDouble() ?: 0.0, durNum, finalPace)
        }
        checkAndUpdatePRs([profileId], wType, distVal?.toDouble() ?: 0.0, durNum, finalPace)
    }
}

def logInfo(msg) {
    if (settings.txtLogEnable) log.info "${app.label}: ${msg}"
}

def getIntensityMultiplierLiveCadence(wType) {
    if (wType == "Running" || wType == "Treadmill Running") return 150
    else if (wType == "Hiking" || wType == "Dog Walking") return 90
    else if (wType == "Walking" || wType == "Treadmill Walking") return 100
    return 0 
}

def calculateSleepScoreFactorLive(pId) {
    def sScoreVar = settings["sleepScoreVar_${pId}"]
    def sScore = 85 
    if (sScoreVar) {
        def val = getGlobalVar(sScoreVar)?.value
        if (val != null && val.toString().isNumber()) sScore = val.toInteger()
    }
    return 1.0 + ((100 - sScore) / 200.0)
}

def calculateEffortScoreLive(pId, wType, durMinsDec) {
    if (durMinsDec < 0.01) return 0
    def age = settings["userAge_${pId}"] != null ? settings["userAge_${pId}"].toInteger() : 35
    def gender = settings["userGender_${pId}"] ?: "Male"
    def intensity = getIntensityMultiplier(wType)
    def sleepFactor = calculateSleepScoreFactorLive(pId) 
    def ageFactor = 1.0 + (age / 150.0)
    def genderFactor = (gender == "Female") ? 1.05 : 1.0
    def weightFactor = getWeightFactor(pId)
    
    def moodVar = settings["moodVar_${pId}"]
    def moodStr = ""
    if (moodVar) {
        def val = getGlobalVar(moodVar)?.value
        if (val) moodStr = val.toString()
    }
    
    def moodEffortFactor = 1.0
    if (moodStr in ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]) moodEffortFactor = 0.95
    else if (moodStr in ["😴", "🥱", "🥺", "🤡"]) moodEffortFactor = 1.10
    else if (moodStr in ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]) moodEffortFactor = 1.15
    else if (moodStr in ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]) moodEffortFactor = 1.20
    
    def tempFactor = getHeatMultiplier(pId, wType)
    
    return Math.round(durMinsDec * intensity * sleepFactor * ageFactor * genderFactor * tempFactor * weightFactor * moodEffortFactor).toInteger()
}

// ==============================================================================
// OAUTH WEB APP INTERFACE
// ==============================================================================

def parseWebArray(raw, defaultId = null) {
    if (!raw) return defaultId ? [defaultId.toString()] : []
    if (raw instanceof String) return raw.split(',').collect { it.trim() }
    if (raw instanceof List) return raw.collect { it.toString() }
    return defaultId ? [defaultId.toString()] : []
}

def handleWebAction() {
    def pId = params.profileId ?: "1"
    def action = params.action
    def nowMs = new Date().time
    def tracker = state."tracker_${pId}" ?: [status: "IDLE"]
    
    if (action == "start") {
        def parts = parseWebArray(params.participants, pId)
        def dogs = parseWebArray(params.dogs)
        parts.each { p -> 
            def initSpeed = getLearnedSpeedMultiplier(p.toString(), params.type)
            def tData = [status: "RUNNING", type: params.type, displayType: params.type, start: nowMs, acc: 0, accSteps: 0, accDistMiles: 0.0, accEffort: 0.0, speedMult: initSpeed, participants: parts, dogs: dogs]
            state."tracker_${p}" = tData 
            triggerStartSwitch(p.toString())
        }
    } else if (action == "transition") {
        def newType = params.newType
        if (newType && newType != tracker.type) {
            def parts = tracker.participants ?: [pId.toString()]
            parts.each { p -> 
                def pTracker = state."tracker_${p}"
                pTracker = bankTrackerSegment(pTracker, p.toString(), nowMs)
                if (!pTracker.displayType) pTracker.displayType = pTracker.type
                pTracker.displayType += " + ${newType}"
                pTracker.type = newType
                pTracker.start = nowMs
                pTracker.speedMult = getLearnedSpeedMultiplier(p.toString(), newType)
                state."tracker_${p}" = pTracker
            }
        }
    } else if (action == "speedUp" || action == "speedDown") {
        def parts = tracker.participants ?: [pId.toString()]
        def newMult = tracker.speedMult ?: 1.0
        tracker = bankTrackerSegment(tracker, pId, nowMs) 
        if (action == "speedUp") newMult = Math.min(2.5, newMult + 0.1)
        if (action == "speedDown") newMult = Math.max(0.5, newMult - 0.1)
        tracker.speedMult = newMult
        parts.each { p -> state."tracker_${p}" = tracker }
    } else if (action == "pause") {
        def parts = tracker.participants ?: [pId.toString()]
        tracker = bankTrackerSegment(tracker, pId, nowMs)
        tracker.status = "PAUSED"
        parts.each { p -> state."tracker_${p}" = tracker }
    } else if (action == "resume") {
        def parts = tracker.participants ?: [pId.toString()]
        tracker.start = nowMs
        tracker.status = "RUNNING"
        parts.each { p -> state."tracker_${p}" = tracker }
    } else if (action == "finish") {
        def parts = tracker.participants ?: [pId.toString()]
        tracker = bankTrackerSegment(tracker, pId, nowMs)
        
        def durMins = Math.max(1, Math.round(tracker.acc / 60000).toInteger())
        def finalDist = Math.round(tracker.accDistMiles * 100) / 100.0
        
        def paceDec = finalDist > 0 ? (durMins / finalDist) : 0
        def pMins = Math.floor(paceDec).toInteger()
        def pSecs = Math.round((paceDec - pMins) * 60).toInteger()
        if (pSecs == 60) { pMins += 1; pSecs = 0 }
        def finalPace = finalDist > 0 ? "${pMins}:${pSecs.toString().padLeft(2, '0')} /mi" : "N/A"
        
        tracker.estDur = durMins
        tracker.estDist = finalDist
        tracker.estPace = finalPace
        tracker.estSteps = tracker.accSteps
        tracker.estEffort = Math.round(tracker.accEffort ?: 0.0).toInteger()
        tracker.status = "PENDING_SAVE"
        
        parts.each { p -> state."tracker_${p}" = tracker }
    } else if (action == "save") {
        def parts = tracker.participants ?: [pId.toString()]
        def effMult = getEffortMultiplier(params.perceivedEffort)
        def d = tracker.dogs ?: []
        def saveType = tracker.displayType ?: tracker.type
        
        // Sync Mood 
        if (params.workoutMood && params.workoutMood != "") {
            def moodVar = settings["moodVar_${pId}"]
            if (moodVar) setGlobalVar(moodVar, params.workoutMood)
        }

        parts.each { p -> 
            executeWorkoutSave(p.toString(), saveType, params.dist ?: 0.0, params.dur, params.pace ?: "N/A", parts, effMult, d, params.perceivedEffort)
            state."tracker_${p}" = [status: "IDLE"]
        }
    } else if (action == "discard") {
        def parts = tracker.participants ?: [pId.toString()]
        parts.each { p -> state."tracker_${p}" = [status: "IDLE"] }
    } else if (action == "manualLog") {
        if (params.type && params.dur) {
            // Sync Mood 
            if (params.workoutMood && params.workoutMood != "") {
                def moodVar = settings["moodVar_${pId}"]
                if (moodVar) setGlobalVar(moodVar, params.workoutMood)
            }
            def parts = parseWebArray(params.participants, pId)
            def effMult = getEffortMultiplier(params.perceivedEffort)
            def d = parseWebArray(params.dogs)
            parts.each { p -> executeWorkoutSave(p.toString(), params.type, params.dist ?: 0.0, params.dur, null, parts, effMult, d, params.perceivedEffort) }
        }
    } else if (action == "logSteps") {
        if (params.deviceSteps) {
            processDailySteps(pId, params.deviceSteps.toInteger())
        }
    } else if (action == "logWeight") {
        if (params.weight) {
            logWeight(pId, params.weight.toDouble())
        }
    } else if (action == "delete") {
        if (params.workoutId) deleteWorkout(pId, params.workoutId)
    } else if (action == "edit") {
        if (params.workoutId && params.type && params.dur) {
            editWorkout(pId, params.workoutId, params.type, params.dist ?: 0.0, params.dur, params.editEffort)
        }
    } else if (action == "setMood") {
        if (params.newMood && params.newMood != "") {
            def moodVar = settings["moodVar_${pId}"]
            if (moodVar) setGlobalVar(moodVar, params.newMood)
        }
    }

    return renderWebUI()
}

def renderWebUI() {
    def pId = params.profileId ?: "1"
    def maxProfiles = settings.numProfiles ? settings.numProfiles.toInteger() : 1
    if (pId.toInteger() > maxProfiles) pId = "1"
    def uName = settings["userName_${pId}"] ?: "Profile ${pId}"
    def dNames = (settings.dogNames ?: "Sly, Goldie").split(",").collect { it.trim() }
    
    // Establish Mood Information for the Form Selectors
    def moodVar = settings["moodVar_${pId}"]
    def moodStr = moodVar ? getGlobalVar(moodVar)?.value?.toString() ?: "None" : "None"
    
    def moodsList = ['😀','😂','🥰','😎','🤔','😴','🤪','🤐','🤯','🥳','🥶','🥵','🤢','🥺','🤬','🤠','🤡','👽','👻','💩','😤','😫']
    def moodOptionsHtml = "<option value=''>-- Keep Current Mood --</option>"
    def dashboardMoodOptions = "<option value=''>Select Mood...</option>"
    moodsList.each { m ->
        def isSel = (moodStr == m) ? "selected" : ""
        moodOptionsHtml += "<option value='${m}' ${isSel}>${m}</option>"
        dashboardMoodOptions += "<option value='${m}' ${isSel}>${m}</option>"
    }
    
    def liftingLinkHtml = ""
    if (settings.liftingAppUrl) {
        liftingLinkHtml = "<a href='${settings.liftingAppUrl}' style='color:white; text-decoration:none; font-size:13px; background:rgba(255,255,255,0.2); padding:6px 12px; border-radius:15px; display:flex; align-items:center; border: 1px solid rgba(255,255,255,0.4); margin-left: auto; margin-right: 15px;'>🦾 Weight Lifting</a>"
    }

    // Header Tabs (Profiles)
    def tabsHtml = "<div class='profile-tabs'>"
    for (int i = 1; i <= maxProfiles; i++) {
        def tName = settings["userName_${i}"] ?: "User ${i}"
        def activeClass = (i.toString() == pId) ? "active" : ""
        tabsHtml += "<a href='?access_token=${state.accessToken}&profileId=${i}' class='profile-tab ${activeClass}'>${tName}</a>"
    }
    tabsHtml += "</div>"
    
    // Shared Components
    def participantsHtml = "<div class='input-group'><label>Participants (Joint Logging)</label><div class='checkbox-group'>"
    for(int j=1; j<=maxProfiles; j++) {
        def n = settings["userName_${j}"] ?: "User ${j}"
        def chk = (j.toString() == pId) ? "checked" : ""
        participantsHtml += "<label class='chk-label'><input type='checkbox' name='participants' value='${j}' ${chk}> ${n}</label>"
    }
    participantsHtml += "</div></div>"
    
    def dogCheckboxes = ""
    dNames.each { dName ->
        dogCheckboxes += "<label class='chk-label'><input type='checkbox' name='dogs' value='${dName}'> ${dName}</label>"
    }
    def liveDogSelectorHtml = "<div id='liveDogSelector' class='input-group dog-group' style='display:none;'><label>Include Dogs? 🐾</label><div class='checkbox-group'>${dogCheckboxes}</div></div>"
    def manualDogSelectorHtml = "<div id='manualDogSelector' class='input-group dog-group' style='display:none;'><label>Include Dogs? 🐾</label><div class='checkbox-group'>${dogCheckboxes}</div></div>"
    
    def tracker = state."tracker_${pId}" ?: [status: "IDLE"]
    def tStatus = tracker.status
    def trackerHtml = ""
    
    // ==========================================
    // SECTION 1: DASHBOARD & GOVERNOR
    // ==========================================
    def tz = location.timeZone ?: TimeZone.getDefault()
    
    def effData = []
    def stepData = []
    def distData = []
    
    for (int d = 6; d >= 0; d--) {
        def dayStart = timeToday("00:00", tz).time - (d * 86400000)
        def dayEnd = dayStart + 86400000
        def dayName = new Date(dayStart).format("E", tz)
        if (d == 0) dayName = "Today"
        
        def dayLogs = (state.weeklyLog?."${pId}" ?: []).findAll { it.timestamp >= dayStart && it.timestamp < dayEnd }
        
        def dEff = dayLogs.sum { it.effort ?: 0 } ?: 0
        def dDist = dayLogs.sum { it.dist ? it.dist.toString().toDouble() : 0.0 } ?: 0.0
        
        // Use history object for total steps to capture end-of-day device sync
        def eod = (state.stepHistory?."${pId}" ?: []).find { it.timestamp >= dayStart && it.timestamp < dayEnd }
        def dSteps = eod ? eod.total : (dayLogs.sum { it.steps ?: 0 } ?: 0)
        
        effData << [name: dayName, val: dEff]
        stepData << [name: dayName, val: dSteps]
        distData << [name: dayName, val: dDist]
    }
    
    def maxEff = Math.max(50, effData.max { it.val }?.val ?: 0)
    def maxSteps = Math.max(1000, stepData.max { it.val }?.val ?: 0)
    def maxDist = Math.max(1.0, distData.max { it.val }?.val ?: 0)
    
    def buildChartBlock = { dataList, maxVal, title, unit, colorClass, gradient ->
        def html = "<h4 style='margin: 15px 0 5px 0; font-size:14px; color:#334155;'>${title}</h4><div class='chart-container'>"
        dataList.each { col ->
            def pct = maxVal > 0 ? Math.min(100, Math.round((col.val / maxVal) * 100)) : 0
            def displayVal = ""
            
            // Only calculate and format if the value is actually greater than zero
            if (col.val > 0) {
                displayVal = unit == "mi" ? (Math.round(col.val * 100) / 100.0) : Math.round(col.val)
            }
            
            html += """
                <div class='chart-col'>
                    <div class='chart-dist' style='color:${colorClass};'>${displayVal}</div>
                    <div class='chart-bar' style='height: ${pct}%; background:${gradient};'></div>
                    <div class='chart-day'>${col.name}</div>
                </div>
            """
        }
        html += "</div>"
        return html
    }

    def effChartHtml = buildChartBlock(effData, maxEff, "⚡ Effort Score", "pts", "var(--warning)", "linear-gradient(to top, #f59e0b, #fcd34d)")
    def stepChartHtml = buildChartBlock(stepData, maxSteps, "👟 Total Steps", "steps", "var(--info)", "linear-gradient(to top, var(--info), #38bdf8)")
    def distChartHtml = buildChartBlock(distData, maxDist, "📏 Total Mileage", "mi", "var(--success)", "linear-gradient(to top, var(--success), #34d399)")
    
    // Recovery Concierge Banner
    def rxHtml = ""
    def currentRx = state."recoveryRx_${pId}"
    if (currentRx) {
        rxHtml = """
        <div class='card' style='border-left: 5px solid #8b5cf6; background: #f5f3ff;'>
            <h3 style='color: #4f46e5; margin-top:0;'>🩹 Recovery Concierge</h3>
            <p style='font-size:13px; color:#334155; font-weight:500; margin-bottom:0;'>${currentRx}</p>
        </div>
        """
    }
    
    // Strain & Recovery Governor Logic
    def sleepScore = 85
    def sVar = settings["sleepScoreVar_${pId}"]
    if (sVar) {
        def val = getGlobalVar(sVar)?.value
        if (val != null && val.toString().isNumber()) sleepScore = val.toInteger()
    }
    
    def currentEffort = 0
    def eVar = settings["effortScoreVar_${pId}"]
    if (eVar) {
        def val = getGlobalVar(eVar)?.value
        if (val != null && val.toString().isNumber()) currentEffort = val.toInteger()
    }
    
    def moodLabel = "Neutral Baseline"
    def moodMultAWL = 1.0
    
    if (moodStr in ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]) { moodLabel = "Highly Motivated"; moodMultAWL = 1.05 }
    else if (moodStr in ["😴", "🥱", "🥺", "🤡"]) { moodLabel = "Low Energy (+10% Cardio Strain)"; moodMultAWL = 0.90 }
    else if (moodStr in ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]) { moodLabel = "Frazzled / Stressed (+15% Cardio Strain)"; moodMultAWL = 0.85 }
    else if (moodStr in ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]) { moodLabel = "Sore/Sick (+20% Cardio Strain)"; moodMultAWL = 0.80 }
    def moodDisplay = moodStr != 'None' ? moodStr : '😐'
    
    def bWeight = 150.0
    def wVar = settings["weightVar_${pId}"]
    if (wVar) {
        def val = getGlobalVar(wVar)?.value
        if (val != null && val.toString().isNumber()) bWeight = val.toDouble()
    }
    
    def strainIndex = sleepScore > 0 ? (currentEffort / sleepScore.toDouble()) : 0
    def strainColor = strainIndex > 1.2 ? "var(--danger)" : (strainIndex >= 0.7 ? "var(--success)" : "var(--info)")
    def strainText = strainIndex > 1.2 ? "Over-Exertion (High Strain)" : (strainIndex >= 0.7 ? "Optimal Stimulus" : "Light Recovery Range")
    def proteinTarget = Math.round(bWeight * 0.8).toInteger()
    // FIX: Reduced effort division from 25.0 to 100.0 to prevent absurdly high water goals
    def waterTarget = Math.round((bWeight * 0.5) + ((currentEffort / 100.0) * 15)).toInteger() 
    
    // Cross-App CNS Visibility calculation
    def baseReadiness = 1.0
    if (strainIndex > 1.2) baseReadiness = 0.70  
    else if (sleepScore >= 85) baseReadiness = 1.05   
    else if (sleepScore >= 65) baseReadiness = 1.0    
    else baseReadiness = 0.84                         
    def awlReadiness = Math.max(0.60, Math.min(1.15, baseReadiness * moodMultAWL))
    def awlText = awlReadiness > 1.0 ? "High (Overload Protocol Enabled)" : (awlReadiness >= 0.95 ? "Normal Progression" : "Low (Forcing Volume Drop)")
    def awlColor = awlReadiness > 1.0 ? "var(--success)" : (awlReadiness >= 0.95 ? "var(--info)" : "var(--danger)")
    
    def fuelingHtml = """
        ${rxHtml}
        <div class='card' style='border-top: 4px solid var(--primary);'>
            <h3>🧬 Strain & Recovery Governor</h3>
            <div style='display:flex; justify-content:space-between; margin-bottom:15px;'>
                <div style='text-align:center;'>
                    <div style='font-size:24px; font-weight:bold; color:${strainColor};'>${strainIndex > 0 ? (Math.round(strainIndex * 100) / 100.0) : 0}</div>
                    <div style='font-size:11px; text-transform:uppercase; color:#64748b;'>Daily Strain Index</div>
                </div>
                <div style='text-align:center;'>
                    <div style='font-size:24px; font-weight:bold;'>${moodDisplay}</div>
                    <div style='font-size:11px; text-transform:uppercase; color:#64748b;'>Mood</div>
                </div>
                <div style='text-align:center;'>
                    <div style='font-size:24px; font-weight:bold; color:var(--dark);'>${proteinTarget}g</div>
                    <div style='font-size:11px; text-transform:uppercase; color:#64748b;'>Daily Protein</div>
                </div>
                <div style='text-align:center;'>
                    <div style='font-size:24px; font-weight:bold; color:var(--info);'>${waterTarget} oz</div>
                    <div style='font-size:11px; text-transform:uppercase; color:#64748b;'>Hydration Goal</div>
                </div>
            </div>
            <div style='font-size:12px; color:#64748b; margin-top:0; line-height: 1.5;'>
                <b>Cardio Strain Status: ${strainText}.</b><br>
                <b>Psychological Status: ${moodLabel}.</b><br>
                <b>🧠 AWL App Readiness Sync:</b> <span style='color:${awlColor}; font-weight:bold;'>${awlText}</span>
            </div>
        </div>
    """
    
    def dashboardMoodHtml = """
        <div class='card' style='padding-bottom: 15px;'>
            <h3>😀 Update Current Mood</h3>
            <p style='font-size:13px; color:#64748b; margin-top:0;'>Syncs your emotional state directly to the Advanced House Management app. (Zany 🤪 = Frazzled/Stressed)</p>
            <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                <input type='hidden' name='action' value='setMood'>
                <div style='display:flex; gap:10px;'>
                    <select name='newMood' class='form-control' required style='margin-top:0;'>
                        ${dashboardMoodOptions}
                    </select>
                    <button type='submit' class='btn btn-primary' style='margin-top:0; width:auto; padding: 10px 15px;'>Update</button>
                </div>
            </form>
        </div>
    """
    
    def moodMatrixHtml = "<div class='card' style='border-top: 4px solid var(--info);'>"
    moodMatrixHtml += "<h3>🧠 Household Mood Matrix</h3>"
    moodMatrixHtml += "<p style='font-size:12px; color:#64748b; margin-top:0; margin-bottom:15px;'>Live psychological state of all tracked profiles.</p>"
    moodMatrixHtml += "<div style='display:flex; flex-wrap:wrap; gap:10px;'>"
    
    def activeMoodsCount = 0
    for(int i=1; i<=maxProfiles; i++) {
        def mName = settings["userName_${i}"] ?: "User ${i}"
        def curMoodVar = settings["moodVar_${i}"]
        if (curMoodVar) {
            def mStr = getGlobalVar(curMoodVar)?.value?.toString() ?: "None"
            def mDisplay = mStr != "None" ? mStr : "😐"
            
            def mLabel = "Neutral Baseline"
            def effMod = "1.00x"
            if (mStr in ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]) { mLabel = "Highly Motivated"; effMod = "0.95x" }
            else if (mStr in ["😴", "🥱", "🥺", "🤡"]) { mLabel = "Low Energy"; effMod = "1.10x" }
            else if (mStr in ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]) { mLabel = "Frazzled / Stressed"; effMod = "1.15x" }
            else if (mStr in ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]) { mLabel = "Sick / Exhausted"; effMod = "1.20x" }
            
            moodMatrixHtml += """
            <div style='flex:1; min-width:120px; background:var(--light); border:1px solid var(--border); border-radius:8px; padding:10px; text-align:center;'>
                <div style='font-size:24px; margin-bottom:5px;'>${mDisplay}</div>
                <div style='font-weight:bold; font-size:13px; color:var(--dark);'>${mName}</div>
                <div style='font-size:11px; color:#64748b;'>${mLabel}</div>
                <div style='font-size:11px; font-weight:bold; color:var(--primary); margin-top:3px;'>Effort Mod: ${effMod}</div>
            </div>
            """
            activeMoodsCount++
        }
    }
    if (activeMoodsCount == 0) {
        moodMatrixHtml += "<div style='width:100%; text-align:center; font-size:13px; color:#64748b; font-style:italic;'>No mood variables linked to profiles.</div>"
    }
    moodMatrixHtml += "</div>"
    
    // -> Add Reference Guide inside the card <-
    moodMatrixHtml += """
    <h4 style='margin-top: 20px; border-bottom:1px solid var(--light); padding-bottom:5px; color:var(--dark);'>Mood Interpretation Guide</h4>
    <div style='overflow-x: auto;'>
    <table class="dash-table" style="font-size:11px; margin-top: 5px;">
        <thead>
            <tr><th style='background-color:#0ea5e9; color:white;'>Category</th><th style='background-color:#0ea5e9; color:white;'>Emojis</th><th style='background-color:#0ea5e9; color:white;'>Effort Mod</th><th style='background-color:#0ea5e9; color:white;'>Lifting Vol</th><th style='background-color:#0ea5e9; color:white;'>House Impact</th></tr>
        </thead>
        <tbody>
            <tr><td class='dash-hl'>Highly Motivated</td><td>🔥 😎 😀 🥳 🥰 😂 🤠</td><td>0.95x</td><td>+5%</td><td>Normal</td></tr>
            <tr><td class='dash-hl'>Neutral Baseline</td><td>😐 (or none)</td><td>1.00x</td><td>0%</td><td>Normal</td></tr>
            <tr><td class='dash-hl'>Low Energy</td><td>😴 🥱 🥺 🤡</td><td>1.10x</td><td>-10%</td><td>Normal</td></tr>
            <tr><td class='dash-hl'>Frazzled / Stressed</td><td>🤯 🤬 🤔 🤪 😤 😫</td><td>1.15x</td><td>-15%</td><td>Auto-Reg (-2°F / Mute)</td></tr>
            <tr><td class='dash-hl'>Sick / Exhausted</td><td>🤕 🤒 🩹 🥶 🥵 🤢 💩 🤐</td><td>1.20x</td><td>-20%</td><td>Auto-Reg (-2°F / Mute)</td></tr>
        </tbody>
    </table>
    </div>
    """
    
    moodMatrixHtml += "</div>"
    
    def weightHist = state.weightHistory?."${pId}" ?: []
    def weightChartHtml = ""
    if (weightHist.size() > 0) {
        def maxW = weightHist.max { it.weight }.weight
        def minW = weightHist.min { it.weight }.weight
        def range = maxW - minW
        if (range == 0) range = 10
        def padding = range * 0.2
        def chartMax = maxW + padding
        def chartMin = minW - padding
        def chartRange = chartMax - chartMin

        def points = []
        def width = 100
        def height = 40
        def step = weightHist.size() > 1 ? (width / (weightHist.size() - 1)) : width
        
        weightHist.eachWithIndex { entry, idx ->
            def x = idx * step
            def y = height - (((entry.weight - chartMin) / chartRange) * height)
            points << "${x},${y}"
        }
        
        def currW = weightHist.last().weight
        def diffStr = ""
        if (weightHist.size() > 1) {
            def firstW = weightHist.first().weight
            def diff = currW - firstW
            def color = diff > 0 ? "var(--warning)" : (diff < 0 ? "var(--success)" : "var(--text)")
            def sign = diff > 0 ? "+" : ""
            diffStr = "<span style='color:${color}; font-size:12px; margin-left:10px;'>${sign}${Math.round(diff * 10) / 10.0} (from start)</span>"
        }
        
        weightChartHtml = """
        <div class='card' style='padding-bottom:10px;'>
            <h3 style='display:flex; align-items:center;'>
                ⚖️ Body Weight Trend 
                <span style='font-size:16px; margin-left:auto; font-weight:bold; color:var(--dark);'>${currW}</span>
                ${diffStr}
            </h3>
            <svg viewBox="-10 -10 120 60" style="width:100%; height:120px; overflow:visible; margin-top:5px;">
                <line x1="0" y1="0" x2="100" y2="0" stroke="#e2e8f0" stroke-width="0.5" />
                <line x1="0" y1="20" x2="100" y2="20" stroke="#e2e8f0" stroke-width="0.5" />
                <line x1="0" y1="40" x2="100" y2="40" stroke="#e2e8f0" stroke-width="0.5" />
                <polyline fill="none" stroke="var(--info)" stroke-width="2" points="${points.join(' ')}" />
        """
        weightHist.eachWithIndex { entry, idx ->
            def x = idx * step
            def y = height - (((entry.weight - chartMin) / chartRange) * height)
            weightChartHtml += "<circle cx='${x}' cy='${y}' r='2' fill='var(--primary)' />"
            
            if (idx == 0 || idx == weightHist.size() - 1) {
                def anchor = idx == 0 ? "start" : "end"
                weightChartHtml += "<text x='${x}' y='${y - 5}' font-size='5' fill='#64748b' text-anchor='${anchor}'>${entry.weight}</text>"
            }
        }
        weightChartHtml += "</svg></div>"
    } else {
        weightChartHtml = "<div class='card'><h3>⚖️ Body Weight Trend</h3><p style='font-size:13px; color:#64748b;'>No weight logs yet. Head to Logging to record your weight to see your trend.</p></div>"
    }
    
    def dogMilesHtml = ""
    dNames.each { dName ->
        def yearDist = state.dogYearlyDist?."${dName}" ?: 0.0
        def weekDist = 0.0
        def sevenDaysAgo = new Date().time - (7 * 86400000)
        for(int j=1; j<=maxProfiles; j++) {
            def pLogs = state.weeklyLog?."${j}" ?: []
            pLogs.findAll { it.timestamp >= sevenDaysAgo && it.dogs?.contains(dName) }.each { log ->
                weekDist += (log.dist ? log.dist.toString().toDouble() : 0.0)
            }
        }
        dogMilesHtml += "<tr><td><b>${dName}</b></td><td>${Math.round(weekDist * 100) / 100.0} mi</td><td>${Math.round(yearDist * 100) / 100.0} mi</td></tr>"
    }

    // Section 2: Live Tracker Setup
    def getSpeedLabel = { tType, sMult, uHeightStr ->
        def noDist = ["Weight Lifting", "Disk Golf", "Pickleball", "Kayaking", "YMCA Fitness Class", "Peloton", "Spin Class", "Indoor Rock Climbing", "Other"]
        if (tType in noDist) return ""
        def distPerMin = 0.0
        if (tType == "Mountain Biking") distPerMin = sMult / 5.0
        else if (tType == "Outdoor Skating" || tType == "Indoor Skating" || tType == "Skating") distPerMin = sMult / 7.0
        else {
            def cad = getIntensityMultiplierLiveCadence(tType)
            def stInches = (uHeightStr != null ? uHeightStr.toString().toDouble() : 70.0) * 0.413
            distPerMin = (stInches * cad * sMult) / 63360.0
        }
        if (distPerMin <= 0) return ""
        def mph = distPerMin * 60.0
        def paceDec = 1.0 / distPerMin
        def pM = Math.floor(paceDec).toInteger()
        def pS = Math.round((paceDec - pM) * 60).toInteger()
        if (pS == 60) { pM++; pS = 0 }
        def pStr = "${pM}:${pS.toString().padLeft(2, '0')}"
        return " <span style='font-size:14px; font-weight:normal; color:#64748b;'>(${Math.round(mph * 10) / 10.0} mph | ${pStr} /mi)</span>"
    }

    if (tStatus == "IDLE") {
        trackerHtml = """
            <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                <input type='hidden' name='action' value='start'>
                <label>Workout Type</label>
                <select name='type' id='liveType' class='form-control' required onchange='checkLiveType()'>
                    <option value=''>Select Type...</option>
                    <option value='Hiking'>Hiking</option>
                    <option value='Kayaking'>Kayaking</option>
                    <option value='Walking'>Walking</option>
                    <option value='Treadmill Walking'>Treadmill Walking</option>
                    <option value='Dog Walking'>Dog Walking</option>
                    <option value='Weight Lifting'>Weight Lifting</option>
                    <option value='Running'>Running</option>
                    <option value='Treadmill Running'>Treadmill Running</option>
                    <option value='Mountain Biking'>Mountain Biking</option>
                    <option value='Peloton'>Peloton</option>
                    <option value='Spin Class'>Spin Class</option>
                    <option value='Outdoor Skating'>Outdoor Skating</option>
                    <option value='Indoor Skating'>Indoor Skating</option>
                    <option value='Indoor Rock Climbing'>Indoor Rock Climbing</option>
                    <option value='YMCA Fitness Class'>YMCA Fitness Class</option>
                    <option value='Disk Golf'>Disk Golf</option>
                    <option value='Pickleball'>Pickleball</option>
                    <option value='Other'>Other</option>
                </select>
                ${participantsHtml}
                ${liveDogSelectorHtml}
                <button type='submit' class='btn btn-primary'>▶️ Start Tracker</button>
            </form>
            <script>
                function checkLiveType() {
                    let t = document.getElementById('liveType').value;
                    let dogOk = ["Dog Walking", "Hiking", "Walking", "Running", "Mountain Biking"].includes(t);
                    document.getElementById('liveDogSelector').style.display = dogOk ? 'block' : 'none';
                }
            </script>
        """
    } else if (tStatus == "RUNNING" || tStatus == "PAUSED") {
        def runningTimeMs = tracker.acc ?: 0
        if (tStatus == "RUNNING") runningTimeMs += (new Date().time - tracker.start)
        def runningMins = Math.floor(runningTimeMs / 60000).toInteger()
        def runningSecs = Math.floor((runningTimeMs % 60000) / 1000).toInteger()
        
        def statusColor = tStatus == "RUNNING" ? "var(--success)" : "var(--warning)"
        def statusText = tStatus == "RUNNING" ? "Tracking" : "PAUSED"
        
        def currentTypeDisplay = tracker.displayType ?: tracker.type
        def pListNames = []
        (tracker.participants ?: [pId.toString()]).each { pid -> pListNames << (settings["userName_${pid}"] ?: "Profile ${pid}") }
        def pString = pListNames.join(", ")
        def dString = tracker.dogs ? "<br><b>Dogs:</b> 🐾 " + tracker.dogs.join(", ") : ""
        
        def age = settings["userAge_${pId}"] ?: 35
        def gender = settings["userGender_${pId}"] ?: "Male"
        def uHeight = settings["userHeight_${pId}"] != null ? settings["userHeight_${pId}"].toDouble() : 70.0
        def strideInches = uHeight * 0.413
        
        def sleepScoreFactor = calculateSleepScoreFactorLive(pId)
        def weightFactor = getWeightFactor(pId)
        def cadence = getIntensityMultiplierLiveCadence(tracker.type)
        def intensityMult = getIntensityMultiplier(tracker.type)
        
        // MOOD EFFORT FACTOR JS LOGIC
        def moodEffortFactor = 1.0
        if (moodStr in ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]) moodEffortFactor = 0.95
        else if (moodStr in ["😴", "🥱", "🥺", "🤡"]) moodEffortFactor = 1.10
        else if (moodStr in ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]) moodEffortFactor = 1.15
        else if (moodStr in ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]) moodEffortFactor = 1.20
        
        def tempFactor = 1.0
        def tSensor = settings["tempSensor_${pId}"]
        def outdoorTypes = ["Running", "Mountain Biking", "Outdoor Skating", "Skating", "Hiking", "Walking", "Dog Walking", "Disk Golf", "Pickleball"]
        def isOutdoor = (tracker.type in outdoorTypes) || (tracker.displayType in outdoorTypes)
        def curT = 70.0
        def curHI = 70.0
        
        if (tSensor && isOutdoor) {
            curT = tSensor.currentValue("temperature")?.toDouble() ?: 70.0
            def rh = tSensor.currentValue("humidity")?.toDouble() ?: 50.0
            curHI = getHeatIndex(curT, rh)
            
            if (curHI >= 100) tempFactor = 1.30
            else if (curHI >= 90) tempFactor = 1.20
            else if (curHI >= 80) tempFactor = 1.10
            else if (curT <= 32) tempFactor = 1.15
            else if (curT <= 45) tempFactor = 1.05
        }

        def hasDogs = (tracker.dogs && tracker.dogs.size() > 0)
        def isHot = (curT >= 80 || curHI >= 85)
        
        def k9WarningHtml = ""
        if (hasDogs && isHot) {
            k9WarningHtml = "<div id='k9Warning' style='background:var(--danger); color:white; padding:10px; border-radius:6px; margin-bottom:15px; font-weight:bold; font-size:13px; text-align:left;'>⚠️ K9 HEAT WARNING: Pavement may be dangerously hot (${curT}°F). Limit duration to 15 mins to protect paws and prevent heat stroke.</div>"
        }
        
        def weatherDisplayHtml = ""
        if (tSensor && isOutdoor) {
            def hiStr = curHI > curT ? " (Feels like ${Math.round(curHI)}°F)" : ""
            weatherDisplayHtml = "<div style='font-size:11px; color:#64748b; margin-top:8px;'>🌤️ Outdoor Conditions: <b>${curT}°F</b>${hiStr}</div>"
        }

        def btnHtml = ""
        if (tStatus == "RUNNING") {
            btnHtml = "<form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}' style='display:inline;'><input type='hidden' name='action' value='pause'><button type='submit' class='btn btn-warning'>⏸️ Pause</button></form>"
        } else {
            btnHtml = "<form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}' style='display:inline;'><input type='hidden' name='action' value='resume'><button type='submit' class='btn btn-primary'>▶️ Resume</button></form>"
        }
        
        def extraSpeedInfo = getSpeedLabel(tracker.type, tracker.speedMult ?: 1.0, settings["userHeight_${pId}"])
        
        def liveSpeedHTML = ""
        if (tStatus == "RUNNING") {
            liveSpeedHTML = """
            <div class='speed-control'>
                <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}' style='margin:0;'>
                    <input type='hidden' name='action' value='speedDown'>
                    <button type='submit' class='btn btn-info' style='width:auto; padding:8px 15px; margin:0;'>🐢 Slower</button>
                </form>
                <div class='speed-readout'>Speed: <span id='speedDisplay'>${Math.round((tracker.speedMult ?: 1.0) * 100)}%${extraSpeedInfo}</span></div>
                <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}' style='margin:0;'>
                    <input type='hidden' name='action' value='speedUp'>
                    <button type='submit' class='btn btn-info' style='width:auto; padding:8px 15px; margin:0;'>🐇 Faster</button>
                </form>
            </div>
            """
        } else {
            liveSpeedHTML = "<div class='speed-readout-offline'>Speed: ${Math.round((tracker.speedMult ?: 1.0) * 100)}%${extraSpeedInfo}</div>"
        }
        
        trackerHtml = """
            <div class='live-panel'>
                ${k9WarningHtml}
                <div><b>Activity:</b> ${currentTypeDisplay}</div>
                <div style='font-size:11px; color:var(--info); margin-bottom:5px; font-weight:bold;'>Current Segment: ${tracker.type}</div>
                <div style='font-size:12px; color:#555;'><b>Participants:</b> ${pString}${dString}</div>
                <div style='margin-top:5px;'><b>Status:</b> <span style='color:${statusColor}; font-weight:bold;'>${statusText}</span></div>
                ${weatherDisplayHtml}
                
                ${liveSpeedHTML}
                
                <div class='live-metrics'>
                    <div>Dist: <span id='liveDist'>0.00</span> mi</div>
                    <div>Steps: <span id='liveSteps'>0</span></div>
                    <div>Effort: <span id='liveEffort'>0</span></div>
                </div>
                
                <div class='timer' id='liveTimer'>00:00</div>
                <div class='btn-group'>
                    ${btnHtml}
                    <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}' style='display:inline;'>
                        <input type='hidden' name='action' value='finish'>
                        <button type='submit' class='btn btn-danger'>⏹️ Finish</button>
                    </form>
                </div>
                
                <div style='margin-top:15px; padding-top:15px; border-top:1px solid var(--border);'>
                    <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}' style='margin:0; display:flex; gap:10px;'>
                        <input type='hidden' name='action' value='transition'>
                        <select name='newType' class='form-control' style='margin:0;' required>
                            <option value=''>Transition To...</option>
                            <option value='Hiking'>Hiking</option>
                            <option value='Kayaking'>Kayaking</option>
                            <option value='Walking'>Walking</option>
                            <option value='Treadmill Walking'>Treadmill Walking</option>
                            <option value='Dog Walking'>Dog Walking</option>
                            <option value='Weight Lifting'>Weight Lifting</option>
                            <option value='Running'>Running</option>
                            <option value='Treadmill Running'>Treadmill Running</option>
                            <option value='Mountain Biking'>Mountain Biking</option>
                            <option value='Peloton'>Peloton</option>
                            <option value='Spin Class'>Spin Class</option>
                            <option value='Outdoor Skating'>Outdoor Skating</option>
                            <option value='Indoor Skating'>Indoor Skating</option>
                            <option value='Indoor Rock Climbing'>Indoor Rock Climbing</option>
                            <option value='YMCA Fitness Class'>YMCA Fitness Class</option>
                            <option value='Disk Golf'>Disk Golf</option>
                            <option value='Pickleball'>Pickleball</option>
                            <option value='Other'>Other</option>
                        </select>
                        <button type='submit' class='btn btn-info' style='width:auto; margin:0;'>🔄 Switch</button>
                    </form>
                </div>
            </div>
            <script>
                let isRunning = ${tStatus == "RUNNING"};
                let startMs = ${tracker.start ?: 0};
                let accMs = ${tracker.acc ?: 0};
                
                let strideInches = ${strideInches};
                let cadence = ${cadence};
                let intensityMult = ${intensityMult};
                let sleepScore = ${sleepScoreFactor};
                let tempFactor = ${tempFactor};
                let weightFactor = ${weightFactor};
                let moodEffortFactor = ${moodEffortFactor}; // MOOD
                
                let age = ${age};
                let gender = '${gender}';
                let ageFactor = 1.0 + (age / 150.0);
                let genderFactor = (gender === "Female") ? 1.05 : 1.0;
                
                let accSteps = ${tracker.accSteps ?: 0};
                let accDistMiles = ${tracker.accDistMiles ?: 0.0};
                let accEffort = ${tracker.accEffort ?: 0.0};
                let speedMult = ${tracker.speedMult ?: 1.0};
                
                let hasDogs = ${hasDogs};
                let isHot = ${isHot};
                
                function updateTimer() {
                    let totalMs = accMs;
                    let currentSegMs = 0;
                    if(isRunning) {
                        currentSegMs = (Date.now() - startMs);
                        totalMs += currentSegMs;
                    }
                    
                    let m = Math.floor(totalMs / 60000);
                    let s = Math.floor((totalMs % 60000) / 1000);
                    let timerEl = document.getElementById('liveTimer');
                    timerEl.innerText = m + 'm ' + (s < 10 ? '0' : '') + s + 's';
                    
                    let currentSegMins = currentSegMs / 60000.0;
                    
                    if (isRunning && hasDogs && isHot && ((accMs + currentSegMs)/60000) >= 15) {
                        timerEl.style.color = 'var(--danger)';
                        let w = document.getElementById('k9Warning');
                        if (w && !w.style.animation) w.style.animation = 'flash 1.5s infinite alternate';
                    }
                    
                    let curSteps = 0;
                    if (cadence > 0) curSteps = Math.round(cadence * speedMult * currentSegMins);
                    let totalSteps = accSteps + curSteps;
                    document.getElementById('liveSteps').innerText = totalSteps;
                    
                    let curDist = 0.0;
                    if (['Weight Lifting', 'Disk Golf', 'Pickleball', 'Kayaking', 'YMCA Fitness Class', 'Peloton', 'Spin Class', 'Indoor Rock Climbing', 'Other'].includes('${tracker.type}')) {
                        curDist = 0.0;
                    } else if ('${tracker.type}' === 'Mountain Biking') {
                        curDist = currentSegMins / (5.0 / speedMult); 
                    } else if ('${tracker.type}' === 'Outdoor Skating' || '${tracker.type}' === 'Indoor Skating' || '${tracker.type}' === 'Skating') {
                        curDist = currentSegMins / (7.0 / speedMult);
                    } else if (strideInches > 0 && cadence > 0) {
                        curDist = (strideInches * curSteps) / 63360.0;
                    }
                    let totalDist = accDistMiles + curDist;
                    document.getElementById('liveDist').innerText = totalDist.toFixed(2);
                    
                    let curEffort = 0;
                    if (currentSegMins >= 0.01) {
                        curEffort = currentSegMins * intensityMult * speedMult * sleepScore * ageFactor * genderFactor * tempFactor * weightFactor * moodEffortFactor;
                    }
                    let totalEffort = Math.round(accEffort + curEffort);
                    document.getElementById('liveEffort').innerText = totalEffort;
                }
                updateTimer();
                if(isRunning) setInterval(updateTimer, 1000);
            </script>
        """
    } else if (tStatus == "PENDING_SAVE") {
        def pListNames = []
        (tracker.participants ?: [pId.toString()]).each { pid -> pListNames << (settings["userName_${pid}"] ?: "Profile ${pid}") }
        def pString = pListNames.join(", ")
        
        def currentTypeDisplay = tracker.displayType ?: tracker.type
        def isNoDist = (tracker.estDist <= 0.0) && (tracker.type in ["Weight Lifting", "Disk Golf", "Pickleball", "Kayaking", "YMCA Fitness Class", "Indoor Rock Climbing", "Other"])
        def distPaceHtml = isNoDist ? "" : """
            <label>Distance (mi/km)</label>
            <input type='number' step='0.01' name='dist' id='dist' class='form-control' value='${tracker.estDist}' onchange='calcPace()' onkeyup='calcPace()'>
            <label>Pace</label>
            <input type='text' name='pace' id='pace' class='form-control' value='${tracker.estPace}' required>
        """
        
        trackerHtml = """
            <div class='live-panel' style='border-left: 5px solid var(--success);'>
                <p><b>✅ Workout Complete!</b> Edit your metrics below if needed.</p>
                <div style='font-size:12px; color:#555; margin-bottom:10px;'><b>Saving to:</b> ${pString}</div>
                <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                    <input type='hidden' name='action' value='save'>
                    
                    <div style='margin-bottom:15px; font-weight:bold; color:var(--dark);'>Activity: ${currentTypeDisplay}</div>
                    
                    ${distPaceHtml}
                    
                    <label>Duration (mins)</label>
                    <input type='number' name='dur' id='dur' class='form-control' value='${tracker.estDur}' required onchange='calcPace()' onkeyup='calcPace()'>
                    
                    <label>Perceived Effort</label>
                    <select name='perceivedEffort' class='form-control' required>
                        <option value='Easy'>Easy (0.8x)</option>
                        <option value='Moderate' selected>Moderate (1.0x)</option>
                        <option value='Hard'>Hard (1.2x)</option>
                        <option value='Extreme'>Extreme (1.5x)</option>
                    </select>

                    <label>Post-Workout Mood (Syncs to House)</label>
                    <select name='workoutMood' class='form-control'>
                        ${moodOptionsHtml}
                    </select>
                    
                    <div class='btn-group' style='margin-top:15px;'>
                        <button type='submit' class='btn btn-primary'>💾 Save</button>
                        <button type='submit' formaction='?access_token=${state.accessToken}&profileId=${pId}&action=discard' class='btn btn-danger'>❌ Discard</button>
                    </div>
                </form>
            </div>
            <script>
                function calcPace() {
                    let distEl = document.getElementById('dist');
                    let durEl = document.getElementById('dur');
                    let paceEl = document.getElementById('pace');
                    if(distEl && durEl && paceEl) {
                        let d = parseFloat(distEl.value);
                        let t = parseInt(durEl.value);
                        if(d > 0 && t > 0) {
                            let p = t / d;
                            let pm = Math.floor(p);
                            let ps = Math.round((p - pm) * 60);
                            if(ps == 60) { pm++; ps = 0; }
                            paceEl.value = pm + ":" + (ps < 10 ? "0" : "") + ps + " /mi";
                        }
                    }
                }
            </script>
        """
    }
    
    def learnedMetrics = [:]
    def userLogs = state.weeklyLog?."${pId}" ?: []
    userLogs.each { log ->
        def t = log.type
        if (t && !t.contains("+")) { 
            if (!learnedMetrics[t]) learnedMetrics[t] = [effT: 0, c: 0, distT: 0.0, durForPaceT: 0.0, pC: 0]
            learnedMetrics[t].c++
            learnedMetrics[t].effT += (log.effort ?: 0)
            
            def dst = log.dist ? log.dist.toString().toDouble() : 0.0
            def dur = log.dur ? log.dur.toString().toInteger() : 0
            if (dst > 0 && dur > 0) {
                learnedMetrics[t].distT += dst
                learnedMetrics[t].durForPaceT += dur
                learnedMetrics[t].pC++
            }
        }
    }
    
    def learnedRows = ""
    if (learnedMetrics.size() > 0) {
        learnedMetrics.each { t, d ->
            def avgEff = Math.round(d.effT / d.c)
            def avgDist = d.pC > 0 ? "${Math.round((d.distT / d.pC) * 100) / 100.0} mi" : "--"
            def avgPace = "--"
            if (d.pC > 0) {
                def paceDec = d.durForPaceT / d.distT
                def pM = Math.floor(paceDec).toInteger()
                def pS = Math.round((paceDec - pM) * 60).toInteger()
                if (pS == 60) { pM++; pS = 0 }
                avgPace = "${pM}:${pS.toString().padLeft(2, '0')} /mi"
            }
            learnedRows += "<tr><td class='dash-hl'>${t}</td><td>${avgDist}</td><td>${avgPace}</td><td>⚡ ${avgEff}</td></tr>"
        }
    } else {
        learnedRows = "<tr><td colspan='4' style='text-align:center; color:#666;'>No single-activity workout data available to learn averages.</td></tr>"
    }
    
    def learnedHtmlCard = """
    <div class='card' style='margin-bottom:20px;'>
        <h3>🧠 Learned Averages (Recent)</h3>
        <p style='font-size:12px; color:#64748b; margin-top:0;'>The app uses these baselines to dynamically score your effort based on pace improvements.</p>
        <table class="dash-table" style="margin-bottom:0;">
            <thead>
                <tr><th style='background-color:#6366f1; color:white; text-align:left; padding-left:15px;'>Activity</th><th style='background-color:#6366f1; color:white;'>Avg Dist</th><th style='background-color:#6366f1; color:white;'>Avg Pace</th><th style='background-color:#6366f1; color:white;'>Avg Effort</th></tr>
            </thead>
            <tbody>
                ${learnedRows}
            </tbody>
        </table>
    </div>
    """
    
    def lbData = []
    for(int i=1; i<=maxProfiles; i++) {
        def wSteps = calculateWeeklySteps(i.toString())
        def ySteps = state.yearlySteps?."${i}" ?: 0
        lbData << [name: settings["userName_${i}"] ?: "Profile ${i}", wSteps: wSteps, ySteps: ySteps]
    }
    lbData.sort { -it.wSteps }
    def webLbHTML = ""
    lbData.each { r ->
        webLbHTML += "<tr><td><b>${r.name}</b></td><td>${r.wSteps}</td><td>${r.ySteps}</td></tr>"
    }

    def prs = state.prs?."${pId}" ?: [:]
    def f1m = prs["fastest_1m"] ? "${prs["fastest_1m"].val} (${prs["fastest_1m"].date})" : "--"
    def f5k = prs["fastest_5k"] ? "${prs["fastest_5k"].val} (${prs["fastest_5k"].date})" : "--"
    def lRun = prs["longest_Running"] ? "${prs["longest_Running"].val} (${prs["longest_Running"].date})" : "--"
    def lWalk = prs["longest_Walking"] ? "${prs["longest_Walking"].val} (${prs["longest_Walking"].date})" : "--"

    // Section 5: History
    def logList = state.weeklyLog?."${pId}" ?: []
    def historyHtml = ""
    if (logList.size() > 0) {
        logList.take(20).each { entry ->
            def safeType = entry.type ?: "Unknown"
            def distValD = entry.dist ? entry.dist.toString().toDouble() : 0.0
            def distText = distValD > 0 ? "${entry.dist} mi" : ""
            def pText = (entry.pace && entry.pace != "N/A" && distValD > 0) ? "(${entry.pace})" : ""
            def effortText = entry.effort ? "⚡ Effort: ${entry.effort}" : ""
            def dogText = (entry.dogs && entry.dogs.size() > 0) ? "<br><span style='font-size:11px; color:#8b4513;'>🐾 ${entry.dogs.join(", ")}</span>" : ""
            def wId = entry.id ?: entry.timestamp.toString()
            def effStrVal = entry.effStr ?: "Moderate"
            historyHtml += """
                <tr>
                    <td><b>${safeType}</b><br><span style='font-size:11px; color:#666;'>${entry.date}</span>${dogText}</td>
                    <td>${entry.dur}m ${effortText}<br><span style='font-size:11px; color:#666;'>${distText} ${pText}</span></td>
                    <td style='text-align:right;'>
                        <div style='display:flex; justify-content:flex-end; gap:5px;'>
                            <button type='button' class='btn-info btn-sm' onclick='openEditModal("${wId}", "${safeType}", "${distValD}", "${entry.dur}", "${effStrVal}")'>✏️</button>
                            <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}' style='margin:0;'>
                                <input type='hidden' name='action' value='delete'>
                                <input type='hidden' name='workoutId' value='${wId}'>
                                <button type='submit' class='btn-delete btn-sm'>🗑️</button>
                            </form>
                        </div>
                    </td>
                </tr>
            """
        }
    } else {
        historyHtml = "<tr><td colspan='3' style='text-align:center; color:#666;'>No workouts logged yet.</td></tr>"
    }

    def html = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset='UTF-8'>
        <meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0'>
        <title>Fitness Tracker</title>
        <style>
            :root {
                --primary: #007bff;
                --primary-hover: #0056b3;
                --dark: #1e293b;
                --light: #f1f5f9;
                --bg: #e2e8f0;
                --card-bg: #ffffff;
                --text: #334155;
                --border: #cbd5e1;
                --success: #10b981;
                --warning: #f59e0b;
                --danger: #ef4444;
                --info: #0ea5e9;
            }
            body { margin: 0; padding: 0; background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
            * { box-sizing: border-box; }
            
            .top-nav { background: var(--dark); display: flex; flex-direction: column; }
            .app-title { padding: 15px; font-size: 18px; font-weight: bold; color: white; display: flex; align-items: center; justify-content: space-between; }
            .profile-tabs { display: flex; overflow-x: auto; -webkit-overflow-scrolling: touch; }
            .profile-tabs::-webkit-scrollbar { display: none; }
            .profile-tab { padding: 12px 20px; color: #94a3b8; text-decoration: none; font-weight: 600; font-size: 14px; white-space: nowrap; border-bottom: 3px solid transparent; }
            .profile-tab.active { color: white; border-bottom-color: var(--primary); background: rgba(255,255,255,0.05); }
            
            .app-body { display: flex; flex: 1; overflow: hidden; flex-direction: row; }
            
            .sidebar { width: 220px; background: var(--card-bg); border-right: 1px solid var(--border); display: flex; flex-direction: column; overflow-y: auto; z-index: 10; }
            .menu-item { padding: 16px 20px; cursor: pointer; border-bottom: 1px solid var(--light); font-weight: 600; color: #64748b; font-size: 14px; transition: all 0.2s; display: flex; align-items: center; gap: 10px; }
            .menu-item:hover { background: var(--light); color: var(--text); }
            .menu-item.active { background: var(--primary); color: white; border-color: var(--primary); }
            
            .main-content { flex: 1; padding: 20px; overflow-y: auto; -webkit-overflow-scrolling: touch; }
            .view-section { display: none; max-width: 800px; margin: 0 auto; animation: fadeIn 0.3s; }
            .view-section.active { display: block; }
            @keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
            @keyframes flash { from { opacity: 1; } to { opacity: 0.3; } }
            
            .card { background: var(--card-bg); border-radius: 10px; padding: 20px; margin-bottom: 20px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06); }
            h3 { margin-top: 0; font-size: 17px; border-bottom: 1px solid var(--light); padding-bottom: 12px; color: var(--dark); display: flex; align-items: center; gap: 8px; }
            label { font-size: 12px; font-weight: bold; color: #64748b; margin-top: 15px; display: block; text-transform: uppercase; letter-spacing: 0.5px; }
            .form-control { width: 100%; padding: 12px; margin-top: 6px; border: 1px solid var(--border); border-radius: 6px; font-size: 15px; background: #fff; color: var(--text); transition: border-color 0.2s; }
            .form-control:focus { outline: none; border-color: var(--primary); }
            
            .input-group { background: var(--light); padding: 12px; border-radius: 6px; margin-top: 12px; }
            .input-group label:first-child { margin-top: 0; margin-bottom: 8px; }
            .checkbox-group { display: flex; flex-wrap: wrap; gap: 12px; }
            .chk-label { display: flex; align-items: center; font-size: 14px; font-weight: normal; color: var(--text); margin-top: 0; text-transform: none; letter-spacing: normal; cursor: pointer; }
            .chk-label input { width: 18px; height: 18px; margin-right: 6px; accent-color: var(--primary); }
            
            .btn { width: 100%; padding: 14px; border: none; border-radius: 6px; color: white; font-size: 15px; font-weight: bold; cursor: pointer; margin-top: 20px; transition: opacity 0.2s; }
            .btn:active { opacity: 0.8; }
            .btn-primary { background-color: var(--primary); }
            .btn-success { background-color: var(--success); }
            .btn-warning { background-color: var(--warning); color: #fff; }
            .btn-danger { background-color: var(--danger); }
            .btn-info { background-color: var(--info); }
            .btn-group { display: flex; gap: 10px; }
            .btn-group .btn { margin-top: 0; }
            .btn-sm { font-size: 12px; padding: 6px 12px; border: none; border-radius: 4px; cursor: pointer; color: #fff; font-weight: bold; }
            .btn-delete { background: var(--danger); }
            .btn-info.btn-sm { background: var(--info); }
            
            .chart-container { display: flex; align-items: flex-end; justify-content: space-between; height: 120px; padding: 10px 5px 0 5px; border-bottom: 2px solid var(--light); margin-bottom: 5px; }
            .chart-col { display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; width: 13%; }
            .chart-dist { font-size:10px; font-weight:bold; text-align:center; margin-bottom:4px; line-height:1; min-height:14px; }
            .chart-bar { width: 80%; border-radius: 4px 4px 0 0; min-height: 2px; position:relative; box-shadow: 0 -2px 4px rgba(0,0,0,0.1); }
            .chart-day { font-size: 10px; margin-top: 6px; color: #64748b; font-weight:bold; text-transform: uppercase; }
            
            .live-panel { background: var(--light); padding: 20px; border-radius: 8px; text-align: center; border: 1px solid var(--border); }
            .speed-control { display:flex; justify-content:center; align-items:center; gap:15px; margin: 20px 0; background:#fff; padding:12px; border-radius:8px; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
            .speed-readout { font-size:16px; font-weight:bold; color:var(--dark); min-width:110px; }
            .speed-readout-offline { margin: 15px 0; background:#fff; padding:12px; border-radius:8px; font-size:16px; font-weight:bold; border: 1px solid var(--border); }
            .live-metrics { display:flex; justify-content:space-around; font-size:13px; margin:15px 0; color:#64748b; background:#fff; padding:12px; border-radius:8px; border: 1px solid var(--border); font-weight: 500; }
            .live-metrics span { font-weight:bold; color:var(--dark); font-size: 15px; display: block; margin-top: 2px; }
            .timer { font-size: 46px; font-weight: bold; font-family: monospace; color: var(--dark); margin: 20px 0; text-shadow: 1px 1px 0px rgba(0,0,0,0.1); }
            
            .dash-table, .hist-table { width: 100%; border-collapse: collapse; font-size: 13px; }
            .dash-table { margin-bottom: 20px; border: 1px solid var(--border); border-radius: 6px; overflow: hidden; }
            .dash-table th, .dash-table td { padding: 12px; text-align: center; border-bottom: 1px solid var(--light); }
            .dash-table th { background-color: var(--light); color: var(--dark); font-size:12px; text-transform: uppercase; font-weight: bold; }
            .dash-hl { font-weight:bold; text-align: left !important; background: #f8fafc; width: 40%; color: var(--dark); }
            .dash-val { text-align: left !important; color: var(--text); }
            
            .hist-table th, .hist-table td { padding: 12px 8px; border-bottom: 1px solid var(--light); text-align: left; }
            .legend { font-size: 12px; color: #64748b; background: var(--light); padding: 12px; border-radius: 6px; margin-bottom: 15px; border: 1px solid var(--border); line-height: 1.5; }
            
            .modal { display: none; position: fixed; z-index: 100; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); align-items: center; justify-content: center; }
            .modal-content { background-color: #fff; padding: 25px; border-radius: 8px; width: 90%; max-width: 450px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); }
            
            @media (max-width: 768px) {
                .app-body { flex-direction: column; }
                .sidebar { width: 100%; flex-direction: row; overflow-x: auto; border-right: none; border-bottom: 1px solid var(--border); z-index: 10; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
                .menu-item { white-space: nowrap; border-bottom: none; border-right: 1px solid var(--light); flex: 1; justify-content: center; padding: 14px 15px; }
                .menu-item.active { border-bottom: 3px solid var(--primary); background: transparent; color: var(--primary); }
                .main-content { padding: 15px; }
            }
        </style>
    </head>
    <body>
        <div class='top-nav'>
            <div class='app-title'>
                <span>🏃 Advanced Fitness Tracker</span>
                ${liftingLinkHtml}
            </div>
            ${tabsHtml}
        </div>
        
        <div class='app-body'>
            <div class='sidebar'>
                <div id='menu-dashboard' class='menu-item' onclick='switchView("dashboard")'>📊 Dashboard</div>
                <div id='menu-live' class='menu-item' onclick='switchView("live")'>⏱️ Live Tracker</div>
                <div id='menu-log' class='menu-item' onclick='switchView("log")'>📝 Logging</div>
                <div id='menu-records' class='menu-item' onclick='switchView("records")'>🏆 Records</div>
                <div id='menu-history' class='menu-item' onclick='switchView("history")'>📋 History</div>
            </div>
            
            <div class='main-content'>
                <!-- DASHBOARD VIEW -->
                <div id='view-dashboard' class='view-section'>
                    ${fuelingHtml}
                    ${dashboardMoodHtml}
                    ${moodMatrixHtml}
                    ${weightChartHtml}
                    <div class='card' style='padding-bottom: 5px;'>
                        <h3>📊 7-Day Performance Trends</h3>
                        ${effChartHtml}
                        ${stepChartHtml}
                        ${distChartHtml}
                    </div>
                    <div class='card'>
                        <h3>🐾 Dog Mileage Tracker</h3>
                        <table class="dash-table">
                            <thead>
                                <tr><th style='background-color:#8b4513; color:white;'>Dog</th><th style='background-color:#8b4513; color:white;'>7-Day</th><th style='background-color:#8b4513; color:white;'>Yearly</th></tr>
                            </thead>
                            <tbody>
                                ${dogMilesHtml}
                            </tbody>
                        </table>
                    </div>
                </div>

                <!-- LIVE TRACKER VIEW -->
                <div id='view-live' class='view-section'>
                    <div class='card'>
                        <h3>⏱️ Live Tracker</h3>
                        ${trackerHtml}
                    </div>
                </div>

                <!-- LOGGING VIEW -->
                <div id='view-log' class='view-section'>
                    
                    <div class='card'>
                        <h3>⚖️ Log Body Weight</h3>
                        <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                            <input type='hidden' name='action' value='logWeight'>
                            <div style='display:flex; gap:10px;'>
                                <input type='number' step='0.1' name='weight' class='form-control' placeholder='E.g., 175.5' required>
                                <button type='submit' class='btn btn-info' style='margin-top:6px; width:auto;'>⚖️ Save</button>
                            </div>
                        </form>
                    </div>

                    <div class='card'>
                        <h3>📝 Manual Log</h3>
                        <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                            <input type='hidden' name='action' value='manualLog'>
                            <label>Workout Type</label>
                            <select name='type' id='manualType' class='form-control' required onchange='checkManualType()'>
                                <option value=''>Select Type...</option>
                                <option value='Hiking'>Hiking</option>
                                <option value='Kayaking'>Kayaking</option>
                                <option value='Walking'>Walking</option>
                                <option value='Treadmill Walking'>Treadmill Walking</option>
                                <option value='Dog Walking'>Dog Walking</option>
                                <option value='Weight Lifting'>Weight Lifting</option>
                                <option value='Running'>Running</option>
                                <option value='Treadmill Running'>Treadmill Running</option>
                                <option value='Mountain Biking'>Mountain Biking</option>
                                <option value='Peloton'>Peloton</option>
                                <option value='Spin Class'>Spin Class</option>
                                <option value='Outdoor Skating'>Outdoor Skating</option>
                                <option value='Indoor Skating'>Indoor Skating</option>
                                <option value='Indoor Rock Climbing'>Indoor Rock Climbing</option>
                                <option value='YMCA Fitness Class'>YMCA Fitness Class</option>
                                <option value='Disk Golf'>Disk Golf</option>
                                <option value='Pickleball'>Pickleball</option>
                                <option value='Other'>Other</option>
                            </select>
                            
                            ${participantsHtml}
                            ${manualDogSelectorHtml}
                            
                            <div style='display:flex; gap:10px;'>
                                <div style='flex:1;' id='manualDistGroup'>
                                    <label>Distance (mi)</label>
                                    <input type='number' step='0.01' name='dist' class='form-control'>
                                </div>
                                <div style='flex:1;'>
                                    <label>Duration (min)</label>
                                    <input type='number' name='dur' class='form-control' required>
                                </div>
                            </div>
                            
                            <label>Perceived Effort</label>
                            <select name='perceivedEffort' class='form-control' required>
                                <option value='Easy'>Easy (0.8x)</option>
                                <option value='Moderate' selected>Moderate (1.0x)</option>
                                <option value='Hard'>Hard (1.2x)</option>
                                <option value='Extreme'>Extreme (1.5x)</option>
                            </select>

                            <label>Post-Workout Mood (Syncs to House)</label>
                            <select name='workoutMood' class='form-control'>
                                ${moodOptionsHtml}
                            </select>
                            
                            <button type='submit' class='btn btn-success'>💾 Save Entry</button>
                        </form>
                        <script>
                            function checkManualType() {
                                let t = document.getElementById('manualType').value;
                                let noDist = ["Weight Lifting", "Disk Golf", "Pickleball", "Kayaking", "YMCA Fitness Class", "Indoor Rock Climbing", "Other"].includes(t);
                                document.getElementById('manualDistGroup').style.display = noDist ? 'none' : 'block';
                                
                                let dogOk = ["Dog Walking", "Hiking", "Walking", "Running", "Mountain Biking"].includes(t);
                                document.getElementById('manualDogSelector').style.display = dogOk ? 'block' : 'none';
                            }
                        </script>
                    </div>
                    
                    <div class='card'>
                        <h3>👟 Log Daily Steps (End of Day)</h3>
                        <p style='font-size:13px; color:#64748b; margin-top:0; line-height:1.4;'>Sync end of day totals from your wearable. Automatically prevents double-counting against today's app tracked workouts.</p>
                        <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                            <input type='hidden' name='action' value='logSteps'>
                            <div style='display:flex; gap:10px;'>
                                <input type='number' name='deviceSteps' class='form-control' placeholder='E.g., 10500' required>
                                <button type='submit' class='btn btn-info' style='margin-top:6px; width:auto;'>📊 Sync</button>
                            </div>
                        </form>
                    </div>
                </div>

                <!-- RECORDS VIEW -->
                <div id='view-records' class='view-section'>
                    ${learnedHtmlCard}
                    
                    <div class='card'>
                        <h3>🏆 Leaderboard & PRs</h3>
                        <table class="dash-table" style="margin-bottom:20px;">
                            <thead>
                                <tr><th colspan='3' style='background-color:#ffc107; color: var(--dark);'>👑 Weekly Steps Leaderboard</th></tr>
                                <tr><th>Participant</th><th>7-Day</th><th>Yearly</th></tr>
                            </thead>
                            <tbody>
                                ${webLbHTML}
                            </tbody>
                        </table>
                        <table class="dash-table">
                            <thead><tr><th colspan='2' style='background-color:var(--info); color:white; text-align:left; padding-left:15px;'>🏅 ${uName} - PRs</th></tr></thead>
                            <tbody>
                                <tr><td class="dash-hl">Fastest 1M</td><td class="dash-val">${f1m}</td></tr>
                                <tr><td class="dash-hl">Fastest 5K</td><td class="dash-val">${f5k}</td></tr>
                                <tr><td class="dash-hl">Longest Run</td><td class="dash-val">${lRun}</td></tr>
                                <tr><td class="dash-hl">Longest Walk</td><td class="dash-val">${lWalk}</td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>

                <!-- HISTORY VIEW -->
                <div id='view-history' class='view-section'>
                    <div class='card'>
                        <h3>📋 Recent History</h3>
                        <div class='legend'>
                            <b>⚡ Effort Score Guide (Smart Pace & Weight Enabled):</b><br>
                            The tracker dynamically learns your average pace per activity and automatically scales your effort output using your most recently logged body weight.<br>
                            • <b>0-25:</b> Light Recovery / Gentle movement<br>
                            • <b>26-50:</b> Moderate Steady-State (e.g., 44 = Solid workout session)<br>
                            • <b>51-80:</b> High Intensity / Heavy Strain<br>
                            • <b>81+:</b> Max Exertion
                        </div>
                        <table class='hist-table'>
                            <tbody>
                                ${historyHtml}
                            </tbody>
                        </table>
                    </div>
                </div>

            </div>
        </div>
        
        <!-- EDIT MODAL -->
        <div id='editModal' class='modal'>
            <div class='modal-content'>
                <h3 style='margin-top:0;'>✏️ Edit Workout</h3>
                <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                    <input type='hidden' name='action' value='edit'>
                    <input type='hidden' name='workoutId' id='editWorkoutId'>
                    
                    <label>Workout Type</label>
                    <select name='type' id='editType' class='form-control' required>
                        <option value='Hiking'>Hiking</option>
                        <option value='Kayaking'>Kayaking</option>
                        <option value='Walking'>Walking</option>
                        <option value='Treadmill Walking'>Treadmill Walking</option>
                        <option value='Dog Walking'>Dog Walking</option>
                        <option value='Weight Lifting'>Weight Lifting</option>
                        <option value='Running'>Running</option>
                        <option value='Treadmill Running'>Treadmill Running</option>
                        <option value='Mountain Biking'>Mountain Biking</option>
                        <option value='Peloton'>Peloton</option>
                        <option value='Spin Class'>Spin Class</option>
                        <option value='Outdoor Skating'>Outdoor Skating</option>
                        <option value='Indoor Skating'>Indoor Skating</option>
                        <option value='Indoor Rock Climbing'>Indoor Rock Climbing</option>
                        <option value='YMCA Fitness Class'>YMCA Fitness Class</option>
                        <option value='Disk Golf'>Disk Golf</option>
                        <option value='Pickleball'>Pickleball</option>
                        <option value='Other'>Other</option>
                    </select>
                    
                    <label>Distance (mi)</label>
                    <input type='number' step='0.01' name='dist' id='editDist' class='form-control'>
                    
                    <label>Duration (mins)</label>
                    <input type='number' name='dur' id='editDur' class='form-control' required>
                    
                    <label>Perceived Effort</label>
                    <select name='editEffort' id='editEffort' class='form-control' required>
                        <option value='Easy'>Easy (0.8x)</option>
                        <option value='Moderate' selected>Moderate (1.0x)</option>
                        <option value='Hard'>Hard (1.2x)</option>
                        <option value='Extreme'>Extreme (1.5x)</option>
                    </select>
                    
                    <div class='btn-group' style='margin-top:20px;'>
                        <button type='submit' class='btn btn-success'>💾 Save Changes</button>
                        <button type='button' class='btn btn-danger' onclick='closeEditModal()'>❌ Cancel</button>
                    </div>
                </form>
            </div>
        </div>
        
        <script>
            function switchView(viewId) {
                // Hide all sections
                document.querySelectorAll('.view-section').forEach(el => el.classList.remove('active'));
                document.querySelectorAll('.menu-item').forEach(el => el.classList.remove('active'));
                
                // Show target
                let targetView = document.getElementById('view-' + viewId);
                let targetMenu = document.getElementById('menu-' + viewId);
                
                if (targetView) targetView.classList.add('active');
                if (targetMenu) targetMenu.classList.add('active');
                
                // Save state
                localStorage.setItem('aft_last_view', viewId);
            }
            
            function openEditModal(wId, wType, wDist, wDur, wEffStr) {
                document.getElementById('editWorkoutId').value = wId;
                document.getElementById('editType').value = wType;
                document.getElementById('editDist').value = wDist;
                document.getElementById('editDur').value = wDur;
                
                let effSelect = document.getElementById('editEffort');
                for(let i=0; i < effSelect.options.length; i++) {
                    if(effSelect.options[i].value === wEffStr) {
                        effSelect.selectedIndex = i;
                        break;
                    }
                }
                
                document.getElementById('editModal').style.display = 'flex';
            }
            
            function closeEditModal() {
                document.getElementById('editModal').style.display = 'none';
            }
            
            document.addEventListener("DOMContentLoaded", function() {
                let isTrackerActive = ${tStatus != "IDLE"};
                let savedView = localStorage.getItem('aft_last_view') || 'dashboard';
                
                // Force open Live Tracker if there's an active/pending workout
                if (isTrackerActive) {
                    switchView('live');
                } else {
                    switchView(savedView);
                }
            });
        </script>
    </body>
    </html>
    """
    
    render contentType: "text/html", data: html
}
