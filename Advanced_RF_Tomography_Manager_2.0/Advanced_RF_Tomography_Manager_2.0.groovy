/**
 * Advanced RF Tomography Manager 2.0 (v7.3.0 Flagship BMS - Vibration & Multipurpose Posture Teacher, Dedicated Child Presence Sensor, Ceiling Fan Immunity & Anti-Teleportation)
 *
 * v7.3.0 Upgrades:
 * - UPGRADE 0 (Vibration & Multipurpose Posture Teacher Sensors - Ground Truth for Chairs & Beds):
 *   - Added `vibrationAssistSensors` (capability.accelerationSensor) & `shockAssistSensors` (capability.shockSensor / multipurpose)
 *     in Section 0 so vibration sensors on chairs, couches, desks, or beds act as a ground-truth teacher just like PIR sensors.
 *   - In `zoneAssignmentPage`, each Vibration/Multipurpose sensor can be assigned to a room, configured with what posture it
 *     teaches (`sitting` in a chair/couch, `laying` in bed, or `standing`), and given a custom Posture/Activity Label
 *     (e.g., "🪑 Sitting in Recliner", "💻 Sitting at Desk Chair", "🛌 Laying in Bed", or Custom Text).
 *   - When vibration is detected, the app immediately pings the room's switches/plugs, trains the 7-Day Rolling Learning Ledger
 *     for that exact posture (Sitting or Laying Down), holds the room occupied, and displays the custom label on the dashboard!
 * - UPGRADE 1 (Dedicated Child Presence Sensor Only - All Child Timing/Schedules Removed):
 *   - Removed all scheduled child time windows, day-of-week schedules, manual date loggers, and child virtual switches.
 *   - Added `childPresenceSensors` (`capability.presenceSensor`) so child presence is determined strictly by whether the
 *     Child Presence sensor is `present`.
 * - UPGRADE 2 (Always-ON Ceiling Fan Switch Immunity):
 *   - Ceiling Fan switches being ON never count toward a room being active or occupied (since they stay ON in Home modes).
 * - UPGRADE 3 (Non-Fan Light Switch Assist Without Self-Locking):
 *   - Non-fan light switches being ON provide a small (+3%) sensitivity assist ONLY when real RF/PIR/Vibration motion is present,
 *     and NEVER lock or hold a room active when someone leaves and Motion Lighting leaves the light on.
 * - UPGRADE 4 (Anti-Teleportation Contiguous Path Guard):
 *   - Prevents a single occupant in the Kitchen from "teleporting" to the far end of the house on a stray mesh ripple.
 */
import groovy.json.JsonSlurper
import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap

@Field static final BigDecimal DEFAULT_DRIFT_DEADBAND_DB = 0.18G
@Field static final double DEFAULT_VARIANCE_WEIGHT = 26.0d
@Field static final double DEFAULT_SHADOW_WEIGHT = 20.0d
@Field static final double DEFAULT_REFERENCE_NEAR_DB = 2.40d
@Field static final int LEARNING_WINDOW_DAYS = 7
@Field static final int RE_EVAL_CYCLE_DAYS = 30
@Field static final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> FAST_RAM_CACHE = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>()
@Field static final List<String> FAST_CACHE_KEYS = [
    "rfHistory", "baselines", "nodeScores", "measuredRtt",
    "lastPingSent", "lastRttReceivedMs", "lastConsumedRttMs", "lastSwitchToggleMs", "lastPowerChangeMs",
    "lastKnownPowerW", "isolatedChildFlags", "nodeHitStreaks", "nodeFlatCounts",
    "dailyStats", "nodeAutoTune", "zoneBleedMatrix", "pirTeacherModel"
]

definition(
    name: "Advanced RF Tomography Manager 2.0",
    namespace: "shaneallen",
    author: "ShaneAllen",
    description: "Flagship BMS Zoned RF Tomography: v7.3.0 Vibration & Multipurpose Chair/Bed Posture Teacher, Child Presence Sensor, Fan Immunity & Anti-Teleportation",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
    page(name: "zoneAssignmentPage")
    page(name: "activityMappingPage")
}

// ==============================================================================
// THREAD-SAFE IN-MEMORY CACHE & NULL-SAFE NUMERIC HELPERS
// ==============================================================================

double safeDouble(Object val, double fallback = 0.0d) {
    if (val == null) return fallback
    if (val instanceof Number) return ((Number) val).doubleValue()
    try {
        return val.toString().toDouble()
    } catch (Exception ignored) {
        return fallback
    }
}

int safeInt(Object val, int fallback = 0) {
    if (val == null) return fallback
    if (val instanceof Number) return ((Number) val).intValue()
    try {
        return val.toString().toBigDecimal().intValue()
    } catch (Exception ignored) {
        return fallback
    }
}

long safeLong(Object val, long fallback = 0L) {
    if (val == null) return fallback
    if (val instanceof Number) return ((Number) val).longValue()
    try {
        return val.toString().toBigDecimal().longValue()
    } catch (Exception ignored) {
        return fallback
    }
}

ConcurrentHashMap<String, Object> getFastMap(String mapName) {
    String appKey = "${app.id}_${mapName}"
    ConcurrentHashMap<String, Object> cached = FAST_RAM_CACHE.get(appKey)
    if (cached == null) {
        cached = new ConcurrentHashMap<String, Object>()
        Map persisted = null
        try {
            persisted = atomicState."${mapName}" as Map
        } catch (Exception ignored) {}
        if (persisted instanceof Map) {
            persisted.each { k, v ->
                if (k != null && v != null) {
                    cached.put(k.toString(), v)
                }
            }
        }
        FAST_RAM_CACHE.putIfAbsent(appKey, cached)
        cached = FAST_RAM_CACHE.get(appKey)
    }
    return cached
}

void putFastMap(String mapName, Map updatedMap, boolean forcePersist = false) {
    ConcurrentHashMap<String, Object> cached = getFastMap(mapName)
    cached.clear()
    updatedMap?.each { k, v ->
        if (k != null && v != null) {
            cached.put(k.toString(), v)
        }
    }
    if (forcePersist) {
        atomicState."${mapName}" = new LinkedHashMap(cached)
    }
}

void flushRamCacheToAtomicState(boolean forceNow = false) {
    long nowTs = now()
    long lastFlush = safeLong(atomicState.lastRamFlushMs, 0L)
    if (!forceNow && (nowTs - lastFlush) < 60000L) {
        return
    }
    atomicState.lastRamFlushMs = nowTs
    FAST_CACHE_KEYS.each { String key ->
        String appKey = "${app.id}_${key}"
        ConcurrentHashMap<String, Object> cached = FAST_RAM_CACHE.get(appKey)
        if (cached != null) {
            atomicState."${key}" = new LinkedHashMap(cached)
        }
    }
}

void clearFastRamCache() {
    FAST_CACHE_KEYS.each { String key ->
        FAST_RAM_CACHE.remove("${app.id}_${key}")
    }
}

// ==============================================================================
// UI & HELPER METHODS
// ==============================================================================

String uiHelpBox(String title, String body, String tip = null) {
    def tipHtml = tip ? "<div style='margin-top:6px; padding-top:6px; border-top:1px dashed #cbd5e1; color:#0f5132;'><b>💡 Recommended:</b> ${tip}</div>" : ""
    return """
    <div style='background-color:#f8fafc; border:1px solid #e2e8f0; border-left:4px solid #0284c7; padding:10px 12px; border-radius:6px; font-size:13px; color:#334155; margin-bottom:10px; line-height:1.45;'>
        <div style='font-weight:bold; color:#0f172a; margin-bottom:3px;'>ℹ️ ${title}</div>
        <div>${body}</div>
        ${tipHtml}
    </div>
    """
}

String uiBadge(String text, String bgColor, String textColor = "#ffffff") {
    return "<span style='background-color:${bgColor}; color:${textColor}; padding:3px 8px; border-radius:12px; font-size:12px; font-weight:bold; display:inline-block;'>${text}</span>"
}

String formatDurationMs(long durationMs) {
    long totalSec = Math.max(0L, Math.round(durationMs / 1000.0d))
    if (totalSec < 60L) return "${totalSec}s"
    long hours = (long)(totalSec / 3600L)
    long mins = (long)((totalSec % 3600L) / 60L)
    long secs = totalSec % 60L
    if (hours > 0L) {
        return "${hours}h ${mins}m"
    }
    return "${mins}m ${secs}s"
}

String buildLastThreeRoomsHtml(Map currentZoneStatus = null) {
    List recentList = (atomicState.recentOccupiedRooms ?: []).toList()
    Map zMap = currentZoneStatus != null ? currentZoneStatus : ((atomicState.zoneStatus ?: [:]) as Map)
    if (!recentList) {
        return "<span style='color:#64748b; font-style:italic;'>No room entries recorded yet. Walk into a monitored room to populate the Last 3 Occupied Rooms trail.</span>"
    }

    long nowTs = now()
    List<String> badges = []
    recentList.take(3).eachWithIndex { Object rawItem, int idx ->
        if (rawItem instanceof Map) {
            String rName = (rawItem.zoneName ?: "Room").toString()
            String enteredAt = (rawItem.enteredAtStr ?: "--:--").toString()
            String exitedAt = rawItem.exitedAtStr ? rawItem.exitedAtStr.toString() : null
            long startMs = safeLong(rawItem.enteredEpochMs, nowTs)
            long endMs = safeLong(rawItem.lastSeenEpochMs, nowTs)

            Map liveZ = zMap[rName] as Map
            boolean isCurrentlyOccupied = (liveZ?.occupancyState in ["active_motion", "stationary_occupied", "sleeping_occupied", "active_motion_cooldown"]) && (idx == 0 || rawItem.isActive == true)
            long durMs = isCurrentlyOccupied ? Math.max(1000L, nowTs - startMs) : Math.max(1000L, endMs - startMs)
            String durStr = formatDurationMs(durMs)
            String postStr = isCurrentlyOccupied ? (liveZ?.posture ?: (rawItem.posture ?: "Occupied")) : (rawItem.posture ?: "Occupied")

            String statusDot = isCurrentlyOccupied ?
                "<span style='color:#16a34a; font-weight:bold;'>🟢 Occupied Now (${durStr})</span>" :
                "<span style='color:#475569;'>⚪ ${exitedAt ? 'Left ' + exitedAt : 'Visit: ' + durStr}</span>"

            String rankBadge = uiBadge("#${idx + 1}", idx == 0 ? "#0284c7" : (idx == 1 ? "#0f766e" : "#475569"))
            badges << """
            <div style='flex:1; min-width:210px; background:#f8fafc; border:1px solid #cbd5e1; border-left:4px solid ${idx == 0 ? "#0284c7" : (idx == 1 ? "#0f766e" : "#64748b")}; border-radius:6px; padding:7px 10px;'>
                <div style='font-size:12.5px; font-weight:bold; color:#0f172a;'>${rankBadge}  ${rName}</div>
                <div style='font-size:11.5px; color:#1e293b; margin-top:3px;'>🕒 <b>Entered:</b> ${enteredAt}  |  ${statusDot}</div>
                <div style='font-size:11px; color:#64748b; margin-top:2px;'>Posture: <b>${postStr}</b></div>
            </div>
            """
        }
    }
    return "<div style='display:flex; gap:8px; flex-wrap:wrap; width:100%;'>${badges.join('')}</div>"
}

// ==============================================================================
// PORTAL ROLE, CEILING FAN & LIGHT SWITCH ASSIST HELPERS (v7.3.0)
// ==============================================================================

boolean isCeilingFanSwitch(dev) {
    if (!dev) return false
    String lbl = (dev.displayName ?: "").toLowerCase()
    return lbl.contains("ceiling fan") || lbl.contains(" fan") || lbl.startsWith("fan ")
}

boolean isClosetOrBackOfRoomSwitch(dev) {
    if (!dev) return false
    String lbl = (dev.displayName ?: "").toLowerCase()
    return lbl.contains("closet") || lbl.contains("wardrobe") || lbl.contains("pantry") || lbl.contains("bath tub") || lbl.contains("tub & shower")
}

boolean isNonFanLightSwitch(dev) {
    if (!dev) return false
    return !isSmartPlugOrApplianceNode(dev) && !isCeilingFanSwitch(dev) && !isClosetOrBackOfRoomSwitch(dev)
}

String fetchDevicePortalRole(dev) {
    if (!dev) return "default_auto"
    String devId = dev.id.toString()
    return (settings["devicePortalRole_${devId}"]?.toString() ?: "default_auto")
}

String getEffectiveDevicePortalRole(dev, List zoneDevices = null) {
    String explicitPortal = fetchDevicePortalRole(dev)
    if (explicitPortal && explicitPortal != "default_auto") {
        return explicitPortal
    }
    List devsInZone = zoneDevices ?: (buildZoneMap()[getDeviceZoneName(dev)] ?: [dev])
    if (isClosetOrBackOfRoomSwitch(dev) && devsInZone.size() >= 2) {
        return "interior"
    }
    if (isSmartPlugOrApplianceNode(dev)) {
        boolean hasWallSwitchInRoom = devsInZone.size() >= 2 && devsInZone.any { !isSmartPlugOrApplianceNode(it) }
        return hasWallSwitchInRoom ? "interior" : "both"
    }
    return "both"
}

String getPortalRoleShortBadge(String portalRole) {
    if (portalRole == "both") return "🚪 Entrance & Exit"
    if (portalRole == "entrance_only") return "➡️ Entrance Only"
    if (portalRole == "exit_only") return "⬅️ Exit Only"
    if (portalRole == "interior") return "🛋️ Back-of-Room / Interior Hold"
    return "⚪ Standard (Any)"
}

boolean isEntrancePortalRole(String portalRole) {
    return (portalRole in ["both", "entrance_only", "none"])
}

boolean isExitPortalRole(String portalRole) {
    return (portalRole in ["both", "exit_only"])
}

boolean isInteriorHoldRole(String portalRole) {
    return (portalRole == "interior")
}

// ==============================================================================
// v7.3.0 VIBRATION & MULTIPURPOSE SENSOR GROUND-TRUTH POSTURE TEACHER HELPERS
// ==============================================================================

List getAllVibrationAssistDevices() {
    Map unique = [:]
    vibrationAssistSensors?.each { unique[it.id.toString()] = it }
    shockAssistSensors?.each { unique[it.id.toString()] = it }
    return unique.values().toList()
}

String getVibrationSensorZoneName(vibDev) {
    if (!vibDev) return "General Zone"
    String custom = settings["vibZoneOverride_${vibDev.id}"]?.toString()?.trim()
    if (custom) return custom
    if (vibDev.roomName && vibDev.roomName.trim()) return vibDev.roomName.trim()
    return inferRoomFromLabel(vibDev.displayName)
}

Map buildVibrationZoneMap() {
    Map vibZones = [:]
    getAllVibrationAssistDevices().each { vibDev ->
        String zName = getVibrationSensorZoneName(vibDev)
        if (!vibZones[zName]) vibZones[zName] = []
        vibZones[zName] << vibDev
    }
    return vibZones
}

boolean isBedOrLayingVibrationByName(vibDev) {
    if (!vibDev) return false
    String lbl = (vibDev.displayName ?: "").toLowerCase()
    List bedWords = ["bed", "mattress", "pillow", "headboard", "bunk", "cot", "sleep", "crib", "futon"]
    return bedWords.any { lbl.contains(it) }
}

boolean isStandingVibrationByName(vibDev) {
    if (!vibDev) return false
    String lbl = (vibDev.displayName ?: "").toLowerCase()
    List standWords = ["mat", "floor", "standing", "treadmill", "counter", "sink", "kitchen"]
    return standWords.any { lbl.contains(it) }
}

String getEffectiveVibrationPostureRole(vibDev) {
    if (!vibDev) return "sitting"
    String vibId = vibDev.id.toString()
    String explicitRole = settings["vibPostureRole_${vibId}"]?.toString()
    if (explicitRole && explicitRole != "auto") {
        return explicitRole
    }
    if (isBedOrLayingVibrationByName(vibDev)) {
        return "laying"
    }
    if (isStandingVibrationByName(vibDev)) {
        return "standing"
    }
    return "sitting"
}

String getVibrationMeaningLabel(vibDev) {
    if (!vibDev) return "🪑 Sitting in Chair"
    String vibId = vibDev.id.toString()
    String preset = settings["vibLabelPreset_${vibId}"]?.toString()
    if (preset == "custom") {
        String customTxt = settings["vibCustomLabel_${vibId}"]?.toString()?.trim()
        if (customTxt) return "📳 ${customTxt}"
    } else if (preset && preset != "auto") {
        return preset
    }
    String pRole = getEffectiveVibrationPostureRole(vibDev)
    if (pRole == "laying") return "🛌 Laying in Bed"
    if (pRole == "standing") return "🧍 Standing at Mat / Counter"
    String lbl = (vibDev.displayName ?: "").toLowerCase()
    if (lbl.contains("recliner")) return "🪑 Sitting in Recliner"
    if (lbl.contains("couch") || lbl.contains("sofa")) return "🛋️ Sitting on Couch"
    if (lbl.contains("desk") || lbl.contains("office")) return "💻 Sitting at Desk Chair"
    return "🪑 Sitting in Chair"
}

boolean isVibrationDeviceCurrentlyActive(vibDev) {
    if (!vibDev) return false
    String accel = (vibDev.currentValue("acceleration") ?: "").toString().toLowerCase()
    String shock = (vibDev.currentValue("shock") ?: "").toString().toLowerCase()
    String motion = (vibDev.currentValue("motion") ?: "").toString().toLowerCase()
    return (accel == "active" || shock == "detected" || shock == "active" || motion == "active")
}

int fetchVibrationHoldMinutes(vibDev) {
    if (!vibDev) return 15
    String vibId = vibDev.id.toString()
    def customHold = settings["vibHoldMinutes_${vibId}"]
    if (customHold != null && customHold.toString().trim() != "") {
        return Math.max(2, Math.min(240, customHold.toInteger()))
    }
    String pRole = getEffectiveVibrationPostureRole(vibDev)
    return (pRole == "laying") ? 45 : 15
}

Map evaluateRoomVibrationTeacherStatus(String zoneName, long nowTs) {
    List roomVibDevs = buildVibrationZoneMap()[zoneName] ?: []
    if (!roomVibDevs) {
        return [
            sensorCount: 0,
            activeNow: false,
            recentlyActive: false,
            postureType: null,
            meaningLabel: null,
            activeSensorName: null,
            summaryText: null
        ]
    }
    Map lastVibMap = (atomicState.lastVibDeviceEpochMs ?: [:]) as Map
    boolean anyActiveNow = false
    boolean anyRecentlyActive = false
    String winningPosture = null
    String winningLabel = null
    String winningSensorName = null
    List<String> roleSummaries = []

    roomVibDevs.each { vibDev ->
        String vId = vibDev.id.toString()
        String pRole = getEffectiveVibrationPostureRole(vibDev)
        String mLabel = getVibrationMeaningLabel(vibDev)
        boolean isLiveActive = isVibrationDeviceCurrentlyActive(vibDev)
        long lastTriggerTs = safeLong(lastVibMap[vId], 0L)
        long holdWindowMs = (fetchVibrationHoldMinutes(vibDev) as long) * 60000L
        boolean isHoldingRecent = isLiveActive || (lastTriggerTs > 0L && (nowTs - lastTriggerTs) <= holdWindowMs)

        roleSummaries << (pRole == "laying" ? "🛌Bed" : (pRole == "standing" ? "🧍Mat" : "🪑Chair"))

        if (isLiveActive) {
            anyActiveNow = true
            anyRecentlyActive = true
            winningPosture = pRole
            winningLabel = mLabel
            winningSensorName = vibDev.displayName
        } else if (isHoldingRecent && !anyActiveNow) {
            anyRecentlyActive = true
            if (winningPosture == null || pRole == "laying") {
                winningPosture = pRole
                winningLabel = mLabel
                winningSensorName = vibDev.displayName
            }
        }
    }

    return [
        sensorCount: roomVibDevs.size(),
        activeNow: anyActiveNow,
        recentlyActive: anyRecentlyActive,
        postureType: winningPosture,
        meaningLabel: winningLabel,
        activeSensorName: winningSensorName,
        summaryText: roleSummaries.unique().join("+")
    ]
}

// ==============================================================================
// v7.3.0 7-DAY ROLLING LEARNING LEDGER, 30-DAY MONTHLY RE-EVALUATION & POSTURE ENGINE
// ==============================================================================

boolean isInteriorPirSensorByName(pirDev) {
    if (!pirDev) return false
    String lbl = (pirDev.displayName ?: "").toLowerCase()
    List interiorWords = ["interior", "back", "bed", "desk", "couch", "sofa", "shower", "tub", "closet", "deep", "inner", "chair", "table", "vanity"]
    return interiorWords.any { lbl.contains(it) }
}

boolean isEntrancePirSensorByName(pirDev) {
    if (!pirDev) return false
    String lbl = (pirDev.displayName ?: "").toLowerCase()
    List entryWords = ["door", "entry", "entrance", "portal", "hall", "foyer", "threshold", "front"]
    return entryWords.any { lbl.contains(it) }
}

String getEffectivePirPlacementRole(pirDev) {
    if (!pirDev) return "both"
    String pirId = pirDev.id.toString()
    String explicitRole = settings["pirPlacementRole_${pirId}"]?.toString()
    if (explicitRole && explicitRole != "auto") {
        return explicitRole
    }
    if (isInteriorPirSensorByName(pirDev) && !isEntrancePirSensorByName(pirDev)) {
        return "interior"
    }
    if (isEntrancePirSensorByName(pirDev)) {
        return "entrance"
    }
    return "both"
}

String getPirPlacementShortBadge(String pirRole) {
    if (pirRole == "entrance") return "🚪 Entrance / Doorway Teacher"
    if (pirRole == "interior") return "🛋️ Back-of-Room / Interior Teacher"
    return "🔄 Whole-Room (Entry + Hold + Exit) Teacher"
}

int fetchLearningWindowDays() {
    return (settings.learningWindowDays != null ? Math.max(3, Math.min(14, settings.learningWindowDays.toInteger())) : LEARNING_WINDOW_DAYS)
}

int fetchMonthlyReEvalDays() {
    return (settings.monthlyReEvalDays != null ? Math.max(14, Math.min(90, settings.monthlyReEvalDays.toInteger())) : RE_EVAL_CYCLE_DAYS)
}

Map getOrInitRoomTeacherModel(String zoneName) {
    ConcurrentHashMap<String, Object> teacherMap = getFastMap("pirTeacherModel")
    Map model = (teacherMap.get(zoneName) instanceof Map) ? new LinkedHashMap(teacherMap.get(zoneName) as Map) : [:]
    long nowTs = now()
    if (model.isEmpty()) {
        double baseStandDb = fetchMinShadowForStationary().doubleValue()
        double baseLayDb = fetchLayingShadowThreshold().doubleValue()
        double baseSitDb = Math.round(((baseStandDb * 0.55d) + (baseLayDb * 0.45d)) * 100.0d) / 100.0d
        model = [
            learningStartEpochMs: nowTs,
            lastGraduationEpochMs: 0L,
            nextReEvalEpochMs: nowTs + (fetchMonthlyReEvalDays() * 86400000L),
            learningCycleNumber: 1,
            inActiveLearningWindow: true,
            learningDaysLogged: [],
            dailyBuckets: [:],
            entrySamples: 0,
            occupiedSamples: 0,
            exitSamples: 0,
            walkingSamples: 0,
            standingSamples: 0,
            sittingSamples: 0,
            layingSamples: 0,
            vibrationSamples: 0,
            learnedEntryThresh: fetchEntryMotionThreshold(),
            learnedKeepThresh: fetchKeepAliveThreshold(),
            learnedEntryStepMs: 16.0d,
            learnedWalkShadowDb: Math.max(0.42d, baseStandDb + 0.12d),
            learnedStandShadowDb: baseStandDb,
            learnedSitShadowDb: baseSitDb,
            learnedLayShadowDb: baseLayDb,
            learnedSitVariance: 0.0028d,
            learnedLayVariance: 0.0014d,
            learnedStillShadowDb: baseStandDb,
            learnedExitClearSec: 180,
            learnedPortalSwitchId: null,
            learnedPortalSwitchName: null,
            learnedInteriorNodeId: null,
            learnedInteriorNodeName: null,
            learnedLastVibMeaning: null,
            portalHitCounts: [:],
            interiorHitCounts: [:],
            lastPirEntryMs: 0L,
            lastPirInteriorMs: 0L,
            lastPirClearMs: 0L,
            lastVibLearnMs: 0L,
            lastRfSelfLearnMs: 0L,
            graduated: false,
            lastTrainedStr: "Day 1 of 7 Started"
        ]
        teacherMap.put(zoneName, model)
    } else {
        if (model.learningStartEpochMs == null) model.learningStartEpochMs = nowTs
        if (model.learningDaysLogged == null) model.learningDaysLogged = []
        if (model.dailyBuckets == null) model.dailyBuckets = [:]
        if (model.learningCycleNumber == null) model.learningCycleNumber = 1
        if (model.vibrationSamples == null) model.vibrationSamples = 0
        if (model.learnedWalkShadowDb == null) model.learnedWalkShadowDb = 0.44d
        if (model.learnedStandShadowDb == null) model.learnedStandShadowDb = fetchMinShadowForStationary().doubleValue()
        if (model.learnedSitShadowDb == null) model.learnedSitShadowDb = 0.28d
        if (model.learnedLayShadowDb == null) model.learnedLayShadowDb = fetchLayingShadowThreshold().doubleValue()
        if (model.learnedSitVariance == null) model.learnedSitVariance = 0.0028d
        if (model.learnedLayVariance == null) model.learnedLayVariance = 0.0014d
    }
    return model
}

Map evaluateRoomLifecycleAndSchedule(String zoneName, Map model, long nowTs) {
    int targetLearnDays = fetchLearningWindowDays()
    int reEvalCycleDays = fetchMonthlyReEvalDays()
    long learnWindowMs = (targetLearnDays as long) * 86400000L
    long reEvalCycleMs = (reEvalCycleDays as long) * 86400000L

    long learnStartMs = safeLong(model.learningStartEpochMs, nowTs)
    long lastGradMs = safeLong(model.lastGraduationEpochMs, 0L)
    long nextReEvalMs = safeLong(model.nextReEvalEpochMs, learnStartMs + reEvalCycleMs)
    List<String> daysList = ((model.learningDaysLogged ?: []).toList()).collect { it.toString() }.unique()
    int distinctDaysCount = daysList.size()
    int totalSamples = safeInt(model.entrySamples, 0) + safeInt(model.occupiedSamples, 0) + safeInt(model.exitSamples, 0)

    boolean wasGraduated = (model.graduated == true)
    boolean windowElapsedByCalendar = ((nowTs - learnStartMs) >= learnWindowMs && totalSamples >= 10) || (distinctDaysCount >= targetLearnDays && totalSamples >= 12)

    if (wasGraduated && lastGradMs > 0L && nowTs >= nextReEvalMs) {
        model.inActiveLearningWindow = true
        model.graduated = false
        model.learningStartEpochMs = nowTs
        model.learningDaysLogged = [getCurrentDateKey()]
        model.learningCycleNumber = safeInt(model.learningCycleNumber, 1) + 1
        model.nextReEvalEpochMs = nowTs + reEvalCycleMs
        model.lastTrainedStr = "Monthly Re-Eval Started"
        logAction("🗓️ Monthly 30-Day Re-Evaluation started for '${zoneName}' (Cycle #${model.learningCycleNumber}) — logging 7 days of fresh RF & Posture telemetry.")
    } else if (!wasGraduated && windowElapsedByCalendar) {
        model.graduated = true
        model.inActiveLearningWindow = false
        model.lastGraduationEpochMs = nowTs
        model.nextReEvalEpochMs = nowTs + reEvalCycleMs
        model.lastTrainedStr = "Graduated (${distinctDaysCount}d Logged)"
        logAction("🎓 '${zoneName}' completed its ${targetLearnDays}-Day Learning Window (${totalSamples} samples) and Graduated! Next auto-re-evaluation in ${reEvalCycleDays} days.")
    } else if (!wasGraduated) {
        model.inActiveLearningWindow = true
    }

    return model
}

void recordDailyLearningBucket(Map model, String sampleType, double shadowDb, double variance, double stepMs, long nowTs) {
    String todayKey = getCurrentDateKey()
    List<String> daysLogged = ((model.learningDaysLogged ?: []).toList()).collect { it.toString() }
    if (!daysLogged.contains(todayKey)) {
        daysLogged << todayKey
    }
    int maxDaysToKeep = fetchLearningWindowDays()
    while (daysLogged.size() > maxDaysToKeep) {
        daysLogged.remove(0)
    }
    model.learningDaysLogged = daysLogged

    Map buckets = (model.dailyBuckets instanceof Map) ? new LinkedHashMap(model.dailyBuckets as Map) : [:]
    Map todayBucket = (buckets[todayKey] instanceof Map) ? new LinkedHashMap(buckets[todayKey] as Map) : [
        samples: 0,
        avgShadowDb: shadowDb,
        avgVariance: variance,
        avgStepMs: stepMs,
        postureHits: [:]
    ]
    int prevCnt = safeInt(todayBucket.samples, 0)
    todayBucket.samples = prevCnt + 1
    todayBucket.avgShadowDb = Math.round((((safeDouble(todayBucket.avgShadowDb, shadowDb) * prevCnt) + shadowDb) / (prevCnt + 1)) * 100.0d) / 100.0d
    todayBucket.avgVariance = Math.round((((safeDouble(todayBucket.avgVariance, variance) * prevCnt) + variance) / (prevCnt + 1)) * 10000.0d) / 10000.0d
    todayBucket.avgStepMs = Math.round((((safeDouble(todayBucket.avgStepMs, stepMs) * prevCnt) + stepMs) / (prevCnt + 1)) * 10.0d) / 10.0d

    Map pHits = (todayBucket.postureHits instanceof Map) ? new LinkedHashMap(todayBucket.postureHits as Map) : [:]
    pHits[sampleType] = safeInt(pHits[sampleType], 0) + 1
    todayBucket.postureHits = pHits
    buckets[todayKey] = todayBucket

    List<String> sortedKeys = buckets.keySet().collect { it.toString() }.sort()
    while (sortedKeys.size() > maxDaysToKeep) {
        String oldestKey = sortedKeys.remove(0)
        buckets.remove(oldestKey)
    }
    model.dailyBuckets = buckets
}

void saveRoomTeacherModel(String zoneName, Map model) {
    if (!zoneName || model == null) return
    long nowTs = now()
    model = evaluateRoomLifecycleAndSchedule(zoneName, model, nowTs)
    ConcurrentHashMap<String, Object> teacherMap = getFastMap("pirTeacherModel")
    teacherMap.put(zoneName, model)
}

boolean isPirTeacherEnabled() {
    return (settings.enablePirSupervisedLearning != false)
}

Map getRoomEffectiveLearnedThresholds(String zoneName) {
    int baseMot = fetchEntryMotionThreshold()
    int baseKeep = fetchKeepAliveThreshold()
    double baseStandDb = fetchMinShadowForStationary().doubleValue()
    double baseLayDb = fetchLayingShadowThreshold().doubleValue()
    double baseSitDb = Math.round(((baseStandDb * 0.55d) + (baseLayDb * 0.45d)) * 100.0d) / 100.0d

    if (!isPirTeacherEnabled()) {
        return [
            entryThresh: baseMot,
            keepThresh: baseKeep,
            walkShadowDb: Math.max(0.42d, baseStandDb + 0.12d),
            standShadowDb: baseStandDb,
            sitShadowDb: baseSitDb,
            layShadowDb: baseLayDb,
            sitVariance: 0.0028d,
            layVariance: 0.0014d,
            stillShadowDb: baseStandDb,
            entryStepGateMs: 16.0d,
            hasLearnedData: false,
            graduated: false,
            inActiveLearningWindow: false,
            daysLoggedCount: 0,
            daysUntilReEval: fetchMonthlyReEvalDays(),
            totalSamples: 0,
            vibrationSamples: 0,
            statusBadge: "⚪ Default RF"
        ]
    }

    long nowTs = now()
    Map model = getOrInitRoomTeacherModel(zoneName)
    model = evaluateRoomLifecycleAndSchedule(zoneName, model, nowTs)

    int entryCnt = safeInt(model.entrySamples, 0)
    int occCnt = safeInt(model.occupiedSamples, 0)
    int exitCnt = safeInt(model.exitSamples, 0)
    int totalCnt = entryCnt + occCnt + exitCnt
    int walkCnt = safeInt(model.walkingSamples, 0)
    int standCnt = safeInt(model.standingSamples, 0)
    int sitCnt = safeInt(model.sittingSamples, 0)
    int layCnt = safeInt(model.layingSamples, 0)
    int vibCnt = safeInt(model.vibrationSamples, 0)

    List<String> daysList = ((model.learningDaysLogged ?: []).toList()).collect { it.toString() }.unique()
    int daysLoggedCnt = daysList.size()
    int targetDays = fetchLearningWindowDays()
    boolean hasData = (totalCnt >= 3)
    boolean isGrad = (model.graduated == true)
    boolean isActivelyLearning = (model.inActiveLearningWindow != false) && !isGrad

    long nextReEvalMs = safeLong(model.nextReEvalEpochMs, nowTs + (fetchMonthlyReEvalDays() * 86400000L))
    int daysUntilReEval = Math.max(1, Math.ceil((nextReEvalMs - nowTs) / 86400000.0d).toInteger())
    int cycleNum = safeInt(model.learningCycleNumber, 1)

    int effEntry = hasData ? Math.max(12, Math.min(35, safeInt(model.learnedEntryThresh, baseMot))) : baseMot
    int effKeep = hasData ? Math.max(6, Math.min(effEntry - 3, safeInt(model.learnedKeepThresh, baseKeep))) : baseKeep
    double effStandDb = (occCnt >= 3) ? Math.max(0.24d, Math.min(0.55d, safeDouble(model.learnedStandShadowDb, baseStandDb))) : baseStandDb
    double effSitDb = (sitCnt >= 2 || occCnt >= 4) ? Math.max(0.20d, Math.min(effStandDb - 0.03d, safeDouble(model.learnedSitShadowDb, baseSitDb))) : baseSitDb
    double effLayDb = (layCnt >= 2 || occCnt >= 6) ? Math.max(0.16d, Math.min(effSitDb - 0.02d, safeDouble(model.learnedLayShadowDb, baseLayDb))) : baseLayDb
    double effWalkDb = Math.max(effStandDb + 0.08d, safeDouble(model.learnedWalkShadowDb, effStandDb + 0.12d))
    double effSitVar = Math.max(0.0015d, Math.min(0.0050d, safeDouble(model.learnedSitVariance, 0.0028d)))
    double effLayVar = Math.max(0.0008d, Math.min(effSitVar, safeDouble(model.learnedLayVariance, 0.0014d)))
    double effStepGate = (entryCnt >= 2) ? Math.max(12.0d, Math.min(28.0d, safeDouble(model.learnedEntryStepMs, 16.0d))) : 16.0d

    List roomPirs = (buildPirZoneMap()[zoneName] ?: [])
    List roomVibs = (buildVibrationZoneMap()[zoneName] ?: [])
    String modeLabel = (roomPirs && roomVibs) ? "PIR+Vib+RF" : (roomVibs ? "Vib+RF" : (roomPirs ? "PIR+RF" : "Self-RF"))

    String badge = "🧠 ${modeLabel} Day ${Math.max(1, daysLoggedCnt)}/${targetDays} (${totalCnt} Samples)"
    if (isGrad) {
        badge = "🎓 7-Day Graduated (${modeLabel} Cycle #${cycleNum} — Next Re-Eval in ${daysUntilReEval}d)"
    } else if (cycleNum > 1) {
        badge = "🔄 Monthly Re-Eval Day ${Math.max(1, daysLoggedCnt)}/${targetDays} (${totalCnt} Samples)"
    }

    return [
        entryThresh: effEntry,
        keepThresh: effKeep,
        walkShadowDb: Math.round(effWalkDb * 100.0d) / 100.0d,
        standShadowDb: Math.round(effStandDb * 100.0d) / 100.0d,
        sitShadowDb: Math.round(effSitDb * 100.0d) / 100.0d,
        layShadowDb: Math.round(effLayDb * 100.0d) / 100.0d,
        sitVariance: effSitVar,
        layVariance: effLayVar,
        stillShadowDb: Math.round(effSitDb * 100.0d) / 100.0d,
        entryStepGateMs: Math.round(effStepGate * 10.0d) / 10.0d,
        learnedPortalSwitchId: model.learnedPortalSwitchId,
        learnedPortalSwitchName: model.learnedPortalSwitchName,
        learnedInteriorNodeId: model.learnedInteriorNodeId,
        learnedInteriorNodeName: model.learnedInteriorNodeName,
        learnedLastVibMeaning: model.learnedLastVibMeaning,
        entrySamples: entryCnt,
        occupiedSamples: occCnt,
        exitSamples: exitCnt,
        walkingSamples: walkCnt,
        standingSamples: standCnt,
        sittingSamples: sitCnt,
        layingSamples: layCnt,
        vibrationSamples: vibCnt,
        totalSamples: totalCnt,
        daysLoggedCount: daysLoggedCnt,
        daysUntilReEval: daysUntilReEval,
        learningCycleNumber: cycleNum,
        hasLearnedData: hasData,
        graduated: isGrad,
        inActiveLearningWindow: isActivelyLearning,
        statusBadge: badge
    ]
}

// ==============================================================================
// v7.3.0 SUPERVISED PIR, VIBRATION/MULTIPURPOSE & SELF-SUPERVISED RF POSTURE LEARNING
// ==============================================================================

void trainPostureProfileSample(Map model, String postureType, double shadowDb, double variance, double stepMs, long nowTs) {
    if (shadowDb <= 0.12d && stepMs <= 1.5d) return
    double alpha = (model.inActiveLearningWindow != false && model.graduated != true) ? 0.20d : 0.04d

    if (postureType == "walking") {
        model.walkingSamples = safeInt(model.walkingSamples, 0) + 1
        double curW = safeDouble(model.learnedWalkShadowDb, 0.44d)
        double targetW = Math.max(0.32d, Math.min(1.20d, shadowDb))
        model.learnedWalkShadowDb = Math.round(((curW * (1.0d - alpha)) + (targetW * alpha)) * 100.0d) / 100.0d
    } else if (postureType == "standing") {
        model.standingSamples = safeInt(model.standingSamples, 0) + 1
        double curSt = safeDouble(model.learnedStandShadowDb, fetchMinShadowForStationary().doubleValue())
        double targetSt = Math.max(0.26d, Math.min(0.55d, shadowDb * 0.90d))
        model.learnedStandShadowDb = Math.round(((curSt * (1.0d - alpha)) + (targetSt * alpha)) * 100.0d) / 100.0d
    } else if (postureType == "sitting") {
        model.sittingSamples = safeInt(model.sittingSamples, 0) + 1
        double curSit = safeDouble(model.learnedSitShadowDb, 0.27d)
        double targetSit = Math.max(0.20d, Math.min(0.42d, shadowDb * 0.88d))
        model.learnedSitShadowDb = Math.round(((curSit * (1.0d - alpha)) + (targetSit * alpha)) * 100.0d) / 100.0d

        double curVar = safeDouble(model.learnedSitVariance, 0.0028d)
        double targetVar = Math.max(0.0012d, Math.min(0.0045d, variance))
        model.learnedSitVariance = Math.round(((curVar * (1.0d - alpha)) + (targetVar * alpha)) * 10000.0d) / 10000.0d
    } else if (postureType == "laying") {
        model.layingSamples = safeInt(model.layingSamples, 0) + 1
        double curLay = safeDouble(model.learnedLayShadowDb, fetchLayingShadowThreshold().doubleValue())
        double targetLay = Math.max(0.16d, Math.min(0.32d, shadowDb * 0.85d))
        model.learnedLayShadowDb = Math.round(((curLay * (1.0d - alpha)) + (targetLay * alpha)) * 100.0d) / 100.0d

        double curLVar = safeDouble(model.learnedLayVariance, 0.0014d)
        double targetLVar = Math.max(0.0006d, Math.min(0.0030d, variance))
        model.learnedLayVariance = Math.round(((curLVar * (1.0d - alpha)) + (targetLVar * alpha)) * 10000.0d) / 10000.0d
    }

    model.learnedStillShadowDb = model.learnedSitShadowDb
    recordDailyLearningBucket(model, postureType, shadowDb, variance, stepMs, nowTs)
}

void trainRoomRfFromVibrationEvent(vibDev, String evtName, String evtValue, long nowTs) {
    if (!isPirTeacherEnabled() || !vibDev) return
    String zoneName = getVibrationSensorZoneName(vibDev)
    String postureRole = getEffectiveVibrationPostureRole(vibDev)
    String meaningLabel = getVibrationMeaningLabel(vibDev)
    Map zones = buildZoneMap()
    List roomDevs = zones[zoneName] ?: []
    if (!roomDevs) return

    boolean isPositiveTrigger = (
        (evtName == "acceleration" && evtValue == "active") ||
        (evtName == "shock" && (evtValue == "detected" || evtValue == "active")) ||
        (evtName == "motion" && evtValue == "active") ||
        (evtName == "contact" && (evtValue == "closed" || evtValue == "open"))
    )
    if (!isPositiveTrigger) return

    ConcurrentHashMap<String, Object> pingSentMap = getFastMap("lastPingSent")
    ConcurrentHashMap<String, Object> nodeScoresMap = getFastMap("nodeScores")

    // Immediately ping up to 3 devices in the room to capture live RF shadow while person sits/lays on furniture
    roomDevs.take(3).eachWithIndex { dev, int idx ->
        if (idx > 0) pauseExecution(20)
        dispatchNonBlockingPing(dev, now(), pingSentMap)
    }

    Map model = getOrInitRoomTeacherModel(zoneName)
    model = evaluateRoomLifecycleAndSchedule(zoneName, model, nowTs)
    boolean inActiveWindow = (model.inActiveLearningWindow != false) && (model.graduated != true)
    double blendRate = inActiveWindow ? 0.25d : 0.05d
    String timeStr = new Date(nowTs).format("MM/dd hh:mm a", location.timeZone)

    // Cross-room handoff if single occupant sat down or laid down in this room
    Map prevZoneStatus = (atomicState.zoneStatus ?: [:]) as Map
    Map census = evaluateHouseholdPresenceCensus()
    int peopleHome = safeInt(census.peopleHomeNow, 1)
    prevZoneStatus.each { String otherZone, Map oz ->
        if (otherZone != zoneName && (oz.occupancyState in ["active_motion", "stationary_occupied", "active_motion_cooldown"])) {
            List otherPirs = (buildPirZoneMap()[otherZone] ?: [])
            boolean otherPirsAllInactive = otherPirs.isEmpty() || otherPirs.every { (it.currentValue("motion") ?: "inactive") == "inactive" }
            if (otherPirsAllInactive && (areZonesInCloseProximity(zoneName, otherZone) || peopleHome <= 1)) {
                if (peopleHome <= 1 || areZonesInCloseProximity(zoneName, otherZone)) {
                    clearDepartedZoneImmediately(otherZone, nowTs)
                }
            }
        }
    }

    def bestInteriorDev = null
    int bestCandScore = 0
    double bestShadowDb = 0.0d
    double bestVar = 0.0d
    double bestStepMs = 0.0d

    roomDevs.each { dev ->
        String devId = dev.id.toString()
        Map ns = (nodeScoresMap[devId] ?: [:]) as Map
        int candSc = Math.max(safeInt(ns.score, 0), safeInt(ns.candidateScore, 0))
        double stepJ = safeDouble(ns.netJitterMs, 0.0d)
        double shadDb = safeDouble(ns.shadowShift, 0.0d)
        double vr = safeDouble(ns.variance, 0.0d)
        if (candSc >= bestCandScore) {
            bestCandScore = candSc
            bestShadowDb = shadDb
            bestVar = vr
            bestStepMs = stepJ
            bestInteriorDev = dev
        }
    }

    if ((nowTs - safeLong(model.lastVibLearnMs, 0L)) > 30000L) {
        model.occupiedSamples = safeInt(model.occupiedSamples, 0) + 1
        model.vibrationSamples = safeInt(model.vibrationSamples, 0) + 1
        model.lastVibLearnMs = nowTs
        model.lastTrainedStr = timeStr
        model.learnedLastVibMeaning = meaningLabel

        if (bestCandScore >= 4) {
            int curKeep = safeInt(model.learnedKeepThresh, fetchKeepAliveThreshold())
            int targetKeep = Math.max(6, Math.min(16, Math.round(bestCandScore * 0.72d).toInteger()))
            model.learnedKeepThresh = Math.round((curKeep * (1.0d - blendRate)) + (targetKeep * blendRate)).toInteger()
        }

        double defaultPostureShadow = (postureRole == "laying") ? 0.22d : (postureRole == "standing" ? 0.32d : 0.26d)
        double sampleShadowToTrain = Math.max(defaultPostureShadow, bestShadowDb)
        double sampleVarToTrain = (bestVar > 0.0004d) ? bestVar : ((postureRole == "laying") ? 0.0014d : 0.0024d)
        trainPostureProfileSample(model, postureRole, sampleShadowToTrain, sampleVarToTrain, bestStepMs, nowTs)

        if (bestInteriorDev != null && bestCandScore >= 4 && !isClosetOrBackOfRoomSwitch(bestInteriorDev)) {
            Map iCounts = (model.interiorHitCounts instanceof Map) ? new LinkedHashMap(model.interiorHitCounts as Map) : [:]
            String iId = bestInteriorDev.id.toString()
            iCounts[iId] = safeInt(iCounts[iId], 0) + 1
            model.interiorHitCounts = iCounts
            def topIntEntry = iCounts.max { it.value }
            if (topIntEntry) {
                model.learnedInteriorNodeId = topIntEntry.key.toString()
                def matchedIntDev = roomDevs.find { it.id.toString() == topIntEntry.key.toString() }
                if (matchedIntDev) model.learnedInteriorNodeName = matchedIntDev.displayName
            }
        }

        saveRoomTeacherModel(zoneName, model)
        logAction("📳 7-Day Vibration Posture Teacher (${vibDev.displayName} -> ${meaningLabel}) trained '${postureRole.toUpperCase()}' in ${zoneName} (Sample #${model.vibrationSamples}, Keep=${model.learnedKeepThresh}%).")
    }
}

void clearDepartedZoneImmediately(String departedZone, long nowTs) {
    Map zStatus = (atomicState.zoneStatus ?: [:]) as Map
    Map lastMot = (atomicState.lastActiveMotionTime ?: [:]) as Map
    Map lastExit = (atomicState.lastZoneExitEpochMs ?: [:]) as Map
    Map stillStart = (atomicState.zoneStillStartMs ?: [:]) as Map
    Map slpStart = (atomicState.zoneSleepCandidateStartMs ?: [:]) as Map
    ConcurrentHashMap<String, Object> hitStreakMap = getFastMap("nodeHitStreaks")
    ConcurrentHashMap<String, Object> nodeScoresMap = getFastMap("nodeScores")

    lastMot.remove(departedZone)
    stillStart.remove(departedZone)
    slpStart.remove(departedZone)
    lastExit[departedZone] = nowTs

    (buildZoneMap()[departedZone] ?: []).each { d ->
        String dId = d.id.toString()
        hitStreakMap.put(dId, 0)
        Map ns = (nodeScoresMap[dId] ?: [:]) as Map
        if (ns) {
            ns.score = 0
            ns.candidateScore = 0
            ns.hitStreak = 0
            ns.shadowShift = 0.0d
            ns.netJitterMs = 0.0d
            nodeScoresMap[dId] = ns
        }
    }

    Map z = (zStatus[departedZone] ?: [:]) as Map
    if (z && !isZoneSleepSwitchOn(departedZone)) {
        z.peopleCount = 0
        z.motion = "inactive"
        z.presence = "not present"
        z.occupancyState = "empty"
        z.posture = "Empty"
        z.score = 0
        z.cooldownRemaining = 0
        zStatus[departedZone] = z
        updateRecentRoomStatus(departedZone, "Empty", false, nowTs)
    }

    atomicState.lastActiveMotionTime = lastMot
    atomicState.lastZoneExitEpochMs = lastExit
    atomicState.zoneStillStartMs = stillStart
    atomicState.zoneSleepCandidateStartMs = slpStart
    atomicState.zoneStatus = zStatus
}

void trainRoomRfFromPirEvent(pirDev, String evtValue, long nowTs) {
    if (!isPirTeacherEnabled() || !pirDev) return
    String zoneName = getMotionSensorZoneName(pirDev)
    String pirRole = getEffectivePirPlacementRole(pirDev)
    Map zones = buildZoneMap()
    List roomDevs = zones[zoneName] ?: []
    if (!roomDevs) return

    ConcurrentHashMap<String, Object> pingSentMap = getFastMap("lastPingSent")
    ConcurrentHashMap<String, Object> nodeScoresMap = getFastMap("nodeScores")
    Map prevZoneStatus = (atomicState.zoneStatus ?: [:]) as Map
    Map prevZ = (prevZoneStatus[zoneName] ?: [:]) as Map
    boolean roomWasEmpty = !(prevZ.occupancyState in ["active_motion", "stationary_occupied", "sleeping_occupied"])

    roomDevs.take(3).eachWithIndex { dev, int idx ->
        if (idx > 0) pauseExecution(20)
        dispatchNonBlockingPing(dev, now(), pingSentMap)
    }

    Map model = getOrInitRoomTeacherModel(zoneName)
    model = evaluateRoomLifecycleAndSchedule(zoneName, model, nowTs)
    boolean inActiveWindow = (model.inActiveLearningWindow != false) && (model.graduated != true)
    double blendRate = inActiveWindow ? 0.25d : 0.05d
    String timeStr = new Date(nowTs).format("MM/dd hh:mm a", location.timeZone)

    if (evtValue == "active") {
        // Cross-room Exit Handoff Learning AND Immediate Departure Clearing!
        Map census = evaluateHouseholdPresenceCensus()
        int peopleHome = safeInt(census.peopleHomeNow, 1)
        prevZoneStatus.each { String otherZone, Map oz ->
            if (otherZone != zoneName && (oz.occupancyState in ["active_motion", "stationary_occupied", "active_motion_cooldown"])) {
                List otherPirs = (buildPirZoneMap()[otherZone] ?: [])
                boolean otherPirsAllInactive = otherPirs.isEmpty() || otherPirs.every { (it.currentValue("motion") ?: "inactive") == "inactive" }
                if (otherPirsAllInactive && (areZonesInCloseProximity(zoneName, otherZone) || peopleHome <= 1)) {
                    Map otherModel = getOrInitRoomTeacherModel(otherZone)
                    if ((nowTs - safeLong(otherModel.lastPirClearMs, 0L)) > 30000L) {
                        otherModel.exitSamples = safeInt(otherModel.exitSamples, 0) + 1
                        otherModel.lastPirClearMs = nowTs
                        otherModel.lastTrainedStr = timeStr
                        saveRoomTeacherModel(otherZone, otherModel)
                        if (inActiveWindow) {
                            logAction("🎓 7-Day PIR Exit Handoff & Clear: ${otherZone} -> ${zoneName} (Cleared ${otherZone} immediately).")
                        }
                    }
                    // Immediately clear the departed room when 1 person is home or transitioning from an adjacent room!
                    if (peopleHome <= 1 || areZonesInCloseProximity(zoneName, otherZone)) {
                        clearDepartedZoneImmediately(otherZone, nowTs)
                    }
                }
            }
        }

        def bestDoorDev = null
        int bestDoorCandScore = 0
        double bestDoorStepMs = 0.0d
        double bestDoorShadowDb = 0.0d
        double bestDoorVar = 0.0d
        def bestInteriorDev = null
        int bestInteriorCandScore = 0
        double bestInteriorShadowDb = 0.0d
        double bestInteriorVar = 0.0d

        roomDevs.each { dev ->
            String devId = dev.id.toString()
            Map ns = (nodeScoresMap[devId] ?: [:]) as Map
            int candSc = Math.max(safeInt(ns.score, 0), safeInt(ns.candidateScore, 0))
            double stepJ = safeDouble(ns.netJitterMs, 0.0d)
            double shadDb = safeDouble(ns.shadowShift, 0.0d)
            double vr = safeDouble(ns.variance, 0.0d)
            String pRole = getEffectiveDevicePortalRole(dev, roomDevs)

            if (!isInteriorHoldRole(pRole) && candSc >= bestDoorCandScore) {
                bestDoorCandScore = candSc
                bestDoorStepMs = stepJ
                bestDoorShadowDb = shadDb
                bestDoorVar = vr
                bestDoorDev = dev
            }
            if ((isInteriorHoldRole(pRole) || isSmartPlugOrApplianceNode(dev) || roomDevs.size() == 1) && candSc >= bestInteriorCandScore) {
                bestInteriorCandScore = candSc
                bestInteriorShadowDb = shadDb
                bestInteriorVar = vr
                bestInteriorDev = dev
            }
        }

        if (pirRole in ["entrance", "both"] && (roomWasEmpty || (nowTs - safeLong(model.lastPirEntryMs, 0L)) > 60000L)) {
            model.entrySamples = safeInt(model.entrySamples, 0) + 1
            model.lastPirEntryMs = nowTs
            model.lastTrainedStr = timeStr

            if (bestDoorCandScore >= 6) {
                int curLearnedEntry = safeInt(model.learnedEntryThresh, fetchEntryMotionThreshold())
                int targetEntryThresh = Math.max(12, Math.min(26, Math.round(bestDoorCandScore * 0.78d).toInteger()))
                model.learnedEntryThresh = Math.round((curLearnedEntry * (1.0d - blendRate)) + (targetEntryThresh * blendRate)).toInteger()
            }
            if (bestDoorStepMs >= 4.0d) {
                double curStep = safeDouble(model.learnedEntryStepMs, 16.0d)
                double targetStep = Math.max(12.0d, Math.min(24.0d, bestDoorStepMs * 0.85d))
                model.learnedEntryStepMs = Math.round(((curStep * (1.0d - blendRate)) + (targetStep * blendRate)) * 10.0d) / 10.0d
            }
            if (bestDoorDev != null && bestDoorCandScore >= 6) {
                Map pCounts = (model.portalHitCounts instanceof Map) ? new LinkedHashMap(model.portalHitCounts as Map) : [:]
                String dId = bestDoorDev.id.toString()
                pCounts[dId] = safeInt(pCounts[dId], 0) + 1
                model.portalHitCounts = pCounts
                def topPortalEntry = pCounts.max { it.value }
                if (topPortalEntry) {
                    model.learnedPortalSwitchId = topPortalEntry.key.toString()
                    def matchedDev = roomDevs.find { it.id.toString() == topPortalEntry.key.toString() }
                    if (matchedDev) model.learnedPortalSwitchName = matchedDev.displayName
                }
            }
            trainPostureProfileSample(model, "walking", Math.max(0.36d, bestDoorShadowDb), bestDoorVar, bestDoorStepMs, nowTs)
            if (inActiveWindow) {
                logAction("🎓 7-Day Entrance Teacher (${pirDev.displayName}) logged ${zoneName} Entry #${model.entrySamples} (Day ${model.learningDaysLogged?.size() ?: 1}/7): Trig=${model.learnedEntryThresh}%, Portal=${model.learnedPortalSwitchName ?: (bestDoorDev?.displayName ?: 'Auto')}.")
            }
        }

        if ((pirRole in ["interior", "both"]) && (nowTs - safeLong(model.lastPirInteriorMs, 0L)) > 60000L) {
            model.occupiedSamples = safeInt(model.occupiedSamples, 0) + 1
            model.lastPirInteriorMs = nowTs
            model.lastTrainedStr = timeStr

            if (bestInteriorCandScore >= 4) {
                int curKeep = safeInt(model.learnedKeepThresh, fetchKeepAliveThreshold())
                int targetKeep = Math.max(6, Math.min(16, Math.round(bestInteriorCandScore * 0.72d).toInteger()))
                model.learnedKeepThresh = Math.round((curKeep * (1.0d - blendRate)) + (targetKeep * blendRate)).toInteger()
            }
            if (bestInteriorShadowDb >= 0.16d) {
                String inferredPosture = (bestDoorStepMs >= 8.0d) ? "standing" : "sitting"
                trainPostureProfileSample(model, inferredPosture, bestInteriorShadowDb, bestInteriorVar, bestDoorStepMs, nowTs)
            }
            if (bestInteriorDev != null && bestInteriorCandScore >= 4 && !isClosetOrBackOfRoomSwitch(bestInteriorDev)) {
                Map iCounts = (model.interiorHitCounts instanceof Map) ? new LinkedHashMap(model.interiorHitCounts as Map) : [:]
                String iId = bestInteriorDev.id.toString()
                iCounts[iId] = safeInt(iCounts[iId], 0) + 1
                model.interiorHitCounts = iCounts
                def topIntEntry = iCounts.max { it.value }
                if (topIntEntry) {
                    model.learnedInteriorNodeId = topIntEntry.key.toString()
                    def matchedIntDev = roomDevs.find { it.id.toString() == topIntEntry.key.toString() }
                    if (matchedIntDev) model.learnedInteriorNodeName = matchedIntDev.displayName
                }
            }
        }
        saveRoomTeacherModel(zoneName, model)
    } else if (evtValue == "inactive") {
        List roomPirs = (buildPirZoneMap()[zoneName] ?: [])
        boolean allRoomPirsInactive = roomPirs.every { (it.currentValue("motion") ?: "inactive") == "inactive" }
        if (allRoomPirsInactive && (nowTs - safeLong(model.lastPirClearMs, 0L)) > 45000L) {
            model.exitSamples = safeInt(model.exitSamples, 0) + 1
            model.lastPirClearMs = nowTs
            model.lastTrainedStr = timeStr
            saveRoomTeacherModel(zoneName, model)
        }
    }
}

void updateContinuousPirAndSelfRfTraining(String zoneName, List roomDevs, List roomPirSensors, Map vibStatus, Map allNodeScores, boolean wasOccupied, long stillElapsedMs, boolean sleepSwitchOn, long nowTs) {
    if (!isPirTeacherEnabled() || !roomDevs) return
    Map model = getOrInitRoomTeacherModel(zoneName)
    model = evaluateRoomLifecycleAndSchedule(zoneName, model, nowTs)

    // Throttle continuous posture training to once every 60 seconds per room so sample counts stay realistic!
    long lastTrainTs = Math.max(Math.max(safeLong(model.lastPirInteriorMs, 0L), safeLong(model.lastRfSelfLearnMs, 0L)), safeLong(model.lastVibLearnMs, 0L))
    if ((nowTs - lastTrainTs) < 60000L) return

    List activePirs = (roomPirSensors ?: []).findAll { (it.currentValue("motion") ?: "inactive") == "active" }
    boolean hasPirInRoom = (roomPirSensors != null && !roomPirSensors.isEmpty())
    boolean hasInteriorPirActive = activePirs.any { getEffectivePirPlacementRole(it) in ["interior", "both"] }
    boolean hasEntrancePirOnly = !hasInteriorPirActive && activePirs.any { getEffectivePirPlacementRole(it) == "entrance" }
    boolean vibRecentlyActive = (vibStatus?.recentlyActive == true)
    String vibPostureType = vibStatus?.postureType?.toString()

    int peakCandSc = 0
    double peakShad = 0.0d
    double peakStepMs = 0.0d
    double peakVar = 0.0d
    int corroboratingNodes = 0
    def strongestNode = null

    roomDevs.each { dev ->
        Map ns = (allNodeScores[dev.id.toString()] ?: [:]) as Map
        int cSc = safeInt(ns.score, 0)
        double sh = safeDouble(ns.shadowShift, 0.0d)
        double stMs = safeDouble(ns.netJitterMs, 0.0d)
        double vr = safeDouble(ns.variance, 0.0d)
        if (cSc >= 8 && stMs >= 2.0d) corroboratingNodes++
        if (cSc > peakCandSc) {
            peakCandSc = cSc
            strongestNode = dev
        }
        if (sh > peakShad) peakShad = sh
        if (stMs > peakStepMs) peakStepMs = stMs
        if (vr > peakVar) peakVar = vr
    }

    double blendRate = (model.inActiveLearningWindow != false && model.graduated != true) ? 0.20d : 0.04d

    if (vibRecentlyActive && vibPostureType) {
        model.occupiedSamples = safeInt(model.occupiedSamples, 0) + 1
        model.vibrationSamples = safeInt(model.vibrationSamples, 0) + 1
        model.lastVibLearnMs = nowTs
        model.lastTrainedStr = new Date(nowTs).format("MM/dd hh:mm a", location.timeZone)
        if (vibStatus?.meaningLabel) {
            model.learnedLastVibMeaning = vibStatus.meaningLabel.toString()
        }
        if (peakCandSc >= 4) {
            int curKeep = safeInt(model.learnedKeepThresh, fetchKeepAliveThreshold())
            int targetKeep = Math.max(6, Math.min(16, Math.round(peakCandSc * 0.72d).toInteger()))
            model.learnedKeepThresh = Math.round((curKeep * (1.0d - blendRate)) + (targetKeep * blendRate)).toInteger()
        }
        double fallbackShad = (vibPostureType == "laying") ? 0.22d : 0.26d
        trainPostureProfileSample(model, vibPostureType, Math.max(fallbackShad, peakShad), peakVar, peakStepMs, nowTs)
        saveRoomTeacherModel(zoneName, model)
    } else if (activePirs || (hasPirInRoom && wasOccupied && peakShad >= 0.18d)) {
        model.occupiedSamples = safeInt(model.occupiedSamples, 0) + 1
        model.lastPirInteriorMs = nowTs
        model.lastTrainedStr = new Date(nowTs).format("MM/dd hh:mm a", location.timeZone)

        if (peakCandSc >= 4) {
            int curKeep = safeInt(model.learnedKeepThresh, fetchKeepAliveThreshold())
            int targetKeep = Math.max(6, Math.min(16, Math.round(peakCandSc * 0.72d).toInteger()))
            model.learnedKeepThresh = Math.round((curKeep * (1.0d - blendRate)) + (targetKeep * blendRate)).toInteger()
        }

        String learnedPostureType = "standing"
        if (sleepSwitchOn || (stillElapsedMs >= 180000L && peakVar <= 0.0035d && isSleepAllowedInZone(zoneName))) {
            learnedPostureType = "laying"
        } else if (hasEntrancePirOnly && peakStepMs >= 9.0d) {
            learnedPostureType = "walking"
        } else if (hasInteriorPirActive && peakStepMs < 7.0d) {
            learnedPostureType = "sitting"
        } else if (!activePirs && wasOccupied) {
            learnedPostureType = (stillElapsedMs >= 180000L && isSleepAllowedInZone(zoneName)) ? "laying" : "sitting"
        }

        trainPostureProfileSample(model, learnedPostureType, Math.max(0.18d, peakShad), peakVar, peakStepMs, nowTs)
        saveRoomTeacherModel(zoneName, model)
    } else if (!hasPirInRoom && wasOccupied && (corroboratingNodes >= 1 || sleepSwitchOn) && peakShad >= 0.18d) {
        model.occupiedSamples = safeInt(model.occupiedSamples, 0) + 1
        model.lastRfSelfLearnMs = nowTs
        model.lastTrainedStr = new Date(nowTs).format("MM/dd hh:mm a", location.timeZone)

        String selfPosture = "standing"
        if (sleepSwitchOn || (stillElapsedMs >= 240000L && peakVar <= 0.0030d && isSleepAllowedInZone(zoneName))) {
            selfPosture = "laying"
        } else if (stillElapsedMs >= 20000L && peakStepMs < 6.0d) {
            selfPosture = "sitting"
        } else if (peakStepMs >= 10.0d) {
            selfPosture = "walking"
        }
        trainPostureProfileSample(model, selfPosture, peakShad, peakVar, peakStepMs, nowTs)
        saveRoomTeacherModel(zoneName, model)
    }
}

// ==============================================================================
// ROOM ASLEEP VIRTUAL SWITCHES & CLOSE-PROXIMITY ADJACENCY MATRIX HELPERS
// ==============================================================================

def getZoneSleepSwitchDevice(String zoneName) {
    String zKey = sanitizeZoneKey(zoneName)
    return settings["zoneSleepSwitch_${zKey}"]
}

boolean isZoneSleepSwitchOn(String zoneName) {
    def sw = getZoneSleepSwitchDevice(zoneName)
    if (!sw) return false
    return (sw.currentValue("switch") ?: "off").toString().toLowerCase() == "on"
}

List getAllConfiguredSleepSwitches() {
    List swList = []
    buildZoneMap().keySet().each { String zName ->
        def sw = getZoneSleepSwitchDevice(zName)
        if (sw) swList << sw
    }
    return swList
}

int countActiveRoomSleepSwitches() {
    int cnt = 0
    buildZoneMap().keySet().each { String zName ->
        if (isZoneSleepSwitchOn(zName)) cnt++
    }
    return cnt
}

List<String> getDefaultAdjacentZones(String zoneName, Set<String> allZones) {
    String lower = (zoneName ?: "").toLowerCase()
    List<String> defaults = []
    allZones.each { String other ->
        if (other != zoneName) {
            String oLower = other.toLowerCase()
            if ((lower.contains("master") && oLower.contains("master")) ||
                (lower.contains("kitchen") && (oLower.contains("living") || oLower.contains("dining") || oLower.contains("laundry") || oLower.contains("hall"))) ||
                (lower.contains("living") && (oLower.contains("kitchen") || oLower.contains("dining") || oLower.contains("office") || oLower.contains("hall") || oLower.contains("foyer") || oLower.contains("master bedroom") || oLower.contains("leanne"))) ||
                (lower.contains("dining") && (oLower.contains("kitchen") || oLower.contains("living"))) ||
                (lower.contains("office") && (oLower.contains("living") || oLower.contains("hall") || oLower.contains("foyer"))) ||
                (lower.contains("leanne") && (oLower.contains("living") || oLower.contains("hall") || oLower.contains("guest") || oLower.contains("bath"))) ||
                (isClosetRoomName(zoneName) && inferParentRoomForSubZone(zoneName, allZones) == other) ||
                (isClosetRoomName(other) && inferParentRoomForSubZone(other, allZones) == zoneName)) {
                defaults << other
            }
        }
    }
    return defaults.unique()
}

Set<String> getCloseProximityRoomsForZone(String zoneName, Set<String> allZones = null) {
    if (allZones == null) {
        allZones = buildZoneMap().keySet().collect { it.toString() } as Set
    }
    Set<String> neighbors = [] as Set

    String zKey = sanitizeZoneKey(zoneName)
    def configured = settings["adjacentZones_${zKey}"]
    if (configured != null) {
        if (configured instanceof Collection) {
            configured.each { if (it) neighbors << it.toString() }
        } else if (configured.toString().trim()) {
            neighbors << configured.toString().trim()
        }
    } else {
        getDefaultAdjacentZones(zoneName, allZones).each { neighbors << it }
    }

    allZones.each { String otherZone ->
        if (otherZone != zoneName) {
            String oKey = sanitizeZoneKey(otherZone)
            def otherConfig = settings["adjacentZones_${oKey}"]
            if (otherConfig != null) {
                List<String> oList = (otherConfig instanceof Collection) ? otherConfig.collect { it.toString() } : [otherConfig.toString()]
                if (oList.contains(zoneName)) {
                    neighbors << otherZone
                }
            }
        }
    }

    return neighbors
}

boolean areZonesInCloseProximity(String zoneA, String zoneB, Set<String> allZones = null) {
    if (!zoneA || !zoneB) return false
    if (zoneA == zoneB) return true
    Set<String> proxA = getCloseProximityRoomsForZone(zoneA, allZones)
    return proxA.contains(zoneB)
}

// ==============================================================================
// v7.3.0 CHILD PRESENCE SENSOR ONLY & HOUSEHOLD CENSUS ENGINE
// ==============================================================================

boolean isHubCurrentlyInAwayMode() {
    String curMode = (location?.mode ?: "Home").toString().trim()
    if (settings.awayModes) {
        List<String> configuredAway = ((settings.awayModes ?: []).toList()).collect { it.toString().trim().toLowerCase() }
        if (configuredAway.contains(curMode.toLowerCase())) return true
    }
    return curMode.toLowerCase().contains("away")
}

int fetchMinPeopleHomeWhenNotAway() {
    return (settings.minPeopleHomeDefault != null ? Math.max(1, Math.min(10, settings.minPeopleHomeDefault.toInteger())) : 1)
}

List getPresentChildPresenceSensors() {
    List childList = (childPresenceSensors ?: []).toList()
    return childList.findAll { (it.currentValue("presence") ?: "not present") == "present" }
}

int getPresentChildCountNow() {
    return getPresentChildPresenceSensors().size()
}

String getChildCoPresenceTriggerSource() {
    List presentChildDevs = getPresentChildPresenceSensors()
    if (!presentChildDevs) return null
    String names = presentChildDevs.collect { it.displayName }.join(", ")
    return "👧 Child Sensor Present (${names})"
}

Map evaluateHouseholdPresenceCensus() {
    long nowTs = now()
    String curMode = (location?.mode ?: "Home").toString()
    boolean isAway = isHubCurrentlyInAwayMode()
    int activeSleepSwitches = countActiveRoomSleepSwitches()

    long lastPhysicalOverrideTs = safeLong(atomicState.lastPhysicalArrivalGraceMs, 0L)
    boolean arrivalGraceActive = (lastPhysicalOverrideTs > 0L) && ((nowTs - lastPhysicalOverrideTs) <= 300000L)

    int configuredMaxCeiling = fetchKnownHousePeopleMax()
    int fallbackWhenNoSensors = (!isAway || arrivalGraceActive) ? fetchMinPeopleHomeWhenNotAway() : 0

    List childPresList = (childPresenceSensors ?: []).toList()
    List presentChildDevs = getPresentChildPresenceSensors()
    int presentChildrenCnt = presentChildDevs.size()
    String childTriggerSource = getChildCoPresenceTriggerSource()
    boolean childSensorActiveNow = (presentChildrenCnt > 0)

    List adultPresList = (presenceSensors ?: []).toList()
    if (adultPresList || childPresList) {
        List presentAdultDevs = adultPresList.findAll { (it.currentValue("presence") ?: "not present") == "present" }
        int presentAdultsCnt = presentAdultDevs.size()

        int baseAdultsHome = adultPresList ?
            ((presentAdultsCnt > 0) ? Math.max(activeSleepSwitches, presentAdultsCnt) : (presentChildrenCnt > 0 ? 0 : Math.max(activeSleepSwitches, fallbackWhenNoSensors))) :
            Math.max(activeSleepSwitches, fallbackWhenNoSensors)

        int totalRawPresent = baseAdultsHome + presentChildrenCnt
        int effectivePeopleHome = (totalRawPresent > 0) ? Math.min(configuredMaxCeiling, totalRawPresent) : 0

        String childTag = (presentChildrenCnt > 0) ? " + 👧 ${presentChildrenCnt} Child (${childTriggerSource})" : (childPresList ? " (👧 Child Not Present)" : "")
        String reason = ""
        if (adultPresList) {
            if (presentAdultsCnt > 0 || presentChildrenCnt > 0) {
                reason = "📱 ${presentAdultsCnt}/${adultPresList.size()} Adult(s)${childTag} -> Max ${effectivePeopleHome} Room(s)"
            } else if (activeSleepSwitches > 0) {
                reason = "🛌 Room Sleep Switch ON${childTag} -> Max ${effectivePeopleHome} Room(s)"
            } else if (isAway && !arrivalGraceActive) {
                reason = "🚗 Mode '${curMode}' & 0 Sensors Present (0 Home)"
            } else {
                reason = "🏠 Fallback (${effectivePeopleHome} Home${childTag} -> Max ${effectivePeopleHome} Room)"
            }
        } else {
            reason = "🏠 ${baseAdultsHome} Adult(s)${childTag} -> Max ${effectivePeopleHome} Room(s)"
        }

        return [
            peopleHomeNow: effectivePeopleHome,
            adultSensorsPresent: presentAdultsCnt,
            assumedChildrenNow: presentChildrenCnt,
            childScheduleActive: childSensorActiveNow,
            childSensorPresent: childSensorActiveNow,
            childTriggerSource: childTriggerSource,
            maxSimultaneousRooms: Math.max(1, effectivePeopleHome),
            isAwayMode: isAway && !arrivalGraceActive && activeSleepSwitches == 0 && presentAdultsCnt == 0 && presentChildrenCnt == 0,
            sensorCount: adultPresList.size() + childPresList.size(),
            presentSensorCount: presentAdultsCnt + presentChildrenCnt,
            reason: reason
        ]
    }

    if (isAway && !arrivalGraceActive && activeSleepSwitches == 0) {
        return [
            peopleHomeNow: 0,
            adultSensorsPresent: 0,
            assumedChildrenNow: 0,
            childScheduleActive: false,
            childSensorPresent: false,
            childTriggerSource: null,
            maxSimultaneousRooms: 1,
            isAwayMode: true,
            sensorCount: 0,
            presentSensorCount: 0,
            reason: "🚗 Hub Mode '${curMode}' is Away (0 Home)"
        ]
    }

    int basePeople = Math.max(activeSleepSwitches, fallbackWhenNoSensors)
    int autoPeople = Math.min(configuredMaxCeiling, basePeople)
    String modeReason = activeSleepSwitches > 0 ?
        "🛌 ${activeSleepSwitches} Room Sleep Switch(es) ON (Max ${autoPeople} Rooms)" :
        "🏠 Active Household Cap: ${autoPeople} Home (Max ${autoPeople} Room${autoPeople > 1 ? 's' : ''})"
    return [
        peopleHomeNow: autoPeople,
        adultSensorsPresent: basePeople,
        assumedChildrenNow: 0,
        childScheduleActive: false,
        childSensorPresent: false,
        childTriggerSource: null,
        maxSimultaneousRooms: Math.max(1, autoPeople),
        isAwayMode: false,
        sensorCount: 0,
        presentSensorCount: 0,
        reason: modeReason
    ]
}

// ==============================================================================
// MAIN BMS PAGE (INSTANT NON-BLOCKING RENDER FROM FAST RAM CACHE)
// ==============================================================================

def mainPage() {
    autoMigrateThresholdsOnce()
    checkDailyMidnightRollover()

    if ((zwaveAnchors || zigbeeAnchors) && !atomicState.initializedOnce) {
        initialize()
    }

    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {

        section("") {
            String statusExplanation = getHumanReadableStatus()
            String decisionReasoning = getLiveDecisionBreakdown()

            paragraph """
            <div style='background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); color:white; padding:14px 16px; border-radius:8px; margin-bottom:10px; box-shadow: 0 2px 5px rgba(0,0,0,0.15);'>
                <div style='font-size:18px; font-weight:bold; letter-spacing:0.3px;'>📡 Advanced RF Tomography Manager <span style='font-size:12px; background:#0284c7; padding:2px 8px; border-radius:10px; vertical-align:middle;'>v7.3.0 Vibration/Multipurpose Posture Teacher + Child Presence Sensor + Anti-Teleport</span></div>
                <div style='margin-top:8px; font-size:13px; background:rgba(255,255,255,0.96); color:#1e293b; padding:10px 12px; border-radius:6px; border-left:5px solid #0284c7; line-height:1.5;'>
                    <b>📡 Live House Status:</b> ${statusExplanation}
                </div>
                <div style='margin-top:6px; font-size:12.5px; background:rgba(241,245,249,0.95); color:#334155; padding:8px 12px; border-radius:6px; border-left:5px solid #10b981; line-height:1.45;'>
                    <b>🧠 Room-by-Room Summary:</b><br>${decisionReasoning}
                </div>
            </div>
            """

            if (atomicState.lastMgmtMessage) {
                paragraph """
                <div style='background-color:#ecfdf5; border:1px solid #6ee7b7; border-left:5px solid #10b981; padding:10px 12px; border-radius:6px; font-size:13px; color:#065f46; margin-bottom:10px;'>
                    <b>✅ Action Completed:</b> ${atomicState.lastMgmtMessage}
                </div>
                """
                atomicState.lastMgmtMessage = null
            }

            input "btnRefresh", "button", title: "🔄 Refresh & Ping Now", width: 4
            input "btnCalibrateAll", "button", title: "🎯 Re-Zero Empty Rooms Now", width: 4
            href "zoneAssignmentPage", title: "🚪📳 Configure PIR, Vibration & Chair/Bed Labels", description: "Configure Motion Sensors, Chair/Bed Vibration Sensors & Portal Switches", width: 4

            List allDevs = getAllAnchorDevices()
            if (allDevs) {
                Map zoneStatus = atomicState.zoneStatus ?: [:]
                Map dailyStats = getFastMap("dailyStats")
                Map houseSummary = atomicState.houseSummary ?: [:]

                int activeMotionCount = 0
                int stationaryCount = 0
                int sleepingZonesCount = 0
                int cooldownCount = 0
                int peakZoneScore = 0
                String peakZoneName = "None"
                int graduatedRoomsCount = 0
                int learningRoomsCount = 0

                zoneStatus.each { String zName, Map z ->
                    int sc = safeInt(z.score, 0)
                    if (sc > peakZoneScore) {
                        peakZoneScore = sc
                        peakZoneName = zName
                    }
                    if (z.occupancyState == "active_motion") activeMotionCount++
                    else if (z.occupancyState == "sleeping_occupied") { stationaryCount++; sleepingZonesCount++ }
                    else if (z.occupancyState in ["stationary_occupied", "pet_occupied"]) stationaryCount++
                    else if (z.occupancyState == "active_motion_cooldown") cooldownCount++

                    Map tInfo = getRoomEffectiveLearnedThresholds(zName)
                    if (tInfo.graduated) graduatedRoomsCount++
                    else learningRoomsCount++
                }

                int totalPeopleDetected = safeInt(houseSummary.detectedPeople, 0)
                int peopleHomeNow = safeInt(houseSummary.peopleHomeNow, 1)
                int maxActiveRooms = safeInt(houseSummary.maxSimultaneousRooms, Math.max(1, peopleHomeNow))
                int totalSleepingPeople = safeInt(houseSummary.sleepingPeople, 0)
                String censusModeLabel = (houseSummary.censusReason ?: "Max 1 Active Room").toString()
                String closestGlobalNode = houseSummary.closestSwitchName ?: "None"
                String closestGlobalDist = houseSummary.closestSwitchDistFt != null ? "${houseSummary.closestSwitchDistFt} ft" : "No One Near Switches"

                int doorwaySwitchCount = allDevs.count { getEffectiveDevicePortalRole(it) in ["both", "entrance_only", "exit_only"] }
                int interiorNodeCount = allDevs.count { getEffectiveDevicePortalRole(it) == "interior" }
                int pirAssistCount = (motionAssistSensors ?: []).size()
                int vibAssistCount = getAllVibrationAssistDevices().size()

                String teacherSummarySub = "100% Pure RF Self-Learning"
                if (pirAssistCount > 0 && vibAssistCount > 0) {
                    teacherSummarySub = "${pirAssistCount} PIR + ${vibAssistCount} Vib/Chair Teacher(s)"
                } else if (vibAssistCount > 0) {
                    teacherSummarySub = "${vibAssistCount} Vib/Chair Teacher(s) + RF"
                } else if (pirAssistCount > 0) {
                    teacherSummarySub = "${pirAssistCount} Optional PIR(s) + RF"
                }

                String sysBadge = uiBadge("🟢 ALL ROOMS EMPTY", "#16a34a")
                String sysSub = "Hub Mode: <b>${location.mode ?: 'Home'}</b> (Max ${maxActiveRooms} Room${maxActiveRooms > 1 ? 's' : ''})"
                if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
                    sysBadge = uiBadge("⏸️ PAUSED", "#64748b")
                    sysSub = "Master Switch OFF"
                } else if (activeMotionCount > 0) {
                    sysBadge = uiBadge("🔴 PERSON MOVING (${activeMotionCount} Room${activeMotionCount > 1 ? 's' : ''})", "#dc2626")
                    sysSub = "Active in: <b>${peakZoneName}</b>"
                } else if (sleepingZonesCount > 0) {
                    sysBadge = uiBadge("🛌 LAYING / SLEEPING (${totalSleepingPeople}P)", "#6366f1")
                    sysSub = "Lights held in sleep mode"
                } else if (stationaryCount > 0) {
                    sysBadge = uiBadge("🟢 SITTING / STANDING (${stationaryCount} Room${stationaryCount > 1 ? 's' : ''})", "#0d9488")
                    sysSub = "Active in: <b>${peakZoneName}</b>"
                } else if (cooldownCount > 0) {
                    sysBadge = uiBadge("🔵 3-MIN HOLD (${cooldownCount})", "#0284c7")
                    sysSub = "Pinging to verify occupancy"
                }

                String lastThreeHtml = buildLastThreeRoomsHtml(zoneStatus)
                List childSensorsList = (childPresenceSensors ?: []).toList()
                List presentChildList = getPresentChildPresenceSensors()
                String childSensorStatusStr = childSensorsList ?
                    (presentChildList ?
                        "<span style='color:#16a34a; font-weight:bold;'>🟢 PRESENT (${presentChildList.collect { it.displayName }.join(', ')})</span>" :
                        "<span style='color:#64748b;'>⚪ NOT PRESENT (${childSensorsList.collect { it.displayName }.join(', ')})</span>") :
                    "<span style='color:#64748b;'>Not Assigned (Select in Section 0 or Section 2)</span>"

                String dashHTML = """
                <style>
                    .kpi-grid { display: flex; gap: 10px; margin-top: 8px; margin-bottom: 12px; flex-wrap: wrap; }
                    .kpi-card { flex: 1; min-width: 135px; background: #ffffff; border: 1px solid #cbd5e1; border-radius: 8px; padding: 10px 12px; text-align: center; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
                    .kpi-title { font-size: 11px; text-transform: uppercase; color: #64748b; font-weight: bold; margin-bottom: 4px; }
                    .kpi-val { font-size: 18px; font-weight: bold; color: #0f172a; }
                    .kpi-sub { font-size: 11px; color: #475569; margin-top: 3px; }
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top:6px; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
                    .dash-table th, .dash-table td { border: 1px solid #e2e8f0; padding: 8px 10px; text-align: center; }
                    .dash-table th { background-color: #1e293b; color: white; font-size: 12px; text-transform: uppercase; letter-spacing: 0.4px; }
                    .dash-hl { background-color: #f8fafc; font-weight:600; color: #1e293b; text-align: left !important; padding-left: 14px !important; width: 24%; }
                    .dash-val { text-align: left !important; padding-left: 14px !important; color: #1e293b; }
                </style>

                <div class="kpi-grid">
                    <div class="kpi-card">
                        <div class="kpi-title">🏠 Active People / Household Cap</div>
                        <div class="kpi-val" style="color:#0284c7;">${totalPeopleDetected} Active <span style="font-size:13px; color:#64748b;">(Max ${maxActiveRooms} Room${maxActiveRooms > 1 ? 's' : ''})</span></div>
                        <div class="kpi-sub">${censusModeLabel}</div>
                    </div>
                    <div class="kpi-card">
                        <div class="kpi-title">🎓 7-Day Learning & Monthly Cycle</div>
                        <div class="kpi-val" style="color:#4f46e5; font-size:15px; margin-top:2px;">${graduatedRoomsCount} Graduated / ${learningRoomsCount} Learning</div>
                        <div class="kpi-sub"><b>${teacherSummarySub}</b></div>
                    </div>
                    <div class="kpi-card">
                        <div class="kpi-title">⚡ Active-Priority Sonar</div>
                        <div class="kpi-val" style="color:#0d9488; font-size:14px; margin-top:2px;">${atomicState.lastPingedZoneName ?: 'Scanning'}</div>
                        <div class="kpi-sub"><b>${doorwaySwitchCount} Doorway</b> + <b>${interiorNodeCount} Back/Interior</b></div>
                    </div>
                    <div class="kpi-card">
                        <div class="kpi-title">📍 Closest Switch / Plug</div>
                        <div class="kpi-val" style="color:#0d9488; font-size:15px; margin-top:3px;">${closestGlobalNode}</div>
                        <div class="kpi-sub">Distance: <b>${closestGlobalDist}</b></div>
                    </div>
                    <div class="kpi-card">
                        <div class="kpi-title">⚙️ Overall Status</div>
                        <div class="kpi-val" style="margin-top:4px;">${sysBadge}</div>
                        <div class="kpi-sub">${sysSub}</div>
                    </div>
                </div>

                <table class="dash-table">
                    <tbody>
                        <tr><td class="dash-hl">🕘 Last 3 Occupied Rooms</td><td colspan="3" class="dash-val">${lastThreeHtml}</td></tr>
                        <tr><td class="dash-hl">👧 Child Presence Sensor & Safeguards</td><td colspan="3" class="dash-val"><b>Child Presence Sensor:</b> ${childSensorStatusStr}  |  <b>Ceiling Fans ON = Ignored</b>  |  <b>Anti-Teleport Active</b></td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
            } else {
                paragraph uiHelpBox(
                    "Welcome to Advanced RF Tomography Manager 2.0 (v7.3.0)!",
                    "To get started, expand <b>Section 0: Select Your Wall Switches, Smart Plugs, Presence Sensors, Motion Sensors & Vibration/Multipurpose Sensors</b> below."
                )
            }
        }

        // ==================================================================
        // 0. COLLAPSIBLE SUB-MENU (NORMALLY CLOSED [N/C]): DEVICE SELECTION
        // ==================================================================
        boolean hasAnyDevicesSelected = ((zwaveAnchors ?: []).size() + (zigbeeAnchors ?: []).size()) > 0
        section("<b>0. Select Your Wall Switches, Smart Plugs, Presence Sensors, Motion Sensors & Vibration/Multipurpose Sensors (Sub-Menu — Normally Closed, Click to Open)</b>", hideable: true, hidden: hasAnyDevicesSelected) {
            input "presenceSensors", "capability.presenceSensor", title: "<b>📱 Select Adult Household Presence Sensors (Phones / Fobs / Life360)</b>", multiple: true, required: false, submitOnChange: true
            input "childPresenceSensors", "capability.presenceSensor", title: "<b>👧 Select Child Presence Sensor(s) (Used Exclusively to Detect if Child is Home)</b>", multiple: true, required: false, submitOnChange: true
            input "zwaveAnchors", "capability.switch", title: "<b>🌊 Select Z-Wave Plus Switches / Dimmers / Plugs</b>", multiple: true, required: false, submitOnChange: true
            input "zigbeeAnchors", "capability.switch", title: "<b>🐝 Select Zigbee Wall Switches, Fan Switches & Smart Plugs</b>", multiple: true, required: false, submitOnChange: true
            input "motionAssistSensors", "capability.motionSensor", title: "<b>🎓 Optional Hardware Motion Sensors (PIR / mmWave) — Leave Configured Permanently or Remove Anytime (Not Required!)</b>", multiple: true, required: false, submitOnChange: true
            input "vibrationAssistSensors", "capability.accelerationSensor", title: "<b>🪑📳 Optional Vibration & Multipurpose Sensors (Chair / Couch / Desk / Bed Acceleration Sensors — Teaches Sitting & Laying RF Signatures!)</b>", multiple: true, required: false, submitOnChange: true
            input "shockAssistSensors", "capability.shockSensor", title: "<b>🛏️📳 Optional Shock / Vibration Switches & Pressure Sensors (Chair / Bed Shock Sensors — Teaches Sitting & Laying RF Signatures!)</b>", multiple: true, required: false, submitOnChange: true
            input "appEnableSwitch", "capability.switch", title: "<b>Master Automation Enable/Disable Switch (Optional)</b>", required: false, multiple: false
        }
        // ==================================================================
        // 2A. COLLAPSIBLE SUB-MENU (NORMALLY OPEN): LIVE ROOM OCCUPANCY TABLE
        // ==================================================================
        section("<b>🏠 Live Room-by-Room Occupancy, Learned Postures & 7-Day/30-Day Ledger (Sub-Menu — Normally Open, Click to Collapse)</b>", hideable: true, hidden: false) {
            List allDevs = getAllAnchorDevices()
            Map zoneStatus = atomicState.zoneStatus ?: [:]
            if (allDevs && zoneStatus) {
                int motT = fetchEntryMotionThreshold()
                int keepT = fetchKeepAliveThreshold()
                Set<String> allZoneNames = zoneStatus.keySet().collect { it.toString() } as Set

                String zTable = """
                <table class='dash-table' style='margin-top:2px;'>
                    <thead>
                        <tr>
                            <th>Room Name, 7-Day/30-Day Cycle & Proximity</th>
                            <th>Current Room & Posture Status</th>
                            <th>People & Pets Inside</th>
                            <th>Closest Switch / Plug</th>
                            <th>Movement Level</th>
                            <th>Today's Visits & Time</th>
                            <th>Plain-English Explanation, Learned Posture Thresholds & Sensor Status</th>
                        </tr>
                    </thead>
                    <tbody>
                """

                zoneStatus.each { String zoneName, Map z ->
                    String postureBadge = uiBadge("🟢 EMPTY", "#16a34a")
                    if (z.occupancyState == "active_motion") {
                        postureBadge = uiBadge("🔴 OCCUPIED (${z.posture ?: 'WALKING'})", "#dc2626")
                    } else if (z.occupancyState == "sleeping_occupied") {
                        String slpBadgeTxt = (z.sleepSwitchActive == true) ? "🛌 SLEEP SWITCH ON" : "🛌 ${z.posture ?: 'LAYING / SLEEPING'}"
                        postureBadge = uiBadge(slpBadgeTxt, "#6366f1")
                    } else if (z.occupancyState == "stationary_occupied") {
                        postureBadge = uiBadge("🟢 OCCUPIED (${z.posture ?: 'SITTING / STANDING'})", "#0d9488")
                    } else if (z.occupancyState == "pet_occupied") {
                        postureBadge = uiBadge("🐾 PET ONLY", "#d97706")
                    } else if (z.occupancyState == "active_motion_cooldown") {
                        postureBadge = uiBadge("🔵 HOLDING (${z.cooldownRemaining ?: 0}s LEFT)", "#0284c7")
                    }

                    int todayCnt = safeInt(z.todayEntryCount, 0)
                    String todayDurStr = z.todayOccupiedDurationStr ?: "0s"
                    String lastEntryStr = z.lastEntryTimeStr ?: "None today"
                    String todayHtml = "<b>${todayCnt} visit(s)</b> (${todayDurStr})<div style='font-size:10.5px; color:#64748b;'>Last: ${lastEntryStr}</div>"

                    int ppl = safeInt(z.peopleCount, 0)
                    int pets = safeInt(z.petCount, 0)
                    boolean isHolding = (z.occupancyState == "active_motion_cooldown")
                    String countHtml = ppl > 0 ? "<b>👤 ${ppl} Person${ppl > 1 ? 's' : ''}</b>" : (isHolding ? "<b style='color:#0284c7;'>👤 Holding (Verifying)</b>" : "<span style='color:#64748b;'>0 People</span>")
                    if (pets > 0) countHtml += "<div style='font-size:11px; color:#d97706;'>🐾 ${pets} Pet</div>"

                    String proxHtml = (ppl > 0 || pets > 0) ?
                        "<b>~${z.closestDistanceFt ?: '--'} ft away</b><div style='font-size:11px; color:#475569;'>Near: <b>${z.closestNodeName ?: 'Switch'}</b></div>" :
                        (isHolding ? "<span style='color:#0284c7;'>Re-Pinging ${z.closestNodeName ?: 'Room'}</span>" : "<span style='color:#94a3b8;'>No One Nearby</span>")

                    int rMotT = safeInt(z.autoRoomEntryThresh, motT)
                    int rKeepT = safeInt(z.autoRoomKeepThresh, keepT)
                    int sc = (ppl > 0) ? safeInt(z.score, 0) : 0
                    String scColor = (z.sleepSwitchActive == true) ? "#6366f1" : ((ppl > 0 && sc >= rMotT) ? "#dc2626" : ((ppl > 0 && sc >= rKeepT) ? "#0d9488" : (isHolding ? "#0284c7" : "#16a34a")))
                    String scoreLabel = (z.sleepSwitchActive == true) ? "🛌 Sleep Locked" : ((ppl > 0 && sc >= rMotT) ? "🔴 ${sc}% (Moving)" : ((ppl > 0) ? "🟢 ${sc}% (Occupied)" : (isHolding ? "🔵 Hold (${z.cooldownRemaining}s)" : "🆗 0% (Calm)")))
                    String scoreBadge = uiBadge(scoreLabel, scColor)

                    Map tInfo = getRoomEffectiveLearnedThresholds(zoneName)
                    String teacherNote = "<div style='font-size:10.5px; color:${tInfo.graduated ? '#16a34a' : '#4f46e5'}; font-weight:bold;'>${tInfo.statusBadge}</div>"
                    teacherNote += "<div style='font-size:10px; color:#334155;'>🧍Stand:<b>${tInfo.standShadowDb}dB</b> | 🪑Sit:<b>${tInfo.sitShadowDb}dB</b> | 🛌Lay:<b>${tInfo.layShadowDb}dB</b></div>"

                    Set<String> closeNeighbors = getCloseProximityRoomsForZone(zoneName, allZoneNames)
                    String neighborNote = closeNeighbors ? "<div style='font-size:10px; color:#475569;'>🧭 Near: ${closeNeighbors.join(', ')}</div>" : ""
                    def slpSwDev = getZoneSleepSwitchDevice(zoneName)
                    String slpSwNote = slpSwDev ? "<div style='font-size:10.5px; color:#6366f1;'>🛌 Sleep Sw: <b>${slpSwDev.displayName} (${z.sleepSwitchActive ? 'ON' : 'OFF'})</b></div>" : ""
                    String lightAssistNote = (z.lightAssistActive == true && ppl > 0) ? "<div style='font-size:10.5px; color:#0284c7;'>💡 Non-Fan Light ON (+3% Assist — Never Locks Room)</div>" : ""
                    String coSensorNote = safeInt(z.coSensorPlugCount, 0) > 0 ? "<div style='font-size:10.5px; color:#0d9488;'>🔌 ${z.coSensorPlugCount} Interior Plug</div>" : ""
                    String portalVectorNote = ((ppl > 0 || isHolding) && z.portalVectorStatus) ? "<div style='font-size:10.5px; color:#0284c7; font-weight:bold;'>${z.portalVectorStatus}</div>" : ""
                    String pirRoleSummary = z.pirPlacementSummary ? " (${z.pirPlacementSummary})" : ""
                    String pirNote = safeInt(z.pirSensorCount, 0) > 0 ? "<div style='font-size:10.5px; color:#4f46e5;'>👁️ ${z.pirSensorCount} Motion Sensor${pirRoleSummary}: <b>${z.pirActiveNow ? 'ACTIVE' : 'Clear'}</b></div>" : "<div style='font-size:10.5px; color:#64748b;'>📡 Pure RF Sonar (${z.nodeCount ?: 1} Dev — No PIR Required)</div>"
                    String vibRoleSummary = z.vibSummaryText ? " (${z.vibSummaryText})" : ""
                    String vibNote = safeInt(z.vibSensorCount, 0) > 0 ? "<div style='font-size:10.5px; color:#0f766e;'>📳 ${z.vibSensorCount} Vib/Chair Sensor${vibRoleSummary}: <b>${z.vibActiveNow ? 'ACTIVE (' + (z.vibMeaningLabel ?: 'Posture') + ')' : (z.vibRecentlyActive ? 'HOLDING (' + (z.vibMeaningLabel ?: 'Posture') + ')' : 'Calm')}</b></div>" : ""
                    String triangulationNote = (z.multiSensorConsensus == true && ppl > 0) ? "<div style='font-size:10.5px; color:#16a34a; font-weight:bold;'>🎯 Corroborated (${z.activeSensorCount} room sensors)</div>" : ""
                    String echoNote = (z.suppressedByCrossTalk == true) ? "<div style='font-size:10.5px; color:#0d9488;'>🔇 Household Cap / Handoff Cleared</div>" : ""
                    String teleportNote = (z.suppressedByAntiTeleport == true) ? "<div style='font-size:10.5px; color:#d97706; font-weight:bold;'>🚫 Anti-Teleport Guard (Occupant in Far Room)</div>" : ""
                    String shieldNote = (z.suppressedByWallShield == true) ? "<div style='font-size:10.5px; color:#0d9488;'>🛡️ Dark-Bathroom / Closet Guard Blocked Solo Route Hop</div>" : ""

                    zTable += "<tr>"
                    zTable += "<td><b>${zoneName}</b><div style='font-size:11px; color:#64748b;'>Hold: ${fetchZoneMotionTimeoutSeconds(zoneName)}s (3m) | Trig: ${rMotT}% / Keep: ${rKeepT}%</div>${teacherNote}${neighborNote}${slpSwNote}${lightAssistNote}${coSensorNote}${portalVectorNote}${pirNote}${vibNote}${triangulationNote}${echoNote}${teleportNote}${shieldNote}</td>"
                    zTable += "<td>${postureBadge}</td>"
                    zTable += "<td>${countHtml}</td>"
                    zTable += "<td>${proxHtml}</td>"
                    zTable += "<td>${scoreBadge}</td>"
                    zTable += "<td>${todayHtml}</td>"
                    zTable += "<td style='text-align:left !important; font-size:12px; color:#1e293b;'>${buildZoneDiagnosticHtml(zoneName, z)}</td>"
                    zTable += "</tr>"
                }
                zTable += "</tbody></table>"
                paragraph zTable

                input "btnResetDailyStats", "button", title: "🔄 Reset Today's Visit Counters", width: 3
                input "btnClearRecentRooms", "button", title: "🧹 Clear Last 3 Rooms Trail", width: 3
                input "btnStartMonthlyReEvalNow", "button", title: "🗓️ Trigger 7-Day Re-Eval Pass Now", width: 3
                input "btnResetPirTeacherModels", "button", title: "🎓 Wipe All Learned Models", width: 3
            } else {
                paragraph "<i>No rooms configured yet. Open Section 0 above to select your switches.</i>"
            }
        }

        // 2B. PLAIN-ENGLISH PER-SWITCH STATUS TABLE
        section("<b>🔬 Individual Switch & Plug Synchronized Telemetry Table (Click to Collapse/Expand)</b>", hideable: true, hidden: false) {
            List allDevs = getAllAnchorDevices()
            if (allDevs) {
                Map nodeScores = getFastMap("nodeScores")
                Map zoneStatus = atomicState.zoneStatus ?: [:]
                int motT = fetchEntryMotionThreshold()
                int keepT = fetchKeepAliveThreshold()

                String nTable = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Switch / Plug Name, Room & Portal Role</th><th>Relay State (Trimmed-Median Base)</th><th>What This Device Sees Right Now</th><th>Estimated Distance</th><th>Disturbance Level</th><th>Live Ping, Trimmed-Median Base, MAD Noise Gate & Explanation</th></tr></thead><tbody>"

                Map zones = buildZoneMap()
                allDevs.each { dev ->
                    String devId = dev.id.toString()
                    String zName = getDeviceZoneName(dev)
                    List zDevs = zones[zName] ?: [dev]
                    Map ns = (nodeScores[devId] ?: [:]) as Map
                    Map zMap = (zoneStatus[zName] ?: [:]) as Map
                    int rMotT = safeInt(zMap.autoRoomEntryThresh, motT)
                    int rKeepT = safeInt(zMap.autoRoomKeepThresh, keepT)
                    int rawNodeScore = safeInt(ns.score, 0)
                    int candScore = safeInt(ns.candidateScore, 0)
                    int streak = safeInt(ns.hitStreak, 0)
                    boolean roomIsOccupied = (safeInt(zMap.peopleCount, 0) > 0) || (zMap.occupancyState == "active_motion_cooldown")
                    boolean roomSuppressed = (zMap.suppressedByCrossTalk == true) || (zMap.suppressedByWallShield == true) || (zMap.suppressedByAntiTeleport == true)

                    String swState = (dev.currentValue("switch") ?: "off").toString().toUpperCase()
                    boolean isFanDev = isCeilingFanSwitch(dev)
                    String swBadge = swState == "ON" ?
                        (isFanDev ? "<span style='color:#64748b; font-weight:bold;'>🌀 FAN ON (State Ignored)</span>" : "<span style='color:#0284c7; font-weight:bold;'>💡 ON (+3% Assist Only)</span>") :
                        "<span style='color:#64748b;'>⚫ OFF (OFF-Base Active)</span>"

                    String roleMode = getEffectiveDeviceTriggerRole(dev, zDevs)
                    String portalRole = getEffectiveDevicePortalRole(dev, zDevs)
                    String portalBadgeStr = getPortalRoleShortBadge(portalRole)
                    boolean isLearnedPortal = (ns.isLearnedPortalSwitch == true)
                    boolean isLearnedInterior = (ns.isLearnedInteriorNode == true)
                    String learnedRoleTag = isLearnedPortal ? " <span style='color:#4f46e5; font-weight:bold;'>[🎓 Learned Portal]</span>" : (isLearnedInterior ? " <span style='color:#0d9488; font-weight:bold;'>[🎓 Learned Interior]</span>" : "")

                    boolean isOffInteriorSwitch = (portalRole == "interior" && swState == "OFF")
                    int displayScore = isOffInteriorSwitch ? 0 : (roomIsOccupied ? Math.max(rawNodeScore, candScore) : (roomSuppressed ? 0 : rawNodeScore))
                    double sh = (roomSuppressed || isOffInteriorSwitch) ? 0.0d : safeDouble(ns.shadowShift, 0.0d)
                    double dFt = safeDouble(ns.distanceFt, 25.0d)

                    String whatItSees = "<span style='color:#16a34a; font-weight:bold;'>🟢 Clear — No One Nearby</span>"
                    if (roleMode == "support_only") {
                        whatItSees = "<span style='color:#64748b; font-weight:bold;'>🛡️ Muted Smart Plug (0% Weight)</span>"
                    } else if (roleMode == "support_assist") {
                        if (swState == "OFF") {
                            whatItSees = "<span style='color:#64748b; font-weight:bold;'>🔌 Plug is OFF (Dark-Room Guard Active)</span>"
                        } else if (roomIsOccupied && displayScore >= rKeepT) {
                            whatItSees = "<span style='color:#dc2626; font-weight:bold;'>🛋️ Person Near Plug (${ns.nodePosture ?: displayScore + '%'})</span>"
                        } else {
                            whatItSees = "<span style='color:#16a34a; font-weight:bold;'>🔌 Interior Plug Calm (0% — Guards Dark Room)</span>"
                        }
                    } else if (isOffInteriorSwitch) {
                        whatItSees = "<span style='color:#64748b; font-weight:bold;'>🛡️ Closet/Interior Switch OFF (Locked 0%)</span>"
                    } else if (zMap.sleepSwitchActive == true) {
                        whatItSees = "<span style='color:#6366f1; font-weight:bold;'>🛌 Room Asleep Switch ON</span>"
                    } else if (roomIsOccupied && displayScore >= rMotT) {
                        whatItSees = "<span style='color:#dc2626; font-weight:bold;'>🔴 ${ns.nodePosture ?: 'Walking / Moving'} (${portalBadgeStr})</span>"
                    } else if (roomIsOccupied && (displayScore >= rKeepT || sh >= 0.18d)) {
                        whatItSees = "<span style='color:#0d9488; font-weight:bold;'>${ns.nodePosture ?: '🟢 Sitting / Standing Nearby'}</span>"
                    } else if (zMap.occupancyState == "active_motion_cooldown") {
                        whatItSees = "<span style='color:#0284c7; font-weight:bold;'>🔵 3-Min Hold (${zMap.cooldownRemaining}s left — Pinging)</span>"
                    } else if (!roomIsOccupied && streak == 1 && candScore > 0 && !roomSuppressed && !isOffInteriorSwitch) {
                        whatItSees = "<span style='color:#d97706; font-weight:bold;'>🟡 Verifying Hit #1 (${candScore}% — Next Ping Confirms)</span>"
                    }

                    String distStr = (roleMode != "support_only" && !isOffInteriorSwitch && roomIsOccupied && (sh >= 0.18d || displayScore >= rKeepT) && dFt < 22.0d) ?
                        "<b>~${dFt} ft from device</b>" :
                        "<span style='color:#94a3b8;'>No One Nearby</span>"

                    String scColor = (roomIsOccupied && displayScore >= rMotT) ? "#dc2626" : ((roomIsOccupied && displayScore >= rKeepT) ? "#0d9488" : (!roomIsOccupied && !isOffInteriorSwitch && !roomSuppressed && streak == 1 && candScore > 0 ? "#d97706" : "#16a34a"))
                    String scBadge = roleMode == "support_only" ?
                        uiBadge("Muted (0%)", "#64748b") :
                        (roleMode == "support_assist" ? uiBadge("${roomIsOccupied ? displayScore : 0}% (Plug Sensor)", scColor) : uiBadge("${roomIsOccupied ? displayScore : 0}% (Trig at ${rMotT}%)", scColor))

                    String explainStr = (ns.plainReason ?: "Signal is completely calm and matches the empty room.")
                    if (isOffInteriorSwitch) {
                        explainStr = "<b>🛡️ Back-of-Room Closet / Interior Guard:</b> This ${portalBadgeStr} switch is physically OFF — locked at 0% so Zigbee route hops never self-latch the room!"
                    } else if (zMap.suppressedByAntiTeleport == true && (rawNodeScore > 0 || candScore > 0)) {
                        explainStr = "<b>🚫 Anti-Teleportation Guard:</b> Occupant is confirmed in a non-adjacent room (${atomicState.lastConfirmedPrimaryZone ?: 'Kitchen'}) — stray ripple ignored."
                    } else if (zMap.suppressedByWallShield == true && (rawNodeScore > 0 || candScore > 0)) {
                        explainStr = "<b>🛡️ Dark-Bathroom / Back-of-Room Guard:</b> Bathroom/Closet switches are OFF inside an unoccupied dark room — route hop ignored."
                    } else if (zMap.suppressedByCrossTalk == true && (rawNodeScore > 0 || candScore > 0)) {
                        explainStr = "<b>🔇 Strict Household Room Cap / Exit Handoff:</b> Cleared because occupant moved to another room."
                    } else if (roomIsOccupied && displayScore >= rKeepT && rawNodeScore == 0 && candScore > 0) {
                        explainStr = "<b>🎯 Room Corroborated Active (${candScore}%):</b> Multiple sensors in ${zName} (or active hold) confirmed occupancy!"
                    }

                    nTable += "<tr>"
                    nTable += "<td><b>${dev.displayName}</b><div style='font-size:11px; color:#64748b;'>Room: <b>${zName}</b> | <b>${portalBadgeStr}</b>${learnedRoleTag}</div></td>"
                    nTable += "<td>${swBadge}</td>"
                    nTable += "<td>${whatItSees}</td>"
                    nTable += "<td>${distStr}</td>"
                    nTable += "<td>${scBadge}</td>"
                    nTable += "<td style='text-align:left !important; font-size:12px; color:#334155;'>${explainStr}</td>"
                    nTable += "</tr>"
                }
                nTable += "</tbody></table>"
                paragraph nTable
            }
        }

        // 3. RECENT EVENT LOGS
        section("<b>📈 Recent Room Entry, 7-Day Learning, Monthly Re-Eval & Exit Event History (Click to Open)</b>", hideable: true, hidden: true) {
            if (atomicState.actionHistory) {
                paragraph "<div style='background:#f8fafc; padding:10px; border:1px solid #e2e8f0; border-radius:6px; font-size:12px; font-family:monospace;'>${((atomicState.actionHistory ?: []).toList()).join('<br>')}</div>"
            } else {
                paragraph "<i>No room events logged yet.</i>"
            }
            input "btnClearLogs", "button", title: "🗑️ Clear Event History", width: 4
        }

        section("<b>1. Entrance/Exit Roles, Optional PIR & Vibration Teachers, Room Sleep Switches & 3-Min Hold Timeouts</b>", hideable: true, hidden: true) {
            href "zoneAssignmentPage", title: "🚪📳 Configure Optional PIR & Vibration/Multipurpose Posture Teachers, Switch Portal Roles & Timeouts", description: "Assign Chair/Bed Vibration Labels, Entrance vs. Back-of-Room PIR Teachers & Room Hold Timeouts"
            href "activityMappingPage", title: "🔌 Configure Smart Plug Activity Labels (Optional)", description: "Assign labels like 'On a Computer' or 'Watching TV' to specific plugs"

            input "motionHoldSeconds", "number", title: "<b>⏱️ Default Room Occupancy Hold Time (Seconds before room marks Empty — Default 180s / 3 Minutes)</b>", range: "30..3600", defaultValue: 180, required: true, submitOnChange: true, width: 6
            input "globalEntryVerifySeconds", "number", title: "<b>🚪 Default Walk-By / Adjacent-Wall Filter Time (Seconds, 1–30s)</b>", range: "1..30", defaultValue: 2, required: true, submitOnChange: true, width: 6

            if (zwaveAnchors || zigbeeAnchors) {
                Map discoveredZones = buildZoneMap()
                paragraph "<hr style='margin:14px 0; border:0; border-top:1px solid #cbd5e1;'><b>🗑️ Remove Devices from App</b>"
                Map devOptions = [:]
                getAllAnchorDevices().each { dev ->
                    devOptions[dev.id.toString()] = "${dev.displayName} (Room: ${getDeviceZoneName(dev)})"
                }
                input "devicesToRemove", "enum", title: "<b>Select Switch(es) / Plug(s) to Remove</b>", options: devOptions, multiple: true, required: false, submitOnChange: true, width: 8
                input "btnRemoveSelectedDevices", "button", title: "🗑️ Remove Selected Device(s)", width: 4

                paragraph "<hr style='margin:14px 0; border:0; border-top:1px solid #cbd5e1;'><b>🗑️ Delete Room Zones</b>"
                Map zoneOptions = [:]
                discoveredZones.keySet().each { String zName ->
                    zoneOptions[zName] = "${zName} (${discoveredZones[zName]?.size() ?: 0} device(s))"
                }
                input "zonesToDelete", "enum", title: "<b>Select Room(s) to Delete</b>", options: zoneOptions, multiple: true, required: false, submitOnChange: true, width: 12
                input "btnDeleteSelectedZonesOnly", "button", title: "🗑️ Delete Room Only", width: 6
                input "btnDeleteSelectedZonesAndDevices", "button", title: "🗑️ Delete Room AND Unselect Its Switches", width: 6

                if (atomicState.excludedZones) {
                    input "btnRestoreExcludedZones", "button", title: "♻️ Restore Previously Deleted Room Zones", width: 12
                }
            }
        }

        section("<b>2. Child Presence Sensor, Ceiling Fan Immunity & Anti-Teleportation (v7.3.0)</b>", hideable: true, hidden: false) {
            paragraph uiHelpBox(
                "Child Presence Sensor Only (All Child Timing & Schedules Removed)",
                "• <b>👧 Dedicated Child Presence Sensor:</b> All scheduled child time windows, day-of-week rules, manual date loggers, and child virtual switches have been removed.<br>" +
                "• <b>📱 Live Presence Detection:</b> Whether a child is home is determined strictly by the <b>Child Presence Sensor(s)</b> (`present` vs `not present`) selected below or in Section 0."
            )
            input "childPresenceSensors", "capability.presenceSensor", title: "<b>👧 Select Child Presence Sensor(s) (Used Exclusively to Detect if Child is Home)</b>", multiple: true, required: false, submitOnChange: true, width: 12

            paragraph "<hr style='margin:10px 0; border:0; border-top:1px solid #cbd5e1;'>"

            input "enableAntiTeleportGuard", "bool", title: "<b>🚫 Enable Anti-Teleportation Contiguous Path Guard (Prevents Single Occupant in Kitchen From Teleporting to Far End of House on a Stray Ripple)</b>", defaultValue: true, width: 6
            input "enableLightSwitchAssist", "bool", title: "<b>💡 Enable Non-Fan Light Switch Assist (+3% RF Sensitivity Help When Light is ON, Never Locks Room Active When Empty; Ceiling Fans Ignored)</b>", defaultValue: true, width: 6

            input "enablePirSupervisedLearning", "bool", title: "<b>🎓 Enable 7-Day Rolling Learning Ledger, Monthly Re-Evaluation & Posture AI (PIR + Vibration + RF)</b>", defaultValue: true, width: 12
            input "learningWindowDays", "number", title: "<b>📅 Active Learning Window Duration (Days Logged Before Locking Graduation — Default 7 Days)</b>", range: "3..14", defaultValue: 7, required: true, width: 6
            input "monthlyReEvalDays", "number", title: "<b>🗓️ Automatic Re-Evaluation Cycle (Days Between Fresh 7-Day Learning Passes — Default 30 Days / Monthly)</b>", range: "14..90", defaultValue: 30, required: true, width: 6

            input "minPeopleHomeDefault", "number", title: "<b>👤 Default Active Adults Home When No Adult Phone Presence Sensors Are Used (Default 1)</b>", range: "1..10", defaultValue: 1, required: true, submitOnChange: true, width: 6
            input "knownHousePeopleMax", "number", title: "<b>🏠 Hard Upper Ceiling of Occupied Rooms When Whole Family is Home (Default 3)</b>", range: "1..20", defaultValue: 3, required: true, submitOnChange: true, width: 6

            input "allowMultiPersonPerSingleRoom", "bool", title: "<b>👥 Allow Reporting '2 People' in a Single Room (Default OFF — Prevents 1 Person Triggering Light + Fan Switch From Showing as 2 People!)</b>", defaultValue: false, width: 12

            input "enableAutonomousTuning", "bool", title: "<b>🤖 Enable Continuous Trimmed-Median Baseline & MAD Noise Gate</b>", defaultValue: true, width: 6
            input "enableMultiDeviceCorroboration", "bool", title: "<b>🎯 Enable Dark-Bathroom & Closet Guard (Blocks OFF Bathroom/Closet Switches From Solo Triggering)</b>", defaultValue: true, width: 6

            input "pollIntervalSec", "enum", title: "<b>Congestion-Free Sonar Speed</b>", options: [
                "2": "Every 2 Seconds (Recommended — Priority Active-Room + Round-Robin Sonar)",
                "3": "Every 3 Seconds (Balanced)",
                "5": "Every 5 Seconds (Low Traffic)"
            ], defaultValue: "2", required: true, width: 4

            input "motionThreshold", "number", title: "<b>🔴 Base Movement Trigger Threshold (%, Default 18%)</b>", range: "8..60", defaultValue: 18, required: true, width: 4
            input "stationaryThreshold", "number", title: "<b>🟢 Base Still Keep-Alive Threshold (%, Default 9%)</b>", range: "4..40", defaultValue: 9, required: true, width: 4

            input "smartPlugBehaviorMode", "enum", title: "<b>🔌 Smart Plug Role in Rooms</b>", options: [
                "co_sensor": "🔌 Interior Hold Only When ON (Never Triggers Empty Dark Rooms When OFF)",
                "mute": "🛡️ Completely Muted (0% Weight — Ignore Smart Plugs for Motion)"
            ], defaultValue: "co_sensor", required: true, width: 6

            input "plugCoSensorWeightPct", "number", title: "<b>🔌 Smart Plug Near-Field Sensitivity Weight (%, Default 75%)</b>", range: "25..100", defaultValue: 75, required: true, width: 6

            input "minShadowDbForStationary", "decimal", title: "<b>🧍 Default Standing Detection Sensitivity (dB, Default 0.32)</b>", defaultValue: 0.32, required: true, width: 4
            input "additionalPersonShadowDb", "decimal", title: "<b>👥 2nd Person Step (+dB)</b>", defaultValue: 1.45, required: true, width: 4
            input "layingShadowDb", "decimal", title: "<b>🛌 Default Laying / Sitting Hold Sensitivity (dB, Default 0.24)</b>", defaultValue: 0.24, required: true, width: 4

            input "switchTransitionBlankerSec", "number", title: "<b>💡 Switch Physical Toggle Immunity Time (Seconds, 4–20s)</b>", range: "4..20", defaultValue: 5, required: true, width: 4
            input "maxStationaryHoldMinutes", "number", title: "<b>🛡️ Max Still Hold Before Auto-Clear (Minutes, 3–60m)</b>", range: "3..60", defaultValue: 20, required: true, width: 4
            input "sleepVerificationMinutes", "number", title: "<b>🛌 Minutes of Stillness Before Auto-'Sleeping' Mode (1–30m)</b>", range: "1..30", defaultValue: 5, required: true, width: 4

            input "enableAdjacentWallShield", "bool", title: "<b>🧱 Enable Closet Parent-Room Check</b>", defaultValue: true, width: 6
            input "enableCrossTalkSuppression", "bool", title: "<b>⚡ Enable Strict Household Cap Enforcement</b>", defaultValue: true, width: 6

            input "ambientDeadbandDb", "decimal", title: "<b>Base Empty-Room Noise Filter (dB, Default 0.18)</b>", defaultValue: 0.18, required: true, width: 12

            input "btnApplyRecommendedTuning", "button", title: "⚡ Reset Thresholds & Apply v7.3.0 Fixes (Vibration Posture Teachers + Child Presence Sensor Only + Fan Immunity + Anti-Teleport)", width: 12
        }

        section("<b>3. Nightly Calibration & Away Mode Config</b>", hideable: true, hidden: true) {
            input "autoCalibrateHour", "bool", title: "<b>Automatically Re-Zero Empty Rooms Nightly at 3:15 AM & Check 30-Day Monthly Re-Evaluation</b>", defaultValue: true
            input "awayModes", "mode", title: "<b>Select Your Hub's 'Away' Mode(s)</b>", multiple: true, required: false
            input "logEnable", "bool", title: "<b>Enable Debug Logging</b>", defaultValue: true, width: 6
            input "traceEnable", "bool", title: "<b>Enable Verbose Trace Logging</b>", defaultValue: false, width: 6
        }
    }
}

// ==============================================================================
// SMART PLUG / SWITCH ACTIVITY TETHERING PAGE
// ==============================================================================

def activityMappingPage() {
    dynamicPage(name: "activityMappingPage", title: "<b>🔌 Smart Plug & Switch Activity Labels</b>", install: false, uninstall: false) {
        List allDevs = getAllAnchorDevices()
        if (allDevs) {
            allDevs.each { dev ->
                String devId = dev.id.toString()
                String zName = getDeviceZoneName(dev)
                section("<b>🔌 ${dev.displayName}</b> — Room: <i>${zName}</i>") {
                    input "activityPreset_${devId}", "enum", title: "<b>When a Person is Close to ${dev.displayName}, Show Activity As:</b>", options: [
                        "none": "⚪ No Activity Assigned",
                        "💻 On a Computer": "💻 On a Computer",
                        "📺 Watching TV": "📺 Watching TV",
                        "🎮 Gaming": "🎮 Gaming",
                        "🍳 Cooking / Kitchen Prep": "🍳 Cooking / Kitchen Prep",
                        "🚿 In Shower / Tub": "🚿 In Shower / Tub",
                        "🛌 In Bed / Sleeping": "🛌 In Bed / Sleeping",
                        "custom": "✏️ Custom Activity Name"
                    ], defaultValue: "none", required: false, submitOnChange: true, width: 4

                    input "activityDistFt_${devId}", "decimal", title: "<b>Trigger Distance (Feet)</b>", defaultValue: 6.5, range: "1..18", required: false, submitOnChange: true, width: 4
                    input "activityMinWatts_${devId}", "decimal", title: "<b>Min Power Draw (Watts, Optional)</b>", defaultValue: 0.0, required: false, submitOnChange: true, width: 4

                    if (settings["activityPreset_${devId}"] == "custom") {
                        input "activityCustom_${devId}", "text", title: "<b>✏️ Custom Activity Label</b>", required: false, submitOnChange: true, width: 12
                    }
                }
            }
        }
    }
}

// ==============================================================================
// ENTRANCE/EXIT PORTAL ROLES, OPTIONAL PIR & VIBRATION ROLES & ROOM SLEEP SWITCHES PAGE
// ==============================================================================

def zoneAssignmentPage() {
    dynamicPage(name: "zoneAssignmentPage", title: "<b>🚪📳 Optional PIR & Vibration Posture Teachers, 7-Day Learning, Switch Portal Roles & 180s Hold Timeouts</b>", install: false, uninstall: false) {
        if (motionAssistSensors) {
            section("<b>🎓 1A. Optional Hardware Motion Sensor (PIR / mmWave) Room Assignment & Placement Role (Entrance vs. Back in Room)</b>") {
                paragraph uiHelpBox(
                    "Leave Motion Sensors Configured Permanently OR Remove Anytime — 7-Day Learning + 30-Day Monthly Re-Evaluation",
                    "• <b>🚪 At Room Entrance / Doorway:</b> Trains the room's <b>Entry Trigger</b>, <b>Walking Signature</b>, <b>Portal Switch</b>, and <b>Instant Exit Handoff</b>.<br>" +
                    "• <b>🛋️ Back in Room / Interior:</b> Trains the room's <b>Standing</b>, <b>Sitting</b>, and <b>Laying Down / Sleeping</b> Zigbee & Z-Wave radio signatures.<br>" +
                    "• <b>🗓️ 7-Day Learning & 30-Day Monthly Refresh:</b> You can leave your motion sensors configured forever! Each room logs 7 days of learning, locks in its graduated model for 30 days, and automatically re-evaluates every month so it stays ahead of the curve."
                )
                motionAssistSensors.each { pirDev ->
                    String pirId = pirDev.id.toString()
                    String nativePirRoom = getMotionSensorZoneName(pirDev)
                    String autoPirRoleLabel = isInteriorPirSensorByName(pirDev) ? "🛋️ Back in Room / Interior" : (isEntrancePirSensorByName(pirDev) ? "🚪 At Room Entrance / Doorway" : "🔄 Whole Room / Both")

                    input "pirZoneOverride_${pirId}", "text", title: "<b>${pirDev.displayName}</b> — Assigned Room Name (Current: <i>${nativePirRoom}</i>)", required: false, submitOnChange: true, width: 6
                    input "pirPlacementRole_${pirId}", "enum", title: "<b>${pirDev.displayName}</b> — Where is this Motion Sensor Located in ${nativePirRoom}?", options: [
                        "auto": "Auto-Detect (${autoPirRoleLabel})",
                        "entrance": "🚪 At Room Entrance / Doorway (Trains Entry, Walking & Exit Vectors)",
                        "interior": "🛋️ Back in Room / Interior (Trains Standing, Sitting & Laying Down)",
                        "both": "🔄 Whole Room / Both (Trains Entry, Postures & Exit)"
                    ], defaultValue: "auto", required: false, submitOnChange: true, width: 6
                }
            }
        }

        List allVibDevs = getAllVibrationAssistDevices()
        if (allVibDevs) {
            section("<b>🪑📳 1B. Optional Vibration & Multipurpose Posture Teachers (Chair, Couch, Desk & Bed Ground-Truth Learning)</b>") {
                paragraph uiHelpBox(
                    "How Vibration & Multipurpose Sensors Teach Sitting & Laying Down (Ground Truth)",
                    "• <b>🪑 Sitting in a Chair / Recliner / Couch / Desk:</b> When vibration is felt on a chair or couch, the app immediately pings the room's switches/plugs and records the exact Zigbee/Z-Wave radio shadow & variance into the room's <b>Sitting Profile</b>.<br>" +
                    "• <b>🛌 Laying Down in Bed:</b> When vibration is felt on a bed or mattress, the app trains the room's <b>Laying Down / Sleeping Profile</b> and holds the room occupied.<br>" +
                    "• <b>🏷️ Custom Meaning Label:</b> Label what each vibration sensor means (e.g., <i>Sitting in Recliner</i>, <i>Sitting at Office Desk</i>, <i>Laying in Master Bed</i>, or type your own Custom Label)!"
                )
                allVibDevs.each { vibDev ->
                    String vibId = vibDev.id.toString()
                    String nativeVibRoom = getVibrationSensorZoneName(vibDev)
                    String autoPostureRole = isBedOrLayingVibrationByName(vibDev) ? "🛌 Laying Down in Bed" : (isStandingVibrationByName(vibDev) ? "🧍 Standing Mat" : "🪑 Sitting in Chair / Couch")
                    int defaultHoldMins = isBedOrLayingVibrationByName(vibDev) ? 45 : 15

                    input "vibZoneOverride_${vibId}", "text", title: "<b>📳 ${vibDev.displayName}</b> — Assigned Room Name (Current: <i>${nativeVibRoom}</i>)", required: false, submitOnChange: true, width: 3
                    input "vibPostureRole_${vibId}", "enum", title: "<b>${vibDev.displayName}</b> — What Posture Does It Teach?", options: [
                        "auto": "Auto-Detect (${autoPostureRole})",
                        "sitting": "🪑 Teaches SITTING (Chair / Recliner / Couch / Desk)",
                        "laying": "🛌 Teaches LAYING DOWN / SLEEPING (Bed / Mattress)",
                        "standing": "🧍 Teaches STANDING (Floor Mat / Counter / Standing Desk)"
                    ], defaultValue: "auto", required: false, submitOnChange: true, width: 3

                    input "vibLabelPreset_${vibId}", "enum", title: "<b>${vibDev.displayName}</b> — What Does Vibration Mean?", options: [
                        "auto": "Auto Label (${getVibrationMeaningLabel(vibDev)})",
                        "🪑 Sitting in Chair": "🪑 Sitting in Chair",
                        "🪑 Sitting in Recliner": "🪑 Sitting in Recliner",
                        "🛋️ Sitting on Couch / Sofa": "🛋️ Sitting on Couch / Sofa",
                        "💻 Sitting at Desk Chair": "💻 Sitting at Desk Chair",
                        "🍽️ Sitting at Dining Table": "🍽️ Sitting at Dining Table",
                        "🛌 Laying in Bed": "🛌 Laying in Bed",
                        "🧍 Standing at Counter / Mat": "🧍 Standing at Counter / Mat",
                        "custom": "✏️ Custom Meaning Label..."
                    ], defaultValue: "auto", required: false, submitOnChange: true, width: 3

                    input "vibHoldMinutes_${vibId}", "number", title: "<b>Hold Time After Vibration (Mins)</b> <i>[Default: ${defaultHoldMins}m]</i>", range: "2..240", required: false, submitOnChange: true, width: 3

                    if (settings["vibLabelPreset_${vibId}"] == "custom") {
                        input "vibCustomLabel_${vibId}", "text", title: "<b>✏️ Custom Label for ${vibDev.displayName} (e.g., 'Sitting in Leanne's Chair' or 'In Bed Reading')</b>", required: false, submitOnChange: true, width: 12
                    }
                }
            }
        }

        section("<b>🚪 2. Per-Room 'Room Asleep' Virtual Switches, Close-Proximity Neighbors & Timeouts</b>") {
            paragraph uiHelpBox(
                "How Posture AI, Anti-Teleportation & Ceiling Fan Immunity Work in v7.3.0",
                "• <b>🌀 Ceiling Fan Immunity:</b> Ceiling Fan switches being ON never count toward a room being occupied or active.<br>" +
                "• <b>💡 Light Switch Assist (Never Locks Room):</b> Non-fan light switches being ON give a small +3% sensitivity assist ONLY when real RF/PIR/Vibration motion is present, and never lock a room active after you leave.<br>" +
                "• <b>🚫 Anti-Teleportation Path Guard:</b> Uses the <b>Rooms in Close Proximity</b> list below so someone in the Kitchen cannot teleport to the far end of the house without passing through an adjacent room first."
            )
            Map zones = buildZoneMap()
            Set<String> allZoneNames = zones.keySet().collect { it.toString() } as Set
            if (zones) {
                zones.each { String zoneName, List devs ->
                    String zKey = sanitizeZoneKey(zoneName)
                    boolean defaultSleepAllowed = !isDefaultNonSleepRoomName(zoneName)
                    Map tInfo = getRoomEffectiveLearnedThresholds(zoneName)

                    Map<String, String> otherRoomOptions = [:]
                    allZoneNames.findAll { it != zoneName }.sort().each { String otherName ->
                        otherRoomOptions[otherName] = otherName
                    }
                    List<String> defaultNeighbors = getDefaultAdjacentZones(zoneName, allZoneNames)

                    paragraph "<div style='background:#f1f5f9; padding:8px 12px; border-left:4px solid #0284c7; font-weight:bold; color:#0f172a; margin-top:10px;'>🏠 ${zoneName} (${devs.size()} Device${devs.size() > 1 ? 's' : ''}) — <span style='color:#4f46e5;'>${tInfo.statusBadge} | Stand:${tInfo.standShadowDb}dB / Sit:${tInfo.sitShadowDb}dB / Lay:${tInfo.layShadowDb}dB</span></div>"

                    input "zoneSleepSwitch_${zKey}", "capability.switch", title: "<b>🛌 ${zoneName} — 'Room Asleep' Virtual Switch (When ON = Room is Locked Asleep)</b>", required: false, multiple: false, submitOnChange: true, width: 6

                    input "adjacentZones_${zKey}", "enum", title: "<b>🧭 Rooms in Close Proximity to ${zoneName} (Used for Instant Handoff & Anti-Teleportation)</b>", options: otherRoomOptions, defaultValue: defaultNeighbors, multiple: true, required: false, submitOnChange: true, width: 6

                    input "zoneEntryMode_${zKey}", "enum", title: "<b>${zoneName}</b> — How Fast Should This Room Turn On?", options: [
                        "auto": "Auto (⚡ Corroborated / 2-Hit Verified)",
                        "immediate": "⚡ Fast Active",
                        "verified": "🧱 Verified (Requires 2 Consecutive Hits)"
                    ], defaultValue: "auto", required: false, submitOnChange: true, width: 4

                    input "zoneTimeout_${zKey}", "number", title: "<b>${zoneName}</b> — Exit Hold Timeout (Seconds) <i>[Default: ${fetchGlobalMotionTimeoutSeconds()}s (3 Min)]</i>", range: "30..3600", required: false, submitOnChange: true, width: 4
                    input "zoneEntryVerifySec_${zKey}", "number", title: "<b>${zoneName}</b> — Verify Time (Seconds) <i>[Default: ${fetchGlobalEntryVerifySeconds()}s]</i>", range: "1..30", required: false, submitOnChange: true, width: 4

                    input "zoneAllowSleep_${zKey}", "enum", title: "<b>${zoneName}</b> — Allow Automatic RF 'Laying Down / Sleeping' Detection?", options: [
                        "auto": "Auto (${defaultSleepAllowed ? 'Yes - Bedroom/Living' : 'No - Bath/Kitchen'})",
                        "yes": "Yes — Allow Laying / Sleep Tracking",
                        "no": "No — Only Use Sleep Virtual Switch (If Set)"
                    ], defaultValue: "auto", required: false, submitOnChange: true, width: 12
                }
            }
        }

        section("<b>🔬 3. Individual Switch & Smart Plug Entrance/Exit Portal Roles & Trigger Roles</b>") {
            Map zones = buildZoneMap()
            getAllAnchorDevices().each { dev ->
                String devId = dev.id.toString()
                String zName = getDeviceZoneName(dev)
                List zDevs = zones[zName] ?: [dev]
                String autoRole = isSmartPlugOrApplianceNode(dev) ?
                    (settings.smartPlugBehaviorMode == "mute" ? "🛡️ Muted Smart Plug" : "🔌 Active Near-Field Plug Sensor") :
                    (isCeilingFanSwitch(dev) ? "🌀 Ceiling Fan Switch (State Ignored)" : "🔄 Primary Wall Switch Sensor")
                boolean autoIsInterior = (isClosetOrBackOfRoomSwitch(dev) || isSmartPlugOrApplianceNode(dev)) && zDevs.size() >= 2
                String defaultPortalLabel = autoIsInterior ?
                    "🛋️ Back-of-Room / Interior Hold" :
                    "🚪 Entrance & Exit (Both)"
                String nativeRoom = dev.roomName ?: inferRoomFromLabel(dev.displayName)

                input "devicePortalRole_${devId}", "enum", title: "<b>${dev.displayName}</b> — Entrance / Exit / Interior Role", options: [
                    "default_auto": "Default (${defaultPortalLabel})",
                    "both": "🚪 Entrance & Exit (Both — Doorway Switch/Plug)",
                    "entrance_only": "➡️ Entrance Only (Detects Entering Room)",
                    "exit_only": "⬅️ Exit Only (Detects Leaving Room)",
                    "interior": "🛋️ Back-of-Room / Interior Hold (Closet Switch / Repeater Plug / Inner Switch)",
                    "none": "⚪ Not Required / Standard Sensor"
                ], defaultValue: "default_auto", required: false, submitOnChange: true, width: 4

                input "deviceTriggerRole_${devId}", "enum", title: "<b>${dev.displayName}</b> — Trigger & Sensor Role", options: [
                    "room_default": "Default (${autoRole})",
                    "immediate": "⚡ Fast Primary Trigger Switch / Plug",
                    "verified": "🧱 2-Hit Verified Switch",
                    "support_assist": "🔌 Active Near-Field Plug Sensor & Interior Hold",
                    "support_only": "🛡️ Completely Muted (0% Weight — Ignore for Motion)"
                ], defaultValue: "room_default", required: false, submitOnChange: true, width: 4

                input "zoneOverride_${devId}", "text", title: "<b>Room Name</b> (Default: <i>${nativeRoom}</i>)", required: false, submitOnChange: true, width: 4
            }
        }
    }
}

// ==============================================================================
// HELPERS, TOPOLOGY & SETTINGS FETCHERS
// ==============================================================================

boolean hasCustomTomographyDriver(dev) {
    if (!dev) return false
    String tName = (dev.typeName ?: "").toLowerCase()
    return tName.contains("tomography") || dev.hasAttribute("rfSignalDb") || dev.hasAttribute("baselineRttMs")
}

boolean isSmartPlugOrApplianceNode(dev) {
    if (!dev) return false
    String labelAndType = "${dev.displayName ?: ''} ${dev.typeName ?: ''} ${dev.getDataValue('manufacturer') ?: ''} ${dev.getDataValue('model') ?: ''}".toLowerCase()
    return labelAndType.contains("plug") || labelAndType.contains("outlet") || labelAndType.contains("repeater") ||
           labelAndType.contains("dehumidifier") || labelAndType.contains("third reality") || labelAndType.contains("thirdreality") || labelAndType.contains("3rsp")
}

int fetchPlugCoSensorWeightPct() {
    return (settings.plugCoSensorWeightPct != null ? Math.max(25, Math.min(100, settings.plugCoSensorWeightPct.toInteger())) : 75)
}

String fetchDeviceTriggerRole(dev) {
    String devId = dev.id.toString()
    return (settings["deviceTriggerRole_${devId}"]?.toString() ?: "room_default")
}

String getEffectiveDeviceTriggerRole(dev, List zoneDevices = null) {
    String explicitRole = fetchDeviceTriggerRole(dev)
    if (explicitRole != "room_default") return explicitRole

    if (isSmartPlugOrApplianceNode(dev)) {
        List devsInZone = zoneDevices ?: (buildZoneMap()[getDeviceZoneName(dev)] ?: [dev])
        boolean hasWallSwitchInRoom = devsInZone.size() >= 2 && devsInZone.any { !isSmartPlugOrApplianceNode(it) }
        if (hasWallSwitchInRoom) {
            return (settings.smartPlugBehaviorMode == "mute") ? "support_only" : "support_assist"
        }
    }
    return "room_default"
}

boolean isClosetRoomName(String zoneName) {
    String lower = (zoneName ?: "").toLowerCase()
    return lower.contains("closet") || lower.contains("wardrobe") || lower.contains("pantry")
}

String inferParentRoomForSubZone(String zoneName, Set<String> availableZones) {
    String lower = (zoneName ?: "").toLowerCase()
    if (lower.contains("master") && lower.contains("closet")) {
        String match = availableZones.find { it.toLowerCase() == "master bedroom" || it.toLowerCase() == "master bathroom" }
        if (match) return match
    }
    if (isClosetRoomName(zoneName)) {
        String baseName = zoneName.replaceAll(/(?i)\s*closet\s*/, "").trim()
        if (baseName && availableZones.contains(baseName)) return baseName
    }
    return null
}

boolean isDefaultWalkthroughRoomName(String zoneName) {
    String lower = (zoneName ?: "").toLowerCase()
    if (isClosetRoomName(lower)) return false
    return true
}

boolean isImmediateEntryAllowedInZone(String zoneName) {
    String zKey = sanitizeZoneKey(zoneName)
    String mode = settings["zoneEntryMode_${zKey}"]?.toString()
    if (mode == "immediate") return true
    if (mode == "verified") return false
    return isDefaultWalkthroughRoomName(zoneName)
}

int fetchGlobalEntryVerifySeconds() {
    return (settings.globalEntryVerifySeconds != null ? Math.max(1, Math.min(30, settings.globalEntryVerifySeconds.toInteger())) : 2)
}

int fetchZoneEntryVerifySeconds(String zoneName) {
    String zKey = sanitizeZoneKey(zoneName)
    def customVal = settings["zoneEntryVerifySec_${zKey}"]
    if (customVal != null && customVal.toString().trim() != "") {
        return Math.max(1, Math.min(30, customVal.toInteger()))
    }
    return fetchGlobalEntryVerifySeconds()
}

int fetchSwitchBlankerSeconds() {
    return (settings.switchTransitionBlankerSec != null ? Math.max(4, Math.min(20, settings.switchTransitionBlankerSec.toInteger())) : 5)
}

long fetchSwitchBlankerMs() {
    return (fetchSwitchBlankerSeconds() as long) * 1000L
}

int fetchMaxStationaryHoldMinutes() {
    return (settings.maxStationaryHoldMinutes != null ? Math.max(3, Math.min(60, settings.maxStationaryHoldMinutes.toInteger())) : 20)
}

long fetchMaxStationaryHoldMs() {
    return (fetchMaxStationaryHoldMinutes() as long) * 60000L
}

double fetchGlobalDeadbandDb() {
    return (settings.ambientDeadbandDb != null ? settings.ambientDeadbandDb.toDouble() : DEFAULT_DRIFT_DEADBAND_DB.doubleValue())
}

boolean isBathroomOrShowerRoomName(String zoneName) {
    String lower = (zoneName ?: "").toLowerCase()
    List bathKeywords = ["bath", "shower", "restroom", "powder", "vanity", "toilet", "lavatory", "ensuite", "wc"]
    return bathKeywords.any { lower.contains(it) }
}

boolean isKitchenOrDiningRoomName(String zoneName) {
    String lower = (zoneName ?: "").toLowerCase()
    List kitchenKeywords = ["kitchen", "pantry", "dining", "breakfast", "nook", "bar", "butler"]
    return kitchenKeywords.any { lower.contains(it) }
}

String getLowProfileNonSleepPosture(String zoneName, boolean forNodeTable = false) {
    if (isBathroomOrShowerRoomName(zoneName)) {
        return "🚿 Shower / Standing Still"
    }
    if (isKitchenOrDiningRoomName(zoneName)) {
        return "🍳 Standing at Counter"
    }
    return "🪑 Sitting Still"
}

boolean isDefaultNonSleepRoomName(String zoneName) {
    String lower = (zoneName ?: "").toLowerCase()
    List nonSleepKeywords = [
        "bath", "shower", "restroom", "powder", "vanity", "kitchen",
        "laundry", "mud", "garage", "porch", "patio", "hall", "foyer",
        "entry", "pantry", "closet", "utility", "dining", "farm", "stand"
    ]
    return nonSleepKeywords.any { lower.contains(it) }
}

boolean isSleepAllowedInZone(String zoneName) {
    if (isZoneSleepSwitchOn(zoneName)) return true
    String zKey = sanitizeZoneKey(zoneName)
    String override = settings["zoneAllowSleep_${zKey}"]?.toString()
    if (override == "yes") return true
    if (override == "no") return false
    return !isDefaultNonSleepRoomName(zoneName)
}

int fetchSleepVerificationMinutes() {
    return (settings.sleepVerificationMinutes != null ? Math.max(1, settings.sleepVerificationMinutes.toInteger()) : 5)
}

long fetchSleepVerificationMs() {
    return (fetchSleepVerificationMinutes() as long) * 60000L
}

String getConfiguredNodeActivityLabel(dev) {
    String devId = dev.id.toString()
    String preset = settings["activityPreset_${devId}"]?.toString()
    if (!preset || preset == "none") return null
    if (preset == "custom") {
        String customTxt = settings["activityCustom_${devId}"]?.toString()?.trim()
        return customTxt ? "⚡ ${customTxt}" : null
    }
    return preset
}

double getConfiguredNodeActivityRadiusFt(dev) {
    String devId = dev.id.toString()
    def val = settings["activityDistFt_${devId}"]
    return (val != null ? val.toDouble() : 6.5d)
}

double getConfiguredNodeActivityMinWatts(dev) {
    String devId = dev.id.toString()
    def val = settings["activityMinWatts_${devId}"]
    return (val != null ? val.toDouble() : 0.0d)
}

void autoMigrateThresholdsOnce() {
    if (!atomicState.v730ThresholdsMigrated) {
        atomicState.v730ThresholdsMigrated = true
        ["v571ThresholdsMigrated", "v580ThresholdsMigrated", "v581ThresholdsMigrated", "v582ThresholdsMigrated", "v583ThresholdsMigrated", "v584ThresholdsMigrated", "v585ThresholdsMigrated", "v590ThresholdsMigrated", "v600ThresholdsMigrated", "v610ThresholdsMigrated", "v620ThresholdsMigrated", "v630ThresholdsMigrated", "v640ThresholdsMigrated", "v641ThresholdsMigrated", "v642ThresholdsMigrated", "v643ThresholdsMigrated", "v644ThresholdsMigrated", "v650ThresholdsMigrated", "v660ThresholdsMigrated", "v670ThresholdsMigrated", "v671ThresholdsMigrated", "v680ThresholdsMigrated", "v681ThresholdsMigrated", "v682ThresholdsMigrated", "v683ThresholdsMigrated", "v690ThresholdsMigrated", "v691ThresholdsMigrated", "v692ThresholdsMigrated", "v693ThresholdsMigrated", "v694ThresholdsMigrated", "v695ThresholdsMigrated", "v700ThresholdsMigrated", "v701ThresholdsMigrated", "v710ThresholdsMigrated", "v711ThresholdsMigrated", "v720ThresholdsMigrated", "rawZigbeeSnippet", "hardwareResolutionLog", "lastZwaveEntryCount", "staticFlatStartMs", "lastSeenActivityMs"].each { String oldKey ->
            atomicState.remove(oldKey)
        }
        [
            "activePeopleHomeNow",
            "childHomeVirtualSwitch",
            "enableScheduledChildPresence",
            "assumedChildCount",
            "childAllDayDaysOfWeek",
            "childPresenceDaysOfWeek",
            "childPresenceStartTime",
            "childPresenceEndTime",
            "childPresenceSpecificDates",
            "childSpecificDatesAllDay"
        ].each { String oldSetting ->
            app.removeSetting(oldSetting)
        }
        getFastMap("nodeAutoTune").clear()
        getFastMap("zoneBleedMatrix").clear()
        atomicState.nodeAutoTune = [:]
        atomicState.zoneBleedMatrix = [:]

        app.updateSetting("enableAntiTeleportGuard", [value: "true", type: "bool"])
        app.updateSetting("enableLightSwitchAssist", [value: "true", type: "bool"])

        app.updateSetting("enablePirSupervisedLearning", [value: "true", type: "bool"])
        app.updateSetting("learningWindowDays", [value: 7, type: "number"])
        app.updateSetting("monthlyReEvalDays", [value: 30, type: "number"])
        app.updateSetting("motionHoldSeconds", [value: 180, type: "number"])
        app.updateSetting("minPeopleHomeDefault", [value: 1, type: "number"])
        app.updateSetting("knownHousePeopleMax", [value: 3, type: "number"])
        app.updateSetting("allowMultiPersonPerSingleRoom", [value: "false", type: "bool"])
        app.updateSetting("enableAutonomousTuning", [value: "true", type: "bool"])
        app.updateSetting("enableMultiDeviceCorroboration", [value: "true", type: "bool"])
        app.updateSetting("pollIntervalSec", [value: "2", type: "enum"])
        app.updateSetting("motionThreshold", [value: 18, type: "number"])
        app.updateSetting("stationaryThreshold", [value: 9, type: "number"])
        app.updateSetting("smartPlugBehaviorMode", [value: "co_sensor", type: "enum"])
        app.updateSetting("plugCoSensorWeightPct", [value: 75, type: "number"])
        app.updateSetting("minShadowDbForStationary", [value: "0.32", type: "decimal"])
        app.updateSetting("additionalPersonShadowDb", [value: "1.45", type: "decimal"])
        app.updateSetting("layingShadowDb", [value: "0.24", type: "decimal"])
        app.updateSetting("globalEntryVerifySeconds", [value: 2, type: "number"])
        app.updateSetting("switchTransitionBlankerSec", [value: 5, type: "number"])
        app.updateSetting("maxStationaryHoldMinutes", [value: 20, type: "number"])
        app.updateSetting("sleepVerificationMinutes", [value: 5, type: "number"])
        app.updateSetting("enableAdjacentWallShield", [value: "true", type: "bool"])
        app.updateSetting("enableCrossTalkSuppression", [value: "true", type: "bool"])
        app.updateSetting("ambientDeadbandDb", [value: "0.18", type: "decimal"])

        getAllAnchorDevices().each { dev ->
            if (isSmartPlugOrApplianceNode(dev)) {
                app.updateSetting("deviceTriggerRole_${dev.id}", [value: "room_default", type: "enum"])
            }
            if (isClosetOrBackOfRoomSwitch(dev)) {
                app.updateSetting("devicePortalRole_${dev.id}", [value: "default_auto", type: "enum"])
            }
        }
    }
}

int fetchEntryMotionThreshold() {
    return (settings.motionThreshold != null ? settings.motionThreshold.toInteger() : 18)
}

int fetchKeepAliveThreshold() {
    return (settings.stationaryThreshold != null ? settings.stationaryThreshold.toInteger() : 9)
}

BigDecimal fetchMinShadowForStationary() {
    return (settings.minShadowDbForStationary != null ? settings.minShadowDbForStationary.toBigDecimal() : 0.32G)
}

BigDecimal fetchAdditionalPersonShadow() {
    return (settings.additionalPersonShadowDb != null ? settings.additionalPersonShadowDb.toBigDecimal() : 1.45G)
}

BigDecimal fetchLayingShadowThreshold() {
    return (settings.layingShadowDb != null ? settings.layingShadowDb.toBigDecimal() : 0.24G)
}

BigDecimal fetchMaxSleepVariance() {
    return 0.0050G
}

BigDecimal fetchPetMinShadowThreshold() {
    return 0.45G
}

BigDecimal fetchPartialExitDropThreshold() {
    return 0.70G
}

int fetchKnownHousePeopleMax() {
    return (settings.knownHousePeopleMax != null ? Math.max(1, Math.min(10, settings.knownHousePeopleMax.toInteger())) : 3)
}

int fetchKnownHousePetsMax() {
    return (settings.knownHousePetsMax != null ? settings.knownHousePetsMax.toInteger() : 2)
}

double estimateDistanceFeet(double netShadowDb, double netVariance) {
    if (netShadowDb <= 0.10d && netVariance <= 0.002d) return 25.0d
    double clampedShadow = Math.min(2.40d, netShadowDb)
    double dynamicBoost = Math.min(0.65d, netVariance * 5.0d)
    double effectiveAtten = Math.max(0.18d, clampedShadow + dynamicBoost)
    double ratio = Math.max(0.0d, (DEFAULT_REFERENCE_NEAR_DB - effectiveAtten) / 2.10d)
    double estFeet = 1.8d * Math.pow(10.0d, ratio * 0.52d)
    return Math.round(Math.max(1.8d, Math.min(25.0d, estFeet)) * 10.0d) / 10.0d
}

String classifyProximityBand(double distFt) {
    if (distFt <= 3.5d) return "Right Next to Switch (<3.5 ft)"
    if (distFt <= 6.5d) return "Near Switch (3.5–6.5 ft)"
    if (distFt <= 12.0d) return "Mid-Room (6.5–12 ft)"
    if (distFt < 20.0d) return "Far Side of Room"
    return "No One Nearby (>20 ft)"
}

// ==============================================================================
// v7.3.0 MULTI-POSTURE CLASSIFIER (WALKING, STANDING, SITTING, LAYING DOWN)
// ==============================================================================
String classifyNodePosture(String zoneName, double netShadowDb, double variance, double stepDelta, int score) {
    Map tInfo = getRoomEffectiveLearnedThresholds(zoneName)
    int keepT = safeInt(tInfo.keepThresh, fetchKeepAliveThreshold())
    int motT = safeInt(tInfo.entryThresh, fetchEntryMotionThreshold())
    double layDb = safeDouble(tInfo.layShadowDb, fetchLayingShadowThreshold().doubleValue())
    double sitDb = safeDouble(tInfo.sitShadowDb, 0.27d)
    double standDb = safeDouble(tInfo.standShadowDb, fetchMinShadowForStationary().doubleValue())
    double sitVarMax = safeDouble(tInfo.sitVariance, 0.0032d)
    double layVarMax = safeDouble(tInfo.layVariance, 0.0018d)

    if (score < keepT && netShadowDb < Math.min(0.18d, layDb)) {
        return "🟢 Clear"
    }
    boolean sleepAllowed = isSleepAllowedInZone(zoneName)

    if (score >= motT && (variance >= 0.0035d || stepDelta >= 0.04d || netShadowDb >= tInfo.walkShadowDb)) {
        return "🚶 Walking / Moving"
    }
    if (netShadowDb >= standDb && (variance > sitVarMax || stepDelta >= 0.02d)) {
        return isKitchenOrDiningRoomName(zoneName) ? "🍳 Standing at Counter" : (isBathroomOrShowerRoomName(zoneName) ? "🚿 Standing / At Vanity" : "🧍 Standing")
    }
    if (netShadowDb >= sitDb || (score >= keepT && variance > layVarMax)) {
        return isBathroomOrShowerRoomName(zoneName) ? "🚿 In Bathroom / Still" : "🪑 Sitting"
    }
    if (netShadowDb >= layDb || score >= keepT) {
        return sleepAllowed ? "🛌 Laying Down / Resting" : getLowProfileNonSleepPosture(zoneName, true)
    }
    return "🪑 Sitting / Still"
}

String sanitizeZoneKey(String zoneName) {
    return (zoneName ?: "General").replaceAll(/[^a-zA-Z0-9]/, "_").toUpperCase()
}

int fetchZoneMotionTimeoutSeconds(String zoneName) {
    String zKey = sanitizeZoneKey(zoneName)
    def customVal = settings["zoneTimeout_${zKey}"]
    if (customVal != null && customVal.toString().trim() != "") {
        return Math.max(30, Math.min(3600, customVal.toInteger()))
    }
    return fetchGlobalMotionTimeoutSeconds()
}

int fetchGlobalMotionTimeoutSeconds() {
    int configured = (settings.motionHoldSeconds != null ? settings.motionHoldSeconds.toInteger() : 180)
    return Math.max(180, configured)
}

boolean isDeviceInConfirmedOccupiedZone(dev, Map currentZoneStatus = null) {
    String zName = getDeviceZoneName(dev)
    Map zMap = currentZoneStatus != null ? currentZoneStatus : (atomicState.zoneStatus ?: [:])
    Map zStat = zMap[zName] as Map
    if (!zStat) return false
    return (zStat.occupancyState in ["active_motion", "stationary_occupied", "sleeping_occupied", "active_motion_cooldown"])
}

// ==============================================================================
// DAILY ROOM ACTIVITY COUNTER, LAST-3 ROOMS & MIDNIGHT ROLLOVER
// ==============================================================================

String getCurrentDateKey() {
    return new Date().format("yyyy-MM-dd", location.timeZone)
}

void recordRecentRoomEntry(String zoneName, String postureLabel, long nowTs) {
    List recent = ((atomicState.recentOccupiedRooms ?: []).toList()).collect { new LinkedHashMap(it as Map) }
    String timeStr = new Date(nowTs).format("hh:mm:ss a", location.timeZone)

    if (recent && (recent[0].zoneName == zoneName) && (recent[0].isActive == true)) {
        recent[0].posture = postureLabel
        recent[0].lastSeenEpochMs = nowTs
    } else {
        recent.add(0, [
            zoneName: zoneName,
            enteredAtStr: timeStr,
            enteredEpochMs: nowTs,
            lastSeenEpochMs: nowTs,
            exitedAtStr: null,
            posture: postureLabel,
            isActive: true
        ])
    }

    while (recent.size() > 3) {
        recent.remove(recent.size() - 1)
    }
    atomicState.recentOccupiedRooms = recent
}

void updateRecentRoomStatus(String zoneName, String postureLabel, boolean isCurrentlyOccupied, long nowTs) {
    List recent = ((atomicState.recentOccupiedRooms ?: []).toList()).collect { new LinkedHashMap(it as Map) }
    if (!recent) return

    boolean changed = false
    recent.each { Map item ->
        if (item.zoneName == zoneName && item.isActive == true) {
            if (isCurrentlyOccupied) {
                item.posture = postureLabel
                item.lastSeenEpochMs = nowTs
            } else {
                item.isActive = false
                item.lastSeenEpochMs = nowTs
                item.exitedAtStr = new Date(nowTs).format("hh:mm a", location.timeZone)
            }
            changed = true
        }
    }
    if (changed) {
        atomicState.recentOccupiedRooms = recent
    }
}

void checkDailyMidnightRollover() {
    String todayKey = getCurrentDateKey()
    String storedKey = atomicState.dailyStatsDateStr
    if (storedKey == null) {
        atomicState.dailyStatsDateStr = todayKey
        return
    }
    if (storedKey != todayKey) {
        performMidnightStatsReset(todayKey)
    }
}

def scheduledMidnightStatsReset() {
    performMidnightStatsReset(getCurrentDateKey())
}

void performMidnightStatsReset(String newDateKey = null) {
    if (!newDateKey) newDateKey = getCurrentDateKey()
    long nowTs = now()
    ConcurrentHashMap<String, Object> dailyStats = getFastMap("dailyStats")
    Map currentZones = (atomicState.zoneStatus ?: [:]) as Map

    List<String> summaryParts = []
    dailyStats.each { String zName, Object rawStat ->
        if (rawStat instanceof Map) {
            int cnt = safeInt(rawStat.entryCount, 0)
            long ms = safeLong(rawStat.occupiedMs, 0L)
            if (cnt > 0 || ms > 0L) {
                summaryParts << "${zName}: ${cnt}x (${formatDurationMs(ms)})"
            }
        }
    }
    if (summaryParts) {
        atomicState.yesterdayStatsSummary = summaryParts.join(" | ")
    }

    buildZoneMap().keySet().each { String zName ->
        Map m = getOrInitRoomTeacherModel(zName)
        m = evaluateRoomLifecycleAndSchedule(zName, m, nowTs)
        saveRoomTeacherModel(zName, m)
    }

    dailyStats.clear()
    String midnightStr = new Date().format("hh:mm a", location.timeZone)
    currentZones.each { String zName, Map z ->
        if (z?.occupancyState in ["active_motion", "stationary_occupied", "sleeping_occupied"]) {
            dailyStats.put(zName, [
                entryCount: 1,
                occupiedMs: 0L,
                peakScore: safeInt(z.score, 15),
                lastEntryTimeStr: "${midnightStr} (Cont.)",
                lastCountedEpochMs: nowTs
            ])
        }
    }

    putFastMap("dailyStats", dailyStats, true)
    flushRamCacheToAtomicState(true)
    atomicState.dailyStatsDateStr = newDateKey
    atomicState.lastStatsSampleEpochMs = nowTs
    logAction("🌙 Midnight Rollover & 7-Day/30-Day Learning Lifecycle Check complete.")
}

void pruneOrphanedDeviceState() {
    Set activeIds = getAllAnchorDevices().collect { it.id.toString() } as Set
    FAST_CACHE_KEYS.findAll { !(it in ["dailyStats", "zoneBleedMatrix", "pirTeacherModel"]) }.each { String mapKey ->
        ConcurrentHashMap<String, Object> fastMap = getFastMap(mapKey)
        List toRemove = []
        fastMap.keySet().each { k ->
            if (!activeIds.contains(k.toString())) toRemove << k.toString()
        }
        if (toRemove) {
            toRemove.each { fastMap.remove(it) }
            atomicState."${mapKey}" = new LinkedHashMap(fastMap)
        }
    }
}

void removeDevicesByIds(List<String> targetIds) {
    if (!targetIds) return
    Set removeSet = targetIds.collect { it.toString() } as Set

    List newZw = (zwaveAnchors ?: []).findAll { !removeSet.contains(it.id.toString()) }
    List newZb = (zigbeeAnchors ?: []).findAll { !removeSet.contains(it.id.toString()) }

    app.updateSetting("zwaveAnchors", [value: newZw, type: "capability.switch"])
    app.updateSetting("zigbeeAnchors", [value: newZb, type: "capability.switch"])
    app.removeSetting("devicesToRemove")

    removeSet.each { String devId ->
        app.removeSetting("zoneOverride_${devId}")
        app.removeSetting("deviceTriggerRole_${devId}")
        app.removeSetting("devicePortalRole_${devId}")
        app.removeSetting("activityPreset_${devId}")
        app.removeSetting("activityDistFt_${devId}")
        app.removeSetting("activityMinWatts_${devId}")
        app.removeSetting("activityCustom_${devId}")
    }

    FAST_CACHE_KEYS.findAll { !(it in ["dailyStats", "zoneBleedMatrix", "pirTeacherModel"]) }.each { String mapKey ->
        ConcurrentHashMap<String, Object> m = getFastMap(mapKey)
        removeSet.each { String id -> m.remove(id) }
        atomicState."${mapKey}" = new LinkedHashMap(m)
    }

    syncZoneChildDevices()
}

void deleteZonesByName(List<String> zoneNames, boolean alsoRemoveDevices) {
    if (!zoneNames) return
    Set targetZones = zoneNames.collect { it.toString() } as Set
    List excluded = ((atomicState.excludedZones ?: []).toList()).collect { it.toString() }

    List<String> devIdsToRemove = []
    if (alsoRemoveDevices) {
        getAllAnchorDevices().each { dev ->
            if (targetZones.contains(getDeviceZoneName(dev))) {
                devIdsToRemove << dev.id.toString()
            }
        }
    } else {
        targetZones.each { String zName ->
            if (!excluded.contains(zName)) excluded << zName
        }
        atomicState.excludedZones = excluded
    }

    Map zStatus = (atomicState.zoneStatus ?: [:]) as Map
    Map lastMot = (atomicState.lastActiveMotionTime ?: [:]) as Map
    Map lastExit = (atomicState.lastZoneExitEpochMs ?: [:]) as Map
    Map slpStart = (atomicState.zoneSleepCandidateStartMs ?: [:]) as Map
    Map entStart = (atomicState.zoneEntryCandidateStartMs ?: [:]) as Map
    Map stillStart = (atomicState.zoneStillStartMs ?: [:]) as Map
    Map portalState = (atomicState.zonePortalState ?: [:]) as Map
    ConcurrentHashMap<String, Object> dailyStats = getFastMap("dailyStats")
    ConcurrentHashMap<String, Object> teacherMap = getFastMap("pirTeacherModel")
    List recentRooms = ((atomicState.recentOccupiedRooms ?: []).toList()).findAll { !(it instanceof Map && targetZones.contains(it.zoneName?.toString())) }
    atomicState.recentOccupiedRooms = recentRooms

    targetZones.each { String zName ->
        zStatus.remove(zName)
        lastMot.remove(zName)
        lastExit.remove(zName)
        slpStart.remove(zName)
        entStart.remove(zName)
        stillStart.remove(zName)
        portalState.remove(zName)
        dailyStats.remove(zName)
        teacherMap.remove(zName)
        String zKey = sanitizeZoneKey(zName)
        app.removeSetting("zoneTimeout_${zKey}")
        app.removeSetting("zoneAllowSleep_${zKey}")
        app.removeSetting("zoneEntryMode_${zKey}")
        app.removeSetting("zoneEntryVerifySec_${zKey}")
        app.removeSetting("zoneSleepSwitch_${zKey}")
        app.removeSetting("adjacentZones_${zKey}")
        String dni = getZoneDni(zName)
        def child = getChildDevice(dni) ?: getChildDevices().find { it.deviceNetworkId == zName || (it.label && it.label.replace(" RF Motion & Presence", "").trim() == zName) }
        if (child) {
            try {
                deleteChildDevice(child.deviceNetworkId)
            } catch (Exception e) {
                log.warn "Could not delete child device ${child.deviceNetworkId}: ${e.message}"
            }
        }
    }

    putFastMap("dailyStats", dailyStats, true)
    putFastMap("pirTeacherModel", teacherMap, true)
    atomicState.zoneStatus = zStatus
    atomicState.lastActiveMotionTime = lastMot
    atomicState.lastZoneExitEpochMs = lastExit
    atomicState.zoneSleepCandidateStartMs = slpStart
    atomicState.zoneEntryCandidateStartMs = entStart
    atomicState.zoneStillStartMs = stillStart
    atomicState.zonePortalState = portalState
    app.removeSetting("zonesToDelete")

    if (devIdsToRemove) {
        removeDevicesByIds(devIdsToRemove)
    } else {
        syncZoneChildDevices()
    }
}

// ==============================================================================
// PLAIN-ENGLISH STATUS & DIAGNOSTIC BUILDERS
// ==============================================================================

String buildZoneDiagnosticHtml(String zoneName, Map z) {
    if (!z) return "Room is empty and signal is calm."
    String occ = z.occupancyState ?: "empty"
    int ppl = safeInt(z.peopleCount, 0)
    double dist = safeDouble(z.closestDistanceFt, 25.0d)
    String node = z.closestNodeName ?: "Wall Switch"
    String act = z.currentActivity ?: "None"
    String postStr = z.posture ?: "Occupied"
    String triStr = (z.multiSensorConsensus == true) ? " <i>(Corroborated by ${z.activeSensorCount} room sensors)</i>" : ""
    String pirStr = (z.pirActiveNow == true) ? " <b>[👁️ Motion Sensor Active: ${z.pirPlacementSummary ?: 'Room'}]</b>" : ""
    String vibStr = (z.vibActiveNow == true || z.vibRecentlyActive == true) ? " <b>[📳 Vibration Teacher: ${z.vibMeaningLabel ?: 'Posture Confirmed'}]</b>" : ""
    String portalStr = z.portalVectorStatus ? " <b>[${z.portalVectorStatus}]</b>" : ""
    Map tInfo = getRoomEffectiveLearnedThresholds(zoneName)
    String learnedNote = " <span style='color:#4f46e5;'>[🎓 ${tInfo.graduated ? 'Graduated' : 'Day ' + Math.max(1, tInfo.daysLoggedCount) + '/7'} | Entry:${tInfo.entryThresh}% Hold:${tInfo.keepThresh}% | W:${tInfo.walkingSamples} St:${tInfo.standingSamples} Si:${tInfo.sittingSamples} L:${tInfo.layingSamples} Vib:${tInfo.vibrationSamples}]</span>"

    String baseHtml = ""
    if (occ == "sleeping_occupied") {
        if (z.sleepSwitchActive == true) {
            def swDev = getZoneSleepSwitchDevice(zoneName)
            baseHtml = "<b>🛌 Room Asleep Switch ON (${swDev?.displayName ?: 'Sleep Switch'}):</b> ${zoneName} is locked in Laying/Sleeping mode until switch turns OFF.${vibStr}${learnedNote}"
        } else {
            baseHtml = "<b>🛌 ${postStr}:</b> Person has been laying down / resting still in ${zoneName} for ${fetchSleepVerificationMinutes()}+ minutes. Keeping room occupied.${triStr}${pirStr}${vibStr}${portalStr}${learnedNote}"
        }
    } else if (occ == "active_motion") {
        baseHtml = "<b>🔴 ${postStr}:</b> Detected ${ppl} person${ppl > 1 ? 's' : ''} moving ~<b>${dist} ft</b> from <b>${node}</b> (3-Min Hold Active).${triStr}${pirStr}${vibStr}${portalStr}${learnedNote}"
    } else if (occ == "stationary_occupied") {
        baseHtml = "<b>🟢 ${postStr}:</b> Detected ${ppl} person${ppl > 1 ? 's' : ''} (${postStr}) ~<b>${dist} ft</b> from <b>${node}</b>. Holding room active (3-Min Hold).${triStr}${pirStr}${vibStr}${portalStr}${learnedNote}"
    } else if (occ == "pet_occupied") {
        baseHtml = "<b>🐾 Pet Only:</b> Small floor movement near <b>${node}</b> (lights stay off)."
    } else if (occ == "active_motion_cooldown") {
        baseHtml = "<b>🔵 3-Minute Occupancy Hold (${z.cooldownRemaining ?: 0}s left):</b> Actively re-pinging <b>${node}</b> to verify you are still in ${zoneName} before allowing it to go Empty (ON lights/fans will NOT lock this room active if you left).${portalStr}${learnedNote}"
    } else if (z.suppressedByAntiTeleport == true) {
        baseHtml = "<b>🚫 Anti-Teleportation Guard:</b> Occupant is confirmed in a non-adjacent room (${atomicState.lastConfirmedPrimaryZone ?: 'Kitchen'}) — blocked stray jump across the house.${learnedNote}"
    } else if (z.suppressedByWallShield == true) {
        baseHtml = "<b>🛡️ Dark-Bathroom / Closet Guard:</b> Bathroom or closet switches are OFF while room is dark — route hop ignored.${learnedNote}"
    } else if (z.suppressedByCrossTalk == true) {
        baseHtml = "<b>🔇 Strict Household Cap / Handoff Cleared:</b> Cleared because occupant is active in a higher-ranked room.${learnedNote}"
    } else {
        baseHtml = "<b>🟢 Empty & Synchronized:</b> No one is in ${zoneName}. All room devices match their Trimmed-Median baseline.${learnedNote}"
    }

    if (act && act != "None") {
        baseHtml += " | <b>Activity: ${act}</b>."
    }
    return baseHtml
}

String getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
        return "⏸️ <b>Automation Paused:</b> Master Enable/Disable Switch is currently OFF."
    }
    List allDevs = getAllAnchorDevices()
    if (!allDevs) {
        return "⏳ <b>Waiting for Setup:</b> Select your switches and plugs in Section 0."
    }

    Map zoneStatus = atomicState.zoneStatus ?: [:]
    Map house = atomicState.houseSummary ?: [:]
    int totPpl = safeInt(house.detectedPeople, 0)
    int homePpl = safeInt(house.peopleHomeNow, 1)
    boolean childPresent = (house.childSensorPresent == true)
    String childSrc = (house.childTriggerSource ?: "Child Present").toString()
    String childNote = childPresent ? " [${childSrc}]" : ""

    List activeParts = []
    zoneStatus.each { String zName, Map z ->
        if (z.occupancyState in ["active_motion", "stationary_occupied", "sleeping_occupied"]) {
            activeParts << "<b>${zName}</b> (${z.posture ?: 'Occupied'}, ~${z.closestDistanceFt ?: '--'}ft from ${z.closestNodeName ?: 'switch'})"
        } else if (z.occupancyState == "active_motion_cooldown") {
            activeParts << "<b>${zName}</b> (3-Min Hold: ${z.cooldownRemaining ?: 0}s left)"
        }
    }

    if (activeParts) {
        return "🏠 <b>${totPpl} Active Room(s) (Household Cap: ${homePpl}${childNote}):</b> " + activeParts.join(" • ")
    }
    return "🟢 <b>ALL ${zoneStatus.size()} MONITORED ROOMS ARE CURRENTLY CALM (Max ${homePpl} Active Room${homePpl > 1 ? 's' : ''}${childNote})</b>"
}

String getLiveDecisionBreakdown() {
    List allDevs = getAllAnchorDevices()
    if (!allDevs) return "Select switches in Section 0 to begin."

    Map zoneStatus = atomicState.zoneStatus ?: [:]
    List zSummaries = []
    zoneStatus.each { String zName, Map z ->
        String stateWord = safeInt(z.peopleCount, 0) > 0 ? "🔴 <b>${z.posture ?: 'Occupied'}</b>" : (z.occupancyState == "active_motion_cooldown" ? "🔵 <b>3-Min Hold (${z.cooldownRemaining}s)</b>" : "🟢 Empty")
        zSummaries << "<b>${zName}:</b> ${stateWord}"
    }
    return zSummaries ? zSummaries.join("  |  ") : "Scanning rooms..."
}

// ==============================================================================
// LIFECYCLE & EVENT HANDLERS
// ==============================================================================

def installed() {
    logAction("Advanced RF Tomography Manager v7.3.0 Installed.")
    initialize()
}

def updated() {
    logAction("Advanced RF Tomography Manager v7.3.0 Updated.")
    unsubscribe()
    unschedule()
    if (settings.logEnable) runIn(1800, "logsOff")
    initialize()
}

def uninstalled() {
    unschedule()
    clearFastRamCache()
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
    atomicState.initializedOnce = true
    if (atomicState.rfHistory == null) atomicState.rfHistory = [:]
    if (atomicState.baselines == null) atomicState.baselines = [:]
    if (atomicState.nodeScores == null) atomicState.nodeScores = [:]
    if (atomicState.nodeAutoTune == null) atomicState.nodeAutoTune = [:]
    if (atomicState.zoneBleedMatrix == null) atomicState.zoneBleedMatrix = [:]
    if (atomicState.pirTeacherModel == null) atomicState.pirTeacherModel = [:]
    if (atomicState.zoneStatus == null) atomicState.zoneStatus = [:]
    if (atomicState.lastActiveMotionTime == null) atomicState.lastActiveMotionTime = [:]
    if (atomicState.lastZoneExitEpochMs == null) atomicState.lastZoneExitEpochMs = [:]
    if (atomicState.actionHistory == null) atomicState.actionHistory = []
    if (atomicState.excludedZones == null) atomicState.excludedZones = []
    if (atomicState.zoneShadowHistory == null) atomicState.zoneShadowHistory = [:]
    if (atomicState.zoneSleepCandidateStartMs == null) atomicState.zoneSleepCandidateStartMs = [:]
    if (atomicState.zoneEntryCandidateStartMs == null) atomicState.zoneEntryCandidateStartMs = [:]
    if (atomicState.zoneStillStartMs == null) atomicState.zoneStillStartMs = [:]
    if (atomicState.zonePortalState == null) atomicState.zonePortalState = [:]
    if (atomicState.lastChildSyncSignature == null) atomicState.lastChildSyncSignature = [:]
    if (atomicState.lastSwitchToggleMs == null) atomicState.lastSwitchToggleMs = [:]
    if (atomicState.lastPowerChangeMs == null) atomicState.lastPowerChangeMs = [:]
    if (atomicState.lastKnownPowerW == null) atomicState.lastKnownPowerW = [:]
    if (atomicState.isolatedChildFlags == null) atomicState.isolatedChildFlags = [:]
    if (atomicState.nodeHitStreaks == null) atomicState.nodeHitStreaks = [:]
    if (atomicState.nodeFlatCounts == null) atomicState.nodeFlatCounts = [:]
    if (atomicState.dailyStats == null) atomicState.dailyStats = [:]
    if (atomicState.recentOccupiedRooms == null) atomicState.recentOccupiedRooms = []
    if (atomicState.dailyStatsDateStr == null) atomicState.dailyStatsDateStr = getCurrentDateKey()
    if (atomicState.lastStatsSampleEpochMs == null) atomicState.lastStatsSampleEpochMs = now()
    if (atomicState.lastPirActiveEpochMs == null) atomicState.lastPirActiveEpochMs = [:]
    if (atomicState.lastVibActiveEpochMs == null) atomicState.lastVibActiveEpochMs = [:]
    if (atomicState.lastVibDeviceEpochMs == null) atomicState.lastVibDeviceEpochMs = [:]
    if (atomicState.lastRttReceivedMs == null) atomicState.lastRttReceivedMs = [:]
    if (atomicState.lastConsumedRttMs == null) atomicState.lastConsumedRttMs = [:]
    if (atomicState.lastDualKineticProofMs == null) atomicState.lastDualKineticProofMs = 0L
    if (atomicState.lastPhysicalArrivalGraceMs == null) atomicState.lastPhysicalArrivalGraceMs = 0L
    if (atomicState.fastFollowDeviceQueue == null) atomicState.fastFollowDeviceQueue = []
    if (atomicState.interleaveActiveTurn == null) atomicState.interleaveActiveTurn = false

    autoMigrateThresholdsOnce()
    pruneOrphanedDeviceState()
    checkDailyMidnightRollover()

    syncZoneChildDevices()

    List allDevs = getAllAnchorDevices()
    if (allDevs) {
        subscribe(allDevs, "switch", "deviceActivityHandler")
        subscribe(allDevs, "level", "deviceActivityHandler")
        subscribe(allDevs, "power", "deviceActivityHandler")
        subscribe(allDevs, "energy", "deviceActivityHandler")
        subscribe(allDevs, "voltage", "deviceActivityHandler")
        subscribe(allDevs, "amperage", "deviceActivityHandler")
        subscribe(allDevs, "rssi", "deviceActivityHandler")
        subscribe(allDevs, "lqi", "deviceActivityHandler")
        subscribe(allDevs, "rttMs", "deviceActivityHandler")
        subscribe(allDevs, "baselineRttMs", "deviceActivityHandler")
        subscribe(allDevs, "rfJitterMs", "deviceActivityHandler")
        subscribe(allDevs, "rfSignalDb", "deviceActivityHandler")
        subscribe(allDevs, "healthStatus", "deviceActivityHandler")
    }

    if (motionAssistSensors) {
        subscribe(motionAssistSensors, "motion", "pirMotionAssistHandler")
    }

    List allVibDevs = getAllVibrationAssistDevices()
    if (allVibDevs) {
        subscribe(allVibDevs, "acceleration", "vibrationAssistHandler")
        subscribe(allVibDevs, "shock", "vibrationAssistHandler")
        subscribe(allVibDevs, "motion", "vibrationAssistHandler")
        subscribe(allVibDevs, "contact", "vibrationAssistHandler")
        subscribe(allVibDevs, "threeAxis", "vibrationAssistHandler")
    }

    if (presenceSensors) {
        subscribe(presenceSensors, "presence", "presenceSensorHandler")
    }

    if (childPresenceSensors) {
        subscribe(childPresenceSensors, "presence", "childPresenceSensorHandler")
    }

    List sleepSwitches = getAllConfiguredSleepSwitches()
    if (sleepSwitches) {
        subscribe(sleepSwitches, "switch", "roomSleepSwitchHandler")
    }

    subscribe(location, "mode", "modeChangeHandler")

    schedule("5 0 0 * * ?", "scheduledMidnightStatsReset")

    if (settings.autoCalibrateHour != false) {
        schedule("0 15 3 * * ?", "scheduledBaselineCalibration")
    }

    executeSynchronousPollAndEvaluate()
    flushRamCacheToAtomicState(true)
    startPollingLoop()
}

def childPresenceSensorHandler(evt) {
    if (!evt?.device) return
    logAction("👧 Child Presence Sensor '${evt.device.displayName}' changed to '${evt.value}'. Updating Household Census.")
    executeSynchronousPollAndEvaluate()
    flushRamCacheToAtomicState(true)
}

def roomSleepSwitchHandler(evt) {
    if (!evt?.device) return
    logAction("🛌 Room Asleep Switch '${evt.device.displayName}' turned ${evt.value.toUpperCase()}.")
    executeSynchronousPollAndEvaluate()
    flushRamCacheToAtomicState(true)
}

def presenceSensorHandler(evt) {
    if (!evt?.device) return
    logAction("📱 Adult Presence Sensor '${evt.device.displayName}' changed to '${evt.value}'. Updating Household Census.")
    executeSynchronousPollAndEvaluate()
    flushRamCacheToAtomicState(true)
}

def vibrationAssistHandler(evt) {
    if (!evt?.device) return
    long nowTs = now()
    String vId = evt.device.id.toString()
    String zName = getVibrationSensorZoneName(evt.device)
    String eName = (evt.name ?: "").toString()
    String eVal = (evt.value ?: "").toString().toLowerCase()

    boolean isPositive = (
        (eName == "acceleration" && eVal == "active") ||
        (eName == "shock" && (eVal == "detected" || eVal == "active")) ||
        (eName == "motion" && eVal == "active") ||
        (eName == "contact" && (eVal == "closed" || eVal == "open")) ||
        (eName == "threeAxis")
    )

    if (isPositive) {
        Map vibZoneMap = (atomicState.lastVibActiveEpochMs ?: [:]) as Map
        Map vibDevMap = (atomicState.lastVibDeviceEpochMs ?: [:]) as Map
        vibZoneMap[zName] = nowTs
        vibDevMap[vId] = nowTs
        atomicState.lastVibActiveEpochMs = vibZoneMap
        atomicState.lastVibDeviceEpochMs = vibDevMap
        atomicState.lastPhysicalArrivalGraceMs = nowTs

        trainRoomRfFromVibrationEvent(evt.device, eName, eVal, nowTs)
    }

    executeSynchronousPollAndEvaluate()
    flushRamCacheToAtomicState(false)
}

def appButtonHandler(String btn) {
    if (btn == "btnRefresh") {
        runCanarySonarBurst(true)
        executeSynchronousPollAndEvaluate()
        flushRamCacheToAtomicState(true)
    } else if (btn == "btnCalibrateAll") {
        logAction("Manual Empty-Room Reset triggered by user (7-Day Learned Models preserved).")
        calibrateAllNodesNow()
        executeSynchronousPollAndEvaluate()
        flushRamCacheToAtomicState(true)
        atomicState.lastMgmtMessage = "All rooms marked Empty and synced to their Trimmed-Median baselines (7-Day Learned Posture & RF models preserved)."
    } else if (btn == "btnResetDailyStats") {
        performMidnightStatsReset(getCurrentDateKey())
        executeSynchronousPollAndEvaluate()
        flushRamCacheToAtomicState(true)
        atomicState.lastMgmtMessage = "Today's room visit counters and timers have been reset."
    } else if (btn == "btnClearRecentRooms") {
        atomicState.recentOccupiedRooms = []
        atomicState.lastMgmtMessage = "Cleared the Last 3 Occupied Rooms history trail."
    } else if (btn == "btnStartMonthlyReEvalNow") {
        long nowTs = now()
        long reEvalCycleMs = (fetchMonthlyReEvalDays() as long) * 86400000L
        buildZoneMap().keySet().each { String zName ->
            Map m = getOrInitRoomTeacherModel(zName)
            m.inActiveLearningWindow = true
            m.graduated = false
            m.learningStartEpochMs = nowTs
            m.learningDaysLogged = [getCurrentDateKey()]
            m.learningCycleNumber = safeInt(m.learningCycleNumber, 1) + 1
            m.nextReEvalEpochMs = nowTs + reEvalCycleMs
            m.lastTrainedStr = "Fresh 7-Day Re-Eval Started"
            saveRoomTeacherModel(zName, m)
        }
        executeSynchronousPollAndEvaluate()
        flushRamCacheToAtomicState(true)
        atomicState.lastMgmtMessage = "Started a fresh 7-Day Re-Evaluation pass across all rooms while keeping prior learned baselines anchored!"
    } else if (btn == "btnResetPirTeacherModels") {
        getFastMap("pirTeacherModel").clear()
        atomicState.pirTeacherModel = [:]
        executeSynchronousPollAndEvaluate()
        flushRamCacheToAtomicState(true)
        atomicState.lastMgmtMessage = "Wiped all learned 7-Day Posture & RF models. Rooms will begin Day 1 of 7 fresh training."
    } else if (btn == "btnApplyRecommendedTuning") {
        atomicState.v730ThresholdsMigrated = false
        getFastMap("nodeAutoTune").clear()
        getFastMap("zoneBleedMatrix").clear()
        atomicState.nodeAutoTune = [:]
        atomicState.zoneBleedMatrix = [:]
        autoMigrateThresholdsOnce()
        calibrateAllNodesNow()
        executeSynchronousPollAndEvaluate()
        flushRamCacheToAtomicState(true)
        atomicState.lastMgmtMessage = "Applied v7.3.0 (Vibration/Multipurpose Chair & Bed Posture Teachers + Child Presence Sensor Only + Fan Immunity + Anti-Teleport Guard)."
    } else if (btn == "btnClearLogs") {
        atomicState.actionHistory = []
    } else if (btn == "btnRemoveSelectedDevices") {
        List<String> toRem = (settings.devicesToRemove ?: []).collect { it.toString() }
        if (toRem) {
            removeDevicesByIds(toRem)
            atomicState.lastMgmtMessage = "Removed ${toRem.size()} selected device(s)."
        }
    } else if (btn == "btnDeleteSelectedZonesOnly") {
        List<String> zDel = (settings.zonesToDelete ?: []).collect { it.toString() }
        if (zDel) {
            deleteZonesByName(zDel, false)
            atomicState.lastMgmtMessage = "Deleted Room Zone(s): ${zDel.join(', ')}."
        }
    } else if (btn == "btnDeleteSelectedZonesAndDevices") {
        List<String> zDel = (settings.zonesToDelete ?: []).collect { it.toString() }
        if (zDel) {
            deleteZonesByName(zDel, true)
            atomicState.lastMgmtMessage = "Deleted Room Zone(s) (${zDel.join(', ')}) and unselected their switches."
        }
    } else if (btn == "btnRestoreExcludedZones") {
        atomicState.excludedZones = []
        syncZoneChildDevices()
        executeSynchronousPollAndEvaluate()
        flushRamCacheToAtomicState(true)
        atomicState.lastMgmtMessage = "Restored all excluded room zones."
    }
}

def pirMotionAssistHandler(evt) {
    if (!evt?.device) return
    long nowTs = now()
    String zName = getMotionSensorZoneName(evt.device)
    Map pirMap = (atomicState.lastPirActiveEpochMs ?: [:]) as Map
    if (evt.value == "active") {
        pirMap[zName] = nowTs
        atomicState.lastPirActiveEpochMs = pirMap
        atomicState.lastPhysicalArrivalGraceMs = nowTs
    }
    trainRoomRfFromPirEvent(evt.device, evt.value.toString(), nowTs)
    executeSynchronousPollAndEvaluate()
    flushRamCacheToAtomicState(false)
}

def deviceActivityHandler(evt) {
    long nowTs = now()
    String devId = evt.deviceId.toString()
    ConcurrentHashMap<String, Object> pingSentMap = getFastMap("lastPingSent")
    ConcurrentHashMap<String, Object> rttMap = getFastMap("measuredRtt")
    ConcurrentHashMap<String, Object> lastRttRecvMap = getFastMap("lastRttReceivedMs")

    if (evt.name in ["switch", "level"]) {
        ConcurrentHashMap<String, Object> toggleMap = getFastMap("lastSwitchToggleMs")
        toggleMap.put(devId, nowTs)
        if (evt.value == "on" && evt.device && !isCeilingFanSwitch(evt.device)) {
            atomicState.lastPhysicalArrivalGraceMs = nowTs
        }
        pingSentMap.remove(devId)
        return
    }

    Long sentTs = pingSentMap.remove(devId) as Long
    if (sentTs != null && (nowTs - sentTs) >= 4L && (nowTs - sentTs) <= 1500L && evt.name != "rttMs") {
        rttMap.put(devId, (nowTs - sentTs) as Double)
        lastRttRecvMap.put(devId, nowTs)
    }

    if (evt.name == "rttMs") {
        double driverRtt = extractDouble(evt.value, 150.0d)
        rttMap.put(devId, Math.max(8.0d, Math.min(1500.0d, driverRtt)))
        lastRttRecvMap.put(devId, nowTs)
        return
    }

    if (evt.name == "power") {
        ConcurrentHashMap<String, Object> powerValMap = getFastMap("lastKnownPowerW")
        ConcurrentHashMap<String, Object> powerTsMap = getFastMap("lastPowerChangeMs")
        double newW = extractDouble(evt.value, 0.0d)
        Double prevW = powerValMap.get(devId) as Double
        powerValMap.put(devId, newW)
        if (prevW != null && Math.abs(newW - prevW) >= 15.0d) {
            powerTsMap.put(devId, nowTs)
            pingSentMap.remove(devId)
            return
        }
    }
}

// ==============================================================================
// HARDWARE CLASSIFICATION, PIR MAPPING & ZONE PROVISIONING
// ==============================================================================

List getAllAnchorDevices() {
    Map unique = [:]
    zwaveAnchors?.each { unique[it.id.toString()] = it }
    zigbeeAnchors?.each { unique[it.id.toString()] = it }
    return unique.values().toList()
}

Boolean isZwaveDevice(dev) {
    String dni = dev.deviceNetworkId?.trim()?.toUpperCase() ?: ""
    if (dni ==~ /^[0-9A-F]{2}$/) return true
    if (dni ==~ /^[0-9A-F]{4}$/) return false
    if (zwaveAnchors?.any { it.id == dev.id } && !zigbeeAnchors?.any { it.id == dev.id }) return true
    return false
}

String getDeviceZoneName(dev) {
    String custom = settings["zoneOverride_${dev.id}"]?.toString()?.trim()
    if (custom) return custom
    if (dev.roomName && dev.roomName.trim()) return dev.roomName.trim()
    return inferRoomFromLabel(dev.displayName)
}

String getMotionSensorZoneName(pirDev) {
    if (!pirDev) return "General Zone"
    String custom = settings["pirZoneOverride_${pirDev.id}"]?.toString()?.trim()
    if (custom) return custom
    if (pirDev.roomName && pirDev.roomName.trim()) return pirDev.roomName.trim()
    return inferRoomFromLabel(pirDev.displayName)
}

Map buildPirZoneMap() {
    Map pirZones = [:]
    motionAssistSensors?.each { pirDev ->
        String zName = getMotionSensorZoneName(pirDev)
        if (!pirZones[zName]) pirZones[zName] = []
        pirZones[zName] << pirDev
    }
    return pirZones
}

String inferRoomFromLabel(String label) {
    if (!label) return "General Zone"
    List tokens = label.trim().split(/\s+/)
    if (tokens.size() >= 3) {
        String firstThree = "${tokens[0]} ${tokens[1]} ${tokens[2]}"
        if (firstThree.toLowerCase() == "master bedroom closet") {
            return firstThree
        }
    }
    if (tokens.size() >= 2) {
        String firstTwo = "${tokens[0]} ${tokens[1]}"
        List commonTwoWordRooms = [
            "master bathroom", "master bedroom", "living room", "dining room",
            "family room", "guest bedroom", "guest bathroom", "laundry room",
            "front porch", "back porch", "farm stand", "mud room", "kids room", "home office",
            "leannes bedroom", "leanne's bedroom"
        ]
        if (commonTwoWordRooms.contains(firstTwo.toLowerCase())) {
            return firstTwo
        }
    }
    return tokens[0]
}

Map buildZoneMap() {
    Map zones = [:]
    Set excluded = ((atomicState.excludedZones ?: []).toList()).collect { it.toString() } as Set
    getAllAnchorDevices().each { dev ->
        String zName = getDeviceZoneName(dev)
        if (!excluded.contains(zName)) {
            if (!zones[zName]) zones[zName] = []
            zones[zName] << dev
        }
    }
    return zones
}

String getZoneDni(String zoneName) {
    String clean = (zoneName ?: "General").replaceAll(/[^a-zA-Z0-9]/, "_").toUpperCase()
    return "KINETIC_SHADOW_ZONE_${clean}"
}

def syncZoneChildDevices() {
    Map zones = buildZoneMap()
    List activeDnis = []

    zones.each { String zoneName, List devs ->
        String dni = getZoneDni(zoneName)
        activeDnis << dni
        def child = getChildDevice(dni)
        if (!child) {
            String label = "${zoneName} RF Motion & Presence"
            try {
                child = addChildDevice("shaneallen", "Kinetic Shadow Zone Sensor", dni, [
                    name: "Kinetic Shadow Zone Sensor",
                    label: label,
                    isComponent: false
                ])
                child?.initialize()
            } catch (Exception e) {
                log.error "Failed to create Child Sensor '${label}': ${e.message}"
            }
        }
    }

    getChildDevices().each { child ->
        if (!activeDnis.contains(child.deviceNetworkId)) {
            try {
                deleteChildDevice(child.deviceNetworkId)
            } catch (Exception ignored) {}
        }
    }
}

// ==============================================================================
// v7.3.0 PRIORITY ACTIVE-ROOM & COOLDOWN RE-PING SONAR LOOP
// ==============================================================================

def startPollingLoop() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    int interval = Math.max(2, safeInt(settings.pollIntervalSec, 2))
    runIn(interval, "tomographyPollCycle", [overwrite: true])
}

def tomographyPollCycle() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    try {
        checkDailyMidnightRollover()
        harvestPlugLastActivityReplies()
        executeSynchronousPollAndEvaluate()
        runCanarySonarBurst(false)
        flushRamCacheToAtomicState(false)
    } catch (Throwable t) {
        if (settings.logEnable) log.warn "Tomography poll cycle caught error: ${t.class.simpleName} - ${t.message}"
    } finally {
        startPollingLoop()
    }
}

def runCanarySonarBurst(boolean forceNow = false) {
    Map zones = buildZoneMap()
    if (!zones) return

    long nowTs = now()
    long switchBlankerMs = fetchSwitchBlankerMs()
    ConcurrentHashMap<String, Object> pingSentMap = getFastMap("lastPingSent")
    ConcurrentHashMap<String, Object> toggleMap = getFastMap("lastSwitchToggleMs")
    Map zoneStatusMap = (atomicState.zoneStatus ?: [:]) as Map

    List<String> zoneNames = zones.keySet().collect { it.toString() }
    if (!zoneNames) return

    List burstDevs = []
    List<String> pingedRoomLabels = []

    // Priority 1: Fast-Follow verification devices (any doorway switch that just saw Hit #1)
    List<String> ffQueue = ((atomicState.fastFollowDeviceQueue ?: []).toList()).collect { it.toString() }.unique()
    atomicState.fastFollowDeviceQueue = []
    if (ffQueue) {
        getAllAnchorDevices().findAll { ffQueue.contains(it.id.toString()) }.take(2).each { d ->
            burstDevs << d
            String zN = getDeviceZoneName(d)
            if (!pingedRoomLabels.contains(zN)) pingedRoomLabels << "${zN} (Verify)"
        }
    }

    // Priority 2: Interleave 1 currently Occupied or 3-Minute Cooldown room on every cycle
    List<String> activeOrHoldingZones = zoneNames.findAll { String zN ->
        Map zS = zoneStatusMap[zN] as Map
        return (zS?.occupancyState in ["active_motion", "stationary_occupied", "active_motion_cooldown"])
    }
    if (activeOrHoldingZones && burstDevs.size() < 2) {
        int actIdx = safeInt(atomicState.activeRoomSonarIdx, 0)
        String prioZone = activeOrHoldingZones[actIdx % activeOrHoldingZones.size()]
        atomicState.activeRoomSonarIdx = (actIdx + 1) % activeOrHoldingZones.size()

        (zones[prioZone] ?: []).each { dev ->
            if (burstDevs.size() < 2 && !burstDevs.any { it.id == dev.id }) {
                String devId = dev.id.toString()
                String role = getEffectiveDeviceTriggerRole(dev, zones[prioZone] ?: [dev])
                String pRole = getEffectiveDevicePortalRole(dev, zones[prioZone] ?: [dev])
                boolean isPlug = isSmartPlugOrApplianceNode(dev)
                boolean isSwOn = ((dev.currentValue("switch") ?: "off").toString().toLowerCase() == "on")
                Long lastToggleTs = toggleMap.get(devId) as Long
                boolean recentlyToggled = (lastToggleTs != null && (nowTs - lastToggleTs) < (switchBlankerMs + 1000L))
                if (!recentlyToggled && role != "support_only" && (isSwOn || (!isPlug && !isInteriorHoldRole(pRole)))) {
                    burstDevs << dev
                    if (!pingedRoomLabels.contains(prioZone)) pingedRoomLabels << "${prioZone} (Hold)"
                }
            }
        }
    }

    // Priority 3: Round-robin all rooms (up to 3 devices total per cycle)
    int zIdx = safeInt(atomicState.canaryZoneIndex, 0)
    int checkedRooms = 0
    while (burstDevs.size() < 3 && checkedRooms < zoneNames.size()) {
        String zName = zoneNames[(zIdx + checkedRooms) % zoneNames.size()]
        checkedRooms++

        (zones[zName] ?: []).each { dev ->
            if (burstDevs.size() < 3 && !burstDevs.any { it.id == dev.id }) {
                String devId = dev.id.toString()
                String role = getEffectiveDeviceTriggerRole(dev, zones[zName] ?: [dev])
                String pRole = getEffectiveDevicePortalRole(dev, zones[zName] ?: [dev])
                boolean isPlug = isSmartPlugOrApplianceNode(dev)
                boolean isSwOn = ((dev.currentValue("switch") ?: "off").toString().toLowerCase() == "on")
                Long lastToggleTs = toggleMap.get(devId) as Long
                boolean recentlyToggled = (lastToggleTs != null && (nowTs - lastToggleTs) < (switchBlankerMs + 1000L))

                boolean shouldPingNode = !recentlyToggled && (role != "support_only") && (
                    isSwOn || (!isPlug && !isInteriorHoldRole(pRole))
                )
                if (shouldPingNode) {
                    burstDevs << dev
                    if (!pingedRoomLabels.contains(zName)) pingedRoomLabels << zName
                }
            }
        }
    }
    atomicState.canaryZoneIndex = (zIdx + Math.max(1, checkedRooms)) % zoneNames.size()

    if (!burstDevs) return

    atomicState.lastPingedZoneName = pingedRoomLabels.join(" + ")
    burstDevs.eachWithIndex { dev, int idx ->
        if (idx > 0) pauseExecution(25)
        dispatchNonBlockingPing(dev, now(), pingSentMap)
    }
}

void dispatchNonBlockingPing(dev, long nowTs, ConcurrentHashMap<String, Object> pingSentMap) {
    if (!dev) return
    String devId = dev.id.toString()
    pingSentMap.put(devId, nowTs)

    boolean isPlug = isSmartPlugOrApplianceNode(dev)
    boolean hasCustomDriver = hasCustomTomographyDriver(dev)
    try {
        if (isPlug && !hasCustomDriver && dev.hasCommand("refresh")) {
            dev.refresh()
        } else if (dev.hasCommand("ping")) {
            dev.ping()
        } else if (dev.hasCommand("refresh")) {
            dev.refresh()
        }
    } catch (Exception ignored) {}
}

void harvestPlugLastActivityReplies() {
    long nowTs = now()
    ConcurrentHashMap<String, Object> pingSentMap = getFastMap("lastPingSent")
    ConcurrentHashMap<String, Object> rttMap = getFastMap("measuredRtt")
    ConcurrentHashMap<String, Object> lastRttRecvMap = getFastMap("lastRttReceivedMs")

    getAllAnchorDevices().findAll { isSmartPlugOrApplianceNode(it) && !hasCustomTomographyDriver(it) }.each { dev ->
        String devId = dev.id.toString()
        Long sentTs = pingSentMap.get(devId) as Long
        if (sentTs != null) {
            try {
                long postActMs = dev.getLastActivity()?.getTime() ?: 0L
                if (postActMs >= sentTs && (postActMs - sentTs) <= 950L) {
                    double rttVal = Math.max(10.0d, (postActMs - sentTs) as double)
                    rttMap.put(devId, rttVal)
                    lastRttRecvMap.put(devId, nowTs)
                    pingSentMap.remove(devId)
                }
            } catch (Exception ignored) {}
        }
    }
}

def executeSynchronousPollAndEvaluate() {
    long nowTs = now()
    atomicState.lastPollEpochMs = nowTs
    atomicState.lastPollTimeStr = new Date(nowTs).format("hh:mm:ss a", location.timeZone)
    List allDevs = getAllAnchorDevices()
    if (!allDevs) return

    ConcurrentHashMap<String, Object> rfHistoryMap = getFastMap("rfHistory")
    ConcurrentHashMap<String, Object> baselinesMap = getFastMap("baselines")
    ConcurrentHashMap<String, Object> nodeScoresMap = getFastMap("nodeScores")
    Map currentZoneStatus = (atomicState.zoneStatus ?: [:]) as Map

    boolean hasZwave = allDevs.any { isZwaveDevice(it) }
    boolean hasZigbee = allDevs.any { !isZwaveDevice(it) }

    if (hasZwave) {
        pollZwaveSync(rfHistoryMap, baselinesMap, nodeScoresMap, currentZoneStatus)
    }
    if (hasZigbee) {
        pollZigbeeSync(rfHistoryMap, baselinesMap, nodeScoresMap, currentZoneStatus)
    }

    evaluateAllZones(nodeScoresMap, baselinesMap)
}

def pollZwaveSync(Map rfHistoryMap, Map baselinesMap, Map nodeScoresMap, Map currentZoneStatus) {
    Map params = [
        uri: "http://127.0.0.1:8080",
        path: "/hub/zwaveDetails/json",
        contentType: "application/json",
        timeout: 3
    ]
    try {
        httpGet(params) { response ->
            if (response.status == 200 && response.data) {
                Map json = (response.data instanceof Map) ? response.data : (new JsonSlurper().parseText(response.data.toString()) as Map)
                processZwaveJson(json, rfHistoryMap, baselinesMap, nodeScoresMap, currentZoneStatus)
            }
        }
    } catch (Exception ignored) {}
}

def processZwaveJson(Map json, Map rfHistoryMap, Map baselinesMap, Map nodeScoresMap, Map currentZoneStatus) {
    Map nodesMap = (json?.nodes ?: json?.zwaveDevices ?: json) as Map
    if (!(nodesMap instanceof Map)) return

    long nowTs = now()
    ConcurrentHashMap<String, Object> rttMap = getFastMap("measuredRtt")
    ConcurrentHashMap<String, Object> isolatedFlags = getFastMap("isolatedChildFlags")

    getAllAnchorDevices().findAll { isZwaveDevice(it) }.each { dev ->
        String devId = dev.id.toString()
        String dni = dev.deviceNetworkId?.trim()?.toUpperCase()
        if (!dni) return

        Integer decNodeId = null
        try { decNodeId = Integer.parseInt(dni, 16) } catch (Exception ignored) {}

        def nodeEntry = nodesMap[dni] ?:
                        (decNodeId != null ? nodesMap[decNodeId.toString()] : null) ?:
                        (decNodeId != null ? nodesMap[String.format("0x%04X", decNodeId)] : null) ?:
                        (decNodeId != null ? nodesMap[String.format("0x%02X", decNodeId)] : null) ?:
                        nodesMap.find { k, v ->
                            (v instanceof Map) && (
                                v.nodeId?.toString() == decNodeId?.toString() ||
                                v.hexId?.toString()?.toUpperCase()?.endsWith(dni)
                            )
                        }?.value

        if (nodeEntry instanceof Map) {
            isolatedFlags.put(devId, false)
            Double rssiDb = extractZwaveRssi(nodeEntry)
            int routeChanges = extractInt(nodeEntry.routeChanges ?: nodeEntry.rc, 0)
            double hubRtt = extractDouble(nodeEntry.rttAvg ?: nodeEntry.rtt, 35.0d)
            if (rssiDb == null) rssiDb = 24.0d - (routeChanges * 0.25d)
            List hist = (rfHistoryMap[devId] ?: []) as List
            double prevRtt = hist ? safeDouble(hist[-1]?.rtt, hubRtt) : hubRtt
            double zwJitter = Math.abs(hubRtt - prevRtt) + (routeChanges > 0 ? 6.0d : 0.0d)
            recordNodeSample(dev, "zwave", "Z-Wave Direct", rssiDb, routeChanges, hubRtt, 0.0d, zwJitter, hubRtt, 0.0d, true, nowTs, rfHistoryMap, baselinesMap, nodeScoresMap, currentZoneStatus)
        } else {
            isolatedFlags.put(devId, true)
            double fallbackRtt = safeDouble(rttMap.get(devId), 35.0d)
            recordNodeSample(dev, "zwave", "Z-Wave Sonar", 22.0d, 1, fallbackRtt, 0.0d, 0.0d, fallbackRtt, 0.0d, true, nowTs, rfHistoryMap, baselinesMap, nodeScoresMap, currentZoneStatus)
        }
    }
}

Double extractZwaveRssi(Map nodeEntry) {
    def raw = nodeEntry.lwrRssi ?: nodeEntry.rssi ?: nodeEntry.signal
    if (raw == null) return null
    def matcher = (raw.toString() =~ /(-?\d+(\.\d+)?)/)
    if (matcher.find()) {
        double val = matcher.group(1) as double
        if (val != 0.0d && val >= -120.0d && val <= 120.0d) return val
    }
    return null
}

// ==============================================================================
// v7.3.0 ZIGBEE EVALUATOR
// ==============================================================================
def pollZigbeeSync(Map rfHistoryMap, Map baselinesMap, Map nodeScoresMap, Map currentZoneStatus) {
    long nowTs = now()
    ConcurrentHashMap<String, Object> rttMap = getFastMap("measuredRtt")
    ConcurrentHashMap<String, Object> lastRttRecvMap = getFastMap("lastRttReceivedMs")
    ConcurrentHashMap<String, Object> lastConsumedMap = getFastMap("lastConsumedRttMs")
    ConcurrentHashMap<String, Object> isolatedFlags = getFastMap("isolatedChildFlags")
    Map zones = buildZoneMap()

    List zbDevs = getAllAnchorDevices().findAll { !isZwaveDevice(it) }
    Map<String, Double> computedFreshJitters = [:]
    Map<String, Boolean> sampleFreshFlags = [:]

    zbDevs.each { dev ->
        String devId = dev.id.toString()
        String zName = getDeviceZoneName(dev)
        List zDevs = zones[zName] ?: [dev]
        String roleMode = getEffectiveDeviceTriggerRole(dev, zDevs)
        boolean isPlug = isSmartPlugOrApplianceNode(dev)

        Double customRttMs = dev.currentValue("rttMs") != null ? safeDouble(dev.currentValue("rttMs"), isPlug ? 28.0d : 160.0d) : null
        double measuredRtt = customRttMs != null ? customRttMs : safeDouble(rttMap.get(devId), isPlug ? 28.0d : 160.0d)
        rttMap.put(devId, measuredRtt)

        Long lastRecvTs = lastRttRecvMap.get(devId) as Long
        Long lastConsumedTs = lastConsumedMap.get(devId) as Long
        List hist = (rfHistoryMap[devId] ?: []) as List
        double prevHistRtt = hist ? safeDouble(hist[-1]?.rtt, measuredRtt) : measuredRtt

        boolean isUnconsumedPing = (lastRecvTs != null && (lastConsumedTs == null || lastRecvTs > lastConsumedTs))
        if (isUnconsumedPing && lastRecvTs != null) {
            lastConsumedMap.put(devId, lastRecvTs)
        }
        boolean isRecentlyActive = (lastRecvTs != null && (nowTs - lastRecvTs) <= 20000L)
        sampleFreshFlags[devId] = isUnconsumedPing

        double liveStepDiff = isUnconsumedPing ? Math.abs(measuredRtt - prevHistRtt) : 0.0d
        double effectiveFreshJitter = 0.0d
        if (roleMode != "support_only" && isRecentlyActive && isUnconsumedPing) {
            effectiveFreshJitter = Math.round(Math.min(130.0d, liveStepDiff) * 10.0d) / 10.0d
        }
        computedFreshJitters[devId] = effectiveFreshJitter
    }

    double meshJitterFloorMs = 0.0d
    atomicState.lastMeshJitterFloorMs = 0.0d

    zbDevs.each { dev ->
        String devId = dev.id.toString()
        String zName = getDeviceZoneName(dev)
        List zDevs = zones[zName] ?: [dev]
        String roleMode = getEffectiveDeviceTriggerRole(dev, zDevs)

        double measuredRtt = safeDouble(rttMap.get(devId), 160.0d)
        double freshJitterMs = safeDouble(computedFreshJitters[devId], 0.0d)
        boolean isFresh = (sampleFreshFlags[devId] == true)
        double driverBaseRtt = dev.currentValue("baselineRttMs") != null ? safeDouble(dev.currentValue("baselineRttMs"), measuredRtt) : measuredRtt
        double driverSigDb = dev.currentValue("rfSignalDb") != null ? safeDouble(dev.currentValue("rfSignalDb"), 50.0d) : 50.0d
        double driverVar = dev.currentValue("rfVariance") != null ? safeDouble(dev.currentValue("rfVariance"), 0.0d) : 0.0d

        if (roleMode == "support_only") {
            isolatedFlags.put(devId, false)
            recordNodeSample(dev, "zigbee", "Muted Smart Plug", 50.0d, 1, 28.0d, 0.0d, 0.0d, 28.0d, 0.0d, false, nowTs, rfHistoryMap, baselinesMap, nodeScoresMap, currentZoneStatus)
        } else if (roleMode == "support_assist") {
            isolatedFlags.put(devId, true)
            recordNodeSample(dev, "zigbee", "Active Plug Sonar", driverSigDb, 1, measuredRtt, driverVar, freshJitterMs, driverBaseRtt, meshJitterFloorMs, isFresh, nowTs, rfHistoryMap, baselinesMap, nodeScoresMap, currentZoneStatus)
        } else {
            isolatedFlags.put(devId, true)
            recordNodeSample(dev, "zigbee", "Unified Switch Sonar", driverSigDb, 1, measuredRtt, driverVar, freshJitterMs, driverBaseRtt, meshJitterFloorMs, isFresh, nowTs, rfHistoryMap, baselinesMap, nodeScoresMap, currentZoneStatus)
        }
    }
}

// ==============================================================================
// v7.3.0 NODE SCORER (CEILING FAN IMMUNITY + OFF CLOSET LOCK + LIGHT ASSIST)
// ==============================================================================

def recordNodeSample(dev, String protocol, String sourceLabel, double driverSigVal, int routeMetric, double rttMs, double driverVar, double liveJitterMs, double driverOrFallbackBaseRtt, double meshJitterFloorMs, boolean isFreshSample, long timestamp,
                     Map rfHistoryMap, Map baselinesMap, Map nodeScoresMap, Map currentZoneStatus) {
    String devId = dev.id.toString()
    String zoneName = getDeviceZoneName(dev)
    List zoneDevs = buildZoneMap()[zoneName] ?: [dev]
    String swState = (dev.currentValue("switch") ?: "off").toString().toLowerCase()
    boolean isSwitchOn = (swState == "on")
    boolean isCeilingFan = isCeilingFanSwitch(dev)

    List history = ((rfHistoryMap[devId] ?: []).toList()).collect { it }
    Map base = (baselinesMap[devId] ?: [:]) as Map

    ConcurrentHashMap<String, Object> toggleMap = getFastMap("lastSwitchToggleMs")
    ConcurrentHashMap<String, Object> powerTsMap = getFastMap("lastPowerChangeMs")
    ConcurrentHashMap<String, Object> hitStreakMap = getFastMap("nodeHitStreaks")
    ConcurrentHashMap<String, Object> flatCountMap = getFastMap("nodeFlatCounts")
    ConcurrentHashMap<String, Object> lastRttRecvMap = getFastMap("lastRttReceivedMs")

    String roleMode = getEffectiveDeviceTriggerRole(dev, zoneDevs)
    String portalRole = getEffectiveDevicePortalRole(dev, zoneDevs)
    boolean isCoSensorPlug = (roleMode == "support_assist")
    boolean isInteriorSwitchOrPlug = isInteriorHoldRole(portalRole) || isCoSensorPlug
    boolean inConfirmedOccupiedZone = isDeviceInConfirmedOccupiedZone(dev, currentZoneStatus)

    Map learnedInfo = getRoomEffectiveLearnedThresholds(zoneName)
    int roomLearnedKeepThresh = safeInt(learnedInfo.keepThresh, fetchKeepAliveThreshold())
    int roomLearnedEntryThresh = safeInt(learnedInfo.entryThresh, fetchEntryMotionThreshold())
    boolean isLearnedPortalSwitch = (learnedInfo.learnedPortalSwitchId != null && learnedInfo.learnedPortalSwitchId.toString() == devId)
    boolean isLearnedInteriorNode = (learnedInfo.learnedInteriorNodeId != null && learnedInfo.learnedInteriorNodeId.toString() == devId)

    // HARD LOCK 1: Muted Smart Plugs, OFF Smart Plugs, OR OFF Closet/Interior Switches NEVER score motion!
    if (roleMode == "support_only" || (isInteriorSwitchOrPlug && !isSwitchOn)) {
        history = [[rawSig: 50.0d, sig: 50.0d, rc: 1, rtt: Math.round(rttMs), jitter: 0.0d, src: sourceLabel, t: timestamp]]
        rfHistoryMap[devId] = history
        hitStreakMap.put(devId, 0)
        flatCountMap.put(devId, 0)
        nodeScoresMap[devId] = [
            name: dev.displayName,
            score: 0,
            candidateScore: 0,
            scoreVelocity: 0,
            variance: 0.0d,
            stepDelta: 0.0d,
            shadowShift: 0.0d,
            effectiveShadow: 0.0d,
            netJitterMs: 0.0d,
            distanceFt: 25.0d,
            proximityBand: "No One Nearby (>20 ft)",
            nodePosture: isCoSensorPlug ? "🔌 Plug OFF (0%)" : (isInteriorHoldRole(portalRole) ? "🛡️ Closet/Interior OFF (0%)" : "🛡️ Muted Plug"),
            triggerRole: roleMode,
            portalRole: portalRole,
            isLearnedPortalSwitch: isLearnedPortalSwitch,
            isLearnedInteriorNode: isLearnedInteriorNode,
            hitStreak: 0,
            switchState: swState,
            switchBlankerActive: false,
            activityTriggered: false,
            triggeredActivityLabel: null,
            protocol: protocol,
            plainReason: isCoSensorPlug ?
                "<b>[Plug OFF — Dark-Room Guard Active]</b> Smart plug is currently OFF, so background Zigbee route hops are ignored (0%)." :
                (isInteriorHoldRole(portalRole) ? "<b>[Closet/Interior Switch OFF — Locked 0%]</b> Back-of-room closet/tub switch is OFF so background Zigbee route hops never self-latch the room." : "Smart plug is muted (0% weight)."),
            t: timestamp
        ]
        return
    }

    long blankerWindowMs = fetchSwitchBlankerMs()
    Long lastToggleTs = toggleMap.get(devId) as Long
    Long lastPowerTs = powerTsMap.get(devId) as Long

    boolean switchBlankerActive = (lastToggleTs != null) && ((timestamp - lastToggleTs) < blankerWindowMs)
    boolean powerEmiBlankerActive = (lastPowerTs != null) && ((timestamp - lastPowerTs) < 5000L)

    Map prevNodeMap = (nodeScoresMap[devId] ?: [:]) as Map
    Long lastRecvTs = lastRttRecvMap.get(devId) as Long

    if (!isFreshSample && !switchBlankerActive && !powerEmiBlankerActive && !prevNodeMap.isEmpty()) {
        long ageMs = (lastRecvTs != null) ? (timestamp - lastRecvTs) : 99999L
        long maxHoldAgeMs = inConfirmedOccupiedZone ? 20000L : 12000L
        if (ageMs <= maxHoldAgeMs) {
            prevNodeMap.switchState = swState
            prevNodeMap.portalRole = portalRole
            prevNodeMap.isLearnedPortalSwitch = isLearnedPortalSwitch
            prevNodeMap.isLearnedInteriorNode = isLearnedInteriorNode
            prevNodeMap.t = timestamp
            nodeScoresMap[devId] = prevNodeMap
            return
        } else {
            hitStreakMap.put(devId, 0)
            prevNodeMap.score = 0
            prevNodeMap.candidateScore = 0
            prevNodeMap.hitStreak = 0
            prevNodeMap.shadowShift = 0.0d
            prevNodeMap.effectiveShadow = 0.0d
            prevNodeMap.netJitterMs = 0.0d
            prevNodeMap.t = timestamp
            nodeScoresMap[devId] = prevNodeMap
            return
        }
    }

    double minRttFloor = (protocol == "zwave") ? 10.0d : (isCoSensorPlug ? 12.0d : 55.0d)
    double clampedLiveRtt = Math.max(minRttFloor, Math.min(550.0d, rttMs))

    if (base.isEmpty()) {
        double initMed = isCoSensorPlug ? Math.min(45.0d, clampedLiveRtt) : Math.min(340.0d, Math.max(minRttFloor, clampedLiveRtt))
        double initCeil = initMed + (isCoSensorPlug ? 6.0d : 32.0d)
        double initMad = isCoSensorPlug ? 3.5d : 14.0d
        base = [
            mean: (protocol == "zwave" ? driverSigVal : 50.0d),
            meanOff: (protocol == "zwave" ? driverSigVal : 50.0d),
            meanOn: (protocol == "zwave" ? driverSigVal : 50.0d),
            baseRtt: initCeil,
            medianRtt: initMed,
            rttOff: initCeil,
            rttOn: initCeil,
            medOff: initMed,
            medOn: initMed,
            madOff: initMad,
            madOn: initMad,
            baseJitter: 0.0d,
            lastSwState: swState,
            restingVar: 0.002d,
            personalDeadbandDb: fetchGlobalDeadbandDb(),
            samples: 1,
            calibratedAt: timestamp
        ]
        baselinesMap[devId] = base
    }

    String prevSwState = (base.lastSwState ?: swState).toString()
    boolean stateJustChanged = (prevSwState != swState)

    List<Double> priorRtts = history.collect { safeDouble(it?.rtt, clampedLiveRtt) }
    if (!priorRtts) priorRtts = [clampedLiveRtt]
    double prevRttSample = priorRtts[-1]
    double signedStepMs = isFreshSample ? (clampedLiveRtt - prevRttSample) : 0.0d
    double absStepMs = isFreshSample ? Math.max(liveJitterMs, Math.abs(signedStepMs)) : 0.0d

    double storedMedianRtt = isSwitchOn ?
        safeDouble(base.medOn != null ? base.medOn : base.medianRtt, clampedLiveRtt) :
        safeDouble(base.medOff != null ? base.medOff : base.medianRtt, clampedLiveRtt)
    double storedAnchorRtt = isSwitchOn ?
        safeDouble(base.rttOn != null ? base.rttOn : base.baseRtt, clampedLiveRtt + 32.0d) :
        safeDouble(base.rttOff != null ? base.rttOff : base.baseRtt, clampedLiveRtt + 32.0d)
    double storedMadMs = isSwitchOn ?
        safeDouble(base.madOn, isCoSensorPlug ? 3.5d : 14.0d) :
        safeDouble(base.madOff, isCoSensorPlug ? 3.5d : 14.0d)

    if (!isCoSensorPlug && !isSwitchOn && !inConfirmedOccupiedZone && (clampedLiveRtt - storedMedianRtt) > 95.0d && absStepMs < 95.0d) {
        storedMedianRtt = Math.round(((storedMedianRtt * 0.35d) + (clampedLiveRtt * 0.65d)) * 10.0d) / 10.0d
        storedAnchorRtt = Math.round((storedMedianRtt + 34.0d) * 10.0d) / 10.0d
    }

    double refMedianRtt = Math.max(minRttFloor, storedMedianRtt)
    double minSpreadMs = isCoSensorPlug ? 6.0d : Math.max(28.0d, storedMadMs * 2.0d)
    double refBaseRtt = Math.max(refMedianRtt + minSpreadMs, Math.min(refMedianRtt + 48.0d, storedAnchorRtt))

    double learnedGateCap = learnedInfo.hasLearnedData ? safeDouble(learnedInfo.entryStepGateMs, 16.0d) : 16.0d
    double personalJitterGateMs = (protocol == "zwave") ? 6.0d : (isCoSensorPlug ?
        Math.max(4.0d, storedMadMs * 1.5d) :
        Math.max(learnedGateCap, Math.min(34.0d, storedMadMs * 1.70d)))

    double positiveShiftMs = Math.max(0.0d, clampedLiveRtt - refMedianRtt)
    double positiveCeilingExcessMs = Math.max(0.0d, clampedLiveRtt - refBaseRtt)

    double zwaveRssiShadowDb = 0.0d
    if (protocol == "zwave") {
        double baseZwSig = safeDouble(isSwitchOn ? base.meanOn : base.meanOff, driverSigVal)
        zwaveRssiShadowDb = Math.max(0.0d, Math.min(1.80d, Math.abs(baseZwSig - driverSigVal) * 0.35d))
    }

    int prevHits = safeInt(hitStreakMap.get(devId), 0)

    boolean isFastReturnToBaseline = ((signedStepMs <= -15.0d) && (positiveCeilingExcessMs <= 12.0d) && (zwaveRssiShadowDb < 0.20d)) ||
                                     (protocol != "zwave" && !isCoSensorPlug && positiveShiftMs < 12.0d && positiveCeilingExcessMs <= 0.0d)

    boolean hasStepExcursion = !isFastReturnToBaseline && (
        (protocol == "zwave" && (zwaveRssiShadowDb >= 0.22d || absStepMs >= personalJitterGateMs)) ||
        (isCoSensorPlug ?
            (absStepMs >= personalJitterGateMs && positiveCeilingExcessMs > 0.0d) :
            (absStepMs >= personalJitterGateMs && signedStepMs > -12.0d && (positiveShiftMs >= 16.0d || positiveCeilingExcessMs >= 8.0d)))
    )

    boolean hasSustainedFollowUpShadow = !isCeilingFan && (prevHits >= 1) && (positiveCeilingExcessMs >= 20.0d || zwaveRssiShadowDb >= 0.24d)

    boolean hasLiveKineticJitter = isFreshSample && !switchBlankerActive && !powerEmiBlankerActive && (hasStepExcursion || hasSustainedFollowUpShadow)

    double effectiveStepJitterMs = hasLiveKineticJitter ? Math.max(absStepMs, hasSustainedFollowUpShadow ? (personalJitterGateMs + (positiveCeilingExcessMs * 0.50d)) : 0.0d) : 0.0d
    double plugScale = isCoSensorPlug ? (fetchPlugCoSensorWeightPct() / 100.0d) : (isLearnedPortalSwitch ? 1.10d : 1.0d)
    double netSwitchJitterMs = hasLiveKineticJitter ? Math.max(4.5d, effectiveStepJitterMs - (personalJitterGateMs * 0.70d)) : 0.0d
    double netPositiveExcessMs = hasLiveKineticJitter ? Math.max(0.0d, positiveCeilingExcessMs) : 0.0d

    double devDivisor = isCoSensorPlug ? 14.0d : 38.0d
    double jitDivisor = isCoSensorPlug ? 12.0d : 30.0d

    double rttShadowDb = Math.min(1.40d, (netPositiveExcessMs / devDivisor) * plugScale)
    double jitterShadowDb = Math.min(1.50d, (netSwitchJitterMs / jitDivisor) * plugScale)
    double synthesizedShadowDb = hasLiveKineticJitter ? Math.min(2.15d, (rttShadowDb * 0.70d) + (jitterShadowDb * 0.80d) + zwaveRssiShadowDb) : 0.0d
    double synthesizedSigDb = Math.round(Math.max(47.80d, 50.0d - synthesizedShadowDb) * 100.0d) / 100.0d

    if (stateJustChanged || switchBlankerActive || powerEmiBlankerActive) {
        base.lastSwState = swState
        baselinesMap[devId] = base
        synthesizedSigDb = 50.0d
        synthesizedShadowDb = 0.0d
    }

    long roundedRtt = Math.round(clampedLiveRtt)
    if (isFreshSample || history.isEmpty()) {
        history << [
            rawSig: synthesizedSigDb,
            sig: synthesizedSigDb,
            rc: routeMetric,
            rtt: roundedRtt,
            jitter: Math.round(absStepMs * 10.0d) / 10.0d,
            src: sourceLabel,
            t: timestamp
        ]
        while (history.size() > 6) {
            history.remove(0)
        }
        rfHistoryMap[devId] = history
    }

    // POST-SCORE TRIMMED-MEDIAN BASELINE UPDATE
    if (!switchBlankerActive && !powerEmiBlankerActive && isFreshSample) {
        List<Double> windowRtts = history.collect { safeDouble(it?.rtt, clampedLiveRtt) }.sort(false)
        double trimmedMeanRtt = clampedLiveRtt
        if (windowRtts.size() >= 4) {
            List<Double> middleSamples = windowRtts.subList(1, windowRtts.size() - 1)
            trimmedMeanRtt = (middleSamples.sum() as double) / middleSamples.size()
        } else {
            trimmedMeanRtt = (windowRtts.sum() as double) / windowRtts.size()
        }

        List<Double> absDevs = windowRtts.collect { Math.abs(it - trimmedMeanRtt) }.sort(false)
        double windowMad = absDevs[(int) Math.floor((absDevs.size() - 1) * 0.50d)]
        double clampedWindowMad = isCoSensorPlug ?
            Math.max(2.5d, Math.min(12.0d, windowMad)) :
            Math.max(9.0d, Math.min(20.0d, windowMad))

        double nextMed = refMedianRtt
        if (!inConfirmedOccupiedZone) {
            double blendRate = (!hasLiveKineticJitter) ? 0.30d : 0.15d
            double rawMed = (refMedianRtt * (1.0d - blendRate)) + (trimmedMeanRtt * blendRate)
            nextMed = Math.round(Math.max(minRttFloor, Math.min(isCoSensorPlug ? 55.0d : 350.0d, rawMed)) * 10.0d) / 10.0d
            if (protocol == "zwave") {
                if (isSwitchOn) base.meanOn = (safeDouble(base.meanOn, driverSigVal) * 0.80d) + (driverSigVal * 0.20d)
                else base.meanOff = (safeDouble(base.meanOff, driverSigVal) * 0.80d) + (driverSigVal * 0.20d)
            }
        } else if (trimmedMeanRtt < refMedianRtt) {
            nextMed = Math.round(((refMedianRtt * 0.75d) + (trimmedMeanRtt * 0.25d)) * 10.0d) / 10.0d
        }

        double nextMad = Math.round(((storedMadMs * 0.75d) + (clampedWindowMad * 0.25d)) * 10.0d) / 10.0d
        double nextCeil = Math.round((nextMed + Math.max(isCoSensorPlug ? 6.0d : 28.0d, nextMad * 2.0d)) * 10.0d) / 10.0d

        if (isSwitchOn) {
            base.medOn = nextMed
            base.rttOn = nextCeil
            base.madOn = nextMad
        } else {
            base.medOff = nextMed
            base.rttOff = nextCeil
            base.madOff = nextMad
        }
        base.medianRtt = nextMed
        base.baseRtt = nextCeil
        baselinesMap[devId] = base
    }

    double deadbandDb = fetchGlobalDeadbandDb()
    List recentWindow = history.takeRight(4)
    double windowMean = (recentWindow.collect { safeDouble(it?.sig, 50.0d) }.sum() as double) / recentWindow.size()
    double rollingVariance = (recentWindow.collect { Math.pow(safeDouble(it?.sig, 50.0d) - windowMean, 2) }.sum() as double) / recentWindow.size()

    double rawShadowShift = (!hasLiveKineticJitter || switchBlankerActive || powerEmiBlankerActive) ? 0.0d : synthesizedShadowDb
    double effectiveShadow = Math.max(0.0d, rawShadowShift - deadbandDb)
    double netVariance = (!hasLiveKineticJitter || switchBlankerActive || powerEmiBlankerActive) ? 0.0d : Math.max(0.0d, rollingVariance - 0.0015d)

    double stepDelta = 0.0d
    if (hasLiveKineticJitter && recentWindow.size() >= 2) {
        stepDelta = Math.abs(safeDouble(recentWindow[-1]?.sig, 50.0d) - safeDouble(recentWindow[-2]?.sig, 50.0d))
    }

    double varContribution = (netVariance * DEFAULT_VARIANCE_WEIGHT) + (stepDelta > 0.04d ? (stepDelta * 12.0d) : 0.0d)
    double shadContribution = effectiveShadow * DEFAULT_SHADOW_WEIGHT
    double rawScore = (!hasLiveKineticJitter || effectiveShadow <= 0.02d) ? 0.0d : (varContribution + shadContribution)

    int prevNodeScore = safeInt(prevNodeMap.score, 0)
    double smoothedRawScore = 0.0d
    if (rawScore <= 1.0d || !hasLiveKineticJitter) {
        smoothedRawScore = 0.0d
    } else if (rawScore >= prevNodeScore) {
        smoothedRawScore = (rawScore * 0.80d) + (prevNodeScore * 0.20d)
    } else {
        smoothedRawScore = (rawScore * 0.55d) + (prevNodeScore * 0.45d)
    }

    int candidateNodeScore = Math.min(100, Math.max(0, Math.round(smoothedRawScore).toInteger()))

    double scaledNetJitter = netSwitchJitterMs * plugScale
    boolean isMeaningfulHit = hasLiveKineticJitter && (candidateNodeScore >= roomLearnedKeepThresh) && (scaledNetJitter >= (isCoSensorPlug ? 2.0d : 3.0d))
    int currentHitStreak = isMeaningfulHit ? Math.min(10, prevHits + 1) : 0
    hitStreakMap.put(devId, currentHitStreak)

    if (currentHitStreak == 1 && !inConfirmedOccupiedZone) {
        List<String> ffQ = ((atomicState.fastFollowDeviceQueue ?: []).toList()).collect { it.toString() }
        if (!ffQ.contains(devId)) {
            ffQ << devId
            atomicState.fastFollowDeviceQueue = ffQ
        }
    }

    int nodeScore = 0
    if (isCeilingFan) {
        if (currentHitStreak >= 2) nodeScore = candidateNodeScore
    } else if (inConfirmedOccupiedZone || currentHitStreak >= 2) {
        nodeScore = candidateNodeScore
    }
    int scoreVelocity = isFreshSample ? (nodeScore - prevNodeScore) : 0

    double netShadowToReport = ((nodeScore > 0 || candidateNodeScore >= roomLearnedKeepThresh) && rawShadowShift >= 0.16d) ? Math.round(rawShadowShift * 100.0d) / 100.0d : 0.0d
    double estDistanceFt = (netShadowToReport > 0.16d) ? estimateDistanceFeet(netShadowToReport, netVariance) : 25.0d
    String proxBand = classifyProximityBand(estDistanceFt)
    String nodePosture = isCoSensorPlug ? (nodeScore >= roomLearnedKeepThresh ? "🪑 Sitting / Near Plug" : "🔌 Plug Calm (0%)") : classifyNodePosture(zoneName, netShadowToReport, netVariance, stepDelta, Math.max(nodeScore, candidateNodeScore))

    boolean wasActivityTriggered = (prevNodeMap.activityTriggered == true)
    String configuredAct = getConfiguredNodeActivityLabel(dev)
    double actRadiusFt = getConfiguredNodeActivityRadiusFt(dev)
    double effectiveRadiusFt = wasActivityTriggered ? (actRadiusFt + 1.5d) : actRadiusFt
    double actMinWatts = getConfiguredNodeActivityMinWatts(dev)
    Double livePowerW = dev.currentValue("power") != null ? safeDouble(dev.currentValue("power"), 0.0d) : null
    boolean powerGatePassed = (actMinWatts <= 0.0d) || (livePowerW != null && livePowerW >= actMinWatts)
    boolean hasHumanShadowNearNode = (Math.max(nodeScore, candidateNodeScore) >= roomLearnedKeepThresh && netShadowToReport >= 0.20d)
    boolean activityTriggered = (configuredAct != null && hasHumanShadowNearNode && estDistanceFt <= effectiveRadiusFt && powerGatePassed)

    int autoPlugWtPct = fetchPlugCoSensorWeightPct()
    String stateTag = isSwitchOn ? (isCeilingFan ? "FAN-ON" : "ON") : "OFF"
    String shiftDisplay = (clampedLiveRtt >= refMedianRtt) ? "+${Math.round(positiveShiftMs)}ms" : "${Math.round(clampedLiveRtt - refMedianRtt)}ms"
    String telemetryTag = "<b>[Ping: ${roundedRtt}ms | ${stateTag}-Med: ${Math.round(refMedianRtt)}ms | ${stateTag}-Ceil: ${Math.round(refBaseRtt)}ms | Diff: ${shiftDisplay} | Step: ${Math.round(absStepMs * 10.0d) / 10.0d}ms (Gate: ${Math.round(personalJitterGateMs)}ms)]</b> "
    String plainReason = telemetryTag + "Signal is calm and within this device's Trimmed-Median baseline."
    if (switchBlankerActive) {
        plainReason = telemetryTag + "Switch was physically toggled — ignoring relay click for ${fetchSwitchBlankerSeconds()} seconds."
    } else if (powerEmiBlankerActive) {
        plainReason = telemetryTag + "Appliance motor/power draw just changed — ignoring electrical surge for 5 seconds."
    } else if (isFastReturnToBaseline) {
        plainReason = telemetryTag + "Ping returned at or below baseline (${shiftDisplay}) — clearing route bounce (0%)."
    } else if (!hasLiveKineticJitter) {
        plainReason = telemetryTag + "Step change (${Math.round(absStepMs)}ms) is below personal noise gate (${Math.round(personalJitterGateMs)}ms) — steady route (0%)."
    } else if (nodeScore == 0 && currentHitStreak == 1) {
        plainReason = telemetryTag + "Kinetic Pulse #1 (${candidateNodeScore}%) — queued fast-follow verification ping."
    } else if (isCoSensorPlug) {
        if (nodeScore >= roomLearnedKeepThresh) {
            plainReason = telemetryTag + "Active Smart Plug (${autoPlugWtPct}% weight) detects verified presence (${nodePosture}) ~${estDistanceFt} ft away (${nodeScore}%)."
        } else {
            plainReason = telemetryTag + "Active Smart Plug (${autoPlugWtPct}% weight, ${Math.round(refMedianRtt)}ms baseline) is 0% calm — guarding dark room."
        }
    } else if (nodeScore >= roomLearnedEntryThresh) {
        plainReason = telemetryTag + "Detecting ${nodePosture} ~${estDistanceFt} ft from this ${getPortalRoleShortBadge(portalRole)} switch (${nodeScore}%, Shadow: ${netShadowToReport} dB)."
    } else if (nodeScore >= roomLearnedKeepThresh) {
        plainReason = telemetryTag + "Detecting ${nodePosture} ~${estDistanceFt} ft from this switch (${nodeScore}%, Shadow: ${netShadowToReport} dB)."
    }

    nodeScoresMap[devId] = [
        name: dev.displayName,
        score: nodeScore,
        scoreVelocity: scoreVelocity,
        candidateScore: candidateNodeScore,
        variance: Math.round(netVariance * 1000.0d) / 1000.0d,
        stepDelta: Math.round(stepDelta * 100.0d) / 100.0d,
        shadowShift: netShadowToReport,
        effectiveShadow: Math.round(effectiveShadow * 100.0d) / 100.0d,
        netJitterMs: Math.round(scaledNetJitter * 10.0d) / 10.0d,
        liveRttMs: roundedRtt,
        baseRttMs: Math.round(refBaseRtt),
        medBaseRttMs: Math.round(refMedianRtt),
        autoDeadbandDb: deadbandDb,
        autoPlugWeightPct: autoPlugWtPct,
        distanceFt: estDistanceFt,
        proximityBand: proxBand,
        nodePosture: nodePosture,
        triggerRole: roleMode,
        portalRole: portalRole,
        isLearnedPortalSwitch: isLearnedPortalSwitch,
        isLearnedInteriorNode: isLearnedInteriorNode,
        hitStreak: currentHitStreak,
        fastFollowQueued: (currentHitStreak == 1 && !inConfirmedOccupiedZone),
        switchState: swState,
        isCeilingFan: isCeilingFan,
        switchBlankerActive: switchBlankerActive,
        activityTriggered: activityTriggered,
        triggeredActivityLabel: activityTriggered ? configuredAct : null,
        protocol: protocol,
        plainReason: plainReason,
        reason: plainReason,
        t: timestamp
    ]
}

// ==============================================================================
// v7.3.0 ZONE EVALUATOR (VIBRATION POSTURE TEACHER + FAN IMMUNITY + LIGHT ASSIST + ANTI-TELEPORTATION)
// ==============================================================================

def evaluateAllZones(Map allNodeScores = null, Map baselinesMap = null) {
    Map zones = buildZoneMap()
    Set<String> allZoneNamesSet = zones.keySet().collect { it.toString() } as Set
    Map pirZones = buildPirZoneMap()
    long nowTs = now()
    long prevSampleTs = safeLong(atomicState.lastStatsSampleEpochMs, nowTs)
    long sampleDeltaMs = Math.max(0L, Math.min(15000L, nowTs - prevSampleTs))
    atomicState.lastStatsSampleEpochMs = nowTs

    double extraPersonStepReq = fetchAdditionalPersonShadow().doubleValue()
    long sleepVerifyReqMs = fetchSleepVerificationMs()
    long maxStationaryHoldMs = fetchMaxStationaryHoldMs()
    double partialExitDropReq = fetchPartialExitDropThreshold().doubleValue()
    int housePetsCap = fetchKnownHousePetsMax()
    boolean crossTalkFilterOn = (settings.enableCrossTalkSuppression != false)
    boolean adjacentWallShieldOn = (settings.enableAdjacentWallShield != false)
    boolean backOfRoomAndDarkBathGuardOn = (settings.enableMultiDeviceCorroboration != false)
    boolean allowTwoPeopleInOneRoom = (settings.allowMultiPersonPerSingleRoom == true)
    boolean antiTeleportGuardOn = (settings.enableAntiTeleportGuard != false)
    boolean lightSwitchAssistOn = (settings.enableLightSwitchAssist != false)

    if (allNodeScores == null) {
        allNodeScores = getFastMap("nodeScores")
    }
    if (baselinesMap == null) {
        baselinesMap = getFastMap("baselines")
    }
    ConcurrentHashMap<String, Object> dailyStatsMap = getFastMap("dailyStats")
    ConcurrentHashMap<String, Object> toggleMap = getFastMap("lastSwitchToggleMs")
    Map allZoneStatus = [:]
    Map prevZoneStatus = (atomicState.zoneStatus ?: [:]) as Map
    Map lastMotionMap = (atomicState.lastActiveMotionTime ?: [:]) as Map
    Map lastExitMap = (atomicState.lastZoneExitEpochMs ?: [:]) as Map
    Map shadowHistMap = (atomicState.zoneShadowHistory ?: [:]) as Map
    Map sleepCandStartMap = (atomicState.zoneSleepCandidateStartMs ?: [:]) as Map
    Map entryCandStartMap = (atomicState.zoneEntryCandidateStartMs ?: [:]) as Map
    Map stillStartMap = (atomicState.zoneStillStartMs ?: [:]) as Map
    Map portalStateMap = (atomicState.zonePortalState ?: [:]) as Map
    Map childSyncSigMap = (atomicState.lastChildSyncSignature ?: [:]) as Map
    Map lastPirMap = (atomicState.lastPirActiveEpochMs ?: [:]) as Map

    Map censusInfo = evaluateHouseholdPresenceCensus()
    int peopleHomeNow = safeInt(censusInfo.peopleHomeNow, 1)

    List<String> previouslyConfirmedZones = []
    prevZoneStatus.each { String zN, Map pz ->
        if (pz?.occupancyState in ["active_motion", "stationary_occupied", "sleeping_occupied"]) {
            previouslyConfirmedZones << zN
        }
    }
    if (previouslyConfirmedZones) {
        atomicState.lastConfirmedPrimaryZone = previouslyConfirmedZones[0]
        atomicState.lastConfirmedPrimaryZoneMs = nowTs
    }

    int houseTotalPeople = 0
    int houseTotalPets = 0
    int houseTotalSleeping = 0
    double globalClosestDist = 99.0d
    String globalClosestSwitch = "None"
    List<String> houseActiveActivities = []

    zones.each { String zoneName, List devs ->
        boolean anyNonFanLightSwitchOnInRoom = devs.any { dev ->
            isNonFanLightSwitch(dev) && ((dev.currentValue("switch") ?: "off").toString().toLowerCase() == "on")
        }
        boolean anyNonFanDeviceOnInRoom = devs.any { dev ->
            !isCeilingFanSwitch(dev) && ((dev.currentValue("switch") ?: "off").toString().toLowerCase() == "on")
        }

        long baseHoldSec = fetchZoneMotionTimeoutSeconds(zoneName) as long
        long effectiveHoldSec = Math.max(180L, baseHoldSec)
        long zoneHoldMs = effectiveHoldSec * 1000L

        boolean sleepSwitchActiveNow = isZoneSleepSwitchOn(zoneName)
        boolean sleepAllowed = isSleepAllowedInZone(zoneName)
        boolean zoneImmediateDefault = isImmediateEntryAllowedInZone(zoneName)
        int zoneEntryVerifySec = fetchZoneEntryVerifySeconds(zoneName)
        boolean isBathroomZone = isBathroomOrShowerRoomName(zoneName)

        List primaryWallSwitchesInZone = devs.findAll { !(getEffectiveDeviceTriggerRole(it, devs) in ["support_only", "support_assist"]) }
        List coSensorPlugsInZone = devs.findAll { getEffectiveDeviceTriggerRole(it, devs) == "support_assist" }
        int activeWallSwitchCount = primaryWallSwitchesInZone.size()
        int coSensorPlugCount = coSensorPlugsInZone.size()
        int totalTrackingSensorCount = activeWallSwitchCount + coSensorPlugCount
        int quarantinedPlugCount = devs.size() - totalTrackingSensorCount

        List roomPirSensors = pirZones[zoneName] ?: []
        int pirSensorCount = roomPirSensors.size()
        boolean pirActiveNow = roomPirSensors.any { (it.currentValue("motion") ?: "inactive") == "active" }
        boolean entrancePirActiveNow = roomPirSensors.any { (it.currentValue("motion") ?: "inactive") == "active" && (getEffectivePirPlacementRole(it) in ["entrance", "both"]) }
        boolean interiorPirActiveNow = roomPirSensors.any { (it.currentValue("motion") ?: "inactive") == "active" && (getEffectivePirPlacementRole(it) in ["interior", "both"]) }
        long lastPirActiveTs = safeLong(lastPirMap[zoneName], 0L)
        boolean pirRecentlyActive = pirActiveNow || (lastPirActiveTs > 0L && (nowTs - lastPirActiveTs) <= 90000L)

        List<String> pirRoleLabels = roomPirSensors.collect { pirDev ->
            String r = getEffectivePirPlacementRole(pirDev)
            return r == "entrance" ? "🚪Door" : (r == "interior" ? "🛋️Back" : "🔄Both")
        }.unique()
        String pirPlacementSummary = pirRoleLabels ? pirRoleLabels.join("+") : null

        // Evaluate Vibration & Multipurpose Posture Teachers for this room
        Map vibStatus = evaluateRoomVibrationTeacherStatus(zoneName, nowTs)
        int vibSensorCount = safeInt(vibStatus.sensorCount, 0)
        boolean vibActiveNow = (vibStatus.activeNow == true)
        boolean vibRecentlyActive = (vibStatus.recentlyActive == true)
        String vibPostureType = vibStatus.postureType?.toString()
        String vibMeaningLabel = vibStatus.meaningLabel?.toString()
        String vibActiveSensorName = vibStatus.activeSensorName?.toString()
        String vibSummaryText = vibStatus.summaryText?.toString()

        Map prevZ = (prevZoneStatus[zoneName] ?: [:]) as Map
        String prevOccState = prevZ.occupancyState ?: "empty"
        int prevPeopleCount = safeInt(prevZ.peopleCount, 0)
        long lastActiveMotion = safeLong(lastMotionMap[zoneName], 0L)
        boolean wasOccupied = sleepSwitchActiveNow || vibRecentlyActive || ((prevOccState in ["active_motion", "stationary_occupied", "sleeping_occupied", "active_motion_cooldown"]) &&
                              (lastActiveMotion > 0L && (nowTs - lastActiveMotion) < zoneHoldMs))

        Long existingStillStartTs = stillStartMap[zoneName] as Long
        long currentStillElapsedMs = (existingStillStartTs != null) ? Math.max(0L, nowTs - existingStillStartTs) : 0L

        updateContinuousPirAndSelfRfTraining(zoneName, devs, roomPirSensors, vibStatus, allNodeScores, wasOccupied, currentStillElapsedMs, sleepSwitchActiveNow, nowTs)

        Map learnedInfo = getRoomEffectiveLearnedThresholds(zoneName)
        int entryMotThresh = safeInt(learnedInfo.entryThresh, fetchEntryMotionThreshold())
        int keepAliveThresh = safeInt(learnedInfo.keepThresh, fetchKeepAliveThreshold())
        double standShadowReq = safeDouble(learnedInfo.standShadowDb, fetchMinShadowForStationary().doubleValue())
        double sitShadowReq = safeDouble(learnedInfo.sitShadowDb, 0.27d)
        double layingShadowReq = safeDouble(learnedInfo.layShadowDb, fetchLayingShadowThreshold().doubleValue())
        double sitVarianceMax = safeDouble(learnedInfo.sitVariance, 0.0032d)
        double maxSleepVarReq = Math.max(0.0020d, safeDouble(learnedInfo.layVariance, fetchMaxSleepVariance().doubleValue()))

        boolean anyNonFanSwitchRecentlyToggledOn = devs.any { dev ->
            if (isCeilingFanSwitch(dev)) return false
            Long tTs = toggleMap.get(dev.id.toString()) as Long
            boolean isOnNow = ((dev.currentValue("switch") ?: "off").toString().toLowerCase() == "on")
            return (isOnNow && tTs != null && (nowTs - tTs) <= 12000L)
        }
        boolean allNonFanLightsOffInRoom = !anyNonFanLightSwitchOnInRoom

        String parentRoomName = inferParentRoomForSubZone(zoneName, allZoneNamesSet)
        boolean parentRoomRecentlyActive = true
        if (parentRoomName != null && adjacentWallShieldOn) {
            long parentLastMot = safeLong(lastMotionMap[parentRoomName], 0L)
            long parentLastExit = safeLong(lastExitMap[parentRoomName], 0L)
            Map parentPrevZ = (prevZoneStatus[parentRoomName] ?: [:]) as Map
            boolean parentOccupiedNow = (parentPrevZ.occupancyState in ["active_motion", "stationary_occupied", "sleeping_occupied", "active_motion_cooldown"])
            parentRoomRecentlyActive = parentOccupiedNow || ((nowTs - Math.max(parentLastMot, parentLastExit)) <= 240000L)
        }

        int maxDoorwaySwitchScore = 0
        int maxDoorwayCandidateScore = 0
        int maxZoneScoreVelocity = 0
        int sensorsSeeingAnyRipple = 0
        int wallSwitchesSeeingCandidateHit = 0
        boolean hasConfiguredInteriorSensor = (vibSensorCount > 0)
        boolean interiorSensorSeeingPresenceNow = interiorPirActiveNow || vibRecentlyActive
        boolean entrancePortalActiveNow = entrancePirActiveNow
        boolean exitPortalActiveNow = false
        String activePortalSwitchName = entrancePirActiveNow ? "🚪 Entrance PIR" : null
        String activeInteriorSensorName = vibRecentlyActive ? "📳 ${vibActiveSensorName ?: 'Vibration'}" : (interiorPirActiveNow ? "🛋️ Interior PIR" : null)

        devs.each { d ->
            String dId = d.id.toString()
            String rRole = getEffectiveDeviceTriggerRole(d, devs)
            String pRole = getEffectiveDevicePortalRole(d, devs)
            boolean isLearnedPortal = (learnedInfo.learnedPortalSwitchId != null && learnedInfo.learnedPortalSwitchId.toString() == dId)
            boolean isLearnedInterior = (learnedInfo.learnedInteriorNodeId != null && learnedInfo.learnedInteriorNodeId.toString() == dId)
            boolean isSwOn = ((d.currentValue("switch") ?: "off").toString().toLowerCase() == "on")
            boolean isFanDev = isCeilingFanSwitch(d)

            if (rRole != "support_only") {
                Map dNs = allNodeScores[dId] as Map
                int dSc = safeInt(dNs?.score, 0)
                int dCand = safeInt(dNs?.candidateScore, 0)
                int dStreak = safeInt(dNs?.hitStreak, 0)
                int dVel = safeInt(dNs?.scoreVelocity, 0)
                double dNj = safeDouble(dNs?.netJitterMs, 0.0d)

                int minStreakReqForCandidate = isFanDev ? 2 : 1
                if (rRole != "support_assist" && (!isInteriorHoldRole(pRole) || isSwOn) && dStreak >= minStreakReqForCandidate && dCand >= keepAliveThresh && dNj >= 3.0d) {
                    wallSwitchesSeeingCandidateHit++
                }

                if (isInteriorHoldRole(pRole) || rRole == "support_assist" || isLearnedInterior) {
                    hasConfiguredInteriorSensor = true
                    if (isSwOn && !isFanDev && dSc >= keepAliveThresh && dNj >= 2.0d) {
                        interiorSensorSeeingPresenceNow = true
                        activeInteriorSensorName = d.displayName
                    }
                }

                if ((isEntrancePortalRole(pRole) && !isInteriorHoldRole(pRole) && rRole != "support_assist") || isLearnedPortal) {
                    if (dSc > maxDoorwaySwitchScore) maxDoorwaySwitchScore = dSc
                    if (dStreak >= minStreakReqForCandidate && dCand > maxDoorwayCandidateScore) maxDoorwayCandidateScore = dCand
                    if (dSc >= keepAliveThresh && dNj >= 2.5d) {
                        entrancePortalActiveNow = true
                        activePortalSwitchName = d.displayName
                    }
                }
                if (isExitPortalRole(pRole) && !isInteriorHoldRole(pRole) && rRole != "support_assist") {
                    if (dSc >= keepAliveThresh && dNj >= 2.5d) {
                        exitPortalActiveNow = true
                        activePortalSwitchName = d.displayName
                    }
                }

                if (dSc >= keepAliveThresh && dNj >= 2.0d && (!isInteriorHoldRole(pRole) || isSwOn)) {
                    sensorsSeeingAnyRipple++
                }
                if (dVel > maxZoneScoreVelocity) maxZoneScoreVelocity = dVel
            }
        }
        if (pirActiveNow || vibActiveNow) {
            sensorsSeeingAnyRipple += 2
            maxZoneScoreVelocity = Math.max(maxZoneScoreVelocity, 25)
        }

        boolean multiSwitchCorroboratedNow = (wallSwitchesSeeingCandidateHit >= 2) && (maxDoorwayCandidateScore >= entryMotThresh) && (!isBathroomZone || anyNonFanDeviceOnInRoom)

        Map zPortal = (portalStateMap[zoneName] instanceof Map) ? new LinkedHashMap(portalStateMap[zoneName] as Map) : [
            lastEntrancePulseMs: 0L,
            lastExitPulseMs: 0L,
            lastInteriorHoldMs: 0L,
            wasInteriorHolding: false,
            secondPersonEnteredViaPortal: false
        ]
        if (entrancePortalActiveNow) {
            zPortal.lastEntrancePulseMs = nowTs
        }
        if (exitPortalActiveNow) {
            zPortal.lastExitPulseMs = nowTs
        }
        if (interiorSensorSeeingPresenceNow) {
            zPortal.lastInteriorHoldMs = nowTs
            zPortal.wasInteriorHolding = true
        } else if (!wasOccupied) {
            zPortal.wasInteriorHolding = false
            zPortal.secondPersonEnteredViaPortal = false
        }

        String portalVectorStatus = null
        if (hasConfiguredInteriorSensor || interiorPirActiveNow || vibRecentlyActive) {
            if (interiorSensorSeeingPresenceNow && entrancePortalActiveNow) {
                portalVectorStatus = "🚪 Doorway (${activePortalSwitchName ?: 'Switch'}) + 🛋️ Back/Interior (${activeInteriorSensorName ?: 'Interior'})"
            } else if (interiorSensorSeeingPresenceNow) {
                portalVectorStatus = "🛋️ Back-of-Room / Interior Hold (${activeInteriorSensorName ?: 'Interior'})"
            } else if (entrancePortalActiveNow) {
                portalVectorStatus = "🚪 Doorway Active (${activePortalSwitchName ?: 'Switch'})"
            }
        } else if (entrancePortalActiveNow) {
            portalVectorStatus = "🚪 Detected Near ${activePortalSwitchName ?: 'Switch'}"
        }
        portalStateMap[zoneName] = zPortal

        List<Integer> primarySwitchScores = []
        List<Integer> coSensorScores = []
        List<Double> variances = []
        List<Double> stepDeltas = []
        List<Double> entryEligibleShadows = []
        List<Double> netJitters = []

        double closestNodeDist = 25.0d
        String closestNodeName = primaryWallSwitchesInZone ? primaryWallSwitchesInZone[0].displayName : (devs ? devs[0].displayName : "Switch")
        int nodesWithStrongPersonShadow = 0
        int nodesWithActiveMotionAndShadow = 0
        int activeSensorCount = sensorsSeeingAnyRipple
        int maxRoomHitStreak = 0
        boolean nonSupportSwitchConfirmed = false
        boolean suppressedByWallShield = false
        boolean suppressedByAntiTeleport = false
        boolean entryVerifiedOrImmediate = false

        String winningActivityLabel = (vibRecentlyActive && vibMeaningLabel) ? vibMeaningLabel : "None"
        String winningActivityNode = (vibRecentlyActive && vibActiveSensorName) ? vibActiveSensorName : null
        double winningActivityDist = 99.0d

        devs.each { dev ->
            Map ns = allNodeScores[dev.id.toString()] as Map
            if (ns) {
                String role = getEffectiveDeviceTriggerRole(dev, devs)
                String pRole = getEffectiveDevicePortalRole(dev, devs)
                boolean isBackOfRoomClosetOrTubNode = isInteriorHoldRole(pRole) && !isSmartPlugOrApplianceNode(dev)
                boolean isThisDeviceOn = ((dev.currentValue("switch") ?: "off").toString().toLowerCase() == "on")
                boolean isFanDev = isCeilingFanSwitch(dev)

                if (role == "support_assist") {
                    int plugSc = (isThisDeviceOn && (wasOccupied || maxDoorwaySwitchScore >= entryMotThresh || interiorPirActiveNow || vibRecentlyActive)) ? safeInt(ns.score, 0) : 0
                    double plugSh = (plugSc > 0) ? safeDouble(ns.shadowShift, 0.0d) : 0.0d
                    double plugVr = (plugSc > 0) ? safeDouble(ns.variance, 0.0d) : 0.0d
                    double plugNj = (plugSc > 0) ? safeDouble(ns.netJitterMs, 0.0d) : 0.0d
                    double plugDist = safeDouble(ns.distanceFt, 25.0d)
                    int plugStreak = (plugSc > 0) ? safeInt(ns.hitStreak, 0) : 0
                    if (plugStreak > maxRoomHitStreak) maxRoomHitStreak = plugStreak

                    coSensorScores << plugSc
                    entryEligibleShadows << plugSh
                    variances << plugVr
                    netJitters << plugNj

                    if (plugDist < closestNodeDist && plugSc >= keepAliveThresh) {
                        closestNodeDist = plugDist
                        closestNodeName = dev.displayName
                    }
                    if (plugDist < globalClosestDist && plugSc >= keepAliveThresh) {
                        globalClosestDist = plugDist
                        globalClosestSwitch = "${dev.displayName} (${zoneName})"
                    }
                } else if (role != "support_only") {
                    int rawSc = safeInt(ns.score, 0)
                    int candSc = safeInt(ns.candidateScore, 0)
                    int streak = safeInt(ns.hitStreak, 0)
                    int minCorroborationStreak = isFanDev ? 2 : 1
                    int sc = ((multiSwitchCorroboratedNow || pirActiveNow || vibRecentlyActive) && streak >= minCorroborationStreak) ? Math.max(rawSc, candSc) : rawSc

                    double vr = safeDouble(ns.variance, 0.0d)
                    double st = safeDouble(ns.stepDelta, 0.0d)
                    double sh = Math.min(2.40d, safeDouble(ns.shadowShift, 0.0d))
                    double netJ = safeDouble(ns.netJitterMs, 0.0d)
                    double dFt = safeDouble(ns.distanceFt, 25.0d)
                    if (streak > maxRoomHitStreak) maxRoomHitStreak = streak
                    boolean blanked = (ns.switchBlankerActive == true)

                    boolean passesPortalAndRoomGuard = true
                    if (!pirActiveNow && !vibRecentlyActive) {
                        if (isBackOfRoomClosetOrTubNode && !isThisDeviceOn) {
                            passesPortalAndRoomGuard = false
                            sc = 0
                            sh = 0.0d
                            netJ = 0.0d
                        } else if (isBathroomZone && !anyNonFanDeviceOnInRoom && backOfRoomAndDarkBathGuardOn) {
                            passesPortalAndRoomGuard = false
                            if (sc > 0 || candSc > 0) {
                                suppressedByWallShield = true
                            }
                            sc = 0
                            sh = 0.0d
                            netJ = 0.0d
                        } else if (!wasOccupied && isClosetRoomName(zoneName) && !parentRoomRecentlyActive && adjacentWallShieldOn) {
                            passesPortalAndRoomGuard = (isThisDeviceOn && !isFanDev) || (streak >= 2 && netJ >= 8.0d)
                            if (!passesPortalAndRoomGuard) {
                                if (sc > 0 || candSc > 0) suppressedByWallShield = true
                                sc = 0
                                sh = 0.0d
                                netJ = 0.0d
                            }
                        }
                    }

                    primarySwitchScores << sc
                    entryEligibleShadows << sh
                    variances << vr
                    stepDeltas << st
                    netJitters << netJ

                    boolean pirTruthAllowsSwitchEntry = (pirSensorCount == 0) || pirRecentlyActive || vibRecentlyActive || (streak >= 2 && sc >= (entryMotThresh + 8))
                    boolean nodeHasConfirmedTrigger = !blanked && passesPortalAndRoomGuard && pirTruthAllowsSwitchEntry && (sc >= entryMotThresh) && (netJ >= 3.5d) && (streak >= 2 || multiSwitchCorroboratedNow)
                    if (nodeHasConfirmedTrigger) {
                        nonSupportSwitchConfirmed = true
                    }

                    if (sc >= keepAliveThresh && netJ >= 2.5d && passesPortalAndRoomGuard) {
                        nodesWithStrongPersonShadow++
                        if (sc >= entryMotThresh) {
                            nodesWithActiveMotionAndShadow++
                        }
                    }

                    if (dFt < closestNodeDist && sc >= keepAliveThresh && passesPortalAndRoomGuard) {
                        closestNodeDist = dFt
                        closestNodeName = dev.displayName
                    }
                    if (dFt < globalClosestDist && sc >= keepAliveThresh && passesPortalAndRoomGuard) {
                        globalClosestDist = dFt
                        globalClosestSwitch = "${dev.displayName} (${zoneName})"
                    }
                }

                if (ns.activityTriggered && ns.triggeredActivityLabel && safeDouble(ns.distanceFt, 25.0d) < winningActivityDist) {
                    winningActivityDist = safeDouble(ns.distanceFt, 25.0d)
                    winningActivityLabel = ns.triggeredActivityLabel.toString()
                    winningActivityNode = dev.displayName
                }
            }
        }

        if (!primarySwitchScores) primarySwitchScores = [0]
        if (!coSensorScores) coSensorScores = [0]
        if (!entryEligibleShadows) entryEligibleShadows = [0.0d]
        if (!variances) variances = [0.0d]
        if (!stepDeltas) stepDeltas = [0.0d]
        if (!netJitters) netJitters = [0.0d]

        List<Integer> sortedPrimary = primarySwitchScores.sort(false) { -it }
        int peakPrimary = safeInt(sortedPrimary[0], 0)
        int secondPrimary = (sortedPrimary.size() > 1) ? safeInt(sortedPrimary[1], 0) : 0
        int bestCoSensor = safeInt(coSensorScores.max(), 0)

        int dominantSensorScore = Math.max(peakPrimary, bestCoSensor)
        int secondarySensorScore = (peakPrimary >= bestCoSensor) ? Math.max(secondPrimary, bestCoSensor) : peakPrimary
        double baseComposite = (dominantSensorScore * 1.00d) + (secondarySensorScore * 0.15d)

        boolean multiSensorConsensus = (
            multiSwitchCorroboratedNow ||
            (totalTrackingSensorCount >= 2 && sensorsSeeingAnyRipple >= 2 && (!allNonFanLightsOffInRoom || interiorSensorSeeingPresenceNow)) ||
            ((pirActiveNow || vibRecentlyActive) && sensorsSeeingAnyRipple >= 1)
        )
        if (multiSensorConsensus && peopleHomeNow > 0 && !suppressedByWallShield) {
            baseComposite = baseComposite * 1.12d
        }

        boolean lightAssistApplied = false
        if (lightSwitchAssistOn && anyNonFanLightSwitchOnInRoom && baseComposite >= (keepAliveThresh - 2) && !suppressedByWallShield && peopleHomeNow > 0) {
            baseComposite += 3.0d
            lightAssistApplied = true
        }

        if ((pirActiveNow || vibActiveNow || sleepSwitchActiveNow) && peopleHomeNow > 0) {
            baseComposite = Math.max(baseComposite, (entryMotThresh + 6) as double)
            nonSupportSwitchConfirmed = true
        } else if (vibRecentlyActive && peopleHomeNow > 0) {
            baseComposite = Math.max(baseComposite, (keepAliveThresh + 4) as double)
            nonSupportSwitchConfirmed = true
        }

        int compositeScore = Math.min(100, Math.max(0, Math.round(baseComposite).toInteger()))

        double maxVar = safeDouble(variances.max(), 0.0d)
        double maxStep = safeDouble(stepDeltas.max(), 0.0d)
        double maxShadow = safeDouble(entryEligibleShadows.max(), 0.0d)
        double maxNetJitter = safeDouble(netJitters.max(), 0.0d)
        if ((pirActiveNow || vibRecentlyActive || sleepSwitchActiveNow) && maxShadow < sitShadowReq) {
            maxShadow = (vibPostureType == "laying" || sleepSwitchActiveNow) ? layingShadowReq : ((interiorPirActiveNow || vibPostureType == "sitting") ? sitShadowReq : standShadowReq)
        }

        List<Double> sortedShadows = entryEligibleShadows.sort(false) { -it }
        double weightedShadowSum = 0.0d
        sortedShadows.eachWithIndex { double shVal, int idx ->
            double spatialWeight = (idx == 0) ? 1.00d : ((idx == 1) ? 0.50d : 0.30d)
            weightedShadowSum += (shVal * spatialWeight)
        }
        double cumulativeShadow = Math.round(Math.min(5.50d, weightedShadowSum) * 100.0d) / 100.0d

        List zShadHist = ((shadowHistMap[zoneName] ?: []).toList())
        double prevPeakCumulativeShadow = zShadHist ? safeDouble(zShadHist.max(), cumulativeShadow) : cumulativeShadow
        zShadHist << cumulativeShadow
        while (zShadHist.size() > 5) zShadHist.remove(0)
        shadowHistMap[zoneName] = zShadHist

        // ======================================================================
        // UPGRADE 4: ANTI-TELEPORTATION CONTIGUOUS PATH GUARD
        // ======================================================================
        boolean blockedByAntiTeleport = false
        if (!wasOccupied && !sleepSwitchActiveNow && !pirActiveNow && !vibActiveNow && antiTeleportGuardOn && previouslyConfirmedZones && !previouslyConfirmedZones.contains(zoneName)) {
            boolean isAdjacentToAnyOccupiedOrRecentRoom = previouslyConfirmedZones.any { String occZ ->
                areZonesInCloseProximity(zoneName, occZ, allZoneNamesSet)
            }
            if (!isAdjacentToAnyOccupiedOrRecentRoom) {
                boolean recentlyWalkedThroughNeighbor = getCloseProximityRoomsForZone(zoneName, allZoneNamesSet).any { String neighZ ->
                    long nMot = safeLong(lastMotionMap[neighZ], 0L)
                    long nEx = safeLong(lastExitMap[neighZ], 0L)
                    return ((nowTs - Math.max(nMot, nEx)) <= 75000L)
                }
                if (!recentlyWalkedThroughNeighbor) {
                    boolean allPeopleSlotsAlreadyAnchored = (previouslyConfirmedZones.size() >= peopleHomeNow)
                    boolean strongPhysicalOverride = (anyNonFanSwitchRecentlyToggledOn && maxRoomHitStreak >= 1) || (multiSwitchCorroboratedNow && maxRoomHitStreak >= 2) || (maxRoomHitStreak >= 3 && compositeScore >= (entryMotThresh + 12))
                    if (allPeopleSlotsAlreadyAnchored && !strongPhysicalOverride) {
                        blockedByAntiTeleport = true
                        suppressedByAntiTeleport = true
                        nonSupportSwitchConfirmed = false
                        compositeScore = 0
                    }
                }
            }
        }

        if (sleepSwitchActiveNow || vibRecentlyActive) {
            entryVerifiedOrImmediate = true
            lastMotionMap[zoneName] = nowTs
            entryCandStartMap.remove(zoneName)
        } else if (wasOccupied && peopleHomeNow > 0 && !suppressedByWallShield) {
            boolean hasActiveMotionNow = pirActiveNow || (compositeScore >= entryMotThresh && maxNetJitter >= 5.0d && maxRoomHitStreak >= 2 && (pirSensorCount == 0 || pirRecentlyActive))
            Long stillStartTs = stillStartMap[zoneName] as Long
            boolean withinMaxStillHold = (stillStartTs == null) || ((nowTs - stillStartTs) < maxStationaryHoldMs) || (prevOccState == "sleeping_occupied") || pirActiveNow || vibRecentlyActive

            boolean hasLiveKeepAliveSignal = pirActiveNow || vibRecentlyActive || interiorSensorSeeingPresenceNow || (compositeScore >= keepAliveThresh && maxNetJitter >= 2.5d && maxRoomHitStreak >= 2 && (pirSensorCount == 0 || pirRecentlyActive))
            boolean hasMicroVarianceStillHold = hasLiveKeepAliveSignal && withinMaxStillHold

            if (!withinMaxStillHold && prevOccState != "sleeping_occupied") {
                stillStartMap.remove(zoneName)
                logAction("🛡️ Max Still Hold reached without movement in ${zoneName}. Marking room Empty.")
            }

            entryVerifiedOrImmediate = hasActiveMotionNow || hasMicroVarianceStillHold
            entryCandStartMap.remove(zoneName)
        } else if (!suppressedByWallShield && !blockedByAntiTeleport && (nonSupportSwitchConfirmed || pirActiveNow || vibActiveNow) && peopleHomeNow > 0) {
            entryVerifiedOrImmediate = true
            atomicState.lastPhysicalArrivalGraceMs = nowTs
            entryCandStartMap.remove(zoneName)
        } else {
            entryCandStartMap.remove(zoneName)
        }

        int estPeople = 0
        int estPets = 0
        boolean partialExitEvent = false
        boolean partialExitAlreadyLogged = (prevZ.partialExitLogged == true)

        if (entryVerifiedOrImmediate && (peopleHomeNow > 0 || sleepSwitchActiveNow)) {
            boolean twoPeopleProven = allowTwoPeopleInOneRoom && (peopleHomeNow >= 2) && (
                !allNonFanLightsOffInRoom && totalTrackingSensorCount >= 2 && nodesWithActiveMotionAndShadow >= 2 &&
                interiorSensorSeeingPresenceNow && entrancePortalActiveNow && cumulativeShadow >= (standShadowReq + extraPersonStepReq)
            )
            estPeople = twoPeopleProven ? 2 : 1

            double shadowDropFromRecentPeak = prevPeakCumulativeShadow - cumulativeShadow
            if (!partialExitAlreadyLogged && prevPeopleCount >= 2 && estPeople < prevPeopleCount && shadowDropFromRecentPeak >= partialExitDropReq) {
                partialExitEvent = true
                partialExitAlreadyLogged = true
                zPortal.secondPersonEnteredViaPortal = false
                portalStateMap[zoneName] = zPortal
            }
        } else {
            partialExitAlreadyLogged = false
        }

        String postureLabel = "Unoccupied"
        String motionState = "inactive"
        String presenceState = "not present"
        String occupancyState = "empty"
        int cooldownRem = 0
        int sleepingCount = 0
        boolean isSleepingNow = false
        int sleepDurationMin = 0
        int sleepVerifyRemSec = 0
        String proxBandStr = classifyProximityBand(closestNodeDist)

        if (estPeople > 0) {
            boolean isActivelyWalking = !sleepSwitchActiveNow && !vibRecentlyActive && (entrancePirActiveNow || ((pirSensorCount == 0 || pirActiveNow) && compositeScore >= entryMotThresh && maxNetJitter >= 6.0d))
            boolean isStandingPosture = (!isActivelyWalking && (vibPostureType == "standing" || (maxShadow >= standShadowReq && (maxNetJitter >= 3.5d || maxVar > sitVarianceMax))))
            boolean isSittingPosture = (!isActivelyWalking && !isStandingPosture && (vibPostureType == "sitting" || interiorPirActiveNow || (maxShadow >= sitShadowReq && maxVar <= sitVarianceMax)))
            boolean isLayingCandidate = sleepSwitchActiveNow || (vibRecentlyActive && vibPostureType == "laying") ||
                                        (!isActivelyWalking && !isStandingPosture && maxShadow >= layingShadowReq && maxShadow < standShadowReq && maxVar <= maxSleepVarReq) ||
                                        (winningActivityLabel.contains("Sleeping") || winningActivityLabel.contains("Bed"))

            motionState = "active"
            presenceState = "present"

            if (isActivelyWalking) {
                stillStartMap.remove(zoneName)
                lastMotionMap[zoneName] = nowTs
            } else {
                if (!stillStartMap.containsKey(zoneName)) {
                    stillStartMap[zoneName] = nowTs
                }
                if (pirActiveNow || vibActiveNow) {
                    lastMotionMap[zoneName] = nowTs
                }
            }

            if (!sleepAllowed || !isLayingCandidate || isActivelyWalking) {
                sleepCandStartMap.remove(zoneName)
            }

            boolean sleepVerified = false
            long elapsedStillForPostureMs = (stillStartMap[zoneName] != null) ? (nowTs - safeLong(stillStartMap[zoneName], nowTs)) : 0L

            if (sleepSwitchActiveNow) {
                sleepVerified = true
                Long candStartTs = sleepCandStartMap[zoneName] as Long
                if (candStartTs == null) {
                    candStartTs = nowTs
                    sleepCandStartMap[zoneName] = candStartTs
                }
                sleepDurationMin = Math.max(1, Math.round((nowTs - candStartTs) / 60000.0d).toInteger())
                lastMotionMap[zoneName] = nowTs
            } else if (sleepAllowed && isLayingCandidate) {
                Long candStartTs = sleepCandStartMap[zoneName] as Long
                if (candStartTs == null) {
                    candStartTs = nowTs
                    sleepCandStartMap[zoneName] = candStartTs
                }
                long elapsedStillMs = nowTs - candStartTs
                if (elapsedStillMs >= sleepVerifyReqMs || winningActivityLabel.contains("Sleeping")) {
                    sleepVerified = true
                    sleepDurationMin = Math.max(1, Math.round(elapsedStillMs / 60000.0d).toInteger())
                    lastMotionMap[zoneName] = nowTs
                } else {
                    sleepVerifyRemSec = Math.max(1, Math.round((sleepVerifyReqMs - elapsedStillMs) / 1000.0d).toInteger())
                }
            }

            if (sleepVerified) {
                occupancyState = "sleeping_occupied"
                isSleepingNow = true
                sleepingCount = estPeople
                postureLabel = sleepSwitchActiveNow ? "🛌 Laying / Asleep (Switch ON)" : (vibRecentlyActive && vibMeaningLabel ? "${vibMeaningLabel} (${sleepDurationMin}m)" : "🛌 Laying Down / Sleeping (${sleepDurationMin}m)")
                if (closestNodeDist >= 20.0d) closestNodeDist = 6.0d
            } else if (vibRecentlyActive && vibMeaningLabel) {
                occupancyState = "stationary_occupied"
                postureLabel = vibMeaningLabel
                if (closestNodeDist >= 20.0d) closestNodeDist = 5.5d
            } else if (isActivelyWalking) {
                occupancyState = "active_motion"
                postureLabel = (estPeople > 1) ? "🚶 Walking / Moving (${estPeople} People)" : "🚶 Walking / Moving"
            } else if (sleepAllowed && isLayingCandidate && elapsedStillForPostureMs >= 45000L) {
                occupancyState = "stationary_occupied"
                postureLabel = "🛌 Laying Down / Resting"
            } else if (isStandingPosture) {
                occupancyState = "stationary_occupied"
                postureLabel = isKitchenOrDiningRoomName(zoneName) ? "🍳 Standing at Counter" : (isBathroomOrShowerRoomName(zoneName) ? "🚿 Standing / Shower" : "🧍 Standing")
            } else if (isSittingPosture || elapsedStillForPostureMs >= 15000L) {
                occupancyState = "stationary_occupied"
                postureLabel = isBathroomOrShowerRoomName(zoneName) ? "🚿 In Bathroom / Seated" : "🪑 Sitting"
            } else {
                occupancyState = "stationary_occupied"
                postureLabel = "🧍 Standing / Sitting"
            }

            if (winningActivityLabel != "None") {
                houseActiveActivities << "${zoneName}: ${winningActivityLabel}"
            }
        } else if (!suppressedByWallShield && !blockedByAntiTeleport && peopleHomeNow > 0 && lastActiveMotion > 0L && (nowTs - lastActiveMotion) < zoneHoldMs) {
            sleepCandStartMap.remove(zoneName)
            stillStartMap.remove(zoneName)
            cooldownRem = Math.max(1, Math.round((zoneHoldMs - (nowTs - lastActiveMotion)) / 1000.0d).toInteger())
            motionState = "active"
            presenceState = "present"
            occupancyState = "active_motion_cooldown"
            postureLabel = "3-Min Hold (${cooldownRem}s)"
        } else {
            sleepCandStartMap.remove(zoneName)
            stillStartMap.remove(zoneName)
            if (suppressedByWallShield || blockedByAntiTeleport) {
                lastMotionMap.remove(zoneName)
            }
            if (prevOccState in ["active_motion", "stationary_occupied", "sleeping_occupied", "active_motion_cooldown"]) {
                lastExitMap[zoneName] = nowTs
                Map exitModel = getOrInitRoomTeacherModel(zoneName)
                exitModel.exitSamples = safeInt(exitModel.exitSamples, 0) + 1
                saveRoomTeacherModel(zoneName, exitModel)
                logAction("🆗 ${zoneName} 3-minute hold expired with no presence detected — room is now Empty.")
            }
            motionState = "inactive"
            presenceState = "not present"
            occupancyState = "empty"
            postureLabel = "Empty"
            compositeScore = 0
        }

        Map payload = [
            motion: motionState,
            presence: presenceState,
            occupancyState: occupancyState,
            posture: postureLabel,
            currentActivity: winningActivityLabel,
            activityNodeName: winningActivityNode,
            portalVectorStatus: portalVectorStatus,
            isSleeping: isSleepingNow,
            sleepSwitchActive: sleepSwitchActiveNow,
            lightAssistActive: lightAssistApplied,
            sleepingCount: sleepingCount,
            sleepDurationMin: sleepDurationMin,
            sleepVerifyRemainingSec: sleepVerifyRemSec,
            sleepAllowedInZone: sleepAllowed,
            immediateEntryAllowed: zoneImmediateDefault,
            entryVerifyTargetSec: zoneEntryVerifySec,
            entryVerifyRemainingSec: 0,
            autoRoomEntryThresh: entryMotThresh,
            autoRoomKeepThresh: keepAliveThresh,
            pirTeacherGraduated: learnedInfo.graduated,
            pirTeacherSamples: learnedInfo.totalSamples,
            learningDaysLoggedCount: learnedInfo.daysLoggedCount,
            daysUntilReEval: learnedInfo.daysUntilReEval,
            peopleCount: estPeople,
            petCount: estPets,
            maxRoomHitStreak: maxRoomHitStreak,
            scoreVelocity: maxZoneScoreVelocity,
            parentRoomRecentlyActive: parentRoomRecentlyActive,
            partialExitDetected: partialExitEvent,
            partialExitLogged: partialExitAlreadyLogged,
            suppressedByCrossTalk: false,
            suppressedByWallShield: suppressedByWallShield,
            suppressedByAntiTeleport: suppressedByAntiTeleport,
            darkRoomShieldActive: suppressedByWallShield,
            multiSensorConsensus: multiSensorConsensus,
            activeSensorCount: activeSensorCount,
            pirSensorCount: pirSensorCount,
            pirActiveNow: pirActiveNow,
            pirPlacementSummary: pirPlacementSummary,
            vibSensorCount: vibSensorCount,
            vibActiveNow: vibActiveNow,
            vibRecentlyActive: vibRecentlyActive,
            vibMeaningLabel: vibMeaningLabel,
            vibSummaryText: vibSummaryText,
            closestNodeName: closestNodeName,
            closestDistanceFt: closestNodeDist,
            proximityBand: proxBandStr,
            score: compositeScore,
            variance: maxVar,
            shadowShift: maxShadow,
            netJitterMs: maxNetJitter,
            cumulativeShadow: cumulativeShadow,
            nodeCount: devs.size(),
            activeWallSwitchCount: activeWallSwitchCount,
            coSensorPlugCount: coSensorPlugCount,
            quarantinedPlugCount: quarantinedPlugCount,
            baselineStatus: "calibrated",
            cooldownRemaining: cooldownRem
        ]

        allZoneStatus[zoneName] = payload
    }

    // ==========================================================================
    // PASS 3: STRICT HOUSEHOLD ROOM CAP & CONTIGUOUS HANDOFF CLEARING
    // ==========================================================================
    List<String> allZoneNamesList = allZoneStatus.keySet().collect { it.toString() }
    int maxSimultaneousRooms = safeInt(censusInfo.maxSimultaneousRooms, 1)

    if (crossTalkFilterOn) {
        List<String> occupiedZones = allZoneNamesList.findAll { String zName ->
            Map z = allZoneStatus[zName] as Map
            return (safeInt(z.peopleCount, 0) > 0) || (z.occupancyState == "active_motion_cooldown")
        }.sort(false) { String a, String b ->
            Map za = allZoneStatus[a] as Map
            Map zb = allZoneStatus[b] as Map
            // Priority 1: Room with Sleep Switch ON
            boolean slpA = (za.sleepSwitchActive == true)
            boolean slpB = (zb.sleepSwitchActive == true)
            if (slpA != slpB) return slpB <=> slpA

            // Priority 2: Room with an ACTIVE hardware Motion or Vibration Sensor right now
            boolean hwA = (za.pirActiveNow == true || za.vibActiveNow == true)
            boolean hwB = (zb.pirActiveNow == true || zb.vibActiveNow == true)
            if (hwA != hwB) return hwB <=> hwA

            // Priority 3: Live Occupied vs Cooldown
            boolean liveA = (safeInt(za.peopleCount, 0) > 0)
            boolean liveB = (safeInt(zb.peopleCount, 0) > 0)
            if (liveA != liveB) return liveB <=> liveA

            // Priority 4: Most recent motion timestamp + score
            long motTsA = safeLong(lastMotionMap[a], 0L)
            long motTsB = safeLong(lastMotionMap[b], 0L)
            if (Math.abs(motTsA - motTsB) > 4000L) return motTsB <=> motTsA

            double rankA = safeDouble(za.score, 0.0d) + (safeDouble(za.netJitterMs, 0.0d) * 1.5d) + (safeInt(za.maxRoomHitStreak, 0) * 15.0d)
            double rankB = safeDouble(zb.score, 0.0d) + (safeDouble(zb.netJitterMs, 0.0d) * 1.5d) + (safeInt(zb.maxRoomHitStreak, 0) * 15.0d)
            return rankB <=> rankA
        }

        if (occupiedZones.size() > maxSimultaneousRooms) {
            occupiedZones.drop(maxSimultaneousRooms).each { String zName ->
                Map z = allZoneStatus[zName] as Map
                if (z.sleepSwitchActive != true) {
                    z.peopleCount = 0
                    z.motion = "inactive"
                    z.presence = "not present"
                    z.occupancyState = "empty"
                    z.posture = "Empty"
                    z.score = 0
                    z.cooldownRemaining = 0
                    z.suppressedByCrossTalk = true
                    lastMotionMap.remove(zName)
                    stillStartMap.remove(zName)
                }
            }
        }
    }

    // ==========================================================================
    // PASS 4: UPDATE DAILY ROOM COUNTERS (WITH 90s ANTI-BOUNCE DEBOUNCER)
    // ==========================================================================
    allZoneStatus.each { String zoneName, Map payload ->
        Map prevZ = (prevZoneStatus[zoneName] ?: [:]) as Map
        String prevOccState = prevZ.occupancyState ?: "empty"
        String occupancyState = payload.occupancyState
        String postureLabel = payload.posture
        int compositeScore = safeInt(payload.score, 0)

        Map zDaily = (dailyStatsMap.get(zoneName) instanceof Map) ? new LinkedHashMap(dailyStatsMap.get(zoneName) as Map) : [
            entryCount: 0,
            occupiedMs: 0L,
            peakScore: 0,
            lastEntryTimeStr: "None today",
            lastCountedEpochMs: 0L
        ]
        boolean isNowHumanOccupied = (occupancyState in ["active_motion", "stationary_occupied", "sleeping_occupied"])
        boolean wasPreviouslyUnoccupied = !(prevOccState in ["active_motion", "stationary_occupied", "sleeping_occupied", "active_motion_cooldown"])
        long lastCountedTs = safeLong(zDaily.lastCountedEpochMs, 0L)

        if (isNowHumanOccupied && wasPreviouslyUnoccupied) {
            if ((nowTs - lastCountedTs) > 90000L || safeInt(zDaily.entryCount, 0) == 0) {
                zDaily.entryCount = safeInt(zDaily.entryCount, 0) + 1
                zDaily.lastEntryTimeStr = new Date(nowTs).format("hh:mm:ss a", location.timeZone)
                zDaily.lastCountedEpochMs = nowTs
                logAction("📊 Entered ${zoneName} at ${zDaily.lastEntryTimeStr} (Visit #${zDaily.entryCount} today — ${postureLabel}).")
            }
            recordRecentRoomEntry(zoneName, postureLabel, nowTs)
        } else if (isNowHumanOccupied || occupancyState == "active_motion_cooldown") {
            zDaily.lastCountedEpochMs = nowTs
            updateRecentRoomStatus(zoneName, postureLabel, true, nowTs)
        } else if (prevOccState in ["active_motion", "stationary_occupied", "sleeping_occupied", "active_motion_cooldown"]) {
            updateRecentRoomStatus(zoneName, "Empty", false, nowTs)
        }

        if (isNowHumanOccupied || occupancyState == "active_motion_cooldown") {
            zDaily.occupiedMs = safeLong(zDaily.occupiedMs, 0L) + sampleDeltaMs
        }
        if (compositeScore > safeInt(zDaily.peakScore, 0)) {
            zDaily.peakScore = compositeScore
        }
        dailyStatsMap.put(zoneName, zDaily)

        payload.todayEntryCount = safeInt(zDaily.entryCount, 0)
        payload.todayOccupiedMs = safeLong(zDaily.occupiedMs, 0L)
        payload.todayOccupiedDurationStr = formatDurationMs(safeLong(zDaily.occupiedMs, 0L))
        payload.todayPeakScore = safeInt(zDaily.peakScore, 0)
        payload.lastEntryTimeStr = zDaily.lastEntryTimeStr ?: "None today"

        houseTotalPeople += (safeInt(payload.peopleCount, 0) > 0 || occupancyState == "active_motion_cooldown") ? 1 : 0
        houseTotalPets += safeInt(payload.petCount, 0)
        houseTotalSleeping += safeInt(payload.sleepingCount, 0)

        String syncSig = "${payload.motion}|${payload.presence}|${payload.occupancyState}|${payload.posture}|${payload.currentActivity}|${payload.peopleCount}|${payload.petCount}|${payload.sleepingCount}|${payload.todayEntryCount}|${Math.round(safeDouble(payload.closestDistanceFt, 25.0d))}"
        Map prevSync = (childSyncSigMap[zoneName] ?: [:]) as Map
        long lastChildPushTs = safeLong(prevSync.ts, 0L)
        if (prevSync.sig != syncSig || (nowTs - lastChildPushTs) >= 60000L) {
            String childDni = getZoneDni(zoneName)
            def childDev = getChildDevice(childDni)
            childDev?.updateTomographyState(payload)
            childSyncSigMap[zoneName] = [sig: syncSig, ts: nowTs]
        }
    }

    int boundedHousePeople = Math.min(maxSimultaneousRooms, houseTotalPeople)
    int boundedHousePets = Math.min(housePetsCap, houseTotalPets)
    int boundedSleeping = Math.min(boundedHousePeople, houseTotalSleeping)

    atomicState.houseSummary = [
        detectedPeople: boundedHousePeople,
        peopleHomeNow: peopleHomeNow,
        adultSensorsPresent: safeInt(censusInfo.adultSensorsPresent, 0),
        assumedChildrenNow: safeInt(censusInfo.assumedChildrenNow, 0),
        childScheduleActive: (censusInfo.childScheduleActive == true),
        childSensorPresent: (censusInfo.childSensorPresent == true),
        childTriggerSource: censusInfo.childTriggerSource,
        maxSimultaneousRooms: maxSimultaneousRooms,
        censusReason: censusInfo.reason,
        detectedPets: boundedHousePets,
        sleepingPeople: boundedSleeping,
        commonModeShadowFloor: 0.0d,
        activeActivities: houseActiveActivities,
        rawZonePeopleSum: houseTotalPeople,
        rawZonePetsSum: houseTotalPets,
        closestSwitchName: globalClosestSwitch,
        closestSwitchDistFt: (globalClosestDist < 50.0d ? globalClosestDist : null)
    ]
    atomicState.lastChildSyncSignature = childSyncSigMap
    atomicState.zoneEntryCandidateStartMs = entryCandStartMap
    atomicState.zoneSleepCandidateStartMs = sleepCandStartMap
    atomicState.zoneStillStartMs = stillStartMap
    atomicState.zonePortalState = portalStateMap
    atomicState.zoneShadowHistory = shadowHistMap
    atomicState.lastActiveMotionTime = lastMotionMap
    atomicState.lastZoneExitEpochMs = lastExitMap
    atomicState.zoneStatus = allZoneStatus
}

// ==============================================================================
// CALIBRATION & TRIMMED-MEDIAN BASELINE SYNC HELPERS
// ==============================================================================

def scheduledBaselineCalibration() {
    long nowTs = now()
    Map currentZoneStatus = (atomicState.zoneStatus ?: [:]) as Map
    buildZoneMap().keySet().each { String zName ->
        Map m = getOrInitRoomTeacherModel(zName)
        m = evaluateRoomLifecycleAndSchedule(zName, m, nowTs)
        saveRoomTeacherModel(zName, m)
    }
    getAllAnchorDevices().each { dev ->
        if (!isDeviceInConfirmedOccupiedZone(dev, currentZoneStatus)) {
            calibrateNode(dev, nowTs)
        }
    }
    flushRamCacheToAtomicState(true)
}

def modeChangeHandler(evt) {
    logAction("🏠 Hub Mode changed to '${evt.value}'. Re-evaluating Household Census.")
    if (isHubCurrentlyInAwayMode()) {
        runIn(60, "calibrateAllNodesNow")
    }
    executeSynchronousPollAndEvaluate()
    flushRamCacheToAtomicState(true)
}

def calibrateAllNodesNow() {
    long nowTs = now()
    atomicState.lastActiveMotionTime = [:]
    atomicState.lastZoneExitEpochMs = [:]
    atomicState.zoneShadowHistory = [:]
    atomicState.zoneSleepCandidateStartMs = [:]
    atomicState.zoneEntryCandidateStartMs = [:]
    atomicState.zoneStillStartMs = [:]
    atomicState.zonePortalState = [:]
    atomicState.lastVibActiveEpochMs = [:]
    atomicState.lastVibDeviceEpochMs = [:]
    atomicState.lastDualKineticProofMs = 0L
    atomicState.fastFollowDeviceQueue = []
    atomicState.lastCalibrationTimeStr = new Date().format("MM/dd hh:mm a", location.timeZone)
    ConcurrentHashMap<String, Object> hitStreakMap = getFastMap("nodeHitStreaks")
    ConcurrentHashMap<String, Object> flatCountMap = getFastMap("nodeFlatCounts")
    ConcurrentHashMap<String, Object> lastRttRecvMap = getFastMap("lastRttReceivedMs")
    ConcurrentHashMap<String, Object> lastConsumedMap = getFastMap("lastConsumedRttMs")
    ConcurrentHashMap<String, Object> nodeScoresMap = getFastMap("nodeScores")
    ConcurrentHashMap<String, Object> autoTuneMap = getFastMap("nodeAutoTune")
    hitStreakMap.clear()
    flatCountMap.clear()
    lastRttRecvMap.clear()
    lastConsumedMap.clear()
    nodeScoresMap.clear()
    autoTuneMap.clear()
    atomicState.nodeAutoTune = [:]

    Map currentZones = (atomicState.zoneStatus ?: [:]) as Map
    currentZones.each { String zName, Map z ->
        if (z instanceof Map && !isZoneSleepSwitchOn(zName)) {
            z.occupancyState = "empty"
            z.motion = "inactive"
            z.presence = "not present"
            z.peopleCount = 0
            z.petCount = 0
            z.sleepingCount = 0
            z.score = 0
            z.cooldownRemaining = 0
            z.partialExitDetected = false
            z.partialExitLogged = false
            updateRecentRoomStatus(zName, "Empty", false, nowTs)
        }
    }
    atomicState.zoneStatus = currentZones

    getAllAnchorDevices().each { dev ->
        if (dev.hasCommand("resetRfBaseline")) {
            try { dev.resetRfBaseline() } catch (Exception ignored) {}
        }
        calibrateNode(dev, nowTs)
    }
    flushRamCacheToAtomicState(true)
}

def calibrateNode(dev, long nowTs) {
    String devId = dev.id.toString()
    boolean isPlug = isSmartPlugOrApplianceNode(dev)
    String swState = (dev.currentValue("switch") ?: "off").toString().toLowerCase()
    boolean isSwitchOn = (swState == "on")

    ConcurrentHashMap<String, Object> rttMap = getFastMap("measuredRtt")
    double curLiveRtt = dev.currentValue("rttMs") != null ?
        safeDouble(dev.currentValue("rttMs"), isPlug ? 28.0d : 165.0d) :
        safeDouble(rttMap.get(devId), isPlug ? 28.0d : 165.0d)

    double minFloor = isPlug ? 12.0d : 75.0d
    double maxEmptyCeiling = isPlug ? 50.0d : 340.0d
    double trueMedRtt = Math.max(minFloor, Math.min(maxEmptyCeiling, curLiveRtt))
    double trueCeilRtt = trueMedRtt + (isPlug ? 6.0d : 32.0d)
    double initMad = isPlug ? 3.5d : 14.0d

    ConcurrentHashMap<String, Object> allBaselines = getFastMap("baselines")
    Map prevBase = (allBaselines.get(devId) ?: [:]) as Map

    double prevMedOn = Math.max(minFloor, Math.min(maxEmptyCeiling, safeDouble(prevBase.medOn, trueMedRtt)))
    double prevMedOff = Math.max(minFloor, Math.min(maxEmptyCeiling, safeDouble(prevBase.medOff, trueMedRtt)))
    double prevRttOn = Math.max(minFloor + 15.0d, Math.min(maxEmptyCeiling + 38.0d, safeDouble(prevBase.rttOn, trueCeilRtt)))
    double prevRttOff = Math.max(minFloor + 15.0d, Math.min(maxEmptyCeiling + 38.0d, safeDouble(prevBase.rttOff, trueCeilRtt)))
    double prevMadOn = Math.min(20.0d, safeDouble(prevBase.madOn, initMad))
    double prevMadOff = Math.min(20.0d, safeDouble(prevBase.madOff, initMad))

    double newMedOn = isSwitchOn ? trueMedRtt : prevMedOn
    double newMedOff = !isSwitchOn ? trueMedRtt : prevMedOff
    double newRttOn = isSwitchOn ? trueCeilRtt : Math.min(prevMedOn + (isPlug ? 8.0d : 34.0d), prevRttOn)
    double newRttOff = !isSwitchOn ? trueCeilRtt : Math.min(prevMedOff + (isPlug ? 8.0d : 34.0d), prevRttOff)

    ConcurrentHashMap<String, Object> allHistory = getFastMap("rfHistory")
    List cleanHist = [
        [rawSig: 50.0d, sig: 50.0d, rc: 1, rtt: Math.round(trueMedRtt), jitter: 0.0d, src: "Calibrated", t: nowTs]
    ]
    allHistory.put(devId, cleanHist)

    allBaselines.put(devId, [
        mean: 50.0d,
        meanOff: 50.0d,
        meanOn: 50.0d,
        baseRtt: trueCeilRtt,
        medianRtt: trueMedRtt,
        rttOff: newRttOff,
        rttOn: newRttOn,
        medOff: newMedOff,
        medOn: newMedOn,
        madOff: prevMadOff,
        madOn: prevMadOn,
        baseJitter: 0.0d,
        lastSwState: swState,
        restingVar: 0.002d,
        personalDeadbandDb: fetchGlobalDeadbandDb(),
        samples: 1,
        calibratedAt: nowTs
    ])
}

void logAction(String msg) {
    if (settings.logEnable) log.info "Advanced RF Tomography Manager 7.3.0: ${msg}"
    List h = ((atomicState.actionHistory ?: []).toList())
    String timestamp = new Date().format("MM/dd hh:mm:ss a", location.timeZone)
    h.add(0, "[${timestamp}] ${msg}")
    if (h.size() > 25) h = h[0..24]
    atomicState.actionHistory = h
}

Integer extractInt(def val, Integer defaultVal = 0) {
    if (val == null) return defaultVal
    try {
        return val.toString().replaceAll(/[^0-9-]/, "") as Integer
    } catch (Exception ignored) {
        return defaultVal
    }
}

Double extractDouble(def val, Double defaultVal = 0.0d) {
    if (val == null) return defaultVal
    try {
        def m = (val.toString() =~ /(-?\d+(\.\d+)?)/)
        return m.find() ? (m.group(1) as Double) : defaultVal
    } catch (Exception ignored) {
        return defaultVal
    }
}

def logsOff() {
    app.updateSetting("logEnable", [value: "false", type: "bool"])
    app.updateSetting("traceEnable", [value: "false", type: "bool"])
}
