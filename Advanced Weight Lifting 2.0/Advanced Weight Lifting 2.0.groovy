/**
 * Advanced Weight Lifting 2.0
 * 
 */
definition(
    name: "Advanced Weight Lifting 2.0",
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
// LOCAL EXERCISE DICTIONARY (THE "AI" KNOWLEDGE BASE)
// ==============================================================================
def getExerciseDB() {
    return [
        // Lower Body
        "Barbell Back Squat":  [cat: "Lower", muscle: "Quads", type: "Compound", inc: 10, defSets: 3, defReps: 5,  defWeight: 95, isBarbell: true],
        "Hack Squat":          [cat: "Lower", muscle: "Quads", type: "Compound", inc: 10, defSets: 3, defReps: 8,  defWeight: 90, isBarbell: false],
        "Leg Press":           [cat: "Lower", muscle: "Quads", type: "Compound", inc: 10, defSets: 3, defReps: 8,  defWeight: 135, isBarbell: false],
        "Goblet Squat":        [cat: "Lower", muscle: "Quads", type: "Accessory", inc: 5, defSets: 3, defReps: 10, defWeight: 35, isBarbell: false],
        "Leg Extension":       [cat: "Lower", muscle: "Quads", type: "Accessory",inc: 5,  defSets: 3, defReps: 15, defWeight: 50, isBarbell: false],
        "Barbell Deadlift":    [cat: "Lower", muscle: "Hamstrings", type: "Compound", inc: 10, defSets: 3, defReps: 5,  defWeight: 135, isBarbell: true],
        "Romanian Deadlift":   [cat: "Lower", muscle: "Hamstrings", type: "Compound", inc: 10, defSets: 3, defReps: 8,  defWeight: 95, isBarbell: true],
        "DB Romanian Deadlift":[cat: "Lower", muscle: "Hamstrings", type: "Accessory", inc: 5,  defSets: 3, defReps: 10, defWeight: 40, isBarbell: false],
        "Leg Curl":            [cat: "Lower", muscle: "Hamstrings", type: "Accessory",inc: 5,  defSets: 3, defReps: 15, defWeight: 50, isBarbell: false],
        "Calf Raise":          [cat: "Lower", muscle: "Calves", type: "Accessory",inc: 10, defSets: 4, defReps: 15, defWeight: 100, isBarbell: false],
        
        // Upper Body
        "Barbell Bench Press": [cat: "Upper", muscle: "Chest", type: "Compound", inc: 5,  defSets: 3, defReps: 8,  defWeight: 95, isBarbell: true],
        "Dumbbell Bench Press":[cat: "Upper", muscle: "Chest", type: "Compound", inc: 5,  defSets: 3, defReps: 10, defWeight: 30, isBarbell: false],
        "Overhead Press":      [cat: "Upper", muscle: "Shoulders", type: "Compound", inc: 5,  defSets: 3, defReps: 8,  defWeight: 65, isBarbell: true],
        "DB Shoulder Press":   [cat: "Upper", muscle: "Shoulders", type: "Compound", inc: 5,  defSets: 3, defReps: 10, defWeight: 20, isBarbell: false],
        "Lateral Raise":       [cat: "Upper", muscle: "Shoulders", type: "Accessory",inc: 2.5,defSets: 3, defReps: 15, defWeight: 15, isBarbell: false],
        "Barbell Row":         [cat: "Upper", muscle: "Back", type: "Compound", inc: 5,  defSets: 3, defReps: 8,  defWeight: 95, isBarbell: true],
        "Dumbbell Row":        [cat: "Upper", muscle: "Back", type: "Compound", inc: 5,  defSets: 3, defReps: 10, defWeight: 30, isBarbell: false],
        "Pull-ups / Pulldown": [cat: "Upper", muscle: "Back", type: "Compound", inc: 5,  defSets: 3, defReps: 10, defWeight: 100, isBarbell: false],
        "Dumbbell Bicep Curl": [cat: "Upper", muscle: "Biceps", type: "Accessory",inc: 2.5,defSets: 3, defReps: 12, defWeight: 20, isBarbell: false],
        "Tricep Pushdown":     [cat: "Upper", muscle: "Triceps", type: "Accessory",inc: 5,  defSets: 3, defReps: 12, defWeight: 30, isBarbell: false]
    ]
}

def getRoutines() {
    return [
        "Full Body": [
            ["Barbell Back Squat", "Hack Squat", "Leg Press"], 
            ["Barbell Bench Press", "Dumbbell Bench Press", "Overhead Press"], 
            ["Barbell Row", "Dumbbell Row", "Pull-ups / Pulldown"], 
            ["Barbell Deadlift", "Romanian Deadlift", "DB Romanian Deadlift"], 
            ["Dumbbell Bicep Curl", "Tricep Pushdown", "Lateral Raise", "Calf Raise"],
            ["Leg Extension", "Leg Curl", "DB Shoulder Press"],
            ["Goblet Squat", "Calf Raise", "Dumbbell Bicep Curl"],
            ["Romanian Deadlift", "Tricep Pushdown", "Lateral Raise"]
        ],
        "Upper Body": [
            ["Barbell Bench Press", "Dumbbell Bench Press"],
            ["Barbell Row", "Dumbbell Row"],
            ["Overhead Press", "DB Shoulder Press"],
            ["Pull-ups / Pulldown"],
            ["Lateral Raise", "Dumbbell Bicep Curl"],
            ["Tricep Pushdown"],
            ["Barbell Bench Press", "Overhead Press"],
            ["Barbell Row", "Tricep Pushdown"]
        ],
        "Lower Body": [
            ["Barbell Back Squat", "Hack Squat", "Leg Press"],
            ["Barbell Deadlift", "Romanian Deadlift"],
            ["Leg Extension", "Goblet Squat"],
            ["Leg Curl", "DB Romanian Deadlift"],
            ["Calf Raise"],
            ["Barbell Back Squat", "Leg Curl"],
            ["Leg Press", "Leg Extension"],
            ["Romanian Deadlift", "Calf Raise"]
        ]
    ]
}

// ==============================================================================
// HUB CONFIGURATION UI
// ==============================================================================
def mainPage() {
    ensureStateMaps()

    dynamicPage(name: "mainPage", title: "Advanced Weight Lifting 2.0: Hub Setup", install: true, uninstall: true) {
        
        section("<b>📱 Web App Access & Integrations</b>", hideable: true, hidden: true) {
            if (!state.accessToken) {
                input "btnEnableOAuth", "button", title: "🔐 Generate API Token"
            } else {
                input "btnRevokeOAuth", "button", title: "Revoke API Token"
                def localUri = "${getFullLocalApiServerUrl()}/ui?access_token=${state.accessToken}"
                def cloudUri = "${getApiServerUrl()}/${hubUID}/apps/${app.id}/ui?access_token=${state.accessToken}"
                paragraph "<b>Local Web Dashboard (Home Wi-Fi):</b><br><span style='font-family:monospace; font-size:12px; word-wrap:break-word;'><a href='${localUri}' target='_blank'>${localUri}</a></span>"
                paragraph "<b>Cloud Web Dashboard (YMCA / Cellular):</b><br><span style='font-family:monospace; font-size:12px; word-wrap:break-word;'><a href='${cloudUri}' target='_blank'>${cloudUri}</a></span>"
                
                input "fitnessAppUrl", "text", title: "<b>Link to Fitness Tracker App</b><br>Paste the CLOUD URL of the Fitness Tracker app here.", required: false
            }
        }

        section("<b>💾 Database & Memory Management</b>", hideable: true, hidden: true) {
            input "historyLimit", "enum", title: "<b>Max History Logs to Retain</b><br>Prevents hub bloat by automatically deleting older workout history.", options: ["15", "30", "60", "90"], defaultValue: "30", required: true
            input "btnCleanupDB", "button", title: "🧹 Force Run Garbage Collection (Clean Orphans)"
        }
        
        section("<b>👤 Profile 1 (Shane)</b>", hideable: true, hidden: true) {
            input "userName_1", "text", title: "Name", defaultValue: "Shane"
            input "weightMult_1", "decimal", title: "Baseline Strength Multiplier (1.0 = Default)", defaultValue: 1.0, required: true
            
            input "gymEnv_1", "enum", title: "Gym Environment", options: ["Commercial Gym (A/C)", "Garage / Outdoor (Sensor Linked)"], defaultValue: "Commercial Gym (A/C)", submitOnChange: true
            if (settings["gymEnv_1"] == "Garage / Outdoor (Sensor Linked)") {
                input "tempSensor_1", "capability.temperatureMeasurement", title: "Weather/Temp Sensor (Garage Heat Governor)", required: false
            }
            
            input "sleepScoreVar_1", "hubVariable", title: "Link Sleep Score Variable", required: false
            input "effortScoreVar_1", "hubVariable", title: "Link Effort Score Variable", required: false
            input "weightVar_1", "hubVariable", title: "Link Body Weight Variable (lbs)", required: false
            input "moodVar_1", "hubVariable", title: "Link Mood Variable (String) - For Psychological Auto-Regulation", required: false
            input "liftingSyncVar_1", "hubVariable", title: "Lifting Sync Variable (String) - Sends workouts to Tracker", required: false
            input "btnClear_1", "button", title: "🗑️ Factory Reset Data (Shane)"
        }
        
        section("<b>👤 Profile 2 (Christy)</b>", hideable: true, hidden: true) {
            input "userName_2", "text", title: "Name", defaultValue: "Christy"
            input "weightMult_2", "decimal", title: "Baseline Strength Multiplier (e.g., 0.5 for 50%)", defaultValue: 0.5, required: true
            
            input "gymEnv_2", "enum", title: "Gym Environment", options: ["Commercial Gym (A/C)", "Garage / Outdoor (Sensor Linked)"], defaultValue: "Commercial Gym (A/C)", submitOnChange: true
            if (settings["gymEnv_2"] == "Garage / Outdoor (Sensor Linked)") {
                input "tempSensor_2", "capability.temperatureMeasurement", title: "Weather/Temp Sensor (Garage Heat Governor)", required: false
            }
            
            input "sleepScoreVar_2", "hubVariable", title: "Link Sleep Score Variable", required: false
            input "effortScoreVar_2", "hubVariable", title: "Link Effort Score Variable", required: false
            input "weightVar_2", "hubVariable", title: "Link Body Weight Variable (lbs)", required: false
            input "moodVar_2", "hubVariable", title: "Link Mood Variable (String) - For Psychological Auto-Regulation", required: false
            input "liftingSyncVar_2", "hubVariable", title: "Lifting Sync Variable (String) - Sends workouts to Tracker", required: false
            input "btnClear_2", "button", title: "🗑️ Factory Reset Data (Christy)"
        }
    }
}

def installed() { initialize() }
def updated() { initialize(); runGarbageCollection() }
def initialize() { ensureStateMaps() }

def ensureStateMaps() {
    if (state.liftingHistory == null) state.liftingHistory = [:]
    if (state.activePlan == null) state.activePlan = [:]
    if (state.personalRecords == null) state.personalRecords = [:]
    if (state.workoutLogs == null) state.workoutLogs = [:]
    if (state.muscleRecovery == null) state.muscleRecovery = [:]
}

def appButtonHandler(btn) {
    if (btn == "btnEnableOAuth") {
        try { if (!state.accessToken) createAccessToken() } catch (e) {}
    } else if (btn == "btnRevokeOAuth") {
        state.remove("accessToken")
    } else if (btn == "btnCleanupDB") {
        runGarbageCollection()
    } else if (btn == "btnClear_1") {
        state.liftingHistory.remove("1"); state.activePlan.remove("1"); state.personalRecords.remove("1"); state.workoutLogs.remove("1"); state.muscleRecovery?.remove("1")
    } else if (btn == "btnClear_2") {
        state.liftingHistory.remove("2"); state.activePlan.remove("2"); state.personalRecords.remove("2"); state.workoutLogs.remove("2"); state.muscleRecovery?.remove("2")
    }
}

// ==============================================================================
// MEMORY MANAGEMENT (Deep Copy Override for Hubitat)
// ==============================================================================

def runGarbageCollection() {
    log.info "Advanced Weight Lifting 2.0: Running Garbage Collection..."
    def validExercises = getExerciseDB().keySet()
    
    ["1", "2"].each { pId ->
        def history = state.liftingHistory?."${pId}"
        if (history) {
            def keysToRemove = history.keySet().findAll { !validExercises.contains(it) }
            keysToRemove.each { history.remove(it) }
            state.liftingHistory["${pId}"] = history
        }
        
        def prs = state.personalRecords?."${pId}"
        if (prs) {
            def keysToRemove = prs.keySet().findAll { it != "totalWorkouts" && !validExercises.contains(it) }
            keysToRemove.each { prs.remove(it) }
            
            def wLogs = state.workoutLogs?."${pId}" ?: []
            if (wLogs.size() == 0 && (prs.totalWorkouts ?: 0) > 0) {
                prs.totalWorkouts = 0
            }
            
            state.personalRecords["${pId}"] = prs
        }
    }
    log.info "Advanced Weight Lifting 2.0: Garbage Collection Complete."
}

def forceSaveHistory(pIdStr, newLogsList) {
    def allLogsMap = state.workoutLogs ?: [:]
    def tempMap = [:]
    
    allLogsMap.each { k, v ->
        if (k != pIdStr) {
            tempMap[k] = v
        }
    }
    tempMap[pIdStr] = newLogsList
    
    state.remove("workoutLogs") 
    state.workoutLogs = tempMap 
}

// ==============================================================================
// ADVANCED GENERATION ENGINE (AI & Periodization Logic)
// ==============================================================================

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

def getReadinessModifier(pId) {
    def sleepVar = settings["sleepScoreVar_${pId}"]
    def sleepScore = 85 
    if (sleepVar) {
        def val = getGlobalVar(sleepVar)?.value
        if (val != null && val.toString().isNumber()) sleepScore = val.toInteger()
    }
    
    def effortVar = settings["effortScoreVar_${pId}"]
    def currentEffort = 0
    if (effortVar) {
        def val = getGlobalVar(effortVar)?.value
        if (val != null && val.toString().isNumber()) currentEffort = val.toInteger()
    }
    
    def moodVar = settings["moodVar_${pId}"]
    def moodStr = ""
    if (moodVar) {
        def val = getGlobalVar(moodVar)?.value
        if (val) moodStr = val.toString()
    }
    
    def moodMult = 1.0
    if (moodStr in ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]) moodMult = 1.05
    else if (moodStr in ["😴", "🥱", "🥺", "🤡"]) moodMult = 0.90
    else if (moodStr in ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]) moodMult = 0.85
    else if (moodStr in ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]) moodMult = 0.80
    
    def tempFactor = 1.0
    def gymEnv = settings["gymEnv_${pId}"] ?: "Commercial Gym (A/C)"
    
    if (gymEnv == "Garage / Outdoor (Sensor Linked)") {
        def tSensor = settings["tempSensor_${pId}"]
        if (tSensor) {
            def curT = tSensor.currentValue("temperature")?.toDouble() ?: 70.0
            def rh = tSensor.currentValue("humidity")?.toDouble() ?: 50.0
            def hi = getHeatIndex(curT, rh)
            if (hi >= 100) tempFactor = 0.85
            else if (hi >= 90) tempFactor = 0.90
            else if (hi >= 80) tempFactor = 0.95
        }
    }
    
    def strainIndex = sleepScore > 0 ? (currentEffort / sleepScore.toDouble()) : 0
    
    def baseReadiness = 1.0
    if (strainIndex > 1.2) baseReadiness = 0.70  
    else if (sleepScore >= 85) baseReadiness = 1.05   
    else if (sleepScore >= 65) baseReadiness = 1.0    
    else baseReadiness = 0.84                         
    
    return Math.max(0.60, Math.min(1.15, baseReadiness * moodMult * tempFactor))
}

def calculateCurrentRecovery(pIdStr) {
    def recoveryMap = state.muscleRecovery?."${pIdStr}" ?: [:]
    def currentStatus = [:]
    def nowMs = new Date().time
    // Allows 5 Days for a muscle to heal from 0% back to 100% capacity
    def fullRecoveryMs = 5L * 24L * 60L * 60L * 1000L 
    
    ["Chest", "Back", "Quads", "Hamstrings", "Shoulders", "Biceps", "Triceps", "Calves"].each { muscle ->
        def mData = recoveryMap[muscle]
        if (mData) {
            def elapsed = nowMs - (mData.timestamp ?: nowMs)
            def decayPct = (elapsed / fullRecoveryMs.toDouble()) * 100.0
            def currentFatigue = Math.max(0.0, (mData.lastFatigue ?: 0.0) - decayPct)
            currentStatus[muscle] = Math.min(100.0, Math.max(0.0, 100.0 - currentFatigue))
        } else {
            currentStatus[muscle] = 100.0
        }
    }
    return currentStatus
}

def buildExerciseTargets(exName, exData, participants, blockPhase = "Standard") {
    def targets = [:]
    participants.each { pId ->
        def readiness = getReadinessModifier(pId)
        def uHistory = state.liftingHistory?."${pId}" ?: [:]
        def pastLog = uHistory[exName]
        
        def muscleRecMap = calculateCurrentRecovery(pId)
        def specificMuscleRec = muscleRecMap[exData.muscle] ?: 100.0
        def isRecovering = false
        
        def currentEffort = 0
        def effortVar = settings["effortScoreVar_${pId}"]
        if (effortVar) {
            def val = getGlobalVar(effortVar)?.value
            if (val != null && val.toString().isNumber()) currentEffort = val.toInteger()
        }
        def isPreFatigued = currentEffort > 60
        
        def tSets = exData.defSets
        def tReps = exData.defReps
        def tWeight = exData.defWeight
        def isDeloading = false

        if (!pastLog) {
            def mult = settings["weightMult_${pId}"] != null ? settings["weightMult_${pId}"].toFloat() : (pId.toString() == "2" ? 0.5 : 1.0)
            tWeight = Math.max(5, (Math.round((tWeight * mult) / 5) * 5).toInteger())
        }
        
        if (pastLog) {
            tWeight = pastLog.lastWeight
            
            if ((pastLog.failCount ?: 0) >= 2) {
                tWeight = Math.max(exData.defWeight, (Math.round((tWeight * 0.9) / 5) * 5).toInteger())
                isDeloading = true
            } else if (readiness >= 1.0 && pastLog.lastCompletedAll) {
                tWeight += exData.inc 
            } else if (readiness < 1.0) {
                if (exData.type == "Compound") tWeight = Math.max(exData.defWeight, (Math.round((tWeight * 0.9) / 5) * 5).toInteger())
            }
            
            if (specificMuscleRec <= 49.0 && !isDeloading) { 
                tWeight = Math.max(exData.defWeight, (Math.round((tWeight * 0.70) / 5) * 5).toInteger())
                tSets = Math.max(1, tSets - 1)
                isRecovering = true
            } else if (specificMuscleRec <= 84.0 && !isDeloading) { 
                tWeight = Math.max(exData.defWeight, (Math.round((tWeight * 0.85) / 5) * 5).toInteger())
                isRecovering = true
            }
            
            if (isPreFatigued && exData.type == "Compound" && !isDeloading && !isRecovering) {
                tWeight = Math.max(exData.defWeight, (Math.round((tWeight * 0.9) / 5) * 5).toInteger())
            }
        }
        
        if (blockPhase == "Hypertrophy") {
            tReps = 10
            tSets = 4
            tWeight = Math.max(15, (Math.round((tWeight * 0.85) / 5) * 5).toInteger())
        } else if (blockPhase == "Strength") {
            tReps = 5
            tSets = 4
            if (exData.type == "Compound") {
                tWeight = Math.max(45, (Math.round((tWeight * 1.05) / 5) * 5).toInteger())
            }
        } else if (blockPhase == "Deload") {
            tReps = 8
            tSets = 2
            tWeight = Math.max(15, (Math.round((tWeight * 0.70) / 5) * 5).toInteger())
            isDeloading = true
        } else {
            if (readiness < 0.85 && !isRecovering) tSets = Math.max(1, tSets - 1)
        }
        
        def warmups = []
        if (exData.type == "Compound" && tWeight > 45 && blockPhase != "Deload") {
            warmups << [reps: 8, weight: (Math.round((tWeight * 0.5) / 5) * 5).toInteger()]
            if (isPreFatigued) {
                warmups << [reps: 6, weight: (Math.round((tWeight * 0.6) / 5) * 5).toInteger()] 
            }
            warmups << [reps: 4, weight: (Math.round((tWeight * 0.7) / 5) * 5).toInteger()]
        }
        
        targets[pId.toString()] = [
            sets: tSets, reps: tReps, weight: tWeight, 
            warmups: warmups, readiness: readiness, isDeloading: isDeloading, isRecovering: isRecovering, isBarbell: exData.isBarbell, muscle: exData.muscle
        ]
    }
    return targets
}

def generateWorkout(participants, routineName, jointFlags, exerciseCount, blockPhase = "Standard") {
    def db = getExerciseDB()
    def pools = getRoutines()[routineName] ?: getRoutines()["Full Body"]
    def generatedPlan = []
    def rnd = new java.util.Random()
    
    def selectedPools = pools.clone()
    def targetCount = Math.min(Math.max(3, exerciseCount.toInteger()), selectedPools.size())
    
    for (int i = 0; i < targetCount; i++) {
        def exPool = selectedPools[i]
        def originalExName = exPool[rnd.nextInt(exPool.size())]
        def exName = originalExName
        
        if (jointFlags.knee) {
            if (exName in ["Barbell Back Squat", "Hack Squat"]) exName = "Goblet Squat"
            if (exName == "Leg Extension") exName = "Leg Curl"
        }
        if (jointFlags.back) {
            if (exName == "Barbell Deadlift") exName = "DB Romanian Deadlift"
            if (exName == "Barbell Row") exName = "Pull-ups / Pulldown"
        }
        if (jointFlags.shoulder) {
            if (exName == "Barbell Bench Press") exName = "Dumbbell Bench Press"
            if (exName == "Overhead Press") exName = "Lateral Raise"
        }
        
        if (!db[exName]) exName = originalExName
        def exData = db[exName]
        
        generatedPlan << [
            id: java.util.UUID.randomUUID().toString().take(8),
            name: exName, type: exData.type, cat: exData.cat,
            targets: buildExerciseTargets(exName, exData, participants, blockPhase)
        ]
    }
    
    def planKey = participants.size() > 1 ? "joint" : participants[0].toString()
    state.activePlan[planKey] = [
        routineName: routineName + (blockPhase != "Standard" ? " (${blockPhase})" : ""), 
        participants: participants,
        date: new Date().format("MMM dd"), exercises: generatedPlan
    ]
}

def swapExercise(planKey, exId, newExName) {
    def plan = state.activePlan[planKey]
    if (!plan) return
    def db = getExerciseDB()
    def newExData = db[newExName]
    if (!newExData) return
    
    def updatedExercises = []
    plan.exercises.each { ex ->
        if (ex.id == exId) {
            ex.id = java.util.UUID.randomUUID().toString().take(8) 
            ex.name = newExName; ex.type = newExData.type; ex.cat = newExData.cat
            ex.targets = buildExerciseTargets(newExName, newExData, plan.participants)
        }
        updatedExercises << ex
    }
    plan.exercises = updatedExercises
    state.activePlan[planKey] = plan
}

def completeWorkout(planKey, durationMins, submittedData) {
    def plan = state.activePlan[planKey]
    if (!plan) return
    
    plan.participants.each { pIdStr ->
        def uHistory = state.liftingHistory?."${pIdStr}" ?: [:]
        def prs = state.personalRecords?."${pIdStr}" ?: [:]
        def participantReadiness = 1.0
        
        def workoutSummary = []
        def workoutVolumeMap = [:] 
        
        plan.exercises.each { ex ->
            def target = ex.targets[pIdStr]
            if (!target) return
            participantReadiness = target.readiness
            def muscleGroup = target.muscle ?: "Other"
            
            def setsCompleted = 0
            def allSetsMetTarget = true
            def maxWeightUsed = 0
            def maxRepsAtMaxWeight = 0
            
            for (int i = 1; i <= 10; i++) {
                def keyW = "${pIdStr}_${ex.id}_w_${i}"
                def keyR = "${pIdStr}_${ex.id}_r_${i}"
                
                def wStr = submittedData[keyW]?.toString()?.trim()
                def rStr = submittedData[keyR]?.toString()?.trim()
                
                if (wStr && rStr) {
                    try {
                        def w = wStr.toDouble()
                        def r = rStr.toInteger()
                        setsCompleted++
                        if (w < target.weight || r < target.reps) allSetsMetTarget = false
                        if (w > maxWeightUsed) { maxWeightUsed = w; maxRepsAtMaxWeight = r }
                    } catch (e) {}
                }
            }
            
            if (setsCompleted > 0) {
                workoutVolumeMap[muscleGroup] = (workoutVolumeMap[muscleGroup] ?: 0) + setsCompleted
                
                def currentFailCount = uHistory[ex.name]?.failCount ?: 0
                if (allSetsMetTarget) currentFailCount = 0 else currentFailCount++
                
                uHistory[ex.name] = [
                    lastWeight: maxWeightUsed, lastReps: maxRepsAtMaxWeight,
                    lastCompletedAll: allSetsMetTarget, failCount: currentFailCount,
                    date: new Date().format("MMM dd")
                ]
                
                def est1RM = Math.round(maxWeightUsed * (1 + (maxRepsAtMaxWeight / 30.0)))
                if (est1RM > (prs[ex.name] ?: 0)) prs[ex.name] = est1RM
                
                workoutSummary << "${ex.name}: ${setsCompleted} sets (Top: ${maxWeightUsed.toInteger()}lbs x ${maxRepsAtMaxWeight})"
            }
        }
        
        state.liftingHistory["${pIdStr}"] = uHistory
        
        def currentRecovery = calculateCurrentRecovery(pIdStr)
        def nowMs = new Date().time
        def mRecMap = state.muscleRecovery?."${pIdStr}" ?: [:]
        
        workoutVolumeMap.each { muscle, sets ->
            if (muscle != "Other") {
                def fatigueAdded = sets * 12.0
                def currentFatigue = 100.0 - (currentRecovery[muscle] ?: 100.0)
                def newFatigue = Math.min(100.0, currentFatigue + fatigueAdded)
                
                mRecMap[muscle] = [lastFatigue: newFatigue, timestamp: nowMs]
            }
        }
        if (state.muscleRecovery == null) state.muscleRecovery = [:]
        state.muscleRecovery["${pIdStr}"] = mRecMap
        
        def weightVar = settings["weightVar_${pIdStr}"]
        def bWeight = 200.0
        if (weightVar) {
            try {
                def val = getGlobalVar(weightVar)?.value
                if (val != null && val.toString().isNumber()) bWeight = val.toDouble()
            } catch (e) { }
        }
        
        // ========================================================
        // Perceived Effort (RPE) Adjustment Integration
        // ========================================================
        def weightFactor = Math.max(0.5, bWeight / 200.0)
        def baseEffort = (durationMins.toInteger() * 1.5 * weightFactor)
        
        def rpeStr = submittedData["rpe_${pIdStr}"]?.toString() ?: "moderate"
        def rpeMult = 1.0
        if (rpeStr == "easy") rpeMult = 0.75
        else if (rpeStr == "hard") rpeMult = 1.25
        else if (rpeStr == "max") rpeMult = 1.50
        
        def finalEffort = Math.round(baseEffort * participantReadiness * rpeMult).toInteger()
        // ========================================================

        def newLog = [
            id: java.util.UUID.randomUUID().toString().take(8), 
            date: new Date().format("MMM dd, yyyy"), 
            routine: plan.routineName, 
            duration: durationMins, 
            effort: finalEffort,
            details: workoutSummary,
            volume: workoutVolumeMap
        ]
        
        def allLogsMap = state.workoutLogs ?: [:]
        def wLogs = allLogsMap["${pIdStr}"] ?: []
        
        def newLogsList = [newLog] + wLogs
        def limit = (settings.historyLimit ?: "30").toInteger()
        if (newLogsList.size() > limit) newLogsList = newLogsList.take(limit)
        
        forceSaveHistory(pIdStr, newLogsList)
        
        prs.totalWorkouts = (prs.totalWorkouts ?: 0) + 1
        state.personalRecords["${pIdStr}"] = prs
        
        def effortVar = settings["effortScoreVar_${pIdStr}"]
        if (effortVar) {
            try {
                def cur = getGlobalVar(effortVar)?.value ?: "0"
                def newEffort = (cur.toString().isNumber() ? cur.toInteger() : 0) + finalEffort
                setGlobalVar(effortVar, newEffort.toString())
            } catch (e) { }
        }
        
        def syncVar = settings["liftingSyncVar_${pIdStr}"]
        if (syncVar) {
            try {
                def payload = "{\"action\":\"logWorkout\", \"profile\":\"${pIdStr}\", \"dur\":${durationMins}, \"effort\":${finalEffort}, \"ts\":${now()}}"
                setGlobalVar(syncVar, payload)
            } catch (e) { }
        }
    }
    state.activePlan.remove(planKey)
    runGarbageCollection() 
}

// ==============================================================================
// WEB APP / UI ROUTES
// ==============================================================================

def handleWebAction() {
    def pId = params.profileId ?: "1"
    def action = params.action
    def planKey = (state.activePlan["joint"]?.participants?.contains(pId)) ? "joint" : pId
    
    if (action == "generate") {
        def parts = []
        if (params.part_1) parts << "1"
        if (params.part_2) parts << "2"
        
        def checkKnee = params.jointKnee ? true : false
        def checkBack = params.jointBack ? true : false
        def checkShoulder = params.jointShoulder ? true : false
        
        def exCount = 5
        if (params.exerciseCount != null) {
            try { exCount = params.exerciseCount.toString().toInteger() } catch(e){}
        }
        
        def blockPhase = params.blockPhase ?: "Standard"
        
        def jointFlags = [knee: checkKnee, back: checkBack, shoulder: checkShoulder]
        if (parts.size() > 0) generateWorkout(parts, params.routineSelect, jointFlags, exCount, blockPhase)
        
    } else if (action == "swap") {
        swapExercise(planKey, params.swapExId, params.swapNewEx)
    } else if (action == "cancel") {
        state.activePlan.remove(planKey)
    } else if (action == "finishWorkout") {
        def dur = params.durationMins ? params.durationMins.toInteger() : 45
        
        if (params.workoutMood && params.workoutMood != "") {
            def moodVar = settings["moodVar_${pId}"]
            if (moodVar) setGlobalVar(moodVar, params.workoutMood)
        }
        
        completeWorkout(planKey, dur, params)
    } else if (action == "setMood") {
        if (params.newMood && params.newMood != "") {
            def moodVar = settings["moodVar_${pId}"]
            if (moodVar) setGlobalVar(moodVar, params.newMood)
        }
    } else if (action == "deleteTargetWeight") {
        def targetProfile = params.profileId ?: pId
        def exToRemove = params.targetEx?.toString()?.trim()
        if (exToRemove && state.liftingHistory?."${targetProfile}") {
            state.liftingHistory["${targetProfile}"].remove(exToRemove)
        }
    } else if (action == "deleteHistory") {
        def logId = params.logId?.toString()?.trim()
        def targetProfile = params.profileId ?: pId
        
        def allLogsMap = state.workoutLogs ?: [:]
        def wLogs = allLogsMap["${targetProfile}"] ?: []
        def newLogs = []
        def oldEffort = 0
        
        wLogs.eachWithIndex { l, idx ->
            def currentId = l.id ? l.id.toString().trim() : "legacy_${idx}"
            if (currentId != logId) {
                newLogs << l
            } else {
                oldEffort = l.effort ?: 0
            }
        }
        
        forceSaveHistory(targetProfile, newLogs)
        
        def prs = state.personalRecords?."${targetProfile}"
        if (prs) {
            if (newLogs.size() == 0) {
                prs.totalWorkouts = 0 
            } else if ((prs.totalWorkouts ?: 0) > 0) {
                prs.totalWorkouts -= 1
            }
            state.personalRecords["${targetProfile}"] = prs
        }
        
        def effortVar = settings["effortScoreVar_${targetProfile}"]
        if (effortVar && oldEffort > 0) {
            try {
                def cur = getGlobalVar(effortVar)?.value ?: "0"
                def curVal = cur.toString().isNumber() ? cur.toInteger() : 0
                def newVal = Math.max(0, curVal - oldEffort)
                setGlobalVar(effortVar, newVal.toString())
            } catch (e) { }
        }
        
    } else if (action == "editHistory") {
        def logId = params.logId?.toString()?.trim()
        def targetProfile = params.profileId ?: pId
        def newDur = params.editDuration?.toInteger() ?: 45
        def newEffort = params.editEffort?.toInteger() ?: 20
        
        def allLogsMap = state.workoutLogs ?: [:]
        def wLogs = allLogsMap["${targetProfile}"] ?: []
        def newLogs = []
        def effortDiff = 0
        
        wLogs.eachWithIndex { l, idx ->
            def currentId = l.id ? l.id.toString().trim() : "legacy_${idx}"
            if (currentId == logId) {
                effortDiff = newEffort - (l.effort ?: 0)
                def updatedLog = [:]
                l.each { k, v -> updatedLog[k] = v }
                updatedLog.duration = newDur
                updatedLog.effort = newEffort
                newLogs << updatedLog
            } else {
                newLogs << l
            }
        }
        
        forceSaveHistory(targetProfile, newLogs)
        
        def effortVar = settings["effortScoreVar_${targetProfile}"]
        if (effortVar && effortDiff != 0) {
            try {
                def cur = getGlobalVar(effortVar)?.value ?: "0"
                def curVal = cur.toString().isNumber() ? cur.toInteger() : 0
                def newVal = Math.max(0, curVal + effortDiff)
                setGlobalVar(effortVar, newVal.toString())
            } catch (e) { }
        }
    }
    
    return renderWebUI()
}

def renderWebUI() {
    def pId = params.profileId ?: "1"
    def uName = settings["userName_${pId}"] ?: "Profile ${pId}"
    def planKey = (state.activePlan["joint"]?.participants?.contains(pId)) ? "joint" : pId
    def activePlan = state.activePlan[planKey]
    def prs = state.personalRecords?."${pId}" ?: [:]
    def db = getExerciseDB()
    
    def fitnessLinkHtml = ""
    if (settings.fitnessAppUrl) {
        fitnessLinkHtml = "<a href='${settings.fitnessAppUrl}' style='color:white; text-decoration:none; font-size:13px; background:rgba(255,255,255,0.2); padding:6px 12px; border-radius:15px; display:flex; align-items:center; border: 1px solid rgba(255,255,255,0.4); margin-left: auto; margin-right: 15px;'>🏃 Fitness Tracker</a>"
    }

    def tabsHtml = "<div class='profile-tabs'>"
    ["1", "2"].each { i ->
        def tName = settings["userName_${i}"] ?: "User ${i}"
        def activeClass = (i.toString() == pId) ? "active" : ""
        tabsHtml += "<a href='?access_token=${state.accessToken}&profileId=${i}' class='profile-tab ${activeClass}'>${tName}</a>"
    }
    tabsHtml += "</div>"
    
    // ==========================================
    // VIEW: DASHBOARD & CHART DATA
    // ==========================================
    def sleepScore = 85
    def sVar = settings["sleepScoreVar_${pId}"]
    if (sVar) {
        try {
            def val = getGlobalVar(sVar)?.value
            if (val != null && val.toString().isNumber()) sleepScore = val.toInteger()
        } catch(e) {}
    }
    
    def currentEffort = 0
    def eVar = settings["effortScoreVar_${pId}"]
    if (eVar) {
        try {
            def val = getGlobalVar(eVar)?.value
            if (val != null && val.toString().isNumber()) currentEffort = val.toInteger()
        } catch (e) {}
    }
    
    def moodVar = settings["moodVar_${pId}"]
    def moodStr = "None"
    if (moodVar) {
        try {
            def val = getGlobalVar(moodVar)?.value
            if (val) moodStr = val.toString()
        } catch(e){}
    }
    
    def moodsList = ['😀','😂','🥰','😎','🤔','😴','🤪','🤐','🤯','🥳','🥶','🥵','🤢','🥺','🤬','🤠','🤡','👽','👻','💩','😤','😫']
    def moodOptionsHtml = "<option value=''>-- Keep Current Mood --</option>"
    def dashboardMoodOptions = "<option value=''>Select Mood...</option>"
    moodsList.each { m ->
        def isSel = (moodStr == m) ? "selected" : ""
        moodOptionsHtml += "<option value='${m}' ${isSel}>${m}</option>"
        dashboardMoodOptions += "<option value='${m}' ${isSel}>${m}</option>"
    }
    
    def moodLabel = "Neutral Baseline"
    def moodMultAWL = 1.0
    
    if (moodStr in ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]) { moodLabel = "Highly Motivated"; moodMultAWL = 1.05 }
    else if (moodStr in ["😴", "🥱", "🥺", "🤡"]) { moodLabel = "Low Energy"; moodMultAWL = 0.90 }
    else if (moodStr in ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]) { moodLabel = "Frazzled / Stressed"; moodMultAWL = 0.85 }
    else if (moodStr in ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]) { moodLabel = "Sick / Exhausted"; moodMultAWL = 0.80 }
    def moodDisplay = moodStr != 'None' ? moodStr : '😐'
    
    def bWeight = 200.0 
    def wVar = settings["weightVar_${pId}"]
    if (wVar) {
        try {
            def val = getGlobalVar(wVar)?.value
            if (val != null && val.toString().isNumber()) bWeight = val.toDouble()
        } catch(e) {}
    }
    
    def strainIndex = sleepScore > 0 ? (currentEffort / sleepScore.toDouble()) : 0
    def strainColor = strainIndex > 1.2 ? "var(--danger)" : (strainIndex >= 0.7 ? "var(--success)" : "var(--info)")
    def strainText = strainIndex > 1.2 ? "Over-Exertion (High Strain)" : (strainIndex >= 0.7 ? "Optimal Stimulus" : "Light Recovery Range")
    def proteinTarget = Math.round(bWeight * 0.8).toInteger()
    def waterTarget = Math.round((bWeight * 0.5) + ((currentEffort / 100.0) * 15)).toInteger()
    
    def isPreFatigued = currentEffort > 60
    def cardioColor = isPreFatigued ? "var(--danger)" : "var(--success)"
    def cardioText = isPreFatigued ? "High Fatigue (${currentEffort} pts) - Pre-Exhaustion Protocols Active" : "Fresh (${currentEffort} pts) - Full Load Capacity"
    
    def gymEnv = settings["gymEnv_${pId}"] ?: "Commercial Gym (A/C)"
    def heatHtml = ""
    
    if (gymEnv == "Commercial Gym (A/C)") {
        heatHtml = "<br><b>❄️ Environment:</b> <span style='color:var(--info); font-weight:bold;'>YMCA / Commercial A/C (68°F) - Optimal</span>"
    } else {
        def tSensor = settings["tempSensor_${pId}"]
        if (tSensor) {
            def curT = tSensor.currentValue("temperature")?.toDouble() ?: 70.0
            def rh = tSensor.currentValue("humidity")?.toDouble() ?: 50.0
            def hi = getHeatIndex(curT, rh)
            def heatColor = hi >= 90 ? "var(--danger)" : (hi >= 80 ? "var(--warning)" : "var(--success)")
            def heatText = hi >= 90 ? "Extreme Heat (Volume & Load Reduced)" : (hi >= 80 ? "Warm (Slight Drop)" : "Optimal Temp")
            heatHtml = "<br><b>🌡️ Garage Heat Governor:</b> <span style='color:${heatColor}; font-weight:bold;'>${Math.round(hi)}°F HI - ${heatText}</span>"
        }
    }
    
    def wLogsForChart = (state.workoutLogs?."${pId}" ?: []).take(10).reverse()
    def chartLabelsJson = []
    def chartVolumeJson = []
    def chartEffortJson = []
    
    def weeklyVolume = [Quads:0, Hamstrings:0, Chest:0, Back:0, Shoulders:0, Biceps:0, Triceps:0, Calves:0]
    def sevenDaysAgo = new Date() - 7
    
    wLogsForChart.each { l ->
        chartLabelsJson << "\"${l.date?.replace(', 2026', '')}\""
        chartEffortJson << (l.effort ?: 0)
        
        def estVolume = 0
        if (l.details) {
            l.details.each { d ->
                def m = (d =~ /(\d+)\s+sets\s+\(Top:\s+([\d\.]+)lbs\s+x\s+(\d+)\)/)
                if (m.find()) {
                    estVolume += (m[0][1].toInteger() * m[0][2].toDouble() * m[0][3].toInteger())
                }
            }
        }
        chartVolumeJson << estVolume.toInteger()
        
        try {
            def logDate = Date.parse("MMM dd, yyyy", l.date)
            if (logDate >= sevenDaysAgo && l.volume) {
                l.volume.each { k, v ->
                    weeklyVolume[k] = (weeklyVolume[k] ?: 0) + v
                }
            }
        } catch(e) {}
    }
    
    // --- BUILD HEATMAP HTML ---
    def currentMuscleRecovery = calculateCurrentRecovery(pId)
    def heatmapHtml = "<div class='heatmap-grid'>"
    ["Chest", "Back", "Quads", "Hamstrings", "Shoulders", "Biceps", "Triceps", "Calves"].each { muscle ->
        def recPct = Math.round(currentMuscleRecovery[muscle] ?: 100.0).toInteger()
        def bgCol = recPct >= 85 ? "var(--success)" : (recPct >= 50 ? "var(--warning)" : "var(--danger)")
        
        heatmapHtml += """
            <div style='background:${bgCol}; color:white; padding:10px 5px; border-radius:8px; text-align:center;'>
                <div style='font-size:11px; font-weight:bold; text-transform:uppercase;'>${muscle}</div>
                <div style='font-size:18px; font-weight:bold; margin-top:4px;'>${recPct}%</div>
            </div>
        """
    }
    heatmapHtml += "</div>"
    
    def volumeHtml = ""
    def rpTargets = [Chest: 15, Back: 15, Quads: 14, Hamstrings: 12, Shoulders: 14, Biceps: 10, Triceps: 10, Calves: 12]
    
    weeklyVolume.each { muscle, sets ->
        if (rpTargets[muscle]) {
            def target = rpTargets[muscle]
            def pct = Math.min(100, Math.round((sets / target) * 100))
            def barColor = pct >= 100 ? "var(--danger)" : (pct >= 60 ? "var(--success)" : "var(--info)")
            
            volumeHtml += """
                <div style='margin-bottom:12px;'>
                    <div style='display:flex; justify-content:space-between; font-size:12px; font-weight:bold; margin-bottom:4px; color:var(--text);'>
                        <span>${muscle}</span>
                        <span>${sets} / ${target} sets</span>
                    </div>
                    <div style='background:var(--bg); border-radius:10px; height:8px; overflow:hidden;'>
                        <div style='background:${barColor}; height:100%; width:${pct}%; transition:width 0.5s;'></div>
                    </div>
                </div>
            """
        }
    }
    
    def fuelingHtml = """
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
                    <div style='font-size:24px; font-weight:bold; color:var(--info);'>${waterTarget}oz</div>
                    <div style='font-size:11px; text-transform:uppercase; color:#64748b;'>Water</div>
                </div>
            </div>
            <p style='font-size:12px; color:#64748b; margin-top:0; line-height: 1.5;'>
                <b>Strain Status: ${strainText}.</b> Optimal index for stimulus is 0.7 - 1.1.<br>
                <b>Psychological Status: ${moodLabel}.</b><br>
                <b>🏃‍♂️ AFT Cardio Strain Sync:</b> <span style='color:${cardioColor}; font-weight:bold;'>${cardioText}</span>${heatHtml}
            </p>
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
    for(int i=1; i<=2; i++) {
        def mName = settings["userName_${i}"] ?: "User ${i}"
        def curMoodVar = settings["moodVar_${i}"]
        if (curMoodVar) {
            def mStr = getGlobalVar(curMoodVar)?.value?.toString() ?: "None"
            def mDisplay = mStr != "None" ? mStr : "😐"
            
            def mLabel = "Neutral Baseline"
            def volMod = "0%"
            if (mStr in ["🔥", "😎", "😀", "🥳", "🥰", "😂", "🤠"]) { mLabel = "Highly Motivated"; volMod = "+5%" }
            else if (mStr in ["😴", "🥱", "🥺", "🤡"]) { mLabel = "Low Energy"; volMod = "-10%" }
            else if (mStr in ["🤯", "🤬", "🤔", "🤪", "😤", "😫"]) { mLabel = "Frazzled / Stressed"; volMod = "-15%" }
            else if (mStr in ["🤕", "🤒", "🩹", "🥶", "🥵", "🤢", "💩", "🤐"]) { mLabel = "Sick / Exhausted"; volMod = "-20%" }
            
            moodMatrixHtml += """
            <div style='flex:1; min-width:120px; background:var(--light); border:1px solid var(--border); border-radius:8px; padding:10px; text-align:center;'>
                <div style='font-size:24px; margin-bottom:5px;'>${mDisplay}</div>
                <div style='font-weight:bold; font-size:13px; color:var(--dark);'>${mName}</div>
                <div style='font-size:11px; color:#64748b;'>${mLabel}</div>
                <div style='font-size:11px; font-weight:bold; color:var(--primary); margin-top:3px;'>Volume Mod: ${volMod}</div>
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
            <tr><th style='background-color:#0ea5e9; color:white;'>Category</th><th style='background-color:#0ea5e9; color:white;'>Emojis</th><th style='background-color:#0ea5e9; color:white;'>Lifting Vol</th><th style='background-color:#0ea5e9; color:white;'>House Impact</th></tr>
        </thead>
        <tbody>
            <tr><td class='dash-hl'>Highly Motivated</td><td>🔥 😎 😀 🥳 🥰 😂 🤠</td><td>+5%</td><td>Normal</td></tr>
            <tr><td class='dash-hl'>Neutral Baseline</td><td>😐 (or none)</td><td>0%</td><td>Normal</td></tr>
            <tr><td class='dash-hl'>Low Energy</td><td>😴 🥱 🥺 🤡</td><td>-10%</td><td>Normal</td></tr>
            <tr><td class='dash-hl'>Frazzled / Stressed</td><td>🤯 🤬 🤔 🤪 😤 😫</td><td>-15%</td><td>Auto-Reg (-2°F / Mute)</td></tr>
            <tr><td class='dash-hl'>Sick / Exhausted</td><td>🤕 🤒 🩹 🥶 🥵 🤢 💩 🤐</td><td>-20%</td><td>Auto-Reg (-2°F / Mute)</td></tr>
        </tbody>
    </table>
    </div>
    </div>
    """
    
    def totalWk = prs.totalWorkouts ?: 0
    
    def targetWeightsHtml = ""
    db.each { exName, exData ->
        def pLog = state.liftingHistory?."${pId}"?."${exName}"
        if (pLog) {
            def target = pLog.lastWeight
            if ((pLog.failCount ?: 0) >= 2) {
                target = Math.max(exData.defWeight, (Math.round((target * 0.9) / 5) * 5).toInteger())
            } else if (pLog.lastCompletedAll) {
                target += exData.inc
            }
            targetWeightsHtml += "<tr><td class='dash-hl'>${exName}</td><td>${target} lbs</td><td style='text-align:right;'><button type='button' class='btn-sm btn-danger-sm' style='padding: 3px 8px; font-size: 11px;' onclick='deleteTargetWeight(\"${exName}\")'>🗑️</button></td></tr>"
        }
    }
    if (targetWeightsHtml == "") targetWeightsHtml = "<tr><td colspan='3' style='text-align:center; color:#666;'>No exercises logged yet. Generate a workout to establish baselines.</td></tr>"

    def readScore = getReadinessModifier(pId)
    def readText = ""
    def readColor = ""
    if (readScore > 1.0) { readText = "High (Overload Protocol Enabled)"; readColor = "var(--success)" }
    else if (readScore >= 0.95 && readScore <= 1.0) { readText = "Normal (Standard Progression)"; readColor = "var(--info)" }
    else { readText = "Low (Recovery / Volume Drop)"; readColor = "var(--warning)" }

    def dashHtml = """
        ${fuelingHtml}
        ${dashboardMoodHtml}
        ${moodMatrixHtml}
        
        <div class='card' style='display:flex; justify-content:space-between; align-items:center; background:linear-gradient(to right, var(--primary), var(--info)); color:white;'>
            <div>
                <h3 style='color:white; border:none; margin:0; padding:0;'>Total Workouts</h3>
                <div style='font-size:12px; opacity:0.8; margin-top:3px;'>${uName}'s Lifetime Progress</div>
            </div>
            <div style='font-size:36px; font-weight:bold; font-family:monospace;'>${totalWk}</div>
        </div>
        
        <div class='card'>
            <h3>🔥 Muscle Recovery Heatmap</h3>
            <p style='font-size:11px; color:#64748b; margin-top:0;'>Live tracking of localized muscle fatigue. If a muscle drops below 85%, the engine automatically reduces loads and sets for back-to-back workouts to prevent overtraining.</p>
            ${heatmapHtml}
        </div>
        
        <div class='card'>
            <h3>📈 Progress & Volume Analytics</h3>
            <div style='position: relative; height:220px; width:100%;'>
                <canvas id='volumeChart'></canvas>
            </div>
        </div>
        
        <div class='card'>
            <h3>📊 Weekly Volume Landmarks (7-Day Rolling)</h3>
            <p style='font-size:11px; color:#64748b; margin-top:0;'>Tracks your completed hard sets against Dr. Mike Israetel's (RP) optimal hypertrophy ranges. Aim for Green (Optimal).</p>
            ${volumeHtml}
        </div>
        
        <div class='card'>
            <h3>🧠 Diagnostics & Auto-Regulation</h3>
            <p><b>Readiness Modifier:</b> <span style='color:${readColor}; font-weight:bold;'>${readText}</span></p>
            <p style='font-size:12px; color:#64748b;'>Engine automatically modifies weights and sets based on your tracked recovery and psychological mood.</p>
        </div>
        
        <div class='card'>
            <h3>🎯 Target Weights (Fully Recovered)</h3>
            <p style='font-size:12px; color:#64748b; margin-top:0;'>Expected load at Normal (1.0) readiness based on your last session.</p>
            <div style='max-height: 350px; overflow-y: auto; border: 1px solid var(--border); border-radius:6px;'>
                <table class="dash-table" style="margin-top:0; border:none;">
                    <thead style="position: sticky; top: 0; z-index: 1;"><tr><th>Exercise</th><th>Target Weight</th><th style="text-align:right;">Action</th></tr></thead>
                    <tbody>
                        ${targetWeightsHtml}
                    </tbody>
                </table>
            </div>
        </div>
    """

    // ==========================================
    // VIEW: HISTORY
    // ==========================================
    def wLogs = state.workoutLogs?."${pId}" ?: []
    def historyRowsHtml = ""
    if (wLogs.size() > 0) {
        wLogs.eachWithIndex { log, idx ->
            def lId = log.id ? log.id.toString() : "legacy_${idx}"
            def detailsHtml = ""
            if (log.details) {
                detailsHtml = "<div style='font-size:11px; color:#64748b; margin-top:6px; line-height:1.4;'>" + log.details.join("<br>") + "</div>"
            }
            
            historyRowsHtml += """
                <tr id='row_${lId}'>
                    <td style='vertical-align:top;'><b>${log.routine}</b><br><span style='font-size:11px; color:#666;'>${log.date}</span>${detailsHtml}</td>
                    <td id='dur_${lId}' style='vertical-align:top; padding-top:14px;'>${log.duration} mins</td>
                    <td id='eff_${lId}' style='vertical-align:top; padding-top:14px;'>⚡ ${log.effort}</td>
                    <td style='text-align:right; vertical-align:top; padding-top:12px;'>
                        <button class='btn-sm' style='margin-bottom:5px;' onclick='openEditModal("${lId}", "${log.duration}", "${log.effort}")'>✏️</button><br>
                        <button class='btn-sm btn-danger-sm' onclick='deleteLog("${lId}")'>🗑️</button>
                    </td>
                </tr>
            """
        }
    } else {
        historyRowsHtml = "<tr><td colspan='4' style='text-align:center; color:#666;'>No workout history found.</td></tr>"
    }

    def historyHtml = """
        <div class='card'>
            <h3>📋 Workout History</h3>
            <table class='dash-table'>
                <thead><tr><th>Routine & Date</th><th>Duration</th><th>Effort</th><th style='text-align:right;'>Actions</th></tr></thead>
                <tbody>
                    ${historyRowsHtml}
                </tbody>
            </table>
        </div>
        
        <!-- Edit Modal Overlay -->
        <div id='editModal' style='display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.5); z-index:200; align-items:center; justify-content:center;'>
            <div class='card' style='width:90%; max-width:400px; background:white; margin:auto;'>
                <h3 style='margin-top:0;'>✏️ Edit Workout Log</h3>
                <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                    <input type='hidden' name='action' value='editHistory'>
                    <input type='hidden' name='profileId' value='${pId}'>
                    <input type='hidden' name='logId' id='editLogId'>
                    
                    <label>Duration (Minutes)</label>
                    <input type='number' name='editDuration' id='editDurationVal' class='form-control' required>
                    
                    <label>Effort Score</label>
                    <input type='number' name='editEffort' id='editEffortVal' class='form-control' required>
                    
                    <div style='display:flex; gap:10px; margin-top:20px;'>
                        <button type='submit' class='btn btn-success' style='margin:0;'>Save</button>
                        <button type='button' class='btn btn-danger' style='margin:0;' onclick='closeEditModal()'>Cancel</button>
                    </div>
                </form>
            </div>
        </div>
    """

    // ==========================================
    // VIEW: GENERATE
    // ==========================================
    def genHtml = ""
    if (!activePlan) {
        def participantsHtml = "<div class='input-group'><label>Participants (Split-Screen / Joint Mode)</label><div class='checkbox-group'>"
        ["1", "2"].each { j ->
            def n = settings["userName_${j}"] ?: "User ${j}"
            def chk = (j == pId) ? "checked" : ""
            participantsHtml += "<label class='chk-label'><input type='checkbox' name='part_${j}' value='true' ${chk}> ${n}</label>"
        }
        participantsHtml += "</div></div>"
        
        genHtml = """
        <div class='card'>
            <h3>⚡ Generate Today's Workout</h3>
            <form method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                <input type='hidden' name='action' value='generate'>
                
                <label>Select Target Routine</label>
                <select name='routineSelect' class='form-control' required>
                    <option value='Full Body'>Full Body (Compound Mix)</option>
                    <option value='Upper Body'>Upper Body Focus</option>
                    <option value='Lower Body'>Lower Body Focus</option>
                </select>
                
                <label>Training Block Macrocycle</label>
                <select name='blockPhase' class='form-control'>
                    <option value='Standard'>Standard Linear Progression</option>
                    <option value='Hypertrophy'>Hypertrophy Block (Higher Reps / 4 Sets)</option>
                    <option value='Strength'>Strength Block (Heavy Low Reps)</option>
                    <option value='Deload'>Deload Week (Active CNS Recovery)</option>
                </select>
                
                <label>Number of Exercises in Workout: <span id='sliderVal'>5</span></label>
                <input type="range" id="exSliderInput" name="exerciseCount" min="3" max="8" value="5" class="form-control" style="padding:0; background:transparent;">
                
                <label>Pre-Workout Joint Check (Auto-Swaps Exercises)</label>
                <div class='checkbox-group' style='background:var(--light); padding:10px; border-radius:6px; border:1px solid var(--border);'>
                    <label class='chk-label'><input type='checkbox' name='jointBack' value='true'> 🔴 Lower Back Tightness</label>
                    <label class='chk-label'><input type='checkbox' name='jointKnee' value='true'> 🔴 Knee Discomfort</label>
                    <label class='chk-label'><input type='checkbox' name='jointShoulder' value='true'> 🔴 Shoulder Impingement</label>
                </div>
                
                ${participantsHtml}
                <button type='submit' class='btn btn-primary' style='margin-top:20px;'>🧠 Build Smart Routine</button>
            </form>
        </div>
        """
    } else {
        genHtml = """
        <div class='card' style='border-left: 5px solid var(--info);'>
            <h3>▶️ Active Workout: ${activePlan.routineName}</h3>
            <p style='font-size:13px; color:#64748b;'>An AI-generated routine is currently in progress. Switch to the Active Workout tab to complete it.</p>
        </div>
        """
    }
    
    // ==========================================
    // VIEW: ACTIVE WORKOUT
    // ==========================================
    def activeHtml = ""
    if (activePlan) {
        def exForms = ""
        def isJoint = activePlan.participants.size() > 1
        
        // ========================================================
        // MODIFIED: Build Perceived Effort Selection UI
        // ========================================================
        def rpeSelectorsHtml = ""
        activePlan.participants.each { loopId ->
            def loopName = settings["userName_${loopId}"] ?: "User ${loopId}"
            rpeSelectorsHtml += """
            <label style='color:var(--dark); margin-top:10px;'>How was the workout for ${loopName}?</label>
            <select name='rpe_${loopId}' id='rpe_${loopId}' class='form-control' style='margin-bottom:15px; font-size:14px;'>
                <option value='easy'>🟢 Easy (Could do much more)</option>
                <option value='moderate' selected>🟡 Moderate (Challenging but doable)</option>
                <option value='hard'>🟠 Hard (Pushed close to limits)</option>
                <option value='max'>🔴 Maximum Effort (Total failure)</option>
            </select>
            """
        }
        
        def rpeModalHtml = """
        <div id='rpeModal' style='display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.7); z-index:200; align-items:center; justify-content:center;'>
            <div class='card' style='width:90%; max-width:400px; background:white; margin:auto;'>
                <h3 style='margin-top:0;'>🏁 Finish Workout</h3>
                <p style='font-size:13px; color:#64748b; margin-top:0;'>Rate the perceived effort to finalize your fatigue scaling and effort scores.</p>
                ${rpeSelectorsHtml}
                <label style='color:var(--dark); margin-top:10px;'>Post-Workout Mood (Syncs to House)</label>
                <select name='workoutMood' class='form-control' style='margin-bottom:15px; font-size:14px;'>
                    ${moodOptionsHtml}
                </select>
                <div style='display:flex; gap:10px; margin-top:20px;'>
                    <button type='button' class='btn btn-success' style='margin:0;' onclick='confirmSubmitWorkout()'>💾 Save Log</button>
                    <button type='button' class='btn btn-danger' style='margin:0;' onclick='closeRpeModal()'>Cancel</button>
                </div>
            </div>
        </div>
        """
        // ========================================================

        activePlan.exercises.each { ex ->
            def swapOpts = ""
            db.findAll { it.value.cat == ex.cat }.each { k, v ->
                def sel = k == ex.name ? "selected" : ""
                swapOpts += "<option value='${k}' ${sel}>${k}</option>"
            }
            
            def swapHtml = """
                <div style='display:inline; margin-left:auto;'>
                    <select onchange='doSwap("${ex.id}", this.value)' style='font-size:11px; padding:3px; border-radius:4px;'>
                        ${swapOpts}
                    </select>
                </div>
            """
            
            def colsHtml = ""
            activePlan.participants.each { loopId ->
                def target = ex.targets[loopId]
                def loopName = settings["userName_${loopId}"] ?: "User ${loopId}"
                def uTitle = isJoint ? "<div style='font-size:13px; font-weight:bold; color:var(--dark); margin-bottom:8px; border-bottom:1px solid var(--border); padding-bottom:4px;'>👤 ${loopName}</div>" : ""
                
                def deloadWarn = target.isDeloading ? "<div style='font-size:10px; background:var(--warning); color:white; padding:2px 4px; border-radius:3px; display:inline-block; margin-bottom:5px;'>Plateau Deload</div>" : ""
                if (target.isRecovering) {
                    deloadWarn += "<div style='font-size:10px; background:var(--danger); color:white; padding:2px 4px; border-radius:3px; display:inline-block; margin-bottom:5px; margin-left:5px;'>Fatigue Drop</div>"
                }
                
                def warmHtml = ""
                if (target.warmups.size() > 0) {
                    target.warmups.eachWithIndex { wu, wIdx ->
                        warmHtml += """
                        <div class='warmup-row' style='display:flex; gap:5px; align-items:center; margin-bottom:4px;'>
                            <span style='font-size:11px; font-weight:bold; min-width:25px;'>W${wIdx+1}</span>
                            <input type='number' name='${loopId}_${ex.id}_wu_w_${wIdx}' class='form-control log-input' style='padding:4px; font-size:12px;' value='${wu.weight}' oninput='saveInputState(this)'>
                            <input type='number' name='${loopId}_${ex.id}_wu_r_${wIdx}' class='form-control log-input' style='padding:4px; font-size:12px;' value='${wu.reps}' oninput='saveInputState(this)'>
                            <button type='button' id='btn_wrm_${loopId}_${ex.id}_${wIdx}' class='btn-check' onclick='completeSet(this, 60)' style='width:30px; height:30px; font-size:12px;'>✅</button>
                        </div>
                        """
                    }
                    warmHtml = "<div style='margin-bottom:10px;'>${warmHtml}</div>"
                }
                
                def plateMathHtml = ""
                if (target.isBarbell && target.weight > 45) {
                    plateMathHtml = "<div class='plate-math' data-weight='${target.weight}'></div>"
                }
                
                def rows = ""
                for (int i = 1; i <= target.sets; i++) {
                    def timerSecs = ex.type == "Compound" ? 120 : 90
                    rows += """
                    <div class='set-row' data-set-num='${i}' style='display:flex; gap:5px; align-items:center; margin-bottom:8px;'>
                        <div style='width:35px; font-size:12px; font-weight:bold; color:var(--text);'>S${i}</div>
                        <input type='number' name='${loopId}_${ex.id}_w_${i}' class='form-control log-input' placeholder='Lbs' value='${target.weight}' onfocus='this.select();' oninput='saveInputState(this)'>
                        <input type='number' name='${loopId}_${ex.id}_r_${i}' class='form-control log-input' placeholder='Reps' value='${target.reps}' onfocus='this.select();' oninput='saveInputState(this)'>
                        <button type='button' id='btn_${loopId}_${ex.id}_${i}' class='btn-check' onclick='completeSet(this, ${timerSecs})'>✅</button>
                    </div>
                    """
                }
                
                rows += "<div id='extraSets_${loopId}_${ex.id}'></div>"
                
                colsHtml += """
                <div class='split-col'>
                    ${uTitle}
                    ${deloadWarn}
                    <div style='font-size:11px; color:var(--info); font-weight:bold; margin-bottom:4px;'>🎯 Target: ${target.sets}x${target.reps} @ ${target.weight}</div>
                    ${plateMathHtml}
                    ${warmHtml}
                    ${rows}
                    <button type='button' class='btn-sm' style='margin-top:5px; width:100%; background:var(--light); color:var(--dark); border:1px dashed var(--border);' onclick='addExtraSet("${loopId}", "${ex.id}", ${target.weight}, ${target.reps})'>➕ Add Set</button>
                </div>
                """
            }
            
            exForms += """
            <div style='border: 1px solid var(--border); border-radius:8px; padding:15px; margin-bottom:15px; background:var(--card-bg); box-shadow: 0 1px 3px rgba(0,0,0,0.05);'>
                <div style='display:flex; align-items:center; margin-bottom:15px;'>
                    <h4 style='margin:0; color:var(--dark); display:flex; align-items:center; gap:8px;'>
                        ${ex.name} <span class='badge'>${ex.type}</span>
                    </h4>
                    ${swapHtml}
                </div>
                <div class='split-container'>
                    ${colsHtml}
                </div>
            </div>
            """
        }
        
        activeHtml = """
        <div class='card' style='padding:15px; background:var(--light);'>
            <div style='display:flex; justify-content:space-between; align-items:center; margin-bottom:15px; border-bottom:1px solid var(--border); padding-bottom:10px;'>
                <h3 style='margin:0; border:none; padding:0;'>🏋️ ${activePlan.routineName}</h3>
                <div style='background:var(--dark); color:white; padding:6px 14px; border-radius:20px; font-family:monospace; font-weight:bold; font-size:14px;'>
                    ⏱️ <span id='stopwatchDisplay'>0:00</span>
                </div>
            </div>
            
            <form id='activeWorkoutForm' method='POST' action='?access_token=${state.accessToken}&profileId=${pId}'>
                <input type='hidden' name='action' id='formAction' value='finishWorkout'>
                <input type='hidden' name='swapExId' id='swapExId' value=''>
                <input type='hidden' name='swapNewEx' id='swapNewEx' value=''>
                <input type='hidden' name='durationMins' id='durationMinsInput' value='45'>
                
                ${exForms}
                ${rpeModalHtml}
                
                <div class='btn-group' style='margin-top:20px;'>
                    <button type='button' class='btn btn-success' onclick='openRpeModal()'>💾 Complete Workout</button>
                    <button type='button' class='btn btn-danger' onclick='submitFormAction("cancel")'>❌ Cancel</button>
                </div>
            </form>
        </div>
        """
    } else {
        activeHtml = "<div class='card'><h3>🏋️ Active Workout</h3><p style='color:#64748b;'>No active workout. Head to the Generate tab to build one.</p></div>"
    }

    def html = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset='UTF-8'>
        <meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0'>
        <title>Advanced Weight Lifting</title>
        <script src='https://cdnjs.cloudflare.com/ajax/libs/Chart.js/3.9.1/chart.min.js'></script>
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
            .app-title { padding: 15px; font-size: 17px; font-weight: bold; color: white; display: flex; align-items: center; justify-content: space-between; }
            .profile-tabs { display: flex; overflow-x: auto; -webkit-overflow-scrolling: touch; }
            .profile-tabs::-webkit-scrollbar { display: none; }
            .profile-tab { padding: 12px 20px; color: #94a3b8; text-decoration: none; font-weight: 600; font-size: 14px; white-space: nowrap; border-bottom: 3px solid transparent; }
            .profile-tab.active { color: white; border-bottom-color: var(--primary); background: rgba(255,255,255,0.05); }
            
            .app-body { display: flex; flex: 1; overflow: hidden; flex-direction: row; }
            .sidebar { width: 220px; background: var(--card-bg); border-right: 1px solid var(--border); display: flex; flex-direction: column; overflow-y: auto; z-index: 10; }
            .menu-item { padding: 16px 20px; cursor: pointer; border-bottom: 1px solid var(--light); font-weight: 600; color: #64748b; font-size: 14px; transition: all 0.2s; display: flex; align-items: center; gap: 10px; }
            .menu-item:hover { background: var(--light); color: var(--text); }
            .menu-item.active { background: var(--primary); color: white; border-color: var(--primary); }
            .main-content { flex: 1; padding: 15px; overflow-y: auto; -webkit-overflow-scrolling: touch; position: relative; }
            .view-section { display: none; max-width: 800px; margin: 0 auto; animation: fadeIn 0.3s; }
            .view-section.active { display: block; }
            @keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
            
            .card { background: var(--card-bg); border-radius: 10px; padding: 20px; margin-bottom: 20px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); }
            h3 { margin-top: 0; font-size: 17px; border-bottom: 1px solid var(--light); padding-bottom: 12px; color: var(--dark); }
            label { font-size: 12px; font-weight: bold; color: #64748b; margin-top: 15px; display: block; text-transform: uppercase; }
            .form-control { width: 100%; padding: 10px; margin-top: 6px; border: 1px solid var(--border); border-radius: 6px; font-size: 15px; background: #fff; }
            .form-control:focus { outline: none; border-color: var(--primary); }
            .log-input { flex: 1; text-align: center; margin: 0; padding: 8px; font-weight:bold; }
            .badge { font-size:10px; font-weight:normal; background:var(--primary); color:white; padding:3px 6px; border-radius:4px; }
            
            .heatmap-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-top: 10px; }
            
            .btn-check { width: 40px; height: 40px; border: 1px solid var(--border); border-radius: 6px; background: #fff; color: #e2e8f0; font-size: 16px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s; padding:0; margin:0; }
            .btn-check.completed { background: var(--success); border-color: var(--success); color: white; }
            .btn-check:active { transform: scale(0.95); }
            
            .btn-sm { padding: 6px 10px; border: 1px solid var(--border); background: white; border-radius: 4px; cursor: pointer; font-size: 13px; }
            .btn-danger-sm { background: #fee2e2; color: var(--danger); border-color: #fca5a5; }
            
            .input-group { background: var(--light); padding: 12px; border-radius: 6px; margin-top: 12px; }
            .input-group label:first-child { margin-top: 0; margin-bottom: 8px; }
            .checkbox-group { display: flex; flex-wrap: wrap; gap: 12px; }
            .chk-label { display: flex; align-items: center; font-size: 14px; margin-top: 0; text-transform: none; }
            .chk-label input { width: 18px; height: 18px; margin-right: 6px; }
            
            .split-container { display: flex; gap: 15px; }
            .split-col { flex: 1; min-width: 0; }
            .warmup-row { display:flex; justify-content:space-between; align-items:center; font-size:11px; background:#fef3c7; color:#b45309; padding:6px 8px; border-radius:4px; margin-bottom:4px; font-weight:bold; }
            .plate-math { font-size:10px; color:#64748b; font-style:italic; margin-bottom:10px; }
            
            .btn { width: 100%; padding: 14px; border: none; border-radius: 6px; color: white; font-size: 15px; font-weight: bold; cursor: pointer; margin-top: 20px; }
            .btn-primary { background-color: var(--primary); }
            .btn-success { background-color: var(--success); }
            .btn-danger { background-color: var(--danger); }
            .btn-group { display: flex; gap: 10px; }
            .btn-group .btn { margin-top: 0; }
            
            .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top: 10px;}
            .dash-table th, .dash-table td { padding: 12px; border-bottom: 1px solid var(--light); text-align: left; }
            .dash-table th { background-color: var(--light); color: var(--dark); font-size:12px; text-transform: uppercase; font-weight: bold; position: relative;}
            .dash-hl { font-weight:bold; color: var(--dark); }
            
            .timer-float { display:none; position:fixed; bottom:20px; right:20px; background:var(--dark); color:white; padding:15px 25px; border-radius:30px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); align-items:center; gap:15px; z-index:100; animation: slideUp 0.3s; }
            .timer-text { font-size:24px; font-weight:bold; font-family:monospace; }
            .timer-close { background:rgba(255,255,255,0.2); border:none; color:white; border-radius:50%; width:28px; height:28px; cursor:pointer; font-weight:bold; }
            @keyframes slideUp { from { bottom:-50px; opacity:0; } to { bottom:20px; opacity:1; } }
            
            @media (max-width: 768px) {
                .app-body { flex-direction: column; }
                .sidebar { width: 100%; flex-direction: row; overflow-x: auto; border-right: none; border-bottom: 1px solid var(--border); }
                .menu-item { white-space: nowrap; border-bottom: none; border-right: 1px solid var(--light); flex: 1; justify-content: center; padding: 14px 15px; }
                .menu-item.active { border-bottom: 3px solid var(--primary); }
                .split-container { flex-direction: column; gap: 20px; }
                .heatmap-grid { grid-template-columns: repeat(2, 1fr); }
                .timer-float { bottom: 15px; right: 50%; transform: translateX(50%); width: 80%; justify-content:center; }
            }
        </style>
    </head>
    <body>
        <div class='top-nav'>
            <div class='app-title'>
                <span>🦾 Advanced Weight Lifting</span>
                ${fitnessLinkHtml}
            </div>
            ${tabsHtml}
        </div>
        
        <div class='app-body'>
            <div class='sidebar'>
                <div id='menu-dashboard' class='menu-item' onclick='switchView("dashboard")'>📊 Dashboard</div>
                <div id='menu-history' class='menu-item' onclick='switchView("history")'>📋 History</div>
                <div id='menu-generate' class='menu-item' onclick='switchView("generate")'>⚡ Generate</div>
                <div id='menu-active' class='menu-item' onclick='switchView("active")'>🏋️ Active Workout</div>
            </div>
            
            <div class='main-content'>
                <div id='view-dashboard' class='view-section'>${dashHtml}</div>
                <div id='view-history' class='view-section'>${historyHtml}</div>
                <div id='view-generate' class='view-section'>${genHtml}</div>
                <div id='view-active' class='view-section'>${activeHtml}</div>
                
                <div id='restTimerFloat' class='timer-float'>
                    <div style='font-size:12px; text-transform:uppercase; color:#94a3b8; font-weight:bold;'>Rest</div>
                    <div id='restTimerText' class='timer-text'>0:00</div>
                    <button class='timer-close' onclick='stopTimer()'>✕</button>
                </div>
            </div>
        </div>
        
        <script>
            let wakeLock = null;
            async function requestWakeLock() {
                try {
                    wakeLock = await navigator.wakeLock.request('screen');
                } catch (err) {}
            }

            function switchView(viewId) {
                document.querySelectorAll('.view-section').forEach(el => el.classList.remove('active'));
                document.querySelectorAll('.menu-item').forEach(el => el.classList.remove('active'));
                
                let targetView = document.getElementById('view-' + viewId);
                let targetMenu = document.getElementById('menu-' + viewId);
                
                if (targetView) targetView.classList.add('active');
                if (targetMenu) targetMenu.classList.add('active');
                
                localStorage.setItem('iron_last_view', viewId);
                
                if (viewId === 'active') {
                    requestWakeLock();
                    startStopwatch();
                }
            }
            
            // --- Live Stopwatch ---
            let stopwatchSeconds = parseInt(localStorage.getItem('awl_stopwatch_secs') || '0');
            let stopwatchInterval = null;
            
            function startStopwatch() {
                if (stopwatchInterval) return;
                stopwatchInterval = setInterval(() => {
                    stopwatchSeconds++;
                    localStorage.setItem('awl_stopwatch_secs', stopwatchSeconds);
                    let m = Math.floor(stopwatchSeconds / 60);
                    let s = stopwatchSeconds % 60;
                    let disp = m + ":" + (s < 10 ? '0' : '') + s;
                    let el = document.getElementById('stopwatchDisplay');
                    if (el) el.innerText = disp;
                }, 1000);
            }
            
            // --- Dynamic Set Adder ---
            function addExtraSet(loopId, exId, defW, defR) {
                let container = document.getElementById('extraSets_' + loopId + '_' + exId);
                let currentCount = container.querySelectorAll('.set-row-extra').length + 4; 
                let newSetIdx = currentCount + 1;
                
                let div = document.createElement('div');
                div.className = 'set-row-extra';
                div.style.cssText = 'display:flex; gap:5px; align-items:center; margin-bottom:8px;';
                div.innerHTML = `
                    <div style='width:35px; font-size:12px; font-weight:bold; color:var(--text);'>S\${newSetIdx}</div>
                    <input type='number' name='\${loopId}_\${exId}_w_\${newSetIdx}' class='form-control log-input' placeholder='Lbs' value='\${defW}' onfocus='this.select();' oninput='saveInputState(this)'>
                    <input type='number' name='\${loopId}_\${exId}_r_\${newSetIdx}' class='form-control log-input' placeholder='Reps' value='\${defR}' onfocus='this.select();' oninput='saveInputState(this)'>
                    <button type='button' id='btn_\${loopId}_\${exId}_\${newSetIdx}' class='btn-check' onclick='completeSet(this, 90)'>✅</button>
                `;
                container.appendChild(div);
            }
            
            // --- History Modal Functions ---
            function openEditModal(logId, dur, eff) {
                document.getElementById('editLogId').value = logId;
                document.getElementById('editDurationVal').value = dur;
                document.getElementById('editEffortVal').value = eff;
                document.getElementById('editModal').style.display = 'flex';
            }
            
            function closeEditModal() {
                document.getElementById('editModal').style.display = 'none';
            }
            
            function deleteLog(logId) {
                if (confirm("Are you sure you want to delete this workout log?")) {
                    let form = document.createElement('form');
                    form.method = 'POST';
                    form.action = "?access_token=${state.accessToken}&profileId=${pId}";
                    
                    let actIn = document.createElement('input');
                    actIn.type = 'hidden'; actIn.name = 'action'; actIn.value = 'deleteHistory';
                    form.appendChild(actIn);
                    
                    let idIn = document.createElement('input');
                    idIn.type = 'hidden'; idIn.name = 'logId'; idIn.value = logId;
                    form.appendChild(idIn);
                    
                    let profIn = document.createElement('input');
                    profIn.type = 'hidden'; profIn.name = 'profileId'; profIn.value = '${pId}';
                    form.appendChild(profIn);
                    
                    document.body.appendChild(form);
                    form.submit();
                }
            }

            function deleteTargetWeight(exName) {
                if (confirm("Are you sure you want to delete the baseline history for " + exName + "? This will reset its target weight to default.")) {
                    let form = document.createElement('form');
                    form.method = 'POST';
                    form.action = "?access_token=${state.accessToken}&profileId=${pId}";
                    
                    let actIn = document.createElement('input');
                    actIn.type = 'hidden'; actIn.name = 'action'; actIn.value = 'deleteTargetWeight';
                    form.appendChild(actIn);
                    
                    let exIn = document.createElement('input');
                    exIn.type = 'hidden'; exIn.name = 'targetEx'; exIn.value = exName;
                    form.appendChild(exIn);
                    
                    let profIn = document.createElement('input');
                    profIn.type = 'hidden'; profIn.name = 'profileId'; profIn.value = '${pId}';
                    form.appendChild(profIn);
                    
                    document.body.appendChild(form);
                    form.submit();
                }
            }

            // --- Auto-Save Caching Engine ---
            function saveInputState(el) {
                localStorage.setItem('awl_' + el.name, el.value);
            }
            
            function restoreInputState() {
                document.querySelectorAll('.log-input').forEach(el => {
                    let saved = localStorage.getItem('awl_' + el.name);
                    if (saved !== null) el.value = saved;
                });
                document.querySelectorAll('.btn-check').forEach(el => {
                    if (localStorage.getItem('awl_' + el.id) === 'true') {
                        el.classList.add('completed');
                    }
                });
            }
            
            function clearCachedState() {
                Object.keys(localStorage).forEach(key => {
                    if (key.startsWith('awl_')) {
                        localStorage.removeItem(key);
                    }
                });
            }

            function doSwap(exId, newEx) {
                document.getElementById('formAction').value = 'swap';
                document.getElementById('swapExId').value = exId;
                document.getElementById('swapNewEx').value = newEx;
                document.getElementById('activeWorkoutForm').submit();
            }
            
            function playChime() {
                try {
                    let ctx = new (window.AudioContext || window.webkitAudioContext)();
                    let osc = ctx.createOscillator();
                    osc.connect(ctx.destination);
                    osc.frequency.setValueAtTime(800, ctx.currentTime);
                    osc.start();
                    osc.stop(ctx.currentTime + 0.6);
                } catch(e) {}
            }
            
            let countdown;
            function triggerTimer(seconds) {
                clearInterval(countdown);
                let time = seconds;
                let timerEl = document.getElementById('restTimerFloat');
                let timerText = document.getElementById('restTimerText');
                
                timerEl.style.display = 'flex';
                timerEl.style.background = 'var(--dark)';
                
                countdown = setInterval(() => {
                    let m = Math.floor(time / 60);
                    let s = time % 60;
                    timerText.innerText = m + ":" + (s < 10 ? '0' : '') + s;
                    
                    if (time <= 0) {
                        clearInterval(countdown);
                        timerText.innerText = "GO!";
                        timerEl.style.background = 'var(--success)';
                        playChime();
                    }
                    time--;
                }, 1000);
            }
            
            function stopTimer() {
                clearInterval(countdown);
                document.getElementById('restTimerFloat').style.display = 'none';
            }
            
            function completeSet(btn, restSecs) {
                if (!btn.classList.contains('completed')) {
                    btn.classList.add('completed');
                    triggerTimer(restSecs);
                } else {
                    btn.classList.remove('completed');
                }
                localStorage.setItem('awl_' + btn.id, btn.classList.contains('completed'));
            }
            
            // ========================================================
            // MODIFIED: RPE Modal Frontend Controls
            // ========================================================
            function openRpeModal() {
                document.getElementById('rpeModal').style.display = 'flex';
            }
            
            function closeRpeModal() {
                document.getElementById('rpeModal').style.display = 'none';
            }
            
            function confirmSubmitWorkout() {
                let mins = Math.max(1, Math.round(stopwatchSeconds / 60));
                document.getElementById('durationMinsInput').value = mins;
                
                document.querySelectorAll('.set-row, .set-row-extra').forEach(row => {
                    let checkBtn = row.querySelector('.btn-check');
                    if (checkBtn && !checkBtn.classList.contains('completed')) {
                        row.querySelectorAll('.log-input').forEach(input => {
                            input.removeAttribute('name');
                        });
                    }
                });

                localStorage.removeItem('awl_stopwatch_secs');
                clearCachedState();
                
                document.getElementById('formAction').value = 'finishWorkout';
                document.getElementById('activeWorkoutForm').submit();
            }
            // ========================================================

            function submitFormAction(act) {
                if (act === 'cancel') {
                    clearCachedState();
                    localStorage.removeItem('awl_stopwatch_secs');
                }
                document.getElementById('formAction').value = act;
                document.getElementById('activeWorkoutForm').submit();
            }
            
            function calcPlates(weight) {
                let bar = 45;
                if(weight <= bar) return "Bar only";
                let rem = (weight - bar) / 2;
                let plates = [];
                [45, 25, 10, 5, 2.5].forEach(p => {
                    let count = Math.floor(rem / p);
                    if(count > 0) {
                        plates.push(count + "x" + p);
                        rem -= (count * p);
                    }
                });
                return "Plates: " + plates.join(", ") + " per side";
            }
            
            // --- Chart.js Rendering Function ---
            function renderDashboardChart() {
                let ctx = document.getElementById('volumeChart');
                if (!ctx) return;
                
                let labels = [${chartLabelsJson.join(",")}];
                let volumeData = [${chartVolumeJson.join(",")}];
                let effortData = [${chartEffortJson.join(",")}];
                
                new Chart(ctx, {
                    type: 'line',
                    data: {
                        labels: labels,
                        datasets: [
                            {
                                label: 'Est. Volume (lbs)',
                                data: volumeData,
                                borderColor: '#007bff',
                                backgroundColor: 'rgba(0, 123, 255, 0.1)',
                                fill: true,
                                yAxisID: 'y'
                            },
                            {
                                label: 'Effort Score',
                                data: effortData,
                                borderColor: '#f59e0b',
                                borderDash: [5, 5],
                                yAxisID: 'y1'
                            }
                        ]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        interaction: { mode: 'index', intersect: false },
                        scales: {
                            y: { type: 'linear', display: true, position: 'left', title: { display: true, text: 'Volume' } },
                            y1: { type: 'linear', display: true, position: 'right', grid: { drawOnChartArea: false }, title: { display: true, text: 'Effort' } }
                        }
                    }
                });
            }
            
            document.addEventListener("DOMContentLoaded", function() {
                let exSlider = document.getElementById('exSliderInput');
                let sVal = document.getElementById('sliderVal');
                if (exSlider && sVal) {
                    exSlider.addEventListener('input', function() {
                        sVal.innerText = this.value;
                    });
                }
            
                document.querySelectorAll('.plate-math').forEach(el => {
                    let w = parseFloat(el.getAttribute('data-weight'));
                    el.innerText = calcPlates(w);
                });
                
                restoreInputState();
                renderDashboardChart();
                
                let isActive = ${activePlan != null};
                if (isActive) {
                    switchView('active');
                } else {
                    let savedView = localStorage.getItem('iron_last_view') || 'dashboard';
                    if (savedView === 'active') savedView = 'generate'; 
                    switchView(savedView);
                }
            });
        </script>
    </body>
    </html>
    """
    
    render contentType: "text/html", data: html
}
