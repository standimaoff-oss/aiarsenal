// Adapted from supplied VolPyro 1.5.6 (MIT), VolPyro Contributors. See THIRD_PARTY_NOTICES.md.
package ru.aiforge.arsenal.volumetric.client.sim;

import ru.aiforge.arsenal.volumetric.client.sim.VolumeInstance;
import ru.aiforge.arsenal.volumetric.config.VolPyroClientConfig;
import ru.aiforge.arsenal.volumetric.sim.ObstacleVoxelCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class VolumetricFxManager {
    private static final List<VolumeInstance> transientVolumes = new ArrayList<VolumeInstance>();
    private static final Map<Long, VolumeInstance> emitters = new HashMap<Long, VolumeInstance>();
    private static final List<VolumeInstance> plumeSegments = new ArrayList<VolumeInstance>();
    private static final List<VolumeInstance> nukeVolumes = new ArrayList<VolumeInstance>();
    public static final int NUKE_TYPE = 6;
    public static final int NUKE_SHOCK_TYPE = 7;
    public static final float SHOCK_SPEED = 120.0f;
    public static final float SHOCK_START = 0.35f;
    public static final float SHOCK_REACH = 6.0f;
    private static final int MAX_NUKES = 8;
    private static final ObstacleVoxelCache obstacleCache = new ObstacleVoxelCache();
    private static final Map<Integer, RocketTrack> rocketTracks = new HashMap<Integer, RocketTrack>();
    private static long clockTicks;
    private static long nextKey;
    private static double lastTickMs;
    public static final int KINETIC_TYPE = 8;
    public static final int THERMOBARIC_TYPE = 9;
    public static final int LAUNCH_TYPE = 10;
    private static final Set<Integer> launchCloudIds;
    private static final double TRAIL_CHORD = 9.0;
    private static final int TRAIL_CHORD_TICKS = 14;
    private static final float TRAIL_LIFETIME = 40.0f;

    private VolumetricFxManager() {
    }

    public static synchronized void addExplosion(Vec3 vec3, float f, long l) {
        float f2 = Math.max(1.0f, Math.min(30.0f, f));
        float f3 = 1.4f + f2 * 0.68f;
        transientVolumes.add(new VolumeInstance(2, vec3, f3 * 2.4f, 1.0f, 6200.0f, f3, 0.0f, Vec3.ZERO, 0.0f, 14.0f, (float)(l & 0xFFFFL) / 65535.0f, nextKey++));
        VolumetricFxManager.spawnDebris(vec3, new Random(l), 12, 4.0, 12.0, 3500.0f, 1.0f, 0.6f);
        VolumetricFxManager.trimVolumes();
    }

    private static void spawnDebris(Vec3 vec3, Random random, int n, double d, double d2, float f, float f2, float f3) {
        for (int i = 0; i < n; ++i) {
            Vec3 vec32 = new Vec3(random.nextDouble() * 2.0 - 1.0, random.nextDouble() * 2.0 - 1.0 + (double)f3, random.nextDouble() * 2.0 - 1.0);
            if (vec32.lengthSqr() < 1.0E-4) {
                vec32 = new Vec3(0.0, 1.0, 0.0);
            }
            vec32 = vec32.normalize();
            float f4 = (0.28f + random.nextFloat() * 0.42f) * f2;
            float f5 = (0.045f + random.nextFloat() * 0.055f) * f2;
            VolumeInstance volumeInstance = new VolumeInstance(5, vec3.add(vec32.scale(0.45 * (double)f2)), f4 + f5 * 3.0f, 0.8f, f, f4, f5, vec32, f4, 1.35f + 0.4f * f2, random.nextFloat(), nextKey++);
            volumeInstance.velocity = vec32.scale(d + random.nextDouble() * (d2 - d));
            transientVolumes.add(volumeInstance);
        }
    }

    public static synchronized void addKineticImpact(Vec3 vec3, float f, Vec3 vec32, long l) {
        float f2 = Math.max(1.5f, f * 4.0f);
        Vec3 vec33 = vec32.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : vec32.normalize();
        float f3 = 8.0f + f2 * 0.5f;
        transientVolumes.add(new VolumeInstance(8, vec3, f2 * 2.6f, 1.0f, 5200.0f, f2, 0.0f, vec33, 0.0f, f3, (float)(l & 0xFFFFL) / 65535.0f, nextKey++));
        Random random = new Random(l);
        for (int i = 0; i < 10 + (int)f2; ++i) {
            Vec3 vec34 = vec33.add(new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(0.9)).normalize();
            float f4 = (0.3f + random.nextFloat() * 0.5f) * (0.6f + f2 * 0.08f);
            float f5 = (0.05f + random.nextFloat() * 0.06f) * (0.6f + f2 * 0.08f);
            VolumeInstance volumeInstance = new VolumeInstance(5, vec3.add(vec34.scale(0.5)), f4 + f5 * 3.0f, 0.9f, 290.0f, f4, f5, vec34, f4, 1.8f + f2 * 0.1f, random.nextFloat(), nextKey++);
            volumeInstance.velocity = vec34.scale(6.0 + random.nextDouble() * 10.0 + (double)f2 * 0.4);
            transientVolumes.add(volumeInstance);
        }
        VolumetricFxManager.trimVolumes();
    }

    public static synchronized void addThermobaric(Vec3 vec3, float f, long l) {
        float f2 = Math.max(2.0f, f);
        float f3 = 14.0f + f2 * 0.6f;
        transientVolumes.add(new VolumeInstance(9, vec3.add(0.0, (double)f2 * 0.9, 0.0), f2 * 3.2f, 1.0f, 4300.0f, f2, 0.0f, Vec3.ZERO, 0.0f, f3, (float)(l & 0xFFFFL) / 65535.0f, nextKey++));
        VolumetricFxManager.spawnDebris(vec3, new Random(l), 6 + (int)(f2 * 0.3f), 3.0, 7.0, 1900.0f, 0.7f + f2 * 0.04f, 0.8f);
        VolumetricFxManager.trimVolumes();
    }

    public static synchronized void addHighExplosive(Vec3 vec3, float f, long l) {
        float f2 = Math.max(1.0f, f * 1.1f);
        float f3 = 10.0f + f2 * 0.45f;
        transientVolumes.add(new VolumeInstance(2, vec3, f2 * 3.0f, 1.0f, 7000.0f, f2, 1.0f, Vec3.ZERO, 0.0f, f3, (float)(l & 0xFFFFL) / 65535.0f, nextKey++));
        VolumetricFxManager.spawnDebris(vec3, new Random(l), 14 + (int)(f2 * 0.8f), 6.0, 16.0 + (double)f2 * 0.5, 3200.0f, 0.8f + f2 * 0.05f, 0.7f);
        VolumetricFxManager.trimVolumes();
    }

    public static synchronized void addShockRing(Vec3 vec3, float f, long l) {
        float f2 = 5.65f * f / 120.0f;
        nukeVolumes.add(new VolumeInstance(7, vec3, f * 0.35f + f * 0.6f, 0.8f, 290.0f, f, 0.0f, Vec3.ZERO, 0.0f, f2 + 1.5f, (float)(l & 0xFFFFL) / 65535.0f, nextKey++));
        while (nukeVolumes.size() > 8) {
            nukeVolumes.remove(0);
        }
    }

    public static synchronized void addNuke(Vec3 vec3, float f, float f2, long l, int n) {
        float f3 = Math.max(4.0f, f * 0.9f);
        Vec3 vec32 = vec3.add(0.0, (double)f3 * 1.5, 0.0);
        nukeVolumes.add(new VolumeInstance(6, vec32, f3 * 3.0f, 1.0f, 9000.0f, f3, n, Vec3.ZERO, 0.0f, Math.max(10.0f, f2), (float)(l & 0xFFFFL) / 65535.0f, nextKey++));
        float f4 = 5.65f * f / 120.0f;
        VolumeInstance volumeInstance = new VolumeInstance(7, vec3, f * 0.35f + f * 0.6f, 1.0f, 290.0f, f, n, Vec3.ZERO, 0.0f, f4 + 1.5f, (float)(l & 0xFFFFL) / 65535.0f, nextKey++);
        nukeVolumes.add(volumeInstance);
        while (nukeVolumes.size() > 8) {
            nukeVolumes.remove(0);
        }
    }

    public static synchronized void addUnderwaterNuke(Vec3 vec3, float f, float f2, long l) {
        float f3 = Math.max(4.0f, f * 0.9f);
        nukeVolumes.add(new VolumeInstance(6, vec3, f3 * 3.2f, 1.0f, 290.0f, f3, 4.0f, Vec3.ZERO, 0.0f, Math.max(2.0f, f2), (float)(l & 0xFFFFL) / 65535.0f, nextKey++));
        while (nukeVolumes.size() > 8) {
            nukeVolumes.remove(0);
        }
    }

    public static synchronized void launchCloud(int n, Vec3 vec3, boolean bl, float f) {
        if (!bl || !launchCloudIds.add(n)) {
            return;
        }
        if (launchCloudIds.size() > 48) {
            launchCloudIds.clear();
            launchCloudIds.add(n);
        }
        float f2 = 1.5f + 3.2f * f;
        transientVolumes.add(new VolumeInstance(10, vec3, f2 * 3.0f, 1.0f, 290.0f, f2, 0.0f, Vec3.ZERO, 0.0f, 20.0f, (float)(n & 0xFF) * 0.117f, nextKey++));
        VolumetricFxManager.trimVolumes();
    }

    public static synchronized boolean hasNuke() {
        return !nukeVolumes.isEmpty();
    }

    public static synchronized void addSmoke(Vec3 vec3, float f, float f2, float f3) {
        float f4 = Math.max(0.25f, Math.min(120.0f, f3));
        float f5 = (float)((double)nextKey * 0.6180339);
        transientVolumes.add(new VolumeInstance(1, vec3, 5.0f, Math.max(0.02f, Math.min(4.0f, f)), Math.max(290.0f, Math.min(6000.0f, f2)), 4.2f, 0.0f, Vec3.ZERO, 0.0f, f4, f5, nextKey++));
        VolumetricFxManager.trimVolumes();
    }

    /** r32: a licking flame tongue - a hot buoyant volume (the shader glows it by temperature). */
    public static synchronized void addFire(Vec3 pos, float radius, float seconds, float seed) {
        float r = Math.max(0.25f, Math.min(4.0f, radius));
        VolumeInstance v = new VolumeInstance(11, pos, r * 1.35f, 1.35f, 3200.0f, r, 7.0f,
                Vec3.ZERO, 0.0f, Math.max(0.55f, seconds), seed, nextKey++);
        v.profile = 7;
        v.velocity = new Vec3(((seed * 7.3f) % 0.24f) - 0.12f, 0.8f + 0.9f * ((seed * 3.1f) % 0.5f), ((seed * 5.7f) % 0.24f) - 0.12f);
        transientVolumes.add(v);
        VolumetricFxManager.trimVolumes();
    }

    public static synchronized void updateEmitter(BlockPos blockPos, boolean bl, float f, float f2) {
        long l = blockPos.asLong();
        if (!bl) {
            emitters.remove(l);
        } else {
            VolumeInstance volumeInstance = emitters.get(l);
            if (volumeInstance == null) {
                volumeInstance = new VolumeInstance(1, new Vec3((double)blockPos.getX() + 0.5, (double)blockPos.getY() + 0.5, (double)blockPos.getZ() + 0.5), 5.5f, f, f2, 4.6f, 0.0f, Vec3.ZERO, 0.0f, 2.0f, (float)(l & 0x3FFL) * 0.137f, l);
                emitters.put(l, volumeInstance);
            } else {
                volumeInstance.center = new Vec3((double)blockPos.getX() + 0.5, (double)blockPos.getY() + 0.5, (double)blockPos.getZ() + 0.5);
                volumeInstance.density = f;
                volumeInstance.temperature = f2;
                volumeInstance.age = 0.0f;
            }
            VolumetricFxManager.trimVolumes();
        }
    }

    public static synchronized void recordRocket(int n, Vec3 vec3, Vec3 vec32) {
        VolumetricFxManager.recordRocket(n, vec3, vec32, 1.0f);
    }

    public static synchronized void recordRocket(int n2, Vec3 vec3, Vec3 vec32, float f) {
        VolumeInstance volumeInstance;
        if (!rocketTracks.containsKey(n2) && rocketTracks.size() >= 64) return;
        RocketTrack rocketTrack = rocketTracks.computeIfAbsent(n2, n -> new RocketTrack());
        float f2 = (float)((double)n2 * 0.271 % 1.0);
        if (rocketTrack.previous != null) {
            volumeInstance = rocketTrack.openSegment;
            if (volumeInstance != null && !plumeSegments.contains(volumeInstance)) {
                volumeInstance = null;
            }
            if (volumeInstance == null || rocketTrack.segmentTicks >= 14 || rocketTrack.segmentStart.distanceTo(vec3) >= 9.0) {
                rocketTrack.segmentStart = rocketTrack.previous;
                rocketTrack.segmentTicks = 0;
                float f3 = (0.42f + Math.min(0.25f, (float)(vec32.length() * 0.1))) * f;
                volumeInstance = new VolumeInstance(3, vec3, 1.0f, 0.95f, 2600.0f, 0.05f, f3, new Vec3(0.0, 0.0, 1.0), 0.0f, 40.0f, f2 + (float)rocketTrack.segmentsCreated * 0.013f, nextKey++);
                plumeSegments.add(volumeInstance);
                rocketTrack.openSegment = volumeInstance;
                if (rocketTrack.segmentsCreated < 4096) {
                    ++rocketTrack.segmentsCreated;
                }
            }
            ++rocketTrack.segmentTicks;
            Vec3 vec33 = vec3.subtract(rocketTrack.segmentStart);
            double d = vec33.length();
            if (d > 0.015 && Double.isFinite(d)) {
                volumeInstance.axis = vec33.scale(1.0 / d);
                volumeInstance.center = rocketTrack.segmentStart.add(vec3).scale(0.5);
                volumeInstance.expansion = (float)(d * 0.5);
                volumeInstance.halfLength = (float)rocketTrack.segmentTicks * 0.05f;
                volumeInstance.boundRadius = volumeInstance.expansion + volumeInstance.auxiliary * 7.0f + 3.0f;
            }
        }
        if (vec32.lengthSqr() > 0.0025) {
            Vec3 jetAxis = vec32.normalize().scale(-1.0);
            float f4 = 0.22f * f;
            float f5 = 4.2f * f;
            plumeSegments.add(new VolumeInstance(4, vec3, f5 + 0.45f, 0.7f, 3400.0f, f5, f4, jetAxis, f5, 0.22f, f2, n2));
        }
        rocketTrack.previous = vec3;
        rocketTrack.lastSeen = clockTicks;
        trimVolumes();
    }

    public static synchronized void tick(Level level) {
        Object object;
        long l = System.nanoTime();
        ++clockTicks;
        float f = 0.05f;
        Iterator<VolumeInstance> iterator = transientVolumes.iterator();
        while (iterator.hasNext()) {
            object = iterator.next();
            ((VolumeInstance)object).age += f;
            if (((VolumeInstance)object).type == 5) {
                ((VolumeInstance)object).center = ((VolumeInstance)object).center.add(((VolumeInstance)object).velocity.scale((double)f));
                ((VolumeInstance)object).velocity = ((VolumeInstance)object).velocity.scale(0.985).add(0.0, -4.5 * (double)f, 0.0);
            }
            if (((VolumeInstance)object).type == 1 || ((VolumeInstance)object).type == VolumeInstance.ARSENAL_SMOKE) {
                VolumeInstance v = (VolumeInstance)object;
                if (v.profile != 0) {
                    v.center = v.center.add(v.velocity.scale(f));
                    v.velocity = v.velocity.scale(.94).add(.003, .012, 0);
                }
                VolumetricFxManager.updateSmokeClearance(level, v);
                if (v.profile != 0 && v.profile != 7) v.expansion *= .25f + .75f * Math.min(1, v.age / Math.min(1.4f,v.lifetime));
            }
            if (!(((VolumeInstance)object).age >= ((VolumeInstance)object).lifetime)) continue;
            iterator.remove();
        }
        iterator = nukeVolumes.iterator();
        while (iterator.hasNext()) {
            object = iterator.next();
            ((VolumeInstance)object).age += f;
            if (((VolumeInstance)object).type == 7) {
                float f2 = ((VolumeInstance)object).maxExpansion * 0.35f + 120.0f * ((VolumeInstance)object).age;
                ((VolumeInstance)object).boundRadius = f2 + ((VolumeInstance)object).maxExpansion * 0.6f + 4.0f;
            }
            if (!(((VolumeInstance)object).age >= ((VolumeInstance)object).lifetime)) continue;
            iterator.remove();
        }
        for (VolumeInstance volumeInstance : emitters.values()) {
            volumeInstance.age = Math.min(volumeInstance.age + f, 1.0f);
            VolumetricFxManager.updateSmokeClearance(level, volumeInstance);
        }
        iterator = plumeSegments.iterator();
        while (iterator.hasNext()) {
            object = iterator.next();
            ((VolumeInstance)object).age += f;
            if (!(((VolumeInstance)object).age >= ((VolumeInstance)object).lifetime)) continue;
            iterator.remove();
        }
        rocketTracks.entrySet().removeIf(entry -> clockTicks - ((RocketTrack)entry.getValue()).lastSeen > 40L);
        VolumetricFxManager.trimVolumes();
        lastTickMs = (double)(System.nanoTime() - l) / 1000000.0;
    }

    public static synchronized List<VolumeInstance> snapshot() {
        List<VolumeInstance> volumes = new ArrayList<>();
        volumes.addAll(nukeVolumes);
        volumes.addAll(transientVolumes);
        volumes.addAll(emitters.values());
        volumes.addAll(plumeSegments);
        Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        volumes.removeIf(v -> v.center.distanceToSqr(camera) > Math.pow(512 + v.boundRadius, 2));
        volumes.sort(java.util.Comparator.comparingInt(VolumetricFxManager::priority)
            .thenComparingDouble(v -> v.center.distanceToSqr(camera)));
        int limit = VolPyroClientConfig.MAX_RENDER_VOLUMES.get();
        return new ArrayList<>(volumes.subList(0, Math.min(limit, volumes.size())));
    }

    private static int priority(VolumeInstance v) {
        if (v.type == NUKE_TYPE || v.type == NUKE_SHOCK_TYPE) return 0;
        if (v.type == VolumeInstance.EXPLOSION || v.type == THERMOBARIC_TYPE) return 1;
        return v.type == VolumeInstance.DEBRIS ? 3 : 2;
    }

    /** Merge nearby repeated emitters before allocation; no unbounded per-particle volumes. */
    public static synchronized void smokePuff(Vec3 pos, float radius, float seconds, float heat) {
        if (!Double.isFinite(pos.lengthSqr()) || !Float.isFinite(radius)) return;
        radius = Math.max(0.15f, Math.min(16f, radius));
        for (VolumeInstance v : transientVolumes) {
            if (v.type == 1 && v.age < 0.3f && v.center.distanceToSqr(pos) < Math.pow(radius * 0.8, 2)) return;
        }
        float seed = (float)((nextKey * 0.6180339) % 1.0);
        transientVolumes.add(new VolumeInstance(1, pos, radius * 1.3f, 0.85f,
            Math.max(290f, Math.min(6000f, heat)), radius, 0, Vec3.ZERO, 0,
            Math.max(0.25f, Math.min(30f, seconds)), seed, nextKey++));
        trimVolumes();
    }

    /** Original Arsenal emitters on the donor's volume renderer, not copies of its explosion presets. */
    public static synchronized void arsenalSmoke(Vec3 pos, Vec3 direction, int profile) {
        if (!Double.isFinite(pos.lengthSqr()) || !Double.isFinite(direction.lengthSqr())) return;
        Vec3 dir = direction.lengthSqr() > .0001 ? direction.normalize() : new Vec3(0, .2, 1).normalize();
        int count = profile == 3 ? 5 : profile == 2 ? 3 : profile == 5 ? 3 : 1;
        float radius = profile == 3 ? 2.1f : profile == 2 ? .95f : profile == 4 ? .42f : profile == 5 ? .8f : .24f;
        float life = profile == 3 ? 8f : profile == 2 ? 5.5f : profile == 4 ? 2.4f : profile == 5 ? 3f : .65f;
        // Coalesce high-rate exhaust locally, but never suppress the first powder discharge.
        if(profile==4) for(var v:transientVolumes)
            if(v.profile==4 && v.age<.15 && v.center.distanceToSqr(pos)<.09)return;
        for (int i=0;i<count;i++) {
            Vec3 at = pos.add(dir.scale(i * radius * .45));
            float size=radius*(1-i*.08f);
            var v=new VolumeInstance(VolumeInstance.ARSENAL_SMOKE,at,size*1.6f,profile==4?.32f:profile==1?.45f:1.25f,
                    290f,size,profile,dir,0,life-i*.12f,(float)((nextKey*.6180339)%1),nextKey++);
            v.profile=profile;
            v.velocity=dir.scale(profile==3?3.2:profile==2?1.7:profile==4?1.1:.6).add(0,.18,0);
            transientVolumes.add(v);
        }
        trimVolumes();
    }

    public static synchronized void arsenalBlast(Vec3 pos,float radius,long seed) {
        float r=Math.max(1,Math.min(30,radius));
        transientVolumes.add(new VolumeInstance(2,pos,r*1.8f,1,4800,r*.75f,0,Vec3.ZERO,0,3.2f,(float)(seed&65535)/65535,nextKey++));
        Random rng=new Random(seed);
        for(int i=0;i<6;i++) {
            double angle=i*Math.PI/3;
            Vec3 dir=new Vec3(Math.cos(angle),.25+rng.nextDouble()*.35,Math.sin(angle));
            float size=Math.max(.65f,r*.55f);
            var v=new VolumeInstance(VolumeInstance.ARSENAL_SMOKE,pos.add(dir.scale(r*.2)),size*1.6f,1.1f,290,size,6,dir.normalize(),0,7f+r*.15f,rng.nextFloat(),nextKey++);
            v.profile=6;v.velocity=dir.scale(1+r*.12);transientVolumes.add(v);
        }
        spawnDebris(pos,rng,4,3,6,1800,.6f,.8f);trimVolumes();
    }

    public static synchronized void clear() {
        transientVolumes.clear();
        nukeVolumes.clear();
        emitters.clear();
        plumeSegments.clear();
        rocketTracks.clear();
        launchCloudIds.clear();
        obstacleCache.clear();
        clockTicks = 0;
        nextKey = 1;
        lastTickMs = 0;
    }

    public static synchronized int volumeCount() {
        return transientVolumes.size() + emitters.size() + nukeVolumes.size();
    }

    public static synchronized int plumeCount() {
        return plumeSegments.size();
    }

    public static synchronized double lastTickMs() {
        return lastTickMs;
    }

    private static void updateSmokeClearance(Level level, VolumeInstance volumeInstance) {
        if (level != null && (volumeInstance.type == 1 || volumeInstance.type == VolumeInstance.ARSENAL_SMOKE)) {
            BlockPos blockPos = BlockPos.containing((double)volumeInstance.center.x, (double)(volumeInstance.center.y - 0.5), (double)volumeInstance.center.z);
            volumeInstance.expansion = obstacleCache.clearanceAbove((BlockGetter)level, blockPos, volumeInstance.maxExpansion);
        }
    }

    private static void trimVolumes() {
        int n = (Integer)VolPyroClientConfig.MAX_ACTIVE_VOLUMES.get();
        while (transientVolumes.size() + emitters.size() > n) {
            if (!transientVolumes.isEmpty()) {
                int remove = 0;
                for (int i = 0; i < transientVolumes.size(); i++) {
                    if (transientVolumes.get(i).type == VolumeInstance.DEBRIS) { remove = i; break; }
                }
                transientVolumes.remove(remove);
                continue;
            }
            Long l = emitters.keySet().iterator().next();
            emitters.remove(l);
        }
        int n2 = (Integer)VolPyroClientConfig.MAX_PLUME_SEGMENTS.get();
        while (plumeSegments.size() > n2) {
            plumeSegments.remove(0);
        }
    }

    static {
        nextKey = 1L;
        launchCloudIds = new HashSet<Integer>();
    }

    private static final class RocketTrack {
        Vec3 previous;
        Vec3 segmentStart;
        VolumeInstance openSegment;
        int segmentTicks;
        long lastSeen;
        int segmentsCreated;

        private RocketTrack() {
        }
    }
}

