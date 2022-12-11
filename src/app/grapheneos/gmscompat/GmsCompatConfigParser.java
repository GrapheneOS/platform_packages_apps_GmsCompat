package app.grapheneos.gmscompat;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatConfig;
import com.android.internal.gmscompat.flags.GmsFlag;
import com.android.internal.gmscompat.StubDef;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GmsCompatConfigParser {
    private static final String TAG = "GmsCompatConfigParser";

    private final boolean strict = Build.isDebuggable();
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
        return res;
    }

    private void parse(ArrayList<String> lines, GmsCompatConfig res) {
        LineParser lineParser = new LineParser();

        int lineIdx = 0;

        String line = lines.get(lineIdx++);

        final int SECTION_FLAGS = 0;
        final int SECTION_STUBS = 1;
        final int SECTION_VERSION_MAP = 2;

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
            line = lines.get(lineIdx++);

            int section2Type;
            if ("flags".equals(sectionL2)) {
                section2Type = SECTION_FLAGS;
            } else if ("stubs".equals(sectionL2)) {
                section2Type = SECTION_STUBS;
            } else if ("versionMap".equals(sectionL2)) {
                section2Type = SECTION_VERSION_MAP;
            }
            else {
                invalidLine(line);
                return;
            }

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
                long versionMapTargetVersion = 0L;

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
                    classStubs = new ArrayMap<>();
                    res.stubs.put(className, classStubs);
                } else if (section2Type == SECTION_VERSION_MAP) {
                    versionMapTargetVersion = Long.parseLong(sectionL1);
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

                        if (strict) {
                            String className = sectionL1;
                            try {
                                checkStubDef(className, methodName, stub);
                            } catch (Exception e) {
                                Log.d(TAG, "", e);
                                invalid = true;
                            }
                        }

                        if (stub != null) {
                            classStubs.put(methodName, stub);
                        }
                    } else if (section2Type == SECTION_VERSION_MAP) {
                        if (versionMapTargetVersion == selfVersionCode) {
                            res.maxGmsCoreVersion = Long.parseLong(lineParser.next());
                            res.maxPlayStoreVersion = Long.parseLong(lineParser.next());
                        }
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

    private void invalidLine(String line) {
        Log.d(TAG, "invalid line " + line);
        invalid = true;
    }
}
