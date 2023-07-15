package app.grapheneos.gmscompat;

import android.Manifest;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatConfig;
import com.android.internal.gmscompat.GmsInfo;
import com.android.internal.gmscompat.flags.GmsFlag;
import com.android.internal.gmscompat.StubDef;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GmsCompatConfigParser {
    private static final String TAG = "GmsCompatConfigParser";

    private final boolean strict = Build.isDebuggable() && Build.VERSION.SDK_INT >= 33;
    private boolean invalid;

    private GmsCompatConfigParser() {}

    public static GmsCompatConfig exec(Context ctx) {
        if (Build.isDebuggable()) {
            try {
                GmsCompatConfig res = execInner(ctx, ConfigUpdateReceiver.CONFIG_HOLDER_PACKAGE_DEV);
                return Objects.requireNonNull(res);
            } catch (PackageManager.NameNotFoundException e) {
                // fallthrough
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            GmsCompatConfig res = execInner(ctx, ConfigUpdateReceiver.CONFIG_HOLDER_PACKAGE);
            return Objects.requireNonNull(res);
        } catch (Throwable t) {
            Log.e(TAG, "unable to obtain config", t);
            System.exit(1);
            return null;
        }
    }

    public static ApplicationInfo configHolderInfo(Context ctx) throws PackageManager.NameNotFoundException {
        var flags = PackageManager.ApplicationInfoFlags.of(0L);
        PackageManager pm = ctx.getPackageManager();
        if (Build.isDebuggable()) {
            try {
                return pm.getApplicationInfo(ConfigUpdateReceiver.CONFIG_HOLDER_PACKAGE_DEV, flags);
            } catch (PackageManager.NameNotFoundException e) {
                // fallthrouugh
            }
        }
        return pm.getApplicationInfo(ConfigUpdateReceiver.CONFIG_HOLDER_PACKAGE, flags);
    }

    private static GmsCompatConfig execInner(Context ctx, String pkg) throws PackageManager.NameNotFoundException, IOException {
        ApplicationInfo appInfo = ctx.getPackageManager()
                .getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0));
        String path = appInfo.publicSourceDir;
        final long version = appInfo.longVersionCode;

        byte[] configBytes;

        try (ZipFile f = new ZipFile(path)) {
            ZipEntry e = f.getEntry("gmscompat_config");
            try (InputStream inputStream = f.getInputStream(e)) {
                configBytes = inputStream.readAllBytes();
            }
        }

        ArrayList<String> lines = parseLines(new String(configBytes));
        GmsCompatConfig res = new GmsCompatConfig();
        {
            GmsCompatConfigParser parser = new GmsCompatConfigParser();
            parser.parse(lines, res);
            if (parser.strict && parser.invalid) {
                throw new RuntimeException();
            }
        }
        res.version = version;
        {
            String key = App.MainProcessPrefs.GMS_PACKAGES_ALLOWED_TO_UPDATE_TO_UNKNOWN_VERSIONS;
            Set<String> set = App.preferences().getStringSet(key, Collections.emptySet());

            if (set.contains(GmsInfo.PACKAGE_GMS_CORE)) {
                res.maxGmsCoreVersion = Long.MAX_VALUE;
            }
            if (set.contains(GmsInfo.PACKAGE_PLAY_STORE)) {
                res.maxPlayStoreVersion = Long.MAX_VALUE;
            }
        }
        return res;
    }

    private void parse(ArrayList<String> lines, GmsCompatConfig res) {
        LineParser lineParser = new LineParser();

        int lineIdx = 0;

        String line = lines.get(lineIdx++);

        final int SECTION_FLAGS = 0;
        final int SECTION_STUBS = 1;
        final int SECTION_VERSION_MAP = 2;
        final int SECTION_FORCE_DEFAULT_FLAGS = 3;
        final int SECTION_SPOOF_SELF_PERMISSION_CHECKS = 4;
        final int SECTION_GmsServiceBroker_SELF_PERMISSION_BYPASS = 5;
        final int SECTION_force_ComponentEnabledSettings = 6;

        final long selfVersionCode = App.ctx().getApplicationInfo().longVersionCode;

        sectionL2Loop: // level-2 section
        for (;;) {
            String sectionL2;
            {
                sectionL2 = isSection(line);
                if (sectionL2 == null) {
                    invalidLine(line);
                    return;
                }

                if (getSectionLevel(sectionL2, line) != 2) {
                    invalidLine(line);
                    return;
                }
            }

            boolean skipStubs = false;

            int section2Type;
            switch (sectionL2) {
                case "flags":
                    section2Type = SECTION_FLAGS;
                    break;
                case "stubs":
                    section2Type = SECTION_STUBS;
                    break;
                case "versionMap":
                    section2Type = SECTION_VERSION_MAP;
                    break;
                case "stubs_12.1":
                    section2Type = SECTION_STUBS;
                    skipStubs = Build.VERSION.SDK_INT >= 33;
                    break;
                case "force_default_flags":
                    section2Type = SECTION_FORCE_DEFAULT_FLAGS;
                    break;
                case "spoof_self_permission_checks_v2":
                    section2Type = SECTION_SPOOF_SELF_PERMISSION_CHECKS;
                    break;
                case "GmsServiceBroker_self_permission_bypass":
                    section2Type = SECTION_GmsServiceBroker_SELF_PERMISSION_BYPASS;
                    break;
                case "force_ComponentEnabledSettings":
                    section2Type = SECTION_force_ComponentEnabledSettings;
                    break;
                default:
                    invalidLine(line);
                    return;
            }

            line = lines.get(lineIdx++);

            sectionL1Loop: // level-1 section
            for (;;) {
                String sectionL1;
                {
                    sectionL1 = isSection(line);
                    if (sectionL1 == null) {
                        invalidLine(line);
                        return;
                    }

                    if (getSectionLevel(sectionL1, line) != 1) {
                        invalidLine(line);
                        return;
                    }
                }

                ArrayMap<String, GmsFlag> packageFlags = null;
                ArrayMap<String, StubDef> classStubs = null;
                long targetGmsCompatVersion = 0L;
                ArrayList<String> forceDefaultFlags = null;
                ArrayList<String> spoofSelfPermissionChecks = null;
                ArrayMap<String, Integer> forceCes = null;

                if (section2Type == SECTION_FLAGS) {
                    String ns = sectionL1;
                    packageFlags = new ArrayMap<>();
                    if (ns.equals(GmsFlag.NAMESPACE_GSERVICES)) {
                        res.gservicesFlags = packageFlags;
                    } else {
                        res.flags.put(ns, packageFlags);
                    }
                } else if (section2Type == SECTION_STUBS) {
                    String className = sectionL1;
                    if (!skipStubs) {
                        classStubs = new ArrayMap<>();
                        res.stubs.put(className, classStubs);
                    }
                } else if (section2Type == SECTION_VERSION_MAP) {
                    targetGmsCompatVersion = Long.parseLong(sectionL1);
                } else if (section2Type == SECTION_FORCE_DEFAULT_FLAGS) {
                    String namespace = sectionL1;
                    forceDefaultFlags = new ArrayList<>();
                    res.forceDefaultFlagsMap.put(namespace, forceDefaultFlags);
                } else if (section2Type == SECTION_SPOOF_SELF_PERMISSION_CHECKS) {
                    String packageName = sectionL1;
                    spoofSelfPermissionChecks = new ArrayList<>();
                    res.spoofSelfPermissionChecksMap.put(packageName, spoofSelfPermissionChecks);
                } else if (section2Type == SECTION_GmsServiceBroker_SELF_PERMISSION_BYPASS) {
                    targetGmsCompatVersion = Long.parseLong(sectionL1);
                    res.gmsServiceBrokerPermissionBypasses.clear();
                } else if (section2Type == SECTION_force_ComponentEnabledSettings) {
                    String packageName = sectionL1;
                    forceCes = new ArrayMap<>();
                    res.forceComponentEnabledSettingsMap.put(packageName, forceCes);
                }

                sectionL0Loop:
                for (;;) {
                    if (lineIdx == lines.size()) {
                        return;
                    }

                    line = lines.get(lineIdx++);
                    String maybeSection = isSection(line);
                    if (maybeSection != null) {
                        int level = getSectionLevel(maybeSection, line);
                        switch (level) {
                            case 2:
                                break sectionL1Loop;
                            case 1:
                                break sectionL0Loop;
                            default:
                                invalidLine(line);
                                return;
                        }
                    }

                    if (section2Type == SECTION_FORCE_DEFAULT_FLAGS) {
                        String regex = line;
                        if (strict) {
                            checkRegex(regex);
                        }
                        forceDefaultFlags.add(regex);
                        continue;
                    } else if (section2Type == SECTION_SPOOF_SELF_PERMISSION_CHECKS) {
                        String permission = line;
                        if (strict) {
                            checkPermission(permission);
                        }
                        spoofSelfPermissionChecks.add(permission);
                        continue;
                    }

                    lineParser.start(line);

                    if (section2Type == SECTION_FLAGS) {
                        String name = lineParser.next();
                        GmsFlag flag = parseGmsFlag(lineParser);
                        if (flag != null) {
                            flag.name = name;
                            packageFlags.put(name, flag);
                        }
                    } else if (section2Type == SECTION_STUBS) {
                        String methodName = lineParser.next();

                        StubDef stub = parseStubDef(lineParser);

                        if (strict && !skipStubs) {
                            String className = sectionL1;
                            try {
                                checkStubDef(className, methodName, stub);
                            } catch (Exception e) {
                                Log.d(TAG, "", e);
                                invalid = true;
                            }
                        }

                        if (stub != null && !skipStubs) {
                            classStubs.put(methodName, stub);
                        }
                    } else if (section2Type == SECTION_VERSION_MAP) {
                        if (targetGmsCompatVersion <= selfVersionCode) {
                            res.maxGmsCoreVersion = Long.parseLong(lineParser.next());
                            res.maxPlayStoreVersion = Long.parseLong(lineParser.next());
                        }
                    } else if (section2Type == SECTION_GmsServiceBroker_SELF_PERMISSION_BYPASS) {
                        if (targetGmsCompatVersion <= selfVersionCode) {
                            int serviceId = Integer.parseInt(lineParser.next());
                            String[] permissions = lineParser.next().split(",");
                            res.gmsServiceBrokerPermissionBypasses.put(serviceId, new ArraySet<>(permissions));
                        }
                    } else if (section2Type == SECTION_force_ComponentEnabledSettings) {
                        String modeStr = lineParser.next();
                        int mode;
                        switch (modeStr) {
                            case "disable":
                                mode = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                                break;
                            case "enable":
                                mode = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                                break;
                            case "default":
                                mode = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                                break;
                            default:
                                Log.d(TAG, "unknown component enabled setting " + modeStr);
                                invalidLine(line);
                                continue;
                        }
                        String className = lineParser.next();
                        forceCes.put(className, Integer.valueOf(mode));
                    }
                }
            }
        }
    }

    @Nullable
    GmsFlag parseGmsFlag(LineParser parser) {
        GmsFlag f = new GmsFlag();

        String token = parser.next();

        String actionStr;

        if (token.startsWith("perms-")) {
            byte mode;
            switch (token) {
                case "perms-none-of":
                    mode = GmsFlag.PERMISSION_CHECK_MODE_NONE_OF;
                    break;
                case "perms-not-all-of":
                    mode = GmsFlag.PERMISSION_CHECK_MODE_NOT_ALL_OF;
                    break;
                case "perms-all-of":
                    mode = GmsFlag.PERMISSION_CHECK_MODE_ALL_OF;
                    break;
                default:
                    Log.i(TAG, "unknown permissionCheckMode " + token);
                    invalid = true;
                    return null;
            }
            f.permissionCheckMode = mode;
            f.permissions = parser.next().split(",");

            actionStr = parser.next();
        } else {
            actionStr = token;
        }

        f.action = GmsFlag.ACTION_SET;

        switch (actionStr) {
            case "false":
                f.type = GmsFlag.TYPE_BOOL;
                break;
            case "true":
                f.type = GmsFlag.TYPE_BOOL;
                f.boolArg = true;
                break;
            case "set-int":
                f.type = GmsFlag.TYPE_INT;
                f.integerArg = Long.parseLong(parser.next());
                break;
            case "set-float":
                f.type = GmsFlag.TYPE_FLOAT;
                f.floatArg = Double.parseDouble(parser.next());
                break;
            case "set-string":
                f.type = GmsFlag.TYPE_STRING;
                f.stringArg = parser.remainder();
                break;
            case "set-string-empty":
                f.type = GmsFlag.TYPE_STRING;
                f.stringArg = "";
                break;
            case "set-string-null":
                f.type = GmsFlag.TYPE_STRING;
                break;
            case "append-string":
                f.action = GmsFlag.ACTION_APPEND;
                f.type = GmsFlag.TYPE_STRING;
                f.stringArg = parser.remainder();
                break;
            case "set-bytes":
                f.type = GmsFlag.TYPE_BYTES;
                f.bytesArg = Base64.decode(parser.next(), 0);
                break;
            case "set-bytes-empty":
                f.type = GmsFlag.TYPE_BYTES;
                f.bytesArg = new byte[0];
                break;
            case "set-bytes-null":
                f.type = GmsFlag.TYPE_BYTES;
                break;
            default:
                Log.d(TAG, "unknown action " + actionStr);
                invalid = true;
                return null;
        }
        return f;
    }

    @Nullable
    StubDef parseStubDef(LineParser parser) {
        String token = parser.next();

        StubDef d = new StubDef();

        switch (token) {
            case "void":
                d.type = StubDef.VOID;
                break;
            case "null":
                d.type = StubDef.NULL;
                break;
            case "nullString":
                d.type = StubDef.NULL_STRING;
                break;
            case "nullArray":
                d.type = StubDef.NULL_ARRAY;
                break;
            case "emptyByteArray":
                d.type = StubDef.EMPTY_BYTE_ARRAY;
                break;
            case "emptyIntArray":
                d.type = StubDef.EMPTY_INT_ARRAY;
                break;
            case "emptyLongArray":
                d.type = StubDef.EMPTY_LONG_ARRAY;
                break;
            case "emptyString":
                d.type = StubDef.EMPTY_STRING;
                break;
            case "emptyList":
                d.type = StubDef.EMPTY_LIST;
                break;
            case "emptyMap":
                d.type = StubDef.EMPTY_MAP;
                break;
            case "false":
                d.type = StubDef.BOOLEAN;
                d.integerVal = 0;
                break;
            case "true":
                d.type = StubDef.BOOLEAN;
                d.integerVal = 1;
                break;
            case "byte":
                d.type = StubDef.BYTE;
                d.integerVal = Byte.parseByte(parser.next());
                break;
            case "int":
                d.type = StubDef.INT;
                d.integerVal = Integer.parseInt(parser.next());
                break;
            case "long":
                d.type = StubDef.LONG;
                d.integerVal = Long.parseLong(parser.next());
                break;
            case "float":
                d.type = StubDef.FLOAT;
                d.doubleVal = Float.parseFloat(parser.next());
                break;
            case "double":
                d.type = StubDef.DOUBLE;
                d.doubleVal = Double.parseDouble(parser.next());
                break;
            case "String":
                d.type = StubDef.STRING;
                d.stringVal = parser.remainder(); // to allow for any characters (except \n)
                break;
            case "throw":
                d.type = StubDef.THROW;
                d.stringVal = parser.next();
                break;
            case "default":
                d.type = StubDef.DEFAULT;
                break;
            default:
                Log.i(TAG, "unknown token " + token);
                invalid = true;
                return null;
        }

        return d;
    }

    static class LineParser {
        private String s;
        private int a;
        private int b;

        void start(String s) {
            this.s = s;
            a = 0;
            b = 0;
        }

        String next() {
            final char SEPARATOR = ' ';
            b = s.indexOf(SEPARATOR, a);
            String res = b >= 0? s.substring(a, b) : s.substring(a);
            a = b + 1;
            return res;
        }

        String remainder() {
            return s.substring(a);
        }
    }

    static ArrayList<String> parseLines(String s) {
        String[] lines = s.split("\n");
        ArrayList<String> result = new ArrayList<>(lines.length);
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            result.add(line);
        }
        return result;
    }

    @Nullable
    private static String isSection(String line) {
        int len = line.length();
        if (len < 3) {
            return null;
        }
        int i = 0;
        while (line.charAt(i) == '[' && line.charAt(len - i - 1) == ']') {
            ++i;
        }

        if (i == 0) {
            return null;
        }

        return line.substring(i, len - i);
    }

    private static int getSectionLevel(String section, String line) {
        return (line.length() - section.length()) >> 1;
    }

    static void checkStubDef(String className, String methodName, @Nullable StubDef stubDef)
            throws InvalidObjectException, ReflectiveOperationException {
        if (stubDef == null) {
            throw new InvalidObjectException("failed to parse StubDef");
        }

        Class cls = Class.forName(className);
        ArrayList<Method> methods = new ArrayList<>(7);

        for (Method m : cls.getMethods()) {
            if (m.getName().equals(methodName)) {
                methods.add(m);
            }
        }

        String signature = className + '#' + methodName;

        if (methods.size() == 0) {
            throw new InvalidObjectException(signature + " not found");
        }

        Class returnType = methods.get(0).getReturnType();

        if (methods.size() > 1) {
            for (Method m : methods) {
                if (m.getReturnType() != returnType) {
                    throw new InvalidObjectException("overloads of method " + signature
                            + " have different return types");
                }
            }
        }

        switch (stubDef.type) {
            case StubDef.VOID:
                stubCheck(signature, returnType == Void.TYPE);
                break;
            case StubDef.NULL:
                // only for types that implement Parcelable
                stubCheck(signature, Parcelable.class.isAssignableFrom(returnType));
                break;
            case StubDef.NULL_STRING:
            case StubDef.EMPTY_STRING:
            case StubDef.STRING:
                stubCheck(signature, returnType == String.class);
                break;
            case StubDef.NULL_ARRAY:
                stubCheck(signature, returnType.isArray()
                    && (
                        Parcelable.class.isAssignableFrom(returnType.getComponentType())
                        ||
                        returnType.getComponentType().isPrimitive()
                    )
                );
                break;
            case StubDef.EMPTY_BYTE_ARRAY:
                stubCheck(signature, returnType == byte[].class);
                break;
            case StubDef.EMPTY_INT_ARRAY:
                stubCheck(signature, returnType == int[].class);
                break;
            case StubDef.EMPTY_LONG_ARRAY:
                stubCheck(signature, returnType == long[].class);
                break;
            case StubDef.EMPTY_LIST:
                stubCheck(signature, returnType == List.class);
                break;
            case StubDef.EMPTY_MAP:
                stubCheck(signature, returnType == Map.class);
                break;
            case StubDef.BOOLEAN:
                stubCheck(signature, returnType == boolean.class);
                break;
            case StubDef.BYTE:
                stubCheck(signature, returnType == byte.class);
                break;
            case StubDef.INT:
                stubCheck(signature, returnType == int.class);
                break;
            case StubDef.LONG:
                stubCheck(signature, returnType == long.class);
                break;
            case StubDef.FLOAT:
                stubCheck(signature, returnType == float.class);
                break;
            case StubDef.DOUBLE:
                stubCheck(signature, returnType == double.class);
                break;
            case StubDef.THROW: {
                    Class c = Class.forName(stubDef.stringVal);
                    stubCheck(signature,
                            Throwable.class.isAssignableFrom(c)
                            &&
                            Modifier.isPublic(c.getConstructor().getModifiers())
                    );
                    break;
            }
            case StubDef.DEFAULT:
                // unknown return type
                break;
            default:
                stubCheck(signature, false);
                break;
        }
    }

    private static void stubCheck(String sig, boolean v) throws InvalidObjectException {
        if (!v) {
            throw new InvalidObjectException("invalid stub for " + sig);
        }
    }

    private void checkRegex(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            Log.d(TAG, "invalid regex " + regex, e);
            invalid = true;
        }
    }

    private static volatile String[] allPermissions;

    private void checkPermission(String perm) {
        if (allPermissions == null) {
            Field[] fields = Manifest.permission.class.getDeclaredFields();
            int cnt = fields.length;
            String[] arr = new String[cnt];
            for (int i = 0; i < cnt; ++i) {
                try {
                    arr[i] = (String) fields[i].get(null);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            Arrays.sort(arr);
            allPermissions = arr;
        }
        if (Arrays.binarySearch(allPermissions, perm) < 0) {
            Log.d(TAG, "unknown permission " + perm);
            invalid = true;
        }
    }

    private void invalidLine(String line) {
        Log.d(TAG, "invalid line " + line);
        invalid = true;
    }
}
